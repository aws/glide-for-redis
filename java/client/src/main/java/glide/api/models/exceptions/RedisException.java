/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.exceptions;

/** Redis client error: Base class for errors. */
public class RedisException extends RuntimeException {
    public RedisException(String message) {
        super(message);
    }
}
