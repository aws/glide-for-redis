/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static glide.api.BaseClient.OK;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.ALL_NODES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.ALL_PRIMARIES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.RANDOM;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static redis_request.RedisRequestOuterClass.RequestType.ClientGetName;
import static redis_request.RedisRequestOuterClass.RequestType.ClientId;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigGet;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigResetStat;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigRewrite;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigSet;
import static redis_request.RedisRequestOuterClass.RequestType.Info;
import static redis_request.RedisRequestOuterClass.RequestType.Ping;
import static redis_request.RedisRequestOuterClass.RequestType.Time;

import glide.api.models.ClusterValue;
import glide.api.models.commands.InfoOptions;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import glide.managers.RedisExceptionCheckedFunction;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis_request.RedisRequestOuterClass.RedisRequest;
import response.ResponseOuterClass.ConstantResponse;
import response.ResponseOuterClass.Response;

@SuppressWarnings("unchecked,resource")
public class RedisClusterClientTest {

    RedisClusterClient service;

    ConnectionManager connectionManager;

    CommandManager commandManager;

    private final String[] TEST_ARGS = new String[0];

    @BeforeEach
    public void setUp() {
        connectionManager = mock(ConnectionManager.class);
        commandManager = mock(CommandManager.class);
        service = new RedisClusterClient(connectionManager, commandManager);
    }

    @Test
    @SneakyThrows
    public void custom_command_returns_single_value() {
        var commandManager = new TestCommandManager(null);

        var client = new TestClient(commandManager, "TEST");

        var value = client.customCommand(TEST_ARGS).get();
        assertEquals("TEST", value.getSingleValue());
    }

    @Test
    @SneakyThrows
    public void custom_command_returns_multi_value() {
        var commandManager = new TestCommandManager(null);

        var data = Map.of("key1", "value1", "key2", "value2");
        var client = new TestClient(commandManager, data);

        var value = client.customCommand(TEST_ARGS).get();
        assertEquals(data, value.getMultiValue());
    }

    @Test
    @SneakyThrows
    // test checks that even a map returned as a single value when single node route is used
    public void custom_command_with_single_node_route_returns_single_value() {
        var commandManager = new TestCommandManager(null);

        var data = Map.of("key1", "value1", "key2", "value2");
        var client = new TestClient(commandManager, data);

        var value = client.customCommand(TEST_ARGS, RANDOM).get();
        assertEquals(data, value.getSingleValue());
    }

    @Test
    @SneakyThrows
    public void custom_command_with_multi_node_route_returns_multi_value() {
        var commandManager = new TestCommandManager(null);

        var data = Map.of("key1", "value1", "key2", "value2");
        var client = new TestClient(commandManager, data);

        var value = client.customCommand(TEST_ARGS, ALL_NODES).get();
        assertEquals(data, value.getMultiValue());
    }

    @Test
    @SneakyThrows
    public void custom_command_returns_single_value_on_constant_response() {
        var commandManager =
                new TestCommandManager(
                        Response.newBuilder().setConstantResponse(ConstantResponse.OK).build());

        var client = new TestClient(commandManager, "OK");

        var value = client.customCommand(TEST_ARGS, ALL_NODES).get();
        assertEquals("OK", value.getSingleValue());
    }

    private static class TestClient extends RedisClusterClient {

        private final Object object;

        public TestClient(CommandManager commandManager, Object objectToReturn) {
            super(null, commandManager);
            object = objectToReturn;
        }

        @Override
        protected <T> T handleRedisResponse(Class<T> classType, boolean isNullable, Response response) {
            return (T) object;
        }
    }

    private static class TestCommandManager extends CommandManager {

        private final Response response;

        public TestCommandManager(Response responseToReturn) {
            super(null);
            response = responseToReturn != null ? responseToReturn : Response.newBuilder().build();
        }

        @Override
        public <T> CompletableFuture<T> submitCommandToChannel(
                RedisRequest.Builder command, RedisExceptionCheckedFunction<Response, T> responseHandler) {
            return CompletableFuture.supplyAsync(() -> responseHandler.apply(response));
        }
    }

    @SneakyThrows
    @Test
    public void ping_returns_success() {
        // setup
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn("PONG");

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Ping), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.ping();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals("PONG", payload);
    }

    @SneakyThrows
    @Test
    public void ping_with_message_returns_success() {
        // setup
        String message = "RETURN OF THE PONG";
        String[] arguments = new String[] {message};
        CompletableFuture<String> testResponse = new CompletableFuture();
        testResponse.complete(message);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Ping), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.ping(message);
        String pong = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(message, pong);
    }

    @SneakyThrows
    @Test
    public void ping_with_route_returns_success() {
        // setup
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn("PONG");

        Route route = ALL_NODES;

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Ping), eq(new String[0]), eq(route), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.ping(route);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals("PONG", payload);
    }

    @SneakyThrows
    @Test
    public void ping_with_message_with_route_returns_success() {
        // setup
        String message = "RETURN OF THE PONG";
        String[] arguments = new String[] {message};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(message);

        Route route = ALL_PRIMARIES;

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Ping), eq(arguments), eq(route), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.ping(message, route);
        String pong = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(message, pong);
    }

    @SneakyThrows
    @Test
    public void info_returns_string() {
        // setup
        CompletableFuture<ClusterValue<String>> testResponse = mock(CompletableFuture.class);
        Map<String, String> testPayload = new HashMap<String, String>();
        testPayload.put("addr1", "value1");
        testPayload.put("addr2", "value2");
        testPayload.put("addr3", "value3");
        when(testResponse.get()).thenReturn(ClusterValue.of(testPayload));
        when(commandManager.<ClusterValue<String>>submitNewCommand(eq(Info), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<String>> response = service.info();

        // verify
        ClusterValue<String> clusterValue = response.get();
        assertTrue(clusterValue.hasMultiData());
        Map<String, String> payload = clusterValue.getMultiValue();
        assertEquals(testPayload, payload);
    }

    @SneakyThrows
    @Test
    public void info_with_route_returns_string() {
        // setup
        CompletableFuture<ClusterValue<String>> testResponse = mock(CompletableFuture.class);
        Map<String, String> testClusterValue = Map.of("addr1", "addr1 result", "addr2", "addr2 result");
        Route route = ALL_NODES;
        when(testResponse.get()).thenReturn(ClusterValue.of(testClusterValue));
        when(commandManager.<ClusterValue<String>>submitNewCommand(
                        eq(Info), eq(new String[0]), eq(route), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<String>> response = service.info(route);

        // verify
        ClusterValue<String> clusterValue = response.get();
        assertTrue(clusterValue.hasMultiData());
        Map<String, String> clusterMap = clusterValue.getMultiValue();
        assertEquals("addr1 result", clusterMap.get("addr1"));
        assertEquals("addr2 result", clusterMap.get("addr2"));
    }

    @SneakyThrows
    @Test
    public void info_with_route_with_infoOptions_returns_string() {
        // setup
        String[] infoArguments = new String[] {"ALL", "DEFAULT"};
        CompletableFuture<ClusterValue<String>> testResponse = mock(CompletableFuture.class);
        Map<String, String> testClusterValue = Map.of("addr1", "addr1 result", "addr2", "addr2 result");
        when(testResponse.get()).thenReturn(ClusterValue.of(testClusterValue));
        Route route = ALL_PRIMARIES;
        when(commandManager.<ClusterValue<String>>submitNewCommand(
                        eq(Info), eq(infoArguments), eq(route), any()))
                .thenReturn(testResponse);

        // exercise
        InfoOptions options =
                InfoOptions.builder()
                        .section(InfoOptions.Section.ALL)
                        .section(InfoOptions.Section.DEFAULT)
                        .build();
        CompletableFuture<ClusterValue<String>> response = service.info(options, route);

        // verify
        assertEquals(testResponse.get(), response.get());
        ClusterValue<String> clusterValue = response.get();
        assertTrue(clusterValue.hasMultiData());
        Map<String, String> clusterMap = clusterValue.getMultiValue();
        assertEquals("addr1 result", clusterMap.get("addr1"));
        assertEquals("addr2 result", clusterMap.get("addr2"));
    }

    @Test
    @SneakyThrows
    public void info_with_single_node_route_returns_single_value() {
        var commandManager = new TestCommandManager(null);

        var data = "info string";
        var client = new TestClient(commandManager, data);

        var value = client.info(RANDOM).get();
        assertAll(
                () -> assertTrue(value.hasSingleData()), () -> assertEquals(data, value.getSingleValue()));
    }

    @Test
    @SneakyThrows
    public void info_with_multi_node_route_returns_multi_value() {
        var commandManager = new TestCommandManager(null);

        var data = Map.of("key1", "value1", "key2", "value2");
        var client = new TestClient(commandManager, data);

        var value = client.info(ALL_NODES).get();
        assertAll(
                () -> assertTrue(value.hasMultiData()), () -> assertEquals(data, value.getMultiValue()));
    }

    @Test
    @SneakyThrows
    public void info_with_options_and_single_node_route_returns_single_value() {
        var commandManager = new TestCommandManager(null);

        var data = "info string";
        var client = new TestClient(commandManager, data);

        var value = client.info(InfoOptions.builder().build(), RANDOM).get();
        assertAll(
                () -> assertTrue(value.hasSingleData()), () -> assertEquals(data, value.getSingleValue()));
    }

    @Test
    @SneakyThrows
    public void info_with_options_and_multi_node_route_returns_multi_value() {
        var commandManager = new TestCommandManager(null);

        var data = Map.of("key1", "value1", "key2", "value2");
        var client = new TestClient(commandManager, data);

        var value = client.info(InfoOptions.builder().build(), ALL_NODES).get();
        assertAll(
                () -> assertTrue(value.hasMultiData()), () -> assertEquals(data, value.getMultiValue()));
    }

    @SneakyThrows
    @Test
    public void clientId_returns_success() {
        // setup
        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(42L);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ClientId), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.clientId();

        // verify
        assertEquals(testResponse, response);
        assertEquals(42L, response.get());
    }

    @Test
    @SneakyThrows
    public void clientId_with_multi_node_route_returns_success() {
        var commandManager = new TestCommandManager(null);

        var data = Map.of("n1", 42L);
        var client = new TestClient(commandManager, data);

        var value = client.clientId(ALL_NODES).get();
        assertEquals(data, value.getMultiValue());
    }

    @Test
    @SneakyThrows
    public void clientId_with_single_node_route_returns_success() {
        var commandManager = new TestCommandManager(null);

        var client = new TestClient(commandManager, 42L);

        var value = client.clientId(RANDOM).get();
        assertEquals(42, value.getSingleValue());
    }

    @SneakyThrows
    @Test
    public void clientGetName_returns_success() {
        // setup
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn("TEST");

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(ClientGetName), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.clientGetName();

        // verify
        assertEquals(testResponse, response);
        assertEquals("TEST", response.get());
    }

    @Test
    @SneakyThrows
    public void clientGetName_with_single_node_route_returns_success() {
        var commandManager = new TestCommandManager(null);

        var client = new TestClient(commandManager, "TEST");

        var value = client.clientGetName(RANDOM).get();
        assertEquals("TEST", value.getSingleValue());
    }

    @Test
    @SneakyThrows
    public void clientGetName_with_multi_node_route_returns_success() {
        var commandManager = new TestCommandManager(null);

        var data = Map.of("n1", "TEST");
        var client = new TestClient(commandManager, data);

        var value = client.clientGetName(ALL_NODES).get();
        assertEquals(data, value.getMultiValue());
    }

    @SneakyThrows
    @Test
    public void configRewrite_without_route_returns_success() {
        // setup
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(ConfigRewrite), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.configRewrite();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void configRewrite_with_route_returns_success() {
        // setup
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(OK);

        Route route = ALL_NODES;

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(ConfigRewrite), eq(new String[0]), eq(route), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.configRewrite(route);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void configResetStat_without_route_returns_success() {
        // setup
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(ConfigResetStat), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.configResetStat();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void configResetStat_with_route_returns_success() {
        // setup
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(OK);

        Route route = ALL_NODES;

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(ConfigResetStat), eq(new String[0]), eq(route), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.configResetStat(route);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    // TODO copy/move tests from RedisClientTest which call super for coverage
    @SneakyThrows
    @Test
    public void configGet_returns_success() {
        // setup
        CompletableFuture<Map<String, String>> testResponse = mock(CompletableFuture.class);
        var testPayload = Map.of("timeout", "1000");
        when(testResponse.get()).thenReturn(testPayload);

        // match on protobuf request
        when(commandManager.<Map<String, String>>submitNewCommand(
                        eq(ConfigGet), eq(new String[] {"timeout"}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, String>> response = service.configGet(new String[] {"timeout"});
        Map<String, String> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(testPayload, payload);
    }

    @Test
    @SneakyThrows
    // test checks that even a map returned as a single value when single node route is used
    public void configGet_with_single_node_route_returns_single_value() {
        var commandManager = new TestCommandManager(null);

        var data = Map.of("timeout", "1000", "maxmemory", "1GB");
        var client = new TestClient(commandManager, data);

        var value = client.configGet(TEST_ARGS, RANDOM).get();
        assertAll(
                () -> assertTrue(value.hasSingleData()), () -> assertEquals(data, value.getSingleValue()));
    }

    @Test
    @SneakyThrows
    public void configGet_with_multi_node_route_returns_multi_value() {
        var commandManager = new TestCommandManager(null);

        var data = Map.of("node1", Map.of("timeout", "1000", "maxmemory", "1GB"));
        var client = new TestClient(commandManager, data);

        var value = client.configGet(TEST_ARGS, ALL_NODES).get();
        assertAll(
                () -> assertTrue(value.hasMultiData()), () -> assertEquals(data, value.getMultiValue()));
    }

    @SneakyThrows
    @Test
    public void configSet_returns_success() {
        // setup
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(ConfigSet), eq(new String[] {"timeout", "1000"}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.configSet(Map.of("timeout", "1000"));

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, response.get());
    }

    @SneakyThrows
    @Test
    public void configSet_with_route_returns_success() {
        // setup
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(ConfigSet), eq(new String[] {"value", "42"}), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.configSet(Map.of("value", "42"), RANDOM);

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, response.get());
    }

    @SneakyThrows
    @Test
    public void time_returns_success() {
        // setup
        CompletableFuture<String[]> testResponse = mock(CompletableFuture.class);
        String[] payload = new String[] {"UnixTime", "ms"};
        when(testResponse.get()).thenReturn(payload);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(Time), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.time();

        // verify
        assertEquals(testResponse, response);
        assertEquals(payload, response.get());
    }

    @SneakyThrows
    @Test
    public void time_returns_with_route_success() {
        // setup
        CompletableFuture<ClusterValue<String[]>> testResponse = mock(CompletableFuture.class);
        String[] payload = new String[] {"UnixTime", "ms"};
        when(testResponse.get()).thenReturn(ClusterValue.ofSingleValue(payload));

        // match on protobuf request
        when(commandManager.<ClusterValue<String[]>>submitNewCommand(
                        eq(Time), eq(new String[0]), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<String[]>> response = service.time(RANDOM);

        // verify
        assertEquals(testResponse, response);
        assertEquals(payload, response.get().getSingleValue());
    }
}
