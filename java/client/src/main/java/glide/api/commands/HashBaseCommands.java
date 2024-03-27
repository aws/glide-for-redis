/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Hash Commands" group for standalone clients and
 * cluster clients.
 *
 * @see <a href="https://redis.io/commands/?group=hash">Hash Commands</a>
 */
public interface HashBaseCommands {

    /**
     * Retrieves the value associated with <code>field</code> in the hash stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/hget/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param field The field in the hash stored at <code>key</code> to retrieve from the database.
     * @return The value associated with <code>field</code>, or <code>null</code> when <code>field
     *     </code> is not present in the hash or <code>key</code> does not exist.
     * @example
     *     <pre>{@code
     * String payload = client.hget("my_hash", "field1").get();
     * assert payload.equals("value");
     *
     * String payload = client.hget("my_hash", "nonexistent_field").get();
     * assert payload.equals(null);
     * }</pre>
     */
    CompletableFuture<String> hget(String key, String field);

    /**
     * Sets the specified fields to their respective values in the hash stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/hset/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param fieldValueMap A field-value map consisting of fields and their corresponding values to
     *     be set in the hash stored at the specified key.
     * @return The number of fields that were added.
     * @example
     *     <pre>{@code
     * Long num = client.hset("my_hash", Map.of("field", "value", "field2", "value2")).get();
     * assert num == 2L;
     * }</pre>
     */
    CompletableFuture<Long> hset(String key, Map<String, String> fieldValueMap);

    /**
     * Removes the specified fields from the hash stored at <code>key</code>. Specified fields that do
     * not exist within this hash are ignored.
     *
     * @see <a href="https://redis.io/commands/hdel/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to remove from the hash stored at <code>key</code>.
     * @return The number of fields that were removed from the hash, not including specified but
     *     non-existing fields.<br>
     *     If <code>key</code> does not exist, it is treated as an empty hash and it returns 0.<br>
     * @example
     *     <pre>{@code
     * Long num = client.hdel("my_hash", new String[] {"field1", "field2"}).get();
     * assert num == 2L; //Indicates that two fields were successfully removed from the hash.
     * }</pre>
     */
    CompletableFuture<Long> hdel(String key, String[] fields);

    /**
     * Returns the values associated with the specified fields in the hash stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/hmget/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields in the hash stored at <code>key</code> to retrieve from the database.
     * @return An array of values associated with the given fields, in the same order as they are
     *     requested.<br>
     *     For every field that does not exist in the hash, a null value is returned.<br>
     *     If <code>key</code> does not exist, it is treated as an empty hash, and it returns an array
     *     of null values.<br>
     * @example
     *     <pre>{@code
     * String[] values = client.hmget("my_hash", new String[] {"field1", "field2"}).get()
     * assert values.equals(new String[] {"value1", "value2"});
     * }</pre>
     */
    CompletableFuture<String[]> hmget(String key, String[] fields);

    /**
     * Returns if <code>field</code> is an existing field in the hash stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/hexists/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param field The field to check in the hash stored at <code>key</code>.
     * @return <code>True</code> if the hash contains the specified field. If the hash does not
     *     contain the field, or if the key does not exist, it returns <code>False</code>.
     * @example
     *     <pre>{@code
     * Boolean exists = client.hexists("my_hash", "field1").get();
     * assert exists;
     *
     * Boolean exists = client.hexists("my_hash", "non_existent_field").get();
     * assert !exists;
     * }</pre>
     */
    CompletableFuture<Boolean> hexists(String key, String field);

    /**
     * Returns all fields and values of the hash stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/hgetall/">redis.io</a> for details.
     * @param key The key of the hash.
     * @return A <code>Map</code> of fields and their values stored in the hash. Every field name in
     *     the map is associated with its corresponding value.<br>
     *     If <code>key</code> does not exist, it returns an empty map.
     * @example
     *     <pre>{@code
     * Map fieldValueMap = client.hgetall("my_hash").get();
     * assert fieldValueMap.equals(Map.of(field1", "value1", "field2", "value2"));
     * }</pre>
     */
    CompletableFuture<Map<String, String>> hgetall(String key);

    /**
     * Increments the number stored at <code>field</code> in the hash stored at <code>key</code> by
     * increment. By using a negative increment value, the value stored at <code>field</code> in the
     * hash stored at <code>key</code> is decremented. If <code>field</code> or <code>key</code> does
     * not exist, it is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/hincrby/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param field The field in the hash stored at <code>key</code> to increment or decrement its
     *     value.
     * @param amount The amount by which to increment or decrement the field's value. Use a negative
     *     value to decrement.
     * @return The value of <code>field</code> in the hash stored at <code>key</code> after the
     *     increment or decrement.
     * @example
     *     <pre>{@code
     * Long num = client.hincrBy("my_hash", "field1", 5).get();
     * assert num == 5L;
     * }</pre>
     */
    CompletableFuture<Long> hincrBy(String key, String field, long amount);

    /**
     * Increments the string representing a floating point number stored at <code>field</code> in the
     * hash stored at <code>key</code> by increment. By using a negative increment value, the value
     * stored at <code>field</code> in the hash stored at <code>key</code> is decremented. If <code>
     * field</code> or <code>key</code> does not exist, it is set to 0 before performing the
     * operation.
     *
     * @see <a href="https://redis.io/commands/hincrbyfloat/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param field The field in the hash stored at <code>key</code> to increment or decrement its
     *     value.
     * @param amount The amount by which to increment or decrement the field's value. Use a negative
     *     value to decrement.
     * @return The value of <code>field</code> in the hash stored at <code>key</code> after the
     *     increment or decrement.
     * @example
     *     <pre>{@code
     * Double num = client.hincrByFloat("my_hash", "field1", 2.5).get();
     * assert num == 2.5;
     * }</pre>
     */
    CompletableFuture<Double> hincrByFloat(String key, String field, double amount);
}
