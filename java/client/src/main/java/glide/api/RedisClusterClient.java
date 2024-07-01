/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static glide.api.BaseClient.OK;
import static glide.api.commands.ServerManagementCommands.VERSION_REDIS_API;
import static glide.api.models.GlideString.gs;
import static glide.api.models.commands.SortBaseOptions.STORE_COMMAND_STRING;
import static glide.api.models.commands.function.FunctionListOptions.LIBRARY_NAME_REDIS_API;
import static glide.api.models.commands.function.FunctionListOptions.WITH_CODE_REDIS_API;
import static glide.api.models.commands.function.FunctionLoadOptions.REPLACE;
import static glide.utils.ArrayTransformUtils.castArray;
import static glide.utils.ArrayTransformUtils.castMapOfArrays;
import static glide.utils.ArrayTransformUtils.concatenateArrays;
import static glide.utils.ArrayTransformUtils.convertMapToKeyValueStringArray;
import static redis_request.RedisRequestOuterClass.RequestType.ClientGetName;
import static redis_request.RedisRequestOuterClass.RequestType.ClientId;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigGet;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigResetStat;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigRewrite;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigSet;
import static redis_request.RedisRequestOuterClass.RequestType.CustomCommand;
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

import glide.api.commands.ConnectionManagementClusterCommands;
import glide.api.commands.GenericClusterCommands;
import glide.api.commands.PubSubClusterCommands;
import glide.api.commands.ScriptingAndFunctionsClusterCommands;
import glide.api.commands.ServerManagementClusterCommands;
import glide.api.commands.TransactionsClusterCommands;
import glide.api.models.ClusterTransaction;
import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.SortClusterOptions;
import glide.api.models.commands.function.FunctionRestorePolicy;
import glide.api.models.commands.scan.ClusterScanCursor;
import glide.api.models.commands.scan.ScanOptions;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute;
import glide.ffi.resolvers.ClusterScanCursorResolver;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import lombok.NonNull;
import org.apache.commons.lang3.ArrayUtils;

import response.ResponseOuterClass.Response;

/**
 * Async (non-blocking) client for Redis in Cluster mode. Use {@link #CreateClient} to request a
 * client to Redis.
 */
public class RedisClusterClient extends BaseClient
        implements ConnectionManagementClusterCommands,
                GenericClusterCommands,
                ServerManagementClusterCommands,
                ScriptingAndFunctionsClusterCommands,
                TransactionsClusterCommands,
                PubSubClusterCommands {

    /** A private constructor. Use {@link #CreateClient} to get a client. */
    RedisClusterClient(ClientBuilder builder) {
        super(builder);
    }

    /**
     * Async request for an async (non-blocking) Redis client in Cluster mode.
     *
     * @param config Redis cluster client Configuration.
     * @return A Future to connect and return a RedisClusterClient.
     */
    public static CompletableFuture<RedisClusterClient> CreateClient(
        @NonNull RedisClusterClientConfiguration config) {
        return CreateClient(config, RedisClusterClient::new);
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> customCommand(@NonNull String[] args) {
        // TODO if a command returns a map as a single value, ClusterValue misleads user
        return commandManager.submitNewCommand(
            CustomCommand, args, response -> ClusterValue.of(handleObjectOrNullResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> customCommand(
        @NonNull String[] args, @NonNull Route route) {
        return commandManager.submitNewCommand(
            CustomCommand, args, route, response -> handleCustomCommandResponse(route, response));
    }

    protected ClusterValue<Object> handleCustomCommandResponse(Route route, Response response) {
        if (route instanceof SingleNodeRoute) {
            return ClusterValue.ofSingleValue(handleObjectOrNullResponse(response));
        }
        if (response.hasConstantResponse()) {
            return ClusterValue.ofSingleValue(handleStringResponse(response));
        }
        return ClusterValue.ofMultiValue(handleMapResponse(response));
    }

    @Override
    public CompletableFuture<Object[]> exec(@NonNull ClusterTransaction transaction) {
        if (transaction.isBinarySafeOutput()) {
            return commandManager.submitNewTransaction(
                    transaction, Optional.empty(), this::handleArrayOrNullResponseBinary);
        } else {
            return commandManager.submitNewTransaction(
                    transaction, Optional.empty(), this::handleArrayOrNullResponse);
        }
    }

    @Override
    public CompletableFuture<Object[]> exec(
            @NonNull ClusterTransaction transaction, @NonNull SingleNodeRoute route) {
        if (transaction.isBinarySafeOutput()) {
            return commandManager.submitNewTransaction(
                    transaction, Optional.of(route), this::handleArrayOrNullResponseBinary);
        } else {
            return commandManager.submitNewTransaction(
                    transaction, Optional.of(route), this::handleArrayOrNullResponse);
        }
    }

    @Override
    public CompletableFuture<String> ping() {
        return commandManager.submitNewCommand(Ping, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> ping(@NonNull String message) {
        return commandManager.submitNewCommand(
            Ping, new String[]{message}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<GlideString> ping(@NonNull GlideString message) {
        return commandManager.submitNewCommand(
                Ping, new GlideString[] {message}, this::handleGlideStringResponse);
    }

    @Override
    public CompletableFuture<String> ping(@NonNull Route route) {
        return commandManager.submitNewCommand(Ping, new String[0], route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> ping(@NonNull String message, @NonNull Route route) {
        return commandManager.submitNewCommand(
            Ping, new String[]{message}, route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<GlideString> ping(@NonNull GlideString message, @NonNull Route route) {
        return commandManager.submitNewCommand(
                Ping, new GlideString[] {message}, route, this::handleGlideStringResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<String>> info() {
        return commandManager.submitNewCommand(
            Info, new String[0], response -> ClusterValue.of(handleMapResponse(response)));
    }

    public CompletableFuture<ClusterValue<String>> info(@NonNull Route route) {
        return commandManager.submitNewCommand(
            Info,
            new String[0],
            route,
            response ->
                route instanceof SingleNodeRoute
                    ? ClusterValue.of(handleStringResponse(response))
                    : ClusterValue.of(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<String>> info(@NonNull InfoOptions options) {
        return commandManager.submitNewCommand(
            Info, options.toArgs(), response -> ClusterValue.of(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<String>> info(
        @NonNull InfoOptions options, @NonNull Route route) {
        return commandManager.submitNewCommand(
            Info,
            options.toArgs(),
            route,
            response ->
                route instanceof SingleNodeRoute
                    ? ClusterValue.of(handleStringResponse(response))
                    : ClusterValue.of(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<Long> clientId() {
        return commandManager.submitNewCommand(ClientId, new String[0], this::handleLongResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<Long>> clientId(@NonNull Route route) {
        return commandManager.submitNewCommand(
            ClientId,
            new String[0],
            route,
            response ->
                route instanceof SingleNodeRoute
                    ? ClusterValue.of(handleLongResponse(response))
                    : ClusterValue.of(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<String> clientGetName() {
        return commandManager.submitNewCommand(
            ClientGetName, new String[0], this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<String>> clientGetName(@NonNull Route route) {
        return commandManager.submitNewCommand(
            ClientGetName,
            new String[0],
            route,
            response ->
                route instanceof SingleNodeRoute
                    ? ClusterValue.of(handleStringOrNullResponse(response))
                    : ClusterValue.of(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<String> configRewrite() {
        return commandManager.submitNewCommand(
            ConfigRewrite, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> configRewrite(@NonNull Route route) {
        return commandManager.submitNewCommand(
            ConfigRewrite, new String[0], route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> configResetStat() {
        return commandManager.submitNewCommand(
            ConfigResetStat, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> configResetStat(@NonNull Route route) {
        return commandManager.submitNewCommand(
            ConfigResetStat, new String[0], route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Map<String, String>> configGet(@NonNull String[] parameters) {
        return commandManager.submitNewCommand(ConfigGet, parameters, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<Map<String, String>>> configGet(
        @NonNull String[] parameters, @NonNull Route route) {
        return commandManager.submitNewCommand(
            ConfigGet,
            parameters,
            route,
            response ->
                route instanceof SingleNodeRoute
                    ? ClusterValue.ofSingleValue(handleMapResponse(response))
                    : ClusterValue.ofMultiValue(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<String> configSet(@NonNull Map<String, String> parameters) {
        return commandManager.submitNewCommand(
            ConfigSet, convertMapToKeyValueStringArray(parameters), this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> configSet(
        @NonNull Map<String, String> parameters, @NonNull Route route) {
        return commandManager.submitNewCommand(
            ConfigSet, convertMapToKeyValueStringArray(parameters), route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> echo(@NonNull String message) {
        return commandManager.submitNewCommand(
            Echo, new String[]{message}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<GlideString> echo(@NonNull GlideString message) {
        return commandManager.submitNewCommand(
            Echo, new GlideString[]{message}, this::handleGlideStringResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<String>> echo(
        @NonNull String message, @NonNull Route route) {
        return commandManager.submitNewCommand(
            Echo,
            new String[]{message},
            route,
            response ->
                route instanceof SingleNodeRoute
                    ? ClusterValue.ofSingleValue(handleStringResponse(response))
                    : ClusterValue.ofMultiValue(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<GlideString>> echo(
        @NonNull GlideString message, @NonNull Route route) {
        return commandManager.submitNewCommand(
            Echo,
            new GlideString[]{message},
            route,
            response ->
                route instanceof SingleNodeRoute
                    ? ClusterValue.ofSingleValue(handleGlideStringResponse(response))
                    : ClusterValue.ofMultiValueBinary(handleBinaryStringMapResponse(response)));
    }

    @Override
    public CompletableFuture<String[]> time() {
        return commandManager.submitNewCommand(
            Time, new String[0], response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<ClusterValue<String[]>> time(@NonNull Route route) {
        return commandManager.submitNewCommand(
            Time,
            new String[0],
            route,
            response ->
                route instanceof SingleNodeRoute
                    ? ClusterValue.ofSingleValue(castArray(handleArrayResponse(response), String.class))
                    : ClusterValue.ofMultiValue(
                    castMapOfArrays(handleMapResponse(response), String.class)));
    }

    @Override
    public CompletableFuture<Long> lastsave() {
        return commandManager.submitNewCommand(LastSave, new String[0], this::handleLongResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<Long>> lastsave(@NonNull Route route) {
        return commandManager.submitNewCommand(
            LastSave,
            new String[0],
            route,
            response ->
                route instanceof SingleNodeRoute
                    ? ClusterValue.of(handleLongResponse(response))
                    : ClusterValue.of(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<String> flushall() {
        return commandManager.submitNewCommand(FlushAll, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> flushall(@NonNull FlushMode mode) {
        return commandManager.submitNewCommand(
            FlushAll, new String[]{mode.toString()}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> flushall(@NonNull Route route) {
        return commandManager.submitNewCommand(
            FlushAll, new String[0], route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> flushall(@NonNull FlushMode mode, @NonNull Route route) {
        return commandManager.submitNewCommand(
            FlushAll, new String[]{mode.toString()}, route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> flushdb() {
        return commandManager.submitNewCommand(FlushDB, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> flushdb(@NonNull FlushMode mode) {
        return commandManager.submitNewCommand(
            FlushDB, new String[]{mode.toString()}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> flushdb(@NonNull Route route) {
        return commandManager.submitNewCommand(
            FlushDB, new String[0], route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> flushdb(@NonNull FlushMode mode, @NonNull Route route) {
        return commandManager.submitNewCommand(
            FlushDB, new String[]{mode.toString()}, route, this::handleStringResponse);
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
            new String[]{VERSION_REDIS_API, Integer.toString(version)},
            this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> lolwut(int version, int @NonNull [] parameters) {
        String[] arguments =
            concatenateArrays(
                new String[]{VERSION_REDIS_API, Integer.toString(version)},
                Arrays.stream(parameters).mapToObj(Integer::toString).toArray(String[]::new));
        return commandManager.submitNewCommand(Lolwut, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<String>> lolwut(@NonNull Route route) {
        return commandManager.submitNewCommand(
            Lolwut,
            new String[0],
            route,
            response ->
                route instanceof SingleNodeRoute
                    ? ClusterValue.ofSingleValue(handleStringResponse(response))
                    : ClusterValue.ofMultiValue(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<String>> lolwut(
        int @NonNull [] parameters, @NonNull Route route) {
        String[] arguments =
            Arrays.stream(parameters).mapToObj(Integer::toString).toArray(String[]::new);
        return commandManager.submitNewCommand(
            Lolwut,
            arguments,
            route,
            response ->
                route instanceof SingleNodeRoute
                    ? ClusterValue.ofSingleValue(handleStringResponse(response))
                    : ClusterValue.ofMultiValue(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<String>> lolwut(int version, @NonNull Route route) {
        return commandManager.submitNewCommand(
            Lolwut,
            new String[]{VERSION_REDIS_API, Integer.toString(version)},
            route,
            response ->
                route instanceof SingleNodeRoute
                    ? ClusterValue.ofSingleValue(handleStringResponse(response))
                    : ClusterValue.ofMultiValue(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<String>> lolwut(
        int version, int @NonNull [] parameters, @NonNull Route route) {
        String[] arguments =
            concatenateArrays(
                new String[]{VERSION_REDIS_API, Integer.toString(version)},
                Arrays.stream(parameters).mapToObj(Integer::toString).toArray(String[]::new));
        return commandManager.submitNewCommand(
            Lolwut,
            arguments,
            route,
            response ->
                route instanceof SingleNodeRoute
                    ? ClusterValue.ofSingleValue(handleStringResponse(response))
                    : ClusterValue.ofMultiValue(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<Long> dbsize() {
        return commandManager.submitNewCommand(DBSize, new String[0], this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> dbsize(@NonNull Route route) {
        return commandManager.submitNewCommand(DBSize, new String[0], route, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String> functionLoad(@NonNull String libraryCode, boolean replace) {
        String[] arguments =
            replace ? new String[]{REPLACE.toString(), libraryCode} : new String[]{libraryCode};
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
    public CompletableFuture<String> functionLoad(
        @NonNull String libraryCode, boolean replace, @NonNull Route route) {
        String[] arguments =
            replace ? new String[]{REPLACE.toString(), libraryCode} : new String[]{libraryCode};
        return commandManager.submitNewCommand(
            FunctionLoad, arguments, route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<GlideString> functionLoad(
            @NonNull GlideString libraryCode, boolean replace, @NonNull Route route) {
        GlideString[] arguments =
                replace
                        ? new GlideString[] {gs(REPLACE.toString()), libraryCode}
                        : new GlideString[] {libraryCode};
        return commandManager.submitNewCommand(
                FunctionLoad, arguments, route, this::handleGlideStringResponse);
    }

    /** Process a <code>FUNCTION LIST</code> cluster response. */
    protected ClusterValue<Map<String, Object>[]> handleFunctionListResponse(
        Response response, Route route) {
        if (route instanceof SingleNodeRoute) {
            Map<String, Object>[] data = handleFunctionListResponse(handleArrayResponse(response));
            return ClusterValue.ofSingleValue(data);
        } else {
            // each `Object` is a `Map<String, Object>[]` actually
            Map<String, Object> info = handleMapResponse(response);
            Map<String, Map<String, Object>[]> data = new HashMap<>();
            for (var nodeInfo : info.entrySet()) {
                data.put(nodeInfo.getKey(), handleFunctionListResponse((Object[]) nodeInfo.getValue()));
            }
            return ClusterValue.ofMultiValue(data);
        }
    }

    @Override
    public CompletableFuture<Map<String, Object>[]> functionList(boolean withCode) {
        return commandManager.submitNewCommand(
            FunctionList,
            withCode ? new String[]{WITH_CODE_REDIS_API} : new String[0],
            response -> handleFunctionListResponse(handleArrayResponse(response)));
    }

    @Override
    public CompletableFuture<Map<String, Object>[]> functionList(
        @NonNull String libNamePattern, boolean withCode) {
        return commandManager.submitNewCommand(
            FunctionList,
            withCode
                ? new String[]{LIBRARY_NAME_REDIS_API, libNamePattern, WITH_CODE_REDIS_API}
                : new String[]{LIBRARY_NAME_REDIS_API, libNamePattern},
            response -> handleFunctionListResponse(handleArrayResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<Map<String, Object>[]>> functionList(
        boolean withCode, @NonNull Route route) {
        return commandManager.submitNewCommand(
            FunctionList,
            withCode ? new String[]{WITH_CODE_REDIS_API} : new String[0],
            route,
            response -> handleFunctionListResponse(response, route));
    }

    @Override
    public CompletableFuture<ClusterValue<Map<String, Object>[]>> functionList(
        @NonNull String libNamePattern, boolean withCode, @NonNull Route route) {
        return commandManager.submitNewCommand(
            FunctionList,
            withCode
                ? new String[]{LIBRARY_NAME_REDIS_API, libNamePattern, WITH_CODE_REDIS_API}
                : new String[]{LIBRARY_NAME_REDIS_API, libNamePattern},
            route,
            response -> handleFunctionListResponse(response, route));
    }

    @Override
    public CompletableFuture<String> functionFlush() {
        return commandManager.submitNewCommand(
            FunctionFlush, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionFlush(@NonNull FlushMode mode) {
        return commandManager.submitNewCommand(
            FunctionFlush, new String[]{mode.toString()}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionFlush(@NonNull Route route) {
        return commandManager.submitNewCommand(
            FunctionFlush, new String[0], route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionFlush(@NonNull FlushMode mode, @NonNull Route route) {
        return commandManager.submitNewCommand(
            FunctionFlush, new String[]{mode.toString()}, route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionDelete(@NonNull String libName) {
        return commandManager.submitNewCommand(
            FunctionDelete, new String[]{libName}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionDelete(@NonNull GlideString libName) {
        return commandManager.submitNewCommand(
                FunctionDelete, new GlideString[] {libName}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionDelete(@NonNull String libName, @NonNull Route route) {
        return commandManager.submitNewCommand(
            FunctionDelete, new String[]{libName}, route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionDelete(
            @NonNull GlideString libName, @NonNull Route route) {
        return commandManager.submitNewCommand(
                FunctionDelete, new GlideString[] {libName}, route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<byte[]> functionDump() {
        return commandManager.submitNewCommand(
            FunctionDump, new GlideString[]{}, this::handleBytesOrNullResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<byte[]>> functionDump(@NonNull Route route) {
        return commandManager.submitNewCommand(
            FunctionDump,
            new GlideString[]{},
            route,
            response ->
                route instanceof SingleNodeRoute
                    ? ClusterValue.ofSingleValue(handleBytesOrNullResponse(response))
                    : ClusterValue.ofMultiValueBinary(handleBinaryStringMapResponse(response)));
    }

    @Override
    public CompletableFuture<String> functionRestore(byte @NonNull [] payload) {
        return commandManager.submitNewCommand(
            FunctionRestore, new GlideString[]{gs(payload)}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionRestore(
        byte @NonNull [] payload, @NonNull FunctionRestorePolicy policy) {
        return commandManager.submitNewCommand(
            FunctionRestore,
            new GlideString[]{gs(payload), gs(policy.toString())},
            this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionRestore(byte @NonNull [] payload, @NonNull Route route) {
        return commandManager.submitNewCommand(
            FunctionRestore, new GlideString[]{gs(payload)}, route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionRestore(
        byte @NonNull [] payload, @NonNull FunctionRestorePolicy policy, @NonNull Route route) {
        return commandManager.submitNewCommand(
            FunctionRestore,
            new GlideString[]{gs(payload), gs(policy.toString())},
            route,
            this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Object> fcall(@NonNull String function) {
        return fcall(function, new String[0]);
    }

    @Override
    public CompletableFuture<Object> fcall(@NonNull GlideString function) {
        return fcall(function, new GlideString[0]);
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> fcall(
        @NonNull String function, @NonNull Route route) {
        return fcall(function, new String[0], route);
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> fcall(
            @NonNull GlideString function, @NonNull Route route) {
        return fcall(function, new GlideString[0], route);
    }

    @Override
    public CompletableFuture<Object> fcall(@NonNull String function, @NonNull String[] arguments) {
        String[] args = concatenateArrays(new String[]{function, "0"}, arguments); // 0 - key count
        return commandManager.submitNewCommand(FCall, args, this::handleObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<Object> fcall(
            @NonNull GlideString function, @NonNull GlideString[] arguments) {
        GlideString[] args =
                concatenateArrays(new GlideString[] {function, gs("0")}, arguments); // 0 - key count
        return commandManager.submitNewCommand(FCall, args, this::handleObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> fcall(
        @NonNull String function, @NonNull String[] arguments, @NonNull Route route) {
        String[] args = concatenateArrays(new String[]{function, "0"}, arguments); // 0 - key count
        return commandManager.submitNewCommand(
            FCall,
            args,
            route,
            response ->
                route instanceof SingleNodeRoute
                    ? ClusterValue.ofSingleValue(handleObjectOrNullResponse(response))
                    : ClusterValue.ofMultiValue(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> fcall(
            @NonNull GlideString function, @NonNull GlideString[] arguments, @NonNull Route route) {
        GlideString[] args =
                concatenateArrays(new GlideString[] {function, gs("0")}, arguments); // 0 - key count
        return commandManager.submitNewCommand(
                FCall,
                args,
                route,
                response ->
                        route instanceof SingleNodeRoute
                                ? ClusterValue.ofSingleValue(handleObjectOrNullResponse(response))
                                : ClusterValue.ofMultiValue(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<Object> fcallReadOnly(@NonNull String function) {
        return fcallReadOnly(function, new String[0]);
    }

    @Override
    public CompletableFuture<Object> fcallReadOnly(@NonNull GlideString function) {
        return fcallReadOnly(function, new GlideString[0]);
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> fcallReadOnly(
        @NonNull String function, @NonNull Route route) {
        return fcallReadOnly(function, new String[0], route);
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> fcallReadOnly(
            @NonNull GlideString function, @NonNull Route route) {
        return fcallReadOnly(function, new GlideString[0], route);
    }

    @Override
    public CompletableFuture<Object> fcallReadOnly(
        @NonNull String function, @NonNull String[] arguments) {
        String[] args = concatenateArrays(new String[]{function, "0"}, arguments); // 0 - key count
        return commandManager.submitNewCommand(FCallReadOnly, args, this::handleObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<Object> fcallReadOnly(
            @NonNull GlideString function, @NonNull GlideString[] arguments) {
        GlideString[] args =
                concatenateArrays(new GlideString[] {function, gs("0")}, arguments); // 0 - key count
        return commandManager.submitNewCommand(FCallReadOnly, args, this::handleObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> fcallReadOnly(
        @NonNull String function, @NonNull String[] arguments, @NonNull Route route) {
        String[] args = concatenateArrays(new String[]{function, "0"}, arguments); // 0 - key count
        return commandManager.submitNewCommand(
            FCallReadOnly,
            args,
            route,
            response ->
                route instanceof SingleNodeRoute
                    ? ClusterValue.ofSingleValue(handleObjectOrNullResponse(response))
                    : ClusterValue.ofMultiValue(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> fcallReadOnly(
            @NonNull GlideString function, @NonNull GlideString[] arguments, @NonNull Route route) {
        GlideString[] args =
                concatenateArrays(new GlideString[] {function, gs("0")}, arguments); // 0 - key count
        return commandManager.submitNewCommand(
                FCallReadOnly,
                args,
                route,
                response ->
                        route instanceof SingleNodeRoute
                                ? ClusterValue.ofSingleValue(handleObjectOrNullResponse(response))
                                : ClusterValue.ofMultiValue(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<String> functionKill() {
        return commandManager.submitNewCommand(FunctionKill, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionKill(@NonNull Route route) {
        return commandManager.submitNewCommand(
            FunctionKill, new String[0], route, this::handleStringResponse);
    }

    /**
     * Process a <code>FUNCTION STATS</code> cluster response.
     */
    protected ClusterValue<Map<String, Map<String, Object>>> handleFunctionStatsResponse(
        Response response, boolean isSingleValue) {
        if (isSingleValue) {
            return ClusterValue.ofSingleValue(handleFunctionStatsResponse(handleMapResponse(response)));
        } else {
            Map<String, Map<String, Map<String, Object>>> data = handleMapResponse(response);
            for (var nodeInfo : data.entrySet()) {
                nodeInfo.setValue(handleFunctionStatsResponse(nodeInfo.getValue()));
            }
            return ClusterValue.ofMultiValue(data);
        }
    }

    @Override
    public CompletableFuture<ClusterValue<Map<String, Map<String, Object>>>> functionStats() {
        return commandManager.submitNewCommand(
            FunctionStats, new String[0], response -> handleFunctionStatsResponse(response, false));
    }

    @Override
    public CompletableFuture<ClusterValue<Map<String, Map<String, Object>>>> functionStats(
        @NonNull Route route) {
        return commandManager.submitNewCommand(
            FunctionStats,
            new String[0],
            route,
            response -> handleFunctionStatsResponse(response, route instanceof SingleNodeRoute));
    }

    @Override
    public CompletableFuture<String> unwatch(@NonNull Route route) {
        return commandManager.submitNewCommand(
            UnWatch, new String[0], route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> unwatch() {
        return commandManager.submitNewCommand(UnWatch, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> randomKey(@NonNull Route route) {
        return commandManager.submitNewCommand(
            RandomKey, new String[0], route, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String> randomKey() {
        return commandManager.submitNewCommand(
            RandomKey, new String[0], this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<Object[]> clusterScan(ClusterScanCursor cursor) {
        return commandManager.submitClusterScan(cursor, ScanOptions.builder().build(), this::handleArrayResponse)
            .thenApply(result -> new Object[] { new NativeClusterScanCursor(result[0].toString()), result[1] });
    }

    @Override
    public CompletableFuture<Object[]> clusterScan(ClusterScanCursor cursor, ScanOptions options) {
        return commandManager.submitClusterScan(cursor, options, this::handleArrayResponse)
            .thenApply(result -> new Object[] { new NativeClusterScanCursor(result[0].toString()), result[1] });
    }

    @Override
    public CompletableFuture<String> spublish(@NonNull String channel, @NonNull String message) {
        return commandManager.submitNewCommand(
                SPublish,
                new String[] {channel, message},
                response -> {
                    // Check, but ignore the number - it is never valid. A GLIDE bug/limitation TODO
                    handleLongResponse(response);
                    return OK;
                });
    }

    @Override
    public CompletableFuture<String[]> sort(
        @NonNull String key, @NonNull SortClusterOptions sortClusterOptions) {
        String[] arguments = ArrayUtils.addFirst(sortClusterOptions.toArgs(), key);
        return commandManager.submitNewCommand(
            Sort, arguments, response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> sort(
            @NonNull GlideString key, @NonNull SortClusterOptions sortClusterOptions) {
        GlideString[] arguments = ArrayUtils.addFirst(sortClusterOptions.toGlideStringArgs(), key);
        return commandManager.submitNewCommand(
                Sort,
                arguments,
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<String[]> sortReadOnly(
        @NonNull String key, @NonNull SortClusterOptions sortClusterOptions) {
        String[] arguments = ArrayUtils.addFirst(sortClusterOptions.toArgs(), key);
        return commandManager.submitNewCommand(
            SortReadOnly,
            arguments,
            response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> sortReadOnly(
            @NonNull GlideString key, @NonNull SortClusterOptions sortClusterOptions) {
        GlideString[] arguments = ArrayUtils.addFirst(sortClusterOptions.toGlideStringArgs(), key);
        return commandManager.submitNewCommand(
                SortReadOnly,
                arguments,
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Long> sortStore(
        @NonNull String key,
        @NonNull String destination,
        @NonNull SortClusterOptions sortClusterOptions) {
        String[] storeArguments = new String[]{STORE_COMMAND_STRING, destination};
        String[] arguments =
            concatenateArrays(new String[]{key}, sortClusterOptions.toArgs(), storeArguments);
        return commandManager.submitNewCommand(Sort, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> sortStore(
            @NonNull GlideString key,
            @NonNull GlideString destination,
            @NonNull SortClusterOptions sortClusterOptions) {
        GlideString[] storeArguments = new GlideString[] {gs(STORE_COMMAND_STRING), destination};
        GlideString[] arguments =
                concatenateArrays(
                        new GlideString[] {key}, sortClusterOptions.toGlideStringArgs(), storeArguments);
        return commandManager.submitNewCommand(Sort, arguments, this::handleLongResponse);
    }

    private static final class NativeClusterScanCursor implements ClusterScanCursor {
        // TODO: This should be made a constant in Rust.
        private static final String FINISHED_CURSOR_MARKER = "finished";

        private String cursorHandle;
        private boolean isFinished;
        private boolean isClosed = false;

        // This is for internal use only.
        public NativeClusterScanCursor(String cursorHandle) {
            this.cursorHandle = cursorHandle;
            this.isFinished = FINISHED_CURSOR_MARKER.equals(cursorHandle);
        }

        @Override
        public String getCursorHandle() {
            return cursorHandle;
        }

        @Override
        public boolean isFinished() {
            return isFinished;
        }

        @Override
        public void releaseCursorHandle() {
            internalClose();
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                // Release the native cursor
                this.internalClose();
            } finally {
                super.finalize();
            }
        }

        private void internalClose() {
            if (!isClosed) {
                ClusterScanCursorResolver.releaseNativeCursor(cursorHandle);

                // Mark the cursor as closed to avoid double-free (if close() gets called more than once).
                isClosed = true;
            }
        }
    }
}
