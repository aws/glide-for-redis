/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.utils;

import connection_request.ConnectionRequestOuterClass.ConnectionRequest;
import glide.connectors.resources.Platform;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.DomainSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import redis_request.RedisRequestOuterClass.RedisRequest;
import response.ResponseOuterClass.ConstantResponse;
import response.ResponseOuterClass.Response;

public class RustCoreMock {

    @FunctionalInterface
    public interface GlideMock {
        default boolean isRaw() {
            return true;
        }

        byte[] handle(byte[] request);
    }

    public abstract static class GlideMockProtobuf implements GlideMock {
        @Override
        public boolean isRaw() {
            return false;
        }

        @Override
        public byte[] handle(byte[] request) {
            return new byte[0];
        }

        /** Return `null` to do not reply. */
        public abstract Response connection(ConnectionRequest request);

        /** Return `null` to do not reply. */
        public abstract Response.Builder redisRequest(RedisRequest request);

        public Response redisRequestWithCallbackId(RedisRequest request) {
            var responseDraft = redisRequest(request);
            return responseDraft == null
                    ? null
                    : responseDraft.setCallbackIdx(request.getCallbackIdx()).build();
        }

        public static Response.Builder OK() {
            return Response.newBuilder().setConstantResponse(ConstantResponse.OK);
        }
    }

    /** Thread pool supplied to <em>Netty</em> to perform all async IO. */
    private final EventLoopGroup group;

    private final Channel channel;

    private final String socketPath;

    private static RustCoreMock instance;

    private GlideMock messageProcessor;

    /** Update {@link GlideMock} into a running {@link RustCoreMock}. */
    public static void updateGlideMock(GlideMock newMock) {
        instance.messageProcessor = newMock;
    }

    private final AtomicBoolean failed = new AtomicBoolean(false);

    /** Get and clear failure status. */
    public static boolean failed() {
        return instance.failed.compareAndSet(true, false);
    }

    @SneakyThrows
    private RustCoreMock() {
        var threadPoolResource = Platform.getThreadPoolResourceSupplier().get();
        socketPath = Files.createTempFile("GlideCoreMock", null).toString();
        group = threadPoolResource.getEventLoopGroup();
        channel =
                new ServerBootstrap()
                        .group(group)
                        .channel(
                                Platform.getCapabilities().isEPollAvailable()
                                        ? EpollServerDomainSocketChannel.class
                                        : KQueueServerDomainSocketChannel.class)
                        .childHandler(
                                new ChannelInitializer<DomainSocketChannel>() {

                                    @Override
                                    protected void initChannel(@NonNull DomainSocketChannel ch) {
                                        ch.pipeline()
                                                // https://netty.io/4.1/api/io/netty/handler/codec/protobuf/ProtobufEncoder.html
                                                .addLast("frameDecoder", new ProtobufVarint32FrameDecoder())
                                                .addLast("frameEncoder", new ProtobufVarint32LengthFieldPrepender())
                                                .addLast("protobufEncoder", new ProtobufEncoder())
                                                .addLast(new UdsServer(ch));
                                    }
                                })
                        .bind(new DomainSocketAddress(socketPath))
                        .syncUninterruptibly()
                        .channel();
    }

    public static String start(GlideMock messageProcessor) {
        if (instance != null) {
            stop();
        }
        instance = new RustCoreMock();
        instance.messageProcessor = messageProcessor;
        return instance.socketPath;
    }

    @SneakyThrows
    public static void stop() {
        if (instance != null) {
            instance.channel.close().syncUninterruptibly();
            instance.group.shutdownGracefully().get(5, TimeUnit.SECONDS);
            instance = null;
        }
    }

    @RequiredArgsConstructor
    private class UdsServer extends ChannelInboundHandlerAdapter {

        private final Channel ch;

        // This works with only one connected client.
        // TODO Rework with `channelActive` override.
        private final AtomicBoolean anybodyConnected = new AtomicBoolean(false);

        @Override
        public void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg)
                throws Exception {
            var buf = (ByteBuf) msg;
            var bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            buf.release();
            if (messageProcessor.isRaw()) {
                ch.writeAndFlush(Unpooled.copiedBuffer(messageProcessor.handle(bytes)));
                return;
            }
            var handler = (GlideMockProtobuf) messageProcessor;
            Response response;
            if (!anybodyConnected.get()) {
                var connection = ConnectionRequest.parseFrom(bytes);
                response = handler.connection(connection);
                anybodyConnected.setPlain(true);
            } else {
                var request = RedisRequest.parseFrom(bytes);
                response = handler.redisRequestWithCallbackId(request);
            }
            if (response != null) {
                ctx.writeAndFlush(response);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
            failed.setPlain(true);
        }
    }
}
