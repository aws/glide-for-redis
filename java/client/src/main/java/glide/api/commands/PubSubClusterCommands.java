/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Pub/Sub" group for a cluster client.
 *
 * @see <a href="https://redis.io/docs/latest/commands/publish/">Pub/Sub Commands</a>
 */
public interface PubSubClusterCommands {

    /**
     * Publishes message on pubsub channel in sharded mode.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://valkey.io/commands/spublish/">redis.io</a> for details.
     * @param channel The channel to publish the message on.
     * @param message The message to publish.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.spublish("announcements", "The cat said 'meow'!").get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> spublish(String channel, String message);
}
