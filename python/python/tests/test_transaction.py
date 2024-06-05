# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

import time
from datetime import date, datetime, timedelta, timezone
from typing import List, Union, cast

import pytest
from glide import RequestError
from glide.async_commands.command_args import Limit, ListDirection, OrderBy
from glide.async_commands.core import (
    GeospatialData,
    InsertPosition,
    StreamAddOptions,
    TrimByMinId,
)
from glide.async_commands.sorted_set import (
    AggregationType,
    InfBound,
    LexBoundary,
    RangeByIndex,
    ScoreBoundary,
    ScoreFilter,
)
from glide.async_commands.transaction import (
    BaseTransaction,
    ClusterTransaction,
    Transaction,
)
from glide.config import ProtocolVersion
from glide.constants import OK, TResult
from glide.redis_client import RedisClient, RedisClusterClient, TRedisClient
from tests.conftest import create_client
from tests.utils.utils import check_if_server_version_lt, get_random_string


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
    key9 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # list
    key10 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # hyper log log
    key11 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # streams
    key12 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # geo
    key13 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # sorted set
    key14 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # sorted set
    key15 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # sorted set
    key16 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # sorted set
    key17 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # sort
    key18 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # sort

    value = datetime.now(timezone.utc).strftime("%m/%d/%Y, %H:%M:%S")
    value2 = get_random_string(5)
    value3 = get_random_string(5)
    args: List[TResult] = []

    transaction.dbsize()
    args.append(0)

    transaction.set(key, value)
    args.append(OK)
    transaction.setrange(key, 0, value)
    args.append(len(value))
    transaction.get(key)
    args.append(value)
    transaction.type(key)
    args.append("string")
    transaction.echo(value)
    args.append(value)
    transaction.strlen(key)
    args.append(len(value))
    transaction.append(key, value)
    args.append(len(value) * 2)

    transaction.persist(key)
    args.append(False)

    transaction.rename(key, key2)
    args.append(OK)

    transaction.exists([key2])
    args.append(1)

    transaction.delete([key2])
    args.append(1)
    transaction.get(key2)
    args.append(None)

    transaction.set(key, value)
    args.append(OK)
    transaction.getdel(key)
    args.append(value)
    transaction.getdel(key)
    args.append(None)

    transaction.mset({key: value, key2: value2})
    args.append(OK)
    transaction.mget([key, key2])
    args.append([value, value2])

    transaction.renamenx(key, key2)
    args.append(False)

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
    transaction.hkeys(key4)
    args.append([key, key2])
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
    transaction.hrandfield(key4)
    args.append(key3)
    transaction.hrandfield_count(key4, 1)
    args.append([key3])
    transaction.hrandfield_withvalues(key4, 1)
    args.append([[key3, "10.5"]])

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
    transaction.lmove(key5, key6, ListDirection.LEFT, ListDirection.LEFT)
    args.append(value2)
    transaction.blmove(key6, key5, ListDirection.LEFT, ListDirection.LEFT, 1)
    args.append(value2)
    transaction.lpop_count(key5, 2)
    args.append([value2, value])
    transaction.linsert(key5, InsertPosition.BEFORE, "non_existing_pivot", "element")
    args.append(0)
    if not await check_if_server_version_lt(redis_client, "7.0.0"):
        transaction.lpush(key5, [value, value2])
        args.append(2)
        transaction.lmpop([key5], ListDirection.LEFT)
        args.append({key5: [value2]})
        transaction.blmpop([key5], ListDirection.LEFT, 0.1)
        args.append({key5: [value]})

    transaction.rpush(key6, [value, value2, value2])
    args.append(3)
    transaction.rpop(key6)
    args.append(value2)
    transaction.rpop_count(key6, 2)
    args.append([value2, value])

    transaction.rpushx(key9, ["_"])
    args.append(0)
    transaction.lpushx(key9, ["_"])
    args.append(0)
    transaction.lpush(key9, [value, value2, value3])
    args.append(3)
    transaction.blpop([key9], 1)
    args.append([key9, value3])
    transaction.brpop([key9], 1)
    args.append([key9, value])

    transaction.sadd(key7, ["foo", "bar"])
    args.append(2)
    transaction.smismember(key7, ["foo", "baz"])
    args.append([True, False])
    transaction.sdiffstore(key7, [key7])
    args.append(2)
    transaction.srem(key7, ["foo"])
    args.append(1)
    transaction.smembers(key7)
    args.append({"bar"})
    transaction.scard(key7)
    args.append(1)
    transaction.sismember(key7, "bar")
    args.append(True)
    transaction.spop(key7)
    args.append("bar")
    transaction.sadd(key7, ["foo", "bar"])
    args.append(2)
    transaction.sunionstore(key7, [key7, key7])
    args.append(2)
    transaction.sinter([key7, key7])
    args.append({"foo", "bar"})
    transaction.sinterstore(key7, [key7, key7])
    args.append(2)
    if not await check_if_server_version_lt(redis_client, "7.0.0"):
        transaction.sintercard([key7, key7])
        args.append(2)
        transaction.sintercard([key7, key7], 1)
        args.append(1)
    transaction.sdiff([key7, key7])
    args.append(set())
    transaction.spop_count(key7, 4)
    args.append({"foo", "bar"})
    transaction.smove(key7, key7, "non_existing_member")
    args.append(False)

    transaction.zadd(key8, {"one": 1, "two": 2, "three": 3, "four": 4})
    args.append(4)
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
    args.append(3)
    transaction.zcount(key8, ScoreBoundary(2, is_inclusive=True), InfBound.POS_INF)
    args.append(3)
    transaction.zlexcount(key8, LexBoundary("a", is_inclusive=True), InfBound.POS_INF)
    args.append(3)
    transaction.zscore(key8, "two")
    args.append(2.0)
    transaction.zrange(key8, RangeByIndex(start=0, stop=-1))
    args.append(["two", "three", "four"])
    transaction.zrange_withscores(key8, RangeByIndex(start=0, stop=-1))
    args.append({"two": 2, "three": 3, "four": 4})
    transaction.zmscore(key8, ["two", "three"])
    args.append([2.0, 3.0])
    transaction.zrangestore(key8, key8, RangeByIndex(0, -1))
    args.append(3)
    transaction.bzpopmin([key8], 0.5)
    args.append([key8, "two", 2.0])
    transaction.bzpopmax([key8], 0.5)
    args.append([key8, "four", 4.0])
    # key8 now only contains one member ("three")
    transaction.zrandmember(key8)
    args.append("three")
    transaction.zrandmember_count(key8, 1)
    args.append(["three"])
    transaction.zrandmember_withscores(key8, 1)
    args.append([["three", 3.0]])
    transaction.zpopmax(key8)
    args.append({"three": 3.0})
    transaction.zpopmin(key8)
    args.append({})
    transaction.zremrangebyscore(key8, InfBound.NEG_INF, InfBound.POS_INF)
    args.append(0)
    transaction.zremrangebylex(key8, InfBound.NEG_INF, InfBound.POS_INF)
    args.append(0)
    transaction.zremrangebyrank(key8, 0, 10)
    args.append(0)
    transaction.zdiffstore(key8, [key8, key8])
    args.append(0)
    if not await check_if_server_version_lt(redis_client, "7.0.0"):
        transaction.zmpop([key8], ScoreFilter.MAX)
        args.append(None)
        transaction.zmpop([key8], ScoreFilter.MAX, 1)
        args.append(None)

    transaction.zadd(key13, {"one": 1.0, "two": 2.0})
    args.append(2)
    transaction.zdiff([key13, key8])
    args.append(["one", "two"])
    transaction.zdiff_withscores([key13, key8])
    args.append({"one": 1.0, "two": 2.0})
    if not await check_if_server_version_lt(redis_client, "7.0.0"):
        transaction.zintercard([key13, key8])
        args.append(0)
        transaction.zintercard([key13, key8], 1)
        args.append(0)

    transaction.zadd(key14, {"one": 1, "two": 2})
    args.append(2)
    transaction.zadd(key15, {"one": 1.0, "two": 2.0, "three": 3.5})
    args.append(3)
    transaction.zinter([key14, key15])
    args.append(["one", "two"])
    transaction.zinter_withscores([key14, key15])
    args.append({"one": 2.0, "two": 4.0})
    transaction.zinterstore(key8, [key14, key15])
    args.append(2)
    transaction.zunion([key14, key15])
    args.append(["one", "three", "two"])
    transaction.zunion_withscores([key14, key15])
    args.append({"one": 2.0, "two": 4.0, "three": 3.5})
    transaction.zunionstore(key8, [key14, key15], AggregationType.MAX)
    args.append(3)

    transaction.pfadd(key10, ["a", "b", "c"])
    args.append(1)
    transaction.pfmerge(key10, [])
    args.append(OK)
    transaction.pfcount([key10])
    args.append(3)

    transaction.geoadd(
        key12,
        {
            "Palermo": GeospatialData(13.361389, 38.115556),
            "Catania": GeospatialData(15.087269, 37.502669),
        },
    )
    args.append(2)
    transaction.geodist(key12, "Palermo", "Catania")
    args.append(166274.1516)
    transaction.geohash(key12, ["Palermo", "Catania", "Place"])
    args.append(["sqc8b49rny0", "sqdtr74hyu0", None])
    transaction.geopos(key12, ["Palermo", "Catania", "Place"])
    # The comparison allows for a small tolerance level due to potential precision errors in floating-point calculations
    # No worries, Python can handle it, therefore, this shouldn't fail
    args.append(
        [
            [13.36138933897018433, 38.11555639549629859],
            [15.08726745843887329, 37.50266842333162032],
            None,
        ]
    )

    transaction.xadd(key11, [("foo", "bar")], StreamAddOptions(id="0-1"))
    args.append("0-1")
    transaction.xadd(key11, [("foo", "bar")], StreamAddOptions(id="0-2"))
    args.append("0-2")
    transaction.xlen(key11)
    args.append(2)
    transaction.xtrim(key11, TrimByMinId(threshold="0-2", exact=True))
    args.append(1)

    transaction.lpush(key17, ["2", "1", "4", "3", "a"])
    args.append(5)
    transaction.sort(
        key17,
        limit=Limit(1, 4),
        order=OrderBy.ASC,
        alpha=True,
    )
    args.append(["2", "3", "4", "a"])
    transaction.sort_ro(
        key17,
        limit=Limit(1, 4),
        order=OrderBy.ASC,
        alpha=True,
    )
    args.append(["2", "3", "4", "a"])
    transaction.sort_store(
        key17,
        key18,
        limit=Limit(1, 4),
        order=OrderBy.ASC,
        alpha=True,
    )
    args.append(4)

    min_version = "7.0.0"
    if not await check_if_server_version_lt(redis_client, min_version):
        transaction.zadd(key16, {"a": 1, "b": 2, "c": 3, "d": 4})
        args.append(4)
        transaction.bzmpop([key16], ScoreFilter.MAX, 0.1)
        args.append([key16, {"d": 4.0}])
        transaction.bzmpop([key16], ScoreFilter.MIN, 0.1, 2)
        args.append([key16, {"a": 1.0, "b": 2.0}])

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
        assert await redis_client.custom_command(["FLUSHALL"]) == OK
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
        assert await redis_client.custom_command(["FLUSHALL"]) == OK
        keyslot = get_random_string(3)
        key = "{{{}}}:{}".format(keyslot, get_random_string(3))  # to get the same slot
        key1 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # to get the same slot
        value = get_random_string(5)
        transaction = Transaction()
        transaction.info()
        transaction.select(1)
        transaction.set(key, value)
        transaction.get(key)
        transaction.hset("user:1", {"name": "Alice", "age": "30"})
        transaction.hset("user:2", {"name": "Bob", "age": "25"})
        transaction.lpush(key1, ["2", "1"])
        transaction.sort(
            key1,
            by_pattern="user:*->age",
            get_patterns=["user:*->name"],
            order=OrderBy.ASC,
            alpha=True,
        )
        transaction.sort_ro(
            key1,
            by_pattern="user:*->age",
            get_patterns=["user:*->name"],
            order=OrderBy.ASC,
            alpha=True,
        )
        transaction.sort_store(
            key1,
            "newSortedKey",
            by_pattern="user:*->age",
            get_patterns=["user:*->name"],
            order=OrderBy.ASC,
            alpha=True,
        )
        transaction.select(0)
        transaction.get(key)
        expected = await transaction_test(transaction, keyslot, redis_client)
        result = await redis_client.exec(transaction)
        assert isinstance(result, list)
        assert isinstance(result[0], str)
        assert "# Memory" in result[0]
        assert result[1:4] == [OK, OK, value]
        assert result[4:12] == [
            2,
            2,
            2,
            ["Bob", "Alice"],
            ["Bob", "Alice"],
            2,
            OK,
            None,
        ]
        assert result[12:] == expected

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

    # The object commands are tested here instead of transaction_test because they have special requirements:
    # - OBJECT FREQ and OBJECT IDLETIME require specific maxmemory policies to be set on the config
    # - we cannot reliably predict the exact response values for OBJECT FREQ, OBJECT IDLETIME, and OBJECT REFCOUNT
    # - OBJECT ENCODING is tested here since all the other OBJECT commands are tested here
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_object_commands(
        self, redis_client: TRedisClient, cluster_mode: bool
    ):
        string_key = get_random_string(10)
        maxmemory_policy_key = "maxmemory-policy"
        config = await redis_client.config_get([maxmemory_policy_key])
        maxmemory_policy = cast(str, config.get(maxmemory_policy_key))

        try:
            transaction = ClusterTransaction() if cluster_mode else Transaction()
            transaction.set(string_key, "foo")
            transaction.object_encoding(string_key)
            transaction.object_refcount(string_key)
            # OBJECT FREQ requires a LFU maxmemory-policy
            transaction.config_set({maxmemory_policy_key: "allkeys-lfu"})
            transaction.object_freq(string_key)
            # OBJECT IDLETIME requires a non-LFU maxmemory-policy
            transaction.config_set({maxmemory_policy_key: "allkeys-random"})
            transaction.object_idletime(string_key)

            response = await redis_client.exec(transaction)
            assert response is not None
            assert response[0] == OK  # transaction.set(string_key, "foo")
            assert response[1] == "embstr"  # transaction.object_encoding(string_key)
            # transaction.object_refcount(string_key)
            assert cast(int, response[2]) >= 0
            # transaction.config_set({maxmemory_policy_key: "allkeys-lfu"})
            assert response[3] == OK
            assert cast(int, response[4]) >= 0  # transaction.object_freq(string_key)
            # transaction.config_set({maxmemory_policy_key: "allkeys-random"})
            assert response[5] == OK
            # transaction.object_idletime(string_key)
            assert cast(int, response[6]) >= 0
        finally:
            await redis_client.config_set({maxmemory_policy_key: maxmemory_policy})

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_lastsave(
        self, redis_client: TRedisClient, cluster_mode: bool
    ):
        yesterday = date.today() - timedelta(1)
        yesterday_unix_time = time.mktime(yesterday.timetuple())
        transaction = ClusterTransaction() if cluster_mode else Transaction()
        transaction.lastsave()
        response = await redis_client.exec(transaction)
        assert isinstance(response, list)
        lastsave_time = response[0]
        assert isinstance(lastsave_time, int)
        assert lastsave_time > yesterday_unix_time
