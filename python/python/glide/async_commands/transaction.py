# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import threading
from typing import List, Mapping, Optional, Tuple, TypeVar, Union

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
from glide.async_commands.core import (
    ConditionalChange,
    ExpireOptions,
    ExpiryGetEx,
    ExpirySet,
    FlushMode,
    GeospatialData,
    GeoUnit,
    InfoSection,
    InsertPosition,
    UpdateOptions,
    _build_sort_args,
)
from glide.async_commands.sorted_set import (
    AggregationType,
    GeoSearchByBox,
    GeoSearchByRadius,
    GeoSearchCount,
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
from glide.protobuf.redis_request_pb2 import RequestType

TTransaction = TypeVar("TTransaction", bound="BaseTransaction")


class BaseTransaction:
    """
    Base class encompassing shared commands for both standalone and cluster mode implementations in transaction.

    Command Response:
        The response for each command depends on the executed Redis command. Specific response types
        are documented alongside each method.

    Example:
        transaction = BaseTransaction()
        >>> transaction.set("key", "value").get("key")
        >>> await client.exec(transaction)
        [OK , "value"]
    """

    def __init__(self) -> None:
        self.commands: List[Tuple[RequestType.ValueType, List[str]]] = []
        self.lock = threading.Lock()

    def append_command(
        self: TTransaction, request_type: RequestType.ValueType, args: List[str]
    ) -> TTransaction:
        self.lock.acquire()
        try:
            self.commands.append((request_type, args))
        finally:
            self.lock.release()
        return self

    def clear(self):
        with self.lock:
            self.commands.clear()

    def get(self: TTransaction, key: str) -> TTransaction:
        """
        Get the value associated with the given key, or null if no such value exists.
        See https://redis.io/commands/get/ for details.

        Args:
            key (str): The key to retrieve from the database.

        Command response:
            Optional[str]: If the key exists, returns the value of the key as a string. Otherwise, return None.
        """
        return self.append_command(RequestType.Get, [key])

    def getdel(self: TTransaction, key: str) -> TTransaction:
        """
        Gets a string value associated with the given `key` and deletes the key.

        See https://valkey.io/commands/getdel for more details.

        Args:
            key (str): The `key` to retrieve from the database.

        Command response:
            Optional[str]: If `key` exists, returns the `value` of `key`. Otherwise, returns `None`.
        """
        return self.append_command(RequestType.GetDel, [key])

    def getrange(self: TTransaction, key: str, start: int, end: int) -> TTransaction:
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

        Commands response:
            str: A substring extracted from the value stored at `key`.
        """
        return self.append_command(RequestType.GetRange, [key, str(start), str(end)])

    def set(
        self: TTransaction,
        key: str,
        value: str,
        conditional_set: Union[ConditionalChange, None] = None,
        expiry: Union[ExpirySet, None] = None,
        return_old_value: bool = False,
    ) -> TTransaction:
        """
        Set the given key with the given value. Return value is dependent on the passed options.
            See https://redis.io/commands/set/ for details.

            @example - Set "foo" to "bar" only if "foo" already exists, and set the key expiration to 5 seconds:

                connection.set("foo", "bar", conditional_set=ConditionalChange.ONLY_IF_EXISTS, expiry=Expiry(ExpiryType.SEC, 5))

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

        Command response:
            Optional[str]:
                If the value is successfully set, return OK.
                If value isn't set because of only_if_exists or only_if_does_not_exist conditions, return None.
                If return_old_value is set, return the old value as a string.
        """
        args = [key, value]
        if conditional_set:
            if conditional_set == ConditionalChange.ONLY_IF_EXISTS:
                args.append("XX")
            if conditional_set == ConditionalChange.ONLY_IF_DOES_NOT_EXIST:
                args.append("NX")
        if return_old_value:
            args.append("GET")
        if expiry is not None:
            args.extend(expiry.get_cmd_args())
        return self.append_command(RequestType.Set, args)

    def strlen(self: TTransaction, key: str) -> TTransaction:
        """
        Get the length of the string value stored at `key`.
        See https://redis.io/commands/strlen/ for more details.

        Args:
            key (str): The key to return its length.

        Commands response:
            int: The length of the string value stored at `key`.
                If `key` does not exist, it is treated as an empty string and 0 is returned.
        """
        return self.append_command(RequestType.Strlen, [key])

    def rename(self: TTransaction, key: str, new_key: str) -> TTransaction:
        """
        Renames `key` to `new_key`.
        If `newkey` already exists it is overwritten.
        In Cluster mode, both `key` and `newkey` must be in the same hash slot,
        meaning that in practice only keys that have the same hash tag can be reliably renamed in cluster.
        See https://redis.io/commands/rename/ for more details.

        Args:
            key (str) : The key to rename.
            new_key (str) : The new name of the key.

        Command response:
            OK: If the `key` was successfully renamed, return "OK". If `key` does not exist, the transaction fails with an error.
        """
        return self.append_command(RequestType.Rename, [key, new_key])

    def renamenx(self: TTransaction, key: str, new_key: str) -> TTransaction:
        """
        Renames `key` to `new_key` if `new_key` does not yet exist.

        See https://valkey.io/commands/renamenx for more details.

        Args:
            key (str): The key to rename.
            new_key (str): The new key name.

        Command response:
            bool: True if `key` was renamed to `new_key`, or False if `new_key` already exists.
        """
        return self.append_command(RequestType.RenameNX, [key, new_key])

    def custom_command(self: TTransaction, command_args: List[str]) -> TTransaction:
        """
        Executes a single command, without checking inputs.
        See the [Glide for Redis Wiki](https://github.com/aws/glide-for-redis/wiki/General-Concepts#custom-command)
        for details on the restrictions and limitations of the custom command API.

            @example - Append a command to list of all pub/sub clients:

                transaction.customCommand(["CLIENT", "LIST","TYPE", "PUBSUB"])

        Args:
            command_args (List[str]): List of strings of the command's arguments.
            Every part of the command, including the command name and subcommands, should be added as a separate value in args.

        Command response:
            TResult: The returning value depends on the executed command.
        """
        return self.append_command(RequestType.CustomCommand, command_args)

    def append(self: TTransaction, key: str, value: str) -> TTransaction:
        """
        Appends a value to a key.
        If `key` does not exist it is created and set as an empty string, so `APPEND` will be similar to SET in this special case.

        See https://redis.io/commands/append for more details.

        Args:
            key (str): The key to which the value will be appended.
            value (str): The value to append.

        Commands response:
            int: The length of the string after appending the value.
        """
        return self.append_command(RequestType.Append, [key, value])

    def info(
        self: TTransaction,
        sections: Optional[List[InfoSection]] = None,
    ) -> TTransaction:
        """
        Get information and statistics about the Redis server.
        See https://redis.io/commands/info/ for details.

        Args:
            sections (Optional[List[InfoSection]]): A list of InfoSection values specifying which sections of
            information to retrieve. When no parameter is provided, the default option is assumed.

        Command response:
            str: Returns a string containing the information for the sections requested.
        """
        args = [section.value for section in sections] if sections else []
        return self.append_command(RequestType.Info, args)

    def delete(self: TTransaction, keys: List[str]) -> TTransaction:
        """
        Delete one or more keys from the database. A key is ignored if it does not exist.
        See https://redis.io/commands/del/ for details.

        Args:
            keys (List[str]): A list of keys to be deleted from the database.

        Command response:
            int: The number of keys that were deleted.
        """
        return self.append_command(RequestType.Del, keys)

    def config_get(self: TTransaction, parameters: List[str]) -> TTransaction:
        """
        Get the values of configuration parameters.
        See https://redis.io/commands/config-get/ for details.

        Args:
            parameters (List[str]): A list of configuration parameter names to retrieve values for.

        Command response:
            Dict[str, str]: A dictionary of values corresponding to the configuration parameters.
        """
        return self.append_command(RequestType.ConfigGet, parameters)

    def config_set(
        self: TTransaction, parameters_map: Mapping[str, str]
    ) -> TTransaction:
        """
        Set configuration parameters to the specified values.
        See https://redis.io/commands/config-set/ for details.

        Args:
            parameters_map (Mapping[str, str]): A map consisting of configuration
            parameters and their respective values to set.

        Command response:
            OK: Returns OK if all configurations have been successfully set. Otherwise, the transaction fails with an error.
        """
        parameters: List[str] = []
        for pair in parameters_map.items():
            parameters.extend(pair)
        return self.append_command(RequestType.ConfigSet, parameters)

    def config_resetstat(self: TTransaction) -> TTransaction:
        """
        Resets the statistics reported by Redis using the INFO and LATENCY HISTOGRAM commands.
        See https://redis.io/commands/config-resetstat/ for details.

        Command response:
            OK: a simple OK response.
        """
        return self.append_command(RequestType.ConfigResetStat, [])

    def mset(self: TTransaction, key_value_map: Mapping[str, str]) -> TTransaction:
        """
        Set multiple keys to multiple values in a single atomic operation.
        See https://redis.io/commands/mset/ for more details.

        Args:
            parameters (Mapping[str, str]): A map of key value pairs.

        Command response:
            OK: a simple OK response.
        """
        parameters: List[str] = []
        for pair in key_value_map.items():
            parameters.extend(pair)
        return self.append_command(RequestType.MSet, parameters)

    def msetnx(self: TTransaction, key_value_map: Mapping[str, str]) -> TTransaction:
        """
        Sets multiple keys to values if the key does not exist. The operation is atomic, and if one or
        more keys already exist, the entire operation fails.

        See https://valkey.io/commands/msetnx/ for more details.

        Args:
            key_value_map (Mapping[str, str]): A key-value map consisting of keys and their respective values to set.

        Commands response:
            bool: True if all keys were set. False if no key was set.
        """
        parameters: List[str] = []
        for pair in key_value_map.items():
            parameters.extend(pair)
        return self.append_command(RequestType.MSetNX, parameters)

    def mget(self: TTransaction, keys: List[str]) -> TTransaction:
        """
        Retrieve the values of multiple keys.
        See https://redis.io/commands/mget/ for more details.

        Args:
            keys (List[str]): A list of keys to retrieve values for.

        Command response:
            List[Optional[str]]: A list of values corresponding to the provided keys. If a key is not found,
            its corresponding value in the list will be None.
        """
        return self.append_command(RequestType.MGet, keys)

    def touch(self: TTransaction, keys: List[str]) -> TTransaction:
        """
        Updates the last access time of specified keys.

        See https://valkey.io/commands/touch/ for details.

        Args:
            keys (List[str]): The keys to update last access time.

        Commands response:
            int: The number of keys that were updated, a key is ignored if it doesn't exist.
        """
        return self.append_command(RequestType.Touch, keys)

    def config_rewrite(self: TTransaction) -> TTransaction:
        """
        Rewrite the configuration file with the current configuration.
        See https://redis.io/commands/config-rewrite/ for details.

        Command response:
            OK: OK is returned when the configuration was rewritten properly. Otherwise, the transaction fails with an error.
        """
        return self.append_command(RequestType.ConfigRewrite, [])

    def client_id(self: TTransaction) -> TTransaction:
        """
        Returns the current connection id.
        See https://redis.io/commands/client-id/ for more information.

        Command response:
            int: the id of the client.
        """
        return self.append_command(RequestType.ClientId, [])

    def incr(self: TTransaction, key: str) -> TTransaction:
        """
        Increments the number stored at `key` by one.
        If `key` does not exist, it is set to 0 before performing the
        operation.
        See https://redis.io/commands/incr/ for more details.

        Args:
          key (str): The key to increment its value.

        Command response:
            int: the value of `key` after the increment.
        """
        return self.append_command(RequestType.Incr, [key])

    def incrby(self: TTransaction, key: str, amount: int) -> TTransaction:
        """
        Increments the number stored at `key` by `amount`. If the key does not exist, it is set to 0 before performing
        the operation.
        See https://redis.io/commands/incrby/ for more details.

        Args:
          key (str): The key to increment its value.
          amount (int) : The amount to increment.

        Command response:
            int: The value of `key` after the increment.
        """
        return self.append_command(RequestType.IncrBy, [key, str(amount)])

    def incrbyfloat(self: TTransaction, key: str, amount: float) -> TTransaction:
        """
        Increment the string representing a floating point number stored at `key` by `amount`.
        By using a negative increment value, the value stored at the `key` is decremented.
        If the key does not exist, it is set to 0 before performing the operation.
        See https://redis.io/commands/incrbyfloat/ for more details.

        Args:
          key (str): The key to increment its value.
          amount (float) : The amount to increment.

        Command response:
            float: The value of key after the increment.
        """
        return self.append_command(RequestType.IncrByFloat, [key, str(amount)])

    def ping(self: TTransaction, message: Optional[str] = None) -> TTransaction:
        """
        Ping the Redis server.
        See https://redis.io/commands/ping/ for more details.

        Args:
           message (Optional[str]): An optional message to include in the PING command. If not provided,
            the server will respond with "PONG". If provided, the server will respond with a copy of the message.

        Command response:
           str: "PONG" if `message` is not provided, otherwise return a copy of `message`.
        """
        argument = [] if message is None else [message]
        return self.append_command(RequestType.Ping, argument)

    def decr(self: TTransaction, key: str) -> TTransaction:
        """
        Decrements the number stored at `key` by one. If the key does not exist, it is set to 0 before performing the
        operation.
        See https://redis.io/commands/decr/ for more details.

        Args:
          key (str): The key to decrement its value.

        Command response:
            int: the value of `key` after the decrement.
        """
        return self.append_command(RequestType.Decr, [key])

    def decrby(self: TTransaction, key: str, amount: int) -> TTransaction:
        """
        Decrements the number stored at `key` by `amount`. If the key does not exist, it is set to 0 before performing
        the operation.
        See https://redis.io/commands/decrby/ for more details.

        Args:
          key (str): The key to decrement its value.
         amount (int) : The amount to decrement.

        Command response:
              int: The value of `key` after the decrement.
        """
        return self.append_command(RequestType.DecrBy, [key, str(amount)])

    def setrange(self: TTransaction, key: str, offset: int, value: str) -> TTransaction:
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

        Command response:
            int: The length of the string stored at `key` after it was modified.
        """
        return self.append_command(RequestType.SetRange, [key, str(offset), value])

    def hset(
        self: TTransaction, key: str, field_value_map: Mapping[str, str]
    ) -> TTransaction:
        """
        Sets the specified fields to their respective values in the hash stored at `key`.
        See https://redis.io/commands/hset/ for more details.

        Args:
            key (str): The key of the hash.
            field_value_map (Mapping[str, str]): A field-value map consisting of fields and their corresponding values
            to be set in the hash stored at the specified key.

        Command response:
            int: The number of fields that were added to the hash.
        """
        field_value_list: List[str] = [key]
        for pair in field_value_map.items():
            field_value_list.extend(pair)
        return self.append_command(RequestType.HSet, field_value_list)

    def hget(self: TTransaction, key: str, field: str) -> TTransaction:
        """
        Retrieves the value associated with `field` in the hash stored at `key`.
        See https://redis.io/commands/hget/ for more details.

        Args:
            key (str): The key of the hash.
            field (str): The field whose value should be retrieved.

        Command response:
            Optional[str]: The value associated `field` in the hash.
            Returns None if `field` is not presented in the hash or `key` does not exist.
        """
        return self.append_command(RequestType.HGet, [key, field])

    def hsetnx(
        self: TTransaction,
        key: str,
        field: str,
        value: str,
    ) -> TTransaction:
        """
        Sets `field` in the hash stored at `key` to `value`, only if `field` does not yet exist.
        If `key` does not exist, a new key holding a hash is created.
        If `field` already exists, this operation has no effect.
        See https://redis.io/commands/hsetnx/ for more details.

        Args:
            key (str): The key of the hash.
            field (str): The field to set the value for.
            value (str): The value to set.

        Commands response:
            bool: True if the field was set, False if the field already existed and was not set.
        """
        return self.append_command(RequestType.HSetNX, [key, field, value])

    def hincrby(self: TTransaction, key: str, field: str, amount: int) -> TTransaction:
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

        Command response:
            int: The value of the specified field in the hash stored at `key` after the increment or decrement.
        """
        return self.append_command(RequestType.HIncrBy, [key, field, str(amount)])

    def hincrbyfloat(
        self: TTransaction, key: str, field: str, amount: float
    ) -> TTransaction:
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

        Command response:
            float: The value of the specified field in the hash stored at `key` after the increment as a string.
        """
        return self.append_command(RequestType.HIncrByFloat, [key, field, str(amount)])

    def hexists(self: TTransaction, key: str, field: str) -> TTransaction:
        """
        Check if a field exists in the hash stored at `key`.
        See https://redis.io/commands/hexists/ for more details.

        Args:
            key (str): The key of the hash.
            field (str): The field to check in the hash stored at `key`.

        Command response:
            bool: Returns 'True' if the hash contains the specified field. If the hash does not contain the field,
                or if the key does not exist, it returns 'False'.
        """
        return self.append_command(RequestType.HExists, [key, field])

    def hlen(self: TTransaction, key: str) -> TTransaction:
        """
        Returns the number of fields contained in the hash stored at `key`.

        See https://redis.io/commands/hlen/ for more details.

        Args:
            key (str): The key of the hash.

        Command response:
            int: The number of fields in the hash, or 0 when the key does not exist.
            If `key` holds a value that is not a hash, the transaction fails with an error.
        """
        return self.append_command(RequestType.HLen, [key])

    def client_getname(self: TTransaction) -> TTransaction:
        """
        Get the name of the connection on which the transaction is being executed.
        See https://redis.io/commands/client-getname/ for more details.

        Command response:
            Optional[str]: Returns the name of the client connection as a string if a name is set,
            or None if no name is assigned.
        """
        return self.append_command(RequestType.ClientGetName, [])

    def hgetall(self: TTransaction, key: str) -> TTransaction:
        """
        Returns all fields and values of the hash stored at `key`.
        See https://redis.io/commands/hgetall/ for details.

        Args:
            key (str): The key of the hash.

        Command response:
            Dict[str, str]: A dictionary of fields and their values stored in the hash. Every field name in the list is followed by
            its value.
            If `key` does not exist, it returns an empty dictionary.
        """
        return self.append_command(RequestType.HGetAll, [key])

    def hmget(self: TTransaction, key: str, fields: List[str]) -> TTransaction:
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
        """
        return self.append_command(RequestType.HMGet, [key] + fields)

    def hdel(self: TTransaction, key: str, fields: List[str]) -> TTransaction:
        """
        Remove specified fields from the hash stored at `key`.
        See https://redis.io/commands/hdel/ for more details.

        Args:
            key (str): The key of the hash.
            fields (List[str]): The list of fields to remove from the hash stored at `key`.

        Returns:
            int: The number of fields that were removed from the hash, excluding specified but non-existing fields.
            If `key` does not exist, it is treated as an empty hash, and the function returns 0.
        """
        return self.append_command(RequestType.HDel, [key] + fields)

    def hvals(self: TTransaction, key: str) -> TTransaction:
        """
        Returns all values in the hash stored at `key`.

        See https://redis.io/commands/hvals/ for more details.

        Args:
            key (str): The key of the hash.

        Command response:
            List[str]: A list of values in the hash, or an empty list when the key does not exist.
        """
        return self.append_command(RequestType.HVals, [key])

    def hkeys(self: TTransaction, key: str) -> TTransaction:
        """
        Returns all field names in the hash stored at `key`.

        See https://redis.io/commands/hkeys/ for more details.

        Args:
            key (str): The key of the hash.

        Command response:
            List[str]: A list of field names for the hash, or an empty list when the key does not exist.
        """
        return self.append_command(RequestType.HKeys, [key])

    def hrandfield(self: TTransaction, key: str) -> TTransaction:
        """
        Returns a random field name from the hash value stored at `key`.

        See https://valkey.io/commands/hrandfield for more details.

        Args:
            key (str): The key of the hash.

        Command response:
            Optional[str]: A random field name from the hash stored at `key`.
            If the hash does not exist or is empty, None will be returned.
        """
        return self.append_command(RequestType.HRandField, [key])

    def hrandfield_count(self: TTransaction, key: str, count: int) -> TTransaction:
        """
        Retrieves up to `count` random field names from the hash value stored at `key`.

        See https://valkey.io/commands/hrandfield for more details.

        Args:
            key (str): The key of the hash.
            count (int): The number of field names to return.
                If `count` is positive, returns unique elements.
                If `count` is negative, allows for duplicates elements.

        Command response:
            List[str]: A list of random field names from the hash.
            If the hash does not exist or is empty, the response will be an empty list.
        """
        return self.append_command(RequestType.HRandField, [key, str(count)])

    def hrandfield_withvalues(self: TTransaction, key: str, count: int) -> TTransaction:
        """
        Retrieves up to `count` random field names along with their values from the hash value stored at `key`.

        See https://valkey.io/commands/hrandfield for more details.

        Args:
            key (str): The key of the hash.
            count (int): The number of field names to return.
                If `count` is positive, returns unique elements.
                If `count` is negative, allows for duplicates elements.

        Command response:
            List[List[str]]: A list of `[field_name, value]` lists, where `field_name` is a random field name from the
            hash and `value` is the associated value of the field name.
            If the hash does not exist or is empty, the response will be an empty list.
        """
        return self.append_command(
            RequestType.HRandField, [key, str(count), "WITHVALUES"]
        )

    def hstrlen(self: TTransaction, key: str, field: str) -> TTransaction:
        """
        Returns the string length of the value associated with `field` in the hash stored at `key`.

        See https://valkey.io/commands/hstrlen/ for more details.

        Args:
            key (str): The key of the hash.
            field (str): The field in the hash.

        Commands response:
            int: The string length or 0 if `field` or `key` does not exist.
        """
        return self.append_command(RequestType.HStrlen, [key, field])

    def lpush(self: TTransaction, key: str, elements: List[str]) -> TTransaction:
        """
        Insert all the specified values at the head of the list stored at `key`.
        `elements` are inserted one after the other to the head of the list, from the leftmost element
        to the rightmost element. If `key` does not exist, it is created as empty list before performing the push operations.
        See https://redis.io/commands/lpush/ for more details.

        Args:
            key (str): The key of the list.
            elements (List[str]): The elements to insert at the head of the list stored at `key`.

        Command response:
            int: The length of the list after the push operations.
        """
        return self.append_command(RequestType.LPush, [key] + elements)

    def lpushx(self: TTransaction, key: str, elements: List[str]) -> TTransaction:
        """
        Inserts all the specified values at the head of the list stored at `key`, only if `key` exists and holds a list.
        If `key` is not a list, this performs no operation.

        See https://redis.io/commands/lpushx/ for more details.

        Args:
            key (str): The key of the list.
            elements (List[str]): The elements to insert at the head of the list stored at `key`.

        Command response:
            int: The length of the list after the push operation.
        """
        return self.append_command(RequestType.LPushX, [key] + elements)

    def lpop(self: TTransaction, key: str) -> TTransaction:
        """
        Remove and return the first elements of the list stored at `key`.
        The command pops a single element from the beginning of the list.
        See https://redis.io/commands/lpop/ for details.

        Args:
            key (str): The key of the list.

        Command response:
            Optional[str]: The value of the first element.
            If `key` does not exist, None will be returned.
        """
        return self.append_command(RequestType.LPop, [key])

    def lpop_count(self: TTransaction, key: str, count: int) -> TTransaction:
        """
        Remove and return up to `count` elements from the list stored at `key`, depending on the list's length.
        See https://redis.io/commands/lpop/ for details.

        Args:
            key (str): The key of the list.
            count (int): The count of elements to pop from the list.

        Command response:
            Optional[List[str]]: A a list of popped elements will be returned depending on the list's length.
            If `key` does not exist, None will be returned.
        """
        return self.append_command(RequestType.LPop, [key, str(count)])

    def blpop(self: TTransaction, keys: List[str], timeout: float) -> TTransaction:
        """
        Pops an element from the head of the first list that is non-empty, with the given keys being checked in the
        order that they are given. Blocks the connection when there are no elements to pop from any of the given lists.

        See https://valkey.io/commands/blpop for details.

        BLPOP is a client blocking command, see https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands for more details and best practices.

        Args:
            keys (List[str]): The keys of the lists to pop from.
            timeout (float): The number of seconds to wait for a blocking operation to complete. A value of 0 will block indefinitely.

        Command response:
            Optional[List[str]]: A two-element list containing the key from which the element was popped and the value of the
                popped element, formatted as `[key, value]`. If no element could be popped and the `timeout` expired, returns None.
        """
        return self.append_command(RequestType.BLPop, keys + [str(timeout)])

    def lmpop(
        self: TTransaction,
        keys: List[str],
        direction: ListDirection,
        count: Optional[int] = None,
    ) -> TTransaction:
        """
        Pops one or more elements from the first non-empty list from the provided `keys`.

        See https://valkey.io/commands/lmpop/ for details.

        Args:
            keys (List[str]): An array of keys of lists.
            direction (ListDirection): The direction based on which elements are popped from (`ListDirection.LEFT` or `ListDirection.RIGHT`).
            count (Optional[int]): The maximum number of popped elements. If not provided, defaults to popping a single element.

        Command response:
            Optional[Mapping[str, List[str]]]: A map of `key` name mapped to an array of popped elements, or None if no elements could be popped.

        Since: Redis version 7.0.0.
        """
        args = [str(len(keys)), *keys, direction.value]
        if count is not None:
            args += ["COUNT", str(count)]

        return self.append_command(RequestType.LMPop, args)

    def blmpop(
        self: TTransaction,
        keys: List[str],
        direction: ListDirection,
        timeout: float,
        count: Optional[int] = None,
    ) -> TTransaction:
        """
        Blocks the connection until it pops one or more elements from the first non-empty list from the provided `keys`.

        `BLMPOP` is the blocking variant of `LMPOP`.

        See https://valkey.io/commands/blmpop/ for details.

        Args:
            keys (List[str]): An array of keys of lists.
            direction (ListDirection): The direction based on which elements are popped from (`ListDirection.LEFT` or `ListDirection.RIGHT`).
            timeout (float): The number of seconds to wait for a blocking operation to complete. A value of `0` will block indefinitely.
            count (Optional[int]): The maximum number of popped elements. If not provided, defaults to popping a single element.

        Command response:
            Optional[Mapping[str, List[str]]]: A map of `key` name mapped to an array of popped elements, or None if no elements could be popped and the timeout expired.

        Since: Redis version 7.0.0.
        """
        args = [str(timeout), str(len(keys)), *keys, direction.value]
        if count is not None:
            args += ["COUNT", str(count)]

        return self.append_command(RequestType.BLMPop, args)

    def lrange(self: TTransaction, key: str, start: int, end: int) -> TTransaction:
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

        Command response:
            List[str]: A list of elements within the specified range.
            If `start` exceeds the `end` of the list, or if `start` is greater than `end`, an empty list will be returned.
            If `end` exceeds the actual end of the list, the range will stop at the actual end of the list.
            If `key` does not exist an empty list will be returned.
        """
        return self.append_command(RequestType.LRange, [key, str(start), str(end)])

    def lindex(
        self: TTransaction,
        key: str,
        index: int,
    ) -> TTransaction:
        """
        Returns the element at `index` in the list stored at `key`.

        The index is zero-based, so 0 means the first element, 1 the second element and so on.
        Negative indices can be used to designate elements starting at the tail of the list.
        Here, -1 means the last element, -2 means the penultimate and so forth.

        See https://redis.io/commands/lindex/ for more details.

        Args:
            key (str): The key of the list.
            index (int): The index of the element in the list to retrieve.

        Command response:
            Optional[str]: The element at `index` in the list stored at `key`.
                If `index` is out of range or if `key` does not exist, None is returned.
        """
        return self.append_command(RequestType.LIndex, [key, str(index)])

    def lset(self: TTransaction, key: str, index: int, element: str) -> TTransaction:
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

        Commands response:
            TOK: A simple `OK` response.
        """
        return self.append_command(RequestType.LSet, [key, str(index), element])

    def rpush(self: TTransaction, key: str, elements: List[str]) -> TTransaction:
        """Inserts all the specified values at the tail of the list stored at `key`.
        `elements` are inserted one after the other to the tail of the list, from the leftmost element
        to the rightmost element. If `key` does not exist, it is created as empty list before performing the push operations.
        See https://redis.io/commands/rpush/ for more details.

        Args:
            key (str): The key of the list.
            elements (List[str]): The elements to insert at the tail of the list stored at `key`.

        Command response:
            int: The length of the list after the push operations.
                If `key` holds a value that is not a list, the transaction fails.
        """
        return self.append_command(RequestType.RPush, [key] + elements)

    def rpushx(self: TTransaction, key: str, elements: List[str]) -> TTransaction:
        """
        Inserts all the specified values at the tail of the list stored at `key`, only if `key` exists and holds a list.
        If `key` is not a list, this performs no operation.

        See https://redis.io/commands/rpushx/ for more details.

        Args:
            key (str): The key of the list.
            elements (List[str]): The elements to insert at the tail of the list stored at `key`.

        Command response:
            int: The length of the list after the push operation.
        """
        return self.append_command(RequestType.RPushX, [key] + elements)

    def rpop(self: TTransaction, key: str, count: Optional[int] = None) -> TTransaction:
        """
        Removes and returns the last elements of the list stored at `key`.
        The command pops a single element from the end of the list.
        See https://redis.io/commands/rpop/ for details.

        Args:
            key (str): The key of the list.

        Commands response:
            Optional[str]: The value of the last element.
            If `key` does not exist, None will be returned.
        """
        return self.append_command(RequestType.RPop, [key])

    def rpop_count(self: TTransaction, key: str, count: int) -> TTransaction:
        """
        Removes and returns up to `count` elements from the list stored at `key`, depending on the list's length.
        See https://redis.io/commands/rpop/ for details.

        Args:
            key (str): The key of the list.
            count (int): The count of elements to pop from the list.

        Commands response:
            Optional[List[str]: A list of popped elements will be returned depending on the list's length.
            If `key` does not exist, None will be returned.
        """
        return self.append_command(RequestType.RPop, [key, str(count)])

    def brpop(self: TTransaction, keys: List[str], timeout: float) -> TTransaction:
        """
        Pops an element from the tail of the first list that is non-empty, with the given keys being checked in the
        order that they are given. Blocks the connection when there are no elements to pop from any of the given lists.

        See https://valkey.io/commands/brpop for details.

        BRPOP is a client blocking command, see https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands for more details and best practices.

        Args:
            keys (List[str]): The keys of the lists to pop from.
            timeout (float): The number of seconds to wait for a blocking operation to complete. A value of 0 will block indefinitely.

        Command response:
            Optional[List[str]]: A two-element list containing the key from which the element was popped and the value of the
                popped element, formatted as `[key, value]`. If no element could be popped and the `timeout` expired, returns None.
        """
        return self.append_command(RequestType.BRPop, keys + [str(timeout)])

    def linsert(
        self: TTransaction, key: str, position: InsertPosition, pivot: str, element: str
    ) -> TTransaction:
        """
        Inserts `element` in the list at `key` either before or after the `pivot`.

        See https://valkey.io/commands/linsert/ for details.

        Args:
            key (str): The key of the list.
            position (InsertPosition): The relative position to insert into - either `InsertPosition.BEFORE` or
                `InsertPosition.AFTER` the `pivot`.
            pivot (str): An element of the list.
            element (str): The new element to insert.

        Command response:
            int: The list length after a successful insert operation.
                If the `key` doesn't exist returns `-1`.
                If the `pivot` wasn't found, returns `0`.
        """
        return self.append_command(
            RequestType.LInsert, [key, position.value, pivot, element]
        )

    def lmove(
        self: TTransaction,
        source: str,
        destination: str,
        where_from: ListDirection,
        where_to: ListDirection,
    ) -> TTransaction:
        """
        Atomically pops and removes the left/right-most element to the list stored at `source`
        depending on `where_from`, and pushes the element at the first/last element of the list
        stored at `destination` depending on `where_to`.

        See https://valkey.io/commands/lmove/ for details.

        Args:
            source (str): The key to the source list.
            destination (str): The key to the destination list.
            where_from (ListDirection): The direction to remove the element from (`ListDirection.LEFT` or `ListDirection.RIGHT`).
            where_to (ListDirection): The direction to add the element to (`ListDirection.LEFT` or `ListDirection.RIGHT`).

        Command response:
            Optional[str]: The popped element, or `None` if `source` does not exist.

        Since: Redis version 6.2.0.
        """
        return self.append_command(
            RequestType.LMove, [source, destination, where_from.value, where_to.value]
        )

    def blmove(
        self: TTransaction,
        source: str,
        destination: str,
        where_from: ListDirection,
        where_to: ListDirection,
        timeout: float,
    ) -> TTransaction:
        """
        Blocks the connection until it pops atomically and removes the left/right-most element to the
        list stored at `source` depending on `where_from`, and pushes the element at the first/last element
        of the list stored at `destination` depending on `where_to`.
        `blmove` is the blocking variant of `lmove`.

        See https://valkey.io/commands/blmove/ for details.

        Args:
            source (str): The key to the source list.
            destination (str): The key to the destination list.
            where_from (ListDirection): The direction to remove the element from (`ListDirection.LEFT` or `ListDirection.RIGHT`).
            where_to (ListDirection): The direction to add the element to (`ListDirection.LEFT` or `ListDirection.RIGHT`).
            timeout (float): The number of seconds to wait for a blocking operation to complete. A value of `0` will block indefinitely.

        Command response:
            Optional[str]: The popped element, or `None` if `source` does not exist or if the operation timed-out.

        Since: Redis version 6.2.0.
        """
        return self.append_command(
            RequestType.BLMove,
            [source, destination, where_from.value, where_to.value, str(timeout)],
        )

    def sadd(self: TTransaction, key: str, members: List[str]) -> TTransaction:
        """
        Add specified members to the set stored at `key`.
        Specified members that are already a member of this set are ignored.
        If `key` does not exist, a new set is created before adding `members`.
        See https://redis.io/commands/sadd/ for more details.

        Args:
            key (str): The key where members will be added to its set.
            members (List[str]): A list of members to add to the set stored at key.

        Command response:
            int: The number of members that were added to the set, excluding members already present.
        """
        return self.append_command(RequestType.SAdd, [key] + members)

    def srem(self: TTransaction, key: str, members: List[str]) -> TTransaction:
        """
        Remove specified members from the set stored at `key`.
        Specified members that are not a member of this set are ignored.
        See https://redis.io/commands/srem/ for details.

        Args:
            key (str): The key from which members will be removed.
            members (List[str]): A list of members to remove from the set stored at key.

        Commands response:
            int: The number of members that were removed from the set, excluding non-existing members.
                If `key` does not exist, it is treated as an empty set and this command returns 0.
        """
        return self.append_command(RequestType.SRem, [key] + members)

    def smembers(self: TTransaction, key: str) -> TTransaction:
        """
        Retrieve all the members of the set value stored at `key`.
        See https://redis.io/commands/smembers/ for details.

        Args:
            key (str): The key from which to retrieve the set members.

        Commands response:
            Set[str]: A set of all members of the set.
                If `key` does not exist an empty list will be returned.
        """
        return self.append_command(RequestType.SMembers, [key])

    def scard(self: TTransaction, key: str) -> TTransaction:
        """
        Retrieve the set cardinality (number of elements) of the set stored at `key`.
        See https://redis.io/commands/scard/ for details.

        Args:
            key (str): The key from which to retrieve the number of set members.

        Commands response:
            int: The cardinality (number of elements) of the set, or 0 if the key does not exist.
        """
        return self.append_command(RequestType.SCard, [key])

    def spop(self: TTransaction, key: str) -> TTransaction:
        """
        Removes and returns one random member from the set stored at `key`.

        See https://valkey-io.github.io/commands/spop/ for more details.
        To pop multiple members, see `spop_count`.

        Args:
            key (str): The key of the set.

        Commands response:
            Optional[str]: The value of the popped member.
            If `key` does not exist, None will be returned.
        """
        return self.append_command(RequestType.SPop, [key])

    def spop_count(self: TTransaction, key: str, count: int) -> TTransaction:
        """
        Removes and returns up to `count` random members from the set stored at `key`, depending on the set's length.

        See https://valkey-io.github.io/commands/spop/ for more details.
        To pop a single member, see `spop`.

        Args:
            key (str): The key of the set.
            count (int): The count of the elements to pop from the set.

        Commands response:
            Set[str]: A set of popped elements will be returned depending on the set's length.
                  If `key` does not exist, an empty set will be returned.
        """
        return self.append_command(RequestType.SPop, [key, str(count)])

    def sismember(
        self: TTransaction,
        key: str,
        member: str,
    ) -> TTransaction:
        """
        Returns if `member` is a member of the set stored at `key`.

        See https://redis.io/commands/sismember/ for more details.

        Args:
            key (str): The key of the set.
            member (str): The member to check for existence in the set.

        Commands response:
            bool: True if the member exists in the set, False otherwise.
            If `key` doesn't exist, it is treated as an empty set and the command returns False.
        """
        return self.append_command(RequestType.SIsMember, [key, member])

    def smove(
        self: TTransaction,
        source: str,
        destination: str,
        member: str,
    ) -> TTransaction:
        """
        Moves `member` from the set at `source` to the set at `destination`, removing it from the source set. Creates a
        new destination set if needed. The operation is atomic.

        See https://valkey.io/commands/smove for more details.

        Args:
            source (str): The key of the set to remove the element from.
            destination (str): The key of the set to add the element to.
            member (str): The set element to move.

        Command response:
            bool: True on success, or False if the `source` set does not exist or the element is not a member of the source set.
        """
        return self.append_command(RequestType.SMove, [source, destination, member])

    def sunion(self: TTransaction, keys: List[str]) -> TTransaction:
        """
        Gets the union of all the given sets.

        See https://valkey.io/commands/sunion for more details.

        Args:
            keys (List[str]): The keys of the sets.

        Commands response:
            Set[str]: A set of members which are present in at least one of the given sets.
                If none of the sets exist, an empty set will be returned.
        """
        return self.append_command(RequestType.SUnion, keys)

    def sunionstore(
        self: TTransaction,
        destination: str,
        keys: List[str],
    ) -> TTransaction:
        """
        Stores the members of the union of all given sets specified by `keys` into a new set at `destination`.

        See https://valkey.io/commands/sunionstore for more details.

        Args:
            destination (str): The key of the destination set.
            keys (List[str]): The keys from which to retrieve the set members.

        Command response:
            int: The number of elements in the resulting set.
        """
        return self.append_command(RequestType.SUnionStore, [destination] + keys)

    def sinter(self: TTransaction, keys: List[str]) -> TTransaction:
        """
        Gets the intersection of all the given sets.

        See https://valkey.io/commands/sinter for more details.

        Args:
            keys (List[str]): The keys of the sets.

        Command response:
            Set[str]: A set of members which are present in all given sets.
                If one or more sets do not exist, an empty set will be returned.
        """
        return self.append_command(RequestType.SInter, keys)

    def sinterstore(
        self: TTransaction, destination: str, keys: List[str]
    ) -> TTransaction:
        """
        Stores the members of the intersection of all given sets specified by `keys` into a new set at `destination`.

        See https://valkey.io/commands/sinterstore for more details.

        Args:
            destination (str): The key of the destination set.
            keys (List[str]): The keys from which to retrieve the set members.

        Command response:
            int: The number of elements in the resulting set.
        """
        return self.append_command(RequestType.SInterStore, [destination] + keys)

    def sintercard(
        self: TTransaction, keys: List[str], limit: Optional[int] = None
    ) -> TTransaction:
        """
        Gets the cardinality of the intersection of all the given sets.
        Optionally, a `limit` can be specified to stop the computation early if the intersection cardinality reaches the specified limit.

        See https://valkey.io/commands/sintercard for more details.

        Args:
            keys (List[str]): A list of keys representing the sets to intersect.
            limit (Optional[int]): An optional limit to the maximum number of intersecting elements to count.
                If specified, the computation stops as soon as the cardinality reaches this limit.

        Command response:
            int: The number of elements in the resulting set of the intersection.
        """
        args = [str(len(keys))]
        args += keys
        if limit is not None:
            args += ["LIMIT", str(limit)]
        return self.append_command(RequestType.SInterCard, args)

    def sdiff(self: TTransaction, keys: List[str]) -> TTransaction:
        """
        Computes the difference between the first set and all the successive sets in `keys`.

        See https://valkey.io/commands/sdiff for more details.

        Args:
            keys (List[str]): The keys of the sets to diff.

        Command response:
            Set[str]: A set of elements representing the difference between the sets.
                If any of the keys in `keys` do not exist, they are treated as empty sets.
        """
        return self.append_command(RequestType.SDiff, keys)

    def sdiffstore(
        self: TTransaction, destination: str, keys: List[str]
    ) -> TTransaction:
        """
        Stores the difference between the first set and all the successive sets in `keys` into a new set at
        `destination`.

        See https://valkey.io/commands/sdiffstore for more details.

        Args:
            destination (str): The key of the destination set.
            keys (List[str]): The keys of the sets to diff.

        Command response:
            int: The number of elements in the resulting set.
        """
        return self.append_command(RequestType.SDiffStore, [destination] + keys)

    def smismember(self: TTransaction, key: str, members: List[str]) -> TTransaction:
        """
        Checks whether each member is contained in the members of the set stored at `key`.

        See https://valkey.io/commands/smismember for more details.

        Args:
            key (str): The key of the set to check.
            members (List[str]): A list of members to check for existence in the set.

        Command response:
            List[bool]: A list of bool values, each indicating if the respective member exists in the set.
        """
        return self.append_command(RequestType.SMIsMember, [key] + members)

    def ltrim(self: TTransaction, key: str, start: int, end: int) -> TTransaction:
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

        Commands response:
            TOK: A simple "OK" response.
                If `start` exceeds the end of the list, or if `start` is greater than `end`, the result will be an empty list
                (which causes `key` to be removed).
                If `end` exceeds the actual end of the list, it will be treated like the last element of the list.
                f `key` does not exist, the response will be "OK" without changes to the database.
        """
        return self.append_command(RequestType.LTrim, [key, str(start), str(end)])

    def lrem(self: TTransaction, key: str, count: int, element: str) -> TTransaction:
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

        Commands response:
            int: The number of removed elements.
                If `key` does not exist, 0 is returned.
        """
        return self.append_command(RequestType.LRem, [key, str(count), element])

    def llen(self: TTransaction, key: str) -> TTransaction:
        """
        Get the length of the list stored at `key`.
        See https://redis.io/commands/llen/ for details.

        Args:
            key (str): The key of the list.

        Commands response:
            int: The length of the list at the specified key.
                If `key` does not exist, it is interpreted as an empty list and 0 is returned.
        """
        return self.append_command(RequestType.LLen, [key])

    def exists(self: TTransaction, keys: List[str]) -> TTransaction:
        """
        Returns the number of keys in `keys` that exist in the database.
        See https://redis.io/commands/exists/ for more details.

        Args:
            keys (List[str]): The list of keys to check.

        Commands response:
            int: The number of keys that exist. If the same existing key is mentioned in `keys` multiple times,
                it will be counted multiple times.
        """
        return self.append_command(RequestType.Exists, keys)

    def unlink(self: TTransaction, keys: List[str]) -> TTransaction:
        """
        Unlink (delete) multiple keys from the database.
        A key is ignored if it does not exist.
        This command, similar to DEL, removes specified keys and ignores non-existent ones.
        However, this command does not block the server, while [DEL](https://redis.io/commands/del) does.
        See https://redis.io/commands/unlink/ for more details.

        Args:
            keys (List[str]): The list of keys to unlink.

        Commands response:
            int: The number of keys that were unlinked.
        """
        return self.append_command(RequestType.Unlink, keys)

    def expire(
        self: TTransaction,
        key: str,
        seconds: int,
        option: Optional[ExpireOptions] = None,
    ) -> TTransaction:
        """
        Sets a timeout on `key` in seconds. After the timeout has expired, the key will automatically be deleted.
        If `key` already has an existing expire set, the time to live is updated to the new value.
        If `seconds` is a non-positive number, the key will be deleted rather than expired.
        The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
        See https://redis.io/commands/expire/ for more details.

        Args:
            key (str): The key to set a timeout on.
            seconds (int): The timeout in seconds.
            option (Optional[ExpireOptions]): The expire option.

        Commands response:
            bool: 'True' if the timeout was set, 'False' if the timeout was not set (e.g., the key doesn't exist or the operation is
                skipped due to the provided arguments).
        """
        args: List[str] = (
            [key, str(seconds)] if option is None else [key, str(seconds), option.value]
        )
        return self.append_command(RequestType.Expire, args)

    def expireat(
        self: TTransaction,
        key: str,
        unix_seconds: int,
        option: Optional[ExpireOptions] = None,
    ) -> TTransaction:
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

        Commands response:
            bool: 'True' if the timeout was set, 'False' if the timeout was not set (e.g., the key doesn't exist or the operation is
                skipped due to the provided arguments).
        """
        args = (
            [key, str(unix_seconds)]
            if option is None
            else [key, str(unix_seconds), option.value]
        )
        return self.append_command(RequestType.ExpireAt, args)

    def pexpire(
        self: TTransaction,
        key: str,
        milliseconds: int,
        option: Optional[ExpireOptions] = None,
    ) -> TTransaction:
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

        Commands response:
            bool: 'True' if the timeout was set, 'False' if the timeout was not set (e.g., the key doesn't exist or the operation is
                skipped due to the provided arguments).
        """
        args = (
            [key, str(milliseconds)]
            if option is None
            else [key, str(milliseconds), option.value]
        )
        return self.append_command(RequestType.PExpire, args)

    def pexpireat(
        self: TTransaction,
        key: str,
        unix_milliseconds: int,
        option: Optional[ExpireOptions] = None,
    ) -> TTransaction:
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

        Commands response:
            bool: 'True' if the timeout was set, 'False' if the timeout was not set (e.g., the key doesn't exist or the operation is
                skipped due to the provided arguments).
        """
        args = (
            [key, str(unix_milliseconds)]
            if option is None
            else [key, str(unix_milliseconds), option.value]
        )
        return self.append_command(RequestType.PExpireAt, args)

    def expiretime(self: TTransaction, key: str) -> TTransaction:
        """
        Returns the absolute Unix timestamp (since January 1, 1970) at which
        the given `key` will expire, in seconds.
        To get the expiration with millisecond precision, use `pexpiretime`.

        See https://valkey.io/commands/expiretime/ for details.

        Args:
            key (str): The `key` to determine the expiration value of.

        Commands response:
            int: The expiration Unix timestamp in seconds, -2 if `key` does not exist or -1 if `key` exists but has no associated expire.

        Since: Redis version 7.0.0.
        """
        return self.append_command(RequestType.ExpireTime, [key])

    def pexpiretime(self: TTransaction, key: str) -> TTransaction:
        """
        Returns the absolute Unix timestamp (since January 1, 1970) at which
        the given `key` will expire, in milliseconds.

        See https://valkey.io/commands/pexpiretime/ for details.

        Args:
            key (str): The `key` to determine the expiration value of.

        Commands response:
            int: The expiration Unix timestamp in milliseconds, -2 if `key` does not exist, or -1 if `key` exists but has no associated expiration.

        Since: Redis version 7.0.0.
        """
        return self.append_command(RequestType.PExpireTime, [key])

    def ttl(self: TTransaction, key: str) -> TTransaction:
        """
        Returns the remaining time to live of `key` that has a timeout.
        See https://redis.io/commands/ttl/ for more details.

        Args:
            key (str): The key to return its timeout.

        Commands response:
            int: TTL in seconds, -2 if `key` does not exist or -1 if `key` exists but has no associated expire.
        """
        return self.append_command(RequestType.TTL, [key])

    def pttl(
        self: TTransaction,
        key: str,
    ) -> TTransaction:
        """
        Returns the remaining time to live of `key` that has a timeout, in milliseconds.
        See https://redis.io/commands/pttl for more details.

        Args:
            key (str): The key to return its timeout.

        Commands Response:
            int: TTL in milliseconds. -2 if `key` does not exist, -1 if `key` exists but has no associated expire.
        """
        return self.append_command(RequestType.PTTL, [key])

    def persist(
        self: TTransaction,
        key: str,
    ) -> TTransaction:
        """
        Remove the existing timeout on `key`, turning the key from volatile (a key with an expire set) to
        persistent (a key that will never expire as no timeout is associated).

        See https://redis.io/commands/persist/ for more details.

        Args:
            key (str): TThe key to remove the existing timeout on.

        Commands response:
            bool: False if `key` does not exist or does not have an associated timeout, True if the timeout has been removed.
        """
        return self.append_command(RequestType.Persist, [key])

    def echo(self: TTransaction, message: str) -> TTransaction:
        """
        Echoes the provided `message` back.

        See https://redis.io/commands/echo for more details.

        Args:
            message (str): The message to be echoed back.

        Commands response:
            str: The provided `message`.
        """
        return self.append_command(RequestType.Echo, [message])

    def lastsave(self: TTransaction) -> TTransaction:
        """
        Returns the Unix time of the last DB save timestamp or startup timestamp if no save was made since then.

        See https://valkey.io/commands/lastsave for more details.

        Command response:
            int: The Unix time of the last successful DB save.
        """
        return self.append_command(RequestType.LastSave, [])

    def type(self: TTransaction, key: str) -> TTransaction:
        """
         Returns the string representation of the type of the value stored at `key`.

         See https://redis.io/commands/type/ for more details.

         Args:
             key (str): The key to check its data type.

        Commands response:
            str: If the key exists, the type of the stored value is returned.
            Otherwise, a "none" string is returned.
        """
        return self.append_command(RequestType.Type, [key])

    def function_load(
        self: TTransaction, library_code: str, replace: bool = False
    ) -> TTransaction:
        """
        Loads a library to Redis.

        See https://valkey.io/docs/latest/commands/function-load/ for more details.

        Args:
            library_code (str): The source code that implements the library.
            replace (bool): Whether the given library should overwrite a library with the same name if
                it already exists.

        Commands response:
            str: The library name that was loaded.

        Since: Redis 7.0.0.
        """
        return self.append_command(
            RequestType.FunctionLoad,
            ["REPLACE", library_code] if replace else [library_code],
        )

    def function_flush(
        self: TTransaction, mode: Optional[FlushMode] = None
    ) -> TTransaction:
        """
        Deletes all function libraries.

        See https://valkey.io/docs/latest/commands/function-flush/ for more details.

        Args:
            mode (Optional[FlushMode]): The flushing mode, could be either `SYNC` or `ASYNC`.

        Commands response:
            TOK: A simple `OK`.

        Since: Redis 7.0.0.
        """
        return self.append_command(
            RequestType.FunctionFlush,
            [mode.value] if mode else [],
        )

    def function_delete(self: TTransaction, library_name: str) -> TTransaction:
        """
        Deletes a library and all its functions.

        See https://valkey.io/docs/latest/commands/function-delete/ for more details.

        Args:
            library_code (str): The libary name to delete

        Commands response:
            TOK: A simple `OK`.

        Since: Redis 7.0.0.
        """
        return self.append_command(
            RequestType.FunctionDelete,
            [library_name],
        )

    def fcall(
        self: TTransaction,
        function: str,
        keys: Optional[List[str]] = None,
        arguments: Optional[List[str]] = None,
    ) -> TTransaction:
        """
        Invokes a previously loaded function.
        See https://redis.io/commands/fcall/ for more details.

        Args:
            function (str): The function name.
            keys (Optional[List[str]]): A list of keys accessed by the function. To ensure the correct
                execution of functions, both in standalone and clustered deployments, all names of keys
                that a function accesses must be explicitly provided as `keys`.
            arguments (Optional[List[str]]): A list of `function` arguments. `Arguments`
                should not represent names of keys.

        Command Response:
            TResult:
                The invoked function's return value.

        Since: Redis version 7.0.0.
        """
        args = []
        if keys is not None:
            args.extend([function, str(len(keys))] + keys)
        else:
            args.extend([function, str(0)])
        if arguments is not None:
            args.extend(arguments)
        return self.append_command(RequestType.FCall, args)

    def fcall_ro(
        self: TTransaction,
        function: str,
        keys: Optional[List[str]] = None,
        arguments: Optional[List[str]] = None,
    ) -> TTransaction:
        """
        Invokes a previously loaded read-only function.

        See https://valkey.io/commands/fcall_ro for more details.

        Args:
            function (str): The function name.
            keys (List[str]): An `array` of keys accessed by the function. To ensure the correct
                execution of functions, all names of keys that a function accesses must be
                explicitly provided as `keys`.
            arguments (List[str]): An `array` of `function` arguments. `arguments` should not
                represent names of keys.

        Command Response:
            TResult: The return value depends on the function that was executed.

        Since: Redis version 7.0.0.
        """
        args = []
        if keys is not None:
            args.extend([function, str(len(keys))] + keys)
        else:
            args.extend([function, str(0)])
        if arguments is not None:
            args.extend(arguments)
        return self.append_command(RequestType.FCallReadOnly, args)

    def xadd(
        self: TTransaction,
        key: str,
        values: List[Tuple[str, str]],
        options: StreamAddOptions = StreamAddOptions(),
    ) -> TTransaction:
        """
        Adds an entry to the specified stream stored at `key`. If the `key` doesn't exist, the stream is created.

        See https://valkey.io/commands/xadd for more details.

        Args:
            key (str): The key of the stream.
            values (List[Tuple[str, str]]): Field-value pairs to be added to the entry.
            options (Optional[StreamAddOptions]): Additional options for adding entries to the stream. Default to None. sSee `StreamAddOptions`.

        Commands response:
            str: The id of the added entry, or None if `options.make_stream` is set to False and no stream with the matching `key` exists.
        """
        args = [key]
        if options:
            args.extend(options.to_args())
        args.extend([field for pair in values for field in pair])

        return self.append_command(RequestType.XAdd, args)

    def xdel(self: TTransaction, key: str, ids: List[str]) -> TTransaction:
        """
        Removes the specified entries by id from a stream, and returns the number of entries deleted.

        See https://valkey.io/commands/xdel for more details.

        Args:
            key (str): The key of the stream.
            ids (List[str]): An array of entry ids.

        Command response:
            int: The number of entries removed from the stream. This number may be less than the number of entries in
                `ids`, if the specified `ids` don't exist in the stream.
        """
        return self.append_command(RequestType.XDel, [key] + ids)

    def xtrim(
        self: TTransaction,
        key: str,
        options: StreamTrimOptions,
    ) -> TTransaction:
        """
        Trims the stream stored at `key` by evicting older entries.

        See https://valkey.io/commands/xtrim for more details.

        Args:
            key (str): The key of the stream.
            options (StreamTrimOptions): Options detailing how to trim the stream. See `StreamTrimOptions`.

        Commands response:
            int: TThe number of entries deleted from the stream. If `key` doesn't exist, 0 is returned.
        """
        args = [key]
        if options:
            args.extend(options.to_args())

        return self.append_command(RequestType.XTrim, args)

    def xlen(self: TTransaction, key: str) -> TTransaction:
        """
        Returns the number of entries in the stream stored at `key`.

        See https://valkey.io/commands/xlen for more details.

        Args:
            key (str): The key of the stream.

        Command response:
            int: The number of entries in the stream. If `key` does not exist, returns 0.
        """
        return self.append_command(RequestType.XLen, [key])

    def xrange(
        self: TTransaction,
        key: str,
        start: StreamRangeBound,
        end: StreamRangeBound,
        count: Optional[int] = None,
    ) -> TTransaction:
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

        Command response:
            Optional[Mapping[str, List[List[str]]]]: A mapping of stream IDs to stream entry data, where entry data is a
                list of pairings with format `[[field, entry], [field, entry], ...]`. Returns None if the range arguments
                are not applicable.
        """
        args = [key, start.to_arg(), end.to_arg()]
        if count is not None:
            args.extend(["COUNT", str(count)])

        return self.append_command(RequestType.XRange, args)

    def xrevrange(
        self: TTransaction,
        key: str,
        end: StreamRangeBound,
        start: StreamRangeBound,
        count: Optional[int] = None,
    ) -> TTransaction:
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

        Command response:
            Optional[Mapping[str, List[List[str]]]]: A mapping of stream IDs to stream entry data, where entry data is a
                list of pairings with format `[[field, entry], [field, entry], ...]`. Returns None if the range arguments
                are not applicable.
        """
        args = [key, end.to_arg(), start.to_arg()]
        if count is not None:
            args.extend(["COUNT", str(count)])

        return self.append_command(RequestType.XRevRange, args)

    def xread(
        self: TTransaction,
        keys_and_ids: Mapping[str, str],
        options: Optional[StreamReadOptions] = None,
    ) -> TTransaction:
        """
        Reads entries from the given streams.

        See https://valkey.io/commands/xread for more details.

        Args:
            keys_and_ids (Mapping[str, str]): A mapping of keys and entry IDs to read from. The mapping is composed of a
                stream's key and the ID of the entry after which the stream will be read.
            options (Optional[StreamReadOptions]): Options detailing how to read the stream.

        Command response:
            Optional[Mapping[str, Mapping[str, List[List[str]]]]]: A mapping of stream keys, to a mapping of stream IDs,
                to a list of pairings with format `[[field, entry], [field, entry], ...]`.
                None will be returned under the following conditions:
                - All key-ID pairs in `keys_and_ids` have either a non-existing key or a non-existing ID, or there are no entries after the given ID.
                - The `BLOCK` option is specified and the timeout is hit.
        """
        args = [] if options is None else options.to_args()
        args.append("STREAMS")
        args.extend([key for key in keys_and_ids.keys()])
        args.extend([value for value in keys_and_ids.values()])

        return self.append_command(RequestType.XRead, args)

    def xgroup_create(
        self: TTransaction,
        key: str,
        group_name: str,
        group_id: str,
        options: Optional[StreamGroupOptions] = None,
    ) -> TTransaction:
        """
        Creates a new consumer group uniquely identified by `group_name` for the stream stored at `key`.

        See https://valkey.io/commands/xgroup-create for more details.

        Args:
            key (str): The key of the stream.
            group_name (str): The newly created consumer group name.
            group_id (str): The stream entry ID that specifies the last delivered entry in the stream from the new
                group’s perspective. The special ID "$" can be used to specify the last entry in the stream.
            options (Optional[StreamGroupOptions]): Options for creating the stream group.

        Command response:
            TOK: A simple "OK" response.
        """
        args = [key, group_name, group_id]
        if options is not None:
            args.extend(options.to_args())

        return self.append_command(RequestType.XGroupCreate, args)

    def xgroup_destroy(self: TTransaction, key: str, group_name: str) -> TTransaction:
        """
        Destroys the consumer group `group_name` for the stream stored at `key`.

        See https://valkey.io/commands/xgroup-destroy for more details.

        Args:
            key (str): The key of the stream.
            group_name (str): The consumer group name to delete.

        Command response:
            bool: True if the consumer group was destroyed. Otherwise, returns False.
        """
        return self.append_command(RequestType.XGroupDestroy, [key, group_name])

    def xgroup_create_consumer(
        self: TTransaction, key: str, group_name: str, consumer_name: str
    ) -> TTransaction:
        """
        Creates a consumer named `consumer_name` in the consumer group `group_name` for the stream stored at `key`.

        See https://valkey.io/commands/xgroup-createconsumer for more details.

        Args:
            key (str): The key of the stream.
            group_name (str): The consumer group name.
            consumer_name (str): The newly created consumer.

        Command response:
            bool: True if the consumer is created. Otherwise, returns False.
        """
        return self.append_command(
            RequestType.XGroupCreateConsumer, [key, group_name, consumer_name]
        )

    def xgroup_del_consumer(
        self: TTransaction, key: str, group_name: str, consumer_name: str
    ) -> TTransaction:
        """
        Deletes a consumer named `consumer_name` in the consumer group `group_name` for the stream stored at `key`.

        See https://valkey.io/commands/xgroup-delconsumer for more details.

        Args:
            key (str): The key of the stream.
            group_name (str): The consumer group name.
            consumer_name (str): The consumer to delete.

        Command response:
            int: The number of pending messages the `consumer` had before it was deleted.
        """
        return self.append_command(
            RequestType.XGroupDelConsumer, [key, group_name, consumer_name]
        )

    def xgroup_set_id(
        self: TTransaction,
        key: str,
        group_name: str,
        stream_id: str,
        entries_read_id: Optional[str] = None,
    ) -> TTransaction:
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

        Command response:
            TOK: A simple "OK" response.
        """
        args = [key, group_name, stream_id]
        if entries_read_id is not None:
            args.extend(["ENTRIESREAD", entries_read_id])

        return self.append_command(RequestType.XGroupSetId, args)

    def xreadgroup(
        self: TTransaction,
        keys_and_ids: Mapping[str, str],
        group_name: str,
        consumer_name: str,
        options: Optional[StreamReadGroupOptions] = None,
    ) -> TTransaction:
        """
        Reads entries from the given streams owned by a consumer group.

        See https://valkey.io/commands/xreadgroup for more details.

        Args:
            keys_and_ids (Mapping[str, str]): A mapping of stream keys to stream entry IDs to read from. The special ">"
                ID returns messages that were never delivered to any other consumer. Any other valid ID will return
                entries pending for the consumer with IDs greater than the one provided.
            group_name (str): The consumer group name.
            consumer_name (str): The consumer name. The consumer will be auto-created if it does not already exist.
            options (Optional[StreamReadGroupOptions]): Options detailing how to read the stream.

        Command response:
            Optional[Mapping[str, Mapping[str, Optional[List[List[str]]]]]]: A mapping of stream keys, to a mapping of
                stream IDs, to a list of pairings with format `[[field, entry], [field, entry], ...]`.
                Returns None if the BLOCK option is given and a timeout occurs, or if there is no stream that can be served.
        """
        args = ["GROUP", group_name, consumer_name]
        if options is not None:
            args.extend(options.to_args())

        args.append("STREAMS")
        args.extend([key for key in keys_and_ids.keys()])
        args.extend([value for value in keys_and_ids.values()])

        return self.append_command(RequestType.XReadGroup, args)

    def xack(
        self: TTransaction,
        key: str,
        group_name: str,
        ids: List[str],
    ) -> TTransaction:
        """
        Removes one or multiple messages from the Pending Entries List (PEL) of a stream consumer group.
        This command should be called on pending messages so that such messages do not get processed again by the
        consumer group.

        See https://valkey.io/commands/xack for more details.

        Args:
            key (str): The key of the stream.
            group_name (str): The consumer group name.
            ids (List[str]): The stream entry IDs to acknowledge and consume for the given consumer group.

        Command response:
            int: The number of messages that were successfully acknowledged.
        """
        return self.append_command(RequestType.XAck, [key, group_name] + ids)

    def xpending(
        self: TTransaction,
        key: str,
        group_name: str,
    ) -> TTransaction:
        """
        Returns stream message summary information for pending messages for the given consumer group.

        See https://valkey.io/commands/xpending for more details.

        Args:
            key (str): The key of the stream.
            group_name (str): The consumer group name.

        Command response:
            List[Union[int, str, List[List[str]], None]]: A list that includes the summary of pending messages, with the
                format `[num_group_messages, start_id, end_id, [[consumer_name, num_consumer_messages]]]`, where:
                - `num_group_messages`: The total number of pending messages for this consumer group.
                - `start_id`: The smallest ID among the pending messages.
                - `end_id`: The greatest ID among the pending messages.
                - `[[consumer_name, num_consumer_messages]]`: A 2D list of every consumer in the consumer group with at
                least one pending message, and the number of pending messages it has.

                If there are no pending messages for the given consumer group, `[0, None, None, None]` will be returned.
        """
        return self.append_command(RequestType.XPending, [key, group_name])

    def xpending_range(
        self: TTransaction,
        key: str,
        group_name: str,
        start: StreamRangeBound,
        end: StreamRangeBound,
        count: int,
        options: Optional[StreamPendingOptions] = None,
    ) -> TTransaction:
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

        Command response:
            List[List[Union[str, int]]]: A list of lists, where each inner list is a length 4 list containing extended
                message information with the format `[[id, consumer_name, time_elapsed, num_delivered]]`, where:
                - `id`: The ID of the message.
                - `consumer_name`: The name of the consumer that fetched the message and has still to acknowledge it. We
                call it the current owner of the message.
                - `time_elapsed`: The number of milliseconds that elapsed since the last time this message was delivered
                to this consumer.
                - `num_delivered`: The number of times this message was delivered.
        """
        args = _create_xpending_range_args(key, group_name, start, end, count, options)
        return self.append_command(RequestType.XPending, args)

    def xautoclaim(
        self: TTransaction,
        key: str,
        group_name: str,
        consumer_name: str,
        min_idle_time_ms: int,
        start: str,
        count: Optional[int] = None,
    ) -> TTransaction:
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

        Command response:
            List[Union[str, Mapping[str, List[List[str]]], List[str]]]: A list containing the following elements:
                - A stream ID to be used as the start argument for the next call to `XAUTOCLAIM`. This ID is equivalent
                to the next ID in the stream after the entries that were scanned, or "0-0" if the entire stream was
                scanned.
                - A mapping of the claimed entries, with the keys being the claimed entry IDs and the values being a
                2D list of the field-value pairs in the format `[[field1, value1], [field2, value2], ...]`.
                - If you are using Redis 7.0.0 or above, the response list will also include a list containing the
                message IDs that were in the Pending Entries List but no longer exist in the stream. These IDs are
                deleted from the Pending Entries List.

        Since: Redis version 6.2.0.
        """
        args = [key, group_name, consumer_name, str(min_idle_time_ms), start]
        if count is not None:
            args.extend(["COUNT", str(count)])

        return self.append_command(RequestType.XAutoClaim, args)

    def xautoclaim_just_id(
        self: TTransaction,
        key: str,
        group_name: str,
        consumer_name: str,
        min_idle_time_ms: int,
        start: str,
        count: Optional[int] = None,
    ) -> TTransaction:
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

        Command response:
            List[Union[str, List[str]]]: A list containing the following elements:
                - A stream ID to be used as the start argument for the next call to `XAUTOCLAIM`. This ID is equivalent
                to the next ID in the stream after the entries that were scanned, or "0-0" if the entire stream was
                scanned.
                - A list of the IDs for the claimed entries.
                - If you are using Redis 7.0.0 or above, the response list will also include a list containing the
                message IDs that were in the Pending Entries List but no longer exist in the stream. These IDs are
                deleted from the Pending Entries List.

        Since: Redis version 6.2.0.
        """
        args = [key, group_name, consumer_name, str(min_idle_time_ms), start]
        if count is not None:
            args.extend(["COUNT", str(count)])

        args.append("JUSTID")

        return self.append_command(RequestType.XAutoClaim, args)

    def geoadd(
        self: TTransaction,
        key: str,
        members_geospatialdata: Mapping[str, GeospatialData],
        existing_options: Optional[ConditionalChange] = None,
        changed: bool = False,
    ) -> TTransaction:
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

        Commands response:
            int: The number of elements added to the sorted set.
            If `changed` is set, returns the number of elements updated in the sorted set.
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

        return self.append_command(RequestType.GeoAdd, args)

    def geodist(
        self: TTransaction,
        key: str,
        member1: str,
        member2: str,
        unit: Optional[GeoUnit] = None,
    ) -> TTransaction:
        """
        Returns the distance between two members in the geospatial index stored at `key`.

        See https://valkey.io/commands/geodist for more details.

        Args:
            key (str): The key of the sorted set.
            member1 (str): The name of the first member.
            member2 (str): The name of the second member.
            unit (Optional[GeoUnit]): The unit of distance measurement. See `GeoUnit`.
                If not specified, the default unit is meters.

        Commands response:
            Optional[float]: The distance between `member1` and `member2`.
            If one or both members do not exist, or if the key does not exist, returns None.
        """
        args = [key, member1, member2]
        if unit:
            args.append(unit.value)

        return self.append_command(RequestType.GeoDist, args)

    def geohash(self: TTransaction, key: str, members: List[str]) -> TTransaction:
        """
        Returns the GeoHash strings representing the positions of all the specified members in the sorted set stored at
        `key`.

        See https://valkey.io/commands/geohash for more details.

        Args:
            key (str): The key of the sorted set.
            members (List[str]): The list of members whose GeoHash strings are to be retrieved.

        Commands response:
            List[Optional[str]]: A list of GeoHash strings representing the positions of the specified members stored at `key`.
            If a member does not exist in the sorted set, a None value is returned for that member.
        """
        return self.append_command(RequestType.GeoHash, [key] + members)

    def geopos(
        self: TTransaction,
        key: str,
        members: List[str],
    ) -> TTransaction:
        """
        Returns the positions (longitude and latitude) of all the given members of a geospatial index in the sorted set stored at
        `key`.

        See https://valkey.io/commands/geopos for more details.

        Args:
            key (str): The key of the sorted set.
            members (List[str]): The members for which to get the positions.

        Commands response:
            List[Optional[List[float]]]: A list of positions (longitude and latitude) corresponding to the given members.
            If a member does not exist, its position will be None.
        """
        return self.append_command(RequestType.GeoPos, [key] + members)

    def geosearch(
        self: TTransaction,
        key: str,
        search_from: Union[str, GeospatialData],
        seach_by: Union[GeoSearchByRadius, GeoSearchByBox],
        order_by: Optional[OrderBy] = None,
        count: Optional[GeoSearchCount] = None,
        with_coord: bool = False,
        with_dist: bool = False,
        with_hash: bool = False,
    ) -> TTransaction:
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

        Command Response:
            List[Union[str, List[Union[str, float, int, List[float]]]]]: By default, returns a list of members (locations) names.
            If any of `with_coord`, `with_dist` or `with_hash` are True, returns an array of arrays, we're each sub array represents a single item in the following order:
                (str): The member (location) name.
                (float): The distance from the center as a floating point number, in the same unit specified in the radius, if `with_dist` is set to True.
                (int): The Geohash integer, if `with_hash` is set to True.
                List[float]: The coordinates as a two item [longitude,latitude] array, if `with_coord` is set to True.

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

        return self.append_command(RequestType.GeoSearch, args)

    def geosearchstore(
        self: TTransaction,
        destination: str,
        source: str,
        search_from: Union[str, GeospatialData],
        search_by: Union[GeoSearchByRadius, GeoSearchByBox],
        count: Optional[GeoSearchCount] = None,
        store_dist: bool = False,
    ) -> TTransaction:
        """
        Searches for members in a sorted set stored at `key` representing geospatial data within a circular or rectangular area and stores the result in `destination`.
        If `destination` already exists, it is overwritten. Otherwise, a new sorted set will be created.

        To get the result directly, see `geosearch`.

        See https://valkey.io/commands/geosearch/ for more details.

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

        Commands response:
            int: The number of elements in the resulting sorted set stored at `destination`.s

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

        return self.append_command(RequestType.GeoSearchStore, args)

    def zadd(
        self: TTransaction,
        key: str,
        members_scores: Mapping[str, float],
        existing_options: Optional[ConditionalChange] = None,
        update_condition: Optional[UpdateOptions] = None,
        changed: bool = False,
    ) -> TTransaction:
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

        Commands response:
            int: The number of elements added to the sorted set.
            If `changed` is set, returns the number of elements updated in the sorted set.
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

        return self.append_command(RequestType.ZAdd, args)

    def zadd_incr(
        self: TTransaction,
        key: str,
        member: str,
        increment: float,
        existing_options: Optional[ConditionalChange] = None,
        update_condition: Optional[UpdateOptions] = None,
    ) -> TTransaction:
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

        Commands response:
            Optional[float]: The score of the member.
            If there was a conflict with choosing the XX/NX/LT/GT options, the operation aborts and None is returned.
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
        return self.append_command(RequestType.ZAdd, args)

    def zcard(self: TTransaction, key: str) -> TTransaction:
        """
        Returns the cardinality (number of elements) of the sorted set stored at `key`.

        See https://redis.io/commands/zcard/ for more details.

        Args:
            key (str): The key of the sorted set.

        Commands response:
            int: The number of elements in the sorted set.
            If `key` does not exist, it is treated as an empty sorted set, and the command returns 0.
        """
        return self.append_command(RequestType.ZCard, [key])

    def zcount(
        self: TTransaction,
        key: str,
        min_score: Union[InfBound, ScoreBoundary],
        max_score: Union[InfBound, ScoreBoundary],
    ) -> TTransaction:
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

        Commands response:
            int: The number of members in the specified score range.
            If key does not exist, 0 is returned.
            If `max_score` < `min_score`, 0 is returned.
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
        return self.append_command(RequestType.ZCount, [key, score_min, score_max])

    def zincrby(
        self: TTransaction, key: str, increment: float, member: str
    ) -> TTransaction:
        """
        Increments the score of `member` in the sorted set stored at `key` by `increment`.
        If `member` does not exist in the sorted set, it is added with `increment` as its score.
        If `key` does not exist, a new sorted set is created with the specified member as its sole member.

        See https://valkey.io/commands/zincrby/ for more details.

        Args:
            key (str): The key of the sorted set.
            increment (float): The score increment.
            member (str): A member of the sorted set.

        Commands response:
            float: The new score of `member`.
        """
        return self.append_command(RequestType.ZIncrBy, [key, str(increment), member])

    def zpopmax(
        self: TTransaction, key: str, count: Optional[int] = None
    ) -> TTransaction:
        """
        Removes and returns the members with the highest scores from the sorted set stored at `key`.
        If `count` is provided, up to `count` members with the highest scores are removed and returned.
        Otherwise, only one member with the highest score is removed and returned.

        See https://redis.io/commands/zpopmax for more details.

        Args:
            key (str): The key of the sorted set.
            count (Optional[int]): Specifies the quantity of members to pop. If not specified, pops one member.
            If `count` is higher than the sorted set's cardinality, returns all members and their scores, ordered from highest to lowest.

        Commands response:
            Mapping[str, float]: A map of the removed members and their scores, ordered from the one with the highest score to the one with the lowest.
            If `key` doesn't exist, it will be treated as an empy sorted set and the command returns an empty map.
        """
        return self.append_command(
            RequestType.ZPopMax, [key, str(count)] if count else [key]
        )

    def bzpopmax(self: TTransaction, keys: List[str], timeout: float) -> TTransaction:
        """
        Pops the member with the highest score from the first non-empty sorted set, with the given keys being checked in
        the order that they are given. Blocks the connection when there are no members to remove from any of the given
        sorted sets.

        `BZPOPMAX` is the blocking variant of `ZPOPMAX`.

        `BZPOPMAX` is a client blocking command, see https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands for more details and best practices.

        See https://valkey.io/commands/bzpopmax for more details.

        Args:
            keys (List[str]): The keys of the sorted sets.
            timeout (float): The number of seconds to wait for a blocking operation to complete.
                A value of 0 will block indefinitely.

        Command response:
            Optional[List[Union[str, float]]]: An array containing the key where the member was popped out, the member itself,
                and the member score. If no member could be popped and the `timeout` expired, returns None.
        """
        return self.append_command(RequestType.BZPopMax, keys + [str(timeout)])

    def zpopmin(
        self: TTransaction, key: str, count: Optional[int] = None
    ) -> TTransaction:
        """
        Removes and returns the members with the lowest scores from the sorted set stored at `key`.
        If `count` is provided, up to `count` members with the lowest scores are removed and returned.
        Otherwise, only one member with the lowest score is removed and returned.

        See https://redis.io/commands/zpopmin for more details.

        Args:
            key (str): The key of the sorted set.
            count (Optional[int]): Specifies the quantity of members to pop. If not specified, pops one member.
            If `count` is higher than the sorted set's cardinality, returns all members and their scores.

        Commands response:
            Mapping[str, float]: A map of the removed members and their scores, ordered from the one with the lowest score to the one with the highest.
            If `key` doesn't exist, it will be treated as an empty sorted set and the command returns an empty map.
        """
        return self.append_command(
            RequestType.ZPopMin, [key, str(count)] if count else [key]
        )

    def bzpopmin(self: TTransaction, keys: List[str], timeout: float) -> TTransaction:
        """
        Pops the member with the lowest score from the first non-empty sorted set, with the given keys being checked in
        the order that they are given. Blocks the connection when there are no members to remove from any of the given
        sorted sets.

        `BZPOPMIN` is the blocking variant of `ZPOPMIN`.

        `BZPOPMIN` is a client blocking command, see https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands for more details and best practices.

        See https://valkey.io/commands/bzpopmin for more details.

        Args:
            keys (List[str]): The keys of the sorted sets.
            timeout (float): The number of seconds to wait for a blocking operation to complete.
                A value of 0 will block indefinitely.

        Command response:
            Optional[List[Union[str, float]]]: An array containing the key where the member was popped out, the member itself,
                and the member score. If no member could be popped and the `timeout` expired, returns None.
        """
        return self.append_command(RequestType.BZPopMin, keys + [str(timeout)])

    def zrange(
        self: TTransaction,
        key: str,
        range_query: Union[RangeByIndex, RangeByLex, RangeByScore],
        reverse: bool = False,
    ) -> TTransaction:
        """
        Returns the specified range of elements in the sorted set stored at `key`.

        ZRANGE can perform different types of range queries: by index (rank), by the score, or by lexicographical order.

        See https://redis.io/commands/zrange/ for more details.

        Args:
            key (str): The key of the sorted set.
            range_query (Union[RangeByIndex, RangeByLex, RangeByScore]): The range query object representing the type of range query to perform.
                - For range queries by index (rank), use RangeByIndex.
                - For range queries by lexicographical order, use RangeByLex.
                - For range queries by score, use RangeByScore.
            reverse (bool): If True, reverses the sorted set, with index 0 as the element with the highest score.

        Commands response:
            List[str]: A list of elements within the specified range.
            If `key` does not exist, it is treated as an empty sorted set, and the command returns an empty array.
        """
        args = _create_zrange_args(key, range_query, reverse, with_scores=False)

        return self.append_command(RequestType.ZRange, args)

    def zrange_withscores(
        self: TTransaction,
        key: str,
        range_query: Union[RangeByIndex, RangeByScore],
        reverse: bool = False,
    ) -> TTransaction:
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

        Commands response:
            Mapping[str , float]: A map of elements and their scores within the specified range.
            If `key` does not exist, it is treated as an empty sorted set, and the command returns an empty map.
        """
        args = _create_zrange_args(key, range_query, reverse, with_scores=True)

        return self.append_command(RequestType.ZRange, args)

    def zrangestore(
        self: TTransaction,
        destination: str,
        source: str,
        range_query: Union[RangeByIndex, RangeByLex, RangeByScore],
        reverse: bool = False,
    ) -> TTransaction:
        """
        Stores a specified range of elements from the sorted set at `source`, into a new sorted set at `destination`. If
        `destination` doesn't exist, a new sorted set is created; if it exists, it's overwritten.

        ZRANGESTORE can perform different types of range queries: by index (rank), by the score, or by lexicographical
        order.

        See https://valkey.io/commands/zrangestore for more details.

        Args:
            destination (str): The key for the destination sorted set.
            source (str): The key of the source sorted set.
            range_query (Union[RangeByIndex, RangeByLex, RangeByScore]): The range query object representing the type of range query to perform.
                - For range queries by index (rank), use RangeByIndex.
                - For range queries by lexicographical order, use RangeByLex.
                - For range queries by score, use RangeByScore.
            reverse (bool): If True, reverses the sorted set, with index 0 as the element with the highest score.

        Command response:
            int: The number of elements in the resulting sorted set.
        """
        args = _create_zrange_args(source, range_query, reverse, False, destination)

        return self.append_command(RequestType.ZRangeStore, args)

    def zrank(
        self: TTransaction,
        key: str,
        member: str,
    ) -> TTransaction:
        """
        Returns the rank of `member` in the sorted set stored at `key`, with scores ordered from low to high.

        See https://redis.io/commands/zrank for more details.

        To get the rank of `member` with its score, see `zrank_withscore`.

        Args:
            key (str): The key of the sorted set.
            member (str): The member whose rank is to be retrieved.

        Commands response:
            Optional[int]: The rank of `member` in the sorted set.
            If `key` doesn't exist, or if `member` is not present in the set, None will be returned.
        """
        return self.append_command(RequestType.ZRank, [key, member])

    def zrank_withscore(
        self: TTransaction,
        key: str,
        member: str,
    ) -> TTransaction:
        """
        Returns the rank of `member` in the sorted set stored at `key` with its score, where scores are ordered from the lowest to highest.

        See https://redis.io/commands/zrank for more details.

        Args:
            key (str): The key of the sorted set.
            member (str): The member whose rank is to be retrieved.

        Commands response:
            Optional[List[Union[int, float]]]: A list containing the rank and score of `member` in the sorted set.
            If `key` doesn't exist, or if `member` is not present in the set, None will be returned.

        Since: Redis version 7.2.0.
        """
        return self.append_command(RequestType.ZRank, [key, member, "WITHSCORE"])

    def zrevrank(self: TTransaction, key: str, member: str) -> TTransaction:
        """
        Returns the rank of `member` in the sorted set stored at `key`, where scores are ordered from the highest to
        lowest, starting from `0`.

        To get the rank of `member` with its score, see `zrevrank_withscore`.

        See https://valkey.io/commands/zrevrank for more details.

        Args:
            key (str): The key of the sorted set.
            member (str): The member whose rank is to be retrieved.

        Command response:
            Optional[int]: The rank of `member` in the sorted set, where ranks are ordered from high to low based on scores.
                If `key` doesn't exist, or if `member` is not present in the set, `None` will be returned.
        """
        return self.append_command(RequestType.ZRevRank, [key, member])

    def zrevrank_withscore(self: TTransaction, key: str, member: str) -> TTransaction:
        """
        Returns the rank of `member` in the sorted set stored at `key` with its score, where scores are ordered from the
        highest to lowest, starting from `0`.

        See https://valkey.io/commands/zrevrank for more details.

        Args:
            key (str): The key of the sorted set.
            member (str): The member whose rank is to be retrieved.

        Command response:
            Optional[List[Union[int, float]]]: A list containing the rank (as `int`) and score (as `float`) of `member`
                in the sorted set, where ranks are ordered from high to low based on scores.
                If `key` doesn't exist, or if `member` is not present in the set, `None` will be returned.

        Since: Redis version 7.2.0.
        """
        return self.append_command(RequestType.ZRevRank, [key, member, "WITHSCORE"])

    def zrem(
        self: TTransaction,
        key: str,
        members: List[str],
    ) -> TTransaction:
        """
        Removes the specified members from the sorted set stored at `key`.
        Specified members that are not a member of this set are ignored.

        See https://redis.io/commands/zrem/ for more details.

        Args:
            key (str): The key of the sorted set.
            members (List[str]): A list of members to remove from the sorted set.

        Commands response:
            int: The number of members that were removed from the sorted set, not including non-existing members.
            If `key` does not exist, it is treated as an empty sorted set, and the command returns 0.
        """
        return self.append_command(RequestType.ZRem, [key] + members)

    def zremrangebyscore(
        self: TTransaction,
        key: str,
        min_score: Union[InfBound, ScoreBoundary],
        max_score: Union[InfBound, ScoreBoundary],
    ) -> TTransaction:
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

        Commands response:
            int: The number of members that were removed from the sorted set.
            If `key` does not exist, it is treated as an empty sorted set, and the command returns 0.
            If `min_score` is greater than `max_score`, 0 is returned.
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
        return self.append_command(
            RequestType.ZRemRangeByScore, [key, score_min, score_max]
        )

    def zremrangebylex(
        self: TTransaction,
        key: str,
        min_lex: Union[InfBound, LexBoundary],
        max_lex: Union[InfBound, LexBoundary],
    ) -> TTransaction:
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

        Command response:
            int: The number of members that were removed from the sorted set.
                If `key` does not exist, it is treated as an empty sorted set, and the command returns `0`.
                If `min_lex` is greater than `max_lex`, `0` is returned.
        """
        min_lex_arg = (
            min_lex.value["lex_arg"] if type(min_lex) == InfBound else min_lex.value
        )
        max_lex_arg = (
            max_lex.value["lex_arg"] if type(max_lex) == InfBound else max_lex.value
        )

        return self.append_command(
            RequestType.ZRemRangeByLex, [key, min_lex_arg, max_lex_arg]
        )

    def zremrangebyrank(
        self: TTransaction,
        key: str,
        start: int,
        end: int,
    ) -> TTransaction:
        """
        Removes all elements in the sorted set stored at `key` with rank between `start` and `end`.
        Both `start` and `end` are zero-based indexes with 0 being the element with the lowest score.
        These indexes can be negative numbers, where they indicate offsets starting at the element with the highest score.

        See https://valkey.io/commands/zremrangebyrank/ for more details.

        Args:
            key (str): The key of the sorted set.
            start (int): The starting point of the range.
            end (int): The end of the range.

        Command response:
            int: The number of elements that were removed.
                If `start` exceeds the end of the sorted set, or if `start` is greater than `end`, `0` is returned.
                If `end` exceeds the actual end of the sorted set, the range will stop at the actual end of the sorted set.
                If `key` does not exist, `0` is returned.
        """
        return self.append_command(
            RequestType.ZRemRangeByRank, [key, str(start), str(end)]
        )

    def zlexcount(
        self: TTransaction,
        key: str,
        min_lex: Union[InfBound, LexBoundary],
        max_lex: Union[InfBound, LexBoundary],
    ) -> TTransaction:
        """
        Returns the number of members in the sorted set stored at `key` with lexographical values between `min_lex` and `max_lex`.

        See https://redis.io/commands/zlexcount/ for more details.

        Args:
            key (str): The key of the sorted set.
            min_lex (Union[InfBound, LexBoundary]): The minimum lexicographical value to count from.
                Can be an instance of InfBound representing positive/negative infinity,
                or LexBoundary representing a specific lexicographical value and inclusivity.
            max_lex (Union[InfBound, LexBoundary]): The maximum lexicographical value to count up to.
                Can be an instance of InfBound representing positive/negative infinity,
                or LexBoundary representing a specific lexicographical value and inclusivity.

        Command response:
            int: The number of members in the specified lexicographical range.
                If `key` does not exist, it is treated as an empty sorted set, and the command returns `0`.
                If `max_lex < min_lex`, `0` is returned.
        """
        min_lex_arg = (
            min_lex.value["lex_arg"] if type(min_lex) == InfBound else min_lex.value
        )
        max_lex_arg = (
            max_lex.value["lex_arg"] if type(max_lex) == InfBound else max_lex.value
        )

        return self.append_command(
            RequestType.ZLexCount, [key, min_lex_arg, max_lex_arg]
        )

    def zscore(self: TTransaction, key: str, member: str) -> TTransaction:
        """
        Returns the score of `member` in the sorted set stored at `key`.

        See https://redis.io/commands/zscore/ for more details.

        Args:
            key (str): The key of the sorted set.
            member (str): The member whose score is to be retrieved.

        Commands response:
            Optional[float]: The score of the member.
            If `member` does not exist in the sorted set, None is returned.
            If `key` does not exist,  None is returned.
        """
        return self.append_command(RequestType.ZScore, [key, member])

    def zmscore(self: TTransaction, key: str, members: List[str]) -> TTransaction:
        """
        Returns the scores associated with the specified `members` in the sorted set stored at `key`.

        See https://valkey.io/commands/zmscore for more details.

        Args:
            key (str): The key of the sorted set.
            members (List[str]): A list of members in the sorted set.

        Command response:
            List[Optional[float]]: A list of scores corresponding to `members`.
                If a member does not exist in the sorted set, the corresponding value in the list will be None.
        """
        return self.append_command(RequestType.ZMScore, [key] + members)

    def zdiff(self: TTransaction, keys: List[str]) -> TTransaction:
        """
        Returns the difference between the first sorted set and all the successive sorted sets.
        To get the elements with their scores, see `zdiff_withscores`.

        See https://valkey.io/commands/zdiff for more details.

        Args:
            keys (List[str]): The keys of the sorted sets.

        Command response:
            List[str]: A list of elements representing the difference between the sorted sets.
                If the first key does not exist, it is treated as an empty sorted set, and the command returns an
                empty list.
        """
        return self.append_command(RequestType.ZDiff, [str(len(keys))] + keys)

    def zdiff_withscores(self: TTransaction, keys: List[str]) -> TTransaction:
        """
        Returns the difference between the first sorted set and all the successive sorted sets, with the associated scores.

        See https://valkey.io/commands/zdiff for more details.

        Args:
            keys (List[str]): The keys of the sorted sets.

        Command response:
            Mapping[str, float]: A mapping of elements and their scores representing the difference between the sorted sets.
                If the first `key` does not exist, it is treated as an empty sorted set, and the command returns an
                empty list.
        """
        return self.append_command(
            RequestType.ZDiff, [str(len(keys))] + keys + ["WITHSCORES"]
        )

    def zdiffstore(
        self: TTransaction, destination: str, keys: List[str]
    ) -> TTransaction:
        """
        Calculates the difference between the first sorted set and all the successive sorted sets at `keys` and stores
        the difference as a sorted set to `destination`, overwriting it if it already exists. Non-existent keys are
        treated as empty sets.

        See https://valkey.io/commands/zdiffstore for more details.

        Args:
            destination (str): The key for the resulting sorted set.
            keys (List[str]): The keys of the sorted sets to compare.

        Command response:
            int: The number of members in the resulting sorted set stored at `destination`.
        """
        return self.append_command(
            RequestType.ZDiffStore, [destination, str(len(keys))] + keys
        )

    def zinter(
        self: TTransaction,
        keys: List[str],
    ) -> TTransaction:
        """
        Computes the intersection of sorted sets given by the specified `keys` and returns a list of intersecting elements.

        See https://valkey.io/commands/zinter/ for more details.

        Args:
            keys (List[str]): The keys of the sorted sets.

        Command response:
            List[str]: The resulting array of intersecting elements.
        """
        return self.append_command(RequestType.ZInter, [str(len(keys))] + keys)

    def zinter_withscores(
        self: TTransaction,
        keys: Union[List[str], List[Tuple[str, float]]],
        aggregation_type: Optional[AggregationType] = None,
    ) -> TTransaction:
        """
        Computes the intersection of sorted sets given by the specified `keys` and returns a sorted set of intersecting elements with scores.

        See https://valkey.io/commands/zinter/ for more details.

        Args:
            keys (Union[List[str], List[Tuple[str, float]]]): The keys of the sorted sets with possible formats:
                List[str] - for keys only.
                List[Tuple[str, float]] - for weighted keys with score multipliers.
            aggregation_type (Optional[AggregationType]): Specifies the aggregation strategy to apply
                when combining the scores of elements. See `AggregationType`.

        Command response:
            Mapping[str, float]: The resulting sorted set with scores.
        """
        args = _create_zinter_zunion_cmd_args(keys, aggregation_type)
        args.append("WITHSCORES")
        return self.append_command(RequestType.ZInter, args)

    def zinterstore(
        self: TTransaction,
        destination: str,
        keys: Union[List[str], List[Tuple[str, float]]],
        aggregation_type: Optional[AggregationType] = None,
    ) -> TTransaction:
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

        Command response:
            int: The number of elements in the resulting sorted set stored at `destination`.
        """
        args = _create_zinter_zunion_cmd_args(keys, aggregation_type, destination)
        return self.append_command(RequestType.ZInterStore, args)

    def zunion(
        self: TTransaction,
        keys: List[str],
    ) -> TTransaction:
        """
        Computes the union of sorted sets given by the specified `keys` and returns a list of union elements.

        See https://valkey.io/commands/zunion/ for more details.

        Args:
            keys (List[str]): The keys of the sorted sets.

        Command response:
            List[str]: The resulting array of union elements.
        """
        return self.append_command(RequestType.ZUnion, [str(len(keys))] + keys)

    def zunion_withscores(
        self: TTransaction,
        keys: Union[List[str], List[Tuple[str, float]]],
        aggregation_type: Optional[AggregationType] = None,
    ) -> TTransaction:
        """
        Computes the union of sorted sets given by the specified `keys` and returns a sorted set of union elements with scores.

        See https://valkey.io/commands/zunion/ for more details.

        Args:
            keys (Union[List[str], List[Tuple[str, float]]]): The keys of the sorted sets with possible formats:
                List[str] - for keys only.
                List[Tuple[str, float]] - for weighted keys with score multipliers.
            aggregation_type (Optional[AggregationType]): Specifies the aggregation strategy to apply
                when combining the scores of elements. See `AggregationType`.

        Command response:
            Mapping[str, float]: The resulting sorted set with scores.
        """
        args = _create_zinter_zunion_cmd_args(keys, aggregation_type)
        args.append("WITHSCORES")
        return self.append_command(RequestType.ZUnion, args)

    def zunionstore(
        self: TTransaction,
        destination: str,
        keys: Union[List[str], List[Tuple[str, float]]],
        aggregation_type: Optional[AggregationType] = None,
    ) -> TTransaction:
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

        Command response:
            int: The number of elements in the resulting sorted set stored at `destination`.
        """
        args = _create_zinter_zunion_cmd_args(keys, aggregation_type, destination)
        return self.append_command(RequestType.ZUnionStore, args)

    def zrandmember(self: TTransaction, key: str) -> TTransaction:
        """
        Returns a random member from the sorted set stored at 'key'.

        See https://valkey.io/commands/zrandmember for more details.

        Args:
            key (str): The key of the sorted set.

        Command response:
            Optional[str]: A random member from the sorted set.
                If the sorted set does not exist or is empty, the response will be None.
        """
        return self.append_command(RequestType.ZRandMember, [key])

    def zrandmember_count(self: TTransaction, key: str, count: int) -> TTransaction:
        """
        Retrieves up to the absolute value of `count` random members from the sorted set stored at 'key'.

        See https://valkey.io/commands/zrandmember for more details.

        Args:
            key (str): The key of the sorted set.
            count (int): The number of members to return.
                If `count` is positive, returns unique members.
                If `count` is negative, allows for duplicates members.

        Command response:
            List[str]: A list of members from the sorted set.
                If the sorted set does not exist or is empty, the response will be an empty list.
        """
        return self.append_command(RequestType.ZRandMember, [key, str(count)])

    def zrandmember_withscores(
        self: TTransaction, key: str, count: int
    ) -> TTransaction:
        """
        Retrieves up to the absolute value of `count` random members along with their scores from the sorted set
        stored at 'key'.

        See https://valkey.io/commands/zrandmember for more details.

        Args:
            key (str): The key of the sorted set.
            count (int): The number of members to return.
                If `count` is positive, returns unique members.
                If `count` is negative, allows for duplicates members.

        Command response:
            List[List[Union[str, float]]]: A list of `[member, score]` lists, where `member` is a random member from
                the sorted set and `score` is the associated score.
                If the sorted set does not exist or is empty, the response will be an empty list.
        """
        return self.append_command(
            RequestType.ZRandMember, [key, str(count), "WITHSCORES"]
        )

    def zmpop(
        self: TTransaction,
        keys: List[str],
        filter: ScoreFilter,
        count: Optional[int] = None,
    ) -> TTransaction:
        """
        Pops a member-score pair from the first non-empty sorted set, with the given keys being checked in the order
        that they are given. The optional `count` argument can be used to specify the number of elements to pop, and is
        set to 1 by default. The number of popped elements is the minimum from the sorted set's cardinality and `count`.

        See https://valkey.io/commands/zmpop for more details.

        Args:
            keys (List[str]): The keys of the sorted sets.
            modifier (ScoreFilter): The element pop criteria - either ScoreFilter.MIN or ScoreFilter.MAX to pop
                members with the lowest/highest scores accordingly.
            count (Optional[int]): The number of elements to pop.

        Command response:
            Optional[List[Union[str, Mapping[str, float]]]]: A two-element list containing the key name of the set from
                which elements were popped, and a member-score mapping of the popped elements. If no members could be
                popped, returns None.

        Since: Redis version 7.0.0.
        """
        args = [str(len(keys))] + keys + [filter.value]
        if count is not None:
            args = args + ["COUNT", str(count)]

        return self.append_command(RequestType.ZMPop, args)

    def bzmpop(
        self: TTransaction,
        keys: List[str],
        modifier: ScoreFilter,
        timeout: float,
        count: Optional[int] = None,
    ) -> TTransaction:
        """
        Pops a member-score pair from the first non-empty sorted set, with the given keys being checked in the order
        that they are given. Blocks the connection when there are no members to pop from any of the given sorted sets.

        The optional `count` argument can be used to specify the number of elements to pop, and is set to 1 by default.

        The number of popped elements is the minimum from the sorted set's cardinality and `count`.

        `BZMPOP` is the blocking variant of `ZMPOP`.

        See https://valkey.io/commands/bzmpop for more details.

        Note:
            `BZMPOP` is a client blocking command, see https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands for more details and best practices.

        Args:
            keys (List[str]): The keys of the sorted sets.
            modifier (ScoreFilter): The element pop criteria - either ScoreFilter.MIN or ScoreFilter.MAX to pop
                members with the lowest/highest scores accordingly.
            timeout (float): The number of seconds to wait for a blocking operation to complete. A value of 0 will
                block indefinitely.
            count (Optional[int]): The number of elements to pop.

        Command response:
            Optional[List[Union[str, Mapping[str, float]]]]: A two-element list containing the key name of the set from
                which elements were popped, and a member-score mapping. If no members could be popped and the timeout
                expired, returns None.

        Since: Redis version 7.0.0.
        """
        args = [str(timeout), str(len(keys))] + keys + [modifier.value]
        if count is not None:
            args = args + ["COUNT", str(count)]

        return self.append_command(RequestType.BZMPop, args)

    def zintercard(
        self: TTransaction, keys: List[str], limit: Optional[int] = None
    ) -> TTransaction:
        """
        Returns the cardinality of the intersection of the sorted sets specified by `keys`. When provided with the
        optional `limit` argument, if the intersection cardinality reaches `limit` partway through the computation, the
        algorithm will exit early and yield `limit` as the cardinality.

        See https://valkey.io/commands/zintercard for more details.

        Args:
            keys (List[str]): The keys of the sorted sets to intersect.
            limit (Optional[int]): An optional argument that can be used to specify a maximum number for the
                intersection cardinality. If limit is not supplied, or if it is set to 0, there will be no limit.

        Command response:
            int: The cardinality of the intersection of the given sorted sets, or the `limit` if reached.

        Since: Redis version 7.0.0.
        """
        args = [str(len(keys))] + keys
        if limit is not None:
            args.extend(["LIMIT", str(limit)])

        return self.append_command(RequestType.ZInterCard, args)

    def dbsize(self: TTransaction) -> TTransaction:
        """
        Returns the number of keys in the currently selected database.
        See https://redis.io/commands/dbsize for more details.

        Commands response:
            int: The number of keys in the database.
        """
        return self.append_command(RequestType.DBSize, [])

    def pfadd(self: TTransaction, key: str, elements: List[str]) -> TTransaction:
        """
        Adds all elements to the HyperLogLog data structure stored at the specified `key`.
        Creates a new structure if the `key` does not exist.
        When no elements are provided, and `key` exists and is a HyperLogLog, then no operation is performed.

        See https://redis.io/commands/pfadd/ for more details.

        Args:
            key (str): The key of the HyperLogLog data structure to add elements into.
            elements (List[str]): A list of members to add to the HyperLogLog stored at `key`.

        Commands response:
            int: If the HyperLogLog is newly created, or if the HyperLogLog approximated cardinality is
            altered, then returns 1. Otherwise, returns 0.
        """
        return self.append_command(RequestType.PfAdd, [key] + elements)

    def pfcount(self: TTransaction, keys: List[str]) -> TTransaction:
        """
        Estimates the cardinality of the data stored in a HyperLogLog structure for a single key or
        calculates the combined cardinality of multiple keys by merging their HyperLogLogs temporarily.

        See https://valkey.io/commands/pfcount for more details.

        Args:
            keys (List[str]): The keys of the HyperLogLog data structures to be analyzed.

        Command response:
            int: The approximated cardinality of given HyperLogLog data structures.
                The cardinality of a key that does not exist is 0.
        """
        return self.append_command(RequestType.PfCount, keys)

    def pfmerge(
        self: TTransaction, destination: str, source_keys: List[str]
    ) -> TTransaction:
        """
        Merges multiple HyperLogLog values into a unique value. If the destination variable exists, it is treated as one
        of the source HyperLogLog data sets, otherwise a new HyperLogLog is created.

        See https://valkey.io/commands/pfmerge for more details.

        Args:
            destination (str): The key of the destination HyperLogLog where the merged data sets will be stored.
            source_keys (List[str]): The keys of the HyperLogLog structures to be merged.

        Command response:
            OK: A simple OK response.
        """
        return self.append_command(RequestType.PfMerge, [destination] + source_keys)

    def bitcount(
        self: TTransaction, key: str, options: Optional[OffsetOptions] = None
    ) -> TTransaction:
        """
        Counts the number of set bits (population counting) in a string stored at `key`. The `options` argument can
        optionally be provided to count the number of bits in a specific string interval.

        See https://valkey.io/commands/bitcount for more details.

        Args:
            key (str): The key for the string to count the set bits of.
            options (Optional[OffsetOptions]): The offset options.

        Command response:
            int: If `options` is provided, returns the number of set bits in the string interval specified by `options`.
                If `options` is not provided, returns the number of set bits in the string stored at `key`.
                Otherwise, if `key` is missing, returns `0` as it is treated as an empty string.
        """
        args = [key]
        if options is not None:
            args = args + options.to_args()

        return self.append_command(RequestType.BitCount, args)

    def setbit(self: TTransaction, key: str, offset: int, value: int) -> TTransaction:
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

        Command response:
            int: The bit value that was previously stored at `offset`.
        """
        return self.append_command(RequestType.SetBit, [key, str(offset), str(value)])

    def getbit(self: TTransaction, key: str, offset: int) -> TTransaction:
        """
        Returns the bit value at `offset` in the string value stored at `key`.
        `offset` should be greater than or equal to zero.

        See https://valkey.io/commands/getbit for more details.

        Args:
            key (str): The key of the string.
            offset (int): The index of the bit to return.

        Command response:
            int: The bit at the given `offset` of the string. Returns `0` if the key is empty or if the `offset` exceeds
                the length of the string.
        """
        return self.append_command(RequestType.GetBit, [key, str(offset)])

    def bitpos(
        self: TTransaction, key: str, bit: int, start: Optional[int] = None
    ) -> TTransaction:
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

        Command response:
            int: The position of the first occurrence of `bit` in the binary value of the string held at `key`.
                If `start` was provided, the search begins at the offset indicated by `start`.
        """
        args = [key, str(bit)] if start is None else [key, str(bit), str(start)]
        return self.append_command(RequestType.BitPos, args)

    def bitpos_interval(
        self: TTransaction,
        key: str,
        bit: int,
        start: int,
        end: int,
        index_type: Optional[BitmapIndexType] = None,
    ) -> TTransaction:
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

        Command response:
            int: The position of the first occurrence from the `start` to the `end` offsets of the `bit` in the binary
                value of the string held at `key`.
        """
        if index_type is not None:
            args = [key, str(bit), str(start), str(end), index_type.value]
        else:
            args = [key, str(bit), str(start), str(end)]

        return self.append_command(RequestType.BitPos, args)

    def bitop(
        self: TTransaction,
        operation: BitwiseOperation,
        destination: str,
        keys: List[str],
    ) -> TTransaction:
        """
        Perform a bitwise operation between multiple keys (containing string values) and store the result in the
        `destination`.

        See https://valkey.io/commands/bitop for more details.

        Args:
            operation (BitwiseOperation): The bitwise operation to perform.
            destination (str): The key that will store the resulting string.
            keys (List[str]): The list of keys to perform the bitwise operation on.

        Command response:
            int: The size of the string stored in `destination`.
        """
        return self.append_command(
            RequestType.BitOp, [operation.value, destination] + keys
        )

    def bitfield(
        self: TTransaction, key: str, subcommands: List[BitFieldSubCommands]
    ) -> TTransaction:
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

        Command response:
            List[Optional[int]]: An array of results from the executed subcommands:
                - `BitFieldGet` returns the value in `Offset` or `OffsetMultiplier`.
                - `BitFieldSet` returns the old value in `Offset` or `OffsetMultiplier`.
                - `BitFieldIncrBy` returns the new value in `Offset` or `OffsetMultiplier`.
                - `BitFieldOverflow` determines the behavior of the "SET" and "INCRBY" subcommands when an overflow or
                  underflow occurs. "OVERFLOW" does not return a value and does not contribute a value to the list
                  response.
        """
        args = [key] + _create_bitfield_args(subcommands)
        return self.append_command(RequestType.BitField, args)

    def bitfield_read_only(
        self: TTransaction, key: str, subcommands: List[BitFieldGet]
    ) -> TTransaction:
        """
        Reads the array of bits representing the string that is held at `key` based on the specified `subcommands`.

        See https://valkey.io/commands/bitfield_ro for more details.

        Args:
            key (str): The key of the string.
            subcommands (List[BitFieldGet]): The "GET" subcommands to be performed.

        Command response:
            List[int]: An array of results from the "GET" subcommands.

        Since: Redis version 6.0.0.
        """
        args = [key] + _create_bitfield_read_only_args(subcommands)
        return self.append_command(RequestType.BitFieldReadOnly, args)

    def object_encoding(self: TTransaction, key: str) -> TTransaction:
        """
        Returns the internal encoding for the Redis object stored at `key`.

        See https://valkey.io/commands/object-encoding for more details.

        Args:
            key (str): The `key` of the object to get the internal encoding of.

        Command response:
            Optional[str]: If `key` exists, returns the internal encoding of the object stored at
                `key` as a string. Otherwise, returns None.
        """
        return self.append_command(RequestType.ObjectEncoding, [key])

    def object_freq(self: TTransaction, key: str) -> TTransaction:
        """
        Returns the logarithmic access frequency counter of a Redis object stored at `key`.

        See https://valkey.io/commands/object-freq for more details.

        Args:
            key (str): The key of the object to get the logarithmic access frequency counter of.

        Command response:
            Optional[int]: If `key` exists, returns the logarithmic access frequency counter of the object stored at `key` as an
                integer. Otherwise, returns None.
        """
        return self.append_command(RequestType.ObjectFreq, [key])

    def object_idletime(self: TTransaction, key: str) -> TTransaction:
        """
        Returns the time in seconds since the last access to the value stored at `key`.

        See https://valkey.io/commands/object-idletime for more details.

        Args:
            key (str): The key of the object to get the idle time of.

        Command response:
            Optional[int]: If `key` exists, returns the idle time in seconds. Otherwise, returns None.
        """
        return self.append_command(RequestType.ObjectIdleTime, [key])

    def object_refcount(self: TTransaction, key: str) -> TTransaction:
        """
        Returns the reference count of the object stored at `key`.

        See https://valkey.io/commands/object-refcount for more details.

        Args:
            key (str): The key of the object to get the reference count of.

        Command response:
            Optional[int]: If `key` exists, returns the reference count of the object stored at `key` as an integer.
                Otherwise, returns None.
        """
        return self.append_command(RequestType.ObjectRefCount, [key])

    def srandmember(self: TTransaction, key: str) -> TTransaction:
        """
        Returns a random element from the set value stored at 'key'.

        See https://valkey.io/commands/srandmember for more details.

        Args:
            key (str): The key from which to retrieve the set member.

        Command Response:
            str: A random element from the set, or None if 'key' does not exist.
        """
        return self.append_command(RequestType.SRandMember, [key])

    def srandmember_count(self: TTransaction, key: str, count: int) -> TTransaction:
        """
        Returns one or more random elements from the set value stored at 'key'.

        See https://valkey.io/commands/srandmember for more details.

        Args:
            key (str): The key of the sorted set.
            count (int): The number of members to return.
                If `count` is positive, returns unique members.
                If `count` is negative, allows for duplicates members.

        Command Response:
            List[str]: A list of members from the set.
                If the set does not exist or is empty, the response will be an empty list.
        """
        return self.append_command(RequestType.SRandMember, [key, str(count)])

    def flushall(
        self: TTransaction, flush_mode: Optional[FlushMode] = None
    ) -> TTransaction:
        """
        Deletes all the keys of all the existing databases. This command never fails.
        See https://valkey.io/commands/flushall for more details.

        Args:
            flush_mode (Optional[FlushMode]): The flushing mode, could be either `SYNC` or `ASYNC`.

        Command Response:
            TOK: OK.
        """
        args = []
        if flush_mode is not None:
            args.append(flush_mode.value)
        return self.append_command(RequestType.FlushAll, args)

    def flushdb(
        self: TTransaction, flush_mode: Optional[FlushMode] = None
    ) -> TTransaction:
        """
        Deletes all the keys of the currently selected database. This command never fails.

        See https://valkey.io/commands/flushdb for more details.

        Args:
            flush_mode (Optional[FlushMode]): The flushing mode, could be either `SYNC` or `ASYNC`.

        Command Response:
            TOK: OK.
        """
        args = []
        if flush_mode is not None:
            args.append(flush_mode.value)
        return self.append_command(RequestType.FlushDB, args)

    def getex(
        self: TTransaction, key: str, expiry: Optional[ExpiryGetEx] = None
    ) -> TTransaction:
        """
        Get the value of `key` and optionally set its expiration. GETEX is similar to GET.
        See https://valkey.io/commands/getex for more details.

        Args:
            key (str): The key to get.
            expiry (Optional[ExpirySet], optional): set expiriation to the given key.
                Equivalent to [`EX` | `PX` | `EXAT` | `PXAT` | `PERSIST`] in the Redis API.

        Command Response:
            Optional[str]:
                If `key` exists, return the value stored at `key`
                If 'key` does not exist, return 'None'

        Since: Redis version 6.2.0.
        """
        args = [key]
        if expiry is not None:
            args.extend(expiry.get_cmd_args())
        return self.append_command(RequestType.GetEx, args)

    def lolwut(
        self: TTransaction,
        version: Optional[int] = None,
        parameters: Optional[List[int]] = None,
    ) -> TTransaction:
        """
        Displays a piece of generative computer art and the Redis version.

        See https://valkey.io/commands/lolwut for more details.

        Args:
            version (Optional[int]): Version of computer art to generate.
            parameters (Optional[List[int]]): Additional set of arguments in order to change the output:
                For version `5`, those are length of the line, number of squares per row, and number of squares per column.
                For version `6`, those are number of columns and number of lines.

        Command Response:
            str: A piece of generative computer art along with the current Redis version.
        """
        args = []
        if version is not None:
            args.extend(["VERSION", str(version)])
        if parameters:
            for var in parameters:
                args.extend(str(var))
        return self.append_command(RequestType.Lolwut, args)

    def random_key(self: TTransaction) -> TTransaction:
        """
        Returns a random existing key name.

        See https://valkey.io/commands/randomkey for more details.

        Command response:
            Optional[str]: A random existing key name.
        """
        return self.append_command(RequestType.RandomKey, [])

    def sscan(
        self: TTransaction,
        key: str,
        cursor: str,
        match: Optional[str] = None,
        count: Optional[int] = None,
    ) -> TTransaction:
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

        Command Response:
            List[Union[str, List[str]]]: An `Array` of the `cursor` and the subset of the set held by `key`.
                The first element is always the `cursor` for the next iteration of results. `0` will be the `cursor`
                returned on the last iteration of the set. The second element is always an `Array` of the subset of the
                set held in `key`.
        """
        args = [key, cursor]
        if match is not None:
            args += ["MATCH", match]
        if count is not None:
            args += ["COUNT", str(count)]

        return self.append_command(RequestType.SScan, args)

    def zscan(
        self: TTransaction,
        key: str,
        cursor: str,
        match: Optional[str] = None,
        count: Optional[int] = None,
    ) -> TTransaction:
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
        """
        args = [key, cursor]
        if match is not None:
            args += ["MATCH", match]
        if count is not None:
            args += ["COUNT", str(count)]

        return self.append_command(RequestType.ZScan, args)

    def hscan(
        self: TTransaction,
        key: str,
        cursor: str,
        match: Optional[str] = None,
        count: Optional[int] = None,
    ) -> TTransaction:
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
        """
        args = [key, cursor]
        if match is not None:
            args += ["MATCH", match]
        if count is not None:
            args += ["COUNT", str(count)]

        return self.append_command(RequestType.HScan, args)

    def lcs(
        self: TTransaction,
        key1: str,
        key2: str,
    ) -> TTransaction:
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

        Command Response:
            A String containing the longest common subsequence between the 2 strings.
            An empty String is returned if the keys do not exist or have no common subsequences.

        Since: Redis version 7.0.0.
        """
        args = [key1, key2]

        return self.append_command(RequestType.LCS, args)

    def lcs_len(
        self: TTransaction,
        key1: str,
        key2: str,
    ) -> TTransaction:
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

        Command Response:
            The length of the longest common subsequence between the 2 strings.

        Since: Redis version 7.0.0.
        """
        args = [key1, key2, "LEN"]

        return self.append_command(RequestType.LCS, args)

    def lcs_idx(
        self: TTransaction,
        key1: str,
        key2: str,
        min_match_len: Optional[int] = None,
        with_match_len: Optional[bool] = False,
    ) -> TTransaction:
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

        Command Response:
            A Map containing the indices of the longest common subsequence between the
            2 strings and the length of the longest common subsequence. The resulting map contains two
            keys, "matches" and "len":
                - "len" is mapped to the length of the longest common subsequence between the 2 strings.
                - "matches" is mapped to a three dimensional int array that stores pairs of indices that
                  represent the location of the common subsequences in the strings held by key1 and key2,
                  with the length of the match after each matches, if with_match_len is enabled.

        Since: Redis version 7.0.0.
        """
        args = [key1, key2, "IDX"]

        if min_match_len is not None:
            args.extend(["MINMATCHLEN", str(min_match_len)])

        if with_match_len:
            args.append("WITHMATCHLEN")

        return self.append_command(RequestType.LCS, args)

    def wait(
        self: TTransaction,
        numreplicas: int,
        timeout: int,
    ) -> TTransaction:
        """
        Returns the number of replicas that acknowledged the write commands sent by the current client
        before this command, both in the case where the specified number of replicas are reached, or
        when the timeout is reached.

        See https://valkey.io/commands/wait for more details.

        Args:
            numreplicas (int): The number of replicas to reach.
            timeout (int): The timeout value specified in milliseconds.

        Command Response:
            str: The number of replicas reached by all the writes performed in the context of the current connection.
        """
        args = [str(numreplicas), str(timeout)]
        return self.append_command(RequestType.Wait, args)

    def lpos(
        self: TTransaction,
        key: str,
        element: str,
        rank: Optional[int] = None,
        count: Optional[int] = None,
        max_len: Optional[int] = None,
    ) -> TTransaction:
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

        Command Response:
            Union[int, list[int], None]: The index of the first occurrence of `element`,
            or None if `element` is not in the list.
            With the `count` option, a list of indices of matching elements will be returned.

        Since: Redis version 6.0.6.
        """
        args = [key, element]

        if rank is not None:
            args.extend(["RANK", str(rank)])

        if count is not None:
            args.extend(["COUNT", str(count)])

        if max_len is not None:
            args.extend(["MAXLEN", str(max_len)])

        return self.append_command(RequestType.LPos, args)


class Transaction(BaseTransaction):
    """
    Extends BaseTransaction class for standalone Redis commands that are not supported in Redis cluster mode.

    Command Response:
        The response for each command depends on the executed Redis command. Specific response types
        are documented alongside each method.

    Example:
        transaction = Transaction()
        >>> transaction.set("key", "value")
        >>> transaction.select(1)  # Standalone command
        >>> transaction.get("key")
        >>> await client.exec(transaction)
        [OK , OK , None]

    """

    # TODO: add SLAVEOF and all SENTINEL commands
    def move(self, key: str, db_index: int) -> "Transaction":
        """
        Move `key` from the currently selected database to the database specified by `db_index`.

        See https://valkey.io/commands/move/ for more details.

        Args:
            key (str): The key to move.
            db_index (int): The index of the database to move `key` to.

        Commands response:
            bool: True if `key` was moved, or False if the `key` already exists in the destination database
                or does not exist in the source database.
        """
        return self.append_command(RequestType.Move, [key, str(db_index)])

    def select(self, index: int) -> "Transaction":
        """
        Change the currently selected Redis database.
        See https://redis.io/commands/select/ for details.

        Args:
            index (int): The index of the database to select.

        Command response:
            A simple OK response.
        """
        return self.append_command(RequestType.Select, [str(index)])

    def sort(
        self: TTransaction,
        key: str,
        by_pattern: Optional[str] = None,
        limit: Optional[Limit] = None,
        get_patterns: Optional[List[str]] = None,
        order: Optional[OrderBy] = None,
        alpha: Optional[bool] = None,
    ) -> TTransaction:
        """
        Sorts the elements in the list, set, or sorted set at `key` and returns the result.
        The `sort` command can be used to sort elements based on different criteria and apply transformations on sorted elements.
        To store the result into a new key, see `sort_store`.

        See https://valkey.io/commands/sort for more details.

        Args:
            key (str): The key of the list, set, or sorted set to be sorted.
            by_pattern (Optional[str]): A pattern to sort by external keys instead of by the elements stored at the key themselves.
                The pattern should contain an asterisk (*) as a placeholder for the element values, where the value
                from the key replaces the asterisk to create the key name. For example, if `key` contains IDs of objects,
                `by_pattern` can be used to sort these IDs based on an attribute of the objects, like their weights or
                timestamps.
                E.g., if `by_pattern` is `weight_*`, the command will sort the elements by the values of the
                keys `weight_<element>`.
                If not provided, elements are sorted by their value.
            limit (Optional[Limit]): Limiting the range of the query by setting offset and result count. See `Limit` class for more information.
            get_pattern (Optional[str]): A pattern used to retrieve external keys' values, instead of the elements at `key`.
                The pattern should contain an asterisk (*) as a placeholder for the element values, where the value
                from `key` replaces the asterisk to create the key name. This allows the sorted elements to be
                transformed based on the related keys values. For example, if `key` contains IDs of users, `get_pattern`
                can be used to retrieve specific attributes of these users, such as their names or email addresses.
                E.g., if `get_pattern` is `name_*`, the command will return the values of the keys `name_<element>`
                for each sorted element. Multiple `get_pattern` arguments can be provided to retrieve multiple attributes.
                The special value `#` can be used to include the actual element from `key` being sorted.
                If not provided, only the sorted elements themselves are returned.
            order (Optional[OrderBy]): Specifies the order to sort the elements.
                Can be `OrderBy.ASC` (ascending) or `OrderBy.DESC` (descending).
            alpha (Optional[bool]): When `True`, sorts elements lexicographically. When `False` (default), sorts elements numerically.
                Use this when the list, set, or sorted set contains string values that cannot be converted into double precision floating point numbers.

        Command response:
            List[Optional[str]]: Returns a list of sorted elements.
        """
        args = _build_sort_args(key, by_pattern, limit, get_patterns, order, alpha)
        return self.append_command(RequestType.Sort, args)

    def sort_store(
        self: TTransaction,
        key: str,
        destination: str,
        by_pattern: Optional[str] = None,
        limit: Optional[Limit] = None,
        get_patterns: Optional[List[str]] = None,
        order: Optional[OrderBy] = None,
        alpha: Optional[bool] = None,
    ) -> TTransaction:
        """
        Sorts the elements in the list, set, or sorted set at `key` and stores the result in `store`.
        The `sort` command can be used to sort elements based on different criteria, apply transformations on sorted elements, and store the result in a new key.
        To get the sort result without storing it into a key, see `sort`.

        See https://valkey.io/commands/sort for more details.

        Args:
            key (str): The key of the list, set, or sorted set to be sorted.
            destination (str): The key where the sorted result will be stored.
            by_pattern (Optional[str]): A pattern to sort by external keys instead of by the elements stored at the key themselves.
                The pattern should contain an asterisk (*) as a placeholder for the element values, where the value
                from the key replaces the asterisk to create the key name. For example, if `key` contains IDs of objects,
                `by_pattern` can be used to sort these IDs based on an attribute of the objects, like their weights or
                timestamps.
                E.g., if `by_pattern` is `weight_*`, the command will sort the elements by the values of the
                keys `weight_<element>`.
                If not provided, elements are sorted by their value.
            limit (Optional[Limit]): Limiting the range of the query by setting offset and result count. See `Limit` class for more information.
            get_pattern (Optional[str]): A pattern used to retrieve external keys' values, instead of the elements at `key`.
                The pattern should contain an asterisk (*) as a placeholder for the element values, where the value
                from `key` replaces the asterisk to create the key name. This allows the sorted elements to be
                transformed based on the related keys values. For example, if `key` contains IDs of users, `get_pattern`
                can be used to retrieve specific attributes of these users, such as their names or email addresses.
                E.g., if `get_pattern` is `name_*`, the command will return the values of the keys `name_<element>`
                for each sorted element. Multiple `get_pattern` arguments can be provided to retrieve multiple attributes.
                The special value `#` can be used to include the actual element from `key` being sorted.
                If not provided, only the sorted elements themselves are returned.
            order (Optional[OrderBy]): Specifies the order to sort the elements.
                Can be `OrderBy.ASC` (ascending) or `OrderBy.DESC` (descending).
            alpha (Optional[bool]): When `True`, sorts elements lexicographically. When `False` (default), sorts elements numerically.
                Use this when the list, set, or sorted set contains string values that cannot be converted into double precision floating point numbers.

        Command response:
            int: The number of elements in the sorted key stored at `store`.
        """
        args = _build_sort_args(
            key, by_pattern, limit, get_patterns, order, alpha, store=destination
        )
        return self.append_command(RequestType.Sort, args)

    def copy(
        self: TTransaction,
        source: str,
        destination: str,
        destinationDB: Optional[int] = None,
        replace: Optional[bool] = None,
    ) -> TTransaction:
        """
        Copies the value stored at the `source` to the `destination` key. If `destinationDB`
        is specified, the value will be copied to the database specified by `destinationDB`,
        otherwise the current database will be used. When `replace` is True, removes the
        `destination` key first if it already exists, otherwise performs no action.

        See https://valkey.io/commands/copy for more details.

        Args:
            source (str): The key to the source value.
            destination (str): The key where the value should be copied to.
            destinationDB (Optional[int]): The alternative logical database index for the destination key.
            replace (Optional[bool]): If the destination key should be removed before copying the value to it.

        Command response:
            bool: True if the source was copied. Otherwise, return False.

        Since: Redis version 6.2.0.
        """
        args = [source, destination]
        if destinationDB is not None:
            args.extend(["DB", str(destinationDB)])
        if replace is not None:
            args.append("REPLACE")

        return self.append_command(RequestType.Copy, args)

    def publish(self: TTransaction, message: str, channel: str) -> TTransaction:
        """
        Publish a message on pubsub channel.
        See https://valkey.io/commands/publish for more details.

        Args:
            message (str): Message to publish
            channel (str): Channel to publish the message on.

        Returns:
            TOK: a simple `OK` response.

        """
        return self.append_command(RequestType.Publish, [channel, message])


class ClusterTransaction(BaseTransaction):
    """
    Extends BaseTransaction class for cluster mode commands that are not supported in standalone.

    Command Response:
        The response for each command depends on the executed Redis command. Specific response types
        are documented alongside each method.
    """

    def sort(
        self: TTransaction,
        key: str,
        limit: Optional[Limit] = None,
        order: Optional[OrderBy] = None,
        alpha: Optional[bool] = None,
    ) -> TTransaction:
        """
        Sorts the elements in the list, set, or sorted set at `key` and returns the result.
        To store the result into a new key, see `sort_store`.

        See https://valkey.io/commands/sort for more details.

        Args:
            key (str): The key of the list, set, or sorted set to be sorted.
            limit (Optional[Limit]): Limiting the range of the query by setting offset and result count. See `Limit` class for more information.
            order (Optional[OrderBy]): Specifies the order to sort the elements.
                Can be `OrderBy.ASC` (ascending) or `OrderBy.DESC` (descending).
            alpha (Optional[bool]): When `True`, sorts elements lexicographically. When `False` (default), sorts elements numerically.
                Use this when the list, set, or sorted set contains string values that cannot be converted into double precision floating point numbers.

        Command response:
            List[str]: A list of sorted elements.
        """
        args = _build_sort_args(key, None, limit, None, order, alpha)
        return self.append_command(RequestType.Sort, args)

    def sort_store(
        self: TTransaction,
        key: str,
        destination: str,
        limit: Optional[Limit] = None,
        order: Optional[OrderBy] = None,
        alpha: Optional[bool] = None,
    ) -> TTransaction:
        """
        Sorts the elements in the list, set, or sorted set at `key` and stores the result in `store`.
        When in cluster mode, `key` and `store` must map to the same hash slot.
        To get the sort result without storing it into a key, see `sort`.

        See https://valkey.io/commands/sort for more details.

        Args:
            key (str): The key of the list, set, or sorted set to be sorted.
            destination (str): The key where the sorted result will be stored.
            limit (Optional[Limit]): Limiting the range of the query by setting offset and result count. See `Limit` class for more information.
            order (Optional[OrderBy]): Specifies the order to sort the elements.
                Can be `OrderBy.ASC` (ascending) or `OrderBy.DESC` (descending).
            alpha (Optional[bool]): When `True`, sorts elements lexicographically. When `False` (default), sorts elements numerically.
                Use this when the list, set, or sorted set contains string values that cannot be converted into double precision floating point numbers.

        Command response:
            int: The number of elements in the sorted key stored at `store`.
        """
        args = _build_sort_args(key, None, limit, None, order, alpha, store=destination)
        return self.append_command(RequestType.Sort, args)

    def copy(
        self: TTransaction,
        source: str,
        destination: str,
        replace: Optional[bool] = None,
    ) -> TTransaction:
        """
        Copies the value stored at the `source` to the `destination` key. When `replace` is True,
        removes the `destination` key first if it already exists, otherwise performs no action.

        See https://valkey.io/commands/copy for more details.

        Args:
            source (str): The key to the source value.
            destination (str): The key where the value should be copied to.
            replace (Optional[bool]): If the destination key should be removed before copying the value to it.

        Command response:
            bool: True if the source was copied. Otherwise, return False.

        Since: Redis version 6.2.0.
        """
        args = [source, destination]
        if replace is not None:
            args.append("REPLACE")

        return self.append_command(RequestType.Copy, args)

    def publish(
        self: TTransaction, message: str, channel: str, sharded: bool = False
    ) -> TTransaction:
        """
        Publish a message on pubsub channel.
        This command aggregates PUBLISH and SPUBLISH commands functionalities.
        The mode is selected using the 'sharded' parameter
        See https://valkey.io/commands/publish and https://valkey.io/commands/spublish for more details.

        Args:
            message (str): Message to publish
            channel (str): Channel to publish the message on.
            sharded (bool): Use sharded pubsub mode. Available since Redis version 7.0.

        Returns:
            int: Number of subscriptions in that shard that received the message.
        """
        return self.append_command(
            RequestType.SPublish if sharded else RequestType.Publish, [channel, message]
        )

    # TODO: add all CLUSTER commands
