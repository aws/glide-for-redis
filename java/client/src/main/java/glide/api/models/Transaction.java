/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static glide.api.commands.GenericBaseCommands.REPLACE_REDIS_API;
import static glide.api.commands.GenericCommands.DB_REDIS_API;
import static glide.api.models.commands.SortBaseOptions.STORE_COMMAND_STRING;
import static redis_request.RedisRequestOuterClass.RequestType.Copy;
import static redis_request.RedisRequestOuterClass.RequestType.Move;
import static redis_request.RedisRequestOuterClass.RequestType.Select;
import static redis_request.RedisRequestOuterClass.RequestType.Sort;
import static redis_request.RedisRequestOuterClass.RequestType.SortReadOnly;

import glide.api.models.commands.SortOptions;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.apache.commons.lang3.ArrayUtils;

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
        protobufTransaction.addCommands(buildCommand(Select, this.buildArgs(Long.toString(index))));
        return this;
    }

    /**
     * Move <code>key</code> from the currently selected database to the database specified by <code>
     * dbIndex</code>.
     *
     * @see <a href="https://redis.io/commands/move/">redis.io</a> for more details.
     * @param key The key to move.
     * @param dbIndex The index of the database to move <code>key</code> to.
     * @return Command Response - <code>true</code> if <code>key</code> was moved, or <code>false
     *     </code> if the <code>key</code> already exists in the destination database or does not
     *     exist in the source database.
     */
    public <ArgType> Transaction move(ArgType key, long dbIndex) {
        protobufTransaction.addCommands(buildCommand(Move, newArgsBuilder().add(key).add(dbIndex)));
        return this;
    }

    /**
     * Copies the value stored at the <code>source</code> to the <code>destination</code> key on
     * <code>destinationDB</code>. When <code>replace</code> is true, removes the <code>destination
     * </code> key first if it already exists, otherwise performs no action.
     *
     * @since Redis 6.2.0 and above.
     * @see <a href="https://redis.io/commands/copy/">redis.io</a> for details.
     * @param source The key to the source value.
     * @param destination The key where the value should be copied to.
     * @param destinationDB The alternative logical database index for the destination key.
     * @return Command Response - <code>true</code> if <code>source</code> was copied, <code>false
     *     </code> if <code>source</code> was not copied.
     */
    public Transaction copy(@NonNull String source, @NonNull String destination, long destinationDB) {
        return copy(source, destination, destinationDB, false);
    }

    /**
     * Copies the value stored at the <code>source</code> to the <code>destination</code> key on
     * <code>destinationDB</code>. When <code>replace</code> is true, removes the <code>destination
     * </code> key first if it already exists, otherwise performs no action.
     *
     * @since Redis 6.2.0 and above.
     * @see <a href="https://redis.io/commands/copy/">redis.io</a> for details.
     * @param source The key to the source value.
     * @param destination The key where the value should be copied to.
     * @param destinationDB The alternative logical database index for the destination key.
     * @param replace If the destination key should be removed before copying the value to it.
     * @return Command Response - <code>true</code> if <code>source</code> was copied, <code>false
     *     </code> if <code>source</code> was not copied.
     */
    public Transaction copy(
            @NonNull String source, @NonNull String destination, long destinationDB, boolean replace) {
        String[] args = new String[] {source, destination, DB_REDIS_API, Long.toString(destinationDB)};
        if (replace) {
            args = ArrayUtils.add(args, REPLACE_REDIS_API);
        }
        protobufTransaction.addCommands(buildCommand(Copy, this.buildArgs(args)));
        return this;
    }

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and returns the result.
     * The <code>sort</code> command can be used to sort elements based on different criteria and
     * apply transformations on sorted elements.<br>
     * To store the result into a new key, see {@link #sortStore(String, String, SortOptions)}.
     *
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param sortOptions The {@link SortOptions}.
     * @return Command Response - An <code>Array</code> of sorted elements.
     */
    public <ArgType> Transaction sort(@NonNull ArgType key, @NonNull SortOptions sortOptions) {
        protobufTransaction.addCommands(
                buildCommand(Sort, newArgsBuilder().add(key).add(sortOptions.toArgs())));
        return this;
    }

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and returns the result.
     * The <code>sortReadOnly</code> command can be used to sort elements based on different criteria
     * and apply transformations on sorted elements.<br>
     *
     * @since Redis 7.0 and above.
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param sortOptions The {@link SortOptions}.
     * @return Command Response - An <code>Array</code> of sorted elements.
     */
    public <ArgType> Transaction sortReadOnly(
            @NonNull ArgType key, @NonNull SortOptions sortOptions) {
        protobufTransaction.addCommands(
                buildCommand(SortReadOnly, newArgsBuilder().add(key).add(sortOptions.toArgs())));
        return this;
    }

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and stores the result in
     * <code>destination</code>. The <code>sort</code> command can be used to sort elements based on
     * different criteria, apply transformations on sorted elements, and store the result in a new
     * key.<br>
     * To get the sort result without storing it into a key, see {@link #sort(String, SortOptions)}.
     *
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param sortOptions The {@link SortOptions}.
     * @param destination The key where the sorted result will be stored.
     * @return Command Response - The number of elements in the sorted key stored at <code>destination
     *     </code>.
     */
    public <ArgType> Transaction sortStore(
            @NonNull ArgType key, @NonNull ArgType destination, @NonNull SortOptions sortOptions) {
        protobufTransaction.addCommands(
                buildCommand(
                        Sort,
                        newArgsBuilder()
                                .add(key)
                                .add(sortOptions.toArgs())
                                .add(STORE_COMMAND_STRING)
                                .add(destination)));
        return this;
    }
}
