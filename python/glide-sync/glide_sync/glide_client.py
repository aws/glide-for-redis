# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import os
import sys
import threading
from types import TracebackType
from typing import TYPE_CHECKING, Any, List, Optional, Tuple, Union

if TYPE_CHECKING:
    from .isolated_scope import IsolatedScope

from glide_shared._fast_response import parse_response as _fast_parse_response
from glide_shared.commands.command_args import ObjectType
from glide_shared.commands.core_options import PubSubMsg
from glide_shared.config import (
    BaseClientConfiguration,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
)
from glide_shared.connection_request import _create_sync_connection_request
from glide_shared.constants import OK, TEncodable, TResult
from glide_shared.exceptions import (
    ClosingError,
    ConfigurationError,
    RequestError,
    get_request_error_class,
)
from glide_shared.opentelemetry import _create_batch_span, _create_command_span
from glide_shared.protobuf.command_request_pb2 import RequestType
from glide_shared.routes import (
    AllNodes,
    AllPrimaries,
    ByAddressRoute,
    RandomNode,
    Route,
    SlotIdRoute,
    SlotKeyRoute,
    SlotType,
    build_protobuf_route,
)
from glide_sync._ffi_instance import _SYNC_FFI

from .logger import Level, Logger
from .sync_commands.cluster_commands import ClusterCommands
from .sync_commands.cluster_scan_cursor import ClusterScanCursor
from .sync_commands.core import CoreCommands
from .sync_commands.standalone_commands import StandaloneCommands

if sys.version_info >= (3, 11):
    from typing import Self
else:
    from typing_extensions import Self

# Pre-allocated null-terminated span name for the EVALSHA (`_execute_script`)
# path. Kept at module scope so we do not re-allocate a `char[]` per sampled
# call. `_SYNC_FFI.ffi` is a process-wide singleton so this buffer is safe to
# share across clients.
_EVALSHA_SPAN_NAME = _SYNC_FFI.ffi.new("char[]", b"EVALSHA")

ENCODING = "utf-8"


# Enum values must match the Rust definition
class FFIClientTypeEnum:
    Async = 0
    Sync = 1


def _slot_for_key(key: bytes) -> int:
    """Compute the Redis cluster hash slot for a key (CRC16 mod 16384).

    Handles hash tags: if the key contains {...}, only the content between the
    first { and first } after it is hashed.
    """
    start = key.find(b"{")
    if start != -1:
        end = key.find(b"}", start + 1)
        if end != -1 and end != start + 1:
            key = key[start + 1 : end]

    crc = 0
    for b in key:
        crc ^= b << 8
        for _ in range(8):
            if crc & 0x8000:
                crc = (crc << 1) ^ 0x1021
            else:
                crc <<= 1
            crc &= 0xFFFF
    return crc % 16384


class BaseClient(CoreCommands):

    def __init__(self, config: BaseClientConfiguration):
        """
        To create a new client, use the `create` classmethod
        """
        _glide_ffi = _SYNC_FFI
        self._ffi = _glide_ffi.ffi
        self._lib = _glide_ffi.lib
        self._config: BaseClientConfiguration = config
        self._pubsub_queue: List[PubSubMsg] = []
        self._pubsub_lock = threading.Lock()
        self._pubsub_condition = threading.Condition(self._pubsub_lock)
        self._pubsub_callback_ref = None  # Keep callback alive
        # Lock protecting _core_client and _is_closed for free-threading safety.
        # Under GIL builds this is a no-op (GIL serializes access).
        # Under free-threaded builds this prevents use-after-free on concurrent close.
        self._client_lock = threading.Lock()

        self._is_closed: bool = False

    @classmethod
    def create(cls, config: BaseClientConfiguration) -> Self:
        if not isinstance(
            config, (GlideClientConfiguration, GlideClusterClientConfiguration)
        ):
            raise ConfigurationError(
                "Configuration must be an instance of the sync version of GlideClientConfiguration or GlideClusterClientConfiguration, imported from glide_sync.config."
            )
        self = cls(config)
        self._config = config
        self._is_closed = False

        os.register_at_fork(after_in_child=self._create_core_client)

        self._create_core_client()

        return self

    def _create_core_client(self):
        # This check is needed in case a fork happens after the client already closed
        # In that case the registered fork function will kick in even if the
        # client already closed, and recreate it anyway.
        if self._is_closed:
            return
        conn_req = _create_sync_connection_request(self._config)
        conn_req_bytes = conn_req.SerializeToString()
        # Store for scoped_connection
        self._conn_req_bytes = conn_req_bytes
        client_type = self._ffi.new(
            "ClientType*",
            {
                "_type": self._ffi.cast("ClientTypeEnum", FFIClientTypeEnum.Sync),
            },
        )

        # Always create pubsub callback to support dynamic subscriptions
        # This ensures messages are always handled by the wrapper, whether they originate
        # from configured subscriptions or from dynamic subscriptions added at runtime
        python_callback = self._create_push_handle_callback()
        pubsub_callback = self._ffi.callback("PubSubCallback", python_callback)
        # Store reference to prevent garbage collection
        self._pubsub_callback_ref = pubsub_callback

        # Create address resolver callback if configured
        address_resolver_callback = self._ffi.NULL
        if self._config.address_resolver is not None:
            resolver_fn = self._config.address_resolver

            def _address_resolver_callback(
                client_id,
                host_ptr,
                host_len,
                port,
                resolved_host_buf,
                resolved_host_buf_len,
                resolved_host_len_ptr,
            ):
                try:
                    host = self._ffi.buffer(host_ptr, host_len)[:].decode("utf-8")
                    resolved_host, resolved_port = resolver_fn(host, port)
                    encoded_host = resolved_host.encode("utf-8")
                    write_len = min(len(encoded_host), resolved_host_buf_len)
                    self._ffi.memmove(resolved_host_buf, encoded_host, write_len)
                    resolved_host_len_ptr[0] = write_len
                    return resolved_port
                except Exception:
                    # On error, return 0 to signal fallback to original address
                    return 0

            address_resolver_callback = self._ffi.callback(
                "AddressResolverCallback", _address_resolver_callback
            )
            # Store reference to prevent garbage collection
            self._address_resolver_callback_ref = address_resolver_callback

        client_response_ptr = self._lib.create_client(
            conn_req_bytes,
            len(conn_req_bytes),
            client_type,
            pubsub_callback,
            address_resolver_callback,
            0,  # client_id is not used by the Python client
        )

        Logger.log(Level.INFO, "connection info", "new connection established")

        # Handle the connection response
        if client_response_ptr != self._ffi.NULL:
            client_response = self._try_ffi_cast(
                "ConnectionResponse*", client_response_ptr
            )
            if client_response.conn_ptr != self._ffi.NULL:
                self._core_client = client_response.conn_ptr
            else:
                error_message = (
                    self._ffi.string(client_response.connection_error_message).decode(
                        ENCODING
                    )
                    if client_response.connection_error_message != self._ffi.NULL
                    else "Unknown error"
                )
                raise ClosingError(error_message)

            # Free the connection response to avoid memory leaks
            self._lib.free_connection_response(client_response_ptr)

            # Note: scope prewarm is deferred to first scoped_connection() call
            # to avoid creating extra connections at client startup (which breaks
            # lazy connection tests and connection count assertions).
        else:
            raise ClosingError("Failed to create client, response pointer is NULL.")

    def _create_push_handle_callback(self):
        """Create the FFI pubsub callback function"""

        def _pubsub_callback(
            client_ptr,
            kind,
            message_ptr,
            message_len,
            channel_ptr,
            channel_len,
            pattern_ptr,
            pattern_len,
        ):
            try:
                # Convert C pointers to Python bytes using ffi.buffer
                message = self._ffi.buffer(message_ptr, message_len)[:]
                channel = self._ffi.buffer(channel_ptr, channel_len)[:]
                pattern = (
                    self._ffi.buffer(pattern_ptr, pattern_len)[:]
                    if pattern_ptr != self._ffi.NULL
                    else None
                )

                push_kind_map = {
                    0: "Disconnection",
                    1: "Other",
                    2: "Invalidate",
                    3: "Message",
                    4: "PMessage",
                    5: "SMessage",
                    6: "Unsubscribe",
                    7: "PUnsubscribe",
                    8: "SUnsubscribe",
                    9: "Subscribe",
                    10: "PSubscribe",
                    11: "SSubscribe",
                }

                message_kind = push_kind_map.get(kind)

                if message_kind == "Disconnection":
                    Logger.log(
                        Level.WARN,
                        "disconnect notification",
                        "Transport disconnected, messages might be lost",
                    )
                elif message_kind in ["Message", "PMessage", "SMessage"]:
                    pubsub_msg = PubSubMsg(
                        message=message, channel=channel, pattern=pattern
                    )

                    # This aquires the underlying `_pubsub_lock` and allows for calling `notify()` on the variable
                    # If a callback is registered, call it with the message and the provided context
                    # Otherwise, append the message to the queue and notify threads that are waiting for a message.
                    with self._pubsub_condition:
                        user_callback, context = (
                            self._config._get_pubsub_callback_and_context()
                        )
                        if user_callback:
                            user_callback(pubsub_msg, context)
                        else:
                            self._pubsub_queue.append(pubsub_msg)
                            self._pubsub_condition.notify()
                elif message_kind in [
                    "PSubscribe",
                    "Subscribe",
                    "SSubscribe",
                    "Unsubscribe",
                    "PUnsubscribe",
                    "SUnsubscribe",
                ]:
                    pass  # Ignore subscription confirmations
                else:
                    Logger.log(
                        Level.WARN,
                        "unknown notification",
                        f"Unknown notification message: '{message_kind}'",
                    )

            except Exception as e:
                Logger.log(
                    Level.ERROR, "pubsub_callback", f"Error in pubsub callback: {e}"
                )

        return _pubsub_callback

    def _handle_response(self, message):
        if message == self._ffi.NULL:
            raise RequestError("Received NULL message.")
        addr = int(self._ffi.cast("uintptr_t", message))
        result, _arena_ptr = _fast_parse_response(addr)
        # Arena is freed by free_command_result in _handle_cmd_result's finally block
        return result

    def _handle_command_response(self, msg):
        """Handle a CommandResponse message based on its response type."""
        handlers = {
            0: self._handle_null_response,
            1: self._handle_int_response,
            2: self._handle_float_response,
            3: self._handle_bool_response,
            4: self._handle_string_response,
            5: self._handle_array_response,
            6: self._handle_map_response,
            7: self._handle_set_response,
            8: self._handle_ok_response,
            9: self._handle_error_response,
        }

        handler = handlers.get(msg.response_type)
        if handler is None:
            raise RequestError(f"Unhandled response type = {msg.response_type}")

        return handler(msg)

    def _handle_null_response(self, msg):
        return None

    def _handle_int_response(self, msg):
        return msg.int_value

    def _handle_float_response(self, msg):
        return msg.float_value

    def _handle_bool_response(self, msg):
        return bool(msg.bool_value)

    def _handle_string_response(self, msg):
        try:
            return self._ffi.buffer(msg.string_value, msg.string_value_len)[:]
        except Exception as e:
            raise RequestError(f"Error decoding string value: {e}")

    def _handle_array_response(self, msg):
        array = []
        for i in range(msg.array_value_len):
            element = self._try_ffi_cast("struct CommandResponse*", msg.array_value + i)
            array.append(self._handle_response(element))
        return array

    def _handle_map_response(self, msg):
        map_dict = {}
        for i in range(msg.array_value_len):
            element = self._try_ffi_cast("struct CommandResponse*", msg.array_value + i)
            key = self._try_ffi_cast("struct CommandResponse*", element.map_key)
            value = self._try_ffi_cast("struct CommandResponse*", element.map_value)
            map_dict[self._handle_response(key)] = self._handle_response(value)
        return map_dict

    def _handle_set_response(self, msg):
        result_set = set()
        sets_array = self._try_ffi_cast(
            f"struct CommandResponse[{msg.sets_value_len}]", msg.sets_value
        )
        for i in range(msg.sets_value_len):
            element = sets_array[i]
            result_set.add(self._handle_response(element))
        return result_set

    def _handle_ok_response(self, msg):
        return OK

    def _handle_error_response(self, msg):
        try:
            error_msg = self._ffi.buffer(msg.string_value, msg.string_value_len)[:]
            return RequestError(f"{error_msg}")
        except Exception as e:
            raise RequestError(f"Error decoding error message: {e}")

    def _try_ffi_cast(self, type, source):
        try:
            return self._ffi.cast(type, source)
        except Exception as e:
            raise ClosingError(f"FFI casting failed: {e}")

    def _to_c_strings(self, args):
        """Convert Python arguments to C-compatible pointers and lengths."""
        c_strings = []
        string_lengths = []
        buffers = []  # Keep a reference to prevent premature garbage collection

        for arg in args:
            if isinstance(arg, str):
                arg_bytes = arg.encode(ENCODING)
            elif isinstance(arg, (bytes, bytearray, memoryview)):
                arg_bytes = arg
            else:
                raise TypeError(f"Unsupported argument type: {type(arg)}")

            # Use ffi.from_buffer for zero-copy conversion
            buffers.append(arg_bytes)  # Keep the byte buffer alive
            c_strings.append(
                self._try_ffi_cast("size_t", self._ffi.from_buffer(arg_bytes))
            )
            string_lengths.append(len(arg_bytes))
        # Return C-compatible arrays and keep buffers alive
        return (
            self._ffi.new("size_t[]", c_strings),
            self._ffi.new("unsigned long[]", string_lengths),
            buffers,  # Ensure buffers stay alive
        )

    # `route_bytes` must remain alive for the duration of the FFI call that consumes `route_ptr`
    def _to_c_route_ptr_and_len(self, route: Optional[Route]):
        proto_route = build_protobuf_route(route)
        if proto_route:
            route_bytes = proto_route.SerializeToString()
            route_ptr = self._ffi.from_buffer(route_bytes)
            route_len = len(route_bytes)
        else:
            route_bytes = None
            route_ptr = self._ffi.NULL
            route_len = 0

        return route_ptr, route_len, route_bytes

    def _handle_cmd_result(self, command_result):
        try:
            if command_result == self._ffi.NULL:
                raise ClosingError("Internal error: Received NULL as a command result")
            if command_result.command_error != self._ffi.NULL:
                # Handle the error case
                error = self._try_ffi_cast(
                    "CommandError*", command_result.command_error
                )
                error_message = self._ffi.string(error.command_error_message).decode(
                    ENCODING
                )
                error_class = get_request_error_class(error.command_error_type)
                # Free the error message to avoid memory leaks
                raise error_class(error_message)
            else:
                return self._handle_response(command_result.response)
                # Free the error message to avoid memory leaks
        finally:
            self._lib.free_command_result(command_result)

    @staticmethod
    def _validate_response_buffers(response_buffers: List[memoryview]) -> None:
        """Each buffer for a multi-value read must be writable and contiguous."""
        for mv in response_buffers:
            if mv.readonly:
                raise TypeError("response_buffers entries must be writable")
            if not mv.c_contiguous:
                raise TypeError("response_buffers entries must be C-contiguous")

    def _execute_command(
        self,
        request_type: RequestType.ValueType,  # type: ignore[override]
        args: List[TEncodable],
        route: Optional[Route] = None,
        response_buffer: Optional[memoryview] = None,
        response_buffers: Optional[List[memoryview]] = None,
    ) -> TResult:
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )
        client_adapter_ptr = self._core_client
        if client_adapter_ptr == self._ffi.NULL:
            raise ValueError("Invalid client pointer.")
        if response_buffer:
            if response_buffer.readonly:
                raise TypeError("response_buffer must be writable")
            if not response_buffer.c_contiguous:
                raise TypeError("response_buffer must be C-contiguous")
        if response_buffers is not None:
            self._validate_response_buffers(response_buffers)

        # Sample before reading the caller's context. A sampled command span uses
        # the active OTel span as its parent when one is available.
        from .opentelemetry import OpenTelemetry

        span = 0
        span_name_cstr = None
        if OpenTelemetry.is_tracing_enabled() and OpenTelemetry.should_sample():
            parent_ctx = OpenTelemetry._get_parent_span_context()
            command_name = RequestType.Name(request_type)
            span_name_cstr = self._ffi.new("char[]", command_name.encode())
            span = _create_command_span(
                self._ffi, self._lib, span_name_cstr, parent_ctx
            )

        try:
            # Convert the arguments to C-compatible pointers
            c_args, c_lengths, buffers = self._to_c_strings(args)

            # Route bytes should be kept alive in the scope of the FFI call
            route_ptr, route_len, route_bytes = self._to_c_route_ptr_and_len(route)

            if response_buffers is not None:
                # One writable buffer per top-level array element (e.g. mget):
                # each value is copied straight into its caller-owned buffer.
                # The from_buffer cdata must stay alive for the call, so keep
                # the list referenced until command_with_buffers returns.
                target_ptrs = [self._ffi.from_buffer(mv) for mv in response_buffers]
                target_bufs = self._ffi.new("uint8_t*[]", target_ptrs)
                target_lens = self._ffi.new(
                    "size_t[]", [mv.nbytes for mv in response_buffers]
                )
                result = self._lib.command_with_buffers(
                    client_adapter_ptr,
                    0,
                    request_type,
                    len(args),
                    c_args,
                    c_lengths,
                    route_ptr,
                    route_len,
                    target_bufs,
                    target_lens,
                    len(response_buffers),
                    span,
                )
            else:
                buf_ptr = (
                    self._ffi.from_buffer(response_buffer)
                    if response_buffer
                    else self._ffi.NULL
                )
                # Capacity must be expressed in bytes, not elements. ``len()`` on
                # a memoryview returns the element count (``shape[0]``), which
                # equals the byte count only for itemsize-1 formats (e.g. "B").
                buf_len = response_buffer.nbytes if response_buffer else 0
                result = self._lib.command_with_buffer(
                    client_adapter_ptr,
                    0,
                    request_type,
                    len(args),
                    c_args,
                    c_lengths,
                    route_ptr,
                    route_len,
                    buf_ptr,
                    buf_len,
                    span,
                )
        finally:
            # Drop span if it was created
            if span != 0:
                self._lib.drop_otel_span(span)
        return self._handle_cmd_result(result)

    def _update_connection_password(
        self,
        password: Optional[str],
        immediate_auth: bool = False,
    ) -> TResult:
        """
        Update the current connection password with a new password.

        Note:
            This method updates the client's internal password configuration and does
            not perform password rotation on the server side.

        This method is useful in scenarios where the server password has changed or when
        utilizing short-lived passwords for enhanced security. It allows the client to
        update its password to reconnect upon disconnection without the need to recreate
        the client instance. This ensures that the internal reconnection mechanism can
        handle reconnection seamlessly, preventing the loss of in-flight commands.

        Args:
            password (`Optional[str]`): The new password to use for the connection,
                if `None` the password will be removed.
            immediate_auth (`bool`):
                `True`: The client will authenticate immediately with the new password against all connections, Using `AUTH`
                command. If password supplied is an empty string, auth will not be performed and warning will be returned.
                The default is `False`.

        Returns:
            TOK: A simple OK response. If `immediate_auth=True` returns OK if the reauthenticate succeed.

        Example:
            >>> client.update_connection_password("new_password", immediate_auth=True)
            'OK'
        """
        if self._is_closed:
            raise ClosingError("Client is closed.")
        client_adapter_ptr = self._core_client
        if client_adapter_ptr == self._ffi.NULL:
            raise ValueError("Invalid client pointer.")

        # Prepare C string for password
        c_password = (
            self._ffi.new("char[]", password.encode(ENCODING))
            if password is not None
            else self._ffi.new("char[]", b"")
        )

        result = self._lib.update_connection_password(
            client_adapter_ptr,
            0,  # Request ID (0 for sync use)
            c_password,
            immediate_auth,
        )
        return self._handle_cmd_result(result)

    def _execute_batch(
        self,
        commands: List[Tuple[RequestType.ValueType, List[TEncodable]]],  # type: ignore[override]
        is_atomic: bool,
        raise_on_error: bool,
        retry_server_error: bool = False,
        retry_connection_error: bool = False,
        route: Optional[Route] = None,
        timeout: Optional[int] = None,
    ) -> List[TResult]:
        """
        Execute a batch of commands synchronously using the FFI batch function.
        Accepts pre-extracted parameters from exec().
        """

        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )

        client_adapter_ptr = self._core_client
        if client_adapter_ptr == self._ffi.NULL:
            raise ValueError("Invalid client pointer.")

        # Create span if OpenTelemetry is configured and sampling indicates we should trace
        from .opentelemetry import OpenTelemetry

        span = 0
        if OpenTelemetry.is_tracing_enabled() and OpenTelemetry.should_sample():
            parent_ctx = OpenTelemetry._get_parent_span_context()
            span = _create_batch_span(self._ffi, self._lib, parent_ctx)

        try:
            # Note: batch_refs and option_refs must remain in scope
            # throughout this entire function call to prevent garbage collection of Python objects
            # that have C pointers pointing to them via ffi.from_buffer().

            # Convert commands + atomic flag to C BatchInfo
            batch_info, batch_refs = self._convert_commands_to_c_batch_info(
                commands, is_atomic
            )

            # Create batch options from extracted parameters
            batch_options, option_refs = self._create_c_batch_options_from_params(
                retry_server_error, retry_connection_error, route, timeout
            )

            result = self._lib.batch(
                client_adapter_ptr,
                0,  # callback_index (0 for sync)
                batch_info,
                raise_on_error,
                batch_options,
                span,  # span_ptr for tracing
            )
            return self._handle_cmd_result(result)
        finally:
            # Drop span if it was created
            if span != 0:
                self._lib.drop_otel_span(span)

    def _convert_commands_to_c_batch_info(
        self,
        commands: List[Tuple[RequestType.ValueType, List[TEncodable]]],
        is_atomic: bool,
    ) -> Tuple[Any, List[Any]]:
        """
        Convert commands directly to C BatchInfo (no intermediate _to_c_strings).
        Returns a tuple of (batch_info, refs) where refs contains all Python objects
        that must be kept alive to prevent garbage collection while C code uses pointers to them.
        """
        # all_refs keeps Python objects alive while C pointers reference their memory.
        # ffi.from_buffer() creates C pointers to Python object memory, and ffi.new() creates
        # FFI-managed memory with a Python reference controlling its lifetime. In both cases,
        # if Python references are garbage collected, the underlying memory may be freed,
        # creating dangling C pointers.

        all_refs = []
        cmd_infos = []

        for request_type, args in commands:
            args_buffers = []
            arg_ptrs = []
            arg_lengths = []

            for arg in args:
                if isinstance(arg, str):
                    arg_bytes = arg.encode(ENCODING)
                elif isinstance(arg, (bytes, bytearray, memoryview)):
                    arg_bytes = arg
                else:
                    raise TypeError(f"Unsupported argument type: {type(arg)}")

                args_buffers.append(arg_bytes)
                arg_ptrs.append(self._ffi.from_buffer(arg_bytes))
                arg_lengths.append(len(arg_bytes))

            c_arg_array = self._ffi.new("const uint8_t*[]", arg_ptrs)
            c_lengths = self._ffi.new("size_t[]", arg_lengths)

            cmd_info = self._ffi.new(
                "CmdInfo*",
                {
                    "request_type": request_type,
                    "args": c_arg_array,
                    "arg_count": len(args),
                    "args_len": c_lengths,
                },
            )

            cmd_infos.append(cmd_info)
            all_refs.extend(args_buffers + [c_arg_array, c_lengths])

        cmd_info_array = self._ffi.new("const CmdInfo*[]", cmd_infos)
        all_refs.append(cmd_info_array)
        all_refs.extend(cmd_infos)

        batch_info = self._ffi.new(
            "BatchInfo*",
            {
                "cmd_count": len(commands),
                "cmds": cmd_info_array,
                "is_atomic": is_atomic,
            },
        )

        return batch_info, all_refs + [batch_info]

    def _create_c_batch_options_from_params(
        self,
        retry_server_error: bool,
        retry_connection_error: bool,
        route: Optional[Route],
        timeout: Optional[int],
    ) -> Tuple[Any, List[Any]]:
        """
        Create BatchOptionsInfo from params, with refs.
        Returns a tuple of (batch_options, refs) where refs contains all Python objects
        that must be kept alive while C code accesses pointers to them.
        """

        route_info, route_refs = self._convert_route_to_c_format(route)

        batch_options = self._ffi.new(
            "BatchOptionsInfo*",
            {
                "retry_server_error": retry_server_error,
                "retry_connection_error": retry_connection_error,
                "has_timeout": timeout is not None,
                "timeout": timeout or 0,
                "route_info": route_info,
            },
        )

        return batch_options, route_refs + [batch_options]

    def _convert_route_to_c_format(
        self, route: Optional[Route]
    ) -> Tuple[Any, List[Any]]:
        """
        Convert a Route object to C RouteInfo format.

        Returns a tuple of (route_info, refs) where refs contains all Python objects
        that must be kept alive while C code uses pointers to them.
        """
        if route is None:
            return self._ffi.NULL, []

        refs = []

        slot_key_ptr = self._ffi.NULL
        hostname_ptr = self._ffi.NULL
        route_type = 2  # Default to Random
        slot_id = 0
        slot_type = 0  # Primary by default
        port = 0

        if isinstance(route, AllNodes):
            route_type = 0
        elif isinstance(route, AllPrimaries):
            route_type = 1
        elif isinstance(route, RandomNode):
            route_type = 2
        elif isinstance(route, SlotIdRoute):
            route_type = 3
            slot_id = route.slot_id
            slot_type = 0 if route.slot_type == SlotType.PRIMARY else 1
        elif isinstance(route, SlotKeyRoute):
            route_type = 4
            # Null termination needed for safety instructions of the FFI layer's `ptr_to_str` call.
            slot_key_bytes = route.slot_key.encode(ENCODING) + b"\0"
            refs.append(slot_key_bytes)
            slot_key_ptr = self._ffi.from_buffer(slot_key_bytes)
            slot_type = 0 if route.slot_type == SlotType.PRIMARY else 1
        elif isinstance(route, ByAddressRoute):
            route_type = 5
            # Null termination needed for safety instructions of the FFI layer's `ptr_to_str` call.
            hostname_bytes = route.host.encode(ENCODING) + b"\0"
            refs.append(hostname_bytes)
            hostname_ptr = self._ffi.from_buffer(hostname_bytes)
            port = route.port if route.port is not None else 0
        else:
            raise RequestError(f"Invalid route type: {type(route)}")

        route_info = self._ffi.new(
            "RouteInfo*",
            {
                "route_type": route_type,
                "slot_id": slot_id,
                "slot_key": slot_key_ptr,
                "slot_type": slot_type,
                "hostname": hostname_ptr,
                "port": port,
            },
        )

        return route_info, refs + [route_info]

    def _execute_script(
        self,
        script_hash: str,
        keys: Optional[List[TEncodable]] = None,
        args: Optional[List[TEncodable]] = None,
        route: Optional[Route] = None,
    ) -> TResult:

        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )

        client_adapter_ptr = self._core_client
        if client_adapter_ptr == self._ffi.NULL:
            raise ValueError("Invalid client pointer.")

        # Default to empty lists if None provided
        if keys is None:
            keys = []
        if args is None:
            args = []

        # Convert keys to C-compatible format
        keys_c_args, keys_c_lengths, keys_buffers = self._to_c_strings(keys)

        # Convert args to C-compatible format
        args_c_args, args_c_lengths, args_buffers = self._to_c_strings(args)

        # Convert script hash to C string
        hash_bytes = script_hash.encode(ENCODING) + b"\0"
        hash_buffer = self._ffi.from_buffer(hash_bytes)

        # Route bytes should be kept alive in the scope of the FFI call
        route_ptr, route_len, route_bytes = self._to_c_route_ptr_and_len(route)

        # Create span if OpenTelemetry is configured and sampling
        from .opentelemetry import OpenTelemetry

        span = 0
        if OpenTelemetry.is_tracing_enabled() and OpenTelemetry.should_sample():
            parent_ctx = OpenTelemetry._get_parent_span_context()
            span = _create_command_span(
                self._ffi, self._lib, _EVALSHA_SPAN_NAME, parent_ctx
            )

        try:
            result = self._lib.invoke_script(
                client_adapter_ptr,
                0,  # Request ID - placeholder for sync clients
                hash_buffer,
                len(keys),
                keys_c_args,
                keys_c_lengths,
                len(args),
                args_c_args,
                args_c_lengths,
                route_ptr,
                route_len,
                span,
            )
            return self._handle_cmd_result(result)
        finally:
            if span != 0:
                self._lib.drop_otel_span(span)

    def try_get_pubsub_message(self) -> Optional[PubSubMsg]:
        """Try to get a pubsub message without blocking"""
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )

        if self._config._get_pubsub_callback_and_context()[0] is not None:
            raise ConfigurationError(
                "The operation will never succeed since messages will be passed to the configured callback."
            )

        with self._pubsub_condition:
            if self._pubsub_queue:
                return self._pubsub_queue.pop(0)
            else:
                return None

    def get_pubsub_message(self) -> PubSubMsg:
        """Get a pubsub message, blocking until one is available"""
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )

        if self._config._get_pubsub_callback_and_context()[0] is not None:
            raise ConfigurationError(
                "The operation will never complete since messages will be passed to the configured callback."
            )

        with self._pubsub_condition:
            while not self._pubsub_queue:
                if self._is_closed:
                    raise ClosingError("Client was closed while waiting for message")

                # Block indefinitely until notify() is called
                self._pubsub_condition.wait()

            return self._pubsub_queue.pop(0)

    def get_statistics(self) -> dict:
        """
        Get compression and connection statistics for this client.

        Returns:
            dict: A dictionary containing statistics with integer values:
                - total_connections: Total number of connections
                - total_clients: Total number of clients
                - total_values_compressed: Number of values successfully compressed
                - total_values_decompressed: Number of values successfully decompressed
                - total_original_bytes: Total bytes of original data before compression
                - total_bytes_compressed: Total bytes after compression
                - total_bytes_decompressed: Total bytes after decompression
                - compression_skipped_count: Number of times compression was skipped
                - subscription_out_of_sync_count: Failed reconciliation attempts
                - subscription_last_sync_timestamp: Last successful sync (milliseconds since epoch)
        """
        # Call the C FFI get_statistics function (returns by value, no manual free needed)
        stats = self._lib.get_statistics()

        # Access the struct fields and convert to a dictionary
        return {
            "total_connections": stats.total_connections,
            "total_clients": stats.total_clients,
            "total_values_compressed": stats.total_values_compressed,
            "total_values_decompressed": stats.total_values_decompressed,
            "total_original_bytes": stats.total_original_bytes,
            "total_bytes_compressed": stats.total_bytes_compressed,
            "total_bytes_decompressed": stats.total_bytes_decompressed,
            "compression_skipped_count": stats.compression_skipped_count,
            "subscription_out_of_sync_count": stats.subscription_out_of_sync_count,
            "subscription_last_sync_timestamp": stats.subscription_last_sync_timestamp,
        }

    def get_subscriptions(self):
        """Get subscription state (desired vs actual)."""
        result = self._execute_command(RequestType.GetSubscriptions, [])
        return self._parse_pubsub_state(
            result, is_cluster=isinstance(self, GlideClusterClient)
        )

    def _parse_pubsub_state(self, result, is_cluster):
        """Parse subscription state from Rust response."""
        if not isinstance(result, list) or len(result) != 4:
            raise RequestError("Invalid response format from GetSubscriptions")

        desired_dict = result[1]
        actual_dict = result[3]

        if is_cluster:
            from glide_shared.config import GlideClusterClientConfiguration

            PubSubChannelModes = GlideClusterClientConfiguration.PubSubChannelModes
            StateClass = GlideClusterClientConfiguration.PubSubState
            mode_map = {
                "Exact": PubSubChannelModes.Exact,
                "Pattern": PubSubChannelModes.Pattern,
                "Sharded": PubSubChannelModes.Sharded,
            }
        else:
            from glide_shared.config import GlideClientConfiguration

            PubSubChannelModes = GlideClientConfiguration.PubSubChannelModes
            StateClass = GlideClientConfiguration.PubSubState
            mode_map = {
                "Exact": PubSubChannelModes.Exact,
                "Pattern": PubSubChannelModes.Pattern,
            }

        desired_subscriptions = {}
        actual_subscriptions = {}

        for key_bytes, value_list in desired_dict.items():
            key = key_bytes.decode() if isinstance(key_bytes, bytes) else key_bytes
            if key in mode_map:
                values = {v.decode() if isinstance(v, bytes) else v for v in value_list}
                desired_subscriptions[mode_map[key]] = values

        for key_bytes, value_list in actual_dict.items():
            key = key_bytes.decode() if isinstance(key_bytes, bytes) else key_bytes
            if key in mode_map:
                values = {v.decode() if isinstance(v, bytes) else v for v in value_list}
                actual_subscriptions[mode_map[key]] = values

        return StateClass(
            desired_subscriptions=desired_subscriptions,
            actual_subscriptions=actual_subscriptions,
        )

    def _get_cache_metrics(self, metrics_type: int) -> TResult:
        """
        Get cache metrics.

        Args:
            metrics_type: Type of metric to retrieve (e.g., hit rate, miss rate).

        Returns:
            The requested cache metric.

        Raises:
            RequestError: If client-side caching is not enabled or metrics tracking is disabled.
        """
        if self._is_closed:
            raise ClosingError("Client is closed.")
        client_adapter_ptr = self._core_client
        if client_adapter_ptr == self._ffi.NULL:
            raise ValueError("Invalid client pointer.")

        result = self._lib.get_cache_metrics(
            client_adapter_ptr,
            0,  # Request ID (0 for sync use)
            metrics_type,
        )
        return self._handle_cmd_result(result)

    def close(self) -> None:
        with self._client_lock:
            if not self._is_closed:
                self._is_closed = True
                with self._pubsub_condition:
                    self._pubsub_condition.notify_all()
                self._lib.close_client(self._core_client)
                self._core_client = self._ffi.NULL
                self._pubsub_callback_ref = None

    def __enter__(self) -> Self:
        return self

    def __exit__(
        self,
        exc_type: Optional[type[BaseException]],
        exc: Optional[BaseException],
        tb: Optional[TracebackType],
    ) -> None:
        self.close()

    def scoped_connection(
        self, timeout: float = 5.0, routing_key: Optional[str] = None
    ) -> "IsolatedScope":
        """
        Acquire an isolated execution scope — a dedicated connection for operations
        requiring per-connection state (WATCH/MULTI/EXEC, CLIENT TRACKING, blocking
        commands).

        The scope bypasses the multiplexer, executing commands on its own TCP
        connection. Use as a context manager for automatic release:

            with client.scoped_connection(routing_key="my-key") as scope:
                scope.watch("my-key")
                val = scope.get("my-key")
                scope.multi()
                scope.set("my-key", str(int(val or "0") + 1))
                result = scope.exec()

        Args:
            timeout: Maximum seconds to wait for a scope connection (default 5.0).
            routing_key: In cluster mode, the key whose hash slot determines which
                node the scope connects to. All keys used in the scope must hash to
                the same slot. If None, defaults to slot 0.

        Returns:
            An IsolatedScope instance.

        Raises:
            TimeoutError: If no scope is available within the timeout.
            ClosingError: If the client is closed.
        """
        import time

        from .isolated_scope import IsolatedScope

        if self._is_closed:
            raise ClosingError("Client is closed.")

        # Use the pointer address as client_id for the scope pool
        client_id = int(self._ffi.cast("uintptr_t", self._core_client))
        conn_req_bytes = self._conn_req_bytes

        # Compute routing slot from key
        if routing_key is not None:
            routing_slot = _slot_for_key(routing_key.encode("utf-8"))
        else:
            routing_slot = 0

        deadline = time.monotonic() + timeout
        backoff = 0.01  # Start at 10ms (first scope needs ~500ms for TCP connect)

        while True:
            buf = self._ffi.from_buffer(conn_req_bytes)
            scope_id = self._lib.glide_scope_try_acquire(
                client_id,
                self._ffi.cast("const uint8_t*", buf),
                len(conn_req_bytes),
                routing_slot,
            )

            if scope_id >= 0:
                return IsolatedScope(
                    scope_id,
                    client_id,
                    _SYNC_FFI,
                    self._parse_scope_response,
                )

            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError(
                    "Timed out waiting for isolated scope (pool exhausted)"
                )

            time.sleep(min(backoff, remaining))
            backoff = min(backoff * 2, 0.5)  # Cap at 500ms

    def _parse_scope_response(self, response_ptr) -> Optional[str]:  # noqa: C901
        """Parse a CommandResponse pointer from a scope execution into a Python string."""
        if response_ptr == self._ffi.NULL:
            return None

        resp = response_ptr
        resp_type = resp.response_type

        # Null response
        if resp_type == 0:  # ResponseType::Null
            return None
        # Int
        elif resp_type == 1:  # ResponseType::Int
            return str(resp.int_value)
        # Float
        elif resp_type == 2:  # ResponseType::Float
            return str(resp.float_value)
        # Bool
        elif resp_type == 3:  # ResponseType::Bool
            return str(resp.bool_value)
        # String
        elif resp_type == 4:  # ResponseType::String
            if resp.string_value == self._ffi.NULL:
                return None
            return self._ffi.buffer(resp.string_value, resp.string_value_len)[:].decode(
                "utf-8"
            )
        # Array (for EXEC results, LRANGE, etc.)
        elif resp_type == 5:  # ResponseType::Array
            if resp.array_value == self._ffi.NULL or resp.array_value_len == 0:
                return None
            # For simple scope usage, return a string repr
            results = []
            for i in range(resp.array_value_len):
                elem = self._parse_scope_response(resp.array_value + i)
                results.append(elem)
            return str(results)
        # Ok
        elif resp_type == 8:  # ResponseType::Ok
            return "OK"
        # Error
        elif resp_type == 9:  # ResponseType::Error
            if resp.string_value != self._ffi.NULL:
                msg = self._ffi.string(resp.string_value).decode("utf-8")
                raise RuntimeError(f"Server error: {msg}")
            raise RuntimeError("Server error (unknown)")
        else:
            # Fallback for Map, Sets, etc.
            return None


class GlideClusterClient(BaseClient, ClusterCommands):
    """
    Client used for connection to cluster servers.
    For full documentation, see
    https://glide.valkey.io/how-to/client-initialization/#cluster
    """

    def _build_cluster_scan_args(self, match, count, type, allow_non_covered_slots):
        args = []
        if match is not None:
            # Inline _encode_arg logic
            if isinstance(match, str):
                encoded_match = match.encode(ENCODING)
            else:
                encoded_match = match
            args.extend([b"MATCH", encoded_match])

        if count is not None:
            args.extend([b"COUNT", str(count).encode(ENCODING)])
        if type is not None:
            args.extend([b"TYPE", type.value.encode(ENCODING)])
        if allow_non_covered_slots:
            args.extend([b"ALLOW_NON_COVERED_SLOTS"])

        return args

    def _cluster_scan(
        self,
        cursor: ClusterScanCursor,
        match: Optional[TEncodable] = None,
        count: Optional[int] = None,
        type: Optional[ObjectType] = None,
        allow_non_covered_slots: bool = False,
    ) -> List[Union[ClusterScanCursor, List[bytes]]]:
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )

        client_adapter_ptr = self._core_client
        if client_adapter_ptr == self._ffi.NULL:
            raise ValueError("Invalid client pointer.")

        # Use helper method to build args
        args = self._build_cluster_scan_args(
            match, count, type, allow_non_covered_slots
        )
        # Convert cursor to C string
        cursor_string = cursor.get_cursor()
        cursor_bytes = cursor_string.encode(ENCODING) + b"\0"  # Null terminate for C

        # Keep references to prevent GC
        temp_buffers: List[Any] = [cursor_bytes]
        cursor_buffer = self._ffi.from_buffer(cursor_bytes)

        # Prepare FFI arguments
        if args:
            args_array, args_len_array, arg_buffers = self._to_c_strings(args)
            temp_buffers.extend(arg_buffers)  # Keep references alive
            arg_count = len(args)
        else:
            args_array = self._ffi.NULL
            args_len_array = self._ffi.NULL
            arg_count = 0

        result_ptr = self._lib.request_cluster_scan(
            client_adapter_ptr,
            0,
            cursor_buffer,
            arg_count,
            args_array,
            args_len_array,
        )

        response_data = self._handle_cmd_result(result_ptr)

        if not isinstance(response_data, list) or len(response_data) != 2:
            raise RequestError("Unexpected cluster scan response format")

        new_cursor = response_data[0]
        if isinstance(new_cursor, bytes):
            new_cursor = new_cursor.decode(ENCODING)

        keys_list = response_data[1] if response_data[1] is not None else []

        return [ClusterScanCursor(new_cursor), keys_list]


class GlideClient(BaseClient, StandaloneCommands):
    """
    Client used for connection to standalone servers.
    For full documentation, see
    https://glide.valkey.io/how-to/client-initialization/#standalone
    """

    pass


TGlideClient = Union[GlideClient, GlideClusterClient]
