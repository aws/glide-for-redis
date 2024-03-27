/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static glide.utils.ArrayTransformUtils.concatenateArrays;

import glide.api.commands.SortedSetBaseCommands;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Arguments for {@link SortedSetBaseCommands#zrange} and {@link
 * SortedSetBaseCommands#zrangeWithScores}
 *
 * @see <a href="https://redis.io/commands/zrange/">redis.io</a>
 */
public class RangeOptions {

    /**
     * Basic interface. Please use one of the following implementations:
     *
     * <ul>
     *   <li>{@link InfScoreBound}
     *   <li>{@link ScoreBoundary}
     * </ul>
     */
    public interface ScoreRange {
        String toArgs();
    }

    /** Enumeration representing numeric positive and negative infinity bounds for a sorted set. */
    @RequiredArgsConstructor
    public enum InfScoreBound implements ScoreRange {
        POSITIVE_INFINITY("+inf"),
        NEGATIVE_INFINITY("-inf");

        private final String redisApi;

        public String toArgs() {
            return redisApi;
        }
    }

    public static class ScoreBoundary implements ScoreRange {
        private final double bound;
        private final boolean isInclusive;

        /**
         * Creates a specific numeric score boundary in a sorted set.
         *
         * @param bound The score value.
         * @param isInclusive Whether the score value is inclusive. Defaults to true if not set.
         */
        public ScoreBoundary(double bound, boolean isInclusive) {
            this.bound = bound;
            this.isInclusive = isInclusive;
        }

        /**
         * Creates a specific numeric score boundary in a sorted set.
         *
         * @param bound The score value.
         */
        public ScoreBoundary(double bound) {
            this(bound, true);
        }

        /** Convert the score boundary to the Redis protocol format. */
        public String toArgs() {
            return (isInclusive ? "" : "(") + bound;
        }
    }

    /**
     * Basic interface. Please use one of the following implementations:
     *
     * <ul>
     *   <li>{@link InfLexBound}
     *   <li>{@link LexBoundary}
     * </ul>
     */
    public interface LexRange {
        String toArgs();
    }

    /**
     * Enumeration representing lexicographic positive and negative infinity bounds for sorted set.
     */
    @RequiredArgsConstructor
    public enum InfLexBound implements LexRange {
        POSITIVE_INFINITY("+"),
        NEGATIVE_INFINITY("-");

        private final String redisApi;

        @Override
        public String toArgs() {
            return redisApi;
        }
    }

    public static class LexBoundary implements LexRange {
        private final String value;
        private final boolean isInclusive;

        /**
         * Creates a specific lexicographic boundary in a sorted set.
         *
         * @param value The lex value.
         * @param isInclusive Whether the lex value is inclusive. Defaults to true if not set.
         */
        public LexBoundary(@NonNull String value, boolean isInclusive) {
            this.value = value;
            this.isInclusive = isInclusive;
        }

        /**
         * Creates a specific lexicographic boundary in a sorted set.
         *
         * @param value The lex value.
         */
        public LexBoundary(@NonNull String value) {
            this(value, true);
        }

        /** Convert the lex boundary to the Redis protocol format. */
        @Override
        public String toArgs() {
            return (isInclusive ? "[" : "(") + value;
        }
    }

    /**
     * Represents a limit argument for a range query in a sorted set.<br>
     * The optional <code>LIMIT</code> argument can be used to obtain a sub-range from the matching
     * elements (similar to <code>SELECT LIMIT</code> offset, count in SQL).
     */
    @RequiredArgsConstructor
    @Getter
    public static class Limit {
        /** The offset from the start of the range. */
        private final long offset;

        /**
         * The number of elements to include in the range. A negative count returns all elements from
         * the offset.
         */
        private final long count;
    }

    /**
     * Basic interface. Please use one of the following implementations:
     *
     * <ul>
     *   <li>{@link RangeByIndex}
     *   <li>{@link RangeByScore}
     *   <li>{@link RangeByLex}
     * </ul>
     */
    public interface RangeQuery {
        String getStart();

        String getEnd();

        Limit getLimit();
    }

    @Getter
    public static class RangeByLex implements RangeQuery {
        private final String start;
        private final String end;
        private final Limit limit;

        /**
         * Creates a range by lexicographical order in a sorted set.<br>
         * The <code>start</code> and <code>stop</code> arguments represent lexicographical boundaries.
         *
         * @param start The start lexicographic boundary.
         * @param end The stop lexicographic boundary.
         * @param limit The limit argument for a range query. Defaults to null. See <code>Limit</code>
         *     class for more information.
         */
        public RangeByLex(
                @NonNull RangeOptions.LexRange start,
                @NonNull RangeOptions.LexRange end,
                @NonNull Limit limit) {
            this.start = start.toArgs();
            this.end = end.toArgs();
            this.limit = limit;
        }

        /**
         * Creates a range by lexicographical order in a sorted set.<br>
         * The <code>start</code> and <code>stop</code> arguments represent lexicographical boundaries.
         *
         * @param start The start lexicographic boundary.
         * @param end The stop lexicographic boundary.
         */
        public RangeByLex(@NonNull RangeOptions.LexRange start, @NonNull RangeOptions.LexRange end) {
            this.start = start.toArgs();
            this.end = end.toArgs();
            this.limit = null;
        }
    }

    /**
     * Basic interface. Please use one of the following implementations:
     *
     * <ul>
     *   <li>{@link RangeByIndex}
     *   <li>{@link RangeByScore}
     * </ul>
     */
    public interface ScoredRangeQuery extends RangeQuery {}

    @RequiredArgsConstructor
    @Getter
    public static class RangeByIndex implements ScoredRangeQuery {
        private final String start;
        private final String end;

        /**
         * Creates a range by index (rank) in a sorted set.<br>
         * The <code>start</code> and <code>stop</code> arguments represent zero-based indexes.
         *
         * @param start The start index of the range.
         * @param end The stop index of the range.
         */
        public RangeByIndex(long start, long end) {
            this.start = Long.toString(start);
            this.end = Long.toString(end);
        }

        @Override
        public Limit getLimit() {
            return null;
        }
    }

    /**
     * Represents a range by score in a sorted set.<br>
     * The <code>start</code> and <code>stop</code> arguments represent score boundaries.
     */
    @Getter
    public static class RangeByScore implements ScoredRangeQuery {
        private final String start;
        private final String end;
        private final Limit limit;

        /**
         * Creates a range by score in a sorted set.<br>
         * The <code>start</code> and <code>stop</code> arguments represent score boundaries.
         *
         * @param start The start score boundary.
         * @param end The stop score boundary.
         * @param limit The limit argument for a range query. Defaults to null. See <code>Limit</code>
         *     class for more information.
         */
        public RangeByScore(
                @NonNull RangeOptions.ScoreRange start,
                @NonNull RangeOptions.ScoreRange end,
                @NonNull Limit limit) {
            this.start = start.toArgs();
            this.end = end.toArgs();
            this.limit = limit;
        }

        /**
         * Creates a range by score in a sorted set.<br>
         * The <code>start</code> and <code>stop</code> arguments represent score boundaries.
         *
         * @param start The start score boundary.
         * @param end The stop score boundary.
         */
        public RangeByScore(
                @NonNull RangeOptions.ScoreRange start, @NonNull RangeOptions.ScoreRange end) {
            this.start = start.toArgs();
            this.end = end.toArgs();
            this.limit = null;
        }
    }

    public static String[] createZrangeArgs(
            String key, RangeQuery rangeQuery, boolean reverse, boolean withScores) {
        String[] arguments = new String[] {key, rangeQuery.getStart(), rangeQuery.getEnd()};

        if (rangeQuery instanceof RangeByScore) {
            arguments = concatenateArrays(arguments, new String[] {"BYSCORE"});
        } else if (rangeQuery instanceof RangeByLex) {
            arguments = concatenateArrays(arguments, new String[] {"BYLEX"});
        }

        if (reverse) {
            arguments = concatenateArrays(arguments, new String[] {"REV"});
        }

        if (rangeQuery.getLimit() != null) {
            arguments =
                    concatenateArrays(
                            arguments,
                            new String[] {
                                "LIMIT",
                                Long.toString(rangeQuery.getLimit().getOffset()),
                                Long.toString(rangeQuery.getLimit().getCount())
                            });
        }

        if (withScores) {
            arguments = concatenateArrays(arguments, new String[] {"WITHSCORES"});
        }

        return arguments;
    }
}
