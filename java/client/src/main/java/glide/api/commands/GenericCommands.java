/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.Transaction;
import java.util.concurrent.CompletableFuture;

/** Generic Commands interface to handle generic command and transaction requests. */
public interface GenericCommands {

    /**
     * Executes a single command, without checking inputs. Every part of the command, including
     * subcommands, should be added as a separate value in args.
     *
     * @remarks This function should only be used for single-response commands. Commands that don't
     *     return response (such as <em>SUBSCRIBE</em>), or that return potentially more than a single
     *     response (such as <em>XREAD</em>), or that change the client's behavior (such as entering
     *     <em>pub</em>/<em>sub</em> mode on <em>RESP2</em> connections) shouldn't be called using
     *     this function.
     * @example Returns a list of all pub/sub clients:
     *     <pre>
     * Object result = client.customCommand(new String[]{ "CLIENT", "LIST", "TYPE", "PUBSUB" }).get();
     * </pre>
     *
     * @param args Arguments for the custom command.
     * @return Response from Redis containing an <code>Object</code>.
     */
    CompletableFuture<Object> customCommand(String[] args);

    /**
     * Execute a transaction by processing the queued commands.
     *
     * @see <a href="https://redis.io/topics/Transactions/">redis.io</a> for details on Redis
     *     Transactions.
     * @param transaction A {@link Transaction} object containing a list of commands to be executed.
     * @return A list of results corresponding to the execution of each command in the transaction.
     * @remarks
     *     <ul>
     *       <li>If a command returns a value, it will be included in the list. If a command doesn't
     *           return a value, the list entry will be empty.
     *       <li>If the transaction failed due to a <em>WATCH</em> command, <code>exec</code> will
     *           return <code>null</code>.
     *     </ul>
     */
    CompletableFuture<Object[]> exec(Transaction transaction);
}
