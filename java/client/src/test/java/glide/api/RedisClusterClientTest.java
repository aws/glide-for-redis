/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static glide.api.BaseClient.OK;
import static glide.api.commands.ServerManagementCommands.VERSION_REDIS_API;
import static glide.api.models.GlideString.gs;
import static glide.api.models.commands.FlushMode.ASYNC;
import static glide.api.models.commands.FlushMode.SYNC;
import static glide.api.models.commands.SortBaseOptions.ALPHA_COMMAND_STRING;
import static glide.api.models.commands.SortBaseOptions.LIMIT_COMMAND_STRING;
import static glide.api.models.commands.SortBaseOptions.OrderBy.DESC;
import static glide.api.models.commands.SortBaseOptions.STORE_COMMAND_STRING;
import static glide.api.models.commands.SortOptions.ALPHA_COMMAND_STRING;
import static glide.api.models.commands.SortOptions.LIMIT_COMMAND_STRING;
import static glide.api.models.commands.SortOptions.STORE_COMMAND_STRING;
import static glide.api.models.commands.function.FunctionListOptions.LIBRARY_NAME_REDIS_API;
import static glide.api.models.commands.function.FunctionListOptions.WITH_CODE_REDIS_API;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_NODES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_PRIMARIES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute.RANDOM;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import static redis_request.RedisRequestOuterClass.RequestType.DBSize;
import static redis_request.RedisRequestOuterClass.RequestType.Echo;
import static redis_request.RedisRequestOuterClass.RequestType.FCall;
import static redis_request.RedisRequestOuterClass.RequestType.FCallReadOnly;
import static redis_request.RedisRequestOuterClass.RequestType.FlushAll;
import static redis_request.RedisRequestOuterClass.RequestType.FlushDB;
import static redis_request.RedisRequestOuterClass.RequestType.FunctionDelete;
import static redis_request.RedisRequestOuterClass.RequestType.FunctionDump;
import static redis_request.RedisRequestOuterClass.RequestType.FunctionFlush;
import static redis_request.RedisRequestOuterClass.RequestType.FunctionKill;
import static redis_request.RedisRequestOuterClass.RequestType.FunctionList;
import static redis_request.RedisRequestOuterClass.RequestType.FunctionLoad;
import static redis_request.RedisRequestOuterClass.RequestType.FunctionRestore;
import static redis_request.RedisRequestOuterClass.RequestType.FunctionStats;
import static redis_request.RedisRequestOuterClass.RequestType.Info;
import static redis_request.RedisRequestOuterClass.RequestType.LastSave;
import static redis_request.RedisRequestOuterClass.RequestType.Lolwut;
import static redis_request.RedisRequestOuterClass.RequestType.Ping;
import static redis_request.RedisRequestOuterClass.RequestType.RandomKey;
import static redis_request.RedisRequestOuterClass.RequestType.SPublish;
import static redis_request.RedisRequestOuterClass.RequestType.Sort;
import static redis_request.RedisRequestOuterClass.RequestType.SortReadOnly;
import static redis_request.RedisRequestOuterClass.RequestType.Time;
import static redis_request.RedisRequestOuterClass.RequestType.UnWatch;

import glide.api.models.ClusterTransaction;
import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.SortBaseOptions.Limit;
import glide.api.models.commands.SortClusterOptions;
import glide.api.models.commands.function.FunctionLoadOptions;
import glide.api.models.commands.function.FunctionRestorePolicy;
import glide.api.models.commands.scan.ClusterScanCursor;
import glide.api.models.commands.scan.ScanOptions;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute;
import glide.managers.CommandManager;
import glide.managers.RedisExceptionCheckedFunction;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import redis_request.RedisRequestOuterClass.RedisRequest;
import response.ResponseOuterClass.ConstantResponse;
import response.ResponseOuterClass.Response;

public class RedisClusterClientTest {

    RedisClusterClient service;

    CommandManager commandManager;

    private final String[] TEST_ARGS = new String[0];

    @BeforeEach
    public void setUp() {
        commandManager = mock(CommandManager.class);
        service =
                new RedisClusterClient(new BaseClient.ClientBuilder(null, commandManager, null, null));
    }

    @Test
    @SneakyThrows
    public void custom_command_returns_single_value() {
        var commandManager = new TestCommandManager(null);

        try (var client = new TestClient(commandManager, "TEST")) {
            var value = client.customCommand(TEST_ARGS).get();
            assertEquals("TEST", value.getSingleValue());
        }
    }

    @Test
    @SneakyThrows
    public void custom_command_returns_multi_value() {
        var commandManager = new TestCommandManager(null);

        var data = Map.of("key1", "value1", "key2", "value2");
        try (var client = new TestClient(commandManager, data)) {
            var value = client.customCommand(TEST_ARGS).get();
            assertEquals(data, value.getMultiValue());
        }
    }

    @Test
    @SneakyThrows
    // test checks that even a map returned as a single value when single node route is used
    public void custom_command_with_single_node_route_returns_single_value() {
        var commandManager = new TestCommandManager(null);

        var data = Map.of("key1", "value1", "key2", "value2");
        try (var client = new TestClient(commandManager, data)) {
            var value = client.customCommand(TEST_ARGS, RANDOM).get();
            assertEquals(data, value.getSingleValue());
        }
    }

    @Test
    @SneakyThrows
    public void custom_command_with_multi_node_route_returns_multi_value() {
        var commandManager = new TestCommandManager(null);

        var data = Map.of("key1", "value1", "key2", "value2");
        try (var client = new TestClient(commandManager, data)) {
            var value = client.customCommand(TEST_ARGS, ALL_NODES).get();
            assertEquals(data, value.getMultiValue());
        }
    }

    @Test
    @SneakyThrows
    public void custom_command_returns_single_value_on_constant_response() {
        var commandManager =
                new TestCommandManager(
                        Response.newBuilder().setConstantResponse(ConstantResponse.OK).build());

        try (var client = new TestClient(commandManager, "OK")) {
            var value = client.customCommand(TEST_ARGS, ALL_NODES).get();
            assertEquals("OK", value.getSingleValue());
        }
    }

    private static class TestClient extends RedisClusterClient {

        private final Object object;

        public TestClient(CommandManager commandManager, Object objectToReturn) {
            super(new BaseClient.ClientBuilder(null, commandManager, null, null));
            object = objectToReturn;
        }

        @Override
        protected <T> T handleRedisResponse(
                Class<T> classType, EnumSet<ResponseFlags> flags, Response response) {
            @SuppressWarnings("unchecked")
            T returnValue = (T) object;
            return returnValue;
        }

        @Override
        public void close() {}
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
    public void exec_without_routing() {
        // setup
        Object[] value = new Object[] {"PONG", "PONG"};
        ClusterTransaction transaction = new ClusterTransaction().ping().ping();

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewTransaction(
                        eq(transaction), eq(Optional.empty()), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.exec(transaction);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertArrayEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void exec_with_routing() {
        // setup
        Object[] value = new Object[] {"PONG", "PONG"};
        ClusterTransaction transaction = new ClusterTransaction().ping().ping();
        SingleNodeRoute route = RANDOM;

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewTransaction(
                        eq(transaction), eq(Optional.of(route)), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.exec(transaction, route);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertArrayEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void ping_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete("PONG");

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
        CompletableFuture<String> testResponse = new CompletableFuture<>();
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
    public void ping_binary_with_message_returns_success() {
        // setup
        GlideString message = gs("RETURN OF THE PONG");
        GlideString[] arguments = new GlideString[] {message};
        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(message);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(Ping), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.ping(message);
        GlideString pong = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(message, pong);
    }

    @SneakyThrows
    @Test
    public void ping_with_route_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete("PONG");

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
    public void ping_binary_with_message_with_route_returns_success() {
        // setup
        GlideString message = gs("RETURN OF THE PONG");
        GlideString[] arguments = new GlideString[] {message};
        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(message);

        Route route = ALL_PRIMARIES;

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(Ping), eq(arguments), eq(route), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.ping(message, route);
        GlideString pong = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(message, pong);
    }

    @SneakyThrows
    @Test
    public void echo_returns_success() {
        // setup
        String message = "GLIDE FOR REDIS";
        String[] arguments = new String[] {message};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(message);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Echo), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.echo(message);
        String echo = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(message, echo);
    }

    @SneakyThrows
    @Test
    public void echo_binary_returns_success() {
        // setup
        GlideString message = gs("GLIDE FOR REDIS");
        GlideString[] arguments = new GlideString[] {message};
        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(message);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(Echo), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.echo(message);
        GlideString echo = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(message, echo);
    }

    @SneakyThrows
    @Test
    public void echo_with_route_returns_success() {
        // setup
        String message = "GLIDE FOR REDIS";
        String[] arguments = new String[] {message};
        CompletableFuture<ClusterValue<String>> testResponse = new CompletableFuture<>();
        testResponse.complete(ClusterValue.ofSingleValue(message));

        // match on protobuf request
        when(commandManager.<ClusterValue<String>>submitNewCommand(
                        eq(Echo), eq(arguments), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<String>> response = service.echo(message, RANDOM);
        String echo = response.get().getSingleValue();

        // verify
        assertEquals(testResponse, response);
        assertEquals(message, echo);
    }

    @SneakyThrows
    @Test
    public void echo_binary_with_route_returns_success() {
        // setup
        GlideString message = gs("GLIDE FOR REDIS");
        GlideString[] arguments = new GlideString[] {message};
        CompletableFuture<ClusterValue<GlideString>> testResponse = new CompletableFuture<>();
        testResponse.complete(ClusterValue.ofSingleValue(message));

        // match on protobuf request
        when(commandManager.<ClusterValue<GlideString>>submitNewCommand(
                        eq(Echo), eq(arguments), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<GlideString>> response = service.echo(message, RANDOM);
        GlideString echo = response.get().getSingleValue();

        // verify
        assertEquals(testResponse, response);
        assertEquals(message, echo);
    }

    @SneakyThrows
    @Test
    public void info_returns_string() {
        // setup
        Map<String, String> testPayload = new HashMap<>();
        testPayload.put("addr1", "value1");
        testPayload.put("addr2", "value2");
        testPayload.put("addr3", "value3");
        CompletableFuture<ClusterValue<String>> testResponse = new CompletableFuture<>();
        testResponse.complete(ClusterValue.of(testPayload));
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
        Map<String, String> testClusterValue = Map.of("addr1", "addr1 result", "addr2", "addr2 result");
        Route route = ALL_NODES;
        CompletableFuture<ClusterValue<String>> testResponse = new CompletableFuture<>();
        testResponse.complete(ClusterValue.of(testClusterValue));
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
        Map<String, String> testClusterValue = Map.of("addr1", "addr1 result", "addr2", "addr2 result");
        CompletableFuture<ClusterValue<String>> testResponse = new CompletableFuture<>();
        testResponse.complete(ClusterValue.of(testClusterValue));

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
        try (var client = new TestClient(commandManager, data)) {
            var value = client.info(RANDOM).get();
            assertAll(
                    () -> assertTrue(value.hasSingleData()),
                    () -> assertEquals(data, value.getSingleValue()));
        }
    }

    @Test
    @SneakyThrows
    public void info_with_multi_node_route_returns_multi_value() {
        var commandManager = new TestCommandManager(null);

        var data = Map.of("key1", "value1", "key2", "value2");
        try (var client = new TestClient(commandManager, data)) {
            var value = client.info(ALL_NODES).get();
            assertAll(
                    () -> assertTrue(value.hasMultiData()), () -> assertEquals(data, value.getMultiValue()));
        }
    }

    @Test
    @SneakyThrows
    public void info_with_options_and_single_node_route_returns_single_value() {
        var commandManager = new TestCommandManager(null);

        var data = "info string";
        try (var client = new TestClient(commandManager, data)) {
            var value = client.info(InfoOptions.builder().build(), RANDOM).get();
            assertAll(
                    () -> assertTrue(value.hasSingleData()),
                    () -> assertEquals(data, value.getSingleValue()));
        }
    }

    @Test
    @SneakyThrows
    public void info_with_options_and_multi_node_route_returns_multi_value() {
        var commandManager = new TestCommandManager(null);

        var data = Map.of("key1", "value1", "key2", "value2");
        try (var client = new TestClient(commandManager, data)) {
            var value = client.info(InfoOptions.builder().build(), ALL_NODES).get();
            assertAll(
                    () -> assertTrue(value.hasMultiData()), () -> assertEquals(data, value.getMultiValue()));
        }
    }

    @SneakyThrows
    @Test
    public void clientId_returns_success() {
        // setup
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(42L);

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
        try (var client = new TestClient(commandManager, data)) {
            var value = client.clientId(ALL_NODES).get();

            assertEquals(data, value.getMultiValue());
        }
    }

    @Test
    @SneakyThrows
    public void clientId_with_single_node_route_returns_success() {
        var commandManager = new TestCommandManager(null);

        try (var client = new TestClient(commandManager, 42L)) {
            var value = client.clientId(RANDOM).get();
            assertEquals(42, value.getSingleValue());
        }
    }

    @SneakyThrows
    @Test
    public void clientGetName_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete("TEST");

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

        try (var client = new TestClient(commandManager, "TEST")) {
            var value = client.clientGetName(RANDOM).get();
            assertEquals("TEST", value.getSingleValue());
        }
    }

    @Test
    @SneakyThrows
    public void clientGetName_with_multi_node_route_returns_success() {
        var commandManager = new TestCommandManager(null);

        var data = Map.of("n1", "TEST");
        try (var client = new TestClient(commandManager, data)) {
            var value = client.clientGetName(ALL_NODES).get();
            assertEquals(data, value.getMultiValue());
        }
    }

    @SneakyThrows
    @Test
    public void configRewrite_without_route_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

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
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

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
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

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
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

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
        var testPayload = Map.of("timeout", "1000");
        CompletableFuture<Map<String, String>> testResponse = new CompletableFuture<>();
        testResponse.complete(testPayload);

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
        try (var client = new TestClient(commandManager, data)) {
            var value = client.configGet(TEST_ARGS, RANDOM).get();
            assertAll(
                    () -> assertTrue(value.hasSingleData()),
                    () -> assertEquals(data, value.getSingleValue()));
        }
    }

    @Test
    @SneakyThrows
    public void configGet_with_multi_node_route_returns_multi_value() {
        var commandManager = new TestCommandManager(null);

        var data = Map.of("node1", Map.of("timeout", "1000", "maxmemory", "1GB"));
        try (var client = new TestClient(commandManager, data)) {
            var value = client.configGet(TEST_ARGS, ALL_NODES).get();
            assertAll(
                    () -> assertTrue(value.hasMultiData()), () -> assertEquals(data, value.getMultiValue()));
        }
    }

    @SneakyThrows
    @Test
    public void configSet_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

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
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

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

        String[] payload = new String[] {"UnixTime", "ms"};
        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(payload);

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
        String[] payload = new String[] {"UnixTime", "ms"};
        CompletableFuture<ClusterValue<String[]>> testResponse = new CompletableFuture<>();
        testResponse.complete(ClusterValue.ofSingleValue(payload));

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

    @SneakyThrows
    @Test
    public void lastsave_returns_success() {
        // setup
        Long value = 42L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(LastSave), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.lastsave();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void lastsave_returns_with_route_success() {
        // setup
        Long value = 42L;
        CompletableFuture<ClusterValue<Long>> testResponse = new CompletableFuture<>();
        testResponse.complete(ClusterValue.ofSingleValue(value));

        // match on protobuf request
        when(commandManager.<ClusterValue<Long>>submitNewCommand(
                        eq(LastSave), eq(new String[0]), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<Long>> response = service.lastsave(RANDOM);

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get().getSingleValue());
    }

    @SneakyThrows
    @Test
    public void flushall_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FlushAll), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.flushall();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void flushall_with_mode_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(FlushAll), eq(new String[] {SYNC.toString()}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.flushall(SYNC);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void flushall_with_route_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(FlushAll), eq(new String[0]), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.flushall(RANDOM);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void flushall_with_route_and_mode_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(FlushAll), eq(new String[] {SYNC.toString()}), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.flushall(SYNC, RANDOM);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void flushdb_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FlushDB), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.flushdb();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void flushdb_with_mode_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(FlushDB), eq(new String[] {SYNC.toString()}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.flushdb(SYNC);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void flushdb_with_route_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FlushDB), eq(new String[0]), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.flushdb(RANDOM);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void flushdb_with_route_and_mode_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(FlushDB), eq(new String[] {SYNC.toString()}), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.flushdb(SYNC, RANDOM);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void lolwut_returns_success() {
        // setup
        String value = "pewpew";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Lolwut), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.lolwut();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void lolwut_with_params_returns_success() {
        // setup
        String value = "pewpew";
        String[] arguments = new String[] {"1", "2"};
        int[] params = new int[] {1, 2};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Lolwut), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.lolwut(params);

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void lolwut_with_version_returns_success() {
        // setup
        String value = "pewpew";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(Lolwut), eq(new String[] {VERSION_REDIS_API, "42"}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.lolwut(42);

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void lolwut_with_version_and_params_returns_success() {
        // setup
        String value = "pewpew";
        String[] arguments = new String[] {VERSION_REDIS_API, "42", "1", "2"};
        int[] params = new int[] {1, 2};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Lolwut), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.lolwut(42, params);

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void lolwut_with_route_returns_success() {
        // setup
        ClusterValue<String> value = ClusterValue.ofSingleValue("pewpew");
        CompletableFuture<ClusterValue<String>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<ClusterValue<String>>submitNewCommand(
                        eq(Lolwut), eq(new String[0]), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<String>> response = service.lolwut(RANDOM);

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void lolwut_with_params_and_route_returns_success() {
        // setup
        ClusterValue<String> value = ClusterValue.ofSingleValue("pewpew");
        String[] arguments = new String[] {"1", "2"};
        int[] params = new int[] {1, 2};
        CompletableFuture<ClusterValue<String>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<ClusterValue<String>>submitNewCommand(
                        eq(Lolwut), eq(arguments), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<String>> response = service.lolwut(params, RANDOM);

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void lolwut_with_version_and_route_returns_success() {
        // setup
        ClusterValue<String> value = ClusterValue.ofSingleValue("pewpew");
        CompletableFuture<ClusterValue<String>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<ClusterValue<String>>submitNewCommand(
                        eq(Lolwut), eq(new String[] {VERSION_REDIS_API, "42"}), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<String>> response = service.lolwut(42, RANDOM);

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void lolwut_with_version_and_params_and_route_returns_success() {
        // setup
        ClusterValue<String> value = ClusterValue.ofSingleValue("pewpew");
        String[] arguments = new String[] {VERSION_REDIS_API, "42", "1", "2"};
        int[] params = new int[] {1, 2};
        CompletableFuture<ClusterValue<String>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<ClusterValue<String>>submitNewCommand(
                        eq(Lolwut), eq(arguments), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<String>> response = service.lolwut(42, params, RANDOM);

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void dbsize_returns_success() {
        // setup
        Long value = 10L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(DBSize), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.dbsize();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void dbsize_with_route_returns_success() {
        // setup
        Long value = 10L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(DBSize), eq(new String[0]), eq(ALL_PRIMARIES), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.dbsize(ALL_PRIMARIES);

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void functionLoad_returns_success() {
        // setup
        String code = "The best code ever";
        String[] args = new String[] {code};
        String value = "42";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionLoad), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionLoad(code, false);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionLoad_binary_returns_success() {
        // setup
        GlideString code = gs("The best code ever");
        GlideString[] args = new GlideString[] {code};
        GlideString value = gs("42");
        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(FunctionLoad), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.functionLoad(code, false);
        GlideString payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionLoad_with_replace_returns_success() {
        // setup
        String code = "The best code ever";
        String[] args = new String[] {FunctionLoadOptions.REPLACE.toString(), code};
        String value = "42";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionLoad), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionLoad(code, true);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionLoad_with_replace_binary_returns_success() {
        // setup
        GlideString code = gs("The best code ever");
        GlideString[] args = new GlideString[] {gs(FunctionLoadOptions.REPLACE.toString()), code};
        GlideString value = gs("42");
        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(FunctionLoad), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.functionLoad(code, true);
        GlideString payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionLoad_with_route_returns_success() {
        // setup
        String code = "The best code ever";
        String[] args = new String[] {code};
        String value = "42";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionLoad), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionLoad(code, false, RANDOM);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionLoad_with_route_binary_returns_success() {
        // setup
        GlideString code = gs("The best code ever");
        GlideString[] args = new GlideString[] {code};
        GlideString value = gs("42");
        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(
                        eq(FunctionLoad), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.functionLoad(code, false, RANDOM);
        GlideString payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionLoad_with_replace_with_route_returns_success() {
        // setup
        String code = "The best code ever";
        String[] args = new String[] {FunctionLoadOptions.REPLACE.toString(), code};
        String value = "42";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionLoad), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionLoad(code, true, RANDOM);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionLoad_with_replace_with_route_binary_returns_success() {
        // setup
        GlideString code = gs("The best code ever");
        GlideString[] args = new GlideString[] {gs(FunctionLoadOptions.REPLACE.toString()), code};
        GlideString value = gs("42");
        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(
                        eq(FunctionLoad), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.functionLoad(code, true, RANDOM);
        GlideString payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionList_returns_success() {
        // setup
        String[] args = new String[0];
        @SuppressWarnings("unchecked")
        Map<String, Object>[] value = new Map[0];
        CompletableFuture<Map<String, Object>[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Object>[]>submitNewCommand(eq(FunctionList), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Object>[]> response = service.functionList(false);
        Map<String, Object>[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionList_with_pattern_returns_success() {
        // setup
        String pattern = "*";
        String[] args = new String[] {LIBRARY_NAME_REDIS_API, pattern, WITH_CODE_REDIS_API};
        @SuppressWarnings("unchecked")
        Map<String, Object>[] value = new Map[0];
        CompletableFuture<Map<String, Object>[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Object>[]>submitNewCommand(eq(FunctionList), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Object>[]> response = service.functionList(pattern, true);
        Map<String, Object>[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionList_with_route_returns_success() {
        // setup
        String[] args = new String[] {WITH_CODE_REDIS_API};
        @SuppressWarnings("unchecked")
        Map<String, Object>[] value = new Map[0];
        CompletableFuture<ClusterValue<Map<String, Object>[]>> testResponse = new CompletableFuture<>();
        testResponse.complete(ClusterValue.ofSingleValue(value));

        // match on protobuf request
        when(commandManager.<ClusterValue<Map<String, Object>[]>>submitNewCommand(
                        eq(FunctionList), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<Map<String, Object>[]>> response =
                service.functionList(true, RANDOM);
        ClusterValue<Map<String, Object>[]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload.getSingleValue());
    }

    @SneakyThrows
    @Test
    public void functionList_with_pattern_and_route_returns_success() {
        // setup
        String pattern = "*";
        String[] args = new String[] {LIBRARY_NAME_REDIS_API, pattern};
        @SuppressWarnings("unchecked")
        Map<String, Object>[] value = new Map[0];
        CompletableFuture<ClusterValue<Map<String, Object>[]>> testResponse = new CompletableFuture<>();
        testResponse.complete(ClusterValue.ofSingleValue(value));

        // match on protobuf request
        when(commandManager.<ClusterValue<Map<String, Object>[]>>submitNewCommand(
                        eq(FunctionList), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<Map<String, Object>[]>> response =
                service.functionList(pattern, false, RANDOM);
        ClusterValue<Map<String, Object>[]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload.getSingleValue());
    }

    @SneakyThrows
    @Test
    public void functionFlush_returns_success() {
        // setup
        String[] args = new String[0];
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionFlush), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionFlush();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void functionFlush_with_mode_returns_success() {
        // setup
        FlushMode mode = ASYNC;
        String[] args = new String[] {mode.toString()};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionFlush), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionFlush(mode);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void functionFlush_with_route_returns_success() {
        // setup
        String[] args = new String[0];
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionFlush), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionFlush(RANDOM);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void functionFlush_with_mode_and_route_returns_success() {
        // setup
        FlushMode mode = ASYNC;
        String[] args = new String[] {mode.toString()};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionFlush), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionFlush(mode, RANDOM);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void functionDelete_returns_success() {
        // setup
        String libName = "GLIDE";
        String[] args = new String[] {libName};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionDelete), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionDelete(libName);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void functionDelete_binary_returns_success() {
        // setup
        GlideString libName = gs("GLIDE");
        GlideString[] args = new GlideString[] {libName};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionDelete), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionDelete(libName);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void functionDelete_with_route_returns_success() {
        // setup
        String libName = "GLIDE";
        String[] args = new String[] {libName};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionDelete), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionDelete(libName, RANDOM);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void functionDelete_with_route_binary_returns_success() {
        // setup
        GlideString libName = gs("GLIDE");
        GlideString[] args = new GlideString[] {libName};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionDelete), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionDelete(libName, RANDOM);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void unwatch_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(UnWatch), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.unwatch();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void unwatch_with_route_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(UnWatch), eq(new String[0]), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.unwatch(RANDOM);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void fcall_without_keys_and_without_args_returns_success() {
        // setup
        String function = "func";
        String[] args = new String[] {function, "0"};
        Object value = "42";
        CompletableFuture<Object> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.submitNewCommand(eq(FCall), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.fcall(function);
        Object payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcall_binary_without_keys_and_without_args_returns_success() {
        // setup
        GlideString function = gs("func");
        GlideString[] args = new GlideString[] {function, gs("0")};
        Object value = "42";
        CompletableFuture<Object> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.submitNewCommand(eq(FCall), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.fcall(function);
        Object payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcall_without_keys_and_without_args_but_with_route_returns_success() {
        // setup
        String function = "func";
        String[] args = new String[] {function, "0"};
        ClusterValue<Object> value = ClusterValue.ofSingleValue("42");
        CompletableFuture<ClusterValue<Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<ClusterValue<Object>>submitNewCommand(
                        eq(FCall), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<Object>> response = service.fcall(function, RANDOM);
        ClusterValue<Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcall_binary_without_keys_and_without_args_but_with_route_returns_success() {
        // setup
        GlideString function = gs("func");
        GlideString[] args = new GlideString[] {function, gs("0")};
        ClusterValue<Object> value = ClusterValue.ofSingleValue("42");
        CompletableFuture<ClusterValue<Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<ClusterValue<Object>>submitNewCommand(
                        eq(FCall), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<Object>> response = service.fcall(function, RANDOM);
        ClusterValue<Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcall_without_keys_returns_success() {
        // setup
        String function = "func";
        String[] arguments = new String[] {"1", "2"};
        String[] args = new String[] {function, "0", "1", "2"};
        Object value = "42";
        CompletableFuture<Object> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.submitNewCommand(eq(FCall), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.fcall(function, arguments);
        Object payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcall_binary_without_keys_returns_success() {
        // setup
        GlideString function = gs("func");
        GlideString[] arguments = new GlideString[] {gs("1"), gs("2")};
        GlideString[] args = new GlideString[] {function, gs("0"), gs("1"), gs("2")};
        Object value = "42";
        CompletableFuture<Object> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.submitNewCommand(eq(FCall), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.fcall(function, arguments);
        Object payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcall_without_keys_and_with_route_returns_success() {
        // setup
        String function = "func";
        String[] arguments = new String[] {"1", "2"};
        String[] args = new String[] {function, "0", "1", "2"};
        ClusterValue<Object> value = ClusterValue.ofSingleValue("42");
        CompletableFuture<ClusterValue<Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<ClusterValue<Object>>submitNewCommand(
                        eq(FCall), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<Object>> response = service.fcall(function, arguments, RANDOM);
        ClusterValue<Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcall_bianry_without_keys_and_with_route_returns_success() {
        // setup
        GlideString function = gs("func");
        GlideString[] arguments = new GlideString[] {gs("1"), gs("2")};
        GlideString[] args = new GlideString[] {function, gs("0"), gs("1"), gs("2")};
        ClusterValue<Object> value = ClusterValue.ofSingleValue("42");
        CompletableFuture<ClusterValue<Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<ClusterValue<Object>>submitNewCommand(
                        eq(FCall), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<Object>> response = service.fcall(function, arguments, RANDOM);
        ClusterValue<Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcallReadOnly_without_keys_and_without_args_returns_success() {
        // setup
        String function = "func";
        String[] args = new String[] {function, "0"};
        Object value = "42";
        CompletableFuture<Object> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.submitNewCommand(eq(FCallReadOnly), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.fcallReadOnly(function);
        Object payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcallReadOnly_binary_without_keys_and_without_args_returns_success() {
        // setup
        GlideString function = gs("func");
        GlideString[] args = new GlideString[] {function, gs("0")};
        Object value = "42";
        CompletableFuture<Object> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.submitNewCommand(eq(FCallReadOnly), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.fcallReadOnly(function);
        Object payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionKill_returns_success() {
        // setup
        String[] args = new String[0];
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionKill), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionKill();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void functionKill_with_route_returns_success() {
        // setup
        String[] args = new String[0];
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionKill), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionKill(RANDOM);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void functionStats_returns_success() {
        // setup
        String[] args = new String[0];
        ClusterValue<Map<String, Map<String, Object>>> value =
                ClusterValue.ofSingleValue(Map.of("1", Map.of("2", 2)));
        CompletableFuture<ClusterValue<Map<String, Map<String, Object>>>> testResponse =
                new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<ClusterValue<Map<String, Map<String, Object>>>>submitNewCommand(
                        eq(FunctionStats), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<Map<String, Map<String, Object>>>> response =
                service.functionStats();
        ClusterValue<Map<String, Map<String, Object>>> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcallReadOnly_without_keys_and_without_args_but_with_route_returns_success() {
        // setup
        String function = "func";
        String[] args = new String[] {function, "0"};
        ClusterValue<Object> value = ClusterValue.ofSingleValue("42");
        CompletableFuture<ClusterValue<Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<ClusterValue<Object>>submitNewCommand(
                        eq(FCallReadOnly), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<Object>> response = service.fcallReadOnly(function, RANDOM);
        ClusterValue<Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcallReadOnly_binary_without_keys_and_without_args_but_with_route_returns_success() {
        // setup
        GlideString function = gs("func");
        GlideString[] args = new GlideString[] {function, gs("0")};
        ClusterValue<Object> value = ClusterValue.ofSingleValue("42");
        CompletableFuture<ClusterValue<Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<ClusterValue<Object>>submitNewCommand(
                        eq(FCallReadOnly), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<Object>> response = service.fcallReadOnly(function, RANDOM);
        ClusterValue<Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcallReadOnly_without_keys_returns_success() {
        // setup
        String function = "func";
        String[] arguments = new String[] {"1", "2"};
        String[] args = new String[] {function, "0", "1", "2"};
        Object value = "42";
        CompletableFuture<Object> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.submitNewCommand(eq(FCallReadOnly), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.fcallReadOnly(function, arguments);
        Object payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcallReadOnly_binary_without_keys_returns_success() {
        // setup
        GlideString function = gs("func");
        GlideString[] arguments = new GlideString[] {gs("1"), gs("2")};
        GlideString[] args = new GlideString[] {function, gs("0"), gs("1"), gs("2")};
        Object value = "42";
        CompletableFuture<Object> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.submitNewCommand(eq(FCallReadOnly), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.fcallReadOnly(function, arguments);
        Object payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcallReadOnly_without_keys_and_with_route_returns_success() {
        // setup
        String function = "func";
        String[] arguments = new String[] {"1", "2"};
        String[] args = new String[] {function, "0", "1", "2"};
        ClusterValue<Object> value = ClusterValue.ofSingleValue("42");
        CompletableFuture<ClusterValue<Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<ClusterValue<Object>>submitNewCommand(
                        eq(FCallReadOnly), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<Object>> response =
                service.fcallReadOnly(function, arguments, RANDOM);
        ClusterValue<Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcallReadOnly_binary_without_keys_and_with_route_returns_success() {
        // setup
        GlideString function = gs("func");
        GlideString[] arguments = new GlideString[] {gs("1"), gs("2")};
        GlideString[] args = new GlideString[] {function, gs("0"), gs("1"), gs("2")};
        ClusterValue<Object> value = ClusterValue.ofSingleValue("42");
        CompletableFuture<ClusterValue<Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<ClusterValue<Object>>submitNewCommand(
                        eq(FCallReadOnly), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<Object>> response =
                service.fcallReadOnly(function, arguments, RANDOM);
        ClusterValue<Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionStats_with_route_returns_success() {
        // setup
        String[] args = new String[0];
        ClusterValue<Map<String, Map<String, Object>>> value =
                ClusterValue.ofSingleValue(Map.of("1", Map.of("2", 2)));
        CompletableFuture<ClusterValue<Map<String, Map<String, Object>>>> testResponse =
                new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<ClusterValue<Map<String, Map<String, Object>>>>submitNewCommand(
                        eq(FunctionStats), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<Map<String, Map<String, Object>>>> response =
                service.functionStats(RANDOM);
        ClusterValue<Map<String, Map<String, Object>>> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionDump_returns_success() {
        // setup
        byte[] value = new byte[] {42};
        CompletableFuture<byte[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<byte[]>submitNewCommand(eq(FunctionDump), eq(new GlideString[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<byte[]> response = service.functionDump();
        byte[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionDump_with_route_returns_success() {
        // setup
        ClusterValue<byte[]> value = ClusterValue.of(new byte[] {42});
        CompletableFuture<ClusterValue<byte[]>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<ClusterValue<byte[]>>submitNewCommand(
                        eq(FunctionDump), eq(new GlideString[0]), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<byte[]>> response = service.functionDump(RANDOM);
        ClusterValue<byte[]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionRestore_returns_success() {
        // setup
        byte[] data = new byte[] {42};
        GlideString[] args = {gs(data)};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionRestore), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionRestore(data);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void functionRestore_with_policy_returns_success() {
        // setup
        byte[] data = new byte[] {42};
        GlideString[] args = {gs(data), gs(FunctionRestorePolicy.FLUSH.toString())};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionRestore), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionRestore(data, FunctionRestorePolicy.FLUSH);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void functionRestore_with_route_returns_success() {
        // setup
        byte[] data = new byte[] {42};
        GlideString[] args = {gs(data)};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionRestore), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionRestore(data, RANDOM);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void functionRestore_with_policy_and_route_returns_success() {
        // setup
        byte[] data = new byte[] {42};
        GlideString[] args = {gs(data), gs(FunctionRestorePolicy.FLUSH.toString())};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionRestore), eq(args), eq(RANDOM), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response =
                service.functionRestore(data, FunctionRestorePolicy.FLUSH, RANDOM);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void randomKey_with_route() {
        // setup
        String key1 = "key1";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(key1);
        Route route = ALL_NODES;

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(RandomKey), eq(new String[0]), eq(route), any()))
                .thenReturn(testResponse);
        CompletableFuture<String> response = service.randomKey(route);

        // verify
        assertEquals(testResponse, response);
    }

    @SneakyThrows
    @Test
    public void randomKey() {
        // setup
        String key1 = "key1";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(key1);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(RandomKey), eq(new String[0]), any()))
                .thenReturn(testResponse);
        CompletableFuture<String> response = service.randomKey();

        // verify
        assertEquals(testResponse, response);
    }

    @SneakyThrows
    @Test
    public void spublish_returns_success() {
        // setup
        String channel = "channel";
        String message = "message";
        String[] arguments = new String[] {channel, message};

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(SPublish), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.spublish(channel, message);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void sort_returns_success() {
        // setup
        String[] result = new String[] {"1", "2", "3"};
        String key = "key";
        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(Sort), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.sort(key);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void sort_binary_returns_success() {
        // setup
        GlideString[] result = new GlideString[] {gs("1"), gs("2"), gs("3")};
        GlideString key = gs("key");
        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(
                        eq(Sort), eq(new GlideString[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response = service.sort(key);
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void sort_with_options_returns_success() {
        // setup
        String[] result = new String[] {"1", "2", "3"};
        String key = "key";
        Long limitOffset = 0L;
        Long limitCount = 2L;
        String[] args =
                new String[] {
                    key,
                    LIMIT_COMMAND_STRING,
                    limitOffset.toString(),
                    limitCount.toString(),
                    DESC.toString(),
                    ALPHA_COMMAND_STRING
                };
        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(Sort), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response =
                service.sort(
                        key,
                        SortClusterOptions.builder()
                                .alpha()
                                .limit(new Limit(limitOffset, limitCount))
                                .orderBy(DESC)
                                .build());
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void sort_with_options_binary_returns_success() {
        // setup
        GlideString[] result = new GlideString[] {gs("1"), gs("2"), gs("3")};
        GlideString key = gs("key");
        Long limitOffset = 0L;
        Long limitCount = 2L;
        GlideString[] args =
                new GlideString[] {
                    key,
                    gs(LIMIT_COMMAND_STRING),
                    gs(limitOffset.toString()),
                    gs(limitCount.toString()),
                    gs(DESC.toString()),
                    gs(ALPHA_COMMAND_STRING)
                };
        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(Sort), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response =
                service.sort(
                        key,
                        SortClusterOptions.builder()
                                .alpha()
                                .limit(new Limit(limitOffset, limitCount))
                                .orderBy(DESC)
                                .build());
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void sortReadOnly_returns_success() {
        // setup
        String[] result = new String[] {"1", "2", "3"};
        String key = "key";
        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(SortReadOnly), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.sortReadOnly(key);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void sortReadOnly_binary_returns_success() {
        // setup
        GlideString[] result = new GlideString[] {gs("1"), gs("2"), gs("3")};
        GlideString key = gs("key");
        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(
                        eq(SortReadOnly), eq(new GlideString[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response = service.sortReadOnly(key);
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void sortReadOnly_with_options_returns_success() {
        // setup
        String[] result = new String[] {"1", "2", "3"};
        String key = "key";
        Long limitOffset = 0L;
        Long limitCount = 2L;
        String[] args =
                new String[] {
                    key,
                    LIMIT_COMMAND_STRING,
                    limitOffset.toString(),
                    limitCount.toString(),
                    DESC.toString(),
                    ALPHA_COMMAND_STRING
                };
        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(SortReadOnly), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response =
                service.sortReadOnly(
                        key,
                        SortClusterOptions.builder()
                                .alpha()
                                .limit(new Limit(limitOffset, limitCount))
                                .orderBy(DESC)
                                .build());
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void sortReadOnly_with_options_binary_returns_success() {
        // setup
        GlideString[] result = new GlideString[] {gs("1"), gs("2"), gs("3")};
        GlideString key = gs("key");
        Long limitOffset = 0L;
        Long limitCount = 2L;
        GlideString[] args =
                new GlideString[] {
                    key,
                    gs(LIMIT_COMMAND_STRING),
                    gs(limitOffset.toString()),
                    gs(limitCount.toString()),
                    gs(DESC.toString()),
                    gs(ALPHA_COMMAND_STRING)
                };
        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(SortReadOnly), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response =
                service.sortReadOnly(
                        key,
                        SortClusterOptions.builder()
                                .alpha()
                                .limit(new Limit(limitOffset, limitCount))
                                .orderBy(DESC)
                                .build());
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void sortStore_returns_success() {
        // setup
        Long result = 5L;
        String key = "key";
        String destKey = "destKey";
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(Sort), eq(new String[] {key, STORE_COMMAND_STRING, destKey}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.sortStore(key, destKey);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void sortStore_binary_returns_success() {
        // setup
        Long result = 5L;
        GlideString key = gs("key");
        GlideString destKey = gs("destKey");
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(Sort), eq(new GlideString[] {key, gs(STORE_COMMAND_STRING), destKey}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.sortStore(key, destKey);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void sortStore_with_options_returns_success() {
        // setup
        Long result = 5L;
        String key = "key";
        String destKey = "destKey";
        Long limitOffset = 0L;
        Long limitCount = 2L;
        String[] args =
                new String[] {
                    key,
                    LIMIT_COMMAND_STRING,
                    limitOffset.toString(),
                    limitCount.toString(),
                    DESC.toString(),
                    ALPHA_COMMAND_STRING,
                    STORE_COMMAND_STRING,
                    destKey
                };
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(Sort), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response =
                service.sortStore(
                        key,
                        destKey,
                        SortClusterOptions.builder()
                                .alpha()
                                .limit(new Limit(limitOffset, limitCount))
                                .orderBy(DESC)
                                .build());
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void sortStore_with_options_binary_returns_success() {
        // setup
        Long result = 5L;
        GlideString key = gs("key");
        GlideString destKey = gs("destKey");
        Long limitOffset = 0L;
        Long limitCount = 2L;
        GlideString[] args =
                new GlideString[] {
                    key,
                    gs(LIMIT_COMMAND_STRING),
                    gs(limitOffset.toString()),
                    gs(limitCount.toString()),
                    gs(DESC.toString()),
                    gs(ALPHA_COMMAND_STRING),
                    gs(STORE_COMMAND_STRING),
                    destKey
                };
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(Sort), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response =
                service.sortStore(
                        key,
                        destKey,
                        SortClusterOptions.builder()
                                .alpha()
                                .limit(new Limit(limitOffset, limitCount))
                                .orderBy(DESC)
                                .build());
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void clusterScan_new_cursor() {
        ClusterScanCursor mockCursor = Mockito.mock(ClusterScanCursor.class);
        when(mockCursor.getCursorHandle()).thenReturn("1");

        final Object[] result = new Object[] {mockCursor.getCursorHandle(), new Object[] {"foo"}};
        final CompletableFuture<Object[]> testResponse = CompletableFuture.completedFuture(result);
        when(commandManager.<Object[]>submitClusterScan(
                        eq(ClusterScanCursor.INITIAL_CURSOR_INSTANCE),
                        eq(ScanOptions.builder().build()),
                        any()))
                .thenReturn(testResponse);

        final CompletableFuture<Object[]> actualResponse =
                service.clusterScan(ClusterScanCursor.initalCursor());
        assertEquals(
                mockCursor.getCursorHandle(),
                ((ClusterScanCursor) actualResponse.get()[0]).getCursorHandle());
    }

    @SneakyThrows
    @Test
    public void clusterScan_existing_cursor() {
        ClusterScanCursor mockCursor = Mockito.mock(ClusterScanCursor.class);
        when(mockCursor.getCursorHandle()).thenReturn("1");

        ClusterScanCursor mockResultCursor = Mockito.mock(ClusterScanCursor.class);
        when(mockResultCursor.getCursorHandle()).thenReturn("2");

        final Object[] result = new Object[] {mockResultCursor.getCursorHandle(), new Object[] {"foo"}};
        final CompletableFuture<Object[]> testResponse = CompletableFuture.completedFuture(result);
        when(commandManager.<Object[]>submitClusterScan(
                        eq(mockCursor), eq(ScanOptions.builder().build()), any()))
                .thenReturn(testResponse);

        CompletableFuture<Object[]> actualResponse = service.clusterScan(mockCursor);
        assertEquals(
                mockResultCursor.getCursorHandle(),
                ((ClusterScanCursor) actualResponse.get()[0]).getCursorHandle());
    }

    @SneakyThrows
    @Test
    public void clusterScan_new_cursor_options() {
        ClusterScanCursor mockCursor = Mockito.mock(ClusterScanCursor.class);
        when(mockCursor.getCursorHandle()).thenReturn("1");

        final Object[] result = new Object[] {mockCursor.getCursorHandle(), new Object[] {"foo"}};
        final CompletableFuture<Object[]> testResponse = CompletableFuture.completedFuture(result);
        when(commandManager.<Object[]>submitClusterScan(
                        eq(ClusterScanCursor.INITIAL_CURSOR_INSTANCE),
                        eq(
                                ScanOptions.builder()
                                        .matchPattern("key:*")
                                        .count(10L)
                                        .type(ScanOptions.ObjectType.STRING)
                                        .build()),
                        any()))
                .thenReturn(testResponse);

        final CompletableFuture<Object[]> actualResponse =
                service.clusterScan(
                        ClusterScanCursor.initalCursor(),
                        ScanOptions.builder()
                                .matchPattern("key:*")
                                .count(10L)
                                .type(ScanOptions.ObjectType.STRING)
                                .build());

        assertEquals(
                mockCursor.getCursorHandle(),
                ((ClusterScanCursor) actualResponse.get()[0]).getCursorHandle());
    }

    @SneakyThrows
    @Test
    public void clusterScan_existing_cursor_options() {
        ClusterScanCursor mockCursor = Mockito.mock(ClusterScanCursor.class);
        when(mockCursor.getCursorHandle()).thenReturn("1");

        ClusterScanCursor mockResultCursor = Mockito.mock(ClusterScanCursor.class);
        when(mockResultCursor.getCursorHandle()).thenReturn("2");

        final Object[] result = new Object[] {mockResultCursor.getCursorHandle(), new Object[] {"foo"}};
        final CompletableFuture<Object[]> testResponse = CompletableFuture.completedFuture(result);
        when(commandManager.<Object[]>submitClusterScan(
                        eq(mockCursor),
                        eq(
                                ScanOptions.builder()
                                        .matchPattern("key:*")
                                        .count(10L)
                                        .type(ScanOptions.ObjectType.STRING)
                                        .build()),
                        any()))
                .thenReturn(testResponse);

        CompletableFuture<Object[]> actualResponse =
                service.clusterScan(
                        mockCursor,
                        ScanOptions.builder()
                                .matchPattern("key:*")
                                .count(10L)
                                .type(ScanOptions.ObjectType.STRING)
                                .build());
        assertEquals(
                mockResultCursor.getCursorHandle(),
                ((ClusterScanCursor) actualResponse.get()[0]).getCursorHandle());
    }
}
