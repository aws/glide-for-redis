/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static glide.api.BaseClient.OK;
import static glide.api.models.commands.SetOptions.ConditionalSet.ONLY_IF_DOES_NOT_EXIST;
import static glide.api.models.commands.SetOptions.ConditionalSet.ONLY_IF_EXISTS;
import static glide.api.models.commands.SetOptions.RETURN_OLD_VALUE;
import static glide.utils.ArrayTransformUtils.concatenateArrays;
import static glide.utils.ArrayTransformUtils.convertMapToValueKeyStringArray;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import static redis_request.RedisRequestOuterClass.RequestType.CustomCommand;
import static redis_request.RedisRequestOuterClass.RequestType.Decr;
import static redis_request.RedisRequestOuterClass.RequestType.DecrBy;
import static redis_request.RedisRequestOuterClass.RequestType.Del;
import static redis_request.RedisRequestOuterClass.RequestType.Exists;
import static redis_request.RedisRequestOuterClass.RequestType.Expire;
import static redis_request.RedisRequestOuterClass.RequestType.ExpireAt;
import static redis_request.RedisRequestOuterClass.RequestType.GetString;
import static redis_request.RedisRequestOuterClass.RequestType.HashDel;
import static redis_request.RedisRequestOuterClass.RequestType.HashExists;
import static redis_request.RedisRequestOuterClass.RequestType.HashGet;
import static redis_request.RedisRequestOuterClass.RequestType.HashGetAll;
import static redis_request.RedisRequestOuterClass.RequestType.HashIncrBy;
import static redis_request.RedisRequestOuterClass.RequestType.HashIncrByFloat;
import static redis_request.RedisRequestOuterClass.RequestType.HashMGet;
import static redis_request.RedisRequestOuterClass.RequestType.HashSet;
import static redis_request.RedisRequestOuterClass.RequestType.Incr;
import static redis_request.RedisRequestOuterClass.RequestType.IncrBy;
import static redis_request.RedisRequestOuterClass.RequestType.IncrByFloat;
import static redis_request.RedisRequestOuterClass.RequestType.Info;
import static redis_request.RedisRequestOuterClass.RequestType.LLen;
import static redis_request.RedisRequestOuterClass.RequestType.LPop;
import static redis_request.RedisRequestOuterClass.RequestType.LPush;
import static redis_request.RedisRequestOuterClass.RequestType.LRange;
import static redis_request.RedisRequestOuterClass.RequestType.LRem;
import static redis_request.RedisRequestOuterClass.RequestType.LTrim;
import static redis_request.RedisRequestOuterClass.RequestType.MGet;
import static redis_request.RedisRequestOuterClass.RequestType.MSet;
import static redis_request.RedisRequestOuterClass.RequestType.PExpire;
import static redis_request.RedisRequestOuterClass.RequestType.PExpireAt;
import static redis_request.RedisRequestOuterClass.RequestType.Ping;
import static redis_request.RedisRequestOuterClass.RequestType.RPop;
import static redis_request.RedisRequestOuterClass.RequestType.RPush;
import static redis_request.RedisRequestOuterClass.RequestType.SAdd;
import static redis_request.RedisRequestOuterClass.RequestType.SCard;
import static redis_request.RedisRequestOuterClass.RequestType.SMembers;
import static redis_request.RedisRequestOuterClass.RequestType.SRem;
import static redis_request.RedisRequestOuterClass.RequestType.Select;
import static redis_request.RedisRequestOuterClass.RequestType.SetString;
import static redis_request.RedisRequestOuterClass.RequestType.TTL;
import static redis_request.RedisRequestOuterClass.RequestType.Unlink;
import static redis_request.RedisRequestOuterClass.RequestType.ZPopMax;
import static redis_request.RedisRequestOuterClass.RequestType.Zadd;
import static redis_request.RedisRequestOuterClass.RequestType.Zcard;
import static redis_request.RedisRequestOuterClass.RequestType.Zrem;

import glide.api.models.commands.ExpireOptions;
import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.SetOptions.Expiry;
import glide.api.models.commands.ZaddOptions;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RedisClientTest {

    RedisClient service;

    ConnectionManager connectionManager;

    CommandManager commandManager;

    @BeforeEach
    public void setUp() {
        connectionManager = mock(ConnectionManager.class);
        commandManager = mock(CommandManager.class);
        service = new RedisClient(connectionManager, commandManager);
    }

    @SneakyThrows
    @Test
    public void customCommand_returns_success() {
        // setup
        String key = "testKey";
        Object value = "testValue";
        String cmd = "GETSTRING";
        String[] arguments = new String[] {cmd, key};
        CompletableFuture<Object> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.submitNewCommand(eq(CustomCommand), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.customCommand(arguments);
        String payload = (String) response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
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
    public void select_returns_success() {
        // setup
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        Long index = 5L;
        when(testResponse.get()).thenReturn(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(Select), eq(new String[] {Long.toString(index)}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.select(index);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void del_returns_long_success() {
        // setup
        String[] keys = new String[] {"testKey1", "testKey2"};
        Long numberDeleted = 1L;
        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(numberDeleted);
        when(commandManager.<Long>submitNewCommand(eq(Del), eq(keys), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.del(keys);
        Long result = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(numberDeleted, result);
    }

    @SneakyThrows
    @Test
    public void unlink_returns_long_success() {
        // setup
        String[] keys = new String[] {"testKey1", "testKey2"};
        Long numberUnlinked = 1L;
        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(numberUnlinked);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(Unlink), eq(keys), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.unlink(keys);
        Long result = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(numberUnlinked, result);
    }

    @SneakyThrows
    @Test
    public void get_returns_success() {
        // setup
        String key = "testKey";
        String value = "testValue";
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);
        when(commandManager.<String>submitNewCommand(eq(GetString), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.get(key);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void set_returns_success() {
        // setup
        String key = "testKey";
        String value = "testValue";
        CompletableFuture<Void> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(null);
        when(commandManager.<Void>submitNewCommand(eq(SetString), eq(new String[] {key, value}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.set(key, value);
        Object okResponse = response.get();

        // verify
        assertEquals(testResponse, response);
        assertNull(okResponse);
    }

    @SneakyThrows
    @Test
    public void set_with_SetOptions_OnlyIfExists_returns_success() {
        // setup
        String key = "testKey";
        String value = "testValue";
        SetOptions setOptions =
                SetOptions.builder()
                        .conditionalSet(ONLY_IF_EXISTS)
                        .returnOldValue(false)
                        .expiry(Expiry.KeepExisting())
                        .build();
        String[] arguments = new String[] {key, value, ONLY_IF_EXISTS.getRedisApi(), "KEEPTTL"};

        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(null);
        when(commandManager.<String>submitNewCommand(eq(SetString), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.set(key, value, setOptions);

        // verify
        assertEquals(testResponse, response);
        assertNull(response.get());
    }

    @SneakyThrows
    @Test
    public void set_with_SetOptions_OnlyIfDoesNotExist_returns_success() {
        // setup
        String key = "testKey";
        String value = "testValue";
        SetOptions setOptions =
                SetOptions.builder()
                        .conditionalSet(ONLY_IF_DOES_NOT_EXIST)
                        .returnOldValue(true)
                        .expiry(Expiry.UnixSeconds(60L))
                        .build();
        String[] arguments =
                new String[] {
                    key, value, ONLY_IF_DOES_NOT_EXIST.getRedisApi(), RETURN_OLD_VALUE, "EXAT", "60"
                };
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);
        when(commandManager.<String>submitNewCommand(eq(SetString), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.set(key, value, setOptions);

        // verify
        assertNotNull(response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void exists_returns_long_success() {
        // setup
        String[] keys = new String[] {"testKey1", "testKey2"};
        Long numberExisting = 1L;
        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(numberExisting);
        when(commandManager.<Long>submitNewCommand(eq(Exists), eq(keys), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.exists(keys);
        Long result = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(numberExisting, result);
    }

    @SneakyThrows
    @Test
    public void expire_returns_success() {
        // setup
        String key = "testKey";
        long seconds = 10L;
        String[] arguments = new String[] {key, Long.toString(seconds)};

        CompletableFuture<Boolean> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(true);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(Expire), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.expire(key, seconds);

        // verify
        assertEquals(testResponse, response);
        assertEquals(true, response.get());
    }

    @SneakyThrows
    @Test
    public void expire_with_expireOptions_returns_success() {
        // setup
        String key = "testKey";
        long seconds = 10L;
        String[] arguments = new String[] {key, Long.toString(seconds), "NX"};

        CompletableFuture<Boolean> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(false);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(Expire), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.expire(key, seconds, ExpireOptions.HAS_NO_EXPIRY);

        // verify
        assertEquals(testResponse, response);
        assertEquals(false, response.get());
    }

    @SneakyThrows
    @Test
    public void expireAt_returns_success() {
        // setup
        String key = "testKey";
        long unixSeconds = 100000L;
        String[] arguments = new String[] {key, Long.toString(unixSeconds)};

        CompletableFuture<Boolean> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(true);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(ExpireAt), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.expireAt(key, unixSeconds);

        // verify
        assertEquals(testResponse, response);
        assertEquals(true, response.get());
    }

    @SneakyThrows
    @Test
    public void expireAt_with_expireOptions_returns_success() {
        // setup
        String key = "testKey";
        long unixSeconds = 100000L;
        String[] arguments = new String[] {key, Long.toString(unixSeconds), "XX"};

        CompletableFuture<Boolean> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(false);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(ExpireAt), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response =
                service.expireAt(key, unixSeconds, ExpireOptions.HAS_EXISTING_EXPIRY);

        // verify
        assertEquals(testResponse, response);
        assertEquals(false, response.get());
    }

    @SneakyThrows
    @Test
    public void pexpire_returns_success() {
        // setup
        String key = "testKey";
        long milliseconds = 50000L;
        String[] arguments = new String[] {key, Long.toString(milliseconds)};

        CompletableFuture<Boolean> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(true);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(PExpire), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.pexpire(key, milliseconds);

        // verify
        assertEquals(testResponse, response);
        assertEquals(true, response.get());
    }

    @SneakyThrows
    @Test
    public void pexpire_with_expireOptions_returns_success() {
        // setup
        String key = "testKey";
        long milliseconds = 50000L;
        String[] arguments = new String[] {key, Long.toString(milliseconds), "LT"};

        CompletableFuture<Boolean> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(false);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(PExpire), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response =
                service.pexpire(key, milliseconds, ExpireOptions.NEW_EXPIRY_LESS_THAN_CURRENT);

        // verify
        assertEquals(testResponse, response);
        assertEquals(false, response.get());
    }

    @SneakyThrows
    @Test
    public void pexpireAt_returns_success() {
        // setup
        String key = "testKey";
        long unixMilliseconds = 999999L;
        String[] arguments = new String[] {key, Long.toString(unixMilliseconds)};

        CompletableFuture<Boolean> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(true);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(PExpireAt), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.pexpireAt(key, unixMilliseconds);

        // verify
        assertEquals(testResponse, response);
        assertEquals(true, response.get());
    }

    @SneakyThrows
    @Test
    public void pexpireAt_with_expireOptions_returns_success() {
        // setup
        String key = "testKey";
        long unixMilliseconds = 999999L;
        String[] arguments = new String[] {key, Long.toString(unixMilliseconds), "GT"};

        CompletableFuture<Boolean> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(false);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(PExpireAt), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response =
                service.pexpireAt(key, unixMilliseconds, ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT);

        // verify
        assertEquals(testResponse, response);
        assertEquals(false, response.get());
    }

    @SneakyThrows
    @Test
    public void ttl_returns_success() {
        // setup
        String key = "testKey";
        long ttl = 999L;

        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(ttl);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(TTL), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.ttl(key);

        // verify
        assertEquals(testResponse, response);
        assertEquals(ttl, response.get());
    }

    @SneakyThrows
    @Test
    public void info_returns_success() {
        // setup
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        String testPayload = "Key: Value";
        when(testResponse.get()).thenReturn(testPayload);
        when(commandManager.<String>submitNewCommand(eq(Info), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.info();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(testPayload, payload);
    }

    @SneakyThrows
    @Test
    public void info_with_multiple_InfoOptions_returns_success() {
        // setup
        String[] arguments =
                new String[] {InfoOptions.Section.ALL.toString(), InfoOptions.Section.DEFAULT.toString()};
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        String testPayload = "Key: Value";
        when(testResponse.get()).thenReturn(testPayload);
        when(commandManager.<String>submitNewCommand(eq(Info), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        InfoOptions options =
                InfoOptions.builder()
                        .section(InfoOptions.Section.ALL)
                        .section(InfoOptions.Section.DEFAULT)
                        .build();
        CompletableFuture<String> response = service.info(options);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(testPayload, payload);
    }

    @SneakyThrows
    @Test
    public void info_with_empty_InfoOptions_returns_success() {
        // setup
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        String testPayload = "Key: Value";
        when(testResponse.get()).thenReturn(testPayload);
        when(commandManager.<String>submitNewCommand(eq(Info), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.info(InfoOptions.builder().build());
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(testPayload, payload);
    }

    @SneakyThrows
    @Test
    public void mget_returns_success() {
        // setup
        String[] keys = {"key1", null, "key2"};
        String[] values = {"value1", null, "value2"};

        CompletableFuture testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(values);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(MGet), eq(keys), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.mget(keys);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(values, payload);
    }

    @SneakyThrows
    @Test
    public void mset_returns_success() {
        // setup
        Map<String, String> keyValueMap = new LinkedHashMap<>();
        keyValueMap.put("key1", "value1");
        keyValueMap.put("key2", "value2");
        String[] args = {"key1", "value1", "key2", "value2"};

        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(MSet), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.mset(keyValueMap);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void incr_returns_success() {
        // setup
        String key = "testKey";
        Long value = 10L;

        CompletableFuture testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Incr), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.incr(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void incrBy_returns_success() {
        // setup
        String key = "testKey";
        long amount = 1L;
        Long value = 10L;

        CompletableFuture testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(IncrBy), eq(new String[] {key, Long.toString(amount)}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.incrBy(key, amount);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void incrByFloat_returns_success() {
        // setup
        String key = "testKey";
        double amount = 1.1;
        Double value = 10.1;

        CompletableFuture testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(IncrByFloat), eq(new String[] {key, Double.toString(amount)}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double> response = service.incrByFloat(key, amount);
        Double payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void decr_returns_success() {
        // setup
        String key = "testKey";
        Long value = 10L;

        CompletableFuture testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Decr), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.decr(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void decrBy_returns_success() {
        // setup
        String key = "testKey";
        long amount = 1L;
        Long value = 10L;

        CompletableFuture testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(DecrBy), eq(new String[] {key, Long.toString(amount)}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.decrBy(key, amount);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hget_success() {
        // setup
        String key = "testKey";
        String field = "field";
        String[] args = new String[] {key, field};
        String value = "value";

        CompletableFuture testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);
        when(commandManager.<String>submitNewCommand(eq(HashGet), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.hget(key, field);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hset_success() {
        // setup
        String key = "testKey";
        Map<String, String> fieldValueMap = new LinkedHashMap<>();
        fieldValueMap.put("field1", "value1");
        fieldValueMap.put("field2", "value2");
        String[] args = new String[] {key, "field1", "value1", "field2", "value2"};
        Long value = 2L;

        CompletableFuture testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);
        when(commandManager.<Long>submitNewCommand(eq(HashSet), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.hset(key, fieldValueMap);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hdel_success() {
        // setup
        String key = "testKey";
        String[] fields = {"testField1", "testField2"};
        String[] args = {key, "testField1", "testField2"};
        Long value = 2L;

        CompletableFuture testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);
        when(commandManager.<Long>submitNewCommand(eq(HashDel), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.hdel(key, fields);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hmget_success() {
        // setup
        String key = "testKey";
        String[] fields = {"testField1", "testField2"};
        String[] args = {"testKey", "testField1", "testField2"};
        String[] value = {"testValue1", "testValue2"};

        CompletableFuture<String[]> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(HashMGet), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.hmget(key, fields);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hexists_success() {
        // setup
        String key = "testKey";
        String field = "testField";
        String[] args = new String[] {key, field};
        Boolean value = true;

        CompletableFuture<Boolean> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(HashExists), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.hexists(key, field);
        Boolean payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hgetall_success() {
        // setup
        String key = "testKey";
        String[] args = new String[] {key};
        Map<String, String> value = new LinkedHashMap<>();
        value.put("key1", "field1");
        value.put("key2", "field2");

        CompletableFuture<Map<String, String>> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<Map<String, String>>submitNewCommand(eq(HashGetAll), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, String>> response = service.hgetall(key);
        Map<String, String> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hincrBy_returns_success() {
        // setup
        String key = "testKey";
        String field = "field";
        long amount = 1L;
        Long value = 10L;

        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(HashIncrBy), eq(new String[] {key, field, Long.toString(amount)}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.hincrBy(key, field, amount);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hincrByFloat_returns_success() {
        // setup
        String key = "testKey";
        String field = "field";
        double amount = 1.0;
        Double value = 10.0;

        CompletableFuture<Double> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<Double>submitNewCommand(
                        eq(HashIncrByFloat), eq(new String[] {key, field, Double.toString(amount)}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double> response = service.hincrByFloat(key, field, amount);
        Double payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lpush_returns_success() {
        // setup
        String key = "testKey";
        String[] elements = new String[] {"value1", "value2"};
        String[] args = new String[] {key, "value1", "value2"};
        Long value = 2L;

        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(LPush), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.lpush(key, elements);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lpop_returns_success() {
        // setup
        String key = "testKey";
        String[] args = new String[] {key};
        String value = "value";

        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(LPop), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.lpop(key);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lpopCount_returns_success() {
        // setup
        String key = "testKey";
        long count = 2L;
        String[] args = new String[] {key, Long.toString(count)};
        String[] value = new String[] {"value1", "value2"};

        CompletableFuture<String[]> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(LPop), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.lpopCount(key, count);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lrange_returns_success() {
        // setup
        String key = "testKey";
        long start = 2L;
        long end = 4L;
        String[] args = new String[] {key, Long.toString(start), Long.toString(end)};
        String[] value = new String[] {"value1", "value2"};

        CompletableFuture<String[]> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(LRange), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.lrange(key, start, end);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void ltrim_returns_success() {
        // setup
        String key = "testKey";
        long start = 2L;
        long end = 2L;
        String[] args = new String[] {key, Long.toString(end), Long.toString(start)};

        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(LTrim), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.ltrim(key, start, end);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void llen_returns_success() {
        // setup
        String key = "testKey";
        String[] args = new String[] {key};
        long value = 2L;

        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(LLen), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.llen(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lrem_returns_success() {
        // setup
        String key = "testKey";
        long count = 2L;
        String element = "value";
        String[] args = new String[] {key, Long.toString(count), element};
        long value = 2L;

        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(LRem), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.lrem(key, count, element);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void rpush_returns_success() {
        // setup
        String key = "testKey";
        String[] elements = new String[] {"value1", "value2"};
        String[] args = new String[] {key, "value1", "value2"};
        Long value = 2L;

        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(RPush), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.rpush(key, elements);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void rpop_returns_success() {
        // setup
        String key = "testKey";
        String value = "value";
        String[] args = new String[] {key};

        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(RPop), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.rpop(key);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void rpopCount_returns_success() {
        // setup
        String key = "testKey";
        long count = 2L;
        String[] args = new String[] {key, Long.toString(count)};
        String[] value = new String[] {"value1", "value2"};

        CompletableFuture<String[]> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(RPop), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.rpopCount(key, count);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sadd_returns_success() {
        // setup
        String key = "testKey";
        String[] members = new String[] {"testMember1", "testMember2"};
        String[] arguments = ArrayUtils.addFirst(members, key);
        Long value = 2L;

        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.sadd(key, members);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void srem_returns_success() {
        // setup
        String key = "testKey";
        String[] members = new String[] {"testMember1", "testMember2"};
        String[] arguments = ArrayUtils.addFirst(members, key);
        Long value = 2L;

        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SRem), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.srem(key, members);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void smembers_returns_success() {
        // setup
        String key = "testKey";
        Set<String> value = Set.of("testMember");

        CompletableFuture<Set<String>> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<Set<String>>submitNewCommand(eq(SMembers), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Set<String>> response = service.smembers(key);
        Set<String> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void scard_returns_success() {
        // setup
        String key = "testKey";
        Long value = 2L;

        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SCard), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.scard(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zadd_noOptions_returns_success() {
        // setup
        String key = "testKey";
        Map<String, Double> membersScores = new LinkedHashMap<>();
        membersScores.put("testMember1", 1.0);
        membersScores.put("testMember2", 2.0);
        String[] membersScoresArgs = convertMapToValueKeyStringArray(membersScores);
        String[] arguments = ArrayUtils.addFirst(membersScoresArgs, key);
        Long value = 2L;

        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(Zadd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response =
                service.zadd(key, membersScores, ZaddOptions.builder().build(), false);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zadd_withOptions_returns_success() {
        // setup
        String key = "testKey";
        ZaddOptions options =
                ZaddOptions.builder()
                        .conditionalChange(ZaddOptions.ConditionalChange.ONLY_IF_EXISTS)
                        .updateOptions(ZaddOptions.UpdateOptions.SCORE_GREATER_THAN_CURRENT)
                        .build();
        Map<String, Double> membersScores = new LinkedHashMap<>();
        membersScores.put("testMember1", 1.0);
        membersScores.put("testMember2", 2.0);
        String[] membersScoresArgs = convertMapToValueKeyStringArray(membersScores);
        String[] arguments = ArrayUtils.addAll(new String[] {key}, options.toArgs());
        arguments = ArrayUtils.addAll(arguments, membersScoresArgs);
        Long value = 2L;

        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(Zadd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zadd(key, membersScores, options, false);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zadd_withIllegalArgument_throws_exception() {
        // setup
        String key = "testKey";
        ZaddOptions options =
                ZaddOptions.builder()
                        .conditionalChange(ZaddOptions.ConditionalChange.ONLY_IF_DOES_NOT_EXIST)
                        .updateOptions(ZaddOptions.UpdateOptions.SCORE_GREATER_THAN_CURRENT)
                        .build();
        Map<String, Double> membersScores = new LinkedHashMap<>();
        membersScores.put("testMember1", 1.0);
        membersScores.put("testMember2", 2.0);

        assertThrows(
                IllegalArgumentException.class, () -> service.zadd(key, membersScores, options, false));
    }

    @SneakyThrows
    @Test
    public void zaddIncr_noOptions_returns_success() {
        // setup
        String key = "testKey";
        String member = "member";
        double increment = 3.0;
        String[] arguments = new String[] {key, "INCR", Double.toString(increment), member};
        Double value = 3.0;

        CompletableFuture<Double> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<Double>submitNewCommand(eq(Zadd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double> response =
                service.zaddIncr(key, member, increment, ZaddOptions.builder().build());
        Double payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zaddIncr_withOptions_returns_success() {
        // setup
        String key = "testKey";
        ZaddOptions options =
                ZaddOptions.builder()
                        .updateOptions(ZaddOptions.UpdateOptions.SCORE_GREATER_THAN_CURRENT)
                        .build();
        String member = "member";
        double increment = 3.0;
        String[] arguments =
                concatenateArrays(
                        new String[] {key},
                        options.toArgs(),
                        new String[] {"INCR", Double.toString(increment), member});
        Double value = 3.0;

        CompletableFuture<Double> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<Double>submitNewCommand(eq(Zadd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double> response = service.zaddIncr(key, member, increment, options);
        Double payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
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

    @SneakyThrows
    @Test
    public void configRewrite_returns_success() {
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
    public void configResetStat_returns_success() {
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
    public void configGet_returns_success() {
        // setup
        CompletableFuture<Map<String, String>> testResponse = mock(CompletableFuture.class);
        Map<String, String> testPayload = Map.of("timeout", "1000");
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
    public void zrem_returns_success() {
        // setup
        String key = "testKey";
        String[] members = new String[] {"member1", "member2"};
        String[] arguments = ArrayUtils.addFirst(members, key);
        Long value = 2L;

        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(Zrem), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zrem(key, members);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zcard_returns_success() {
        // setup
        String key = "testKey";
        String[] arguments = new String[] {key};
        Long value = 3L;

        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(Zcard), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zcard(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zpopmax_returns_success() {
        // setup
        String key = "testKey";
        String[] arguments = new String[] {key};
        Map<String, Double> value = Map.of("member1", 2.5);

        CompletableFuture<Map<String, Double>> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<Map<String, Double>>submitNewCommand(eq(ZPopMax), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Double>> response = service.zpopmax(key);
        Map<String, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zpopmax_with_count_returns_success() {
        // setup
        String key = "testKey";
        long count = 2L;
        String[] arguments = new String[] {key, Long.toString(count)};
        Map<String, Double> value = Map.of("member1", 3.0, "member2", 1.0);

        CompletableFuture<Map<String, Double>> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<Map<String, Double>>submitNewCommand(eq(ZPopMax), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Double>> response = service.zpopmax(key, count);
        Map<String, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }
}
