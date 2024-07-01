# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
from __future__ import annotations

from abc import ABC, abstractmethod
from typing import List, Optional, Union


class StreamTrimOptions(ABC):
    """
    Abstract base class for stream trim options.
    """

    @abstractmethod
    def __init__(
        self,
        exact: bool,
        threshold: Union[str, int],
        method: str,
        limit: Optional[int] = None,
    ):
        """
        Initialize stream trim options.

        Args:
            exact (bool): If `true`, the stream will be trimmed exactly.
                Otherwise the stream will be trimmed in a near-exact manner, which is more efficient.
            threshold (Union[str, int]): Threshold for trimming.
            method (str): Method for trimming (e.g., MINID, MAXLEN).
            limit (Optional[int]): Max number of entries to be trimmed. Defaults to None.
                Note: If `exact` is set to `True`, `limit` cannot be specified.
        """
        if exact and limit:
            raise ValueError(
                "If `exact` is set to `True`, `limit` cannot be specified."
            )
        self.exact = exact
        self.threshold = threshold
        self.method = method
        self.limit = limit

    def to_args(self) -> List[str]:
        """
        Convert options to arguments for Redis command.

        Returns:
            List[str]: List of arguments for Redis command.
        """
        option_args = [
            self.method,
            "=" if self.exact else "~",
            str(self.threshold),
        ]
        if self.limit is not None:
            option_args.extend(["LIMIT", str(self.limit)])
        return option_args


class TrimByMinId(StreamTrimOptions):
    """
    Stream trim option to trim by minimum ID.
    """

    def __init__(self, exact: bool, threshold: str, limit: Optional[int] = None):
        """
        Initialize trim option by minimum ID.

        Args:
            exact (bool): If `true`, the stream will be trimmed exactly.
                Otherwise the stream will be trimmed in a near-exact manner, which is more efficient.
            threshold (str): Threshold for trimming by minimum ID.
            limit (Optional[int]): Max number of entries to be trimmed. Defaults to None.
                Note: If `exact` is set to `True`, `limit` cannot be specified.
        """
        super().__init__(exact, threshold, "MINID", limit)


class TrimByMaxLen(StreamTrimOptions):
    """
    Stream trim option to trim by maximum length.
    """

    def __init__(self, exact: bool, threshold: int, limit: Optional[int] = None):
        """
        Initialize trim option by maximum length.

        Args:
            exact (bool): If `true`, the stream will be trimmed exactly.
                Otherwise the stream will be trimmed in a near-exact manner, which is more efficient.
            threshold (int): Threshold for trimming by maximum length.
            limit (Optional[int]): Max number of entries to be trimmed. Defaults to None.
                Note: If `exact` is set to `True`, `limit` cannot be specified.
        """
        super().__init__(exact, threshold, "MAXLEN", limit)


class StreamAddOptions:
    """
    Options for adding entries to a stream.
    """

    def __init__(
        self,
        id: Optional[str] = None,
        make_stream: bool = True,
        trim: Optional[StreamTrimOptions] = None,
    ):
        """
        Initialize stream add options.

        Args:
            id (Optional[str]): ID for the new entry. If set, the new entry will be added with this ID. If not specified, '*' is used.
            make_stream (bool, optional): If set to False, a new stream won't be created if no stream matches the given key.
            trim (Optional[StreamTrimOptions]): If set, the add operation will also trim the older entries in the stream. See `StreamTrimOptions`.
        """
        self.id = id
        self.make_stream = make_stream
        self.trim = trim

    def to_args(self) -> List[str]:
        """
        Convert options to arguments for Redis command.

        Returns:
            List[str]: List of arguments for Redis command.
        """
        option_args = []
        if not self.make_stream:
            option_args.append("NOMKSTREAM")
        if self.trim:
            option_args.extend(self.trim.to_args())
        option_args.append(self.id if self.id else "*")

        return option_args


class StreamRangeBound(ABC):
    """
    Abstract Base Class used in the `XPENDING`, `XRANGE`, and `XREVRANGE` commands to specify the starting and ending
    range bound for the stream search by stream entry ID.
    """

    @abstractmethod
    def to_arg(self) -> str:
        """
        Returns the stream range bound as a string argument to be used in the `XRANGE` or `XREVRANGE` commands.
        """
        pass


class MinId(StreamRangeBound):
    """
    Stream ID boundary used to specify the minimum stream entry ID. Can be used in the `XRANGE` or `XREVRANGE` commands
    to get the first stream ID.
    """

    MIN_RANGE_REDIS_API = "-"

    def to_arg(self) -> str:
        return self.MIN_RANGE_REDIS_API


class MaxId(StreamRangeBound):
    """
    Stream ID boundary used to specify the maximum stream entry ID. Can be used in the `XRANGE` or `XREVRANGE` commands
    to get the last stream ID.
    """

    MAX_RANGE_REDIS_API = "+"

    def to_arg(self) -> str:
        return self.MAX_RANGE_REDIS_API


class IdBound(StreamRangeBound):
    """
    Inclusive (closed) stream ID boundary used to specify a range of IDs to search. Stream ID bounds can be complete
    with a timestamp and sequence number separated by a dash ("-"), for example "1526985054069-0". Stream ID bounds can
    also be incomplete, with just a timestamp.
    """

    @staticmethod
    def from_timestamp(timestamp: int) -> IdBound:
        """
        Creates an incomplete stream ID boundary without the sequence number for a range search.

        Args:
            timestamp (int): The stream ID timestamp.
        """
        return IdBound(str(timestamp))

    def __init__(self, stream_id: str):
        """
        Creates a stream ID boundary for a range search.

        Args:
            stream_id (str): The stream ID.
        """
        self.stream_id = stream_id

    def to_arg(self) -> str:
        return self.stream_id


class ExclusiveIdBound(StreamRangeBound):
    """
    Exclusive (open) stream ID boundary used to specify a range of IDs to search. Stream ID bounds can be complete with
    a timestamp and sequence number separated by a dash ("-"), for example "1526985054069-0". Stream ID bounds can also
    be incomplete, with just a timestamp.

    Since: Redis version 6.2.0.
    """

    EXCLUSIVE_BOUND_REDIS_API = "("

    @staticmethod
    def from_timestamp(timestamp: int) -> ExclusiveIdBound:
        """
        Creates an incomplete stream ID boundary without the sequence number for a range search.

        Args:
            timestamp (int): The stream ID timestamp.
        """
        return ExclusiveIdBound(str(timestamp))

    def __init__(self, stream_id: str):
        """
        Creates a stream ID boundary for a range search.

        Args:
            stream_id (str): The stream ID.
        """
        self.stream_id = f"{self.EXCLUSIVE_BOUND_REDIS_API}{stream_id}"

    def to_arg(self) -> str:
        return self.stream_id


class StreamReadOptions:
    READ_COUNT_REDIS_API = "COUNT"
    READ_BLOCK_REDIS_API = "BLOCK"

    def __init__(self, block_ms: Optional[int] = None, count: Optional[int] = None):
        """
        Options for reading entries from streams. Can be used as an optional argument to `XREAD`.

        Args:
            block_ms (Optional[int]): If provided, the request will be blocked for the set amount of milliseconds or
                until the server has the required number of entries. Equivalent to `BLOCK` in the Redis API.
            count (Optional[int]): The maximum number of elements requested. Equivalent to `COUNT` in the Redis API.
        """
        self.block_ms = block_ms
        self.count = count

    def to_args(self) -> List[str]:
        """
        Returns the options as a list of string arguments to be used in the `XREAD` command.

        Returns:
            List[str]: The options as a list of arguments for the `XREAD` command.
        """
        args = []
        if self.block_ms is not None:
            args.extend([self.READ_BLOCK_REDIS_API, str(self.block_ms)])

        if self.count is not None:
            args.extend([self.READ_COUNT_REDIS_API, str(self.count)])

        return args


class StreamGroupOptions:
    MAKE_STREAM_REDIS_API = "MKSTREAM"
    ENTRIES_READ_REDIS_API = "ENTRIESREAD"

    def __init__(
        self, make_stream: bool = False, entries_read_id: Optional[str] = None
    ):
        """
        Options for creating stream consumer groups. Can be used as an optional argument to `XGROUP CREATE`.

        Args:
            make_stream (bool): If set to True and the stream doesn't exist, this creates a new stream with a
                length of 0.
            entries_read_id: (Optional[str]): An arbitrary ID (that isn't the first ID, last ID, or the zero ID ("0-0"))
                used to find out how many entries are between the arbitrary ID (excluding it) and the stream's last
                entry. This option can only be specified if you are using Redis version 7.0.0 or above.
        """
        self.make_stream = make_stream
        self.entries_read_id = entries_read_id

    def to_args(self) -> List[str]:
        """
        Returns the options as a list of string arguments to be used in the `XGROUP CREATE` command.

        Returns:
            List[str]: The options as a list of arguments for the `XGROUP CREATE` command.
        """
        args = []
        if self.make_stream is True:
            args.append(self.MAKE_STREAM_REDIS_API)

        if self.entries_read_id is not None:
            args.extend([self.ENTRIES_READ_REDIS_API, self.entries_read_id])

        return args


class StreamReadGroupOptions(StreamReadOptions):
    READ_NOACK_REDIS_API = "NOACK"

    def __init__(
        self, no_ack=False, block_ms: Optional[int] = None, count: Optional[int] = None
    ):
        """
        Options for reading entries from streams using a consumer group. Can be used as an optional argument to
        `XREADGROUP`.

        Args:
            no_ack (bool): If set, messages are not added to the Pending Entries List (PEL). This is equivalent to
                acknowledging the message when it is read. Equivalent to `NOACK` in the Redis API.
            block_ms (Optional[int]): If provided, the request will be blocked for the set amount of milliseconds or
                until the server has the required number of entries. Equivalent to `BLOCK` in the Redis API.
            count (Optional[int]): The maximum number of elements requested. Equivalent to `COUNT` in the Redis API.
        """
        super().__init__(block_ms=block_ms, count=count)
        self.no_ack = no_ack

    def to_args(self) -> List[str]:
        """
        Returns the options as a list of string arguments to be used in the `XREADGROUP` command.

        Returns:
            List[str]: The options as a list of arguments for the `XREADGROUP` command.
        """
        args = super().to_args()
        if self.no_ack:
            args.append(self.READ_NOACK_REDIS_API)

        return args


class StreamPendingOptions:
    IDLE_TIME_REDIS_API = "IDLE"

    def __init__(
        self,
        min_idle_time_ms: Optional[int] = None,
        consumer_name: Optional[str] = None,
    ):
        """
        Options for `XPENDING` that can be used to filter returned items by minimum idle time and consumer name.

        Args:
            min_idle_time_ms (Optional[int]): Filters pending entries by their minimum idle time in milliseconds. This
                option can only be specified if you are using Redis version 6.2.0 or above.
            consumer_name (Optional[str]): Filters pending entries by consumer name.
        """
        self.min_idle_time = min_idle_time_ms
        self.consumer_name = consumer_name


def _create_xpending_range_args(
    key: str,
    group_name: str,
    start: StreamRangeBound,
    end: StreamRangeBound,
    count: int,
    options: Optional[StreamPendingOptions],
) -> List[str]:
    args = [key, group_name]
    if options is not None and options.min_idle_time is not None:
        args.extend([options.IDLE_TIME_REDIS_API, str(options.min_idle_time)])

    args.extend([start.to_arg(), end.to_arg(), str(count)])
    if options is not None and options.consumer_name is not None:
        args.append(options.consumer_name)

    return args
