# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

from typing import Dict, List, Mapping, Optional, cast

from glide.async_commands.core import CoreCommands, InfoSection
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
            @remarks - This function should only be used for single-response commands. Commands that don't return response (such as SUBSCRIBE), or that return potentially more than a single response (such as XREAD), or that change the client's behavior (such as entering pub/sub mode on RESP2 connections) shouldn't be called using this function.
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
    ) -> TClusterResponse[str]:
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
            TClusterResponse[str],
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
            >>> await client.client_getname(AllNodes())
            {'addr': ['1710925775', '913580'], 'addr2': ['1710925775', '913580'], 'addr3': ['1710925775', '913580']}
        """
        return cast(
            TClusterResponse[List[str]],
            await self._execute_command(RequestType.Time, [], route),
        )
