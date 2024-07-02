# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
from abc import ABC, abstractmethod
from collections.abc import Mapping
from dataclasses import dataclass
from datetime import datetime, timedelta
from enum import Enum
from typing import (
    Dict,
    List,
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
from glide.async_commands.command_args import Limit, ListDirection, OrderBy
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
    StreamGroupOptions,
    StreamPendingOptions,
    StreamRangeBound,
    StreamReadGroupOptions,
    StreamReadOptions,
    StreamTrimOptions,
    _create_xpending_range_args,
)
from glide.constants import TOK, TResult
from glide.protobuf.redis_request_pb2 import RequestType
from glide.routes import Route

from ..glide import Script


class ConditionalChange(Enum):
    """
    A condition to the `SET`, `ZADD` and `GEOADD` commands.
    - ONLY_IF_EXISTS - Only update key / elements that already exist. Equivalent to `XX` in the Redis API
    - ONLY_IF_DOES_NOT_EXIST - Only set key / add elements that does not already exist. Equivalent to `NX` in the Redis API
    """

    ONLY_IF_EXISTS = "XX"
    ONLY_IF_DOES_NOT_EXIST = "NX"


class ExpiryType(Enum):
    """SET option: The type of the expiry.
    - SEC - Set the specified expire time, in seconds. Equivalent to `EX` in the Redis API.
    - MILLSEC - Set the specified expire time, in milliseconds. Equivalent to `PX` in the Redis API.
    - UNIX_SEC - Set the specified Unix time at which the key will expire, in seconds. Equivalent to `EXAT` in the Redis API.
    - UNIX_MILLSEC - Set the specified Unix time at which the key will expire, in milliseconds. Equivalent to `PXAT` in the
        Redis API.
    - KEEP_TTL - Retain the time to live associated with the key. Equivalent to `KEEPTTL` in the Redis API.
    """

    SEC = 0, Union[int, timedelta]  # Equivalent to `EX` in the Redis API
    MILLSEC = 1, Union[int, timedelta]  # Equivalent to `PX` in the Redis API
    UNIX_SEC = 2, Union[int, datetime]  # Equivalent to `EXAT` in the Redis API
    UNIX_MILLSEC = 3, Union[int, datetime]  # Equivalent to `PXAT` in the Redis API
    KEEP_TTL = 4, Type[None]  # Equivalent to `KEEPTTL` in the Redis API


class ExpiryTypeGetEx(Enum):
    """GetEx option: The type of the expiry.
    - EX - Set the specified expire time, in seconds. Equivalent to `EX` in the Redis API.
    - PX - Set the specified expire time, in milliseconds. Equivalent to `PX` in the Redis API.
    - UNIX_SEC - Set the specified Unix time at which the key will expire, in seconds. Equivalent to `EXAT` in the Redis API.
    - UNIX_MILLSEC - Set the specified Unix time at which the key will expire, in milliseconds. Equivalent to `PXAT` in the
        Redis API.
    - PERSIST - Remove the time to live associated with the key. Equivalent to `PERSIST` in the Redis API.
    """

    SEC = 0, Union[int, timedelta]  # Equivalent to `EX` in the Redis API
    MILLSEC = 1, Union[int, timedelta]  # Equivalent to `PX` in the Redis API
    UNIX_SEC = 2, Union[int, datetime]  # Equivalent to `EXAT` in the Redis API
    UNIX_MILLSEC = 3, Union[int, datetime]  # Equivalent to `PXAT` in the Redis API
    PERSIST = 4, Type[None]  # Equivalent to `PERSIST` in the Redis API


class InfoSection(Enum):
    """
    INFO option: a specific section of information:

    -SERVER: General information about the Redis server
    -CLIENTS: Client connections section
    -MEMORY: Memory consumption related information
    -PERSISTENCE: RDB and AOF related information
    -STATS: General statistics
    -REPLICATION: Master/replica replication information
    -CPU: CPU consumption statistics
    -COMMANDSTATS: Redis command statistics
    -LATENCYSTATS: Redis command latency percentile distribution statistics
    -SENTINEL: Redis Sentinel section (only applicable to Sentinel instances)
    -CLUSTER: Redis Cluster section
    -MODULES: Modules section
    -KEYSPACE: Database related statistics
    -ERRORSTATS: Redis error statistics
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

    - HasNoExpiry: Set expiry only when the key has no expiry (Equivalent to "NX" in Redis).
    - HasExistingExpiry: Set expiry only when the key has an existing expiry (Equivalent to "XX" in Redis).
    - NewExpiryGreaterThanCurrent: Set expiry only when the new expiry is greater than the current one (Equivalent
        to "GT" in Redis).
    - NewExpiryLessThanCurrent: Set expiry only when the new expiry is less than the current one (Equivalent to "LT" in Redis).
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


def _build_sort_args(
    key: str,
    by_pattern: Optional[str] = None,
    limit: Optional[Limit] = None,
    get_patterns: Optional[List[str]] = None,
    order: Optional[OrderBy] = None,
    alpha: Optional[bool] = None,
    store: Optional[str] = None,
) -> List[str]:
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
        args: List[str],
        route: Optional[Route] = ...,
    ) -> TResult: ...

    async def _execute_transaction(
        self,
        commands: List[Tuple[RequestType.ValueType, List[str]]],
        route: Optional[Route] = None,
    ) -> List[TResult]: ...

    async def _execute_script(
        self,
        hash: str,
        keys: Optional[List[Union[str, bytes]]] = None,
        args: Optional[List[Union[str, bytes]]] = None,
        route: Optional[Route] = None,
    ) -> TResult: ...

    async def set(
        self,
        key: str,
        value: str,
        conditional_set: Optional[ConditionalChange] = None,
        expiry: Optional[ExpirySet] = None,
        return_old_value: bool = False,
    ) -> Optional[str]:
        """
        Set the given key with the given value. Return value is dependent on the passed options.
        See https://redis.io/commands/set/ for more details.

        Args:
            key (str): the key to store.
            value (str): the value to store with the given key.
            conditional_set (Optional[ConditionalChange], optional): set the key only if the given condition is met.
                Equivalent to [`XX` | `NX`] in the Redis API. Defaults to None.
            expiry (Optional[ExpirySet], optional): set expiriation to the given key.
                Equivalent to [`EX` | `PX` | `EXAT` | `PXAT` | `KEEPTTL`] in the Redis API. Defaults to None.
            return_old_value (bool, optional): Return the old string stored at key, or None if key did not exist.
                An error is returned and SET aborted if the value stored at key is not a string.
                Equivalent to `GET` in the Redis API. Defaults to False.

        Returns:
            Optional[str]:
                If the value is successfully set, return OK.
                If value isn't set because of only_if_exists or only_if_does_not_exist conditions, return None.
                If return_old_value is set, return the old value as a string.

        Example:
            >>> await client.set("key", "value")
                'OK'
            >>> await client.set("key", "new_value",conditional_set=ConditionalChange.ONLY_IF_EXISTS, expiry=Expiry(ExpiryType.SEC, 5))
                'OK' # Set "new_value" to "key" only if "key" already exists, and set the key expiration to 5 seconds.
            >>> await client.set("key", "value", conditional_set=ConditionalChange.ONLY_IF_DOES_NOT_EXIST,return_old_value=True)
                'new_value' # Returns the old value of "key".
            >>> await client.get("key")
                'new_value' # Value wasn't modified back to being "value" because of "NX" flag.
        """
        args = [key, value]
        if conditional_set:
            args.append(conditional_set.value)
        if return_old_value:
            args.append("GET")
        if expiry is not None:
            args.extend(expiry.get_cmd_args())
        return cast(Optional[str], await self._execute_command(RequestType.Set, args))

    async def get(self, key: str) -> Optional[str]:
        """
        Get the value associated with the given key, or null if no such value exists.
        See https://redis.io/commands/get/ for details.

        Args:
            key (str): The key to retrieve from the database.

        Returns:
            Optional[str]: If the key exists, returns the value of the key as a string. Otherwise, return None.

        Example:
            >>> await client.get("key")
                'value'
        """
        return cast(Optional[str], await self._execute_command(RequestType.Get, [key]))

    async def getdel(self, key: str) -> Optional[str]:
        """
        Gets a string value associated with the given `key` and deletes the key.

        See https://valkey.io/commands/getdel for more details.

        Args:
            key (str): The `key` to retrieve from the database.

        Returns:
            Optional[str]: If `key` exists, returns the `value` of `key`. Otherwise, returns `None`.

        Examples:
            >>> await client.set("key", "value")
            >>> await client.getdel("key")
               'value'
            >>> await client.getdel("key")
                None
        """
        return cast(
            Optional[str], await self._execute_command(RequestType.GetDel, [key])
        )

    async def getrange(self, key: str, start: int, end: int) -> str:
        """
        Returns the substring of the string value stored at `key`, determined by the offsets `start` and `end` (both are inclusive).
        Negative offsets can be used in order to provide an offset starting from the end of the string.
        So `-1` means the last character, `-2` the penultimate and so forth.

        If `key` does not exist, an empty string is returned. If `start` or `end`
        are out of range, returns the substring within the valid range of the string.

        See https://valkey.io/commands/getrange/ for more details.

        Args:
            key (str): The key of the string.
            start (int): The starting offset.
            end (int): The ending offset.

        Returns:
            str: A substring extracted from the value stored at `key`.

        Examples:
            >>> await client.set("mykey", "This is a string")
            >>> await client.getrange("mykey", 0, 3)
                "This"
            >>> await client.getrange("mykey", -3, -1)
                "ing"  # extracted last 3 characters of a string
            >>> await client.getrange("mykey", 0, 100)
                "This is a string"
            >>> await client.getrange("non_existing", 5, 6)
                ""
        """
        return cast(
            str,
            await self._execute_command(
                RequestType.GetRange, [key, str(start), str(end)]
            ),
        )

    async def append(self, key: str, value: str) -> int:
        """
        Appends a value to a key.
        If `key` does not exist it is created and set as an empty string, so `APPEND` will be similar to `SET` in this special case.

        See https://redis.io/commands/append for more details.

        Args:
            key (str): The key to which the value will be appended.
            value (str): The value to append.

        Returns:
            int: The length of the string after appending the value.

        Examples:
            >>> await client.append("key", "Hello")
                5  # Indicates that "Hello" has been appended to the value of "key", which was initially empty, resulting in a new value of "Hello" with a length of 5 - similar to the set operation.
            >>> await client.append("key", " world")
                11  # Indicates that " world" has been appended to the value of "key", resulting in a new value of "Hello world" with a length of 11.
            >>> await client.get("key")
                "Hello world"  # Returns the value stored in "key", which is now "Hello world".
        """
        return cast(int, await self._execute_command(RequestType.Append, [key, value]))

    async def strlen(self, key: str) -> int:
        """
        Get the length of the string value stored at `key`.
        See https://redis.io/commands/strlen/ for more details.

        Args:
            key (str): The key to return its length.

        Returns:
            int: The length of the string value stored at `key`.
                If `key` does not exist, it is treated as an empty string and 0 is returned.

        Examples:
            >>> await client.set("key", "GLIDE")
            >>> await client.strlen("key")
                5  # Indicates that the length of the string value stored at `key` is 5.
        """
        return cast(int, await self._execute_command(RequestType.Strlen, [key]))

    async def rename(self, key: str, new_key: str) -> TOK:
        """
        Renames `key` to `new_key`.
        If `newkey` already exists it is overwritten.
        See https://redis.io/commands/rename/ for more details.

        Note:
            When in cluster mode, both `key` and `newkey` must map to the same hash slot.

        Args:
            key (str) : The key to rename.
            new_key (str) : The new name of the key.

        Returns:
            OK: If the `key` was successfully renamed, return "OK". If `key` does not exist, an error is thrown.
        """
        return cast(
            TOK, await self._execute_command(RequestType.Rename, [key, new_key])
        )

    async def renamenx(self, key: str, new_key: str) -> bool:
        """
        Renames `key` to `new_key` if `new_key` does not yet exist.

        See https://valkey.io/commands/renamenx for more details.

        Note:
            When in cluster mode, both `key` and `new_key` must map to the same hash slot.

        Args:
            key (str): The key to rename.
            new_key (str): The new key name.

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

    async def delete(self, keys: List[str]) -> int:
        """
        Delete one or more keys from the database. A key is ignored if it does not exist.
        See https://redis.io/commands/del/ for details.

        Note:
            When in cluster mode, the command may route to multiple nodes when `keys` map to different hash slots.

        Args:
            keys (List[str]): A list of keys to be deleted from the database.

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

    async def incr(self, key: str) -> int:
        """
        Increments the number stored at `key` by one. If the key does not exist, it is set to 0 before performing the
        operation.
        See https://redis.io/commands/incr/ for more details.

        Args:
          key (str): The key to increment its value.

        Returns:
            int: The value of `key` after the increment.

        Examples:
            >>> await client.set("key", "10")
            >>> await client.incr("key")
                11
        """
        return cast(int, await self._execute_command(RequestType.Incr, [key]))

    async def incrby(self, key: str, amount: int) -> int:
        """
        Increments the number stored at `key` by `amount`. If the key does not exist, it is set to 0 before performing
        the operation. See https://redis.io/commands/incrby/ for more details.

        Args:
          key (str): The key to increment its value.
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

    async def incrbyfloat(self, key: str, amount: float) -> float:
        """
        Increment the string representing a floating point number stored at `key` by `amount`.
        By using a negative increment value, the value stored at the `key` is decremented.
        If the key does not exist, it is set to 0 before performing the operation.
        See https://redis.io/commands/incrbyfloat/ for more details.

        Args:
          key (str): The key to increment its value.
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

    async def setrange(self, key: str, offset: int, value: str) -> int:
        """
        Overwrites part of the string stored at `key`, starting at the specified
        `offset`, for the entire length of `value`.
        If the `offset` is larger than the current length of the string at `key`,
        the string is padded with zero bytes to make `offset` fit. Creates the `key`
        if it doesn't exist.

        See https://valkey.io/commands/setrange for more details.

        Args:
            key (str): The key of the string to update.
            offset (int): The position in the string where `value` should be written.
            value (str): The string written with `offset`.

        Returns:
            int: The length of the string stored at `key` after it was modified.

        Examples:
            >>> await client.set("key", "Hello World")
            >>> await client.setrange("key", 6, "Redis")
                11  # The length of the string stored at `key` after it was modified.
        """
        return cast(
            int,
            await self._execute_command(
                RequestType.SetRange, [key, str(offset), value]
            ),
        )

    async def mset(self, key_value_map: Mapping[str, str]) -> TOK:
        """
        Set multiple keys to multiple values in a single atomic operation.
        See https://redis.io/commands/mset/ for more details.

        Note:
            When in cluster mode, the command may route to multiple nodes when keys in `key_value_map` map to different hash slots.

        Args:
            key_value_map (Mapping[str, str]): A map of key value pairs.

        Returns:
            OK: a simple OK response.

        Example:
            >>> await client.mset({"key" : "value", "key2": "value2"})
                'OK'
        """
        parameters: List[str] = []
        for pair in key_value_map.items():
            parameters.extend(pair)
        return cast(TOK, await self._execute_command(RequestType.MSet, parameters))

    async def msetnx(self, key_value_map: Mapping[str, str]) -> bool:
        """
        Sets multiple keys to values if the key does not exist. The operation is atomic, and if one or
        more keys already exist, the entire operation fails.

        Note:
            When in cluster mode, all keys in `key_value_map` must map to the same hash slot.

        See https://valkey.io/commands/msetnx/ for more details.

        Args:
            key_value_map (Mapping[str, str]): A key-value map consisting of keys and their respective values to set.

        Returns:
            bool: True if all keys were set. False if no key was set.

        Examples:
            >>> await client.msetnx({"key1": "value1", "key2": "value2"})
                True
            >>> await client.msetnx({"key2": "value4", "key3": "value5"})
                False
        """
        parameters: List[str] = []
        for pair in key_value_map.items():
            parameters.extend(pair)
        return cast(
            bool,
            await self._execute_command(RequestType.MSetNX, parameters),
        )

    async def mget(self, keys: List[str]) -> List[Optional[str]]:
        """
        Retrieve the values of multiple keys.
        See https://redis.io/commands/mget/ for more details.

        Note:
            When in cluster mode, the command may route to multiple nodes when `keys` map to different hash slots.

        Args:
            keys (List[str]): A list of keys to retrieve values for.

        Returns:
            List[Optional[str]]: A list of values corresponding to the provided keys. If a key is not found,
            its corresponding value in the list will be None.

        Examples:
            >>> await client.set("key1", "value1")
            >>> await client.set("key2", "value2")
            >>> await client.mget(["key1", "key2"])
                ['value1' , 'value2']
        """
        return cast(
            List[Optional[str]], await self._execute_command(RequestType.MGet, keys)
        )

    async def decr(self, key: str) -> int:
        """
        Decrement the number stored at `key` by one. If the key does not exist, it is set to 0 before performing the
        operation.
        See https://redis.io/commands/decr/ for more details.

        Args:
          key (str): The key to increment its value.

        Returns:
            int: The value of key after the decrement.

        Examples:
            >>> await client.set("key", "10")
            >>> await client.decr("key")
                9
        """
        return cast(int, await self._execute_command(RequestType.Decr, [key]))

    async def decrby(self, key: str, amount: int) -> int:
        """
        Decrements the number stored at `key` by `amount`. If the key does not exist, it is set to 0 before performing
        the operation.
        See https://redis.io/commands/decrby/ for more details.

        Args:
          key (str): The key to decrement its value.
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

    async def touch(self, keys: List[str]) -> int:
        """
        Updates the last access time of specified keys.

        See https://valkey.io/commands/touch/ for details.

        Note:
            When in cluster mode, the command may route to multiple nodes when `keys` map to different hash slots.

        Args:
            keys (List[str]): The keys to update last access time.

        Returns:
            int: The number of keys that were updated, a key is ignored if it doesn't exist.

        Examples:
            >>> await client.set("myKey1", "value1")
            >>> await client.set("myKey2", "value2")
            >>> await client.touch(["myKey1", "myKey2", "nonExistentKey"])
                2  # Last access time of 2 keys has been updated.
        """
        return cast(int, await self._execute_command(RequestType.Touch, keys))

    async def hset(self, key: str, field_value_map: Mapping[str, str]) -> int:
        """
        Sets the specified fields to their respective values in the hash stored at `key`.
        See https://redis.io/commands/hset/ for more details.

        Args:
            key (str): The key of the hash.
            field_value_map (Mapping[str, str]): A field-value map consisting of fields and their corresponding values
            to be set in the hash stored at the specified key.

        Returns:
            int: The number of fields that were added to the hash.

        Example:
            >>> await client.hset("my_hash", {"field": "value", "field2": "value2"})
                2 # Indicates that 2 fields were successfully set in the hash "my_hash".
        """
        field_value_list: List[str] = [key]
        for pair in field_value_map.items():
            field_value_list.extend(pair)
        return cast(
            int,
            await self._execute_command(RequestType.HSet, field_value_list),
        )

    async def hget(self, key: str, field: str) -> Optional[str]:
        """
        Retrieves the value associated with `field` in the hash stored at `key`.
        See https://redis.io/commands/hget/ for more details.

        Args:
            key (str): The key of the hash.
            field (str): The field whose value should be retrieved.

        Returns:
            Optional[str]: The value associated `field` in the hash.
            Returns None if `field` is not presented in the hash or `key` does not exist.

        Examples:
            >>> await client.hset("my_hash", "field", "value")
            >>> await client.hget("my_hash", "field")
                "value"
            >>> await client.hget("my_hash", "nonexistent_field")
                None
        """
        return cast(
            Optional[str],
            await self._execute_command(RequestType.HGet, [key, field]),
        )

    async def hsetnx(
        self,
        key: str,
        field: str,
        value: str,
    ) -> bool:
        """
        Sets `field` in the hash stored at `key` to `value`, only if `field` does not yet exist.
        If `key` does not exist, a new key holding a hash is created.
        If `field` already exists, this operation has no effect.
        See https://redis.io/commands/hsetnx/ for more details.

        Args:
            key (str): The key of the hash.
            field (str): The field to set the value for.
            value (str): The value to set.

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

    async def hincrby(self, key: str, field: str, amount: int) -> int:
        """
        Increment or decrement the value of a `field` in the hash stored at `key` by the specified amount.
        By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
        If `field` or `key` does not exist, it is set to 0 before performing the operation.
        See https://redis.io/commands/hincrby/ for more details.

        Args:
            key (str): The key of the hash.
            field (str): The field in the hash stored at `key` to increment or decrement its value.
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

    async def hincrbyfloat(self, key: str, field: str, amount: float) -> float:
        """
        Increment or decrement the floating-point value stored at `field` in the hash stored at `key` by the specified
        amount.
        By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
        If `field` or `key` does not exist, it is set to 0 before performing the operation.
        See https://redis.io/commands/hincrbyfloat/ for more details.

        Args:
            key (str): The key of the hash.
            field (str): The field in the hash stored at `key` to increment or decrement its value.
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

    async def hexists(self, key: str, field: str) -> bool:
        """
        Check if a field exists in the hash stored at `key`.
        See https://redis.io/commands/hexists/ for more details.

        Args:
            key (str): The key of the hash.
            field (str): The field to check in the hash stored at `key`.

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

    async def hgetall(self, key: str) -> Dict[str, str]:
        """
        Returns all fields and values of the hash stored at `key`.
        See https://redis.io/commands/hgetall/ for details.

        Args:
            key (str): The key of the hash.

        Returns:
            Dict[str, str]: A dictionary of fields and their values stored in the hash. Every field name in the list is followed by
            its value.
            If `key` does not exist, it returns an empty dictionary.

        Examples:
            >>> await client.hgetall("my_hash")
                {"field1": "value1", "field2": "value2"}
        """
        return cast(
            Dict[str, str], await self._execute_command(RequestType.HGetAll, [key])
        )

    async def hmget(self, key: str, fields: List[str]) -> List[Optional[str]]:
        """
        Retrieve the values associated with specified fields in the hash stored at `key`.
        See https://redis.io/commands/hmget/ for details.

        Args:
            key (str): The key of the hash.
            fields (List[str]): The list of fields in the hash stored at `key` to retrieve from the database.

        Returns:
            List[Optional[str]]: A list of values associated with the given fields, in the same order as they are requested.
            For every field that does not exist in the hash, a null value is returned.
            If `key` does not exist, it is treated as an empty hash, and the function returns a list of null values.

        Examples:
            >>> await client.hmget("my_hash", ["field1", "field2"])
                ["value1", "value2"]  # A list of values associated with the specified fields.
        """
        return cast(
            List[Optional[str]],
            await self._execute_command(RequestType.HMGet, [key] + fields),
        )

    async def hdel(self, key: str, fields: List[str]) -> int:
        """
        Remove specified fields from the hash stored at `key`.
        See https://redis.io/commands/hdel/ for more details.

        Args:
            key (str): The key of the hash.
            fields (List[str]): The list of fields to remove from the hash stored at `key`.

        Returns:
            int: The number of fields that were removed from the hash, excluding specified but non-existing fields.
            If `key` does not exist, it is treated as an empty hash, and the function returns 0.

        Examples:
            >>> await client.hdel("my_hash", ["field1", "field2"])
                2  # Indicates that two fields were successfully removed from the hash.
        """
        return cast(int, await self._execute_command(RequestType.HDel, [key] + fields))

    async def hlen(self, key: str) -> int:
        """
        Returns the number of fields contained in the hash stored at `key`.

        See https://redis.io/commands/hlen/ for more details.

        Args:
            key (str): The key of the hash.

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

    async def hvals(self, key: str) -> List[str]:
        """
        Returns all values in the hash stored at `key`.

        See https://redis.io/commands/hvals/ for more details.

        Args:
            key (str): The key of the hash.

        Returns:
            List[str]: A list of values in the hash, or an empty list when the key does not exist.

        Examples:
           >>> await client.hvals("my_hash")
               ["value1", "value2", "value3"]  # Returns all the values stored in the hash "my_hash".
        """
        return cast(List[str], await self._execute_command(RequestType.HVals, [key]))

    async def hkeys(self, key: str) -> List[str]:
        """
        Returns all field names in the hash stored at `key`.

        See https://redis.io/commands/hkeys/ for more details.

        Args:
            key (str): The key of the hash.

        Returns:
            List[str]: A list of field names for the hash, or an empty list when the key does not exist.

        Examples:
            >>> await client.hkeys("my_hash")
                ["field1", "field2", "field3"]  # Returns all the field names stored in the hash "my_hash".
        """
        return cast(List[str], await self._execute_command(RequestType.HKeys, [key]))

    async def hrandfield(self, key: str) -> Optional[str]:
        """
        Returns a random field name from the hash value stored at `key`.

        See https://valkey.io/commands/hrandfield for more details.

        Args:
            key (str): The key of the hash.

        Returns:
            Optional[str]: A random field name from the hash stored at `key`.
            If the hash does not exist or is empty, None will be returned.

        Examples:
            >>> await client.hrandfield("my_hash")
                "field1"  # A random field name stored in the hash "my_hash".
        """
        return cast(
            Optional[str], await self._execute_command(RequestType.HRandField, [key])
        )

    async def hrandfield_count(self, key: str, count: int) -> List[str]:
        """
        Retrieves up to `count` random field names from the hash value stored at `key`.

        See https://valkey.io/commands/hrandfield for more details.

        Args:
            key (str): The key of the hash.
            count (int): The number of field names to return.
                If `count` is positive, returns unique elements.
                If `count` is negative, allows for duplicates elements.

        Returns:
            List[str]: A list of random field names from the hash.
            If the hash does not exist or is empty, the response will be an empty list.

        Examples:
            >>> await client.hrandfield_count("my_hash", -3)
                ["field1", "field1", "field2"]  # Non-distinct, random field names stored in the hash "my_hash".
            >>> await client.hrandfield_count("non_existing_hash", 3)
                []  # Empty list
        """
        return cast(
            List[str],
            await self._execute_command(RequestType.HRandField, [key, str(count)]),
        )

    async def hrandfield_withvalues(self, key: str, count: int) -> List[List[str]]:
        """
        Retrieves up to `count` random field names along with their values from the hash value stored at `key`.

        See https://valkey.io/commands/hrandfield for more details.

        Args:
            key (str): The key of the hash.
            count (int): The number of field names to return.
                If `count` is positive, returns unique elements.
                If `count` is negative, allows for duplicates elements.

        Returns:
            List[List[str]]: A list of `[field_name, value]` lists, where `field_name` is a random field name from the
            hash and `value` is the associated value of the field name.
            If the hash does not exist or is empty, the response will be an empty list.

        Examples:
            >>> await client.hrandfield_withvalues("my_hash", -3)
                [["field1", "value1"], ["field1", "value1"], ["field2", "value2"]]
        """
        return cast(
            List[List[str]],
            await self._execute_command(
                RequestType.HRandField, [key, str(count), "WITHVALUES"]
            ),
        )

    async def hstrlen(self, key: str, field: str) -> int:
        """
        Returns the string length of the value associated with `field` in the hash stored at `key`.

        See https://valkey.io/commands/hstrlen/ for more details.

        Args:
            key (str): The key of the hash.
            field (str): The field in the hash.

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

    async def lpush(self, key: str, elements: List[str]) -> int:
        """
        Insert all the specified values at the head of the list stored at `key`.
        `elements` are inserted one after the other to the head of the list, from the leftmost element
        to the rightmost element. If `key` does not exist, it is created as empty list before performing the push operations.
        See https://redis.io/commands/lpush/ for more details.

        Args:
            key (str): The key of the list.
            elements (List[str]): The elements to insert at the head of the list stored at `key`.

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

    async def lpushx(self, key: str, elements: List[str]) -> int:
        """
        Inserts all the specified values at the head of the list stored at `key`, only if `key` exists and holds a list.
        If `key` is not a list, this performs no operation.

        See https://redis.io/commands/lpushx/ for more details.

        Args:
            key (str): The key of the list.
            elements (List[str]): The elements to insert at the head of the list stored at `key`.

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

    async def lpop(self, key: str) -> Optional[str]:
        """
        Remove and return the first elements of the list stored at `key`.
        The command pops a single element from the beginning of the list.
        See https://redis.io/commands/lpop/ for details.

        Args:
            key (str): The key of the list.

        Returns:
            Optional[str]: The value of the first element.
            If `key` does not exist, None will be returned.

        Examples:
            >>> await client.lpop("my_list")
                "value1"
            >>> await client.lpop("non_exiting_key")
                None
        """
        return cast(
            Optional[str],
            await self._execute_command(RequestType.LPop, [key]),
        )

    async def lpop_count(self, key: str, count: int) -> Optional[List[str]]:
        """
        Remove and return up to `count` elements from the list stored at `key`, depending on the list's length.
        See https://redis.io/commands/lpop/ for details.

        Args:
            key (str): The key of the list.
            count (int): The count of elements to pop from the list.

        Returns:
            Optional[List[str]]: A a list of popped elements will be returned depending on the list's length.
            If `key` does not exist, None will be returned.

        Examples:
            >>> await client.lpop_count("my_list", 2)
                ["value1", "value2"]
            >>> await client.lpop_count("non_exiting_key" , 3)
                None
        """
        return cast(
            Optional[List[str]],
            await self._execute_command(RequestType.LPop, [key, str(count)]),
        )

    async def blpop(self, keys: List[str], timeout: float) -> Optional[List[str]]:
        """
        Pops an element from the head of the first list that is non-empty, with the given keys being checked in the
        order that they are given. Blocks the connection when there are no elements to pop from any of the given lists.
        See https://valkey.io/commands/blpop for details.

        Notes:
            1. When in cluster mode, all `keys` must map to the same hash slot.
            2. `BLPOP` is a client blocking command, see https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands for more details and best practices.

        Args:
            keys (List[str]): The keys of the lists to pop from.
            timeout (float): The number of seconds to wait for a blocking operation to complete. A value of 0 will block indefinitely.

        Returns:
            Optional[List[str]]: A two-element list containing the key from which the element was popped and the value of the
                popped element, formatted as `[key, value]`. If no element could be popped and the `timeout` expired, returns None.

        Examples:
            >>> await client.blpop(["list1", "list2"], 0.5)
                ["list1", "element"]  # "element" was popped from the head of the list with key "list1"
        """
        return cast(
            Optional[List[str]],
            await self._execute_command(RequestType.BLPop, keys + [str(timeout)]),
        )

    async def lmpop(
        self, keys: List[str], direction: ListDirection, count: Optional[int] = None
    ) -> Optional[Mapping[str, List[str]]]:
        """
        Pops one or more elements from the first non-empty list from the provided `keys`.

        When in cluster mode, all `keys` must map to the same hash slot.

        See https://valkey.io/commands/lmpop/ for details.

        Args:
            keys (List[str]): An array of keys of lists.
            direction (ListDirection): The direction based on which elements are popped from (`ListDirection.LEFT` or `ListDirection.RIGHT`).
            count (Optional[int]): The maximum number of popped elements. If not provided, defaults to popping a single element.

        Returns:
            Optional[Mapping[str, List[str]]]: A map of `key` name mapped to an array of popped elements, or None if no elements could be popped.

        Examples:
            >>> await client.lpush("testKey", ["one", "two", "three"])
            >>> await client.lmpop(["testKey"], ListDirection.LEFT, 2)
               {"testKey": ["three", "two"]}

        Since: Redis version 7.0.0.
        """
        args = [str(len(keys)), *keys, direction.value]
        if count is not None:
            args += ["COUNT", str(count)]

        return cast(
            Optional[Mapping[str, List[str]]],
            await self._execute_command(RequestType.LMPop, args),
        )

    async def blmpop(
        self,
        keys: List[str],
        direction: ListDirection,
        timeout: float,
        count: Optional[int] = None,
    ) -> Optional[Mapping[str, List[str]]]:
        """
        Blocks the connection until it pops one or more elements from the first non-empty list from the provided `keys`.

        `BLMPOP` is the blocking variant of `LMPOP`.

        Notes:
            1. When in cluster mode, all `keys` must map to the same hash slot.
            2. `BLMPOP` is a client blocking command, see https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands for more details and best practices.

        See https://valkey.io/commands/blmpop/ for details.

        Args:
            keys (List[str]): An array of keys of lists.
            direction (ListDirection): The direction based on which elements are popped from (`ListDirection.LEFT` or `ListDirection.RIGHT`).
            timeout (float): The number of seconds to wait for a blocking operation to complete. A value of `0` will block indefinitely.
            count (Optional[int]): The maximum number of popped elements. If not provided, defaults to popping a single element.

        Returns:
            Optional[Mapping[str, List[str]]]: A map of `key` name mapped to an array of popped elements, or None if no elements could be popped and the timeout expired.

        Examples:
            >>> await client.lpush("testKey", ["one", "two", "three"])
            >>> await client.blmpop(["testKey"], ListDirection.LEFT, 0.1, 2)
               {"testKey": ["three", "two"]}

        Since: Redis version 7.0.0.
        """
        args = [str(timeout), str(len(keys)), *keys, direction.value]
        if count is not None:
            args += ["COUNT", str(count)]

        return cast(
            Optional[Mapping[str, List[str]]],
            await self._execute_command(RequestType.BLMPop, args),
        )

    async def lrange(self, key: str, start: int, end: int) -> List[str]:
        """
        Retrieve the specified elements of the list stored at `key` within the given range.
        The offsets `start` and `end` are zero-based indexes, with 0 being the first element of the list, 1 being the next
        element and so on. These offsets can also be negative numbers indicating offsets starting at the end of the list,
        with -1 being the last element of the list, -2 being the penultimate, and so on.
        See https://redis.io/commands/lrange/ for details.

        Args:
            key (str): The key of the list.
            start (int): The starting point of the range.
            end (int): The end of the range.

        Returns:
            List[str]: A list of elements within the specified range.
            If `start` exceeds the `end` of the list, or if `start` is greater than `end`, an empty list will be returned.
            If `end` exceeds the actual end of the list, the range will stop at the actual end of the list.
            If `key` does not exist an empty list will be returned.

        Examples:
            >>> await client.lrange("my_list", 0, 2)
                ["value1", "value2", "value3"]
            >>> await client.lrange("my_list", -2, -1)
                ["value2", "value3"]
            >>> await client.lrange("non_exiting_key", 0, 2)
                []
        """
        return cast(
            List[str],
            await self._execute_command(
                RequestType.LRange, [key, str(start), str(end)]
            ),
        )

    async def lindex(
        self,
        key: str,
        index: int,
    ) -> Optional[str]:
        """
        Returns the element at `index` in the list stored at `key`.

        The index is zero-based, so 0 means the first element, 1 the second element and so on.
        Negative indices can be used to designate elements starting at the tail of the list.
        Here, -1 means the last element, -2 means the penultimate and so forth.

        See https://redis.io/commands/lindex/ for more details.

        Args:
            key (str): The key of the list.
            index (int): The index of the element in the list to retrieve.

        Returns:
            Optional[str]: The element at `index` in the list stored at `key`.
                If `index` is out of range or if `key` does not exist, None is returned.

        Examples:
            >>> await client.lindex("my_list", 0)
                'value1'  # Returns the first element in the list stored at 'my_list'.
            >>> await client.lindex("my_list", -1)
                'value3'  # Returns the last element in the list stored at 'my_list'.
        """
        return cast(
            Optional[str],
            await self._execute_command(RequestType.LIndex, [key, str(index)]),
        )

    async def lset(self, key: str, index: int, element: str) -> TOK:
        """
        Sets the list element at `index` to `element`.

        The index is zero-based, so `0` means the first element, `1` the second element and so on.
        Negative indices can be used to designate elements starting at the tail of the list.
        Here, `-1` means the last element, `-2` means the penultimate and so forth.

        See https://valkey.io/commands/lset/ for details.

        Args:
            key (str): The key of the list.
            index (int): The index of the element in the list to be set.
            element (str): The new element to set at the specified index.

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

    async def rpush(self, key: str, elements: List[str]) -> int:
        """
        Inserts all the specified values at the tail of the list stored at `key`.
        `elements` are inserted one after the other to the tail of the list, from the leftmost element
        to the rightmost element. If `key` does not exist, it is created as empty list before performing the push operations.
        See https://redis.io/commands/rpush/ for more details.

        Args:
            key (str): The key of the list.
            elements (List[str]): The elements to insert at the tail of the list stored at `key`.

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

    async def rpushx(self, key: str, elements: List[str]) -> int:
        """
        Inserts all the specified values at the tail of the list stored at `key`, only if `key` exists and holds a list.
        If `key` is not a list, this performs no operation.

        See https://redis.io/commands/rpushx/ for more details.

        Args:
            key (str): The key of the list.
            elements (List[str]): The elements to insert at the tail of the list stored at `key`.

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

    async def rpop(self, key: str, count: Optional[int] = None) -> Optional[str]:
        """
        Removes and returns the last elements of the list stored at `key`.
        The command pops a single element from the end of the list.
        See https://redis.io/commands/rpop/ for details.

        Args:
            key (str): The key of the list.

        Returns:
            Optional[str]: The value of the last element.
            If `key` does not exist, None will be returned.

        Examples:
            >>> await client.rpop("my_list")
                "value1"
            >>> await client.rpop("non_exiting_key")
                None
        """
        return cast(
            Optional[str],
            await self._execute_command(RequestType.RPop, [key]),
        )

    async def rpop_count(self, key: str, count: int) -> Optional[List[str]]:
        """
        Removes and returns up to `count` elements from the list stored at `key`, depending on the list's length.
        See https://redis.io/commands/rpop/ for details.

        Args:
            key (str): The key of the list.
            count (int): The count of elements to pop from the list.

        Returns:
            Optional[List[str]: A list of popped elements will be returned depending on the list's length.
            If `key` does not exist, None will be returned.

        Examples:
            >>> await client.rpop_count("my_list", 2)
                ["value1", "value2"]
            >>> await client.rpop_count("non_exiting_key" , 7)
                None
        """
        return cast(
            Optional[List[str]],
            await self._execute_command(RequestType.RPop, [key, str(count)]),
        )

    async def brpop(self, keys: List[str], timeout: float) -> Optional[List[str]]:
        """
        Pops an element from the tail of the first list that is non-empty, with the given keys being checked in the
        order that they are given. Blocks the connection when there are no elements to pop from any of the given lists.
        See https://valkey.io/commands/brpop for details.

        Notes:
            1. When in cluster mode, all `keys` must map to the same hash slot.
            2. `BRPOP` is a client blocking command, see https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands for more details and best practices.

        Args:
            keys (List[str]): The keys of the lists to pop from.
            timeout (float): The number of seconds to wait for a blocking operation to complete. A value of 0 will block indefinitely.

        Returns:
            Optional[List[str]]: A two-element list containing the key from which the element was popped and the value of the
                popped element, formatted as `[key, value]`. If no element could be popped and the `timeout` expired, returns None.

        Examples:
            >>> await client.brpop(["list1", "list2"], 0.5)
                ["list1", "element"]  # "element" was popped from the tail of the list with key "list1"
        """
        return cast(
            Optional[List[str]],
            await self._execute_command(RequestType.BRPop, keys + [str(timeout)]),
        )

    async def linsert(
        self, key: str, position: InsertPosition, pivot: str, element: str
    ) -> int:
        """
        Inserts `element` in the list at `key` either before or after the `pivot`.

        See https://redis.io/commands/linsert/ for details.

        Args:
            key (str): The key of the list.
            position (InsertPosition): The relative position to insert into - either `InsertPosition.BEFORE` or
                `InsertPosition.AFTER` the `pivot`.
            pivot (str): An element of the list.
            element (str): The new element to insert.

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
        source: str,
        destination: str,
        where_from: ListDirection,
        where_to: ListDirection,
    ) -> Optional[str]:
        """
        Atomically pops and removes the left/right-most element to the list stored at `source`
        depending on `where_from`, and pushes the element at the first/last element of the list
        stored at `destination` depending on `where_to`.

        When in cluster mode, both `source` and `destination` must map to the same hash slot.

        See https://valkey.io/commands/lmove/ for details.

        Args:
            source (str): The key to the source list.
            destination (str): The key to the destination list.
            where_from (ListDirection): The direction to remove the element from (`ListDirection.LEFT` or `ListDirection.RIGHT`).
            where_to (ListDirection): The direction to add the element to (`ListDirection.LEFT` or `ListDirection.RIGHT`).

        Returns:
            Optional[str]: The popped element, or None if `source` does not exist.

        Examples:
            >>> client.lpush("testKey1", ["two", "one"])
            >>> client.lpush("testKey2", ["four", "three"])
            >>> await client.lmove("testKey1", "testKey2", ListDirection.LEFT, ListDirection.LEFT)
            "one"
            >>> updated_array1 = await client.lrange("testKey1", 0, -1)
            ["two"]
            >>> await client.lrange("testKey2", 0, -1)
            ["one", "three", "four"]

        Since: Redis version 6.2.0.
        """
        return cast(
            Optional[str],
            await self._execute_command(
                RequestType.LMove,
                [source, destination, where_from.value, where_to.value],
            ),
        )

    async def blmove(
        self,
        source: str,
        destination: str,
        where_from: ListDirection,
        where_to: ListDirection,
        timeout: float,
    ) -> Optional[str]:
        """
        Blocks the connection until it pops atomically and removes the left/right-most element to the
        list stored at `source` depending on `where_from`, and pushes the element at the first/last element
        of the list stored at `destination` depending on `where_to`.
        `BLMOVE` is the blocking variant of `LMOVE`.

        Notes:
            1. When in cluster mode, both `source` and `destination` must map to the same hash slot.
            2. `BLMOVE` is a client blocking command, see https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands for more details and best practices.

        See https://valkey.io/commands/blmove/ for details.

        Args:
            source (str): The key to the source list.
            destination (str): The key to the destination list.
            where_from (ListDirection): The direction to remove the element from (`ListDirection.LEFT` or `ListDirection.RIGHT`).
            where_to (ListDirection): The direction to add the element to (`ListDirection.LEFT` or `ListDirection.RIGHT`).
            timeout (float): The number of seconds to wait for a blocking operation to complete. A value of `0` will block indefinitely.

        Returns:
            Optional[str]: The popped element, or None if `source` does not exist or if the operation timed-out.

        Examples:
            >>> await client.lpush("testKey1", ["two", "one"])
            >>> await client.lpush("testKey2", ["four", "three"])
            >>> await client.blmove("testKey1", "testKey2", ListDirection.LEFT, ListDirection.LEFT, 0.1)
            "one"
            >>> await client.lrange("testKey1", 0, -1)
            ["two"]
            >>> updated_array2 = await client.lrange("testKey2", 0, -1)
            ["one", "three", "four"]

        Since: Redis version 6.2.0.
        """
        return cast(
            Optional[str],
            await self._execute_command(
                RequestType.BLMove,
                [source, destination, where_from.value, where_to.value, str(timeout)],
            ),
        )

    async def sadd(self, key: str, members: List[str]) -> int:
        """
        Add specified members to the set stored at `key`.
        Specified members that are already a member of this set are ignored.
        If `key` does not exist, a new set is created before adding `members`.
        See https://redis.io/commands/sadd/ for more details.

        Args:
            key (str): The key where members will be added to its set.
            members (List[str]): A list of members to add to the set stored at `key`.

        Returns:
            int: The number of members that were added to the set, excluding members already present.

        Examples:
            >>> await client.sadd("my_set", ["member1", "member2"])
                2
        """
        return cast(int, await self._execute_command(RequestType.SAdd, [key] + members))

    async def srem(self, key: str, members: List[str]) -> int:
        """
        Remove specified members from the set stored at `key`.
        Specified members that are not a member of this set are ignored.
        See https://redis.io/commands/srem/ for details.

        Args:
            key (str): The key from which members will be removed.
            members (List[str]): A list of members to remove from the set stored at `key`.

        Returns:
            int: The number of members that were removed from the set, excluding non-existing members.
                If `key` does not exist, it is treated as an empty set and this command returns 0.

        Examples:
            >>> await client.srem("my_set", ["member1", "member2"])
                2
        """
        return cast(int, await self._execute_command(RequestType.SRem, [key] + members))

    async def smembers(self, key: str) -> Set[str]:
        """
        Retrieve all the members of the set value stored at `key`.
        See https://redis.io/commands/smembers/ for details.

        Args:
            key (str): The key from which to retrieve the set members.

        Returns:
            Set[str]: A set of all members of the set.
                If `key` does not exist an empty set will be returned.

        Examples:
            >>> await client.smembers("my_set")
                {"member1", "member2", "member3"}
        """
        return cast(Set[str], await self._execute_command(RequestType.SMembers, [key]))

    async def scard(self, key: str) -> int:
        """
        Retrieve the set cardinality (number of elements) of the set stored at `key`.
        See https://redis.io/commands/scard/ for details.

        Args:
            key (str): The key from which to retrieve the number of set members.

        Returns:
            int: The cardinality (number of elements) of the set, or 0 if the key does not exist.

        Examples:
            >>> await client.scard("my_set")
                3
        """
        return cast(int, await self._execute_command(RequestType.SCard, [key]))

    async def spop(self, key: str) -> Optional[str]:
        """
        Removes and returns one random member from the set stored at `key`.

        See https://valkey-io.github.io/commands/spop/ for more details.
        To pop multiple members, see `spop_count`.

        Args:
            key (str): The key of the set.

        Returns:
            Optional[str]: The value of the popped member.
            If `key` does not exist, None will be returned.

        Examples:
            >>> await client.spop("my_set")
                "value1" # Removes and returns a random member from the set "my_set".
            >>> await client.spop("non_exiting_key")
                None
        """
        return cast(Optional[str], await self._execute_command(RequestType.SPop, [key]))

    async def spop_count(self, key: str, count: int) -> Set[str]:
        """
        Removes and returns up to `count` random members from the set stored at `key`, depending on the set's length.

        See https://valkey-io.github.io/commands/spop/ for more details.
        To pop a single member, see `spop`.

        Args:
            key (str): The key of the set.
            count (int): The count of the elements to pop from the set.

        Returns:
            Set[str]: A set of popped elements will be returned depending on the set's length.
                If `key` does not exist, an empty set will be returned.

        Examples:
            >>> await client.spop_count("my_set", 2)
                {"value1", "value2"} # Removes and returns 2 random members from the set "my_set".
            >>> await client.spop_count("non_exiting_key", 2)
                Set()
        """
        return cast(
            Set[str], await self._execute_command(RequestType.SPop, [key, str(count)])
        )

    async def sismember(
        self,
        key: str,
        member: str,
    ) -> bool:
        """
        Returns if `member` is a member of the set stored at `key`.

        See https://redis.io/commands/sismember/ for more details.

        Args:
            key (str): The key of the set.
            member (str): The member to check for existence in the set.

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
        source: str,
        destination: str,
        member: str,
    ) -> bool:
        """
        Moves `member` from the set at `source` to the set at `destination`, removing it from the source set. Creates a
        new destination set if needed. The operation is atomic.

        See https://valkey.io/commands/smove for more details.

        Note:
            When in cluster mode, `source` and `destination` must map to the same hash slot.

        Args:
            source (str): The key of the set to remove the element from.
            destination (str): The key of the set to add the element to.
            member (str): The set element to move.

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

    async def sunion(self, keys: List[str]) -> Set[str]:
        """
        Gets the union of all the given sets.

        See https://valkey.io/commands/sunion for more details.

        Note:
            When in cluster mode, all `keys` must map to the same hash slot.

        Args:
            keys (List[str]): The keys of the sets.

        Returns:
            Set[str]: A set of members which are present in at least one of the given sets.
                If none of the sets exist, an empty set will be returned.

        Examples:
            >>> await client.sadd("my_set1", ["member1", "member2"])
            >>> await client.sadd("my_set2", ["member2", "member3"])
            >>> await client.sunion(["my_set1", "my_set2"])
                {"member1", "member2", "member3"} # sets "my_set1" and "my_set2" have three unique members
            >>> await client.sunion(["my_set1", "non_existing_set"])
                {"member1", "member2"}
        """
        return cast(Set[str], await self._execute_command(RequestType.SUnion, keys))

    async def sunionstore(
        self,
        destination: str,
        keys: List[str],
    ) -> int:
        """
        Stores the members of the union of all given sets specified by `keys` into a new set at `destination`.

        See https://valkey.io/commands/sunionstore for more details.

        Note:
            When in cluster mode, all keys in `keys` and `destination` must map to the same hash slot.

        Args:
            destination (str): The key of the destination set.
            keys (List[str]): The keys from which to retrieve the set members.

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

    async def sdiffstore(self, destination: str, keys: List[str]) -> int:
        """
        Stores the difference between the first set and all the successive sets in `keys` into a new set at
        `destination`.

        See https://valkey.io/commands/sdiffstore for more details.

        Note:
            When in Cluster mode, all keys in `keys` and `destination` must map to the same hash slot.

        Args:
            destination (str): The key of the destination set.
            keys (List[str]): The keys of the sets to diff.

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

    async def sinter(self, keys: List[str]) -> Set[str]:
        """
        Gets the intersection of all the given sets.

        See https://valkey.io/commands/sinter for more details.

        Note:
            When in cluster mode, all `keys` must map to the same hash slot.

        Args:
            keys (List[str]): The keys of the sets.

        Returns:
            Set[str]: A set of members which are present in all given sets.
                If one or more sets do no exist, an empty set will be returned.

        Examples:
            >>> await client.sadd("my_set1", ["member1", "member2"])
            >>> await client.sadd("my_set2", ["member2", "member3"])
            >>> await client.sinter(["my_set1", "my_set2"])
                 {"member2"} # sets "my_set1" and "my_set2" have one commom member
            >>> await client.sinter([my_set1", "non_existing_set"])
                None
        """
        return cast(Set[str], await self._execute_command(RequestType.SInter, keys))

    async def sinterstore(self, destination: str, keys: List[str]) -> int:
        """
        Stores the members of the intersection of all given sets specified by `keys` into a new set at `destination`.

        See https://valkey.io/commands/sinterstore for more details.

        Note:
            When in Cluster mode, all `keys` and `destination` must map to the same hash slot.

        Args:
            destination (str): The key of the destination set.
            keys (List[str]): The keys from which to retrieve the set members.

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

    async def sintercard(self, keys: List[str], limit: Optional[int] = None) -> int:
        """
        Gets the cardinality of the intersection of all the given sets.
        Optionally, a `limit` can be specified to stop the computation early if the intersection cardinality reaches the specified limit.

        When in cluster mode, all keys in `keys` must map to the same hash slot.

        See https://valkey.io/commands/sintercard for more details.

        Args:
            keys (List[str]): A list of keys representing the sets to intersect.
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
        args = [str(len(keys))]
        args += keys
        if limit is not None:
            args += ["LIMIT", str(limit)]
        return cast(
            int,
            await self._execute_command(RequestType.SInterCard, args),
        )

    async def sdiff(self, keys: List[str]) -> Set[str]:
        """
        Computes the difference between the first set and all the successive sets in `keys`.

        See https://valkey.io/commands/sdiff for more details.

        Note:
            When in cluster mode, all `keys` must map to the same hash slot.

        Args:
            keys (List[str]): The keys of the sets to diff.

        Returns:
            Set[str]: A set of elements representing the difference between the sets.
                If any of the keys in `keys` do not exist, they are treated as empty sets.

        Examples:
            >>> await client.sadd("set1", ["member1", "member2"])
            >>> await client.sadd("set2", ["member1"])
            >>> await client.sdiff("set1", "set2")
                {"member2"}  # "member2" is in "set1" but not "set2"
        """
        return cast(
            Set[str],
            await self._execute_command(RequestType.SDiff, keys),
        )

    async def smismember(self, key: str, members: List[str]) -> List[bool]:
        """
        Checks whether each member is contained in the members of the set stored at `key`.

        See https://valkey.io/commands/smismember for more details.

        Args:
            key (str): The key of the set to check.
            members (List[str]): A list of members to check for existence in the set.

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

    async def ltrim(self, key: str, start: int, end: int) -> TOK:
        """
        Trim an existing list so that it will contain only the specified range of elements specified.
        The offsets `start` and `end` are zero-based indexes, with 0 being the first element of the list, 1 being the next
        element and so on.
        These offsets can also be negative numbers indicating offsets starting at the end of the list, with -1 being the last
        element of the list, -2 being the penultimate, and so on.
        See https://redis.io/commands/ltrim/ for more details.

        Args:
            key (str): The key of the list.
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

    async def lrem(self, key: str, count: int, element: str) -> int:
        """
        Removes the first `count` occurrences of elements equal to `element` from the list stored at `key`.
        If `count` is positive, it removes elements equal to `element` moving from head to tail.
        If `count` is negative, it removes elements equal to `element` moving from tail to head.
        If `count` is 0 or greater than the occurrences of elements equal to `element`, it removes all elements
        equal to `element`.
        See https://redis.io/commands/lrem/ for more details.

        Args:
            key (str): The key of the list.
            count (int): The count of occurrences of elements equal to `element` to remove.
            element (str): The element to remove from the list.

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

    async def llen(self, key: str) -> int:
        """
        Get the length of the list stored at `key`.
        See https://redis.io/commands/llen/ for details.

        Args:
            key (str): The key of the list.

        Returns:
            int: The length of the list at the specified key.
                If `key` does not exist, it is interpreted as an empty list and 0 is returned.

        Examples:
            >>> await client.llen("my_list")
                3  # Indicates that there are 3 elements in the list.
        """
        return cast(int, await self._execute_command(RequestType.LLen, [key]))

    async def exists(self, keys: List[str]) -> int:
        """
        Returns the number of keys in `keys` that exist in the database.
        See https://redis.io/commands/exists/ for more details.

        Note:
            When in cluster mode, the command may route to multiple nodes when `keys` map to different hash slots.

        Args:
            keys (List[str]): The list of keys to check.

        Returns:
            int: The number of keys that exist. If the same existing key is mentioned in `keys` multiple times,
                it will be counted multiple times.

        Examples:
            >>> await client.exists(["key1", "key2", "key3"])
                3  # Indicates that all three keys exist in the database.
        """
        return cast(int, await self._execute_command(RequestType.Exists, keys))

    async def unlink(self, keys: List[str]) -> int:
        """
        Unlink (delete) multiple keys from the database.
        A key is ignored if it does not exist.
        This command, similar to DEL, removes specified keys and ignores non-existent ones.
        However, this command does not block the server, while [DEL](https://redis.io/commands/del) does.
        See https://redis.io/commands/unlink/ for more details.

        Note:
            When in cluster mode, the command may route to multiple nodes when `keys` map to different hash slots.

        Args:
            keys (List[str]): The list of keys to unlink.

        Returns:
            int: The number of keys that were unlinked.

        Examples:
            >>> await client.unlink(["key1", "key2", "key3"])
                3  # Indicates that all three keys were unlinked from the database.
        """
        return cast(int, await self._execute_command(RequestType.Unlink, keys))

    async def expire(
        self, key: str, seconds: int, option: Optional[ExpireOptions] = None
    ) -> bool:
        """
        Sets a timeout on `key` in seconds. After the timeout has expired, the key will automatically be deleted.
        If `key` already has an existing expire set, the time to live is updated to the new value.
        If `seconds` is a non-positive number, the key will be deleted rather than expired.
        The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
        See https://redis.io/commands/expire/ for more details.

        Args:
            key (str): The key to set a timeout on.
            seconds (int): The timeout in seconds.
            option (ExpireOptions, optional): The expire option.

        Returns:
            bool: 'True' if the timeout was set, 'False' if the timeout was not set (e.g., the key doesn't exist or the operation is
                skipped due to the provided arguments).

        Examples:
            >>> await client.expire("my_key", 60)
                True  # Indicates that a timeout of 60 seconds has been set for "my_key."
        """
        args: List[str] = (
            [key, str(seconds)] if option is None else [key, str(seconds), option.value]
        )
        return cast(bool, await self._execute_command(RequestType.Expire, args))

    async def expireat(
        self, key: str, unix_seconds: int, option: Optional[ExpireOptions] = None
    ) -> bool:
        """
        Sets a timeout on `key` using an absolute Unix timestamp (seconds since January 1, 1970) instead of specifying the
        number of seconds.
        A timestamp in the past will delete the key immediately. After the timeout has expired, the key will automatically be
        deleted.
        If `key` already has an existing expire set, the time to live is updated to the new value.
        The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
        See https://redis.io/commands/expireat/ for more details.

        Args:
            key (str): The key to set a timeout on.
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
        self, key: str, milliseconds: int, option: Optional[ExpireOptions] = None
    ) -> bool:
        """
        Sets a timeout on `key` in milliseconds. After the timeout has expired, the key will automatically be deleted.
        If `key` already has an existing expire set, the time to live is updated to the new value.
        If `milliseconds` is a non-positive number, the key will be deleted rather than expired.
        The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
        See https://redis.io/commands/pexpire/ for more details.

        Args:
            key (str): The key to set a timeout on.
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
        self, key: str, unix_milliseconds: int, option: Optional[ExpireOptions] = None
    ) -> bool:
        """
        Sets a timeout on `key` using an absolute Unix timestamp in milliseconds (milliseconds since January 1, 1970) instead
        of specifying the number of milliseconds.
        A timestamp in the past will delete the key immediately. After the timeout has expired, the key will automatically be
        deleted.
        If `key` already has an existing expire set, the time to live is updated to the new value.
        The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
        See https://redis.io/commands/pexpireat/ for more details.

        Args:
            key (str): The key to set a timeout on.
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

    async def expiretime(self, key: str) -> int:
        """
        Returns the absolute Unix timestamp (since January 1, 1970) at which
        the given `key` will expire, in seconds.
        To get the expiration with millisecond precision, use `pexpiretime`.

        See https://valkey.io/commands/expiretime/ for details.

        Args:
            key (str): The `key` to determine the expiration value of.

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

        Since: Redis version 7.0.0.
        """
        return cast(int, await self._execute_command(RequestType.ExpireTime, [key]))

    async def pexpiretime(self, key: str) -> int:
        """
        Returns the absolute Unix timestamp (since January 1, 1970) at which
        the given `key` will expire, in milliseconds.

        See https://valkey.io/commands/pexpiretime/ for details.

        Args:
            key (str): The `key` to determine the expiration value of.

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

        Since: Redis version 7.0.0.
        """
        return cast(int, await self._execute_command(RequestType.PExpireTime, [key]))

    async def ttl(self, key: str) -> int:
        """
        Returns the remaining time to live of `key` that has a timeout.
        See https://redis.io/commands/ttl/ for more details.

        Args:
            key (str): The key to return its timeout.

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
        key: str,
    ) -> int:
        """
        Returns the remaining time to live of `key` that has a timeout, in milliseconds.
        See https://redis.io/commands/pttl for more details.

        Args:
            key (str): The key to return its timeout.

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
        key: str,
    ) -> bool:
        """
        Remove the existing timeout on `key`, turning the key from volatile (a key with an expire set) to
        persistent (a key that will never expire as no timeout is associated).

        See https://redis.io/commands/persist/ for more details.

        Args:
            key (str): TThe key to remove the existing timeout on.

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

    async def type(self, key: str) -> str:
        """
        Returns the string representation of the type of the value stored at `key`.

        See https://redis.io/commands/type/ for more details.

        Args:
            key (str): The key to check its data type.

        Returns:
            str: If the key exists, the type of the stored value is returned.
            Otherwise, a "none" string is returned.

        Examples:
            >>> await client.set("key", "value")
            >>> await client.type("key")
                'string'
            >>> await client.lpush("key", ["value"])
            >>> await client.type("key")
                'list'
        """
        return cast(str, await self._execute_command(RequestType.Type, [key]))

    async def xadd(
        self,
        key: str,
        values: List[Tuple[str, str]],
        options: Optional[StreamAddOptions] = None,
    ) -> Optional[str]:
        """
        Adds an entry to the specified stream stored at `key`. If the `key` doesn't exist, the stream is created.

        See https://valkey.io/commands/xadd for more details.

        Args:
            key (str): The key of the stream.
            values (List[Tuple[str, str]]): Field-value pairs to be added to the entry.
            options (Optional[StreamAddOptions]): Additional options for adding entries to the stream. Default to None. sSee `StreamAddOptions`.

        Returns:
            str: The id of the added entry, or None if `options.make_stream` is set to False and no stream with the matching `key` exists.

        Example:
            >>> await client.xadd("mystream", [("field", "value"), ("field2", "value2")])
                "1615957011958-0"  # Example stream entry ID.
            >>> await client.xadd("non_existing_stream", [(field, "foo1"), (field2, "bar1")], StreamAddOptions(id="0-1", make_stream=False))
                None  # The key doesn't exist, therefore, None is returned.
            >>> await client.xadd("non_existing_stream", [(field, "foo1"), (field2, "bar1")], StreamAddOptions(id="0-1"))
                "0-1"  # Returns the stream id.
        """
        args = [key]
        if options:
            args.extend(options.to_args())
        else:
            args.append("*")
        args.extend([field for pair in values for field in pair])

        return cast(Optional[str], await self._execute_command(RequestType.XAdd, args))

    async def xdel(self, key: str, ids: List[str]) -> int:
        """
        Removes the specified entries by id from a stream, and returns the number of entries deleted.

        See https://valkey.io/commands/xdel for more details.

        Args:
            key (str): The key of the stream.
            ids (List[str]): An array of entry ids.

        Returns:
            int: The number of entries removed from the stream. This number may be less than the number of entries in
                `ids`, if the specified `ids` don't exist in the stream.

        Examples:
            >>> await client.xdel("key", ["1538561698944-0", "1538561698944-1"])
                2  # Stream marked 2 entries as deleted.
        """
        return cast(
            int,
            await self._execute_command(RequestType.XDel, [key] + ids),
        )

    async def xtrim(
        self,
        key: str,
        options: StreamTrimOptions,
    ) -> int:
        """
        Trims the stream stored at `key` by evicting older entries.

        See https://valkey.io/commands/xtrim for more details.

        Args:
            key (str): The key of the stream.
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

    async def xlen(self, key: str) -> int:
        """
        Returns the number of entries in the stream stored at `key`.

        See https://valkey.io/commands/xlen for more details.

        Args:
            key (str): The key of the stream.

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
        key: str,
        start: StreamRangeBound,
        end: StreamRangeBound,
        count: Optional[int] = None,
    ) -> Optional[Mapping[str, List[List[str]]]]:
        """
        Returns stream entries matching a given range of IDs.

        See https://valkey.io/commands/xrange for more details.

        Args:
            key (str): The key of the stream.
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
            Optional[Mapping[str, List[List[str]]]]: A mapping of stream IDs to stream entry data, where entry data is a
                list of pairings with format `[[field, entry], [field, entry], ...]`. Returns None if the range
                arguments are not applicable.

        Examples:
            >>> await client.xadd("mystream", [("field1", "value1")], StreamAddOptions(id="0-1"))
            >>> await client.xadd("mystream", [("field2", "value2"), ("field2", "value3")], StreamAddOptions(id="0-2"))
            >>> await client.xrange("mystream", MinId(), MaxId())
                {
                    "0-1": [["field1", "value1"]],
                    "0-2": [["field2", "value2"], ["field2", "value3"]],
                }  # Indicates the stream IDs and their associated field-value pairs for all stream entries in "mystream".
        """
        args = [key, start.to_arg(), end.to_arg()]
        if count is not None:
            args.extend(["COUNT", str(count)])

        return cast(
            Optional[Mapping[str, List[List[str]]]],
            await self._execute_command(RequestType.XRange, args),
        )

    async def xrevrange(
        self,
        key: str,
        end: StreamRangeBound,
        start: StreamRangeBound,
        count: Optional[int] = None,
    ) -> Optional[Mapping[str, List[List[str]]]]:
        """
        Returns stream entries matching a given range of IDs in reverse order. Equivalent to `XRANGE` but returns the
        entries in reverse order.

        See https://valkey.io/commands/xrevrange for more details.

        Args:
            key (str): The key of the stream.
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
            Optional[Mapping[str, List[List[str]]]]: A mapping of stream IDs to stream entry data, where entry data is a
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
        args = [key, end.to_arg(), start.to_arg()]
        if count is not None:
            args.extend(["COUNT", str(count)])

        return cast(
            Optional[Mapping[str, List[List[str]]]],
            await self._execute_command(RequestType.XRevRange, args),
        )

    async def xread(
        self,
        keys_and_ids: Mapping[str, str],
        options: Optional[StreamReadOptions] = None,
    ) -> Optional[Mapping[str, Mapping[str, List[List[str]]]]]:
        """
        Reads entries from the given streams.

        See https://valkey.io/commands/xread for more details.

        Note:
            When in cluster mode, all keys in `keys_and_ids` must map to the same hash slot.

        Args:
            keys_and_ids (Mapping[str, str]): A mapping of keys and entry IDs to read from. The mapping is composed of a
                stream's key and the ID of the entry after which the stream will be read.
            options (Optional[StreamReadOptions]): Options detailing how to read the stream.

        Returns:
            Optional[Mapping[str, Mapping[str, List[List[str]]]]]: A mapping of stream keys, to a mapping of stream IDs,
                to a list of pairings with format `[[field, entry], [field, entry], ...]`.
                None will be returned under the following conditions:
                - All key-ID pairs in `keys_and_ids` have either a non-existing key or a non-existing ID, or there are no entries after the given ID.
                - The `BLOCK` option is specified and the timeout is hit.

        Examples:
            >>> await client.xadd("mystream", [("field1", "value1")], StreamAddOptions(id="0-1"))
            >>> await client.xadd("mystream", [("field2", "value2"), ("field2", "value3")], StreamAddOptions(id="0-2"))
            >>> await client.xread({"mystream": "0-0"}, StreamReadOptions(block_ms=1000))
                {
                    "mystream": {
                        "0-1": [["field1", "value1"]],
                        "0-2": [["field2", "value2"], ["field2", "value3"]],
                    }
                }
                # Indicates the stream entries for "my_stream" with IDs greater than "0-0". The operation blocks up to
                # 1000ms if there is no stream data.
        """
        args = [] if options is None else options.to_args()
        args.append("STREAMS")
        args.extend([key for key in keys_and_ids.keys()])
        args.extend([value for value in keys_and_ids.values()])

        return cast(
            Optional[Mapping[str, Mapping[str, List[List[str]]]]],
            await self._execute_command(RequestType.XRead, args),
        )

    async def xgroup_create(
        self,
        key: str,
        group_name: str,
        group_id: str,
        options: Optional[StreamGroupOptions] = None,
    ) -> TOK:
        """
        Creates a new consumer group uniquely identified by `group_name` for the stream stored at `key`.

        See https://valkey.io/commands/xgroup-create for more details.

        Args:
            key (str): The key of the stream.
            group_name (str): The newly created consumer group name.
            group_id (str): The stream entry ID that specifies the last delivered entry in the stream from the new
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
        args = [key, group_name, group_id]
        if options is not None:
            args.extend(options.to_args())

        return cast(
            TOK,
            await self._execute_command(RequestType.XGroupCreate, args),
        )

    async def xgroup_destroy(self, key: str, group_name: str) -> bool:
        """
        Destroys the consumer group `group_name` for the stream stored at `key`.

        See https://valkey.io/commands/xgroup-destroy for more details.

        Args:
            key (str): The key of the stream.
            group_name (str): The consumer group name to delete.

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
        self, key: str, group_name: str, consumer_name: str
    ) -> bool:
        """
        Creates a consumer named `consumer_name` in the consumer group `group_name` for the stream stored at `key`.

        See https://valkey.io/commands/xgroup-createconsumer for more details.

        Args:
            key (str): The key of the stream.
            group_name (str): The consumer group name.
            consumer_name (str): The newly created consumer.

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
        self, key: str, group_name: str, consumer_name: str
    ) -> int:
        """
        Deletes a consumer named `consumer_name` in the consumer group `group_name` for the stream stored at `key`.

        See https://valkey.io/commands/xgroup-delconsumer for more details.

        Args:
            key (str): The key of the stream.
            group_name (str): The consumer group name.
            consumer_name (str): The consumer to delete.

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
        key: str,
        group_name: str,
        stream_id: str,
        entries_read_id: Optional[str] = None,
    ) -> TOK:
        """
        Set the last delivered ID for a consumer group.

        See https://valkey.io/commands/xgroup-setid for more details.

        Args:
            key (str): The key of the stream.
            group_name (str): The consumer group name.
            stream_id (str): The stream entry ID that should be set as the last delivered ID for the consumer group.
            entries_read_id (Optional[str]): An arbitrary ID (that isn't the first ID, last ID, or the zero ID ("0-0"))
                used to find out how many entries are between the arbitrary ID (excluding it) and the stream's last
                entry. This argument can only be specified if you are using Redis version 7.0.0 or above.

        Returns:
            TOK: A simple "OK" response.

        Examples:
            >>> await client.xgroup_set_id("mystream", "mygroup", "0")
                OK  # The last delivered ID for consumer group "mygroup" was set to 0.
        """
        args = [key, group_name, stream_id]
        if entries_read_id is not None:
            args.extend(["ENTRIESREAD", entries_read_id])

        return cast(
            TOK,
            await self._execute_command(RequestType.XGroupSetId, args),
        )

    async def xreadgroup(
        self,
        keys_and_ids: Mapping[str, str],
        group_name: str,
        consumer_name: str,
        options: Optional[StreamReadGroupOptions] = None,
    ) -> Optional[Mapping[str, Mapping[str, Optional[List[List[str]]]]]]:
        """
        Reads entries from the given streams owned by a consumer group.

        See https://valkey.io/commands/xreadgroup for more details.

        Note:
            When in cluster mode, all keys in `keys_and_ids` must map to the same hash slot.

        Args:
            keys_and_ids (Mapping[str, str]): A mapping of stream keys to stream entry IDs to read from. The special ">"
                ID returns messages that were never delivered to any other consumer. Any other valid ID will return
                entries pending for the consumer with IDs greater than the one provided.
            group_name (str): The consumer group name.
            consumer_name (str): The consumer name. The consumer will be auto-created if it does not already exist.
            options (Optional[StreamReadGroupOptions]): Options detailing how to read the stream.

        Returns:
            Optional[Mapping[str, Mapping[str, Optional[List[List[str]]]]]]: A mapping of stream keys, to a mapping of
                stream IDs, to a list of pairings with format `[[field, entry], [field, entry], ...]`.
                Returns None if the BLOCK option is given and a timeout occurs, or if there is no stream that can be served.

        Examples:
            >>> await client.xadd("mystream", [("field1", "value1")], StreamAddOptions(id="1-0"))
            >>> await client.xgroup_create("mystream", "mygroup", "0-0")
            >>> await client.xreadgroup({"mystream": ">"}, "mygroup", "myconsumer", StreamReadGroupOptions(count=1))
                {
                    "mystream": {
                        "1-0": [["field1", "value1"]],
                    }
                }  # Read one stream entry from "mystream" using "myconsumer" in the consumer group "mygroup".
        """
        args = ["GROUP", group_name, consumer_name]
        if options is not None:
            args.extend(options.to_args())

        args.append("STREAMS")
        args.extend([key for key in keys_and_ids.keys()])
        args.extend([value for value in keys_and_ids.values()])

        return cast(
            Optional[Mapping[str, Mapping[str, Optional[List[List[str]]]]]],
            await self._execute_command(RequestType.XReadGroup, args),
        )

    async def xack(
        self,
        key: str,
        group_name: str,
        ids: List[str],
    ) -> int:
        """
        Removes one or multiple messages from the Pending Entries List (PEL) of a stream consumer group.
        This command should be called on pending messages so that such messages do not get processed again by the
        consumer group.

        See https://valkey.io/commands/xack for more details.

        Args:
            key (str): The key of the stream.
            group_name (str): The consumer group name.
            ids (List[str]): The stream entry IDs to acknowledge and consume for the given consumer group.

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

        return cast(
            int,
            await self._execute_command(RequestType.XAck, [key, group_name] + ids),
        )

    async def xpending(
        self,
        key: str,
        group_name: str,
    ) -> List[Union[int, str, List[List[str]], None]]:
        """
        Returns stream message summary information for pending messages for the given consumer group.

        See https://valkey.io/commands/xpending for more details.

        Args:
            key (str): The key of the stream.
            group_name (str): The consumer group name.

        Returns:
            List[Union[int, str, List[List[str]], None]]: A list that includes the summary of pending messages, with the
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
            List[Union[int, str, List[List[str]], None]],
            await self._execute_command(RequestType.XPending, [key, group_name]),
        )

    async def xpending_range(
        self,
        key: str,
        group_name: str,
        start: StreamRangeBound,
        end: StreamRangeBound,
        count: int,
        options: Optional[StreamPendingOptions] = None,
    ) -> List[List[Union[str, int]]]:
        """
        Returns an extended form of stream message information for pending messages matching a given range of IDs.

        See https://valkey.io/commands/xpending for more details.

        Args:
            key (str): The key of the stream.
            group_name (str): The consumer group name.
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
            List[List[Union[str, int]]]: A list of lists, where each inner list is a length 4 list containing extended
                message information with the format `[[id, consumer_name, time_elapsed, num_delivered]]`, where:
                - `id`: The ID of the message.
                - `consumer_name`: The name of the consumer that fetched the message and has still to acknowledge it. We
                call it the current owner of the message.
                - `time_elapsed`: The number of milliseconds that elapsed since the last time this message was delivered
                to this consumer.
                - `num_delivered`: The number of times this message was delivered.

        Examples:
            >>> await client.xpending_range("my_stream", "my_group", MinId(), MaxId(), 10, StreamPendingOptions(consumer_name="my_consumer"))
                [["1-0", "my_consumer", 1234, 1], ["1-1", "my_consumer", 1123, 1]]
                # Extended stream entry information for the pending entries associated with "my_consumer".
        """
        args = _create_xpending_range_args(key, group_name, start, end, count, options)
        return cast(
            List[List[Union[str, int]]],
            await self._execute_command(RequestType.XPending, args),
        )

    async def xautoclaim(
        self,
        key: str,
        group_name: str,
        consumer_name: str,
        min_idle_time_ms: int,
        start: str,
        count: Optional[int] = None,
    ) -> List[Union[str, Mapping[str, List[List[str]]], List[str]]]:
        """
        Transfers ownership of pending stream entries that match the specified criteria.

        See https://valkey.io/commands/xautoclaim for more details.

        Args:
            key (str): The key of the stream.
            group_name (str): The consumer group name.
            consumer_name (str): The consumer name.
            min_idle_time_ms (int): Filters the claimed entries to those that have been idle for more than the specified
                value.
            start (str): Filters the claimed entries to those that have an ID equal or greater than the specified value.
            count (Optional[int]): Limits the number of claimed entries to the specified value.

        Returns:
            List[Union[str, Mapping[str, List[List[str]]], List[str]]]: A list containing the following elements:
                - A stream ID to be used as the start argument for the next call to `XAUTOCLAIM`. This ID is equivalent
                to the next ID in the stream after the entries that were scanned, or "0-0" if the entire stream was
                scanned.
                - A mapping of the claimed entries, with the keys being the claimed entry IDs and the values being a
                2D list of the field-value pairs in the format `[[field1, value1], [field2, value2], ...]`.
                - If you are using Redis 7.0.0 or above, the response list will also include a list containing the
                message IDs that were in the Pending Entries List but no longer exist in the stream. These IDs are
                deleted from the Pending Entries List.

        Examples:
            # Redis version < 7.0.0:
            >>> await client.xautoclaim("my_stream", "my_group", "my_consumer", 3_600_000, "0-0")
                [
                    "0-0",
                    {
                        "1-1": [
                            ["field1", "value1"],
                            ["field2", "value2"],
                        ]
                    }
                ]
                # Stream entry "1-1" was idle for over an hour and was thus claimed by "my_consumer". The entire stream
                # was scanned.

            # Redis version 7.0.0 and above:
            >>> await client.xautoclaim("my_stream", "my_group", "my_consumer", 3_600_000, "0-0")
                [
                    "0-0",
                    {
                        "1-1": [
                            ["field1", "value1"],
                            ["field2", "value2"],
                        ]
                    },
                    ["1-2"]
                ]
                # Stream entry "1-1" was idle for over an hour and was thus claimed by "my_consumer". The entire stream
                # was scanned. Additionally, entry "1-2" was removed from the Pending Entries List because it no longer
                # exists in the stream.

        Since: Redis version 6.2.0.
        """
        args = [key, group_name, consumer_name, str(min_idle_time_ms), start]
        if count is not None:
            args.extend(["COUNT", str(count)])

        return cast(
            List[Union[str, Mapping[str, List[List[str]]], List[str]]],
            await self._execute_command(RequestType.XAutoClaim, args),
        )

    async def xautoclaim_just_id(
        self,
        key: str,
        group_name: str,
        consumer_name: str,
        min_idle_time_ms: int,
        start: str,
        count: Optional[int] = None,
    ) -> List[Union[str, List[str]]]:
        """
        Transfers ownership of pending stream entries that match the specified criteria. This command uses the JUSTID
        argument to further specify that the return value should contain a list of claimed IDs without their
        field-value info.

        See https://valkey.io/commands/xautoclaim for more details.

        Args:
            key (str): The key of the stream.
            group_name (str): The consumer group name.
            consumer_name (str): The consumer name.
            min_idle_time_ms (int): Filters the claimed entries to those that have been idle for more than the specified
                value.
            start (str): Filters the claimed entries to those that have an ID equal or greater than the specified value.
            count (Optional[int]): Limits the number of claimed entries to the specified value.

        Returns:
            List[Union[str, List[str]]]: A list containing the following elements:
                - A stream ID to be used as the start argument for the next call to `XAUTOCLAIM`. This ID is equivalent
                to the next ID in the stream after the entries that were scanned, or "0-0" if the entire stream was
                scanned.
                - A list of the IDs for the claimed entries.
                - If you are using Redis 7.0.0 or above, the response list will also include a list containing the
                message IDs that were in the Pending Entries List but no longer exist in the stream. These IDs are
                deleted from the Pending Entries List.

        Examples:
            # Redis version < 7.0.0:
            >>> await client.xautoclaim_just_id("my_stream", "my_group", "my_consumer", 3_600_000, "0-0")
                ["0-0", ["1-1"]]
                # Stream entry "1-1" was idle for over an hour and was thus claimed by "my_consumer". The entire stream
                # was scanned.

            # Redis version 7.0.0 and above:
            >>> await client.xautoclaim_just_id("my_stream", "my_group", "my_consumer", 3_600_000, "0-0")
                ["0-0", ["1-1"], ["1-2"]]
                # Stream entry "1-1" was idle for over an hour and was thus claimed by "my_consumer". The entire stream
                # was scanned. Additionally, entry "1-2" was removed from the Pending Entries List because it no longer
                # exists in the stream.

        Since: Redis version 6.2.0.
        """
        args = [key, group_name, consumer_name, str(min_idle_time_ms), start]
        if count is not None:
            args.extend(["COUNT", str(count)])

        args.append("JUSTID")

        return cast(
            List[Union[str, List[str]]],
            await self._execute_command(RequestType.XAutoClaim, args),
        )

    async def geoadd(
        self,
        key: str,
        members_geospatialdata: Mapping[str, GeospatialData],
        existing_options: Optional[ConditionalChange] = None,
        changed: bool = False,
    ) -> int:
        """
        Adds geospatial members with their positions to the specified sorted set stored at `key`.
        If a member is already a part of the sorted set, its position is updated.

        See https://valkey.io/commands/geoadd for more details.

        Args:
            key (str): The key of the sorted set.
            members_geospatialdata (Mapping[str, GeospatialData]): A mapping of member names to their corresponding positions. See `GeospatialData`.
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
        key: str,
        member1: str,
        member2: str,
        unit: Optional[GeoUnit] = None,
    ) -> Optional[float]:
        """
        Returns the distance between two members in the geospatial index stored at `key`.

        See https://valkey.io/commands/geodist for more details.

        Args:
            key (str): The key of the sorted set.
            member1 (str): The name of the first member.
            member2 (str): The name of the second member.
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

    async def geohash(self, key: str, members: List[str]) -> List[Optional[str]]:
        """
        Returns the GeoHash strings representing the positions of all the specified members in the sorted set stored at
        `key`.

        See https://valkey.io/commands/geohash for more details.

        Args:
            key (str): The key of the sorted set.
            members (List[str]): The list of members whose GeoHash strings are to be retrieved.

        Returns:
            List[Optional[str]]: A list of GeoHash strings representing the positions of the specified members stored at `key`.
            If a member does not exist in the sorted set, a None value is returned for that member.

        Examples:
            >>> await client.geoadd("my_geo_sorted_set", {"Palermo": GeospatialData(13.361389, 38.115556), "Catania": GeospatialData(15.087269, 37.502669)})
            >>> await client.geohash("my_geo_sorted_set", ["Palermo", "Catania", "some city])
                ["sqc8b49rny0", "sqdtr74hyu0", None]  # Indicates the GeoHash strings for the specified members.
        """
        return cast(
            List[Optional[str]],
            await self._execute_command(RequestType.GeoHash, [key] + members),
        )

    async def geopos(
        self,
        key: str,
        members: List[str],
    ) -> List[Optional[List[float]]]:
        """
        Returns the positions (longitude and latitude) of all the given members of a geospatial index in the sorted set stored at
        `key`.

        See https://valkey.io/commands/geopos for more details.

        Args:
            key (str): The key of the sorted set.
            members (List[str]): The members for which to get the positions.

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
        key: str,
        search_from: Union[str, GeospatialData],
        seach_by: Union[GeoSearchByRadius, GeoSearchByBox],
        order_by: Optional[OrderBy] = None,
        count: Optional[GeoSearchCount] = None,
        with_coord: bool = False,
        with_dist: bool = False,
        with_hash: bool = False,
    ) -> List[Union[str, List[Union[str, float, int, List[float]]]]]:
        """
        Searches for members in a sorted set stored at `key` representing geospatial data within a circular or rectangular area.

        See https://valkey.io/commands/geosearch/ for more details.

        Args:
            key (str): The key of the sorted set representing geospatial data.
            search_from (Union[str, GeospatialData]): The location to search from. Can be specified either as a member
                from the sorted set or as a geospatial data (see `GeospatialData`).
            search_by (Union[GeoSearchByRadius, GeoSearchByBox]): The search criteria.
                For circular area search, see `GeoSearchByRadius`.
                For rectengal area search, see `GeoSearchByBox`.
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
            List[Union[str, List[Union[str, float, int, List[float]]]]]: By default, returns a list of members (locations) names.
            If any of `with_coord`, `with_dist` or `with_hash` are True, returns an array of arrays, we're each sub array represents a single item in the following order:
                (str): The member (location) name.
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
                        "Catania",
                        [56.4413, 3479447370796909, [15.087267458438873, 37.50266842333162]],
                    ],
                    [
                        "Palermo",
                        [190.4424, 3479099956230698, [13.361389338970184, 38.1155563954963]],
                    ],
                    [
                        "edge2",
                        [279.7403, 3481342659049484, [17.241510450839996, 38.78813451624225]],
                    ],
                    [
                        "edge1",
                        [279.7405, 3479273021651468, [12.75848776102066, 38.78813451624225]],
                    ],
                ]  # Returns locations within the square box of 400 km, with the center being a specific point, from nearest to farthest with the dist, hash and coords.

        Since: Redis version 6.2.0.
        """
        args = _create_geosearch_args(
            [key],
            search_from,
            seach_by,
            order_by,
            count,
            with_coord,
            with_dist,
            with_hash,
        )

        return cast(
            List[Union[str, List[Union[str, float, int, List[float]]]]],
            await self._execute_command(RequestType.GeoSearch, args),
        )

    async def geosearchstore(
        self,
        destination: str,
        source: str,
        search_from: Union[str, GeospatialData],
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
            destination (str): The key to store the search results.
            source (str): The key of the sorted set representing geospatial data to search from.
            search_from (Union[str, GeospatialData]): The location to search from. Can be specified either as a member
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
                {"Palermo": 3479099956230698.0, "Catania": 3479447370796909.0} # The elements within te search area, with their geohash as score.
            >>> await client.geosearchstore("my_dest_sorted_set", "my_geo_sorted_set", GeospatialData(15, 37), GeoSearchByBox(400, 400, GeoUnit.KILOMETERS), store_dist=True)
                2 # Number of elements stored in "my_dest_sorted_set", with distance as score.
            >>> await client.zrange_withscores("my_dest_sorted_set", RangeByIndex(0, -1))
                {"Catania": 56.4412578701582, "Palermo": 190.44242984775784} # The elements within te search area, with the distance as score.

        Since: Redis version 6.2.0.
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
        key: str,
        members_scores: Mapping[str, float],
        existing_options: Optional[ConditionalChange] = None,
        update_condition: Optional[UpdateOptions] = None,
        changed: bool = False,
    ) -> int:
        """
        Adds members with their scores to the sorted set stored at `key`.
        If a member is already a part of the sorted set, its score is updated.

        See https://redis.io/commands/zadd/ for more details.

        Args:
            key (str): The key of the sorted set.
            members_scores (Mapping[str, float]): A mapping of members to their corresponding scores.
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
        key: str,
        member: str,
        increment: float,
        existing_options: Optional[ConditionalChange] = None,
        update_condition: Optional[UpdateOptions] = None,
    ) -> Optional[float]:
        """
        Increments the score of member in the sorted set stored at `key` by `increment`.
        If `member` does not exist in the sorted set, it is added with `increment` as its score (as if its previous score was 0.0).
        If `key` does not exist, a new sorted set with the specified member as its sole member is created.

        See https://redis.io/commands/zadd/ for more details.

        Args:
            key (str): The key of the sorted set.
            member (str): A member in the sorted set to increment.
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

    async def zcard(self, key: str) -> int:
        """
        Returns the cardinality (number of elements) of the sorted set stored at `key`.

        See https://redis.io/commands/zcard/ for more details.

        Args:
            key (str): The key of the sorted set.

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
        key: str,
        min_score: Union[InfBound, ScoreBoundary],
        max_score: Union[InfBound, ScoreBoundary],
    ) -> int:
        """
        Returns the number of members in the sorted set stored at `key` with scores between `min_score` and `max_score`.

        See https://redis.io/commands/zcount/ for more details.

        Args:
            key (str): The key of the sorted set.
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

    async def zincrby(self, key: str, increment: float, member: str) -> float:
        """
        Increments the score of `member` in the sorted set stored at `key` by `increment`.
        If `member` does not exist in the sorted set, it is added with `increment` as its score.
        If `key` does not exist, a new sorted set is created with the specified member as its sole member.

        See https://valkey.io/commands/zincrby/ for more details.

        Args:
            key (str): The key of the sorted set.
            increment (float): The score increment.
            member (str): A member of the sorted set.

        Returns:
            float: The new score of `member`.

        Examples:
            >>> await client.zadd("my_sorted_set", {"member": 10.5, "member2": 8.2})
            >>> await client.zincrby("my_sorted_set", 1.2, "member")
                11.7  # The member existed in the set before score was altered, the new score is 11.7.
            >>> await client.zincrby("my_sorted_set", -1.7, "member")
                10.0 # Negetive increment, decrements the score.
            >>> await client.zincrby("my_sorted_set", 5.5, "non_existing_member")
                5.5  # A new memeber is added to the sorted set with the score being 5.5.
        """
        return cast(
            float,
            await self._execute_command(
                RequestType.ZIncrBy, [key, str(increment), member]
            ),
        )

    async def zpopmax(
        self, key: str, count: Optional[int] = None
    ) -> Mapping[str, float]:
        """
        Removes and returns the members with the highest scores from the sorted set stored at `key`.
        If `count` is provided, up to `count` members with the highest scores are removed and returned.
        Otherwise, only one member with the highest score is removed and returned.

        See https://redis.io/commands/zpopmax for more details.

        Args:
            key (str): The key of the sorted set.
            count (Optional[int]): Specifies the quantity of members to pop. If not specified, pops one member.
            If `count` is higher than the sorted set's cardinality, returns all members and their scores, ordered from highest to lowest.

        Returns:
            Mapping[str, float]: A map of the removed members and their scores, ordered from the one with the highest score to the one with the lowest.
            If `key` doesn't exist, it will be treated as an empy sorted set and the command returns an empty map.

        Examples:
            >>> await client.zpopmax("my_sorted_set")
                {'member1': 10.0}  # Indicates that 'member1' with a score of 10.0 has been removed from the sorted set.
            >>> await client.zpopmax("my_sorted_set", 2)
                {'member2': 8.0, 'member3': 7.5}  # Indicates that 'member2' with a score of 8.0 and 'member3' with a score of 7.5 have been removed from the sorted set.
        """
        return cast(
            Mapping[str, float],
            await self._execute_command(
                RequestType.ZPopMax, [key, str(count)] if count else [key]
            ),
        )

    async def bzpopmax(
        self, keys: List[str], timeout: float
    ) -> Optional[List[Union[str, float]]]:
        """
        Pops the member with the highest score from the first non-empty sorted set, with the given keys being checked in
        the order that they are given. Blocks the connection when there are no members to remove from any of the given
        sorted sets.

        When in cluster mode, all keys must map to the same hash slot.

        `BZPOPMAX` is the blocking variant of `ZPOPMAX`.

        `BZPOPMAX` is a client blocking command, see https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands for more details and best practices.

        See https://valkey.io/commands/bzpopmax for more details.

        Args:
            keys (List[str]): The keys of the sorted sets.
            timeout (float): The number of seconds to wait for a blocking operation to complete.
                A value of 0 will block indefinitely.

        Returns:
            Optional[List[Union[str, float]]]: An array containing the key where the member was popped out, the member itself,
                and the member score. If no member could be popped and the `timeout` expired, returns None.

        Examples:
            >>> await client.zadd("my_sorted_set1", {"member1": 10.0, "member2": 5.0})
                2  # Two elements have been added to the sorted set at "my_sorted_set1".
            >>> await client.bzpopmax(["my_sorted_set1", "my_sorted_set2"], 0.5)
                ['my_sorted_set1', 'member1', 10.0]  # "member1" with a score of 10.0 has been removed from "my_sorted_set1".
        """
        return cast(
            Optional[List[Union[str, float]]],
            await self._execute_command(RequestType.BZPopMax, keys + [str(timeout)]),
        )

    async def zpopmin(
        self, key: str, count: Optional[int] = None
    ) -> Mapping[str, float]:
        """
        Removes and returns the members with the lowest scores from the sorted set stored at `key`.
        If `count` is provided, up to `count` members with the lowest scores are removed and returned.
        Otherwise, only one member with the lowest score is removed and returned.

        See https://redis.io/commands/zpopmin for more details.

        Args:
            key (str): The key of the sorted set.
            count (Optional[int]): Specifies the quantity of members to pop. If not specified, pops one member.
            If `count` is higher than the sorted set's cardinality, returns all members and their scores.

        Returns:
            Mapping[str, float]: A map of the removed members and their scores, ordered from the one with the lowest score to the one with the highest.
            If `key` doesn't exist, it will be treated as an empy sorted set and the command returns an empty map.

        Examples:
            >>> await client.zpopmin("my_sorted_set")
                {'member1': 5.0}  # Indicates that 'member1' with a score of 5.0 has been removed from the sorted set.
            >>> await client.zpopmin("my_sorted_set", 2)
                {'member3': 7.5 , 'member2': 8.0}  # Indicates that 'member3' with a score of 7.5 and 'member2' with a score of 8.0 have been removed from the sorted set.
        """
        return cast(
            Mapping[str, float],
            await self._execute_command(
                RequestType.ZPopMin, [key, str(count)] if count else [key]
            ),
        )

    async def bzpopmin(
        self, keys: List[str], timeout: float
    ) -> Optional[List[Union[str, float]]]:
        """
        Pops the member with the lowest score from the first non-empty sorted set, with the given keys being checked in
        the order that they are given. Blocks the connection when there are no members to remove from any of the given
        sorted sets.

        When in cluster mode, all keys must map to the same hash slot.

        `BZPOPMIN` is the blocking variant of `ZPOPMIN`.

        `BZPOPMIN` is a client blocking command, see https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands for more details and best practices.

        See https://valkey.io/commands/bzpopmin for more details.

        Args:
            keys (List[str]): The keys of the sorted sets.
            timeout (float): The number of seconds to wait for a blocking operation to complete.
                A value of 0 will block indefinitely.

        Returns:
            Optional[List[Union[str, float]]]: An array containing the key where the member was popped out, the member itself,
                and the member score. If no member could be popped and the `timeout` expired, returns None.

        Examples:
            >>> await client.zadd("my_sorted_set1", {"member1": 10.0, "member2": 5.0})
                2  # Two elements have been added to the sorted set at "my_sorted_set1".
            >>> await client.bzpopmin(["my_sorted_set1", "my_sorted_set2"], 0.5)
                ['my_sorted_set1', 'member2', 5.0]  # "member2" with a score of 5.0 has been removed from "my_sorted_set1".
        """
        return cast(
            Optional[List[Union[str, float]]],
            await self._execute_command(RequestType.BZPopMin, keys + [str(timeout)]),
        )

    async def zrange(
        self,
        key: str,
        range_query: Union[RangeByIndex, RangeByLex, RangeByScore],
        reverse: bool = False,
    ) -> List[str]:
        """
        Returns the specified range of elements in the sorted set stored at `key`.

        ZRANGE can perform different types of range queries: by index (rank), by the score, or by lexicographical order.

        See https://redis.io/commands/zrange/ for more details.

        To get the elements with their scores, see zrange_withscores.

        Args:
            key (str): The key of the sorted set.
            range_query (Union[RangeByIndex, RangeByLex, RangeByScore]): The range query object representing the type of range query to perform.
                - For range queries by index (rank), use RangeByIndex.
                - For range queries by lexicographical order, use RangeByLex.
                - For range queries by score, use RangeByScore.
            reverse (bool): If True, reverses the sorted set, with index 0 as the element with the highest score.

        Returns:
            List[str]: A list of elements within the specified range.
            If `key` does not exist, it is treated as an empty sorted set, and the command returns an empty array.

        Examples:
            >>> await client.zrange("my_sorted_set", RangeByIndex(0, -1))
                ['member1', 'member2', 'member3']  # Returns all members in ascending order.
            >>> await client.zrange("my_sorted_set", RangeByScore(start=InfBound.NEG_INF, stop=ScoreBoundary(3)))
                ['member2', 'member3'] # Returns members with scores within the range of negative infinity to 3, in ascending order.
        """
        args = _create_zrange_args(key, range_query, reverse, with_scores=False)

        return cast(List[str], await self._execute_command(RequestType.ZRange, args))

    async def zrange_withscores(
        self,
        key: str,
        range_query: Union[RangeByIndex, RangeByScore],
        reverse: bool = False,
    ) -> Mapping[str, float]:
        """
        Returns the specified range of elements with their scores in the sorted set stored at `key`.
        Similar to ZRANGE but with a WITHSCORE flag.

        See https://redis.io/commands/zrange/ for more details.

        Args:
            key (str): The key of the sorted set.
            range_query (Union[RangeByIndex, RangeByScore]): The range query object representing the type of range query to perform.
                - For range queries by index (rank), use RangeByIndex.
                - For range queries by score, use RangeByScore.
            reverse (bool): If True, reverses the sorted set, with index 0 as the element with the highest score.

        Returns:
            Mapping[str , float]: A map of elements and their scores within the specified range.
            If `key` does not exist, it is treated as an empty sorted set, and the command returns an empty map.

        Examples:
            >>> await client.zrange_withscores("my_sorted_set", RangeByScore(ScoreBoundary(10), ScoreBoundary(20)))
                {'member1': 10.5, 'member2': 15.2}  # Returns members with scores between 10 and 20 with their scores.
           >>> await client.zrange_withscores("my_sorted_set", RangeByScore(start=InfBound.NEG_INF, stop=ScoreBoundary(3)))
                {'member4': -2.0, 'member7': 1.5} # Returns members with with scores within the range of negative infinity to 3, with their scores.
        """
        args = _create_zrange_args(key, range_query, reverse, with_scores=True)

        return cast(
            Mapping[str, float], await self._execute_command(RequestType.ZRange, args)
        )

    async def zrangestore(
        self,
        destination: str,
        source: str,
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
            destination (str): The key for the destination sorted set.
            source (str): The key of the source sorted set.
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
        key: str,
        member: str,
    ) -> Optional[int]:
        """
        Returns the rank of `member` in the sorted set stored at `key`, with scores ordered from low to high.

        See https://redis.io/commands/zrank for more details.

        To get the rank of `member` with its score, see `zrank_withscore`.

        Args:
            key (str): The key of the sorted set.
            member (str): The member whose rank is to be retrieved.

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
        key: str,
        member: str,
    ) -> Optional[List[Union[int, float]]]:
        """
        Returns the rank of `member` in the sorted set stored at `key` with its score, where scores are ordered from the lowest to highest.

        See https://redis.io/commands/zrank for more details.

        Args:
            key (str): The key of the sorted set.
            member (str): The member whose rank is to be retrieved.

        Returns:
            Optional[List[Union[int, float]]]: A list containing the rank and score of `member` in the sorted set.
            If `key` doesn't exist, or if `member` is not present in the set, None will be returned.

        Examples:
            >>> await client.zrank_withscore("my_sorted_set", "member2")
                [1 , 6.0]  # Indicates that "member2" with score 6.0 has the second-lowest score in the sorted set "my_sorted_set".
            >>> await client.zrank_withscore("my_sorted_set", "non_existing_member")
                None  # Indicates that "non_existing_member" is not present in the sorted set "my_sorted_set".

        Since: Redis version 7.2.0.
        """
        return cast(
            Optional[List[Union[int, float]]],
            await self._execute_command(RequestType.ZRank, [key, member, "WITHSCORE"]),
        )

    async def zrevrank(self, key: str, member: str) -> Optional[int]:
        """
        Returns the rank of `member` in the sorted set stored at `key`, where scores are ordered from the highest to
        lowest, starting from `0`.

        To get the rank of `member` with its score, see `zrevrank_withscore`.

        See https://valkey.io/commands/zrevrank for more details.

        Args:
            key (str): The key of the sorted set.
            member (str): The member whose rank is to be retrieved.

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
        self, key: str, member: str
    ) -> Optional[List[Union[int, float]]]:
        """
        Returns the rank of `member` in the sorted set stored at `key` with its score, where scores are ordered from the
        highest to lowest, starting from `0`.

        See https://valkey.io/commands/zrevrank for more details.

        Args:
            key (str): The key of the sorted set.
            member (str): The member whose rank is to be retrieved.

        Returns:
            Optional[List[Union[int, float]]]: A list containing the rank (as `int`) and score (as `float`) of `member`
                in the sorted set, where ranks are ordered from high to low based on scores.
                If `key` doesn't exist, or if `member` is not present in the set, `None` will be returned.

        Examples:
            >>> await client.zadd("my_sorted_set", {"member1": 10.5, "member2": 8.2, "member3": 9.6})
            >>> await client.zrevrank("my_sorted_set", "member2")
                [2, 8.2]  # "member2" with score 8.2 has the third-highest score in the sorted set "my_sorted_set"

        Since: Redis version 7.2.0.
        """
        return cast(
            Optional[List[Union[int, float]]],
            await self._execute_command(
                RequestType.ZRevRank, [key, member, "WITHSCORE"]
            ),
        )

    async def zrem(
        self,
        key: str,
        members: List[str],
    ) -> int:
        """
        Removes the specified members from the sorted set stored at `key`.
        Specified members that are not a member of this set are ignored.

        See https://redis.io/commands/zrem/ for more details.

        Args:
            key (str): The key of the sorted set.
            members (List[str]): A list of members to remove from the sorted set.

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
        key: str,
        min_score: Union[InfBound, ScoreBoundary],
        max_score: Union[InfBound, ScoreBoundary],
    ) -> int:
        """
        Removes all elements in the sorted set stored at `key` with a score between `min_score` and `max_score`.

        See https://redis.io/commands/zremrangebyscore/ for more details.

        Args:
            key (str): The key of the sorted set.
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
        key: str,
        min_lex: Union[InfBound, LexBoundary],
        max_lex: Union[InfBound, LexBoundary],
    ) -> int:
        """
        Removes all elements in the sorted set stored at `key` with a lexicographical order between `min_lex` and
        `max_lex`.

        See https://redis.io/commands/zremrangebylex/ for more details.

        Args:
            key (str): The key of the sorted set.
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
        key: str,
        start: int,
        end: int,
    ) -> int:
        """
        Removes all elements in the sorted set stored at `key` with rank between `start` and `end`.
        Both `start` and `end` are zero-based indexes with 0 being the element with the lowest score.
        These indexes can be negative numbers, where they indicate offsets starting at the element with the highest score.

        See https://valkey.io/commands/zremrangebyrank/ for more details.

        Args:
            key (str): The key of the sorted set.
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
        key: str,
        min_lex: Union[InfBound, LexBoundary],
        max_lex: Union[InfBound, LexBoundary],
    ) -> int:
        """
        Returns the number of members in the sorted set stored at `key` with lexicographical values between `min_lex` and `max_lex`.

        See https://redis.io/commands/zlexcount/ for more details.

        Args:
            key (str): The key of the sorted set.
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

    async def zscore(self, key: str, member: str) -> Optional[float]:
        """
        Returns the score of `member` in the sorted set stored at `key`.

        See https://redis.io/commands/zscore/ for more details.

        Args:
            key (str): The key of the sorted set.
            member (str): The member whose score is to be retrieved.

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
        key: str,
        members: List[str],
    ) -> List[Optional[float]]:
        """
        Returns the scores associated with the specified `members` in the sorted set stored at `key`.

        See https://valkey.io/commands/zmscore for more details.

        Args:
            key (str): The key of the sorted set.
            members (List[str]): A list of members in the sorted set.

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

    async def zdiff(self, keys: List[str]) -> List[str]:
        """
        Returns the difference between the first sorted set and all the successive sorted sets.
        To get the elements with their scores, see `zdiff_withscores`.

        When in Cluster mode, all keys must map to the same hash slot.

        See https://valkey.io/commands/zdiff for more details.

        Args:
            keys (List[str]): The keys of the sorted sets.

        Returns:
            List[str]: A list of elements representing the difference between the sorted sets.
                If the first key does not exist, it is treated as an empty sorted set, and the command returns an
                empty list.

        Examples:
            >>> await client.zadd("sorted_set1", {"element1":1.0, "element2": 2.0, "element3": 3.0})
            >>> await client.zadd("sorted_set2", {"element2": 2.0})
            >>> await client.zadd("sorted_set3", {"element3": 3.0})
            >>> await client.zdiff("sorted_set1", "sorted_set2", "sorted_set3")
                ["element1"]  # Indicates that "element1" is in "sorted_set1" but not "sorted_set2" or "sorted_set3".
        """
        return cast(
            List[str],
            await self._execute_command(RequestType.ZDiff, [str(len(keys))] + keys),
        )

    async def zdiff_withscores(self, keys: List[str]) -> Mapping[str, float]:
        """
        Returns the difference between the first sorted set and all the successive sorted sets, with the associated scores.
        When in Cluster mode, all keys must map to the same hash slot.

        See https://valkey.io/commands/zdiff for more details.

        Args:
            keys (List[str]): The keys of the sorted sets.

        Returns:
            Mapping[str, float]: A mapping of elements and their scores representing the difference between the sorted
                sets.
                If the first `key` does not exist, it is treated as an empty sorted set, and the command returns an
                empty list.

        Examples:
            >>> await client.zadd("sorted_set1", {"element1":1.0, "element2": 2.0, "element3": 3.0})
            >>> await client.zadd("sorted_set2", {"element2": 2.0})
            >>> await client.zadd("sorted_set3", {"element3": 3.0})
            >>> await client.zdiff_withscores("sorted_set1", "sorted_set2", "sorted_set3")
                {"element1": 1.0}  # Indicates that "element1" is in "sorted_set1" but not "sorted_set2" or "sorted_set3".
        """
        return cast(
            Mapping[str, float],
            await self._execute_command(
                RequestType.ZDiff, [str(len(keys))] + keys + ["WITHSCORES"]
            ),
        )

    async def zdiffstore(self, destination: str, keys: List[str]) -> int:
        """
        Calculates the difference between the first sorted set and all the successive sorted sets at `keys` and stores
        the difference as a sorted set to `destination`, overwriting it if it already exists. Non-existent keys are
        treated as empty sets.
        See https://valkey.io/commands/zdiffstore for more details.

        Note:
            When in Cluster mode, all keys in `keys` and `destination` must map to the same hash slot.

        Args:
            destination (str): The key for the resulting sorted set.
            keys (List[str]): The keys of the sorted sets to compare.

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
        keys: List[str],
    ) -> List[str]:
        """
        Computes the intersection of sorted sets given by the specified `keys` and returns a list of intersecting elements.
        To get the scores as well, see `zinter_withscores`.
        To store the result in a key as a sorted set, see `zinterstore`.

        When in cluster mode, all keys in `keys` must map to the same hash slot.

        See https://valkey.io/commands/zinter/ for more details.

        Args:
            keys (List[str]): The keys of the sorted sets.

        Returns:
            List[str]: The resulting array of intersecting elements.

        Examples:
            >>> await client.zadd("key1", {"member1": 10.5, "member2": 8.2})
            >>> await client.zadd("key2", {"member1": 9.5})
            >>> await client.zinter(["key1", "key2"])
                ['member1']
        """
        return cast(
            List[str],
            await self._execute_command(RequestType.ZInter, [str(len(keys))] + keys),
        )

    async def zinter_withscores(
        self,
        keys: Union[List[str], List[Tuple[str, float]]],
        aggregation_type: Optional[AggregationType] = None,
    ) -> Mapping[str, float]:
        """
        Computes the intersection of sorted sets given by the specified `keys` and returns a sorted set of intersecting elements with scores.
        To get the elements only, see `zinter`.
        To store the result in a key as a sorted set, see `zinterstore`.

        When in cluster mode, all keys in `keys` must map to the same hash slot.

        See https://valkey.io/commands/zinter/ for more details.

        Args:
            keys (Union[List[str], List[Tuple[str, float]]]): The keys of the sorted sets with possible formats:
                List[str] - for keys only.
                List[Tuple[str, float]] - for weighted keys with score multipliers.
            aggregation_type (Optional[AggregationType]): Specifies the aggregation strategy to apply
                when combining the scores of elements. See `AggregationType`.

        Returns:
            Mapping[str, float]: The resulting sorted set with scores.

        Examples:
            >>> await client.zadd("key1", {"member1": 10.5, "member2": 8.2})
            >>> await client.zadd("key2", {"member1": 9.5})
            >>> await client.zinter_withscores(["key1", "key2"])
                {'member1': 20}  # "member1" with score of 20 is the result
            >>> await client.zinter_withscores(["key1", "key2"], AggregationType.MAX)
                {'member1': 10.5}  # "member1" with score of 10.5 is the result.
        """
        args = _create_zinter_zunion_cmd_args(keys, aggregation_type)
        args.append("WITHSCORES")
        return cast(
            Mapping[str, float],
            await self._execute_command(RequestType.ZInter, args),
        )

    async def zinterstore(
        self,
        destination: str,
        keys: Union[List[str], List[Tuple[str, float]]],
        aggregation_type: Optional[AggregationType] = None,
    ) -> int:
        """
        Computes the intersection of sorted sets given by the specified `keys` and stores the result in `destination`.
        If `destination` already exists, it is overwritten. Otherwise, a new sorted set will be created.
        To get the result directly, see `zinter_withscores`.

        When in cluster mode, `destination` and all keys in `keys` must map to the same hash slot.

        See https://valkey.io/commands/zinterstore/ for more details.

        Args:
            destination (str): The key of the destination sorted set.
            keys (Union[List[str], List[Tuple[str, float]]]): The keys of the sorted sets with possible formats:
                List[str] - for keys only.
                List[Tuple[str, float]] - for weighted keys with score multipliers.
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
                {'member1': 20}  # "member1" is now stored in "my_sorted_set" with score of 20.
            >>> await client.zinterstore("my_sorted_set", ["key1", "key2"], AggregationType.MAX)
                1 # Indicates that the sorted set "my_sorted_set" contains one element, and its score is the maximum score between the sets.
            >>> await client.zrange_withscores("my_sorted_set", RangeByIndex(0, -1))
                {'member1': 10.5}  # "member1" is now stored in "my_sorted_set" with score of 10.5.
        """
        args = _create_zinter_zunion_cmd_args(keys, aggregation_type, destination)
        return cast(
            int,
            await self._execute_command(RequestType.ZInterStore, args),
        )

    async def zunion(
        self,
        keys: List[str],
    ) -> List[str]:
        """
        Computes the union of sorted sets given by the specified `keys` and returns a list of union elements.
        To get the scores as well, see `zunion_withscores`.
        To store the result in a key as a sorted set, see `zunionstore`.

        When in cluster mode, all keys in `keys` must map to the same hash slot.

        See https://valkey.io/commands/zunion/ for more details.

        Args:
            keys (List[str]): The keys of the sorted sets.

        Returns:
            List[str]: The resulting array of union elements.

        Examples:
            >>> await client.zadd("key1", {"member1": 10.5, "member2": 8.2})
            >>> await client.zadd("key2", {"member1": 9.5})
            >>> await client.zunion(["key1", "key2"])
                ['member1', 'member2']
        """
        return cast(
            List[str],
            await self._execute_command(RequestType.ZUnion, [str(len(keys))] + keys),
        )

    async def zunion_withscores(
        self,
        keys: Union[List[str], List[Tuple[str, float]]],
        aggregation_type: Optional[AggregationType] = None,
    ) -> Mapping[str, float]:
        """
        Computes the union of sorted sets given by the specified `keys` and returns a sorted set of union elements with scores.
        To get the elements only, see `zunion`.
        To store the result in a key as a sorted set, see `zunionstore`.

        When in cluster mode, all keys in `keys` must map to the same hash slot.

        See https://valkey.io/commands/zunion/ for more details.

        Args:
            keys (Union[List[str], List[Tuple[str, float]]]): The keys of the sorted sets with possible formats:
                List[str] - for keys only.
                List[Tuple[str, float]] - for weighted keys with score multipliers.
            aggregation_type (Optional[AggregationType]): Specifies the aggregation strategy to apply
                when combining the scores of elements. See `AggregationType`.

        Returns:
            Mapping[str, float]: The resulting sorted set with scores.

        Examples:
            >>> await client.zadd("key1", {"member1": 10.5, "member2": 8.2})
            >>> await client.zadd("key2", {"member1": 9.5})
            >>> await client.zunion_withscores(["key1", "key2"])
                {'member1': 20, 'member2': 8.2}
            >>> await client.zunion_withscores(["key1", "key2"], AggregationType.MAX)
                {'member1': 10.5, 'member2': 8.2}
        """
        args = _create_zinter_zunion_cmd_args(keys, aggregation_type)
        args.append("WITHSCORES")
        return cast(
            Mapping[str, float],
            await self._execute_command(RequestType.ZUnion, args),
        )

    async def zunionstore(
        self,
        destination: str,
        keys: Union[List[str], List[Tuple[str, float]]],
        aggregation_type: Optional[AggregationType] = None,
    ) -> int:
        """
        Computes the union of sorted sets given by the specified `keys` and stores the result in `destination`.
        If `destination` already exists, it is overwritten. Otherwise, a new sorted set will be created.
        To get the result directly, see `zunion_withscores`.

        When in cluster mode, `destination` and all keys in `keys` must map to the same hash slot.

        See https://valkey.io/commands/zunionstore/ for more details.

        Args:
            destination (str): The key of the destination sorted set.
            keys (Union[List[str], List[Tuple[str, float]]]): The keys of the sorted sets with possible formats:
                List[str] - for keys only.
                List[Tuple[str, float]] - for weighted keys with score multipliers.
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
                {'member1': 20, 'member2': 8.2}
            >>> await client.zunionstore("my_sorted_set", ["key1", "key2"], AggregationType.MAX)
                2 # Indicates that the sorted set "my_sorted_set" contains two elements, and each score is the maximum score between the sets.
            >>> await client.zrange_withscores("my_sorted_set", RangeByIndex(0, -1))
                {'member1': 10.5, 'member2': 8.2}
        """
        args = _create_zinter_zunion_cmd_args(keys, aggregation_type, destination)
        return cast(
            int,
            await self._execute_command(RequestType.ZUnionStore, args),
        )

    async def zrandmember(self, key: str) -> Optional[str]:
        """
        Returns a random member from the sorted set stored at 'key'.

        See https://valkey.io/commands/zrandmember for more details.

        Args:
            key (str): The key of the sorted set.

        Returns:
            Optional[str]: A random member from the sorted set.
                If the sorted set does not exist or is empty, the response will be None.

        Examples:
            >>> await client.zadd("my_sorted_set", {"member1": 1.0, "member2": 2.0})
            >>> await client.zrandmember("my_sorted_set")
                "member1"  # "member1" is a random member of "my_sorted_set".
            >>> await client.zrandmember("non_existing_sorted_set")
                None  # "non_existing_sorted_set" is not an existing key, so None was returned.
        """
        return cast(
            Optional[str],
            await self._execute_command(RequestType.ZRandMember, [key]),
        )

    async def zrandmember_count(self, key: str, count: int) -> List[str]:
        """
        Retrieves up to the absolute value of `count` random members from the sorted set stored at 'key'.

        See https://valkey.io/commands/zrandmember for more details.

        Args:
            key (str): The key of the sorted set.
            count (int): The number of members to return.
                If `count` is positive, returns unique members.
                If `count` is negative, allows for duplicates members.

        Returns:
            List[str]: A list of members from the sorted set.
                If the sorted set does not exist or is empty, the response will be an empty list.

        Examples:
            >>> await client.zadd("my_sorted_set", {"member1": 1.0, "member2": 2.0})
            >>> await client.zrandmember("my_sorted_set", -3)
                ["member1", "member1", "member2"]  # "member1" and "member2" are random members of "my_sorted_set".
            >>> await client.zrandmember("non_existing_sorted_set", 3)
                []  # "non_existing_sorted_set" is not an existing key, so an empty list was returned.
        """
        return cast(
            List[str],
            await self._execute_command(RequestType.ZRandMember, [key, str(count)]),
        )

    async def zrandmember_withscores(
        self, key: str, count: int
    ) -> List[List[Union[str, float]]]:
        """
        Retrieves up to the absolute value of `count` random members along with their scores from the sorted set
        stored at 'key'.

        See https://valkey.io/commands/zrandmember for more details.

        Args:
            key (str): The key of the sorted set.
            count (int): The number of members to return.
                If `count` is positive, returns unique members.
                If `count` is negative, allows for duplicates members.

        Returns:
            List[List[Union[str, float]]]: A list of `[member, score]` lists, where `member` is a random member from
                the sorted set and `score` is the associated score.
                If the sorted set does not exist or is empty, the response will be an empty list.

        Examples:
            >>> await client.zadd("my_sorted_set", {"member1": 1.0, "member2": 2.0})
            >>> await client.zrandmember_withscores("my_sorted_set", -3)
                [["member1", 1.0], ["member1", 1.0], ["member2", 2.0]]  # "member1" and "member2" are random members of "my_sorted_set", and have scores of 1.0 and 2.0, respectively.
            >>> await client.zrandmember_withscores("non_existing_sorted_set", 3)
                []  # "non_existing_sorted_set" is not an existing key, so an empty list was returned.
        """
        return cast(
            List[List[Union[str, float]]],
            await self._execute_command(
                RequestType.ZRandMember, [key, str(count), "WITHSCORES"]
            ),
        )

    async def zmpop(
        self, keys: List[str], filter: ScoreFilter, count: Optional[int] = None
    ) -> Optional[List[Union[str, Mapping[str, float]]]]:
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
            keys (List[str]): The keys of the sorted sets.
            modifier (ScoreFilter): The element pop criteria - either ScoreFilter.MIN or ScoreFilter.MAX to pop
                members with the lowest/highest scores accordingly.
            count (Optional[int]): The number of elements to pop.

        Returns:
            Optional[List[Union[str, Mapping[str, float]]]]: A two-element list containing the key name of the set from
                which elements were popped, and a member-score mapping of the popped elements. If no members could be
                popped, returns None.

        Examples:
            >>> await client.zadd("zSet1", {"one": 1.0, "two": 2.0, "three": 3.0})
            >>> await client.zadd("zSet2", {"four": 4.0})
            >>> await client.zmpop(["zSet1", "zSet2"], ScoreFilter.MAX, 2)
                ['zSet1', {'three': 3.0, 'two': 2.0}]  # "three" with score 3.0 and "two" with score 2.0 were popped from "zSet1".

        Since: Redis version 7.0.0.
        """
        args = [str(len(keys))] + keys + [filter.value]
        if count is not None:
            args = args + ["COUNT", str(count)]

        return cast(
            Optional[List[Union[str, Mapping[str, float]]]],
            await self._execute_command(RequestType.ZMPop, args),
        )

    async def bzmpop(
        self,
        keys: List[str],
        modifier: ScoreFilter,
        timeout: float,
        count: Optional[int] = None,
    ) -> Optional[List[Union[str, Mapping[str, float]]]]:
        """
        Pops a member-score pair from the first non-empty sorted set, with the given keys being checked in the order
        that they are given. Blocks the connection when there are no members to pop from any of the given sorted sets.

        The optional `count` argument can be used to specify the number of elements to pop, and is set to 1 by default.

        The number of popped elements is the minimum from the sorted set's cardinality and `count`.

        `BZMPOP` is the blocking variant of `ZMPOP`.

        See https://valkey.io/commands/bzmpop for more details.

        Notes:
            1. When in cluster mode, all `keys` must map to the same hash slot.
            2. `BZMPOP` is a client blocking command, see https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands for more details and best practices.

        Args:
            keys (List[str]): The keys of the sorted sets.
            modifier (ScoreFilter): The element pop criteria - either ScoreFilter.MIN or ScoreFilter.MAX to pop
                members with the lowest/highest scores accordingly.
            timeout (float): The number of seconds to wait for a blocking operation to complete. A value of 0 will
                block indefinitely.
            count (Optional[int]): The number of elements to pop.

        Returns:
            Optional[List[Union[str, Mapping[str, float]]]]: A two-element list containing the key name of the set from
                which elements were popped, and a member-score mapping of the popped elements. If no members could be
                popped and the timeout expired, returns None.

        Examples:
            >>> await client.zadd("zSet1", {"one": 1.0, "two": 2.0, "three": 3.0})
            >>> await client.zadd("zSet2", {"four": 4.0})
            >>> await client.bzmpop(["zSet1", "zSet2"], ScoreFilter.MAX, 0.5, 2)
                ['zSet1', {'three': 3.0, 'two': 2.0}]  # "three" with score 3.0 and "two" with score 2.0 were popped from "zSet1".

        Since: Redis version 7.0.0.
        """
        args = [str(timeout), str(len(keys))] + keys + [modifier.value]
        if count is not None:
            args = args + ["COUNT", str(count)]

        return cast(
            Optional[List[Union[str, Mapping[str, float]]]],
            await self._execute_command(RequestType.BZMPop, args),
        )

    async def zintercard(self, keys: List[str], limit: Optional[int] = None) -> int:
        """
        Returns the cardinality of the intersection of the sorted sets specified by `keys`. When provided with the
        optional `limit` argument, if the intersection cardinality reaches `limit` partway through the computation, the
        algorithm will exit early and yield `limit` as the cardinality.

        See https://valkey.io/commands/zintercard for more details.

        Args:
            keys (List[str]): The keys of the sorted sets to intersect.
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

        Since: Redis version 7.0.0.
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
        keys: Optional[List[Union[str, bytes]]] = None,
        args: Optional[List[Union[str, bytes]]] = None,
    ) -> TResult:
        """
        Invokes a Lua script with its keys and arguments.
        This method simplifies the process of invoking scripts on a Redis server by using an object that represents a Lua script.
        The script loading, argument preparation, and execution will all be handled internally.
        If the script has not already been loaded, it will be loaded automatically using the Redis `SCRIPT LOAD` command.
        After that, it will be invoked using the Redis `EVALSHA` command.

        See https://redis.io/commands/script-load/ and https://redis.io/commands/evalsha/ for more details.

        Args:
            script (Script): The Lua script to execute.
            keys (List[Union[str, bytes]]): The keys that are used in the script.
            args (List[Union[str, bytes]]): The arguments for the script.

        Returns:
            TResult: a value that depends on the script that was executed.

        Examples:
            >>> lua_script = Script("return { KEYS[1], ARGV[1] }")
            >>> await invoke_script(lua_script, keys=["foo"], args=["bar"] );
                ["foo", "bar"]
        """
        return await self._execute_script(script.get_hash(), keys, args)

    async def pfadd(self, key: str, elements: List[str]) -> int:
        """
        Adds all elements to the HyperLogLog data structure stored at the specified `key`.
        Creates a new structure if the `key` does not exist.
        When no elements are provided, and `key` exists and is a HyperLogLog, then no operation is performed.

        See https://redis.io/commands/pfadd/ for more details.

        Args:
            key (str): The key of the HyperLogLog data structure to add elements into.
            elements (List[str]): A list of members to add to the HyperLogLog stored at `key`.

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

    async def pfcount(self, keys: List[str]) -> int:
        """
        Estimates the cardinality of the data stored in a HyperLogLog structure for a single key or
        calculates the combined cardinality of multiple keys by merging their HyperLogLogs temporarily.

        See https://valkey.io/commands/pfcount for more details.

        Note:
            When in Cluster mode, all `keys` must map to the same hash slot.

        Args:
            keys (List[str]): The keys of the HyperLogLog data structures to be analyzed.

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

    async def pfmerge(self, destination: str, source_keys: List[str]) -> TOK:
        """
        Merges multiple HyperLogLog values into a unique value. If the destination variable exists, it is treated as one
        of the source HyperLogLog data sets, otherwise a new HyperLogLog is created.

        See https://valkey.io/commands/pfmerge for more details.

        Note:
            When in Cluster mode, all keys in `source_keys` and `destination` must map to the same hash slot.

        Args:
            destination (str): The key of the destination HyperLogLog where the merged data sets will be stored.
            source_keys (List[str]): The keys of the HyperLogLog structures to be merged.

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

    async def bitcount(self, key: str, options: Optional[OffsetOptions] = None) -> int:
        """
        Counts the number of set bits (population counting) in the string stored at `key`. The `options` argument can
        optionally be provided to count the number of bits in a specific string interval.

        See https://valkey.io/commands/bitcount for more details.

        Args:
            key (str): The key for the string to count the set bits of.
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
        args = [key]
        if options is not None:
            args = args + options.to_args()

        return cast(
            int,
            await self._execute_command(RequestType.BitCount, args),
        )

    async def setbit(self, key: str, offset: int, value: int) -> int:
        """
        Sets or clears the bit at `offset` in the string value stored at `key`. The `offset` is a zero-based index,
        with `0` being the first element of the list, `1` being the next element, and so on. The `offset` must be less
        than `2^32` and greater than or equal to `0`. If a key is non-existent then the bit at `offset` is set to
        `value` and the preceding bits are set to `0`.

        See https://valkey.io/commands/setbit for more details.

        Args:
            key (str): The key of the string.
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

    async def getbit(self, key: str, offset: int) -> int:
        """
        Returns the bit value at `offset` in the string value stored at `key`.
        `offset` should be greater than or equal to zero.

        See https://valkey.io/commands/getbit for more details.

        Args:
            key (str): The key of the string.
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

    async def bitpos(self, key: str, bit: int, start: Optional[int] = None) -> int:
        """
        Returns the position of the first bit matching the given `bit` value. The optional starting offset
        `start` is a zero-based index, with `0` being the first byte of the list, `1` being the next byte and so on.
        The offset can also be a negative number indicating an offset starting at the end of the list, with `-1` being
        the last byte of the list, `-2` being the penultimate, and so on.

        See https://valkey.io/commands/bitpos for more details.

        Args:
            key (str): The key of the string.
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
        key: str,
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

        If you are using Redis 7.0.0 or above, the optional `index_type` can also be provided to specify whether the
        `start` and `end` offsets specify BIT or BYTE offsets. If `index_type` is not provided, BYTE offsets
        are assumed. If BIT is specified, `start=0` and `end=2` means to look at the first three bits. If BYTE is
        specified, `start=0` and `end=2` means to look at the first three bytes.

        See https://valkey.io/commands/bitpos for more details.

        Args:
            key (str): The key of the string.
            bit (int): The bit value to match. Must be `0` or `1`.
            start (int): The starting offset.
            end (int): The ending offset.
            index_type (Optional[BitmapIndexType]): The index offset type. This option can only be specified if you are
                using Redis version 7.0.0 or above. Could be either `BitmapIndexType.BYTE` or `BitmapIndexType.BIT`.
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
        self, operation: BitwiseOperation, destination: str, keys: List[str]
    ) -> int:
        """
        Perform a bitwise operation between multiple keys (containing string values) and store the result in the
        `destination`.

        See https://valkey.io/commands/bitop for more details.

        Note:
            When in cluster mode, `destination` and all `keys` must map to the same hash slot.

        Args:
            operation (BitwiseOperation): The bitwise operation to perform.
            destination (str): The key that will store the resulting string.
            keys (List[str]): The list of keys to perform the bitwise operation on.

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
        self, key: str, subcommands: List[BitFieldSubCommands]
    ) -> List[Optional[int]]:
        """
        Reads or modifies the array of bits representing the string that is held at `key` based on the specified
        `subcommands`.

        See https://valkey.io/commands/bitfield for more details.

        Args:
            key (str): The key of the string.
            subcommands (List[BitFieldSubCommands]): The subcommands to be performed on the binary value of the string
                at `key`, which could be any of the following:
                    - `BitFieldGet`
                    - `BitFieldSet`
                    - `BitFieldIncrBy`
                    - `BitFieldOverflow`

        Returns:
            List[Optional[int]]: An array of results from the executed subcommands:
                - `BitFieldGet` returns the value in `Offset` or `OffsetMultiplier`.
                - `BitFieldSet` returns the old value in `Offset` or `OffsetMultiplier`.
                - `BitFieldIncrBy` returns the new value in `Offset` or `OffsetMultiplier`.
                - `BitFieldOverflow` determines the behavior of the "SET" and "INCRBY" subcommands when an overflow or
                  underflow occurs. "OVERFLOW" does not return a value and does not contribute a value to the list
                  response.

        Examples:
            >>> await client.set("my_key", "A")  # "A" has binary value 01000001
            >>> await client.bitfield("my_key", [BitFieldSet(UnsignedEncoding(2), Offset(1), 3), BitFieldGet(UnsignedEncoding(2), Offset(1))])
                [2, 3]  # The old value at offset 1 with an unsigned encoding of 2 was 2. The new value at offset 1 with an unsigned encoding of 2 is 3.
        """
        args = [key] + _create_bitfield_args(subcommands)
        return cast(
            List[Optional[int]],
            await self._execute_command(RequestType.BitField, args),
        )

    async def bitfield_read_only(
        self, key: str, subcommands: List[BitFieldGet]
    ) -> List[int]:
        """
        Reads the array of bits representing the string that is held at `key` based on the specified `subcommands`.

        See https://valkey.io/commands/bitfield_ro for more details.

        Args:
            key (str): The key of the string.
            subcommands (List[BitFieldGet]): The "GET" subcommands to be performed.

        Returns:
            List[int]: An array of results from the "GET" subcommands.

        Examples:
            >>> await client.set("my_key", "A")  # "A" has binary value 01000001
            >>> await client.bitfield_read_only("my_key", [BitFieldGet(UnsignedEncoding(2), Offset(1))])
                [2]  # The value at offset 1 with an unsigned encoding of 2 is 3.

        Since: Redis version 6.0.0.
        """
        args = [key] + _create_bitfield_read_only_args(subcommands)
        return cast(
            List[int],
            await self._execute_command(RequestType.BitFieldReadOnly, args),
        )

    async def object_encoding(self, key: str) -> Optional[str]:
        """
        Returns the internal encoding for the Redis object stored at `key`.

        See https://valkey.io/commands/object-encoding for more details.

        Args:
            key (str): The `key` of the object to get the internal encoding of.

        Returns:
            Optional[str]: If `key` exists, returns the internal encoding of the object stored at
                `key` as a string. Otherwise, returns None.

        Examples:
            >>> await client.object_encoding("my_hash")
                "listpack"  # The hash stored at "my_hash" has an internal encoding of "listpack".
        """
        return cast(
            Optional[str],
            await self._execute_command(RequestType.ObjectEncoding, [key]),
        )

    async def object_freq(self, key: str) -> Optional[int]:
        """
        Returns the logarithmic access frequency counter of a Redis object stored at `key`.

        See https://valkey.io/commands/object-freq for more details.

        Args:
            key (str): The key of the object to get the logarithmic access frequency counter of.

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

    async def object_idletime(self, key: str) -> Optional[int]:
        """
        Returns the time in seconds since the last access to the value stored at `key`.

        See https://valkey.io/commands/object-idletime for more details.

        Args:
            key (str): The key of the object to get the idle time of.

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

    async def object_refcount(self, key: str) -> Optional[int]:
        """
        Returns the reference count of the object stored at `key`.

        See https://valkey.io/commands/object-refcount for more details.

        Args:
            key (str): The key of the object to get the reference count of.

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

    async def srandmember(self, key: str) -> Optional[str]:
        """
        Returns a random element from the set value stored at 'key'.

        See https://valkey.io/commands/srandmember for more details.

        Args:
            key (str): The key from which to retrieve the set member.

        Returns:
            str: A random element from the set, or None if 'key' does not exist.

        Examples:
            >>> await client.sadd("my_set", {"member1": 1.0, "member2": 2.0})
            >>> await client.srandmember("my_set")
            "member1"  # "member1" is a random member of "my_set".
            >>> await client.srandmember("non_existing_set")
            None  # "non_existing_set" is not an existing key, so None was returned.
        """
        return cast(
            Optional[str],
            await self._execute_command(RequestType.SRandMember, [key]),
        )

    async def srandmember_count(self, key: str, count: int) -> List[str]:
        """
        Returns one or more random elements from the set value stored at 'key'.

        See https://valkey.io/commands/srandmember for more details.

        Args:
            key (str): The key of the sorted set.
            count (int): The number of members to return.
                If `count` is positive, returns unique members.
                If `count` is negative, allows for duplicates members.

        Returns:
            List[str]: A list of members from the set.
                If the set does not exist or is empty, the response will be an empty list.

        Examples:
            >>> await client.sadd("my_set", {"member1": 1.0, "member2": 2.0})
            >>> await client.srandmember("my_set", -3)
                ["member1", "member1", "member2"]  # "member1" and "member2" are random members of "my_set".
            >>> await client.srandmember("non_existing_set", 3)
                []  # "non_existing_set" is not an existing key, so an empty list was returned.
        """
        return cast(
            List[str],
            await self._execute_command(RequestType.SRandMember, [key, str(count)]),
        )

    async def getex(
        self,
        key: str,
        expiry: Optional[ExpiryGetEx] = None,
    ) -> Optional[str]:
        """
        Get the value of `key` and optionally set its expiration. `GETEX` is similar to `GET`.
        See https://valkey.io/commands/getex for more details.

        Args:
            key (str): The key to get.
            expiry (Optional[ExpirySet], optional): set expiriation to the given key.
                Equivalent to [`EX` | `PX` | `EXAT` | `PXAT` | `PERSIST`] in the Redis API.

        Returns:
            Optional[str]:
                If `key` exists, return the value stored at `key`
                If `key` does not exist, return `None`

        Examples:
            >>> await client.set("key", "value")
                'OK'
            >>> await client.getex("key")
                'value'
            >>> await client.getex("key", ExpiryGetEx(ExpiryTypeGetEx.SEC, 1))
                'value'
            >>> time.sleep(1)
            >>> await client.getex("key")
                None

        Since: Redis version 6.2.0.
        """
        args = [key]
        if expiry is not None:
            args.extend(expiry.get_cmd_args())
        return cast(
            Optional[str],
            await self._execute_command(RequestType.GetEx, args),
        )

    async def sscan(
        self,
        key: str,
        cursor: str,
        match: Optional[str] = None,
        count: Optional[int] = None,
    ) -> List[Union[str, List[str]]]:
        """
        Iterates incrementally over a set.

        See https://valkey.io/commands/sscan for more details.

        Args:
            key (str): The key of the set.
            cursor (str): The cursor that points to the next iteration of results. A value of "0" indicates the start of
                the search.
            match (Optional[str]): The match filter is applied to the result of the command and will only include
                strings that match the pattern specified. If the set is large enough for scan commands to return only a
                subset of the set then there could be a case where the result is empty although there are items that
                match the pattern specified. This is due to the default `COUNT` being `10` which indicates that it will
                only fetch and match `10` items from the list.
            count (Optional[int]): `COUNT` is a just a hint for the command for how many elements to fetch from the set.
                `COUNT` could be ignored until the set is large enough for the `SCAN` commands to represent the results
                as compact single-allocation packed encoding.

        Returns:
            List[Union[str, List[str]]]: An `Array` of the `cursor` and the subset of the set held by `key`.
                The first element is always the `cursor` for the next iteration of results. `0` will be the `cursor`
                returned on the last iteration of the set. The second element is always an `Array` of the subset of the
                set held in `key`.

        Examples:
            # Assume "key" contains a set with 130 members
            >>> result_cursor = "0"
            >>> while True:
            ...     result = await redis_client.sscan("key", "0", match="*")
            ...     new_cursor = str(result [0])
            ...     print("Cursor: ", new_cursor)
            ...     print("Members: ", result[1])
            ...     if new_cursor == "0":
            ...         break
            ...     result_cursor = new_cursor
            Cursor:  48
            Members:  ['3', '118', '120', '86', '76', '13', '61', '111', '55', '45']
            Cursor:  24
            Members:  ['38', '109', '11', '119', '34', '24', '40', '57', '20', '17']
            Cursor:  0
            Members:  ['47', '122', '1', '53', '10', '14', '80']
        """
        args = [key, cursor]
        if match is not None:
            args += ["MATCH", match]
        if count is not None:
            args += ["COUNT", str(count)]

        return cast(
            List[Union[str, List[str]]],
            await self._execute_command(RequestType.SScan, args),
        )

    async def zscan(
        self,
        key: str,
        cursor: str,
        match: Optional[str] = None,
        count: Optional[int] = None,
    ) -> List[Union[str, List[str]]]:
        """
        Iterates incrementally over a sorted set.

        See https://valkey.io/commands/zscan for more details.

        Args:
            key (str): The key of the sorted set.
            cursor (str): The cursor that points to the next iteration of results. A value of "0" indicates the start of
                the search.
            match (Optional[str]): The match filter is applied to the result of the command and will only include
                strings that match the pattern specified. If the sorted set is large enough for scan commands to return
                only a subset of the sorted set then there could be a case where the result is empty although there are
                items that match the pattern specified. This is due to the default `COUNT` being `10` which indicates
                that it will only fetch and match `10` items from the list.
            count (Optional[int]): `COUNT` is a just a hint for the command for how many elements to fetch from the
                sorted set. `COUNT` could be ignored until the sorted set is large enough for the `SCAN` commands to
                represent the results as compact single-allocation packed encoding.

        Returns:
            List[Union[str, List[str]]]: An `Array` of the `cursor` and the subset of the sorted set held by `key`.
                The first element is always the `cursor` for the next iteration of results. `0` will be the `cursor`
                returned on the last iteration of the sorted set. The second element is always an `Array` of the subset
                of the sorted set held in `key`. The `Array` in the second element is always a flattened series of
                `String` pairs, where the value is at even indices and the score is at odd indices.

        Examples:
            # Assume "key" contains a sorted set with multiple members
            >>> result_cursor = "0"
            >>> while True:
            ...     result = await redis_client.zscan("key", "0", match="*", count=5)
            ...     new_cursor = str(result [0])
            ...     print("Cursor: ", new_cursor)
            ...     print("Members: ", result[1])
            ...     if new_cursor == "0":
            ...         break
            ...     result_cursor = new_cursor
            Cursor:  123
            Members:  ['value 163', '163', 'value 114', '114', 'value 25', '25', 'value 82', '82', 'value 64', '64']
            Cursor:  47
            Members:  ['value 39', '39', 'value 127', '127', 'value 43', '43', 'value 139', '139', 'value 211', '211']
            Cursor:  0
            Members:  ['value 55', '55', 'value 24', '24', 'value 90', '90', 'value 113', '113']
        """
        args = [key, cursor]
        if match is not None:
            args += ["MATCH", match]
        if count is not None:
            args += ["COUNT", str(count)]

        return cast(
            List[Union[str, List[str]]],
            await self._execute_command(RequestType.ZScan, args),
        )

    async def hscan(
        self,
        key: str,
        cursor: str,
        match: Optional[str] = None,
        count: Optional[int] = None,
    ) -> List[Union[str, List[str]]]:
        """
        Iterates incrementally over a hash.

        See https://valkey.io/commands/hscan for more details.

        Args:
            key (str): The key of the set.
            cursor (str): The cursor that points to the next iteration of results. A value of "0" indicates the start of
                the search.
            match (Optional[str]): The match filter is applied to the result of the command and will only include
                strings that match the pattern specified. If the hash is large enough for scan commands to return only a
                subset of the hash then there could be a case where the result is empty although there are items that
                match the pattern specified. This is due to the default `COUNT` being `10` which indicates that it will
                only fetch and match `10` items from the list.
            count (Optional[int]): `COUNT` is a just a hint for the command for how many elements to fetch from the hash.
                `COUNT` could be ignored until the hash is large enough for the `SCAN` commands to represent the results
                as compact single-allocation packed encoding.

        Returns:
            List[Union[str, List[str]]]: An `Array` of the `cursor` and the subset of the hash held by `key`.
                The first element is always the `cursor` for the next iteration of results. `0` will be the `cursor`
                returned on the last iteration of the hash. The second element is always an `Array` of the subset of the
                hash held in `key`. The `Array` in the second element is always a flattened series of `String` pairs,
                where the value is at even indices and the score is at odd indices.

        Examples:
            # Assume "key" contains a hash with multiple members
            >>> result_cursor = "0"
            >>> while True:
            ...     result = await redis_client.hscan("key", "0", match="*", count=3)
            ...     new_cursor = str(result [0])
            ...     print("Cursor: ", new_cursor)
            ...     print("Members: ", result[1])
            ...     if new_cursor == "0":
            ...         break
            ...     result_cursor = new_cursor
            Cursor:  31
            Members:  ['field 79', 'value 79', 'field 20', 'value 20', 'field 115', 'value 115']
            Cursor:  39
            Members:  ['field 63', 'value 63', 'field 293', 'value 293', 'field 162', 'value 162']
            Cursor:  0
            Members:  ['field 420', 'value 420', 'field 221', 'value 221']
        """
        args = [key, cursor]
        if match is not None:
            args += ["MATCH", match]
        if count is not None:
            args += ["COUNT", str(count)]

        return cast(
            List[Union[str, List[str]]],
            await self._execute_command(RequestType.HScan, args),
        )

    async def fcall(
        self,
        function: str,
        keys: Optional[List[str]] = None,
        arguments: Optional[List[str]] = None,
    ) -> TResult:
        """
        Invokes a previously loaded function.
        See https://redis.io/commands/fcall/ for more details.

        When in cluster mode, all keys in `keys` must map to the same hash slot.

        Args:
            function (str): The function name.
            keys (Optional[List[str]]): A list of keys accessed by the function. To ensure the correct
                execution of functions, both in standalone and clustered deployments, all names of keys
                that a function accesses must be explicitly provided as `keys`.
            arguments (Optional[List[str]]): A list of `function` arguments. `Arguments`
                should not represent names of keys.

        Returns:
            TResult:
                The invoked function's return value.

        Example:
            >>> await client.fcall("Deep_Thought")
                'new_value' # Returns the function's return value.

        Since: Redis version 7.0.0.
        """
        args = []
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
        function: str,
        keys: Optional[List[str]] = None,
        arguments: Optional[List[str]] = None,
    ) -> TResult:
        """
        Invokes a previously loaded read-only function.

        See https://valkey.io/commands/fcall_ro for more details.

        When in cluster mode, all keys in `keys` must map to the same hash slot.

        Args:
            function (str): The function name.
            keys (List[str]): An `array` of keys accessed by the function. To ensure the correct
                execution of functions, all names of keys that a function accesses must be
                explicitly provided as `keys`.
            arguments (List[str]): An `array` of `function` arguments. `arguments` should not
                represent names of keys.

        Returns:
            TResult: The return value depends on the function that was executed.

        Examples:
            >>> await client.fcall_ro("Deep_Thought", ["key1"], ["Answer", "to", "the",
                    "Ultimate", "Question", "of", "Life,", "the", "Universe,", "and", "Everything"])
                42 # The return value on the function that was executed

        Since: Redis version 7.0.0.
        """
        args = []
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

    async def watch(self, keys: List[str]) -> TOK:
        """
        Marks the given keys to be watched for conditional execution of a transaction. Transactions
        will only execute commands if the watched keys are not modified before execution of the
        transaction.

        See https://valkey.io/commands/watch for more details.

        Note:
            When in cluster mode, the command may route to multiple nodes when `keys` map to different hash slots.

        Args:
            keys (List[str]): The keys to watch.

        Returns:
            TOK: A simple "OK" response.

        Examples:
            >>> await client.watch("sampleKey")
                'OK'
            >>> transaction.set("sampleKey", "foobar")
            >>> await redis_client.exec(transaction)
                'OK' # Executes successfully and keys are unwatched.

            >>> await client.watch("sampleKey")
                'OK'
            >>> transaction.set("sampleKey", "foobar")
            >>> await client.set("sampleKey", "hello world")
                'OK'
            >>> await redis_client.exec(transaction)
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
            message (str): Incoming message.
            channel (str): Name of an channel that triggered the message.
            pattern (Optional[str]): Pattern that triggered the message.
        """

        message: str
        channel: str
        pattern: Optional[str]

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
        key1: str,
        key2: str,
    ) -> str:
        """
        Returns the longest common subsequence between strings stored at key1 and key2.

        Note that this is different than the longest common string algorithm, since
        matching characters in the two strings do not need to be contiguous.

        For instance the LCS between "foo" and "fao" is "fo", since scanning the two strings
        from left to right, the longest common set of characters is composed of the first "f" and then the "o".

        See https://valkey.io/commands/lcs for more details.

        Args:
            key1 (str): The key that stores the first string.
            key2 (str): The key that stores the second string.

        Returns:
            A String containing the longest common subsequence between the 2 strings.
            An empty String is returned if the keys do not exist or have no common subsequences.

        Examples:
            >>> await client.mset({"testKey1" : "abcd", "testKey2": "axcd"})
                'OK'
            >>> await client.lcs("testKey1", "testKey2")
                'acd'

        Since: Redis version 7.0.0.
        """
        args = [key1, key2]

        return cast(
            str,
            await self._execute_command(RequestType.LCS, args),
        )

    async def lcs_len(
        self,
        key1: str,
        key2: str,
    ) -> int:
        """
        Returns the length of the longest common subsequence between strings stored at key1 and key2.

        Note that this is different than the longest common string algorithm, since
        matching characters in the two strings do not need to be contiguous.

        For instance the LCS between "foo" and "fao" is "fo", since scanning the two strings
        from left to right, the longest common set of characters is composed of the first "f" and then the "o".

        See https://valkey.io/commands/lcs for more details.

        Args:
            key1 (str): The key that stores the first string.
            key2 (str): The key that stores the second string.

        Returns:
            The length of the longest common subsequence between the 2 strings.

        Examples:
            >>> await client.mset({"testKey1" : "abcd", "testKey2": "axcd"})
                'OK'
            >>> await client.lcs_len("testKey1", "testKey2")
                3  # the length of the longest common subsequence between these 2 strings ("acd") is 3.

        Since: Redis version 7.0.0.
        """
        args = [key1, key2, "LEN"]

        return cast(
            int,
            await self._execute_command(RequestType.LCS, args),
        )

    async def lcs_idx(
        self,
        key1: str,
        key2: str,
        min_match_len: Optional[int] = None,
        with_match_len: Optional[bool] = False,
    ) -> Mapping[str, Union[list[list[Union[list[int], int]]], int]]:
        """
        Returns the indices and length of the longest common subsequence between strings stored at key1 and key2.

        Note that this is different than the longest common string algorithm, since
        matching characters in the two strings do not need to be contiguous.

        For instance the LCS between "foo" and "fao" is "fo", since scanning the two strings
        from left to right, the longest common set of characters is composed of the first "f" and then the "o".

        See https://valkey.io/commands/lcs for more details.

        Args:
            key1 (str): The key that stores the first string.
            key2 (str): The key that stores the second string.
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
                    'matches': [
                        [
                            [4, 7],  # starting and ending indices of the subsequence "1234" in "abcd1234" (testKey1)
                            [5, 8],  # starting and ending indices of the subsequence "1234" in "bcdef1234" (testKey2)
                        ],
                        [
                            [1, 3],  # starting and ending indices of the subsequence "bcd" in "abcd1234" (testKey1)
                            [0, 2],  # starting and ending indices of the subsequence "bcd" in "bcdef1234" (testKey2)
                        ],
                    ],
                    'len': 7  # length of the entire longest common subsequence
                }
            >>> await client.lcs_idx("testKey1", "testKey2", min_match_len=4)
                {
                    'matches': [
                        [
                            [4, 7],
                            [5, 8],
                        ],
                        # the other match with a length of 3 is excluded
                    ],
                    'len': 7
                }
            >>> await client.lcs_idx("testKey1", "testKey2", with_match_len=True)
                {
                    'matches': [
                        [
                            [4, 7],
                            [5, 8],
                            4,  # length of this match ("1234")
                        ],
                        [
                            [1, 3],
                            [0, 2],
                            3,  # length of this match ("bcd")
                        ],
                    ],
                    'len': 7
                }

        Since: Redis version 7.0.0.
        """
        args = [key1, key2, "IDX"]

        if min_match_len is not None:
            args.extend(["MINMATCHLEN", str(min_match_len)])

        if with_match_len:
            args.append("WITHMATCHLEN")

        return cast(
            Mapping[str, Union[list[list[Union[list[int], int]]], int]],
            await self._execute_command(RequestType.LCS, args),
        )

    async def lpos(
        self,
        key: str,
        element: str,
        rank: Optional[int] = None,
        count: Optional[int] = None,
        max_len: Optional[int] = None,
    ) -> Union[int, list[int], None]:
        """
        Returns the index or indexes of element(s) matching `element` in the `key` list. If no match is found,
        None is returned.

        See https://valkey.io/commands/lpos for more details.

        Args:
            key (str): The name of the list.
            element (str): The value to search for within the list.
            rank (Optional[int]): The rank of the match to return.
            count (Optional[int]): The number of matches wanted. A `count` of 0 returns all the matches.
            max_len (Optional[int]): The maximum number of comparisons to make between the element and the items
                                     in the list. A `max_len` of 0 means unlimited amount of comparisons.

        Returns:
            Union[int, list[int], None]: The index of the first occurrence of `element`,
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

        Since: Redis version 6.0.6.
        """
        args = [key, element]

        if rank is not None:
            args.extend(["RANK", str(rank)])

        if count is not None:
            args.extend(["COUNT", str(count)])

        if max_len is not None:
            args.extend(["MAXLEN", str(max_len)])

        return cast(
            Union[int, list[int], None],
            await self._execute_command(RequestType.LPos, args),
        )
