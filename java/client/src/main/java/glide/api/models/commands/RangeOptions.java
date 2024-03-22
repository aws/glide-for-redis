/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static glide.utils.ArrayTransformUtils.concatenateArrays;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Arguments for {@link glide.api.commands.SortedSetBaseCommands#zrange} and {@link
 * glide.api.commands.SortedSetBaseCommands#zrangeWithScores}
 *
 * @see <a href="https://redis.io/commands/zrange/">redis.io</a>
 */
public class RangeOptions {

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

    /** Represents a specific numeric score boundary in a sorted set. */
    public static class ScoreBoundary implements ScoreRange {
        /** The score value. */
        private final double bound;

        /** Whether the score value is inclusive. Defaults to true if not set. */
        private final boolean isInclusive;

        public ScoreBoundary(double bound, boolean isInclusive) {
            this.bound = bound;
            this.isInclusive = isInclusive;
        }

        public ScoreBoundary(double bound) {
            this.bound = bound;
            this.isInclusive = true;
        }

        /** Convert the score boundary to the Redis protocol format. */
        public String toArgs() {
            return this.isInclusive ? String.valueOf(this.bound) : "(" + this.bound;
        }
    }

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

    /** Represents a specific lexicographic boundary in a sorted set. */
    public static class LexBoundary implements LexRange {
        /** The lex value. */
        private final String value;

        /** Whether the lex value is inclusive. Defaults to true if not set. */
        private final boolean isInclusive;

        public LexBoundary(@NonNull String value, boolean isInclusive) {
            this.value = value;
            this.isInclusive = isInclusive;
        }

        public LexBoundary(@NonNull String value) {
            this.value = value;
            this.isInclusive = true;
        }

        /** Convert the lex boundary to the Redis protocol format. */
        @Override
        public String toArgs() {
            return this.isInclusive ? "[" + this.value : "(" + this.value;
        }
    }

    /**
     * Represents a limit argument for a range query in a sorted set to be used in <a
     * href="https://redis.io/commands/zrange">ZRANGE</a> command.<br>
     * The optional LIMIT argument can be used to obtain a sub-range from the matching elements
     * (similar to SELECT LIMIT offset, count in SQL).
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

    public interface RangeQuery {
        String getStart();

        String getEnd();

        Limit getLimit();
    }

    /**
     * Represents a range by lexicographical order in a sorted set.<br>
     * The <code>start</code> and <code>stop</code> arguments represent lexicographical boundaries.
     */
    @Getter
    public static class RangeByLex implements RangeQuery {
        /** The start lexicographic boundary. */
        private final String start;

        /** The stop lexicographic boundary. */
        private final String end;

        /**
         * The limit argument for a range query. Defaults to null. See <code>Limit</code> class for more
         * information.
         */
        private final Limit limit;

        public RangeByLex(
                @NonNull RangeOptions.LexRange start,
                @NonNull RangeOptions.LexRange end,
                @NonNull Limit limit) {
            this.start = start.toArgs();
            this.end = end.toArgs();
            this.limit = limit;
        }

        public RangeByLex(@NonNull RangeOptions.LexRange start, @NonNull RangeOptions.LexRange end) {
            this.start = start.toArgs();
            this.end = end.toArgs();
            this.limit = null;
        }
    }

    public interface ScoredRangeQuery extends RangeQuery {}

    /**
     * Represents a range by index (rank) in a sorted set.<br>
     * The <code>start</code> and <code>stop</code> arguments represent zero-based indexes.
     */
    @RequiredArgsConstructor
    @Getter
    public static class RangeByIndex implements ScoredRangeQuery {
        /** The start index of the range. */
        private final String start;

        /** The stop index of the range. */
        private final String end;

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
        /** The start score boundary. */
        private final String start;

        /** The stop score boundary. */
        private final String end;

        /**
         * The limit argument for a range query. Defaults to null. See <code>Limit</code> class for more
         * information.
         */
        private final Limit limit;

        public RangeByScore(
                @NonNull RangeOptions.ScoreRange start,
                @NonNull RangeOptions.ScoreRange end,
                @NonNull Limit limit) {
            this.start = start.toArgs();
            this.end = end.toArgs();
            this.limit = limit;
        }

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
