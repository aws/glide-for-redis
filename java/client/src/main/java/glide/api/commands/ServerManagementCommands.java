/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.commands.FlushMode;
import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.InfoOptions.Section;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Server Management" group for a standalone client.
 *
 * @see <a href="https://redis.io/commands/?group=server">Server Management Commands</a>
 */
public interface ServerManagementCommands {

    /** A keyword for {@link #lolwut(int)} and {@link #lolwut(int, int[])}. */
    String VERSION_REDIS_API = "VERSION";

    /**
     * Gets information and statistics about the Redis server using the {@link Section#DEFAULT}
     * option.
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @return Response from Redis containing a <code>String</code> with the information for the
     *     default sections.
     * @example
     *     <pre>{@code
     * String response = client.info().get();
     * assert response.contains("# Stats");
     * }</pre>
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
     * @example
     *     <pre>{@code
     * String response = regularClient.info(InfoOptions.builder().section(STATS).build()).get();
     * assert response.contains("total_net_input_bytes");
     * }</pre>
     */
    CompletableFuture<String> info(InfoOptions options);

    /**
     * Changes the currently selected Redis database.
     *
     * @see <a href="https://redis.io/commands/select/">redis.io</a> for details.
     * @param index The index of the database to select.
     * @return A simple <code>OK</code> response.
     * @example
     *     <pre>{@code
     * String response = regularClient.select(0).get();
     * assert response.equals("OK");
     * }</pre>
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
     * @return The current server time as a <code>String</code> array with two elements: A <code>
     *     UNIX TIME</code> and the amount of microseconds already elapsed in the current second. The
     *     returned array is in a <code>[UNIX TIME, Microseconds already elapsed]</code> format.
     * @example
     *     <pre>{@code
     * String[] serverTime = client.time().get();
     * System.out.println("Server time is: " + serverTime[0] + "." + serverTime[1]);
     * }</pre>
     */
    CompletableFuture<String[]> time();

    /**
     * Returns <code>UNIX TIME</code> of the last DB save timestamp or startup timestamp if no save
     * was made since then.
     *
     * @see <a href="https://redis.io/commands/lastsave/">redis.io</a> for details.
     * @return <code>UNIX TIME</code> of the last DB save executed with success.
     * @example
     *     <pre>{@code
     * Long timestamp = client.lastsave().get();
     * System.out.printf("Last DB save was done at %s%n", Instant.ofEpochSecond(timestamp));
     * }</pre>
     */
    CompletableFuture<Long> lastsave();

    /**
     * Deletes all the keys of all the existing databases. This command never fails.
     *
     * @see <a href="https://valkey.io/commands/flushall/">valkey.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.flushall().get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> flushall();

    /**
     * Deletes all the keys of all the existing databases. This command never fails.
     *
     * @see <a href="https://valkey.io/commands/flushall/">valkey.io</a> for details.
     * @param mode The flushing mode, could be either {@link FlushMode#SYNC} or {@link
     *     FlushMode#ASYNC}.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.flushall(ASYNC).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> flushall(FlushMode mode);

    /**
     * Deletes all the keys of the currently selected database. This command never fails.
     *
     * @see <a href="https://valkey.io/commands/flushdb/">valkey.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.flushdb().get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> flushdb();

    /**
     * Deletes all the keys of the currently selected database. This command never fails.
     *
     * @see <a href="https://valkey.io/commands/flushdb/">valkey.io</a> for details.
     * @param mode The flushing mode, could be either {@link FlushMode#SYNC} or {@link
     *     FlushMode#ASYNC}.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.flushdb(ASYNC).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> flushdb(FlushMode mode);

    /**
     * Displays a piece of generative computer art and the Redis version.
     *
     * @see <a href="https://redis.io/commands/lolwut/">redis.io</a> for details.
     * @return A piece of generative computer art along with the current Redis version.
     * @example
     *     <pre>{@code
     * String data = client.lolwut().get();
     * System.out.println(data);
     * assert data.contains("Redis ver. 7.2.3");
     * }</pre>
     */
    CompletableFuture<String> lolwut();

    /**
     * Displays a piece of generative computer art and the Redis version.
     *
     * @see <a href="https://redis.io/commands/lolwut/">redis.io</a> for details.
     * @param parameters Additional set of arguments in order to change the output:
     *     <ul>
     *       <li>On Redis version <code>5</code>, those are length of the line, number of squares per
     *           row, and number of squares per column.
     *       <li>On Redis version <code>6</code>, those are number of columns and number of lines.
     *       <li>On other versions parameters are ignored.
     *     </ul>
     *
     * @return A piece of generative computer art along with the current Redis version.
     * @example
     *     <pre>{@code
     * String data = client.lolwut(new int[] { 40, 20 }).get();
     * System.out.println(data);
     * assert data.contains("Redis ver. 7.2.3");
     * }</pre>
     */
    CompletableFuture<String> lolwut(int[] parameters);

    /**
     * Displays a piece of generative computer art and the Redis version.
     *
     * @apiNote Versions 5 and 6 produce graphical things.
     * @see <a href="https://redis.io/commands/lolwut/">redis.io</a> for details.
     * @param version Version of computer art to generate.
     * @return A piece of generative computer art along with the current Redis version.
     * @example
     *     <pre>{@code
     * String data = client.lolwut(6).get();
     * System.out.println(data);
     * assert data.contains("Redis ver. 7.2.3");
     * }</pre>
     */
    CompletableFuture<String> lolwut(int version);

    /**
     * Displays a piece of generative computer art and the Redis version.
     *
     * @apiNote Versions 5 and 6 produce graphical things.
     * @see <a href="https://redis.io/commands/lolwut/">redis.io</a> for details.
     * @param version Version of computer art to generate.
     * @param parameters Additional set of arguments in order to change the output:
     *     <ul>
     *       <li>For version <code>5</code>, those are length of the line, number of squares per row,
     *           and number of squares per column.
     *       <li>For version <code>6</code>, those are number of columns and number of lines.
     *     </ul>
     *
     * @return A piece of generative computer art along with the current Redis version.
     * @example
     *     <pre>{@code
     * String data = client.lolwut(6, new int[] { 40, 20 }).get();
     * System.out.println(data);
     * assert data.contains("Redis ver. 7.2.3");
     * data = client.lolwut(5, new int[] { 30, 5, 5 }).get();
     * System.out.println(data);
     * assert data.contains("Redis ver. 7.2.3");
     * }</pre>
     */
    CompletableFuture<String> lolwut(int version, int[] parameters);

    /**
     * Returns the number of keys in the currently selected database.
     *
     * @see <a href="https://valkey.io/commands/dbsize/">valkey.io</a> for details.
     * @return The number of keys in the currently selected database.
     * @example
     *     <pre>{@code
     * Long numKeys = client.dbsize().get();
     * System.out.printf("Number of keys in the current database: %d%n", numKeys);
     * }</pre>
     */
    CompletableFuture<Long> dbsize();
}
