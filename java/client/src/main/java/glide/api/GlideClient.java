/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static command_request.CommandRequestOuterClass.RequestType.ClientGetName;
import static command_request.CommandRequestOuterClass.RequestType.ClientId;
import static command_request.CommandRequestOuterClass.RequestType.ConfigGet;
import static command_request.CommandRequestOuterClass.RequestType.ConfigResetStat;
import static command_request.CommandRequestOuterClass.RequestType.ConfigRewrite;
import static command_request.CommandRequestOuterClass.RequestType.ConfigSet;
import static command_request.CommandRequestOuterClass.RequestType.Copy;
import static command_request.CommandRequestOuterClass.RequestType.CustomCommand;
import static command_request.CommandRequestOuterClass.RequestType.DBSize;
import static command_request.CommandRequestOuterClass.RequestType.Echo;
import static command_request.CommandRequestOuterClass.RequestType.FlushAll;
import static command_request.CommandRequestOuterClass.RequestType.FlushDB;
import static command_request.CommandRequestOuterClass.RequestType.FunctionDelete;
import static command_request.CommandRequestOuterClass.RequestType.FunctionDump;
import static command_request.CommandRequestOuterClass.RequestType.FunctionFlush;
import static command_request.CommandRequestOuterClass.RequestType.FunctionKill;
import static command_request.CommandRequestOuterClass.RequestType.FunctionList;
import static command_request.CommandRequestOuterClass.RequestType.FunctionLoad;
import static command_request.CommandRequestOuterClass.RequestType.FunctionRestore;
import static command_request.CommandRequestOuterClass.RequestType.FunctionStats;
import static command_request.CommandRequestOuterClass.RequestType.Info;
import static command_request.CommandRequestOuterClass.RequestType.LastSave;
import static command_request.CommandRequestOuterClass.RequestType.Lolwut;
import static command_request.CommandRequestOuterClass.RequestType.Move;
import static command_request.CommandRequestOuterClass.RequestType.Ping;
import static command_request.CommandRequestOuterClass.RequestType.RandomKey;
import static command_request.CommandRequestOuterClass.RequestType.Scan;
import static command_request.CommandRequestOuterClass.RequestType.Select;
import static command_request.CommandRequestOuterClass.RequestType.Sort;
import static command_request.CommandRequestOuterClass.RequestType.SortReadOnly;
import static command_request.CommandRequestOuterClass.RequestType.Time;
import static command_request.CommandRequestOuterClass.RequestType.UnWatch;
import static glide.api.models.GlideString.gs;
import static glide.api.models.commands.SortBaseOptions.STORE_COMMAND_STRING;
import static glide.api.models.commands.function.FunctionListOptions.LIBRARY_NAME_VALKEY_API;
import static glide.api.models.commands.function.FunctionListOptions.WITH_CODE_VALKEY_API;
import static glide.api.models.commands.function.FunctionLoadOptions.REPLACE;
import static glide.utils.ArrayTransformUtils.castArray;
import static glide.utils.ArrayTransformUtils.concatenateArrays;
import static glide.utils.ArrayTransformUtils.convertMapToKeyValueStringArray;

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
import glide.api.models.commands.scan.ScanOptions;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.utils.ArgsBuilder;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;
import org.apache.commons.lang3.ArrayUtils;

/**
 * Async (non-blocking) client for Standalone mode. Use {@link #createClient} to request a client.
 */
public class GlideClient extends BaseClient
        implements GenericCommands,
                ServerManagementCommands,
                ConnectionManagementCommands,
                ScriptingAndFunctionsCommands,
                TransactionsCommands {

    /**
     * A constructor. Use {@link #createClient} to get a client. Made protected to simplify testing.
     */
    protected GlideClient(ClientBuilder builder) {
        super(builder);
    }

    /**
     * Async request for an async (non-blocking) client in Standalone mode.
     *
     * @param config Glide client Configuration.
     * @return A Future to connect and return a GlideClient.
     */
    public static CompletableFuture<GlideClient> createClient(
            @NonNull GlideClientConfiguration config) {
        return createClient(config, GlideClient::new);
    }

    @Override
    public CompletableFuture<Object> customCommand(@NonNull String[] args) {
        return commandManager.submitNewCommand(CustomCommand, args, this::handleObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<Object> customCommand(@NonNull GlideString[] args) {
        return commandManager.submitNewCommand(
                CustomCommand, args, this::handleBinaryObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<Object[]> exec(@NonNull Transaction transaction) {
        if (transaction.isBinaryOutput()) {
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
                new String[] {VERSION_VALKEY_API, Integer.toString(version)},
                this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> lolwut(int version, int @NonNull [] parameters) {
        String[] arguments =
                concatenateArrays(
                        new String[] {VERSION_VALKEY_API, Integer.toString(version)},
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
                withCode ? new String[] {WITH_CODE_VALKEY_API} : new String[0],
                response -> handleFunctionListResponse(handleArrayResponse(response)));
    }

    @Override
    public CompletableFuture<Map<GlideString, Object>[]> functionListBinary(boolean withCode) {
        return commandManager.submitNewCommand(
                FunctionList,
                new ArgsBuilder().addIf(WITH_CODE_VALKEY_API, withCode).toArray(),
                response -> handleFunctionListResponseBinary(handleArrayResponseBinary(response)));
    }

    @Override
    public CompletableFuture<Map<String, Object>[]> functionList(
            @NonNull String libNamePattern, boolean withCode) {
        return commandManager.submitNewCommand(
                FunctionList,
                withCode
                        ? new String[] {LIBRARY_NAME_VALKEY_API, libNamePattern, WITH_CODE_VALKEY_API}
                        : new String[] {LIBRARY_NAME_VALKEY_API, libNamePattern},
                response -> handleFunctionListResponse(handleArrayResponse(response)));
    }

    @Override
    public CompletableFuture<Map<GlideString, Object>[]> functionListBinary(
            @NonNull GlideString libNamePattern, boolean withCode) {
        return commandManager.submitNewCommand(
                FunctionList,
                new ArgsBuilder()
                        .add(LIBRARY_NAME_VALKEY_API)
                        .add(libNamePattern)
                        .addIf(WITH_CODE_VALKEY_API, withCode)
                        .toArray(),
                response -> handleFunctionListResponseBinary(handleArrayResponseBinary(response)));
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
                new String[] {source, destination, DB_VALKEY_API, Long.toString(destinationDB)};
        return commandManager.submitNewCommand(Copy, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> copy(
            @NonNull GlideString source, @NonNull GlideString destination, long destinationDB) {
        GlideString[] arguments =
                new GlideString[] {
                    source, destination, gs(DB_VALKEY_API), gs(Long.toString(destinationDB))
                };
        return commandManager.submitNewCommand(Copy, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> copy(
            @NonNull String source, @NonNull String destination, long destinationDB, boolean replace) {
        String[] arguments =
                new String[] {source, destination, DB_VALKEY_API, Long.toString(destinationDB)};
        if (replace) {
            arguments = ArrayUtils.add(arguments, REPLACE_VALKEY_API);
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
                new GlideString[] {
                    source, destination, gs(DB_VALKEY_API), gs(Long.toString(destinationDB))
                };
        if (replace) {
            arguments = ArrayUtils.add(arguments, gs(REPLACE_VALKEY_API));
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
    public CompletableFuture<Map<GlideString, Map<GlideString, Object>>> functionStatsBinary() {
        return commandManager.submitNewCommand(
                FunctionStats,
                new GlideString[0],
                response -> handleFunctionStatsBinaryResponse(handleBinaryStringMapResponse(response)));
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
    public CompletableFuture<GlideString> randomKeyBinary() {
        return commandManager.submitNewCommand(
                RandomKey, new GlideString[0], this::handleGlideStringOrNullResponse);
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
        GlideString[] arguments = new ArgsBuilder().add(key).add(sortOptions.toArgs()).toArray();
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
        GlideString[] arguments = new ArgsBuilder().add(key).add(sortOptions.toArgs()).toArray();

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

        GlideString[] arguments =
                new ArgsBuilder()
                        .add(key)
                        .add(sortOptions.toArgs())
                        .add(STORE_COMMAND_STRING)
                        .add(destination)
                        .toArray();

        return commandManager.submitNewCommand(Sort, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Object[]> scan(@NonNull String cursor) {
        return commandManager.submitNewCommand(Scan, new String[] {cursor}, this::handleArrayResponse);
    }

    @Override
    public CompletableFuture<Object[]> scan(@NonNull GlideString cursor) {
        return commandManager.submitNewCommand(
                Scan, new GlideString[] {cursor}, this::handleArrayResponseBinary);
    }

    @Override
    public CompletableFuture<Object[]> scan(@NonNull String cursor, @NonNull ScanOptions options) {
        String[] arguments = ArrayUtils.addFirst(options.toArgs(), cursor);
        return commandManager.submitNewCommand(Scan, arguments, this::handleArrayResponse);
    }

    @Override
    public CompletableFuture<Object[]> scan(
            @NonNull GlideString cursor, @NonNull ScanOptions options) {
        GlideString[] arguments = new ArgsBuilder().add(cursor).add(options.toArgs()).toArray();
        return commandManager.submitNewCommand(Scan, arguments, this::handleArrayResponseBinary);
    }
}
