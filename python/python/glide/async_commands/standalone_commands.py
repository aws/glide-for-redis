# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

from typing import Dict, List, Mapping, Optional, cast

from glide.async_commands.command_args import Limit, OrderBy
from glide.async_commands.core import CoreCommands, InfoSection, _build_sort_args
from glide.async_commands.transaction import BaseTransaction, Transaction
from glide.constants import TOK, TResult
from glide.protobuf.redis_request_pb2 import RequestType


class StandaloneCommands(CoreCommands):
    async def custom_command(self, command_args: List[str]) -> TResult:
        """
        Executes a single command, without checking inputs.
        See the [Glide for Redis Wiki](https://github.com/aws/glide-for-redis/wiki/General-Concepts#custom-command)
        for details on the restrictions and limitations of the custom command API.

            @example - Return a list of all pub/sub clients:

                connection.customCommand(["CLIENT", "LIST","TYPE", "PUBSUB"])
        Args:
            command_args (List[str]): List of strings of the command's arguments.
            Every part of the command, including the command name and subcommands, should be added as a separate value in args.

        Returns:
            TResult: The returning value depends on the executed command and the route
        """
        return await self._execute_command(RequestType.CustomCommand, command_args)

    async def info(
        self,
        sections: Optional[List[InfoSection]] = None,
    ) -> str:
        """
        Get information and statistics about the Redis server.
        See https://redis.io/commands/info/ for details.

        Args:
            sections (Optional[List[InfoSection]]): A list of InfoSection values specifying which sections of
            information to retrieve. When no parameter is provided, the default option is assumed.


        Returns:
            str: Returns a string containing the information for the sections requested.
        """
        args = [section.value for section in sections] if sections else []
        return cast(str, await self._execute_command(RequestType.Info, args))

    async def exec(
        self,
        transaction: BaseTransaction | Transaction,
    ) -> Optional[List[TResult]]:
        """
        Execute a transaction by processing the queued commands.
        See https://redis.io/topics/Transactions/ for details on Redis Transactions.

        Args:
            transaction (Transaction): A Transaction object containing a list of commands to be executed.

        Returns:
            Optional[List[TResult]]: A list of results corresponding to the execution of each command
            in the transaction. If a command returns a value, it will be included in the list. If a command
            doesn't return a value, the list entry will be None.
            If the transaction failed due to a WATCH command, `exec` will return `None`.
        """
        commands = transaction.commands[:]
        return await self._execute_transaction(commands)

    async def select(self, index: int) -> TOK:
        """
        Change the currently selected Redis database.
        See https://redis.io/commands/select/ for details.

        Args:
            index (int): The index of the database to select.

        Returns:
            A simple OK response.
        """
        return cast(TOK, await self._execute_command(RequestType.Select, [str(index)]))

    async def config_resetstat(self) -> TOK:
        """
        Resets the statistics reported by Redis using the INFO and LATENCY HISTOGRAM commands.
        See https://redis.io/commands/config-resetstat/ for details.

        Returns:
            OK: Returns "OK" to confirm that the statistics were successfully reset.
        """
        return cast(TOK, await self._execute_command(RequestType.ConfigResetStat, []))

    async def config_rewrite(self) -> TOK:
        """
        Rewrite the configuration file with the current configuration.
        See https://redis.io/commands/config-rewrite/ for details.

        Returns:
            OK: OK is returned when the configuration was rewritten properly. Otherwise, an error is raised.
        """
        return cast(TOK, await self._execute_command(RequestType.ConfigRewrite, []))

    async def client_id(
        self,
    ) -> int:
        """
        Returns the current connection id.
        See https://redis.io/commands/client-id/ for more information.

        Returns:
            int: the id of the client.
        """
        return cast(int, await self._execute_command(RequestType.ClientId, []))

    async def ping(self, message: Optional[str] = None) -> str:
        """
        Ping the Redis server.
        See https://redis.io/commands/ping/ for more details.

        Args:
           message (Optional[str]): An optional message to include in the PING command. If not provided,
            the server will respond with "PONG". If provided, the server will respond with a copy of the message.

        Returns:
           str: "PONG" if `message` is not provided, otherwise return a copy of `message`.

        Examples:
            >>> await client.ping()
            "PONG"
            >>> await client.ping("Hello")
            "Hello"
        """
        argument = [] if message is None else [message]
        return cast(str, await self._execute_command(RequestType.Ping, argument))

    async def config_get(self, parameters: List[str]) -> Dict[str, str]:
        """
        Get the values of configuration parameters.
        See https://redis.io/commands/config-get/ for details.

        Args:
            parameters (List[str]): A list of configuration parameter names to retrieve values for.

        Returns:
            Dict[str, str]: A dictionary of values corresponding to the configuration parameters.

        Examples:
            >>> await client.config_get(["timeout"] , RandomNode())
            {'timeout': '1000'}
            >>> await client.config_get(["timeout" , "maxmemory"])
            {'timeout': '1000', "maxmemory": "1GB"}

        """
        return cast(
            Dict[str, str],
            await self._execute_command(RequestType.ConfigGet, parameters),
        )

    async def config_set(self, parameters_map: Mapping[str, str]) -> TOK:
        """
        Set configuration parameters to the specified values.
        See https://redis.io/commands/config-set/ for details.

        Args:
            parameters_map (Mapping[str, str]): A map consisting of configuration
            parameters and their respective values to set.

        Returns:
            OK: Returns OK if all configurations have been successfully set. Otherwise, raises an error.

        Examples:
            >>> config_set({"timeout": "1000", "maxmemory": "1GB"})
            OK
        """
        parameters: List[str] = []
        for pair in parameters_map.items():
            parameters.extend(pair)
        return cast(TOK, await self._execute_command(RequestType.ConfigSet, parameters))

    async def client_getname(self) -> Optional[str]:
        """
        Get the name of the primary's connection.
        See https://redis.io/commands/client-getname/ for more details.

        Returns:
            Optional[str]: Returns the name of the client connection as a string if a name is set,
            or None if no name is assigned.

        Examples:
            >>> await client.client_getname()
            'Connection Name'
        """
        return cast(
            Optional[str], await self._execute_command(RequestType.ClientGetName, [])
        )

    async def dbsize(self) -> int:
        """
        Returns the number of keys in the currently selected database.
        See https://redis.io/commands/dbsize for more details.

        Returns:
            int: The number of keys in the currently selected database.

        Examples:
            >>> await client.dbsize()
                10  # Indicates there are 10 keys in the current database.
        """
        return cast(int, await self._execute_command(RequestType.DBSize, []))

    async def echo(self, message: str) -> str:
        """
        Echoes the provided `message` back.

        See https://redis.io/commands/echo for more details.

        Args:
            message (str): The message to be echoed back.

        Returns:
            str: The provided `message`.

        Examples:
            >>> await client.echo("Glide-for-Redis")
                'Glide-for-Redis'
        """
        return cast(str, await self._execute_command(RequestType.Echo, [message]))

    async def time(self) -> List[str]:
        """
        Returns the server time.

        See https://redis.io/commands/time/ for more details.

        Returns:
            List[str]:  The current server time as a two items `array`:
            A Unix timestamp and the amount of microseconds already elapsed in the current second.
            The returned `array` is in a [Unix timestamp, Microseconds already elapsed] format.

        Examples:
            >>> await client.time()
            ['1710925775', '913580']
        """
        return cast(
            List[str],
            await self._execute_command(RequestType.Time, []),
        )

    async def lastsave(self) -> int:
        """
        Returns the Unix time of the last DB save timestamp or startup timestamp if no save was made since then.

        See https://valkey.io/commands/lastsave for more details.

        Returns:
            int: The Unix time of the last successful DB save.

        Examples:
            >>> await client.lastsave()
            1710925775  # Unix time of the last DB save
        """
        return cast(
            int,
            await self._execute_command(RequestType.LastSave, []),
        )

    async def sort(
        self,
        key: str,
        by_pattern: Optional[str] = None,
        limit: Optional[Limit] = None,
        get_patterns: Optional[List[str]] = None,
        order: Optional[OrderBy] = None,
        alpha: Optional[bool] = None,
    ) -> List[Optional[str]]:
        """
        Sorts the elements in the list, set, or sorted set at `key` and returns the result.
        The `sort` command can be used to sort elements based on different criteria and apply transformations on sorted elements.
        This command is routed to primary nodes only.
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
                Use this when the list, set, or sorted set contains string values that cannot be converted into double precision floating point

        Returns:
            List[Optional[str]]: Returns a list of sorted elements.

        Examples:
            >>> await client.lpush("mylist", 3, 1, 2)
            >>> await client.sort("mylist")
            ['1', '2', '3']
            >>> await client.sort("mylist", order=OrderBy.DESC)
            ['3', '2', '1']
            >>> await client.lpush("mylist2", 2, 1, 2, 3, 3, 1)
            >>> await client.sort("mylist2", limit=Limit(2, 3))
            ['2', '2', '3']
            >>> await client.hset("user:1", "name", "Alice", "age", 30)
            >>> await client.hset("user:2", "name", "Bob", "age", 25)
            >>> await client.lpush("user_ids", 2, 1)
            >>> await client.sort("user_ids", by_pattern="user:*->age", get_patterns=["user:*->name"])
            ['Bob', 'Alice']
        """
        args = _build_sort_args(key, by_pattern, limit, get_patterns, order, alpha)
        result = await self._execute_command(RequestType.Sort, args)
        return cast(List[Optional[str]], result)

    async def sort_ro(
        self,
        key: str,
        by_pattern: Optional[str] = None,
        limit: Optional[Limit] = None,
        get_patterns: Optional[List[str]] = None,
        order: Optional[OrderBy] = None,
        alpha: Optional[bool] = None,
    ) -> List[Optional[str]]:
        """
        Sorts the elements in the list, set, or sorted set at `key` and returns the result.
        The `sort_ro` command can be used to sort elements based on different criteria and apply transformations on sorted elements.

        See https://valkey.io/commands/sort_ro for more details.

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
                Use this when the list, set, or sorted set contains string values that cannot be converted into double precision floating point

        Returns:
            List[Optional[str]]: Returns a list of sorted elements.

        Examples:
            >>> await client.lpush("mylist", 3, 1, 2)
            >>> await client.sort_ro("mylist")
            ['1', '2', '3']
            >>> await client.sort_ro("mylist", order=OrderBy.DESC)
            ['3', '2', '1']
            >>> await client.lpush("mylist2", 2, 1, 2, 3, 3, 1)
            >>> await client.sort_ro("mylist2", limit=Limit(2, 3))
            ['2', '2', '3']
            >>> await client.hset("user:1", "name", "Alice", "age", 30)
            >>> await client.hset("user:2", "name", "Bob", "age", 25)
            >>> await client.lpush("user_ids", 2, 1)
            >>> await client.sort_ro("user_ids", by_pattern="user:*->age", get_patterns=["user:*->name"])
            ['Bob', 'Alice']
        """
        args = _build_sort_args(key, by_pattern, limit, get_patterns, order, alpha)
        result = await self._execute_command(RequestType.SortReadOnly, args)
        return cast(List[Optional[str]], result)

    async def sort_store(
        self,
        key: str,
        destination: str,
        by_pattern: Optional[str] = None,
        limit: Optional[Limit] = None,
        get_patterns: Optional[List[str]] = None,
        order: Optional[OrderBy] = None,
        alpha: Optional[bool] = None,
    ) -> int:
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
                Use this when the list, set, or sorted set contains string values that cannot be converted into double precision floating point

        Returns:
            int: The number of elements in the sorted key stored at `store`.

        Examples:
            >>> await client.lpush("mylist", 3, 1, 2)
            >>> await client.sort_store("mylist", "sorted_list")
            3  # Indicates that the sorted list "sorted_list" contains three elements.
            >>> await client.lrange("sorted_list", 0, -1)
            ['1', '2', '3']
        """
        args = _build_sort_args(
            key, by_pattern, limit, get_patterns, order, alpha, store=destination
        )
        result = await self._execute_command(RequestType.Sort, args)
        return cast(int, result)
