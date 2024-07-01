/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_NODES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_PRIMARIES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute.RANDOM;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static redis_request.RedisRequestOuterClass.RequestType.CustomCommand;

import com.google.protobuf.ByteString;
import glide.api.models.ClusterTransaction;
import glide.api.models.Transaction;
import glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotIdRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotKeyRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotType;
import glide.api.models.exceptions.RequestException;
import glide.connectors.handlers.ChannelHandler;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import redis_request.RedisRequestOuterClass.RedisRequest;
import redis_request.RedisRequestOuterClass.SimpleRoutes;
import redis_request.RedisRequestOuterClass.SlotTypes;
import response.ResponseOuterClass.Response;

public class CommandManagerTest {

    ChannelHandler channelHandler;

    CommandManager service;

    @BeforeEach
    void init() {
        channelHandler = mock(ChannelHandler.class);
        service = new CommandManager(channelHandler);
    }

    @Test
    @SneakyThrows
    public void submitNewCommand_return_Object_result() {

        // setup
        long pointer = -1;
        Response respPointerResponse = Response.newBuilder().setRespPointer(pointer).build();
        Object respObject = mock(Object.class);

        CompletableFuture<Response> future = new CompletableFuture<>();
        future.complete(respPointerResponse);
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);
        when(channelHandler.isClosed()).thenReturn(false);

        // exercise
        CompletableFuture<Object> result =
                service.submitNewCommand(
                        CustomCommand,
                        new String[0],
                        new BaseResponseResolver((ptr) -> ptr == pointer ? respObject : null));
        Object respPointer = result.get();

        // verify
        assertEquals(respObject, respPointer);
    }

    @Test
    @SneakyThrows
    public void submitNewCommand_return_Null_result() {
        // setup
        Response respPointerResponse = Response.newBuilder().build();
        CompletableFuture<Response> future = new CompletableFuture<>();
        future.complete(respPointerResponse);
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);
        when(channelHandler.isClosed()).thenReturn(false);

        // exercise
        CompletableFuture<Object> result =
                service.submitNewCommand(
                        CustomCommand,
                        new String[0],
                        new BaseResponseResolver(
                                (p) ->
                                        new RuntimeException("Testing: something went wrong if you see this error")));
        Object respPointer = result.get();

        // verify
        assertNull(respPointer);
    }

    @Test
    @SneakyThrows
    public void submitNewCommand_return_String_result() {

        // setup
        long pointer = 123;
        String testString = "TEST STRING";

        Response respPointerResponse = Response.newBuilder().setRespPointer(pointer).build();

        CompletableFuture<Response> future = new CompletableFuture<>();
        future.complete(respPointerResponse);
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);
        when(channelHandler.isClosed()).thenReturn(false);

        // exercise
        CompletableFuture<Object> result =
                service.submitNewCommand(
                        CustomCommand,
                        new String[0],
                        new BaseResponseResolver((p) -> p == pointer ? testString : null));
        Object respPointer = result.get();

        // verify
        assertTrue(respPointer instanceof String);
        assertEquals(testString, respPointer);
    }

    public static Stream<Arguments> getEnumRoutes() {
        return Stream.of(Arguments.of(RANDOM), Arguments.of(ALL_NODES), Arguments.of(ALL_PRIMARIES));
    }

    @ParameterizedTest
    @MethodSource("getEnumRoutes")
    public void prepare_request_with_simple_routes(Route routeType) {
        CompletableFuture<Response> future = new CompletableFuture<>();
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);
        when(channelHandler.isClosed()).thenReturn(false);

        ArgumentCaptor<RedisRequest.Builder> captor =
                ArgumentCaptor.forClass(RedisRequest.Builder.class);

        service.submitNewCommand(CustomCommand, new String[0], routeType, r -> null);
        verify(channelHandler).write(captor.capture(), anyBoolean());
        var requestBuilder = captor.getValue();

        var protobufToClientRouteMapping =
                Map.of(
                        SimpleRoutes.AllNodes, ALL_NODES,
                        SimpleRoutes.AllPrimaries, ALL_PRIMARIES,
                        SimpleRoutes.Random, RANDOM);

        assertAll(
                () -> assertTrue(requestBuilder.hasRoute()),
                () -> assertTrue(requestBuilder.getRoute().hasSimpleRoutes()),
                () ->
                        assertEquals(
                                routeType,
                                protobufToClientRouteMapping.get(requestBuilder.getRoute().getSimpleRoutes())),
                () -> assertFalse(requestBuilder.getRoute().hasSlotIdRoute()),
                () -> assertFalse(requestBuilder.getRoute().hasSlotKeyRoute()));
    }

    @ParameterizedTest
    @EnumSource(value = SlotType.class)
    public void prepare_request_with_slot_id_routes(SlotType slotType) {
        CompletableFuture<Response> future = new CompletableFuture<>();
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);
        when(channelHandler.isClosed()).thenReturn(false);

        ArgumentCaptor<RedisRequest.Builder> captor =
                ArgumentCaptor.forClass(RedisRequest.Builder.class);

        service.submitNewCommand(
                CustomCommand, new String[0], new SlotIdRoute(42, slotType), r -> null);
        verify(channelHandler).write(captor.capture(), anyBoolean());
        var requestBuilder = captor.getValue();

        var protobufToClientRouteMapping =
                Map.of(
                        SlotTypes.Primary, SlotType.PRIMARY,
                        SlotTypes.Replica, SlotType.REPLICA);

        assertAll(
                () -> assertTrue(requestBuilder.hasRoute()),
                () -> assertTrue(requestBuilder.getRoute().hasSlotIdRoute()),
                () ->
                        assertEquals(
                                slotType,
                                protobufToClientRouteMapping.get(
                                        requestBuilder.getRoute().getSlotIdRoute().getSlotType())),
                () -> assertEquals(42, requestBuilder.getRoute().getSlotIdRoute().getSlotId()),
                () -> assertFalse(requestBuilder.getRoute().hasSimpleRoutes()),
                () -> assertFalse(requestBuilder.getRoute().hasSlotKeyRoute()));
    }

    @ParameterizedTest
    @EnumSource(value = SlotType.class)
    public void prepare_request_with_slot_key_routes(SlotType slotType) {
        CompletableFuture<Response> future = new CompletableFuture<>();
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);
        when(channelHandler.isClosed()).thenReturn(false);

        ArgumentCaptor<RedisRequest.Builder> captor =
                ArgumentCaptor.forClass(RedisRequest.Builder.class);

        service.submitNewCommand(
                CustomCommand, new String[0], new SlotKeyRoute("TEST", slotType), r -> null);
        verify(channelHandler).write(captor.capture(), anyBoolean());
        var requestBuilder = captor.getValue();

        var protobufToClientRouteMapping =
                Map.of(
                        SlotTypes.Primary, SlotType.PRIMARY,
                        SlotTypes.Replica, SlotType.REPLICA);

        assertAll(
                () -> assertTrue(requestBuilder.hasRoute()),
                () -> assertTrue(requestBuilder.getRoute().hasSlotKeyRoute()),
                () ->
                        assertEquals(
                                slotType,
                                protobufToClientRouteMapping.get(
                                        requestBuilder.getRoute().getSlotKeyRoute().getSlotType())),
                () -> assertEquals("TEST", requestBuilder.getRoute().getSlotKeyRoute().getSlotKey()),
                () -> assertFalse(requestBuilder.getRoute().hasSimpleRoutes()),
                () -> assertFalse(requestBuilder.getRoute().hasSlotIdRoute()));
    }

    @Test
    public void prepare_request_with_by_address_route() {
        CompletableFuture<Response> future = new CompletableFuture<>();
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);
        when(channelHandler.isClosed()).thenReturn(false);

        ArgumentCaptor<RedisRequest.Builder> captor =
                ArgumentCaptor.forClass(RedisRequest.Builder.class);

        service.submitNewCommand(
                CustomCommand, new String[0], new ByAddressRoute("testhost", 6379), r -> null);
        verify(channelHandler).write(captor.capture(), anyBoolean());
        var requestBuilder = captor.getValue();

        assertAll(
                () -> assertTrue(requestBuilder.hasRoute()),
                () -> assertTrue(requestBuilder.getRoute().hasByAddressRoute()),
                () -> assertEquals("testhost", requestBuilder.getRoute().getByAddressRoute().getHost()),
                () -> assertEquals(6379, requestBuilder.getRoute().getByAddressRoute().getPort()),
                () -> assertFalse(requestBuilder.getRoute().hasSimpleRoutes()),
                () -> assertFalse(requestBuilder.getRoute().hasSlotIdRoute()),
                () -> assertFalse(requestBuilder.getRoute().hasSlotKeyRoute()));
    }

    @Test
    public void prepare_request_with_unknown_route_type() {
        CompletableFuture<Response> future = new CompletableFuture<>();
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);

        var exception =
                assertThrows(
                        RequestException.class,
                        () ->
                                service.submitNewCommand(CustomCommand, new String[0], new Route() {}, r -> null));
        assertTrue(exception.getMessage().startsWith("Unknown type of route"));
    }

    @SneakyThrows
    @Test
    public void submitNewCommand_with_Transaction_sends_protobuf_request() {
        // setup
        String[] arg1 = new String[] {"GETSTRING", "one"};
        String[] arg2 = new String[] {"GETSTRING", "two"};
        String[] arg3 = new String[] {"GETSTRING", "three"};
        Transaction trans = new Transaction();
        trans.customCommand(arg1).customCommand(arg2).customCommand(arg3);

        CompletableFuture<Response> future = new CompletableFuture<>();
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);
        when(channelHandler.isClosed()).thenReturn(false);

        ArgumentCaptor<RedisRequest.Builder> captor =
                ArgumentCaptor.forClass(RedisRequest.Builder.class);

        // exercise
        service.submitNewTransaction(trans, r -> null);

        // verify
        verify(channelHandler).write(captor.capture(), anyBoolean());
        var requestBuilder = captor.getValue();

        // verify
        assertTrue(requestBuilder.hasTransaction());
        assertEquals(3, requestBuilder.getTransaction().getCommandsCount());

        LinkedList<ByteString> resultPayloads = new LinkedList<>();
        resultPayloads.add(ByteString.copyFromUtf8("one"));
        resultPayloads.add(ByteString.copyFromUtf8("two"));
        resultPayloads.add(ByteString.copyFromUtf8("three"));
        for (redis_request.RedisRequestOuterClass.Command command :
                requestBuilder.getTransaction().getCommandsList()) {
            assertEquals(CustomCommand, command.getRequestType());
            assertEquals(ByteString.copyFromUtf8("GETSTRING"), command.getArgsArray().getArgs(0));
            assertEquals(resultPayloads.pop(), command.getArgsArray().getArgs(1));
        }
    }

    @ParameterizedTest
    @MethodSource("getEnumRoutes")
    public void submitNewCommand_with_ClusterTransaction_with_route_sends_protobuf_request(
            Route routeType) {

        String[] arg1 = new String[] {"GETSTRING", "one"};
        String[] arg2 = new String[] {"GETSTRING", "two"};
        String[] arg3 = new String[] {"GETSTRING", "two"};
        ClusterTransaction trans =
                new ClusterTransaction().customCommand(arg1).customCommand(arg2).customCommand(arg3);

        CompletableFuture<Response> future = new CompletableFuture<>();
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);
        when(channelHandler.isClosed()).thenReturn(false);

        ArgumentCaptor<RedisRequest.Builder> captor =
                ArgumentCaptor.forClass(RedisRequest.Builder.class);

        service.submitNewTransaction(trans, Optional.of(routeType), r -> null);
        verify(channelHandler).write(captor.capture(), anyBoolean());
        var requestBuilder = captor.getValue();

        var protobufToClientRouteMapping =
                Map.of(
                        SimpleRoutes.AllNodes, ALL_NODES,
                        SimpleRoutes.AllPrimaries, ALL_PRIMARIES,
                        SimpleRoutes.Random, RANDOM);

        assertAll(
                () -> assertTrue(requestBuilder.hasRoute()),
                () -> assertTrue(requestBuilder.getRoute().hasSimpleRoutes()),
                () ->
                        assertEquals(
                                routeType,
                                protobufToClientRouteMapping.get(requestBuilder.getRoute().getSimpleRoutes())),
                () -> assertFalse(requestBuilder.getRoute().hasSlotIdRoute()),
                () -> assertFalse(requestBuilder.getRoute().hasSlotKeyRoute()));
    }
}
