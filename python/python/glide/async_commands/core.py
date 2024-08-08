# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
from dataclasses import dataclass
from datetime import datetime, timedelta
from enum import Enum
from typing import (
    Dict,
    List,
    Mapping,
    Optional,
    Protocol,
    Set,
    Tuple,
    Type,
    Union,
    cast,
    get_args,
)

from glide.async_commands.bitmap import (
    BitFieldGet,
    BitFieldSubCommands,
    BitmapIndexType,
    BitwiseOperation,
    OffsetOptions,
    _create_bitfield_args,
    _create_bitfield_read_only_args,
)
from glide.async_commands.command_args import Limit, ListDirection, ObjectType, OrderBy
from glide.async_commands.sorted_set import (
    AggregationType,
    GeoSearchByBox,
    GeoSearchByRadius,
    GeoSearchCount,
    GeospatialData,
    GeoUnit,
    InfBound,
    LexBoundary,
    RangeByIndex,
    RangeByLex,
    RangeByScore,
    ScoreBoundary,
    ScoreFilter,
    _create_geosearch_args,
    _create_zinter_zunion_cmd_args,
    _create_zrange_args,
)
from glide.async_commands.stream import (
    StreamAddOptions,
    StreamClaimOptions,
    StreamGroupOptions,
    StreamPendingOptions,
    StreamRangeBound,
    StreamReadGroupOptions,
    StreamReadOptions,
    StreamTrimOptions,
    _create_xpending_range_args,
)
from glide.constants import (
    TOK,
    TEncodable,
    TResult,
    TXInfoStreamFullResponse,
    TXInfoStreamResponse,
)
from glide.protobuf.command_request_pb2 import RequestType
from glide.routes import Route

from ..glide import ClusterScanCursor, Script


class ConditionalChange(Enum):
    """
    A condition to the `SET`, `ZADD` and `GEOADD` commands.
    - ONLY_IF_EXISTS - Only update key / elements that already exist. Equivalent to `XX` in the Valkey API.
    - ONLY_IF_DOES_NOT_EXIST - Only set key / add elements that does not already exist. Equivalent to `NX` in the Valkey API.
    """

    ONLY_IF_EXISTS = "XX"
    ONLY_IF_DOES_NOT_EXIST = "NX"


class ExpiryType(Enum):
    """SET option: The type of the expiry.
    - SEC - Set the specified expire time, in seconds. Equivalent to `EX` in the Valkey API.
    - MILLSEC - Set the specified expire time, in milliseconds. Equivalent to `PX` in the Valkey API.
    - UNIX_SEC - Set the specified Unix time at which the key will expire, in seconds. Equivalent to `EXAT` in the Valkey API.
    - UNIX_MILLSEC - Set the specified Unix time at which the key will expire, in milliseconds. Equivalent to `PXAT` in the
        Valkey API.
    - KEEP_TTL - Retain the time to live associated with the key. Equivalent to `KEEPTTL` in the Valkey API.
    """

    SEC = 0, Union[int, timedelta]  # Equivalent to `EX` in the Valkey API
    MILLSEC = 1, Union[int, timedelta]  # Equivalent to `PX` in the Valkey API
    UNIX_SEC = 2, Union[int, datetime]  # Equivalent to `EXAT` in the Valkey API
    UNIX_MILLSEC = 3, Union[int, datetime]  # Equivalent to `PXAT` in the Valkey API
    KEEP_TTL = 4, Type[None]  # Equivalent to `KEEPTTL` in the Valkey API


class ExpiryTypeGetEx(Enum):
    """GetEx option: The type of the expiry.
    - EX - Set the specified expire time, in seconds. Equivalent to `EX` in the Valkey API.
    - PX - Set the specified expire time, in milliseconds. Equivalent to `PX` in the Valkey API.
    - UNIX_SEC - Set the specified Unix time at which the key will expire, in seconds. Equivalent to `EXAT` in the Valkey API.
    - UNIX_MILLSEC - Set the specified Unix time at which the key will expire, in milliseconds. Equivalent to `PXAT` in the
        Valkey API.
    - PERSIST - Remove the time to live associated with the key. Equivalent to `PERSIST` in the Valkey API.
    """

    SEC = 0, Union[int, timedelta]  # Equivalent to `EX` in the Valkey API
    MILLSEC = 1, Union[int, timedelta]  # Equivalent to `PX` in the Valkey API
    UNIX_SEC = 2, Union[int, datetime]  # Equivalent to `EXAT` in the Valkey API
    UNIX_MILLSEC = 3, Union[int, datetime]  # Equivalent to `PXAT` in the Valkey API
    PERSIST = 4, Type[None]  # Equivalent to `PERSIST` in the Valkey API


class InfoSection(Enum):
    """
    INFO option: a specific section of information:

    -SERVER: General information about the server
    -CLIENTS: Client connections section
    -MEMORY: Memory consumption related information
    -PERSISTENCE: RDB and AOF related information
    -STATS: General statistics
    -REPLICATION: Master/replica replication information
    -CPU: CPU consumption statistics
    -COMMANDSTATS: Valkey command statistics
    -LATENCYSTATS: Valkey command latency percentile distribution statistics
    -SENTINEL: Valkey Sentinel section (only applicable to Sentinel instances)
    -CLUSTER: Valkey Cluster section
    -MODULES: Modules section
    -KEYSPACE: Database related statistics
    -ERRORSTATS: Valkey error statistics
    -ALL: Return all sections (excluding module generated ones)
    -DEFAULT: Return only the default set of sections
    -EVERYTHING: Includes all and modules
    When no parameter is provided, the default option is assumed.
    """

    SERVER = "server"
    CLIENTS = "clients"
    MEMORY = "memory"
    PERSISTENCE = "persistence"
    STATS = "stats"
    REPLICATION = "replication"
    CPU = "cpu"
    COMMAND_STATS = "commandstats"
    LATENCY_STATS = "latencystats"
    SENTINEL = "sentinel"
    CLUSTER = "cluster"
    MODULES = "modules"
    KEYSPACE = "keyspace"
    ERROR_STATS = "errorstats"
    ALL = "all"
    DEFAULT = "default"
    EVERYTHING = "everything"


class ExpireOptions(Enum):
    """
    EXPIRE option: options for setting key expiry.

    - HasNoExpiry: Set expiry only when the key has no expiry (Equivalent to "NX" in Valkey).
    - HasExistingExpiry: Set expiry only when the key has an existing expiry (Equivalent to "XX" in Valkey).
    - NewExpiryGreaterThanCurrent: Set expiry only when the new expiry is greater than the current one (Equivalent
        to "GT" in Valkey).
    - NewExpiryLessThanCurrent: Set expiry only when the new expiry is less than the current one (Equivalent to "LT" in Valkey).
    """

    HasNoExpiry = "NX"
    HasExistingExpiry = "XX"
    NewExpiryGreaterThanCurrent = "GT"
    NewExpiryLessThanCurrent = "LT"


class UpdateOptions(Enum):
    """
    Options for updating elements of a sorted set key.

    - LESS_THAN: Only update existing elements if the new score is less than the current score.
    - GREATER_THAN: Only update existing elements if the new score is greater than the current score.
    """

    LESS_THAN = "LT"
    GREATER_THAN = "GT"


class ExpirySet:
    """SET option: Represents the expiry type and value to be executed with "SET" command."""

    def __init__(
        self,
        expiry_type: ExpiryType,
        value: Optional[Union[int, datetime, timedelta]],
    ) -> None:
        """
        Args:
            - expiry_type (ExpiryType): The expiry type.
            - value (Optional[Union[int, datetime, timedelta]]): The value of the expiration type. The type of expiration
                determines the type of expiration value:
                - SEC: Union[int, timedelta]
                - MILLSEC: Union[int, timedelta]
                - UNIX_SEC: Union[int, datetime]
                - UNIX_MILLSEC: Union[int, datetime]
                - KEEP_TTL: Type[None]
        """
        self.set_expiry_type_and_value(expiry_type, value)

    def set_expiry_type_and_value(
        self, expiry_type: ExpiryType, value: Optional[Union[int, datetime, timedelta]]
    ):
        if not isinstance(value, get_args(expiry_type.value[1])):
            raise ValueError(
                f"The value of {expiry_type} should be of type {expiry_type.value[1]}"
            )
        self.expiry_type = expiry_type
        if self.expiry_type == ExpiryType.SEC:
            self.cmd_arg = "EX"
            if isinstance(value, timedelta):
                value = int(value.total_seconds())
        elif self.expiry_type == ExpiryType.MILLSEC:
            self.cmd_arg = "PX"
            if isinstance(value, timedelta):
                value = int(value.total_seconds() * 1000)
        elif self.expiry_type == ExpiryType.UNIX_SEC:
            self.cmd_arg = "EXAT"
            if isinstance(value, datetime):
                value = int(value.timestamp())
        elif self.expiry_type == ExpiryType.UNIX_MILLSEC:
            self.cmd_arg = "PXAT"
            if isinstance(value, datetime):
                value = int(value.timestamp() * 1000)
        elif self.expiry_type == ExpiryType.KEEP_TTL:
            self.cmd_arg = "KEEPTTL"
        self.value = str(value) if value else None

    def get_cmd_args(self) -> List[str]:
        return [self.cmd_arg] if self.value is None else [self.cmd_arg, self.value]


class ExpiryGetEx:
    """GetEx option: Represents the expiry type and value to be executed with "GetEx" command."""

    def __init__(
        self,
        expiry_type: ExpiryTypeGetEx,
        value: Optional[Union[int, datetime, timedelta]],
    ) -> None:
        """
        Args:
            - expiry_type (ExpiryType): The expiry type.
            - value (Optional[Union[int, datetime, timedelta]]): The value of the expiration type. The type of expiration
                determines the type of expiration value:
                - SEC: Union[int, timedelta]
                - MILLSEC: Union[int, timedelta]
                - UNIX_SEC: Union[int, datetime]
                - UNIX_MILLSEC: Union[int, datetime]
                - PERSIST: Type[None]
        """
        self.set_expiry_type_and_value(expiry_type, value)

    def set_expiry_type_and_value(
        self,
        expiry_type: ExpiryTypeGetEx,
        value: Optional[Union[int, datetime, timedelta]],
    ):
        if not isinstance(value, get_args(expiry_type.value[1])):
            raise ValueError(
                f"The value of {expiry_type} should be of type {expiry_type.value[1]}"
            )
        self.expiry_type = expiry_type
        if self.expiry_type == ExpiryTypeGetEx.SEC:
            self.cmd_arg = "EX"
            if isinstance(value, timedelta):
                value = int(value.total_seconds())
        elif self.expiry_type == ExpiryTypeGetEx.MILLSEC:
            self.cmd_arg = "PX"
            if isinstance(value, timedelta):
                value = int(value.total_seconds() * 1000)
        elif self.expiry_type == ExpiryTypeGetEx.UNIX_SEC:
            self.cmd_arg = "EXAT"
            if isinstance(value, datetime):
                value = int(value.timestamp())
        elif self.expiry_type == ExpiryTypeGetEx.UNIX_MILLSEC:
            self.cmd_arg = "PXAT"
            if isinstance(value, datetime):
                value = int(value.timestamp() * 1000)
        elif self.expiry_type == ExpiryTypeGetEx.PERSIST:
            self.cmd_arg = "PERSIST"
        self.value = str(value) if value else None

    def get_cmd_args(self) -> List[str]:
        return [self.cmd_arg] if self.value is None else [self.cmd_arg, self.value]


class InsertPosition(Enum):
    BEFORE = "BEFORE"
    AFTER = "AFTER"


class FlushMode(Enum):
    """
    Defines flushing mode for:

    `FLUSHALL` command and `FUNCTION FLUSH` command.

    See https://valkey.io/commands/flushall/ and https://valkey.io/commands/function-flush/ for details

    SYNC was introduced in version 6.2.0.
    """

    ASYNC = "ASYNC"
    SYNC = "SYNC"


class FunctionRestorePolicy(Enum):
    """
    Options for the FUNCTION RESTORE command.

    - APPEND: Appends the restored libraries to the existing libraries and aborts on collision. This is the
        default policy.
    - FLUSH: Deletes all existing libraries before restoring the payload.
    - REPLACE: Appends the restored libraries to the existing libraries, replacing any existing ones in case
        of name collisions. Note that this policy doesn't prevent function name collisions, only libraries.
    """

    APPEND = "APPEND"
    FLUSH = "FLUSH"
    REPLACE = "REPLACE"


def _build_sort_args(
    key: TEncodable,
    by_pattern: Optional[TEncodable] = None,
    limit: Optional[Limit] = None,
    get_patterns: Optional[List[TEncodable]] = None,
    order: Optional[OrderBy] = None,
    alpha: Optional[bool] = None,
    store: Optional[TEncodable] = None,
) -> List[TEncodable]:
    args = [key]

    if by_pattern:
        args.extend(["BY", by_pattern])

    if limit:
        args.extend(["LIMIT", str(limit.offset), str(limit.count)])

    if get_patterns:
        for pattern in get_patterns:
            args.extend(["GET", pattern])

    if order:
        args.append(order.value)

    if alpha:
        args.append("ALPHA")

    if store:
        args.extend(["STORE", store])

    return args


class CoreCommands(Protocol):
    async def _execute_command(
        self,
        request_type: RequestType.ValueType,
        args: List[TEncodable],
        route: Optional[Route] = ...,
    ) -> TResult: ...

    async def _execute_transaction(
        self,
        commands: List[Tuple[RequestType.ValueType, List[TEncodable]]],
        route: Optional[Route] = None,
    ) -> List[TResult]: ...

    async def _execute_script(
        self,
        hash: str,
        keys: Optional[List[TEncodable]] = None,
        args: Optional[List[TEncodable]] = None,
        route: Optional[Route] = None,
    ) -> TResult: ...

    async def _cluster_scan(
        self,
        cursor: ClusterScanCursor,
        match: Optional[TEncodable] = ...,
        count: Optional[int] = ...,
        type: Optional[ObjectType] = ...,
    ) -> TResult: ...

    async def set(
        self,
        key: TEncodable,
        value: TEncodable,
        conditional_set: Optional[ConditionalChange] = None,
        expiry: Optional[ExpirySet] = None,
        return_old_value: bool = False,
    ) -> Optional[bytes]:
        """
        Set the given key with the given value. Return value is dependent on the passed options.
        See https://valkey.io/commands/set/ for more details.

        Args:
            key (TEncodable): the key to store.
            value (TEncodable): the value to store with the given key.
            conditional_set (Optional[ConditionalChange], optional): set the key only if the given condition is met.
                Equivalent to [`XX` | `NX`] in the Valkey API. Defaults to None.
            expiry (Optional[ExpirySet], optional): set expiriation to the given key.
                Equivalent to [`EX` | `PX` | `EXAT` | `PXAT` | `KEEPTTL`] in the Valkey API. Defaults to None.
            return_old_value (bool, optional): Return the old value stored at key, or None if key did not exist.
                An error is returned and SET aborted if the value stored at key is not a string.
                Equivalent to `GET` in the Valkey API. Defaults to False.

        Returns:
            Optional[bytes]:
                If the value is successfully set, return OK.
                If value isn't set because of only_if_exists or only_if_does_not_exist conditions, return None.
                If return_old_value is set, return the old value as a bytes string.

        Example:
            >>> await client.set(b"key", b"value")
                'OK'
            >>> await client.set("key", "new_value",conditional_set=ConditionalChange.ONLY_IF_EXISTS, expiry=Expiry(ExpiryType.SEC, 5))
                'OK' # Set "new_value" to "key" only if "key" already exists, and set the key expiration to 5 seconds.
            >>> await client.set("key", "value", conditional_set=ConditionalChange.ONLY_IF_DOES_NOT_EXIST,return_old_value=True)
                b'new_value' # Returns the old value of "key".
            >>> await client.get("key")
                b'new_value' # Value wasn't modified back to being "value" because of "NX" flag.
        """
        args = [key, value]
        if conditional_set:
            args.append(conditional_set.value)
        if return_old_value:
            args.append("GET")
        if expiry is not None:
            args.extend(expiry.get_cmd_args())
        return cast(Optional[bytes], await self._execute_command(RequestType.Set, args))

    async def get(self, key: TEncodable) -> Optional[bytes]:
        """
        Get the value associated with the given key, or null if no such value exists.
        See https://valkey.io/commands/get/ for details.

        Args:
            key (TEncodable): The key to retrieve from the database.

        Returns:
            Optional[bytes]: If the key exists, returns the value of the key as a byte string. Otherwise, return None.

        Example:
            >>> await client.get("key")
                b'value'
        """
        args: List[TEncodable] = [key]
        return cast(Optional[bytes], await self._execute_command(RequestType.Get, args))

    async def getdel(self, key: TEncodable) -> Optional[bytes]:
        """
        Gets a value associated with the given string `key` and deletes the key.

        See https://valkey.io/commands/getdel for more details.

        Args:
            key (TEncodable): The `key` to retrieve from the database.

        Returns:
            Optional[bytes]: If `key` exists, returns the `value` of `key`. Otherwise, returns `None`.

        Examples:
            >>> await client.set("key", "value")
            >>> await client.getdel("key")
                b'value'
            >>> await client.getdel("key")
                None
        """
        return cast(
            Optional[bytes], await self._execute_command(RequestType.GetDel, [key])
        )

    async def getrange(self, key: TEncodable, start: int, end: int) -> bytes:
        """
        Returns the substring of the value stored at `key`, determined by the offsets `start` and `end` (both are inclusive).
        Negative offsets can be used in order to provide an offset starting from the end of the value.
        So `-1` means the last character, `-2` the penultimate and so forth.

        If `key` does not exist, an empty byte string is returned. If `start` or `end`
        are out of range, returns the substring within the valid range of the value.

        See https://valkey.io/commands/getrange/ for more details.

        Args:
            key (TEncodable): The key of the string.
            start (int): The starting offset.
            end (int): The ending offset.

        Returns:
            bytes: A substring extracted from the value stored at `key`.

        Examples:
            >>> await client.set("mykey", "This is a string")
            >>> await client.getrange("mykey", 0, 3)
                b"This"
            >>> await client.getrange("mykey", -3, -1)
                b"ing"  # extracted last 3 characters of a string
            >>> await client.getrange("mykey", 0, 100)
                b"This is a string"
            >>> await client.getrange("non_existing", 5, 6)
                b""
        """
        return cast(
            bytes,
            await self._execute_command(
                RequestType.GetRange, [key, str(start), str(end)]
            ),
        )

    async def append(self, key: TEncodable, value: TEncodable) -> int:
        """
        Appends a value to a key.
        If `key` does not exist it is created and set as an empty string, so `APPEND` will be similar to `SET` in this special case.

        See https://valkey.io/commands/append for more details.

        Args:
            key (TEncodable): The key to which the value will be appended.
            value (TEncodable): The value to append.

        Returns:
            int: The length of the stored value after appending `value`.

        Examples:
            >>> await client.append("key", "Hello")
                5  # Indicates that "Hello" has been appended to the value of "key", which was initially empty, resulting in a new value of "Hello" with a length of 5 - similar to the set operation.
            >>> await client.append("key", " world")
                11  # Indicates that " world" has been appended to the value of "key", resulting in a new value of "Hello world" with a length of 11.
            >>> await client.get("key")
                b"Hello world"  # Returns the value stored in "key", which is now "Hello world".
        """
        return cast(int, await self._execute_command(RequestType.Append, [key, value]))

    async def strlen(self, key: TEncodable) -> int:
        """
        Get the length of the string value stored at `key`.
        See https://valkey.io/commands/strlen/ for more details.

        Args:
            key (TEncodable): The key to return its length.

        Returns:
            int: The length of the string value stored at `key`.
                If `key` does not exist, it is treated as an empty string and 0 is returned.

        Examples:
            >>> await client.set("key", "GLIDE")
            >>> await client.strlen("key")
                5  # Indicates that the length of the string value stored at `key` is 5.
        """
        args: List[TEncodable] = [key]
        return cast(int, await self._execute_command(RequestType.Strlen, args))

    async def rename(self, key: TEncodable, new_key: TEncodable) -> TOK:
        """
        Renames `key` to `new_key`.
        If `newkey` already exists it is overwritten.
        See https://valkey.io/commands/rename/ for more details.

        Note:
            When in cluster mode, both `key` and `newkey` must map to the same hash slot.

        Args:
            key (TEncodable) : The key to rename.
            new_key (TEncodable) : The new name of the key.

        Returns:
            OK: If the `key` was successfully renamed, return "OK". If `key` does not exist, an error is thrown.
        """
        return cast(
            TOK, await self._execute_command(RequestType.Rename, [key, new_key])
        )

    async def renamenx(self, key: TEncodable, new_key: TEncodable) -> bool:
        """
        Renames `key` to `new_key` if `new_key` does not yet exist.

        See https://valkey.io/commands/renamenx for more details.

        Note:
            When in cluster mode, both `key` and `new_key` must map to the same hash slot.

        Args:
            key (TEncodable): The key to rename.
            new_key (TEncodable): The new key name.

        Returns:
            bool: True if `key` was renamed to `new_key`, or False if `new_key` already exists.

        Examples:
            >>> await client.renamenx("old_key", "new_key")
                True  # "old_key" was renamed to "new_key"
        """
        return cast(
            bool,
            await self._execute_command(RequestType.RenameNX, [key, new_key]),
        )

    async def delete(self, keys: List[TEncodable]) -> int:
        """
        Delete one or more keys from the database. A key is ignored if it does not exist.
        See https://valkey.io/commands/del/ for details.

        Note:
            When in cluster mode, the command may route to multiple nodes when `keys` map to different hash slots.

        Args:
            keys (List[TEncodable]): A list of keys to be deleted from the database.

        Returns:
            int: The number of keys that were deleted.

        Examples:
            >>> await client.set("key", "value")
            >>> await client.delete(["key"])
                1 # Indicates that the key was successfully deleted.
            >>> await client.delete(["key"])
                0 # No keys we're deleted since "key" doesn't exist.
        """
        return cast(int, await self._execute_command(RequestType.Del, keys))

    async def incr(self, key: TEncodable) -> int:
        """
        Increments the number stored at `key` by one. If the key does not exist, it is set to 0 before performing the
        operation.
        See https://valkey.io/commands/incr/ for more details.

        Args:
          key (TEncodable): The key to increment its value.

        Returns:
            int: The value of `key` after the increment.

        Examples:
            >>> await client.set("key", "10")
            >>> await client.incr("key")
                11
        """
        return cast(int, await self._execute_command(RequestType.Incr, [key]))

    async def incrby(self, key: TEncodable, amount: int) -> int:
        """
        Increments the number stored at `key` by `amount`. If the key does not exist, it is set to 0 before performing
        the operation. See https://valkey.io/commands/incrby/ for more details.

        Args:
          key (TEncodable): The key to increment its value.
          amount (int) : The amount to increment.

        Returns:
            int: The value of key after the increment.

        Example:
            >>> await client.set("key", "10")
            >>> await client.incrby("key" , 5)
                15
        """
        return cast(
            int, await self._execute_command(RequestType.IncrBy, [key, str(amount)])
        )

    async def incrbyfloat(self, key: TEncodable, amount: float) -> float:
        """
        Increment the string representing a floating point number stored at `key` by `amount`.
        By using a negative increment value, the value stored at the `key` is decremented.
        If the key does not exist, it is set to 0 before performing the operation.
        See https://valkey.io/commands/incrbyfloat/ for more details.

        Args:
          key (TEncodable): The key to increment its value.
          amount (float) : The amount to increment.

        Returns:
            float: The value of key after the increment.

        Examples:
            >>> await client.set("key", "10")
            >>> await client.incrbyfloat("key" , 5.5)
                15.55
        """
        return cast(
            float,
            await self._execute_command(RequestType.IncrByFloat, [key, str(amount)]),
        )

    async def setrange(self, key: TEncodable, offset: int, value: TEncodable) -> int:
        """
        Overwrites part of the string stored at `key`, starting at the specified
        `offset`, for the entire length of `value`.
        If the `offset` is larger than the current length of the string at `key`,
        the string is padded with zero bytes to make `offset` fit. Creates the `key`
        if it doesn't exist.

        See https://valkey.io/commands/setrange for more details.

        Args:
            key (TEncodable): The key of the string to update.
            offset (int): The position in the string where `value` should be written.
            value (TEncodable): The value written with `offset`.

        Returns:
            int: The length of the string stored at `key` after it was modified.

        Examples:
            >>> await client.set("key", "Hello World")
            >>> await client.setrange("key", 6, "Glide")
                11  # The length of the string stored at `key` after it was modified.
        """
        return cast(
            int,
            await self._execute_command(
                RequestType.SetRange, [key, str(offset), value]
            ),
        )

    async def mset(self, key_value_map: Mapping[TEncodable, TEncodable]) -> TOK:
        """
        Set multiple keys to multiple values in a single atomic operation.
        See https://valkey.io/commands/mset/ for more details.

        Note:
            When in cluster mode, the command may route to multiple nodes when keys in `key_value_map` map to different hash slots.

        Args:
            key_value_map (Mapping[TEncodable, TEncodable]): A map of key value pairs.

        Returns:
            OK: a simple OK response.

        Example:
            >>> await client.mset({"key" : "value", "key2": "value2"})
                'OK'
        """
        parameters: List[TEncodable] = []
        for pair in key_value_map.items():
            parameters.extend(pair)
        return cast(TOK, await self._execute_command(RequestType.MSet, parameters))

    async def msetnx(self, key_value_map: Mapping[TEncodable, TEncodable]) -> bool:
        """
        Sets multiple keys to values if the key does not exist. The operation is atomic, and if one or
        more keys already exist, the entire operation fails.

        Note:
            When in cluster mode, all keys in `key_value_map` must map to the same hash slot.

        See https://valkey.io/commands/msetnx/ for more details.

        Args:
            key_value_map (Mapping[TEncodable, TEncodable]): A key-value map consisting of keys and their respective values to set.

        Returns:
            bool: True if all keys were set. False if no key was set.

        Examples:
            >>> await client.msetnx({"key1": "value1", "key2": "value2"})
                True
            >>> await client.msetnx({"key2": "value4", "key3": "value5"})
                False
        """
        parameters: List[TEncodable] = []
        for pair in key_value_map.items():
            parameters.extend(pair)
        return cast(
            bool,
            await self._execute_command(RequestType.MSetNX, parameters),
        )

    async def mget(self, keys: List[TEncodable]) -> List[Optional[bytes]]:
        """
        Retrieve the values of multiple keys.
        See https://valkey.io/commands/mget/ for more details.

        Note:
            When in cluster mode, the command may route to multiple nodes when `keys` map to different hash slots.

        Args:
            keys (List[TEncodable]): A list of keys to retrieve values for.

        Returns:
            List[Optional[bytes]]: A list of values corresponding to the provided keys. If a key is not found,
            its corresponding value in the list will be None.

        Examples:
            >>> await client.set("key1", "value1")
            >>> await client.set("key2", "value2")
            >>> await client.mget(["key1", "key2"])
                [b'value1' , b'value2']
        """
        return cast(
            List[Optional[bytes]], await self._execute_command(RequestType.MGet, keys)
        )

    async def decr(self, key: TEncodable) -> int:
        """
        Decrement the number stored at `key` by one. If the key does not exist, it is set to 0 before performing the
        operation.
        See https://valkey.io/commands/decr/ for more details.

        Args:
          key (TEncodable): The key to increment its value.

        Returns:
            int: The value of key after the decrement.

        Examples:
            >>> await client.set("key", "10")
            >>> await client.decr("key")
                9
        """
        return cast(int, await self._execute_command(RequestType.Decr, [key]))

    async def decrby(self, key: TEncodable, amount: int) -> int:
        """
        Decrements the number stored at `key` by `amount`. If the key does not exist, it is set to 0 before performing
        the operation.
        See https://valkey.io/commands/decrby/ for more details.

        Args:
          key (TEncodable): The key to decrement its value.
          amount (int) : The amount to decrement.

        Returns:
            int: The value of key after the decrement.

        Example:
            >>> await client.set("key", "10")
            >>> await client.decrby("key" , 5)
                5
        """
        return cast(
            int, await self._execute_command(RequestType.DecrBy, [key, str(amount)])
        )

    async def touch(self, keys: List[TEncodable]) -> int:
        """
        Updates the last access time of specified keys.

        See https://valkey.io/commands/touch/ for details.

        Note:
            When in cluster mode, the command may route to multiple nodes when `keys` map to different hash slots.

        Args:
            keys (List[TEncodable]): The keys to update last access time.

        Returns:
            int: The number of keys that were updated, a key is ignored if it doesn't exist.

        Examples:
            >>> await client.set("myKey1", "value1")
            >>> await client.set("myKey2", "value2")
            >>> await client.touch(["myKey1", "myKey2", "nonExistentKey"])
                2  # Last access time of 2 keys has been updated.
        """
        return cast(int, await self._execute_command(RequestType.Touch, keys))

    async def hset(
        self,
        key: TEncodable,
        field_value_map: Mapping[TEncodable, TEncodable],
    ) -> int:
        """
        Sets the specified fields to their respective values in the hash stored at `key`.
        See https://valkey.io/commands/hset/ for more details.

        Args:
            key (TEncodable): The key of the hash.
            field_value_map (Mapping[TEncodable, TEncodable]): A field-value map consisting of fields and their corresponding values
            to be set in the hash stored at the specified key.

        Returns:
            int: The number of fields that were added to the hash.

        Example:
            >>> await client.hset("my_hash", {"field": "value", "field2": "value2"})
                2 # Indicates that 2 fields were successfully set in the hash "my_hash".
        """
        field_value_list: List[TEncodable] = [key]
        for pair in field_value_map.items():
            field_value_list.extend(pair)
        return cast(
            int,
            await self._execute_command(RequestType.HSet, field_value_list),
        )

    async def hget(self, key: TEncodable, field: TEncodable) -> Optional[bytes]:
        """
        Retrieves the value associated with `field` in the hash stored at `key`.
        See https://valkey.io/commands/hget/ for more details.

        Args:
            key (TEncodable): The key of the hash.
            field (TEncodable): The field whose value should be retrieved.

        Returns:
            Optional[bytes]: The value associated `field` in the hash.
            Returns None if `field` is not presented in the hash or `key` does not exist.

        Examples:
            >>> await client.hset("my_hash", "field", "value")
            >>> await client.hget("my_hash", "field")
                b"value"
            >>> await client.hget("my_hash", "nonexistent_field")
                None
        """
        return cast(
            Optional[bytes],
            await self._execute_command(RequestType.HGet, [key, field]),
        )

    async def hsetnx(
        self,
        key: TEncodable,
        field: TEncodable,
        value: TEncodable,
    ) -> bool:
        """
        Sets `field` in the hash stored at `key` to `value`, only if `field` does not yet exist.
        If `key` does not exist, a new key holding a hash is created.
        If `field` already exists, this operation has no effect.
        See https://valkey.io/commands/hsetnx/ for more details.

        Args:
            key (TEncodable): The key of the hash.
            field (TEncodable): The field to set the value for.
            value (TEncodable): The value to set.

        Returns:
            bool: True if the field was set, False if the field already existed and was not set.

        Examples:
            >>> await client.hsetnx("my_hash", "field", "value")
                True  # Indicates that the field "field" was set successfully in the hash "my_hash".
            >>> await client.hsetnx("my_hash", "field", "new_value")
                False # Indicates that the field "field" already existed in the hash "my_hash" and was not set again.
        """
        return cast(
            bool,
            await self._execute_command(RequestType.HSetNX, [key, field, value]),
        )

    async def hincrby(self, key: TEncodable, field: TEncodable, amount: int) -> int:
        """
        Increment or decrement the value of a `field` in the hash stored at `key` by the specified amount.
        By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
        If `field` or `key` does not exist, it is set to 0 before performing the operation.
        See https://valkey.io/commands/hincrby/ for more details.

        Args:
            key (TEncodable): The key of the hash.
            field (TEncodable): The field in the hash stored at `key` to increment or decrement its value.
            amount (int): The amount by which to increment or decrement the field's value.
                Use a negative value to decrement.

        Returns:
            int: The value of the specified field in the hash stored at `key` after the increment or decrement.

        Examples:
            >>> await client.hincrby("my_hash", "field1", 5)
                5
        """
        return cast(
            int,
            await self._execute_command(RequestType.HIncrBy, [key, field, str(amount)]),
        )

    async def hincrbyfloat(
        self, key: TEncodable, field: TEncodable, amount: float
    ) -> float:
        """
        Increment or decrement the floating-point value stored at `field` in the hash stored at `key` by the specified
        amount.
        By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
        If `field` or `key` does not exist, it is set to 0 before performing the operation.
        See https://valkey.io/commands/hincrbyfloat/ for more details.

        Args:
            key (TEncodable): The key of the hash.
            field (TEncodable): The field in the hash stored at `key` to increment or decrement its value.
            amount (float): The amount by which to increment or decrement the field's value.
                Use a negative value to decrement.

        Returns:
            float: The value of the specified field in the hash stored at `key` after the increment as a string.

        Examples:
            >>> await client.hincrbyfloat("my_hash", "field1", 2.5)
                "2.5"
        """
        return cast(
            float,
            await self._execute_command(
                RequestType.HIncrByFloat, [key, field, str(amount)]
            ),
        )

    async def hexists(self, key: TEncodable, field: TEncodable) -> bool:
        """
        Check if a field exists in the hash stored at `key`.
        See https://valkey.io/commands/hexists/ for more details.

        Args:
            key (TEncodable): The key of the hash.
            field (TEncodable): The field to check in the hash stored at `key`.

        Returns:
            bool: Returns 'True' if the hash contains the specified field. If the hash does not contain the field,
                or if the key does not exist, it returns 'False'.

        Examples:
            >>> await client.hexists("my_hash", "field1")
                True
            >>> await client.hexists("my_hash", "nonexistent_field")
                False
        """
        return cast(
            bool, await self._execute_command(RequestType.HExists, [key, field])
        )

    async def hgetall(self, key: TEncodable) -> Dict[bytes, bytes]:
        """
        Returns all fields and values of the hash stored at `key`.
        See https://valkey.io/commands/hgetall/ for details.

        Args:
            key (TEncodable): The key of the hash.

        Returns:
            Dict[bytes, bytes]: A dictionary of fields and their values stored in the hash. Every field name in the list is followed by
            its value.
            If `key` does not exist, it returns an empty dictionary.

        Examples:
            >>> await client.hgetall("my_hash")
                {b"field1": b"value1", b"field2": b"value2"}
        """
        return cast(
            Dict[bytes, bytes], await self._execute_command(RequestType.HGetAll, [key])
        )

    async def hmget(
        self, key: TEncodable, fields: List[TEncodable]
    ) -> List[Optional[bytes]]:
        """
        Retrieve the values associated with specified fields in the hash stored at `key`.
        See https://valkey.io/commands/hmget/ for details.

        Args:
            key (TEncodable): The key of the hash.
            fields (List[TEncodable]): The list of fields in the hash stored at `key` to retrieve from the database.

        Returns:
            List[Optional[bytes]]: A list of values associated with the given fields, in the same order as they are requested.
            For every field that does not exist in the hash, a null value is returned.
            If `key` does not exist, it is treated as an empty hash, and the function returns a list of null values.

        Examples:
            >>> await client.hmget("my_hash", ["field1", "field2"])
                [b"value1", b"value2"]  # A list of values associated with the specified fields.
        """
        return cast(
            List[Optional[bytes]],
            await self._execute_command(RequestType.HMGet, [key] + fields),
        )

    async def hdel(self, key: TEncodable, fields: List[TEncodable]) -> int:
        """
        Remove specified fields from the hash stored at `key`.
        See https://valkey.io/commands/hdel/ for more details.

        Args:
            key (TEncodable): The key of the hash.
            fields (List[TEncodable]): The list of fields to remove from the hash stored at `key`.

        Returns:
            int: The number of fields that were removed from the hash, excluding specified but non-existing fields.
            If `key` does not exist, it is treated as an empty hash, and the function returns 0.

        Examples:
            >>> await client.hdel("my_hash", ["field1", "field2"])
                2  # Indicates that two fields were successfully removed from the hash.
        """
        return cast(int, await self._execute_command(RequestType.HDel, [key] + fields))

    async def hlen(self, key: TEncodable) -> int:
        """
        Returns the number of fields contained in the hash stored at `key`.

        See https://valkey.io/commands/hlen/ for more details.

        Args:
            key (TEncodable): The key of the hash.

        Returns:
            int: The number of fields in the hash, or 0 when the key does not exist.
            If `key` holds a value that is not a hash, an error is returned.

        Examples:
            >>> await client.hlen("my_hash")
                3
            >>> await client.hlen("non_existing_key")
                0
        """
        return cast(int, await self._execute_command(RequestType.HLen, [key]))

    async def hvals(self, key: TEncodable) -> List[bytes]:
        """
        Returns all values in the hash stored at `key`.

        See https://valkey.io/commands/hvals/ for more details.

        Args:
            key (TEncodable): The key of the hash.

        Returns:
            List[bytes]: A list of values in the hash, or an empty list when the key does not exist.

        Examples:
           >>> await client.hvals("my_hash")
               [b"value1", b"value2", b"value3"]  # Returns all the values stored in the hash "my_hash".
        """
        return cast(List[bytes], await self._execute_command(RequestType.HVals, [key]))

    async def hkeys(self, key: TEncodable) -> List[bytes]:
        """
        Returns all field names in the hash stored at `key`.

        See https://valkey.io/commands/hkeys/ for more details.

        Args:
            key (TEncodable): The key of the hash.

        Returns:
            List[bytes]: A list of field names for the hash, or an empty list when the key does not exist.

        Examples:
            >>> await client.hkeys("my_hash")
                [b"field1", b"field2", b"field3"]  # Returns all the field names stored in the hash "my_hash".
        """
        return cast(List[bytes], await self._execute_command(RequestType.HKeys, [key]))

    async def hrandfield(self, key: TEncodable) -> Optional[bytes]:
        """
        Returns a random field name from the hash value stored at `key`.

        See https://valkey.io/commands/hrandfield for more details.

        Args:
            key (TEncodable): The key of the hash.

        Returns:
            Optional[bytes]: A random field name from the hash stored at `key`.
            If the hash does not exist or is empty, None will be returned.

        Examples:
            >>> await client.hrandfield("my_hash")
                b"field1"  # A random field name stored in the hash "my_hash".
        """
        return cast(
            Optional[bytes], await self._execute_command(RequestType.HRandField, [key])
        )

    async def hrandfield_count(self, key: TEncodable, count: int) -> List[bytes]:
        """
        Retrieves up to `count` random field names from the hash value stored at `key`.

        See https://valkey.io/commands/hrandfield for more details.

        Args:
            key (TEncodable): The key of the hash.
            count (int): The number of field names to return.
                If `count` is positive, returns unique elements.
                If `count` is negative, allows for duplicates elements.

        Returns:
            List[bytes]: A list of random field names from the hash.
            If the hash does not exist or is empty, the response will be an empty list.

        Examples:
            >>> await client.hrandfield_count("my_hash", -3)
                [b"field1", b"field1", b"field2"]  # Non-distinct, random field names stored in the hash "my_hash".
            >>> await client.hrandfield_count("non_existing_hash", 3)
                []  # Empty list
        """
        return cast(
            List[bytes],
            await self._execute_command(RequestType.HRandField, [key, str(count)]),
        )

    async def hrandfield_withvalues(
        self, key: TEncodable, count: int
    ) -> List[List[bytes]]:
        """
        Retrieves up to `count` random field names along with their values from the hash value stored at `key`.

        See https://valkey.io/commands/hrandfield for more details.

        Args:
            key (TEncodable): The key of the hash.
            count (int): The number of field names to return.
                If `count` is positive, returns unique elements.
                If `count` is negative, allows for duplicates elements.

        Returns:
            List[List[bytes]]: A list of `[field_name, value]` lists, where `field_name` is a random field name from the
            hash and `value` is the associated value of the field name.
            If the hash does not exist or is empty, the response will be an empty list.

        Examples:
            >>> await client.hrandfield_withvalues("my_hash", -3)
                [[b"field1", b"value1"], [b"field1", b"value1"], [b"field2", b"value2"]]
        """
        return cast(
            List[List[bytes]],
            await self._execute_command(
                RequestType.HRandField, [key, str(count), "WITHVALUES"]
            ),
        )

    async def hstrlen(self, key: TEncodable, field: TEncodable) -> int:
        """
        Returns the string length of the value associated with `field` in the hash stored at `key`.

        See https://valkey.io/commands/hstrlen/ for more details.

        Args:
            key (TEncodable): The key of the hash.
            field (TEncodable): The field in the hash.

        Returns:
            int: The string length or 0 if `field` or `key` does not exist.

        Examples:
            >>> await client.hset("my_hash", "field", "value")
            >>> await client.hstrlen("my_hash", "my_field")
                5
        """
        return cast(
            int,
            await self._execute_command(RequestType.HStrlen, [key, field]),
        )

    async def lpush(self, key: TEncodable, elements: List[TEncodable]) -> int:
        """
        Insert all the specified values at the head of the list stored at `key`.
        `elements` are inserted one after the other to the head of the list, from the leftmost element
        to the rightmost element. If `key` does not exist, it is created as empty list before performing the push operations.
        See https://valkey.io/commands/lpush/ for more details.

        Args:
            key (TEncodable): The key of the list.
            elements (List[TEncodable]): The elements to insert at the head of the list stored at `key`.

        Returns:
            int: The length of the list after the push operations.

        Examples:
            >>> await client.lpush("my_list", ["value2", "value3"])
                3 # Indicates that the new length of the list is 3 after the push operation.
            >>> await client.lpush("nonexistent_list", ["new_value"])
                1
        """
        return cast(
            int, await self._execute_command(RequestType.LPush, [key] + elements)
        )

    async def lpushx(self, key: TEncodable, elements: List[TEncodable]) -> int:
        """
        Inserts all the specified values at the head of the list stored at `key`, only if `key` exists and holds a list.
        If `key` is not a list, this performs no operation.

        See https://valkey.io/commands/lpushx/ for more details.

        Args:
            key (TEncodable): The key of the list.
            elements (List[TEncodable]): The elements to insert at the head of the list stored at `key`.

        Returns:
            int: The length of the list after the push operation.

        Examples:
            >>> await client.lpushx("my_list", ["value1", "value2"])
                3 # Indicates that 2 elements we're added to the list "my_list", and the new length of the list is 3.
            >>> await client.lpushx("nonexistent_list", ["new_value"])
                0 # Indicates that the list "nonexistent_list" does not exist, so "new_value" could not be pushed.
        """
        return cast(
            int, await self._execute_command(RequestType.LPushX, [key] + elements)
        )

    async def lpop(self, key: TEncodable) -> Optional[bytes]:
        """
        Remove and return the first elements of the list stored at `key`.
        The command pops a single element from the beginning of the list.
        See https://valkey.io/commands/lpop/ for details.

        Args:
            key (TEncodable): The key of the list.

        Returns:
            Optional[bytes]: The value of the first element.
            If `key` does not exist, None will be returned.

        Examples:
            >>> await client.lpop("my_list")
                b"value1"
            >>> await client.lpop("non_exiting_key")
                None
        """
        return cast(
            Optional[bytes],
            await self._execute_command(RequestType.LPop, [key]),
        )

    async def lpop_count(self, key: TEncodable, count: int) -> Optional[List[bytes]]:
        """
        Remove and return up to `count` elements from the list stored at `key`, depending on the list's length.
        See https://valkey.io/commands/lpop/ for details.

        Args:
            key (TEncodable): The key of the list.
            count (int): The count of elements to pop from the list.

        Returns:
            Optional[List[bytes]]: A a list of popped elements will be returned depending on the list's length.
            If `key` does not exist, None will be returned.

        Examples:
            >>> await client.lpop_count("my_list", 2)
                [b"value1", b"value2"]
            >>> await client.lpop_count("non_exiting_key" , 3)
                None
        """
        return cast(
            Optional[List[bytes]],
            await self._execute_command(RequestType.LPop, [key, str(count)]),
        )

    async def blpop(
        self, keys: List[TEncodable], timeout: float
    ) -> Optional[List[bytes]]:
        """
        Pops an element from the head of the first list that is non-empty, with the given keys being checked in the
        order that they are given. Blocks the connection when there are no elements to pop from any of the given lists.
        See https://valkey.io/commands/blpop for details.

        Notes:
            1. When in cluster mode, all `keys` must map to the same hash slot.
            2. `BLPOP` is a client blocking command, see https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands for more details and best practices.

        Args:
            keys (List[TEncodable]): The keys of the lists to pop from.
            timeout (float): The number of seconds to wait for a blocking operation to complete. A value of 0 will block indefinitely.

        Returns:
            Optional[List[bytes]]: A two-element list containing the key from which the element was popped and the value of the
                popped element, formatted as `[key, value]`. If no element could be popped and the `timeout` expired, returns None.

        Examples:
            >>> await client.blpop(["list1", "list2"], 0.5)
                [b"list1", b"element"]  # "element" was popped from the head of the list with key "list1"
        """
        return cast(
            Optional[List[bytes]],
            await self._execute_command(RequestType.BLPop, keys + [str(timeout)]),
        )

    async def lmpop(
        self,
        keys: List[TEncodable],
        direction: ListDirection,
        count: Optional[int] = None,
    ) -> Optional[Mapping[bytes, List[bytes]]]:
        """
        Pops one or more elements from the first non-empty list from the provided `keys`.

        When in cluster mode, all `keys` must map to the same hash slot.

        See https://valkey.io/commands/lmpop/ for details.

        Args:
            keys (List[TEncodable]): An array of keys of lists.
            direction (ListDirection): The direction based on which elements are popped from (`ListDirection.LEFT` or `ListDirection.RIGHT`).
            count (Optional[int]): The maximum number of popped elements. If not provided, defaults to popping a single element.

        Returns:
            Optional[Mapping[bytes, List[bytes]]]: A map of `key` name mapped to an array of popped elements, or None if no elements could be popped.

        Examples:
            >>> await client.lpush("testKey", ["one", "two", "three"])
            >>> await client.lmpop(["testKey"], ListDirection.LEFT, 2)
               {b"testKey": [b"three", b"two"]}

        Since: Valkey version 7.0.0.
        """
        args = [str(len(keys)), *keys, direction.value]
        if count is not None:
            args += ["COUNT", str(count)]

        return cast(
            Optional[Mapping[bytes, List[bytes]]],
            await self._execute_command(RequestType.LMPop, args),
        )

    async def blmpop(
        self,
        keys: List[TEncodable],
        direction: ListDirection,
        timeout: float,
        count: Optional[int] = None,
    ) -> Optional[Mapping[bytes, List[bytes]]]:
        """
        Blocks the connection until it pops one or more elements from the first non-empty list from the provided `keys`.

        `BLMPOP` is the blocking variant of `LMPOP`.

        Notes:
            1. When in cluster mode, all `keys` must map to the same hash slot.
            2. `BLMPOP` is a client blocking command, see https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands for more details and best practices.

        See https://valkey.io/commands/blmpop/ for details.

        Args:
            keys (List[TEncodable]): An array of keys of lists.
            direction (ListDirection): The direction based on which elements are popped from (`ListDirection.LEFT` or `ListDirection.RIGHT`).
            timeout (float): The number of seconds to wait for a blocking operation to complete. A value of `0` will block indefinitely.
            count (Optional[int]): The maximum number of popped elements. If not provided, defaults to popping a single element.

        Returns:
            Optional[Mapping[bytes, List[bytes]]]: A map of `key` name mapped to an array of popped elements, or None if no elements could be popped and the timeout expired.

        Examples:
            >>> await client.lpush("testKey", ["one", "two", "three"])
            >>> await client.blmpop(["testKey"], ListDirection.LEFT, 0.1, 2)
               {b"testKey": [b"three", b"two"]}

        Since: Valkey version 7.0.0.
        """
        args = [str(timeout), str(len(keys)), *keys, direction.value]
        if count is not None:
            args += ["COUNT", str(count)]

        return cast(
            Optional[Mapping[bytes, List[bytes]]],
            await self._execute_command(RequestType.BLMPop, args),
        )

    async def lrange(self, key: TEncodable, start: int, end: int) -> List[bytes]:
        """
        Retrieve the specified elements of the list stored at `key` within the given range.
        The offsets `start` and `end` are zero-based indexes, with 0 being the first element of the list, 1 being the next
        element and so on. These offsets can also be negative numbers indicating offsets starting at the end of the list,
        with -1 being the last element of the list, -2 being the penultimate, and so on.
        See https://valkey.io/commands/lrange/ for details.

        Args:
            key (TEncodable): The key of the list.
            start (int): The starting point of the range.
            end (int): The end of the range.

        Returns:
            List[bytes]: A list of elements within the specified range.
            If `start` exceeds the `end` of the list, or if `start` is greater than `end`, an empty list will be returned.
            If `end` exceeds the actual end of the list, the range will stop at the actual end of the list.
            If `key` does not exist an empty list will be returned.

        Examples:
            >>> await client.lrange("my_list", 0, 2)
                [b"value1", b"value2", b"value3"]
            >>> await client.lrange("my_list", -2, -1)
                [b"value2", b"value3"]
            >>> await client.lrange("non_exiting_key", 0, 2)
                []
        """
        return cast(
            List[bytes],
            await self._execute_command(
                RequestType.LRange, [key, str(start), str(end)]
            ),
        )

    async def lindex(
        self,
        key: TEncodable,
        index: int,
    ) -> Optional[bytes]:
        """
        Returns the element at `index` in the list stored at `key`.

        The index is zero-based, so 0 means the first element, 1 the second element and so on.
        Negative indices can be used to designate elements starting at the tail of the list.
        Here, -1 means the last element, -2 means the penultimate and so forth.

        See https://valkey.io/commands/lindex/ for more details.

        Args:
            key (TEncodable): The key of the list.
            index (int): The index of the element in the list to retrieve.

        Returns:
            Optional[bytes]: The element at `index` in the list stored at `key`.
                If `index` is out of range or if `key` does not exist, None is returned.

        Examples:
            >>> await client.lindex("my_list", 0)
                b'value1'  # Returns the first element in the list stored at 'my_list'.
            >>> await client.lindex("my_list", -1)
                b'value3'  # Returns the last element in the list stored at 'my_list'.
        """
        return cast(
            Optional[bytes],
            await self._execute_command(RequestType.LIndex, [key, str(index)]),
        )

    async def lset(self, key: TEncodable, index: int, element: TEncodable) -> TOK:
        """
        Sets the list element at `index` to `element`.

        The index is zero-based, so `0` means the first element, `1` the second element and so on.
        Negative indices can be used to designate elements starting at the tail of the list.
        Here, `-1` means the last element, `-2` means the penultimate and so forth.

        See https://valkey.io/commands/lset/ for details.

        Args:
            key (TEncodable): The key of the list.
            index (int): The index of the element in the list to be set.
            element (TEncodable): The new element to set at the specified index.

        Returns:
            TOK: A simple `OK` response.

        Examples:
            >>> await client.lset("testKey", 1, "two")
                OK
        """
        return cast(
            TOK,
            await self._execute_command(RequestType.LSet, [key, str(index), element]),
        )

    async def rpush(self, key: TEncodable, elements: List[TEncodable]) -> int:
        """
        Inserts all the specified values at the tail of the list stored at `key`.
        `elements` are inserted one after the other to the tail of the list, from the leftmost element
        to the rightmost element. If `key` does not exist, it is created as empty list before performing the push operations.
        See https://valkey.io/commands/rpush/ for more details.

        Args:
            key (TEncodable): The key of the list.
            elements (List[TEncodable]): The elements to insert at the tail of the list stored at `key`.

        Returns:
            int: The length of the list after the push operations.

        Examples:
            >>> await client.rpush("my_list", ["value2", "value3"])
                3 # Indicates that the new length of the list is 3 after the push operation.
            >>> await client.rpush("nonexistent_list", ["new_value"])
                1
        """
        return cast(
            int, await self._execute_command(RequestType.RPush, [key] + elements)
        )

    async def rpushx(self, key: TEncodable, elements: List[TEncodable]) -> int:
        """
        Inserts all the specified values at the tail of the list stored at `key`, only if `key` exists and holds a list.
        If `key` is not a list, this performs no operation.

        See https://valkey.io/commands/rpushx/ for more details.

        Args:
            key (TEncodable): The key of the list.
            elements (List[TEncodable]): The elements to insert at the tail of the list stored at `key`.

        Returns:
            int: The length of the list after the push operation.

        Examples:
            >>> await client.rpushx("my_list", ["value1", "value2"])
                3 # Indicates that 2 elements we're added to the list "my_list", and the new length of the list is 3.
            >>> await client.rpushx("nonexistent_list", ["new_value"])
                0 # Indicates that the list "nonexistent_list" does not exist, so "new_value" could not be pushed.
        """
        return cast(
            int, await self._execute_command(RequestType.RPushX, [key] + elements)
        )

    async def rpop(self, key: TEncodable) -> Optional[bytes]:
        """
        Removes and returns the last elements of the list stored at `key`.
        The command pops a single element from the end of the list.
        See https://valkey.io/commands/rpop/ for details.

        Args:
            key (TEncodable): The key of the list.

        Returns:
            Optional[bytes]: The value of the last element.
            If `key` does not exist, None will be returned.

        Examples:
            >>> await client.rpop("my_list")
                b"value1"
            >>> await client.rpop("non_exiting_key")
                None
        """
        return cast(
            Optional[bytes],
            await self._execute_command(RequestType.RPop, [key]),
        )

    async def rpop_count(self, key: TEncodable, count: int) -> Optional[List[bytes]]:
        """
        Removes and returns up to `count` elements from the list stored at `key`, depending on the list's length.
        See https://valkey.io/commands/rpop/ for details.

        Args:
            key (TEncodable): The key of the list.
            count (int): The count of elements to pop from the list.

        Returns:
            Optional[List[bytes]: A list of popped elements will be returned depending on the list's length.
            If `key` does not exist, None will be returned.

        Examples:
            >>> await client.rpop_count("my_list", 2)
                [b"value1", b"value2"]
            >>> await client.rpop_count("non_exiting_key" , 7)
                None
        """
        return cast(
            Optional[List[bytes]],
            await self._execute_command(RequestType.RPop, [key, str(count)]),
        )

    async def brpop(
        self, keys: List[TEncodable], timeout: float
    ) -> Optional[List[bytes]]:
        """
        Pops an element from the tail of the first list that is non-empty, with the given keys being checked in the
        order that they are given. Blocks the connection when there are no elements to pop from any of the given lists.
        See https://valkey.io/commands/brpop for details.

        Notes:
            1. When in cluster mode, all `keys` must map to the same hash slot.
            2. `BRPOP` is a client blocking command, see https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands for more details and best practices.

        Args:
            keys (List[TEncodable]): The keys of the lists to pop from.
            timeout (float): The number of seconds to wait for a blocking operation to complete. A value of 0 will block indefinitely.

        Returns:
            Optional[List[bytes]]: A two-element list containing the key from which the element was popped and the value of the
                popped element, formatted as `[key, value]`. If no element could be popped and the `timeout` expired, returns None.

        Examples:
            >>> await client.brpop(["list1", "list2"], 0.5)
                [b"list1", b"element"]  # "element" was popped from the tail of the list with key "list1"
        """
        return cast(
            Optional[List[bytes]],
            await self._execute_command(RequestType.BRPop, keys + [str(timeout)]),
        )

    async def linsert(
        self,
        key: TEncodable,
        position: InsertPosition,
        pivot: TEncodable,
        element: TEncodable,
    ) -> int:
        """
        Inserts `element` in the list at `key` either before or after the `pivot`.

        See https://valkey.io/commands/linsert/ for details.

        Args:
            key (TEncodable): The key of the list.
            position (InsertPosition): The relative position to insert into - either `InsertPosition.BEFORE` or
                `InsertPosition.AFTER` the `pivot`.
            pivot (TEncodable): An element of the list.
            element (TEncodable): The new element to insert.

        Returns:
            int: The list length after a successful insert operation.
                If the `key` doesn't exist returns `-1`.
                If the `pivot` wasn't found, returns `0`.

        Examples:
            >>> await client.linsert("my_list", InsertPosition.BEFORE, "World", "There")
                3 # "There" was inserted before "World", and the new length of the list is 3.
        """
        return cast(
            int,
            await self._execute_command(
                RequestType.LInsert, [key, position.value, pivot, element]
            ),
        )

    async def lmove(
        self,
        source: TEncodable,
        destination: TEncodable,
        where_from: ListDirection,
        where_to: ListDirection,
    ) -> Optional[bytes]:
        """
        Atomically pops and removes the left/right-most element to the list stored at `source`
        depending on `where_from`, and pushes the element at the first/last element of the list
        stored at `destination` depending on `where_to`.

        When in cluster mode, both `source` and `destination` must map to the same hash slot.

        See https://valkey.io/commands/lmove/ for details.

        Args:
            source (TEncodable): The key to the source list.
            destination (TEncodable): The key to the destination list.
            where_from (ListDirection): The direction to remove the element from (`ListDirection.LEFT` or `ListDirection.RIGHT`).
            where_to (ListDirection): The direction to add the element to (`ListDirection.LEFT` or `ListDirection.RIGHT`).

        Returns:
            Optional[bytes]: The popped element, or None if `source` does not exist.

        Examples:
            >>> client.lpush("testKey1", ["two", "one"])
            >>> client.lpush("testKey2", ["four", "three"])
            >>> await client.lmove("testKey1", "testKey2", ListDirection.LEFT, ListDirection.LEFT)
                b"one"
            >>> updated_array1 = await client.lrange("testKey1", 0, -1)
                [b"two"]
            >>> await client.lrange("testKey2", 0, -1)
                [b"one", b"three", b"four"]

        Since: Valkey version 6.2.0.
        """
        return cast(
            Optional[bytes],
            await self._execute_command(
                RequestType.LMove,
                [source, destination, where_from.value, where_to.value],
            ),
        )

    async def blmove(
        self,
        source: TEncodable,
        destination: TEncodable,
        where_from: ListDirection,
        where_to: ListDirection,
        timeout: float,
    ) -> Optional[bytes]:
        """
        Blocks the connection until it pops atomically and removes the left/right-most element to the
        list stored at `source` depending on `where_from`, and pushes the element at the first/last element
        of the list stored at `destination` depending on `where_to`.
        `BLMOVE` is the blocking variant of `LMOVE`.

        Notes:
            1. When in cluster mode, both `source` and `destination` must map to the same hash slot.
            2. `BLMOVE` is a client blocking command, see https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands for more details and best practices.

        See https://valkey.io/commands/blmove/ for details.

        Args:
            source (TEncodable): The key to the source list.
            destination (TEncodable): The key to the destination list.
            where_from (ListDirection): The direction to remove the element from (`ListDirection.LEFT` or `ListDirection.RIGHT`).
            where_to (ListDirection): The direction to add the element to (`ListDirection.LEFT` or `ListDirection.RIGHT`).
            timeout (float): The number of seconds to wait for a blocking operation to complete. A value of `0` will block indefinitely.

        Returns:
            Optional[bytes]: The popped element, or None if `source` does not exist or if the operation timed-out.

        Examples:
            >>> await client.lpush("testKey1", ["two", "one"])
            >>> await client.lpush("testKey2", ["four", "three"])
            >>> await client.blmove("testKey1", "testKey2", ListDirection.LEFT, ListDirection.LEFT, 0.1)
                b"one"
            >>> await client.lrange("testKey1", 0, -1)
                [b"two"]
            >>> updated_array2 = await client.lrange("testKey2", 0, -1)
                [b"one", b"three", bb"four"]

        Since: Valkey version 6.2.0.
        """
        return cast(
            Optional[bytes],
            await self._execute_command(
                RequestType.BLMove,
                [source, destination, where_from.value, where_to.value, str(timeout)],
            ),
        )

    async def sadd(self, key: TEncodable, members: List[TEncodable]) -> int:
        """
        Add specified members to the set stored at `key`.
        Specified members that are already a member of this set are ignored.
        If `key` does not exist, a new set is created before adding `members`.
        See https://valkey.io/commands/sadd/ for more details.

        Args:
            key (TEncodable): The key where members will be added to its set.
            members (List[TEncodable]): A list of members to add to the set stored at `key`.

        Returns:
            int: The number of members that were added to the set, excluding members already present.

        Examples:
            >>> await client.sadd("my_set", ["member1", "member2"])
                2
        """
        return cast(int, await self._execute_command(RequestType.SAdd, [key] + members))

    async def srem(self, key: TEncodable, members: List[TEncodable]) -> int:
        """
        Remove specified members from the set stored at `key`.
        Specified members that are not a member of this set are ignored.
        See https://valkey.io/commands/srem/ for details.

        Args:
            key (TEncodable): The key from which members will be removed.
            members (List[TEncodable]): A list of members to remove from the set stored at `key`.

        Returns:
            int: The number of members that were removed from the set, excluding non-existing members.
                If `key` does not exist, it is treated as an empty set and this command returns 0.

        Examples:
            >>> await client.srem("my_set", ["member1", "member2"])
                2
        """
        return cast(int, await self._execute_command(RequestType.SRem, [key] + members))

    async def smembers(self, key: TEncodable) -> Set[bytes]:
        """
        Retrieve all the members of the set value stored at `key`.
        See https://valkey.io/commands/smembers/ for details.

        Args:
            key (TEncodable): The key from which to retrieve the set members.

        Returns:
            Set[bytes]: A set of all members of the set.
                If `key` does not exist an empty set will be returned.

        Examples:
            >>> await client.smembers("my_set")
                {b"member1", b"member2", b"member3"}
        """
        return cast(
            Set[bytes], await self._execute_command(RequestType.SMembers, [key])
        )

    async def scard(self, key: TEncodable) -> int:
        """
        Retrieve the set cardinality (number of elements) of the set stored at `key`.
        See https://valkey.io/commands/scard/ for details.

        Args:
            key (TEncodable): The key from which to retrieve the number of set members.

        Returns:
            int: The cardinality (number of elements) of the set, or 0 if the key does not exist.

        Examples:
            >>> await client.scard("my_set")
                3
        """
        return cast(int, await self._execute_command(RequestType.SCard, [key]))

    async def spop(self, key: TEncodable) -> Optional[bytes]:
        """
        Removes and returns one random member from the set stored at `key`.

        See https://valkey-io.github.io/commands/spop/ for more details.
        To pop multiple members, see `spop_count`.

        Args:
            key (TEncodable): The key of the set.

        Returns:
            Optional[bytes]: The value of the popped member.
            If `key` does not exist, None will be returned.

        Examples:
            >>> await client.spop("my_set")
                b"value1" # Removes and returns a random member from the set "my_set".
            >>> await client.spop("non_exiting_key")
                None
        """
        return cast(
            Optional[bytes], await self._execute_command(RequestType.SPop, [key])
        )

    async def spop_count(self, key: TEncodable, count: int) -> Set[bytes]:
        """
        Removes and returns up to `count` random members from the set stored at `key`, depending on the set's length.

        See https://valkey-io.github.io/commands/spop/ for more details.
        To pop a single member, see `spop`.

        Args:
            key (TEncodable): The key of the set.
            count (int): The count of the elements to pop from the set.

        Returns:
            Set[bytes]: A set of popped elements will be returned depending on the set's length.
                If `key` does not exist, an empty set will be returned.

        Examples:
            >>> await client.spop_count("my_set", 2)
                {b"value1", b"value2"} # Removes and returns 2 random members from the set "my_set".
            >>> await client.spop_count("non_exiting_key", 2)
                Set()
        """
        return cast(
            Set[bytes], await self._execute_command(RequestType.SPop, [key, str(count)])
        )

    async def sismember(
        self,
        key: TEncodable,
        member: TEncodable,
    ) -> bool:
        """
        Returns if `member` is a member of the set stored at `key`.

        See https://valkey.io/commands/sismember/ for more details.

        Args:
            key (TEncodable): The key of the set.
            member (TEncodable): The member to check for existence in the set.

        Returns:
            bool: True if the member exists in the set, False otherwise.
            If `key` doesn't exist, it is treated as an empty set and the command returns False.

        Examples:
            >>> await client.sismember("my_set", "member1")
                True  # Indicates that "member1" exists in the set "my_set".
            >>> await client.sismember("my_set", "non_existing_member")
                False  # Indicates that "non_existing_member" does not exist in the set "my_set".
        """
        return cast(
            bool,
            await self._execute_command(RequestType.SIsMember, [key, member]),
        )

    async def smove(
        self,
        source: TEncodable,
        destination: TEncodable,
        member: TEncodable,
    ) -> bool:
        """
        Moves `member` from the set at `source` to the set at `destination`, removing it from the source set. Creates a
        new destination set if needed. The operation is atomic.

        See https://valkey.io/commands/smove for more details.

        Note:
            When in cluster mode, `source` and `destination` must map to the same hash slot.

        Args:
            source (TEncodable): The key of the set to remove the element from.
            destination (TEncodable): The key of the set to add the element to.
            member (TEncodable): The set element to move.

        Returns:
            bool: True on success, or False if the `source` set does not exist or the element is not a member of the source set.

        Examples:
            >>> await client.smove("set1", "set2", "member1")
                True  # "member1" was moved from "set1" to "set2".
        """
        return cast(
            bool,
            await self._execute_command(
                RequestType.SMove, [source, destination, member]
            ),
        )

    async def sunion(self, keys: List[TEncodable]) -> Set[bytes]:
        """
        Gets the union of all the given sets.

        See https://valkey.io/commands/sunion for more details.

        Note:
            When in cluster mode, all `keys` must map to the same hash slot.

        Args:
            keys (List[TEncodable]): The keys of the sets.

        Returns:
            Set[bytes]: A set of members which are present in at least one of the given sets.
                If none of the sets exist, an empty set will be returned.

        Examples:
            >>> await client.sadd("my_set1", ["member1", "member2"])
            >>> await client.sadd("my_set2", ["member2", "member3"])
            >>> await client.sunion(["my_set1", "my_set2"])
                {b"member1", b"member2", b"member3"} # sets "my_set1" and "my_set2" have three unique members
            >>> await client.sunion(["my_set1", "non_existing_set"])
                {b"member1", b"member2"}
        """
        return cast(Set[bytes], await self._execute_command(RequestType.SUnion, keys))

    async def sunionstore(
        self,
        destination: TEncodable,
        keys: List[TEncodable],
    ) -> int:
        """
        Stores the members of the union of all given sets specified by `keys` into a new set at `destination`.

        See https://valkey.io/commands/sunionstore for more details.

        Note:
            When in cluster mode, all keys in `keys` and `destination` must map to the same hash slot.

        Args:
            destination (TEncodable): The key of the destination set.
            keys (List[TEncodable]): The keys from which to retrieve the set members.

        Returns:
            int: The number of elements in the resulting set.

        Examples:
            >>> await client.sadd("set1", ["member1"])
            >>> await client.sadd("set2", ["member2"])
            >>> await client.sunionstore("my_set", ["set1", "set2"])
                2  # Two elements were stored in "my_set", and those two members are the union of "set1" and "set2".
        """
        return cast(
            int,
            await self._execute_command(RequestType.SUnionStore, [destination] + keys),
        )

    async def sdiffstore(self, destination: TEncodable, keys: List[TEncodable]) -> int:
        """
        Stores the difference between the first set and all the successive sets in `keys` into a new set at
        `destination`.

        See https://valkey.io/commands/sdiffstore for more details.

        Note:
            When in Cluster mode, all keys in `keys` and `destination` must map to the same hash slot.

        Args:
            destination (TEncodable): The key of the destination set.
            keys (List[TEncodable]): The keys of the sets to diff.

        Returns:
            int: The number of elements in the resulting set.

        Examples:
            >>> await client.sadd("set1", ["member1", "member2"])
            >>> await client.sadd("set2", ["member1"])
            >>> await client.sdiffstore("set3", ["set1", "set2"])
                1  # Indicates that one member was stored in "set3", and that member is the diff between "set1" and "set2".
        """
        return cast(
            int,
            await self._execute_command(RequestType.SDiffStore, [destination] + keys),
        )

    async def sinter(self, keys: List[TEncodable]) -> Set[bytes]:
        """
        Gets the intersection of all the given sets.

        See https://valkey.io/commands/sinter for more details.

        Note:
            When in cluster mode, all `keys` must map to the same hash slot.

        Args:
            keys (List[TEncodable]): The keys of the sets.

        Returns:
            Set[bytes]: A set of members which are present in all given sets.
                If one or more sets do no exist, an empty set will be returned.

        Examples:
            >>> await client.sadd("my_set1", ["member1", "member2"])
            >>> await client.sadd("my_set2", ["member2", "member3"])
            >>> await client.sinter(["my_set1", "my_set2"])
                 {b"member2"} # sets "my_set1" and "my_set2" have one commom member
            >>> await client.sinter([my_set1", "non_existing_set"])
                None
        """
        return cast(Set[bytes], await self._execute_command(RequestType.SInter, keys))

    async def sinterstore(self, destination: TEncodable, keys: List[TEncodable]) -> int:
        """
        Stores the members of the intersection of all given sets specified by `keys` into a new set at `destination`.

        See https://valkey.io/commands/sinterstore for more details.

        Note:
            When in Cluster mode, all `keys` and `destination` must map to the same hash slot.

        Args:
            destination (TEncodable): The key of the destination set.
            keys (List[TEncodable]): The keys from which to retrieve the set members.

        Returns:
            int: The number of elements in the resulting set.

        Examples:
            >>> await client.sadd("my_set1", ["member1", "member2"])
            >>> await client.sadd("my_set2", ["member2", "member3"])
            >>> await client.sinterstore("my_set3", ["my_set1", "my_set2"])
                1  # One element was stored at "my_set3", and that element is the intersection of "my_set1" and "myset2".
        """
        return cast(
            int,
            await self._execute_command(RequestType.SInterStore, [destination] + keys),
        )

    async def sintercard(
        self, keys: List[TEncodable], limit: Optional[int] = None
    ) -> int:
        """
        Gets the cardinality of the intersection of all the given sets.
        Optionally, a `limit` can be specified to stop the computation early if the intersection cardinality reaches the specified limit.

        When in cluster mode, all keys in `keys` must map to the same hash slot.

        See https://valkey.io/commands/sintercard for more details.

        Args:
            keys (List[TEncodable]): A list of keys representing the sets to intersect.
            limit (Optional[int]): An optional limit to the maximum number of intersecting elements to count.
                If specified, the computation stops as soon as the cardinality reaches this limit.

        Returns:
            int: The number of elements in the resulting set of the intersection.

        Examples:
            >>> await client.sadd("set1", {"a", "b", "c"})
            >>> await client.sadd("set2", {"b", "c", "d"})
            >>> await client.sintercard(["set1", "set2"])
            2  # The intersection of "set1" and "set2" contains 2 elements: "b" and "c".

            >>> await client.sintercard(["set1", "set2"], limit=1)
            1  # The computation stops early as the intersection cardinality reaches the limit of 1.
        """
        args: List[TEncodable] = [str(len(keys))]
        args.extend(keys)
        if limit is not None:
            args += ["LIMIT", str(limit)]
        return cast(
            int,
            await self._execute_command(RequestType.SInterCard, args),
        )

    async def sdiff(self, keys: List[TEncodable]) -> Set[bytes]:
        """
        Computes the difference between the first set and all the successive sets in `keys`.

        See https://valkey.io/commands/sdiff for more details.

        Note:
            When in cluster mode, all `keys` must map to the same hash slot.

        Args:
            keys (List[TEncodable]): The keys of the sets to diff

        Returns:
            Set[bytes]: A set of elements representing the difference between the sets.
                If any of the keys in `keys` do not exist, they are treated as empty sets.

        Examples:
            >>> await client.sadd("set1", ["member1", "member2"])
            >>> await client.sadd("set2", ["member1"])
            >>> await client.sdiff("set1", "set2")
                {b"member2"}  # "member2" is in "set1" but not "set2"
        """
        return cast(
            Set[bytes],
            await self._execute_command(RequestType.SDiff, keys),
        )

    async def smismember(
        self, key: TEncodable, members: List[TEncodable]
    ) -> List[bool]:
        """
        Checks whether each member is contained in the members of the set stored at `key`.

        See https://valkey.io/commands/smismember for more details.

        Args:
            key (TEncodable): The key of the set to check.
            members (List[TEncodable]): A list of members to check for existence in the set.

        Returns:
            List[bool]: A list of bool values, each indicating if the respective member exists in the set.

        Examples:
            >>> await client.sadd("set1", ["a", "b", "c"])
            >>> await client.smismember("set1", ["b", "c", "d"])
                [True, True, False]  # "b" and "c" are members of "set1", but "d" is not.
        """
        return cast(
            List[bool],
            await self._execute_command(RequestType.SMIsMember, [key] + members),
        )

    async def ltrim(self, key: TEncodable, start: int, end: int) -> TOK:
        """
        Trim an existing list so that it will contain only the specified range of elements specified.
        The offsets `start` and `end` are zero-based indexes, with 0 being the first element of the list, 1 being the next
        element and so on.
        These offsets can also be negative numbers indicating offsets starting at the end of the list, with -1 being the last
        element of the list, -2 being the penultimate, and so on.
        See https://valkey.io/commands/ltrim/ for more details.

        Args:
            key (TEncodable): The key of the list.
            start (int): The starting point of the range.
            end (int): The end of the range.

        Returns:
            TOK: A simple "OK" response.
                If `start` exceeds the end of the list, or if `start` is greater than `end`, the result will be an empty list
                (which causes `key` to be removed).
                If `end` exceeds the actual end of the list, it will be treated like the last element of the list.
                If `key` does not exist, "OK" will be returned without changes to the database.

        Examples:
            >>> await client.ltrim("my_list", 0, 1)
                "OK"  # Indicates that the list has been trimmed to contain elements from 0 to 1.
        """
        return cast(
            TOK,
            await self._execute_command(RequestType.LTrim, [key, str(start), str(end)]),
        )

    async def lrem(self, key: TEncodable, count: int, element: TEncodable) -> int:
        """
        Removes the first `count` occurrences of elements equal to `element` from the list stored at `key`.
        If `count` is positive, it removes elements equal to `element` moving from head to tail.
        If `count` is negative, it removes elements equal to `element` moving from tail to head.
        If `count` is 0 or greater than the occurrences of elements equal to `element`, it removes all elements
        equal to `element`.
        See https://valkey.io/commands/lrem/ for more details.

        Args:
            key (TEncodable): The key of the list.
            count (int): The count of occurrences of elements equal to `element` to remove.
            element (TEncodable): The element to remove from the list.

        Returns:
            int: The number of removed elements.
                If `key` does not exist, 0 is returned.

        Examples:
            >>> await client.lrem("my_list", 2, "value")
                2  # Removes the first 2 occurrences of "value" in the list.
        """
        return cast(
            int,
            await self._execute_command(RequestType.LRem, [key, str(count), element]),
        )

    async def llen(self, key: TEncodable) -> int:
        """
        Get the length of the list stored at `key`.
        See https://valkey.io/commands/llen/ for details.

        Args:
            key (TEncodable): The key of the list.

        Returns:
            int: The length of the list at the specified key.
                If `key` does not exist, it is interpreted as an empty list and 0 is returned.

        Examples:
            >>> await client.llen("my_list")
                3  # Indicates that there are 3 elements in the list.
        """
        return cast(int, await self._execute_command(RequestType.LLen, [key]))

    async def exists(self, keys: List[TEncodable]) -> int:
        """
        Returns the number of keys in `keys` that exist in the database.
        See https://valkey.io/commands/exists/ for more details.

        Note:
            When in cluster mode, the command may route to multiple nodes when `keys` map to different hash slots.

        Args:
            keys (List[TEncodable]): The list of keys to check.

        Returns:
            int: The number of keys that exist. If the same existing key is mentioned in `keys` multiple times,
                it will be counted multiple times.

        Examples:
            >>> await client.exists(["key1", "key2", "key3"])
                3  # Indicates that all three keys exist in the database.
        """
        return cast(int, await self._execute_command(RequestType.Exists, keys))

    async def unlink(self, keys: List[TEncodable]) -> int:
        """
        Unlink (delete) multiple keys from the database.
        A key is ignored if it does not exist.
        This command, similar to DEL, removes specified keys and ignores non-existent ones.
        However, this command does not block the server, while [DEL](https://valkey.io/commands/del) does.
        See https://valkey.io/commands/unlink/ for more details.

        Note:
            When in cluster mode, the command may route to multiple nodes when `keys` map to different hash slots.

        Args:
            keys (List[TEncodable]): The list of keys to unlink.

        Returns:
            int: The number of keys that were unlinked.

        Examples:
            >>> await client.unlink(["key1", "key2", "key3"])
                3  # Indicates that all three keys were unlinked from the database.
        """
        return cast(int, await self._execute_command(RequestType.Unlink, keys))

    async def expire(
        self,
        key: TEncodable,
        seconds: int,
        option: Optional[ExpireOptions] = None,
    ) -> bool:
        """
        Sets a timeout on `key` in seconds. After the timeout has expired, the key will automatically be deleted.
        If `key` already has an existing expire set, the time to live is updated to the new value.
        If `seconds` is a non-positive number, the key will be deleted rather than expired.
        The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
        See https://valkey.io/commands/expire/ for more details.

        Args:
            key (TEncodable): The key to set a timeout on.
            seconds (int): The timeout in seconds.
            option (ExpireOptions, optional): The expire option.

        Returns:
            bool: 'True' if the timeout was set, 'False' if the timeout was not set (e.g., the key doesn't exist or the operation is
                skipped due to the provided arguments).

        Examples:
            >>> await client.expire("my_key", 60)
                True  # Indicates that a timeout of 60 seconds has been set for "my_key."
        """
        args: List[TEncodable] = (
            [key, str(seconds)] if option is None else [key, str(seconds), option.value]
        )
        return cast(bool, await self._execute_command(RequestType.Expire, args))

    async def expireat(
        self,
        key: TEncodable,
        unix_seconds: int,
        option: Optional[ExpireOptions] = None,
    ) -> bool:
        """
        Sets a timeout on `key` using an absolute Unix timestamp (seconds since January 1, 1970) instead of specifying the
        number of seconds.
        A timestamp in the past will delete the key immediately. After the timeout has expired, the key will automatically be
        deleted.
        If `key` already has an existing expire set, the time to live is updated to the new value.
        The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
        See https://valkey.io/commands/expireat/ for more details.

        Args:
            key (TEncodable): The key to set a timeout on.
            unix_seconds (int): The timeout in an absolute Unix timestamp.
            option (Optional[ExpireOptions]): The expire option.

        Returns:
            bool: 'True' if the timeout was set, 'False' if the timeout was not set (e.g., the key doesn't exist or the operation is
                skipped due to the provided arguments).

        Examples:
            >>> await client.expireAt("my_key", 1672531200, ExpireOptions.HasNoExpiry)
                True
        """
        args = (
            [key, str(unix_seconds)]
            if option is None
            else [key, str(unix_seconds), option.value]
        )
        return cast(bool, await self._execute_command(RequestType.ExpireAt, args))

    async def pexpire(
        self,
        key: TEncodable,
        milliseconds: int,
        option: Optional[ExpireOptions] = None,
    ) -> bool:
        """
        Sets a timeout on `key` in milliseconds. After the timeout has expired, the key will automatically be deleted.
        If `key` already has an existing expire set, the time to live is updated to the new value.
        If `milliseconds` is a non-positive number, the key will be deleted rather than expired.
        The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
        See https://valkey.io/commands/pexpire/ for more details.

        Args:
            key (TEncodable): The key to set a timeout on.
            milliseconds (int): The timeout in milliseconds.
            option (Optional[ExpireOptions]): The expire option.

        Returns:
            bool: 'True' if the timeout was set, 'False' if the timeout was not set (e.g., the key doesn't exist or the operation is
                skipped due to the provided arguments).

        Examples:
            >>> await client.pexpire("my_key", 60000, ExpireOptions.HasNoExpiry)
                True  # Indicates that a timeout of 60,000 milliseconds has been set for "my_key."
        """
        args = (
            [key, str(milliseconds)]
            if option is None
            else [key, str(milliseconds), option.value]
        )
        return cast(bool, await self._execute_command(RequestType.PExpire, args))

    async def pexpireat(
        self,
        key: TEncodable,
        unix_milliseconds: int,
        option: Optional[ExpireOptions] = None,
    ) -> bool:
        """
        Sets a timeout on `key` using an absolute Unix timestamp in milliseconds (milliseconds since January 1, 1970) instead
        of specifying the number of milliseconds.
        A timestamp in the past will delete the key immediately. After the timeout has expired, the key will automatically be
        deleted.
        If `key` already has an existing expire set, the time to live is updated to the new value.
        The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
        See https://valkey.io/commands/pexpireat/ for more details.

        Args:
            key (TEncodable): The key to set a timeout on.
            unix_milliseconds (int): The timeout in an absolute Unix timestamp in milliseconds.
            option (Optional[ExpireOptions]): The expire option.

        Returns:
            bool: 'True' if the timeout was set, 'False' if the timeout was not set (e.g., the key doesn't exist or the operation is
                skipped due to the provided arguments).

        Examples:
            >>> await client.pexpireAt("my_key", 1672531200000, ExpireOptions.HasNoExpiry)
                True
        """
        args = (
            [key, str(unix_milliseconds)]
            if option is None
            else [key, str(unix_milliseconds), option.value]
        )
        return cast(bool, await self._execute_command(RequestType.PExpireAt, args))

    async def expiretime(self, key: TEncodable) -> int:
        """
        Returns the absolute Unix timestamp (since January 1, 1970) at which
        the given `key` will expire, in seconds.
        To get the expiration with millisecond precision, use `pexpiretime`.

        See https://valkey.io/commands/expiretime/ for details.

        Args:
            key (TEncodable): The `key` to determine the expiration value of.

        Returns:
            int: The expiration Unix timestamp in seconds, -2 if `key` does not exist or -1 if `key` exists but has no associated expire.

        Examples:
            >>> await client.expiretime("my_key")
                -2 # 'my_key' doesn't exist.
            >>> await client.set("my_key", "value")
            >>> await client.expiretime("my_key")
                -1 # 'my_key' has no associate expiration.
            >>> await client.expire("my_key", 60)
            >>> await client.expiretime("my_key")
                1718614954

        Since: Valkey version 7.0.0.
        """
        return cast(int, await self._execute_command(RequestType.ExpireTime, [key]))

    async def pexpiretime(self, key: TEncodable) -> int:
        """
        Returns the absolute Unix timestamp (since January 1, 1970) at which
        the given `key` will expire, in milliseconds.

        See https://valkey.io/commands/pexpiretime/ for details.

        Args:
            key (TEncodable): The `key` to determine the expiration value of.

        Returns:
            int: The expiration Unix timestamp in milliseconds, -2 if `key` does not exist, or -1 if `key` exists but has no associated expiration.

        Examples:
            >>> await client.pexpiretime("my_key")
                -2 # 'my_key' doesn't exist.
            >>> await client.set("my_key", "value")
            >>> await client.pexpiretime("my_key")
                -1 # 'my_key' has no associate expiration.
            >>> await client.expire("my_key", 60)
            >>> await client.pexpiretime("my_key")
                1718615446670

        Since: Valkey version 7.0.0.
        """
        return cast(int, await self._execute_command(RequestType.PExpireTime, [key]))

    async def ttl(self, key: TEncodable) -> int:
        """
        Returns the remaining time to live of `key` that has a timeout.
        See https://valkey.io/commands/ttl/ for more details.

        Args:
            key (TEncodable): The key to return its timeout.

        Returns:
            int: TTL in seconds, -2 if `key` does not exist or -1 if `key` exists but has no associated expire.

        Examples:
            >>> await client.ttl("my_key")
                3600  # Indicates that "my_key" has a remaining time to live of 3600 seconds.
            >>> await client.ttl("nonexistent_key")
                -2  # Returns -2 for a non-existing key.
            >>> await client.ttl("key")
                -1  # Indicates that "key: has no has no associated expire.
        """
        return cast(int, await self._execute_command(RequestType.TTL, [key]))

    async def pttl(
        self,
        key: TEncodable,
    ) -> int:
        """
        Returns the remaining time to live of `key` that has a timeout, in milliseconds.
        See https://valkey.io/commands/pttl for more details.

        Args:
            key (TEncodable): The key to return its timeout.

        Returns:
            int: TTL in milliseconds. -2 if `key` does not exist, -1 if `key` exists but has no associated expire.

        Examples:
            >>> await client.pttl("my_key")
                5000  # Indicates that the key "my_key" has a remaining time to live of 5000 milliseconds.
            >>> await client.pttl("non_existing_key")
                -2  # Indicates that the key "non_existing_key" does not exist.
        """
        return cast(
            int,
            await self._execute_command(RequestType.PTTL, [key]),
        )

    async def persist(
        self,
        key: TEncodable,
    ) -> bool:
        """
        Remove the existing timeout on `key`, turning the key from volatile (a key with an expire set) to
        persistent (a key that will never expire as no timeout is associated).

        See https://valkey.io/commands/persist/ for more details.

        Args:
            key (TEncodable): The key to remove the existing timeout on.

        Returns:
            bool: False if `key` does not exist or does not have an associated timeout, True if the timeout has been removed.

        Examples:
            >>> await client.persist("my_key")
                True  # Indicates that the timeout associated with the key "my_key" was successfully removed.
        """
        return cast(
            bool,
            await self._execute_command(RequestType.Persist, [key]),
        )

    async def type(self, key: TEncodable) -> bytes:
        """
        Returns the bytes string representation of the type of the value stored at `key`.

        See https://valkey.io/commands/type/ for more details.

        Args:
            key (TEncodable): The key to check its data type.

        Returns:
            bytes: If the key exists, the type of the stored value is returned.
            Otherwise, a b"none" bytes string is returned.

        Examples:
            >>> await client.set("key", "value")
            >>> await client.type("key")
                b'string'
            >>> await client.lpush("key", ["value"])
            >>> await client.type("key")
                b'list'
        """
        return cast(bytes, await self._execute_command(RequestType.Type, [key]))

    async def xadd(
        self,
        key: TEncodable,
        values: List[Tuple[TEncodable, TEncodable]],
        options: Optional[StreamAddOptions] = None,
    ) -> Optional[bytes]:
        """
        Adds an entry to the specified stream stored at `key`. If the `key` doesn't exist, the stream is created.

        See https://valkey.io/commands/xadd for more details.

        Args:
            key (TEncodable): The key of the stream.
            values (List[Tuple[TEncodable, TEncodable]]): Field-value pairs to be added to the entry.
            options (Optional[StreamAddOptions]): Additional options for adding entries to the stream. Default to None. See `StreamAddOptions`.

        Returns:
            bytes: The id of the added entry, or None if `options.make_stream` is set to False and no stream with the matching `key` exists.

        Example:
            >>> await client.xadd("mystream", [("field", "value"), ("field2", "value2")])
                b"1615957011958-0"  # Example stream entry ID.
            >>> await client.xadd("non_existing_stream", [(field, "foo1"), (field2, "bar1")], StreamAddOptions(id="0-1", make_stream=False))
                None  # The key doesn't exist, therefore, None is returned.
            >>> await client.xadd("non_existing_stream", [(field, "foo1"), (field2, "bar1")], StreamAddOptions(id="0-1"))
                b"0-1"  # Returns the stream id.
        """
        args: List[TEncodable] = [key]
        if options:
            args.extend(options.to_args())
        else:
            args.append("*")
        args.extend([field for pair in values for field in pair])

        return cast(
            Optional[bytes], await self._execute_command(RequestType.XAdd, args)
        )

    async def xdel(self, key: TEncodable, ids: List[TEncodable]) -> int:
        """
        Removes the specified entries by id from a stream, and returns the number of entries deleted.

        See https://valkey.io/commands/xdel for more details.

        Args:
            key (TEncodable): The key of the stream.
            ids (List[TEncodable]): An array of entry ids.

        Returns:
            int: The number of entries removed from the stream. This number may be less than the number of entries in
                `ids`, if the specified `ids` don't exist in the stream.

        Examples:
            >>> await client.xdel("key", ["1538561698944-0", "1538561698944-1"])
                2  # Stream marked 2 entries as deleted.
        """
        args: List[TEncodable] = [key]
        args.extend(ids)
        return cast(
            int,
            await self._execute_command(RequestType.XDel, [key] + ids),
        )

    async def xtrim(
        self,
        key: TEncodable,
        options: StreamTrimOptions,
    ) -> int:
        """
        Trims the stream stored at `key` by evicting older entries.

        See https://valkey.io/commands/xtrim for more details.

        Args:
            key (TEncodable): The key of the stream.
            options (StreamTrimOptions): Options detailing how to trim the stream. See `StreamTrimOptions`.

        Returns:
            int: TThe number of entries deleted from the stream. If `key` doesn't exist, 0 is returned.

        Example:
            >>> await client.xadd("mystream", [("field", "value"), ("field2", "value2")], StreamAddOptions(id="0-1"))
            >>> await client.xtrim("mystream", TrimByMinId(exact=True, threshold="0-2")))
                1 # One entry was deleted from the stream.
        """
        args = [key]
        if options:
            args.extend(options.to_args())

        return cast(int, await self._execute_command(RequestType.XTrim, args))

    async def xlen(self, key: TEncodable) -> int:
        """
        Returns the number of entries in the stream stored at `key`.

        See https://valkey.io/commands/xlen for more details.

        Args:
            key (TEncodable): The key of the stream.

        Returns:
            int: The number of entries in the stream. If `key` does not exist, returns 0.

        Examples:
            >>> await client.xadd("mystream", [("field", "value")])
            >>> await client.xadd("mystream", [("field2", "value2")])
            >>> await client.xlen("mystream")
                2  # There are 2 entries in "mystream".
        """
        return cast(
            int,
            await self._execute_command(RequestType.XLen, [key]),
        )

    async def xrange(
        self,
        key: TEncodable,
        start: StreamRangeBound,
        end: StreamRangeBound,
        count: Optional[int] = None,
    ) -> Optional[Mapping[bytes, List[List[bytes]]]]:
        """
        Returns stream entries matching a given range of IDs.

        See https://valkey.io/commands/xrange for more details.

        Args:
            key (TEncodable): The key of the stream.
            start (StreamRangeBound): The starting stream ID bound for the range.
                - Use `IdBound` to specify a stream ID.
                - Use `ExclusiveIdBound` to specify an exclusive bounded stream ID.
                - Use `MinId` to start with the minimum available ID.
            end (StreamRangeBound): The ending stream ID bound for the range.
                - Use `IdBound` to specify a stream ID.
                - Use `ExclusiveIdBound` to specify an exclusive bounded stream ID.
                - Use `MaxId` to end with the maximum available ID.
            count (Optional[int]): An optional argument specifying the maximum count of stream entries to return.
                If `count` is not provided, all stream entries in the range will be returned.

        Returns:
            Optional[Mapping[bytes, List[List[bytes]]]]: A mapping of stream IDs to stream entry data, where entry data is a
                list of pairings with format `[[field, entry], [field, entry], ...]`. Returns None if the range
                arguments are not applicable.

        Examples:
            >>> await client.xadd("mystream", [("field1", "value1")], StreamAddOptions(id="0-1"))
            >>> await client.xadd("mystream", [("field2", "value2"), ("field2", "value3")], StreamAddOptions(id="0-2"))
            >>> await client.xrange("mystream", MinId(), MaxId())
                {
                    b"0-1": [[b"field1", b"value1"]],
                    b"0-2": [[b"field2", b"value2"], [b"field2", b"value3"]],
                }  # Indicates the stream IDs and their associated field-value pairs for all stream entries in "mystream".
        """
        args: List[TEncodable] = [key, start.to_arg(), end.to_arg()]
        if count is not None:
            args.extend(["COUNT", str(count)])

        return cast(
            Optional[Mapping[bytes, List[List[bytes]]]],
            await self._execute_command(RequestType.XRange, args),
        )

    async def xrevrange(
        self,
        key: TEncodable,
        end: StreamRangeBound,
        start: StreamRangeBound,
        count: Optional[int] = None,
    ) -> Optional[Mapping[bytes, List[List[bytes]]]]:
        """
        Returns stream entries matching a given range of IDs in reverse order. Equivalent to `XRANGE` but returns the
        entries in reverse order.

        See https://valkey.io/commands/xrevrange for more details.

        Args:
            key (TEncodable): The key of the stream.
            end (StreamRangeBound): The ending stream ID bound for the range.
                - Use `IdBound` to specify a stream ID.
                - Use `ExclusiveIdBound` to specify an exclusive bounded stream ID.
                - Use `MaxId` to end with the maximum available ID.
            start (StreamRangeBound): The starting stream ID bound for the range.
                - Use `IdBound` to specify a stream ID.
                - Use `ExclusiveIdBound` to specify an exclusive bounded stream ID.
                - Use `MinId` to start with the minimum available ID.
            count (Optional[int]): An optional argument specifying the maximum count of stream entries to return.
                If `count` is not provided, all stream entries in the range will be returned.

        Returns:
            Optional[Mapping[bytes, List[List[bytes]]]]: A mapping of stream IDs to stream entry data, where entry data is a
                list of pairings with format `[[field, entry], [field, entry], ...]`. Returns None if the range
                arguments are not applicable.

        Examples:
            >>> await client.xadd("mystream", [("field1", "value1")], StreamAddOptions(id="0-1"))
            >>> await client.xadd("mystream", [("field2", "value2"), ("field2", "value3")], StreamAddOptions(id="0-2"))
            >>> await client.xrevrange("mystream", MaxId(), MinId())
                {
                    "0-2": [["field2", "value2"], ["field2", "value3"]],
                    "0-1": [["field1", "value1"]],
                }  # Indicates the stream IDs and their associated field-value pairs for all stream entries in "mystream".
        """
        args: List[TEncodable] = [key, end.to_arg(), start.to_arg()]
        if count is not None:
            args.extend(["COUNT", str(count)])

        return cast(
            Optional[Mapping[bytes, List[List[bytes]]]],
            await self._execute_command(RequestType.XRevRange, args),
        )

    async def xread(
        self,
        keys_and_ids: Mapping[TEncodable, TEncodable],
        options: Optional[StreamReadOptions] = None,
    ) -> Optional[Mapping[bytes, Mapping[bytes, List[List[bytes]]]]]:
        """
        Reads entries from the given streams.

        See https://valkey.io/commands/xread for more details.

        Note:
            When in cluster mode, all keys in `keys_and_ids` must map to the same hash slot.

        Args:
            keys_and_ids (Mapping[TEncodable, TEncodable]): A mapping of keys and entry IDs to read from. The mapping is composed of a
                stream's key and the ID of the entry after which the stream will be read.
            options (Optional[StreamReadOptions]): Options detailing how to read the stream.

        Returns:
            Optional[Mapping[bytes, Mapping[bytes, List[List[bytes]]]]]: A mapping of stream keys, to a mapping of stream IDs,
                to a list of pairings with format `[[field, entry], [field, entry], ...]`.
                None will be returned under the following conditions:
                - All key-ID pairs in `keys_and_ids` have either a non-existing key or a non-existing ID, or there are no entries after the given ID.
                - The `BLOCK` option is specified and the timeout is hit.

        Examples:
            >>> await client.xadd("mystream", [("field1", "value1")], StreamAddOptions(id="0-1"))
            >>> await client.xadd("mystream", [("field2", "value2"), ("field2", "value3")], StreamAddOptions(id="0-2"))
            >>> await client.xread({"mystream": "0-0"}, StreamReadOptions(block_ms=1000))
                {
                    b"mystream": {
                        b"0-1": [[b"field1", b"value1"]],
                        b"0-2": [[b"field2", b"value2"], [b"field2", b"value3"]],
                    }
                }
                # Indicates the stream entries for "my_stream" with IDs greater than "0-0". The operation blocks up to
                # 1000ms if there is no stream data.
        """
        args: List[TEncodable] = [] if options is None else options.to_args()
        args.append("STREAMS")
        args.extend([key for key in keys_and_ids.keys()])
        args.extend([value for value in keys_and_ids.values()])

        return cast(
            Optional[Mapping[bytes, Mapping[bytes, List[List[bytes]]]]],
            await self._execute_command(RequestType.XRead, args),
        )

    async def xgroup_create(
        self,
        key: TEncodable,
        group_name: TEncodable,
        group_id: TEncodable,
        options: Optional[StreamGroupOptions] = None,
    ) -> TOK:
        """
        Creates a new consumer group uniquely identified by `group_name` for the stream stored at `key`.

        See https://valkey.io/commands/xgroup-create for more details.

        Args:
            key (TEncodable): The key of the stream.
            group_name (TEncodable): The newly created consumer group name.
            group_id (TEncodable): The stream entry ID that specifies the last delivered entry in the stream from the new
                group’s perspective. The special ID "$" can be used to specify the last entry in the stream.
            options (Optional[StreamGroupOptions]): Options for creating the stream group.

        Returns:
            TOK: A simple "OK" response.

        Examples:
            >>> await client.xgroup_create("mystream", "mygroup", "$", StreamGroupOptions(make_stream=True))
                OK
                # Created the consumer group "mygroup" for the stream "mystream", which will track entries created after
                # the most recent entry. The stream was created with length 0 if it did not already exist.
        """
        args: List[TEncodable] = [key, group_name, group_id]
        if options is not None:
            args.extend(options.to_args())

        return cast(
            TOK,
            await self._execute_command(RequestType.XGroupCreate, args),
        )

    async def xgroup_destroy(self, key: TEncodable, group_name: TEncodable) -> bool:
        """
        Destroys the consumer group `group_name` for the stream stored at `key`.

        See https://valkey.io/commands/xgroup-destroy for more details.

        Args:
            key (TEncodable): The key of the stream.
            group_name (TEncodable): The consumer group name to delete.

        Returns:
            bool: True if the consumer group was destroyed. Otherwise, returns False.

        Examples:
            >>> await client.xgroup_destroy("mystream", "mygroup")
                True  # The consumer group "mygroup" for stream "mystream" was destroyed.
        """
        return cast(
            bool,
            await self._execute_command(RequestType.XGroupDestroy, [key, group_name]),
        )

    async def xgroup_create_consumer(
        self,
        key: TEncodable,
        group_name: TEncodable,
        consumer_name: TEncodable,
    ) -> bool:
        """
        Creates a consumer named `consumer_name` in the consumer group `group_name` for the stream stored at `key`.

        See https://valkey.io/commands/xgroup-createconsumer for more details.

        Args:
            key (TEncodable): The key of the stream.
            group_name (TEncodable): The consumer group name.
            consumer_name (TEncodable): The newly created consumer.

        Returns:
            bool: True if the consumer is created. Otherwise, returns False.

        Examples:
            >>> await client.xgroup_create_consumer("mystream", "mygroup", "myconsumer")
                True  # The consumer "myconsumer" was created in consumer group "mygroup" for the stream "mystream".
        """
        return cast(
            bool,
            await self._execute_command(
                RequestType.XGroupCreateConsumer, [key, group_name, consumer_name]
            ),
        )

    async def xgroup_del_consumer(
        self,
        key: TEncodable,
        group_name: TEncodable,
        consumer_name: TEncodable,
    ) -> int:
        """
        Deletes a consumer named `consumer_name` in the consumer group `group_name` for the stream stored at `key`.

        See https://valkey.io/commands/xgroup-delconsumer for more details.

        Args:
            key (TEncodable): The key of the stream.
            group_name (TEncodable): The consumer group name.
            consumer_name (TEncodable): The consumer to delete.

        Returns:
            int: The number of pending messages the `consumer` had before it was deleted.

        Examples:
            >>> await client.xgroup_del_consumer("mystream", "mygroup", "myconsumer")
                5  # Consumer "myconsumer" was deleted, and had 5 pending messages unclaimed.
        """
        return cast(
            int,
            await self._execute_command(
                RequestType.XGroupDelConsumer, [key, group_name, consumer_name]
            ),
        )

    async def xgroup_set_id(
        self,
        key: TEncodable,
        group_name: TEncodable,
        stream_id: TEncodable,
        entries_read: Optional[int] = None,
    ) -> TOK:
        """
        Set the last delivered ID for a consumer group.

        See https://valkey.io/commands/xgroup-setid for more details.

        Args:
            key (TEncodable): The key of the stream.
            group_name (TEncodable): The consumer group name.
            stream_id (TEncodable): The stream entry ID that should be set as the last delivered ID for the consumer group.
            entries_read: (Optional[int]): A value representing the number of stream entries already read by the
                group. This option can only be specified if you are using Valkey version 7.0.0 or above.

        Returns:
            TOK: A simple "OK" response.

        Examples:
            >>> await client.xgroup_set_id("mystream", "mygroup", "0")
                OK  # The last delivered ID for consumer group "mygroup" was set to 0.
        """
        args: List[TEncodable] = [key, group_name, stream_id]
        if entries_read is not None:
            args.extend(["ENTRIESREAD", str(entries_read)])

        return cast(
            TOK,
            await self._execute_command(RequestType.XGroupSetId, args),
        )

    async def xreadgroup(
        self,
        keys_and_ids: Mapping[TEncodable, TEncodable],
        group_name: TEncodable,
        consumer_name: TEncodable,
        options: Optional[StreamReadGroupOptions] = None,
    ) -> Optional[Mapping[bytes, Mapping[bytes, Optional[List[List[bytes]]]]]]:
        """
        Reads entries from the given streams owned by a consumer group.

        See https://valkey.io/commands/xreadgroup for more details.

        Note:
            When in cluster mode, all keys in `keys_and_ids` must map to the same hash slot.

        Args:
            keys_and_ids (Mapping[TEncodable, TEncodable]): A mapping of stream keys to stream entry IDs to read from. The special ">"
                ID returns messages that were never delivered to any other consumer. Any other valid ID will return
                entries pending for the consumer with IDs greater than the one provided.
            group_name (TEncodable): The consumer group name.
            consumer_name (TEncodable): The consumer name. The consumer will be auto-created if it does not already exist.
            options (Optional[StreamReadGroupOptions]): Options detailing how to read the stream.

        Returns:
            Optional[Mapping[bytes, Mapping[bytes, Optional[List[List[bytes]]]]]]: A mapping of stream keys, to a mapping of
                stream IDs, to a list of pairings with format `[[field, entry], [field, entry], ...]`.
                Returns None if the BLOCK option is given and a timeout occurs, or if there is no stream that can be served.

        Examples:
            >>> await client.xadd("mystream", [("field1", "value1")], StreamAddOptions(id="1-0"))
            >>> await client.xgroup_create("mystream", "mygroup", "0-0")
            >>> await client.xreadgroup({"mystream": ">"}, "mygroup", "myconsumer", StreamReadGroupOptions(count=1))
                {
                    b"mystream": {
                        b"1-0": [[b"field1", b"value1"]],
                    }
                }  # Read one stream entry from "mystream" using "myconsumer" in the consumer group "mygroup".
        """
        args: List[TEncodable] = ["GROUP", group_name, consumer_name]
        if options is not None:
            args.extend(options.to_args())

        args.append("STREAMS")
        args.extend([key for key in keys_and_ids.keys()])
        args.extend([value for value in keys_and_ids.values()])

        return cast(
            Optional[Mapping[bytes, Mapping[bytes, Optional[List[List[bytes]]]]]],
            await self._execute_command(RequestType.XReadGroup, args),
        )

    async def xack(
        self,
        key: TEncodable,
        group_name: TEncodable,
        ids: List[TEncodable],
    ) -> int:
        """
        Removes one or multiple messages from the Pending Entries List (PEL) of a stream consumer group.
        This command should be called on pending messages so that such messages do not get processed again by the
        consumer group.

        See https://valkey.io/commands/xack for more details.

        Args:
            key (TEncodable): The key of the stream.
            group_name (TEncodable): The consumer group name.
            ids (List[TEncodable]): The stream entry IDs to acknowledge and consume for the given consumer group.

        Returns:
            int: The number of messages that were successfully acknowledged.

        Examples:
            >>> await client.xadd("mystream", [("field1", "value1")], StreamAddOptions(id="1-0"))
            >>> await client.xgroup_create("mystream", "mygroup", "0-0")
            >>> await client.xreadgroup({"mystream": ">"}, "mygroup", "myconsumer")
                {
                    "mystream": {
                        "1-0": [["field1", "value1"]],
                    }
                }  # Read one stream entry, the entry is now in the Pending Entries List for "mygroup".
            >>> await client.xack("mystream", "mygroup", ["1-0"])
                1  # 1 pending message was acknowledged and removed from the Pending Entries List for "mygroup".
        """
        args: List[TEncodable] = [key, group_name]
        args.extend(ids)
        return cast(
            int,
            await self._execute_command(RequestType.XAck, [key, group_name] + ids),
        )

    async def xpending(
        self,
        key: TEncodable,
        group_name: TEncodable,
    ) -> List[Union[int, bytes, List[List[bytes]], None]]:
        """
        Returns stream message summary information for pending messages for the given consumer group.

        See https://valkey.io/commands/xpending for more details.

        Args:
            key (TEncodable): The key of the stream.
            group_name (TEncodable): The consumer group name.

        Returns:
            List[Union[int, bytes, List[List[bytes]], None]]: A list that includes the summary of pending messages, with the
                format `[num_group_messages, start_id, end_id, [[consumer_name, num_consumer_messages]]]`, where:
                - `num_group_messages`: The total number of pending messages for this consumer group.
                - `start_id`: The smallest ID among the pending messages.
                - `end_id`: The greatest ID among the pending messages.
                - `[[consumer_name, num_consumer_messages]]`: A 2D list of every consumer in the consumer group with at
                least one pending message, and the number of pending messages it has.

                If there are no pending messages for the given consumer group, `[0, None, None, None]` will be returned.

        Examples:
            >>> await client.xpending("my_stream", "my_group")
                [4, "1-0", "1-3", [["my_consumer1", "3"], ["my_consumer2", "1"]]
        """
        return cast(
            List[Union[int, bytes, List[List[bytes]], None]],
            await self._execute_command(RequestType.XPending, [key, group_name]),
        )

    async def xpending_range(
        self,
        key: TEncodable,
        group_name: TEncodable,
        start: StreamRangeBound,
        end: StreamRangeBound,
        count: int,
        options: Optional[StreamPendingOptions] = None,
    ) -> List[List[Union[bytes, int]]]:
        """
        Returns an extended form of stream message information for pending messages matching a given range of IDs.

        See https://valkey.io/commands/xpending for more details.

        Args:
            key (TEncodable): The key of the stream.
            group_name (TEncodable): The consumer group name.
            start (StreamRangeBound): The starting stream ID bound for the range.
                - Use `IdBound` to specify a stream ID.
                - Use `ExclusiveIdBound` to specify an exclusive bounded stream ID.
                - Use `MinId` to start with the minimum available ID.
            end (StreamRangeBound): The ending stream ID bound for the range.
                - Use `IdBound` to specify a stream ID.
                - Use `ExclusiveIdBound` to specify an exclusive bounded stream ID.
                - Use `MaxId` to end with the maximum available ID.
            count (int): Limits the number of messages returned.
            options (Optional[StreamPendingOptions]): The stream pending options.

        Returns:
            List[List[Union[bytes, int]]]: A list of lists, where each inner list is a length 4 list containing extended
                message information with the format `[[id, consumer_name, time_elapsed, num_delivered]]`, where:
                - `id`: The ID of the message.
                - `consumer_name`: The name of the consumer that fetched the message and has still to acknowledge it. We
                call it the current owner of the message.
                - `time_elapsed`: The number of milliseconds that elapsed since the last time this message was delivered
                to this consumer.
                - `num_delivered`: The number of times this message was delivered.

        Examples:
            >>> await client.xpending_range("my_stream", "my_group", MinId(), MaxId(), 10, StreamPendingOptions(consumer_name="my_consumer"))
                [[b"1-0", b"my_consumer", 1234, 1], [b"1-1", b"my_consumer", 1123, 1]]
                # Extended stream entry information for the pending entries associated with "my_consumer".
        """
        args = _create_xpending_range_args(key, group_name, start, end, count, options)
        return cast(
            List[List[Union[bytes, int]]],
            await self._execute_command(RequestType.XPending, args),
        )

    async def xclaim(
        self,
        key: TEncodable,
        group: TEncodable,
        consumer: TEncodable,
        min_idle_time_ms: int,
        ids: List[TEncodable],
        options: Optional[StreamClaimOptions] = None,
    ) -> Mapping[bytes, List[List[bytes]]]:
        """
        Changes the ownership of a pending message.

        See https://valkey.io/commands/xclaim for more details.

        Args:
            key (TEncodable): The key of the stream.
            group (TEncodable): The consumer group name.
            consumer (TEncodable): The group consumer.
            min_idle_time_ms (int): The minimum idle time for the message to be claimed.
            ids (List[TEncodable]): A array of entry ids.
            options (Optional[StreamClaimOptions]): Stream claim options.

        Returns:
            Mapping[bytes, List[List[bytes]]]: A Mapping of message entries with the format
                {"entryId": [["entry", "data"], ...], ...} that are claimed by the consumer.

        Examples:
            # read messages from streamId for consumer1
            >>> await client.xreadgroup({"mystream": ">"}, "mygroup", "consumer1")
                {
                    b"mystream": {
                        b"1-0": [[b"field1", b"value1"]],
                    }
                }
                # "1-0" is now read, and we can assign the pending messages to consumer2
            >>> await client.xclaim("mystream", "mygroup", "consumer2", 0, ["1-0"])
                {b"1-0": [[b"field1", b"value1"]]}
        """

        args = [key, group, consumer, str(min_idle_time_ms), *ids]

        if options:
            args.extend(options.to_args())

        return cast(
            Mapping[bytes, List[List[bytes]]],
            await self._execute_command(RequestType.XClaim, args),
        )

    async def xclaim_just_id(
        self,
        key: TEncodable,
        group: TEncodable,
        consumer: TEncodable,
        min_idle_time_ms: int,
        ids: List[TEncodable],
        options: Optional[StreamClaimOptions] = None,
    ) -> List[bytes]:
        """
        Changes the ownership of a pending message. This function returns a List with
        only the message/entry IDs, and is equivalent to using JUSTID in the Valkey API.

        See https://valkey.io/commands/xclaim for more details.

        Args:
            key (TEncodable): The key of the stream.
            group (TEncodable): The consumer group name.
            consumer (TEncodable): The group consumer.
            min_idle_time_ms (int): The minimum idle time for the message to be claimed.
            ids (List[TEncodable]): A array of entry ids.
            options (Optional[StreamClaimOptions]): Stream claim options.

        Returns:
            List[bytes]: A List of message ids claimed by the consumer.

        Examples:
            # read messages from streamId for consumer1
            >>> await client.xreadgroup({"mystream": ">"}, "mygroup", "consumer1")
                {
                    b"mystream": {
                        b"1-0": [[b"field1", b"value1"]],
                    }
                }
                # "1-0" is now read, and we can assign the pending messages to consumer2
            >>> await client.xclaim_just_id("mystream", "mygroup", "consumer2", 0, ["1-0"])
                [b"1-0"]
        """

        args = [
            key,
            group,
            consumer,
            str(min_idle_time_ms),
            *ids,
            StreamClaimOptions.JUST_ID_VALKEY_API,
        ]

        if options:
            args.extend(options.to_args())

        return cast(
            List[bytes],
            await self._execute_command(RequestType.XClaim, args),
        )

    async def xautoclaim(
        self,
        key: TEncodable,
        group_name: TEncodable,
        consumer_name: TEncodable,
        min_idle_time_ms: int,
        start: TEncodable,
        count: Optional[int] = None,
    ) -> List[Union[bytes, Mapping[bytes, List[List[bytes]]], List[bytes]]]:
        """
        Transfers ownership of pending stream entries that match the specified criteria.

        See https://valkey.io/commands/xautoclaim for more details.

        Args:
            key (TEncodable): The key of the stream.
            group_name (TEncodable): The consumer group name.
            consumer_name (TEncodable): The consumer name.
            min_idle_time_ms (int): Filters the claimed entries to those that have been idle for more than the specified
                value.
            start (TEncodable): Filters the claimed entries to those that have an ID equal or greater than the specified value.
            count (Optional[int]): Limits the number of claimed entries to the specified value.

        Returns:
            List[Union[bytes, Mapping[bytes, List[List[bytes]]], List[bytes]]]: A list containing the following elements:
                - A stream ID to be used as the start argument for the next call to `XAUTOCLAIM`. This ID is equivalent
                to the next ID in the stream after the entries that were scanned, or "0-0" if the entire stream was
                scanned.
                - A mapping of the claimed entries, with the keys being the claimed entry IDs and the values being a
                2D list of the field-value pairs in the format `[[field1, value1], [field2, value2], ...]`.
                - If you are using Valkey 7.0.0 or above, the response list will also include a list containing the
                message IDs that were in the Pending Entries List but no longer exist in the stream. These IDs are
                deleted from the Pending Entries List.

        Examples:
            # Valkey version < 7.0.0:
            >>> await client.xautoclaim("my_stream", "my_group", "my_consumer", 3_600_000, "0-0")
                [
                    b"0-0",
                    {
                        b"1-1": [
                            [b"field1", b"value1"],
                            [b"field2", b"value2"],
                        ]
                    }
                ]
                # Stream entry "1-1" was idle for over an hour and was thus claimed by "my_consumer". The entire stream
                # was scanned.

            # Valkey version 7.0.0 and above:
            >>> await client.xautoclaim("my_stream", "my_group", "my_consumer", 3_600_000, "0-0")
                [
                    b"0-0",
                    {
                        b"1-1": [
                            [b"field1", b"value1"],
                            [b"field2", b"value2"],
                        ]
                    },
                    [b"1-2"]
                ]
                # Stream entry "1-1" was idle for over an hour and was thus claimed by "my_consumer". The entire stream
                # was scanned. Additionally, entry "1-2" was removed from the Pending Entries List because it no longer
                # exists in the stream.

        Since: Valkey version 6.2.0.
        """
        args: List[TEncodable] = [
            key,
            group_name,
            consumer_name,
            str(min_idle_time_ms),
            start,
        ]
        if count is not None:
            args.extend(["COUNT", str(count)])

        return cast(
            List[Union[bytes, Mapping[bytes, List[List[bytes]]], List[bytes]]],
            await self._execute_command(RequestType.XAutoClaim, args),
        )

    async def xautoclaim_just_id(
        self,
        key: TEncodable,
        group_name: TEncodable,
        consumer_name: TEncodable,
        min_idle_time_ms: int,
        start: TEncodable,
        count: Optional[int] = None,
    ) -> List[Union[bytes, List[bytes]]]:
        """
        Transfers ownership of pending stream entries that match the specified criteria. This command uses the JUSTID
        argument to further specify that the return value should contain a list of claimed IDs without their
        field-value info.

        See https://valkey.io/commands/xautoclaim for more details.

        Args:
            key (TEncodable): The key of the stream.
            group_name (TEncodable): The consumer group name.
            consumer_name (TEncodable): The consumer name.
            min_idle_time_ms (int): Filters the claimed entries to those that have been idle for more than the specified
                value.
            start (TEncodable): Filters the claimed entries to those that have an ID equal or greater than the specified value.
            count (Optional[int]): Limits the number of claimed entries to the specified value.

        Returns:
            List[Union[bytes, List[bytes]]]: A list containing the following elements:
                - A stream ID to be used as the start argument for the next call to `XAUTOCLAIM`. This ID is equivalent
                to the next ID in the stream after the entries that were scanned, or "0-0" if the entire stream was
                scanned.
                - A list of the IDs for the claimed entries.
                - If you are using Valkey 7.0.0 or above, the response list will also include a list containing the
                message IDs that were in the Pending Entries List but no longer exist in the stream. These IDs are
                deleted from the Pending Entries List.

        Examples:
            # Valkey version < 7.0.0:
            >>> await client.xautoclaim_just_id("my_stream", "my_group", "my_consumer", 3_600_000, "0-0")
                [b"0-0", [b"1-1"]]
                # Stream entry "1-1" was idle for over an hour and was thus claimed by "my_consumer". The entire stream
                # was scanned.

            # Valkey version 7.0.0 and above:
            >>> await client.xautoclaim_just_id("my_stream", "my_group", "my_consumer", 3_600_000, "0-0")
                [b"0-0", [b"1-1"], [b"1-2"]]
                # Stream entry "1-1" was idle for over an hour and was thus claimed by "my_consumer". The entire stream
                # was scanned. Additionally, entry "1-2" was removed from the Pending Entries List because it no longer
                # exists in the stream.

        Since: Valkey version 6.2.0.
        """
        args: List[TEncodable] = [
            key,
            group_name,
            consumer_name,
            str(min_idle_time_ms),
            start,
        ]
        if count is not None:
            args.extend(["COUNT", str(count)])

        args.append("JUSTID")

        return cast(
            List[Union[bytes, List[bytes]]],
            await self._execute_command(RequestType.XAutoClaim, args),
        )

    async def xinfo_groups(
        self,
        key: TEncodable,
    ) -> List[Mapping[bytes, Union[bytes, int, None]]]:
        """
        Returns the list of all consumer groups and their attributes for the stream stored at `key`.

        See https://valkey.io/commands/xinfo-groups for more details.

        Args:
            key (TEncodable): The key of the stream.

        Returns:
            List[Mapping[bytes, Union[bytes, int, None]]]: A list of mappings, where each mapping represents the
                attributes of a consumer group for the stream at `key`.

        Examples:
            >>> await client.xinfo_groups("my_stream")
                [
                    {
                        b"name": b"mygroup",
                        b"consumers": 2,
                        b"pending": 2,
                        b"last-delivered-id": b"1638126030001-0",
                        b"entries-read": 2,  # The "entries-read" field was added in Valkey version 7.0.0.
                        b"lag": 0,  # The "lag" field was added in Valkey version 7.0.0.
                    },
                    {
                        b"name": b"some-other-group",
                        b"consumers": 1,
                        b"pending": 0,
                        b"last-delivered-id": b"1638126028070-0",
                        b"entries-read": 1,
                        b"lag": 1,
                    }
                ]
                # The list of consumer groups and their attributes for stream "my_stream".
        """
        return cast(
            List[Mapping[bytes, Union[bytes, int, None]]],
            await self._execute_command(RequestType.XInfoGroups, [key]),
        )

    async def xinfo_consumers(
        self,
        key: TEncodable,
        group_name: TEncodable,
    ) -> List[Mapping[bytes, Union[bytes, int]]]:
        """
        Returns the list of all consumers and their attributes for the given consumer group of the stream stored at
        `key`.

        See https://valkey.io/commands/xinfo-consumers for more details.

        Args:
            key (TEncodable): The key of the stream.
            group_name (TEncodable): The consumer group name.

        Returns:
            List[Mapping[bytes, Union[bytes, int]]]: A list of mappings, where each mapping contains the attributes of a
                consumer for the given consumer group of the stream at `key`.

        Examples:
            >>> await client.xinfo_consumers("my_stream", "my_group")
                [
                    {
                        b"name": b"Alice",
                        b"pending": 1,
                        b"idle": 9104628,
                        b"inactive": 18104698,  # The "inactive" field was added in Valkey version 7.2.0.
                    },
                    {
                        b"name": b"Bob",
                        b"pending": 1,
                        b"idle": 83841983,
                        b"inactive": 993841998,
                    }
                ]
                # The list of consumers and their attributes for consumer group "my_group" of stream "my_stream".
        """
        return cast(
            List[Mapping[bytes, Union[bytes, int]]],
            await self._execute_command(RequestType.XInfoConsumers, [key, group_name]),
        )

    async def xinfo_stream(
        self,
        key: TEncodable,
    ) -> TXInfoStreamResponse:
        """
        Returns information about the stream stored at `key`. To get more detailed information, use `xinfo_stream_full`.

        See https://valkey.io/commands/xinfo-stream for more details.

        Args:
            key (TEncodable): The key of the stream.

        Returns:
            TXInfoStreamResponse: A mapping of stream information for the given `key`. See the example for a sample
                response.

        Examples:
            >>> await client.xinfo_stream("my_stream")
                {
                    b"length": 4,
                    b"radix-tree-keys": 1L,
                    b"radix-tree-nodes": 2L,
                    b"last-generated-id": b"1719877599564-0",
                    b"max-deleted-entry-id": b"0-0",  # This field was added in Valkey version 7.0.0.
                    b"entries-added": 4L,  # This field was added in Valkey version 7.0.0.
                    b"recorded-first-entry-id": b"1719710679916-0",  # This field was added in Valkey version 7.0.0.
                    b"groups": 1L,
                    b"first-entry": [
                        b"1719710679916-0",
                        [b"foo1", b"bar1", b"foo2", b"bar2"],
                    ],
                    b"last-entry": [
                        b"1719877599564-0",
                        [b"field1", b"value1"],
                    ],
                }
                # Stream information for "my_stream". Note that "first-entry" and "last-entry" could both be `None` if
                # the stream is empty.
        """
        return cast(
            TXInfoStreamResponse,
            await self._execute_command(RequestType.XInfoStream, [key]),
        )

    async def xinfo_stream_full(
        self,
        key: TEncodable,
        count: Optional[int] = None,
    ) -> TXInfoStreamFullResponse:
        """
        Returns verbose information about the stream stored at `key`.

        See https://valkey.io/commands/xinfo-stream for more details.

        Args:
            key (TEncodable): The key of the stream.
            count (Optional[int]): The number of stream and PEL entries that are returned. A value of `0` means that all
                entries will be returned. If not provided, defaults to `10`.

        Returns:
            TXInfoStreamFullResponse: A mapping of detailed stream information for the given `key`. See the example for
                a sample response.

        Examples:
            >>> await client.xinfo_stream_full("my_stream")
                {
                    b"length": 4,
                    b"radix-tree-keys": 1L,
                    b"radix-tree-nodes": 2L,
                    b"last-generated-id": b"1719877599564-0",
                    b"max-deleted-entry-id": b"0-0",  # This field was added in Valkey version 7.0.0.
                    b"entries-added": 4L,  # This field was added in Valkey version 7.0.0.
                    b"recorded-first-entry-id": b"1719710679916-0",  # This field was added in Valkey version 7.0.0.
                    b"entries": [
                        [
                            b"1719710679916-0",
                            [b"foo1", b"bar1", b"foo2", b"bar2"],
                        ],
                        [
                            b"1719877599564-0":
                            [b"field1", b"value1"],
                        ]
                    ],
                    b"groups": [
                        {
                            b"name": b"mygroup",
                            b"last-delivered-id": b"1719710688676-0",
                            b"entries-read": 2,  # This field was added in Valkey version 7.0.0.
                            b"lag": 0,  # This field was added in Valkey version 7.0.0.
                            b"pel-count": 2,
                            b"pending": [
                                [
                                    b"1719710679916-0",
                                    b"Alice",
                                    1719710707260,
                                    1,
                                ],
                                [
                                    b"1719710688676-0",
                                    b"Alice",
                                    1719710718373,
                                    1,
                                ],
                            ],
                            b"consumers": [
                                {
                                    b"name": b"Alice",
                                    b"seen-time": 1719710718373,
                                    b"active-time": 1719710718373,  # This field was added in Valkey version 7.2.0.
                                    b"pel-count": 2,
                                    b"pending": [
                                        [
                                            b"1719710679916-0",
                                            1719710707260,
                                            1
                                        ],
                                        [
                                            b"1719710688676-0",
                                            1719710718373,
                                            1
                                        ]
                                    ]
                                }
                            ]
                        }
                    ]
                }
                # Detailed stream information for "my_stream".

        Since: Valkey version 6.0.0.
        """
        args = [key, "FULL"]
        if count is not None:
            args.extend(["COUNT", str(count)])

        return cast(
            TXInfoStreamFullResponse,
            await self._execute_command(RequestType.XInfoStream, args),
        )

    async def geoadd(
        self,
        key: TEncodable,
        members_geospatialdata: Mapping[TEncodable, GeospatialData],
        existing_options: Optional[ConditionalChange] = None,
        changed: bool = False,
    ) -> int:
        """
        Adds geospatial members with their positions to the specified sorted set stored at `key`.
        If a member is already a part of the sorted set, its position is updated.

        See https://valkey.io/commands/geoadd for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            members_geospatialdata (Mapping[TEncodable, GeospatialData]): A mapping of member names to their corresponding positions. See `GeospatialData`.
            The command will report an error when the user attempts to index coordinates outside the specified ranges.
            existing_options (Optional[ConditionalChange]): Options for handling existing members.
                - NX: Only add new elements.
                - XX: Only update existing elements.
            changed (bool): Modify the return value to return the number of changed elements, instead of the number of new elements added.

        Returns:
            int: The number of elements added to the sorted set.
            If `changed` is set, returns the number of elements updated in the sorted set.

        Examples:
            >>> await client.geoadd("my_sorted_set", {"Palermo": GeospatialData(13.361389, 38.115556), "Catania": GeospatialData(15.087269, 37.502669)})
                2  # Indicates that two elements have been added to the sorted set "my_sorted_set".
            >>> await client.geoadd("my_sorted_set", {"Palermo": GeospatialData(14.361389, 38.115556)}, existing_options=ConditionalChange.XX, changed=True)
                1  # Updates the position of an existing member in the sorted set "my_sorted_set".
        """
        args = [key]
        if existing_options:
            args.append(existing_options.value)

        if changed:
            args.append("CH")

        members_geospatialdata_list = [
            coord
            for member, position in members_geospatialdata.items()
            for coord in [str(position.longitude), str(position.latitude), member]
        ]
        args += members_geospatialdata_list

        return cast(
            int,
            await self._execute_command(RequestType.GeoAdd, args),
        )

    async def geodist(
        self,
        key: TEncodable,
        member1: TEncodable,
        member2: TEncodable,
        unit: Optional[GeoUnit] = None,
    ) -> Optional[float]:
        """
        Returns the distance between two members in the geospatial index stored at `key`.

        See https://valkey.io/commands/geodist for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            member1 (TEncodable): The name of the first member.
            member2 (TEncodable): The name of the second member.
            unit (Optional[GeoUnit]): The unit of distance measurement. See `GeoUnit`.
                If not specified, the default unit is `METERS`.

        Returns:
            Optional[float]: The distance between `member1` and `member2`.
            If one or both members do not exist, or if the key does not exist, returns None.

        Examples:
            >>> await client.geoadd("my_geo_set", {"Palermo": GeospatialData(13.361389, 38.115556), "Catania": GeospatialData(15.087269, 37.502669)})
            >>> await client.geodist("my_geo_set", "Palermo", "Catania")
                166274.1516  # Indicates the distance between "Palermo" and "Catania" in meters.
            >>> await client.geodist("my_geo_set", "Palermo", "Palermo", unit=GeoUnit.KILOMETERS)
                166.2742  # Indicates the distance between "Palermo" and "Palermo" in kilometers.
            >>> await client.geodist("my_geo_set", "non-existing", "Palermo", unit=GeoUnit.KILOMETERS)
                None  # Returns None for non-existing member.
        """
        args = [key, member1, member2]
        if unit:
            args.append(unit.value)

        return cast(
            Optional[float],
            await self._execute_command(RequestType.GeoDist, args),
        )

    async def geohash(
        self, key: TEncodable, members: List[TEncodable]
    ) -> List[Optional[bytes]]:
        """
        Returns the GeoHash bytes strings representing the positions of all the specified members in the sorted set stored at
        `key`.

        See https://valkey.io/commands/geohash for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            members (List[TEncodable]): The list of members whose GeoHash bytes strings are to be retrieved.

        Returns:
            List[Optional[bytes]]: A list of GeoHash bytes strings representing the positions of the specified members stored at `key`.
            If a member does not exist in the sorted set, a None value is returned for that member.

        Examples:
            >>> await client.geoadd("my_geo_sorted_set", {"Palermo": GeospatialData(13.361389, 38.115556), "Catania": GeospatialData(15.087269, 37.502669)})
            >>> await client.geohash("my_geo_sorted_set", ["Palermo", "Catania", "some city])
                ["sqc8b49rny0", "sqdtr74hyu0", None]  # Indicates the GeoHash bytes strings for the specified members.
        """
        return cast(
            List[Optional[bytes]],
            await self._execute_command(RequestType.GeoHash, [key] + members),
        )

    async def geopos(
        self,
        key: TEncodable,
        members: List[TEncodable],
    ) -> List[Optional[List[float]]]:
        """
        Returns the positions (longitude and latitude) of all the given members of a geospatial index in the sorted set stored at
        `key`.

        See https://valkey.io/commands/geopos for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            members (List[TEncodable]): The members for which to get the positions.

        Returns:
            List[Optional[List[float]]]: A list of positions (longitude and latitude) corresponding to the given members.
            If a member does not exist, its position will be None.

        Example:
            >>> await client.geoadd("my_geo_sorted_set", {"Palermo": GeospatialData(13.361389, 38.115556), "Catania": GeospatialData(15.087269, 37.502669)})
            >>> await client.geopos("my_geo_sorted_set", ["Palermo", "Catania", "NonExisting"])
                [[13.36138933897018433, 38.11555639549629859], [15.08726745843887329, 37.50266842333162032], None]
        """
        return cast(
            List[Optional[List[float]]],
            await self._execute_command(RequestType.GeoPos, [key] + members),
        )

    async def geosearch(
        self,
        key: TEncodable,
        search_from: Union[str, bytes, GeospatialData],
        search_by: Union[GeoSearchByRadius, GeoSearchByBox],
        order_by: Optional[OrderBy] = None,
        count: Optional[GeoSearchCount] = None,
        with_coord: bool = False,
        with_dist: bool = False,
        with_hash: bool = False,
    ) -> List[Union[bytes, List[Union[bytes, float, int, List[float]]]]]:
        """
        Searches for members in a sorted set stored at `key` representing geospatial data within a circular or rectangular area.

        See https://valkey.io/commands/geosearch/ for more details.

        Args:
            key (TEncodable): The key of the sorted set representing geospatial data.
            search_from (Union[str, bytes, GeospatialData]): The location to search from. Can be specified either as a member
                from the sorted set or as a geospatial data (see `GeospatialData`).
            search_by (Union[GeoSearchByRadius, GeoSearchByBox]): The search criteria.
                For circular area search, see `GeoSearchByRadius`.
                For rectangular area search, see `GeoSearchByBox`.
            order_by (Optional[OrderBy]): Specifies the order in which the results should be returned.
                    - `ASC`: Sorts items from the nearest to the farthest, relative to the center point.
                    - `DESC`: Sorts items from the farthest to the nearest, relative to the center point.
                If not specified, the results would be unsorted.
            count (Optional[GeoSearchCount]): Specifies the maximum number of results to return. See `GeoSearchCount`.
                If not specified, return all results.
            with_coord (bool): Whether to include coordinates of the returned items. Defaults to False.
            with_dist (bool): Whether to include distance from the center in the returned items.
                The distance is returned in the same unit as specified for the `search_by` arguments. Defaults to False.
            with_hash (bool): Whether to include geohash of the returned items. Defaults to False.

        Returns:
            List[Union[bytes, List[Union[bytes, float, int, List[float]]]]]: By default, returns a list of members (locations) names.
            If any of `with_coord`, `with_dist` or `with_hash` are True, returns an array of arrays, we're each sub array represents a single item in the following order:
                (bytes): The member (location) name.
                (float): The distance from the center as a floating point number, in the same unit specified in the radius, if `with_dist` is set to True.
                (int): The Geohash integer, if `with_hash` is set to True.
                List[float]: The coordinates as a two item [longitude,latitude] array, if `with_coord` is set to True.

        Examples:
            >>> await client.geoadd("my_geo_sorted_set", {"edge1": GeospatialData(12.758489, 38.788135), "edge2": GeospatialData(17.241510, 38.788135)}})
            >>> await client.geoadd("my_geo_sorted_set", {"Palermo": GeospatialData(13.361389, 38.115556), "Catania": GeospatialData(15.087269, 37.502669)})
            >>> await client.geosearch("my_geo_sorted_set", "Catania", GeoSearchByRadius(175, GeoUnit.MILES), OrderBy.DESC)
                ['Palermo', 'Catania'] # Returned the locations names within the radius of 175 miles, with the center being 'Catania' from farthest to nearest.
            >>> await client.geosearch("my_geo_sorted_set", GeospatialData(15, 37), GeoSearchByBox(400, 400, GeoUnit.KILOMETERS), OrderBy.DESC, with_coord=true, with_dist=true, with_hash=true)
                [
                    [
                        b"Catania",
                        [56.4413, 3479447370796909, [15.087267458438873, 37.50266842333162]],
                    ],
                    [
                        b"Palermo",
                        [190.4424, 3479099956230698, [13.361389338970184, 38.1155563954963]],
                    ],
                    [
                        b"edge2",
                        [279.7403, 3481342659049484, [17.241510450839996, 38.78813451624225]],
                    ],
                    [
                        b"edge1",
                        [279.7405, 3479273021651468, [12.75848776102066, 38.78813451624225]],
                    ],
                ]  # Returns locations within the square box of 400 km, with the center being a specific point, from nearest to farthest with the dist, hash and coords.

        Since: Valkey version 6.2.0.
        """
        args = _create_geosearch_args(
            [key],
            search_from,
            search_by,
            order_by,
            count,
            with_coord,
            with_dist,
            with_hash,
        )

        return cast(
            List[Union[bytes, List[Union[bytes, float, int, List[float]]]]],
            await self._execute_command(RequestType.GeoSearch, args),
        )

    async def geosearchstore(
        self,
        destination: TEncodable,
        source: TEncodable,
        search_from: Union[str, bytes, GeospatialData],
        search_by: Union[GeoSearchByRadius, GeoSearchByBox],
        count: Optional[GeoSearchCount] = None,
        store_dist: bool = False,
    ) -> int:
        """
        Searches for members in a sorted set stored at `key` representing geospatial data within a circular or rectangular area and stores the result in `destination`.
        If `destination` already exists, it is overwritten. Otherwise, a new sorted set will be created.

        To get the result directly, see `geosearch`.

        Note:
            When in cluster mode, both `source` and `destination` must map to the same hash slot.

            Args:
                destination (TEncodable): The key to store the search results.
                source (TEncodable): The key of the sorted set representing geospatial data to search from.
                search_from (Union[str, bytes, GeospatialData]): The location to search from. Can be specified either as a member
                    from the sorted set or as a geospatial data (see `GeospatialData`).
                search_by (Union[GeoSearchByRadius, GeoSearchByBox]): The search criteria.
                    For circular area search, see `GeoSearchByRadius`.
                    For rectangular area search, see `GeoSearchByBox`.
                count (Optional[GeoSearchCount]): Specifies the maximum number of results to store. See `GeoSearchCount`.
                    If not specified, stores all results.
                store_dist (bool): Determines what is stored as the sorted set score. Defaults to False.
                    - If set to False, the geohash of the location will be stored as the sorted set score.
                    - If set to True, the distance from the center of the shape (circle or box) will be stored as the sorted set score.
                        The distance is represented as a floating-point number in the same unit specified for that shape.

        Returns:
            int: The number of elements in the resulting sorted set stored at `destination`.

        Examples:
            >>> await client.geoadd("my_geo_sorted_set", {"Palermo": GeospatialData(13.361389, 38.115556), "Catania": GeospatialData(15.087269, 37.502669)})
            >>> await client.geosearchstore("my_dest_sorted_set", "my_geo_sorted_set", "Catania", GeoSearchByRadius(175, GeoUnit.MILES))
                2 # Number of elements stored in "my_dest_sorted_set".
            >>> await client.zrange_withscores("my_dest_sorted_set", RangeByIndex(0, -1))
                {b"Palermo": 3479099956230698.0, b"Catania": 3479447370796909.0} # The elements within te search area, with their geohash as score.
            >>> await client.geosearchstore("my_dest_sorted_set", "my_geo_sorted_set", GeospatialData(15, 37), GeoSearchByBox(400, 400, GeoUnit.KILOMETERS), store_dist=True)
                2 # Number of elements stored in "my_dest_sorted_set", with distance as score.
            >>> await client.zrange_withscores("my_dest_sorted_set", RangeByIndex(0, -1))
                {b"Catania": 56.4412578701582, b"Palermo": 190.44242984775784} # The elements within te search area, with the distance as score.

        Since: Valkey version 6.2.0.
        """
        args = _create_geosearch_args(
            [destination, source],
            search_from,
            search_by,
            None,
            count,
            False,
            False,
            False,
            store_dist,
        )

        return cast(
            int,
            await self._execute_command(RequestType.GeoSearchStore, args),
        )

    async def zadd(
        self,
        key: TEncodable,
        members_scores: Mapping[TEncodable, float],
        existing_options: Optional[ConditionalChange] = None,
        update_condition: Optional[UpdateOptions] = None,
        changed: bool = False,
    ) -> int:
        """
        Adds members with their scores to the sorted set stored at `key`.
        If a member is already a part of the sorted set, its score is updated.

        See https://valkey.io/commands/zadd/ for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            members_scores (Mapping[TEncodable, float]): A mapping of members to their corresponding scores.
            existing_options (Optional[ConditionalChange]): Options for handling existing members.
                - NX: Only add new elements.
                - XX: Only update existing elements.
            update_condition (Optional[UpdateOptions]): Options for updating scores.
                - GT: Only update scores greater than the current values.
                - LT: Only update scores less than the current values.
            changed (bool): Modify the return value to return the number of changed elements, instead of the number of new elements added.

        Returns:
            int: The number of elements added to the sorted set.
            If `changed` is set, returns the number of elements updated in the sorted set.

        Examples:
            >>> await client.zadd("my_sorted_set", {"member1": 10.5, "member2": 8.2})
                2  # Indicates that two elements have been added to the sorted set "my_sorted_set."
            >>> await client.zadd("existing_sorted_set", {"member1": 15.0, "member2": 5.5}, existing_options=ConditionalChange.XX, changed=True)
                2  # Updates the scores of two existing members in the sorted set "existing_sorted_set."
        """
        args = [key]
        if existing_options:
            args.append(existing_options.value)

        if update_condition:
            args.append(update_condition.value)

        if changed:
            args.append("CH")

        if existing_options and update_condition:
            if existing_options == ConditionalChange.ONLY_IF_DOES_NOT_EXIST:
                raise ValueError(
                    "The GT, LT and NX options are mutually exclusive. "
                    f"Cannot choose both {update_condition.value} and NX."
                )

        members_scores_list = [
            str(item) for pair in members_scores.items() for item in pair[::-1]
        ]
        args += members_scores_list

        return cast(
            int,
            await self._execute_command(RequestType.ZAdd, args),
        )

    async def zadd_incr(
        self,
        key: TEncodable,
        member: TEncodable,
        increment: float,
        existing_options: Optional[ConditionalChange] = None,
        update_condition: Optional[UpdateOptions] = None,
    ) -> Optional[float]:
        """
        Increments the score of member in the sorted set stored at `key` by `increment`.
        If `member` does not exist in the sorted set, it is added with `increment` as its score (as if its previous score was 0.0).
        If `key` does not exist, a new sorted set with the specified member as its sole member is created.

        See https://valkey.io/commands/zadd/ for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            member (TEncodable): A member in the sorted set to increment.
            increment (float): The score to increment the member.
            existing_options (Optional[ConditionalChange]): Options for handling the member's existence.
                - NX: Only increment a member that doesn't exist.
                - XX: Only increment an existing member.
            update_condition (Optional[UpdateOptions]): Options for updating the score.
                - GT: Only increment the score of the member if the new score will be greater than the current score.
                - LT: Only increment (decrement) the score of the member if the new score will be less than the current score.

        Returns:
            Optional[float]: The score of the member.
            If there was a conflict with choosing the XX/NX/LT/GT options, the operation aborts and None is returned.

        Examples:
            >>> await client.zadd_incr("my_sorted_set", member , 5.0)
                5.0
            >>> await client.zadd_incr("existing_sorted_set", member , "3.0" , UpdateOptions.LESS_THAN)
                None
        """
        args = [key]
        if existing_options:
            args.append(existing_options.value)

        if update_condition:
            args.append(update_condition.value)

        args.append("INCR")

        if existing_options and update_condition:
            if existing_options == ConditionalChange.ONLY_IF_DOES_NOT_EXIST:
                raise ValueError(
                    "The GT, LT and NX options are mutually exclusive. "
                    f"Cannot choose both {update_condition.value} and NX."
                )

        args += [str(increment), member]
        return cast(
            Optional[float],
            await self._execute_command(RequestType.ZAdd, args),
        )

    async def zcard(self, key: TEncodable) -> int:
        """
        Returns the cardinality (number of elements) of the sorted set stored at `key`.

        See https://valkey.io/commands/zcard/ for more details.

        Args:
            key (TEncodable): The key of the sorted set.

        Returns:
            int: The number of elements in the sorted set.
            If `key` does not exist, it is treated as an empty sorted set, and the command returns 0.

        Examples:
            >>> await client.zcard("my_sorted_set")
                3  # Indicates that there are 3 elements in the sorted set "my_sorted_set".
            >>> await client.zcard("non_existing_key")
                0
        """
        return cast(int, await self._execute_command(RequestType.ZCard, [key]))

    async def zcount(
        self,
        key: TEncodable,
        min_score: Union[InfBound, ScoreBoundary],
        max_score: Union[InfBound, ScoreBoundary],
    ) -> int:
        """
        Returns the number of members in the sorted set stored at `key` with scores between `min_score` and `max_score`.

        See https://valkey.io/commands/zcount/ for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            min_score (Union[InfBound, ScoreBoundary]): The minimum score to count from.
                Can be an instance of InfBound representing positive/negative infinity,
                or ScoreBoundary representing a specific score and inclusivity.
            max_score (Union[InfBound, ScoreBoundary]): The maximum score to count up to.
                Can be an instance of InfBound representing positive/negative infinity,
                or ScoreBoundary representing a specific score and inclusivity.

        Returns:
            int: The number of members in the specified score range.
            If `key` does not exist, it is treated as an empty sorted set, and the command returns 0.
            If `max_score` < `min_score`, 0 is returned.

        Examples:
            >>> await client.zcount("my_sorted_set", ScoreBoundary(5.0 , is_inclusive=true) , InfBound.POS_INF)
                2  # Indicates that there are 2 members with scores between 5.0 (not exclusive) and +inf in the sorted set "my_sorted_set".
            >>> await client.zcount("my_sorted_set", ScoreBoundary(5.0 , is_inclusive=true) , ScoreBoundary(10.0 , is_inclusive=false))
                1  # Indicates that there is one ScoreBoundary with 5.0 < score <= 10.0 in the sorted set "my_sorted_set".
        """
        score_min = (
            min_score.value["score_arg"]
            if type(min_score) == InfBound
            else min_score.value
        )
        score_max = (
            max_score.value["score_arg"]
            if type(max_score) == InfBound
            else max_score.value
        )
        return cast(
            int,
            await self._execute_command(
                RequestType.ZCount, [key, score_min, score_max]
            ),
        )

    async def zincrby(
        self, key: TEncodable, increment: float, member: TEncodable
    ) -> float:
        """
        Increments the score of `member` in the sorted set stored at `key` by `increment`.
        If `member` does not exist in the sorted set, it is added with `increment` as its score.
        If `key` does not exist, a new sorted set is created with the specified member as its sole member.

        See https://valkey.io/commands/zincrby/ for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            increment (float): The score increment.
            member (TEncodable): A member of the sorted set.

        Returns:
            float: The new score of `member`.

        Examples:
            >>> await client.zadd("my_sorted_set", {"member": 10.5, "member2": 8.2})
            >>> await client.zincrby("my_sorted_set", 1.2, "member")
                11.7  # The member existed in the set before score was altered, the new score is 11.7.
            >>> await client.zincrby("my_sorted_set", -1.7, "member")
                10.0 # Negative increment, decrements the score.
            >>> await client.zincrby("my_sorted_set", 5.5, "non_existing_member")
                5.5  # A new member is added to the sorted set with the score being 5.5.
        """
        return cast(
            float,
            await self._execute_command(
                RequestType.ZIncrBy, [key, str(increment), member]
            ),
        )

    async def zpopmax(
        self, key: TEncodable, count: Optional[int] = None
    ) -> Mapping[bytes, float]:
        """
        Removes and returns the members with the highest scores from the sorted set stored at `key`.
        If `count` is provided, up to `count` members with the highest scores are removed and returned.
        Otherwise, only one member with the highest score is removed and returned.

        See https://valkey.io/commands/zpopmax for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            count (Optional[int]): Specifies the quantity of members to pop. If not specified, pops one member.
            If `count` is higher than the sorted set's cardinality, returns all members and their scores, ordered from highest to lowest.

        Returns:
            Mapping[bytes, float]: A map of the removed members and their scores, ordered from the one with the highest score to the one with the lowest.
            If `key` doesn't exist, it will be treated as an empy sorted set and the command returns an empty map.

        Examples:
            >>> await client.zpopmax("my_sorted_set")
                {b'member1': 10.0}  # Indicates that 'member1' with a score of 10.0 has been removed from the sorted set.
            >>> await client.zpopmax("my_sorted_set", 2)
                {b'member2': 8.0, b'member3': 7.5}  # Indicates that 'member2' with a score of 8.0 and 'member3' with a score of 7.5 have been removed from the sorted set.
        """
        return cast(
            Mapping[bytes, float],
            await self._execute_command(
                RequestType.ZPopMax, [key, str(count)] if count else [key]
            ),
        )

    async def bzpopmax(
        self, keys: List[TEncodable], timeout: float
    ) -> Optional[List[Union[bytes, float]]]:
        """
        Pops the member with the highest score from the first non-empty sorted set, with the given keys being checked in
        the order that they are given. Blocks the connection when there are no members to remove from any of the given
        sorted sets.

        When in cluster mode, all keys must map to the same hash slot.

        `BZPOPMAX` is the blocking variant of `ZPOPMAX`.

        `BZPOPMAX` is a client blocking command, see https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands for more details and best practices.

        See https://valkey.io/commands/bzpopmax for more details.

        Args:
            keys (List[TEncodable]): The keys of the sorted sets.
            timeout (float): The number of seconds to wait for a blocking operation to complete.
                A value of 0 will block indefinitely.

        Returns:
            Optional[List[Union[bytes, float]]]: An array containing the key where the member was popped out, the member itself,
                and the member score. If no member could be popped and the `timeout` expired, returns None.

        Examples:
            >>> await client.zadd("my_sorted_set1", {"member1": 10.0, "member2": 5.0})
                2  # Two elements have been added to the sorted set at "my_sorted_set1".
            >>> await client.bzpopmax(["my_sorted_set1", "my_sorted_set2"], 0.5)
                [b'my_sorted_set1', b'member1', 10.0]  # "member1" with a score of 10.0 has been removed from "my_sorted_set1".
        """
        return cast(
            Optional[List[Union[bytes, float]]],
            await self._execute_command(RequestType.BZPopMax, keys + [str(timeout)]),
        )

    async def zpopmin(
        self, key: TEncodable, count: Optional[int] = None
    ) -> Mapping[bytes, float]:
        """
        Removes and returns the members with the lowest scores from the sorted set stored at `key`.
        If `count` is provided, up to `count` members with the lowest scores are removed and returned.
        Otherwise, only one member with the lowest score is removed and returned.

        See https://valkey.io/commands/zpopmin for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            count (Optional[int]): Specifies the quantity of members to pop. If not specified, pops one member.
            If `count` is higher than the sorted set's cardinality, returns all members and their scores.

        Returns:
            Mapping[bytes, float]: A map of the removed members and their scores, ordered from the one with the lowest score to the one with the highest.
            If `key` doesn't exist, it will be treated as an empy sorted set and the command returns an empty map.

        Examples:
            >>> await client.zpopmin("my_sorted_set")
                {b'member1': 5.0}  # Indicates that 'member1' with a score of 5.0 has been removed from the sorted set.
            >>> await client.zpopmin("my_sorted_set", 2)
                {b'member3': 7.5 , b'member2': 8.0}  # Indicates that 'member3' with a score of 7.5 and 'member2' with a score of 8.0 have been removed from the sorted set.
        """
        args: List[TEncodable] = [key, str(count)] if count else [key]
        return cast(
            Mapping[bytes, float],
            await self._execute_command(RequestType.ZPopMin, args),
        )

    async def bzpopmin(
        self, keys: List[TEncodable], timeout: float
    ) -> Optional[List[Union[bytes, float]]]:
        """
        Pops the member with the lowest score from the first non-empty sorted set, with the given keys being checked in
        the order that they are given. Blocks the connection when there are no members to remove from any of the given
        sorted sets.

        When in cluster mode, all keys must map to the same hash slot.

        `BZPOPMIN` is the blocking variant of `ZPOPMIN`.

        `BZPOPMIN` is a client blocking command, see https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands for more details and best practices.

        See https://valkey.io/commands/bzpopmin for more details.

        Args:
            keys (List[TEncodable]): The keys of the sorted sets.
            timeout (float): The number of seconds to wait for a blocking operation to complete.
                A value of 0 will block indefinitely.

        Returns:
            Optional[List[Union[bytes, float]]]: An array containing the key where the member was popped out, the member itself,
                and the member score. If no member could be popped and the `timeout` expired, returns None.

        Examples:
            >>> await client.zadd("my_sorted_set1", {"member1": 10.0, "member2": 5.0})
                2  # Two elements have been added to the sorted set at "my_sorted_set1".
            >>> await client.bzpopmin(["my_sorted_set1", "my_sorted_set2"], 0.5)
                [b'my_sorted_set1', b'member2', 5.0]  # "member2" with a score of 5.0 has been removed from "my_sorted_set1".
        """
        args: List[TEncodable] = keys + [str(timeout)]
        return cast(
            Optional[List[Union[bytes, float]]],
            await self._execute_command(RequestType.BZPopMin, args),
        )

    async def zrange(
        self,
        key: TEncodable,
        range_query: Union[RangeByIndex, RangeByLex, RangeByScore],
        reverse: bool = False,
    ) -> List[bytes]:
        """
        Returns the specified range of elements in the sorted set stored at `key`.

        ZRANGE can perform different types of range queries: by index (rank), by the score, or by lexicographical order.

        See https://valkey.io/commands/zrange/ for more details.

        To get the elements with their scores, see zrange_withscores.

        Args:
            key (TEncodable): The key of the sorted set.
            range_query (Union[RangeByIndex, RangeByLex, RangeByScore]): The range query object representing the type of range query to perform.
                - For range queries by index (rank), use RangeByIndex.
                - For range queries by lexicographical order, use RangeByLex.
                - For range queries by score, use RangeByScore.
            reverse (bool): If True, reverses the sorted set, with index 0 as the element with the highest score.

        Returns:
            List[bytes]: A list of elements within the specified range.
            If `key` does not exist, it is treated as an empty sorted set, and the command returns an empty array.

        Examples:
            >>> await client.zrange("my_sorted_set", RangeByIndex(0, -1))
                [b'member1', b'member2', b'member3']  # Returns all members in ascending order.
            >>> await client.zrange("my_sorted_set", RangeByScore(start=InfBound.NEG_INF, stop=ScoreBoundary(3)))
                [b'member2', b'member3'] # Returns members with scores within the range of negative infinity to 3, in ascending order.
        """
        args = _create_zrange_args(key, range_query, reverse, with_scores=False)

        return cast(List[bytes], await self._execute_command(RequestType.ZRange, args))

    async def zrange_withscores(
        self,
        key: TEncodable,
        range_query: Union[RangeByIndex, RangeByScore],
        reverse: bool = False,
    ) -> Mapping[bytes, float]:
        """
        Returns the specified range of elements with their scores in the sorted set stored at `key`.
        Similar to ZRANGE but with a WITHSCORE flag.

        See https://valkey.io/commands/zrange/ for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            range_query (Union[RangeByIndex, RangeByScore]): The range query object representing the type of range query to perform.
                - For range queries by index (rank), use RangeByIndex.
                - For range queries by score, use RangeByScore.
            reverse (bool): If True, reverses the sorted set, with index 0 as the element with the highest score.

        Returns:
            Mapping[bytes , float]: A map of elements and their scores within the specified range.
            If `key` does not exist, it is treated as an empty sorted set, and the command returns an empty map.

        Examples:
            >>> await client.zrange_withscores("my_sorted_set", RangeByScore(ScoreBoundary(10), ScoreBoundary(20)))
                {b'member1': 10.5, b'member2': 15.2}  # Returns members with scores between 10 and 20 with their scores.
           >>> await client.zrange_withscores("my_sorted_set", RangeByScore(start=InfBound.NEG_INF, stop=ScoreBoundary(3)))
                {b'member4': -2.0, b'member7': 1.5} # Returns members with with scores within the range of negative infinity to 3, with their scores.
        """
        args = _create_zrange_args(key, range_query, reverse, with_scores=True)

        return cast(
            Mapping[bytes, float], await self._execute_command(RequestType.ZRange, args)
        )

    async def zrangestore(
        self,
        destination: TEncodable,
        source: TEncodable,
        range_query: Union[RangeByIndex, RangeByLex, RangeByScore],
        reverse: bool = False,
    ) -> int:
        """
        Stores a specified range of elements from the sorted set at `source`, into a new sorted set at `destination`. If
        `destination` doesn't exist, a new sorted set is created; if it exists, it's overwritten.

        ZRANGESTORE can perform different types of range queries: by index (rank), by the score, or by lexicographical
        order.

        See https://valkey.io/commands/zrangestore for more details.

        Note:
            When in Cluster mode, `source` and `destination` must map to the same hash slot.

        Args:
            destination (TEncodable): The key for the destination sorted set.
            source (TEncodable): The key of the source sorted set.
            range_query (Union[RangeByIndex, RangeByLex, RangeByScore]): The range query object representing the type of range query to perform.
                - For range queries by index (rank), use RangeByIndex.
                - For range queries by lexicographical order, use RangeByLex.
                - For range queries by score, use RangeByScore.
            reverse (bool): If True, reverses the sorted set, with index 0 as the element with the highest score.

        Returns:
            int: The number of elements in the resulting sorted set.

        Examples:
            >>> await client.zrangestore("destination_key", "my_sorted_set", RangeByIndex(0, 2), True)
                3  # The 3 members with the highest scores from "my_sorted_set" were stored in the sorted set at "destination_key".
            >>> await client.zrangestore("destination_key", "my_sorted_set", RangeByScore(InfBound.NEG_INF, ScoreBoundary(3)))
                2  # The 2 members with scores between negative infinity and 3 (inclusive) from "my_sorted_set" were stored in the sorted set at "destination_key".
        """
        args = _create_zrange_args(source, range_query, reverse, False, destination)

        return cast(int, await self._execute_command(RequestType.ZRangeStore, args))

    async def zrank(
        self,
        key: TEncodable,
        member: TEncodable,
    ) -> Optional[int]:
        """
        Returns the rank of `member` in the sorted set stored at `key`, with scores ordered from low to high.

        See https://valkey.io/commands/zrank for more details.

        To get the rank of `member` with its score, see `zrank_withscore`.

        Args:
            key (TEncodable): The key of the sorted set.
            member (TEncodable): The member whose rank is to be retrieved.

        Returns:
            Optional[int]: The rank of `member` in the sorted set.
            If `key` doesn't exist, or if `member` is not present in the set, None will be returned.

            Examples:
            >>> await client.zrank("my_sorted_set", "member2")
                1  # Indicates that "member2" has the second-lowest score in the sorted set "my_sorted_set".
            >>> await client.zrank("my_sorted_set", "non_existing_member")
                None  # Indicates that "non_existing_member" is not present in the sorted set "my_sorted_set".
        """
        return cast(
            Optional[int], await self._execute_command(RequestType.ZRank, [key, member])
        )

    async def zrank_withscore(
        self,
        key: TEncodable,
        member: TEncodable,
    ) -> Optional[List[Union[int, float]]]:
        """
        Returns the rank of `member` in the sorted set stored at `key` with its score, where scores are ordered from the lowest to highest.

        See https://valkey.io/commands/zrank for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            member (TEncodable): The member whose rank is to be retrieved.

        Returns:
            Optional[List[Union[int, float]]]: A list containing the rank and score of `member` in the sorted set.
            If `key` doesn't exist, or if `member` is not present in the set, None will be returned.

        Examples:
            >>> await client.zrank_withscore("my_sorted_set", "member2")
                [1 , 6.0]  # Indicates that "member2" with score 6.0 has the second-lowest score in the sorted set "my_sorted_set".
            >>> await client.zrank_withscore("my_sorted_set", "non_existing_member")
                None  # Indicates that "non_existing_member" is not present in the sorted set "my_sorted_set".

        Since: Valkey version 7.2.0.
        """
        return cast(
            Optional[List[Union[int, float]]],
            await self._execute_command(RequestType.ZRank, [key, member, "WITHSCORE"]),
        )

    async def zrevrank(self, key: TEncodable, member: TEncodable) -> Optional[int]:
        """
        Returns the rank of `member` in the sorted set stored at `key`, where scores are ordered from the highest to
        lowest, starting from `0`.

        To get the rank of `member` with its score, see `zrevrank_withscore`.

        See https://valkey.io/commands/zrevrank for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            member (TEncodable): The member whose rank is to be retrieved.

        Returns:
            Optional[int]: The rank of `member` in the sorted set, where ranks are ordered from high to low based on scores.
                If `key` doesn't exist, or if `member` is not present in the set, `None` will be returned.

        Examples:
            >>> await client.zadd("my_sorted_set", {"member1": 10.5, "member2": 8.2, "member3": 9.6})
            >>> await client.zrevrank("my_sorted_set", "member2")
                2  # "member2" has the third-highest score in the sorted set "my_sorted_set"
        """
        return cast(
            Optional[int],
            await self._execute_command(RequestType.ZRevRank, [key, member]),
        )

    async def zrevrank_withscore(
        self, key: TEncodable, member: TEncodable
    ) -> Optional[List[Union[int, float]]]:
        """
        Returns the rank of `member` in the sorted set stored at `key` with its score, where scores are ordered from the
        highest to lowest, starting from `0`.

        See https://valkey.io/commands/zrevrank for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            member (TEncodable): The member whose rank is to be retrieved.

        Returns:
            Optional[List[Union[int, float]]]: A list containing the rank (as `int`) and score (as `float`) of `member`
                in the sorted set, where ranks are ordered from high to low based on scores.
                If `key` doesn't exist, or if `member` is not present in the set, `None` will be returned.

        Examples:
            >>> await client.zadd("my_sorted_set", {"member1": 10.5, "member2": 8.2, "member3": 9.6})
            >>> await client.zrevrank("my_sorted_set", "member2")
                [2, 8.2]  # "member2" with score 8.2 has the third-highest score in the sorted set "my_sorted_set"

        Since: Valkey version 7.2.0.
        """
        return cast(
            Optional[List[Union[int, float]]],
            await self._execute_command(
                RequestType.ZRevRank, [key, member, "WITHSCORE"]
            ),
        )

    async def zrem(
        self,
        key: TEncodable,
        members: List[TEncodable],
    ) -> int:
        """
        Removes the specified members from the sorted set stored at `key`.
        Specified members that are not a member of this set are ignored.

        See https://valkey.io/commands/zrem/ for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            members (List[TEncodable]): A list of members to remove from the sorted set.

        Returns:
            int: The number of members that were removed from the sorted set, not including non-existing members.
            If `key` does not exist, it is treated as an empty sorted set, and the command returns 0.

        Examples:
            >>> await client.zrem("my_sorted_set", ["member1", "member2"])
                2  # Indicates that two members have been removed from the sorted set "my_sorted_set."
            >>> await client.zrem("non_existing_sorted_set", ["member1", "member2"])
                0  # Indicates that no members were removed as the sorted set "non_existing_sorted_set" does not exist.
        """
        return cast(
            int,
            await self._execute_command(RequestType.ZRem, [key] + members),
        )

    async def zremrangebyscore(
        self,
        key: TEncodable,
        min_score: Union[InfBound, ScoreBoundary],
        max_score: Union[InfBound, ScoreBoundary],
    ) -> int:
        """
        Removes all elements in the sorted set stored at `key` with a score between `min_score` and `max_score`.

        See https://valkey.io/commands/zremrangebyscore/ for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            min_score (Union[InfBound, ScoreBoundary]): The minimum score to remove from.
                Can be an instance of InfBound representing positive/negative infinity,
                or ScoreBoundary representing a specific score and inclusivity.
            max_score (Union[InfBound, ScoreBoundary]): The maximum score to remove up to.
                Can be an instance of InfBound representing positive/negative infinity,
                or ScoreBoundary representing a specific score and inclusivity.
        Returns:
            int: The number of members that were removed from the sorted set.
            If `key` does not exist, it is treated as an empty sorted set, and the command returns 0.
            If `min_score` is greater than `max_score`, 0 is returned.

        Examples:
            >>> await client.zremrangebyscore("my_sorted_set",  ScoreBoundary(5.0 , is_inclusive=true) , InfBound.POS_INF)
                2  # Indicates that  2 members with scores between 5.0 (not exclusive) and +inf have been removed from the sorted set "my_sorted_set".
            >>> await client.zremrangebyscore("non_existing_sorted_set", ScoreBoundary(5.0 , is_inclusive=true) , ScoreBoundary(10.0 , is_inclusive=false))
                0  # Indicates that no members were removed as the sorted set "non_existing_sorted_set" does not exist.
        """
        score_min = (
            min_score.value["score_arg"]
            if type(min_score) == InfBound
            else min_score.value
        )
        score_max = (
            max_score.value["score_arg"]
            if type(max_score) == InfBound
            else max_score.value
        )

        return cast(
            int,
            await self._execute_command(
                RequestType.ZRemRangeByScore, [key, score_min, score_max]
            ),
        )

    async def zremrangebylex(
        self,
        key: TEncodable,
        min_lex: Union[InfBound, LexBoundary],
        max_lex: Union[InfBound, LexBoundary],
    ) -> int:
        """
        Removes all elements in the sorted set stored at `key` with a lexicographical order between `min_lex` and
        `max_lex`.

        See https://valkey.io/commands/zremrangebylex/ for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            min_lex (Union[InfBound, LexBoundary]): The minimum bound of the lexicographical range.
                Can be an instance of `InfBound` representing positive/negative infinity, or `LexBoundary`
                representing a specific lex and inclusivity.
            max_lex (Union[InfBound, LexBoundary]): The maximum bound of the lexicographical range.
                Can be an instance of `InfBound` representing positive/negative infinity, or `LexBoundary`
                representing a specific lex and inclusivity.

        Returns:
            int: The number of members that were removed from the sorted set.
                If `key` does not exist, it is treated as an empty sorted set, and the command returns `0`.
                If `min_lex` is greater than `max_lex`, `0` is returned.

        Examples:
            >>> await client.zremrangebylex("my_sorted_set",  LexBoundary("a", is_inclusive=False), LexBoundary("e"))
                4  # Indicates that 4 members, with lexicographical values ranging from "a" (exclusive) to "e" (inclusive), have been removed from "my_sorted_set".
            >>> await client.zremrangebylex("non_existing_sorted_set", InfBound.NEG_INF, LexBoundary("e"))
                0  # Indicates that no members were removed as the sorted set "non_existing_sorted_set" does not exist.
        """
        min_lex_arg = (
            min_lex.value["lex_arg"] if type(min_lex) == InfBound else min_lex.value
        )
        max_lex_arg = (
            max_lex.value["lex_arg"] if type(max_lex) == InfBound else max_lex.value
        )

        return cast(
            int,
            await self._execute_command(
                RequestType.ZRemRangeByLex, [key, min_lex_arg, max_lex_arg]
            ),
        )

    async def zremrangebyrank(
        self,
        key: TEncodable,
        start: int,
        end: int,
    ) -> int:
        """
        Removes all elements in the sorted set stored at `key` with rank between `start` and `end`.
        Both `start` and `end` are zero-based indexes with 0 being the element with the lowest score.
        These indexes can be negative numbers, where they indicate offsets starting at the element with the highest score.

        See https://valkey.io/commands/zremrangebyrank/ for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            start (int): The starting point of the range.
            end (int): The end of the range.

        Returns:
            int: The number of elements that were removed.
                If `start` exceeds the end of the sorted set, or if `start` is greater than `end`, `0` is returned.
                If `end` exceeds the actual end of the sorted set, the range will stop at the actual end of the sorted set.
                If `key` does not exist, `0` is returned.

        Examples:
            >>> await client.zremrangebyrank("my_sorted_set", 0, 4)
                5  # Indicates that 5 elements, with ranks ranging from 0 to 4 (inclusive), have been removed from "my_sorted_set".
            >>> await client.zremrangebyrank("my_sorted_set", 0, 4)
                0  # Indicates that nothing was removed.
        """
        return cast(
            int,
            await self._execute_command(
                RequestType.ZRemRangeByRank, [key, str(start), str(end)]
            ),
        )

    async def zlexcount(
        self,
        key: TEncodable,
        min_lex: Union[InfBound, LexBoundary],
        max_lex: Union[InfBound, LexBoundary],
    ) -> int:
        """
        Returns the number of members in the sorted set stored at `key` with lexicographical values between `min_lex` and `max_lex`.

        See https://valkey.io/commands/zlexcount/ for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            min_lex (Union[InfBound, LexBoundary]): The minimum lexicographical value to count from.
                Can be an instance of InfBound representing positive/negative infinity,
                or LexBoundary representing a specific lexicographical value and inclusivity.
            max_lex (Union[InfBound, LexBoundary]): The maximum lexicographical to count up to.
                Can be an instance of InfBound representing positive/negative infinity,
                or LexBoundary representing a specific lexicographical value and inclusivity.

        Returns:
            int: The number of members in the specified lexicographical range.
                If `key` does not exist, it is treated as an empty sorted set, and the command returns `0`.
                If `max_lex < min_lex`, `0` is returned.

        Examples:
            >>> await client.zlexcount("my_sorted_set",  LexBoundary("c" , is_inclusive=True), InfBound.POS_INF)
                2  # Indicates that there are 2 members with lexicographical values between "c" (inclusive) and positive infinity in the sorted set "my_sorted_set".
            >>> await client.zlexcount("my_sorted_set", LexBoundary("c" , is_inclusive=True), LexBoundary("k" , is_inclusive=False))
                1  # Indicates that there is one member with LexBoundary "c" <= lexicographical value < "k" in the sorted set "my_sorted_set".
        """
        min_lex_arg = (
            min_lex.value["lex_arg"] if type(min_lex) == InfBound else min_lex.value
        )
        max_lex_arg = (
            max_lex.value["lex_arg"] if type(max_lex) == InfBound else max_lex.value
        )

        return cast(
            int,
            await self._execute_command(
                RequestType.ZLexCount, [key, min_lex_arg, max_lex_arg]
            ),
        )

    async def zscore(self, key: TEncodable, member: TEncodable) -> Optional[float]:
        """
        Returns the score of `member` in the sorted set stored at `key`.

        See https://valkey.io/commands/zscore/ for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            member (TEncodable): The member whose score is to be retrieved.

        Returns:
            Optional[float]: The score of the member.
            If `member` does not exist in the sorted set, None is returned.
            If `key` does not exist,  None is returned.

        Examples:
            >>> await client.zscore("my_sorted_set", "member")
                10.5  # Indicates that the score of "member" in the sorted set "my_sorted_set" is 10.5.
            >>> await client.zscore("my_sorted_set", "non_existing_member")
                None
        """
        return cast(
            Optional[float],
            await self._execute_command(RequestType.ZScore, [key, member]),
        )

    async def zmscore(
        self,
        key: TEncodable,
        members: List[TEncodable],
    ) -> List[Optional[float]]:
        """
        Returns the scores associated with the specified `members` in the sorted set stored at `key`.

        See https://valkey.io/commands/zmscore for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            members (List[TEncodable]): A list of members in the sorted set.

        Returns:
            List[Optional[float]]: A list of scores corresponding to `members`.
                If a member does not exist in the sorted set, the corresponding value in the list will be None.

        Examples:
            >>> await client.zmscore("my_sorted_set", ["one", "non_existent_member", "three"])
                [1.0, None, 3.0]
        """
        return cast(
            List[Optional[float]],
            await self._execute_command(RequestType.ZMScore, [key] + members),
        )

    async def zdiff(self, keys: List[TEncodable]) -> List[bytes]:
        """
        Returns the difference between the first sorted set and all the successive sorted sets.
        To get the elements with their scores, see `zdiff_withscores`.

        When in Cluster mode, all keys must map to the same hash slot.

        See https://valkey.io/commands/zdiff for more details.

        Args:
            keys (List[TEncodable]): The keys of the sorted sets.

        Returns:
            List[bytes]: A list of elements representing the difference between the sorted sets.
                If the first key does not exist, it is treated as an empty sorted set, and the command returns an
                empty list.

        Examples:
            >>> await client.zadd("sorted_set1", {"element1":1.0, "element2": 2.0, "element3": 3.0})
            >>> await client.zadd("sorted_set2", {"element2": 2.0})
            >>> await client.zadd("sorted_set3", {"element3": 3.0})
            >>> await client.zdiff("sorted_set1", "sorted_set2", "sorted_set3")
                [b"element1"]  # Indicates that "element1" is in "sorted_set1" but not "sorted_set2" or "sorted_set3".
        """
        args: List[TEncodable] = [str(len(keys))]
        args.extend(keys)
        return cast(
            List[bytes],
            await self._execute_command(RequestType.ZDiff, args),
        )

    async def zdiff_withscores(self, keys: List[TEncodable]) -> Mapping[bytes, float]:
        """
        Returns the difference between the first sorted set and all the successive sorted sets, with the associated scores.
        When in Cluster mode, all keys must map to the same hash slot.

        See https://valkey.io/commands/zdiff for more details.

        Args:
            keys (List[TEncodable]): The keys of the sorted sets.

        Returns:
            Mapping[bytes, float]: A mapping of elements and their scores representing the difference between the sorted
                sets.
                If the first `key` does not exist, it is treated as an empty sorted set, and the command returns an
                empty list.

        Examples:
            >>> await client.zadd("sorted_set1", {"element1":1.0, "element2": 2.0, "element3": 3.0})
            >>> await client.zadd("sorted_set2", {"element2": 2.0})
            >>> await client.zadd("sorted_set3", {"element3": 3.0})
            >>> await client.zdiff_withscores("sorted_set1", "sorted_set2", "sorted_set3")
                {b"element1": 1.0}  # Indicates that "element1" is in "sorted_set1" but not "sorted_set2" or "sorted_set3".
        """
        return cast(
            Mapping[bytes, float],
            await self._execute_command(
                RequestType.ZDiff, [str(len(keys))] + keys + ["WITHSCORES"]
            ),
        )

    async def zdiffstore(self, destination: TEncodable, keys: List[TEncodable]) -> int:
        """
        Calculates the difference between the first sorted set and all the successive sorted sets at `keys` and stores
        the difference as a sorted set to `destination`, overwriting it if it already exists. Non-existent keys are
        treated as empty sets.
        See https://valkey.io/commands/zdiffstore for more details.

        Note:
            When in Cluster mode, all keys in `keys` and `destination` must map to the same hash slot.

        Args:
            destination (TEncodable): The key for the resulting sorted set.
            keys (List[TEncodable]): The keys of the sorted sets to compare.

        Returns:
            int: The number of members in the resulting sorted set stored at `destination`.

        Examples:
            >>> await client.zadd("key1", {"member1": 10.5, "member2": 8.2})
                2  # Indicates that two elements have been added to the sorted set at "key1".
            >>> await client.zadd("key2", {"member1": 10.5})
                1  # Indicates that one element has been added to the sorted set at "key2".
            >>> await client.zdiffstore("my_sorted_set", ["key1", "key2"])
                1  # One member exists in "key1" but not "key2", and this member was stored in "my_sorted_set".
            >>> await client.zrange("my_sorted_set", RangeByIndex(0, -1))
                ['member2']  # "member2" is now stored in "my_sorted_set"
        """
        return cast(
            int,
            await self._execute_command(
                RequestType.ZDiffStore, [destination, str(len(keys))] + keys
            ),
        )

    async def zinter(
        self,
        keys: List[TEncodable],
    ) -> List[bytes]:
        """
        Computes the intersection of sorted sets given by the specified `keys` and returns a list of intersecting elements.
        To get the scores as well, see `zinter_withscores`.
        To store the result in a key as a sorted set, see `zinterstore`.

        When in cluster mode, all keys in `keys` must map to the same hash slot.

        See https://valkey.io/commands/zinter/ for more details.

        Args:
            keys (List[TEncodable]): The keys of the sorted sets.

        Returns:
            List[bytes]: The resulting array of intersecting elements.

        Examples:
            >>> await client.zadd("key1", {"member1": 10.5, "member2": 8.2})
            >>> await client.zadd("key2", {"member1": 9.5})
            >>> await client.zinter(["key1", "key2"])
                [b'member1']
        """
        args: List[TEncodable] = [str(len(keys))]
        args.extend(keys)
        return cast(
            List[bytes],
            await self._execute_command(RequestType.ZInter, args),
        )

    async def zinter_withscores(
        self,
        keys: Union[List[TEncodable], List[Tuple[TEncodable, float]]],
        aggregation_type: Optional[AggregationType] = None,
    ) -> Mapping[bytes, float]:
        """
        Computes the intersection of sorted sets given by the specified `keys` and returns a sorted set of intersecting elements with scores.
        To get the elements only, see `zinter`.
        To store the result in a key as a sorted set, see `zinterstore`.

        When in cluster mode, all keys in `keys` must map to the same hash slot.

        See https://valkey.io/commands/zinter/ for more details.

        Args:
            keys (Union[List[TEncodable], List[Tuple[TEncodable, float]]]): The keys of the sorted sets with possible formats:
                List[TEncodable] - for keys only.
                List[Tuple[TEncodable, float]] - for weighted keys with score multipliers.
            aggregation_type (Optional[AggregationType]): Specifies the aggregation strategy to apply
                when combining the scores of elements. See `AggregationType`.

        Returns:
            Mapping[bytes, float]: The resulting sorted set with scores.

        Examples:
            >>> await client.zadd("key1", {"member1": 10.5, "member2": 8.2})
            >>> await client.zadd("key2", {"member1": 9.5})
            >>> await client.zinter_withscores(["key1", "key2"])
                {b'member1': 20}  # "member1" with score of 20 is the result
            >>> await client.zinter_withscores(["key1", "key2"], AggregationType.MAX)
                {b'member1': 10.5}  # "member1" with score of 10.5 is the result.
        """
        args = _create_zinter_zunion_cmd_args(keys, aggregation_type)
        args.append("WITHSCORES")
        return cast(
            Mapping[bytes, float],
            await self._execute_command(RequestType.ZInter, args),
        )

    async def zinterstore(
        self,
        destination: TEncodable,
        keys: Union[List[TEncodable], List[Tuple[TEncodable, float]]],
        aggregation_type: Optional[AggregationType] = None,
    ) -> int:
        """
        Computes the intersection of sorted sets given by the specified `keys` and stores the result in `destination`.
        If `destination` already exists, it is overwritten. Otherwise, a new sorted set will be created.
        To get the result directly, see `zinter_withscores`.

        When in cluster mode, `destination` and all keys in `keys` must map to the same hash slot.

        See https://valkey.io/commands/zinterstore/ for more details.

        Args:
            destination (TEncodable): The key of the destination sorted set.
            keys (Union[List[TEncodable], List[Tuple[TEncodable, float]]]): The keys of the sorted sets with possible formats:
                List[TEncodable] - for keys only.
                List[Tuple[TEncodable, float]] - for weighted keys with score multipliers.
            aggregation_type (Optional[AggregationType]): Specifies the aggregation strategy to apply
                when combining the scores of elements. See `AggregationType`.

        Returns:
            int: The number of elements in the resulting sorted set stored at `destination`.

        Examples:
            >>> await client.zadd("key1", {"member1": 10.5, "member2": 8.2})
            >>> await client.zadd("key2", {"member1": 9.5})
            >>> await client.zinterstore("my_sorted_set", ["key1", "key2"])
                1 # Indicates that the sorted set "my_sorted_set" contains one element.
            >>> await client.zrange_withscores("my_sorted_set", RangeByIndex(0, -1))
                {b'member1': 20}  # "member1" is now stored in "my_sorted_set" with score of 20.
            >>> await client.zinterstore("my_sorted_set", ["key1", "key2"], AggregationType.MAX)
                1 # Indicates that the sorted set "my_sorted_set" contains one element, and its score is the maximum score between the sets.
            >>> await client.zrange_withscores("my_sorted_set", RangeByIndex(0, -1))
                {b'member1': 10.5}  # "member1" is now stored in "my_sorted_set" with score of 10.5.
        """
        args = _create_zinter_zunion_cmd_args(keys, aggregation_type, destination)
        return cast(
            int,
            await self._execute_command(RequestType.ZInterStore, args),
        )

    async def zunion(
        self,
        keys: List[TEncodable],
    ) -> List[bytes]:
        """
        Computes the union of sorted sets given by the specified `keys` and returns a list of union elements.
        To get the scores as well, see `zunion_withscores`.
        To store the result in a key as a sorted set, see `zunionstore`.

        When in cluster mode, all keys in `keys` must map to the same hash slot.

        See https://valkey.io/commands/zunion/ for more details.

        Args:
            keys (List[TEncodable]): The keys of the sorted sets.

        Returns:
            List[bytes]: The resulting array of union elements.

        Examples:
            >>> await client.zadd("key1", {"member1": 10.5, "member2": 8.2})
            >>> await client.zadd("key2", {"member1": 9.5})
            >>> await client.zunion(["key1", "key2"])
                [b'member1', b'member2']
        """
        args: List[TEncodable] = [str(len(keys))]
        args.extend(keys)
        return cast(
            List[bytes],
            await self._execute_command(RequestType.ZUnion, args),
        )

    async def zunion_withscores(
        self,
        keys: Union[List[TEncodable], List[Tuple[TEncodable, float]]],
        aggregation_type: Optional[AggregationType] = None,
    ) -> Mapping[bytes, float]:
        """
        Computes the union of sorted sets given by the specified `keys` and returns a sorted set of union elements with scores.
        To get the elements only, see `zunion`.
        To store the result in a key as a sorted set, see `zunionstore`.

        When in cluster mode, all keys in `keys` must map to the same hash slot.

        See https://valkey.io/commands/zunion/ for more details.

        Args:
            keys (Union[List[TEncodable], List[Tuple[TEncodable, float]]]): The keys of the sorted sets with possible formats:
                List[TEncodable] - for keys only.
                List[Tuple[TEncodable, float]] - for weighted keys with score multipliers.
            aggregation_type (Optional[AggregationType]): Specifies the aggregation strategy to apply
                when combining the scores of elements. See `AggregationType`.

        Returns:
            Mapping[bytes, float]: The resulting sorted set with scores.

        Examples:
            >>> await client.zadd("key1", {"member1": 10.5, "member2": 8.2})
            >>> await client.zadd("key2", {"member1": 9.5})
            >>> await client.zunion_withscores(["key1", "key2"])
                {b'member1': 20, b'member2': 8.2}
            >>> await client.zunion_withscores(["key1", "key2"], AggregationType.MAX)
                {b'member1': 10.5, b'member2': 8.2}
        """
        args = _create_zinter_zunion_cmd_args(keys, aggregation_type)
        args.append("WITHSCORES")
        return cast(
            Mapping[bytes, float],
            await self._execute_command(RequestType.ZUnion, args),
        )

    async def zunionstore(
        self,
        destination: TEncodable,
        keys: Union[List[TEncodable], List[Tuple[TEncodable, float]]],
        aggregation_type: Optional[AggregationType] = None,
    ) -> int:
        """
        Computes the union of sorted sets given by the specified `keys` and stores the result in `destination`.
        If `destination` already exists, it is overwritten. Otherwise, a new sorted set will be created.
        To get the result directly, see `zunion_withscores`.

        When in cluster mode, `destination` and all keys in `keys` must map to the same hash slot.

        See https://valkey.io/commands/zunionstore/ for more details.

        Args:
            destination (TEncodable): The key of the destination sorted set.
            keys (Union[List[TEncodable], List[Tuple[TEncodable, float]]]): The keys of the sorted sets with possible formats:
                List[TEncodable] - for keys only.
                List[Tuple[TEncodable, float]] - for weighted keys with score multipliers.
            aggregation_type (Optional[AggregationType]): Specifies the aggregation strategy to apply
                when combining the scores of elements. See `AggregationType`.

        Returns:
            int: The number of elements in the resulting sorted set stored at `destination`.

        Examples:
            >>> await client.zadd("key1", {"member1": 10.5, "member2": 8.2})
            >>> await client.zadd("key2", {"member1": 9.5})
            >>> await client.zunionstore("my_sorted_set", ["key1", "key2"])
                2 # Indicates that the sorted set "my_sorted_set" contains two elements.
            >>> await client.zrange_withscores("my_sorted_set", RangeByIndex(0, -1))
                {b'member1': 20, b'member2': 8.2}
            >>> await client.zunionstore("my_sorted_set", ["key1", "key2"], AggregationType.MAX)
                2 # Indicates that the sorted set "my_sorted_set" contains two elements, and each score is the maximum score between the sets.
            >>> await client.zrange_withscores("my_sorted_set", RangeByIndex(0, -1))
                {b'member1': 10.5, b'member2': 8.2}
        """
        args = _create_zinter_zunion_cmd_args(keys, aggregation_type, destination)
        return cast(
            int,
            await self._execute_command(RequestType.ZUnionStore, args),
        )

    async def zrandmember(self, key: TEncodable) -> Optional[bytes]:
        """
        Returns a random member from the sorted set stored at 'key'.

        See https://valkey.io/commands/zrandmember for more details.

        Args:
            key (TEncodable): The key of the sorted set.

        Returns:
            Optional[bytes]: A random member from the sorted set.
                If the sorted set does not exist or is empty, the response will be None.

        Examples:
            >>> await client.zadd("my_sorted_set", {"member1": 1.0, "member2": 2.0})
            >>> await client.zrandmember("my_sorted_set")
                b"member1"  # "member1" is a random member of "my_sorted_set".
            >>> await client.zrandmember("non_existing_sorted_set")
                None  # "non_existing_sorted_set" is not an existing key, so None was returned.
        """
        args: List[TEncodable] = [key]
        return cast(
            Optional[bytes],
            await self._execute_command(RequestType.ZRandMember, [key]),
        )

    async def zrandmember_count(self, key: TEncodable, count: int) -> List[bytes]:
        """
        Retrieves up to the absolute value of `count` random members from the sorted set stored at 'key'.

        See https://valkey.io/commands/zrandmember for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            count (int): The number of members to return.
                If `count` is positive, returns unique members.
                If `count` is negative, allows for duplicates members.

        Returns:
            List[bytes]: A list of members from the sorted set.
                If the sorted set does not exist or is empty, the response will be an empty list.

        Examples:
            >>> await client.zadd("my_sorted_set", {"member1": 1.0, "member2": 2.0})
            >>> await client.zrandmember("my_sorted_set", -3)
                [b"member1", b"member1", b"member2"]  # "member1" and "member2" are random members of "my_sorted_set".
            >>> await client.zrandmember("non_existing_sorted_set", 3)
                []  # "non_existing_sorted_set" is not an existing key, so an empty list was returned.
        """
        args: List[TEncodable] = [key, str(count)]
        return cast(
            List[bytes],
            await self._execute_command(RequestType.ZRandMember, args),
        )

    async def zrandmember_withscores(
        self, key: TEncodable, count: int
    ) -> List[List[Union[bytes, float]]]:
        """
        Retrieves up to the absolute value of `count` random members along with their scores from the sorted set
        stored at 'key'.

        See https://valkey.io/commands/zrandmember for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            count (int): The number of members to return.
                If `count` is positive, returns unique members.
                If `count` is negative, allows for duplicates members.

        Returns:
            List[List[Union[bytes, float]]]: A list of `[member, score]` lists, where `member` is a random member from
                the sorted set and `score` is the associated score.
                If the sorted set does not exist or is empty, the response will be an empty list.

        Examples:
            >>> await client.zadd("my_sorted_set", {"member1": 1.0, "member2": 2.0})
            >>> await client.zrandmember_withscores("my_sorted_set", -3)
                [[b"member1", 1.0], [b"member1", 1.0], [b"member2", 2.0]]  # "member1" and "member2" are random members of "my_sorted_set", and have scores of 1.0 and 2.0, respectively.
            >>> await client.zrandmember_withscores("non_existing_sorted_set", 3)
                []  # "non_existing_sorted_set" is not an existing key, so an empty list was returned.
        """
        args: List[TEncodable] = [key, str(count), "WITHSCORES"]
        return cast(
            List[List[Union[bytes, float]]],
            await self._execute_command(RequestType.ZRandMember, args),
        )

    async def zmpop(
        self,
        keys: List[TEncodable],
        filter: ScoreFilter,
        count: Optional[int] = None,
    ) -> Optional[List[Union[bytes, Mapping[bytes, float]]]]:
        """
        Pops a member-score pair from the first non-empty sorted set, with the given keys being checked in the order
        that they are given.

        The optional `count` argument can be used to specify the number of elements to pop, and is
        set to 1 by default.

        The number of popped elements is the minimum from the sorted set's cardinality and `count`.

        See https://valkey.io/commands/zmpop for more details.

        Note:
            When in cluster mode, all `keys` must map to the same hash slot.

        Args:
            keys (List[TEncodable]): The keys of the sorted sets.
            filter (ScoreFilter): The element pop criteria - either ScoreFilter.MIN or ScoreFilter.MAX to pop
                members with the lowest/highest scores accordingly.
            count (Optional[int]): The number of elements to pop.

        Returns:
            Optional[List[Union[bytes, Mapping[bytes, float]]]]: A two-element list containing the key name of the set from
                which elements were popped, and a member-score mapping of the popped elements. If no members could be
                popped, returns None.

        Examples:
            >>> await client.zadd("zSet1", {"one": 1.0, "two": 2.0, "three": 3.0})
            >>> await client.zadd("zSet2", {"four": 4.0})
            >>> await client.zmpop(["zSet1", "zSet2"], ScoreFilter.MAX, 2)
                [b'zSet1', {b'three': 3.0, b'two': 2.0}]  # "three" with score 3.0 and "two" with score 2.0 were popped from "zSet1".

        Since: Valkey version 7.0.0.
        """
        args: List[TEncodable] = [str(len(keys))] + keys + [filter.value]
        if count is not None:
            args.extend(["COUNT", str(count)])

        return cast(
            Optional[List[Union[bytes, Mapping[bytes, float]]]],
            await self._execute_command(RequestType.ZMPop, args),
        )

    async def bzmpop(
        self,
        keys: List[TEncodable],
        modifier: ScoreFilter,
        timeout: float,
        count: Optional[int] = None,
    ) -> Optional[List[Union[bytes, Mapping[bytes, float]]]]:
        """
        Pops a member-score pair from the first non-empty sorted set, with the given keys being checked in the order
        that they are given. Blocks the connection when there are no members to pop from any of the given sorted sets.

        The optional `count` argument can be used to specify the number of elements to pop, and is set to 1 by default.

        The number of popped elements is the minimum from the sorted set's cardinality and `count`.

        `BZMPOP` is the blocking variant of `ZMPOP`.

        See https://valkey.io/commands/bzmpop for more details.

        Notes:
            1. When in cluster mode, all `keys` must map to the same hash slot.
            2. `BZMPOP` is a client blocking command, see https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands for more details and best practices.

        Args:
            keys (List[TEncodable]): The keys of the sorted sets.
            modifier (ScoreFilter): The element pop criteria - either ScoreFilter.MIN or ScoreFilter.MAX to pop
                members with the lowest/highest scores accordingly.
            timeout (float): The number of seconds to wait for a blocking operation to complete. A value of 0 will
                block indefinitely.
            count (Optional[int]): The number of elements to pop.

        Returns:
            Optional[List[Union[bytes, Mapping[bytes, float]]]]: A two-element list containing the key name of the set from
                which elements were popped, and a member-score mapping of the popped elements. If no members could be
                popped and the timeout expired, returns None.

        Examples:
            >>> await client.zadd("zSet1", {"one": 1.0, "two": 2.0, "three": 3.0})
            >>> await client.zadd("zSet2", {"four": 4.0})
            >>> await client.bzmpop(["zSet1", "zSet2"], ScoreFilter.MAX, 0.5, 2)
                [b'zSet1', {b'three': 3.0, b'two': 2.0}]  # "three" with score 3.0 and "two" with score 2.0 were popped from "zSet1".

        Since: Valkey version 7.0.0.
        """
        args = [str(timeout), str(len(keys))] + keys + [modifier.value]
        if count is not None:
            args = args + ["COUNT", str(count)]

        return cast(
            Optional[List[Union[bytes, Mapping[bytes, float]]]],
            await self._execute_command(RequestType.BZMPop, args),
        )

    async def zintercard(
        self, keys: List[TEncodable], limit: Optional[int] = None
    ) -> int:
        """
        Returns the cardinality of the intersection of the sorted sets specified by `keys`. When provided with the
        optional `limit` argument, if the intersection cardinality reaches `limit` partway through the computation, the
        algorithm will exit early and yield `limit` as the cardinality.

        See https://valkey.io/commands/zintercard for more details.

        Args:
            keys (List[TEncodable]): The keys of the sorted sets to intersect.
            limit (Optional[int]): An optional argument that can be used to specify a maximum number for the
                intersection cardinality. If limit is not supplied, or if it is set to 0, there will be no limit.

        Note:
            When in cluster mode, all `keys` must map to the same hash slot.

        Returns:
            int: The cardinality of the intersection of the given sorted sets, or the `limit` if reached.

        Examples:
            >>> await client.zadd("key1", {"member1": 10.5, "member2": 8.2, "member3": 9.6})
            >>> await client.zadd("key2", {"member1": 10.5, "member2": 3.5})
            >>> await client.zintercard(["key1", "key2"])
                2  # Indicates that the intersection of the sorted sets at "key1" and "key2" has a cardinality of 2.
            >>> await client.zintercard(["key1", "key2"], 1)
                1  # A `limit` of 1 was provided, so the intersection computation exits early and yields the `limit` value of 1.

        Since: Valkey version 7.0.0.
        """
        args = [str(len(keys))] + keys
        if limit is not None:
            args.extend(["LIMIT", str(limit)])

        return cast(
            int,
            await self._execute_command(RequestType.ZInterCard, args),
        )

    async def invoke_script(
        self,
        script: Script,
        keys: Optional[List[TEncodable]] = None,
        args: Optional[List[TEncodable]] = None,
    ) -> TResult:
        """
        Invokes a Lua script with its keys and arguments.
        This method simplifies the process of invoking scripts on a the server by using an object that represents a Lua script.
        The script loading, argument preparation, and execution will all be handled internally.
        If the script has not already been loaded, it will be loaded automatically using the `SCRIPT LOAD` command.
        After that, it will be invoked using the `EVALSHA` command.

        See https://valkey.io/commands/script-load/ and https://valkey.io/commands/evalsha/ for more details.

        Args:
            script (Script): The Lua script to execute.
            keys (Optional[List[TEncodable]]): The keys that are used in the script.
            args (Optional[List[TEncodable]]): The arguments for the script.

        Returns:
            TResult: a value that depends on the script that was executed.

        Examples:
            >>> lua_script = Script("return { KEYS[1], ARGV[1] }")
            >>> await invoke_script(lua_script, keys=["foo"], args=["bar"] );
                [b"foo", b"bar"]
        """
        return await self._execute_script(script.get_hash(), keys, args)

    async def script_exists(self, sha1s: List[TEncodable]) -> List[bool]:
        """
        Check existence of scripts in the script cache by their SHA1 digest.

        See https://valkey.io/commands/script-exists for more details.

        Args:
            sha1s (List[TEncodable]): List of SHA1 digests of the scripts to check.

        Returns:
            List[bool]: A list of boolean values indicating the existence of each script.

        Examples:
            >>> await client.script_exists(["sha1_digest1", "sha1_digest2"])
                [True, False]
        """
        return cast(
            List[bool], await self._execute_command(RequestType.ScriptExists, sha1s)
        )

    async def script_flush(self, mode: Optional[FlushMode] = None) -> TOK:
        """
        Flush the Lua scripts cache.

        See https://valkey.io/commands/script-flush for more details.

        Args:
            mode (Optional[FlushMode]): The flushing mode, could be either `SYNC` or `ASYNC`.

        Returns:
            TOK: A simple `OK` response.

        Examples:
            >>> await client.script_flush()
                "OK"

            >>> await client.script_flush(FlushMode.ASYNC)
                "OK"
        """

        return cast(
            TOK,
            await self._execute_command(
                RequestType.ScriptFlush, [mode.value] if mode else []
            ),
        )

    async def script_kill(self) -> TOK:
        """
        Kill the currently executing Lua script, assuming no write operation was yet performed by the script.

        See https://valkey.io/commands/script-kill for more details.

        Returns:
            TOK: A simple `OK` response.

        Examples:
            >>> await client.script_kill()
                "OK"
        """
        return cast(TOK, await self._execute_command(RequestType.ScriptKill, []))

    async def pfadd(self, key: TEncodable, elements: List[TEncodable]) -> int:
        """
        Adds all elements to the HyperLogLog data structure stored at the specified `key`.
        Creates a new structure if the `key` does not exist.
        When no elements are provided, and `key` exists and is a HyperLogLog, then no operation is performed.

        See https://valkey.io/commands/pfadd/ for more details.

        Args:
            key (TEncodable): The key of the HyperLogLog data structure to add elements into.
            elements (List[TEncodable]): A list of members to add to the HyperLogLog stored at `key`.

        Returns:
            int: If the HyperLogLog is newly created, or if the HyperLogLog approximated cardinality is
            altered, then returns 1. Otherwise, returns 0.

        Examples:
            >>> await client.pfadd("hll_1", ["a", "b", "c" ])
                1 # A data structure was created or modified
            >>> await client.pfadd("hll_2", [])
                1 # A new empty data structure was created
        """
        return cast(
            int,
            await self._execute_command(RequestType.PfAdd, [key] + elements),
        )

    async def pfcount(self, keys: List[TEncodable]) -> int:
        """
        Estimates the cardinality of the data stored in a HyperLogLog structure for a single key or
        calculates the combined cardinality of multiple keys by merging their HyperLogLogs temporarily.

        See https://valkey.io/commands/pfcount for more details.

        Note:
            When in Cluster mode, all `keys` must map to the same hash slot.

        Args:
            keys (List[TEncodable]): The keys of the HyperLogLog data structures to be analyzed.

        Returns:
            int: The approximated cardinality of given HyperLogLog data structures.
                The cardinality of a key that does not exist is 0.

        Examples:
            >>> await client.pfcount(["hll_1", "hll_2"])
                4  # The approximated cardinality of the union of "hll_1" and "hll_2" is 4.
        """
        return cast(
            int,
            await self._execute_command(RequestType.PfCount, keys),
        )

    async def pfmerge(
        self, destination: TEncodable, source_keys: List[TEncodable]
    ) -> TOK:
        """
        Merges multiple HyperLogLog values into a unique value. If the destination variable exists, it is treated as one
        of the source HyperLogLog data sets, otherwise a new HyperLogLog is created.

        See https://valkey.io/commands/pfmerge for more details.

        Note:
            When in Cluster mode, all keys in `source_keys` and `destination` must map to the same hash slot.

        Args:
            destination (TEncodable): The key of the destination HyperLogLog where the merged data sets will be stored.
            source_keys (List[TEncodable]): The keys of the HyperLogLog structures to be merged.

        Returns:
            OK: A simple OK response.

        Examples:
            >>> await client.pfadd("hll1", ["a", "b"])
            >>> await client.pfadd("hll2", ["b", "c"])
            >>> await client.pfmerge("new_hll", ["hll1", "hll2"])
                OK  # The value of "hll1" merged with "hll2" was stored in "new_hll".
            >>> await client.pfcount(["new_hll"])
                3  # The approximated cardinality of "new_hll" is 3.
        """
        return cast(
            TOK,
            await self._execute_command(
                RequestType.PfMerge, [destination] + source_keys
            ),
        )

    async def bitcount(
        self, key: TEncodable, options: Optional[OffsetOptions] = None
    ) -> int:
        """
        Counts the number of set bits (population counting) in the string stored at `key`. The `options` argument can
        optionally be provided to count the number of bits in a specific string interval.

        See https://valkey.io/commands/bitcount for more details.

        Args:
            key (TEncodable): The key for the string to count the set bits of.
            options (Optional[OffsetOptions]): The offset options.

        Returns:
            int: If `options` is provided, returns the number of set bits in the string interval specified by `options`.
                If `options` is not provided, returns the number of set bits in the string stored at `key`.
                Otherwise, if `key` is missing, returns `0` as it is treated as an empty string.

        Examples:
            >>> await client.bitcount("my_key1")
                2  # The string stored at "my_key1" contains 2 set bits.
            >>> await client.bitcount("my_key2", OffsetOptions(1, 3))
                2  # The second to fourth bytes of the string stored at "my_key2" contain 2 set bits.
            >>> await client.bitcount("my_key3", OffsetOptions(1, 1, BitmapIndexType.BIT))
                1  # Indicates that the second bit of the string stored at "my_key3" is set.
            >>> await client.bitcount("my_key3", OffsetOptions(-1, -1, BitmapIndexType.BIT))
                1  # Indicates that the last bit of the string stored at "my_key3" is set.
        """
        args: List[TEncodable] = [key]
        if options is not None:
            args.extend(options.to_args())

        return cast(
            int,
            await self._execute_command(RequestType.BitCount, args),
        )

    async def setbit(self, key: TEncodable, offset: int, value: int) -> int:
        """
        Sets or clears the bit at `offset` in the string value stored at `key`. The `offset` is a zero-based index,
        with `0` being the first element of the list, `1` being the next element, and so on. The `offset` must be less
        than `2^32` and greater than or equal to `0`. If a key is non-existent then the bit at `offset` is set to
        `value` and the preceding bits are set to `0`.

        See https://valkey.io/commands/setbit for more details.

        Args:
            key (TEncodable): The key of the string.
            offset (int): The index of the bit to be set.
            value (int): The bit value to set at `offset`. The value must be `0` or `1`.

        Returns:
            int: The bit value that was previously stored at `offset`.

        Examples:
            >>> await client.setbit("string_key", 1, 1)
                0  # The second bit value was 0 before setting to 1.
        """
        return cast(
            int,
            await self._execute_command(
                RequestType.SetBit, [key, str(offset), str(value)]
            ),
        )

    async def getbit(self, key: TEncodable, offset: int) -> int:
        """
        Returns the bit value at `offset` in the string value stored at `key`.
        `offset` should be greater than or equal to zero.

        See https://valkey.io/commands/getbit for more details.

        Args:
            key (TEncodable): The key of the string.
            offset (int): The index of the bit to return.

        Returns:
            int: The bit at the given `offset` of the string. Returns `0` if the key is empty or if the `offset` exceeds
                the length of the string.

        Examples:
            >>> await client.getbit("my_key", 1)
                1  # Indicates that the second bit of the string stored at "my_key" is set to 1.
        """
        return cast(
            int,
            await self._execute_command(RequestType.GetBit, [key, str(offset)]),
        )

    async def bitpos(
        self, key: TEncodable, bit: int, start: Optional[int] = None
    ) -> int:
        """
        Returns the position of the first bit matching the given `bit` value. The optional starting offset
        `start` is a zero-based index, with `0` being the first byte of the list, `1` being the next byte and so on.
        The offset can also be a negative number indicating an offset starting at the end of the list, with `-1` being
        the last byte of the list, `-2` being the penultimate, and so on.

        See https://valkey.io/commands/bitpos for more details.

        Args:
            key (TEncodable): The key of the string.
            bit (int): The bit value to match. Must be `0` or `1`.
            start (Optional[int]): The starting offset.

        Returns:
            int: The position of the first occurrence of `bit` in the binary value of the string held at `key`.
                If `start` was provided, the search begins at the offset indicated by `start`.

        Examples:
            >>> await client.set("key1", "A1")  # "A1" has binary value 01000001 00110001
            >>> await client.bitpos("key1", 1)
                1  # The first occurrence of bit value 1 in the string stored at "key1" is at the second position.
            >>> await client.bitpos("key1", 1, -1)
                10  # The first occurrence of bit value 1, starting at the last byte in the string stored at "key1", is at the eleventh position.
        """
        args = [key, str(bit)] if start is None else [key, str(bit), str(start)]
        return cast(
            int,
            await self._execute_command(RequestType.BitPos, args),
        )

    async def bitpos_interval(
        self,
        key: TEncodable,
        bit: int,
        start: int,
        end: int,
        index_type: Optional[BitmapIndexType] = None,
    ) -> int:
        """
        Returns the position of the first bit matching the given `bit` value. The offsets are zero-based indexes, with
        `0` being the first element of the list, `1` being the next, and so on. These offsets can also be negative
        numbers indicating offsets starting at the end of the list, with `-1` being the last element of the list, `-2`
        being the penultimate, and so on.

        If you are using Valkey 7.0.0 or above, the optional `index_type` can also be provided to specify whether the
        `start` and `end` offsets specify BIT or BYTE offsets. If `index_type` is not provided, BYTE offsets
        are assumed. If BIT is specified, `start=0` and `end=2` means to look at the first three bits. If BYTE is
        specified, `start=0` and `end=2` means to look at the first three bytes.

        See https://valkey.io/commands/bitpos for more details.

        Args:
            key (TEncodable): The key of the string.
            bit (int): The bit value to match. Must be `0` or `1`.
            start (int): The starting offset.
            end (int): The ending offset.
            index_type (Optional[BitmapIndexType]): The index offset type. This option can only be specified if you are
                using Valkey version 7.0.0 or above. Could be either `BitmapIndexType.BYTE` or `BitmapIndexType.BIT`.
                If no index type is provided, the indexes will be assumed to be byte indexes.

        Returns:
            int: The position of the first occurrence from the `start` to the `end` offsets of the `bit` in the binary
                value of the string held at `key`.

        Examples:
            >>> await client.set("key1", "A12")  # "A12" has binary value 01000001 00110001 00110010
            >>> await client.bitpos_interval("key1", 1, 1, -1)
                10  # The first occurrence of bit value 1 in the second byte to the last byte of the string stored at "key1" is at the eleventh position.
            >>> await client.bitpos_interval("key1", 1, 2, 9, BitmapIndexType.BIT)
                7  # The first occurrence of bit value 1 in the third to tenth bits of the string stored at "key1" is at the eighth position.
        """
        if index_type is not None:
            args = [key, str(bit), str(start), str(end), index_type.value]
        else:
            args = [key, str(bit), str(start), str(end)]

        return cast(
            int,
            await self._execute_command(RequestType.BitPos, args),
        )

    async def bitop(
        self,
        operation: BitwiseOperation,
        destination: TEncodable,
        keys: List[TEncodable],
    ) -> int:
        """
        Perform a bitwise operation between multiple keys (containing string values) and store the result in the
        `destination`.

        See https://valkey.io/commands/bitop for more details.

        Note:
            When in cluster mode, `destination` and all `keys` must map to the same hash slot.

        Args:
            operation (BitwiseOperation): The bitwise operation to perform.
            destination (TEncodable): The key that will store the resulting string.
            keys (List[TEncodable]): The list of keys to perform the bitwise operation on.

        Returns:
            int: The size of the string stored in `destination`.

        Examples:
            >>> await client.set("key1", "A")  # "A" has binary value 01000001
            >>> await client.set("key1", "B")  # "B" has binary value 01000010
            >>> await client.bitop(BitwiseOperation.AND, "destination", ["key1", "key2"])
                1  # The size of the resulting string stored in "destination" is 1
            >>> await client.get("destination")
                "@"  # "@" has binary value 01000000
        """
        return cast(
            int,
            await self._execute_command(
                RequestType.BitOp, [operation.value, destination] + keys
            ),
        )

    async def bitfield(
        self, key: TEncodable, subcommands: List[BitFieldSubCommands]
    ) -> List[Optional[int]]:
        """
        Reads or modifies the array of bits representing the string that is held at `key` based on the specified
        `subcommands`.

        See https://valkey.io/commands/bitfield for more details.

        Args:
            key (TEncodable): The key of the string.
            subcommands (List[BitFieldSubCommands]): The subcommands to be performed on the binary value of the string
                at `key`, which could be any of the following:
                    - `BitFieldGet`
                    - `BitFieldSet`
                    - `BitFieldIncrBy`
                    - `BitFieldOverflow`

        Returns:
            List[Optional[int]]: An array of results from the executed subcommands:
                - `BitFieldGet` returns the value in `BitOffset` or `BitOffsetMultiplier`.
                - `BitFieldSet` returns the old value in `BitOffset` or `BitOffsetMultiplier`.
                - `BitFieldIncrBy` returns the new value in `BitOffset` or `BitOffsetMultiplier`.
                - `BitFieldOverflow` determines the behavior of the "SET" and "INCRBY" subcommands when an overflow or
                  underflow occurs. "OVERFLOW" does not return a value and does not contribute a value to the list
                  response.

        Examples:
            >>> await client.set("my_key", "A")  # "A" has binary value 01000001
            >>> await client.bitfield("my_key", [BitFieldSet(UnsignedEncoding(2), BitOffset(1), 3), BitFieldGet(UnsignedEncoding(2), BitOffset(1))])
                [2, 3]  # The old value at offset 1 with an unsigned encoding of 2 was 2. The new value at offset 1 with an unsigned encoding of 2 is 3.
        """
        args = [key] + _create_bitfield_args(subcommands)
        return cast(
            List[Optional[int]],
            await self._execute_command(RequestType.BitField, args),
        )

    async def bitfield_read_only(
        self, key: TEncodable, subcommands: List[BitFieldGet]
    ) -> List[int]:
        """
        Reads the array of bits representing the string that is held at `key` based on the specified `subcommands`.

        See https://valkey.io/commands/bitfield_ro for more details.

        Args:
            key (TEncodable): The key of the string.
            subcommands (List[BitFieldGet]): The "GET" subcommands to be performed.

        Returns:
            List[int]: An array of results from the "GET" subcommands.

        Examples:
            >>> await client.set("my_key", "A")  # "A" has binary value 01000001
            >>> await client.bitfield_read_only("my_key", [BitFieldGet(UnsignedEncoding(2), Offset(1))])
                [2]  # The value at offset 1 with an unsigned encoding of 2 is 2.

        Since: Valkey version 6.0.0.
        """
        args = [key] + _create_bitfield_read_only_args(subcommands)
        return cast(
            List[int],
            await self._execute_command(RequestType.BitFieldReadOnly, args),
        )

    async def object_encoding(self, key: TEncodable) -> Optional[bytes]:
        """
        Returns the internal encoding for the Valkey object stored at `key`.

        See https://valkey.io/commands/object-encoding for more details.

        Args:
            key (TEncodable): The `key` of the object to get the internal encoding of.

        Returns:
            Optional[bytes]: If `key` exists, returns the internal encoding of the object stored at
                `key` as a bytes string. Otherwise, returns None.

        Examples:
            >>> await client.object_encoding("my_hash")
                b"listpack"  # The hash stored at "my_hash" has an internal encoding of "listpack".
        """
        return cast(
            Optional[bytes],
            await self._execute_command(RequestType.ObjectEncoding, [key]),
        )

    async def object_freq(self, key: TEncodable) -> Optional[int]:
        """
        Returns the logarithmic access frequency counter of a Valkey object stored at `key`.

        See https://valkey.io/commands/object-freq for more details.

        Args:
            key (TEncodable): The key of the object to get the logarithmic access frequency counter of.

        Returns:
            Optional[int]: If `key` exists, returns the logarithmic access frequency counter of the object stored at `key` as an
                integer. Otherwise, returns None.

        Examples:
            >>> await client.object_freq("my_hash")
                2  # The logarithmic access frequency counter of "my_hash" has a value of 2.
        """
        return cast(
            Optional[int],
            await self._execute_command(RequestType.ObjectFreq, [key]),
        )

    async def object_idletime(self, key: TEncodable) -> Optional[int]:
        """
        Returns the time in seconds since the last access to the value stored at `key`.

        See https://valkey.io/commands/object-idletime for more details.

        Args:
            key (TEncodable): The key of the object to get the idle time of.

        Returns:
            Optional[int]: If `key` exists, returns the idle time in seconds. Otherwise, returns None.

        Examples:
            >>> await client.object_idletime("my_hash")
                13  # "my_hash" was last accessed 13 seconds ago.
        """
        return cast(
            Optional[int],
            await self._execute_command(RequestType.ObjectIdleTime, [key]),
        )

    async def object_refcount(self, key: TEncodable) -> Optional[int]:
        """
        Returns the reference count of the object stored at `key`.

        See https://valkey.io/commands/object-refcount for more details.

        Args:
            key (TEncodable): The key of the object to get the reference count of.

        Returns:
            Optional[int]: If `key` exists, returns the reference count of the object stored at `key` as an integer.
                Otherwise, returns None.

        Examples:
            >>> await client.object_refcount("my_hash")
                2  # "my_hash" has a reference count of 2.
        """
        return cast(
            Optional[int],
            await self._execute_command(RequestType.ObjectRefCount, [key]),
        )

    async def srandmember(self, key: TEncodable) -> Optional[bytes]:
        """
        Returns a random element from the set value stored at 'key'.

        See https://valkey.io/commands/srandmember for more details.

        Args:
            key (TEncodable): The key from which to retrieve the set member.

        Returns:
            Optional[bytes]: A random element from the set, or None if 'key' does not exist.

        Examples:
            >>> await client.sadd("my_set", {"member1": 1.0, "member2": 2.0})
            >>> await client.srandmember(b"my_set")
                b"member1"  # "member1" is a random member of "my_set".
            >>> await client.srandmember("non_existing_set")
                None  # "non_existing_set" is not an existing key, so None was returned.
        """
        args: List[TEncodable] = [key]
        return cast(
            Optional[bytes],
            await self._execute_command(RequestType.SRandMember, args),
        )

    async def srandmember_count(self, key: TEncodable, count: int) -> List[bytes]:
        """
        Returns one or more random elements from the set value stored at 'key'.

        See https://valkey.io/commands/srandmember for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            count (int): The number of members to return.
                If `count` is positive, returns unique members.
                If `count` is negative, allows for duplicates members.

        Returns:
            List[bytes]: A list of members from the set.
                If the set does not exist or is empty, the response will be an empty list.

        Examples:
            >>> await client.sadd("my_set", {"member1": 1.0, "member2": 2.0})
            >>> await client.srandmember("my_set", -3)
                [b"member1", b"member1", b"member2"]  # "member1" and "member2" are random members of "my_set".
            >>> await client.srandmember("non_existing_set", 3)
                []  # "non_existing_set" is not an existing key, so an empty list was returned.
        """
        return cast(
            List[bytes],
            await self._execute_command(RequestType.SRandMember, [key, str(count)]),
        )

    async def getex(
        self,
        key: TEncodable,
        expiry: Optional[ExpiryGetEx] = None,
    ) -> Optional[bytes]:
        """
        Get the value of `key` and optionally set its expiration. `GETEX` is similar to `GET`.
        See https://valkey.io/commands/getex for more details.

        Args:
            key (TEncodable): The key to get.
            expiry (Optional[ExpiryGetEx], optional): set expiriation to the given key.
                Equivalent to [`EX` | `PX` | `EXAT` | `PXAT` | `PERSIST`] in the Valkey API.

        Returns:
            Optional[bytes]:
                If `key` exists, return the value stored at `key`
                If `key` does not exist, return `None`

        Examples:
            >>> await client.set("key", "value")
                'OK'
            >>> await client.getex("key")
                b'value'
            >>> await client.getex("key", ExpiryGetEx(ExpiryTypeGetEx.SEC, 1))
                b'value'
            >>> time.sleep(1)
            >>> await client.getex(b"key")
                None

        Since: Valkey version 6.2.0.
        """
        args = [key]
        if expiry is not None:
            args.extend(expiry.get_cmd_args())
        return cast(
            Optional[bytes],
            await self._execute_command(RequestType.GetEx, args),
        )

    async def dump(
        self,
        key: TEncodable,
    ) -> Optional[bytes]:
        """
        Serialize the value stored at `key` in a Valkey-specific format and return it to the user.

        See https://valkey.io/commands/dump for more details.

        Args:
            key (TEncodable): The `key` to serialize.

        Returns:
            Optional[bytes]: The serialized value of the data stored at `key`.
                If `key` does not exist, `None` will be returned.

        Examples:
            >>> await client.dump("key")
                b"value" # The serialized value stored at `key`.
            >>> await client.dump("nonExistingKey")
                None # Non-existing key will return `None`.
        """
        return cast(
            Optional[bytes],
            await self._execute_command(RequestType.Dump, [key]),
        )

    async def restore(
        self,
        key: TEncodable,
        ttl: int,
        value: TEncodable,
        replace: bool = False,
        absttl: bool = False,
        idletime: Optional[int] = None,
        frequency: Optional[int] = None,
    ) -> TOK:
        """
        Create a `key` associated with a `value` that is obtained by deserializing the provided
        serialized `value` obtained via `dump`.

        See https://valkey.io/commands/restore for more details.

        Args:
            key (TEncodable): The `key` to create.
            ttl (int): The expiry time (in milliseconds). If `0`, the `key` will persist.
            value (TEncodable) The serialized value to deserialize and assign to `key`.
            replace (bool): Set to `True` to replace the key if it exists.
            absttl (bool): Set to `True` to specify that `ttl` represents an absolute Unix
                timestamp (in milliseconds).
            idletime (Optional[int]): Set the `IDLETIME` option with object idletime to the given key.
            frequency (Optional[int]): Set the `FREQ` option with object frequency to the given key.

        Returns:
            OK: If the `key` was successfully restored with a `value`.

        Examples:
            >>> await client.restore("newKey", 0, value)
                OK # Indicates restore `newKey` without any ttl expiry nor any option
            >>> await client.restore("newKey", 0, value, replace=True)
                OK # Indicates restore `newKey` with `REPLACE` option
            >>> await client.restore("newKey", 0, value, absttl=True)
                OK # Indicates restore `newKey` with `ABSTTL` option
            >>> await client.restore("newKey", 0, value, idletime=10)
                OK # Indicates restore `newKey` with `IDLETIME` option
            >>> await client.restore("newKey", 0, value, frequency=5)
                OK # Indicates restore `newKey` with `FREQ` option
        """
        args = [key, str(ttl), value]
        if replace is True:
            args.append("REPLACE")
        if absttl is True:
            args.append("ABSTTL")
        if idletime is not None:
            args.extend(["IDLETIME", str(idletime)])
        if frequency is not None:
            args.extend(["FREQ", str(frequency)])
        return cast(
            TOK,
            await self._execute_command(RequestType.Restore, args),
        )

    async def sscan(
        self,
        key: TEncodable,
        cursor: TEncodable,
        match: Optional[TEncodable] = None,
        count: Optional[int] = None,
    ) -> List[Union[bytes, List[bytes]]]:
        """
        Iterates incrementally over a set.

        See https://valkey.io/commands/sscan for more details.

        Args:
            key (TEncodable): The key of the set.
            cursor (TEncodable): The cursor that points to the next iteration of results. A value of "0" indicates the start of
                the search.
            match (Optional[TEncodable]): The match filter is applied to the result of the command and will only include
                strings or byte strings that match the pattern specified. If the set is large enough for scan commands to return only a
                subset of the set then there could be a case where the result is empty although there are items that
                match the pattern specified. This is due to the default `COUNT` being `10` which indicates that it will
                only fetch and match `10` items from the list.
            count (Optional[int]): `COUNT` is a just a hint for the command for how many elements to fetch from the set.
                `COUNT` could be ignored until the set is large enough for the `SCAN` commands to represent the results
                as compact single-allocation packed encoding.

        Returns:
            List[Union[bytes, List[bytes]]]: An `Array` of the `cursor` and the subset of the set held by `key`.
                The first element is always the `cursor` for the next iteration of results. `0` will be the `cursor`
                returned on the last iteration of the set. The second element is always an `Array` of the subset of the
                set held in `key`.

        Examples:
            # Assume "key" contains a set with 130 members
            >>> result_cursor = "0"
            >>> while True:
            ...     result = await client.sscan("key", "0", match="*")
            ...     new_cursor = str(result [0])
            ...     print("Cursor: ", new_cursor)
            ...     print("Members: ", result[1])
            ...     if new_cursor == "0":
            ...         break
            ...     result_cursor = new_cursor
            Cursor:  48
            Members: [b'3', b'118', b'120', b'86', b'76', b'13', b'61', b'111', b'55', b'45']
            Cursor: 24
            Members: [b'38', b'109', b'11', b'119', b'34', b'24', b'40', b'57', b'20', b'17']
            Cursor: 0
            Members: [b'47', b'122', b'1', b'53', b'10', b'14', b'80']
        """
        args: List[TEncodable] = [key, cursor]
        if match is not None:
            args += ["MATCH", match]
        if count is not None:
            args += ["COUNT", str(count)]

        return cast(
            List[Union[bytes, List[bytes]]],
            await self._execute_command(RequestType.SScan, args),
        )

    async def zscan(
        self,
        key: TEncodable,
        cursor: TEncodable,
        match: Optional[TEncodable] = None,
        count: Optional[int] = None,
    ) -> List[Union[bytes, List[bytes]]]:
        """
        Iterates incrementally over a sorted set.

        See https://valkey.io/commands/zscan for more details.

        Args:
            key (TEncodable): The key of the sorted set.
            cursor (TEncodable): The cursor that points to the next iteration of results. A value of "0" indicates the start of
                the search.
            match (Optional[TEncodable]): The match filter is applied to the result of the command and will only include
                strings or byte strings that match the pattern specified. If the sorted set is large enough for scan commands to return
                only a subset of the sorted set then there could be a case where the result is empty although there are
                items that match the pattern specified. This is due to the default `COUNT` being `10` which indicates
                that it will only fetch and match `10` items from the list.
            count (Optional[int]): `COUNT` is a just a hint for the command for how many elements to fetch from the
                sorted set. `COUNT` could be ignored until the sorted set is large enough for the `SCAN` commands to
                represent the results as compact single-allocation packed encoding.

        Returns:
            List[Union[bytes, List[bytes]]]: An `Array` of the `cursor` and the subset of the sorted set held by `key`.
                The first element is always the `cursor` for the next iteration of results. `0` will be the `cursor`
                returned on the last iteration of the sorted set. The second element is always an `Array` of the subset
                of the sorted set held in `key`. The `Array` in the second element is always a flattened series of
                `String` pairs, where the value is at even indices and the score is at odd indices.

        Examples:
            # Assume "key" contains a sorted set with multiple members
            >>> result_cursor = "0"
            >>> while True:
            ...     result = await client.zscan("key", "0", match="*", count=5)
            ...     new_cursor = str(result [0])
            ...     print("Cursor: ", new_cursor)
            ...     print("Members: ", result[1])
            ...     if new_cursor == "0":
            ...         break
            ...     result_cursor = new_cursor
            Cursor: 123
            Members: [b'value 163', b'163', b'value 114', b'114', b'value 25', b'25', b'value 82', b'82', b'value 64', b'64']
            Cursor: 47
            Members: [b'value 39', b'39', b'value 127', b'127', b'value 43', b'43', b'value 139', b'139', b'value 211', b'211']
            Cursor: 0
            Members: [b'value 55', b'55', b'value 24', b'24', b'value 90', b'90', b'value 113', b'113']
        """
        args: List[TEncodable] = [key, cursor]
        if match is not None:
            args += ["MATCH", match]
        if count is not None:
            args += ["COUNT", str(count)]

        return cast(
            List[Union[bytes, List[bytes]]],
            await self._execute_command(RequestType.ZScan, args),
        )

    async def hscan(
        self,
        key: TEncodable,
        cursor: TEncodable,
        match: Optional[TEncodable] = None,
        count: Optional[int] = None,
    ) -> List[Union[bytes, List[bytes]]]:
        """
        Iterates incrementally over a hash.

        See https://valkey.io/commands/hscan for more details.

        Args:
            key (TEncodable): The key of the set.
            cursor (TEncodable): The cursor that points to the next iteration of results. A value of "0" indicates the start of
                the search.
            match (Optional[TEncodable]): The match filter is applied to the result of the command and will only include
                strings or byte strings that match the pattern specified. If the hash is large enough for scan commands to return only a
                subset of the hash then there could be a case where the result is empty although there are items that
                match the pattern specified. This is due to the default `COUNT` being `10` which indicates that it will
                only fetch and match `10` items from the list.
            count (Optional[int]): `COUNT` is a just a hint for the command for how many elements to fetch from the hash.
                `COUNT` could be ignored until the hash is large enough for the `SCAN` commands to represent the results
                as compact single-allocation packed encoding.

        Returns:
            List[Union[bytes, List[bytes]]]: An `Array` of the `cursor` and the subset of the hash held by `key`.
                The first element is always the `cursor` for the next iteration of results. `0` will be the `cursor`
                returned on the last iteration of the hash. The second element is always an `Array` of the subset of the
                hash held in `key`. The `Array` in the second element is always a flattened series of `String` pairs,
                where the value is at even indices and the score is at odd indices.

        Examples:
            # Assume "key" contains a hash with multiple members
            >>> result_cursor = "0"
            >>> while True:
            ...     result = await client.hscan("key", "0", match="*", count=3)
            ...     new_cursor = str(result [0])
            ...     print("Cursor: ", new_cursor)
            ...     print("Members: ", result[1])
            ...     if new_cursor == "0":
            ...         break
            ...     result_cursor = new_cursor
            Cursor: 1
            Members: [b'field 79', b'value 79', b'field 20', b'value 20', b'field 115', b'value 115']
            Cursor: 39
            Members: [b'field 63', b'value 63', b'field 293', b'value 293', b'field 162', b'value 162']
            Cursor: 0
            Members: [b'field 420', b'value 420', b'field 221', b'value 221']
        """
        args: List[TEncodable] = [key, cursor]
        if match is not None:
            args += ["MATCH", match]
        if count is not None:
            args += ["COUNT", str(count)]

        return cast(
            List[Union[bytes, List[bytes]]],
            await self._execute_command(RequestType.HScan, args),
        )

    async def fcall(
        self,
        function: TEncodable,
        keys: Optional[List[TEncodable]] = None,
        arguments: Optional[List[TEncodable]] = None,
    ) -> TResult:
        """
        Invokes a previously loaded function.
        See https://valkey.io/commands/fcall/ for more details.
        When in cluster mode, all keys in `keys` must map to the same hash slot.
        Args:
            function (TEncodable): The function name.
            keys (Optional[List[TEncodable]]): A list of keys accessed by the function. To ensure the correct
                execution of functions, both in standalone and clustered deployments, all names of keys
                that a function accesses must be explicitly provided as `keys`.
            arguments (Optional[List[TEncodable]]): A list of `function` arguments. `Arguments`
                should not represent names of keys.
        Returns:
            TResult:
                The invoked function's return value.
        Example:
            >>> await client.fcall("Deep_Thought")
                b'new_value' # Returns the function's return value.

        Since: Valkey version 7.0.0.
        """
        args: List[TEncodable] = []
        if keys is not None:
            args.extend([function, str(len(keys))] + keys)
        else:
            args.extend([function, str(0)])
        if arguments is not None:
            args.extend(arguments)
        return cast(
            TResult,
            await self._execute_command(RequestType.FCall, args),
        )

    async def fcall_ro(
        self,
        function: TEncodable,
        keys: Optional[List[TEncodable]] = None,
        arguments: Optional[List[TEncodable]] = None,
    ) -> TResult:
        """
        Invokes a previously loaded read-only function.

        See https://valkey.io/commands/fcall_ro for more details.

        When in cluster mode, all keys in `keys` must map to the same hash slot.

        Args:
            function (TEncodable): The function name.
            keys (List[TEncodable]): An `array` of keys accessed by the function. To ensure the correct
                execution of functions, all names of keys that a function accesses must be
                explicitly provided as `keys`.
            arguments (List[TEncodable]): An `array` of `function` arguments. `arguments` should not
                represent names of keys.

        Returns:
            TResult: The return value depends on the function that was executed.

        Examples:
            >>> await client.fcall_ro("Deep_Thought", ["key1"], ["Answer", "to", "the",
                    "Ultimate", "Question", "of", "Life,", "the", "Universe,", "and", "Everything"])
                42 # The return value on the function that was executed

        Since: Valkey version 7.0.0.
        """
        args: List[TEncodable] = []
        if keys is not None:
            args.extend([function, str(len(keys))] + keys)
        else:
            args.extend([function, str(0)])
        if arguments is not None:
            args.extend(arguments)
        return cast(
            TResult,
            await self._execute_command(RequestType.FCallReadOnly, args),
        )

    async def watch(self, keys: List[TEncodable]) -> TOK:
        """
        Marks the given keys to be watched for conditional execution of a transaction. Transactions
        will only execute commands if the watched keys are not modified before execution of the
        transaction.

        See https://valkey.io/commands/watch for more details.

        Note:
            When in cluster mode, the command may route to multiple nodes when `keys` map to different hash slots.

        Args:
            keys (List[TEncodable]): The keys to watch.

        Returns:
            TOK: A simple "OK" response.

        Examples:
            >>> await client.watch("sampleKey")
                'OK'
            >>> transaction.set("sampleKey", "foobar")
            >>> await client.exec(transaction)
                'OK' # Executes successfully and keys are unwatched.

            >>> await client.watch("sampleKey")
                'OK'
            >>> transaction.set("sampleKey", "foobar")
            >>> await client.set("sampleKey", "hello world")
                'OK'
            >>> await client.exec(transaction)
                None  # None is returned when the watched key is modified before transaction execution.
        """

        return cast(
            TOK,
            await self._execute_command(RequestType.Watch, keys),
        )

    @dataclass
    class PubSubMsg:
        """
        Describes the incoming pubsub message

        Attributes:
            message (TEncodable): Incoming message.
            channel (TEncodable): Name of an channel that triggered the message.
            pattern (Optional[TEncodable]): Pattern that triggered the message.
        """

        message: TEncodable
        channel: TEncodable
        pattern: Optional[TEncodable]

    async def get_pubsub_message(self) -> PubSubMsg:
        """
        Returns the next pubsub message.
        Throws WrongConfiguration in cases:
        1. No pubsub subscriptions are configured for the client
        2. Callback is configured with the pubsub subsciptions

        See https://valkey.io/docs/topics/pubsub/ for more details.

        Returns:
            PubSubMsg: The next pubsub message

        Examples:
            >>> pubsub_msg = await listening_client.get_pubsub_message()
        """
        ...

    def try_get_pubsub_message(self) -> Optional[PubSubMsg]:
        """
        Tries to return the next pubsub message.
        Throws WrongConfiguration in cases:
        1. No pubsub subscriptions are configured for the client
        2. Callback is configured with the pubsub subsciptions

        See https://valkey.io/docs/topics/pubsub/ for more details.

        Returns:
            Optional[PubSubMsg]: The next pubsub message or None

        Examples:
            >>> pubsub_msg = listening_client.try_get_pubsub_message()
        """
        ...

    async def lcs(
        self,
        key1: TEncodable,
        key2: TEncodable,
    ) -> bytes:
        """
        Returns the longest common subsequence between strings stored at key1 and key2.

        Note that this is different than the longest common string algorithm, since
        matching characters in the two strings do not need to be contiguous.

        For instance the LCS between "foo" and "fao" is "fo", since scanning the two strings
        from left to right, the longest common set of characters is composed of the first "f" and then the "o".

        See https://valkey.io/commands/lcs for more details.

        Args:
            key1 (TEncodable): The key that stores the first string.
            key2 (TEncodable): The key that stores the second string.

        Returns:
            A Bytes String containing the longest common subsequence between the 2 strings.
            An empty String is returned if the keys do not exist or have no common subsequences.

        Examples:
            >>> await client.mset({"testKey1" : "abcd", "testKey2": "axcd"})
                b'OK'
            >>> await client.lcs("testKey1", "testKey2")
                b'acd'

        Since: Valkey version 7.0.0.
        """
        args: List[TEncodable] = [key1, key2]

        return cast(
            bytes,
            await self._execute_command(RequestType.LCS, args),
        )

    async def lcs_len(
        self,
        key1: TEncodable,
        key2: TEncodable,
    ) -> int:
        """
        Returns the length of the longest common subsequence between strings stored at key1 and key2.

        Note that this is different than the longest common string algorithm, since
        matching characters in the two strings do not need to be contiguous.

        For instance the LCS between "foo" and "fao" is "fo", since scanning the two strings
        from left to right, the longest common set of characters is composed of the first "f" and then the "o".

        See https://valkey.io/commands/lcs for more details.

        Args:
            key1 (TEncodable): The key that stores the first string value.
            key2 (TEncodable): The key that stores the second string value.

        Returns:
            The length of the longest common subsequence between the 2 strings.

        Examples:
            >>> await client.mset({"testKey1" : "abcd", "testKey2": "axcd"})
                'OK'
            >>> await client.lcs_len("testKey1", "testKey2")
                3  # the length of the longest common subsequence between these 2 strings (b"acd") is 3.

        Since: Valkey version 7.0.0.
        """
        args: List[TEncodable] = [key1, key2, "LEN"]

        return cast(
            int,
            await self._execute_command(RequestType.LCS, args),
        )

    async def lcs_idx(
        self,
        key1: TEncodable,
        key2: TEncodable,
        min_match_len: Optional[int] = None,
        with_match_len: Optional[bool] = False,
    ) -> Mapping[bytes, Union[List[List[Union[List[int], int]]], int]]:
        """
        Returns the indices and length of the longest common subsequence between strings stored at key1 and key2.

        Note that this is different than the longest common string algorithm, since
        matching characters in the two strings do not need to be contiguous.

        For instance the LCS between "foo" and "fao" is "fo", since scanning the two strings
        from left to right, the longest common set of characters is composed of the first "f" and then the "o".

        See https://valkey.io/commands/lcs for more details.

        Args:
            key1 (TEncodable): The key that stores the first string value.
            key2 (TEncodable): The key that stores the second string value.
            min_match_len (Optional[int]): The minimum length of matches to include in the result.
            with_match_len (Optional[bool]): If True, include the length of the substring matched for each substring.

        Returns:
            A Mapping containing the indices of the longest common subsequence between the
            2 strings and the length of the longest common subsequence. The resulting map contains two
            keys, "matches" and "len":
                - "len" is mapped to the length of the longest common subsequence between the 2 strings.
                - "matches" is mapped to a three dimensional int array that stores pairs of indices that
                  represent the location of the common subsequences in the strings held by key1 and key2,
                  with the length of the match after each matches, if with_match_len is enabled.

        Examples:
            >>> await client.mset({"testKey1" : "abcd1234", "testKey2": "bcdef1234"})
                'OK'
            >>> await client.lcs_idx("testKey1", "testKey2")
                {
                    b'matches': [
                        [
                            [4, 7],  # starting and ending indices of the subsequence b"1234" in b"abcd1234" (testKey1)
                            [5, 8],  # starting and ending indices of the subsequence b"1234" in b"bcdef1234" (testKey2)
                        ],
                        [
                            [1, 3],  # starting and ending indices of the subsequence b"bcd" in b"abcd1234" (testKey1)
                            [0, 2],  # starting and ending indices of the subsequence b"bcd" in b"bcdef1234" (testKey2)
                        ],
                    ],
                    b'len': 7  # length of the entire longest common subsequence
                }
            >>> await client.lcs_idx("testKey1", "testKey2", min_match_len=4)
                {
                    b'matches': [
                        [
                            [4, 7],
                            [5, 8],
                        ],
                        # the other match with a length of 3 is excluded
                    ],
                    b'len': 7
                }
            >>> await client.lcs_idx("testKey1", "testKey2", with_match_len=True)
                {
                    b'matches': [
                        [
                            [4, 7],
                            [5, 8],
                            4,  # length of this match (b"1234")
                        ],
                        [
                            [1, 3],
                            [0, 2],
                            3,  # length of this match (b"bcd")
                        ],
                    ],
                    b'len': 7
                }

        Since: Valkey version 7.0.0.
        """
        args: List[TEncodable] = [key1, key2, "IDX"]

        if min_match_len is not None:
            args.extend(["MINMATCHLEN", str(min_match_len)])

        if with_match_len:
            args.append("WITHMATCHLEN")

        return cast(
            Mapping[bytes, Union[List[List[Union[List[int], int]]], int]],
            await self._execute_command(RequestType.LCS, args),
        )

    async def lpos(
        self,
        key: TEncodable,
        element: TEncodable,
        rank: Optional[int] = None,
        count: Optional[int] = None,
        max_len: Optional[int] = None,
    ) -> Union[int, List[int], None]:
        """
        Returns the index or indexes of element(s) matching `element` in the `key` list. If no match is found,
        None is returned.

        See https://valkey.io/commands/lpos for more details.

        Args:
            key (TEncodable): The name of the list.
            element (TEncodable): The value to search for within the list.
            rank (Optional[int]): The rank of the match to return.
            count (Optional[int]): The number of matches wanted. A `count` of 0 returns all the matches.
            max_len (Optional[int]): The maximum number of comparisons to make between the element and the items
                                     in the list. A `max_len` of 0 means unlimited amount of comparisons.

        Returns:
            Union[int, List[int], None]: The index of the first occurrence of `element`,
            or None if `element` is not in the list.
            With the `count` option, a list of indices of matching elements will be returned.

        Examples:
            >>> await client.rpush(key, ['a', 'b', 'c', '1', '2', '3', 'c', 'c'])
            >>> await client.lpos(key, 'c')
                2
            >>> await client.lpos(key, 'c', rank = 2)
                6
            >>> await client.lpos(key, 'c', rank = -1)
                7
            >>> await client.lpos(key, 'c', count = 2)
                [2, 6]
            >>> await client.lpos(key, 'c', count = 0)
                [2, 6, 7]

        Since: Valkey version 6.0.6.
        """
        args: List[TEncodable] = [key, element]

        if rank is not None:
            args.extend(["RANK", str(rank)])

        if count is not None:
            args.extend(["COUNT", str(count)])

        if max_len is not None:
            args.extend(["MAXLEN", str(max_len)])

        return cast(
            Union[int, List[int], None],
            await self._execute_command(RequestType.LPos, args),
        )

    async def pubsub_channels(
        self, pattern: Optional[TEncodable] = None
    ) -> List[bytes]:
        """
        Lists the currently active channels.
        The command is routed to all nodes, and aggregates the response to a single array.

        See https://valkey.io/commands/pubsub-channels for more details.

        Args:
            pattern (Optional[TEncodable]): A glob-style pattern to match active channels.
                                If not provided, all active channels are returned.

        Returns:
            List[bytes]: A list of currently active channels matching the given pattern.
                    If no pattern is specified, all active channels are returned.

        Examples:
            >>> await client.pubsub_channels()
                [b"channel1", b"channel2"]

            >>> await client.pubsub_channels("news.*")
                [b"news.sports", "news.weather"]
        """

        return cast(
            List[bytes],
            await self._execute_command(
                RequestType.PubSubChannels, [pattern] if pattern else []
            ),
        )

    async def pubsub_numpat(self) -> int:
        """
        Returns the number of unique patterns that are subscribed to by clients.

        Note: This is the total number of unique patterns all the clients are subscribed to,
        not the count of clients subscribed to patterns.
        The command is routed to all nodes, and aggregates the response the sum of all pattern subscriptions.

        See https://valkey.io/commands/pubsub-numpat for more details.

        Returns:
            int: The number of unique patterns.

        Examples:
            >>> await client.pubsub_numpat()
                3
        """
        return cast(int, await self._execute_command(RequestType.PubSubNumPat, []))

    async def pubsub_numsub(
        self, channels: Optional[List[TEncodable]] = None
    ) -> Mapping[bytes, int]:
        """
        Returns the number of subscribers (exclusive of clients subscribed to patterns) for the specified channels.

        Note that it is valid to call this command without channels. In this case, it will just return an empty map.
        The command is routed to all nodes, and aggregates the response to a single map of the channels and their number of subscriptions.

        See https://valkey.io/commands/pubsub-numsub for more details.

        Args:
            channels (Optional[List[TEncodable]]): The list of channels to query for the number of subscribers.
                                            If not provided, returns an empty map.

        Returns:
            Mapping[bytes, int]: A map where keys are the channel names and values are the number of subscribers.

        Examples:
            >>> await client.pubsub_numsub(["channel1", "channel2"])
                {b'channel1': 3, b'channel2': 5}

            >>> await client.pubsub_numsub()
                {}
        """
        return cast(
            Mapping[bytes, int],
            await self._execute_command(
                RequestType.PubSubNumSub, channels if channels else []
            ),
        )
