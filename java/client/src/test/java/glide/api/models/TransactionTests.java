/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static command_request.CommandRequestOuterClass.RequestType.Append;
import static command_request.CommandRequestOuterClass.RequestType.BLMPop;
import static command_request.CommandRequestOuterClass.RequestType.BLMove;
import static command_request.CommandRequestOuterClass.RequestType.BLPop;
import static command_request.CommandRequestOuterClass.RequestType.BRPop;
import static command_request.CommandRequestOuterClass.RequestType.BZMPop;
import static command_request.CommandRequestOuterClass.RequestType.BZPopMax;
import static command_request.CommandRequestOuterClass.RequestType.BZPopMin;
import static command_request.CommandRequestOuterClass.RequestType.BitCount;
import static command_request.CommandRequestOuterClass.RequestType.BitField;
import static command_request.CommandRequestOuterClass.RequestType.BitFieldReadOnly;
import static command_request.CommandRequestOuterClass.RequestType.BitOp;
import static command_request.CommandRequestOuterClass.RequestType.BitPos;
import static command_request.CommandRequestOuterClass.RequestType.ClientGetName;
import static command_request.CommandRequestOuterClass.RequestType.ClientId;
import static command_request.CommandRequestOuterClass.RequestType.ConfigGet;
import static command_request.CommandRequestOuterClass.RequestType.ConfigResetStat;
import static command_request.CommandRequestOuterClass.RequestType.ConfigRewrite;
import static command_request.CommandRequestOuterClass.RequestType.ConfigSet;
import static command_request.CommandRequestOuterClass.RequestType.Copy;
import static command_request.CommandRequestOuterClass.RequestType.DBSize;
import static command_request.CommandRequestOuterClass.RequestType.Decr;
import static command_request.CommandRequestOuterClass.RequestType.DecrBy;
import static command_request.CommandRequestOuterClass.RequestType.Del;
import static command_request.CommandRequestOuterClass.RequestType.Echo;
import static command_request.CommandRequestOuterClass.RequestType.Exists;
import static command_request.CommandRequestOuterClass.RequestType.Expire;
import static command_request.CommandRequestOuterClass.RequestType.ExpireAt;
import static command_request.CommandRequestOuterClass.RequestType.ExpireTime;
import static command_request.CommandRequestOuterClass.RequestType.FCall;
import static command_request.CommandRequestOuterClass.RequestType.FCallReadOnly;
import static command_request.CommandRequestOuterClass.RequestType.FlushAll;
import static command_request.CommandRequestOuterClass.RequestType.FlushDB;
import static command_request.CommandRequestOuterClass.RequestType.FunctionDelete;
import static command_request.CommandRequestOuterClass.RequestType.FunctionFlush;
import static command_request.CommandRequestOuterClass.RequestType.FunctionList;
import static command_request.CommandRequestOuterClass.RequestType.FunctionLoad;
import static command_request.CommandRequestOuterClass.RequestType.FunctionStats;
import static command_request.CommandRequestOuterClass.RequestType.GeoAdd;
import static command_request.CommandRequestOuterClass.RequestType.GeoDist;
import static command_request.CommandRequestOuterClass.RequestType.GeoHash;
import static command_request.CommandRequestOuterClass.RequestType.GeoPos;
import static command_request.CommandRequestOuterClass.RequestType.Get;
import static command_request.CommandRequestOuterClass.RequestType.GetBit;
import static command_request.CommandRequestOuterClass.RequestType.GetDel;
import static command_request.CommandRequestOuterClass.RequestType.GetEx;
import static command_request.CommandRequestOuterClass.RequestType.GetRange;
import static command_request.CommandRequestOuterClass.RequestType.HDel;
import static command_request.CommandRequestOuterClass.RequestType.HExists;
import static command_request.CommandRequestOuterClass.RequestType.HGet;
import static command_request.CommandRequestOuterClass.RequestType.HGetAll;
import static command_request.CommandRequestOuterClass.RequestType.HIncrBy;
import static command_request.CommandRequestOuterClass.RequestType.HIncrByFloat;
import static command_request.CommandRequestOuterClass.RequestType.HKeys;
import static command_request.CommandRequestOuterClass.RequestType.HLen;
import static command_request.CommandRequestOuterClass.RequestType.HMGet;
import static command_request.CommandRequestOuterClass.RequestType.HRandField;
import static command_request.CommandRequestOuterClass.RequestType.HSet;
import static command_request.CommandRequestOuterClass.RequestType.HSetNX;
import static command_request.CommandRequestOuterClass.RequestType.HStrlen;
import static command_request.CommandRequestOuterClass.RequestType.HVals;
import static command_request.CommandRequestOuterClass.RequestType.Incr;
import static command_request.CommandRequestOuterClass.RequestType.IncrBy;
import static command_request.CommandRequestOuterClass.RequestType.IncrByFloat;
import static command_request.CommandRequestOuterClass.RequestType.Info;
import static command_request.CommandRequestOuterClass.RequestType.LCS;
import static command_request.CommandRequestOuterClass.RequestType.LIndex;
import static command_request.CommandRequestOuterClass.RequestType.LInsert;
import static command_request.CommandRequestOuterClass.RequestType.LLen;
import static command_request.CommandRequestOuterClass.RequestType.LMPop;
import static command_request.CommandRequestOuterClass.RequestType.LMove;
import static command_request.CommandRequestOuterClass.RequestType.LPop;
import static command_request.CommandRequestOuterClass.RequestType.LPos;
import static command_request.CommandRequestOuterClass.RequestType.LPush;
import static command_request.CommandRequestOuterClass.RequestType.LPushX;
import static command_request.CommandRequestOuterClass.RequestType.LRange;
import static command_request.CommandRequestOuterClass.RequestType.LRem;
import static command_request.CommandRequestOuterClass.RequestType.LSet;
import static command_request.CommandRequestOuterClass.RequestType.LTrim;
import static command_request.CommandRequestOuterClass.RequestType.LastSave;
import static command_request.CommandRequestOuterClass.RequestType.Lolwut;
import static command_request.CommandRequestOuterClass.RequestType.MGet;
import static command_request.CommandRequestOuterClass.RequestType.MSet;
import static command_request.CommandRequestOuterClass.RequestType.MSetNX;
import static command_request.CommandRequestOuterClass.RequestType.ObjectEncoding;
import static command_request.CommandRequestOuterClass.RequestType.ObjectFreq;
import static command_request.CommandRequestOuterClass.RequestType.ObjectIdleTime;
import static command_request.CommandRequestOuterClass.RequestType.ObjectRefCount;
import static command_request.CommandRequestOuterClass.RequestType.PExpire;
import static command_request.CommandRequestOuterClass.RequestType.PExpireAt;
import static command_request.CommandRequestOuterClass.RequestType.PExpireTime;
import static command_request.CommandRequestOuterClass.RequestType.PTTL;
import static command_request.CommandRequestOuterClass.RequestType.Persist;
import static command_request.CommandRequestOuterClass.RequestType.PfAdd;
import static command_request.CommandRequestOuterClass.RequestType.PfCount;
import static command_request.CommandRequestOuterClass.RequestType.PfMerge;
import static command_request.CommandRequestOuterClass.RequestType.Ping;
import static command_request.CommandRequestOuterClass.RequestType.RPop;
import static command_request.CommandRequestOuterClass.RequestType.RPush;
import static command_request.CommandRequestOuterClass.RequestType.RPushX;
import static command_request.CommandRequestOuterClass.RequestType.RandomKey;
import static command_request.CommandRequestOuterClass.RequestType.Rename;
import static command_request.CommandRequestOuterClass.RequestType.RenameNX;
import static command_request.CommandRequestOuterClass.RequestType.SAdd;
import static command_request.CommandRequestOuterClass.RequestType.SCard;
import static command_request.CommandRequestOuterClass.RequestType.SDiff;
import static command_request.CommandRequestOuterClass.RequestType.SDiffStore;
import static command_request.CommandRequestOuterClass.RequestType.SInter;
import static command_request.CommandRequestOuterClass.RequestType.SInterCard;
import static command_request.CommandRequestOuterClass.RequestType.SInterStore;
import static command_request.CommandRequestOuterClass.RequestType.SIsMember;
import static command_request.CommandRequestOuterClass.RequestType.SMIsMember;
import static command_request.CommandRequestOuterClass.RequestType.SMembers;
import static command_request.CommandRequestOuterClass.RequestType.SMove;
import static command_request.CommandRequestOuterClass.RequestType.SPop;
import static command_request.CommandRequestOuterClass.RequestType.SRandMember;
import static command_request.CommandRequestOuterClass.RequestType.SRem;
import static command_request.CommandRequestOuterClass.RequestType.SUnion;
import static command_request.CommandRequestOuterClass.RequestType.SUnionStore;
import static command_request.CommandRequestOuterClass.RequestType.Set;
import static command_request.CommandRequestOuterClass.RequestType.SetBit;
import static command_request.CommandRequestOuterClass.RequestType.SetRange;
import static command_request.CommandRequestOuterClass.RequestType.Sort;
import static command_request.CommandRequestOuterClass.RequestType.SortReadOnly;
import static command_request.CommandRequestOuterClass.RequestType.Strlen;
import static command_request.CommandRequestOuterClass.RequestType.TTL;
import static command_request.CommandRequestOuterClass.RequestType.Time;
import static command_request.CommandRequestOuterClass.RequestType.Touch;
import static command_request.CommandRequestOuterClass.RequestType.Type;
import static command_request.CommandRequestOuterClass.RequestType.Unlink;
import static command_request.CommandRequestOuterClass.RequestType.XAck;
import static command_request.CommandRequestOuterClass.RequestType.XAdd;
import static command_request.CommandRequestOuterClass.RequestType.XDel;
import static command_request.CommandRequestOuterClass.RequestType.XGroupCreate;
import static command_request.CommandRequestOuterClass.RequestType.XGroupCreateConsumer;
import static command_request.CommandRequestOuterClass.RequestType.XGroupDelConsumer;
import static command_request.CommandRequestOuterClass.RequestType.XGroupDestroy;
import static command_request.CommandRequestOuterClass.RequestType.XLen;
import static command_request.CommandRequestOuterClass.RequestType.XPending;
import static command_request.CommandRequestOuterClass.RequestType.XRange;
import static command_request.CommandRequestOuterClass.RequestType.XRead;
import static command_request.CommandRequestOuterClass.RequestType.XReadGroup;
import static command_request.CommandRequestOuterClass.RequestType.XRevRange;
import static command_request.CommandRequestOuterClass.RequestType.XTrim;
import static command_request.CommandRequestOuterClass.RequestType.ZAdd;
import static command_request.CommandRequestOuterClass.RequestType.ZCard;
import static command_request.CommandRequestOuterClass.RequestType.ZCount;
import static command_request.CommandRequestOuterClass.RequestType.ZDiff;
import static command_request.CommandRequestOuterClass.RequestType.ZDiffStore;
import static command_request.CommandRequestOuterClass.RequestType.ZIncrBy;
import static command_request.CommandRequestOuterClass.RequestType.ZInter;
import static command_request.CommandRequestOuterClass.RequestType.ZInterCard;
import static command_request.CommandRequestOuterClass.RequestType.ZInterStore;
import static command_request.CommandRequestOuterClass.RequestType.ZLexCount;
import static command_request.CommandRequestOuterClass.RequestType.ZMPop;
import static command_request.CommandRequestOuterClass.RequestType.ZMScore;
import static command_request.CommandRequestOuterClass.RequestType.ZPopMax;
import static command_request.CommandRequestOuterClass.RequestType.ZPopMin;
import static command_request.CommandRequestOuterClass.RequestType.ZRandMember;
import static command_request.CommandRequestOuterClass.RequestType.ZRange;
import static command_request.CommandRequestOuterClass.RequestType.ZRangeStore;
import static command_request.CommandRequestOuterClass.RequestType.ZRank;
import static command_request.CommandRequestOuterClass.RequestType.ZRem;
import static command_request.CommandRequestOuterClass.RequestType.ZRemRangeByLex;
import static command_request.CommandRequestOuterClass.RequestType.ZRemRangeByRank;
import static command_request.CommandRequestOuterClass.RequestType.ZRemRangeByScore;
import static command_request.CommandRequestOuterClass.RequestType.ZRevRank;
import static command_request.CommandRequestOuterClass.RequestType.ZScore;
import static command_request.CommandRequestOuterClass.RequestType.ZUnion;
import static command_request.CommandRequestOuterClass.RequestType.ZUnionStore;
import static glide.api.commands.GenericBaseCommands.REPLACE_REDIS_API;
import static glide.api.commands.HashBaseCommands.WITH_VALUES_REDIS_API;
import static glide.api.commands.ServerManagementCommands.VERSION_REDIS_API;
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
import static glide.api.models.commands.stream.StreamGroupOptions.ENTRIES_READ_REDIS_API;
import static glide.api.models.commands.stream.StreamGroupOptions.MAKE_STREAM_REDIS_API;
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

import com.google.protobuf.ByteString;
import command_request.CommandRequestOuterClass.Command;
import command_request.CommandRequestOuterClass.Command.ArgsArray;
import command_request.CommandRequestOuterClass.RequestType;
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
import glide.api.models.commands.geospatial.GeoUnit;
import glide.api.models.commands.geospatial.GeospatialData;
import glide.api.models.commands.stream.StreamAddOptions;
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
                                "key", "group", "id", MAKE_STREAM_REDIS_API, ENTRIES_READ_REDIS_API, "entry")));

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
