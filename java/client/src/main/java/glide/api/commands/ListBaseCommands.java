/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "List Commands" group for standalone clients and
 * cluster clients.
 *
 * @see <a href="https://redis.io/commands/?group=list">List Commands</a>
 */
public interface ListBaseCommands {

    /**
     * Inserts all the specified values at the head of the list stored at <code>key</code>. <code>
     * elements</code> are inserted one after the other to the head of the list, from the leftmost
     * element to the rightmost element. If <code>key</code> does not exist, it is created as an empty
     * list before performing the push operations.
     *
     * @see <a href="https://redis.io/commands/lpush/">redis.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to insert at the head of the list stored at <code>key</code>.
     * @return The length of the list after the push operations.
     * @example
     *     <pre>{@code
     * Long pushCount1 = client.lpush("my_list", new String[] {"value1", "value2"}).get();
     * assert pushCount1 == 2L;
     *
     * Long pushCount2 = client.lpush("nonexistent_list", new String[] {"new_value"}).get();
     * assert pushCount2 == 1L;
     * }</pre>
     */
    CompletableFuture<Long> lpush(String key, String[] elements);

    /**
     * Removes and returns the first elements of the list stored at <code>key</code>. The command pops
     * a single element from the beginning of the list.
     *
     * @see <a href="https://redis.io/commands/lpop/">redis.io</a> for details.
     * @param key The key of the list.
     * @return The value of the first element.<br>
     *     If <code>key</code> does not exist, null will be returned.
     * @example
     *     <pre>{@code
     * String value1 = client.lpop("my_list").get();
     * assert value1.equals("value1");
     *
     * String value2 = client.lpop("non_exiting_key").get();
     * assert value2.equals(null);
     * }</pre>
     */
    CompletableFuture<String> lpop(String key);

    /**
     * Removes and returns up to <code>count</code> elements of the list stored at <code>key</code>,
     * depending on the list's length.
     *
     * @see <a href="https://redis.io/commands/lpop/">redis.io</a> for details.
     * @param key The key of the list.
     * @param count The count of the elements to pop from the list.
     * @return An array of the popped elements will be returned depending on the list's length.<br>
     *     If <code>key</code> does not exist, null will be returned.
     * @example
     *     <pre>{@code
     * String[] values1 = client.lpopCount("my_list", 2).get();
     * assert values1.equals(new String[] {"value1", "value2"});
     *
     * String[] values2 = client.lpopCount("non_exiting_key" , 7).get();
     * assert values2.equals(null);
     * }</pre>
     */
    CompletableFuture<String[]> lpopCount(String key, long count);

    /**
     * Returns the specified elements of the list stored at <code>key</code>.<br>
     * The offsets <code>start</code> and <code>end</code> are zero-based indexes, with <code>0</code>
     * being the first element of the list, <code>1</code> being the next element and so on. These
     * offsets can also be negative numbers indicating offsets starting at the end of the list, with
     * <code>-1</code> being the last element of the list, <code>-2</code> being the penultimate, and
     * so on.
     *
     * @see <a href="https://redis.io/commands/lrange/">redis.io</a> for details.
     * @param key The key of the list.
     * @param start The starting point of the range.
     * @param end The end of the range.
     * @return Array of elements in the specified range.<br>
     *     If <code>start</code> exceeds the end of the list, or if <code>start</code> is greater than
     *     <code>end</code>, an empty array will be returned.<br>
     *     If <code>end</code> exceeds the actual end of the list, the range will stop at the actual
     *     end of the list.<br>
     *     If <code>key</code> does not exist an empty array will be returned.
     * @example
     *     <pre>{@code
     * String[] payload = lient.lrange("my_list", 0, 2).get();
     * assert payload.equals(new String[] {"value1", "value2", "value3"});
     *
     * String[] payload = client.lrange("my_list", -2, -1).get();
     * assert payload.equals(new String[] {"value2", "value3"});
     *
     * String[] payload = client.lrange("non_exiting_key", 0, 2).get();
     * assert payload.equals(new String[] {});
     * }</pre>
     */
    CompletableFuture<String[]> lrange(String key, long start, long end);

    /**
     * Trims an existing list so that it will contain only the specified range of elements specified.
     * <br>
     * The offsets <code>start</code> and <code>end</code> are zero-based indexes, with 0 being the
     * first element of the list, 1 being the next element and so on.<br>
     * These offsets can also be negative numbers indicating offsets starting at the end of the list,
     * with -1 being the last element of the list, -2 being the penultimate, and so on.
     *
     * @see <a href="https://redis.io/commands/ltrim/">redis.io</a> for details.
     * @param key The key of the list.
     * @param start The starting point of the range.
     * @param end The end of the range.
     * @return Always <code>OK</code>.<br>
     *     If <code>start</code> exceeds the end of the list, or if <code>start</code> is greater than
     *     <code>end</code>, the result will be an empty list (which causes key to be removed).<br>
     *     If <code>end</code> exceeds the actual end of the list, it will be treated like the last
     *     element of the list.<br>
     *     If <code>key</code> does not exist, OK will be returned without changes to the database.
     * @example
     *     <pre>{@code
     * String payload = client.ltrim("my_list", 0, 1).get();
     * assert payload.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> ltrim(String key, long start, long end);

    /**
     * Returns the length of the list stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/llen/">redis.io</a> for details.
     * @param key The key of the list.
     * @return The length of the list at <code>key</code>.<br>
     *     If <code>key</code> does not exist, it is interpreted as an empty list and <code>0</code>
     *     is returned.
     * @example
     *     <pre>{@code
     * Long lenList = client.llen("my_list").get();
     * assert lenList == 3L //Indicates that there are 3 elements in the list.;
     * }</pre>
     */
    CompletableFuture<Long> llen(String key);

    /**
     * Removes the first <code>count</code> occurrences of elements equal to <code>element</code> from
     * the list stored at <code>key</code>.<br>
     * If <code>count</code> is positive: Removes elements equal to <code>element</code> moving from
     * head to tail.<br>
     * If <code>count</code> is negative: Removes elements equal to <code>element</code> moving from
     * tail to head.<br>
     * If <code>count</code> is 0 or <code>count</code> is greater than the occurrences of elements
     * equal to <code>element</code>, it removes all elements equal to <code>element</code>.
     *
     * @see <a href="https://redis.io/commands/lrem/">redis.io</a> for details.
     * @param key The key of the list.
     * @param count The count of the occurrences of elements equal to <code>element</code> to remove.
     * @param element The element to remove from the list.
     * @return The number of the removed elements.<br>
     *     If <code>key</code> does not exist, <code>0</code> is returned.
     * @example
     *     <pre>{@code
     * Long num = client.rem("my_list", 2, "value").get();
     * assert num == 2L;
     * }</pre>
     */
    CompletableFuture<Long> lrem(String key, long count, String element);

    /**
     * Inserts all the specified values at the tail of the list stored at <code>key</code>.<br>
     * <code>elements</code> are inserted one after the other to the tail of the list, from the
     * leftmost element to the rightmost element. If <code>key</code> does not exist, it is created as
     * an empty list before performing the push operations.
     *
     * @see <a href="https://redis.io/commands/rpush/">redis.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to insert at the tail of the list stored at <code>key</code>.
     * @return The length of the list after the push operations.
     * @example
     *     <pre>{@code
     * Long pushCount1 = client.rpush("my_list", new String[] {"value1", "value2"}).get();
     * assert pushCount1 == 2L;
     *
     * Long pushCount2 = client.rpush("nonexistent_list", new String[] {"new_value"}).get();
     * assert pushCount2 == 1L;
     * }</pre>
     */
    CompletableFuture<Long> rpush(String key, String[] elements);

    /**
     * Removes and returns the last elements of the list stored at <code>key</code>.<br>
     * The command pops a single element from the end of the list.
     *
     * @see <a href="https://redis.io/commands/rpop/">redis.io</a> for details.
     * @param key The key of the list.
     * @return The value of the last element.<br>
     *     If <code>key</code> does not exist, <code>null</code> will be returned.
     * @example
     *     <pre>{@code
     * String value1 = client.rpop("my_list").get();
     * assert value1.equals("value1");
     *
     * String value2 = client.rpop("non_exiting_key").get();
     * assert value2.equals(null);
     * }</pre>
     */
    CompletableFuture<String> rpop(String key);

    /**
     * Removes and returns up to <code>count</code> elements from the list stored at <code>key</code>,
     * depending on the list's length.
     *
     * @see <a href="https://redis.io/commands/rpop/">redis.io</a> for details.
     * @param count The count of the elements to pop from the list.
     * @return An array of popped elements will be returned depending on the list's length.<br>
     *     If <code>key</code> does not exist, <code>null</code> will be returned.
     * @example
     *     <pre>{@code
     * String[] values1 = client.rpopCount("my_list", 2).get();
     * assert values1.equals(new String[] {"value1", "value2"});
     *
     * String[] values2 = client.rpopCount("non_exiting_key" , 7).get();
     * assert values2.equals(null);
     * }</pre>
     */
    CompletableFuture<String[]> rpopCount(String key, long count);
}
