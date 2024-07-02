/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.SortedSetBaseCommands;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

/**
 * Optional arguments to {@link SortedSetBaseCommands#zadd(String, Map, ZAddOptions, boolean)},
 * {@link SortedSetBaseCommands#zadd(String, Map, ZAddOptions)} and {@link
 * SortedSetBaseCommands#zaddIncr(String, String, double, ZAddOptions)}
 *
 * @see <a href="https://valkey.io/commands/zadd/">redis.io</a>
 */
@Builder
public final class ZAddOptions {
    /**
     * Defines conditions for updating or adding elements with {@link SortedSetBaseCommands#zadd}
     * command.
     */
    private final ConditionalChange conditionalChange;

    /**
     * Specifies conditions for updating scores with zadd {@link SortedSetBaseCommands#zadd} command.
     */
    private final UpdateOptions updateOptions;

    @RequiredArgsConstructor
    public enum ConditionalChange {
        /**
         * Only update elements that already exist. Don't add new elements. Equivalent to <code>XX
         * </code> in the Redis API.
         */
        ONLY_IF_EXISTS("XX"),
        /**
         * Only add new elements. Don't update already existing elements. Equivalent to <code>NX</code>
         * in the Redis API.
         */
        ONLY_IF_DOES_NOT_EXIST("NX");

        private final String redisApi;
    }

    @RequiredArgsConstructor
    public enum UpdateOptions {
        /**
         * Only update existing elements if the new score is less than the current score. Equivalent to
         * <code>LT</code> in the Redis API.
         */
        SCORE_LESS_THAN_CURRENT("LT"),
        /**
         * Only update existing elements if the new score is greater than the current score. Equivalent
         * to <code>GT</code> in the Redis API.
         */
        SCORE_GREATER_THAN_CURRENT("GT");

        private final String redisApi;
    }

    /**
     * Converts ZaddOptions into a String[].
     *
     * @return String[]
     */
    public String[] toArgs() {
        if (conditionalChange == ConditionalChange.ONLY_IF_DOES_NOT_EXIST && updateOptions != null) {
            throw new IllegalArgumentException(
                    "The GT, LT, and NX options are mutually exclusive. Cannot choose both "
                            + updateOptions.redisApi
                            + " and NX.");
        }

        List<String> optionArgs = new ArrayList<>();

        if (conditionalChange != null) {
            optionArgs.add(conditionalChange.redisApi);
        }

        if (updateOptions != null) {
            optionArgs.add(updateOptions.redisApi);
        }

        return optionArgs.toArray(new String[0]);
    }
}
