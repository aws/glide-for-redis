/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.configuration.ReadFrom;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Scripting and Function" group for standalone and
 * cluster clients.
 *
 * @see <a href="https://redis.io/docs/latest/commands/?group=scripting">Scripting and Function
 *     Commands</a>
 */
public interface ScriptingAndFunctionsBaseCommands {

    /**
     * Invokes a previously loaded function.<br>
     * This command is routed to primary nodes only.<br>
     * To route to a replica please refer to {@link #fcallReadOnly}.
     *
     * @apiNote When in cluster mode
     *     <ul>
     *       <li>all <code>keys</code> must map to the same hash slot.
     *       <li>if no <code>keys</code> are given, command will be routed to a random primary node.
     *     </ul>
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/fcall/">redis.io</a> for details.
     * @param function The function name.
     * @param keys An <code>array</code> of keys accessed by the function. To ensure the correct
     *     execution of functions, both in standalone and clustered deployments, all names of keys
     *     that a function accesses must be explicitly provided as <code>keys</code>.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @return The invoked function's return value.
     * @example
     *     <pre>{@code
     * String[] args = new String[] { "Answer", "to", "the", "Ultimate", "Question", "of", "Life,", "the", "Universe,", "and", "Everything"};
     * Object response = client.fcall("Deep_Thought", new String[0], args).get();
     * assert response == 42L;
     * }</pre>
     */
    CompletableFuture<Object> fcall(String function, String[] keys, String[] arguments);

    /**
     * Invokes a previously loaded read-only function.<br>
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * @apiNote When in cluster mode
     *     <ul>
     *       <li>all <code>keys</code> must map to the same hash slot.
     *       <li>if no <code>keys</code> are given, command will be routed to a random node.
     *     </ul>
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/fcall_ro/">redis.io</a> for details.
     * @param function The function name.
     * @param keys An <code>array</code> of keys accessed by the function. To ensure the correct
     *     execution of functions, both in standalone and clustered deployments, all names of keys
     *     that a function accesses must be explicitly provided as <code>keys</code>.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @return The invoked function's return value.
     * @example
     *     <pre>{@code
     * String[] args = new String[] { "Answer", "to", "the", "Ultimate", "Question", "of", "Life,", "the", "Universe,", "and", "Everything"};
     * Object response = client.fcallReadOnly("Deep_Thought", new String[0], args).get();
     * assert response == 42L;
     * }</pre>
     */
    CompletableFuture<Object> fcallReadOnly(String function, String[] keys, String[] arguments);
}
