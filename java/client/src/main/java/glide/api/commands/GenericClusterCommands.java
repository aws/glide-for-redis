/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterTransaction;
import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.Transaction;
import glide.api.models.commands.SortClusterOptions;
import glide.api.models.commands.scan.ClusterScanCursor;
import glide.api.models.commands.scan.ScanOptions;
import glide.api.models.configuration.ReadFrom;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Generic Commands" group for a cluster client.
 *
 * @see <a href="https://valkey.io/commands/?group=generic">Generic Commands</a>
 */
public interface GenericClusterCommands {

    /**
     * Executes a single command, without checking inputs. Every part of the command, including
     * subcommands, should be added as a separate value in <code>args</code>.<br>
     * The command will be routed automatically based on the passed command's default request policy.
     *
     * @apiNote See <a
     *     href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command">Valkey
     *     GLIDE Wiki</a> for details on the restrictions and limitations of the custom command API.
     * @param args Arguments for the custom command including the command name.
     * @return The returning value depends on the executed command.
     * @example
     *     <pre>{@code
     * ClusterValue<Object> data = client.customCommand(new String[] {"ping"}).get();
     * assert data.getSingleValue().equals("PONG");
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object>> customCommand(String[] args);

    /**
     * Executes a single command, without checking inputs. Every part of the command, including
     * subcommands, should be added as a separate value in <code>args</code>.<br>
     * The command will be routed automatically based on the passed command's default request policy.
     *
     * @apiNote See <a
     *     href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command">Valkey
     *     GLIDE Wiki</a> for details on the restrictions and limitations of the custom command API.
     * @param args Arguments for the custom command including the command name.
     * @return The returning value depends on the executed command.
     * @example
     *     <pre>{@code
     * ClusterValue<Object> data = client.customCommand(new GlideString[] {gs("ping")}).get();
     * assert data.getSingleValue().equals(gs("PONG"));
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object>> customCommand(GlideString[] args);

    /**
     * Executes a single command, without checking inputs. Every part of the command, including
     * subcommands, should be added as a separate value in <code>args</code>.
     *
     * @apiNote See <a
     *     href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command">Valkey
     *     GLIDE Wiki</a> for details on the restrictions and limitations of the custom command API.
     * @param args Arguments for the custom command including the command name
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The returning value depends on the executed command and route.
     * @example
     *     <pre>{@code
     * ClusterValue<Object> result = clusterClient.customCommand(new String[]{ "CONFIG", "GET", "maxmemory"}, ALL_NODES).get();
     * Map<String, Object> payload = result.getMultiValue();
     * assert payload.get("node1").equals("1GB");
     * assert payload.get("node2").equals("100MB");
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object>> customCommand(String[] args, Route route);

    /**
     * Executes a single command, without checking inputs. Every part of the command, including
     * subcommands, should be added as a separate value in <code>args</code>.
     *
     * @apiNote See <a
     *     href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command">Valkey
     *     GLIDE Wiki</a> for details on the restrictions and limitations of the custom command API.
     * @param args Arguments for the custom command including the command name
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The returning value depends on the executed command and route.
     * @example
     *     <pre>{@code
     * ClusterValue<Object> result = clusterClient.customCommand(new GlideString[] { gs("CONFIG"), gs("GET"), gs("maxmemory") }, ALL_NODES).get();
     * Map<String, Object> payload = result.getMultiValue();
     * assert payload.get(gs("node1")).equals(gs("1GB"));
     * assert payload.get(gs("node2")).equals(gs("100MB"));
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object>> customCommand(GlideString[] args, Route route);

    /**
     * Executes a transaction by processing the queued commands.
     *
     * <p>The transaction will be routed to the slot owner of the first key found in the transaction.
     * If no key is found, the command will be sent to a random node.
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
     * ClusterTransaction transaction = new ClusterTransaction().customCommand(new String[] {"info"});
     * Object[] result = clusterClient.exec(transaction).get();
     * assert ((String) result[0]).contains("# Stats");
     * }</pre>
     */
    CompletableFuture<Object[]> exec(ClusterTransaction transaction);

    /**
     * Executes a transaction by processing the queued commands.
     *
     * @see <a href="https://valkey.io/docs/topics/transactions/">valkey.io</a> for details on
     *     Transactions.
     * @param transaction A {@link Transaction} object containing a list of commands to be executed.
     * @param route A single-node routing configuration for the transaction. The client will route the
     *     transaction to the node defined by <code>route</code>.
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
     * ClusterTransaction transaction = new ClusterTransaction().ping().info();
     * Object[] result = clusterClient.exec(transaction, RANDOM).get();
     * assert ((String) result[0]).equals("PONG");
     * assert ((String) result[1]).contains("# Stats");
     * }</pre>
     */
    CompletableFuture<Object[]> exec(ClusterTransaction transaction, SingleNodeRoute route);

    /**
     * Returns a random key.
     *
     * @see <a href="https://valkey.io/commands/randomkey/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>, and will return the first successful
     *     result.
     * @return A random <code>key</code> from the database.
     * @example
     *     <pre>{@code
     * String value = client.set("key", "value").get();
     * String value_1 = client.set("key1", "value_1").get();
     * String key = client.randomKey(RANDOM).get();
     * System.out.println("The random key is: " + key);
     * // The value of key is either "key" or "key1"
     * }</pre>
     */
    CompletableFuture<String> randomKey(Route route);

    /**
     * Returns a random key.
     *
     * @see <a href="https://valkey.io/commands/randomkey/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>, and will return the first successful
     *     result.
     * @return A random <code>key</code> from the database.
     * @example
     *     <pre>{@code
     * String value = client.set("key", "value").get();
     * String value_1 = client.set("key1", "value_1").get();
     * GlideString key = client.randomKeyBinary(RANDOM).get();
     * System.out.println("The random key is: " + key);
     * // The value of key is either "key" or "key1"
     * }</pre>
     */
    CompletableFuture<GlideString> randomKeyBinary(Route route);

    /**
     * Returns a random key.<br>
     * The command will be routed to all primary nodes, and will return the first successful result.
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
     * Returns a random key.<br>
     * The command will be routed to all primary nodes, and will return the first successful result.
     *
     * @see <a href="https://valkey.io/docs/commands/randomkey/">valkey.io</a> for details.
     * @return A random <code>key</code> from the database.
     * @example
     *     <pre>{@code
     * String value = client.set(gs("key"),gs( "value")).get();
     * String value_1 = client.set(gs("key1"), gs("value_1")).get();
     * GlideString key = client.randomKeyBinary().get();
     * System.out.println("The random key is: " + key);
     * // The value of key is either "key" or "key1"
     * }</pre>
     */
    CompletableFuture<GlideString> randomKeyBinary();

    /**
     * Incrementally iterates over the keys in the Cluster.
     *
     * <p>This command is similar to the <code>SCAN</code> command, but it is designed to work in a
     * Cluster environment. The main difference is that this command uses a {@link ClusterScanCursor}
     * object to manage iterations. For more information about the Cluster Scan implementation, see <a
     * href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#cluster-scan">Cluster
     * Scan</a>.
     *
     * <p>As with the <code>SCAN</code> command, this command is a cursor-based iterator. This means
     * that at every call of the command, the server returns an updated cursor ({@link
     * ClusterScanCursor}) that the user needs to re-send as the <code>cursor</code> argument in the
     * next call. The iteration terminates when the returned cursor {@link
     * ClusterScanCursor#isFinished()} returns <code>true</code>.
     *
     * <p>This method guarantees that all keyslots available when the first SCAN is called will be
     * scanned before the cursor is finished. Any keys added after the initial scan request is made
     * are not guaranteed to be scanned.
     *
     * <p>Note that the same key may be returned in multiple scan iterations.
     *
     * <p>How to use the {@link ClusterScanCursor}: <br>
     * For each iteration, the previous scan {@link ClusterScanCursor} object should be used to
     * continue the <code>SCAN</code> by passing it in the <code>cursor</code> argument. Using the
     * same cursor object for multiple iterations may result in the same keys returned or unexpected
     * behavior.
     *
     * <p>When the cursor is no longer needed, call {@link ClusterScanCursor#releaseCursorHandle()} to
     * immediately free resources tied to the cursor. Note that this makes the cursor unusable in
     * subsequent calls to <code>SCAN</code>.
     *
     * @see <a href="https://valkey.io/commands/scan">valkey.io</a> for details.
     * @param cursor The {@link ClusterScanCursor} object that wraps the scan state. To start a new
     *     scan, create a new empty ClusterScanCursor using {@link ClusterScanCursor#initalCursor()}.
     * @return An <code>Array</code> with two elements. The first element is always the {@link
     *     ClusterScanCursor} for the next iteration of results. To see if there is more data on the
     *     given cursor, call {@link ClusterScanCursor#isFinished()}. To release resources for the
     *     current cursor immediately, call {@link ClusterScanCursor#releaseCursorHandle()} after
     *     using the cursor in a call to this method. The cursor cannot be used in a scan again after
     *     {@link ClusterScanCursor#releaseCursorHandle()} has been called. The second element is an
     *     <code>
     *     Array</code> of <code>String</code> elements each representing a key.
     * @example
     *     <pre>{@code
     * // Assume key contains a set with 200 keys
     * ClusterScanCursor cursor = ClusterScanCursor.initialCursor();
     * Object[] result;
     * while (!cursor.isFinished()) {
     *   result = client.scan(cursor).get();
     *   cursor.releaseCursorHandle();
     *   cursor = (ClusterScanCursor) result[0];
     *   Object[] stringResults = (Object[]) result[1];
     *   System.out.println("\nSCAN iteration:");
     *   Arrays.asList(stringResults).stream().forEach(i -> System.out.print(i + ", "));
     * }
     * }</pre>
     */
    CompletableFuture<Object[]> scan(ClusterScanCursor cursor);

    /**
     * Incrementally iterates over the keys in the Cluster.
     *
     * <p>This command is similar to the <code>SCAN</code> command, but it is designed to work in a
     * Cluster environment. The main difference is that this command uses a {@link ClusterScanCursor}
     * object to manage iterations. For more information about the Cluster Scan implementation, see <a
     * href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#cluster-scan">Cluster
     * Scan</a>.
     *
     * <p>As with the <code>SCAN</code> command, this command is a cursor-based iterator. This means
     * that at every call of the command, the server returns an updated cursor ({@link
     * ClusterScanCursor}) that the user needs to re-send as the <code>cursor</code> argument in the
     * next call. The iteration terminates when the returned cursor {@link
     * ClusterScanCursor#isFinished()} returns <code>true</code>.
     *
     * <p>This method guarantees that all keyslots available when the first SCAN is called will be
     * scanned before the cursor is finished. Any keys added after the initial scan request is made
     * are not guaranteed to be scanned.
     *
     * <p>Note that the same key may be returned in multiple scan iterations.
     *
     * <p>How to use the {@link ClusterScanCursor}: <br>
     * For each iteration, the previous scan {@link ClusterScanCursor} object should be used to
     * continue the <code>SCAN</code> by passing it in the <code>cursor</code> argument. Using the
     * same cursor object for multiple iterations may result in the same keys returned or unexpected
     * behavior.
     *
     * <p>When the cursor is no longer needed, call {@link ClusterScanCursor#releaseCursorHandle()} to
     * immediately free resources tied to the cursor. Note that this makes the cursor unusable in
     * subsequent calls to <code>SCAN</code>.
     *
     * @see <a href="https://valkey.io/commands/scan">valkey.io</a> for details.
     * @param cursor The {@link ClusterScanCursor} object that wraps the scan state. To start a new
     *     scan, create a new empty ClusterScanCursor using {@link ClusterScanCursor#initalCursor()}.
     * @return An <code>Array</code> with two elements. The first element is always the {@link
     *     ClusterScanCursor} for the next iteration of results. To see if there is more data on the
     *     given cursor, call {@link ClusterScanCursor#isFinished()}. To release resources for the
     *     current cursor immediately, call {@link ClusterScanCursor#releaseCursorHandle()} after
     *     using the cursor in a call to this method. The cursor cannot be used in a scan again after
     *     {@link ClusterScanCursor#releaseCursorHandle()} has been called. The second element is an
     *     <code>Array</code> of <code>GlideString</code> elements each representing a key.
     * @example
     *     <pre>{@code
     * // Assume key contains a set with 200 keys
     * ClusterScanCursor cursor = ClusterScanCursor.initialCursor();
     * Object[] result;
     * while (!cursor.isFinished()) {
     *   result = client.scan(cursor).get();
     *   cursor.releaseCursorHandle();
     *   cursor = (ClusterScanCursor) result[0];
     *   Object[] glideStringResults = (Object[]) result[1];
     *   System.out.println("\nSCAN iteration:");
     *   Arrays.asList(stringResults).stream().forEach(i -> System.out.print(i + ", "));
     * }
     * }</pre>
     */
    CompletableFuture<Object[]> scanBinary(ClusterScanCursor cursor);

    /**
     * Incrementally iterates over the keys in the Cluster.
     *
     * <p>This command is similar to the <code>SCAN</code> command, but it is designed to work in a
     * Cluster environment. The main difference is that this command uses a {@link ClusterScanCursor}
     * object to manage iterations. For more information about the Cluster Scan implementation, see <a
     * href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#cluster-scan">Cluster
     * Scan</a>.
     *
     * <p>As with the <code>SCAN</code> command, this command is a cursor-based iterator. This means
     * that at every call of the command, the server returns an updated cursor ({@link
     * ClusterScanCursor}) that the user needs to re-send as the <code>cursor</code> argument in the
     * next call. The iteration terminates when the returned cursor {@link
     * ClusterScanCursor#isFinished()} returns <code>true</code>.
     *
     * <p>This method guarantees that all keyslots available when the first SCAN is called will be
     * scanned before the cursor is finished. Any keys added after the initial scan request is made
     * are not guaranteed to be scanned.
     *
     * <p>Note that the same key may be returned in multiple scan iterations.
     *
     * <p>How to use the {@link ClusterScanCursor}: <br>
     * For each iteration, the previous scan {@link ClusterScanCursor} object should be used to
     * continue the <code>SCAN</code> by passing it in the <code>cursor</code> argument. Using the
     * same cursor object for multiple iterations may result in the same keys returned or unexpected
     * behavior.
     *
     * <p>When the cursor is no longer needed, call {@link ClusterScanCursor#releaseCursorHandle()} to
     * immediately free resources tied to the cursor. Note that this makes the cursor unusable in
     * subsequent calls to <code>SCAN</code>.
     *
     * @see <a href="https://valkey.io/commands/scan">valkey.io</a> for details.
     * @param cursor The {@link ClusterScanCursor} object that wraps the scan state. To start a new
     *     scan, create a new empty ClusterScanCursor using {@link ClusterScanCursor#initalCursor()}.
     * @param options The {@link ScanOptions}.
     * @return An <code>Array</code> with two elements. The first element is always the {@link
     *     ClusterScanCursor} for the next iteration of results. To see if there is more data on the
     *     given cursor, call {@link ClusterScanCursor#isFinished()}. To release resources for the
     *     current cursor immediately, call {@link ClusterScanCursor#releaseCursorHandle()} after
     *     using the cursor in a call to this method. The cursor cannot be used in a scan again after
     *     {@link ClusterScanCursor#releaseCursorHandle()} has been called. The second element is an
     *     <code>Array</code> of <code>String</code> elements each representing a key.
     * @example
     *     <pre>{@code
     * // Assume key contains a set with 200 keys
     * ClusterScanCursor cursor = ClusterScanCursor.initialCursor();
     * // Scan for keys with archived in the name
     * ScanOptions options = ScanOptions.builder().matchPattern("*archived*").build();
     * Object[] result;
     * while (!cursor.isFinished()) {
     *   result = client.scan(cursor, options).get();
     *   cursor.releaseCursorHandle();
     *   cursor = (ClusterScanCursor) result[0];
     *   Object[] stringResults = (Object[]) result[1];
     *   System.out.println("\nSCAN iteration:");
     *   Arrays.asList(stringResults).stream().forEach(i -> System.out.print(i + ", "));
     * }
     * }</pre>
     */
    CompletableFuture<Object[]> scan(ClusterScanCursor cursor, ScanOptions options);

    /**
     * Incrementally iterates over the keys in the Cluster.
     *
     * <p>This command is similar to the <code>SCAN</code> command, but it is designed to work in a
     * Cluster environment. The main difference is that this command uses a {@link ClusterScanCursor}
     * object to manage iterations. For more information about the Cluster Scan implementation, see <a
     * href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#cluster-scan">Cluster
     * Scan</a>.
     *
     * <p>As with the <code>SCAN</code> command, this command is a cursor-based iterator. This means
     * that at every call of the command, the server returns an updated cursor ({@link
     * ClusterScanCursor}) that the user needs to re-send as the <code>cursor</code> argument in the
     * next call. The iteration terminates when the returned cursor {@link
     * ClusterScanCursor#isFinished()} returns <code>true</code>.
     *
     * <p>This method guarantees that all keyslots available when the first SCAN is called will be
     * scanned before the cursor is finished. Any keys added after the initial scan request is made
     * are not guaranteed to be scanned.
     *
     * <p>Note that the same key may be returned in multiple scan iterations.
     *
     * <p>How to use the {@link ClusterScanCursor}: <br>
     * For each iteration, the previous scan {@link ClusterScanCursor} object should be used to
     * continue the <code>SCAN</code> by passing it in the <code>cursor</code> argument. Using the
     * same cursor object for multiple iterations may result in the same keys returned or unexpected
     * behavior.
     *
     * <p>When the cursor is no longer needed, call {@link ClusterScanCursor#releaseCursorHandle()} to
     * immediately free resources tied to the cursor. Note that this makes the cursor unusable in
     * subsequent calls to <code>SCAN</code>.
     *
     * @see <a href="https://valkey.io/commands/scan">valkey.io</a> for details.
     * @param cursor The {@link ClusterScanCursor} object that wraps the scan state. To start a new
     *     scan, create a new empty ClusterScanCursor using {@link ClusterScanCursor#initalCursor()}.
     * @param options The {@link ScanOptions}.
     * @return An <code>Array</code> with two elements. The first element is always the {@link
     *     ClusterScanCursor} for the next iteration of results. To see if there is more data on the
     *     given cursor, call {@link ClusterScanCursor#isFinished()}. To release resources for the
     *     current cursor immediately, call {@link ClusterScanCursor#releaseCursorHandle()} after
     *     using the cursor in a call to this method. The cursor cannot be used in a scan again after
     *     {@link ClusterScanCursor#releaseCursorHandle()} has been called. The second element is an
     *     <code>Array</code> of <code>GlideString</code> elements each representing a key.
     * @example
     *     <pre>{@code
     * // Assume key contains a set with 200 keys
     * ClusterScanCursor cursor = ClusterScanCursor.initialCursor();
     * // Scan for keys with archived in the name
     * ScanOptions options = ScanOptions.builder().matchPattern("*archived*").build();
     * Object[] result;
     * while (!cursor.isFinished()) {
     *   result = client.scan(cursor, options).get();
     *   cursor.releaseCursorHandle();
     *   cursor = (ClusterScanCursor) result[0];
     *   Object[] glideStringResults = (Object[]) result[1];
     *   System.out.println("\nSCAN iteration:");
     *   Arrays.asList(stringResults).stream().forEach(i -> System.out.print(i + ", "));
     * }
     * }</pre>
     */
    CompletableFuture<Object[]> scanBinary(ClusterScanCursor cursor, ScanOptions options);

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and returns the result.
     * <br>
     * The <code>sort</code> command can be used to sort elements based on different criteria and
     * apply transformations on sorted elements.<br>
     * To store the result into a new key, see {@link #sortStore(String, String, SortClusterOptions)}.
     *
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param sortClusterOptions The {@link SortClusterOptions}.
     * @return An <code>Array</code> of sorted elements.
     * @example
     *     <pre>{@code
     * client.lpush("mylist", new String[] {"3", "1", "2", "a"}).get();
     * String[] payload = client.sort("mylist", SortClusterOptions.builder().alpha()
     *          .orderBy(DESC).limit(new SortBaseOptions.Limit(0L, 3L)).build()).get();
     * assertArrayEquals(new String[] {"a", "3", "2"}, payload); // List is sorted in descending order lexicographically starting
     * }</pre>
     */
    CompletableFuture<String[]> sort(String key, SortClusterOptions sortClusterOptions);

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and returns the result.
     * <br>
     * The <code>sort</code> command can be used to sort elements based on different criteria and
     * apply transformations on sorted elements.<br>
     * To store the result into a new key, see {@link #sortStore(String, String, SortClusterOptions)}.
     *
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param sortClusterOptions The {@link SortClusterOptions}.
     * @return An <code>Array</code> of sorted elements.
     * @example
     *     <pre>{@code
     * client.lpush(gs("mylist"), new GlideString[] {gs("3"), gs("1"), gs("2"), gs("a")}).get();
     * GlideString[] payload = client.sort(gs("mylist"), SortClusterOptions.builder().alpha()
     *          .orderBy(DESC).limit(new SortBaseOptions.Limit(0L, 3L)).build()).get();
     * assertArrayEquals(new GlideString[] {gs("a"), gs("3"), gs("2")}, payload); // List is sorted in descending order lexicographically starting
     * }</pre>
     */
    CompletableFuture<GlideString[]> sort(GlideString key, SortClusterOptions sortClusterOptions);

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and returns the result.
     * <br>
     * The <code>sortReadOnly</code> command can be used to sort elements based on different criteria
     * and apply transformations on sorted elements.<br>
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * @since Valkey 7.0 and above.
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param sortClusterOptions The {@link SortClusterOptions}.
     * @return An <code>Array</code> of sorted elements.
     * @example
     *     <pre>{@code
     * client.lpush("mylist", new String[] {"3", "1", "2", "a"}).get();
     * String[] payload = client.sortReadOnly("mylist", SortClusterOptions.builder().alpha()
     *          .orderBy(DESC).limit(new SortBaseOptions.Limit(0L, 3L)).build()).get();
     * assertArrayEquals(new String[] {"a", "3", "2"}, payload); // List is sorted in descending order lexicographically starting
     * }</pre>
     */
    CompletableFuture<String[]> sortReadOnly(String key, SortClusterOptions sortClusterOptions);

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and returns the result.
     * <br>
     * The <code>sortReadOnly</code> command can be used to sort elements based on different criteria
     * and apply transformations on sorted elements.<br>
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * @since Valkey 7.0 and above.
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param sortClusterOptions The {@link SortClusterOptions}.
     * @return An <code>Array</code> of sorted elements.
     * @example
     *     <pre>{@code
     * client.lpush("mylist", new GlideString[] {gs("3"), gs("1"), gs("2"), gs("a")}).get();
     * GlideString[] payload = client.sortReadOnly(gs("mylist"), SortClusterOptions.builder().alpha()
     *          .orderBy(DESC).limit(new SortBaseOptions.Limit(0L, 3L)).build()).get();
     * assertArrayEquals(new GlideString[] {gs("a"), gs("3"), gs("2")}, payload); // List is sorted in descending order lexicographically starting
     * }</pre>
     */
    CompletableFuture<GlideString[]> sortReadOnly(
            GlideString key, SortClusterOptions sortClusterOptions);

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and stores the result in
     * <code>destination</code>. The <code>sort</code> command can be used to sort elements based on
     * different criteria, apply transformations on sorted elements, and store the result in a new
     * key.<br>
     * To get the sort result without storing it into a key, see {@link #sort(String,
     * SortClusterOptions)} or {@link #sortReadOnly(String, SortClusterOptions)}.
     *
     * @apiNote When in cluster mode, <code>key</code> and <code>destination</code> must map to the
     *     same hash slot.
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param destination The key where the sorted result will be stored.
     * @param sortClusterOptions The {@link SortClusterOptions}.
     * @return The number of elements in the sorted key stored at <code>destination</code>.
     * @example
     *     <pre>{@code
     * client.lpush("mylist", new String[] {"3", "1", "2", "a"}).get();
     * Long payload = client.sortStore("mylist", "destination",
     *          SortClusterOptions.builder().alpha().orderBy(DESC)
     *              .limit(new SortBaseOptions.Limit(0L, 3L))build()).get();
     * assertEquals(3, payload);
     * assertArrayEquals(
     *      new String[] {"a", "3", "2"},
     *      client.lrange("destination", 0, -1).get()); // Sorted list is stored in "destination"
     * }</pre>
     */
    CompletableFuture<Long> sortStore(
            String key, String destination, SortClusterOptions sortClusterOptions);

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and stores the result in
     * <code>destination</code>. The <code>sort</code> command can be used to sort elements based on
     * different criteria, apply transformations on sorted elements, and store the result in a new
     * key.<br>
     * To get the sort result without storing it into a key, see {@link #sort(String,
     * SortClusterOptions)} or {@link #sortReadOnly(String, SortClusterOptions)}.
     *
     * @apiNote When in cluster mode, <code>key</code> and <code>destination</code> must map to the
     *     same hash slot.
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param destination The key where the sorted result will be stored.
     * @param sortClusterOptions The {@link SortClusterOptions}.
     * @return The number of elements in the sorted key stored at <code>destination</code>.
     * @example
     *     <pre>{@code
     * client.lpush(gs("mylist"), new GlideString[] {gs("3"), gs("1"), gs("2"), gs("a")}).get();
     * Long payload = client.sortStore(gs("mylist"), gs("destination"),
     *          SortClusterOptions.builder().alpha().orderBy(DESC)
     *              .limit(new SortBaseOptions.Limit(0L, 3L))build()).get();
     * assertEquals(3, payload);
     * assertArrayEquals(
     *      new GlideString[] {gs("a"), gs("3"), gs("2")},
     *      client.lrange(gs("destination"), 0, -1).get()); // Sorted list is stored in "destination"
     * }</pre>
     */
    CompletableFuture<Long> sortStore(
            GlideString key, GlideString destination, SortClusterOptions sortClusterOptions);
}
