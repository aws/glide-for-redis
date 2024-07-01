/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static glide.api.commands.GenericBaseCommands.REPLACE_REDIS_API;
import static glide.api.commands.HashBaseCommands.WITH_VALUES_REDIS_API;
import static glide.api.commands.ServerManagementCommands.VERSION_REDIS_API;
import static glide.api.commands.SortedSetBaseCommands.COUNT_REDIS_API;
import static glide.api.commands.SortedSetBaseCommands.LIMIT_REDIS_API;
import static glide.api.commands.SortedSetBaseCommands.WITH_SCORES_REDIS_API;
import static glide.api.commands.SortedSetBaseCommands.WITH_SCORE_REDIS_API;
import static glide.api.commands.StringBaseCommands.IDX_COMMAND_STRING;
import static glide.api.commands.StringBaseCommands.MINMATCHLEN_COMMAND_STRING;
import static glide.api.commands.StringBaseCommands.WITHMATCHLEN_COMMAND_STRING;
import static glide.api.models.commands.ExpireOptions.HAS_EXISTING_EXPIRY;
import static glide.api.models.commands.ExpireOptions.HAS_NO_EXPIRY;
import static glide.api.models.commands.ExpireOptions.NEW_EXPIRY_LESS_THAN_CURRENT;
import static glide.api.models.commands.FlushMode.ASYNC;
import static glide.api.models.commands.InfoOptions.Section.EVERYTHING;
import static glide.api.models.commands.LInsertOptions.InsertPosition.AFTER;
import static glide.api.models.commands.RangeOptions.InfScoreBound.NEGATIVE_INFINITY;
import static glide.api.models.commands.RangeOptions.InfScoreBound.POSITIVE_INFINITY;
import static glide.api.models.commands.ScoreFilter.MAX;
import static glide.api.models.commands.ScoreFilter.MIN;
import static glide.api.models.commands.SetOptions.RETURN_OLD_VALUE;
import static glide.api.models.commands.SortBaseOptions.STORE_COMMAND_STRING;
import static glide.api.models.commands.WeightAggregateOptions.AGGREGATE_REDIS_API;
import static glide.api.models.commands.WeightAggregateOptions.WEIGHTS_REDIS_API;
import static glide.api.models.commands.ZAddOptions.UpdateOptions.SCORE_LESS_THAN_CURRENT;
import static glide.api.models.commands.function.FunctionListOptions.LIBRARY_NAME_REDIS_API;
import static glide.api.models.commands.function.FunctionListOptions.WITH_CODE_REDIS_API;
import static glide.api.models.commands.geospatial.GeoAddOptions.CHANGED_REDIS_API;
import static glide.api.models.commands.geospatial.GeoSearchOrigin.FROMLONLAT_VALKEY_API;
import static glide.api.models.commands.geospatial.GeoSearchOrigin.FROMMEMBER_VALKEY_API;
import static glide.api.models.commands.stream.StreamClaimOptions.FORCE_REDIS_API;
import static glide.api.models.commands.stream.StreamClaimOptions.IDLE_REDIS_API;
import static glide.api.models.commands.stream.StreamClaimOptions.JUST_ID_REDIS_API;
import static glide.api.models.commands.stream.StreamClaimOptions.RETRY_COUNT_REDIS_API;
import static glide.api.models.commands.stream.StreamClaimOptions.TIME_REDIS_API;
import static glide.api.models.commands.stream.StreamGroupOptions.ENTRIES_READ_VALKEY_API;
import static glide.api.models.commands.stream.StreamGroupOptions.MAKE_STREAM_VALKEY_API;
import static glide.api.models.commands.stream.StreamPendingOptions.IDLE_TIME_REDIS_API;
import static glide.api.models.commands.stream.StreamRange.EXCLUSIVE_RANGE_REDIS_API;
import static glide.api.models.commands.stream.StreamRange.MAXIMUM_RANGE_REDIS_API;
import static glide.api.models.commands.stream.StreamRange.MINIMUM_RANGE_REDIS_API;
import static glide.api.models.commands.stream.StreamRange.RANGE_COUNT_REDIS_API;
import static glide.api.models.commands.stream.StreamReadGroupOptions.READ_GROUP_REDIS_API;
import static glide.api.models.commands.stream.StreamReadGroupOptions.READ_NOACK_REDIS_API;
import static glide.api.models.commands.stream.StreamReadOptions.READ_BLOCK_REDIS_API;
import static glide.api.models.commands.stream.StreamReadOptions.READ_COUNT_REDIS_API;
import static glide.api.models.commands.stream.StreamReadOptions.READ_STREAMS_REDIS_API;
import static glide.api.models.commands.stream.StreamTrimOptions.TRIM_EXACT_REDIS_API;
import static glide.api.models.commands.stream.StreamTrimOptions.TRIM_MINID_REDIS_API;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import glide.api.models.commands.ConditionalChange;
import glide.api.models.commands.GetExOptions;
import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.LPosOptions;
import glide.api.models.commands.ListDirection;
import glide.api.models.commands.RangeOptions;
import glide.api.models.commands.RangeOptions.InfLexBound;
import glide.api.models.commands.RangeOptions.InfScoreBound;
import glide.api.models.commands.RangeOptions.LexBoundary;
import glide.api.models.commands.RangeOptions.Limit;
import glide.api.models.commands.RangeOptions.RangeByIndex;
import glide.api.models.commands.RangeOptions.RangeByScore;
import glide.api.models.commands.RangeOptions.ScoreBoundary;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.SortOrder;
import glide.api.models.commands.WeightAggregateOptions.Aggregate;
import glide.api.models.commands.WeightAggregateOptions.KeyArray;
import glide.api.models.commands.WeightAggregateOptions.WeightedKeys;
import glide.api.models.commands.ZAddOptions;
import glide.api.models.commands.bitmap.BitFieldOptions;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldGet;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldReadOnlySubCommands;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldSet;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldSubCommands;
import glide.api.models.commands.bitmap.BitFieldOptions.Offset;
import glide.api.models.commands.bitmap.BitFieldOptions.OffsetMultiplier;
import glide.api.models.commands.bitmap.BitFieldOptions.SignedEncoding;
import glide.api.models.commands.bitmap.BitFieldOptions.UnsignedEncoding;
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
import glide.api.models.commands.stream.StreamClaimOptions;
import glide.api.models.commands.stream.StreamGroupOptions;
import glide.api.models.commands.stream.StreamPendingOptions;
import glide.api.models.commands.stream.StreamRange;
import glide.api.models.commands.stream.StreamRange.InfRangeBound;
import glide.api.models.commands.stream.StreamReadGroupOptions;
import glide.api.models.commands.stream.StreamReadOptions;
import glide.api.models.commands.stream.StreamTrimOptions.MinId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import redis_request.RedisRequestOuterClass.Command;
import redis_request.RedisRequestOuterClass.Command.ArgsArray;
import redis_request.RedisRequestOuterClass.RequestType;

public class TransactionTests {
    private static Stream<Arguments> getTransactionBuilders() {
        return Stream.of(Arguments.of(new Transaction()), Arguments.of(new ClusterTransaction()));
    }

    @ParameterizedTest
    @MethodSource("getTransactionBuilders")
    public void transaction_builds_protobuf_request(BaseTransaction<?> transaction) {
        List<Pair<RequestType, ArgsArray>> results = new LinkedList<>();

        transaction.get("key");
        results.add(Pair.of(Get, buildArgs("key")));

        transaction.getex("key");
        results.add(Pair.of(GetEx, buildArgs("key")));

        transaction.getex("key", GetExOptions.Seconds(10L));
        results.add(Pair.of(GetEx, buildArgs("key", "EX", "10")));

        transaction.set("key", "value");
        results.add(Pair.of(Set, buildArgs("key", "value")));

        transaction.set("key", "value", SetOptions.builder().returnOldValue(true).build());
        results.add(Pair.of(Set, buildArgs("key", "value", RETURN_OLD_VALUE)));

        transaction.append("key", "value");
        results.add(Pair.of(Append, buildArgs("key", "value")));

        transaction.del(new String[] {"key1", "key2"});
        results.add(Pair.of(Del, buildArgs("key1", "key2")));

        transaction.echo("GLIDE");
        results.add(Pair.of(Echo, buildArgs("GLIDE")));

        transaction.ping();
        results.add(Pair.of(Ping, buildArgs()));

        transaction.ping("KING PONG");
        results.add(Pair.of(Ping, buildArgs("KING PONG")));

        transaction.info();
        results.add(Pair.of(Info, buildArgs()));

        transaction.info(InfoOptions.builder().section(EVERYTHING).build());
        results.add(Pair.of(Info, buildArgs(EVERYTHING.toString())));

        transaction.mset(Map.of("key", "value"));
        results.add(Pair.of(MSet, buildArgs("key", "value")));

        transaction.msetnx(Map.of("key", "value"));
        results.add(Pair.of(MSetNX, buildArgs("key", "value")));

        transaction.mget(new String[] {"key"});
        results.add(Pair.of(MGet, buildArgs("key")));

        transaction.incr("key");
        results.add(Pair.of(Incr, buildArgs("key")));

        transaction.incrBy("key", 1);
        results.add(Pair.of(IncrBy, buildArgs("key", "1")));

        transaction.incrByFloat("key", 2.5);
        results.add(Pair.of(IncrByFloat, buildArgs("key", "2.5")));

        transaction.decr("key");
        results.add(Pair.of(Decr, buildArgs("key")));

        transaction.decrBy("key", 2);
        results.add(Pair.of(DecrBy, buildArgs("key", "2")));

        transaction.strlen("key");
        results.add(Pair.of(Strlen, buildArgs("key")));

        transaction.setrange("key", 42, "str");
        results.add(Pair.of(SetRange, buildArgs("key", "42", "str")));

        transaction.getrange("key", 42, 54);
        results.add(Pair.of(GetRange, buildArgs("key", "42", "54")));

        transaction.hset("key", Map.of("field", "value"));
        results.add(Pair.of(HSet, buildArgs("key", "field", "value")));

        transaction.hsetnx("key", "field", "value");
        results.add(Pair.of(HSetNX, buildArgs("key", "field", "value")));

        transaction.hget("key", "field");
        results.add(Pair.of(HGet, buildArgs("key", "field")));

        transaction.hdel("key", new String[] {"field"});
        results.add(Pair.of(HDel, buildArgs("key", "field")));

        transaction.hlen("key");
        results.add(Pair.of(HLen, buildArgs("key")));

        transaction.hvals("key");
        results.add(Pair.of(HVals, buildArgs("key")));

        transaction.hmget("key", new String[] {"field"});
        results.add(Pair.of(HMGet, buildArgs("key", "field")));

        transaction.hexists("key", "field");
        results.add(Pair.of(HExists, buildArgs("key", "field")));

        transaction.hgetall("key");
        results.add(Pair.of(HGetAll, buildArgs("key")));

        transaction.hincrBy("key", "field", 1);
        results.add(Pair.of(HIncrBy, buildArgs("key", "field", "1")));

        transaction.hincrByFloat("key", "field", 1.5);
        results.add(Pair.of(HIncrByFloat, buildArgs("key", "field", "1.5")));

        transaction.hkeys("key");
        results.add(Pair.of(HKeys, buildArgs("key")));

        transaction.hstrlen("key", "field");
        results.add(Pair.of(HStrlen, buildArgs("key", "field")));

        transaction
                .hrandfield("key")
                .hrandfieldWithCount("key", 2)
                .hrandfieldWithCountWithValues("key", 3);
        results.add(Pair.of(HRandField, buildArgs("key")));
        results.add(Pair.of(HRandField, buildArgs("key", "2")));
        results.add(Pair.of(HRandField, buildArgs("key", "3", WITH_VALUES_REDIS_API)));

        transaction.lpush("key", new String[] {"element1", "element2"});
        results.add(Pair.of(LPush, buildArgs("key", "element1", "element2")));

        transaction.lpos("key", "element1");
        results.add(Pair.of(LPos, buildArgs("key", "element1")));

        transaction.lpos("key", "element1", LPosOptions.builder().rank(1L).build());
        results.add(Pair.of(LPos, buildArgs("key", "element1", "RANK", "1")));

        transaction.lposCount("key", "element1", 1L);
        results.add(Pair.of(LPos, buildArgs("key", "element1", "COUNT", "1")));

        transaction.lposCount("key", "element1", 1L, LPosOptions.builder().rank(1L).build());
        results.add(Pair.of(LPos, buildArgs("key", "element1", "COUNT", "1", "RANK", "1")));

        transaction.lpop("key");
        results.add(Pair.of(LPop, buildArgs("key")));

        transaction.lpopCount("key", 2);
        results.add(Pair.of(LPop, buildArgs("key", "2")));

        transaction.lrange("key", 1, 2);
        results.add(Pair.of(LRange, buildArgs("key", "1", "2")));

        transaction.lindex("key", 1);
        results.add(Pair.of(LIndex, buildArgs("key", "1")));

        transaction.ltrim("key", 1, 2);
        results.add(Pair.of(LTrim, buildArgs("key", "1", "2")));

        transaction.llen("key");
        results.add(Pair.of(LLen, buildArgs("key")));

        transaction.lrem("key", 1, "element");
        results.add(Pair.of(LRem, buildArgs("key", "1", "element")));

        transaction.rpush("key", new String[] {"element"});
        results.add(Pair.of(RPush, buildArgs("key", "element")));

        transaction.rpop("key");
        results.add(Pair.of(RPop, buildArgs("key")));

        transaction.rpopCount("key", 2);
        results.add(Pair.of(RPop, buildArgs("key", "2")));

        transaction.sadd("key", new String[] {"value"});
        results.add(Pair.of(SAdd, buildArgs("key", "value")));

        transaction.sismember("key", "member");
        results.add(Pair.of(SIsMember, buildArgs("key", "member")));

        transaction.srem("key", new String[] {"value"});
        results.add(Pair.of(SRem, buildArgs("key", "value")));

        transaction.smembers("key");
        results.add(Pair.of(SMembers, buildArgs("key")));

        transaction.scard("key");
        results.add(Pair.of(SCard, buildArgs("key")));

        transaction.smove("key1", "key2", "elem");
        results.add(Pair.of(SMove, buildArgs("key1", "key2", "elem")));

        transaction.sinter(new String[] {"key1", "key2"});
        results.add(Pair.of(SInter, buildArgs("key1", "key2")));

        transaction.sinterstore("key", new String[] {"set1", "set2"});
        results.add(Pair.of(SInterStore, buildArgs("key", "set1", "set2")));

        transaction.smismember("key", new String[] {"1", "2"});
        results.add(Pair.of(SMIsMember, buildArgs("key", "1", "2")));

        transaction.sunionstore("key", new String[] {"set1", "set2"});
        results.add(Pair.of(SUnionStore, buildArgs("key", "set1", "set2")));

        transaction.exists(new String[] {"key1", "key2"});
        results.add(Pair.of(Exists, buildArgs("key1", "key2")));

        transaction.unlink(new String[] {"key1", "key2"});
        results.add(Pair.of(Unlink, buildArgs("key1", "key2")));

        transaction.expire("key", 9L);
        results.add(Pair.of(Expire, buildArgs("key", "9")));
        transaction.expireAt("key", 9999L, NEW_EXPIRY_LESS_THAN_CURRENT);
        results.add(Pair.of(ExpireAt, buildArgs("key", "9999", "LT")));

        transaction.pexpire("key", 99999L);
        results.add(Pair.of(PExpire, buildArgs("key", "99999")));

        transaction.pexpire("key", 999999L, HAS_EXISTING_EXPIRY);
        results.add(Pair.of(PExpire, buildArgs("key", "999999", "XX")));

        transaction.pexpireAt("key", 9999999L);
        results.add(Pair.of(PExpireAt, buildArgs("key", "9999999")));

        transaction.pexpireAt("key", 99999999L, HAS_NO_EXPIRY);
        results.add(Pair.of(PExpireAt, buildArgs("key", "99999999", "NX")));

        transaction.getdel("key");
        results.add(Pair.of(GetDel, buildArgs("key")));

        transaction.ttl("key");
        results.add(Pair.of(TTL, buildArgs("key")));

        transaction.pttl("key");
        results.add(Pair.of(PTTL, buildArgs("key")));

        transaction.expiretime("key");
        results.add(Pair.of(ExpireTime, buildArgs("key")));

        transaction.pexpiretime("key");
        results.add(Pair.of(PExpireTime, buildArgs("key")));

        transaction.clientId();
        results.add(Pair.of(ClientId, buildArgs()));

        transaction.clientGetName();
        results.add(Pair.of(ClientGetName, buildArgs()));

        transaction.configRewrite();
        results.add(Pair.of(ConfigRewrite, buildArgs()));

        transaction.configResetStat();
        results.add(Pair.of(ConfigResetStat, buildArgs()));

        transaction.configGet(new String[] {"maxmemory", "hash-max-listpack-entries"});
        results.add(Pair.of(ConfigGet, buildArgs("maxmemory", "hash-max-listpack-entries")));

        var configSetMap = new LinkedHashMap<String, String>();
        configSetMap.put("maxmemory", "100mb");
        configSetMap.put("save", "60");

        transaction.configSet(configSetMap);
        results.add(Pair.of(ConfigSet, buildArgs("maxmemory", "100mb", "save", "60")));

        Map<String, Double> membersScores = new LinkedHashMap<>();
        membersScores.put("member1", 1.0);
        membersScores.put("member2", 2.0);
        transaction.zadd(
                "key",
                membersScores,
                ZAddOptions.builder().updateOptions(SCORE_LESS_THAN_CURRENT).build(),
                true);
        results.add(Pair.of(ZAdd, buildArgs("key", "LT", "CH", "1.0", "member1", "2.0", "member2")));

        transaction.zaddIncr(
                "key",
                "member1",
                3.0,
                ZAddOptions.builder().updateOptions(SCORE_LESS_THAN_CURRENT).build());
        results.add(Pair.of(ZAdd, buildArgs("key", "LT", "INCR", "3.0", "member1")));

        transaction.zrem("key", new String[] {"member1", "member2"});
        results.add(Pair.of(ZRem, buildArgs("key", "member1", "member2")));

        transaction.zcard("key");
        results.add(Pair.of(ZCard, buildArgs("key")));

        transaction.zpopmin("key");
        results.add(Pair.of(ZPopMin, buildArgs("key")));

        transaction.zpopmin("key", 2);
        results.add(Pair.of(ZPopMin, buildArgs("key", "2")));

        transaction.bzpopmin(new String[] {"key1", "key2"}, .5);
        results.add(Pair.of(BZPopMin, buildArgs("key1", "key2", "0.5")));

        transaction.zpopmax("key");
        results.add(Pair.of(ZPopMax, buildArgs("key")));

        transaction.bzpopmax(new String[] {"key1", "key2"}, .5);
        results.add(Pair.of(BZPopMax, buildArgs("key1", "key2", "0.5")));

        transaction.zpopmax("key", 2);
        results.add(Pair.of(ZPopMax, buildArgs("key", "2")));

        transaction.zscore("key", "member");
        results.add(Pair.of(ZScore, buildArgs("key", "member")));

        transaction.zrank("key", "member");
        results.add(Pair.of(ZRank, buildArgs("key", "member")));

        transaction.zrankWithScore("key", "member");
        results.add(Pair.of(ZRank, buildArgs("key", "member", WITH_SCORE_REDIS_API)));

        transaction.zrevrank("key", "member");
        results.add(Pair.of(ZRevRank, buildArgs("key", "member")));

        transaction.zrevrankWithScore("key", "member");
        results.add(Pair.of(ZRevRank, buildArgs("key", "member", WITH_SCORE_REDIS_API)));

        transaction.zmscore("key", new String[] {"member1", "member2"});
        results.add(Pair.of(ZMScore, buildArgs("key", "member1", "member2")));

        transaction.zdiff(new String[] {"key1", "key2"});
        results.add(Pair.of(ZDiff, buildArgs("2", "key1", "key2")));

        transaction.zdiffWithScores(new String[] {"key1", "key2"});
        results.add(Pair.of(ZDiff, buildArgs("2", "key1", "key2", WITH_SCORES_REDIS_API)));

        transaction.zdiffstore("destKey", new String[] {"key1", "key2"});
        results.add(Pair.of(ZDiffStore, buildArgs("destKey", "2", "key1", "key2")));

        transaction.zmpop(new String[] {"key1", "key2"}, MAX).zmpop(new String[] {"key"}, MIN, 42);
        results.add(Pair.of(ZMPop, buildArgs("2", "key1", "key2", "MAX")));
        results.add(Pair.of(ZMPop, buildArgs("1", "key", "MIN", "COUNT", "42")));

        transaction
                .bzmpop(new String[] {"key1", "key2"}, MAX, .1)
                .bzmpop(new String[] {"key"}, MIN, .1, 42);
        results.add(Pair.of(BZMPop, buildArgs("0.1", "2", "key1", "key2", "MAX")));
        results.add(Pair.of(BZMPop, buildArgs("0.1", "1", "key", "MIN", "COUNT", "42")));

        transaction.zcount("key", new ScoreBoundary(5, false), InfScoreBound.POSITIVE_INFINITY);
        results.add(Pair.of(ZCount, buildArgs("key", "(5.0", "+inf")));

        transaction.zremrangebyrank("key", 0, -1);
        results.add(Pair.of(ZRemRangeByRank, buildArgs("key", "0", "-1")));

        transaction.zremrangebylex("key", new LexBoundary("a", false), InfLexBound.POSITIVE_INFINITY);
        results.add(Pair.of(ZRemRangeByLex, buildArgs("key", "(a", "+")));

        transaction.zremrangebyscore(
                "key", new ScoreBoundary(5, false), RangeOptions.InfScoreBound.POSITIVE_INFINITY);
        results.add(Pair.of(ZRemRangeByScore, buildArgs("key", "(5.0", "+inf")));

        transaction.zlexcount("key", new LexBoundary("c", false), InfLexBound.POSITIVE_INFINITY);
        results.add(Pair.of(ZLexCount, buildArgs("key", "(c", "+")));

        transaction.zrangestore(
                "destination",
                "source",
                new RangeByScore(
                        InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(3, false), new Limit(1, 2)),
                true);
        results.add(
                Pair.of(
                        ZRangeStore,
                        buildArgs(
                                "destination", "source", "-inf", "(3.0", "BYSCORE", "REV", "LIMIT", "1", "2")));

        transaction.zrangestore("destination", "source", new RangeByIndex(2, 3));
        results.add(Pair.of(ZRangeStore, buildArgs("destination", "source", "2", "3")));

        transaction.zinterstore("destination", new KeyArray(new String[] {"key1", "key2"}));
        results.add(Pair.of(ZInterStore, buildArgs("destination", "2", "key1", "key2")));

        transaction.zunionstore("destination", new KeyArray(new String[] {"key1", "key2"}));
        results.add(Pair.of(ZUnionStore, buildArgs("destination", "2", "key1", "key2")));

        transaction.zunion(new KeyArray(new String[] {"key1", "key2"}));
        results.add(Pair.of(ZUnion, buildArgs("2", "key1", "key2")));

        transaction.zunionWithScores(new KeyArray(new String[] {"key1", "key2"}));
        results.add(Pair.of(ZUnion, buildArgs("2", "key1", "key2", WITH_SCORES_REDIS_API)));

        List<Pair<String, Double>> weightedKeys = new ArrayList<>();
        weightedKeys.add(Pair.of("key1", 10.0));
        weightedKeys.add(Pair.of("key2", 20.0));

        transaction.zinterstore("destination", new WeightedKeys(weightedKeys), Aggregate.MAX);
        results.add(
                Pair.of(
                        ZInterStore,
                        buildArgs(
                                "destination",
                                "2",
                                "key1",
                                "key2",
                                WEIGHTS_REDIS_API,
                                "10.0",
                                "20.0",
                                AGGREGATE_REDIS_API,
                                Aggregate.MAX.toString())));

        transaction.zunionstore("destination", new WeightedKeys(weightedKeys), Aggregate.MAX);
        results.add(
                Pair.of(
                        ZUnionStore,
                        buildArgs(
                                "destination",
                                "2",
                                "key1",
                                "key2",
                                WEIGHTS_REDIS_API,
                                "10.0",
                                "20.0",
                                AGGREGATE_REDIS_API,
                                Aggregate.MAX.toString())));

        transaction.zunionWithScores(new WeightedKeys(weightedKeys), Aggregate.MAX);
        results.add(
                Pair.of(
                        ZUnion,
                        buildArgs(
                                "2",
                                "key1",
                                "key2",
                                WEIGHTS_REDIS_API,
                                "10.0",
                                "20.0",
                                AGGREGATE_REDIS_API,
                                Aggregate.MAX.toString(),
                                WITH_SCORES_REDIS_API)));
        transaction.zintercard(new String[] {"key1", "key2"}, 5);
        results.add(Pair.of(ZInterCard, buildArgs("2", "key1", "key2", LIMIT_REDIS_API, "5")));

        transaction.zintercard(new String[] {"key1", "key2"});
        results.add(Pair.of(ZInterCard, buildArgs("2", "key1", "key2")));

        transaction.zinter(new KeyArray(new String[] {"key1", "key2"}));
        results.add(Pair.of(ZInter, buildArgs("2", "key1", "key2")));

        transaction.zinterWithScores(new KeyArray(new String[] {"key1", "key2"}));
        results.add(Pair.of(ZInter, buildArgs("2", "key1", "key2", WITH_SCORES_REDIS_API)));

        transaction.zinterWithScores(new WeightedKeys(weightedKeys), Aggregate.MAX);
        results.add(
                Pair.of(
                        ZInter,
                        buildArgs(
                                "2",
                                "key1",
                                "key2",
                                WEIGHTS_REDIS_API,
                                "10.0",
                                "20.0",
                                AGGREGATE_REDIS_API,
                                Aggregate.MAX.toString(),
                                WITH_SCORES_REDIS_API)));

        transaction.xadd("key", Map.of("field1", "foo1"));
        results.add(Pair.of(XAdd, buildArgs("key", "*", "field1", "foo1")));

        transaction.xadd("key", Map.of("field1", "foo1"), StreamAddOptions.builder().id("id").build());
        results.add(Pair.of(XAdd, buildArgs("key", "id", "field1", "foo1")));

        transaction.xtrim("key", new MinId(true, "id"));
        results.add(Pair.of(XTrim, buildArgs("key", TRIM_MINID_REDIS_API, TRIM_EXACT_REDIS_API, "id")));

        transaction.xread(Map.of("key", "id"));
        results.add(Pair.of(XRead, buildArgs(READ_STREAMS_REDIS_API, "key", "id")));

        transaction.xread(Map.of("key", "id"), StreamReadOptions.builder().block(1L).count(2L).build());
        results.add(
                Pair.of(
                        XRead,
                        buildArgs(
                                READ_COUNT_REDIS_API,
                                "2",
                                READ_BLOCK_REDIS_API,
                                "1",
                                READ_STREAMS_REDIS_API,
                                "key",
                                "id")));

        transaction.xlen("key");
        results.add(Pair.of(XLen, buildArgs("key")));

        transaction.xdel("key", new String[] {"12345-1", "98765-4"});
        results.add(Pair.of(XDel, buildArgs("key", "12345-1", "98765-4")));

        transaction.xrange("key", InfRangeBound.MIN, InfRangeBound.MAX);
        results.add(
                Pair.of(XRange, buildArgs("key", MINIMUM_RANGE_REDIS_API, MAXIMUM_RANGE_REDIS_API)));

        transaction.xrange("key", InfRangeBound.MIN, InfRangeBound.MAX, 99L);
        results.add(
                Pair.of(
                        XRange,
                        buildArgs(
                                "key",
                                MINIMUM_RANGE_REDIS_API,
                                MAXIMUM_RANGE_REDIS_API,
                                RANGE_COUNT_REDIS_API,
                                "99")));

        transaction.xrevrange("key", InfRangeBound.MAX, InfRangeBound.MIN);
        results.add(
                Pair.of(XRevRange, buildArgs("key", MAXIMUM_RANGE_REDIS_API, MINIMUM_RANGE_REDIS_API)));

        transaction.xrevrange("key", InfRangeBound.MAX, InfRangeBound.MIN, 99L);
        results.add(
                Pair.of(
                        XRevRange,
                        buildArgs(
                                "key",
                                MAXIMUM_RANGE_REDIS_API,
                                MINIMUM_RANGE_REDIS_API,
                                RANGE_COUNT_REDIS_API,
                                "99")));

        transaction.xgroupCreate("key", "group", "id");
        results.add(Pair.of(XGroupCreate, buildArgs("key", "group", "id")));

        transaction.xgroupCreate(
                "key",
                "group",
                "id",
                StreamGroupOptions.builder().makeStream().entriesRead("entry").build());
        results.add(
                Pair.of(
                        XGroupCreate,
                        buildArgs(
                                "key", "group", "id", MAKE_STREAM_VALKEY_API, ENTRIES_READ_VALKEY_API, "entry")));

        transaction.xgroupDestroy("key", "group");
        results.add(Pair.of(XGroupDestroy, buildArgs("key", "group")));

        transaction.xgroupCreateConsumer("key", "group", "consumer");
        results.add(Pair.of(XGroupCreateConsumer, buildArgs("key", "group", "consumer")));

        transaction.xgroupDelConsumer("key", "group", "consumer");
        results.add(Pair.of(XGroupDelConsumer, buildArgs("key", "group", "consumer")));

        transaction.xreadgroup(Map.of("key", "id"), "group", "consumer");
        results.add(
                Pair.of(
                        XReadGroup,
                        buildArgs(
                                READ_GROUP_REDIS_API, "group", "consumer", READ_STREAMS_REDIS_API, "key", "id")));

        transaction.xgroupSetId("key", "group", "id");
        results.add(Pair.of(XGroupSetId, buildArgs("key", "group", "id")));

        transaction.xgroupSetId("key", "group", "id", "1-1");
        results.add(Pair.of(XGroupSetId, buildArgs("key", "group", "id", "ENTRIESREAD", "1-1")));

        transaction.xreadgroup(
                Map.of("key", "id"),
                "group",
                "consumer",
                StreamReadGroupOptions.builder().block(1L).count(2L).noack().build());
        results.add(
                Pair.of(
                        XReadGroup,
                        buildArgs(
                                READ_GROUP_REDIS_API,
                                "group",
                                "consumer",
                                READ_COUNT_REDIS_API,
                                "2",
                                READ_BLOCK_REDIS_API,
                                "1",
                                READ_NOACK_REDIS_API,
                                READ_STREAMS_REDIS_API,
                                "key",
                                "id")));

        transaction.xack("key", "group", new String[] {"12345-1", "98765-4"});
        results.add(Pair.of(XAck, buildArgs("key", "group", "12345-1", "98765-4")));

        transaction.xpending("key", "group");
        results.add(Pair.of(XPending, buildArgs("key", "group")));

        transaction.xpending("key", "group", InfRangeBound.MAX, InfRangeBound.MIN, 99L);
        results.add(
                Pair.of(
                        XPending,
                        buildArgs("key", "group", MAXIMUM_RANGE_REDIS_API, MINIMUM_RANGE_REDIS_API, "99")));

        transaction.xpending(
                "key",
                "group",
                StreamRange.IdBound.ofExclusive("11"),
                StreamRange.IdBound.ofExclusive("1234-0"),
                99L,
                StreamPendingOptions.builder().minIdleTime(5L).consumer("consumer").build());
        results.add(
                Pair.of(
                        XPending,
                        buildArgs(
                                "key",
                                "group",
                                IDLE_TIME_REDIS_API,
                                "5",
                                EXCLUSIVE_RANGE_REDIS_API + "11",
                                EXCLUSIVE_RANGE_REDIS_API + "1234-0",
                                "99",
                                "consumer")));

        transaction.xclaim("key", "group", "consumer", 99L, new String[] {"12345-1", "98765-4"});
        results.add(Pair.of(XClaim, buildArgs("key", "group", "consumer", "99", "12345-1", "98765-4")));

        StreamClaimOptions claimOptions =
                StreamClaimOptions.builder().force().idle(11L).idleUnixTime(12L).retryCount(5L).build();
        transaction.xclaim(
                "key", "group", "consumer", 99L, new String[] {"12345-1", "98765-4"}, claimOptions);
        results.add(
                Pair.of(
                        XClaim,
                        buildArgs(
                                "key",
                                "group",
                                "consumer",
                                "99",
                                "12345-1",
                                "98765-4",
                                IDLE_REDIS_API,
                                "11",
                                TIME_REDIS_API,
                                "12",
                                RETRY_COUNT_REDIS_API,
                                "5",
                                FORCE_REDIS_API)));

        transaction.xclaimJustId("key", "group", "consumer", 99L, new String[] {"12345-1", "98765-4"});
        results.add(
                Pair.of(
                        XClaim,
                        buildArgs("key", "group", "consumer", "99", "12345-1", "98765-4", JUST_ID_REDIS_API)));

        transaction.xclaimJustId(
                "key", "group", "consumer", 99L, new String[] {"12345-1", "98765-4"}, claimOptions);
        results.add(
                Pair.of(
                        XClaim,
                        buildArgs(
                                "key",
                                "group",
                                "consumer",
                                "99",
                                "12345-1",
                                "98765-4",
                                IDLE_REDIS_API,
                                "11",
                                TIME_REDIS_API,
                                "12",
                                RETRY_COUNT_REDIS_API,
                                "5",
                                FORCE_REDIS_API,
                                JUST_ID_REDIS_API)));

        transaction.time();
        results.add(Pair.of(Time, buildArgs()));

        transaction.lastsave();
        results.add(Pair.of(LastSave, buildArgs()));

        transaction.flushall().flushall(ASYNC);
        results.add(Pair.of(FlushAll, buildArgs()));
        results.add(Pair.of(FlushAll, buildArgs(ASYNC.toString())));

        transaction.flushdb().flushdb(ASYNC);
        results.add(Pair.of(FlushDB, buildArgs()));
        results.add(Pair.of(FlushDB, buildArgs(ASYNC.toString())));

        transaction.lolwut().lolwut(5).lolwut(new int[] {1, 2}).lolwut(6, new int[] {42});
        results.add(Pair.of(Lolwut, buildArgs()));
        results.add(Pair.of(Lolwut, buildArgs(VERSION_REDIS_API, "5")));
        results.add(Pair.of(Lolwut, buildArgs("1", "2")));
        results.add(Pair.of(Lolwut, buildArgs(VERSION_REDIS_API, "6", "42")));

        transaction.dbsize();
        results.add(Pair.of(DBSize, buildArgs()));

        transaction.persist("key");
        results.add(Pair.of(Persist, buildArgs("key")));

        transaction.zrandmember("key");
        results.add(Pair.of(ZRandMember, buildArgs("key")));

        transaction.zrandmemberWithCount("key", 5);
        results.add(Pair.of(ZRandMember, buildArgs("key", "5")));

        transaction.zrandmemberWithCountWithScores("key", 5);
        results.add(Pair.of(ZRandMember, buildArgs("key", "5", WITH_SCORES_REDIS_API)));

        transaction.zincrby("key", 3.14, "value");
        results.add(Pair.of(ZIncrBy, buildArgs("key", "3.14", "value")));

        transaction.type("key");
        results.add(Pair.of(Type, buildArgs("key")));

        transaction.randomKey();
        results.add(Pair.of(RandomKey, buildArgs()));

        transaction.rename("key", "newKey");
        results.add(Pair.of(Rename, buildArgs("key", "newKey")));

        transaction.renamenx("key", "newKey");
        results.add(Pair.of(RenameNX, buildArgs("key", "newKey")));

        transaction.linsert("key", AFTER, "pivot", "elem");
        results.add(Pair.of(LInsert, buildArgs("key", "AFTER", "pivot", "elem")));

        transaction.brpop(new String[] {"key1", "key2"}, 0.5);
        results.add(Pair.of(BRPop, buildArgs("key1", "key2", "0.5")));
        transaction.blpop(new String[] {"key1", "key2"}, 0.5);
        results.add(Pair.of(BLPop, buildArgs("key1", "key2", "0.5")));

        transaction.rpushx("key", new String[] {"element1", "element2"});
        results.add(Pair.of(RPushX, buildArgs("key", "element1", "element2")));

        transaction.lpushx("key", new String[] {"element1", "element2"});
        results.add(Pair.of(LPushX, buildArgs("key", "element1", "element2")));

        transaction.zrange(
                "key",
                new RangeByScore(NEGATIVE_INFINITY, new ScoreBoundary(3, false), new Limit(1, 2)),
                true);
        results.add(
                Pair.of(ZRange, buildArgs("key", "-inf", "(3.0", "BYSCORE", "REV", "LIMIT", "1", "2")));

        transaction.zrangeWithScores(
                "key",
                new RangeByScore(new ScoreBoundary(5, true), POSITIVE_INFINITY, new Limit(1, 2)),
                false);
        results.add(
                Pair.of(
                        ZRange,
                        buildArgs("key", "5.0", "+inf", "BYSCORE", "LIMIT", "1", "2", WITH_SCORES_REDIS_API)));

        transaction.pfadd("hll", new String[] {"a", "b", "c"});
        results.add(Pair.of(PfAdd, buildArgs("hll", "a", "b", "c")));

        transaction.pfcount(new String[] {"hll1", "hll2"});
        results.add(Pair.of(PfCount, buildArgs("hll1", "hll2")));
        transaction.pfmerge("hll", new String[] {"hll1", "hll2"});
        results.add(Pair.of(PfMerge, buildArgs("hll", "hll1", "hll2")));

        transaction.sdiff(new String[] {"key1", "key2"});
        results.add(Pair.of(SDiff, buildArgs("key1", "key2")));

        transaction.sdiffstore("key1", new String[] {"key2", "key3"});
        results.add(Pair.of(SDiffStore, buildArgs("key1", "key2", "key3")));

        transaction.objectEncoding("key");
        results.add(Pair.of(ObjectEncoding, buildArgs("key")));

        transaction.objectFreq("key");
        results.add(Pair.of(ObjectFreq, buildArgs("key")));

        transaction.objectIdletime("key");
        results.add(Pair.of(ObjectIdleTime, buildArgs("key")));

        transaction.objectRefcount("key");
        results.add(Pair.of(ObjectRefCount, buildArgs("key")));

        transaction.touch(new String[] {"key1", "key2"});
        results.add(Pair.of(Touch, buildArgs("key1", "key2")));

        transaction.geoadd("key", Map.of("Place", new GeospatialData(10.0, 20.0)));
        results.add(Pair.of(GeoAdd, buildArgs("key", "10.0", "20.0", "Place")));

        transaction.getbit("key", 1);
        results.add(Pair.of(GetBit, buildArgs("key", "1")));

        transaction.geoadd(
                "key",
                Map.of("Place", new GeospatialData(10.0, 20.0)),
                new GeoAddOptions(ConditionalChange.ONLY_IF_EXISTS, true));
        results.add(
                Pair.of(
                        GeoAdd,
                        buildArgs(
                                "key",
                                ConditionalChange.ONLY_IF_EXISTS.getRedisApi(),
                                CHANGED_REDIS_API,
                                "10.0",
                                "20.0",
                                "Place")));
        transaction.geopos("key", new String[] {"Place"});
        results.add(Pair.of(GeoPos, buildArgs("key", "Place")));

        transaction.functionLoad("pewpew", false).functionLoad("ololo", true);
        results.add(Pair.of(FunctionLoad, buildArgs("pewpew")));
        results.add(Pair.of(FunctionLoad, buildArgs("REPLACE", "ololo")));

        transaction.functionList(true).functionList("*", false);
        results.add(Pair.of(FunctionList, buildArgs(WITH_CODE_REDIS_API)));
        results.add(Pair.of(FunctionList, buildArgs(LIBRARY_NAME_REDIS_API, "*")));

        transaction.fcall("func", new String[] {"key1", "key2"}, new String[] {"arg1", "arg2"});
        results.add(Pair.of(FCall, buildArgs("func", "2", "key1", "key2", "arg1", "arg2")));
        transaction.fcall("func", new String[] {"arg1", "arg2"});
        results.add(Pair.of(FCall, buildArgs("func", "0", "arg1", "arg2")));

        transaction.fcallReadOnly("func", new String[] {"key1", "key2"}, new String[] {"arg1", "arg2"});
        results.add(Pair.of(FCallReadOnly, buildArgs("func", "2", "key1", "key2", "arg1", "arg2")));
        transaction.fcallReadOnly("func", new String[] {"arg1", "arg2"});
        results.add(Pair.of(FCallReadOnly, buildArgs("func", "0", "arg1", "arg2")));

        transaction.functionStats();
        results.add(Pair.of(FunctionStats, buildArgs()));

        transaction.geodist("key", "Place", "Place2");
        results.add(Pair.of(GeoDist, buildArgs("key", "Place", "Place2")));
        transaction.geodist("key", "Place", "Place2", GeoUnit.KILOMETERS);
        results.add(Pair.of(GeoDist, buildArgs("key", "Place", "Place2", "km")));

        transaction.geohash("key", new String[] {"Place"});
        results.add(Pair.of(GeoHash, buildArgs("key", "Place")));

        transaction.bitcount("key");
        results.add(Pair.of(BitCount, buildArgs("key")));

        transaction.bitcount("key", 1, 1);
        results.add(Pair.of(BitCount, buildArgs("key", "1", "1")));

        transaction.bitcount("key", 1, 1, BitmapIndexType.BIT);
        results.add(Pair.of(BitCount, buildArgs("key", "1", "1", BitmapIndexType.BIT.toString())));

        transaction.setbit("key", 8, 1);
        results.add(Pair.of(SetBit, buildArgs("key", "8", "1")));

        transaction.bitpos("key", 1);
        results.add(Pair.of(BitPos, buildArgs("key", "1")));
        transaction.bitpos("key", 0, 8);
        results.add(Pair.of(BitPos, buildArgs("key", "0", "8")));
        transaction.bitpos("key", 1, 8, 10);
        results.add(Pair.of(BitPos, buildArgs("key", "1", "8", "10")));
        transaction.bitpos("key", 1, 8, 10, BitmapIndexType.BIT);
        results.add(Pair.of(BitPos, buildArgs("key", "1", "8", "10", BitmapIndexType.BIT.toString())));

        transaction.bitop(BitwiseOperation.AND, "destination", new String[] {"key"});
        results.add(Pair.of(BitOp, buildArgs(BitwiseOperation.AND.toString(), "destination", "key")));

        transaction.lmpop(new String[] {"key"}, ListDirection.LEFT);
        results.add(Pair.of(LMPop, buildArgs("1", "key", "LEFT")));
        transaction.lmpop(new String[] {"key"}, ListDirection.LEFT, 1L);
        results.add(Pair.of(LMPop, buildArgs("1", "key", "LEFT", "COUNT", "1")));

        transaction.blmpop(new String[] {"key"}, ListDirection.LEFT, 0.1);
        results.add(Pair.of(BLMPop, buildArgs("0.1", "1", "key", "LEFT")));
        transaction.blmpop(new String[] {"key"}, ListDirection.LEFT, 1L, 0.1);
        results.add(Pair.of(BLMPop, buildArgs("0.1", "1", "key", "LEFT", "COUNT", "1")));

        transaction.lset("key", 0, "zero");
        results.add(Pair.of(LSet, buildArgs("key", "0", "zero")));

        transaction.lmove("key1", "key2", ListDirection.LEFT, ListDirection.LEFT);
        results.add(Pair.of(LMove, buildArgs("key1", "key2", "LEFT", "LEFT")));

        transaction.blmove("key1", "key2", ListDirection.LEFT, ListDirection.LEFT, 0.1);
        results.add(Pair.of(BLMove, buildArgs("key1", "key2", "LEFT", "LEFT", "0.1")));

        transaction.srandmember("key");
        results.add(Pair.of(SRandMember, buildArgs("key")));

        transaction.srandmember("key", 1);
        results.add(Pair.of(SRandMember, buildArgs("key", "1")));

        transaction.spop("key");
        results.add(Pair.of(SPop, buildArgs("key")));

        transaction.spopCount("key", 1);
        results.add(Pair.of(SPop, buildArgs("key", "1")));

        transaction.bitfieldReadOnly(
                "key",
                new BitFieldReadOnlySubCommands[] {new BitFieldGet(new SignedEncoding(5), new Offset(3))});
        results.add(
                Pair.of(
                        BitFieldReadOnly,
                        buildArgs(
                                "key",
                                BitFieldOptions.GET_COMMAND_STRING,
                                BitFieldOptions.SIGNED_ENCODING_PREFIX.concat("5"),
                                "3")));
        transaction.bitfield(
                "key",
                new BitFieldSubCommands[] {
                    new BitFieldSet(new UnsignedEncoding(10), new OffsetMultiplier(3), 4)
                });
        results.add(
                Pair.of(
                        BitField,
                        buildArgs(
                                "key",
                                BitFieldOptions.SET_COMMAND_STRING,
                                BitFieldOptions.UNSIGNED_ENCODING_PREFIX.concat("10"),
                                BitFieldOptions.OFFSET_MULTIPLIER_PREFIX.concat("3"),
                                "4")));

        transaction.sintercard(new String[] {"key1", "key2"});
        results.add(Pair.of(SInterCard, buildArgs("2", "key1", "key2")));

        transaction.sintercard(new String[] {"key1", "key2"}, 1);
        results.add(Pair.of(SInterCard, buildArgs("2", "key1", "key2", "LIMIT", "1")));

        transaction.functionFlush().functionFlush(ASYNC);
        results.add(Pair.of(FunctionFlush, buildArgs()));
        results.add(Pair.of(FunctionFlush, buildArgs("ASYNC")));

        transaction.functionDelete("LIB");
        results.add(Pair.of(FunctionDelete, buildArgs("LIB")));

        transaction.copy("key1", "key2", true);
        results.add(Pair.of(Copy, buildArgs("key1", "key2", REPLACE_REDIS_API)));

        transaction.lcs("key1", "key2");
        results.add(Pair.of(LCS, buildArgs("key1", "key2")));

        transaction.lcsLen("key1", "key2");
        results.add(Pair.of(LCS, buildArgs("key1", "key2", "LEN")));

        transaction.publish("ch1", "msg");
        results.add(Pair.of(Publish, buildArgs("ch1", "msg")));

        transaction.lcsIdx("key1", "key2");
        results.add(Pair.of(LCS, buildArgs("key1", "key2", IDX_COMMAND_STRING)));

        transaction.lcsIdx("key1", "key2", 10);
        results.add(
                Pair.of(
                        LCS, buildArgs("key1", "key2", IDX_COMMAND_STRING, MINMATCHLEN_COMMAND_STRING, "10")));

        transaction.lcsIdxWithMatchLen("key1", "key2");
        results.add(
                Pair.of(LCS, buildArgs("key1", "key2", IDX_COMMAND_STRING, WITHMATCHLEN_COMMAND_STRING)));

        transaction.lcsIdxWithMatchLen("key1", "key2", 10);
        results.add(
                Pair.of(
                        LCS,
                        buildArgs(
                                "key1",
                                "key2",
                                IDX_COMMAND_STRING,
                                MINMATCHLEN_COMMAND_STRING,
                                "10",
                                WITHMATCHLEN_COMMAND_STRING)));

        transaction.sunion(new String[] {"key1", "key2"});
        results.add(Pair.of(SUnion, buildArgs("key1", "key2")));

        transaction.sort("key1");
        results.add(Pair.of(Sort, buildArgs("key1")));
        transaction.sortReadOnly("key1");
        results.add(Pair.of(SortReadOnly, buildArgs("key1")));
        transaction.sortStore("key1", "key2");
        results.add(Pair.of(Sort, buildArgs("key1", STORE_COMMAND_STRING, "key2")));

        transaction.geosearch(
                "key",
                new GeoSearchOrigin.MemberOrigin("member"),
                new GeoSearchShape(1, GeoUnit.KILOMETERS));
        results.add(
                Pair.of(
                        GeoSearch, buildArgs("key", FROMMEMBER_VALKEY_API, "member", "BYRADIUS", "1.0", "km")));

        transaction.geosearch(
                "key",
                new GeoSearchOrigin.CoordOrigin(new GeospatialData(1.0, 1.0)),
                new GeoSearchShape(1, 1, GeoUnit.KILOMETERS),
                new GeoSearchResultOptions(SortOrder.ASC, 2));
        results.add(
                Pair.of(
                        GeoSearch,
                        buildArgs(
                                "key",
                                FROMLONLAT_VALKEY_API,
                                "1.0",
                                "1.0",
                                "BYBOX",
                                "1.0",
                                "1.0",
                                "km",
                                COUNT_REDIS_API,
                                "2",
                                "ASC")));

        transaction.geosearch(
                "key",
                new GeoSearchOrigin.MemberOrigin("member"),
                new GeoSearchShape(1, GeoUnit.KILOMETERS),
                GeoSearchOptions.builder().withhash().withdist().withcoord().build(),
                new GeoSearchResultOptions(SortOrder.ASC, 1, true));
        results.add(
                Pair.of(
                        GeoSearch,
                        buildArgs(
                                "key",
                                FROMMEMBER_VALKEY_API,
                                "member",
                                "BYRADIUS",
                                "1.0",
                                "km",
                                "WITHDIST",
                                "WITHCOORD",
                                "WITHHASH",
                                "COUNT",
                                "1",
                                "ANY",
                                "ASC")));

        transaction.geosearch(
                "key",
                new GeoSearchOrigin.CoordOrigin(new GeospatialData(1.0, 1.0)),
                new GeoSearchShape(1, 1, GeoUnit.KILOMETERS),
                GeoSearchOptions.builder().withhash().withdist().withcoord().build());
        results.add(
                Pair.of(
                        GeoSearch,
                        buildArgs(
                                "key",
                                FROMLONLAT_VALKEY_API,
                                "1.0",
                                "1.0",
                                "BYBOX",
                                "1.0",
                                "1.0",
                                "km",
                                "WITHDIST",
                                "WITHCOORD",
                                "WITHHASH")));

        transaction.geosearchstore(
                "destination",
                "source",
                new GeoSearchOrigin.MemberOrigin("member"),
                new GeoSearchShape(1, GeoUnit.KILOMETERS));
        results.add(
                Pair.of(
                        GeoSearchStore,
                        buildArgs(
                                "destination",
                                "source",
                                FROMMEMBER_VALKEY_API,
                                "member",
                                "BYRADIUS",
                                "1.0",
                                "km")));

        transaction.geosearchstore(
                "destination",
                "source",
                new GeoSearchOrigin.CoordOrigin(new GeospatialData(1.0, 1.0)),
                new GeoSearchShape(1, 1, GeoUnit.KILOMETERS),
                new GeoSearchResultOptions(SortOrder.ASC, 2));
        results.add(
                Pair.of(
                        GeoSearchStore,
                        buildArgs(
                                "destination",
                                "source",
                                FROMLONLAT_VALKEY_API,
                                "1.0",
                                "1.0",
                                "BYBOX",
                                "1.0",
                                "1.0",
                                "km",
                                COUNT_REDIS_API,
                                "2",
                                "ASC")));

        transaction.geosearchstore(
                "destination",
                "source",
                new GeoSearchOrigin.MemberOrigin("member"),
                new GeoSearchShape(1, GeoUnit.KILOMETERS),
                GeoSearchStoreOptions.builder().storedist().build());
        results.add(
                Pair.of(
                        GeoSearchStore,
                        buildArgs(
                                "destination",
                                "source",
                                FROMMEMBER_VALKEY_API,
                                "member",
                                "BYRADIUS",
                                "1.0",
                                "km",
                                "STOREDIST")));

        transaction.geosearchstore(
                "destination",
                "source",
                new GeoSearchOrigin.MemberOrigin("member"),
                new GeoSearchShape(1, GeoUnit.KILOMETERS),
                GeoSearchStoreOptions.builder().storedist().build(),
                new GeoSearchResultOptions(SortOrder.ASC, 1, true));
        results.add(
                Pair.of(
                        GeoSearchStore,
                        buildArgs(
                                "destination",
                                "source",
                                FROMMEMBER_VALKEY_API,
                                "member",
                                "BYRADIUS",
                                "1.0",
                                "km",
                                "STOREDIST",
                                "COUNT",
                                "1",
                                "ANY",
                                "ASC")));

        transaction.sscan("key1", "0");
        results.add(Pair.of(SScan, buildArgs("key1", "0")));

        transaction.sscan("key1", "0", SScanOptions.builder().matchPattern("*").count(10L).build());
        results.add(
                Pair.of(
                        SScan,
                        buildArgs(
                                "key1",
                                "0",
                                SScanOptions.MATCH_OPTION_STRING,
                                "*",
                                SScanOptions.COUNT_OPTION_STRING,
                                "10")));

        transaction.zscan("key1", "0");
        results.add(Pair.of(ZScan, buildArgs("key1", "0")));

        transaction.zscan("key1", "0", ZScanOptions.builder().matchPattern("*").count(10L).build());
        results.add(
                Pair.of(
                        ZScan,
                        buildArgs(
                                "key1",
                                "0",
                                ZScanOptions.MATCH_OPTION_STRING,
                                "*",
                                ZScanOptions.COUNT_OPTION_STRING,
                                "10")));

        transaction.hscan("key1", "0");
        results.add(Pair.of(HScan, buildArgs("key1", "0")));

        transaction.hscan("key1", "0", HScanOptions.builder().matchPattern("*").count(10L).build());
        results.add(
                Pair.of(
                        HScan,
                        buildArgs(
                                "key1",
                                "0",
                                HScanOptions.MATCH_OPTION_STRING,
                                "*",
                                HScanOptions.COUNT_OPTION_STRING,
                                "10")));

        transaction.wait(1L, 1000L);
        results.add(Pair.of(Wait, buildArgs("1", "1000")));

        var protobufTransaction = transaction.getProtobufTransaction().build();

        for (int idx = 0; idx < protobufTransaction.getCommandsCount(); idx++) {
            Command protobuf = protobufTransaction.getCommands(idx);

            assertEquals(results.get(idx).getLeft(), protobuf.getRequestType());
            assertEquals(
                    results.get(idx).getRight().getArgsCount(), protobuf.getArgsArray().getArgsCount());
            assertEquals(results.get(idx).getRight(), protobuf.getArgsArray());
        }
    }

    static ArgsArray buildArgs(String... args) {
        var builder = ArgsArray.newBuilder();
        for (var arg : args) {
            builder.addArgs(ByteString.copyFromUtf8(arg));
        }
        return builder.build();
    }
}
