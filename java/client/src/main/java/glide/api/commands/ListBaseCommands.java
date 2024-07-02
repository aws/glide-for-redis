/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.GlideString;
import glide.api.models.commands.LInsertOptions.InsertPosition;
import glide.api.models.commands.LPosOptions;
import glide.api.models.commands.ListDirection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;

/**
 * Supports commands and transactions for the "List Commands" group for standalone and cluster
 * clients.
 *
 * @see <a href="https://redis.io/commands/?group=list">List Commands</a>
 */
public interface ListBaseCommands {
    /** Redis API keyword used to extract specific count of members from a sorted set. */
    String COUNT_FOR_LIST_REDIS_API = "COUNT";

    /**
     * Inserts all the specified values at the head of the list stored at <code>key</code>. <code>
     * elements</code> are inserted one after the other to the head of the list, from the leftmost
     * element to the rightmost element. If <code>key</code> does not exist, it is created as an empty
     * list before performing the push operation.
     *
     * @see <a href="https://redis.io/commands/lpush/">redis.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to insert at the head of the list stored at <code>key</code>.
     * @return The length of the list after the push operation.
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
     * Inserts all the specified values at the head of the list stored at <code>key</code>. <code>
     * elements</code> are inserted one after the other to the head of the list, from the leftmost
     * element to the rightmost element. If <code>key</code> does not exist, it is created as an empty
     * list before performing the push operation.
     *
     * @see <a href="https://redis.io/commands/lpush/">redis.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to insert at the head of the list stored at <code>key</code>.
     * @return The length of the list after the push operation.
     * @example
     *     <pre>{@code
     * Long pushCount1 = client.lpush(gs("my_list"), new GlideString[] {gs("value1"), gs("value2")}).get();
     * assert pushCount1 == 2L;
     *
     * Long pushCount2 = client.lpush(gs("nonexistent_list"), new GlideString[] {gs("new_value")}).get();
     * assert pushCount2 == 1L;
     * }</pre>
     */
    CompletableFuture<Long> lpush(GlideString key, GlideString[] elements);

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
     * Returns the index of the first occurrence of <code>element</code> inside the list specified by
     * <code>key</code>. If no match is found, <code>null</code> is returned.
     *
     * @since Redis 6.0.6.
     * @see <a href="https://redis.io/docs/latest/commands/lpos/">redis.io</a> for details.
     * @param key The name of the list.
     * @param element The value to search for within the list.
     * @return The index of the first occurrence of <code>element</code>, or <code>null</code> if
     *     <code>element</code> is not in the list.
     * @example
     *     <pre>{@code
     * Long listLen = client.rpush("my_list", new String[] {"a", "b", "c", "d", "e", "e"}).get();
     * Long position = client.lpos("my_list", "e").get();
     * assert position == 4L;
     * }</pre>
     */
    CompletableFuture<Long> lpos(String key, String element);

    /**
     * Returns the index of the first occurrence of <code>element</code> inside the list specified by
     * <code>key</code>. If no match is found, <code>null</code> is returned.
     *
     * @since Redis 6.0.6.
     * @see <a href="https://redis.io/docs/latest/commands/lpos/">redis.io</a> for details.
     * @param key The name of the list.
     * @param element The value to search for within the list.
     * @return The index of the first occurrence of <code>element</code>, or <code>null</code> if
     *     <code>element</code> is not in the list.
     * @example
     *     <pre>{@code
     * Long listLen = client.rpush(gs("my_list"), new GlideString[] {gs("a"), gs("b"), gs("c"), gs("d"), gs("e"), gs("e")}).get();
     * Long position = client.lpos(gs("my_list"), gs("e")).get();
     * assert position == 4L;
     * }</pre>
     */
    CompletableFuture<Long> lpos(GlideString key, GlideString element);

    /**
     * Returns the index of an occurrence of <code>element</code> within a list based on the given
     * <code>options</code>. If no match is found, <code>null</code> is returned.
     *
     * @since Redis 6.0.6.
     * @see <a href="https://redis.io/docs/latest/commands/lpos/">redis.io</a> for details.
     * @param key The name of the list.
     * @param element The value to search for within the list.
     * @param options The LPos options.
     * @return The index of <code>element</code>, or <code>null</code> if <code>element</code> is not
     *     in the list.
     * @example
     *     <pre>{@code
     * Long listLen = client.rpush("my_list", new String[] {"a", "b", "c", "d", "e", "e"}).get();
     *
     * // Returns the second occurrence of the element "e".
     * LPosOptions options1 = LPosOptions.builder().rank(2L).build();
     * Long position1 = client.lpos("my_list", "e", options1).get();
     * assert position1 == 5L;
     *
     * // rank and maxLength
     * LPosOptions options2 = LPosOptions.builder().rank(1L).maxLength(1000L).build();
     * Long position2 = client.lpos("my_list", "e", options2).get();
     * assert position2 == 4L;
     * }</pre>
     */
    CompletableFuture<Long> lpos(
            @NonNull String key, @NonNull String element, @NonNull LPosOptions options);

    /**
     * Returns the index of an occurrence of <code>element</code> within a list based on the given
     * <code>options</code>. If no match is found, <code>null</code> is returned.
     *
     * @since Redis 6.0.6.
     * @see <a href="https://redis.io/docs/latest/commands/lpos/">redis.io</a> for details.
     * @param key The name of the list.
     * @param element The value to search for within the list.
     * @param options The LPos options.
     * @return The index of <code>element</code>, or <code>null</code> if <code>element</code> is not
     *     in the list.
     * @example
     *     <pre>{@code
     * Long listLen = client.rpush(gs("my_list")), new GlideString[] {gs("a"), gs("b"), gs("c"), gs("d"), gs("e"), gs("e")}).get();
     *
     * // Returns the second occurrence of the element gs("e").
     * LPosOptions options1 = LPosOptions.builder().rank(2L).build();
     * Long position1 = client.lpos(gs("my_list"), gs("e"), options1).get();
     * assert position1 == 5L;
     *
     * // rank and maxLength
     * LPosOptions options2 = LPosOptions.builder().rank(1L).maxLength(1000L).build();
     * Long position2 = client.lpos(gs("my_list"), gs("e"), options2).get();
     * assert position2 == 4L;
     * }</pre>
     */
    CompletableFuture<Long> lpos(
            @NonNull GlideString key, @NonNull GlideString element, @NonNull LPosOptions options);

    /**
     * Returns an <code>array</code> of indices of matching elements within a list.
     *
     * @since Redis 6.0.6.
     * @see <a href="https://redis.io/docs/latest/commands/lpos/">redis.io</a> for details.
     * @param key The name of the list.
     * @param element The value to search for within the list.
     * @param count The number of matches wanted.
     * @return An <code>array</code> that holds the indices of the matching elements within the list.
     * @example
     *     <pre>{@code
     * Long listLen = client.rpush("my_list", new String[] {"a", "b", "c", "d", "e", "e", "e"}).get();
     * Long[] position = client.lposCount("my_list", "e", 3L).get());
     * assertArrayEquals(new Long[]{4L, 5L, 6L}, position);
     * }</pre>
     */
    CompletableFuture<Long[]> lposCount(@NonNull String key, @NonNull String element, long count);

    /**
     * Returns an <code>array</code> of indices of matching elements within a list.
     *
     * @since Redis 6.0.6.
     * @see <a href="https://redis.io/docs/latest/commands/lpos/">redis.io</a> for details.
     * @param key The name of the list.
     * @param element The value to search for within the list.
     * @param count The number of matches wanted.
     * @return An <code>array</code> that holds the indices of the matching elements within the list.
     * @example
     *     <pre>{@code
     * Long listLen = client.rpush(gs("my_list"), new GlideString[] {gs("a"), gs("b"), gs("c"), gs("d"), gs("e"), gs("e"), gs("e")}).get();
     * Long[] position = client.lposCount(gs("my_list"), gs("e"), 3L).get());
     * assertArrayEquals(new Long[]{4L, 5L, 6L}, position);
     * }</pre>
     */
    CompletableFuture<Long[]> lposCount(
            @NonNull GlideString key, @NonNull GlideString element, long count);

    /**
     * Returns an <code>array</code> of indices of matching elements within a list based on the given
     * <code>options</code>. If no match is found, an empty <code>array</code>is returned.
     *
     * @since Redis 6.0.6.
     * @see <a href="https://redis.io/docs/latest/commands/lpos/">redis.io</a> for details.
     * @param key The name of the list.
     * @param element The value to search for within the list.
     * @param count The number of matches wanted.
     * @param options The LPos options.
     * @return An <code>array</code> that holds the indices of the matching elements within the list.
     * @example
     *     <pre>{@code
     * Long listLen = client.rpush("my_list", new String[] {"a", "b", "c", "d", "e", "e", "e"}).get();
     *
     * // rank
     * LPosOptions options1 = LPosOptions.builder().rank(2L).build();
     * Long[] position1 = client.lposCount("my_list", "e", 1L, options1).get();
     * assertArrayEquals(new Long[]{5L}, position1);
     *
     * // rank and maxLength
     * LPosOptions options2 = LPosOptions.builder.rank(2L).maxLength(1000L).build();
     * Long[] position2 = client.lposCount("my_list", "e", 3L, options2).get();
     * assertArrayEquals(new Long[]{5L, 6L}, position2);
     * }</pre>
     */
    CompletableFuture<Long[]> lposCount(
            @NonNull String key, @NonNull String element, long count, @NonNull LPosOptions options);

    /**
     * Returns an <code>array</code> of indices of matching elements within a list based on the given
     * <code>options</code>. If no match is found, an empty <code>array</code>is returned.
     *
     * @since Redis 6.0.6.
     * @see <a href="https://redis.io/docs/latest/commands/lpos/">redis.io</a> for details.
     * @param key The name of the list.
     * @param element The value to search for within the list.
     * @param count The number of matches wanted.
     * @param options The LPos options.
     * @return An <code>array</code> that holds the indices of the matching elements within the list.
     * @example
     *     <pre>{@code
     * Long listLen = client.rpush(gs("my_list"), new GlideString[] {gs("a"), gs("b"), gs("c"), gs("d"), gs("e"), gs("e"), gs("e")}).get();
     *
     * // rank
     * LPosOptions options1 = LPosOptions.builder().rank(2L).build();
     * Long[] position1 = client.lposCount(gs("my_list"), gs("e"), 1L, options1).get();
     * assertArrayEquals(new Long[]{5L}, position1);
     *
     * // rank and maxLength
     * LPosOptions options2 = LPosOptions.builder.rank(2L).maxLength(1000L).build();
     * Long[] position2 = client.lposCount(gs("my_list"), gs("e"), 3L, options2).get();
     * assertArrayEquals(new Long[]{5L, 6L}, position2);
     * }</pre>
     */
    CompletableFuture<Long[]> lposCount(
            @NonNull GlideString key,
            @NonNull GlideString element,
            long count,
            @NonNull LPosOptions options);

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
     * GlideString[] payload = lient.lrange(gs("my_list"), 0, 2).get();
     * assert Arrays.equals(new GlideString[] {gs("value1"), gs("value2"), gs("value3")});
     *
     * GlideString[] payload = client.lrange(gs("my_list"), -2, -1).get();
     * assert Arrays.equals(new GlideString[] {gs("value2"), gs("value3")});
     *
     * GlideString[] payload = client.lrange(gs("non_exiting_key"), 0, 2).get();
     * assert Arrays.equals(new GlideString[] {});
     * }</pre>
     */
    CompletableFuture<GlideString[]> lrange(GlideString key, long start, long end);

    /**
     * Returns the element at <code>index</code> from the list stored at <code>key</code>.<br>
     * The index is zero-based, so <code>0</code> means the first element, <code>1</code> the second
     * element and so on. Negative indices can be used to designate elements starting at the tail of
     * the list. Here, <code>-1</code> means the last element, <code>-2</code> means the penultimate
     * and so forth.
     *
     * @see <a href="https://redis.io/commands/lindex/">redis.io</a> for details.
     * @param key The key of the list.
     * @param index The index of the element in the list to retrieve.
     * @return The element at <code>index</code> in the list stored at <code>key</code>.<br>
     *     If <code>index</code> is out of range or if <code>key</code> does not exist, <code>null
     *     </code> is returned.
     * @example
     *     <pre>{@code
     * String payload1 = client.lindex("myList", 0).get();
     * assert payload1.equals("value1"); // Returns the first element in the list stored at 'myList'.
     *
     * String payload2 = client.lindex("myList", -1).get();
     * assert payload2.equals("value3"); // Returns the last element in the list stored at 'myList'.
     * }</pre>
     */
    CompletableFuture<String> lindex(String key, long index);

    /**
     * Returns the element at <code>index</code> from the list stored at <code>key</code>.<br>
     * The index is zero-based, so <code>0</code> means the first element, <code>1</code> the second
     * element and so on. Negative indices can be used to designate elements starting at the tail of
     * the list. Here, <code>-1</code> means the last element, <code>-2</code> means the penultimate
     * and so forth.
     *
     * @see <a href="https://redis.io/commands/lindex/">redis.io</a> for details.
     * @param key The key of the list.
     * @param index The index of the element in the list to retrieve.
     * @return The element at <code>index</code> in the list stored at <code>key</code>.<br>
     *     If <code>index</code> is out of range or if <code>key</code> does not exist, <code>null
     *     </code> is returned.
     * @example
     *     <pre>{@code
     * String payload1 = client.lindex(gs("myList"), 0).get();
     * assert payload1.equals(gs("value1")); // Returns the first element in the list stored at 'myList'.
     *
     * String payload2 = client.lindex(gs("myList"), -1).get();
     * assert payload2.equals(gs("value3")); // Returns the last element in the list stored at 'myList'.
     * }</pre>
     */
    CompletableFuture<GlideString> lindex(GlideString key, long index);

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
     * String payload = client.ltrim(gs("my_list"), 0, 1).get();
     * assert payload.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> ltrim(GlideString key, long start, long end);

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
     * Returns the length of the list stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/llen/">redis.io</a> for details.
     * @param key The key of the list.
     * @return The length of the list at <code>key</code>.<br>
     *     If <code>key</code> does not exist, it is interpreted as an empty list and <code>0</code>
     *     is returned.
     * @example
     *     <pre>{@code
     * Long lenList = client.llen(gs("my_list")).get();
     * assert lenList == 3L //Indicates that there are 3 elements in the list.;
     * }</pre>
     */
    CompletableFuture<Long> llen(GlideString key);

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
     * Long num = client.rem(gs("my_list"), 2, gs("value")).get();
     * assert num == 2L;
     * }</pre>
     */
    CompletableFuture<Long> lrem(GlideString key, long count, GlideString element);

    /**
     * Inserts all the specified values at the tail of the list stored at <code>key</code>.<br>
     * <code>elements</code> are inserted one after the other to the tail of the list, from the
     * leftmost element to the rightmost element. If <code>key</code> does not exist, it is created as
     * an empty list before performing the push operation.
     *
     * @see <a href="https://redis.io/commands/rpush/">redis.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to insert at the tail of the list stored at <code>key</code>.
     * @return The length of the list after the push operation.
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
     * Inserts all the specified values at the tail of the list stored at <code>key</code>.<br>
     * <code>elements</code> are inserted one after the other to the tail of the list, from the
     * leftmost element to the rightmost element. If <code>key</code> does not exist, it is created as
     * an empty list before performing the push operation.
     *
     * @see <a href="https://redis.io/commands/rpush/">redis.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to insert at the tail of the list stored at <code>key</code>.
     * @return The length of the list after the push operation.
     * @example
     *     <pre>{@code
     * Long pushCount1 = client.rpush(gs("my_list"), new GlideString[] {gs("value1"), gs("value2")}).get();
     * assert pushCount1 == 2L;
     *
     * Long pushCount2 = client.rpush(gs("nonexistent_list"), new GlideString[] {gs("new_value")}).get();
     * assert pushCount2 == 1L;
     * }</pre>
     */
    CompletableFuture<Long> rpush(GlideString key, GlideString[] elements);

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
     * @param key The key of the list.
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

    /**
     * Inserts <code>element</code> in the list at <code>key</code> either before or after the <code>
     * pivot</code>.
     *
     * @see <a href="https://redis.io/commands/linsert/">redis.io</a> for details.
     * @param key The key of the list.
     * @param position The relative position to insert into - either {@link InsertPosition#BEFORE} or
     *     {@link InsertPosition#AFTER} the <code>pivot</code>.
     * @param pivot An element of the list.
     * @param element The new element to insert.
     * @return The list length after a successful insert operation.<br>
     *     If the <code>key</code> doesn't exist returns <code>-1</code>.<br>
     *     If the <code>pivot</code> wasn't found, returns <code>0</code>.
     * @example
     *     <pre>{@code
     * Long length = client.linsert("my_list", BEFORE, "World", "There").get();
     * assert length > 0L;
     * }</pre>
     */
    CompletableFuture<Long> linsert(
            String key, InsertPosition position, String pivot, String element);

    /**
     * Inserts <code>element</code> in the list at <code>key</code> either before or after the <code>
     * pivot</code>.
     *
     * @see <a href="https://redis.io/commands/linsert/">redis.io</a> for details.
     * @param key The key of the list.
     * @param position The relative position to insert into - either {@link InsertPosition#BEFORE} or
     *     {@link InsertPosition#AFTER} the <code>pivot</code>.
     * @param pivot An element of the list.
     * @param element The new element to insert.
     * @return The list length after a successful insert operation.<br>
     *     If the <code>key</code> doesn't exist returns <code>-1</code>.<br>
     *     If the <code>pivot</code> wasn't found, returns <code>0</code>.
     * @example
     *     <pre>{@code
     * Long length = client.linsert(gs("my_list"), BEFORE, gs("World"), gs("There")).get();
     * assert length > 0L;
     * }</pre>
     */
    CompletableFuture<Long> linsert(
            GlideString key, InsertPosition position, GlideString pivot, GlideString element);

    /**
     * Pops an element from the head of the first list that is non-empty, with the given <code>keys
     * </code> being checked in the order that they are given.<br>
     * Blocks the connection when there are no elements to pop from any of the given lists.
     *
     * @apiNote
     *     <ul>
     *       <li>When in cluster mode, all <code>keys</code> must map to the same hash slot.
     *       <li><code>BLPOP</code> is a client blocking command, see <a
     *           href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *           Commands</a> for more details and best practices.
     *     </ul>
     *
     * @see <a href="https://redis.io/commands/blpop/">redis.io</a> for details.
     * @param keys The <code>keys</code> of the lists to pop from.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @return A two-element <code>array</code> containing the <code>key</code> from which the element
     *     was popped and the <code>value</code> of the popped element, formatted as <code>
     *     [key, value]</code>. If no element could be popped and the timeout expired, returns </code>
     *     null</code>.
     * @example
     *     <pre>{@code
     * String[] response = client.blpop(["list1", "list2"], 0.5).get();
     * assert response[0].equals("list1");
     * assert response[1].equals("element");
     * }</pre>
     */
    CompletableFuture<String[]> blpop(String[] keys, double timeout);

    /**
     * Pops an element from the tail of the first list that is non-empty, with the given <code>keys
     * </code> being checked in the order that they are given.<br>
     * Blocks the connection when there are no elements to pop from any of the given lists.
     *
     * @apiNote
     *     <ul>
     *       <li>When in cluster mode, all <code>keys</code> must map to the same hash slot.
     *       <li><code>BRPOP</code> is a client blocking command, see <a
     *           href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *           Commands</a> for more details and best practices.
     *     </ul>
     *
     * @see <a href="https://redis.io/commands/brpop/">redis.io</a> for details.
     * @param keys The <code>keys</code> of the lists to pop from.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @return A two-element <code>array</code> containing the <code>key</code> from which the element
     *     was popped and the <code>value</code> of the popped element, formatted as <code>
     *     [key, value]</code>. If no element could be popped and the timeout expired, returns </code>
     *     null</code>.
     * @example
     *     <pre>{@code
     * String[] response = client.brpop(["list1", "list2"], 0.5).get();
     * assert response[0].equals("list1");
     * assert response[1].equals("element");
     * }</pre>
     */
    CompletableFuture<String[]> brpop(String[] keys, double timeout);

    /**
     * Inserts all the specified values at the tail of the list stored at <code>key</code>, only if
     * <code>key</code> exists and holds a list. If <code>key</code> is not a list, this performs no
     * operation.
     *
     * @see <a href="https://redis.io/commands/rpushx/">redis.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to insert at the tail of the list stored at <code>key</code>.
     * @return The length of the list after the push operation.
     * @example
     *     <pre>{@code
     * Long listLength = client.rpushx("my_list", new String[] {"value1", "value2"}).get();
     * assert listLength >= 2L;
     * }</pre>
     */
    CompletableFuture<Long> rpushx(String key, String[] elements);

    /**
     * Inserts all the specified values at the tail of the list stored at <code>key</code>, only if
     * <code>key</code> exists and holds a list. If <code>key</code> is not a list, this performs no
     * operation.
     *
     * @see <a href="https://redis.io/commands/rpushx/">redis.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to insert at the tail of the list stored at <code>key</code>.
     * @return The length of the list after the push operation.
     * @example
     *     <pre>{@code
     * Long listLength = client.rpushx(gs("my_list"), new GlideString[] {gs("value1"), gs("value2")}).get();
     * assert listLength >= 2L;
     * }</pre>
     */
    CompletableFuture<Long> rpushx(GlideString key, GlideString[] elements);

    /**
     * Inserts all the specified values at the head of the list stored at <code>key</code>, only if
     * <code>key</code> exists and holds a list. If <code>key</code> is not a list, this performs no
     * operation.
     *
     * @see <a href="https://redis.io/commands/lpushx/">redis.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to insert at the head of the list stored at <code>key</code>.
     * @return The length of the list after the push operation.
     * @example
     *     <pre>{@code
     * Long listLength = client.lpushx("my_list", new String[] {"value1", "value2"}).get();
     * assert listLength >= 2L;
     * }</pre>
     */
    CompletableFuture<Long> lpushx(String key, String[] elements);

    /**
     * Inserts all the specified values at the head of the list stored at <code>key</code>, only if
     * <code>key</code> exists and holds a list. If <code>key</code> is not a list, this performs no
     * operation.
     *
     * @see <a href="https://redis.io/commands/lpushx/">redis.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to insert at the head of the list stored at <code>key</code>.
     * @return The length of the list after the push operation.
     * @example
     *     <pre>{@code
     * Long listLength = client.lpushx(gs("my_list"), new GlideString[] {gs("value1"), gs("value2")}).get();
     * assert listLength >= 2L;
     * }</pre>
     */
    CompletableFuture<Long> lpushx(GlideString key, GlideString[] elements);

    /**
     * Pops one or more elements from the first non-empty list from the provided <code>keys
     * </code>.
     *
     * @since Redis 7.0 and above.
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @see <a href="https://valkey.io/commands/lmpop/">valkey.io</a> for details.
     * @param keys An array of keys to lists.
     * @param direction The direction based on which elements are popped from - see {@link
     *     ListDirection}.
     * @param count The maximum number of popped elements.
     * @return A <code>Map</code> of <code>key</code> name mapped array of popped elements.
     * @example
     *     <pre>{@code
     * client.lpush("testKey", new String[] {"one", "two", "three"}).get();
     * Map<String, String[]> result = client.lmpop(new String[] {"testKey"}, PopDirection.LEFT, 1L).get();
     * String[] resultValue = result.get("testKey");
     * assertArrayEquals(new String[] {"three"}, resultValue);
     * }</pre>
     */
    CompletableFuture<Map<String, String[]>> lmpop(
            String[] keys, ListDirection direction, long count);

    /**
     * Pops one element from the first non-empty list from the provided <code>keys</code>.
     *
     * @since Redis 7.0 and above.
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @see <a href="https://valkey.io/commands/lmpop/">valkey.io</a> for details.
     * @param keys An array of keys to lists.
     * @param direction The direction based on which elements are popped from - see {@link
     *     ListDirection}.
     * @return A <code>Map</code> of <code>key</code> name mapped array of the popped element.
     * @example
     *     <pre>{@code
     * client.lpush("testKey", new String[] {"one", "two", "three"}).get();
     * Map<String, String[]> result = client.lmpop(new String[] {"testKey"}, PopDirection.LEFT).get();
     * String[] resultValue = result.get("testKey");
     * assertArrayEquals(new String[] {"three"}, resultValue);
     * }</pre>
     */
    CompletableFuture<Map<String, String[]>> lmpop(String[] keys, ListDirection direction);

    /**
     * Blocks the connection until it pops one or more elements from the first non-empty list from the
     * provided <code>keys</code> <code>BLMPOP</code> is the blocking variant of {@link
     * #lmpop(String[], ListDirection, long)}.
     *
     * @apiNote
     *     <ol>
     *       <li>When in cluster mode, all <code>keys</code> must map to the same hash slot.
     *       <li><code>BLMPOP</code> is a client blocking command, see <a
     *           href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *           Commands</a> for more details and best practices.
     *     </ol>
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://valkey.io/commands/blmpop/">valkey.io</a> for details.
     * @param keys An array of keys to lists.
     * @param direction The direction based on which elements are popped from - see {@link
     *     ListDirection}.
     * @param count The maximum number of popped elements.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @return A <code>Map</code> of <code>key</code> name mapped array of popped elements.<br>
     *     If no member could be popped and the timeout expired, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * client.lpush("testKey", new String[] {"one", "two", "three"}).get();
     * Map<String, String[]> result = client.blmpop(new String[] {"testKey"}, PopDirection.LEFT, 1L, 0.1).get();
     * String[] resultValue = result.get("testKey");
     * assertArrayEquals(new String[] {"three"}, resultValue);
     * }</pre>
     */
    CompletableFuture<Map<String, String[]>> blmpop(
            String[] keys, ListDirection direction, long count, double timeout);

    /**
     * Blocks the connection until it pops one element from the first non-empty list from the provided
     * <code>keys</code> <code>BLMPOP</code> is the blocking variant of {@link #lmpop(String[],
     * ListDirection)}.
     *
     * @apiNote
     *     <ol>
     *       <li>When in cluster mode, all <code>keys</code> must map to the same hash slot.
     *       <li><code>BLMPOP</code> is a client blocking command, see <a
     *           href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *           Commands</a> for more details and best practices.
     *     </ol>
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://valkey.io/commands/lmpop/">valkey.io</a> for details.
     * @param keys An array of keys to lists.
     * @param direction The direction based on which elements are popped from - see {@link
     *     ListDirection}.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @return A <code>Map</code> of <code>key</code> name mapped array of the popped element.<br>
     *     If no member could be popped and the timeout expired, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * client.lpush("testKey", new String[] {"one", "two", "three"}).get();
     * Map<String, String[]> result = client.blmpop(new String[] {"testKey"}, PopDirection.LEFT, 0.1).get();
     * String[] resultValue = result.get("testKey");
     * assertArrayEquals(new String[] {"three"}, resultValue);
     * }</pre>
     */
    CompletableFuture<Map<String, String[]>> blmpop(
            String[] keys, ListDirection direction, double timeout);

    /**
     * Sets the list element at <code>index</code> to <code>element</code>.<br>
     * The index is zero-based, so <code>0</code> means the first element, <code>1</code> the second
     * element and so on. Negative indices can be used to designate elements starting at the tail of
     * the list. Here, <code>-1</code> means the last element, <code>-2</code> means the penultimate
     * and so forth.
     *
     * @see <a href="https://valkey.io/commands/lset/">valkey.io</a> for details.
     * @param key The key of the list.
     * @param index The index of the element in the list to be set.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.lset("testKey", 1, "two").get();
     * assertEquals(response, "OK");
     * }</pre>
     */
    CompletableFuture<String> lset(String key, long index, String element);

    /**
     * Sets the list element at <code>index</code> to <code>element</code>.<br>
     * The index is zero-based, so <code>0</code> means the first element, <code>1</code> the second
     * element and so on. Negative indices can be used to designate elements starting at the tail of
     * the list. Here, <code>-1</code> means the last element, <code>-2</code> means the penultimate
     * and so forth.
     *
     * @see <a href="https://valkey.io/commands/lset/">valkey.io</a> for details.
     * @param key The key of the list.
     * @param index The index of the element in the list to be set.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.lset(gs("testKey"), 1, gs("two")).get();
     * assertEquals(response, "OK");
     * }</pre>
     */
    CompletableFuture<String> lset(GlideString key, long index, GlideString element);

    /**
     * Atomically pops and removes the left/right-most element to the list stored at <code>source
     * </code> depending on <code>wherefrom</code>, and pushes the element at the first/last element
     * of the list stored at <code>destination</code> depending on <code>wherefrom</code>.
     *
     * @since Redis 6.2.0 and above.
     * @apiNote When in cluster mode, <code>source</code> and <code>destination</code> must map to the
     *     same hash slot.
     * @see <a href="https://valkey.io/commands/lmove/">valkey.io</a> for details.
     * @param source The key to the source list.
     * @param destination The key to the destination list.
     * @param wherefrom The {@link ListDirection} the element should be removed from.
     * @param whereto The {@link ListDirection} the element should be added to.
     * @return The popped element or <code>null</code> if <code>source</code> does not exist.
     * @example
     *     <pre>{@code
     * client.lpush("testKey1", new String[] {"two", "one"}).get();
     * client.lpush("testKey2", new String[] {"four", "three"}).get();
     * var result = client.lmove("testKey1", "testKey2", ListDirection.LEFT, ListDirection.LEFT).get();
     * assertEquals(result, "one");
     * String[] upratedArray1 = client.lrange("testKey1", 0, -1).get();
     * String[] upratedArray2 = client.lrange("testKey2", 0, -1).get();
     * assertArrayEquals(new String[] {"two"}, updatedArray1);
     * assertArrayEquals(new String[] {"one", "three", "four"}, updatedArray2);
     * }</pre>
     */
    CompletableFuture<String> lmove(
            String source, String destination, ListDirection wherefrom, ListDirection whereto);

    /**
     * Atomically pops and removes the left/right-most element to the list stored at <code>source
     * </code> depending on <code>wherefrom</code>, and pushes the element at the first/last element
     * of the list stored at <code>destination</code> depending on <code>wherefrom</code>.
     *
     * @since Redis 6.2.0 and above.
     * @apiNote When in cluster mode, <code>source</code> and <code>destination</code> must map to the
     *     same hash slot.
     * @see <a href="https://valkey.io/commands/lmove/">valkey.io</a> for details.
     * @param source The key to the source list.
     * @param destination The key to the destination list.
     * @param wherefrom The {@link ListDirection} the element should be removed from.
     * @param whereto The {@link ListDirection} the element should be added to.
     * @return The popped element or <code>null</code> if <code>source</code> does not exist.
     * @example
     *     <pre>{@code
     * client.lpush(gs("testKey1"), new GlideString[] {gs("two"), gs("one")}).get();
     * client.lpush(gs("testKey2"), new GlideString[] {gs("four"), gs("three")}).get();
     * var result = client.lmove(gs("testKey1"), gs("testKey2"), ListDirection.LEFT, ListDirection.LEFT).get();
     * assertEquals(result, gs("one"));
     * GlideString[] upratedArray1 = client.lrange(gs("testKey1"), 0, -1).get();
     * GlideString[] upratedArray2 = client.lrange(gs("testKey2"), 0, -1).get();
     * assertArrayEquals(new GlideString[] {gs("two")}, updatedArray1);
     * assertArrayEquals(new GlideString[] {gs("one"), gs("three"), gs("four)"}, updatedArray2);
     * }</pre>
     */
    CompletableFuture<GlideString> lmove(
            GlideString source, GlideString destination, ListDirection wherefrom, ListDirection whereto);

    /**
     * Blocks the connection until it pops atomically and removes the left/right-most element to the
     * list stored at <code>source</code> depending on <code>wherefrom</code>, and pushes the element
     * at the first/last element of the list stored at <code>destination</code> depending on <code>
     * wherefrom</code>.<br>
     * <code>BLMove</code> is the blocking variant of {@link #lmove(String, String, ListDirection,
     * ListDirection)}.
     *
     * @since Redis 6.2.0 and above.
     * @apiNote
     *     <ol>
     *       <li>When in cluster mode, all <code>source</code> and <code>destination</code> must map
     *           to the same hash slot.
     *       <li><code>BLMove</code> is a client blocking command, see <a
     *           href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *           Commands</a> for more details and best practices.
     *     </ol>
     *
     * @see <a href="https://valkey.io/commands/blmove/">valkey.io</a> for details.
     * @param source The key to the source list.
     * @param destination The key to the destination list.
     * @param wherefrom The {@link ListDirection} the element should be removed from.
     * @param whereto The {@link ListDirection} the element should be added to.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @return The popped element or <code>null</code> if <code>source</code> does not exist or if the
     *     operation timed-out.
     * @example
     *     <pre>{@code
     * client.lpush("testKey1", new String[] {"two", "one"}).get();
     * client.lpush("testKey2", new String[] {"four", "three"}).get();
     * var result = client.blmove("testKey1", "testKey2", ListDirection.LEFT, ListDirection.LEFT, 0.1).get();
     * assertEquals(result, "one");
     * String[] upratedArray1 = client.lrange("testKey1", 0, -1).get();
     * String[] upratedArray2 = client.lrange("testKey2", 0, -1).get();
     * assertArrayEquals(new String[] {"two"}, updatedArray1);
     * assertArrayEquals(new String[] {"one", "three", "four"}, updatedArray2);
     * }</pre>
     */
    CompletableFuture<String> blmove(
            String source,
            String destination,
            ListDirection wherefrom,
            ListDirection whereto,
            double timeout);

    /**
     * Blocks the connection until it pops atomically and removes the left/right-most element to the
     * list stored at <code>source</code> depending on <code>wherefrom</code>, and pushes the element
     * at the first/last element of the list stored at <code>destination</code> depending on <code>
     * wherefrom</code>.<br>
     * <code>BLMove</code> is the blocking variant of {@link #lmove(String, String, ListDirection,
     * ListDirection)}.
     *
     * @since Redis 6.2.0 and above.
     * @apiNote
     *     <ol>
     *       <li>When in cluster mode, all <code>source</code> and <code>destination</code> must map
     *           to the same hash slot.
     *       <li><code>BLMove</code> is a client blocking command, see <a
     *           href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *           Commands</a> for more details and best practices.
     *     </ol>
     *
     * @see <a href="https://valkey.io/commands/blmove/">valkey.io</a> for details.
     * @param source The key to the source list.
     * @param destination The key to the destination list.
     * @param wherefrom The {@link ListDirection} the element should be removed from.
     * @param whereto The {@link ListDirection} the element should be added to.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @return The popped element or <code>null</code> if <code>source</code> does not exist or if the
     *     operation timed-out.
     * @example
     *     <pre>{@code
     * client.lpush(gs("testKey1"), new GlideString[] {gs("two"), gs("one")}).get();
     * client.lpush(gs("testKey2"), new GlideString[] {gs("four"), gs("three")}).get();
     * var result = client.blmove(gs("testKey1"), gs("testKey2"), ListDirection.LEFT, ListDirection.LEFT, 0.1).get();
     * assertEquals(result, gs("one"));
     * GlideString[] upratedArray1 = client.lrange(gs("testKey1"), 0, -1).get();
     * GlideString[] upratedArray2 = client.lrange(gs("testKey2"), 0, -1).get();
     * assertArrayEquals(new GlideString[] {gs("two")}, updatedArray1);
     * assertArrayEquals(new GlideString[] {gs("one"), gs("three"), gs("four")}, updatedArray2);
     * }</pre>
     */
    CompletableFuture<GlideString> blmove(
            GlideString source,
            GlideString destination,
            ListDirection wherefrom,
            ListDirection whereto,
            double timeout);
}
