import asyncio
import threading
from typing import Awaitable, List, Optional, Type, Union

import async_timeout
from google.protobuf.internal.decoder import _DecodeVarint32
from google.protobuf.internal.encoder import _VarintBytes
from pybushka.async_commands import CoreCommands
from pybushka.config import ClientConfiguration
from pybushka.constants import (
    DEFAULT_READ_BYTES_SIZE,
    OK,
    TConnectionRequest,
    TRedisRequest,
    TRequest,
    TRequestType,
    TResult,
)
from pybushka.Logger import Level as LogLevel
from pybushka.Logger import Logger
from pybushka.protobuf.redis_request_pb2 import Command, RedisRequest
from pybushka.protobuf.response_pb2 import Response
from typing_extensions import Self

from .pybushka import start_socket_listener_external, value_from_pointer


def _protobuf_encode_delimited(b_arr, request: TRequest) -> None:
    bytes_request = request.SerializeToString()
    varint = _VarintBytes(len(bytes_request))
    b_arr.extend(varint)
    b_arr.extend(bytes_request)


class BaseRedisClient(CoreCommands):
    @classmethod
    async def create(cls, config: ClientConfiguration = None) -> Self:
        config = config or ClientConfiguration()
        self = cls()
        self.config: Type[ClientConfiguration] = config
        self._write_buffer: bytearray = bytearray(1024)
        self._available_futures: dict[int, Awaitable[TResult]] = {}
        self._available_callbackIndexes: set[int] = set()
        self._buffered_requests: List[TRedisRequest] = list()
        self._writer_lock = threading.Lock()
        init_future = asyncio.Future()
        loop = asyncio.get_event_loop()

        def init_callback(socket_path: Optional[str], err: Optional[str]):
            if err is not None:
                raise Exception(
                    f"Failed to initialize the socket \
                    connection: {str(err)}"
                )
            elif socket_path is None:
                raise Exception("Received None as the socket_path")
            else:
                # Received socket path
                self.socket_path = socket_path
                loop.call_soon_threadsafe(init_future.set_result, True)

        start_socket_listener_external(init_callback=init_callback)

        # will log if the logger was created (wrapper or costumer) on info
        # level or higher
        Logger.log(LogLevel.INFO, "connection info", "new connection established")
        # Wait for the socket listener to complete its initialization
        await init_future
        # Create UDS connection
        await self._create_uds_connection()
        # Start the reader loop as a background task
        self._reader_task = asyncio.create_task(self._reader_loop())
        # Set the client configurations
        await self._set_connection_configurations()
        return self

    async def _wait_for_init_complete(self) -> None:
        while not self._done_init:
            await asyncio.sleep(0.1)

    async def _create_uds_connection(self) -> None:
        try:
            # Open an UDS connection
            async with async_timeout.timeout(self.config.client_creation_timeout):
                reader, writer = await asyncio.open_unix_connection(
                    path=self.socket_path
                )
            self._reader = reader
            self._writer = writer
        except Exception as e:
            self.close(f"Failed to create UDS connection: {e}")
            raise

    def __del__(self) -> None:
        try:
            if self._reader_task:
                self._reader_task.cancel()
        except RuntimeError as e:
            if "no running event loop" in str(e):
                # event loop already closed
                pass

    def close(self, err: str = "") -> None:
        for response_future in self._available_futures.values():
            if not response_future.done():
                response_future.set_exception(err)
        self.__del__()

    def _get_future(self, callback_idx: int) -> Type[asyncio.Future]:
        response_future = asyncio.Future()
        self._available_futures.update({callback_idx: response_future})
        return response_future

    def _get_protobuf_conn_request(self) -> TConnectionRequest:
        return self.config.convert_to_protobuf_request()

    async def _set_connection_configurations(self) -> None:
        conn_request = self._get_protobuf_conn_request()
        response_future = self._get_future(0)
        await self._write_or_buffer_request(conn_request)
        await response_future
        if response_future.result() is not None:
            raise Exception(f"Failed to set configurations={response_future.result()}")

    async def _write_or_buffer_request(self, request: TRequest):
        self._buffered_requests.append(request)
        if self._writer_lock.acquire(False):
            try:
                await self._write_buffered_requests_to_socket()
            finally:
                self._writer_lock.release()

    async def _write_buffered_requests_to_socket(self) -> None:
        requests = self._buffered_requests
        self._buffered_requests = list()
        b_arr = bytearray()
        for request in requests:
            _protobuf_encode_delimited(b_arr, request)
        self._writer.write(b_arr)
        await self._writer.drain()
        if len(self._buffered_requests) > 0:
            # might not be threadsafe, we should consider adding a lock
            await self._write_buffered_requests_to_socket()

    async def execute_command(
        self, request_type: TRequestType, args: List[str]
    ) -> TResult:
        request = RedisRequest()
        request.callback_idx = self._get_callback_index()
        request.single_command.request_type = request_type
        request.single_command.args_array.args[:] = args  # TODO - use arg pointer
        # Create a response future for this request and add it to the available
        # futures map
        response_future = self._get_future(request.callback_idx)
        await self._write_or_buffer_request(request)
        await response_future
        return response_future.result()

    async def execute_transaction(
        self, commands: List[Union[TRequestType, List[str]]]
    ) -> TResult:
        request = RedisRequest()
        request.callback_idx = self._get_callback_index()
        transaction_commands = []
        for requst_type, args in commands:
            command = Command()
            command.request_type = requst_type
            command.args_array.args[:] = args
            transaction_commands.append(command)
        request.transaction.commands.extend(transaction_commands)
        # Create a response future for this request and add it to the available
        # futures map
        response_future = self._get_future(request.callback_idx)
        await self._write_or_buffer_request(request)
        await response_future
        return response_future.result()

    def _get_callback_index(self) -> int:
        if not self._available_callbackIndexes:
            # Set is empty
            return len(self._available_futures)
        return self._available_callbackIndexes.pop()

    async def _reader_loop(self) -> None:
        # Socket reader loop
        remaining_read_bytes = bytearray()
        while True:
            read_bytes = await self._reader.read(DEFAULT_READ_BYTES_SIZE)
            if len(read_bytes) == 0:
                self.close("The server closed the connection")
                raise Exception("read 0 bytes")
            read_bytes = remaining_read_bytes + bytearray(read_bytes)
            read_bytes_view = memoryview(read_bytes)
            offset = 0
            while offset <= len(read_bytes):
                try:
                    msg_len, new_pos = _DecodeVarint32(read_bytes_view, offset)
                except IndexError:
                    # Didn't read enough bytes to decode the varint,
                    # break the inner loop
                    remaining_read_bytes = read_bytes[offset:]
                    break
                required_read_size = new_pos + msg_len
                if required_read_size > len(read_bytes):
                    # Recieved only partial response,
                    # break the inner loop
                    remaining_read_bytes = read_bytes[offset:]
                    break
                offset = new_pos
                msg_buf = read_bytes_view[offset : offset + msg_len]
                offset += msg_len
                response = Response()
                response.ParseFromString(msg_buf)
                res_future = self._available_futures.get(response.callback_idx)
                if not res_future:
                    self.close("Got wrong callback index: {}", response.callback_idx)
                else:
                    if response.HasField("request_error"):
                        res_future.set_exception(Exception(response.request_error))
                    elif response.HasField("closing_error"):
                        res_future.set_exception(Exception(response.closing_error))
                        self.close(response.closing_error)
                    elif response.HasField("resp_pointer"):
                        res_future.set_result(value_from_pointer(response.resp_pointer))
                    elif response.HasField("constant_response"):
                        res_future.set_result(OK)
                    else:
                        res_future.set_result(None)


class RedisClusterClient(BaseRedisClient):
    def _get_protobuf_conn_request(self) -> TConnectionRequest:
        return self.config.convert_to_protobuf_request(cluster_mode=True)


class RedisClient(BaseRedisClient):
    pass
