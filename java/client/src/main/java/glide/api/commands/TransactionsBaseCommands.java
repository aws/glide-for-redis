/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.GlideString;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Transactions Commands" group for standalone and cluster clients.
 *
 * @see <a href="https://redis.io/commands/?group=transactions">Transactions Commands</a>
 */
public interface TransactionsBaseCommands {
    /**
     * Marks the given keys to be watched for conditional execution of a transaction. Transactions
     * will only execute commands if the watched keys are not modified before execution of the
     * transaction.
     *
     * @apiNote When in cluster mode, the command may route to multiple nodes when <code>keys</code>
     *     map to different hash slots.
     * @see <a href="https://redis.io/docs/latest/commands/watch/">redis.io</a> for details.
     * @param keys The keys to watch.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * assert client.watch(new String[] {"sampleKey"}).get().equals("OK");
     * transaction.set("sampleKey", "foobar");
     * Object[] result = client.exec(transaction).get();
     * assert result != null; // Executes successfully and keys are unwatched.
     *
     * assert client.watch(new String[] {"sampleKey"}).get().equals("OK");
     * transaction.set("sampleKey", "foobar");
     * assert client.set("sampleKey", "hello world").get().equals("OK");
     * Object[] result = client.exec(transaction).get();
     * assert result == null; // null is returned when the watched key is modified before transaction execution.
     * }</pre>
     */
    CompletableFuture<String> watch(String[] keys);

    /**
     * Marks the given keys to be watched for conditional execution of a transaction. Transactions
     * will only execute commands if the watched keys are not modified before execution of the
     * transaction.
     *
     * @apiNote When in cluster mode, the command may route to multiple nodes when <code>keys</code>
     *     map to different hash slots.
     * @see <a href="https://redis.io/docs/latest/commands/watch/">redis.io</a> for details.
     * @param keys The keys to watch.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * assert client.watch(new GlideString[] {gs("sampleKey")}).get().equals("OK");
     * transaction.set(gs("sampleKey"), gs("foobar"));
     * Object[] result = client.exec(transaction).get();
     * assert result != null; // Executes successfully and keys are unwatched.
     *
     * assert client.watch(new GlideString[] {gs("sampleKey")}).get().equals("OK");
     * transaction.set(gs("sampleKey"), gs("foobar"));
     * assert client.set(gs("sampleKey"), gs("hello world")).get().equals("OK");
     * Object[] result = client.exec(transaction).get();
     * assert result == null; // null is returned when the watched key is modified before transaction execution.
     * }</pre>
     */
    CompletableFuture<String> watch(GlideString[] keys);
}
