/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.ffi.resolvers.SocketListenerResolver.getSocket;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static redis_request.RedisRequestOuterClass.RequestType.CustomCommand;
import static response.ResponseOuterClass.RequestErrorType.Disconnect;
import static response.ResponseOuterClass.RequestErrorType.ExecAbort;
import static response.ResponseOuterClass.RequestErrorType.Timeout;
import static response.ResponseOuterClass.RequestErrorType.Unspecified;

import connection_request.ConnectionRequestOuterClass;
import glide.api.models.configuration.RedisClientConfiguration;
import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ConnectionException;
import glide.api.models.exceptions.ExecAbortException;
import glide.api.models.exceptions.RedisException;
import glide.api.models.exceptions.RequestException;
import glide.api.models.exceptions.TimeoutException;
import glide.connectors.handlers.CallbackDispatcher;
import glide.connectors.handlers.ChannelHandler;
import glide.connectors.resources.Platform;
import glide.connectors.resources.ThreadPoolResourceAllocator;
import glide.managers.BaseResponseResolver;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import io.netty.channel.ChannelFuture;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import redis_request.RedisRequestOuterClass.RedisRequest;
import response.ResponseOuterClass.RequestError;
import response.ResponseOuterClass.RequestErrorType;
import response.ResponseOuterClass.Response;

public class ExceptionHandlingTests {
    @BeforeEach
    public void init() {
        var threadPoolResource = ThreadPoolResourceAllocator.getOrCreate(() -> null);
        if (threadPoolResource != null) {
            threadPoolResource.getEventLoopGroup().shutdownGracefully();
            ThreadPoolResourceAllocator.getOrCreate(() -> null);
        }
    }

    /**
     * This test shows how exception handling works in the middle of future pipeline The client has
     * similar stuff, but it rethrows an exception.
     */
    @SneakyThrows
    @Test
    public void verify_future_pipeline_abortion() {
        var future = new CompletableFuture<Boolean>();
        var future2 = future.exceptionally(e -> false).thenApplyAsync(r -> r ? 42 : 100500);

        future.completeExceptionally(new IllegalArgumentException());

        assertEquals(100500, future2.get());
    }

    @Test
    @SneakyThrows
    public void channel_is_closed_when_failed_to_connect() {
        // init stuff like client does
        var callbackDispatcher = new TestCallbackDispatcher(new ClosingException("TEST"));
        var channelHandler = new TestChannelHandler(callbackDispatcher);
        var connectionManager = new ConnectionManager(channelHandler);
        var future = connectionManager.connectToRedis(createDummyConfig());

        callbackDispatcher.completeRequest(null);
        var exception = assertThrows(ExecutionException.class, future::get);
        // a ClosingException thrown from CallbackDispatcher::completeRequest and then
        // rethrown by ConnectionManager::exceptionHandler
        assertTrue(exception.getCause() instanceof ClosingException);
        assertTrue(channelHandler.wasClosed);
    }

    @Test
    @SneakyThrows
    public void channel_is_closed_when_disconnected_on_command() {
        var callbackDispatcher = new TestCallbackDispatcher(new ClosingException("TEST"));
        var channelHandler = new TestChannelHandler(callbackDispatcher);
        var commandManager = new CommandManager(channelHandler);

        var future = commandManager.submitNewCommand(CustomCommand, new String[0], r -> null);
        callbackDispatcher.completeRequest(null);
        var exception = assertThrows(ExecutionException.class, future::get);
        // a ClosingException thrown from CallbackDispatcher::completeRequest and then
        // rethrown by CommandManager::exceptionHandler
        assertTrue(exception.getCause() instanceof ClosingException);
        // check the channel
        assertTrue(channelHandler.wasClosed);
    }

    @Test
    @SneakyThrows
    public void channel_is_not_closed_when_error_was_in_command_pipeline() {
        var callbackDispatcher = new TestCallbackDispatcher(new RequestException("TEST"));
        var channelHandler = new TestChannelHandler(callbackDispatcher);
        var commandManager = new CommandManager(channelHandler);

        var future = commandManager.submitNewCommand(CustomCommand, new String[0], r -> null);
        callbackDispatcher.completeRequest(null);
        var exception = assertThrows(ExecutionException.class, future::get);
        // a RequestException thrown from CallbackDispatcher::completeRequest and then
        // rethrown by CommandManager::exceptionHandler
        assertTrue(exception.getCause() instanceof RequestException);
        // check the channel
        assertFalse(channelHandler.wasClosed);
    }

    @Test
    @SneakyThrows
    public void command_manager_rethrows_non_RedisException_too() {
        var callbackDispatcher = new TestCallbackDispatcher(new IOException("TEST"));
        var channelHandler = new TestChannelHandler(callbackDispatcher);
        var commandManager = new CommandManager(channelHandler);

        var future = commandManager.submitNewCommand(CustomCommand, new String[0], r -> null);
        callbackDispatcher.completeRequest(null);
        var exception = assertThrows(ExecutionException.class, future::get);
        // a IOException thrown from CallbackDispatcher::completeRequest and then wrapped
        // by a RuntimeException and rethrown by CommandManager::exceptionHandler
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertTrue(exception.getCause().getCause() instanceof IOException);
        // check the channel
        assertFalse(channelHandler.wasClosed);
    }

    @Test
    @SneakyThrows
    public void connection_manager_rethrows_non_RedisException_too() {
        var callbackDispatcher = new TestCallbackDispatcher(new IOException("TEST"));
        var channelHandler = new TestChannelHandler(callbackDispatcher);
        var connectionManager = new ConnectionManager(channelHandler);

        var future = connectionManager.connectToRedis(createDummyConfig());
        callbackDispatcher.completeRequest(null);

        var exception = assertThrows(ExecutionException.class, future::get);
        // a IOException thrown from CallbackDispatcher::completeRequest and then wrapped
        // by a RuntimeException and rethrown by ConnectionManager::exceptionHandler
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertTrue(exception.getCause().getCause() instanceof IOException);
        // check the channel
        assertTrue(channelHandler.wasClosed);
    }

    @Test
    @SneakyThrows
    public void close_connection_on_response_with_closing_error() {
        // CallbackDispatcher throws ClosingException which causes ConnectionManager and CommandManager
        // to close the channel
        var callbackDispatcher = new CallbackDispatcher(null);
        var channelHandler = new TestChannelHandler(callbackDispatcher);
        var connectionManager = new ConnectionManager(channelHandler);

        var future1 = connectionManager.connectToRedis(createDummyConfig());
        var future2 = connectionManager.connectToRedis(createDummyConfig());
        var response = Response.newBuilder().setClosingError("TEST").build();
        callbackDispatcher.completeRequest(response);

        var exception = assertThrows(ExecutionException.class, future1::get);
        // a ClosingException thrown from CallbackDispatcher::completeRequest and then
        // rethrown by CommandManager::exceptionHandler
        assertTrue(exception.getCause() instanceof ClosingException);
        // check the channel
        assertTrue(channelHandler.wasClosed);

        // all pending requests should be aborted once ClosingError received in callback dispatcher
        exception = assertThrows(ExecutionException.class, future2::get);
        // or could be cancelled in CallbackDispatcher::shutdownGracefully
        // cancellation overwrites previous status, so we may not get ClosingException due to a race
        assertTrue(
                exception.getCause() instanceof ClosingException
                        || exception.getCause() instanceof CancellationException);
    }

    private static Stream<Arguments> getProtobufErrorsToJavaClientErrorsMapping() {
        return Stream.of(
                Arguments.of(Unspecified, RequestException.class),
                Arguments.of(ExecAbort, ExecAbortException.class),
                Arguments.of(Timeout, TimeoutException.class),
                Arguments.of(Disconnect, ConnectionException.class)
                // can't test default branch in switch-case
                );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("getProtobufErrorsToJavaClientErrorsMapping")
    @SneakyThrows
    public void dont_close_connection_when_callback_dispatcher_receives_response_with_closing_error(
            // CallbackDispatcher throws a corresponding exception which should not cause
            // ConnectionManager and CommandManager to close the channel
            RequestErrorType errorType, Class<? extends RedisException> exceptionType) {
        var callbackDispatcher = new CallbackDispatcher(null);
        var channelHandler = new TestChannelHandler(callbackDispatcher);
        var commandManager = new CommandManager(channelHandler);

        var future = commandManager.submitNewCommand(CustomCommand, new String[0], r -> null);
        var response =
                Response.newBuilder()
                        .setCallbackIdx(0)
                        .setRequestError(RequestError.newBuilder().setType(errorType).setMessage("TEST"))
                        .build();
        callbackDispatcher.completeRequest(response);

        var exception = assertThrows(ExecutionException.class, future::get);
        // a RedisException thrown from CallbackDispatcher::completeRequest and then
        // rethrown by CommandManager::exceptionHandler
        assertEquals(exceptionType, exception.getCause().getClass());
        assertEquals("TEST", exception.getCause().getMessage());
        // check the channel
        assertFalse(channelHandler.wasClosed);
    }

    @Test
    @SneakyThrows
    public void close_connection_on_response_without_error_but_with_incorrect_callback_id() {
        // CallbackDispatcher does the same as it received closing error
        var callbackDispatcher = new CallbackDispatcher(null);
        var channelHandler = new TestChannelHandler(callbackDispatcher);
        var connectionManager = new ConnectionManager(channelHandler);

        var future1 = connectionManager.connectToRedis(createDummyConfig());
        var future2 = connectionManager.connectToRedis(createDummyConfig());

        var response = Response.newBuilder().setCallbackIdx(42).build();
        callbackDispatcher.completeRequest(response);

        var exception = assertThrows(ExecutionException.class, future1::get);
        // a ClosingException thrown from CallbackDispatcher::completeRequest and then
        // rethrown by CommandManager::exceptionHandler
        assertTrue(exception.getCause() instanceof ClosingException);
        assertEquals(
                exception.getCause().getMessage(), "Client is in an erroneous state and should close");
        // check the channel
        assertTrue(channelHandler.wasClosed);

        // all pending requests should be aborted once ClosingError received in callback dispatcher
        exception = assertThrows(ExecutionException.class, future2::get);
        // or could be cancelled in CallbackDispatcher::shutdownGracefully
        // cancellation overwrites previous status, so we may not get ClosingException due to a race
        assertTrue(
                exception.getCause() instanceof ClosingException
                        || exception.getCause() instanceof CancellationException);
    }

    @Test
    public void response_resolver_does_not_expect_errors() {
        var resolver = new BaseResponseResolver(null);

        var response1 =
                Response.newBuilder()
                        .setRequestError(RequestError.newBuilder().setType(ExecAbort).setMessage("TEST"))
                        .build();
        var exception = assertThrows(Throwable.class, () -> resolver.apply(response1));
        assertEquals("Unhandled response request error", exception.getMessage());

        var response2 = Response.newBuilder().setClosingError("TEST").build();
        exception = assertThrows(Throwable.class, () -> resolver.apply(response2));
        assertEquals("Unhandled response closing error", exception.getMessage());
    }

    // TODO add tests for error handling in MessageHandler

    /** Create a config which causes connection failure. */
    private static RedisClientConfiguration createDummyConfig() {
        return RedisClientConfiguration.builder().build();
    }

    /** Test ChannelHandler extension which allows to validate whether the channel was closed. */
    private static class TestChannelHandler extends ChannelHandler {

        public TestChannelHandler(CallbackDispatcher callbackDispatcher) throws InterruptedException {
            super(
                    callbackDispatcher,
                    getSocket(),
                    ThreadPoolResourceAllocator.getOrCreate(Platform.getThreadPoolResourceSupplier()));
        }

        public boolean wasClosed = false;

        @Override
        public ChannelFuture close() {
            wasClosed = true;
            return super.close();
        }

        @Override
        public CompletableFuture<Response> write(RedisRequest.Builder request, boolean flush) {
            var commandId = callbackDispatcher.registerRequest();
            return commandId.getValue();
        }

        @Override
        public CompletableFuture<Response> connect(
                ConnectionRequestOuterClass.ConnectionRequest request) {
            return callbackDispatcher.registerConnection();
        }
    }

    /** Test ChannelHandler extension which aborts futures for all commands. */
    private static class TestCallbackDispatcher extends CallbackDispatcher {

        public final Throwable exceptionToThrow;

        private TestCallbackDispatcher(Throwable exceptionToThrow) {
            super(null);
            this.exceptionToThrow = exceptionToThrow;
        }

        @Override
        public void completeRequest(Response response) {
            responses.values().forEach(future -> future.completeExceptionally(exceptionToThrow));
        }
    }
}
