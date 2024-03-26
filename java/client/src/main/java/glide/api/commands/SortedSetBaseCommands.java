/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

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
     * assert payload.equals(Map.of('member2', 8.0, 'member3', 7.5)); // Indicates that 'member1' with a score of 10.0 has been removed from the sorted set.
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
}
