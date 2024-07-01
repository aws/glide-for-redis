/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Transactions Commands" group for cluster clients.
 *
 * @see <a href="https://redis.io/commands/?group=transactions">Transactions Commands</a>
 */
public interface TransactionsClusterCommands {
    /**
     * Flushes all the previously watched keys for a transaction. Executing a transaction will
     * automatically flush all previously watched keys.<br>
     * The command will be routed to all primary nodes.
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

    /**
     * Flushes all the previously watched keys for a transaction. Executing a transaction will
     * automatically flush all previously watched keys.
     *
     * @see <a href="https://redis.io/docs/latest/commands/unwatch/">redis.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * assert client.watch(new String[] {"sampleKey"}).get().equals("OK");
     * assert client.unwatch(ALL_PRIMARIES).get().equals("OK"); // Flushes "sampleKey" from watched keys for all primary nodes.
     * }</pre>
     */
    CompletableFuture<String> unwatch(Route route);
}
