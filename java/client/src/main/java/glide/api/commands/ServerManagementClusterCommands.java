/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterValue;
import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Set Commands" group for standalone clients and
 * cluster clients.
 *
 * @see <a href="https://redis.io/commands/?group=set">Set Commands</a>
 */
public interface ServerManagementClusterCommands {

    /**
     * Get information and statistics about the Redis server using the {@link Section#DEFAULT} option.
     * The command will be routed to all primary nodes.
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @return Response from Redis cluster with a <code>Map{@literal <String, String>}</code> with
     *     each address as the key and its corresponding value is the information for the node.
     * @example
     *     <p><code>
     *     {@literal Map<String, String>} routedInfoResult = clusterClient.info().get().getMultiValue();
     *     </code>
     */
    CompletableFuture<ClusterValue<String>> info();

    /**
     * Get information and statistics about the Redis server. If no argument is provided, so the
     * {@link Section#DEFAULT} option is assumed.
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return Response from Redis cluster with a <code>String</code> with the requested Sections.
     *     When specifying a <code>route</code> other than a single node, it returns a <code>
     *     Map{@literal <String, String>}</code> with each address as the key and its corresponding
     *     value is the information for the node.
     */
    CompletableFuture<ClusterValue<String>> info(Route route);

    /**
     * Get information and statistics about the Redis server. The command will be routed to all
     * primary nodes.
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @param options A list of {@link InfoOptions.Section} values specifying which sections of
     *     information to retrieve. When no parameter is provided, the {@link
     *     InfoOptions.Section#DEFAULT} option is assumed.
     * @return Response from Redis cluster with a <code>Map{@literal <String, String>}</code> with
     *     each address as the key and its corresponding value is the information of the sections
     *     requested for the node.
     */
    CompletableFuture<ClusterValue<String>> info(InfoOptions options);

    /**
     * Get information and statistics about the Redis server.
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @param options A list of {@link InfoOptions.Section} values specifying which sections of
     *     information to retrieve. When no parameter is provided, the {@link
     *     InfoOptions.Section#DEFAULT} option is assumed.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return Response from Redis cluster with a <code>String</code> with the requested sections.
     *     When specifying a <code>route</code> other than a single node, it returns a <code>
     *     Map{@literal <String, String>}</code> with each address as the key and its corresponding
     *     value is the information of the sections requested for the node.
     */
    CompletableFuture<ClusterValue<String>> info(InfoOptions options, Route route);

    /**
     * Rewrites the configuration file with the current configuration.<br>
     * The command will be routed automatically to all nodes.
     *
     * @see <a href="https://redis.io/commands/config-rewrite/">redis.io</a> for details.
     * @return <code>OK</code> when the configuration was rewritten properly, otherwise an error is
     *     thrown.
     * @example
     *     <pre>{@code
     * String response = client.configRewrite().get();
     * assert response.equals("OK")
     * }</pre>
     */
    CompletableFuture<String> configRewrite();

    /**
     * Rewrites the configuration file with the current configuration.
     *
     * @see <a href="https://redis.io/commands/config-rewrite/">redis.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code> when the configuration was rewritten properly, otherwise an error is
     *     thrown.
     * @example
     *     <pre>{@code
     * String response = client.configRewrite(ALL_PRIMARIES).get();
     * assert response.equals("OK")
     * }</pre>
     */
    CompletableFuture<String> configRewrite(Route route);

    /**
     * Resets the statistics reported by Redis using the <a
     * href="https://redis.io/commands/info/">INFO</a> and <a
     * href="https://redis.io/commands/latency-histogram/">LATENCY HISTOGRAM</a> commands.<br>
     * The command will be routed automatically to all nodes.
     *
     * @see <a href="https://redis.io/commands/config-resetstat/">redis.io</a> for details.
     * @return <code>OK</code> to confirm that the statistics were successfully reset.
     * @example
     *     <pre>{@code
     * String response = client.configResetStat().get();
     * assert response.equals("OK")
     * }</pre>
     */
    CompletableFuture<String> configResetStat();

    /**
     * Resets the statistics reported by Redis using the <a
     * href="https://redis.io/commands/info/">INFO</a> and <a
     * href="https://redis.io/commands/latency-histogram/">LATENCY HISTOGRAM</a> commands.
     *
     * @see <a href="https://redis.io/commands/config-resetstat/">redis.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code> to confirm that the statistics were successfully reset.
     * @example
     *     <pre>{@code
     * String response = client.configResetStat(ALL_PRIMARIES).get();
     * assert response.equals("OK")
     * }</pre>
     */
    CompletableFuture<String> configResetStat(Route route);

    /**
     * Reads the configuration parameters of a running Redis server.<br>
     * The command will be sent to a random node.
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
     * Reads the configuration parameters of a running Redis server.
     *
     * @see <a href="https://redis.io/commands/config-get/">redis.io</a> for details.
     * @param parameters An <code>array</code> of configuration parameter names to retrieve values
     *     for.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A <code>map</code> of values corresponding to the configuration parameters.<br>
     *     When specifying a route other than a single node, it returns a dictionary where each
     *     address is the key and its corresponding node response is the value.
     * @example
     *     <pre>{@code
     * Map<String, String> configParams = client.configGet("timeout", RANDOM).get().getSingleValue();
     * assert configParams.get("timeout").equals("1000");
     *
     * Map<String, Map<String, String>> configParamsPerNode = client.configGet("maxmemory", ALL_NODES).get().getMultiValue();
     * assert configParamsPerNode.get("node1.example.com:6379").get("maxmemory").equals("1GB");
     * assert configParamsPerNode.get("node2.example.com:6379").get("maxmemory").equals("2GB");
     * }</pre>
     */
    CompletableFuture<ClusterValue<Map<String, String>>> configGet(String[] parameters, Route route);

    /**
     * Sets configuration parameters to the specified values.<br>
     * The command will be sent to all nodes.
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
     * Sets configuration parameters to the specified values.
     *
     * @see <a href="https://redis.io/commands/config-set/">redis.io</a> for details.
     * @param parameters A <code>map</code> consisting of configuration parameters and their
     *     respective values to set.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code> if all configurations have been successfully set. Otherwise, raises an
     *     error.
     * @example
     *     <pre>{@code
     * String response = client.configSet(Map.of("timeout", "1000", "maxmemory", "1GB"), ALL_PRIMARIES).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> configSet(Map<String, String> parameters, Route route);

    /**
     * Returns the server time. <br>
     * The command will be routed to a random node.
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

    /**
     * Returns the server time.
     *
     * @see <a href="https://redis.io/commands/time/">redis.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The current server time as a <code>String</code> array with two elements: A Unix
     *     timestamp and the amount of microseconds already elapsed in the current second. The
     *     returned array is in a [Unix timestamp, Microseconds already elapsed] format.
     * @example
     *     <pre>{@code
     * // Command sent to a single random node via RANDOM route, expecting a SingleValue result.
     * String[] serverTime = client.time().get(RANDOM).getSingleValue();
     * System.out.println("Server time is: " + serverTime[0] + "." + serverTime[1]);
     *
     * // Command sent to all nodes via ALL_NODES route, expecting a MultiValue result.
     * Map<String, String[]> serverTimeForAllNodes = client.time(ALL_NODES).get().getMultiValue();
     * for(var serverTimePerNode : serverTimeForAllNodes.getMultiValue().entrySet()) {
     *     String node = serverTimePerNode.getKey();
     *     String serverTimePerNodeStr = serverTimePerNode.getValue()[0] + "." + serverTimePerNode.getValue()[1];
     *     System.out.println("Server time for node [" + node + "]: " + serverTimePerNodeStr);
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<String[]>> time(Route route);
}
