/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import glide.api.RedisClient;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClientConfiguration;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10) // seconds
public class ConnectionTests {

    @Test
    @SneakyThrows
    public void basic_client() {
        var regularClient =
                RedisClient.CreateClient(
                                RedisClientConfiguration.builder()
                                        .address(
                                                NodeAddress.builder().port(TestConfiguration.STANDALONE_PORTS[0]).build())
                                        .build())
                        .get();
        regularClient.close();
    }

    @Test
    @SneakyThrows
    public void cluster_client() {
        var regularClient =
                RedisClient.CreateClient(
                                RedisClientConfiguration.builder()
                                        .address(NodeAddress.builder().port(TestConfiguration.CLUSTER_PORTS[0]).build())
                                        .build())
                        .get();
        regularClient.close();
    }
}
