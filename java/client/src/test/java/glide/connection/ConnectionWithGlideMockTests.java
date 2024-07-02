/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.connection;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import connection_request.ConnectionRequestOuterClass.ConnectionRequest;
import connection_request.ConnectionRequestOuterClass.NodeAddress;
import glide.api.RedisClient;
import glide.api.logging.Logger;
import glide.api.models.exceptions.ClosingException;
import glide.connectors.handlers.CallbackDispatcher;
import glide.connectors.handlers.ChannelHandler;
import glide.connectors.resources.Platform;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import glide.utils.RustCoreLibMockTestBase;
import glide.utils.RustCoreMock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis_request.RedisRequestOuterClass.RedisRequest;
import response.ResponseOuterClass.Response;

public class ConnectionWithGlideMockTests extends RustCoreLibMockTestBase {

    private ChannelHandler channelHandler = null;

    @BeforeEach
    @SneakyThrows
    public void createTestClient() {
        // TODO: Add DISABLED level to logger-core
        Logger.setLoggerConfig(Logger.Level.DISABLED);
        channelHandler =
                new ChannelHandler(
                        new CallbackDispatcher(null),
                        socketPath,
                        Platform.getThreadPoolResourceSupplier().get());
    }

    @AfterEach
    public void closeTestClient() {
        channelHandler.close();
    }

    private Future<Response> testConnection() {
        return channelHandler.connect(createConnectionRequest());
    }

    private static ConnectionRequest createConnectionRequest() {
        return ConnectionRequest.newBuilder()
                .addAddresses(NodeAddress.newBuilder().setHost("dummyhost").setPort(42).build())
                .build();
    }

    @BeforeAll
    public static void init() {
        startRustCoreLibMock(null);
    }

    @Test
    @SneakyThrows
    // as of #710 https://github.com/aws/babushka/pull/710 - connection response is empty
    public void can_connect_with_empty_response() {
        RustCoreMock.updateGlideMock(
                new RustCoreMock.GlideMockProtobuf() {
                    @Override
                    public Response connection(ConnectionRequest request) {
                        return Response.newBuilder().build();
                    }

                    @Override
                    public Response.Builder redisRequest(RedisRequest request) {
                        return null;
                    }
                });

        var connectionResponse = testConnection().get();
        assertAll(
                () -> assertFalse(connectionResponse.hasClosingError()),
                () -> assertFalse(connectionResponse.hasRequestError()),
                () -> assertFalse(connectionResponse.hasRespPointer()));
    }

    @Test
    @SneakyThrows
    public void can_connect_with_ok_response() {
        RustCoreMock.updateGlideMock(
                new RustCoreMock.GlideMockProtobuf() {
                    @Override
                    public Response connection(ConnectionRequest request) {
                        return OK().build();
                    }

                    @Override
                    public Response.Builder redisRequest(RedisRequest request) {
                        return null;
                    }
                });

        var connectionResponse = testConnection().get();
        assertAll(
                () -> assertTrue(connectionResponse.hasConstantResponse()),
                () -> assertFalse(connectionResponse.hasClosingError()),
                () -> assertFalse(connectionResponse.hasRequestError()),
                () -> assertFalse(connectionResponse.hasRespPointer()));
    }

    @Test
    public void cant_connect_when_no_response() {
        RustCoreMock.updateGlideMock(
                new RustCoreMock.GlideMockProtobuf() {
                    @Override
                    public Response connection(ConnectionRequest request) {
                        return null;
                    }

                    @Override
                    public Response.Builder redisRequest(RedisRequest request) {
                        return null;
                    }
                });

        assertThrows(TimeoutException.class, () -> testConnection().get(1, SECONDS));
    }

    @Test
    @SneakyThrows
    public void cant_connect_when_negative_response() {
        RustCoreMock.updateGlideMock(
                new RustCoreMock.GlideMockProtobuf() {
                    @Override
                    public Response connection(ConnectionRequest request) {
                        return Response.newBuilder().setClosingError("You shall not pass!").build();
                    }

                    @Override
                    public Response.Builder redisRequest(RedisRequest request) {
                        return null;
                    }
                });

        var exception = assertThrows(ExecutionException.class, () -> testConnection().get(1, SECONDS));
        assertAll(
                () -> assertTrue(exception.getCause() instanceof ClosingException),
                () -> assertEquals("You shall not pass!", exception.getCause().getMessage()));
    }

    @Test
    @SneakyThrows
    public void rethrow_error_on_read_when_malformed_packet_received() {
        RustCoreMock.updateGlideMock(request -> new byte[] {-1});

        var exception = assertThrows(ExecutionException.class, () -> testConnection().get(1, SECONDS));
        assertAll(
                () -> assertTrue(exception.getCause() instanceof ClosingException),
                () ->
                        assertTrue(
                                exception
                                        .getCause()
                                        .getMessage()
                                        .contains("An unhandled error while reading from UDS channel")));
    }

    @Test
    @SneakyThrows
    public void rethrow_error_if_UDS_channel_closed() {
        try (var client = new TestClient(channelHandler)) {
            stopRustCoreLibMock();
            try {
                var exception =
                        assertThrows(ExecutionException.class, () -> client.customCommand(new String[0]).get());
                assertTrue(exception.getCause() instanceof ClosingException);
            } finally {
                // restart mock to let other tests pass if this one failed
                startRustCoreLibMock(null);
            }
        }
    }

    private static class TestClient extends RedisClient {

        public TestClient(ChannelHandler channelHandler) {
            super(
                    new ClientBuilder(
                            new ConnectionManager(channelHandler),
                            new CommandManager(channelHandler),
                            null,
                            null));
        }
    }
}
