/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterValue;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.function.FunctionRestorePolicy;
import glide.api.models.configuration.ReadFrom;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Scripting and Function" group for a cluster client.
 *
 * @see <a href="https://redis.io/docs/latest/commands/?group=scripting">Scripting and Function
 *     Commands</a>
 */
public interface ScriptingAndFunctionsClusterCommands {

    /**
     * Loads a library to Redis.<br>
     * The command will be routed to all primary nodes.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-load/">redis.io</a> for details.
     * @param libraryCode The source code that implements the library.
     * @param replace Whether the given library should overwrite a library with the same name if it
     *     already exists.
     * @return The library name that was loaded.
     * @example
     *     <pre>{@code
     * String code = "#!lua name=mylib \n redis.register_function('myfunc', function(keys, args) return args[1] end)";
     * String response = client.functionLoad(code, true).get();
     * assert response.equals("mylib");
     * }</pre>
     */
    CompletableFuture<String> functionLoad(String libraryCode, boolean replace);

    /**
     * Loads a library to Redis.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-load/">redis.io</a> for details.
     * @param libraryCode The source code that implements the library.
     * @param replace Whether the given library should overwrite a library with the same name if it
     *     already exists.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The library name that was loaded.
     * @example
     *     <pre>{@code
     * String code = "#!lua name=mylib \n redis.register_function('myfunc', function(keys, args) return args[1] end)";
     * Route route = new SlotKeyRoute("key", PRIMARY);
     * String response = client.functionLoad(code, true, route).get();
     * assert response.equals("mylib");
     * }</pre>
     */
    CompletableFuture<String> functionLoad(String libraryCode, boolean replace, Route route);

    /**
     * Returns information about the functions and libraries.<br>
     * The command will be routed to a random node.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-list/">redis.io</a> for details.
     * @param withCode Specifies whether to request the library code from the server or not.
     * @return Info about all libraries and their functions.
     * @example
     *     <pre>{@code
     * Map<String, Object>[] response = client.functionList(true).get();
     * for (Map<String, Object> libraryInfo : response) {
     *     System.out.printf("Server has library '%s' which runs on %s engine%n",
     *         libraryInfo.get("library_name"), libraryInfo.get("engine"));
     *     Map<String, Object>[] functions = (Map<String, Object>[]) libraryInfo.get("functions");
     *     for (Map<String, Object> function : functions) {
     *         Set<String> flags = (Set<String>) function.get("flags");
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get("name"), String.join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     * }
     * }</pre>
     */
    CompletableFuture<Map<String, Object>[]> functionList(boolean withCode);

    /**
     * Returns information about the functions and libraries.<br>
     * The command will be routed to a random node.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-list/">redis.io</a> for details.
     * @param libNamePattern A wildcard pattern for matching library names.
     * @param withCode Specifies whether to request the library code from the server or not.
     * @return Info about queried libraries and their functions.
     * @example
     *     <pre>{@code
     * Map<String, Object>[] response = client.functionList("myLib?_backup", true).get();
     * for (Map<String, Object> libraryInfo : response) {
     *     System.out.printf("Server has library '%s' which runs on %s engine%n",
     *         libraryInfo.get("library_name"), libraryInfo.get("engine"));
     *     Map<String, Object>[] functions = (Map<String, Object>[]) libraryInfo.get("functions");
     *     for (Map<String, Object> function : functions) {
     *         Set<String> flags = (Set<String>) function.get("flags");
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get("name"), String.join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     * }
     * }</pre>
     */
    CompletableFuture<Map<String, Object>[]> functionList(String libNamePattern, boolean withCode);

    /**
     * Returns information about the functions and libraries.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-list/">redis.io</a> for details.
     * @param withCode Specifies whether to request the library code from the server or not.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return Info about all libraries and their functions.
     * @example
     *     <pre>{@code
     * ClusterValue<Map<String, Object>[]> response = client.functionList(true, ALL_NODES).get();
     * for (String node : response.getMultiValue().keySet()) {
     *   for (Map<String, Object> libraryInfo : response.getMultiValue().get(node)) {
     *     System.out.printf("Node '%s' has library '%s' which runs on %s engine%n",
     *         node, libraryInfo.get("library_name"), libraryInfo.get("engine"));
     *     Map<String, Object>[] functions = (Map<String, Object>[]) libraryInfo.get("functions");
     *     for (Map<String, Object> function : functions) {
     *         Set<String> flags = (Set<String>) function.get("flags");
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get("name"), String.join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     *   }
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Map<String, Object>[]>> functionList(
            boolean withCode, Route route);

    /**
     * Returns information about the functions and libraries.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-list/">redis.io</a> for details.
     * @param libNamePattern A wildcard pattern for matching library names.
     * @param withCode Specifies whether to request the library code from the server or not.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return Info about queried libraries and their functions.
     * @example
     *     <pre>{@code
     * ClusterValue<Map<String, Object>[]> response = client.functionList("myLib?_backup", true, ALL_NODES).get();
     * for (String node : response.getMultiValue().keySet()) {
     *   for (Map<String, Object> libraryInfo : response.getMultiValue().get(node)) {
     *     System.out.printf("Node '%s' has library '%s' which runs on %s engine%n",
     *         node, libraryInfo.get("library_name"), libraryInfo.get("engine"));
     *     Map<String, Object>[] functions = (Map<String, Object>[]) libraryInfo.get("functions");
     *     for (Map<String, Object> function : functions) {
     *         Set<String> flags = (Set<String>) function.get("flags");
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get("name"), String.join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     *   }
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Map<String, Object>[]>> functionList(
            String libNamePattern, boolean withCode, Route route);

    /**
     * Deletes all function libraries.<br>
     * The command will be routed to all primary nodes.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-flush/">redis.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionFlush().get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionFlush();

    /**
     * Deletes all function libraries.<br>
     * The command will be routed to all primary nodes.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-flush/">redis.io</a> for details.
     * @param mode The flushing mode, could be either {@link FlushMode#SYNC} or {@link
     *     FlushMode#ASYNC}.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionFlush(SYNC).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionFlush(FlushMode mode);

    /**
     * Deletes all function libraries.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-flush/">redis.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionFlush(RANDOM).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionFlush(Route route);

    /**
     * Deletes all function libraries.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-flush/">redis.io</a> for details.
     * @param mode The flushing mode, could be either {@link FlushMode#SYNC} or {@link
     *     FlushMode#ASYNC}.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionFlush(SYNC, RANDOM).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionFlush(FlushMode mode, Route route);

    /**
     * Deletes a library and all its functions.<br>
     * The command will be routed to all primary nodes.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-delete/">redis.io</a> for details.
     * @param libName The library name to delete.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionDelete("myLib").get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionDelete(String libName);

    /**
     * Deletes a library and all its functions.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-delete/">redis.io</a> for details.
     * @param libName The library name to delete.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionDelete("myLib", RANDOM).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionDelete(String libName, Route route);

    /**
     * Returns the serialized payload of all loaded libraries.<br>
     * The command will be routed to a random node.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-dump/">redis.io</a> for details.
     * @return The serialized payload of all loaded libraries.
     * @example
     *     <pre>{@code
     * byte[] data = client.functionDump().get();
     * // data can be used to restore loaded functions on any Redis instance
     * }</pre>
     */
    CompletableFuture<byte[]> functionDump();

    /**
     * Returns the serialized payload of all loaded libraries.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-dump/">redis.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The serialized payload of all loaded libraries.
     * @example
     *     <pre>{@code
     * byte[] data = client.functionDump(RANDOM).get().getSingleValue();
     * // data can be used to restore loaded functions on any Redis instance
     * }</pre>
     */
    CompletableFuture<ClusterValue<byte[]>> functionDump(Route route);

    /**
     * Restores libraries from the serialized payload returned by {@link #functionDump()}.<br>
     * The command will be routed to all primary nodes.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-restore/">redis.io</a> for
     *     details.
     * @param payload The serialized data from {@link #functionDump()}.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionRestore(data).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionRestore(byte[] payload);

    /**
     * Restores libraries from the serialized payload returned by {@link #functionDump()}.<br>
     * The command will be routed to all primary nodes.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-restore/">redis.io</a> for
     *     details.
     * @param payload The serialized data from {@link #functionDump()}.
     * @param policy A policy for handling existing libraries.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionRestore(data, FLUSH).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionRestore(byte[] payload, FunctionRestorePolicy policy);

    /**
     * Restores libraries from the serialized payload returned by {@link #functionDump(Route)}.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-restore/">redis.io</a> for
     *     details.
     * @param payload The serialized data from {@link #functionDump()}.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionRestore(data, ALL_PRIMARIES).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionRestore(byte[] payload, Route route);

    /**
     * Restores libraries from the serialized payload returned by {@link #functionDump(Route)}.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-restore/">redis.io</a> for
     *     details.
     * @param payload The serialized data from {@link #functionDump()}.
     * @param policy A policy for handling existing libraries.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionRestore(data, FLUSH, ALL_PRIMARIES).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionRestore(
            byte[] payload, FunctionRestorePolicy policy, Route route);

    /**
     * Invokes a previously loaded function.<br>
     * The command will be routed to a primary random node.<br>
     * To route to a replica please refer to {@link #fcallReadOnly(String)}.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/fcall/">redis.io</a> for details.
     * @param function The function name.
     * @return The invoked function's return value.
     * @example
     *     <pre>{@code
     * Object response = client.fcall("Deep_Thought").get();
     * assert response == 42L;
     * }</pre>
     */
    CompletableFuture<Object> fcall(String function);

    /**
     * Invokes a previously loaded function.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/fcall/">redis.io</a> for details.
     * @param function The function name.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The invoked function's return value wrapped by a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * ClusterValue<Object> response = client.fcall("Deep_Thought", ALL_NODES).get();
     * for (Object nodeResponse : response.getMultiValue().values()) {
     *   assert nodeResponse == 42L;
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object>> fcall(String function, Route route);

    /**
     * Invokes a previously loaded function.<br>
     * The command will be routed to a random primary node.<br>
     * To route to a replica please refer to {@link #fcallReadOnly(String, String[])}.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/fcall/">redis.io</a> for details.
     * @param function The function name.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @return The invoked function's return value.
     * @example
     *     <pre>{@code
     * String[] args = new String[] { "Answer", "to", "the", "Ultimate", "Question", "of", "Life,", "the", "Universe,", "and", "Everything" };
     * Object response = client.fcall("Deep_Thought", args).get();
     * assert response == 42L;
     * }</pre>
     */
    CompletableFuture<Object> fcall(String function, String[] arguments);

    /**
     * Invokes a previously loaded function.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/fcall/">redis.io</a> for details.
     * @param function The function name.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The invoked function's return value wrapped by a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * String[] args = new String[] { "Answer", "to", "the", "Ultimate", "Question", "of", "Life,", "the", "Universe,", "and", "Everything" };
     * ClusterValue<Object> response = client.fcall("Deep_Thought", args, RANDOM).get();
     * assert response.getSingleValue() == 42L;
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object>> fcall(String function, String[] arguments, Route route);

    /**
     * Invokes a previously loaded read-only function.<br>
     * The command is routed to a random node depending on the client's {@link ReadFrom} strategy.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/fcall_ro/">redis.io</a> for details.
     * @param function The function name.
     * @return The invoked function's return value.
     * @example
     *     <pre>{@code
     * Object response = client.fcallReadOnly("Deep_Thought").get();
     * assert response == 42L;
     * }</pre>
     */
    CompletableFuture<Object> fcallReadOnly(String function);

    /**
     * Invokes a previously loaded read-only function.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/fcall_ro/">redis.io</a> for details.
     * @param function The function name.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The invoked function's return value wrapped by a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * ClusterValue<Object> response = client.fcallReadOnly("Deep_Thought", ALL_NODES).get();
     * for (Object nodeResponse : response.getMultiValue().values()) {
     *   assert nodeResponse == 42L;
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object>> fcallReadOnly(String function, Route route);

    /**
     * Invokes a previously loaded function.<br>
     * The command is routed to a random node depending on the client's {@link ReadFrom} strategy.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/fcall_ro/">redis.io</a> for details.
     * @param function The function name.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @return The invoked function's return value.
     * @example
     *     <pre>{@code
     * String[] args = new String[] { "Answer", "to", "the", "Ultimate", "Question", "of", "Life,", "the", "Universe,", "and", "Everything" };
     * Object response = client.fcallReadOnly("Deep_Thought", args).get();
     * assert response == 42L;
     * }</pre>
     */
    CompletableFuture<Object> fcallReadOnly(String function, String[] arguments);

    /**
     * Invokes a previously loaded read-only function.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/fcall_ro/">redis.io</a> for details.
     * @param function The function name.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The invoked function's return value wrapped by a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * String[] args = new String[] { "Answer", "to", "the", "Ultimate", "Question", "of", "Life,", "the", "Universe,", "and", "Everything" };
     * ClusterValue<Object> response = client.fcallReadOnly("Deep_Thought", args, RANDOM).get();
     * assert response.getSingleValue() == 42L;
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object>> fcallReadOnly(
            String function, String[] arguments, Route route);

    /**
     * Kills a function that is currently executing.<br>
     * <code>FUNCTION KILL</code> terminates read-only functions only.<br>
     * The command will be routed to all primary nodes.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-kill/">redis.io</a> for details.
     * @return <code>OK</code> if function is terminated. Otherwise, throws an error.
     * @example
     *     <pre>{@code
     * String response = client.functionKill().get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionKill();

    /**
     * Kills a function that is currently executing.<br>
     * <code>FUNCTION KILL</code> terminates read-only functions only.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-kill/">redis.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code> if function is terminated. Otherwise, throws an error.
     * @example
     *     <pre>{@code
     * String response = client.functionKill(RANDOM).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionKill(Route route);

    /**
     * Returns information about the function that's currently running and information about the
     * available execution engines.<br>
     * The command will be routed to all primary nodes.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-stats/">redis.io</a> for details.
     * @return A <code>Map</code> with two keys:
     *     <ul>
     *       <li><code>running_script</code> with information about the running script.
     *       <li><code>engines</code> with information about available engines and their stats.
     *     </ul>
     *     See example for more details.
     * @example
     *     <pre>{@code
     * Map<String, Map<String, Map<String, Object>>> response = client.functionStats().get().getMultiValue();
     * for (String node : response.keySet()) {
     *   Map<String, Object> runningScriptInfo = response.get(node).get("running_script");
     *   if (runningScriptInfo != null) {
     *     String[] commandLine = (String[]) runningScriptInfo.get("command");
     *     System.out.printf("Node '%s' is currently running function '%s' with command line '%s', which has been running for %d ms%n",
     *         node, runningScriptInfo.get("name"), String.join(" ", commandLine), (long) runningScriptInfo.get("duration_ms"));
     *   }
     *   Map<String, Object> enginesInfo = response.get(node).get("engines");
     *   for (String engineName : enginesInfo.keySet()) {
     *     Map<String, Long> engine = (Map<String, Long>) enginesInfo.get(engineName);
     *     System.out.printf("Node '%s' supports engine '%s', which has %d libraries and %d functions in total%n",
     *         node, engineName, engine.get("libraries_count"), engine.get("functions_count"));
     *   }
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Map<String, Map<String, Object>>>> functionStats();

    /**
     * Returns information about the function that's currently running and information about the
     * available execution engines.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-stats/">redis.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A <code>Map</code> with two keys:
     *     <ul>
     *       <li><code>running_script</code> with information about the running script.
     *       <li><code>engines</code> with information about available engines and their stats.
     *     </ul>
     *     See example for more details.
     * @example
     *     <pre>{@code
     * Map<String, Map<String, Object>> response = client.functionStats(RANDOM).get().getSingleValue();
     * Map<String, Object> runningScriptInfo = response.get("running_script");
     * if (runningScriptInfo != null) {
     *   String[] commandLine = (String[]) runningScriptInfo.get("command");
     *   System.out.printf("Node is currently running function '%s' with command line '%s', which has been running for %d ms%n",
     *       runningScriptInfo.get("name"), String.join(" ", commandLine), (long)runningScriptInfo.get("duration_ms"));
     * }
     * Map<String, Object> enginesInfo = response.get("engines");
     * for (String engineName : enginesInfo.keySet()) {
     *   Map<String, Long> engine = (Map<String, Long>) enginesInfo.get(engineName);
     *   System.out.printf("Node supports engine '%s', which has %d libraries and %d functions in total%n",
     *       engineName, engine.get("libraries_count"), engine.get("functions_count"));
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Map<String, Map<String, Object>>>> functionStats(Route route);
}
