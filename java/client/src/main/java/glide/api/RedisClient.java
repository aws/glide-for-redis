/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static glide.api.models.GlideString.gs;
import static glide.api.models.commands.SortBaseOptions.STORE_COMMAND_STRING;
import static glide.api.models.commands.SortOptions.STORE_COMMAND_STRING;
import static glide.api.models.commands.function.FunctionListOptions.LIBRARY_NAME_REDIS_API;
import static glide.api.models.commands.function.FunctionListOptions.WITH_CODE_REDIS_API;
import static glide.api.models.commands.function.FunctionLoadOptions.REPLACE;
import static glide.utils.ArrayTransformUtils.castArray;
import static glide.utils.ArrayTransformUtils.concatenateArrays;
import static glide.utils.ArrayTransformUtils.convertMapToKeyValueStringArray;
import static redis_request.RedisRequestOuterClass.RequestType.ClientGetName;
import static redis_request.RedisRequestOuterClass.RequestType.ClientId;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigGet;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigResetStat;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigRewrite;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigSet;
import static redis_request.RedisRequestOuterClass.RequestType.Copy;
import static redis_request.RedisRequestOuterClass.RequestType.CustomCommand;
import static redis_request.RedisRequestOuterClass.RequestType.DBSize;
import static redis_request.RedisRequestOuterClass.RequestType.Echo;
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
import static redis_request.RedisRequestOuterClass.RequestType.Move;
import static redis_request.RedisRequestOuterClass.RequestType.Ping;
import static redis_request.RedisRequestOuterClass.RequestType.RandomKey;
import static redis_request.RedisRequestOuterClass.RequestType.Select;
import static redis_request.RedisRequestOuterClass.RequestType.Sort;
import static redis_request.RedisRequestOuterClass.RequestType.SortReadOnly;
import static redis_request.RedisRequestOuterClass.RequestType.Time;
import static redis_request.RedisRequestOuterClass.RequestType.UnWatch;

import glide.api.commands.ConnectionManagementCommands;
import glide.api.commands.GenericCommands;
import glide.api.commands.ScriptingAndFunctionsCommands;
import glide.api.commands.ServerManagementCommands;
import glide.api.commands.TransactionsCommands;
import glide.api.models.GlideString;
import glide.api.models.Transaction;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.SortOptions;
import glide.api.models.commands.SortOptionsBinary;
import glide.api.models.commands.function.FunctionRestorePolicy;
import glide.api.models.configuration.RedisClientConfiguration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;
import org.apache.commons.lang3.ArrayUtils;

/**
 * Async (non-blocking) client for Redis in Standalone mode. Use {@link #CreateClient} to request a
 * client to Redis.
 */
public class RedisClient extends BaseClient
        implements GenericCommands,
                ServerManagementCommands,
                ConnectionManagementCommands,
                ScriptingAndFunctionsCommands,
                TransactionsCommands {

    /**
     * A constructor. Use {@link #CreateClient} to get a client. Made protected to simplify testing.
     */
    protected RedisClient(ClientBuilder builder) {
        super(builder);
    }

    /**
     * Async request for an async (non-blocking) Redis client in Standalone mode.
     *
     * @param config Redis client Configuration.
     * @return A Future to connect and return a RedisClient.
     */
    public static CompletableFuture<RedisClient> CreateClient(
            @NonNull RedisClientConfiguration config) {
        return CreateClient(config, RedisClient::new);
    }

    @Override
    public CompletableFuture<Object> customCommand(@NonNull String[] args) {
        return commandManager.submitNewCommand(CustomCommand, args, this::handleObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<Object[]> exec(@NonNull Transaction transaction) {
        if (transaction.isBinarySafeOutput()) {
            return commandManager.submitNewTransaction(
                    transaction, this::handleArrayOrNullResponseBinary);
        } else {
            return commandManager.submitNewTransaction(transaction, this::handleArrayOrNullResponse);
        }
    }

    @Override
    public CompletableFuture<String> ping() {
        return commandManager.submitNewCommand(Ping, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> ping(@NonNull String message) {
        return commandManager.submitNewCommand(
                Ping, new String[] {message}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<GlideString> ping(@NonNull GlideString message) {
        return commandManager.submitNewCommand(
                Ping, new GlideString[] {message}, this::handleGlideStringResponse);
    }

    @Override
    public CompletableFuture<String> info() {
        return commandManager.submitNewCommand(Info, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> info(@NonNull InfoOptions options) {
        return commandManager.submitNewCommand(Info, options.toArgs(), this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> select(long index) {
        return commandManager.submitNewCommand(
                Select, new String[] {Long.toString(index)}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Long> clientId() {
        return commandManager.submitNewCommand(ClientId, new String[0], this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String> clientGetName() {
        return commandManager.submitNewCommand(
                ClientGetName, new String[0], this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String> configRewrite() {
        return commandManager.submitNewCommand(
                ConfigRewrite, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> configResetStat() {
        return commandManager.submitNewCommand(
                ConfigResetStat, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Map<String, String>> configGet(@NonNull String[] parameters) {
        return commandManager.submitNewCommand(ConfigGet, parameters, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<String> configSet(@NonNull Map<String, String> parameters) {
        return commandManager.submitNewCommand(
                ConfigSet, convertMapToKeyValueStringArray(parameters), this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> echo(@NonNull String message) {
        return commandManager.submitNewCommand(
                Echo, new String[] {message}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<GlideString> echo(@NonNull GlideString message) {
        return commandManager.submitNewCommand(
                Echo, new GlideString[] {message}, this::handleGlideStringResponse);
    }

    @Override
    public CompletableFuture<String[]> time() {
        return commandManager.submitNewCommand(
                Time, new String[0], response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<Long> lastsave() {
        return commandManager.submitNewCommand(LastSave, new String[0], this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String> flushall() {
        return commandManager.submitNewCommand(FlushAll, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> flushall(@NonNull FlushMode mode) {
        return commandManager.submitNewCommand(
                FlushAll, new String[] {mode.toString()}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> flushdb() {
        return commandManager.submitNewCommand(FlushDB, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> flushdb(@NonNull FlushMode mode) {
        return commandManager.submitNewCommand(
                FlushDB, new String[] {mode.toString()}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> lolwut() {
        return commandManager.submitNewCommand(Lolwut, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> lolwut(int @NonNull [] parameters) {
        String[] arguments =
                Arrays.stream(parameters).mapToObj(Integer::toString).toArray(String[]::new);
        return commandManager.submitNewCommand(Lolwut, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> lolwut(int version) {
        return commandManager.submitNewCommand(
                Lolwut,
                new String[] {VERSION_REDIS_API, Integer.toString(version)},
                this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> lolwut(int version, int @NonNull [] parameters) {
        String[] arguments =
                concatenateArrays(
                        new String[] {VERSION_REDIS_API, Integer.toString(version)},
                        Arrays.stream(parameters).mapToObj(Integer::toString).toArray(String[]::new));
        return commandManager.submitNewCommand(Lolwut, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Long> dbsize() {
        return commandManager.submitNewCommand(DBSize, new String[0], this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String> functionLoad(@NonNull String libraryCode, boolean replace) {
        String[] arguments =
                replace ? new String[] {REPLACE.toString(), libraryCode} : new String[] {libraryCode};
        return commandManager.submitNewCommand(FunctionLoad, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<GlideString> functionLoad(
            @NonNull GlideString libraryCode, boolean replace) {
        GlideString[] arguments =
                replace
                        ? new GlideString[] {gs(REPLACE.toString()), libraryCode}
                        : new GlideString[] {libraryCode};
        return commandManager.submitNewCommand(
                FunctionLoad, arguments, this::handleGlideStringResponse);
    }

    @Override
    public CompletableFuture<Boolean> move(@NonNull String key, long dbIndex) {
        return commandManager.submitNewCommand(
                Move, new String[] {key, Long.toString(dbIndex)}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> move(@NonNull GlideString key, long dbIndex) {
        return commandManager.submitNewCommand(
                Move, new GlideString[] {key, gs(Long.toString(dbIndex))}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Map<String, Object>[]> functionList(boolean withCode) {
        return commandManager.submitNewCommand(
                FunctionList,
                withCode ? new String[] {WITH_CODE_REDIS_API} : new String[0],
                response -> handleFunctionListResponse(handleArrayResponse(response)));
    }

    @Override
    public CompletableFuture<Map<String, Object>[]> functionList(
            @NonNull String libNamePattern, boolean withCode) {
        return commandManager.submitNewCommand(
                FunctionList,
                withCode
                        ? new String[] {LIBRARY_NAME_REDIS_API, libNamePattern, WITH_CODE_REDIS_API}
                        : new String[] {LIBRARY_NAME_REDIS_API, libNamePattern},
                response -> handleFunctionListResponse(handleArrayResponse(response)));
    }

    @Override
    public CompletableFuture<String> functionFlush() {
        return commandManager.submitNewCommand(
                FunctionFlush, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionFlush(@NonNull FlushMode mode) {
        return commandManager.submitNewCommand(
                FunctionFlush, new String[] {mode.toString()}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionDelete(@NonNull String libName) {
        return commandManager.submitNewCommand(
                FunctionDelete, new String[] {libName}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionDelete(@NonNull GlideString libName) {
        return commandManager.submitNewCommand(
                FunctionDelete, new GlideString[] {libName}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<byte[]> functionDump() {
        return commandManager.submitNewCommand(
                FunctionDump, new GlideString[0], this::handleBytesOrNullResponse);
    }

    @Override
    public CompletableFuture<String> functionRestore(byte @NonNull [] payload) {
        return commandManager.submitNewCommand(
                FunctionRestore, new GlideString[] {gs(payload)}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionRestore(
            byte @NonNull [] payload, @NonNull FunctionRestorePolicy policy) {
        return commandManager.submitNewCommand(
                FunctionRestore,
                new GlideString[] {gs(payload), gs(policy.toString())},
                this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Object> fcall(@NonNull String function) {
        return fcall(function, new String[0], new String[0]);
    }

    @Override
    public CompletableFuture<Object> fcall(@NonNull GlideString function) {
        return fcall(function, new GlideString[0], new GlideString[0]);
    }

    @Override
    public CompletableFuture<Object> fcallReadOnly(@NonNull String function) {
        return fcallReadOnly(function, new String[0], new String[0]);
    }

    @Override
    public CompletableFuture<Object> fcallReadOnly(@NonNull GlideString function) {
        return fcallReadOnly(function, new GlideString[0], new GlideString[0]);
    }

    @Override
    public CompletableFuture<Boolean> copy(
            @NonNull String source, @NonNull String destination, long destinationDB) {
        String[] arguments =
                new String[] {source, destination, DB_REDIS_API, Long.toString(destinationDB)};
        return commandManager.submitNewCommand(Copy, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> copy(
            @NonNull GlideString source, @NonNull GlideString destination, long destinationDB) {
        GlideString[] arguments =
                new GlideString[] {source, destination, gs(DB_REDIS_API), gs(Long.toString(destinationDB))};
        return commandManager.submitNewCommand(Copy, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> copy(
            @NonNull String source, @NonNull String destination, long destinationDB, boolean replace) {
        String[] arguments =
                new String[] {source, destination, DB_REDIS_API, Long.toString(destinationDB)};
        if (replace) {
            arguments = ArrayUtils.add(arguments, REPLACE_REDIS_API);
        }
        return commandManager.submitNewCommand(Copy, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> copy(
            @NonNull GlideString source,
            @NonNull GlideString destination,
            long destinationDB,
            boolean replace) {
        GlideString[] arguments =
                new GlideString[] {source, destination, gs(DB_REDIS_API), gs(Long.toString(destinationDB))};
        if (replace) {
            arguments = ArrayUtils.add(arguments, gs(REPLACE_REDIS_API));
        }
        return commandManager.submitNewCommand(Copy, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<String> functionKill() {
        return commandManager.submitNewCommand(FunctionKill, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Map<String, Map<String, Object>>> functionStats() {
        return commandManager.submitNewCommand(
                FunctionStats,
                new String[0],
                response -> handleFunctionStatsResponse(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<String> unwatch() {
        return commandManager.submitNewCommand(UnWatch, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> randomKey() {
        return commandManager.submitNewCommand(
                RandomKey, new String[0], this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String[]> sort(@NonNull String key, @NonNull SortOptions sortOptions) {
        String[] arguments = ArrayUtils.addFirst(sortOptions.toArgs(), key);
        return commandManager.submitNewCommand(
                Sort, arguments, response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> sort(
            @NonNull GlideString key, @NonNull SortOptionsBinary sortOptions) {
        GlideString[] arguments = ArrayUtils.addFirst(sortOptions.toGlideStringArgs(), key);
        return commandManager.submitNewCommand(
                Sort,
                arguments,
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<String[]> sortReadOnly(
            @NonNull String key, @NonNull SortOptions sortOptions) {
        String[] arguments = ArrayUtils.addFirst(sortOptions.toArgs(), key);
        return commandManager.submitNewCommand(
                SortReadOnly,
                arguments,
                response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> sortReadOnly(
            @NonNull GlideString key, @NonNull SortOptionsBinary sortOptions) {
        GlideString[] arguments = ArrayUtils.addFirst(sortOptions.toGlideStringArgs(), key);
        return commandManager.submitNewCommand(
                SortReadOnly,
                arguments,
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Long> sortStore(
            @NonNull String key, @NonNull String destination, @NonNull SortOptions sortOptions) {
        String[] storeArguments = new String[] {STORE_COMMAND_STRING, destination};
        String[] arguments =
                concatenateArrays(new String[] {key}, sortOptions.toArgs(), storeArguments);
        return commandManager.submitNewCommand(Sort, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> sortStore(
            @NonNull GlideString key,
            @NonNull GlideString destination,
            @NonNull SortOptionsBinary sortOptions) {
        GlideString[] storeArguments = new GlideString[] {gs(STORE_COMMAND_STRING), destination};
        GlideString[] arguments =
                concatenateArrays(new GlideString[] {key}, sortOptions.toGlideStringArgs(), storeArguments);
        return commandManager.submitNewCommand(Sort, arguments, this::handleLongResponse);
    }
}
