/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import glide.api.RedisClient;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Represents the configuration settings for a Standalone {@link RedisClient}.
 *
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
 *         .reconnectStrategy(reconnectionConfiguration)
 *         .databaseId(1)
 *         .clientName("GLIDE")
 *         .subscriptionConfiguration(subscriptionConfiguration)
 *         .build();
 * }</pre>
 */
@Getter
@SuperBuilder
public class RedisClientConfiguration extends BaseClientConfiguration {
    /** Strategy used to determine how and when to reconnect, in case of connection failures. */
    private final BackoffStrategy reconnectStrategy;

    /** Index of the logical database to connect to. */
    private final Integer databaseId;

    /** Subscription configuration for the current client. */
    private final StandaloneSubscriptionConfiguration subscriptionConfiguration;
}
