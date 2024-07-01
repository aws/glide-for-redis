/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.connectors.resources;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.unix.DomainSocketChannel;
import lombok.Getter;
import lombok.NonNull;

/**
 * Abstract base class that provides the EventLoopGroup and DomainSocketChannel to be used by the
 * Netty protocol.
 */
@Getter
public abstract class ThreadPoolResource {
    private EventLoopGroup eventLoopGroup;
    private Class<? extends DomainSocketChannel> domainSocketChannelClass;

    public ThreadPoolResource(
            @NonNull EventLoopGroup eventLoopGroup,
            @NonNull Class<? extends DomainSocketChannel> domainSocketChannelClass) {
        this.eventLoopGroup = eventLoopGroup;
        this.domainSocketChannelClass = domainSocketChannelClass;
    }
}
