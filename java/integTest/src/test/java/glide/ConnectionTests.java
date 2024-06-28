/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import glide.api.GlideClient;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10) // seconds
public class ConnectionTests {

    @Test
    @SneakyThrows
    public void basic_client() {
        var regularClient =
                GlideClient.CreateClient(
                                GlideClientConfiguration.builder()
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
                GlideClient.CreateClient(
                                GlideClientConfiguration.builder()
                                        .address(NodeAddress.builder().port(TestConfiguration.CLUSTER_PORTS[0]).build())
                                        .build())
                        .get();
        regularClient.close();
    }
}
