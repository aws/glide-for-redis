/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static glide.utils.ArrayTransformUtils.castArray;
import static glide.utils.ArrayTransformUtils.castMapOfArrays;
import static glide.utils.ArrayTransformUtils.convertMapToKeyValueStringArray;
import static redis_request.RedisRequestOuterClass.RequestType.ClientGetName;
import static redis_request.RedisRequestOuterClass.RequestType.ClientId;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigGet;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigResetStat;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigRewrite;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigSet;
import static redis_request.RedisRequestOuterClass.RequestType.CustomCommand;
import static redis_request.RedisRequestOuterClass.RequestType.Info;
import static redis_request.RedisRequestOuterClass.RequestType.Ping;
import static redis_request.RedisRequestOuterClass.RequestType.Time;

import glide.api.commands.ConnectionManagementClusterCommands;
import glide.api.commands.GenericClusterCommands;
import glide.api.commands.ServerManagementClusterCommands;
import glide.api.models.ClusterTransaction;
import glide.api.models.ClusterValue;
import glide.api.models.commands.InfoOptions;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;
import response.ResponseOuterClass.Response;

/**
 * Async (non-blocking) client for Redis in Cluster mode. Use {@link #CreateClient} to request a
 * client to Redis.
 */
public class RedisClusterClient extends BaseClient
        implements ConnectionManagementClusterCommands,
                GenericClusterCommands,
                ServerManagementClusterCommands {

    protected RedisClusterClient(ConnectionManager connectionManager, CommandManager commandManager) {
        super(connectionManager, commandManager);
    }

    /**
     * Async request for an async (non-blocking) Redis client in Cluster mode.
     *
     * @param config Redis cluster client Configuration
     * @return A Future to connect and return a RedisClusterClient
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
        if (route.isSingleNodeRoute()) {
            return ClusterValue.ofSingleValue(handleObjectOrNullResponse(response));
        }
        if (response.hasConstantResponse()) {
            return ClusterValue.ofSingleValue(handleStringResponse(response));
        }
        return ClusterValue.ofMultiValue(handleMapResponse(response));
    }

    @Override
    public CompletableFuture<Object[]> exec(@NonNull ClusterTransaction transaction) {
        return commandManager.submitNewCommand(
                transaction, Optional.empty(), this::handleArrayOrNullResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<Object>[]> exec(
            @NonNull ClusterTransaction transaction, Route route) {
        return commandManager
                .submitNewCommand(transaction, Optional.ofNullable(route), this::handleArrayOrNullResponse)
                .thenApply(
                        objects ->
                                Arrays.stream(objects)
                                        .map(ClusterValue::of)
                                        .<ClusterValue<Object>>toArray(ClusterValue[]::new));
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
    public CompletableFuture<String> ping(@NonNull Route route) {
        return commandManager.submitNewCommand(Ping, new String[0], route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> ping(@NonNull String message, @NonNull Route route) {
        return commandManager.submitNewCommand(
                Ping, new String[] {message}, route, this::handleStringResponse);
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
                        route.isSingleNodeRoute()
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
                        route.isSingleNodeRoute()
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
                        route.isSingleNodeRoute()
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
                        route.isSingleNodeRoute()
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
                        route.isSingleNodeRoute()
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
                        route.isSingleNodeRoute()
                                ? ClusterValue.ofSingleValue(castArray(handleArrayResponse(response), String.class))
                                : ClusterValue.ofMultiValue(
                                        castMapOfArrays(handleMapResponse(response), String.class)));
    }
}
