/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import java.util.Optional;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** PubSub message received by the client. */
@Getter
@EqualsAndHashCode
public class PubSubMessage {
    /** An incoming message received. */
    private final String message;

    /** A name of the originating channel. */
    private final String channel;

    /** A pattern matched to the channel name. */
    private final Optional<String> pattern;

    public PubSubMessage(String message, String channel, String pattern) {
        this.message = message;
        this.channel = channel;
        this.pattern = Optional.ofNullable(pattern);
    }

    public PubSubMessage(String message, String channel) {
        this.message = message;
        this.channel = channel;
        this.pattern = Optional.empty();
    }

    @Override
    public String toString() {
        String res = String.format("%s, channel = %s", message, channel);
        if (pattern.isPresent()) {
            res += ", pattern = " + pattern.get();
        }
        return res;
    }
}
