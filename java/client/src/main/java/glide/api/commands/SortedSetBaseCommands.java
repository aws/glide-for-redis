/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.GlideString;
import glide.api.models.commands.RangeOptions.InfLexBound;
import glide.api.models.commands.RangeOptions.InfScoreBound;
import glide.api.models.commands.RangeOptions.LexBoundary;
import glide.api.models.commands.RangeOptions.LexRange;
import glide.api.models.commands.RangeOptions.RangeByIndex;
import glide.api.models.commands.RangeOptions.RangeByLex;
import glide.api.models.commands.RangeOptions.RangeByScore;
import glide.api.models.commands.RangeOptions.RangeQuery;
import glide.api.models.commands.RangeOptions.ScoreBoundary;
import glide.api.models.commands.RangeOptions.ScoreRange;
import glide.api.models.commands.RangeOptions.ScoredRangeQuery;
import glide.api.models.commands.ScoreFilter;
import glide.api.models.commands.WeightAggregateOptions.Aggregate;
import glide.api.models.commands.WeightAggregateOptions.KeyArray;
import glide.api.models.commands.WeightAggregateOptions.KeyArrayBinary;
import glide.api.models.commands.WeightAggregateOptions.KeysOrWeightedKeys;
import glide.api.models.commands.WeightAggregateOptions.KeysOrWeightedKeysBinary;
import glide.api.models.commands.WeightAggregateOptions.WeightedKeys;
import glide.api.models.commands.WeightAggregateOptions.WeightedKeysBinary;
import glide.api.models.commands.ZAddOptions;
import glide.api.models.commands.scan.ZScanOptions;
import glide.api.models.commands.scan.ZScanOptionsBinary;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Sorted Set Commands" group for standalone and cluster
 * clients.
 *
 * @see <a href="https://redis.io/commands/?group=sorted-set">Sorted Set Commands</a>
 */
public interface SortedSetBaseCommands {
    /** Redis API keyword used to query sorted set members with their scores. */
    String WITH_SCORES_REDIS_API = "WITHSCORES";

    /** Redis API keyword used to query a sorted set member with its score. */
    String WITH_SCORE_REDIS_API = "WITHSCORE";

    /** Redis API keyword used to extract specific count of members from a sorted set. */
    String COUNT_REDIS_API = "COUNT";

    /** Redis API keyword used to limit calculation of intersection of sorted sets. */
    String LIMIT_REDIS_API = "LIMIT";

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A <code>Map</code> of members to their corresponding scores.
     * @param options The ZAdd options.
     * @param changed Modify the return value from the number of new elements added, to the total
     *     number of elements changed.
     * @return The number of elements added to the sorted set.<br>
     *     If <code>changed</code> is set, returns the number of elements updated in the sorted set.
     * @example
     *     <pre>{@code
     * ZaddOptions options = ZaddOptions.builder().conditionalChange(ONLY_IF_DOES_NOT_EXIST).build();
     * Long num = client.zadd("mySortedSet", Map.of("member1", 10.5, "member2", 8.2), options, false).get();
     * assert num == 2L; // Indicates that two elements have been added or updated in the sorted set "mySortedSet".
     *
     * options = ZaddOptions.builder().conditionalChange(ONLY_IF_EXISTS).build();
     * Long num = client.zadd("existingSortedSet", Map.of("member1", 15.0, "member2", 5.5), options, false).get();
     * assert num == 2L; // Updates the scores of two existing members in the sorted set "existingSortedSet".
     * }</pre>
     */
    CompletableFuture<Long> zadd(
            String key, Map<String, Double> membersScoresMap, ZAddOptions options, boolean changed);

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A <code>Map</code> of members to their corresponding scores.
     * @param options The ZAdd options.
     * @return The number of elements added to the sorted set.
     * @example
     *     <pre>{@code
     * ZaddOptions options = ZaddOptions.builder().conditionalChange(ONLY_IF_DOES_NOT_EXIST).build();
     * Long num = client.zadd("mySortedSet", Map.of("member1", 10.5, "member2", 8.2), options).get();
     * assert num == 2L; // Indicates that two elements have been added to the sorted set "mySortedSet".
     *
     * options = ZaddOptions.builder().conditionalChange(ONLY_IF_EXISTS).build();
     * Long num = client.zadd("existingSortedSet", Map.of("member1", 15.0, "member2", 5.5), options).get();
     * assert num == 0L; // No new members were added to the sorted set "existingSortedSet".
     * }</pre>
     */
    CompletableFuture<Long> zadd(
            String key, Map<String, Double> membersScoresMap, ZAddOptions options);

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A <code>Map</code> of members to their corresponding scores.
     * @param changed Modify the return value from the number of new elements added, to the total
     *     number of elements changed.
     * @return The number of elements added to the sorted set.<br>
     *     If <code>changed</code> is set, returns the number of elements updated in the sorted set.
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
     * increment</code> as its score (as if its previous score was <code>0.0</code>).<br>
     * If <code>key</code> does not exist, a new sorted set with the specified member as its sole
     * member is created.<br>
     * <code>zaddIncr</code> with empty option acts as {@link #zincrby(String, double, String)}.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member A member in the sorted set to increment.
     * @param increment The score to increment the member.
     * @param options The ZAdd options.
     * @return The score of the member.<br>
     *     If there was a conflict with the options, the operation aborts and <code>null</code> is
     *     returned.
     * @example
     *     <pre>{@code
     * ZAddOptions options = ZaddOptions.builder().conditionalChange(ONLY_IF_DOES_NOT_EXIST).build();
     * Double num = client.zaddIncr("mySortedSet", member, 5.0, options).get();
     * assert num == 5.0;
     *
     * options = ZAddOptions.builder().updateOptions(SCORE_LESS_THAN_CURRENT).build();
     * Double num = client.zaddIncr("existingSortedSet", member, 3.0, options).get();
     * assert num == null;
     * }</pre>
     */
    CompletableFuture<Double> zaddIncr(
            String key, String member, double increment, ZAddOptions options);

    /**
     * Increments the score of member in the sorted set stored at <code>key</code> by <code>increment
     * </code>.<br>
     * If <code>member</code> does not exist in the sorted set, it is added with <code>
     * increment</code> as its score (as if its previous score was <code>0.0</code>).<br>
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
     * Long num1 = client.zrem(gs("mySortedSet"), new GlideString[] {gs("member1"), gs("member2")}).get();
     * assert num1 == 2L; // Indicates that two members have been removed from the sorted set "mySortedSet".
     *
     * Long num2 = client.zrem(gs("nonExistingSortedSet"), new GlideString[] {gs("member1"), gs("member2")}).get();
     * assert num2 == 0L; // Indicates that no members were removed as the sorted set "nonExistingSortedSet" does not exist.
     * }</pre>
     */
    CompletableFuture<Long> zrem(GlideString key, GlideString[] members);

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
     * Returns the cardinality (number of elements) of the sorted set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zcard/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @return The number of elements in the sorted set.<br>
     *     If <code>key</code> does not exist, it is treated as an empty sorted set, and this command
     *     return <code>0</code>.
     * @example
     *     <pre>{@code
     * Long num1 = client.zcard(gs("mySortedSet")).get();
     * assert num1 == 3L; // Indicates that there are 3 elements in the sorted set "mySortedSet".
     *
     * Long num2 = client.zcard((gs("nonExistingSortedSet")).get();
     * assert num2 == 0L;
     * }</pre>
     */
    CompletableFuture<Long> zcard(GlideString key);

    /**
     * Removes and returns up to <code>count</code> members with the lowest scores from the sorted set
     * stored at the specified <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zpopmin/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param count Specifies the quantity of members to pop.<br>
     *     If <code>count</code> is higher than the sorted set's cardinality, returns all members and
     *     their scores, ordered from lowest to highest.
     * @return A map of the removed members and their scores, ordered from the one with the lowest
     *     score to the one with the highest.<br>
     *     If <code>key</code> doesn't exist, it will be treated as an empty sorted set and the
     *     command returns an empty <code>Map</code>.
     * @example
     *     <pre>{@code
     * Map<String, Double> payload = client.zpopmax("mySortedSet", 2).get();
     * assert payload.equals(Map.of('member3', 7.5 , 'member2', 8.0)); // Indicates that 'member3' with a score of 7.5 and 'member2' with a score of 8.0 have been removed from the sorted set.
     * }</pre>
     */
    CompletableFuture<Map<String, Double>> zpopmin(String key, long count);

    /**
     * Removes and returns the member with the lowest score from the sorted set stored at the
     * specified <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zpopmin/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @return A map containing the removed member and its corresponding score.<br>
     *     If <code>key</code> doesn't exist, it will be treated as an empty sorted set and the
     *     command returns an empty <code>Map</code>.
     * @example
     *     <pre>{@code
     * Map<String, Double> payload = client.zpopmin("mySortedSet").get();
     * assert payload.equals(Map.of('member1', 5.0)); // Indicates that 'member1' with a score of 5.0 has been removed from the sorted set.
     * }</pre>
     */
    CompletableFuture<Map<String, Double>> zpopmin(String key);

    /**
     * Blocks the connection until it removes and returns a member with the lowest score from the
     * first non-empty sorted set, with the given <code>keys</code> being checked in the order they
     * are provided.<br>
     * <code>BZPOPMIN</code> is the blocking variant of {@link #zpopmin(String)}.<br>
     *
     * @apiNote
     *     <ul>
     *       <li>When in cluster mode, all <code>keys</code> must map to the same hash slot.
     *       <li><code>BZPOPMIN</code> is a client blocking command, see <a
     *           href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *           Commands</a> for more details and best practices.
     *     </ul>
     *
     * @see <a href="https://redis.io/commands/bzpopmin/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @return An <code>array</code> containing the key where the member was popped out, the member
     *     itself, and the member score.<br>
     *     If no member could be popped and the <code>timeout</code> expired, returns <code>null
     *     </code>.
     * @example
     *     <pre>{@code
     * Object[] data = client.bzpopmin(new String[] {"zset1", "zset2"}, 0.5).get();
     * System.out.printf("Popped '%s' with score %d from sorted set '%s'%n", data[1], data[2], data[0]);
     * }</pre>
     */
    CompletableFuture<Object[]> bzpopmin(String[] keys, double timeout);

    /**
     * Removes and returns up to <code>count</code> members with the highest scores from the sorted
     * set stored at the specified <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zpopmax/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param count Specifies the quantity of members to pop.<br>
     *     If <code>count</code> is higher than the sorted set's cardinality, returns all members and
     *     their scores, ordered from highest to lowest.
     * @return A map of the removed members and their scores, ordered from the one with the highest
     *     score to the one with the lowest.<br>
     *     If <code>key</code> doesn't exist, it will be treated as an empty sorted set and the
     *     command returns an empty <code>Map</code>.
     * @example
     *     <pre>{@code
     * Map<String, Double> payload = client.zpopmax("mySortedSet", 2).get();
     * assert payload.equals(Map.of('member2', 8.0, 'member3', 7.5)); // Indicates that 'member2' with a score of 8.0 and 'member3' with a score of 7.5 have been removed from the sorted set.
     * }</pre>
     */
    CompletableFuture<Map<String, Double>> zpopmax(String key, long count);

    /**
     * Removes and returns the member with the highest score from the sorted set stored at the
     * specified <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zpopmax/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @return A map containing the removed member and its corresponding score.<br>
     *     If <code>key</code> doesn't exist, it will be treated as an empty sorted set and the
     *     command returns an empty <code>Map</code>.
     * @example
     *     <pre>{@code
     * Map<String, Double> payload = client.zpopmax("mySortedSet").get();
     * assert payload.equals(Map.of('member1', 10.0)); // Indicates that 'member1' with a score of 10.0 has been removed from the sorted set.
     * }</pre>
     */
    CompletableFuture<Map<String, Double>> zpopmax(String key);

    /**
     * Blocks the connection until it removes and returns a member with the highest score from the
     * first non-empty sorted set, with the given <code>keys</code> being checked in the order they
     * are provided.<br>
     * <code>BZPOPMAX</code> is the blocking variant of {@link #zpopmax(String)}.<br>
     *
     * @apiNote
     *     <ul>
     *       <li>When in cluster mode, all <code>keys</code> must map to the same hash slot.
     *       <li><code>BZPOPMAX</code> is a client blocking command, see <a
     *           href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *           Commands</a> for more details and best practices.
     *     </ul>
     *
     * @see <a href="https://redis.io/commands/bzpopmax/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @return An <code>array</code> containing the key where the member was popped out, the member
     *     itself, and the member score.<br>
     *     If no member could be popped and the <code>timeout</code> expired, returns <code>null
     *     </code>.
     * @example
     *     <pre>{@code
     * Object[] data = client.bzpopmax(new String[] {"zset1", "zset2"}, 0.5).get();
     * System.out.printf("Popped '%s' with score %d from sorted set '%s'%n", data[1], data[2], data[0]);
     * }</pre>
     */
    CompletableFuture<Object[]> bzpopmax(String[] keys, double timeout);

    /**
     * Returns the score of <code>member</code> in the sorted set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zscore/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member The member whose score is to be retrieved.
     * @return The score of the member.<br>
     *     If <code>member</code> does not exist in the sorted set, <code>null</code> is returned.<br>
     *     If <code>key</code> does not exist, <code>null</code> is returned.
     * @example
     *     <pre>{@code
     * Double num1 = client.zscore("mySortedSet", "member").get();
     * assert num1 == 10.5; // Indicates that the score of "member" in the sorted set "mySortedSet" is 10.5.
     *
     * Double num2 = client.zscore("mySortedSet", "nonExistingMember").get();
     * assert num2 == null;
     * }</pre>
     */
    CompletableFuture<Double> zscore(String key, String member);

    /**
     * Returns the score of <code>member</code> in the sorted set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zscore/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member The member whose score is to be retrieved.
     * @return The score of the member.<br>
     *     If <code>member</code> does not exist in the sorted set, <code>null</code> is returned.<br>
     *     If <code>key</code> does not exist, <code>null</code> is returned.
     * @example
     *     <pre>{@code
     * Double num1 = client.zscore(gs("mySortedSet"), gs("member")).get();
     * assert num1 == 10.5; // Indicates that the score of "member" in the sorted set "mySortedSet" is 10.5.
     *
     * Double num2 = client.zscore(gs("mySortedSet"), gs("nonExistingMember")).get();
     * assert num2 == null;
     * }</pre>
     */
    CompletableFuture<Double> zscore(GlideString key, GlideString member);

    /**
     * Returns the specified range of elements in the sorted set stored at <code>key</code>.<br>
     * <code>ZRANGE</code> can perform different types of range queries: by index (rank), by the
     * score, or by lexicographical order.<br>
     * To get the elements with their scores, see {@link #zrangeWithScores}.
     *
     * @see <a href="https://redis.io/commands/zrange/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by lexicographical order, use {@link RangeByLex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @param reverse If true, reverses the sorted set, with index 0 as the element with the highest
     *     score.
     * @return An array of elements within the specified range. If <code>key</code> does not exist, it
     *     is treated as an empty sorted set, and the command returns an empty array.
     * @example
     *     <pre>{@code
     * RangeByScore query1 = new RangeByScore(new ScoreBoundary(10), new ScoreBoundary(20));
     * String[] payload1 = client.zrange("mySortedSet", query1, true).get(); // Returns members with scores between 10 and 20.
     * assert payload1.equals(new String[] {'member3', 'member2', 'member1'}); // Returns all members in descending order.
     *
     * RangeByScore query2 = new RangeByScore(InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(3));
     * String[] payload2 = client.zrange("mySortedSet", query2, false).get();
     * assert payload2.equals(new String[] {'member2', 'member3'}); // Returns members with scores within the range of negative infinity to 3, in ascending order.
     * }</pre>
     */
    CompletableFuture<String[]> zrange(String key, RangeQuery rangeQuery, boolean reverse);

    /**
     * Returns the specified range of elements in the sorted set stored at <code>key</code>.<br>
     * <code>ZRANGE</code> can perform different types of range queries: by index (rank), by the
     * score, or by lexicographical order.<br>
     * To get the elements with their scores, see {@link #zrangeWithScores}.
     *
     * @see <a href="https://redis.io/commands/zrange/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by lexicographical order, use {@link RangeByLex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @return An of array elements within the specified range. If <code>key</code> does not exist, it
     *     is treated as an empty sorted set, and the command returns an empty array.
     * @example
     *     <pre>{@code
     * RangeByIndex query1 = new RangeByIndex(0, -1);
     * String[] payload1 = client.zrange("mySortedSet",query1).get();
     * assert payload1.equals(new String[] {'member1', 'member2', 'member3'}); // Returns all members in ascending order.
     *
     * RangeByScore query2 = new RangeByScore(InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(3));
     * String[] payload2 = client.zrange("mySortedSet", query2).get();
     * assert payload2.equals(new String[] {'member2', 'member3'}); // Returns members with scores within the range of negative infinity to 3, in ascending order.
     * }</pre>
     */
    CompletableFuture<String[]> zrange(String key, RangeQuery rangeQuery);

    /**
     * Returns the specified range of elements with their scores in the sorted set stored at <code>key
     * </code>. Similar to {@link #zrange} but with a <code>WITHSCORE</code> flag.
     *
     * @see <a href="https://redis.io/commands/zrange/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @param reverse If true, reverses the sorted set, with index 0 as the element with the highest
     *     score.
     * @return A <code>Map</code> of elements and their scores within the specified range. If <code>
     *     key</code> does not exist, it is treated as an empty sorted set, and the command returns an
     *     empty <code>Map</code>.
     * @example
     *     <pre>{@code
     * RangeByScore query1 = new RangeByScore(new ScoreBoundary(10), new ScoreBoundary(20));
     * Map<String, Double> payload1 = client.zrangeWithScores("mySortedSet", query1, true).get();
     * assert payload1.equals(Map.of('member2', 15.2, 'member1', 10.5)); // Returns members with scores between 10 and 20 (inclusive) with their scores.
     *
     * RangeByScore query2 = new RangeByScore(InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(3));
     * Map<String, Double> payload2 = client.zrangeWithScores("mySortedSet", query2, false).get();
     * assert payload2.equals(Map.of('member4', -2.0, 'member7', 1.5)); // Returns members with with scores within the range of negative infinity to 3, with their scores.
     * }</pre>
     */
    CompletableFuture<Map<String, Double>> zrangeWithScores(
            String key, ScoredRangeQuery rangeQuery, boolean reverse);

    /**
     * Returns the specified range of elements with their scores in the sorted set stored at <code>key
     * </code>. Similar to {@link #zrange} but with a <code>WITHSCORE</code> flag.
     *
     * @see <a href="https://redis.io/commands/zrange/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @return A <code>Map</code> of elements and their scores within the specified range. If <code>
     *     key</code> does not exist, it is treated as an empty sorted set, and the command returns an
     *     empty <code>Map</code>.
     * @example
     *     <pre>{@code
     * RangeByScore query1 = new RangeByScore(new ScoreBoundary(10), new ScoreBoundary(20));
     * Map<String, Double> payload1 = client.zrangeWithScores("mySortedSet", query1).get();
     * assert payload1.equals(Map.of('member1', 10.5, 'member2', 15.2)); // Returns members with scores between 10 and 20 (inclusive) with their scores.
     *
     * RangeByScore query2 = new RangeByScore(InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(3));
     * Map<String, Double> payload2 = client.zrangeWithScores("mySortedSet", query2).get();
     * assert payload2.equals(Map.of('member4', -2.0, 'member7', 1.5)); // Returns members with with scores within the range of negative infinity to 3, with their scores.
     * }</pre>
     */
    CompletableFuture<Map<String, Double>> zrangeWithScores(String key, ScoredRangeQuery rangeQuery);

    /**
     * Stores a specified range of elements from the sorted set at <code>source</code>, into a new
     * sorted set at <code>destination</code>. If <code>destination</code> doesn't exist, a new sorted
     * set is created; if it exists, it's overwritten.<br>
     *
     * @apiNote When in cluster mode, <code>destination</code> and <code>source</code> must map to the
     *     same hash slot.
     * @see <a href="https://redis.io/commands/zrangestore/">redis.io</a> for more details.
     * @param destination The key for the destination sorted set.
     * @param source The key of the source sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by lexicographical order, use {@link RangeByLex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @param reverse If <code>true</code>, reverses the sorted set, with index <code>0</code> as the
     *     element with the highest score.
     * @return The number of elements in the resulting sorted set.
     * @example
     *     <pre>{@code
     * RangeByIndex query1 = new RangeByIndex(0, -1); // Query for all members.
     * Long payload1 = client.zrangestore("destinationKey", "mySortedSet", query1, true).get();
     * assert payload1 == 7L;
     *
     * RangeByScore query2 = new RangeByScore(InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(3)); // Query for members with scores within the range of negative infinity to 3.
     * Long payload2 = client.zrangestore("destinationKey", "mySortedSet", query2, false).get();
     * assert payload2 == 5L;
     * }</pre>
     */
    CompletableFuture<Long> zrangestore(
            String destination, String source, RangeQuery rangeQuery, boolean reverse);

    /**
     * Stores a specified range of elements from the sorted set at <code>source</code>, into a new
     * sorted set at <code>destination</code>. If <code>destination</code> doesn't exist, a new sorted
     * set is created; if it exists, it's overwritten.<br>
     *
     * @apiNote When in cluster mode, <code>destination</code> and <code>source</code> must map to the
     *     same hash slot.
     * @see <a href="https://redis.io/commands/zrangestore/">redis.io</a> for more details.
     * @param destination The key for the destination sorted set.
     * @param source The key of the source sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by lexicographical order, use {@link RangeByLex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @return The number of elements in the resulting sorted set.
     * @example
     *     <pre>{@code
     * RangeByIndex query1 = new RangeByIndex(0, -1); // Query for all members.
     * Long payload1 = client.zrangestore("destinationKey", "mySortedSet", query1).get();
     * assert payload1 == 7L;
     *
     * RangeByScore query2 = new RangeByScore(InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(3)); // Query for members with scores within the range of negative infinity to 3.
     * Long payload2 = client.zrangestore("destinationKey", "mySortedSet", query2).get();
     * assert payload2 == 5L;
     * }</pre>
     */
    CompletableFuture<Long> zrangestore(String destination, String source, RangeQuery rangeQuery);

    /**
     * Returns the rank of <code>member</code> in the sorted set stored at <code>key</code>, with
     * scores ordered from low to high, starting from <code>0</code>.<br>
     * To get the rank of <code>member</code> with its score, see {@link #zrankWithScore}.
     *
     * @see <a href="https://redis.io/commands/zrank/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member The member whose rank is to be retrieved.
     * @return The rank of <code>member</code> in the sorted set.<br>
     *     If <code>key</code> doesn't exist, or if <code>member</code> is not present in the set,
     *     <code>null</code> will be returned.
     * @example
     *     <pre>{@code
     * Long num1 = client.zrank("mySortedSet", "member2").get();
     * assert num1 == 3L; // Indicates that "member2" has the second-lowest score in the sorted set "mySortedSet".
     *
     * Long num2 = client.zcard("mySortedSet", "nonExistingMember").get();
     * assert num2 == null; // Indicates that "nonExistingMember" is not present in the sorted set "mySortedSet".
     * }</pre>
     */
    CompletableFuture<Long> zrank(String key, String member);

    /**
     * Returns the rank of <code>member</code> in the sorted set stored at <code>key</code>, with
     * scores ordered from low to high, starting from <code>0</code>.<br>
     * To get the rank of <code>member</code> with its score, see {@link #zrankWithScore}.
     *
     * @see <a href="https://redis.io/commands/zrank/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member The member whose rank is to be retrieved.
     * @return The rank of <code>member</code> in the sorted set.<br>
     *     If <code>key</code> doesn't exist, or if <code>member</code> is not present in the set,
     *     <code>null</code> will be returned.
     * @example
     *     <pre>{@code
     * Long num1 = client.zrank(gs("mySortedSet"), gs("member2")).get();
     * assert num1 == 3L; // Indicates that "member2" has the second-lowest score in the sorted set "mySortedSet".
     *
     * Long num2 = client.zcard(gs("mySortedSet"), gs("nonExistingMember")).get();
     * assert num2 == null; // Indicates that "nonExistingMember" is not present in the sorted set "mySortedSet".
     * }</pre>
     */
    CompletableFuture<Long> zrank(GlideString key, GlideString member);

    /**
     * Returns the rank of <code>member</code> in the sorted set stored at <code>key</code> with its
     * score, where scores are ordered from the lowest to highest, starting from <code>0</code>.<br>
     *
     * @see <a href="https://redis.io/commands/zrank/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member The member whose rank is to be retrieved.
     * @return An array containing the rank (as <code>Long</code>) and score (as <code>Double</code>)
     *     of <code>member</code> in the sorted set.<br>
     *     If <code>key</code> doesn't exist, or if <code>member</code> is not present in the set,
     *     <code>null</code> will be returned.
     * @example
     *     <pre>{@code
     * Object[] result1 = client.zrankWithScore("mySortedSet", "member2").get();
     * assert ((Long) result1[0]) == 1L && ((Double) result1[1]) == 6.0; // Indicates that "member2" with score 6.0 has the second-lowest score in the sorted set "mySortedSet".
     *
     * Object[] result2 = client.zrankWithScore("mySortedSet", "nonExistingMember").get();
     * assert result2 == null; // Indicates that "nonExistingMember" is not present in the sorted set "mySortedSet".
     * }</pre>
     */
    CompletableFuture<Object[]> zrankWithScore(String key, String member);

    /**
     * Returns the rank of <code>member</code> in the sorted set stored at <code>key</code>, where
     * scores are ordered from the highest to lowest, starting from <code>0</code>.<br>
     * To get the rank of <code>member</code> with its score, see {@link #zrevrankWithScore}.
     *
     * @see <a href="https://redis.io/commands/zrevrank/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member The member whose rank is to be retrieved.
     * @return The rank of <code>member</code> in the sorted set, where ranks are ordered from high to
     *     low based on scores.<br>
     *     If <code>key</code> doesn't exist, or if <code>member</code> is not present in the set,
     *     <code>null</code> will be returned.
     * @example
     *     <pre>{@code
     * Long num1 = client.zrevrank("mySortedSet", "member2").get();
     * assert num1 == 1L; // Indicates that "member2" has the second-highest score in the sorted set "mySortedSet".
     *
     * Long num2 = client.zrevrank("mySortedSet", "nonExistingMember").get();
     * assert num2 == null; // Indicates that "nonExistingMember" is not present in the sorted set "mySortedSet".
     * }</pre>
     */
    CompletableFuture<Long> zrevrank(String key, String member);

    /**
     * Returns the rank of <code>member</code> in the sorted set stored at <code>key</code> with its
     * score, where scores are ordered from the highest to lowest, starting from <code>0</code>.
     *
     * @see <a href="https://redis.io/commands/zrevrank/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member The member whose rank is to be retrieved.
     * @return An array containing the rank (as <code>Long</code>) and score (as <code>Double</code>)
     *     of <code>member</code> in the sorted set, where ranks are ordered from high to low based on
     *     scores.<br>
     *     If <code>key</code> doesn't exist, or if <code>member</code> is not present in the set,
     *     <code>null</code> will be returned.
     * @example
     *     <pre>{@code
     * Object[] result1 = client.zrevrankWithScore("mySortedSet", "member2").get();
     * assert ((Long) result1[0]) == 1L && ((Double) result1[1]) == 6.0; // Indicates that "member2" with score 6.0 has the second-highest score in the sorted set "mySortedSet".
     *
     * Object[] result2 = client.zrevrankWithScore("mySortedSet", "nonExistingMember").get();
     * assert result2 == null; // Indicates that "nonExistingMember" is not present in the sorted set "mySortedSet".
     * }</pre>
     */
    CompletableFuture<Object[]> zrevrankWithScore(String key, String member);

    /**
     * Returns the scores associated with the specified <code>members</code> in the sorted set stored
     * at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zmscore/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param members An array of members in the sorted set.
     * @return An <code>Array</code> of scores of the <code>members</code>.<br>
     *     If a <code>member</code> does not exist, the corresponding value in the <code>Array</code>
     *     will be <code>null</code>.
     * @example
     *     <pre>{@code
     * Double[] payload = client.zmscore(key1, new String[] {"one", "nonExistentMember", "three"}).get();
     * assert payload.equals(new Double[] {1.0, null, 3.0});
     * }</pre>
     */
    CompletableFuture<Double[]> zmscore(String key, String[] members);

    /**
     * Returns the scores associated with the specified <code>members</code> in the sorted set stored
     * at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zmscore/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param members An array of members in the sorted set.
     * @return An <code>Array</code> of scores of the <code>members</code>.<br>
     *     If a <code>member</code> does not exist, the corresponding value in the <code>Array</code>
     *     will be <code>null</code>.
     * @example
     *     <pre>{@code
     * Double[] payload = client.zmscore(key1, new GlideString[] {gs("one"), gs("nonExistentMember"), gs("three")}).get();
     * assert payload.equals(new Double[] {1.0, null, 3.0});
     * }</pre>
     */
    CompletableFuture<Double[]> zmscore(GlideString key, GlideString[] members);

    /**
     * Returns the difference between the first sorted set and all the successive sorted sets.<br>
     * To get the elements with their scores, see {@link #zdiffWithScores}.
     *
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zdiff/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets.
     * @return An <code>array</code> of elements representing the difference between the sorted sets.
     *     <br>
     *     If the first <code>key</code> does not exist, it is treated as an empty sorted set, and the
     *     command returns an empty <code>array</code>.
     * @example
     *     <pre>{@code
     * String[] payload = client.zdiff(new String[] {"sortedSet1", "sortedSet2", "sortedSet3"}).get();
     * assert payload.equals(new String[]{"element1"});
     * }</pre>
     */
    CompletableFuture<String[]> zdiff(String[] keys);

    /**
     * Returns the difference between the first sorted set and all the successive sorted sets.
     *
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zdiff/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets.
     * @return A <code>Map</code> of elements and their scores representing the difference between the
     *     sorted sets.<br>
     *     If the first <code>key</code> does not exist, it is treated as an empty sorted set, and the
     *     command returns an empty <code>Map</code>.
     * @example
     *     <pre>{@code
     * Map<String, Double> payload = client.zdiffWithScores(new String[] {"sortedSet1", "sortedSet2", "sortedSet3"}).get();
     * assert payload.equals(Map.of("element1", 1.0));
     * }</pre>
     */
    CompletableFuture<Map<String, Double>> zdiffWithScores(String[] keys);

    /**
     * Calculates the difference between the first sorted set and all the successive sorted sets at
     * <code>keys</code> and stores the difference as a sorted set to <code>destination</code>,
     * overwriting it if it already exists. Non-existent keys are treated as empty sets.
     *
     * @apiNote When in cluster mode, <code>destination</code> and all <code>keys</code> must map to
     *     the same hash slot.
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zdiffstore/">redis.io</a> for more details.
     * @param destination The key for the resulting sorted set.
     * @param keys The keys of the sorted sets to compare.
     * @return The number of members in the resulting sorted set stored at <code>destination</code>.
     * @example
     *     <pre>{@code
     * Long payload = client.zdiffstore("mySortedSet", new String[] {"key1", "key2"}).get();
     * assert payload > 0; // At least one member differed in "key1" compared to "key2", and this difference was stored in "mySortedSet".
     * }</pre>
     */
    CompletableFuture<Long> zdiffstore(String destination, String[] keys);

    /**
     * Calculates the difference between the first sorted set and all the successive sorted sets at
     * <code>keys</code> and stores the difference as a sorted set to <code>destination</code>,
     * overwriting it if it already exists. Non-existent keys are treated as empty sets.
     *
     * @apiNote When in cluster mode, <code>destination</code> and all <code>keys</code> must map to
     *     the same hash slot.
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zdiffstore/">redis.io</a> for more details.
     * @param destination The key for the resulting sorted set.
     * @param keys The keys of the sorted sets to compare.
     * @return The number of members in the resulting sorted set stored at <code>destination</code>.
     * @example
     *     <pre>{@code
     * Long payload = client.zdiffstore(gs("mySortedSet"), new GlideString[] {gs("key1"), gs("key2")}).get();
     * assert payload > 0; // At least one member differed in "key1" compared to "key2", and this difference was stored in "mySortedSet".
     * }</pre>
     */
    CompletableFuture<Long> zdiffstore(GlideString destination, GlideString[] keys);

    /**
     * Returns the number of members in the sorted set stored at <code>key</code> with scores between
     * <code>minScore</code> and <code>maxScore</code>.
     *
     * @see <a href="https://redis.io/commands/zcount/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param minScore The minimum score to count from. Can be an implementation of {@link
     *     InfScoreBound} representing positive/negative infinity, or {@link ScoreBoundary}
     *     representing a specific score and inclusivity.
     * @param maxScore The maximum score to count up to. Can be an implementation of {@link
     *     InfScoreBound} representing positive/negative infinity, or {@link ScoreBoundary}
     *     representing a specific score and inclusivity.
     * @return The number of members in the specified score range.<br>
     *     If <code>key</code> does not exist, it is treated as an empty sorted set, and the command
     *     returns <code>0</code>.<br>
     *     If <code>maxScore < minScore</code>, <code>0</code> is returned.
     * @example
     *     <pre>{@code
     * Long num1 = client.zcount("my_sorted_set", new ScoreBoundary(5.0, true), InfScoreBound.POSITIVE_INFINITY).get();
     * assert num1 == 2L; // Indicates that there are 2 members with scores between 5.0 (inclusive) and +inf in the sorted set "my_sorted_set".
     *
     * Long num2 = client.zcount("my_sorted_set", new ScoreBoundary(5.0, true), new ScoreBoundary(10.0, false)).get();
     * assert num2 == 1L; // Indicates that there is one member with ScoreBoundary 5.0 <= score < 10.0 in the sorted set "my_sorted_set".
     * }</pre>
     */
    CompletableFuture<Long> zcount(String key, ScoreRange minScore, ScoreRange maxScore);

    /**
     * Removes all elements in the sorted set stored at <code>key</code> with rank between <code>start
     * </code> and <code>end</code>. Both <code>start</code> and <code>end</code> are zero-based
     * indexes with <code>0</code> being the element with the lowest score. These indexes can be
     * negative numbers, where they indicate offsets starting at the element with the highest score.
     *
     * @see <a href="https://redis.io/commands/zremrangebyrank/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param start The starting point of the range.
     * @param end The end of the range.
     * @return The number of elements removed.<br>
     *     If <code>start</code> exceeds the end of the sorted set, or if <code>start</code> is
     *     greater than <code>end</code>, <code>0</code> returned.<br>
     *     If <code>end</code> exceeds the actual end of the sorted set, the range will stop at the
     *     actual end of the sorted set.<br>
     *     If <code>key</code> does not exist <code>0</code> will be returned.
     * @example
     *     <pre>{@code
     * Long payload1 = client.zremrangebyrank("mySortedSet", 0, 4).get();
     * assert payload1 == 5L; // Indicates that 5 elements, with ranks ranging from 0 to 4 (inclusive), have been removed from "mySortedSet".
     *
     * Long payload2 = client.zremrangebyrank("mySortedSet", 0, 4).get();
     * assert payload2 == 0L; // Indicates that nothing was removed.
     * }</pre>
     */
    CompletableFuture<Long> zremrangebyrank(String key, long start, long end);

    /**
     * Removes all elements in the sorted set stored at <code>key</code> with rank between <code>start
     * </code> and <code>end</code>. Both <code>start</code> and <code>end</code> are zero-based
     * indexes with <code>0</code> being the element with the lowest score. These indexes can be
     * negative numbers, where they indicate offsets starting at the element with the highest score.
     *
     * @see <a href="https://redis.io/commands/zremrangebyrank/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param start The starting point of the range.
     * @param end The end of the range.
     * @return The number of elements removed.<br>
     *     If <code>start</code> exceeds the end of the sorted set, or if <code>start</code> is
     *     greater than <code>end</code>, <code>0</code> returned.<br>
     *     If <code>end</code> exceeds the actual end of the sorted set, the range will stop at the
     *     actual end of the sorted set.<br>
     *     If <code>key</code> does not exist <code>0</code> will be returned.
     * @example
     *     <pre>{@code
     * Long payload1 = client.zremrangebyrank(gs("mySortedSet"), 0, 4).get();
     * assert payload1 == 5L; // Indicates that 5 elements, with ranks ranging from 0 to 4 (inclusive), have been removed from "mySortedSet".
     *
     * Long payload2 = client.zremrangebyrank(gs("mySortedSet"), 0, 4).get();
     * assert payload2 == 0L; // Indicates that nothing was removed.
     * }</pre>
     */
    CompletableFuture<Long> zremrangebyrank(GlideString key, long start, long end);

    /**
     * Removes all elements in the sorted set stored at <code>key</code> with a lexicographical order
     * between <code>minLex</code> and <code>maxLex</code>.
     *
     * @see <a href="https://redis.io/commands/zremrangebylex/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param minLex The minimum bound of the lexicographical range. Can be an implementation of
     *     {@link InfLexBound} representing positive/negative infinity, or {@link LexBoundary}
     *     representing a specific lex and inclusivity.
     * @param maxLex The maximum bound of the lexicographical range. Can be an implementation of
     *     {@link InfLexBound} representing positive/negative infinity, or {@link LexBoundary}
     *     representing a specific lex and inclusivity.
     * @return The number of members removed from the sorted set.<br>
     *     If <code>key</code> does not exist, it is treated as an empty sorted set, and the command
     *     returns <code>0</code>.<br>
     *     If <code>minLex</code> is greater than <code>maxLex</code>, <code>0</code> is returned.
     * @example
     *     <pre>{@code
     * Long payload1 = client.zremrangebylex("mySortedSet", new LexBoundary("a", false), new LexBoundary("e")).get();
     * assert payload1 == 4L; // Indicates that 4 members, with lexicographical values ranging from "a" (exclusive) to "e" (inclusive), have been removed from "mySortedSet".
     *
     * Long payload2 = client.zremrangebylex("mySortedSet", InfLexBound.NEGATIVE_INFINITY , new LexBoundary("e")).get();
     * assert payload2 == 0L; // Indicates that no elements were removed.
     * }</pre>
     */
    CompletableFuture<Long> zremrangebylex(String key, LexRange minLex, LexRange maxLex);

    /**
     * Removes all elements in the sorted set stored at <code>key</code> with a score between <code>
     * minScore</code> and <code>maxScore</code>.
     *
     * @see <a href="https://redis.io/commands/zremrangebyscore/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param minScore The minimum score to remove from. Can be an implementation of {@link
     *     InfScoreBound} representing positive/negative infinity, or {@link ScoreBoundary}
     *     representing a specific score and inclusivity.
     * @param maxScore The maximum score to remove to. Can be an implementation of {@link
     *     InfScoreBound} representing positive/negative infinity, or {@link ScoreBoundary}
     *     representing a specific score and inclusivity.
     * @return The number of members removed.<br>
     *     If <code>key</code> does not exist, it is treated as an empty sorted set, and the command
     *     returns <code>0</code>.<br>
     *     If <code>minScore</code> is greater than <code>maxScore</code>, <code>0</code> is returned.
     * @example
     *     <pre>{@code
     * Long payload1 = client.zremrangebyscore("mySortedSet", new ScoreBoundary(1, false), new ScoreBoundary(5)).get();
     * assert payload1 == 4L; // Indicates that 4 members, with scores ranging from 1 (exclusive) to 5 (inclusive), have been removed from "mySortedSet".
     *
     * Long payload2 = client.zremrangebyscore("mySortedSet", InfScoreBound.NEGATIVE_INFINITY , new ScoreBoundary(-42)).get();
     * assert payload2 == 0L; // Indicates that no elements were removed.
     * }</pre>
     */
    CompletableFuture<Long> zremrangebyscore(String key, ScoreRange minScore, ScoreRange maxScore);

    /**
     * Returns the number of members in the sorted set stored at <code>key</code> with scores between
     * <code>minLex</code> and <code>maxLex</code>.
     *
     * @see <a href="https://redis.io/commands/zlexcount/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param minLex The minimum lex to count from. Can be an implementation of {@link InfLexBound}
     *     representing positive/negative infinity, or {@link LexBoundary} representing a specific lex
     *     and inclusivity.
     * @param maxLex The maximum lex to count up to. Can be an implementation of {@link InfLexBound}
     *     representing positive/negative infinity, or {@link LexBoundary} representing a specific lex
     *     and inclusivity.
     * @return The number of members in the specified lex range.<br>
     *     If <code>key</code> does not exist, it is treated as an empty sorted set, and the command
     *     returns <code>0</code>.<br>
     *     If <code>maxLex < minLex</code>, <code>0</code> is returned.
     * @example
     *     <pre>{@code
     * Long num1 = client.zlexcount("my_sorted_set", new LexBoundary("c", true), InfLexBound.POSITIVE_INFINITY).get();
     * assert num1 == 2L; // Indicates that there are 2 members with lex scores between "c" (inclusive) and positive infinity in the sorted set "my_sorted_set".
     *
     * Long num2 = client.zlexcount("my_sorted_set", new ScoreBoundary("c", true), new ScoreBoundary("k", false)).get();
     * assert num2 == 1L; // Indicates that there is one member with LexBoundary "c" <= score < "k" in the sorted set "my_sorted_set".
     * }</pre>
     */
    CompletableFuture<Long> zlexcount(String key, LexRange minLex, LexRange maxLex);

    /**
     * Computes the union of sorted sets given by the specified <code>KeysOrWeightedKeys</code>, and
     * stores the result in <code>destination</code>. If <code>destination</code> already exists, it
     * is overwritten. Otherwise, a new sorted set will be created.
     *
     * @apiNote When in cluster mode, <code>destination</code> and all keys in <code>
     *     keysOrWeightedKeys</code> must map to the same hash slot.
     * @see <a href="https://redis.io/commands/zunionstore/">redis.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArray} for keys only.
     *       <li>Use {@link WeightedKeys} for weighted keys with score multipliers.
     *     </ul>
     *
     * @param aggregate Specifies the aggregation strategy to apply when combining the scores of
     *     elements.
     * @return The number of elements in the resulting sorted set stored at <code>destination</code>.
     * @example
     *     <pre>{@code
     * WeightedKeys weightedKeys = new WeightedKeys(List.of(Pair.of("mySortedSet1", 1.0), Pair.of("mySortedSet2", 2.0)));
     * Long payload = client.zunionstore("newSortedSet", weightedKeys, Aggregate.MAX).get()
     * assert payload == 3L; // Indicates the new sorted set contains three members from the union of "mySortedSet1" and "mySortedSet2".
     * }</pre>
     */
    CompletableFuture<Long> zunionstore(
            String destination, KeysOrWeightedKeys keysOrWeightedKeys, Aggregate aggregate);

    /**
     * Computes the union of sorted sets given by the specified <code>KeysOrWeightedKeysBinary</code>,
     * and stores the result in <code>destination</code>. If <code>destination</code> already exists,
     * it is overwritten. Otherwise, a new sorted set will be created.
     *
     * @apiNote When in cluster mode, <code>destination</code> and all keys in <code>
     *     keysOrWeightedKeys</code> must map to the same hash slot.
     * @see <a href="https://redis.io/commands/zunionstore/">redis.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param keysOrWeightedKeysBinary The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArrayBinary} for keys only.
     *       <li>Use {@link WeightedKeysBinary} for weighted keys with score multipliers.
     *     </ul>
     *
     * @param aggregate Specifies the aggregation strategy to apply when combining the scores of
     *     elements.
     * @return The number of elements in the resulting sorted set stored at <code>destination</code>.
     * @example
     *     <pre>{@code
     * WeightedKeysBinary weightedKeys = new WeightedKeysBinary(List.of(Pair.of(gs("mySortedSet1"), 1.0), Pair.of(gs("mySortedSet2"), 2.0)));
     * Long payload = client.zunionstore(gs("newSortedSet"), weightedKeys, Aggregate.MAX).get()
     * assert payload == 3L; // Indicates the new sorted set contains three members from the union of gs("mySortedSet1") and gs("mySortedSet2").
     * }</pre>
     */
    CompletableFuture<Long> zunionstore(
            GlideString destination, KeysOrWeightedKeysBinary keysOrWeightedKeys, Aggregate aggregate);

    /**
     * Computes the union of sorted sets given by the specified <code>KeysOrWeightedKeys</code>, and
     * stores the result in <code>destination</code>. If <code>destination</code> already exists, it
     * is overwritten. Otherwise, a new sorted set will be created.
     *
     * @apiNote When in cluster mode, <code>destination</code> and all keys in <code>
     *     keysOrWeightedKeys</code> must map to the same hash slot.
     * @see <a href="https://redis.io/commands/zunionstore/">redis.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArray} for keys only.
     *       <li>Use {@link WeightedKeys} for weighted keys with score multipliers.
     *     </ul>
     *
     * @return The number of elements in the resulting sorted set stored at <code>destination</code>.
     * @example
     *     <pre>{@code
     * KeyArray keyArray = new KeyArray(new String[] {"mySortedSet1", "mySortedSet2"});
     * Long payload = client.zunionstore("newSortedSet", keyArray).get()
     * assert payload == 3L; // Indicates the new sorted set contains three members from the union of "mySortedSet1" and "mySortedSet2".
     * }</pre>
     */
    CompletableFuture<Long> zunionstore(String destination, KeysOrWeightedKeys keysOrWeightedKeys);

    /**
     * Computes the union of sorted sets given by the specified <code>KeysOrWeightedKeysBinary</code>,
     * and stores the result in <code>destination</code>. If <code>destination</code> already exists,
     * it is overwritten. Otherwise, a new sorted set will be created.
     *
     * @apiNote When in cluster mode, <code>destination</code> and all keys in <code>
     *     keysOrWeightedKeysBinary</code> must map to the same hash slot.
     * @see <a href="https://redis.io/commands/zunionstore/">redis.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param keysOrWeightedKeysBinary The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArrayBinary} for keys only.
     *       <li>Use {@link WeightedKeysBinary} for weighted keys with score multipliers.
     *     </ul>
     *
     * @return The number of elements in the resulting sorted set stored at <code>destination</code>.
     * @example
     *     <pre>{@code
     * KeyArrayBinary keyArray = new KeyArray(new GlideString[] {gs("mySortedSet1"), gs("mySortedSet2")});
     * Long payload = client.zunionstore(gs("newSortedSet"), keyArray).get()
     * assert payload == 3L; // Indicates the new sorted set contains three members from the union of gs("mySortedSet1") and gs("mySortedSet2").
     * }</pre>
     */
    CompletableFuture<Long> zunionstore(
            GlideString destination, KeysOrWeightedKeysBinary keysOrWeightedKeys);

    /**
     * Computes the intersection of sorted sets given by the specified <code>keysOrWeightedKeys</code>
     * , and stores the result in <code>destination</code>. If <code>destination</code> already
     * exists, it is overwritten. Otherwise, a new sorted set will be created.
     *
     * @apiNote When in cluster mode, <code>destination</code> and all keys in <code>
     *     keysOrWeightedKeys</code> must map to the same hash slot.
     * @see <a href="https://redis.io/commands/zinterstore/">redis.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArray} for keys only.
     *       <li>Use {@link WeightedKeys} for weighted keys with score multipliers.
     *     </ul>
     *
     * @param aggregate Specifies the aggregation strategy to apply when combining the scores of
     *     elements.
     * @return The number of elements in the resulting sorted set stored at <code>destination</code>.
     * @example
     *     <pre>{@code
     * WeightedKeys weightedKeys = new WeightedKeys(List.of(Pair.of("mySortedSet1", 1.0), Pair.of("mySortedSet2", 2.0)));
     * Long payload = client.zinterstore("newSortedSet", weightedKeys, Aggregate.MAX).get()
     * assert payload == 3L; // Indicates the new sorted set contains three members from the intersection of "mySortedSet1" and "mySortedSet2".
     * }</pre>
     */
    CompletableFuture<Long> zinterstore(
            String destination, KeysOrWeightedKeys keysOrWeightedKeys, Aggregate aggregate);

    /**
     * Computes the intersection of sorted sets given by the specified <code>KeysOrWeightedKeys</code>
     * , and stores the result in <code>destination</code>. If <code>destination</code> already
     * exists, it is overwritten. Otherwise, a new sorted set will be created.
     *
     * @apiNote When in cluster mode, <code>destination</code> and all keys in <code>
     *     keysOrWeightedKeys</code> must map to the same hash slot.
     * @see <a href="https://redis.io/commands/zinterstore/">redis.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArray} for keys only.
     *       <li>Use {@link WeightedKeys} for weighted keys with score multipliers.
     *     </ul>
     *
     * @return The number of elements in the resulting sorted set stored at <code>destination</code>.
     * @example
     *     <pre>{@code
     * KeyArray keyArray = new KeyArray(new String[] {"mySortedSet1", "mySortedSet2"});
     * Long payload = client.zinterstore("newSortedSet", keyArray).get()
     * assert payload == 3L; // Indicates the new sorted set contains three members from the intersection of "mySortedSet1" and "mySortedSet2".
     * }</pre>
     */
    CompletableFuture<Long> zinterstore(String destination, KeysOrWeightedKeys keysOrWeightedKeys);

    /**
     * Pops a member-score pair from the first non-empty sorted set, with the given <code>keys</code>
     * being checked in the order they are provided.
     *
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/commands/zmpop/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets.
     * @param modifier The element pop criteria - either {@link ScoreFilter#MIN} or {@link
     *     ScoreFilter#MAX} to pop the member with the lowest/highest score accordingly.
     * @return A two-element <code>array</code> containing the key name of the set from which the
     *     element was popped, and a member-score <code>Map</code> of the popped element.<br>
     *     If no member could be popped, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Object[] result = client.zmpop(new String[] { "zSet1", "zSet2" }, MAX).get();
     * Map<String, Double> data = (Map<String, Double>)result[1];
     * String element = data.keySet().toArray(String[]::new)[0];
     * System.out.printf("Popped '%s' with score %d from '%s'%n", element, data.get(element), result[0]);
     * }</pre>
     */
    CompletableFuture<Object[]> zmpop(String[] keys, ScoreFilter modifier);

    /**
     * Pops multiple member-score pairs from the first non-empty sorted set, with the given <code>keys
     * </code> being checked in the order they are provided.
     *
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/commands/zmpop/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets.
     * @param modifier The element pop criteria - either {@link ScoreFilter#MIN} or {@link
     *     ScoreFilter#MAX} to pop members with the lowest/highest scores accordingly.
     * @param count The number of elements to pop.
     * @return A two-element <code>array</code> containing the key name of the set from which elements
     *     were popped, and a member-score <code>Map</code> of the popped elements.<br>
     *     If no member could be popped, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Object[] result = client.zmpop(new String[] { "zSet1", "zSet2" }, MAX, 2).get();
     * Map<String, Double> data = (Map<String, Double>)result[1];
     * for (Map.Entry<String, Double> entry : data.entrySet()) {
     *     System.out.printf("Popped '%s' with score %d from '%s'%n", entry.getKey(), entry.getValue(), result[0]);
     * }
     * }</pre>
     */
    CompletableFuture<Object[]> zmpop(String[] keys, ScoreFilter modifier, long count);

    /**
     * Blocks the connection until it pops and returns a member-score pair from the first non-empty
     * sorted set, with the given <code>keys</code> being checked in the order they are provided.<br>
     * <code>BZMPOP</code> is the blocking variant of {@link #zmpop(String[], ScoreFilter)}.
     *
     * @apiNote
     *     <ol>
     *       <li>When in cluster mode, all <code>keys</code> must map to the same hash slot.
     *       <li><code>BZMPOP</code> is a client blocking command, see <a
     *           href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *           Commands</a> for more details and best practices.
     *     </ol>
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/commands/bzmpop/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets.
     * @param modifier The element pop criteria - either {@link ScoreFilter#MIN} or {@link
     *     ScoreFilter#MAX} to pop members with the lowest/highest scores accordingly.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @return A two-element <code>array</code> containing the key name of the set from which an
     *     element was popped, and a member-score <code>Map</code> of the popped elements.<br>
     *     If no member could be popped and the timeout expired, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Object[] result = client.bzmpop(new String[] { "zSet1", "zSet2" }, MAX, 0.1).get();
     * Map<String, Double> data = (Map<String, Double>)result[1];
     * String element = data.keySet().toArray(String[]::new)[0];
     * System.out.printf("Popped '%s' with score %d from '%s'%n", element, data.get(element), result[0]);
     * }</pre>
     */
    CompletableFuture<Object[]> bzmpop(String[] keys, ScoreFilter modifier, double timeout);

    /**
     * Blocks the connection until it pops and returns multiple member-score pairs from the first
     * non-empty sorted set, with the given <code>keys</code> being checked in the order they are
     * provided.<br>
     * <code>BZMPOP</code> is the blocking variant of {@link #zmpop(String[], ScoreFilter, long)}.
     *
     * @apiNote
     *     <ol>
     *       <li>When in cluster mode, all <code>keys</code> must map to the same hash slot.
     *       <li><code>BZMPOP</code> is a client blocking command, see <a
     *           href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *           Commands</a> for more details and best practices.
     *     </ol>
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/commands/bzmpop/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets.
     * @param modifier The element pop criteria - either {@link ScoreFilter#MIN} or {@link
     *     ScoreFilter#MAX} to pop members with the lowest/highest scores accordingly.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @param count The number of elements to pop.
     * @return A two-element <code>array</code> containing the key name of the set from which elements
     *     were popped, and a member-score <code>Map</code> of the popped elements.<br>
     *     If no members could be popped and the timeout expired, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Object[] result = client.bzmpop(new String[] { "zSet1", "zSet2" }, MAX, 0.1, 2).get();
     * Map<String, Double> data = (Map<String, Double>)result[1];
     * for (Map.Entry<String, Double> entry : data.entrySet()) {
     *     System.out.printf("Popped '%s' with score %d from '%s'%n", entry.getKey(), entry.getValue(), result[0]);
     * }
     * }</pre>
     */
    CompletableFuture<Object[]> bzmpop(
            String[] keys, ScoreFilter modifier, double timeout, long count);

    /**
     * Returns the union of members from sorted sets specified by the given <code>keys</code>.<br>
     * To get the elements with their scores, see {@link #zunionWithScores}.
     *
     * @apiNote When in cluster mode, all keys in <code>keys</code> must map to the same hash slot.
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zunion/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets.
     * @return The resulting sorted set from the union.
     * @example
     *     <pre>{@code
     * KeyArray keyArray = new KeyArray(new String[] {"mySortedSet1", "mySortedSet2"});
     * String[] payload = client.zunion(keyArray).get()
     * assert payload.equals(new String[] {"elem1", "elem2", "elem3"});
     *
     * WeightedKeys weightedKeys = new WeightedKeys(List.of(Pair.of("mySortedSet1", 2.0), Pair.of("mySortedSet2", 2.0)));
     * String[] payload = client.zunion(weightedKeys).get()
     * assert payload.equals(new String[] {"elem1", "elem2", "elem3"});
     * }</pre>
     */
    CompletableFuture<String[]> zunion(KeyArray keys);

    /**
     * Returns the union of members from sorted sets specified by the given <code>keys</code>.<br>
     * To get the elements with their scores, see {@link #zunionWithScores}.
     *
     * @apiNote When in cluster mode, all keys in <code>keys</code> must map to the same hash slot.
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zunion/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets.
     * @return The resulting sorted set from the union.
     * @example
     *     <pre>{@code
     * KeyArrayBinary keyArray = new KeyArray(new GlideString[] {gs("mySortedSet1"), gs("mySortedSet2")});
     * GlideString[] payload = client.zunion(keyArray).get()
     * assert payload.equals(new GlideString[] {gs("elem1"), gs("elem2"), gs("elem3")});
     *
     * WeightedKeys weightedKeys = new WeightedKeys(List.of(Pair.of("mySortedSet1", 2.0), Pair.of(gs("mySortedSet2"), 2.0)));
     * GlideString[] payload = client.zunion(weightedKeys).get()
     * assert payload.equals(new GlideString[] {gs("elem1"), gs("elem2"), gs("elem3")});
     * }</pre>
     */
    CompletableFuture<GlideString[]> zunion(KeyArrayBinary keys);

    /**
     * Returns the union of members and their scores from sorted sets specified by the given <code>
     * keysOrWeightedKeys</code>.
     *
     * @apiNote When in cluster mode, all keys in <code>keysOrWeightedKeys</code> must map to the same
     *     hash slot.
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zunion/">redis.io</a> for more details.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArray} for keys only.
     *       <li>Use {@link WeightedKeys} for weighted keys with score multipliers.
     *     </ul>
     *
     * @param aggregate Specifies the aggregation strategy to apply when combining the scores of
     *     elements.
     * @return The resulting sorted set from the union.
     * @example
     *     <pre>{@code
     * KeyArray keyArray = new KeyArray(new String[] {"mySortedSet1", "mySortedSet2"});
     * Map<String, Double> payload1 = client.zunionWithScores(keyArray, Aggregate.MAX).get();
     * assert payload1.equals(Map.of("elem1", 1.0, "elem2", 2.0, "elem3", 3.0));
     *
     * WeightedKeys weightedKeys = new WeightedKeys(List.of(Pair.of("mySortedSet1", 2.0), Pair.of("mySortedSet2", 2.0)));
     * Map<String, Double> payload2 = client.zunionWithScores(keyArray, Aggregate.SUM).get();
     * assert payload2.equals(Map.of("elem1", 2.0, "elem2", 4.0, "elem3", 6.0));
     * }</pre>
     */
    CompletableFuture<Map<String, Double>> zunionWithScores(
            KeysOrWeightedKeys keysOrWeightedKeys, Aggregate aggregate);

    /**
     * Returns the union of members and their scores from sorted sets specified by the given <code>
     * keysOrWeightedKeys</code>.<br>
     * To perform a <code>zunion</code> operation while specifying aggregation settings, use {@link
     * #zunionWithScores(KeysOrWeightedKeys, Aggregate)}.
     *
     * @apiNote When in cluster mode, all keys in <code>keysOrWeightedKeys</code> must map to the same
     *     hash slot.
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zunion/">redis.io</a> for more details.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArray} for keys only.
     *       <li>Use {@link WeightedKeys} for weighted keys with score multipliers.
     *     </ul>
     *
     * @return The resulting sorted set from the union.
     * @example
     *     <pre>{@code
     * KeyArray keyArray = new KeyArray(new String[] {"mySortedSet1", "mySortedSet2"});
     * Map<String, Double> payload1 = client.zunionWithScores(keyArray).get();
     * assert payload1.equals(Map.of("elem1", 1.0, "elem2", 2.0, "elem3", 3.0));
     *
     * WeightedKeys weightedKeys = new WeightedKeys(List.of(Pair.of("mySortedSet1", 2.0), Pair.of("mySortedSet2", 2.0)));
     * Map<String, Double> payload2 = client.zunionWithScores(keyArray).get();
     * assert payload2.equals(Map.of("elem1", 2.0, "elem2", 4.0, "elem3", 6.0));
     * }</pre>
     */
    CompletableFuture<Map<String, Double>> zunionWithScores(KeysOrWeightedKeys keysOrWeightedKeys);

    /**
     * Returns the intersection of members from sorted sets specified by the given <code>keys</code>.
     * <br>
     * To get the elements with their scores, see {@link #zinterWithScores}.
     *
     * @apiNote When in cluster mode, all keys in <code>keys</code> must map to the same hash slot.
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zinter/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets.
     * @return The resulting sorted set from the intersection.
     * @example
     *     <pre>{@code
     * KeyArray keyArray = new KeyArray(new String[] {"mySortedSet1", "mySortedSet2"});
     * String[] payload = client.zinter(keyArray).get()
     * assert payload.equals(new String[] {"elem1", "elem2", "elem3"});
     *
     * WeightedKeys weightedKeys = new WeightedKeys(List.of(Pair.of("mySortedSet1", 2.0), Pair.of("mySortedSet2", 2.0)));
     * String[] payload = client.zinter(weightedKeys).get()
     * assert payload.equals(new String[] {"elem1", "elem2", "elem3"});
     * }</pre>
     */
    CompletableFuture<String[]> zinter(KeyArray keys);

    /**
     * Returns the intersection of members and their scores from sorted sets specified by the given
     * <code>keysOrWeightedKeys</code>. To perform a <code>zinter</code> operation while specifying
     * aggregation settings, use {@link #zinterWithScores(KeysOrWeightedKeys, Aggregate)}.
     *
     * @apiNote When in cluster mode, all keys in <code>keysOrWeightedKeys</code> must map to the same
     *     hash slot.
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zinter/">redis.io</a> for more details.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArray} for keys only.
     *       <li>Use {@link WeightedKeys} for weighted keys with score multipliers.
     *     </ul>
     *
     * @return The resulting sorted set from the intersection.
     * @example
     *     <pre>{@code
     * KeyArray keyArray = new KeyArray(new String[] {"mySortedSet1", "mySortedSet2"});
     * Map<String, Double> payload1 = client.zinterWithScores(keyArray).get();
     * assert payload1.equals(Map.of("elem1", 1.0, "elem2", 2.0, "elem3", 3.0));
     *
     * WeightedKeys weightedKeys = new WeightedKeys(List.of(Pair.of("mySortedSet1", 2.0), Pair.of("mySortedSet2", 2.0)));
     * Map<String, Double> payload2 = client.zinterWithScores(weightedKeys).get();
     * assert payload2.equals(Map.of("elem1", 2.0, "elem2", 4.0, "elem3", 6.0));
     * }</pre>
     */
    CompletableFuture<Map<String, Double>> zinterWithScores(KeysOrWeightedKeys keysOrWeightedKeys);

    /**
     * Returns the intersection of members and their scores from sorted sets specified by the given
     * <code>keysOrWeightedKeys</code>.
     *
     * @apiNote When in cluster mode, all keys in <code>keysOrWeightedKeys</code> must map to the same
     *     hash slot.
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zinter/">redis.io</a> for more details.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArray} for keys only.
     *       <li>Use {@link WeightedKeys} for weighted keys with score multipliers.
     *     </ul>
     *
     * @param aggregate Specifies the aggregation strategy to apply when combining the scores of
     *     elements.
     * @return The resulting sorted set from the intersection.
     * @example
     *     <pre>{@code
     * KeyArray keyArray = new KeyArray(new String[] {"mySortedSet1", "mySortedSet2"});
     * Map<String, Double> payload1 = client.zinterWithScores(keyArray, Aggregate.MAX).get();
     * assert payload1.equals(Map.of("elem1", 1.0, "elem2", 2.0, "elem3", 3.0));
     *
     * WeightedKeys weightedKeys = new WeightedKeys(List.of(Pair.of("mySortedSet1", 2.0), Pair.of("mySortedSet2", 2.0)));
     * Map<String, Double> payload2 = client.zinterWithScores(weightedKeys, Aggregate.SUM).get();
     * assert payload2.equals(Map.of("elem1", 2.0, "elem2", 4.0, "elem3", 6.0));
     * }</pre>
     */
    CompletableFuture<Map<String, Double>> zinterWithScores(
            KeysOrWeightedKeys keysOrWeightedKeys, Aggregate aggregate);

    /**
     * Returns a random element from the sorted set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zrandmember/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @return A <code>String</code> representing a random element from the sorted set.<br>
     *     If the sorted set does not exist or is empty, the response will be <code>null</code>.
     * @example
     *     <pre>{@code
     * String payload1 = client.zrandmember("mySortedSet").get();
     * assert payload1.equals("GLIDE");
     *
     * String payload2 = client.zrandmember("nonExistingSortedSet").get();
     * assert payload2 == null;
     * }</pre>
     */
    CompletableFuture<String> zrandmember(String key);

    /**
     * Retrieves random elements from the sorted set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zrandmember/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param count The number of elements to return.<br>
     *     If <code>count</code> is positive, returns unique elements.<br>
     *     If negative, allows for duplicates.<br>
     * @return An <code>array</code> of elements from the sorted set.<br>
     *     If the sorted set does not exist or is empty, the response will be an empty <code>array
     *     </code>.
     * @example
     *     <pre>{@code
     * String[] payload1 = client.zrandmember("mySortedSet", -3).get();
     * assert payload1.equals(new String[] {"GLIDE", "GLIDE", "JAVA"});
     *
     * String[] payload2 = client.zrandmember("nonExistingSortedSet", 3).get();
     * assert payload2.length == 0;
     * }</pre>
     */
    CompletableFuture<String[]> zrandmemberWithCount(String key, long count);

    /**
     * Retrieves random elements along with their scores from the sorted set stored at <code>key
     * </code>.
     *
     * @see <a href="https://redis.io/commands/zrandmember/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param count The number of elements to return.<br>
     *     If <code>count</code> is positive, returns unique elements.<br>
     *     If negative, allows duplicates.<br>
     * @return An <code>array</code> of <code>[element, score]</code> <code>arrays</code>, where
     *     element is a <code>String</code> and score is a <code>Double</code>.<br>
     *     If the sorted set does not exist or is empty, the response will be an empty <code>array
     *     </code>.
     * @example
     *     <pre>{@code
     * Object[][] data = client.zrandmemberWithCountWithScores("mySortedSet", -3).get();
     * assert data.length == 3;
     * for (Object[] memberScorePair : data) {
     *     System.out.printf("Member: '%s', score: %d", memberScorePair[0], memberScorePair[1]);
     * }
     * }</pre>
     */
    CompletableFuture<Object[][]> zrandmemberWithCountWithScores(String key, long count);

    /**
     * Increments the score of <code>member</code> in the sorted set stored at <code>key</code> by
     * <code>increment</code>.<br>
     * If <code>member</code> does not exist in the sorted set, it is added with <code>increment
     * </code> as its score. If <code>key</code> does not exist, a new sorted set with the specified
     * member as its sole member is created.
     *
     * @see <a href="https://redis.io/commands/zincrby/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param increment The score increment.
     * @param member A member of the sorted set.
     * @return The new score of <code>member</code>.
     * @example
     *     <pre>{@code
     * Double score = client.zincrby("mySortedSet", -3.14, "value").get();
     * assert score > 0; // member "value" existed in the set before score was altered
     * }</pre>
     */
    CompletableFuture<Double> zincrby(String key, double increment, String member);

    /**
     * Increments the score of <code>member</code> in the sorted set stored at <code>key</code> by
     * <code>increment</code>.<br>
     * If <code>member</code> does not exist in the sorted set, it is added with <code>increment
     * </code> as its score. If <code>key</code> does not exist, a new sorted set with the specified
     * member as its sole member is created.
     *
     * @see <a href="https://redis.io/commands/zincrby/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param increment The score increment.
     * @param member A member of the sorted set.
     * @return The new score of <code>member</code>.
     * @example
     *     <pre>{@code
     * Double score = client.zincrby(gs("mySortedSet"), -3.14, gs("value")).get();
     * assert score > 0; // member "value" existed in the set before score was altered
     * }</pre>
     */
    CompletableFuture<Double> zincrby(GlideString key, double increment, GlideString member);

    /**
     * Returns the cardinality of the intersection of the sorted sets specified by <code>keys</code>.
     *
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/commands/zintercard/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets to intersect.
     * @return The cardinality of the intersection of the given sorted sets.
     * @example
     *     <pre>{@code
     * Long length = client.zintercard(new String[] {"mySortedSet1", "mySortedSet2"}).get();
     * assert length == 3L;
     * }</pre>
     */
    CompletableFuture<Long> zintercard(String[] keys);

    /**
     * Returns the cardinality of the intersection of the sorted sets specified by <code>keys</code>.
     *
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/commands/zintercard/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets to intersect.
     * @return The cardinality of the intersection of the given sorted sets.
     * @example
     *     <pre>{@code
     * Long length = client.zintercard(new GlideString[] {gs("mySortedSet1"), gs("mySortedSet2")}).get();
     * assert length == 3L;
     * }</pre>
     */
    CompletableFuture<Long> zintercard(GlideString[] keys);

    /**
     * Returns the cardinality of the intersection of the sorted sets specified by <code>keys</code>.
     * If the intersection cardinality reaches <code>limit</code> partway through the computation, the
     * algorithm will exit early and yield <code>limit</code> as the cardinality.
     *
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/commands/zintercard/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets to intersect.
     * @param limit Specifies a maximum number for the intersection cardinality. If limit is set to
     *     <code>0</code> the range will be unlimited.
     * @return The cardinality of the intersection of the given sorted sets, or the <code>limit</code>
     *     if reached.
     * @example
     *     <pre>{@code
     * Long length = client.zintercard(new String[] {"mySortedSet1", "mySortedSet2"}, 5).get();
     * assert length == 3L;
     * }</pre>
     */
    CompletableFuture<Long> zintercard(String[] keys, long limit);

    /**
     * Returns the cardinality of the intersection of the sorted sets specified by <code>keys</code>.
     * If the intersection cardinality reaches <code>limit</code> partway through the computation, the
     * algorithm will exit early and yield <code>limit</code> as the cardinality.
     *
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/commands/zintercard/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets to intersect.
     * @param limit Specifies a maximum number for the intersection cardinality. If limit is set to
     *     <code>0</code> the range will be unlimited.
     * @return The cardinality of the intersection of the given sorted sets, or the <code>limit</code>
     *     if reached.
     * @example
     *     <pre>{@code
     * Long length = client.zintercard(new GlideString[] {gs("mySortedSet1"), gs("mySortedSet2")}, 5).get();
     * assert length == 3L;
     * }</pre>
     */
    CompletableFuture<Long> zintercard(GlideString[] keys, long limit);

    /**
     * Iterates incrementally over a sorted set.
     *
     * @see <a href="https://valkey.io/commands/zscan">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>"0"
     *     </code> indicates the start of the search.
     * @return An <code>Array</code> of <code>Objects</code>. The first element is always the <code>
     *     cursor</code> for the next iteration of results. <code>"0"</code> will be the <code>cursor
     *     </code> returned on the last iteration of the sorted set. The second element is always an
     *     <code>
     *     Array</code> of the subset of the sorted set held in <code>key</code>. The array in the
     *     second element is always a flattened series of <code>String</code> pairs, where the value
     *     is at even indices and the score is at odd indices.
     * @example
     *     <pre>{@code
     * // Assume key contains a set with 200 member-score pairs
     * String cursor = "0";
     * Object[] result;
     * do {
     *   result = client.zscan(key1, cursor).get();
     *   cursor = result[0].toString();
     *   Object[] stringResults = (Object[]) result[1];
     *
     *   System.out.println("\nZSCAN iteration:");
     *   for (int i = 0; i < stringResults.length; i += 2) {
     *     System.out.printf("{%s=%s}", stringResults[i], stringResults[i + 1]);
     *     if (i + 2 < stringResults.length) {
     *       System.out.print(", ");
     *     }
     *   }
     * } while (!cursor.equals("0"));
     * }</pre>
     */
    CompletableFuture<Object[]> zscan(String key, String cursor);

    /**
     * Iterates incrementally over a sorted set.
     *
     * @see <a href="https://valkey.io/commands/zscan">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>"0"
     *     </code> indicates the start of the search.
     * @return An <code>Array</code> of <code>Objects</code>. The first element is always the <code>
     *     cursor</code> for the next iteration of results. <code>"0"</code> will be the <code>cursor
     *     </code> returned on the last iteration of the sorted set. The second element is always an
     *     <code>
     *     Array</code> of the subset of the sorted set held in <code>key</code>. The array in the
     *     second element is always a flattened series of <code>String</code> pairs, where the value
     *     is at even indices and the score is at odd indices.
     * @example
     *     <pre>{@code
     * // Assume key contains a set with 200 member-score pairs
     * GlideString cursor = gs("0");
     * Object[] result;
     * do {
     *   result = client.zscan(key1, cursor).get();
     *   cursor = gs(result[0].toString());
     *   Object[] glideStringResults = (Object[]) result[1];
     *
     *   System.out.println("\nZSCAN iteration:");
     *   for (int i = 0; i < glideStringResults.length; i += 2) {
     *     System.out.printf("{%s=%s}", glideStringResults[i], glideStringResults[i + 1]);
     *     if (i + 2 < glideStringResults.length) {
     *       System.out.print(", ");
     *     }
     *   }
     * } while (!cursor.equals(gs("0")));
     * }</pre>
     */
    CompletableFuture<Object[]> zscan(GlideString key, GlideString cursor);

    /**
     * Iterates incrementally over a sorted set.
     *
     * @see <a href="https://valkey.io/commands/zscan">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>"0"
     *     </code> indicates the start of the search.
     * @param zScanOptions The {@link ZScanOptions}.
     * @return An <code>Array</code> of <code>Objects</code>. The first element is always the <code>
     *     cursor</code> for the next iteration of results. <code>"0"</code> will be the <code>cursor
     *     </code> returned on the last iteration of the sorted set. The second element is always an
     *     <code>
     *     Array</code> of the subset of the sorted set held in <code>key</code>. The array in the
     *     second element is always a flattened series of <code>String</code> pairs, where the value
     *     is at even indices and the score is at odd indices.
     * @example
     *     <pre>{@code
     * // Assume key contains a set with 200 member-score pairs
     * String cursor = "0";
     * Object[] result;
     * do {
     *   result = client.zscan(key1, cursor, ZScanOptions.builder().matchPattern("*").count(20L).build()).get();
     *   cursor = result[0].toString();
     *   Object[] stringResults = (Object[]) result[1];
     *
     *   System.out.println("\nZSCAN iteration:");
     *   for (int i = 0; i < stringResults.length; i += 2) {
     *     System.out.printf("{%s=%s}", stringResults[i], stringResults[i + 1]);
     *     if (i + 2 < stringResults.length) {
     *       System.out.print(", ");
     *     }
     *   }
     * } while (!cursor.equals("0"));
     * }</pre>
     */
    CompletableFuture<Object[]> zscan(String key, String cursor, ZScanOptions zScanOptions);

    /**
     * Iterates incrementally over a sorted set.
     *
     * @see <a href="https://valkey.io/commands/zscan">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>"0"
     *     </code> indicates the start of the search.
     * @param zScanOptions The {@link ZScanOptions}.
     * @return An <code>Array</code> of <code>Objects</code>. The first element is always the <code>
     *     cursor</code> for the next iteration of results. <code>"0"</code> will be the <code>cursor
     *     </code> returned on the last iteration of the sorted set. The second element is always an
     *     <code>
     *     Array</code> of the subset of the sorted set held in <code>key</code>. The array in the
     *     second element is always a flattened series of <code>String</code> pairs, where the value
     *     is at even indices and the score is at odd indices.
     * @example
     *     <pre>{@code
     * // Assume key contains a set with 200 member-score pairs
     * GlideString cursor = gs("0");
     * Object[] result;
     * do {
     *   result = client.zscan(key1, cursor, ZScanOptionsBinary.builder().matchPattern(gs("*")).count(20L).build()).get();
     *   cursor = gs(result[0].toString());
     *   Object[] glideStringResults = (Object[]) result[1];
     *
     *   System.out.println("\nZSCAN iteration:");
     *   for (int i = 0; i < glideStringResults.length; i += 2) {
     *     System.out.printf("{%s=%s}", glideStringResults[i], glideStringResults[i + 1]);
     *     if (i + 2 < glideStringResults.length) {
     *       System.out.print(", ");
     *     }
     *   }
     * } while (!cursor.equals(gs("0")));
     * }</pre>
     */
    CompletableFuture<Object[]> zscan(
            GlideString key, GlideString cursor, ZScanOptionsBinary zScanOptions);
}
