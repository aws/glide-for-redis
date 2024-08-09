/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.GlideString;
import glide.api.models.Transaction;
import glide.api.models.commands.SortOptions;
import glide.api.models.commands.SortOptionsBinary;
import glide.api.models.commands.scan.ScanOptions;
import glide.api.models.configuration.ReadFrom;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Generic Commands" group for a standalone client.
 *
 * @see <a href="https://valkey.io/commands/?group=generic">Generic Commands</a>
 */
public interface GenericCommands {
    /** Valkey API keyword used to denote the destination db index. */
    String DB_VALKEY_API = "DB";

    /**
     * Executes a single command, without checking inputs. Every part of the command, including
     * subcommands, should be added as a separate value in <code>args</code>.
     *
     * @apiNote See <a
     *     href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command">Valkey
     *     GLIDE Wiki</a> for details on the restrictions and limitations of the custom command API.
     * @param args Arguments for the custom command.
     * @return The returning value depends on the executed command.
     * @example
     *     <pre>{@code
     * Object response = client.customCommand(new String[] {"ping", "GLIDE"}).get();
     * assert response.equals("GLIDE");
     * // Get a list of all pub/sub clients:
     * Object result = client.customCommand(new String[]{ "CLIENT", "LIST", "TYPE", "PUBSUB" }).get();
     * }</pre>
     */
    CompletableFuture<Object> customCommand(String[] args);

    /**
     * Executes a single command, without checking inputs. Every part of the command, including
     * subcommands, should be added as a separate value in <code>args</code>.
     *
     * @apiNote See <a
     *     href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command">Valkey
     *     GLIDE Wiki</a> for details on the restrictions and limitations of the custom command API.
     * @param args Arguments for the custom command.
     * @return The returning value depends on the executed command.
     * @example
     *     <pre>{@code
     * Object response = client.customCommand(new GlideString[] {gs("ping"), gs("GLIDE")}).get();
     * assert response.equals(gs("GLIDE"));
     * // Get a list of all pub/sub clients:
     * Object result = client.customCommand(new GlideString[] { gs("CLIENT"), gs("LIST"), gs("TYPE"), gs("PUBSUB") }).get();
     * }</pre>
     */
    CompletableFuture<Object> customCommand(GlideString[] args);

    /**
     * Executes a transaction by processing the queued commands.
     *
     * @see <a href="https://valkey.io/docs/topics/transactions/">valkey.io</a> for details on
     *     Transactions.
     * @param transaction A {@link Transaction} object containing a list of commands to be executed.
     * @return A list of results corresponding to the execution of each command in the transaction.
     * @remarks
     *     <ul>
     *       <li>If a command returns a value, it will be included in the list.
     *       <li>If a command doesn't return a value, the list entry will be empty.
     *       <li>If the transaction failed due to a <code>WATCH</code> command, <code>exec</code> will
     *           return <code>null</code>.
     *     </ul>
     *
     * @example
     *     <pre>{@code
     * Transaction transaction = new Transaction().customCommand(new String[] {"info"});
     * Object[] result = client.exec(transaction).get();
     * assert ((String) result[0]).contains("# Stats");
     * }</pre>
     */
    CompletableFuture<Object[]> exec(Transaction transaction);

    /**
     * Move <code>key</code> from the currently selected database to the database specified by <code>
     * dbIndex</code>.
     *
     * @see <a href="https://valkey.io/commands/move/">valkey.io</a> for more details.
     * @param key The key to move.
     * @param dbIndex The index of the database to move <code>key</code> to.
     * @return <code>true</code> if <code>key</code> was moved, or <code>false</code> if the <code>key
     *     </code> already exists in the destination database or does not exist in the source
     *     database.
     * @example
     *     <pre>{@code
     * Boolean moved = client.move("some_key", 1L).get();
     * assert moved;
     * }</pre>
     */
    CompletableFuture<Boolean> move(String key, long dbIndex);

    /**
     * Move <code>key</code> from the currently selected database to the database specified by <code>
     * dbIndex</code>.
     *
     * @see <a href="https://valkey.io/commands/move/">valkey.io</a> for more details.
     * @param key The key to move.
     * @param dbIndex The index of the database to move <code>key</code> to.
     * @return <code>true</code> if <code>key</code> was moved, or <code>false</code> if the <code>key
     *     </code> already exists in the destination database or does not exist in the source
     *     database.
     * @example
     *     <pre>{@code
     * Boolean moved = client.move(gs("some_key"), 1L).get();
     * assert moved;
     * }</pre>
     */
    CompletableFuture<Boolean> move(GlideString key, long dbIndex);

    /**
     * Copies the value stored at the <code>source</code> to the <code>destination</code> key on
     * <code>destinationDB</code>. When <code>replace</code> is true, removes the <code>destination
     * </code> key first if it already exists, otherwise performs no action.
     *
     * @since Valkey 6.2.0 and above.
     * @see <a href="https://valkey.io/commands/copy/">valkey.io</a> for details.
     * @param source The key to the source value.
     * @param destination The key where the value should be copied to.
     * @param destinationDB The alternative logical database index for the destination key.
     * @param replace If the destination key should be removed before copying the value to it.
     * @return <code>true</code> if <code>source</code> was copied, <code>false</code> if <code>source
     * </code> was not copied.
     * @example
     *     <pre>{@code
     * client.set("test1", "one").get();
     * assert client.copy("test1", "test2", 1, false).get();
     * }</pre>
     */
    CompletableFuture<Boolean> copy(
            String source, String destination, long destinationDB, boolean replace);

    /**
     * Copies the value stored at the <code>source</code> to the <code>destination</code> key on
     * <code>destinationDB</code>. When <code>replace</code> is true, removes the <code>destination
     * </code> key first if it already exists, otherwise performs no action.
     *
     * @since Valkey 6.2.0 and above.
     * @see <a href="https://valkey.io/commands/copy/">valkey.io</a> for details.
     * @param source The key to the source value.
     * @param destination The key where the value should be copied to.
     * @param destinationDB The alternative logical database index for the destination key.
     * @param replace If the destination key should be removed before copying the value to it.
     * @return <code>true</code> if <code>source</code> was copied, <code>false</code> if <code>source
     * </code> was not copied.
     * @example
     *     <pre>{@code
     * client.set(gs("test1"), gs("one")).get();
     * assert client.copy(gs("test1"), gs("test2"), 1, false).get();
     * }</pre>
     */
    CompletableFuture<Boolean> copy(
            GlideString source, GlideString destination, long destinationDB, boolean replace);

    /**
     * Copies the value stored at the <code>source</code> to the <code>destination</code> key on
     * <code>destinationDB</code>. When <code>replace</code> is true, removes the <code>destination
     * </code> key first if it already exists, otherwise performs no action.
     *
     * @since Valkey 6.2.0 and above.
     * @see <a href="https://valkey.io/commands/copy/">valkey.io</a> for details.
     * @param source The key to the source value.
     * @param destination The key where the value should be copied to.
     * @param destinationDB The alternative logical database index for the destination key.
     * @return <code>true</code> if <code>source</code> was copied, <code>false</code> if <code>source
     * </code> was not copied.
     * @example
     *     <pre>{@code
     * client.set("test1", "one").get();
     * assert client.copy("test1", "test2", 1).get();
     * }</pre>
     */
    CompletableFuture<Boolean> copy(String source, String destination, long destinationDB);

    /**
     * Copies the value stored at the <code>source</code> to the <code>destination</code> key on
     * <code>destinationDB</code>. When <code>replace</code> is true, removes the <code>destination
     * </code> key first if it already exists, otherwise performs no action.
     *
     * @since Valkey 6.2.0 and above.
     * @see <a href="https://valkey.io/commands/copy/">valkey.io</a> for details.
     * @param source The key to the source value.
     * @param destination The key where the value should be copied to.
     * @param destinationDB The alternative logical database index for the destination key.
     * @return <code>true</code> if <code>source</code> was copied, <code>false</code> if <code>source
     * </code> was not copied.
     * @example
     *     <pre>{@code
     * client.set(gs("test1"), gs("one")).get();
     * assert client.copy(gs("test1"), gs("test2"), 1).get();
     * }</pre>
     */
    CompletableFuture<Boolean> copy(GlideString source, GlideString destination, long destinationDB);

    /**
     * Returns a random key from currently selected database.
     *
     * @see <a href="https://valkey.io/commands/randomkey/">valkey.io</a> for details.
     * @return A random <code>key</code> from the database.
     * @example
     *     <pre>{@code
     * String value = client.set("key", "value").get();
     * String value_1 = client.set("key1", "value_1").get();
     * String key = client.randomKey().get();
     * System.out.println("The random key is: " + key);
     * // The value of key is either "key" or "key1"
     * }</pre>
     */
    CompletableFuture<String> randomKey();

    /**
     * Returns a random key from currently selected database.
     *
     * @see <a href="https://valkey.io/commands/randomkey/">valkey.io</a> for details.
     * @return A random <code>key</code> from the database.
     * @example
     *     <pre>{@code
     * String value = client.set(gs("key"), gs("value")).get();
     * String value_1 = client.set(gs("key1"), gs("value_1")).get();
     * String key = client.randomKeyBinary().get();
     * System.out.println("The random key is: " + key);
     * // The value of key is either "key" or "key1"
     * }</pre>
     */
    CompletableFuture<GlideString> randomKeyBinary();

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and returns the result.
     * The <code>sort</code> command can be used to sort elements based on different criteria and
     * apply transformations on sorted elements.<br>
     * To store the result into a new key, see {@link #sortStore(String, String, SortOptions)}.
     *
     * @see <a href="https://valkey.io/commands/sort/">valkey.io</a> for details.
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param sortOptions The {@link SortOptions}.
     * @return An <code>Array</code> of sorted elements.
     * @example
     *     <pre>{@code
     * client.hset("user:1", Map.of("name", "Alice", "age", "30")).get();
     * client.hset("user:2", Map.of("name", "Bob", "age", "25")).get();
     * client.lpush("user_ids", new String[] {"2", "1"}).get();
     * String [] payload = client.sort("user_ids", SortOptions.builder().byPattern("user:*->age")
     *                  .getPattern("user:*->name").build()).get();
     * assertArrayEquals(new String[] {"Bob", "Alice"}, payload); // Returns a list of the names sorted by age
     * }</pre>
     */
    CompletableFuture<String[]> sort(String key, SortOptions sortOptions);

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and returns the result.
     * The <code>sort</code> command can be used to sort elements based on different criteria and
     * apply transformations on sorted elements.<br>
     * To store the result into a new key, see {@link #sortStore(GlideString, GlideString,
     * SortOptions)}.
     *
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param sortOptions The {@link SortOptionsBinary}.
     * @return An <code>Array</code> of sorted elements.
     * @example
     *     <pre>{@code
     * client.hset(gs("user:1"), Map.of(gs("name"), gs("Alice"), gs("age"), gs("30"))).get();
     * client.hset(gs("user:2"), Map.of(gs("name"), gs("Bob"), gs("age"), gs("25"))).get();
     * client.lpush(gs("user_ids"), new GlideString[] {gs("2"), gs("1")}).get();
     * GlideString [] payload = client.sort(gs("user_ids"), SortOptionsBinary.builder().byPattern(gs("user:*->age"))
     *                  .getPattern(gs("user:*->name")).build()).get();
     * assertArrayEquals(new GlideString[] {gs("Bob"), gs("Alice")}, payload); // Returns a list of the names sorted by age
     * }</pre>
     */
    CompletableFuture<GlideString[]> sort(GlideString key, SortOptionsBinary sortOptions);

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and returns the result.
     * The <code>sortReadOnly</code> command can be used to sort elements based on different criteria
     * and apply transformations on sorted elements.<br>
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/sort/">valkey.io</a> for details.
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param sortOptions The {@link SortOptions}.
     * @return An <code>Array</code> of sorted elements.
     * @example
     *     <pre>{@code
     * client.hset("user:1", Map.of("name", "Alice", "age", "30")).get();
     * client.hset("user:2", Map.of("name", "Bob", "age", "25")).get();
     * client.lpush("user_ids", new String[] {"2", "1"}).get();
     * String [] payload = client.sortReadOnly("user_ids", SortOptions.builder().byPattern("user:*->age")
     *                  .getPattern("user:*->name").build()).get();
     * assertArrayEquals(new String[] {"Bob", "Alice"}, payload); // Returns a list of the names sorted by age
     * }</pre>
     */
    CompletableFuture<String[]> sortReadOnly(String key, SortOptions sortOptions);

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and returns the result.
     * The <code>sortReadOnly</code> command can be used to sort elements based on different criteria
     * and apply transformations on sorted elements.<br>
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * @since Valkey 7.0 and above.
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param sortOptions The {@link SortOptions}.
     * @return An <code>Array</code> of sorted elements.
     * @example
     *     <pre>{@code
     * client.hset(gs("user:1"), Map.of(gs("name"), gs("Alice"), gs("age"), gs("30"))).get();
     * client.hset(gs("user:2"), Map.of(gs("name"), gs("Bob"), gs("age"), gs("25"))).get();
     * client.lpush("user_ids", new GlideString[] {gs("2"), gs("1")}).get();
     * GlideString [] payload = client.sortReadOnly(gs("user_ids"), SortOptionsBinary.builder().byPattern(gs("user:*->age"))
     *                  .getPattern(gs("user:*->name")).build()).get();
     * assertArrayEquals(new GlideString[] {gs("Bob"), gs("Alice")}, payload); // Returns a list of the names sorted by age
     * }</pre>
     */
    CompletableFuture<GlideString[]> sortReadOnly(GlideString key, SortOptionsBinary sortOptions);

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and stores the result in
     * <code>destination</code>. The <code>sort</code> command can be used to sort elements based on
     * different criteria, apply transformations on sorted elements, and store the result in a new
     * key.<br>
     * To get the sort result without storing it into a key, see {@link #sort(String, SortOptions)}.
     *
     * @see <a href="https://valkey.io/commands/sort/">valkey.io</a> for details.
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param sortOptions The {@link SortOptions}.
     * @param destination The key where the sorted result will be stored.
     * @return The number of elements in the sorted key stored at <code>destination</code>.
     * @example
     *     <pre>{@code
     * client.hset("user:1", Map.of("name", "Alice", "age", "30")).get();
     * client.hset("user:2", Map.of("name", "Bob", "age", "25")).get();
     * client.lpush("user_ids", new String[] {"2", "1"}).get();
     * Long payload = client.sortStore("user_ids", "destination",
     *          SortOptions.builder().byPattern("user:*->age").getPattern("user:*->name").build())
     *          .get();
     * assertEquals(2, payload);
     * assertArrayEquals(
     *      new String[] {"Bob", "Alice"},
     *      client.lrange("destination", 0, -1).get()); // The list of the names sorted by age is stored in `destination`
     * }</pre>
     */
    CompletableFuture<Long> sortStore(String key, String destination, SortOptions sortOptions);

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and stores the result in
     * <code>destination</code>. The <code>sort</code> command can be used to sort elements based on
     * different criteria, apply transformations on sorted elements, and store the result in a new
     * key.<br>
     * To get the sort result without storing it into a key, see {@link #sort(GlideString,
     * SortOptions)}.
     *
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param sortOptions The {@link SortOptions}.
     * @param destination The key where the sorted result will be stored.
     * @return The number of elements in the sorted key stored at <code>destination</code>.
     * @example
     *     <pre>{@code
     * client.hset(gs("user:1"), Map.of(gs("name"), gs("Alice"), gs("age"), gs("30"))).get();
     * client.hset(gs("user:2"), Map.of(gs("name"), gs("Bob"), gs("age"), gs("25"))).get();
     * client.lpush(gs("user_ids"), new GlideString[] {gs("2"), gs("1")}).get();
     * Long payload = client.sortStore(gs("user_ids"), gs("destination"),
     *          SortOptionsBinary.builder().byPattern(gs("user:*->age")).getPattern(gs("user:*->name")).build())
     *          .get();
     * assertEquals(2, payload);
     * assertArrayEquals(
     *      new GlideString[] {gs("Bob"), gs("Alice")},
     *      client.lrange(gs("destination"), 0, -1).get()); // The list of the names sorted by age is stored in `destination`
     * }</pre>
     */
    CompletableFuture<Long> sortStore(
            GlideString key, GlideString destination, SortOptionsBinary sortOptions);

    /**
     * Iterates incrementally over a database for matching keys.
     *
     * @see <a href="https://valkey.io/commands/scan/">valkey.io</a> for details.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>"0"
     *     </code> indicates the start of the search.
     * @return An <code>Array</code> of <code>Objects</code>. The first element is always the <code>
     *     cursor</code> for the next iteration of results. <code>"0"</code> will be the <code>cursor
     *     </code> returned on the last iteration of the scan.<br>
     *     The second element is always an <code>Array</code> of matched keys from the database.
     * @example
     *     <pre>{@code
     * // Assume database contains a set with 200 keys
     * String cursor = "0";
     * Object[] result;
     * do {
     *     result = client.scan(cursor).get();
     *     cursor = result[0].toString();
     *     Object[] stringResults = (Object[]) result[1];
     *     String keyList = Arrays.stream(stringResults)
     *         .map(obj -> (String)obj)
     *         .collect(Collectors.joining(", "));
     *     System.out.println("\nSCAN iteration: " + keyList);
     * } while (!cursor.equals("0"));
     * }</pre>
     */
    CompletableFuture<Object[]> scan(String cursor);

    /**
     * Iterates incrementally over a database for matching keys.
     *
     * @see <a href="https://valkey.io/commands/scan/">valkey.io</a> for details.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>gs("0")
     *     </code> indicates the start of the search.
     * @return An <code>Array</code> of <code>Objects</code>. The first element is always the <code>
     *     cursor</code> for the next iteration of results. <code>gs("0")</code> will be the <code>
     *     cursor
     *     </code> returned on the last iteration of the scan.<br>
     *     The second element is always an <code>Array</code> of matched keys from the database.
     * @example
     *     <pre>{@code
     * // Assume database contains a set with 200 keys
     * GlideString cursor = gs("0");
     * Object[] result;
     * do {
     *     result = client.scan(cursor).get();
     *     cursor = gs(result[0].toString());
     *     Object[] stringResults = (Object[]) result[1];
     *     String keyList = Arrays.stream(stringResults)
     *         .map(obj -> obj.toString())
     *         .collect(Collectors.joining(", "));
     *     System.out.println("\nSCAN iteration: " + keyList);
     * } while (!cursor.equals(gs("0")));
     * }</pre>
     */
    CompletableFuture<Object[]> scan(GlideString cursor);

    /**
     * Iterates incrementally over a database for matching keys.
     *
     * @see <a href="https://valkey.io/commands/scan/">valkey.io</a> for details.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>"0"
     *     </code> indicates the start of the search.
     * @param options The {@link ScanOptions}.
     * @return An <code>Array</code> of <code>Objects</code>. The first element is always the <code>
     *     cursor</code> for the next iteration of results. <code>"0"</code> will be the <code>cursor
     *     </code> returned on the last iteration of the scan.<br>
     *     The second element is always an <code>Array</code> of matched keys from the database.
     * @example
     *     <pre>{@code
     * // Assume database contains a set with 200 keys
     * String cursor = "0";
     * Object[] result;
     * // match keys on pattern *11*
     * ScanOptions options = ScanOptions.builder().matchPattern("*11*").build();
     * do {
     *     result = client.scan(cursor, options).get();
     *     cursor = result[0].toString();
     *     Object[] stringResults = (Object[]) result[1];
     *     String keyList = Arrays.stream(stringResults)
     *         .map(obj -> (String)obj)
     *         .collect(Collectors.joining(", "));
     *     System.out.println("\nSCAN iteration: " + keyList);
     * } while (!cursor.equals("0"));
     * }</pre>
     */
    CompletableFuture<Object[]> scan(String cursor, ScanOptions options);

    /**
     * Iterates incrementally over a database for matching keys.
     *
     * @see <a href="https://valkey.io/commands/scan/">valkey.io</a> for details.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>gs("0")
     *     </code> indicates the start of the search.
     * @param options The {@link ScanOptions}.
     * @return An <code>Array</code> of <code>Objects</code>. The first element is always the <code>
     *     cursor</code> for the next iteration of results. <code>gs("0")</code> will be the <code>
     *     cursor
     *     </code> returned on the last iteration of the scan.<br>
     *     The second element is always an <code>Array</code> of matched keys from the database.
     * @example
     *     <pre>{@code
     * // Assume database contains a set with 200 keys
     * GlideString cursor = gs("0");
     * Object[] result;
     * // match keys on pattern *11*
     * ScanOptions options = ScanOptions.builder().matchPattern("*11*").build();
     * do {
     *     result = client.scan(cursor, options).get();
     *     cursor = gs(result[0].toString());
     *     Object[] stringResults = (Object[]) result[1];
     *     String keyList = Arrays.stream(stringResults)
     *         .map(obj -> obj.toString())
     *         .collect(Collectors.joining(", "));
     *     System.out.println("\nSCAN iteration: " + keyList);
     * } while (!cursor.equals(gs("0")));
     * }</pre>
     */
    CompletableFuture<Object[]> scan(GlideString cursor, ScanOptions options);
}
