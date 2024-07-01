/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.connectors.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import response.ResponseOuterClass.Response;

/** Handler for inbound traffic though UDS. Used by Netty. */
@RequiredArgsConstructor
public class ReadHandler extends ChannelInboundHandlerAdapter {

    private final CallbackDispatcher callbackDispatcher;

    /** Submit responses from glide to an instance {@link CallbackDispatcher} to handle them. */
    @Override
    public void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg)
            throws RuntimeException {
        if (msg instanceof Response) {
            Response response = (Response) msg;
            callbackDispatcher.completeRequest(response);
            ctx.fireChannelRead(msg);
            return;
        }
        throw new RuntimeException("Unexpected message in socket");
    }

    /** Handles uncaught exceptions from {@link #channelRead(ChannelHandlerContext, Object)}. */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // TODO: log thru logger
        System.out.printf("=== exceptionCaught %s %s %n", ctx, cause);
        cause.printStackTrace();

        callbackDispatcher.distributeClosingException(
                "An unhandled error while reading from UDS channel: " + cause);
    }
}
