/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.RedisClient;
import glide.api.RedisClusterClient;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute;

// TODO add links to script flush
/**
 * Defines flushing mode for:
 *
 * <ul>
 *   <li><code>FLUSHALL</code> command implemented by {@link RedisClient#flushall(FlushMode)},
 *       {@link RedisClusterClient#flushall(FlushMode)}, and {@link
 *       RedisClusterClient#flushall(FlushMode, SingleNodeRoute)}.
 *   <li><code>FLUSHDB</code> command implemented by {@link RedisClient#flushdb(FlushMode)}, {@link
 *       RedisClusterClient#flushdb(FlushMode)}, and {@link RedisClusterClient#flushdb(FlushMode,
 *       SingleNodeRoute)}.
 *   <li><code>FUNCTION FLUSH</code> command implemented by {@link
 *       RedisClient#functionFlush(FlushMode)}, {@link RedisClusterClient#functionFlush(FlushMode)},
 *       and {@link RedisClusterClient#functionFlush(FlushMode, Route)}.
 * </ul>
 *
 * @see <a href="https://valkey.io/commands/flushall/">flushall</a>, <a
 *     href="https://valkey.io/commands/flushdb/">flushdb</a>, and <a
 *     href="https://valkey.io/commands/function-flush/">function flush</a> at valkey.io
 */
public enum FlushMode {
    /**
     * Flushes synchronously.
     *
     * @since Redis 6.2 and above.
     */
    SYNC,
    /** Flushes asynchronously. */
    ASYNC
}
