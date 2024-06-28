/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import connection_request.ConnectionRequestOuterClass;
import connection_request.ConnectionRequestOuterClass.AuthenticationInfo;
import connection_request.ConnectionRequestOuterClass.ConnectionRequest;
import connection_request.ConnectionRequestOuterClass.TlsMode;
import glide.api.models.configuration.BaseClientConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ReadFrom;
import glide.api.models.exceptions.ClosingException;
import glide.connectors.handlers.ChannelHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import response.ResponseOuterClass.Response;

/**
 * Service responsible for submitting connection requests to a socket channel handler and unpack
 * responses from the same socket channel handler.
 */
@RequiredArgsConstructor
public class ConnectionManager {

    // TODO: consider making connection manager static, and moving the
    // ChannelHandler to the
    // GlideClient.
    /** UDS connection representation. */
    private final ChannelHandler channel;

    /**
     * Make a connection request to Redis Rust-core client.
     *
     * @param configuration Connection Request Configuration
     */
    public CompletableFuture<Void> connectToRedis(BaseClientConfiguration configuration) {
        ConnectionRequest request = createConnectionRequest(configuration);
        return channel
                .connect(request)
                .exceptionally(this::exceptionHandler)
                .thenApplyAsync(this::checkGlideRsResponse);
    }

    /**
     * Exception handler for future pipeline.
     *
     * @param e An exception thrown in the pipeline before
     * @return Nothing, it always rethrows the exception
     */
    private Response exceptionHandler(Throwable e) {
        channel.close();
        if (e instanceof RuntimeException) {
            // RedisException also goes here
            throw (RuntimeException) e;
        }
        throw new RuntimeException(e);
    }

    /**
     * Close the connection and the corresponding channel. Creates a ConnectionRequest protobuf
     * message based on the type of client Standalone/Cluster.
     *
     * @param configuration Connection Request Configuration
     * @return ConnectionRequest protobuf message
     */
    private ConnectionRequest createConnectionRequest(BaseClientConfiguration configuration) {
        if (configuration instanceof GlideClusterClientConfiguration) {
            return setupConnectionRequestBuilderGlideClusterClient(
                            (GlideClusterClientConfiguration) configuration)
                    .build();
        }

        return setupConnectionRequestBuilderGlideClient((GlideClientConfiguration) configuration)
                .build();
    }

    /**
     * Creates ConnectionRequestBuilder, so it has appropriate fields for the BaseClientConfiguration
     * where the Standalone/Cluster inherit from.
     *
     * @param configuration
     */
    private ConnectionRequest.Builder setupConnectionRequestBuilderBaseConfiguration(
            BaseClientConfiguration configuration) {
        ConnectionRequest.Builder connectionRequestBuilder = ConnectionRequest.newBuilder();
        for (NodeAddress nodeAddress : configuration.getAddresses()) {
            connectionRequestBuilder.addAddresses(
                    ConnectionRequestOuterClass.NodeAddress.newBuilder()
                            .setHost(nodeAddress.getHost())
                            .setPort(nodeAddress.getPort())
                            .build());
        }

        connectionRequestBuilder
                .setTlsMode(configuration.isUseTLS() ? TlsMode.SecureTls : TlsMode.NoTls)
                .setReadFrom(mapReadFromEnum(configuration.getReadFrom()));

        if (configuration.getCredentials() != null) {
            AuthenticationInfo.Builder authenticationInfoBuilder = AuthenticationInfo.newBuilder();
            if (configuration.getCredentials().getUsername() != null) {
                authenticationInfoBuilder.setUsername(configuration.getCredentials().getUsername());
            }
            authenticationInfoBuilder.setPassword(configuration.getCredentials().getPassword());

            connectionRequestBuilder.setAuthenticationInfo(authenticationInfoBuilder.build());
        }

        if (configuration.getRequestTimeout() != null) {
            connectionRequestBuilder.setRequestTimeout(configuration.getRequestTimeout());
        }

        if (configuration.getClientName() != null) {
            connectionRequestBuilder.setClientName(configuration.getClientName());
        }

        return connectionRequestBuilder;
    }

    /**
     * Creates ConnectionRequestBuilder, so it has appropriate fields for the Redis Standalone Client.
     *
     * @param configuration Connection Request Configuration
     */
    private ConnectionRequest.Builder setupConnectionRequestBuilderGlideClient(
            GlideClientConfiguration configuration) {
        ConnectionRequest.Builder connectionRequestBuilder =
                setupConnectionRequestBuilderBaseConfiguration(configuration);
        connectionRequestBuilder.setClusterModeEnabled(false);
        if (configuration.getReconnectStrategy() != null) {
            connectionRequestBuilder.setConnectionRetryStrategy(
                    ConnectionRequestOuterClass.ConnectionRetryStrategy.newBuilder()
                            .setNumberOfRetries(configuration.getReconnectStrategy().getNumOfRetries())
                            .setFactor(configuration.getReconnectStrategy().getFactor())
                            .setExponentBase(configuration.getReconnectStrategy().getExponentBase())
                            .build());
        }

        if (configuration.getDatabaseId() != null) {
            connectionRequestBuilder.setDatabaseId(configuration.getDatabaseId());
        }

        return connectionRequestBuilder;
    }

    /**
     * Creates ConnectionRequestBuilder, so it has appropriate fields for the Redis Cluster Client.
     *
     * @param configuration
     */
    private ConnectionRequestOuterClass.ConnectionRequest.Builder
            setupConnectionRequestBuilderGlideClusterClient(
                    GlideClusterClientConfiguration configuration) {
        ConnectionRequest.Builder connectionRequestBuilder =
                setupConnectionRequestBuilderBaseConfiguration(configuration);
        connectionRequestBuilder.setClusterModeEnabled(true);

        return connectionRequestBuilder;
    }

    /**
     * Look up for java ReadFrom enum to protobuf defined ReadFrom enum.
     *
     * @param readFrom
     * @return Protobuf defined ReadFrom enum
     */
    private ConnectionRequestOuterClass.ReadFrom mapReadFromEnum(ReadFrom readFrom) {
        if (readFrom == ReadFrom.PREFER_REPLICA) {
            return ConnectionRequestOuterClass.ReadFrom.PreferReplica;
        }

        return ConnectionRequestOuterClass.ReadFrom.Primary;
    }

    /** Check a response received from Glide. */
    private Void checkGlideRsResponse(Response response) {
        // Note: errors are already handled before in CallbackDispatcher, but we double-check
        if (response.hasRequestError()) {
            throwClosingError(
                    "Unhandled request error in response: " + response.getRequestError().getMessage());
        }
        if (response.hasClosingError()) {
            throwClosingError("Unhandled closing error in response: " + response.getClosingError());
        }

        if (response.hasRespPointer()) {
            throwClosingError("Unexpected data in response");
        }
        if (!response.hasConstantResponse()) {
            throwClosingError("Unexpected empty data in response");
        }
        // Expect a constant "OK" response and return Void/null
        return null;
    }

    private void throwClosingError(String msg) {
        closeConnection();
        throw new ClosingException(msg);
    }

    /**
     * Close the connection to the channel.
     *
     * @return a CompletableFuture to indicate the channel is closed
     */
    public Future<Void> closeConnection() {
        return channel.close();
    }
}
