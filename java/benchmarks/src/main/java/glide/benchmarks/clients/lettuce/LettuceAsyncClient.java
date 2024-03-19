/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.clients.lettuce;

import glide.benchmarks.clients.AsyncClient;
import glide.benchmarks.utils.ConnectionSettings;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisStringAsyncCommands;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import java.time.Duration;

/** A Lettuce client with async capabilities see: https://lettuce.io/ */
public class LettuceAsyncClient implements AsyncClient<String> {
    static final int ASYNC_OPERATION_TIMEOUT_SEC = 1;

    private AbstractRedisClient client;
    private RedisStringAsyncCommands<String, String> asyncCommands;
    private StatefulConnection<String, String> connection;

    @Override
    public void connectToRedis(ConnectionSettings connectionSettings) {
        RedisURI uri =
                RedisURI.builder()
                        .withHost(connectionSettings.host)
                        .withPort(connectionSettings.port)
                        .withSsl(connectionSettings.useSsl)
                        .build();
        if (!connectionSettings.clusterMode) {
            client = RedisClient.create(uri);
            connection = ((RedisClient) client).connect();
            asyncCommands = ((StatefulRedisConnection<String, String>) connection).async();
        } else {
            client = RedisClusterClient.create(uri);
            connection = ((RedisClusterClient) client).connect();
            asyncCommands = ((StatefulRedisClusterConnection<String, String>) connection).async();
        }
        connection.setTimeout(Duration.ofSeconds(ASYNC_OPERATION_TIMEOUT_SEC));
    }

    @Override
    public RedisFuture<String> asyncSet(String key, String value) {
        return asyncCommands.set(key, value);
    }

    @Override
    public RedisFuture<String> asyncGet(String key) {
        return asyncCommands.get(key);
    }

    @Override
    public void closeConnection() {
        connection.close();
        client.shutdown();
    }

    @Override
    public String getName() {
        return "lettuce";
    }
}
