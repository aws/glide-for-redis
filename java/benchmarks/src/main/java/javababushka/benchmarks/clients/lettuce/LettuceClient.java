package javababushka.benchmarks.clients.lettuce;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisStringCommands;
import javababushka.benchmarks.clients.SyncClient;
import javababushka.benchmarks.utils.ConnectionSettings;

/** A Lettuce client with sync capabilities see: https://lettuce.io/ */
public class LettuceClient implements SyncClient {

  RedisClient client;
  RedisStringCommands syncCommands;
  StatefulRedisConnection<String, String> connection;

  @Override
  public void connectToRedis() {
    connectToRedis(new ConnectionSettings("localhost", 6379, false, false));
  }

  @Override
  public void connectToRedis(ConnectionSettings connectionSettings) {
    client =
        RedisClient.create(
            String.format(
                "%s://%s:%d",
                connectionSettings.useSsl ? "rediss" : "redis",
                connectionSettings.host,
                connectionSettings.port));
    connection = client.connect();
    syncCommands = connection.sync();
  }

  @Override
  public void set(String key, String value) {
    syncCommands.set(key, value);
  }

  @Override
  public String get(String key) {
    return (String) syncCommands.get(key);
  }

  @Override
  public void closeConnection() {
    connection.close();
    client.shutdown();
  }

  @Override
  public String getName() {
    return "Lettuce";
  }
}
