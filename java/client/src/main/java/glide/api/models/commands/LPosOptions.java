/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.ListBaseCommands;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;

/**
 * Optional arguments to {@link ListBaseCommands#lpos(String, String, LPosOptions)} and {@link
 * ListBaseCommands#lposCount(String, String, long)} command.
 *
 * @see <a href="https://redis.io/commands/lpos/">redis.io</a>
 */
@Builder
public final class LPosOptions {

    /** The rank of the match to return. */
    private Long rank;

    /** The maximum number of comparisons to make between the element and the items in the list. */
    private Long maxLength;

    /** Redis API keyword used to extract specific number of matching indices from a list. */
    public static final String COUNT_REDIS_API = "COUNT";

    /** Redis API keyword use to determine the rank of the match to return. */
    public static final String RANK_REDIS_API = "RANK";

    /** Redis API keyword used to determine the maximum number of list items to compare. */
    public static final String MAXLEN_REDIS_API = "MAXLEN";

    /**
     * Converts LPosOptions into a String[].
     *
     * @return String[]
     */
    public String[] toArgs() {
        List<String> optionArgs = new ArrayList<>();
        if (rank != null) {
            optionArgs.add(RANK_REDIS_API);
            optionArgs.add(String.valueOf(rank));
        }

        if (maxLength != null) {
            optionArgs.add(MAXLEN_REDIS_API);
            optionArgs.add(String.valueOf(maxLength));
        }

        return optionArgs.toArray(new String[0]);
    }
}
