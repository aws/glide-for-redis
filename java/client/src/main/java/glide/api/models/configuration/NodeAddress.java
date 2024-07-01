/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Represents the address and port of a node in the cluster or in standalone installation.
 *
 * @example
 *     <pre>{@code
 * NodeAddress address1 = NodeAddress.builder().build(); // default parameters: localhost:6379
 * NodeAddress address2 = NodeAddress.builder().port(6380).build(); // localhost:6380
 * NodeAddress address2 = NodeAddress.builder().address("my.cloud.com").port(12345).build(); // custom address
 * }</pre>
 */
@Getter
@Builder
public class NodeAddress {
    public static String DEFAULT_HOST = "localhost";
    public static Integer DEFAULT_PORT = 6379;

    @NonNull @Builder.Default private final String host = DEFAULT_HOST;
    @NonNull @Builder.Default private final Integer port = DEFAULT_PORT;
}
