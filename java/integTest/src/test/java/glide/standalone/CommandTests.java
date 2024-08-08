/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.standalone;

import static glide.TestConfiguration.SERVER_VERSION;
import static glide.TestUtilities.assertDeepEquals;
import static glide.TestUtilities.checkFunctionListResponse;
import static glide.TestUtilities.checkFunctionListResponseBinary;
import static glide.TestUtilities.checkFunctionStatsBinaryResponse;
import static glide.TestUtilities.checkFunctionStatsResponse;
import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.createLuaLibWithLongRunningFunction;
import static glide.TestUtilities.generateLuaLibCode;
import static glide.TestUtilities.generateLuaLibCodeBinary;
import static glide.TestUtilities.getValueFromInfo;
import static glide.TestUtilities.parseInfoResponseToMap;
import static glide.TestUtilities.waitForNotBusy;
import static glide.api.BaseClient.OK;
import static glide.api.models.GlideString.gs;
import static glide.api.models.commands.FlushMode.ASYNC;
import static glide.api.models.commands.FlushMode.SYNC;
import static glide.api.models.commands.InfoOptions.Section.CLUSTER;
import static glide.api.models.commands.InfoOptions.Section.CPU;
import static glide.api.models.commands.InfoOptions.Section.EVERYTHING;
import static glide.api.models.commands.InfoOptions.Section.MEMORY;
import static glide.api.models.commands.InfoOptions.Section.SERVER;
import static glide.api.models.commands.InfoOptions.Section.STATS;
import static glide.api.models.commands.SortBaseOptions.Limit;
import static glide.api.models.commands.SortBaseOptions.OrderBy.ASC;
import static glide.api.models.commands.SortBaseOptions.OrderBy.DESC;
import static glide.api.models.commands.function.FunctionRestorePolicy.APPEND;
import static glide.api.models.commands.function.FunctionRestorePolicy.FLUSH;
import static glide.api.models.commands.function.FunctionRestorePolicy.REPLACE;
import static glide.api.models.commands.scan.ScanOptions.ObjectType.HASH;
import static glide.api.models.commands.scan.ScanOptions.ObjectType.SET;
import static glide.api.models.commands.scan.ScanOptions.ObjectType.STRING;
import static glide.cluster.CommandTests.DEFAULT_INFO_SECTIONS;
import static glide.cluster.CommandTests.EVERYTHING_INFO_SECTIONS;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.api.GlideClient;
import glide.api.models.GlideString;
import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.SortOptions;
import glide.api.models.commands.SortOptionsBinary;
import glide.api.models.commands.scan.ScanOptions;
import glide.api.models.exceptions.RequestException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10) // seconds
public class CommandTests {

    private static final String INITIAL_VALUE = "VALUE";

    private static GlideClient regularClient = null;

    @BeforeAll
    @SneakyThrows
    public static void init() {
        regularClient =
                GlideClient.createClient(commonClientConfig().requestTimeout(7000).build()).get();
    }

    @AfterAll
    @SneakyThrows
    public static void teardown() {
        regularClient.close();
    }

    @AfterEach
    @SneakyThrows
    public void cleanup() {
        regularClient.flushall().get();
    }

    @Test
    @SneakyThrows
    public void custom_command_info() {
        Object data = regularClient.customCommand(new String[] {"info"}).get();
        assertTrue(((String) data).contains("# Stats"));
    }

    @Test
    @SneakyThrows
    public void custom_command_del_returns_a_number() {
        String key = "custom_command_del_returns_a_number";
        regularClient.set(key, INITIAL_VALUE).get();
        var del = regularClient.customCommand(new String[] {"DEL", key}).get();
        assertEquals(1L, del);
        var data = regularClient.get(key).get();
        assertNull(data);
    }

    @Test
    @SneakyThrows
    public void ping() {
        String data = regularClient.ping().get();
        assertEquals("PONG", data);
    }

    @Test
    @SneakyThrows
    public void ping_with_message() {
        String data = regularClient.ping("H3LL0").get();
        assertEquals("H3LL0", data);
    }

    @Test
    @SneakyThrows
    public void ping_binary_with_message() {
        GlideString data = regularClient.ping(gs("H3LL0")).get();
        assertEquals(gs("H3LL0"), data);
    }

    @Test
    @SneakyThrows
    public void info_without_options() {
        String data = regularClient.info().get();
        for (String section : DEFAULT_INFO_SECTIONS) {
            assertTrue(data.contains("# " + section), "Section " + section + " is missing");
        }
    }

    @Test
    @SneakyThrows
    public void info_with_multiple_options() {
        InfoOptions.InfoOptionsBuilder builder = InfoOptions.builder().section(CLUSTER);
        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            builder.section(CPU).section(MEMORY);
        }
        InfoOptions options = builder.build();
        String data = regularClient.info(options).get();
        for (String section : options.toArgs()) {
            assertTrue(
                    data.toLowerCase().contains("# " + section.toLowerCase()),
                    "Section " + section + " is missing");
        }
    }

    @Test
    @SneakyThrows
    public void info_with_everything_option() {
        InfoOptions options = InfoOptions.builder().section(EVERYTHING).build();
        String data = regularClient.info(options).get();
        for (String section : EVERYTHING_INFO_SECTIONS) {
            assertTrue(data.contains("# " + section), "Section " + section + " is missing");
        }
    }

    @Test
    @SneakyThrows
    public void simple_select_test() {
        assertEquals(OK, regularClient.select(0).get());

        String key = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        assertEquals(OK, regularClient.set(key, value).get());

        assertEquals(OK, regularClient.select(1).get());
        assertNull(regularClient.get(key).get());

        assertEquals(OK, regularClient.select(0).get());
        assertEquals(value, regularClient.get(key).get());
    }

    @Test
    @SneakyThrows
    public void select_test_gives_error() {
        ExecutionException e =
                assertThrows(ExecutionException.class, () -> regularClient.select(-1).get());
        assertTrue(e.getCause() instanceof RequestException);
    }

    @Test
    @SneakyThrows
    public void move() {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String value1 = UUID.randomUUID().toString();
        String value2 = UUID.randomUUID().toString();
        String nonExistingKey = UUID.randomUUID().toString();
        assertEquals(OK, regularClient.select(0).get());

        assertEquals(false, regularClient.move(nonExistingKey, 1L).get());
        assertEquals(OK, regularClient.set(key1, value1).get());
        assertEquals(OK, regularClient.set(key2, value2).get());
        assertEquals(true, regularClient.move(key1, 1L).get());
        assertNull(regularClient.get(key1).get());

        assertEquals(OK, regularClient.select(1).get());
        assertEquals(value1, regularClient.get(key1).get());

        assertEquals(OK, regularClient.set(key2, value2).get());
        // Move does not occur because key2 already exists in DB 0
        assertEquals(false, regularClient.move(key2, 0).get());
        assertEquals(value2, regularClient.get(key2).get());

        // Incorrect argument - DB index must be non-negative
        ExecutionException e =
                assertThrows(ExecutionException.class, () -> regularClient.move(key1, -1L).get());
        assertTrue(e.getCause() instanceof RequestException);
    }

    @Test
    @SneakyThrows
    public void move_binary() {
        GlideString key1 = gs(UUID.randomUUID().toString());
        GlideString key2 = gs(UUID.randomUUID().toString());
        GlideString value1 = gs(UUID.randomUUID().toString());
        GlideString value2 = gs(UUID.randomUUID().toString());
        GlideString nonExistingKey = gs(UUID.randomUUID().toString());
        assertEquals(OK, regularClient.select(0).get());

        assertEquals(false, regularClient.move(nonExistingKey, 1L).get());
        assertEquals(OK, regularClient.set(key1, value1).get());
        assertEquals(OK, regularClient.set(key2, value2).get());
        assertEquals(true, regularClient.move(key1, 1L).get());
        assertNull(regularClient.get(key1).get());

        assertEquals(OK, regularClient.select(1).get());
        assertEquals(value1, regularClient.get(key1).get());

        assertEquals(OK, regularClient.set(key2, value2).get());
        // Move does not occur because key2 already exists in DB 0
        assertEquals(false, regularClient.move(key2, 0).get());
        assertEquals(value2, regularClient.get(key2).get());

        // Incorrect argument - DB index must be non-negative
        ExecutionException e =
                assertThrows(ExecutionException.class, () -> regularClient.move(key1, -1L).get());
        assertTrue(e.getCause() instanceof RequestException);
    }

    @Test
    @SneakyThrows
    public void clientId() {
        var id = regularClient.clientId().get();
        assertTrue(id > 0);
    }

    @Test
    @SneakyThrows
    public void clientGetName() {
        // TODO replace with the corresponding command once implemented
        regularClient.customCommand(new String[] {"client", "setname", "clientGetName"}).get();

        var name = regularClient.clientGetName().get();

        assertEquals("clientGetName", name);
    }

    @Test
    @SneakyThrows
    public void config_reset_stat() {
        String data = regularClient.info(InfoOptions.builder().section(STATS).build()).get();
        long value_before = getValueFromInfo(data, "total_net_input_bytes");

        var result = regularClient.configResetStat().get();
        assertEquals(OK, result);

        data = regularClient.info(InfoOptions.builder().section(STATS).build()).get();
        long value_after = getValueFromInfo(data, "total_net_input_bytes");
        assertTrue(value_after < value_before);
    }

    @Test
    @SneakyThrows
    public void config_rewrite_non_existent_config_file() {
        var info = regularClient.info(InfoOptions.builder().section(SERVER).build()).get();
        var configFile = parseInfoResponseToMap(info).get("config_file");

        if (configFile.isEmpty()) {
            ExecutionException executionException =
                    assertThrows(ExecutionException.class, () -> regularClient.configRewrite().get());
            assertTrue(executionException.getCause() instanceof RequestException);
        } else {
            assertEquals(OK, regularClient.configRewrite().get());
        }
    }

    @Test
    @SneakyThrows
    public void configGet_with_no_args_returns_error() {
        var exception =
                assertThrows(
                        ExecutionException.class, () -> regularClient.configGet(new String[] {}).get());
        assertTrue(exception.getCause() instanceof RequestException);
        assertTrue(exception.getCause().getMessage().contains("wrong number of arguments"));
    }

    @Test
    @SneakyThrows
    public void configGet_with_wildcard() {
        var data = regularClient.configGet(new String[] {"*file"}).get();
        assertTrue(data.size() > 5);
        assertTrue(data.containsKey("pidfile"));
        assertTrue(data.containsKey("logfile"));
    }

    @Test
    @SneakyThrows
    public void configGet_with_multiple_params() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");
        var data = regularClient.configGet(new String[] {"pidfile", "logfile"}).get();
        assertAll(
                () -> assertEquals(2, data.size()),
                () -> assertTrue(data.containsKey("pidfile")),
                () -> assertTrue(data.containsKey("logfile")));
    }

    @Test
    @SneakyThrows
    public void configSet_with_unknown_parameter_returns_error() {
        var exception =
                assertThrows(
                        ExecutionException.class,
                        () -> regularClient.configSet(Map.of("Unknown Option", "Unknown Value")).get());
        assertTrue(exception.getCause() instanceof RequestException);
    }

    @Test
    @SneakyThrows
    public void configSet_a_parameter() {
        var oldValue = regularClient.configGet(new String[] {"maxclients"}).get().get("maxclients");

        var response = regularClient.configSet(Map.of("maxclients", "42")).get();
        assertEquals(OK, response);
        var newValue = regularClient.configGet(new String[] {"maxclients"}).get();
        assertEquals("42", newValue.get("maxclients"));

        response = regularClient.configSet(Map.of("maxclients", oldValue)).get();
        assertEquals(OK, response);
    }

    @SneakyThrows
    @Test
    public void echo() {
        String message = "GLIDE";
        String response = regularClient.echo(message).get();
        assertEquals(message, response);
        message = "";
        response = regularClient.echo(message).get();
        assertEquals(message, response);
    }

    @SneakyThrows
    @Test
    public void echo_gs() {
        byte[] message = {(byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x02};
        GlideString response = regularClient.echo(gs(message)).get();
        assertEquals(gs(message), response);
    }

    @Test
    @SneakyThrows
    public void time() {
        // Take the time now, convert to 10 digits and subtract 1 second
        long now = Instant.now().getEpochSecond() - 1L;
        String[] result = regularClient.time().get();

        assertEquals(2, result.length);

        assertTrue(
                Long.parseLong(result[0]) > now,
                "Time() result (" + result[0] + ") should be greater than now (" + now + ")");
        assertTrue(Long.parseLong(result[1]) < 1000000);
    }

    @Test
    @SneakyThrows
    public void lastsave() {
        long result = regularClient.lastsave().get();
        var yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        assertTrue(Instant.ofEpochSecond(result).isAfter(yesterday));
    }

    @Test
    @SneakyThrows
    public void lolwut_lolwut() {
        var response = regularClient.lolwut().get();
        System.out.printf("%nLOLWUT standalone client standard response%n%s%n", response);
        assertTrue(response.contains("Redis ver. " + SERVER_VERSION));

        response = regularClient.lolwut(new int[] {30, 4, 4}).get();
        System.out.printf(
                "%nLOLWUT standalone client standard response with params 30 4 4%n%s%n", response);
        assertTrue(response.contains("Redis ver. " + SERVER_VERSION));

        response = regularClient.lolwut(5).get();
        System.out.printf("%nLOLWUT standalone client ver 5 response%n%s%n", response);
        assertTrue(response.contains("Redis ver. " + SERVER_VERSION));

        response = regularClient.lolwut(6, new int[] {50, 20}).get();
        System.out.printf(
                "%nLOLWUT standalone client ver 6 response with params 50 20%n%s%n", response);
        assertTrue(response.contains("Redis ver. " + SERVER_VERSION));
    }

    @Test
    @SneakyThrows
    public void dbsize_and_flushdb() {
        assertEquals(OK, regularClient.flushall().get());
        assertEquals(OK, regularClient.select(0).get());

        // fill DB and check size
        int numKeys = 10;
        for (int i = 0; i < numKeys; i++) {
            assertEquals(OK, regularClient.set(UUID.randomUUID().toString(), "foo").get());
        }
        assertEquals(10L, regularClient.dbsize().get());

        // check another empty DB
        assertEquals(OK, regularClient.select(1).get());
        assertEquals(0L, regularClient.dbsize().get());

        // check non-empty
        assertEquals(OK, regularClient.set(UUID.randomUUID().toString(), "foo").get());
        assertEquals(1L, regularClient.dbsize().get());

        // flush and check again
        if (SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0")) {
            assertEquals(OK, regularClient.flushdb(SYNC).get());
        } else {
            var executionException =
                    assertThrows(ExecutionException.class, () -> regularClient.flushdb(SYNC).get());
            assertInstanceOf(RequestException.class, executionException.getCause());
            assertEquals(OK, regularClient.flushdb(ASYNC).get());
        }
        assertEquals(0L, regularClient.dbsize().get());

        // switch to DB 0 and flush and check
        assertEquals(OK, regularClient.select(0).get());
        assertEquals(10L, regularClient.dbsize().get());
        assertEquals(OK, regularClient.flushdb().get());
        assertEquals(0L, regularClient.dbsize().get());
    }

    @Test
    @SneakyThrows
    public void objectFreq() {
        String key = UUID.randomUUID().toString();
        String maxmemoryPolicy = "maxmemory-policy";
        String oldPolicy =
                regularClient.configGet(new String[] {maxmemoryPolicy}).get().get(maxmemoryPolicy);
        try {
            assertEquals(OK, regularClient.configSet(Map.of(maxmemoryPolicy, "allkeys-lfu")).get());
            assertEquals(OK, regularClient.set(key, "").get());
            assertTrue(regularClient.objectFreq(key).get() >= 0L);
        } finally {
            regularClient.configSet(Map.of(maxmemoryPolicy, oldPolicy)).get();
        }
    }

    @Test
    @SneakyThrows
    public void flushall() {
        if (SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0")) {
            assertEquals(OK, regularClient.flushall(SYNC).get());
        } else {
            var executionException =
                    assertThrows(ExecutionException.class, () -> regularClient.flushall(SYNC).get());
            assertInstanceOf(RequestException.class, executionException.getCause());
            assertEquals(OK, regularClient.flushall(ASYNC).get());
        }

        // TODO replace with KEYS command when implemented
        Object[] keysAfter = (Object[]) regularClient.customCommand(new String[] {"keys", "*"}).get();
        assertEquals(0, keysAfter.length);

        assertEquals(OK, regularClient.flushall().get());
        assertEquals(OK, regularClient.flushall(ASYNC).get());
    }

    @SneakyThrows
    @Test
    public void function_commands() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        assertEquals(OK, regularClient.functionFlush(SYNC).get());

        String libName = "mylib1c";
        String funcName = "myfunc1c";
        // function $funcName returns first argument
        String code = generateLuaLibCode(libName, Map.of(funcName, "return args[1]"), true);
        assertEquals(libName, regularClient.functionLoad(code, false).get());

        var functionResult =
                regularClient.fcall(funcName, new String[0], new String[] {"one", "two"}).get();
        assertEquals("one", functionResult);
        functionResult =
                regularClient.fcallReadOnly(funcName, new String[0], new String[] {"one", "two"}).get();
        assertEquals("one", functionResult);

        var flist = regularClient.functionList(false).get();
        var expectedDescription =
                new HashMap<String, String>() {
                    {
                        put(funcName, null);
                    }
                };
        var expectedFlags =
                new HashMap<String, Set<String>>() {
                    {
                        put(funcName, Set.of("no-writes"));
                    }
                };
        checkFunctionListResponse(flist, libName, expectedDescription, expectedFlags, Optional.empty());

        flist = regularClient.functionList(true).get();
        checkFunctionListResponse(
                flist, libName, expectedDescription, expectedFlags, Optional.of(code));

        // re-load library without overwriting
        var executionException =
                assertThrows(ExecutionException.class, () -> regularClient.functionLoad(code, false).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        assertTrue(
                executionException.getMessage().contains("Library '" + libName + "' already exists"));

        // re-load library with overwriting
        assertEquals(libName, regularClient.functionLoad(code, true).get());
        String newFuncName = "myfunc2c";
        // function $funcName returns first argument
        // function $newFuncName returns argument array len
        String newCode =
                generateLuaLibCode(
                        libName, Map.of(funcName, "return args[1]", newFuncName, "return #args"), true);
        assertEquals(libName, regularClient.functionLoad(newCode, true).get());

        // load new lib and delete it - first lib remains loaded
        String anotherLib = generateLuaLibCode("anotherLib", Map.of("anotherFunc", ""), false);
        assertEquals("anotherLib", regularClient.functionLoad(anotherLib, true).get());
        assertEquals(OK, regularClient.functionDelete("anotherLib").get());

        // delete missing lib returns a error
        executionException =
                assertThrows(
                        ExecutionException.class, () -> regularClient.functionDelete("anotherLib").get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        assertTrue(executionException.getMessage().contains("Library not found"));

        flist = regularClient.functionList(libName, false).get();
        expectedDescription.put(newFuncName, null);
        expectedFlags.put(newFuncName, Set.of("no-writes"));
        checkFunctionListResponse(flist, libName, expectedDescription, expectedFlags, Optional.empty());

        flist = regularClient.functionList(libName, true).get();
        checkFunctionListResponse(
                flist, libName, expectedDescription, expectedFlags, Optional.of(newCode));

        functionResult =
                regularClient.fcall(newFuncName, new String[0], new String[] {"one", "two"}).get();
        assertEquals(2L, functionResult);
        functionResult =
                regularClient.fcallReadOnly(newFuncName, new String[0], new String[] {"one", "two"}).get();
        assertEquals(2L, functionResult);

        assertEquals(OK, regularClient.functionFlush(ASYNC).get());
    }

    @SneakyThrows
    @Test
    public void function_commands_binary() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        assertEquals(OK, regularClient.functionFlush(SYNC).get());

        GlideString libName = gs("mylib1c");
        GlideString funcName = gs("myfunc1c");
        // function $funcName returns first argument
        GlideString code =
                generateLuaLibCodeBinary(libName, Map.of(funcName, gs("return args[1]")), true);
        assertEquals(libName, regularClient.functionLoad(code, false).get());

        var functionResult =
                regularClient
                        .fcall(funcName, new GlideString[0], new GlideString[] {gs("one"), gs("two")})
                        .get();
        assertEquals(gs("one"), functionResult);
        functionResult =
                regularClient
                        .fcallReadOnly(funcName, new GlideString[0], new GlideString[] {gs("one"), gs("two")})
                        .get();
        assertEquals(gs("one"), functionResult);

        var flist = regularClient.functionListBinary(false).get();
        var expectedDescription =
                new HashMap<GlideString, GlideString>() {
                    {
                        put(funcName, null);
                    }
                };
        var expectedFlags =
                new HashMap<GlideString, Set<GlideString>>() {
                    {
                        put(funcName, Set.of(gs("no-writes")));
                    }
                };
        checkFunctionListResponseBinary(
                flist, libName, expectedDescription, expectedFlags, Optional.empty());

        flist = regularClient.functionListBinary(true).get();
        checkFunctionListResponseBinary(
                flist, libName, expectedDescription, expectedFlags, Optional.of(code));

        // re-load library without overwriting
        var executionException =
                assertThrows(ExecutionException.class, () -> regularClient.functionLoad(code, false).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        assertTrue(
                executionException.getMessage().contains("Library '" + libName + "' already exists"));

        // re-load library with overwriting
        assertEquals(libName, regularClient.functionLoad(code, true).get());
        GlideString newFuncName = gs("myfunc2c");
        // function $funcName returns first argument
        // function $newFuncName returns argument array len
        GlideString newCode =
                generateLuaLibCodeBinary(
                        libName, Map.of(funcName, gs("return args[1]"), newFuncName, gs("return #args")), true);
        assertEquals(libName, regularClient.functionLoad(newCode, true).get());

        // load new lib and delete it - first lib remains loaded
        GlideString anotherLib =
                generateLuaLibCodeBinary(gs("anotherLib"), Map.of(gs("anotherFunc"), gs("")), false);
        assertEquals(gs("anotherLib"), regularClient.functionLoad(anotherLib, true).get());
        assertEquals(OK, regularClient.functionDelete(gs("anotherLib")).get());

        // delete missing lib returns a error
        executionException =
                assertThrows(
                        ExecutionException.class, () -> regularClient.functionDelete(gs("anotherLib")).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        assertTrue(executionException.getMessage().contains("Library not found"));

        flist = regularClient.functionListBinary(libName, false).get();
        expectedDescription.put(newFuncName, null);
        expectedFlags.put(newFuncName, Set.of(gs("no-writes")));
        checkFunctionListResponseBinary(
                flist, libName, expectedDescription, expectedFlags, Optional.empty());

        flist = regularClient.functionListBinary(libName, true).get();
        checkFunctionListResponseBinary(
                flist, libName, expectedDescription, expectedFlags, Optional.of(newCode));

        functionResult =
                regularClient
                        .fcall(newFuncName, new GlideString[0], new GlideString[] {gs("one"), gs("two")})
                        .get();
        assertEquals(2L, functionResult);
        functionResult =
                regularClient
                        .fcallReadOnly(
                                newFuncName, new GlideString[0], new GlideString[] {gs("one"), gs("two")})
                        .get();
        assertEquals(2L, functionResult);

        assertEquals(OK, regularClient.functionFlush(ASYNC).get());
    }

    @Test
    @SneakyThrows
    public void copy() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0"), "This feature added in version 6.2.0");
        // setup
        String source = "{key}-1" + UUID.randomUUID();
        String destination = "{key}-2" + UUID.randomUUID();
        long index1 = 1;
        long index2 = 2;

        try {
            // neither key exists, returns false
            assertFalse(regularClient.copy(source, destination, index1, false).get());

            // source exists, destination does not
            regularClient.set(source, "one").get();
            assertTrue(regularClient.copy(source, destination, index1, false).get());
            regularClient.select(1).get();
            assertEquals("one", regularClient.get(destination).get());

            // new value for source key
            regularClient.select(0).get();
            regularClient.set(source, "two").get();

            // no REPLACE, copying to existing key on DB 0&1, non-existing key on DB 2
            assertFalse(regularClient.copy(source, destination, index1, false).get());
            assertTrue(regularClient.copy(source, destination, index2, false).get());

            // new value only gets copied to DB 2
            regularClient.select(1).get();
            assertEquals("one", regularClient.get(destination).get());
            regularClient.select(2).get();
            assertEquals("two", regularClient.get(destination).get());

            // both exists, with REPLACE, when value isn't the same, source always get copied to
            // destination
            regularClient.select(0).get();
            assertTrue(regularClient.copy(source, destination, index1, true).get());
            regularClient.select(1).get();
            assertEquals("two", regularClient.get(destination).get());
        }

        // switching back to db 0
        finally {
            regularClient.select(0).get();
        }
    }

    @Timeout(20)
    @Test
    @SneakyThrows
    public void functionKill_no_write() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        String libName = "functionKill_no_write";
        String funcName = "deadlock";
        String code = createLuaLibWithLongRunningFunction(libName, funcName, 6, true);

        assertEquals(OK, regularClient.functionFlush(SYNC).get());

        // nothing to kill
        var exception =
                assertThrows(ExecutionException.class, () -> regularClient.functionKill().get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().toLowerCase().contains("notbusy"));

        // load the lib
        assertEquals(libName, regularClient.functionLoad(code, true).get());

        try (var testClient =
                GlideClient.createClient(commonClientConfig().requestTimeout(10000).build()).get()) {
            try {
                // call the function without await
                testClient.fcall(funcName);

                Thread.sleep(1000);

                // Run FKILL until it returns OK
                boolean functionKilled = false;
                int timeout = 4000; // ms
                while (timeout >= 0) {
                    try {
                        assertEquals(OK, regularClient.functionKill().get());
                        functionKilled = true;
                        break;
                    } catch (RequestException ignored) {
                    }
                    Thread.sleep(500);
                    timeout -= 500;
                }

                assertTrue(functionKilled);
            } finally {
                waitForNotBusy(regularClient);
            }
        }
    }

    @Timeout(20)
    @Test
    @SneakyThrows
    public void functionKillBinary_no_write() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        GlideString libName = gs("functionKillBinary_no_write");
        GlideString funcName = gs("binary_deadlock");
        GlideString code =
                gs(createLuaLibWithLongRunningFunction(libName.toString(), funcName.toString(), 6, true));

        assertEquals(OK, regularClient.functionFlush(SYNC).get());

        // nothing to kill
        var exception =
                assertThrows(ExecutionException.class, () -> regularClient.functionKill().get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().toLowerCase().contains("notbusy"));

        // load the lib
        assertEquals(libName, regularClient.functionLoad(code, true).get());

        try (var testClient =
                GlideClient.createClient(commonClientConfig().requestTimeout(10000).build()).get()) {
            try {
                // call the function without await
                testClient.fcall(funcName);

                Thread.sleep(1000);

                // Run FKILL until it returns OK
                boolean functionKilled = false;
                int timeout = 4000; // ms
                while (timeout >= 0) {
                    try {
                        assertEquals(OK, regularClient.functionKill().get());
                        functionKilled = true;
                        break;
                    } catch (RequestException ignored) {
                    }
                    Thread.sleep(500);
                    timeout -= 500;
                }

                assertTrue(functionKilled);
            } finally {
                waitForNotBusy(regularClient);
            }
        }
    }

    @Timeout(20)
    @Test
    @SneakyThrows
    public void functionKill_write_function() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        String libName = "functionKill_write_function";
        String funcName = "deadlock_write_function";
        String key = libName;
        String code = createLuaLibWithLongRunningFunction(libName, funcName, 6, false);
        CompletableFuture<Object> promise = new CompletableFuture<>();
        promise.complete(null);

        assertEquals(OK, regularClient.functionFlush(SYNC).get());

        // nothing to kill
        var exception =
                assertThrows(ExecutionException.class, () -> regularClient.functionKill().get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().toLowerCase().contains("notbusy"));

        // load the lib
        assertEquals(libName, regularClient.functionLoad(code, true).get());

        try (var testClient =
                GlideClient.createClient(commonClientConfig().requestTimeout(10000).build()).get()) {
            try {
                // call the function without await
                promise = testClient.fcall(funcName, new String[] {key}, new String[0]);

                Thread.sleep(1000);

                boolean foundUnkillable = false;
                int timeout = 4000; // ms
                while (timeout >= 0) {
                    try {
                        // valkey kills a function with 5 sec delay
                        // but this will always throw an error in the test
                        regularClient.functionKill().get();
                    } catch (ExecutionException executionException) {
                        // looking for an error with "unkillable" in the message
                        // at that point we can break the loop
                        if (executionException.getCause() instanceof RequestException
                                && executionException.getMessage().toLowerCase().contains("unkillable")) {
                            foundUnkillable = true;
                            break;
                        }
                    }
                    Thread.sleep(500);
                    timeout -= 500;
                }
                assertTrue(foundUnkillable);

            } finally {
                // If function wasn't killed, and it didn't time out - it blocks the server and cause rest
                // test to fail.
                // wait for the function to complete (we cannot kill it)
                try {
                    promise.get();
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Timeout(20)
    @Test
    @SneakyThrows
    public void functionKillBinary_write_function() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        GlideString libName = gs("functionKill_write_function");
        GlideString funcName = gs("deadlock_write_function");
        GlideString key = libName;
        GlideString code =
                gs(createLuaLibWithLongRunningFunction(libName.toString(), funcName.toString(), 6, false));
        CompletableFuture<Object> promise = new CompletableFuture<>();
        promise.complete(null);

        assertEquals(OK, regularClient.functionFlush(SYNC).get());

        // nothing to kill
        var exception =
                assertThrows(ExecutionException.class, () -> regularClient.functionKill().get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().toLowerCase().contains("notbusy"));

        // load the lib
        assertEquals(libName, regularClient.functionLoad(code, true).get());

        try (var testClient =
                GlideClient.createClient(commonClientConfig().requestTimeout(10000).build()).get()) {
            try {
                // call the function without await
                promise = testClient.fcall(funcName, new GlideString[] {key}, new GlideString[0]);

                Thread.sleep(1000);

                boolean foundUnkillable = false;
                int timeout = 4000; // ms
                while (timeout >= 0) {
                    try {
                        // valkey kills a function with 5 sec delay
                        // but this will always throw an error in the test
                        regularClient.functionKill().get();
                    } catch (ExecutionException executionException) {
                        // looking for an error with "unkillable" in the message
                        // at that point we can break the loop
                        if (executionException.getCause() instanceof RequestException
                                && executionException.getMessage().toLowerCase().contains("unkillable")) {
                            foundUnkillable = true;
                            break;
                        }
                    }
                    Thread.sleep(500);
                    timeout -= 500;
                }
                assertTrue(foundUnkillable);

            } finally {
                // If function wasn't killed, and it didn't time out - it blocks the server and cause rest
                // test to fail.
                // wait for the function to complete (we cannot kill it)
                try {
                    promise.get();
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Test
    @SneakyThrows
    public void functionStats() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        String libName = "functionStats";
        String funcName = libName;
        assertEquals(OK, regularClient.functionFlush(SYNC).get());

        // function $funcName returns first argument
        String code = generateLuaLibCode(libName, Map.of(funcName, "return args[1]"), false);
        assertEquals(libName, regularClient.functionLoad(code, true).get());

        var response = regularClient.functionStats().get();
        checkFunctionStatsResponse(response, new String[0], 1, 1);

        code =
                generateLuaLibCode(
                        libName + "_2",
                        Map.of(funcName + "_2", "return 'OK'", funcName + "_3", "return 42"),
                        false);
        assertEquals(libName + "_2", regularClient.functionLoad(code, true).get());

        response = regularClient.functionStats().get();
        checkFunctionStatsResponse(response, new String[0], 2, 3);

        assertEquals(OK, regularClient.functionFlush(SYNC).get());

        response = regularClient.functionStats().get();
        checkFunctionStatsResponse(response, new String[0], 0, 0);
    }

    @Test
    @SneakyThrows
    public void functionStatsBinary() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        GlideString libName = gs("functionStats");
        GlideString funcName = libName;
        assertEquals(OK, regularClient.functionFlush(SYNC).get());

        // function $funcName returns first argument
        GlideString code =
                generateLuaLibCodeBinary(libName, Map.of(funcName, gs("return args[1]")), false);
        assertEquals(libName, regularClient.functionLoad(code, true).get());

        var response = regularClient.functionStatsBinary().get();
        checkFunctionStatsBinaryResponse(response, new GlideString[0], 1, 1);

        code =
                generateLuaLibCodeBinary(
                        gs(libName.toString() + "_2"),
                        Map.of(
                                gs(funcName.toString() + "_2"),
                                gs("return 'OK'"),
                                gs(funcName.toString() + "_3"),
                                gs("return 42")),
                        false);
        assertEquals(gs(libName.toString() + "_2"), regularClient.functionLoad(code, true).get());

        response = regularClient.functionStatsBinary().get();
        checkFunctionStatsBinaryResponse(response, new GlideString[0], 2, 3);

        assertEquals(OK, regularClient.functionFlush(SYNC).get());

        response = regularClient.functionStatsBinary().get();
        checkFunctionStatsBinaryResponse(response, new GlideString[0], 0, 0);
    }

    @Test
    @SneakyThrows
    public void function_dump_and_restore() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        assertEquals(OK, regularClient.functionFlush(SYNC).get());

        // dumping an empty lib
        byte[] emptyDump = regularClient.functionDump().get();
        assertTrue(emptyDump.length > 0);

        String name1 = "Foster";
        String name2 = "Dogster";

        // function $name1 returns first argument
        // function $name2 returns argument array len
        String code =
                generateLuaLibCode(name1, Map.of(name1, "return args[1]", name2, "return #args"), false);
        assertEquals(name1, regularClient.functionLoad(code, true).get());
        var flist = regularClient.functionList(true).get();

        final byte[] dump = regularClient.functionDump().get();

        // restore without cleaning the lib and/or overwrite option causes an error
        var executionException =
                assertThrows(ExecutionException.class, () -> regularClient.functionRestore(dump).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        assertTrue(executionException.getMessage().contains("Library " + name1 + " already exists"));

        // APPEND policy also fails for the same reason (name collision)
        executionException =
                assertThrows(
                        ExecutionException.class, () -> regularClient.functionRestore(dump, APPEND).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        assertTrue(executionException.getMessage().contains("Library " + name1 + " already exists"));

        // REPLACE policy succeeds
        assertEquals(OK, regularClient.functionRestore(dump, REPLACE).get());
        // but nothing changed - all code overwritten
        assertDeepEquals(flist, regularClient.functionList(true).get());

        // create lib with another name, but with the same function names
        assertEquals(OK, regularClient.functionFlush(SYNC).get());
        code = generateLuaLibCode(name2, Map.of(name1, "return args[1]", name2, "return #args"), false);
        assertEquals(name2, regularClient.functionLoad(code, true).get());

        // REPLACE policy now fails due to a name collision
        executionException =
                assertThrows(
                        ExecutionException.class, () -> regularClient.functionRestore(dump, REPLACE).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        // valkey checks names in random order and blames on first collision
        assertTrue(
                executionException.getMessage().contains("Function " + name1 + " already exists")
                        || executionException.getMessage().contains("Function " + name2 + " already exists"));

        // FLUSH policy succeeds, but deletes the second lib
        assertEquals(OK, regularClient.functionRestore(dump, FLUSH).get());
        assertDeepEquals(flist, regularClient.functionList(true).get());

        // call restored functions
        assertEquals(
                "meow", regularClient.fcall(name1, new String[0], new String[] {"meow", "woem"}).get());
        assertEquals(
                2L, regularClient.fcall(name2, new String[0], new String[] {"meow", "woem"}).get());
    }

    @SneakyThrows
    @Test
    public void randomkey() {
        String key1 = "{key}" + UUID.randomUUID();
        String key2 = "{key}" + UUID.randomUUID();

        assertEquals(OK, regularClient.set(key1, "a").get());
        assertEquals(OK, regularClient.set(key2, "b").get());

        String randomKey = regularClient.randomKey().get();
        assertEquals(1L, regularClient.exists(new String[] {randomKey}).get());

        // no keys in database
        assertEquals(OK, regularClient.flushall().get());
        assertNull(regularClient.randomKey().get());
    }

    @SneakyThrows
    @Test
    public void randomKeyBinary() {
        GlideString key1 = gs("{key}" + UUID.randomUUID());
        GlideString key2 = gs("{key}" + UUID.randomUUID());

        assertEquals(OK, regularClient.set(key1, gs("a")).get());
        assertEquals(OK, regularClient.set(key2, gs("b")).get());

        GlideString randomKeyBinary = regularClient.randomKeyBinary().get();
        assertEquals(1L, regularClient.exists(new GlideString[] {randomKeyBinary}).get());

        // no keys in database
        assertEquals(OK, regularClient.flushall().get());
        assertNull(regularClient.randomKeyBinary().get());
    }

    @Test
    @SneakyThrows
    public void sort() {
        String setKey1 = "setKey1";
        String setKey2 = "setKey2";
        String setKey3 = "setKey3";
        String setKey4 = "setKey4";
        String setKey5 = "setKey5";
        String[] setKeys = new String[] {setKey1, setKey2, setKey3, setKey4, setKey5};
        String listKey = "listKey";
        String storeKey = "storeKey";
        String nameField = "name";
        String ageField = "age";
        String[] names = new String[] {"Alice", "Bob", "Charlie", "Dave", "Eve"};
        String[] namesSortedByAge = new String[] {"Dave", "Bob", "Alice", "Charlie", "Eve"};
        String[] ages = new String[] {"30", "25", "35", "20", "40"};
        String[] userIDs = new String[] {"3", "1", "5", "4", "2"};
        String namePattern = "setKey*->name";
        String agePattern = "setKey*->age";
        String missingListKey = "100000";

        for (int i = 0; i < setKeys.length; i++) {
            assertEquals(
                    2, regularClient.hset(setKeys[i], Map.of(nameField, names[i], ageField, ages[i])).get());
        }

        assertEquals(5, regularClient.rpush(listKey, userIDs).get());
        assertArrayEquals(
                new String[] {"Alice", "Bob"},
                regularClient
                        .sort(
                                listKey,
                                SortOptions.builder().limit(new Limit(0L, 2L)).getPattern(namePattern).build())
                        .get());
        assertArrayEquals(
                new String[] {"Eve", "Dave"},
                regularClient
                        .sort(
                                listKey,
                                SortOptions.builder()
                                        .limit(new Limit(0L, 2L))
                                        .orderBy(DESC)
                                        .getPattern(namePattern)
                                        .build())
                        .get());
        assertArrayEquals(
                new String[] {"Eve", "40", "Charlie", "35"},
                regularClient
                        .sort(
                                listKey,
                                SortOptions.builder()
                                        .limit(new Limit(0L, 2L))
                                        .orderBy(DESC)
                                        .byPattern(agePattern)
                                        .getPatterns(List.of(namePattern, agePattern))
                                        .build())
                        .get());

        // Non-existent key in the BY pattern will result in skipping the sorting operation
        assertArrayEquals(
                userIDs,
                regularClient.sort(listKey, SortOptions.builder().byPattern("noSort").build()).get());

        // Non-existent key in the GET pattern results in nulls
        assertArrayEquals(
                new String[] {null, null, null, null, null},
                regularClient
                        .sort(listKey, SortOptions.builder().alpha().getPattern("missing").build())
                        .get());

        // Missing key in the set
        assertEquals(6, regularClient.lpush(listKey, new String[] {missingListKey}).get());
        assertArrayEquals(
                new String[] {null, "Dave", "Bob", "Alice", "Charlie", "Eve"},
                regularClient
                        .sort(
                                listKey,
                                SortOptions.builder().byPattern(agePattern).getPattern(namePattern).build())
                        .get());
        assertEquals(missingListKey, regularClient.lpop(listKey).get());

        // SORT_RO
        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            assertArrayEquals(
                    new String[] {"Alice", "Bob"},
                    regularClient
                            .sortReadOnly(
                                    listKey,
                                    SortOptions.builder().limit(new Limit(0L, 2L)).getPattern(namePattern).build())
                            .get());
            assertArrayEquals(
                    new String[] {"Eve", "Dave"},
                    regularClient
                            .sortReadOnly(
                                    listKey,
                                    SortOptions.builder()
                                            .limit(new Limit(0L, 2L))
                                            .orderBy(DESC)
                                            .getPattern(namePattern)
                                            .build())
                            .get());
            assertArrayEquals(
                    new String[] {"Eve", "40", "Charlie", "35"},
                    regularClient
                            .sortReadOnly(
                                    listKey,
                                    SortOptions.builder()
                                            .limit(new Limit(0L, 2L))
                                            .orderBy(DESC)
                                            .byPattern(agePattern)
                                            .getPatterns(List.of(namePattern, agePattern))
                                            .build())
                            .get());

            // Non-existent key in the BY pattern will result in skipping the sorting operation
            assertArrayEquals(
                    userIDs,
                    regularClient
                            .sortReadOnly(listKey, SortOptions.builder().byPattern("noSort").build())
                            .get());

            // Non-existent key in the GET pattern results in nulls
            assertArrayEquals(
                    new String[] {null, null, null, null, null},
                    regularClient
                            .sortReadOnly(listKey, SortOptions.builder().alpha().getPattern("missing").build())
                            .get());

            assertArrayEquals(
                    namesSortedByAge,
                    regularClient
                            .sortReadOnly(
                                    listKey,
                                    SortOptions.builder().byPattern(agePattern).getPattern(namePattern).build())
                            .get());

            // Missing key in the set
            assertEquals(6, regularClient.lpush(listKey, new String[] {missingListKey}).get());
            assertArrayEquals(
                    new String[] {null, "Dave", "Bob", "Alice", "Charlie", "Eve"},
                    regularClient
                            .sortReadOnly(
                                    listKey,
                                    SortOptions.builder().byPattern(agePattern).getPattern(namePattern).build())
                            .get());
            assertEquals(missingListKey, regularClient.lpop(listKey).get());
        }

        // SORT with STORE
        assertEquals(
                5,
                regularClient
                        .sortStore(
                                listKey,
                                storeKey,
                                SortOptions.builder()
                                        .limit(new Limit(0L, -1L))
                                        .orderBy(ASC)
                                        .byPattern(agePattern)
                                        .getPattern(namePattern)
                                        .build())
                        .get());
        assertArrayEquals(namesSortedByAge, regularClient.lrange(storeKey, 0, -1).get());
        assertEquals(
                5,
                regularClient
                        .sortStore(
                                listKey,
                                storeKey,
                                SortOptions.builder().byPattern(agePattern).getPattern(namePattern).build())
                        .get());
        assertArrayEquals(namesSortedByAge, regularClient.lrange(storeKey, 0, -1).get());
    }

    @Test
    @SneakyThrows
    public void sort_binary() {
        GlideString setKey1 = gs("setKey1");
        GlideString setKey2 = gs("setKey2");
        GlideString setKey3 = gs("setKey3");
        GlideString setKey4 = gs("setKey4");
        GlideString setKey5 = gs("setKey5");
        GlideString[] setKeys = new GlideString[] {setKey1, setKey2, setKey3, setKey4, setKey5};
        GlideString listKey = gs("listKey");
        GlideString storeKey = gs("storeKey");
        GlideString nameField = gs("name");
        GlideString ageField = gs("age");
        GlideString[] names =
                new GlideString[] {gs("Alice"), gs("Bob"), gs("Charlie"), gs("Dave"), gs("Eve")};
        String[] namesSortedByAge = new String[] {"Dave", "Bob", "Alice", "Charlie", "Eve"};
        GlideString[] namesSortedByAge_gs =
                new GlideString[] {gs("Dave"), gs("Bob"), gs("Alice"), gs("Charlie"), gs("Eve")};
        GlideString[] ages = new GlideString[] {gs("30"), gs("25"), gs("35"), gs("20"), gs("40")};
        GlideString[] userIDs = new GlideString[] {gs("3"), gs("1"), gs("5"), gs("4"), gs("2")};
        GlideString namePattern = gs("setKey*->name");
        GlideString agePattern = gs("setKey*->age");
        GlideString missingListKey = gs("100000");

        for (int i = 0; i < setKeys.length; i++) {
            assertEquals(
                    2,
                    regularClient
                            .hset(
                                    setKeys[i].toString(),
                                    Map.of(
                                            nameField.toString(),
                                            names[i].toString(),
                                            ageField.toString(),
                                            ages[i].toString()))
                            .get());
        }

        assertEquals(5, regularClient.rpush(listKey, userIDs).get());
        assertArrayEquals(
                new GlideString[] {gs("Alice"), gs("Bob")},
                regularClient
                        .sort(
                                listKey,
                                SortOptionsBinary.builder()
                                        .limit(new Limit(0L, 2L))
                                        .getPattern(namePattern)
                                        .build())
                        .get());

        assertArrayEquals(
                new GlideString[] {gs("Eve"), gs("Dave")},
                regularClient
                        .sort(
                                listKey,
                                SortOptionsBinary.builder()
                                        .limit(new Limit(0L, 2L))
                                        .orderBy(DESC)
                                        .getPattern(namePattern)
                                        .build())
                        .get());
        assertArrayEquals(
                new GlideString[] {gs("Eve"), gs("40"), gs("Charlie"), gs("35")},
                regularClient
                        .sort(
                                listKey,
                                SortOptionsBinary.builder()
                                        .limit(new Limit(0L, 2L))
                                        .orderBy(DESC)
                                        .byPattern(agePattern)
                                        .getPatterns(List.of(namePattern, agePattern))
                                        .build())
                        .get());

        // Non-existent key in the BY pattern will result in skipping the sorting operation
        assertArrayEquals(
                userIDs,
                regularClient
                        .sort(listKey, SortOptionsBinary.builder().byPattern(gs("noSort")).build())
                        .get());

        // Non-existent key in the GET pattern results in nulls
        assertArrayEquals(
                new GlideString[] {null, null, null, null, null},
                regularClient
                        .sort(listKey, SortOptionsBinary.builder().alpha().getPattern(gs("missing")).build())
                        .get());

        // Missing key in the set
        assertEquals(6, regularClient.lpush(listKey, new GlideString[] {missingListKey}).get());
        assertArrayEquals(
                new GlideString[] {null, gs("Dave"), gs("Bob"), gs("Alice"), gs("Charlie"), gs("Eve")},
                regularClient
                        .sort(
                                listKey,
                                SortOptionsBinary.builder().byPattern(agePattern).getPattern(namePattern).build())
                        .get());
        assertEquals(missingListKey.toString(), regularClient.lpop(listKey.toString()).get());

        // SORT_RO
        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            assertArrayEquals(
                    new GlideString[] {gs("Alice"), gs("Bob")},
                    regularClient
                            .sortReadOnly(
                                    listKey,
                                    SortOptionsBinary.builder()
                                            .limit(new Limit(0L, 2L))
                                            .getPattern(namePattern)
                                            .build())
                            .get());
            assertArrayEquals(
                    new GlideString[] {gs("Eve"), gs("Dave")},
                    regularClient
                            .sortReadOnly(
                                    listKey,
                                    SortOptionsBinary.builder()
                                            .limit(new Limit(0L, 2L))
                                            .orderBy(DESC)
                                            .getPattern(namePattern)
                                            .build())
                            .get());
            assertArrayEquals(
                    new GlideString[] {gs("Eve"), gs("40"), gs("Charlie"), gs("35")},
                    regularClient
                            .sortReadOnly(
                                    listKey,
                                    SortOptionsBinary.builder()
                                            .limit(new Limit(0L, 2L))
                                            .orderBy(DESC)
                                            .byPattern(agePattern)
                                            .getPatterns(List.of(namePattern, agePattern))
                                            .build())
                            .get());

            // Non-existent key in the BY pattern will result in skipping the sorting operation
            assertArrayEquals(
                    userIDs,
                    regularClient
                            .sortReadOnly(listKey, SortOptionsBinary.builder().byPattern(gs("noSort")).build())
                            .get());

            // Non-existent key in the GET pattern results in nulls
            assertArrayEquals(
                    new GlideString[] {null, null, null, null, null},
                    regularClient
                            .sortReadOnly(
                                    listKey, SortOptionsBinary.builder().alpha().getPattern(gs("missing")).build())
                            .get());

            assertArrayEquals(
                    namesSortedByAge_gs,
                    regularClient
                            .sortReadOnly(
                                    listKey,
                                    SortOptionsBinary.builder().byPattern(agePattern).getPattern(namePattern).build())
                            .get());

            // Missing key in the set
            assertEquals(6, regularClient.lpush(listKey, new GlideString[] {missingListKey}).get());
            assertArrayEquals(
                    new GlideString[] {null, gs("Dave"), gs("Bob"), gs("Alice"), gs("Charlie"), gs("Eve")},
                    regularClient
                            .sortReadOnly(
                                    listKey,
                                    SortOptionsBinary.builder().byPattern(agePattern).getPattern(namePattern).build())
                            .get());
            assertEquals(missingListKey.toString(), regularClient.lpop(listKey.toString()).get());
        }

        // SORT with STORE
        assertEquals(
                5,
                regularClient
                        .sortStore(
                                listKey,
                                storeKey,
                                SortOptionsBinary.builder()
                                        .limit(new Limit(0L, -1L))
                                        .orderBy(ASC)
                                        .byPattern(agePattern)
                                        .getPattern(namePattern)
                                        .build())
                        .get());
        assertArrayEquals(namesSortedByAge, regularClient.lrange(storeKey.toString(), 0, -1).get());
        assertEquals(
                5,
                regularClient
                        .sortStore(
                                listKey,
                                storeKey,
                                SortOptionsBinary.builder().byPattern(agePattern).getPattern(namePattern).build())
                        .get());
        assertArrayEquals(namesSortedByAge, regularClient.lrange(storeKey.toString(), 0, -1).get());
    }

    @Test
    @SneakyThrows
    public void scan() {
        String initialCursor = "0";

        int numberKeys = 500;
        Map<String, String> keys = new HashMap<>();
        for (int i = 0; i < numberKeys; i++) {
            keys.put("{key}-" + i + "-" + UUID.randomUUID(), "{value}-" + i + "-" + UUID.randomUUID());
        }

        int resultCursorIndex = 0;
        int resultCollectionIndex = 1;

        // empty the database
        assertEquals(OK, regularClient.flushdb().get());

        // Empty return
        Object[] emptyResult = regularClient.scan(initialCursor).get();
        assertEquals(initialCursor, emptyResult[resultCursorIndex]);
        assertDeepEquals(new String[] {}, emptyResult[resultCollectionIndex]);

        // Negative cursor
        Object[] negativeResult = regularClient.scan("-1").get();
        assertEquals(initialCursor, negativeResult[resultCursorIndex]);
        assertDeepEquals(new String[] {}, negativeResult[resultCollectionIndex]);

        // Add keys to the database using mset
        regularClient.mset(keys).get();

        Object[] result;
        Object[] keysFound = new String[0];
        String resultCursor = "0";
        boolean isFirstLoop = true;
        do {
            result = regularClient.scan(resultCursor).get();
            resultCursor = result[resultCursorIndex].toString();
            Object[] resultKeys = (Object[]) result[resultCollectionIndex];
            keysFound = ArrayUtils.addAll(keysFound, resultKeys);

            if (isFirstLoop) {
                assertNotEquals("0", resultCursor);
                isFirstLoop = false;
            } else if (resultCursor.equals("0")) {
                break;
            }
        } while (!resultCursor.equals("0")); // 0 is returned for the cursor of the last iteration.

        // check that each key added to the database is found through the cursor
        Object[] finalKeysFound = keysFound;
        keys.forEach((key, value) -> assertTrue(ArrayUtils.contains(finalKeysFound, key)));
    }

    @Test
    @SneakyThrows
    public void scan_binary() {
        GlideString initialCursor = gs("0");

        int numberKeys = 500;
        Map<GlideString, GlideString> keys = new HashMap<>();
        for (int i = 0; i < numberKeys; i++) {
            keys.put(
                    gs("{key}-" + i + "-" + UUID.randomUUID()), gs("{value}-" + i + "-" + UUID.randomUUID()));
        }

        int resultCursorIndex = 0;
        int resultCollectionIndex = 1;

        // empty the database
        assertEquals(OK, regularClient.flushdb().get());

        // Empty return
        Object[] emptyResult = regularClient.scan(initialCursor).get();
        assertEquals(initialCursor, emptyResult[resultCursorIndex]);
        assertDeepEquals(new String[] {}, emptyResult[resultCollectionIndex]);

        // Negative cursor
        Object[] negativeResult = regularClient.scan(gs("-1")).get();
        assertEquals(initialCursor, negativeResult[resultCursorIndex]);
        assertDeepEquals(new String[] {}, negativeResult[resultCollectionIndex]);

        // Add keys to the database using mset
        regularClient.msetBinary(keys).get();

        Object[] result;
        Object[] keysFound = new GlideString[0];
        GlideString resultCursor = gs("0");
        boolean isFirstLoop = true;
        do {
            result = regularClient.scan(resultCursor).get();
            resultCursor = (GlideString) result[resultCursorIndex];
            Object[] resultKeys = (Object[]) result[resultCollectionIndex];
            keysFound = ArrayUtils.addAll(keysFound, resultKeys);

            if (isFirstLoop) {
                assertNotEquals(gs("0"), resultCursor);
                isFirstLoop = false;
            } else if (resultCursor.equals(gs("0"))) {
                break;
            }
        } while (!resultCursor.equals(gs("0"))); // 0 is returned for the cursor of the last iteration.

        // check that each key added to the database is found through the cursor
        Object[] finalKeysFound = keysFound;
        keys.forEach((key, value) -> assertTrue(ArrayUtils.contains(finalKeysFound, key)));
    }

    @Test
    @SneakyThrows
    public void scan_with_options() {
        String initialCursor = "0";
        String matchPattern = UUID.randomUUID().toString();

        int resultCursorIndex = 0;
        int resultCollectionIndex = 1;

        // Add string keys to the database using mset
        Map<String, String> stringKeys = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            stringKeys.put("{key}-" + i + "-" + matchPattern, "{value}-" + i + "-" + matchPattern);
        }
        regularClient.mset(stringKeys).get();

        // Add set keys to the database using sadd
        List<String> setKeys = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String key = "{key}-set-" + i + "-" + matchPattern;
            regularClient.sadd(
                    gs(key),
                    new GlideString[] {gs(UUID.randomUUID().toString()), gs(UUID.randomUUID().toString())});
            setKeys.add(key);
        }

        // Empty return - match a random UUID
        ScanOptions options = ScanOptions.builder().matchPattern("*" + UUID.randomUUID()).build();
        Object[] emptyResult = regularClient.scan(initialCursor, options).get();
        assertNotEquals(initialCursor, emptyResult[resultCursorIndex]);
        assertDeepEquals(new String[] {}, emptyResult[resultCollectionIndex]);

        // Negative cursor
        Object[] negativeResult = regularClient.scan("-1", options).get();
        assertEquals(initialCursor, negativeResult[resultCursorIndex]);
        assertDeepEquals(new String[] {}, negativeResult[resultCollectionIndex]);

        // scan for strings by match pattern:
        options =
                ScanOptions.builder().matchPattern("*" + matchPattern).count(100L).type(STRING).build();
        Object[] result;
        Object[] keysFound = new String[0];
        String resultCursor = "0";
        do {
            result = regularClient.scan(resultCursor, options).get();
            resultCursor = result[resultCursorIndex].toString();
            Object[] resultKeys = (Object[]) result[resultCollectionIndex];
            keysFound = ArrayUtils.addAll(keysFound, resultKeys);
        } while (!resultCursor.equals("0")); // 0 is returned for the cursor of the last iteration.

        // check that each key added to the database is found through the cursor
        Object[] finalKeysFound = keysFound;
        stringKeys.forEach((key, value) -> assertTrue(ArrayUtils.contains(finalKeysFound, key)));

        // scan for sets by match pattern:
        options = ScanOptions.builder().matchPattern("*" + matchPattern).count(100L).type(SET).build();
        Object[] setResult;
        Object[] setsFound = new String[0];
        String setCursor = "0";
        do {
            setResult = regularClient.scan(setCursor, options).get();
            setCursor = setResult[resultCursorIndex].toString();
            Object[] resultKeys = (Object[]) setResult[resultCollectionIndex];
            setsFound = ArrayUtils.addAll(setsFound, resultKeys);
        } while (!setCursor.equals("0")); // 0 is returned for the cursor of the last iteration.

        // check that each key added to the database is found through the cursor
        Object[] finalSetsFound = setsFound;
        setKeys.forEach(k -> assertTrue(ArrayUtils.contains(finalSetsFound, k)));

        // scan for hashes by match pattern:
        // except in this case, we should never find anything
        options = ScanOptions.builder().matchPattern("*" + matchPattern).count(100L).type(HASH).build();
        String hashCursor = "0";
        do {
            Object[] hashResult = regularClient.scan(hashCursor, options).get();
            hashCursor = hashResult[resultCursorIndex].toString();
            assertEquals(0, ((Object[]) hashResult[resultCollectionIndex]).length);
        } while (!hashCursor.equals("0")); // 0 is returned for the cursor of the last iteration.
    }

    @Test
    @SneakyThrows
    public void scan_binary_with_options() {
        GlideString initialCursor = gs("0");
        String matchPattern = UUID.randomUUID().toString();

        int resultCursorIndex = 0;
        int resultCollectionIndex = 1;

        // Add string keys to the database using mset
        Map<GlideString, GlideString> stringKeys = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            stringKeys.put(
                    gs("{key}-" + i + "-" + matchPattern), gs("{value}-" + i + "-" + matchPattern));
        }
        regularClient.msetBinary(stringKeys).get();

        // Add set keys to the database using sadd
        List<GlideString> setKeys = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            GlideString key = gs("{key}-set-" + i + "-" + matchPattern);
            regularClient.sadd(
                    key,
                    new GlideString[] {gs(UUID.randomUUID().toString()), gs(UUID.randomUUID().toString())});
            setKeys.add(key);
        }

        // Empty return - match a random UUID
        ScanOptions options = ScanOptions.builder().matchPattern("*" + UUID.randomUUID()).build();
        Object[] emptyResult = regularClient.scan(initialCursor, options).get();
        assertNotEquals(initialCursor, emptyResult[resultCursorIndex]);
        assertDeepEquals(new String[] {}, emptyResult[resultCollectionIndex]);

        // Negative cursor
        Object[] negativeResult = regularClient.scan(gs("-1"), options).get();
        assertEquals(initialCursor, negativeResult[resultCursorIndex]);
        assertDeepEquals(new String[] {}, negativeResult[resultCollectionIndex]);

        // scan for strings by match pattern:
        options =
                ScanOptions.builder().matchPattern("*" + matchPattern).count(100L).type(STRING).build();
        Object[] result;
        Object[] keysFound = new GlideString[0];
        GlideString resultCursor = gs("0");
        do {
            result = regularClient.scan(resultCursor, options).get();
            resultCursor = (GlideString) result[resultCursorIndex];
            Object[] resultKeys = (Object[]) result[resultCollectionIndex];
            keysFound = ArrayUtils.addAll(keysFound, resultKeys);
        } while (!resultCursor.equals(gs("0"))); // 0 is returned for the cursor of the last iteration.

        // check that each key added to the database is found through the cursor
        Object[] finalKeysFound = keysFound;
        stringKeys.forEach((key, value) -> assertTrue(ArrayUtils.contains(finalKeysFound, key)));

        // scan for sets by match pattern:
        options = ScanOptions.builder().matchPattern("*" + matchPattern).count(100L).type(SET).build();
        Object[] setResult;
        Object[] setsFound = new GlideString[0];
        GlideString setCursor = gs("0");
        do {
            setResult = regularClient.scan(setCursor, options).get();
            setCursor = (GlideString) setResult[resultCursorIndex];
            Object[] resultKeys = (Object[]) setResult[resultCollectionIndex];
            setsFound = ArrayUtils.addAll(setsFound, resultKeys);
        } while (!setCursor.equals(gs("0"))); // 0 is returned for the cursor of the last iteration.

        // check that each key added to the database is found through the cursor
        Object[] finalSetsFound = setsFound;
        setKeys.forEach(k -> assertTrue(ArrayUtils.contains(finalSetsFound, k)));

        // scan for hashes by match pattern:
        // except in this case, we should never find anything
        options = ScanOptions.builder().matchPattern("*" + matchPattern).count(100L).type(HASH).build();
        GlideString hashCursor = gs("0");
        do {
            Object[] hashResult = regularClient.scan(hashCursor, options).get();
            hashCursor = (GlideString) hashResult[resultCursorIndex];
            assertEquals(0, ((Object[]) hashResult[resultCollectionIndex]).length);
        } while (!hashCursor.equals(gs("0"))); // 0 is returned for the cursor of the last iteration.
    }
}
