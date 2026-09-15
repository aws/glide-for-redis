/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import glide.api.GlideClient;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Represents the configuration settings for a Standalone {@link GlideClient}.
 *
 * @example
 *     <pre>{@code
 * GlideClientConfiguration glideClientConfiguration =
 *     GlideClientConfiguration.builder()
 *         .address(node1address)
 *         .address(node2address)
 *         .useTLS(true)
 *         .readFrom(ReadFrom.PREFER_REPLICA)
 *         .credentials(credentialsConfiguration)
 *         .requestTimeout(2000)
 *         .reconnectStrategy(reconnectionConfiguration)
 *         .databaseId(1)
 *         .clientName("GLIDE")
 *         .subscriptionConfiguration(subscriptionConfiguration)
 *         .inflightRequestsLimit(1000)
 *         .clientSideCache(ClientSideCache.create(1024, 60000))
 *         .advancedConfiguration(AdvancedGlideClientConfiguration.builder().connectionTimeout(500).build())
 *         .readOnly(true)
 *         .build();
 * }</pre>
 */
@Getter
@SuperBuilder
@ToString
public class GlideClientConfiguration extends BaseClientConfiguration {

    /** Client-side cache configuration. If provided, enables caching for this client. */
    private final ClientSideCache clientSideCache;

    /** Subscription configuration for the current client. */
    private final StandaloneSubscriptionConfiguration subscriptionConfiguration;

    /** Advanced configuration settings for the client. */
    @Builder.Default
    private final AdvancedGlideClientConfiguration advancedConfiguration =
            AdvancedGlideClientConfiguration.builder().build();

    /**
     * When true, enables read-only mode for the standalone client. In read-only mode:
     *
     * <ul>
     *   <li>The client skips primary node detection (INFO REPLICATION command)
     *   <li>All connected nodes are treated as valid read targets
     *   <li>Write commands are blocked and will return an error
     *   <li>The default ReadFrom strategy becomes PREFER_REPLICA if not explicitly set
     * </ul>
     *
     * <p>This is useful for connecting to replica-only deployments or when you want to prevent
     * accidental write operations.
     *
     * <p>Note: read-only mode is not compatible with the AZ_AFFINITY,
     * AZ_AFFINITY_REPLICAS_AND_PRIMARY, or AZ_AFFINITY_ALL_NODES read strategies. Attempting to
     * combine them fails during client creation.
     *
     * <p>Defaults to false.
     */
    @Builder.Default private final boolean readOnly = false;

    /**
     * Controls how the client discovers node roles and topology in standalone mode. Defaults to
     * {@link NodeDiscoveryMode#STANDARD}.
     */
    @Builder.Default private final NodeDiscoveryMode nodeDiscoveryMode = NodeDiscoveryMode.STANDARD;
}
