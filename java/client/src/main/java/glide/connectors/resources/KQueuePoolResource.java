/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.connectors.resources;

import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;

/**
 * Implementation of ThreadPoolResource for Kqueue-based systems. Enabling custom/default
 * configurations.
 */
public class KQueuePoolResource extends ThreadPoolResource {
    private static final String KQUEUE_EVENT_LOOP_IDENTIFIER = "glide-channel-kqueue-elg";

    public KQueuePoolResource() {
        this(
                new KQueueEventLoopGroup(
                        Runtime.getRuntime().availableProcessors(),
                        new DefaultThreadFactory(KQUEUE_EVENT_LOOP_IDENTIFIER, true)));
    }

    public KQueuePoolResource(KQueueEventLoopGroup eventLoopGroup) {
        super(eventLoopGroup, KQueueDomainSocketChannel.class);
    }
}
