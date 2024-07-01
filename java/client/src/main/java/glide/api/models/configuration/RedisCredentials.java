/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Represents the credentials for connecting to a Redis server.
 *
 * @example
 *     <pre>{@code
 * // credentials with username:
 * RedisCredentials credentials1 = RedisCredentials.builder()
 *     .username("GLIDE")
 *     .build();
 * // credentials with username and password:
 * RedisCredentials credentials2 = RedisCredentials.builder()
 *     .username("GLIDE")
 *     .password(pwd)
 *     .build();
 * }</pre>
 */
@Getter
@Builder
public class RedisCredentials {
    /** The password that will be used for authenticating connections to the Redis servers. */
    @NonNull private final String password;

    /**
     * The username that will be used for authenticating connections to the Redis servers. If not
     * supplied, "default" will be used.
     */
    private final String username;
}
