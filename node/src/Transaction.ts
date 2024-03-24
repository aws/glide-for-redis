/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

import {
    ExpireOptions,
    InfoOptions,
    ScoreLimit,
    SetOptions,
    StreamAddOptions,
    StreamReadOptions,
    StreamTrimOptions,
    ZaddOptions,
    createBrpop,
    createClientGetName,
    createClientId,
    createConfigGet,
    createConfigResetStat,
    createConfigRewrite,
    createConfigSet,
    createCustomCommand,
    createDecr,
    createDecrBy,
    createDel,
    createEcho,
    createExists,
    createExpire,
    createExpireAt,
    createGet,
    createHDel,
    createHExists,
    createHGet,
    createHGetAll,
    createHIncrBy,
    createHIncrByFloat,
    createHLen,
    createHMGet,
    createHSet,
    createHSetNX,
    createHvals,
    createIncr,
    createIncrBy,
    createIncrByFloat,
    createInfo,
    createLLen,
    createLPop,
    createLPush,
    createLRange,
    createLRem,
    createLTrim,
    createLindex,
    createMGet,
    createMSet,
    createPExpire,
    createPExpireAt,
    createPersist,
    createPing,
    createPttl,
    createRPop,
    createRPush,
    createRename,
    createSAdd,
    createSCard,
    createSMembers,
    createSRem,
    createSelect,
    createSet,
    createSismember,
    createStrlen,
    createTTL,
    createTime,
    createType,
    createUnlink,
    createXadd,
    createXread,
    createXtrim,
    createZadd,
    createZcard,
    createZcount,
    createZpopmax,
    createZpopmin,
    createZrank,
    createZrem,
    createZremRangeByRank,
    createZremRangeByScore,
    createZscore,
} from "./Commands";
import { redis_request } from "./ProtobufMessage";

/**
 * Base class encompassing shared commands for both standalone and cluster mode implementations in a transaction.
 * Transactions allow the execution of a group of commands in a single step.
 *
 * Command Response:
 *  An array of command responses is returned by the client exec command, in the order they were given.
 *  Each element in the array represents a command given to the transaction.
 *  The response for each command depends on the executed Redis command.
 *  Specific response types are documented alongside each method.
 *
 * @example
 *       transaction = new BaseTransaction()
 *          .set("key", "value")
 *          .get("key");
 *       await client.exec(transaction);
 *       [OK , "value"]
 */
export class BaseTransaction<T extends BaseTransaction<T>> {
    /**
     * @internal
     */
    readonly commands: redis_request.Command[] = [];

    protected addAndReturn(command: redis_request.Command): T {
        this.commands.push(command);
        return this as unknown as T;
    }

    /** Get the value associated with the given key, or null if no such value exists.
     * See https://redis.io/commands/get/ for details.
     *
     * @param key - The key to retrieve from the database.
     *
     * Command Response - If `key` exists, returns the value of `key` as a string. Otherwise, return null.
     */
    public get(key: string): T {
        return this.addAndReturn(createGet(key));
    }

    /** Set the given key with the given value. Return value is dependent on the passed options.
     * See https://redis.io/commands/set/ for details.
     *
     * @param key - The key to store.
     * @param value - The value to store with the given key.
     * @param options - The set options.
     *
     * Command Response - If the value is successfully set, return OK.
     * If `value` isn't set because of `onlyIfExists` or `onlyIfDoesNotExist` conditions, return null.
     * If `returnOldValue` is set, return the old value as a string.
     */
    public set(key: string, value: string, options?: SetOptions): T {
        return this.addAndReturn(createSet(key, value, options));
    }

    /** Ping the Redis server.
     * See https://redis.io/commands/ping/ for details.
     *
     * @param message - An optional message to include in the PING command.
     * If not provided, the server will respond with "PONG".
     * If provided, the server will respond with a copy of the message.
     *
     * Command Response - "PONG" if `message` is not provided, otherwise return a copy of `message`.
     */
    public ping(message?: string): T {
        return this.addAndReturn(createPing(message));
    }

    /** Get information and statistics about the Redis server.
     * See https://redis.io/commands/info/ for details.
     *
     * @param options - A list of InfoSection values specifying which sections of information to retrieve.
     * When no parameter is provided, the default option is assumed.
     *
     * Command Response - a string containing the information for the sections requested.
     */
    public info(options?: InfoOptions[]): T {
        return this.addAndReturn(createInfo(options));
    }

    /** Remove the specified keys. A key is ignored if it does not exist.
     * See https://redis.io/commands/del/ for details.
     *
     * @param keys - A list of keys to be deleted from the database.
     *
     * Command Response - the number of keys that were removed.
     */
    public del(keys: string[]): T {
        return this.addAndReturn(createDel(keys));
    }

    /** Get the name of the connection on which the transaction is being executed.
     * See https://redis.io/commands/client-getname/ for more details.
     *
     * Command Response - the name of the client connection as a string if a name is set, or null if no name is assigned.
     */
    public clientGetName(): T {
        return this.addAndReturn(createClientGetName());
    }

    /** Rewrite the configuration file with the current configuration.
     * See https://redis.io/commands/select/ for details.
     *
     * Command Response - "OK" when the configuration was rewritten properly. Otherwise, the transaction fails with an error.
     */
    public configRewrite(): T {
        return this.addAndReturn(createConfigRewrite());
    }

    /** Resets the statistics reported by Redis using the INFO and LATENCY HISTOGRAM commands.
     * See https://redis.io/commands/config-resetstat/ for details.
     *
     * Command Response - always "OK".
     */
    public configResetStat(): T {
        return this.addAndReturn(createConfigResetStat());
    }

    /** Retrieve the values of multiple keys.
     * See https://redis.io/commands/mget/ for details.
     *
     * @param keys - A list of keys to retrieve values for.
     *
     * Command Response - A list of values corresponding to the provided keys. If a key is not found,
     * its corresponding value in the list will be null.
     */
    public mget(keys: string[]): T {
        return this.addAndReturn(createMGet(keys));
    }

    /** Set multiple keys to multiple values in a single atomic operation.
     * See https://redis.io/commands/mset/ for details.
     *
     * @param keyValueMap - A key-value map consisting of keys and their respective values to set.
     *
     * Command Response - always "OK".
     */
    public mset(keyValueMap: Record<string, string>): T {
        return this.addAndReturn(createMSet(keyValueMap));
    }

    /** Increments the number stored at `key` by one. If `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/incr/ for details.
     *
     * @param key - The key to increment its value.
     *
     * Command Response - the value of `key` after the increment.
     */
    public incr(key: string): T {
        return this.addAndReturn(createIncr(key));
    }

    /** Increments the number stored at `key` by `amount`. If `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/incrby/ for details.
     *
     * @param key - The key to increment its value.
     * @param amount - The amount to increment.
     *
     * Command Response - the value of `key` after the increment.
     */
    public incrBy(key: string, amount: number): T {
        return this.addAndReturn(createIncrBy(key, amount));
    }

    /** Increment the string representing a floating point number stored at `key` by `amount`.
     * By using a negative amount value, the result is that the value stored at `key` is decremented.
     * If `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/incrbyfloat/ for details.
     *
     * @param key - The key to increment its value.
     * @param amount - The amount to increment.
     *
     * Command Response - the value of `key` after the increment.
     *
     */
    public incrByFloat(key: string, amount: number): T {
        return this.addAndReturn(createIncrByFloat(key, amount));
    }

    /** Returns the current connection id.
     * See https://redis.io/commands/client-id/ for details.
     *
     * Command Response - the id of the client.
     */
    public clientId(): T {
        return this.addAndReturn(createClientId());
    }

    /** Decrements the number stored at `key` by one. If `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/decr/ for details.
     *
     * @param key - The key to decrement its value.
     *
     * Command Response - the value of `key` after the decrement.
     */
    public decr(key: string): T {
        return this.addAndReturn(createDecr(key));
    }

    /** Decrements the number stored at `key` by `amount`. If `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/decrby/ for details.
     *
     * @param key - The key to decrement its value.
     * @param amount - The amount to decrement.
     *
     * Command Response - the value of `key` after the decrement.
     */
    public decrBy(key: string, amount: number): T {
        return this.addAndReturn(createDecrBy(key, amount));
    }

    /** Reads the configuration parameters of a running Redis server.
     * See https://redis.io/commands/config-get/ for details.
     *
     * @param parameters - A list of configuration parameter names to retrieve values for.
     *
     * Command Response - A map of values corresponding to the configuration parameters.
     *
     */
    public configGet(parameters: string[]): T {
        return this.addAndReturn(createConfigGet(parameters));
    }

    /** Set configuration parameters to the specified values.
     * See https://redis.io/commands/config-set/ for details.
     *
     * @param parameters - A List of keyValuePairs consisting of configuration parameters and their respective values to set.
     *
     * Command Response - "OK" when the configuration was set properly. Otherwise, the transaction fails with an error.
     *
     * @example
     * config_set([("timeout", "1000")], [("maxmemory", "1GB")]) - Returns OK
     *
     */
    public configSet(parameters: Record<string, string>): T {
        return this.addAndReturn(createConfigSet(parameters));
    }

    /** Retrieve the value associated with `field` in the hash stored at `key`.
     * See https://redis.io/commands/hget/ for details.
     *
     * @param key - The key of the hash.
     * @param field - The field in the hash stored at `key` to retrieve from the database.
     *
     * Command Response - the value associated with `field`, or null when `field` is not present in the hash or `key` does not exist.
     */
    public hget(key: string, field: string): T {
        return this.addAndReturn(createHGet(key, field));
    }

    /** Sets the specified fields to their respective values in the hash stored at `key`.
     * See https://redis.io/commands/hset/ for details.
     *
     * @param key - The key of the hash.
     * @param fieldValueMap - A field-value map consisting of fields and their corresponding values
     * to be set in the hash stored at the specified key.
     *
     * Command Response - The number of fields that were added.
     */
    public hset(key: string, fieldValueMap: Record<string, string>): T {
        return this.addAndReturn(createHSet(key, fieldValueMap));
    }

    /** Sets `field` in the hash stored at `key` to `value`, only if `field` does not yet exist.
     * If `key` does not exist, a new key holding a hash is created.
     * If `field` already exists, this operation has no effect.
     * See https://redis.io/commands/hsetnx/ for more details.
     *
     * @param key - The key of the hash.
     * @param field - The field to set the value for.
     * @param value - The value to set.
     *
     * Command Response - `true` if the field was set, `false` if the field already existed and was not set.
     */
    public hsetnx(key: string, field: string, value: string): T {
        return this.addAndReturn(createHSetNX(key, field, value));
    }

    /** Removes the specified fields from the hash stored at `key`.
     * Specified fields that do not exist within this hash are ignored.
     * See https://redis.io/commands/hdel/ for details.
     *
     * @param key - The key of the hash.
     * @param fields - The fields to remove from the hash stored at `key`.
     *
     * Command Response - the number of fields that were removed from the hash, not including specified but non existing fields.
     * If `key` does not exist, it is treated as an empty hash and it returns 0.
     */
    public hdel(key: string, fields: string[]): T {
        return this.addAndReturn(createHDel(key, fields));
    }

    /** Returns the values associated with the specified fields in the hash stored at `key`.
     * See https://redis.io/commands/hmget/ for details.
     *
     * @param key - The key of the hash.
     * @param fields - The fields in the hash stored at `key` to retrieve from the database.
     *
     * Command Response - a list of values associated with the given fields, in the same order as they are requested.
     * For every field that does not exist in the hash, a null value is returned.
     * If `key` does not exist, it is treated as an empty hash and it returns a list of null values.
     */
    public hmget(key: string, fields: string[]): T {
        return this.addAndReturn(createHMGet(key, fields));
    }

    /** Returns if `field` is an existing field in the hash stored at `key`.
     * See https://redis.io/commands/hexists/ for details.
     *
     * @param key - The key of the hash.
     * @param field - The field to check in the hash stored at `key`.
     *
     * Command Response - `true` if the hash contains `field`. If the hash does not contain `field`, or if `key` does not exist,
     * the command response will be `false`.
     */
    public hexists(key: string, field: string): T {
        return this.addAndReturn(createHExists(key, field));
    }

    /** Returns all fields and values of the hash stored at `key`.
     * See https://redis.io/commands/hgetall/ for details.
     *
     * @param key - The key of the hash.
     *
     * Command Response - a map of fields and their values stored in the hash. Every field name in the map is followed by its value.
     * If `key` does not exist, it returns an empty map.
     */
    public hgetall(key: string): T {
        return this.addAndReturn(createHGetAll(key));
    }

    /** Increments the number stored at `field` in the hash stored at `key` by `increment`.
     * By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
     * If `field` or `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/hincrby/ for details.
     *
     * @param key - The key of the hash.
     * @param amount - The amount to increment.
     * @param field - The field in the hash stored at `key` to increment its value.
     *
     * Command Response - the value of `field` in the hash stored at `key` after the increment.
     */
    public hincrBy(key: string, field: string, amount: number): T {
        return this.addAndReturn(createHIncrBy(key, field, amount));
    }

    /** Increment the string representing a floating point number stored at `field` in the hash stored at `key` by `increment`.
     * By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
     * If `field` or `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/hincrbyfloat/ for details.
     *
     * @param key - The key of the hash.
     * @param amount - The amount to increment.
     * @param field - The field in the hash stored at `key` to increment its value.
     *
     * Command Response - the value of `field` in the hash stored at `key` after the increment.
     */
    public hincrByFloat(key: string, field: string, amount: number): T {
        return this.addAndReturn(createHIncrByFloat(key, field, amount));
    }

    /** Returns the number of fields contained in the hash stored at `key`.
     * See https://redis.io/commands/hlen/ for more details.
     *
     * @param key - The key of the hash.
     *
     * Command Response - The number of fields in the hash, or 0 when the key does not exist.
     */
    public hlen(key: string): T {
        return this.addAndReturn(createHLen(key));
    }

    /** Returns all values in the hash stored at key.
     * See https://redis.io/commands/hvals/ for more details.
     *
     * @param key - The key of the hash.
     *
     * Command Response - a list of values in the hash, or an empty list when the key does not exist.
     */
    public hvals(key: string): T {
        return this.addAndReturn(createHvals(key));
    }

    /** Inserts all the specified values at the head of the list stored at `key`.
     * `elements` are inserted one after the other to the head of the list, from the leftmost element to the rightmost element.
     * If `key` does not exist, it is created as empty list before performing the push operations.
     * See https://redis.io/commands/lpush/ for details.
     *
     * @param key - The key of the list.
     * @param elements - The elements to insert at the head of the list stored at `key`.
     *
     * Command Response - the length of the list after the push operations.
     */
    public lpush(key: string, elements: string[]): T {
        return this.addAndReturn(createLPush(key, elements));
    }

    /** Removes and returns the first elements of the list stored at `key`.
     * The command pops a single element from the beginning of the list.
     * See https://redis.io/commands/lpop/ for details.
     *
     * @param key - The key of the list.
     *
     * Command Response - The value of the first element.
     * If `key` does not exist null will be returned.
     */
    public lpop(key: string): T {
        return this.addAndReturn(createLPop(key));
    }

    /** Removes and returns up to `count` elements of the list stored at `key`, depending on the list's length.
     * See https://redis.io/commands/lpop/ for details.
     *
     * @param key - The key of the list.
     * @param count - The count of the elements to pop from the list.
     *
     * Command Response - A list of the popped elements will be returned depending on the list's length.
     * If `key` does not exist null will be returned.
     */
    public lpopCount(key: string, count: number): T {
        return this.addAndReturn(createLPop(key, count));
    }

    /** Returns the specified elements of the list stored at `key`.
     * The offsets `start` and `end` are zero-based indexes, with 0 being the first element of the list, 1 being the next element and so on.
     * These offsets can also be negative numbers indicating offsets starting at the end of the list,
     * with -1 being the last element of the list, -2 being the penultimate, and so on.
     * See https://redis.io/commands/lrange/ for details.
     *
     * @param key - The key of the list.
     * @param start - The starting point of the range.
     * @param end - The end of the range.
     *
     * Command Response - list of elements in the specified range.
     * If `start` exceeds the end of the list, or if `start` is greater than `end`, an empty list will be returned.
     * If `end` exceeds the actual end of the list, the range will stop at the actual end of the list.
     * If `key` does not exist an empty list will be returned.
     */
    public lrange(key: string, start: number, end: number): T {
        return this.addAndReturn(createLRange(key, start, end));
    }

    /** Returns the length of the list stored at `key`.
     * See https://redis.io/commands/llen/ for details.
     *
     * @param key - The key of the list.
     *
     * Command Response - the length of the list at `key`.
     * If `key` does not exist, it is interpreted as an empty list and 0 is returned.
     */
    public llen(key: string): T {
        return this.addAndReturn(createLLen(key));
    }

    /** Trim an existing list so that it will contain only the specified range of elements specified.
     * The offsets `start` and `end` are zero-based indexes, with 0 being the first element of the list, 1 being the next element and so on.
     * These offsets can also be negative numbers indicating offsets starting at the end of the list,
     * with -1 being the last element of the list, -2 being the penultimate, and so on.
     * See https://redis.io/commands/ltrim/ for details.
     *
     * @param key - The key of the list.
     * @param start - The starting point of the range.
     * @param end - The end of the range.
     *
     * Command Response - always "OK".
     * If `start` exceeds the end of the list, or if `start` is greater than `end`, the result will be an empty list (which causes key to be removed).
     * If `end` exceeds the actual end of the list, it will be treated like the last element of the list.
     * If `key` does not exist the command will be ignored.
     */
    public ltrim(key: string, start: number, end: number): T {
        return this.addAndReturn(createLTrim(key, start, end));
    }

    /** Removes the first `count` occurrences of elements equal to `element` from the list stored at `key`.
     * If `count` is positive : Removes elements equal to `element` moving from head to tail.
     * If `count` is negative : Removes elements equal to `element` moving from tail to head.
     * If `count` is 0 or `count` is greater than the occurrences of elements equal to `element`: Removes all elements equal to `element`.
     *
     * @param key - The key of the list.
     * @param count - The count of the occurrences of elements equal to `element` to remove.
     * @param element - The element to remove from the list.
     *
     * Command Response - the number of the removed elements.
     * If `key` does not exist, 0 is returned.
     */
    public lrem(key: string, count: number, element: string): T {
        return this.addAndReturn(createLRem(key, count, element));
    }

    /** Inserts all the specified values at the tail of the list stored at `key`.
     * `elements` are inserted one after the other to the tail of the list, from the leftmost element to the rightmost element.
     * If `key` does not exist, it is created as empty list before performing the push operations.
     * See https://redis.io/commands/rpush/ for details.
     *
     * @param key - The key of the list.
     * @param elements - The elements to insert at the tail of the list stored at `key`.
     *
     * Command Response - the length of the list after the push operations.
     */
    public rpush(key: string, elements: string[]): T {
        return this.addAndReturn(createRPush(key, elements));
    }

    /** Removes and returns the last elements of the list stored at `key`.
     * The command pops a single element from the end of the list.
     * See https://redis.io/commands/rpop/ for details.
     *
     * @param key - The key of the list.
     *
     * Command Response - The value of the last element.
     * If `key` does not exist null will be returned.
     */
    public rpop(key: string): T {
        return this.addAndReturn(createRPop(key));
    }

    /** Removes and returns up to `count` elements from the list stored at `key`, depending on the list's length.
     * See https://redis.io/commands/rpop/ for details.
     *
     * @param key - The key of the list.
     * @param count - The count of the elements to pop from the list.
     *
     * Command Response - A list of popped elements will be returned depending on the list's length.
     * If `key` does not exist null will be returned.
     */
    public rpopCount(key: string, count: number): T {
        return this.addAndReturn(createRPop(key, count));
    }

    /** Adds the specified members to the set stored at `key`. Specified members that are already a member of this set are ignored.
     * If `key` does not exist, a new set is created before adding `members`.
     * See https://redis.io/commands/sadd/ for details.
     *
     * @param key - The key to store the members to its set.
     * @param members - A list of members to add to the set stored at `key`.
     *
     * Command Response - the number of members that were added to the set, not including all the members already present in the set.
     */
    public sadd(key: string, members: string[]): T {
        return this.addAndReturn(createSAdd(key, members));
    }

    /** Removes the specified members from the set stored at `key`. Specified members that are not a member of this set are ignored.
     * See https://redis.io/commands/srem/ for details.
     *
     * @param key - The key to remove the members from its set.
     * @param members - A list of members to remove from the set stored at `key`.
     *
     * Command Response - the number of members that were removed from the set, not including non existing members.
     * If `key` does not exist, it is treated as an empty set and this command returns 0.
     */
    public srem(key: string, members: string[]): T {
        return this.addAndReturn(createSRem(key, members));
    }

    /** Returns all the members of the set value stored at `key`.
     * See https://redis.io/commands/smembers/ for details.
     *
     * @param key - The key to return its members.
     *
     * Command Response - all members of the set.
     * If `key` does not exist, it is treated as an empty set and this command returns empty list.
     */
    public smembers(key: string): T {
        return this.addAndReturn(createSMembers(key));
    }

    /** Returns the set cardinality (number of elements) of the set stored at `key`.
     * See https://redis.io/commands/scard/ for details.
     *
     * @param key - The key to return the number of its members.
     *
     * Command Response - the cardinality (number of elements) of the set, or 0 if key does not exist.
     */
    public scard(key: string): T {
        return this.addAndReturn(createSCard(key));
    }

    /** Returns if `member` is a member of the set stored at `key`.
     * See https://redis.io/commands/sismember/ for more details.
     *
     * @param key - The key of the set.
     * @param member - The member to check for existence in the set.
     *
     * Command Response - `true` if the member exists in the set, `false` otherwise.
     * If `key` doesn't exist, it is treated as an empty set and the command returns `false`.
     */
    public sismember(key: string, member: string): T {
        return this.addAndReturn(createSismember(key, member));
    }

    /** Returns the number of keys in `keys` that exist in the database.
     * See https://redis.io/commands/exists/ for details.
     *
     * @param keys - The keys list to check.
     *
     * Command Response - the number of keys that exist. If the same existing key is mentioned in `keys` multiple times,
     * it will be counted multiple times.
     */
    public exists(keys: string[]): T {
        return this.addAndReturn(createExists(keys));
    }

    /** Removes the specified keys. A key is ignored if it does not exist.
     * This command, similar to DEL, removes specified keys and ignores non-existent ones.
     * However, this command does not block the server, while [DEL](https://redis.io/commands/del) does.
     * See https://redis.io/commands/unlink/ for details.
     *
     * @param keys - The keys we wanted to unlink.
     *
     * Command Response - the number of keys that were unlinked.
     */
    public unlink(keys: string[]): T {
        return this.addAndReturn(createUnlink(keys));
    }

    /** Sets a timeout on `key` in seconds. After the timeout has expired, the key will automatically be deleted.
     * If `key` already has an existing expire set, the time to live is updated to the new value.
     * If `seconds` is non-positive number, the key will be deleted rather than expired.
     * The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
     * See https://redis.io/commands/expire/ for details.
     *
     * @param key - The key to set timeout on it.
     * @param seconds - The timeout in seconds.
     * @param option - The expire option.
     *
     * Command Response - `true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
     * or operation skipped due to the provided arguments.
     */
    public expire(key: string, seconds: number, option?: ExpireOptions): T {
        return this.addAndReturn(createExpire(key, seconds, option));
    }

    /** Sets a timeout on `key`. It takes an absolute Unix timestamp (seconds since January 1, 1970) instead of specifying the number of seconds.
     * A timestamp in the past will delete the key immediately. After the timeout has expired, the key will automatically be deleted.
     * If `key` already has an existing expire set, the time to live is updated to the new value.
     * The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
     * See https://redis.io/commands/expireat/ for details.
     *
     * @param key - The key to set timeout on it.
     * @param unixSeconds - The timeout in an absolute Unix timestamp.
     * @param option - The expire option.
     *
     * Command Response - `true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
     * or operation skipped due to the provided arguments.
     */
    public expireAt(
        key: string,
        unixSeconds: number,
        option?: ExpireOptions,
    ): T {
        return this.addAndReturn(createExpireAt(key, unixSeconds, option));
    }

    /** Sets a timeout on `key` in milliseconds. After the timeout has expired, the key will automatically be deleted.
     * If `key` already has an existing expire set, the time to live is updated to the new value.
     * If `milliseconds` is non-positive number, the key will be deleted rather than expired.
     * The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
     * See https://redis.io/commands/pexpire/ for details.
     *
     * @param key - The key to set timeout on it.
     * @param milliseconds - The timeout in milliseconds.
     * @param option - The expire option.
     *
     * Command Response - `true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
     * or operation skipped due to the provided arguments.
     */
    public pexpire(
        key: string,
        milliseconds: number,
        option?: ExpireOptions,
    ): T {
        return this.addAndReturn(createPExpire(key, milliseconds, option));
    }

    /** Sets a timeout on `key`. It takes an absolute Unix timestamp (milliseconds since January 1, 1970) instead of specifying the number of milliseconds.
     * A timestamp in the past will delete the key immediately. After the timeout has expired, the key will automatically be deleted.
     * If `key` already has an existing expire set, the time to live is updated to the new value.
     * The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
     * See https://redis.io/commands/pexpireat/ for details.
     *
     * @param key - The key to set timeout on it.
     * @param unixMilliseconds - The timeout in an absolute Unix timestamp.
     * @param option - The expire option.
     *
     * Command Response - `true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
     * or operation skipped due to the provided arguments.
     */
    public pexpireAt(
        key: string,
        unixMilliseconds: number,
        option?: ExpireOptions,
    ): T {
        return this.addAndReturn(
            createPExpireAt(key, unixMilliseconds, option),
        );
    }

    /** Returns the remaining time to live of `key` that has a timeout.
     * See https://redis.io/commands/ttl/ for details.
     *
     * @param key - The key to return its timeout.
     *
     * Command Response -  TTL in seconds, -2 if `key` does not exist or -1 if `key` exists but has no associated expire.
     */
    public ttl(key: string): T {
        return this.addAndReturn(createTTL(key));
    }

    /** Adds members with their scores to the sorted set stored at `key`.
     * If a member is already a part of the sorted set, its score is updated.
     * See https://redis.io/commands/zadd/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param membersScoresMap - A mapping of members to their corresponding scores.
     * @param options - The Zadd options.
     * @param changed - Modify the return value from the number of new elements added, to the total number of elements changed.
     *
     * Command Response - The number of elements added to the sorted set.
     * If `changed` is set, returns the number of elements updated in the sorted set.
     */
    public zadd(
        key: string,
        membersScoresMap: Record<string, number>,
        options?: ZaddOptions,
        changed?: boolean,
    ): T {
        return this.addAndReturn(
            createZadd(
                key,
                membersScoresMap,
                options,
                changed ? "CH" : undefined,
            ),
        );
    }

    /** Increments the score of member in the sorted set stored at `key` by `increment`.
     * If `member` does not exist in the sorted set, it is added with `increment` as its score (as if its previous score was 0.0).
     * If `key` does not exist, a new sorted set with the specified member as its sole member is created.
     * See https://redis.io/commands/zadd/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param member - A member in the sorted set to increment.
     * @param increment - The score to increment the member.
     * @param options - The Zadd options.
     *
     * Command Response - The score of the member.
     * If there was a conflict with the options, the operation aborts and null is returned.
     */
    public zaddIncr(
        key: string,
        member: string,
        increment: number,
        options?: ZaddOptions,
    ): T {
        return this.addAndReturn(
            createZadd(key, { [member]: increment }, options, "INCR"),
        );
    }

    /** Removes the specified members from the sorted set stored at `key`.
     * Specified members that are not a member of this set are ignored.
     * See https://redis.io/commands/zrem/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param members - A list of members to remove from the sorted set.
     *
     * Command Response - The number of members that were removed from the sorted set, not including non-existing members.
     * If `key` does not exist, it is treated as an empty sorted set, and this command returns 0.
     */
    public zrem(key: string, members: string[]): T {
        return this.addAndReturn(createZrem(key, members));
    }

    /** Returns the cardinality (number of elements) of the sorted set stored at `key`.
     * See https://redis.io/commands/zcard/ for more details.
     *
     * @param key - The key of the sorted set.
     *
     * Command Response - The number of elements in the sorted set.
     * If `key` does not exist, it is treated as an empty sorted set, and this command returns 0.
     */
    public zcard(key: string): T {
        return this.addAndReturn(createZcard(key));
    }

    /** Returns the score of `member` in the sorted set stored at `key`.
     * See https://redis.io/commands/zscore/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param member - The member whose score is to be retrieved.
     *
     * Command Response - The score of the member.
     * If `member` does not exist in the sorted set, null is returned.
     * If `key` does not exist, null is returned.
     */
    public zscore(key: string, member: string): T {
        return this.addAndReturn(createZscore(key, member));
    }

    /** Returns the number of members in the sorted set stored at `key` with scores between `minScore` and `maxScore`.
     * See https://redis.io/commands/zcount/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param minScore - The minimum score to count from. Can be positive/negative infinity, or specific score and inclusivity.
     * @param maxScore - The maximum score to count up to. Can be positive/negative infinity, or specific score and inclusivity.
     *
     * Command Response - The number of members in the specified score range.
     * If `key` does not exist, it is treated as an empty sorted set, and the command returns 0.
     * If `minScore` is greater than `maxScore`, 0 is returned.
     */
    public zcount(key: string, minScore: ScoreLimit, maxScore: ScoreLimit): T {
        return this.addAndReturn(createZcount(key, minScore, maxScore));
    }

    /** Returns the string representation of the type of the value stored at `key`.
     * See https://redis.io/commands/type/ for more details.
     *
     * @param key - The key to check its data type.
     *
     * Command Response - If the key exists, the type of the stored value is returned. Otherwise, a "none" string is returned.
     */
    public type(key: string): T {
        return this.addAndReturn(createType(key));
    }

    /** Returns the length of the string value stored at `key`.
     * See https://redis.io/commands/strlen/ for more details.
     *
     * @param key - The `key` to check its length.
     *
     * Command Response - The length of the string value stored at `key`
     * If `key` does not exist, it is treated as an empty string, and the command returns 0.
     */
    public strlen(key: string): T {
        return this.addAndReturn(createStrlen(key));
    }

    /** Removes and returns the members with the lowest scores from the sorted set stored at `key`.
     * If `count` is provided, up to `count` members with the lowest scores are removed and returned.
     * Otherwise, only one member with the lowest score is removed and returned.
     * See https://redis.io/commands/zpopmin for more details.
     *
     * @param key - The key of the sorted set.
     * @param count - Specifies the quantity of members to pop. If not specified, pops one member.
     *
     * Command Response - A map of the removed members and their scores, ordered from the one with the lowest score to the one with the highest.
     * If `key` doesn't exist, it will be treated as an empty sorted set and the command returns an empty map.
     * If `count` is higher than the sorted set's cardinality, returns all members and their scores.
     */
    public zpopmin(key: string, count?: number): T {
        return this.addAndReturn(createZpopmin(key, count));
    }

    /** Removes and returns the members with the highest scores from the sorted set stored at `key`.
     * If `count` is provided, up to `count` members with the highest scores are removed and returned.
     * Otherwise, only one member with the highest score is removed and returned.
     * See https://redis.io/commands/zpopmax for more details.
     *
     * @param key - The key of the sorted set.
     * @param count - Specifies the quantity of members to pop. If not specified, pops one member.
     *
     * Command Response - A map of the removed members and their scores, ordered from the one with the highest score to the one with the lowest.
     * If `key` doesn't exist, it will be treated as an empty sorted set and the command returns an empty map.
     * If `count` is higher than the sorted set's cardinality, returns all members and their scores, ordered from highest to lowest.
     */
    public zpopmax(key: string, count?: number): T {
        return this.addAndReturn(createZpopmax(key, count));
    }

    /** Echoes the provided `message` back.
     * See https://redis.io/commands/echo for more details.
     *
     * @param message - The message to be echoed back.
     *
     * Command Response - The provided `message`.
     */
    public echo(message: string): T {
        return this.addAndReturn(createEcho(message));
    }

    /** Returns the remaining time to live of `key` that has a timeout, in milliseconds.
     * See https://redis.io/commands/pttl for more details.
     *
     * @param key - The key to return its timeout.
     *
     * Command Response - TTL in milliseconds. -2 if `key` does not exist, -1 if `key` exists but has no associated expire.
     */
    public pttl(key: string): T {
        return this.addAndReturn(createPttl(key));
    }

    /** Removes all elements in the sorted set stored at `key` with rank between `start` and `end`.
     * Both `start` and `end` are zero-based indexes with 0 being the element with the lowest score.
     * These indexes can be negative numbers, where they indicate offsets starting at the element with the highest score.
     * See https://redis.io/commands/zremrangebyrank/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param start - The starting point of the range.
     * @param end - The end of the range.
     *
     * Command Response - The number of members removed.
     * If `start` exceeds the end of the sorted set, or if `start` is greater than `end`, 0 returned.
     * If `end` exceeds the actual end of the sorted set, the range will stop at the actual end of the sorted set.
     * If `key` does not exist 0 will be returned.
     */
    public zremRangeByRank(key: string, start: number, end: number): T {
        return this.addAndReturn(createZremRangeByRank(key, start, end));
    }

    /** Removes all elements in the sorted set stored at `key` with a score between `minScore` and `maxScore`.
     * See https://redis.io/commands/zremrangebyscore/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param minScore - The minimum score to remove from. Can be positive/negative infinity, or specific score and inclusivity.
     * @param maxScore - The maximum score to remove to. Can be positive/negative infinity, or specific score and inclusivity.
     *
     * Command Response - the number of members removed.
     * If `key` does not exist, it is treated as an empty sorted set, and the command returns 0.
     * If `minScore` is greater than `maxScore`, 0 is returned.
     */
    public zremRangeByScore(
        key: string,
        minScore: ScoreLimit,
        maxScore: ScoreLimit,
    ): T {
        return this.addAndReturn(
            createZremRangeByScore(key, minScore, maxScore),
        );
    }

    /** Returns the rank of `member` in the sorted set stored at `key`, with scores ordered from low to high.
     * See https://redis.io/commands/zrank for more details.
     * To get the rank of `member` with its score, see `zrankWithScore`.
     *
     * @param key - The key of the sorted set.
     * @param member - The member whose rank is to be retrieved.
     *
     * Command Response - The rank of `member` in the sorted set.
     * If `key` doesn't exist, or if `member` is not present in the set, null will be returned.
     */
    public zrank(key: string, member: string): T {
        return this.addAndReturn(createZrank(key, member));
    }

    /** Returns the rank of `member` in the sorted set stored at `key` with its score, where scores are ordered from the lowest to highest.
     * See https://redis.io/commands/zrank for more details.
     *
     * @param key - The key of the sorted set.
     * @param member - The member whose rank is to be retrieved.
     *
     * Command Response - A list containing the rank and score of `member` in the sorted set.
     * If `key` doesn't exist, or if `member` is not present in the set, null will be returned.
     *
     * since - Redis version 7.2.0.
     */
    public zrankWithScore(key: string, member: string): T {
        return this.addAndReturn(createZrank(key, member, true));
    }

    /** Remove the existing timeout on `key`, turning the key from volatile (a key with an expire set) to
     * persistent (a key that will never expire as no timeout is associated).
     * See https://redis.io/commands/persist/ for more details.
     *
     * @param key - The key to remove the existing timeout on.
     *
     * Command Response - `false` if `key` does not exist or does not have an associated timeout, `true` if the timeout has been removed.
     */
    public persist(key: string): T {
        return this.addAndReturn(createPersist(key));
    }

    /** Executes a single command, without checking inputs. Every part of the command, including subcommands,
     *  should be added as a separate value in args.
     *
     *  @remarks - This function should only be used for single-response commands. Commands that don't return response (such as SUBSCRIBE), or that return potentially more than a single response (such as XREAD), or that change the client's behavior (such as entering pub/sub mode on RESP2 connections) shouldn't be called using this function.
     *
     * @example
     * Returns a list of all pub/sub clients:
     * ```ts
     * connection.customCommand(["CLIENT", "LIST","TYPE", "PUBSUB"])
     * ```
     */
    public customCommand(args: string[]): T {
        return this.addAndReturn(createCustomCommand(args));
    }

    /** Returns the element at index `index` in the list stored at `key`.
     * The index is zero-based, so 0 means the first element, 1 the second element and so on.
     * Negative indices can be used to designate elements starting at the tail of the list.
     * Here, -1 means the last element, -2 means the penultimate and so forth.
     * See https://redis.io/commands/lindex/ for more details.
     *
     * @param key - The `key` of the list.
     * @param index - The `index` of the element in the list to retrieve.
     * Command Response - The element at index in the list stored at `key`.
     * If `index` is out of range or if `key` does not exist, null is returned.
     */
    public lindex(key: string, index: number): T {
        return this.addAndReturn(createLindex(key, index));
    }

    /**
     * Adds an entry to the specified stream.
     * See https://redis.io/commands/xadd/ for more details.
     *
     * @param key - The key of the stream.
     * @param values - field-value pairs to be added to the entry.
     * @returns The id of the added entry, or `null` if `options.makeStream` is set to `false` and no stream with the matching `key` exists.
     */
    public xadd(
        key: string,
        values: [string, string][],
        options?: StreamAddOptions,
    ): T {
        return this.addAndReturn(createXadd(key, values, options));
    }

    /**
     * Trims the stream by evicting older entries.
     * See https://redis.io/commands/xtrim/ for more details.
     *
     * @param key - the key of the stream
     * @param options - options detailing how to trim the stream.
     * @returns The number of entries deleted from the stream.
     */
    public xtrim(key: string, options: StreamTrimOptions): T {
        return this.addAndReturn(createXtrim(key, options));
    }

    /** Returns the server time.
     * See https://redis.io/commands/time/ for details.
     *
     * @returns - The current server time as a two items `array`:
     * A Unix timestamp and the amount of microseconds already elapsed in the current second.
     * The returned `array` is in a [Unix timestamp, Microseconds already elapsed] format.
     */
    public time(): T {
        return this.addAndReturn(createTime());
    }

    /**
     * Reads entries from the given streams.
     * See https://redis.io/commands/xread/ for more details.
     *
     * @param keys_and_ids - pairs of keys and entry ids to read from. A pair is composed of a stream's key and the id of the entry after which the stream will be read.
     * @param options - options detailing how to read the stream.
     * @returns A map between a stream key, and an array of entries in the matching key. The entries are in an [id, fields[]] format.
     */
    public xread(
        keys_and_ids: Record<string, string>,
        options?: StreamReadOptions,
    ): T {
        return this.addAndReturn(createXread(keys_and_ids, options));
    }

    /**
     * Renames `key` to `newkey`.
     * If `newkey` already exists it is overwritten.
     * In Cluster mode, both `key` and `newkey` must be in the same hash slot,
     * meaning that in practice only keys that have the same hash tag can be reliably renamed in cluster.
     * See https://redis.io/commands/rename/ for more details.
     *
     * @param key - The key to rename.
     * @param newKey - The new name of the key.
     * Command Response - If the `key` was successfully renamed, return "OK". If `key` does not exist, an error is thrown.
     */
    public rename(key: string, newKey: string): T {
        return this.addAndReturn(createRename(key, newKey));
    }

    /** Blocking list pop primitive.
     * Pop an element from the tail of the first list that is non-empty,
     * with the given keys being checked in the order that they are given.
     * Blocks the connection when there are no elements to pop from any of the given lists.
     * See https://redis.io/commands/brpop/ for more details.
     * Note: BRPOP is a blocking command,
     * see [Blocking Commands](https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands) for more details and best practices.
     *
     * @param keys - The `keys` of the lists to pop from.
     * @param timeout - The `timeout` in seconds.
     * Command Response - An `array` containing the `key` from which the element was popped and the value of the popped element,
     * formatted as [key, value]. If no element could be popped and the timeout expired, returns Null.
     */
    public brpop(keys: string[], timeout: number): T {
        return this.addAndReturn(createBrpop(keys, timeout));
    }
}

/**
 * Extends BaseTransaction class for Redis standalone commands.
 * Transactions allow the execution of a group of commands in a single step.
 *
 * Command Response:
 *  An array of command responses is returned by the RedisClient.exec command, in the order they were given.
 *  Each element in the array represents a command given to the transaction.
 *  The response for each command depends on the executed Redis command.
 *  Specific response types are documented alongside each method.
 *
 * @example
 *       transaction = new Transaction()
 *          .set("key", "value")
 *          .select(1)  /// Standalone command
 *          .get("key");
 *       await RedisClient.exec(transaction);
 *       [OK , OK , null]
 */
export class Transaction extends BaseTransaction<Transaction> {
    /// TODO: add MOVE, SLAVEOF and all SENTINEL commands

    /** Change the currently selected Redis database.
     * See https://redis.io/commands/select/ for details.
     *
     * @param index - The index of the database to select.
     *
     * Command Response - A simple OK response.
     */
    public select(index: number): Transaction {
        return this.addAndReturn(createSelect(index));
    }
}

/**
 * Extends BaseTransaction class for cluster mode commands.
 * Transactions allow the execution of a group of commands in a single step.
 *
 * Command Response:
 *  An array of command responses is returned by the RedisClusterClient.exec command, in the order they were given.
 *  Each element in the array represents a command given to the transaction.
 *  The response for each command depends on the executed Redis command.
 *  Specific response types are documented alongside each method.
 *
 */
export class ClusterTransaction extends BaseTransaction<ClusterTransaction> {
    /// TODO: add all CLUSTER commands
}
