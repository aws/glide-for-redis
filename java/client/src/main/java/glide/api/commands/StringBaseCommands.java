/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.GlideString;
import glide.api.models.commands.GetExOptions;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.SetOptions.ConditionalSet;
import glide.api.models.commands.SetOptions.SetOptionsBuilder;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "String Commands" group for standalone and cluster
 * clients.
 *
 * @see <a href="https://redis.io/commands/?group=string">String Commands</a>
 */
public interface StringBaseCommands {

    /** Redis API keyword used to indicate that the length of the lcs should be returned. */
    public static final String LEN_REDIS_API = "LEN";

    /** <code>IDX</code> option string to include in the <code>LCS</code> command. */
    public static final String IDX_COMMAND_STRING = "IDX";

    /** <code>MINMATCHLEN</code> option string to include in the <code>LCS</code> command. */
    public static final String MINMATCHLEN_COMMAND_STRING = "MINMATCHLEN";

    /** <code>WITHMATCHLEN</code> option string to include in the <code>LCS</code> command. */
    public static final String WITHMATCHLEN_COMMAND_STRING = "WITHMATCHLEN";

    /** Key for LCS matches result. */
    public static final String LCS_MATCHES_RESULT_KEY = "matches";

    /**
     * Gets the value associated with the given <code>key</code>, or <code>null</code> if no such
     * value exists.
     *
     * @see <a href="https://redis.io/commands/get/">redis.io</a> for details.
     * @param key The <code>key</code> to retrieve from the database.
     * @return Response from Redis. If <code>key</code> exists, returns the <code>value</code> of
     *     <code>key</code> as a <code>String</code>. Otherwise, return <code>null</code>.
     * @example
     *     <pre>{@code
     * String value = client.get("key").get();
     * assert value.equals("value");
     *
     * String value = client.get("non_existing_key").get();
     * assert value.equals(null);
     * }</pre>
     */
    CompletableFuture<String> get(String key);

    /**
     * Gets the value associated with the given <code>key</code>, or <code>null</code> if no such
     * value exists.
     *
     * @see <a href="https://redis.io/commands/get/">redis.io</a> for details.
     * @param key The <code>key</code> to retrieve from the database.
     * @return Response from Redis. If <code>key</code> exists, returns the <code>value</code> of
     *     <code>key</code> as a <code>String</code>. Otherwise, return <code>null</code>.
     * @example
     *     <pre>{@code
     * GlideString value = client.get(gs("key")).get();
     * assert Arrays.equals(value.getString(), "value");
     *
     * String value = client.get("non_existing_key").get();
     * assert value.equals(null);
     * }</pre>
     */
    CompletableFuture<GlideString> get(GlideString key);

    /**
     * Gets a string value associated with the given <code>key</code> and deletes the key.
     *
     * @see <a href="https://redis.io/docs/latest/commands/getdel/">redis.io</a> for details.
     * @param key The <code>key</code> to retrieve from the database.
     * @return If <code>key</code> exists, returns the <code>value</code> of <code>key</code>.
     *     Otherwise, return <code>null</code>.
     * @example
     *     <pre>{@code
     * String value = client.getdel("key").get();
     * assert value.equals("value");
     *
     * String value = client.getdel("key").get();
     * assert value.equals(null);
     * }</pre>
     */
    CompletableFuture<String> getdel(String key);

    /**
     * Gets a string value associated with the given <code>key</code> and deletes the key.
     *
     * @see <a href="https://redis.io/docs/latest/commands/getdel/">redis.io</a> for details.
     * @param key The <code>key</code> to retrieve from the database.
     * @return If <code>key</code> exists, returns the <code>value</code> of <code>key</code>.
     *     Otherwise, return <code>null</code>.
     * @example
     *     <pre>{@code
     * GlideString value = client.getdel(gs("key")).get();
     * assert value.getString().equals("value");
     *
     * GlideString value = client.getdel(gs("key")).get();
     * assert value.equals(null);
     * }</pre>
     */
    CompletableFuture<GlideString> getdel(GlideString key);

    /**
     * Gets the value associated with the given <code>key</code>.
     *
     * @since Redis 6.2.0.
     * @see <a href="https://redis.io/docs/latest/commands/getex/">redis.io</a> for details.
     * @param key The <code>key</code> to retrieve from the database.
     * @return If <code>key</code> exists, return the <code>value</code> of the <code>key</code>.
     *     Otherwise, return <code>null</code>.
     * @example
     *     <pre>{@code
     * String value = client.getex("key").get();
     * assert value.equals("value");
     * }</pre>
     */
    CompletableFuture<String> getex(String key);

    /**
     * Gets the value associated with the given <code>key</code>.
     *
     * @since Redis 6.2.0.
     * @see <a href="https://redis.io/docs/latest/commands/getex/">redis.io</a> for details.
     * @param key The <code>key</code> to retrieve from the database.
     * @return If <code>key</code> exists, return the <code>value</code> of the <code>key</code>.
     *     Otherwise, return <code>null</code>.
     * @example
     *     <pre>{@code
     * GlideString value = client.getex(gs("key")).get();
     * assert value.equals(gs("value"));
     * }</pre>
     */
    CompletableFuture<GlideString> getex(GlideString key);

    /**
     * Gets the value associated with the given <code>key</code>.
     *
     * @since Redis 6.2.0.
     * @see <a href="https://redis.io/docs/latest/commands/getex/">redis.io</a> for details.
     * @param key The <code>key</code> to retrieve from the database.
     * @param options The {@link GetExOptions} options.
     * @return If <code>key</code> exists, return the <code>value</code> of the <code>key</code>.
     *     Otherwise, return <code>null</code>.
     * @example
     *     <pre>{@code
     * String response = client.set("key", "value").get();
     * assert response.equals(OK);
     * String value = client.getex("key", GetExOptions.Seconds(10L)).get();
     * assert value.equals("value");
     * }</pre>
     */
    CompletableFuture<String> getex(String key, GetExOptions options);

    /**
     * Gets the value associated with the given <code>key</code>.
     *
     * @since Redis 6.2.0.
     * @see <a href="https://redis.io/docs/latest/commands/getex/">redis.io</a> for details.
     * @param key The <code>key</code> to retrieve from the database.
     * @param options The {@link GetExOptions} options.
     * @return If <code>key</code> exists, return the <code>value</code> of the <code>key</code>.
     *     Otherwise, return <code>null</code>.
     * @example
     *     <pre>{@code
     * String response = client.set(gs("key"), gs("value").get();
     * assert response.equals(OK);
     * GlideString value = client.getex(gs("key"), GetExOptions.Seconds(10L)).get();
     * assert value.equals(gs("value"));
     * }</pre>
     */
    CompletableFuture<GlideString> getex(GlideString key, GetExOptions options);

    /**
     * Sets the given <code>key</code> with the given value.
     *
     * @see <a href="https://redis.io/commands/set/">redis.io</a> for details.
     * @param key The <code>key</code> to store.
     * @param value The value to store with the given <code>key</code>.
     * @return Response from Redis containing <code>"OK"</code>.
     * @example
     *     <pre>{@code
     * String value = client.set("key", "value").get();
     * assert value.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> set(String key, String value);

    /**
     * Sets the given <code>key</code> with the given value.
     *
     * @see <a href="https://redis.io/commands/set/">redis.io</a> for details.
     * @param key The <code>key</code> to store.
     * @param value The value to store with the given <code>key</code>.
     * @return Response from Redis containing <code>"OK"</code>.
     * @example
     *     <pre>{@code
     * GlideString value = client.set(gs("key"), gs("value")).get();
     * assert value.getString().equals("OK");
     * }</pre>
     */
    CompletableFuture<String> set(GlideString key, GlideString value);

    /**
     * Sets the given key with the given value. Return value is dependent on the passed options.
     *
     * @see <a href="https://redis.io/commands/set/">redis.io</a> for details.
     * @param key The key to store.
     * @param value The value to store with the given key.
     * @param options The Set options.
     * @return Response from Redis containing a <code>String</code> or <code>null</code> response. If
     *     the value is successfully set, return <code>"OK"</code>. If value isn't set because of
     *     {@link ConditionalSet#ONLY_IF_EXISTS} or {@link ConditionalSet#ONLY_IF_DOES_NOT_EXIST}
     *     conditions, return <code>null</code>. If {@link SetOptionsBuilder#returnOldValue(boolean)}
     *     is set, return the old value as a <code>String</code>.
     * @example
     *     <pre>{@code
     * SetOptions options = SetOptions.builder().conditionalSet(ONLY_IF_EXISTS).expiry(Seconds(5L)).build();
     * String value = client.set("key", "value", options).get();
     * assert value.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> set(String key, String value, SetOptions options);

    /**
     * Sets the given key with the given value. Return value is dependent on the passed options.
     *
     * @see <a href="https://redis.io/commands/set/">redis.io</a> for details.
     * @param key The key to store.
     * @param value The value to store with the given key.
     * @param options The Set options.
     * @return Response from Redis containing a <code>String</code> or <code>null</code> response. If
     *     the value is successfully set, return <code>"OK"</code>. If value isn't set because of
     *     {@link ConditionalSet#ONLY_IF_EXISTS} or {@link ConditionalSet#ONLY_IF_DOES_NOT_EXIST}
     *     conditions, return <code>null</code>. If {@link SetOptionsBuilder#returnOldValue(boolean)}
     *     is set, return the old value as a <code>String</code>.
     * @example
     *     <pre>{@code
     * SetOptions options = SetOptions.builder().conditionalSet(ONLY_IF_EXISTS).expiry(Seconds(5L)).build();
     * String value = client.set("key".getBytes(), "value".getBytes(), options).get();
     * assert value.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> set(GlideString key, GlideString value, SetOptions options);

    /**
     * Retrieves the values of multiple <code>keys</code>.
     *
     * @apiNote When in cluster mode, the command may route to multiple nodes when <code>keys</code>
     *     map to different hash slots.
     * @see <a href="https://redis.io/commands/mget/">redis.io</a> for details.
     * @param keys A list of keys to retrieve values for.
     * @return An array of values corresponding to the provided <code>keys</code>.<br>
     *     If a <code>key</code>is not found, its corresponding value in the list will be <code>null
     *     </code>.
     * @example
     *     <pre>{@code
     * String values = client.mget(new String[] {"key1", "key2"}).get();
     * assert values.equals(new String[] {"value1", "value2"});
     * }</pre>
     */
    CompletableFuture<String[]> mget(String[] keys);

    /**
     * Retrieves the values of multiple <code>keys</code>.
     *
     * @apiNote When in cluster mode, the command may route to multiple nodes when <code>keys</code>
     *     map to different hash slots.
     * @see <a href="https://redis.io/commands/mget/">redis.io</a> for details.
     * @param keys A list of keys to retrieve values for.
     * @return An array of values corresponding to the provided <code>keys</code>.<br>
     *     If a <code>key</code>is not found, its corresponding value in the list will be <code>null
     *     </code>.
     * @example
     *     <pre>{@code
     * GlideString[] values = client.mget(new GlideString[] {"key1", "key2"}).get();
     * assert values.equals(new GlideString[] {"value1", "value2"});
     * }</pre>
     */
    CompletableFuture<GlideString[]> mget(GlideString[] keys);

    /**
     * Sets multiple keys to multiple values in a single operation.
     *
     * @apiNote When in cluster mode, the command may route to multiple nodes when keys in <code>
     *     keyValueMap</code> map to different hash slots.
     * @see <a href="https://redis.io/commands/mset/">redis.io</a> for details.
     * @param keyValueMap A key-value map consisting of keys and their respective values to set.
     * @return Always <code>OK</code>.
     * @example
     *     <pre>{@code
     * String result = client.mset(Map.of("key1", "value1", "key2", "value2"}).get();
     * assert result.equals("OK"));
     * }</pre>
     */
    CompletableFuture<String> mset(Map<String, String> keyValueMap);

    /**
     * Sets multiple keys to values if the key does not exist. The operation is atomic, and if one or
     * more keys already exist, the entire operation fails.
     *
     * @apiNote When in cluster mode, all keys in <code>keyValueMap</code> must map to the same hash
     *     slot.
     * @see <a href="https://redis.io/commands/msetnx/">redis.io</a> for details.
     * @param keyValueMap A key-value map consisting of keys and their respective values to set.
     * @return <code>true</code> if all keys were set. <code>false</code> if no key was set.
     * @example
     *     <pre>{@code
     * Boolean result = client.msetnx(Map.of("key1", "value1", "key2", "value2"}).get();
     * assert result;
     * }</pre>
     */
    CompletableFuture<Boolean> msetnx(Map<String, String> keyValueMap);

    /**
     * Increments the number stored at <code>key</code> by one. If <code>key</code> does not exist, it
     * is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/incr/">redis.io</a> for details.
     * @param key The key to increment its value.
     * @return The value of <code>key</code> after the increment.
     * @example
     *     <pre>{@code
     * Long num = client.incr("key").get();
     * assert num == 5L;
     * }</pre>
     */
    CompletableFuture<Long> incr(String key);

    /**
     * Increments the number stored at <code>key</code> by one. If <code>key</code> does not exist, it
     * is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/incr/">redis.io</a> for details.
     * @param key The key to increment its value.
     * @return The value of <code>key</code> after the increment.
     * @example
     *     <pre>{@code
     * Long num = client.incr(gs("key")).get();
     * assert num == 5L;
     * }</pre>
     */
    CompletableFuture<Long> incr(GlideString key);

    /**
     * Increments the number stored at <code>key</code> by <code>amount</code>. If <code>key</code>
     * does not exist, it is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/incrby/">redis.io</a> for details.
     * @param key The key to increment its value.
     * @param amount The amount to increment.
     * @return The value of <code>key</code> after the increment.
     * @example
     *     <pre>{@code
     * Long num = client.incrBy("key", 2).get();
     * assert num == 7L;
     * }</pre>
     */
    CompletableFuture<Long> incrBy(String key, long amount);

    /**
     * Increments the number stored at <code>key</code> by <code>amount</code>. If <code>key</code>
     * does not exist, it is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/incrby/">redis.io</a> for details.
     * @param key The key to increment its value.
     * @param amount The amount to increment.
     * @return The value of <code>key</code> after the increment.
     * @example
     *     <pre>{@code
     * Long num = client.incrBy(gs("key"), 2).get();
     * assert num == 7L;
     * }</pre>
     */
    CompletableFuture<Long> incrBy(GlideString key, long amount);

    /**
     * Increments the string representing a floating point number stored at <code>key</code> by <code>
     * amount</code>. By using a negative increment value, the result is that the value stored at
     * <code>key</code> is decremented. If <code>key</code> does not exist, it is set to 0 before
     * performing the operation.
     *
     * @see <a href="https://redis.io/commands/incrbyfloat/">redis.io</a> for details.
     * @param key The key to increment its value.
     * @param amount The amount to increment.
     * @return The value of <code>key</code> after the increment.
     * @example
     *     <pre>{@code
     * Double num = client.incrByFloat("key", 0.5).get();
     * assert num == 7.5;
     * }</pre>
     */
    CompletableFuture<Double> incrByFloat(String key, double amount);

    /**
     * Increments the string representing a floating point number stored at <code>key</code> by <code>
     * amount</code>. By using a negative increment value, the result is that the value stored at
     * <code>key</code> is decremented. If <code>key</code> does not exist, it is set to 0 before
     * performing the operation.
     *
     * @see <a href="https://redis.io/commands/incrbyfloat/">redis.io</a> for details.
     * @param key The key to increment its value.
     * @param amount The amount to increment.
     * @return The value of <code>key</code> after the increment.
     * @example
     *     <pre>{@code
     * Double num = client.incrByFloat(gs("key"), 0.5).get();
     * assert num == 7.5;
     * }</pre>
     */
    CompletableFuture<Double> incrByFloat(GlideString key, double amount);

    /**
     * Decrements the number stored at <code>key</code> by one. If <code>key</code> does not exist, it
     * is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/decr/">redis.io</a> for details.
     * @param key The key to decrement its value.
     * @return The value of <code>key</code> after the decrement.
     * @example
     *     <pre>{@code
     * Long num = client.decr("key").get();
     * assert num == 4L;
     * }</pre>
     */
    CompletableFuture<Long> decr(String key);

    /**
     * Decrements the number stored at <code>key</code> by one. If <code>key</code> does not exist, it
     * is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/decr/">redis.io</a> for details.
     * @param key The key to decrement its value.
     * @return The value of <code>key</code> after the decrement.
     * @example
     *     <pre>{@code
     * Long num = client.decr(gs("key")).get();
     * assert num == 4L;
     * }</pre>
     */
    CompletableFuture<Long> decr(GlideString key);

    /**
     * Decrements the number stored at <code>key</code> by <code>amount</code>. If <code>key</code>
     * does not exist, it is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/decrby/">redis.io</a> for details.
     * @param key The key to decrement its value.
     * @param amount The amount to decrement.
     * @return The value of <code>key</code> after the decrement.
     * @example
     *     <pre>{@code
     * Long num = client.decrBy("key", 2).get();
     * assert num == 2L;
     * }</pre>
     */
    CompletableFuture<Long> decrBy(String key, long amount);

    /**
     * Decrements the number stored at <code>key</code> by <code>amount</code>. If <code>key</code>
     * does not exist, it is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/decrby/">redis.io</a> for details.
     * @param key The key to decrement its value.
     * @param amount The amount to decrement.
     * @return The value of <code>key</code> after the decrement.
     * @example
     *     <pre>{@code
     * Long num = client.decrBy(gs("key"), 2).get();
     * assert num == 2L;
     * }</pre>
     */
    CompletableFuture<Long> decrBy(GlideString key, long amount);

    /**
     * Returns the length of the string value stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/strlen/">redis.io</a> for details.
     * @param key The key to check its length.
     * @return The length of the string value stored at key.<br>
     *     If <code>key</code> does not exist, it is treated as an empty string, and the command
     *     returns <code>0</code>.
     * @example
     *     <pre>{@code
     * client.set("key", "GLIDE").get();
     * Long len = client.strlen("key").get();
     * assert len == 5L;
     *
     * len = client.strlen("non_existing_key").get();
     * assert len == 0L;
     * }</pre>
     */
    CompletableFuture<Long> strlen(String key);

    /**
     * Returns the length of the string value stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/strlen/">redis.io</a> for details.
     * @param key The key to check its length.
     * @return The length of the string value stored at key.<br>
     *     If <code>key</code> does not exist, it is treated as an empty string, and the command
     *     returns <code>0</code>.
     * @example
     *     <pre>{@code
     * client.set(gs("key"), gs("GLIDE")).get();
     * Long len = client.strlen(gs("key")).get();
     * assert len == 5L;
     *
     * len = client.strlen(gs("non_existing_key")).get();
     * assert len == 0L;
     * }</pre>
     */
    CompletableFuture<Long> strlen(GlideString key);

    /**
     * Overwrites part of the string stored at <code>key</code>, starting at the specified <code>
     * offset</code>, for the entire length of <code>value</code>.<br>
     * If the <code>offset</code> is larger than the current length of the string at <code>key</code>,
     * the string is padded with zero bytes to make <code>offset</code> fit. Creates the <code>key
     * </code> if it doesn't exist.
     *
     * @see <a href="https://redis.io/commands/setrange/">redis.io</a> for details.
     * @param key The key of the string to update.
     * @param offset The position in the string where <code>value</code> should be written.
     * @param value The string written with <code>offset</code>.
     * @return The length of the string stored at <code>key</code> after it was modified.
     * @example
     *     <pre>{@code
     * Long len = client.setrange("key", 6, "GLIDE").get();
     * assert len == 11L; // New key was created with length of 11 symbols
     * String value = client.get("key").get();
     * assert value.equals("\0\0\0\0\0\0GLIDE"); // The string was padded with zero bytes
     * }</pre>
     */
    CompletableFuture<Long> setrange(String key, int offset, String value);

    /**
     * Overwrites part of the GlideString stored at <code>key</code>, starting at the specified <code>
     * offset</code>, for the entire length of <code>value</code>.<br>
     * If the <code>offset</code> is larger than the current length of the GlideString at <code>key
     * </code>, the GlideString is padded with zero bytes to make <code>offset</code> fit. Creates the
     * <code>key
     * </code> if it doesn't exist.
     *
     * @see <a href="https://redis.io/commands/setrange/">redis.io</a> for details.
     * @param key The key of the GlideString to update.
     * @param offset The position in the GlideString where <code>value</code> should be written.
     * @param value The GlideString written with <code>offset</code>.
     * @return The length of the GlideString stored at <code>key</code> after it was modified.
     * @example
     *     <pre>{@code
     * Long len = client.setrange(gs("key"), 6, gs("GLIDE")).get();
     * assert len == 11L; // New key was created with length of 11 symbols
     * GlideString value = client.get(gs("key")).get();
     * assert value.equals(gs("\0\0\0\0\0\0GLIDE")); // The string was padded with zero bytes
     * }</pre>
     */
    CompletableFuture<Long> setrange(GlideString key, int offset, GlideString value);

    /**
     * Returns the substring of the string value stored at <code>key</code>, determined by the offsets
     * <code>start</code> and <code>end</code> (both are inclusive). Negative offsets can be used in
     * order to provide an offset starting from the end of the string. So <code>-1</code> means the
     * last character, <code>-2</code> the penultimate and so forth.
     *
     * @see <a href="https://redis.io/commands/getrange/">redis.io</a> for details.
     * @param key The key of the string.
     * @param start The starting offset.
     * @param end The ending offset.
     * @return A substring extracted from the value stored at <code>key</code>..
     * @example
     *     <pre>{@code
     * client.set("mykey", "This is a string").get();
     * String substring = client.getrange("mykey", 0, 3).get();
     * assert substring.equals("This");
     * String substring = client.getrange("mykey", -3, -1).get();
     * assert substring.equals("ing"); // extracted last 3 characters of a string
     * }</pre>
     */
    CompletableFuture<String> getrange(String key, int start, int end);

    /**
     * Returns the subGlideString of the GlideString value stored at <code>key</code>, determined by
     * the offsets <code>start</code> and <code>end</code> (both are inclusive). Negative offsets can
     * be used in order to provide an offset starting from the end of the GlideString. So <code>-1
     * </code> means the last character, <code>-2</code> the penultimate and so forth.
     *
     * @see <a href="https://redis.io/commands/getrange/">redis.io</a> for details.
     * @param key The key of the GlideString.
     * @param start The starting offset.
     * @param end The ending offset.
     * @return A subGlideString extracted from the value stored at <code>key</code>..
     * @example
     *     <pre>{@code
     * client.set(gs("mykey"), gs("This is a GlideString")).get();
     * GlideString subGlideString = client.getrange(gs("mykey"), 0, 3).get();
     * assert subGlideString.equals(gs("This"));
     * GlideString subGlideString = client.getrange(gs("mykey"), -3, -1).get();
     * assert subGlideString.equals(gs("ing")); // extracted last 3 characters of a GlideString
     * }</pre>
     */
    CompletableFuture<GlideString> getrange(GlideString key, int start, int end);

    /**
     * Appends a <code>value</code> to a <code>key</code>. If <code>key</code> does not exist it is
     * created and set as an empty string, so <code>APPEND</code> will be similar to {@see #set} in
     * this special case.
     *
     * @see <a href="https://redis.io/docs/latest/commands/append/">redis.io</a> for details.
     * @param key The key of the string.
     * @param value The value to append.
     * @return The length of the string after appending the value.
     * @example
     *     <pre>{@code
     * Long value = client.append("key", "value").get();
     * assert value.equals(5L);
     * }</pre>
     */
    CompletableFuture<Long> append(String key, String value);

    /**
     * Appends a <code>value</code> to a <code>key</code>. If <code>key</code> does not exist it is
     * created and set as an empty string, so <code>APPEND</code> will be similar to {@see #set} in
     * this special case.
     *
     * @see <a href="https://redis.io/docs/latest/commands/append/">redis.io</a> for details.
     * @param key The key of the string.
     * @param value The value to append.
     * @return The length of the string after appending the value.
     * @example
     *     <pre>{@code
     * Long value = client.append(gs("key"), gs("value")).get();
     * assert value.equals(5L);
     * }</pre>
     */
    CompletableFuture<Long> append(GlideString key, GlideString value);

    /**
     * Returns the longest common subsequence between strings stored at <code>key1</code> and <code>
     * key2</code>.
     *
     * @since Redis 7.0 and above.
     * @apiNote When in cluster mode, <code>key1</code> and <code>key2</code> must map to the same
     *     hash slot.
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1 The key that stores the first string.
     * @param key2 The key that stores the second string.
     * @return A <code>String</code> containing the longest common subsequence between the 2 strings.
     *     An empty <code>String</code> is returned if the keys do not exist or have no common
     *     subsequences.
     * @example
     *     <pre>{@code
     * // testKey1 = abcd, testKey2 = axcd
     * String result = client.lcs("testKey1", "testKey2").get();
     * assert result.equals("acd");
     * }</pre>
     */
    CompletableFuture<String> lcs(String key1, String key2);

    /**
     * Returns the length of the longest common subsequence between strings stored at <code>key1
     * </code> and <code>key2</code>.
     *
     * @since Redis 7.0 and above.
     * @apiNote When in cluster mode, <code>key1</code> and <code>key2</code> must map to the same
     *     hash slot.
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1 The key that stores the first string.
     * @param key2 The key that stores the second string.
     * @return The length of the longest common subsequence between the 2 strings.
     * @example
     *     <pre>{@code
     * // testKey1 = abcd, testKey2 = axcd
     * Long result = client.lcsLen("testKey1", "testKey2").get();
     * assert result.equals(3L);
     * }</pre>
     */
    CompletableFuture<Long> lcsLen(String key1, String key2);

    /**
     * Returns the indices and length of the longest common subsequence between strings stored at
     * <code>key1</code> and <code>key2</code>.
     *
     * @since Redis 7.0 and above.
     * @apiNote When in cluster mode, <code>key1</code> and <code>key2</code> must map to the same
     *     hash slot.
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1 The key that stores the first string.
     * @param key2 The key that stores the second string.
     * @return A <code>Map</code> containing the indices of the longest common subsequence between the
     *     2 strings and the length of the longest common subsequence. The resulting map contains two
     *     keys, "matches" and "len":
     *     <ul>
     *       <li>"len" is mapped to the length of the longest common subsequence between the 2 strings
     *           stored as <code>Long</code>.
     *       <li>"matches" is mapped to a three dimensional <code>Long</code> array that stores pairs
     *           of indices that represent the location of the common subsequences in the strings held
     *           by <code>key1</code> and <code>key2</code>.
     *     </ul>
     *
     * @example If <code>key1</code> holds the string <code>"abcd123"</code> and <code>key2</code>
     *     holds the string <code>"bcdef123"</code> then the sample result would be
     *     <pre>{@code
     * new Long[][][] {
     *      {
     *          {4L, 6L},
     *          {5L, 7L}
     *      },
     *      {
     *          {1L, 3L},
     *          {0L, 2L}
     *      }
     *  }
     * }</pre>
     *     The result indicates that the first substring match is <code>"123"</code> in <code>key1
     *     </code> at index <code>4</code> to <code>6</code> which matches the substring in <code>key2
     *     </code> at index <code>5</code> to <code>7</code>. And the second substring match is <code>
     *     "bcd"</code> in <code>key1</code> at index <code>1</code> to <code>3</code> which matches
     *     the substring in <code>key2</code> at index <code>0</code> to <code>2</code>.
     */
    CompletableFuture<Map<String, Object>> lcsIdx(String key1, String key2);

    /**
     * Returns the indices and length of the longest common subsequence between strings stored at
     * <code>key1</code> and <code>key2</code>.
     *
     * @since Redis 7.0 and above.
     * @apiNote When in cluster mode, <code>key1</code> and <code>key2</code> must map to the same
     *     hash slot.
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1 The key that stores the first string.
     * @param key2 The key that stores the second string.
     * @param minMatchLen The minimum length of matches to include in the result.
     * @return A <code>Map</code> containing the indices of the longest common subsequence between the
     *     2 strings and the length of the longest common subsequence. The resulting map contains two
     *     keys, "matches" and "len":
     *     <ul>
     *       <li>"len" is mapped to the length of the longest common subsequence between the 2 strings
     *           stored as <code>Long</code>.
     *       <li>"matches" is mapped to a three dimensional <code>Long</code> array that stores pairs
     *           of indices that represent the location of the common subsequences in the strings held
     *           by <code>key1</code> and <code>key2</code>.
     *     </ul>
     *
     * @example If <code>key1</code> holds the string <code>"abcd123"</code> and <code>key2</code>
     *     holds the string <code>"bcdef123"</code> then the sample result would be
     *     <pre>{@code
     * new Long[][][] {
     *      {
     *          {4L, 6L},
     *          {5L, 7L}
     *      },
     *      {
     *          {1L, 3L},
     *          {0L, 2L}
     *      }
     *  }
     * }</pre>
     *     The result indicates that the first substring match is <code>"123"</code> in <code>key1
     *     </code> at index <code>4</code> to <code>6</code> which matches the substring in <code>key2
     *     </code> at index <code>5</code> to <code>7</code>. And the second substring match is <code>
     *     "bcd"</code> in <code>key1</code> at index <code>1</code> to <code>3</code> which matches
     *     the substring in <code>key2</code> at index <code>0</code> to <code>2</code>.
     */
    CompletableFuture<Map<String, Object>> lcsIdx(String key1, String key2, long minMatchLen);

    /**
     * Returns the indices and length of the longest common subsequence between strings stored at
     * <code>key1</code> and <code>key2</code>.
     *
     * @since Redis 7.0 and above.
     * @apiNote When in cluster mode, <code>key1</code> and <code>key2</code> must map to the same
     *     hash slot.
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1 The key that stores the first string.
     * @param key2 The key that stores the second string.
     * @return A <code>Map</code> containing the indices of the longest common subsequence between the
     *     2 strings and the length of the longest common subsequence. The resulting map contains two
     *     keys, "matches" and "len":
     *     <ul>
     *       <li>"len" is mapped to the length of the longest common subsequence between the 2 strings
     *           stored as <code>Long</code>.
     *       <li>"matches" is mapped to a three dimensional <code>Long</code> array that stores pairs
     *           of indices that represent the location of the common subsequences in the strings held
     *           by <code>key1</code> and <code>key2</code>.
     *     </ul>
     *
     * @example If <code>key1</code> holds the string <code>"abcd1234"</code> and <code>key2</code>
     *     holds the string <code>"bcdef1234"</code> then the sample result would be
     *     <pre>{@code
     * new Object[] {
     *      new Object[] {
     *          new Long[] {4L, 7L},
     *          new Long[] {5L, 8L},
     *          4L},
     *      new Object[] {
     *          new Long[] {1L, 3L},
     *          new Long[] {0L, 2L},
     *          3L}
     *      }
     * }</pre>
     *     The result indicates that the first substring match is <code>"1234"</code> in <code>key1
     *     </code> at index <code>4</code> to <code>7</code> which matches the substring in <code>key2
     *     </code> at index <code>5</code> to <code>8</code> and the last element in the array is the
     *     length of the substring match which is <code>4</code>. And the second substring match is
     *     <code>"bcd"</code> in <code>key1</code> at index <code>1</code> to <code>3</code> which
     *     matches the substring in <code>key2</code> at index <code>0</code> to <code>2</code> and
     *     the last element in the array is the length of the substring match which is <code>3</code>.
     */
    CompletableFuture<Map<String, Object>> lcsIdxWithMatchLen(String key1, String key2);

    /**
     * Returns the indices and length of the longest common subsequence between strings stored at
     * <code>key1</code> and <code>key2</code>.
     *
     * @since Redis 7.0 and above.
     * @apiNote When in cluster mode, <code>key1</code> and <code>key2</code> must map to the same
     *     hash slot.
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1 The key that stores the first string.
     * @param key2 The key that stores the second string.
     * @param minMatchLen The minimum length of matches to include in the result.
     * @return A <code>Map</code> containing the indices of the longest common subsequence between the
     *     2 strings and the length of the longest common subsequence. The resulting map contains two
     *     keys, "matches" and "len":
     *     <ul>
     *       <li>"len" is mapped to the length of the longest common subsequence between the 2 strings
     *           stored as <code>Long</code>.
     *       <li>"matches" is mapped to a three dimensional <code>Long</code> array that stores pairs
     *           of indices that represent the location of the common subsequences in the strings held
     *           by <code>key1</code> and <code>key2</code>.
     *     </ul>
     *
     * @example If <code>key1</code> holds the string <code>"abcd1234"</code> and <code>key2</code>
     *     holds the string <code>"bcdef1234"</code> then the sample result would be
     *     <pre>{@code
     * new Object[] {
     *      new Object[] {
     *          new Long[] {4L, 7L},
     *          new Long[] {5L, 8L},
     *          4L},
     *      new Object[] {
     *          new Long[] {1L, 3L},
     *          new Long[] {0L, 2L},
     *          3L}
     *      }
     * }</pre>
     *     The result indicates that the first substring match is <code>"1234"</code> in <code>key1
     *     </code> at index <code>4</code> to <code>7</code> which matches the substring in <code>key2
     *     </code> at index <code>5</code> to <code>8</code> and the last element in the array is the
     *     length of the substring match which is <code>4</code>. And the second substring match is
     *     <code>"bcd"</code> in <code>key1</code> at index <code>1</code> to <code>3</code> which
     *     matches the substring in <code>key2</code> at index <code>0</code> to <code>2</code> and
     *     the last element in the array is the length of the substring match which is <code>3</code>.
     */
    CompletableFuture<Map<String, Object>> lcsIdxWithMatchLen(
            String key1, String key2, long minMatchLen);
}
