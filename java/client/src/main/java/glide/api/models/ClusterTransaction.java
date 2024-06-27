/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static command_request.CommandRequestOuterClass.RequestType.Sort;
import static command_request.CommandRequestOuterClass.RequestType.SortReadOnly;
import static glide.api.models.commands.SortBaseOptions.STORE_COMMAND_STRING;
import static glide.utils.ArrayTransformUtils.concatenateArrays;

import command_request.CommandRequestOuterClass;
import glide.api.models.commands.SortClusterOptions;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.apache.commons.lang3.ArrayUtils;

/**
 * Extends BaseTransaction class for cluster mode commands. Transactions allow the execution of a
 * group of commands in a single step.
 *
 * <p>Command Response: An array of command responses is returned by the client <code>exec</code>
 * command, in the order they were given. Each element in the array represents a command given to
 * the <code>Transaction</code>. The response for each command depends on the executed Redis
 * command. Specific response types are documented alongside each method.
 *
 * @example
 *     <pre>
 *  ClusterTransaction transaction = new ClusterTransaction();
 *    .set("key", "value");
 *    .get("key");
 *  ClusterValue[] result = client.exec(transaction, route).get();
 *  // result contains: OK and "value"
 *  </pre>
 */
@AllArgsConstructor
public class ClusterTransaction extends BaseTransaction<ClusterTransaction> {
    @Override
    protected ClusterTransaction getThis() {
        return this;
    }

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and returns the result.
     * <br>
     * The <code>sort</code> command can be used to sort elements based on different criteria and
     * apply transformations on sorted elements.<br>
     * To store the result into a new key, see {@link #sortStore(String, String, SortClusterOptions)}.
     *
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param sortClusterOptions The {@link SortClusterOptions}.
     * @return Command Response - An <code>Array</code> of sorted elements.
     */
    public ClusterTransaction sort(
            @NonNull String key, @NonNull SortClusterOptions sortClusterOptions) {
        CommandRequestOuterClass.Command.ArgsArray commandArgs =
                buildArgs(ArrayUtils.addFirst(sortClusterOptions.toArgs(), key));
        protobufTransaction.addCommands(buildCommand(Sort, commandArgs));
        return this;
    }

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and returns the result.
     * <br>
     * The <code>sortReadOnly</code> command can be used to sort elements based on different criteria
     * and apply transformations on sorted elements.<br>
     *
     * @since Redis 7.0 and above.
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param sortClusterOptions The {@link SortClusterOptions}.
     * @return Command Response - An <code>Array</code> of sorted elements.
     */
    public ClusterTransaction sortReadOnly(
            @NonNull String key, @NonNull SortClusterOptions sortClusterOptions) {
        CommandRequestOuterClass.Command.ArgsArray commandArgs =
                buildArgs(ArrayUtils.addFirst(sortClusterOptions.toArgs(), key));
        protobufTransaction.addCommands(buildCommand(SortReadOnly, commandArgs));
        return this;
    }

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and stores the result in
     * <code>destination</code>. The <code>sort</code> command can be used to sort elements based on
     * different criteria, apply transformations on sorted elements, and store the result in a new
     * key.<br>
     * To get the sort result without storing it into a key, see {@link #sort(String,
     * SortClusterOptions)} or {@link #sortReadOnly(String, SortClusterOptions)}.
     *
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param destination The key where the sorted result will be stored.
     * @param sortClusterOptions The {@link SortClusterOptions}.
     * @return Command Response - The number of elements in the sorted key stored at <code>destination
     *     </code>.
     */
    public ClusterTransaction sortStore(
            @NonNull String key,
            @NonNull String destination,
            @NonNull SortClusterOptions sortClusterOptions) {
        String[] storeArguments = new String[] {STORE_COMMAND_STRING, destination};
        CommandRequestOuterClass.Command.ArgsArray commandArgs =
                buildArgs(
                        concatenateArrays(new String[] {key}, sortClusterOptions.toArgs(), storeArguments));
        protobufTransaction.addCommands(buildCommand(Sort, commandArgs));
        return this;
    }
}
