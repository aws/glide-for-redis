# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
from abc import ABC, abstractmethod
from collections.abc import Mapping
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

from glide.async_commands.sorted_set import (
    AggregationType,
    InfBound,
    LexBoundary,
    RangeByIndex,
    RangeByLex,
    RangeByScore,
    ScoreBoundary,
    ScoreFilter,
    _create_z_cmd_store_args,
    _create_zrange_args,
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


class GeospatialData:
    def __init__(self, longitude: float, latitude: float):
        """
        Represents a geographic position defined by longitude and latitude.

        The exact limits, as specified by EPSG:900913 / EPSG:3785 / OSGEO:41001 are the following:
            - Valid longitudes are from -180 to 180 degrees.
            - Valid latitudes are from -85.05112878 to 85.05112878 degrees.

        Args:
            longitude (float): The longitude coordinate.
            latitude (float): The latitude coordinate.
        """
        self.longitude = longitude
        self.latitude = latitude


class StreamTrimOptions(ABC):
    """
    Abstract base class for stream trim options.
    """

    @abstractmethod
    def __init__(
        self,
        threshold: Union[str, int],
        method: str,
        exact: Optional[bool] = None,
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
        if exact is not None and exact and limit is not None:
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
        ]
        if self.exact is not None:
            if self.exact:
                option_args.extend("=")
            else:
                option_args.extend("~")
        option_args.extend([str(self.threshold)])
        if self.limit is not None:
            option_args.extend(["LIMIT", str(self.limit)])
        return option_args


class TrimByMinId(StreamTrimOptions):
    """
    Stream trim option to trim by minimum ID.
    """

    def __init__(
        self,
        threshold: str,
        method: str,
        exact: Optional[bool] = None,
        limit: Optional[int] = None,
    ):
        super().__init__(threshold, method, exact, limit)

    @classmethod
    def create(cls, threshold: str):
        """
        Initialize trim option by minimum ID.

        Args:
            threshold (str): Threshold for trimming by minimum ID.
        """
        return cls(threshold, "MINID", None, None)

    @classmethod
    def create_withexact(cls, exact: bool, threshold: str):
        """
        Initialize trim option by minimum ID.

        Args:
            exact (bool): If `true`, the stream will be trimmed exactly.
                Otherwise the stream will be trimmed in a near-exact manner, which is more efficient.
            threshold (str): Threshold for trimming by minimum ID.
        """
        print(threshold)
        return cls(threshold, "MINID", exact, None)

    @classmethod
    def create_withlimit(cls, threshold: str, limit: int):
        """
        Initialize trim option by minimum ID.

        Args:
            threshold (str): Threshold for trimming by minimum ID.
            limit (int): Max number of entries to be trimmed.
        """
        return cls(threshold, "MINID", False, limit)


class TrimByMaxLen(StreamTrimOptions):
    """
    Stream trim option to trim by maximum length.
    """

    def __init__(
        self,
        threshold: int,
        method: str,
        exact: Optional[bool] = None,
        limit: Optional[int] = None,
    ):
        super().__init__(threshold, method, exact, limit)

    @classmethod
    def create(cls, threshold: int):
        """
        Initialize trim option by maximum length.

        Args:
            threshold (int): Threshold for trimming by maximum length.
        """
        return cls(threshold, "MAXLEN", None, None)

    @classmethod
    def create_withexact(cls, exact: bool, threshold: int):
        """
        Initialize trim option by maximum length.

        Args:
            exact (bool): If `true`, the stream will be trimmed exactly.
                Otherwise the stream will be trimmed in a near-exact manner, which is more efficient.
            threshold (int): Threshold for trimming by maximum length.
        """
        return cls(threshold, "MAXLEN", exact, None)

    @classmethod
    def create_withlimit(cls, threshold: int, limit: int):
        """
        Initialize trim option by maximum length.

        Args:
            threshold (int): Threshold for trimming by maximum length.
            limit (int): Max number of entries to be trimmed.
        """
        return cls(threshold, "MAXLEN", False, limit)


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


class GeoUnit(Enum):
    """
    Enumeration representing distance units options for the `GEODIST` command.
    """

    METERS = "m"
    """
    Represents distance in meters.
    """
    KILOMETERS = "km"
    """
    Represents distance in kilometers.
    """
    MILES = "mi"
    """
    Represents distance in miles.
    """
    FEET = "ft"
    """
    Represents distance in feet.
    """


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


class InsertPosition(Enum):
    BEFORE = "BEFORE"
    AFTER = "AFTER"


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
        keys: Optional[List[str]] = None,
        args: Optional[List[str]] = None,
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
            >>> await client.hset("my_hash", "field")
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
            >>> await client.xtrim("mystream", TrimByMinId.create_withexact(True, "0-2")))
                1 # One entry was deleted from the stream.
        """
        args = [key]
        if options:
            args.extend(options.to_args())

        return cast(int, await self._execute_command(RequestType.XTrim, args))

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

    async def zinterstore(
        self,
        destination: str,
        keys: Union[List[str], List[Tuple[str, float]]],
        aggregation_type: Optional[AggregationType] = None,
    ) -> int:
        """
        Computes the intersection of sorted sets given by the specified `keys` and stores the result in `destination`.
        If `destination` already exists, it is overwritten. Otherwise, a new sorted set will be created.

        When in cluster mode, `destination` and all keys in `keys` must map to the same hash slot.

        See https://valkey.io/commands/zinterstore/ for more details.

        Args:
            destination (str): The key of the destination sorted set.
            keys (Union[List[str], List[Tuple[str, float]]]): The keys of the sorted sets with possible formats:
                List[str] - for keys only.
                List[Tuple[str, float]]] - for weighted keys with score multipliers.
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
                {'member1': 20}  # "member1"  is now stored in "my_sorted_set" with score of 20.
            >>> await client.zinterstore("my_sorted_set", ["key1", "key2"] , AggregationType.MAX )
                1 # Indicates that the sorted set "my_sorted_set" contains one element, and it's score is the maximum score between the sets.
            >>> await client.zrange_withscores("my_sorted_set", RangeByIndex(0, -1))
                {'member1': 10.5}  # "member1"  is now stored in "my_sorted_set" with score of 10.5.
        """
        args = _create_z_cmd_store_args(destination, keys, aggregation_type)
        return cast(
            int,
            await self._execute_command(RequestType.ZInterStore, args),
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

        When in cluster mode, `destination` and all keys in `keys` must map to the same hash slot.

        see https://valkey.io/commands/zunionstore/ for more details.

        Args:
            destination (str): The key of the destination sorted set.
            keys (Union[List[str], List[Tuple[str, float]]]): The keys of the sorted sets with possible formats:
                List[str] - for keys only.
                List[Tuple[str, float]]] - for weighted keys with score multipliers.
            aggregation_type (Optional[AggregationType]): Specifies the aggregation strategy to apply
                when combining the scores of elements. See `AggregationType`.

        Returns:
            int: The number of elements in the resulting sorted set stored at `destination`.

        Examples:
            >>> await client.zadd("key1", {"member1": 10.5, "member2": 8.2})
            >>> await client.zadd("key2", {"member1": 9.5})
            >>> await client.zunionstore("my_sorted_set", ["key1", "key2"])
                2 # Indicates that the sorted set "my_sorted_set" contains two element.
            >>> await client.zrange_withscores("my_sorted_set", RangeByIndex(0, -1))
                {'member1': 20, 'member2': 8.2}
            >>> await client.zunionstore("my_sorted_set", ["key1", "key2"] , AggregationType.MAX )
                2 # Indicates that the sorted set "my_sorted_set" contains two element, and each score is the maximum score between the sets.
            >>> await client.zrange_withscores("my_sorted_set", RangeByIndex(0, -1))
                {'member1': 10.5, 'member2': 8.2}
        """
        args = _create_z_cmd_store_args(destination, keys, aggregation_type)
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
        keys: Optional[List[str]] = None,
        args: Optional[List[str]] = None,
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
            keys (List[str]): The keys that are used in the script.
            args (List[str]): The arguments for the script.

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
