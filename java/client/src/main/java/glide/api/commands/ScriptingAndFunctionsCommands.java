/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.commands.FlushMode;
import glide.api.models.commands.function.FunctionRestorePolicy;
import glide.api.models.configuration.ReadFrom;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Scripting and Function" group for a standalone
 * client.
 *
 * @see <a href="https://redis.io/docs/latest/commands/?group=scripting">Scripting and Function
 *     Commands</a>
 */
public interface ScriptingAndFunctionsCommands {

    /**
     * Loads a library to Redis.
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
     * Returns information about the functions and libraries.
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
     *             function.get("name"), String. join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     * }
     * }</pre>
     */
    CompletableFuture<Map<String, Object>[]> functionList(boolean withCode);

    /**
     * Returns information about the functions and libraries.
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
     *             function.get("name"), String. join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     * }
     * }</pre>
     */
    CompletableFuture<Map<String, Object>[]> functionList(String libNamePattern, boolean withCode);

    /**
     * Deletes all function libraries.
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
     * Deletes all function libraries.
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
     * Deletes a library and all its functions.
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
     * Returns the serialized payload of all loaded libraries.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-dump/">redis.io</a> for details.
     * @return The serialized payload of all loaded libraries.
     * @example
     *     <pre>{@code
     * byte[] data = client.functionDump().get();
     * // now data could be saved to restore loaded functions on any Redis instance
     * }</pre>
     */
    CompletableFuture<byte[]> functionDump();

    /**
     * Restores libraries from the serialized payload returned by {@link #functionDump()}.
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
     * Restores libraries from the serialized payload returned by {@link #functionDump()}..
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
     * Invokes a previously loaded function.<br>
     * This command is routed to primary nodes only.<br>
     * To route to a replica please refer to {@link #fcallReadOnly}.
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
     * Invokes a previously loaded read-only function.<br>
     * This command is routed depending on the client's {@link ReadFrom} strategy.
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
     * Kills a function that is currently executing.<br>
     * <code>FUNCTION KILL</code> terminates read-only functions only.
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
     * Returns information about the function that's currently running and information about the
     * available execution engines.
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
     * Map<String, Map<String, Object>> response = client.functionStats().get();
     * Map<String, Object> runningScriptInfo = response.get("running_script");
     * if (runningScriptInfo != null) {
     *   String[] commandLine = (String[]) runningScriptInfo.get("command");
     *   System.out.printf("Server is currently running function '%s' with command line '%s', which has been running for %d ms%n",
     *       runningScriptInfo.get("name"), String.join(" ", commandLine), (long)runningScriptInfo.get("duration_ms"));
     * }
     * Map<String, Object> enginesInfo = response.get("engines");
     * for (String engineName : enginesInfo.keySet()) {
     *   Map<String, Long> engine = (Map<String, Long>) enginesInfo.get(engineName);
     *   System.out.printf("Server supports engine '%s', which has %d libraries and %d functions in total%n",
     *       engineName, engine.get("libraries_count"), engine.get("functions_count"));
     * }
     * }</pre>
     */
    CompletableFuture<Map<String, Map<String, Object>>> functionStats();
}
