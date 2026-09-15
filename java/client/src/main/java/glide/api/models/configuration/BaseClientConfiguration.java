/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.experimental.SuperBuilder;

/**
 * Configuration settings class for creating a client. Shared settings for standalone and cluster
 * clients.
 */
@Getter
@SuppressFBWarnings(
        value = "CT_CONSTRUCTOR_THROW",
        justification = "Lombok-generated builder validates inputs and throws before object escapes")
@SuperBuilder
public abstract class BaseClientConfiguration {
    /**
     * DNS Addresses and ports of known nodes in the cluster. If the server is in cluster mode the
     * list can be partial, as the client will attempt to map out the cluster and find all nodes. If
     * the server is in standalone mode, only nodes whose addresses were provided will be used by the
     * client. For example: <code>[ {address:sample-address-0001.use1.cache.amazonaws.com, port:6379},
     * {address: sample-address-0002.use2.cache.amazonaws.com, port:6379} ]</code>.
     */
    @Getter(AccessLevel.NONE)
    @Singular
    private final List<NodeAddress> addresses;

    /** Client-side cache configuration. If provided, enables caching for this client. */
    public abstract ClientSideCache getClientSideCache();

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
    private final ServerCredentials credentials;

    /**
     * The duration in milliseconds that the client should wait for a request to complete. This
     * duration encompasses sending the request, awaiting for a response from the server, and any
     * required reconnection or retries. If the specified timeout is exceeded for a pending request,
     * it will result in a timeout error. If not explicitly set, a default value of 250 milliseconds
     * will be used.
     */
    private final Integer requestTimeout;

    /**
     * Client name to be used for the client. Will be used with CLIENT SETNAME command during
     * connection establishment.
     */
    private final String clientName;

    /**
     * Optional library-name override sent with {@code CLIENT SETINFO LIB-NAME} during connection
     * establishment. If null or empty, {@code GlideJava} is used. When {@link #clientInfoTag} is
     * present, it is appended to the effective library name in parentheses. Every character in a
     * non-empty override must be printable ASCII from {@code !} (U+0021) through {@code ~} (U+007E),
     * inclusive, excluding {@code (} and {@code )}. Dedicated standalone monitor clients also apply
     * this option. See: validateClientAttr in
     * https://github.com/valkey-io/valkey/blob/4e98093b208f956050fb441d89e1e2d7f91ac466/src/networking.c
     */
    private final String libName;

    /**
     * Optional attribution tag appended to the effective library name in parentheses. For example,
     * {@code GlideJava(my-framework:1.2.3)}. Every character in a non-empty tag must be printable
     * ASCII from {@code !} (U+0021) through {@code ~} (U+007E), inclusive, excluding {@code (} and
     * {@code )}. If null or empty, no tag is appended. Dedicated standalone monitor clients also
     * apply this option. See: validateClientAttr in
     * https://github.com/valkey-io/valkey/blob/4e98093b208f956050fb441d89e1e2d7f91ac466/src/networking.c
     */
    private final String clientInfoTag;

    /**
     * Serialization protocol to be used with the server. If not set, {@link ProtocolVersion#RESP3}
     * will be used.
     */
    private final ProtocolVersion protocol;

    /** Returns the subscription configuration for the client. */
    public abstract BaseSubscriptionConfiguration getSubscriptionConfiguration();

    /** Returns the advanced configuration settings for the client. */
    public abstract AdvancedBaseClientConfiguration getAdvancedConfiguration();

    /**
     * The maximum number of concurrent requests allowed to be in-flight (sent but not yet completed).
     * This limit is used to control the memory usage and prevent the client from overwhelming the
     * server or getting stuck in case of a queue backlog. If not set, a default value of 1000 will be
     * used.
     */
    private final Integer inflightRequestsLimit;

    /**
     * Configuration for the client-wide circuit breaker. When set, enables the circuit breaker which
     * detects sustained error rates and rejects requests at the FFI boundary before threads park.
     *
     * <p>If null (default), the circuit breaker is disabled.
     */
    private final ClientCircuitBreakerConfiguration clientCircuitBreakerConfiguration;

    /**
     * Availability Zone of the client, used by the AZ-affinity read strategies:
     *
     * <ul>
     *   <li>{@link ReadFrom#AZ_AFFINITY}: readonly commands go to replicas in this AZ.
     *   <li>{@link ReadFrom#AZ_AFFINITY_REPLICAS_AND_PRIMARY}: readonly commands go to nodes in this
     *       AZ, replicas before the primary.
     *   <li>{@link ReadFrom#AZ_AFFINITY_ALL_NODES}: readonly commands are spread equally across all
     *       nodes in this AZ.
     * </ul>
     *
     * <p>Required for all three. {@code createClient} throws a {@code ConfigurationError} when it is
     * unset or blank.
     */
    private final String clientAZ;

    /** Strategy used to determine how and when to reconnect, in case of connection failures. */
    private final BackoffStrategy reconnectStrategy;

    /**
     * Index of the logical database to connect to. Must be non-negative and within the range
     * supported by the server configuration. If not specified, defaults to database 0.
     */
    private final Integer databaseId;

    /**
     * Enables lazy connection mode, where physical connections to the server(s) are deferred until
     * the first command is sent. This can reduce startup latency and allow for client creation in
     * disconnected environments.
     *
     * <p>When set to {@code true}, the client will not attempt to connect to the specified nodes
     * during initialization. Instead, connections will be established only when a command is actually
     * executed.
     *
     * <p>Note that the first command executed with lazy connections may experience additional latency
     * as it needs to establish the connection first. During this initial connection, the standard
     * request timeout does not apply yet - instead, the connection establishment is governed by
     * {@code connectionTimeout}. The request timeout ({@code requestTimeout}) only begins counting
     * after the connection has been successfully established. This behavior can effectively increase
     * the total time needed for the first command to complete.
     *
     * <p>This setting applies to both standalone and cluster modes. Note that if an operation is
     * attempted and connection fails (e.g., unreachable nodes), errors will surface at that point.
     *
     * <p>If not set, connections are established immediately during client creation (equivalent to
     * {@code false}).
     */
    @Builder.Default private final boolean lazyConnect = false;

    /**
     * Optional callback for resolving server addresses before connection.
     *
     * <p>If provided, this callback will be invoked for each configured address during connection
     * establishment. The callback receives the configured host and port, and should return the actual
     * host and port to use for the connection.
     *
     * <p>This is useful for:
     *
     * <ul>
     *   <li>Custom DNS resolution for service discovery
     *   <li>Address translation for proxy setups
     *   <li>Dynamic endpoint resolution for cloud environments
     * </ul>
     *
     * <p>If not set, addresses are used as configured without modification.
     *
     * @example
     *     <pre>{@code
     * AddressResolver resolver = (host, port) -> {
     *     String resolvedHost = myDnsResolver.resolve(host);
     *     return new ResolvedAddress(resolvedHost, port);
     * };
     *
     * GlideClientConfiguration config = GlideClientConfiguration.builder()
     *     .address(NodeAddress.builder().host("my-service").port(6379).build())
     *     .addressResolver(resolver)
     *     .build();
     * }</pre>
     */
    private final AddressResolver addressResolver;

    /*
     * Configuration for automatic transparent compression of values.
     *
     * <p>When set, values sent to the server will be compressed using the specified backend if they
     * meet the minimum size threshold. Compressed values are automatically decompressed on retrieval.
     *
     * <p><b>Note:</b> This feature is experimental. Currently only applies to GET and SET commands.
     *
     * @see CompressionConfiguration
     */
    private final CompressionConfiguration compressionConfiguration;

    /** Abstract builder class for {@link BaseClientConfiguration}. */
    public abstract static class BaseClientConfigurationBuilder<
            C extends BaseClientConfiguration, B extends BaseClientConfigurationBuilder<C, B>> {

        /**
         * Sets the optional library-name override.
         *
         * @param libName library-name override; null or empty means absent
         * @return this builder
         * @throws IllegalArgumentException if a non-empty override contains a character outside
         *     printable ASCII U+0021 through U+007E or contains {@code (} or {@code )}. See:
         *     validateClientAttr in
         *     https://github.com/valkey-io/valkey/blob/4e98093b208f956050fb441d89e1e2d7f91ac466/src/networking.c
         */
        public B libName(String libName) {
            validatePrintableAscii("libName", libName);
            this.libName = libName;
            return self();
        }

        /**
         * Sets the optional client information tag.
         *
         * @param clientInfoTag attribution tag; null or empty means absent
         * @return this builder
         * @throws IllegalArgumentException if a non-empty tag contains a character outside printable
         *     ASCII U+0021 through U+007E or contains {@code (} or {@code )}. See: validateClientAttr
         *     in
         *     https://github.com/valkey-io/valkey/blob/4e98093b208f956050fb441d89e1e2d7f91ac466/src/networking.c
         */
        public B clientInfoTag(String clientInfoTag) {
            validatePrintableAscii("clientInfoTag", clientInfoTag);
            this.clientInfoTag = clientInfoTag;
            return self();
        }

        private static void validatePrintableAscii(String fieldName, String value) {
            if (value != null
                    && value
                            .codePoints()
                            .anyMatch(
                                    codePoint ->
                                            codePoint < '!' || codePoint > '~' || codePoint == '(' || codePoint == ')')) {
                throw new IllegalArgumentException(
                        fieldName
                                + " must contain only printable ASCII characters from '!' through '~', excluding"
                                + " '(' and ')'");
            }
        }
    }

    public List<NodeAddress> getAddresses() {
        return Collections.unmodifiableList(new ArrayList<>(addresses));
    }

    /**
     * Returns the address resolver if configured.
     *
     * @return An Optional containing the AddressResolver, or empty if not configured
     */
    public Optional<AddressResolver> getAddressResolver() {
        return Optional.ofNullable(addressResolver);
    }
}
