/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.connectors.handlers;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.unix.UnixChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import response.ResponseOuterClass.Response;

/** Builder for the channel used by {@link ChannelHandler}. */
@RequiredArgsConstructor
public class ProtobufSocketChannelInitializer extends ChannelInitializer<UnixChannel> {

    private final CallbackDispatcher callbackDispatcher;

    @Override
    public void initChannel(@NonNull UnixChannel ch) {
        ch.pipeline()
                // https://netty.io/4.1/api/io/netty/handler/codec/protobuf/ProtobufEncoder.html
                .addLast("frameDecoder", new ProtobufVarint32FrameDecoder())
                .addLast("frameEncoder", new ProtobufVarint32LengthFieldPrepender())
                .addLast("protobufDecoder", new ProtobufDecoder(Response.getDefaultInstance()))
                .addLast("protobufEncoder", new ProtobufEncoder())
                .addLast(new ReadHandler(callbackDispatcher))
                .addLast(new ChannelOutboundHandlerAdapter());
    }
}
