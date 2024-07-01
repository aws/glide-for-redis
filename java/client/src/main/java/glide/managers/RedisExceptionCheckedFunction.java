/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import glide.api.models.exceptions.RedisException;

/**
 * Functional Interface to convert values and throw RedisException when encountering an error state.
 *
 * @param <R> type to evaluate
 * @param <T> payload type
 */
@FunctionalInterface
public interface RedisExceptionCheckedFunction<R, T> {

    /**
     * Functional response handler that takes a value of type R and returns a payload of type T.
     * Throws RedisException when encountering an invalid or error state.
     *
     * @param value - received value type
     * @return T - returning payload type
     * @throws RedisException
     */
    T apply(R value) throws RedisException;
}
