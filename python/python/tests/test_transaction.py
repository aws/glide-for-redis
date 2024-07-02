# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import time
from datetime import date, datetime, timedelta, timezone
from typing import List, Union, cast

import pytest
from glide import RequestError
from glide.async_commands.bitmap import (
    BitFieldGet,
    BitFieldSet,
    BitmapIndexType,
    BitOffset,
    BitOffsetMultiplier,
    BitwiseOperation,
    OffsetOptions,
    SignedEncoding,
    UnsignedEncoding,
)
from glide.async_commands.command_args import Limit, ListDirection, OrderBy
from glide.async_commands.core import (
    ExpiryGetEx,
    ExpiryTypeGetEx,
    FlushMode,
    InsertPosition,
)
from glide.async_commands.sorted_set import (
    AggregationType,
    GeoSearchByBox,
    GeoSearchByRadius,
    GeospatialData,
    GeoUnit,
    InfBound,
    LexBoundary,
    OrderBy,
    RangeByIndex,
    ScoreBoundary,
    ScoreFilter,
)
from glide.async_commands.stream import (
    IdBound,
    MaxId,
    MinId,
    StreamAddOptions,
    StreamGroupOptions,
    StreamReadGroupOptions,
    TrimByMinId,
)
from glide.async_commands.transaction import (
    BaseTransaction,
    ClusterTransaction,
    Transaction,
)
from glide.config import ProtocolVersion
from glide.constants import OK, TResult
from glide.glide_client import GlideClient, GlideClusterClient, TGlideClient
from tests.conftest import create_client
from tests.utils.utils import (
    check_if_server_version_lt,
    convert_bytes_to_string_object,
    generate_lua_lib_code,
    get_random_string,
)


async def transaction_test(
    transaction: Union[Transaction, ClusterTransaction],
    keyslot: str,
    redis_client: TGlideClient,
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
    key19 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # bitmap
    key20 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # bitmap
    key22 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # getex
    key23 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # string
    key24 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # string
    key25 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # list

    value = datetime.now(timezone.utc).strftime("%m/%d/%Y, %H:%M:%S")
    value_bytes = value.encode()
    value2 = get_random_string(5)
    value2_bytes = value2.encode()
    value3 = get_random_string(5)
    value3_bytes = value3.encode()
    lib_name = f"mylib1C{get_random_string(5)}"
    func_name = f"myfunc1c{get_random_string(5)}"
    code = generate_lua_lib_code(lib_name, {func_name: "return args[1]"}, True)
    args: List[TResult] = []

    if not await check_if_server_version_lt(redis_client, "7.0.0"):
        transaction.function_load(code)
        args.append(lib_name.encode())
        transaction.function_load(code, True)
        args.append(lib_name.encode())
        transaction.fcall(func_name, [], arguments=["one", "two"])
        args.append(b"one")
        transaction.fcall(func_name, [key], arguments=["one", "two"])
        args.append(b"one")
        transaction.fcall_ro(func_name, [], arguments=["one", "two"])
        args.append(b"one")
        transaction.fcall_ro(func_name, [key], arguments=["one", "two"])
        args.append(b"one")
        transaction.function_delete(lib_name)
        args.append(OK)
        transaction.function_flush()
        args.append(OK)
        transaction.function_flush(FlushMode.ASYNC)
        args.append(OK)
        transaction.function_flush(FlushMode.SYNC)
        args.append(OK)

    transaction.dbsize()
    args.append(0)

    transaction.set(key, value)
    args.append(OK)
    transaction.setrange(key, 0, value)
    args.append(len(value))
    transaction.get(key)
    args.append(value_bytes)
    transaction.type(key)
    args.append(b"string")
    transaction.echo(value)
    args.append(value_bytes)
    transaction.strlen(key)
    args.append(len(value))
    transaction.append(key, value)
    args.append(len(value) * 2)

    transaction.persist(key)
    args.append(False)
    transaction.ttl(key)
    args.append(-1)
    if not await check_if_server_version_lt(redis_client, "7.0.0"):
        transaction.expiretime(key)
        args.append(-1)
        transaction.pexpiretime(key)
        args.append(-1)

    if not await check_if_server_version_lt(redis_client, "6.2.0"):
        transaction.copy(key, key2, replace=True)
        args.append(True)

    transaction.rename(key, key2)
    args.append(OK)

    transaction.exists([key2])
    args.append(1)
    transaction.touch([key2])
    args.append(1)

    transaction.delete([key2])
    args.append(1)
    transaction.get(key2)
    args.append(None)

    transaction.set(key, value)
    args.append(OK)
    transaction.getrange(key, 0, -1)
    args.append(value_bytes)
    transaction.getdel(key)
    args.append(value_bytes)
    transaction.getdel(key)
    args.append(None)

    transaction.mset({key: value, key2: value2})
    args.append(OK)
    transaction.msetnx({key: value, key2: value2})
    args.append(False)
    transaction.mget([key, key2])
    args.append([value_bytes, value2_bytes])

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
    args.append(b"PONG")

    transaction.config_set({"timeout": "1000"})
    args.append(OK)
    transaction.config_get(["timeout"])
    args.append({b"timeout": b"1000"})

    transaction.hset(key4, {key: value, key2: value2})
    args.append(2)
    transaction.hget(key4, key2)
    args.append(value2_bytes)
    transaction.hlen(key4)
    args.append(2)
    transaction.hvals(key4)
    args.append([value_bytes, value2_bytes])
    transaction.hkeys(key4)
    args.append([key.encode(), key2.encode()])
    transaction.hsetnx(key4, key, value)
    args.append(False)
    transaction.hincrby(key4, key3, 5)
    args.append(5)
    transaction.hincrbyfloat(key4, key3, 5.5)
    args.append(10.5)

    transaction.hexists(key4, key)
    args.append(True)
    transaction.hmget(key4, [key, "nonExistingField", key2])
    args.append([value_bytes, None, value2_bytes])
    transaction.hgetall(key4)
    key3_bytes = key3.encode()
    args.append(
        {
            key.encode(): value_bytes,
            key2.encode(): value2_bytes,
            key3_bytes: b"10.5",
        }
    )
    transaction.hdel(key4, [key, key2])
    args.append(2)
    transaction.hscan(key4, "0")
    args.append([b"0", [key3.encode(), b"10.5"]])
    transaction.hscan(key4, "0", match="*", count=10)
    args.append([b"0", [key3.encode(), b"10.5"]])
    transaction.hrandfield(key4)
    args.append(key3_bytes)
    transaction.hrandfield_count(key4, 1)
    args.append([key3_bytes])
    transaction.hrandfield_withvalues(key4, 1)
    args.append([[key3_bytes, b"10.5"]])
    transaction.hstrlen(key4, key3)
    args.append(4)

    transaction.client_getname()
    args.append(None)

    transaction.lpush(key5, [value, value, value2, value2])
    args.append(4)
    transaction.llen(key5)
    args.append(4)
    transaction.lindex(key5, 0)
    args.append(value2_bytes)
    transaction.lpop(key5)
    args.append(value2_bytes)
    transaction.lrem(key5, 1, value)
    args.append(1)
    transaction.ltrim(key5, 0, 1)
    args.append(OK)
    transaction.lrange(key5, 0, -1)
    args.append([value2_bytes, value_bytes])
    transaction.lmove(key5, key6, ListDirection.LEFT, ListDirection.LEFT)
    args.append(value2_bytes)
    transaction.blmove(key6, key5, ListDirection.LEFT, ListDirection.LEFT, 1)
    args.append(value2_bytes)
    transaction.lpop_count(key5, 2)
    args.append([value2_bytes, value_bytes])
    transaction.linsert(key5, InsertPosition.BEFORE, "non_existing_pivot", "element")
    args.append(0)
    if not await check_if_server_version_lt(redis_client, "7.0.0"):
        transaction.lpush(key5, [value, value2])
        args.append(2)
        transaction.lmpop([key5], ListDirection.LEFT)
        args.append({key5.encode(): [value2_bytes]})
        transaction.blmpop([key5], ListDirection.LEFT, 0.1)
        args.append({key5.encode(): [value_bytes]})

    transaction.rpush(key6, [value, value2, value2])
    args.append(3)
    transaction.rpop(key6)
    args.append(value2_bytes)
    transaction.rpop_count(key6, 2)
    args.append([value2_bytes, value_bytes])

    transaction.rpushx(key9, ["_"])
    args.append(0)
    transaction.lpushx(key9, ["_"])
    args.append(0)
    transaction.lpush(key9, [value, value2, value3])
    args.append(3)
    transaction.blpop([key9], 1)
    args.append([key9.encode(), value3_bytes])
    transaction.brpop([key9], 1)
    args.append([key9.encode(), value_bytes])
    transaction.lset(key9, 0, value2)
    args.append(OK)

    transaction.sadd(key7, ["foo", "bar"])
    args.append(2)
    transaction.smismember(key7, ["foo", "baz"])
    args.append([True, False])
    transaction.sdiffstore(key7, [key7])
    args.append(2)
    transaction.srem(key7, ["foo"])
    args.append(1)
    transaction.sscan(key7, "0")
    args.append([b"0", [b"bar"]])
    transaction.sscan(key7, "0", match="*", count=10)
    args.append([b"0", [b"bar"]])
    transaction.smembers(key7)
    args.append({b"bar"})
    transaction.scard(key7)
    args.append(1)
    transaction.sismember(key7, "bar")
    args.append(True)
    transaction.spop(key7)
    args.append(b"bar")
    transaction.sadd(key7, ["foo", "bar"])
    args.append(2)
    transaction.sunionstore(key7, [key7, key7])
    args.append(2)
    transaction.sinter([key7, key7])
    args.append({b"foo", b"bar"})
    transaction.sunion([key7, key7])
    args.append({b"foo", b"bar"})
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
    args.append({b"foo", b"bar"})
    transaction.smove(key7, key7, "non_existing_member")
    args.append(False)

    transaction.zadd(key8, {"one": 1, "two": 2, "three": 3, "four": 4})
    args.append(4)
    transaction.zrank(key8, "one")
    args.append(0)
    transaction.zrevrank(key8, "one")
    args.append(3)
    if not await check_if_server_version_lt(redis_client, "7.2.0"):
        transaction.zrank_withscore(key8, "one")
        args.append([0, 1])
        transaction.zrevrank_withscore(key8, "one")
        args.append([3, 1])
    transaction.zadd_incr(key8, "one", 3)
    args.append(4)
    transaction.zincrby(key8, 3, "one")
    args.append(7)
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
    args.append([b"two", b"three", b"four"])
    transaction.zrange_withscores(key8, RangeByIndex(start=0, stop=-1))
    args.append({b"two": 2, b"three": 3, b"four": 4})
    transaction.zmscore(key8, ["two", "three"])
    args.append([2.0, 3.0])
    transaction.zrangestore(key8, key8, RangeByIndex(0, -1))
    args.append(3)
    transaction.bzpopmin([key8], 0.5)
    args.append([key8.encode(), b"two", 2.0])
    transaction.bzpopmax([key8], 0.5)
    args.append([key8.encode(), b"four", 4.0])
    # key8 now only contains one member ("three")
    transaction.zrandmember(key8)
    args.append(b"three")
    transaction.zrandmember_count(key8, 1)
    args.append([b"three"])
    transaction.zrandmember_withscores(key8, 1)
    args.append([[b"three", 3.0]])
    transaction.zscan(key8, "0")
    args.append([b"0", [b"three", b"3"]])
    transaction.zscan(key8, "0", match="*", count=20)
    args.append([b"0", [b"three", b"3"]])
    transaction.zpopmax(key8)
    args.append({b"three": 3.0})
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
    args.append([b"one", b"two"])
    transaction.zdiff_withscores([key13, key8])
    args.append({b"one": 1.0, b"two": 2.0})
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
    args.append([b"one", b"two"])
    transaction.zinter_withscores([key14, key15])
    args.append({b"one": 2.0, b"two": 4.0})
    transaction.zinterstore(key8, [key14, key15])
    args.append(2)
    transaction.zunion([key14, key15])
    args.append([b"one", b"three", b"two"])
    transaction.zunion_withscores([key14, key15])
    args.append({b"one": 2.0, b"two": 4.0, b"three": 3.5})
    transaction.zunionstore(key8, [key14, key15], AggregationType.MAX)
    args.append(3)

    transaction.pfadd(key10, ["a", "b", "c"])
    args.append(1)
    transaction.pfmerge(key10, [])
    args.append(OK)
    transaction.pfcount([key10])
    args.append(3)

    transaction.setbit(key19, 1, 1)
    args.append(0)
    transaction.getbit(key19, 1)
    args.append(1)

    transaction.set(key20, "foobar")
    args.append(OK)
    transaction.bitcount(key20)
    args.append(26)
    transaction.bitcount(key20, OffsetOptions(1, 1))
    args.append(6)
    transaction.bitpos(key20, 1)
    args.append(1)

    if not await check_if_server_version_lt(redis_client, "6.0.0"):
        transaction.bitfield_read_only(
            key20, [BitFieldGet(SignedEncoding(5), BitOffset(3))]
        )
        args.append([6])

    transaction.set(key19, "abcdef")
    args.append(OK)
    transaction.bitop(BitwiseOperation.AND, key19, [key19, key20])
    args.append(6)
    transaction.get(key19)
    args.append(b"`bc`ab")
    transaction.bitfield(
        key20, [BitFieldSet(UnsignedEncoding(10), BitOffsetMultiplier(3), 4)]
    )
    args.append([609])

    if not await check_if_server_version_lt(redis_client, "7.0.0"):
        transaction.set(key20, "foobar")
        args.append(OK)
        transaction.bitcount(key20, OffsetOptions(5, 30, BitmapIndexType.BIT))
        args.append(17)
        transaction.bitpos_interval(key20, 1, 44, 50, BitmapIndexType.BIT)
        args.append(46)

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
    args.append([b"sqc8b49rny0", b"sqdtr74hyu0", None])
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

    transaction.geosearch(
        key12, "Catania", GeoSearchByRadius(200, GeoUnit.KILOMETERS), OrderBy.ASC
    )
    args.append([b"Catania", b"Palermo"])
    transaction.geosearchstore(
        key12,
        key12,
        GeospatialData(15, 37),
        GeoSearchByBox(400, 400, GeoUnit.KILOMETERS),
        store_dist=True,
    )
    args.append(2)

    transaction.xadd(key11, [("foo", "bar")], StreamAddOptions(id="0-1"))
    args.append(b"0-1")
    transaction.xadd(key11, [("foo", "bar")], StreamAddOptions(id="0-2"))
    args.append(b"0-2")
    transaction.xlen(key11)
    args.append(2)
    transaction.xread({key11: "0-1"})
    args.append({key11.encode(): {b"0-2": [[b"foo", b"bar"]]}})
    transaction.xrange(key11, IdBound("0-1"), IdBound("0-1"))
    args.append({b"0-1": [[b"foo", b"bar"]]})
    transaction.xrevrange(key11, IdBound("0-1"), IdBound("0-1"))
    args.append({b"0-1": [[b"foo", b"bar"]]})
    transaction.xtrim(key11, TrimByMinId(threshold="0-2", exact=True))
    args.append(1)

    group_name1 = get_random_string(10)
    group_name2 = get_random_string(10)
    consumer = get_random_string(10)
    transaction.xgroup_create(key11, group_name1, "0-1")
    args.append(OK)
    transaction.xgroup_create(
        key11, group_name2, "0-0", StreamGroupOptions(make_stream=True)
    )
    args.append(OK)
    transaction.xgroup_create_consumer(key11, group_name1, consumer)
    args.append(True)
    transaction.xreadgroup(
        {key11: ">"}, group_name1, consumer, StreamReadGroupOptions(count=5)
    )
    args.append({key11.encode(): {b"0-2": [[b"foo", b"bar"]]}})
    transaction.xpending(key11, group_name1)
    args.append([1, b"0-2", b"0-2", [[consumer.encode(), b"1"]]])

    min_version = "6.2.0"
    if not await check_if_server_version_lt(redis_client, min_version):
        transaction.xautoclaim(key11, group_name1, consumer, 0, "0-0")
        transaction.xautoclaim_just_id(key11, group_name1, consumer, 0, "0-0")
        # if using Redis 7.0.0 or above, responses also include a list of entry IDs that were removed from the Pending
        # Entries List because they no longer exist in the stream
        if await check_if_server_version_lt(redis_client, "7.0.0"):
            args.append(
                [b"0-0", {b"0-2": [[b"foo", b"bar"]]}]
            )  # transaction.xautoclaim(key11, group_name1, consumer, 0, "0-0")
            args.append(
                [b"0-0", [b"0-2"]]
            )  # transaction.xautoclaim_just_id(key11, group_name1, consumer, 0, "0-0")
        else:
            args.append(
                [b"0-0", {b"0-2": [[b"foo", b"bar"]]}, []]
            )  # transaction.xautoclaim(key11, group_name1, consumer, 0, "0-0")
            args.append(
                [b"0-0", [b"0-2"], []]
            )  # transaction.xautoclaim_just_id(key11, group_name1, consumer, 0, "0-0")

    transaction.xack(key11, group_name1, ["0-2"])
    args.append(1)
    transaction.xpending_range(key11, group_name1, MinId(), MaxId(), 1)
    args.append([])
    transaction.xgroup_set_id(key11, group_name1, "0-2")
    args.append(OK)
    transaction.xgroup_del_consumer(key11, group_name1, consumer)
    args.append(0)
    transaction.xgroup_destroy(key11, group_name1)
    args.append(True)
    transaction.xgroup_destroy(key11, group_name2)
    args.append(True)

    transaction.xdel(key11, ["0-2", "0-3"])
    args.append(1)

    transaction.lpush(key17, ["2", "1", "4", "3", "a"])
    args.append(5)
    transaction.sort(
        key17,
        limit=Limit(1, 4),
        order=OrderBy.ASC,
        alpha=True,
    )
    args.append([b"2", b"3", b"4", b"a"])
    transaction.sort_store(
        key17,
        key18,
        limit=Limit(1, 4),
        order=OrderBy.ASC,
        alpha=True,
    )
    args.append(4)
    transaction.sadd(key7, ["one"])
    args.append(1)
    transaction.srandmember(key7)
    args.append(b"one")
    transaction.srandmember_count(key7, 1)
    args.append([b"one"])
    transaction.wait(1, 1000)
    args.append(0)
    transaction.flushall(FlushMode.ASYNC)
    args.append(OK)
    transaction.flushall()
    args.append(OK)
    transaction.flushdb(FlushMode.ASYNC)
    args.append(OK)
    transaction.flushdb()
    args.append(OK)
    transaction.set(key, "foo")
    args.append(OK)
    transaction.random_key()
    args.append(key.encode())

    min_version = "6.0.6"
    if not await check_if_server_version_lt(redis_client, min_version):
        transaction.rpush(key25, ["a", "a", "b", "c", "a", "b"])
        args.append(6)
        transaction.lpos(key25, "a")
        args.append(0)
        transaction.lpos(key25, "a", 1, 0, 0)
        args.append([0, 1, 4])

    min_version = "6.2.0"
    if not await check_if_server_version_lt(redis_client, min_version):
        transaction.flushall(FlushMode.SYNC)
        args.append(OK)
        transaction.flushdb(FlushMode.SYNC)
        args.append(OK)

    min_version = "6.2.0"
    if not await check_if_server_version_lt(redis_client, min_version):
        transaction.set(key22, "value")
        args.append(OK)
        transaction.getex(key22)
        args.append(b"value")
        transaction.getex(key22, ExpiryGetEx(ExpiryTypeGetEx.SEC, 1))
        args.append(b"value")

    min_version = "7.0.0"
    if not await check_if_server_version_lt(redis_client, min_version):
        transaction.zadd(key16, {"a": 1, "b": 2, "c": 3, "d": 4})
        args.append(4)
        transaction.bzmpop([key16], ScoreFilter.MAX, 0.1)
        args.append([key16.encode(), {b"d": 4.0}])
        transaction.bzmpop([key16], ScoreFilter.MIN, 0.1, 2)
        args.append([key16.encode(), {b"a": 1.0, b"b": 2.0}])

        transaction.mset({key23: "abcd1234", key24: "bcdef1234"})
        args.append(OK)
        transaction.lcs(key23, key24)
        args.append(b"bcd1234")
        transaction.lcs_len(key23, key24)
        args.append(7)
        transaction.lcs_idx(key23, key24)
        args.append({b"matches": [[[4, 7], [5, 8]], [[1, 3], [0, 2]]], b"len": 7})
        transaction.lcs_idx(key23, key24, min_match_len=4)
        args.append({b"matches": [[[4, 7], [5, 8]]], b"len": 7})
        transaction.lcs_idx(key23, key24, with_match_len=True)
        args.append({b"matches": [[[4, 7], [5, 8], 4], [[1, 3], [0, 2], 3]], b"len": 7})

    return args


@pytest.mark.asyncio
class TestTransaction:
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_with_different_slots(self, redis_client: TGlideClient):
        transaction = (
            Transaction()
            if isinstance(redis_client, GlideClient)
            else ClusterTransaction()
        )
        transaction.set("key1", "value1")
        transaction.set("key2", "value2")
        with pytest.raises(RequestError, match="CrossSlot"):
            await redis_client.exec(transaction)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_custom_command(self, redis_client: TGlideClient):
        key = get_random_string(10)
        transaction = (
            Transaction()
            if isinstance(redis_client, GlideClient)
            else ClusterTransaction()
        )
        transaction.custom_command(["HSET", key, "foo", "bar"])
        transaction.custom_command(["HGET", key, "foo"])
        result = await redis_client.exec(transaction)
        assert result == [1, b"bar"]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_custom_unsupported_command(
        self, redis_client: TGlideClient
    ):
        key = get_random_string(10)
        transaction = (
            Transaction()
            if isinstance(redis_client, GlideClient)
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
    async def test_transaction_discard_command(self, redis_client: TGlideClient):
        key = get_random_string(10)
        await redis_client.set(key, "1")
        transaction = (
            Transaction()
            if isinstance(redis_client, GlideClient)
            else ClusterTransaction()
        )

        transaction.custom_command(["INCR", key])
        transaction.custom_command(["DISCARD"])
        with pytest.raises(RequestError) as e:
            await redis_client.exec(transaction)
        assert "EXEC without MULTI" in str(e)  # TODO : add an assert on EXEC ABORT
        value = await redis_client.get(key)
        assert value == b"1"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_exec_abort(self, redis_client: TGlideClient):
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
    async def test_cluster_transaction(self, redis_client: GlideClusterClient):
        assert await redis_client.custom_command(["FLUSHALL"]) == OK
        keyslot = get_random_string(3)
        transaction = ClusterTransaction()
        transaction.info()
        if await check_if_server_version_lt(redis_client, "7.0.0"):
            transaction.publish("test_message", keyslot, False)
        else:
            transaction.publish("test_message", keyslot, True)
        expected = await transaction_test(transaction, keyslot, redis_client)
        result = await redis_client.exec(transaction)
        assert isinstance(result, list)
        assert isinstance(result[0], bytes)
        result[0] = result[0].decode()
        assert isinstance(result[0], str)
        # Making sure the "info" command is indeed a return at position 0
        assert "# Memory" in result[0]
        assert result[1] == 0
        assert result[2:] == expected

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_can_return_null_on_watch_transaction_failures(
        self, redis_client: TGlideClient, request
    ):
        is_cluster = isinstance(redis_client, GlideClusterClient)
        client2 = await create_client(
            request,
            is_cluster,
        )
        keyslot = get_random_string(3)
        transaction = ClusterTransaction() if is_cluster else Transaction()
        transaction.get(keyslot)
        result1 = await redis_client.watch([keyslot])
        assert result1 == OK

        result2 = await client2.set(keyslot, "foo")
        assert result2 == OK

        result3 = await redis_client.exec(transaction)
        assert result3 is None

        await client2.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_large_values(self, request, cluster_mode, protocol):
        redis_client = await create_client(
            request, cluster_mode=cluster_mode, protocol=protocol, timeout=5000
        )
        length = 2**25  # 33mb
        key = "0" * length
        value = "0" * length
        transaction = Transaction()
        transaction.set(key, value)
        transaction.get(key)
        result = await redis_client.exec(transaction)
        assert isinstance(result, list)
        assert result[0] == OK
        assert result[1] == value.encode()

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_standalone_transaction(self, redis_client: GlideClient):
        assert await redis_client.custom_command(["FLUSHALL"]) == OK
        keyslot = get_random_string(3)
        key = "{{{}}}:{}".format(keyslot, get_random_string(3))  # to get the same slot
        key1 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # to get the same slot
        value = get_random_string(5)
        transaction = Transaction()
        transaction.info()
        transaction.select(1)
        transaction.move(key, 0)
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
        transaction.publish("test_message", "test_channel")
        expected = await transaction_test(transaction, keyslot, redis_client)
        result = await redis_client.exec(transaction)
        assert isinstance(result, list)
        assert isinstance(result[0], bytes)
        result[0] = result[0].decode()
        assert isinstance(result[0], str)
        assert "# Memory" in result[0]
        assert result[1:5] == [OK, False, OK, value.encode()]
        assert result[5:13] == [2, 2, 2, [b"Bob", b"Alice"], 2, OK, None, 0]
        assert result[13:] == expected

    def test_transaction_clear(self):
        transaction = Transaction()
        transaction.info()
        transaction.select(1)
        transaction.clear()
        assert len(transaction.commands) == 0

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_standalone_copy_transaction(self, redis_client: GlideClient):
        min_version = "6.2.0"
        if await check_if_server_version_lt(redis_client, min_version):
            return pytest.mark.skip(reason=f"Redis version required >= {min_version}")

        keyslot = get_random_string(3)
        key = "{{{}}}:{}".format(keyslot, get_random_string(3))  # to get the same slot
        key1 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # to get the same slot
        value = get_random_string(5)
        transaction = Transaction()
        transaction.select(1)
        transaction.set(key, value)
        transaction.copy(key, key1, 1, replace=True)
        transaction.get(key1)
        result = await redis_client.exec(transaction)
        assert result is not None
        assert result[2] == True
        assert result[3] == value.encode()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_chaining_calls(self, redis_client: TGlideClient):
        cluster_mode = isinstance(redis_client, GlideClusterClient)
        key = get_random_string(3)

        transaction = ClusterTransaction() if cluster_mode else Transaction()
        transaction.set(key, "value").get(key).delete([key])

        assert await redis_client.exec(transaction) == [OK, b"value", 1]

    # The object commands are tested here instead of transaction_test because they have special requirements:
    # - OBJECT FREQ and OBJECT IDLETIME require specific maxmemory policies to be set on the config
    # - we cannot reliably predict the exact response values for OBJECT FREQ, OBJECT IDLETIME, and OBJECT REFCOUNT
    # - OBJECT ENCODING is tested here since all the other OBJECT commands are tested here
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_object_commands(
        self, redis_client: TGlideClient, cluster_mode: bool
    ):
        string_key = get_random_string(10)
        maxmemory_policy_key = "maxmemory-policy"
        config = await redis_client.config_get([maxmemory_policy_key])
        config_decoded = cast(dict, convert_bytes_to_string_object(config))
        assert config_decoded is not None
        maxmemory_policy = cast(str, config_decoded.get(maxmemory_policy_key))

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
            assert response[1] == b"embstr"  # transaction.object_encoding(string_key)
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
        self, redis_client: TGlideClient, cluster_mode: bool
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

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lolwut_transaction(self, redis_client: GlideClusterClient):
        transaction = Transaction()
        transaction.lolwut().lolwut(5).lolwut(parameters=[1, 2]).lolwut(6, [42])
        results = await redis_client.exec(transaction)
        assert results is not None

        for element in results:
            assert isinstance(element, bytes)
            assert b"Redis ver. " in element
