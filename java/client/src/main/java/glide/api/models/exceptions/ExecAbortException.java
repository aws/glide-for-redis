/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.exceptions;

/** Redis client error: Errors that are thrown when a transaction is aborted. */
public class ExecAbortException extends RedisException {
    public ExecAbortException(String message) {
        super(message);
    }
}
