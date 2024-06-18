/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestConfiguration.CLUSTER_PORTS;
import static glide.TestConfiguration.REDIS_VERSION;
import static glide.TestConfiguration.STANDALONE_PORTS;
import static glide.TestUtilities.assertDeepEquals;
import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static glide.api.BaseClient.OK;
import static glide.api.models.commands.LInsertOptions.InsertPosition.AFTER;
import static glide.api.models.commands.LInsertOptions.InsertPosition.BEFORE;
import static glide.api.models.commands.RangeOptions.InfScoreBound.NEGATIVE_INFINITY;
import static glide.api.models.commands.RangeOptions.InfScoreBound.POSITIVE_INFINITY;
import static glide.api.models.commands.ScoreFilter.MAX;
import static glide.api.models.commands.ScoreFilter.MIN;
import static glide.api.models.commands.SetOptions.ConditionalSet.ONLY_IF_DOES_NOT_EXIST;
import static glide.api.models.commands.SetOptions.ConditionalSet.ONLY_IF_EXISTS;
import static glide.api.models.commands.SetOptions.Expiry.Milliseconds;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.api.BaseClient;
import glide.api.RedisClient;
import glide.api.RedisClusterClient;
import glide.api.models.Script;
import glide.api.models.commands.ConditionalChange;
import glide.api.models.commands.ExpireOptions;
import glide.api.models.commands.LPosOptions;
import glide.api.models.commands.ListDirection;
import glide.api.models.commands.RangeOptions.InfLexBound;
import glide.api.models.commands.RangeOptions.InfScoreBound;
import glide.api.models.commands.RangeOptions.LexBoundary;
import glide.api.models.commands.RangeOptions.Limit;
import glide.api.models.commands.RangeOptions.RangeByIndex;
import glide.api.models.commands.RangeOptions.RangeByLex;
import glide.api.models.commands.RangeOptions.RangeByScore;
import glide.api.models.commands.RangeOptions.ScoreBoundary;
import glide.api.models.commands.ScriptOptions;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.WeightAggregateOptions.Aggregate;
import glide.api.models.commands.WeightAggregateOptions.KeyArray;
import glide.api.models.commands.WeightAggregateOptions.WeightedKeys;
import glide.api.models.commands.ZAddOptions;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldGet;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldIncrby;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldOverflow;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldOverflow.BitOverflowControl;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldReadOnlySubCommands;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldSet;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldSubCommands;
import glide.api.models.commands.bitmap.BitFieldOptions.Offset;
import glide.api.models.commands.bitmap.BitFieldOptions.OffsetMultiplier;
import glide.api.models.commands.bitmap.BitFieldOptions.SignedEncoding;
import glide.api.models.commands.bitmap.BitFieldOptions.UnsignedEncoding;
import glide.api.models.commands.bitmap.BitmapIndexType;
import glide.api.models.commands.bitmap.BitwiseOperation;
import glide.api.models.commands.geospatial.GeoAddOptions;
import glide.api.models.commands.geospatial.GeoUnit;
import glide.api.models.commands.geospatial.GeospatialData;
import glide.api.models.commands.stream.StreamAddOptions;
import glide.api.models.commands.stream.StreamGroupOptions;
import glide.api.models.commands.stream.StreamRange.IdBound;
import glide.api.models.commands.stream.StreamRange.InfRangeBound;
import glide.api.models.commands.stream.StreamReadOptions;
import glide.api.models.commands.stream.StreamTrimOptions.MaxLen;
import glide.api.models.commands.stream.StreamTrimOptions.MinId;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClientConfiguration;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import glide.api.models.exceptions.RequestException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Timeout(10) // seconds
public class SharedCommandTests {

    private static RedisClient standaloneClient = null;
    private static RedisClusterClient clusterClient = null;

    @Getter private static List<Arguments> clients;

    private static final String KEY_NAME = "key";
    private static final String INITIAL_VALUE = "VALUE";
    private static final String ANOTHER_VALUE = "VALUE2";

    @BeforeAll
    @SneakyThrows
    public static void init() {
        standaloneClient =
                RedisClient.CreateClient(
                                RedisClientConfiguration.builder()
                                        .address(NodeAddress.builder().port(STANDALONE_PORTS[0]).build())
                                        .requestTimeout(5000)
                                        .build())
                        .get();

        clusterClient =
                RedisClusterClient.CreateClient(
                                RedisClusterClientConfiguration.builder()
                                        .address(NodeAddress.builder().port(CLUSTER_PORTS[0]).build())
                                        .requestTimeout(5000)
                                        .build())
                        .get();

        clients = List.of(Arguments.of(standaloneClient), Arguments.of(clusterClient));
    }

    @AfterAll
    @SneakyThrows
    public static void teardown() {
        standaloneClient.close();
        clusterClient.close();
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void unlink_multiple_keys(BaseClient client) {
        String key1 = "{key}" + UUID.randomUUID();
        String key2 = "{key}" + UUID.randomUUID();
        String key3 = "{key}" + UUID.randomUUID();
        String value = UUID.randomUUID().toString();

        String setResult = client.set(key1, value).get();
        assertEquals(OK, setResult);
        setResult = client.set(key2, value).get();
        assertEquals(OK, setResult);
        setResult = client.set(key3, value).get();
        assertEquals(OK, setResult);

        Long unlinkedKeysNum = client.unlink(new String[] {key1, key2, key3}).get();
        assertEquals(3L, unlinkedKeysNum);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void unlink_non_existent_key(BaseClient client) {
        Long unlinkedKeysNum = client.unlink(new String[] {UUID.randomUUID().toString()}).get();
        assertEquals(0L, unlinkedKeysNum);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void set_and_get_without_options(BaseClient client) {
        String ok = client.set(KEY_NAME, INITIAL_VALUE).get();
        assertEquals(OK, ok);

        String data = client.get(KEY_NAME).get();
        assertEquals(INITIAL_VALUE, data);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void get_missing_value(BaseClient client) {
        String data = client.get("invalid").get();
        assertNull(data);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void append(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String value1 = String.valueOf(UUID.randomUUID());
        String key2 = UUID.randomUUID().toString();

        // Append on non-existing string(similar to SET)
        assertEquals(value1.length(), client.append(key1, value1).get());

        assertEquals(value1.length() * 2L, client.append(key1, value1).get());
        assertEquals(value1.concat(value1), client.get(key1).get());

        // key exists but holding the wrong kind of value
        assertEquals(1, client.sadd(key2, new String[] {"a"}).get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.append(key2, "z").get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void del_multiple_keys(BaseClient client) {
        String key1 = "{key}" + UUID.randomUUID();
        String key2 = "{key}" + UUID.randomUUID();
        String key3 = "{key}" + UUID.randomUUID();
        String value = UUID.randomUUID().toString();

        String setResult = client.set(key1, value).get();
        assertEquals(OK, setResult);
        setResult = client.set(key2, value).get();
        assertEquals(OK, setResult);
        setResult = client.set(key3, value).get();
        assertEquals(OK, setResult);

        Long deletedKeysNum = client.del(new String[] {key1, key2, key3}).get();
        assertEquals(3L, deletedKeysNum);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void del_non_existent_key(BaseClient client) {
        Long deletedKeysNum = client.del(new String[] {UUID.randomUUID().toString()}).get();
        assertEquals(0L, deletedKeysNum);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void set_overwrite_value_and_returnOldValue_returns_string(BaseClient client) {
        String ok = client.set(KEY_NAME, INITIAL_VALUE).get();
        assertEquals(OK, ok);

        SetOptions options = SetOptions.builder().returnOldValue(true).build();
        String data = client.set(KEY_NAME, ANOTHER_VALUE, options).get();
        assertEquals(INITIAL_VALUE, data);
    }

    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void set_requires_a_value(BaseClient client) {
        assertThrows(NullPointerException.class, () -> client.set("SET", null));
    }

    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void set_requires_a_key(BaseClient client) {
        assertThrows(NullPointerException.class, () -> client.set(null, INITIAL_VALUE));
    }

    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void get_requires_a_key(BaseClient client) {
        assertThrows(NullPointerException.class, () -> client.get((String) null));
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void getdel(BaseClient client) {
        String key1 = "{key}" + UUID.randomUUID();
        String value1 = String.valueOf(UUID.randomUUID());
        String key2 = "{key}" + UUID.randomUUID();

        client.set(key1, value1).get();
        String data = client.getdel(key1).get();
        assertEquals(data, value1);
        data = client.getdel(key1).get();
        assertNull(data);
        assertNull(client.getdel(key2).get());

        // key isn't a string
        client.sadd(key2, new String[] {"a"}).get();
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.getdel(key2).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void set_only_if_exists_overwrite(BaseClient client) {
        String key = "set_only_if_exists_overwrite";
        SetOptions options = SetOptions.builder().conditionalSet(ONLY_IF_EXISTS).build();
        client.set(key, INITIAL_VALUE).get();
        client.set(key, ANOTHER_VALUE, options).get();
        String data = client.get(key).get();
        assertEquals(ANOTHER_VALUE, data);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void set_only_if_exists_missing_key(BaseClient client) {
        String key = "set_only_if_exists_missing_key";
        SetOptions options = SetOptions.builder().conditionalSet(ONLY_IF_EXISTS).build();
        client.set(key, ANOTHER_VALUE, options).get();
        String data = client.get(key).get();
        assertNull(data);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void set_only_if_does_not_exists_missing_key(BaseClient client) {
        String key = "set_only_if_does_not_exists_missing_key";
        SetOptions options = SetOptions.builder().conditionalSet(ONLY_IF_DOES_NOT_EXIST).build();
        client.set(key, ANOTHER_VALUE, options).get();
        String data = client.get(key).get();
        assertEquals(ANOTHER_VALUE, data);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void set_get_binary_data(BaseClient client) {
        byte[] key = "set_get_binary_data_key".getBytes();
        byte[] value = {(byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x02};
        assert client.set(key, value).get().equals("OK");
        byte[] data = client.get(key).get();
        assert Arrays.equals(data, value);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void set_only_if_does_not_exists_existing_key(BaseClient client) {
        String key = "set_only_if_does_not_exists_existing_key";
        SetOptions options = SetOptions.builder().conditionalSet(ONLY_IF_DOES_NOT_EXIST).build();
        client.set(key, INITIAL_VALUE).get();
        client.set(key, ANOTHER_VALUE, options).get();
        String data = client.get(key).get();
        assertEquals(INITIAL_VALUE, data);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void set_value_with_ttl_and_update_value_with_keeping_ttl(BaseClient client) {
        String key = "set_value_with_ttl_and_update_value_with_keeping_ttl";
        SetOptions options = SetOptions.builder().expiry(Milliseconds(2000L)).build();
        client.set(key, INITIAL_VALUE, options).get();
        String data = client.get(key).get();
        assertEquals(INITIAL_VALUE, data);

        options = SetOptions.builder().expiry(SetOptions.Expiry.KeepExisting()).build();
        client.set(key, ANOTHER_VALUE, options).get();
        data = client.get(key).get();
        assertEquals(ANOTHER_VALUE, data);

        Thread.sleep(2222); // sleep a bit more than TTL

        data = client.get(key).get();
        assertNull(data);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void set_value_with_ttl_and_update_value_with_new_ttl(BaseClient client) {
        String key = "set_value_with_ttl_and_update_value_with_new_ttl";
        SetOptions options = SetOptions.builder().expiry(Milliseconds(100500L)).build();
        client.set(key, INITIAL_VALUE, options).get();
        String data = client.get(key).get();
        assertEquals(INITIAL_VALUE, data);

        options = SetOptions.builder().expiry(Milliseconds(2000L)).build();
        client.set(key, ANOTHER_VALUE, options).get();
        data = client.get(key).get();
        assertEquals(ANOTHER_VALUE, data);

        Thread.sleep(2222); // sleep a bit more than new TTL

        data = client.get(key).get();
        assertNull(data);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void set_expired_value(BaseClient client) {
        String key = "set_expired_value";
        SetOptions options =
                SetOptions.builder()
                        // expiration is in the past
                        .expiry(SetOptions.Expiry.UnixSeconds(100500L))
                        .build();
        client.set(key, INITIAL_VALUE, options).get();
        String data = client.get(key).get();
        assertNull(data);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void set_missing_value_and_returnOldValue_is_null(BaseClient client) {
        String ok = client.set(KEY_NAME, INITIAL_VALUE).get();
        assertEquals(OK, ok);

        SetOptions options = SetOptions.builder().returnOldValue(true).build();
        String data = client.set(UUID.randomUUID().toString(), ANOTHER_VALUE, options).get();
        assertNull(data);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void mset_mget_existing_non_existing_key(BaseClient client) {
        // keys are from different slots
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String key3 = UUID.randomUUID().toString();
        String nonExisting = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        Map<String, String> keyValueMap = Map.of(key1, value, key2, value, key3, value);

        assertEquals(OK, client.mset(keyValueMap).get());
        assertArrayEquals(
                new String[] {value, value, null, value},
                client.mget(new String[] {key1, key2, nonExisting, key3}).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void incr_commands_existing_key(BaseClient client) {
        String key = UUID.randomUUID().toString();

        assertEquals(OK, client.set(key, "10").get());

        assertEquals(11, client.incr(key).get());
        assertEquals("11", client.get(key).get());

        assertEquals(15, client.incrBy(key, 4).get());
        assertEquals("15", client.get(key).get());

        assertEquals(20.5, client.incrByFloat(key, 5.5).get());
        assertEquals("20.5", client.get(key).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void incr_commands_non_existing_key(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String key3 = UUID.randomUUID().toString();

        assertNull(client.get(key1).get());
        assertEquals(1, client.incr(key1).get());
        assertEquals("1", client.get(key1).get());

        assertNull(client.get(key2).get());
        assertEquals(3, client.incrBy(key2, 3).get());
        assertEquals("3", client.get(key2).get());

        assertNull(client.get(key3).get());
        assertEquals(0.5, client.incrByFloat(key3, 0.5).get());
        assertEquals("0.5", client.get(key3).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void test_incr_commands_type_error(BaseClient client) {
        String key1 = UUID.randomUUID().toString();

        assertEquals(OK, client.set(key1, "foo").get());

        Exception incrException = assertThrows(ExecutionException.class, () -> client.incr(key1).get());
        assertTrue(incrException.getCause() instanceof RequestException);

        Exception incrByException =
                assertThrows(ExecutionException.class, () -> client.incrBy(key1, 3).get());
        assertTrue(incrByException.getCause() instanceof RequestException);

        Exception incrByFloatException =
                assertThrows(ExecutionException.class, () -> client.incrByFloat(key1, 3.5).get());
        assertTrue(incrByFloatException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void decr_and_decrBy_existing_key(BaseClient client) {
        String key = UUID.randomUUID().toString();

        assertEquals(OK, client.set(key, "10").get());

        assertEquals(9, client.decr(key).get());
        assertEquals("9", client.get(key).get());

        assertEquals(5, client.decrBy(key, 4).get());
        assertEquals("5", client.get(key).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void decr_and_decrBy_non_existing_key(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();

        assertNull(client.get(key1).get());
        assertEquals(-1, client.decr(key1).get());
        assertEquals("-1", client.get(key1).get());

        assertNull(client.get(key2).get());
        assertEquals(-3, client.decrBy(key2, 3).get());
        assertEquals("-3", client.get(key2).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void strlen(BaseClient client) {
        String stringKey = UUID.randomUUID().toString();
        String nonStringKey = UUID.randomUUID().toString();
        String nonExistingKey = UUID.randomUUID().toString();

        assertEquals(OK, client.set(stringKey, "GLIDE").get());
        assertEquals(5L, client.strlen(stringKey).get());

        assertEquals(0L, client.strlen(nonExistingKey).get());

        assertEquals(1, client.lpush(nonStringKey, new String[] {"_"}).get());
        Exception exception =
                assertThrows(ExecutionException.class, () -> client.strlen(nonStringKey).get());
        assertTrue(exception.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void setrange(BaseClient client) {
        String stringKey = UUID.randomUUID().toString();
        String nonStringKey = UUID.randomUUID().toString();
        // new key
        assertEquals(11L, client.setrange(stringKey, 0, "Hello world").get());
        // existing key
        assertEquals(11L, client.setrange(stringKey, 6, "GLIDE").get());
        assertEquals("Hello GLIDE", client.get(stringKey).get());

        // offset > len
        assertEquals(20L, client.setrange(stringKey, 15, "GLIDE").get());
        assertEquals("Hello GLIDE\0\0\0\0GLIDE", client.get(stringKey).get());

        // non-string key
        assertEquals(1, client.lpush(nonStringKey, new String[] {"_"}).get());
        Exception exception =
                assertThrows(ExecutionException.class, () -> client.setrange(nonStringKey, 0, "_").get());
        assertTrue(exception.getCause() instanceof RequestException);
        exception =
                assertThrows(
                        ExecutionException.class,
                        () -> client.setrange(stringKey, Integer.MAX_VALUE, "_").get());
        assertTrue(exception.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void getrange(BaseClient client) {
        String stringKey = UUID.randomUUID().toString();
        String nonStringKey = UUID.randomUUID().toString();

        assertEquals(OK, client.set(stringKey, "This is a string").get());
        assertEquals("This", client.getrange(stringKey, 0, 3).get());
        assertEquals("ing", client.getrange(stringKey, -3, -1).get());
        assertEquals("This is a string", client.getrange(stringKey, 0, -1).get());

        // out of range
        assertEquals("string", client.getrange(stringKey, 10, 100).get());
        assertEquals("This is a stri", client.getrange(stringKey, -200, -3).get());
        assertEquals("", client.getrange(stringKey, 100, 200).get());

        // incorrect range
        assertEquals("", client.getrange(stringKey, -1, -3).get());

        // a redis bug, fixed in version 8: https://github.com/redis/redis/issues/13207
        assertEquals(
                REDIS_VERSION.isLowerThan("8.0.0") ? "T" : "",
                client.getrange(stringKey, -200, -100).get());

        // empty key (returning null isn't implemented)
        assertEquals(
                REDIS_VERSION.isLowerThan("8.0.0") ? "" : null, client.getrange(nonStringKey, 0, -1).get());

        // non-string key
        assertEquals(1, client.lpush(nonStringKey, new String[] {"_"}).get());
        Exception exception =
                assertThrows(ExecutionException.class, () -> client.getrange(nonStringKey, 0, -1).get());
        assertTrue(exception.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void hset_hget_existing_fields_non_existing_fields(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String field1 = UUID.randomUUID().toString();
        String field2 = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        Map<String, String> fieldValueMap = Map.of(field1, value, field2, value);

        assertEquals(2, client.hset(key, fieldValueMap).get());
        assertEquals(value, client.hget(key, field1).get());
        assertEquals(value, client.hget(key, field2).get());
        assertNull(client.hget(key, "non_existing_field").get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void hsetnx(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String field = UUID.randomUUID().toString();

        assertTrue(client.hsetnx(key1, field, "value").get());
        assertFalse(client.hsetnx(key1, field, "newValue").get());
        assertEquals("value", client.hget(key1, field).get());

        // Key exists, but it is not a hash
        assertEquals(OK, client.set(key2, "value").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.hsetnx(key2, field, "value").get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void hdel_multiple_existing_fields_non_existing_field_non_existing_key(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String field1 = UUID.randomUUID().toString();
        String field2 = UUID.randomUUID().toString();
        String field3 = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        Map<String, String> fieldValueMap = Map.of(field1, value, field2, value, field3, value);

        assertEquals(3, client.hset(key, fieldValueMap).get());
        assertEquals(2, client.hdel(key, new String[] {field1, field2}).get());
        assertEquals(0, client.hdel(key, new String[] {"non_existing_field"}).get());
        assertEquals(0, client.hdel("non_existing_key", new String[] {field3}).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void hlen(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String field1 = UUID.randomUUID().toString();
        String field2 = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        Map<String, String> fieldValueMap = Map.of(field1, value, field2, value);

        assertEquals(2, client.hset(key1, fieldValueMap).get());
        assertEquals(2, client.hlen(key1).get());
        assertEquals(1, client.hdel(key1, new String[] {field1}).get());
        assertEquals(1, client.hlen(key1).get());
        assertEquals(0, client.hlen("nonExistingHash").get());

        // Key exists, but it is not a hash
        assertEquals(OK, client.set(key2, "value").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.hlen(key2).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void hvals(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String field1 = UUID.randomUUID().toString();
        String field2 = UUID.randomUUID().toString();
        Map<String, String> fieldValueMap = Map.of(field1, "value1", field2, "value2");

        assertEquals(2, client.hset(key1, fieldValueMap).get());

        String[] hvalsPayload = client.hvals(key1).get();
        Arrays.sort(hvalsPayload); // ordering for values by hvals is not guaranteed
        assertArrayEquals(new String[] {"value1", "value2"}, hvalsPayload);

        assertEquals(1, client.hdel(key1, new String[] {field1}).get());
        assertArrayEquals(new String[] {"value2"}, client.hvals(key1).get());
        assertArrayEquals(new String[] {}, client.hvals("nonExistingKey").get());

        assertEquals(OK, client.set(key2, "value2").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.hvals(key2).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void hmget_multiple_existing_fields_non_existing_field_non_existing_key(
            BaseClient client) {
        String key = UUID.randomUUID().toString();
        String field1 = UUID.randomUUID().toString();
        String field2 = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        Map<String, String> fieldValueMap = Map.of(field1, value, field2, value);

        assertEquals(2, client.hset(key, fieldValueMap).get());
        assertArrayEquals(
                new String[] {value, null, value},
                client.hmget(key, new String[] {field1, "non_existing_field", field2}).get());
        assertArrayEquals(
                new String[] {null, null},
                client.hmget("non_existing_key", new String[] {field1, field2}).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void hexists_existing_field_non_existing_field_non_existing_key(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String field1 = UUID.randomUUID().toString();
        String field2 = UUID.randomUUID().toString();
        Map<String, String> fieldValueMap = Map.of(field1, "value1", field2, "value1");

        assertEquals(2, client.hset(key, fieldValueMap).get());
        assertTrue(client.hexists(key, field1).get());
        assertFalse(client.hexists(key, "non_existing_field").get());
        assertFalse(client.hexists("non_existing_key", field2).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void hgetall_multiple_existing_fields_existing_key_non_existing_key(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String field1 = UUID.randomUUID().toString();
        String field2 = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        Map<String, String> fieldValueMap = Map.of(field1, value, field2, value);

        assertEquals(2, client.hset(key, fieldValueMap).get());
        assertEquals(fieldValueMap, client.hgetall(key).get());
        assertEquals(Map.of(), client.hgetall("non_existing_key").get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void hincrBy_hincrByFloat_commands_existing_key_existing_field(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String field = UUID.randomUUID().toString();
        Map<String, String> fieldValueMap = Map.of(field, "10");

        assertEquals(1, client.hset(key, fieldValueMap).get());

        assertEquals(11, client.hincrBy(key, field, 1).get());
        assertEquals(15, client.hincrBy(key, field, 4).get());
        assertEquals(16.5, client.hincrByFloat(key, field, 1.5).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void hincrBy_hincrByFloat_commands_non_existing_key_non_existing_field(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String field = UUID.randomUUID().toString();
        Map<String, String> fieldValueMap = Map.of(field, "10");

        assertEquals(1, client.hincrBy("non_existing_key_1", field, 1).get());
        assertEquals(1, client.hset(key1, fieldValueMap).get());
        assertEquals(2, client.hincrBy(key1, "non_existing_field_1", 2).get());

        assertEquals(0.5, client.hincrByFloat("non_existing_key_2", field, 0.5).get());
        assertEquals(1, client.hset(key2, fieldValueMap).get());
        assertEquals(-0.5, client.hincrByFloat(key1, "non_existing_field_2", -0.5).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void hincrBy_hincrByFloat_type_error(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String field = UUID.randomUUID().toString();
        Map<String, String> fieldValueMap = Map.of(field, "foo");

        assertEquals(1, client.hset(key, fieldValueMap).get());

        Exception hincrByException =
                assertThrows(ExecutionException.class, () -> client.hincrBy(key, field, 2).get());
        assertTrue(hincrByException.getCause() instanceof RequestException);

        Exception hincrByFloatException =
                assertThrows(ExecutionException.class, () -> client.hincrByFloat(key, field, 2.5).get());
        assertTrue(hincrByFloatException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void hkeys(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();

        var data = new LinkedHashMap<String, String>();
        data.put("f 1", "v 1");
        data.put("f 2", "v 2");
        assertEquals(2, client.hset(key1, data).get());
        assertArrayEquals(new String[] {"f 1", "f 2"}, client.hkeys(key1).get());

        assertEquals(0, client.hkeys(key2).get().length);

        // Key exists, but it is not a hash
        assertEquals(OK, client.set(key2, "value").get());
        Exception executionException =
                assertThrows(ExecutionException.class, () -> client.hkeys(key2).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void hstrlen(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();

        assertEquals(1, client.hset(key1, Map.of("field", "value")).get());
        assertEquals(5L, client.hstrlen(key1, "field").get());

        // missing value
        assertEquals(0, client.hstrlen(key1, "field 2").get());

        // missing key
        assertEquals(0, client.hstrlen(key2, "field").get());

        // Key exists, but it is not a hash
        assertEquals(OK, client.set(key2, "value").get());
        Exception executionException =
                assertThrows(ExecutionException.class, () -> client.hstrlen(key2, "field").get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void hrandfield(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();

        // key does not exist
        assertNull(client.hrandfield(key1).get());
        assertEquals(0, client.hrandfieldWithCount(key1, 5).get().length);
        assertEquals(0, client.hrandfieldWithCountWithValues(key1, 5).get().length);

        var data = Map.of("f 1", "v 1", "f 2", "v 2", "f 3", "v 3");
        assertEquals(3, client.hset(key1, data).get());

        // random key
        assertTrue(data.containsKey(client.hrandfield(key1).get()));

        // WithCount - positive count
        var keys = client.hrandfieldWithCount(key1, 5).get();
        assertEquals(data.keySet().size(), keys.length);
        assertEquals(data.keySet(), Set.of(keys));

        // WithCount - negative count
        keys = client.hrandfieldWithCount(key1, -5).get();
        assertEquals(5, keys.length);
        Arrays.stream(keys).forEach(key -> assertTrue(data.containsKey(key)));

        // WithCountWithValues - positive count
        var keysWithValues = client.hrandfieldWithCountWithValues(key1, 5).get();
        assertEquals(data.keySet().size(), keysWithValues.length);
        for (var pair : keysWithValues) {
            assertEquals(data.get(pair[0]), pair[1]);
        }

        // WithCountWithValues - negative count
        keysWithValues = client.hrandfieldWithCountWithValues(key1, -5).get();
        assertEquals(5, keysWithValues.length);
        for (var pair : keysWithValues) {
            assertEquals(data.get(pair[0]), pair[1]);
        }

        // Key exists, but it is not a List
        assertEquals(OK, client.set(key2, "value").get());
        Exception executionException =
                assertThrows(ExecutionException.class, () -> client.hrandfield(key2).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        executionException =
                assertThrows(ExecutionException.class, () -> client.hrandfieldWithCount(key2, 2).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        executionException =
                assertThrows(
                        ExecutionException.class, () -> client.hrandfieldWithCountWithValues(key2, 3).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void lpush_lpop_lrange_existing_non_existing_key(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String[] valueArray = new String[] {"value4", "value3", "value2", "value1"};

        assertEquals(4, client.lpush(key, valueArray).get());
        assertEquals("value1", client.lpop(key).get());
        assertArrayEquals(new String[] {"value2", "value3", "value4"}, client.lrange(key, 0, -1).get());
        assertArrayEquals(new String[] {"value2", "value3"}, client.lpopCount(key, 2).get());
        assertArrayEquals(new String[] {}, client.lrange("non_existing_key", 0, -1).get());
        assertNull(client.lpop("non_existing_key").get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void lpush_lpop_lrange_type_error(BaseClient client) {
        String key = UUID.randomUUID().toString();

        assertEquals(OK, client.set(key, "foo").get());

        Exception lpushException =
                assertThrows(ExecutionException.class, () -> client.lpush(key, new String[] {"foo"}).get());
        assertTrue(lpushException.getCause() instanceof RequestException);

        Exception lpopException = assertThrows(ExecutionException.class, () -> client.lpop(key).get());
        assertTrue(lpopException.getCause() instanceof RequestException);

        Exception lpopCountException =
                assertThrows(ExecutionException.class, () -> client.lpopCount(key, 2).get());
        assertTrue(lpopCountException.getCause() instanceof RequestException);

        Exception lrangeException =
                assertThrows(ExecutionException.class, () -> client.lrange(key, 0, -1).get());
        assertTrue(lrangeException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void lindex(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String[] valueArray = new String[] {"value1", "value2"};

        assertEquals(2, client.lpush(key1, valueArray).get());
        assertEquals(valueArray[1], client.lindex(key1, 0).get());
        assertEquals(valueArray[0], client.lindex(key1, -1).get());
        assertNull(client.lindex(key1, 3).get());
        assertNull(client.lindex(key2, 3).get());

        // Key exists, but it is not a List
        assertEquals(OK, client.set(key2, "value").get());
        Exception executionException =
                assertThrows(ExecutionException.class, () -> client.lindex(key2, 0).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ltrim_existing_non_existing_key_and_type_error(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String[] valueArray = new String[] {"value4", "value3", "value2", "value1"};

        assertEquals(4, client.lpush(key, valueArray).get());
        assertEquals(OK, client.ltrim(key, 0, 1).get());
        assertArrayEquals(new String[] {"value1", "value2"}, client.lrange(key, 0, -1).get());

        // `start` is greater than `end` so the key will be removed.
        assertEquals(OK, client.ltrim(key, 4, 2).get());
        assertArrayEquals(new String[] {}, client.lrange(key, 0, -1).get());

        assertEquals(OK, client.set(key, "foo").get());

        Exception ltrimException =
                assertThrows(ExecutionException.class, () -> client.ltrim(key, 0, 1).get());
        assertTrue(ltrimException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void llen_existing_non_existing_key_and_type_error(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String[] valueArray = new String[] {"value4", "value3", "value2", "value1"};

        assertEquals(4, client.lpush(key1, valueArray).get());
        assertEquals(4, client.llen(key1).get());
        assertEquals(0, client.llen("non_existing_key").get());

        assertEquals(OK, client.set(key2, "foo").get());

        Exception lrangeException =
                assertThrows(ExecutionException.class, () -> client.llen(key2).get());
        assertTrue(lrangeException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void lrem_existing_non_existing_key_and_type_error(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String[] valueArray =
                new String[] {
                    "value1", "value2", "value1", "value1", "value2",
                };

        assertEquals(5, client.lpush(key, valueArray).get());
        assertEquals(2, client.lrem(key, 2, "value1").get());
        assertArrayEquals(
                new String[] {
                    "value2", "value2", "value1",
                },
                client.lrange(key, 0, -1).get());
        assertEquals(1, client.lrem(key, -1, "value2").get());
        assertArrayEquals(new String[] {"value2", "value1"}, client.lrange(key, 0, -1).get());
        assertEquals(1, client.lrem(key, 0, "value2").get());
        assertArrayEquals(new String[] {"value1"}, client.lrange(key, 0, -1).get());
        assertEquals(0, client.lrem("non_existing_key", 0, "value").get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void lpos(BaseClient client) {
        String key = "{ListKey}-1-" + UUID.randomUUID();
        String[] valueArray = new String[] {"a", "a", "b", "c", "a", "b"};
        assertEquals(6L, client.rpush(key, valueArray).get());

        // simplest case
        assertEquals(0L, client.lpos(key, "a").get());
        assertEquals(5L, client.lpos(key, "b", LPosOptions.builder().rank(2L).build()).get());

        // element doesn't exist
        assertNull(client.lpos(key, "e").get());

        // reverse traversal
        assertEquals(2L, client.lpos(key, "b", LPosOptions.builder().rank(-2L).build()).get());

        // unlimited comparisons
        assertEquals(
                0L, client.lpos(key, "a", LPosOptions.builder().rank(1L).maxLength(0L).build()).get());

        // limited comparisons
        assertNull(client.lpos(key, "c", LPosOptions.builder().rank(1L).maxLength(2L).build()).get());

        // invalid rank value
        ExecutionException lposException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.lpos(key, "a", LPosOptions.builder().rank(0L).build()).get());
        assertTrue(lposException.getCause() instanceof RequestException);

        // invalid maxlen value
        ExecutionException lposMaxlenException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.lpos(key, "a", LPosOptions.builder().maxLength(-1L).build()).get());
        assertTrue(lposMaxlenException.getCause() instanceof RequestException);

        // non-existent key
        assertNull(client.lpos("non-existent_key", "a").get());

        // wrong key data type
        String wrong_data_type = "key" + UUID.randomUUID();
        assertEquals(2L, client.sadd(wrong_data_type, new String[] {"a", "b"}).get());
        ExecutionException lposWrongKeyDataTypeException =
                assertThrows(ExecutionException.class, () -> client.lpos(wrong_data_type, "a").get());
        assertTrue(lposWrongKeyDataTypeException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void lposCount(BaseClient client) {
        String key = "{ListKey}-1-" + UUID.randomUUID();
        String[] valueArray = new String[] {"a", "a", "b", "c", "a", "b"};
        assertEquals(6L, client.rpush(key, valueArray).get());

        assertArrayEquals(new Long[] {0L, 1L}, client.lposCount(key, "a", 2L).get());
        assertArrayEquals(new Long[] {0L, 1L, 4L}, client.lposCount(key, "a", 0L).get());

        // invalid count value
        ExecutionException lposCountException =
                assertThrows(ExecutionException.class, () -> client.lposCount(key, "a", -1L).get());
        assertTrue(lposCountException.getCause() instanceof RequestException);

        // with option
        assertArrayEquals(
                new Long[] {0L, 1L, 4L},
                client.lposCount(key, "a", 0L, LPosOptions.builder().rank(1L).build()).get());
        assertArrayEquals(
                new Long[] {1L, 4L},
                client.lposCount(key, "a", 0L, LPosOptions.builder().rank(2L).build()).get());
        assertArrayEquals(
                new Long[] {4L},
                client.lposCount(key, "a", 0L, LPosOptions.builder().rank(3L).build()).get());

        // reverse traversal
        assertArrayEquals(
                new Long[] {4L, 1L, 0L},
                client.lposCount(key, "a", 0L, LPosOptions.builder().rank(-1L).build()).get());

        // non-existent key
        assertArrayEquals(new Long[] {}, client.lposCount("non-existent_key", "a", 1L).get());

        // wrong key data type
        String wrong_data_type = "key" + UUID.randomUUID();
        assertEquals(2L, client.sadd(wrong_data_type, new String[] {"a", "b"}).get());
        ExecutionException lposWrongKeyDataTypeException =
                assertThrows(
                        ExecutionException.class, () -> client.lposCount(wrong_data_type, "a", 1L).get());
        assertTrue(lposWrongKeyDataTypeException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void rpush_rpop_existing_non_existing_key(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String[] valueArray = new String[] {"value1", "value2", "value3", "value4"};

        assertEquals(4, client.rpush(key, valueArray).get());
        assertEquals("value4", client.rpop(key).get());

        assertArrayEquals(new String[] {"value3", "value2"}, client.rpopCount(key, 2).get());
        assertNull(client.rpop("non_existing_key").get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void rpush_rpop_type_error(BaseClient client) {
        String key = UUID.randomUUID().toString();

        assertEquals(OK, client.set(key, "foo").get());

        Exception rpushException =
                assertThrows(ExecutionException.class, () -> client.rpush(key, new String[] {"foo"}).get());
        assertTrue(rpushException.getCause() instanceof RequestException);

        Exception rpopException = assertThrows(ExecutionException.class, () -> client.rpop(key).get());
        assertTrue(rpopException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void sadd_srem_scard_smembers_existing_set(BaseClient client) {
        String key = UUID.randomUUID().toString();
        assertEquals(
                4, client.sadd(key, new String[] {"member1", "member2", "member3", "member4"}).get());
        assertEquals(1, client.srem(key, new String[] {"member3", "nonExistingMember"}).get());

        Set<String> expectedMembers = Set.of("member1", "member2", "member4");
        assertEquals(expectedMembers, client.smembers(key).get());
        assertEquals(1, client.srem(key, new String[] {"member1"}).get());
        assertEquals(2, client.scard(key).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void srem_scard_smembers_non_existing_key(BaseClient client) {
        assertEquals(0, client.srem("nonExistingKey", new String[] {"member"}).get());
        assertEquals(0, client.scard("nonExistingKey").get());
        assertEquals(Set.of(), client.smembers("nonExistingKey").get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void sadd_srem_scard_smembers_key_with_non_set_value(BaseClient client) {
        String key = UUID.randomUUID().toString();
        assertEquals(OK, client.set(key, "foo").get());

        Exception e =
                assertThrows(ExecutionException.class, () -> client.sadd(key, new String[] {"baz"}).get());
        assertTrue(e.getCause() instanceof RequestException);

        e = assertThrows(ExecutionException.class, () -> client.srem(key, new String[] {"baz"}).get());
        assertTrue(e.getCause() instanceof RequestException);

        e = assertThrows(ExecutionException.class, () -> client.scard(key).get());
        assertTrue(e.getCause() instanceof RequestException);

        e = assertThrows(ExecutionException.class, () -> client.smembers(key).get());
        assertTrue(e.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void smove(BaseClient client) {
        String setKey1 = "{key}" + UUID.randomUUID();
        String setKey2 = "{key}" + UUID.randomUUID();
        String setKey3 = "{key}" + UUID.randomUUID();
        String nonSetKey = "{key}" + UUID.randomUUID();

        assertEquals(3, client.sadd(setKey1, new String[] {"1", "2", "3"}).get());
        assertEquals(2, client.sadd(setKey2, new String[] {"2", "3"}).get());

        // move an elem
        assertTrue(client.smove(setKey1, setKey2, "1").get());
        assertEquals(Set.of("2", "3"), client.smembers(setKey1).get());
        assertEquals(Set.of("1", "2", "3"), client.smembers(setKey2).get());

        // move an elem which preset at destination
        assertTrue(client.smove(setKey2, setKey1, "2").get());
        assertEquals(Set.of("2", "3"), client.smembers(setKey1).get());
        assertEquals(Set.of("1", "3"), client.smembers(setKey2).get());

        // move from missing key
        assertFalse(client.smove(setKey3, setKey1, "4").get());
        assertEquals(Set.of("2", "3"), client.smembers(setKey1).get());

        // move to a new set
        assertTrue(client.smove(setKey1, setKey3, "2").get());
        assertEquals(Set.of("3"), client.smembers(setKey1).get());
        assertEquals(Set.of("2"), client.smembers(setKey3).get());

        // move missing element
        assertFalse(client.smove(setKey1, setKey3, "42").get());
        assertEquals(Set.of("3"), client.smembers(setKey1).get());
        assertEquals(Set.of("2"), client.smembers(setKey3).get());

        // move missing element to missing key
        assertFalse(client.smove(setKey1, nonSetKey, "42").get());
        assertEquals(Set.of("3"), client.smembers(setKey1).get());
        assertEquals("none", client.type(nonSetKey).get());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(nonSetKey, "bar").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.smove(nonSetKey, setKey1, "_").get());
        assertInstanceOf(RequestException.class, executionException.getCause());

        executionException =
                assertThrows(ExecutionException.class, () -> client.smove(setKey1, nonSetKey, "_").get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void rename(BaseClient client) {
        String key1 = "{key}" + UUID.randomUUID();

        assertEquals(OK, client.set(key1, "foo").get());
        assertEquals(OK, client.rename(key1, key1 + "_rename").get());
        assertEquals(1L, client.exists(new String[] {key1 + "_rename"}).get());

        // key doesn't exist
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client.rename("{same_slot}" + "non_existing_key", "{same_slot}" + "_rename").get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void renamenx(BaseClient client) {
        String key1 = "{key}" + UUID.randomUUID();
        String key2 = "{key}" + UUID.randomUUID();
        String key3 = "{key}" + UUID.randomUUID();

        assertEquals(OK, client.set(key3, "key3").get());

        // rename missing key
        var executionException =
                assertThrows(ExecutionException.class, () -> client.renamenx(key1, key2).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        assertTrue(executionException.getMessage().toLowerCase().contains("no such key"));

        // rename a string
        assertEquals(OK, client.set(key1, "key1").get());
        assertTrue(client.renamenx(key1, key2).get());
        assertFalse(client.renamenx(key2, key3).get());
        assertEquals("key1", client.get(key2).get());
        assertEquals(1, client.del(new String[] {key1, key2}).get());

        // this one remains unchanged
        assertEquals("key3", client.get(key3).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void sismember(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String member = UUID.randomUUID().toString();

        assertEquals(1, client.sadd(key1, new String[] {member}).get());
        assertTrue(client.sismember(key1, member).get());
        assertFalse(client.sismember(key1, "nonExistingMember").get());
        assertFalse(client.sismember("nonExistingKey", member).get());

        assertEquals(OK, client.set(key2, "value").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.sismember(key2, member).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void sinterstore(BaseClient client) {
        String key1 = "{key}-1-" + UUID.randomUUID();
        String key2 = "{key}-2-" + UUID.randomUUID();
        String key3 = "{key}-3-" + UUID.randomUUID();
        String key4 = "{key}-4-" + UUID.randomUUID();
        String key5 = "{key}-5-" + UUID.randomUUID();

        assertEquals(3, client.sadd(key1, new String[] {"a", "b", "c"}).get());
        assertEquals(3, client.sadd(key2, new String[] {"c", "d", "e"}).get());
        assertEquals(3, client.sadd(key4, new String[] {"e", "f", "g"}).get());

        // create new
        assertEquals(1, client.sinterstore(key3, new String[] {key1, key2}).get());
        assertEquals(Set.of("c"), client.smembers(key3).get());

        // overwrite existing set
        assertEquals(1, client.sinterstore(key2, new String[] {key3, key2}).get());
        assertEquals(Set.of("c"), client.smembers(key2).get());

        // overwrite source
        assertEquals(0, client.sinterstore(key1, new String[] {key1, key4}).get());
        assertEquals(Set.of(), client.smembers(key1).get());

        // overwrite source
        assertEquals(1, client.sinterstore(key2, new String[] {key2}).get());
        assertEquals(Set.of("c"), client.smembers(key2).get());

        // source key exists, but it is not a set
        assertEquals(OK, client.set(key5, "value").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class, () -> client.sinterstore(key1, new String[] {key5}).get());
        assertTrue(executionException.getCause() instanceof RequestException);

        // overwrite destination - not a set
        assertEquals(0, client.sinterstore(key5, new String[] {key1, key2}).get());
        assertEquals(Set.of(), client.smembers(key5).get());

        // wrong arguments
        executionException =
                assertThrows(ExecutionException.class, () -> client.sinterstore(key5, new String[0]).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void sdiff(BaseClient client) {
        String key1 = "{key}-1-" + UUID.randomUUID();
        String key2 = "{key}-2-" + UUID.randomUUID();
        String key3 = "{key}-3-" + UUID.randomUUID();

        assertEquals(3, client.sadd(key1, new String[] {"a", "b", "c"}).get());
        assertEquals(3, client.sadd(key2, new String[] {"c", "d", "e"}).get());

        assertEquals(Set.of("a", "b"), client.sdiff(new String[] {key1, key2}).get());
        assertEquals(Set.of("d", "e"), client.sdiff(new String[] {key2, key1}).get());

        // second set is empty
        assertEquals(Set.of("a", "b", "c"), client.sdiff(new String[] {key1, key3}).get());

        // first set is empty
        assertEquals(Set.of(), client.sdiff(new String[] {key3, key1}).get());

        // source key exists, but it is not a set
        assertEquals(OK, client.set(key3, "value").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.sdiff(new String[] {key1, key3}).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void smismember(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();

        assertEquals(2, client.sadd(key1, new String[] {"one", "two"}).get());
        assertArrayEquals(
                new Boolean[] {true, false}, client.smismember(key1, new String[] {"one", "three"}).get());

        // empty set
        assertArrayEquals(
                new Boolean[] {false, false}, client.smismember(key2, new String[] {"one", "three"}).get());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key2, "value").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class, () -> client.smismember(key2, new String[] {"_"}).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void sdiffstore(BaseClient client) {
        String key1 = "{key}-1-" + UUID.randomUUID();
        String key2 = "{key}-2-" + UUID.randomUUID();
        String key3 = "{key}-3-" + UUID.randomUUID();
        String key4 = "{key}-4-" + UUID.randomUUID();
        String key5 = "{key}-5-" + UUID.randomUUID();

        assertEquals(3, client.sadd(key1, new String[] {"a", "b", "c"}).get());
        assertEquals(3, client.sadd(key2, new String[] {"c", "d", "e"}).get());
        assertEquals(3, client.sadd(key4, new String[] {"e", "f", "g"}).get());

        // create new
        assertEquals(2, client.sdiffstore(key3, new String[] {key1, key2}).get());
        assertEquals(Set.of("a", "b"), client.smembers(key3).get());

        // overwrite existing set
        assertEquals(2, client.sdiffstore(key2, new String[] {key3, key2}).get());
        assertEquals(Set.of("a", "b"), client.smembers(key2).get());

        // overwrite source
        assertEquals(3, client.sdiffstore(key1, new String[] {key1, key4}).get());
        assertEquals(Set.of("a", "b", "c"), client.smembers(key1).get());

        // diff with empty set
        assertEquals(3, client.sdiffstore(key1, new String[] {key1, key5}).get());
        assertEquals(Set.of("a", "b", "c"), client.smembers(key1).get());

        // diff empty with non-empty set
        assertEquals(0, client.sdiffstore(key3, new String[] {key5, key1}).get());
        assertEquals(Set.of(), client.smembers(key3).get());

        // source key exists, but it is not a set
        assertEquals(OK, client.set(key5, "value").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class, () -> client.sdiffstore(key1, new String[] {key5}).get());
        assertInstanceOf(RequestException.class, executionException.getCause());

        // overwrite destination - not a set
        assertEquals(1, client.sdiffstore(key5, new String[] {key1, key2}).get());
        assertEquals(Set.of("c"), client.smembers(key5).get());

        // wrong arguments
        executionException =
                assertThrows(ExecutionException.class, () -> client.sdiffstore(key5, new String[0]).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void sinter(BaseClient client) {
        String key1 = "{sinter}-" + UUID.randomUUID();
        String key2 = "{sinter}-" + UUID.randomUUID();
        String key3 = "{sinter}-" + UUID.randomUUID();

        assertEquals(3, client.sadd(key1, new String[] {"a", "b", "c"}).get());
        assertEquals(3, client.sadd(key2, new String[] {"c", "d", "e"}).get());
        assertEquals(Set.of("c"), client.sinter(new String[] {key1, key2}).get());
        assertEquals(0, client.sinter(new String[] {key1, key3}).get().size());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key3, "bar").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.sinter(new String[] {key3}).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void sunionstore(BaseClient client) {
        String key1 = "{key}-1-" + UUID.randomUUID();
        String key2 = "{key}-2-" + UUID.randomUUID();
        String key3 = "{key}-3-" + UUID.randomUUID();
        String key4 = "{key}-4-" + UUID.randomUUID();
        String key5 = "{key}-5-" + UUID.randomUUID();

        assertEquals(3, client.sadd(key1, new String[] {"a", "b", "c"}).get());
        assertEquals(3, client.sadd(key2, new String[] {"c", "d", "e"}).get());
        assertEquals(3, client.sadd(key4, new String[] {"e", "f", "g"}).get());

        // create new
        assertEquals(5, client.sunionstore(key3, new String[] {key1, key2}).get());
        assertEquals(Set.of("a", "b", "c", "d", "e"), client.smembers(key3).get());

        // overwrite existing set
        assertEquals(5, client.sunionstore(key2, new String[] {key3, key2}).get());
        assertEquals(Set.of("a", "b", "c", "d", "e"), client.smembers(key2).get());

        // overwrite source
        assertEquals(6, client.sunionstore(key1, new String[] {key1, key4}).get());
        assertEquals(Set.of("a", "b", "c", "e", "f", "g"), client.smembers(key1).get());

        // source key exists, but it is not a set
        assertEquals(OK, client.set(key5, "value").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class, () -> client.sunionstore(key1, new String[] {key5}).get());
        assertInstanceOf(RequestException.class, executionException.getCause());

        // overwrite destination - not a set
        assertEquals(7, client.sunionstore(key5, new String[] {key1, key2}).get());
        assertEquals(Set.of("a", "b", "c", "d", "e", "f", "g"), client.smembers(key5).get());

        // wrong arguments
        executionException =
                assertThrows(ExecutionException.class, () -> client.sunionstore(key5, new String[0]).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void exists_multiple_keys(BaseClient client) {
        String key1 = "{key}" + UUID.randomUUID();
        String key2 = "{key}" + UUID.randomUUID();
        String value = UUID.randomUUID().toString();

        String setResult = client.set(key1, value).get();
        assertEquals(OK, setResult);
        setResult = client.set(key2, value).get();
        assertEquals(OK, setResult);

        Long existsKeysNum =
                client.exists(new String[] {key1, key2, key1, UUID.randomUUID().toString()}).get();
        assertEquals(3L, existsKeysNum);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void expire_pexpire_ttl_and_expiretime_with_positive_timeout(BaseClient client) {
        String key = UUID.randomUUID().toString();
        assertEquals(OK, client.set(key, "expire_timeout").get());
        assertTrue(client.expire(key, 10L).get());
        assertTrue(client.ttl(key).get() <= 10L);

        // set command clears the timeout.
        assertEquals(OK, client.set(key, "pexpire_timeout").get());
        if (REDIS_VERSION.isLowerThan("7.0.0")) {
            assertTrue(client.pexpire(key, 10000L).get());
        } else {
            assertTrue(client.pexpire(key, 10000L, ExpireOptions.HAS_NO_EXPIRY).get());
        }
        assertTrue(client.ttl(key).get() <= 10L);

        // TTL will be updated to the new value = 15
        if (REDIS_VERSION.isLowerThan("7.0.0")) {
            assertTrue(client.expire(key, 15L).get());
        } else {
            assertTrue(client.expire(key, 15L, ExpireOptions.HAS_EXISTING_EXPIRY).get());
            assertTrue(client.expiretime(key).get() > Instant.now().getEpochSecond());
            assertTrue(client.pexpiretime(key).get() > Instant.now().toEpochMilli());
        }
        assertTrue(client.ttl(key).get() <= 15L);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void expireAt_pexpireAt_and_ttl_with_positive_timeout(BaseClient client) {
        String key = UUID.randomUUID().toString();
        assertEquals(OK, client.set(key, "expireAt_timeout").get());
        assertTrue(client.expireAt(key, Instant.now().getEpochSecond() + 10L).get());
        assertTrue(client.ttl(key).get() <= 10L);

        // extend TTL
        if (REDIS_VERSION.isLowerThan("7.0.0")) {
            assertTrue(client.expireAt(key, Instant.now().getEpochSecond() + 50L).get());
        } else {
            assertTrue(
                    client
                            .expireAt(
                                    key,
                                    Instant.now().getEpochSecond() + 50L,
                                    ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT)
                            .get());
        }
        assertTrue(client.ttl(key).get() <= 50L);

        if (REDIS_VERSION.isLowerThan("7.0.0")) {
            assertTrue(client.pexpireAt(key, Instant.now().toEpochMilli() + 50000L).get());
        } else {
            // set command clears the timeout.
            assertEquals(OK, client.set(key, "pexpireAt_timeout").get());
            assertFalse(
                    client
                            .pexpireAt(
                                    key, Instant.now().toEpochMilli() + 50000L, ExpireOptions.HAS_EXISTING_EXPIRY)
                            .get());
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void expire_pexpire_ttl_and_expiretime_with_timestamp_in_the_past_or_negative_timeout(
            BaseClient client) {
        String key = UUID.randomUUID().toString();

        assertEquals(OK, client.set(key, "expire_with_past_timestamp").get());
        // no timeout set yet
        assertEquals(-1L, client.ttl(key).get());
        if (REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            assertEquals(-1L, client.expiretime(key).get());
            assertEquals(-1L, client.pexpiretime(key).get());
        }

        assertTrue(client.expire(key, -10L).get());
        assertEquals(-2L, client.ttl(key).get());

        assertEquals(OK, client.set(key, "pexpire_with_past_timestamp").get());
        assertTrue(client.pexpire(key, -10000L).get());
        assertEquals(-2L, client.ttl(key).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void expireAt_pexpireAt_ttl_with_timestamp_in_the_past_or_negative_timeout(
            BaseClient client) {
        String key = UUID.randomUUID().toString();

        assertEquals(OK, client.set(key, "expireAt_with_past_timestamp").get());
        // set timeout in the past
        assertTrue(client.expireAt(key, Instant.now().getEpochSecond() - 50L).get());
        assertEquals(-2L, client.ttl(key).get());

        assertEquals(OK, client.set(key, "pexpireAt_with_past_timestamp").get());
        // set timeout in the past
        assertTrue(client.pexpireAt(key, Instant.now().toEpochMilli() - 50000L).get());
        assertEquals(-2L, client.ttl(key).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void expire_pexpire_ttl_and_expiretime_with_non_existing_key(BaseClient client) {
        String key = UUID.randomUUID().toString();

        assertFalse(client.expire(key, 10L).get());
        assertFalse(client.pexpire(key, 10000L).get());

        assertEquals(-2L, client.ttl(key).get());
        if (REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            assertEquals(-2L, client.expiretime(key).get());
            assertEquals(-2L, client.pexpiretime(key).get());
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void expireAt_pexpireAt_and_ttl_with_non_existing_key(BaseClient client) {
        String key = UUID.randomUUID().toString();

        assertFalse(client.expireAt(key, Instant.now().getEpochSecond() + 10L).get());
        assertFalse(client.pexpireAt(key, Instant.now().toEpochMilli() + 10000L).get());

        assertEquals(-2L, client.ttl(key).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void expire_pexpire_and_pttl_with_positive_timeout(BaseClient client) {
        String key = UUID.randomUUID().toString();

        assertEquals(-2L, client.pttl(key).get());

        assertEquals(OK, client.set(key, "expire_timeout").get());
        assertTrue(client.expire(key, 10L).get());
        Long pttlResult = client.pttl(key).get();
        assertTrue(0 <= pttlResult);
        assertTrue(pttlResult <= 10000L);

        assertEquals(OK, client.set(key, "pexpire_timeout").get());
        assertEquals(-1L, client.pttl(key).get());

        assertTrue(client.pexpire(key, 10000L).get());
        pttlResult = client.pttl(key).get();
        assertTrue(0 <= pttlResult);
        assertTrue(pttlResult <= 10000L);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void persist_on_existing_and_non_existing_key(BaseClient client) {
        String key = UUID.randomUUID().toString();

        assertFalse(client.persist(key).get());

        assertEquals(OK, client.set(key, "persist_value").get());
        assertFalse(client.persist(key).get());

        assertTrue(client.expire(key, 10L).get());
        Long persistAmount = client.ttl(key).get();
        assertTrue(0L <= persistAmount && persistAmount <= 10L);
        assertTrue(client.persist(key).get());

        assertEquals(-1L, client.ttl(key).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void invokeScript_test(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();

        try (Script script = new Script("return 'Hello'")) {
            Object response = client.invokeScript(script).get();
            assertEquals("Hello", response);
        }

        try (Script script = new Script("return redis.call('SET', KEYS[1], ARGV[1])")) {
            Object setResponse1 =
                    client
                            .invokeScript(script, ScriptOptions.builder().key(key1).arg("value1").build())
                            .get();
            assertEquals(OK, setResponse1);

            Object setResponse2 =
                    client
                            .invokeScript(script, ScriptOptions.builder().key(key2).arg("value2").build())
                            .get();
            assertEquals(OK, setResponse2);
        }

        try (Script script = new Script("return redis.call('GET', KEYS[1])")) {
            Object getResponse1 =
                    client.invokeScript(script, ScriptOptions.builder().key(key1).build()).get();
            assertEquals("value1", getResponse1);

            Object getResponse2 =
                    client.invokeScript(script, ScriptOptions.builder().key(key2).build()).get();
            assertEquals("value2", getResponse2);
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zadd_and_zaddIncr(BaseClient client) {
        String key = UUID.randomUUID().toString();
        Map<String, Double> membersScores = Map.of("one", 1.0, "two", 2.0, "three", 3.0);

        assertEquals(3, client.zadd(key, membersScores).get());
        assertEquals(3.0, client.zaddIncr(key, "one", 2.0).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zadd_and_zaddIncr_wrong_type(BaseClient client) {
        assertEquals(OK, client.set("foo", "bar").get());
        Map<String, Double> membersScores = Map.of("one", 1.0, "two", 2.0, "three", 3.0);

        ExecutionException executionExceptionZadd =
                assertThrows(ExecutionException.class, () -> client.zadd("foo", membersScores).get());
        assertTrue(executionExceptionZadd.getCause() instanceof RequestException);

        ExecutionException executionExceptionZaddIncr =
                assertThrows(ExecutionException.class, () -> client.zaddIncr("foo", "one", 2.0).get());
        assertTrue(executionExceptionZaddIncr.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zadd_and_zaddIncr_with_NX_XX(BaseClient client) {
        String key = UUID.randomUUID().toString();
        Map<String, Double> membersScores = Map.of("one", 1.0, "two", 2.0, "three", 3.0);

        ZAddOptions onlyIfExistsOptions =
                ZAddOptions.builder()
                        .conditionalChange(ZAddOptions.ConditionalChange.ONLY_IF_EXISTS)
                        .build();
        ZAddOptions onlyIfDoesNotExistOptions =
                ZAddOptions.builder()
                        .conditionalChange(ZAddOptions.ConditionalChange.ONLY_IF_DOES_NOT_EXIST)
                        .build();

        assertEquals(0, client.zadd(key, membersScores, onlyIfExistsOptions).get());
        assertEquals(3, client.zadd(key, membersScores, onlyIfDoesNotExistOptions).get());
        assertNull(client.zaddIncr(key, "one", 5, onlyIfDoesNotExistOptions).get());
        assertEquals(6, client.zaddIncr(key, "one", 5, onlyIfExistsOptions).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zadd_and_zaddIncr_with_GT_LT(BaseClient client) {
        String key = UUID.randomUUID().toString();
        Map<String, Double> membersScores = new LinkedHashMap<>();
        membersScores.put("one", -3.0);
        membersScores.put("two", 2.0);
        membersScores.put("three", 3.0);

        assertEquals(3, client.zadd(key, membersScores).get());
        membersScores.put("one", 10.0);

        ZAddOptions scoreGreaterThanOptions =
                ZAddOptions.builder()
                        .updateOptions(ZAddOptions.UpdateOptions.SCORE_GREATER_THAN_CURRENT)
                        .build();
        ZAddOptions scoreLessThanOptions =
                ZAddOptions.builder()
                        .updateOptions(ZAddOptions.UpdateOptions.SCORE_LESS_THAN_CURRENT)
                        .build();

        assertEquals(1, client.zadd(key, membersScores, scoreGreaterThanOptions, true).get());
        assertEquals(0, client.zadd(key, membersScores, scoreLessThanOptions, true).get());
        assertEquals(7, client.zaddIncr(key, "one", -3, scoreLessThanOptions).get());
        assertNull(client.zaddIncr(key, "one", -3, scoreGreaterThanOptions).get());
    }

    // TODO move to another class
    @Test
    public void zadd_illegal_arguments() {
        ZAddOptions existsGreaterThanOptions =
                ZAddOptions.builder()
                        .conditionalChange(ZAddOptions.ConditionalChange.ONLY_IF_DOES_NOT_EXIST)
                        .updateOptions(ZAddOptions.UpdateOptions.SCORE_GREATER_THAN_CURRENT)
                        .build();
        assertThrows(IllegalArgumentException.class, existsGreaterThanOptions::toArgs);
        ZAddOptions existsLessThanOptions =
                ZAddOptions.builder()
                        .conditionalChange(ZAddOptions.ConditionalChange.ONLY_IF_DOES_NOT_EXIST)
                        .updateOptions(ZAddOptions.UpdateOptions.SCORE_LESS_THAN_CURRENT)
                        .build();
        assertThrows(IllegalArgumentException.class, existsLessThanOptions::toArgs);
        ZAddOptions options =
                ZAddOptions.builder()
                        .conditionalChange(ZAddOptions.ConditionalChange.ONLY_IF_DOES_NOT_EXIST)
                        .build();
        options.toArgs();
        options =
                ZAddOptions.builder()
                        .conditionalChange(ZAddOptions.ConditionalChange.ONLY_IF_EXISTS)
                        .updateOptions(ZAddOptions.UpdateOptions.SCORE_GREATER_THAN_CURRENT)
                        .build();
        options.toArgs();
        options =
                ZAddOptions.builder()
                        .conditionalChange(ZAddOptions.ConditionalChange.ONLY_IF_EXISTS)
                        .updateOptions(ZAddOptions.UpdateOptions.SCORE_LESS_THAN_CURRENT)
                        .build();
        options.toArgs();
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zrem(BaseClient client) {
        String key = UUID.randomUUID().toString();
        Map<String, Double> membersScores = Map.of("one", 1.0, "two", 2.0, "three", 3.0);
        assertEquals(3, client.zadd(key, membersScores).get());
        assertEquals(1, client.zrem(key, new String[] {"one"}).get());
        assertEquals(2, client.zrem(key, new String[] {"one", "two", "three"}).get());
        assertEquals(0, client.zrem("non_existing_set", new String[] {"member"}).get());

        // Key exists, but it is not a set
        assertEquals(OK, client.set("foo", "bar").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class, () -> client.zrem("foo", new String[] {"bar"}).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zcard(BaseClient client) {
        String key = UUID.randomUUID().toString();
        Map<String, Double> membersScores = Map.of("one", 1.0, "two", 2.0, "three", 3.0);
        assertEquals(3, client.zadd(key, membersScores).get());
        assertEquals(3, client.zcard(key).get());
        assertEquals(1, client.zrem(key, new String[] {"one"}).get());
        assertEquals(2, client.zcard(key).get());

        assertEquals(0, client.zcard("nonExistentSet").get());

        // Key exists, but it is not a set
        assertEquals(OK, client.set("foo", "bar").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.zcard("foo").get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zpopmin(BaseClient client) {
        String key = UUID.randomUUID().toString();
        Map<String, Double> membersScores = Map.of("a", 1.0, "b", 2.0, "c", 3.0);
        assertEquals(3, client.zadd(key, membersScores).get());
        assertEquals(Map.of("a", 1.0), client.zpopmin(key).get());
        assertEquals(Map.of("b", 2.0, "c", 3.0), client.zpopmin(key, 3).get());
        assertTrue(client.zpopmin(key).get().isEmpty());
        assertTrue(client.zpopmin("non_existing_key").get().isEmpty());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key, "value").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.zpopmin(key).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void bzpopmin(BaseClient client) {
        String key1 = "{test}-1-" + UUID.randomUUID();
        String key2 = "{test}-2-" + UUID.randomUUID();
        String key3 = "{test}-3-" + UUID.randomUUID();

        assertEquals(2, client.zadd(key1, Map.of("a", 1.0, "b", 1.5)).get());
        assertEquals(1, client.zadd(key2, Map.of("c", 2.0)).get());
        assertArrayEquals(
                new Object[] {key1, "a", 1.0}, client.bzpopmin(new String[] {key1, key2}, .5).get());

        // nothing popped out - key does not exist
        assertNull(
                client
                        .bzpopmin(new String[] {key3}, REDIS_VERSION.isLowerThan("7.0.0") ? 1. : 0.001)
                        .get());

        // pops from the second key
        assertArrayEquals(
                new Object[] {key2, "c", 2.0}, client.bzpopmin(new String[] {key3, key2}, .5).get());

        // Key exists, but it is not a sorted set
        assertEquals(OK, client.set(key3, "value").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class, () -> client.bzpopmin(new String[] {key3}, .5).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void bzpopmin_timeout_check(BaseClient client) {
        String key = UUID.randomUUID().toString();
        // create new client with default request timeout (250 millis)
        try (var testClient =
                client instanceof RedisClient
                        ? RedisClient.CreateClient(commonClientConfig().build()).get()
                        : RedisClusterClient.CreateClient(commonClusterClientConfig().build()).get()) {

            // ensure that commands doesn't time out even if timeout > request timeout
            assertNull(testClient.bzpopmin(new String[] {key}, 1).get());

            // with 0 timeout (no timeout) should never time out,
            // but we wrap the test with timeout to avoid test failing or stuck forever
            assertThrows(
                    TimeoutException.class, // <- future timeout, not command timeout
                    () -> testClient.bzpopmin(new String[] {key}, 0).get(3, TimeUnit.SECONDS));
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zpopmax(BaseClient client) {
        String key = UUID.randomUUID().toString();
        Map<String, Double> membersScores = Map.of("a", 1.0, "b", 2.0, "c", 3.0);
        assertEquals(3, client.zadd(key, membersScores).get());
        assertEquals(Map.of("c", 3.0), client.zpopmax(key).get());
        assertEquals(Map.of("b", 2.0, "a", 1.0), client.zpopmax(key, 3).get());
        assertTrue(client.zpopmax(key).get().isEmpty());
        assertTrue(client.zpopmax("non_existing_key").get().isEmpty());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key, "value").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.zpopmax(key).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void bzpopmax(BaseClient client) {
        String key1 = "{test}-1-" + UUID.randomUUID();
        String key2 = "{test}-2-" + UUID.randomUUID();
        String key3 = "{test}-3-" + UUID.randomUUID();

        assertEquals(2, client.zadd(key1, Map.of("a", 1.0, "b", 1.5)).get());
        assertEquals(1, client.zadd(key2, Map.of("c", 2.0)).get());
        assertArrayEquals(
                new Object[] {key1, "b", 1.5}, client.bzpopmax(new String[] {key1, key2}, .5).get());

        // nothing popped out - key does not exist
        assertNull(
                client
                        .bzpopmax(new String[] {key3}, REDIS_VERSION.isLowerThan("7.0.0") ? 1. : 0.001)
                        .get());

        // pops from the second key
        assertArrayEquals(
                new Object[] {key2, "c", 2.0}, client.bzpopmax(new String[] {key3, key2}, .5).get());

        // Key exists, but it is not a sorted set
        assertEquals(OK, client.set(key3, "value").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class, () -> client.bzpopmax(new String[] {key3}, .5).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void bzpopmax_timeout_check(BaseClient client) {
        String key = UUID.randomUUID().toString();
        // create new client with default request timeout (250 millis)
        try (var testClient =
                client instanceof RedisClient
                        ? RedisClient.CreateClient(commonClientConfig().build()).get()
                        : RedisClusterClient.CreateClient(commonClusterClientConfig().build()).get()) {

            // ensure that commands doesn't time out even if timeout > request timeout
            assertNull(testClient.bzpopmax(new String[] {key}, 1).get());

            // with 0 timeout (no timeout) should never time out,
            // but we wrap the test with timeout to avoid test failing or stuck forever
            assertThrows(
                    TimeoutException.class, // <- future timeout, not command timeout
                    () -> testClient.bzpopmax(new String[] {key}, 0).get(3, TimeUnit.SECONDS));
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zscore(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();

        Map<String, Double> membersScores = Map.of("one", 1.0, "two", 2.0, "three", 3.0);
        assertEquals(3, client.zadd(key1, membersScores).get());
        assertEquals(1.0, client.zscore(key1, "one").get());
        assertNull(client.zscore(key1, "non_existing_member").get());
        assertNull(client.zscore("non_existing_key", "non_existing_member").get());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key2, "bar").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.zscore(key2, "one").get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zrank(BaseClient client) {
        String key = UUID.randomUUID().toString();
        Map<String, Double> membersScores = Map.of("one", 1.5, "two", 2.0, "three", 3.0);
        assertEquals(3, client.zadd(key, membersScores).get());
        assertEquals(0, client.zrank(key, "one").get());

        if (REDIS_VERSION.isGreaterThanOrEqualTo("7.2.0")) {
            assertArrayEquals(new Object[] {0L, 1.5}, client.zrankWithScore(key, "one").get());
            assertNull(client.zrankWithScore(key, "nonExistingMember").get());
            assertNull(client.zrankWithScore("nonExistingKey", "nonExistingMember").get());
        }
        assertNull(client.zrank(key, "nonExistingMember").get());
        assertNull(client.zrank("nonExistingKey", "nonExistingMember").get());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key, "value").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.zrank(key, "one").get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zrevrank(BaseClient client) {
        String key = UUID.randomUUID().toString();
        Map<String, Double> membersScores = Map.of("one", 1.5, "two", 2.0, "three", 3.0);
        assertEquals(3, client.zadd(key, membersScores).get());
        assertEquals(0, client.zrevrank(key, "three").get());

        if (REDIS_VERSION.isGreaterThanOrEqualTo("7.2.0")) {
            assertArrayEquals(new Object[] {2L, 1.5}, client.zrevrankWithScore(key, "one").get());
            assertNull(client.zrevrankWithScore(key, "nonExistingMember").get());
            assertNull(client.zrevrankWithScore("nonExistingKey", "nonExistingMember").get());
        }
        assertNull(client.zrevrank(key, "nonExistingMember").get());
        assertNull(client.zrevrank("nonExistingKey", "nonExistingMember").get());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key, "value").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.zrevrank(key, "one").get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zdiff(BaseClient client) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("6.2.0"), "This feature added in redis 6.2.0");

        String key1 = "{testKey}:1-" + UUID.randomUUID();
        String key2 = "{testKey}:2-" + UUID.randomUUID();
        String key3 = "{testKey}:3-" + UUID.randomUUID();
        String nonExistentKey = "{testKey}:4-" + UUID.randomUUID();

        Map<String, Double> membersScores1 = Map.of("one", 1.0, "two", 2.0, "three", 3.0);
        Map<String, Double> membersScores2 = Map.of("two", 2.0);
        Map<String, Double> membersScores3 = Map.of("one", 0.5, "two", 2.0, "three", 3.0, "four", 4.0);

        assertEquals(3, client.zadd(key1, membersScores1).get());
        assertEquals(1, client.zadd(key2, membersScores2).get());
        assertEquals(4, client.zadd(key3, membersScores3).get());

        assertArrayEquals(new String[] {"one", "three"}, client.zdiff(new String[] {key1, key2}).get());
        assertArrayEquals(new String[] {}, client.zdiff(new String[] {key1, key3}).get());
        assertArrayEquals(new String[] {}, client.zdiff(new String[] {nonExistentKey, key3}).get());

        assertEquals(
                Map.of("one", 1.0, "three", 3.0), client.zdiffWithScores(new String[] {key1, key2}).get());
        assertEquals(Map.of(), client.zdiffWithScores(new String[] {key1, key3}).get());
        assertTrue(client.zdiffWithScores(new String[] {nonExistentKey, key3}).get().isEmpty());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(nonExistentKey, "bar").get());

        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.zdiff(new String[] {nonExistentKey, key2}).get());
        assertTrue(executionException.getCause() instanceof RequestException);

        executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.zdiffWithScores(new String[] {nonExistentKey, key2}).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zmscore(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        Map<String, Double> membersScores = Map.of("one", 1.0, "two", 2.0, "three", 3.0);
        assertEquals(3, client.zadd(key1, membersScores).get());
        assertArrayEquals(
                new Double[] {1.0, 2.0, 3.0},
                client.zmscore(key1, new String[] {"one", "two", "three"}).get());
        assertArrayEquals(
                new Double[] {1.0, null, null, 3.0},
                client
                        .zmscore(key1, new String[] {"one", "nonExistentMember", "nonExistentMember", "three"})
                        .get());
        assertArrayEquals(
                new Double[] {null}, client.zmscore("nonExistentKey", new String[] {"one"}).get());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key2, "bar").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class, () -> client.zmscore(key2, new String[] {"one"}).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zdiffstore(BaseClient client) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("6.2.0"), "This feature added in redis 6.2.0");

        String key1 = "{testKey}:1-" + UUID.randomUUID();
        String key2 = "{testKey}:2-" + UUID.randomUUID();
        String key3 = "{testKey}:3-" + UUID.randomUUID();
        String key4 = "{testKey}:4-" + UUID.randomUUID();
        String key5 = "{testKey}:5-" + UUID.randomUUID();

        Map<String, Double> membersScores1 = Map.of("one", 1.0, "two", 2.0, "three", 3.0);
        Map<String, Double> membersScores2 = Map.of("two", 2.0);
        Map<String, Double> membersScores3 = Map.of("one", 0.5, "two", 2.0, "three", 3.0, "four", 4.0);

        assertEquals(3, client.zadd(key1, membersScores1).get());
        assertEquals(1, client.zadd(key2, membersScores2).get());
        assertEquals(4, client.zadd(key3, membersScores3).get());

        assertEquals(2, client.zdiffstore(key4, new String[] {key1, key2}).get());
        assertEquals(
                Map.of("one", 1.0, "three", 3.0),
                client.zrangeWithScores(key4, new RangeByIndex(0, -1)).get());

        assertEquals(1, client.zdiffstore(key4, new String[] {key3, key2, key1}).get());
        assertEquals(Map.of("four", 4.0), client.zrangeWithScores(key4, new RangeByIndex(0, -1)).get());

        assertEquals(0, client.zdiffstore(key4, new String[] {key1, key3}).get());
        assertTrue(client.zrangeWithScores(key4, new RangeByIndex(0, -1)).get().isEmpty());

        // Non-Existing key
        assertEquals(0, client.zdiffstore(key4, new String[] {key5, key1}).get());
        assertTrue(client.zrangeWithScores(key4, new RangeByIndex(0, -1)).get().isEmpty());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key5, "bar").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.zdiffstore(key4, new String[] {key5, key1}).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zcount(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        Map<String, Double> membersScores = Map.of("one", 1.0, "two", 2.0, "three", 3.0);
        assertEquals(3, client.zadd(key1, membersScores).get());

        // In range negative to positive infinity.
        assertEquals(3, client.zcount(key1, NEGATIVE_INFINITY, POSITIVE_INFINITY).get());
        assertEquals(
                3,
                client
                        .zcount(
                                key1,
                                new ScoreBoundary(Double.NEGATIVE_INFINITY),
                                new ScoreBoundary(Double.POSITIVE_INFINITY))
                        .get());
        // In range 1 (exclusive) to 3 (inclusive)
        assertEquals(
                2, client.zcount(key1, new ScoreBoundary(1, false), new ScoreBoundary(3, true)).get());
        // In range negative infinity to 3 (inclusive)
        assertEquals(3, client.zcount(key1, NEGATIVE_INFINITY, new ScoreBoundary(3, true)).get());
        // Incorrect range start > end
        assertEquals(0, client.zcount(key1, POSITIVE_INFINITY, new ScoreBoundary(3, true)).get());
        // Non-existing key
        assertEquals(0, client.zcount("non_existing_key", NEGATIVE_INFINITY, POSITIVE_INFINITY).get());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key2, "value").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.zcount(key2, NEGATIVE_INFINITY, POSITIVE_INFINITY).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zremrangebyrank(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        RangeByIndex query = new RangeByIndex(0, -1);
        Map<String, Double> membersScores = Map.of("one", 1.0, "two", 2.0, "three", 3.0);
        assertEquals(3, client.zadd(key1, membersScores).get());

        // Incorrect range start > stop
        assertEquals(0, client.zremrangebyrank(key1, 2, 1).get());
        assertEquals(
                Map.of("one", 1.0, "two", 2.0, "three", 3.0), client.zrangeWithScores(key1, query).get());

        assertEquals(2, client.zremrangebyrank(key1, 0, 1).get());
        assertEquals(Map.of("three", 3.0), client.zrangeWithScores(key1, query).get());

        assertEquals(1, client.zremrangebyrank(key1, 0, 10).get());
        assertTrue(client.zrangeWithScores(key1, query).get().isEmpty());

        // Non Existing Key
        assertEquals(0, client.zremrangebyrank(key2, 0, 10).get());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key2, "value").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.zremrangebyrank(key2, 2, 1).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zremrangebylex(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        RangeByIndex query = new RangeByIndex(0, -1);
        Map<String, Double> membersScores = Map.of("a", 1.0, "b", 2.0, "c", 3.0, "d", 4.0);
        assertEquals(4, client.zadd(key1, membersScores).get());

        assertEquals(
                2, client.zremrangebylex(key1, new LexBoundary("a", false), new LexBoundary("c")).get());
        assertEquals(Map.of("a", 1.0, "d", 4.0), client.zrangeWithScores(key1, query).get());

        assertEquals(
                1, client.zremrangebylex(key1, new LexBoundary("d"), InfLexBound.POSITIVE_INFINITY).get());
        assertEquals(Map.of("a", 1.0), client.zrangeWithScores(key1, query).get());

        // MinLex > MaxLex
        assertEquals(
                0, client.zremrangebylex(key1, new LexBoundary("a"), InfLexBound.NEGATIVE_INFINITY).get());
        assertEquals(Map.of("a", 1.0), client.zrangeWithScores(key1, query).get());

        // Non Existing Key
        assertEquals(
                0,
                client
                        .zremrangebylex(key2, InfLexBound.NEGATIVE_INFINITY, InfLexBound.POSITIVE_INFINITY)
                        .get());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key2, "value").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.zremrangebylex(key2, new LexBoundary("a"), new LexBoundary("c")).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zremrangebyscore(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        RangeByIndex query = new RangeByIndex(0, -1);
        Map<String, Double> membersScores = Map.of("one", 1.0, "two", 2.0, "three", 3.0, "four", 4.0);
        assertEquals(4, client.zadd(key1, membersScores).get());

        // MinScore > MaxScore
        assertEquals(
                0,
                client.zremrangebyscore(key1, new ScoreBoundary(1), InfScoreBound.NEGATIVE_INFINITY).get());
        assertEquals(
                Map.of("one", 1.0, "two", 2.0, "three", 3.0, "four", 4.0),
                client.zrangeWithScores(key1, query).get());

        assertEquals(
                2, client.zremrangebyscore(key1, new ScoreBoundary(1, false), new ScoreBoundary(3)).get());
        assertEquals(Map.of("one", 1.0, "four", 4.0), client.zrangeWithScores(key1, query).get());

        assertEquals(
                1,
                client.zremrangebyscore(key1, new ScoreBoundary(4), InfScoreBound.POSITIVE_INFINITY).get());
        assertEquals(Map.of("one", 1.0), client.zrangeWithScores(key1, query).get());

        // Non Existing Key
        assertEquals(
                0,
                client
                        .zremrangebyscore(
                                key2, InfScoreBound.NEGATIVE_INFINITY, InfScoreBound.POSITIVE_INFINITY)
                        .get());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key2, "value").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.zremrangebyscore(key2, new ScoreBoundary(1), new ScoreBoundary(2)).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zlexcount(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        Map<String, Double> membersScores = Map.of("a", 1.0, "b", 2.0, "c", 3.0);
        assertEquals(3, client.zadd(key1, membersScores).get());

        // In range negative to positive infinity.
        assertEquals(
                3,
                client.zlexcount(key1, InfLexBound.NEGATIVE_INFINITY, InfLexBound.POSITIVE_INFINITY).get());

        // In range a (exclusive) to c (inclusive)
        assertEquals(
                2, client.zlexcount(key1, new LexBoundary("a", false), new LexBoundary("c", true)).get());

        // In range negative infinity to c (inclusive)
        assertEquals(
                3, client.zlexcount(key1, InfLexBound.NEGATIVE_INFINITY, new LexBoundary("c", true)).get());

        // Incorrect range start > end
        assertEquals(
                0, client.zlexcount(key1, InfLexBound.POSITIVE_INFINITY, new LexBoundary("c", true)).get());

        // Non-existing key
        assertEquals(
                0,
                client
                        .zlexcount(
                                "non_existing_key", InfLexBound.NEGATIVE_INFINITY, InfLexBound.POSITIVE_INFINITY)
                        .get());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key2, "value").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .zlexcount(key2, InfLexBound.NEGATIVE_INFINITY, InfLexBound.POSITIVE_INFINITY)
                                        .get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zrangestore_by_index(BaseClient client) {
        String key = "{testKey}:" + UUID.randomUUID();
        String destination = "{testKey}:" + UUID.randomUUID();
        String source = "{testKey}:" + UUID.randomUUID();
        Map<String, Double> membersScores = Map.of("one", 1.0, "two", 2.0, "three", 3.0);
        assertEquals(3, client.zadd(source, membersScores).get());

        // Full range.
        assertEquals(3, client.zrangestore(destination, source, new RangeByIndex(0, -1)).get());
        assertEquals(
                Map.of("one", 1.0, "two", 2.0, "three", 3.0),
                client.zrangeWithScores(destination, new RangeByIndex(0, -1)).get());

        // Range from rank 0 to 1. In descending order of scores.
        assertEquals(2, client.zrangestore(destination, source, new RangeByIndex(0, 1), true).get());
        assertEquals(
                Map.of("three", 3.0, "two", 2.0),
                client.zrangeWithScores(destination, new RangeByIndex(0, -1)).get());

        // Incorrect range as start > stop.
        assertEquals(0, client.zrangestore(destination, source, new RangeByIndex(3, 1)).get());
        assertEquals(Map.of(), client.zrangeWithScores(destination, new RangeByIndex(0, -1)).get());

        // Non-existing source.
        assertEquals(0, client.zrangestore(destination, key, new RangeByIndex(0, -1)).get());
        assertEquals(Map.of(), client.zrangeWithScores(destination, new RangeByIndex(0, -1)).get());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key, "value").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.zrangestore(destination, key, new RangeByIndex(3, 1)).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zrangestore_by_score(BaseClient client) {
        String key = "{testKey}:" + UUID.randomUUID();
        String destination = "{testKey}:" + UUID.randomUUID();
        String source = "{testKey}:" + UUID.randomUUID();
        Map<String, Double> membersScores = Map.of("one", 1.0, "two", 2.0, "three", 3.0);
        assertEquals(3, client.zadd(source, membersScores).get());

        // Range from negative infinity to 3 (exclusive).
        RangeByScore query =
                new RangeByScore(InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(3, false));
        assertEquals(2, client.zrangestore(destination, source, query).get());
        assertEquals(
                Map.of("one", 1.0, "two", 2.0),
                client.zrangeWithScores(destination, new RangeByIndex(0, -1)).get());

        // Range from 1 (inclusive) to positive infinity.
        query = new RangeByScore(new ScoreBoundary(1), InfScoreBound.POSITIVE_INFINITY);
        assertEquals(3, client.zrangestore(destination, source, query).get());
        assertEquals(
                Map.of("one", 1.0, "two", 2.0, "three", 3.0),
                client.zrangeWithScores(destination, new RangeByIndex(0, -1)).get());

        // Range from negative to positive infinity. Limited to ranks 1 to 2.
        query =
                new RangeByScore(
                        InfScoreBound.NEGATIVE_INFINITY, InfScoreBound.POSITIVE_INFINITY, new Limit(1, 2));
        assertEquals(2, client.zrangestore(destination, source, query).get());
        assertEquals(
                Map.of("two", 2.0, "three", 3.0),
                client.zrangeWithScores(destination, new RangeByIndex(0, -1)).get());

        // Range from positive to negative infinity with rev set to true. Limited to ranks 1 to 2.
        query =
                new RangeByScore(
                        InfScoreBound.POSITIVE_INFINITY, InfScoreBound.NEGATIVE_INFINITY, new Limit(1, 2));
        assertEquals(2, client.zrangestore(destination, source, query, true).get());
        assertEquals(
                Map.of("two", 2.0, "one", 1.0),
                client.zrangeWithScores(destination, new RangeByIndex(0, -1)).get());

        // Incorrect range as start > stop.
        query = new RangeByScore(new ScoreBoundary(3, false), InfScoreBound.NEGATIVE_INFINITY);
        assertEquals(0, client.zrangestore(destination, source, query).get());
        assertEquals(Map.of(), client.zrangeWithScores(destination, new RangeByIndex(0, -1)).get());

        // Non-existent source.
        query = new RangeByScore(InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(3, false));
        assertEquals(0, client.zrangestore(destination, key, query).get());
        assertEquals(Map.of(), client.zrangeWithScores(destination, new RangeByIndex(0, -1)).get());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key, "value").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .zrangestore(
                                                destination,
                                                key,
                                                new RangeByScore(new ScoreBoundary(0), new ScoreBoundary(3)))
                                        .get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zrangestore_by_lex(BaseClient client) {
        String key = "{testKey}:" + UUID.randomUUID();
        String destination = "{testKey}:" + UUID.randomUUID();
        String source = "{testKey}:" + UUID.randomUUID();
        Map<String, Double> membersScores = Map.of("a", 1.0, "b", 2.0, "c", 3.0);
        assertEquals(3, client.zadd(source, membersScores).get());

        // Range from negative infinity to "c" (exclusive).
        RangeByLex query = new RangeByLex(InfLexBound.NEGATIVE_INFINITY, new LexBoundary("c", false));
        assertEquals(2, client.zrangestore(destination, source, query).get());
        assertEquals(
                Map.of("a", 1.0, "b", 2.0),
                client.zrangeWithScores(destination, new RangeByIndex(0, -1)).get());

        // Range from "a" (inclusive) to positive infinity.
        query = new RangeByLex(new LexBoundary("a"), InfLexBound.POSITIVE_INFINITY);
        assertEquals(3, client.zrangestore(destination, source, query).get());
        assertEquals(
                Map.of("a", 1.0, "b", 2.0, "c", 3.0),
                client.zrangeWithScores(destination, new RangeByIndex(0, -1)).get());

        // Range from negative to positive infinity. Limited to ranks 1 to 2.
        query =
                new RangeByLex(
                        InfLexBound.NEGATIVE_INFINITY, InfLexBound.POSITIVE_INFINITY, new Limit(1, 2));
        assertEquals(2, client.zrangestore(destination, source, query).get());
        assertEquals(
                Map.of("b", 2.0, "c", 3.0),
                client.zrangeWithScores(destination, new RangeByIndex(0, -1)).get());

        // Range from positive to negative infinity with rev set to true. Limited to ranks 1 to 2.
        query =
                new RangeByLex(
                        InfLexBound.POSITIVE_INFINITY, InfLexBound.NEGATIVE_INFINITY, new Limit(1, 2));
        assertEquals(2, client.zrangestore(destination, source, query, true).get());
        assertEquals(
                Map.of("b", 2.0, "a", 1.0),
                client.zrangeWithScores(destination, new RangeByIndex(0, -1)).get());

        // Non-existent source.
        query = new RangeByLex(InfLexBound.NEGATIVE_INFINITY, new LexBoundary("c", false));
        assertEquals(0, client.zrangestore(destination, key, query).get());
        assertEquals(Map.of(), client.zrangeWithScores(destination, new RangeByIndex(0, -1)).get());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key, "value").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .zrangestore(
                                                destination,
                                                key,
                                                new RangeByLex(new LexBoundary("a"), new LexBoundary("c")))
                                        .get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zunionstore(BaseClient client) {
        String key1 = "{testKey}:1-" + UUID.randomUUID();
        String key2 = "{testKey}:2-" + UUID.randomUUID();
        String key3 = "{testKey}:3-" + UUID.randomUUID();
        String key4 = "{testKey}:4-" + UUID.randomUUID();
        RangeByIndex query = new RangeByIndex(0, -1);
        Map<String, Double> membersScores1 = Map.of("one", 1.0, "two", 2.0);
        Map<String, Double> membersScores2 = Map.of("two", 2.5, "three", 3.0);

        assertEquals(2, client.zadd(key1, membersScores1).get());
        assertEquals(2, client.zadd(key2, membersScores2).get());

        assertEquals(3, client.zunionstore(key3, new KeyArray(new String[] {key1, key2})).get());
        assertEquals(
                Map.of("one", 1.0, "two", 4.5, "three", 3.0), client.zrangeWithScores(key3, query).get());

        // Union results are aggregated by the max score of elements
        assertEquals(
                3, client.zunionstore(key3, new KeyArray(new String[] {key1, key2}), Aggregate.MAX).get());
        assertEquals(
                Map.of("one", 1.0, "two", 2.5, "three", 3.0), client.zrangeWithScores(key3, query).get());

        // Union results are aggregated by the min score of elements
        assertEquals(
                3, client.zunionstore(key3, new KeyArray(new String[] {key1, key2}), Aggregate.MIN).get());
        assertEquals(
                Map.of("one", 1.0, "two", 2.0, "three", 3.0), client.zrangeWithScores(key3, query).get());

        // Union results are aggregated by the sum of the scores of elements
        assertEquals(
                3, client.zunionstore(key3, new KeyArray(new String[] {key1, key2}), Aggregate.SUM).get());
        assertEquals(
                Map.of("one", 1.0, "two", 4.5, "three", 3.0), client.zrangeWithScores(key3, query).get());

        // Scores are multiplied by 2.0 for key1 and key2 during aggregation.
        assertEquals(
                3,
                client
                        .zunionstore(key3, new WeightedKeys(List.of(Pair.of(key1, 2.0), Pair.of(key2, 2.0))))
                        .get());
        assertEquals(
                Map.of("one", 2.0, "two", 9.0, "three", 6.0), client.zrangeWithScores(key3, query).get());

        // Union results are aggregated by the maximum score, with scores for key1 multiplied by 1.0 and
        // for key2 by 2.0.
        assertEquals(
                3,
                client
                        .zunionstore(
                                key3,
                                new WeightedKeys(List.of(Pair.of(key1, 1.0), Pair.of(key2, 2.0))),
                                Aggregate.MAX)
                        .get());
        assertEquals(
                Map.of("one", 1.0, "two", 5.0, "three", 6.0), client.zrangeWithScores(key3, query).get());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key4, "value").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.zunionstore(key3, new KeyArray(new String[] {key4, key2})).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zunion(BaseClient client) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("6.2.0"), "This feature added in redis 6.2.0");

        String key1 = "{testKey}:1-" + UUID.randomUUID();
        String key2 = "{testKey}:2-" + UUID.randomUUID();
        String key3 = "{testKey}:3-" + UUID.randomUUID();
        Map<String, Double> membersScores1 = Map.of("one", 1.0, "two", 2.0);
        Map<String, Double> membersScores2 = Map.of("two", 3.5, "three", 3.0);

        assertEquals(2, client.zadd(key1, membersScores1).get());
        assertEquals(2, client.zadd(key2, membersScores2).get());

        // Union results are aggregated by the sum of the scores of elements by default
        assertArrayEquals(
                new String[] {"one", "three", "two"},
                client.zunion(new KeyArray(new String[] {key1, key2})).get());
        assertEquals(
                Map.of("one", 1.0, "three", 3.0, "two", 5.5),
                client.zunionWithScores(new KeyArray(new String[] {key1, key2})).get());

        // Union results are aggregated by the max score of elements
        assertEquals(
                Map.of("one", 1.0, "three", 3.0, "two", 3.5),
                client.zunionWithScores(new KeyArray(new String[] {key1, key2}), Aggregate.MAX).get());

        // Union results are aggregated by the min score of elements
        assertEquals(
                Map.of("one", 1.0, "two", 2.0, "three", 3.0),
                client.zunionWithScores(new KeyArray(new String[] {key1, key2}), Aggregate.MIN).get());

        // Union results are aggregated by the sum of the scores of elements
        assertEquals(
                Map.of("one", 1.0, "three", 3.0, "two", 5.5),
                client.zunionWithScores(new KeyArray(new String[] {key1, key2}), Aggregate.SUM).get());

        // Scores are multiplied by 2.0 for key1 and key2 during aggregation.
        assertEquals(
                Map.of("one", 2.0, "three", 6.0, "two", 11.0),
                client
                        .zunionWithScores(new WeightedKeys(List.of(Pair.of(key1, 2.0), Pair.of(key2, 2.0))))
                        .get());

        // Union results are aggregated by the minimum score, with scores for key1 multiplied by 1.0 and
        // for key2 by -2.0.
        assertEquals(
                Map.of("two", -7.0, "three", -6.0, "one", 1.0),
                client
                        .zunionWithScores(
                                new WeightedKeys(List.of(Pair.of(key1, 1.0), Pair.of(key2, -2.0))), Aggregate.MIN)
                        .get());

        // Non Existing Key
        assertArrayEquals(
                new String[] {"one", "two"}, client.zunion(new KeyArray(new String[] {key1, key3})).get());
        assertEquals(
                Map.of("one", 1.0, "two", 2.0),
                client.zunionWithScores(new KeyArray(new String[] {key1, key3})).get());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key3, "value").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.zunion(new KeyArray(new String[] {key1, key3})).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zinter(BaseClient client) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("6.2.0"), "This feature added in redis 6.2.0");

        String key1 = "{testKey}:1-" + UUID.randomUUID();
        String key2 = "{testKey}:2-" + UUID.randomUUID();
        String key3 = "{testKey}:3-" + UUID.randomUUID();
        Map<String, Double> membersScores1 = Map.of("one", 1.0, "two", 2.0);
        Map<String, Double> membersScores2 = Map.of("two", 3.5, "three", 3.0);

        assertEquals(2, client.zadd(key1, membersScores1).get());
        assertEquals(2, client.zadd(key2, membersScores2).get());

        // Intersection results are aggregated by the sum of the scores of elements by default
        assertArrayEquals(
                new String[] {"two"}, client.zinter(new KeyArray(new String[] {key1, key2})).get());
        assertEquals(
                Map.of("two", 5.5), client.zinterWithScores(new KeyArray(new String[] {key1, key2})).get());

        // Intersection results are aggregated by the max score of elements
        assertEquals(
                Map.of("two", 3.5),
                client.zinterWithScores(new KeyArray(new String[] {key1, key2}), Aggregate.MAX).get());

        // Intersection results are aggregated by the min score of elements
        assertEquals(
                Map.of("two", 2.0),
                client.zinterWithScores(new KeyArray(new String[] {key1, key2}), Aggregate.MIN).get());

        // Intersection results are aggregated by the sum of the scores of elements
        assertEquals(
                Map.of("two", 5.5),
                client.zinterWithScores(new KeyArray(new String[] {key1, key2}), Aggregate.SUM).get());

        // Scores are multiplied by 2.0 for key1 and key2 during aggregation.
        assertEquals(
                Map.of("two", 11.0),
                client
                        .zinterWithScores(new WeightedKeys(List.of(Pair.of(key1, 2.0), Pair.of(key2, 2.0))))
                        .get());

        // Intersection results are aggregated by the minimum score,
        // with scores for key1 multiplied by 1.0 and for key2 by -2.0.
        assertEquals(
                Map.of("two", -7.0),
                client
                        .zinterWithScores(
                                new WeightedKeys(List.of(Pair.of(key1, 1.0), Pair.of(key2, -2.0))), Aggregate.MIN)
                        .get());

        // Non-existing Key - empty intersection
        assertEquals(0, client.zinter(new KeyArray(new String[] {key1, key3})).get().length);
        assertEquals(0, client.zinterWithScores(new KeyArray(new String[] {key1, key3})).get().size());

        // empty key list
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class, () -> client.zinter(new KeyArray(new String[0])).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.zinterWithScores(new WeightedKeys(List.of())).get());
        assertInstanceOf(RequestException.class, executionException.getCause());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key3, "value").get());
        executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.zinter(new KeyArray(new String[] {key1, key3})).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zintercard(BaseClient client) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"));
        String key1 = "{zintercard}-" + UUID.randomUUID();
        String key2 = "{zintercard}-" + UUID.randomUUID();
        String key3 = "{zintercard}-" + UUID.randomUUID();

        assertEquals(3, client.zadd(key1, Map.of("a", 1.0, "b", 2.0, "c", 3.0)).get());
        assertEquals(3, client.zadd(key2, Map.of("b", 1.0, "c", 2.0, "d", 3.0)).get());

        assertEquals(2L, client.zintercard(new String[] {key1, key2}).get());
        assertEquals(0, client.zintercard(new String[] {key1, key3}).get());

        assertEquals(2L, client.zintercard(new String[] {key1, key2}, 0).get());
        assertEquals(1L, client.zintercard(new String[] {key1, key2}, 1).get());
        assertEquals(2L, client.zintercard(new String[] {key1, key2}, 3).get());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key3, "bar").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.zintercard(new String[] {key3}).get());
        assertInstanceOf(RequestException.class, executionException.getCause());

        // incorrect arguments
        executionException =
                assertThrows(ExecutionException.class, () -> client.zintercard(new String[0]).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        executionException =
                assertThrows(ExecutionException.class, () -> client.zintercard(new String[0], 42).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zinterstore(BaseClient client) {
        String key1 = "{testKey}:1-" + UUID.randomUUID();
        String key2 = "{testKey}:2-" + UUID.randomUUID();
        String key3 = "{testKey}:3-" + UUID.randomUUID();
        String key4 = "{testKey}:4-" + UUID.randomUUID();
        RangeByIndex query = new RangeByIndex(0, -1);
        Map<String, Double> membersScores1 = Map.of("one", 1.0, "two", 2.0);
        Map<String, Double> membersScores2 = Map.of("one", 1.5, "two", 2.5, "three", 3.5);

        assertEquals(2, client.zadd(key1, membersScores1).get());
        assertEquals(3, client.zadd(key2, membersScores2).get());

        assertEquals(2, client.zinterstore(key3, new KeyArray(new String[] {key1, key2})).get());
        assertEquals(Map.of("one", 2.5, "two", 4.5), client.zrangeWithScores(key3, query).get());

        // Intersection results are aggregated by the max score of elements
        assertEquals(
                2, client.zinterstore(key3, new KeyArray(new String[] {key1, key2}), Aggregate.MAX).get());
        assertEquals(Map.of("one", 1.5, "two", 2.5), client.zrangeWithScores(key3, query).get());

        // Intersection results are aggregated by the min score of elements
        assertEquals(
                2, client.zinterstore(key3, new KeyArray(new String[] {key1, key2}), Aggregate.MIN).get());
        assertEquals(Map.of("one", 1.0, "two", 2.0), client.zrangeWithScores(key3, query).get());

        // Intersection results are aggregated by the sum of the scores of elements
        assertEquals(
                2, client.zinterstore(key3, new KeyArray(new String[] {key1, key2}), Aggregate.SUM).get());
        assertEquals(Map.of("one", 2.5, "two", 4.5), client.zrangeWithScores(key3, query).get());

        // Scores are multiplied by 2.0 for key1 and key2 during aggregation.
        assertEquals(
                2,
                client
                        .zinterstore(key3, new WeightedKeys(List.of(Pair.of(key1, 2.0), Pair.of(key2, 2.0))))
                        .get());
        assertEquals(Map.of("one", 5.0, "two", 9.0), client.zrangeWithScores(key3, query).get());

        // Intersection results are aggregated by the minimum score, with scores for key1 multiplied by
        // 1.0 and for key2 by -2.0.
        assertEquals(
                2,
                client
                        .zinterstore(
                                key3,
                                new WeightedKeys(List.of(Pair.of(key1, 1.0), Pair.of(key2, -2.0))),
                                Aggregate.MIN)
                        .get());
        assertEquals(Map.of("two", -5.0, "one", -3.0), client.zrangeWithScores(key3, query).get());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key4, "value").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.zinterstore(key3, new KeyArray(new String[] {key4, key2})).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zmpop(BaseClient client) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7");
        String key1 = "{zmpop}-1-" + UUID.randomUUID();
        String key2 = "{zmpop}-2-" + UUID.randomUUID();
        String key3 = "{zmpop}-3-" + UUID.randomUUID();

        assertEquals(2, client.zadd(key1, Map.of("a1", 1., "b1", 2.)).get());
        assertEquals(2, client.zadd(key2, Map.of("a2", .1, "b2", .2)).get());

        assertArrayEquals(
                new Object[] {key1, Map.of("b1", 2.)}, client.zmpop(new String[] {key1, key2}, MAX).get());
        assertArrayEquals(
                new Object[] {key2, Map.of("b2", .2, "a2", .1)},
                client.zmpop(new String[] {key2, key1}, MAX, 10).get());

        // nothing popped out
        assertNull(client.zmpop(new String[] {key3}, MIN).get());
        assertNull(client.zmpop(new String[] {key3}, MIN, 1).get());

        // Key exists, but it is not a sorted set
        assertEquals(OK, client.set(key3, "value").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.zmpop(new String[] {key3}, MAX).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        executionException =
                assertThrows(
                        ExecutionException.class, () -> client.zmpop(new String[] {key3}, MAX, 1).get());
        assertInstanceOf(RequestException.class, executionException.getCause());

        // incorrect argument
        executionException =
                assertThrows(
                        ExecutionException.class, () -> client.zmpop(new String[] {key1}, MAX, 0).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        executionException =
                assertThrows(ExecutionException.class, () -> client.zmpop(new String[0], MAX).get());
        assertInstanceOf(RequestException.class, executionException.getCause());

        // check that order of entries in the response is preserved
        var entries = new LinkedHashMap<String, Double>();
        for (int i = 0; i < 10; i++) {
            // a => 1., b => 2. etc
            entries.put("" + ('a' + i), (double) i);
        }
        assertEquals(10, client.zadd(key2, entries).get());
        assertEquals(entries, client.zmpop(new String[] {key2}, MIN, 10).get()[1]);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void bzmpop(BaseClient client) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7");
        String key1 = "{bzmpop}-1-" + UUID.randomUUID();
        String key2 = "{bzmpop}-2-" + UUID.randomUUID();
        String key3 = "{bzmpop}-3-" + UUID.randomUUID();

        assertEquals(2, client.zadd(key1, Map.of("a1", 1., "b1", 2.)).get());
        assertEquals(2, client.zadd(key2, Map.of("a2", .1, "b2", .2)).get());

        assertArrayEquals(
                new Object[] {key1, Map.of("b1", 2.)},
                client.bzmpop(new String[] {key1, key2}, MAX, 0.1).get());
        assertArrayEquals(
                new Object[] {key2, Map.of("b2", .2, "a2", .1)},
                client.bzmpop(new String[] {key2, key1}, MAX, 0.1, 10).get());

        // nothing popped out
        assertNull(client.bzmpop(new String[] {key3}, MIN, 0.001).get());
        assertNull(client.bzmpop(new String[] {key3}, MIN, 0.001, 1).get());

        // Key exists, but it is not a sorted set
        assertEquals(OK, client.set(key3, "value").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class, () -> client.bzmpop(new String[] {key3}, MAX, .1).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        executionException =
                assertThrows(
                        ExecutionException.class, () -> client.bzmpop(new String[] {key3}, MAX, .1, 1).get());
        assertInstanceOf(RequestException.class, executionException.getCause());

        // incorrect argument
        executionException =
                assertThrows(
                        ExecutionException.class, () -> client.bzmpop(new String[] {key1}, MAX, .1, 0).get());
        assertInstanceOf(RequestException.class, executionException.getCause());

        // check that order of entries in the response is preserved
        var entries = new LinkedHashMap<String, Double>();
        for (int i = 0; i < 10; i++) {
            // a => 1., b => 2. etc
            entries.put("" + ('a' + i), (double) i);
        }
        assertEquals(10, client.zadd(key2, entries).get());
        assertEquals(entries, client.bzmpop(new String[] {key2}, MIN, .1, 10).get()[1]);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void bzmpop_timeout_check(BaseClient client) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7");
        String key = UUID.randomUUID().toString();
        // create new client with default request timeout (250 millis)
        try (var testClient =
                client instanceof RedisClient
                        ? RedisClient.CreateClient(commonClientConfig().build()).get()
                        : RedisClusterClient.CreateClient(commonClusterClientConfig().build()).get()) {

            // ensure that commands doesn't time out even if timeout > request timeout
            assertNull(testClient.bzmpop(new String[] {key}, MAX, 1).get());

            // with 0 timeout (no timeout) should never time out,
            // but we wrap the test with timeout to avoid test failing or stuck forever
            assertThrows(
                    TimeoutException.class, // <- future timeout, not command timeout
                    () -> testClient.bzmpop(new String[] {key}, MAX, 0).get(3, TimeUnit.SECONDS));
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void xadd_xlen_and_xtrim(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String field1 = UUID.randomUUID().toString();
        String field2 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();

        assertNull(
                client
                        .xadd(
                                key,
                                Map.of(field1, "foo0", field2, "bar0"),
                                StreamAddOptions.builder().makeStream(Boolean.FALSE).build())
                        .get());

        String timestamp1 = "0-1";
        assertEquals(
                timestamp1,
                client
                        .xadd(
                                key,
                                Map.of(field1, "foo1", field2, "bar1"),
                                StreamAddOptions.builder().id(timestamp1).build())
                        .get());

        assertNotNull(client.xadd(key, Map.of(field1, "foo2", field2, "bar2")).get());
        assertEquals(2L, client.xlen(key).get());

        // this will trim the first entry.
        String id =
                client
                        .xadd(
                                key,
                                Map.of(field1, "foo3", field2, "bar3"),
                                StreamAddOptions.builder().trim(new MaxLen(true, 2L)).build())
                        .get();
        assertNotNull(id);
        assertEquals(2L, client.xlen(key).get());

        // this will trim the second entry.
        assertNotNull(
                client
                        .xadd(
                                key,
                                Map.of(field1, "foo4", field2, "bar4"),
                                StreamAddOptions.builder().trim(new MinId(true, id)).build())
                        .get());
        assertEquals(2L, client.xlen(key).get());

        // test xtrim to remove 1 element
        assertEquals(1L, client.xtrim(key, new MaxLen(1)).get());
        assertEquals(1L, client.xlen(key).get());

        // Key does not exist - returns 0
        assertEquals(0L, client.xtrim(key2, new MaxLen(true, 1)).get());
        assertEquals(0L, client.xlen(key2).get());

        // Throw Exception: Key exists - but it is not a stream
        assertEquals(OK, client.set(key2, "xtrimtest").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.xtrim(key2, new MinId("0-1")).get());
        assertTrue(executionException.getCause() instanceof RequestException);
        executionException = assertThrows(ExecutionException.class, () -> client.xlen(key2).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void xread(BaseClient client) {
        String key1 = "{key}:1" + UUID.randomUUID();
        String key2 = "{key}:2" + UUID.randomUUID();
        String field1 = "f1_";
        String field2 = "f2_";
        String field3 = "f3_";

        // setup first entries in streams key1 and key2
        Map<String, String> timestamp_1_1_map = new LinkedHashMap<>();
        timestamp_1_1_map.put(field1, field1 + "1");
        timestamp_1_1_map.put(field3, field3 + "1");
        String timestamp_1_1 =
                client.xadd(key1, timestamp_1_1_map, StreamAddOptions.builder().id("1-1").build()).get();
        assertNotNull(timestamp_1_1);

        String timestamp_2_1 =
                client
                        .xadd(key2, Map.of(field2, field2 + "1"), StreamAddOptions.builder().id("2-1").build())
                        .get();
        assertNotNull(timestamp_2_1);

        // setup second entries in streams key1 and key2
        String timestamp_1_2 =
                client
                        .xadd(key1, Map.of(field1, field1 + "2"), StreamAddOptions.builder().id("1-2").build())
                        .get();
        assertNotNull(timestamp_1_2);

        String timestamp_2_2 =
                client
                        .xadd(key2, Map.of(field2, field2 + "2"), StreamAddOptions.builder().id("2-2").build())
                        .get();
        assertNotNull(timestamp_2_2);

        // setup third entries in streams key1 and key2
        Map<String, String> timestamp_1_3_map = new LinkedHashMap<>();
        timestamp_1_3_map.put(field1, field1 + "3");
        timestamp_1_3_map.put(field3, field3 + "3");
        String timestamp_1_3 =
                client.xadd(key1, timestamp_1_3_map, StreamAddOptions.builder().id("1-3").build()).get();
        assertNotNull(timestamp_1_3);

        String timestamp_2_3 =
                client
                        .xadd(key2, Map.of(field2, field2 + "3"), StreamAddOptions.builder().id("2-3").build())
                        .get();
        assertNotNull(timestamp_2_3);

        Map<String, Map<String, String[][]>> result =
                client.xread(Map.of(key1, timestamp_1_1, key2, timestamp_2_1)).get();

        // check key1
        Map<String, String[][]> expected_key1 = new LinkedHashMap<>();
        expected_key1.put(timestamp_1_2, new String[][] {{field1, field1 + "2"}});
        expected_key1.put(
                timestamp_1_3,
                new String[][] {
                    {field1, field1 + "3"},
                    {field3, field3 + "3"}
                });
        assertDeepEquals(expected_key1, result.get(key1));

        // check key2
        Map<String, String[][]> expected_key2 = new LinkedHashMap<>();
        expected_key2.put(timestamp_2_2, new String[][] {{field2, field2 + "2"}});
        expected_key2.put(timestamp_2_3, new String[][] {{field2, field2 + "3"}});
        assertDeepEquals(expected_key2, result.get(key2));
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void xread_return_failures(BaseClient client) {
        String key1 = "{key}:1" + UUID.randomUUID();
        String nonStreamKey = "{key}:3" + UUID.randomUUID();
        String field1 = "f1_";

        // setup first entries in streams key1 and key2
        Map<String, String> timestamp_1_1_map = new LinkedHashMap<>();
        timestamp_1_1_map.put(field1, field1 + "1");
        String timestamp_1_1 =
                client.xadd(key1, timestamp_1_1_map, StreamAddOptions.builder().id("1-1").build()).get();
        assertNotNull(timestamp_1_1);

        // Key exists, but it is not a stream
        assertEquals(OK, client.set(nonStreamKey, "bar").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.xread(Map.of(nonStreamKey, timestamp_1_1, key1, timestamp_1_1)).get());
        assertInstanceOf(RequestException.class, executionException.getCause());

        executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.xread(Map.of(key1, timestamp_1_1, nonStreamKey, timestamp_1_1)).get());
        assertInstanceOf(RequestException.class, executionException.getCause());

        try (var testClient =
                client instanceof RedisClient
                        ? RedisClient.CreateClient(commonClientConfig().build()).get()
                        : RedisClusterClient.CreateClient(commonClusterClientConfig().build()).get()) {

            // ensure that commands doesn't time out even if timeout > request timeout
            long oneSecondInMS = 1000L;
            assertNull(
                    testClient
                            .xread(
                                    Map.of(key1, timestamp_1_1),
                                    StreamReadOptions.builder().block(oneSecondInMS).build())
                            .get());

            // with 0 timeout (no timeout) should never time out,
            // but we wrap the test with timeout to avoid test failing or stuck forever
            assertThrows(
                    TimeoutException.class, // <- future timeout, not command timeout
                    () ->
                            testClient
                                    .xread(Map.of(key1, timestamp_1_1), StreamReadOptions.builder().block(0L).build())
                                    .get(3, TimeUnit.SECONDS));
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void xdel(BaseClient client) {

        String key = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String streamId1 = "0-1";
        String streamId2 = "0-2";
        String streamId3 = "0-3";

        assertEquals(
                streamId1,
                client
                        .xadd(
                                key,
                                Map.of("f1", "foo1", "f2", "bar2"),
                                StreamAddOptions.builder().id(streamId1).build())
                        .get());
        assertEquals(
                streamId2,
                client
                        .xadd(
                                key,
                                Map.of("f1", "foo1", "f2", "bar2"),
                                StreamAddOptions.builder().id(streamId2).build())
                        .get());
        assertEquals(2L, client.xlen(key).get());

        // Deletes one stream id, and ignores anything invalid:
        assertEquals(1L, client.xdel(key, new String[] {streamId1, streamId3}).get());
        assertEquals(0L, client.xdel(key2, new String[] {streamId3}).get());

        // Throw Exception: Key exists - but it is not a stream
        assertEquals(OK, client.set(key2, "xdeltest").get());

        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class, () -> client.xdel(key2, new String[] {streamId3}).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void xrange_and_xrevrange(BaseClient client) {

        String key = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String streamId1 = "0-1";
        String streamId2 = "0-2";
        String streamId3 = "0-3";

        assertEquals(
                streamId1,
                client
                        .xadd(
                                key,
                                Map.of("f1", "foo1", "f2", "bar2"),
                                StreamAddOptions.builder().id(streamId1).build())
                        .get());
        assertEquals(
                streamId2,
                client
                        .xadd(
                                key,
                                Map.of("f1", "foo1", "f2", "bar2"),
                                StreamAddOptions.builder().id(streamId2).build())
                        .get());
        assertEquals(2L, client.xlen(key).get());

        // get everything from the stream
        Map<String, String[][]> result = client.xrange(key, InfRangeBound.MIN, InfRangeBound.MAX).get();
        assertEquals(2, result.size());
        assertNotNull(result.get(streamId1));
        assertNotNull(result.get(streamId2));

        // get everything from the stream using a reverse range search
        Map<String, String[][]> revResult =
                client.xrevrange(key, InfRangeBound.MAX, InfRangeBound.MIN).get();
        assertEquals(2, revResult.size());
        assertNotNull(revResult.get(streamId1));
        assertNotNull(revResult.get(streamId2));

        // returns empty if + before -
        Map<String, String[][]> emptyResult =
                client.xrange(key, InfRangeBound.MAX, InfRangeBound.MIN).get();
        assertEquals(0, emptyResult.size());

        // rev search returns empty if - before +
        Map<String, String[][]> emptyRevResult =
                client.xrevrange(key, InfRangeBound.MIN, InfRangeBound.MAX).get();
        assertEquals(0, emptyRevResult.size());

        assertEquals(
                streamId3,
                client
                        .xadd(
                                key,
                                Map.of("f3", "foo3", "f4", "bar3"),
                                StreamAddOptions.builder().id(streamId3).build())
                        .get());

        // get the newest entry
        Map<String, String[][]> newResult =
                client.xrange(key, IdBound.ofExclusive(streamId2), IdBound.ofExclusive(5), 1L).get();
        assertEquals(1, newResult.size());
        assertNotNull(newResult.get(streamId3));
        // ...and from xrevrange
        Map<String, String[][]> newRevResult =
                client.xrevrange(key, IdBound.ofExclusive(5), IdBound.ofExclusive(streamId2), 1L).get();
        assertEquals(1, newRevResult.size());
        assertNotNull(newRevResult.get(streamId3));

        // xrange against an emptied stream
        assertEquals(3, client.xdel(key, new String[] {streamId1, streamId2, streamId3}).get());
        Map<String, String[][]> emptiedResult =
                client.xrange(key, InfRangeBound.MIN, InfRangeBound.MAX, 10L).get();
        assertEquals(0, emptiedResult.size());
        // ...and xrevrange
        Map<String, String[][]> emptiedRevResult =
                client.xrevrange(key, InfRangeBound.MAX, InfRangeBound.MIN, 10L).get();
        assertEquals(0, emptiedRevResult.size());

        // xrange against a non-existent stream
        emptyResult = client.xrange(key2, InfRangeBound.MIN, InfRangeBound.MAX).get();
        assertEquals(0, emptyResult.size());
        // ...and xrevrange
        emptiedRevResult = client.xrevrange(key2, InfRangeBound.MAX, InfRangeBound.MIN).get();
        assertEquals(0, emptiedRevResult.size());

        // xrange against a non-stream value
        assertEquals(OK, client.set(key2, "not_a_stream").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.xrange(key2, InfRangeBound.MIN, InfRangeBound.MAX).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        // ...and xrevrange
        executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.xrevrange(key2, InfRangeBound.MAX, InfRangeBound.MIN).get());
        assertInstanceOf(RequestException.class, executionException.getCause());

        // xrange when range bound is not valid ID
        executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .xrange(key, IdBound.ofExclusive("not_a_stream_id"), InfRangeBound.MAX)
                                        .get());
        assertInstanceOf(RequestException.class, executionException.getCause());

        executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .xrange(key, InfRangeBound.MIN, IdBound.ofExclusive("not_a_stream_id"))
                                        .get());
        assertInstanceOf(RequestException.class, executionException.getCause());

        // ... and xrevrange

        executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .xrevrange(key, IdBound.ofExclusive("not_a_stream_id"), InfRangeBound.MIN)
                                        .get());
        assertInstanceOf(RequestException.class, executionException.getCause());

        executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .xrevrange(key, InfRangeBound.MAX, IdBound.ofExclusive("not_a_stream_id"))
                                        .get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void xgroupCreate_xgroupDestroy(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String stringKey = UUID.randomUUID().toString();
        String groupName = "group" + UUID.randomUUID();
        String streamId = "0-1";

        // Stream not created results in error
        Exception executionException =
                assertThrows(
                        ExecutionException.class, () -> client.xgroupCreate(key, groupName, streamId).get());
        assertInstanceOf(RequestException.class, executionException.getCause());

        // Stream with option to create creates stream & Group
        assertEquals(
                OK,
                client
                        .xgroupCreate(
                                key, groupName, streamId, StreamGroupOptions.builder().makeStream().build())
                        .get());

        // ...and again results in BUSYGROUP error, because group names must be unique
        executionException =
                assertThrows(
                        ExecutionException.class, () -> client.xgroupCreate(key, groupName, streamId).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        assertTrue(executionException.getMessage().contains("BUSYGROUP"));

        // Stream Group can be destroyed returns: true
        assertEquals(true, client.xgroupDestroy(key, groupName).get());

        // ...and again results in: false
        assertEquals(false, client.xgroupDestroy(key, groupName).get());

        // ENTRIESREAD option was added in redis 7.0.0
        StreamGroupOptions entriesReadOption = StreamGroupOptions.builder().entriesRead("10").build();
        if (REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            assertEquals(OK, client.xgroupCreate(key, groupName, streamId, entriesReadOption).get());
        } else {
            executionException =
                    assertThrows(
                            ExecutionException.class,
                            () -> client.xgroupCreate(key, groupName, streamId, entriesReadOption).get());
            assertInstanceOf(RequestException.class, executionException.getCause());
        }

        // key is a string and cannot be created as a stream
        assertEquals(OK, client.set(stringKey, "not_a_stream").get());
        executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .xgroupCreate(
                                                stringKey,
                                                groupName,
                                                streamId,
                                                StreamGroupOptions.builder().makeStream().build())
                                        .get());
        assertInstanceOf(RequestException.class, executionException.getCause());

        executionException =
                assertThrows(
                        ExecutionException.class, () -> client.xgroupDestroy(stringKey, groupName).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zrandmember(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        Map<String, Double> membersScores = Map.of("one", 1.0, "two", 2.0);
        assertEquals(2, client.zadd(key1, membersScores).get());

        String randMember = client.zrandmember(key1).get();
        assertTrue(membersScores.containsKey(randMember));
        assertNull(client.zrandmember("nonExistentKey").get());

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key2, "bar").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.zrandmember(key2).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zrandmemberWithCount(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        Map<String, Double> membersScores = Map.of("one", 1.0, "two", 2.0);
        assertEquals(2, client.zadd(key1, membersScores).get());

        // Unique values are expected as count is positive
        List<String> randMembers = Arrays.asList(client.zrandmemberWithCount(key1, 4).get());
        assertEquals(2, randMembers.size());
        assertEquals(2, new HashSet<>(randMembers).size());
        randMembers.forEach(member -> assertTrue(membersScores.containsKey(member)));

        // Duplicate values are expected as count is negative
        randMembers = Arrays.asList(client.zrandmemberWithCount(key1, -4).get());
        assertEquals(4, randMembers.size());
        randMembers.forEach(member -> assertTrue(membersScores.containsKey(member)));

        assertEquals(0, client.zrandmemberWithCount(key1, 0).get().length);
        assertEquals(0, client.zrandmemberWithCount("nonExistentKey", 4).get().length);

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key2, "bar").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.zrandmemberWithCount(key2, 5).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zrandmemberWithCountWithScores(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        Map<String, Double> membersScores = Map.of("one", 1.0, "two", 2.0);
        assertEquals(2, client.zadd(key1, membersScores).get());

        // Unique values are expected as count is positive
        Object[][] randMembersWithScores = client.zrandmemberWithCountWithScores(key1, 4).get();
        assertEquals(2, randMembersWithScores.length);
        for (Object[] membersWithScore : randMembersWithScores) {
            String member = (String) membersWithScore[0];
            Double score = (Double) membersWithScore[1];

            assertEquals(score, membersScores.get(member));
        }

        // Duplicate values are expected as count is negative
        randMembersWithScores = client.zrandmemberWithCountWithScores(key1, -4).get();
        assertEquals(4, randMembersWithScores.length);
        for (Object[] randMembersWithScore : randMembersWithScores) {
            String member = (String) randMembersWithScore[0];
            Double score = (Double) randMembersWithScore[1];

            assertEquals(score, membersScores.get(member));
        }

        assertEquals(0, client.zrandmemberWithCountWithScores(key1, 0).get().length);
        assertEquals(0, client.zrandmemberWithCountWithScores("nonExistentKey", 4).get().length);

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key2, "bar").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class, () -> client.zrandmemberWithCountWithScores(key2, 5).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zincrby(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();

        // key does not exist
        assertEquals(2.5, client.zincrby(key1, 2.5, "value1").get());
        assertEquals(2.5, client.zscore(key1, "value1").get());

        // key exists, but value doesn't
        assertEquals(-3.3, client.zincrby(key1, -3.3, "value2").get());
        assertEquals(-3.3, client.zscore(key1, "value2").get());

        // updating existing value in existing key
        assertEquals(3.5, client.zincrby(key1, 1., "value1").get());
        assertEquals(3.5, client.zscore(key1, "value1").get());

        // Key exists, but it is not a sorted set
        assertEquals(2L, client.sadd(key2, new String[] {"one", "two"}).get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.zincrby(key2, .5, "_").get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void type(BaseClient client) {
        String nonExistingKey = UUID.randomUUID().toString();
        String stringKey = UUID.randomUUID().toString();
        String listKey = UUID.randomUUID().toString();
        String hashKey = UUID.randomUUID().toString();
        String setKey = UUID.randomUUID().toString();
        String zsetKey = UUID.randomUUID().toString();
        String streamKey = UUID.randomUUID().toString();

        assertEquals(OK, client.set(stringKey, "value").get());
        assertEquals(1, client.lpush(listKey, new String[] {"value"}).get());
        assertEquals(1, client.hset(hashKey, Map.of("1", "2")).get());
        assertEquals(1, client.sadd(setKey, new String[] {"value"}).get());
        assertEquals(1, client.zadd(zsetKey, Map.of("1", 2d)).get());
        assertNotNull(client.xadd(streamKey, Map.of("field", "value")));

        assertTrue("none".equalsIgnoreCase(client.type(nonExistingKey).get()));
        assertTrue("string".equalsIgnoreCase(client.type(stringKey).get()));
        assertTrue("list".equalsIgnoreCase(client.type(listKey).get()));
        assertTrue("hash".equalsIgnoreCase(client.type(hashKey).get()));
        assertTrue("set".equalsIgnoreCase(client.type(setKey).get()));
        assertTrue("zset".equalsIgnoreCase(client.type(zsetKey).get()));
        assertTrue("stream".equalsIgnoreCase(client.type(streamKey).get()));
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void linsert(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();

        assertEquals(4, client.lpush(key1, new String[] {"4", "3", "2", "1"}).get());
        assertEquals(5, client.linsert(key1, BEFORE, "2", "1.5").get());
        assertEquals(6, client.linsert(key1, AFTER, "3", "3.5").get());
        assertArrayEquals(
                new String[] {"1", "1.5", "2", "3", "3.5", "4"}, client.lrange(key1, 0, -1).get());

        assertEquals(0, client.linsert(key2, BEFORE, "pivot", "elem").get());
        assertEquals(-1, client.linsert(key1, AFTER, "5", "6").get());

        // Key exists, but it is not a list
        assertEquals(OK, client.set(key2, "linsert").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.linsert(key2, AFTER, "p", "e").get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void brpop(BaseClient client) {
        String listKey1 = "{listKey}-1-" + UUID.randomUUID();
        String listKey2 = "{listKey}-2-" + UUID.randomUUID();
        String value1 = "value1-" + UUID.randomUUID();
        String value2 = "value2-" + UUID.randomUUID();
        assertEquals(2, client.lpush(listKey1, new String[] {value1, value2}).get());

        var response = client.brpop(new String[] {listKey1, listKey2}, 0.5).get();
        assertArrayEquals(new String[] {listKey1, value1}, response);

        // nothing popped out
        assertNull(
                client
                        .brpop(new String[] {listKey2}, REDIS_VERSION.isLowerThan("7.0.0") ? 1. : 0.001)
                        .get());

        // Key exists, but it is not a list
        assertEquals(OK, client.set("foo", "bar").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class, () -> client.brpop(new String[] {"foo"}, .0001).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void rpushx(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String key3 = UUID.randomUUID().toString();

        assertEquals(1, client.rpush(key1, new String[] {"0"}).get());
        assertEquals(4, client.rpushx(key1, new String[] {"1", "2", "3"}).get());
        assertArrayEquals(new String[] {"0", "1", "2", "3"}, client.lrange(key1, 0, -1).get());

        assertEquals(0, client.rpushx(key2, new String[] {"1"}).get());
        assertArrayEquals(new String[0], client.lrange(key2, 0, -1).get());

        // Key exists, but it is not a list
        assertEquals(OK, client.set(key3, "bar").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.rpushx(key3, new String[] {"_"}).get());
        assertTrue(executionException.getCause() instanceof RequestException);
        // empty element list
        executionException =
                assertThrows(ExecutionException.class, () -> client.rpushx(key2, new String[0]).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void blpop(BaseClient client) {
        String listKey1 = "{listKey}-1-" + UUID.randomUUID();
        String listKey2 = "{listKey}-2-" + UUID.randomUUID();
        String value1 = "value1-" + UUID.randomUUID();
        String value2 = "value2-" + UUID.randomUUID();
        assertEquals(2, client.lpush(listKey1, new String[] {value1, value2}).get());

        var response = client.blpop(new String[] {listKey1, listKey2}, 0.5).get();
        assertArrayEquals(new String[] {listKey1, value2}, response);

        // nothing popped out
        assertNull(
                client
                        .blpop(new String[] {listKey2}, REDIS_VERSION.isLowerThan("7.0.0") ? 1. : 0.001)
                        .get());

        // Key exists, but it is not a list
        assertEquals(OK, client.set("foo", "bar").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class, () -> client.blpop(new String[] {"foo"}, .0001).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void lpushx(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String key3 = UUID.randomUUID().toString();

        assertEquals(1, client.lpush(key1, new String[] {"0"}).get());
        assertEquals(4, client.lpushx(key1, new String[] {"1", "2", "3"}).get());
        assertArrayEquals(new String[] {"3", "2", "1", "0"}, client.lrange(key1, 0, -1).get());

        assertEquals(0, client.lpushx(key2, new String[] {"1"}).get());
        assertArrayEquals(new String[0], client.lrange(key2, 0, -1).get());

        // Key exists, but it is not a list
        assertEquals(OK, client.set(key3, "bar").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.lpushx(key3, new String[] {"_"}).get());
        // empty element list
        executionException =
                assertThrows(ExecutionException.class, () -> client.lpushx(key2, new String[0]).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zrange_by_index(BaseClient client) {
        String key = UUID.randomUUID().toString();
        Map<String, Double> membersScores = Map.of("one", 1.0, "two", 2.0, "three", 3.0);
        assertEquals(3, client.zadd(key, membersScores).get());

        RangeByIndex query = new RangeByIndex(0, 1);
        assertArrayEquals(new String[] {"one", "two"}, client.zrange(key, query).get());

        query = new RangeByIndex(0, -1);
        assertEquals(
                Map.of("one", 1.0, "two", 2.0, "three", 3.0), client.zrangeWithScores(key, query).get());

        query = new RangeByIndex(0, 1);
        assertArrayEquals(new String[] {"three", "two"}, client.zrange(key, query, true).get());

        query = new RangeByIndex(3, 1);
        assertArrayEquals(new String[] {}, client.zrange(key, query, true).get());
        assertTrue(client.zrangeWithScores(key, query).get().isEmpty());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zrange_by_score(BaseClient client) {
        String key = UUID.randomUUID().toString();
        Map<String, Double> membersScores = Map.of("one", 1.0, "two", 2.0, "three", 3.0);
        assertEquals(3, client.zadd(key, membersScores).get());

        RangeByScore query = new RangeByScore(NEGATIVE_INFINITY, new ScoreBoundary(3, false));
        assertArrayEquals(new String[] {"one", "two"}, client.zrange(key, query).get());

        query = new RangeByScore(NEGATIVE_INFINITY, POSITIVE_INFINITY);
        assertEquals(
                Map.of("one", 1.0, "two", 2.0, "three", 3.0), client.zrangeWithScores(key, query).get());

        query = new RangeByScore(new ScoreBoundary(3, false), NEGATIVE_INFINITY);
        assertArrayEquals(new String[] {"two", "one"}, client.zrange(key, query, true).get());

        query = new RangeByScore(NEGATIVE_INFINITY, POSITIVE_INFINITY, new Limit(1, 2));
        assertArrayEquals(new String[] {"two", "three"}, client.zrange(key, query).get());

        query = new RangeByScore(NEGATIVE_INFINITY, new ScoreBoundary(3, false));
        assertArrayEquals(
                new String[] {},
                client
                        .zrange(key, query, true)
                        .get()); // stop is greater than start with reverse set to True

        query = new RangeByScore(POSITIVE_INFINITY, new ScoreBoundary(3, false));
        assertArrayEquals(
                new String[] {}, client.zrange(key, query, true).get()); // start is greater than stop

        query = new RangeByScore(POSITIVE_INFINITY, new ScoreBoundary(3, false));
        assertTrue(client.zrangeWithScores(key, query).get().isEmpty()); // start is greater than stop

        query = new RangeByScore(NEGATIVE_INFINITY, new ScoreBoundary(3, false));
        assertTrue(
                client
                        .zrangeWithScores(key, query, true)
                        .get()
                        .isEmpty()); // stop is greater than start with reverse set to True
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zrange_by_lex(BaseClient client) {
        String key = UUID.randomUUID().toString();
        Map<String, Double> membersScores = Map.of("a", 1.0, "b", 2.0, "c", 3.0);
        assertEquals(3, client.zadd(key, membersScores).get());

        RangeByLex query = new RangeByLex(InfLexBound.NEGATIVE_INFINITY, new LexBoundary("c", false));
        assertArrayEquals(new String[] {"a", "b"}, client.zrange(key, query).get());

        query =
                new RangeByLex(
                        InfLexBound.NEGATIVE_INFINITY, InfLexBound.POSITIVE_INFINITY, new Limit(1, 2));
        assertArrayEquals(new String[] {"b", "c"}, client.zrange(key, query).get());

        query = new RangeByLex(new LexBoundary("c", false), InfLexBound.NEGATIVE_INFINITY);
        assertArrayEquals(new String[] {"b", "a"}, client.zrange(key, query, true).get());

        query = new RangeByLex(InfLexBound.NEGATIVE_INFINITY, new LexBoundary("c", false));
        assertArrayEquals(
                new String[] {},
                client
                        .zrange(key, query, true)
                        .get()); // stop is greater than start with reverse set to True

        query = new RangeByLex(InfLexBound.POSITIVE_INFINITY, new LexBoundary("c", false));
        assertArrayEquals(
                new String[] {}, client.zrange(key, query).get()); // start is greater than stop
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void zrange_with_different_types_of_keys(BaseClient client) {
        String key = UUID.randomUUID().toString();
        RangeByIndex query = new RangeByIndex(0, 1);

        assertArrayEquals(new String[] {}, client.zrange("non_existing_key", query).get());

        assertTrue(
                client
                        .zrangeWithScores("non_existing_key", query)
                        .get()
                        .isEmpty()); // start is greater than stop

        // Key exists, but it is not a set
        assertEquals(OK, client.set(key, "value").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.zrange(key, query).get());
        assertTrue(executionException.getCause() instanceof RequestException);

        executionException =
                assertThrows(ExecutionException.class, () -> client.zrangeWithScores(key, query).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void pfadd(BaseClient client) {
        String key = UUID.randomUUID().toString();
        assertEquals(1, client.pfadd(key, new String[0]).get());
        assertEquals(1, client.pfadd(key, new String[] {"one", "two"}).get());
        assertEquals(0, client.pfadd(key, new String[] {"two"}).get());
        assertEquals(0, client.pfadd(key, new String[0]).get());

        // Key exists, but it is not a HyperLogLog
        assertEquals(OK, client.set("foo", "bar").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.pfadd("foo", new String[0]).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void pfcount(BaseClient client) {
        String key1 = "{test}-hll1-" + UUID.randomUUID();
        String key2 = "{test}-hll2-" + UUID.randomUUID();
        String key3 = "{test}-hll3-" + UUID.randomUUID();
        assertEquals(1, client.pfadd(key1, new String[] {"a", "b", "c"}).get());
        assertEquals(1, client.pfadd(key2, new String[] {"b", "c", "d"}).get());
        assertEquals(3, client.pfcount(new String[] {key1}).get());
        assertEquals(3, client.pfcount(new String[] {key2}).get());
        assertEquals(4, client.pfcount(new String[] {key1, key2}).get());
        assertEquals(4, client.pfcount(new String[] {key1, key2, key3}).get());
        // empty HyperLogLog data set
        assertEquals(1, client.pfadd(key3, new String[0]).get());
        assertEquals(0, client.pfcount(new String[] {key3}).get());

        // Key exists, but it is not a HyperLogLog
        assertEquals(OK, client.set("foo", "bar").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.pfcount(new String[] {"foo"}).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void pfmerge(BaseClient client) {
        String key1 = "{test}-hll1-" + UUID.randomUUID();
        String key2 = "{test}-hll2-" + UUID.randomUUID();
        String key3 = "{test}-hll3-" + UUID.randomUUID();
        assertEquals(1, client.pfadd(key1, new String[] {"a", "b", "c"}).get());
        assertEquals(1, client.pfadd(key2, new String[] {"b", "c", "d"}).get());
        // new HyperLogLog data set
        assertEquals(OK, client.pfmerge(key3, new String[] {key1, key2}).get());
        assertEquals(
                client.pfcount(new String[] {key1, key2}).get(), client.pfcount(new String[] {key3}).get());
        // existing HyperLogLog data set
        assertEquals(OK, client.pfmerge(key1, new String[] {key2}).get());
        assertEquals(
                client.pfcount(new String[] {key1, key2}).get(), client.pfcount(new String[] {key1}).get());

        // Key exists, but it is not a HyperLogLog
        assertEquals(OK, client.set("foo", "bar").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class, () -> client.pfmerge("foo", new String[] {key1}).get());
        assertTrue(executionException.getCause() instanceof RequestException);
        executionException =
                assertThrows(
                        ExecutionException.class, () -> client.pfmerge(key1, new String[] {"foo"}).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void objectEncoding_returns_null(BaseClient client) {
        String nonExistingKey = UUID.randomUUID().toString();
        assertNull(client.objectEncoding(nonExistingKey).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void objectEncoding_returns_string_raw(BaseClient client) {
        String stringRawKey = UUID.randomUUID().toString();
        assertEquals(
                OK,
                client
                        .set(stringRawKey, "a really loooooooooooooooooooooooooooooooooooooooong value")
                        .get());
        assertEquals("raw", client.objectEncoding(stringRawKey).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void objectEncoding_returns_string_int(BaseClient client) {
        String stringIntKey = UUID.randomUUID().toString();
        assertEquals(OK, client.set(stringIntKey, "2").get());
        assertEquals("int", client.objectEncoding(stringIntKey).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void objectEncoding_returns_string_embstr(BaseClient client) {
        String stringEmbstrKey = UUID.randomUUID().toString();
        assertEquals(OK, client.set(stringEmbstrKey, "value").get());
        assertEquals("embstr", client.objectEncoding(stringEmbstrKey).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void objectEncoding_returns_list_listpack(BaseClient client) {
        String listListpackKey = UUID.randomUUID().toString();
        assertEquals(1, client.lpush(listListpackKey, new String[] {"1"}).get());
        // API documentation states that a ziplist should be returned for Redis versions <= 6.2, but
        // actual behavior returns a quicklist.
        assertEquals(
                REDIS_VERSION.isLowerThan("7.0.0") ? "quicklist" : "listpack",
                client.objectEncoding(listListpackKey).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void objectEncoding_returns_set_hashtable(BaseClient client) {
        String setHashtableKey = UUID.randomUUID().toString();
        // The default value of set-max-intset-entries is 512
        for (Integer i = 0; i <= 512; i++) {
            assertEquals(1, client.sadd(setHashtableKey, new String[] {i.toString()}).get());
        }
        assertEquals("hashtable", client.objectEncoding(setHashtableKey).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void objectEncoding_returns_set_intset(BaseClient client) {
        String setIntsetKey = UUID.randomUUID().toString();
        assertEquals(1, client.sadd(setIntsetKey, new String[] {"1"}).get());
        assertEquals("intset", client.objectEncoding(setIntsetKey).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void objectEncoding_returns_set_listpack(BaseClient client) {
        String setListpackKey = UUID.randomUUID().toString();
        assertEquals(1, client.sadd(setListpackKey, new String[] {"foo"}).get());
        assertEquals(
                REDIS_VERSION.isLowerThan("7.2.0") ? "hashtable" : "listpack",
                client.objectEncoding(setListpackKey).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void objectEncoding_returns_hash_hashtable(BaseClient client) {
        String hashHashtableKey = UUID.randomUUID().toString();
        // The default value of hash-max-listpack-entries is 512
        for (Integer i = 0; i <= 512; i++) {
            assertEquals(1, client.hset(hashHashtableKey, Map.of(i.toString(), "2")).get());
        }
        assertEquals("hashtable", client.objectEncoding(hashHashtableKey).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void objectEncoding_returns_hash_listpack(BaseClient client) {
        String hashListpackKey = UUID.randomUUID().toString();
        assertEquals(1, client.hset(hashListpackKey, Map.of("1", "2")).get());
        assertEquals(
                REDIS_VERSION.isLowerThan("7.0.0") ? "ziplist" : "listpack",
                client.objectEncoding(hashListpackKey).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void objectEncoding_returns_zset_skiplist(BaseClient client) {
        String zsetSkiplistKey = UUID.randomUUID().toString();
        // The default value of zset-max-listpack-entries is 128
        for (Integer i = 0; i <= 128; i++) {
            assertEquals(1, client.zadd(zsetSkiplistKey, Map.of(i.toString(), 2d)).get());
        }
        assertEquals("skiplist", client.objectEncoding(zsetSkiplistKey).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void objectEncoding_returns_zset_listpack(BaseClient client) {
        String zsetListpackKey = UUID.randomUUID().toString();
        assertEquals(1, client.zadd(zsetListpackKey, Map.of("1", 2d)).get());
        assertEquals(
                REDIS_VERSION.isLowerThan("7.0.0") ? "ziplist" : "listpack",
                client.objectEncoding(zsetListpackKey).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void objectEncoding_returns_stream(BaseClient client) {
        String streamKey = UUID.randomUUID().toString();
        assertNotNull(client.xadd(streamKey, Map.of("field", "value")));
        assertEquals("stream", client.objectEncoding(streamKey).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void objectFreq_returns_null(BaseClient client) {
        String nonExistingKey = UUID.randomUUID().toString();
        assertNull(client.objectFreq(nonExistingKey).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void objectIdletime_returns_null(BaseClient client) {
        String nonExistingKey = UUID.randomUUID().toString();
        assertNull(client.objectIdletime(nonExistingKey).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void objectIdletime(BaseClient client) {
        String key = UUID.randomUUID().toString();
        assertEquals(OK, client.set(key, "").get());
        Thread.sleep(2000);
        assertTrue(client.objectIdletime(key).get() > 0L);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void objectRefcount_returns_null(BaseClient client) {
        String nonExistingKey = UUID.randomUUID().toString();
        assertNull(client.objectRefcount(nonExistingKey).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void objectRefcount(BaseClient client) {
        String key = UUID.randomUUID().toString();
        assertEquals(OK, client.set(key, "").get());
        assertTrue(client.objectRefcount(key).get() >= 0L);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void touch(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String key3 = UUID.randomUUID().toString();
        String value = "{value}" + UUID.randomUUID();

        assertEquals(OK, client.set(key1, value).get());
        assertEquals(OK, client.set(key2, value).get());

        assertEquals(2, client.touch(new String[] {key1, key2}).get());
        assertEquals(0, client.touch(new String[] {key3}).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void geoadd(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        Map<String, GeospatialData> membersToCoordinates = new HashMap<>();
        membersToCoordinates.put("Palermo", new GeospatialData(13.361389, 38.115556));
        membersToCoordinates.put("Catania", new GeospatialData(15.087269, 37.502669));

        assertEquals(2, client.geoadd(key1, membersToCoordinates).get());

        membersToCoordinates.put("Catania", new GeospatialData(15.087269, 39));
        assertEquals(
                0,
                client
                        .geoadd(
                                key1,
                                membersToCoordinates,
                                new GeoAddOptions(ConditionalChange.ONLY_IF_DOES_NOT_EXIST))
                        .get());
        assertEquals(
                0,
                client
                        .geoadd(key1, membersToCoordinates, new GeoAddOptions(ConditionalChange.ONLY_IF_EXISTS))
                        .get());

        membersToCoordinates.put("Catania", new GeospatialData(15.087269, 40));
        membersToCoordinates.put("Tel-Aviv", new GeospatialData(32.0853, 34.7818));
        assertEquals(2, client.geoadd(key1, membersToCoordinates, new GeoAddOptions(true)).get());

        assertEquals(OK, client.set(key2, "bar").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class, () -> client.geoadd(key2, membersToCoordinates).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void geoadd_invalid_args(BaseClient client) {
        String key = UUID.randomUUID().toString();

        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.geoadd(key, Map.of()).get());
        assertTrue(executionException.getCause() instanceof RequestException);

        executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.geoadd(key, Map.of("Place", new GeospatialData(-181, 0))).get());
        assertTrue(executionException.getCause() instanceof RequestException);

        executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.geoadd(key, Map.of("Place", new GeospatialData(181, 0))).get());
        assertTrue(executionException.getCause() instanceof RequestException);

        executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.geoadd(key, Map.of("Place", new GeospatialData(0, 86))).get());
        assertTrue(executionException.getCause() instanceof RequestException);

        executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.geoadd(key, Map.of("Place", new GeospatialData(0, -86))).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void geopos(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String[] members = {"Palermo", "Catania"};
        Double[][] expected = {
            {13.36138933897018433, 38.11555639549629859}, {15.08726745843887329, 37.50266842333162032}
        };

        // adding locations
        Map<String, GeospatialData> membersToCoordinates = new HashMap<>();
        membersToCoordinates.put("Palermo", new GeospatialData(13.361389, 38.115556));
        membersToCoordinates.put("Catania", new GeospatialData(15.087269, 37.502669));
        assertEquals(2, client.geoadd(key1, membersToCoordinates).get());

        // Loop through the arrays and perform assertions
        Double[][] actual = client.geopos(key1, members).get();
        for (int i = 0; i < expected.length; i++) {
            for (int j = 0; j < expected[i].length; j++) {
                assertEquals(expected[i][j], actual[i][j], 1e-9);
            }
        }

        // key exists but holding the wrong kind of value (non-ZSET)
        assertEquals(OK, client.set(key2, "geopos").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.geopos(key2, members).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void geodist(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String member1 = "Palermo";
        String member2 = "Catania";
        String member3 = "NonExisting";
        GeoUnit geoUnitKM = GeoUnit.KILOMETERS;
        Double expected = 166274.1516;
        Double expectedKM = 166.2742;
        Double delta = 1e-9;

        // adding locations
        Map<String, GeospatialData> membersToCoordinates = new HashMap<>();
        membersToCoordinates.put("Palermo", new GeospatialData(13.361389, 38.115556));
        membersToCoordinates.put("Catania", new GeospatialData(15.087269, 37.502669));
        assertEquals(2, client.geoadd(key1, membersToCoordinates).get());

        // assert correct result with default metric
        Double actual = client.geodist(key1, member1, member2).get();
        assertEquals(expected, actual, delta);

        // assert correct result with manual metric specification kilometers
        Double actualKM = client.geodist(key1, member1, member2, geoUnitKM).get();
        assertEquals(expectedKM, actualKM, delta);

        // assert null result when member index is missing
        Double actualMissing = client.geodist(key1, member1, member3).get();
        assertNull(actualMissing);

        // key exists but holds a non-ZSET value
        assertEquals(OK, client.set(key2, "geodist").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.geodist(key2, member1, member2).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void geohash(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String[] members = {"Palermo", "Catania", "NonExisting"};
        String[] empty = {};
        String[] expected = {"sqc8b49rny0", "sqdtr74hyu0", null};

        // adding locations
        Map<String, GeospatialData> membersToCoordinates = new HashMap<>();
        membersToCoordinates.put("Palermo", new GeospatialData(13.361389, 38.115556));
        membersToCoordinates.put("Catania", new GeospatialData(15.087269, 37.502669));
        assertEquals(2, client.geoadd(key1, membersToCoordinates).get());

        String[] actual = client.geohash(key1, members).get();
        assertArrayEquals(expected, actual);

        // members array is empty
        assertEquals(client.geohash(key1, empty).get().length, 0);

        // key exists but holding the wrong kind of value (non-ZSET)
        assertEquals(OK, client.set(key2, "geohash").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.geohash(key2, members).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void bitcount(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String missingKey = "missing";
        String value = "foobar";

        assertEquals(OK, client.set(key1, value).get());
        assertEquals(1, client.sadd(key2, new String[] {value}).get());
        assertEquals(26, client.bitcount(key1).get());
        assertEquals(6, client.bitcount(key1, 1, 1).get());
        assertEquals(10, client.bitcount(key1, 0, -5).get());
        assertEquals(0, client.bitcount(missingKey, 5, 30).get());
        assertEquals(0, client.bitcount(missingKey).get());

        // Exception thrown due to the key holding a value with the wrong type
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.bitcount(key2).get());
        assertTrue(executionException.getCause() instanceof RequestException);

        // Exception thrown due to the key holding a value with the wrong type
        executionException =
                assertThrows(ExecutionException.class, () -> client.bitcount(key2, 1, 1).get());
        assertTrue(executionException.getCause() instanceof RequestException);

        if (REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            assertEquals(16L, client.bitcount(key1, 2, 5, BitmapIndexType.BYTE).get());
            assertEquals(17L, client.bitcount(key1, 5, 30, BitmapIndexType.BIT).get());
            assertEquals(23, client.bitcount(key1, 5, -5, BitmapIndexType.BIT).get());
            assertEquals(0, client.bitcount(missingKey, 5, 30, BitmapIndexType.BIT).get());

            // Exception thrown due to the key holding a value with the wrong type
            executionException =
                    assertThrows(
                            ExecutionException.class,
                            () -> client.bitcount(key2, 1, 1, BitmapIndexType.BIT).get());
            assertTrue(executionException.getCause() instanceof RequestException);
        } else {
            // Exception thrown because BIT and BYTE options were implemented after 7.0.0
            executionException =
                    assertThrows(
                            ExecutionException.class,
                            () -> client.bitcount(key1, 2, 5, BitmapIndexType.BYTE).get());
            assertTrue(executionException.getCause() instanceof RequestException);

            // Exception thrown because BIT and BYTE options were implemented after 7.0.0
            executionException =
                    assertThrows(
                            ExecutionException.class,
                            () -> client.bitcount(key1, 5, 30, BitmapIndexType.BIT).get());
            assertTrue(executionException.getCause() instanceof RequestException);
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void setbit(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();

        assertEquals(0, client.setbit(key1, 0, 1).get());
        assertEquals(1, client.setbit(key1, 0, 0).get());

        // Exception thrown due to the negative offset
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.setbit(key1, -1, 1).get());
        assertTrue(executionException.getCause() instanceof RequestException);

        // Exception thrown due to the value set not being 0 or 1
        executionException =
                assertThrows(ExecutionException.class, () -> client.setbit(key1, 1, 2).get());
        assertTrue(executionException.getCause() instanceof RequestException);

        // Exception thrown: key is not a string
        assertEquals(1, client.sadd(key2, new String[] {"value"}).get());
        executionException =
                assertThrows(ExecutionException.class, () -> client.setbit(key2, 1, 1).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void getbit(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String missingKey = UUID.randomUUID().toString();
        String value = "foobar";

        assertEquals(OK, client.set(key1, value).get());
        assertEquals(1, client.getbit(key1, 1).get());
        assertEquals(0, client.getbit(key1, 1000).get());
        assertEquals(0, client.getbit(missingKey, 1).get());
        if (client instanceof RedisClient) {
            assertEquals(
                    1L, ((RedisClient) client).customCommand(new String[] {"SETBIT", key1, "5", "0"}).get());
            assertEquals(0, client.getbit(key1, 5).get());
        }

        // Exception thrown due to the negative offset
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.getbit(key1, -1).get());
        assertTrue(executionException.getCause() instanceof RequestException);

        // Exception thrown due to the key holding a value with the wrong type
        assertEquals(1, client.sadd(key2, new String[] {value}).get());
        executionException = assertThrows(ExecutionException.class, () -> client.getbit(key2, 1).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void bitpos(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String key3 = UUID.randomUUID().toString();
        String value = "?f0obar"; // 00111111 01100110 00110000 01101111 01100010 01100001 01110010

        assertEquals(OK, client.set(key1, value).get());
        assertEquals(0, client.bitpos(key1, 0).get());
        assertEquals(2, client.bitpos(key1, 1).get());
        assertEquals(9, client.bitpos(key1, 1, 1).get());
        assertEquals(24, client.bitpos(key1, 0, 3, 5).get());

        // Bitpos returns -1 for empty strings
        assertEquals(-1, client.bitpos(key2, 1).get());
        assertEquals(-1, client.bitpos(key2, 1, 1).get());
        assertEquals(-1, client.bitpos(key2, 1, 3, 5).get());

        // Exception thrown due to the key holding a value with the wrong type
        assertEquals(1, client.sadd(key3, new String[] {value}).get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.bitpos(key3, 0).get());
        assertTrue(executionException.getCause() instanceof RequestException);
        executionException =
                assertThrows(ExecutionException.class, () -> client.bitpos(key3, 1, 2).get());
        assertTrue(executionException.getCause() instanceof RequestException);
        executionException =
                assertThrows(ExecutionException.class, () -> client.bitpos(key3, 0, 1, 4).get());
        assertTrue(executionException.getCause() instanceof RequestException);

        if (REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            assertEquals(24, client.bitpos(key1, 0, 3, 5, BitmapIndexType.BYTE).get());
            assertEquals(47, client.bitpos(key1, 1, 43, -2, BitmapIndexType.BIT).get());
            assertEquals(-1, client.bitpos(key2, 1, 3, 5, BitmapIndexType.BYTE).get());
            assertEquals(-1, client.bitpos(key2, 1, 3, 5, BitmapIndexType.BIT).get());

            // Exception thrown due to the key holding a value with the wrong type
            executionException =
                    assertThrows(
                            ExecutionException.class,
                            () -> client.bitpos(key3, 1, 4, 5, BitmapIndexType.BIT).get());
            assertTrue(executionException.getCause() instanceof RequestException);
        } else {
            // Exception thrown because BIT and BYTE options were implemented after 7.0.0
            executionException =
                    assertThrows(
                            ExecutionException.class,
                            () -> client.bitpos(key1, 0, 3, 5, BitmapIndexType.BYTE).get());
            assertTrue(executionException.getCause() instanceof RequestException);

            // Exception thrown because BIT and BYTE options were implemented after 7.0.0
            executionException =
                    assertThrows(
                            ExecutionException.class,
                            () -> client.bitpos(key1, 1, 43, -2, BitmapIndexType.BIT).get());
            assertTrue(executionException.getCause() instanceof RequestException);
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void bitop(BaseClient client) {
        String key1 = "{key}-1".concat(UUID.randomUUID().toString());
        String key2 = "{key}-2".concat(UUID.randomUUID().toString());
        String emptyKey1 = "{key}-3".concat(UUID.randomUUID().toString());
        String emptyKey2 = "{key}-4".concat(UUID.randomUUID().toString());
        String destination = "{key}-5".concat(UUID.randomUUID().toString());
        String[] keys = new String[] {key1, key2};
        String[] emptyKeys = new String[] {emptyKey1, emptyKey2};
        String value1 = "foobar";
        String value2 = "abcdef";

        assertEquals(OK, client.set(key1, value1).get());
        assertEquals(OK, client.set(key2, value2).get());
        assertEquals(6L, client.bitop(BitwiseOperation.AND, destination, keys).get());
        assertEquals("`bc`ab", client.get(destination).get());
        assertEquals(6L, client.bitop(BitwiseOperation.OR, destination, keys).get());
        assertEquals("goofev", client.get(destination).get());

        // Reset values for simplicity of results in XOR
        assertEquals(OK, client.set(key1, "a").get());
        assertEquals(OK, client.set(key2, "b").get());
        assertEquals(1L, client.bitop(BitwiseOperation.XOR, destination, keys).get());
        assertEquals("\u0003", client.get(destination).get());

        // Test single source key
        assertEquals(1L, client.bitop(BitwiseOperation.AND, destination, new String[] {key1}).get());
        assertEquals("a", client.get(destination).get());
        assertEquals(1L, client.bitop(BitwiseOperation.OR, destination, new String[] {key1}).get());
        assertEquals("a", client.get(destination).get());
        assertEquals(1L, client.bitop(BitwiseOperation.XOR, destination, new String[] {key1}).get());
        assertEquals("a", client.get(destination).get());
        assertEquals(1L, client.bitop(BitwiseOperation.NOT, destination, new String[] {key1}).get());
        // First bit is flipped to 1 and throws 'utf-8' codec can't decode byte 0x9e in position 0:
        // invalid start byte
        // TODO: update once fix is implemented for https://github.com/aws/glide-for-redis/issues/1447
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.get(destination).get());
        assertTrue(executionException.getCause() instanceof RuntimeException);
        assertEquals(0, client.setbit(key1, 0, 1).get());
        assertEquals(1L, client.bitop(BitwiseOperation.NOT, destination, new String[] {key1}).get());
        assertEquals("\u001e", client.get(destination).get());

        // Returns null when all keys hold empty strings
        assertEquals(0L, client.bitop(BitwiseOperation.AND, destination, emptyKeys).get());
        assertEquals(null, client.get(destination).get());
        assertEquals(0L, client.bitop(BitwiseOperation.OR, destination, emptyKeys).get());
        assertEquals(null, client.get(destination).get());
        assertEquals(0L, client.bitop(BitwiseOperation.XOR, destination, emptyKeys).get());
        assertEquals(null, client.get(destination).get());
        assertEquals(
                0L, client.bitop(BitwiseOperation.NOT, destination, new String[] {emptyKey1}).get());
        assertEquals(null, client.get(destination).get());

        // Exception thrown due to the key holding a value with the wrong type
        assertEquals(1, client.sadd(emptyKey1, new String[] {value1}).get());
        executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.bitop(BitwiseOperation.AND, destination, new String[] {emptyKey1}).get());

        // Source keys is an empty list
        executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.bitop(BitwiseOperation.OR, destination, new String[] {}).get());
        assertTrue(executionException.getCause() instanceof RequestException);

        // NOT with more than one source key
        executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.bitop(BitwiseOperation.NOT, destination, new String[] {key1, key2}).get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void lmpop(BaseClient client) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7");
        // setup
        String key1 = "{key}-1" + UUID.randomUUID();
        String key2 = "{key}-2" + UUID.randomUUID();
        String nonListKey = "{key}-3" + UUID.randomUUID();
        String[] singleKeyArray = {key1};
        String[] multiKeyArray = {key2, key1};
        long count = 1L;
        Long arraySize = 5L;
        String[] lpushArgs = {"one", "two", "three", "four", "five"};
        Map<String, String[]> expected = Map.of(key1, new String[] {"five"});
        Map<String, String[]> expected2 = Map.of(key2, new String[] {"one", "two"});

        // nothing to be popped
        assertNull(client.lmpop(singleKeyArray, ListDirection.LEFT).get());
        assertNull(client.lmpop(singleKeyArray, ListDirection.LEFT, count).get());

        // pushing to the arrays to be popped
        assertEquals(arraySize, client.lpush(key1, lpushArgs).get());
        assertEquals(arraySize, client.lpush(key2, lpushArgs).get());

        // assert correct result from popping
        Map<String, String[]> result = client.lmpop(singleKeyArray, ListDirection.LEFT).get();
        assertDeepEquals(result, expected);

        // assert popping multiple elements from the right
        Map<String, String[]> result2 = client.lmpop(multiKeyArray, ListDirection.RIGHT, 2L).get();
        assertDeepEquals(result2, expected2);

        // key exists but is not a list type key
        assertEquals(OK, client.set(nonListKey, "lmpop").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.lmpop(new String[] {nonListKey}, ListDirection.LEFT).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void blmpop(BaseClient client) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7");
        // setup
        String key1 = "{key}-1" + UUID.randomUUID();
        String key2 = "{key}-2" + UUID.randomUUID();
        String nonListKey = "{key}-3" + UUID.randomUUID();
        String[] singleKeyArray = {key1};
        String[] multiKeyArray = {key2, key1};
        long count = 1L;
        Long arraySize = 5L;
        String[] lpushArgs = {"one", "two", "three", "four", "five"};
        Map<String, String[]> expected = Map.of(key1, new String[] {"five"});
        Map<String, String[]> expected2 = Map.of(key2, new String[] {"one", "two"});

        // nothing to be popped
        assertNull(client.blmpop(singleKeyArray, ListDirection.LEFT, 0.1).get());
        assertNull(client.blmpop(singleKeyArray, ListDirection.LEFT, count, 0.1).get());

        // pushing to the arrays to be popped
        assertEquals(arraySize, client.lpush(key1, lpushArgs).get());
        assertEquals(arraySize, client.lpush(key2, lpushArgs).get());

        // assert correct result from popping
        Map<String, String[]> result = client.blmpop(singleKeyArray, ListDirection.LEFT, 0.1).get();
        assertDeepEquals(result, expected);

        // assert popping multiple elements from the right
        Map<String, String[]> result2 =
                client.blmpop(multiKeyArray, ListDirection.RIGHT, 2L, 0.1).get();
        assertDeepEquals(result2, expected2);

        // key exists but is not a list type key
        assertEquals(OK, client.set(nonListKey, "blmpop").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.blmpop(new String[] {nonListKey}, ListDirection.LEFT, 0.1).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void blmpop_timeout_check(BaseClient client) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7");
        String key = UUID.randomUUID().toString();
        // create new client with default request timeout (250 millis)
        try (var testClient =
                client instanceof RedisClient
                        ? RedisClient.CreateClient(commonClientConfig().build()).get()
                        : RedisClusterClient.CreateClient(commonClusterClientConfig().build()).get()) {

            // ensure that commands doesn't time out even if timeout > request timeout
            assertNull(testClient.blmpop(new String[] {key}, ListDirection.LEFT, 1).get());

            // with 0 timeout (no timeout) should never time out,
            // but we wrap the test with timeout to avoid test failing or stuck forever
            assertThrows(
                    TimeoutException.class, // <- future timeout, not command timeout
                    () ->
                            testClient
                                    .blmpop(new String[] {key}, ListDirection.LEFT, 0)
                                    .get(3, TimeUnit.SECONDS));
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void lset(BaseClient client) {
        // setup
        String key = UUID.randomUUID().toString();
        String nonExistingKey = UUID.randomUUID().toString();
        long index = 0;
        long oobIndex = 10;
        long negativeIndex = -1;
        String element = "zero";
        String[] lpushArgs = {"four", "three", "two", "one"};
        String[] expectedList = {"zero", "two", "three", "four"};
        String[] expectedList2 = {"zero", "two", "three", "zero"};

        // key does not exist
        ExecutionException noSuchKeyException =
                assertThrows(
                        ExecutionException.class, () -> client.lset(nonExistingKey, index, element).get());
        assertInstanceOf(RequestException.class, noSuchKeyException.getCause());

        // pushing elements to list
        client.lpush(key, lpushArgs).get();

        // index out of range
        ExecutionException indexOutOfBoundException =
                assertThrows(ExecutionException.class, () -> client.lset(key, oobIndex, element).get());
        assertInstanceOf(RequestException.class, indexOutOfBoundException.getCause());

        // assert lset result
        String response = client.lset(key, index, element).get();
        assertEquals(OK, response);
        String[] updatedList = client.lrange(key, 0, -1).get();
        assertArrayEquals(updatedList, expectedList);

        // assert lset with a negative index for the last element in the list
        String response2 = client.lset(key, negativeIndex, element).get();
        assertEquals(OK, response2);
        String[] updatedList2 = client.lrange(key, 0, -1).get();
        assertArrayEquals(updatedList2, expectedList2);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void lmove(BaseClient client) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("6.2.0"), "This feature added in redis 6.2.0");
        // setup
        String key1 = "{key}-1" + UUID.randomUUID();
        String key2 = "{key}-2" + UUID.randomUUID();
        String nonExistingKey = "{key}-3" + UUID.randomUUID();
        String nonListKey = "{key}-4" + UUID.randomUUID();
        String[] lpushArgs1 = {"four", "three", "two", "one"};
        String[] lpushArgs2 = {"six", "five", "four"};

        // source does not exist or is empty
        assertNull(client.lmove(key1, key2, ListDirection.LEFT, ListDirection.RIGHT).get());

        // only source exists, only source elements gets popped, creates a list at nonExistingKey
        assertEquals(lpushArgs1.length, client.lpush(key1, lpushArgs1).get());
        assertEquals(
                "four", client.lmove(key1, nonExistingKey, ListDirection.RIGHT, ListDirection.LEFT).get());
        assertArrayEquals(new String[] {"one", "two", "three"}, client.lrange(key1, 0, -1).get());

        // source and destination are the same, performing list rotation, "three" gets popped and added
        // back
        assertEquals("one", client.lmove(key1, key1, ListDirection.LEFT, ListDirection.LEFT).get());
        assertArrayEquals(new String[] {"one", "two", "three"}, client.lrange(key1, 0, -1).get());

        // normal use case, "three" gets popped and added to the left of destination
        assertEquals(lpushArgs2.length, client.lpush(key2, lpushArgs2).get());
        assertEquals("three", client.lmove(key1, key2, ListDirection.RIGHT, ListDirection.LEFT).get());
        assertArrayEquals(new String[] {"one", "two"}, client.lrange(key1, 0, -1).get());
        assertArrayEquals(
                new String[] {"three", "four", "five", "six"}, client.lrange(key2, 0, -1).get());

        // source exists but is not a list type key
        assertEquals(OK, client.set(nonListKey, "NotAList").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> client.lmove(nonListKey, key1, ListDirection.LEFT, ListDirection.LEFT).get());
        assertInstanceOf(RequestException.class, executionException.getCause());

        // destination exists but is not a list type key
        ExecutionException executionException2 =
                assertThrows(
                        ExecutionException.class,
                        () -> client.lmove(key1, nonListKey, ListDirection.LEFT, ListDirection.LEFT).get());
        assertInstanceOf(RequestException.class, executionException2.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void blmove(BaseClient client) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("6.2.0"), "This feature added in redis 6.2.0");
        // setup
        String key1 = "{key}-1" + UUID.randomUUID();
        String key2 = "{key}-2" + UUID.randomUUID();
        String nonExistingKey = "{key}-3" + UUID.randomUUID();
        String nonListKey = "{key}-4" + UUID.randomUUID();
        String[] lpushArgs1 = {"four", "three", "two", "one"};
        String[] lpushArgs2 = {"six", "five", "four"};
        double timeout = 1;

        // source does not exist or is empty
        assertNull(client.blmove(key1, key2, ListDirection.LEFT, ListDirection.RIGHT, timeout).get());

        // only source exists, only source elements gets popped, creates a list at nonExistingKey
        assertEquals(lpushArgs1.length, client.lpush(key1, lpushArgs1).get());
        assertEquals(
                "four",
                client
                        .blmove(key1, nonExistingKey, ListDirection.RIGHT, ListDirection.LEFT, timeout)
                        .get());
        assertArrayEquals(new String[] {"one", "two", "three"}, client.lrange(key1, 0, -1).get());

        // source and destination are the same, performing list rotation, "three" gets popped and added
        // back
        assertEquals(
                "one", client.blmove(key1, key1, ListDirection.LEFT, ListDirection.LEFT, timeout).get());
        assertArrayEquals(new String[] {"one", "two", "three"}, client.lrange(key1, 0, -1).get());

        // normal use case, "three" gets popped and added to the left of destination
        assertEquals(lpushArgs2.length, client.lpush(key2, lpushArgs2).get());
        assertEquals(
                "three", client.blmove(key1, key2, ListDirection.RIGHT, ListDirection.LEFT, timeout).get());
        assertArrayEquals(new String[] {"one", "two"}, client.lrange(key1, 0, -1).get());
        assertArrayEquals(
                new String[] {"three", "four", "five", "six"}, client.lrange(key2, 0, -1).get());

        // source exists but is not a list type key
        assertEquals(OK, client.set(nonListKey, "NotAList").get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .blmove(nonListKey, key1, ListDirection.LEFT, ListDirection.LEFT, timeout)
                                        .get());
        assertInstanceOf(RequestException.class, executionException.getCause());

        // destination exists but is not a list type key
        ExecutionException executionException2 =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .blmove(key1, nonListKey, ListDirection.LEFT, ListDirection.LEFT, timeout)
                                        .get());
        assertInstanceOf(RequestException.class, executionException2.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void blmove_timeout_check(BaseClient client) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("6.2.0"), "This feature added in redis 6.2.0");
        String key1 = "{key}-1" + UUID.randomUUID();
        String key2 = "{key}-2" + UUID.randomUUID();
        // create new client with default request timeout (250 millis)
        try (var testClient =
                client instanceof RedisClient
                        ? RedisClient.CreateClient(commonClientConfig().build()).get()
                        : RedisClusterClient.CreateClient(commonClusterClientConfig().build()).get()) {

            // ensure that commands doesn't time out even if timeout > request timeout
            assertNull(testClient.blmove(key1, key2, ListDirection.LEFT, ListDirection.LEFT, 1).get());

            // with 0 timeout (no timeout) should never time out,
            // but we wrap the test with timeout to avoid test failing or stuck forever
            assertThrows(
                    TimeoutException.class, // <- future timeout, not command timeout
                    () ->
                            testClient
                                    .blmove(key1, key2, ListDirection.LEFT, ListDirection.LEFT, 0)
                                    .get(3, TimeUnit.SECONDS));
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void srandmember(BaseClient client) {
        // setup
        String key = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String nonExistingKey = "nonExisting";
        String nonSetKey = "NonSet";
        long count = 2;
        long countNegative = -2;
        String[] singleArr = new String[] {"one"};

        // expected results
        String expectedNoCount = "one";
        String[] expectedNegCount = new String[] {"one", "one"};

        // key does not exist, without count the command returns null, and with count command returns an
        // empty array
        assertNull(client.srandmember(nonExistingKey).get());
        assertEquals(0, client.srandmember(nonExistingKey, count).get().length);

        // adding element to set
        client.sadd(key, singleArr).get();

        // with no count or a positive count, single array result should only contain element "one"
        String resultNoCount = client.srandmember(key).get();
        assertEquals(resultNoCount, expectedNoCount);
        String[] resultPosCount = client.srandmember(key, count).get();
        assertArrayEquals(resultPosCount, singleArr);

        // with negative count, the same element can be returned multiple times
        String[] resultNegCount = client.srandmember(key, countNegative).get();
        assertArrayEquals(resultNegCount, expectedNegCount);

        // key exists but is not a list type key
        assertEquals(OK, client.set(nonSetKey, "notaset").get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.srandmember(nonSetKey).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        ExecutionException executionExceptionWithCount =
                assertThrows(ExecutionException.class, () -> client.srandmember(nonSetKey, count).get());
        assertInstanceOf(RequestException.class, executionExceptionWithCount.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void spop_spopCount(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String stringKey = UUID.randomUUID().toString();
        String nonExistingKey = UUID.randomUUID().toString();
        String member1 = UUID.randomUUID().toString();
        String member2 = UUID.randomUUID().toString();
        String member3 = UUID.randomUUID().toString();

        assertEquals(1, client.sadd(key, new String[] {member1}).get());
        assertEquals(member1, client.spop(key).get());

        assertEquals(3, client.sadd(key, new String[] {member1, member2, member3}).get());
        // Pop with count value greater than the size of the set
        assertEquals(Set.of(member1, member2, member3), client.spopCount(key, 4).get());
        assertEquals(0, client.scard(key).get());

        assertEquals(3, client.sadd(key, new String[] {member1, member2, member3}).get());
        assertEquals(Set.of(), client.spopCount(key, 0).get());

        assertNull(client.spop(nonExistingKey).get());
        assertEquals(Set.of(), client.spopCount(nonExistingKey, 3).get());

        // invalid argument - count must be positive
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.spopCount(key, -1).get());
        assertInstanceOf(RequestException.class, executionException.getCause());

        // key exists but is not a set
        assertEquals(OK, client.set(stringKey, "foo").get());
        executionException = assertThrows(ExecutionException.class, () -> client.spop(stringKey).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        executionException =
                assertThrows(ExecutionException.class, () -> client.spopCount(stringKey, 3).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void bitfieldReadOnly(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        BitFieldGet unsignedOffsetGet = new BitFieldGet(new UnsignedEncoding(2), new Offset(1));
        String emptyKey = UUID.randomUUID().toString();
        String foobar = "foobar";

        client.set(key1, foobar);
        assertArrayEquals(
                new Long[] {3L, -2L, 118L, 111L},
                client
                        .bitfieldReadOnly(
                                key1,
                                new BitFieldReadOnlySubCommands[] {
                                    // Get value in: 0(11)00110 01101111 01101111 01100010 01100001 01110010 00010100
                                    unsignedOffsetGet,
                                    // Get value in: 01100(110) 01101111 01101111 01100010 01100001 01110010 00010100
                                    new BitFieldGet(new SignedEncoding(3), new Offset(5)),
                                    // Get value in: 01100110 01101111 01101(111 0110)0010 01100001 01110010 00010100
                                    new BitFieldGet(new UnsignedEncoding(7), new OffsetMultiplier(3)),
                                    // Get value in: 01100110 01101111 (01101111) 01100010 01100001 01110010 00010100
                                    new BitFieldGet(new SignedEncoding(8), new OffsetMultiplier(2))
                                })
                        .get());
        assertArrayEquals(
                new Long[] {0L},
                client
                        .bitfieldReadOnly(emptyKey, new BitFieldReadOnlySubCommands[] {unsignedOffsetGet})
                        .get());

        // Empty subcommands return an empty array
        assertArrayEquals(
                new Long[] {}, client.bitfieldReadOnly(key2, new BitFieldReadOnlySubCommands[] {}).get());

        // Exception thrown due to the key holding a value with the wrong type
        assertEquals(1, client.sadd(key2, new String[] {foobar}).get());
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .bitfieldReadOnly(key2, new BitFieldReadOnlySubCommands[] {unsignedOffsetGet})
                                        .get());
        assertTrue(executionException.getCause() instanceof RequestException);

        // Offset must be >= 0
        executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .bitfieldReadOnly(
                                                key1,
                                                new BitFieldReadOnlySubCommands[] {
                                                    new BitFieldGet(new UnsignedEncoding(5), new Offset(-1))
                                                })
                                        .get());
        assertTrue(executionException.getCause() instanceof RequestException);

        // Encoding must be > 0
        executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .bitfieldReadOnly(
                                                key1,
                                                new BitFieldReadOnlySubCommands[] {
                                                    new BitFieldGet(new UnsignedEncoding(-1), new Offset(1))
                                                })
                                        .get());
        assertTrue(executionException.getCause() instanceof RequestException);

        // Encoding must be < 64 for unsigned bit encoding
        executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .bitfieldReadOnly(
                                                key1,
                                                new BitFieldReadOnlySubCommands[] {
                                                    new BitFieldGet(new UnsignedEncoding(64), new Offset(1))
                                                })
                                        .get());
        assertTrue(executionException.getCause() instanceof RequestException);

        // Encoding must be < 65 for signed bit encoding
        executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .bitfieldReadOnly(
                                                key1,
                                                new BitFieldReadOnlySubCommands[] {
                                                    new BitFieldGet(new SignedEncoding(65), new Offset(1))
                                                })
                                        .get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void bitfield(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String setKey = UUID.randomUUID().toString();
        String foobar = "foobar";
        UnsignedEncoding u2 = new UnsignedEncoding(2);
        UnsignedEncoding u7 = new UnsignedEncoding(7);
        SignedEncoding i3 = new SignedEncoding(3);
        SignedEncoding i8 = new SignedEncoding(8);
        Offset offset1 = new Offset(1);
        Offset offset5 = new Offset(5);
        OffsetMultiplier offsetMultiplier4 = new OffsetMultiplier(4);
        OffsetMultiplier offsetMultiplier8 = new OffsetMultiplier(8);
        BitFieldSet overflowSet = new BitFieldSet(u2, offset1, -10);
        BitFieldGet overflowGet = new BitFieldGet(u2, offset1);

        client.set(key1, foobar); // binary value: 01100110 01101111 01101111 01100010 01100001 01110010

        // SET tests
        assertArrayEquals(
                new Long[] {3L, -2L, 19L, 0L, 2L, 3L, 18L, 20L},
                client
                        .bitfield(
                                key1,
                                new BitFieldSubCommands[] {
                                    // binary value becomes: 0(10)00110 01101111 01101111 01100010 01100001 01110010
                                    new BitFieldSet(u2, offset1, 2),
                                    // binary value becomes: 01000(011) 01101111 01101111 01100010 01100001 01110010
                                    new BitFieldSet(i3, offset5, 3),
                                    // binary value becomes: 01000011 01101111 01101111 0110(0010 010)00001 01110010
                                    new BitFieldSet(u7, offsetMultiplier4, 18),
                                    // binary value becomes: 01000011 01101111 01101111 01100010 01000001 01110010
                                    // 00000000 00000000 (00010100)
                                    new BitFieldSet(i8, offsetMultiplier8, 20),
                                    new BitFieldGet(u2, offset1),
                                    new BitFieldGet(i3, offset5),
                                    new BitFieldGet(u7, offsetMultiplier4),
                                    new BitFieldGet(i8, offsetMultiplier8)
                                })
                        .get());

        // INCRBY tests
        assertArrayEquals(
                new Long[] {3L, -3L, 15L, 30L},
                client
                        .bitfield(
                                key1,
                                new BitFieldSubCommands[] {
                                    // binary value becomes: 0(11)00011 01101111 01101111 01100010 01000001 01110010
                                    // 00000000 00000000  00010100
                                    new BitFieldIncrby(u2, offset1, 1),
                                    // binary value becomes: 01100(101) 01101111 01101111 01100010 01000001 01110010
                                    // 00000000 00000000 00010100
                                    new BitFieldIncrby(i3, offset5, 2),
                                    // binary value becomes: 01100101 01101111 01101111 0110(0001 111)00001 01110010
                                    // 00000000 00000000 00010100
                                    new BitFieldIncrby(u7, offsetMultiplier4, -3),
                                    // binary value becomes: 01100101 01101111 01101111 01100001 11100001 01110010
                                    // 00000000 00000000 (00011110)
                                    new BitFieldIncrby(i8, offsetMultiplier8, 10)
                                })
                        .get());

        // OVERFLOW WRAP is used by default if no OVERFLOW is specified
        assertArrayEquals(
                new Long[] {0L, 2L, 2L},
                client
                        .bitfield(
                                key2,
                                new BitFieldSubCommands[] {
                                    overflowSet,
                                    new BitFieldOverflow(BitOverflowControl.WRAP),
                                    overflowSet,
                                    overflowGet
                                })
                        .get());

        // OVERFLOW affects only SET or INCRBY after OVERFLOW subcommand
        assertArrayEquals(
                new Long[] {2L, 2L, 3L, null},
                client
                        .bitfield(
                                key2,
                                new BitFieldSubCommands[] {
                                    overflowSet,
                                    new BitFieldOverflow(BitOverflowControl.SAT),
                                    overflowSet,
                                    overflowGet,
                                    new BitFieldOverflow(BitOverflowControl.FAIL),
                                    overflowSet
                                })
                        .get());

        // Empty subcommands return an empty array
        assertArrayEquals(new Long[] {}, client.bitfield(key2, new BitFieldSubCommands[] {}).get());

        // Exceptions
        // Encoding must be > 0
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .bitfield(
                                                key1,
                                                new BitFieldSubCommands[] {
                                                    new BitFieldSet(new UnsignedEncoding(-1), new Offset(1), 1)
                                                })
                                        .get());
        assertTrue(executionException.getCause() instanceof RequestException);

        // Offset must be > 0
        executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .bitfield(
                                                key1,
                                                new BitFieldSubCommands[] {
                                                    new BitFieldIncrby(new UnsignedEncoding(5), new Offset(-1), 1)
                                                })
                                        .get());
        assertTrue(executionException.getCause() instanceof RequestException);

        // Unsigned bit encoding must be < 64
        executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .bitfield(
                                                key1,
                                                new BitFieldSubCommands[] {
                                                    new BitFieldIncrby(new UnsignedEncoding(64), new Offset(1), 1)
                                                })
                                        .get());
        assertTrue(executionException.getCause() instanceof RequestException);

        // Signed bit encoding must be < 65
        executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .bitfield(
                                                key1,
                                                new BitFieldSubCommands[] {
                                                    new BitFieldSet(new SignedEncoding(65), new Offset(1), 1)
                                                })
                                        .get());
        assertTrue(executionException.getCause() instanceof RequestException);

        // Exception thrown due to the key holding a value with the wrong type
        assertEquals(1, client.sadd(setKey, new String[] {foobar}).get());
        executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .bitfield(
                                                setKey,
                                                new BitFieldSubCommands[] {
                                                    new BitFieldSet(new SignedEncoding(3), new Offset(1), 2)
                                                })
                                        .get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void sintercard(BaseClient client) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7.0.0");
        // setup
        String key1 = "{key}-1" + UUID.randomUUID();
        String key2 = "{key}-2" + UUID.randomUUID();
        String nonSetKey = "{key}-4" + UUID.randomUUID();
        String[] saddargs = {"one", "two", "three", "four"};
        String[] saddargs2 = {"two", "three", "four", "five"};
        long limit = 2;
        long limit2 = 4;

        // keys does not exist or is empty
        String[] keys = {key1, key2};
        assertEquals(0, client.sintercard(keys).get());
        assertEquals(0, client.sintercard(keys, limit).get());

        // one of the keys is empty, intersection is empty, cardinality equals to 0
        assertEquals(4, client.sadd(key1, saddargs).get());
        assertEquals(0, client.sintercard(keys).get());

        // sets at both keys have value, get cardinality of the intersection
        assertEquals(4, client.sadd(key2, saddargs2).get());
        assertEquals(3, client.sintercard(keys).get());

        // returns limit as cardinality when the limit is reached partway through the computation
        assertEquals(limit, client.sintercard(keys, limit).get());

        // non set keys are used
        assertEquals(OK, client.set(nonSetKey, "NotASet").get());
        String[] badArr = new String[] {key1, nonSetKey};
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.sintercard(badArr).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void copy(BaseClient client) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("6.2.0"), "This feature added in redis 6.2.0");
        // setup
        String source = "{key}-1" + UUID.randomUUID();
        String destination = "{key}-2" + UUID.randomUUID();

        // neither key exists, returns false
        assertFalse(client.copy(source, destination, false).get());
        assertFalse(client.copy(source, destination).get());

        // source exists, destination does not
        client.set(source, "one");
        assertTrue(client.copy(source, destination, false).get());
        assertEquals("one", client.get(destination).get());

        // setting new value for source
        client.set(source, "two");

        // both exists, no REPLACE
        assertFalse(client.copy(source, destination).get());
        assertFalse(client.copy(source, destination, false).get());
        assertEquals("one", client.get(destination).get());

        // both exists, with REPLACE
        assertTrue(client.copy(source, destination, true).get());
        assertEquals("two", client.get(destination).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void msetnx(BaseClient client) {
        // keys are from different slots
        String key1 = "{key}-1" + UUID.randomUUID();
        String key2 = "{key}-2" + UUID.randomUUID();
        String key3 = "{key}-3" + UUID.randomUUID();
        String nonExisting = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        Map<String, String> keyValueMap1 = Map.of(key1, value, key2, value);
        Map<String, String> keyValueMap2 = Map.of(key2, value, key3, value);

        // all keys are empty, successfully set
        assertTrue(client.msetnx(keyValueMap1).get());
        assertArrayEquals(
                new String[] {value, value, null},
                client.mget(new String[] {key1, key2, nonExisting}).get());

        // one of the keys is already set, nothing gets set
        assertFalse(client.msetnx(keyValueMap2).get());
        assertNull(client.get(key3).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void lcs(BaseClient client) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7.0.0");
        // setup
        String key1 = "{key}-1" + UUID.randomUUID();
        String key2 = "{key}-2" + UUID.randomUUID();
        String key3 = "{key}-3" + UUID.randomUUID();
        String nonStringKey = "{key}-4" + UUID.randomUUID();

        // keys does not exist or is empty
        assertEquals("", client.lcs(key1, key2).get());

        // setting string values
        client.set(key1, "abcd");
        client.set(key2, "bcde");
        client.set(key3, "wxyz");

        // getting the lcs
        assertEquals("", client.lcs(key1, key3).get());
        assertEquals("bcd", client.lcs(key1, key2).get());

        // non set keys are used
        client.sadd(nonStringKey, new String[] {"setmember"}).get();
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.lcs(nonStringKey, key1).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void lcs_with_len_option(BaseClient client) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7.0.0");
        // setup
        String key1 = "{key}-1" + UUID.randomUUID();
        String key2 = "{key}-2" + UUID.randomUUID();
        String key3 = "{key}-3" + UUID.randomUUID();
        String nonStringKey = "{key}-4" + UUID.randomUUID();

        // keys does not exist or is empty
        assertEquals(0, client.lcsLen(key1, key2).get());

        // setting string values
        client.set(key1, "abcd");
        client.set(key2, "bcde");
        client.set(key3, "wxyz");

        // getting the lcs
        assertEquals(0, client.lcsLen(key1, key3).get());
        assertEquals(3, client.lcsLen(key1, key2).get());

        // non set keys are used
        client.sadd(nonStringKey, new String[] {"setmember"}).get();
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.lcs(nonStringKey, key1).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }
}
