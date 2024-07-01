/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Optional arguments to sort, sortReadOnly, and sortStore commands
 *
 * @see <a href="https://redis.io/commands/sort/">redis.io</a> and <a
 *     href="https://redis.io/docs/latest/commands/sort_ro/">redis.io</a>
 */
@SuperBuilder
public abstract class SortBaseOptions {
    /**
     * <code>LIMIT</code> subcommand string to include in the <code>SORT</code> and <code>SORT_RO
     * </code> commands.
     */
    public static final String LIMIT_COMMAND_STRING = "LIMIT";

    /**
     * <code>ALPHA</code> subcommand string to include in the <code>SORT</code> and <code>SORT_RO
     * </code> commands.
     */
    public static final String ALPHA_COMMAND_STRING = "ALPHA";

    /** <code>STORE</code> subcommand string to include in the <code>SORT</code> command. */
    public static final String STORE_COMMAND_STRING = "STORE";

    /**
     * Limiting the range of the query by setting offset and result count. See {@link Limit} class for
     * more information.
     */
    private final Limit limit;

    /** Options for sorting order of elements. */
    private final OrderBy orderBy;

    /**
     * When <code>true</code>, sorts elements lexicographically. When <code>false</code> (default),
     * sorts elements numerically. Use this when the list, set, or sorted set contains string values
     * that cannot be converted into double precision floating point numbers.
     */
    private final boolean isAlpha;

    public abstract static class SortBaseOptionsBuilder<
            C extends SortBaseOptions, B extends SortBaseOptionsBuilder<C, B>> {
        public B alpha() {
            this.isAlpha = true;
            return self();
        }
    }

    /**
     * The <code>LIMIT</code> argument is commonly used to specify a subset of results from the
     * matching elements, similar to the <code>LIMIT</code> clause in SQL (e.g., `SELECT LIMIT offset,
     * count`).
     */
    @RequiredArgsConstructor
    public static final class Limit {
        /** The starting position of the range, zero based. */
        private final long offset;

        /**
         * The maximum number of elements to include in the range. A negative count returns all elements
         * from the offset.
         */
        private final long count;
    }

    /**
     * Specifies the order to sort the elements. Can be <code>ASC</code> (ascending) or <code>DESC
     * </code> (descending).
     */
    @RequiredArgsConstructor
    public enum OrderBy {
        ASC,
        DESC
    }

    /**
     * Creates the arguments to be used in <code>SORT</code> and <code>SORT_RO</code> commands.
     *
     * @return a String array that holds the sub commands and their arguments.
     */
    public String[] toArgs() {
        List<String> optionArgs = new ArrayList<>();

        if (limit != null) {
            optionArgs.addAll(
                    List.of(
                            LIMIT_COMMAND_STRING,
                            Long.toString(this.limit.offset),
                            Long.toString(this.limit.count)));
        }

        if (orderBy != null) {
            optionArgs.add(this.orderBy.toString());
        }

        if (isAlpha) {
            optionArgs.add(ALPHA_COMMAND_STRING);
        }

        return optionArgs.toArray(new String[0]);
    }
}
