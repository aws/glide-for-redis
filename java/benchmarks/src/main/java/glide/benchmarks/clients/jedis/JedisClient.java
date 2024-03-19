/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.clients.jedis;

import glide.benchmarks.clients.SyncClient;
import glide.benchmarks.utils.ConnectionSettings;
import java.util.Set;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;

/** A Jedis client with sync capabilities. See: https://github.com/redis/jedis */
public class JedisClient implements SyncClient {
    boolean isClusterMode;
    private JedisPool jedisStandalonePool;
    private JedisCluster jedisCluster;

    @Override
    public void closeConnection() {
        if (jedisCluster != null) {
            jedisCluster.close();
        }
        if (jedisStandalonePool != null) {
            jedisStandalonePool.close();
        }
    }

    @Override
    public String getName() {
        return "jedis";
    }

    @Override
    public void connectToRedis(ConnectionSettings connectionSettings) {
        isClusterMode = connectionSettings.clusterMode;
        if (isClusterMode) {
            jedisCluster =
                    new JedisCluster(
                            Set.of(new HostAndPort(connectionSettings.host, connectionSettings.port)),
                            DefaultJedisClientConfig.builder().ssl(connectionSettings.useSsl).build());
        } else {
            jedisStandalonePool =
                    new JedisPool(
                            connectionSettings.host, connectionSettings.port, connectionSettings.useSsl);
        }
    }

    @Override
    public void set(String key, String value) {
        if (isClusterMode) {
            jedisCluster.set(key, value);
        } else {
            try (Jedis jedis = jedisStandalonePool.getResource()) {
                jedis.set(key, value);
            }
        }
    }

    @Override
    public String get(String key) {
        if (isClusterMode) {
            return jedisCluster.get(key);
        } else {
            try (Jedis jedis = jedisStandalonePool.getResource()) {
                return jedis.get(key);
            }
        }
    }
}
