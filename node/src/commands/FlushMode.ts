/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

/**
 * Defines flushing mode for {@link GlideClient.flushall|flushall} and {@link GlideClient.flushdb|flushdb} commands.
 *
 * See https://valkey.io/commands/flushall/ and https://valkey.io/commands/flushdb/ for details.
 */
export enum FlushMode {
    /**
     * Flushes synchronously.
     *
     * since Valkey 6.2 and above.
     */
    SYNC = "SYNC",
    /** Flushes asynchronously. */
    ASYNC = "ASYNC",
}
