/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "HyperLogLog Commands" group for standalone and
 * cluster clients.
 *
 * @see <a href="https://redis.io/commands/?group=hyperloglog">HyperLogLog Commands</a>
 */
public interface HyperLogLogBaseCommands {

    /**
     * Adds all elements to the HyperLogLog data structure stored at the specified <code>key</code>.
     * <br>
     * Creates a new structure if the <code>key</code> does not exist.
     *
     * <p>When no <code>elements</code> are provided, and <code>key</code> exists and is a
     * HyperLogLog, then no operation is performed. If <code>key</code> does not exist, then the
     * HyperLogLog structure is created.
     *
     * @see <a href="https://redis.io/commands/pfadd/">redis.io</a> for details.
     * @param key The <code>key</code> of the HyperLogLog data structure to add elements into.
     * @param elements An array of members to add to the HyperLogLog stored at <code>key</code>.
     * @return If the HyperLogLog is newly created, or if the HyperLogLog approximated cardinality is
     *     altered, then returns <code>1</code>. Otherwise, returns <code>0</code>.
     * @example
     *     <pre>{@code
     * Long result = client.pfadd("hll_1", new String[] { "a", "b", "c" }).get();
     * assert result == 1L; // A data structure was created or modified
     *
     * result = client.pfadd("hll_2", new String[0]).get();
     * assert result == 1L; // A new empty data structure was created
     * }</pre>
     */
    CompletableFuture<Long> pfadd(String key, String[] elements);

    /**
     * Estimates the cardinality of the data stored in a HyperLogLog structure for a single key or
     * calculates the combined cardinality of multiple keys by merging their HyperLogLogs temporarily.
     *
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @see <a href="https://redis.io/commands/pfcount/">redis.io</a> for details.
     * @param keys The keys of the HyperLogLog data structures to be analyzed.
     * @return The approximated cardinality of given HyperLogLog data structures.<br>
     *     The cardinality of a key that does not exist is <code>0</code>.
     * @example
     *     <pre>{@code
     * Long result = client.pfcount("hll_1", "hll_2").get();
     * assert result == 42L; // Count of unique elements in multiple data structures
     * }</pre>
     */
    CompletableFuture<Long> pfcount(String[] keys);

    /**
     * Merges multiple HyperLogLog values into a unique value.<br>
     * If the destination variable exists, it is treated as one of the source HyperLogLog data sets,
     * otherwise a new HyperLogLog is created.
     *
     * @apiNote When in cluster mode, <code>destination</code> and all keys in <code>sourceKeys</code>
     *     must map to the same hash slot.
     * @see <a href="https://redis.io/commands/pfmerge/">redis.io</a> for details.
     * @param destination The key of the destination HyperLogLog where the merged data sets will be
     *     stored.
     * @param sourceKeys The keys of the HyperLogLog structures to be merged.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.pfmerge("new_HLL", "old_HLL_1", "old_HLL_2").get();
     * assert response.equals("OK"); // new HyperLogLog data set was created with merged content of old ones
     *
     * String response = client.pfmerge("old_HLL_1", "old_HLL_2", "old_HLL_3").get();
     * assert response.equals("OK"); // content of existing HyperLogLogs was merged into existing variable
     * }</pre>
     */
    CompletableFuture<String> pfmerge(String destination, String[] sourceKeys);
}
