/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.connectors.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ThreadPoolResourceAllocatorTest {

    @BeforeEach
    public void init() {
        var threadPoolResource = ThreadPoolResourceAllocator.getOrCreate(() -> null);
        if (threadPoolResource != null) {
            threadPoolResource.getEventLoopGroup().shutdownGracefully();
            ThreadPoolResourceAllocator.getOrCreate(() -> null);
        }
    }

    @Test
    public void getOrCreate_returns_default_after_repeated_calls() {
        ThreadPoolResource mockedThreadPoolResource = mock(ThreadPoolResource.class);
        EventLoopGroup mockedEventLoopGroup = mock(EventLoop.class);

        Supplier<ThreadPoolResource> threadPoolSupplier = () -> mockedThreadPoolResource;

        when(mockedThreadPoolResource.getEventLoopGroup()).thenReturn(mockedEventLoopGroup);
        when(mockedEventLoopGroup.isShuttingDown()).thenReturn(false);

        ThreadPoolResource theResource = ThreadPoolResourceAllocator.getOrCreate(threadPoolSupplier);
        assertEquals(mockedThreadPoolResource, theResource);

        // Ensure that supplier only is invoked once to set up the shared resource
        ThreadPoolResource theSameResource =
                ThreadPoolResourceAllocator.getOrCreate(threadPoolSupplier);
        assertEquals(mockedThreadPoolResource, theSameResource);

        // teardown
        when(mockedEventLoopGroup.isShuttingDown()).thenReturn(true);
    }

    @Test
    public void getOrCreate_returns_new_thread_pool_after_shutdown() {
        ThreadPoolResource mockedThreadPoolResource = mock(ThreadPoolResource.class);
        EventLoopGroup mockedEventLoopGroup = mock(EventLoop.class);

        Supplier<ThreadPoolResource> threadPoolSupplier = () -> mockedThreadPoolResource;

        when(mockedThreadPoolResource.getEventLoopGroup()).thenReturn(mockedEventLoopGroup);
        when(mockedEventLoopGroup.isShuttingDown()).thenReturn(true);

        ThreadPoolResource theResource = ThreadPoolResourceAllocator.getOrCreate(threadPoolSupplier);
        assertEquals(mockedThreadPoolResource, theResource);

        // Ensure that supplier only is invoked once to set up the shared resource
        ThreadPoolResource theSameResource =
                ThreadPoolResourceAllocator.getOrCreate(threadPoolSupplier);
        assertEquals(mockedThreadPoolResource, theSameResource);

        // teardown
        when(mockedEventLoopGroup.isShuttingDown()).thenReturn(true);
    }
}
