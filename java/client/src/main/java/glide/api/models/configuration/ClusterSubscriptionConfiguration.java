/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import glide.api.RedisClusterClient;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;

/**
 * Subscription configuration for {@link RedisClusterClient}.
 *
 * @example
 *     <pre>{@code
 * // Configuration with 3 subscriptions and a callback:
 * ClusterSubscriptionConfiguration subscriptionConfiguration =
 *     ClusterSubscriptionConfiguration.builder()
 *         .subscription(EXACT, "notifications")
 *         .subscription(EXACT, "news")
 *         .subscription(SHARDED, "data")
 *         .callback(callback)
 *         .build();
 * // Now it could be supplied to `RedisClusterClientConfiguration`:
 * RedisClusterClientConfiguration clientConfiguration =
 *     RedisClusterClientConfiguration.builder()
 *         .address(NodeAddress.builder().port(6379).build())
 *         .subscriptionConfiguration(subscriptionConfiguration)
 *         .build();
 * }</pre>
 */
@Getter
public final class ClusterSubscriptionConfiguration extends BaseSubscriptionConfiguration {

    /**
     * Describes subscription modes for cluster client.
     *
     * @see <a href="https://valkey.io/docs/topics/pubsub/">redis.io</a> for details.
     */
    public enum PubSubClusterChannelMode implements ChannelMode {
        /** Use exact channel names. */
        EXACT,
        /** Use glob-style channel name patterns. */
        PATTERN,
        /**
         * Use sharded pubsub.
         *
         * @since Redis 7.0 and above.
         */
        SHARDED,
    }

    /**
     * PubSub subscriptions to be used for the client.<br>
     * Will be applied via <code>SUBSCRIBE</code>/<code>PSUBSCRIBE</code>/<code>SSUBSCRIBE</code>
     * commands during connection establishment.
     */
    private final Map<PubSubClusterChannelMode, Set<String>> subscriptions;

    // All code below is a custom implementation of `SuperBuilder`
    private ClusterSubscriptionConfiguration(
            Optional<MessageCallback> callback,
            Optional<Object> context,
            Map<PubSubClusterChannelMode, Set<String>> subscriptions) {
        super(callback, context);
        this.subscriptions = subscriptions;
    }

    public static ClusterSubscriptionConfigurationBuilder builder() {
        return new ClusterSubscriptionConfigurationBuilder();
    }

    /** Builder for {@link ClusterSubscriptionConfiguration}. */
    public static final class ClusterSubscriptionConfigurationBuilder
            extends BaseSubscriptionConfigurationBuilder<
                    ClusterSubscriptionConfigurationBuilder, ClusterSubscriptionConfiguration> {

        private ClusterSubscriptionConfigurationBuilder() {}

        private Map<PubSubClusterChannelMode, Set<String>> subscriptions = new HashMap<>(3);

        /**
         * Add a subscription to a channel or to multiple channels if {@link
         * PubSubClusterChannelMode#PATTERN} is used.<br>
         * See {@link ClusterSubscriptionConfiguration#subscriptions}.
         */
        public ClusterSubscriptionConfigurationBuilder subscription(
                PubSubClusterChannelMode mode, String channelOrPattern) {
            addSubscription(subscriptions, mode, channelOrPattern);
            return this;
        }

        /**
         * Set all subscriptions in a bulk. Rewrites previously stored configurations.<br>
         * See {@link ClusterSubscriptionConfiguration#subscriptions}.
         */
        public ClusterSubscriptionConfigurationBuilder subscriptions(
                Map<PubSubClusterChannelMode, Set<String>> subscriptions) {
            this.subscriptions = subscriptions;
            return this;
        }

        /**
         * Set subscriptions in a bulk for the given mode. Rewrites previously stored configurations for
         * that mode.<br>
         * See {@link ClusterSubscriptionConfiguration#subscriptions}.
         */
        public ClusterSubscriptionConfigurationBuilder subscriptions(
                PubSubClusterChannelMode mode, Set<String> subscriptions) {
            this.subscriptions.put(mode, subscriptions);
            return this;
        }

        @Override
        protected ClusterSubscriptionConfigurationBuilder self() {
            return this;
        }

        @Override
        public ClusterSubscriptionConfiguration build() {
            return new ClusterSubscriptionConfiguration(callback, context, subscriptions);
        }
    }
}
