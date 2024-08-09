/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.GlideString;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Pub/Sub" group for standalone and cluster clients.
 *
 * @see <a href="https://valkey.io/commands/?group=pubsub">Pub/Sub Commands</a>
 */
public interface PubSubBaseCommands {

    /**
     * Publishes message on pubsub channel.
     *
     * @see <a href="https://valkey.io/commands/publish/">valkey.io</a> for details.
     * @param message The message to publish.
     * @param channel The channel to publish the message on.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.publish("The cat said 'meow'!", "announcements").get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> publish(String message, String channel);

    /**
     * Publishes message on pubsub channel.
     *
     * @see <a href="https://valkey.io/commands/publish/">valkey.io</a> for details.
     * @param message The message to publish.
     * @param channel The channel to publish the message on.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.publish(gs("The cat said 'meow'!"), gs("announcements")).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> publish(GlideString message, GlideString channel);

    /**
     * Lists the currently active channels.
     *
     * @apiNote When in cluster mode, the command is routed to all nodes, and aggregates the response
     *     into a single array.
     * @see <a href="https://valkey.io/commands/pubsub-channels/">valkey.io</a> for details.
     * @return An <code>Array</code> of all active channels.
     * @example
     *     <pre>{@code
     * String[] response = client.pubsubChannels().get();
     * assert Arrays.equals(new String[] { "channel1", "channel2" });
     * }</pre>
     */
    CompletableFuture<String[]> pubsubChannels();

    /**
     * Lists the currently active channels.<br>
     * Dislike of {@link #pubsubChannels()}, returns channel names as {@link GlideString}s.
     *
     * @apiNote When in cluster mode, the command is routed to all nodes, and aggregates the response
     *     into a single array.
     * @see <a href="https://valkey.io/commands/pubsub-channels/">valkey.io</a> for details.
     * @return An <code>Array</code> of all active channels.
     * @example
     *     <pre>{@code
     * GlideString[] response = client.pubsubChannels().get();
     * assert Arrays.equals(new GlideString[] { "channel1", "channel2" });
     * }</pre>
     */
    CompletableFuture<GlideString[]> pubsubChannelsBinary();

    /**
     * Lists the currently active channels.
     *
     * @apiNote When in cluster mode, the command is routed to all nodes, and aggregates the response
     *     into a single array.
     * @see <a href="https://valkey.io/commands/pubsub-channels/">valkey.io</a> for details.
     * @param pattern A glob-style pattern to match active channels.
     * @return An <code>Array</code> of currently active channels matching the given pattern.
     * @example
     *     <pre>{@code
     * String[] response = client.pubsubChannels("news.*").get();
     * assert Arrays.equals(new String[] { "news.sports", "news.weather" });
     * }</pre>
     */
    CompletableFuture<String[]> pubsubChannels(String pattern);

    /**
     * Lists the currently active channels.
     *
     * @apiNote When in cluster mode, the command is routed to all nodes, and aggregates the response
     *     into a single array.
     * @see <a href="https://valkey.io/commands/pubsub-channels/">valkey.io</a> for details.
     * @param pattern A glob-style pattern to match active channels.
     * @return An <code>Array</code> of currently active channels matching the given pattern.
     * @example
     *     <pre>{@code
     * GlideString[] response = client.pubsubChannels(gs("news.*")).get();
     * assert Arrays.equals(new GlideString[] { gs("news.sports"), gs("news.weather") });
     * }</pre>
     */
    CompletableFuture<GlideString[]> pubsubChannels(GlideString pattern);

    /**
     * Returns the number of unique patterns that are subscribed to by clients.
     *
     * @apiNote
     *     <ul>
     *       <li>When in cluster mode, the command is routed to all nodes, and aggregates the response
     *           into a single array.
     *       <li>This is the total number of unique patterns all the clients are subscribed to, not
     *           the count of clients subscribed to patterns.
     *     </ul>
     *
     * @see <a href="https://valkey.io/commands/pubsub-numpat/">valkey.io</a> for details.
     * @return The number of unique patterns.
     * @example
     *     <pre>{@code
     * Long result = client.pubsubNumPat().get();
     * assert result == 3L;
     * }</pre>
     */
    CompletableFuture<Long> pubsubNumPat();

    /**
     * Returns the number of subscribers (exclusive of clients subscribed to patterns) for the
     * specified channels.
     *
     * @apiNote When in cluster mode, the command is routed to all nodes, and aggregates the response
     *     into a single map.
     * @see <a href="https://valkey.io/commands/pubsub-numsub/">valkey.io</a> for details.
     * @param channels The list of channels to query for the number of subscribers.
     * @return A <code>Map</code> where keys are the channel names and values are the numbers of
     *     subscribers.
     * @example
     *     <pre>{@code
     * Map<String, Long> result = client.pubsubNumSub(new String[] {"channel1", "channel2"}).get();
     * assert result.equals(Map.of("channel1", 3L, "channel2", 5L));
     * }</pre>
     */
    CompletableFuture<Map<String, Long>> pubsubNumSub(String[] channels);

    /**
     * Returns the number of subscribers (exclusive of clients subscribed to patterns) for the
     * specified channels.
     *
     * @apiNote When in cluster mode, the command is routed to all nodes, and aggregates the response
     *     into a single map.
     * @see <a href="https://valkey.io/commands/pubsub-numsub/">valkey.io</a> for details.
     * @param channels The list of channels to query for the number of subscribers.
     * @return A <code>Map</code> where keys are the channel names and values are the numbers of
     *     subscribers.
     * @example
     *     <pre>{@code
     * Map<GlideString, Long> result = client.pubsubNumSub(new GlideString[] {gs("channel1"), gs("channel2")}).get();
     * assert result.equals(Map.of(gs("channel1"), 3L, gs("channel2"), 5L));
     * }</pre>
     */
    CompletableFuture<Map<GlideString, Long>> pubsubNumSub(GlideString[] channels);
}
