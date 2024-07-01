/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.stream;

import glide.api.commands.StreamBaseCommands;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;

/**
 * Arguments for {@link StreamBaseCommands#xpending} to specify additional filter items by idle time
 * and consumer.
 *
 * @see <a href="https://valkey.io/commands/xpending/">valkey.io</a>
 */
@Builder
public class StreamPendingOptions {

    /** Redis api string to designate IDLE or minimum idle time */
    public static final String IDLE_TIME_REDIS_API = "IDLE";

    /** Filters pending entries by their idle time - in Milliseconds */
    private final Long minIdleTime; // Milliseconds

    /** Filters pending entries by consumer */
    private final String consumer;

    /**
     * Convert StreamPendingOptions arguments to a string array
     *
     * @return arguments converted to an array to be consumed by Redis
     */
    public String[] toArgs(StreamRange start, StreamRange end, long count) {
        List<String> optionArgs = new ArrayList<>();
        if (minIdleTime != null) {
            optionArgs.add(IDLE_TIME_REDIS_API);
            optionArgs.add(Long.toString(minIdleTime));
        }

        optionArgs.add(start.getRedisApi());
        optionArgs.add(end.getRedisApi());
        optionArgs.add(Long.toString(count));

        if (consumer != null) {
            optionArgs.add(consumer);
        }

        return optionArgs.toArray(new String[0]);
    }
}
