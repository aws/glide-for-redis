/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import glide.api.BaseClient;
import glide.api.models.PubSubMessage;
import glide.api.models.configuration.ClusterSubscriptionConfiguration.ClusterSubscriptionConfigurationBuilder;
import glide.api.models.configuration.ClusterSubscriptionConfiguration.PubSubClusterChannelMode;
import glide.api.models.configuration.StandaloneSubscriptionConfiguration.PubSubChannelMode;
import glide.api.models.configuration.StandaloneSubscriptionConfiguration.StandaloneSubscriptionConfigurationBuilder;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Client subscription configuration. Could be either {@link StandaloneSubscriptionConfiguration} or
 * {@link ClusterSubscriptionConfiguration}.
 */
@Getter
@RequiredArgsConstructor
public abstract class BaseSubscriptionConfiguration {

    /**
     * A channel subscription mode. Could be either {@link PubSubChannelMode} or {@link
     * PubSubClusterChannelMode}.
     */
    public interface ChannelMode {}

    /**
     * Callback called for every incoming message. It should be a fast, non-blocking operation to
     * avoid issues. A next call could happen even before then the previous call complete.<br>
     * The callback arguments are:
     *
     * <ol>
     *   <li>A received {@link PubSubMessage}.
     *   <li>A user-defined {@link #context} or <code>null</code> if not configured.
     * </ol>
     */
    public interface MessageCallback extends BiConsumer<PubSubMessage, Object> {}

    /**
     * Optional callback to accept the incoming messages. See {@link MessageCallback}.<br>
     * If not set, messages will be available via {@link BaseClient#tryGetPubSubMessage()} or {@link
     * BaseClient#getPubSubMessage()}.
     */
    protected final Optional<MessageCallback> callback;

    /**
     * Optional arbitrary context, which will be passed to callback along with all received messages.
     * <br>
     * Could be used to distinguish clients if multiple clients use a shared callback.
     */
    protected final Optional<Object> context;

    // All code below is a custom implementation of `SuperBuilder`, because we provide
    // custom user-friendly API `callback` and `subscription`.
    /**
     * Superclass for {@link ClusterSubscriptionConfigurationBuilder} and for {@link
     * StandaloneSubscriptionConfigurationBuilder}.
     */
    public abstract static class BaseSubscriptionConfigurationBuilder<
            B extends BaseSubscriptionConfigurationBuilder<B, C>,
            C extends BaseSubscriptionConfiguration> {

        protected Optional<MessageCallback> callback = Optional.empty();
        protected Optional<Object> context = Optional.empty();

        protected <M extends ChannelMode> void addSubscription(
                Map<M, Set<String>> subscriptions, M mode, String channelOrPattern) {
            if (!subscriptions.containsKey(mode)) {
                subscriptions.put(mode, new HashSet<>());
            }
            subscriptions.get(mode).add(channelOrPattern);
        }

        protected abstract B self();

        protected abstract C build();

        /**
         * Set a callback and a context.
         *
         * @param callback The {@link #callback}.
         * @param context The {@link #context}.
         */
        public B callback(MessageCallback callback, Object context) {
            this.callback = Optional.ofNullable(callback);
            this.context = Optional.ofNullable(context);
            return self();
        }

        /**
         * Set a callback without context. <code>null</code> will be supplied to all callback calls as a
         * context.
         *
         * @param callback The {@link #callback}.
         */
        public B callback(MessageCallback callback) {
            this.callback = Optional.ofNullable(callback);
            return self();
        }
    }
}
