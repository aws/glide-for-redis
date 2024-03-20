# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

from typing import Dict, List, Mapping, Optional, cast

from glide.async_commands.core import CoreCommands, InfoSection
from glide.async_commands.transaction import BaseTransaction, Transaction
from glide.constants import TOK, TResult
from glide.protobuf.redis_request_pb2 import RequestType


class StandaloneCommands(CoreCommands):
    async def custom_command(self, command_args: List[str]) -> TResult:
        """
        Executes a single command, without checking inputs.
            @remarks - This function should only be used for single-response commands. Commands that don't return response (such as SUBSCRIBE), or that return potentially more than a single response (such as XREAD), or that change the client's behavior (such as entering pub/sub mode on RESP2 connections) shouldn't be called using this function.
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
