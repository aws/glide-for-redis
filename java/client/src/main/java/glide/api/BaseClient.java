/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static command_request.CommandRequestOuterClass.CacheMetricsType;
import static command_request.CommandRequestOuterClass.RequestType.AclCat;
import static command_request.CommandRequestOuterClass.RequestType.AclDelUser;
import static command_request.CommandRequestOuterClass.RequestType.AclDryRun;
import static command_request.CommandRequestOuterClass.RequestType.AclGenPass;
import static command_request.CommandRequestOuterClass.RequestType.AclGetUser;
import static command_request.CommandRequestOuterClass.RequestType.AclList;
import static command_request.CommandRequestOuterClass.RequestType.AclLoad;
import static command_request.CommandRequestOuterClass.RequestType.AclLog;
import static command_request.CommandRequestOuterClass.RequestType.AclSave;
import static command_request.CommandRequestOuterClass.RequestType.AclSetUser;
import static command_request.CommandRequestOuterClass.RequestType.AclUsers;
import static command_request.CommandRequestOuterClass.RequestType.AclWhoami;
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
import static command_request.CommandRequestOuterClass.RequestType.Copy;
import static command_request.CommandRequestOuterClass.RequestType.Decr;
import static command_request.CommandRequestOuterClass.RequestType.DecrBy;
import static command_request.CommandRequestOuterClass.RequestType.Del;
import static command_request.CommandRequestOuterClass.RequestType.Dump;
import static command_request.CommandRequestOuterClass.RequestType.EvalReadOnly;
import static command_request.CommandRequestOuterClass.RequestType.EvalShaReadOnly;
import static command_request.CommandRequestOuterClass.RequestType.Exists;
import static command_request.CommandRequestOuterClass.RequestType.Expire;
import static command_request.CommandRequestOuterClass.RequestType.ExpireAt;
import static command_request.CommandRequestOuterClass.RequestType.ExpireTime;
import static command_request.CommandRequestOuterClass.RequestType.FCall;
import static command_request.CommandRequestOuterClass.RequestType.FCallReadOnly;
import static command_request.CommandRequestOuterClass.RequestType.GeoAdd;
import static command_request.CommandRequestOuterClass.RequestType.GeoDist;
import static command_request.CommandRequestOuterClass.RequestType.GeoHash;
import static command_request.CommandRequestOuterClass.RequestType.GeoPos;
import static command_request.CommandRequestOuterClass.RequestType.GeoSearch;
import static command_request.CommandRequestOuterClass.RequestType.GeoSearchStore;
import static command_request.CommandRequestOuterClass.RequestType.Get;
import static command_request.CommandRequestOuterClass.RequestType.GetBit;
import static command_request.CommandRequestOuterClass.RequestType.GetDel;
import static command_request.CommandRequestOuterClass.RequestType.GetEx;
import static command_request.CommandRequestOuterClass.RequestType.GetRange;
import static command_request.CommandRequestOuterClass.RequestType.HDel;
import static command_request.CommandRequestOuterClass.RequestType.HExists;
import static command_request.CommandRequestOuterClass.RequestType.HExpire;
import static command_request.CommandRequestOuterClass.RequestType.HExpireAt;
import static command_request.CommandRequestOuterClass.RequestType.HExpireTime;
import static command_request.CommandRequestOuterClass.RequestType.HGet;
import static command_request.CommandRequestOuterClass.RequestType.HGetAll;
import static command_request.CommandRequestOuterClass.RequestType.HGetEx;
import static command_request.CommandRequestOuterClass.RequestType.HIncrBy;
import static command_request.CommandRequestOuterClass.RequestType.HIncrByFloat;
import static command_request.CommandRequestOuterClass.RequestType.HKeys;
import static command_request.CommandRequestOuterClass.RequestType.HLen;
import static command_request.CommandRequestOuterClass.RequestType.HMGet;
import static command_request.CommandRequestOuterClass.RequestType.HPExpire;
import static command_request.CommandRequestOuterClass.RequestType.HPExpireAt;
import static command_request.CommandRequestOuterClass.RequestType.HPExpireTime;
import static command_request.CommandRequestOuterClass.RequestType.HPTtl;
import static command_request.CommandRequestOuterClass.RequestType.HPersist;
import static command_request.CommandRequestOuterClass.RequestType.HRandField;
import static command_request.CommandRequestOuterClass.RequestType.HScan;
import static command_request.CommandRequestOuterClass.RequestType.HSet;
import static command_request.CommandRequestOuterClass.RequestType.HSetEx;
import static command_request.CommandRequestOuterClass.RequestType.HSetNX;
import static command_request.CommandRequestOuterClass.RequestType.HStrlen;
import static command_request.CommandRequestOuterClass.RequestType.HTtl;
import static command_request.CommandRequestOuterClass.RequestType.HVals;
import static command_request.CommandRequestOuterClass.RequestType.Incr;
import static command_request.CommandRequestOuterClass.RequestType.IncrBy;
import static command_request.CommandRequestOuterClass.RequestType.IncrByFloat;
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
import static command_request.CommandRequestOuterClass.RequestType.MGet;
import static command_request.CommandRequestOuterClass.RequestType.MSet;
import static command_request.CommandRequestOuterClass.RequestType.MSetNX;
import static command_request.CommandRequestOuterClass.RequestType.Migrate;
import static command_request.CommandRequestOuterClass.RequestType.Move;
import static command_request.CommandRequestOuterClass.RequestType.ObjectEncoding;
import static command_request.CommandRequestOuterClass.RequestType.ObjectFreq;
import static command_request.CommandRequestOuterClass.RequestType.ObjectIdleTime;
import static command_request.CommandRequestOuterClass.RequestType.ObjectRefCount;
import static command_request.CommandRequestOuterClass.RequestType.PExpire;
import static command_request.CommandRequestOuterClass.RequestType.PExpireAt;
import static command_request.CommandRequestOuterClass.RequestType.PExpireTime;
import static command_request.CommandRequestOuterClass.RequestType.PSubscribe;
import static command_request.CommandRequestOuterClass.RequestType.PSubscribeBlocking;
import static command_request.CommandRequestOuterClass.RequestType.PTTL;
import static command_request.CommandRequestOuterClass.RequestType.PUnsubscribe;
import static command_request.CommandRequestOuterClass.RequestType.PUnsubscribeBlocking;
import static command_request.CommandRequestOuterClass.RequestType.Persist;
import static command_request.CommandRequestOuterClass.RequestType.PfAdd;
import static command_request.CommandRequestOuterClass.RequestType.PfCount;
import static command_request.CommandRequestOuterClass.RequestType.PfMerge;
import static command_request.CommandRequestOuterClass.RequestType.PubSubChannels;
import static command_request.CommandRequestOuterClass.RequestType.PubSubNumPat;
import static command_request.CommandRequestOuterClass.RequestType.PubSubNumSub;
import static command_request.CommandRequestOuterClass.RequestType.Publish;
import static command_request.CommandRequestOuterClass.RequestType.RPop;
import static command_request.CommandRequestOuterClass.RequestType.RPush;
import static command_request.CommandRequestOuterClass.RequestType.RPushX;
import static command_request.CommandRequestOuterClass.RequestType.Rename;
import static command_request.CommandRequestOuterClass.RequestType.RenameNX;
import static command_request.CommandRequestOuterClass.RequestType.Restore;
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
import static command_request.CommandRequestOuterClass.RequestType.SScan;
import static command_request.CommandRequestOuterClass.RequestType.SUnion;
import static command_request.CommandRequestOuterClass.RequestType.SUnionStore;
import static command_request.CommandRequestOuterClass.RequestType.ScriptDebug;
import static command_request.CommandRequestOuterClass.RequestType.ScriptExists;
import static command_request.CommandRequestOuterClass.RequestType.ScriptFlush;
import static command_request.CommandRequestOuterClass.RequestType.ScriptKill;
import static command_request.CommandRequestOuterClass.RequestType.ScriptShow;
import static command_request.CommandRequestOuterClass.RequestType.Set;
import static command_request.CommandRequestOuterClass.RequestType.SetBit;
import static command_request.CommandRequestOuterClass.RequestType.SetRange;
import static command_request.CommandRequestOuterClass.RequestType.Sort;
import static command_request.CommandRequestOuterClass.RequestType.SortReadOnly;
import static command_request.CommandRequestOuterClass.RequestType.Strlen;
import static command_request.CommandRequestOuterClass.RequestType.Subscribe;
import static command_request.CommandRequestOuterClass.RequestType.SubscribeBlocking;
import static command_request.CommandRequestOuterClass.RequestType.TTL;
import static command_request.CommandRequestOuterClass.RequestType.Touch;
import static command_request.CommandRequestOuterClass.RequestType.Type;
import static command_request.CommandRequestOuterClass.RequestType.Unlink;
import static command_request.CommandRequestOuterClass.RequestType.Unsubscribe;
import static command_request.CommandRequestOuterClass.RequestType.UnsubscribeBlocking;
import static command_request.CommandRequestOuterClass.RequestType.Wait;
import static command_request.CommandRequestOuterClass.RequestType.WaitAof;
import static command_request.CommandRequestOuterClass.RequestType.Watch;
import static command_request.CommandRequestOuterClass.RequestType.XAck;
import static command_request.CommandRequestOuterClass.RequestType.XAdd;
import static command_request.CommandRequestOuterClass.RequestType.XAutoClaim;
import static command_request.CommandRequestOuterClass.RequestType.XClaim;
import static command_request.CommandRequestOuterClass.RequestType.XDel;
import static command_request.CommandRequestOuterClass.RequestType.XGroupCreate;
import static command_request.CommandRequestOuterClass.RequestType.XGroupCreateConsumer;
import static command_request.CommandRequestOuterClass.RequestType.XGroupDelConsumer;
import static command_request.CommandRequestOuterClass.RequestType.XGroupDestroy;
import static command_request.CommandRequestOuterClass.RequestType.XGroupSetId;
import static command_request.CommandRequestOuterClass.RequestType.XInfoConsumers;
import static command_request.CommandRequestOuterClass.RequestType.XInfoGroups;
import static command_request.CommandRequestOuterClass.RequestType.XInfoStream;
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
import static command_request.CommandRequestOuterClass.RequestType.ZScan;
import static command_request.CommandRequestOuterClass.RequestType.ZScore;
import static command_request.CommandRequestOuterClass.RequestType.ZUnion;
import static command_request.CommandRequestOuterClass.RequestType.ZUnionStore;
import static glide.api.models.GlideString.gs;
import static glide.api.models.commands.SortBaseOptions.STORE_COMMAND_STRING;
import static glide.api.models.commands.bitmap.BitFieldOptions.createBitFieldArgs;
import static glide.api.models.commands.bitmap.BitFieldOptions.createBitFieldGlideStringArgs;
import static glide.api.models.commands.stream.StreamClaimOptions.JUST_ID_VALKEY_API;
import static glide.api.models.commands.stream.StreamGroupOptions.ENTRIES_READ_VALKEY_API;
import static glide.api.models.commands.stream.StreamReadOptions.READ_COUNT_VALKEY_API;
import static glide.api.models.commands.stream.XInfoStreamOptions.COUNT;
import static glide.api.models.commands.stream.XInfoStreamOptions.FULL;
import static glide.utils.ArrayTransformUtils.cast3DArray;
import static glide.utils.ArrayTransformUtils.castArray;
import static glide.utils.ArrayTransformUtils.castArrayofArrays;
import static glide.utils.ArrayTransformUtils.castBinaryStringMapOfArrays;
import static glide.utils.ArrayTransformUtils.castMapOf2DArray;
import static glide.utils.ArrayTransformUtils.castMapOfArrays;
import static glide.utils.ArrayTransformUtils.concatenateArrays;
import static glide.utils.ArrayTransformUtils.convertBinaryStringKeyValueArrayToMap;
import static glide.utils.ArrayTransformUtils.convertKeyValueArrayToMap;
import static glide.utils.ArrayTransformUtils.convertMapToKeyValueGlideStringArray;
import static glide.utils.ArrayTransformUtils.convertMapToKeyValueStringArray;
import static glide.utils.ArrayTransformUtils.convertMapToValueKeyStringArray;
import static glide.utils.ArrayTransformUtils.convertMapToValueKeyStringArrayBinary;
import static glide.utils.ArrayTransformUtils.convertNestedArrayToKeyValueGlideStringArray;
import static glide.utils.ArrayTransformUtils.convertNestedArrayToKeyValueStringArray;
import static glide.utils.ArrayTransformUtils.mapGeoDataToArray;
import static glide.utils.ArrayTransformUtils.mapGeoDataToGlideStringArray;

import glide.api.commands.BitmapBaseCommands;
import glide.api.commands.GenericBaseCommands;
import glide.api.commands.GeospatialIndicesBaseCommands;
import glide.api.commands.HashBaseCommands;
import glide.api.commands.HyperLogLogBaseCommands;
import glide.api.commands.ListBaseCommands;
import glide.api.commands.PubSubBaseCommands;
import glide.api.commands.ScriptingAndFunctionsBaseCommands;
import glide.api.commands.SetBaseCommands;
import glide.api.commands.SortedSetBaseCommands;
import glide.api.commands.StreamBaseCommands;
import glide.api.commands.StringBaseCommands;
import glide.api.commands.TransactionsBaseCommands;
import glide.api.logging.Logger;
import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.PubSubMessage;
import glide.api.models.Script;
import glide.api.models.commands.ExpireOptions;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.GetExOptions;
import glide.api.models.commands.HGetExOptions;
import glide.api.models.commands.HSetExOptions;
import glide.api.models.commands.HashFieldExpirationConditionOptions;
import glide.api.models.commands.LInsertOptions.InsertPosition;
import glide.api.models.commands.LPosOptions;
import glide.api.models.commands.ListDirection;
import glide.api.models.commands.MigrateOptions;
import glide.api.models.commands.RangeOptions;
import glide.api.models.commands.RangeOptions.LexRange;
import glide.api.models.commands.RangeOptions.RangeQuery;
import glide.api.models.commands.RangeOptions.ScoreRange;
import glide.api.models.commands.RangeOptions.ScoredRangeQuery;
import glide.api.models.commands.RestoreOptions;
import glide.api.models.commands.ScoreFilter;
import glide.api.models.commands.ScriptDebugMode;
import glide.api.models.commands.ScriptOptions;
import glide.api.models.commands.ScriptOptionsGlideString;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.SortOptions;
import glide.api.models.commands.SortOptionsBinary;
import glide.api.models.commands.WeightAggregateOptions.Aggregate;
import glide.api.models.commands.WeightAggregateOptions.KeyArray;
import glide.api.models.commands.WeightAggregateOptions.KeyArrayBinary;
import glide.api.models.commands.WeightAggregateOptions.KeysOrWeightedKeys;
import glide.api.models.commands.WeightAggregateOptions.KeysOrWeightedKeysBinary;
import glide.api.models.commands.ZAddOptions;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldReadOnlySubCommands;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldSubCommands;
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
import glide.api.models.commands.scan.HScanOptionsBinary;
import glide.api.models.commands.scan.SScanOptions;
import glide.api.models.commands.scan.SScanOptionsBinary;
import glide.api.models.commands.scan.ZScanOptions;
import glide.api.models.commands.scan.ZScanOptionsBinary;
import glide.api.models.commands.stream.StreamAddOptions;
import glide.api.models.commands.stream.StreamAddOptionsBinary;
import glide.api.models.commands.stream.StreamClaimOptions;
import glide.api.models.commands.stream.StreamGroupOptions;
import glide.api.models.commands.stream.StreamPendingOptions;
import glide.api.models.commands.stream.StreamPendingOptionsBinary;
import glide.api.models.commands.stream.StreamRange;
import glide.api.models.commands.stream.StreamReadGroupOptions;
import glide.api.models.commands.stream.StreamReadOptions;
import glide.api.models.commands.stream.StreamTrimOptions;
import glide.api.models.configuration.BaseClientConfiguration;
import glide.api.models.configuration.BaseSubscriptionConfiguration;
import glide.api.models.configuration.ServerCredentials;
import glide.api.models.exceptions.ConfigurationError;
import glide.api.models.exceptions.GlideException;
import glide.connectors.handlers.MessageHandler;
import glide.ffi.resolvers.GlideValueResolver;
import glide.ffi.resolvers.NativeUtils;
import glide.ffi.resolvers.StatisticsResolver;
import glide.internal.GlideCoreClient;
import glide.managers.BaseResponseResolver;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import glide.utils.ArgsBuilder;
import glide.utils.BufferUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.apache.commons.lang3.ArrayUtils;
import response.ResponseOuterClass.ConstantResponse;
import response.ResponseOuterClass.Response;

/** Base Client class */
public abstract class BaseClient
        implements AutoCloseable,
                BitmapBaseCommands,
                GenericBaseCommands,
                StringBaseCommands,
                HashBaseCommands,
                ListBaseCommands,
                SetBaseCommands,
                SortedSetBaseCommands,
                StreamBaseCommands,
                HyperLogLogBaseCommands,
                GeospatialIndicesBaseCommands,
                ScriptingAndFunctionsBaseCommands,
                TransactionsBaseCommands,
                PubSubBaseCommands {

    /** Valkey simple string response with "OK" */
    public static final String OK = ConstantResponse.OK.toString();

    // Response processing flags
    public enum ResponseFlags {
        ENCODING_UTF8,
        IS_NULLABLE
    }

    // Constants for various commands
    public static final String WITH_VALUES_VALKEY_API = "WITHVALUES";
    public static final String COUNT_VALKEY_API = "COUNT";
    public static final String WITH_SCORE_VALKEY_API = "WITHSCORE";
    public static final String WITH_SCORES_VALKEY_API = "WITHSCORES";
    public static final String LIMIT_VALKEY_API = "LIMIT";
    public static final String SET_LIMIT_VALKEY_API = "LIMIT";
    public static final String COUNT_FOR_LIST_VALKEY_API = "COUNT";
    public static final String REPLACE_VALKEY_API = "REPLACE";
    public static final String LEN_VALKEY_API = "LEN";
    public static final String IDX_COMMAND_STRING = "IDX";
    public static final String MINMATCHLEN_COMMAND_STRING = "MINMATCHLEN";
    public static final String WITHMATCHLEN_COMMAND_STRING = "WITHMATCHLEN";
    public static final String LCS_MATCHES_RESULT_KEY = "matches";

    protected static final String SCHEDULE_VALKEY_API = "SCHEDULE";
    protected static final String CANCEL_VALKEY_API = "CANCEL";

    // Constant empty arrays to reduce allocations
    protected static final String[] EMPTY_STRING_ARRAY = new String[0];
    protected static final GlideString[] EMPTY_GLIDE_STRING_ARRAY = new GlideString[0];

    // Client components
    protected final CommandManager commandManager;
    protected final ConnectionManager connectionManager;
    protected final MessageHandler messageHandler;
    protected final Optional<BaseSubscriptionConfiguration> subscriptionConfiguration;

    // Native library loading
    static {
        try {
            NativeUtils.loadGlideLib();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load native library", e);
        }
    }

    /** Helper which extracts data from received {@link Response}s from GLIDE. */
    private static final BaseResponseResolver responseResolver =
            new BaseResponseResolver(
                    pointer -> {
                        if (pointer == null || pointer == 0) {
                            return null;
                        }
                        // The pointer is an ID to retrieve the object from the registry
                        // The object was already converted from Redis Value to Java in the native layer
                        return GlideValueResolver.valueFromPointer(pointer);
                    });

    /** Helper which extracts data with binary strings from received {@link Response}s from GLIDE. */
    private static final BaseResponseResolver binaryResponseResolver =
            new BaseResponseResolver(
                    pointer -> {
                        if (pointer == null || pointer == 0) {
                            return null;
                        }
                        // The pointer is an ID to retrieve the object from the registry
                        // The object was already converted with binary encoding in the native layer
                        return GlideValueResolver.valueFromPointerBinary(pointer);
                    });

    /** Constructor for client initialization */
    protected BaseClient(ClientBuilder builder) {
        this.connectionManager = builder.connectionManager;
        this.commandManager = builder.commandManager;
        this.messageHandler = builder.messageHandler;
        this.subscriptionConfiguration = builder.subscriptionConfiguration;
    }

    /** Auxiliary builder which wraps all fields */
    protected static class ClientBuilder {
        private final ConnectionManager connectionManager;
        private final CommandManager commandManager;
        private final MessageHandler messageHandler;
        private final Optional<BaseSubscriptionConfiguration> subscriptionConfiguration;

        protected ClientBuilder(
                ConnectionManager connectionManager,
                CommandManager commandManager,
                MessageHandler messageHandler,
                Optional<BaseSubscriptionConfiguration> subscriptionConfiguration) {
            this.connectionManager = connectionManager;
            this.commandManager = commandManager;
            this.messageHandler = messageHandler;
            this.subscriptionConfiguration = subscriptionConfiguration;
        }
    }

    /**
     * Create native client
     *
     * @param config client Configuration.
     * @param constructor client constructor reference.
     * @param <T> Client type.
     * @return a Future to connect and return a client.
     */
    protected static <T extends BaseClient> CompletableFuture<T> createClient(
            @NonNull BaseClientConfiguration config, Function<ClientBuilder, T> constructor) {
        // Validate protocol compatibility for PubSub subscriptions
        if (config.getSubscriptionConfiguration() != null
                && config.getProtocol() == glide.api.models.configuration.ProtocolVersion.RESP2) {
            throw new ConfigurationError("PubSub subscriptions require RESP3 protocol");
        }
        ConnectionManager.validateClientAz(config);
        try {
            // Create client components using build methods
            ConnectionManager connectionManager = buildConnectionManager();
            MessageHandler messageHandler = buildMessageHandler(config);

            // Return async chain
            return connectionManager
                    .connectToValkey(config)
                    .thenApply(
                            ignored -> {
                                // Create CommandManager after connection established
                                CommandManager commandManager = buildCommandManager(connectionManager);

                                T client =
                                        constructor.apply(
                                                new ClientBuilder(
                                                        connectionManager,
                                                        commandManager,
                                                        messageHandler,
                                                        Optional.ofNullable(config.getSubscriptionConfiguration())));
                                try {
                                    glide.internal.GlideCoreClient.registerClient(
                                            connectionManager.getNativeClientHandle(), client);
                                } catch (Throwable t) {
                                    glide.api.logging.Logger.log(
                                            glide.api.logging.Logger.Level.WARN,
                                            "BaseClient",
                                            () ->
                                                    "Failed to register native client (handle="
                                                            + connectionManager.getNativeClientHandle()
                                                            + ") - continuing",
                                            t);
                                }
                                return client;
                            });
        } catch (Exception e) {
            // Something bad happened during initial setup
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /** Build ConnectionManager for native client */
    protected static ConnectionManager buildConnectionManager() {
        return new ConnectionManager();
    }

    /** Build MessageHandler for native client */
    protected static MessageHandler buildMessageHandler(BaseClientConfiguration config) {
        // TODO: Extract callback and context from config
        return new MessageHandler(
                config.getSubscriptionConfiguration() != null
                        ? config.getSubscriptionConfiguration().getCallback()
                        : Optional.empty(),
                config.getSubscriptionConfiguration() != null
                        ? config.getSubscriptionConfiguration().getContext()
                        : Optional.empty(),
                responseResolver);
    }

    /** Build CommandManager for native client */
    protected static CommandManager buildCommandManager(ConnectionManager connectionManager) {
        // CommandManager will be created after connection is established
        // We'll update this once the connection provides the native handle
        GlideCoreClient core =
                new GlideCoreClient(
                        connectionManager.getNativeClientHandle(),
                        connectionManager.getMaxInflightRequests(),
                        connectionManager.getRequestTimeoutMs());
        // Register for PubSub push delivery
        try {
            GlideCoreClient.registerClient(connectionManager.getNativeClientHandle(), null);
        } catch (Throwable t) {
            glide.api.logging.Logger.log(
                    glide.api.logging.Logger.Level.WARN,
                    "BaseClient",
                    () ->
                            "Failed to register native client for push delivery (handle="
                                    + connectionManager.getNativeClientHandle()
                                    + ") - continuing",
                    t);
        }
        return new CommandManager(core);
    }

    /**
     * Return a statistics
     *
     * @return Return a {@link Map} that contains the statistics collected internally by GLIDE core
     */
    public Map<String, String> getStatistics() {
        return StatisticsResolver.getStatistics();
    }

    /**
     * Return a next pubsub message if it is present.
     *
     * @throws ConfigurationError If client is not subscribed to any channel or if client configured
     *     with a callback.
     * @return A message if any or <code>null</code> if there are no unread messages.
     */
    public PubSubMessage tryGetPubSubMessage() {
        if (subscriptionConfiguration.isPresent()
                && subscriptionConfiguration.get().getCallback().isPresent()) {
            throw new ConfigurationError(
                    "The operation will never complete since messages will be passed to the configured"
                            + " callback.");
        }
        // Pull next message from internal queue (non-blocking)
        return messageHandler.getQueue().popSync();
    }

    /**
     * Returns a promise for a next pubsub message.<br>
     * Message gets unrecoverable lost if future is cancelled or reference to this future is lost.
     *
     * @throws ConfigurationError If client is not subscribed to any channel or if client configured
     *     with a callback.
     * @return A {@link CompletableFuture} which will asynchronously hold the next available message.
     */
    public CompletableFuture<PubSubMessage> getPubSubMessage() {
        if (subscriptionConfiguration.isPresent()
                && subscriptionConfiguration.get().getCallback().isPresent()) {
            throw new ConfigurationError(
                    "The operation will never complete since messages will be passed to the configured"
                            + " callback.");
        }
        // Return a future for the next message (non-blocking await)
        return messageHandler.getQueue().popAsync();
    }

    /**
     * Returns the number of pubsub messages currently buffered and ready for consumption.
     *
     * @return The number of buffered messages.
     */
    public int getPubSubMessageCount() {
        return messageHandler.getQueue().size();
    }

    /**
     * Closes this resource, relinquishing any underlying resources. This method is invoked
     * automatically on objects managed by the try-with-resources statement.
     *
     * @see <a
     *     href="https://docs.oracle.com/javase/8/docs/api/java/lang/AutoCloseable.html#close--">AutoCloseable::close()</a>
     */
    @Override
    public void close() throws ExecutionException {
        connectionManager.closeConnectionSync();
    }

    /** Check if the client is connected and ready for commands. */
    public boolean isConnected() {
        return connectionManager.isConnected();
    }

    /** Get client information from the native layer. */
    public String getClientInfo() {
        return connectionManager.getClientInfo();
    }

    /**
     * Extracts the value from a <code>GLIDE core</code> response message and either throws an
     * exception or returns the value as an object of type <code>T</code>.
     *
     * @param response protobuf message.
     * @param classType Parameter <code>T</code> class type.
     * @param flags A set of parameters which describes how to handle the response. Could be empty or
     *     any combination of
     *     <ul>
     *       <li>{@link ResponseFlags#ENCODING_UTF8} to return the data as a <code>String</code>; if
     *           unset, a <code>byte[]</code> is returned.
     *       <li>{@link ResponseFlags#IS_NULLABLE} to accept <code>null</code> values.
     *     </ul>
     *
     * @return Response as an object of type <code>T</code> or <code>null</code>.
     * @param <T> The return value type.
     * @throws GlideException On a type mismatch.
     */
    @SuppressWarnings("unchecked")
    protected <T> T handleValkeyResponse(
            Class<T> classType, EnumSet<ResponseFlags> flags, Response response) throws GlideException {
        boolean encodingUtf8 = flags.contains(ResponseFlags.ENCODING_UTF8);
        boolean isNullable = flags.contains(ResponseFlags.IS_NULLABLE);
        Object value =
                encodingUtf8 ? responseResolver.apply(response) : binaryResponseResolver.apply(response);
        if (isNullable && (value == null)) {
            return null;
        }

        // Handle DirectByteBuffer conversion for large responses (>16KB)
        if (value instanceof java.nio.ByteBuffer) {
            java.nio.ByteBuffer duplicatedBuffer = ((java.nio.ByteBuffer) value).duplicate();
            duplicatedBuffer.rewind();
            java.nio.ByteBuffer readOnlyBuffer = duplicatedBuffer.asReadOnlyBuffer();
            if (classType == String.class && encodingUtf8) {
                value = java.nio.charset.StandardCharsets.UTF_8.decode(readOnlyBuffer).toString();
            } else if (classType == GlideString.class) {
                value = GlideString.of(copyBufferToArray(readOnlyBuffer));
            } else if (classType == byte[].class) {
                value = copyBufferToArray(readOnlyBuffer);
            } else if (classType == Map.class) {
                // Deserialize Map from DirectByteBuffer without intermediate copy
                value = deserializeMapFromBuffer(readOnlyBuffer, encodingUtf8);
            }
        }

        value = convertByteArrayToGlideString(value);

        if (classType.isInstance(value)) {
            return (T) value;
        }
        String className = value == null ? "null" : value.getClass().getSimpleName();
        throw new GlideException(
                "Unexpected return type from Glide: got "
                        + className
                        + " expected "
                        + classType.getSimpleName());
    }

    protected Object handleObjectOrNullResponse(Response response) throws GlideException {
        return handleValkeyResponse(
                Object.class, EnumSet.of(ResponseFlags.IS_NULLABLE, ResponseFlags.ENCODING_UTF8), response);
    }

    protected Object handleBinaryObjectOrNullResponse(Response response) throws GlideException {
        return handleValkeyResponse(Object.class, EnumSet.of(ResponseFlags.IS_NULLABLE), response);
    }

    protected String handleStringResponse(Response response) throws GlideException {
        return handleValkeyResponse(String.class, EnumSet.of(ResponseFlags.ENCODING_UTF8), response);
    }

    protected String handleStringOrNullResponse(Response response) throws GlideException {
        return handleValkeyResponse(
                String.class, EnumSet.of(ResponseFlags.IS_NULLABLE, ResponseFlags.ENCODING_UTF8), response);
    }

    protected byte[] handleBytesOrNullResponse(Response response) throws GlideException {
        GlideString result =
                handleValkeyResponse(GlideString.class, EnumSet.of(ResponseFlags.IS_NULLABLE), response);
        if (result == null) return null;

        return result.getBytes();
    }

    protected GlideString handleGlideStringOrNullResponse(Response response) throws GlideException {
        return handleValkeyResponse(GlideString.class, EnumSet.of(ResponseFlags.IS_NULLABLE), response);
    }

    protected GlideString handleGlideStringResponse(Response response) throws GlideException {
        return handleValkeyResponse(GlideString.class, EnumSet.noneOf(ResponseFlags.class), response);
    }

    protected Boolean handleBooleanResponse(Response response) throws GlideException {
        return handleValkeyResponse(Boolean.class, EnumSet.noneOf(ResponseFlags.class), response);
    }

    protected Long handleLongResponse(Response response) throws GlideException {
        return handleValkeyResponse(Long.class, EnumSet.noneOf(ResponseFlags.class), response);
    }

    protected Long handleLongOrNullResponse(Response response) throws GlideException {
        return handleValkeyResponse(Long.class, EnumSet.of(ResponseFlags.IS_NULLABLE), response);
    }

    protected Double handleDoubleResponse(Response response) throws GlideException {
        return handleValkeyResponse(Double.class, EnumSet.noneOf(ResponseFlags.class), response);
    }

    protected Double handleDoubleOrNullResponse(Response response) throws GlideException {
        return handleValkeyResponse(Double.class, EnumSet.of(ResponseFlags.IS_NULLABLE), response);
    }

    protected Object[] handleArrayResponse(Response response) throws GlideException {
        return handleValkeyResponse(Object[].class, EnumSet.of(ResponseFlags.ENCODING_UTF8), response);
    }

    protected Object[] handleArrayResponseBinary(Response response) throws GlideException {
        return handleValkeyResponse(Object[].class, EnumSet.noneOf(ResponseFlags.class), response);
    }

    protected Object[] handleArrayOrNullResponse(Response response) throws GlideException {
        return handleValkeyResponse(
                Object[].class,
                EnumSet.of(ResponseFlags.IS_NULLABLE, ResponseFlags.ENCODING_UTF8),
                response);
    }

    protected Object[] handleArrayOrNullResponseBinary(Response response) throws GlideException {
        return handleValkeyResponse(Object[].class, EnumSet.of(ResponseFlags.IS_NULLABLE), response);
    }

    /**
     * @param response A Protobuf response
     * @return A map of <code>String</code> to <code>V</code>.
     * @param <V> Value type.
     */
    @SuppressWarnings("unchecked") // raw Map cast to Map<String, V>
    protected <V> Map<String, V> handleMapResponse(Response response) throws GlideException {
        return handleValkeyResponse(Map.class, EnumSet.of(ResponseFlags.ENCODING_UTF8), response);
    }

    /**
     * Get a map and convert {@link Map} keys from <code>byte[]</code> to {@link String}.
     *
     * @param response A Protobuf response
     * @return A map of <code>GlideString</code> to <code>V</code>.
     * @param <V> Value type.
     */
    @SuppressWarnings("unchecked") // raw Map cast to Map<GlideString, V>
    protected <V> Map<GlideString, V> handleBinaryStringMapResponse(Response response)
            throws GlideException {
        return handleValkeyResponse(Map.class, EnumSet.noneOf(ResponseFlags.class), response);
    }

    /**
     * @param response A Protobuf response
     * @return A map of <code>String</code> to <code>V</code> or <code>null</code>
     * @param <V> Value type.
     */
    @SuppressWarnings("unchecked") // raw Map cast to Map<String, V>
    protected <V> Map<String, V> handleMapOrNullResponse(Response response) throws GlideException {
        return handleValkeyResponse(
                Map.class, EnumSet.of(ResponseFlags.IS_NULLABLE, ResponseFlags.ENCODING_UTF8), response);
    }

    /**
     * @param response A Protobuf response
     * @return A map of <code>String</code> to <code>V</code> or <code>null</code>
     * @param <V> Value type.
     */
    @SuppressWarnings("unchecked") // raw Map cast to Map<String, V>
    protected <V> Map<GlideString, V> handleBinaryStringMapOrNullResponse(Response response)
            throws GlideException {
        return handleValkeyResponse(Map.class, EnumSet.of(ResponseFlags.IS_NULLABLE), response);
    }

    /**
     * @param response A Protobuf response
     * @return A map of <code>String</code> to <code>String[]</code>
     */
    @SuppressWarnings("unchecked") // raw Map cast to Map<String, Object[]>
    protected Map<String, String[]> handleMapOfArraysResponse(Response response)
            throws GlideException {
        Map<String, Object[]> mapResponse =
                handleValkeyResponse(Map.class, EnumSet.of(ResponseFlags.ENCODING_UTF8), response);
        return castMapOfArrays(mapResponse, String.class);
    }

    /**
     * @param response A Protobuf response
     * @return A map of <code>String</code> to <code>GlideString[]</code>
     */
    @SuppressWarnings("unchecked") // raw Map cast to Map<String, Object[]>
    protected Map<String, GlideString[]> handleBinaryStringMapOfArraysResponse(Response response)
            throws GlideException {
        Map<String, Object[]> mapResponse =
                handleValkeyResponse(Map.class, EnumSet.noneOf(ResponseFlags.class), response);
        return castMapOfArrays(mapResponse, GlideString.class);
    }

    /**
     * @param response A Protobuf response
     * @return A map of a map of <code>String[][]</code>
     */
    protected Map<String, Map<String, String[][]>> handleXReadResponse(Response response)
            throws GlideException {
        Map<String, Object> mapResponse = handleMapOrNullResponse(response);
        if (mapResponse == null) {
            return null;
        }
        return mapResponse.entrySet().stream()
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                e -> castMapOf2DArray((Map<String, Object[][]>) e.getValue(), String.class)));
    }

    /**
     * @param response A Protobuf response
     * @return A map of a map of <code>GlideString[][]</code>
     */
    protected Map<GlideString, Map<GlideString, GlideString[][]>> handleXReadResponseBinary(
            Response response) throws GlideException {
        Map<GlideString, Object> mapResponse = handleBinaryStringMapOrNullResponse(response);
        if (mapResponse == null) {
            return null;
        }
        return mapResponse.entrySet().stream()
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                e ->
                                        castMapOf2DArray(
                                                (Map<GlideString, Object[][]>) e.getValue(), GlideString.class)));
    }

    @SuppressWarnings("unchecked") // raw Set cast to Set<String>
    protected Set<String> handleSetResponse(Response response) throws GlideException {
        return handleValkeyResponse(Set.class, EnumSet.of(ResponseFlags.ENCODING_UTF8), response);
    }

    @SuppressWarnings("unchecked")
    protected Set<GlideString> handleSetBinaryResponse(Response response) throws GlideException {
        return handleValkeyResponse(Set.class, EnumSet.noneOf(ResponseFlags.class), response);
    }

    /** Process a <code>FUNCTION LIST</code> standalone response. */
    @SuppressWarnings("unchecked")
    protected Map<String, Object>[] handleFunctionListResponse(Object[] response) {
        Map<String, Object>[] data = castArray(response, Map.class);
        for (Map<String, Object> libraryInfo : data) {
            Object[] functions = (Object[]) libraryInfo.get("functions");
            Map<String, Object>[] functionInfo = castArray(functions, Map.class);
            libraryInfo.put("functions", functionInfo);
        }
        return data;
    }

    /** Process a <code>FUNCTION LIST</code> standalone response. */
    @SuppressWarnings("unchecked")
    protected Map<GlideString, Object>[] handleFunctionListResponseBinary(Object[] response) {
        Map<GlideString, Object>[] data = castArray(response, Map.class);
        for (Map<GlideString, Object> libraryInfo : data) {
            Object[] functions = (Object[]) libraryInfo.get(gs("functions"));
            Map<String, Object>[] functionInfo = castArray(functions, Map.class);
            libraryInfo.put(gs("functions"), functionInfo);
        }
        return data;
    }

    /** Process a <code>FUNCTION STATS</code> response from one node. */
    protected Map<String, Map<String, Object>> handleFunctionStatsResponse(
            Map<String, Map<String, Object>> response) {
        Map<String, Object> runningScriptInfo = response.get("running_script");
        if (runningScriptInfo != null) {
            Object[] command = (Object[]) runningScriptInfo.get("command");
            runningScriptInfo.put("command", castArray(command, String.class));
        }
        return response;
    }

    /** Process a <code>FUNCTION STATS</code> response from one node. */
    protected Map<GlideString, Map<GlideString, Object>> handleFunctionStatsBinaryResponse(
            Map<GlideString, Map<GlideString, Object>> response) {
        Map<GlideString, Object> runningScriptInfo = response.get(gs("running_script"));
        if (runningScriptInfo != null) {
            Object[] command = (Object[]) runningScriptInfo.get(gs("command"));
            runningScriptInfo.put(gs("command"), castArray(command, GlideString.class));
        }
        return response;
    }

    /** Process a <code>FUNCTION STATS</code> cluster response. */
    protected ClusterValue<Map<String, Map<String, Object>>> handleFunctionStatsResponse(
            Response response, boolean isSingleValue) {
        if (isSingleValue) {
            return ClusterValue.ofSingleValue(handleFunctionStatsResponse(handleMapResponse(response)));
        } else {
            Map<String, Map<String, Map<String, Object>>> data = handleMapResponse(response);
            for (Map.Entry<String, Map<String, Map<String, Object>>> nodeInfo : data.entrySet()) {
                nodeInfo.setValue(handleFunctionStatsResponse(nodeInfo.getValue()));
            }
            return ClusterValue.ofMultiValue(data);
        }
    }

    /** Process a <code>FUNCTION STATS</code> cluster response. */
    protected ClusterValue<Map<GlideString, Map<GlideString, Object>>>
            handleFunctionStatsBinaryResponse(Response response, boolean isSingleValue) {
        if (isSingleValue) {
            return ClusterValue.ofSingleValue(
                    handleFunctionStatsBinaryResponse(handleBinaryStringMapResponse(response)));
        } else {
            Map<GlideString, Map<GlideString, Map<GlideString, Object>>> data =
                    handleBinaryStringMapResponse(response);
            for (Map.Entry<GlideString, Map<GlideString, Map<GlideString, Object>>> nodeInfo :
                    data.entrySet()) {
                nodeInfo.setValue(handleFunctionStatsBinaryResponse(nodeInfo.getValue()));
            }
            return ClusterValue.ofMultiValueBinary(data);
        }
    }

    /** Process a <code>LCS key1 key2 IDX</code> response */
    protected Map<String, Object> handleLcsIdxResponse(Map<String, Object> response)
            throws GlideException {
        Long[][][] convertedMatchesObject =
                cast3DArray((Object[]) (response.get(LCS_MATCHES_RESULT_KEY)), Long.class);

        if (convertedMatchesObject == null) {
            throw new NullPointerException(
                    "LCS result does not contain the key \"" + LCS_MATCHES_RESULT_KEY + "\"");
        }

        response.put("matches", convertedMatchesObject);
        return response;
    }

    private Map<String, Object> convertBinaryLcsMap(Map<GlideString, Object> response) {
        if (response == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> converted = new LinkedHashMap<>(Math.max(response.size(), 1));
        for (Map.Entry<GlideString, Object> entry : response.entrySet()) {
            GlideString key = entry.getKey();
            if (key == null) {
                throw new IllegalStateException(
                        "Received null key from native layer - this indicates a bug in the protocol handling");
            }
            converted.put(key.toString(), entry.getValue());
        }
        return converted;
    }

    /**
     * Update the current connection with a new password.
     *
     * <p>This method is useful in scenarios where the server password has changed or when utilizing
     * short-lived passwords for enhanced security. It allows the client to update its password to
     * reconnect upon disconnection without the need to recreate the client instance. This ensures
     * that the internal reconnection mechanism can handle reconnection seamlessly, preventing the
     * loss of in-flight commands.
     *
     * @param immediateAuth A <code>boolean</code> flag. If <code>true</code>, the client will
     *     authenticate immediately with the new password against all connections, Using <code>AUTH
     *     </code> command. <br>
     *     If password supplied is an empty string, the client will not perform auth and a warning
     *     will be returned. <br>
     *     The default is `false`.
     * @apiNote This method updates the client's internal password configuration and does not perform
     *     password rotation on the server side.
     * @param password A new password to set.
     * @return <code>"OK"</code>.
     * @throws ConfigurationError if the client is using IAM authentication.
     * @example
     *     <pre>{@code
     * String response = client.resetConnectionPassword("new_password", RE_AUTHENTICATE).get();
     * assert response.equals("OK");
     * }</pre>
     */
    public CompletableFuture<String> updateConnectionPassword(
            @NonNull String password, boolean immediateAuth) {
        // Check if using IAM authentication
        ServerCredentials creds = connectionManager.getCredentials();
        if (creds != null && creds.getIamConfig() != null) {
            throw new ConfigurationError(
                    "updateConnectionPassword is not supported when IAM authentication is enabled.");
        }

        return commandManager
                .submitPasswordUpdate(Optional.of(password), immediateAuth, this::handleStringResponse)
                .thenApply(
                        result -> {
                            if (OK.equals(result)) {
                                connectionManager.updateStoredPassword(password);
                            }
                            return result;
                        });
    }

    /**
     * Update the current connection by removing the password.
     *
     * <p>This method is useful in scenarios where the server password has changed or when utilizing
     * short-lived passwords for enhanced security. It allows the client to update its password to
     * reconnect upon disconnection without the need to recreate the client instance. This ensures
     * that the internal reconnection mechanism can handle reconnection seamlessly, preventing the
     * loss of in-flight commands.
     *
     * @apiNote This method updates the client's internal password configuration and does not perform
     *     password rotation on the server side.
     * @param immediateAuth A <code>boolean</code> flag. If <code>true</code>, the client will
     *     authenticate immediately with the new password against all connections, Using <code>AUTH
     *     </code> command. <br>
     *     If password supplied is an empty string, the client will not perform auth and a warning
     *     will be returned. <br>
     *     The default is `false`.
     * @return <code>"OK"</code>.
     * @throws ConfigurationError if the client is using IAM authentication.
     * @example
     *     <pre>{@code
     * String response = client.resetConnectionPassword(true).get();
     * assert response.equals("OK");
     * }</pre>
     */
    public CompletableFuture<String> updateConnectionPassword(boolean immediateAuth) {
        // Check if using IAM authentication
        ServerCredentials creds = connectionManager.getCredentials();
        if (creds != null && creds.getIamConfig() != null) {
            throw new ConfigurationError(
                    "updateConnectionPassword is not supported when IAM authentication is enabled.");
        }

        return commandManager
                .submitPasswordUpdate(Optional.empty(), immediateAuth, this::handleStringResponse)
                .thenApply(
                        result -> {
                            if (OK.equals(result)) {
                                connectionManager.updateStoredPassword(null);
                            }
                            return result;
                        });
    }

    /**
     * Manually refresh the IAM token for the current connection.
     *
     * <p>This method is only available if the client was created with IAM authentication. It triggers
     * an immediate refresh of the IAM token and updates the connection.
     *
     * @return A CompletableFuture that resolves to <code>"OK"</code> on success.
     * @throws ConfigurationError if the client is not using IAM authentication.
     * @example
     *     <pre>{@code
     * // Create client with IAM authentication
     * GlideClientConfiguration config = GlideClientConfiguration.builder()
     *     .address(NodeAddress.builder().host("my-cluster.amazonaws.com").port(6379).build())
     *     .credentials(ServerCredentials.builder()
     *         .username("myUser")
     *         .iamConfig(IamAuthConfig.builder()
     *             .clusterName("my-cluster")
     *             .service(ServiceType.ELASTICACHE)
     *             .region("us-east-1")
     *             .build())
     *         .build())
     *     .build();
     * GlideClient client = GlideClient.createClient(config).get();
     *
     * // Manually refresh the IAM token
     * String response = client.refreshIamToken().get();
     * assert response.equals("OK");
     * }</pre>
     */
    public CompletableFuture<String> refreshIamToken() {
        // Check if using IAM authentication
        ServerCredentials creds = connectionManager.getCredentials();
        if (creds == null || creds.getIamConfig() == null) {
            throw new ConfigurationError(
                    "refreshIamToken is only available when IAM authentication is enabled.");
        }

        return commandManager.submitRefreshIamToken(this::handleStringResponse);
    }

    /**
     * Get cache metrics.
     *
     * <p>This is the internal method that communicates with the core to retrieve cache metrics. All
     * public cache metric methods delegate to this method.
     *
     * @param metricsType The type of metric to retrieve.
     * @return A CompletableFuture that resolves to the requested cache metric.
     * @throws RequestException if caching is not enabled or metrics are disabled.
     */
    private CompletableFuture<Object> getCacheMetrics(CacheMetricsType metricsType) {
        return commandManager.submitGetCacheMetrics(metricsType, this::handleObjectOrNullResponse);
    }

    /**
     * Get the cache hit rate metric.
     *
     * <p>This method returns the percentage of cache hits out of total cache requests. Only available
     * when client-side caching is enabled with metrics collection.
     *
     * @return A CompletableFuture that resolves to the cache hit rate as a percentage (0.0 to 100.0).
     * @throws RequestException if caching is not enabled or metrics are disabled.
     * @example
     *     <pre>{@code
     * // Get cache hit rate
     * Double hitRate = client.getCacheHitRate().get();
     * System.out.println("Cache hit rate: " + hitRate + "%");
     * }</pre>
     */
    public CompletableFuture<Double> getCacheHitRate() {
        return getCacheMetrics(CacheMetricsType.HitRate).thenApply(result -> (Double) result);
    }

    /**
     * Get the cache miss rate metric.
     *
     * <p>This method returns the percentage of cache misses out of total cache requests. Only
     * available when client-side caching is enabled with metrics collection.
     *
     * @return A CompletableFuture that resolves to the cache miss rate as a percentage (0.0 to
     *     100.0).
     * @throws RequestException if caching is not enabled or metrics are disabled.
     * @example
     *     <pre>{@code
     * // Get cache miss rate
     * Double missRate = client.getCacheMissRate().get();
     * System.out.println("Cache miss rate: " + missRate + "%");
     * }</pre>
     */
    public CompletableFuture<Double> getCacheMissRate() {
        return getCacheMetrics(CacheMetricsType.MissRate).thenApply(result -> (Double) result);
    }

    /**
     * Get the current number of entries in the cache.
     *
     * <p>This method returns the total count of cached entries currently stored in the client-side
     * cache. Available when client-side caching is enabled.
     *
     * @return A CompletableFuture that resolves to the number of cache entries.
     * @throws RequestException if caching is not enabled.
     * @example
     *     <pre>{@code
     * // Get cache entry count
     * Long entryCount = client.getCacheEntryCount().get();
     * System.out.println("Cache entries: " + entryCount);
     * }</pre>
     */
    public CompletableFuture<Long> getCacheEntryCount() {
        return getCacheMetrics(CacheMetricsType.EntryCount).thenApply(result -> (Long) result);
    }

    /**
     * Get the total number of cache evictions.
     *
     * <p>This method returns the count of entries that have been evicted from the cache due to memory
     * limits or eviction policies. Only available when client-side caching is enabled with metrics
     * collection.
     *
     * @return A CompletableFuture that resolves to the total number of evictions.
     * @throws RequestException if caching is not enabled or metrics are disabled.
     * @example
     *     <pre>{@code
     * // Get cache evictions count
     * Long evictions = client.getCacheEvictions().get();
     * System.out.println("Cache evictions: " + evictions);
     * }</pre>
     */
    public CompletableFuture<Long> getCacheEvictions() {
        return getCacheMetrics(CacheMetricsType.Evictions).thenApply(result -> (Long) result);
    }

    /**
     * Get the total number of cache expirations.
     *
     * <p>This method returns the count of entries that have expired from the cache due to TTL
     * settings. Only available when client-side caching is enabled with metrics collection.
     *
     * @return A CompletableFuture that resolves to the total number of expirations.
     * @throws RequestException if caching is not enabled or metrics are disabled.
     * @example
     *     <pre>{@code
     * // Get cache expirations count
     * Long expirations = client.getCacheExpirations().get();
     * System.out.println("Cache expirations: " + expirations);
     * }</pre>
     */
    public CompletableFuture<Long> getCacheExpirations() {
        return getCacheMetrics(CacheMetricsType.Expirations).thenApply(result -> (Long) result);
    }

    /**
     * Get the total number of cache lookups (hits + misses).
     *
     * <p>This method returns the sum of cache hits and misses, representing the total number of cache
     * lookup operations performed. Only available when client-side caching is enabled with metrics
     * collection.
     *
     * @return A CompletableFuture that resolves to the total number of cache lookups.
     * @throws RequestException if caching is not enabled or metrics are disabled.
     * @example
     *     <pre>{@code
     * // Get total cache lookups
     * Long totalLookups = client.getCacheTotalLookups().get();
     * System.out.println("Total cache lookups: " + totalLookups);
     * }</pre>
     */
    public CompletableFuture<Long> getCacheTotalLookups() {
        return getCacheMetrics(CacheMetricsType.TotalLookups).thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> del(@NonNull String[] keys) {
        return commandManager.submitNewCommand(Del, keys, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> del(@NonNull GlideString[] keys) {
        return commandManager.submitNewCommand(Del, keys, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String> get(@NonNull String key) {
        return commandManager.submitNewCommand(
                Get, new String[] {key}, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<GlideString> get(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                Get, new GlideString[] {key}, this::handleGlideStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String> getdel(@NonNull String key) {
        return commandManager.submitNewCommand(
                GetDel, new String[] {key}, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<GlideString> getdel(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                GetDel, new GlideString[] {key}, this::handleGlideStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String> getex(@NonNull String key) {
        return commandManager.submitNewCommand(
                GetEx, new String[] {key}, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<GlideString> getex(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                GetEx, new GlideString[] {key}, this::handleGlideStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String> getex(@NonNull String key, @NonNull GetExOptions options) {
        String[] arguments = ArrayUtils.addFirst(options.toArgs(), key);
        return commandManager.submitNewCommand(GetEx, arguments, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<GlideString> getex(
            @NonNull GlideString key, @NonNull GetExOptions options) {
        GlideString[] arguments = new ArgsBuilder().add(key).add(options.toArgs()).toArray();
        return commandManager.submitNewCommand(GetEx, arguments, this::handleGlideStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String> set(@NonNull String key, @NonNull String value) {
        return commandManager.submitNewCommand(
                Set, new String[] {key, value}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> set(@NonNull GlideString key, @NonNull GlideString value) {
        return commandManager.submitNewCommand(
                Set, new GlideString[] {key, value}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> set(
            @NonNull String key, @NonNull String value, @NonNull SetOptions options) {
        String[] arguments = ArrayUtils.addAll(new String[] {key, value}, options.toArgs());
        return commandManager.submitNewCommand(Set, arguments, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String> set(
            @NonNull GlideString key, @NonNull GlideString value, @NonNull SetOptions options) {
        GlideString[] arguments =
                new ArgsBuilder().add(key).add(value).add(options.toArgsBinary()).toArray();
        return commandManager.submitNewCommand(Set, arguments, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<Long> append(@NonNull String key, @NonNull String value) {
        return commandManager.submitNewCommand(
                Append, new String[] {key, value}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> append(@NonNull GlideString key, @NonNull GlideString value) {
        return commandManager.submitNewCommand(
                Append, new GlideString[] {key, value}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String[]> mget(@NonNull String[] keys) {
        return commandManager.submitNewCommand(
                MGet, keys, response -> castArray(handleArrayOrNullResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> mget(@NonNull GlideString[] keys) {
        return commandManager.submitNewCommand(
                MGet,
                keys,
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<String> mset(@NonNull Map<String, String> keyValueMap) {
        String[] args = convertMapToKeyValueStringArray(keyValueMap);
        return commandManager.submitNewCommand(MSet, args, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> msetBinary(@NonNull Map<GlideString, GlideString> keyValueMap) {
        GlideString[] args = convertMapToKeyValueGlideStringArray(keyValueMap);
        return commandManager.submitNewCommand(MSet, args, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Boolean> move(@NonNull String key, long dbIndex) {
        return commandManager.submitNewCommand(
                Move, new String[] {key, Long.toString(dbIndex)}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> move(@NonNull GlideString key, long dbIndex) {
        return commandManager.submitNewCommand(
                Move, new GlideString[] {key, gs(Long.toString(dbIndex))}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<String> objectEncoding(@NonNull String key) {
        return commandManager.submitNewCommand(
                ObjectEncoding, new String[] {key}, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String> objectEncoding(@NonNull GlideString key) {
        // Prefer specialized path (textual response) without adding hot-path branching.
        // In unit tests where CommandManager is a mock, this may return null; fallback
        // to
        // the generic mocked method to satisfy the unit contract.
        CompletableFuture<String> specialized =
                commandManager.submitObjectEncoding(
                        new GlideString[] {key}, this::handleStringOrNullResponse);
        if (specialized != null) {
            return specialized;
        }
        return commandManager.submitNewCommand(
                ObjectEncoding, new GlideString[] {key}, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<Long> objectFreq(@NonNull String key) {
        return commandManager.submitNewCommand(
                ObjectFreq, new String[] {key}, this::handleLongOrNullResponse);
    }

    @Override
    public CompletableFuture<Long> objectFreq(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                ObjectFreq, new GlideString[] {key}, this::handleLongOrNullResponse);
    }

    @Override
    public CompletableFuture<Long> objectIdletime(@NonNull String key) {
        return commandManager.submitNewCommand(
                ObjectIdleTime, new String[] {key}, this::handleLongOrNullResponse);
    }

    @Override
    public CompletableFuture<Long> objectIdletime(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                ObjectIdleTime, new GlideString[] {key}, this::handleLongOrNullResponse);
    }

    @Override
    public CompletableFuture<Long> objectRefcount(@NonNull String key) {
        return commandManager.submitNewCommand(
                ObjectRefCount, new String[] {key}, this::handleLongOrNullResponse);
    }

    @Override
    public CompletableFuture<Long> objectRefcount(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                ObjectRefCount, new GlideString[] {key}, this::handleLongOrNullResponse);
    }

    @Override
    public CompletableFuture<String> rename(@NonNull String key, @NonNull String newKey) {
        return commandManager.submitNewCommand(
                Rename, new String[] {key, newKey}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> rename(@NonNull GlideString key, @NonNull GlideString newKey) {
        return commandManager.submitNewCommand(
                Rename, new GlideString[] {key, newKey}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Boolean> renamenx(@NonNull String key, @NonNull String newKey) {
        return commandManager.submitNewCommand(
                RenameNX, new String[] {key, newKey}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> renamenx(
            @NonNull GlideString key, @NonNull GlideString newKey) {
        return commandManager.submitNewCommand(
                RenameNX, new GlideString[] {key, newKey}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Long> incr(@NonNull String key) {
        return commandManager.submitNewCommand(Incr, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> incr(@NonNull GlideString key) {
        return commandManager.submitNewCommand(Incr, new GlideString[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> incrBy(@NonNull String key, long amount) {
        return commandManager.submitNewCommand(
                IncrBy, new String[] {key, Long.toString(amount)}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> incrBy(@NonNull GlideString key, long amount) {
        return commandManager.submitNewCommand(
                IncrBy, new GlideString[] {key, gs(Long.toString(amount))}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Double> incrByFloat(@NonNull String key, double amount) {
        return commandManager.submitNewCommand(
                IncrByFloat, new String[] {key, Double.toString(amount)}, this::handleDoubleResponse);
    }

    @Override
    public CompletableFuture<Double> incrByFloat(@NonNull GlideString key, double amount) {
        return commandManager.submitNewCommand(
                IncrByFloat,
                new GlideString[] {key, gs(Double.toString(amount))},
                this::handleDoubleResponse);
    }

    @Override
    public CompletableFuture<Long> decr(@NonNull String key) {
        return commandManager.submitNewCommand(Decr, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> decr(@NonNull GlideString key) {
        return commandManager.submitNewCommand(Decr, new GlideString[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> decrBy(@NonNull String key, long amount) {
        return commandManager.submitNewCommand(
                DecrBy, new String[] {key, Long.toString(amount)}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> decrBy(@NonNull GlideString key, long amount) {
        return commandManager.submitNewCommand(
                DecrBy, new GlideString[] {key, gs(Long.toString(amount))}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> strlen(@NonNull String key) {
        return commandManager.submitNewCommand(Strlen, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> strlen(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                Strlen, new GlideString[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> setrange(@NonNull String key, int offset, @NonNull String value) {
        String[] arguments = new String[] {key, Integer.toString(offset), value};
        return commandManager.submitNewCommand(SetRange, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> setrange(
            @NonNull GlideString key, int offset, @NonNull GlideString value) {
        GlideString[] arguments = new GlideString[] {key, gs(Integer.toString(offset)), value};
        return commandManager.submitNewCommand(SetRange, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String> getrange(@NonNull String key, int start, int end) {
        String[] arguments = new String[] {key, Integer.toString(start), Integer.toString(end)};
        return commandManager.submitNewCommand(GetRange, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<GlideString> getrange(@NonNull GlideString key, int start, int end) {
        GlideString[] arguments =
                new GlideString[] {key, gs(Integer.toString(start)), gs(Integer.toString(end))};
        return commandManager.submitNewCommand(GetRange, arguments, this::handleGlideStringResponse);
    }

    @Override
    public CompletableFuture<String> hget(@NonNull String key, @NonNull String field) {
        return commandManager.submitNewCommand(
                HGet, new String[] {key, field}, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<GlideString> hget(@NonNull GlideString key, @NonNull GlideString field) {
        return commandManager.submitNewCommand(
                HGet, new GlideString[] {key, field}, this::handleGlideStringOrNullResponse);
    }

    @Override
    public CompletableFuture<Long> hset(
            @NonNull String key, @NonNull Map<String, String> fieldValueMap) {
        String[] args = ArrayUtils.addFirst(convertMapToKeyValueStringArray(fieldValueMap), key);
        return commandManager.submitNewCommand(HSet, args, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> hset(
            @NonNull GlideString key, @NonNull Map<GlideString, GlideString> fieldValueMap) {
        GlideString[] args =
                ArrayUtils.addFirst(convertMapToKeyValueGlideStringArray(fieldValueMap), key);
        return commandManager.submitNewCommand(HSet, args, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Boolean> hsetnx(
            @NonNull String key, @NonNull String field, @NonNull String value) {
        return commandManager.submitNewCommand(
                HSetNX, new String[] {key, field, value}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> hsetnx(
            @NonNull GlideString key, @NonNull GlideString field, @NonNull GlideString value) {
        return commandManager.submitNewCommand(
                HSetNX, new GlideString[] {key, field, value}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Long> hsetex(
            @NonNull String key, @NonNull Map<String, String> fieldValueMap, HSetExOptions options) {
        String[] optionsArgs = options != null ? options.toArgs() : EMPTY_STRING_ARRAY;
        String[] arguments =
                concatenateArrays(
                        new String[] {key},
                        optionsArgs,
                        new String[] {"FIELDS", String.valueOf(fieldValueMap.size())},
                        convertMapToKeyValueStringArray(fieldValueMap));
        return commandManager.submitNewCommand(HSetEx, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> hsetex(
            @NonNull GlideString key,
            @NonNull Map<GlideString, GlideString> fieldValueMap,
            HSetExOptions options) {
        ArgsBuilder argsBuilder = new ArgsBuilder().add(key);
        if (options != null) {
            argsBuilder.add(options.toArgs());
        }
        GlideString[] arguments =
                argsBuilder
                        .add("FIELDS")
                        .add(fieldValueMap.size())
                        .add(convertMapToKeyValueGlideStringArray(fieldValueMap))
                        .toArray();
        return commandManager.submitNewCommand(HSetEx, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String[]> hgetex(
            @NonNull String key, @NonNull String[] fields, HGetExOptions options) {
        String[] optionsArgs = options != null ? options.toArgs() : EMPTY_STRING_ARRAY;
        String[] arguments =
                concatenateArrays(
                        new String[] {key},
                        optionsArgs,
                        new String[] {"FIELDS", String.valueOf(fields.length)},
                        fields);
        return commandManager.submitNewCommand(
                HGetEx, arguments, response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> hgetex(
            @NonNull GlideString key, @NonNull GlideString[] fields, HGetExOptions options) {
        ArgsBuilder argsBuilder = new ArgsBuilder().add(key);
        if (options != null) {
            argsBuilder.add(options.toArgs());
        }
        GlideString[] arguments = argsBuilder.add("FIELDS").add(fields.length).add(fields).toArray();
        return commandManager.submitNewCommand(
                HGetEx,
                arguments,
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Long[]> hexpire(
            @NonNull String key,
            long seconds,
            @NonNull String[] fields,
            HashFieldExpirationConditionOptions options) {
        String[] optionsArgs = options != null ? options.toArgs() : EMPTY_STRING_ARRAY;
        String[] arguments =
                concatenateArrays(
                        new String[] {key, String.valueOf(seconds)},
                        optionsArgs,
                        new String[] {"FIELDS", String.valueOf(fields.length)},
                        fields);
        return commandManager.submitNewCommand(
                HExpire, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> hexpire(
            @NonNull GlideString key,
            long seconds,
            @NonNull GlideString[] fields,
            HashFieldExpirationConditionOptions options) {
        ArgsBuilder argsBuilder = new ArgsBuilder().add(key).add(seconds);
        if (options != null) {
            argsBuilder.add(options.toArgs());
        }
        GlideString[] arguments = argsBuilder.add("FIELDS").add(fields.length).add(fields).toArray();
        return commandManager.submitNewCommand(
                HExpire, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> hpersist(@NonNull String key, @NonNull String[] fields) {
        String[] arguments =
                concatenateArrays(
                        new String[] {key}, new String[] {"FIELDS", String.valueOf(fields.length)}, fields);
        return commandManager.submitNewCommand(
                HPersist, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> hpersist(
            @NonNull GlideString key, @NonNull GlideString[] fields) {
        GlideString[] arguments =
                new ArgsBuilder().add(key).add("FIELDS").add(fields.length).add(fields).toArray();
        return commandManager.submitNewCommand(
                HPersist, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> hpexpire(
            @NonNull String key,
            long milliseconds,
            @NonNull String[] fields,
            HashFieldExpirationConditionOptions options) {
        String[] optionsArgs = options != null ? options.toArgs() : EMPTY_STRING_ARRAY;
        String[] arguments =
                concatenateArrays(
                        new String[] {key, String.valueOf(milliseconds)},
                        optionsArgs,
                        new String[] {"FIELDS", String.valueOf(fields.length)},
                        fields);
        return commandManager.submitNewCommand(
                HPExpire, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> hpexpire(
            @NonNull GlideString key,
            long milliseconds,
            @NonNull GlideString[] fields,
            HashFieldExpirationConditionOptions options) {
        ArgsBuilder argsBuilder = new ArgsBuilder().add(key).add(milliseconds);
        if (options != null) {
            argsBuilder.add(options.toArgs());
        }
        GlideString[] arguments = argsBuilder.add("FIELDS").add(fields.length).add(fields).toArray();
        return commandManager.submitNewCommand(
                HPExpire, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> hexpireat(
            @NonNull String key,
            long unixSeconds,
            @NonNull String[] fields,
            HashFieldExpirationConditionOptions options) {
        String[] optionsArgs = options != null ? options.toArgs() : EMPTY_STRING_ARRAY;
        String[] arguments =
                concatenateArrays(
                        new String[] {key, String.valueOf(unixSeconds)},
                        optionsArgs,
                        new String[] {"FIELDS", String.valueOf(fields.length)},
                        fields);
        return commandManager.submitNewCommand(
                HExpireAt, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> hexpireat(
            @NonNull GlideString key,
            long unixSeconds,
            @NonNull GlideString[] fields,
            HashFieldExpirationConditionOptions options) {
        ArgsBuilder argsBuilder = new ArgsBuilder().add(key).add(unixSeconds);
        if (options != null) {
            argsBuilder.add(options.toArgs());
        }
        GlideString[] arguments = argsBuilder.add("FIELDS").add(fields.length).add(fields).toArray();
        return commandManager.submitNewCommand(
                HExpireAt, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> hpexpireat(
            @NonNull String key,
            long unixMilliseconds,
            @NonNull String[] fields,
            HashFieldExpirationConditionOptions options) {
        String[] optionsArgs = options != null ? options.toArgs() : EMPTY_STRING_ARRAY;
        String[] arguments =
                concatenateArrays(
                        new String[] {key, String.valueOf(unixMilliseconds)},
                        optionsArgs,
                        new String[] {"FIELDS", String.valueOf(fields.length)},
                        fields);
        return commandManager.submitNewCommand(
                HPExpireAt, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> hpexpireat(
            @NonNull GlideString key,
            long unixMilliseconds,
            @NonNull GlideString[] fields,
            HashFieldExpirationConditionOptions options) {
        ArgsBuilder argsBuilder = new ArgsBuilder().add(key).add(unixMilliseconds);
        if (options != null) {
            argsBuilder.add(options.toArgs());
        }
        GlideString[] arguments = argsBuilder.add("FIELDS").add(fields.length).add(fields).toArray();
        return commandManager.submitNewCommand(
                HPExpireAt, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> httl(@NonNull String key, @NonNull String[] fields) {
        String[] arguments =
                concatenateArrays(
                        new String[] {key}, new String[] {"FIELDS", String.valueOf(fields.length)}, fields);
        return commandManager.submitNewCommand(
                HTtl, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> httl(@NonNull GlideString key, @NonNull GlideString[] fields) {
        GlideString[] arguments =
                new ArgsBuilder().add(key).add("FIELDS").add(fields.length).add(fields).toArray();
        return commandManager.submitNewCommand(
                HTtl, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> hpttl(@NonNull String key, @NonNull String[] fields) {
        String[] arguments =
                concatenateArrays(
                        new String[] {key}, new String[] {"FIELDS", String.valueOf(fields.length)}, fields);
        return commandManager.submitNewCommand(
                HPTtl, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> hpttl(@NonNull GlideString key, @NonNull GlideString[] fields) {
        GlideString[] arguments =
                new ArgsBuilder().add(key).add("FIELDS").add(fields.length).add(fields).toArray();
        return commandManager.submitNewCommand(
                HPTtl, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> hexpiretime(@NonNull String key, @NonNull String[] fields) {
        String[] arguments =
                concatenateArrays(
                        new String[] {key}, new String[] {"FIELDS", String.valueOf(fields.length)}, fields);
        return commandManager.submitNewCommand(
                HExpireTime, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> hexpiretime(
            @NonNull GlideString key, @NonNull GlideString[] fields) {
        GlideString[] arguments =
                new ArgsBuilder().add(key).add("FIELDS").add(fields.length).add(fields).toArray();
        return commandManager.submitNewCommand(
                HExpireTime, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> hpexpiretime(@NonNull String key, @NonNull String[] fields) {
        String[] arguments =
                concatenateArrays(
                        new String[] {key}, new String[] {"FIELDS", String.valueOf(fields.length)}, fields);
        return commandManager.submitNewCommand(
                HPExpireTime, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> hpexpiretime(
            @NonNull GlideString key, @NonNull GlideString[] fields) {
        GlideString[] arguments =
                new ArgsBuilder().add(key).add("FIELDS").add(fields.length).add(fields).toArray();
        return commandManager.submitNewCommand(
                HPExpireTime, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long> hdel(@NonNull String key, @NonNull String[] fields) {
        String[] args = ArrayUtils.addFirst(fields, key);
        return commandManager.submitNewCommand(HDel, args, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> hdel(@NonNull GlideString key, @NonNull GlideString[] fields) {
        GlideString[] args = ArrayUtils.addFirst(fields, key);
        return commandManager.submitNewCommand(HDel, args, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> hlen(@NonNull String key) {
        return commandManager.submitNewCommand(HLen, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> hlen(@NonNull GlideString key) {
        return commandManager.submitNewCommand(HLen, new GlideString[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String[]> hvals(@NonNull String key) {
        return commandManager.submitNewCommand(
                HVals,
                new String[] {key},
                response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> hvals(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                HVals,
                new GlideString[] {key},
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<String[]> hmget(@NonNull String key, @NonNull String[] fields) {
        String[] arguments = ArrayUtils.addFirst(fields, key);
        return commandManager.submitNewCommand(
                HMGet, arguments, response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> hmget(
            @NonNull GlideString key, @NonNull GlideString[] fields) {
        GlideString[] arguments = ArrayUtils.addFirst(fields, key);
        return commandManager.submitNewCommand(
                HMGet,
                arguments,
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Boolean> hexists(@NonNull String key, @NonNull String field) {
        return commandManager.submitNewCommand(
                HExists, new String[] {key, field}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> hexists(@NonNull GlideString key, @NonNull GlideString field) {
        return commandManager.submitNewCommand(
                HExists, new GlideString[] {key, field}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Map<String, String>> hgetall(@NonNull String key) {
        return commandManager.submitNewCommand(HGetAll, new String[] {key}, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<GlideString, GlideString>> hgetall(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                HGetAll, new GlideString[] {key}, this::handleBinaryStringMapResponse);
    }

    @Override
    public CompletableFuture<Long> hincrBy(@NonNull String key, @NonNull String field, long amount) {
        return commandManager.submitNewCommand(
                HIncrBy, new String[] {key, field, Long.toString(amount)}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> hincrBy(
            @NonNull GlideString key, @NonNull GlideString field, long amount) {
        return commandManager.submitNewCommand(
                HIncrBy,
                new GlideString[] {key, field, gs(Long.toString(amount))},
                this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Double> hincrByFloat(
            @NonNull String key, @NonNull String field, double amount) {
        return commandManager.submitNewCommand(
                HIncrByFloat,
                new String[] {key, field, Double.toString(amount)},
                this::handleDoubleResponse);
    }

    @Override
    public CompletableFuture<Double> hincrByFloat(
            @NonNull GlideString key, @NonNull GlideString field, double amount) {
        return commandManager.submitNewCommand(
                HIncrByFloat,
                new GlideString[] {key, field, gs(Double.toString(amount))},
                this::handleDoubleResponse);
    }

    @Override
    public CompletableFuture<String[]> hkeys(@NonNull String key) {
        return commandManager.submitNewCommand(
                HKeys,
                new String[] {key},
                response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> hkeys(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                HKeys,
                new GlideString[] {key},
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Long> hstrlen(@NonNull String key, @NonNull String field) {
        return commandManager.submitNewCommand(
                HStrlen, new String[] {key, field}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> hstrlen(@NonNull GlideString key, @NonNull GlideString field) {
        return commandManager.submitNewCommand(
                HStrlen, new GlideString[] {key, field}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String> hrandfield(@NonNull String key) {
        return commandManager.submitNewCommand(
                HRandField, new String[] {key}, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<GlideString> hrandfield(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                HRandField, new GlideString[] {key}, this::handleGlideStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String[]> hrandfieldWithCount(@NonNull String key, long count) {
        return commandManager.submitNewCommand(
                HRandField,
                new String[] {key, Long.toString(count)},
                response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> hrandfieldWithCount(
            @NonNull GlideString key, long count) {
        return commandManager.submitNewCommand(
                HRandField,
                new GlideString[] {key, GlideString.of(count)},
                response -> castArray(handleArrayResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<String[][]> hrandfieldWithCountWithValues(
            @NonNull String key, long count) {
        return commandManager.submitNewCommand(
                HRandField,
                new String[] {key, Long.toString(count), WITH_VALUES_VALKEY_API},
                response -> castArrayofArrays(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[][]> hrandfieldWithCountWithValues(
            @NonNull GlideString key, long count) {
        return commandManager.submitNewCommand(
                HRandField,
                new GlideString[] {key, GlideString.of(count), GlideString.of(WITH_VALUES_VALKEY_API)},
                response -> castArrayofArrays(handleArrayResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Long> lpush(@NonNull String key, @NonNull String[] elements) {
        String[] arguments = ArrayUtils.addFirst(elements, key);
        return commandManager.submitNewCommand(LPush, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> lpush(@NonNull GlideString key, @NonNull GlideString[] elements) {
        GlideString[] arguments = ArrayUtils.addFirst(elements, key);
        return commandManager.submitNewCommand(LPush, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String> lpop(@NonNull String key) {
        return commandManager.submitNewCommand(
                LPop, new String[] {key}, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<GlideString> lpop(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                LPop, new GlideString[] {key}, this::handleGlideStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String[]> lpopCount(@NonNull String key, long count) {
        return commandManager.submitNewCommand(
                LPop,
                new String[] {key, Long.toString(count)},
                response -> castArray(handleArrayOrNullResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> lpopCount(@NonNull GlideString key, long count) {
        return commandManager.submitNewCommand(
                LPop,
                new GlideString[] {key, gs(Long.toString(count))},
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Long> lpos(@NonNull String key, @NonNull String element) {
        return commandManager.submitNewCommand(
                LPos, new String[] {key, element}, this::handleLongOrNullResponse);
    }

    @Override
    public CompletableFuture<Long> lpos(@NonNull GlideString key, @NonNull GlideString element) {
        return commandManager.submitNewCommand(
                LPos, new GlideString[] {key, element}, this::handleLongOrNullResponse);
    }

    @Override
    public CompletableFuture<Long> lpos(
            @NonNull String key, @NonNull String element, @NonNull LPosOptions options) {
        String[] arguments = concatenateArrays(new String[] {key, element}, options.toArgs());
        return commandManager.submitNewCommand(LPos, arguments, this::handleLongOrNullResponse);
    }

    @Override
    public CompletableFuture<Long> lpos(
            @NonNull GlideString key, @NonNull GlideString element, @NonNull LPosOptions options) {
        GlideString[] arguments =
                new ArgsBuilder().add(key).add(element).add(options.toArgs()).toArray();
        return commandManager.submitNewCommand(LPos, arguments, this::handleLongOrNullResponse);
    }

    @Override
    public CompletableFuture<Long[]> lposCount(
            @NonNull String key, @NonNull String element, long count) {
        return commandManager.submitNewCommand(
                LPos,
                new String[] {key, element, COUNT_VALKEY_API, Long.toString(count)},
                response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> lposCount(
            @NonNull GlideString key, @NonNull GlideString element, long count) {
        return commandManager.submitNewCommand(
                LPos,
                new GlideString[] {key, element, gs(COUNT_VALKEY_API), gs(Long.toString(count))},
                response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> lposCount(
            @NonNull String key, @NonNull String element, long count, @NonNull LPosOptions options) {
        String[] arguments =
                concatenateArrays(
                        new String[] {key, element, COUNT_VALKEY_API, Long.toString(count)}, options.toArgs());

        return commandManager.submitNewCommand(
                LPos, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> lposCount(
            @NonNull GlideString key,
            @NonNull GlideString element,
            long count,
            @NonNull LPosOptions options) {
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(key)
                        .add(element)
                        .add(COUNT_VALKEY_API)
                        .add(count)
                        .add(options.toArgs())
                        .toArray();

        return commandManager.submitNewCommand(
                LPos, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<String[]> lrange(@NonNull String key, long start, long end) {
        return commandManager.submitNewCommand(
                LRange,
                new String[] {key, Long.toString(start), Long.toString(end)},
                response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> lrange(@NonNull GlideString key, long start, long end) {
        return commandManager.submitNewCommand(
                LRange,
                new GlideString[] {key, gs(Long.toString(start)), gs(Long.toString(end))},
                response -> castArray(handleArrayResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<String> lindex(@NonNull String key, long index) {
        return commandManager.submitNewCommand(
                LIndex, new String[] {key, Long.toString(index)}, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<GlideString> lindex(@NonNull GlideString key, long index) {
        return commandManager.submitNewCommand(
                LIndex,
                new GlideString[] {key, gs(Long.toString(index))},
                this::handleGlideStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String> ltrim(@NonNull String key, long start, long end) {
        return commandManager.submitNewCommand(
                LTrim,
                new String[] {key, Long.toString(start), Long.toString(end)},
                this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> ltrim(@NonNull GlideString key, long start, long end) {
        return commandManager.submitNewCommand(
                LTrim,
                new GlideString[] {key, gs(Long.toString(start)), gs(Long.toString(end))},
                this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Long> llen(@NonNull String key) {
        return commandManager.submitNewCommand(LLen, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> llen(@NonNull GlideString key) {
        return commandManager.submitNewCommand(LLen, new GlideString[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> lrem(@NonNull String key, long count, @NonNull String element) {
        return commandManager.submitNewCommand(
                LRem, new String[] {key, Long.toString(count), element}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> lrem(
            @NonNull GlideString key, long count, @NonNull GlideString element) {
        return commandManager.submitNewCommand(
                LRem, new GlideString[] {key, gs(Long.toString(count)), element}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> rpush(@NonNull String key, @NonNull String[] elements) {
        String[] arguments = ArrayUtils.addFirst(elements, key);
        return commandManager.submitNewCommand(RPush, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> rpush(@NonNull GlideString key, @NonNull GlideString[] elements) {
        GlideString[] arguments = ArrayUtils.addFirst(elements, key);
        return commandManager.submitNewCommand(RPush, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String> rpop(@NonNull String key) {
        return commandManager.submitNewCommand(
                RPop, new String[] {key}, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<GlideString> rpop(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                RPop, new GlideString[] {key}, this::handleGlideStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String[]> rpopCount(@NonNull String key, long count) {
        return commandManager.submitNewCommand(
                RPop,
                new String[] {key, Long.toString(count)},
                response -> castArray(handleArrayOrNullResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> rpopCount(@NonNull GlideString key, long count) {
        return commandManager.submitNewCommand(
                RPop,
                new GlideString[] {key, gs(Long.toString(count))},
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Long> sadd(@NonNull String key, @NonNull String[] members) {
        String[] arguments = ArrayUtils.addFirst(members, key);
        return commandManager.submitNewCommand(SAdd, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> sadd(@NonNull GlideString key, @NonNull GlideString[] members) {
        GlideString[] arguments = ArrayUtils.addFirst(members, key);
        return commandManager.submitNewCommand(SAdd, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Boolean> sismember(@NonNull String key, @NonNull String member) {
        return commandManager.submitNewCommand(
                SIsMember, new String[] {key, member}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> sismember(
            @NonNull GlideString key, @NonNull GlideString member) {
        return commandManager.submitNewCommand(
                SIsMember, new GlideString[] {key, member}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Long> srem(@NonNull String key, @NonNull String[] members) {
        String[] arguments = ArrayUtils.addFirst(members, key);
        return commandManager.submitNewCommand(SRem, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> srem(@NonNull GlideString key, @NonNull GlideString[] members) {
        GlideString[] arguments = ArrayUtils.addFirst(members, key);
        return commandManager.submitNewCommand(SRem, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Set<String>> smembers(@NonNull String key) {
        return commandManager.submitNewCommand(SMembers, new String[] {key}, this::handleSetResponse);
    }

    @Override
    public CompletableFuture<Set<GlideString>> smembers(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                SMembers, new GlideString[] {key}, this::handleSetBinaryResponse);
    }

    @Override
    public CompletableFuture<Long> scard(@NonNull String key) {
        return commandManager.submitNewCommand(SCard, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> scard(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                SCard, new GlideString[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Set<String>> sdiff(@NonNull String[] keys) {
        return commandManager.submitNewCommand(SDiff, keys, this::handleSetResponse);
    }

    @Override
    public CompletableFuture<Set<GlideString>> sdiff(@NonNull GlideString[] keys) {
        return commandManager.submitNewCommand(SDiff, keys, this::handleSetBinaryResponse);
    }

    @Override
    public CompletableFuture<Boolean[]> smismember(@NonNull String key, @NonNull String[] members) {
        String[] arguments = ArrayUtils.addFirst(members, key);
        return commandManager.submitNewCommand(
                SMIsMember, arguments, response -> castArray(handleArrayResponse(response), Boolean.class));
    }

    @Override
    public CompletableFuture<Boolean[]> smismember(
            @NonNull GlideString key, @NonNull GlideString[] members) {
        GlideString[] arguments = ArrayUtils.addFirst(members, key);
        return commandManager.submitNewCommand(
                SMIsMember, arguments, response -> castArray(handleArrayResponse(response), Boolean.class));
    }

    @Override
    public CompletableFuture<Long> sdiffstore(@NonNull String destination, @NonNull String[] keys) {
        String[] arguments = ArrayUtils.addFirst(keys, destination);
        return commandManager.submitNewCommand(SDiffStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> sdiffstore(
            @NonNull GlideString destination, @NonNull GlideString[] keys) {
        GlideString[] arguments = ArrayUtils.addFirst(keys, destination);
        return commandManager.submitNewCommand(SDiffStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Boolean> smove(
            @NonNull String source, @NonNull String destination, @NonNull String member) {
        return commandManager.submitNewCommand(
                SMove, new String[] {source, destination, member}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> smove(
            @NonNull GlideString source, @NonNull GlideString destination, @NonNull GlideString member) {
        return commandManager.submitNewCommand(
                SMove, new GlideString[] {source, destination, member}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Long> sinterstore(@NonNull String destination, @NonNull String[] keys) {
        String[] arguments = ArrayUtils.addFirst(keys, destination);
        return commandManager.submitNewCommand(SInterStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> sinterstore(
            @NonNull GlideString destination, @NonNull GlideString[] keys) {
        GlideString[] arguments = ArrayUtils.addFirst(keys, destination);
        return commandManager.submitNewCommand(SInterStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Set<String>> sinter(@NonNull String[] keys) {
        return commandManager.submitNewCommand(SInter, keys, this::handleSetResponse);
    }

    @Override
    public CompletableFuture<Set<GlideString>> sinter(@NonNull GlideString[] keys) {
        return commandManager.submitNewCommand(SInter, keys, this::handleSetBinaryResponse);
    }

    @Override
    public CompletableFuture<Long> sunionstore(@NonNull String destination, @NonNull String[] keys) {
        String[] arguments = ArrayUtils.addFirst(keys, destination);
        return commandManager.submitNewCommand(SUnionStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> sunionstore(
            @NonNull GlideString destination, @NonNull GlideString[] keys) {
        GlideString[] arguments = ArrayUtils.addFirst(keys, destination);
        return commandManager.submitNewCommand(SUnionStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> exists(@NonNull String[] keys) {
        return commandManager.submitNewCommand(Exists, keys, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> exists(@NonNull GlideString[] keys) {
        return commandManager.submitNewCommand(Exists, keys, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> unlink(@NonNull String[] keys) {
        return commandManager.submitNewCommand(Unlink, keys, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> unlink(@NonNull GlideString[] keys) {
        return commandManager.submitNewCommand(Unlink, keys, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Boolean> expire(@NonNull String key, long seconds) {
        return commandManager.submitNewCommand(
                Expire, new String[] {key, Long.toString(seconds)}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> expire(@NonNull GlideString key, long seconds) {
        return commandManager.submitNewCommand(
                Expire, new GlideString[] {key, gs(Long.toString(seconds))}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> expire(
            @NonNull String key, long seconds, @NonNull ExpireOptions expireOptions) {
        String[] arguments =
                ArrayUtils.addAll(new String[] {key, Long.toString(seconds)}, expireOptions.toArgs());
        return commandManager.submitNewCommand(Expire, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> expire(
            @NonNull GlideString key, long seconds, @NonNull ExpireOptions expireOptions) {
        GlideString[] arguments =
                new ArgsBuilder().add(key).add(seconds).add(expireOptions.toArgs()).toArray();

        return commandManager.submitNewCommand(Expire, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> expireAt(@NonNull String key, long unixSeconds) {
        return commandManager.submitNewCommand(
                ExpireAt, new String[] {key, Long.toString(unixSeconds)}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> expireAt(@NonNull GlideString key, long unixSeconds) {
        return commandManager.submitNewCommand(
                ExpireAt,
                new GlideString[] {key, gs(Long.toString(unixSeconds))},
                this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> expireAt(
            @NonNull String key, long unixSeconds, @NonNull ExpireOptions expireOptions) {
        String[] arguments =
                ArrayUtils.addAll(new String[] {key, Long.toString(unixSeconds)}, expireOptions.toArgs());
        return commandManager.submitNewCommand(ExpireAt, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> expireAt(
            @NonNull GlideString key, long unixSeconds, @NonNull ExpireOptions expireOptions) {
        GlideString[] arguments =
                new ArgsBuilder().add(key).add(unixSeconds).add(expireOptions.toArgs()).toArray();
        return commandManager.submitNewCommand(ExpireAt, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> pexpire(@NonNull String key, long milliseconds) {
        return commandManager.submitNewCommand(
                PExpire, new String[] {key, Long.toString(milliseconds)}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> pexpire(@NonNull GlideString key, long milliseconds) {
        return commandManager.submitNewCommand(
                PExpire,
                new GlideString[] {key, gs(Long.toString(milliseconds))},
                this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> pexpire(
            @NonNull String key, long milliseconds, @NonNull ExpireOptions expireOptions) {
        String[] arguments =
                ArrayUtils.addAll(new String[] {key, Long.toString(milliseconds)}, expireOptions.toArgs());
        return commandManager.submitNewCommand(PExpire, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> pexpire(
            @NonNull GlideString key, long milliseconds, @NonNull ExpireOptions expireOptions) {
        GlideString[] arguments =
                new ArgsBuilder().add(key).add(milliseconds).add(expireOptions.toArgs()).toArray();
        return commandManager.submitNewCommand(PExpire, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> pexpireAt(@NonNull String key, long unixMilliseconds) {
        return commandManager.submitNewCommand(
                PExpireAt,
                new String[] {key, Long.toString(unixMilliseconds)},
                this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> pexpireAt(@NonNull GlideString key, long unixMilliseconds) {
        return commandManager.submitNewCommand(
                PExpireAt,
                new GlideString[] {key, gs(Long.toString(unixMilliseconds))},
                this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> pexpireAt(
            @NonNull String key, long unixMilliseconds, @NonNull ExpireOptions expireOptions) {
        String[] arguments =
                ArrayUtils.addAll(
                        new String[] {key, Long.toString(unixMilliseconds)}, expireOptions.toArgs());
        return commandManager.submitNewCommand(PExpireAt, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> pexpireAt(
            @NonNull GlideString key, long unixMilliseconds, @NonNull ExpireOptions expireOptions) {
        GlideString[] arguments =
                new ArgsBuilder().add(key).add(unixMilliseconds).add(expireOptions.toArgs()).toArray();

        return commandManager.submitNewCommand(PExpireAt, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Long> ttl(@NonNull String key) {
        return commandManager.submitNewCommand(TTL, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> ttl(@NonNull GlideString key) {
        return commandManager.submitNewCommand(TTL, new GlideString[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> expiretime(@NonNull String key) {
        return commandManager.submitNewCommand(
                ExpireTime, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> expiretime(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                ExpireTime, new GlideString[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> pexpiretime(@NonNull String key) {
        return commandManager.submitNewCommand(
                PExpireTime, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> pexpiretime(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                PExpireTime, new GlideString[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Object> invokeScript(@NonNull Script script) {
        if (script.getBinaryOutput()) {
            return commandManager.submitScript(
                    script,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    this::handleBinaryObjectOrNullResponse);
        } else {
            return commandManager.submitScript(
                    script,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    this::handleObjectOrNullResponse);
        }
    }

    @Override
    public CompletableFuture<Object> invokeScript(
            @NonNull Script script, @NonNull ScriptOptions options) {
        if (script.getBinaryOutput()) {
            return commandManager.submitScript(
                    script,
                    options.getKeys().stream().map(GlideString::gs).collect(Collectors.toList()),
                    options.getArgs().stream().map(GlideString::gs).collect(Collectors.toList()),
                    this::handleBinaryObjectOrNullResponse);
        } else {
            return commandManager.submitScript(
                    script,
                    options.getKeys().stream().map(GlideString::gs).collect(Collectors.toList()),
                    options.getArgs().stream().map(GlideString::gs).collect(Collectors.toList()),
                    this::handleObjectOrNullResponse);
        }
    }

    @Override
    public CompletableFuture<Object> invokeScript(
            @NonNull Script script, @NonNull ScriptOptionsGlideString options) {
        if (script.getBinaryOutput()) {
            return commandManager.submitScript(
                    script, options.getKeys(), options.getArgs(), this::handleBinaryObjectOrNullResponse);
        } else {
            return commandManager.submitScript(
                    script, options.getKeys(), options.getArgs(), this::handleObjectOrNullResponse);
        }
    }

    @Override
    public CompletableFuture<String> scriptShow(@NonNull String sha1) {
        return commandManager.submitNewCommand(
                ScriptShow, new String[] {sha1}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<GlideString> scriptShow(@NonNull GlideString sha1) {
        return commandManager.submitNewCommand(
                ScriptShow, new GlideString[] {sha1}, this::handleGlideStringResponse);
    }

    public CompletableFuture<Boolean[]> scriptExists(@NonNull String[] sha1s) {
        return commandManager.submitNewCommand(
                ScriptExists, sha1s, response -> castArray(handleArrayResponse(response), Boolean.class));
    }

    public CompletableFuture<Boolean[]> scriptExists(@NonNull GlideString[] sha1s) {
        return commandManager.submitNewCommand(
                ScriptExists, sha1s, response -> castArray(handleArrayResponse(response), Boolean.class));
    }

    public CompletableFuture<String> scriptFlush() {
        return commandManager.submitNewCommand(
                ScriptFlush, EMPTY_STRING_ARRAY, this::handleStringResponse);
    }

    public CompletableFuture<String> scriptFlush(@NonNull FlushMode flushMode) {
        return commandManager.submitNewCommand(
                ScriptFlush, flushMode.toArgs(), this::handleStringResponse);
    }

    public CompletableFuture<String> scriptKill() {
        return commandManager.submitNewCommand(
                ScriptKill, EMPTY_STRING_ARRAY, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Long> zadd(
            @NonNull String key,
            @NonNull Map<String, Double> membersScoresMap,
            @NonNull ZAddOptions options,
            boolean changed) {
        String[] changedArg = changed ? new String[] {"CH"} : EMPTY_STRING_ARRAY;
        String[] membersScores = convertMapToValueKeyStringArray(membersScoresMap);

        String[] arguments =
                concatenateArrays(new String[] {key}, options.toArgs(), changedArg, membersScores);

        return commandManager.submitNewCommand(ZAdd, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zadd(
            @NonNull GlideString key,
            @NonNull Map<GlideString, Double> membersScoresMap,
            @NonNull ZAddOptions options,
            boolean changed) {
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(key)
                        .add(options.toArgs())
                        .addIf("CH", changed)
                        .add(convertMapToValueKeyStringArrayBinary(membersScoresMap))
                        .toArray();

        return commandManager.submitNewCommand(ZAdd, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zadd(
            @NonNull String key,
            @NonNull Map<String, Double> membersScoresMap,
            @NonNull ZAddOptions options) {
        return this.zadd(key, membersScoresMap, options, false);
    }

    @Override
    public CompletableFuture<Long> zadd(
            @NonNull GlideString key,
            @NonNull Map<GlideString, Double> membersScoresMap,
            @NonNull ZAddOptions options) {
        return this.zadd(key, membersScoresMap, options, false);
    }

    @Override
    public CompletableFuture<Long> zadd(
            @NonNull String key, @NonNull Map<String, Double> membersScoresMap, boolean changed) {
        return this.zadd(key, membersScoresMap, ZAddOptions.builder().build(), changed);
    }

    @Override
    public CompletableFuture<Long> zadd(
            @NonNull GlideString key,
            @NonNull Map<GlideString, Double> membersScoresMap,
            boolean changed) {
        return this.zadd(key, membersScoresMap, ZAddOptions.builder().build(), changed);
    }

    @Override
    public CompletableFuture<Long> zadd(
            @NonNull String key, @NonNull Map<String, Double> membersScoresMap) {
        return this.zadd(key, membersScoresMap, ZAddOptions.builder().build(), false);
    }

    @Override
    public CompletableFuture<Long> zadd(
            @NonNull GlideString key, @NonNull Map<GlideString, Double> membersScoresMap) {
        return this.zadd(key, membersScoresMap, ZAddOptions.builder().build(), false);
    }

    @Override
    public CompletableFuture<Double> zaddIncr(
            @NonNull String key, @NonNull String member, double increment, @NonNull ZAddOptions options) {
        String[] arguments =
                concatenateArrays(
                        new String[] {key},
                        options.toArgs(),
                        new String[] {"INCR", Double.toString(increment), member});

        return commandManager.submitNewCommand(ZAdd, arguments, this::handleDoubleOrNullResponse);
    }

    @Override
    public CompletableFuture<Double> zaddIncr(
            @NonNull GlideString key,
            @NonNull GlideString member,
            double increment,
            @NonNull ZAddOptions options) {
        GlideString[] arguments =
                concatenateArrays(
                        new GlideString[] {key},
                        options.toArgsBinary(),
                        new GlideString[] {gs("INCR"), gs(Double.toString(increment)), member});

        return commandManager.submitNewCommand(ZAdd, arguments, this::handleDoubleOrNullResponse);
    }

    @Override
    public CompletableFuture<Double> zaddIncr(
            @NonNull String key, @NonNull String member, double increment) {
        String[] arguments =
                concatenateArrays(
                        new String[] {key}, new String[] {"INCR", Double.toString(increment), member});

        return commandManager.submitNewCommand(ZAdd, arguments, this::handleDoubleResponse);
    }

    @Override
    public CompletableFuture<Double> zaddIncr(
            @NonNull GlideString key, @NonNull GlideString member, double increment) {
        GlideString[] arguments =
                concatenateArrays(
                        new GlideString[] {key},
                        new GlideString[] {gs("INCR"), gs(Double.toString(increment)), member});

        return commandManager.submitNewCommand(ZAdd, arguments, this::handleDoubleResponse);
    }

    @Override
    public CompletableFuture<Long> zrem(@NonNull String key, @NonNull String[] members) {
        String[] arguments = ArrayUtils.addFirst(members, key);
        return commandManager.submitNewCommand(ZRem, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zrem(@NonNull GlideString key, @NonNull GlideString[] members) {
        GlideString[] arguments = ArrayUtils.addFirst(members, key);
        return commandManager.submitNewCommand(ZRem, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zcard(@NonNull String key) {
        return commandManager.submitNewCommand(ZCard, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zcard(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                ZCard, new GlideString[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zpopmin(@NonNull String key, long count) {
        return commandManager.submitNewCommand(
                ZPopMin, new String[] {key, Long.toString(count)}, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<GlideString, Double>> zpopmin(@NonNull GlideString key, long count) {
        return commandManager.submitNewCommand(
                ZPopMin,
                new GlideString[] {key, gs(Long.toString(count))},
                this::handleBinaryStringMapResponse);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zpopmin(@NonNull String key) {
        return commandManager.submitNewCommand(ZPopMin, new String[] {key}, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<GlideString, Double>> zpopmin(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                ZPopMin, new GlideString[] {key}, this::handleBinaryStringMapResponse);
    }

    @Override
    public CompletableFuture<Object[]> bzpopmin(@NonNull String[] keys, double timeout) {
        String[] arguments = ArrayUtils.add(keys, Double.toString(timeout));
        return commandManager.submitBlockingCommand(
                BZPopMin, arguments, this::handleArrayOrNullResponse);
    }

    @Override
    public CompletableFuture<Object[]> bzpopmin(@NonNull GlideString[] keys, double timeout) {
        GlideString[] arguments = ArrayUtils.add(keys, gs(Double.toString(timeout)));
        return commandManager.submitBlockingCommand(
                BZPopMin, arguments, this::handleArrayOrNullResponseBinary);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zpopmax(@NonNull String key, long count) {
        return commandManager.submitNewCommand(
                ZPopMax, new String[] {key, Long.toString(count)}, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<GlideString, Double>> zpopmax(@NonNull GlideString key, long count) {
        return commandManager.submitNewCommand(
                ZPopMax,
                new GlideString[] {key, gs(Long.toString(count))},
                this::handleBinaryStringMapResponse);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zpopmax(@NonNull String key) {
        return commandManager.submitNewCommand(ZPopMax, new String[] {key}, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<GlideString, Double>> zpopmax(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                ZPopMax, new GlideString[] {key}, this::handleBinaryStringMapResponse);
    }

    @Override
    public CompletableFuture<Object[]> bzpopmax(@NonNull String[] keys, double timeout) {
        String[] arguments = ArrayUtils.add(keys, Double.toString(timeout));
        return commandManager.submitBlockingCommand(
                BZPopMax, arguments, this::handleArrayOrNullResponse);
    }

    @Override
    public CompletableFuture<Object[]> bzpopmax(@NonNull GlideString[] keys, double timeout) {
        GlideString[] arguments = ArrayUtils.add(keys, gs(Double.toString(timeout)));
        return commandManager.submitBlockingCommand(
                BZPopMax, arguments, this::handleArrayOrNullResponseBinary);
    }

    @Override
    public CompletableFuture<Double> zscore(@NonNull String key, @NonNull String member) {
        return commandManager.submitNewCommand(
                ZScore, new String[] {key, member}, this::handleDoubleOrNullResponse);
    }

    @Override
    public CompletableFuture<Double> zscore(@NonNull GlideString key, @NonNull GlideString member) {
        return commandManager.submitNewCommand(
                ZScore, new GlideString[] {key, member}, this::handleDoubleOrNullResponse);
    }

    @Override
    public CompletableFuture<Long> zrank(@NonNull String key, @NonNull String member) {
        return commandManager.submitNewCommand(
                ZRank, new String[] {key, member}, this::handleLongOrNullResponse);
    }

    @Override
    public CompletableFuture<Long> zrank(@NonNull GlideString key, @NonNull GlideString member) {
        return commandManager.submitNewCommand(
                ZRank, new GlideString[] {key, member}, this::handleLongOrNullResponse);
    }

    @Override
    public CompletableFuture<Object[]> zrankWithScore(@NonNull String key, @NonNull String member) {
        return commandManager.submitNewCommand(
                ZRank, new String[] {key, member, WITH_SCORE_VALKEY_API}, this::handleArrayOrNullResponse);
    }

    @Override
    public CompletableFuture<Object[]> zrankWithScore(
            @NonNull GlideString key, @NonNull GlideString member) {
        return commandManager.submitNewCommand(
                ZRank,
                new GlideString[] {key, member, gs(WITH_SCORE_VALKEY_API)},
                this::handleArrayOrNullResponse);
    }

    @Override
    public CompletableFuture<Long> zrevrank(@NonNull String key, @NonNull String member) {
        return commandManager.submitNewCommand(
                ZRevRank, new String[] {key, member}, this::handleLongOrNullResponse);
    }

    @Override
    public CompletableFuture<Long> zrevrank(@NonNull GlideString key, @NonNull GlideString member) {
        return commandManager.submitNewCommand(
                ZRevRank, new GlideString[] {key, member}, this::handleLongOrNullResponse);
    }

    @Override
    public CompletableFuture<Object[]> zrevrankWithScore(
            @NonNull String key, @NonNull String member) {
        return commandManager.submitNewCommand(
                ZRevRank,
                new String[] {key, member, WITH_SCORE_VALKEY_API},
                this::handleArrayOrNullResponse);
    }

    @Override
    public CompletableFuture<Object[]> zrevrankWithScore(
            @NonNull GlideString key, @NonNull GlideString member) {
        return commandManager.submitNewCommand(
                ZRevRank,
                new GlideString[] {key, member, gs(WITH_SCORE_VALKEY_API)},
                this::handleArrayOrNullResponseBinary);
    }

    @Override
    public CompletableFuture<Double[]> zmscore(@NonNull String key, @NonNull String[] members) {
        String[] arguments = ArrayUtils.addFirst(members, key);
        return commandManager.submitNewCommand(
                ZMScore,
                arguments,
                response -> castArray(handleArrayOrNullResponse(response), Double.class));
    }

    @Override
    public CompletableFuture<Double[]> zmscore(
            @NonNull GlideString key, @NonNull GlideString[] members) {
        GlideString[] arguments = ArrayUtils.addFirst(members, key);
        return commandManager.submitNewCommand(
                ZMScore,
                arguments,
                response -> castArray(handleArrayOrNullResponse(response), Double.class));
    }

    @Override
    public CompletableFuture<String[]> zdiff(@NonNull String[] keys) {
        String[] arguments = ArrayUtils.addFirst(keys, Long.toString(keys.length));
        return commandManager.submitNewCommand(
                ZDiff, arguments, response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> zdiff(@NonNull GlideString[] keys) {
        GlideString[] arguments = new ArgsBuilder().add(keys.length).add(keys).toArray();
        return commandManager.submitNewCommand(
                ZDiff,
                arguments,
                response -> castArray(handleArrayResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Map<String, Double>> zdiffWithScores(@NonNull String[] keys) {
        String[] arguments = ArrayUtils.addFirst(keys, Long.toString(keys.length));
        arguments = ArrayUtils.add(arguments, WITH_SCORES_VALKEY_API);
        return commandManager.submitNewCommand(ZDiff, arguments, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<GlideString, Double>> zdiffWithScores(@NonNull GlideString[] keys) {
        GlideString[] arguments = new ArgsBuilder().add(keys.length).add(keys).toArray();
        arguments = ArrayUtils.add(arguments, gs(WITH_SCORES_VALKEY_API));
        return commandManager.submitNewCommand(ZDiff, arguments, this::handleBinaryStringMapResponse);
    }

    @Override
    public CompletableFuture<Long> zdiffstore(@NonNull String destination, @NonNull String[] keys) {
        String[] arguments =
                ArrayUtils.addAll(new String[] {destination, Long.toString(keys.length)}, keys);
        return commandManager.submitNewCommand(ZDiffStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zdiffstore(
            @NonNull GlideString destination, @NonNull GlideString[] keys) {
        GlideString[] arguments =
                ArrayUtils.addAll(new GlideString[] {destination, gs(Long.toString(keys.length))}, keys);
        return commandManager.submitNewCommand(ZDiffStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zcount(
            @NonNull String key, @NonNull ScoreRange minScore, @NonNull ScoreRange maxScore) {
        return commandManager.submitNewCommand(
                ZCount, new String[] {key, minScore.toArgs(), maxScore.toArgs()}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zcount(
            @NonNull GlideString key, @NonNull ScoreRange minScore, @NonNull ScoreRange maxScore) {
        return commandManager.submitNewCommand(
                ZCount,
                new ArgsBuilder().add(key).add(minScore.toArgs()).add(maxScore.toArgs()).toArray(),
                this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zremrangebyrank(@NonNull String key, long start, long end) {
        return commandManager.submitNewCommand(
                ZRemRangeByRank,
                new String[] {key, Long.toString(start), Long.toString(end)},
                this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zremrangebyrank(@NonNull GlideString key, long start, long end) {
        return commandManager.submitNewCommand(
                ZRemRangeByRank,
                new GlideString[] {key, gs(Long.toString(start)), gs(Long.toString(end))},
                this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zremrangebylex(
            @NonNull String key, @NonNull LexRange minLex, @NonNull LexRange maxLex) {
        return commandManager.submitNewCommand(
                ZRemRangeByLex,
                new String[] {key, minLex.toArgs(), maxLex.toArgs()},
                this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zremrangebylex(
            @NonNull GlideString key, @NonNull LexRange minLex, @NonNull LexRange maxLex) {
        return commandManager.submitNewCommand(
                ZRemRangeByLex,
                new ArgsBuilder().add(key).add(minLex.toArgs()).add(maxLex.toArgs()).toArray(),
                this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zremrangebyscore(
            @NonNull String key, @NonNull ScoreRange minScore, @NonNull ScoreRange maxScore) {
        return commandManager.submitNewCommand(
                ZRemRangeByScore,
                new String[] {key, minScore.toArgs(), maxScore.toArgs()},
                this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zremrangebyscore(
            @NonNull GlideString key, @NonNull ScoreRange minScore, @NonNull ScoreRange maxScore) {
        return commandManager.submitNewCommand(
                ZRemRangeByScore,
                new ArgsBuilder().add(key).add(minScore.toArgs()).add(maxScore.toArgs()).toArray(),
                this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zlexcount(
            @NonNull String key, @NonNull LexRange minLex, @NonNull LexRange maxLex) {
        return commandManager.submitNewCommand(
                ZLexCount, new String[] {key, minLex.toArgs(), maxLex.toArgs()}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zlexcount(
            @NonNull GlideString key, @NonNull LexRange minLex, @NonNull LexRange maxLex) {
        return commandManager.submitNewCommand(
                ZLexCount,
                new ArgsBuilder().add(key).add(minLex.toArgs()).add(maxLex.toArgs()).toArray(),
                this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zrangestore(
            @NonNull String destination,
            @NonNull String source,
            @NonNull RangeQuery rangeQuery,
            boolean reverse) {
        String[] arguments =
                RangeOptions.createZRangeStoreArgs(destination, source, rangeQuery, reverse);

        return commandManager.submitNewCommand(ZRangeStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zrangestore(
            @NonNull GlideString destination,
            @NonNull GlideString source,
            @NonNull RangeQuery rangeQuery,
            boolean reverse) {
        GlideString[] arguments =
                RangeOptions.createZRangeStoreArgsBinary(destination, source, rangeQuery, reverse);

        return commandManager.submitNewCommand(ZRangeStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zrangestore(
            @NonNull String destination, @NonNull String source, @NonNull RangeQuery rangeQuery) {
        return zrangestore(destination, source, rangeQuery, false);
    }

    @Override
    public CompletableFuture<Long> zrangestore(
            @NonNull GlideString destination,
            @NonNull GlideString source,
            @NonNull RangeQuery rangeQuery) {
        return zrangestore(destination, source, rangeQuery, false);
    }

    @Override
    public CompletableFuture<Long> zunionstore(
            @NonNull String destination,
            @NonNull KeysOrWeightedKeys keysOrWeightedKeys,
            @NonNull Aggregate aggregate) {
        String[] arguments =
                concatenateArrays(
                        new String[] {destination}, keysOrWeightedKeys.toArgs(), aggregate.toArgs());
        return commandManager.submitNewCommand(ZUnionStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zunionstore(
            @NonNull GlideString destination,
            @NonNull KeysOrWeightedKeysBinary keysOrWeightedKeys,
            @NonNull Aggregate aggregate) {
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(destination)
                        .add(keysOrWeightedKeys.toArgs())
                        .add(aggregate.toArgs())
                        .toArray();

        return commandManager.submitNewCommand(ZUnionStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zunionstore(
            @NonNull String destination, @NonNull KeysOrWeightedKeys keysOrWeightedKeys) {
        String[] arguments = concatenateArrays(new String[] {destination}, keysOrWeightedKeys.toArgs());
        return commandManager.submitNewCommand(ZUnionStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zunionstore(
            @NonNull GlideString destination, @NonNull KeysOrWeightedKeysBinary keysOrWeightedKeys) {
        GlideString[] arguments =
                new ArgsBuilder().add(destination).add(keysOrWeightedKeys.toArgs()).toArray();
        return commandManager.submitNewCommand(ZUnionStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zinterstore(
            @NonNull String destination,
            @NonNull KeysOrWeightedKeys keysOrWeightedKeys,
            @NonNull Aggregate aggregate) {
        String[] arguments =
                concatenateArrays(
                        new String[] {destination}, keysOrWeightedKeys.toArgs(), aggregate.toArgs());
        return commandManager.submitNewCommand(ZInterStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zinterstore(
            @NonNull GlideString destination,
            @NonNull KeysOrWeightedKeysBinary keysOrWeightedKeys,
            @NonNull Aggregate aggregate) {
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(destination)
                        .add(keysOrWeightedKeys.toArgs())
                        .add(aggregate.toArgs())
                        .toArray();
        return commandManager.submitNewCommand(ZInterStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zinterstore(
            @NonNull String destination, @NonNull KeysOrWeightedKeys keysOrWeightedKeys) {
        String[] arguments = concatenateArrays(new String[] {destination}, keysOrWeightedKeys.toArgs());
        return commandManager.submitNewCommand(ZInterStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zinterstore(
            @NonNull GlideString destination, @NonNull KeysOrWeightedKeysBinary keysOrWeightedKeys) {
        GlideString[] arguments =
                concatenateArrays(new GlideString[] {destination}, keysOrWeightedKeys.toArgs());
        return commandManager.submitNewCommand(ZInterStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String[]> zunion(@NonNull KeyArray keys) {
        return commandManager.submitNewCommand(
                ZUnion, keys.toArgs(), response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> zunion(@NonNull KeyArrayBinary keys) {
        return commandManager.submitNewCommand(
                ZUnion,
                keys.toArgs(),
                response -> castArray(handleArrayResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Map<String, Double>> zunionWithScores(
            @NonNull KeysOrWeightedKeys keysOrWeightedKeys, @NonNull Aggregate aggregate) {
        String[] arguments =
                concatenateArrays(
                        keysOrWeightedKeys.toArgs(), aggregate.toArgs(), new String[] {WITH_SCORES_VALKEY_API});
        return commandManager.submitNewCommand(ZUnion, arguments, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<GlideString, Double>> zunionWithScores(
            @NonNull KeysOrWeightedKeysBinary keysOrWeightedKeys, @NonNull Aggregate aggregate) {
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(keysOrWeightedKeys.toArgs())
                        .add(aggregate.toArgs())
                        .add(WITH_SCORES_VALKEY_API)
                        .toArray();
        return commandManager.submitNewCommand(ZUnion, arguments, this::handleBinaryStringMapResponse);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zunionWithScores(
            @NonNull KeysOrWeightedKeys keysOrWeightedKeys) {
        String[] arguments =
                concatenateArrays(keysOrWeightedKeys.toArgs(), new String[] {WITH_SCORES_VALKEY_API});
        return commandManager.submitNewCommand(ZUnion, arguments, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<GlideString, Double>> zunionWithScores(
            @NonNull KeysOrWeightedKeysBinary keysOrWeightedKeys) {
        GlideString[] arguments =
                new ArgsBuilder().add(keysOrWeightedKeys.toArgs()).add(WITH_SCORES_VALKEY_API).toArray();

        return commandManager.submitNewCommand(ZUnion, arguments, this::handleBinaryStringMapResponse);
    }

    @Override
    public CompletableFuture<String[]> zinter(@NonNull KeyArray keys) {
        return commandManager.submitNewCommand(
                ZInter, keys.toArgs(), response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> zinter(@NonNull KeyArrayBinary keys) {
        return commandManager.submitNewCommand(
                ZInter,
                keys.toArgs(),
                response -> castArray(handleArrayResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Map<String, Double>> zinterWithScores(
            @NonNull KeysOrWeightedKeys keysOrWeightedKeys, @NonNull Aggregate aggregate) {
        String[] arguments =
                concatenateArrays(
                        keysOrWeightedKeys.toArgs(), aggregate.toArgs(), new String[] {WITH_SCORES_VALKEY_API});
        return commandManager.submitNewCommand(ZInter, arguments, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<GlideString, Double>> zinterWithScores(
            @NonNull KeysOrWeightedKeysBinary keysOrWeightedKeys, @NonNull Aggregate aggregate) {
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(keysOrWeightedKeys.toArgs())
                        .add(aggregate.toArgs())
                        .add(WITH_SCORES_VALKEY_API)
                        .toArray();
        return commandManager.submitNewCommand(ZInter, arguments, this::handleBinaryStringMapResponse);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zinterWithScores(
            @NonNull KeysOrWeightedKeys keysOrWeightedKeys) {
        String[] arguments =
                concatenateArrays(keysOrWeightedKeys.toArgs(), new String[] {WITH_SCORES_VALKEY_API});
        return commandManager.submitNewCommand(ZInter, arguments, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<GlideString, Double>> zinterWithScores(
            @NonNull KeysOrWeightedKeysBinary keysOrWeightedKeys) {
        GlideString[] arguments =
                concatenateArrays(
                        keysOrWeightedKeys.toArgs(), new GlideString[] {gs(WITH_SCORES_VALKEY_API)});
        return commandManager.submitNewCommand(ZInter, arguments, this::handleBinaryStringMapResponse);
    }

    @Override
    public CompletableFuture<String> zrandmember(@NonNull String key) {
        return commandManager.submitNewCommand(
                ZRandMember, new String[] {key}, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<GlideString> zrandmember(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                ZRandMember, new GlideString[] {key}, this::handleGlideStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String[]> zrandmemberWithCount(@NonNull String key, long count) {
        return commandManager.submitNewCommand(
                ZRandMember,
                new String[] {key, Long.toString(count)},
                response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> zrandmemberWithCount(
            @NonNull GlideString key, long count) {
        return commandManager.submitNewCommand(
                ZRandMember,
                new GlideString[] {key, gs(Long.toString(count))},
                response -> castArray(handleArrayResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Object[][]> zrandmemberWithCountWithScores(
            @NonNull String key, long count) {
        String[] arguments = new String[] {key, Long.toString(count), WITH_SCORES_VALKEY_API};
        return commandManager.submitNewCommand(
                ZRandMember,
                arguments,
                response -> castArray(handleArrayResponse(response), Object[].class));
    }

    @Override
    public CompletableFuture<Object[][]> zrandmemberWithCountWithScores(
            @NonNull GlideString key, long count) {
        GlideString[] arguments =
                new GlideString[] {key, gs(Long.toString(count)), gs(WITH_SCORES_VALKEY_API)};
        return commandManager.submitNewCommand(
                ZRandMember,
                arguments,
                response -> castArray(handleArrayResponseBinary(response), Object[].class));
    }

    @Override
    public CompletableFuture<Double> zincrby(
            @NonNull String key, double increment, @NonNull String member) {
        String[] arguments = new String[] {key, Double.toString(increment), member};
        return commandManager.submitNewCommand(ZIncrBy, arguments, this::handleDoubleResponse);
    }

    @Override
    public CompletableFuture<Double> zincrby(
            @NonNull GlideString key, double increment, @NonNull GlideString member) {
        GlideString[] arguments = new GlideString[] {key, gs(Double.toString(increment)), member};
        return commandManager.submitNewCommand(ZIncrBy, arguments, this::handleDoubleResponse);
    }

    @Override
    public CompletableFuture<Long> zintercard(@NonNull String[] keys) {
        String[] arguments = ArrayUtils.addFirst(keys, Integer.toString(keys.length));
        return commandManager.submitNewCommand(ZInterCard, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zintercard(@NonNull GlideString[] keys) {
        GlideString[] arguments = ArrayUtils.addFirst(keys, gs(Integer.toString(keys.length)));
        return commandManager.submitNewCommand(ZInterCard, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zintercard(@NonNull String[] keys, long limit) {
        String[] arguments =
                concatenateArrays(
                        new String[] {Integer.toString(keys.length)},
                        keys,
                        new String[] {LIMIT_VALKEY_API, Long.toString(limit)});
        return commandManager.submitNewCommand(ZInterCard, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zintercard(@NonNull GlideString[] keys, long limit) {
        GlideString[] arguments =
                concatenateArrays(
                        new GlideString[] {gs(Integer.toString(keys.length))},
                        keys,
                        new GlideString[] {gs(LIMIT_VALKEY_API), gs(Long.toString(limit))});
        return commandManager.submitNewCommand(ZInterCard, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String> xadd(@NonNull String key, @NonNull Map<String, String> values) {
        return xadd(key, values, StreamAddOptions.builder().build());
    }

    @Override
    public CompletableFuture<String> xadd(@NonNull String key, @NonNull String[][] values) {
        return xadd(key, values, StreamAddOptions.builder().build());
    }

    @Override
    public CompletableFuture<GlideString> xadd(
            @NonNull GlideString key, @NonNull Map<GlideString, GlideString> values) {
        return xadd(key, values, StreamAddOptionsBinary.builder().build());
    }

    @Override
    public CompletableFuture<GlideString> xadd(
            @NonNull GlideString key, @NonNull GlideString[][] values) {
        return xadd(key, values, StreamAddOptionsBinary.builder().build());
    }

    @Override
    public CompletableFuture<String> xadd(
            @NonNull String key, @NonNull Map<String, String> values, @NonNull StreamAddOptions options) {
        String[] arguments =
                ArrayUtils.addAll(
                        ArrayUtils.addFirst(options.toArgs(), key), convertMapToKeyValueStringArray(values));
        return commandManager.submitNewCommand(XAdd, arguments, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String> xadd(
            @NonNull String key, @NonNull String[][] values, @NonNull StreamAddOptions options) {
        String[] arguments =
                ArrayUtils.addAll(
                        ArrayUtils.addFirst(options.toArgs(), key),
                        convertNestedArrayToKeyValueStringArray(values));
        return commandManager.submitNewCommand(XAdd, arguments, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<GlideString> xadd(
            @NonNull GlideString key,
            @NonNull Map<GlideString, GlideString> values,
            @NonNull StreamAddOptionsBinary options) {
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(key)
                        .add(options.toArgs())
                        .add(convertMapToKeyValueGlideStringArray(values))
                        .toArray();

        return commandManager.submitNewCommand(XAdd, arguments, this::handleGlideStringOrNullResponse);
    }

    @Override
    public CompletableFuture<GlideString> xadd(
            @NonNull GlideString key,
            @NonNull GlideString[][] values,
            @NonNull StreamAddOptionsBinary options) {
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(key)
                        .add(options.toArgs())
                        .add(convertNestedArrayToKeyValueGlideStringArray(values))
                        .toArray();

        return commandManager.submitNewCommand(XAdd, arguments, this::handleGlideStringOrNullResponse);
    }

    @Override
    public CompletableFuture<Map<String, Map<String, String[][]>>> xread(
            @NonNull Map<String, String> keysAndIds) {
        return xread(keysAndIds, StreamReadOptions.builder().build());
    }

    @Override
    public CompletableFuture<Map<GlideString, Map<GlideString, GlideString[][]>>> xreadBinary(
            @NonNull Map<GlideString, GlideString> keysAndIds) {
        return xreadBinary(keysAndIds, StreamReadOptions.builder().build());
    }

    @Override
    public CompletableFuture<Map<String, Map<String, String[][]>>> xread(
            @NonNull Map<String, String> keysAndIds, @NonNull StreamReadOptions options) {
        String[] arguments = options.toArgs(keysAndIds);
        if (options.isBlocking()) {
            return commandManager.submitBlockingCommand(XRead, arguments, this::handleXReadResponse);
        }
        return commandManager.submitNewCommand(XRead, arguments, this::handleXReadResponse);
    }

    @Override
    public CompletableFuture<Map<GlideString, Map<GlideString, GlideString[][]>>> xreadBinary(
            @NonNull Map<GlideString, GlideString> keysAndIds, @NonNull StreamReadOptions options) {
        GlideString[] arguments = options.toArgsBinary(keysAndIds);
        if (options.isBlocking()) {
            return commandManager.submitBlockingCommand(
                    XRead, arguments, this::handleXReadResponseBinary);
        }
        return commandManager.submitNewCommand(XRead, arguments, this::handleXReadResponseBinary);
    }

    @Override
    public CompletableFuture<Long> xtrim(@NonNull String key, @NonNull StreamTrimOptions options) {
        String[] arguments = ArrayUtils.addFirst(options.toArgs(), key);
        return commandManager.submitNewCommand(XTrim, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> xtrim(
            @NonNull GlideString key, @NonNull StreamTrimOptions options) {
        GlideString[] arguments = new ArgsBuilder().add(key).add(options.toArgs()).toArray();

        return commandManager.submitNewCommand(XTrim, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> xlen(@NonNull String key) {
        return commandManager.submitNewCommand(XLen, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> xlen(@NonNull GlideString key) {
        return commandManager.submitNewCommand(XLen, new GlideString[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> xdel(@NonNull String key, @NonNull String[] ids) {
        String[] arguments = ArrayUtils.addFirst(ids, key);
        return commandManager.submitNewCommand(XDel, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> xdel(@NonNull GlideString key, @NonNull GlideString[] ids) {
        GlideString[] arguments = ArrayUtils.addFirst(ids, key);
        return commandManager.submitNewCommand(XDel, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Map<String, String[][]>> xrange(
            @NonNull String key, @NonNull StreamRange start, @NonNull StreamRange end) {
        String[] arguments = ArrayUtils.addFirst(StreamRange.toArgs(start, end), key);
        return commandManager.submitNewCommand(
                XRange, arguments, response -> castMapOf2DArray(handleMapResponse(response), String.class));
    }

    @Override
    public CompletableFuture<Map<GlideString, GlideString[][]>> xrange(
            @NonNull GlideString key, @NonNull StreamRange start, @NonNull StreamRange end) {
        String[] toArgsString = StreamRange.toArgs(start, end);
        GlideString[] toArgsBinary =
                Arrays.stream(toArgsString).map(GlideString::gs).toArray(GlideString[]::new);
        GlideString[] arguments = ArrayUtils.addFirst(toArgsBinary, key);
        return commandManager.submitNewCommand(
                XRange,
                arguments,
                response -> castMapOf2DArray(handleBinaryStringMapResponse(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Map<String, String[][]>> xrange(
            @NonNull String key, @NonNull StreamRange start, @NonNull StreamRange end, long count) {
        String[] arguments = ArrayUtils.addFirst(StreamRange.toArgs(start, end, count), key);
        return commandManager.submitNewCommand(
                XRange,
                arguments,
                response -> castMapOf2DArray(handleMapOrNullResponse(response), String.class));
    }

    @Override
    public CompletableFuture<Map<GlideString, GlideString[][]>> xrange(
            @NonNull GlideString key, @NonNull StreamRange start, @NonNull StreamRange end, long count) {
        String[] toArgsString = StreamRange.toArgs(start, end, count);
        GlideString[] toArgsBinary =
                Arrays.stream(toArgsString).map(GlideString::gs).toArray(GlideString[]::new);
        GlideString[] arguments = ArrayUtils.addFirst(toArgsBinary, key);
        return commandManager.submitNewCommand(
                XRange,
                arguments,
                response ->
                        castMapOf2DArray(handleBinaryStringMapOrNullResponse(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Map<String, String[][]>> xrevrange(
            @NonNull String key, @NonNull StreamRange end, @NonNull StreamRange start) {
        String[] arguments = ArrayUtils.addFirst(StreamRange.toArgs(end, start), key);
        return commandManager.submitNewCommand(
                XRevRange,
                arguments,
                response -> castMapOf2DArray(handleMapResponse(response), String.class));
    }

    @Override
    public CompletableFuture<Map<GlideString, GlideString[][]>> xrevrange(
            @NonNull GlideString key, @NonNull StreamRange end, @NonNull StreamRange start) {
        String[] toArgsString = StreamRange.toArgs(end, start);
        GlideString[] toArgsBinary =
                Arrays.stream(toArgsString).map(GlideString::gs).toArray(GlideString[]::new);
        GlideString[] arguments = ArrayUtils.addFirst(toArgsBinary, key);
        return commandManager.submitNewCommand(
                XRevRange,
                arguments,
                response -> castMapOf2DArray(handleBinaryStringMapResponse(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Map<String, String[][]>> xrevrange(
            @NonNull String key, @NonNull StreamRange end, @NonNull StreamRange start, long count) {
        String[] arguments = ArrayUtils.addFirst(StreamRange.toArgs(end, start, count), key);
        return commandManager.submitNewCommand(
                XRevRange,
                arguments,
                response -> castMapOf2DArray(handleMapOrNullResponse(response), String.class));
    }

    @Override
    public CompletableFuture<Map<GlideString, GlideString[][]>> xrevrange(
            @NonNull GlideString key, @NonNull StreamRange end, @NonNull StreamRange start, long count) {
        String[] toArgsString = StreamRange.toArgs(end, start, count);
        GlideString[] toArgsBinary =
                Arrays.stream(toArgsString).map(GlideString::gs).toArray(GlideString[]::new);
        GlideString[] arguments = ArrayUtils.addFirst(toArgsBinary, key);
        return commandManager.submitNewCommand(
                XRevRange,
                arguments,
                response ->
                        castMapOf2DArray(handleBinaryStringMapOrNullResponse(response), GlideString.class));
    }

    @Override
    public CompletableFuture<String> xgroupCreate(
            @NonNull String key, @NonNull String groupName, @NonNull String id) {
        return commandManager.submitNewCommand(
                XGroupCreate, new String[] {key, groupName, id}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> xgroupCreate(
            @NonNull GlideString key, @NonNull GlideString groupName, @NonNull GlideString id) {
        return commandManager.submitNewCommand(
                XGroupCreate, new GlideString[] {key, groupName, id}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> xgroupCreate(
            @NonNull String key,
            @NonNull String groupName,
            @NonNull String id,
            @NonNull StreamGroupOptions options) {
        String[] arguments = concatenateArrays(new String[] {key, groupName, id}, options.toArgs());
        return commandManager.submitNewCommand(XGroupCreate, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> xgroupCreate(
            @NonNull GlideString key,
            @NonNull GlideString groupName,
            @NonNull GlideString id,
            @NonNull StreamGroupOptions options) {
        GlideString[] arguments =
                new ArgsBuilder().add(key).add(groupName).add(id).add(options.toArgs()).toArray();
        return commandManager.submitNewCommand(XGroupCreate, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Boolean> xgroupDestroy(@NonNull String key, @NonNull String groupname) {
        return commandManager.submitNewCommand(
                XGroupDestroy, new String[] {key, groupname}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> xgroupDestroy(
            @NonNull GlideString key, @NonNull GlideString groupname) {
        return commandManager.submitNewCommand(
                XGroupDestroy, new GlideString[] {key, groupname}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> xgroupCreateConsumer(
            @NonNull String key, @NonNull String group, @NonNull String consumer) {
        return commandManager.submitNewCommand(
                XGroupCreateConsumer, new String[] {key, group, consumer}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> xgroupCreateConsumer(
            @NonNull GlideString key, @NonNull GlideString group, @NonNull GlideString consumer) {
        return commandManager.submitNewCommand(
                XGroupCreateConsumer,
                new GlideString[] {key, group, consumer},
                this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Long> xgroupDelConsumer(
            @NonNull String key, @NonNull String group, @NonNull String consumer) {
        return commandManager.submitNewCommand(
                XGroupDelConsumer, new String[] {key, group, consumer}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> xgroupDelConsumer(
            @NonNull GlideString key, @NonNull GlideString group, @NonNull GlideString consumer) {
        return commandManager.submitNewCommand(
                XGroupDelConsumer, new GlideString[] {key, group, consumer}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String> xgroupSetId(
            @NonNull String key, @NonNull String groupName, @NonNull String id) {
        return commandManager.submitNewCommand(
                XGroupSetId, new String[] {key, groupName, id}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> xgroupSetId(
            @NonNull GlideString key, @NonNull GlideString groupName, @NonNull GlideString id) {
        return commandManager.submitNewCommand(
                XGroupSetId, new GlideString[] {key, groupName, id}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> xgroupSetId(
            @NonNull String key, @NonNull String groupName, @NonNull String id, long entriesRead) {
        String[] arguments =
                new String[] {key, groupName, id, ENTRIES_READ_VALKEY_API, Long.toString(entriesRead)};
        return commandManager.submitNewCommand(XGroupSetId, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> xgroupSetId(
            @NonNull GlideString key,
            @NonNull GlideString groupName,
            @NonNull GlideString id,
            long entriesRead) {
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(key)
                        .add(groupName)
                        .add(id)
                        .add(ENTRIES_READ_VALKEY_API)
                        .add(entriesRead)
                        .toArray();
        return commandManager.submitNewCommand(XGroupSetId, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Map<String, Map<String, String[][]>>> xreadgroup(
            @NonNull Map<String, String> keysAndIds, @NonNull String group, @NonNull String consumer) {
        return xreadgroup(keysAndIds, group, consumer, StreamReadGroupOptions.builder().build());
    }

    @Override
    public CompletableFuture<Map<GlideString, Map<GlideString, GlideString[][]>>> xreadgroup(
            @NonNull Map<GlideString, GlideString> keysAndIds,
            @NonNull GlideString group,
            @NonNull GlideString consumer) {
        return xreadgroup(keysAndIds, group, consumer, StreamReadGroupOptions.builder().build());
    }

    @Override
    public CompletableFuture<Map<String, Map<String, String[][]>>> xreadgroup(
            @NonNull Map<String, String> keysAndIds,
            @NonNull String group,
            @NonNull String consumer,
            @NonNull StreamReadGroupOptions options) {
        String[] arguments = options.toArgs(group, consumer, keysAndIds);
        if (options.isBlocking()) {
            return commandManager.submitBlockingCommand(XReadGroup, arguments, this::handleXReadResponse);
        }
        return commandManager.submitNewCommand(XReadGroup, arguments, this::handleXReadResponse);
    }

    @Override
    public CompletableFuture<Map<GlideString, Map<GlideString, GlideString[][]>>> xreadgroup(
            @NonNull Map<GlideString, GlideString> keysAndIds,
            @NonNull GlideString group,
            @NonNull GlideString consumer,
            @NonNull StreamReadGroupOptions options) {
        GlideString[] arguments = options.toArgsBinary(group, consumer, keysAndIds);
        if (options.isBlocking()) {
            return commandManager.submitBlockingCommand(
                    XReadGroup, arguments, this::handleXReadResponseBinary);
        }
        return commandManager.submitNewCommand(XReadGroup, arguments, this::handleXReadResponseBinary);
    }

    @Override
    public CompletableFuture<Long> xack(
            @NonNull String key, @NonNull String group, @NonNull String[] ids) {
        String[] args = concatenateArrays(new String[] {key, group}, ids);
        return commandManager.submitNewCommand(XAck, args, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> xack(
            @NonNull GlideString key, @NonNull GlideString group, @NonNull GlideString[] ids) {
        GlideString[] args = concatenateArrays(new GlideString[] {key, group}, ids);
        return commandManager.submitNewCommand(XAck, args, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Object[]> xpending(@NonNull String key, @NonNull String group) {
        return commandManager.submitNewCommand(
                XPending, new String[] {key, group}, this::handleArrayResponse);
    }

    @Override
    public CompletableFuture<Object[]> xpending(
            @NonNull GlideString key, @NonNull GlideString group) {
        return commandManager.submitNewCommand(
                XPending, new GlideString[] {key, group}, this::handleArrayResponseBinary);
    }

    @Override
    public CompletableFuture<Object[][]> xpending(
            @NonNull String key,
            @NonNull String group,
            @NonNull StreamRange start,
            @NonNull StreamRange end,
            long count) {
        return xpending(key, group, start, end, count, StreamPendingOptions.builder().build());
    }

    @Override
    public CompletableFuture<Object[][]> xpending(
            @NonNull GlideString key,
            @NonNull GlideString group,
            @NonNull StreamRange start,
            @NonNull StreamRange end,
            long count) {
        return xpending(key, group, start, end, count, StreamPendingOptionsBinary.builder().build());
    }

    @Override
    public CompletableFuture<Object[][]> xpending(
            @NonNull String key,
            @NonNull String group,
            @NonNull StreamRange start,
            @NonNull StreamRange end,
            long count,
            @NonNull StreamPendingOptions options) {
        String[] args = concatenateArrays(new String[] {key, group}, options.toArgs(start, end, count));
        return commandManager.submitNewCommand(
                XPending, args, response -> castArray(handleArrayResponse(response), Object[].class));
    }

    @Override
    public CompletableFuture<Object[][]> xpending(
            @NonNull GlideString key,
            @NonNull GlideString group,
            @NonNull StreamRange start,
            @NonNull StreamRange end,
            long count,
            @NonNull StreamPendingOptionsBinary options) {
        GlideString[] args =
                concatenateArrays(new GlideString[] {key, group}, options.toArgs(start, end, count));
        return commandManager.submitNewCommand(
                XPending, args, response -> castArray(handleArrayResponseBinary(response), Object[].class));
    }

    @Override
    public CompletableFuture<Map<String, String[][]>> xclaim(
            @NonNull String key,
            @NonNull String group,
            @NonNull String consumer,
            long minIdleTime,
            @NonNull String[] ids) {
        String[] args =
                concatenateArrays(new String[] {key, group, consumer, Long.toString(minIdleTime)}, ids);
        return commandManager.submitNewCommand(XClaim, args, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<GlideString, GlideString[][]>> xclaim(
            @NonNull GlideString key,
            @NonNull GlideString group,
            @NonNull GlideString consumer,
            long minIdleTime,
            @NonNull GlideString[] ids) {
        GlideString[] args =
                concatenateArrays(
                        new GlideString[] {key, group, consumer, gs(Long.toString(minIdleTime))}, ids);
        return commandManager.submitNewCommand(
                XClaim,
                args,
                response -> castMapOf2DArray(handleBinaryStringMapResponse(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Map<String, String[][]>> xclaim(
            @NonNull String key,
            @NonNull String group,
            @NonNull String consumer,
            long minIdleTime,
            @NonNull String[] ids,
            @NonNull StreamClaimOptions options) {
        String[] args =
                concatenateArrays(
                        new String[] {key, group, consumer, Long.toString(minIdleTime)}, ids, options.toArgs());
        return commandManager.submitNewCommand(XClaim, args, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<GlideString, GlideString[][]>> xclaim(
            @NonNull GlideString key,
            @NonNull GlideString group,
            @NonNull GlideString consumer,
            long minIdleTime,
            @NonNull GlideString[] ids,
            @NonNull StreamClaimOptions options) {
        String[] toArgsString = options.toArgs();
        GlideString[] toArgs =
                Arrays.stream(toArgsString).map(GlideString::gs).toArray(GlideString[]::new);
        GlideString[] args =
                concatenateArrays(
                        new GlideString[] {key, group, consumer, gs(Long.toString(minIdleTime))}, ids, toArgs);
        return commandManager.submitNewCommand(
                XClaim,
                args,
                response -> castMapOf2DArray(handleBinaryStringMapResponse(response), GlideString.class));
    }

    @Override
    public CompletableFuture<String[]> xclaimJustId(
            @NonNull String key,
            @NonNull String group,
            @NonNull String consumer,
            long minIdleTime,
            @NonNull String[] ids) {
        String[] args =
                concatenateArrays(
                        new String[] {key, group, consumer, Long.toString(minIdleTime)},
                        ids,
                        new String[] {JUST_ID_VALKEY_API});
        return commandManager.submitNewCommand(
                XClaim, args, response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> xclaimJustId(
            @NonNull GlideString key,
            @NonNull GlideString group,
            @NonNull GlideString consumer,
            long minIdleTime,
            @NonNull GlideString[] ids) {
        GlideString[] args =
                concatenateArrays(
                        new GlideString[] {key, group, consumer, gs(Long.toString(minIdleTime))},
                        ids,
                        new GlideString[] {gs(JUST_ID_VALKEY_API)});
        return commandManager.submitNewCommand(
                XClaim,
                args,
                response -> castArray(handleArrayResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<String[]> xclaimJustId(
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
                        new String[] {JUST_ID_VALKEY_API});
        return commandManager.submitNewCommand(
                XClaim, args, response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> xclaimJustId(
            @NonNull GlideString key,
            @NonNull GlideString group,
            @NonNull GlideString consumer,
            long minIdleTime,
            @NonNull GlideString[] ids,
            @NonNull StreamClaimOptions options) {
        String[] toArgsString = options.toArgs();
        GlideString[] toArgs =
                Arrays.stream(toArgsString).map(GlideString::gs).toArray(GlideString[]::new);
        GlideString[] args =
                concatenateArrays(
                        new GlideString[] {key, group, consumer, gs(Long.toString(minIdleTime))},
                        ids,
                        toArgs,
                        new GlideString[] {gs(JUST_ID_VALKEY_API)});
        return commandManager.submitNewCommand(
                XClaim,
                args,
                response -> castArray(handleArrayResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Map<String, Object>[]> xinfoGroups(@NonNull String key) {
        return commandManager.submitNewCommand(
                XInfoGroups,
                new String[] {key},
                response -> castArray(handleArrayResponse(response), Map.class));
    }

    @Override
    public CompletableFuture<Map<GlideString, Object>[]> xinfoGroups(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                XInfoGroups,
                new GlideString[] {key},
                response -> castArray(handleArrayResponseBinary(response), Map.class));
    }

    @Override
    public CompletableFuture<Map<String, Object>[]> xinfoConsumers(
            @NonNull String key, @NonNull String groupName) {
        return commandManager.submitNewCommand(
                XInfoConsumers,
                new String[] {key, groupName},
                response -> castArray(handleArrayResponse(response), Map.class));
    }

    @Override
    public CompletableFuture<Map<GlideString, Object>[]> xinfoConsumers(
            @NonNull GlideString key, @NonNull GlideString groupName) {
        return commandManager.submitNewCommand(
                XInfoConsumers,
                new GlideString[] {key, groupName},
                response -> castArray(handleArrayResponseBinary(response), Map.class));
    }

    @Override
    public CompletableFuture<Object[]> xautoclaim(
            @NonNull String key,
            @NonNull String group,
            @NonNull String consumer,
            long minIdleTime,
            @NonNull String start) {
        String[] args = new String[] {key, group, consumer, Long.toString(minIdleTime), start};
        return commandManager.submitNewCommand(XAutoClaim, args, this::handleArrayResponse);
    }

    @Override
    public CompletableFuture<Object[]> xautoclaim(
            @NonNull GlideString key,
            @NonNull GlideString group,
            @NonNull GlideString consumer,
            long minIdleTime,
            @NonNull GlideString start) {
        GlideString[] args =
                new GlideString[] {key, group, consumer, gs(Long.toString(minIdleTime)), start};
        return commandManager.submitNewCommand(XAutoClaim, args, this::handleArrayResponseBinary);
    }

    @Override
    public CompletableFuture<Object[]> xautoclaim(
            @NonNull String key,
            @NonNull String group,
            @NonNull String consumer,
            long minIdleTime,
            @NonNull String start,
            long count) {
        String[] args =
                new String[] {
                    key,
                    group,
                    consumer,
                    Long.toString(minIdleTime),
                    start,
                    READ_COUNT_VALKEY_API,
                    Long.toString(count)
                };
        return commandManager.submitNewCommand(XAutoClaim, args, this::handleArrayResponse);
    }

    @Override
    public CompletableFuture<Object[]> xautoclaim(
            @NonNull GlideString key,
            @NonNull GlideString group,
            @NonNull GlideString consumer,
            long minIdleTime,
            @NonNull GlideString start,
            long count) {
        GlideString[] args =
                new GlideString[] {
                    key,
                    group,
                    consumer,
                    gs(Long.toString(minIdleTime)),
                    start,
                    gs(READ_COUNT_VALKEY_API),
                    gs(Long.toString(count))
                };
        return commandManager.submitNewCommand(XAutoClaim, args, this::handleArrayResponseBinary);
    }

    @Override
    public CompletableFuture<Object[]> xautoclaimJustId(
            @NonNull String key,
            @NonNull String group,
            @NonNull String consumer,
            long minIdleTime,
            @NonNull String start) {
        String[] args =
                new String[] {key, group, consumer, Long.toString(minIdleTime), start, JUST_ID_VALKEY_API};
        return commandManager.submitNewCommand(XAutoClaim, args, this::handleArrayResponse);
    }

    @Override
    public CompletableFuture<Object[]> xautoclaimJustId(
            @NonNull GlideString key,
            @NonNull GlideString group,
            @NonNull GlideString consumer,
            long minIdleTime,
            @NonNull GlideString start) {
        GlideString[] args =
                new GlideString[] {
                    key, group, consumer, gs(Long.toString(minIdleTime)), start, gs(JUST_ID_VALKEY_API)
                };
        return commandManager.submitNewCommand(XAutoClaim, args, this::handleArrayResponseBinary);
    }

    @Override
    public CompletableFuture<Object[]> xautoclaimJustId(
            @NonNull String key,
            @NonNull String group,
            @NonNull String consumer,
            long minIdleTime,
            @NonNull String start,
            long count) {
        String[] args =
                new String[] {
                    key,
                    group,
                    consumer,
                    Long.toString(minIdleTime),
                    start,
                    READ_COUNT_VALKEY_API,
                    Long.toString(count),
                    JUST_ID_VALKEY_API
                };
        return commandManager.submitNewCommand(XAutoClaim, args, this::handleArrayResponse);
    }

    @Override
    public CompletableFuture<Object[]> xautoclaimJustId(
            @NonNull GlideString key,
            @NonNull GlideString group,
            @NonNull GlideString consumer,
            long minIdleTime,
            @NonNull GlideString start,
            long count) {
        GlideString[] args =
                new GlideString[] {
                    key,
                    group,
                    consumer,
                    gs(Long.toString(minIdleTime)),
                    start,
                    gs(READ_COUNT_VALKEY_API),
                    gs(Long.toString(count)),
                    gs(JUST_ID_VALKEY_API)
                };
        return commandManager.submitNewCommand(XAutoClaim, args, this::handleArrayResponseBinary);
    }

    @Override
    public CompletableFuture<Map<String, Object>> xinfoStream(@NonNull String key) {
        return commandManager.submitNewCommand(
                XInfoStream, new String[] {key}, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<String, Object>> xinfoStreamFull(@NonNull String key) {
        return commandManager.submitNewCommand(
                XInfoStream, new String[] {key, FULL}, response -> handleMapResponse(response));
    }

    @Override
    public CompletableFuture<Map<String, Object>> xinfoStreamFull(@NonNull String key, int count) {
        return commandManager.submitNewCommand(
                XInfoStream,
                new String[] {key, FULL, COUNT, Integer.toString(count)},
                this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<GlideString, Object>> xinfoStream(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                XInfoStream, new GlideString[] {key}, this::handleBinaryStringMapResponse);
    }

    @Override
    public CompletableFuture<Map<GlideString, Object>> xinfoStreamFull(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                XInfoStream, new GlideString[] {key, gs(FULL)}, this::handleBinaryStringMapResponse);
    }

    @Override
    public CompletableFuture<Map<GlideString, Object>> xinfoStreamFull(
            @NonNull GlideString key, int count) {
        return commandManager.submitNewCommand(
                XInfoStream,
                new GlideString[] {key, gs(FULL), gs(COUNT), gs(Integer.toString(count))},
                this::handleBinaryStringMapResponse);
    }

    @Override
    public CompletableFuture<Long> pttl(@NonNull String key) {
        return commandManager.submitNewCommand(PTTL, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> pttl(@NonNull GlideString key) {
        return commandManager.submitNewCommand(PTTL, new GlideString[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Boolean> persist(@NonNull String key) {
        return commandManager.submitNewCommand(
                Persist, new String[] {key}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> persist(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                Persist, new GlideString[] {key}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<String> type(@NonNull String key) {
        return commandManager.submitNewCommand(Type, new String[] {key}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> type(@NonNull GlideString key) {
        return commandManager.submitNewCommandWithResponseType(
                Type, new GlideString[] {key}, this::handleStringResponse, true);
    }

    @Override
    public CompletableFuture<Long> linsert(
            @NonNull String key,
            @NonNull InsertPosition position,
            @NonNull String pivot,
            @NonNull String element) {
        return commandManager.submitNewCommand(
                LInsert, new String[] {key, position.toString(), pivot, element}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> linsert(
            @NonNull GlideString key,
            @NonNull InsertPosition position,
            @NonNull GlideString pivot,
            @NonNull GlideString element) {
        return commandManager.submitNewCommand(
                LInsert,
                new GlideString[] {key, gs(position.toString()), pivot, element},
                this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String[]> blpop(@NonNull String[] keys, double timeout) {
        String[] arguments = ArrayUtils.add(keys, Double.toString(timeout));
        return commandManager.submitBlockingCommand(
                BLPop, arguments, response -> castArray(handleArrayOrNullResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> blpop(@NonNull GlideString[] keys, double timeout) {
        GlideString[] arguments = ArrayUtils.add(keys, gs(Double.toString(timeout)));
        return commandManager.submitBlockingCommand(
                BLPop,
                arguments,
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<String[]> brpop(@NonNull String[] keys, double timeout) {
        String[] arguments = ArrayUtils.add(keys, Double.toString(timeout));
        return commandManager.submitBlockingCommand(
                BRPop, arguments, response -> castArray(handleArrayOrNullResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> brpop(@NonNull GlideString[] keys, double timeout) {
        GlideString[] arguments = ArrayUtils.add(keys, gs(Double.toString(timeout)));
        return commandManager.submitBlockingCommand(
                BRPop,
                arguments,
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Long> rpushx(@NonNull String key, @NonNull String[] elements) {
        String[] arguments = ArrayUtils.addFirst(elements, key);
        return commandManager.submitNewCommand(RPushX, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> rpushx(@NonNull GlideString key, @NonNull GlideString[] elements) {
        GlideString[] arguments = ArrayUtils.addFirst(elements, key);
        return commandManager.submitNewCommand(RPushX, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> lpushx(@NonNull String key, @NonNull String[] elements) {
        String[] arguments = ArrayUtils.addFirst(elements, key);
        return commandManager.submitNewCommand(LPushX, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> lpushx(@NonNull GlideString key, @NonNull GlideString[] elements) {
        GlideString[] arguments = ArrayUtils.addFirst(elements, key);
        return commandManager.submitNewCommand(LPushX, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String[]> zrange(
            @NonNull String key, @NonNull RangeQuery rangeQuery, boolean reverse) {
        String[] arguments = RangeOptions.createZRangeArgs(key, rangeQuery, reverse, false);

        return commandManager.submitNewCommand(
                ZRange,
                arguments,
                response -> castArray(handleArrayOrNullResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> zrange(
            @NonNull GlideString key, @NonNull RangeQuery rangeQuery, boolean reverse) {
        GlideString[] arguments = RangeOptions.createZRangeArgsBinary(key, rangeQuery, reverse, false);

        return commandManager.submitNewCommand(
                ZRange,
                arguments,
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<String[]> zrange(@NonNull String key, @NonNull RangeQuery rangeQuery) {
        return zrange(key, rangeQuery, false);
    }

    @Override
    public CompletableFuture<GlideString[]> zrange(
            @NonNull GlideString key, @NonNull RangeQuery rangeQuery) {
        return zrange(key, rangeQuery, false);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zrangeWithScores(
            @NonNull String key, @NonNull ScoredRangeQuery rangeQuery, boolean reverse) {
        String[] arguments = RangeOptions.createZRangeArgs(key, rangeQuery, reverse, true);

        return commandManager.submitNewCommand(ZRange, arguments, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<GlideString, Double>> zrangeWithScores(
            @NonNull GlideString key, @NonNull ScoredRangeQuery rangeQuery, boolean reverse) {
        GlideString[] arguments = RangeOptions.createZRangeArgsBinary(key, rangeQuery, reverse, true);

        return commandManager.submitNewCommand(ZRange, arguments, this::handleBinaryStringMapResponse);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zrangeWithScores(
            @NonNull String key, @NonNull ScoredRangeQuery rangeQuery) {
        return zrangeWithScores(key, rangeQuery, false);
    }

    @Override
    public CompletableFuture<Map<GlideString, Double>> zrangeWithScores(
            @NonNull GlideString key, @NonNull ScoredRangeQuery rangeQuery) {
        return zrangeWithScores(key, rangeQuery, false);
    }

    @Override
    public CompletableFuture<Map<String, Object>> zmpop(
            @NonNull String[] keys, @NonNull ScoreFilter modifier) {
        String[] arguments =
                concatenateArrays(
                        new String[] {Integer.toString(keys.length)}, keys, new String[] {modifier.toString()});
        return commandManager.submitNewCommand(
                ZMPop,
                arguments,
                response -> convertKeyValueArrayToMap(handleArrayOrNullResponse(response), Double.class));
    }

    @Override
    public CompletableFuture<Map<GlideString, Object>> zmpop(
            @NonNull GlideString[] keys, @NonNull ScoreFilter modifier) {
        GlideString[] arguments =
                concatenateArrays(
                        new GlideString[] {gs(Integer.toString(keys.length))},
                        keys,
                        new GlideString[] {gs(modifier.toString())});
        return commandManager.submitNewCommand(
                ZMPop,
                arguments,
                response ->
                        convertBinaryStringKeyValueArrayToMap(
                                handleArrayOrNullResponseBinary(response), Double.class));
    }

    @Override
    public CompletableFuture<Map<String, Object>> zmpop(
            @NonNull String[] keys, @NonNull ScoreFilter modifier, long count) {
        String[] arguments =
                concatenateArrays(
                        new String[] {Integer.toString(keys.length)},
                        keys,
                        new String[] {modifier.toString(), COUNT_VALKEY_API, Long.toString(count)});
        return commandManager.submitNewCommand(
                ZMPop,
                arguments,
                response -> convertKeyValueArrayToMap(handleArrayOrNullResponse(response), Double.class));
    }

    @Override
    public CompletableFuture<Map<GlideString, Object>> zmpop(
            @NonNull GlideString[] keys, @NonNull ScoreFilter modifier, long count) {
        GlideString[] arguments =
                concatenateArrays(
                        new GlideString[] {gs(Integer.toString(keys.length))},
                        keys,
                        new GlideString[] {
                            gs(modifier.toString()), gs(COUNT_VALKEY_API), gs(Long.toString(count))
                        });
        return commandManager.submitNewCommand(
                ZMPop,
                arguments,
                response ->
                        convertBinaryStringKeyValueArrayToMap(
                                handleArrayOrNullResponseBinary(response), Double.class));
    }

    @Override
    public CompletableFuture<Map<String, Object>> bzmpop(
            @NonNull String[] keys, @NonNull ScoreFilter modifier, double timeout) {
        String[] arguments =
                concatenateArrays(
                        new String[] {Double.toString(timeout), Integer.toString(keys.length)},
                        keys,
                        new String[] {modifier.toString()});
        return commandManager.submitBlockingCommand(
                BZMPop,
                arguments,
                response -> convertKeyValueArrayToMap(handleArrayOrNullResponse(response), Double.class));
    }

    @Override
    public CompletableFuture<Map<GlideString, Object>> bzmpop(
            @NonNull GlideString[] keys, @NonNull ScoreFilter modifier, double timeout) {
        GlideString[] arguments =
                concatenateArrays(
                        new GlideString[] {gs(Double.toString(timeout)), gs(Integer.toString(keys.length))},
                        keys,
                        new GlideString[] {gs(modifier.toString())});
        return commandManager.submitBlockingCommand(
                BZMPop,
                arguments,
                response ->
                        convertBinaryStringKeyValueArrayToMap(
                                handleArrayOrNullResponseBinary(response), Double.class));
    }

    @Override
    public CompletableFuture<Map<String, Object>> bzmpop(
            @NonNull String[] keys, @NonNull ScoreFilter modifier, double timeout, long count) {
        String[] arguments =
                concatenateArrays(
                        new String[] {Double.toString(timeout), Integer.toString(keys.length)},
                        keys,
                        new String[] {modifier.toString(), COUNT_VALKEY_API, Long.toString(count)});
        return commandManager.submitBlockingCommand(
                BZMPop,
                arguments,
                response -> convertKeyValueArrayToMap(handleArrayOrNullResponse(response), Double.class));
    }

    @Override
    public CompletableFuture<Map<GlideString, Object>> bzmpop(
            @NonNull GlideString[] keys, @NonNull ScoreFilter modifier, double timeout, long count) {
        GlideString[] arguments =
                concatenateArrays(
                        new GlideString[] {gs(Double.toString(timeout)), gs(Integer.toString(keys.length))},
                        keys,
                        new GlideString[] {
                            gs(modifier.toString()), gs(COUNT_VALKEY_API), gs(Long.toString(count))
                        });
        return commandManager.submitBlockingCommand(
                BZMPop,
                arguments,
                response ->
                        convertBinaryStringKeyValueArrayToMap(
                                handleArrayOrNullResponseBinary(response), Double.class));
    }

    @Override
    public CompletableFuture<Boolean> pfadd(@NonNull String key, @NonNull String[] elements) {
        String[] arguments = ArrayUtils.addFirst(elements, key);
        return commandManager.submitNewCommand(PfAdd, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> pfadd(
            @NonNull GlideString key, @NonNull GlideString[] elements) {
        GlideString[] arguments = ArrayUtils.addFirst(elements, key);
        return commandManager.submitNewCommand(PfAdd, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Long> pfcount(@NonNull String[] keys) {
        return commandManager.submitNewCommand(PfCount, keys, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> pfcount(@NonNull GlideString[] keys) {
        return commandManager.submitNewCommand(PfCount, keys, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String> pfmerge(
            @NonNull String destination, @NonNull String[] sourceKeys) {
        String[] arguments = ArrayUtils.addFirst(sourceKeys, destination);
        return commandManager.submitNewCommand(PfMerge, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> pfmerge(
            @NonNull GlideString destination, @NonNull GlideString[] sourceKeys) {
        GlideString[] arguments = ArrayUtils.addFirst(sourceKeys, destination);
        return commandManager.submitNewCommand(PfMerge, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Long> touch(@NonNull String[] keys) {
        return commandManager.submitNewCommand(Touch, keys, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> touch(@NonNull GlideString[] keys) {
        return commandManager.submitNewCommand(Touch, keys, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> geoadd(
            @NonNull String key,
            @NonNull Map<String, GeospatialData> membersToGeospatialData,
            @NonNull GeoAddOptions options) {
        String[] arguments =
                concatenateArrays(
                        new String[] {key}, options.toArgs(), mapGeoDataToArray(membersToGeospatialData));
        return commandManager.submitNewCommand(GeoAdd, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> geoadd(
            @NonNull GlideString key,
            @NonNull Map<GlideString, GeospatialData> membersToGeospatialData,
            @NonNull GeoAddOptions options) {
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(key)
                        .add(options.toArgs())
                        .add(mapGeoDataToGlideStringArray(membersToGeospatialData))
                        .toArray();

        return commandManager.submitNewCommand(GeoAdd, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> geoadd(
            @NonNull String key, @NonNull Map<String, GeospatialData> membersToGeospatialData) {
        return geoadd(key, membersToGeospatialData, new GeoAddOptions(false));
    }

    @Override
    public CompletableFuture<Long> geoadd(
            @NonNull GlideString key, @NonNull Map<GlideString, GeospatialData> membersToGeospatialData) {
        return geoadd(key, membersToGeospatialData, new GeoAddOptions(false));
    }

    @Override
    public CompletableFuture<Double[][]> geopos(@NonNull String key, @NonNull String[] members) {
        String[] arguments = concatenateArrays(new String[] {key}, members);
        return commandManager.submitNewCommand(
                GeoPos,
                arguments,
                response -> castArrayofArrays(handleArrayResponse(response), Double.class));
    }

    @Override
    public CompletableFuture<Double[][]> geopos(
            @NonNull GlideString key, @NonNull GlideString[] members) {
        GlideString[] arguments = concatenateArrays(new GlideString[] {key}, members);
        return commandManager.submitNewCommand(
                GeoPos,
                arguments,
                response -> castArrayofArrays(handleArrayResponse(response), Double.class));
    }

    @Override
    public CompletableFuture<Double> geodist(
            @NonNull String key,
            @NonNull String member1,
            @NonNull String member2,
            @NonNull GeoUnit geoUnit) {
        String[] arguments = new String[] {key, member1, member2, geoUnit.getValkeyAPI()};
        return commandManager.submitNewCommand(GeoDist, arguments, this::handleDoubleOrNullResponse);
    }

    @Override
    public CompletableFuture<Double> geodist(
            @NonNull GlideString key,
            @NonNull GlideString member1,
            @NonNull GlideString member2,
            @NonNull GeoUnit geoUnit) {
        GlideString[] arguments = new GlideString[] {key, member1, member2, gs(geoUnit.getValkeyAPI())};
        return commandManager.submitNewCommand(GeoDist, arguments, this::handleDoubleOrNullResponse);
    }

    @Override
    public CompletableFuture<Double> geodist(
            @NonNull String key, @NonNull String member1, @NonNull String member2) {
        String[] arguments = new String[] {key, member1, member2};
        return commandManager.submitNewCommand(GeoDist, arguments, this::handleDoubleOrNullResponse);
    }

    @Override
    public CompletableFuture<Double> geodist(
            @NonNull GlideString key, @NonNull GlideString member1, @NonNull GlideString member2) {
        GlideString[] arguments = new GlideString[] {key, member1, member2};
        return commandManager.submitNewCommand(GeoDist, arguments, this::handleDoubleOrNullResponse);
    }

    @Override
    public CompletableFuture<String[]> geohash(@NonNull String key, @NonNull String[] members) {
        String[] arguments = concatenateArrays(new String[] {key}, members);
        return commandManager.submitNewCommand(
                GeoHash, arguments, response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> geohash(
            @NonNull GlideString key, @NonNull GlideString[] members) {
        GlideString[] arguments = concatenateArrays(new GlideString[] {key}, members);
        return commandManager.submitNewCommand(
                GeoHash,
                arguments,
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Long> bitcount(@NonNull String key) {
        return commandManager.submitNewCommand(BitCount, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> bitcount(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                BitCount, new GlideString[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> bitcount(@NonNull String key, long start) {
        return commandManager.submitNewCommand(
                BitCount, new String[] {key, Long.toString(start)}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> bitcount(@NonNull GlideString key, long start) {
        return commandManager.submitNewCommand(
                BitCount, new GlideString[] {key, gs(Long.toString(start))}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> bitcount(@NonNull String key, long start, long end) {
        return commandManager.submitNewCommand(
                BitCount,
                new String[] {key, Long.toString(start), Long.toString(end)},
                this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> bitcount(@NonNull GlideString key, long start, long end) {
        return commandManager.submitNewCommand(
                BitCount,
                new GlideString[] {key, gs(Long.toString(start)), gs(Long.toString(end))},
                this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> bitcount(
            @NonNull String key, long start, long end, @NonNull BitmapIndexType options) {
        String[] arguments =
                new String[] {key, Long.toString(start), Long.toString(end), options.toString()};
        return commandManager.submitNewCommand(BitCount, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> bitcount(
            @NonNull GlideString key, long start, long end, @NonNull BitmapIndexType options) {
        GlideString[] arguments =
                new GlideString[] {
                    key, gs(Long.toString(start)), gs(Long.toString(end)), gs(options.toString())
                };
        return commandManager.submitNewCommand(BitCount, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> setbit(@NonNull String key, long offset, long value) {
        String[] arguments = new String[] {key, Long.toString(offset), Long.toString(value)};
        return commandManager.submitNewCommand(SetBit, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> setbit(@NonNull GlideString key, long offset, long value) {
        GlideString[] arguments =
                new GlideString[] {key, gs(Long.toString(offset)), gs(Long.toString(value))};
        return commandManager.submitNewCommand(SetBit, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> getbit(@NonNull String key, long offset) {
        String[] arguments = new String[] {key, Long.toString(offset)};
        return commandManager.submitNewCommand(GetBit, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> getbit(@NonNull GlideString key, long offset) {
        GlideString[] arguments = new GlideString[] {key, gs(Long.toString(offset))};
        return commandManager.submitNewCommand(GetBit, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> bitpos(@NonNull String key, long bit) {
        String[] arguments = new String[] {key, Long.toString(bit)};
        return commandManager.submitNewCommand(BitPos, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> bitpos(@NonNull GlideString key, long bit) {
        GlideString[] arguments = new GlideString[] {key, gs(Long.toString(bit))};
        return commandManager.submitNewCommand(BitPos, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> bitpos(@NonNull String key, long bit, long start) {
        String[] arguments = new String[] {key, Long.toString(bit), Long.toString(start)};
        return commandManager.submitNewCommand(BitPos, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> bitpos(@NonNull GlideString key, long bit, long start) {
        GlideString[] arguments =
                new GlideString[] {key, gs(Long.toString(bit)), gs(Long.toString(start))};
        return commandManager.submitNewCommand(BitPos, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> bitpos(@NonNull String key, long bit, long start, long end) {
        String[] arguments =
                new String[] {key, Long.toString(bit), Long.toString(start), Long.toString(end)};
        return commandManager.submitNewCommand(BitPos, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> bitpos(@NonNull GlideString key, long bit, long start, long end) {
        GlideString[] arguments =
                new GlideString[] {
                    key, gs(Long.toString(bit)), gs(Long.toString(start)), gs(Long.toString(end))
                };
        return commandManager.submitNewCommand(BitPos, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> bitpos(
            @NonNull String key, long bit, long start, long end, @NonNull BitmapIndexType options) {
        String[] arguments =
                new String[] {
                    key, Long.toString(bit), Long.toString(start), Long.toString(end), options.toString()
                };
        return commandManager.submitNewCommand(BitPos, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> bitpos(
            @NonNull GlideString key, long bit, long start, long end, @NonNull BitmapIndexType options) {
        GlideString[] arguments =
                new GlideString[] {
                    key,
                    gs(Long.toString(bit)),
                    gs(Long.toString(start)),
                    gs(Long.toString(end)),
                    gs(options.toString())
                };
        return commandManager.submitNewCommand(BitPos, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> bitop(
            @NonNull BitwiseOperation bitwiseOperation,
            @NonNull String destination,
            @NonNull String[] keys) {
        String[] arguments =
                concatenateArrays(new String[] {bitwiseOperation.toString(), destination}, keys);
        return commandManager.submitNewCommand(BitOp, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> bitop(
            @NonNull BitwiseOperation bitwiseOperation,
            @NonNull GlideString destination,
            @NonNull GlideString[] keys) {
        GlideString[] arguments =
                concatenateArrays(new GlideString[] {gs(bitwiseOperation.toString()), destination}, keys);
        return commandManager.submitNewCommand(BitOp, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Map<String, String[]>> lmpop(
            @NonNull String[] keys, @NonNull ListDirection direction, long count) {
        String[] arguments =
                concatenateArrays(
                        new String[] {Long.toString(keys.length)},
                        keys,
                        new String[] {direction.toString(), COUNT_FOR_LIST_VALKEY_API, Long.toString(count)});
        return commandManager.submitNewCommand(
                LMPop,
                arguments,
                response -> castMapOfArrays(handleMapOrNullResponse(response), String.class));
    }

    @Override
    public CompletableFuture<Map<GlideString, GlideString[]>> lmpop(
            @NonNull GlideString[] keys, @NonNull ListDirection direction, long count) {
        GlideString[] arguments =
                concatenateArrays(
                        new GlideString[] {gs(Long.toString(keys.length))},
                        keys,
                        new GlideString[] {
                            gs(direction.toString()), gs(COUNT_FOR_LIST_VALKEY_API), gs(Long.toString(count))
                        });
        return commandManager.submitNewCommand(
                LMPop,
                arguments,
                response ->
                        castBinaryStringMapOfArrays(
                                handleBinaryStringMapOrNullResponse(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Map<String, String[]>> lmpop(
            @NonNull String[] keys, @NonNull ListDirection direction) {
        String[] arguments =
                concatenateArrays(
                        new String[] {Long.toString(keys.length)}, keys, new String[] {direction.toString()});
        return commandManager.submitNewCommand(
                LMPop,
                arguments,
                response -> castMapOfArrays(handleMapOrNullResponse(response), String.class));
    }

    @Override
    public CompletableFuture<Map<GlideString, GlideString[]>> lmpop(
            @NonNull GlideString[] keys, @NonNull ListDirection direction) {
        GlideString[] arguments =
                concatenateArrays(
                        new GlideString[] {gs(Long.toString(keys.length))},
                        keys,
                        new GlideString[] {gs(direction.toString())});
        return commandManager.submitNewCommand(
                LMPop,
                arguments,
                response ->
                        castBinaryStringMapOfArrays(
                                handleBinaryStringMapOrNullResponse(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Map<String, String[]>> blmpop(
            @NonNull String[] keys, @NonNull ListDirection direction, long count, double timeout) {
        String[] arguments =
                concatenateArrays(
                        new String[] {Double.toString(timeout), Long.toString(keys.length)},
                        keys,
                        new String[] {direction.toString(), COUNT_FOR_LIST_VALKEY_API, Long.toString(count)});
        return commandManager.submitBlockingCommand(
                BLMPop,
                arguments,
                response -> castMapOfArrays(handleMapOrNullResponse(response), String.class));
    }

    @Override
    public CompletableFuture<Map<GlideString, GlideString[]>> blmpop(
            @NonNull GlideString[] keys, @NonNull ListDirection direction, long count, double timeout) {
        GlideString[] arguments =
                concatenateArrays(
                        new GlideString[] {gs(Double.toString(timeout)), gs(Long.toString(keys.length))},
                        keys,
                        new GlideString[] {
                            gs(direction.toString()), gs(COUNT_FOR_LIST_VALKEY_API), gs(Long.toString(count))
                        });
        return commandManager.submitBlockingCommand(
                BLMPop,
                arguments,
                response ->
                        castBinaryStringMapOfArrays(
                                handleBinaryStringMapOrNullResponse(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Map<String, String[]>> blmpop(
            @NonNull String[] keys, @NonNull ListDirection direction, double timeout) {
        String[] arguments =
                concatenateArrays(
                        new String[] {Double.toString(timeout), Long.toString(keys.length)},
                        keys,
                        new String[] {direction.toString()});
        return commandManager.submitBlockingCommand(
                BLMPop,
                arguments,
                response -> castMapOfArrays(handleMapOrNullResponse(response), String.class));
    }

    @Override
    public CompletableFuture<Map<GlideString, GlideString[]>> blmpop(
            @NonNull GlideString[] keys, @NonNull ListDirection direction, double timeout) {
        GlideString[] arguments =
                concatenateArrays(
                        new GlideString[] {gs(Double.toString(timeout)), gs(Long.toString(keys.length))},
                        keys,
                        new GlideString[] {gs(direction.toString())});
        return commandManager.submitBlockingCommand(
                BLMPop,
                arguments,
                response ->
                        castBinaryStringMapOfArrays(
                                handleBinaryStringMapOrNullResponse(response), GlideString.class));
    }

    @Override
    public CompletableFuture<String> lset(@NonNull String key, long index, @NonNull String element) {
        String[] arguments = new String[] {key, Long.toString(index), element};
        return commandManager.submitNewCommand(LSet, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> lset(
            @NonNull GlideString key, long index, @NonNull GlideString element) {
        GlideString[] arguments = new GlideString[] {key, gs(Long.toString(index)), element};
        return commandManager.submitNewCommand(LSet, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> lmove(
            @NonNull String source,
            @NonNull String destination,
            @NonNull ListDirection wherefrom,
            @NonNull ListDirection whereto) {
        String[] arguments =
                new String[] {source, destination, wherefrom.toString(), whereto.toString()};
        return commandManager.submitNewCommand(LMove, arguments, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<GlideString> lmove(
            @NonNull GlideString source,
            @NonNull GlideString destination,
            @NonNull ListDirection wherefrom,
            @NonNull ListDirection whereto) {
        GlideString[] arguments =
                new GlideString[] {source, destination, gs(wherefrom.toString()), gs(whereto.toString())};
        return commandManager.submitNewCommand(LMove, arguments, this::handleGlideStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String> blmove(
            @NonNull String source,
            @NonNull String destination,
            @NonNull ListDirection wherefrom,
            @NonNull ListDirection whereto,
            double timeout) {
        String[] arguments =
                new String[] {
                    source, destination, wherefrom.toString(), whereto.toString(), Double.toString(timeout)
                };
        return commandManager.submitBlockingCommand(
                BLMove, arguments, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<GlideString> blmove(
            @NonNull GlideString source,
            @NonNull GlideString destination,
            @NonNull ListDirection wherefrom,
            @NonNull ListDirection whereto,
            double timeout) {
        GlideString[] arguments =
                new GlideString[] {
                    source,
                    destination,
                    gs(wherefrom.toString()),
                    gs(whereto.toString()),
                    gs(Double.toString(timeout))
                };
        return commandManager.submitBlockingCommand(
                BLMove, arguments, this::handleGlideStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String> srandmember(@NonNull String key) {
        String[] arguments = new String[] {key};
        return commandManager.submitNewCommand(
                SRandMember, arguments, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<GlideString> srandmember(@NonNull GlideString key) {
        GlideString[] arguments = new GlideString[] {key};
        return commandManager.submitNewCommand(
                SRandMember, arguments, this::handleGlideStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String[]> srandmember(@NonNull String key, long count) {
        String[] arguments = new String[] {key, Long.toString(count)};
        return commandManager.submitNewCommand(
                SRandMember, arguments, response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> srandmember(@NonNull GlideString key, long count) {
        GlideString[] arguments = new GlideString[] {key, gs(Long.toString(count))};
        return commandManager.submitNewCommand(
                SRandMember,
                arguments,
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<String> spop(@NonNull String key) {
        String[] arguments = new String[] {key};
        return commandManager.submitNewCommand(SPop, arguments, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<GlideString> spop(@NonNull GlideString key) {
        GlideString[] arguments = new GlideString[] {key};
        return commandManager.submitNewCommand(SPop, arguments, this::handleGlideStringOrNullResponse);
    }

    @Override
    public CompletableFuture<Set<String>> spopCount(@NonNull String key, long count) {
        String[] arguments = new String[] {key, Long.toString(count)};
        return commandManager.submitNewCommand(SPop, arguments, this::handleSetResponse);
    }

    @Override
    public CompletableFuture<Set<GlideString>> spopCount(@NonNull GlideString key, long count) {
        GlideString[] arguments = new GlideString[] {key, gs(Long.toString(count))};
        return commandManager.submitNewCommand(SPop, arguments, this::handleSetBinaryResponse);
    }

    @Override
    public CompletableFuture<Long[]> bitfield(
            @NonNull String key, @NonNull BitFieldSubCommands[] subCommands) {
        String[] arguments = ArrayUtils.addFirst(createBitFieldArgs(subCommands), key);
        return commandManager.submitNewCommand(
                BitField, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> bitfield(
            @NonNull GlideString key, @NonNull BitFieldSubCommands[] subCommands) {
        GlideString[] arguments = ArrayUtils.addFirst(createBitFieldGlideStringArgs(subCommands), key);
        return commandManager.submitNewCommand(
                BitField, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> bitfieldReadOnly(
            @NonNull String key, @NonNull BitFieldReadOnlySubCommands[] subCommands) {
        String[] arguments = ArrayUtils.addFirst(createBitFieldArgs(subCommands), key);
        return commandManager.submitNewCommand(
                BitFieldReadOnly,
                arguments,
                response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> bitfieldReadOnly(
            @NonNull GlideString key, @NonNull BitFieldReadOnlySubCommands[] subCommands) {
        GlideString[] arguments = ArrayUtils.addFirst(createBitFieldGlideStringArgs(subCommands), key);
        return commandManager.submitNewCommand(
                BitFieldReadOnly,
                arguments,
                response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long> sintercard(@NonNull String[] keys) {
        String[] arguments = ArrayUtils.addFirst(keys, Long.toString(keys.length));
        return commandManager.submitNewCommand(SInterCard, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> sintercard(@NonNull GlideString[] keys) {
        GlideString[] arguments = new ArgsBuilder().add(keys.length).add(keys).toArray();
        return commandManager.submitNewCommand(SInterCard, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> sintercard(@NonNull String[] keys, long limit) {
        String[] arguments =
                concatenateArrays(
                        new String[] {Long.toString(keys.length)},
                        keys,
                        new String[] {SET_LIMIT_VALKEY_API, Long.toString(limit)});
        return commandManager.submitNewCommand(SInterCard, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> sintercard(@NonNull GlideString[] keys, long limit) {
        GlideString[] arguments =
                concatenateArrays(
                        new GlideString[] {gs(Long.toString(keys.length))},
                        keys,
                        new GlideString[] {gs(SET_LIMIT_VALKEY_API), gs(Long.toString(limit))});
        return commandManager.submitNewCommand(SInterCard, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Object> fcall(
            @NonNull String function, @NonNull String[] keys, @NonNull String[] arguments) {
        String[] args =
                concatenateArrays(new String[] {function, Long.toString(keys.length)}, keys, arguments);
        return commandManager.submitNewCommand(FCall, args, this::handleObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<Object> fcall(
            @NonNull GlideString function,
            @NonNull GlideString[] keys,
            @NonNull GlideString[] arguments) {
        GlideString[] args =
                concatenateArrays(
                        new GlideString[] {function, gs(Long.toString(keys.length))}, keys, arguments);
        return commandManager.submitNewCommand(FCall, args, this::handleBinaryObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<Object> fcallReadOnly(
            @NonNull String function, @NonNull String[] keys, @NonNull String[] arguments) {
        String[] args =
                concatenateArrays(new String[] {function, Long.toString(keys.length)}, keys, arguments);
        return commandManager.submitNewCommand(FCallReadOnly, args, this::handleObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<Object> fcallReadOnly(
            @NonNull GlideString function,
            @NonNull GlideString[] keys,
            @NonNull GlideString[] arguments) {
        GlideString[] args =
                concatenateArrays(
                        new GlideString[] {function, gs(Long.toString(keys.length))}, keys, arguments);
        return commandManager.submitNewCommand(
                FCallReadOnly, args, this::handleBinaryObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<Object> evalReadOnly(@NonNull String script) {
        return evalReadOnly(script, EMPTY_STRING_ARRAY, EMPTY_STRING_ARRAY);
    }

    @Override
    public CompletableFuture<Object> evalReadOnly(
            @NonNull String script, @NonNull String[] keys, @NonNull String[] args) {
        String[] arguments =
                concatenateArrays(new String[] {script, String.valueOf(keys.length)}, keys, args);
        return commandManager.submitNewCommand(
                EvalReadOnly, arguments, this::handleObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<Object> evalReadOnly(@NonNull GlideString script) {
        return evalReadOnly(script, EMPTY_GLIDE_STRING_ARRAY, EMPTY_GLIDE_STRING_ARRAY);
    }

    @Override
    public CompletableFuture<Object> evalReadOnly(
            @NonNull GlideString script, @NonNull GlideString[] keys, @NonNull GlideString[] args) {
        GlideString[] arguments =
                concatenateArrays(new GlideString[] {script, gs(String.valueOf(keys.length))}, keys, args);
        return commandManager.submitNewCommand(
                EvalReadOnly, arguments, this::handleBinaryObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<Object> evalshaReadOnly(@NonNull String sha1) {
        return evalshaReadOnly(sha1, EMPTY_STRING_ARRAY, EMPTY_STRING_ARRAY);
    }

    @Override
    public CompletableFuture<Object> evalshaReadOnly(
            @NonNull String sha1, @NonNull String[] keys, @NonNull String[] args) {
        String[] arguments =
                concatenateArrays(new String[] {sha1, String.valueOf(keys.length)}, keys, args);
        return commandManager.submitNewCommand(
                EvalShaReadOnly, arguments, this::handleObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<Object> evalshaReadOnly(@NonNull GlideString sha1) {
        return evalshaReadOnly(sha1, EMPTY_GLIDE_STRING_ARRAY, EMPTY_GLIDE_STRING_ARRAY);
    }

    @Override
    public CompletableFuture<Object> evalshaReadOnly(
            @NonNull GlideString sha1, @NonNull GlideString[] keys, @NonNull GlideString[] args) {
        GlideString[] arguments =
                concatenateArrays(new GlideString[] {sha1, gs(String.valueOf(keys.length))}, keys, args);
        return commandManager.submitNewCommand(
                EvalShaReadOnly, arguments, this::handleBinaryObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<String> scriptDebug(@NonNull ScriptDebugMode mode) {
        return commandManager.submitNewCommand(
                ScriptDebug, new String[] {mode.name()}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Boolean> copy(
            @NonNull String source, @NonNull String destination, boolean replace) {
        String[] arguments = new String[] {source, destination};
        if (replace) {
            arguments = ArrayUtils.add(arguments, REPLACE_VALKEY_API);
        }
        return commandManager.submitNewCommand(Copy, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> copy(
            @NonNull GlideString source, @NonNull GlideString destination, boolean replace) {
        GlideString[] arguments = new GlideString[] {source, destination};
        if (replace) {
            arguments = ArrayUtils.add(arguments, gs(REPLACE_VALKEY_API));
        }
        return commandManager.submitNewCommand(Copy, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> copy(@NonNull String source, @NonNull String destination) {
        String[] arguments = new String[] {source, destination};
        return commandManager.submitNewCommand(Copy, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> copy(
            @NonNull GlideString source, @NonNull GlideString destination) {
        GlideString[] arguments = new GlideString[] {source, destination};
        return commandManager.submitNewCommand(Copy, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> copy(
            @NonNull String source, @NonNull String destination, long destinationDB) {
        String[] arguments =
                new String[] {source, destination, DB_VALKEY_API, Long.toString(destinationDB)};
        return commandManager.submitNewCommand(Copy, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> copy(
            @NonNull GlideString source, @NonNull GlideString destination, long destinationDB) {
        GlideString[] arguments =
                new GlideString[] {
                    source, destination, gs(DB_VALKEY_API), gs(Long.toString(destinationDB))
                };
        return commandManager.submitNewCommand(Copy, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> copy(
            @NonNull String source, @NonNull String destination, long destinationDB, boolean replace) {
        String[] arguments =
                new String[] {source, destination, DB_VALKEY_API, Long.toString(destinationDB)};
        if (replace) {
            arguments = ArrayUtils.add(arguments, REPLACE_VALKEY_API);
        }
        return commandManager.submitNewCommand(Copy, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> copy(
            @NonNull GlideString source,
            @NonNull GlideString destination,
            long destinationDB,
            boolean replace) {
        GlideString[] arguments =
                new GlideString[] {
                    source, destination, gs(DB_VALKEY_API), gs(Long.toString(destinationDB))
                };
        if (replace) {
            arguments = ArrayUtils.add(arguments, gs(REPLACE_VALKEY_API));
        }
        return commandManager.submitNewCommand(Copy, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> msetnx(@NonNull Map<String, String> keyValueMap) {
        String[] args = convertMapToKeyValueStringArray(keyValueMap);
        return commandManager.submitNewCommand(MSetNX, args, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> msetnxBinary(
            @NonNull Map<GlideString, GlideString> keyValueMap) {
        GlideString[] args = convertMapToKeyValueGlideStringArray(keyValueMap);
        return commandManager.submitNewCommand(MSetNX, args, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<String> lcs(@NonNull String key1, @NonNull String key2) {
        String[] arguments = new String[] {key1, key2};
        return commandManager.submitNewCommand(LCS, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<GlideString> lcs(@NonNull GlideString key1, @NonNull GlideString key2) {
        GlideString[] arguments = new GlideString[] {key1, key2};
        return commandManager.submitNewCommand(LCS, arguments, this::handleGlideStringResponse);
    }

    @Override
    public CompletableFuture<Long> lcsLen(@NonNull String key1, @NonNull String key2) {
        String[] arguments = new String[] {key1, key2, LEN_VALKEY_API};
        return commandManager.submitNewCommand(LCS, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> lcsLen(@NonNull GlideString key1, @NonNull GlideString key2) {
        GlideString[] arguments = new ArgsBuilder().add(key1).add(key2).add(LEN_VALKEY_API).toArray();
        return commandManager.submitNewCommand(LCS, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Map<String, Object>> lcsIdx(@NonNull String key1, @NonNull String key2) {
        String[] arguments = new String[] {key1, key2, IDX_COMMAND_STRING};
        return commandManager.submitNewCommand(
                LCS, arguments, response -> handleLcsIdxResponse(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<Map<String, Object>> lcsIdx(
            @NonNull GlideString key1, @NonNull GlideString key2) {
        GlideString[] arguments =
                new ArgsBuilder().add(key1).add(key2).add(IDX_COMMAND_STRING).toArray();

        return commandManager.submitNewCommand(
                LCS,
                arguments,
                response ->
                        handleLcsIdxResponse(convertBinaryLcsMap(handleBinaryStringMapResponse(response))));
    }

    @Override
    public CompletableFuture<Map<String, Object>> lcsIdx(
            @NonNull String key1, @NonNull String key2, long minMatchLen) {
        String[] arguments =
                new String[] {
                    key1, key2, IDX_COMMAND_STRING, MINMATCHLEN_COMMAND_STRING, String.valueOf(minMatchLen)
                };
        return commandManager.submitNewCommand(
                LCS, arguments, response -> handleLcsIdxResponse(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<Map<String, Object>> lcsIdx(
            @NonNull GlideString key1, @NonNull GlideString key2, long minMatchLen) {
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(key1)
                        .add(key2)
                        .add(IDX_COMMAND_STRING)
                        .add(MINMATCHLEN_COMMAND_STRING)
                        .add(minMatchLen)
                        .toArray();
        return commandManager.submitNewCommand(
                LCS,
                arguments,
                response ->
                        handleLcsIdxResponse(convertBinaryLcsMap(handleBinaryStringMapResponse(response))));
    }

    @Override
    public CompletableFuture<Map<String, Object>> lcsIdxWithMatchLen(
            @NonNull String key1, @NonNull String key2) {
        String[] arguments = new String[] {key1, key2, IDX_COMMAND_STRING, WITHMATCHLEN_COMMAND_STRING};
        return commandManager.submitNewCommand(LCS, arguments, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<String, Object>> lcsIdxWithMatchLen(
            @NonNull GlideString key1, @NonNull GlideString key2) {
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(key1)
                        .add(key2)
                        .add(IDX_COMMAND_STRING)
                        .add(WITHMATCHLEN_COMMAND_STRING)
                        .toArray();
        return commandManager.submitNewCommand(
                LCS, arguments, response -> convertBinaryLcsMap(handleBinaryStringMapResponse(response)));
    }

    @Override
    public CompletableFuture<Map<String, Object>> lcsIdxWithMatchLen(
            @NonNull String key1, @NonNull String key2, long minMatchLen) {
        String[] arguments =
                concatenateArrays(
                        new String[] {
                            key1,
                            key2,
                            IDX_COMMAND_STRING,
                            MINMATCHLEN_COMMAND_STRING,
                            String.valueOf(minMatchLen),
                            WITHMATCHLEN_COMMAND_STRING
                        });
        return commandManager.submitNewCommand(LCS, arguments, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<String, Object>> lcsIdxWithMatchLen(
            @NonNull GlideString key1, @NonNull GlideString key2, long minMatchLen) {
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(key1)
                        .add(key2)
                        .add(IDX_COMMAND_STRING)
                        .add(MINMATCHLEN_COMMAND_STRING)
                        .add(minMatchLen)
                        .add(WITHMATCHLEN_COMMAND_STRING)
                        .toArray();
        return commandManager.submitNewCommand(
                LCS, arguments, response -> convertBinaryLcsMap(handleBinaryStringMapResponse(response)));
    }

    @Override
    public CompletableFuture<String> publish(@NonNull String message, @NonNull String channel) {
        return commandManager.submitNewCommand(
                Publish,
                new String[] {channel, message},
                response -> {
                    // Check, but ignore the number - it is never valid. A GLIDE bug/limitation TODO
                    handleLongResponse(response);
                    return OK;
                });
    }

    @Override
    public CompletableFuture<String> publish(
            @NonNull GlideString message, @NonNull GlideString channel) {
        return commandManager.submitNewCommand(
                Publish,
                new GlideString[] {channel, message},
                response -> {
                    // Check, but ignore the number - it is never valid. A GLIDE bug/limitation TODO
                    handleLongResponse(response);
                    return OK;
                });
    }

    @Override
    public CompletableFuture<String[]> pubsubChannels() {
        return commandManager.submitNewCommand(
                PubSubChannels,
                EMPTY_STRING_ARRAY,
                response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> pubsubChannelsBinary() {
        return commandManager.submitNewCommand(
                PubSubChannels,
                EMPTY_GLIDE_STRING_ARRAY,
                response -> castArray(handleArrayResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<String[]> pubsubChannels(@NonNull String pattern) {
        return commandManager.submitNewCommand(
                PubSubChannels,
                new String[] {pattern},
                response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> pubsubChannels(@NonNull GlideString pattern) {
        return commandManager.submitNewCommand(
                PubSubChannels,
                new GlideString[] {pattern},
                response -> castArray(handleArrayResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Long> pubsubNumPat() {
        return commandManager.submitNewCommand(
                PubSubNumPat, EMPTY_STRING_ARRAY, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Map<String, Long>> pubsubNumSub(@NonNull String[] channels) {
        return commandManager.submitNewCommand(PubSubNumSub, channels, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<GlideString, Long>> pubsubNumSub(@NonNull GlideString[] channels) {
        return commandManager.submitNewCommand(
                PubSubNumSub, channels, this::handleBinaryStringMapResponse);
    }

    @Override
    public CompletableFuture<String> watch(@NonNull String[] keys) {
        return commandManager.submitNewCommand(Watch, keys, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> watch(@NonNull GlideString[] keys) {
        return commandManager.submitNewCommand(Watch, keys, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Set<String>> sunion(@NonNull String[] keys) {
        return commandManager.submitNewCommand(SUnion, keys, this::handleSetResponse);
    }

    @Override
    public CompletableFuture<Set<GlideString>> sunion(@NonNull GlideString[] keys) {
        return commandManager.submitNewCommand(SUnion, keys, this::handleSetBinaryResponse);
    }

    // Hack: convert all `byte[]` -> `GlideString`. Better doing it here in the Java realm
    // rather than doing it in the Rust code using native calls (performance)
    private Object convertByteArrayToGlideString(Object o) {
        if (o == null) return o;

        if (o instanceof byte[]) {
            o = GlideString.of((byte[]) o);
        } else if (o.getClass().isArray()) {
            Object[] array = (Object[]) o;
            for (int i = 0; i < array.length; i++) {
                array[i] = convertByteArrayToGlideString(array[i]);
            }
        } else if (o instanceof Set) {
            Set<?> set = (Set<?>) o;
            o = set.stream().map(this::convertByteArrayToGlideString).collect(Collectors.toSet());
        } else if (o instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) o;
            o =
                    map.entrySet().stream()
                            .collect(
                                    LinkedHashMap::new,
                                    (m, e) ->
                                            m.put(
                                                    convertByteArrayToGlideString(e.getKey()),
                                                    convertByteArrayToGlideString(e.getValue())),
                                    LinkedHashMap::putAll);
        }
        return o;
    }

    private byte[] copyBufferToArray(java.nio.ByteBuffer buffer) {
        java.nio.ByteBuffer duplicate = buffer.duplicate();
        duplicate.rewind();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }

    private Map<?, ?> deserializeMapFromBuffer(java.nio.ByteBuffer buffer, boolean encodingUtf8) {
        if (buffer == null) {
            return new LinkedHashMap<>();
        }

        java.nio.ByteBuffer workingBuffer = buffer.duplicate();
        workingBuffer.rewind();
        workingBuffer = workingBuffer.asReadOnlyBuffer();
        workingBuffer.order(java.nio.ByteOrder.BIG_ENDIAN);

        try {
            if (!workingBuffer.hasRemaining()) {
                return new LinkedHashMap<>();
            }

            byte marker = workingBuffer.get();
            if (marker != '%') {
                // Older buffers may omit the '%' prefix; rewind and fall back to entry-count first.
                workingBuffer.position(workingBuffer.position() - 1);
            }

            if (workingBuffer.remaining() < Integer.BYTES) {
                Logger.log(
                        Logger.Level.ERROR,
                        "BaseClient",
                        () -> "DirectByteBuffer map payload missing entry count");
                return new LinkedHashMap<>();
            }

            int entryCount = workingBuffer.getInt();
            Map<Object, Object> map = new LinkedHashMap<>(Math.max(16, entryCount));

            for (int i = 0; i < entryCount; i++) {
                if (workingBuffer.remaining() < Integer.BYTES) {
                    Logger.log(
                            Logger.Level.ERROR,
                            "BaseClient",
                            () -> "DirectByteBuffer map payload truncated before key length");
                    return new LinkedHashMap<>();
                }

                int keyLen = workingBuffer.getInt();
                if (keyLen < 0 || keyLen > workingBuffer.remaining()) {
                    Logger.log(
                            Logger.Level.ERROR,
                            "BaseClient",
                            () -> "Invalid key length in DirectByteBuffer map: " + keyLen);
                    return new LinkedHashMap<>();
                }

                Object key;
                if (encodingUtf8) {
                    // Decode UTF-8 directly from buffer
                    key = BufferUtils.decodeUtf8(workingBuffer, keyLen);
                } else {
                    // For binary mode, we need the byte array
                    byte[] keyBytes = new byte[keyLen];
                    workingBuffer.get(keyBytes);
                    key = GlideString.of(keyBytes);
                }

                if (workingBuffer.remaining() < Integer.BYTES) {
                    Logger.log(
                            Logger.Level.ERROR,
                            "BaseClient",
                            () -> "DirectByteBuffer map payload truncated before value length");
                    return new LinkedHashMap<>();
                }

                int valueLen = workingBuffer.getInt();

                if (valueLen < 0) {
                    map.put(key, null);
                    continue;
                }

                if (valueLen > workingBuffer.remaining()) {
                    Logger.log(
                            Logger.Level.ERROR,
                            "BaseClient",
                            () -> "Invalid value length in DirectByteBuffer map: " + valueLen);
                    return new LinkedHashMap<>();
                }

                Object value;
                if (encodingUtf8) {
                    // Decode UTF-8 directly from buffer
                    value = BufferUtils.decodeUtf8(workingBuffer, valueLen);
                } else {
                    // For binary mode, we need the byte array
                    byte[] valueBytes = new byte[valueLen];
                    workingBuffer.get(valueBytes);
                    value = GlideString.of(valueBytes);
                }
                map.put(key, value);
            }

            return map;
        } catch (Exception e) {
            Logger.log(
                    Logger.Level.ERROR,
                    "BaseClient",
                    () -> "Error deserializing Map from DirectByteBuffer",
                    e);
            return new LinkedHashMap<>();
        }
    }

    @Override
    public CompletableFuture<byte[]> dump(@NonNull GlideString key) {
        GlideString[] arguments = new GlideString[] {key};
        return commandManager.submitNewCommand(Dump, arguments, this::handleBytesOrNullResponse);
    }

    @Override
    public CompletableFuture<String> restore(
            @NonNull GlideString key, long ttl, @NonNull byte[] value) {
        GlideString[] arguments = new GlideString[] {key, gs(Long.toString(ttl)), gs(value)};
        return commandManager.submitNewCommand(Restore, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> restore(
            @NonNull GlideString key,
            long ttl,
            @NonNull byte[] value,
            @NonNull RestoreOptions restoreOptions) {
        GlideString[] arguments =
                concatenateArrays(
                        new GlideString[] {key, gs(Long.toString(ttl)), gs(value)}, restoreOptions.toArgs());
        return commandManager.submitNewCommand(Restore, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String[]> sort(@NonNull String key) {
        return commandManager.submitNewCommand(
                Sort,
                new String[] {key},
                response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> sort(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                Sort,
                new GlideString[] {key},
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<String[]> sort(@NonNull String key, @NonNull SortOptions sortOptions) {
        String[] arguments = ArrayUtils.addFirst(sortOptions.toArgs(), key);
        return commandManager.submitNewCommand(
                Sort, arguments, response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> sort(
            @NonNull GlideString key, @NonNull SortOptionsBinary sortOptions) {
        GlideString[] arguments = new ArgsBuilder().add(key).add(sortOptions.toArgs()).toArray();
        return commandManager.submitNewCommand(
                Sort,
                arguments,
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<String[]> sortReadOnly(@NonNull String key) {
        return commandManager.submitNewCommand(
                SortReadOnly,
                new String[] {key},
                response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> sortReadOnly(@NonNull GlideString key) {
        return commandManager.submitNewCommand(
                SortReadOnly,
                new GlideString[] {key},
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<String[]> sortReadOnly(
            @NonNull String key, @NonNull SortOptions sortOptions) {
        String[] arguments = ArrayUtils.addFirst(sortOptions.toArgs(), key);
        return commandManager.submitNewCommand(
                SortReadOnly,
                arguments,
                response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> sortReadOnly(
            @NonNull GlideString key, @NonNull SortOptionsBinary sortOptions) {
        GlideString[] arguments = new ArgsBuilder().add(key).add(sortOptions.toArgs()).toArray();

        return commandManager.submitNewCommand(
                SortReadOnly,
                arguments,
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Long> sortStore(@NonNull String key, @NonNull String destination) {
        return commandManager.submitNewCommand(
                Sort, new String[] {key, STORE_COMMAND_STRING, destination}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> sortStore(
            @NonNull GlideString key, @NonNull GlideString destination) {
        return commandManager.submitNewCommand(
                Sort,
                new GlideString[] {key, gs(STORE_COMMAND_STRING), destination},
                this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> sortStore(
            @NonNull String key, @NonNull String destination, @NonNull SortOptions sortOptions) {
        String[] storeArguments = new String[] {STORE_COMMAND_STRING, destination};
        String[] arguments =
                concatenateArrays(new String[] {key}, sortOptions.toArgs(), storeArguments);
        return commandManager.submitNewCommand(Sort, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> sortStore(
            @NonNull GlideString key,
            @NonNull GlideString destination,
            @NonNull SortOptionsBinary sortOptions) {

        GlideString[] arguments =
                new ArgsBuilder()
                        .add(key)
                        .add(sortOptions.toArgs())
                        .add(STORE_COMMAND_STRING)
                        .add(destination)
                        .toArray();

        return commandManager.submitNewCommand(Sort, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String[]> geosearch(
            @NonNull String key,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy) {
        String[] arguments =
                concatenateArrays(new String[] {key}, searchFrom.toArgs(), searchBy.toArgs());
        return commandManager.submitNewCommand(
                GeoSearch, arguments, response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> geosearch(
            @NonNull GlideString key,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy) {
        GlideString[] arguments =
                new ArgsBuilder().add(key).add(searchFrom.toArgs()).add(searchBy.toArgs()).toArray();

        return commandManager.submitNewCommand(
                GeoSearch,
                arguments,
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<String[]> geosearch(
            @NonNull String key,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy,
            @NonNull GeoSearchResultOptions resultOptions) {
        String[] arguments =
                concatenateArrays(
                        new String[] {key}, searchFrom.toArgs(), searchBy.toArgs(), resultOptions.toArgs());
        return commandManager.submitNewCommand(
                GeoSearch, arguments, response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> geosearch(
            @NonNull GlideString key,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy,
            @NonNull GeoSearchResultOptions resultOptions) {
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(key)
                        .add(searchFrom.toArgs())
                        .add(searchBy.toArgs())
                        .add(resultOptions.toArgs())
                        .toArray();

        return commandManager.submitNewCommand(
                GeoSearch,
                arguments,
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Object[]> geosearch(
            @NonNull String key,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy,
            @NonNull GeoSearchOptions options) {
        String[] arguments =
                concatenateArrays(
                        new String[] {key}, searchFrom.toArgs(), searchBy.toArgs(), options.toArgs());
        return commandManager.submitNewCommand(GeoSearch, arguments, this::handleArrayResponse);
    }

    @Override
    public CompletableFuture<Object[]> geosearch(
            @NonNull GlideString key,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy,
            @NonNull GeoSearchOptions options) {
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(key)
                        .add(searchFrom.toArgs())
                        .add(searchBy.toArgs())
                        .add(options.toArgs())
                        .toArray();
        return commandManager.submitNewCommand(
                GeoSearch, arguments, this::handleArrayOrNullResponseBinary);
    }

    @Override
    public CompletableFuture<Object[]> geosearch(
            @NonNull String key,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy,
            @NonNull GeoSearchOptions options,
            @NonNull GeoSearchResultOptions resultOptions) {
        String[] arguments =
                concatenateArrays(
                        new String[] {key},
                        searchFrom.toArgs(),
                        searchBy.toArgs(),
                        options.toArgs(),
                        resultOptions.toArgs());
        return commandManager.submitNewCommand(GeoSearch, arguments, this::handleArrayResponse);
    }

    @Override
    public CompletableFuture<Object[]> geosearch(
            @NonNull GlideString key,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy,
            @NonNull GeoSearchOptions options,
            @NonNull GeoSearchResultOptions resultOptions) {
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(key)
                        .add(searchFrom.toArgs())
                        .add(searchBy.toArgs())
                        .add(options.toArgs())
                        .add(resultOptions.toArgs())
                        .toArray();
        return commandManager.submitNewCommand(
                GeoSearch, arguments, this::handleArrayOrNullResponseBinary);
    }

    @Override
    public CompletableFuture<Long> geosearchstore(
            @NonNull String destination,
            @NonNull String source,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy) {
        String[] arguments =
                concatenateArrays(
                        new String[] {destination, source}, searchFrom.toArgs(), searchBy.toArgs());
        return commandManager.submitNewCommand(GeoSearchStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> geosearchstore(
            @NonNull GlideString destination,
            @NonNull GlideString source,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy) {
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(destination)
                        .add(source)
                        .add(searchFrom.toArgs())
                        .add(searchBy.toArgs())
                        .toArray();
        return commandManager.submitNewCommand(GeoSearchStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> geosearchstore(
            @NonNull String destination,
            @NonNull String source,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy,
            @NonNull GeoSearchResultOptions resultOptions) {
        String[] arguments =
                concatenateArrays(
                        new String[] {destination, source},
                        searchFrom.toArgs(),
                        searchBy.toArgs(),
                        resultOptions.toArgs());
        return commandManager.submitNewCommand(GeoSearchStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> geosearchstore(
            @NonNull GlideString destination,
            @NonNull GlideString source,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy,
            @NonNull GeoSearchResultOptions resultOptions) {
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(destination)
                        .add(source)
                        .add(searchFrom.toArgs())
                        .add(searchBy.toArgs())
                        .add(resultOptions.toArgs())
                        .toArray();
        return commandManager.submitNewCommand(GeoSearchStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> geosearchstore(
            @NonNull String destination,
            @NonNull String source,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy,
            @NonNull GeoSearchStoreOptions options) {
        String[] arguments =
                concatenateArrays(
                        new String[] {destination, source},
                        searchFrom.toArgs(),
                        searchBy.toArgs(),
                        options.toArgs());
        return commandManager.submitNewCommand(GeoSearchStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> geosearchstore(
            @NonNull GlideString destination,
            @NonNull GlideString source,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy,
            @NonNull GeoSearchStoreOptions options) {
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(destination)
                        .add(source)
                        .add(searchFrom.toArgs())
                        .add(searchBy.toArgs())
                        .add(options.toArgs())
                        .toArray();
        return commandManager.submitNewCommand(GeoSearchStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> geosearchstore(
            @NonNull String destination,
            @NonNull String source,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy,
            @NonNull GeoSearchStoreOptions options,
            @NonNull GeoSearchResultOptions resultOptions) {
        String[] arguments =
                concatenateArrays(
                        new String[] {destination, source},
                        searchFrom.toArgs(),
                        searchBy.toArgs(),
                        options.toArgs(),
                        resultOptions.toArgs());
        return commandManager.submitNewCommand(GeoSearchStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> geosearchstore(
            @NonNull GlideString destination,
            @NonNull GlideString source,
            @NonNull GeoSearchOrigin.SearchOrigin searchFrom,
            @NonNull GeoSearchShape searchBy,
            @NonNull GeoSearchStoreOptions options,
            @NonNull GeoSearchResultOptions resultOptions) {
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(destination)
                        .add(source)
                        .add(searchFrom.toArgs())
                        .add(searchBy.toArgs())
                        .add(options.toArgs())
                        .add(resultOptions.toArgs())
                        .toArray();
        return commandManager.submitNewCommand(GeoSearchStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Object[]> sscan(@NonNull String key, @NonNull String cursor) {
        String[] arguments = new String[] {key, cursor};
        return commandManager.submitNewCommand(SScan, arguments, this::handleArrayResponse);
    }

    @Override
    public CompletableFuture<Object[]> sscan(@NonNull GlideString key, @NonNull GlideString cursor) {
        GlideString[] arguments = new GlideString[] {key, cursor};
        return commandManager.submitNewCommand(SScan, arguments, this::handleArrayOrNullResponseBinary);
    }

    @Override
    public CompletableFuture<Object[]> sscan(
            @NonNull String key, @NonNull String cursor, @NonNull SScanOptions sScanOptions) {
        String[] arguments = concatenateArrays(new String[] {key, cursor}, sScanOptions.toArgs());
        return commandManager.submitNewCommand(SScan, arguments, this::handleArrayResponse);
    }

    @Override
    public CompletableFuture<Object[]> sscan(
            @NonNull GlideString key,
            @NonNull GlideString cursor,
            @NonNull SScanOptionsBinary sScanOptions) {
        GlideString[] arguments =
                new ArgsBuilder().add(key).add(cursor).add(sScanOptions.toArgs()).toArray();

        return commandManager.submitNewCommand(SScan, arguments, this::handleArrayOrNullResponseBinary);
    }

    @Override
    public CompletableFuture<Object[]> zscan(@NonNull String key, @NonNull String cursor) {
        String[] arguments = new String[] {key, cursor};
        return commandManager.submitNewCommand(ZScan, arguments, this::handleArrayResponse);
    }

    @Override
    public CompletableFuture<Object[]> zscan(@NonNull GlideString key, @NonNull GlideString cursor) {
        GlideString[] arguments = new GlideString[] {key, cursor};
        return commandManager.submitNewCommand(ZScan, arguments, this::handleArrayOrNullResponseBinary);
    }

    @Override
    public CompletableFuture<Object[]> zscan(
            @NonNull String key, @NonNull String cursor, @NonNull ZScanOptions zScanOptions) {
        String[] arguments = concatenateArrays(new String[] {key, cursor}, zScanOptions.toArgs());
        return commandManager.submitNewCommand(ZScan, arguments, this::handleArrayResponse);
    }

    @Override
    public CompletableFuture<Object[]> zscan(
            @NonNull GlideString key,
            @NonNull GlideString cursor,
            @NonNull ZScanOptionsBinary zScanOptions) {
        GlideString[] arguments =
                new ArgsBuilder().add(key).add(cursor).add(zScanOptions.toArgs()).toArray();

        return commandManager.submitNewCommand(ZScan, arguments, this::handleArrayOrNullResponseBinary);
    }

    @Override
    public CompletableFuture<Object[]> hscan(@NonNull String key, @NonNull String cursor) {
        String[] arguments = new String[] {key, cursor};
        return commandManager.submitNewCommand(HScan, arguments, this::handleArrayResponse);
    }

    @Override
    public CompletableFuture<Object[]> hscan(@NonNull GlideString key, @NonNull GlideString cursor) {
        GlideString[] arguments = new GlideString[] {key, cursor};
        return commandManager.submitNewCommand(HScan, arguments, this::handleArrayOrNullResponseBinary);
    }

    @Override
    public CompletableFuture<Object[]> hscan(
            @NonNull String key, @NonNull String cursor, @NonNull HScanOptions hScanOptions) {
        String[] arguments = concatenateArrays(new String[] {key, cursor}, hScanOptions.toArgs());
        return commandManager.submitNewCommand(HScan, arguments, this::handleArrayResponse);
    }

    @Override
    public CompletableFuture<Object[]> hscan(
            @NonNull GlideString key,
            @NonNull GlideString cursor,
            @NonNull HScanOptionsBinary hScanOptions) {
        GlideString[] arguments =
                new ArgsBuilder().add(key).add(cursor).add(hScanOptions.toArgs()).toArray();

        return commandManager.submitNewCommand(HScan, arguments, this::handleArrayOrNullResponseBinary);
    }

    @Override
    public CompletableFuture<Long> wait(long numreplicas, long timeout) {
        return commandManager.submitBlockingCommand(
                Wait,
                new String[] {Long.toString(numreplicas), Long.toString(timeout)},
                this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long[]> waitaof(long numlocal, long numreplicas, long timeout) {
        return commandManager.submitBlockingCommand(
                WaitAof,
                new String[] {Long.toString(numlocal), Long.toString(numreplicas), Long.toString(timeout)},
                response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<String> migrate(
            String destinationHost, long destinationPort, String key, long destinationDB, long timeout) {
        return commandManager.submitNewCommand(
                Migrate,
                new String[] {
                    destinationHost,
                    Long.toString(destinationPort),
                    key,
                    Long.toString(destinationDB),
                    Long.toString(timeout)
                },
                this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> migrate(
            String destinationHost,
            long destinationPort,
            GlideString key,
            long destinationDB,
            long timeout) {
        return commandManager.submitNewCommand(
                Migrate,
                new ArgsBuilder()
                        .add(destinationHost)
                        .add(destinationPort)
                        .add(key)
                        .add(destinationDB)
                        .add(timeout)
                        .toArray(),
                this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> migrate(
            String destinationHost,
            long destinationPort,
            String key,
            long destinationDB,
            long timeout,
            MigrateOptions migrateOptions) {
        return commandManager.submitNewCommand(
                Migrate,
                new ArgsBuilder()
                        .add(destinationHost)
                        .add(Long.toString(destinationPort))
                        .add(key)
                        .add(Long.toString(destinationDB))
                        .add(Long.toString(timeout))
                        .add(migrateOptions.toArgs())
                        .toArray(),
                this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> migrate(
            String destinationHost,
            long destinationPort,
            GlideString key,
            long destinationDB,
            long timeout,
            MigrateOptions migrateOptions) {
        return commandManager.submitNewCommand(
                Migrate,
                new ArgsBuilder()
                        .add(destinationHost)
                        .add(destinationPort)
                        .add(key)
                        .add(destinationDB)
                        .add(timeout)
                        .add(migrateOptions.toArgs())
                        .toArray(),
                this::handleStringResponse);
    }

    public CompletableFuture<String[]> aclCat() {
        return commandManager.submitNewCommand(
                AclCat,
                EMPTY_STRING_ARRAY,
                response -> castArray(handleArrayResponse(response), String.class));
    }

    public CompletableFuture<String[]> aclCat(String category) {
        return commandManager.submitNewCommand(
                AclCat,
                new String[] {category},
                response -> castArray(handleArrayResponse(response), String.class));
    }

    public CompletableFuture<Long> aclDelUser(String[] usernames) {
        return commandManager.submitNewCommand(AclDelUser, usernames, this::handleLongResponse);
    }

    public CompletableFuture<String> aclDryRun(String username, String command, String[] args) {
        String[] arguments = concatenateArrays(new String[] {username, command}, args);
        return commandManager.submitNewCommand(AclDryRun, arguments, this::handleStringResponse);
    }

    public CompletableFuture<String> aclGenPass() {
        return commandManager.submitNewCommand(
                AclGenPass, EMPTY_STRING_ARRAY, this::handleStringResponse);
    }

    public CompletableFuture<String> aclGenPass(int bits) {
        return commandManager.submitNewCommand(
                AclGenPass, new String[] {String.valueOf(bits)}, this::handleStringResponse);
    }

    public CompletableFuture<Object> aclGetUser(String username) {
        return commandManager.submitNewCommand(
                AclGetUser, new String[] {username}, this::handleObjectOrNullResponse);
    }

    public CompletableFuture<String[]> aclList() {
        return commandManager.submitNewCommand(
                AclList,
                EMPTY_STRING_ARRAY,
                response -> castArray(handleArrayResponse(response), String.class));
    }

    public CompletableFuture<String> aclLoad() {
        return commandManager.submitNewCommand(AclLoad, EMPTY_STRING_ARRAY, this::handleStringResponse);
    }

    public CompletableFuture<Object[]> aclLog() {
        return commandManager.submitNewCommand(AclLog, EMPTY_STRING_ARRAY, this::handleArrayResponse);
    }

    public CompletableFuture<Object[]> aclLog(int count) {
        return commandManager.submitNewCommand(
                AclLog, new String[] {String.valueOf(count)}, this::handleArrayResponse);
    }

    public CompletableFuture<String> aclSave() {
        return commandManager.submitNewCommand(AclSave, EMPTY_STRING_ARRAY, this::handleStringResponse);
    }

    public CompletableFuture<String> aclSetUser(String username, String[] rules) {
        String[] arguments = concatenateArrays(new String[] {username}, rules);
        return commandManager.submitNewCommand(AclSetUser, arguments, this::handleStringResponse);
    }

    public CompletableFuture<String[]> aclUsers() {
        return commandManager.submitNewCommand(
                AclUsers,
                EMPTY_STRING_ARRAY,
                response -> castArray(handleArrayResponse(response), String.class));
    }

    public CompletableFuture<String> aclWhoami() {
        return commandManager.submitNewCommand(
                AclWhoami, EMPTY_STRING_ARRAY, this::handleStringResponse);
    }

    public CompletableFuture<Void> subscribeLazy(Set<String> channels) {
        return commandManager.submitNewCommand(
                Subscribe, channels.toArray(EMPTY_STRING_ARRAY), response -> null);
    }

    public CompletableFuture<Void> subscribe(Set<String> channels, int timeoutMs) {
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("Timeout must be non-negative, got: " + timeoutMs);
        }
        String[] args = new String[channels.size() + 1];
        int i = 0;
        for (String channel : channels) {
            args[i++] = channel;
        }
        args[i] = String.valueOf(timeoutMs);
        return commandManager.submitNewCommand(SubscribeBlocking, args, response -> null);
    }

    public CompletableFuture<Void> psubscribeLazy(Set<String> patterns) {
        return commandManager.submitNewCommand(
                PSubscribe, patterns.toArray(EMPTY_STRING_ARRAY), response -> null);
    }

    public CompletableFuture<Void> psubscribe(Set<String> patterns, int timeoutMs) {
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("Timeout must be non-negative, got: " + timeoutMs);
        }
        String[] args = new String[patterns.size() + 1];
        int i = 0;
        for (String pattern : patterns) {
            args[i++] = pattern;
        }
        args[i] = String.valueOf(timeoutMs);
        return commandManager.submitNewCommand(PSubscribeBlocking, args, response -> null);
    }

    public CompletableFuture<Void> unsubscribeLazy() {
        return commandManager.submitNewCommand(Unsubscribe, EMPTY_STRING_ARRAY, response -> null);
    }

    public CompletableFuture<Void> unsubscribeLazy(Set<String> channels) {
        return commandManager.submitNewCommand(
                Unsubscribe, channels.toArray(EMPTY_STRING_ARRAY), response -> null);
    }

    public CompletableFuture<Void> unsubscribe(Set<String> channels, int timeoutMs) {
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("Timeout must be non-negative, got: " + timeoutMs);
        }
        String[] args = new String[channels.size() + 1];
        int i = 0;
        for (String channel : channels) {
            args[i++] = channel;
        }
        args[i] = String.valueOf(timeoutMs);
        return commandManager.submitNewCommand(UnsubscribeBlocking, args, response -> null);
    }

    public CompletableFuture<Void> unsubscribe(int timeoutMs) {
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("Timeout must be non-negative, got: " + timeoutMs);
        }
        return commandManager.submitNewCommand(
                UnsubscribeBlocking, new String[] {String.valueOf(timeoutMs)}, response -> null);
    }

    public CompletableFuture<Void> punsubscribeLazy() {
        return commandManager.submitNewCommand(PUnsubscribe, EMPTY_STRING_ARRAY, response -> null);
    }

    public CompletableFuture<Void> punsubscribeLazy(Set<String> patterns) {
        return commandManager.submitNewCommand(
                PUnsubscribe, patterns.toArray(EMPTY_STRING_ARRAY), response -> null);
    }

    public CompletableFuture<Void> punsubscribe(Set<String> patterns, int timeoutMs) {
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("Timeout must be non-negative, got: " + timeoutMs);
        }
        String[] args = new String[patterns.size() + 1];
        int i = 0;
        for (String pattern : patterns) {
            args[i++] = pattern;
        }
        args[i] = String.valueOf(timeoutMs);
        return commandManager.submitNewCommand(PUnsubscribeBlocking, args, response -> null);
    }

    public CompletableFuture<Void> punsubscribe(int timeoutMs) {
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("Timeout must be non-negative, got: " + timeoutMs);
        }
        return commandManager.submitNewCommand(
                PUnsubscribeBlocking, new String[] {String.valueOf(timeoutMs)}, response -> null);
    }

    protected Object parseSubscriptionState(Object response) {
        if (!(response instanceof Object[])) {
            throw new RuntimeException(
                    "Invalid response format from GetSubscriptions: expected Object[], got "
                            + (response == null ? "null" : response.getClass().getName()));
        }
        Object[] arr = (Object[]) response;
        if (arr.length != 4) {
            throw new RuntimeException(
                    "Invalid response format from GetSubscriptions: expected array length 4, got "
                            + arr.length);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object[]> desiredMap = (Map<String, Object[]>) arr[1];
        @SuppressWarnings("unchecked")
        Map<String, Object[]> actualMap = (Map<String, Object[]>) arr[3];

        return new Object[] {desiredMap, actualMap};
    }

    /**
     * Internal method for enqueueing PubSub messages from native callback. This is called by the
     * native layer when PubSub messages are received.
     */
    public void __enqueuePubSubMessage(PubSubMessage message) {
        // Deliver to callback if configured; otherwise enqueue for pull-based APIs
        if (subscriptionConfiguration.isPresent()
                && subscriptionConfiguration.get().getCallback().isPresent()) {
            try {
                subscriptionConfiguration
                        .get()
                        .getCallback()
                        .get()
                        .accept(message, subscriptionConfiguration.get().getContext().orElse(null));
            } catch (Throwable ignored) {
                // Ensure user callback exceptions do not break push delivery loop
            }
            return;
        }
        messageHandler.getQueue().push(message);
    }
}
