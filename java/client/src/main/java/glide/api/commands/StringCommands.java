/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.commands.SetOptions;
import glide.api.models.commands.SetOptions.ConditionalSet;
import glide.api.models.commands.SetOptions.SetOptionsBuilder;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "String Commands" group for standalone clients and
 * cluster clients.
 *
 * @see <a href="https://redis.io/commands/?group=string">String Commands</a>
 */
public interface StringCommands {

    /**
     * Get the value associated with the given <code>key</code>, or <code>null</code> if no such value
     * exists.
     *
     * @see <a href="https://redis.io/commands/get/">redis.io</a> for details.
     * @param key The <code>key</code> to retrieve from the database.
     * @return Response from Redis. If <code>key</code> exists, returns the <code>value</code> of
     *     <code>key</code> as a <code>String</code>. Otherwise, return <code>null</code>.
     * @example
     *     <pre>{@code
     * String payload = client.get("key").get();
     * assert payload.equals("value");
     *
     * String payload = client.get("non_existing_key").get();
     * assert payload.equals(null);
     * }</pre>
     */
    CompletableFuture<String> get(String key);

    /**
     * Set the given <code>key</code> with the given value.
     *
     * @see <a href="https://redis.io/commands/set/">redis.io</a> for details.
     * @param key The <code>key</code> to store.
     * @param value The value to store with the given <code>key</code>.
     * @return Response from Redis containing <code>"OK"</code>.
     * @example
     *     <pre>{@code
     * String payload = client.set("key", "value").get();
     * assert payload.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> set(String key, String value);

    /**
     * Set the given key with the given value. Return value is dependent on the passed options.
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
     * String payload =
     *         client.set("key", "value", SetOptions.builder()
     *                 .conditionalSet(ONLY_IF_EXISTS)
     *                 .expiry(SetOptions.Expiry.Seconds(5L))
     *                 .build())
     *                 .get();
     * assert payload.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> set(String key, String value, SetOptions options);

    /**
     * Retrieve the values of multiple <code>keys</code>.
     *
     * @see <a href="https://redis.io/commands/mget/">redis.io</a> for details.
     * @param keys A list of keys to retrieve values for.
     * @return An array of values corresponding to the provided <code>keys</code>.<br>
     *     If a <code>key</code>is not found, its corresponding value in the list will be <code>null
     *     </code>.
     * @example
     *     <pre>{@code
     * String payload = client.mget(new String[] {"key1", "key2"}).get();
     * assert payload.equals(new String[] {"value1", "value2"});
     * }</pre>
     */
    CompletableFuture<String[]> mget(String[] keys);

    /**
     * Set multiple keys to multiple values in a single operation.
     *
     * @see <a href="https://redis.io/commands/mset/">redis.io</a> for details.
     * @param keyValueMap A key-value map consisting of keys and their respective values to set.
     * @return Always <code>OK</code>.
     * @example
     *     <pre>{@code
     * String payload = client.mset(Map.of("key1", "value1", "key2", "value2"}).get();
     * assert payload.equals("OK"));
     * }</pre>
     */
    CompletableFuture<String> mset(Map<String, String> keyValueMap);

    /**
     * Increment the number stored at <code>key</code> by one. If <code>key</code> does not exist, it
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
     * Increment the number stored at <code>key</code> by <code>amount</code>. If <code>key</code>
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
     * Increment the string representing a floating point number stored at <code>key</code> by <code>
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
     * Long num = client.incrByFloat("key", 0.5).get();
     * assert num == 7.5;
     * }</pre>
     */
    CompletableFuture<Double> incrByFloat(String key, double amount);

    /**
     * Decrement the number stored at <code>key</code> by one. If <code>key</code> does not exist, it
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
     * Decrement the number stored at <code>key</code> by <code>amount</code>. If <code>key</code>
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
}
