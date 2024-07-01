/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.connectors.handlers;

import glide.api.models.PubSubMessage;
import glide.api.models.configuration.BaseSubscriptionConfiguration.MessageCallback;
import glide.api.models.exceptions.RedisException;
import glide.managers.BaseResponseResolver;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import response.ResponseOuterClass.Response;

/** Handler for incoming push messages (subscriptions). */
@Getter
@RequiredArgsConstructor
public class MessageHandler {

    // TODO maybe store `BaseSubscriptionConfiguration` as is?
    /**
     * A user callback to call for every incoming message, if given. If missing, messages are pushed
     * into the {@link #queue}.
     */
    private final Optional<MessageCallback> callback;

    /** An arbitrary user object to be passed to callback. */
    private final Optional<Object> context;

    /** Helper which extracts data from received {@link Response}s from GLIDE. */
    private final BaseResponseResolver responseResolver;

    /** A message queue wrapper. */
    private final ConcurrentLinkedDeque<PubSubMessage> queue = new ConcurrentLinkedDeque<>();

    /** Process a push (PUBSUB) message received as a part of {@link Response} from GLIDE. */
    public void handle(Response response) {
        Object data = responseResolver.apply(response);
        if (!(data instanceof Map)) {
            // TODO log thru logger https://github.com/aws/glide-for-redis/pull/1422
            System.err.println("Received invalid push: empty or in incorrect format.");
            throw new RedisException("Received invalid push: empty or in incorrect format.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> push = (Map<String, Object>) data;
        PushKind pushType = Enum.valueOf(PushKind.class, (String) push.get("kind"));
        Object[] values = (Object[]) push.get("values");

        switch (pushType) {
            case Disconnection:
                // TODO log thru logger https://github.com/aws/glide-for-redis/pull/1422
                // ClientLogger.log(
                //    LogLevel.WARN,
                //    "disconnect notification",
                //    "Transport disconnected, messages might be lost",
                break;
            case PMessage:
                handle(new PubSubMessage((String) values[2], (String) values[1], (String) values[0]));
                return;
            case Message:
            case SMessage:
                handle(new PubSubMessage((String) values[1], (String) values[0]));
                return;
            case Subscribe:
            case PSubscribe:
            case SSubscribe:
            case Unsubscribe:
            case PUnsubscribe:
            case SUnsubscribe:
                // ignore for now
                // TODO log thru logger https://github.com/aws/glide-for-redis/pull/1422
                System.out.printf(
                        "Received push notification of type '%s': %s\n",
                        pushType,
                        Arrays.stream(values)
                                .map(v -> v instanceof Number ? v.toString() : String.format("'%s'", v))
                                .collect(Collectors.joining(" ")));
                break;
            default:
                // TODO log thru logger https://github.com/aws/glide-for-redis/pull/1422
                System.err.printf("Received push with unsupported type: %s.\n", pushType);
                // ClientLogger.log(
                //    LogLevel.WARN,
                //    "unknown notification",
                //    f"Unknown notification message: '{message_kind}'",
        }
    }

    /** Process a {@link PubSubMessage} received. */
    private void handle(PubSubMessage message) {
        if (callback.isPresent()) {
            callback.get().accept(message, context.orElse(null));
        } else {
            queue.push(message);
        }
    }

    /** Push type enum copy-pasted 1:1 from `redis-rs`. */
    enum PushKind {
        /// `Disconnection` is sent from the **library** when connection is closed.
        Disconnection,
        /// Other kind to catch future kinds.
        Other,
        /// `invalidate` is received when a key is changed/deleted.
        Invalidate,
        /// `message` is received when pubsub message published by another client.
        Message,
        /// `pmessage` is received when pubsub message published by another client and client subscribed
        // to topic via pattern.
        PMessage,
        /// `smessage` is received when pubsub message published by another client and client subscribed
        // to it with sharding.
        SMessage,
        /// `unsubscribe` is received when client unsubscribed from a channel.
        Unsubscribe,
        /// `punsubscribe` is received when client unsubscribed from a pattern.
        PUnsubscribe,
        /// `sunsubscribe` is received when client unsubscribed from a shard channel.
        SUnsubscribe,
        /// `subscribe` is received when client subscribed to a channel.
        Subscribe,
        /// `psubscribe` is received when client subscribed to a pattern.
        PSubscribe,
        /// `ssubscribe` is received when client subscribed to a shard channel.
        SSubscribe,
    }
}
