/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import glide.connectors.resources.ThreadPoolResource;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.experimental.SuperBuilder;

/**
 * Configuration settings class for creating a Redis Client. Shared settings for standalone and
 * cluster clients.
 */
@Getter
@SuperBuilder
public abstract class BaseClientConfiguration {
    /**
     * DNS Addresses and ports of known nodes in the cluster. If the server is in cluster mode the
     * list can be partial, as the client will attempt to map out the cluster and find all nodes. If
     * the server is in standalone mode, only nodes whose addresses were provided will be used by the
     * client. For example: <code>[ {address:sample-address-0001.use1.cache.amazonaws.com, port:6379},
     * {address: sample-address-0002.use2.cache.amazonaws.com, port:6379} ]</code>.
     */
    @Singular private final List<NodeAddress> addresses;

    /**
     * True if communication with the cluster should use Transport Level Security.
     *
     * <p>If the server/cluster requires TLS, not setting this will cause the connection attempt to
     * fail.
     *
     * <p>If the server/cluster doesn't require TLS, setting this will also cause the connection
     * attempt to fail.
     */
    @Builder.Default private final boolean useTLS = false;

    /** Represents the client's read from strategy. */
    @NonNull @Builder.Default private final ReadFrom readFrom = ReadFrom.PRIMARY;

    /**
     * Credentials for authentication process. If none are set, the client will not authenticate
     * itself with the server.
     */
    private final RedisCredentials credentials;

    /**
     * The duration in milliseconds that the client should wait for a request to complete. This
     * duration encompasses sending the request, awaiting for a response from the server, and any
     * required reconnections or retries. If the specified timeout is exceeded for a pending request,
     * it will result in a timeout error. If not set, a default value will be used.
     */
    private final Integer requestTimeout;

    /**
     * Client name to be used for the client. Will be used with CLIENT SETNAME command during
     * connection establishment.
     */
    private final String clientName;

    /**
     * Advanced users can pass an extended {@link ThreadPoolResource} to pass a user-defined event
     * loop group. If set, users are responsible for shutting the resource down when no longer in use.
     */
    private final ThreadPoolResource threadPoolResource;

    public abstract BaseSubscriptionConfiguration getSubscriptionConfiguration();
}
