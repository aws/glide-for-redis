/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Set Commands" group for standalone clients and
 * cluster clients.
 *
 * @see <a href="https://redis.io/commands/?group=set">Set Commands</a>
 */
public interface SetBaseCommands {
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
}
