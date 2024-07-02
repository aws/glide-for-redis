/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static glide.api.commands.GenericBaseCommands.REPLACE_REDIS_API;
import static glide.api.commands.HashBaseCommands.WITH_VALUES_REDIS_API;
import static glide.api.commands.ListBaseCommands.COUNT_FOR_LIST_REDIS_API;
import static glide.api.commands.ServerManagementCommands.VERSION_REDIS_API;
import static glide.api.commands.SetBaseCommands.SET_LIMIT_REDIS_API;
import static glide.api.commands.SortedSetBaseCommands.COUNT_REDIS_API;
import static glide.api.commands.SortedSetBaseCommands.LIMIT_REDIS_API;
import static glide.api.commands.SortedSetBaseCommands.WITH_SCORES_REDIS_API;
import static glide.api.commands.SortedSetBaseCommands.WITH_SCORE_REDIS_API;
import static glide.api.commands.StringBaseCommands.IDX_COMMAND_STRING;
import static glide.api.commands.StringBaseCommands.LEN_REDIS_API;
import static glide.api.commands.StringBaseCommands.MINMATCHLEN_COMMAND_STRING;
import static glide.api.commands.StringBaseCommands.WITHMATCHLEN_COMMAND_STRING;
import static glide.api.models.commands.RangeOptions.createZRangeArgs;
import static glide.api.models.commands.SortBaseOptions.STORE_COMMAND_STRING;
import static glide.api.models.commands.bitmap.BitFieldOptions.createBitFieldArgs;
import static glide.api.models.commands.function.FunctionListOptions.LIBRARY_NAME_REDIS_API;
import static glide.api.models.commands.function.FunctionListOptions.WITH_CODE_REDIS_API;
import static glide.api.models.commands.function.FunctionLoadOptions.REPLACE;
import static glide.api.models.commands.stream.StreamClaimOptions.JUST_ID_REDIS_API;
import static glide.utils.ArrayTransformUtils.concatenateArrays;
import static glide.utils.ArrayTransformUtils.convertMapToKeyValueStringArray;
import static glide.utils.ArrayTransformUtils.flattenMapToGlideStringArray;
import static glide.utils.ArrayTransformUtils.flattenMapToGlideStringArrayValueFirst;
import static glide.utils.ArrayTransformUtils.mapGeoDataToArray;
import static redis_request.RedisRequestOuterClass.RequestType.Append;
import static redis_request.RedisRequestOuterClass.RequestType.BLMPop;
import static redis_request.RedisRequestOuterClass.RequestType.BLMove;
import static redis_request.RedisRequestOuterClass.RequestType.BLPop;
import static redis_request.RedisRequestOuterClass.RequestType.BRPop;
import static redis_request.RedisRequestOuterClass.RequestType.BZMPop;
import static redis_request.RedisRequestOuterClass.RequestType.BZPopMax;
import static redis_request.RedisRequestOuterClass.RequestType.BZPopMin;
import static redis_request.RedisRequestOuterClass.RequestType.BitCount;
import static redis_request.RedisRequestOuterClass.RequestType.BitField;
import static redis_request.RedisRequestOuterClass.RequestType.BitFieldReadOnly;
import static redis_request.RedisRequestOuterClass.RequestType.BitOp;
import static redis_request.RedisRequestOuterClass.RequestType.BitPos;
import static redis_request.RedisRequestOuterClass.RequestType.ClientGetName;
import static redis_request.RedisRequestOuterClass.RequestType.ClientId;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigGet;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigResetStat;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigRewrite;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigSet;
import static redis_request.RedisRequestOuterClass.RequestType.Copy;
import static redis_request.RedisRequestOuterClass.RequestType.CustomCommand;
import static redis_request.RedisRequestOuterClass.RequestType.DBSize;
import static redis_request.RedisRequestOuterClass.RequestType.Decr;
import static redis_request.RedisRequestOuterClass.RequestType.DecrBy;
import static redis_request.RedisRequestOuterClass.RequestType.Del;
import static redis_request.RedisRequestOuterClass.RequestType.Echo;
import static redis_request.RedisRequestOuterClass.RequestType.Exists;
import static redis_request.RedisRequestOuterClass.RequestType.Expire;
import static redis_request.RedisRequestOuterClass.RequestType.ExpireAt;
import static redis_request.RedisRequestOuterClass.RequestType.ExpireTime;
import static redis_request.RedisRequestOuterClass.RequestType.FCall;
import static redis_request.RedisRequestOuterClass.RequestType.FCallReadOnly;
import static redis_request.RedisRequestOuterClass.RequestType.FlushAll;
import static redis_request.RedisRequestOuterClass.RequestType.FlushDB;
import static redis_request.RedisRequestOuterClass.RequestType.FunctionDelete;
import static redis_request.RedisRequestOuterClass.RequestType.FunctionFlush;
import static redis_request.RedisRequestOuterClass.RequestType.FunctionList;
import static redis_request.RedisRequestOuterClass.RequestType.FunctionLoad;
import static redis_request.RedisRequestOuterClass.RequestType.FunctionStats;
import static redis_request.RedisRequestOuterClass.RequestType.GeoAdd;
import static redis_request.RedisRequestOuterClass.RequestType.GeoDist;
import static redis_request.RedisRequestOuterClass.RequestType.GeoHash;
import static redis_request.RedisRequestOuterClass.RequestType.GeoPos;
import static redis_request.RedisRequestOuterClass.RequestType.GeoSearch;
import static redis_request.RedisRequestOuterClass.RequestType.GeoSearchStore;
import static redis_request.RedisRequestOuterClass.RequestType.Get;
import static redis_request.RedisRequestOuterClass.RequestType.GetBit;
import static redis_request.RedisRequestOuterClass.RequestType.GetDel;
import static redis_request.RedisRequestOuterClass.RequestType.GetEx;
import static redis_request.RedisRequestOuterClass.RequestType.GetRange;
import static redis_request.RedisRequestOuterClass.RequestType.HDel;
import static redis_request.RedisRequestOuterClass.RequestType.HExists;
import static redis_request.RedisRequestOuterClass.RequestType.HGet;
import static redis_request.RedisRequestOuterClass.RequestType.HGetAll;
import static redis_request.RedisRequestOuterClass.RequestType.HIncrBy;
import static redis_request.RedisRequestOuterClass.RequestType.HIncrByFloat;
import static redis_request.RedisRequestOuterClass.RequestType.HKeys;
import static redis_request.RedisRequestOuterClass.RequestType.HLen;
import static redis_request.RedisRequestOuterClass.RequestType.HMGet;
import static redis_request.RedisRequestOuterClass.RequestType.HRandField;
import static redis_request.RedisRequestOuterClass.RequestType.HScan;
import static redis_request.RedisRequestOuterClass.RequestType.HSet;
import static redis_request.RedisRequestOuterClass.RequestType.HSetNX;
import static redis_request.RedisRequestOuterClass.RequestType.HStrlen;
import static redis_request.RedisRequestOuterClass.RequestType.HVals;
import static redis_request.RedisRequestOuterClass.RequestType.Incr;
import static redis_request.RedisRequestOuterClass.RequestType.IncrBy;
import static redis_request.RedisRequestOuterClass.RequestType.IncrByFloat;
import static redis_request.RedisRequestOuterClass.RequestType.Info;
import static redis_request.RedisRequestOuterClass.RequestType.LCS;
import static redis_request.RedisRequestOuterClass.RequestType.LIndex;
import static redis_request.RedisRequestOuterClass.RequestType.LInsert;
import static redis_request.RedisRequestOuterClass.RequestType.LLen;
import static redis_request.RedisRequestOuterClass.RequestType.LMPop;
import static redis_request.RedisRequestOuterClass.RequestType.LMove;
import static redis_request.RedisRequestOuterClass.RequestType.LPop;
import static redis_request.RedisRequestOuterClass.RequestType.LPos;
import static redis_request.RedisRequestOuterClass.RequestType.LPush;
import static redis_request.RedisRequestOuterClass.RequestType.LPushX;
import static redis_request.RedisRequestOuterClass.RequestType.LRange;
import static redis_request.RedisRequestOuterClass.RequestType.LRem;
import static redis_request.RedisRequestOuterClass.RequestType.LSet;
import static redis_request.RedisRequestOuterClass.RequestType.LTrim;
import static redis_request.RedisRequestOuterClass.RequestType.LastSave;
import static redis_request.RedisRequestOuterClass.RequestType.Lolwut;
import static redis_request.RedisRequestOuterClass.RequestType.MGet;
import static redis_request.RedisRequestOuterClass.RequestType.MSet;
import static redis_request.RedisRequestOuterClass.RequestType.MSetNX;
import static redis_request.RedisRequestOuterClass.RequestType.ObjectEncoding;
import static redis_request.RedisRequestOuterClass.RequestType.ObjectFreq;
import static redis_request.RedisRequestOuterClass.RequestType.ObjectIdleTime;
import static redis_request.RedisRequestOuterClass.RequestType.ObjectRefCount;
import static redis_request.RedisRequestOuterClass.RequestType.PExpire;
import static redis_request.RedisRequestOuterClass.RequestType.PExpireAt;
import static redis_request.RedisRequestOuterClass.RequestType.PExpireTime;
import static redis_request.RedisRequestOuterClass.RequestType.PTTL;
import static redis_request.RedisRequestOuterClass.RequestType.Persist;
import static redis_request.RedisRequestOuterClass.RequestType.PfAdd;
import static redis_request.RedisRequestOuterClass.RequestType.PfCount;
import static redis_request.RedisRequestOuterClass.RequestType.PfMerge;
import static redis_request.RedisRequestOuterClass.RequestType.Ping;
import static redis_request.RedisRequestOuterClass.RequestType.Publish;
import static redis_request.RedisRequestOuterClass.RequestType.RPop;
import static redis_request.RedisRequestOuterClass.RequestType.RPush;
import static redis_request.RedisRequestOuterClass.RequestType.RPushX;
import static redis_request.RedisRequestOuterClass.RequestType.RandomKey;
import static redis_request.RedisRequestOuterClass.RequestType.Rename;
import static redis_request.RedisRequestOuterClass.RequestType.RenameNX;
import static redis_request.RedisRequestOuterClass.RequestType.SAdd;
import static redis_request.RedisRequestOuterClass.RequestType.SCard;
import static redis_request.RedisRequestOuterClass.RequestType.SDiff;
import static redis_request.RedisRequestOuterClass.RequestType.SDiffStore;
import static redis_request.RedisRequestOuterClass.RequestType.SInter;
import static redis_request.RedisRequestOuterClass.RequestType.SInterCard;
import static redis_request.RedisRequestOuterClass.RequestType.SInterStore;
import static redis_request.RedisRequestOuterClass.RequestType.SIsMember;
import static redis_request.RedisRequestOuterClass.RequestType.SMIsMember;
import static redis_request.RedisRequestOuterClass.RequestType.SMembers;
import static redis_request.RedisRequestOuterClass.RequestType.SMove;
import static redis_request.RedisRequestOuterClass.RequestType.SPop;
import static redis_request.RedisRequestOuterClass.RequestType.SRandMember;
import static redis_request.RedisRequestOuterClass.RequestType.SRem;
import static redis_request.RedisRequestOuterClass.RequestType.SScan;
import static redis_request.RedisRequestOuterClass.RequestType.SUnion;
import static redis_request.RedisRequestOuterClass.RequestType.SUnionStore;
import static redis_request.RedisRequestOuterClass.RequestType.Set;
import static redis_request.RedisRequestOuterClass.RequestType.SetBit;
import static redis_request.RedisRequestOuterClass.RequestType.SetRange;
import static redis_request.RedisRequestOuterClass.RequestType.Sort;
import static redis_request.RedisRequestOuterClass.RequestType.SortReadOnly;
import static redis_request.RedisRequestOuterClass.RequestType.Strlen;
import static redis_request.RedisRequestOuterClass.RequestType.TTL;
import static redis_request.RedisRequestOuterClass.RequestType.Time;
import static redis_request.RedisRequestOuterClass.RequestType.Touch;
import static redis_request.RedisRequestOuterClass.RequestType.Type;
import static redis_request.RedisRequestOuterClass.RequestType.Unlink;
import static redis_request.RedisRequestOuterClass.RequestType.Wait;
import static redis_request.RedisRequestOuterClass.RequestType.XAck;
import static redis_request.RedisRequestOuterClass.RequestType.XAdd;
import static redis_request.RedisRequestOuterClass.RequestType.XClaim;
import static redis_request.RedisRequestOuterClass.RequestType.XDel;
import static redis_request.RedisRequestOuterClass.RequestType.XGroupCreate;
import static redis_request.RedisRequestOuterClass.RequestType.XGroupCreateConsumer;
import static redis_request.RedisRequestOuterClass.RequestType.XGroupDelConsumer;
import static redis_request.RedisRequestOuterClass.RequestType.XGroupDestroy;
import static redis_request.RedisRequestOuterClass.RequestType.XGroupSetId;
import static redis_request.RedisRequestOuterClass.RequestType.XLen;
import static redis_request.RedisRequestOuterClass.RequestType.XPending;
import static redis_request.RedisRequestOuterClass.RequestType.XRange;
import static redis_request.RedisRequestOuterClass.RequestType.XRead;
import static redis_request.RedisRequestOuterClass.RequestType.XReadGroup;
import static redis_request.RedisRequestOuterClass.RequestType.XRevRange;
import static redis_request.RedisRequestOuterClass.RequestType.XTrim;
import static redis_request.RedisRequestOuterClass.RequestType.ZAdd;
import static redis_request.RedisRequestOuterClass.RequestType.ZCard;
import static redis_request.RedisRequestOuterClass.RequestType.ZCount;
import static redis_request.RedisRequestOuterClass.RequestType.ZDiff;
import static redis_request.RedisRequestOuterClass.RequestType.ZDiffStore;
import static redis_request.RedisRequestOuterClass.RequestType.ZIncrBy;
import static redis_request.RedisRequestOuterClass.RequestType.ZInter;
import static redis_request.RedisRequestOuterClass.RequestType.ZInterCard;
import static redis_request.RedisRequestOuterClass.RequestType.ZInterStore;
import static redis_request.RedisRequestOuterClass.RequestType.ZLexCount;
import static redis_request.RedisRequestOuterClass.RequestType.ZMPop;
import static redis_request.RedisRequestOuterClass.RequestType.ZMScore;
import static redis_request.RedisRequestOuterClass.RequestType.ZPopMax;
import static redis_request.RedisRequestOuterClass.RequestType.ZPopMin;
import static redis_request.RedisRequestOuterClass.RequestType.ZRandMember;
import static redis_request.RedisRequestOuterClass.RequestType.ZRange;
import static redis_request.RedisRequestOuterClass.RequestType.ZRangeStore;
import static redis_request.RedisRequestOuterClass.RequestType.ZRank;
import static redis_request.RedisRequestOuterClass.RequestType.ZRem;
import static redis_request.RedisRequestOuterClass.RequestType.ZRemRangeByLex;
import static redis_request.RedisRequestOuterClass.RequestType.ZRemRangeByRank;
import static redis_request.RedisRequestOuterClass.RequestType.ZRemRangeByScore;
import static redis_request.RedisRequestOuterClass.RequestType.ZRevRank;
import static redis_request.RedisRequestOuterClass.RequestType.ZScan;
import static redis_request.RedisRequestOuterClass.RequestType.ZScore;
import static redis_request.RedisRequestOuterClass.RequestType.ZUnion;
import static redis_request.RedisRequestOuterClass.RequestType.ZUnionStore;

import com.google.protobuf.ByteString;
import glide.api.models.commands.ExpireOptions;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.GetExOptions;
import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.commands.LInsertOptions.InsertPosition;
import glide.api.models.commands.LPosOptions;
import glide.api.models.commands.ListDirection;
import glide.api.models.commands.RangeOptions;
import glide.api.models.commands.RangeOptions.InfLexBound;
import glide.api.models.commands.RangeOptions.InfScoreBound;
import glide.api.models.commands.RangeOptions.LexBoundary;
import glide.api.models.commands.RangeOptions.LexRange;
import glide.api.models.commands.RangeOptions.RangeByIndex;
import glide.api.models.commands.RangeOptions.RangeByLex;
import glide.api.models.commands.RangeOptions.RangeByScore;
import glide.api.models.commands.RangeOptions.RangeQuery;
import glide.api.models.commands.RangeOptions.ScoreBoundary;
import glide.api.models.commands.RangeOptions.ScoreRange;
import glide.api.models.commands.RangeOptions.ScoredRangeQuery;
import glide.api.models.commands.ScoreFilter;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.SetOptions.ConditionalSet;
import glide.api.models.commands.SetOptions.SetOptionsBuilder;
import glide.api.models.commands.WeightAggregateOptions;
import glide.api.models.commands.WeightAggregateOptions.Aggregate;
import glide.api.models.commands.WeightAggregateOptions.KeyArray;
import glide.api.models.commands.WeightAggregateOptions.KeyArrayBinary;
import glide.api.models.commands.WeightAggregateOptions.KeysOrWeightedKeys;
import glide.api.models.commands.WeightAggregateOptions.KeysOrWeightedKeysBinary;
import glide.api.models.commands.WeightAggregateOptions.WeightedKeys;
import glide.api.models.commands.WeightAggregateOptions.WeightedKeysBinary;
import glide.api.models.commands.ZAddOptions;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldGet;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldIncrby;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldOverflow;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldReadOnlySubCommands;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldSet;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldSubCommands;
import glide.api.models.commands.bitmap.BitFieldOptions.Offset;
import glide.api.models.commands.bitmap.BitFieldOptions.OffsetMultiplier;
import glide.api.models.commands.bitmap.BitmapIndexType;
import glide.api.models.commands.bitmap.BitwiseOperation;
import glide.api.models.commands.geospatial.GeoAddOptions;
import glide.api.models.commands.geospatial.GeoSearchOptions;
import glide.api.models.commands.geospatial.GeoSearchOrigin;
import glide.api.models.commands.geospatial.GeoSearchResultOptions;
import glide.api.models.commands.geospatial.GeoSearchShape;
import glide.api.models.commands.geospatial.GeoSearchStoreOptions;
import glide.api.models.commands.geospatial.GeoUnit;
import glide.api.models.commands.geospatial.GeospatialData;
import glide.api.models.commands.scan.HScanOptions;
import glide.api.models.commands.scan.SScanOptions;
import glide.api.models.commands.scan.ZScanOptions;
import glide.api.models.commands.stream.StreamAddOptions;
import glide.api.models.commands.stream.StreamAddOptions.StreamAddOptionsBuilder;
import glide.api.models.commands.stream.StreamClaimOptions;
import glide.api.models.commands.stream.StreamGroupOptions;
import glide.api.models.commands.stream.StreamPendingOptions;
import glide.api.models.commands.stream.StreamRange;
import glide.api.models.commands.stream.StreamRange.IdBound;
import glide.api.models.commands.stream.StreamRange.InfRangeBound;
import glide.api.models.commands.stream.StreamReadGroupOptions;
import glide.api.models.commands.stream.StreamReadOptions;
import glide.api.models.commands.stream.StreamTrimOptions;
import glide.api.models.configuration.ReadFrom;
import glide.managers.CommandManager;
import java.util.Arrays;
import java.util.Map;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.lang3.ArrayUtils;
import redis_request.RedisRequestOuterClass.Command;
import redis_request.RedisRequestOuterClass.Command.ArgsArray;
import redis_request.RedisRequestOuterClass.RequestType;
import redis_request.RedisRequestOuterClass.Transaction;

/**
 * Base class encompassing shared commands for both standalone and cluster mode implementations in a
 * transaction. Transactions allow the execution of a group of commands in a single step.
 *
 * <p>Command Response: An array of command responses is returned by the client exec command, in the
 * order they were given. Each element in the array represents a command given to the transaction.
 * The response for each command depends on the executed Redis command. Specific response types are
 * documented alongside each method.
 *
 * @param <T> child typing for chaining method calls
 */
@Getter
public abstract class BaseTransaction<T extends BaseTransaction<T>> {
    /** Command class to send a single request to Redis. */
    protected final Transaction.Builder protobufTransaction = Transaction.newBuilder();

    protected boolean binarySafeOutput = false;

    protected abstract T getThis();

    /** Enable binary output */
    public T withBinarySafeOutput() {
        binarySafeOutput = true;
        return getThis();
    }

    /**
     * Return true if the output array from this transaction should handle strings as valid UTF-8
     * strings or use GlideString
     */
    public boolean isBinarySafeOutput() {
        return this.binarySafeOutput;
    }

    /**
     * Executes a single command, without checking inputs. Every part of the command, including
     * subcommands, should be added as a separate value in args.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @apiNote See <a
     *     href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#custom-command">Glide
     *     for Redis Wiki</a> for details on the restrictions and limitations of the custom command
     *     API.
     * @param args Arguments for the custom command.
     * @return A response from Redis with an <code>Object</code>.
     * @example Returns a list of all pub/sub clients:
     *     <pre>{@code
     * Object result = client.customCommand(new String[]{ "CLIENT", "LIST", "TYPE", "PUBSUB" }).get();
     * }</pre>
     */
    public <ArgType> T customCommand(ArgType[] args) {
        checkTypeOrThrow(args);
        protobufTransaction.addCommands(buildCommand(CustomCommand, newArgsBuilder().add(args)));
        return getThis();
    }

    /**
     * Echoes the provided <code>message</code> back.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/echo>redis.io</a> for details.
     * @param message The message to be echoed back.
     * @return Command Response - The provided <code>message</code>.
     */
    public <ArgType> T echo(@NonNull ArgType message) {
        checkTypeOrThrow(message);
        protobufTransaction.addCommands(buildCommand(Echo, newArgsBuilder().add(message)));
        return getThis();
    }

    /**
     * Pings the Redis server.
     *
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
     * @return Command Response - A response from Redis with a <code>String</code>.
     */
    public T ping() {
        protobufTransaction.addCommands(buildCommand(Ping));
        return getThis();
    }

    /**
     * Pings the Redis server.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
     * @param msg The ping argument that will be returned.
     * @return Command Response - A response from Redis with a <code>String</code>.
     */
    public <ArgType> T ping(@NonNull ArgType msg) {
        checkTypeOrThrow(msg);
        protobufTransaction.addCommands(buildCommand(Ping, newArgsBuilder().add(msg)));
        return getThis();
    }

    /**
     * Gets information and statistics about the Redis server using the {@link Section#DEFAULT}
     * option.
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @return Command Response - A <code>String</code> with server info.
     */
    public T info() {
        protobufTransaction.addCommands(buildCommand(Info));
        return getThis();
    }

    /**
     * Gets information and statistics about the Redis server.
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @param options A list of {@link Section} values specifying which sections of information to
     *     retrieve. When no parameter is provided, the {@link Section#DEFAULT} option is assumed.
     * @return Command Response - A <code>String</code> containing the requested {@link Section}s.
     */
    public T info(@NonNull InfoOptions options) {
        protobufTransaction.addCommands(buildCommand(Info, newArgsBuilder().add(options.toArgs())));
        return getThis();
    }

    /**
     * Removes the specified <code>keys</code> from the database. A key is ignored if it does not
     * exist.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/del/">redis.io</a> for details.
     * @param keys The keys we wanted to remove.
     * @return Command Response - The number of keys that were removed.
     */
    public <ArgType> T del(@NonNull ArgType[] keys) {
        checkTypeOrThrow(keys);
        protobufTransaction.addCommands(buildCommand(Del, newArgsBuilder().add(keys)));
        return getThis();
    }

    /**
     * Gets the value associated with the given key, or null if no such value exists.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/get/">redis.io</a> for details.
     * @param key The key to retrieve from the database.
     * @return Command Response - If <code>key</code> exists, returns the <code>value</code> of <code>
     *     key</code> as a String. Otherwise, return <code>null</code>.
     */
    public <ArgType> T get(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(Get, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Gets a string value associated with the given <code>key</code> and deletes the key.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/docs/latest/commands/getdel/">redis.io</a> for details.
     * @param key The <code>key</code> to retrieve from the database.
     * @return Command Response - If <code>key</code> exists, returns the <code>value</code> of <code>
     *     key</code>. Otherwise, return <code>null</code>.
     */
    public <ArgType> T getdel(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(GetDel, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Gets the value associated with the given <code>key</code>.
     *
     * @since Redis 6.2.0.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/docs/latest/commands/getex/">redis.io</a> for details.
     * @param key The <code>key</code> to retrieve from the database.
     * @return Command Response - If <code>key</code> exists, return the <code>value</code> of the
     *     <code>key</code>. Otherwise, return <code>null</code>.
     */
    public <ArgType> T getex(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(GetEx, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Gets the value associated with the given <code>key</code>.
     *
     * @since Redis 6.2.0.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/docs/latest/commands/getex/">redis.io</a> for details.
     * @param key The <code>key</code> to retrieve from the database.
     * @param options The {@link GetExOptions} options.
     * @return Command Response - If <code>key</code> exists, return the <code>value</code> of the
     *     <code>key</code>. Otherwise, return <code>null</code>.
     */
    public <ArgType> T getex(@NonNull ArgType key, @NonNull GetExOptions options) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(GetEx, newArgsBuilder().add(key).add(options.toArgs())));
        return getThis();
    }

    /**
     * Sets the given key with the given value.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/set/">redis.io</a> for details.
     * @param key The key to store.
     * @param value The value to store with the given <code>key</code>.
     * @return Command Response - A response from Redis.
     */
    public <ArgType> T set(@NonNull ArgType key, @NonNull ArgType value) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(Set, newArgsBuilder().add(key).add(value)));
        return getThis();
    }

    /**
     * Sets the given key with the given value. Return value is dependent on the passed options.
     *
     * @see <a href="https://redis.io/commands/set/">redis.io</a> for details.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @param key The key to store.
     * @param value The value to store with the given key.
     * @param options The Set options.
     * @return Command Response - A <code>String</code> or <code>null</code> response. The old value
     *     as a <code>String</code> if {@link SetOptionsBuilder#returnOldValue(boolean)} is set.
     *     Otherwise, if the value isn't set because of {@link ConditionalSet#ONLY_IF_EXISTS} or
     *     {@link ConditionalSet#ONLY_IF_DOES_NOT_EXIST} conditions, return <code>null</code>.
     *     Otherwise, return <code>OK</code>.
     */
    public <ArgType> T set(
            @NonNull ArgType key, @NonNull ArgType value, @NonNull SetOptions options) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(Set, newArgsBuilder().add(key).add(value).add(options.toArgs())));
        return getThis();
    }

    /**
     * Appends a <code>value</code> to a <code>key</code>. If <code>key</code> does not exist it is
     * created and set as an empty string, so <code>APPEND</code> will be similar to {@see #set} in
     * this special case.
     *
     * @see <a href="https://redis.io/docs/latest/commands/append/">redis.io</a> for details.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @param key The key of the string.
     * @param value The value to append.
     * @return Command Response - The length of the string after appending the value.
     */
    public <ArgType> T append(@NonNull ArgType key, @NonNull ArgType value) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(Append, newArgsBuilder().add(key).add(value)));
        return getThis();
    }

    /**
     * Retrieves the values of multiple <code>keys</code>.
     *
     * @see <a href="https://redis.io/commands/mget/">redis.io</a> for details.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @param keys A list of keys to retrieve values for.
     * @return Command Response - An array of values corresponding to the provided <code>keys</code>.
     *     <br>
     *     If a <code>key</code>is not found, its corresponding value in the list will be <code>null
     *     </code>.
     */
    public <ArgType> T mget(@NonNull ArgType[] keys) {
        checkTypeOrThrow(keys);
        protobufTransaction.addCommands(buildCommand(MGet, newArgsBuilder().add(keys)));
        return getThis();
    }

    /**
     * Sets multiple keys to multiple values in a single operation.
     *
     * @see <a href="https://redis.io/commands/mset/">redis.io</a> for details.
     * @param keyValueMap A key-value map consisting of keys and their respective values to set.
     * @return Command Response - Always <code>OK</code>.
     */
    public T mset(@NonNull Map<?, ?> keyValueMap) {
        GlideString[] args = flattenMapToGlideStringArray(keyValueMap);
        protobufTransaction.addCommands(buildCommand(MSet, newArgsBuilder().add(args)));
        return getThis();
    }

    /**
     * Sets multiple keys to multiple values in a single operation. Performs no operation at all even
     * if just a single key already exists.
     *
     * @see <a href="https://redis.io/commands/msetnx/">redis.io</a> for details.
     * @param keyValueMap A key-value map consisting of keys and their respective values to set.
     * @return Command Response - <code>true</code> if all keys were set, <code>false</code> if no key
     *     was set.
     */
    public T msetnx(@NonNull Map<?, ?> keyValueMap) {
        GlideString[] args = flattenMapToGlideStringArray(keyValueMap);
        protobufTransaction.addCommands(buildCommand(MSetNX, newArgsBuilder().add(args)));
        return getThis();
    }

    /**
     * Increments the number stored at <code>key</code> by one. If <code>key</code> does not exist, it
     * is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/incr/">redis.io</a> for details.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @param key The key to increment its value.
     * @return Command Response - The value of <code>key</code> after the increment.
     */
    public <ArgType> T incr(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(Incr, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Increments the number stored at <code>key</code> by <code>amount</code>. If <code>key</code>
     * does not exist, it is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/incrby/">redis.io</a> for details.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @param key The key to increment its value.
     * @param amount The amount to increment.
     * @return Command Response - The value of <code>key</code> after the increment.
     */
    public <ArgType> T incrBy(@NonNull ArgType key, long amount) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(IncrBy, newArgsBuilder().add(key).add(amount)));
        return getThis();
    }

    /**
     * Increments the string representing a floating point number stored at <code>key</code> by <code>
     * amount</code>. By using a negative increment value, the result is that the value stored at
     * <code>key</code> is decremented. If <code>key</code> does not exist, it is set to 0 before
     * performing the operation.
     *
     * @see <a href="https://redis.io/commands/incrbyfloat/">redis.io</a> for details.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @param key The key to increment its value.
     * @param amount The amount to increment.
     * @return Command Response - The value of <code>key</code> after the increment.
     */
    public <ArgType> T incrByFloat(@NonNull ArgType key, double amount) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(IncrByFloat, newArgsBuilder().add(key).add(amount)));
        return getThis();
    }

    /**
     * Decrements the number stored at <code>key</code> by one. If <code>key</code> does not exist, it
     * is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/decr/">redis.io</a> for details.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @param key The key to decrement its value.
     * @return Command Response - The value of <code>key</code> after the decrement.
     */
    public <ArgType> T decr(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(Decr, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Decrements the number stored at <code>key</code> by <code>amount</code>. If <code>key</code>
     * does not exist, it is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/decrby/">redis.io</a> for details.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @param key The key to decrement its value.
     * @param amount The amount to decrement.
     * @return Command Response - The value of <code>key</code> after the decrement.
     */
    public <ArgType> T decrBy(@NonNull ArgType key, long amount) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(DecrBy, newArgsBuilder().add(key).add(amount)));
        return getThis();
    }

    /**
     * Returns the length of the string value stored at <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/strlen/">redis.io</a> for details.
     * @param key The key to check its length.
     * @return Command Response - The length of the string value stored at key.<br>
     *     If <code>key</code> does not exist, it is treated as an empty string, and the command
     *     returns <code>0</code>.
     */
    public <ArgType> T strlen(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(Strlen, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Overwrites part of the string stored at <code>key</code>, starting at the specified <code>
     * offset</code>, for the entire length of <code>value</code>.<br>
     * If the <code>offset</code> is larger than the current length of the string at <code>key</code>,
     * the string is padded with zero bytes to make <code>offset</code> fit. Creates the <code>key
     * </code> if it doesn't exist.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/setrange/">redis.io</a> for details.
     * @param key The key of the string to update.
     * @param offset The position in the string where <code>value</code> should be written.
     * @param value The string written with <code>offset</code>.
     * @return Command Response - The length of the string stored at <code>key</code> after it was
     *     modified.
     */
    public <ArgType> T setrange(@NonNull ArgType key, int offset, @NonNull ArgType value) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(SetRange, newArgsBuilder().add(key).add(offset).add(value)));
        return getThis();
    }

    /**
     * Returns the substring of the string value stored at <code>key</code>, determined by the offsets
     * <code>start</code> and <code>end</code> (both are inclusive). Negative offsets can be used in
     * order to provide an offset starting from the end of the string. So <code>-1</code> means the
     * last character, <code>-2</code> the penultimate and so forth.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/getrange/">redis.io</a> for details.
     * @param key The key of the string.
     * @param start The starting offset.
     * @param end The ending offset.
     * @return Command Response - A substring extracted from the value stored at <code>key</code>.
     */
    public <ArgType> T getrange(@NonNull ArgType key, int start, int end) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(GetRange, newArgsBuilder().add(key).add(start).add(end)));
        return getThis();
    }

    /**
     * Retrieves the value associated with <code>field</code> in the hash stored at <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/hget/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param field The field in the hash stored at <code>key</code> to retrieve from the database.
     * @return Command Response - The value associated with <code>field</code>, or <code>null</code>
     *     when <code>field</code> is not present in the hash or <code>key</code> does not exist.
     */
    public <ArgType> T hget(@NonNull ArgType key, @NonNull ArgType field) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(HGet, newArgsBuilder().add(key).add(field)));
        return getThis();
    }

    /**
     * Sets the specified fields to their respective values in the hash stored at <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/hset/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param fieldValueMap A field-value map consisting of fields and their corresponding values to
     *     be set in the hash stored at the specified key.
     * @return Command Response - The number of fields that were added.
     */
    public <ArgType> T hset(@NonNull ArgType key, @NonNull Map<ArgType, ArgType> fieldValueMap) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(
                        HSet, newArgsBuilder().add(key).add(flattenMapToGlideStringArray(fieldValueMap))));
        return getThis();
    }

    /**
     * Sets <code>field</code> in the hash stored at <code>key</code> to <code>value</code>, only if
     * <code>field</code> does not yet exist.<br>
     * If <code>key</code> does not exist, a new key holding a hash is created.<br>
     * If <code>field</code> already exists, this operation has no effect.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/hsetnx/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param field The field to set the value for.
     * @param value The value to set.
     * @return Command Response - <code>true</code> if the field was set, <code>false</code> if the
     *     field already existed and was not set.
     */
    public <ArgType> T hsetnx(@NonNull ArgType key, @NonNull ArgType field, @NonNull ArgType value) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(HSetNX, newArgsBuilder().add(key).add(field).add(value)));
        return getThis();
    }

    /**
     * Removes the specified fields from the hash stored at <code>key</code>. Specified fields that do
     * not exist within this hash are ignored.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/hdel/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to remove from the hash stored at <code>key</code>.
     * @return Command Response - The number of fields that were removed from the hash, not including
     *     specified but non-existing fields.<br>
     *     If <code>key</code> does not exist, it is treated as an empty hash and it returns 0.<br>
     */
    public <ArgType> T hdel(@NonNull ArgType key, @NonNull ArgType[] fields) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(HDel, newArgsBuilder().add(key).add(fields)));
        return getThis();
    }

    /**
     * Returns the number of fields contained in the hash stored at <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/hlen/">redis.io</a> for details.
     * @param key The key of the hash.
     * @return Command Response - The number of fields in the hash, or <code>0</code> when the key
     *     does not exist.<br>
     *     If <code>key</code> holds a value that is not a hash, an error is returned.
     */
    public <ArgType> T hlen(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(HLen, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Returns all values in the hash stored at <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/hvals/">redis.io</a> for details.
     * @param key The key of the hash.
     * @return Command Response - An <code>array</code> of values in the hash, or an <code>empty array
     *     </code> when the key does not exist.
     */
    public <ArgType> T hvals(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(HVals, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Returns the values associated with the specified fields in the hash stored at <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/hmget/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields in the hash stored at <code>key</code> to retrieve from the database.
     * @return Command Response - An array of values associated with the given fields, in the same
     *     order as they are requested.<br>
     *     For every field that does not exist in the hash, a null value is returned.<br>
     *     If <code>key</code> does not exist, it is treated as an empty hash, and it returns an array
     *     of null values.<br>
     */
    public <ArgType> T hmget(@NonNull ArgType key, @NonNull ArgType[] fields) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(HMGet, newArgsBuilder().add(key).add(fields)));
        return getThis();
    }

    /**
     * Returns if <code>field</code> is an existing field in the hash stored at <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/hexists/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param field The field to check in the hash stored at <code>key</code>.
     * @return Command Response - <code>True</code> if the hash contains the specified field. If the
     *     hash does not contain the field, or if the key does not exist, it returns <code>False
     *     </code>.
     */
    public <ArgType> T hexists(@NonNull ArgType key, @NonNull ArgType field) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(HExists, newArgsBuilder().add(key).add(field)));
        return getThis();
    }

    /**
     * Returns all fields and values of the hash stored at <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/hgetall/">redis.io</a> for details.
     * @param key The key of the hash.
     * @return Command Response - A <code>Map</code> of fields and their values stored in the hash.
     *     Every field name in the map is associated with its corresponding value.<br>
     *     If <code>key</code> does not exist, it returns an empty map.
     */
    public <ArgType> T hgetall(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(HGetAll, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Increments the number stored at <code>field</code> in the hash stored at <code>key</code> by
     * increment. By using a negative increment value, the value stored at <code>field</code> in the
     * hash stored at <code>key</code> is decremented. If <code>field</code> or <code>key</code> does
     * not exist, it is set to 0 before performing the operation.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/hincrby/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param field The field in the hash stored at <code>key</code> to increment or decrement its
     *     value.
     * @param amount The amount by which to increment or decrement the field's value. Use a negative
     *     value to decrement.
     * @return Command Response - The value of <code>field</code> in the hash stored at <code>key
     *     </code> after the increment or decrement.
     */
    public <ArgType> T hincrBy(@NonNull ArgType key, @NonNull ArgType field, long amount) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(HIncrBy, newArgsBuilder().add(key).add(field).add(amount)));
        return getThis();
    }

    /**
     * Increments the string representing a floating point number stored at <code>field</code> in the
     * hash stored at <code>key</code> by increment. By using a negative increment value, the value
     * stored at <code>field</code> in the hash stored at <code>key</code> is decremented. If <code>
     * field</code> or <code>key</code> does not exist, it is set to 0 before performing the
     * operation.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/hincrbyfloat/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param field The field in the hash stored at <code>key</code> to increment or decrement its
     *     value.
     * @param amount The amount by which to increment or decrement the field's value. Use a negative
     *     value to decrement.
     * @return Command Response - The value of <code>field</code> in the hash stored at <code>key
     *     </code> after the increment or decrement.
     */
    public <ArgType> T hincrByFloat(@NonNull ArgType key, @NonNull ArgType field, double amount) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(HIncrByFloat, newArgsBuilder().add(key).add(field).add(amount)));
        return getThis();
    }

    /**
     * Returns all field names in the hash stored at <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://valkey.io/commands/hkeys/">redis.io</a> for details
     * @param key The key of the hash.
     * @return Command Response - An <code>array</code> of field names in the hash, or an <code>
     *     empty array</code> when the key does not exist.
     */
    public <ArgType> T hkeys(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(HKeys, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Returns the string length of the value associated with <code>field</code> in the hash stored at
     * <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://valkey.io/commands/hstrlen/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param field The field in the hash.
     * @return Command Response - The string length or <code>0</code> if <code>field</code> or <code>
     *     key</code> does not exist.
     */
    public <ArgType> T hstrlen(@NonNull ArgType key, @NonNull ArgType field) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(HStrlen, newArgsBuilder().add(key).add(field)));
        return getThis();
    }

    /**
     * Returns a random field name from the hash value stored at <code>key</code>.
     *
     * @since Redis 6.2 and above.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/hrandfield/">redis.io</a> for details.
     * @param key The key of the hash.
     * @return Command Response - A random field name from the hash stored at <code>key</code>, or
     *     <code>null</code> when the key does not exist.
     */
    public <ArgType> T hrandfield(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(HRandField, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Retrieves up to <code>count</code> random field names from the hash value stored at <code>key
     * </code>.
     *
     * @since Redis 6.2 and above.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/hrandfield/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param count The number of field names to return.<br>
     *     If <code>count</code> is positive, returns unique elements.<br>
     *     If negative, allows for duplicates.
     * @return Command Response - An <code>array</code> of random field names from the hash stored at
     *     <code>key</code>, or an <code>empty array</code> when the key does not exist.
     */
    public <ArgType> T hrandfieldWithCount(@NonNull ArgType key, long count) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(HRandField, newArgsBuilder().add(key).add(count)));
        return getThis();
    }

    /**
     * Retrieves up to <code>count</code> random field names along with their values from the hash
     * value stored at <code>key</code>.
     *
     * @since Redis 6.2 and above.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/hrandfield/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param count The number of field names to return.<br>
     *     If <code>count</code> is positive, returns unique elements.<br>
     *     If negative, allows for duplicates.
     * @return Command Response - A 2D <code>array</code> of <code>[fieldName, value]</code> <code>
     *     arrays</code>, where <code>fieldName</code> is a random field name from the hash and <code>
     *     value</code> is the associated value of the field name.<br>
     *     If the hash does not exist or is empty, the response will be an empty <code>array</code>.
     */
    public <ArgType> T hrandfieldWithCountWithValues(@NonNull ArgType key, long count) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(HRandField, newArgsBuilder().add(key).add(count).add(WITH_VALUES_REDIS_API)));
        return getThis();
    }

    /**
     * Inserts all the specified values at the head of the list stored at <code>key</code>. <code>
     * elements</code> are inserted one after the other to the head of the list, from the leftmost
     * element to the rightmost element. If <code>key</code> does not exist, it is created as an empty
     * list before performing the push operations.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/lpush/">redis.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to insert at the head of the list stored at <code>key</code>.
     * @return Command Response - The length of the list after the push operations.
     */
    public <ArgType> T lpush(@NonNull ArgType key, @NonNull ArgType[] elements) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(LPush, newArgsBuilder().add(key).add(elements)));
        return getThis();
    }

    /**
     * Removes and returns the first elements of the list stored at <code>key</code>. The command pops
     * a single element from the beginning of the list.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/lpop/">redis.io</a> for details.
     * @param key The key of the list.
     * @return Command Response - The value of the first element.<br>
     *     If <code>key</code> does not exist, null will be returned.
     */
    public <ArgType> T lpop(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(LPop, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Returns the index of the first occurrence of <code>element</code> inside the list specified by
     * <code>key</code>. If no match is found, <code>null</code> is returned.
     *
     * @since Redis 6.0.6.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/docs/latest/commands/lpos/">redis.io</a> for details.
     * @param key The name of the list.
     * @param element The value to search for within the list.
     * @return Command Response - The index of the first occurrence of <code>element</code>, or <code>
     *     null</code> if <code>element</code> is not in the list.
     */
    public <ArgType> T lpos(@NonNull ArgType key, @NonNull ArgType element) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(LPos, newArgsBuilder().add(key).add(element)));
        return getThis();
    }

    /**
     * Returns the index of an occurrence of <code>element</code> within a list based on the given
     * <code>options</code>. If no match is found, <code>null</code> is returned.
     *
     * @since Redis 6.0.6.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/docs/latest/commands/lpos/">redis.io</a> for details.
     * @param key The name of the list.
     * @param element The value to search for within the list.
     * @param options The LPos options.
     * @return Command Response - The index of <code>element</code>, or <code>null</code> if <code>
     *     element</code> is not in the list.
     */
    public <ArgType> T lpos(
            @NonNull ArgType key, @NonNull ArgType element, @NonNull LPosOptions options) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(LPos, newArgsBuilder().add(key).add(element).add(options.toArgs())));
        return getThis();
    }

    /**
     * Returns an <code>array</code> of indices of matching elements within a list.
     *
     * @since Redis 6.0.6.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/docs/latest/commands/lpos/">redis.io</a> for details.
     * @param key The name of the list.
     * @param element The value to search for within the list.
     * @param count The number of matches wanted.
     * @return Command Response - An <code>array</code> that holds the indices of the matching
     *     elements within the list.
     */
    public <ArgType> T lposCount(@NonNull ArgType key, @NonNull ArgType element, long count) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(LPos, newArgsBuilder().add(key).add(element).add(COUNT_REDIS_API).add(count)));
        return getThis();
    }

    /**
     * Returns an <code>array</code> of indices of matching elements within a list based on the given
     * <code>options</code>. If no match is found, an empty <code>array</code>is returned.
     *
     * @since Redis 6.0.6.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/docs/latest/commands/lpos/">redis.io</a> for details.
     * @param key The name of the list.
     * @param element The value to search for within the list.
     * @param count The number of matches wanted.
     * @param options The LPos options.
     * @return Command Response - An <code>array</code> that holds the indices of the matching
     *     elements within the list.
     */
    public <ArgType> T lposCount(
            @NonNull ArgType key, @NonNull ArgType element, long count, @NonNull LPosOptions options) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(
                        LPos,
                        newArgsBuilder()
                                .add(key)
                                .add(element)
                                .add(COUNT_REDIS_API)
                                .add(count)
                                .add(options.toArgs())));
        return getThis();
    }

    /**
     * Removes and returns up to <code>count</code> elements of the list stored at <code>key</code>,
     * depending on the list's length.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/lpop/">redis.io</a> for details.
     * @param key The key of the list.
     * @param count The count of the elements to pop from the list.
     * @return Command Response - An array of the popped elements will be returned depending on the
     *     list's length.<br>
     *     If <code>key</code> does not exist, null will be returned.
     */
    public <ArgType> T lpopCount(@NonNull ArgType key, long count) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(LPop, newArgsBuilder().add(key).add(count)));
        return getThis();
    }

    /**
     * Returns the specified elements of the list stored at <code>key</code>.<br>
     * The offsets <code>start</code> and <code>end</code> are zero-based indexes, with <code>0</code>
     * being the first element of the list, <code>1</code> being the next element and so on. These
     * offsets can also be negative numbers indicating offsets starting at the end of the list, with
     * <code>-1</code> being the last element of the list, <code>-2</code> being the penultimate, and
     * so on.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/lrange/">redis.io</a> for details.
     * @param key The key of the list.
     * @param start The starting point of the range.
     * @param end The end of the range.
     * @return Command Response - Array of elements in the specified range.<br>
     *     If <code>start</code> exceeds the end of the list, or if <code>start</code> is greater than
     *     <code>end</code>, an empty array will be returned.<br>
     *     If <code>end</code> exceeds the actual end of the list, the range will stop at the actual
     *     end of the list.<br>
     *     If <code>key</code> does not exist an empty array will be returned.
     */
    public <ArgType> T lrange(@NonNull ArgType key, long start, long end) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(LRange, newArgsBuilder().add(key).add(start).add(end)));
        return getThis();
    }

    /**
     * Returns the element at <code>index</code> from the list stored at <code>key</code>.<br>
     * The index is zero-based, so <code>0</code> means the first element, <code>1</code> the second
     * element and so on. Negative indices can be used to designate elements starting at the tail of
     * the list. Here, <code>-1</code> means the last element, <code>-2</code> means the penultimate
     * and so forth.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/lindex/">redis.io</a> for details.
     * @param key The key of the list.
     * @param index The index of the element in the list to retrieve.
     * @return Command Response - The element at <code>index</code> in the list stored at <code>key
     *     </code>.<br>
     *     If <code>index</code> is out of range or if <code>key</code> does not exist, <code>null
     *     </code> is returned.
     */
    public <ArgType> T lindex(@NonNull ArgType key, long index) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(LIndex, newArgsBuilder().add(key).add(index)));
        return getThis();
    }

    /**
     * Trims an existing list so that it will contain only the specified range of elements specified.
     * <br>
     * The offsets <code>start</code> and <code>end</code> are zero-based indexes, with <code>0</code>
     * being the first element of the list, <code>1</code> being the next element and so on.<br>
     * These offsets can also be negative numbers indicating offsets starting at the end of the list,
     * with <code>-1</code> being the last element of the list, <code>-2</code> being the penultimate,
     * and so on.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/ltrim/">redis.io</a> for details.
     * @param key The key of the list.
     * @param start The starting point of the range.
     * @param end The end of the range.
     * @return Command Response - Always <code>OK</code>.<br>
     *     If <code>start</code> exceeds the end of the list, or if <code>start</code> is greater than
     *     <code>end</code>, the result will be an empty list (which causes key to be removed).<br>
     *     If <code>end</code> exceeds the actual end of the list, it will be treated like the last
     *     element of the list.<br>
     *     If <code>key</code> does not exist, OK will be returned without changes to the database.
     */
    public <ArgType> T ltrim(@NonNull ArgType key, long start, long end) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(LTrim, newArgsBuilder().add(key).add(start).add(end)));
        return getThis();
    }

    /**
     * Returns the length of the list stored at <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/llen/">redis.io</a> for details.
     * @param key The key of the list.
     * @return Command Response - The length of the list at <code>key</code>.<br>
     *     If <code>key</code> does not exist, it is interpreted as an empty list and <code>0</code>
     *     is returned.
     */
    public <ArgType> T llen(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(LLen, newArgsBuilder().add(key)));
        return getThis();
    }

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
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/lrem/">redis.io</a> for details.
     * @param key The key of the list.
     * @param count The count of the occurrences of elements equal to <code>element</code> to remove.
     * @param element The element to remove from the list.
     * @return Command Response - The number of the removed elements.<br>
     *     If <code>key</code> does not exist, <code>0</code> is returned.
     */
    public <ArgType> T lrem(@NonNull ArgType key, long count, @NonNull ArgType element) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(LRem, newArgsBuilder().add(key).add(count).add(element)));
        return getThis();
    }

    /**
     * Inserts all the specified values at the tail of the list stored at <code>key</code>.<br>
     * <code>elements</code> are inserted one after the other to the tail of the list, from the
     * leftmost element to the rightmost element. If <code>key</code> does not exist, it is created as
     * an empty list before performing the push operations.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/rpush/">redis.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to insert at the tail of the list stored at <code>key</code>.
     * @return Command Response - The length of the list after the push operations.
     */
    public <ArgType> T rpush(@NonNull ArgType key, @NonNull ArgType[] elements) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(RPush, newArgsBuilder().add(key).add(elements)));
        return getThis();
    }

    /**
     * Removes and returns the last elements of the list stored at <code>key</code>.<br>
     * The command pops a single element from the end of the list.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/rpop/">redis.io</a> for details.
     * @param key The key of the list.
     * @return Command Response - The value of the last element.<br>
     *     If <code>key</code> does not exist, <code>null</code> will be returned.
     */
    public <ArgType> T rpop(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(RPop, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Removes and returns up to <code>count</code> elements from the list stored at <code>key</code>,
     * depending on the list's length.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/rpop/">redis.io</a> for details.
     * @param count The count of the elements to pop from the list.
     * @return Command Response - An array of popped elements will be returned depending on the list's
     *     length.<br>
     *     If <code>key</code> does not exist, <code>null</code> will be returned.
     */
    public <ArgType> T rpopCount(@NonNull ArgType key, long count) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(RPop, newArgsBuilder().add(key).add(count)));
        return getThis();
    }

    /**
     * Adds specified members to the set stored at <code>key</code>. Specified members that are
     * already a member of this set are ignored.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/sadd/">redis.io</a> for details.
     * @param key The <code>key</code> where members will be added to its set.
     * @param members A list of members to add to the set stored at <code>key</code>.
     * @return Command Response - The number of members that were added to the set, excluding members
     *     already present.
     * @remarks If <code>key</code> does not exist, a new set is created before adding <code>members
     *     </code>.
     */
    public <ArgType> T sadd(@NonNull ArgType key, @NonNull ArgType[] members) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(SAdd, newArgsBuilder().add(key).add(members)));
        return getThis();
    }

    /**
     * Returns if <code>member</code> is a member of the set stored at <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/sismember/">redis.io</a> for details.
     * @param key The key of the set.
     * @param member The member to check for existence in the set.
     * @return Command Response - <code>true</code> if the member exists in the set, <code>false
     *     </code> otherwise. If <code>key</code> doesn't exist, it is treated as an <code>empty set
     *     </code> and the command returns <code>false</code>.
     */
    public <ArgType> T sismember(@NonNull ArgType key, @NonNull ArgType member) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(SIsMember, newArgsBuilder().add(key).add(member)));
        return getThis();
    }

    /**
     * Removes specified members from the set stored at <code>key</code>. Specified members that are
     * not a member of this set are ignored.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/srem/">redis.io</a> for details.
     * @param key The <code>key</code> from which members will be removed.
     * @param members A list of members to remove from the set stored at <code>key</code>.
     * @return Command Response - The number of members that were removed from the set, excluding
     *     non-existing members.
     * @remarks If <code>key</code> does not exist, it is treated as an empty set and this command
     *     returns <code>0</code>.
     */
    public <ArgType> T srem(@NonNull ArgType key, @NonNull ArgType[] members) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(SRem, newArgsBuilder().add(key).add(members)));
        return getThis();
    }

    /**
     * Retrieves all the members of the set value stored at <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/smembers/">redis.io</a> for details.
     * @param key The key from which to retrieve the set members.
     * @return Command Response - A <code>Set</code> of all members of the set.
     * @remarks If <code>key</code> does not exist an empty set will be returned.
     */
    public <ArgType> T smembers(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(SMembers, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Retrieves the set cardinality (number of elements) of the set stored at <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/scard/">redis.io</a> for details.
     * @param key The key from which to retrieve the number of set members.
     * @return Command Response - The cardinality (number of elements) of the set, or 0 if the key
     *     does not exist.
     */
    public <ArgType> T scard(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(SCard, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Computes the difference between the first set and all the successive sets in <code>keys</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/sdiff/">redis.io</a> for details.
     * @param keys The keys of the sets to diff.
     * @return Command Response - A <code>Set</code> of elements representing the difference between
     *     the sets.<br>
     *     If the a <code>key</code> does not exist, it is treated as an empty set.
     */
    public <ArgType> T sdiff(@NonNull ArgType[] keys) {
        checkTypeOrThrow(keys);
        protobufTransaction.addCommands(buildCommand(SDiff, newArgsBuilder().add(keys)));
        return getThis();
    }

    /**
     * Checks whether each member is contained in the members of the set stored at <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/smismember/">redis.io</a> for details.
     * @param key The key of the set to check.
     * @param members A list of members to check for existence in the set.
     * @return Command Response - An <code>array</code> of <code>Boolean</code> values, each
     *     indicating if the respective member exists in the set.
     */
    public <ArgType> T smismember(@NonNull ArgType key, @NonNull ArgType[] members) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(SMIsMember, newArgsBuilder().add(key).add(members)));
        return getThis();
    }

    /**
     * Stores the difference between the first set and all the successive sets in <code>keys</code>
     * into a new set at <code>destination</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/sdiffstore/">redis.io</a> for details.
     * @param destination The key of the destination set.
     * @param keys The keys of the sets to diff.
     * @return Command Response - The number of elements in the resulting set.
     */
    public <ArgType> T sdiffstore(@NonNull ArgType destination, @NonNull ArgType[] keys) {
        checkTypeOrThrow(destination);
        protobufTransaction.addCommands(
                buildCommand(SDiffStore, newArgsBuilder().add(destination).add(keys)));
        return getThis();
    }

    /**
     * Moves <code>member</code> from the set at <code>source</code> to the set at <code>destination
     * </code>, removing it from the source set. Creates a new destination set if needed. The
     * operation is atomic.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/smove/">redis.io</a> for details.
     * @param source The key of the set to remove the element from.
     * @param destination The key of the set to add the element to.
     * @param member The set element to move.
     * @return Command response - <code>true</code> on success, or <code>false</code> if the <code>
     *     source</code> set does not exist or the element is not a member of the source set.
     */
    public <ArgType> T smove(
            @NonNull ArgType source, @NonNull ArgType destination, @NonNull ArgType member) {
        checkTypeOrThrow(source);
        protobufTransaction.addCommands(
                buildCommand(SMove, newArgsBuilder().add(source).add(destination).add(member)));
        return getThis();
    }

    /**
     * Gets the intersection of all the given sets.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/sinter/">redis.io</a> for details.
     * @param keys The keys of the sets.
     * @return Command Response - A <code>Set</code> of members which are present in all given sets.
     *     <br>
     *     Missing or empty input sets cause an empty response.
     */
    public <ArgType> T sinter(@NonNull ArgType[] keys) {
        checkTypeOrThrow(keys);
        protobufTransaction.addCommands(buildCommand(SInter, newArgsBuilder().add(keys)));
        return getThis();
    }

    /**
     * Stores the members of the intersection of all given sets specified by <code>keys</code> into a
     * new set at <code>destination</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/sinterstore/">redis.io</a> for details.
     * @param destination The key of the destination set.
     * @param keys The keys from which to retrieve the set members.
     * @return Command Response - The number of elements in the resulting set.
     */
    public <ArgType> T sinterstore(@NonNull ArgType destination, @NonNull ArgType[] keys) {
        checkTypeOrThrow(destination);
        protobufTransaction.addCommands(
                buildCommand(SInterStore, newArgsBuilder().add(destination).add(keys)));
        return getThis();
    }

    /**
     * Gets the cardinality of the intersection of all the given sets.
     *
     * @since Redis 7.0 and above.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/sintercard/">redis.io</a> for details.
     * @param keys The keys of the sets.
     * @return Command Response - The cardinality of the intersection result. If one or more sets do
     *     not exist, <code>0</code> is returned.
     */
    public <ArgType> T sintercard(@NonNull ArgType[] keys) {
        checkTypeOrThrow(keys);
        protobufTransaction.addCommands(
                buildCommand(SInterCard, newArgsBuilder().add(keys.length).add(keys)));
        return getThis();
    }

    /**
     * Gets the cardinality of the intersection of all the given sets.
     *
     * @since Redis 7.0 and above.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/sintercard/">redis.io</a> for details.
     * @param keys The keys of the sets.
     * @param limit The limit for the intersection cardinality value.
     * @return Command Response - The cardinality of the intersection result. If one or more sets do
     *     not exist, <code>0</code> is returned. If the intersection cardinality reaches <code>limit
     *     </code> partway through the computation, returns <code>limit</code> as the cardinality.
     */
    public <ArgType> T sintercard(@NonNull ArgType[] keys, long limit) {
        checkTypeOrThrow(keys);
        protobufTransaction.addCommands(
                buildCommand(
                        SInterCard,
                        newArgsBuilder().add(keys.length).add(keys).add(SET_LIMIT_REDIS_API).add(limit)));
        return getThis();
    }

    /**
     * Stores the members of the union of all given sets specified by <code>keys</code> into a new set
     * at <code>destination</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/sunionstore/">redis.io</a> for details.
     * @param destination The key of the destination set.
     * @param keys The keys from which to retrieve the set members.
     * @return Command Response - The number of elements in the resulting set.
     */
    public <ArgType> T sunionstore(@NonNull ArgType destination, @NonNull ArgType[] keys) {
        checkTypeOrThrow(destination);
        protobufTransaction.addCommands(
                buildCommand(SUnionStore, newArgsBuilder().add(destination).add(keys)));
        return getThis();
    }

    /**
     * Reads the configuration parameters of a running Redis server.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/config-get/">redis.io</a> for details.
     * @param parameters An <code>array</code> of configuration parameter names to retrieve values
     *     for.
     * @return Command response - A <code>map</code> of values corresponding to the configuration
     *     parameters.
     */
    public <ArgType> T configGet(@NonNull ArgType[] parameters) {
        checkTypeOrThrow(parameters);
        protobufTransaction.addCommands(buildCommand(ConfigGet, newArgsBuilder().add(parameters)));
        return getThis();
    }

    /**
     * Sets configuration parameters to the specified values.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/config-set/">redis.io</a> for details.
     * @param parameters A <code>map</code> consisting of configuration parameters and their
     *     respective values to set.
     * @return Command response - <code>OK</code> if all configurations have been successfully set.
     *     Otherwise, the transaction fails with an error.
     */
    public T configSet(@NonNull Map<?, ?> parameters) {
        protobufTransaction.addCommands(
                buildCommand(ConfigSet, newArgsBuilder().add(flattenMapToGlideStringArray(parameters))));
        return getThis();
    }

    /**
     * Returns the number of keys in <code>keys</code> that exist in the database.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/exists/">redis.io</a> for details.
     * @param keys The keys list to check.
     * @return Command Response - The number of keys that exist. If the same existing key is mentioned
     *     in <code>keys</code> multiple times, it will be counted multiple times.
     */
    public <ArgType> T exists(@NonNull ArgType[] keys) {
        checkTypeOrThrow(keys);
        protobufTransaction.addCommands(buildCommand(Exists, newArgsBuilder().add(keys)));
        return getThis();
    }

    /**
     * Unlinks (deletes) multiple <code>keys</code> from the database. A key is ignored if it does not
     * exist. This command, similar to DEL, removes specified keys and ignores non-existent ones.
     * However, this command does not block the server, while <a
     * href="https://redis.io/commands/del/">DEL</a> does.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/unlink/">redis.io</a> for details.
     * @param keys The list of keys to unlink.
     * @return Command Response - The number of <code>keys</code> that were unlinked.
     */
    public <ArgType> T unlink(@NonNull ArgType[] keys) {
        checkTypeOrThrow(keys);
        protobufTransaction.addCommands(buildCommand(Unlink, newArgsBuilder().add(keys)));
        return getThis();
    }

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
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/expire/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param seconds The timeout in seconds.
     * @return Command response - <code>true</code> if the timeout was set. <code>false</code> if the
     *     timeout was not set. e.g. key doesn't exist.
     */
    public <ArgType> T expire(@NonNull ArgType key, long seconds) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(Expire, newArgsBuilder().add(key).add(seconds)));
        return getThis();
    }

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
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/expire/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param seconds The timeout in seconds.
     * @param expireOptions The expire options.
     * @return Command response - <code>true</code> if the timeout was set. <code>false</code> if the
     *     timeout was not set. e.g. <code>key</code> doesn't exist, or operation skipped due to the
     *     provided arguments.
     */
    public <ArgType> T expire(
            @NonNull ArgType key, long seconds, @NonNull ExpireOptions expireOptions) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(Expire, newArgsBuilder().add(key).add(seconds).add(expireOptions.toArgs())));
        return getThis();
    }

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
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/expireat/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param unixSeconds The timeout in an absolute Unix timestamp.
     * @return Command response - <code>true</code> if the timeout was set. <code>false</code> if the
     *     timeout was not set. e.g. <code>key</code> doesn't exist.
     */
    public <ArgType> T expireAt(@NonNull ArgType key, long unixSeconds) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(ExpireAt, newArgsBuilder().add(key).add(unixSeconds)));
        return getThis();
    }

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
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/expireat/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param unixSeconds The timeout in an absolute Unix timestamp.
     * @param expireOptions The expire options.
     * @return Command response - <code>true</code> if the timeout was set. <code>false</code> if the
     *     timeout was not set. e.g. <code>key</code> doesn't exist, or operation skipped due to the
     *     provided arguments.
     */
    public <ArgType> T expireAt(
            @NonNull ArgType key, long unixSeconds, @NonNull ExpireOptions expireOptions) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(
                        ExpireAt, newArgsBuilder().add(key).add(unixSeconds).add(expireOptions.toArgs())));
        return getThis();
    }

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
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/pexpire/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param milliseconds The timeout in milliseconds.
     * @return Command response - <code>true</code> if the timeout was set. <code>false</code> if the
     *     timeout was not set. e.g. <code>key</code> doesn't exist.
     */
    public <ArgType> T pexpire(@NonNull ArgType key, long milliseconds) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(PExpire, newArgsBuilder().add(key).add(milliseconds)));
        return getThis();
    }

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
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/pexpire/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param milliseconds The timeout in milliseconds.
     * @param expireOptions The expire options.
     * @return Command response - <code>true</code> if the timeout was set. <code>false</code> if the
     *     timeout was not set. e.g. <code>key</code> doesn't exist, or operation skipped due to the
     *     provided arguments.
     */
    public <ArgType> T pexpire(
            @NonNull ArgType key, long milliseconds, @NonNull ExpireOptions expireOptions) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(
                        PExpire, newArgsBuilder().add(key).add(milliseconds).add(expireOptions.toArgs())));
        return getThis();
    }

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
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/pexpireat/">redis.io</a> for details.
     * @param key The <code>key</code> to set timeout on it.
     * @param unixMilliseconds The timeout in an absolute Unix timestamp.
     * @return Command response - <code>true</code> if the timeout was set. <code>false</code> if the
     *     timeout was not set. e.g. <code>key</code> doesn't exist.
     */
    public <ArgType> T pexpireAt(@NonNull ArgType key, long unixMilliseconds) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(PExpireAt, newArgsBuilder().add(key).add(unixMilliseconds)));
        return getThis();
    }

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
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/pexpireat/">redis.io</a> for details.
     * @param key The <code>key</code> to set timeout on it.
     * @param unixMilliseconds The timeout in an absolute Unix timestamp.
     * @param expireOptions The expiration option.
     * @return Command response - <code>true</code> if the timeout was set. <code>false</code> if the
     *     timeout was not set. e.g. <code>key</code> doesn't exist, or operation skipped due to the
     *     provided arguments.
     */
    public <ArgType> T pexpireAt(
            @NonNull ArgType key, long unixMilliseconds, @NonNull ExpireOptions expireOptions) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(
                        PExpireAt,
                        newArgsBuilder().add(key).add(unixMilliseconds).add(expireOptions.toArgs())));
        return getThis();
    }

    /**
     * Returns the remaining time to live of <code>key</code> that has a timeout.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/ttl/">redis.io</a> for details.
     * @param key The <code>key</code> to return its timeout.
     * @return Command response - TTL in seconds, <code>-2</code> if <code>key</code> does not exist,
     *     or <code>-1</code> if <code>key</code> exists but has no associated expire.
     */
    public <ArgType> T ttl(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(TTL, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Returns the absolute Unix timestamp (since January 1, 1970) at which the given <code>key</code>
     * will expire, in seconds.<br>
     * To get the expiration with millisecond precision, use {@link #pexpiretime(String)}.
     *
     * @since Redis 7.0 and above.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/expiretime/">redis.io</a> for details.
     * @param key The <code>key</code> to determine the expiration value of.
     * @return Command response - The expiration Unix timestamp in seconds, <code>-2</code> if <code>
     *     key</code> does not exist, or <code>-1</code> if <code>key</code> exists but has no
     *     associated expiration.
     */
    public <ArgType> T expiretime(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(ExpireTime, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Returns the absolute Unix timestamp (since January 1, 1970) at which the given <code>key</code>
     * will expire, in milliseconds.
     *
     * @since Redis 7.0 and above.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/pexpiretime/">redis.io</a> for details.
     * @param key The <code>key</code> to determine the expiration value of.
     * @return Command response - The expiration Unix timestamp in milliseconds, <code>-2</code> if
     *     <code>key
     *     </code> does not exist, or <code>-1</code> if <code>key</code> exists but has no associated
     *     expiration.
     */
    public <ArgType> T pexpiretime(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(PExpireTime, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Gets the current connection id.
     *
     * @see <a href="https://redis.io/commands/client-id/">redis.io</a> for details.
     * @return Command response - The id of the client.
     */
    public T clientId() {
        protobufTransaction.addCommands(buildCommand(ClientId));
        return getThis();
    }

    /**
     * Gets the name of the current connection.
     *
     * @see <a href="https://redis.io/commands/client-getname/">redis.io</a> for details.
     * @return Command response - The name of the client connection as a string if a name is set, or
     *     <code>null</code> if no name is assigned.
     */
    public T clientGetName() {
        protobufTransaction.addCommands(buildCommand(ClientGetName));
        return getThis();
    }

    /**
     * Rewrites the configuration file with the current configuration.
     *
     * @see <a href="https://redis.io/commands/config-rewrite/">redis.io</a> for details.
     * @return <code>OK</code> is returned when the configuration was rewritten properly. Otherwise,
     *     the transaction fails with an error.
     */
    public T configRewrite() {
        protobufTransaction.addCommands(buildCommand(ConfigRewrite));
        return getThis();
    }

    /**
     * Resets the statistics reported by Redis using the <a
     * href="https://redis.io/commands/info/">INFO</a> and <a
     * href="https://redis.io/commands/latency-histogram/">LATENCY HISTOGRAM</a> commands.
     *
     * @see <a href="https://redis.io/commands/config-resetstat/">redis.io</a> for details.
     * @return <code>OK</code> to confirm that the statistics were successfully reset.
     */
    public T configResetStat() {
        protobufTransaction.addCommands(buildCommand(ConfigResetStat));
        return getThis();
    }

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A <code>Map</code> of members to their corresponding scores.
     * @param options The ZAdd options.
     * @param changed Modify the return value from the number of new elements added, to the total
     *     number of elements changed.
     * @return Command Response - The number of elements added to the sorted set. <br>
     *     If <code>changed</code> is set, returns the number of elements updated in the sorted set.
     */
    public <ArgType> T zadd(
            @NonNull ArgType key,
            @NonNull Map<ArgType, Double> membersScoresMap,
            @NonNull ZAddOptions options,
            boolean changed) {
        checkTypeOrThrow(key);
        ArgsBuilder args = new ArgsBuilder();
        args.add(key).add(options.toArgs());
        if (changed) {
            args.add("CH");
        }
        args.add(flattenMapToGlideStringArrayValueFirst(membersScoresMap));
        protobufTransaction.addCommands(buildCommand(ZAdd, args));
        return getThis();
    }

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A <code>Map</code> of members to their corresponding scores.
     * @param options The ZAdd options.
     * @return Command Response - The number of elements added to the sorted set.
     */
    public <ArgType> T zadd(
            @NonNull ArgType key,
            @NonNull Map<ArgType, Double> membersScoresMap,
            @NonNull ZAddOptions options) {
        return zadd(key, membersScoresMap, options, false);
    }

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A <code>Map</code> of members to their corresponding scores.
     * @param changed Modify the return value from the number of new elements added, to the total
     *     number of elements changed.
     * @return Command Response - The number of elements added to the sorted set. <br>
     *     If <code>changed</code> is set, returns the number of elements updated in the sorted set.
     */
    public <ArgType> T zadd(
            @NonNull ArgType key, @NonNull Map<ArgType, Double> membersScoresMap, boolean changed) {
        return zadd(key, membersScoresMap, ZAddOptions.builder().build(), changed);
    }

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A <code>Map</code> of members to their corresponding scores.
     * @return Command Response - The number of elements added to the sorted set.
     */
    public <ArgType> T zadd(@NonNull ArgType key, @NonNull Map<ArgType, Double> membersScoresMap) {
        return zadd(key, membersScoresMap, ZAddOptions.builder().build(), false);
    }

    /**
     * Increments the score of member in the sorted set stored at <code>key</code> by <code>increment
     * </code>.<br>
     * If <code>member</code> does not exist in the sorted set, it is added with <code>
     * increment</code> as its score (as if its previous score was 0.0).<br>
     * If <code>key</code> does not exist, a new sorted set with the specified member as its sole
     * member is created.<br>
     * <code>zaddIncr</code> with empty option acts as {@link #zincrby(String, double, String)}.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member A member in the sorted set to increment.
     * @param increment The score to increment the member.
     * @param options The ZAdd options.
     * @return Command Response - The score of the member.<br>
     *     If there was a conflict with the options, the operation aborts and <code>null</code> is
     *     returned.
     */
    public <ArgType> T zaddIncr(
            @NonNull ArgType key,
            @NonNull ArgType member,
            double increment,
            @NonNull ZAddOptions options) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(
                        ZAdd,
                        newArgsBuilder()
                                .add(key)
                                .add(options.toArgs())
                                .add("INCR")
                                .add(increment)
                                .add(member)));
        return getThis();
    }

    /**
     * Increments the score of member in the sorted set stored at <code>key</code> by <code>increment
     * </code>.<br>
     * If <code>member</code> does not exist in the sorted set, it is added with <code>
     * increment</code> as its score (as if its previous score was 0.0).<br>
     * If <code>key</code> does not exist, a new sorted set with the specified member as its sole
     * member is created.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member A member in the sorted set to increment.
     * @param increment The score to increment the member.
     * @return Command Response - The score of the member.
     */
    public <ArgType> T zaddIncr(@NonNull ArgType key, @NonNull ArgType member, double increment) {
        return zaddIncr(key, member, increment, ZAddOptions.builder().build());
    }

    /**
     * Removes the specified members from the sorted set stored at <code>key</code>.<br>
     * Specified members that are not a member of this set are ignored.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zrem/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param members An array of members to remove from the sorted set.
     * @return Command Response - The number of members that were removed from the sorted set, not
     *     including non-existing members.<br>
     *     If <code>key</code> does not exist, it is treated as an empty sorted set, and this command
     *     returns <code>0</code>.
     */
    public <ArgType> T zrem(@NonNull ArgType key, @NonNull ArgType[] members) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(ZRem, newArgsBuilder().add(key).add(members)));
        return getThis();
    }

    /**
     * Returns the cardinality (number of elements) of the sorted set stored at <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zcard/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @return Command Response - The number of elements in the sorted set.<br>
     *     If <code>key</code> does not exist, it is treated as an empty sorted set, and this command
     *     return <code>0</code>.
     */
    public <ArgType> T zcard(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(ZCard, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Removes and returns up to <code>count</code> members with the lowest scores from the sorted set
     * stored at the specified <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zpopmin/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param count Specifies the quantity of members to pop.<br>
     *     If <code>count</code> is higher than the sorted set's cardinality, returns all members and
     *     their scores, ordered from lowest to highest.
     * @return Command Response - A map of the removed members and their scores, ordered from the one
     *     with the lowest score to the one with the highest.<br>
     *     If <code>key</code> doesn't exist, it will be treated as an empty sorted set and the
     *     command returns an empty <code>Map</code>.
     */
    public <ArgType> T zpopmin(@NonNull ArgType key, long count) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(ZPopMin, newArgsBuilder().add(key).add(count)));
        return getThis();
    }

    /**
     * Removes and returns the member with the lowest score from the sorted set stored at the
     * specified <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zpopmin/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @return Command Response - A map containing the removed member and its corresponding score.<br>
     *     If <code>key</code> doesn't exist, it will be treated as an empty sorted set and the
     *     command returns an empty <code>Map</code>.
     */
    public <ArgType> T zpopmin(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(ZPopMin, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Returns a random element from the sorted set stored at <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zrandmember/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @return Command Response - A <code>String</code> representing a random element from the sorted
     *     set.<br>
     *     If the sorted set does not exist or is empty, the response will be <code>null</code>.
     */
    public <ArgType> T zrandmember(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(ZRandMember, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Retrieves random elements from the sorted set stored at <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zrandmember/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param count The number of elements to return.<br>
     *     If <code>count</code> is positive, returns unique elements.<br>
     *     If negative, allows for duplicates.<br>
     * @return Command Response - An <code>array</code> of elements from the sorted set.<br>
     *     If the sorted set does not exist or is empty, the response will be an empty <code>array
     *     </code>.
     */
    public <ArgType> T zrandmemberWithCount(@NonNull ArgType key, long count) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(ZRandMember, newArgsBuilder().add(key).add(count)));
        return getThis();
    }

    /**
     * Retrieves random elements along with their scores from the sorted set stored at <code>key
     * </code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zrandmember/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param count The number of elements to return.<br>
     *     If <code>count</code> is positive, returns unique elements.<br>
     *     If negative, allows duplicates.<br>
     * @return Command Response - An <code>array</code> of <code>[element, score]</code> <code>arrays
     *     </code>, where element is a <code>String</code> and score is a <code>Double</code>.<br>
     *     If the sorted set does not exist or is empty, the response will be an empty <code>array
     *     </code>.
     */
    public <ArgType> T zrandmemberWithCountWithScores(ArgType key, long count) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(ZRandMember, newArgsBuilder().add(key).add(count).add(WITH_SCORES_REDIS_API)));
        return getThis();
    }

    /**
     * Increments the score of <code>member</code> in the sorted set stored at <code>key</code> by
     * <code>increment</code>.<br>
     * If <code>member</code> does not exist in the sorted set, it is added with <code>increment
     * </code> as its score. If <code>key</code> does not exist, a new sorted set with the specified
     * member as its sole member is created.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zincrby/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param increment The score increment.
     * @param member A member of the sorted set.
     * @return Command Response - The new score of <code>member</code>.
     */
    public <ArgType> T zincrby(@NonNull ArgType key, double increment, @NonNull ArgType member) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(ZIncrBy, newArgsBuilder().add(key).add(increment).add(member)));
        return getThis();
    }

    /**
     * Blocks the connection until it removes and returns a member with the lowest score from the
     * sorted sets stored at the specified <code>keys</code>. The sorted sets are checked in the order
     * they are provided.<br>
     * <code>BZPOPMIN</code> is the blocking variant of {@link #zpopmin(String)}.<br>
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/bzpopmin/">redis.io</a> for more details.
     * @apiNote <code>BZPOPMIN</code> is a client blocking command, see <a
     *     href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *     Commands</a> for more details and best practices.
     * @param keys The keys of the sorted sets.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @return Command Response - An <code>array</code> containing the key where the member was popped
     *     out, the member itself, and the member score.<br>
     *     If no member could be popped and the <code>timeout</code> expired, returns <code>null
     *     </code>.
     */
    public <ArgType> T bzpopmin(@NonNull ArgType[] keys, double timeout) {
        checkTypeOrThrow(keys);
        protobufTransaction.addCommands(
                buildCommand(BZPopMin, newArgsBuilder().add(keys).add(timeout)));
        return getThis();
    }

    /**
     * Removes and returns up to <code>count</code> members with the highest scores from the sorted
     * set stored at the specified <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zpopmax/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param count Specifies the quantity of members to pop.<br>
     *     If <code>count</code> is higher than the sorted set's cardinality, returns all members and
     *     their scores, ordered from highest to lowest.
     * @return Command Response - A map of the removed members and their scores, ordered from the one
     *     with the highest score to the one with the lowest.<br>
     *     If <code>key</code> doesn't exist, it will be treated as an empty sorted set and the
     *     command returns an empty <code>Map</code>.
     */
    public <ArgType> T zpopmax(@NonNull ArgType key, long count) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(ZPopMax, newArgsBuilder().add(key).add(count)));
        return getThis();
    }

    /**
     * Removes and returns the member with the highest score from the sorted set stored at the
     * specified <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zpopmax/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @return Command Response - A map containing the removed member and its corresponding score.<br>
     *     If <code>key</code> doesn't exist, it will be treated as an empty sorted set and the
     *     command returns an empty <code>Map</code>.
     */
    public <ArgType> T zpopmax(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(ZPopMax, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Blocks the connection until it removes and returns a member with the highest score from the
     * sorted sets stored at the specified <code>keys</code>. The sorted sets are checked in the order
     * they are provided.<br>
     * <code>BZPOPMAX</code> is the blocking variant of {@link #zpopmax(String)}.<br>
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/bzpopmax/">redis.io</a> for more details.
     * @apiNote <code>BZPOPMAX</code> is a client blocking command, see <a
     *     href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *     Commands</a> for more details and best practices.
     * @param keys The keys of the sorted sets.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @return Command Response - An <code>array</code> containing the key where the member was popped
     *     out, the member itself, and the member score.<br>
     *     If no member could be popped and the <code>timeout</code> expired, returns <code>null
     *     </code>.
     */
    public <ArgType> T bzpopmax(@NonNull ArgType[] keys, double timeout) {
        checkTypeOrThrow(keys);
        protobufTransaction.addCommands(
                buildCommand(BZPopMax, newArgsBuilder().add(keys).add(timeout)));
        return getThis();
    }

    /**
     * Returns the score of <code>member</code> in the sorted set stored at <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zscore/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member The member whose score is to be retrieved.
     * @return Command Response - The score of the member.<br>
     *     If <code>member</code> does not exist in the sorted set, <code>null</code> is returned.<br>
     *     If <code>key</code> does not exist, <code>null</code> is returned.
     */
    public <ArgType> T zscore(@NonNull ArgType key, @NonNull ArgType member) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(ZScore, newArgsBuilder().add(key).add(member)));
        return getThis();
    }

    /**
     * Returns the rank of <code>member</code> in the sorted set stored at <code>key</code>, with
     * scores ordered from low to high, starting from <code>0</code>.<br>
     * To get the rank of <code>member</code> with its score, see {@link #zrankWithScore}.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zrank/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member The member whose rank is to be retrieved.
     * @return The rank of <code>member</code> in the sorted set.<br>
     *     If <code>key</code> doesn't exist, or if <code>member</code> is not present in the set,
     *     <code>null</code> will be returned.
     */
    public <ArgType> T zrank(@NonNull ArgType key, @NonNull ArgType member) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(ZRank, newArgsBuilder().add(key).add(member)));
        return getThis();
    }

    /**
     * Returns the rank of <code>member</code> in the sorted set stored at <code>key</code> with its
     * score, where scores are ordered from the lowest to highest, starting from <code>0</code>.<br>
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zrank/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member The member whose rank is to be retrieved.
     * @return An array containing the rank (as <code>Long</code>) and score (as <code>Double</code>)
     *     of <code>member</code> in the sorted set.<br>
     *     If <code>key</code> doesn't exist, or if <code>member</code> is not present in the set,
     *     <code>null</code> will be returned.
     */
    public <ArgType> T zrankWithScore(@NonNull ArgType key, @NonNull ArgType member) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(ZRank, newArgsBuilder().add(key).add(member).add(WITH_SCORE_REDIS_API)));
        return getThis();
    }

    /**
     * Returns the rank of <code>member</code> in the sorted set stored at <code>key</code>, where
     * scores are ordered from the highest to lowest, starting from <code>0</code>.<br>
     * To get the rank of <code>member</code> with its score, see {@link #zrevrankWithScore}.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zrevrank/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member The member whose rank is to be retrieved.
     * @return Command Response - The rank of <code>member</code> in the sorted set, where ranks are
     *     ordered from high to low based on scores.<br>
     *     If <code>key</code> doesn't exist, or if <code>member</code> is not present in the set,
     *     <code>null</code> will be returned.
     */
    public <ArgType> T zrevrank(@NonNull ArgType key, @NonNull ArgType member) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(ZRevRank, newArgsBuilder().add(key).add(member)));
        return getThis();
    }

    /**
     * Returns the rank of <code>member</code> in the sorted set stored at <code>key</code> with its
     * score, where scores are ordered from the highest to lowest, starting from <code>0</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zrevrank/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member The member whose rank is to be retrieved.
     * @return Command Response - An array containing the rank (as <code>Long</code>) and score (as
     *     <code>Double</code>) of <code>member</code> in the sorted set, where ranks are ordered from
     *     high to low based on scores.<br>
     *     If <code>key</code> doesn't exist, or if <code>member</code> is not present in the set,
     *     <code>null</code> will be returned.
     */
    public <ArgType> T zrevrankWithScore(@NonNull ArgType key, @NonNull ArgType member) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(ZRevRank, newArgsBuilder().add(key).add(member).add(WITH_SCORE_REDIS_API)));
        return getThis();
    }

    /**
     * Returns the scores associated with the specified <code>members</code> in the sorted set stored
     * at <code>key</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zmscore/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param members An array of members in the sorted set.
     * @return Command Response - An <code>Array</code> of scores of the <code>members</code>.<br>
     *     If a <code>member</code> does not exist, the corresponding value in the <code>Array</code>
     *     will be <code>null</code>.
     */
    public <ArgType> T zmscore(@NonNull ArgType key, @NonNull ArgType[] members) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(buildCommand(ZMScore, newArgsBuilder().add(key).add(members)));
        return getThis();
    }

    /**
     * Returns the difference between the first sorted set and all the successive sorted sets.<br>
     * To get the elements with their scores, see {@link #zdiffWithScores}.
     *
     * @since Redis 6.2 and above.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zdiff/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets.
     * @return Command Response - An <code>array</code> of elements representing the difference
     *     between the sorted sets. <br>
     *     If the first <code>key</code> does not exist, it is treated as an empty sorted set, and the
     *     command returns an empty <code>array</code>.
     */
    public <ArgType> T zdiff(@NonNull ArgType[] keys) {
        checkTypeOrThrow(keys);
        protobufTransaction.addCommands(
                buildCommand(ZDiff, newArgsBuilder().add(keys.length).add(keys)));
        return getThis();
    }

    /**
     * Returns the difference between the first sorted set and all the successive sorted sets.
     *
     * @since Redis 6.2 and above.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zdiff/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets.
     * @return Command Response - A <code>Map</code> of elements and their scores representing the
     *     difference between the sorted sets.<br>
     *     If the first <code>key</code> does not exist, it is treated as an empty sorted set, and the
     *     command returns an empty <code>Map</code>.
     */
    public <ArgType> T zdiffWithScores(@NonNull ArgType[] keys) {
        checkTypeOrThrow(keys);
        protobufTransaction.addCommands(
                buildCommand(
                        ZDiff, newArgsBuilder().add(keys.length).add(keys).add(WITH_SCORES_REDIS_API)));
        return getThis();
    }

    /**
     * Calculates the difference between the first sorted set and all the successive sorted sets at
     * <code>keys</code> and stores the difference as a sorted set to <code>destination</code>,
     * overwriting it if it already exists. Non-existent keys are treated as empty sets.
     *
     * @since Redis 6.2 and above.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zdiffstore/">redis.io</a> for more details.
     * @param destination The key for the resulting sorted set.
     * @param keys The keys of the sorted sets to compare.
     * @return Command Response - The number of members in the resulting sorted set stored at <code>
     *     destination</code>.
     */
    public <ArgType> T zdiffstore(@NonNull ArgType destination, @NonNull ArgType[] keys) {
        checkTypeOrThrow(destination);
        protobufTransaction.addCommands(
                buildCommand(ZDiffStore, newArgsBuilder().add(destination).add(keys.length).add(keys)));
        return getThis();
    }

    /**
     * Returns the number of members in the sorted set stored at <code>key</code> with scores between
     * <code>minScore</code> and <code>maxScore</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zcount/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param minScore The minimum score to count from. Can be an implementation of {@link
     *     InfScoreBound} representing positive/negative infinity, or {@link ScoreBoundary}
     *     representing a specific score and inclusivity.
     * @param maxScore The maximum score to count up to. Can be an implementation of {@link
     *     InfScoreBound} representing positive/negative infinity, or {@link ScoreBoundary}
     *     representing a specific score and inclusivity.
     * @return Command Response - The number of members in the specified score range.<br>
     *     If <code>key</code> does not exist, it is treated as an empty sorted set, and the command
     *     returns <code>0</code>.<br>
     *     If <code>maxScore < minScore</code>, <code>0</code> is returned.
     */
    public <ArgType> T zcount(
            @NonNull ArgType key, @NonNull ScoreRange minScore, @NonNull ScoreRange maxScore) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(
                        ZCount, newArgsBuilder().add(key).add(minScore.toArgs()).add(maxScore.toArgs())));
        return getThis();
    }

    /**
     * Removes all elements in the sorted set stored at <code>key</code> with rank between <code>start
     * </code> and <code>end</code>. Both <code>start</code> and <code>end</code> are zero-based
     * indexes with <code>0</code> being the element with the lowest score. These indexes can be
     * negative numbers, where they indicate offsets starting at the element with the highest score.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zremrangebyrank/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param start The starting point of the range.
     * @param end The end of the range.
     * @return Command Response - The number of elements removed.<br>
     *     If <code>start</code> exceeds the end of the sorted set, or if <code>start</code> is
     *     greater than <code>end</code>, <code>0</code> returned.<br>
     *     If <code>end</code> exceeds the actual end of the sorted set, the range will stop at the
     *     actual end of the sorted set.<br>
     *     If <code>key</code> does not exist <code>0</code> will be returned.
     */
    public <ArgType> T zremrangebyrank(@NonNull ArgType key, long start, long end) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(ZRemRangeByRank, newArgsBuilder().add(key).add(start).add(end)));
        return getThis();
    }

    /**
     * Stores a specified range of elements from the sorted set at <code>source</code>, into a new
     * sorted set at <code>destination</code>. If <code>destination</code> doesn't exist, a new sorted
     * set is created; if it exists, it's overwritten.<br>
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zrangestore/">redis.io</a> for more details.
     * @param destination The key for the destination sorted set.
     * @param source The key of the source sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by lexicographical order, use {@link RangeByLex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @param reverse If <code>true</code>, reverses the sorted set, with index <code>0</code> as the
     *     element with the highest score.
     * @return Command Response - The number of elements in the resulting sorted set.
     */
    public <ArgType> T zrangestore(
            @NonNull ArgType destination,
            @NonNull ArgType source,
            @NonNull RangeQuery rangeQuery,
            boolean reverse) {
        checkTypeOrThrow(destination);
        protobufTransaction.addCommands(
                buildCommand(
                        ZRangeStore,
                        newArgsBuilder()
                                .add(destination)
                                .add(source)
                                .add(RangeOptions.createZRangeBaseArgs(rangeQuery, reverse))));
        return getThis();
    }

    /**
     * Stores a specified range of elements from the sorted set at <code>source</code>, into a new
     * sorted set at <code>destination</code>. If <code>destination</code> doesn't exist, a new sorted
     * set is created; if it exists, it's overwritten.<br>
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zrangestore/">redis.io</a> for more details.
     * @param destination The key for the destination sorted set.
     * @param source The key of the source sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by lexicographical order, use {@link RangeByLex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @return Command Response - The number of elements in the resulting sorted set.
     */
    public <ArgType> T zrangestore(
            @NonNull ArgType destination, @NonNull ArgType source, @NonNull RangeQuery rangeQuery) {
        checkTypeOrThrow(destination);
        return getThis().zrangestore(destination, source, rangeQuery, false);
    }

    /**
     * Removes all elements in the sorted set stored at <code>key</code> with a lexicographical order
     * between <code>minLex</code> and <code>maxLex</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zremrangebylex/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param minLex The minimum bound of the lexicographical range. Can be an implementation of
     *     {@link InfLexBound} representing positive/negative infinity, or {@link LexBoundary}
     *     representing a specific lex and inclusivity.
     * @param maxLex The maximum bound of the lexicographical range. Can be an implementation of
     *     {@link InfLexBound} representing positive/negative infinity, or {@link LexBoundary}
     *     representing a specific lex and inclusivity.
     * @return Command Response - The number of members removed from the sorted set.<br>
     *     If <code>key</code> does not exist, it is treated as an empty sorted set, and the command
     *     returns <code>0</code>.<br>
     *     If <code>minLex</code> is greater than <code>maxLex</code>, <code>0</code> is returned.
     */
    public <ArgType> T zremrangebylex(
            @NonNull ArgType key, @NonNull LexRange minLex, @NonNull LexRange maxLex) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(
                        ZRemRangeByLex, newArgsBuilder().add(key).add(minLex.toArgs()).add(maxLex.toArgs())));
        return getThis();
    }

    /**
     * Removes all elements in the sorted set stored at <code>key</code> with a score between <code>
     * minScore</code> and <code>maxScore</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zremrangebyscore/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param minScore The minimum score to remove from. Can be an implementation of {@link
     *     InfScoreBound} representing positive/negative infinity, or {@link ScoreBoundary}
     *     representing a specific score and inclusivity.
     * @param maxScore The maximum score to remove to. Can be an implementation of {@link
     *     InfScoreBound} representing positive/negative infinity, or {@link ScoreBoundary}
     *     representing a specific score and inclusivity.
     * @return Command Response - The number of members removed.<br>
     *     If <code>key</code> does not exist, it is treated as an empty sorted set, and the command
     *     returns <code>0</code>.<br>
     *     If <code>minScore</code> is greater than <code>maxScore</code>, <code>0</code> is returned.
     */
    public <ArgType> T zremrangebyscore(
            @NonNull ArgType key, @NonNull ScoreRange minScore, @NonNull ScoreRange maxScore) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(
                        ZRemRangeByScore,
                        newArgsBuilder().add(key).add(minScore.toArgs()).add(maxScore.toArgs())));
        return getThis();
    }

    /**
     * Returns the number of members in the sorted set stored at <code>key</code> with scores between
     * <code>minLex</code> and <code>maxLex</code>.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zlexcount/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param minLex The minimum lex to count from. Can be an implementation of {@link InfLexBound}
     *     representing positive/negative infinity, or {@link LexBoundary} representing a specific lex
     *     and inclusivity.
     * @param maxLex The maximum lex to count up to. Can be an implementation of {@link InfLexBound}
     *     representing positive/negative infinity, or {@link LexBoundary} representing a specific lex
     *     and inclusivity.
     * @return Command Response - The number of members in the specified lex range.<br>
     *     If <code>key</code> does not exist, it is treated as an empty sorted set, and the command
     *     returns <code>0</code>.<br>
     *     If <code>maxLex < minLex</code>, <code>0</code> is returned.
     */
    public <ArgType> T zlexcount(
            @NonNull ArgType key, @NonNull LexRange minLex, @NonNull LexRange maxLex) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(
                        ZLexCount, newArgsBuilder().add(key).add(minLex.toArgs()).add(maxLex.toArgs())));
        return getThis();
    }

    /**
     * Computes the union of sorted sets given by the specified <code>KeysOrWeightedKeys</code>, and
     * stores the result in <code>destination</code>. If <code>destination</code> already exists, it
     * is overwritten. Otherwise, a new sorted set will be created.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zunionstore/">redis.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link WeightAggregateOptions.KeyArray} for keys only.
     *       <li>Use {@link WeightAggregateOptions.WeightedKeys} for weighted keys with score
     *           multipliers.
     *     </ul>
     *
     * @param aggregate Specifies the aggregation strategy to apply when combining the scores of
     *     elements.
     * @return Command Response - The number of elements in the resulting sorted set stored at <code>
     *     destination</code>.
     */
    public <ArgType> T zunionstore(
            @NonNull ArgType destination,
            @NonNull KeysOrWeightedKeys keysOrWeightedKeys,
            @NonNull Aggregate aggregate) {
        checkTypeOrThrow(destination);
        protobufTransaction.addCommands(
                buildCommand(
                        ZUnionStore,
                        newArgsBuilder()
                                .add(destination)
                                .add(keysOrWeightedKeys.toArgs())
                                .add(aggregate.toArgs())));
        return getThis();
    }

    /**
     * Computes the union of sorted sets given by the specified <code>KeysOrWeightedKeys</code>, and
     * stores the result in <code>destination</code>. If <code>destination</code> already exists, it
     * is overwritten. Otherwise, a new sorted set will be created.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zunionstore/">redis.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArray} for keys only.
     *       <li>Use {@link WeightedKeys} for weighted keys with score multipliers.
     *     </ul>
     *
     * @return Command Response - The number of elements in the resulting sorted set stored at <code>
     *     destination</code>.
     */
    public <ArgType> T zunionstore(
            @NonNull ArgType destination, @NonNull KeysOrWeightedKeys keysOrWeightedKeys) {
        checkTypeOrThrow(destination);
        protobufTransaction.addCommands(
                buildCommand(
                        ZUnionStore, newArgsBuilder().add(destination).add(keysOrWeightedKeys.toArgs())));
        return getThis();
    }

    /**
     * Computes the intersection of sorted sets given by the specified <code>keysOrWeightedKeys</code>
     * , and stores the result in <code>destination</code>. If <code>destination</code> already
     * exists, it is overwritten. Otherwise, a new sorted set will be created.
     *
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zinterstore/">redis.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link WeightAggregateOptions.KeyArray} for keys only.
     *       <li>Use {@link WeightAggregateOptions.WeightedKeys} for weighted keys with score
     *           multipliers.
     *     </ul>
     *
     * @param aggregate Specifies the aggregation strategy to apply when combining the scores of
     *     elements.
     * @return Command Response - The number of elements in the resulting sorted set stored at <code>
     *     destination</code>.
     */
    public <ArgType> T zinterstore(
            @NonNull ArgType destination,
            @NonNull KeysOrWeightedKeys keysOrWeightedKeys,
            @NonNull Aggregate aggregate) {
        checkTypeOrThrow(destination);
        protobufTransaction.addCommands(
                buildCommand(
                        ZInterStore,
                        newArgsBuilder()
                                .add(destination)
                                .add(keysOrWeightedKeys.toArgs())
                                .add(aggregate.toArgs())));
        return getThis();
    }

    /**
     * Returns the cardinality of the intersection of the sorted sets specified by <code>keys</code>.
     *
     * @since Redis 7.0 and above.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zintercard/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets to intersect.
     * @return Command Response - The cardinality of the intersection of the given sorted sets.
     */
    public <ArgType> T zintercard(@NonNull ArgType[] keys) {
        checkTypeOrThrow(keys);
        protobufTransaction.addCommands(
                buildCommand(ZInterCard, newArgsBuilder().add(keys.length).add(keys)));
        return getThis();
    }

    /**
     * Returns the cardinality of the intersection of the sorted sets specified by <code>keys</code>.
     * If the intersection cardinality reaches <code>limit</code> partway through the computation, the
     * algorithm will exit early and yield <code>limit</code> as the cardinality.
     *
     * @since Redis 7.0 and above.
     * @implNote ArgType is limited to String or GlideString, any other type will throw
     *     IllegalArgumentException
     * @see <a href="https://redis.io/commands/zintercard/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets to intersect.
     * @param limit Specifies a maximum number for the intersection cardinality. If limit is set to
     *     <code>0</code> the range will be unlimited.
     * @return Command Response - The cardinality of the intersection of the given sorted sets, or the
     *     <code>limit</code> if reached.
     */
    public <ArgType> T zintercard(@NonNull ArgType[] keys, long limit) {
        checkTypeOrThrow(keys);
        protobufTransaction.addCommands(
                buildCommand(
                        ZInterCard,
                        newArgsBuilder().add(keys.length).add(keys).add(LIMIT_REDIS_API).add(limit)));
        return getThis();
    }

    /**
     * Computes the intersection of sorted sets given by the specified <code>KeysOrWeightedKeys</code>
     * , and stores the result in <code>destination</code>. If <code>destination</code> already
     * exists, it is overwritten. Otherwise, a new sorted set will be created.<br>
     * To perform a <code>zinterstore</code> operation while specifying aggregation settings, use
     * {@link #zinterstore(String, KeysOrWeightedKeys, Aggregate)}
     *
     * @see <a href="https://redis.io/commands/zinterstore/">redis.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArray} for keys only.
     *       <li>Use {@link WeightedKeys} for weighted keys with score multipliers.
     *     </ul>
     *
     * @return Command Response - The number of elements in the resulting sorted set stored at <code>
     *     destination</code>.
     */
    public T zinterstore(
            @NonNull String destination, @NonNull KeysOrWeightedKeys keysOrWeightedKeys) {
        protobufTransaction.addCommands(
                buildCommand(
                        ZInterStore, newArgsBuilder().add(destination).add(keysOrWeightedKeys.toArgs())));
        return getThis();
    }

    /**
     * Computes the intersection of sorted sets given by the specified <code>KeysOrWeightedKeys</code>
     * , and stores the result in <code>destination</code>. If <code>destination</code> already
     * exists, it is overwritten. Otherwise, a new sorted set will be created.<br>
     * To perform a <code>zinterstore</code> operation while specifying aggregation settings, use
     * {@link #zinterstore(String, KeysOrWeightedKeys, Aggregate)}
     *
     * @see <a href="https://redis.io/commands/zinterstore/">redis.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArrayBinary} for keys only.
     *       <li>Use {@link KeysOrWeightedKeysBinary} for weighted keys with score multipliers.
     *     </ul>
     *
     * @return Command Response - The number of elements in the resulting sorted set stored at <code>
     *     destination</code>.
     */
    public T zinterstore(
            @NonNull GlideString destination, @NonNull KeysOrWeightedKeysBinary keysOrWeightedKeys) {
        protobufTransaction.addCommands(
                buildCommand(
                        ZInterStore, newArgsBuilder().add(destination).add(keysOrWeightedKeys.toArgs())));
        return getThis();
    }

    /**
     * Returns the union of members from sorted sets specified by the given <code>keys</code>.<br>
     * To get the elements with their scores, see {@link #zunionWithScores}.
     *
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zunion/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets.
     * @return Command Response - The resulting sorted set from the union.
     */
    public T zunion(@NonNull KeyArray keys) {
        protobufTransaction.addCommands(buildCommand(ZUnion, newArgsBuilder().add(keys.toArgs())));
        return getThis();
    }

    /**
     * Returns the union of members from sorted sets specified by the given <code>keys</code>.<br>
     * To get the elements with their scores, see {@link #zunionWithScores}.
     *
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zunion/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets.
     * @return Command Response - The resulting sorted set from the union.
     */
    public T zunion(@NonNull KeyArrayBinary keys) {
        protobufTransaction.addCommands(buildCommand(ZUnion, newArgsBuilder().add(keys.toArgs())));
        return getThis();
    }

    /**
     * Returns the union of members and their scores from sorted sets specified by the given <code>
     * keysOrWeightedKeys</code>.
     *
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zunion/">redis.io</a> for more details.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArray} for keys only.
     *       <li>Use {@link WeightedKeys} for weighted keys with score multipliers.
     *     </ul>
     *
     * @param aggregate Specifies the aggregation strategy to apply when combining the scores of
     *     elements.
     * @return Command Response - The resulting sorted set from the union.
     */
    public T zunionWithScores(
            @NonNull KeysOrWeightedKeys keysOrWeightedKeys, @NonNull Aggregate aggregate) {
        protobufTransaction.addCommands(
                buildCommand(
                        ZUnion,
                        newArgsBuilder()
                                .add(keysOrWeightedKeys.toArgs())
                                .add(aggregate.toArgs())
                                .add(WITH_SCORES_REDIS_API)));
        return getThis();
    }

    /**
     * Returns the union of members and their scores from sorted sets specified by the given <code>
     * keysOrWeightedKeys</code>.
     *
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zunion/">redis.io</a> for more details.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArrayBinary} for keys only.
     *       <li>Use {@link WeightedKeysBinary} for weighted keys with score multipliers.
     *     </ul>
     *
     * @param aggregate Specifies the aggregation strategy to apply when combining the scores of
     *     elements.
     * @return Command Response - The resulting sorted set from the union.
     */
    public T zunionWithScores(
            @NonNull KeysOrWeightedKeysBinary keysOrWeightedKeys, @NonNull Aggregate aggregate) {
        protobufTransaction.addCommands(
                buildCommand(
                        ZUnion,
                        newArgsBuilder()
                                .add(keysOrWeightedKeys.toArgs())
                                .add(aggregate.toArgs())
                                .add(WITH_SCORES_REDIS_API)));
        return getThis();
    }

    /**
     * Returns the union of members and their scores from sorted sets specified by the given <code>
     * keysOrWeightedKeys</code>.<br>
     * To perform a <code>zunion</code> operation while specifying aggregation settings, use {@link
     * #zunionWithScores(KeysOrWeightedKeys, Aggregate)}.
     *
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zunion/">redis.io</a> for more details.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArray} for keys only.
     *       <li>Use {@link WeightedKeys} for weighted keys with score multipliers.
     *     </ul>
     *
     * @return Command Response - The resulting sorted set from the union.
     */
    public T zunionWithScores(@NonNull KeysOrWeightedKeys keysOrWeightedKeys) {
        protobufTransaction.addCommands(
                buildCommand(
                        ZUnion, newArgsBuilder().add(keysOrWeightedKeys.toArgs()).add(WITH_SCORES_REDIS_API)));
        return getThis();
    }

    /**
     * Returns the union of members and their scores from sorted sets specified by the given <code>
     * keysOrWeightedKeys</code>.<br>
     * To perform a <code>zunion</code> operation while specifying aggregation settings, use {@link
     * #zunionWithScores(KeysOrWeightedKeys, Aggregate)}.
     *
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zunion/">redis.io</a> for more details.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArrayBinary} for keys only.
     *       <li>Use {@link WeightedKeysBinary} for weighted keys with score multipliers.
     *     </ul>
     *
     * @return Command Response - The resulting sorted set from the union.
     */
    public T zunionWithScores(@NonNull KeysOrWeightedKeysBinary keysOrWeightedKeys) {
        protobufTransaction.addCommands(
                buildCommand(
                        ZUnion, newArgsBuilder().add(keysOrWeightedKeys.toArgs()).add(WITH_SCORES_REDIS_API)));
        return getThis();
    }

    /**
     * Returns the intersection of members from sorted sets specified by the given <code>keys</code>.
     * <br>
     * To get the elements with their scores, see {@link #zinterWithScores}.
     *
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zinter/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets.
     * @return Command Response - The resulting sorted set from the intersection.
     */
    public T zinter(@NonNull KeyArray keys) {
        protobufTransaction.addCommands(buildCommand(ZInter, newArgsBuilder().add(keys.toArgs())));
        return getThis();
    }

    /**
     * Returns the intersection of members from sorted sets specified by the given <code>keys</code>.
     * <br>
     * To get the elements with their scores, see {@link #zinterWithScores}.
     *
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zinter/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets.
     * @return Command Response - The resulting sorted set from the intersection.
     */
    public T zinter(@NonNull KeyArrayBinary keys) {
        protobufTransaction.addCommands(buildCommand(ZInter, newArgsBuilder().add(keys.toArgs())));
        return getThis();
    }

    /**
     * Returns the intersection of members and their scores from sorted sets specified by the given
     * <code>keysOrWeightedKeys</code>. To perform a <code>zinter</code> operation while specifying
     * aggregation settings, use {@link #zinterWithScores(KeysOrWeightedKeys, Aggregate)}.
     *
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zinter/">redis.io</a> for more details.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArray} for keys only.
     *       <li>Use {@link WeightedKeys} for weighted keys with score multipliers.
     *     </ul>
     *
     * @return Command Response - The resulting sorted set from the intersection.
     */
    public T zinterWithScores(@NonNull KeysOrWeightedKeys keysOrWeightedKeys) {
        protobufTransaction.addCommands(
                buildCommand(
                        ZInter, newArgsBuilder().add(keysOrWeightedKeys.toArgs()).add(WITH_SCORES_REDIS_API)));
        return getThis();
    }

    /**
     * Returns the intersection of members and their scores from sorted sets specified by the given
     * <code>keysOrWeightedKeys</code>. To perform a <code>zinter</code> operation while specifying
     * aggregation settings, use {@link #zinterWithScores(KeysOrWeightedKeys, Aggregate)}.
     *
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zinter/">redis.io</a> for more details.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArrayBinary} for keys only.
     *       <li>Use {@link WeightedKeysBinary} for weighted keys with score multipliers.
     *     </ul>
     *
     * @return Command Response - The resulting sorted set from the intersection.
     */
    public T zinterWithScores(@NonNull KeysOrWeightedKeysBinary keysOrWeightedKeys) {
        protobufTransaction.addCommands(
                buildCommand(
                        ZInter, newArgsBuilder().add(keysOrWeightedKeys.toArgs()).add(WITH_SCORES_REDIS_API)));
        return getThis();
    }

    /**
     * Returns the intersection of members and their scores from sorted sets specified by the given
     * <code>keysOrWeightedKeys</code>.
     *
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zinter/">redis.io</a> for more details.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArray} for keys only.
     *       <li>Use {@link WeightedKeys} for weighted keys with score multipliers.
     *     </ul>
     *
     * @param aggregate Specifies the aggregation strategy to apply when combining the scores of
     *     elements.
     * @return Command Response - The resulting sorted set from the intersection.
     */
    public T zinterWithScores(
            @NonNull KeysOrWeightedKeys keysOrWeightedKeys, @NonNull Aggregate aggregate) {
        protobufTransaction.addCommands(
                buildCommand(
                        ZInter,
                        newArgsBuilder()
                                .add(keysOrWeightedKeys.toArgs())
                                .add(aggregate.toArgs())
                                .add(WITH_SCORES_REDIS_API)));
        return getThis();
    }

    /**
     * Returns the intersection of members and their scores from sorted sets specified by the given
     * <code>keysOrWeightedKeys</code>.
     *
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/zinter/">redis.io</a> for more details.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArrayBinary} for keys only.
     *       <li>Use {@link WeightedKeysBinary} for weighted keys with score multipliers.
     *     </ul>
     *
     * @param aggregate Specifies the aggregation strategy to apply when combining the scores of
     *     elements.
     * @return Command Response - The resulting sorted set from the intersection.
     */
    public T zinterWithScores(
            @NonNull KeysOrWeightedKeysBinary keysOrWeightedKeys, @NonNull Aggregate aggregate) {
        protobufTransaction.addCommands(
                buildCommand(
                        ZInter,
                        newArgsBuilder()
                                .add(keysOrWeightedKeys.toArgs())
                                .add(aggregate.toArgs())
                                .add(WITH_SCORES_REDIS_API)));
        return getThis();
    }

    /**
     * Adds an entry to the specified stream stored at <code>key</code>.<br>
     * If the <code>key</code> doesn't exist, the stream is created.
     *
     * @see <a href="https://valkey.io/commands/xadd/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param values Field-value pairs to be added to the entry.
     * @return Command Response - The id of the added entry.
     */
    public T xadd(@NonNull String key, @NonNull Map<String, String> values) {
        return xadd(key, values, StreamAddOptions.builder().build());
    }

    /**
     * Adds an entry to the specified stream stored at <code>key</code>.<br>
     * If the <code>key</code> doesn't exist, the stream is created.
     *
     * @see <a href="https://valkey.io/commands/xadd/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param values Field-value pairs to be added to the entry.
     * @param options Stream add options {@link StreamAddOptions}.
     * @return Command Response - The id of the added entry, or <code>null</code> if {@link
     *     StreamAddOptionsBuilder#makeStream(Boolean)} is set to <code>false</code> and no stream
     *     with the matching <code>key</code> exists.
     */
    public T xadd(
            @NonNull String key, @NonNull Map<String, String> values, @NonNull StreamAddOptions options) {
        String[] arguments =
                ArrayUtils.addAll(
                        ArrayUtils.addFirst(options.toArgs(), key), convertMapToKeyValueStringArray(values));
        ArgsArray commandArgs = buildArgs(arguments);
        protobufTransaction.addCommands(buildCommand(XAdd, commandArgs));
        return getThis();
    }

    /**
     * Reads entries from the given streams.
     *
     * @see <a href="https://valkey.io/commands/xread/">valkey.io</a> for details.
     * @param keysAndIds An array of <code>Pair</code>s of keys and entry ids to read from. A <code>
     *     pair</code> is composed of a stream's key and the id of the entry after which the stream
     *     will be read.
     * @return Command Response - A <code>{@literal Map<String, Map<String, String[][]>>}</code> with stream
     *     keys, to <code>Map</code> of stream-ids, to an array of pairings with format <code>[[field, entry], [field, entry], ...]<code>.
     */
    public T xread(@NonNull Map<String, String> keysAndIds) {
        return xread(keysAndIds, StreamReadOptions.builder().build());
    }

    /**
     * Reads entries from the given streams.
     *
     * @see <a href="https://valkey.io/commands/xread/">valkey.io</a> for details.
     * @param keysAndIds An array of <code>Pair</code>s of keys and entry ids to read from. A <code>
     *     pair</code> is composed of a stream's key and the id of the entry after which the stream
     *     will be read.
     * @param options options detailing how to read the stream {@link StreamReadOptions}.
     * @return Command Response - A <code>{@literal Map<String, Map<String, String[][]>>}</code> with stream
     *     keys, to <code>Map</code> of stream-ids, to an array of pairings with format <code>[[field, entry], [field, entry], ...]<code>.
     */
    public T xread(@NonNull Map<String, String> keysAndIds, @NonNull StreamReadOptions options) {
        protobufTransaction.addCommands(buildCommand(XRead, buildArgs(options.toArgs(keysAndIds))));
        return getThis();
    }

    /**
     * Trims the stream by evicting older entries.
     *
     * @see <a href="https://valkey.io/commands/xtrim/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param options Stream trim options {@link StreamTrimOptions}.
     * @return Command Response - The number of entries deleted from the stream.
     */
    public T xtrim(@NonNull String key, @NonNull StreamTrimOptions options) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(options.toArgs(), key));
        protobufTransaction.addCommands(buildCommand(XTrim, commandArgs));
        return getThis();
    }

    /**
     * Returns the number of entries in the stream stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/xlen/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @return Command Response - The number of entries in the stream. If <code>key</code> does not
     *     exist, return <code>0</code>.
     */
    public T xlen(@NonNull String key) {
        protobufTransaction.addCommands(buildCommand(XLen, buildArgs(key)));
        return getThis();
    }

    /**
     * Removes the specified entries by id from a stream, and returns the number of entries deleted.
     *
     * @see <a href="https://valkey.io/commands/xdel/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param ids An array of entry ids.
     * @return Command Response - The number of entries removed from the stream. This number may be
     *     less than the number of entries in <code>ids</code>, if the specified <code>ids</code>
     *     don't exist in the stream.
     */
    public T xdel(@NonNull String key, @NonNull String[] ids) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(ids, key));
        protobufTransaction.addCommands(buildCommand(XDel, commandArgs));
        return getThis();
    }

    /**
     * Returns stream entries matching a given range of IDs.
     *
     * @see <a href="https://valkey.io/commands/xrange/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param start Starting stream ID bound for range.
     *     <ul>
     *       <li>Use {@link StreamRange.IdBound#of} to specify a stream ID.
     *       <li>Use {@link StreamRange.IdBound#ofExclusive} to specify an exclusive bounded stream
     *           ID.
     *       <li>Use {@link StreamRange.InfRangeBound#MIN} to start with the minimum available ID.
     *     </ul>
     *
     * @param end Ending stream ID bound for range.
     *     <ul>
     *       <li>Use {@link StreamRange.IdBound#of} to specify a stream ID.
     *       <li>Use {@link StreamRange.IdBound#ofExclusive} to specify an exclusive bounded stream
     *           ID.
     *       <li>Use {@link StreamRange.InfRangeBound#MAX} to end with the maximum available ID.
     *     </ul>
     *
     * @return Command Response - A <code>Map</code> of key to stream entry data, where entry data is an array of pairings with format <code>[[field, entry], [field, entry], ...]<code>.
     */
    public T xrange(@NonNull String key, @NonNull StreamRange start, @NonNull StreamRange end) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(StreamRange.toArgs(start, end), key));
        protobufTransaction.addCommands(buildCommand(XRange, commandArgs));
        return getThis();
    }

    /**
     * Returns stream entries matching a given range of IDs.
     *
     * @see <a href="https://valkey.io/commands/xrange/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param start Starting stream ID bound for range.
     *     <ul>
     *       <li>Use {@link StreamRange.IdBound#of} to specify a stream ID.
     *       <li>Use {@link StreamRange.IdBound#ofExclusive} to specify an exclusive bounded stream
     *           ID.
     *       <li>Use {@link StreamRange.InfRangeBound#MIN} to start with the minimum available ID.
     *     </ul>
     *
     * @param end Ending stream ID bound for range.
     *     <ul>
     *       <li>Use {@link StreamRange.IdBound#of} to specify a stream ID.
     *       <li>Use {@link StreamRange.IdBound#ofExclusive} to specify an exclusive bounded stream
     *           ID.
     *       <li>Use {@link StreamRange.InfRangeBound#MAX} to end with the maximum available ID.
     *     </ul>
     *
     * @param count Maximum count of stream entries to return.
     * @return Command Response - A <code>Map</code> of key to stream entry data, where entry data is an array of pairings with format <code>[[field, entry], [field, entry], ...]<code>.
     */
    public T xrange(
            @NonNull String key, @NonNull StreamRange start, @NonNull StreamRange end, long count) {
        ArgsArray commandArgs =
                buildArgs(ArrayUtils.addFirst(StreamRange.toArgs(start, end, count), key));
        protobufTransaction.addCommands(buildCommand(XRange, commandArgs));
        return getThis();
    }

    /**
     * Returns stream entries matching a given range of IDs in reverse order.<br>
     * Equivalent to {@link #xrange(String, StreamRange, StreamRange)} but returns the entries in
     * reverse order.
     *
     * @see <a href="https://valkey.io/commands/xrevrange/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param end Ending stream ID bound for range.
     *     <ul>
     *       <li>Use {@link StreamRange.IdBound#of} to specify a stream ID.
     *       <li>Use {@link StreamRange.IdBound#ofExclusive} to specify an exclusive bounded stream
     *           ID.
     *       <li>Use {@link StreamRange.InfRangeBound#MAX} to end with the maximum available ID.
     *     </ul>
     *
     * @param start Starting stream ID bound for range.
     *     <ul>
     *       <li>Use {@link StreamRange.IdBound#of} to specify a stream ID.
     *       <li>Use {@link StreamRange.IdBound#ofExclusive} to specify an exclusive bounded stream
     *           ID.
     *       <li>Use {@link StreamRange.InfRangeBound#MIN} to start with the minimum available ID.
     *     </ul>
     *
     * @return Command Response - A <code>Map</code> of key to stream entry data, where entry data is an array of pairings with format <code>[[field, entry], [field, entry], ...]<code>.
     */
    public T xrevrange(@NonNull String key, @NonNull StreamRange end, @NonNull StreamRange start) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(StreamRange.toArgs(end, start), key));
        protobufTransaction.addCommands(buildCommand(XRevRange, commandArgs));
        return getThis();
    }

    /**
     * Returns stream entries matching a given range of IDs in reverse order.<br>
     * Equivalent to {@link #xrange(String, StreamRange, StreamRange, long)} but returns the entries
     * in reverse order.
     *
     * @see <a href="https://valkey.io/commands/xrevrange/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param start Starting stream ID bound for range.
     *     <ul>
     *       <li>Use {@link StreamRange.IdBound#of} to specify a stream ID.
     *       <li>Use {@link StreamRange.IdBound#ofExclusive} to specify an exclusive bounded stream
     *           ID.
     *       <li>Use {@link StreamRange.InfRangeBound#MIN} to start with the minimum available ID.
     *     </ul>
     *
     * @param end Ending stream ID bound for range.
     *     <ul>
     *       <li>Use {@link StreamRange.IdBound#of} to specify a stream ID.
     *       <li>Use {@link StreamRange.IdBound#ofExclusive} to specify an exclusive bounded stream
     *           ID.
     *       <li>Use {@link StreamRange.InfRangeBound#MAX} to end with the maximum available ID.
     *     </ul>
     *
     * @param count Maximum count of stream entries to return.
     * @return Command Response - A <code>Map</code> of key to stream entry data, where entry data is an array of pairings with format <code>[[field, entry], [field, entry], ...]<code>.
     */
    public T xrevrange(
            @NonNull String key, @NonNull StreamRange end, @NonNull StreamRange start, long count) {
        ArgsArray commandArgs =
                buildArgs(ArrayUtils.addFirst(StreamRange.toArgs(end, start, count), key));
        protobufTransaction.addCommands(buildCommand(XRevRange, commandArgs));
        return getThis();
    }

    /**
     * Creates a new consumer group uniquely identified by <code>groupname</code> for the stream
     * stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/xgroup-create/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param groupName The newly created consumer group name.
     * @param id Stream entry ID that specifies the last delivered entry in the stream from the new
     *     group’s perspective. The special ID <code>"$"</code> can be used to specify the last entry
     *     in the stream.
     * @return Command Response - <code>OK</code>.
     */
    public T xgroupCreate(@NonNull String key, @NonNull String groupName, @NonNull String id) {
        protobufTransaction.addCommands(buildCommand(XGroupCreate, buildArgs(key, groupName, id)));
        return getThis();
    }

    /**
     * Creates a new consumer group uniquely identified by <code>groupname</code> for the stream
     * stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/xgroup-create/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param groupName The newly created consumer group name.
     * @param id Stream entry ID that specifies the last delivered entry in the stream from the new
     *     group’s perspective. The special ID <code>"$"</code> can be used to specify the last entry
     *     in the stream.
     * @param options The group options {@link StreamGroupOptions}.
     * @return Command Response - <code>OK</code>.
     */
    public <ArgType> T xgroupCreate(
            @NonNull ArgType key,
            @NonNull ArgType groupName,
            @NonNull ArgType id,
            @NonNull StreamGroupOptions options) {
        protobufTransaction.addCommands(
                buildCommand(
                        XGroupCreate, newArgsBuilder().add(key).add(groupName).add(id).add(options.toArgs())));
        return getThis();
    }

    /**
     * Destroys the consumer group <code>groupname</code> for the stream stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/xgroup-destroy/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param groupname The newly created consumer group name.
     * @return Command Response - <code>true</code> if the consumer group is destroyed. Otherwise,
     *     <code>false</code>.
     */
    public T xgroupDestroy(@NonNull String key, @NonNull String groupname) {
        protobufTransaction.addCommands(buildCommand(XGroupDestroy, buildArgs(key, groupname)));
        return getThis();
    }

    /**
     * Creates a consumer named <code>consumer</code> in the consumer group <code>group</code> for the
     * stream stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/xgroup-createconsumer/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param group The consumer group name.
     * @param consumer The newly created consumer.
     * @return Command Response - <code>true</code> if the consumer is created. Otherwise, <code>false
     *     </code>.
     */
    public T xgroupCreateConsumer(
            @NonNull String key, @NonNull String group, @NonNull String consumer) {
        protobufTransaction.addCommands(
                buildCommand(XGroupCreateConsumer, buildArgs(key, group, consumer)));
        return getThis();
    }

    /**
     * Deletes a consumer named <code>consumer</code> in the consumer group <code>group</code>.
     *
     * @see <a href="https://valkey.io/commands/xgroup-delconsumer/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param group The consumer group name.
     * @param consumer The newly created consumer.
     * @return Command Response - The number of pending messages the <code>consumer</code> had before
     *     it was deleted.
     */
    public T xgroupDelConsumer(@NonNull String key, @NonNull String group, @NonNull String consumer) {
        protobufTransaction.addCommands(
                buildCommand(XGroupDelConsumer, buildArgs(key, group, consumer)));
        return getThis();
    }

    /**
     * Sets the last delivered ID for a consumer group.
     *
     * @see <a href="https://valkey.io/commands/xgroup-setid/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param groupName The consumer group name.
     * @param id The stream entry ID that should be set as the last delivered ID for the consumer
     *     group.
     * @return Command Response - <code>OK</code>.
     */
    public T xgroupSetId(@NonNull String key, @NonNull String groupName, @NonNull String id) {
        protobufTransaction.addCommands(buildCommand(XGroupSetId, buildArgs(key, groupName, id)));
        return getThis();
    }

    /**
     * Sets the last delivered ID for a consumer group.
     *
     * @since Redis 7.0 and above
     * @see <a href="https://valkey.io/commands/xgroup-setid/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param groupName The consumer group name.
     * @param id The stream entry ID that should be set as the last delivered ID for the consumer
     *     group.
     * @param entriesReadId An arbitrary ID (that isn't the first ID, last ID, or the zero ID (<code>
     *     "0-0"</code>)) used to find out how many entries are between the arbitrary ID (excluding
     *     it) and the stream's last entry.
     * @return Command Response - <code>OK</code>.
     */
    public <ArgType> T xgroupSetId(
            @NonNull ArgType key,
            @NonNull ArgType groupName,
            @NonNull ArgType id,
            @NonNull ArgType entriesReadId) {
        protobufTransaction.addCommands(
                buildCommand(
                        XGroupSetId,
                        newArgsBuilder()
                                .add(key)
                                .add(groupName)
                                .add(id)
                                .add("ENTRIESREAD")
                                .add(entriesReadId)));
        return getThis();
    }

    /**
     * Reads entries from the given streams owned by a consumer group.
     *
     * @apiNote When in cluster mode, all keys in <code>keysAndIds</code> must map to the same hash
     *     slot.
     * @see <a href="https://valkey.io/commands/xreadgroup/">valkey.io</a> for details.
     * @param keysAndIds A <code>Map</code> of keys and entry ids to read from. The <code>
     *     Map</code> is composed of a stream's key and the id of the entry after which the stream
     *     will be read. Use the special id of <code>{@literal Map<String, Map<String, String[][]>>}
     *     </code> to receive only new messages.
     * @param group The consumer group name.
     * @param consumer The newly created consumer.
     * @return Command Response - A <code>{@literal Map<String, Map<String, String[][]>>}</code> with
     *     stream keys, to <code>Map</code> of stream-ids, to an array of pairings with format <code>
     *     [[field, entry], [field, entry], ...]<code>.
     *     Returns <code>null</code> if the consumer group does not exist. Returns a <code>Map</code>
     *     with a value of code>null</code> if the stream is empty.
     */
    public T xreadgroup(
            @NonNull Map<String, String> keysAndIds, @NonNull String group, @NonNull String consumer) {
        return xreadgroup(keysAndIds, group, consumer, StreamReadGroupOptions.builder().build());
    }

    /**
     * Reads entries from the given streams owned by a consumer group.
     *
     * @apiNote When in cluster mode, all keys in <code>keysAndIds</code> must map to the same hash
     *     slot.
     * @see <a href="https://valkey.io/commands/xreadgroup/">valkey.io</a> for details.
     * @param keysAndIds A <code>Map</code> of keys and entry ids to read from. The <code>
     *     Map</code> is composed of a stream's key and the id of the entry after which the stream
     *     will be read. Use the special id of <code>{@literal Map<String, Map<String, String[][]>>}
     *     </code> to receive only new messages.
     * @param group The consumer group name.
     * @param consumer The newly created consumer.
     * @param options Options detailing how to read the stream {@link StreamReadGroupOptions}.
     * @return Command Response - A <code>{@literal Map<String, Map<String, String[][]>>}</code> with
     *     stream keys, to <code>Map</code> of stream-ids, to an array of pairings with format <code>
     *     [[field, entry], [field, entry], ...]<code>.
     *     Returns <code>null</code> if the consumer group does not exist. Returns a <code>Map</code>
     *     with a value of code>null</code> if the stream is empty.
     */
    public T xreadgroup(
            @NonNull Map<String, String> keysAndIds,
            @NonNull String group,
            @NonNull String consumer,
            @NonNull StreamReadGroupOptions options) {
        protobufTransaction.addCommands(
                buildCommand(XReadGroup, buildArgs(options.toArgs(group, consumer, keysAndIds))));
        return getThis();
    }

    /**
     * Returns the number of messages that were successfully acknowledged by the consumer group member
     * of a stream. This command should be called on a pending message so that such message does not
     * get processed again.
     *
     * @param key The key of the stream.
     * @param group The consumer group name.
     * @param ids Stream entry ID to acknowledge and purge messages.
     * @return Command Response - The number of messages that were successfully acknowledged.
     */
    public T xack(@NonNull String key, @NonNull String group, @NonNull String[] ids) {
        String[] args = concatenateArrays(new String[] {key, group}, ids);
        protobufTransaction.addCommands(buildCommand(XAck, buildArgs(args)));
        return getThis();
    }

    /**
     * Returns stream message summary information for pending messages matching a given range of IDs.
     *
     * @see <a href="https://valkey.io/commands/xpending/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param group The consumer group name.
     * @return Command Response - A 2D-<code>array</code> that includes the summary of pending
     *     messages, with the format <code>
     *     [NumOfMessages, StartId, EndId, [[Consumer, NumOfMessages], ...]</code>, where:
     *     <ul>
     *       <li><code>NumOfMessages</code>: The total number of pending messages for this consumer
     *           group.
     *       <li><code>StartId</code>: The smallest ID among the pending messages.
     *       <li><code>EndId</code>: The greatest ID among the pending messages.
     *       <li><code>[[Consumer, NumOfMessages], ...]</code>: A 2D-<code>array</code> of every
     *           consumer in the consumer group with at least one pending message, and the number of
     *           pending messages it has.
     *     </ul>
     */
    public T xpending(@NonNull String key, @NonNull String group) {
        String[] args = {key, group};
        protobufTransaction.addCommands(buildCommand(XPending, buildArgs(args)));
        return getThis();
    }

    /**
     * Returns an extended form of stream message information for pending messages matching a given
     * range of IDs.
     *
     * @see <a href="https://valkey.io/commands/xpending/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param group The consumer group name.
     * @param start Starting stream ID bound for range.
     *     <ul>
     *       <li>Use {@link IdBound#of} to specify a stream ID.
     *       <li>Use {@link IdBound#ofExclusive} to specify an exclusive bounded stream ID.
     *       <li>Use {@link InfRangeBound#MIN} to start with the minimum available ID.
     *     </ul>
     *
     * @param end Ending stream ID bound for range.
     *     <ul>
     *       <li>Use {@link IdBound#of} to specify a stream ID.
     *       <li>Use {@link IdBound#ofExclusive} to specify an exclusive bounded stream ID.
     *       <li>Use {@link InfRangeBound#MAX} to end with the maximum available ID.
     *     </ul>
     *
     * @param count Limits the number of messages returned.
     * @return Command Response - A 2D-<code>array</code> of 4-tuples containing extended message
     *     information with the format <code>[[ID, Consumer, TimeElapsed, NumOfDelivered], ... ]
     *     </code>, where:
     *     <ul>
     *       <li><code>ID</code>: The ID of the message.
     *       <li><code>Consumer</code>: The name of the consumer that fetched the message and has
     *           still to acknowledge it. We call it the current owner of the message.
     *       <li><code>TimeElapsed</code>: The number of milliseconds that elapsed since the last time
     *           this message was delivered to this consumer.
     *       <li><code>NumOfDelivered</code>: The number of times this message was delivered.
     *     </ul>
     */
    public T xpending(
            @NonNull String key,
            @NonNull String group,
            @NonNull StreamRange start,
            @NonNull StreamRange end,
            long count) {
        return xpending(key, group, start, end, count, StreamPendingOptions.builder().build());
    }

    /**
     * Returns an extended form of stream message information for pending messages matching a given
     * range of IDs.
     *
     * @see <a href="https://valkey.io/commands/xpending/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param group The consumer group name.
     * @param start Starting stream ID bound for range.
     *     <ul>
     *       <li>Use {@link IdBound#of} to specify a stream ID.
     *       <li>Use {@link IdBound#ofExclusive} to specify an exclusive bounded stream ID.
     *       <li>Use {@link InfRangeBound#MIN} to start with the minimum available ID.
     *     </ul>
     *
     * @param end Ending stream ID bound for range.
     *     <ul>
     *       <li>Use {@link IdBound#of} to specify a stream ID.
     *       <li>Use {@link IdBound#ofExclusive} to specify an exclusive bounded stream ID.
     *       <li>Use {@link InfRangeBound#MAX} to end with the maximum available ID.
     *     </ul>
     *
     * @param count Limits the number of messages returned.
     * @param options Stream add options {@link StreamPendingOptions}.
     * @return Command Response - A 2D-<code>array</code> of 4-tuples containing extended message
     *     information with the format <code>[[ID, Consumer, TimeElapsed, NumOfDelivered], ... ]
     *     </code>, where:
     *     <ul>
     *       <li><code>ID</code>: The ID of the message.
     *       <li><code>Consumer</code>: The name of the consumer that fetched the message and has
     *           still to acknowledge it. We call it the current owner of the message.
     *       <li><code>TimeElapsed</code>: The number of milliseconds that elapsed since the last time
     *           this message was delivered to this consumer.
     *       <li><code>NumOfDelivered</code>: The number of times this message was delivered.
     *     </ul>
     */
    public T xpending(
            @NonNull String key,
            @NonNull String group,
            @NonNull StreamRange start,
            @NonNull StreamRange end,
            long count,
            @NonNull StreamPendingOptions options) {
        String[] args = concatenateArrays(new String[] {key, group}, options.toArgs(start, end, count));
        protobufTransaction.addCommands(buildCommand(XPending, buildArgs(args)));
        return getThis();
    }

    /**
     * Changes the ownership of a pending message.
     *
     * @see <a href="https://valkey.io/commands/xclaim/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param group The consumer group name.
     * @param consumer The group consumer.
     * @param minIdleTime The minimum idle time for the message to be claimed.
     * @param ids An array of entry ids.
     * @return Command Response - A <code>Map</code> of message entries with the format <code>
     *      {"entryId": [["entry", "data"], ...], ...}</code> that are claimed by the consumer.
     */
    public T xclaim(
            @NonNull String key,
            @NonNull String group,
            @NonNull String consumer,
            long minIdleTime,
            @NonNull String[] ids) {
        String[] args =
                concatenateArrays(new String[] {key, group, consumer, Long.toString(minIdleTime)}, ids);
        protobufTransaction.addCommands(buildCommand(XClaim, buildArgs(args)));
        return getThis();
    }

    /**
     * Changes the ownership of a pending message.
     *
     * @see <a href="https://valkey.io/commands/xclaim/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param group The consumer group name.
     * @param consumer The group consumer.
     * @param minIdleTime The minimum idle time for the message to be claimed.
     * @param ids An array of entry ids.
     * @param options Stream claim options {@link StreamClaimOptions}.
     * @return Command Response - A <code>Map</code> of message entries with the format <code>
     *      {"entryId": [["entry", "data"], ...], ...}</code> that are claimed by the consumer.
     */
    public T xclaim(
            @NonNull String key,
            @NonNull String group,
            @NonNull String consumer,
            long minIdleTime,
            @NonNull String[] ids,
            @NonNull StreamClaimOptions options) {
        String[] args =
                concatenateArrays(
                        new String[] {key, group, consumer, Long.toString(minIdleTime)}, ids, options.toArgs());
        protobufTransaction.addCommands(buildCommand(XClaim, buildArgs(args)));
        return getThis();
    }

    /**
     * Changes the ownership of a pending message. This function returns an <code>array</code> with
     * only the message/entry IDs, and is equivalent to using <code>JUSTID</code> in the Redis API.
     *
     * @see <a href="https://valkey.io/commands/xclaim/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param group The consumer group name.
     * @param consumer The group consumer.
     * @param minIdleTime The minimum idle time for the message to be claimed.
     * @param ids An array of entry ids.
     * @return Command Response - An <code>array</code> of message ids claimed by the consumer.
     */
    public T xclaimJustId(
            @NonNull String key,
            @NonNull String group,
            @NonNull String consumer,
            long minIdleTime,
            @NonNull String[] ids) {
        String[] args =
                concatenateArrays(
                        new String[] {key, group, consumer, Long.toString(minIdleTime)},
                        ids,
                        new String[] {JUST_ID_REDIS_API});
        protobufTransaction.addCommands(buildCommand(XClaim, buildArgs(args)));
        return getThis();
    }

    /**
     * Changes the ownership of a pending message. This function returns an <code>array</code> with
     * only the message/entry IDs, and is equivalent to using <code>JUSTID</code> in the Redis API.
     *
     * @see <a href="https://valkey.io/commands/xclaim/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param group The consumer group name.
     * @param consumer The group consumer.
     * @param minIdleTime The minimum idle time for the message to be claimed.
     * @param ids An array of entry ids.
     * @param options Stream claim options {@link StreamClaimOptions}.
     * @return Command Response - An <code>array</code> of message ids claimed by the consumer.
     */
    public T xclaimJustId(
            @NonNull String key,
            @NonNull String group,
            @NonNull String consumer,
            long minIdleTime,
            @NonNull String[] ids,
            @NonNull StreamClaimOptions options) {
        String[] args =
                concatenateArrays(
                        new String[] {key, group, consumer, Long.toString(minIdleTime)},
                        ids,
                        options.toArgs(),
                        new String[] {JUST_ID_REDIS_API});
        protobufTransaction.addCommands(buildCommand(XClaim, buildArgs(args)));
        return getThis();
    }

    /**
     * Returns the remaining time to live of <code>key</code> that has a timeout, in milliseconds.
     *
     * @see <a href="https://redis.io/commands/pttl/">redis.io</a> for details.
     * @param key The key to return its timeout.
     * @return Command Response - TTL in milliseconds. <code>-2</code> if <code>key</code> does not
     *     exist, <code>-1</code> if <code>key</code> exists but has no associated expire.
     */
    public T pttl(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(PTTL, commandArgs));
        return getThis();
    }

    /**
     * Removes the existing timeout on <code>key</code>, turning the <code>key</code> from volatile (a
     * <code>key</code> with an expire set) to persistent (a <code>key</code> that will never expire
     * as no timeout is associated).
     *
     * @see <a href="https://redis.io/commands/persist/">redis.io</a> for details.
     * @param key The <code>key</code> to remove the existing timeout on.
     * @return Command Response - <code>false</code> if <code>key</code> does not exist or does not
     *     have an associated timeout, <code>true</code> if the timeout has been removed.
     */
    public T persist(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(Persist, commandArgs));
        return getThis();
    }

    /**
     * Returns the server time.
     *
     * @see <a href="https://redis.io/commands/time/">redis.io</a> for details.
     * @return Command Response - The current server time as a <code>String</code> array with two
     *     elements: A <code>UNIX TIME</code> and the amount of microseconds already elapsed in the
     *     current second. The returned array is in a <code>[UNIX TIME, Microseconds already elapsed]
     *     </code> format.
     */
    public T time() {
        protobufTransaction.addCommands(buildCommand(Time));
        return getThis();
    }

    /**
     * Returns <code>UNIX TIME</code> of the last DB save timestamp or startup timestamp if no save
     * was made since then.
     *
     * @see <a href="https://redis.io/commands/lastsave/">redis.io</a> for details.
     * @return Command Response - <code>UNIX TIME</code> of the last DB save executed with success.
     */
    public T lastsave() {
        protobufTransaction.addCommands(buildCommand(LastSave));
        return getThis();
    }

    /**
     * Deletes all the keys of all the existing databases. This command never fails.
     *
     * @see <a href="https://valkey.io/commands/flushall/">valkey.io</a> for details.
     * @return Command Response - <code>OK</code>.
     */
    public T flushall() {
        protobufTransaction.addCommands(buildCommand(FlushAll));
        return getThis();
    }

    /**
     * Deletes all the keys of all the existing databases. This command never fails.
     *
     * @see <a href="https://valkey.io/commands/flushall/">valkey.io</a> for details.
     * @param mode The flushing mode, could be either {@link FlushMode#SYNC} or {@link
     *     FlushMode#ASYNC}.
     * @return Command Response - <code>OK</code>.
     */
    public T flushall(FlushMode mode) {
        protobufTransaction.addCommands(buildCommand(FlushAll, buildArgs(mode.toString())));
        return getThis();
    }

    /**
     * Deletes all the keys of the currently selected database. This command never fails.
     *
     * @see <a href="https://valkey.io/commands/flushdb/">valkey.io</a> for details.
     * @return Command Response - <code>OK</code>.
     */
    public T flushdb() {
        protobufTransaction.addCommands(buildCommand(FlushDB));
        return getThis();
    }

    /**
     * Deletes all the keys of the currently selected database. This command never fails.
     *
     * @see <a href="https://valkey.io/commands/flushdb/">valkey.io</a> for details.
     * @param mode The flushing mode, could be either {@link FlushMode#SYNC} or {@link
     *     FlushMode#ASYNC}.
     * @return Command Response - <code>OK</code>.
     */
    public T flushdb(FlushMode mode) {
        protobufTransaction.addCommands(buildCommand(FlushDB, buildArgs(mode.toString())));
        return getThis();
    }

    /**
     * Displays a piece of generative computer art and the Redis version.
     *
     * @see <a href="https://redis.io/commands/lolwut/">redis.io</a> for details.
     * @return Command Response - A piece of generative computer art along with the current Redis
     *     version.
     */
    public T lolwut() {
        protobufTransaction.addCommands(buildCommand(Lolwut));
        return getThis();
    }

    /**
     * Displays a piece of generative computer art and the Redis version.
     *
     * @see <a href="https://redis.io/commands/lolwut/">redis.io</a> for details.
     * @param parameters Additional set of arguments in order to change the output:
     *     <ul>
     *       <li>On Redis version <code>5</code>, those are length of the line, number of squares per
     *           row, and number of squares per column.
     *       <li>On Redis version <code>6</code>, those are number of columns and number of lines.
     *       <li>On other versions parameters are ignored.
     *     </ul>
     *
     * @return Command Response - A piece of generative computer art along with the current Redis
     *     version.
     */
    public T lolwut(int @NonNull [] parameters) {
        String[] arguments =
                Arrays.stream(parameters).mapToObj(Integer::toString).toArray(String[]::new);
        protobufTransaction.addCommands(buildCommand(Lolwut, buildArgs(arguments)));
        return getThis();
    }

    /**
     * Displays a piece of generative computer art and the Redis version.
     *
     * @apiNote Versions 5 and 6 produce graphical things.
     * @see <a href="https://redis.io/commands/lolwut/">redis.io</a> for details.
     * @param version Version of computer art to generate.
     * @return Command Response - A piece of generative computer art along with the current Redis
     *     version.
     */
    public T lolwut(int version) {
        ArgsArray commandArgs = buildArgs(VERSION_REDIS_API, Integer.toString(version));
        protobufTransaction.addCommands(buildCommand(Lolwut, commandArgs));
        return getThis();
    }

    /**
     * Displays a piece of generative computer art and the Redis version.
     *
     * @apiNote Versions 5 and 6 produce graphical things.
     * @see <a href="https://redis.io/commands/lolwut/">redis.io</a> for details.
     * @param version Version of computer art to generate.
     * @param parameters Additional set of arguments in order to change the output:
     *     <ul>
     *       <li>For version <code>5</code>, those are length of the line, number of squares per row,
     *           and number of squares per column.
     *       <li>For version <code>6</code>, those are number of columns and number of lines.
     *     </ul>
     *
     * @return Command Response - A piece of generative computer art along with the current Redis
     *     version.
     */
    public T lolwut(int version, int @NonNull [] parameters) {
        String[] arguments =
                concatenateArrays(
                        new String[] {VERSION_REDIS_API, Integer.toString(version)},
                        Arrays.stream(parameters).mapToObj(Integer::toString).toArray(String[]::new));
        protobufTransaction.addCommands(buildCommand(Lolwut, buildArgs(arguments)));
        return getThis();
    }

    /**
     * Returns the number of keys in the currently selected database.
     *
     * @see <a href="https://valkey.io/commands/dbsize/">valkey.io</a> for details.
     * @return Command Response - The number of keys in the currently selected database.
     */
    public T dbsize() {
        protobufTransaction.addCommands(buildCommand(DBSize));
        return getThis();
    }

    /**
     * Returns the string representation of the type of the value stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/type/>redis.io</a> for details.
     * @param key The <code>key</code> to check its data type.
     * @return Command Response - If the <code>key</code> exists, the type of the stored value is
     *     returned. Otherwise, a "none" string is returned.
     */
    public T type(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(Type, commandArgs));
        return getThis();
    }

    /**
     * Returns a random key from the currently selected database. *
     *
     * @see <a href="https://redis.io/docs/latest/commands/randomkey/">redis.io</a> for details.
     * @return Command Response - A random <code>key</code> from the database.
     */
    public T randomKey() {
        protobufTransaction.addCommands(buildCommand(RandomKey));
        return getThis();
    }

    /**
     * Renames <code>key</code> to <code>newKey</code>.<br>
     * If <code>newKey</code> already exists it is overwritten.
     *
     * @see <a href="https://redis.io/commands/rename/">redis.io</a> for details.
     * @param key The <code>key</code> to rename.
     * @param newKey The new name of the <code>key</code>.
     * @return Command Response - If the <code>key</code> was successfully renamed, return <code>"OK"
     *     </code>. If <code>key</code> does not exist, the transaction fails with an error.
     */
    public T rename(@NonNull String key, @NonNull String newKey) {
        ArgsArray commandArgs = buildArgs(key, newKey);
        protobufTransaction.addCommands(buildCommand(Rename, commandArgs));
        return getThis();
    }

    /**
     * Renames <code>key</code> to <code>newKey</code> if <code>newKey</code> does not yet exist.
     *
     * @see <a href="https://redis.io/commands/renamenx/">redis.io</a> for details.
     * @param key The key to rename.
     * @param newKey The new key name.
     * @return Command Response - <code>true</code> if <code>key</code> was renamed to <code>newKey
     *     </code>, <code>false</code> if <code>newKey</code> already exists.
     */
    public T renamenx(@NonNull String key, @NonNull String newKey) {
        ArgsArray commandArgs = buildArgs(key, newKey);
        protobufTransaction.addCommands(buildCommand(RenameNX, commandArgs));
        return getThis();
    }

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
     * @return Command Response - The list length after a successful insert operation.<br>
     *     If the <code>key</code> doesn't exist returns <code>-1</code>.<br>
     *     If the <code>pivot</code> wasn't found, returns <code>0</code>.
     */
    public T linsert(
            @NonNull String key,
            @NonNull InsertPosition position,
            @NonNull String pivot,
            @NonNull String element) {
        ArgsArray commandArgs = buildArgs(key, position.toString(), pivot, element);
        protobufTransaction.addCommands(buildCommand(LInsert, commandArgs));
        return getThis();
    }

    /**
     * Pops an element from the tail of the first list that is non-empty, with the given <code>keys
     * </code> being checked in the order that they are given.<br>
     * Blocks the connection when there are no elements to pop from any of the given lists.
     *
     * @see <a href="https://redis.io/commands/brpop/">redis.io</a> for details.
     * @apiNote <code>BRPOP</code> is a client blocking command, see <a
     *     href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *     Commands</a> for more details and best practices.
     * @param keys The <code>keys</code> of the lists to pop from.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @return Command Response - A two-element <code>array</code> containing the <code>key</code>
     *     from which the element was popped and the <code>value</code> of the popped element,
     *     formatted as <code>
     *     [key, value]</code>. If no element could be popped and the timeout expired, returns </code>
     *     null</code>.
     */
    public T brpop(@NonNull String[] keys, double timeout) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.add(keys, Double.toString(timeout)));
        protobufTransaction.addCommands(buildCommand(BRPop, commandArgs));
        return getThis();
    }

    /**
     * Inserts all the specified values at the head of the list stored at <code>key</code>, only if
     * <code>key</code> exists and holds a list. If <code>key</code> is not a list, this performs no
     * operation.
     *
     * @see <a href="https://redis.io/commands/lpushx/">redis.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to insert at the head of the list stored at <code>key</code>.
     * @return Command Response - The length of the list after the push operation.
     */
    public T lpushx(@NonNull String key, @NonNull String[] elements) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(elements, key));
        protobufTransaction.addCommands(buildCommand(LPushX, commandArgs));
        return getThis();
    }

    /**
     * Inserts all the specified values at the tail of the list stored at <code>key</code>, only if
     * <code>key</code> exists and holds a list. If <code>key</code> is not a list, this performs no
     * operation.
     *
     * @see <a href="https://redis.io/commands/rpushx/">redis.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to insert at the tail of the list stored at <code>key</code>.
     * @return Command Response - The length of the list after the push operation.
     */
    public T rpushx(@NonNull String key, @NonNull String[] elements) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(elements, key));
        protobufTransaction.addCommands(buildCommand(RPushX, commandArgs));
        return getThis();
    }

    /**
     * Pops an element from the head of the first list that is non-empty, with the given <code>keys
     * </code> being checked in the order that they are given.<br>
     * Blocks the connection when there are no elements to pop from any of the given lists.
     *
     * @see <a href="https://redis.io/commands/blpop/">redis.io</a> for details.
     * @apiNote <code>BLPOP</code> is a client blocking command, see <a
     *     href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *     Commands</a> for more details and best practices.
     * @param keys The <code>keys</code> of the lists to pop from.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @return Command Response - A two-element <code>array</code> containing the <code>key</code>
     *     from which the element was popped and the <code>value</code> of the popped element,
     *     formatted as <code>
     *     [key, value]</code>. If no element could be popped and the timeout expired, returns </code>
     *     null</code>.
     */
    public T blpop(@NonNull String[] keys, double timeout) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.add(keys, Double.toString(timeout)));
        protobufTransaction.addCommands(buildCommand(BLPop, commandArgs));
        return getThis();
    }

    /**
     * Returns the specified range of elements in the sorted set stored at <code>key</code>.<br>
     * <code>ZRANGE</code> can perform different types of range queries: by index (rank), by the
     * score, or by lexicographical order.<br>
     * To get the elements with their scores, see {@link #zrangeWithScores}.
     *
     * @see <a href="https://redis.io/commands/zrange/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by lexicographical order, use {@link RangeByLex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @param reverse If true, reverses the sorted set, with index 0 as the element with the highest
     *     score.
     * @return Command Response - An array of elements within the specified range. If <code>key</code>
     *     does not exist, it is treated as an empty sorted set, and the command returns an empty
     *     array.
     */
    public T zrange(@NonNull String key, @NonNull RangeQuery rangeQuery, boolean reverse) {
        ArgsArray commandArgs = buildArgs(createZRangeArgs(key, rangeQuery, reverse, false));
        protobufTransaction.addCommands(buildCommand(ZRange, commandArgs));
        return getThis();
    }

    /**
     * Returns the specified range of elements in the sorted set stored at <code>key</code>.<br>
     * <code>ZRANGE</code> can perform different types of range queries: by index (rank), by the
     * score, or by lexicographical order.<br>
     * To get the elements with their scores, see {@link #zrangeWithScores}.
     *
     * @see <a href="https://redis.io/commands/zrange/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by lexicographical order, use {@link RangeByLex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @return Command Response - An array of elements within the specified range. If <code>key</code>
     *     does not exist, it is treated as an empty sorted set, and the command returns an empty
     *     array.
     */
    public T zrange(@NonNull String key, @NonNull RangeQuery rangeQuery) {
        return getThis().zrange(key, rangeQuery, false);
    }

    /**
     * Returns the specified range of elements with their scores in the sorted set stored at <code>key
     * </code>. Similar to {@link #zrange} but with a <code>WITHSCORE</code> flag.
     *
     * @see <a href="https://redis.io/commands/zrange/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @param reverse If true, reverses the sorted set, with index 0 as the element with the highest
     *     score.
     * @return Command Response - A <code>Map</code> of elements and their scores within the specified
     *     range. If <code>key</code> does not exist, it is treated as an empty sorted set, and the
     *     command returns an empty <code>Map</code>.
     */
    public T zrangeWithScores(
            @NonNull String key, @NonNull ScoredRangeQuery rangeQuery, boolean reverse) {
        ArgsArray commandArgs = buildArgs(createZRangeArgs(key, rangeQuery, reverse, true));
        protobufTransaction.addCommands(buildCommand(ZRange, commandArgs));
        return getThis();
    }

    /**
     * Returns the specified range of elements with their scores in the sorted set stored at <code>key
     * </code>. Similar to {@link #zrange} but with a <code>WITHSCORE</code> flag.
     *
     * @see <a href="https://redis.io/commands/zrange/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @return Command Response - A <code>Map</code> of elements and their scores within the specified
     *     range. If <code>key</code> does not exist, it is treated as an empty sorted set, and the
     *     command returns an empty <code>Map</code>.
     */
    public T zrangeWithScores(@NonNull String key, @NonNull ScoredRangeQuery rangeQuery) {
        return zrangeWithScores(key, rangeQuery, false);
    }

    /**
     * Pops a member-score pair from the first non-empty sorted set, with the given <code>keys</code>
     * being checked in the order they are provided.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/commands/zmpop/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets.
     * @param modifier The element pop criteria - either {@link ScoreFilter#MIN} or {@link
     *     ScoreFilter#MAX} to pop the member with the lowest/highest score accordingly.
     * @return Command Response - A two-element <code>array</code> containing the key name of the set
     *     from which the element was popped, and a member-score <code>Map</code> of the popped
     *     element.<br>
     *     If no member could be popped, returns <code>null</code>.
     */
    public T zmpop(@NonNull String[] keys, @NonNull ScoreFilter modifier) {
        ArgsArray commandArgs =
                buildArgs(
                        concatenateArrays(
                                new String[] {Integer.toString(keys.length)},
                                keys,
                                new String[] {modifier.toString()}));
        protobufTransaction.addCommands(buildCommand(ZMPop, commandArgs));
        return getThis();
    }

    /**
     * Pops multiple member-score pairs from the first non-empty sorted set, with the given <code>keys
     * </code> being checked in the order they are provided.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/commands/zmpop/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets.
     * @param modifier The element pop criteria - either {@link ScoreFilter#MIN} or {@link
     *     ScoreFilter#MAX} to pop members with the lowest/highest scores accordingly.
     * @param count The number of elements to pop.
     * @return Command Response - A two-element <code>array</code> containing the key name of the set
     *     from which elements were popped, and a member-score <code>Map</code> of the popped
     *     elements.<br>
     *     If no member could be popped, returns <code>null</code>.
     */
    public T zmpop(@NonNull String[] keys, @NonNull ScoreFilter modifier, long count) {
        ArgsArray commandArgs =
                buildArgs(
                        concatenateArrays(
                                new String[] {Integer.toString(keys.length)},
                                keys,
                                new String[] {modifier.toString(), COUNT_REDIS_API, Long.toString(count)}));
        protobufTransaction.addCommands(buildCommand(ZMPop, commandArgs));
        return getThis();
    }

    /**
     * Blocks the connection until it pops and returns a member-score pair from the first non-empty
     * sorted set, with the given <code>keys</code> being checked in the order they are provided.<br>
     * <code>BZMPOP</code> is the blocking variant of {@link #zmpop(String[], ScoreFilter)}.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/commands/bzmpop/">redis.io</a> for more details.
     * @apiNote <code>BZMPOP</code> is a client blocking command, see <a
     *     href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *     Commands</a> for more details and best practices.
     * @param keys The keys of the sorted sets.
     * @param modifier The element pop criteria - either {@link ScoreFilter#MIN} or {@link
     *     ScoreFilter#MAX} to pop members with the lowest/highest scores accordingly.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @return Command Response - A two-element <code>array</code> containing the key name of the set
     *     from which an element was popped, and a member-score <code>Map</code> of the popped
     *     elements.<br>
     *     If no member could be popped and the timeout expired, returns <code>null</code>.
     */
    public T bzmpop(@NonNull String[] keys, @NonNull ScoreFilter modifier, double timeout) {
        ArgsArray commandArgs =
                buildArgs(
                        concatenateArrays(
                                new String[] {Double.toString(timeout), Integer.toString(keys.length)},
                                keys,
                                new String[] {modifier.toString()}));
        protobufTransaction.addCommands(buildCommand(BZMPop, commandArgs));
        return getThis();
    }

    /**
     * Blocks the connection until it pops and returns multiple member-score pairs from the first
     * non-empty sorted set, with the given <code>keys</code> being checked in the order they are
     * provided.<br>
     * <code>BZMPOP</code> is the blocking variant of {@link #zmpop(String[], ScoreFilter, long)}.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/commands/bzmpop/">redis.io</a> for more details.
     * @apiNote <code>BZMPOP</code> is a client blocking command, see <a
     *     href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *     Commands</a> for more details and best practices.
     * @param keys The keys of the sorted sets.
     * @param modifier The element pop criteria - either {@link ScoreFilter#MIN} or {@link
     *     ScoreFilter#MAX} to pop members with the lowest/highest scores accordingly.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @param count The number of elements to pop.
     * @return Command Response - A two-element <code>array</code> containing the key name of the set
     *     from which elements were popped, and a member-score <code>Map</code> of the popped
     *     elements.<br>
     *     If no members could be popped and the timeout expired, returns <code>null</code>.
     */
    public T bzmpop(
            @NonNull String[] keys, @NonNull ScoreFilter modifier, double timeout, long count) {
        ArgsArray commandArgs =
                buildArgs(
                        concatenateArrays(
                                new String[] {Double.toString(timeout), Integer.toString(keys.length)},
                                keys,
                                new String[] {modifier.toString(), COUNT_REDIS_API, Long.toString(count)}));
        protobufTransaction.addCommands(buildCommand(BZMPop, commandArgs));
        return getThis();
    }

    /**
     * Adds all elements to the HyperLogLog data structure stored at the specified <code>key</code>.
     * <br>
     * Creates a new structure if the <code>key</code> does not exist.
     *
     * <p>When no <code>elements</code> are provided, and <code>key</code> exists and is a
     * HyperLogLog, then no operation is performed. If <code>key</code> does not exist, then the
     * HyperLogLog structure is created.
     *
     * @see <a href="https://redis.io/commands/pfadd/">redis.io</a> for details.
     * @param key The <code>key</code> of the HyperLogLog data structure to add elements into.
     * @param elements An array of members to add to the HyperLogLog stored at <code>key</code>.
     * @return Command Response - If the HyperLogLog is newly created, or if the HyperLogLog
     *     approximated cardinality is altered, then returns <code>1</code>. Otherwise, returns <code>
     *     0</code>.
     */
    public T pfadd(@NonNull String key, @NonNull String[] elements) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(elements, key));
        protobufTransaction.addCommands(buildCommand(PfAdd, commandArgs));
        return getThis();
    }

    /**
     * Estimates the cardinality of the data stored in a HyperLogLog structure for a single key or
     * calculates the combined cardinality of multiple keys by merging their HyperLogLogs temporarily.
     *
     * @see <a href="https://redis.io/commands/pfcount/">redis.io</a> for details.
     * @param keys The keys of the HyperLogLog data structures to be analyzed.
     * @return Command Response - The approximated cardinality of given HyperLogLog data structures.
     *     <br>
     *     The cardinality of a key that does not exist is <code>0</code>.
     */
    public T pfcount(@NonNull String[] keys) {
        ArgsArray commandArgs = buildArgs(keys);
        protobufTransaction.addCommands(buildCommand(PfCount, commandArgs));
        return getThis();
    }

    /**
     * Merges multiple HyperLogLog values into a unique value.<br>
     * If the destination variable exists, it is treated as one of the source HyperLogLog data sets,
     * otherwise a new HyperLogLog is created.
     *
     * @see <a href="https://redis.io/commands/pfmerge/">redis.io</a> for details.
     * @param destination The key of the destination HyperLogLog where the merged data sets will be
     *     stored.
     * @param sourceKeys The keys of the HyperLogLog structures to be merged.
     * @return Command Response - <code>OK</code>.
     */
    public T pfmerge(@NonNull String destination, @NonNull String[] sourceKeys) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(sourceKeys, destination));
        protobufTransaction.addCommands(buildCommand(PfMerge, commandArgs));
        return getThis();
    }

    /**
     * Returns the internal encoding for the Redis object stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/object-encoding/">redis.io</a> for details.
     * @param key The <code>key</code> of the object to get the internal encoding of.
     * @return Command response - If <code>key</code> exists, returns the internal encoding of the
     *     object stored at <code>key</code> as a <code>String</code>. Otherwise, return <code>null
     *     </code>.
     */
    public T objectEncoding(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(ObjectEncoding, commandArgs));
        return getThis();
    }

    /**
     * Returns the logarithmic access frequency counter of a Redis object stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/object-freq/">redis.io</a> for details.
     * @param key The <code>key</code> of the object to get the logarithmic access frequency counter
     *     of.
     * @return Command response - If <code>key</code> exists, returns the logarithmic access frequency
     *     counter of the object stored at <code>key</code> as a <code>Long</code>. Otherwise, returns
     *     <code>null</code>.
     */
    public T objectFreq(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(ObjectFreq, commandArgs));
        return getThis();
    }

    /**
     * Returns the time in seconds since the last access to the value stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/object-idletime/">redis.io</a> for details.
     * @param key The <code>key</code> of the object to get the idle time of.
     * @return Command response - If <code>key</code> exists, returns the idle time in seconds.
     *     Otherwise, returns <code>null</code>.
     */
    public T objectIdletime(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(ObjectIdleTime, commandArgs));
        return getThis();
    }

    /**
     * Returns the reference count of the object stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/object-refcount/">redis.io</a> for details.
     * @param key The <code>key</code> of the object to get the reference count of.
     * @return Command response - If <code>key</code> exists, returns the reference count of the
     *     object stored at <code>key</code> as a <code>Long</code>. Otherwise, returns <code>null
     *     </code>.
     */
    public T objectRefcount(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(ObjectRefCount, commandArgs));
        return getThis();
    }

    /**
     * Updates the last access time of specified <code>keys</code>.
     *
     * @see <a href="https://redis.io/commands/touch/">redis.io</a> for details.
     * @param keys The keys to update last access time.
     * @return Command Response - The number of keys that were updated.
     */
    public T touch(@NonNull String[] keys) {
        ArgsArray commandArgs = buildArgs(keys);
        protobufTransaction.addCommands(buildCommand(Touch, commandArgs));
        return getThis();
    }

    /**
     * Copies the value stored at the <code>source</code> to the <code>destination</code> key. When
     * <code>replace</code> is true, removes the <code>destination</code> key first if it already
     * exists, otherwise performs no action.
     *
     * @since Redis 6.2.0 and above.
     * @see <a href="https://redis.io/commands/copy/">redis.io</a> for details.
     * @param source The key to the source value.
     * @param destination The key where the value should be copied to.
     * @param replace If the destination key should be removed before copying the value to it.
     * @return Command Response - <code>1L</code> if <code>source</code> was copied, <code>0L</code>
     *     if <code>source</code> was not copied.
     */
    public T copy(@NonNull String source, @NonNull String destination, boolean replace) {
        String[] args = new String[] {source, destination};
        if (replace) {
            args = ArrayUtils.add(args, REPLACE_REDIS_API);
        }
        ArgsArray commandArgs = buildArgs(args);
        protobufTransaction.addCommands(buildCommand(Copy, commandArgs));
        return getThis();
    }

    /**
     * Copies the value stored at the <code>source</code> to the <code>destination</code> key if the
     * <code>destination</code> key does not yet exist.
     *
     * @since Redis 6.2.0 and above.
     * @see <a href="https://redis.io/commands/copy/">redis.io</a> for details.
     * @param source The key to the source value.
     * @param destination The key where the value should be copied to.
     * @return Command Response - <code>true</code> if <code>source</code> was copied, <code>false
     *     </code> if <code>source</code> was not copied.
     */
    public T copy(@NonNull String source, @NonNull String destination) {
        return copy(source, destination, false);
    }

    /**
     * Counts the number of set bits (population counting) in a string stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/bitcount/">valkey.io</a> for details.
     * @param key The key for the string to count the set bits of.
     * @return Command Response - The number of set bits in the string. Returns zero if the key is
     *     missing as it is treated as an empty string.
     */
    public T bitcount(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(BitCount, commandArgs));
        return getThis();
    }

    /**
     * Counts the number of set bits (population counting) in a string stored at <code>key</code>. The
     * offsets <code>start</code> and <code>end</code> are zero-based indexes, with <code>0</code>
     * being the first element of the list, <code>1</code> being the next element and so on. These
     * offsets can also be negative numbers indicating offsets starting at the end of the list, with
     * <code>-1</code> being the last element of the list, <code>-2</code> being the penultimate, and
     * so on.
     *
     * @see <a href="https://valkey.io/commands/bitcount/">valkey.io</a> for details.
     * @param key The key for the string to count the set bits of.
     * @param start The starting byte offset.
     * @param end The ending byte offset.
     * @return Command Response - The number of set bits in the string byte interval specified by
     *     <code>start</code> and <code>end</code>. Returns zero if the key is missing as it is
     *     treated as an empty string.
     */
    public T bitcount(@NonNull String key, long start, long end) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(start), Long.toString(end));

        protobufTransaction.addCommands(buildCommand(BitCount, commandArgs));
        return getThis();
    }

    /**
     * Counts the number of set bits (population counting) in a string stored at <code>key</code>. The
     * offsets <code>start</code> and <code>end</code> are zero-based indexes, with <code>0</code>
     * being the first element of the list, <code>1</code> being the next element and so on. These
     * offsets can also be negative numbers indicating offsets starting at the end of the list, with
     * <code>-1</code> being the last element of the list, <code>-2</code> being the penultimate, and
     * so on.
     *
     * @since Redis 7.0 and above
     * @see <a href="https://valkey.io/commands/bitcount/">valkey.io</a> for details.
     * @param key The key for the string to count the set bits of.
     * @param start The starting offset.
     * @param end The ending offset.
     * @param options The index offset type. Could be either {@link BitmapIndexType#BIT} or {@link
     *     BitmapIndexType#BYTE}.
     * @return Command Response - The number of set bits in the string interval specified by <code>
     *     start</code>, <code>end</code>, and <code>options</code>. Returns zero if the key is
     *     missing as it is treated as an empty string.
     */
    public T bitcount(@NonNull String key, long start, long end, @NonNull BitmapIndexType options) {
        ArgsArray commandArgs =
                buildArgs(key, Long.toString(start), Long.toString(end), options.toString());

        protobufTransaction.addCommands(buildCommand(BitCount, commandArgs));
        return getThis();
    }

    /**
     * Adds geospatial members with their positions to the specified sorted set stored at <code>key
     * </code>.<br>
     * If a member is already a part of the sorted set, its position is updated.
     *
     * @see <a href="https://redis.io/commands/geoadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersToGeospatialData A mapping of member names to their corresponding positions - see
     *     {@link GeospatialData}. The command will report an error when the user attempts to index
     *     coordinates outside the specified ranges.
     * @param options The GeoAdd options - see {@link GeoAddOptions}
     * @return Command Response - The number of elements added to the sorted set. If <code>changed
     *     </code> is set to <code>true</code> in the options, returns the number of elements updated
     *     in the sorted set.
     */
    public T geoadd(
            @NonNull String key,
            @NonNull Map<String, GeospatialData> membersToGeospatialData,
            @NonNull GeoAddOptions options) {
        ArgsArray commandArgs =
                buildArgs(
                        concatenateArrays(
                                new String[] {key}, options.toArgs(), mapGeoDataToArray(membersToGeospatialData)));
        protobufTransaction.addCommands(buildCommand(GeoAdd, commandArgs));
        return getThis();
    }

    /**
     * Adds geospatial members with their positions to the specified sorted set stored at <code>key
     * </code>.<br>
     * If a member is already a part of the sorted set, its position is updated.<br>
     * To perform a <code>geoadd</code> operation while specifying optional parameters, use {@link
     * #geoadd(String, Map, GeoAddOptions)}.
     *
     * @see <a href="https://redis.io/commands/geoadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersToGeospatialData A mapping of member names to their corresponding positions - see
     *     {@link GeospatialData}. The command will report an error when the user attempts to index
     *     coordinates outside the specified ranges.
     * @return Command Response - The number of elements added to the sorted set.
     */
    public T geoadd(
            @NonNull String key, @NonNull Map<String, GeospatialData> membersToGeospatialData) {
        return geoadd(key, membersToGeospatialData, new GeoAddOptions(false));
    }

    /**
     * Returns the positions (longitude,latitude) of all the specified <code>members</code> of the
     * geospatial index represented by the sorted set at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/geopos">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param members The members for which to get the positions.
     * @return Command Response - A 2D <code>array</code> which represent positions (longitude and
     *     latitude) corresponding to the given members. If a member does not exist, its position will
     *     be <code>null</code>.
     */
    public T geopos(@NonNull String key, @NonNull String[] members) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(members, key));
        protobufTransaction.addCommands(buildCommand(GeoPos, commandArgs));
        return getThis();
    }

    /**
     * Returns the distance between <code>member1</code> and <code>member2</code> saved in the
     * geospatial index stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/geodist">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member1 The name of the first member.
     * @param member2 The name of the second member.
     * @param geoUnit The unit of distance measurement - see {@link GeoUnit}.
     * @return Command Response - The distance between <code>member1</code> and <code>member2</code>.
     *     If one or both members do not exist or if the key does not exist returns <code>null</code>.
     */
    public <ArgType> T geodist(
            @NonNull ArgType key,
            @NonNull ArgType member1,
            @NonNull ArgType member2,
            @NonNull GeoUnit geoUnit) {
        protobufTransaction.addCommands(
                buildCommand(
                        GeoDist,
                        newArgsBuilder().add(key).add(member1).add(member2).add(geoUnit.getValkeyAPI())));
        return getThis();
    }

    /**
     * Returns the distance between <code>member1</code> and <code>member2</code> saved in the
     * geospatial index stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/geodist">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member1 The name of the first member.
     * @param member2 The name of the second member.
     * @return Command Response - The distance between <code>member1</code> and <code>member2</code>.
     *     If one or both members do not exist or if the key does not exist returns <code>null</code>.
     *     The default unit is {@see GeoUnit#METERS}.
     */
    public T geodist(@NonNull String key, @NonNull String member1, @NonNull String member2) {
        ArgsArray commandArgs = buildArgs(key, member1, member2);
        protobufTransaction.addCommands(buildCommand(GeoDist, commandArgs));
        return getThis();
    }

    /**
     * Returns the <code>GeoHash</code> strings representing the positions of all the specified <code>
     * members</code> in the sorted set stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/geohash">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param members The array of members whose <code>GeoHash</code> strings are to be retrieved.
     * @return Command Response - An array of <code>GeoHash</code> strings representing the positions
     *     of the specified members stored at <code>key</code>. If a member does not exist in the
     *     sorted set, a <code>null</code> value is returned for that member.
     */
    public T geohash(@NonNull String key, @NonNull String[] members) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(members, key));
        protobufTransaction.addCommands(buildCommand(GeoHash, commandArgs));
        return getThis();
    }

    /**
     * Loads a library to Redis.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-load/">redis.io</a> for details.
     * @param libraryCode The source code that implements the library.
     * @param replace Whether the given library should overwrite a library with the same name if it
     *     already exists.
     * @return Command Response - The library name that was loaded.
     */
    public T functionLoad(@NonNull String libraryCode, boolean replace) {
        ArgsArray commandArgs =
                replace ? buildArgs(REPLACE.toString(), libraryCode) : buildArgs(libraryCode);
        protobufTransaction.addCommands(buildCommand(FunctionLoad, commandArgs));
        return getThis();
    }

    /**
     * Returns information about the functions and libraries.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-list/">redis.io</a> for details.
     * @param withCode Specifies whether to request the library code from the server or not.
     * @return Command Response - Info about all libraries and their functions.
     */
    public T functionList(boolean withCode) {
        ArgsArray commandArgs = withCode ? buildArgs(WITH_CODE_REDIS_API) : emptyArgs();
        protobufTransaction.addCommands(buildCommand(FunctionList, commandArgs));
        return getThis();
    }

    /**
     * Returns information about the functions and libraries.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-list/">redis.io</a> for details.
     * @param libNamePattern A wildcard pattern for matching library names.
     * @param withCode Specifies whether to request the library code from the server or not.
     * @return Command Response - Info about queried libraries and their functions.
     */
    public T functionList(@NonNull String libNamePattern, boolean withCode) {
        ArgsArray commandArgs =
                withCode
                        ? buildArgs(LIBRARY_NAME_REDIS_API, libNamePattern, WITH_CODE_REDIS_API)
                        : buildArgs(LIBRARY_NAME_REDIS_API, libNamePattern);
        protobufTransaction.addCommands(buildCommand(FunctionList, commandArgs));
        return getThis();
    }

    /**
     * Invokes a previously loaded function.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/fcall/">redis.io</a> for details.
     * @param function The function name.
     * @param keys An <code>array</code> of key arguments accessed by the function. To ensure the
     *     correct execution of functions, both in standalone and clustered deployments, all names of
     *     keys that a function accesses must be explicitly provided as <code>keys</code>.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @return Command Response - The invoked function's return value.
     */
    public T fcall(@NonNull String function, @NonNull String[] keys, @NonNull String[] arguments) {
        ArgsArray commandArgs =
                buildArgs(
                        concatenateArrays(
                                new String[] {function, Long.toString(keys.length)}, keys, arguments));
        protobufTransaction.addCommands(buildCommand(FCall, commandArgs));
        return getThis();
    }

    /**
     * Invokes a previously loaded read-only function.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/fcall/">redis.io</a> for details.
     * @param function The function name.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @return Command Response - The invoked function's return value.
     */
    public T fcall(@NonNull String function, @NonNull String[] arguments) {
        return fcall(function, new String[0], arguments);
    }

    /**
     * Invokes a previously loaded read-only function.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/fcall_ro/">redis.io</a> for details.
     * @param function The function name.
     * @param keys An <code>array</code> of key arguments accessed by the function. To ensure the
     *     correct execution of functions, both in standalone and clustered deployments, all names of
     *     keys that a function accesses must be explicitly provided as <code>keys</code>.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @return Command Response - The invoked function's return value.
     */
    public T fcallReadOnly(
            @NonNull String function, @NonNull String[] keys, @NonNull String[] arguments) {
        ArgsArray commandArgs =
                buildArgs(
                        concatenateArrays(
                                new String[] {function, Long.toString(keys.length)}, keys, arguments));
        protobufTransaction.addCommands(buildCommand(FCallReadOnly, commandArgs));
        return getThis();
    }

    /**
     * Invokes a previously loaded function.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/fcall_ro/">redis.io</a> for details.
     * @param function The function name.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @return Command Response - The invoked function's return value.
     */
    public T fcallReadOnly(@NonNull String function, @NonNull String[] arguments) {
        return fcallReadOnly(function, new String[0], arguments);
    }

    /**
     * Returns information about the function that's currently running and information about the
     * available execution engines.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-stats/">redis.io</a> for details.
     * @return Command Response - A <code>Map</code> with two keys:
     *     <ul>
     *       <li><code>running_script</code> with information about the running script.
     *       <li><code>engines</code> with information about available engines and their stats.
     *     </ul>
     */
    public T functionStats() {
        protobufTransaction.addCommands(buildCommand(FunctionStats));
        return getThis();
    }

    /**
     * Sets or clears the bit at <code>offset</code> in the string value stored at <code>key</code>.
     * The <code>offset</code> is a zero-based index, with <code>0</code> being the first element of
     * the list, <code>1</code> being the next element, and so on. The <code>offset</code> must be
     * less than <code>2^32</code> and greater than or equal to <code>0</code>. If a key is
     * non-existent then the bit at <code>offset</code> is set to <code>value</code> and the preceding
     * bits are set to <code>0</code>.
     *
     * @see <a href="https://valkey.io/commands/setbit/">valkey.io</a> for details.
     * @param key The key of the string.
     * @param offset The index of the bit to be set.
     * @param value The bit value to set at <code>offset</code>. The value must be <code>0</code> or
     *     <code>1</code>.
     * @return Command Response - The bit value that was previously stored at <code>offset</code>.
     */
    public T setbit(@NonNull String key, long offset, long value) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(offset), Long.toString(value));
        protobufTransaction.addCommands(buildCommand(SetBit, commandArgs));
        return getThis();
    }

    /**
     * Returns the bit value at <code>offset</code> in the string value stored at <code>key</code>.
     * <code>offset</code> should be greater than or equal to zero.
     *
     * @see <a href="https://valkey.io/commands/getbit/">valkey.io</a> for details.
     * @param key The key of the string.
     * @param offset The index of the bit to return.
     * @return Command Response - The bit at offset of the string. Returns zero if the key is empty or
     *     if the positive <code>offset</code> exceeds the length of the string.
     */
    public T getbit(@NonNull String key, long offset) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(offset));
        protobufTransaction.addCommands(buildCommand(GetBit, commandArgs));
        return getThis();
    }

    /**
     * Blocks the connection until it pops one or more elements from the first non-empty list from the
     * provided <code>keys</code>. <code>BLMPOP</code> is the blocking variant of {@link
     * #lmpop(String[], ListDirection, Long)}.
     *
     * @apiNote <code>BLMPOP</code> is a client blocking command, see <a
     *     href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *     Commands</a> for more details and best practices.
     * @since Redis 7.0 and above.
     * @see <a href="https://valkey.io/commands/blmpop/">valkey.io</a> for details.
     * @param keys The list of provided <code>key</code> names.
     * @param direction The direction based on which elements are popped from - see {@link
     *     ListDirection}.
     * @param count The maximum number of popped elements.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @return Command Response - A <code>Map</code> of <code>key</code> names arrays of popped
     *     elements.<br>
     *     If no member could be popped and the timeout expired, returns <code>null</code>.
     */
    public T blmpop(
            @NonNull String[] keys,
            @NonNull ListDirection direction,
            @NonNull Long count,
            double timeout) {
        ArgsArray commandArgs =
                buildArgs(
                        concatenateArrays(
                                new String[] {Double.toString(timeout), Long.toString(keys.length)},
                                keys,
                                new String[] {
                                    direction.toString(), COUNT_FOR_LIST_REDIS_API, Long.toString(count)
                                }));
        protobufTransaction.addCommands(buildCommand(BLMPop, commandArgs));
        return getThis();
    }

    /**
     * Blocks the connection until it pops one element from the first non-empty list from the provided
     * <code>keys</code>. <code>BLMPOP</code> is the blocking variant of {@link #lmpop(String[],
     * ListDirection)}.
     *
     * @apiNote <code>BLMPOP</code> is a client blocking command, see <a
     *     href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *     Commands</a> for more details and best practices.
     * @since Redis 7.0 and above.
     * @see <a href="https://valkey.io/commands/lmpop/">valkey.io</a> for details.
     * @param keys The list of provided <code>key</code> names.
     * @param direction The direction based on which elements are popped from - see {@link
     *     ListDirection}.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @return Command Response - A <code>Map</code> of <code>key</code> names arrays of popped
     *     elements.<br>
     *     If no member could be popped and the timeout expired, returns <code>null</code>.
     */
    public T blmpop(@NonNull String[] keys, @NonNull ListDirection direction, double timeout) {
        ArgsArray commandArgs =
                buildArgs(
                        concatenateArrays(
                                new String[] {Double.toString(timeout), Long.toString(keys.length)},
                                keys,
                                new String[] {direction.toString()}));
        protobufTransaction.addCommands(buildCommand(BLMPop, commandArgs));
        return getThis();
    }

    /**
     * Returns the position of the first bit matching the given <code>bit</code> value.
     *
     * @see <a href="https://valkey.io/commands/bitpos/">valkey.io</a> for details.
     * @param key The key of the string.
     * @param bit The bit value to match. The value must be <code>0</code> or <code>1</code>.
     * @return Command Response - The position of the first occurrence matching <code>bit</code> in
     *     the binary value of the string held at <code>key</code>. If <code>bit</code> is not found,
     *     a <code>-1</code> is returned.
     */
    public T bitpos(@NonNull String key, long bit) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(bit));
        protobufTransaction.addCommands(buildCommand(BitPos, commandArgs));
        return getThis();
    }

    /**
     * Returns the position of the first bit matching the given <code>bit</code> value. The offset
     * <code>start</code> is a zero-based index, with <code>0</code> being the first byte of the list,
     * <code>1</code> being the next byte and so on. These offsets can also be negative numbers
     * indicating offsets starting at the end of the list, with <code>-1</code> being the last byte of
     * the list, <code>-2</code> being the penultimate, and so on.
     *
     * @see <a href="https://valkey.io/commands/bitpos/">valkey.io</a> for details.
     * @param key The key of the string.
     * @param bit The bit value to match. The value must be <code>0</code> or <code>1</code>.
     * @param start The starting offset.
     * @return Command Response - The position of the first occurrence beginning at the <code>start
     *     </code> offset of the <code>bit</code> in the binary value of the string held at <code>key
     *     </code>. If <code>bit</code> is not found, a <code>-1</code> is returned.
     */
    public T bitpos(@NonNull String key, long bit, long start) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(bit), Long.toString(start));
        protobufTransaction.addCommands(buildCommand(BitPos, commandArgs));
        return getThis();
    }

    /**
     * Returns the position of the first bit matching the given <code>bit</code> value. The offsets
     * <code>start</code> and <code>end</code> are zero-based indexes, with <code>0</code> being the
     * first byte of the list, <code>1</code> being the next byte and so on. These offsets can also be
     * negative numbers indicating offsets starting at the end of the list, with <code>-1</code> being
     * the last byte of the list, <code>-2</code> being the penultimate, and so on.
     *
     * @see <a href="https://valkey.io/commands/bitpos/">valkey.io</a> for details.
     * @param key The key of the string.
     * @param bit The bit value to match. The value must be <code>0</code> or <code>1</code>.
     * @param start The starting offset.
     * @param end The ending offset.
     * @return Command Response - The position of the first occurrence from the <code>start</code> to
     *     the <code>end</code> offsets of the <code>bit</code> in the binary value of the string held
     *     at <code>key</code>. If <code>bit</code> is not found, a <code>-1</code> is returned.
     */
    public T bitpos(@NonNull String key, long bit, long start, long end) {
        ArgsArray commandArgs =
                buildArgs(key, Long.toString(bit), Long.toString(start), Long.toString(end));
        protobufTransaction.addCommands(buildCommand(BitPos, commandArgs));
        return getThis();
    }

    /**
     * Returns the position of the first bit matching the given <code>bit</code> value. The offset
     * <code>offsetType</code> specifies whether the offset is a BIT or BYTE. If BIT is specified,
     * <code>start==0</code> and <code>end==2</code> means to look at the first three bits. If BYTE is
     * specified, <code>start==0</code> and <code>end==2</code> means to look at the first three bytes
     * The offsets are zero-based indexes, with <code>0</code> being the first element of the list,
     * <code>1</code> being the next, and so on. These offsets can also be negative numbers indicating
     * offsets starting at the end of the list, with <code>-1</code> being the last element of the
     * list, <code>-2</code> being the penultimate, and so on.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://valkey.io/commands/bitpos/">valkey.io</a> for details.
     * @param key The key of the string.
     * @param bit The bit value to match. The value must be <code>0</code> or <code>1</code>.
     * @param start The starting offset.
     * @param end The ending offset.
     * @param offsetType The index offset type. Could be either {@link BitmapIndexType#BIT} or {@link
     *     BitmapIndexType#BYTE}.
     * @return Command Response - The position of the first occurrence from the <code>start</code> to
     *     the <code>end</code> offsets of the <code>bit</code> in the binary value of the string held
     *     at <code>key</code>. If <code>bit</code> is not found, a <code>-1</code> is returned.
     */
    public T bitpos(
            @NonNull String key, long bit, long start, long end, @NonNull BitmapIndexType offsetType) {
        ArgsArray commandArgs =
                buildArgs(
                        key,
                        Long.toString(bit),
                        Long.toString(start),
                        Long.toString(end),
                        offsetType.toString());

        protobufTransaction.addCommands(buildCommand(BitPos, commandArgs));
        return getThis();
    }

    /**
     * Perform a bitwise operation between multiple keys (containing string values) and store the
     * result in the <code>destination</code>.
     *
     * @see <a href="https://valkey.io/commands/bitop/">valkey.io</a> for details.
     * @param bitwiseOperation The bitwise operation to perform.
     * @param destination The key that will store the resulting string.
     * @param keys The list of keys to perform the bitwise operation on.
     * @return Command Response - The size of the string stored in <code>destination</code>.
     */
    public T bitop(
            @NonNull BitwiseOperation bitwiseOperation,
            @NonNull String destination,
            @NonNull String[] keys) {
        ArgsArray commandArgs =
                buildArgs(concatenateArrays(new String[] {bitwiseOperation.toString(), destination}, keys));

        protobufTransaction.addCommands(buildCommand(BitOp, commandArgs));
        return getThis();
    }

    /**
     * Pops one or more elements from the first non-empty list from the provided <code>keys
     * </code>.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://valkey.io/commands/lmpop/">valkey.io</a> for details.
     * @param keys An array of keys to lists.
     * @param direction The direction based on which elements are popped from - see {@link
     *     ListDirection}.
     * @param count The maximum number of popped elements.
     * @return Command Response - A <code>Map</code> of <code>key</code> name mapped arrays of popped
     *     elements.
     */
    public T lmpop(@NonNull String[] keys, @NonNull ListDirection direction, @NonNull Long count) {
        ArgsArray commandArgs =
                buildArgs(
                        concatenateArrays(
                                new String[] {Long.toString(keys.length)},
                                keys,
                                new String[] {
                                    direction.toString(), COUNT_FOR_LIST_REDIS_API, Long.toString(count)
                                }));
        protobufTransaction.addCommands(buildCommand(LMPop, commandArgs));
        return getThis();
    }

    /**
     * Pops one element from the first non-empty list from the provided <code>keys</code>.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://valkey.io/commands/lmpop/">valkey.io</a> for details.
     * @param keys An array of keys to lists.
     * @param direction The direction based on which elements are popped from - see {@link
     *     ListDirection}.
     * @return Command Response - A <code>Map</code> of <code>key</code> name mapped array of the
     *     popped element.
     */
    public T lmpop(@NonNull String[] keys, @NonNull ListDirection direction) {
        ArgsArray commandArgs =
                buildArgs(
                        concatenateArrays(
                                new String[] {Long.toString(keys.length)},
                                keys,
                                new String[] {direction.toString()}));
        protobufTransaction.addCommands(buildCommand(LMPop, commandArgs));
        return getThis();
    }

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
     * @return Command Response - <code>OK</code>.
     */
    public T lset(@NonNull String key, long index, @NonNull String element) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(index), element);
        protobufTransaction.addCommands(buildCommand(LSet, commandArgs));
        return getThis();
    }

    /**
     * Atomically pops and removes the left/right-most element to the list stored at <code>source
     * </code> depending on <code>wherefrom</code>, and pushes the element at the first/last element
     * of the list stored at <code>destination</code> depending on <code>wherefrom</code>.
     *
     * @since Redis 6.2.0 and above.
     * @see <a href="https://valkey.io/commands/lmove/">valkey.io</a> for details.
     * @param source The key to the source list.
     * @param destination The key to the destination list.
     * @param wherefrom The {@link ListDirection} the element should be removed from.
     * @param whereto The {@link ListDirection} the element should be added to.
     * @return Command Response - The popped element or <code>null</code> if <code>source</code> does
     *     not exist.
     */
    public T lmove(
            @NonNull String source,
            @NonNull String destination,
            @NonNull ListDirection wherefrom,
            @NonNull ListDirection whereto) {
        ArgsArray commandArgs =
                buildArgs(source, destination, wherefrom.toString(), whereto.toString());
        protobufTransaction.addCommands(buildCommand(LMove, commandArgs));
        return getThis();
    }

    /**
     * Blocks the connection until it atomically pops and removes the left/right-most element to the
     * list stored at <code>source</code> depending on <code>wherefrom</code>, and pushes the element
     * at the first/last element of the list stored at <code>destination</code> depending on <code>
     * wherefrom</code>.<br>
     * <code>BLMove</code> is the blocking variant of {@link #lmove(String, String, ListDirection,
     * ListDirection)}.
     *
     * @since Redis 6.2.0 and above.
     * @apiNote <code>BLMove</code> is a client blocking command, see <a
     *     href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *     Commands</a> for more details and best practices.
     * @see <a href="https://valkey.io/commands/blmove/">valkey.io</a> for details.
     * @param source The key to the source list.
     * @param destination The key to the destination list.
     * @param wherefrom The {@link ListDirection} the element should be removed from.
     * @param whereto The {@link ListDirection} the element should be added to.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @return Command Response - The popped element or <code>null</code> if <code>source</code> does
     *     not exist or if the operation timed-out.
     */
    public T blmove(
            @NonNull String source,
            @NonNull String destination,
            @NonNull ListDirection wherefrom,
            @NonNull ListDirection whereto,
            double timeout) {
        ArgsArray commandArgs =
                buildArgs(
                        source,
                        destination,
                        wherefrom.toString(),
                        whereto.toString(),
                        Double.toString(timeout));
        protobufTransaction.addCommands(buildCommand(BLMove, commandArgs));
        return getThis();
    }

    /**
     * Returns a random element from the set value stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/srandmember/">redis.io</a> for details.
     * @param key The key from which to retrieve the set member.
     * @return Command Response - A random element from the set, or <code>null</code> if <code>key
     *     </code> does not exist.
     */
    public T srandmember(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(SRandMember, commandArgs));
        return getThis();
    }

    /**
     * Returns random elements from the set value stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/srandmember/">redis.io</a> for details.
     * @param key The key from which to retrieve the set members.
     * @param count The number of elements to return.<br>
     *     If <code>count</code> is positive, returns unique elements.<br>
     *     If negative, allows for duplicates.<br>
     * @return Command Response - An <code>array</code> of elements from the set, or an empty <code>
     *     array</code> if <code>key</code> does not exist.
     */
    public T srandmember(@NonNull String key, long count) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(count));
        protobufTransaction.addCommands(buildCommand(SRandMember, commandArgs));
        return getThis();
    }

    /**
     * Removes and returns one random member from the set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/spop/">redis.io</a> for details.
     * @param key The key of the set.
     * @return Command Response - The value of the popped member.<br>
     *     If <code>key</code> does not exist, <code>null</code> will be returned.
     */
    public T spop(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(SPop, commandArgs));
        return getThis();
    }

    /**
     * Removes and returns up to <code>count</code> random members from the set stored at <code>key
     * </code>, depending on the set's length.
     *
     * @see <a href="https://redis.io/commands/spop/">redis.io</a> for details.
     * @param key The key of the set.
     * @param count The count of the elements to pop from the set.
     * @return Command Response - A set of popped elements will be returned depending on the set's
     *     length.<br>
     *     If <code>key</code> does not exist, an empty <code>Set</code> will be returned.
     */
    public <ArgType> T spopCount(@NonNull ArgType key, long count) {
        protobufTransaction.addCommands(buildCommand(SPop, newArgsBuilder().add(key).add(count)));
        return getThis();
    }

    /**
     * Reads or modifies the array of bits representing the string that is held at <code>key</code>
     * based on the specified <code>subCommands</code>.
     *
     * @see <a href="https://valkey.io/commands/bitfield/">valkey.io</a> for details.
     * @param key The key of the string.
     * @param subCommands The subCommands to be performed on the binary value of the string at <code>
     *     key</code>, which could be any of the following:
     *     <ul>
     *       <li>{@link BitFieldGet}.
     *       <li>{@link BitFieldSet}.
     *       <li>{@link BitFieldIncrby}.
     *       <li>{@link BitFieldOverflow}.
     *     </ul>
     *
     * @return Command Response - An <code>array</code> of results from the executed subcommands.
     *     <ul>
     *       <li>{@link BitFieldGet} returns the value in {@link Offset} or {@link OffsetMultiplier}.
     *       <li>{@link BitFieldSet} returns the old value in {@link Offset} or {@link
     *           OffsetMultiplier}.
     *       <li>{@link BitFieldIncrby} returns the new value in {@link Offset} or {@link
     *           OffsetMultiplier}.
     *       <li>{@link BitFieldOverflow} determines the behaviour of <code>SET</code> and <code>
     *           INCRBY</code> when an overflow occurs. <code>OVERFLOW</code> does not return a value
     *           and does not contribute a value to the array response.
     *     </ul>
     */
    public T bitfield(@NonNull String key, @NonNull BitFieldSubCommands[] subCommands) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(createBitFieldArgs(subCommands), key));
        protobufTransaction.addCommands(buildCommand(BitField, commandArgs));
        return getThis();
    }

    /**
     * Reads the array of bits representing the string that is held at <code>key</code> based on the
     * specified <code>subCommands</code>.<br>
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * @since Redis 6.0 and above
     * @see <a href="https://valkey.io/commands/bitfield_ro/">valkey.io</a> for details.
     * @param key The key of the string.
     * @param subCommands The <code>GET</code> subCommands to be performed.
     * @return Command Response - An array of results from the <code>GET</code> subcommands.
     */
    public T bitfieldReadOnly(
            @NonNull String key, @NonNull BitFieldReadOnlySubCommands[] subCommands) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(createBitFieldArgs(subCommands), key));
        protobufTransaction.addCommands(buildCommand(BitFieldReadOnly, commandArgs));
        return getThis();
    }

    /**
     * Deletes all function libraries.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-flush/">redis.io</a> for details.
     * @return Command Response - <code>OK</code>.
     */
    public T functionFlush() {
        protobufTransaction.addCommands(buildCommand(FunctionFlush));
        return getThis();
    }

    /**
     * Deletes all function libraries.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-flush/">redis.io</a> for details.
     * @param mode The flushing mode, could be either {@link FlushMode#SYNC} or {@link
     *     FlushMode#ASYNC}.
     * @return Command Response - <code>OK</code>.
     */
    public T functionFlush(@NonNull FlushMode mode) {
        protobufTransaction.addCommands(buildCommand(FunctionFlush, buildArgs(mode.toString())));
        return getThis();
    }

    /**
     * Deletes a library and all its functions.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-delete/">redis.io</a> for details.
     * @param libName The library name to delete.
     * @return Command Response - <code>OK</code>.
     */
    public T functionDelete(@NonNull String libName) {
        protobufTransaction.addCommands(buildCommand(FunctionDelete, buildArgs(libName)));
        return getThis();
    }

    /**
     * Returns the longest common subsequence between strings stored at <code>key1</code> and <code>
     * key2</code>.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1 The key that stores the first string.
     * @param key2 The key that stores the second string.
     * @return Command Response - A <code>String</code> containing the longest common subsequence
     *     between the 2 strings. An empty <code>String</code> is returned if the keys do not exist or
     *     have no common subsequences.
     */
    public T lcs(@NonNull String key1, @NonNull String key2) {
        protobufTransaction.addCommands(buildCommand(LCS, buildArgs(key1, key2)));
        return getThis();
    }

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
     * @return Command Response - The length of the longest common subsequence between the 2 strings.
     */
    public T lcsLen(@NonNull String key1, @NonNull String key2) {
        ArgsArray args = buildArgs(key1, key2, LEN_REDIS_API);
        protobufTransaction.addCommands(buildCommand(LCS, args));
        return getThis();
    }

    /**
     * Publishes message on pubsub channel.
     *
     * @see <a href="https://valkey.io/commands/publish/">redis.io</a> for details.
     * @param channel The channel to publish the message on.
     * @param message The message to publish.
     * @return Command response - The number of clients that received the message.
     */
    public T publish(@NonNull String channel, @NonNull String message) {
        protobufTransaction.addCommands(buildCommand(Publish, buildArgs(channel, message)));
        return getThis();
    }

    /**
     * Gets the union of all the given sets.
     *
     * @see <a href="https://valkey.io/commands/sunion">valkey.io</a> for details.
     * @param keys The keys of the sets.
     * @return Command Response - A set of members which are present in at least one of the given
     *     sets. If none of the sets exist, an empty set will be returned.
     */
    public T sunion(@NonNull String[] keys) {
        protobufTransaction.addCommands(buildCommand(SUnion, buildArgs(keys)));
        return getThis();
    }

    /**
     * Returns the indices and length of the longest common subsequence between strings stored at
     * <code>key1</code> and <code>key2</code>.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1 The key that stores the first string.
     * @param key2 The key that stores the second string.
     * @return Command Response - A <code>Map</code> containing the indices of the longest common
     *     subsequence between the 2 strings and the length of the longest common subsequence. The
     *     resulting map contains two keys, "matches" and "len":
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
    public <ArgType> T lcsIdx(@NonNull ArgType key1, @NonNull ArgType key2) {
        protobufTransaction.addCommands(
                buildCommand(LCS, newArgsBuilder().add(key1).add(key2).add(IDX_COMMAND_STRING)));
        return getThis();
    }

    /**
     * Returns the indices and length of the longest common subsequence between strings stored at
     * <code>key1</code> and <code>key2</code>.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1 The key that stores the first string.
     * @param key2 The key that stores the second string.
     * @param minMatchLen The minimum length of matches to include in the result.
     * @return Command Response - A <code>Map</code> containing the indices of the longest common
     *     subsequence between the 2 strings and the length of the longest common subsequence. The
     *     resulting map contains two keys, "matches" and "len":
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
    public <ArgType> T lcsIdx(@NonNull ArgType key1, @NonNull ArgType key2, long minMatchLen) {
        protobufTransaction.addCommands(
                buildCommand(
                        LCS,
                        newArgsBuilder()
                                .add(key1)
                                .add(key2)
                                .add(IDX_COMMAND_STRING)
                                .add(MINMATCHLEN_COMMAND_STRING)
                                .add(minMatchLen)));
        return getThis();
    }

    /**
     * Returns the indices and length of the longest common subsequence between strings stored at
     * <code>key1</code> and <code>key2</code>.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1 The key that stores the first string.
     * @param key2 The key that stores the second string.
     * @return Command Response - A <code>Map</code> containing the indices of the longest common
     *     subsequence between the 2 strings and the length of the longest common subsequence. The
     *     resulting map contains two keys, "matches" and "len":
     *     <ul>
     *       <li>"len" is mapped to the length of the longest common subsequence between the 2 strings
     *           stored as <code>Long</code>.
     *       <li>"matches" is mapped to a three dimensional <code>Long</code> array that stores pairs
     *           of indices that represent the location of the common subsequences in the strings held
     *           by <code>key1</code> and <code>key2</code>. For example,
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
    public <ArgType> T lcsIdxWithMatchLen(@NonNull ArgType key1, @NonNull ArgType key2) {
        protobufTransaction.addCommands(
                buildCommand(
                        LCS,
                        newArgsBuilder()
                                .add(key1)
                                .add(key2)
                                .add(IDX_COMMAND_STRING)
                                .add(WITHMATCHLEN_COMMAND_STRING)));
        return getThis();
    }

    /**
     * Returns the indices and length of the longest common subsequence between strings stored at
     * <code>key1</code> and <code>key2</code>.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1 The key that stores the first string.
     * @param key2 The key that stores the second string.
     * @param minMatchLen The minimum length of matches to include in the result.
     * @return Command Response - A <code>Map</code> containing the indices of the longest common
     *     subsequence between the 2 strings and the length of the longest common subsequence. The
     *     resulting map contains two keys, "matches" and "len":
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
    public <ArgType> T lcsIdxWithMatchLen(
            @NonNull ArgType key1, @NonNull ArgType key2, long minMatchLen) {
        protobufTransaction.addCommands(
                buildCommand(
                        LCS,
                        newArgsBuilder()
                                .add(key1)
                                .add(key2)
                                .add(IDX_COMMAND_STRING)
                                .add(MINMATCHLEN_COMMAND_STRING)
                                .add(minMatchLen)
                                .add(WITHMATCHLEN_COMMAND_STRING)));
        return getThis();
    }

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and returns the result.
     * <br>
     * The <code>sort</code> command can be used to sort elements based on different criteria and
     * apply transformations on sorted elements.<br>
     * To store the result into a new key, see {@link #sortStore(String, String)}.<br>
     *
     * @param key The key of the list, set, or sorted set to be sorted.
     * @return Command Response - An <code>Array</code> of sorted elements.
     */
    public T sort(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(Sort, commandArgs));
        return getThis();
    }

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and returns the result.
     * <br>
     * The <code>sortReadOnly</code> command can be used to sort elements based on different criteria
     * and apply transformations on sorted elements.
     *
     * @since Redis 7.0 and above.
     * @param key The key of the list, set, or sorted set to be sorted.
     * @return Command Response - An <code>Array</code> of sorted elements.
     */
    public T sortReadOnly(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(SortReadOnly, commandArgs));
        return getThis();
    }

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and stores the result in
     * <code>destination</code>. The <code>sort</code> command can be used to sort elements based on
     * different criteria, apply transformations on sorted elements, and store the result in a new
     * key.<br>
     * To get the sort result without storing it into a key, see {@link #sort(String)} or {@link
     * #sortReadOnly(String)}.
     *
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param destination The key where the sorted result will be stored.
     * @return Command Response - The number of elements in the sorted key stored at <code>destination
     *     </code>.
     */
    public <ArgType> T sortStore(@NonNull ArgType key, @NonNull ArgType destination) {
        protobufTransaction.addCommands(
                buildCommand(Sort, newArgsBuilder().add(key).add(STORE_COMMAND_STRING).add(destination)));
        return getThis();
    }

    /**
     * Returns the members of a sorted set populated with geospatial information using {@link
     * #geoadd(String, Map)}, which are within the borders of the area specified by a given shape.
     *
     * @since Valkey 6.2.0 and above.
     * @see <a href="https://valkey.io/commands/geosearch">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param searchFrom The query's center point options, could be one of:
     *     <ul>
     *       <li>{@link GeoSearchOrigin.MemberOrigin} to use the position of the given existing member
     *           in the sorted set.
     *       <li>{@link GeoSearchOrigin.CoordOrigin} to use the given longitude and latitude
     *           coordinates.
     *     </ul>
     *
     * @param searchBy The query's shape options:
     *     <ul>
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, GeoUnit)} to search inside circular area
     *           according to given radius.
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, double, GeoUnit)} to search inside an
     *           axis-aligned rectangle, determined by height and width.
     *     </ul>
     *
     * @return Command Response - An <code>array</code> of matched member names.
     */
    public <ArgType> T geosearch(
            @NonNull ArgType key,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy) {
        protobufTransaction.addCommands(
                buildCommand(
                        GeoSearch, newArgsBuilder().add(key).add(searchFrom.toArgs()).add(searchBy.toArgs())));
        return getThis();
    }

    /**
     * Returns the members of a sorted set populated with geospatial information using {@link
     * #geoadd(String, Map)}, which are within the borders of the area specified by a given shape.
     *
     * @since Valkey 6.2.0 and above.
     * @see <a href="https://valkey.io/commands/geosearch">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param searchFrom The query's center point options, could be one of:
     *     <ul>
     *       <li>{@link GeoSearchOrigin.MemberOrigin} to use the position of the given existing member
     *           in the sorted set.
     *       <li>{@link GeoSearchOrigin.CoordOrigin} to use the given longitude and latitude
     *           coordinates.
     *     </ul>
     *
     * @param searchBy The query's shape options:
     *     <ul>
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, GeoUnit)} to search inside circular area
     *           according to given radius.
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, double, GeoUnit)} to search inside an
     *           axis-aligned rectangle, determined by height and width.
     *     </ul>
     *
     * @param resultOptions Optional inputs for sorting/limiting the results. See - {@link
     *     GeoSearchResultOptions}
     * @return Command Response - An <code>array</code> of matched member names.
     */
    public <ArgType> T geosearch(
            @NonNull ArgType key,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy,
            @NonNull GeoSearchResultOptions resultOptions) {
        protobufTransaction.addCommands(
                buildCommand(
                        GeoSearch,
                        newArgsBuilder()
                                .add(key)
                                .add(searchFrom.toArgs())
                                .add(searchBy.toArgs())
                                .add(resultOptions.toArgs())));
        return getThis();
    }

    /**
     * Returns the members of a sorted set populated with geospatial information using {@link
     * #geoadd(String, Map)}, which are within the borders of the area specified by a given shape.
     *
     * @since Valkey 6.2.0 and above.
     * @see <a href="https://valkey.io/commands/geosearch">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param searchFrom The query's center point options, could be one of:
     *     <ul>
     *       <li>{@link GeoSearchOrigin.MemberOrigin} to use the position of the given existing member
     *           in the sorted set.
     *       <li>{@link GeoSearchOrigin.CoordOrigin} to use the given longitude and latitude
     *           coordinates.
     *     </ul>
     *
     * @param searchBy The query's shape options:
     *     <ul>
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, GeoUnit)} to search inside circular area
     *           according to given radius.
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, double, GeoUnit)} to search inside an
     *           axis-aligned rectangle, determined by height and width.
     *     </ul>
     *
     * @param options The optional inputs to request additional information.
     * @return Command Response - An array of arrays where each sub-array represents a single item in
     *     the following order:
     *     <ul>
     *       <li>The member (location) name.
     *       <li>The distance from the center as a <code>Double</code>, in the same unit specified for
     *           <code>searchBy</code>.
     *       <li>The geohash of the location as a <code>Long</code>.
     *       <li>The coordinates as a two item <code>array</code> of <code>Double</code>.
     *     </ul>
     */
    public <ArgType> T geosearch(
            @NonNull ArgType key,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy,
            @NonNull GeoSearchOptions options) {
        protobufTransaction.addCommands(
                buildCommand(
                        GeoSearch,
                        newArgsBuilder()
                                .add(key)
                                .add(searchFrom.toArgs())
                                .add(searchBy.toArgs())
                                .add(options.toArgs())));
        return getThis();
    }

    /**
     * Returns the members of a sorted set populated with geospatial information using {@link
     * #geoadd(String, Map)}, which are within the borders of the area specified by a given shape.
     *
     * @since Valkey 6.2.0 and above.
     * @see <a href="https://valkey.io/commands/geosearch">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param searchFrom The query's center point options, could be one of:
     *     <ul>
     *       <li>{@link GeoSearchOrigin.MemberOrigin} to use the position of the given existing member
     *           in the sorted set.
     *       <li>{@link GeoSearchOrigin.CoordOrigin} to use the given longitude and latitude
     *           coordinates.
     *     </ul>
     *
     * @param searchBy The query's shape options:
     *     <ul>
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, GeoUnit)} to search inside circular area
     *           according to given radius.
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, double, GeoUnit)} to search inside an
     *           axis-aligned rectangle, determined by height and width.
     *     </ul>
     *
     * @param options The optional inputs to request additional information.
     * @param resultOptions Optional inputs for sorting/limiting the results. See - {@link
     *     GeoSearchResultOptions}
     * @return Command Response - An array of arrays where each sub-array represents a single item in
     *     the following order:
     *     <ul>
     *       <li>The member (location) name.
     *       <li>The distance from the center as a <code>Double</code>, in the same unit specified for
     *           <code>searchBy</code>.
     *       <li>The geohash of the location as a <code>Long</code>.
     *       <li>The coordinates as a two item <code>array</code> of <code>Double</code>.
     *     </ul>
     */
    public <ArgType> T geosearch(
            @NonNull ArgType key,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy,
            @NonNull GeoSearchOptions options,
            @NonNull GeoSearchResultOptions resultOptions) {
        protobufTransaction.addCommands(
                buildCommand(
                        GeoSearch,
                        newArgsBuilder()
                                .add(key)
                                .add(searchFrom.toArgs())
                                .add(searchBy.toArgs())
                                .add(options.toArgs())
                                .add(resultOptions.toArgs())));
        return getThis();
    }

    /**
     * Searches for members in a sorted set stored at <code>source</code> representing geospatial data
     * within a circular or rectangular area and stores the result in <code>destination</code>. If
     * <code>destination</code> already exists, it is overwritten. Otherwise, a new sorted set will be
     * created. To get the result directly, see `{@link #geosearch(String,
     * GeoSearchOrigin.SearchOrigin, GeoSearchShape)}.
     *
     * @since Valkey 6.2.0 and above.
     * @see <a href="https://valkey.io/commands/geosearch">valkey.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param source The key of the source sorted set.
     * @param searchFrom The query's center point options, could be one of:
     *     <ul>
     *       <li>{@link GeoSearchOrigin.MemberOrigin} to use the position of the given existing member
     *           in the sorted set.
     *       <li>{@link GeoSearchOrigin.CoordOrigin} to use the given longitude and latitude
     *           coordinates.
     *     </ul>
     *
     * @param searchBy The query's shape options:
     *     <ul>
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, GeoUnit)} to search inside circular area
     *           according to given radius.
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, double, GeoUnit)} to search inside an
     *           axis-aligned rectangle, determined by height and width.
     *     </ul>
     *
     * @return Command Response - The number of elements in the resulting set.
     */
    public <ArgType> T geosearchstore(
            @NonNull ArgType destination,
            @NonNull ArgType source,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy) {
        protobufTransaction.addCommands(
                buildCommand(
                        GeoSearchStore,
                        newArgsBuilder()
                                .add(destination)
                                .add(source)
                                .add(searchFrom.toArgs())
                                .add(searchBy.toArgs())));
        return getThis();
    }

    /**
     * Searches for members in a sorted set stored at <code>source</code> representing geospatial data
     * within a circular or rectangular area and stores the result in <code>destination</code>. If
     * <code>destination</code> already exists, it is overwritten. Otherwise, a new sorted set will be
     * created. To get the result directly, see `{@link #geosearch(String,
     * GeoSearchOrigin.SearchOrigin, GeoSearchShape, GeoSearchResultOptions)}.
     *
     * @since Valkey 6.2.0 and above.
     * @see <a href="https://valkey.io/commands/geosearch">valkey.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param source The key of the source sorted set.
     * @param searchFrom The query's center point options, could be one of:
     *     <ul>
     *       <li>{@link GeoSearchOrigin.MemberOrigin} to use the position of the given existing member
     *           in the sorted set.
     *       <li>{@link GeoSearchOrigin.CoordOrigin} to use the given longitude and latitude
     *           coordinates.
     *     </ul>
     *
     * @param searchBy The query's shape options:
     *     <ul>
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, GeoUnit)} to search inside circular area
     *           according to given radius.
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, double, GeoUnit)} to search inside an
     *           axis-aligned rectangle, determined by height and width.
     *     </ul>
     *
     * @param resultOptions Optional inputs for sorting/limiting the results. See - {@link
     *     GeoSearchResultOptions}
     * @return Command Response - The number of elements in the resulting set.
     */
    public <ArgType> T geosearchstore(
            @NonNull ArgType destination,
            @NonNull ArgType source,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy,
            @NonNull GeoSearchResultOptions resultOptions) {
        protobufTransaction.addCommands(
                buildCommand(
                        GeoSearchStore,
                        newArgsBuilder()
                                .add(destination)
                                .add(source)
                                .add(searchFrom.toArgs())
                                .add(searchBy.toArgs())
                                .add(resultOptions.toArgs())));
        return getThis();
    }

    /**
     * Searches for members in a sorted set stored at <code>source</code> representing geospatial data
     * within a circular or rectangular area and stores the result in <code>destination</code>. If
     * <code>destination</code> already exists, it is overwritten. Otherwise, a new sorted set will be
     * created. To get the result directly, see `{@link #geosearch(String,
     * GeoSearchOrigin.SearchOrigin, GeoSearchShape, GeoSearchOptions)}.
     *
     * @since Valkey 6.2.0 and above.
     * @see <a href="https://valkey.io/commands/geosearch">valkey.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param source The key of the source sorted set.
     * @param searchFrom The query's center point options, could be one of:
     *     <ul>
     *       <li>{@link GeoSearchOrigin.MemberOrigin} to use the position of the given existing member
     *           in the sorted set.
     *       <li>{@link GeoSearchOrigin.CoordOrigin} to use the given longitude and latitude
     *           coordinates.
     *     </ul>
     *
     * @param searchBy The query's shape options:
     *     <ul>
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, GeoUnit)} to search inside circular area
     *           according to given radius.
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, double, GeoUnit)} to search inside an
     *           axis-aligned rectangle, determined by height and width.
     *     </ul>
     *
     * @param options The optional inputs to request additional information.
     * @return Command Response - The number of elements in the resulting set.
     */
    public <ArgType> T geosearchstore(
            @NonNull ArgType destination,
            @NonNull ArgType source,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy,
            @NonNull GeoSearchStoreOptions options) {
        protobufTransaction.addCommands(
                buildCommand(
                        GeoSearchStore,
                        newArgsBuilder()
                                .add(destination)
                                .add(source)
                                .add(searchFrom.toArgs())
                                .add(searchBy.toArgs())
                                .add(options.toArgs())));
        return getThis();
    }

    /**
     * Searches for members in a sorted set stored at <code>source</code> representing geospatial data
     * within a circular or rectangular area and stores the result in <code>destination</code>. If
     * <code>destination</code> already exists, it is overwritten. Otherwise, a new sorted set will be
     * created. To get the result directly, see `{@link #geosearch(String,
     * GeoSearchOrigin.SearchOrigin, GeoSearchShape, GeoSearchOptions, GeoSearchResultOptions)}.
     *
     * @since Valkey 6.2.0 and above.
     * @see <a href="https://valkey.io/commands/geosearch">valkey.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param source The key of the source sorted set.
     * @param searchFrom The query's center point options, could be one of:
     *     <ul>
     *       <li>{@link GeoSearchOrigin.MemberOrigin} to use the position of the given existing member
     *           in the sorted set.
     *       <li>{@link GeoSearchOrigin.CoordOrigin} to use the given longitude and latitude
     *           coordinates.
     *     </ul>
     *
     * @param searchBy The query's shape options:
     *     <ul>
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, GeoUnit)} to search inside circular area
     *           according to given radius.
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, double, GeoUnit)} to search inside an
     *           axis-aligned rectangle, determined by height and width.
     *     </ul>
     *
     * @param options The optional inputs to request additional information.
     * @param resultOptions Optional inputs for sorting/limiting the results. See - {@link
     *     GeoSearchResultOptions}
     * @return Command Response - The number of elements in the resulting set.
     */
    public <ArgType> T geosearchstore(
            @NonNull ArgType destination,
            @NonNull ArgType source,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy,
            @NonNull GeoSearchStoreOptions options,
            @NonNull GeoSearchResultOptions resultOptions) {
        protobufTransaction.addCommands(
                buildCommand(
                        GeoSearchStore,
                        newArgsBuilder()
                                .add(destination)
                                .add(source)
                                .add(searchFrom.toArgs())
                                .add(searchBy.toArgs())
                                .add(options.toArgs())
                                .add(resultOptions.toArgs())));
        return getThis();
    }

    /**
     * Iterates incrementally over a set.
     *
     * @see <a href="https://valkey.io/commands/sscan">valkey.io</a> for details.
     * @param key The key of the set.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>"0"
     *     </code> indicates the start of the search.
     * @return Command Response - An <code>Array</code> of <code>Objects</code>. The first element is
     *     always the <code>cursor</code> for the next iteration of results. <code>"0"</code> will be
     *     the <code>cursor</code> returned on the last iteration of the set. The second element is
     *     always an <code>Array</code> of the subset of the set held in <code>key</code>.
     */
    public <ArgType> T sscan(@NonNull ArgType key, @NonNull ArgType cursor) {
        protobufTransaction.addCommands(buildCommand(SScan, buildArgs(key, cursor)));
        return getThis();
    }

    /**
     * Iterates incrementally over a set.
     *
     * @see <a href="https://valkey.io/commands/sscan">valkey.io</a> for details.
     * @param key The key of the set.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>"0"
     *     </code> indicates the start of the search.
     * @param sScanOptions The {@link SScanOptions}.
     * @return Command Response - An <code>Array</code> of <code>Objects</code>. The first element is
     *     always the <code>cursor</code> for the next iteration of results. <code>"0"</code> will be
     *     the <code>cursor</code> returned on the last iteration of the set. The second element is
     *     always an <code>Array</code> of the subset of the set held in <code>key</code>.
     */
    public <ArgType> T sscan(
            @NonNull ArgType key, @NonNull ArgType cursor, @NonNull SScanOptions sScanOptions) {
        protobufTransaction.addCommands(
                buildCommand(SScan, newArgsBuilder().add(key).add(cursor).add(sScanOptions.toArgs())));
        return getThis();
    }

    /**
     * Iterates incrementally over a sorted set.
     *
     * @see <a href="https://valkey.io/commands/zscan">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>"0"
     *     </code> indicates the start of the search.
     * @return Command Response - An <code>Array</code> of <code>Objects</code>. The first element is
     *     always the <code>cursor</code> for the next iteration of results. <code>"0"</code> will be
     *     the <code>cursor</code> returned on the last iteration of the sorted set. The second
     *     element is always an <code>Array</code> of the subset of the sorted set held in <code>key
     *     </code>. The array in the second element is always a flattened series of <code>String
     *     </code> pairs, where the value is at even indices and the score is at odd indices.
     */
    public <ArgType> T zscan(@NonNull ArgType key, @NonNull ArgType cursor) {
        protobufTransaction.addCommands(buildCommand(ZScan, buildArgs(key, cursor)));
        return getThis();
    }

    /**
     * Iterates incrementally over a sorted set.
     *
     * @see <a href="https://valkey.io/commands/zscan">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>"0"
     *     </code> indicates the start of the search.
     * @param zScanOptions The {@link ZScanOptions}.
     * @return Command Response - An <code>Array</code> of <code>Objects</code>. The first element is
     *     always the <code>cursor</code> for the next iteration of results. <code>"0"</code> will be
     *     the <code>cursor</code> returned on the last iteration of the sorted set. The second
     *     element is always an <code>Array</code> of the subset of the sorted set held in <code>key
     *     </code>. The array in the second element is always a flattened series of <code>String
     *     </code> pairs, where the value is at even indices and the score is at odd indices.
     */
    public <ArgType> T zscan(
            @NonNull ArgType key, @NonNull ArgType cursor, @NonNull ZScanOptions zScanOptions) {
        protobufTransaction.addCommands(
                buildCommand(ZScan, newArgsBuilder().add(key).add(cursor).add(zScanOptions.toArgs())));
        return getThis();
    }

    /**
     * Iterates fields of Hash types and their associated values.
     *
     * @see <a href="https://valkey.io/commands/hscan">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>"0"
     *     </code> indicates the start of the search.
     * @return Command Response - An <code>Array</code> of <code>Objects</code>. The first element is
     *     always the <code>cursor</code> for the next iteration of results. <code>"0"</code> will be
     *     the <code>cursor</code> returned on the last iteration of the result. The second element is
     *     always an <code>Array</code> of the subset of the hash held in <code>key</code>. The array
     *     in the second element is always a flattened series of <code>String</code> pairs, where the
     *     key is at even indices and the value is at odd indices.
     */
    public <ArgType> T hscan(@NonNull ArgType key, @NonNull ArgType cursor) {
        protobufTransaction.addCommands(buildCommand(HScan, buildArgs(key, cursor)));
        return getThis();
    }

    /**
     * Iterates fields of Hash types and their associated values.
     *
     * @see <a href="https://valkey.io/commands/hscan">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>"0"
     *     </code> indicates the start of the search.
     * @param hScanOptions The {@link HScanOptions}.
     * @return Command Response - An <code>Array</code> of <code>Objects</code>. The first element is
     *     always the <code>cursor</code> for the next iteration of results. <code>"0"</code> will be
     *     the <code>cursor</code> returned on the last iteration of the result. The second element is
     *     always an <code>Array</code> of the subset of the hash held in <code>key</code>. The array
     *     in the second element is always a flattened series of <code>String</code> pairs, where the
     *     key is at even indices and the value is at odd indices.
     */
    public <ArgType> T hscan(
            @NonNull ArgType key, @NonNull ArgType cursor, @NonNull HScanOptions hScanOptions) {
        protobufTransaction.addCommands(
                buildCommand(HScan, newArgsBuilder().add(key).add(cursor).add(hScanOptions.toArgs())));
        return getThis();
    }

    /**
     * Returns the number of replicas that acknowledged the write commands sent by the current client
     * before this command, both in the case where the specified number of replicas are reached, or
     * when the timeout is reached.
     *
     * @param numreplicas The number of replicas to reach.
     * @param timeout The timeout value specified in milliseconds.
     * @return Command Response - The number of replicas reached by all the writes performed in the
     *     context of the current connection.
     */
    public T wait(long numreplicas, long timeout) {
        protobufTransaction.addCommands(buildCommand(Wait, buildArgs(numreplicas, timeout)));
        return getThis();
    }

    /** Build protobuf {@link Command} object for given command and arguments. */
    protected Command buildCommand(RequestType requestType) {
        return buildCommand(requestType, emptyArgs());
    }

    /** Build protobuf {@link Command} object for given command and arguments. */
    protected Command buildCommand(RequestType requestType, ArgsArray args) {
        return Command.newBuilder().setRequestType(requestType).setArgsArray(args).build();
    }

    /** Build protobuf {@link Command} object for given command and arguments. */
    protected Command buildCommand(RequestType requestType, ArgsBuilder argsBuilder) {
        final Command.Builder builder = Command.newBuilder();
        builder.setRequestType(requestType);
        CommandManager.populateCommandWithArgs(argsBuilder.toArray(), builder);
        return builder.build();
    }

    /** Build protobuf {@link ArgsArray} object for empty arguments. */
    protected ArgsArray emptyArgs() {
        ArgsArray.Builder commandArgs = ArgsArray.newBuilder();
        return commandArgs.build();
    }

    protected <ArgType> ArgsArray.Builder newArgsArrayBuilder(ArgType... args) {
        ArgsArray.Builder builder = ArgsArray.newBuilder();
        for (ArgType arg : args) {
            builder.addArgs(ByteString.copyFrom(GlideString.of(arg).getBytes()));
        }
        return builder;
    }

    /** Build protobuf {@link ArgsArray} object for given arguments. */
    protected <ArgType> ArgsArray buildArgs(ArgType... args) {
        return this.newArgsArrayBuilder(args).build();
    }

    protected ArgsBuilder newArgsBuilder() {
        return new ArgsBuilder();
    }

    protected <ArgType> void checkTypeOrThrow(ArgType arg) {
        if ((arg instanceof String) || (arg instanceof GlideString)) {
            return;
        }
        throw new IllegalArgumentException("Expected String or GlideString");
    }

    protected <ArgType> void checkTypeOrThrow(ArgType[] args) {
        if (args.length == 0) {
            // nothing to check here
            return;
        }

        if ((args[0] instanceof String) || (args[0] instanceof GlideString)) {
            return;
        }
        throw new IllegalArgumentException("Expected String or GlideString");
    }
}
