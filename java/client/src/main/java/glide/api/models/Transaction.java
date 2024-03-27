/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static redis_request.RedisRequestOuterClass.RequestType.Select;

import lombok.AllArgsConstructor;
import redis_request.RedisRequestOuterClass;

/**
 * Extends BaseTransaction class for Redis standalone commands. Transactions allow the execution of
 * a group of commands in a single step.
 *
 * <p>Command Response: An array of command responses is returned by the client <code>exec</code>
 * command, in the order they were given. Each element in the array represents a command given to
 * the <code>Transaction</code>. The response for each command depends on the executed Redis
 * command. Specific response types are documented alongside each method.
 *
 * @example
 *     <pre>{@code
 * Transaction transaction = new Transaction()
 *     .set("key", "value")
 *     .get("key");
 * Object[] result = client.exec(transaction).get();
 * // result contains: OK and "value"
 * assert result[0].equals("OK");
 * assert result[1].equals("value");
 * }</pre>
 */
@AllArgsConstructor
public class Transaction extends BaseTransaction<Transaction> {
    @Override
    protected Transaction getThis() {
        return this;
    }

    /**
     * Changes the currently selected Redis database.
     *
     * @see <a href="https://redis.io/commands/select/">redis.io</a> for details.
     * @param index The index of the database to select.
     * @return Command Response - A simple <code>OK</code> response.
     */
    public Transaction select(long index) {
        RedisRequestOuterClass.Command.ArgsArray commandArgs = buildArgs(Long.toString(index));

        protobufTransaction.addCommands(buildCommand(Select, commandArgs));
        return this;
    }
}
