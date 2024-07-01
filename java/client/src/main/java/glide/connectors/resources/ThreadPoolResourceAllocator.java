/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.connectors.resources;

import java.util.function.Supplier;

/** A class responsible to allocating and deallocating the default Thread Pool Resource. */
public class ThreadPoolResourceAllocator {
    private static final Object lock = new Object();
    private static ThreadPoolResource defaultThreadPoolResource = null;

    /**
     * Sets up and returns the shared default ThreadPoolResource instance. On its first invocation,
     * this method creates and caches the defaultThreadPoolResource with a ThreadPoolResource instance
     * using the provided supplier. Subsequent calls return the cached instance, ensuring resource
     * sharing among clients. If the current defaultThreadPoolResource’s ELG is in a shutting down
     * state, the cache is invalidated and a new default ThreadPoolResource is created and cached.
     *
     * @param supplier The supplier function used to create the ThreadPoolResource
     * @return The default shared ThreadPoolResource instance.
     */
    public static ThreadPoolResource getOrCreate(Supplier<ThreadPoolResource> supplier) {
        // once the default is set, we want to avoid hitting the lock
        if (defaultThreadPoolResource != null
                && !defaultThreadPoolResource.getEventLoopGroup().isShuttingDown()) {
            return defaultThreadPoolResource;
        }

        synchronized (lock) {
            if (defaultThreadPoolResource == null
                    || defaultThreadPoolResource.getEventLoopGroup().isShuttingDown()) {
                defaultThreadPoolResource = supplier.get();
            }
        }

        return defaultThreadPoolResource;
    }

    /**
     * A JVM shutdown hook to be registered. It is responsible for closing connection and freeing
     * resources. It is recommended to use a class instead of lambda to ensure that it is called.<br>
     * See {@link Runtime#addShutdownHook}.
     */
    private static class ShutdownHook implements Runnable {
        @Override
        public void run() {
            if (defaultThreadPoolResource != null) {
                defaultThreadPoolResource.getEventLoopGroup().shutdownGracefully();
                defaultThreadPoolResource = null;
            }
        }
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownHook(), "Glide-shutdown-hook"));
    }
}
