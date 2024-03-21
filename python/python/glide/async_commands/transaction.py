# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

import threading
from typing import List, Mapping, Optional, Tuple, TypeVar, Union

from glide.async_commands.core import (
    ConditionalChange,
    ExpireOptions,
    ExpirySet,
    InfoSection,
    UpdateOptions,
)
from glide.async_commands.sorted_set import (
    InfBound,
    RangeByIndex,
    RangeByLex,
    RangeByScore,
    ScoreBoundary,
    _create_zrange_args,
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
        return self.append_command(RequestType.GetString, [key])

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
        return self.append_command(RequestType.SetString, args)

    def custom_command(self: TTransaction, command_args: List[str]) -> TTransaction:
        """
        Executes a single command, without checking inputs.
            @remarks - This function should only be used for single-response commands. Commands that don't return response (such as SUBSCRIBE), or that return potentially more than a single response (such as XREAD), or that change the client's behavior (such as entering pub/sub mode on RESP2 connections) shouldn't be called using this function.
            @example - Append a command to list of all pub/sub clients:

                transaction.customCommand(["CLIENT", "LIST","TYPE", "PUBSUB"])

        Args:
            command_args (List[str]): List of strings of the command's arguments.
            Every part of the command, including the command name and subcommands, should be added as a separate value in args.

        Command response:
            TResult: The returning value depends on the executed command.
        """
        return self.append_command(RequestType.CustomCommand, command_args)

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
        return self.append_command(RequestType.HashSet, field_value_list)

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
        return self.append_command(RequestType.HashGet, [key, field])

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
        return self.append_command(RequestType.HashIncrBy, [key, field, str(amount)])

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
        return self.append_command(
            RequestType.HashIncrByFloat, [key, field, str(amount)]
        )

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
        return self.append_command(RequestType.HashExists, [key, field])

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
        return self.append_command(RequestType.HashGetAll, [key])

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
        return self.append_command(RequestType.HashMGet, [key] + fields)

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
        return self.append_command(RequestType.HashDel, [key] + fields)

    def hvals(self: TTransaction, key: str) -> TTransaction:
        """
        Returns all values in the hash stored at `key`.

        See https://redis.io/commands/hvals/ for more details.

        Args:
            key (str): The key of the hash.

        Command response:
            List[str]: A list of values in the hash, or an empty list when the key does not exist.
        """
        return self.append_command(RequestType.Hvals, [key])

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
        return self.append_command(RequestType.Lindex, [key, str(index)])

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

        return self.append_command(RequestType.Zadd, args)

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
        return self.append_command(RequestType.Zadd, args)

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
        return self.append_command(RequestType.Zcard, [key])

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
        return self.append_command(RequestType.Zcount, [key, score_min, score_max])

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

        return self.append_command(RequestType.Zrange, args)

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

        return self.append_command(RequestType.Zrange, args)

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
        return self.append_command(RequestType.Zrank, [key, member])

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
        return self.append_command(RequestType.Zrank, [key, member, "WITHSCORE"])

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
        return self.append_command(RequestType.Zrem, [key] + members)

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

    def dbsize(self: TTransaction) -> TTransaction:
        """
        Returns the number of keys in the currently selected database.
        See https://redis.io/commands/dbsize for more details.

        Commands response:
            int: The number of keys in the database.
        """
        return self.append_command(RequestType.DBSize, [])


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

    # TODO: add MOVE, SLAVEOF and all SENTINEL commands
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


class ClusterTransaction(BaseTransaction):
    """
    Extends BaseTransaction class for cluster mode commands that are not supported in standalone.

    Command Response:
        The response for each command depends on the executed Redis command. Specific response types
        are documented alongside each method.
    """

    # TODO: add all CLUSTER commands
    pass
