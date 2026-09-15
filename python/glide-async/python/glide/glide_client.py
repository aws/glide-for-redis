# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import asyncio
import itertools
import os
import struct
import sys
import threading
from typing import (
    TYPE_CHECKING,
    Any,
    Dict,
    List,
    Optional,
    Tuple,
    Union,
    cast,
)

if TYPE_CHECKING:
    from .isolated_scope import AsyncIsolatedScope

import sniffio

try:
    import anyio

    HAS_ANYIO = True
except ImportError:
    HAS_ANYIO = False

try:
    import trio

    HAS_TRIO = True
except ImportError:
    HAS_TRIO = False

from glide._ffi_wrappers import ClusterScanCursor
from glide_shared._fast_response import parse_response as _c_parse_response
from glide_shared._glide_ffi import _GlideFFI
from glide_shared.commands.command_args import ObjectType
from glide_shared.commands.core_options import PubSubMsg
from glide_shared.config import (
    BaseClientConfiguration,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    ServerCredentials,
)
from glide_shared.connection_request import _create_async_connection_request
from glide_shared.constants import (
    OK,
    TEncodable,
    TResult,
)
from glide_shared.exceptions import (
    ClosingError,
    ConfigurationError,
    RequestError,
    get_request_error_class,
)
from glide_shared.ffi_helpers import (
    ENCODING,
    FFIClientTypeEnum,
    convert_commands_to_c_batch_info,
    create_c_batch_options,
    to_c_route_ptr_and_len,
    to_c_strings,
)
from glide_shared.opentelemetry import _create_batch_span, _create_command_span
from glide_shared.routes import Route

from .async_commands.cluster_commands import ClusterCommands
from .async_commands.core import CoreCommands, RequestType
from .async_commands.standalone_commands import StandaloneCommands
from .logger import Level as LogLevel
from .logger import Logger as ClientLogger
from .opentelemetry import OpenTelemetry

_ASYNC_FFI = _GlideFFI()  # Async client's own FFI instance


if sys.version_info >= (3, 11):
    from typing import Self
else:
    from typing_extensions import Self


# Pre-allocated null-terminated span name for the EVALSHA (`_execute_script`)
# path. Kept at module scope so we do not re-allocate a `char[]` per sampled
# call. `_ASYNC_FFI.ffi` is a process-wide singleton so this buffer is safe to
# share across clients.
_EVALSHA_SPAN_NAME = _ASYNC_FFI.ffi.new("char[]", b"EVALSHA")


# ==================== Framework-Agnostic Future ====================


class _CompatFuture:
    """anyio shim for asyncio.Future-like functionality (used for trio support).

    ``_is_done`` is an ``anyio.Event``, which under trio is a ``trio.Event``.
    trio primitives are not thread-safe: setting one from a foreign thread
    marks it set but never reschedules the parked waiter, so the awaiting task
    hangs forever.  ``_token`` captures the owning ``trio.run()`` at creation
    time so completions arriving on another thread are trampolined back onto
    the trio thread via ``run_sync_soon``.
    """

    def __init__(self) -> None:
        if not HAS_ANYIO:
            raise RuntimeError(
                "anyio is required for trio support. Install it with: pip install anyio"
            )
        self._is_done = anyio.Event()
        self._result: Any = None
        self._exception: Optional[Exception] = None
        self._token: Optional[Any] = None
        if HAS_TRIO:
            try:
                self._token = trio.lowlevel.current_trio_token()
            except RuntimeError:
                # Not running under trio (asyncio also uses _CompatFuture on
                # some paths).  anyio.Event is then an asyncio primitive and
                # _resolve_future handles thread affinity on the caller side.
                pass

    def _mark_done(self) -> None:
        self._is_done.set()

    def _set_done_threadsafe(self) -> None:
        """Set the completion event, hopping to the trio thread when needed."""
        if self._token is None or trio.lowlevel.in_trio_task():
            self._is_done.set()
            return
        try:
            self._token.run_sync_soon(self._mark_done)
        except trio.RunFinishedError:
            # The owning trio.run() already exited; nobody is left to wake.
            pass

    def set_result(self, result: Any) -> None:
        self._result = result
        self._set_done_threadsafe()

    def set_exception(self, exception: Exception) -> None:
        self._exception = exception
        self._set_done_threadsafe()

    def done(self) -> bool:
        return self._is_done.is_set()

    def __await__(self):
        yield from self._is_done.wait().__await__()
        if self._exception:
            raise self._exception
        return self._result

    def result(self) -> Any:
        if self._exception:
            raise self._exception
        return self._result


TFuture = Union[asyncio.Future, "_CompatFuture"]


def _get_new_future_instance() -> "TFuture":
    """Create a framework-appropriate future instance."""
    try:
        if sniffio.current_async_library() == "asyncio":
            return asyncio.get_running_loop().create_future()
    except sniffio.AsyncLibraryNotFoundError:
        return asyncio.get_running_loop().create_future()

    # _CompatFuture is also compatible with asyncio, but introduces performance
    # degradation, so we only use it for trio/other frameworks
    return _CompatFuture()


def _slot_for_key(key: bytes) -> int:
    """Compute the Redis cluster hash slot for a key (CRC16 mod 16384).

    Handles hash tags: if the key contains {...}, only the content between the
    first { and first } after it is hashed.
    """
    # Extract hash tag if present
    start = key.find(b"{")
    if start != -1:
        end = key.find(b"}", start + 1)
        if end != -1 and end != start + 1:
            key = key[start + 1 : end]

    # CRC16-CCITT (XMODEM)
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


_async_pipe_read_fd: int = -1
_async_pipe_write_fd: int = -1
_async_pipe_init_pid: int = -1
_next_client_id = itertools.count(1)


_async_pipe_registered: bool = False
_async_pipe_loop: Optional[asyncio.AbstractEventLoop] = (
    None  # loop that owns the reader
)
# Identity of the trio.run() that owns the reader system task.  A given
# reader lives for exactly one trio.run(); the token is set eagerly (under
# _async_pipe_lock, before the spawned task starts) so registration is
# idempotent within a run and re-registers cleanly across runs.  This is
# what guarantees at most one _trio_pipe_reader ever waits on the shared fd:
# trio raises BusyResourceError if two tasks wait on the same fd at once.
_trio_pipe_token: Optional[object] = None
_async_pipe_lock = threading.Lock()
_client_registry: dict = {}
_pipe_remainder: bytes = b""
_FRAME_STRUCT = struct.Struct("=QQQQ")  # Pre-compiled for hot path
_PUBSUB_SENTINEL = 0xFFFFFFFFFFFFFFFF  # request_id sentinel for pubsub frames

# Free-threading support: detect no-GIL builds for thread-safe data structure access.
# Response parsing runs on the event loop thread for correctness. Under free-threading,
# the _FREE_THREADED flag enables explicit locking on shared data structures.
_FREE_THREADED: bool = hasattr(sys, "_is_gil_enabled") and not sys._is_gil_enabled()

# Thread pool for parallel response parsing on free-threaded builds.
# Large responses (MGET, LRANGE) benefit from parsing across cores.
_response_thread_pool = None
if _FREE_THREADED:
    from concurrent.futures import ThreadPoolExecutor

    _response_thread_pool = ThreadPoolExecutor(
        max_workers=min(os.cpu_count() or 4, 8),
        thread_name_prefix="glide-resp-parser",
    )


def _free_orphaned_frame(request_id, response_ptr, arena_or_err):
    """Free resources from a pipe frame whose client has been closed."""
    if request_id == _PUBSUB_SENTINEL:
        if arena_or_err & (1 << 63):
            # Pointer-mode pubsub: free the heap-allocated payload
            payload_len = arena_or_err & 0x7FFFFFFFFFFFFFFF
            any_c = next(iter(_client_registry.values()), None)
            if any_c:
                any_c._lib.free_pubsub_pointer_payload(
                    any_c._ffi.cast("uint8_t*", response_ptr), payload_len
                )
        return
    any_c = next(iter(_client_registry.values()), None)
    if any_c is None:
        return
    try:
        if response_ptr != 0 and arena_or_err != 0:
            any_c._lib.free_response_arena(any_c._ffi.cast("void*", arena_or_err))
        elif response_ptr == 0 and arena_or_err != 0:
            err_ptr = arena_or_err & 0x00FFFFFFFFFFFFFF
            if err_ptr:
                any_c._lib.free_pipe_error_string(any_c._ffi.cast("char*", err_ptr))
    except Exception:
        pass


def _resolve_future(fut, result, client):
    """Resolve a future with a result or exception, handling cross-loop/thread dispatch."""
    if isinstance(fut, _CompatFuture):
        (
            fut.set_exception(result)
            if isinstance(result, Exception)
            else fut.set_result(result)
        )
    elif _FREE_THREADED and client._loop is not None:
        # Under free-threading, handlers may run on thread pool workers.
        # Must use call_soon_threadsafe to resolve futures on the event loop thread.
        if isinstance(result, Exception):
            client._loop.call_soon_threadsafe(fut.set_exception, result)
        else:
            client._loop.call_soon_threadsafe(fut.set_result, result)
    elif client._loop and client._loop != _async_pipe_loop:
        if isinstance(result, Exception):
            client._loop.call_soon_threadsafe(fut.set_exception, result)
        else:
            client._loop.call_soon_threadsafe(fut.set_result, result)
    elif isinstance(result, Exception):
        fut.set_exception(result)
    else:
        fut.set_result(result)


def _handle_pipe_success(client, request_id, response_ptr, arena_or_err):
    """Handle a success frame from the shared pipe (event loop thread)."""
    if client._is_closed:
        if arena_or_err and response_ptr != 0:
            try:
                client._lib.free_response_arena(client._ffi.cast("void*", arena_or_err))
            except Exception:
                pass
        return
    try:
        if response_ptr == 0:
            result = None
        else:
            result, _ = _c_parse_response(response_ptr)
    except Exception as e:
        result = e
    finally:
        if arena_or_err:
            client._lib.free_response_arena(client._ffi.cast("void*", arena_or_err))
    if _FREE_THREADED:
        with client._lock:
            fut = client._pending_futures.pop(request_id, None)
    else:
        fut = client._pending_futures.pop(request_id, None)
    if fut is not None and not fut.done():
        _resolve_future(fut, result, client)


def _handle_pipe_error(client, request_id, arena_or_err):
    """Handle an error frame from the shared pipe."""
    error_type = (arena_or_err >> 56) & 0xFF
    err_ptr = arena_or_err & 0x00FFFFFFFFFFFFFF
    if client._is_closed:
        if err_ptr:
            try:
                client._lib.free_pipe_error_string(client._ffi.cast("char*", err_ptr))
            except Exception:
                pass
        return
    msg = "Unknown error"
    if err_ptr:
        try:
            msg = client._ffi.string(client._ffi.cast("char*", err_ptr)).decode("utf-8")
        except Exception:
            pass
        finally:
            client._lib.free_pipe_error_string(client._ffi.cast("char*", err_ptr))
    exc = get_request_error_class(error_type)(msg)
    if _FREE_THREADED:
        with client._lock:
            fut = client._pending_futures.pop(request_id, None)
    else:
        fut = client._pending_futures.pop(request_id, None)
    if fut is not None and not fut.done():
        _resolve_future(fut, exc, client)


def _handle_pointer_pubsub(client, ptr_val: int, payload_len: int):
    """Handle pointer-mode pubsub data (large messages delivered via heap pointer)."""
    try:
        ffi = client._ffi
        buf_ptr = ffi.cast("uint8_t*", ptr_val)
        payload = bytes(ffi.buffer(buf_ptr, payload_len))
        _handle_inline_pubsub(client, payload)
    except Exception as e:
        ClientLogger.log(
            LogLevel.ERROR, "pubsub_pipe", f"Error handling pointer pubsub: {e}"
        )
    finally:
        client._lib.free_pubsub_pointer_payload(
            client._ffi.cast("uint8_t*", ptr_val), payload_len
        )


def _handle_inline_pubsub(client, payload: bytes):
    """Handle inline pubsub data from the pipe (no native pointers)."""
    try:
        from glide_shared.ffi_helpers import parse_inline_pubsub

        kind_str, message, channel, pattern = parse_inline_pubsub(payload)
        if kind_str == "Disconnection":
            ClientLogger.log(
                LogLevel.WARN,
                "disconnect notification",
                "Transport disconnected, messages might be lost",
            )
        elif kind_str in ("Message", "PMessage", "SMessage"):
            pubsub_msg = PubSubMsg(message=message, channel=channel, pattern=pattern)
            with client._pubsub_lock:
                user_callback, context = (
                    client.config._get_pubsub_callback_and_context()
                )
                if not user_callback:
                    client._pending_push_notifications.append(pubsub_msg)
                    client._complete_pubsub_futures_safe()
            if user_callback:
                user_callback(pubsub_msg, context)
        elif kind_str is None:
            ClientLogger.log(
                LogLevel.WARN,
                "pubsub_pipe",
                f"Unknown push notification kind received: {payload[:4]!r}",
            )
    except Exception as e:
        ClientLogger.log(
            LogLevel.ERROR, "pubsub_pipe", f"Error handling pubsub frame: {e}"
        )


def _detect_fork_and_reset() -> None:
    """Detect if we are in a forked child and reset pipe state.

    After fork(), the flush thread is dead (threads don't survive fork) but
    the pipe fd and Rust OnceLock state are inherited. Reset the Python-side
    state so _setup_pipe will create a fresh pipe and call reinit_async_pipe.
    Must be called while holding _async_pipe_lock.
    """
    global _async_pipe_read_fd, _async_pipe_write_fd
    global _async_pipe_registered, _async_pipe_loop
    global _pipe_remainder, _trio_pipe_token
    current_pid = os.getpid()
    if (
        _async_pipe_read_fd >= 0
        and _async_pipe_init_pid > 0
        and current_pid != _async_pipe_init_pid
    ):
        try:
            os.close(_async_pipe_read_fd)
        except OSError:
            pass
        if _async_pipe_write_fd >= 0:
            try:
                os.close(_async_pipe_write_fd)
            except OSError:
                pass
        _async_pipe_read_fd = -1
        _async_pipe_write_fd = -1
        _async_pipe_registered = False
        _async_pipe_loop = None
        _pipe_remainder = b""
        _trio_pipe_token = None
        _client_registry.clear()


def _drain_stale_pipe_frames():
    """Drain stale frames from the pipe to prevent reading freed pointers."""
    while True:
        try:
            stale = os.read(_async_pipe_read_fd, 32 * 256)
            if not stale:
                break
        except (BlockingIOError, OSError):
            break


def _on_async_pipe_readable() -> None:  # noqa: C901
    # Free-threading optimization: when GIL is disabled, dispatch response parsing
    # to a thread pool for parallel execution across cores. With GIL enabled,
    # parse serially on the event loop thread (thread pool overhead not worth it).
    global _pipe_remainder
    try:
        data = os.read(_async_pipe_read_fd, 32 * 512)
    except (BlockingIOError, OSError):
        return
    if not data:
        return
    if _pipe_remainder:
        data = _pipe_remainder + data
        _pipe_remainder = b""
    offset = 0
    while offset + 32 <= len(data):
        client_id, request_id, response_ptr, arena_or_err = _FRAME_STRUCT.unpack_from(
            data, offset
        )
        offset += 32
        client = _client_registry.get(client_id)
        if request_id == _PUBSUB_SENTINEL:
            if arena_or_err & (1 << 63):
                # Pointer-mode: large message delivered via heap pointer
                payload_len = arena_or_err & 0x7FFFFFFFFFFFFFFF
                if client is not None:
                    _handle_pointer_pubsub(client, response_ptr, payload_len)
                else:
                    any_c = next(iter(_client_registry.values()), None)
                    if any_c:
                        any_c._lib.free_pubsub_pointer_payload(
                            any_c._ffi.cast("uint8_t*", response_ptr), payload_len
                        )
            else:
                # Inline pubsub: response_ptr = payload_len, data follows header
                payload_len = response_ptr
                if offset + payload_len > len(data):
                    # Incomplete payload — put header + remaining back
                    offset -= 32
                    break
                if client is not None:
                    _handle_inline_pubsub(client, data[offset : offset + payload_len])
                offset += payload_len
            continue
        if client is None:
            _free_orphaned_frame(request_id, response_ptr, arena_or_err)
            continue
        if response_ptr != 0:
            if _FREE_THREADED and _response_thread_pool is not None:
                _response_thread_pool.submit(
                    _handle_pipe_success, client, request_id, response_ptr, arena_or_err
                )
            else:
                _handle_pipe_success(client, request_id, response_ptr, arena_or_err)
        else:
            if _FREE_THREADED and _response_thread_pool is not None:
                _response_thread_pool.submit(
                    _handle_pipe_error, client, request_id, arena_or_err
                )
            else:
                _handle_pipe_error(client, request_id, arena_or_err)
    if offset < len(data):
        _pipe_remainder = data[offset:]


async def _trio_pipe_reader(pipe_fd: int, token: object) -> None:
    """Background trio task that reads from the shared pipe.

    ``token`` identifies the trio.run() this reader was spawned for.  On exit
    it clears the shared registration only if it still owns that token, so a
    reader that is cancelled while a newer trio.run() has already registered
    its own reader cannot wipe the newer run's state.
    """
    import trio

    global _trio_pipe_token, _async_pipe_registered
    try:
        while True:
            await trio.lowlevel.wait_readable(pipe_fd)
            try:
                _on_async_pipe_readable()
            except trio.Cancelled:
                raise
            except Exception as e:
                ClientLogger.log(LogLevel.ERROR, "trio_pipe", f"Pipe read error: {e}")
    finally:
        with _async_pipe_lock:
            if _trio_pipe_token is token:
                _trio_pipe_token = None
                _async_pipe_registered = False


class BaseClient(CoreCommands):
    def __init__(self, config: BaseClientConfiguration):
        """To create a new client, use the `create` classmethod"""
        _glide_ffi = _ASYNC_FFI
        self._ffi = _glide_ffi.ffi
        self._lib = _glide_ffi.lib
        self.config: BaseClientConfiguration = config
        self._is_closed: bool = False
        self._core_client = None
        self._conn_req_bytes: bytes = b""
        self._pubsub_callback_ref = None
        self._loop: Optional[asyncio.AbstractEventLoop] = None  # set in create()
        self._pending_futures: Dict[int, "TFuture"] = {}
        self._callback_id_gen = itertools.count(1)
        self._lock = threading.Lock()
        self._address_resolver_callback_ref = None
        self._pubsub_futures: List["TFuture"] = []
        self._pubsub_lock = threading.Lock()
        self._pending_push_notifications: List[PubSubMsg] = []
        self._pipe_client_id: int = 0
        self._create_pid: int = 0
        self._is_asyncio: bool = True

    @classmethod
    async def create(cls, config: BaseClientConfiguration) -> Self:
        """Creates a Glide client.

        Args:
            config (ClientConfiguration): The configuration options for the client.

        Returns:
            Self: A promise that resolves to a connected client instance.
        """
        self = cls(config)

        try:
            self._is_asyncio = sniffio.current_async_library() == "asyncio"
        except sniffio.AsyncLibraryNotFoundError:
            self._is_asyncio = True

        self._loop = asyncio.get_running_loop() if self._is_asyncio else None

        # Build connection request
        conn_req = _create_async_connection_request(config)
        conn_req_bytes = conn_req.SerializeToString()
        # Store for scoped_connection
        self._conn_req_bytes = conn_req_bytes

        # Create AsyncClient type
        client_type = self._ffi.new(
            "ClientType*",
            {
                "_type": self._ffi.cast("ClientTypeEnum", FFIClientTypeEnum.Async),
                "async_client": {
                    "success_callback": self._lib.noop_success_callback,
                    "failure_callback": self._lib.noop_failure_callback,
                    "allow_stack_response": False,
                },
            },
        )

        # Pubsub messages are delivered via the shared pipe — no callback needed.
        pubsub_callback = self._ffi.cast("PubSubCallback", 0)

        # Create address resolver callback if configured
        from glide_shared.ffi_helpers import create_address_resolver_callback

        address_resolver_callback = create_address_resolver_callback(
            self._ffi, self.config.address_resolver
        )
        if self.config.address_resolver is not None:
            self._address_resolver_callback_ref = address_resolver_callback

        # Set pipe_client_id before create_client so Rust routes responses
        # through the pipe from the very first command — no race window.
        self._pipe_client_id = next(_next_client_id)
        self._create_pid = os.getpid()

        client_response_ptr = self._lib.create_client(
            conn_req_bytes,
            len(conn_req_bytes),
            client_type,
            pubsub_callback,
            address_resolver_callback,
            self._pipe_client_id,
        )

        ClientLogger.log(LogLevel.INFO, "connection info", "new connection established")

        if client_response_ptr == self._ffi.NULL:
            raise ClosingError("Failed to create client, response pointer is NULL.")

        client_response = self._ffi.cast("ConnectionResponse*", client_response_ptr)
        if client_response.conn_ptr != self._ffi.NULL:
            self._core_client = client_response.conn_ptr
        else:
            error_msg = (
                self._ffi.string(client_response.connection_error_message).decode(
                    ENCODING
                )
                if client_response.connection_error_message != self._ffi.NULL
                else "Unknown error"
            )
            self._lib.free_connection_response(client_response_ptr)
            raise ClosingError(error_msg)

        self._lib.free_connection_response(client_response_ptr)

        self._setup_pipe()

        return self

    def _setup_pipe(self) -> None:
        """Initialize and register the shared response pipe."""
        global _async_pipe_read_fd, _async_pipe_write_fd
        global _async_pipe_registered, _async_pipe_loop
        global _pipe_remainder, _trio_pipe_token, _async_pipe_init_pid
        # Identify the current trio.run() up front (outside the lock).  The
        # token uniquely names this run, so it both makes registration
        # idempotent within a run and tells a fresh run that a prior run's
        # registration is stale.
        trio_token = None
        if not self._is_asyncio:
            import trio

            trio_token = trio.lowlevel.current_trio_token()
        with _async_pipe_lock:
            _detect_fork_and_reset()
            current_pid = os.getpid()

            if _async_pipe_read_fd < 0:
                try:
                    _async_pipe_read_fd, pw = os.pipe()
                    os.set_blocking(_async_pipe_read_fd, False)
                    if _async_pipe_init_pid > 0 and current_pid != _async_pipe_init_pid:
                        self._lib.reinit_async_pipe(pw)
                    else:
                        self._lib.init_async_pipe(pw)
                    _async_pipe_write_fd = pw
                    _async_pipe_init_pid = current_pid
                except OSError:
                    _async_pipe_read_fd = -1
                    self._pipe_client_id = 0
            # Detect stale registration: the loop that originally called
            # add_reader has been closed/destroyed (e.g. between anyio.run()
            # calls in benchmarks).  Reset so we re-register below.
            if _async_pipe_registered and _async_pipe_loop is not None:
                if _async_pipe_loop.is_closed():
                    _async_pipe_registered = False
                    _async_pipe_loop = None
                    _pipe_remainder = b""
                    _drain_stale_pipe_frames()
            # Trio: registration belongs to exactly one trio.run().  If the
            # recorded token differs from this run's, the previous run's reader
            # is gone (trio.run() cancels its system tasks before returning), so
            # re-register for this run.  Keying on the token instead of a
            # lazily-set liveness flag prevents a second client in the same run
            # from spawning a duplicate reader on the shared fd, which is what
            # triggered BusyResourceError.
            if (
                not self._is_asyncio
                and _async_pipe_registered
                and _async_pipe_loop is None
                and _trio_pipe_token is not trio_token
            ):
                _async_pipe_registered = False
                _trio_pipe_token = None
                _pipe_remainder = b""
                _drain_stale_pipe_frames()
            if _async_pipe_read_fd >= 0 and self._pipe_client_id:
                _client_registry[self._pipe_client_id] = self
                if not _async_pipe_registered:
                    if self._is_asyncio:
                        assert self._loop is not None
                        self._loop.add_reader(
                            _async_pipe_read_fd, _on_async_pipe_readable
                        )
                        _async_pipe_loop = self._loop
                    else:
                        # For trio: spawn a background task that polls the pipe.
                        # Record the token before spawning (still under the
                        # lock) so a concurrent _setup_pipe in this same run
                        # sees us as registered and does not spawn a second
                        # reader on the same fd.
                        import trio

                        _trio_pipe_token = trio_token
                        trio.lowlevel.spawn_system_task(
                            _trio_pipe_reader, _async_pipe_read_fd, trio_token
                        )
                    _async_pipe_registered = True

    # ==================== Callback Handling ====================

    def _get_callback_id(self) -> int:
        return next(self._callback_id_gen)

    def _register_future(self, callback_id: int, fut: "TFuture") -> None:
        """Register a pending future (thread-safe for free-threading)."""
        if _FREE_THREADED:
            with self._lock:
                self._pending_futures[callback_id] = fut
        else:
            self._pending_futures[callback_id] = fut

    def _complete_pubsub_futures_safe(self):
        """Complete pending pubsub futures with available messages. Must hold _pubsub_lock."""
        loop = self._loop
        while self._pending_push_notifications and self._pubsub_futures:
            fut = self._pubsub_futures[0]
            if fut.done():
                self._pubsub_futures.pop(0)
                continue
            msg = self._pending_push_notifications.pop(0)
            self._pubsub_futures.pop(0)
            if isinstance(fut, _CompatFuture):
                fut.set_result(msg)
            elif loop and not loop.is_closed():
                loop.call_soon_threadsafe(fut.set_result, msg)

    async def get_pubsub_message(self) -> PubSubMsg:
        if self._is_closed:
            raise ClosingError("Client is closed.")
        self._check_same_process()
        if self.config._get_pubsub_callback_and_context()[0] is not None:
            raise ConfigurationError(
                "The operation will never complete since messages will be passed to the configured callback."
            )
        fut: "TFuture" = _get_new_future_instance()
        with self._pubsub_lock:
            self._pubsub_futures.append(fut)
            self._complete_pubsub_futures_safe()
        return await fut

    def try_get_pubsub_message(self) -> Optional[PubSubMsg]:
        if self._is_closed:
            raise ClosingError("Client is closed.")
        self._check_same_process()
        if self.config._get_pubsub_callback_and_context()[0] is not None:
            raise ConfigurationError(
                "The operation will never complete since messages will be passed to the configured callback."
            )
        with self._pubsub_lock:
            if self._pending_push_notifications:
                return self._pending_push_notifications.pop(0)
            return None

    # ==================== Response Parsing ====================

    def _handle_response(self, message):
        """Parse a CommandResponse pointer into a Python object.

        For the async client, NULL means no response (returns None) and the arena
        is freed here since responses arrive via the pipe path without automatic
        cleanup. The sync client's _handle_response raises on NULL and relies on
        free_command_result to free the arena.
        """
        if message == self._ffi.NULL:
            return None
        addr = int(self._ffi.cast("uintptr_t", message))
        result, _arena_ptr = _c_parse_response(addr)
        # Arena is freed by the caller (free_command_result for sync path,
        # explicit free_response_arena for pipe path)
        return result

    # ==================== FFI Helpers ====================

    def _to_c_strings(self, args):
        return to_c_strings(self._ffi, args)

    def _to_c_route_ptr_and_len(self, route):
        return to_c_route_ptr_and_len(self._ffi, route)

    # ==================== Command Execution ====================

    def _check_same_process(self) -> None:
        """Raise if this client is used from a forked child process."""
        if self._create_pid and self._create_pid != os.getpid():
            raise ClosingError(
                "Cannot use a client created before fork(). "
                "Create a new client in the child process."
            )

    async def _execute_command(
        self,
        request_type: int,
        args: List[TEncodable],
        route: Optional[Route] = None,
    ) -> TResult:
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )
        self._check_same_process()

        callback_id = self._get_callback_id()
        fut = _get_new_future_instance()

        self._register_future(callback_id, fut)

        c_args, c_lengths, buffers = self._to_c_strings(args)

        # Sample before reading the caller's context. A sampled command span uses
        # the active OTel span as its parent when one is available.
        span = 0
        if OpenTelemetry.is_tracing_enabled() and OpenTelemetry.should_sample():
            parent_ctx = OpenTelemetry._get_parent_span_context()
            span_name_cstr = self._ffi.new(
                "char[]", RequestType.Name(request_type).encode()
            )
            span = _create_command_span(
                self._ffi, self._lib, span_name_cstr, parent_ctx
            )

        try:
            if route is None:
                self._lib.command(
                    self._core_client,
                    callback_id,
                    request_type,
                    len(args),
                    c_args,
                    c_lengths,
                    self._ffi.NULL,
                    0,
                    span,
                )
            else:
                route_ptr, route_len, route_bytes = self._to_c_route_ptr_and_len(route)
                self._lib.command(
                    self._core_client,
                    callback_id,
                    request_type,
                    len(args),
                    c_args,
                    c_lengths,
                    route_ptr,
                    route_len,
                    span,
                )

            return await fut
        finally:
            if span:
                self._lib.drop_otel_span(span)

    async def _execute_batch(
        self,
        commands: List[Tuple[int, List[TEncodable]]],
        is_atomic: bool,
        raise_on_error: bool = False,
        retry_server_error: bool = False,
        retry_connection_error: bool = False,
        route: Optional[Route] = None,
        timeout: Optional[int] = None,
    ) -> List[TResult]:
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )
        self._check_same_process()

        callback_id = self._get_callback_id()
        fut = _get_new_future_instance()

        self._register_future(callback_id, fut)

        span = 0
        if OpenTelemetry.is_tracing_enabled() and OpenTelemetry.should_sample():
            parent_ctx = OpenTelemetry._get_parent_span_context()
            span = _create_batch_span(self._ffi, self._lib, parent_ctx)

        try:
            batch_info, batch_refs = convert_commands_to_c_batch_info(
                self._ffi, commands, is_atomic
            )
            batch_options, opts_refs = create_c_batch_options(
                self._ffi,
                route,
                retry_server_error=retry_server_error,
                retry_connection_error=retry_connection_error,
                timeout=timeout,
            )
            _refs = batch_refs + opts_refs  # noqa: F841  prevent GC

            self._lib.batch(
                self._core_client,
                callback_id,
                batch_info,
                raise_on_error,
                batch_options,
                span,
            )

            return await fut
        finally:
            if span != 0:
                self._lib.drop_otel_span(span)

    async def _execute_script(
        self,
        hash: str,
        keys: Optional[List[TEncodable]] = None,
        args: Optional[List[TEncodable]] = None,
        route: Optional[Route] = None,
    ) -> TResult:
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )
        self._check_same_process()

        callback_id = self._get_callback_id()
        fut = _get_new_future_instance()

        self._register_future(callback_id, fut)

        if keys is None:
            keys = []
        if args is None:
            args = []

        keys_c_args, keys_c_lengths, keys_buffers = self._to_c_strings(keys)
        args_c_args, args_c_lengths, args_buffers = self._to_c_strings(args)

        hash_bytes = hash.encode(ENCODING) + b"\0"
        hash_buffer = self._ffi.from_buffer(hash_bytes)

        route_ptr, route_len, route_bytes = self._to_c_route_ptr_and_len(route)

        # OTel span creation only when initialized (rare). The core attaches the
        # EVALSHA DB semantic convention attributes to the span via invoke_script.
        span = 0
        if OpenTelemetry.is_tracing_enabled() and OpenTelemetry.should_sample():
            parent_ctx = OpenTelemetry._get_parent_span_context()
            span = _create_command_span(
                self._ffi, self._lib, _EVALSHA_SPAN_NAME, parent_ctx
            )

        try:
            self._lib.invoke_script(
                self._core_client,
                callback_id,
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
            return await fut
        finally:
            if span != 0:
                self._lib.drop_otel_span(span)

    # ==================== Cache Metrics ====================

    def _get_cache_metrics(self, metrics_type: int) -> "TResult":
        """
        Get cache metrics via FFI.

        Args:
            metrics_type: Type of metric to retrieve.

        Returns:
            The requested cache metric.

        Raises:
            RequestError: If client-side caching is not enabled or metrics tracking is disabled.
        """
        if self._is_closed:
            raise ClosingError("Client is closed.")
        self._check_same_process()
        if self._core_client == self._ffi.NULL:
            raise ValueError("Invalid client pointer.")

        from glide_shared.ffi_helpers import handle_command_result

        command_result = self._lib.get_cache_metrics(self._core_client, 0, metrics_type)
        return handle_command_result(
            self._ffi, self._lib, command_result, self._handle_response
        )

    # ==================== Connection Management ====================

    async def _update_connection_password(
        self, password: Optional[str], immediate_auth: bool
    ) -> TResult:
        if self._is_closed:
            raise ClosingError("Client is closed.")
        self._check_same_process()

        callback_id = self._get_callback_id()
        fut = _get_new_future_instance()

        self._register_future(callback_id, fut)

        c_password = (
            self._ffi.new("char[]", password.encode(ENCODING))
            if password is not None
            else self._ffi.new("char[]", b"")
        )

        self._lib.update_connection_password(
            self._core_client,
            callback_id,
            c_password,
            immediate_auth,
        )

        result = await fut
        if result is OK:
            if self.config.credentials is None:
                self.config.credentials = ServerCredentials(password=password or "")
            self.config.credentials.password = password or ""
        return result

    async def _refresh_iam_token(self) -> TResult:
        if self._is_closed:
            raise ClosingError("Client is closed.")
        self._check_same_process()

        callback_id = self._get_callback_id()
        fut = _get_new_future_instance()

        self._register_future(callback_id, fut)

        self._lib.refresh_iam_token(
            self._core_client,
            callback_id,
        )

        return await fut

    async def get_statistics(self) -> dict:
        stats = self._lib.get_statistics()
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

    def _parse_pubsub_state(self, result: TResult, is_cluster: bool):
        if not isinstance(result, list) or len(result) != 4:
            raise RequestError("Invalid response format from GetSubscriptions")

        desired_dict = result[1]
        actual_dict = result[3]

        if is_cluster:
            PubSubChannelModes = GlideClusterClientConfiguration.PubSubChannelModes
            StateClass = GlideClusterClientConfiguration.PubSubState
            mode_map = {
                "Exact": PubSubChannelModes.Exact,
                "Pattern": PubSubChannelModes.Pattern,
                "Sharded": PubSubChannelModes.Sharded,
            }
        else:
            PubSubChannelModes = GlideClientConfiguration.PubSubChannelModes  # type: ignore[assignment]
            StateClass = GlideClientConfiguration.PubSubState  # type: ignore[assignment]
            mode_map = {
                "Exact": PubSubChannelModes.Exact,
                "Pattern": PubSubChannelModes.Pattern,
            }

        desired_subscriptions = {}
        actual_subscriptions = {}

        for key_bytes, value_list in desired_dict.items():  # type: ignore[union-attr]
            key = key_bytes.decode() if isinstance(key_bytes, bytes) else key_bytes
            if key in mode_map:
                values = {v.decode() if isinstance(v, bytes) else v for v in value_list}
                desired_subscriptions[mode_map[key]] = values

        for key_bytes, value_list in actual_dict.items():  # type: ignore[union-attr]
            key = key_bytes.decode() if isinstance(key_bytes, bytes) else key_bytes
            if key in mode_map:
                values = {v.decode() if isinstance(v, bytes) else v for v in value_list}
                actual_subscriptions[mode_map[key]] = values

        return StateClass(
            desired_subscriptions=desired_subscriptions,
            actual_subscriptions=actual_subscriptions,
        )

    async def close(self, err_message: Optional[str] = None) -> None:
        if not self._is_closed:
            self._is_closed = True
            err_message = "" if err_message is None else err_message

            with self._lock:
                for fut in self._pending_futures.values():
                    if not fut.done():
                        fut.set_exception(ClosingError(err_message))
                self._pending_futures.clear()

            with self._pubsub_lock:
                for fut in self._pubsub_futures:
                    if not fut.done():
                        fut.set_exception(ClosingError(err_message))
                self._pubsub_futures.clear()

            _client_registry.pop(getattr(self, "_pipe_client_id", 0), None)

            # Skip FFI call if this client was created in a different process
            # (the tokio Runtime doesn't survive fork; dropping it would hang).
            if self._core_client is not None and self._create_pid == os.getpid():
                self._lib.close_client(self._core_client)
                self._core_client = None

    async def aclose(self, err_message: Optional[str] = None) -> None:
        """Alias for close() for compatibility with async context managers."""
        await self.close(err_message)

    async def __aenter__(self) -> Self:
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb) -> None:
        await self.close()

    async def scoped_connection(
        self, timeout: float = 5.0, routing_key: Optional[str] = None
    ) -> "AsyncIsolatedScope":
        """
        Acquire an isolated execution scope — a dedicated connection for operations
        requiring per-connection state (WATCH/MULTI/EXEC, CLIENT TRACKING, blocking
        commands).

        The scope bypasses the multiplexer, executing commands on its own TCP
        connection. Use as an async context manager for automatic release:

            async with await client.scoped_connection(routing_key="my-key") as scope:
                await scope.watch("my-key")
                val = await scope.get("my-key")
                await scope.multi()
                await scope.set("my-key", str(int(val or "0") + 1))
                result = await scope.exec()

        Args:
            timeout: Maximum seconds to wait for a scope connection (default 5.0).
            routing_key: In cluster mode, the key whose hash slot determines which
                node the scope connects to. All keys used in the scope must hash to
                the same slot. If None, defaults to slot 0.

        Returns:
            An AsyncIsolatedScope instance.

        Raises:
            TimeoutError: If no scope is available within the timeout.
            ClosingError: If the client is closed.
        """
        import time

        from .isolated_scope import AsyncIsolatedScope

        if self._is_closed:
            raise ClosingError("Client is closed.")

        client_id = int(self._ffi.cast("uintptr_t", self._core_client))
        conn_req_bytes = self._conn_req_bytes

        # Compute routing slot from key (CRC16 mod 16384)
        if routing_key is not None:
            routing_slot = _slot_for_key(routing_key.encode("utf-8"))
        else:
            routing_slot = 0

        loop = asyncio.get_running_loop()

        def _acquire_sync():
            deadline = time.monotonic() + timeout
            backoff = 0.001
            while True:
                buf = self._ffi.from_buffer(conn_req_bytes)
                scope_id = self._lib.glide_scope_try_acquire(
                    client_id,
                    self._ffi.cast("const uint8_t*", buf),
                    len(conn_req_bytes),
                    routing_slot,
                )
                if scope_id >= 0:
                    return scope_id
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise TimeoutError(
                        "Timed out waiting for isolated scope (pool exhausted)"
                    )
                time.sleep(min(backoff, remaining))
                backoff = min(backoff * 2, 0.05)

        scope_id = await loop.run_in_executor(None, _acquire_sync)

        return AsyncIsolatedScope(
            scope_id,
            client_id,
            _ASYNC_FFI,
            self._parse_scope_response,
        )

    def _parse_scope_response(self, response_ptr) -> Optional[str]:  # noqa: C901
        """Parse a CommandResponse pointer from a scope execution into a Python string."""
        if response_ptr == self._ffi.NULL:
            return None

        resp = response_ptr
        resp_type = resp.response_type

        if resp_type == 0:  # Null
            return None
        elif resp_type == 1:  # Int
            return str(resp.int_value)
        elif resp_type == 2:  # Float
            return str(resp.float_value)
        elif resp_type == 3:  # Bool
            return str(resp.bool_value)
        elif resp_type == 4:  # String
            if resp.string_value == self._ffi.NULL:
                return None
            return self._ffi.buffer(resp.string_value, resp.string_value_len)[:].decode(
                "utf-8"
            )
        elif resp_type == 5:  # Array
            if resp.array_value == self._ffi.NULL or resp.array_value_len == 0:
                return None
            results = []
            for i in range(resp.array_value_len):
                elem = self._parse_scope_response(resp.array_value + i)
                results.append(elem)
            return str(results)
        elif resp_type == 8:  # Ok
            return "OK"
        elif resp_type == 9:  # Error
            if resp.string_value != self._ffi.NULL:
                msg = self._ffi.string(resp.string_value).decode("utf-8")
                raise RuntimeError(f"Server error: {msg}")
            raise RuntimeError("Server error (unknown)")
        else:
            return None


class GlideClusterClient(BaseClient, ClusterCommands):
    """
    Client used for connection to cluster servers.
    Use :func:`~BaseClient.create` to request a client.
    For full documentation, see
    [Valkey GLIDE Documentation](https://glide.valkey.io/how-to/client-initialization/#cluster)
    """

    async def _cluster_scan(
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
        self._check_same_process()

        callback_id = self._get_callback_id()
        fut = _get_new_future_instance()

        self._register_future(callback_id, fut)

        # Build scan args
        args = []
        if match is not None:
            encoded_match = match.encode(ENCODING) if isinstance(match, str) else match
            args.extend([b"MATCH", encoded_match])
        if count is not None:
            args.extend([b"COUNT", str(count).encode(ENCODING)])
        if type is not None:
            args.extend([b"TYPE", type.value.encode(ENCODING)])
        if allow_non_covered_slots:
            args.extend([b"ALLOW_NON_COVERED_SLOTS"])

        cursor_string = cursor.get_cursor()
        cursor_bytes = cursor_string.encode(ENCODING) + b"\0"
        cursor_buffer = self._ffi.from_buffer(cursor_bytes)

        if args:
            args_array, args_len_array, arg_buffers = self._to_c_strings(args)
            arg_count = len(args)
        else:
            args_array = self._ffi.NULL
            args_len_array = self._ffi.NULL
            arg_count = 0

        self._lib.request_cluster_scan(
            self._core_client,
            callback_id,
            cursor_buffer,
            arg_count,
            args_array,
            args_len_array,
        )

        response_data = await fut

        if not isinstance(response_data, list) or len(response_data) != 2:
            raise RequestError("Unexpected cluster scan response format")

        new_cursor = response_data[0]
        if isinstance(new_cursor, bytes):
            new_cursor = new_cursor.decode(ENCODING)

        keys_list = response_data[1] if response_data[1] is not None else []
        return [ClusterScanCursor(new_cursor), keys_list]

    async def get_subscriptions(
        self,
    ) -> GlideClusterClientConfiguration.PubSubState:
        result = await self._execute_command(RequestType.GetSubscriptions, [])
        return cast(
            GlideClusterClientConfiguration.PubSubState,
            self._parse_pubsub_state(result, is_cluster=True),
        )


class GlideClient(BaseClient, StandaloneCommands):
    """
    Client used for connection to standalone servers.
    Use :func:`~BaseClient.create` to request a client.
    For full documentation, see
    [Valkey GLIDE Documentation](https://glide.valkey.io/how-to/client-initialization/#standalone)
    """

    async def get_subscriptions(
        self,
    ) -> GlideClientConfiguration.PubSubState:
        result = await self._execute_command(RequestType.GetSubscriptions, [])
        return cast(
            GlideClientConfiguration.PubSubState,
            self._parse_pubsub_state(result, is_cluster=False),
        )


TGlideClient = Union[GlideClient, GlideClusterClient]
