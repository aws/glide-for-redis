/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Transactions Commands" group for standalone clients.
 *
 * @see <a href="https://redis.io/commands/?group=transactions">Transactions Commands</a>
 */
public interface TransactionsCommands {
    /**
     * Flushes all the previously watched keys for a transaction. Executing a transaction will
     * automatically flush all previously watched keys.
     *
     * @see <a href="https://redis.io/docs/latest/commands/unwatch/">redis.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * assert client.watch(new String[] {"sampleKey"}).get().equals("OK");
     * assert client.unwatch().get().equals("OK"); // Flushes "sampleKey" from watched keys.
     * }</pre>
     */
    CompletableFuture<String> unwatch();
}
