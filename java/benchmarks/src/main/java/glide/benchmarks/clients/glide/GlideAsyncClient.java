/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.clients.glide;

import static java.util.concurrent.TimeUnit.SECONDS;

import glide.api.BaseClient;
import glide.api.RedisClient;
import glide.api.RedisClusterClient;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClientConfiguration;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import glide.benchmarks.clients.AsyncClient;
import glide.benchmarks.utils.ConnectionSettings;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** A Glide client with async capabilities */
public class GlideAsyncClient implements AsyncClient<String> {
    private BaseClient redisClient;

    @Override
    public void connectToRedis(ConnectionSettings connectionSettings) {

        if (connectionSettings.clusterMode) {
            RedisClusterClientConfiguration config =
                    RedisClusterClientConfiguration.builder()
                            .address(
                                    NodeAddress.builder()
                                            .host(connectionSettings.host)
                                            .port(connectionSettings.port)
                                            .build())
                            .useTLS(connectionSettings.useSsl)
                            .build();
            try {
                redisClient = RedisClusterClient.CreateClient(config).get(10, SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }

        } else {
            RedisClientConfiguration config =
                    RedisClientConfiguration.builder()
                            .address(
                                    NodeAddress.builder()
                                            .host(connectionSettings.host)
                                            .port(connectionSettings.port)
                                            .build())
                            .useTLS(connectionSettings.useSsl)
                            .build();

            try {
                redisClient = RedisClient.CreateClient(config).get(10, SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public CompletableFuture<String> asyncSet(String key, String value) {
        return redisClient.set(key, value);
    }

    @Override
    public CompletableFuture<String> asyncGet(String key) {
        return redisClient.get(key);
    }

    @Override
    public void closeConnection() {
        try {
            redisClient.close();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getName() {
        return "glide";
    }
}
