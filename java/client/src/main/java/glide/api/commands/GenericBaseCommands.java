/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.GlideString;
import glide.api.models.Script;
import glide.api.models.commands.ExpireOptions;
import glide.api.models.commands.RestoreOptions;
import glide.api.models.commands.ScriptOptions;
import glide.api.models.commands.ScriptOptionsGlideString;
import glide.api.models.configuration.ReadFrom;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Generic Commands" group for standalone and cluster
 * clients.
 *
 * @see <a href="https://valkey.io/commands/?group=generic">Generic Commands</a>
 */
public interface GenericBaseCommands {
    /** Redis API keyword used to replace the destination key. */
    String REPLACE_REDIS_API = "REPLACE";

    /**
     * Removes the specified <code>keys</code> from the database. A key is ignored if it does not
     * exist.
     *
     * @apiNote When in cluster mode, the command may route to multiple nodes when <code>keys</code>
     *     map to different hash slots.
     * @see <a href="https://valkey.io/commands/del/">redis.io</a> for details.
     * @param keys The keys we wanted to remove.
     * @return The number of keys that were removed.
     * @example
     *     <pre>{@code
     * Long num = client.del(new String[] {"key1", "key2"}).get();
     * assert num == 2L;
     * }</pre>
     */
    CompletableFuture<Long> del(String[] keys);

    /**
     * Removes the specified <code>keys</code> from the database. A key is ignored if it does not
     * exist.
     *
     * @apiNote When in cluster mode, the command may route to multiple nodes when <code>keys</code>
     *     map to different hash slots.
     * @see <a href="https://valkey.io/commands/del/">redis.io</a> for details.
     * @param keys The keys we wanted to remove.
     * @return The number of keys that were removed.
     * @example
     *     <pre>{@code
     * Long num = client.del(new GlideString[] {gs("key1"), gs("key2")}).get();
     * assert num == 2L;
     * }</pre>
     */
    CompletableFuture<Long> del(GlideString[] keys);

    /**
     * Returns the number of keys in <code>keys</code> that exist in the database.
     *
     * @apiNote When in cluster mode, the command may route to multiple nodes when <code>keys</code>
     *     map to different hash slots.
     * @see <a href="https://valkey.io/commands/exists/">redis.io</a> for details.
     * @param keys The keys list to check.
     * @return The number of keys that exist. If the same existing key is mentioned in <code>keys
     *     </code> multiple times, it will be counted multiple times.
     * @example
     *     <pre>{@code
     * Long result = client.exists(new String[] {"my_key", "invalid_key"}).get();
     * assert result == 1L;
     * }</pre>
     */
    CompletableFuture<Long> exists(String[] keys);

    /**
     * Returns the number of keys in <code>keys</code> that exist in the database.
     *
     * @apiNote When in cluster mode, the command may route to multiple nodes when <code>keys</code>
     *     map to different hash slots.
     * @see <a href="https://valkey.io/commands/exists/">redis.io</a> for details.
     * @param keys The keys list to check.
     * @return The number of keys that exist. If the same existing key is mentioned in <code>keys
     *     </code> multiple times, it will be counted multiple times.
     * @example
     *     <pre>{@code
     * Long result = client.exists(new GlideString[] {gs("my_key"), gs("invalid_key")}).get();
     * assert result == 1L;
     * }</pre>
     */
    CompletableFuture<Long> exists(GlideString[] keys);

    /**
     * Unlink (delete) multiple <code>keys</code> from the database. A key is ignored if it does not
     * exist. This command, similar to <a href="https://valkey.io/commands/del/">DEL</a>, removes
     * specified keys and ignores non-existent ones. However, this command does not block the server,
     * while <a href="https://valkey.io/commands/del/">DEL</a> does.
     *
     * @apiNote When in cluster mode, the command may route to multiple nodes when <code>keys</code>
     *     map to different hash slots.
     * @see <a href="https://valkey.io/commands/unlink/">redis.io</a> for details.
     * @param keys The list of keys to unlink.
     * @return The number of <code>keys</code> that were unlinked.
     * @example
     *     <pre>{@code
     * Long result = client.unlink(new String[] {"my_key"}).get();
     * assert result == 1L;
     * }</pre>
     */
    CompletableFuture<Long> unlink(String[] keys);

    /**
     * Unlink (delete) multiple <code>keys</code> from the database. A key is ignored if it does not
     * exist. This command, similar to <a href="https://valkey.io/commands/del/">DEL</a>, removes
     * specified keys and ignores non-existent ones. However, this command does not block the server,
     * while <a href="https://valkey.io/commands/del/">DEL</a> does.
     *
     * @apiNote When in cluster mode, the command may route to multiple nodes when <code>keys</code>
     *     map to different hash slots.
     * @see <a href="https://valkey.io/commands/unlink/">redis.io</a> for details.
     * @param keys The list of keys to unlink.
     * @return The number of <code>keys</code> that were unlinked.
     * @example
     *     <pre>{@code
     * Long result = client.unlink(new GlideString[] {gs("my_key")}).get();
     * assert result == 1L;
     * }</pre>
     */
    CompletableFuture<Long> unlink(GlideString[] keys);

    /**
     * Sets a timeout on <code>key</code> in seconds. After the timeout has expired, the <code>key
     * </code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing <code>expire
     * </code> set, the time to live is updated to the new value.<br>
     * If <code>seconds</code> is a non-positive number, the <code>key</code> will be deleted rather
     * than expired.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://valkey.io/commands/expire/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param seconds The timeout in seconds.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.expire("my_key", 60).get();
     * assert isSet; //Indicates that a timeout of 60 seconds has been set for "my_key."
     * }</pre>
     */
    CompletableFuture<Boolean> expire(String key, long seconds);

    /**
     * Sets a timeout on <code>key</code> in seconds. After the timeout has expired, the <code>key
     * </code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing <code>expire
     * </code> set, the time to live is updated to the new value.<br>
     * If <code>seconds</code> is a non-positive number, the <code>key</code> will be deleted rather
     * than expired.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://valkey.io/commands/expire/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param seconds The timeout in seconds.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.expire(gs("my_key"), 60).get();
     * assert isSet; //Indicates that a timeout of 60 seconds has been set for gs("my_key").
     * }</pre>
     */
    CompletableFuture<Boolean> expire(GlideString key, long seconds);

    /**
     * Sets a timeout on <code>key</code> in seconds. After the timeout has expired, the <code>key
     * </code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing <code>expire
     * </code> set, the time to live is updated to the new value.<br>
     * If <code>seconds</code> is a non-positive number, the <code>key</code> will be deleted rather
     * than expired.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://valkey.io/commands/expire/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param seconds The timeout in seconds.
     * @param expireOptions The expire options.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist, or operation skipped due to the provided
     *     arguments.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.expire("my_key", 60, ExpireOptions.HAS_NO_EXPIRY).get();
     * assert isSet; //Indicates that a timeout of 60 seconds has been set for "my_key".
     * }</pre>
     */
    CompletableFuture<Boolean> expire(String key, long seconds, ExpireOptions expireOptions);

    /**
     * Sets a timeout on <code>key</code> in seconds. After the timeout has expired, the <code>key
     * </code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing <code>expire
     * </code> set, the time to live is updated to the new value.<br>
     * If <code>seconds</code> is a non-positive number, the <code>key</code> will be deleted rather
     * than expired.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://valkey.io/commands/expire/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param seconds The timeout in seconds.
     * @param expireOptions The expire options.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist, or operation skipped due to the provided
     *     arguments.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.expire(gs("my_key"), 60, ExpireOptions.HAS_NO_EXPIRY).get();
     * assert isSet; //Indicates that a timeout of 60 seconds has been set for gs("my_key").
     * }</pre>
     */
    CompletableFuture<Boolean> expire(GlideString key, long seconds, ExpireOptions expireOptions);

    /**
     * Sets a timeout on <code>key</code>. It takes an absolute Unix timestamp (seconds since January
     * 1, 1970) instead of specifying the number of seconds.<br>
     * A timestamp in the past will delete the <code>key</code> immediately. After the timeout has
     * expired, the <code>key</code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing <code>expire</code> set, the time to live is
     * updated to the new value.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://valkey.io/commands/expireat/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param unixSeconds The timeout in an absolute Unix timestamp.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.expireAt("my_key", Instant.now().getEpochSecond() + 10).get();
     * assert isSet;
     * }</pre>
     */
    CompletableFuture<Boolean> expireAt(String key, long unixSeconds);

    /**
     * Sets a timeout on <code>key</code>. It takes an absolute Unix timestamp (seconds since January
     * 1, 1970) instead of specifying the number of seconds.<br>
     * A timestamp in the past will delete the <code>key</code> immediately. After the timeout has
     * expired, the <code>key</code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing <code>expire</code> set, the time to live is
     * updated to the new value.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://valkey.io/commands/expireat/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param unixSeconds The timeout in an absolute Unix timestamp.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.expireAt(gs("my_key"), Instant.now().getEpochSecond() + 10).get();
     * assert isSet;
     * }</pre>
     */
    CompletableFuture<Boolean> expireAt(GlideString key, long unixSeconds);

    /**
     * Sets a timeout on <code>key</code>. It takes an absolute Unix timestamp (seconds since January
     * 1, 1970) instead of specifying the number of seconds.<br>
     * A timestamp in the past will delete the <code>key</code> immediately. After the timeout has
     * expired, the <code>key</code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing <code>expire</code> set, the time to live is
     * updated to the new value.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://valkey.io/commands/expireat/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param unixSeconds The timeout in an absolute Unix timestamp.
     * @param expireOptions The expire options.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist, or operation skipped due to the provided
     *     arguments.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.expireAt("my_key", Instant.now().getEpochSecond() + 10, ExpireOptions.HasNoExpiry).get();
     * assert isSet;
     * }</pre>
     */
    CompletableFuture<Boolean> expireAt(String key, long unixSeconds, ExpireOptions expireOptions);

    /**
     * Sets a timeout on <code>key</code>. It takes an absolute Unix timestamp (seconds since January
     * 1, 1970) instead of specifying the number of seconds.<br>
     * A timestamp in the past will delete the <code>key</code> immediately. After the timeout has
     * expired, the <code>key</code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing <code>expire</code> set, the time to live is
     * updated to the new value.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://valkey.io/commands/expireat/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param unixSeconds The timeout in an absolute Unix timestamp.
     * @param expireOptions The expire options.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist, or operation skipped due to the provided
     *     arguments.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.expireAt(gs("my_key"), Instant.now().getEpochSecond() + 10, ExpireOptions.HasNoExpiry).get();
     * assert isSet;
     * }</pre>
     */
    CompletableFuture<Boolean> expireAt(
            GlideString key, long unixSeconds, ExpireOptions expireOptions);

    /**
     * Sets a timeout on <code>key</code> in milliseconds. After the timeout has expired, the <code>
     * key</code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing <code>
     * expire</code> set, the time to live is updated to the new value.<br>
     * If <code>milliseconds</code> is a non-positive number, the <code>key</code> will be deleted
     * rather than expired.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://valkey.io/commands/pexpire/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param milliseconds The timeout in milliseconds.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.pexpire("my_key", 60000).get();
     * assert isSet;
     * }</pre>
     */
    CompletableFuture<Boolean> pexpire(String key, long milliseconds);

    /**
     * Sets a timeout on <code>key</code> in milliseconds. After the timeout has expired, the <code>
     * key</code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing <code>
     * expire</code> set, the time to live is updated to the new value.<br>
     * If <code>milliseconds</code> is a non-positive number, the <code>key</code> will be deleted
     * rather than expired.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://valkey.io/commands/pexpire/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param milliseconds The timeout in milliseconds.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.pexpire(gs("my_key"), 60000).get();
     * assert isSet;
     * }</pre>
     */
    CompletableFuture<Boolean> pexpire(GlideString key, long milliseconds);

    /**
     * Sets a timeout on <code>key</code> in milliseconds. After the timeout has expired, the <code>
     * key</code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing expire set, the time to live is updated to the new
     * value.<br>
     * If <code>milliseconds</code> is a non-positive number, the <code>key</code> will be deleted
     * rather than expired.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://valkey.io/commands/pexpire/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param milliseconds The timeout in milliseconds.
     * @param expireOptions The expire options.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist, or operation skipped due to the provided
     *     arguments.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.pexpire("my_key", 60000, ExpireOptions.HasNoExpiry).get();
     * assert isSet;
     * }</pre>
     */
    CompletableFuture<Boolean> pexpire(String key, long milliseconds, ExpireOptions expireOptions);

    /**
     * Sets a timeout on <code>key</code> in milliseconds. After the timeout has expired, the <code>
     * key</code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing expire set, the time to live is updated to the new
     * value.<br>
     * If <code>milliseconds</code> is a non-positive number, the <code>key</code> will be deleted
     * rather than expired.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://valkey.io/commands/pexpire/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param milliseconds The timeout in milliseconds.
     * @param expireOptions The expire options.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist, or operation skipped due to the provided
     *     arguments.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.pexpire(gs("my_key"), 60000, ExpireOptions.HasNoExpiry).get();
     * assert isSet;
     * }</pre>
     */
    CompletableFuture<Boolean> pexpire(
            GlideString key, long milliseconds, ExpireOptions expireOptions);

    /**
     * Sets a timeout on <code>key</code>. It takes an absolute Unix timestamp (milliseconds since
     * January 1, 1970) instead of specifying the number of milliseconds.<br>
     * A timestamp in the past will delete the <code>key</code> immediately. After the timeout has
     * expired, the <code>key</code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing <code>expire</code> set, the time to live is
     * updated to the new value.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://valkey.io/commands/pexpireat/">redis.io</a> for details.
     * @param key The <code>key</code> to set timeout on it.
     * @param unixMilliseconds The timeout in an absolute Unix timestamp.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.pexpireAt("my_key", Instant.now().toEpochMilli() + 10).get();
     * assert isSet;
     * }</pre>
     */
    CompletableFuture<Boolean> pexpireAt(String key, long unixMilliseconds);

    /**
     * Sets a timeout on <code>key</code>. It takes an absolute Unix timestamp (milliseconds since
     * January 1, 1970) instead of specifying the number of milliseconds.<br>
     * A timestamp in the past will delete the <code>key</code> immediately. After the timeout has
     * expired, the <code>key</code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing <code>expire</code> set, the time to live is
     * updated to the new value.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://valkey.io/commands/pexpireat/">redis.io</a> for details.
     * @param key The <code>key</code> to set timeout on it.
     * @param unixMilliseconds The timeout in an absolute Unix timestamp.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.pexpireAt(gs("my_key"), Instant.now().toEpochMilli() + 10).get();
     * assert isSet;
     * }</pre>
     */
    CompletableFuture<Boolean> pexpireAt(GlideString key, long unixMilliseconds);

    /**
     * Sets a timeout on <code>key</code>. It takes an absolute Unix timestamp (milliseconds since
     * January 1, 1970) instead of specifying the number of milliseconds.<br>
     * A timestamp in the past will delete the <code>key</code> immediately. After the timeout has
     * expired, the <code>key</code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing <code>expire</code> set, the time to live is
     * updated to the new value.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://valkey.io/commands/pexpireat/">redis.io</a> for details.
     * @param key The <code>key</code> to set timeout on it.
     * @param unixMilliseconds The timeout in an absolute Unix timestamp.
     * @param expireOptions The expire option.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist, or operation skipped due to the provided
     *     arguments.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.pexpireAt("my_key", Instant.now().toEpochMilli() + 10, ExpireOptions.HasNoExpiry).get();
     * assert isSet;
     * }</pre>
     */
    CompletableFuture<Boolean> pexpireAt(
            String key, long unixMilliseconds, ExpireOptions expireOptions);

    /**
     * Sets a timeout on <code>key</code>. It takes an absolute Unix timestamp (milliseconds since
     * January 1, 1970) instead of specifying the number of milliseconds.<br>
     * A timestamp in the past will delete the <code>key</code> immediately. After the timeout has
     * expired, the <code>key</code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing <code>expire</code> set, the time to live is
     * updated to the new value.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://valkey.io/commands/pexpireat/">redis.io</a> for details.
     * @param key The <code>key</code> to set timeout on it.
     * @param unixMilliseconds The timeout in an absolute Unix timestamp.
     * @param expireOptions The expire option.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist, or operation skipped due to the provided
     *     arguments.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.pexpireAt(gs("my_key"), Instant.now().toEpochMilli() + 10, ExpireOptions.HasNoExpiry).get();
     * assert isSet;
     * }</pre>
     */
    CompletableFuture<Boolean> pexpireAt(
            GlideString key, long unixMilliseconds, ExpireOptions expireOptions);

    /**
     * Returns the remaining time to live of <code>key</code> that has a timeout, in seconds.
     *
     * @see <a href="https://valkey.io/commands/ttl/">redis.io</a> for details.
     * @param key The <code>key</code> to return its timeout.
     * @return TTL in seconds, <code>-2</code> if <code>key</code> does not exist, or <code>-1</code>
     *     if <code>key</code> exists but has no associated expiration.
     * @example
     *     <pre>{@code
     * Long timeRemaining = client.ttl("my_key").get();
     * assert timeRemaining == 3600L; //Indicates that "my_key" has a remaining time to live of 3600 seconds.
     *
     * Long timeRemaining = client.ttl("nonexistent_key").get();
     * assert timeRemaining == -2L; //Returns -2 for a non-existing key.
     * }</pre>
     */
    CompletableFuture<Long> ttl(String key);

    /**
     * Returns the remaining time to live of <code>key</code> that has a timeout, in seconds.
     *
     * @see <a href="https://valkey.io/commands/ttl/">redis.io</a> for details.
     * @param key The <code>key</code> to return its timeout.
     * @return TTL in seconds, <code>-2</code> if <code>key</code> does not exist, or <code>-1</code>
     *     if <code>key</code> exists but has no associated expiration.
     * @example
     *     <pre>{@code
     * Long timeRemaining = client.ttl(gs("my_key")).get();
     * assert timeRemaining == 3600L; //Indicates that gs("my_key") has a remaining time to live of 3600 seconds.
     *
     * Long timeRemaining = client.ttl(gs("nonexistent_key")).get();
     * assert timeRemaining == -2L; //Returns -2 for a non-existing key.
     * }</pre>
     */
    CompletableFuture<Long> ttl(GlideString key);

    /**
     * Returns the absolute Unix timestamp (since January 1, 1970) at which the given <code>key</code>
     * will expire, in seconds.<br>
     * To get the expiration with millisecond precision, use {@link #pexpiretime(String)}.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://valkey.io/commands/expiretime/">redis.io</a> for details.
     * @param key The <code>key</code> to determine the expiration value of.
     * @return The expiration Unix timestamp in seconds. <code>-2</code> if <code>key</code> does not
     *     exist, or <code>-1</code> if <code>key</code> exists but has no associated expiration.
     * @example
     *     <pre>{@code
     * Long expiration = client.expiretime("my_key").get();
     * System.out.printf("The key expires at %d epoch time", expiration);
     * }</pre>
     */
    CompletableFuture<Long> expiretime(String key);

    /**
     * Returns the absolute Unix timestamp (since January 1, 1970) at which the given <code>key</code>
     * will expire, in seconds.<br>
     * To get the expiration with millisecond precision, use {@link #pexpiretime(String)}.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://valkey.io/commands/expiretime/">redis.io</a> for details.
     * @param key The <code>key</code> to determine the expiration value of.
     * @return The expiration Unix timestamp in seconds. <code>-2</code> if <code>key</code> does not
     *     exist, or <code>-1</code> if <code>key</code> exists but has no associated expiration.
     * @example
     *     <pre>{@code
     * Long expiration = client.expiretime(gs("my_key")).get();
     * System.out.printf("The key expires at %d epoch time", expiration);
     * }</pre>
     */
    CompletableFuture<Long> expiretime(GlideString key);

    /**
     * Returns the absolute Unix timestamp (since January 1, 1970) at which the given <code>key</code>
     * will expire, in milliseconds.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://valkey.io/commands/pexpiretime/">redis.io</a> for details.
     * @param key The <code>key</code> to determine the expiration value of.
     * @return The expiration Unix timestamp in milliseconds. <code>-2</code> if <code>key</code> does
     *     not exist, or <code>-1</code> if <code>key</code> exists but has no associated expiration.
     * @example
     *     <pre>{@code
     * Long expiration = client.pexpiretime("my_key").get();
     * System.out.printf("The key expires at %d epoch time (ms)", expiration);
     * }</pre>
     */
    CompletableFuture<Long> pexpiretime(String key);

    /**
     * Returns the absolute Unix timestamp (since January 1, 1970) at which the given <code>key</code>
     * will expire, in milliseconds.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://valkey.io/commands/pexpiretime/">redis.io</a> for details.
     * @param key The <code>key</code> to determine the expiration value of.
     * @return The expiration Unix timestamp in milliseconds. <code>-2</code> if <code>key</code> does
     *     not exist, or <code>-1</code> if <code>key</code> exists but has no associated expiration.
     * @example
     *     <pre>{@code
     * Long expiration = client.pexpiretime(gs("my_key")).get();
     * System.out.printf("The key expires at %d epoch time (ms)", expiration);
     * }</pre>
     */
    CompletableFuture<Long> pexpiretime(GlideString key);

    // TODO move invokeScript to ScriptingAndFunctionsBaseCommands
    // TODO add note to invokeScript about routing on cluster client
    /**
     * Invokes a Lua script.<br>
     * This method simplifies the process of invoking scripts on a Redis server by using an object
     * that represents a Lua script. The script loading and execution will all be handled internally.
     * If the script has not already been loaded, it will be loaded automatically using the Redis
     * <code>SCRIPT LOAD</code> command. After that, it will be invoked using the Redis <code>EVALSHA
     * </code> command.
     *
     * @see <a href="https://valkey.io/commands/script-load/">SCRIPT LOAD</a> and <a
     *     href="https://valkey.io/commands/evalsha/">EVALSHA</a> for details.
     * @param script The Lua script to execute.
     * @return a value that depends on the script that was executed.
     * @example
     *     <pre>{@code
     * try(Script luaScript = new Script("return 'Hello'", false)) {
     *     String result = (String) client.invokeScript(luaScript).get();
     *     assert result.equals("Hello");
     * }
     * }</pre>
     */
    CompletableFuture<Object> invokeScript(Script script);

    /**
     * Invokes a Lua script with its keys and arguments.<br>
     * This method simplifies the process of invoking scripts on a Redis server by using an object
     * that represents a Lua script. The script loading, argument preparation, and execution will all
     * be handled internally. If the script has not already been loaded, it will be loaded
     * automatically using the Redis <code>SCRIPT LOAD</code> command. After that, it will be invoked
     * using the Redis <code>EVALSHA</code> command.
     *
     * @see <a href="https://valkey.io/commands/script-load/">SCRIPT LOAD</a> and <a
     *     href="https://valkey.io/commands/evalsha/">EVALSHA</a> for details.
     * @param script The Lua script to execute.
     * @param options The script option that contains keys and arguments for the script.
     * @return a value that depends on the script that was executed.
     * @example
     *     <pre>{@code
     * try(Script luaScript = new Script("return { KEYS[1], ARGV[1] }", false)) {
     *     ScriptOptions scriptOptions = ScriptOptions.builder().key("foo").arg("bar").build();
     *     Object[] result = (Object[]) client.invokeScript(luaScript, scriptOptions).get();
     *     assert result[0].equals("foo");
     *     assert result[1].equals("bar");
     * }
     * }</pre>
     */
    CompletableFuture<Object> invokeScript(Script script, ScriptOptions options);

    /**
     * Invokes a Lua script with its keys and arguments.<br>
     * This method simplifies the process of invoking scripts on a Redis server by using an object
     * that represents a Lua script. The script loading, argument preparation, and execution will all
     * be handled internally. If the script has not already been loaded, it will be loaded
     * automatically using the Redis <code>SCRIPT LOAD</code> command. After that, it will be invoked
     * using the Redis <code>EVALSHA</code> command.
     *
     * @see <a href="https://valkey.io/commands/script-load/">SCRIPT LOAD</a> and <a
     *     href="https://valkey.io/commands/evalsha/">EVALSHA</a> for details.
     * @param script The Lua script to execute.
     * @param options The script option that contains keys and arguments for the script.
     * @return a value that depends on the script that was executed.
     * @example
     *     <pre>{@code
     * try(Script luaScript = new Script(gs("return { KEYS[1], ARGV[1] }", true))) {
     *     ScriptOptionsGlideString scriptOptions = ScriptOptionsGlideString.builder().key(gs("foo")).arg(gs("bar")).build();
     *     Object[] result = (Object[]) client.invokeScript(luaScript, scriptOptions).get();
     *     assert result[0].equals(gs("foo"));
     *     assert result[1].equals(gs("bar"));
     * }
     * }</pre>
     */
    CompletableFuture<Object> invokeScript(Script script, ScriptOptionsGlideString options);

    /**
     * Returns the remaining time to live of <code>key</code> that has a timeout, in milliseconds.
     *
     * @see <a href="https://valkey.io/commands/pttl/">redis.io</a> for details.
     * @param key The key to return its timeout.
     * @return TTL in milliseconds. <code>-2</code> if <code>key</code> does not exist, <code>-1
     *     </code> if <code>key</code> exists but has no associated expire.
     * @example
     *     <pre>{@code
     * Long timeRemainingMS = client.pttl("my_key").get()
     * assert timeRemainingMS == 5000L // Indicates that "my_key" has a remaining time to live of 5000 milliseconds.
     *
     * Long timeRemainingMS = client.pttl("nonexistent_key").get();
     * assert timeRemainingMS == -2L; // Returns -2 for a non-existing key.
     * }</pre>
     */
    CompletableFuture<Long> pttl(String key);

    /**
     * Returns the remaining time to live of <code>key</code> that has a timeout, in milliseconds.
     *
     * @see <a href="https://valkey.io/commands/pttl/">redis.io</a> for details.
     * @param key The key to return its timeout.
     * @return TTL in milliseconds. <code>-2</code> if <code>key</code> does not exist, <code>-1
     *     </code> if <code>key</code> exists but has no associated expire.
     * @example
     *     <pre>{@code
     * Long timeRemainingMS = client.pttl(gs("my_key")).get()
     * assert timeRemainingMS == 5000L // Indicates that gs("my_key") has a remaining time to live of 5000 milliseconds.
     *
     * Long timeRemainingMS = client.pttl(gs("nonexistent_key")).get();
     * assert timeRemainingMS == -2L; // Returns -2 for a non-existing key.
     * }</pre>
     */
    CompletableFuture<Long> pttl(GlideString key);

    /**
     * Removes the existing timeout on <code>key</code>, turning the <code>key</code> from volatile (a
     * <code>key</code> with an expire set) to persistent (a <code>key</code> that will never expire
     * as no timeout is associated).
     *
     * @see <a href="https://valkey.io/commands/persist/">redis.io</a> for details.
     * @param key The <code>key</code> to remove the existing timeout on.
     * @return <code>false</code> if <code>key</code> does not exist or does not have an associated
     *     timeout, <code>true</code> if the timeout has been removed.
     * @example
     *     <pre>{@code
     * Boolean timeoutRemoved = client.persist("my_key").get();
     * assert timeoutRemoved; // Indicates that the timeout associated with the key "my_key" was successfully removed.
     * }</pre>
     */
    CompletableFuture<Boolean> persist(String key);

    /**
     * Removes the existing timeout on <code>key</code>, turning the <code>key</code> from volatile (a
     * <code>key</code> with an expire set) to persistent (a <code>key</code> that will never expire
     * as no timeout is associated).
     *
     * @see <a href="https://valkey.io/commands/persist/">redis.io</a> for details.
     * @param key The <code>key</code> to remove the existing timeout on.
     * @return <code>false</code> if <code>key</code> does not exist or does not have an associated
     *     timeout, <code>true</code> if the timeout has been removed.
     * @example
     *     <pre>{@code
     * Boolean timeoutRemoved = client.persist(gs("my_key")).get();
     * assert timeoutRemoved; // Indicates that the timeout associated with the key "my_key" was successfully removed.
     * }</pre>
     */
    CompletableFuture<Boolean> persist(GlideString key);

    /**
     * Returns the string representation of the type of the value stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/type/">redis.io</a> for details.
     * @param key The <code>key</code> to check its data type.
     * @return If the <code>key</code> exists, the type of the stored value is returned. Otherwise, a
     *     "none" string is returned.
     * @example
     *     <pre>{@code
     * String type = client.type("StringKey").get();
     * assert type.equals("string");
     *
     * type = client.type("ListKey").get();
     * assert type.equals("list");
     * }</pre>
     */
    CompletableFuture<String> type(String key);

    /**
     * Returns the string representation of the type of the value stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/type/">redis.io</a> for details.
     * @param key The <code>key</code> to check its data type.
     * @return If the <code>key</code> exists, the type of the stored value is returned. Otherwise, a
     *     "none" string is returned.
     * @example
     *     <pre>{@code
     * String type = client.type(gs("StringKey")).get();
     * assert type.equals("string");
     *
     * type = client.type(gs("ListKey")).get();
     * assert type.equals("list");
     * }</pre>
     */
    CompletableFuture<String> type(GlideString key);

    /**
     * Returns the internal encoding for the Redis object stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/object-encoding/">redis.io</a> for details.
     * @param key The <code>key</code> of the object to get the internal encoding of.
     * @return If <code>key</code> exists, returns the internal encoding of the object stored at
     *     <code>key</code> as a <code>String</code>. Otherwise, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * String encoding = client.objectEncoding("my_hash").get();
     * assert encoding.equals("listpack");
     *
     * encoding = client.objectEncoding("non_existing_key").get();
     * assert encoding == null;
     * }</pre>
     */
    CompletableFuture<String> objectEncoding(String key);

    /**
     * Returns the internal encoding for the Redis object stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/object-encoding/">redis.io</a> for details.
     * @param key The <code>key</code> of the object to get the internal encoding of.
     * @return If <code>key</code> exists, returns the internal encoding of the object stored at
     *     <code>key</code> as a <code>String</code>. Otherwise, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * String encoding = client.objectEncoding(gs("my_hash")).get();
     * assert encoding.equals("listpack");
     *
     * encoding = client.objectEncoding(gs("non_existing_key")).get();
     * assert encoding == null;
     * }</pre>
     */
    CompletableFuture<String> objectEncoding(GlideString key);

    /**
     * Returns the logarithmic access frequency counter of a Redis object stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/object-freq/">redis.io</a> for details.
     * @param key The <code>key</code> of the object to get the logarithmic access frequency counter
     *     of.
     * @return If <code>key</code> exists, returns the logarithmic access frequency counter of the
     *     object stored at <code>key</code> as a <code>Long</code>. Otherwise, returns <code>null
     *     </code>.
     * @example
     *     <pre>{@code
     * Long frequency = client.objectFreq("my_hash").get();
     * assert frequency == 2L;
     *
     * frequency = client.objectFreq("non_existing_key").get();
     * assert frequency == null;
     * }</pre>
     */
    CompletableFuture<Long> objectFreq(String key);

    /**
     * Returns the logarithmic access frequency counter of a Redis object stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/object-freq/">redis.io</a> for details.
     * @param key The <code>key</code> of the object to get the logarithmic access frequency counter
     *     of.
     * @return If <code>key</code> exists, returns the logarithmic access frequency counter of the
     *     object stored at <code>key</code> as a <code>Long</code>. Otherwise, returns <code>null
     *     </code>.
     * @example
     *     <pre>{@code
     * Long frequency = client.objectFreq(gs("my_hash")).get();
     * assert frequency == 2L;
     *
     * frequency = client.objectFreq(gs("non_existing_key")).get();
     * assert frequency == null;
     * }</pre>
     */
    CompletableFuture<Long> objectFreq(GlideString key);

    /**
     * Returns the time in seconds since the last access to the value stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/object-idletime/">redis.io</a> for details.
     * @param key The <code>key</code> of the object to get the idle time of.
     * @return If <code>key</code> exists, returns the idle time in seconds. Otherwise, returns <code>
     *     null</code>.
     * @example
     *     <pre>{@code
     * Long idletime = client.objectIdletime("my_hash").get();
     * assert idletime == 2L;
     *
     * idletime = client.objectIdletime("non_existing_key").get();
     * assert idletime == null;
     * }</pre>
     */
    CompletableFuture<Long> objectIdletime(String key);

    /**
     * Returns the time in seconds since the last access to the value stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/object-idletime/">redis.io</a> for details.
     * @param key The <code>key</code> of the object to get the idle time of.
     * @return If <code>key</code> exists, returns the idle time in seconds. Otherwise, returns <code>
     *     null</code>.
     * @example
     *     <pre>{@code
     * Long idletime = client.objectIdletime(gs("my_hash")).get();
     * assert idletime == 2L;
     *
     * idletime = client.objectIdletime(gs("non_existing_key")).get();
     * assert idletime == null;
     * }</pre>
     */
    CompletableFuture<Long> objectIdletime(GlideString key);

    /**
     * Returns the reference count of the object stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/object-refcount/">redis.io</a> for details.
     * @param key The <code>key</code> of the object to get the reference count of.
     * @return If <code>key</code> exists, returns the reference count of the object stored at <code>
     *     key</code> as a <code>Long</code>. Otherwise, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Long refcount = client.objectRefcount("my_hash").get();
     * assert refcount == 2L;
     *
     * refcount = client.objectRefcount("non_existing_key").get();
     * assert refcount == null;
     * }</pre>
     */
    CompletableFuture<Long> objectRefcount(String key);

    /**
     * Returns the reference count of the object stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/object-refcount/">redis.io</a> for details.
     * @param key The <code>key</code> of the object to get the reference count of.
     * @return If <code>key</code> exists, returns the reference count of the object stored at <code>
     *     key</code> as a <code>Long</code>. Otherwise, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Long refcount = client.objectRefcount(gs("my_hash")).get();
     * assert refcount == 2L;
     *
     * refcount = client.objectRefcount(gs("non_existing_key")).get();
     * assert refcount == null;
     * }</pre>
     */
    CompletableFuture<Long> objectRefcount(GlideString key);

    /**
     * Renames <code>key</code> to <code>newKey</code>.<br>
     * If <code>newKey</code> already exists it is overwritten.
     *
     * @apiNote When in cluster mode, both <code>key</code> and <code>newKey</code> must map to the
     *     same hash slot.
     * @see <a href="https://valkey.io/commands/rename/">redis.io</a> for details.
     * @param key The key to rename.
     * @param newKey The new name of the key.
     * @return If the <code>key</code> was successfully renamed, return <code>"OK"</code>. If <code>
     *     key</code> does not exist, an error is thrown.
     * @example
     *     <pre>{@code
     * String value = client.set("key", "value").get();
     * value = client.rename("key", "newKeyName").get();
     * assert value.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> rename(String key, String newKey);

    /**
     * Renames <code>key</code> to <code>newKey</code>.<br>
     * If <code>newKey</code> already exists it is overwritten.
     *
     * @apiNote When in cluster mode, both <code>key</code> and <code>newKey</code> must map to the
     *     same hash slot.
     * @see <a href="https://valkey.io/commands/rename/">redis.io</a> for details.
     * @param key The key to rename.
     * @param newKey The new name of the key.
     * @return If the <code>key</code> was successfully renamed, return <code>"OK"</code>. If <code>
     *     key</code> does not exist, an error is thrown.
     * @example
     *     <pre>{@code
     * String value = client.set(gs("key"), gs("value")).get();
     * value = client.rename(gs("key"), gs("newKeyName")).get();
     * assert value.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> rename(GlideString key, GlideString newKey);

    /**
     * Renames <code>key</code> to <code>newKey</code> if <code>newKey</code> does not yet exist.
     *
     * @apiNote When in cluster mode, both <code>key</code> and <code>newKey</code> must map to the
     *     same hash slot.
     * @see <a href="https://valkey.io/commands/renamenx/">redis.io</a> for details.
     * @param key The key to rename.
     * @param newKey The new key name.
     * @return <code>true</code> if <code>key</code> was renamed to <code>newKey</code>, <code>false
     *     </code> if <code>newKey</code> already exists.
     * @example
     *     <pre>{@code
     * Boolean renamed = client.renamenx("old_key", "new_key").get();
     * assert renamed;
     * }</pre>
     */
    CompletableFuture<Boolean> renamenx(String key, String newKey);

    /**
     * Renames <code>key</code> to <code>newKey</code> if <code>newKey</code> does not yet exist.
     *
     * @apiNote When in cluster mode, both <code>key</code> and <code>newKey</code> must map to the
     *     same hash slot.
     * @see <a href="https://valkey.io/commands/renamenx/">redis.io</a> for details.
     * @param key The key to rename.
     * @param newKey The new key name.
     * @return <code>true</code> if <code>key</code> was renamed to <code>newKey</code>, <code>false
     *     </code> if <code>newKey</code> already exists.
     * @example
     *     <pre>{@code
     * Boolean renamed = client.renamenx(gs("old_key"), gs("new_key")).get();
     * assert renamed;
     * }</pre>
     */
    CompletableFuture<Boolean> renamenx(GlideString key, GlideString newKey);

    /**
     * Updates the last access time of specified <code>keys</code>.
     *
     * @apiNote When in cluster mode, the command may route to multiple nodes when <code>keys</code>
     *     map to different hash slots.
     * @see <a href="https://valkey.io/commands/touch/">redis.io</a> for details.
     * @param keys The keys to update last access time.
     * @return The number of keys that were updated.
     * @example
     *     <pre>{@code
     * Long payload = client.touch(new String[] {"myKey1", "myKey2", "nonExistentKey"}).get();
     * assert payload == 2L; // Last access time of 2 keys has been updated.
     * }</pre>
     */
    CompletableFuture<Long> touch(String[] keys);

    /**
     * Updates the last access time of specified <code>keys</code>.
     *
     * @apiNote When in cluster mode, the command may route to multiple nodes when <code>keys</code>
     *     map to different hash slots.
     * @see <a href="https://valkey.io/commands/touch/">redis.io</a> for details.
     * @param keys The keys to update last access time.
     * @return The number of keys that were updated.
     * @example
     *     <pre>{@code
     * Long payload = client.touch(new GlideString[] {gs("myKey1"), gs("myKey2"), gs("nonExistentKey")}).get();
     * assert payload == 2L; // Last access time of 2 keys has been updated.
     * }</pre>
     */
    CompletableFuture<Long> touch(GlideString[] keys);

    /**
     * Copies the value stored at the <code>source</code> to the <code>destination</code> key if the
     * <code>destination</code> key does not yet exist.
     *
     * @apiNote When in cluster mode, both <code>source</code> and <code>destination</code> must map
     *     to the same hash slot.
     * @since Redis 6.2.0 and above.
     * @see <a href="https://valkey.io/commands/copy/">redis.io</a> for details.
     * @param source The key to the source value.
     * @param destination The key where the value should be copied to.
     * @return <code>true</code> if <code>source</code> was copied, <code>false</code> if <code>source
     * </code> was not copied.
     * @example
     *     <pre>{@code
     * client.set("test1", "one").get();
     * client.set("test2", "two").get();
     * assert !client.copy("test1", "test2").get();
     * assert client.copy("test1", "test2").get();
     * }</pre>
     */
    CompletableFuture<Boolean> copy(String source, String destination);

    /**
     * Copies the value stored at the <code>source</code> to the <code>destination</code> key if the
     * <code>destination</code> key does not yet exist.
     *
     * @apiNote When in cluster mode, both <code>source</code> and <code>destination</code> must map
     *     to the same hash slot.
     * @since Redis 6.2.0 and above.
     * @see <a href="https://valkey.io/commands/copy/">redis.io</a> for details.
     * @param source The key to the source value.
     * @param destination The key where the value should be copied to.
     * @return <code>true</code> if <code>source</code> was copied, <code>false</code> if <code>source
     * </code> was not copied.
     * @example
     *     <pre>{@code
     * client.set(gs("test1"), gs("one")).get();
     * client.set(gs("test2"), gs("two")).get();
     * assert !client.copy(gs("test1", gs("test2")).get();
     * assert client.copy(gs("test1"), gs("test2")).get();
     * }</pre>
     */
    CompletableFuture<Boolean> copy(GlideString source, GlideString destination);

    /**
     * Copies the value stored at the <code>source</code> to the <code>destination</code> key. When
     * <code>replace</code> is true, removes the <code>destination</code> key first if it already
     * exists, otherwise performs no action.
     *
     * @apiNote When in cluster mode, both <code>source</code> and <code>destination</code> must map
     *     to the same hash slot.
     * @since Redis 6.2.0 and above.
     * @see <a href="https://valkey.io/commands/copy/">redis.io</a> for details.
     * @param source The key to the source value.
     * @param destination The key where the value should be copied to.
     * @param replace If the destination key should be removed before copying the value to it.
     * @return <code>true</code> if <code>source</code> was copied, <code>false</code> if <code>source
     * </code> was not copied.
     * @example
     *     <pre>{@code
     * client.set("test1", "one").get();
     * client.set("test2", "two").get();
     * assert !client.copy("test1", "test2", false).get();
     * assert client.copy("test1", "test2", true).get();
     * }</pre>
     */
    CompletableFuture<Boolean> copy(String source, String destination, boolean replace);

    /**
     * Copies the value stored at the <code>source</code> to the <code>destination</code> key. When
     * <code>replace</code> is true, removes the <code>destination</code> key first if it already
     * exists, otherwise performs no action.
     *
     * @apiNote When in cluster mode, both <code>source</code> and <code>destination</code> must map
     *     to the same hash slot.
     * @since Redis 6.2.0 and above.
     * @see <a href="https://valkey.io/commands/copy/">redis.io</a> for details.
     * @param source The key to the source value.
     * @param destination The key where the value should be copied to.
     * @param replace If the destination key should be removed before copying the value to it.
     * @return <code>true</code> if <code>source</code> was copied, <code>false</code> if <code>source
     * </code> was not copied.
     * @example
     *     <pre>{@code
     * client.set(gs("test1"), gs("one")).get();
     * client.set(gs("test2"), gs("two")).get();
     * assert !client.copy(gs("test1", gs("test2"), false).get();
     * assert client.copy(gs("test1", gs("test2"), true).get();
     * }</pre>
     */
    CompletableFuture<Boolean> copy(GlideString source, GlideString destination, boolean replace);

    /**
     * Serialize the value stored at <code>key</code> in a Valkey-specific format and return it to the
     * user.
     *
     * @see <a href="https://valkey.io/commands/dump/">valkey.io</a> for details.
     * @param key The key of the set.
     * @return The serialized value of a set.<br>
     *     If <code>key</code> does not exist, <code>null</code> will be returned.
     * @example
     *     <pre>{@code
     * byte[] result = client.dump("myKey").get();
     *
     * byte[] response = client.dump("nonExistingKey").get();
     * assert response.equals(null);
     * }</pre>
     */
    CompletableFuture<byte[]> dump(GlideString key);

    /**
     * Create a <code>key</code> associated with a <code>value</code> that is obtained by
     * deserializing the provided serialized <code>value</code> (obtained via {@link #dump}).
     *
     * @see <a href="https://valkey.io/commands/restore/">valkey.io</a> for details.
     * @param key The key of the set.
     * @param ttl The expiry time (in milliseconds). If <code>0</code>, the <code>key</code> will
     *     persist.
     * @param value The serialized value.
     * @return Return <code>OK</code> if successfully create a <code>key</code> with a <code>value
     *      </code>.
     * @example
     *     <pre>{@code
     * String result = client.restore(gs("newKey"), 0, value).get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> restore(GlideString key, long ttl, byte[] value);

    /**
     * Create a <code>key</code> associated with a <code>value</code> that is obtained by
     * deserializing the provided serialized <code>value</code> (obtained via {@link #dump}).
     *
     * @see <a href="https://valkey.io/commands/restore/">valkey.io</a> for details.
     * @param key The key of the set.
     * @param ttl The expiry time (in milliseconds). If <code>0</code>, the <code>key</code> will
     *     persist.
     * @param value The serialized value.
     * @param restoreOptions The restore options. See {@link RestoreOptions}.
     * @return Return <code>OK</code> if successfully create a <code>key</code> with a <code>value
     *      </code>.
     * @example
     *     <pre>{@code
     * RestoreOptions options = RestoreOptions.builder().replace().absttl().idletime(10).frequency(10).build()).get();
     * // Set restore options with replace and absolute TTL modifiers, object idletime and frequency to 10.
     * String result = client.restore(gs("newKey"), 0, value, options).get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> restore(
            GlideString key, long ttl, byte[] value, RestoreOptions restoreOptions);

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and returns the result.
     * <br>
     * The <code>sort</code> command can be used to sort elements based on different criteria and
     * apply transformations on sorted elements.<br>
     * To store the result into a new key, see {@link #sortStore(String, String)}.<br>
     *
     * @param key The key of the list, set, or sorted set to be sorted.
     * @return An <code>Array</code> of sorted elements.
     * @example
     *     <pre>{@code
     * client.lpush("mylist", new String[] {"3", "1", "2"}).get();
     * assertArrayEquals(new String[] {"1", "2", "3"}, client.sort("mylist").get()); // List is sorted in ascending order
     * }</pre>
     */
    CompletableFuture<String[]> sort(String key);

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and returns the result.
     * <br>
     * The <code>sort</code> command can be used to sort elements based on different criteria and
     * apply transformations on sorted elements.<br>
     * To store the result into a new key, see {@link #sortStore(String, String)}.<br>
     *
     * @param key The key of the list, set, or sorted set to be sorted.
     * @return An <code>Array</code> of sorted elements.
     * @example
     *     <pre>{@code
     * client.lpush(gs("mylist"), new GlideString[] {gs("3"), gs("1"), gs("2")}).get();
     * assertArrayEquals(new GlideString[] {gs("1"), gs("2"), gs("3")}, client.sort(gs("mylist")).get()); // List is sorted in ascending order
     * }</pre>
     */
    CompletableFuture<GlideString[]> sort(GlideString key);

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and returns the result.
     * <br>
     * The <code>sortReadOnly</code> command can be used to sort elements based on different criteria
     * and apply transformations on sorted elements.<br>
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * @since Redis 7.0 and above.
     * @param key The key of the list, set, or sorted set to be sorted.
     * @return An <code>Array</code> of sorted elements.
     * @example
     *     <pre>{@code
     * client.lpush("mylist", new String[] {"3", "1", "2"}).get();
     * assertArrayEquals(new String[] {"1", "2", "3"}, client.sortReadOnly("mylist").get()); // List is sorted in ascending order
     * }</pre>
     */
    CompletableFuture<String[]> sortReadOnly(String key);

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and returns the result.
     * <br>
     * The <code>sortReadOnly</code> command can be used to sort elements based on different criteria
     * and apply transformations on sorted elements.<br>
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * @since Redis 7.0 and above.
     * @param key The key of the list, set, or sorted set to be sorted.
     * @return An <code>Array</code> of sorted elements.
     * @example
     *     <pre>{@code
     * client.lpush(gs("mylist", new GlideString[] {gs("3"), gs("1"), gs("2")}).get();
     * assertArrayEquals(new GlideString[] {gs("1"), gs("2"), gs("3")}, client.sortReadOnly(gs("mylist")).get()); // List is sorted in ascending order
     * }</pre>
     */
    CompletableFuture<GlideString[]> sortReadOnly(GlideString key);

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and stores the result in
     * <code>destination</code>. The <code>sort</code> command can be used to sort elements based on
     * different criteria, apply transformations on sorted elements, and store the result in a new
     * key.<br>
     * To get the sort result without storing it into a key, see {@link #sort(String)} or {@link
     * #sortReadOnly(String)}.
     *
     * @apiNote When in cluster mode, <code>key</code> and <code>destination</code> must map to the
     *     same hash slot.
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param destination The key where the sorted result will be stored.
     * @return The number of elements in the sorted key stored at <code>destination</code>.
     * @example
     *     <pre>{@code
     * client.lpush("mylist", new String[] {"3", "1", "2"}).get();
     * assert client.sortStore("mylist", "destination").get() == 3;
     * assertArrayEquals(
     *    new String[] {"1", "2", "3"},
     *    client.lrange("destination", 0, -1).get()); // Sorted list is stored in `destination`
     * }</pre>
     */
    CompletableFuture<Long> sortStore(String key, String destination);

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and stores the result in
     * <code>destination</code>. The <code>sort</code> command can be used to sort elements based on
     * different criteria, apply transformations on sorted elements, and store the result in a new
     * key.<br>
     * To get the sort result without storing it into a key, see {@link #sort(GlideString)} or {@link
     * #sortReadOnly(GlideString)}.
     *
     * @apiNote When in cluster mode, <code>key</code> and <code>destination</code> must map to the
     *     same hash slot.
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param destination The key where the sorted result will be stored.
     * @return The number of elements in the sorted key stored at <code>destination</code>.
     * @example
     *     <pre>{@code
     * client.lpush(gs("mylist"), new GlideString[] {gs("3"), gs("1"), gs("2")}).get();
     * assert client.sortStore(gs("mylist"), gs("destination")).get() == 3;
     * assertArrayEquals(
     *    new GlideString[] {gs("1"), gs("2"), gs("3")},
     *    client.lrange(gs("destination"), 0, -1).get()); // Sorted list is stored in `destination`
     * }</pre>
     */
    CompletableFuture<Long> sortStore(GlideString key, GlideString destination);

    /**
     * Blocks the current client until all the previous write commands are successfully transferred
     * and acknowledged by at least <code>numreplicas</code> of replicas. If <code>timeout</code> is
     * reached, the command returns even if the specified number of replicas were not yet reached.
     *
     * @param numreplicas The number of replicas to reach.
     * @param timeout The timeout value specified in milliseconds. A value of <code>0</code> will
     *     block indefinitely.
     * @return The number of replicas reached by all the writes performed in the context of the
     *     current connection.
     * @example
     *     <pre>{@code
     * client.set("key", "value).get();
     * assert client.wait(1L, 1000L).get() == 1L;
     * }</pre>
     */
    CompletableFuture<Long> wait(long numreplicas, long timeout);
}
