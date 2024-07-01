/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import glide.api.RedisClient;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;

/**
 * Subscription configuration for {@link RedisClient}.
 *
 * @example
 *     <pre>{@code
 * // Configuration with 2 subscriptions, a callback, and a context:
 * StandaloneSubscriptionConfiguration subscriptionConfiguration =
 *     StandaloneSubscriptionConfiguration.builder()
 *         .subscription(EXACT, "notifications")
 *         .subscription(PATTERN, "news.*")
 *         .callback(callback, messageConsumer)
 *         .build();
 * // Now it could be supplied to `RedisClientConfiguration`:
 * RedisClientConfiguration clientConfiguration =
 *     RedisClientConfiguration.builder()
 *         .address(NodeAddress.builder().port(6379).build())
 *         .subscriptionConfiguration(subscriptionConfiguration)
 *         .build();
 * }</pre>
 */
@Getter
public final class StandaloneSubscriptionConfiguration extends BaseSubscriptionConfiguration {

    /**
     * Describes subscription modes for standalone client.
     *
     * @see <a href="https://valkey.io/docs/topics/pubsub/">redis.io</a> for details.
     */
    public enum PubSubChannelMode implements ChannelMode {
        /** Use exact channel names. */
        EXACT,
        /** Use glob-style channel name patterns. */
        PATTERN,
    }

    /**
     * PubSub subscriptions to be used for the client.<br>
     * Will be applied via <code>SUBSCRIBE</code>/<code>PSUBSCRIBE</code> commands during connection
     * establishment.
     */
    private final Map<PubSubChannelMode, Set<String>> subscriptions;

    // All code below is a custom implementation of `SuperBuilder`
    public StandaloneSubscriptionConfiguration(
            Optional<MessageCallback> callback,
            Optional<Object> context,
            Map<PubSubChannelMode, Set<String>> subscriptions) {
        super(callback, context);
        this.subscriptions = subscriptions;
    }

    public static StandaloneSubscriptionConfigurationBuilder builder() {
        return new StandaloneSubscriptionConfigurationBuilder();
    }

    /** Builder for {@link StandaloneSubscriptionConfiguration}. */
    public static final class StandaloneSubscriptionConfigurationBuilder
            extends BaseSubscriptionConfigurationBuilder<
                    StandaloneSubscriptionConfigurationBuilder, StandaloneSubscriptionConfiguration> {

        private StandaloneSubscriptionConfigurationBuilder() {}

        private Map<PubSubChannelMode, Set<String>> subscriptions = new HashMap<>(2);

        /**
         * Add a subscription to a channel or to multiple channels if {@link PubSubChannelMode#PATTERN}
         * is used.<br>
         * See {@link StandaloneSubscriptionConfiguration#subscriptions}.
         */
        public StandaloneSubscriptionConfigurationBuilder subscription(
                PubSubChannelMode mode, String channelOrPattern) {
            addSubscription(subscriptions, mode, channelOrPattern);
            return self();
        }

        /**
         * Set all subscriptions in a bulk. Rewrites previously stored configurations.<br>
         * See {@link StandaloneSubscriptionConfiguration#subscriptions}.
         */
        public StandaloneSubscriptionConfigurationBuilder subscriptions(
                Map<PubSubChannelMode, Set<String>> subscriptions) {
            this.subscriptions = subscriptions;
            return this;
        }

        /**
         * Set subscriptions in a bulk for the given mode. Rewrites previously stored configurations for
         * that mode.<br>
         * See {@link StandaloneSubscriptionConfiguration#subscriptions}.
         */
        public StandaloneSubscriptionConfigurationBuilder subscriptions(
                PubSubChannelMode mode, Set<String> subscriptions) {
            this.subscriptions.put(mode, subscriptions);
            return this;
        }

        @Override
        protected StandaloneSubscriptionConfigurationBuilder self() {
            return this;
        }

        @Override
        public StandaloneSubscriptionConfiguration build() {
            return new StandaloneSubscriptionConfiguration(callback, context, subscriptions);
        }
    }
}
