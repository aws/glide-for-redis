/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestConfiguration.CLUSTER_HOSTS;
import static glide.TestConfiguration.SERVER_VERSION;
import static glide.TestConfiguration.STANDALONE_HOSTS;
import static glide.TestUtilities.*;
import static glide.TestUtilities.IAM_TEST_CLUSTER_NAME;
import static glide.TestUtilities.IAM_TEST_REGION_US_EAST_1;
import static glide.TestUtilities.IAM_USERNAME;
import static glide.TestUtilities.createTestIamConfig;
import static glide.api.BaseClient.OK;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_NODES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_PRIMARIES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SlotType.PRIMARY;
import static glide.api.models.configuration.RequestRoutingConfiguration.SlotType.REPLICA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.ClusterValue;
import glide.api.models.commands.ClientPauseMode;
import glide.api.models.commands.InfoOptions;
import glide.api.models.configuration.AddressResolver;
import glide.api.models.configuration.AdvancedGlideClientConfiguration;
import glide.api.models.configuration.AdvancedGlideClusterClientConfiguration;
import glide.api.models.configuration.AwsCredentials;
import glide.api.models.configuration.BackoffStrategy;
import glide.api.models.configuration.ClientCircuitBreakerConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.GlideCredentialProvider;
import glide.api.models.configuration.IamAuthConfig;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.PeriodicChecksManualInterval;
import glide.api.models.configuration.PeriodicChecksStatus;
import glide.api.models.configuration.ProtocolVersion;
import glide.api.models.configuration.ReadFrom;
import glide.api.models.configuration.RequestRoutingConfiguration;
import glide.api.models.configuration.ResolvedAddress;
import glide.api.models.configuration.ServerCredentials;
import glide.api.models.configuration.ServiceType;
import glide.api.models.configuration.TlsAdvancedConfiguration;
import glide.api.models.exceptions.ClosingException;
import glide.cluster.ValkeyCluster;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(10) // seconds
public class ConnectionTests {

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    @SneakyThrows
    public void basic_client(ProtocolVersion protocol) {
        GlideClient regularClient =
                GlideClient.createClient(commonClientConfig().protocol(protocol).build()).get();
        regularClient.close();
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    @SneakyThrows
    public void cluster_client(ProtocolVersion protocol) {
        GlideClusterClient clusterClient =
                GlideClusterClient.createClient(commonClusterClientConfig().protocol(protocol).build())
                        .get();
        clusterClient.close();
    }

    @SneakyThrows
    public GlideClusterClient createAzTestClient(String az) {
        return GlideClusterClient.createClient(
                        azClusterClientConfig()
                                .readFrom(ReadFrom.AZ_AFFINITY)
                                .clientAZ(az)
                                .requestTimeout(2000)
                                .build())
                .get();
    }

    @SneakyThrows
    private ValkeyCluster createDedicatedCluster(boolean clusterMode) {
        return new ValkeyCluster(false, clusterMode, clusterMode ? 3 : 1, 0, null, null);
    }

    @SneakyThrows
    public BaseClient createConnectionTimeoutClient(
            Boolean clusterMode,
            int connectionTimeout,
            int requestTimeout,
            BackoffStrategy backoffStrategy) {
        if (clusterMode) {
            AdvancedGlideClusterClientConfiguration advancedConfiguration =
                    AdvancedGlideClusterClientConfiguration.builder()
                            .connectionTimeout(connectionTimeout)
                            .build();
            return GlideClusterClient.createClient(
                            commonClusterClientConfig()
                                    .advancedConfiguration(advancedConfiguration)
                                    .requestTimeout(requestTimeout)
                                    .build())
                    .get();
        }
        AdvancedGlideClientConfiguration advancedConfiguration =
                AdvancedGlideClientConfiguration.builder().connectionTimeout(connectionTimeout).build();
        return GlideClient.createClient(
                        commonClientConfig()
                                .advancedConfiguration(advancedConfiguration)
                                .requestTimeout(requestTimeout)
                                .reconnectStrategy(backoffStrategy)
                                .build())
                .get();
    }

    @SneakyThrows
    public BaseClient createClientWithTLSMode(boolean isCluster, boolean useInsecureTLS) {
        if (isCluster) {
            AdvancedGlideClusterClientConfiguration advancedConfiguration =
                    AdvancedGlideClusterClientConfiguration.builder()
                            .tlsAdvancedConfiguration(
                                    TlsAdvancedConfiguration.builder().useInsecureTLS(useInsecureTLS).build())
                            .build();
            return GlideClusterClient.createClient(
                            commonClusterClientConfig().advancedConfiguration(advancedConfiguration).build())
                    .get();
        }
        AdvancedGlideClientConfiguration advancedConfiguration =
                AdvancedGlideClientConfiguration.builder()
                        .tlsAdvancedConfiguration(
                                TlsAdvancedConfiguration.builder().useInsecureTLS(useInsecureTLS).build())
                        .build();
        return GlideClient.createClient(
                        commonClientConfig().advancedConfiguration(advancedConfiguration).build())
                .get();
    }

    /**
     * Test that the client with AZ affinity strategy routes in a round-robin manner to all replicas
     * within the specified AZ.
     */
    @SneakyThrows
    @Test
    public void test_routing_by_slot_to_replica_with_az_affinity_strategy_to_all_replicas() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("8.0.0"), "Skip for versions below 8");
        // Windows integration tests has replicas set to zero due to resource limitations
        // on Github Action using Windows runner with WSL
        // TODO: Remove the skip after fixing Windows Replicas issues
        // https://github.com/valkey-io/valkey-glide/issues/5210
        assumeTrue(!isWindows(), "Skip on Windows");

        String az = "us-east-1a";

        // Create client for setting the configs
        GlideClusterClient configSetClient =
                GlideClusterClient.createClient(azClusterClientConfig().requestTimeout(2000).build()).get();
        assertEquals(configSetClient.configResetStat().get(), OK);

        // Get Replica Count for current cluster
        long nReplicas =
                getReplicaCount(
                        configSetClient, new RequestRoutingConfiguration.SlotKeyRoute("key", PRIMARY));
        long nGetCalls = 3 * nReplicas;
        String getCmdstat = String.format("cmdstat_get:calls=%d", 3);

        // Setting AZ for all Nodes
        configSetClient.configSet(Collections.singletonMap("availability-zone", az), ALL_NODES).get();
        configSetClient.close();

        // Creating Client with AZ configuration for testing
        GlideClusterClient azTestClient = createAzTestClient(az);
        ClusterValue<Map<String, String>> azGetResult =
                azTestClient.configGet(new String[] {"availability-zone"}, ALL_NODES).get();
        Map<String, Map<String, String>> azData = azGetResult.getMultiValue();

        // Check that all replicas have the availability zone set to the az
        for (Map.Entry<String, Map<String, String>> entry : azData.entrySet()) {
            assertEquals(az, entry.getValue().get("availability-zone"));
        }

        // execute GET commands
        for (int i = 0; i < nGetCalls; i++) {
            azTestClient.get("foo").get();
        }

        ClusterValue<String> infoResult =
                azTestClient.info(new InfoOptions.Section[] {InfoOptions.Section.ALL}, ALL_NODES).get();
        Map<String, String> infoData = infoResult.getMultiValue();

        // Check that all replicas have the same number of GET calls
        long matchingEntries =
                infoData.values().stream()
                        .filter(value -> value.contains(getCmdstat) && value.contains(az))
                        .count();
        assertEquals(nReplicas, matchingEntries);
        azTestClient.close();
    }

    /**
     * Test that the client with az affinity strategy will only route to the 1 replica with the same
     * az.
     */
    @SneakyThrows
    @Test
    public void test_routing_with_az_affinity_strategy_to_1_replica() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("8.0.0"), "Skip for versions below 8");

        String az = "us-east-1a";
        int nGetCalls = 3;
        String getCmdstat = String.format("cmdstat_get:calls=%d", nGetCalls);

        GlideClusterClient configSetClient =
                GlideClusterClient.createClient(azClusterClientConfig().requestTimeout(2000).build()).get();

        // reset availability zone for all nodes
        configSetClient.configSet(Collections.singletonMap("availability-zone", ""), ALL_NODES).get();
        assertEquals(configSetClient.configResetStat().get(), OK);

        Long fooSlotKey =
                (Long)
                        configSetClient
                                .customCommand(new String[] {"CLUSTER", "KEYSLOT", "foo"})
                                .get()
                                .getSingleValue();
        int convertedKey = Integer.parseInt(fooSlotKey.toString());
        configSetClient
                .configSet(
                        Collections.singletonMap("availability-zone", az),
                        new RequestRoutingConfiguration.SlotIdRoute(convertedKey, REPLICA))
                .get();
        configSetClient.close();

        GlideClusterClient azTestClient = createAzTestClient(az);

        // execute GET commands
        for (int i = 0; i < nGetCalls; i++) {
            azTestClient.get("foo").get();
        }

        ClusterValue<String> infoResult =
                azTestClient.info(new InfoOptions.Section[] {InfoOptions.Section.ALL}, ALL_NODES).get();
        Map<String, String> infoData = infoResult.getMultiValue();

        // Check that all replicas have the same number of GET calls
        long matchingEntries =
                infoData.values().stream()
                        .filter(value -> value.contains(getCmdstat) && value.contains(az))
                        .count();
        assertEquals(1, matchingEntries);
        azTestClient.close();
    }

    @SneakyThrows
    @Test
    public void test_az_affinity_non_existing_az() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("8.0.0"), "Skip for versions below 8");

        int nGetCalls = 4;
        int nReplicaCalls = 1;
        String getCmdstat = String.format("cmdstat_get:calls=%d", nReplicaCalls);

        GlideClusterClient azTestClient = createAzTestClient("non-existing-az");
        assertEquals(azTestClient.configResetStat(ALL_NODES).get(), OK);

        // execute GET commands
        for (int i = 0; i < nGetCalls; i++) {
            azTestClient.get("foo").get();
        }

        ClusterValue<String> infoResult =
                azTestClient
                        .info(new InfoOptions.Section[] {InfoOptions.Section.COMMANDSTATS}, ALL_NODES)
                        .get();
        Map<String, String> infoData = infoResult.getMultiValue();

        //  We expect the calls to be distributed evenly among the replicas
        long matchingEntries =
                infoData.values().stream().filter(value -> value.contains(getCmdstat)).count();
        // TODO: Remove the override after fixing Windows Replicas issues
        // https://github.com/valkey-io/valkey-glide/issues/5210
        long expectedReplicas = 4;
        if (isWindows()) {
            expectedReplicas = 0;
        }

        assertEquals(expectedReplicas, matchingEntries);
        azTestClient.close();
    }

    @SneakyThrows
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_connection_timeout(boolean clusterMode) {
        BackoffStrategy backoffStrategy =
                BackoffStrategy.builder().exponentBase(2).factor(100).numOfRetries(1).build();
        BaseClient client = createConnectionTimeoutClient(clusterMode, 250, 20000, backoffStrategy);

        // Runnable for long-running DEBUG SLEEP command
        Runnable debugSleepTask =
                () -> {
                    try {
                        if (client instanceof GlideClusterClient) {
                            ((GlideClusterClient) client)
                                    .customCommand(new String[] {"DEBUG", "sleep", "7"}, ALL_NODES)
                                    .get();
                        } else if (client instanceof GlideClient) {
                            ((GlideClient) client).customCommand(new String[] {"DEBUG", "sleep", "7"}).get();
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException("Error during DEBUG SLEEP command", e);
                    }
                };

        // Runnable for testing connection failure due to timeout
        Runnable failToConnectTask =
                () -> {
                    try {
                        Thread.sleep(1000); // Wait to ensure the debug sleep command is running
                        ExecutionException executionException =
                                assertThrows(
                                        ExecutionException.class,
                                        () -> createConnectionTimeoutClient(clusterMode, 100, 250, backoffStrategy));
                        assertInstanceOf(ClosingException.class, executionException.getCause());
                        assertTrue(executionException.getMessage().toLowerCase().contains("timed out"));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Thread was interrupted", e);
                    }
                };

        // Runnable for testing successful connection
        Runnable connectToClientTask =
                () -> {
                    try {
                        Thread.sleep(1000); // Wait to ensure the debug sleep command is running
                        BaseClient timeoutClient =
                                createConnectionTimeoutClient(clusterMode, 10000, 250, backoffStrategy);
                        assertEquals(timeoutClient.set("key", "value").get(), "OK");
                        timeoutClient.close();
                    } catch (Exception e) {
                        throw new RuntimeException("Error during successful connection attempt", e);
                    }
                };

        // Execute all tasks concurrently
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        try {
            executorService.invokeAll(
                    Arrays.asList(
                            Executors.callable(debugSleepTask),
                            Executors.callable(failToConnectTask),
                            Executors.callable(connectToClientTask)));
        } finally {
            executorService.shutdown();
            // Clean up the main client
            if (client != null) {
                client.close();
            }
        }
    }

    @SneakyThrows
    @Test
    public void test_az_affinity_replicas_and_primary_routes_to_primary() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("8.0.0"), "Skip for versions below 8");
        // Windows integration tests has replicas set to zero due to resource limitations
        // on Github Action using Windows runner with WSL
        // TODO: Remove the skip after fixing Windows Replicas issues
        // https://github.com/valkey-io/valkey-glide/issues/5210
        assumeTrue(!isWindows(), "Skip on Windows");

        String az = "us-east-1a";
        String otherAz = "us-east-1b";
        int nGetCalls = 4;
        String getCmdstat = String.format("cmdstat_get:calls=%d", nGetCalls);

        // Create client for setting the configs
        GlideClusterClient configSetClient =
                GlideClusterClient.createClient(azClusterClientConfig().requestTimeout(2000).build()).get();

        // Reset stats and set all nodes to other_az
        assertEquals(configSetClient.configResetStat().get(), OK);
        configSetClient
                .configSet(Collections.singletonMap("availability-zone", otherAz), ALL_NODES)
                .get();

        // Set primary for slot 12182 to az
        configSetClient
                .configSet(
                        Collections.singletonMap("availability-zone", az),
                        new RequestRoutingConfiguration.SlotIdRoute(12182, PRIMARY))
                .get();

        // Verify primary AZ
        ClusterValue<Map<String, String>> primaryAzResult =
                configSetClient
                        .configGet(
                                new String[] {"availability-zone"},
                                new RequestRoutingConfiguration.SlotIdRoute(12182, PRIMARY))
                        .get();
        assertEquals(
                az,
                primaryAzResult.getSingleValue().get("availability-zone"),
                "Primary for slot 12182 is not in the expected AZ " + az);

        configSetClient.close();

        // Create test client with AZ_AFFINITY_REPLICAS_AND_PRIMARY configuration
        GlideClusterClient azTestClient =
                GlideClusterClient.createClient(
                                azClusterClientConfig()
                                        .readFrom(ReadFrom.AZ_AFFINITY_REPLICAS_AND_PRIMARY)
                                        .clientAZ(az)
                                        .requestTimeout(2000)
                                        .build())
                        .get();

        // Execute GET commands
        for (int i = 0; i < nGetCalls; i++) {
            azTestClient.get("foo").get();
        }

        ClusterValue<String> infoResult =
                azTestClient.info(new InfoOptions.Section[] {InfoOptions.Section.ALL}, ALL_NODES).get();
        Map<String, String> infoData = infoResult.getMultiValue();

        // Check that only the primary in the specified AZ handled all GET calls
        long matchingEntries =
                infoData.values().stream()
                        .filter(
                                value ->
                                        value.contains(getCmdstat)
                                                && value.contains(az)
                                                && value.contains("role:master"))
                        .count();
        assertEquals(1, matchingEntries, "Exactly one primary in AZ should handle all calls");

        // Verify total GET calls
        long totalGetCalls =
                infoData.values().stream()
                        .filter(value -> value.contains("cmdstat_get:calls="))
                        .mapToInt(
                                value -> {
                                    int startIndex =
                                            value.indexOf("cmdstat_get:calls=") + "cmdstat_get:calls=".length();
                                    int endIndex = value.indexOf(",", startIndex);
                                    return Integer.parseInt(value.substring(startIndex, endIndex));
                                })
                        .sum();
        assertEquals(nGetCalls, totalGetCalls, "Total GET calls mismatch");

        azTestClient.close();
    }

    @SneakyThrows
    @Test
    public void test_all_nodes_routes_to_primary_and_replicas() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("8.0.0"), "Skip for versions below 8");
        // Windows integration tests has replicas set to zero due to resource limitations
        // on Github Action using Windows runner with WSL
        // TODO: Remove the skip after fixing Windows Replicas issues
        // https://github.com/valkey-io/valkey-glide/issues/5210
        assumeTrue(!isWindows(), "Skip on Windows");

        // Use more calls to ensure statistical significance for round-robin distribution
        int nGetCalls = 60;

        try (GlideClusterClient client =
                GlideClusterClient.createClient(
                                commonClusterClientConfig()
                                        .readFrom(ReadFrom.ALL_NODES)
                                        .requestTimeout(2000)
                                        .build())
                        .get()) {
            assertEquals(client.configResetStat(ALL_NODES).get(), OK);

            // Execute GET commands for key "foo" asynchronously
            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (int i = 0; i < nGetCalls; i++) {
                futures.add(client.get("foo"));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

            ClusterValue<String> infoResult =
                    client.info(new InfoOptions.Section[] {InfoOptions.Section.ALL}, ALL_NODES).get();
            Map<String, String> infoData = infoResult.getMultiValue();

            // Get replica count for the slot that handles key "foo"
            ClusterValue<Object> clusterInfo =
                    client
                            .customCommand(
                                    new String[] {"INFO", "REPLICATION"},
                                    new RequestRoutingConfiguration.SlotKeyRoute("foo", PRIMARY))
                            .get();
            long nReplicas =
                    Long.parseLong(
                            Stream.of(((String) clusterInfo.getSingleValue()).split("\\R"))
                                    .map(line -> line.split(":", 2))
                                    .filter(parts -> parts.length == 2 && parts[0].trim().equals("connected_slaves"))
                                    .map(parts -> parts[1].trim())
                                    .findFirst()
                                    .get());

            // Count nodes that received GET calls
            long nodesWithCalls =
                    infoData.values().stream().filter(value -> value.contains("cmdstat_get:calls=")).count();

            // Verify that primary + replicas for this slot received calls (slot-based routing)
            assertEquals(
                    nReplicas + 1,
                    nodesWithCalls,
                    "ALL_NODES should route to primary and all replicas for the slot. Expected "
                            + (nReplicas + 1)
                            + " nodes, got "
                            + nodesWithCalls);

            // Verify both primary and replicas received calls
            long primaryCalls =
                    infoData.values().stream()
                            .filter(
                                    value -> value.contains("cmdstat_get:calls=") && value.contains("role:master"))
                            .count();
            long replicaCalls =
                    infoData.values().stream()
                            .filter(value -> value.contains("cmdstat_get:calls=") && value.contains("role:slave"))
                            .count();

            assertTrue(primaryCalls > 0, "Primary should receive calls with ALL_NODES strategy");
            assertTrue(replicaCalls > 0, "Replicas should receive calls with ALL_NODES strategy");

            // Verify total GET calls
            long totalGetCalls =
                    infoData.values().stream()
                            .filter(value -> value.contains("cmdstat_get:calls="))
                            .mapToInt(
                                    value -> {
                                        int startIndex =
                                                value.indexOf("cmdstat_get:calls=") + "cmdstat_get:calls=".length();
                                        int endIndex = value.indexOf(",", startIndex);
                                        return Integer.parseInt(value.substring(startIndex, endIndex));
                                    })
                            .sum();
            assertEquals(nGetCalls, totalGetCalls, "Total GET calls mismatch");
        }
    }

    @SneakyThrows
    @Test
    public void test_az_affinity_all_nodes_splits_between_primary_and_replica() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("8.0.0"), "Skip for versions below 8");
        // Windows integration tests has replicas set to zero due to resource limitations
        // on Github Action using Windows runner with WSL
        // TODO: Remove the skip after fixing Windows Replicas issues
        // https://github.com/valkey-io/valkey-glide/issues/5210
        assumeTrue(!isWindows(), "Skip on Windows");

        String az = "us-east-1a";
        String otherAz = "us-east-1b";
        int nGetCalls = 4;
        int nodesInSameAz = 2; // one primary + one replica
        int perNodeCalls = nGetCalls / nodesInSameAz;
        String perNodeGetCmdstat = String.format("cmdstat_get:calls=%d", perNodeCalls);

        // Create client for setting the configs
        GlideClusterClient configSetClient =
                GlideClusterClient.createClient(azClusterClientConfig().requestTimeout(2000).build()).get();

        // Reset stats and set all nodes to other_az
        assertEquals(configSetClient.configResetStat().get(), OK);
        configSetClient
                .configSet(Collections.singletonMap("availability-zone", otherAz), ALL_NODES)
                .get();

        // Move the primary and one replica of slot 12182 into az
        configSetClient
                .configSet(
                        Collections.singletonMap("availability-zone", az),
                        new RequestRoutingConfiguration.SlotIdRoute(12182, PRIMARY))
                .get();
        configSetClient
                .configSet(
                        Collections.singletonMap("availability-zone", az),
                        new RequestRoutingConfiguration.SlotIdRoute(12182, REPLICA))
                .get();

        // Read the AZs back, so a fixture that failed to apply is distinguishable from a routing
        // regression - both would otherwise surface as "0 nodes handled the GET calls".
        // The primary is addressable deterministically; a REPLICA slot route is not (it may pick a
        // different replica than the one just tagged), so the replica is confirmed by counting how
        // many nodes cluster-wide report the AZ.
        ClusterValue<Map<String, String>> primaryAzResult =
                configSetClient
                        .configGet(
                                new String[] {"availability-zone"},
                                new RequestRoutingConfiguration.SlotIdRoute(12182, PRIMARY))
                        .get();
        assertEquals(
                az,
                primaryAzResult.getSingleValue().get("availability-zone"),
                "Primary for slot 12182 is not in the expected AZ " + az);

        ClusterValue<Map<String, String>> allAzResult =
                configSetClient.configGet(new String[] {"availability-zone"}, ALL_NODES).get();
        long nodesTaggedWithAz =
                allAzResult.getMultiValue().values().stream()
                        .filter(nodeConfig -> az.equals(nodeConfig.get("availability-zone")))
                        .count();
        assertEquals(
                nodesInSameAz,
                nodesTaggedWithAz,
                "Expected exactly "
                        + nodesInSameAz
                        + " nodes (one primary + one replica) in AZ "
                        + az
                        + " after fixture setup");

        configSetClient.close();

        // Create test client AFTER configuration so it picks up the AZs on connect
        GlideClusterClient azTestClient =
                GlideClusterClient.createClient(
                                azClusterClientConfig()
                                        .readFrom(ReadFrom.AZ_AFFINITY_ALL_NODES)
                                        .clientAZ(az)
                                        .requestTimeout(2000)
                                        .build())
                        .get();

        try {
            // Execute GET commands
            for (int i = 0; i < nGetCalls; i++) {
                azTestClient.get("foo").get();
            }

            ClusterValue<String> infoResult =
                    azTestClient.info(new InfoOptions.Section[] {InfoOptions.Section.ALL}, ALL_NODES).get();
            Map<String, String> infoData = infoResult.getMultiValue();

            // Reads must be spread evenly across every node in the AZ - primary and replica alike -
            // rather than prioritizing the replica the way AZ_AFFINITY_REPLICAS_AND_PRIMARY would.
            long matchingEntries =
                    infoData.values().stream()
                            .filter(value -> value.contains(perNodeGetCmdstat) && value.contains(az))
                            .count();
            assertEquals(
                    nodesInSameAz,
                    matchingEntries,
                    "Each of the "
                            + nodesInSameAz
                            + " nodes in AZ "
                            + az
                            + " should have handled "
                            + perNodeCalls
                            + " GET calls");

            // No GET should have landed on a node outside the client's AZ
            long outOfAzEntries =
                    infoData.values().stream()
                            .filter(value -> value.contains("cmdstat_get:calls=") && !value.contains(az))
                            .count();
            assertEquals(0, outOfAzEntries, "GET calls landed on nodes outside AZ " + az);

            // Verify total GET calls
            long totalGetCalls =
                    infoData.values().stream()
                            .filter(value -> value.contains("cmdstat_get:calls="))
                            .mapToInt(
                                    value -> {
                                        int startIndex =
                                                value.indexOf("cmdstat_get:calls=") + "cmdstat_get:calls=".length();
                                        int endIndex = value.indexOf(",", startIndex);
                                        return Integer.parseInt(value.substring(startIndex, endIndex));
                                    })
                            .sum();
            assertEquals(nGetCalls, totalGetCalls, "Total GET calls mismatch");
        } finally {
            azTestClient.close();
        }
    }

    @SneakyThrows
    @Test
    public void test_az_affinity_replicas_and_primary_prioritizes_replicas_over_primary() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("8.0.0"), "Skip for versions below 8");
        // Windows integration tests has replicas set to zero. This is set because of the resource
        // limitation
        // on Github Action using Windows runner with WSL, which is making the server with replicas hang
        // and not be fully initialized
        // TODO: Remove the skip after fixing Windows Replicas issues
        // https://github.com/valkey-io/valkey-glide/issues/5210
        assumeTrue(!isWindows(), "Skip on Windows");

        String clientAz = "us-east-1b"; // Client is in 1B
        String otherAz = "us-east-1a"; // Other nodes in 1A
        int nGetCalls = 4;
        String getCmdstat = String.format("cmdstat_get:calls=%d", nGetCalls);
        int slot = 12182; // slot for key "foo"

        // Create client for setting the configs
        GlideClusterClient configSetClient =
                GlideClusterClient.createClient(azClusterClientConfig().requestTimeout(2000).build()).get();

        // Reset stats
        assertEquals(configSetClient.configResetStat().get(), OK);

        // Set ALL nodes to otherAz (us-east-1a)
        configSetClient
                .configSet(Collections.singletonMap("availability-zone", otherAz), ALL_NODES)
                .get();

        // Set REPLICA for slot to clientAz (us-east-1b)
        configSetClient
                .configSet(
                        Collections.singletonMap("availability-zone", clientAz),
                        new RequestRoutingConfiguration.SlotIdRoute(slot, REPLICA))
                .get();

        // Verify setup: Primary should be in otherAz (1A)
        ClusterValue<Map<String, String>> primaryAzResult =
                configSetClient
                        .configGet(
                                new String[] {"availability-zone"},
                                new RequestRoutingConfiguration.SlotIdRoute(slot, PRIMARY))
                        .get();
        assertEquals(
                otherAz,
                primaryAzResult.getSingleValue().get("availability-zone"),
                "Primary for slot " + slot + " should be in: " + otherAz);

        configSetClient.close();

        // Create test client with AZ_AFFINITY_REPLICAS_AND_PRIMARY configuration
        // Client is in us-east-1b, same as the replica
        GlideClusterClient azTestClient =
                GlideClusterClient.createClient(
                                azClusterClientConfig()
                                        .readFrom(ReadFrom.AZ_AFFINITY_REPLICAS_AND_PRIMARY)
                                        .clientAZ(clientAz)
                                        .requestTimeout(2000)
                                        .build())
                        .get();

        // Execute GET commands - these should go to the replica in clientAz (1B)
        for (int i = 0; i < nGetCalls; i++) {
            azTestClient.get("foo").get();
        }

        ClusterValue<String> infoResult =
                azTestClient.info(new InfoOptions.Section[] {InfoOptions.Section.ALL}, ALL_NODES).get();
        Map<String, String> infoData = infoResult.getMultiValue();

        // Check that a REPLICA in client's AZ (1B) handled all GET calls
        long replicaMatchingEntries =
                infoData.values().stream()
                        .filter(
                                value ->
                                        value.contains(getCmdstat)
                                                && value.contains(clientAz)
                                                && value.contains("role:slave"))
                        .count();
        assertEquals(
                1,
                replicaMatchingEntries,
                "Exactly one replica in client's AZ (" + clientAz + ") should handle all GET calls");

        // Verify that the PRIMARY did NOT receive any GET calls
        boolean primaryReceivedGets =
                infoData.values().stream()
                        .anyMatch(
                                value -> value.contains("role:master") && value.contains("cmdstat_get:calls="));

        assertFalse(
                primaryReceivedGets,
                "Primary should NOT receive GET calls when a replica is available in client's AZ");

        // Verify total GET calls equals expected
        long totalGetCalls =
                infoData.values().stream()
                        .filter(value -> value.contains("cmdstat_get:calls="))
                        .mapToInt(
                                value -> {
                                    int startIndex =
                                            value.indexOf("cmdstat_get:calls=") + "cmdstat_get:calls=".length();
                                    int endIndex = value.indexOf(",", startIndex);
                                    return Integer.parseInt(value.substring(startIndex, endIndex));
                                })
                        .sum();
        assertEquals(nGetCalls, totalGetCalls, "Total GET calls mismatch");

        azTestClient.close();
    }

    /**
     * Test that the client can connect using both secure and insecure TLS modes, meaning the client
     * bypasses the SSL certificate validation.
     */
    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {"true, true", "true, false", "false, true", "false, false"})
    public void test_connection_tls_mode(String clusterMode, String insecureTls) {
        try (BaseClient client =
                createClientWithTLSMode(Boolean.getBoolean(clusterMode), Boolean.getBoolean(insecureTls))) {
            assertEquals("OK", client.set("key", "val").get());
            assertEquals("val", client.get("key").get());
            assertEquals(1, client.del(new String[] {"key"}).get());
        }
    }

    /** Helper method to get client connection count for either standalone or cluster client. */
    private int getClientCount(BaseClient client) throws ExecutionException, InterruptedException {
        if (client instanceof GlideClusterClient) {
            // For cluster client, execute CLIENT LIST on all nodes
            ClusterValue<Object> result =
                    ((GlideClusterClient) client)
                            .customCommand(new String[] {"CLIENT", "LIST"}, ALL_NODES)
                            .get();

            // Result will be a dict with node addresses as keys and CLIENT LIST output as values
            int totalCount = 0;
            for (Object nodeOutput : ((Map<String, Object>) result.getMultiValue()).values()) {
                totalCount += getClientListOutputCount((String) nodeOutput);
            }

            return totalCount;
        } else {
            // For standalone client, execute CLIENT LIST directly
            String result =
                    (String) ((GlideClient) client).customCommand(new String[] {"CLIENT", "LIST"}).get();
            return getClientListOutputCount(result);
        }
    }

    /** Helper method to parse CLIENT LIST output and return the number of clients. */
    private int getClientListOutputCount(String output) {
        if (output == null || output.trim().isEmpty()) {
            return 0;
        }
        return output.trim().split("\n").length;
    }

    /**
     * Helper method to get the expected number of new connections when a lazy client is initialized.
     */
    private int getExpectedNewConnections(BaseClient client)
            throws ExecutionException, InterruptedException {
        if (client instanceof GlideClusterClient) {
            // For cluster, get node count and multiply by 2 (2 connections per node)
            ClusterValue<Object> result =
                    ((GlideClusterClient) client).customCommand(new String[] {"CLUSTER", "NODES"}).get();
            String[] nodesInfo = ((String) result.getSingleValue()).trim().split("\n");
            return nodesInfo.length * 2;
        } else {
            // For standalone, always expect 1 new connection
            return 1;
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @Timeout(30)
    public void test_lazy_connection_establishes_on_first_command(boolean clusterMode)
            throws Exception {
        BaseClient monitoringClient = null;
        BaseClient lazyGlideClient = null;
        String modeString = clusterMode ? "Cluster" : "Standalone";
        try (ValkeyCluster dedicatedCluster = createDedicatedCluster(clusterMode)) {
            // 1. Create a monitoring client (eagerly connected)
            monitoringClient = createDedicatedClient(clusterMode, null, dedicatedCluster, false);
            if (clusterMode) {
                assertInstanceOf(GlideClusterClient.class, monitoringClient);
                assertEquals("PONG", ((GlideClusterClient) monitoringClient).ping().get());
            } else {
                assertInstanceOf(GlideClient.class, monitoringClient);
                assertEquals("PONG", ((GlideClient) monitoringClient).ping().get());
            }

            // 2. Get initial client count
            int clientsBeforeLazyInit = getClientCount(monitoringClient);

            // 3. Create the "lazy" client
            lazyGlideClient = createDedicatedClient(clusterMode, null, dedicatedCluster, true);

            // 4. Check count (should not change)
            int clientsAfterLazyInit = getClientCount(monitoringClient);
            assertEquals(
                    clientsBeforeLazyInit,
                    clientsAfterLazyInit,
                    String.format(
                            "Lazy client (%s) should not connect before the first command. "
                                    + "Before: %d, After: %d",
                            modeString.toLowerCase(), clientsBeforeLazyInit, clientsAfterLazyInit));

            // 5. Send the first command using the lazy client
            Object pingResponse = null;
            if (clusterMode) {
                pingResponse = ((GlideClusterClient) lazyGlideClient).ping().get();
            } else {
                pingResponse = ((GlideClient) lazyGlideClient).ping().get();
            }

            // Assert PING success for both modes
            assertEquals("PONG", pingResponse, "PING response was not 'PONG': " + pingResponse);

            // 6. Check client count after the first command
            int clientsAfterFirstCommand = getClientCount(monitoringClient);
            int expectedNewConnections = getExpectedNewConnections(monitoringClient);

            assertEquals(
                    clientsBeforeLazyInit + expectedNewConnections,
                    clientsAfterFirstCommand,
                    String.format(
                            "Lazy client (%s) should establish %d new connection(s) after the first command. "
                                    + "Before: %d, After first command: %d",
                            modeString.toLowerCase(),
                            expectedNewConnections,
                            clientsBeforeLazyInit,
                            clientsAfterFirstCommand));
        } finally {
            if (monitoringClient != null) {
                monitoringClient.close();
            }
            if (lazyGlideClient != null) {
                lazyGlideClient.close();
            }
        }
    }

    @Test
    public void testRefreshTopologyFromInitialNodesDefault() {
        // Test that refreshTopologyFromInitialNodes defaults to false when not specified
        AdvancedGlideClusterClientConfiguration config =
                AdvancedGlideClusterClientConfiguration.builder().build();
        assertFalse(config.isRefreshTopologyFromInitialNodes());
    }

    @Test
    public void testRefreshTopologyFromInitialNodesEnabled() {
        // Test that refreshTopologyFromInitialNodes can be set to true
        AdvancedGlideClusterClientConfiguration config =
                AdvancedGlideClusterClientConfiguration.builder()
                        .refreshTopologyFromInitialNodes(true)
                        .build();
        assertTrue(config.isRefreshTopologyFromInitialNodes());
    }

    @Test
    public void testRefreshTopologyFromInitialNodesDisabled() {
        // Test that refreshTopologyFromInitialNodes can be explicitly set to false
        AdvancedGlideClusterClientConfiguration config =
                AdvancedGlideClusterClientConfiguration.builder()
                        .refreshTopologyFromInitialNodes(false)
                        .build();
        assertFalse(config.isRefreshTopologyFromInitialNodes());
    }

    @Test
    public void testPeriodicChecksDefault() {
        // Test that periodicChecks defaults to ENABLED_DEFAULT_CONFIGS when not specified
        AdvancedGlideClusterClientConfiguration config =
                AdvancedGlideClusterClientConfiguration.builder().build();
        assertEquals(PeriodicChecksStatus.ENABLED_DEFAULT_CONFIGS, config.getPeriodicChecks());
    }

    @Test
    public void testPeriodicChecksDisabled() {
        // Test that periodicChecks can be set to DISABLED
        AdvancedGlideClusterClientConfiguration config =
                AdvancedGlideClusterClientConfiguration.builder()
                        .periodicChecks(PeriodicChecksStatus.DISABLED)
                        .build();
        assertEquals(PeriodicChecksStatus.DISABLED, config.getPeriodicChecks());
    }

    @Test
    public void testPeriodicChecksEnabledExplicitly() {
        // Test that periodicChecks can be explicitly set to ENABLED_DEFAULT_CONFIGS
        AdvancedGlideClusterClientConfiguration config =
                AdvancedGlideClusterClientConfiguration.builder()
                        .periodicChecks(PeriodicChecksStatus.ENABLED_DEFAULT_CONFIGS)
                        .build();
        assertEquals(PeriodicChecksStatus.ENABLED_DEFAULT_CONFIGS, config.getPeriodicChecks());
    }

    @Test
    public void testPeriodicChecksManualInterval() {
        // Test that periodicChecks can be set to a manual interval
        int durationInSec = 30;
        PeriodicChecksManualInterval manualInterval =
                PeriodicChecksManualInterval.builder().durationInSec(durationInSec).build();

        AdvancedGlideClusterClientConfiguration config =
                AdvancedGlideClusterClientConfiguration.builder().periodicChecks(manualInterval).build();

        assertInstanceOf(PeriodicChecksManualInterval.class, config.getPeriodicChecks());
        assertEquals(
                durationInSec,
                ((PeriodicChecksManualInterval) config.getPeriodicChecks()).getDurationInSec());
    }

    @Test
    public void testPeriodicChecksManualIntervalValue() {
        // Test that PeriodicChecksManualInterval stores the correct duration value
        int durationInSec = 60;
        PeriodicChecksManualInterval manualInterval =
                PeriodicChecksManualInterval.builder().durationInSec(durationInSec).build();

        assertEquals(durationInSec, manualInterval.getDurationInSec());
    }

    @Test
    public void test_tcp_nodelay_default_value() {
        // Verify default is null (not set)
        AdvancedGlideClientConfiguration standaloneConfig =
                AdvancedGlideClientConfiguration.builder().build();
        assertEquals(null, standaloneConfig.getTcpNoDelay());

        AdvancedGlideClusterClientConfiguration clusterConfig =
                AdvancedGlideClusterClientConfiguration.builder().build();
        assertEquals(null, clusterConfig.getTcpNoDelay());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void test_tcp_nodelay_configuration(boolean clusterMode) {
        // Test default (null - not set)
        BaseClient defaultClient =
                clusterMode
                        ? GlideClusterClient.createClient(commonClusterClientConfig().build()).get()
                        : GlideClient.createClient(commonClientConfig().build()).get();
        if (clusterMode) {
            assertEquals("PONG", ((GlideClusterClient) defaultClient).ping().get());
        } else {
            assertEquals("PONG", ((GlideClient) defaultClient).ping().get());
        }
        assertEquals("OK", defaultClient.set("key", "value").get());
        assertEquals("value", defaultClient.get("key").get());
        defaultClient.close();

        // Test explicit true
        BaseClient clientTrue;
        if (clusterMode) {
            AdvancedGlideClusterClientConfiguration advancedConfig =
                    AdvancedGlideClusterClientConfiguration.builder().tcpNoDelay(true).build();
            clientTrue =
                    GlideClusterClient.createClient(
                                    commonClusterClientConfig().advancedConfiguration(advancedConfig).build())
                            .get();
            assertEquals("PONG", ((GlideClusterClient) clientTrue).ping().get());
        } else {
            AdvancedGlideClientConfiguration advancedConfig =
                    AdvancedGlideClientConfiguration.builder().tcpNoDelay(true).build();
            clientTrue =
                    GlideClient.createClient(
                                    commonClientConfig().advancedConfiguration(advancedConfig).build())
                            .get();
            assertEquals("PONG", ((GlideClient) clientTrue).ping().get());
        }
        assertEquals("OK", clientTrue.set("key2", "value2").get());
        assertEquals("value2", clientTrue.get("key2").get());
        clientTrue.close();

        // Test explicit false
        BaseClient clientFalse;
        if (clusterMode) {
            AdvancedGlideClusterClientConfiguration advancedConfig =
                    AdvancedGlideClusterClientConfiguration.builder().tcpNoDelay(false).build();
            clientFalse =
                    GlideClusterClient.createClient(
                                    commonClusterClientConfig().advancedConfiguration(advancedConfig).build())
                            .get();
            assertEquals("PONG", ((GlideClusterClient) clientFalse).ping().get());
        } else {
            AdvancedGlideClientConfiguration advancedConfig =
                    AdvancedGlideClientConfiguration.builder().tcpNoDelay(false).build();
            clientFalse =
                    GlideClient.createClient(
                                    commonClientConfig().advancedConfiguration(advancedConfig).build())
                            .get();
            assertEquals("PONG", ((GlideClient) clientFalse).ping().get());
        }
        assertEquals("OK", clientFalse.set("key3", "value3").get());
        assertEquals("value3", clientFalse.get("key3").get());
        clientFalse.close();
    }

    @Test
    @SneakyThrows
    public void test_address_resolver_standalone_client() {
        // Get the actual server address from test configuration
        String[] actualHostParts = STANDALONE_HOSTS[0].split(":");
        String actualHost = actualHostParts[0];
        int actualPort = Integer.parseInt(actualHostParts[1]);

        // Use a fake/placeholder address in configuration
        String fakeHost = "fake-host-that-does-not-exist.invalid";
        int fakePort = 9999;

        // Create resolver that translates fake address to real server address
        AddressResolver resolver =
                (host, port) -> {
                    // Only translate our fake address to the real one
                    if (fakeHost.equals(host) && port == fakePort) {
                        return new ResolvedAddress(actualHost, actualPort);
                    }
                    // Pass through any other addresses unchanged
                    return new ResolvedAddress(host, port);
                };

        // Configure client with fake address - connection should succeed because
        // resolver translates it to the real address
        GlideClient client =
                GlideClient.createClient(
                                GlideClientConfiguration.builder()
                                        .address(NodeAddress.builder().host(fakeHost).port(fakePort).build())
                                        .addressResolver(resolver)
                                        .build())
                        .get();

        // Verify the client works - this proves the resolved address was used
        assertEquals("PONG", client.ping().get());
        assertEquals("OK", client.set("resolver_test_key", "resolver_test_value").get());
        assertEquals("resolver_test_value", client.get("resolver_test_key").get());

        client.close();
    }

    @Test
    @SneakyThrows
    public void test_address_resolver_cluster_client() {
        // Get the actual server address from test configuration
        String[] actualHostParts = CLUSTER_HOSTS[0].split(":");
        String actualHost = actualHostParts[0];
        int actualPort = Integer.parseInt(actualHostParts[1]);

        // Use a fake/placeholder address in configuration
        String fakeHost = "fake-cluster-host.invalid";
        int fakePort = 9999;

        // Create resolver that translates fake address to real server address
        AddressResolver resolver =
                (host, port) -> {
                    // Only translate our fake address to the real one
                    if (fakeHost.equals(host) && port == fakePort) {
                        return new ResolvedAddress(actualHost, actualPort);
                    }
                    // Pass through any other addresses unchanged
                    return new ResolvedAddress(host, port);
                };

        // Configure client with fake address - connection should succeed because
        // resolver translates it to the real address
        GlideClusterClient client =
                GlideClusterClient.createClient(
                                GlideClusterClientConfiguration.builder()
                                        .address(NodeAddress.builder().host(fakeHost).port(fakePort).build())
                                        .addressResolver(resolver)
                                        .build())
                        .get();

        // Verify the client works - this proves the resolved address was used
        assertEquals("PONG", client.ping().get());
        assertEquals(
                "OK", client.set("cluster_resolver_test_key", "cluster_resolver_test_value").get());
        assertEquals("cluster_resolver_test_value", client.get("cluster_resolver_test_key").get());

        client.close();
    }

    @Test
    @SneakyThrows
    public void test_address_resolver_cluster_client_throws_exception() {
        // Get the actual server address from test configuration
        String[] actualHostParts = CLUSTER_HOSTS[0].split(":");
        String actualHost = actualHostParts[0];
        int actualPort = Integer.parseInt(actualHostParts[1]);

        AddressResolver resolver =
                (host, port) -> {
                    throw new RuntimeException("test-exception");
                };

        GlideClusterClient client =
                GlideClusterClient.createClient(
                                GlideClusterClientConfiguration.builder()
                                        .address(NodeAddress.builder().host(actualHost).port(actualPort).build())
                                        .addressResolver(resolver)
                                        .build())
                        .get();

        // Connection still goes through, as the fallback is to use the original address if resolver
        // throws an exception
        assertEquals("PONG", client.ping().get());
        assertEquals(
                "OK", client.set("cluster_resolver_test_key", "cluster_resolver_test_value").get());
        assertEquals("cluster_resolver_test_value", client.get("cluster_resolver_test_key").get());

        client.close();
    }

    @Test
    @SneakyThrows
    public void test_address_resolver_cluster_client_returns_null() {
        // Get the actual server address from test configuration
        String[] actualHostParts = CLUSTER_HOSTS[0].split(":");
        String actualHost = actualHostParts[0];
        int actualPort = Integer.parseInt(actualHostParts[1]);

        AddressResolver resolver = (host, port) -> null;

        GlideClusterClient client =
                GlideClusterClient.createClient(
                                GlideClusterClientConfiguration.builder()
                                        .address(NodeAddress.builder().host(actualHost).port(actualPort).build())
                                        .addressResolver(resolver)
                                        .build())
                        .get();

        // Connection still goes through, as the fallback is to use the original address if resolver
        // returns null
        assertEquals("PONG", client.ping().get());
        assertEquals(
                "OK", client.set("cluster_resolver_test_key", "cluster_resolver_test_value").get());
        assertEquals("cluster_resolver_test_value", client.get("cluster_resolver_test_key").get());

        client.close();
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    @SneakyThrows
    public void test_client_pause_and_unpause_standalone(ProtocolVersion protocol) {
        try (GlideClient client =
                GlideClient.createClient(commonClientConfig().protocol(protocol).build()).get()) {

            // pause with default mode (ALL implied by server)
            assertEquals(OK, client.clientPause(100).get());
            assertEquals(OK, client.clientUnpause().get());

            // pause with explicit WRITE mode
            assertEquals(OK, client.clientPause(100, ClientPauseMode.WRITE).get());
            assertEquals(OK, client.clientUnpause().get());

            // pause with explicit ALL mode
            assertEquals(OK, client.clientPause(100, ClientPauseMode.ALL).get());
            assertEquals(OK, client.clientUnpause().get());

            // zero timeout: immediately unpauses, subsequent command must succeed
            assertEquals(OK, client.clientPause(0).get());
            assertEquals("PONG", client.ping().get());
        }
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    @SneakyThrows
    public void test_client_pause_and_unpause_cluster(ProtocolVersion protocol) {
        try (GlideClusterClient client =
                GlideClusterClient.createClient(commonClusterClientConfig().protocol(protocol).build())
                        .get()) {

            // default route (ALL_PRIMARIES)
            assertEquals(OK, client.clientPause(100).get());
            assertEquals(OK, client.clientUnpause().get());

            // explicit ALL_PRIMARIES route
            assertEquals(OK, client.clientPause(100, ALL_PRIMARIES).get());
            assertEquals(OK, client.clientUnpause(ALL_PRIMARIES).get());

            // with WRITE mode, default route
            assertEquals(OK, client.clientPause(100, ClientPauseMode.WRITE).get());
            assertEquals(OK, client.clientUnpause().get());

            // with WRITE mode, explicit route
            assertEquals(OK, client.clientPause(100, ClientPauseMode.WRITE, ALL_PRIMARIES).get());
            assertEquals(OK, client.clientUnpause(ALL_PRIMARIES).get());

            // zero timeout + verify cluster still responsive
            assertEquals(OK, client.clientPause(0).get());
            assertEquals("PONG", client.ping().get());
        }
    }

    @SneakyThrows
    @Test
    public void test_client_circuit_breaker_config_connects_successfully() {
        // Verify client connects and operates normally with CB enabled
        GlideClusterClient clusterClient =
                GlideClusterClient.createClient(
                                commonClusterClientConfig()
                                        .clientCircuitBreakerConfiguration(
                                                ClientCircuitBreakerConfiguration.builder()
                                                        .windowSizeMs(10000)
                                                        .failureRateThreshold(0.5f)
                                                        .minErrors(50)
                                                        .openTimeoutMs(5000)
                                                        .countTimeouts(false)
                                                        .consecutiveSuccesses(3)
                                                        .build())
                                        .build())
                        .get();
        assertEquals("PONG", clusterClient.ping().get());
        clusterClient.close();

        GlideClient standaloneClient =
                GlideClient.createClient(
                                commonClientConfig()
                                        .clientCircuitBreakerConfiguration(
                                                ClientCircuitBreakerConfiguration.builder().build())
                                        .build())
                        .get();
        assertEquals("PONG", standaloneClient.ping().get());
        standaloneClient.close();
    }

    @Test
    @SneakyThrows
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    public void test_iam_authentication_standalone_with_mock_credentials() {
        // See DEVELOPER.md for instructions on running IAM authentication tests
        try (GlideClient client = createStandaloneClientWithIam(5)) {
            TestUtilities.assertConnected(client);
            assertEquals("OK", client.set("iam_test_key", "iam_test_value").get());
            assertEquals("iam_test_value", client.get("iam_test_key").get());
            client.refreshIamToken().get();
            assertEquals("OK", client.set("iam_test_key2", "iam_test_value2").get());
            assertEquals("iam_test_value2", client.get("iam_test_key2").get());
        }
    }

    @Test
    @Timeout(30)
    @SneakyThrows
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    public void test_iam_authentication_standalone_automatic_token_refresh() {
        try (GlideClient client = createStandaloneClientWithIam(2)) {
            TestUtilities.assertConnected(client);
            waitForScheduledRefresh(3000);
            assertEquals("OK", client.set("iam_auto_refresh_key", "iam_auto_refresh_value").get());
            assertEquals("iam_auto_refresh_value", client.get("iam_auto_refresh_key").get());
        }
    }

    @Test
    @Timeout(30)
    @SneakyThrows
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
    public void test_iam_authentication_standalone_custom_provider_invoked_on_scheduled_refresh() {
        AtomicInteger invocations = new AtomicInteger(0);
        GlideCredentialProvider provider =
                () -> {
                    invocations.incrementAndGet();
                    return AwsCredentials.builder()
                            .accessKeyId(System.getenv("AWS_ACCESS_KEY_ID"))
                            .secretAccessKey(System.getenv("AWS_SECRET_ACCESS_KEY"))
                            .sessionToken(System.getenv("AWS_SESSION_TOKEN"))
                            .build();
                };
        IamAuthConfig iamConfig =
                IamAuthConfig.builder()
                        .clusterName(IAM_TEST_CLUSTER_NAME)
                        .service(ServiceType.ELASTICACHE)
                        .region(IAM_TEST_REGION_US_EAST_1)
                        .refreshIntervalSeconds(2)
                        .credentialsProvider(provider)
                        .build();
        ServerCredentials credentials =
                ServerCredentials.builder().username(IAM_USERNAME).iamConfig(iamConfig).build();
        try (GlideClient client =
                GlideClient.createClient(commonClientConfig().credentials(credentials).build()).get()) {
            TestUtilities.assertConnected(client);
            int afterConnect = invocations.get();
            assertTrue(afterConnect > 0, "Provider not invoked on initial connect");
            waitForProviderInvocation(invocations, afterConnect, 8000);
            assertEquals(
                    "OK", client.set("iam_scheduled_refresh_key", "iam_scheduled_refresh_value").get());
            assertEquals("iam_scheduled_refresh_value", client.get("iam_scheduled_refresh_key").get());
        }
    }

    @Test
    @SneakyThrows
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
    public void test_iam_authentication_standalone_with_custom_credentials_provider() {
        AtomicInteger invocations = new AtomicInteger(0);
        GlideCredentialProvider provider =
                () -> {
                    invocations.incrementAndGet();
                    return AwsCredentials.builder()
                            .accessKeyId(System.getenv("AWS_ACCESS_KEY_ID"))
                            .secretAccessKey(System.getenv("AWS_SECRET_ACCESS_KEY"))
                            .sessionToken(System.getenv("AWS_SESSION_TOKEN"))
                            .build();
                };
        IamAuthConfig iamConfig =
                IamAuthConfig.builder()
                        .clusterName(IAM_TEST_CLUSTER_NAME)
                        .service(ServiceType.ELASTICACHE)
                        .region(IAM_TEST_REGION_US_EAST_1)
                        .refreshIntervalSeconds(5)
                        .credentialsProvider(provider)
                        .build();
        ServerCredentials credentials =
                ServerCredentials.builder().username(IAM_USERNAME).iamConfig(iamConfig).build();
        try (GlideClient client =
                GlideClient.createClient(commonClientConfig().credentials(credentials).build()).get()) {
            TestUtilities.assertConnected(client);
            assertEquals("OK", client.set("iam_custom_provider_key", "iam_custom_provider_value").get());
            assertEquals("iam_custom_provider_value", client.get("iam_custom_provider_key").get());
        }
        assertTrue(
                invocations.get() > 0,
                "Custom credentials provider was never invoked — JNI may have silently dropped the"
                        + " provider");
    }

    @Test
    @Timeout(30)
    @SneakyThrows
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
    public void test_iam_authentication_standalone_custom_provider_throws_exception() {
        // A provider that always throws should cause client creation to fail.
        GlideCredentialProvider throwingProvider =
                () -> {
                    throw new RuntimeException("injected test error from credentials provider");
                };
        IamAuthConfig iamConfig =
                IamAuthConfig.builder()
                        .clusterName(IAM_TEST_CLUSTER_NAME)
                        .service(ServiceType.ELASTICACHE)
                        .region(IAM_TEST_REGION_US_EAST_1)
                        .credentialsProvider(throwingProvider)
                        .build();
        ServerCredentials credentials =
                ServerCredentials.builder().username(IAM_USERNAME).iamConfig(iamConfig).build();
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                GlideClient.createClient(commonClientConfig().credentials(credentials).build())
                                        .get());
        // Verify the injected error message propagates through the exception chain.
        String fullMessage = getFullExceptionMessage(exception);
        assertTrue(
                fullMessage.contains("injected test error"),
                "Expected injected error message in exception chain, got: " + fullMessage);
    }

    @Test
    @SneakyThrows
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
    public void test_iam_authentication_standalone_custom_provider_with_expires_at() {
        AtomicInteger invocations = new AtomicInteger(0);
        GlideCredentialProvider provider =
                () -> {
                    invocations.incrementAndGet();
                    return AwsCredentials.builder()
                            .accessKeyId(System.getenv("AWS_ACCESS_KEY_ID"))
                            .secretAccessKey(System.getenv("AWS_SECRET_ACCESS_KEY"))
                            .sessionToken(System.getenv("AWS_SESSION_TOKEN"))
                            .expiresAt(Instant.now().plusSeconds(3600))
                            .build();
                };
        IamAuthConfig iamConfig =
                IamAuthConfig.builder()
                        .clusterName(IAM_TEST_CLUSTER_NAME)
                        .service(ServiceType.ELASTICACHE)
                        .region(IAM_TEST_REGION_US_EAST_1)
                        .credentialsProvider(provider)
                        .build();
        ServerCredentials credentials =
                ServerCredentials.builder().username(IAM_USERNAME).iamConfig(iamConfig).build();
        try (GlideClient client =
                GlideClient.createClient(commonClientConfig().credentials(credentials).build()).get()) {
            TestUtilities.assertConnected(client);
            assertEquals("OK", client.set("iam_expires_at_key", "iam_expires_at_value").get());
            assertEquals("iam_expires_at_value", client.get("iam_expires_at_key").get());
        }
        assertTrue(invocations.get() > 0, "Provider was never invoked");
    }

    @Test
    @SneakyThrows
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
    public void test_iam_authentication_standalone_custom_provider_invoked_on_manual_refresh() {
        AtomicInteger invocations = new AtomicInteger(0);
        GlideCredentialProvider provider =
                () -> {
                    invocations.incrementAndGet();
                    return AwsCredentials.builder()
                            .accessKeyId(System.getenv("AWS_ACCESS_KEY_ID"))
                            .secretAccessKey(System.getenv("AWS_SECRET_ACCESS_KEY"))
                            .sessionToken(System.getenv("AWS_SESSION_TOKEN"))
                            .build();
                };
        IamAuthConfig iamConfig =
                IamAuthConfig.builder()
                        .clusterName(IAM_TEST_CLUSTER_NAME)
                        .service(ServiceType.ELASTICACHE)
                        .region(IAM_TEST_REGION_US_EAST_1)
                        .credentialsProvider(provider)
                        .build();
        ServerCredentials credentials =
                ServerCredentials.builder().username(IAM_USERNAME).iamConfig(iamConfig).build();
        try (GlideClient client =
                GlideClient.createClient(commonClientConfig().credentials(credentials).build()).get()) {
            TestUtilities.assertConnected(client);
            int afterConnect = invocations.get();
            assertTrue(afterConnect > 0, "Provider not invoked on initial connect");
            client.refreshIamToken().get();
            assertTrue(
                    invocations.get() > afterConnect, "Provider not invoked on manual refreshIamToken()");
        }
    }

    @Test
    @SneakyThrows
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    public void test_iam_authentication_cluster_with_mock_credentials() {
        try (GlideClusterClient client = createClusterClientWithIam(5)) {
            TestUtilities.assertConnected(client);
            assertEquals("OK", client.set("iam_test_key", "iam_test_value").get());
            assertEquals("iam_test_value", client.get("iam_test_key").get());
            assertEquals("OK", client.set("iam_test_key2", "iam_test_value2").get());
            assertEquals("iam_test_value2", client.get("iam_test_key2").get());
            client.refreshIamToken().get();
        }
    }

    @Test
    @Timeout(30)
    @SneakyThrows
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    public void test_iam_authentication_cluster_automatic_token_refresh() {
        try (GlideClusterClient client = createClusterClientWithIam(2)) {
            TestUtilities.assertConnected(client);
            waitForScheduledRefresh(3000);
            assertEquals("OK", client.set("iam_auto_refresh_key", "iam_auto_refresh_value").get());
            assertEquals("iam_auto_refresh_value", client.get("iam_auto_refresh_key").get());
        }
    }

    @Test
    @Timeout(30)
    @SneakyThrows
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
    public void test_iam_authentication_cluster_custom_provider_invoked_on_scheduled_refresh() {
        AtomicInteger invocations = new AtomicInteger(0);
        GlideCredentialProvider provider =
                () -> {
                    invocations.incrementAndGet();
                    return AwsCredentials.builder()
                            .accessKeyId(System.getenv("AWS_ACCESS_KEY_ID"))
                            .secretAccessKey(System.getenv("AWS_SECRET_ACCESS_KEY"))
                            .sessionToken(System.getenv("AWS_SESSION_TOKEN"))
                            .build();
                };
        IamAuthConfig iamConfig =
                IamAuthConfig.builder()
                        .clusterName(IAM_TEST_CLUSTER_NAME)
                        .service(ServiceType.ELASTICACHE)
                        .region(IAM_TEST_REGION_US_EAST_1)
                        .refreshIntervalSeconds(2)
                        .credentialsProvider(provider)
                        .build();
        ServerCredentials credentials =
                ServerCredentials.builder().username(IAM_USERNAME).iamConfig(iamConfig).build();
        try (GlideClusterClient client =
                GlideClusterClient.createClient(
                                commonClusterClientConfig().credentials(credentials).build())
                        .get()) {
            TestUtilities.assertConnected(client);
            int afterConnect = invocations.get();
            assertTrue(afterConnect > 0, "Provider not invoked on initial connect");
            waitForProviderInvocation(invocations, afterConnect, 8000);
            assertEquals(
                    "OK", client.set("iam_scheduled_refresh_key", "iam_scheduled_refresh_value").get());
            assertEquals("iam_scheduled_refresh_value", client.get("iam_scheduled_refresh_key").get());
        }
    }

    @Test
    @SneakyThrows
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
    public void test_iam_authentication_cluster_with_custom_credentials_provider() {
        AtomicInteger invocations = new AtomicInteger(0);
        GlideCredentialProvider provider =
                () -> {
                    invocations.incrementAndGet();
                    return AwsCredentials.builder()
                            .accessKeyId(System.getenv("AWS_ACCESS_KEY_ID"))
                            .secretAccessKey(System.getenv("AWS_SECRET_ACCESS_KEY"))
                            .sessionToken(System.getenv("AWS_SESSION_TOKEN"))
                            .build();
                };
        IamAuthConfig iamConfig =
                IamAuthConfig.builder()
                        .clusterName(IAM_TEST_CLUSTER_NAME)
                        .service(ServiceType.ELASTICACHE)
                        .region(IAM_TEST_REGION_US_EAST_1)
                        .refreshIntervalSeconds(5)
                        .credentialsProvider(provider)
                        .build();
        ServerCredentials credentials =
                ServerCredentials.builder().username(IAM_USERNAME).iamConfig(iamConfig).build();
        try (GlideClusterClient client =
                GlideClusterClient.createClient(
                                commonClusterClientConfig().credentials(credentials).build())
                        .get()) {
            TestUtilities.assertConnected(client);
            assertEquals("OK", client.set("iam_custom_provider_key", "iam_custom_provider_value").get());
            assertEquals("iam_custom_provider_value", client.get("iam_custom_provider_key").get());
        }
        assertTrue(
                invocations.get() > 0,
                "Custom credentials provider was never invoked — JNI may have silently dropped the"
                        + " provider");
    }

    @Test
    @SneakyThrows
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
    public void test_iam_authentication_cluster_custom_provider_invoked_on_manual_refresh() {
        AtomicInteger invocations = new AtomicInteger(0);
        GlideCredentialProvider provider =
                () -> {
                    invocations.incrementAndGet();
                    return AwsCredentials.builder()
                            .accessKeyId(System.getenv("AWS_ACCESS_KEY_ID"))
                            .secretAccessKey(System.getenv("AWS_SECRET_ACCESS_KEY"))
                            .sessionToken(System.getenv("AWS_SESSION_TOKEN"))
                            .build();
                };
        IamAuthConfig iamConfig =
                IamAuthConfig.builder()
                        .clusterName(IAM_TEST_CLUSTER_NAME)
                        .service(ServiceType.ELASTICACHE)
                        .region(IAM_TEST_REGION_US_EAST_1)
                        .credentialsProvider(provider)
                        .build();
        ServerCredentials credentials =
                ServerCredentials.builder().username(IAM_USERNAME).iamConfig(iamConfig).build();
        try (GlideClusterClient client =
                GlideClusterClient.createClient(
                                commonClusterClientConfig().credentials(credentials).build())
                        .get()) {
            TestUtilities.assertConnected(client);
            int afterConnect = invocations.get();
            assertTrue(afterConnect > 0, "Provider not invoked on initial connect");
            client.refreshIamToken().get();
            assertTrue(
                    invocations.get() > afterConnect, "Provider not invoked on manual refreshIamToken()");
        }
    }

    @SneakyThrows
    private GlideClient createStandaloneClientWithIam(int refreshIntervalSeconds) {
        IamAuthConfig iamConfig = createTestIamConfig(refreshIntervalSeconds);
        ServerCredentials credentials =
                ServerCredentials.builder().username(IAM_USERNAME).iamConfig(iamConfig).build();
        return GlideClient.createClient(commonClientConfig().credentials(credentials).build()).get();
    }

    @SneakyThrows
    private GlideClusterClient createClusterClientWithIam(int refreshIntervalSeconds) {
        IamAuthConfig iamConfig = createTestIamConfig(refreshIntervalSeconds);
        ServerCredentials credentials =
                ServerCredentials.builder().username(IAM_USERNAME).iamConfig(iamConfig).build();
        return GlideClusterClient.createClient(
                        commonClusterClientConfig().credentials(credentials).build())
                .get();
    }

    /** Collects all exception messages in the cause chain into a single string. */
    private static String getFullExceptionMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        while (t != null) {
            if (t.getMessage() != null) {
                sb.append(t.getMessage()).append(" | ");
            }
            t = t.getCause();
        }
        return sb.toString();
    }

    /**
     * Polls until {@code counter.get() > baseline} or {@code timeoutMs} elapses.
     *
     * @throws AssertionError if the counter does not increase within the deadline
     */
    @SneakyThrows
    private static void waitForProviderInvocation(
            AtomicInteger counter, int baseline, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (counter.get() <= baseline) {
            if (System.currentTimeMillis() >= deadline) {
                throw new AssertionError(
                        "Custom credentials provider was not re-invoked within "
                                + timeoutMs
                                + "ms (baseline="
                                + baseline
                                + ", current="
                                + counter.get()
                                + ")");
            }
            Thread.sleep(100);
        }
    }

    /**
     * Waits at least {@code millis} milliseconds to allow a background token refresh to occur.
     *
     * <p>This is intentionally a fixed sleep: these tests use the default AWS credential chain and
     * have no counter or hook to poll. The sleep gives the background refresh task (running at {@code
     * refreshIntervalSeconds=2}) time to fire at least once before the assertions.
     */
    @SneakyThrows
    private static void waitForScheduledRefresh(long millis) {
        Thread.sleep(millis);
    }
}
