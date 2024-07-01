/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import glide.api.RedisClusterClient;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Represents the configuration settings for a Cluster Redis client {@link RedisClusterClient}.
 *
 * @apiNote Currently, the reconnection strategy in cluster mode is not configurable, and
 *     exponential backoff with fixed values is used.
 * @example
 *     <pre>{@code
 * RedisClientConfiguration redisClientConfiguration =
 *     RedisClientConfiguration.builder()
 *         .address(node1address)
 *         .address(node2address)
 *         .useTLS(true)
 *         .readFrom(ReadFrom.PREFER_REPLICA)
 *         .credentials(credentialsConfiguration)
 *         .requestTimeout(2000)
 *         .clientName("GLIDE")
 *         .subscriptionConfiguration(subscriptionConfiguration)
 *         .build();
 * }</pre>
 */
@SuperBuilder
@Getter
public class RedisClusterClientConfiguration extends BaseClientConfiguration {

    /** Subscription configuration for the current client. */
    private final ClusterSubscriptionConfiguration subscriptionConfiguration;
}
