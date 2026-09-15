/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.standalone;

import static glide.TestConfiguration.STANDALONE_HOSTS;
import static glide.TestConfiguration.TLS;
import static glide.TestUtilities.commonClientConfig;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.GlideClient;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ReadFrom;
import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.RequestException;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Integration tests for read-only mode in standalone client.
 *
 * <p>These tests verify that:
 *
 * <ul>
 *   <li>Write commands are blocked in read-only mode
 *   <li>Read commands are allowed in read-only mode
 *   <li>AZAffinity strategies are rejected with read-only mode
 *   <li>PreferReplica strategy is accepted with read-only mode
 * </ul>
 */
@Timeout(20) // seconds
public class ReadOnlyModeTests {

    @SneakyThrows
    @Test
    public void test_read_only_mode_blocks_write_commands() {
        GlideClient client =
                GlideClient.createClient(commonClientConfig().readOnly(true).build()).get();

        try {
            // Attempt to execute a write command - should be blocked
            ExecutionException exception =
                    assertThrows(ExecutionException.class, () -> client.set("key", "value").get());

            assertInstanceOf(RequestException.class, exception.getCause());
            assertTrue(
                    exception
                            .getCause()
                            .getMessage()
                            .toLowerCase()
                            .contains("write commands are not allowed in read-only mode"));
        } finally {
            client.close();
        }
    }

    @SneakyThrows
    @Test
    public void test_read_only_mode_allows_read_commands() {
        GlideClient client =
                GlideClient.createClient(commonClientConfig().readOnly(true).build()).get();

        try {
            // Read commands should work without raising an error
            String result = client.get("nonexistent_key").get();
            // The key doesn't exist, so result should be null
            assertNull(result);
        } finally {
            client.close();
        }
    }

    @SneakyThrows
    @Test
    public void test_read_only_rejects_az_affinity() {
        // Test that read-only mode with AZAffinity strategy fails during client creation.
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                GlideClient.createClient(
                                                commonClientConfig()
                                                        .readOnly(true)
                                                        .readFrom(ReadFrom.AZ_AFFINITY)
                                                        .clientAZ("us-east-1a")
                                                        .build())
                                        .get());

        assertInstanceOf(ClosingException.class, exception.getCause());
        assertTrue(
                exception
                        .getCause()
                        .getMessage()
                        .contains("read-only mode is not compatible with AZAffinity strategies"),
                "Unexpected failure reason: " + exception.getCause().getMessage());
    }

    @SneakyThrows
    @Test
    public void test_read_only_rejects_az_affinity_replicas_and_primary() {
        // Test that read-only mode with AZAffinityReplicasAndPrimary strategy fails during client
        // creation.
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                GlideClient.createClient(
                                                commonClientConfig()
                                                        .readOnly(true)
                                                        .readFrom(ReadFrom.AZ_AFFINITY_REPLICAS_AND_PRIMARY)
                                                        .clientAZ("us-east-1a")
                                                        .build())
                                        .get());

        assertInstanceOf(ClosingException.class, exception.getCause());
        assertTrue(
                exception
                        .getCause()
                        .getMessage()
                        .contains("read-only mode is not compatible with AZAffinity strategies"),
                "Unexpected failure reason: " + exception.getCause().getMessage());
    }

    @SneakyThrows
    @Test
    public void test_read_only_rejects_az_affinity_all_nodes() {
        // Test that read-only mode with AZAffinityAllNodes strategy fails during client creation.
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                GlideClient.createClient(
                                                commonClientConfig()
                                                        .readOnly(true)
                                                        .readFrom(ReadFrom.AZ_AFFINITY_ALL_NODES)
                                                        .clientAZ("us-east-1a")
                                                        .build())
                                        .get());

        assertInstanceOf(ClosingException.class, exception.getCause());
        // The core's rejection reason reaches Java intact through the JNI layer, so assert on it
        // rather than only on the failure - otherwise this test would still pass if the client
        // failed to connect for an unrelated reason.
        assertTrue(
                exception
                        .getCause()
                        .getMessage()
                        .contains("read-only mode is not compatible with AZAffinity strategies"),
                "Unexpected failure reason: " + exception.getCause().getMessage());
    }

    @SneakyThrows
    @Test
    public void test_read_only_accepts_prefer_replica() {
        // Test that read-only mode accepts PreferReplica strategy
        GlideClient client =
                GlideClient.createClient(
                                commonClientConfig().readOnly(true).readFrom(ReadFrom.PREFER_REPLICA).build())
                        .get();

        try {
            // Client should be created successfully and read commands should work
            String result = client.get("nonexistent_key").get();
            assertNull(result);
        } finally {
            client.close();
        }
    }

    @SneakyThrows
    @Test
    public void test_read_only_default_is_false() {
        // Test that read-only mode defaults to false (write commands work)
        GlideClient client = GlideClient.createClient(commonClientConfig().build()).get();

        try {
            // Write commands should work when readOnly is not set (defaults to false)
            String key = "test_key_" + System.currentTimeMillis();
            client.set(key, "value").get();
            String result = client.get(key).get();
            assertTrue("value".equals(result));

            // Clean up
            client.del(new String[] {key}).get();
        } finally {
            client.close();
        }
    }

    @SneakyThrows
    @Test
    public void test_read_only_connects_to_individual_nodes() {
        // Test that read-only mode works when connecting to individual nodes.
        // This verifies that:
        // 1. Read-only mode works when connecting to a single node (skips INFO REPLICATION)
        // 2. Write commands are blocked
        // 3. Read commands work
        //
        // Note: The Java test environment starts standalone with -r 0 (no replicas),
        // so we test with the available nodes which are all primaries.

        assertTrue(STANDALONE_HOSTS.length >= 1, "Standalone cluster should have at least 1 node");

        // Connect to the first node only with read-only mode
        String[] parts = STANDALONE_HOSTS[0].split(":");
        NodeAddress nodeAddr =
                NodeAddress.builder().host(parts[0]).port(Integer.parseInt(parts[1])).build();

        GlideClient client =
                GlideClient.createClient(
                                GlideClientConfiguration.builder()
                                        .address(nodeAddr)
                                        .useTLS(TLS)
                                        .readOnly(true)
                                        .build())
                        .get();

        try {
            // Test read commands work
            String result = client.get("test_key_read_only").get();
            // Key doesn't exist, should return null without error
            assertNull(result);

            // Test write commands are blocked
            ExecutionException exception =
                    assertThrows(ExecutionException.class, () -> client.set("test_key", "value").get());

            assertInstanceOf(RequestException.class, exception.getCause());
            assertTrue(
                    exception
                            .getCause()
                            .getMessage()
                            .toLowerCase()
                            .contains("write commands are not allowed in read-only mode"));
        } finally {
            client.close();
        }
    }
}
