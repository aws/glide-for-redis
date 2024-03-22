/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.commands.RangeOptions;
import glide.api.models.commands.RangeOptions.RangeQuery;
import glide.api.models.commands.RangeOptions.ScoredRangeQuery;
import glide.api.models.commands.ZaddOptions;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Sorted Set Commands" group for standalone clients and
 * cluster clients.
 *
 * @see <a href="https://redis.io/commands/?group=sorted-set">Sorted Set Commands</a>
 */
public interface SortedSetBaseCommands {

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A <code>Map</code> of members to their corresponding scores.
     * @param options The Zadd options.
     * @param changed Modify the return value from the number of new elements added, to the total
     *     number of elements changed.
     * @return The number of elements added to the sorted set. <br>
     *     If <code>changed</code> is set, returns the number of elements updated in the sorted set.
     * @example
     *     <pre>{@code
     * Long num = client.zadd("mySortedSet", Map.of("member1", 10.5, "member2", 8.2), ZaddOptions.builder().build(), false).get();
     * assert num == 2L; // Indicates that two elements have been added or updated in the sorted set "mySortedSet".
     *
     * Long num = client.zadd("existingSortedSet", Map.of("member1", 15.0, "member2", 5.5), ZaddOptions.builder().conditionalChange(ZaddOptions.ConditionalChange.ONLY_IF_EXISTS).build(), false).get();
     * assert num == 2L; // Updates the scores of two existing members in the sorted set "existingSortedSet".
     * }</pre>
     */
    CompletableFuture<Long> zadd(
            String key, Map<String, Double> membersScoresMap, ZaddOptions options, boolean changed);

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A <code>Map</code> of members to their corresponding scores.
     * @param options The Zadd options.
     * @return The number of elements added to the sorted set.
     * @example
     *     <pre>{@code
     * Long num = client.zadd("mySortedSet", Map.of("member1", 10.5, "member2", 8.2), ZaddOptions.builder().build()).get();
     * assert num == 2L; // Indicates that two elements have been added to the sorted set "mySortedSet".
     *
     * Long num = client.zadd("existingSortedSet", Map.of("member1", 15.0, "member2", 5.5), ZaddOptions.builder().conditionalChange(ZaddOptions.ConditionalChange.ONLY_IF_EXISTS).build()).get();
     * assert num == 0L; // No new members were added to the sorted set "existingSortedSet".
     * }</pre>
     */
    CompletableFuture<Long> zadd(
            String key, Map<String, Double> membersScoresMap, ZaddOptions options);

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A <code>Map</code> of members to their corresponding scores.
     * @param changed Modify the return value from the number of new elements added, to the total
     *     number of elements changed.
     * @return The number of elements added to the sorted set. <br>
     *     If <code>changed</code> is set, returns the number of elements updated in the sorted set.
     *     <br>
     * @example
     *     <pre>{@code
     * Long num = client.zadd("mySortedSet", Map.of("member1", 10.5, "member2", 8.2), true).get();
     * assert num == 2L; // Indicates that two elements have been added or updated in the sorted set "mySortedSet".
     * }</pre>
     */
    CompletableFuture<Long> zadd(String key, Map<String, Double> membersScoresMap, boolean changed);

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A <code>Map</code> of members to their corresponding scores.
     * @return The number of elements added to the sorted set.
     * @example
     *     <pre>{@code
     * Long num = client.zadd("mySortedSet", Map.of("member1", 10.5, "member2", 8.2)).get();
     * assert num == 2L; // Indicates that two elements have been added to the sorted set "mySortedSet".
     * }</pre>
     */
    CompletableFuture<Long> zadd(String key, Map<String, Double> membersScoresMap);

    /**
     * Increments the score of member in the sorted set stored at <code>key</code> by <code>increment
     * </code>.<br>
     * If <code>member</code> does not exist in the sorted set, it is added with <code>
     * increment</code> as its score (as if its previous score was 0.0).<br>
     * If <code>key</code> does not exist, a new sorted set with the specified member as its sole
     * member is created.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member A member in the sorted set to increment.
     * @param increment The score to increment the member.
     * @param options The Zadd options.
     * @return The score of the member.<br>
     *     If there was a conflict with the options, the operation aborts and <code>null</code> is
     *     returned.<br>
     * @example
     *     <pre>{@code
     * Double num = client.zaddIncr("mySortedSet", member, 5.0, ZaddOptions.builder().build()).get();
     * assert num == 5.0;
     *
     * Double num = client.zaddIncr("existingSortedSet", member, 3.0, ZaddOptions.builder().updateOptions(ZaddOptions.UpdateOptions.SCORE_LESS_THAN_CURRENT).build()).get();
     * assert num == null;
     * }</pre>
     */
    CompletableFuture<Double> zaddIncr(
            String key, String member, double increment, ZaddOptions options);

    /**
     * Increments the score of member in the sorted set stored at <code>key</code> by <code>increment
     * </code>.<br>
     * If <code>member</code> does not exist in the sorted set, it is added with <code>
     * increment</code> as its score (as if its previous score was 0.0).<br>
     * If <code>key</code> does not exist, a new sorted set with the specified member as its sole
     * member is created.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member A member in the sorted set to increment.
     * @param increment The score to increment the member.
     * @return The score of the member.
     * @example
     *     <pre>{@code
     * Double num = client.zaddIncr("mySortedSet", member, 5.0).get();
     * assert num == 5.0;
     * }</pre>
     */
    CompletableFuture<Double> zaddIncr(String key, String member, double increment);

    /**
     * Removes the specified members from the sorted set stored at <code>key</code>.<br>
     * Specified members that are not a member of this set are ignored.
     *
     * @see <a href="https://redis.io/commands/zrem/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param members An array of members to remove from the sorted set.
     * @return The number of members that were removed from the sorted set, not including non-existing
     *     members.<br>
     *     If <code>key</code> does not exist, it is treated as an empty sorted set, and this command
     *     returns <code>0</code>.
     * @example
     *     <pre>{@code
     * Long num1 = client.zrem("mySortedSet", new String[] {"member1", "member2"}).get();
     * assert num1 == 2L; // Indicates that two members have been removed from the sorted set "mySortedSet".
     *
     * Long num2 = client.zrem("nonExistingSortedSet", new String[] {"member1", "member2"}).get();
     * assert num2 == 0L; // Indicates that no members were removed as the sorted set "nonExistingSortedSet" does not exist.
     * }</pre>
     */
    CompletableFuture<Long> zrem(String key, String[] members);

    /**
     * Returns the cardinality (number of elements) of the sorted set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zcard/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @return The number of elements in the sorted set.<br>
     *     If <code>key</code> does not exist, it is treated as an empty sorted set, and this command
     *     return <code>0</code>.
     * @example
     *     <pre>{@code
     * Long num1 = client.zcard("mySortedSet").get();
     * assert num1 == 3L; // Indicates that there are 3 elements in the sorted set "mySortedSet".
     *
     * Long num2 = client.zcard("nonExistingSortedSet").get();
     * assert num2 == 0L;
     * }</pre>
     */
    CompletableFuture<Long> zcard(String key);

    /**
     * Returns the specified range of elements in the sorted set stored at <code>key</code>.<br>
     * ZRANGE can perform different types of range queries: by index (rank), by the score, or by
     * lexicographical order.<br>
     * To get the elements with their scores, see {@link #zrangeWithScores}.
     *
     * @see <a href="https://redis.io/commands/zrange/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     - For range queries by index (rank), use {@link RangeOptions.RangeByIndex}.<br>
     *     - For range queries by lexicographical order, use {@link RangeOptions.RangeByLex}.<br>
     *     - For range queries by score, use {@link RangeOptions.RangeByScore}.
     * @param reverse If true, reverses the sorted set, with index 0 as the element with the highest
     *     score.
     * @return An array of elements within the specified range. If <code>key</code> does not exist, it
     *     is treated as an empty sorted set, and the command returns an empty array.
     * @example
     *     <pre>{@code
     * String[] payload1 = client.zrange("mySortedSet", new RangeByIndex(0, -1), true).get(); // RangeByIndex(0, -1) specifies retrieval of all elements from the start to the end of the sorted set.
     * assert payload1.equals(new String[] {'member3', 'member2', 'member1'}); // Returns all members in descending order.
     *
     * String[] payload2 = client.zrange("mySortedSet", new RangeByScore(InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(3)), false).get();
     * assert payload2.equals(new String[] {'member2', 'member3'}); // Returns members with scores within the range of negative infinity to 3, in ascending order.
     * }</pre>
     */
    CompletableFuture<String[]> zrange(String key, RangeQuery rangeQuery, boolean reverse);

    /**
     * Returns the specified range of elements in the sorted set stored at <code>key</code>.<br>
     * ZRANGE can perform different types of range queries: by index (rank), by the score, or by
     * lexicographical order.<br>
     * To get the elements with their scores, see {@link #zrangeWithScores}.
     *
     * @see <a href="https://redis.io/commands/zrange/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     - For range queries by index (rank), use {@link RangeOptions.RangeByIndex}.<br>
     *     - For range queries by lexicographical order, use {@link RangeOptions.RangeByLex}.<br>
     *     - For range queries by score, use {@link RangeOptions.RangeByScore}.
     * @return An of array elements within the specified range. If <code>key</code> does not exist, it
     *     is treated as an empty sorted set, and the command returns an empty array.
     * @example
     *     <pre>{@code
     * String[] payload1 = client.zrange("mySortedSet", new RangeByIndex(0, -1)).get();
     * assert payload1.equals(new String[] {'member1', 'member2', 'member3'}); // Returns all members in ascending order.
     *
     * String[] payload2 = client.zrange("mySortedSet", new RangeByScore(InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(3))).get();
     * assert payload2.equals(new String[] {'member2', 'member3'}); // Returns members with scores within the range of negative infinity to 3, in ascending order.
     * }</pre>
     */
    CompletableFuture<String[]> zrange(String key, RangeQuery rangeQuery);

    /**
     * Returns the specified range of elements with their scores in the sorted set stored at <code>key
     * </code>. Similar to ZRANGE but with a WITHSCORE flag.
     *
     * @see <a href="https://redis.io/commands/zrange/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     - For range queries by index (rank), use {@link RangeOptions.RangeByIndex}.<br>
     *     - For range queries by score, use {@link RangeOptions.RangeByScore}.
     * @param reverse If true, reverses the sorted set, with index 0 as the element with the highest
     *     score.
     * @return A <code>Map</code> of elements and their scores within the specified range. If <code>
     *     key</code> does not exist, it is treated as an empty sorted set, and the command returns an
     *     empty <code>Map</code>.
     * @example
     *     <pre>{@code
     * Map<String, Double> payload1 = client.zrangeWithScores("mySortedSet", new RangeByScore(new ScoreBoundary(10), new ScoreBoundary(20)), true).get();
     * assert payload1.equals(Map.of('member2', 15.2, 'member1', 10.5)); // Returns members with scores between 10 and 20 with their scores.
     *
     * Map<String, Double> payload2 = client.zrangeWithScores("mySortedSet", new RangeByScore(InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(3)), false).get();
     * assert payload2.equals(Map.of('member4', -2.0, 'member7', 1.5)); // Returns members with with scores within the range of negative infinity to 3, with their scores.
     * }</pre>
     */
    CompletableFuture<Map<String, Double>> zrangeWithScores(
            String key, ScoredRangeQuery rangeQuery, boolean reverse);

    /**
     * Returns the specified range of elements with their scores in the sorted set stored at <code>key
     * </code>. Similar to ZRANGE but with a WITHSCORE flag.
     *
     * @see <a href="https://redis.io/commands/zrange/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     - For range queries by index (rank), use {@link RangeOptions.RangeByIndex}.<br>
     *     - For range queries by score, use {@link RangeOptions.RangeByScore}.
     * @return A <code><ap</code> of elements and their scores within the specified range. If <code>
     *     key</code> does not exist, it is treated as an empty sorted set, and the command returns an
     *     empty <code>Map</code>.
     * @example
     *     <pre>{@code
     * Map<String, Double> payload1 = client.zrangeWithScores("mySortedSet", new RangeByScore(new ScoreBoundary(10), new ScoreBoundary(20))).get();
     * assert payload1.equals(Map.of('member1', 10.5, 'member2', 15.2)); // Returns members with scores between 10 and 20 with their scores.
     *
     * Map<String, Double> payload2 = client.zrangeWithScores("mySortedSet", new RangeByScore(InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(3))).get();
     * assert payload2.equals(Map.of('member4', -2.0, 'member7', 1.5)); // Returns members with with scores within the range of negative infinity to 3, with their scores.
     * }</pre>
     */
    CompletableFuture<Map<String, Double>> zrangeWithScores(String key, ScoredRangeQuery rangeQuery);
}
