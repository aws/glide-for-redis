/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import static glide.api.models.configuration.NodeAddress.DEFAULT_HOST;
import static glide.api.models.configuration.NodeAddress.DEFAULT_PORT;
import static glide.api.models.configuration.StandaloneSubscriptionConfiguration.PubSubChannelMode.EXACT;
import static glide.api.models.configuration.StandaloneSubscriptionConfiguration.PubSubChannelMode.PATTERN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import connection_request.ConnectionRequestOuterClass;
import connection_request.ConnectionRequestOuterClass.AuthenticationInfo;
import connection_request.ConnectionRequestOuterClass.ConnectionRequest;
import connection_request.ConnectionRequestOuterClass.ConnectionRetryStrategy;
import connection_request.ConnectionRequestOuterClass.PubSubChannelsOrPatterns;
import connection_request.ConnectionRequestOuterClass.PubSubSubscriptions;
import connection_request.ConnectionRequestOuterClass.TlsMode;
import glide.api.models.configuration.BackoffStrategy;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ReadFrom;
import glide.api.models.configuration.RedisClientConfiguration;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import glide.api.models.configuration.RedisCredentials;
import glide.api.models.configuration.StandaloneSubscriptionConfiguration;
import glide.api.models.exceptions.ClosingException;
import glide.connectors.handlers.ChannelHandler;
import io.netty.channel.ChannelFuture;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import response.ResponseOuterClass.ConstantResponse;
import response.ResponseOuterClass.Response;

public class ConnectionManagerTest {
    ConnectionManager connectionManager;

    ChannelHandler channel;

    private static final String HOST = "aws.com";
    private static final int PORT = 9999;

    private static final String USERNAME = "JohnDoe";
    private static final String PASSWORD = "Password1";

    private static final int NUM_OF_RETRIES = 5;
    private static final int FACTOR = 10;
    private static final int EXPONENT_BASE = 50;

    private static final int DATABASE_ID = 1;

    private static final int REQUEST_TIMEOUT = 3;

    private static final String CLIENT_NAME = "ClientName";

    @BeforeEach
    public void setUp() {
        channel = mock(ChannelHandler.class);
        ChannelFuture closeFuture = mock(ChannelFuture.class);
        when(closeFuture.syncUninterruptibly()).thenReturn(closeFuture);
        when(channel.close()).thenReturn(closeFuture);
        connectionManager = new ConnectionManager(channel);
    }

    @SneakyThrows
    @Test
    public void connection_request_protobuf_generation_default_standalone_configuration() {
        // setup
        RedisClientConfiguration redisClientConfiguration = RedisClientConfiguration.builder().build();
        ConnectionRequest expectedProtobufConnectionRequest =
                ConnectionRequest.newBuilder()
                        .setTlsMode(TlsMode.NoTls)
                        .setClusterModeEnabled(false)
                        .setReadFrom(ConnectionRequestOuterClass.ReadFrom.Primary)
                        .build();
        CompletableFuture<Response> completedFuture = new CompletableFuture<>();
        Response response = Response.newBuilder().setConstantResponse(ConstantResponse.OK).build();
        completedFuture.complete(response);

        // execute
        when(channel.connect(eq(expectedProtobufConnectionRequest))).thenReturn(completedFuture);
        CompletableFuture<Void> result = connectionManager.connectToRedis(redisClientConfiguration);

        // verify
        // no exception
        assertNull(result.get());
    }

    @SneakyThrows
    @Test
    public void connection_request_protobuf_generation_default_cluster_configuration() {
        // setup
        RedisClusterClientConfiguration redisClusterClientConfiguration =
                RedisClusterClientConfiguration.builder().build();
        ConnectionRequest expectedProtobufConnectionRequest =
                ConnectionRequest.newBuilder()
                        .setTlsMode(TlsMode.NoTls)
                        .setClusterModeEnabled(true)
                        .setReadFrom(ConnectionRequestOuterClass.ReadFrom.Primary)
                        .build();
        CompletableFuture<Response> completedFuture = new CompletableFuture<>();
        Response response = Response.newBuilder().setConstantResponse(ConstantResponse.OK).build();
        completedFuture.complete(response);

        // execute
        when(channel.connect(eq(expectedProtobufConnectionRequest))).thenReturn(completedFuture);
        CompletableFuture<Void> result =
                connectionManager.connectToRedis(redisClusterClientConfiguration);

        // verify
        assertNull(result.get());
        verify(channel).connect(eq(expectedProtobufConnectionRequest));
    }

    @SneakyThrows
    @Test
    public void connection_request_protobuf_generation_with_all_fields_set() {
        // setup
        RedisClientConfiguration redisClientConfiguration =
                RedisClientConfiguration.builder()
                        .address(NodeAddress.builder().host(HOST).port(PORT).build())
                        .address(NodeAddress.builder().host(DEFAULT_HOST).port(DEFAULT_PORT).build())
                        .useTLS(true)
                        .readFrom(ReadFrom.PREFER_REPLICA)
                        .credentials(RedisCredentials.builder().username(USERNAME).password(PASSWORD).build())
                        .requestTimeout(REQUEST_TIMEOUT)
                        .reconnectStrategy(
                                BackoffStrategy.builder()
                                        .numOfRetries(NUM_OF_RETRIES)
                                        .exponentBase(EXPONENT_BASE)
                                        .factor(FACTOR)
                                        .build())
                        .databaseId(DATABASE_ID)
                        .clientName(CLIENT_NAME)
                        .subscriptionConfiguration(
                                StandaloneSubscriptionConfiguration.builder()
                                        .subscription(EXACT, "channel_1")
                                        .subscription(EXACT, "channel_2")
                                        .subscription(PATTERN, "*chatRoom*")
                                        .build())
                        .build();
        ConnectionRequest expectedProtobufConnectionRequest =
                ConnectionRequest.newBuilder()
                        .addAddresses(
                                ConnectionRequestOuterClass.NodeAddress.newBuilder()
                                        .setHost(HOST)
                                        .setPort(PORT)
                                        .build())
                        .addAddresses(
                                ConnectionRequestOuterClass.NodeAddress.newBuilder()
                                        .setHost(DEFAULT_HOST)
                                        .setPort(DEFAULT_PORT)
                                        .build())
                        .setTlsMode(TlsMode.SecureTls)
                        .setReadFrom(ConnectionRequestOuterClass.ReadFrom.PreferReplica)
                        .setClusterModeEnabled(false)
                        .setAuthenticationInfo(
                                AuthenticationInfo.newBuilder().setUsername(USERNAME).setPassword(PASSWORD).build())
                        .setRequestTimeout(REQUEST_TIMEOUT)
                        .setConnectionRetryStrategy(
                                ConnectionRetryStrategy.newBuilder()
                                        .setNumberOfRetries(NUM_OF_RETRIES)
                                        .setFactor(FACTOR)
                                        .setExponentBase(EXPONENT_BASE)
                                        .build())
                        .setDatabaseId(DATABASE_ID)
                        .setClientName(CLIENT_NAME)
                        .setPubsubSubscriptions(
                                PubSubSubscriptions.newBuilder()
                                        .putAllChannelsOrPatternsByType(
                                                Map.of(
                                                        EXACT.ordinal(),
                                                                PubSubChannelsOrPatterns.newBuilder()
                                                                        .addChannelsOrPatterns(ByteString.copyFromUtf8("channel_1"))
                                                                        .addChannelsOrPatterns(ByteString.copyFromUtf8("channel_2"))
                                                                        .build(),
                                                        PATTERN.ordinal(),
                                                                PubSubChannelsOrPatterns.newBuilder()
                                                                        .addChannelsOrPatterns(ByteString.copyFromUtf8("*chatRoom*"))
                                                                        .build()))
                                        .build())
                        .build();
        CompletableFuture<Response> completedFuture = new CompletableFuture<>();
        Response response = Response.newBuilder().setConstantResponse(ConstantResponse.OK).build();
        completedFuture.complete(response);

        // execute
        when(channel.connect(eq(expectedProtobufConnectionRequest))).thenReturn(completedFuture);
        CompletableFuture<Void> result = connectionManager.connectToRedis(redisClientConfiguration);

        // verify
        assertNull(result.get());
        verify(channel).connect(eq(expectedProtobufConnectionRequest));
    }

    @SneakyThrows
    @Test
    public void response_validation_on_constant_response_returns_successfully() {
        // setup
        RedisClientConfiguration redisClientConfiguration = RedisClientConfiguration.builder().build();
        CompletableFuture<Response> completedFuture = new CompletableFuture<>();
        Response response = Response.newBuilder().setConstantResponse(ConstantResponse.OK).build();
        completedFuture.complete(response);

        // execute
        when(channel.connect(any())).thenReturn(completedFuture);
        CompletableFuture<Void> result = connectionManager.connectToRedis(redisClientConfiguration);

        // verify
        assertNull(result.get());
        verify(channel).connect(any());
    }

    @Test
    public void connection_on_empty_response_throws_ClosingException() {
        // setup
        RedisClientConfiguration redisClientConfiguration = RedisClientConfiguration.builder().build();
        CompletableFuture<Response> completedFuture = new CompletableFuture<>();
        Response response = Response.newBuilder().build();
        completedFuture.complete(response);

        // execute
        when(channel.connect(any())).thenReturn(completedFuture);
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> connectionManager.connectToRedis(redisClientConfiguration).get());

        assertTrue(executionException.getCause() instanceof ClosingException);
        assertEquals("Unexpected empty data in response", executionException.getCause().getMessage());
        verify(channel).close();
    }

    @Test
    public void connection_on_resp_pointer_throws_ClosingException() {
        // setup
        RedisClientConfiguration redisClientConfiguration = RedisClientConfiguration.builder().build();
        CompletableFuture<Response> completedFuture = new CompletableFuture<>();
        Response response = Response.newBuilder().setRespPointer(42).build();
        completedFuture.complete(response);

        // execute
        when(channel.connect(any())).thenReturn(completedFuture);
        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> connectionManager.connectToRedis(redisClientConfiguration).get());

        assertTrue(executionException.getCause() instanceof ClosingException);
        assertEquals("Unexpected data in response", executionException.getCause().getMessage());
        verify(channel).close();
    }
}
