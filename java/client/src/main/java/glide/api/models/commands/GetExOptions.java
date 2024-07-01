/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static glide.api.models.commands.GetExOptions.ExpiryType.MILLISECONDS;
import static glide.api.models.commands.GetExOptions.ExpiryType.PERSIST;
import static glide.api.models.commands.GetExOptions.ExpiryType.SECONDS;
import static glide.api.models.commands.GetExOptions.ExpiryType.UNIX_MILLISECONDS;
import static glide.api.models.commands.GetExOptions.ExpiryType.UNIX_SECONDS;

import glide.api.commands.StringBaseCommands;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Optional arguments to {@link StringBaseCommands#getex(String, GetExOptions)} command.
 *
 * @see <a href="https://redis.io/docs/latest/commands/getex/">redis.io</a>
 */
public class GetExOptions {

    /** Expiry type for the time to live */
    private final ExpiryType type;

    /** The amount of time to live before the key expires. */
    private Long count;

    private GetExOptions(ExpiryType type) {
        this.type = type;
    }

    private GetExOptions(ExpiryType type, Long count) {
        this.type = type;
        this.count = count;
    }

    /**
     * Set the specified expire time, in seconds. Equivalent to <code>EX</code> in the Redis API.
     *
     * @param seconds The time to expire, in seconds.
     * @return The options specifying the given expiry.
     */
    public static GetExOptions Seconds(Long seconds) {
        return new GetExOptions(SECONDS, seconds);
    }

    /**
     * Set the specified expire time, in milliseconds. Equivalent to <code>PX</code> in the Redis API.
     *
     * @param milliseconds The time to expire, in milliseconds.
     * @return The options specifying the given expiry.
     */
    public static GetExOptions Milliseconds(Long milliseconds) {
        return new GetExOptions(MILLISECONDS, milliseconds);
    }

    /**
     * Set the specified Unix time at which the key will expire, in seconds. Equivalent to <code>
     * EXAT</code> in the Redis API.
     *
     * @param unixSeconds The <code>UNIX TIME</code> to expire, in seconds.
     * @return The options specifying the given expiry.
     */
    public static GetExOptions UnixSeconds(Long unixSeconds) {
        return new GetExOptions(UNIX_SECONDS, unixSeconds);
    }

    /**
     * Set the specified Unix time at which the key will expire, in milliseconds. Equivalent to <code>
     * PXAT</code> in the Redis API.
     *
     * @param unixMilliseconds The <code>UNIX TIME</code> to expire, in milliseconds.
     * @return The options specifying the given expiry.
     */
    public static GetExOptions UnixMilliseconds(Long unixMilliseconds) {
        return new GetExOptions(UNIX_MILLISECONDS, unixMilliseconds);
    }

    /** Remove the time to live associated with the key. */
    public static GetExOptions Persist() {
        return new GetExOptions(PERSIST);
    }

    /** Types of value expiration configuration. */
    @RequiredArgsConstructor
    protected enum ExpiryType {
        SECONDS("EX"),
        MILLISECONDS("PX"),
        UNIX_SECONDS("EXAT"),
        UNIX_MILLISECONDS("PXAT"),
        PERSIST("PERSIST");

        private final String redisApi;
    }

    /**
     * Converts GetExOptions into a String[] to pass to the <code>GETEX</code> command.
     *
     * @return String[]
     */
    public String[] toArgs() {
        List<String> optionArgs = new ArrayList<>();

        optionArgs.add(type.redisApi);
        if (count != null) {
            optionArgs.add(String.valueOf(count));
        }
        System.out.println(optionArgs);
        return optionArgs.toArray(new String[0]);
    }
}
