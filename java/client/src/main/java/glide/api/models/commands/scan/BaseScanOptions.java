/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.SuperBuilder;

/**
 * This base class represents the common set of optional arguments for the SCAN family of commands.
 * Concrete implementations of this class are tied to specific SCAN commands (SCAN, HSCAN, SSCAN,
 * and ZSCAN).
 */
@SuperBuilder
public abstract class BaseScanOptions {
    /** <code>MATCH</code> option string to include in the <code>SCAN</code> commands. */
    public static final String MATCH_OPTION_STRING = "MATCH";

    /** <code>COUNT</code> option string to include in the <code>SCAN</code> commands. */
    public static final String COUNT_OPTION_STRING = "COUNT";

    /**
     * The match filter is applied to the result of the command and will only include strings that
     * match the pattern specified. If the set, hash, or list is large enough for scan commands to
     * return only a subset of the set, hash, or list, then there could be a case where the result is
     * empty although there are items that match the pattern specified. This is due to the default
     * <code>COUNT</code> being <code>10</code> which indicates that it will only fetch and match
     * <code>10</code> items from the list.
     */
    private final String matchPattern;

    /**
     * <code>COUNT</code> is a just a hint for the command for how many elements to fetch from the
     * set, hash, or list. <code>COUNT</code> could be ignored until the set, hash, or list is large
     * enough for the <code>SCAN</code> commands to represent the results as compact single-allocation
     * packed encoding.
     */
    private final Long count;

    /**
     * Creates the arguments to be used in <code>SCAN</code> commands.
     *
     * @return a String array that holds the options and their arguments.
     */
    public String[] toArgs() {
        List<String> optionArgs = new ArrayList<>();

        if (matchPattern != null) {
            optionArgs.add(MATCH_OPTION_STRING);
            optionArgs.add(matchPattern);
        }

        if (count != null) {
            optionArgs.add(COUNT_OPTION_STRING);
            optionArgs.add(count.toString());
        }

        return optionArgs.toArray(new String[0]);
    }
}
