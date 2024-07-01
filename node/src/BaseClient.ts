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
    ExpireOptions,
    InsertPosition,
    KeyWeight,
    RangeByIndex,
    RangeByLex,
    RangeByScore,
    ScoreBoundary,
    SetOptions,
    StreamAddOptions,
    StreamReadOptions,
    StreamTrimOptions,
    ZAddOptions,
    createBLPop,
    createBRPop,
    createDecr,
    createDecrBy,
    createDel,
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
    createHVals,
    createIncr,
    createIncrBy,
    createIncrByFloat,
    createLIndex,
    createLInsert,
    createLLen,
    createLPop,
    createLPush,
    createLRange,
    createLRem,
    createLTrim,
    createMGet,
    createMSet,
    createObjectEncoding,
    createObjectFreq,
    createObjectIdletime,
    createObjectRefcount,
    createPExpire,
    createPExpireAt,
    createPTTL,
    createPersist,
    createPfAdd,
    createPfCount,
    createRPop,
    createRPush,
    createRename,
    createRenameNX,
    createSAdd,
    createSCard,
    createSInter,
    createSIsMember,
    createSMembers,
    createSMove,
    createSPop,
    createSRem,
    createSUnionStore,
    createSet,
    createStrlen,
    createTTL,
    createType,
    createUnlink,
    createXAdd,
    createXLen,
    createXRead,
    createXTrim,
    createZAdd,
    createZCard,
    createZCount,
    createZInterCard,
    createZInterstore,
    createZPopMax,
    createZPopMin,
    createZRange,
    createZRangeWithScores,
    createZRank,
    createZRem,
    createZRemRangeByRank,
    createZRemRangeByScore,
    createZScore,
} from "./Commands";
import {
    ClosingError,
    ConnectionError,
    ExecAbortError,
    RedisError,
    RequestError,
    TimeoutError,
} from "./Errors";
import { Logger } from "./Logger";
import { connection_request, redis_request, response } from "./ProtobufMessage";

/* eslint-disable-next-line @typescript-eslint/no-explicit-any */
type PromiseFunction = (value?: any) => void;
type ErrorFunction = (error: RedisError) => void;
export type ReturnTypeMap = { [key: string]: ReturnType };
export type ReturnTypeAttribute = {
    value: ReturnType;
    attributes: ReturnTypeMap;
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
    | Set<ReturnType>
    | ReturnTypeMap
    | ReturnTypeAttribute
    | ReturnType[];

type RedisCredentials = {
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

type ReadFrom =
    /** Always get from primary, in order to get the freshest data.*/
    | "primary"
    /** Spread the requests between all replicas in a round robin manner.
        If no replica is available, route the requests to the primary.*/
    | "preferReplica";

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
    keys?: string[];
    /**
     * The arguments for the script.
     */
    args?: string[];
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

            if (message.closingError != null) {
                this.close(message.closingError);
                return;
            }

            const [resolve, reject] =
                this.promiseCallbackFunctions[message.callbackIdx];
            this.availableCallbackSlots.push(message.callbackIdx);

            if (message.requestError != null) {
                const errorType = getRequestErrorClass(
                    message.requestError.type,
                );
                reject(
                    new errorType(message.requestError.message ?? undefined),
                );
            } else if (message.respPointer != null) {
                const pointer = message.respPointer;

                if (typeof pointer === "number") {
                    resolve(valueFromSplitPointer(0, pointer));
                } else {
                    resolve(valueFromSplitPointer(pointer.high, pointer.low));
                }
            } else if (
                message.constantResponse === response.ConstantResponse.OK
            ) {
                resolve("OK");
            } else {
                resolve(null);
            }
        }

        this.remainingReadData = undefined;
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
            | redis_request.Command
            | redis_request.Command[]
            | redis_request.ScriptInvocation,
        route?: redis_request.Routes,
    ): Promise<T> {
        if (this.isClosed) {
            throw new ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client.",
            );
        }

        return new Promise((resolve, reject) => {
            const callbackIndex = this.getCallbackIndex();
            this.promiseCallbackFunctions[callbackIndex] = [resolve, reject];
            this.writeOrBufferRedisRequest(callbackIndex, command, route);
        });
    }

    private writeOrBufferRedisRequest(
        callbackIdx: number,
        command:
            | redis_request.Command
            | redis_request.Command[]
            | redis_request.ScriptInvocation,
        route?: redis_request.Routes,
    ) {
        const message = Array.isArray(command)
            ? redis_request.RedisRequest.create({
                  callbackIdx,
                  transaction: redis_request.Transaction.create({
                      commands: command,
                  }),
              })
            : command instanceof redis_request.Command
              ? redis_request.RedisRequest.create({
                    callbackIdx,
                    singleCommand: command,
                })
              : redis_request.RedisRequest.create({
                    callbackIdx,
                    scriptInvocation: command,
                });
        message.route = route;

        this.writeOrBufferRequest(
            message,
            (message: redis_request.RedisRequest, writer: Writer) => {
                redis_request.RedisRequest.encodeDelimited(message, writer);
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

    /** Get the value associated with the given key, or null if no such value exists.
     * See https://redis.io/commands/get/ for details.
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

    /** Set the given key with the given value. Return value is dependent on the passed options.
     * See https://redis.io/commands/set/ for details.
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
     * See https://redis.io/commands/del/ for details.
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
     * See https://redis.io/commands/mget/ for details.
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
     * See https://redis.io/commands/mset/ for details.
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

    /** Increments the number stored at `key` by one. If `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/incr/ for details.
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
     * See https://redis.io/commands/incrby/ for details.
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
     * See https://redis.io/commands/incrbyfloat/ for details.
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
     * See https://redis.io/commands/decr/ for details.
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
     * See https://redis.io/commands/decrby/ for details.
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

    /** Retrieve the value associated with `field` in the hash stored at `key`.
     * See https://redis.io/commands/hget/ for details.
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

    /** Sets the specified fields to their respective values in the hash stored at `key`.
     * See https://redis.io/commands/hset/ for details.
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
     * See https://redis.io/commands/hsetnx/ for more details.
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
     * See https://redis.io/commands/hdel/ for details.
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
     * See https://redis.io/commands/hmget/ for details.
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
     * See https://redis.io/commands/hexists/ for details.
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
     * See https://redis.io/commands/hgetall/ for details.
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
     * See https://redis.io/commands/hincrby/ for details.
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
     * See https://redis.io/commands/hincrbyfloat/ for details.
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
     * See https://redis.io/commands/hlen/ for more details.
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
     * See https://redis.io/commands/hvals/ for more details.
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

    /** Inserts all the specified values at the head of the list stored at `key`.
     * `elements` are inserted one after the other to the head of the list, from the leftmost element to the rightmost element.
     * If `key` does not exist, it is created as empty list before performing the push operations.
     * See https://redis.io/commands/lpush/ for details.
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

    /** Removes and returns the first elements of the list stored at `key`.
     * The command pops a single element from the beginning of the list.
     * See https://redis.io/commands/lpop/ for details.
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
     * See https://redis.io/commands/lpop/ for details.
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
     * See https://redis.io/commands/lrange/ for details.
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
     * See https://redis.io/commands/llen/ for details.
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

    /** Trim an existing list so that it will contain only the specified range of elements specified.
     * The offsets `start` and `end` are zero-based indexes, with 0 being the first element of the list, 1 being the next element and so on.
     * These offsets can also be negative numbers indicating offsets starting at the end of the list,
     * with -1 being the last element of the list, -2 being the penultimate, and so on.
     * See https://redis.io/commands/ltrim/ for details.
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
     * See https://redis.io/commands/rpush/ for details.
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

    /** Removes and returns the last elements of the list stored at `key`.
     * The command pops a single element from the end of the list.
     * See https://redis.io/commands/rpop/ for details.
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
     * See https://redis.io/commands/rpop/ for details.
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
     * See https://redis.io/commands/sadd/ for details.
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
     * See https://redis.io/commands/srem/ for details.
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
     * See https://redis.io/commands/smembers/ for details.
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
     * See https://redis.io/commands/scard/ for details.
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
     * See https://redis.io/commands/sismember/ for more details.
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

    /** Removes and returns one random member from the set value store at `key`.
     * See https://redis.io/commands/spop/ for details.
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
     * See https://redis.io/commands/spop/ for details.
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

    /** Returns the number of keys in `keys` that exist in the database.
     * See https://redis.io/commands/exists/ for details.
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
     * However, this command does not block the server, while [DEL](https://redis.io/commands/del) does.
     * See https://redis.io/commands/unlink/ for details.
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
     * See https://redis.io/commands/expire/ for details.
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
     * See https://redis.io/commands/expireat/ for details.
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

    /** Sets a timeout on `key` in milliseconds. After the timeout has expired, the key will automatically be deleted.
     * If `key` already has an existing expire set, the time to live is updated to the new value.
     * If `milliseconds` is non-positive number, the key will be deleted rather than expired.
     * The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
     * See https://redis.io/commands/pexpire/ for details.
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
     * See https://redis.io/commands/pexpireat/ for details.
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

    /** Returns the remaining time to live of `key` that has a timeout.
     * See https://redis.io/commands/ttl/ for details.
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
     * See https://redis.io/commands/script-load/ and https://redis.io/commands/evalsha/ for details.
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
        const scriptInvocation = redis_request.ScriptInvocation.create({
            hash: script.getHash(),
            keys: option?.keys,
            args: option?.args,
        });
        return this.createWritePromise(scriptInvocation);
    }

    /** Adds members with their scores to the sorted set stored at `key`.
     * If a member is already a part of the sorted set, its score is updated.
     * See https://redis.io/commands/zadd/ for more details.
     *
     * @param key - The key of the sorted set.
     * @param membersScoresMap - A mapping of members to their corresponding scores.
     * @param options - The ZAdd options.
     * @param changed - Modify the return value from the number of new elements added, to the total number of elements changed.
     * @returns The number of elements added to the sorted set.
     * If `changed` is set, returns the number of elements updated in the sorted set.
     *
     * @example
     * ```typescript
     * // Example usage of the zadd method to add elements to a sorted set
     * const result = await client.zadd("my_sorted_set", \{ "member1": 10.5, "member2": 8.2 \});
     * console.log(result); // Output: 2 - Indicates that two elements have been added to the sorted set "my_sorted_set."
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the zadd method to update scores in an existing sorted set
     * const result = await client.zadd("existing_sorted_set", { member1: 15.0, member2: 5.5 }, options={ conditionalChange: "onlyIfExists" } , changed=true);
     * console.log(result); // Output: 2 - Updates the scores of two existing members in the sorted set "existing_sorted_set."
     * ```
     */
    public zadd(
        key: string,
        membersScoresMap: Record<string, number>,
        options?: ZAddOptions,
        changed?: boolean,
    ): Promise<number> {
        return this.createWritePromise(
            createZAdd(
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
            createZAdd(key, { [member]: increment }, options, "INCR"),
        );
    }

    /** Removes the specified members from the sorted set stored at `key`.
     * Specified members that are not a member of this set are ignored.
     * See https://redis.io/commands/zrem/ for more details.
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
     * See https://redis.io/commands/zcard/ for more details.
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

    /** Returns the score of `member` in the sorted set stored at `key`.
     * See https://redis.io/commands/zscore/ for more details.
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

    /** Returns the number of members in the sorted set stored at `key` with scores between `minScore` and `maxScore`.
     * See https://redis.io/commands/zcount/ for more details.
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
     * const result = await client.zcount("my_sorted_set", { bound: 5.0, isInclusive: true }, "positiveInfinity");
     * console.log(result); // Output: 2 - Indicates that there are 2 members with scores between 5.0 (inclusive) and +inf in the sorted set "my_sorted_set".
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the zcount method to count members in a sorted set within a score range
     * const result = await client.zcount("my_sorted_set", { bound: 5.0, isInclusive: true }, { bound: 10.0, isInclusive: false });
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
     * See https://redis.io/commands/zrange/ for more details.
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
     *              start: "negativeInfinity",
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
     * See https://redis.io/commands/zrange/ for more details.
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
     *              start: "negativeInfinity",
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

    /** Returns the length of the string value stored at `key`.
     * See https://redis.io/commands/strlen/ for more details.
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
     * See https://redis.io/commands/type/ for more details.
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
     * See https://redis.io/commands/zpopmin for more details.
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

    /** Removes and returns the members with the highest scores from the sorted set stored at `key`.
     * If `count` is provided, up to `count` members with the highest scores are removed and returned.
     * Otherwise, only one member with the highest score is removed and returned.
     * See https://redis.io/commands/zpopmax for more details.
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

    /** Returns the remaining time to live of `key` that has a timeout, in milliseconds.
     * See https://redis.io/commands/pttl for more details.
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
     * See https://redis.io/commands/zremrangebyrank/ for more details.
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

    /** Removes all elements in the sorted set stored at `key` with a score between `minScore` and `maxScore`.
     * See https://redis.io/commands/zremrangebyscore/ for more details.
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
     * const result = await client.zremRangeByScore("my_sorted_set", { bound: 5.0, isInclusive: true }, "positiveInfinity");
     * console.log(result); // Output: 2 - Indicates that 2 members with scores between 5.0 (inclusive) and +inf have been removed from the sorted set "my_sorted_set".
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of zremRangeByScore method when the sorted set does not exist
     * const result = await client.zremRangeByScore("non_existing_sorted_set", { bound: 5.0, isInclusive: true }, { bound: 10.0, isInclusive: false });
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

    /** Returns the rank of `member` in the sorted set stored at `key`, with scores ordered from low to high.
     * See https://redis.io/commands/zrank for more details.
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
     * See https://redis.io/commands/zrank for more details.
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
     * Adds an entry to the specified stream stored at `key`. If the `key` doesn't exist, the stream is created.
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
    ): Promise<string | null> {
        return this.createWritePromise(createXAdd(key, values, options));
    }

    /**
     * Trims the stream stored at `key` by evicting older entries.
     * See https://redis.io/commands/xtrim/ for more details.
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
     * See https://redis.io/commands/xread/ for more details.
     *
     * @param keys_and_ids - pairs of keys and entry ids to read from. A pair is composed of a stream's key and the id of the entry after which the stream will be read.
     * @param options - options detailing how to read the stream.
     * @returns A map of stream keys, to a map of stream ids, to an array of entries.
     * @example
     * ```typescript
     * const streamResults = await client.xread({"my_stream": "0-0", "writers": "0-0"});
     * console.log(result); // Output: {
     *                      //     "my_stream": {
     *                      //         "1526984818136-0": [["duration", "1532"], ["event-id", "5"], ["user-id", "7782813"]],
     *                      //         "1526999352406-0": [["duration", "812"], ["event-id", "9"], ["user-id", "388234"]],
     *                      //     }, "writers": {
     *                      //         "1526985676425-0": [["name", "Virginia"], ["surname", "Woolf"]],
     *                      //         "1526985685298-0": [["name", "Jane"], ["surname", "Austen"]],
     *                      //     }
     *                      // }
     * ```
     */
    public xread(
        keys_and_ids: Record<string, string>,
        options?: StreamReadOptions,
    ): Promise<Record<string, Record<string, string[][]>>> {
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
     * See https://redis.io/commands/lindex/ for more details.
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
     * See https://redis.io/commands/persist/ for more details.
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
     * See https://redis.io/commands/rename/ for more details.
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
     * See https://redis.io/commands/renamenx/ for more details.
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
     * See https://redis.io/commands/brpop/ for more details.
     *
     * @remarks
     * 1. When in cluster mode, all `keys` must map to the same hash slot.
     * 2. `BRPOP` is a blocking command, see [Blocking Commands](https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands) for more details and best practices.
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
     * See https://redis.io/commands/blpop/ for more details.
     *
     * @remarks
     * 1. When in cluster mode, all `keys` must map to the same hash slot.
     * 2. `BLPOP` is a blocking command, see [Blocking Commands](https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands) for more details and best practices.
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
     * See https://redis.io/commands/pfadd/ for more details.
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
     * @internal
     */
    protected createClientRequest(
        options: BaseClientConfiguration,
    ): connection_request.IConnectionRequest {
        const readFrom = options.readFrom
            ? this.MAP_READ_FROM_STRATEGY[options.readFrom]
            : undefined;
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
            reject(new ClosingError(errorMessage));
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
        return await this.__createClientInternal<TConnection>(
            options,
            socket,
            constructor,
        );
    }
}
