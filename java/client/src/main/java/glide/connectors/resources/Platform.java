/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.connectors.resources;

import io.netty.channel.epoll.Epoll;
import io.netty.channel.kqueue.KQueue;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.UtilityClass;

/**
 * An auxiliary class purposed to detect platform (OS + JVM) {@link Capabilities} and allocate
 * corresponding resources.
 */
@UtilityClass
public class Platform {

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @ToString
    public static class Capabilities {
        private final boolean isKQueueAvailable;
        private final boolean isEPollAvailable;
        // TODO support IO-Uring and NIO
        private final boolean isIOUringAvailable;
        // At the moment, Windows is not supported
        // Probably we should use NIO (NioEventLoopGroup) for Windows.
        private final boolean isNIOAvailable;
    }

    /** Detected platform (OS + JVM) capabilities. Not supposed to be changed in runtime. */
    @Getter
    private static final Capabilities capabilities =
            new Capabilities(isKQueueAvailable(), isEPollAvailable(), false, false);

    /** Detect <em>kqueue</em> availability. */
    private static boolean isKQueueAvailable() {
        try {
            Class.forName("io.netty.channel.kqueue.KQueue");
            return KQueue.isAvailable();
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Detect <em>epoll</em> availability. */
    private static boolean isEPollAvailable() {
        try {
            Class.forName("io.netty.channel.epoll.Epoll");
            return Epoll.isAvailable();
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static Supplier<ThreadPoolResource> getThreadPoolResourceSupplier() {
        if (Platform.getCapabilities().isKQueueAvailable()) {
            return KQueuePoolResource::new;
        }

        if (Platform.getCapabilities().isEPollAvailable()) {
            return EpollResource::new;
        }
        // TODO support IO-Uring and NIO
        throw new RuntimeException("Current platform supports no known thread pool resources");
    }
}
