/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.InfoOptions.Section;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Server Management" group for standalone clients.
 *
 * @see <a href="https://redis.io/commands/?group=server">Server Management Commands</a>
 */
public interface ServerManagementCommands {

    /**
     * Get information and statistics about the Redis server using the {@link Section#DEFAULT} option.
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @return Response from Redis containing a <code>String</code> with the information for the
     *     default sections.
     */
    CompletableFuture<String> info();

    /**
     * Get information and statistics about the Redis server.
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @param options A list of {@link Section} values specifying which sections of information to
     *     retrieve. When no parameter is provided, the {@link Section#DEFAULT} option is assumed.
     * @return Response from Redis containing a <code>String</code> with the information for the
     *     sections requested.
     */
    CompletableFuture<String> info(InfoOptions options);

    /**
     * Change the currently selected Redis database.
     *
     * @see <a href="https://redis.io/commands/select/">redis.io</a> for details.
     * @param index The index of the database to select.
     * @return A simple <code>OK</code> response.
     */
    CompletableFuture<String> select(long index);

    /**
     * Rewrites the configuration file with the current configuration.
     *
     * @see <a href="https://redis.io/commands/config-rewrite/">redis.io</a> for details.
     * @return <code>OK</code> when the configuration was rewritten properly, otherwise an error is
     *     thrown.
     * @example
     *     <pre>{@code
     * String response = client.configRewrite().get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> configRewrite();

    /**
     * Resets the statistics reported by Redis using the <a
     * href="https://redis.io/commands/info/">INFO</a> and <a
     * href="https://redis.io/commands/latency-histogram/">LATENCY HISTOGRAM</a> commands.
     *
     * @see <a href="https://redis.io/commands/config-resetstat/">redis.io</a> for details.
     * @return <code>OK</code> to confirm that the statistics were successfully reset.
     * @example
     *     <pre>{@code
     * String response = client.configResetStat().get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> configResetStat();

    /**
     * Reads the configuration parameters of a running Redis server.
     *
     * @see <a href="https://redis.io/commands/config-get/">redis.io</a> for details.
     * @param parameters An <code>array</code> of configuration parameter names to retrieve values
     *     for.
     * @return A <code>map</code> of values corresponding to the configuration parameters.
     * @example
     *     <pre>{@code
     * Map<String, String> configParams = client.configGet(new String[] {"timeout" , "maxmemory"}).get();
     * assert configParams.get("timeout").equals("1000");
     * assert configParams.get("maxmemory").equals("1GB");
     * }</pre>
     */
    CompletableFuture<Map<String, String>> configGet(String[] parameters);

    /**
     * Sets configuration parameters to the specified values.
     *
     * @see <a href="https://redis.io/commands/config-set/">redis.io</a> for details.
     * @param parameters A <code>map</code> consisting of configuration parameters and their
     *     respective values to set.
     * @return <code>OK</code> if all configurations have been successfully set. Otherwise, raises an
     *     error.
     * @example
     *     <pre>{@code
     * String response = client.configSet(Map.of("timeout", "1000", "maxmemory", "1GB")).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> configSet(Map<String, String> parameters);

    /**
     * Returns the server time.
     *
     * @see <a href="https://redis.io/commands/time/">redis.io</a> for details.
     * @return The current server time as a <code>String</code> array with two elements: A Unix
     *     timestamp and the amount of microseconds already elapsed in the current second. The
     *     returned array is in a [Unix timestamp, Microseconds already elapsed] format.
     * @example
     *     <pre>{@code
     * String[] serverTime = client.time().get();
     * System.out.println("Server time is: " + serverTime[0] + "." + serverTime[1]);
     * }</pre>
     */
    CompletableFuture<String[]> time();
}
