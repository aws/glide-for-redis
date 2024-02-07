/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

\import glide.api.models.ClusterTransaction;
import static redis_request.RedisRequestOuterClass.RequestType.CustomCommand;
import static redis_request.RedisRequestOuterClass.RequestType.Info;
import static redis_request.RedisRequestOuterClass.RequestType.Ping;

import glide.api.commands.ConnectionManagementClusterCommands;
import glide.api.commands.GenericClusterCommands;
import glide.api.commands.ServerManagementClusterCommands;
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
            RedisClusterClientConfiguration config) {
        return CreateClient(config, RedisClusterClient::new);
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> customCommand(String[] args) {
        // TODO if a command returns a map as a single value, ClusterValue misleads user
        return commandManager.submitNewCommand(
                CustomCommand, args, response -> ClusterValue.of(handleObjectOrNullResponse(response)));
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<ClusterValue<Object>> customCommand(String[] args, Route route) {
        return commandManager.submitNewCommand(
                CustomCommand,
                args,
                route,
                response ->
                        route.isSingleNodeRoute()
                                ? ClusterValue.ofSingleValue(handleObjectOrNullResponse(response))
                                : ClusterValue.ofMultiValue(
                                        (Map<String, Object>) handleObjectOrNullResponse(response)));
    }

    @Override
    public CompletableFuture<Object[]> exec(ClusterTransaction transaction) {
        return commandManager.submitNewCommand(
            transaction, Optional.empty(), this::handleArrayResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<Object>[]> exec(
            ClusterTransaction transaction, Route route) {
        return commandManager
                .submitNewCommand(transaction, Optional.ofNullable(route), this::handleArrayResponse)
                .thenApply(
                        objects ->
                                Arrays.stream(objects)
                                        .map(ClusterValue::of)
                                        .<ClusterValue<Object>>toArray(ClusterValue[]::new));
    }

    @Override
    public CompletableFuture<String> ping(@NonNull Route route) {
        return commandManager.submitNewCommand(Ping, new String[0], route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> ping(@NonNull String str, @NonNull Route route) {
        return commandManager.submitNewCommand(
                Ping, new String[] {str}, route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<String>> info() {
        return commandManager.submitNewCommand(
                Info, new String[0], response -> ClusterValue.of(handleObjectResponse(response)));
    }

    public CompletableFuture<ClusterValue<String>> info(@NonNull Route route) {
                Info, new String[0], route, response -> ClusterValue.of(handleObjectResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<String>> info(@NonNull InfoOptions options) {
        return commandManager.submitNewCommand(
                Info, options.toArgs(), response -> ClusterValue.of(handleObjectResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<String>> info(
            @NonNull InfoOptions options, @NonNull Route route) {
        return commandManager.submitNewCommand(
                Info, options.toArgs(), route, response -> ClusterValue.of(handleObjectResponse(response)));
}
