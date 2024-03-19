# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

from datetime import datetime, timezone
from typing import List, Union

import pytest
from glide import RequestError
from glide.async_commands.sorted_set import InfBound, RangeByIndex, ScoreBoundary
from glide.async_commands.transaction import (
    BaseTransaction,
    ClusterTransaction,
    Transaction,
)
from glide.config import ProtocolVersion
from glide.constants import OK, TResult
from glide.redis_client import RedisClient, RedisClusterClient, TRedisClient
from tests.conftest import create_client
from tests.test_async_client import check_if_server_version_lt, get_random_string


async def transaction_test(
    transaction: Union[Transaction, ClusterTransaction],
    keyslot: str,
    redis_client: TRedisClient,
) -> List[TResult]:
    key = "{{{}}}:{}".format(keyslot, get_random_string(3))  # to get the same slot
    key2 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # to get the same slot
    key3 = "{{{}}}:{}".format(keyslot, get_random_string(3))
    key4 = "{{{}}}:{}".format(keyslot, get_random_string(3))
    key5 = "{{{}}}:{}".format(keyslot, get_random_string(3))
    key6 = "{{{}}}:{}".format(keyslot, get_random_string(3))
    key7 = "{{{}}}:{}".format(keyslot, get_random_string(3))
    key8 = "{{{}}}:{}".format(keyslot, get_random_string(3))

    value = datetime.now(timezone.utc).strftime("%m/%d/%Y, %H:%M:%S")
    value2 = get_random_string(5)
    args: List[TResult] = []

    transaction.set(key, value)
    args.append(OK)
    transaction.get(key)
    args.append(value)
    transaction.type(key)
    args.append("string")
    transaction.echo(value)
    args.append(value)

    transaction.persist(key)
    args.append(False)

    transaction.exists([key])
    args.append(1)

    transaction.delete([key])
    args.append(1)
    transaction.get(key)
    args.append(None)

    transaction.mset({key: value, key2: value2})
    args.append(OK)
    transaction.mget([key, key2])
    args.append([value, value2])

    transaction.incr(key3)
    args.append(1)
    transaction.incrby(key3, 2)
    args.append(3)

    transaction.decr(key3)
    args.append(2)
    transaction.decrby(key3, 2)
    args.append(0)

    transaction.incrbyfloat(key3, 0.5)
    args.append(0.5)

    transaction.unlink([key3])
    args.append(1)

    transaction.ping()
    args.append("PONG")

    transaction.config_set({"timeout": "1000"})
    args.append(OK)
    transaction.config_get(["timeout"])
    args.append({"timeout": "1000"})

    transaction.hset(key4, {key: value, key2: value2})
    args.append(2)
    transaction.hget(key4, key2)
    args.append(value2)
    transaction.hlen(key4)
    args.append(2)
    transaction.hvals(key4)
    args.append([value, value2])
    transaction.hsetnx(key4, key, value)
    args.append(False)
    transaction.hincrby(key4, key3, 5)
    args.append(5)
    transaction.hincrbyfloat(key4, key3, 5.5)
    args.append(10.5)

    transaction.hexists(key4, key)
    args.append(True)
    transaction.hmget(key4, [key, "nonExistingField", key2])
    args.append([value, None, value2])
    transaction.hgetall(key4)
    args.append({key: value, key2: value2, key3: "10.5"})
    transaction.hdel(key4, [key, key2])
    args.append(2)

    transaction.client_getname()
    args.append(None)

    transaction.lpush(key5, [value, value, value2, value2])
    args.append(4)
    transaction.llen(key5)
    args.append(4)
    transaction.lindex(key5, 0)
    args.append(value2)
    transaction.lpop(key5)
    args.append(value2)
    transaction.lrem(key5, 1, value)
    args.append(1)
    transaction.ltrim(key5, 0, 1)
    args.append(OK)
    transaction.lrange(key5, 0, -1)
    args.append([value2, value])
    transaction.lpop_count(key5, 2)
    args.append([value2, value])

    transaction.rpush(key6, [value, value2, value2])
    args.append(3)
    transaction.rpop(key6)
    args.append(value2)
    transaction.rpop_count(key6, 2)
    args.append([value2, value])

    transaction.sadd(key7, ["foo", "bar"])
    args.append(2)
    transaction.srem(key7, ["foo"])
    args.append(1)
    transaction.smembers(key7)
    args.append({"bar"})
    transaction.scard(key7)
    args.append(1)
    transaction.sismember(key7, "bar")
    args.append(True)

    transaction.zadd(key8, {"one": 1, "two": 2, "three": 3})
    args.append(3)
    transaction.zrank(key8, "one")
    args.append(0)
    if not await check_if_server_version_lt(redis_client, "7.2.0"):
        transaction.zrank_withscore(key8, "one")
        args.append([0, 1])
    transaction.zadd_incr(key8, "one", 3)
    args.append(4)
    transaction.zrem(key8, ["one"])
    args.append(1)
    transaction.zcard(key8)
    args.append(2)
    transaction.zcount(key8, ScoreBoundary(2, is_inclusive=True), InfBound.POS_INF)
    args.append(2)
    transaction.zscore(key8, "two")
    args.append(2.0)
    transaction.zrange(key8, RangeByIndex(start=0, stop=-1))
    args.append(["two", "three"])
    transaction.zrange_withscores(key8, RangeByIndex(start=0, stop=-1))
    args.append({"two": 2, "three": 3})
    transaction.zpopmin(key8)
    args.append({"two": 2.0})
    transaction.zpopmax(key8)
    args.append({"three": 3})
    return args


@pytest.mark.asyncio
class TestTransaction:
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_with_different_slots(self, redis_client: TRedisClient):
        transaction = (
            Transaction()
            if isinstance(redis_client, RedisClient)
            else ClusterTransaction()
        )
        transaction.set("key1", "value1")
        transaction.set("key2", "value2")
        with pytest.raises(RequestError, match="CrossSlot"):
            await redis_client.exec(transaction)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_custom_command(self, redis_client: TRedisClient):
        key = get_random_string(10)
        transaction = (
            Transaction()
            if isinstance(redis_client, RedisClient)
            else ClusterTransaction()
        )
        transaction.custom_command(["HSET", key, "foo", "bar"])
        transaction.custom_command(["HGET", key, "foo"])
        result = await redis_client.exec(transaction)
        assert result == [1, "bar"]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_custom_unsupported_command(
        self, redis_client: TRedisClient
    ):
        key = get_random_string(10)
        transaction = (
            Transaction()
            if isinstance(redis_client, RedisClient)
            else ClusterTransaction()
        )
        transaction.custom_command(["WATCH", key])
        with pytest.raises(RequestError) as e:
            await redis_client.exec(transaction)
        assert "WATCH inside MULTI is not allowed" in str(
            e
        )  # TODO : add an assert on EXEC ABORT

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_discard_command(self, redis_client: TRedisClient):
        key = get_random_string(10)
        await redis_client.set(key, "1")
        transaction = (
            Transaction()
            if isinstance(redis_client, RedisClient)
            else ClusterTransaction()
        )

        transaction.custom_command(["INCR", key])
        transaction.custom_command(["DISCARD"])
        with pytest.raises(RequestError) as e:
            await redis_client.exec(transaction)
        assert "EXEC without MULTI" in str(e)  # TODO : add an assert on EXEC ABORT
        value = await redis_client.get(key)
        assert value == "1"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_exec_abort(self, redis_client: TRedisClient):
        key = get_random_string(10)
        transaction = BaseTransaction()
        transaction.custom_command(["INCR", key, key, key])
        with pytest.raises(RequestError) as e:
            await redis_client.exec(transaction)
        assert "wrong number of arguments" in str(
            e
        )  # TODO : add an assert on EXEC ABORT

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_transaction(self, redis_client: RedisClusterClient):
        keyslot = get_random_string(3)
        transaction = ClusterTransaction()
        transaction.info()
        expected = await transaction_test(transaction, keyslot, redis_client)
        result = await redis_client.exec(transaction)
        assert isinstance(result, list)
        assert isinstance(result[0], str)
        assert "# Memory" in result[0]
        assert result[1:] == expected

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_can_return_null_on_watch_transaction_failures(
        self, redis_client: TRedisClient, request
    ):
        is_cluster = isinstance(redis_client, RedisClusterClient)
        client2 = await create_client(
            request,
            is_cluster,
        )
        keyslot = get_random_string(3)
        transaction = ClusterTransaction() if is_cluster else Transaction()
        transaction.get(keyslot)
        result1 = await redis_client.custom_command(["WATCH", keyslot])
        assert result1 == OK

        result2 = await client2.set(keyslot, "foo")
        assert result2 == OK

        result3 = await redis_client.exec(transaction)
        assert result3 is None

        await client2.close()

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_standalone_transaction(self, redis_client: RedisClient):
        keyslot = get_random_string(3)
        key = "{{{}}}:{}".format(keyslot, get_random_string(3))  # to get the same slot
        value = get_random_string(5)
        transaction = Transaction()
        transaction.info()
        transaction.select(1)
        transaction.set(key, value)
        transaction.get(key)
        transaction.select(0)
        transaction.get(key)
        expected = await transaction_test(transaction, keyslot, redis_client)
        result = await redis_client.exec(transaction)
        assert isinstance(result, list)
        assert isinstance(result[0], str)
        assert "# Memory" in result[0]
        assert result[1:6] == [OK, OK, value, OK, None]
        assert result[6:] == expected

    def test_transaction_clear(self):
        transaction = Transaction()
        transaction.info()
        transaction.select(1)
        transaction.clear()
        assert len(transaction.commands) == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_chaining_calls(self, redis_client: TRedisClient):
        cluster_mode = isinstance(redis_client, RedisClusterClient)
        key = get_random_string(3)

        transaction = ClusterTransaction() if cluster_mode else Transaction()
        transaction.set(key, "value").get(key).delete([key])

        assert await redis_client.exec(transaction) == [OK, "value", 1]
