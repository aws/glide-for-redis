# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

from typing import Dict, List, Mapping, Optional, Union, cast

from glide.async_commands.command_args import Limit, OrderBy
from glide.async_commands.core import (
    CoreCommands,
    FlushMode,
    InfoSection,
    _build_sort_args,
)
from glide.async_commands.transaction import BaseTransaction, ClusterTransaction
from glide.constants import TOK, TClusterResponse, TResult, TSingleNodeRoute
from glide.protobuf.redis_request_pb2 import RequestType
from glide.routes import Route


class ClusterCommands(CoreCommands):
    async def custom_command(
        self, command_args: List[str], route: Optional[Route] = None
    ) -> TResult:
        """
        Executes a single command, without checking inputs.
        See the [Glide for Redis Wiki](https://github.com/aws/glide-for-redis/wiki/General-Concepts#custom-command)
        for details on the restrictions and limitations of the custom command API.

            @example - Return a list of all pub/sub clients from all nodes:

                connection.customCommand(["CLIENT", "LIST","TYPE", "PUBSUB"], AllNodes())
        Args:
            command_args (List[str]): List of strings of the command's arguments.
            Every part of the command, including the command name and subcommands, should be added as a separate value in args.
            route (Optional[Route]): The command will be routed automatically based on the passed command's default request policy, unless `route` is provided, in which
            case the client will route the command to the nodes defined by `route`. Defaults to None.

        Returns:
            TResult: The returning value depends on the executed command and the route
        """
        return await self._execute_command(
            RequestType.CustomCommand, command_args, route
        )

    async def info(
        self,
        sections: Optional[List[InfoSection]] = None,
        route: Optional[Route] = None,
    ) -> TClusterResponse[bytes]:
        """
        Get information and statistics about the Redis server.
        See https://redis.io/commands/info/ for details.

        Args:
            sections (Optional[List[InfoSection]]): A list of InfoSection values specifying which sections of
            information to retrieve. When no parameter is provided, the default option is assumed.
            route (Optional[Route]): The command will be routed to all primaries, unless `route` is provided, in which
            case the client will route the command to the nodes defined by `route`. Defaults to None.

        Returns:
            TClusterResponse[str]: If a single node route is requested, returns a string containing the information for
            the required sections. Otherwise, returns a dict of strings, with each key containing the address of
            the queried node and value containing the information regarding the requested sections.
        """
        args = [section.value for section in sections] if sections else []

        return cast(
            TClusterResponse[bytes],
            await self._execute_command(RequestType.Info, args, route),
        )

    async def exec(
        self,
        transaction: BaseTransaction | ClusterTransaction,
        route: Optional[TSingleNodeRoute] = None,
    ) -> Optional[List[TResult]]:
        """
        Execute a transaction by processing the queued commands.
        See https://redis.io/topics/Transactions/ for details on Redis Transactions.

        Args:
            transaction (ClusterTransaction): A ClusterTransaction object containing a list of commands to be executed.
            route (Optional[TSingleNodeRoute]): If `route` is not provided, the transaction will be routed to the slot owner of the
            first key found in the transaction. If no key is found, the command will be sent to a random node.
            If `route` is provided, the client will route the command to the nodes defined by `route`.

        Returns:
            Optional[List[TResult]]: A list of results corresponding to the execution of each command
            in the transaction. If a command returns a value, it will be included in the list. If a command
            doesn't return a value, the list entry will be None.
            If the transaction failed due to a WATCH command, `exec` will return `None`.
        """
        commands = transaction.commands[:]
        return await self._execute_transaction(commands, route)

    async def config_resetstat(
        self,
        route: Optional[Route] = None,
    ) -> TOK:
        """
        Resets the statistics reported by Redis using the INFO and LATENCY HISTOGRAM commands.
        See https://redis.io/commands/config-resetstat/ for details.

        Args:
            route (Optional[Route]): The command will be routed automatically to all nodes, unless `route` is provided, in which
            case the client will route the command to the nodes defined by `route`. Defaults to None.

        Returns:
            OK: Returns "OK" to confirm that the statistics were successfully reset.
        """
        return cast(
            TOK, await self._execute_command(RequestType.ConfigResetStat, [], route)
        )

    async def config_rewrite(
        self,
        route: Optional[Route] = None,
    ) -> TOK:
        """
        Rewrite the configuration file with the current configuration.
        See https://redis.io/commands/config-rewrite/ for details.

        Args:
            route (Optional[TRoute]): The command will be routed automatically to all nodes, unless `route` is provided, in which
            case the client will route the command to the nodes defined by `route`. Defaults to None.

        Returns:
            OK: OK is returned when the configuration was rewritten properly. Otherwise an error is raised.

        Example:
            >>> await client.config_rewrite()
                'OK'
        """
        return cast(
            TOK, await self._execute_command(RequestType.ConfigRewrite, [], route)
        )

    async def client_id(
        self,
        route: Optional[Route] = None,
    ) -> TClusterResponse[int]:
        """
        Returns the current connection id.
        See https://redis.io/commands/client-id/ for more information.

        Args:
            route (Optional[Route]): The command will be sent to a random node, unless `route` is provided, in which
            case the client will route the command to the nodes defined by `route`.

        Returns:
            TClusterResponse[int]: The id of the client.
            If a single node route is requested, returns a int representing the client's id.
            Otherwise, returns a dict of [str , int] where each key contains the address of
            the queried node and the value contains the client's id.
        """
        return cast(
            TClusterResponse[int],
            await self._execute_command(RequestType.ClientId, [], route),
        )

    async def ping(
        self, message: Optional[str] = None, route: Optional[Route] = None
    ) -> str:
        """
        Ping the Redis server.
        See https://redis.io/commands/ping/ for more details.

        Args:
            message (Optional[str]): An optional message to include in the PING command. If not provided,
            the server will respond with "PONG". If provided, the server will respond with a copy of the message

            route (Optional[Route]): The command will be sent to all primaries, unless `route` is provided, in which
            case the client will route the command to the nodes defined by `route`

        Returns:
           str: "PONG" if `message` is not provided, otherwise return a copy of `message`.

        Examples:
            >>> await client.ping()
            "PONG"
            >>> await client.ping("Hello")
            "Hello"
        """
        argument = [] if message is None else [message]
        return cast(str, await self._execute_command(RequestType.Ping, argument, route))

    async def config_get(
        self, parameters: List[str], route: Optional[Route] = None
    ) -> TClusterResponse[Dict[str, str]]:
        """
        Get the values of configuration parameters.
        See https://redis.io/commands/config-get/ for details.

        Args:
            parameters (List[str]): A list of configuration parameter names to retrieve values for.

            route (Optional[Route]): The command will be routed to a random node, unless `route` is provided,
            in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TClusterResponse[Dict[str, str]]: A dictionary of values corresponding to the
            configuration parameters.
            When specifying a route other than a single node, response will be : {Address (str) : response (Dict[str, str]) , ... }
            with type of Dict[str, Dict[str, str]].

        Examples:
            >>> await client.config_get(["timeout"] , RandomNode())
            {'timeout': '1000'}
            >>> await client.config_get(["timeout" , "maxmemory"])
            {'timeout': '1000', "maxmemory": "1GB"}
        """
        return cast(
            TClusterResponse[Dict[str, str]],
            await self._execute_command(RequestType.ConfigGet, parameters, route),
        )

    async def config_set(
        self, parameters_map: Mapping[str, str], route: Optional[Route] = None
    ) -> TOK:
        """
        Set configuration parameters to the specified values.
        See https://redis.io/commands/config-set/ for details.

        Args:
            parameters_map (Mapping[str, str]): A map consisting of configuration
            parameters and their respective values to set.

            route (Optional[Route]): The command will be routed to all nodes, unless `route` is provided,
            in which case the client will route the command to the nodes defined by `route`.

        Returns:
            OK: Returns OK if all configurations have been successfully set. Otherwise, raises an error.

        Examples:
            >>> await client.config_set([("timeout", "1000")], [("maxmemory", "1GB")])
            OK
        """
        parameters: List[str] = []
        for pair in parameters_map.items():
            parameters.extend(pair)
        return cast(
            TOK,
            await self._execute_command(RequestType.ConfigSet, parameters, route),
        )

    async def client_getname(
        self, route: Optional[Route] = None
    ) -> TClusterResponse[Optional[str]]:
        """
        Get the name of the connection to which the request is routed.
        See https://redis.io/commands/client-getname/ for more details.

        Args:
            route (Optional[Route]): The command will be routed to a random node, unless `route` is provided,
            in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TClusterResponse[Optional[str]]: The name of the client connection as a string if a name is set,
            or None if no name is assigned.
            When specifying a route other than a single node, response will be:
            {Address (str) : response (Optional[str]) , ... } with type of Dict[str, Optional[str]].

        Examples:
            >>> await client.client_getname()
            'Connection Name'
            >>> await client.client_getname(AllNodes())
            {'addr': 'Connection Name', 'addr2': 'Connection Name', 'addr3': 'Connection Name'}
        """
        return cast(
            TClusterResponse[Optional[str]],
            await self._execute_command(RequestType.ClientGetName, [], route),
        )

    async def dbsize(self, route: Optional[Route] = None) -> int:
        """
        Returns the number of keys in the database.
        See https://redis.io/commands/dbsize for more details.

        Args:
            route (Optional[Route]): The command will be routed to all primaries, unless `route` is provided,
            in which case the client will route the command to the nodes defined by `route`.

        Returns:
            int: The number of keys in the database.
            In the case of routing the query to multiple nodes, returns the aggregated number of keys across the different nodes.

        Examples:
            >>> await client.dbsize()
                10  # Indicates there are 10 keys in the cluster.
        """
        return cast(int, await self._execute_command(RequestType.DBSize, [], route))

    async def echo(
        self, message: str, route: Optional[Route] = None
    ) -> TClusterResponse[str]:
        """
        Echoes the provided `message` back.

        See https://redis.io/commands/echo for more details.

        Args:
            message (str): The message to be echoed back.
            route (Optional[Route]): The command will be routed to a random node, unless `route` is provided,
            in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TClusterResponse[str]: The provided `message`.
            When specifying a route other than a single node, response will be:
            {Address (str) : response (str) , ... } with type of Dict[str, str].

        Examples:
            >>> await client.echo("Glide-for-Redis")
                'Glide-for-Redis'
            >>> await client.echo("Glide-for-Redis", AllNodes())
                {'addr': 'Glide-for-Redis', 'addr2': 'Glide-for-Redis', 'addr3': 'Glide-for-Redis'}
        """
        return cast(
            TClusterResponse[str],
            await self._execute_command(RequestType.Echo, [message], route),
        )

    async def function_load(
        self, library_code: str, replace: bool = False, route: Optional[Route] = None
    ) -> str:
        """
        Loads a library to Redis.

        See https://valkey.io/docs/latest/commands/function-load/ for more details.

        Args:
            library_code (str): The source code that implements the library.
            replace (bool): Whether the given library should overwrite a library with the same name if
                it already exists.
            route (Optional[Route]): The command will be routed to all primaries, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            str: The library name that was loaded.

        Examples:
            >>> code = "#!lua name=mylib \n redis.register_function('myfunc', function(keys, args) return args[1] end)"
            >>> await client.function_load(code, True, RandomNode())
                "mylib"

        Since: Redis 7.0.0.
        """
        return cast(
            str,
            await self._execute_command(
                RequestType.FunctionLoad,
                ["REPLACE", library_code] if replace else [library_code],
                route,
            ),
        )

    async def function_flush(
        self, mode: Optional[FlushMode] = None, route: Optional[Route] = None
    ) -> TOK:
        """
        Deletes all function libraries.

        See https://valkey.io/docs/latest/commands/function-flush/ for more details.

        Args:
            mode (Optional[FlushMode]): The flushing mode, could be either `SYNC` or `ASYNC`.
            route (Optional[Route]): The command will be routed to all primaries, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TOK: A simple `OK`.

        Examples:
            >>> await client.function_flush(FlushMode.SYNC)
                "OK"

        Since: Redis 7.0.0.
        """
        return cast(
            TOK,
            await self._execute_command(
                RequestType.FunctionFlush,
                [mode.value] if mode else [],
                route,
            ),
        )

    async def function_delete(
        self, library_name: str, route: Optional[Route] = None
    ) -> TOK:
        """
        Deletes a library and all its functions.

        See https://valkey.io/docs/latest/commands/function-delete/ for more details.

        Args:
            library_code (str): The libary name to delete
            route (Optional[Route]): The command will be routed to all primaries, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TOK: A simple `OK`.

        Examples:
            >>> await client.function_delete("my_lib")
                "OK"

        Since: Redis 7.0.0.
        """
        return cast(
            TOK,
            await self._execute_command(
                RequestType.FunctionDelete,
                [library_name],
                route,
            ),
        )

    async def fcall_route(
        self,
        function: str,
        arguments: Optional[List[str]] = None,
        route: Optional[Route] = None,
    ) -> TClusterResponse[TResult]:
        """
        Invokes a previously loaded function.
        See https://redis.io/commands/fcall/ for more details.

        Args:
            function (str): The function name.
            arguments (Optional[List[str]]): A list of `function` arguments. `Arguments`
                should not represent names of keys.
            route (Optional[Route]): The command will be routed to a random primay node, unless `route` is provided, in which
                case the client will route the command to the nodes defined by `route`. Defaults to None.

        Returns:
            TClusterResponse[TResult]:
                If a single node route is requested, returns a Optional[TResult] representing the function's return value.
                Otherwise, returns a dict of [str , Optional[TResult]] where each key contains the address of
                the queried node and the value contains the function's return value.

        Example:
            >>> await client.fcall("Deep_Thought", ["Answer", "to", "the", "Ultimate", "Question", "of", "Life,", "the", "Universe,", "and", "Everything"], RandomNode())
                'new_value' # Returns the function's return value.

        Since: Redis version 7.0.0.
        """
        args = [function, "0"]
        if arguments is not None:
            args.extend(arguments)
        return cast(
            TClusterResponse[TResult],
            await self._execute_command(RequestType.FCall, args, route),
        )

    async def fcall_ro_route(
        self,
        function: str,
        arguments: Optional[List[str]] = None,
        route: Optional[Route] = None,
    ) -> TClusterResponse[TResult]:
        """
        Invokes a previously loaded read-only function.

        See https://valkey.io/commands/fcall_ro for more details.

        Args:
            function (str): The function name.
            arguments (List[str]): An `array` of `function` arguments. `arguments` should not
                represent names of keys.
            route (Optional[Route]): Specifies the routing configuration of the command. The client
                will route the command to the nodes defined by `route`.

        Returns:
            TClusterResponse[TResult]: The return value depends on the function that was executed.

        Examples:
            >>> await client.fcall_ro_route("Deep_Thought", ALL_NODES)
                42 # The return value on the function that was executed

        Since: Redis version 7.0.0.
        """
        args = [function, "0"]
        if arguments is not None:
            args.extend(arguments)
        return cast(
            TClusterResponse[TResult],
            await self._execute_command(RequestType.FCallReadOnly, args, route),
        )

    async def time(self, route: Optional[Route] = None) -> TClusterResponse[List[str]]:
        """
        Returns the server time.

        See https://redis.io/commands/time/ for more details.

        Args:
            route (Optional[Route]): The command will be routed to a random node, unless `route` is provided,
            in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TClusterResponse[Optional[str]]:  The current server time as a two items `array`:
            A Unix timestamp and the amount of microseconds already elapsed in the current second.
            The returned `array` is in a [Unix timestamp, Microseconds already elapsed] format.
            When specifying a route other than a single node, response will be:
            {Address (str) : response (List[str]) , ... } with type of Dict[str, List[str]].

        Examples:
            >>> await client.time()
            ['1710925775', '913580']
            >>> await client.time(AllNodes())
            {'addr': ['1710925775', '913580'], 'addr2': ['1710925775', '913580'], 'addr3': ['1710925775', '913580']}
        """
        return cast(
            TClusterResponse[List[str]],
            await self._execute_command(RequestType.Time, [], route),
        )

    async def lastsave(self, route: Optional[Route] = None) -> TClusterResponse[int]:
        """
        Returns the Unix time of the last DB save timestamp or startup timestamp if no save was made since then.

        See https://valkey.io/commands/lastsave for more details.

        Args:
            route (Optional[Route]): The command will be routed to a random node, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TClusterResponse[int]: The Unix time of the last successful DB save.
                If no route is provided, or a single node route is requested, returns an int representing the Unix time
                of the last successful DB save. Otherwise, returns a dict of [str , int] where each key contains the
                address of the queried node and the value contains the Unix time of the last successful DB save.

        Examples:
            >>> await client.lastsave()
            1710925775  # Unix time of the last DB save
            >>> await client.lastsave(AllNodes())
            {'addr1': 1710925775, 'addr2': 1710925775, 'addr3': 1710925775}  # Unix time of the last DB save on each node
        """
        return cast(
            TClusterResponse[int],
            await self._execute_command(RequestType.LastSave, [], route),
        )

    async def sort(
        self,
        key: str,
        limit: Optional[Limit] = None,
        order: Optional[OrderBy] = None,
        alpha: Optional[bool] = None,
    ) -> List[str]:
        """
        Sorts the elements in the list, set, or sorted set at `key` and returns the result.
        To store the result into a new key, see `sort_store`.

        By default, sorting is numeric, and elements are compared by their value interpreted as double precision floating point numbers.

        See https://valkey.io/commands/sort for more details.

        Args:
            key (str): The key of the list, set, or sorted set to be sorted.
            limit (Optional[Limit]): Limiting the range of the query by setting offset and result count. See `Limit` class for more information.
            order (Optional[OrderBy]): Specifies the order to sort the elements.
                Can be `OrderBy.ASC` (ascending) or `OrderBy.DESC` (descending).
            alpha (Optional[bool]): When `True`, sorts elements lexicographically. When `False` (default), sorts elements numerically.
                Use this when the list, set, or sorted set contains string values that cannot be converted into double precision floating point numbers.

        Returns:
            List[str]: A list of sorted elements.

        Examples:
            >>> await client.lpush("mylist", '3', '1', '2')
            >>> await client.sort("mylist")
            ['1', '2', '3']

            >>> await client.sort("mylist", order=OrderBy.DESC)
            ['3', '2', '1']

            >>> await client.lpush("mylist", '2', '1', '2', '3', '3', '1')
            >>> await client.sort("mylist", limit=Limit(2, 3))
            ['1', '2', '2']

            >>> await client.lpush("mylist", "a", "b", "c", "d")
            >>> await client.sort("mylist", limit=Limit(2, 2), order=OrderBy.DESC, alpha=True)
            ['b', 'a']
        """
        args = _build_sort_args(key, None, limit, None, order, alpha)
        result = await self._execute_command(RequestType.Sort, args)
        return cast(List[str], result)

    async def sort_store(
        self,
        key: str,
        destination: str,
        limit: Optional[Limit] = None,
        order: Optional[OrderBy] = None,
        alpha: Optional[bool] = None,
    ) -> int:
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

        Returns:
            int: The number of elements in the sorted key stored at `store`.

        Examples:
            >>> await client.lpush("mylist", 3, 1, 2)
            >>> await client.sort_store("mylist", "sorted_list")
            3  # Indicates that the sorted list "sorted_list" contains three elements.
            >>> await client.lrange("sorted_list", 0, -1)
            ['1', '2', '3']
        """
        args = _build_sort_args(key, None, limit, None, order, alpha, store=destination)
        result = await self._execute_command(RequestType.Sort, args)
        return cast(int, result)

    async def publish(self, message: str, channel: str, sharded: bool = False) -> int:
        """
        Publish a message on pubsub channel.
        This command aggregates PUBLISH and SPUBLISH commands functionalities.
        The mode is selected using the 'sharded' parameter.
        For both sharded and non-sharded mode, request is routed using hashed channel as key.
        See https://valkey.io/commands/publish and https://valkey.io/commands/spublish for more details.

        Args:
            message (str): Message to publish
            channel (str): Channel to publish the message on.
            sharded (bool): Use sharded pubsub mode. Available since Redis version 7.0.

        Returns:
            int: Number of subscriptions in that node that received the message.

        Examples:
            >>> await client.publish("Hi all!", "global-channel", False)
                1  # Published 1 instance of "Hi all!" message on global-channel channel using non-sharded mode
            >>> await client.publish("Hi to sharded channel1!", "channel1, True)
                2  # Published 2 instances of "Hi to sharded channel1!" message on channel1 using sharded mode
        """
        result = await self._execute_command(
            RequestType.SPublish if sharded else RequestType.Publish, [channel, message]
        )
        return cast(int, result)

    async def flushall(
        self, flush_mode: Optional[FlushMode] = None, route: Optional[Route] = None
    ) -> TClusterResponse[TOK]:
        """
        Deletes all the keys of all the existing databases. This command never fails.

        See https://valkey.io/commands/flushall for more details.

        Args:
            flush_mode (Optional[FlushMode]): The flushing mode, could be either `SYNC` or `ASYNC`.
            route (Optional[Route]): The command will be routed to all primary nodes, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TClusterResponse[TOK]: OK.

        Examples:
             >>> await client.flushall(FlushMode.ASYNC)
                 OK  # This command never fails.
             >>> await client.flushall(FlushMode.ASYNC, AllNodes())
                 OK  # This command never fails.
        """
        args = []
        if flush_mode is not None:
            args.append(flush_mode.value)

        return cast(
            TClusterResponse[TOK],
            await self._execute_command(RequestType.FlushAll, args, route),
        )

    async def flushdb(
        self, flush_mode: Optional[FlushMode] = None, route: Optional[Route] = None
    ) -> TClusterResponse[TOK]:
        """
        Deletes all the keys of the currently selected database. This command never fails.

        See https://valkey.io/commands/flushdb for more details.

        Args:
            flush_mode (Optional[FlushMode]): The flushing mode, could be either `SYNC` or `ASYNC`.
            route (Optional[Route]): The command will be routed to all primary nodes, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TOK: OK.

        Examples:
             >>> await client.flushdb()
                 OK  # The keys of the currently selected database were deleted.
             >>> await client.flushdb(FlushMode.ASYNC)
                 OK  # The keys of the currently selected database were deleted asynchronously.
             >>> await client.flushdb(FlushMode.ASYNC, AllNodes())
                 OK  # The keys of the currently selected database were deleted asynchronously on all nodes.
        """
        args = []
        if flush_mode is not None:
            args.append(flush_mode.value)

        return cast(
            TClusterResponse[TOK],
            await self._execute_command(RequestType.FlushDB, args, route),
        )

    async def copy(
        self,
        source: str,
        destination: str,
        replace: Optional[bool] = None,
    ) -> bool:
        """
        Copies the value stored at the `source` to the `destination` key. When `replace` is True,
        removes the `destination` key first if it already exists, otherwise performs no action.

        See https://valkey.io/commands/copy for more details.

        Note:
            Both `source` and `destination` must map to the same hash slot.

        Args:
            source (str): The key to the source value.
            destination (str): The key where the value should be copied to.
            replace (Optional[bool]): If the destination key should be removed before copying the value to it.

        Returns:
            bool: True if the source was copied. Otherwise, returns False.

        Examples:
            >>> await client.set("source", "sheep")
            >>> await client.copy("source", "destination")
                True # Source was copied
            >>> await client.get("destination")
                "sheep"

        Since: Redis version 6.2.0.
        """
        args = [source, destination]
        if replace is True:
            args.append("REPLACE")
        return cast(
            bool,
            await self._execute_command(RequestType.Copy, args),
        )

    async def lolwut(
        self,
        version: Optional[int] = None,
        parameters: Optional[List[int]] = None,
        route: Optional[Route] = None,
    ) -> TClusterResponse[str]:
        """
        Displays a piece of generative computer art and the Redis version.

        See https://valkey.io/commands/lolwut for more details.

        Args:
            version (Optional[int]): Version of computer art to generate.
            parameters (Optional[List[int]]): Additional set of arguments in order to change the output:
                For version `5`, those are length of the line, number of squares per row, and number of squares per column.
                For version `6`, those are number of columns and number of lines.
            route (Optional[Route]): The command will be routed to a random node, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TClusterResponse[str]: A piece of generative computer art along with the current Redis version.

        Examples:
            >>> await client.lolwut(6, [40, 20], ALL_NODES);
            "Redis ver. 7.2.3" # Indicates the current Redis version
        """
        args = []
        if version is not None:
            args.extend(["VERSION", str(version)])
        if parameters:
            for var in parameters:
                args.extend(str(var))
        return cast(
            TClusterResponse[str],
            await self._execute_command(RequestType.Lolwut, args, route),
        )

    async def random_key(self, route: Optional[Route] = None) -> Optional[str]:
        """
        Returns a random existing key name.

        See https://valkey.io/commands/randomkey for more details.

        Args:
            route (Optional[Route]): The command will be routed to all primary nodes, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            Optional[str]: A random existing key name.

        Examples:
            >>> await client.random_key()
            "random_key_name"  # "random_key_name" is a random existing key name.
        """
        return cast(
            Optional[str],
            await self._execute_command(RequestType.RandomKey, [], route),
        )

    async def wait(
        self,
        numreplicas: int,
        timeout: int,
        route: Optional[Route] = None,
    ) -> int:
        """
        Blocks the current client until all the previous write commands are successfully transferred
        and acknowledged by at least `numreplicas` of replicas. If `timeout` is
        reached, the command returns even if the specified number of replicas were not yet reached.

        See https://valkey.io/commands/wait for more details.

        Args:
            numreplicas (int): The number of replicas to reach.
            timeout (int): The timeout value specified in milliseconds. A value of 0 will block indefinitely.
            route (Optional[Route]): The command will be routed to all primary nodes, unless `route` is provided,
            in which case the client will route the command to the nodes defined by `route`.
        Returns:
            int: The number of replicas reached by all the writes performed in the context of the current connection.

        Examples:
            >>> await client.set("key", "value");
            >>> await client.wait(1, 1000);
            // return 1 when a replica is reached or 0 if 1000ms is reached.
        """
        args = [str(numreplicas), str(timeout)]
        return cast(
            int,
            await self._execute_command(RequestType.Wait, args, route),
        )

    async def unwatch(self, route: Optional[Route] = None) -> TOK:
        """
        Flushes all the previously watched keys for a transaction. Executing a transaction will
        automatically flush all previously watched keys.

        See https://valkey.io/commands/unwatch for more details.

        Args:
            route (Optional[Route]): The command will be routed to all primary nodes, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TOK: A simple "OK" response.

        Examples:
            >>> await client.unwatch()
                'OK'
        """
        return cast(
            TOK,
            await self._execute_command(RequestType.UnWatch, [], route),
        )
