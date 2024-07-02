/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static glide.api.models.GlideString.gs;
import static glide.api.models.commands.SortBaseOptions.STORE_COMMAND_STRING;
import static glide.api.models.commands.bitmap.BitFieldOptions.BitFieldReadOnlySubCommands;
import static glide.api.models.commands.bitmap.BitFieldOptions.BitFieldSubCommands;
import static glide.api.models.commands.bitmap.BitFieldOptions.createBitFieldArgs;
import static glide.api.models.commands.bitmap.BitFieldOptions.createBitFieldGlideStringArgs;
import static glide.api.models.commands.stream.StreamClaimOptions.JUST_ID_REDIS_API;
import static glide.ffi.resolvers.SocketListenerResolver.getSocket;
import static glide.utils.ArrayTransformUtils.cast3DArray;
import static glide.utils.ArrayTransformUtils.castArray;
import static glide.utils.ArrayTransformUtils.castArrayofArrays;
import static glide.utils.ArrayTransformUtils.castMapOf2DArray;
import static glide.utils.ArrayTransformUtils.castMapOfArrays;
import static glide.utils.ArrayTransformUtils.concatenateArrays;
import static glide.utils.ArrayTransformUtils.convertMapToKeyValueGlideStringArray;
import static glide.utils.ArrayTransformUtils.convertMapToKeyValueStringArray;
import static glide.utils.ArrayTransformUtils.convertMapToValueKeyStringArray;
import static glide.utils.ArrayTransformUtils.mapGeoDataToArray;
import static glide.utils.ArrayTransformUtils.mapGeoDataToGlideStringArray;
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
import static redis_request.RedisRequestOuterClass.RequestType.Copy;
import static redis_request.RedisRequestOuterClass.RequestType.Decr;
import static redis_request.RedisRequestOuterClass.RequestType.DecrBy;
import static redis_request.RedisRequestOuterClass.RequestType.Del;
import static redis_request.RedisRequestOuterClass.RequestType.Dump;
import static redis_request.RedisRequestOuterClass.RequestType.Exists;
import static redis_request.RedisRequestOuterClass.RequestType.Expire;
import static redis_request.RedisRequestOuterClass.RequestType.ExpireAt;
import static redis_request.RedisRequestOuterClass.RequestType.ExpireTime;
import static redis_request.RedisRequestOuterClass.RequestType.FCall;
import static redis_request.RedisRequestOuterClass.RequestType.FCallReadOnly;
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
import static redis_request.RedisRequestOuterClass.RequestType.Publish;
import static redis_request.RedisRequestOuterClass.RequestType.RPop;
import static redis_request.RedisRequestOuterClass.RequestType.RPush;
import static redis_request.RedisRequestOuterClass.RequestType.RPushX;
import static redis_request.RedisRequestOuterClass.RequestType.Rename;
import static redis_request.RedisRequestOuterClass.RequestType.RenameNX;
import static redis_request.RedisRequestOuterClass.RequestType.Restore;
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
import static redis_request.RedisRequestOuterClass.RequestType.Touch;
import static redis_request.RedisRequestOuterClass.RequestType.Type;
import static redis_request.RedisRequestOuterClass.RequestType.Unlink;
import static redis_request.RedisRequestOuterClass.RequestType.Wait;
import static redis_request.RedisRequestOuterClass.RequestType.Watch;
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
import glide.api.models.GlideString;
import glide.api.models.PubSubMessage;
import glide.api.models.Script;
import glide.api.models.commands.ExpireOptions;
import glide.api.models.commands.GetExOptions;
import glide.api.models.commands.LInsertOptions.InsertPosition;
import glide.api.models.commands.LPosOptions;
import glide.api.models.commands.ListDirection;
import glide.api.models.commands.RangeOptions;
import glide.api.models.commands.RangeOptions.LexRange;
import glide.api.models.commands.RangeOptions.RangeQuery;
import glide.api.models.commands.RangeOptions.ScoreRange;
import glide.api.models.commands.RangeOptions.ScoredRangeQuery;
import glide.api.models.commands.RestoreOptions;
import glide.api.models.commands.ScoreFilter;
import glide.api.models.commands.ScriptOptions;
import glide.api.models.commands.ScriptOptionsGlideString;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.WeightAggregateOptions.Aggregate;
import glide.api.models.commands.WeightAggregateOptions.KeyArray;
import glide.api.models.commands.WeightAggregateOptions.KeysOrWeightedKeys;
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
import glide.api.models.commands.stream.StreamClaimOptions;
import glide.api.models.commands.stream.StreamGroupOptions;
import glide.api.models.commands.stream.StreamPendingOptions;
import glide.api.models.commands.stream.StreamRange;
import glide.api.models.commands.stream.StreamReadGroupOptions;
import glide.api.models.commands.stream.StreamReadOptions;
import glide.api.models.commands.stream.StreamTrimOptions;
import glide.api.models.configuration.BaseClientConfiguration;
import glide.api.models.configuration.BaseSubscriptionConfiguration;
import glide.api.models.exceptions.ConfigurationError;
import glide.api.models.exceptions.RedisException;
import glide.connectors.handlers.CallbackDispatcher;
import glide.connectors.handlers.ChannelHandler;
import glide.connectors.handlers.MessageHandler;
import glide.connectors.resources.Platform;
import glide.connectors.resources.ThreadPoolResource;
import glide.connectors.resources.ThreadPoolResourceAllocator;
import glide.ffi.resolvers.RedisValueResolver;
import glide.managers.BaseResponseResolver;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.NotImplementedException;
import response.ResponseOuterClass.ConstantResponse;
import response.ResponseOuterClass.Response;

/** Base Client class for Redis */
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

    /** Redis simple string response with "OK" */
    public static final String OK = ConstantResponse.OK.toString();

    protected final CommandManager commandManager;
    protected final ConnectionManager connectionManager;
    protected final ConcurrentLinkedDeque<PubSubMessage> messageQueue;
    protected final Optional<BaseSubscriptionConfiguration> subscriptionConfiguration;

    /** Helper which extracts data from received {@link Response}s from GLIDE. */
    private static final BaseResponseResolver responseResolver =
            new BaseResponseResolver(RedisValueResolver::valueFromPointer);

    /** Helper which extracts data with binary strings from received {@link Response}s from GLIDE. */
    private static final BaseResponseResolver binaryResponseResolver =
            new BaseResponseResolver(RedisValueResolver::valueFromPointerBinary);

    /** A constructor. */
    protected BaseClient(ClientBuilder builder) {
        this.connectionManager = builder.connectionManager;
        this.commandManager = builder.commandManager;
        this.messageQueue = builder.messageQueue;
        this.subscriptionConfiguration = builder.subscriptionConfiguration;
    }

    /** Auxiliary builder which wraps all fields to be initialized in the constructor. */
    @RequiredArgsConstructor
    protected static class ClientBuilder {
        private final ConnectionManager connectionManager;
        private final CommandManager commandManager;
        private final ConcurrentLinkedDeque<PubSubMessage> messageQueue;
        private final Optional<BaseSubscriptionConfiguration> subscriptionConfiguration;
    }

    /**
     * Async request for an async (non-blocking) Redis client.
     *
     * @param config Redis client Configuration.
     * @param constructor Redis client constructor reference.
     * @param <T> Client type.
     * @return a Future to connect and return a RedisClient.
     */
    protected static <T extends BaseClient> CompletableFuture<T> CreateClient(
            @NonNull BaseClientConfiguration config, Function<ClientBuilder, T> constructor) {
        try {
            ThreadPoolResource threadPoolResource = config.getThreadPoolResource();
            if (threadPoolResource == null) {
                threadPoolResource =
                        ThreadPoolResourceAllocator.getOrCreate(Platform.getThreadPoolResourceSupplier());
            }
            MessageHandler messageHandler = buildMessageHandler(config);
            ChannelHandler channelHandler = buildChannelHandler(threadPoolResource, messageHandler);
            ConnectionManager connectionManager = buildConnectionManager(channelHandler);
            CommandManager commandManager = buildCommandManager(channelHandler);
            // TODO: Support exception throwing, including interrupted exceptions
            return connectionManager
                    .connectToRedis(config)
                    .thenApply(
                            ignored ->
                                    constructor.apply(
                                            new ClientBuilder(
                                                    connectionManager,
                                                    commandManager,
                                                    messageHandler.getQueue(),
                                                    Optional.ofNullable(config.getSubscriptionConfiguration()))));
        } catch (InterruptedException e) {
            // Something bad happened while we were establishing netty connection to UDS
            var future = new CompletableFuture<T>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Tries to return a next pubsub message.
     *
     * @throws ConfigurationError If client is not subscribed to any channel or if client configured
     *     with a callback.
     * @return A message if any or <code>null</code> if there are no unread messages.
     */
    public PubSubMessage tryGetPubSubMessage() {
        if (subscriptionConfiguration.isEmpty()) {
            throw new ConfigurationError(
                    "The operation will never complete since there was no pubsub subscriptions applied to the"
                            + " client.");
        }
        if (subscriptionConfiguration.get().getCallback().isPresent()) {
            throw new ConfigurationError(
                    "The operation will never complete since messages will be passed to the configured"
                            + " callback.");
        }
        return messageQueue.poll();
    }

    /**
     * Returns a promise for a next pubsub message.
     *
     * @apiNote <b>Not implemented!</b>
     * @throws ConfigurationError If client is not subscribed to any channel or if client configured
     *     with a callback.
     * @return A <code>Future</code> which resolved with the next incoming message.
     */
    public CompletableFuture<PubSubMessage> getPubSubMessage() {
        if (subscriptionConfiguration.isEmpty()) {
            throw new ConfigurationError(
                    "The operation will never complete since there was no pubsub subscriptions applied to the"
                            + " client.");
        }
        if (subscriptionConfiguration.get().getCallback().isPresent()) {
            throw new ConfigurationError(
                    "The operation will never complete since messages will be passed to the configured"
                            + " callback.");
        }
        throw new NotImplementedException(
                "This feature will be supported in a future release of the GLIDE java client");
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
        try {
            connectionManager.closeConnection().get();
        } catch (InterruptedException e) {
            // suppressing the interrupted exception - it is already suppressed in the future
            throw new RuntimeException(e);
        }
    }

    protected static MessageHandler buildMessageHandler(BaseClientConfiguration config) {
        if (config.getSubscriptionConfiguration() == null) {
            return new MessageHandler(Optional.empty(), Optional.empty(), responseResolver);
        }
        return new MessageHandler(
                config.getSubscriptionConfiguration().getCallback(),
                config.getSubscriptionConfiguration().getContext(),
                responseResolver);
    }

    protected static ChannelHandler buildChannelHandler(
            ThreadPoolResource threadPoolResource, MessageHandler messageHandler)
            throws InterruptedException {
        CallbackDispatcher callbackDispatcher = new CallbackDispatcher(messageHandler);
        return new ChannelHandler(callbackDispatcher, getSocket(), threadPoolResource);
    }

    protected static ConnectionManager buildConnectionManager(ChannelHandler channelHandler) {
        return new ConnectionManager(channelHandler);
    }

    protected static CommandManager buildCommandManager(ChannelHandler channelHandler) {
        return new CommandManager(channelHandler);
    }

    /**
     * Extracts the value from a <code>GLIDE core</code> response message and either throws an
     * exception or returns the value as an object of type <code>T</code>.
     *
     * @param response Redis protobuf message.
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
     * @throws RedisException On a type mismatch.
     */
    @SuppressWarnings("unchecked")
    protected <T> T handleRedisResponse(
            Class<T> classType, EnumSet<ResponseFlags> flags, Response response) throws RedisException {
        boolean encodingUtf8 = flags.contains(ResponseFlags.ENCODING_UTF8);
        boolean isNullable = flags.contains(ResponseFlags.IS_NULLABLE);
        Object value =
                encodingUtf8 ? responseResolver.apply(response) : binaryResponseResolver.apply(response);
        if (isNullable && (value == null)) {
            return null;
        }

        value = convertByteArrayToGlideString(value);

        if (classType.isInstance(value)) {
            return (T) value;
        }
        String className = value == null ? "null" : value.getClass().getSimpleName();
        throw new RedisException(
                "Unexpected return type from Redis: got "
                        + className
                        + " expected "
                        + classType.getSimpleName());
    }

    protected Object handleObjectOrNullResponse(Response response) throws RedisException {
        return handleRedisResponse(
                Object.class, EnumSet.of(ResponseFlags.IS_NULLABLE, ResponseFlags.ENCODING_UTF8), response);
    }

    protected Object handleBinaryObjectOrNullResponse(Response response) throws RedisException {
        return handleRedisResponse(Object.class, EnumSet.of(ResponseFlags.IS_NULLABLE), response);
    }

    protected String handleStringResponse(Response response) throws RedisException {
        return handleRedisResponse(String.class, EnumSet.of(ResponseFlags.ENCODING_UTF8), response);
    }

    protected String handleStringOrNullResponse(Response response) throws RedisException {
        return handleRedisResponse(
                String.class, EnumSet.of(ResponseFlags.IS_NULLABLE, ResponseFlags.ENCODING_UTF8), response);
    }

    protected byte[] handleBytesOrNullResponse(Response response) throws RedisException {
        var result =
                handleRedisResponse(GlideString.class, EnumSet.of(ResponseFlags.IS_NULLABLE), response);
        if (result == null) return null;

        return result.getBytes();
    }

    protected GlideString handleGlideStringOrNullResponse(Response response) throws RedisException {
        return handleRedisResponse(GlideString.class, EnumSet.of(ResponseFlags.IS_NULLABLE), response);
    }

    protected GlideString handleGlideStringResponse(Response response) throws RedisException {
        return handleRedisResponse(GlideString.class, EnumSet.noneOf(ResponseFlags.class), response);
    }

    protected Boolean handleBooleanResponse(Response response) throws RedisException {
        return handleRedisResponse(Boolean.class, EnumSet.noneOf(ResponseFlags.class), response);
    }

    protected Long handleLongResponse(Response response) throws RedisException {
        return handleRedisResponse(Long.class, EnumSet.noneOf(ResponseFlags.class), response);
    }

    protected Long handleLongOrNullResponse(Response response) throws RedisException {
        return handleRedisResponse(Long.class, EnumSet.of(ResponseFlags.IS_NULLABLE), response);
    }

    protected Double handleDoubleResponse(Response response) throws RedisException {
        return handleRedisResponse(Double.class, EnumSet.noneOf(ResponseFlags.class), response);
    }

    protected Double handleDoubleOrNullResponse(Response response) throws RedisException {
        return handleRedisResponse(Double.class, EnumSet.of(ResponseFlags.IS_NULLABLE), response);
    }

    protected Object[] handleArrayResponse(Response response) throws RedisException {
        return handleRedisResponse(Object[].class, EnumSet.of(ResponseFlags.ENCODING_UTF8), response);
    }

    protected Object[] handleArrayResponseBinary(Response response) throws RedisException {
        return handleRedisResponse(Object[].class, EnumSet.noneOf(ResponseFlags.class), response);
    }

    protected Object[] handleArrayOrNullResponse(Response response) throws RedisException {
        return handleRedisResponse(
                Object[].class,
                EnumSet.of(ResponseFlags.IS_NULLABLE, ResponseFlags.ENCODING_UTF8),
                response);
    }

    protected Object[] handleArrayOrNullResponseBinary(Response response) throws RedisException {
        return handleRedisResponse(Object[].class, EnumSet.of(ResponseFlags.IS_NULLABLE), response);
    }

    /**
     * @param response A Protobuf response
     * @return A map of <code>String</code> to <code>V</code>.
     * @param <V> Value type.
     */
    @SuppressWarnings("unchecked") // raw Map cast to Map<String, V>
    protected <V> Map<String, V> handleMapResponse(Response response) throws RedisException {
        return handleRedisResponse(Map.class, EnumSet.of(ResponseFlags.ENCODING_UTF8), response);
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
            throws RedisException {
        return handleRedisResponse(Map.class, EnumSet.noneOf(ResponseFlags.class), response);
    }

    /**
     * @param response A Protobuf response
     * @return A map of <code>String</code> to <code>V</code> or <code>null</code>
     * @param <V> Value type.
     */
    @SuppressWarnings("unchecked") // raw Map cast to Map<String, V>
    protected <V> Map<String, V> handleMapOrNullResponse(Response response) throws RedisException {
        return handleRedisResponse(
                Map.class, EnumSet.of(ResponseFlags.IS_NULLABLE, ResponseFlags.ENCODING_UTF8), response);
    }

    /**
     * @param response A Protobuf response
     * @return A map of a map of <code>String[][]</code>
     */
    protected Map<String, Map<String, String[][]>> handleXReadResponse(Response response)
            throws RedisException {
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

    @SuppressWarnings("unchecked") // raw Set cast to Set<String>
    protected Set<String> handleSetResponse(Response response) throws RedisException {
        return handleRedisResponse(Set.class, EnumSet.of(ResponseFlags.ENCODING_UTF8), response);
    }

    @SuppressWarnings("unchecked")
    protected Set<GlideString> handleSetBinaryResponse(Response response) throws RedisException {
        return handleRedisResponse(Set.class, EnumSet.noneOf(ResponseFlags.class), response);
    }

    /** Process a <code>FUNCTION LIST</code> standalone response. */
    @SuppressWarnings("unchecked")
    protected Map<String, Object>[] handleFunctionListResponse(Object[] response) {
        Map<String, Object>[] data = castArray(response, Map.class);
        for (Map<String, Object> libraryInfo : data) {
            Object[] functions = (Object[]) libraryInfo.get("functions");
            var functionInfo = castArray(functions, Map.class);
            libraryInfo.put("functions", functionInfo);
        }
        return data;
    }

    /** Process a <code>FUNCTION STATS</code> standalone response. */
    protected Map<String, Map<String, Object>> handleFunctionStatsResponse(
            Map<String, Map<String, Object>> response) {
        Map<String, Object> runningScriptInfo = response.get("running_script");
        if (runningScriptInfo != null) {
            Object[] command = (Object[]) runningScriptInfo.get("command");
            runningScriptInfo.put("command", castArray(command, String.class));
        }
        return response;
    }

    /** Process a <code>LCS key1 key2 IDX</code> response */
    protected Map<String, Object> handleLcsIdxResponse(Map<String, Object> response)
            throws RedisException {
        Long[][][] convertedMatchesObject =
                cast3DArray((Object[]) (response.get(LCS_MATCHES_RESULT_KEY)), Long.class);

        if (convertedMatchesObject == null) {
            throw new NullPointerException(
                    "LCS result does not contain the key \"" + LCS_MATCHES_RESULT_KEY + "\"");
        }

        response.put("matches", convertedMatchesObject);
        return response;
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
        GlideString[] arguments = ArrayUtils.addFirst(options.toGlideStringArgs(), key);
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
                ArrayUtils.addAll(new GlideString[] {key, value}, options.toGlideStringArgs());
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
    public CompletableFuture<String> objectEncoding(@NonNull String key) {
        return commandManager.submitNewCommand(
                ObjectEncoding, new String[] {key}, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String> objectEncoding(@NonNull GlideString key) {
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
                new String[] {key, Long.toString(count), WITH_VALUES_REDIS_API},
                response -> castArrayofArrays(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[][]> hrandfieldWithCountWithValues(
            @NonNull GlideString key, long count) {
        return commandManager.submitNewCommand(
                HRandField,
                new GlideString[] {key, GlideString.of(count), GlideString.of(WITH_VALUES_REDIS_API)},
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
    public CompletableFuture<String[]> lpopCount(@NonNull String key, long count) {
        return commandManager.submitNewCommand(
                LPop,
                new String[] {key, Long.toString(count)},
                response -> castArray(handleArrayResponse(response), String.class));
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
                concatenateArrays(new GlideString[] {key, element}, options.toGlideStringArgs());
        return commandManager.submitNewCommand(LPos, arguments, this::handleLongOrNullResponse);
    }

    @Override
    public CompletableFuture<Long[]> lposCount(
            @NonNull String key, @NonNull String element, long count) {
        return commandManager.submitNewCommand(
                LPos,
                new String[] {key, element, COUNT_REDIS_API, Long.toString(count)},
                response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> lposCount(
            @NonNull GlideString key, @NonNull GlideString element, long count) {
        return commandManager.submitNewCommand(
                LPos,
                new GlideString[] {key, element, gs(COUNT_REDIS_API), gs(Long.toString(count))},
                response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<Long[]> lposCount(
            @NonNull String key, @NonNull String element, long count, @NonNull LPosOptions options) {
        String[] arguments =
                concatenateArrays(
                        new String[] {key, element, COUNT_REDIS_API, Long.toString(count)}, options.toArgs());

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
                concatenateArrays(
                        new GlideString[] {key, element, gs(COUNT_REDIS_API), gs(Long.toString(count))},
                        options.toGlideStringArgs());

        return commandManager.submitNewCommand(
                LPos, arguments, response -> castArray(handleArrayResponse(response), Long.class));
    }

    @Override
    public CompletableFuture<String[]> lrange(@NonNull String key, long start, long end) {
        return commandManager.submitNewCommand(
                LRange,
                new String[] {key, Long.toString(start), Long.toString(end)},
                response -> castArray(handleArrayOrNullResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> lrange(@NonNull GlideString key, long start, long end) {
        return commandManager.submitNewCommand(
                LRange,
                new GlideString[] {key, gs(Long.toString(start)), gs(Long.toString(end))},
                response -> castArray(handleArrayOrNullResponseBinary(response), GlideString.class));
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
    public CompletableFuture<String[]> rpopCount(@NonNull String key, long count) {
        return commandManager.submitNewCommand(
                RPop,
                new String[] {key, Long.toString(count)},
                response -> castArray(handleArrayOrNullResponse(response), String.class));
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
                ArrayUtils.addAll(
                        new GlideString[] {key, gs(Long.toString(seconds))}, expireOptions.toGlideStringArgs());
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
                ArrayUtils.addAll(
                        new GlideString[] {key, gs(Long.toString(unixSeconds))},
                        expireOptions.toGlideStringArgs());
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
                ArrayUtils.addAll(
                        new GlideString[] {key, gs(Long.toString(milliseconds))},
                        expireOptions.toGlideStringArgs());
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
                ArrayUtils.addAll(
                        new GlideString[] {key, gs(Long.toString(unixMilliseconds))},
                        expireOptions.toGlideStringArgs());
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
        if (script.getBinarySafeOutput()) {
            return commandManager.submitScript(
                    script, List.of(), List.of(), this::handleBinaryObjectOrNullResponse);
        } else {
            return commandManager.submitScript(
                    script, List.of(), List.of(), this::handleObjectOrNullResponse);
        }
    }

    @Override
    public CompletableFuture<Object> invokeScript(
            @NonNull Script script, @NonNull ScriptOptions options) {
        if (script.getBinarySafeOutput()) {
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
        if (script.getBinarySafeOutput()) {
            return commandManager.submitScript(
                    script, options.getKeys(), options.getArgs(), this::handleBinaryObjectOrNullResponse);
        } else {
            return commandManager.submitScript(
                    script, options.getKeys(), options.getArgs(), this::handleObjectOrNullResponse);
        }
    }

    @Override
    public CompletableFuture<Long> zadd(
            @NonNull String key,
            @NonNull Map<String, Double> membersScoresMap,
            @NonNull ZAddOptions options,
            boolean changed) {
        String[] changedArg = changed ? new String[] {"CH"} : new String[] {};
        String[] membersScores = convertMapToValueKeyStringArray(membersScoresMap);

        String[] arguments =
                concatenateArrays(new String[] {key}, options.toArgs(), changedArg, membersScores);

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
            @NonNull String key, @NonNull Map<String, Double> membersScoresMap, boolean changed) {
        return this.zadd(key, membersScoresMap, ZAddOptions.builder().build(), changed);
    }

    @Override
    public CompletableFuture<Long> zadd(
            @NonNull String key, @NonNull Map<String, Double> membersScoresMap) {
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
            @NonNull String key, @NonNull String member, double increment) {
        String[] arguments =
                concatenateArrays(
                        new String[] {key}, new String[] {"INCR", Double.toString(increment), member});

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
    public CompletableFuture<Map<String, Double>> zpopmin(@NonNull String key) {
        return commandManager.submitNewCommand(ZPopMin, new String[] {key}, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Object[]> bzpopmin(@NonNull String[] keys, double timeout) {
        String[] arguments = ArrayUtils.add(keys, Double.toString(timeout));
        return commandManager.submitNewCommand(BZPopMin, arguments, this::handleArrayOrNullResponse);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zpopmax(@NonNull String key, long count) {
        return commandManager.submitNewCommand(
                ZPopMax, new String[] {key, Long.toString(count)}, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zpopmax(@NonNull String key) {
        return commandManager.submitNewCommand(ZPopMax, new String[] {key}, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Object[]> bzpopmax(@NonNull String[] keys, double timeout) {
        String[] arguments = ArrayUtils.add(keys, Double.toString(timeout));
        return commandManager.submitNewCommand(BZPopMax, arguments, this::handleArrayOrNullResponse);
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
                ZRank, new String[] {key, member, WITH_SCORE_REDIS_API}, this::handleArrayOrNullResponse);
    }

    @Override
    public CompletableFuture<Long> zrevrank(@NonNull String key, @NonNull String member) {
        return commandManager.submitNewCommand(
                ZRevRank, new String[] {key, member}, this::handleLongOrNullResponse);
    }

    @Override
    public CompletableFuture<Object[]> zrevrankWithScore(
            @NonNull String key, @NonNull String member) {
        return commandManager.submitNewCommand(
                ZRevRank,
                new String[] {key, member, WITH_SCORE_REDIS_API},
                this::handleArrayOrNullResponse);
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
    public CompletableFuture<Map<String, Double>> zdiffWithScores(@NonNull String[] keys) {
        String[] arguments = ArrayUtils.addFirst(keys, Long.toString(keys.length));
        arguments = ArrayUtils.add(arguments, WITH_SCORES_REDIS_API);
        return commandManager.submitNewCommand(ZDiff, arguments, this::handleMapResponse);
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
    public CompletableFuture<Long> zremrangebyscore(
            @NonNull String key, @NonNull ScoreRange minScore, @NonNull ScoreRange maxScore) {
        return commandManager.submitNewCommand(
                ZRemRangeByScore,
                new String[] {key, minScore.toArgs(), maxScore.toArgs()},
                this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zlexcount(
            @NonNull String key, @NonNull LexRange minLex, @NonNull LexRange maxLex) {
        return commandManager.submitNewCommand(
                ZLexCount, new String[] {key, minLex.toArgs(), maxLex.toArgs()}, this::handleLongResponse);
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
            @NonNull String destination, @NonNull String source, @NonNull RangeQuery rangeQuery) {
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
            @NonNull String destination, @NonNull KeysOrWeightedKeys keysOrWeightedKeys) {
        String[] arguments = concatenateArrays(new String[] {destination}, keysOrWeightedKeys.toArgs());
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
            @NonNull String destination, @NonNull KeysOrWeightedKeys keysOrWeightedKeys) {
        String[] arguments = concatenateArrays(new String[] {destination}, keysOrWeightedKeys.toArgs());
        return commandManager.submitNewCommand(ZInterStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String[]> zunion(@NonNull KeyArray keys) {
        return commandManager.submitNewCommand(
                ZUnion, keys.toArgs(), response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<Map<String, Double>> zunionWithScores(
            @NonNull KeysOrWeightedKeys keysOrWeightedKeys, @NonNull Aggregate aggregate) {
        String[] arguments =
                concatenateArrays(
                        keysOrWeightedKeys.toArgs(), aggregate.toArgs(), new String[] {WITH_SCORES_REDIS_API});
        return commandManager.submitNewCommand(ZUnion, arguments, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zunionWithScores(
            @NonNull KeysOrWeightedKeys keysOrWeightedKeys) {
        String[] arguments =
                concatenateArrays(keysOrWeightedKeys.toArgs(), new String[] {WITH_SCORES_REDIS_API});
        return commandManager.submitNewCommand(ZUnion, arguments, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<String[]> zinter(@NonNull KeyArray keys) {
        return commandManager.submitNewCommand(
                ZInter, keys.toArgs(), response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<Map<String, Double>> zinterWithScores(
            @NonNull KeysOrWeightedKeys keysOrWeightedKeys, @NonNull Aggregate aggregate) {
        String[] arguments =
                concatenateArrays(
                        keysOrWeightedKeys.toArgs(), aggregate.toArgs(), new String[] {WITH_SCORES_REDIS_API});
        return commandManager.submitNewCommand(ZInter, arguments, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zinterWithScores(
            @NonNull KeysOrWeightedKeys keysOrWeightedKeys) {
        String[] arguments =
                concatenateArrays(keysOrWeightedKeys.toArgs(), new String[] {WITH_SCORES_REDIS_API});
        return commandManager.submitNewCommand(ZInter, arguments, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<String> zrandmember(@NonNull String key) {
        return commandManager.submitNewCommand(
                ZRandMember, new String[] {key}, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String[]> zrandmemberWithCount(@NonNull String key, long count) {
        return commandManager.submitNewCommand(
                ZRandMember,
                new String[] {key, Long.toString(count)},
                response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<Object[][]> zrandmemberWithCountWithScores(
            @NonNull String key, long count) {
        String[] arguments = new String[] {key, Long.toString(count), WITH_SCORES_REDIS_API};
        return commandManager.submitNewCommand(
                ZRandMember,
                arguments,
                response -> castArray(handleArrayResponse(response), Object[].class));
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
                        new String[] {LIMIT_REDIS_API, Long.toString(limit)});
        return commandManager.submitNewCommand(ZInterCard, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zintercard(@NonNull GlideString[] keys, long limit) {
        GlideString[] arguments =
                concatenateArrays(
                        new GlideString[] {gs(Integer.toString(keys.length))},
                        keys,
                        new GlideString[] {gs(LIMIT_REDIS_API), gs(Long.toString(limit))});
        return commandManager.submitNewCommand(ZInterCard, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String> xadd(@NonNull String key, @NonNull Map<String, String> values) {
        return xadd(key, values, StreamAddOptions.builder().build());
    }

    @Override
    public CompletableFuture<GlideString> xadd(
            @NonNull GlideString key, @NonNull Map<GlideString, GlideString> values) {
        return xadd(key, values, StreamAddOptions.builder().build());
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
    public CompletableFuture<GlideString> xadd(
            @NonNull GlideString key,
            @NonNull Map<GlideString, GlideString> values,
            @NonNull StreamAddOptions options) {
        String[] toArgsString = options.toArgs();
        GlideString[] toArgs =
                Arrays.stream(toArgsString).map(GlideString::gs).toArray(GlideString[]::new);
        GlideString[] arguments =
                ArrayUtils.addAll(
                        ArrayUtils.addFirst(toArgs, key), convertMapToKeyValueGlideStringArray(values));
        return commandManager.submitNewCommand(XAdd, arguments, this::handleGlideStringOrNullResponse);
    }

    @Override
    public CompletableFuture<Map<String, Map<String, String[][]>>> xread(
            @NonNull Map<String, String> keysAndIds) {
        return xread(keysAndIds, StreamReadOptions.builder().build());
    }

    @Override
    public CompletableFuture<Map<String, Map<String, String[][]>>> xread(
            @NonNull Map<String, String> keysAndIds, @NonNull StreamReadOptions options) {
        String[] arguments = options.toArgs(keysAndIds);
        return commandManager.submitNewCommand(XRead, arguments, this::handleXReadResponse);
    }

    @Override
    public CompletableFuture<Long> xtrim(@NonNull String key, @NonNull StreamTrimOptions options) {
        String[] arguments = ArrayUtils.addFirst(options.toArgs(), key);
        return commandManager.submitNewCommand(XTrim, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> xtrim(
            @NonNull GlideString key, @NonNull StreamTrimOptions options) {
        GlideString[] arguments = ArrayUtils.addFirst(options.toGlideStringArgs(), key);
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
                XRange, arguments, response -> castMapOf2DArray(handleMapResponse(response), String.class));
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
                response -> castMapOf2DArray(handleBinaryStringMapResponse(response), GlideString.class));
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
                response -> castMapOf2DArray(handleMapResponse(response), String.class));
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
                response -> castMapOf2DArray(handleBinaryStringMapResponse(response), GlideString.class));
    }

    @Override
    public CompletableFuture<String> xgroupCreate(
            @NonNull String key, @NonNull String groupName, @NonNull String id) {
        return commandManager.submitNewCommand(
                XGroupCreate, new String[] {key, groupName, id}, this::handleStringResponse);
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
    public CompletableFuture<Boolean> xgroupDestroy(@NonNull String key, @NonNull String groupname) {
        return commandManager.submitNewCommand(
                XGroupDestroy, new String[] {key, groupname}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> xgroupCreateConsumer(
            @NonNull String key, @NonNull String group, @NonNull String consumer) {
        return commandManager.submitNewCommand(
                XGroupCreateConsumer, new String[] {key, group, consumer}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Long> xgroupDelConsumer(
            @NonNull String key, @NonNull String group, @NonNull String consumer) {
        return commandManager.submitNewCommand(
                XGroupDelConsumer, new String[] {key, group, consumer}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String> xgroupSetId(
            @NonNull String key, @NonNull String groupName, @NonNull String id) {
        return commandManager.submitNewCommand(
                XGroupSetId, new String[] {key, groupName, id}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> xgroupSetId(
            @NonNull String key,
            @NonNull String groupName,
            @NonNull String id,
            @NonNull String entriesReadId) {
        String[] arguments = new String[] {key, groupName, id, "ENTRIESREAD", entriesReadId};
        return commandManager.submitNewCommand(XGroupSetId, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Map<String, Map<String, String[][]>>> xreadgroup(
            @NonNull Map<String, String> keysAndIds, @NonNull String group, @NonNull String consumer) {
        return xreadgroup(keysAndIds, group, consumer, StreamReadGroupOptions.builder().build());
    }

    @Override
    public CompletableFuture<Map<String, Map<String, String[][]>>> xreadgroup(
            @NonNull Map<String, String> keysAndIds,
            @NonNull String group,
            @NonNull String consumer,
            @NonNull StreamReadGroupOptions options) {
        String[] arguments = options.toArgs(group, consumer, keysAndIds);
        return commandManager.submitNewCommand(XReadGroup, arguments, this::handleXReadResponse);
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
                XPending, new String[] {key, group}, this::handleArrayOrNullResponse);
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
                        new String[] {JUST_ID_REDIS_API});
        return commandManager.submitNewCommand(
                XClaim, args, response -> castArray(handleArrayResponse(response), String.class));
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
                        new String[] {JUST_ID_REDIS_API});
        return commandManager.submitNewCommand(
                XClaim, args, response -> castArray(handleArrayResponse(response), String.class));
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
        return commandManager.submitNewCommand(
                Type, new GlideString[] {key}, this::handleStringResponse);
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
        return commandManager.submitNewCommand(
                BLPop, arguments, response -> castArray(handleArrayOrNullResponse(response), String.class));
    }

    @Override
    public CompletableFuture<String[]> brpop(@NonNull String[] keys, double timeout) {
        String[] arguments = ArrayUtils.add(keys, Double.toString(timeout));
        return commandManager.submitNewCommand(
                BRPop, arguments, response -> castArray(handleArrayOrNullResponse(response), String.class));
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
    public CompletableFuture<String[]> zrange(@NonNull String key, @NonNull RangeQuery rangeQuery) {
        return zrange(key, rangeQuery, false);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zrangeWithScores(
            @NonNull String key, @NonNull ScoredRangeQuery rangeQuery, boolean reverse) {
        String[] arguments = RangeOptions.createZRangeArgs(key, rangeQuery, reverse, true);

        return commandManager.submitNewCommand(ZRange, arguments, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zrangeWithScores(
            @NonNull String key, @NonNull ScoredRangeQuery rangeQuery) {
        return zrangeWithScores(key, rangeQuery, false);
    }

    @Override
    public CompletableFuture<Object[]> zmpop(@NonNull String[] keys, @NonNull ScoreFilter modifier) {
        String[] arguments =
                concatenateArrays(
                        new String[] {Integer.toString(keys.length)}, keys, new String[] {modifier.toString()});
        return commandManager.submitNewCommand(ZMPop, arguments, this::handleArrayOrNullResponse);
    }

    @Override
    public CompletableFuture<Object[]> zmpop(
            @NonNull String[] keys, @NonNull ScoreFilter modifier, long count) {
        String[] arguments =
                concatenateArrays(
                        new String[] {Integer.toString(keys.length)},
                        keys,
                        new String[] {modifier.toString(), COUNT_REDIS_API, Long.toString(count)});
        return commandManager.submitNewCommand(ZMPop, arguments, this::handleArrayOrNullResponse);
    }

    @Override
    public CompletableFuture<Object[]> bzmpop(
            @NonNull String[] keys, @NonNull ScoreFilter modifier, double timeout) {
        String[] arguments =
                concatenateArrays(
                        new String[] {Double.toString(timeout), Integer.toString(keys.length)},
                        keys,
                        new String[] {modifier.toString()});
        return commandManager.submitNewCommand(BZMPop, arguments, this::handleArrayOrNullResponse);
    }

    @Override
    public CompletableFuture<Object[]> bzmpop(
            @NonNull String[] keys, @NonNull ScoreFilter modifier, double timeout, long count) {
        String[] arguments =
                concatenateArrays(
                        new String[] {Double.toString(timeout), Integer.toString(keys.length)},
                        keys,
                        new String[] {modifier.toString(), COUNT_REDIS_API, Long.toString(count)});
        return commandManager.submitNewCommand(BZMPop, arguments, this::handleArrayOrNullResponse);
    }

    @Override
    public CompletableFuture<Long> pfadd(@NonNull String key, @NonNull String[] elements) {
        String[] arguments = ArrayUtils.addFirst(elements, key);
        return commandManager.submitNewCommand(PfAdd, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> pfadd(@NonNull GlideString key, @NonNull GlideString[] elements) {
        GlideString[] arguments = ArrayUtils.addFirst(elements, key);
        return commandManager.submitNewCommand(PfAdd, arguments, this::handleLongResponse);
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
                concatenateArrays(
                        new GlideString[] {key},
                        options.toGlideStringArgs(),
                        mapGeoDataToGlideStringArray(membersToGeospatialData));
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
                        new String[] {direction.toString(), COUNT_FOR_LIST_REDIS_API, Long.toString(count)});
        return commandManager.submitNewCommand(
                LMPop,
                arguments,
                response -> castMapOfArrays(handleMapOrNullResponse(response), String.class));
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
    public CompletableFuture<Map<String, String[]>> blmpop(
            @NonNull String[] keys, @NonNull ListDirection direction, long count, double timeout) {
        String[] arguments =
                concatenateArrays(
                        new String[] {Double.toString(timeout), Long.toString(keys.length)},
                        keys,
                        new String[] {direction.toString(), COUNT_FOR_LIST_REDIS_API, Long.toString(count)});
        return commandManager.submitNewCommand(
                BLMPop,
                arguments,
                response -> castMapOfArrays(handleMapOrNullResponse(response), String.class));
    }

    @Override
    public CompletableFuture<Map<String, String[]>> blmpop(
            @NonNull String[] keys, @NonNull ListDirection direction, double timeout) {
        String[] arguments =
                concatenateArrays(
                        new String[] {Double.toString(timeout), Long.toString(keys.length)},
                        keys,
                        new String[] {direction.toString()});
        return commandManager.submitNewCommand(
                BLMPop,
                arguments,
                response -> castMapOfArrays(handleMapOrNullResponse(response), String.class));
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
        return commandManager.submitNewCommand(BLMove, arguments, this::handleStringOrNullResponse);
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
        return commandManager.submitNewCommand(
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
    public CompletableFuture<Set<String>> spopCount(@NonNull String key, long count) {
        String[] arguments = new String[] {key, Long.toString(count)};
        return commandManager.submitNewCommand(SPop, arguments, this::handleSetResponse);
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
        GlideString[] arguments = ArrayUtils.addFirst(keys, gs(Long.toString(keys.length)));
        return commandManager.submitNewCommand(SInterCard, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> sintercard(@NonNull String[] keys, long limit) {
        String[] arguments =
                concatenateArrays(
                        new String[] {Long.toString(keys.length)},
                        keys,
                        new String[] {SET_LIMIT_REDIS_API, Long.toString(limit)});
        return commandManager.submitNewCommand(SInterCard, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> sintercard(@NonNull GlideString[] keys, long limit) {
        GlideString[] arguments =
                concatenateArrays(
                        new GlideString[] {gs(Long.toString(keys.length))},
                        keys,
                        new GlideString[] {gs(SET_LIMIT_REDIS_API), gs(Long.toString(limit))});
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
        return commandManager.submitNewCommand(FCall, args, this::handleObjectOrNullResponse);
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
        return commandManager.submitNewCommand(FCallReadOnly, args, this::handleObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<Boolean> copy(
            @NonNull String source, @NonNull String destination, boolean replace) {
        String[] arguments = new String[] {source, destination};
        if (replace) {
            arguments = ArrayUtils.add(arguments, REPLACE_REDIS_API);
        }
        return commandManager.submitNewCommand(Copy, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> copy(
            @NonNull GlideString source, @NonNull GlideString destination, boolean replace) {
        GlideString[] arguments = new GlideString[] {source, destination};
        if (replace) {
            arguments = ArrayUtils.add(arguments, gs(REPLACE_REDIS_API));
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
    public CompletableFuture<Boolean> msetnx(@NonNull Map<String, String> keyValueMap) {
        String[] args = convertMapToKeyValueStringArray(keyValueMap);
        return commandManager.submitNewCommand(MSetNX, args, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<String> lcs(@NonNull String key1, @NonNull String key2) {
        String[] arguments = new String[] {key1, key2};
        return commandManager.submitNewCommand(LCS, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Long> lcsLen(@NonNull String key1, @NonNull String key2) {
        String[] arguments = new String[] {key1, key2, LEN_REDIS_API};
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
            @NonNull String key1, @NonNull String key2, long minMatchLen) {
        String[] arguments =
                new String[] {
                    key1, key2, IDX_COMMAND_STRING, MINMATCHLEN_COMMAND_STRING, String.valueOf(minMatchLen)
                };
        return commandManager.submitNewCommand(
                LCS, arguments, response -> handleLcsIdxResponse(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<Map<String, Object>> lcsIdxWithMatchLen(
            @NonNull String key1, @NonNull String key2) {
        String[] arguments = new String[] {key1, key2, IDX_COMMAND_STRING, WITHMATCHLEN_COMMAND_STRING};
        return commandManager.submitNewCommand(LCS, arguments, this::handleMapResponse);
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
    public CompletableFuture<String> publish(@NonNull String channel, @NonNull String message) {
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
    // rather than doing it in the Rust code using JNI calls (performance)
    private Object convertByteArrayToGlideString(Object o) {
        if (o == null) return o;

        if (o instanceof byte[]) {
            o = GlideString.of((byte[]) o);
        } else if (o.getClass().isArray()) {
            var array = (Object[]) o;
            for (var i = 0; i < array.length; i++) {
                array[i] = convertByteArrayToGlideString(array[i]);
            }
        } else if (o instanceof Set) {
            var set = (Set<?>) o;
            o = set.stream().map(this::convertByteArrayToGlideString).collect(Collectors.toSet());
        } else if (o instanceof Map) {
            var map = (Map<?, ?>) o;
            o =
                    map.entrySet().stream()
                            .collect(
                                    HashMap::new,
                                    (m, e) ->
                                            m.put(
                                                    convertByteArrayToGlideString(e.getKey()),
                                                    convertByteArrayToGlideString(e.getValue())),
                                    HashMap::putAll);
        }
        return o;
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
        GlideString[] arguments = restoreOptions.toArgs(key, ttl, value);
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
                concatenateArrays(new GlideString[] {key, cursor}, sScanOptions.toGlideStringArgs());
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
                concatenateArrays(new GlideString[] {key, cursor}, zScanOptions.toGlideStringArgs());
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
                concatenateArrays(new GlideString[] {key, cursor}, hScanOptions.toGlideStringArgs());
        return commandManager.submitNewCommand(HScan, arguments, this::handleArrayOrNullResponseBinary);
    }

    @Override
    public CompletableFuture<Long> wait(long numreplicas, long timeout) {
        return commandManager.submitNewCommand(
                Wait,
                new String[] {Long.toString(numreplicas), Long.toString(timeout)},
                this::handleLongResponse);
    }
}
