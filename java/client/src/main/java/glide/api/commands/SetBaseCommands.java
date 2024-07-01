/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.GlideString;
import glide.api.models.commands.scan.SScanOptions;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Set Commands" group for standalone and cluster
 * clients.
 *
 * @see <a href="https://redis.io/commands/?group=set">Set Commands</a>
 */
public interface SetBaseCommands {
    /** Redis API keyword used to limit calculation of intersection of sorted sets. */
    String SET_LIMIT_REDIS_API = "LIMIT";

    /**
     * Adds specified members to the set stored at <code>key</code>. Specified members that are
     * already a member of this set are ignored.
     *
     * @see <a href="https://redis.io/commands/sadd/">redis.io</a> for details.
     * @param key The <code>key</code> where members will be added to its set.
     * @param members A list of members to add to the set stored at <code>key</code>.
     * @return The number of members that were added to the set, excluding members already present.
     * @remarks If <code>key</code> does not exist, a new set is created before adding <code>members
     *     </code>.
     * @example
     *     <pre>{@code
     * Long result = client.sadd("my_set", new String[]{"member1", "member2"}).get();
     * assert result == 2L;
     * }</pre>
     */
    CompletableFuture<Long> sadd(String key, String[] members);

    /**
     * Adds specified members to the set stored at <code>key</code>. Specified members that are
     * already a member of this set are ignored.
     *
     * @see <a href="https://redis.io/commands/sadd/">redis.io</a> for details.
     * @param key The <code>key</code> where members will be added to its set.
     * @param members A list of members to add to the set stored at <code>key</code>.
     * @return The number of members that were added to the set, excluding members already present.
     * @remarks If <code>key</code> does not exist, a new set is created before adding <code>members
     *     </code>.
     * @example
     *     <pre>{@code
     * Long result = client.sadd(gs("my_set"), new GlideString[]{gs("member1"), gs("member2")}).get();
     * assert result == 2L;
     * }</pre>
     */
    CompletableFuture<Long> sadd(GlideString key, GlideString[] members);

    /**
     * Removes specified members from the set stored at <code>key</code>. Specified members that are
     * not a member of this set are ignored.
     *
     * @see <a href="https://redis.io/commands/srem/">redis.io</a> for details.
     * @param key The <code>key</code> from which members will be removed.
     * @param members A list of members to remove from the set stored at <code>key</code>.
     * @return The number of members that were removed from the set, excluding non-existing members.
     * @remarks If <code>key</code> does not exist, it is treated as an empty set and this command
     *     returns <code>0</code>.
     * @example
     *     <pre>{@code
     * Long result = client.srem("my_set", new String[]{"member1", "member2"}).get();
     * assert result == 2L;
     * }</pre>
     */
    CompletableFuture<Long> srem(String key, String[] members);

    /**
     * Removes specified members from the set stored at <code>key</code>. Specified members that are
     * not a member of this set are ignored.
     *
     * @see <a href="https://redis.io/commands/srem/">redis.io</a> for details.
     * @param key The <code>key</code> from which members will be removed.
     * @param members A list of members to remove from the set stored at <code>key</code>.
     * @return The number of members that were removed from the set, excluding non-existing members.
     * @remarks If <code>key</code> does not exist, it is treated as an empty set and this command
     *     returns <code>0</code>.
     * @example
     *     <pre>{@code
     * Long result = client.srem(gs("my_set"), new GlideString[]{gs("member1"), gs("member2")}).get();
     * assert result == 2L;
     * }</pre>
     */
    CompletableFuture<Long> srem(GlideString key, GlideString[] members);

    /**
     * Retrieves all the members of the set value stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/smembers/">redis.io</a> for details.
     * @param key The key from which to retrieve the set members.
     * @return A <code>Set</code> of all members of the set.
     * @remarks If <code>key</code> does not exist an empty set will be returned.
     * @example
     *     <pre>{@code
     * Set<String> result = client.smembers("my_set").get();
     * assert result.equals(Set.of("member1", "member2", "member3"));
     * }</pre>
     */
    CompletableFuture<Set<String>> smembers(String key);

    /**
     * Retrieves all the members of the set value stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/smembers/">redis.io</a> for details.
     * @param key The key from which to retrieve the set members.
     * @return A <code>Set</code> of all members of the set.
     * @remarks If <code>key</code> does not exist an empty set will be returned.
     * @example
     *     <pre>{@code
     * Set<String> result = client.smembers(gs("my_set")).get();
     * assert result.equals(Set.of(gs("member1"), gs("member2"), gs("member3")));
     * }</pre>
     */
    CompletableFuture<Set<GlideString>> smembers(GlideString key);

    /**
     * Retrieves the set cardinality (number of elements) of the set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/scard/">redis.io</a> for details.
     * @param key The key from which to retrieve the number of set members.
     * @return The cardinality (number of elements) of the set, or 0 if the key does not exist.
     * @example
     *     <pre>{@code
     * Long result = client.scard("my_set").get();
     * assert result == 3L;
     * }</pre>
     */
    CompletableFuture<Long> scard(String key);

    /**
     * Retrieves the set cardinality (number of elements) of the set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/scard/">redis.io</a> for details.
     * @param key The key from which to retrieve the number of set members.
     * @return The cardinality (number of elements) of the set, or 0 if the key does not exist.
     * @example
     *     <pre>{@code
     * Long result = client.scard("my_set").get();
     * assert result == 3L;
     * }</pre>
     */
    CompletableFuture<Long> scard(GlideString key);

    /**
     * Checks whether each member is contained in the members of the set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/smismember/">redis.io</a> for details.
     * @param key The key of the set to check.
     * @param members A list of members to check for existence in the set.
     * @return An <code>array</code> of <code>Boolean</code> values, each indicating if the respective
     *     member exists in the set.
     * @example
     *     <pre>{@code
     * Boolean[] areMembers = client.smismembmer("my_set", new String[] { "a", "b", "c" }).get();
     * assert areMembers[0] && areMembers[1] && !areMembers[2]; // Only first two elements are present in "my_set"
     * }</pre>
     */
    CompletableFuture<Boolean[]> smismember(String key, String[] members);

    /**
     * Checks whether each member is contained in the members of the set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/smismember/">redis.io</a> for details.
     * @param key The key of the set to check.
     * @param members A list of members to check for existence in the set.
     * @return An <code>array</code> of <code>Boolean</code> values, each indicating if the respective
     *     member exists in the set.
     * @example
     *     <pre>{@code
     * Boolean[] areMembers = client.smismembmer(gs("my_set"), new GlideString[] { gs("a"), gs("b"), gs("c") }).get();
     * assert areMembers[0] && areMembers[1] && !areMembers[2]; // Only first two elements are present in "my_set"
     * }</pre>
     */
    CompletableFuture<Boolean[]> smismember(GlideString key, GlideString[] members);

    /**
     * Moves <code>member</code> from the set at <code>source</code> to the set at <code>destination
     * </code>, removing it from the source set. Creates a new destination set if needed. The
     * operation is atomic.
     *
     * @apiNote When in cluster mode, both <code>source</code> and <code>destination</code> must map
     *     to the same hash slot.
     * @see <a href="https://redis.io/commands/smove/">redis.io</a> for details.
     * @param source The key of the set to remove the element from.
     * @param destination The key of the set to add the element to.
     * @param member The set element to move.
     * @return <code>true</code> on success, or <code>false</code> if the <code>source</code> set does
     *     not exist or the element is not a member of the source set.
     * @example
     *     <pre>{@code
     * Boolean moved = client.smove("set1", "set2", "element").get();
     * assert moved;
     * }</pre>
     */
    CompletableFuture<Boolean> smove(String source, String destination, String member);

    /**
     * Moves <code>member</code> from the set at <code>source</code> to the set at <code>destination
     * </code>, removing it from the source set. Creates a new destination set if needed. The
     * operation is atomic.
     *
     * @apiNote When in cluster mode, both <code>source</code> and <code>destination</code> must map
     *     to the same hash slot.
     * @see <a href="https://redis.io/commands/smove/">redis.io</a> for details.
     * @param source The key of the set to remove the element from.
     * @param destination The key of the set to add the element to.
     * @param member The set element to move.
     * @return <code>true</code> on success, or <code>false</code> if the <code>source</code> set does
     *     not exist or the element is not a member of the source set.
     * @example
     *     <pre>{@code
     * Boolean moved = client.smove(gs("set1"), gs("set2"), gs("element")).get();
     * assert moved;
     * }</pre>
     */
    CompletableFuture<Boolean> smove(GlideString source, GlideString destination, GlideString member);

    /**
     * Returns if <code>member</code> is a member of the set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/sismember/">redis.io</a> for details.
     * @param key The key of the set.
     * @param member The member to check for existence in the set.
     * @return <code>true</code> if the member exists in the set, <code>false</code> otherwise. If
     *     <code>key</code> doesn't exist, it is treated as an <code>empty set</code> and the command
     *     returns <code>false</code>.
     * @example
     *     <pre>{@code
     * Boolean payload1 = client.sismember("mySet", "member1").get();
     * assert payload1; // Indicates that "member1" exists in the set "mySet".
     *
     * Boolean payload2 = client.sismember("mySet", "nonExistingMember").get();
     * assert !payload2; // Indicates that "nonExistingMember" does not exist in the set "mySet".
     * }</pre>
     */
    CompletableFuture<Boolean> sismember(String key, String member);

    /**
     * Returns if <code>member</code> is a member of the set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/sismember/">redis.io</a> for details.
     * @param key The key of the set.
     * @param member The member to check for existence in the set.
     * @return <code>true</code> if the member exists in the set, <code>false</code> otherwise. If
     *     <code>key</code> doesn't exist, it is treated as an <code>empty set</code> and the command
     *     returns <code>false</code>.
     * @example
     *     <pre>{@code
     * Boolean payload1 = client.sismember(gs("mySet"), gs("member1")).get();
     * assert payload1; // Indicates that "member1" exists in the set "mySet".
     *
     * Boolean payload2 = client.sismember(gs("mySet"), gs("nonExistingMember")).get();
     * assert !payload2; // Indicates that "nonExistingMember" does not exist in the set "mySet".
     * }</pre>
     */
    CompletableFuture<Boolean> sismember(GlideString key, GlideString member);

    /**
     * Computes the difference between the first set and all the successive sets in <code>keys</code>.
     *
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @see <a href="https://redis.io/commands/sdiff/">redis.io</a> for details.
     * @param keys The keys of the sets to diff.
     * @return A <code>Set</code> of elements representing the difference between the sets.<br>
     *     If the a <code>key</code> does not exist, it is treated as an empty set.
     * @example
     *     <pre>{@code
     * Set<String> values = client.sdiff(new String[] {"set1", "set2"}).get();
     * assert values.contains("element"); // Indicates that "element" is present in "set1", but missing in "set2"
     * }</pre>
     */
    CompletableFuture<Set<String>> sdiff(String[] keys);

    /**
     * Computes the difference between the first set and all the successive sets in <code>keys</code>.
     *
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @see <a href="https://redis.io/commands/sdiff/">redis.io</a> for details.
     * @param keys The keys of the sets to diff.
     * @return A <code>Set</code> of elements representing the difference between the sets.<br>
     *     If the a <code>key</code> does not exist, it is treated as an empty set.
     * @example
     *     <pre>{@code
     * Set<GlideString> values = client.sdiff(new GlideString[] {gs("set1"), gs("set2")}).get();
     * assert values.contains(gs("element")); // Indicates that "element" is present in "set1", but missing in "set2"
     * }</pre>
     */
    CompletableFuture<Set<GlideString>> sdiff(GlideString[] keys);

    /**
     * Stores the difference between the first set and all the successive sets in <code>keys</code>
     * into a new set at <code>destination</code>.
     *
     * @apiNote When in cluster mode, <code>destination</code> and all <code>keys</code> must map to
     *     the same hash slot.
     * @see <a href="https://redis.io/commands/sdiffstore/">redis.io</a> for details.
     * @param destination The key of the destination set.
     * @param keys The keys of the sets to diff.
     * @return The number of elements in the resulting set.
     * @example
     *     <pre>{@code
     * Long length = client.sdiffstore("mySet", new String[] { "set1", "set2" }).get();
     * assert length == 5L;
     * }</pre>
     */
    CompletableFuture<Long> sdiffstore(String destination, String[] keys);

    /**
     * Stores the difference between the first set and all the successive sets in <code>keys</code>
     * into a new set at <code>destination</code>.
     *
     * @apiNote When in cluster mode, <code>destination</code> and all <code>keys</code> must map to
     *     the same hash slot.
     * @see <a href="https://redis.io/commands/sdiffstore/">redis.io</a> for details.
     * @param destination The key of the destination set.
     * @param keys The keys of the sets to diff.
     * @return The number of elements in the resulting set.
     * @example
     *     <pre>{@code
     * Long length = client.sdiffstore(gs("mySet"), new GlideString[] { gs("set1"), gs("set2") }).get();
     * assert length == 5L;
     * }</pre>
     */
    CompletableFuture<Long> sdiffstore(GlideString destination, GlideString[] keys);

    /**
     * Gets the intersection of all the given sets.
     *
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @see <a href="https://redis.io/commands/sinter/">redis.io</a> for details.
     * @param keys The keys of the sets.
     * @return A <code>Set</code> of members which are present in all given sets.<br>
     *     If one or more sets do not exist, an empty set will be returned.
     * @example
     *     <pre>{@code
     * Set<String> values = client.sinter(new String[] {"set1", "set2"}).get();
     * assert values.contains("element"); // Indicates that these sets have a common element
     *
     * Set<String> values = client.sinter(new String[] {"set1", "nonExistingSet"}).get();
     * assert values.size() == 0;
     * }</pre>
     */
    CompletableFuture<Set<String>> sinter(String[] keys);

    /**
     * Gets the intersection of all the given sets.
     *
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @see <a href="https://redis.io/commands/sinter/">redis.io</a> for details.
     * @param keys The keys of the sets.
     * @return A <code>Set</code> of members which are present in all given sets.<br>
     *     If one or more sets do not exist, an empty set will be returned.
     * @example
     *     <pre>{@code
     * Set<GlideString> values = client.sinter(new GlideString[] {gs("set1"), gs("set2")}).get();
     * assert values.contains(gs("element")); // Indicates that these sets have a common element
     *
     * Set<GlideString> values = client.sinter(new GlideString[] {gs("set1"), gs("nonExistingSet")}).get();
     * assert values.size() == 0;
     * }</pre>
     */
    CompletableFuture<Set<GlideString>> sinter(GlideString[] keys);

    /**
     * Gets the cardinality of the intersection of all the given sets.
     *
     * @since Redis 7.0 and above.
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @see <a href="https://redis.io/commands/sintercard/">redis.io</a> for details.
     * @param keys The keys of the sets.
     * @return The cardinality of the intersection result. If one or more sets do not exist, <code>0
     *     </code> is returned.
     * @example
     *     <pre>{@code
     * Long response = client.sintercard(new String[] {"set1", "set2"}).get();
     * assertEquals(2L, response);
     *
     * Long emptyResponse = client.sintercard(new String[] {"set1", "nonExistingSet"}).get();
     * assertEquals(emptyResponse, 0L);
     * }</pre>
     */
    CompletableFuture<Long> sintercard(String[] keys);

    /**
     * Gets the cardinality of the intersection of all the given sets.
     *
     * @since Redis 7.0 and above.
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @see <a href="https://redis.io/commands/sintercard/">redis.io</a> for details.
     * @param keys The keys of the sets.
     * @return The cardinality of the intersection result. If one or more sets do not exist, <code>0
     *     </code> is returned.
     * @example
     *     <pre>{@code
     * Long response = client.sintercard(new GlideString[] {gs("set1"), gs("set2")}).get();
     * assertEquals(2L, response);
     *
     * Long emptyResponse = client.sintercard(new GlideString[] {gs("set1"), gs("nonExistingSet")}).get();
     * assertEquals(emptyResponse, 0L);
     * }</pre>
     */
    CompletableFuture<Long> sintercard(GlideString[] keys);

    /**
     * Gets the cardinality of the intersection of all the given sets.
     *
     * @since Redis 7.0 and above.
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @see <a href="https://redis.io/commands/sintercard/">redis.io</a> for details.
     * @param keys The keys of the sets.
     * @param limit The limit for the intersection cardinality value.
     * @return The cardinality of the intersection result. If one or more sets do not exist, <code>0
     *     </code> is returned. If the intersection cardinality reaches <code>limit</code> partway
     *     through the computation, returns <code>limit</code> as the cardinality.
     * @example
     *     <pre>{@code
     * Long response = client.sintercard(new String[] {"set1", "set2"}, 3).get();
     * assertEquals(2L, response);
     *
     * Long emptyResponse = client.sintercard(new String[] {"set1", "nonExistingSet"}, 3).get();
     * assertEquals(emptyResponse, 0L);
     *
     * // when intersection cardinality > limit, returns limit as cardinality
     * Long response2 = client.sintercard(new String[] {"set3", "set4"}, 3).get();
     * assertEquals(3L, response2);
     * }</pre>
     */
    CompletableFuture<Long> sintercard(String[] keys, long limit);

    /**
     * Gets the cardinality of the intersection of all the given sets.
     *
     * @since Redis 7.0 and above.
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @see <a href="https://redis.io/commands/sintercard/">redis.io</a> for details.
     * @param keys The keys of the sets.
     * @param limit The limit for the intersection cardinality value.
     * @return The cardinality of the intersection result. If one or more sets do not exist, <code>0
     *     </code> is returned. If the intersection cardinality reaches <code>limit</code> partway
     *     through the computation, returns <code>limit</code> as the cardinality.
     * @example
     *     <pre>{@code
     * Long response = client.sintercard(new GlideString[] {gs("set1"), gs("set2")}, 3).get();
     * assertEquals(2L, response);
     *
     * Long emptyResponse = client.sintercard(new GlideString[] {gs("set1"), gs("nonExistingSet")}, 3).get();
     * assertEquals(emptyResponse, 0L);
     *
     * // when intersection cardinality > limit, returns limit as cardinality
     * Long response2 = client.sintercard(new GlideString[] {gs("set3"), gs("set4")}, 3).get();
     * assertEquals(3L, response2);
     * }</pre>
     */
    CompletableFuture<Long> sintercard(GlideString[] keys, long limit);

    /**
     * Stores the members of the intersection of all given sets specified by <code>keys</code> into a
     * new set at <code>destination</code>.
     *
     * @apiNote When in cluster mode, <code>destination</code> and all <code>keys</code> must map to
     *     the same hash slot.
     * @see <a href="https://redis.io/commands/sinterstore/">redis.io</a> for details.
     * @param destination The key of the destination set.
     * @param keys The keys from which to retrieve the set members.
     * @return The number of elements in the resulting set.
     * @example
     *     <pre>{@code
     * Long length = client.sinterstore("mySet", new String[] { "set1", "set2" }).get();
     * assert length == 5L;
     * }</pre>
     */
    CompletableFuture<Long> sinterstore(String destination, String[] keys);

    /**
     * Stores the members of the intersection of all given sets specified by <code>keys</code> into a
     * new set at <code>destination</code>.
     *
     * @apiNote When in cluster mode, <code>destination</code> and all <code>keys</code> must map to
     *     the same hash slot.
     * @see <a href="https://redis.io/commands/sinterstore/">redis.io</a> for details.
     * @param destination The key of the destination set.
     * @param keys The keys from which to retrieve the set members.
     * @return The number of elements in the resulting set.
     * @example
     *     <pre>{@code
     * Long length = client.sinterstore(gs("mySet"), new GlideString[] { gs("set1"), gs("set2") }).get();
     * assert length == 5L;
     * }</pre>
     */
    CompletableFuture<Long> sinterstore(GlideString destination, GlideString[] keys);

    /**
     * Stores the members of the union of all given sets specified by <code>keys</code> into a new set
     * at <code>destination</code>.
     *
     * @apiNote When in cluster mode, <code>destination</code> and all <code>keys</code> must map to
     *     the same hash slot.
     * @see <a href="https://redis.io/commands/sunionstore/">redis.io</a> for details.
     * @param destination The key of the destination set.
     * @param keys The keys from which to retrieve the set members.
     * @return The number of elements in the resulting set.
     * @example
     *     <pre>{@code
     * Long length = client.sunionstore("mySet", new String[] { "set1", "set2" }).get();
     * assert length == 5L;
     * }</pre>
     */
    CompletableFuture<Long> sunionstore(String destination, String[] keys);

    /**
     * Returns a random element from the set value stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/srandmember/">redis.io</a> for details.
     * @param key The key from which to retrieve the set member.
     * @return A random element from the set, or <code>null</code> if <code>key</code> does not exist.
     * @example
     *     <pre>{@code
     * client.sadd("test", new String[] {"one"}).get();
     * String response = client.srandmember("test").get();
     * assertEquals("one", response);
     * }</pre>
     */
    CompletableFuture<String> srandmember(String key);

    /**
     * Returns one or more random elements from the set value stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/srandmember/">redis.io</a> for details.
     * @param key The key from which to retrieve the set members.
     * @param count The number of elements to return.<br>
     *     If <code>count</code> is positive, returns unique elements.<br>
     *     If negative, allows for duplicates.<br>
     * @return An <code>array</code> of elements from the set, or an empty <code>array</code> if
     *     <code>key</code> does not exist.
     * @example
     *     <pre>{@code
     * client.sadd("test", new String[] {"one"}).get();
     * String[] response = client.srandmember("test", -2).get();
     * assertArrayEquals(new String[] {"one", "one"}, response);
     * }</pre>
     */
    CompletableFuture<String[]> srandmember(String key, long count);

    /**
     * Removes and returns one random member from the set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/spop/">redis.io</a> for details.
     * @param key The key of the set.
     * @return The value of the popped member.<br>
     *     If <code>key</code> does not exist, <code>null</code> will be returned.
     * @example
     *     <pre>{@code
     * String value1 = client.spop("mySet").get();
     * assert value1.equals("value1");
     *
     * String value2 = client.spop("nonExistingSet").get();
     * assert value2.equals(null);
     * }</pre>
     */
    CompletableFuture<String> spop(String key);

    /**
     * Removes and returns up to <code>count</code> random members from the set stored at <code>key
     * </code>, depending on the set's length.
     *
     * @see <a href="https://redis.io/commands/spop/">redis.io</a> for details.
     * @param key The key of the set.
     * @param count The count of the elements to pop from the set.
     * @return A set of popped elements will be returned depending on the set's length.<br>
     *     If <code>key</code> does not exist, an empty <code>Set</code> will be returned.
     * @example
     *     <pre>{@code
     * Set<String> values1 = client.spopCount("mySet", 2).get();
     * assert values1.equals(new String[] {"value1", "value2"});
     *
     * Set<String> values2 = client.spopCount("nonExistingSet", 2).get();
     * assert values2.size() == 0;
     * }</pre>
     */
    CompletableFuture<Set<String>> spopCount(String key, long count);

    /**
     * Gets the union of all the given sets.
     *
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @see <a href="https://valkey.io/commands/sunion">valkey.io</a> for details.
     * @param keys The keys of the sets.
     * @return A set of members which are present in at least one of the given sets. If none of the
     *     sets exist, an empty set will be returned.
     * @example
     *     <pre>{@code
     * assert client.sadd("my_set1", new String[]{"member1", "member2"}).get() == 2;
     * assert client.sadd("my_set2", new String[]{"member2", "member3"}).get() == 2;
     * Set<String> result = client.sunion(new String[] {"my_set1", "my_set2"}).get();
     * assertEquals(Set.of("member1", "member2", "member3"), result);
     *
     * result = client.sunion(new String[] {"my_set1", "non_existent_set"}).get();
     * assertEquals(Set.of("member1", "member2"), result);
     * }</pre>
     */
    CompletableFuture<Set<String>> sunion(String[] keys);

    /**
     * Iterates incrementally over a set.
     *
     * @see <a href="https://valkey.io/commands/sscan">valkey.io</a> for details.
     * @param key The key of the set.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>"0"
     *     </code> indicates the start of the search.
     * @return An <code>Array</code> of <code>Objects</code>. The first element is always the <code>
     *     cursor</code> for the next iteration of results. <code>"0"</code> will be the <code>cursor
     *     </code> returned on the last iteration of the set. The second element is always an <code>
     *     Array</code> of the subset of the set held in <code>key</code>.
     * @example
     *     <pre>{@code
     * // Assume key contains a set with 200 members
     * String cursor = "0";
     * Object[] result;
     * do {
     *   result = client.sscan(key1, cursor).get();
     *   cursor = result[0].toString();
     *   Object[] stringResults = (Object[]) result[1];
     *
     *   System.out.println("\nSSCAN iteration:");
     *   Arrays.asList(stringResults).stream().forEach(i -> System.out.print(i + ", "));
     * } while (!cursor.equals("0"));
     * }</pre>
     */
    CompletableFuture<Object[]> sscan(String key, String cursor);

    /**
     * Iterates incrementally over a set.
     *
     * @see <a href="https://valkey.io/commands/sscan">valkey.io</a> for details.
     * @param key The key of the set.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>"0"
     *     </code> indicates the start of the search.
     * @param sScanOptions The {@link SScanOptions}.
     * @return An <code>Array</code> of <code>Objects</code>. The first element is always the <code>
     *     cursor</code> for the next iteration of results. <code>"0"</code> will be the <code>cursor
     *     </code> returned on the last iteration of the set. The second element is always an <code>
     *     Array</code> of the subset of the set held in <code>key</code>.
     * @example
     *     <pre>{@code
     * // Assume key contains a set with 200 members
     * String cursor = "0";
     * Object[] result;
     * do {
     *   result = client.sscan(key1, cursor, SScanOptions.builder().matchPattern("*").count(20L).build()).get();
     *   cursor = result[0].toString();
     *   Object[] stringResults = (Object[]) result[1];
     *
     *   System.out.println("\nSSCAN iteration:");
     *   Arrays.asList(stringResults).stream().forEach(i -> System.out.print(i + ", "));
     * } while (!cursor.equals("0"));
     * }</pre>
     */
    CompletableFuture<Object[]> sscan(String key, String cursor, SScanOptions sScanOptions);
}
