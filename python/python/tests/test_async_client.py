# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

import asyncio
import random
import string
import time
from datetime import datetime, timedelta, timezone
from typing import Dict, List, TypeVar, Union, cast

import pytest
from glide import ClosingError, RequestError, Script, TimeoutError
from glide.async_commands.core import (
    ConditionalChange,
    ExpireOptions,
    ExpirySet,
    ExpiryType,
    InfBound,
    InfoSection,
    UpdateOptions,
)
from glide.async_commands.sorted_set import (
    InfBound,
    LexBoundary,
    Limit,
    RangeByIndex,
    RangeByLex,
    RangeByScore,
    ScoreBoundary,
)
from glide.config import ProtocolVersion, RedisCredentials
from glide.constants import OK
from glide.redis_client import RedisClient, RedisClusterClient, TRedisClient
from glide.routes import (
    AllNodes,
    AllPrimaries,
    ByAddressRoute,
    RandomNode,
    Route,
    SlotIdRoute,
    SlotKeyRoute,
    SlotType,
)
from packaging import version
from tests.conftest import create_client

T = TypeVar("T")


def is_single_response(response: T, single_res: T) -> bool:
    """
    Recursively checks if a given response matches the type structure of single_res.

    Args:
        response (T): The response to check.
        single_res (T): An object with the expected type structure as an example for the single node response.

    Returns:
        bool: True if response matches the structure of single_res, False otherwise.

     Example:
        >>> is_single_response(["value"], LIST_STR)
        True
        >>> is_single_response([["value"]], LIST_STR)
        False
    """
    if isinstance(single_res, list) and isinstance(response, list):
        return is_single_response(response[0], single_res[0])
    elif isinstance(response, type(single_res)):
        return True
    return False


def get_first_result(res: str | List[str] | List[List[str]] | Dict[str, str]) -> str:
    while isinstance(res, list):
        res = (
            res[1]
            if not isinstance(res[0], list) and res[0].startswith("127.0.0.1")
            else res[0]
        )

    if isinstance(res, dict):
        res = list(res.values())[0]

    return res


def parse_info_response(res: str | Dict[str, str]) -> Dict[str, str]:
    res = get_first_result(res)
    info_lines = [
        line for line in res.splitlines() if line and not line.startswith("#")
    ]
    info_dict = {}
    for line in info_lines:
        splitted_line = line.split(":")
        key = splitted_line[0]
        value = splitted_line[1]
        info_dict[key] = value
    return info_dict


def get_random_string(length):
    result_str = "".join(random.choice(string.ascii_letters) for i in range(length))
    return result_str


async def check_if_server_version_lt(client: TRedisClient, min_version: str) -> bool:
    # TODO: change it to pytest fixture after we'll implement a sync client
    info_str = await client.info([InfoSection.SERVER])
    redis_version = parse_info_response(info_str).get("redis_version")
    assert redis_version is not None
    return version.parse(redis_version) < version.parse(min_version)


@pytest.mark.asyncio
class TestRedisClients:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_register_client_name_and_version(self, redis_client: TRedisClient):
        min_version = "7.2.0"
        if await check_if_server_version_lt(redis_client, min_version):
            # TODO: change it to pytest fixture after we'll implement a sync client
            return pytest.mark.skip(reason=f"Redis version required >= {min_version}")
        info = await redis_client.custom_command(["CLIENT", "INFO"])
        assert type(info) is str
        assert "lib-name=GlidePy" in info
        assert "lib-ver=unknown" in info

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_send_and_receive_large_values(self, redis_client: TRedisClient):
        length = 2**16
        key = get_random_string(length)
        value = get_random_string(length)
        assert len(key) == length
        assert len(value) == length
        await redis_client.set(key, value)
        assert await redis_client.get(key) == value

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_send_and_receive_non_ascii_unicode(self, redis_client: TRedisClient):
        key = "foo"
        value = "שלום hello 汉字"
        assert value == "שלום hello 汉字"
        await redis_client.set(key, value)
        assert await redis_client.get(key) == value

    @pytest.mark.parametrize("value_size", [100, 2**16])
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_client_handle_concurrent_workload_without_dropping_or_changing_values(
        self, redis_client: TRedisClient, value_size
    ):
        num_of_concurrent_tasks = 100
        running_tasks = set()

        async def exec_command(i):
            range_end = 1 if value_size > 100 else 100
            for _ in range(range_end):
                value = get_random_string(value_size)
                assert await redis_client.set(str(i), value) == OK
                assert await redis_client.get(str(i)) == value

        for i in range(num_of_concurrent_tasks):
            task = asyncio.create_task(exec_command(i))
            running_tasks.add(task)
            task.add_done_callback(running_tasks.discard)
        await asyncio.gather(*(list(running_tasks)))

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_can_connect_with_auth_requirepass(
        self, redis_client: TRedisClient, request
    ):
        is_cluster = isinstance(redis_client, RedisClusterClient)
        password = "TEST_AUTH"
        credentials = RedisCredentials(password)
        try:
            await redis_client.custom_command(
                ["CONFIG", "SET", "requirepass", password]
            )

            with pytest.raises(ClosingError, match="NOAUTH"):
                # Creation of a new client without password should fail
                await create_client(
                    request,
                    is_cluster,
                    addresses=redis_client.config.addresses,
                )

            auth_client = await create_client(
                request,
                is_cluster,
                credentials,
                addresses=redis_client.config.addresses,
            )
            key = get_random_string(10)
            assert await auth_client.set(key, key) == OK
            assert await auth_client.get(key) == key
        finally:
            # Reset the password
            auth_client = await create_client(
                request,
                is_cluster,
                credentials,
                addresses=redis_client.config.addresses,
            )
            await auth_client.custom_command(["CONFIG", "SET", "requirepass", ""])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_can_connect_with_auth_acl(
        self, redis_client: Union[RedisClient, RedisClusterClient], request
    ):
        is_cluster = isinstance(redis_client, RedisClusterClient)
        username = "testuser"
        password = "TEST_AUTH"
        try:
            assert (
                await redis_client.custom_command(
                    [
                        "ACL",
                        "SETUSER",
                        username,
                        "on",
                        "allkeys",
                        "+get",
                        "+cluster",
                        "+ping",
                        "+info",
                        "+client",
                        f">{password}",
                    ]
                )
                == OK
            )
            key = get_random_string(10)
            assert await redis_client.set(key, key) == OK
            credentials = RedisCredentials(password, username)

            testuser_client = await create_client(
                request,
                is_cluster,
                credentials,
                addresses=redis_client.config.addresses,
            )
            assert await testuser_client.get(key) == key
            with pytest.raises(RequestError) as e:
                # This client isn't authorized to perform SET
                await testuser_client.set("foo", "bar")
            assert "NOPERM" in str(e)
        finally:
            # Delete this user
            await redis_client.custom_command(["ACL", "DELUSER", username])

    async def test_select_standalone_database_id(self, request):
        redis_client = await create_client(request, cluster_mode=False, database_id=4)
        client_info = await redis_client.custom_command(["CLIENT", "INFO"])
        assert "db=4" in client_info

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_client_name(self, request, cluster_mode, protocol):
        redis_client = await create_client(
            request,
            cluster_mode=cluster_mode,
            client_name="TEST_CLIENT_NAME",
            protocol=protocol,
        )
        client_info = await redis_client.custom_command(["CLIENT", "INFO"])
        assert "name=TEST_CLIENT_NAME" in client_info

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_closed_client_raises_error(self, redis_client: TRedisClient):
        await redis_client.close()
        with pytest.raises(ClosingError) as e:
            await redis_client.set("foo", "bar")
        assert "the client is closed" in str(e)


@pytest.mark.asyncio
class TestCommands:
    @pytest.mark.smoke_test
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_socket_set_get(self, redis_client: TRedisClient):
        key = get_random_string(10)
        value = datetime.now(timezone.utc).strftime("%m/%d/%Y, %H:%M:%S")
        assert await redis_client.set(key, value) == OK
        assert await redis_client.get(key) == value

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP3])
    async def test_use_resp3_protocol(self, redis_client: TRedisClient):
        result = cast(Dict[str, str], await redis_client.custom_command(["HELLO"]))

        assert int(result["proto"]) == 3

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2])
    async def test_allow_opt_in_to_resp2_protocol(self, redis_client: TRedisClient):
        result = cast(Dict[str, str], await redis_client.custom_command(["HELLO"]))

        assert int(result["proto"]) == 2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_conditional_set(self, redis_client: TRedisClient):
        key = get_random_string(10)
        value = get_random_string(10)
        res = await redis_client.set(
            key, value, conditional_set=ConditionalChange.ONLY_IF_EXISTS
        )
        assert res is None
        res = await redis_client.set(
            key, value, conditional_set=ConditionalChange.ONLY_IF_DOES_NOT_EXIST
        )
        assert res == OK
        assert await redis_client.get(key) == value
        res = await redis_client.set(
            key, "foobar", conditional_set=ConditionalChange.ONLY_IF_DOES_NOT_EXIST
        )
        assert res is None
        assert await redis_client.get(key) == value

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_set_return_old_value(self, redis_client: TRedisClient):
        min_version = "6.2.0"
        if await check_if_server_version_lt(redis_client, min_version):
            # TODO: change it to pytest fixture after we'll implement a sync client
            return pytest.mark.skip(reason=f"Redis version required >= {min_version}")
        key = get_random_string(10)
        value = get_random_string(10)
        res = await redis_client.set(key, value)
        assert res == OK
        assert await redis_client.get(key) == value
        new_value = get_random_string(10)
        res = await redis_client.set(key, new_value, return_old_value=True)
        assert res == value
        assert await redis_client.get(key) == new_value

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_custom_command_single_arg(self, redis_client: TRedisClient):
        # Test single arg command
        res = await redis_client.custom_command(["PING"])
        assert res == "PONG"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_custom_command_multi_arg(self, redis_client: TRedisClient):
        # Test multi args command
        client_list = await redis_client.custom_command(
            ["CLIENT", "LIST", "TYPE", "NORMAL"]
        )
        assert isinstance(client_list, (str, list))
        res: str = get_first_result(client_list)
        assert res is not None
        assert "id" in res
        assert "cmd=client" in res

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_custom_command_lower_and_upper_case(
        self, redis_client: TRedisClient
    ):
        # Test multi args command
        client_list = await redis_client.custom_command(
            ["CLIENT", "LIST", "TYPE", "NORMAL"]
        )
        assert isinstance(client_list, (str, list))
        res: str = get_first_result(client_list)
        assert res is not None
        assert "id" in res
        assert "cmd=client" in res

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_request_error_raises_exception(self, redis_client: TRedisClient):
        key = get_random_string(10)
        value = get_random_string(10)
        await redis_client.set(key, value)
        with pytest.raises(RequestError) as e:
            await redis_client.custom_command(["HSET", key, "1", "bar"])
        assert "WRONGTYPE" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_info_server_replication(self, redis_client: TRedisClient):
        info = get_first_result(await redis_client.info([InfoSection.SERVER]))
        assert "# Server" in info
        cluster_mode = parse_info_response(info)["redis_mode"]
        expected_cluster_mode = isinstance(redis_client, RedisClusterClient)
        assert cluster_mode == "cluster" if expected_cluster_mode else "standalone"
        info = get_first_result(await redis_client.info([InfoSection.REPLICATION]))
        assert "# Replication" in info
        assert "# Errorstats" not in info

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_info_default(self, redis_client: TRedisClient):
        cluster_mode = isinstance(redis_client, RedisClusterClient)
        info_result = await redis_client.info()
        if cluster_mode:
            cluster_nodes = await redis_client.custom_command(["CLUSTER", "NODES"])
            assert isinstance(cluster_nodes, (str, list))
            cluster_nodes = get_first_result(cluster_nodes)
            expected_num_of_results = cluster_nodes.count("master")
            assert len(info_result) == expected_num_of_results
        info_result = get_first_result(info_result)
        assert "# Memory" in info_result

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_select(self, redis_client: RedisClient):
        assert await redis_client.select(0) == OK
        key = get_random_string(10)
        value = get_random_string(10)
        assert await redis_client.set(key, value) == OK
        assert await redis_client.get(key) == value
        assert await redis_client.select(1) == OK
        assert await redis_client.get(key) is None
        assert await redis_client.select(0) == OK
        assert await redis_client.get(key) == value

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_delete(self, redis_client: TRedisClient):
        keys = [get_random_string(10), get_random_string(10), get_random_string(10)]
        value = get_random_string(10)
        [await redis_client.set(key, value) for key in keys]
        assert await redis_client.get(keys[0]) == value
        assert await redis_client.get(keys[1]) == value
        assert await redis_client.get(keys[2]) == value
        delete_keys = keys + [get_random_string(10)]
        assert await redis_client.delete(delete_keys) == 3
        assert await redis_client.delete(keys) == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_config_reset_stat(self, redis_client: TRedisClient):
        # we execute set and info so the commandstats will show `cmdstat_set::calls` greater than 1
        # after the configResetStat call we initiate an info command and the the commandstats won't contain `cmdstat_set`.
        await redis_client.set("foo", "bar")
        info_stats = str(await redis_client.info([InfoSection.COMMAND_STATS]))

        assert "cmdstat_set" in info_stats

        assert await redis_client.config_resetstat() == OK
        info_stats = str(await redis_client.info([InfoSection.COMMAND_STATS]))

        # 1 stands for the second info command
        assert "cmdstat_set" not in info_stats

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_config_rewrite(self, redis_client: TRedisClient):
        info_server = parse_info_response(
            get_first_result(await redis_client.info([InfoSection.SERVER]))
        )
        if len(info_server["config_file"]) > 0:
            assert await redis_client.config_rewrite() == OK
        else:
            # We expect Redis to return an error since the test cluster doesn't use redis.conf file
            with pytest.raises(RequestError) as e:
                await redis_client.config_rewrite()
            assert "The server is running without a config file" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_client_id(self, redis_client: TRedisClient):
        client_id = await redis_client.client_id()
        assert type(client_id) is int
        assert client_id > 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_incr_commands_existing_key(self, redis_client: TRedisClient):
        key = get_random_string(10)
        assert await redis_client.set(key, "10") == OK
        assert await redis_client.incr(key) == 11
        assert await redis_client.get(key) == "11"
        assert await redis_client.incrby(key, 4) == 15
        assert await redis_client.get(key) == "15"
        assert await redis_client.incrbyfloat(key, 5.5) == 20.5
        assert await redis_client.get(key) == "20.5"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_incr_commands_non_existing_key(self, redis_client: TRedisClient):
        key = get_random_string(10)
        key2 = get_random_string(10)
        key3 = get_random_string(10)

        assert await redis_client.get(key) is None
        assert await redis_client.incr(key) == 1
        assert await redis_client.get(key) == "1"

        assert await redis_client.get(key2) is None
        assert await redis_client.incrby(key2, 3) == 3
        assert await redis_client.get(key2) == "3"

        assert await redis_client.get(key3) is None
        assert await redis_client.incrbyfloat(key3, 0.5) == 0.5
        assert await redis_client.get(key3) == "0.5"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_incr_commands_with_str_value(self, redis_client: TRedisClient):
        key = get_random_string(10)
        assert await redis_client.set(key, "foo") == OK
        with pytest.raises(RequestError) as e:
            await redis_client.incr(key)

        assert "value is not an integer" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.incrby(key, 3)

        assert "value is not an integer" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.incrbyfloat(key, 3.5)
        assert "value is not a valid float" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_client_getname(self, redis_client: TRedisClient):
        assert await redis_client.client_getname() is None
        assert (
            await redis_client.custom_command(["CLIENT", "SETNAME", "GlideConnection"])
            == OK
        )
        assert await redis_client.client_getname() == "GlideConnection"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_mset_mget(self, redis_client: TRedisClient):
        keys = [get_random_string(10), get_random_string(10), get_random_string(10)]
        non_existing_key = get_random_string(10)
        key_value_pairs = {key: value for key, value in zip(keys, keys)}

        assert await redis_client.mset(key_value_pairs) == OK

        # Add the non-existing key
        keys.append(non_existing_key)
        mget_res = await redis_client.mget(keys)
        keys[-1] = None
        assert mget_res == keys

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ping(self, redis_client: TRedisClient):
        assert await redis_client.ping() == "PONG"
        assert await redis_client.ping("HELLO") == "HELLO"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_config_get_set(self, redis_client: TRedisClient):
        previous_timeout = await redis_client.config_get(["timeout"])
        assert await redis_client.config_set({"timeout": "1000"}) == OK
        assert await redis_client.config_get(["timeout"]) == {"timeout": "1000"}
        # revert changes to previous timeout
        assert isinstance(previous_timeout, dict)
        assert isinstance(previous_timeout["timeout"], str)
        assert (
            await redis_client.config_set({"timeout": previous_timeout["timeout"]})
            == OK
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_decr_decrby_existing_key(self, redis_client: TRedisClient):
        key = get_random_string(10)
        assert await redis_client.set(key, "10") == OK
        assert await redis_client.decr(key) == 9
        assert await redis_client.get(key) == "9"
        assert await redis_client.decrby(key, 4) == 5
        assert await redis_client.get(key) == "5"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_decr_decrby_non_existing_key(self, redis_client: TRedisClient):
        key = get_random_string(10)
        key2 = get_random_string(10)

        assert await redis_client.get(key) is None
        assert await redis_client.decr(key) == -1
        assert await redis_client.get(key) == "-1"

        assert await redis_client.get(key2) is None
        assert await redis_client.decrby(key2, 3) == -3
        assert await redis_client.get(key2) == "-3"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_decr_with_str_value(self, redis_client: TRedisClient):
        key = get_random_string(10)
        assert await redis_client.set(key, "foo") == OK
        with pytest.raises(RequestError) as e:
            await redis_client.decr(key)

        assert "value is not an integer" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.decrby(key, 3)

        assert "value is not an integer" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hset_hget_hgetall(self, redis_client: TRedisClient):
        key = get_random_string(10)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await redis_client.hset(key, field_value_map) == 2
        assert await redis_client.hget(key, field) == "value"
        assert await redis_client.hget(key, field2) == "value2"
        assert await redis_client.hget(key, "non_existing_field") is None

        assert await redis_client.hgetall(key) == {field: "value", field2: "value2"}
        assert await redis_client.hgetall("non_existing_field") == {}

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hdel(self, redis_client: TRedisClient):
        key = get_random_string(10)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field3 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2", field3: "value3"}

        assert await redis_client.hset(key, field_value_map) == 3
        assert await redis_client.hdel(key, [field, field2]) == 2
        assert await redis_client.hdel(key, ["nonExistingField"]) == 0
        assert await redis_client.hdel("nonExistingKey", [field3]) == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hsetnx(self, redis_client: TRedisClient):
        key = get_random_string(10)
        field = get_random_string(5)

        assert await redis_client.hsetnx(key, field, "value") == True
        assert await redis_client.hsetnx(key, field, "new value") == False
        assert await redis_client.hget(key, field) == "value"
        key = get_random_string(5)
        assert await redis_client.set(key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.hsetnx(key, field, "value")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hmget(self, redis_client: TRedisClient):
        key = get_random_string(10)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await redis_client.hset(key, field_value_map) == 2
        assert await redis_client.hmget(key, [field, "nonExistingField", field2]) == [
            "value",
            None,
            "value2",
        ]
        assert await redis_client.hmget("nonExistingKey", [field, field2]) == [
            None,
            None,
        ]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hset_without_data(self, redis_client: TRedisClient):
        with pytest.raises(RequestError) as e:
            await redis_client.hset("key", {})

        assert "wrong number of arguments" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hincrby_hincrbyfloat(self, redis_client: TRedisClient):
        key = get_random_string(10)
        field = get_random_string(5)
        field_value_map = {field: "10"}

        assert await redis_client.hset(key, field_value_map) == 1
        assert await redis_client.hincrby(key, field, 1) == 11
        assert await redis_client.hincrby(key, field, 4) == 15
        assert await redis_client.hincrbyfloat(key, field, 1.5) == 16.5

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hincrby_non_existing_key_field(self, redis_client: TRedisClient):
        key = get_random_string(10)
        key2 = get_random_string(10)
        field = get_random_string(5)
        field_value_map = {field: "10"}

        assert await redis_client.hincrby("nonExistingKey", field, 1) == 1
        assert await redis_client.hset(key, field_value_map) == 1
        assert await redis_client.hincrby(key, "nonExistingField", 2) == 2
        assert await redis_client.hset(key2, field_value_map) == 1
        assert await redis_client.hincrbyfloat(key2, "nonExistingField", -0.5) == -0.5

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hincrby_invalid_value(self, redis_client: TRedisClient):
        key = get_random_string(10)
        field = get_random_string(5)
        field_value_map = {field: "value"}

        assert await redis_client.hset(key, field_value_map) == 1

        with pytest.raises(RequestError) as e:
            await redis_client.hincrby(key, field, 2)
        assert "hash value is not an integer" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.hincrbyfloat(key, field, 1.5)
        assert "hash value is not a float" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hexist(self, redis_client: TRedisClient):
        key = get_random_string(10)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await redis_client.hset(key, field_value_map) == 2
        assert await redis_client.hexists(key, field)
        assert not await redis_client.hexists(key, "nonExistingField")
        assert not await redis_client.hexists("nonExistingKey", field2)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hlen(self, redis_client: TRedisClient):
        key = get_random_string(10)
        key2 = get_random_string(5)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await redis_client.hset(key, field_value_map) == 2
        assert await redis_client.hlen(key) == 2
        assert await redis_client.hdel(key, [field]) == 1
        assert await redis_client.hlen(key) == 1
        assert await redis_client.hlen("non_existing_hash") == 0

        assert await redis_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.hlen(key2)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hvals(self, redis_client: TRedisClient):
        key = get_random_string(10)
        key2 = get_random_string(5)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await redis_client.hset(key, field_value_map) == 2
        assert await redis_client.hvals(key) == ["value", "value2"]
        assert await redis_client.hdel(key, [field]) == 1
        assert await redis_client.hvals(key) == ["value2"]
        assert await redis_client.hvals("non_existing_key") == []

        assert await redis_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.hvals(key2)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lpush_lpop_lrange(self, redis_client: TRedisClient):
        key = get_random_string(10)
        value_list = ["value4", "value3", "value2", "value1"]

        assert await redis_client.lpush(key, value_list) == 4
        assert await redis_client.lpop(key) == value_list[-1]
        assert await redis_client.lrange(key, 0, -1) == value_list[-2::-1]
        assert await redis_client.lpop_count(key, 2) == value_list[-2:0:-1]
        assert await redis_client.lrange("non_existing_key", 0, -1) == []
        assert await redis_client.lpop("non_existing_key") is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lpush_lpop_lrange_wrong_type_raise_error(
        self, redis_client: TRedisClient
    ):
        key = get_random_string(10)
        assert await redis_client.set(key, "foo") == OK

        with pytest.raises(RequestError) as e:
            await redis_client.lpush(key, ["bar"])
        assert "Operation against a key holding the wrong kind of value" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.lpop(key)
        assert "Operation against a key holding the wrong kind of value" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.lrange(key, 0, -1)
        assert "Operation against a key holding the wrong kind of value" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lindex(self, redis_client: TRedisClient):
        key = get_random_string(10)
        value_list = [get_random_string(5), get_random_string(5)]
        assert await redis_client.lpush(key, value_list) == 2
        assert await redis_client.lindex(key, 0) == value_list[1]
        assert await redis_client.lindex(key, 1) == value_list[0]
        assert await redis_client.lindex(key, 3) is None
        assert await redis_client.lindex("non_existing_key", 0) is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_rpush_rpop(self, redis_client: TRedisClient):
        key = get_random_string(10)
        value_list = ["value4", "value3", "value2", "value1"]

        assert await redis_client.rpush(key, value_list) == 4
        assert await redis_client.rpop(key) == value_list[-1]

        assert await redis_client.rpop_count(key, 2) == value_list[-2:0:-1]
        assert await redis_client.rpop("non_existing_key") is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_rpush_rpop_wrong_type_raise_error(self, redis_client: TRedisClient):
        key = get_random_string(10)
        assert await redis_client.set(key, "foo") == OK

        with pytest.raises(RequestError) as e:
            await redis_client.rpush(key, ["bar"])
        assert "Operation against a key holding the wrong kind of value" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.rpop(key)
        assert "Operation against a key holding the wrong kind of value" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sadd_srem_smembers_scard(self, redis_client: TRedisClient):
        key = get_random_string(10)
        value_list = ["member1", "member2", "member3", "member4"]

        assert await redis_client.sadd(key, value_list) == 4
        assert await redis_client.srem(key, ["member4", "nonExistingMember"]) == 1

        assert set(await redis_client.smembers(key)) == set(value_list[:3])

        assert await redis_client.srem(key, ["member1"]) == 1
        assert await redis_client.scard(key) == 2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sadd_srem_smembers_scard_non_existing_key(
        self, redis_client: TRedisClient
    ):
        non_existing_key = get_random_string(10)
        assert await redis_client.srem(non_existing_key, ["member"]) == 0
        assert await redis_client.scard(non_existing_key) == 0
        assert await redis_client.smembers(non_existing_key) == set()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sadd_srem_smembers_scard_wrong_type_raise_error(
        self, redis_client: TRedisClient
    ):
        key = get_random_string(10)
        assert await redis_client.set(key, "foo") == OK

        with pytest.raises(RequestError) as e:
            await redis_client.sadd(key, ["bar"])
        assert "Operation against a key holding the wrong kind of value" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.srem(key, ["bar"])
        assert "Operation against a key holding the wrong kind of value" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.scard(key)
        assert "Operation against a key holding the wrong kind of value" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.smembers(key)
        assert "Operation against a key holding the wrong kind of value" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sismember(self, redis_client: TRedisClient):
        key = get_random_string(10)
        member = get_random_string(5)
        assert await redis_client.sadd(key, [member]) == 1
        assert await redis_client.sismember(key, member)
        assert not await redis_client.sismember(key, get_random_string(5))
        assert not await redis_client.sismember("non_existing_key", member)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ltrim(self, redis_client: TRedisClient):
        key = get_random_string(10)
        value_list = ["value4", "value3", "value2", "value1"]

        assert await redis_client.lpush(key, value_list) == 4
        assert await redis_client.ltrim(key, 0, 1) == OK
        assert await redis_client.lrange(key, 0, -1) == ["value1", "value2"]

        assert await redis_client.ltrim(key, 4, 2) == OK
        assert await redis_client.lrange(key, 0, -1) == []

        assert await redis_client.ltrim("non_existing_key", 0, 1) == OK

        assert await redis_client.set(key, "foo") == OK
        with pytest.raises(RequestError) as e:
            await redis_client.ltrim(key, 0, 1)
        assert "Operation against a key holding the wrong kind of value" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lrem(self, redis_client: TRedisClient):
        key = get_random_string(10)
        value_list = ["value1", "value2", "value1", "value1", "value2"]

        assert await redis_client.lpush(key, value_list) == 5

        assert await redis_client.lrem(key, 2, "value1") == 2
        assert await redis_client.lrange(key, 0, -1) == ["value2", "value2", "value1"]

        assert await redis_client.lrem(key, -1, "value2") == 1
        assert await redis_client.lrange(key, 0, -1) == ["value2", "value1"]

        assert await redis_client.lrem(key, 0, "value2") == 1
        assert await redis_client.lrange(key, 0, -1) == ["value1"]

        assert await redis_client.lrem("non_existing_key", 2, "value") == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_llen(self, redis_client: TRedisClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        value_list = ["value4", "value3", "value2", "value1"]

        assert await redis_client.lpush(key1, value_list) == 4
        assert await redis_client.llen(key1) == 4

        assert await redis_client.llen("non_existing_key") == 0

        assert await redis_client.set(key2, "foo") == OK
        with pytest.raises(RequestError) as e:
            await redis_client.llen(key2)
        assert "Operation against a key holding the wrong kind of value" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_exists(self, redis_client: TRedisClient):
        keys = [get_random_string(10), get_random_string(10)]

        assert await redis_client.set(keys[0], "value") == OK
        assert await redis_client.exists(keys) == 1

        assert await redis_client.set(keys[1], "value") == OK
        assert await redis_client.exists(keys) == 2
        keys.append("non_existing_key")
        assert await redis_client.exists(keys) == 2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_unlink(self, redis_client: TRedisClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        key3 = get_random_string(10)

        assert await redis_client.set(key1, "value") == OK
        assert await redis_client.set(key2, "value") == OK
        assert await redis_client.set(key3, "value") == OK
        assert await redis_client.unlink([key1, key2, "non_existing_key", key3]) == 3

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_expire_pexpire_ttl_with_positive_timeout(
        self, redis_client: TRedisClient
    ):
        key = get_random_string(10)
        assert await redis_client.set(key, "foo") == OK

        assert await redis_client.expire(key, 10) == 1
        assert await redis_client.ttl(key) in range(11)

        # set command clears the timeout.
        assert await redis_client.set(key, "bar") == OK
        if await check_if_server_version_lt(redis_client, "7.0.0"):
            assert await redis_client.pexpire(key, 10000)
        else:
            assert await redis_client.pexpire(key, 10000, ExpireOptions.HasNoExpiry)
        assert await redis_client.ttl(key) in range(11)

        if await check_if_server_version_lt(redis_client, "7.0.0"):
            assert await redis_client.expire(key, 15)
        else:
            assert await redis_client.expire(key, 15, ExpireOptions.HasExistingExpiry)
        assert await redis_client.ttl(key) in range(16)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_expireat_pexpireat_ttl_with_positive_timeout(
        self, redis_client: TRedisClient
    ):
        key = get_random_string(10)
        assert await redis_client.set(key, "foo") == OK
        current_time = int(time.time())

        assert await redis_client.expireat(key, current_time + 10) == 1
        assert await redis_client.ttl(key) in range(11)
        if await check_if_server_version_lt(redis_client, "7.0.0"):
            assert await redis_client.expireat(key, current_time + 50) == 1
        else:
            assert (
                await redis_client.expireat(
                    key, current_time + 50, ExpireOptions.NewExpiryGreaterThanCurrent
                )
                == 1
            )
        assert await redis_client.ttl(key) in range(51)

        # set command clears the timeout.
        assert await redis_client.set(key, "bar") == OK
        current_time_ms = int(time.time() * 1000)
        if not await check_if_server_version_lt(redis_client, "7.0.0"):
            assert not await redis_client.pexpireat(
                key, current_time_ms + 50000, ExpireOptions.HasExistingExpiry
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_expire_pexpire_expireat_pexpireat_past_or_negative_timeout(
        self, redis_client: TRedisClient
    ):
        key = get_random_string(10)
        assert await redis_client.set(key, "foo") == OK
        assert await redis_client.ttl(key) == -1

        assert await redis_client.expire(key, -10) == 1
        assert await redis_client.ttl(key) == -2

        assert await redis_client.set(key, "foo") == OK
        assert await redis_client.pexpire(key, -10000)
        assert await redis_client.ttl(key) == -2

        assert await redis_client.set(key, "foo") == OK
        assert await redis_client.expireat(key, int(time.time()) - 50) == 1
        assert await redis_client.ttl(key) == -2

        assert await redis_client.set(key, "foo") == OK
        assert await redis_client.pexpireat(key, int(time.time() * 1000) - 50000)
        assert await redis_client.ttl(key) == -2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_expire_pexpire_expireAt_pexpireAt_ttl_non_existing_key(
        self, redis_client: TRedisClient
    ):
        key = get_random_string(10)

        assert await redis_client.expire(key, 10) == 0
        assert not await redis_client.pexpire(key, 10000)
        assert await redis_client.expireat(key, int(time.time()) + 50) == 0
        assert not await redis_client.pexpireat(key, int(time.time() * 1000) + 50000)
        assert await redis_client.ttl(key) == -2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_pttl(self, redis_client: TRedisClient):
        key = get_random_string(10)
        assert await redis_client.pttl(key) == -2
        current_time = int(time.time())

        assert await redis_client.set(key, "value") == OK
        assert await redis_client.pttl(key) == -1

        assert await redis_client.expire(key, 10)
        assert 0 < await redis_client.pttl(key) <= 10000

        assert await redis_client.expireat(key, current_time + 20)
        assert 0 < await redis_client.pttl(key) <= 20000

        assert await redis_client.pexpireat(key, current_time * 1000 + 30000)
        assert 0 < await redis_client.pttl(key) <= 30000

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_persist(self, redis_client: TRedisClient):
        key = get_random_string(10)
        assert await redis_client.set(key, "value") == OK
        assert not await redis_client.persist(key)

        assert await redis_client.expire(key, 10)
        assert await redis_client.persist(key)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zadd_zaddincr(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"one": 1, "two": 2, "three": 3}
        assert await redis_client.zadd(key, members_scores=members_scores) == 3
        assert await redis_client.zadd_incr(key, member="one", increment=2) == 3.0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zadd_nx_xx(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"one": 1, "two": 2, "three": 3}
        assert (
            await redis_client.zadd(
                key,
                members_scores=members_scores,
                existing_options=ConditionalChange.ONLY_IF_EXISTS,
            )
            == 0
        )
        assert (
            await redis_client.zadd(
                key,
                members_scores=members_scores,
                existing_options=ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
            )
            == 3
        )

        assert (
            await redis_client.zadd_incr(
                key,
                member="one",
                increment=5.0,
                existing_options=ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
            )
            is None
        )

        assert (
            await redis_client.zadd_incr(
                key,
                member="one",
                increment=5.0,
                existing_options=ConditionalChange.ONLY_IF_EXISTS,
            )
            == 6.0
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zadd_gt_lt(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"one": -3, "two": 2, "three": 3}
        assert await redis_client.zadd(key, members_scores=members_scores) == 3
        members_scores["one"] = 10
        assert (
            await redis_client.zadd(
                key,
                members_scores=members_scores,
                update_condition=UpdateOptions.GREATER_THAN,
                changed=True,
            )
            == 1
        )

        assert (
            await redis_client.zadd(
                key,
                members_scores=members_scores,
                update_condition=UpdateOptions.LESS_THAN,
                changed=True,
            )
            == 0
        )

        assert (
            await redis_client.zadd_incr(
                key,
                member="one",
                increment=-3.0,
                update_condition=UpdateOptions.LESS_THAN,
            )
            == 7.0
        )

        assert (
            await redis_client.zadd_incr(
                key,
                member="one",
                increment=-3.0,
                update_condition=UpdateOptions.GREATER_THAN,
            )
            is None
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrem(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"one": 1, "two": 2, "three": 3}
        assert await redis_client.zadd(key, members_scores=members_scores) == 3

        assert await redis_client.zrem(key, ["one"]) == 1
        assert await redis_client.zrem(key, ["one", "two", "three"]) == 2

        assert await redis_client.zrem("non_existing_set", ["member"]) == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zcard(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"one": 1, "two": 2, "three": 3}
        assert await redis_client.zadd(key, members_scores=members_scores) == 3
        assert await redis_client.zcard(key) == 3

        assert await redis_client.zrem(key, ["one"]) == 1
        assert await redis_client.zcard(key) == 2
        assert await redis_client.zcard("non_existing_key") == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zcount(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"one": 1, "two": 2, "three": 3}
        assert await redis_client.zadd(key, members_scores=members_scores) == 3

        assert await redis_client.zcount(key, InfBound.NEG_INF, InfBound.POS_INF) == 3
        assert (
            await redis_client.zcount(
                key,
                ScoreBoundary(1, is_inclusive=False),
                ScoreBoundary(3, is_inclusive=False),
            )
            == 1
        )
        assert (
            await redis_client.zcount(
                key,
                ScoreBoundary(1, is_inclusive=False),
                ScoreBoundary(3, is_inclusive=True),
            )
            == 2
        )
        assert (
            await redis_client.zcount(
                key, InfBound.NEG_INF, ScoreBoundary(3, is_inclusive=True)
            )
            == 3
        )
        assert (
            await redis_client.zcount(
                key, InfBound.POS_INF, ScoreBoundary(3, is_inclusive=True)
            )
            == 0
        )
        assert (
            await redis_client.zcount(
                "non_existing_key", InfBound.NEG_INF, InfBound.POS_INF
            )
            == 0
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zscore(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"one": 1, "two": 2, "three": 3}
        assert await redis_client.zadd(key, members_scores=members_scores) == 3
        assert await redis_client.zscore(key, "one") == 1.0

        assert await redis_client.zscore(key, "non_existing_member") is None
        assert (
            await redis_client.zscore("non_existing_key", "non_existing_member") is None
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zpopmin(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"a": 1.0, "b": 2.0, "c": 3.0}
        assert await redis_client.zadd(key, members_scores=members_scores) == 3
        assert await redis_client.zpopmin(key) == {"a": 1.0}

        assert await redis_client.zpopmin(key, 3) == {"b": 2.0, "c": 3.0}
        assert await redis_client.zpopmin(key) == {}
        assert await redis_client.set(key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zpopmin(key)

        assert await redis_client.zpopmin("non_exisitng_key") == {}

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zpopmax(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"a": 1.0, "b": 2.0, "c": 3.0}
        assert await redis_client.zadd(key, members_scores) == 3
        assert await redis_client.zpopmax(key) == {"c": 3.0}
        assert await redis_client.zpopmax(key, 3) == {"b": 2.0, "a": 1.0}
        assert await redis_client.zpopmax(key) == {}
        assert await redis_client.set(key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zpopmax(key)

        assert await redis_client.zpopmax("non_exisitng_key") == {}

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrange_by_index(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"one": 1, "two": 2, "three": 3}
        assert await redis_client.zadd(key, members_scores=members_scores) == 3

        assert await redis_client.zrange(key, RangeByIndex(start=0, stop=1)) == [
            "one",
            "two",
        ]

        assert (
            await redis_client.zrange_withscores(key, RangeByIndex(start=0, stop=-1))
        ) == {"one": 1.0, "two": 2.0, "three": 3.0}

        assert await redis_client.zrange(
            key, RangeByIndex(start=0, stop=1), reverse=True
        ) == [
            "three",
            "two",
        ]

        assert await redis_client.zrange(key, RangeByIndex(start=3, stop=1)) == []
        assert (
            await redis_client.zrange_withscores(key, RangeByIndex(start=3, stop=1))
            == {}
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrange_byscore(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"one": 1, "two": 2, "three": 3}
        assert await redis_client.zadd(key, members_scores=members_scores) == 3

        assert await redis_client.zrange(
            key,
            RangeByScore(
                start=InfBound.NEG_INF, stop=ScoreBoundary(3, is_inclusive=False)
            ),
        ) == ["one", "two"]

        assert (
            await redis_client.zrange_withscores(
                key,
                RangeByScore(start=InfBound.NEG_INF, stop=InfBound.POS_INF),
            )
        ) == {"one": 1.0, "two": 2.0, "three": 3.0}

        assert await redis_client.zrange(
            key,
            RangeByScore(
                start=ScoreBoundary(3, is_inclusive=False), stop=InfBound.NEG_INF
            ),
            reverse=True,
        ) == ["two", "one"]

        assert (
            await redis_client.zrange(
                key,
                RangeByScore(
                    start=InfBound.NEG_INF,
                    stop=InfBound.POS_INF,
                    limit=Limit(offset=1, count=2),
                ),
            )
        ) == ["two", "three"]

        assert (
            await redis_client.zrange(
                key,
                RangeByScore(
                    start=InfBound.NEG_INF, stop=ScoreBoundary(3, is_inclusive=False)
                ),
                reverse=True,
            )
            == []
        )  # stop is greater than start with reverse set to True

        assert (
            await redis_client.zrange(
                key,
                RangeByScore(
                    start=InfBound.POS_INF, stop=ScoreBoundary(3, is_inclusive=False)
                ),
            )
            == []
        )  # start is greater than stop

        assert (
            await redis_client.zrange_withscores(
                key,
                RangeByScore(
                    start=InfBound.POS_INF, stop=ScoreBoundary(3, is_inclusive=False)
                ),
            )
            == {}
        )  # start is greater than stop

        assert (
            await redis_client.zrange_withscores(
                key,
                RangeByScore(
                    start=InfBound.NEG_INF, stop=ScoreBoundary(3, is_inclusive=False)
                ),
                reverse=True,
            )
            == {}
        )  # stop is greater than start with reverse set to True

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrange_bylex(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"a": 1, "b": 2, "c": 3}
        assert await redis_client.zadd(key, members_scores=members_scores) == 3

        assert await redis_client.zrange(
            key,
            RangeByLex(
                start=InfBound.NEG_INF, stop=LexBoundary("c", is_inclusive=False)
            ),
        ) == ["a", "b"]

        assert (
            await redis_client.zrange(
                key,
                RangeByLex(
                    start=InfBound.NEG_INF,
                    stop=InfBound.POS_INF,
                    limit=Limit(offset=1, count=2),
                ),
            )
        ) == ["b", "c"]

        assert await redis_client.zrange(
            key,
            RangeByLex(
                start=LexBoundary("c", is_inclusive=False), stop=InfBound.NEG_INF
            ),
            reverse=True,
        ) == ["b", "a"]

        assert (
            await redis_client.zrange(
                key,
                RangeByLex(
                    start=InfBound.NEG_INF, stop=LexBoundary("c", is_inclusive=False)
                ),
                reverse=True,
            )
            == []
        )  # stop is greater than start with reverse set to True

        assert (
            await redis_client.zrange(
                key,
                RangeByLex(
                    start=InfBound.POS_INF, stop=LexBoundary("c", is_inclusive=False)
                ),
            )
            == []
        )  # start is greater than stop

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrange_different_types_of_keys(self, redis_client: TRedisClient):
        key = get_random_string(10)

        assert (
            await redis_client.zrange("non_existing_key", RangeByIndex(start=0, stop=1))
            == []
        )

        assert (
            await redis_client.zrange_withscores(
                "non_existing_key", RangeByIndex(start=0, stop=-1)
            )
        ) == {}

        assert await redis_client.set(key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zrange(key, RangeByIndex(start=0, stop=1))

        with pytest.raises(RequestError):
            await redis_client.zrange_withscores(key, RangeByIndex(start=0, stop=1))

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrank(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"one": 1.5, "two": 2, "three": 3}
        assert await redis_client.zadd(key, members_scores) == 3
        assert await redis_client.zrank(key, "one") == 0
        if not await check_if_server_version_lt(redis_client, "7.2.0"):
            assert await redis_client.zrank_withscore(key, "one") == [0, 1.5]
            assert await redis_client.zrank_withscore(key, "non_existing_field") is None
            assert (
                await redis_client.zrank_withscore("non_existing_key", "field") is None
            )

        assert await redis_client.zrank(key, "non_existing_field") is None
        assert await redis_client.zrank("non_existing_key", "field") is None

        assert await redis_client.set(key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zrank(key, "one")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_type(self, redis_client: TRedisClient):
        key = get_random_string(10)
        assert await redis_client.set(key, "value") == OK
        assert (await redis_client.type(key)).lower() == "string"
        assert await redis_client.delete([key]) == 1

        assert await redis_client.lpush(key, ["value"]) == 1
        assert (await redis_client.type(key)).lower() == "list"
        assert await redis_client.delete([key]) == 1

        assert await redis_client.sadd(key, ["value"]) == 1
        assert (await redis_client.type(key)).lower() == "set"
        assert await redis_client.delete([key]) == 1

        assert await redis_client.zadd(key, {"member": 1.0}) == 1
        assert (await redis_client.type(key)).lower() == "zset"
        assert await redis_client.delete([key]) == 1

        assert await redis_client.hset(key, {"field": "value"}) == 1
        assert (await redis_client.type(key)).lower() == "hash"
        assert await redis_client.delete([key]) == 1

        await redis_client.custom_command(["XADD", key, "*", "field", "value"])
        assert await redis_client.type(key) == "stream"
        assert await redis_client.delete([key]) == 1

        assert (await redis_client.type(key)).lower() == "none"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_echo(self, redis_client: TRedisClient):
        message = get_random_string(5)
        assert await redis_client.echo(message) == message


class TestCommandsUnitTests:
    def test_expiry_cmd_args(self):
        exp_sec = ExpirySet(ExpiryType.SEC, 5)
        assert exp_sec.get_cmd_args() == ["EX", "5"]

        exp_sec_timedelta = ExpirySet(ExpiryType.SEC, timedelta(seconds=5))
        assert exp_sec_timedelta.get_cmd_args() == ["EX", "5"]

        exp_millsec = ExpirySet(ExpiryType.MILLSEC, 5)
        assert exp_millsec.get_cmd_args() == ["PX", "5"]

        exp_millsec_timedelta = ExpirySet(ExpiryType.MILLSEC, timedelta(seconds=5))
        assert exp_millsec_timedelta.get_cmd_args() == ["PX", "5000"]

        exp_millsec_timedelta = ExpirySet(ExpiryType.MILLSEC, timedelta(seconds=5))
        assert exp_millsec_timedelta.get_cmd_args() == ["PX", "5000"]

        exp_unix_sec = ExpirySet(ExpiryType.UNIX_SEC, 1682575739)
        assert exp_unix_sec.get_cmd_args() == ["EXAT", "1682575739"]

        exp_unix_sec_datetime = ExpirySet(
            ExpiryType.UNIX_SEC,
            datetime(2023, 4, 27, 23, 55, 59, 342380, timezone.utc),
        )
        assert exp_unix_sec_datetime.get_cmd_args() == ["EXAT", "1682639759"]

        exp_unix_millisec = ExpirySet(ExpiryType.UNIX_MILLSEC, 1682586559964)
        assert exp_unix_millisec.get_cmd_args() == ["PXAT", "1682586559964"]

        exp_unix_millisec_datetime = ExpirySet(
            ExpiryType.UNIX_MILLSEC,
            datetime(2023, 4, 27, 23, 55, 59, 342380, timezone.utc),
        )
        assert exp_unix_millisec_datetime.get_cmd_args() == ["PXAT", "1682639759342"]

    def test_expiry_raises_on_value_error(self):
        with pytest.raises(ValueError):
            ExpirySet(ExpiryType.SEC, 5.5)

    def test_is_single_response(self):
        assert is_single_response("This is a string value", "")
        assert is_single_response(["value", "value"], [""])
        assert not is_single_response(
            [["value", ["value"]], ["value", ["valued"]]], [""]
        )
        assert is_single_response(None, None)


@pytest.mark.asyncio
class TestClusterRoutes:
    async def cluster_route_custom_command_multi_nodes(
        self,
        redis_client: RedisClusterClient,
        route: Route,
    ):
        cluster_nodes = await redis_client.custom_command(["CLUSTER", "NODES"])
        assert isinstance(cluster_nodes, (str, list))
        cluster_nodes = get_first_result(cluster_nodes)
        num_of_nodes = len(cluster_nodes.splitlines())
        expected_num_of_results = (
            num_of_nodes
            if isinstance(route, AllNodes)
            else num_of_nodes - cluster_nodes.count("slave")
        )
        expected_primary_count = cluster_nodes.count("master")
        expected_replica_count = (
            cluster_nodes.count("slave") if isinstance(route, AllNodes) else 0
        )

        all_results = await redis_client.custom_command(["INFO", "REPLICATION"], route)
        assert isinstance(all_results, dict)
        assert len(all_results) == expected_num_of_results
        primary_count = 0
        replica_count = 0
        for _, info_res in all_results.items():
            assert "role:master" in info_res or "role:slave" in info_res
            if "role:master" in info_res:
                primary_count += 1
            else:
                replica_count += 1
        assert primary_count == expected_primary_count
        assert replica_count == expected_replica_count

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_route_custom_command_all_nodes(
        self, redis_client: RedisClusterClient
    ):
        await self.cluster_route_custom_command_multi_nodes(redis_client, AllNodes())

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_route_custom_command_all_primaries(
        self, redis_client: RedisClusterClient
    ):
        await self.cluster_route_custom_command_multi_nodes(
            redis_client, AllPrimaries()
        )

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_route_custom_command_random_node(
        self, redis_client: RedisClusterClient
    ):
        info_res = await redis_client.custom_command(
            ["INFO", "REPLICATION"], RandomNode()
        )
        assert type(info_res) is str
        assert "role:master" in info_res or "role:slave" in info_res

    async def cluster_route_custom_command_slot_route(
        self, redis_client: RedisClusterClient, is_slot_key: bool
    ):
        route_class = SlotKeyRoute if is_slot_key else SlotIdRoute
        route_second_arg = "foo" if is_slot_key else 4000
        primary_res = await redis_client.custom_command(
            ["CLUSTER", "NODES"], route_class(SlotType.PRIMARY, route_second_arg)
        )
        assert type(primary_res) is str
        assert "myself,master" in primary_res
        expected_primary_node_id = ""
        for node_line in primary_res.splitlines():
            if "myself" in node_line:
                expected_primary_node_id = node_line.split(" ")[0]

        replica_res = await redis_client.custom_command(
            ["CLUSTER", "NODES"], route_class(SlotType.REPLICA, route_second_arg)
        )
        assert isinstance(replica_res, str)
        assert "myself,slave" in replica_res
        for node_line in replica_res:
            if "myself" in node_line:
                primary_node_id = node_line.split(" ")[3]
                assert primary_node_id == expected_primary_node_id

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_route_custom_command_slot_key_route(
        self, redis_client: RedisClusterClient
    ):
        await self.cluster_route_custom_command_slot_route(redis_client, True)

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_route_custom_command_slot_id_route(
        self, redis_client: RedisClusterClient
    ):
        await self.cluster_route_custom_command_slot_route(redis_client, False)

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_info_random_route(self, redis_client: RedisClusterClient):
        info = await redis_client.info([InfoSection.SERVER], RandomNode())
        assert isinstance(info, str)
        assert "# Server" in info

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_route_by_address_reaches_correct_node(
        self, redis_client: RedisClusterClient
    ):
        # returns the line that contains the word "myself", up to that point. This is done because the values after it might change with time.
        clean_result = lambda value: [
            line for line in value.split("\n") if "myself" in line
        ][0]

        cluster_nodes = clean_result(
            await redis_client.custom_command(["cluster", "nodes"], RandomNode())
        )
        assert isinstance(cluster_nodes, str)
        host = cluster_nodes.split(" ")[1].split("@")[0]

        second_result = clean_result(
            await redis_client.custom_command(
                ["cluster", "nodes"], ByAddressRoute(host)
            )
        )

        assert cluster_nodes == second_result

        host, port = host.split(":")
        port_as_int = int(port)

        third_result = clean_result(
            await redis_client.custom_command(
                ["cluster", "nodes"], ByAddressRoute(host, port_as_int)
            )
        )

        assert cluster_nodes == third_result

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_fail_routing_by_address_if_no_port_is_provided(
        self, redis_client: RedisClusterClient
    ):
        with pytest.raises(RequestError) as e:
            await redis_client.info(route=ByAddressRoute("foo"))


@pytest.mark.asyncio
class TestExceptions:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_timeout_exception_with_blpop(self, redis_client: TRedisClient):
        key = get_random_string(10)
        with pytest.raises(TimeoutError):
            await redis_client.custom_command(["BLPOP", key, "1"])


@pytest.mark.asyncio
class TestScripts:
    @pytest.mark.smoke_test
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_script(self, redis_client: TRedisClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        script = Script("return 'Hello'")
        assert await redis_client.invoke_script(script) == "Hello"

        script = Script("return redis.call('SET', KEYS[1], ARGV[1])")
        assert (
            await redis_client.invoke_script(script, keys=[key1], args=["value1"])
            == "OK"
        )
        # Reuse the same script with different parameters.
        assert (
            await redis_client.invoke_script(script, keys=[key2], args=["value2"])
            == "OK"
        )
        script = Script("return redis.call('GET', KEYS[1])")
        assert await redis_client.invoke_script(script, keys=[key1]) == "value1"
        assert await redis_client.invoke_script(script, keys=[key2]) == "value2"
