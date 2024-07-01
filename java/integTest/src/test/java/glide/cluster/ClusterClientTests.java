/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.TestConfiguration.REDIS_VERSION;
import static glide.TestUtilities.commonClusterClientConfig;
import static glide.TestUtilities.getRandomString;
import static glide.api.BaseClient.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.api.RedisClusterClient;
import glide.api.models.commands.scan.ClusterScanCursor;
import glide.api.models.commands.scan.ScanOptions;
import glide.api.models.configuration.RedisCredentials;
import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.RequestException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10) // seconds
public class ClusterClientTests {

    @SneakyThrows
    @Test
    public void register_client_name_and_version() {
        String minVersion = "7.2.0";
        assumeTrue(
                REDIS_VERSION.isGreaterThanOrEqualTo(minVersion),
                "Redis version required >= " + minVersion);

        RedisClusterClient client =
                RedisClusterClient.CreateClient(commonClusterClientConfig().build()).get();

        String info =
                (String) client.customCommand(new String[] {"CLIENT", "INFO"}).get().getSingleValue();
        assertTrue(info.contains("lib-name=GlideJava"));
        assertTrue(info.contains("lib-ver=unknown"));

        client.close();
    }

    @SneakyThrows
    @Test
    public void can_connect_with_auth_requirepass() {
        RedisClusterClient client =
                RedisClusterClient.CreateClient(commonClusterClientConfig().build()).get();

        String password = "TEST_AUTH";
        client.customCommand(new String[] {"CONFIG", "SET", "requirepass", password}).get();

        // Creation of a new client without a password should fail
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> RedisClusterClient.CreateClient(commonClusterClientConfig().build()).get());
        assertTrue(exception.getCause() instanceof ClosingException);

        // Creation of a new client with credentials
        RedisClusterClient auth_client =
                RedisClusterClient.CreateClient(
                                commonClusterClientConfig()
                                        .credentials(RedisCredentials.builder().password(password).build())
                                        .build())
                        .get();

        String key = getRandomString(10);
        String value = getRandomString(10);

        assertEquals(OK, auth_client.set(key, value).get());
        assertEquals(value, auth_client.get(key).get());

        // Reset password
        client.customCommand(new String[] {"CONFIG", "SET", "requirepass", ""}).get();

        auth_client.close();
        client.close();
    }

    @SneakyThrows
    @Test
    public void can_connect_with_auth_acl() {
        RedisClusterClient client =
                RedisClusterClient.CreateClient(commonClusterClientConfig().build()).get();

        String username = "testuser";
        String password = "TEST_AUTH";
        assertEquals(
                OK,
                client
                        .customCommand(
                                new String[] {
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
                                    ">" + password,
                                })
                        .get()
                        .getSingleValue());

        String key = getRandomString(10);
        String value = getRandomString(10);

        assertEquals(OK, client.set(key, value).get());

        // Creation of a new cluster client with credentials
        RedisClusterClient testUserClient =
                RedisClusterClient.CreateClient(
                                commonClusterClientConfig()
                                        .credentials(
                                                RedisCredentials.builder().username(username).password(password).build())
                                        .build())
                        .get();

        assertEquals(value, testUserClient.get(key).get());

        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> testUserClient.set("foo", "bar").get());
        assertTrue(executionException.getCause() instanceof RequestException);

        client.customCommand(new String[] {"ACL", "DELUSER", username}).get();

        testUserClient.close();
        client.close();
    }

    @SneakyThrows
    @Test
    public void client_name() {
        RedisClusterClient client =
                RedisClusterClient.CreateClient(
                                commonClusterClientConfig().clientName("TEST_CLIENT_NAME").build())
                        .get();

        String clientInfo =
                (String) client.customCommand(new String[] {"CLIENT", "INFO"}).get().getSingleValue();
        assertTrue(clientInfo.contains("name=TEST_CLIENT_NAME"));

        client.close();
    }

    @Test
    @SneakyThrows
    public void closed_client_throws_ExecutionException_with_ClosingException_as_cause() {
        RedisClusterClient client =
                RedisClusterClient.CreateClient(commonClusterClientConfig().build()).get();

        client.close();
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.set("foo", "bar").get());
        assertTrue(executionException.getCause() instanceof ClosingException);
    }

    @Test
    @SneakyThrows
    public void test_cluster_scan_simple() {
        try (RedisClusterClient client =
                RedisClusterClient.CreateClient(commonClusterClientConfig().build()).get()) {
            assertEquals(OK, client.flushall().get());

            String key = "key:test_cluster_scan_simple" + UUID.randomUUID();
            Map<String, String> expectedData = new LinkedHashMap<>();
            for (int i = 0; i < 100; i++) {
                expectedData.put(key + ":" + i, "value " + i);
            }

            assertEquals(OK, client.mset(expectedData).get());

            Set<String> result = new LinkedHashSet<>();
            ClusterScanCursor cursor = ClusterScanCursor.initalCursor();
            while (!cursor.isFinished()) {
                final Object[] response = client.clusterScan(cursor).get();
                cursor.releaseCursorHandle();

                cursor = (ClusterScanCursor) response[0];
                final Object[] data = (Object[]) response[1];
                for (Object datum : data) {
                    result.add(datum.toString());
                }
            }
            cursor.releaseCursorHandle();

            assertEquals(expectedData.keySet(), result);
        }
    }

    @Test
    @SneakyThrows
    public void test_cluster_scan_with_object_type_and_pattern() {
        try (RedisClusterClient client =
                RedisClusterClient.CreateClient(commonClusterClientConfig().build()).get()) {

            assertEquals(OK, client.flushall().get());
            String key = "key:" + UUID.randomUUID();
            Map<String, String> expectedData = new LinkedHashMap<>();
            final int baseNumberOfEntries = 100;
            for (int i = 0; i < baseNumberOfEntries; i++) {
                expectedData.put(key + ":" + i, "value " + i);
            }

            assertEquals(OK, client.mset(expectedData).get());

            ArrayList<String> unexpectedTypeKeys = new ArrayList<>();
            for (int i = baseNumberOfEntries; i < baseNumberOfEntries + 100; i++) {
                unexpectedTypeKeys.add(key + ":" + i);
            }

            for (String keyStr : unexpectedTypeKeys) {
                assertEquals(1L, client.sadd(keyStr, new String[] {"value"}).get());
            }

            Map<String, String> unexpectedPatterns = new LinkedHashMap<>();
            for (int i = baseNumberOfEntries + 100; i < baseNumberOfEntries + 200; i++) {
                unexpectedPatterns.put("foo:" + i, "value " + i);
            }
            assertEquals(OK, client.mset(unexpectedPatterns).get());

            Set<String> result = new LinkedHashSet<>();
            ClusterScanCursor cursor = ClusterScanCursor.initalCursor();
            while (!cursor.isFinished()) {
                final Object[] response =
                        client
                                .clusterScan(
                                        cursor,
                                        ScanOptions.builder()
                                                .matchPattern("key:*")
                                                .type(ScanOptions.ObjectType.STRING)
                                                .build())
                                .get();
                cursor.releaseCursorHandle();

                cursor = (ClusterScanCursor) response[0];
                final Object[] data = (Object[]) response[1];
                for (Object datum : data) {
                    result.add(datum.toString());
                }
            }
            cursor.releaseCursorHandle();
            assertEquals(expectedData.keySet(), result);

            // Ensure that no unexpected types were in the result.
            assertFalse(new LinkedHashSet<>(result).removeAll(new LinkedHashSet<>(unexpectedTypeKeys)));
            assertFalse(new LinkedHashSet<>(result).removeAll(unexpectedPatterns.keySet()));
        }
    }

    @Test
    @SneakyThrows
    public void test_cluster_scan_with_count() {
        try (RedisClusterClient client =
                RedisClusterClient.CreateClient(commonClusterClientConfig().build()).get()) {

            assertEquals(OK, client.flushall().get());
            String key = "key:" + UUID.randomUUID();
            Map<String, String> expectedData = new LinkedHashMap<>();
            final int baseNumberOfEntries = 2000;
            for (int i = 0; i < baseNumberOfEntries; i++) {
                expectedData.put(key + ":" + i, "value " + i);
            }

            assertEquals(OK, client.mset(expectedData).get());

            ClusterScanCursor cursor = ClusterScanCursor.initalCursor();
            Set<String> keys = new LinkedHashSet<>();
            int successfulComparedScans = 0;
            while (!cursor.isFinished()) {
                Object[] resultOf1 =
                        client.clusterScan(cursor, ScanOptions.builder().count(1L).build()).get();
                cursor.releaseCursorHandle();
                cursor = (ClusterScanCursor) resultOf1[0];
                keys.addAll(
                        Arrays.stream((Object[]) resultOf1[1])
                                .map(Object::toString)
                                .collect(Collectors.toList()));
                if (cursor.isFinished()) {
                    break;
                }

                Object[] resultOf100 =
                        client.clusterScan(cursor, ScanOptions.builder().count(100L).build()).get();
                cursor.releaseCursorHandle();
                cursor = (ClusterScanCursor) resultOf100[0];
                keys.addAll(
                        Arrays.stream((Object[]) resultOf100[1])
                                .map(Object::toString)
                                .collect(Collectors.toList()));

                // Note: count is only an optimization hint. It does not have to return the size specified.
                if (resultOf1.length <= resultOf100.length) {
                    successfulComparedScans++;
                }
            }
            cursor.releaseCursorHandle();
            assertTrue(successfulComparedScans > 0);
            assertEquals(expectedData.keySet(), keys);
        }
    }

    @Test
    @SneakyThrows
    public void test_cluster_scan_with_match() {
        try (RedisClusterClient client =
                RedisClusterClient.CreateClient(commonClusterClientConfig().build()).get()) {

            assertEquals(OK, client.flushall().get());
            String key = "key:" + UUID.randomUUID();
            Map<String, String> expectedData = new LinkedHashMap<>();
            final int baseNumberOfEntries = 2000;
            for (int i = 0; i < baseNumberOfEntries; i++) {
                expectedData.put(key + ":" + i, "value " + i);
            }
            assertEquals(OK, client.mset(expectedData).get());

            Map<String, String> unexpectedPatterns = new LinkedHashMap<>();
            for (int i = baseNumberOfEntries + 100; i < baseNumberOfEntries + 200; i++) {
                unexpectedPatterns.put("foo:" + i, "value " + i);
            }
            assertEquals(OK, client.mset(unexpectedPatterns).get());

            ClusterScanCursor cursor = ClusterScanCursor.initalCursor();
            Set<String> keys = new LinkedHashSet<>();
            while (!cursor.isFinished()) {
                Object[] result =
                        client.clusterScan(cursor, ScanOptions.builder().matchPattern("key:*").build()).get();
                cursor.releaseCursorHandle();
                cursor = (ClusterScanCursor) result[0];
                keys.addAll(
                        Arrays.stream((Object[]) result[1]).map(Object::toString).collect(Collectors.toList()));
            }
            cursor.releaseCursorHandle();
            assertEquals(expectedData.keySet(), keys);
            assertFalse(new LinkedHashSet<>(keys).removeAll(unexpectedPatterns.keySet()));
        }
    }

    @Test
    @SneakyThrows
    public void test_cluster_scan_cleaning_cursor() {
        // We test whether the cursor is cleaned up after it is deleted, which we expect to happen when
        // th GC is called.
        try (RedisClusterClient client =
                RedisClusterClient.CreateClient(commonClusterClientConfig().build()).get()) {
            assertEquals(OK, client.flushall().get());

            String key = "key:" + UUID.randomUUID();
            Map<String, String> expectedData = new LinkedHashMap<>();
            final int baseNumberOfEntries = 100;
            for (int i = 0; i < baseNumberOfEntries; i++) {
                expectedData.put(key + ":" + i, "value " + i);
            }
            assertEquals(OK, client.mset(expectedData).get());

            ClusterScanCursor cursor = ClusterScanCursor.initalCursor();
            final Object[] response = client.clusterScan(cursor).get();
            cursor = (ClusterScanCursor) (response[0]);
            cursor.releaseCursorHandle();
            final ClusterScanCursor brokenCursor = cursor;
            ExecutionException exception =
                    assertThrows(ExecutionException.class, () -> client.clusterScan(brokenCursor).get());
            assertInstanceOf(RequestException.class, exception.getCause());
            assertTrue(exception.getCause().getMessage().contains("Invalid scan_state_cursor id"));
        }
    }

    @Test
    @SneakyThrows
    public void test_cluster_scan_all_types() {
        try (RedisClusterClient client =
                RedisClusterClient.CreateClient(commonClusterClientConfig().build()).get()) {
            assertEquals(OK, client.flushall().get());

            String key = "key:" + UUID.randomUUID();
            Map<String, String> stringData = new LinkedHashMap<>();
            final int baseNumberOfEntries = 100;
            for (int i = 0; i < baseNumberOfEntries; i++) {
                stringData.put(key + ":" + i, "value " + i);
            }
            assertEquals(OK, client.mset(stringData).get());

            String setKey = "setKey:" + UUID.randomUUID();
            Map<String, String> setData = new LinkedHashMap<>();
            for (int i = 0; i < baseNumberOfEntries; i++) {
                setData.put(setKey + ":" + i, "value " + i);
            }
            for (String k : setData.keySet()) {
                assertEquals(1L, client.sadd(k, new String[] {"value" + k}).get());
            }

            String hashKey = "hashKey:" + UUID.randomUUID();
            Map<String, String> hashData = new LinkedHashMap<>();
            for (int i = 0; i < baseNumberOfEntries; i++) {
                hashData.put(hashKey + ":" + i, "value " + i);
            }
            for (String k : hashData.keySet()) {
                assertEquals(1L, client.hset(k, Map.of("field" + k, "value" + k)).get());
            }

            String listKey = "listKey:" + UUID.randomUUID();
            Map<String, String> listData = new LinkedHashMap<>();
            for (int i = 0; i < baseNumberOfEntries; i++) {
                listData.put(listKey + ":" + i, "value " + i);
            }
            for (String k : listData.keySet()) {
                assertEquals(1L, client.lpush(k, new String[] {"value" + k}).get());
            }

            String zSetKey = "zSetKey:" + UUID.randomUUID();
            Map<String, String> zSetData = new LinkedHashMap<>();
            for (int i = 0; i < baseNumberOfEntries; i++) {
                zSetData.put(zSetKey + ":" + i, "value " + i);
            }
            for (String k : zSetData.keySet()) {
                assertEquals(1L, client.zadd(k, Map.of(k, 1.0)).get());
            }

            ClusterScanCursor cursor = ClusterScanCursor.initalCursor();
            Set<String> results = new LinkedHashSet<>();
            while (!cursor.isFinished()) {
                Object[] response =
                        client
                                .clusterScan(
                                        cursor, ScanOptions.builder().type(ScanOptions.ObjectType.STRING).build())
                                .get();
                cursor.releaseCursorHandle();
                cursor = (ClusterScanCursor) response[0];
                results.addAll(
                        Arrays.stream((Object[]) response[1])
                                .map(Object::toString)
                                .collect(Collectors.toSet()));
            }
            cursor.releaseCursorHandle();
            assertEquals(stringData.keySet(), results);

            cursor = ClusterScanCursor.initalCursor();
            results.clear();
            while (!cursor.isFinished()) {
                Object[] response =
                        client
                                .clusterScan(cursor, ScanOptions.builder().type(ScanOptions.ObjectType.SET).build())
                                .get();
                cursor.releaseCursorHandle();
                cursor = (ClusterScanCursor) response[0];
                results.addAll(
                        Arrays.stream((Object[]) response[1])
                                .map(Object::toString)
                                .collect(Collectors.toSet()));
            }
            cursor.releaseCursorHandle();
            assertEquals(setData.keySet(), results);

            cursor = ClusterScanCursor.initalCursor();
            results.clear();
            while (!cursor.isFinished()) {
                Object[] response =
                        client
                                .clusterScan(
                                        cursor, ScanOptions.builder().type(ScanOptions.ObjectType.HASH).build())
                                .get();
                cursor.releaseCursorHandle();
                cursor = (ClusterScanCursor) response[0];
                results.addAll(
                        Arrays.stream((Object[]) response[1])
                                .map(Object::toString)
                                .collect(Collectors.toSet()));
            }
            cursor.releaseCursorHandle();
            assertEquals(hashData.keySet(), results);

            cursor = ClusterScanCursor.initalCursor();
            results.clear();
            while (!cursor.isFinished()) {
                Object[] response =
                        client
                                .clusterScan(
                                        cursor, ScanOptions.builder().type(ScanOptions.ObjectType.LIST).build())
                                .get();
                cursor.releaseCursorHandle();
                cursor = (ClusterScanCursor) response[0];
                results.addAll(
                        Arrays.stream((Object[]) response[1])
                                .map(Object::toString)
                                .collect(Collectors.toSet()));
            }
            cursor.releaseCursorHandle();
            assertEquals(listData.keySet(), results);

            cursor = ClusterScanCursor.initalCursor();
            results.clear();
            while (!cursor.isFinished()) {
                Object[] response =
                        client
                                .clusterScan(
                                        cursor, ScanOptions.builder().type(ScanOptions.ObjectType.ZSET).build())
                                .get();
                cursor.releaseCursorHandle();
                cursor = (ClusterScanCursor) response[0];
                results.addAll(
                        Arrays.stream((Object[]) response[1])
                                .map(Object::toString)
                                .collect(Collectors.toSet()));
            }
            cursor.releaseCursorHandle();
            assertEquals(zSetData.keySet(), results);
        }
    }
}
