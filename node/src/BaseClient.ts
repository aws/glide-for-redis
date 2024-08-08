/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */
import {
    DEFAULT_TIMEOUT_IN_MILLISECONDS,
    Script,
    StartSocketConnection,
    valueFromSplitPointer,
} from "glide-rs";
import * as net from "net";
import { Buffer, BufferWriter, Reader, Writer } from "protobufjs";
import {
    AggregationType,
    BaseScanOptions,
    BitFieldGet,
    BitFieldIncrBy, // eslint-disable-line @typescript-eslint/no-unused-vars
    BitFieldOverflow, // eslint-disable-line @typescript-eslint/no-unused-vars
    BitFieldSet, // eslint-disable-line @typescript-eslint/no-unused-vars
    BitFieldSubCommands,
    BitOffset, // eslint-disable-line @typescript-eslint/no-unused-vars
    BitOffsetMultiplier, // eslint-disable-line @typescript-eslint/no-unused-vars
    BitOffsetOptions,
    BitmapIndexType,
    BitwiseOperation,
    CoordOrigin, // eslint-disable-line @typescript-eslint/no-unused-vars
    ExpireOptions,
    GeoAddOptions,
    GeoBoxShape, // eslint-disable-line @typescript-eslint/no-unused-vars
    GeoCircleShape, // eslint-disable-line @typescript-eslint/no-unused-vars
    GeoSearchResultOptions,
    GeoSearchShape,
    GeoSearchStoreResultOptions,
    GeoUnit,
    GeospatialData,
    InsertPosition,
    KeyWeight, // eslint-disable-line @typescript-eslint/no-unused-vars
    LPosOptions,
    ListDirection,
    MemberOrigin, // eslint-disable-line @typescript-eslint/no-unused-vars
    RangeByIndex,
    RangeByLex,
    RangeByScore,
    ReturnTypeXinfoStream,
    ScoreBoundary,
    ScoreFilter,
    SearchOrigin,
    SetOptions,
    StreamAddOptions,
    StreamGroupOptions,
    StreamReadOptions,
    StreamTrimOptions,
    ZAddOptions,
    createAppend,
    createBLMPop,
    createBLMove,
    createBLPop,
    createBRPop,
    createBZMPop,
    createBZPopMax,
    createBZPopMin,
    createBitCount,
    createBitField,
    createBitOp,
    createBitPos,
    createDecr,
    createDecrBy,
    createDel,
    createExists,
    createExpire,
    createExpireAt,
    createExpireTime,
    createFCall,
    createFCallReadOnly,
    createGeoAdd,
    createGeoDist,
    createGeoHash,
    createGeoPos,
    createGeoSearch,
    createGeoSearchStore,
    createGet,
    createGetBit,
    createGetDel,
    createGetRange,
    createHDel,
    createHExists,
    createHGet,
    createHGetAll,
    createHIncrBy,
    createHIncrByFloat,
    createHLen,
    createHMGet,
    createHScan,
    createHRandField,
    createHSet,
    createHSetNX,
    createHStrlen,
    createHVals,
    createIncr,
    createIncrBy,
    createIncrByFloat,
    createLCS,
    createLIndex,
    createLInsert,
    createLLen,
    createLMPop,
    createLMove,
    createLPop,
    createLPos,
    createLPush,
    createLPushX,
    createLRange,
    createLRem,
    createLSet,
    createLTrim,
    createMGet,
    createMSet,
    createMSetNX,
    createObjectEncoding,
    createObjectFreq,
    createObjectIdletime,
    createObjectRefcount,
    createPExpire,
    createPExpireAt,
    createPExpireTime,
    createPTTL,
    createPersist,
    createPfAdd,
    createPfCount,
    createPfMerge,
    createPubSubChannels,
    createPubSubNumPat,
    createPubSubNumSub,
    createRPop,
    createRPush,
    createRPushX,
    createRename,
    createRenameNX,
    createSAdd,
    createSCard,
    createSDiff,
    createSDiffStore,
    createSInter,
    createSInterCard,
    createSInterStore,
    createSIsMember,
    createSMIsMember,
    createSMembers,
    createSMove,
    createSPop,
    createSRandMember,
    createSRem,
    createSUnion,
    createSUnionStore,
    createSet,
    createSetBit,
    createSetRange,
    createStrlen,
    createTTL,
    createTouch,
    createType,
    createUnlink,
    createWatch,
    createXAdd,
    createXDel,
    createXGroupCreate,
    createXGroupDestroy,
    createXInfoConsumers,
    createXInfoStream,
    createXGroupCreateConsumer,
    createXGroupDelConsumer,
    createXLen,
    createXRead,
    createXTrim,
    createZAdd,
    createZCard,
    createZCount,
    createZDiff,
    createZDiffStore,
    createZDiffWithScores,
    createZIncrBy,
    createZInterCard,
    createZInterstore,
    createZLexCount,
    createZMPop,
    createZMScore,
    createZPopMax,
    createZPopMin,
    createZRandMember,
    createZRange,
    createZRangeWithScores,
    createZRank,
    createZRem,
    createZRemRangeByLex,
    createZRemRangeByRank,
    createZRemRangeByScore,
    createZRevRank,
    createZRevRankWithScore,
    createZScan,
    createZScore,
    StreamClaimOptions,
    createXClaim,
} from "./Commands";
import {
    ClosingError,
    ConfigurationError,
    ConnectionError,
    ExecAbortError,
    RedisError,
    RequestError,
    TimeoutError,
} from "./Errors";
import { GlideClientConfiguration } from "./GlideClient";
import { GlideClusterClientConfiguration } from "./GlideClusterClient";
import { Logger } from "./Logger";
import {
    command_request,
    connection_request,
    response,
} from "./ProtobufMessage";

/* eslint-disable-next-line @typescript-eslint/no-explicit-any */
type PromiseFunction = (value?: any) => void;
type ErrorFunction = (error: RedisError) => void;
export type ReturnTypeRecord = { [key: string]: ReturnType };
export type ReturnTypeMap = Map<string, ReturnType>;
export type ReturnTypeAttribute = {
    value: ReturnType;
    attributes: ReturnTypeRecord;
};
export enum ProtocolVersion {
    /** Use RESP2 to communicate with the server nodes. */
    RESP2 = connection_request.ProtocolVersion.RESP2,
    /** Use RESP3 to communicate with the server nodes. */
    RESP3 = connection_request.ProtocolVersion.RESP3,
}
export type ReturnType =
    | "OK"
    | string
    | number
    | null
    | boolean
    | bigint
    | Buffer
    | Set<ReturnType>
    | ReturnTypeRecord
    | ReturnTypeMap
    | ReturnTypeAttribute
    | ReturnType[];

/** Represents the credentials for connecting to a server. */
export type RedisCredentials = {
    /**
     * The username that will be used for authenticating connections to the Redis servers.
     * If not supplied, "default" will be used.
     */
    username?: string;
    /**
     * The password that will be used for authenticating connections to the Redis servers.
     */
    password: string;
};

/** Represents the client's read from strategy. */
export type ReadFrom =
    /** Always get from primary, in order to get the freshest data.*/
    | "primary"
    /** Spread the requests between all replicas in a round robin manner.
        If no replica is available, route the requests to the primary.*/
    | "preferReplica";

/**
 * Configuration settings for creating a client. Shared settings for standalone and cluster clients.
 */
export type BaseClientConfiguration = {
    /**
     * DNS Addresses and ports of known nodes in the cluster.
     * If the server is in cluster mode the list can be partial, as the client will attempt to map out the cluster and find all nodes.
     * If the server is in standalone mode, only nodes whose addresses were provided will be used by the client.
     *
     * @example
     * ```typescript
     * configuration.addresses =
     * [
     *   { address: sample-address-0001.use1.cache.amazonaws.com, port:6378 },
     *   { address: sample-address-0002.use2.cache.amazonaws.com }
     *   { address: sample-address-0003.use2.cache.amazonaws.com, port:6380 }
     * ]
     * ```
     */
    addresses: {
        host: string;
        /**
         * If port isn't supplied, 6379 will be used
         */
        port?: number;
    }[];
    /**
     * True if communication with the cluster should use Transport Level Security.
     * Should match the TLS configuration of the server/cluster,
     * otherwise the connection attempt will fail.
     */
    useTLS?: boolean;
    /**
     * Credentials for authentication process.
     * If none are set, the client will not authenticate itself with the server.
     */
    credentials?: RedisCredentials;
    /**
     * The duration in milliseconds that the client should wait for a request to complete.
     * This duration encompasses sending the request, awaiting for a response from the server, and any required reconnections or retries.
     * If the specified timeout is exceeded for a pending request, it will result in a timeout error.
     * If not set, a default value will be used.
     * Value must be an integer.
     */
    requestTimeout?: number;
    /**
     * Represents the client's read from strategy.
     * If not set, `Primary` will be used.
     */
    readFrom?: ReadFrom;
    /**
     * Choose the Redis protocol to be used with the server.
     * If not set, `RESP3` will be used.
     */
    protocol?: ProtocolVersion;
    /**
     * Client name to be used for the client. Will be used with CLIENT SETNAME command during connection establishment.
     */
    clientName?: string;
};

export type ScriptOptions = {
    /**
     * The keys that are used in the script.
     */
    keys?: (string | Uint8Array)[];
    /**
     * The arguments for the script.
     */
    args?: (string | Uint8Array)[];
};

function getRequestErrorClass(
    type: response.RequestErrorType | null | undefined,
): typeof RequestError {
    if (type === response.RequestErrorType.Disconnect) {
        return ConnectionError;
    }

    if (type === response.RequestErrorType.ExecAbort) {
        return ExecAbortError;
    }

    if (type === response.RequestErrorType.Timeout) {
        return TimeoutError;
    }

    if (type === response.RequestErrorType.Unspecified) {
        return RequestError;
    }

    return RequestError;
}

export type PubSubMsg = {
    message: string;
    channel: string;
    pattern?: string | null;
};
export class BaseClient {
    private socket: net.Socket;
    private readonly promiseCallbackFunctions: [
        PromiseFunction,
        ErrorFunction,
    ][] = [];
    private readonly availableCallbackSlots: number[] = [];
    private requestWriter = new BufferWriter();
    private writeInProgress = false;
    private remainingReadData: Uint8Array | undefined;
    private readonly requestTimeout: number; // Timeout in milliseconds
    private isClosed = false;
    private readonly pubsubFutures: [PromiseFunction, ErrorFunction][] = [];
    private pendingPushNotification: response.Response[] = [];
    private config: BaseClientConfiguration | undefined;

    protected configurePubsub(
        options: GlideClusterClientConfiguration | GlideClientConfiguration,
        configuration: connection_request.IConnectionRequest,
    ) {
        if (options.pubsubSubscriptions) {
            if (options.protocol == ProtocolVersion.RESP2) {
                throw new ConfigurationError(
                    "PubSub subscriptions require RESP3 protocol, but RESP2 was configured.",
                );
            }

            const { context, callback } = options.pubsubSubscriptions;

            if (context && !callback) {
                throw new ConfigurationError(
                    "PubSub subscriptions with a context require a callback function to be configured.",
                );
            }

            configuration.pubsubSubscriptions =
                connection_request.PubSubSubscriptions.create({});

            for (const [channelType, channelsPatterns] of Object.entries(
                options.pubsubSubscriptions.channelsAndPatterns,
            )) {
                let entry =
                    configuration.pubsubSubscriptions!
                        .channelsOrPatternsByType![parseInt(channelType)];

                if (!entry) {
                    entry = connection_request.PubSubChannelsOrPatterns.create({
                        channelsOrPatterns: [],
                    });
                    configuration.pubsubSubscriptions!.channelsOrPatternsByType![
                        parseInt(channelType)
                    ] = entry;
                }

                for (const channelPattern of channelsPatterns) {
                    entry.channelsOrPatterns!.push(Buffer.from(channelPattern));
                }
            }
        }
    }
    private handleReadData(data: Buffer) {
        const buf = this.remainingReadData
            ? Buffer.concat([this.remainingReadData, data])
            : data;
        let lastPos = 0;
        const reader = Reader.create(buf);

        while (reader.pos < reader.len) {
            lastPos = reader.pos;
            let message = undefined;

            try {
                message = response.Response.decodeDelimited(reader);
            } catch (err) {
                if (err instanceof RangeError) {
                    // Partial response received, more data is required
                    this.remainingReadData = buf.slice(lastPos);
                    return;
                } else {
                    // Unhandled error
                    const err_message = `Failed to decode the response: ${err}`;
                    Logger.log("error", "connection", err_message);
                    this.close(err_message);
                    return;
                }
            }

            if (message.isPush) {
                this.processPush(message);
            } else {
                this.processResponse(message);
            }
        }

        this.remainingReadData = undefined;
    }

    processResponse(message: response.Response) {
        if (message.closingError != null) {
            this.close(message.closingError);
            return;
        }

        const [resolve, reject] =
            this.promiseCallbackFunctions[message.callbackIdx];
        this.availableCallbackSlots.push(message.callbackIdx);

        if (message.requestError != null) {
            const errorType = getRequestErrorClass(message.requestError.type);
            reject(new errorType(message.requestError.message ?? undefined));
        } else if (message.respPointer != null) {
            const pointer = message.respPointer;

            if (typeof pointer === "number") {
                // TODO: change according to https://github.com/valkey-io/valkey-glide/pull/2052
                resolve(valueFromSplitPointer(0, pointer, true));
            } else {
                // TODO: change according to https://github.com/valkey-io/valkey-glide/pull/2052
                resolve(valueFromSplitPointer(pointer.high, pointer.low, true));
            }
        } else if (message.constantResponse === response.ConstantResponse.OK) {
            resolve("OK");
        } else {
            resolve(null);
        }
    }

    processPush(response: response.Response) {
        if (response.closingError != null || !response.respPointer) {
            const errMsg = response.closingError
                ? response.closingError
                : "Client Error - push notification without resp_pointer";

            this.close(errMsg);
            return;
        }

        const [callback, context] = this.getPubsubCallbackAndContext(
            this.config!,
        );

        if (callback) {
            const pubsubMessage =
                this.notificationToPubSubMessageSafe(response);

            if (pubsubMessage) {
                callback(pubsubMessage, context);
            }
        } else {
            this.pendingPushNotification.push(response);
            this.completePubSubFuturesSafe();
        }
    }

    /**
     * @internal
     */
    protected constructor(
        socket: net.Socket,
        options?: BaseClientConfiguration,
    ) {
        // if logger has been initialized by the external-user on info level this log will be shown
        Logger.log("info", "Client lifetime", `construct client`);
        this.config = options;
        this.requestTimeout =
            options?.requestTimeout ?? DEFAULT_TIMEOUT_IN_MILLISECONDS;
        this.socket = socket;
        this.socket
            .on("data", (data) => this.handleReadData(data))
            .on("error", (err) => {
                console.error(`Server closed: ${err}`);
                this.close();
            });
    }

    private getCallbackIndex(): number {
        return (
            this.availableCallbackSlots.pop() ??
            this.promiseCallbackFunctions.length
        );
    }

    private writeBufferedRequestsToSocket() {
        this.writeInProgress = true;
        const requests = this.requestWriter.finish();
        this.requestWriter.reset();

        this.socket.write(requests, undefined, () => {
            if (this.requestWriter.len > 0) {
                this.writeBufferedRequestsToSocket();
            } else {
                this.writeInProgress = false;
            }
        });
    }

    /**
     * @internal
     */
    protected createWritePromise<T>(
        command:
            | command_request.Command
            | command_request.Command[]
            | command_request.ScriptInvocation,
        route?: command_request.Routes,
    ): Promise<T> {
        if (this.isClosed) {
            throw new ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client.",
            );
        }

        return new Promise((resolve, reject) => {
            const callbackIndex = this.getCallbackIndex();
            this.promiseCallbackFunctions[callbackIndex] = [resolve, reject];
            this.writeOrBufferCommandRequest(callbackIndex, command, route);
        });
    }

    private writeOrBufferCommandRequest(
        callbackIdx: number,
        command:
            | command_request.Command
            | command_request.Command[]
            | command_request.ScriptInvocation,
        route?: command_request.Routes,
    ) {
        const message = Array.isArray(command)
            ? command_request.CommandRequest.create({
                  callbackIdx,
                  transaction: command_request.Transaction.create({
                      commands: command,
                  }),
              })
            : command instanceof command_request.Command
              ? command_request.CommandRequest.create({
                    callbackIdx,
                    singleCommand: command,
                })
              : command_request.CommandRequest.create({
                    callbackIdx,
                    scriptInvocation: command,
                });
        message.route = route;

        this.writeOrBufferRequest(
            message,
            (message: command_request.CommandRequest, writer: Writer) => {
                command_request.CommandRequest.encodeDelimited(message, writer);
            },
        );
    }

    private writeOrBufferRequest<TRequest>(
        message: TRequest,
        encodeDelimited: (message: TRequest, writer: Writer) => void,
    ) {
        encodeDelimited(message, this.requestWriter);

        if (this.writeInProgress) {
            return;
        }

        this.writeBufferedRequestsToSocket();
    }

    // Define a common function to process the result of a transaction with set commands
    /**
     * @internal
     */
    protected processResultWithSetCommands(
        result: ReturnType[] | null,
        setCommandsIndexes: number[],
    ): ReturnType[] | null {
        if (result === null) {
            return null;
        }

        for (const index of setCommandsIndexes) {
            result[index] = new Set<ReturnType>(result[index] as ReturnType[]);
        }

        return result;
    }

    cancelPubSubFuturesWithExceptionSafe(exception: ConnectionError): void {
        while (this.pubsubFutures.length > 0) {
            const nextFuture = this.pubsubFutures.shift();

            if (nextFuture) {
                const [, reject] = nextFuture;
                reject(exception);
            }
        }
    }

    isPubsubConfigured(
        config: GlideClientConfiguration | GlideClusterClientConfiguration,
    ): boolean {
        return !!config.pubsubSubscriptions;
    }

    getPubsubCallbackAndContext(
        config: GlideClientConfiguration | GlideClusterClientConfiguration,
        /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
    ): [((msg: PubSubMsg, context: any) => void) | null | undefined, any] {
        if (config.pubsubSubscriptions) {
            return [
                config.pubsubSubscriptions.callback,
                config.pubsubSubscriptions.context,
            ];
        }

        return [null, null];
    }

    public getPubSubMessage(): Promise<PubSubMsg> {
        if (this.isClosed) {
            throw new ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client.",
            );
        }

        if (!this.isPubsubConfigured(this.config!)) {
            throw new ConfigurationError(
                "The operation will never complete since there was no pubsbub subscriptions applied to the client.",
            );
        }

        if (this.getPubsubCallbackAndContext(this.config!)[0]) {
            throw new ConfigurationError(
                "The operation will never complete since messages will be passed to the configured callback.",
            );
        }

        return new Promise((resolve, reject) => {
            this.pubsubFutures.push([resolve, reject]);
            this.completePubSubFuturesSafe();
        });
    }

    public tryGetPubSubMessage(): PubSubMsg | null {
        if (this.isClosed) {
            throw new ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client.",
            );
        }

        if (!this.isPubsubConfigured(this.config!)) {
            throw new ConfigurationError(
                "The operation will never complete since there was no pubsbub subscriptions applied to the client.",
            );
        }

        if (this.getPubsubCallbackAndContext(this.config!)[0]) {
            throw new ConfigurationError(
                "The operation will never complete since messages will be passed to the configured callback.",
            );
        }

        let msg: PubSubMsg | null = null;
        this.completePubSubFuturesSafe();

        while (this.pendingPushNotification.length > 0 && !msg) {
            const pushNotification = this.pendingPushNotification.shift()!;
            msg = this.notificationToPubSubMessageSafe(pushNotification);
        }

        return msg;
    }
    notificationToPubSubMessageSafe(
        pushNotification: response.Response,
    ): PubSubMsg | null {
        let msg: PubSubMsg | null = null;
        const responsePointer = pushNotification.respPointer;
        let nextPushNotificationValue: Record<string, unknown> = {};

        if (responsePointer) {
            if (typeof responsePointer !== "number") {
                nextPushNotificationValue = valueFromSplitPointer(
                    responsePointer.high,
                    responsePointer.low,
                    // TODO: change according to https://github.com/valkey-io/valkey-glide/pull/2052
                    true,
                ) as Record<string, unknown>;
            } else {
                nextPushNotificationValue = valueFromSplitPointer(
                    0,
                    responsePointer,
                    // TODO: change according to https://github.com/valkey-io/valkey-glide/pull/2052
                    true,
                ) as Record<string, unknown>;
            }

            const messageKind = nextPushNotificationValue["kind"];

            if (messageKind === "Disconnect") {
                Logger.log(
                    "warn",
                    "disconnect notification",
                    "Transport disconnected, messages might be lost",
                );
            } else if (
                messageKind === "Message" ||
                messageKind === "PMessage" ||
                messageKind === "SMessage"
            ) {
                const values = nextPushNotificationValue["values"] as string[];

                if (messageKind === "PMessage") {
                    msg = {
                        message: values[2],
                        channel: values[1],
                        pattern: values[0],
                    };
                } else {
                    msg = {
                        message: values[1],
                        channel: values[0],
                        pattern: null,
                    };
                }
            } else if (
                messageKind === "PSubscribe" ||
                messageKind === "Subscribe" ||
                messageKind === "SSubscribe" ||
                messageKind === "Unsubscribe" ||
                messageKind === "SUnsubscribe" ||
                messageKind === "PUnsubscribe"
            ) {
                // pass
            } else {
                Logger.log(
                    "error",
                    "unknown notification",
                    `Unknown notification: '${messageKind}'`,
                );
            }
        }

        return msg;
    }
    completePubSubFuturesSafe() {
        while (
            this.pendingPushNotification.length > 0 &&
            this.pubsubFutures.length > 0
        ) {
            const nextPushNotification = this.pendingPushNotification.shift()!;
            const pubsubMessage =
                this.notificationToPubSubMessageSafe(nextPushNotification);

            if (pubsubMessage) {
                const [resolve] = this.pubsubFutures.shift()!;
                resolve(pubsubMessage);
            }
        }
    }

    /** Get the value associated with the given key, or null if no such value exists.
     * See https://valkey.io/commands/get/ for details.
     *
     * @param key - The key to retrieve from the database.
     * @returns If `key` exists, returns the value of `key` as a string. Otherwise, return null.
     *
     * @example
     * ```typescript
     * // Example usage of get method to retrieve the value of a key
     * const result = await client.get("key");
     * console.log(result); // Output: 'value'
     * ```
     */
    public get(key: string): Promise<string | null> {
        return this.createWritePromise(createGet(key));
    }

    /**
     * Gets a string value associated with the given `key`and deletes the key.
     *
     * See https://valkey.io/commands/getdel/ for details.
     *
     * @param key - The key to retrieve from the database.
     * @returns If `key` exists, returns the `value` of `key`. Otherwise, return `null`.
     *
     * @example
     * ```typescript
     * const result = client.getdel("key");
     * console.log(result); // Output: 'value'
     *
     * const value = client.getdel("key");  // value is null
     * ```
     */
    public getdel(key: string): Promise<string | null> {
        return this.createWritePromise(createGetDel(key));
    }

    /**
     * Returns the substring of the string value stored at `key`, determined by the offsets
     * `start` and `end` (both are inclusive). Negative offsets can be used in order to provide
     * an offset starting from the end of the string. So `-1` means the last character, `-2` the
     * penultimate and so forth. If `key` does not exist, an empty string is returned. If `start`
     * or `end` are out of range, returns the substring within the valid range of the string.
     *
     * See https://valkey.io/commands/getrange/ for details.
     *
     * @param key - The key of the string.
     * @param start - The starting offset.
     * @param end - The ending offset.
     * @returns A substring extracted from the value stored at `key`.
     *
     * @example
     * ```typescript
     * await client.set("mykey", "This is a string")
     * let result = await client.getrange("mykey", 0, 3)
     * console.log(result); // Output: "This"
     * result = await client.getrange("mykey", -3, -1)
     * console.log(result); // Output: "ing" - extracted last 3 characters of a string
     * result = await client.getrange("mykey", 0, 100)
     * console.log(result); // Output: "This is a string"
     * result = await client.getrange("mykey", 5, 6)
     * console.log(result); // Output: ""
     * ```
     */
    public async getrange(
        key: string,
        start: number,
        end: number,
    ): Promise<string | null> {
        return this.createWritePromise(createGetRange(key, start, end));
    }

    /** Set the given key with the given value. Return value is dependent on the passed options.
     * See https://valkey.io/commands/set/ for details.
     *
     * @param key - The key to store.
     * @param value - The value to store with the given key.
     * @param options - The set options.
     * @returns - If the value is successfully set, return OK.
     * If value isn't set because of `onlyIfExists` or `onlyIfDoesNotExist` conditions, return null.
     * If `returnOldValue` is set, return the old value as a string.
     *
     * @example
     * ```typescript
     * // Example usage of set method to set a key-value pair
     * const result = await client.set("my_key", "my_value");
     * console.log(result); // Output: 'OK'
     *
     * // Example usage of set method with conditional options and expiration
     * const result2 = await client.set("key", "new_value", {conditionalSet: "onlyIfExists", expiry: { type: "seconds", count: 5 }});
     * console.log(result2); // Output: 'OK' - Set "new_value" to "key" only if "key" already exists, and set the key expiration to 5 seconds.
     *
     * // Example usage of set method with conditional options and returning old value
     * const result3 = await client.set("key", "value", {conditionalSet: "onlyIfDoesNotExist", returnOldValue: true});
     * console.log(result3); // Output: 'new_value' - Returns the old value of "key".
     *
     * // Example usage of get method to retrieve the value of a key
     * const result4 = await client.get("key");
     * console.log(result4); // Output: 'new_value' - Value wasn't modified back to being "value" because of "NX" flag.
     * ```
     */
    public set(
        key: string | Uint8Array,
        value: string | Uint8Array,
        options?: SetOptions,
    ): Promise<"OK" | string | null> {
        return this.createWritePromise(createSet(key, value, options));
    }

    /** Removes the specified keys. A key is ignored if it does not exist.
     * See https://valkey.io/commands/del/ for details.
     *
     * @param keys - the keys we wanted to remove.
     * @returns the number of keys that were removed.
     *
     * @example
     * ```typescript
     * // Example usage of del method to delete an existing key
     * await client.set("my_key", "my_value");
     * const result = await client.del(["my_key"]);
     * console.log(result); // Output: 1
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of del method for a non-existing key
     * const result = await client.del(["non_existing_key"]);
     * console.log(result); // Output: 0
     * ```
     */
    public del(keys: string[]): Promise<number> {
        return this.createWritePromise(createDel(keys));
    }

    /** Retrieve the values of multiple keys.
     * See https://valkey.io/commands/mget/ for details.
     *
     * @remarks When in cluster mode, the command may route to multiple nodes when `keys` map to different hash slots.
     * @param keys - A list of keys to retrieve values for.
     * @returns A list of values corresponding to the provided keys. If a key is not found,
     * its corresponding value in the list will be null.
     *
     * @example
     * ```typescript
     * // Example usage of mget method to retrieve values of multiple keys
     * await client.set("key1", "value1");
     * await client.set("key2", "value2");
     * const result = await client.mget(["key1", "key2"]);
     * console.log(result); // Output: ['value1', 'value2']
     * ```
     */
    public mget(keys: string[]): Promise<(string | null)[]> {
        return this.createWritePromise(createMGet(keys));
    }

    /** Set multiple keys to multiple values in a single operation.
     * See https://valkey.io/commands/mset/ for details.
     *
     * @remarks When in cluster mode, the command may route to multiple nodes when keys in `keyValueMap` map to different hash slots.
     * @param keyValueMap - A key-value map consisting of keys and their respective values to set.
     * @returns always "OK".
     *
     * @example
     * ```typescript
     * // Example usage of mset method to set values for multiple keys
     * const result = await client.mset({"key1": "value1", "key2": "value2"});
     * console.log(result); // Output: 'OK'
     * ```
     */
    public mset(keyValueMap: Record<string, string>): Promise<"OK"> {
        return this.createWritePromise(createMSet(keyValueMap));
    }

    /**
     * Sets multiple keys to values if the key does not exist. The operation is atomic, and if one or
     * more keys already exist, the entire operation fails.
     *
     * See https://valkey.io/commands/msetnx/ for more details.
     *
     * @remarks When in cluster mode, all keys in `keyValueMap` must map to the same hash slot.
     * @param keyValueMap - A key-value map consisting of keys and their respective values to set.
     * @returns `true` if all keys were set. `false` if no key was set.
     *
     * @example
     * ```typescript
     * const result1 = await client.msetnx({"key1": "value1", "key2": "value2"});
     * console.log(result1); // Output: `true`
     *
     * const result2 = await client.msetnx({"key2": "value4", "key3": "value5"});
     * console.log(result2); // Output: `false`
     * ```
     */
    public async msetnx(keyValueMap: Record<string, string>): Promise<boolean> {
        return this.createWritePromise(createMSetNX(keyValueMap));
    }

    /** Increments the number stored at `key` by one. If `key` does not exist, it is set to 0 before performing the operation.
     * See https://valkey.io/commands/incr/ for details.
     *
     * @param key - The key to increment its value.
     * @returns the value of `key` after the increment.
     *
     * @example
     * ```typescript
     * // Example usage of incr method to increment the value of a key
     * await client.set("my_counter", "10");
     * const result = await client.incr("my_counter");
     * console.log(result); // Output: 11
     * ```
     */
    public incr(key: string): Promise<number> {
        return this.createWritePromise(createIncr(key));
    }

    /** Increments the number stored at `key` by `amount`. If `key` does not exist, it is set to 0 before performing the operation.
     * See https://valkey.io/commands/incrby/ for details.
     *
     * @param key - The key to increment its value.
     * @param amount - The amount to increment.
     * @returns the value of `key` after the increment.
     *
     * @example
     * ```typescript
     * // Example usage of incrBy method to increment the value of a key by a specified amount
     * await client.set("my_counter", "10");
     * const result = await client.incrBy("my_counter", 5);
     * console.log(result); // Output: 15
     * ```
     */
    public incrBy(key: string, amount: number): Promise<number> {
        return this.createWritePromise(createIncrBy(key, amount));
    }

    /** Increment the string representing a floating point number stored at `key` by `amount`.
     * By using a negative increment value, the result is that the value stored at `key` is decremented.
     * If `key` does not exist, it is set to 0 before performing the operation.
     * See https://valkey.io/commands/incrbyfloat/ for details.
     *
     * @param key - The key to increment its value.
     * @param amount - The amount to increment.
     * @returns the value of `key` after the increment.
     *
     * @example
     * ```typescript
     * // Example usage of incrByFloat method to increment the value of a floating point key by a specified amount
     * await client.set("my_float_counter", "10.5");
     * const result = await client.incrByFloat("my_float_counter", 2.5);
     * console.log(result); // Output: 13.0
     * ```
     */
    public incrByFloat(key: string, amount: number): Promise<number> {
        return this.createWritePromise(createIncrByFloat(key, amount));
    }

    /** Decrements the number stored at `key` by one. If `key` does not exist, it is set to 0 before performing the operation.
     * See https://valkey.io/commands/decr/ for details.
     *
     * @param key - The key to decrement its value.
     * @returns the value of `key` after the decrement.
     *
     * @example
     * ```typescript
     * // Example usage of decr method to decrement the value of a key by 1
     * await client.set("my_counter", "10");
     * const result = await client.decr("my_counter");
     * console.log(result); // Output: 9
     * ```
     */
    public decr(key: string): Promise<number> {
        return this.createWritePromise(createDecr(key));
    }

    /** Decrements the number stored at `key` by `amount`. If `key` does not exist, it is set to 0 before performing the operation.
     * See https://valkey.io/commands/decrby/ for details.
     *
     * @param key - The key to decrement its value.
     * @param amount - The amount to decrement.
     * @returns the value of `key` after the decrement.
     *
     * @example
     * ```typescript
     * // Example usage of decrby method to decrement the value of a key by a specified amount
     * await client.set("my_counter", "10");
     * const result = await client.decrby("my_counter", 5);
     * console.log(result); // Output: 5
     * ```
     */
    public decrBy(key: string, amount: number): Promise<number> {
        return this.createWritePromise(createDecrBy(key, amount));
    }

    /**
     * Perform a bitwise operation between multiple keys (containing string values) and store the result in the
     * `destination`.
     *
     * See https://valkey.io/commands/bitop/ for more details.
     *
     * @remarks When in cluster mode, `destination` and all `keys` must map to the same hash slot.
     * @param operation - The bitwise operation to perform.
     * @param destination - The key that will store the resulting string.
     * @param keys - The list of keys to perform the bitwise operation on.
     * @returns The size of the string stored in `destination`.
     *
     * @example
     * ```typescript
     * await client.set("key1", "A"); // "A" has binary value 01000001
     * await client.set("key2", "B"); // "B" has binary value 01000010
     * const result1 = await client.bitop(BitwiseOperation.AND, "destination", ["key1", "key2"]);
     * console.log(result1); // Output: 1 - The size of the resulting string stored in "destination" is 1.
     *
     * const result2 = await client.get("destination");
     * console.log(result2); // Output: "@" - "@" has binary value 01000000
     * ```
     */
    public bitop(
        operation: BitwiseOperation,
        destination: string,
        keys: string[],
    ): Promise<number> {
        return this.createWritePromise(
            createBitOp(operation, destination, keys),
        );
    }

    /**
     * Returns the bit value at `offset` in the string value stored at `key`. `offset` must be greater than or equal
     * to zero.
     *
     * See https://valkey.io/commands/getbit/ for more details.
     *
     * @param key - The key of the string.
     * @param offset - The index of the bit to return.
     * @returns The bit at the given `offset` of the string. Returns `0` if the key is empty or if the `offset` exceeds
     * the length of the string.
     *
     * @example
     * ```typescript
     * const result = await client.getbit("key", 1);
     * console.log(result); // Output: 1 - The second bit of the string stored at "key" is set to 1.
     * ```
     */
    public getbit(key: string, offset: number): Promise<number> {
        return this.createWritePromise(createGetBit(key, offset));
    }

    /**
     * Sets or clears the bit at `offset` in the string value stored at `key`. The `offset` is a zero-based index, with
     * `0` being the first element of the list, `1` being the next element, and so on. The `offset` must be less than
     * `2^32` and greater than or equal to `0`. If a key is non-existent then the bit at `offset` is set to `value` and
     * the preceding bits are set to `0`.
     *
     * See https://valkey.io/commands/setbit/ for more details.
     *
     * @param key - The key of the string.
     * @param offset - The index of the bit to be set.
     * @param value - The bit value to set at `offset`. The value must be `0` or `1`.
     * @returns The bit value that was previously stored at `offset`.
     *
     * @example
     * ```typescript
     * const result = await client.setbit("key", 1, 1);
     * console.log(result); // Output: 0 - The second bit value was 0 before setting to 1.
     * ```
     */
    public setbit(key: string, offset: number, value: number): Promise<number> {
        return this.createWritePromise(createSetBit(key, offset, value));
    }

    /**
     * Returns the position of the first bit matching the given `bit` value. The optional starting offset
     * `start` is a zero-based index, with `0` being the first byte of the list, `1` being the next byte and so on.
     * The offset can also be a negative number indicating an offset starting at the end of the list, with `-1` being
     * the last byte of the list, `-2` being the penultimate, and so on.
     *
     * See https://valkey.io/commands/bitpos/ for more details.
     *
     * @param key - The key of the string.
     * @param bit - The bit value to match. Must be `0` or `1`.
     * @param start - (Optional) The starting offset. If not supplied, the search will start at the beginning of the string.
     * @returns The position of the first occurrence of `bit` in the binary value of the string held at `key`.
     *      If `start` was provided, the search begins at the offset indicated by `start`.
     *
     * @example
     * ```typescript
     * await client.set("key1", "A1");  // "A1" has binary value 01000001 00110001
     * const result1 = await client.bitpos("key1", 1);
     * console.log(result1); // Output: 1 - The first occurrence of bit value 1 in the string stored at "key1" is at the second position.
     *
     * const result2 = await client.bitpos("key1", 1, -1);
     * console.log(result2); // Output: 10 - The first occurrence of bit value 1, starting at the last byte in the string stored at "key1", is at the eleventh position.
     * ```
     */
    public async bitpos(
        key: string,
        bit: number,
        start?: number,
    ): Promise<number> {
        return this.createWritePromise(createBitPos(key, bit, start));
    }

    /**
     * Returns the position of the first bit matching the given `bit` value. The offsets are zero-based indexes, with
     * `0` being the first element of the list, `1` being the next, and so on. These offsets can also be negative
     * numbers indicating offsets starting at the end of the list, with `-1` being the last element of the list, `-2`
     * being the penultimate, and so on.
     *
     * If you are using Valkey 7.0.0 or above, the optional `indexType` can also be provided to specify whether the
     * `start` and `end` offsets specify BIT or BYTE offsets. If `indexType` is not provided, BYTE offsets
     * are assumed. If BIT is specified, `start=0` and `end=2` means to look at the first three bits. If BYTE is
     * specified, `start=0` and `end=2` means to look at the first three bytes.
     *
     * See https://valkey.io/commands/bitpos/ for more details.
     *
     * @param key - The key of the string.
     * @param bit - The bit value to match. Must be `0` or `1`.
     * @param start - The starting offset.
     * @param end - The ending offset.
     * @param indexType - (Optional) The index offset type. This option can only be specified if you are using Valkey
     *      version 7.0.0 or above. Could be either {@link BitmapIndexType.BYTE} or {@link BitmapIndexType.BIT}. If no
     *      index type is provided, the indexes will be assumed to be byte indexes.
     * @returns The position of the first occurrence from the `start` to the `end` offsets of the `bit` in the binary
     *      value of the string held at `key`.
     *
     * @example
     * ```typescript
     * await client.set("key1", "A12");  // "A12" has binary value 01000001 00110001 00110010
     * const result1 = await client.bitposInterval("key1", 1, 1, -1);
     * console.log(result1); // Output: 10 - The first occurrence of bit value 1 in the second byte to the last byte of the string stored at "key1" is at the eleventh position.
     *
     * const result2 = await client.bitposInterval("key1", 1, 2, 9, BitmapIndexType.BIT);
     * console.log(result2); // Output: 7 - The first occurrence of bit value 1 in the third to tenth bits of the string stored at "key1" is at the eighth position.
     * ```
     */
    public async bitposInterval(
        key: string,
        bit: number,
        start: number,
        end: number,
        indexType?: BitmapIndexType,
    ): Promise<number> {
        return this.createWritePromise(
            createBitPos(key, bit, start, end, indexType),
        );
    }

    /**
     * Reads or modifies the array of bits representing the string that is held at `key` based on the specified
     * `subcommands`.
     *
     * See https://valkey.io/commands/bitfield/ for more details.
     *
     * @param key - The key of the string.
     * @param subcommands - The subcommands to be performed on the binary value of the string at `key`, which could be
     *      any of the following:
     *
     * - {@link BitFieldGet}
     * - {@link BitFieldSet}
     * - {@link BitFieldIncrBy}
     * - {@link BitFieldOverflow}
     *
     * @returns An array of results from the executed subcommands:
     *
     * - {@link BitFieldGet} returns the value in {@link BitOffset} or {@link BitOffsetMultiplier}.
     * - {@link BitFieldSet} returns the old value in {@link BitOffset} or {@link BitOffsetMultiplier}.
     * - {@link BitFieldIncrBy} returns the new value in {@link BitOffset} or {@link BitOffsetMultiplier}.
     * - {@link BitFieldOverflow} determines the behavior of the {@link BitFieldSet} and {@link BitFieldIncrBy}
     *   subcommands when an overflow or underflow occurs. {@link BitFieldOverflow} does not return a value and
     *   does not contribute a value to the array response.
     *
     * @example
     * ```typescript
     * await client.set("key", "A");  // "A" has binary value 01000001
     * const result = await client.bitfield("key", [new BitFieldSet(new UnsignedEncoding(2), new BitOffset(1), 3), new BitFieldGet(new UnsignedEncoding(2), new BitOffset(1))]);
     * console.log(result); // Output: [2, 3] - The old value at offset 1 with an unsigned encoding of 2 was 2. The new value at offset 1 with an unsigned encoding of 2 is 3.
     * ```
     */
    public async bitfield(
        key: string,
        subcommands: BitFieldSubCommands[],
    ): Promise<(number | null)[]> {
        return this.createWritePromise(createBitField(key, subcommands));
    }

    /**
     * Reads the array of bits representing the string that is held at `key` based on the specified `subcommands`.
     *
     * See https://valkey.io/commands/bitfield_ro/ for more details.
     *
     * @param key - The key of the string.
     * @param subcommands - The {@link BitFieldGet} subcommands to be performed.
     * @returns An array of results from the {@link BitFieldGet} subcommands.
     *
     * since Valkey version 6.0.0.
     *
     * @example
     * ```typescript
     * await client.set("key", "A");  // "A" has binary value 01000001
     * const result = await client.bitfieldReadOnly("key", [new BitFieldGet(new UnsignedEncoding(2), new BitOffset(1))]);
     * console.log(result); // Output: [2] - The value at offset 1 with an unsigned encoding of 2 is 2.
     * ```
     */
    public async bitfieldReadOnly(
        key: string,
        subcommands: BitFieldGet[],
    ): Promise<number[]> {
        return this.createWritePromise(createBitField(key, subcommands, true));
    }

    /** Retrieve the value associated with `field` in the hash stored at `key`.
     * See https://valkey.io/commands/hget/ for details.
     *
     * @param key - The key of the hash.
     * @param field - The field in the hash stored at `key` to retrieve from the database.
     * @returns the value associated with `field`, or null when `field` is not present in the hash or `key` does not exist.
     *
     * @example
     * ```typescript
     * // Example usage of the hget method on an-existing field
     * await client.hset("my_hash", "field");
     * const result = await client.hget("my_hash", "field");
     * console.log(result); // Output: "value"
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the hget method on a non-existing field
     * const result = await client.hget("my_hash", "nonexistent_field");
     * console.log(result); // Output: null
     * ```
     */
    public hget(key: string, field: string): Promise<string | null> {
        return this.createWritePromise(createHGet(key, field));
    }

    /**
     * Iterates incrementally over a hash.
     *
     * See https://valkey.io/commands/hscan for more details.
     *
     * @param key - The key of the set.
     * @param cursor - The cursor that points to the next iteration of results. A value of `"0"` indicates the start of the search.
     * @param options - The {@link BaseScanOptions}.
     * @returns An array of the `cursor` and the subset of the hash held by `key`.
     * The first element is always the `cursor` for the next iteration of results. `"0"` will be the `cursor`
     * returned on the last iteration of the hash. The second element is always an array of the subset of the
     * hash held in `key`. The array in the second element is always a flattened series of string pairs,
     * where the value is at even indices and the value is at odd indices.
     *
     * @example
     * ```typescript
     * // Assume "key" contains a hash with multiple members
     * let newCursor = "0";
     * let result = [];
     * do {
     *      result = await client.hscan(key1, newCursor, {
     *          match: "*",
     *          count: 3,
     *      });
     *      newCursor = result[0];
     *      console.log("Cursor: ", newCursor);
     *      console.log("Members: ", result[1]);
     * } while (newCursor !== "0");
     * // The output of the code above is something similar to:
     * // Cursor:  31
     * // Members:  ['field 79', 'value 79', 'field 20', 'value 20', 'field 115', 'value 115']
     * // Cursor:  39
     * // Members:  ['field 63', 'value 63', 'field 293', 'value 293', 'field 162', 'value 162']
     * // Cursor:  0
     * // Members:  ['value 55', '55', 'value 24', '24', 'value 90', '90', 'value 113', '113']
     * ```
     */
    public async hscan(
        key: string,
        cursor: string,
        options?: BaseScanOptions,
    ): Promise<[string, string[]]> {
        return this.createWritePromise(createHScan(key, cursor, options));
    }

    /** Sets the specified fields to their respective values in the hash stored at `key`.
     * See https://valkey.io/commands/hset/ for details.
     *
     * @param key - The key of the hash.
     * @param fieldValueMap - A field-value map consisting of fields and their corresponding values
     * to be set in the hash stored at the specified key.
     * @returns The number of fields that were added.
     *
     * @example
     * ```typescript
     * // Example usage of the hset method
     * const result = await client.hset("my_hash", \{"field": "value", "field2": "value2"\});
     * console.log(result); // Output: 2 - Indicates that 2 fields were successfully set in the hash "my_hash".
     * ```
     */
    public hset(
        key: string,
        fieldValueMap: Record<string, string>,
    ): Promise<number> {
        return this.createWritePromise(createHSet(key, fieldValueMap));
    }

    /** Sets `field` in the hash stored at `key` to `value`, only if `field` does not yet exist.
     * If `key` does not exist, a new key holding a hash is created.
     * If `field` already exists, this operation has no effect.
     * See https://valkey.io/commands/hsetnx/ for more details.
     *
     * @param key - The key of the hash.
     * @param field - The field to set the value for.
     * @param value - The value to set.
     * @returns `true` if the field was set, `false` if the field already existed and was not set.
     *
     * @example
     * ```typescript
     * // Example usage of the hsetnx method
     * const result = await client.hsetnx("my_hash", "field", "value");
     * console.log(result); // Output: true - Indicates that the field "field" was set successfully in the hash "my_hash".
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the hsetnx method on a field that already exists
     * const result = await client.hsetnx("my_hash", "field", "new_value");
     * console.log(result); // Output: false - Indicates that the field "field" already existed in the hash "my_hash" and was not set again.
     * ```
     */
    public hsetnx(key: string, field: string, value: string): Promise<boolean> {
        return this.createWritePromise(createHSetNX(key, field, value));
    }

    /** Removes the specified fields from the hash stored at `key`.
     * Specified fields that do not exist within this hash are ignored.
     * See https://valkey.io/commands/hdel/ for details.
     *
     * @param key - The key of the hash.
     * @param fields - The fields to remove from the hash stored at `key`.
     * @returns the number of fields that were removed from the hash, not including specified but non existing fields.
     * If `key` does not exist, it is treated as an empty hash and it returns 0.
     *
     * @example
     * ```typescript
     * // Example usage of the hdel method
     * const result = await client.hdel("my_hash", ["field1", "field2"]);
     * console.log(result); // Output: 2 - Indicates that two fields were successfully removed from the hash.
     * ```
     */
    public hdel(key: string, fields: string[]): Promise<number> {
        return this.createWritePromise(createHDel(key, fields));
    }

    /** Returns the values associated with the specified fields in the hash stored at `key`.
     * See https://valkey.io/commands/hmget/ for details.
     *
     * @param key - The key of the hash.
     * @param fields - The fields in the hash stored at `key` to retrieve from the database.
     * @returns a list of values associated with the given fields, in the same order as they are requested.
     * For every field that does not exist in the hash, a null value is returned.
     * If `key` does not exist, it is treated as an empty hash and it returns a list of null values.
     *
     * @example
     * ```typescript
     * // Example usage of the hmget method
     * const result = await client.hmget("my_hash", ["field1", "field2"]);
     * console.log(result); // Output: ["value1", "value2"] - A list of values associated with the specified fields.
     * ```
     */
    public hmget(key: string, fields: string[]): Promise<(string | null)[]> {
        return this.createWritePromise(createHMGet(key, fields));
    }

    /** Returns if `field` is an existing field in the hash stored at `key`.
     * See https://valkey.io/commands/hexists/ for details.
     *
     * @param key - The key of the hash.
     * @param field - The field to check in the hash stored at `key`.
     * @returns `true` the hash contains `field`. If the hash does not contain `field`, or if `key` does not exist, it returns `false`.
     *
     * @example
     * ```typescript
     * // Example usage of the hexists method with existing field
     * const result = await client.hexists("my_hash", "field1");
     * console.log(result); // Output: true
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the hexists method with non-existing field
     * const result = await client.hexists("my_hash", "nonexistent_field");
     * console.log(result); // Output: false
     * ```
     */
    public hexists(key: string, field: string): Promise<boolean> {
        return this.createWritePromise(createHExists(key, field));
    }

    /** Returns all fields and values of the hash stored at `key`.
     * See https://valkey.io/commands/hgetall/ for details.
     *
     * @param key - The key of the hash.
     * @returns a list of fields and their values stored in the hash. Every field name in the list is followed by its value.
     * If `key` does not exist, it returns an empty list.
     *
     * @example
     * ```typescript
     * // Example usage of the hgetall method
     * const result = await client.hgetall("my_hash");
     * console.log(result); // Output: {"field1": "value1", "field2": "value2"}
     * ```
     */
    public hgetall(key: string): Promise<Record<string, string>> {
        return this.createWritePromise(createHGetAll(key));
    }

    /** Increments the number stored at `field` in the hash stored at `key` by increment.
     * By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
     * If `field` or `key` does not exist, it is set to 0 before performing the operation.
     * See https://valkey.io/commands/hincrby/ for details.
     *
     * @param key - The key of the hash.
     * @param amount - The amount to increment.
     * @param field - The field in the hash stored at `key` to increment its value.
     * @returns the value of `field` in the hash stored at `key` after the increment.
     *
     * @example
     * ```typescript
     * // Example usage of the hincrby method to increment the value in a hash by a specified amount
     * const result = await client.hincrby("my_hash", "field1", 5);
     * console.log(result); // Output: 5
     * ```
     */
    public hincrBy(
        key: string,
        field: string,
        amount: number,
    ): Promise<number> {
        return this.createWritePromise(createHIncrBy(key, field, amount));
    }

    /** Increment the string representing a floating point number stored at `field` in the hash stored at `key` by increment.
     * By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
     * If `field` or `key` does not exist, it is set to 0 before performing the operation.
     * See https://valkey.io/commands/hincrbyfloat/ for details.
     *
     * @param key - The key of the hash.
     * @param amount - The amount to increment.
     * @param field - The field in the hash stored at `key` to increment its value.
     * @returns the value of `field` in the hash stored at `key` after the increment.
     *
     * @example
     * ```typescript
     * // Example usage of the hincrbyfloat method to increment the value of a floating point in a hash by a specified amount
     * const result = await client.hincrbyfloat("my_hash", "field1", 2.5);
     * console.log(result); // Output: '2.5'
     * ```
     */
    public hincrByFloat(
        key: string,
        field: string,
        amount: number,
    ): Promise<number> {
        return this.createWritePromise(createHIncrByFloat(key, field, amount));
    }

    /** Returns the number of fields contained in the hash stored at `key`.
     * See https://valkey.io/commands/hlen/ for more details.
     *
     * @param key - The key of the hash.
     * @returns The number of fields in the hash, or 0 when the key does not exist.
     *
     * @example
     * ```typescript
     * // Example usage of the hlen method with an existing key
     * const result = await client.hlen("my_hash");
     * console.log(result); // Output: 3
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the hlen method with a non-existing key
     * const result = await client.hlen("non_existing_key");
     * console.log(result); // Output: 0
     * ```
     */
    public hlen(key: string): Promise<number> {
        return this.createWritePromise(createHLen(key));
    }

    /** Returns all values in the hash stored at key.
     * See https://valkey.io/commands/hvals/ for more details.
     *
     * @param key - The key of the hash.
     * @returns a list of values in the hash, or an empty list when the key does not exist.
     *
     * @example
     * ```typescript
     * // Example usage of the hvals method
     * const result = await client.hvals("my_hash");
     * console.log(result); // Output: ["value1", "value2", "value3"] - Returns all the values stored in the hash "my_hash".
     * ```
     */
    public hvals(key: string): Promise<string[]> {
        return this.createWritePromise(createHVals(key));
    }

    /**
     * Returns the string length of the value associated with `field` in the hash stored at `key`.
     *
     * See https://valkey.io/commands/hstrlen/ for details.
     *
     * @param key - The key of the hash.
     * @param field - The field in the hash.
     * @returns The string length or `0` if `field` or `key` does not exist.
     *
     * @example
     * ```typescript
     * await client.hset("my_hash", {"field": "value"});
     * const result = await client.hstrlen("my_hash", "field");
     * console.log(result); // Output: 5
     * ```
     */
    public hstrlen(key: string, field: string): Promise<number> {
        return this.createWritePromise(createHStrlen(key, field));
    }

    /**
     * Returns a random field name from the hash value stored at `key`.
     *
     * See https://valkey.io/commands/hrandfield/ for more details.
     *
     * since Valkey version 6.2.0.
     *
     * @param key - The key of the hash.
     * @returns A random field name from the hash stored at `key`, or `null` when
     *     the key does not exist.
     *
     * @example
     * ```typescript
     * console.log(await client.hrandfield("myHash")); // Output: 'field'
     * ```
     */
    public async hrandfield(key: string): Promise<string | null> {
        return this.createWritePromise(createHRandField(key));
    }

    /**
     * Retrieves up to `count` random field names from the hash value stored at `key`.
     *
     * See https://valkey.io/commands/hrandfield/ for more details.
     *
     * since Valkey version 6.2.0.
     *
     * @param key - The key of the hash.
     * @param count - The number of field names to return.
     *
     *     If `count` is positive, returns unique elements. If negative, allows for duplicates.
     * @returns An `array` of random field names from the hash stored at `key`,
     *     or an `empty array` when the key does not exist.
     *
     * @example
     * ```typescript
     * console.log(await client.hrandfieldCount("myHash", 2)); // Output: ['field1', 'field2']
     * ```
     */
    public async hrandfieldCount(
        key: string,
        count: number,
    ): Promise<string[]> {
        return this.createWritePromise(createHRandField(key, count));
    }

    /**
     * Retrieves up to `count` random field names along with their values from the hash
     * value stored at `key`.
     *
     * See https://valkey.io/commands/hrandfield/ for more details.
     *
     * since Valkey version 6.2.0.
     *
     * @param key - The key of the hash.
     * @param count - The number of field names to return.
     *
     *     If `count` is positive, returns unique elements. If negative, allows for duplicates.
     * @returns A 2D `array` of `[fieldName, value]` `arrays`, where `fieldName` is a random
     *     field name from the hash and `value` is the associated value of the field name.
     *     If the hash does not exist or is empty, the response will be an empty `array`.
     *
     * @example
     * ```typescript
     * const result = await client.hrandfieldCountWithValues("myHash", 2);
     * console.log(result); // Output: [['field1', 'value1'], ['field2', 'value2']]
     * ```
     */
    public async hrandfieldWithValues(
        key: string,
        count: number,
    ): Promise<[string, string][]> {
        return this.createWritePromise(createHRandField(key, count, true));
    }

    /** Inserts all the specified values at the head of the list stored at `key`.
     * `elements` are inserted one after the other to the head of the list, from the leftmost element to the rightmost element.
     * If `key` does not exist, it is created as empty list before performing the push operations.
     * See https://valkey.io/commands/lpush/ for details.
     *
     * @param key - The key of the list.
     * @param elements - The elements to insert at the head of the list stored at `key`.
     * @returns the length of the list after the push operations.
     *
     * @example
     * ```typescript
     * // Example usage of the lpush method with an existing list
     * const result = await client.lpush("my_list", ["value2", "value3"]);
     * console.log(result); // Output: 3 - Indicated that the new length of the list is 3 after the push operation.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the lpush method with a non-existing list
     * const result = await client.lpush("nonexistent_list", ["new_value"]);
     * console.log(result); // Output: 1 - Indicates that a new list was created with one element
     * ```
     */
    public lpush(key: string, elements: string[]): Promise<number> {
        return this.createWritePromise(createLPush(key, elements));
    }

    /**
     * Inserts specified values at the head of the `list`, only if `key` already
     * exists and holds a list.
     *
     * See https://valkey.io/commands/lpushx/ for details.
     *
     * @param key - The key of the list.
     * @param elements - The elements to insert at the head of the list stored at `key`.
     * @returns The length of the list after the push operation.
     * @example
     * ```typescript
     * const listLength = await client.lpushx("my_list", ["value1", "value2"]);
     * console.log(result); // Output: 2 - Indicates that the list has two elements.
     * ```
     */
    public lpushx(key: string, elements: string[]): Promise<number> {
        return this.createWritePromise(createLPushX(key, elements));
    }

    /** Removes and returns the first elements of the list stored at `key`.
     * The command pops a single element from the beginning of the list.
     * See https://valkey.io/commands/lpop/ for details.
     *
     * @param key - The key of the list.
     * @returns The value of the first element.
     * If `key` does not exist null will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of the lpop method with an existing list
     * const result = await client.lpop("my_list");
     * console.log(result); // Output: 'value1'
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the lpop method with a non-existing list
     * const result = await client.lpop("non_exiting_key");
     * console.log(result); // Output: null
     * ```
     */
    public lpop(key: string): Promise<string | null> {
        return this.createWritePromise(createLPop(key));
    }

    /** Removes and returns up to `count` elements of the list stored at `key`, depending on the list's length.
     * See https://valkey.io/commands/lpop/ for details.
     *
     * @param key - The key of the list.
     * @param count - The count of the elements to pop from the list.
     * @returns A list of the popped elements will be returned depending on the list's length.
     * If `key` does not exist null will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of the lpopCount method with an existing list
     * const result = await client.lpopCount("my_list", 2);
     * console.log(result); // Output: ["value1", "value2"]
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the lpopCount method with a non-existing list
     * const result = await client.lpopCount("non_exiting_key", 3);
     * console.log(result); // Output: null
     * ```
     */
    public lpopCount(key: string, count: number): Promise<string[] | null> {
        return this.createWritePromise(createLPop(key, count));
    }

    /** Returns the specified elements of the list stored at `key`.
     * The offsets `start` and `end` are zero-based indexes, with 0 being the first element of the list, 1 being the next element and so on.
     * These offsets can also be negative numbers indicating offsets starting at the end of the list,
     * with -1 being the last element of the list, -2 being the penultimate, and so on.
     * See https://valkey.io/commands/lrange/ for details.
     *
     * @param key - The key of the list.
     * @param start - The starting point of the range.
     * @param end - The end of the range.
     * @returns list of elements in the specified range.
     * If `start` exceeds the end of the list, or if `start` is greater than `end`, an empty list will be returned.
     * If `end` exceeds the actual end of the list, the range will stop at the actual end of the list.
     * If `key` does not exist an empty list will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of the lrange method with an existing list and positive indices
     * const result = await client.lrange("my_list", 0, 2);
     * console.log(result); // Output: ["value1", "value2", "value3"]
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the lrange method with an existing list and negative indices
     * const result = await client.lrange("my_list", -2, -1);
     * console.log(result); // Output: ["value2", "value3"]
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the lrange method with a non-existing list
     * const result = await client.lrange("non_exiting_key", 0, 2);
     * console.log(result); // Output: []
     * ```
     */
    public lrange(key: string, start: number, end: number): Promise<string[]> {
        return this.createWritePromise(createLRange(key, start, end));
    }

    /** Returns the length of the list stored at `key`.
     * See https://valkey.io/commands/llen/ for details.
     *
     * @param key - The key of the list.
     * @returns the length of the list at `key`.
     * If `key` does not exist, it is interpreted as an empty list and 0 is returned.
     *
     * @example
     * ```typescript
     * // Example usage of the llen method
     * const result = await client.llen("my_list");
     * console.log(result); // Output: 3 - Indicates that there are 3 elements in the list.
     * ```
     */
    public llen(key: string): Promise<number> {
        return this.createWritePromise(createLLen(key));
    }

    /**
     * Atomically pops and removes the left/right-most element to the list stored at `source`
     * depending on `whereTo`, and pushes the element at the first/last element of the list
     * stored at `destination` depending on `whereFrom`, see {@link ListDirection}.
     *
     * See https://valkey.io/commands/lmove/ for details.
     *
     * @param source - The key to the source list.
     * @param destination - The key to the destination list.
     * @param whereFrom - The {@link ListDirection} to remove the element from.
     * @param whereTo - The {@link ListDirection} to add the element to.
     * @returns The popped element, or `null` if `source` does not exist.
     *
     * since Valkey version 6.2.0.
     *
     * @example
     * ```typescript
     * await client.lpush("testKey1", ["two", "one"]);
     * await client.lpush("testKey2", ["four", "three"]);
     *
     * const result1 = await client.lmove("testKey1", "testKey2", ListDirection.LEFT, ListDirection.LEFT);
     * console.log(result1); // Output: "one".
     *
     * const updated_array_key1 = await client.lrange("testKey1", 0, -1);
     * console.log(updated_array); // Output: "two".
     *
     * const updated_array_key2 = await client.lrange("testKey2", 0, -1);
     * console.log(updated_array_key2); // Output: ["one", "three", "four"].
     * ```
     */
    public async lmove(
        source: string,
        destination: string,
        whereFrom: ListDirection,
        whereTo: ListDirection,
    ): Promise<string | null> {
        return this.createWritePromise(
            createLMove(source, destination, whereFrom, whereTo),
        );
    }

    /**
     * Blocks the connection until it pops atomically and removes the left/right-most element to the
     * list stored at `source` depending on `whereFrom`, and pushes the element at the first/last element
     * of the list stored at `destination` depending on `whereTo`.
     * `BLMOVE` is the blocking variant of {@link lmove}.
     *
     * @remarks
     * 1. When in cluster mode, both `source` and `destination` must map to the same hash slot.
     * 2. `BLMOVE` is a client blocking command, see https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands for more details and best practices.
     *
     * See https://valkey.io/commands/blmove/ for details.
     *
     * @param source - The key to the source list.
     * @param destination - The key to the destination list.
     * @param whereFrom - The {@link ListDirection} to remove the element from.
     * @param whereTo - The {@link ListDirection} to add the element to.
     * @param timeout - The number of seconds to wait for a blocking operation to complete. A value of `0` will block indefinitely.
     * @returns The popped element, or `null` if `source` does not exist or if the operation timed-out.
     *
     * since Valkey version 6.2.0.
     *
     * @example
     * ```typescript
     * await client.lpush("testKey1", ["two", "one"]);
     * await client.lpush("testKey2", ["four", "three"]);
     * const result = await client.blmove("testKey1", "testKey2", ListDirection.LEFT, ListDirection.LEFT, 0.1);
     * console.log(result); // Output: "one"
     *
     * const result2 = await client.lrange("testKey1", 0, -1);
     * console.log(result2);   // Output: "two"
     *
     * const updated_array2 = await client.lrange("testKey2", 0, -1);
     * console.log(updated_array2); // Output: ["one", "three", "four"]
     * ```
     */
    public async blmove(
        source: string,
        destination: string,
        whereFrom: ListDirection,
        whereTo: ListDirection,
        timeout: number,
    ): Promise<string | null> {
        return this.createWritePromise(
            createBLMove(source, destination, whereFrom, whereTo, timeout),
        );
    }

    /**
     * Sets the list element at `index` to `element`.
     * The index is zero-based, so `0` means the first element, `1` the second element and so on.
     * Negative indices can be used to designate elements starting at the tail of
     * the list. Here, `-1` means the last element, `-2` means the penultimate and so forth.
     *
     * See https://valkey.io/commands/lset/ for details.
     *
     * @param key - The key of the list.
     * @param index - The index of the element in the list to be set.
     * @param element - The new element to set at the specified index.
     * @returns Always "OK".
     *
     * @example
     * ```typescript
     * // Example usage of the lset method
     * const response = await client.lset("test_key", 1, "two");
     * console.log(response); // Output: 'OK' - Indicates that the second index of the list has been set to "two".
     * ```
     */
    public lset(key: string, index: number, element: string): Promise<"OK"> {
        return this.createWritePromise(createLSet(key, index, element));
    }

    /** Trim an existing list so that it will contain only the specified range of elements specified.
     * The offsets `start` and `end` are zero-based indexes, with 0 being the first element of the list, 1 being the next element and so on.
     * These offsets can also be negative numbers indicating offsets starting at the end of the list,
     * with -1 being the last element of the list, -2 being the penultimate, and so on.
     * See https://valkey.io/commands/ltrim/ for details.
     *
     * @param key - The key of the list.
     * @param start - The starting point of the range.
     * @param end - The end of the range.
     * @returns always "OK".
     * If `start` exceeds the end of the list, or if `start` is greater than `end`, the result will be an empty list (which causes key to be removed).
     * If `end` exceeds the actual end of the list, it will be treated like the last element of the list.
     * If `key` does not exist the command will be ignored.
     *
     * @example
     * ```typescript
     * // Example usage of the ltrim method
     * const result = await client.ltrim("my_list", 0, 1);
     * console.log(result); // Output: 'OK' - Indicates that the list has been trimmed to contain elements from 0 to 1.
     * ```
     */
    public ltrim(key: string, start: number, end: number): Promise<"OK"> {
        return this.createWritePromise(createLTrim(key, start, end));
    }

    /** Removes the first `count` occurrences of elements equal to `element` from the list stored at `key`.
     * If `count` is positive : Removes elements equal to `element` moving from head to tail.
     * If `count` is negative : Removes elements equal to `element` moving from tail to head.
     * If `count` is 0 or `count` is greater than the occurrences of elements equal to `element`: Removes all elements equal to `element`.
     *
     * @param key - The key of the list.
     * @param count - The count of the occurrences of elements equal to `element` to remove.
     * @param element - The element to remove from the list.
     * @returns the number of the removed elements.
     * If `key` does not exist, 0 is returned.
     *
     * @example
     * ```typescript
     * // Example usage of the lrem method
     * const result = await client.lrem("my_list", 2, "value");
     * console.log(result); // Output: 2 - Removes the first 2 occurrences of "value" in the list.
     * ```
     */
    public lrem(key: string, count: number, element: string): Promise<number> {
        return this.createWritePromise(createLRem(key, count, element));
    }

    /** Inserts all the specified values at the tail of the list stored at `key`.
     * `elements` are inserted one after the other to the tail of the list, from the leftmost element to the rightmost element.
     * If `key` does not exist, it is created as empty list before performing the push operations.
     * See https://valkey.io/commands/rpush/ for details.
     *
     * @param key - The key of the list.
     * @param elements - The elements to insert at the tail of the list stored at `key`.
     * @returns the length of the list after the push operations.
     *
     * @example
     * ```typescript
     * // Example usage of the rpush method with an existing list
     * const result = await client.rpush("my_list", ["value2", "value3"]);
     * console.log(result); // Output: 3 - Indicates that the new length of the list is 3 after the push operation.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the rpush method with a non-existing list
     * const result = await client.rpush("nonexistent_list", ["new_value"]);
     * console.log(result); // Output: 1
     * ```
     */
    public rpush(key: string, elements: string[]): Promise<number> {
        return this.createWritePromise(createRPush(key, elements));
    }

    /**
     * Inserts specified values at the tail of the `list`, only if `key` already
     * exists and holds a list.
     *
     * See https://valkey.io/commands/rpushx/ for details.
     *
     * @param key - The key of the list.
     * @param elements - The elements to insert at the tail of the list stored at `key`.
     * @returns The length of the list after the push operation.
     * @example
     * ```typescript
     * const result = await client.rpushx("my_list", ["value1", "value2"]);
     * console.log(result);  // Output: 2 - Indicates that the list has two elements.
     * ```
     * */
    public rpushx(key: string, elements: string[]): Promise<number> {
        return this.createWritePromise(createRPushX(key, elements));
    }

    /** Removes and returns the last elements of the list stored at `key`.
     * The command pops a single element from the end of the list.
     * See https://valkey.io/commands/rpop/ for details.
     *
     * @param key - The key of the list.
     * @returns The value of the last element.
     * If `key` does not exist null will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of the rpop method with an existing list
     * const result = await client.rpop("my_list");
     * console.log(result); // Output: 'value1'
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the rpop method with a non-existing list
     * const result = await client.rpop("non_exiting_key");
     * console.log(result); // Output: null
     * ```
     */
    public rpop(key: string): Promise<string | null> {
        return this.createWritePromise(createRPop(key));
    }

    /** Removes and returns up to `count` elements from the list stored at `key`, depending on the list's length.
     * See https://valkey.io/commands/rpop/ for details.
     *
     * @param key - The key of the list.
     * @param count - The count of the elements to pop from the list.
     * @returns A list of popped elements will be returned depending on the list's length.
     * If `key` does not exist null will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of the rpopCount method with an existing list
     * const result = await client.rpopCount("my_list", 2);
     * console.log(result); // Output: ["value1", "value2"]
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the rpopCount method with a non-existing list
     * const result = await client.rpopCount("non_exiting_key", 7);
     * console.log(result); // Output: null
     * ```
     */
    public rpopCount(key: string, count: number): Promise<string[] | null> {
        return this.createWritePromise(createRPop(key, count));
    }

    /** Adds the specified members to the set stored at `key`. Specified members that are already a member of this set are ignored.
     * If `key` does not exist, a new set is created before adding `members`.
     * See https://valkey.io/commands/sadd/ for details.
     *
     * @param key - The key to store the members to its set.
     * @param members - A list of members to add to the set stored at `key`.
     * @returns The number of members that were added to the set, not including all the members already present in the set.
     *
     * @example
     * ```typescript
     * // Example usage of the sadd method with an existing set
     * const result = await client.sadd("my_set", ["member1", "member2"]);
     * console.log(result); // Output: 2
     * ```
     */
    public sadd(key: string, members: string[]): Promise<number> {
        return this.createWritePromise(createSAdd(key, members));
    }

    /** Removes the specified members from the set stored at `key`. Specified members that are not a member of this set are ignored.
     * See https://valkey.io/commands/srem/ for details.
     *
     * @param key - The key to remove the members from its set.
     * @param members - A list of members to remove from the set stored at `key`.
     * @returns The number of members that were removed from the set, not including non existing members.
     * If `key` does not exist, it is treated as an empty set and this command returns 0.
     *
     * @example
     * ```typescript
     * // Example usage of the srem method
     * const result = await client.srem("my_set", ["member1", "member2"]);
     * console.log(result); // Output: 2
     * ```
     */
    public srem(key: string, members: string[]): Promise<number> {
        return this.createWritePromise(createSRem(key, members));
    }

    /** Returns all the members of the set value stored at `key`.
     * See https://valkey.io/commands/smembers/ for details.
     *
     * @param key - The key to return its members.
     * @returns A `Set` containing all members of the set.
     * If `key` does not exist, it is treated as an empty set and this command returns an empty `Set`.
     *
     * @example
     * ```typescript
     * // Example usage of the smembers method
     * const result = await client.smembers("my_set");
     * console.log(result); // Output: Set {'member1', 'member2', 'member3'}
     * ```
     */
    public smembers(key: string): Promise<Set<string>> {
        return this.createWritePromise<string[]>(createSMembers(key)).then(
            (smembes) => new Set<string>(smembes),
        );
    }

    /** Moves `member` from the set at `source` to the set at `destination`, removing it from the source set.
     * Creates a new destination set if needed. The operation is atomic.
     * See https://valkey.io/commands/smove for more details.
     *
     * @remarks When in cluster mode, `source` and `destination` must map to the same hash slot.
     *
     * @param source - The key of the set to remove the element from.
     * @param destination - The key of the set to add the element to.
     * @param member - The set element to move.
     * @returns `true` on success, or `false` if the `source` set does not exist or the element is not a member of the source set.
     *
     * @example
     * ```typescript
     * const result = await client.smove("set1", "set2", "member1");
     * console.log(result); // Output: true - "member1" was moved from "set1" to "set2".
     * ```
     */
    public smove(
        source: string,
        destination: string,
        member: string,
    ): Promise<boolean> {
        return this.createWritePromise(
            createSMove(source, destination, member),
        );
    }

    /** Returns the set cardinality (number of elements) of the set stored at `key`.
     * See https://valkey.io/commands/scard/ for details.
     *
     * @param key - The key to return the number of its members.
     * @returns The cardinality (number of elements) of the set, or 0 if key does not exist.
     *
     * @example
     * ```typescript
     * // Example usage of the scard method
     * const result = await client.scard("my_set");
     * console.log(result); // Output: 3
     * ```
     */
    public scard(key: string): Promise<number> {
        return this.createWritePromise(createSCard(key));
    }

    /** Gets the intersection of all the given sets.
     * See https://valkey.io/docs/latest/commands/sinter/ for more details.
     *
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @param keys - The `keys` of the sets to get the intersection.
     * @returns - A set of members which are present in all given sets.
     * If one or more sets do not exist, an empty set will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of sinter method when member exists
     * const result = await client.sinter(["my_set1", "my_set2"]);
     * console.log(result); // Output: Set {'member2'} - Indicates that sets have one common member
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of sinter method with non-existing key
     * const result = await client.sinter(["my_set", "non_existing_key"]);
     * console.log(result); // Output: Set {} - An empty set is returned since the key does not exist.
     * ```
     */
    public sinter(keys: string[]): Promise<Set<string>> {
        return this.createWritePromise<string[]>(createSInter(keys)).then(
            (sinter) => new Set<string>(sinter),
        );
    }

    /**
     * Gets the cardinality of the intersection of all the given sets.
     *
     * See https://valkey.io/commands/sintercard/ for more details.
     *
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @param keys - The keys of the sets.
     * @param limit - The limit for the intersection cardinality value. If not specified, or set to `0`, no limit is used.
     * @returns The cardinality of the intersection result. If one or more sets do not exist, `0` is returned.
     *
     * since Valkey version 7.0.0.
     *
     * @example
     * ```typescript
     * await client.sadd("set1", ["a", "b", "c"]);
     * await client.sadd("set2", ["b", "c", "d"]);
     * const result1 = await client.sintercard(["set1", "set2"]);
     * console.log(result1); // Output: 2 - The intersection of "set1" and "set2" contains 2 elements: "b" and "c".
     *
     * const result2 = await client.sintercard(["set1", "set2"], 1);
     * console.log(result2); // Output: 1 - The computation stops early as the intersection cardinality reaches the limit of 1.
     * ```
     */
    public sintercard(keys: string[], limit?: number): Promise<number> {
        return this.createWritePromise(createSInterCard(keys, limit));
    }

    /**
     * Stores the members of the intersection of all given sets specified by `keys` into a new set at `destination`.
     *
     * See https://valkey.io/commands/sinterstore/ for more details.
     *
     * @remarks When in cluster mode, `destination` and all `keys` must map to the same hash slot.
     * @param destination - The key of the destination set.
     * @param keys - The keys from which to retrieve the set members.
     * @returns The number of elements in the resulting set.
     *
     * @example
     * ```typescript
     * const result = await client.sinterstore("my_set", ["set1", "set2"]);
     * console.log(result); // Output: 2 - Two elements were stored at "my_set", and those elements are the intersection of "set1" and "set2".
     * ```
     */
    public sinterstore(destination: string, keys: string[]): Promise<number> {
        return this.createWritePromise(createSInterStore(destination, keys));
    }

    /**
     * Computes the difference between the first set and all the successive sets in `keys`.
     *
     * See https://valkey.io/commands/sdiff/ for more details.
     *
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @param keys - The keys of the sets to diff.
     * @returns A `Set` of elements representing the difference between the sets.
     * If a key in `keys` does not exist, it is treated as an empty set.
     *
     * @example
     * ```typescript
     * await client.sadd("set1", ["member1", "member2"]);
     * await client.sadd("set2", ["member1"]);
     * const result = await client.sdiff(["set1", "set2"]);
     * console.log(result); // Output: Set {"member1"} - "member2" is in "set1" but not "set2"
     * ```
     */
    public sdiff(keys: string[]): Promise<Set<string>> {
        return this.createWritePromise<string[]>(createSDiff(keys)).then(
            (sdiff) => new Set<string>(sdiff),
        );
    }

    /**
     * Stores the difference between the first set and all the successive sets in `keys` into a new set at `destination`.
     *
     * See https://valkey.io/commands/sdiffstore/ for more details.
     *
     * @remarks When in cluster mode, `destination` and all `keys` must map to the same hash slot.
     * @param destination - The key of the destination set.
     * @param keys - The keys of the sets to diff.
     * @returns The number of elements in the resulting set.
     *
     * @example
     * ```typescript
     * await client.sadd("set1", ["member1", "member2"]);
     * await client.sadd("set2", ["member1"]);
     * const result = await client.sdiffstore("set3", ["set1", "set2"]);
     * console.log(result); // Output: 1 - One member was stored in "set3", and that member is the diff between "set1" and "set2".
     * ```
     */
    public sdiffstore(destination: string, keys: string[]): Promise<number> {
        return this.createWritePromise(createSDiffStore(destination, keys));
    }

    /**
     * Gets the union of all the given sets.
     *
     * See https://valkey.io/commands/sunion/ for more details.
     *
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @param keys - The keys of the sets.
     * @returns A `Set` of members which are present in at least one of the given sets.
     * If none of the sets exist, an empty `Set` will be returned.
     *
     * @example
     * ```typescript
     * await client.sadd("my_set1", ["member1", "member2"]);
     * await client.sadd("my_set2", ["member2", "member3"]);
     * const result1 = await client.sunion(["my_set1", "my_set2"]);
     * console.log(result1); // Output: Set {'member1', 'member2', 'member3'} - Sets "my_set1" and "my_set2" have three unique members.
     *
     * const result2 = await client.sunion(["my_set1", "non_existing_set"]);
     * console.log(result2); // Output: Set {'member1', 'member2'}
     * ```
     */
    public sunion(keys: string[]): Promise<Set<string>> {
        return this.createWritePromise<string[]>(createSUnion(keys)).then(
            (sunion) => new Set<string>(sunion),
        );
    }

    /**
     * Stores the members of the union of all given sets specified by `keys` into a new set
     * at `destination`.
     *
     * See https://valkey.io/commands/sunionstore/ for details.
     *
     * @remarks When in cluster mode, `destination` and all `keys` must map to the same hash slot.
     * @param destination - The key of the destination set.
     * @param keys - The keys from which to retrieve the set members.
     * @returns The number of elements in the resulting set.
     *
     * @example
     * ```typescript
     * const length = await client.sunionstore("mySet", ["set1", "set2"]);
     * console.log(length); // Output: 2 - Two elements were stored in "mySet", and those two members are the union of "set1" and "set2".
     * ```
     */
    public sunionstore(destination: string, keys: string[]): Promise<number> {
        return this.createWritePromise(createSUnionStore(destination, keys));
    }

    /** Returns if `member` is a member of the set stored at `key`.
     * See https://valkey.io/commands/sismember/ for more details.
     *
     * @param key - The key of the set.
     * @param member - The member to check for existence in the set.
     * @returns `true` if the member exists in the set, `false` otherwise.
     * If `key` doesn't exist, it is treated as an empty set and the command returns `false`.
     *
     * @example
     * ```typescript
     * // Example usage of the sismember method when member exists
     * const result = await client.sismember("my_set", "member1");
     * console.log(result); // Output: true - Indicates that "member1" exists in the set "my_set".
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the sismember method when member does not exist
     * const result = await client.sismember("my_set", "non_existing_member");
     * console.log(result); // Output: false - Indicates that "non_existing_member" does not exist in the set "my_set".
     * ```
     */
    public sismember(key: string, member: string): Promise<boolean> {
        return this.createWritePromise(createSIsMember(key, member));
    }

    /**
     * Checks whether each member is contained in the members of the set stored at `key`.
     *
     * See https://valkey.io/commands/smismember/ for more details.
     *
     * @param key - The key of the set to check.
     * @param members - A list of members to check for existence in the set.
     * @returns An `array` of `boolean` values, each indicating if the respective member exists in the set.
     *
     * since Valkey version 6.2.0.
     *
     * @example
     * ```typescript
     * await client.sadd("set1", ["a", "b", "c"]);
     * const result = await client.smismember("set1", ["b", "c", "d"]);
     * console.log(result); // Output: [true, true, false] - "b" and "c" are members of "set1", but "d" is not.
     * ```
     */
    public smismember(key: string, members: string[]): Promise<boolean[]> {
        return this.createWritePromise(createSMIsMember(key, members));
    }

    /** Removes and returns one random member from the set value store at `key`.
     * See https://valkey.io/commands/spop/ for details.
     * To pop multiple members, see `spopCount`.
     *
     * @param key - The key of the set.
     * @returns the value of the popped member.
     * If `key` does not exist, null will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of spop method to remove and return a random member from a set
     * const result = await client.spop("my_set");
     * console.log(result); // Output: 'member1' - Removes and returns a random member from the set "my_set".
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of spop method with non-existing key
     * const result = await client.spop("non_existing_key");
     * console.log(result); // Output: null
     * ```
     */
    public spop(key: string): Promise<string | null> {
        return this.createWritePromise(createSPop(key));
    }

    /** Removes and returns up to `count` random members from the set value store at `key`, depending on the set's length.
     * See https://valkey.io/commands/spop/ for details.
     *
     * @param key - The key of the set.
     * @param count - The count of the elements to pop from the set.
     * @returns A `Set` containing the popped elements, depending on the set's length.
     * If `key` does not exist, an empty `Set` will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of spopCount method to remove and return multiple random members from a set
     * const result = await client.spopCount("my_set", 2);
     * console.log(result); // Output: Set {'member2', 'member3'} - Removes and returns 2 random members from the set "my_set".
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of spopCount method with non-existing key
     * const result = await client.spopCount("non_existing_key");
     * console.log(result); // Output: Set {} - An empty set is returned since the key does not exist.
     * ```
     */
    public async spopCount(key: string, count: number): Promise<Set<string>> {
        return this.createWritePromise<string[]>(createSPop(key, count)).then(
            (spop) => new Set<string>(spop),
        );
    }

    /**
     * Returns a random element from the set value stored at `key`.
     *
     * See https://valkey.io/commands/srandmember for more details.
     *
     * @param key - The key from which to retrieve the set member.
     * @returns A random element from the set, or null if `key` does not exist.
     *
     * @example
     * ```typescript
     * // Example usage of srandmember method to return a random member from a set
     * const result = await client.srandmember("my_set");
     * console.log(result); // Output: 'member1' - A random member of "my_set".
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of srandmember method with non-existing key
     * const result = await client.srandmember("non_existing_set");
     * console.log(result); // Output: null
     * ```
     */
    public srandmember(key: string): Promise<string | null> {
        return this.createWritePromise(createSRandMember(key));
    }

    /**
     * Returns one or more random elements from the set value stored at `key`.
     *
     * See https://valkey.io/commands/srandmember for more details.
     *
     * @param key - The key of the sorted set.
     * @param count - The number of members to return.
     *                If `count` is positive, returns unique members.
     *                If `count` is negative, allows for duplicates members.
     * @returns a list of members from the set. If the set does not exist or is empty, an empty list will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of srandmemberCount method to return multiple random members from a set
     * const result = await client.srandmemberCount("my_set", -3);
     * console.log(result); // Output: ['member1', 'member1', 'member2'] - Random members of "my_set".
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of srandmemberCount method with non-existing key
     * const result = await client.srandmemberCount("non_existing_set", 3);
     * console.log(result); // Output: [] - An empty list since the key does not exist.
     * ```
     */
    public async srandmemberCount(
        key: string,
        count: number,
    ): Promise<string[]> {
        return this.createWritePromise(createSRandMember(key, count));
    }

    /** Returns the number of keys in `keys` that exist in the database.
     * See https://valkey.io/commands/exists/ for details.
     *
     * @param keys - The keys list to check.
     * @returns The number of keys that exist. If the same existing key is mentioned in `keys` multiple times,
     * it will be counted multiple times.
     *
     * @example
     * ```typescript
     * // Example usage of the exists method
     * const result = await client.exists(["key1", "key2", "key3"]);
     * console.log(result); // Output: 3 - Indicates that all three keys exist in the database.
     * ```
     */
    public exists(keys: string[]): Promise<number> {
        return this.createWritePromise(createExists(keys));
    }

    /** Removes the specified keys. A key is ignored if it does not exist.
     * This command, similar to DEL, removes specified keys and ignores non-existent ones.
     * However, this command does not block the server, while [DEL](https://valkey.io/commands/del) does.
     * See https://valkey.io/commands/unlink/ for details.
     *
     * @param keys - The keys we wanted to unlink.
     * @returns The number of keys that were unlinked.
     *
     * @example
     * ```typescript
     * // Example usage of the unlink method
     * const result = await client.unlink(["key1", "key2", "key3"]);
     * console.log(result); // Output: 3 - Indicates that all three keys were unlinked from the database.
     * ```
     */
    public unlink(keys: string[]): Promise<number> {
        return this.createWritePromise(createUnlink(keys));
    }

    /** Sets a timeout on `key` in seconds. After the timeout has expired, the key will automatically be deleted.
     * If `key` already has an existing expire set, the time to live is updated to the new value.
     * If `seconds` is non-positive number, the key will be deleted rather than expired.
     * The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
     * See https://valkey.io/commands/expire/ for details.
     *
     * @param key - The key to set timeout on it.
     * @param seconds - The timeout in seconds.
     * @param option - The expire option.
     * @returns `true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
     * or operation skipped due to the provided arguments.
     *
     * @example
     * ```typescript
     * // Example usage of the expire method
     * const result = await client.expire("my_key", 60);
     * console.log(result); // Output: true - Indicates that a timeout of 60 seconds has been set for "my_key".
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the expire method with exisiting expiry
     * const result = await client.expire("my_key", 60, ExpireOptions.HasNoExpiry);
     * console.log(result); // Output: false - Indicates that "my_key" has an existing expiry.
     * ```
     */
    public expire(
        key: string,
        seconds: number,
        option?: ExpireOptions,
    ): Promise<boolean> {
        return this.createWritePromise(createExpire(key, seconds, option));
    }

    /** Sets a timeout on `key`. It takes an absolute Unix timestamp (seconds since January 1, 1970) instead of specifying the number of seconds.
     * A timestamp in the past will delete the key immediately. After the timeout has expired, the key will automatically be deleted.
     * If `key` already has an existing expire set, the time to live is updated to the new value.
     * The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
     * See https://valkey.io/commands/expireat/ for details.
     *
     * @param key - The key to set timeout on it.
     * @param unixSeconds - The timeout in an absolute Unix timestamp.
     * @param option - The expire option.
     * @returns `true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
     * or operation skipped due to the provided arguments.
     *
     * @example
     * ```typescript
     * // Example usage of the expireAt method on a key with no previous expiry
     * const result = await client.expireAt("my_key", 1672531200, ExpireOptions.HasNoExpiry);
     * console.log(result); // Output: true - Indicates that the expiration time for "my_key" was successfully set.
     * ```
     */
    public expireAt(
        key: string,
        unixSeconds: number,
        option?: ExpireOptions,
    ): Promise<boolean> {
        return this.createWritePromise(
            createExpireAt(key, unixSeconds, option),
        );
    }

    /**
     * Returns the absolute Unix timestamp (since January 1, 1970) at which the given `key` will expire, in seconds.
     * To get the expiration with millisecond precision, use {@link pexpiretime}.
     *
     * See https://valkey.io/commands/expiretime/ for details.
     *
     * @param key - The `key` to determine the expiration value of.
     * @returns The expiration Unix timestamp in seconds, `-2` if `key` does not exist or `-1` if `key` exists but has no associated expire.
     *
     * since Valkey version 7.0.0.
     *
     * @example
     * ```typescript
     * const result1 = await client.expiretime("myKey");
     * console.log(result1); // Output: -2 - myKey doesn't exist.
     *
     * const result2 = await client.set(myKey, "value");
     * const result3 = await client.expireTime(myKey);
     * console.log(result2); // Output: -1 - myKey has no associated expiration.
     *
     * client.expire(myKey, 60);
     * const result3 = await client.expireTime(myKey);
     * console.log(result3); // Output: 123456 - the Unix timestamp (in seconds) when "myKey" will expire.
     * ```
     */
    public async expiretime(key: string): Promise<number> {
        return this.createWritePromise(createExpireTime(key));
    }

    /** Sets a timeout on `key` in milliseconds. After the timeout has expired, the key will automatically be deleted.
     * If `key` already has an existing expire set, the time to live is updated to the new value.
     * If `milliseconds` is non-positive number, the key will be deleted rather than expired.
     * The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
     * See https://valkey.io/commands/pexpire/ for details.
     *
     * @param key - The key to set timeout on it.
     * @param milliseconds - The timeout in milliseconds.
     * @param option - The expire option.
     * @returns `true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
     * or operation skipped due to the provided arguments.
     *
     * @example
     * ```typescript
     * // Example usage of the pexpire method on a key with no previous expiry
     * const result = await client.pexpire("my_key", 60000, ExpireOptions.HasNoExpiry);
     * console.log(result); // Output: true - Indicates that a timeout of 60,000 milliseconds has been set for "my_key".
     * ```
     */
    public pexpire(
        key: string,
        milliseconds: number,
        option?: ExpireOptions,
    ): Promise<boolean> {
        return this.createWritePromise(
            createPExpire(key, milliseconds, option),
        );
    }

    /** Sets a timeout on `key`. It takes an absolute Unix timestamp (milliseconds since January 1, 1970) instead of specifying the number of milliseconds.
     * A timestamp in the past will delete the key immediately. After the timeout has expired, the key will automatically be deleted.
     * If `key` already has an existing expire set, the time to live is updated to the new value.
     * The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
     * See https://valkey.io/commands/pexpireat/ for details.
     *
     * @param key - The key to set timeout on it.
     * @param unixMilliseconds - The timeout in an absolute Unix timestamp.
     * @param option - The expire option.
     * @returns `true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
     * or operation skipped due to the provided arguments.
     *
     * @example
     * ```typescript
     * // Example usage of the pexpireAt method on a key with no previous expiry
     * const result = await client.pexpireAt("my_key", 1672531200000, ExpireOptions.HasNoExpiry);
     * console.log(result); // Output: true - Indicates that the expiration time for "my_key" was successfully set.
     * ```
     */
    public pexpireAt(
        key: string,
        unixMilliseconds: number,
        option?: ExpireOptions,
    ): Promise<number> {
        return this.createWritePromise(
            createPExpireAt(key, unixMilliseconds, option),
        );
    }

    /**
     * Returns the absolute Unix timestamp (since January 1, 1970) at which the given `key` will expire, in milliseconds.
     *
     * See https://valkey.io/commands/pexpiretime/ for details.
     *
     * @param key - The `key` to determine the expiration value of.
     * @returns The expiration Unix timestamp in seconds, `-2` if `key` does not exist or `-1` if `key` exists but has no associated expire.
     *
     * since Valkey version 7.0.0.
     *
     * @example
     * ```typescript
     * const result1 = client.pexpiretime("myKey");
     * console.log(result1); // Output: -2 - myKey doesn't exist.
     *
     * const result2 = client.set(myKey, "value");
     * const result3 = client.pexpireTime(myKey);
     * console.log(result2); // Output: -1 - myKey has no associated expiration.
     *
     * client.expire(myKey, 60);
     * const result3 = client.pexpireTime(myKey);
     * console.log(result3); // Output: 123456789 - the Unix timestamp (in milliseconds) when "myKey" will expire.
     * ```
     */
    public async pexpiretime(key: string): Promise<number> {
        return this.createWritePromise(createPExpireTime(key));
    }

    /** Returns the remaining time to live of `key` that has a timeout.
     * See https://valkey.io/commands/ttl/ for details.
     *
     * @param key - The key to return its timeout.
     * @returns TTL in seconds, -2 if `key` does not exist or -1 if `key` exists but has no associated expire.
     *
     * @example
     * ```typescript
     * // Example usage of the ttl method with existing key
     * const result = await client.ttl("my_key");
     * console.log(result); // Output: 3600 - Indicates that "my_key" has a remaining time to live of 3600 seconds.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the ttl method with existing key that has no associated expire.
     * const result = await client.ttl("key");
     * console.log(result); // Output: -1 - Indicates that the key has no associated expire.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the ttl method with a non-existing key
     * const result = await client.ttl("nonexistent_key");
     * console.log(result); // Output: -2 - Indicates that the key doesn't exist.
     * ```
     */
    public ttl(key: string): Promise<number> {
        return this.createWritePromise(createTTL(key));
    }

    /** Invokes a Lua script with its keys and arguments.
     * This method simplifies the process of invoking scripts on a Redis server by using an object that represents a Lua script.
     * The script loading, argument preparation, and execution will all be handled internally. If the script has not already been loaded,
     * it will be loaded automatically using the Redis `SCRIPT LOAD` command. After that, it will be invoked using the Redis `EVALSHA` command
     * See https://valkey.io/commands/script-load/ and https://valkey.io/commands/evalsha/ for details.
     *
     * @param script - The Lua script to execute.
     * @param options - The script option that contains keys and arguments for the script.
     * @returns A value that depends on the script that was executed.
     *
     * @example
     * ```typescript
     * const luaScript = new Script("return { KEYS[1], ARGV[1] }");
     * const scriptOptions = {
     *      keys: ["foo"],
     *      args: ["bar"],
     * };
     * const result = await invokeScript(luaScript, scriptOptions);
     * console.log(result); // Output: ['foo', 'bar']
     * ```
     */
    public invokeScript(
        script: Script,
        option?: ScriptOptions,
    ): Promise<ReturnType> {
        const scriptInvocation = command_request.ScriptInvocation.create({
            hash: script.getHash(),
            keys: option?.keys?.map((item) => {
                if (typeof item === "string") {
                    // Convert the string to a Uint8Array
                    return Buffer.from(item);
                } else {
                    // If it's already a Uint8Array, just return it
                    return item;
                }
            }),
            args: option?.args?.map((item) => {
                if (typeof item === "string") {
                    // Convert the string to a Uint8Array
                    return Buffer.from(item);
                } else {
                    // If it's already a Uint8Array, just return it
                    return item;
                }
            }),
        });
        return this.createWritePromise(scriptInvocation);
    }

    /** Adds members with their scores to the sorted set stored at `key`.
     * If a member is already a part of the sorted set, its score is updated.
     * See https://valkey.io/commands/zadd/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param membersScoresMap - A mapping of members to their corresponding scores.
     * @param options - The ZAdd options.
     * @returns The number of elements added to the sorted set.
     * If `changed` is set, returns the number of elements updated in the sorted set.
     *
     * @example
     * ```typescript
     * // Example usage of the zadd method to add elements to a sorted set
     * const result = await client.zadd("my_sorted_set", { "member1": 10.5, "member2": 8.2 });
     * console.log(result); // Output: 2 - Indicates that two elements have been added to the sorted set "my_sorted_set."
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the zadd method to update scores in an existing sorted set
     * const options = { conditionalChange: ConditionalChange.ONLY_IF_EXISTS, changed: true };
     * const result = await client.zadd("existing_sorted_set", { member1: 15.0, member2: 5.5 }, options);
     * console.log(result); // Output: 2 - Updates the scores of two existing members in the sorted set "existing_sorted_set."
     * ```
     */
    public zadd(
        key: string,
        membersScoresMap: Record<string, number>,
        options?: ZAddOptions,
    ): Promise<number> {
        return this.createWritePromise(
            createZAdd(key, membersScoresMap, options),
        );
    }

    /** Increments the score of member in the sorted set stored at `key` by `increment`.
     * If `member` does not exist in the sorted set, it is added with `increment` as its score (as if its previous score was 0.0).
     * If `key` does not exist, a new sorted set with the specified member as its sole member is created.
     * See https://valkey.io/commands/zadd/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param member - A member in the sorted set to increment.
     * @param increment - The score to increment the member.
     * @param options - The ZAdd options.
     * @returns The score of the member.
     * If there was a conflict with the options, the operation aborts and null is returned.
     *
     * @example
     * ```typescript
     * // Example usage of the zaddIncr method to add a member with a score to a sorted set
     * const result = await client.zaddIncr("my_sorted_set", member, 5.0);
     * console.log(result); // Output: 5.0
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the zaddIncr method to add or update a member with a score in an existing sorted set
     * const result = await client.zaddIncr("existing_sorted_set", member, "3.0", { UpdateOptions: "ScoreLessThanCurrent" });
     * console.log(result); // Output: null - Indicates that the member in the sorted set haven't been updated.
     * ```
     */
    public zaddIncr(
        key: string,
        member: string,
        increment: number,
        options?: ZAddOptions,
    ): Promise<number | null> {
        return this.createWritePromise(
            createZAdd(key, { [member]: increment }, options, true),
        );
    }

    /** Removes the specified members from the sorted set stored at `key`.
     * Specified members that are not a member of this set are ignored.
     * See https://valkey.io/commands/zrem/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param members - A list of members to remove from the sorted set.
     * @returns The number of members that were removed from the sorted set, not including non-existing members.
     * If `key` does not exist, it is treated as an empty sorted set, and this command returns 0.
     *
     * @example
     * ```typescript
     * // Example usage of the zrem function to remove members from a sorted set
     * const result = await client.zrem("my_sorted_set", ["member1", "member2"]);
     * console.log(result); // Output: 2 - Indicates that two members have been removed from the sorted set "my_sorted_set."
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the zrem function when the sorted set does not exist
     * const result = await client.zrem("non_existing_sorted_set", ["member1", "member2"]);
     * console.log(result); // Output: 0 - Indicates that no members were removed as the sorted set "non_existing_sorted_set" does not exist.
     * ```
     */
    public zrem(key: string, members: string[]): Promise<number> {
        return this.createWritePromise(createZRem(key, members));
    }

    /** Returns the cardinality (number of elements) of the sorted set stored at `key`.
     * See https://valkey.io/commands/zcard/ for more details.
     *
     * @param key - The key of the sorted set.
     * @returns The number of elements in the sorted set.
     * If `key` does not exist, it is treated as an empty sorted set, and this command returns 0.
     *
     * @example
     * ```typescript
     * // Example usage of the zcard method to get the cardinality of a sorted set
     * const result = await client.zcard("my_sorted_set");
     * console.log(result); // Output: 3 - Indicates that there are 3 elements in the sorted set "my_sorted_set".
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the zcard method with a non-existing key
     * const result = await client.zcard("non_existing_key");
     * console.log(result); // Output: 0
     * ```
     */
    public zcard(key: string): Promise<number> {
        return this.createWritePromise(createZCard(key));
    }

    /**
     * Returns the cardinality of the intersection of the sorted sets specified by `keys`.
     *
     * See https://valkey.io/commands/zintercard/ for more details.
     *
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @param keys - The keys of the sorted sets to intersect.
     * @param limit - An optional argument that can be used to specify a maximum number for the
     * intersection cardinality. If limit is not supplied, or if it is set to `0`, there will be no limit.
     * @returns The cardinality of the intersection of the given sorted sets.
     *
     * since - Redis version 7.0.0.
     *
     * @example
     * ```typescript
     * const cardinality = await client.zintercard(["key1", "key2"], 10);
     * console.log(cardinality); // Output: 3 - The intersection of the sorted sets at "key1" and "key2" has a cardinality of 3.
     * ```
     */
    public zintercard(keys: string[], limit?: number): Promise<number> {
        return this.createWritePromise(createZInterCard(keys, limit));
    }

    /**
     * Returns the difference between the first sorted set and all the successive sorted sets.
     * To get the elements with their scores, see {@link zdiffWithScores}.
     *
     * See https://valkey.io/commands/zdiff/ for more details.
     *
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @param keys - The keys of the sorted sets.
     * @returns An `array` of elements representing the difference between the sorted sets.
     * If the first key does not exist, it is treated as an empty sorted set, and the command returns an empty `array`.
     *
     * since Valkey version 6.2.0.
     *
     * @example
     * ```typescript
     * await client.zadd("zset1", {"member1": 1.0, "member2": 2.0, "member3": 3.0});
     * await client.zadd("zset2", {"member2": 2.0});
     * await client.zadd("zset3", {"member3": 3.0});
     * const result = await client.zdiff(["zset1", "zset2", "zset3"]);
     * console.log(result); // Output: ["member1"] - "member1" is in "zset1" but not "zset2" or "zset3".
     * ```
     */
    public zdiff(keys: string[]): Promise<string[]> {
        return this.createWritePromise(createZDiff(keys));
    }

    /**
     * Returns the difference between the first sorted set and all the successive sorted sets, with the associated
     * scores.
     *
     * See https://valkey.io/commands/zdiff/ for more details.
     *
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @param keys - The keys of the sorted sets.
     * @returns A map of elements and their scores representing the difference between the sorted sets.
     * If the first key does not exist, it is treated as an empty sorted set, and the command returns an empty `array`.
     *
     * since Valkey version 6.2.0.
     *
     * @example
     * ```typescript
     * await client.zadd("zset1", {"member1": 1.0, "member2": 2.0, "member3": 3.0});
     * await client.zadd("zset2", {"member2": 2.0});
     * await client.zadd("zset3", {"member3": 3.0});
     * const result = await client.zdiffWithScores(["zset1", "zset2", "zset3"]);
     * console.log(result); // Output: {"member1": 1.0} - "member1" is in "zset1" but not "zset2" or "zset3".
     * ```
     */
    public zdiffWithScores(keys: string[]): Promise<Record<string, number>> {
        return this.createWritePromise(createZDiffWithScores(keys));
    }

    /**
     * Calculates the difference between the first sorted set and all the successive sorted sets in `keys` and stores
     * the difference as a sorted set to `destination`, overwriting it if it already exists. Non-existent keys are
     * treated as empty sets.
     *
     * See https://valkey.io/commands/zdiffstore/ for more details.
     *
     * @remarks When in cluster mode, all keys in `keys` and `destination` must map to the same hash slot.
     * @param destination - The key for the resulting sorted set.
     * @param keys - The keys of the sorted sets to compare.
     * @returns The number of members in the resulting sorted set stored at `destination`.
     *
     * since Valkey version 6.2.0.
     *
     * @example
     * ```typescript
     * await client.zadd("zset1", {"member1": 1.0, "member2": 2.0});
     * await client.zadd("zset2", {"member1": 1.0});
     * const result1 = await client.zdiffstore("zset3", ["zset1", "zset2"]);
     * console.log(result1); // Output: 1 - One member exists in "key1" but not "key2", and this member was stored in "zset3".
     *
     * const result2 = await client.zrange("zset3", {start: 0, stop: -1});
     * console.log(result2); // Output: ["member2"] - "member2" is now stored in "my_sorted_set".
     * ```
     */
    public zdiffstore(destination: string, keys: string[]): Promise<number> {
        return this.createWritePromise(createZDiffStore(destination, keys));
    }

    /** Returns the score of `member` in the sorted set stored at `key`.
     * See https://valkey.io/commands/zscore/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param member - The member whose score is to be retrieved.
     * @returns The score of the member.
     * If `member` does not exist in the sorted set, null is returned.
     * If `key` does not exist, null is returned.
     *
     * @example
     * ```typescript
     * // Example usage of the zscore method∂∂ to get the score of a member in a sorted set
     * const result = await client.zscore("my_sorted_set", "member");
     * console.log(result); // Output: 10.5 - Indicates that the score of "member" in the sorted set "my_sorted_set" is 10.5.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the zscore method when the member does not exist in the sorted set
     * const result = await client.zscore("my_sorted_set", "non_existing_member");
     * console.log(result); // Output: null
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the zscore method with non existimng key
     * const result = await client.zscore("non_existing_set", "member");
     * console.log(result); // Output: null
     * ```
     */
    public zscore(key: string, member: string): Promise<number | null> {
        return this.createWritePromise(createZScore(key, member));
    }

    /**
     * Returns the scores associated with the specified `members` in the sorted set stored at `key`.
     *
     * See https://valkey.io/commands/zmscore/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param members - A list of members in the sorted set.
     * @returns An `array` of scores corresponding to `members`.
     * If a member does not exist in the sorted set, the corresponding value in the list will be `null`.
     *
     * since Valkey version 6.2.0.
     *
     * @example
     * ```typescript
     * const result = await client.zmscore("zset1", ["member1", "non_existent_member", "member2"]);
     * console.log(result); // Output: [1.0, null, 2.0] - "member1" has a score of 1.0, "non_existent_member" does not exist in the sorted set, and "member2" has a score of 2.0.
     * ```
     */
    public zmscore(key: string, members: string[]): Promise<(number | null)[]> {
        return this.createWritePromise(createZMScore(key, members));
    }

    /** Returns the number of members in the sorted set stored at `key` with scores between `minScore` and `maxScore`.
     * See https://valkey.io/commands/zcount/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param minScore - The minimum score to count from. Can be positive/negative infinity, or specific score and inclusivity.
     * @param maxScore - The maximum score to count up to. Can be positive/negative infinity, or specific score and inclusivity.
     * @returns The number of members in the specified score range.
     * If `key` does not exist, it is treated as an empty sorted set, and the command returns 0.
     * If `minScore` is greater than `maxScore`, 0 is returned.
     *
     * @example
     * ```typescript
     * // Example usage of the zcount method to count members in a sorted set within a score range
     * const result = await client.zcount("my_sorted_set", { value: 5.0, isInclusive: true }, InfScoreBoundary.PositiveInfinity);
     * console.log(result); // Output: 2 - Indicates that there are 2 members with scores between 5.0 (inclusive) and +inf in the sorted set "my_sorted_set".
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the zcount method to count members in a sorted set within a score range
     * const result = await client.zcount("my_sorted_set", { value: 5.0, isInclusive: true }, { value: 10.0, isInclusive: false });
     * console.log(result); // Output: 1 - Indicates that there is one member with score between 5.0 (inclusive) and 10.0 (exclusive) in the sorted set "my_sorted_set".
     * ```
     */
    public zcount(
        key: string,
        minScore: ScoreBoundary<number>,
        maxScore: ScoreBoundary<number>,
    ): Promise<number> {
        return this.createWritePromise(createZCount(key, minScore, maxScore));
    }

    /** Returns the specified range of elements in the sorted set stored at `key`.
     * ZRANGE can perform different types of range queries: by index (rank), by the score, or by lexicographical order.
     *
     * See https://valkey.io/commands/zrange/ for more details.
     * To get the elements with their scores, see `zrangeWithScores`.
     *
     * @param key - The key of the sorted set.
     * @param rangeQuery - The range query object representing the type of range query to perform.
     * For range queries by index (rank), use RangeByIndex.
     * For range queries by lexicographical order, use RangeByLex.
     * For range queries by score, use RangeByScore.
     * @param reverse - If true, reverses the sorted set, with index 0 as the element with the highest score.
     * @returns A list of elements within the specified range.
     * If `key` does not exist, it is treated as an empty sorted set, and the command returns an empty array.
     *
     * @example
     * ```typescript
     * // Example usage of zrange method to retrieve all members of a sorted set in ascending order
     * const result = await client.zrange("my_sorted_set", { start: 0, stop: -1 });
     * console.log(result1); // Output: ['member1', 'member2', 'member3'] - Returns all members in ascending order.
     * ```
     * @example
     * ```typescript
     * // Example usage of zrange method to retrieve members within a score range in ascending order
     * const result = await client.zrange("my_sorted_set", {
     *              start: InfScoreBoundary.NegativeInfinity,
     *              stop: { value: 3, isInclusive: false },
     *              type: "byScore",
     *           });
     * console.log(result); // Output: ['member2', 'member3'] - Returns members with scores within the range of negative infinity to 3, in ascending order.
     * ```
     */
    public zrange(
        key: string,
        rangeQuery: RangeByScore | RangeByLex | RangeByIndex,
        reverse: boolean = false,
    ): Promise<string[]> {
        return this.createWritePromise(createZRange(key, rangeQuery, reverse));
    }

    /** Returns the specified range of elements with their scores in the sorted set stored at `key`.
     * Similar to ZRANGE but with a WITHSCORE flag.
     * See https://valkey.io/commands/zrange/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param rangeQuery - The range query object representing the type of range query to perform.
     * For range queries by index (rank), use RangeByIndex.
     * For range queries by lexicographical order, use RangeByLex.
     * For range queries by score, use RangeByScore.
     * @param reverse - If true, reverses the sorted set, with index 0 as the element with the highest score.
     * @returns A map of elements and their scores within the specified range.
     * If `key` does not exist, it is treated as an empty sorted set, and the command returns an empty map.
     *
     * @example
     * ```typescript
     * // Example usage of zrangeWithScores method to retrieve members within a score range with their scores
     * const result = await client.zrangeWithScores("my_sorted_set", {
     *              start: { value: 10, isInclusive: false },
     *              stop: { value: 20, isInclusive: false },
     *              type: "byScore",
     *           });
     * console.log(result); // Output: {'member1': 10.5, 'member2': 15.2} - Returns members with scores between 10 and 20 with their scores.
     * ```
     * @example
     * ```typescript
     * // Example usage of zrangeWithScores method to retrieve members within a score range with their scores
     * const result = await client.zrangeWithScores("my_sorted_set", {
     *              start: InfScoreBoundary.NegativeInfinity,
     *              stop: { value: 3, isInclusive: false },
     *              type: "byScore",
     *           });
     * console.log(result); // Output: {'member4': -2.0, 'member7': 1.5} - Returns members with scores within the range of negative infinity to 3, with their scores.
     * ```
     */
    public zrangeWithScores(
        key: string,
        rangeQuery: RangeByScore | RangeByLex | RangeByIndex,
        reverse: boolean = false,
    ): Promise<Record<string, number>> {
        return this.createWritePromise(
            createZRangeWithScores(key, rangeQuery, reverse),
        );
    }

    /**
     * Computes the intersection of sorted sets given by the specified `keys` and stores the result in `destination`.
     * If `destination` already exists, it is overwritten. Otherwise, a new sorted set will be created.
     * To get the result directly, see `zinter_withscores`.
     *
     * When in cluster mode, `destination` and all keys in `keys` must map to the same hash slot.
     *
     * See https://valkey.io/commands/zinterstore/ for more details.
     *
     * @param destination - The key of the destination sorted set.
     * @param keys - The keys of the sorted sets with possible formats:
     *  string[] - for keys only.
     *  KeyWeight[] - for weighted keys with score multipliers.
     * @param aggregationType - Specifies the aggregation strategy to apply when combining the scores of elements. See `AggregationType`.
     * @returns The number of elements in the resulting sorted set stored at `destination`.
     *
     * @example
     * ```typescript
     * // Example usage of zinterstore command with an existing key
     * await client.zadd("key1", {"member1": 10.5, "member2": 8.2})
     * await client.zadd("key2", {"member1": 9.5})
     * await client.zinterstore("my_sorted_set", ["key1", "key2"]) // Output: 1 - Indicates that the sorted set "my_sorted_set" contains one element.
     * await client.zrange_withscores("my_sorted_set", RangeByIndex(0, -1)) // Output: {'member1': 20}  - "member1"  is now stored in "my_sorted_set" with score of 20.
     * await client.zinterstore("my_sorted_set", ["key1", "key2"] , AggregationType.MAX ) // Output: 1 - Indicates that the sorted set "my_sorted_set" contains one element, and it's score is the maximum score between the sets.
     * await client.zrange_withscores("my_sorted_set", RangeByIndex(0, -1)) // Output: {'member1': 10.5}  - "member1"  is now stored in "my_sorted_set" with score of 10.5.
     * ```
     */
    public zinterstore(
        destination: string,
        keys: string[] | KeyWeight[],
        aggregationType?: AggregationType,
    ): Promise<number> {
        return this.createWritePromise(
            createZInterstore(destination, keys, aggregationType),
        );
    }

    /**
     * Returns a random member from the sorted set stored at `key`.
     *
     * See https://valkey.io/commands/zrandmember/ for more details.
     *
     * @param keys - The key of the sorted set.
     * @returns A string representing a random member from the sorted set.
     *     If the sorted set does not exist or is empty, the response will be `null`.
     *
     * @example
     * ```typescript
     * const payload1 = await client.zrandmember("mySortedSet");
     * console.log(payload1); // Output: "Glide" (a random member from the set)
     * ```
     *
     * @example
     * ```typescript
     * const payload2 = await client.zrandmember("nonExistingSortedSet");
     * console.log(payload2); // Output: null since the sorted set does not exist.
     * ```
     */
    public async zrandmember(key: string): Promise<string | null> {
        return this.createWritePromise(createZRandMember(key));
    }

    /**
     * Returns random members from the sorted set stored at `key`.
     *
     * See https://valkey.io/commands/zrandmember/ for more details.
     *
     * @param keys - The key of the sorted set.
     * @param count - The number of members to return.
     *     If `count` is positive, returns unique members.
     *     If negative, allows for duplicates.
     * @returns An `array` of members from the sorted set.
     *     If the sorted set does not exist or is empty, the response will be an empty `array`.
     *
     * @example
     * ```typescript
     * const payload1 = await client.zrandmemberWithCount("mySortedSet", -3);
     * console.log(payload1); // Output: ["Glide", "GLIDE", "node"]
     * ```
     *
     * @example
     * ```typescript
     * const payload2 = await client.zrandmemberWithCount("nonExistingKey", 3);
     * console.log(payload1); // Output: [] since the sorted set does not exist.
     * ```
     */
    public async zrandmemberWithCount(
        key: string,
        count: number,
    ): Promise<string[]> {
        return this.createWritePromise(createZRandMember(key, count));
    }

    /**
     * Returns random members with scores from the sorted set stored at `key`.
     *
     * See https://valkey.io/commands/zrandmember/ for more details.
     *
     * @param keys - The key of the sorted set.
     * @param count - The number of members to return.
     *     If `count` is positive, returns unique members.
     *     If negative, allows for duplicates.
     * @returns A 2D `array` of `[member, score]` `arrays`, where
     *     member is a `string` and score is a `number`.
     *     If the sorted set does not exist or is empty, the response will be an empty `array`.
     *
     * @example
     * ```typescript
     * const payload1 = await client.zrandmemberWithCountWithScore("mySortedSet", -3);
     * console.log(payload1); // Output: [["Glide", 1.0], ["GLIDE", 1.0], ["node", 2.0]]
     * ```
     *
     * @example
     * ```typescript
     * const payload2 = await client.zrandmemberWithCountWithScore("nonExistingKey", 3);
     * console.log(payload1); // Output: [] since the sorted set does not exist.
     * ```
     */
    public async zrandmemberWithCountWithScores(
        key: string,
        count: number,
    ): Promise<[string, number][]> {
        return this.createWritePromise(createZRandMember(key, count, true));
    }

    /** Returns the length of the string value stored at `key`.
     * See https://valkey.io/commands/strlen/ for more details.
     *
     * @param key - The key to check its length.
     * @returns - The length of the string value stored at key
     * If `key` does not exist, it is treated as an empty string, and the command returns 0.
     *
     * @example
     * ```typescript
     * // Example usage of strlen method with an existing key
     * await client.set("key", "GLIDE");
     * const len1 = await client.strlen("key");
     * console.log(len1); // Output: 5
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of strlen method with a non-existing key
     * const len2 = await client.strlen("non_existing_key");
     * console.log(len2); // Output: 0
     * ```
     */
    public strlen(key: string): Promise<number> {
        return this.createWritePromise(createStrlen(key));
    }

    /** Returns the string representation of the type of the value stored at `key`.
     * See https://valkey.io/commands/type/ for more details.
     *
     * @param key - The `key` to check its data type.
     * @returns If the `key` exists, the type of the stored value is returned. Otherwise, a "none" string is returned.
     *
     * @example
     * ```typescript
     * // Example usage of type method with a string value
     * await client.set("key", "value");
     * const type = await client.type("key");
     * console.log(type); // Output: 'string'
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of type method with a list
     * await client.lpush("key", ["value"]);
     * const type = await client.type("key");
     * console.log(type); // Output: 'list'
     * ```
     */
    public type(key: string): Promise<string> {
        return this.createWritePromise(createType(key));
    }

    /** Removes and returns the members with the lowest scores from the sorted set stored at `key`.
     * If `count` is provided, up to `count` members with the lowest scores are removed and returned.
     * Otherwise, only one member with the lowest score is removed and returned.
     * See https://valkey.io/commands/zpopmin for more details.
     *
     * @param key - The key of the sorted set.
     * @param count - Specifies the quantity of members to pop. If not specified, pops one member.
     * @returns A map of the removed members and their scores, ordered from the one with the lowest score to the one with the highest.
     * If `key` doesn't exist, it will be treated as an empty sorted set and the command returns an empty map.
     * If `count` is higher than the sorted set's cardinality, returns all members and their scores.
     *
     * @example
     * ```typescript
     * // Example usage of zpopmin method to remove and return the member with the lowest score from a sorted set
     * const result = await client.zpopmin("my_sorted_set");
     * console.log(result); // Output: {'member1': 5.0} - Indicates that 'member1' with a score of 5.0 has been removed from the sorted set.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of zpopmin method to remove and return multiple members with the lowest scores from a sorted set
     * const result = await client.zpopmin("my_sorted_set", 2);
     * console.log(result); // Output: {'member3': 7.5 , 'member2': 8.0} - Indicates that 'member3' with a score of 7.5 and 'member2' with a score of 8.0 have been removed from the sorted set.
     * ```
     */
    public zpopmin(
        key: string,
        count?: number,
    ): Promise<Record<string, number>> {
        return this.createWritePromise(createZPopMin(key, count));
    }

    /**
     * Blocks the connection until it removes and returns a member with the lowest score from the
     * first non-empty sorted set, with the given `key` being checked in the order they
     * are provided.
     * `BZPOPMIN` is the blocking variant of {@link zpopmin}.
     *
     * See https://valkey.io/commands/bzpopmin/ for more details.
     *
     * @remarks When in cluster mode, `keys` must map to the same hash slot.
     * @param keys - The keys of the sorted sets.
     * @param timeout - The number of seconds to wait for a blocking operation to complete. A value of
     *     `0` will block indefinitely. Since 6.0.0: timeout is interpreted as a double instead of an integer.
     * @returns An `array` containing the key where the member was popped out, the member, itself, and the member score.
     *     If no member could be popped and the `timeout` expired, returns `null`.
     *
     * @example
     * ```typescript
     * const data = await client.bzpopmin(["zset1", "zset2"], 0.5);
     * console.log(data); // Output: ["zset1", "a", 2];
     * ```
     */
    public async bzpopmin(
        keys: string[],
        timeout: number,
    ): Promise<[string, string, number] | null> {
        return this.createWritePromise(createBZPopMin(keys, timeout));
    }

    /** Removes and returns the members with the highest scores from the sorted set stored at `key`.
     * If `count` is provided, up to `count` members with the highest scores are removed and returned.
     * Otherwise, only one member with the highest score is removed and returned.
     * See https://valkey.io/commands/zpopmax for more details.
     *
     * @param key - The key of the sorted set.
     * @param count - Specifies the quantity of members to pop. If not specified, pops one member.
     * @returns A map of the removed members and their scores, ordered from the one with the highest score to the one with the lowest.
     * If `key` doesn't exist, it will be treated as an empty sorted set and the command returns an empty map.
     * If `count` is higher than the sorted set's cardinality, returns all members and their scores, ordered from highest to lowest.
     *
     * @example
     * ```typescript
     * // Example usage of zpopmax method to remove and return the member with the highest score from a sorted set
     * const result = await client.zpopmax("my_sorted_set");
     * console.log(result); // Output: {'member1': 10.0} - Indicates that 'member1' with a score of 10.0 has been removed from the sorted set.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of zpopmax method to remove and return multiple members with the highest scores from a sorted set
     * const result = await client.zpopmax("my_sorted_set", 2);
     * console.log(result); // Output: {'member2': 8.0, 'member3': 7.5} - Indicates that 'member2' with a score of 8.0 and 'member3' with a score of 7.5 have been removed from the sorted set.
     * ```
     */
    public zpopmax(
        key: string,
        count?: number,
    ): Promise<Record<string, number>> {
        return this.createWritePromise(createZPopMax(key, count));
    }

    /**
     * Blocks the connection until it removes and returns a member with the highest score from the
     * first non-empty sorted set, with the given `key` being checked in the order they
     * are provided.
     * `BZPOPMAX` is the blocking variant of {@link zpopmax}.
     *
     * See https://valkey.io/commands/zpopmax/ for more details.
     *
     * @remarks When in cluster mode, `keys` must map to the same hash slot.
     * @param keys - The keys of the sorted sets.
     * @param timeout - The number of seconds to wait for a blocking operation to complete. A value of
     *     `0` will block indefinitely. Since 6.0.0: timeout is interpreted as a double instead of an integer.
     * @returns An `array` containing the key where the member was popped out, the member, itself, and the member score.
     *     If no member could be popped and the `timeout` expired, returns `null`.
     *
     * @example
     * ```typescript
     * const data = await client.bzpopmax(["zset1", "zset2"], 0.5);
     * console.log(data); // Output: ["zset1", "c", 2];
     * ```
     */
    public async bzpopmax(
        keys: string[],
        timeout: number,
    ): Promise<[string, string, number] | null> {
        return this.createWritePromise(createBZPopMax(keys, timeout));
    }

    /** Returns the remaining time to live of `key` that has a timeout, in milliseconds.
     * See https://valkey.io/commands/pttl for more details.
     *
     * @param key - The key to return its timeout.
     * @returns TTL in milliseconds. -2 if `key` does not exist, -1 if `key` exists but has no associated expire.
     *
     * @example
     * ```typescript
     * // Example usage of pttl method with an existing key
     * const result = await client.pttl("my_key");
     * console.log(result); // Output: 5000 - Indicates that the key "my_key" has a remaining time to live of 5000 milliseconds.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of pttl method with a non-existing key
     * const result = await client.pttl("non_existing_key");
     * console.log(result); // Output: -2 - Indicates that the key "non_existing_key" does not exist.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of pttl method with an exisiting key that has no associated expire.
     * const result = await client.pttl("key");
     * console.log(result); // Output: -1 - Indicates that the key "key" has no associated expire.
     * ```
     */
    public pttl(key: string): Promise<number> {
        return this.createWritePromise(createPTTL(key));
    }

    /** Removes all elements in the sorted set stored at `key` with rank between `start` and `end`.
     * Both `start` and `end` are zero-based indexes with 0 being the element with the lowest score.
     * These indexes can be negative numbers, where they indicate offsets starting at the element with the highest score.
     * See https://valkey.io/commands/zremrangebyrank/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param start - The starting point of the range.
     * @param end - The end of the range.
     * @returns The number of members removed.
     * If `start` exceeds the end of the sorted set, or if `start` is greater than `end`, 0 returned.
     * If `end` exceeds the actual end of the sorted set, the range will stop at the actual end of the sorted set.
     * If `key` does not exist 0 will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of zremRangeByRank method
     * const result = await client.zremRangeByRank("my_sorted_set", 0, 2);
     * console.log(result); // Output: 3 - Indicates that three elements have been removed from the sorted set "my_sorted_set" between ranks 0 and 2.
     * ```
     */
    public zremRangeByRank(
        key: string,
        start: number,
        end: number,
    ): Promise<number> {
        return this.createWritePromise(createZRemRangeByRank(key, start, end));
    }

    /**
     * Removes all elements in the sorted set stored at `key` with lexicographical order between `minLex` and `maxLex`.
     *
     * See https://valkey.io/commands/zremrangebylex/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param minLex - The minimum lex to count from. Can be positive/negative infinity, or a specific lex and inclusivity.
     * @param maxLex - The maximum lex to count up to. Can be positive/negative infinity, or a specific lex and inclusivity.
     * @returns The number of members removed.
     * If `key` does not exist, it is treated as an empty sorted set, and the command returns 0.
     * If `minLex` is greater than `maxLex`, 0 is returned.
     *
     * @example
     * ```typescript
     * // Example usage of zremRangeByLex method to remove members from a sorted set based on lexicographical order range
     * const result = await client.zremRangeByLex("my_sorted_set", { value: "a", isInclusive: false }, { value: "e" });
     * console.log(result); // Output: 4 - Indicates that 4 members, with lexicographical values ranging from "a" (exclusive) to "e" (inclusive), have been removed from "my_sorted_set".
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of zremRangeByLex method when the sorted set does not exist
     * const result = await client.zremRangeByLex("non_existing_sorted_set", InfScoreBoundary.NegativeInfinity, { value: "e" });
     * console.log(result); // Output: 0 - Indicates that no elements were removed.
     * ```
     */
    public zremRangeByLex(
        key: string,
        minLex: ScoreBoundary<string>,
        maxLex: ScoreBoundary<string>,
    ): Promise<number> {
        return this.createWritePromise(
            createZRemRangeByLex(key, minLex, maxLex),
        );
    }

    /** Removes all elements in the sorted set stored at `key` with a score between `minScore` and `maxScore`.
     * See https://valkey.io/commands/zremrangebyscore/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param minScore - The minimum score to remove from. Can be positive/negative infinity, or specific score and inclusivity.
     * @param maxScore - The maximum score to remove to. Can be positive/negative infinity, or specific score and inclusivity.
     * @returns the number of members removed.
     * If `key` does not exist, it is treated as an empty sorted set, and the command returns 0.
     * If `minScore` is greater than `maxScore`, 0 is returned.
     *
     * @example
     * ```typescript
     * // Example usage of zremRangeByScore method to remove members from a sorted set based on score range
     * const result = await client.zremRangeByScore("my_sorted_set", { value: 5.0, isInclusive: true }, InfScoreBoundary.PositiveInfinity);
     * console.log(result); // Output: 2 - Indicates that 2 members with scores between 5.0 (inclusive) and +inf have been removed from the sorted set "my_sorted_set".
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of zremRangeByScore method when the sorted set does not exist
     * const result = await client.zremRangeByScore("non_existing_sorted_set", { value: 5.0, isInclusive: true }, { value: 10.0, isInclusive: false });
     * console.log(result); // Output: 0 - Indicates that no members were removed as the sorted set "non_existing_sorted_set" does not exist.
     * ```
     */
    public zremRangeByScore(
        key: string,
        minScore: ScoreBoundary<number>,
        maxScore: ScoreBoundary<number>,
    ): Promise<number> {
        return this.createWritePromise(
            createZRemRangeByScore(key, minScore, maxScore),
        );
    }

    /**
     * Returns the number of members in the sorted set stored at 'key' with scores between 'minLex' and 'maxLex'.
     *
     * See https://valkey.io/commands/zlexcount/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param minLex - The minimum lex to count from. Can be positive/negative infinity, or a specific lex and inclusivity.
     * @param maxLex - The maximum lex to count up to. Can be positive/negative infinity, or a specific lex and inclusivity.
     * @returns The number of members in the specified lex range.
     * If 'key' does not exist, it is treated as an empty sorted set, and the command returns '0'.
     * If maxLex is less than minLex, '0' is returned.
     *
     * @example
     * ```typescript
     * const result = await client.zlexcount("my_sorted_set", {value: "c"}, InfScoreBoundary.PositiveInfinity);
     * console.log(result); // Output: 2 - Indicates that there are 2 members with lex scores between "c" (inclusive) and positive infinity in the sorted set "my_sorted_set".
     * ```
     *
     * @example
     * ```typescript
     * const result = await client.zlexcount("my_sorted_set", {value: "c"}, {value: "k", isInclusive: false});
     * console.log(result); // Output: 1 - Indicates that there is one member with a lex score between "c" (inclusive) and "k" (exclusive) in the sorted set "my_sorted_set".
     * ```
     */
    public async zlexcount(
        key: string,
        minLex: ScoreBoundary<string>,
        maxLex: ScoreBoundary<string>,
    ): Promise<number> {
        return this.createWritePromise(createZLexCount(key, minLex, maxLex));
    }

    /** Returns the rank of `member` in the sorted set stored at `key`, with scores ordered from low to high.
     * See https://valkey.io/commands/zrank for more details.
     * To get the rank of `member` with its score, see `zrankWithScore`.
     *
     * @param key - The key of the sorted set.
     * @param member - The member whose rank is to be retrieved.
     * @returns The rank of `member` in the sorted set.
     * If `key` doesn't exist, or if `member` is not present in the set, null will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of zrank method to retrieve the rank of a member in a sorted set
     * const result = await client.zrank("my_sorted_set", "member2");
     * console.log(result); // Output: 1 - Indicates that "member2" has the second-lowest score in the sorted set "my_sorted_set".
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of zrank method with a non-existing member
     * const result = await client.zrank("my_sorted_set", "non_existing_member");
     * console.log(result); // Output: null - Indicates that "non_existing_member" is not present in the sorted set "my_sorted_set".
     * ```
     */
    public zrank(key: string, member: string): Promise<number | null> {
        return this.createWritePromise(createZRank(key, member));
    }

    /** Returns the rank of `member` in the sorted set stored at `key` with its score, where scores are ordered from the lowest to highest.
     * See https://valkey.io/commands/zrank for more details.
     *
     * @param key - The key of the sorted set.
     * @param member - The member whose rank is to be retrieved.
     * @returns A list containing the rank and score of `member` in the sorted set.
     * If `key` doesn't exist, or if `member` is not present in the set, null will be returned.
     *
     * since - Redis version 7.2.0.
     *
     * @example
     * ```typescript
     * // Example usage of zrank_withscore method to retrieve the rank and score of a member in a sorted set
     * const result = await client.zrank_withscore("my_sorted_set", "member2");
     * console.log(result); // Output: [1, 6.0] - Indicates that "member2" with score 6.0 has the second-lowest score in the sorted set "my_sorted_set".
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of zrank_withscore method with a non-existing member
     * const result = await client.zrank_withscore("my_sorted_set", "non_existing_member");
     * console.log(result); // Output: null - Indicates that "non_existing_member" is not present in the sorted set "my_sorted_set".
     * ```
     */
    public zrankWithScore(
        key: string,
        member: string,
    ): Promise<number[] | null> {
        return this.createWritePromise(createZRank(key, member, true));
    }

    /**
     * Returns the rank of `member` in the sorted set stored at `key`, where
     * scores are ordered from the highest to lowest, starting from 0.
     * To get the rank of `member` with its score, see {@link zrevrankWithScore}.
     *
     * See https://valkey.io/commands/zrevrank/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param member - The member whose rank is to be retrieved.
     * @returns The rank of `member` in the sorted set, where ranks are ordered from high to low based on scores.
     *     If `key` doesn't exist, or if `member` is not present in the set, `null` will be returned.
     *
     * @example
     * ```typescript
     * const result = await client.zrevrank("my_sorted_set", "member2");
     * console.log(result); // Output: 1 - Indicates that "member2" has the second-highest score in the sorted set "my_sorted_set".
     * ```
     */
    public zrevrank(key: string, member: string): Promise<number | null> {
        return this.createWritePromise(createZRevRank(key, member));
    }

    /**
     * Returns the rank of `member` in the sorted set stored at `key` with its
     * score, where scores are ordered from the highest to lowest, starting from 0.
     *
     * See https://valkey.io/commands/zrevrank/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param member - The member whose rank is to be retrieved.
     * @returns A list containing the rank and score of `member` in the sorted set, where ranks
     *     are ordered from high to low based on scores.
     *     If `key` doesn't exist, or if `member` is not present in the set, `null` will be returned.
     *
     * since - Valkey version 7.2.0.
     *
     * @example
     * ```typescript
     * const result = await client.zrevankWithScore("my_sorted_set", "member2");
     * console.log(result); // Output: [1, 6.0] - Indicates that "member2" with score 6.0 has the second-highest score in the sorted set "my_sorted_set".
     * ```
     */
    public zrevrankWithScore(
        key: string,
        member: string,
    ): Promise<(number[] | null)[]> {
        return this.createWritePromise(createZRevRankWithScore(key, member));
    }

    /**
     * Adds an entry to the specified stream stored at `key`. If the `key` doesn't exist, the stream is created.
     * See https://valkey.io/commands/xadd/ for more details.
     *
     * @param key - The key of the stream.
     * @param values - field-value pairs to be added to the entry.
     * @param options - options detailing how to add to the stream.
     * @returns The id of the added entry, or `null` if `options.makeStream` is set to `false` and no stream with the matching `key` exists.
     */
    public xadd(
        key: string,
        values: [string, string][],
        options?: StreamAddOptions,
    ): Promise<string | null> {
        return this.createWritePromise(createXAdd(key, values, options));
    }

    /**
     * Removes the specified entries by id from a stream, and returns the number of entries deleted.
     *
     * See https://valkey.io/commands/xdel for more details.
     *
     * @param key - The key of the stream.
     * @param ids - An array of entry ids.
     * @returns The number of entries removed from the stream. This number may be less than the number of entries in
     *      `ids`, if the specified `ids` don't exist in the stream.
     *
     * @example
     * ```typescript
     * console.log(await client.xdel("key", ["1538561698944-0", "1538561698944-1"]));
     * // Output is 2 since the stream marked 2 entries as deleted.
     * ```
     */
    public xdel(key: string, ids: string[]): Promise<number> {
        return this.createWritePromise(createXDel(key, ids));
    }

    /**
     * Trims the stream stored at `key` by evicting older entries.
     * See https://valkey.io/commands/xtrim/ for more details.
     *
     * @param key - the key of the stream
     * @param options - options detailing how to trim the stream.
     * @returns The number of entries deleted from the stream. If `key` doesn't exist, 0 is returned.
     */
    public xtrim(key: string, options: StreamTrimOptions): Promise<number> {
        return this.createWritePromise(createXTrim(key, options));
    }

    /**
     * Reads entries from the given streams.
     * See https://valkey.io/commands/xread/ for more details.
     *
     * @param keys_and_ids - pairs of keys and entry ids to read from. A pair is composed of a stream's key and the id of the entry after which the stream will be read.
     * @param options - options detailing how to read the stream.
     * @returns A map of stream keys, to a map of stream ids, to an array of entries.
     * @example
     * ```typescript
     * const streamResults = await client.xread({"my_stream": "0-0", "writers": "0-0"});
     * console.log(result); // Output:
     * // {
     * //     "my_stream": {
     * //         "1526984818136-0": [["duration", "1532"], ["event-id", "5"], ["user-id", "7782813"]],
     * //         "1526999352406-0": [["duration", "812"], ["event-id", "9"], ["user-id", "388234"]],
     * //     },
     * //     "writers": {
     * //         "1526985676425-0": [["name", "Virginia"], ["surname", "Woolf"]],
     * //         "1526985685298-0": [["name", "Jane"], ["surname", "Austen"]],
     * //     }
     * // }
     * ```
     */
    public xread(
        keys_and_ids: Record<string, string>,
        options?: StreamReadOptions,
    ): Promise<Record<string, Record<string, [string, string][]>>> {
        return this.createWritePromise(createXRead(keys_and_ids, options));
    }

    /**
     * Returns the number of entries in the stream stored at `key`.
     *
     * See https://valkey.io/commands/xlen/ for more details.
     *
     * @param key - The key of the stream.
     * @returns The number of entries in the stream. If `key` does not exist, returns `0`.
     *
     * @example
     * ```typescript
     * const numEntries = await client.xlen("my_stream");
     * console.log(numEntries); // Output: 2 - "my_stream" contains 2 entries.
     * ```
     */
    public xlen(key: string): Promise<number> {
        return this.createWritePromise(createXLen(key));
    }

    /**
     * Returns the list of all consumers and their attributes for the given consumer group of the
     * stream stored at `key`.
     *
     * See https://valkey.io/commands/xinfo-consumers/ for more details.
     *
     * @param key - The key of the stream.
     * @param group - The consumer group name.
     * @returns An `Array` of `Records`, where each mapping contains the attributes
     *     of a consumer for the given consumer group of the stream at `key`.
     *
     * @example
     * ```typescript
     * const result = await client.xinfoConsumers("my_stream", "my_group");
     * console.log(result); // Output:
     * // [
     * //     {
     * //         "name": "Alice",
     * //         "pending": 1,
     * //         "idle": 9104628,
     * //         "inactive": 18104698   // Added in 7.2.0
     * //     },
     * //     ...
     * // ]
     * ```
     */
    public async xinfoConsumers(
        key: string,
        group: string,
    ): Promise<Record<string, string | number>[]> {
        return this.createWritePromise(createXInfoConsumers(key, group));
    }

    /**
     * Changes the ownership of a pending message.
     *
     * See https://valkey.io/commands/xclaim/ for more details.
     *
     * @param key - The key of the stream.
     * @param group - The consumer group name.
     * @param consumer - The group consumer.
     * @param minIdleTime - The minimum idle time for the message to be claimed.
     * @param ids - An array of entry ids.
     * @param options - (Optional) Stream claim options {@link StreamClaimOptions}.
     * @returns A `Record` of message entries that are claimed by the consumer.
     *
     * @example
     * ```typescript
     * const result = await client.xclaim("myStream", "myGroup", "myConsumer", 42,
     *     ["1-0", "2-0", "3-0"], { idle: 500, retryCount: 3, isForce: true });
     * console.log(result); // Output:
     * // {
     * //     "2-0": [["duration", "1532"], ["event-id", "5"], ["user-id", "7782813"]]
     * // }
     * ```
     */
    public async xclaim(
        key: string,
        group: string,
        consumer: string,
        minIdleTime: number,
        ids: string[],
        options?: StreamClaimOptions,
    ): Promise<Record<string, [string, string][]>> {
        return this.createWritePromise(
            createXClaim(key, group, consumer, minIdleTime, ids, options),
        );
    }

    /**
     * Changes the ownership of a pending message. This function returns an `array` with
     * only the message/entry IDs, and is equivalent to using `JUSTID` in the Valkey API.
     *
     * See https://valkey.io/commands/xclaim/ for more details.
     *
     * @param key - The key of the stream.
     * @param group - The consumer group name.
     * @param consumer - The group consumer.
     * @param minIdleTime - The minimum idle time for the message to be claimed.
     * @param ids - An array of entry ids.
     * @param options - (Optional) Stream claim options {@link StreamClaimOptions}.
     * @returns An `array` of message ids claimed by the consumer.
     *
     * @example
     * ```typescript
     * const result = await client.xclaimJustId("my_stream", "my_group", "my_consumer", 42,
     *     ["1-0", "2-0", "3-0"], { idle: 500, retryCount: 3, isForce: true });
     * console.log(result); // Output: [ "2-0", "3-0" ]
     * ```
     */
    public async xclaimJustId(
        key: string,
        group: string,
        consumer: string,
        minIdleTime: number,
        ids: string[],
        options?: StreamClaimOptions,
    ): Promise<string[]> {
        return this.createWritePromise(
            createXClaim(key, group, consumer, minIdleTime, ids, options, true),
        );
    }

    /**
     * Creates a new consumer group uniquely identified by `groupname` for the stream stored at `key`.
     *
     * See https://valkey.io/commands/xgroup-create/ for more details.
     *
     * @param key - The key of the stream.
     * @param groupName - The newly created consumer group name.
     * @param id - Stream entry ID that specifies the last delivered entry in the stream from the new
     *     group’s perspective. The special ID `"$"` can be used to specify the last entry in the stream.
     * @returns `"OK"`.
     *
     * @example
     * ```typescript
     * // Create the consumer group "mygroup", using zero as the starting ID:
     * console.log(await client.xgroupCreate("mystream", "mygroup", "0-0")); // Output is "OK"
     * ```
     */
    public async xgroupCreate(
        key: string,
        groupName: string,
        id: string,
        options?: StreamGroupOptions,
    ): Promise<string> {
        return this.createWritePromise(
            createXGroupCreate(key, groupName, id, options),
        );
    }

    /**
     * Destroys the consumer group `groupname` for the stream stored at `key`.
     *
     * See https://valkey.io/commands/xgroup-destroy/ for more details.
     *
     * @param key - The key of the stream.
     * @param groupname - The newly created consumer group name.
     * @returns `true` if the consumer group is destroyed. Otherwise, `false`.
     *
     * @example
     * ```typescript
     * // Destroys the consumer group "mygroup"
     * console.log(await client.xgroupDestroy("mystream", "mygroup")); // Output is true
     * ```
     */
    public async xgroupDestroy(
        key: string,
        groupName: string,
    ): Promise<boolean> {
        return this.createWritePromise(createXGroupDestroy(key, groupName));
    }

    /**
     * Returns information about the stream stored at `key`.
     *
     * @param key - The key of the stream.
     * @param fullOptions - If `true`, returns verbose information with a limit of the first 10 PEL entries.
     * If `number` is specified, returns verbose information limiting the returned PEL entries.
     * If `0` is specified, returns verbose information with no limit.
     * @returns A {@link ReturnTypeXinfoStream} of detailed stream information for the given `key`. See
     *     the example for a sample response.
     * @example
     * ```typescript
     * const infoResult = await client.xinfoStream("my_stream");
     * console.log(infoResult);
     * // Output: {
     * //   length: 2,
     * //   'radix-tree-keys': 1,
     * //   'radix-tree-nodes': 2,
     * //   'last-generated-id': '1719877599564-1',
     * //   'max-deleted-entry-id': '0-0',
     * //   'entries-added': 2,
     * //   'recorded-first-entry-id': '1719877599564-0',
     * //   'first-entry': [ '1719877599564-0', ['some_field", "some_value', ...] ],
     * //   'last-entry': [ '1719877599564-0', ['some_field", "some_value', ...] ],
     * //   groups: 1,
     * // }
     * ```
     *
     * @example
     * ```typescript
     * const infoResult = await client.xinfoStream("my_stream", true); // default limit of 10 entries
     * const infoResult = await client.xinfoStream("my_stream", 15); // limit of 15 entries
     * console.log(infoResult);
     * // Output: {
     * //   length: 2,
     * //   'radix-tree-keys': 1,
     * //   'radix-tree-nodes': 2,
     * //   'last-generated-id': '1719877599564-1',
     * //   'max-deleted-entry-id': '0-0',
     * //   'entries-added': 2,
     * //   'recorded-first-entry-id': '1719877599564-0',
     * //   entries: [ [ '1719877599564-0', ['some_field", "some_value', ...] ] ],
     * //   groups: [ {
     * //     name: 'group',
     * //     'last-delivered-id': '1719877599564-0',
     * //     'entries-read': 1,
     * //     lag: 1,
     * //     'pel-count': 1,
     * //     pending: [ [ '1719877599564-0', 'consumer', 1722624726802, 1 ] ],
     * //     consumers: [ {
     * //         name: 'consumer',
     * //         'seen-time': 1722624726802,
     * //         'active-time': 1722624726802,
     * //         'pel-count': 1,
     * //         pending: [ [ '1719877599564-0', 'consumer', 1722624726802, 1 ] ],
     * //         }
     * //       ]
     * //     }
     * //   ]
     * // }
     * ```
     */
    public async xinfoStream(
        key: string,
        fullOptions?: boolean | number,
    ): Promise<ReturnTypeXinfoStream> {
        return this.createWritePromise(
            createXInfoStream(key, fullOptions ?? false),
        );
    }

    /**
     * Creates a consumer named `consumerName` in the consumer group `groupName` for the stream stored at `key`.
     *
     * See https://valkey.io/commands/xgroup-createconsumer for more details.
     *
     * @param key - The key of the stream.
     * @param groupName - The consumer group name.
     * @param consumerName - The newly created consumer.
     * @returns `true` if the consumer is created. Otherwise, returns `false`.
     *
     * @example
     * ```typescript
     * // The consumer "myconsumer" was created in consumer group "mygroup" for the stream "mystream".
     * console.log(await client.xgroupCreateConsumer("mystream", "mygroup", "myconsumer")); // Output is true
     * ```
     */
    public async xgroupCreateConsumer(
        key: string,
        groupName: string,
        consumerName: string,
    ): Promise<boolean> {
        return this.createWritePromise(
            createXGroupCreateConsumer(key, groupName, consumerName),
        );
    }

    /**
     * Deletes a consumer named `consumerName` in the consumer group `groupName` for the stream stored at `key`.
     *
     * See https://valkey.io/commands/xgroup-delconsumer for more details.
     *
     * @param key - The key of the stream.
     * @param groupName - The consumer group name.
     * @param consumerName - The consumer to delete.
     * @returns The number of pending messages the `consumer` had before it was deleted.
     *
     * * @example
     * ```typescript
     * // Consumer "myconsumer" was deleted, and had 5 pending messages unclaimed.
     * console.log(await client.xgroupDelConsumer("mystream", "mygroup", "myconsumer")); // Output is 5
     * ```
     */
    public async xgroupDelConsumer(
        key: string,
        groupName: string,
        consumerName: string,
    ): Promise<number> {
        return this.createWritePromise(
            createXGroupDelConsumer(key, groupName, consumerName),
        );
    }

    private readonly MAP_READ_FROM_STRATEGY: Record<
        ReadFrom,
        connection_request.ReadFrom
    > = {
        primary: connection_request.ReadFrom.Primary,
        preferReplica: connection_request.ReadFrom.PreferReplica,
    };

    /** Returns the element at index `index` in the list stored at `key`.
     * The index is zero-based, so 0 means the first element, 1 the second element and so on.
     * Negative indices can be used to designate elements starting at the tail of the list.
     * Here, -1 means the last element, -2 means the penultimate and so forth.
     * See https://valkey.io/commands/lindex/ for more details.
     *
     * @param key - The `key` of the list.
     * @param index - The `index` of the element in the list to retrieve.
     * @returns - The element at `index` in the list stored at `key`.
     * If `index` is out of range or if `key` does not exist, null is returned.
     *
     * @example
     * ```typescript
     * // Example usage of lindex method to retrieve elements from a list by index
     * const result = await client.lindex("my_list", 0);
     * console.log(result); // Output: 'value1' - Returns the first element in the list stored at 'my_list'.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of lindex method to retrieve elements from a list by negative index
     * const result = await client.lindex("my_list", -1);
     * console.log(result); // Output: 'value3' - Returns the last element in the list stored at 'my_list'.
     * ```
     */
    public lindex(key: string, index: number): Promise<string | null> {
        return this.createWritePromise(createLIndex(key, index));
    }

    /**
     * Inserts `element` in the list at `key` either before or after the `pivot`.
     *
     * See https://valkey.io/commands/linsert/ for more details.
     *
     * @param key - The key of the list.
     * @param position - The relative position to insert into - either `InsertPosition.Before` or
     *     `InsertPosition.After` the `pivot`.
     * @param pivot - An element of the list.
     * @param element - The new element to insert.
     * @returns The list length after a successful insert operation.
     * If the `key` doesn't exist returns `-1`.
     * If the `pivot` wasn't found, returns `0`.
     *
     * @example
     * ```typescript
     * const length = await client.linsert("my_list", InsertPosition.Before, "World", "There");
     * console.log(length); // Output: 2 - The list has a length of 2 after performing the insert.
     * ```
     */
    public linsert(
        key: string,
        position: InsertPosition,
        pivot: string,
        element: string,
    ): Promise<number> {
        return this.createWritePromise(
            createLInsert(key, position, pivot, element),
        );
    }

    /** Remove the existing timeout on `key`, turning the key from volatile (a key with an expire set) to
     * persistent (a key that will never expire as no timeout is associated).
     * See https://valkey.io/commands/persist/ for more details.
     *
     * @param key - The key to remove the existing timeout on.
     * @returns `false` if `key` does not exist or does not have an associated timeout, `true` if the timeout has been removed.
     *
     * @example
     * ```typescript
     * // Example usage of persist method to remove the timeout associated with a key
     * const result = await client.persist("my_key");
     * console.log(result); // Output: true - Indicates that the timeout associated with the key "my_key" was successfully removed.
     * ```
     */
    public persist(key: string): Promise<boolean> {
        return this.createWritePromise(createPersist(key));
    }

    /**
     * Renames `key` to `newkey`.
     * If `newkey` already exists it is overwritten.
     * See https://valkey.io/commands/rename/ for more details.
     *
     * @remarks When in cluster mode, `key` and `newKey` must map to the same hash slot.
     * @param key - The key to rename.
     * @param newKey - The new name of the key.
     * @returns - If the `key` was successfully renamed, return "OK". If `key` does not exist, an error is thrown.
     *
     * @example
     * ```typescript
     * // Example usage of rename method to rename a key
     * await client.set("old_key", "value");
     * const result = await client.rename("old_key", "new_key");
     * console.log(result); // Output: OK - Indicates successful renaming of the key "old_key" to "new_key".
     * ```
     */
    public rename(key: string, newKey: string): Promise<"OK"> {
        return this.createWritePromise(createRename(key, newKey));
    }

    /**
     * Renames `key` to `newkey` if `newkey` does not yet exist.
     * See https://valkey.io/commands/renamenx/ for more details.
     *
     * @remarks When in cluster mode, `key` and `newKey` must map to the same hash slot.
     * @param key - The key to rename.
     * @param newKey - The new name of the key.
     * @returns - If the `key` was successfully renamed, returns `true`. Otherwise, returns `false`.
     * If `key` does not exist, an error is thrown.
     *
     * @example
     * ```typescript
     * // Example usage of renamenx method to rename a key
     * await client.set("old_key", "value");
     * const result = await client.renamenx("old_key", "new_key");
     * console.log(result); // Output: true - Indicates successful renaming of the key "old_key" to "new_key".
     * ```
     */
    public renamenx(key: string, newKey: string): Promise<boolean> {
        return this.createWritePromise(createRenameNX(key, newKey));
    }

    /** Blocking list pop primitive.
     * Pop an element from the tail of the first list that is non-empty,
     * with the given `keys` being checked in the order that they are given.
     * Blocks the connection when there are no elements to pop from any of the given lists.
     * See https://valkey.io/commands/brpop/ for more details.
     *
     * @remarks
     * 1. When in cluster mode, all `keys` must map to the same hash slot.
     * 2. `BRPOP` is a blocking command, see [Blocking Commands](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands) for more details and best practices.
     * @param keys - The `keys` of the lists to pop from.
     * @param timeout - The `timeout` in seconds.
     * @returns - An `array` containing the `key` from which the element was popped and the value of the popped element,
     * formatted as [key, value]. If no element could be popped and the timeout expired, returns `null`.
     *
     * @example
     * ```typescript
     * // Example usage of brpop method to block and wait for elements from multiple lists
     * const result = await client.brpop(["list1", "list2"], 5);
     * console.log(result); // Output: ["list1", "element"] - Indicates an element "element" was popped from "list1".
     * ```
     */
    public brpop(
        keys: string[],
        timeout: number,
    ): Promise<[string, string] | null> {
        return this.createWritePromise(createBRPop(keys, timeout));
    }

    /** Blocking list pop primitive.
     * Pop an element from the head of the first list that is non-empty,
     * with the given `keys` being checked in the order that they are given.
     * Blocks the connection when there are no elements to pop from any of the given lists.
     * See https://valkey.io/commands/blpop/ for more details.
     *
     * @remarks
     * 1. When in cluster mode, all `keys` must map to the same hash slot.
     * 2. `BLPOP` is a blocking command, see [Blocking Commands](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands) for more details and best practices.
     * @param keys - The `keys` of the lists to pop from.
     * @param timeout - The `timeout` in seconds.
     * @returns - An `array` containing the `key` from which the element was popped and the value of the popped element,
     * formatted as [key, value]. If no element could be popped and the timeout expired, returns `null`.
     *
     * @example
     * ```typescript
     * const result = await client.blpop(["list1", "list2"], 5);
     * console.log(result); // Output: ['list1', 'element']
     * ```
     */
    public blpop(
        keys: string[],
        timeout: number,
    ): Promise<[string, string] | null> {
        return this.createWritePromise(createBLPop(keys, timeout));
    }

    /** Adds all elements to the HyperLogLog data structure stored at the specified `key`.
     * Creates a new structure if the `key` does not exist.
     * When no elements are provided, and `key` exists and is a HyperLogLog, then no operation is performed.
     *
     * See https://valkey.io/commands/pfadd/ for more details.
     *
     * @param key - The key of the HyperLogLog data structure to add elements into.
     * @param elements - An array of members to add to the HyperLogLog stored at `key`.
     * @returns - If the HyperLogLog is newly created, or if the HyperLogLog approximated cardinality is
     *     altered, then returns `1`. Otherwise, returns `0`.
     * @example
     * ```typescript
     * const result = await client.pfadd("hll_1", ["a", "b", "c"]);
     * console.log(result); // Output: 1 - Indicates that a data structure was created or modified
     * const result = await client.pfadd("hll_2", []);
     * console.log(result); // Output: 1 - Indicates that a new empty data structure was created
     * ```
     */
    public pfadd(key: string, elements: string[]): Promise<number> {
        return this.createWritePromise(createPfAdd(key, elements));
    }

    /** Estimates the cardinality of the data stored in a HyperLogLog structure for a single key or
     * calculates the combined cardinality of multiple keys by merging their HyperLogLogs temporarily.
     *
     * See https://valkey.io/commands/pfcount/ for more details.
     *
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @param keys - The keys of the HyperLogLog data structures to be analyzed.
     * @returns - The approximated cardinality of given HyperLogLog data structures.
     *     The cardinality of a key that does not exist is `0`.
     * @example
     * ```typescript
     * const result = await client.pfcount(["hll_1", "hll_2"]);
     * console.log(result); // Output: 4 - The approximated cardinality of the union of "hll_1" and "hll_2"
     * ```
     */
    public pfcount(keys: string[]): Promise<number> {
        return this.createWritePromise(createPfCount(keys));
    }

    /**
     * Merges multiple HyperLogLog values into a unique value. If the destination variable exists, it is
     * treated as one of the source HyperLogLog data sets, otherwise a new HyperLogLog is created.
     *
     * See https://valkey.io/commands/pfmerge/ for more details.
     *
     * @remarks When in Cluster mode, all keys in `sourceKeys` and `destination` must map to the same hash slot.
     * @param destination - The key of the destination HyperLogLog where the merged data sets will be stored.
     * @param sourceKeys - The keys of the HyperLogLog structures to be merged.
     * @returns A simple "OK" response.
     *
     * @example
     * ```typescript
     * await client.pfadd("hll1", ["a", "b"]);
     * await client.pfadd("hll2", ["b", "c"]);
     * const result = await client.pfmerge("new_hll", ["hll1", "hll2"]);
     * console.log(result); // Output: OK  - The value of "hll1" merged with "hll2" was stored in "new_hll".
     * const count = await client.pfcount(["new_hll"]);
     * console.log(count); // Output: 3  - The approximated cardinality of "new_hll" is 3.
     * ```
     */
    public async pfmerge(
        destination: string,
        sourceKeys: string[],
    ): Promise<"OK"> {
        return this.createWritePromise(createPfMerge(destination, sourceKeys));
    }

    /** Returns the internal encoding for the Redis object stored at `key`.
     *
     * See https://valkey.io/commands/object-encoding for more details.
     *
     * @param key - The `key` of the object to get the internal encoding of.
     * @returns - If `key` exists, returns the internal encoding of the object stored at `key` as a string.
     *     Otherwise, returns None.
     * @example
     * ```typescript
     * const result = await client.objectEncoding("my_hash");
     * console.log(result); // Output: "listpack"
     * ```
     */
    public objectEncoding(key: string): Promise<string | null> {
        return this.createWritePromise(createObjectEncoding(key));
    }

    /** Returns the logarithmic access frequency counter of a Redis object stored at `key`.
     *
     * See https://valkey.io/commands/object-freq for more details.
     *
     * @param key - The `key` of the object to get the logarithmic access frequency counter of.
     * @returns - If `key` exists, returns the logarithmic access frequency counter of the object
     *            stored at `key` as a `number`. Otherwise, returns `null`.
     * @example
     * ```typescript
     * const result = await client.objectFreq("my_hash");
     * console.log(result); // Output: 2 - The logarithmic access frequency counter of "my_hash".
     * ```
     */
    public objectFreq(key: string): Promise<number | null> {
        return this.createWritePromise(createObjectFreq(key));
    }

    /**
     * Returns the time in seconds since the last access to the value stored at `key`.
     *
     * See https://valkey.io/commands/object-idletime/ for more details.
     *
     * @param key - The key of the object to get the idle time of.
     * @returns If `key` exists, returns the idle time in seconds. Otherwise, returns `null`.
     *
     * @example
     * ```typescript
     * const result = await client.objectIdletime("my_hash");
     * console.log(result); // Output: 13 - "my_hash" was last accessed 13 seconds ago.
     * ```
     */
    public objectIdletime(key: string): Promise<number | null> {
        return this.createWritePromise(createObjectIdletime(key));
    }

    /**
     * Returns the reference count of the object stored at `key`.
     *
     * See https://valkey.io/commands/object-refcount/ for more details.
     *
     * @param key - The `key` of the object to get the reference count of.
     * @returns If `key` exists, returns the reference count of the object stored at `key` as a `number`.
     * Otherwise, returns `null`.
     *
     * @example
     * ```typescript
     * const result = await client.objectRefcount("my_hash");
     * console.log(result); // Output: 2 - "my_hash" has a reference count of 2.
     * ```
     */
    public objectRefcount(key: string): Promise<number | null> {
        return this.createWritePromise(createObjectRefcount(key));
    }

    /**
     * Invokes a previously loaded function.
     *
     * See https://valkey.io/commands/fcall/ for more details.
     *
     * since Valkey version 7.0.0.
     *
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @param func - The function name.
     * @param keys - A list of `keys` accessed by the function. To ensure the correct execution of functions,
     *     all names of keys that a function accesses must be explicitly provided as `keys`.
     * @param args - A list of `function` arguments and it should not represent names of keys.
     * @returns The invoked function's return value.
     *
     * @example
     * ```typescript
     * const response = await client.fcall("Deep_Thought", [], []);
     * console.log(response); // Output: Returns the function's return value.
     * ```
     */
    public fcall(
        func: string,
        keys: string[],
        args: string[],
    ): Promise<string> {
        return this.createWritePromise(createFCall(func, keys, args));
    }

    /**
     * Invokes a previously loaded read-only function.
     *
     * See https://valkey.io/commands/fcall/ for more details.
     *
     * since Valkey version 7.0.0.
     *
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @param func - The function name.
     * @param keys - A list of `keys` accessed by the function. To ensure the correct execution of functions,
     *     all names of keys that a function accesses must be explicitly provided as `keys`.
     * @param args - A list of `function` arguments and it should not represent names of keys.
     * @returns The invoked function's return value.
     *
     * @example
     * ```typescript
     * const response = await client.fcallReadOnly("Deep_Thought", ["key1"], ["Answer", "to", "the",
     *            "Ultimate", "Question", "of", "Life,", "the", "Universe,", "and", "Everything"]);
     * console.log(response); // Output: 42 # The return value on the function that was executed.
     * ```
     */
    public fcallReadonly(
        func: string,
        keys: string[],
        args: string[],
    ): Promise<string> {
        return this.createWritePromise(createFCallReadOnly(func, keys, args));
    }

    /**
     * Returns the index of the first occurrence of `element` inside the list specified by `key`. If no
     * match is found, `null` is returned. If the `count` option is specified, then the function returns
     * an `array` of indices of matching elements within the list.
     *
     * See https://valkey.io/commands/lpos/ for more details.
     *
     * @param key - The name of the list.
     * @param element - The value to search for within the list.
     * @param options - The LPOS options.
     * @returns The index of `element`, or `null` if `element` is not in the list. If the `count` option
     * is specified, then the function returns an `array` of indices of matching elements within the list.
     *
     * since - Valkey version 6.0.6.
     *
     * @example
     * ```typescript
     * await client.rpush("myList", ["a", "b", "c", "d", "e", "e"]);
     * console.log(await client.lpos("myList", "e", { rank: 2 })); // Output: 5 - the second occurrence of "e" is at index 5.
     * console.log(await client.lpos("myList", "e", { count: 3 })); // Output: [ 4, 5 ] - indices for the occurrences of "e" in list "myList".
     * ```
     */
    public lpos(
        key: string,
        element: string,
        options?: LPosOptions,
    ): Promise<number | number[] | null> {
        return this.createWritePromise(createLPos(key, element, options));
    }

    /**
     * Counts the number of set bits (population counting) in the string stored at `key`. The `options` argument can
     * optionally be provided to count the number of bits in a specific string interval.
     *
     * See https://valkey.io/commands/bitcount for more details.
     *
     * @param key - The key for the string to count the set bits of.
     * @param options - The offset options.
     * @returns If `options` is provided, returns the number of set bits in the string interval specified by `options`.
     *     If `options` is not provided, returns the number of set bits in the string stored at `key`.
     *     Otherwise, if `key` is missing, returns `0` as it is treated as an empty string.
     *
     * @example
     * ```typescript
     * console.log(await client.bitcount("my_key1")); // Output: 2 - The string stored at "my_key1" contains 2 set bits.
     * console.log(await client.bitcount("my_key2", { start: 1, end: 3 })); // Output: 2 - The second to fourth bytes of the string stored at "my_key2" contain 2 set bits.
     * console.log(await client.bitcount("my_key3", { start: 1, end: 1, indexType: BitmapIndexType.BIT })); // Output: 1 - Indicates that the second bit of the string stored at "my_key3" is set.
     * console.log(await client.bitcount("my_key3", { start: -1, end: -1, indexType: BitmapIndexType.BIT })); // Output: 1 - Indicates that the last bit of the string stored at "my_key3" is set.
     * ```
     */
    public bitcount(key: string, options?: BitOffsetOptions): Promise<number> {
        return this.createWritePromise(createBitCount(key, options));
    }

    /**
     * Adds geospatial members with their positions to the specified sorted set stored at `key`.
     * If a member is already a part of the sorted set, its position is updated.
     *
     * See https://valkey.io/commands/geoadd/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param membersToGeospatialData - A mapping of member names to their corresponding positions - see
     *     {@link GeospatialData}. The command will report an error when the user attempts to index
     *     coordinates outside the specified ranges.
     * @param options - The GeoAdd options - see {@link GeoAddOptions}.
     * @returns The number of elements added to the sorted set. If `changed` is set to
     *    `true` in the options, returns the number of elements updated in the sorted set.
     *
     * @example
     * ```typescript
     * const options = {updateMode: ConditionalChange.ONLY_IF_EXISTS, changed: true};
     * const membersToCoordinates = new Map<string, GeospatialData>([
     *      ["Palermo", { longitude: 13.361389, latitude: 38.115556 }],
     * ]);
     * const num = await client.geoadd("mySortedSet", membersToCoordinates, options);
     * console.log(num); // Output: 1 - Indicates that the position of an existing member in the sorted set "mySortedSet" has been updated.
     * ```
     */
    public geoadd(
        key: string,
        membersToGeospatialData: Map<string, GeospatialData>,
        options?: GeoAddOptions,
    ): Promise<number> {
        return this.createWritePromise(
            createGeoAdd(key, membersToGeospatialData, options),
        );
    }

    /**
     * Returns the members of a sorted set populated with geospatial information using {@link geoadd},
     * which are within the borders of the area specified by a given shape.
     *
     * See https://valkey.io/commands/geosearch/ for more details.
     *
     * since - Valkey 6.2.0 and above.
     *
     * @param key - The key of the sorted set.
     * @param searchFrom - The query's center point options, could be one of:
     * - {@link MemberOrigin} to use the position of the given existing member in the sorted set.
     * - {@link CoordOrigin} to use the given longitude and latitude coordinates.
     * @param searchBy - The query's shape options, could be one of:
     * - {@link GeoCircleShape} to search inside circular area according to given radius.
     * - {@link GeoBoxShape} to search inside an axis-aligned rectangle, determined by height and width.
     * @param resultOptions - (Optional) Parameters to request additional information and configure sorting/limiting the results, see {@link GeoSearchResultOptions}.
     * @returns By default, returns an `Array` of members (locations) names.
     *     If any of `withCoord`, `withDist` or `withHash` are set to `true` in {@link GeoSearchResultOptions}, a 2D `Array` returned,
     *     where each sub-array represents a single item in the following order:
     * - The member (location) name.
     * - The distance from the center as a floating point `number`, in the same unit specified for `searchBy`, if `withDist` is set to `true`.
     * - The geohash of the location as a integer `number`, if `withHash` is set to `true`.
     * - The coordinates as a two item `array` of floating point `number`s, if `withCoord` is set to `true`.
     *
     * @example
     * ```typescript
     * const data = new Map([["Palermo", { longitude: 13.361389, latitude: 38.115556 }], ["Catania", { longitude: 15.087269, latitude: 37.502669 }]]);
     * await client.geoadd("mySortedSet", data);
     * // search for locations within 200 km circle around stored member named 'Palermo'
     * const result1 = await client.geosearch("mySortedSet", { member: "Palermo" }, { radius: 200, unit: GeoUnit.KILOMETERS });
     * console.log(result1); // Output: ['Palermo', 'Catania']
     *
     * // search for locations in 200x300 mi rectangle centered at coordinate (15, 37), requesting additional info,
     * // limiting results by 2 best matches, ordered by ascending distance from the search area center
     * const result2 = await client.geosearch(
     *     "mySortedSet",
     *     { position: { longitude: 15, latitude: 37 } },
     *     { width: 200, height: 300, unit: GeoUnit.MILES },
     *     {
     *         sortOrder: SortOrder.ASC,
     *         count: 2,
     *         withCoord: true,
     *         withDist: true,
     *         withHash: true,
     *     },
     * );
     * console.log(result2); // Output:
     * // [
     * //     [
     * //         'Catania',                                       // location name
     * //         [
     * //             56.4413,                                     // distance
     * //             3479447370796909,                            // geohash of the location
     * //             [15.087267458438873, 37.50266842333162],     // coordinates of the location
     * //         ],
     * //     ],
     * //     [
     * //         'Palermo',
     * //         [
     * //             190.4424,
     * //             3479099956230698,
     * //             [13.361389338970184, 38.1155563954963],
     * //         ],
     * //     ],
     * // ]
     * ```
     */
    public async geosearch(
        key: string,
        searchFrom: SearchOrigin,
        searchBy: GeoSearchShape,
        resultOptions?: GeoSearchResultOptions,
    ): Promise<(string | (number | number[])[])[]> {
        return this.createWritePromise(
            createGeoSearch(key, searchFrom, searchBy, resultOptions),
        );
    }

    /**
     * Searches for members in a sorted set stored at `source` representing geospatial data
     * within a circular or rectangular area and stores the result in `destination`.
     *
     * If `destination` already exists, it is overwritten. Otherwise, a new sorted set will be created.
     *
     * To get the result directly, see {@link geosearch}.
     *
     * See https://valkey.io/commands/geosearchstore/ for more details.
     *
     * since - Valkey 6.2.0 and above.
     *
     * @remarks When in cluster mode, `destination` and `source` must map to the same hash slot.
     *
     * @param destination - The key of the destination sorted set.
     * @param source - The key of the sorted set.
     * @param searchFrom - The query's center point options, could be one of:
     * - {@link MemberOrigin} to use the position of the given existing member in the sorted set.
     * - {@link CoordOrigin} to use the given longitude and latitude coordinates.
     * @param searchBy - The query's shape options, could be one of:
     * - {@link GeoCircleShape} to search inside circular area according to given radius.
     * - {@link GeoBoxShape} to search inside an axis-aligned rectangle, determined by height and width.
     * @param resultOptions - (Optional) Parameters to request additional information and configure sorting/limiting the results, see {@link GeoSearchStoreResultOptions}.
     * @returns The number of elements in the resulting sorted set stored at `destination`.
     *
     * @example
     * ```typescript
     * const data = new Map([["Palermo", { longitude: 13.361389, latitude: 38.115556 }], ["Catania", { longitude: 15.087269, latitude: 37.502669 }]]);
     * await client.geoadd("mySortedSet", data);
     * // search for locations within 200 km circle around stored member named 'Palermo' and store in `destination`:
     * await client.geosearchstore("destination", "mySortedSet", { member: "Palermo" }, { radius: 200, unit: GeoUnit.KILOMETERS });
     * // query the stored results
     * const result1 = await client.zrangeWithScores("destination", { start: 0, stop: -1 });
     * console.log(result1); // Output:
     * // {
     * //     Palermo: 3479099956230698,   // geohash of the location is stored as element's score
     * //     Catania: 3479447370796909
     * // }
     *
     * // search for locations in 200x300 mi rectangle centered at coordinate (15, 37), requesting to store distance instead of geohashes,
     * // limiting results by 2 best matches, ordered by ascending distance from the search area center
     * await client.geosearchstore(
     *     "destination",
     *     "mySortedSet",
     *     { position: { longitude: 15, latitude: 37 } },
     *     { width: 200, height: 300, unit: GeoUnit.MILES },
     *     {
     *         sortOrder: SortOrder.ASC,
     *         count: 2,
     *         storeDist: true,
     *     },
     * );
     * // query the stored results
     * const result2 = await client.zrangeWithScores("destination", { start: 0, stop: -1 });
     * console.log(result2); // Output:
     * // {
     * //     Palermo: 190.4424,   // distance from the search area center is stored as element's score
     * //     Catania: 56.4413,    // the distance is measured in units used for the search query (miles)
     * // }
     * ```
     */
    public async geosearchstore(
        destination: string,
        source: string,
        searchFrom: SearchOrigin,
        searchBy: GeoSearchShape,
        resultOptions?: GeoSearchStoreResultOptions,
    ): Promise<number> {
        return this.createWritePromise(
            createGeoSearchStore(
                destination,
                source,
                searchFrom,
                searchBy,
                resultOptions,
            ),
        );
    }

    /**
     * Returns the positions (longitude, latitude) of all the specified `members` of the
     * geospatial index represented by the sorted set at `key`.
     *
     * See https://valkey.io/commands/geopos for more details.
     *
     * @param key - The key of the sorted set.
     * @param members - The members for which to get the positions.
     * @returns A 2D `Array` which represents positions (longitude and latitude) corresponding to the
     *     given members. The order of the returned positions matches the order of the input members.
     *     If a member does not exist, its position will be `null`.
     *
     * @example
     * ```typescript
     * const data = new Map([["Palermo", { longitude: 13.361389, latitude: 38.115556 }], ["Catania", { longitude: 15.087269, latitude: 37.502669 }]]);
     * await client.geoadd("mySortedSet", data);
     * const result = await client.geopos("mySortedSet", ["Palermo", "Catania", "NonExisting"]);
     * // When added via GEOADD, the geospatial coordinates are converted into a 52 bit geohash, so the coordinates
     * // returned might not be exactly the same as the input values
     * console.log(result); // Output: [[13.36138933897018433, 38.11555639549629859], [15.08726745843887329, 37.50266842333162032], null]
     * ```
     */
    public geopos(
        key: string,
        members: string[],
    ): Promise<(number[] | null)[]> {
        return this.createWritePromise(createGeoPos(key, members));
    }

    /**
     * Pops a member-score pair from the first non-empty sorted set, with the given `keys`
     * being checked in the order they are provided.
     *
     * See https://valkey.io/commands/zmpop/ for more details.
     *
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @param keys - The keys of the sorted sets.
     * @param modifier - The element pop criteria - either {@link ScoreFilter.MIN} or
     *     {@link ScoreFilter.MAX} to pop the member with the lowest/highest score accordingly.
     * @param count - (Optional) The number of elements to pop. If not supplied, only one element will be popped.
     * @returns A two-element `array` containing the key name of the set from which the element
     *     was popped, and a member-score `Record` of the popped element.
     *     If no member could be popped, returns `null`.
     *
     * since Valkey version 7.0.0.
     *
     * @example
     * ```typescript
     * await client.zadd("zSet1", { one: 1.0, two: 2.0, three: 3.0 });
     * await client.zadd("zSet2", { four: 4.0 });
     * console.log(await client.zmpop(["zSet1", "zSet2"], ScoreFilter.MAX, 2));
     * // Output: [ "zSet1", { three: 3, two: 2 } ] - "three" with score 3 and "two" with score 2 were popped from "zSet1".
     * ```
     */
    public async zmpop(
        keys: string[],
        modifier: ScoreFilter,
        count?: number,
    ): Promise<[string, [Record<string, number>]] | null> {
        return this.createWritePromise(createZMPop(keys, modifier, count));
    }

    /**
     * Pops a member-score pair from the first non-empty sorted set, with the given `keys` being
     * checked in the order they are provided. Blocks the connection when there are no members
     * to pop from any of the given sorted sets. `BZMPOP` is the blocking variant of {@link zmpop}.
     *
     * See https://valkey.io/commands/bzmpop/ for more details.
     *
     * @remarks
     *      1. When in cluster mode, all `keys` must map to the same hash slot.
     *      2. `BZMPOP` is a client blocking command, see {@link https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands | the wiki}
     *         for more details and best practices.
     * @param keys - The keys of the sorted sets.
     * @param modifier - The element pop criteria - either {@link ScoreFilter.MIN} or
     *     {@link ScoreFilter.MAX} to pop the member with the lowest/highest score accordingly.
     * @param timeout - The number of seconds to wait for a blocking operation to complete.
     *     A value of 0 will block indefinitely.
     * @param count - (Optional) The number of elements to pop. If not supplied, only one element will be popped.
     * @returns A two-element `array` containing the key name of the set from which the element
     *     was popped, and a member-score `Record` of the popped element.
     *     If no member could be popped, returns `null`.
     *
     * since Valkey version 7.0.0.
     *
     * @example
     * ```typescript
     * await client.zadd("zSet1", { one: 1.0, two: 2.0, three: 3.0 });
     * await client.zadd("zSet2", { four: 4.0 });
     * console.log(await client.bzmpop(["zSet1", "zSet2"], ScoreFilter.MAX, 0.1, 2));
     * // Output: [ "zSet1", { three: 3, two: 2 } ] - "three" with score 3 and "two" with score 2 were popped from "zSet1".
     * ```
     */
    public async bzmpop(
        keys: string[],
        modifier: ScoreFilter,
        timeout: number,
        count?: number,
    ): Promise<[string, [Record<string, number>]] | null> {
        return this.createWritePromise(
            createBZMPop(keys, modifier, timeout, count),
        );
    }

    /**
     * Increments the score of `member` in the sorted set stored at `key` by `increment`.
     * If `member` does not exist in the sorted set, it is added with `increment` as its score.
     * If `key` does not exist, a new sorted set is created with the specified member as its sole member.
     *
     * See https://valkey.io/commands/zincrby/ for details.
     *
     * @param key - The key of the sorted set.
     * @param increment - The score increment.
     * @param member - A member of the sorted set.
     *
     * @returns The new score of `member`.
     *
     * @example
     * ```typescript
     * // Example usage of zincrBy method to increment the value of a member's score
     * await client.zadd("my_sorted_set", {"member": 10.5, "member2": 8.2});
     * console.log(await client.zincrby("my_sorted_set", 1.2, "member"));
     * // Output: 11.7 - The member existed in the set before score was altered, the new score is 11.7.
     * console.log(await client.zincrby("my_sorted_set", -1.7, "member"));
     * // Output: 10.0 - Negative increment, decrements the score.
     * console.log(await client.zincrby("my_sorted_set", 5.5, "non_existing_member"));
     * // Output: 5.5 - A new member is added to the sorted set with the score of 5.5.
     * ```
     */
    public async zincrby(
        key: string,
        increment: number,
        member: string,
    ): Promise<number> {
        return this.createWritePromise(createZIncrBy(key, increment, member));
    }

    /**
     * Iterates incrementally over a sorted set.
     *
     * See https://valkey.io/commands/zscan for more details.
     *
     * @param key - The key of the sorted set.
     * @param cursor - The cursor that points to the next iteration of results. A value of `"0"` indicates the start of
     *      the search.
     * @param options - (Optional) The zscan options.
     * @returns An `Array` of the `cursor` and the subset of the sorted set held by `key`.
     *      The first element is always the `cursor` for the next iteration of results. `0` will be the `cursor`
     *      returned on the last iteration of the sorted set. The second element is always an `Array` of the subset
     *      of the sorted set held in `key`. The `Array` in the second element is always a flattened series of
     *      `String` pairs, where the value is at even indices and the score is at odd indices.
     *
     * @example
     * ```typescript
     * // Assume "key" contains a sorted set with multiple members
     * let newCursor = "0";
     * let result = [];
     *
     * do {
     *      result = await client.zscan(key1, newCursor, {
     *          match: "*",
     *          count: 5,
     *      });
     *      newCursor = result[0];
     *      console.log("Cursor: ", newCursor);
     *      console.log("Members: ", result[1]);
     * } while (newCursor !== "0");
     * // The output of the code above is something similar to:
     * // Cursor:  123
     * // Members:  ['value 163', '163', 'value 114', '114', 'value 25', '25', 'value 82', '82', 'value 64', '64']
     * // Cursor:  47
     * // Members:  ['value 39', '39', 'value 127', '127', 'value 43', '43', 'value 139', '139', 'value 211', '211']
     * // Cursor:  0
     * // Members:  ['value 55', '55', 'value 24', '24', 'value 90', '90', 'value 113', '113']
     * ```
     */
    public async zscan(
        key: string,
        cursor: string,
        options?: BaseScanOptions,
    ): Promise<[string, string[]]> {
        return this.createWritePromise(createZScan(key, cursor, options));
    }

    /**
     * Returns the distance between `member1` and `member2` saved in the geospatial index stored at `key`.
     *
     * See https://valkey.io/commands/geodist/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param member1 - The name of the first member.
     * @param member2 - The name of the second member.
     * @param geoUnit - The unit of distance measurement - see {@link GeoUnit}. If not specified, the default unit is {@link GeoUnit.METERS}.
     * @returns The distance between `member1` and `member2`. Returns `null`, if one or both members do not exist,
     *     or if the key does not exist.
     *
     * @example
     * ```typescript
     * const result = await client.geodist("mySortedSet", "Place1", "Place2", GeoUnit.KILOMETERS);
     * console.log(num); // Output: the distance between Place1 and Place2.
     * ```
     */
    public geodist(
        key: string,
        member1: string,
        member2: string,
        geoUnit?: GeoUnit,
    ): Promise<number | null> {
        return this.createWritePromise(
            createGeoDist(key, member1, member2, geoUnit),
        );
    }

    /**
     * Returns the `GeoHash` strings representing the positions of all the specified `members` in the sorted set stored at `key`.
     *
     * See https://valkey.io/commands/geohash/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param members - The array of members whose `GeoHash` strings are to be retrieved.
     * @returns An array of `GeoHash` strings representing the positions of the specified members stored at `key`.
     *   If a member does not exist in the sorted set, a `null` value is returned for that member.
     *
     * @example
     * ```typescript
     * const result = await client.geohash("mySortedSet",["Palermo", "Catania", "NonExisting"]);
     * console.log(num); // Output: ["sqc8b49rny0", "sqdtr74hyu0", null]
     * ```
     */
    public geohash(key: string, members: string[]): Promise<(string | null)[]> {
        return this.createWritePromise<(string | null)[]>(
            createGeoHash(key, members),
        ).then((hashes) =>
            hashes.map((hash) => (hash === null ? null : "" + hash)),
        );
    }

    /**
     * Returns all the longest common subsequences combined between strings stored at `key1` and `key2`.
     *
     * since Valkey version 7.0.0.
     *
     * @remarks When in cluster mode, `key1` and `key2` must map to the same hash slot.
     *
     * See https://valkey.io/commands/lcs/ for more details.
     *
     * @param key1 - The key that stores the first string.
     * @param key2 - The key that stores the second string.
     * @returns A `String` containing all the longest common subsequence combined between the 2 strings.
     *     An empty `String` is returned if the keys do not exist or have no common subsequences.
     *
     * @example
     * ```typescript
     * await client.mset({"testKey1": "abcd", "testKey2": "axcd"});
     * const result = await client.lcs("testKey1", "testKey2");
     * console.log(result); // Output: 'cd'
     * ```
     */
    public async lcs(key1: string, key2: string): Promise<string> {
        return this.createWritePromise(createLCS(key1, key2));
    }

    /**
     * Returns the total length of all the longest common subsequences between strings stored at `key1` and `key2`.
     *
     * since Valkey version 7.0.0.
     *
     * @remarks When in cluster mode, `key1` and `key2` must map to the same hash slot.
     *
     * See https://valkey.io/commands/lcs/ for more details.
     *
     * @param key1 - The key that stores the first string.
     * @param key2 - The key that stores the second string.
     * @returns The total length of all the longest common subsequences between the 2 strings.
     *
     * @example
     * ```typescript
     * await client.mset({"testKey1": "abcd", "testKey2": "axcd"});
     * const result = await client.lcsLen("testKey1", "testKey2");
     * console.log(result); // Output: 2
     * ```
     */
    public async lcsLen(key1: string, key2: string): Promise<number> {
        return this.createWritePromise(createLCS(key1, key2, { len: true }));
    }

    /**
     * Returns the indices and lengths of the longest common subsequences between strings stored at
     * `key1` and `key2`.
     *
     * since Valkey version 7.0.0.
     *
     * @remarks When in cluster mode, `key1` and `key2` must map to the same hash slot.
     *
     * See https://valkey.io/commands/lcs/ for more details.
     *
     * @param key1 - The key that stores the first string.
     * @param key2 - The key that stores the second string.
     * @param withMatchLen - (Optional) If `true`, include the length of the substring matched for the each match.
     * @param minMatchLen - (Optional) The minimum length of matches to include in the result.
     * @returns A `Record` containing the indices of the longest common subsequences between the
     *     2 strings and the lengths of the longest common subsequences. The resulting map contains two
     *     keys, "matches" and "len":
     *     - `"len"` is mapped to the total length of the all longest common subsequences between the 2 strings
     *           stored as an integer. This value doesn't count towards the `minMatchLen` filter.
     *     - `"matches"` is mapped to a three dimensional array of integers that stores pairs
     *           of indices that represent the location of the common subsequences in the strings held
     *           by `key1` and `key2`.
     *
     * @example
     * ```typescript
     * await client.mset({"key1": "ohmytext", "key2": "mynewtext"});
     * const result = await client.lcsIdx("key1", "key2");
     * console.log(result); // Output:
     * {
     *     "matches" :
     *     [
     *         [              // first substring match is "text"
     *             [4, 7],    // in `key1` it is located between indices 4 and 7
     *             [5, 8],    // and in `key2` - in between 5 and 8
     *             4          // the match length, returned if `withMatchLen` set to `true`
     *         ],
     *         [              // second substring match is "my"
     *             [2, 3],    // in `key1` it is located between indices 2 and 3
     *             [0, 1],    // and in `key2` - in between 0 and 1
     *             2          // the match length, returned if `withMatchLen` set to `true`
     *         ]
     *     ],
     *     "len" : 6          // total length of the all matches found
     * }
     * ```
     */
    public async lcsIdx(
        key1: string,
        key2: string,
        options?: { withMatchLen?: boolean; minMatchLen?: number },
    ): Promise<Record<string, (number | [number, number])[][] | number>> {
        return this.createWritePromise(
            createLCS(key1, key2, { idx: options ?? {} }),
        );
    }

    /**
     * Updates the last access time of the specified keys.
     *
     * See https://valkey.io/commands/touch/ for more details.
     *
     * @remarks When in cluster mode, the command may route to multiple nodes when `keys` map to different hash slots.
     * @param keys - The keys to update the last access time of.
     * @returns The number of keys that were updated. A key is ignored if it doesn't exist.
     *
     * @example
     * ```typescript
     * await client.set("key1", "value1");
     * await client.set("key2", "value2");
     * const result = await client.touch(["key1", "key2", "nonExistingKey"]);
     * console.log(result); // Output: 2 - The last access time of 2 keys has been updated.
     * ```
     */
    public async touch(keys: string[]): Promise<number> {
        return this.createWritePromise(createTouch(keys));
    }

    /**
     * Marks the given keys to be watched for conditional execution of a transaction. Transactions
     * will only execute commands if the watched keys are not modified before execution of the
     * transaction. Executing a transaction will automatically flush all previously watched keys.
     *
     * See https://valkey.io/commands/watch/ and https://valkey.io/topics/transactions/#cas for more details.
     *
     * @remarks When in cluster mode, the command may route to multiple nodes when `keys` map to different hash slots.
     * @param keys - The keys to watch.
     * @returns A simple "OK" response.
     *
     * @example
     * ```typescript
     * const response = await client.watch(["sampleKey"]);
     * console.log(response); // Output: "OK"
     * const transaction = new Transaction().set("SampleKey", "foobar");
     * const result = await client.exec(transaction);
     * console.log(result); // Output: "OK" - Executes successfully and keys are unwatched.
     * ```
     * ```typescript
     * const response = await client.watch(["sampleKey"]);
     * console.log(response); // Output: "OK"
     * const transaction = new Transaction().set("SampleKey", "foobar");
     * await client.set("sampleKey", "hello world");
     * const result = await client.exec(transaction);
     * console.log(result); // Output: null - null is returned when the watched key is modified before transaction execution.
     * ```
     */
    public async watch(keys: string[]): Promise<"OK"> {
        return this.createWritePromise(createWatch(keys));
    }

    /**
     * Overwrites part of the string stored at `key`, starting at the specified `offset`,
     * for the entire length of `value`. If the `offset` is larger than the current length of the string at `key`,
     * the string is padded with zero bytes to make `offset` fit. Creates the `key` if it doesn't exist.
     *
     * See https://valkey.io/commands/setrange/ for more details.
     *
     * @param key - The key of the string to update.
     * @param offset - The position in the string where `value` should be written.
     * @param value - The string written with `offset`.
     * @returns The length of the string stored at `key` after it was modified.
     *
     * @example
     * ```typescript
     * const len = await client.setrange("key", 6, "GLIDE");
     * console.log(len); // Output: 11 - New key was created with length of 11 symbols
     * const value = await client.get("key");
     * console.log(result); // Output: "\0\0\0\0\0\0GLIDE" - The string was padded with zero bytes
     * ```
     */
    public async setrange(
        key: string,
        offset: number,
        value: string,
    ): Promise<number> {
        return this.createWritePromise(createSetRange(key, offset, value));
    }

    /**
     * Appends a `value` to a `key`. If `key` does not exist it is created and set as an empty string,
     * so `APPEND` will be similar to {@link set} in this special case.
     *
     * See https://valkey.io/commands/append/ for more details.
     *
     * @param key - The key of the string.
     * @param value - The key of the string.
     * @returns The length of the string after appending the value.
     *
     * @example
     * ```typescript
     * const len = await client.append("key", "Hello");
     * console.log(len);
     *     // Output: 5 - Indicates that "Hello" has been appended to the value of "key", which was initially
     *     // empty, resulting in a new value of "Hello" with a length of 5 - similar to the set operation.
     * len = await client.append("key", " world");
     * console.log(result);
     *     // Output: 11 - Indicates that " world" has been appended to the value of "key", resulting in a
     *     // new value of "Hello world" with a length of 11.
     * ```
     */
    public async append(key: string, value: string): Promise<number> {
        return this.createWritePromise(createAppend(key, value));
    }

    /**
     * Pops one or more elements from the first non-empty list from the provided `keys`.
     *
     * See https://valkey.io/commands/lmpop/ for more details.
     *
     * @remarks When in cluster mode, all `key`s must map to the same hash slot.
     * @param keys - An array of keys to lists.
     * @param direction - The direction based on which elements are popped from - see {@link ListDirection}.
     * @param count - (Optional) The maximum number of popped elements.
     * @returns A `Record` of key-name mapped array of popped elements.
     *
     * since Valkey version 7.0.0.
     *
     * @example
     * ```typescript
     * await client.lpush("testKey", ["one", "two", "three"]);
     * await client.lpush("testKey2", ["five", "six", "seven"]);
     * const result = await client.lmpop(["testKey", "testKey2"], ListDirection.LEFT, 1L);
     * console.log(result.get("testKey")); // Output: { "testKey": ["three"] }
     * ```
     */
    public async lmpop(
        keys: string[],
        direction: ListDirection,
        count?: number,
    ): Promise<Record<string, string[]>> {
        return this.createWritePromise(createLMPop(keys, direction, count));
    }

    /**
     * Blocks the connection until it pops one or more elements from the first non-empty list from the
     * provided `key`. `BLMPOP` is the blocking variant of {@link lmpop}.
     *
     * See https://valkey.io/commands/blmpop/ for more details.
     *
     * @remarks When in cluster mode, all `key`s must map to the same hash slot.
     * @param keys - An array of keys to lists.
     * @param direction - The direction based on which elements are popped from - see {@link ListDirection}.
     * @param timeout - The number of seconds to wait for a blocking operation to complete. A value of `0` will block indefinitely.
     * @param count - (Optional) The maximum number of popped elements.
     * @returns - A `Record` of `key` name mapped array of popped elements.
     *     If no member could be popped and the timeout expired, returns `null`.
     *
     * since Valkey version 7.0.0.
     *
     * @example
     * ```typescript
     * await client.lpush("testKey", ["one", "two", "three"]);
     * await client.lpush("testKey2", ["five", "six", "seven"]);
     * const result = await client.blmpop(["testKey", "testKey2"], ListDirection.LEFT, 0.1, 1L);
     * console.log(result.get("testKey")); // Output: { "testKey": ["three"] }
     * ```
     */
    public async blmpop(
        keys: string[],
        direction: ListDirection,
        timeout: number,
        count?: number,
    ): Promise<Record<string, string[]>> {
        return this.createWritePromise(
            createBLMPop(timeout, keys, direction, count),
        );
    }

    /**
     * Lists the currently active channels.
     * The command is routed to all nodes, and aggregates the response to a single array.
     *
     * See https://valkey.io/commands/pubsub-channels for more details.
     *
     * @param pattern - A glob-style pattern to match active channels.
     *                  If not provided, all active channels are returned.
     * @returns A list of currently active channels matching the given pattern.
     *          If no pattern is specified, all active channels are returned.
     *
     * @example
     * ```typescript
     * const channels = await client.pubsubChannels();
     * console.log(channels); // Output: ["channel1", "channel2"]
     *
     * const newsChannels = await client.pubsubChannels("news.*");
     * console.log(newsChannels); // Output: ["news.sports", "news.weather"]
     * ```
     */
    public async pubsubChannels(pattern?: string): Promise<string[]> {
        return this.createWritePromise(createPubSubChannels(pattern));
    }

    /**
     * Returns the number of unique patterns that are subscribed to by clients.
     *
     * Note: This is the total number of unique patterns all the clients are subscribed to,
     * not the count of clients subscribed to patterns.
     * The command is routed to all nodes, and aggregates the response to the sum of all pattern subscriptions.
     *
     * See https://valkey.io/commands/pubsub-numpat for more details.
     *
     * @returns The number of unique patterns.
     *
     * @example
     * ```typescript
     * const patternCount = await client.pubsubNumpat();
     * console.log(patternCount); // Output: 3
     * ```
     */
    public async pubsubNumPat(): Promise<number> {
        return this.createWritePromise(createPubSubNumPat());
    }

    /**
     * Returns the number of subscribers (exclusive of clients subscribed to patterns) for the specified channels.
     *
     * Note that it is valid to call this command without channels. In this case, it will just return an empty map.
     * The command is routed to all nodes, and aggregates the response to a single map of the channels and their number of subscriptions.
     *
     * See https://valkey.io/commands/pubsub-numsub for more details.
     *
     * @param channels - The list of channels to query for the number of subscribers.
     *                   If not provided, returns an empty map.
     * @returns A map where keys are the channel names and values are the number of subscribers.
     *
     * @example
     * ```typescript
     * const result1 = await client.pubsubNumsub(["channel1", "channel2"]);
     * console.log(result1); // Output: { "channel1": 3, "channel2": 5 }
     *
     * const result2 = await client.pubsubNumsub();
     * console.log(result2); // Output: {}
     * ```
     */
    public async pubsubNumSub(
        channels?: string[],
    ): Promise<Record<string, number>> {
        return this.createWritePromise(createPubSubNumSub(channels));
    }

    /**
     * @internal
     */
    protected createClientRequest(
        options: BaseClientConfiguration,
    ): connection_request.IConnectionRequest {
        const readFrom = options.readFrom
            ? this.MAP_READ_FROM_STRATEGY[options.readFrom]
            : connection_request.ReadFrom.Primary;
        const authenticationInfo =
            options.credentials !== undefined &&
            "password" in options.credentials
                ? {
                      password: options.credentials.password,
                      username: options.credentials.username,
                  }
                : undefined;
        const protocol = options.protocol as
            | connection_request.ProtocolVersion
            | undefined;
        return {
            protocol,
            clientName: options.clientName,
            addresses: options.addresses,
            tlsMode: options.useTLS
                ? connection_request.TlsMode.SecureTls
                : connection_request.TlsMode.NoTls,
            requestTimeout: options.requestTimeout,
            clusterModeEnabled: false,
            readFrom,
            authenticationInfo,
        };
    }

    /**
     * @internal
     */
    protected connectToServer(options: BaseClientConfiguration): Promise<void> {
        return new Promise((resolve, reject) => {
            this.promiseCallbackFunctions[0] = [resolve, reject];

            const message = connection_request.ConnectionRequest.create(
                this.createClientRequest(options),
            );

            this.writeOrBufferRequest(
                message,
                (
                    message: connection_request.ConnectionRequest,
                    writer: Writer,
                ) => {
                    connection_request.ConnectionRequest.encodeDelimited(
                        message,
                        writer,
                    );
                },
            );
        });
    }

    /**
     *  Terminate the client by closing all associated resources, including the socket and any active promises.
     *  All open promises will be closed with an exception.
     * @param errorMessage - If defined, this error message will be passed along with the exceptions when closing all open promises.
     */
    public close(errorMessage?: string): void {
        this.isClosed = true;
        this.promiseCallbackFunctions.forEach(([, reject]) => {
            reject(new ClosingError(errorMessage || ""));
        });

        // Handle pubsub futures
        this.pubsubFutures.forEach(([, reject]) => {
            reject(new ClosingError(errorMessage || ""));
        });
        Logger.log("info", "Client lifetime", "disposing of client");
        this.socket.end();
    }

    /**
     * @internal
     */
    protected static async __createClientInternal<
        TConnection extends BaseClient,
    >(
        options: BaseClientConfiguration,
        connectedSocket: net.Socket,
        constructor: (
            socket: net.Socket,
            options?: BaseClientConfiguration,
        ) => TConnection,
    ): Promise<TConnection> {
        const connection = constructor(connectedSocket, options);
        await connection.connectToServer(options);
        Logger.log("info", "Client lifetime", "connected to server");
        return connection;
    }

    /**
     * @internal
     */
    protected static GetSocket(path: string): Promise<net.Socket> {
        return new Promise((resolve, reject) => {
            const socket = new net.Socket();
            socket
                .connect(path)
                .once("connect", () => resolve(socket))
                .once("error", reject);
        });
    }

    /**
     * @internal
     */
    protected static async createClientInternal<TConnection extends BaseClient>(
        options: BaseClientConfiguration,
        constructor: (
            socket: net.Socket,
            options?: BaseClientConfiguration,
        ) => TConnection,
    ): Promise<TConnection> {
        const path = await StartSocketConnection();
        const socket = await this.GetSocket(path);

        try {
            return await this.__createClientInternal<TConnection>(
                options,
                socket,
                constructor,
            );
        } catch (err) {
            // Ensure socket is closed
            socket.end();
            throw err;
        }
    }
}
