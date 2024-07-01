/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.TestConfiguration.REDIS_VERSION;
import static glide.TestUtilities.assertDeepEquals;
import static glide.api.BaseClient.OK;
import static glide.api.models.commands.SortBaseOptions.OrderBy.DESC;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_PRIMARIES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute.RANDOM;
import static glide.utils.ArrayTransformUtils.concatenateArrays;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.TestConfiguration;
import glide.TransactionTestUtilities.TransactionBuilder;
import glide.api.RedisClusterClient;
import glide.api.models.ClusterTransaction;
import glide.api.models.commands.SortClusterOptions;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotIdRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@Timeout(10) // seconds
public class ClusterTransactionTests {

    private static RedisClusterClient clusterClient = null;

    @BeforeAll
    @SneakyThrows
    public static void init() {
        clusterClient =
                RedisClusterClient.CreateClient(
                                RedisClusterClientConfiguration.builder()
                                        .address(NodeAddress.builder().port(TestConfiguration.CLUSTER_PORTS[0]).build())
                                        .requestTimeout(5000)
                                        .build())
                        .get();
    }

    @AfterAll
    @SneakyThrows
    public static void teardown() {
        clusterClient.close();
    }

    @Test
    @SneakyThrows
    public void custom_command_info() {
        ClusterTransaction transaction = new ClusterTransaction().customCommand(new String[] {"info"});
        Object[] result = clusterClient.exec(transaction).get();
        assertTrue(((String) result[0]).contains("# Stats"));
    }

    @Test
    @SneakyThrows
    public void info_simple_route_test() {
        ClusterTransaction transaction = new ClusterTransaction().info().info();
        Object[] result = clusterClient.exec(transaction, RANDOM).get();

        assertTrue(((String) result[0]).contains("# Stats"));
        assertTrue(((String) result[1]).contains("# Stats"));
    }

    @SneakyThrows
    @ParameterizedTest(name = "{0}")
    @MethodSource("glide.TransactionTestUtilities#getCommonTransactionBuilders")
    public void transactions_with_group_of_commands(String testName, TransactionBuilder builder) {
        ClusterTransaction transaction = new ClusterTransaction();
        Object[] expectedResult = builder.apply(transaction);

        Object[] results = clusterClient.exec(transaction).get();
        assertDeepEquals(expectedResult, results);
    }

    @SneakyThrows
    @ParameterizedTest(name = "{0}")
    @MethodSource("glide.TransactionTestUtilities#getPrimaryNodeTransactionBuilders")
    public void keyless_transactions_with_group_of_commands(
            String testName, TransactionBuilder builder) {
        ClusterTransaction transaction = new ClusterTransaction();
        Object[] expectedResult = builder.apply(transaction);

        SingleNodeRoute route = new SlotIdRoute(1, SlotType.PRIMARY);
        Object[] results = clusterClient.exec(transaction, route).get();
        assertDeepEquals(expectedResult, results);
    }

    @SneakyThrows
    @Test
    public void test_transaction_large_values() {
        int length = 1 << 25; // 33mb
        String key = "0".repeat(length);
        String value = "0".repeat(length);

        ClusterTransaction transaction = new ClusterTransaction();
        transaction.set(key, value);
        transaction.get(key);

        Object[] expectedResult =
                new Object[] {
                    OK, // transaction.set(key, value);
                    value, // transaction.get(key);
                };

        Object[] result = clusterClient.exec(transaction).get();
        assertArrayEquals(expectedResult, result);
    }

    @Test
    @SneakyThrows
    public void lastsave() {
        var yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        var response = clusterClient.exec(new ClusterTransaction().lastsave()).get();
        assertTrue(Instant.ofEpochSecond((long) response[0]).isAfter(yesterday));
    }

    @Test
    @SneakyThrows
    public void objectFreq() {
        String objectFreqKey = "key";
        String maxmemoryPolicy = "maxmemory-policy";
        String oldPolicy =
                clusterClient.configGet(new String[] {maxmemoryPolicy}).get().get(maxmemoryPolicy);
        try {
            ClusterTransaction transaction = new ClusterTransaction();
            transaction.configSet(Map.of(maxmemoryPolicy, "allkeys-lfu"));
            transaction.set(objectFreqKey, "");
            transaction.objectFreq(objectFreqKey);
            var response = clusterClient.exec(transaction).get();
            assertEquals(OK, response[0]);
            assertEquals(OK, response[1]);
            assertTrue((long) response[2] >= 0L);
        } finally {
            clusterClient.configSet(Map.of(maxmemoryPolicy, oldPolicy));
        }
    }

    @Test
    @SneakyThrows
    public void objectIdletime() {
        String objectIdletimeKey = "key";
        ClusterTransaction transaction = new ClusterTransaction();
        transaction.set(objectIdletimeKey, "");
        transaction.objectIdletime(objectIdletimeKey);
        var response = clusterClient.exec(transaction).get();
        assertEquals(OK, response[0]);
        assertTrue((long) response[1] >= 0L);
    }

    @Test
    @SneakyThrows
    public void objectRefcount() {
        String objectRefcountKey = "key";
        ClusterTransaction transaction = new ClusterTransaction();
        transaction.set(objectRefcountKey, "");
        transaction.objectRefcount(objectRefcountKey);
        var response = clusterClient.exec(transaction).get();
        assertEquals(OK, response[0]);
        assertTrue((long) response[1] >= 0L);
    }

    @Test
    @SneakyThrows
    public void zrank_zrevrank_withscores() {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.2.0"));
        String zSetKey1 = "{key}:zsetKey1-" + UUID.randomUUID();
        ClusterTransaction transaction = new ClusterTransaction();
        transaction.zadd(zSetKey1, Map.of("one", 1.0, "two", 2.0, "three", 3.0));
        transaction.zrankWithScore(zSetKey1, "one");
        transaction.zrevrankWithScore(zSetKey1, "one");

        Object[] result = clusterClient.exec(transaction).get();
        assertEquals(3L, result[0]);
        assertArrayEquals(new Object[] {0L, 1.0}, (Object[]) result[1]);
        assertArrayEquals(new Object[] {2L, 1.0}, (Object[]) result[2]);
    }

    @Test
    @SneakyThrows
    public void watch() {
        String key1 = "{key}-1" + UUID.randomUUID();
        String key2 = "{key}-2" + UUID.randomUUID();
        String key3 = "{key}-3" + UUID.randomUUID();
        String key4 = "{key}-4" + UUID.randomUUID();
        String foobarString = "foobar";
        String helloString = "hello";
        String[] keys = new String[] {key1, key2, key3};
        ClusterTransaction setFoobarTransaction = new ClusterTransaction();
        ClusterTransaction setHelloTransaction = new ClusterTransaction();
        String[] expectedExecResponse = new String[] {OK, OK, OK};

        // Returns null when a watched key is modified before it is executed in a transaction command.
        // Transaction commands are not performed.
        assertEquals(OK, clusterClient.watch(keys).get());
        assertEquals(OK, clusterClient.set(key2, helloString).get());
        setFoobarTransaction.set(key1, foobarString).set(key2, foobarString).set(key3, foobarString);
        assertNull(clusterClient.exec(setFoobarTransaction).get()); // Sanity check
        assertNull(clusterClient.get(key1).get());
        assertEquals(helloString, clusterClient.get(key2).get());
        assertNull(clusterClient.get(key3).get());

        // Transaction executes command successfully with a read command on the watch key before
        // transaction is executed.
        assertEquals(OK, clusterClient.watch(keys).get());
        assertEquals(helloString, clusterClient.get(key2).get());
        assertArrayEquals(expectedExecResponse, clusterClient.exec(setFoobarTransaction).get());
        assertEquals(foobarString, clusterClient.get(key1).get()); // Sanity check
        assertEquals(foobarString, clusterClient.get(key2).get());
        assertEquals(foobarString, clusterClient.get(key3).get());

        // Transaction executes command successfully with unmodified watched keys
        assertEquals(OK, clusterClient.watch(keys).get());
        assertArrayEquals(expectedExecResponse, clusterClient.exec(setFoobarTransaction).get());
        assertEquals(foobarString, clusterClient.get(key1).get()); // Sanity check
        assertEquals(foobarString, clusterClient.get(key2).get());
        assertEquals(foobarString, clusterClient.get(key3).get());

        // Transaction executes command successfully with a modified watched key but is not in the
        // transaction.
        assertEquals(OK, clusterClient.watch(new String[] {key4}).get());
        setHelloTransaction.set(key1, helloString).set(key2, helloString).set(key3, helloString);
        assertArrayEquals(expectedExecResponse, clusterClient.exec(setHelloTransaction).get());
        assertEquals(helloString, clusterClient.get(key1).get()); // Sanity check
        assertEquals(helloString, clusterClient.get(key2).get());
        assertEquals(helloString, clusterClient.get(key3).get());

        // WATCH can not have an empty String array parameter
        // Test fails due to https://github.com/amazon-contributing/redis-rs/issues/158
        // ExecutionException executionException =
        //         assertThrows(ExecutionException.class, () -> clusterClient.watch(new String[]
        // {}).get());
        // assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @Test
    @SneakyThrows
    public void unwatch() {
        String key1 = "{key}-1" + UUID.randomUUID();
        String key2 = "{key}-2" + UUID.randomUUID();
        String foobarString = "foobar";
        String helloString = "hello";
        String[] keys = new String[] {key1, key2};
        ClusterTransaction setFoobarTransaction = new ClusterTransaction();
        String[] expectedExecResponse = new String[] {OK, OK};

        // UNWATCH returns OK when there no watched keys
        assertEquals(OK, clusterClient.unwatch().get());

        // Transaction executes successfully after modifying a watched key then calling UNWATCH
        assertEquals(OK, clusterClient.watch(keys).get());
        assertEquals(OK, clusterClient.set(key2, helloString).get());
        assertEquals(OK, clusterClient.unwatch().get());
        assertEquals(OK, clusterClient.unwatch(ALL_PRIMARIES).get());
        setFoobarTransaction.set(key1, foobarString).set(key2, foobarString);
        assertArrayEquals(expectedExecResponse, clusterClient.exec(setFoobarTransaction).get());
        assertEquals(foobarString, clusterClient.get(key1).get());
        assertEquals(foobarString, clusterClient.get(key2).get());
    }

    @Test
    @SneakyThrows
    public void spublish() {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7");
        ClusterTransaction transaction = new ClusterTransaction().spublish("Schannel", "message");

        assertArrayEquals(new Object[] {0L}, clusterClient.exec(transaction).get());
    }

    @Test
    @SneakyThrows
    public void sort() {
        String key1 = "{key}-1" + UUID.randomUUID();
        String key2 = "{key}-2" + UUID.randomUUID();
        String[] descendingList = new String[] {"3", "2", "1"};
        ClusterTransaction transaction = new ClusterTransaction();
        transaction
                .lpush(key1, new String[] {"3", "1", "2"})
                .sort(key1, SortClusterOptions.builder().orderBy(DESC).build())
                .sortStore(key1, key2, SortClusterOptions.builder().orderBy(DESC).build())
                .lrange(key2, 0, -1);

        if (REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            transaction.sortReadOnly(key1, SortClusterOptions.builder().orderBy(DESC).build());
        }

        Object[] results = clusterClient.exec(transaction).get();
        Object[] expectedResult =
                new Object[] {
                    3L, // lpush(key1, new String[] {"3", "1", "2"})
                    descendingList, // sort(key1, SortClusterOptions.builder().orderBy(DESC).build())
                    3L, // sortStore(key1, key2, DESC))
                    descendingList, // lrange(key2, 0, -1)
                };

        if (REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            expectedResult =
                    concatenateArrays(
                            expectedResult, new Object[] {descendingList} // sortReadOnly(key1, DESC)
                            );
        }

        assertDeepEquals(expectedResult, results);
    }

    @SneakyThrows
    @Test
    public void waitTest() {
        // setup
        String key = UUID.randomUUID().toString();
        long numreplicas = 1L;
        long timeout = 1000L;
        ClusterTransaction transaction = new ClusterTransaction();

        transaction.set(key, "value").wait(numreplicas, timeout);
        Object[] results = clusterClient.exec(transaction).get();
        Object[] expectedResult =
                new Object[] {
                    OK, // set(key,  "value")
                    0L, // wait(numreplicas, timeout)
                };
        assertEquals(expectedResult[0], results[0]);
        assertTrue((Long) expectedResult[1] <= (Long) results[1]);
    }
}
