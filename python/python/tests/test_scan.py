from __future__ import annotations

from typing import cast

import pytest
from glide import ClusterScanCursor
from glide.async_commands.command_args import ObjectType
from glide.config import ProtocolVersion
from glide.exceptions import RequestError
from glide.glide_client import GlideClient, GlideClusterClient
from tests.utils.utils import get_random_string


@pytest.mark.asyncio
class TestScan:
    # Cluster scan tests
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_scan_simple(self, redis_client: GlideClusterClient):
        key = get_random_string(10)
        expected_keys = [f"{key}:{i}" for i in range(100)]
        await redis_client.mset({k: "value" for k in expected_keys})
        expected_keys_encoded = map(lambda k: k.encode(), expected_keys)
        cursor = ClusterScanCursor()
        keys: list[str] = []
        while not cursor.is_finished():
            result = await redis_client.scan(cursor)
            cursor = cast(ClusterScanCursor, result[0])
            result_keys = cast(list[str], result[1])
            keys.extend(result_keys)

        assert set(expected_keys_encoded) == set(keys)

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_scan_with_object_type_and_pattern(
        self, redis_client: GlideClusterClient
    ):
        key = get_random_string(10)
        expected_keys = [f"key:{key}:{i}" for i in range(100)]
        await redis_client.mset({k: "value" for k in expected_keys})
        encoded_expected_keys = map(lambda k: k.encode(), expected_keys)
        unexpected_type_keys = [f"{key}:{i}" for i in range(100, 200)]
        for key in unexpected_type_keys:
            await redis_client.sadd(key, ["value"])
        encoded_unexpected_type_keys = map(lambda k: k.encode(), unexpected_type_keys)
        unexpected_pattern_keys = [f"{i}" for i in range(200, 300)]
        await redis_client.mset({k: "value" for k in unexpected_pattern_keys})
        encoded_unexpected_pattern_keys = map(
            lambda k: k.encode(), unexpected_pattern_keys
        )
        keys: list[str] = []
        cursor = ClusterScanCursor()
        while not cursor.is_finished():
            result = await redis_client.scan(
                cursor, match=b"key:*", type=ObjectType.STRING
            )
            cursor = cast(ClusterScanCursor, result[0])
            result_keys = cast(list[str], result[1])
            keys.extend(result_keys)

        assert set(encoded_expected_keys) == set(keys)
        assert not set(encoded_unexpected_type_keys).intersection(set(keys))
        assert not set(encoded_unexpected_pattern_keys).intersection(set(keys))

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_scan_with_count(self, redis_client: GlideClusterClient):
        key = get_random_string(10)
        expected_keys = [f"{key}:{i}" for i in range(100)]
        await redis_client.mset({k: "value" for k in expected_keys})
        encoded_expected_keys = map(lambda k: k.encode(), expected_keys)
        cursor = ClusterScanCursor()
        keys: list[str] = []
        successful_compared_scans = 0
        while not cursor.is_finished():
            result_of_1 = await redis_client.scan(cursor, count=1)
            cursor = cast(ClusterScanCursor, result_of_1[0])
            result_keys_of_1 = cast(list[str], result_of_1[1])
            keys.extend(result_keys_of_1)
            if cursor.is_finished():
                break
            result_of_100 = await redis_client.scan(cursor, count=100)
            cursor = cast(ClusterScanCursor, result_of_100[0])
            result_keys_of_100 = cast(list[str], result_of_100[1])
            keys.extend(result_keys_of_100)
            if len(result_keys_of_100) > len(result_keys_of_1):
                successful_compared_scans += 1

        assert set(encoded_expected_keys) == set(keys)
        assert successful_compared_scans > 0

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_scan_with_match(self, redis_client: GlideClusterClient):
        unexpected_keys = [f"{i}" for i in range(100)]
        await redis_client.mset({k: "value" for k in unexpected_keys})
        encoded_unexpected_keys = map(lambda k: k.encode(), unexpected_keys)
        key = get_random_string(10)
        expected_keys = [f"key:{key}:{i}" for i in range(100)]
        await redis_client.mset({k: "value" for k in expected_keys})
        encoded_expected_keys = map(lambda k: k.encode(), expected_keys)
        cursor = ClusterScanCursor()
        keys: list[str] = []
        while not cursor.is_finished():
            result = await redis_client.scan(cursor, match="key:*")
            cursor = cast(ClusterScanCursor, result[0])
            result_keys = cast(list[str], result[1])
            keys.extend(result_keys)
        assert set(encoded_expected_keys) == set(keys)
        assert not set(encoded_unexpected_keys).intersection(set(keys))

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    # We test whether the cursor is cleaned up after it is deleted, which we expect to happen when th GC is called
    async def test_cluster_scan_cleaning_cursor(self, redis_client: GlideClusterClient):
        key = get_random_string(10)
        await redis_client.mset(
            {k: "value" for k in [f"{key}:{i}" for i in range(100)]}
        )
        cursor = cast(
            ClusterScanCursor, (await redis_client.scan(ClusterScanCursor()))[0]
        )
        cursor_string = cursor.get_cursor()
        print(cursor_string)
        del cursor
        new_cursor_with_same_id = ClusterScanCursor(cursor_string)
        with pytest.raises(RequestError) as e_info:
            await redis_client.scan(new_cursor_with_same_id)
            print(new_cursor_with_same_id)
            print(new_cursor_with_same_id.get_cursor())
        assert "Invalid scan_state_cursor id" in str(e_info.value)

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_scan_all_types(self, redis_client: GlideClusterClient):
        # We test that the scan command work for all types of keys
        key = get_random_string(10)
        string_keys = [f"{key}:{i}" for i in range(100)]
        await redis_client.mset({k: "value" for k in string_keys})
        encoded_string_keys = list(map(lambda k: k.encode(), string_keys))

        set_key = get_random_string(10)
        set_keys = [f"{set_key}:{i}" for i in range(100, 200)]
        for key in set_keys:
            await redis_client.sadd(key, ["value"])
        encoded_set_keys = list(map(lambda k: k.encode(), set_keys))

        hash_key = get_random_string(10)
        hash_keys = [f"{hash_key}:{i}" for i in range(200, 300)]
        for key in hash_keys:
            await redis_client.hset(key, {"field": "value"})
        encoded_hash_keys = list(map(lambda k: k.encode(), hash_keys))

        list_key = get_random_string(10)
        list_keys = [f"{list_key}:{i}" for i in range(300, 400)]
        for key in list_keys:
            await redis_client.lpush(key, ["value"])
        encoded_list_keys = list(map(lambda k: k.encode(), list_keys))

        zset_key = get_random_string(10)
        zset_keys = [f"{zset_key}:{i}" for i in range(400, 500)]
        for key in zset_keys:
            await redis_client.zadd(key, {"value": 1})
        encoded_zset_keys = list(map(lambda k: k.encode(), zset_keys))

        stream_key = get_random_string(10)
        stream_keys = [f"{stream_key}:{i}" for i in range(500, 600)]
        for key in stream_keys:
            await redis_client.xadd(key, [("field", "value")])
        encoded_stream_keys = list(map(lambda k: k.encode(), stream_keys))

        cursor = ClusterScanCursor()
        keys: list[bytes] = []
        while not cursor.is_finished():
            result = await redis_client.scan(cursor, type=ObjectType.STRING)
            cursor = cast(ClusterScanCursor, result[0])
            result_keys = result[1]
            keys.extend(cast(list[bytes], result_keys))
        assert set(encoded_string_keys) == set(keys)
        assert not set(encoded_set_keys).intersection(set(keys))
        assert not set(encoded_hash_keys).intersection(set(keys))
        assert not set(encoded_list_keys).intersection(set(keys))
        assert not set(encoded_zset_keys).intersection(set(keys))
        assert not set(encoded_stream_keys).intersection(set(keys))

        cursor = ClusterScanCursor()
        keys.clear()
        while not cursor.is_finished():
            result = await redis_client.scan(cursor, type=ObjectType.SET)
            cursor = cast(ClusterScanCursor, result[0])
            result_keys = result[1]
            keys.extend(cast(list[bytes], result_keys))
        assert set(encoded_set_keys) == set(keys)
        assert not set(encoded_string_keys).intersection(set(keys))
        assert not set(encoded_hash_keys).intersection(set(keys))
        assert not set(encoded_zset_keys).intersection(set(keys))
        assert not set(encoded_list_keys).intersection(set(keys))
        assert not set(encoded_stream_keys).intersection(set(keys))

        cursor = ClusterScanCursor()
        keys.clear()
        while not cursor.is_finished():
            result = await redis_client.scan(cursor, type=ObjectType.HASH)
            cursor = cast(ClusterScanCursor, result[0])
            result_keys = result[1]
            keys.extend(cast(list[bytes], result_keys))
        assert set(encoded_hash_keys) == set(keys)
        assert not set(encoded_string_keys).intersection(set(keys))
        assert not set(encoded_set_keys).intersection(set(keys))
        assert not set(encoded_zset_keys).intersection(set(keys))
        assert not set(encoded_list_keys).intersection(set(keys))
        assert not set(encoded_stream_keys).intersection(set(keys))

        cursor = ClusterScanCursor()
        keys.clear()
        while not cursor.is_finished():
            result = await redis_client.scan(cursor, type=ObjectType.LIST)
            cursor = cast(ClusterScanCursor, result[0])
            result_keys = result[1]
            keys.extend(cast(list[bytes], result_keys))
        assert set(encoded_list_keys) == set(keys)
        assert not set(encoded_string_keys).intersection(set(keys))
        assert not set(encoded_set_keys).intersection(set(keys))
        assert not set(encoded_hash_keys).intersection(set(keys))
        assert not set(encoded_zset_keys).intersection(set(keys))
        assert not set(encoded_stream_keys).intersection(set(keys))

        cursor = ClusterScanCursor()
        keys.clear()
        while not cursor.is_finished():
            result = await redis_client.scan(cursor, type=ObjectType.ZSET)
            cursor = cast(ClusterScanCursor, result[0])
            result_keys = result[1]
            keys.extend(cast(list[bytes], result_keys))
        assert set(encoded_zset_keys) == set(keys)
        assert not set(encoded_string_keys).intersection(set(keys))
        assert not set(encoded_set_keys).intersection(set(keys))
        assert not set(encoded_hash_keys).intersection(set(keys))
        assert not set(encoded_list_keys).intersection(set(keys))
        assert not set(encoded_stream_keys).intersection(set(keys))

        cursor = ClusterScanCursor()
        keys.clear()
        while not cursor.is_finished():
            result = await redis_client.scan(cursor, type=ObjectType.STREAM)
            cursor = cast(ClusterScanCursor, result[0])
            result_keys = result[1]
            keys.extend(cast(list[bytes], result_keys))
        assert set(encoded_stream_keys) == set(keys)
        assert not set(encoded_string_keys).intersection(set(keys))
        assert not set(encoded_set_keys).intersection(set(keys))
        assert not set(encoded_hash_keys).intersection(set(keys))
        assert not set(encoded_list_keys).intersection(set(keys))
        assert not set(encoded_zset_keys).intersection(set(keys))

    # Standalone scan tests
    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_standalone_scan_simple(self, redis_client: GlideClient):
        key = get_random_string(10)
        expected_keys = [f"{key}:{i}" for i in range(100)]
        await redis_client.mset({k: "value" for k in expected_keys})
        encoded_expected_keys = map(lambda k: k.encode(), expected_keys)
        keys: list[str] = []
        cursor = b"0"
        while True:
            result = await redis_client.scan(cursor)
            cursor_bytes = cast(bytes, result[0])
            cursor = cursor_bytes
            new_keys = cast(list[str], result[1])
            keys.extend(new_keys)
            if cursor == b"0":
                break
        assert set(encoded_expected_keys) == set(keys)

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_standalone_scan_with_object_type_and_pattern(
        self, redis_client: GlideClient
    ):
        key = get_random_string(10)
        expected_keys = [f"key:{key}:{i}" for i in range(100)]
        await redis_client.mset({k: "value" for k in expected_keys})
        unexpected_type_keys = [f"key:{i}" for i in range(100, 200)]
        for key in unexpected_type_keys:
            await redis_client.sadd(key, ["value"])
        unexpected_pattern_keys = [f"{i}" for i in range(200, 300)]
        for key in unexpected_pattern_keys:
            await redis_client.set(key, "value")
        keys: list[str] = []
        cursor = b"0"
        while True:
            result = await redis_client.scan(
                cursor, match=b"key:*", type=ObjectType.STRING
            )
            cursor = cast(bytes, result[0])
            keys.extend(list(map(lambda k: k.decode(), cast(list[bytes], result[1]))))
            if cursor == b"0":
                break
        assert set(expected_keys) == set(keys)
        assert not set(unexpected_type_keys).intersection(set(keys))
        assert not set(unexpected_pattern_keys).intersection(set(keys))

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_standalone_scan_with_count(self, redis_client: GlideClient):
        key = get_random_string(10)
        expected_keys = [f"{key}:{i}" for i in range(100)]
        await redis_client.mset({k: "value" for k in expected_keys})
        encoded_expected_keys = map(lambda k: k.encode(), expected_keys)
        cursor = "0"
        keys: list[str] = []
        successful_compared_scans = 0
        while True:
            result_of_1 = await redis_client.scan(cursor, count=1)
            cursor_bytes = cast(bytes, result_of_1[0])
            cursor = cursor_bytes.decode()
            keys_of_1 = cast(list[str], result_of_1[1])
            keys.extend(keys_of_1)
            result_of_100 = await redis_client.scan(cursor, count=100)
            cursor_bytes = cast(bytes, result_of_100[0])
            cursor = cursor_bytes.decode()
            keys_of_100 = cast(list[str], result_of_100[1])
            keys.extend(keys_of_100)
            if len(keys_of_100) > len(keys_of_1):
                successful_compared_scans += 1
            if cursor == "0":
                break
        assert set(encoded_expected_keys) == set(keys)
        assert successful_compared_scans > 0

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_standalone_scan_with_match(self, redis_client: GlideClient):
        key = get_random_string(10)
        expected_keys = [f"key:{key}:{i}" for i in range(100)]
        await redis_client.mset({k: "value" for k in expected_keys})
        encoded_expected_keys = map(lambda k: k.encode(), expected_keys)
        unexpected_keys = [f"{i}" for i in range(100)]
        await redis_client.mset({k: "value" for k in [f"{i}" for i in range(100)]})
        encoded_unexpected_keys = map(lambda k: k.encode(), unexpected_keys)
        cursor = "0"
        keys: list[str] = []
        while True:
            result = await redis_client.scan(cursor, match="key:*")
            cursor_bytes = cast(bytes, result[0])
            cursor = cursor_bytes.decode()
            new_keys = cast(list[str], result[1])
            keys.extend(new_keys)
            if cursor == "0":
                break
        assert set(encoded_expected_keys) == set(keys)
        assert not set(encoded_unexpected_keys).intersection(set(keys))

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_standalone_scan_all_types(self, redis_client: GlideClient):
        # We test that the scan command work for all types of keys
        key = get_random_string(10)
        string_keys = [f"{key}:{i}" for i in range(100)]
        await redis_client.mset({k: "value" for k in string_keys})
        encoded_string_keys = list(map(lambda k: k.encode(), string_keys))

        set_keys = [f"{key}:{i}" for i in range(100, 200)]
        for key in set_keys:
            await redis_client.sadd(key, ["value"])
        encoded_set_keys = list(map(lambda k: k.encode(), set_keys))

        hash_keys = [f"{key}:{i}" for i in range(200, 300)]
        for key in hash_keys:
            await redis_client.hset(key, {"field": "value"})
        encoded_hash_keys = list(map(lambda k: k.encode(), hash_keys))

        list_keys = [f"{key}:{i}" for i in range(300, 400)]
        for key in list_keys:
            await redis_client.lpush(key, ["value"])
        encoded_list_keys = list(map(lambda k: k.encode(), list_keys))

        zset_keys = [f"{key}:{i}" for i in range(400, 500)]
        for key in zset_keys:
            await redis_client.zadd(key, {"value": 1})
        encoded_zset_keys = list(map(lambda k: k.encode(), zset_keys))

        stream_keys = [f"{key}:{i}" for i in range(500, 600)]
        for key in stream_keys:
            await redis_client.xadd(key, [("field", "value")])
        encoded_stream_keys = list(map(lambda k: k.encode(), stream_keys))

        cursor = "0"
        keys: list[bytes] = []
        while True:
            result = await redis_client.scan(cursor, type=ObjectType.STRING)
            cursor_bytes = cast(bytes, result[0])
            cursor = cursor_bytes.decode()
            new_keys = result[1]
            keys.extend(cast(list[bytes], new_keys))
            if cursor == "0":
                break
        assert set(encoded_string_keys) == set(keys)
        assert not set(encoded_set_keys).intersection(set(keys))
        assert not set(encoded_hash_keys).intersection(set(keys))
        assert not set(encoded_list_keys).intersection(set(keys))
        assert not set(encoded_zset_keys).intersection(set(keys))
        assert not set(encoded_stream_keys).intersection(set(keys))

        keys.clear()
        while True:
            result = await redis_client.scan(cursor, type=ObjectType.SET)
            cursor_bytes = cast(bytes, result[0])
            cursor = cursor_bytes.decode()
            new_keys = result[1]
            keys.extend(cast(list[bytes], new_keys))
            if cursor == "0":
                break
        assert set(encoded_set_keys) == set(keys)
        assert not set(encoded_string_keys).intersection(set(keys))
        assert not set(encoded_hash_keys).intersection(set(keys))
        assert not set(encoded_list_keys).intersection(set(keys))
        assert not set(encoded_zset_keys).intersection(set(keys))
        assert not set(encoded_stream_keys).intersection(set(keys))

        keys.clear()
        while True:
            result = await redis_client.scan(cursor, type=ObjectType.HASH)
            cursor_bytes = cast(bytes, result[0])
            cursor = cursor_bytes.decode()
            new_keys = result[1]
            keys.extend(cast(list[bytes], new_keys))
            if cursor == "0":
                break
        assert set(encoded_hash_keys) == set(keys)
        assert not set(encoded_string_keys).intersection(set(keys))
        assert not set(encoded_set_keys).intersection(set(keys))
        assert not set(encoded_list_keys).intersection(set(keys))
        assert not set(encoded_zset_keys).intersection(set(keys))
        assert not set(encoded_stream_keys).intersection(set(keys))

        keys.clear()
        while True:
            result = await redis_client.scan(cursor, type=ObjectType.LIST)
            cursor_bytes = cast(bytes, result[0])
            cursor = cursor_bytes.decode()
            new_keys = result[1]
            keys.extend(cast(list[bytes], new_keys))
            if cursor == "0":
                break
        assert set(encoded_list_keys) == set(keys)
        assert not set(encoded_string_keys).intersection(set(keys))
        assert not set(encoded_set_keys).intersection(set(keys))
        assert not set(encoded_hash_keys).intersection(set(keys))
        assert not set(encoded_zset_keys).intersection(set(keys))
        assert not set(encoded_stream_keys).intersection(set(keys))

        keys.clear()
        while True:
            result = await redis_client.scan(cursor, type=ObjectType.ZSET)
            cursor_bytes = cast(bytes, result[0])
            cursor = cursor_bytes.decode()
            new_keys = result[1]
            keys.extend(cast(list[bytes], new_keys))
            if cursor == "0":
                break
        assert set(encoded_zset_keys) == set(keys)
        assert not set(encoded_string_keys).intersection(set(keys))
        assert not set(encoded_set_keys).intersection(set(keys))
        assert not set(encoded_hash_keys).intersection(set(keys))
        assert not set(encoded_list_keys).intersection(set(keys))
        assert not set(encoded_stream_keys).intersection(set(keys))

        keys.clear()
        while True:
            result = await redis_client.scan(cursor, type=ObjectType.STREAM)
            cursor_bytes = cast(bytes, result[0])
            cursor = cursor_bytes.decode()
            new_keys = result[1]
            keys.extend(cast(list[bytes], new_keys))
            if cursor == "0":
                break
        assert set(encoded_stream_keys) == set(keys)
        assert not set(encoded_string_keys).intersection(set(keys))
        assert not set(encoded_set_keys).intersection(set(keys))
        assert not set(encoded_hash_keys).intersection(set(keys))
        assert not set(encoded_list_keys).intersection(set(keys))
        assert not set(encoded_zset_keys).intersection(set(keys))
