import {
    DEFAULT_TIMEOUT_IN_MILLISECONDS,
    Script,
    StartSocketConnection,
    valueFromSplitPointer,
} from "glide-rs";
import * as net from "net";
import { Buffer, BufferWriter, Reader, Writer } from "protobufjs";
import {
    ExpireOptions,
    SetOptions,
    ZaddOptions,
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
    createHMGet,
    createHSet,
    createIncr,
    createIncrBy,
    createIncrByFloat,
    createLLen,
    createLPop,
    createLPush,
    createLRange,
    createLRem,
    createLTrim,
    createMGet,
    createMSet,
    createPExpire,
    createPExpireAt,
    createRPop,
    createRPush,
    createSAdd,
    createSCard,
    createSMembers,
    createSRem,
    createSet,
    createTTL,
    createUnlink,
    createZadd,
    createZrem,
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
     * @example
     * <code>
     * [
     *   \{ address:sample-address-0001.use1.cache.amazonaws.com, port:6378 \},
     *   \{ address: sample-address-0002.use2.cache.amazonaws.com \}
     *   \{ address: sample-address-0003.use2.cache.amazonaws.com, port:6380 \}
     * ]
     * </code>
     */
    addresses: {
        host: string;
        /**
         * If port isn't supplied, 6379 will be used
         */
        port?: number;
    }[];
    /**
     * `"auto"` if communication with the cluster should use Transport Level Security, with automatic loading of certificates.
     * `"disabled"` if no TLS should be used.
     * Should match the TLS configuration of the server/cluster, otherwise the connection attempt will fail.
     */
    tlsMode?: "auto" | "disabled";
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
    serverProtocol?: ProtocolVersion;
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
    type: response.RequestErrorType | null | undefined
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
        ErrorFunction
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
                    message.requestError.type
                );
                reject(
                    new errorType(message.requestError.message ?? undefined)
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
        options?: BaseClientConfiguration
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
        route?: redis_request.Routes
    ): Promise<T> {
        if (this.isClosed) {
            throw new ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
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
        route?: redis_request.Routes
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
            }
        );
    }

    private writeOrBufferRequest<TRequest>(
        message: TRequest,
        encodeDelimited: (message: TRequest, writer: Writer) => void
    ) {
        encodeDelimited(message, this.requestWriter);

        if (this.writeInProgress) {
            return;
        }

        this.writeBufferedRequestsToSocket();
    }

    /** Get the value associated with the given key, or null if no such value exists.
     * See https://redis.io/commands/get/ for details.
     *
     * @param key - The key to retrieve from the database.
     * @returns If `key` exists, returns the value of `key` as a string. Otherwise, return null.
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
     */
    public set(
        key: string,
        value: string,
        options?: SetOptions
    ): Promise<"OK" | string | null> {
        return this.createWritePromise(createSet(key, value, options));
    }

    /** Removes the specified keys. A key is ignored if it does not exist.
     * See https://redis.io/commands/del/ for details.
     *
     * @param keys - the keys we wanted to remove.
     * @returns the number of keys that were removed.
     */
    public del(keys: string[]): Promise<number> {
        return this.createWritePromise(createDel(keys));
    }

    /** Retrieve the values of multiple keys.
     * See https://redis.io/commands/mget/ for details.
     *
     * @param keys - A list of keys to retrieve values for.
     * @returns A list of values corresponding to the provided keys. If a key is not found,
     * its corresponding value in the list will be null.
     */
    public mget(keys: string[]): Promise<(string | null)[]> {
        return this.createWritePromise(createMGet(keys));
    }

    /** Set multiple keys to multiple values in a single operation.
     * See https://redis.io/commands/mset/ for details.
     *
     * @param keyValueMap - A key-value map consisting of keys and their respective values to set.
     * @returns always "OK".
     */
    public mset(keyValueMap: Record<string, string>): Promise<"OK"> {
        return this.createWritePromise(createMSet(keyValueMap));
    }

    /** Increments the number stored at `key` by one. If `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/incr/ for details.
     *
     * @param key - The key to increment its value.
     * @returns the value of `key` after the increment, An error is raised if `key` contains a value
     * of the wrong type or contains a string that can not be represented as integer.
     */
    public incr(key: string): Promise<number> {
        return this.createWritePromise(createIncr(key));
    }

    /** Increments the number stored at `key` by `amount`. If `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/incrby/ for details.
     *
     * @param key - The key to increment its value.
     * @param amount - The amount to increment.
     * @returns the value of `key` after the increment, An error is raised if `key` contains a value
     * of the wrong type or contains a string that can not be represented as integer.
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
     * An error is raised if `key` contains a value of the wrong type,
     * or the current key content is not parsable as a double precision floating point number.
     *
     */
    public incrByFloat(key: string, amount: number): Promise<number> {
        return this.createWritePromise(createIncrByFloat(key, amount));
    }

    /** Decrements the number stored at `key` by one. If `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/decr/ for details.
     *
     * @param key - The key to decrement its value.
     * @returns the value of `key` after the decrement. An error is raised if `key` contains a value
     * of the wrong type or contains a string that can not be represented as integer.
     */
    public decr(key: string): Promise<number> {
        return this.createWritePromise(createDecr(key));
    }

    /** Decrements the number stored at `key` by `amount`. If `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/decrby/ for details.
     *
     * @param key - The key to decrement its value.
     * @param amount - The amount to decrement.
     * @returns the value of `key` after the decrement. An error is raised if `key` contains a value
     * of the wrong type or contains a string that can not be represented as integer.
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
     */
    public hset(
        key: string,
        fieldValueMap: Record<string, string>
    ): Promise<number> {
        return this.createWritePromise(createHSet(key, fieldValueMap));
    }

    /** Removes the specified fields from the hash stored at `key`.
     * Specified fields that do not exist within this hash are ignored.
     * See https://redis.io/commands/hdel/ for details.
     *
     * @param key - The key of the hash.
     * @param fields - The fields to remove from the hash stored at `key`.
     * @returns the number of fields that were removed from the hash, not including specified but non existing fields.
     * If `key` does not exist, it is treated as an empty hash and it returns 0.
     * If `key` holds a value that is not a hash, an error is raised.
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
     * If `key` holds a value that is not a hash, an error is raised.
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
     *  An error will be raised if `key` holds a value of an incorrect type (not a string)
     *  or if it contains a string that cannot be represented as an integer.
     */
    public hincrBy(
        key: string,
        field: string,
        amount: number
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
     *  An error is raised if `key` contains a value of the wrong type
     *  or the current field content is not parsable as a double precision floating point number.
     *
     */
    public hincrByFloat(
        key: string,
        field: string,
        amount: number
    ): Promise<number> {
        return this.createWritePromise(createHIncrByFloat(key, field, amount));
    }

    /** Inserts all the specified values at the head of the list stored at `key`.
     * `elements` are inserted one after the other to the head of the list, from the leftmost element to the rightmost element.
     * If `key` does not exist, it is created as empty list before performing the push operations.
     * See https://redis.io/commands/lpush/ for details.
     *
     * @param key - The key of the list.
     * @param elements - The elements to insert at the head of the list stored at `key`.
     * @returns the length of the list after the push operations.
     * If `key` holds a value that is not a list, an error is raised.
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
     * If `key` holds a value that is not a list, an error is raised.
     */
    public lpop(
        key: string,
    ): Promise<string | null> {
        return this.createWritePromise(createLPop(key));
    }

    /** Removes and returns up to `count` elements of the list stored at `key`, depending on the list's length.
     * See https://redis.io/commands/lpop/ for details.
     *
     * @param key - The key of the list.
     * @param count - The count of the elements to pop from the list.
     * @returns A list of the popped elements will be returned depending on the list's length.
     * If `key` does not exist null will be returned.
     * If `key` holds a value that is not a list, an error is raised.
     */
    public lpopCount(
        key: string,
        count: number
    ): Promise<string[] | null> {
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
     * If `key` holds a value that is not a list, an error is raised.
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
     * If `key` holds a value that is not a list, an error is raised.
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
     * If `key` holds a value that is not a list, an error is raised.
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
     * If `key` holds a value that is not a list, an error is raised.
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
     * If `key` holds a value that is not a list, an error is raised.
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
     * If `key` holds a value that is not a list, an error is raised.
     */
    public rpop(
        key: string,
    ): Promise<string | null> {
        return this.createWritePromise(createRPop(key));
    }

    /** Removes and returns up to `count` elements from the list stored at `key`, depending on the list's length.
     * See https://redis.io/commands/rpop/ for details.
     *
     * @param key - The key of the list.
     * @param count - The count of the elements to pop from the list.
     * @returns A list of popped elements will be returned depending on the list's length.
     * If `key` does not exist null will be returned.
     * If `key` holds a value that is not a list, an error is raised.
     */
    public rpopCount(
        key: string,
        count: number
    ): Promise<string[] | null> {
        return this.createWritePromise(createRPop(key, count));
    }

    /** Adds the specified members to the set stored at `key`. Specified members that are already a member of this set are ignored.
     * If `key` does not exist, a new set is created before adding `members`.
     * See https://redis.io/commands/sadd/ for details.
     *
     * @param key - The key to store the members to its set.
     * @param members - A list of members to add to the set stored at `key`.
     * @returns the number of members that were added to the set, not including all the members already present in the set.
     * If `key` holds a value that is not a set, an error is raised.
     */
    public sadd(key: string, members: string[]): Promise<number> {
        return this.createWritePromise(createSAdd(key, members));
    }

    /** Removes the specified members from the set stored at `key`. Specified members that are not a member of this set are ignored.
     * See https://redis.io/commands/srem/ for details.
     *
     * @param key - The key to remove the members from its set.
     * @param members - A list of members to remove from the set stored at `key`.
     * @returns the number of members that were removed from the set, not including non existing members.
     * If `key` does not exist, it is treated as an empty set and this command returns 0.
     * If `key` holds a value that is not a set, an error is raised.
     */
    public srem(key: string, members: string[]): Promise<number> {
        return this.createWritePromise(createSRem(key, members));
    }

    /** Returns all the members of the set value stored at `key`.
     * See https://redis.io/commands/smembers/ for details.
     *
     * @param key - The key to return its members.
     * @returns all members of the set.
     * If `key` does not exist, it is treated as an empty set and this command returns empty list.
     * If `key` holds a value that is not a set, an error is raised.
     */
    public smembers(key: string): Promise<string[]> {
        return this.createWritePromise(createSMembers(key));
    }

    /** Returns the set cardinality (number of elements) of the set stored at `key`.
     * See https://redis.io/commands/scard/ for details.
     *
     * @param key - The key to return the number of its members.
     * @returns the cardinality (number of elements) of the set, or 0 if key does not exist.
     * If `key` holds a value that is not a set, an error is raised.
     */
    public scard(key: string): Promise<number> {
        return this.createWritePromise(createSCard(key));
    }

    /** Returns the number of keys in `keys` that exist in the database.
     * See https://redis.io/commands/exists/ for details.
     *
     * @param keys - The keys list to check.
     * @returns the number of keys that exist. If the same existing key is mentioned in `keys` multiple times,
     * it will be counted multiple times.
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
     * @returns the number of keys that were unlinked.
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
     */
    public expire(
        key: string,
        seconds: number,
        option?: ExpireOptions
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
     */
    public expireAt(
        key: string,
        unixSeconds: number,
        option?: ExpireOptions
    ): Promise<boolean> {
        return this.createWritePromise(
            createExpireAt(key, unixSeconds, option)
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
     */
    public pexpire(
        key: string,
        milliseconds: number,
        option?: ExpireOptions
    ): Promise<boolean> {
        return this.createWritePromise(
            createPExpire(key, milliseconds, option)
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
     */
    public pexpireAt(
        key: string,
        unixMilliseconds: number,
        option?: ExpireOptions
    ): Promise<number> {
        return this.createWritePromise(
            createPExpireAt(key, unixMilliseconds, option)
        );
    }

    /** Returns the remaining time to live of `key` that has a timeout.
     * See https://redis.io/commands/ttl/ for details.
     *
     * @param key - The key to return its timeout.
     * @returns TTL in seconds, -2 if `key` does not exist or -1 if `key` exists but has no associated expire.
     */
    public ttl(key: string): Promise<number> {
        return this.createWritePromise(createTTL(key));
    }

    /** Invokes a Lua script with its keys and arguments.
     * This method simplifies the process of invoking scripts on a Redis server by using an object that represents a Lua script.
     * The script loading, argument preparation, and execution will all be handled internally. If the script has not already been loaded,
     * it will be loaded automatically using the Redis `SCRIPT LOAD` command. After that, it will be invoked using the Redis `EVALSHA` command
     *
     * @param script - The Lua script to execute.
     * @param options - The script option that contains keys and arguments for the script.
     * @returns a value that depends on the script that was executed.
     *
     * @example
     *       const luaScript = "return \{ KEYS[1], ARGV[1] \}";
     *       const scriptOptions = \{
     *            keys: ["foo"],
     *            args: ["bar"],
     *       \};
     *       await invokeScript(luaScript, scriptOptions);
     *       ["foo", "bar"]
     */
    public invokeScript(
        script: Script,
        option?: ScriptOptions
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
     * @param options - The Zadd options.
     * @param changed - Modify the return value from the number of new elements added, to the total number of elements changed.
     * @returns The number of elements added to the sorted set.
     * If `changed` is set, returns the number of elements updated in the sorted set.
     *
     * @example
     *      await zadd("mySortedSet", \{ "member1": 10.5, "member2": 8.2 \})
     *      2 (Indicates that two elements have been added or updated in the sorted set "mySortedSet".)
     *
     *      await zadd("existingSortedSet", \{ member1: 15.0, member2: 5.5 \}, \{ conditionalChange: "onlyIfExists" \});
     *      2 (Updates the scores of two existing members in the sorted set "existingSortedSet".)
     *
     */
    public zadd(
        key: string,
        membersScoresMap: Record<string, number>,
        options?: ZaddOptions,
        changed?: boolean
    ): Promise<number> {
        return this.createWritePromise(
            createZadd(
                key,
                membersScoresMap,
                options,
                changed ? "CH" : undefined
            )
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
     * @returns The score of the member.
     * If there was a conflict with the options, the operation aborts and null is returned.
     *
     * @example
     *      await zaddIncr("mySortedSet", member , 5.0)
     *      5.0
     *
     *      await zaddIncr("existingSortedSet", member , "3.0" , \{ UpdateOptions: "ScoreLessThanCurrent" \})
     *      null
     */
    public zaddIncr(
        key: string,
        member: string,
        increment: number,
        options?: ZaddOptions
    ): Promise<number | null> {
        return this.createWritePromise(
            createZadd(key, { [member]: increment }, options, "INCR")
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
     * If `key` holds a value that is not a sorted set, an error is returned.
     */
    public zrem(key: string, members: string[]): Promise<number> {
        return this.createWritePromise(createZrem(key, members));
    }

    private readonly MAP_READ_FROM_STRATEGY: Record<
        ReadFrom,
        connection_request.ReadFrom
    > = {
        primary: connection_request.ReadFrom.Primary,
        preferReplica: connection_request.ReadFrom.PreferReplica,
    };

    /**
     * @internal
     */
    protected createClientRequest(
        options: BaseClientConfiguration
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
        const protocol = options.serverProtocol as
            | connection_request.ProtocolVersion
            | undefined;
        return {
            protocol,
            clientName: options.clientName,
            addresses: options.addresses,
            tlsMode:
                options.tlsMode == "auto"
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
                this.createClientRequest(options)
            );

            this.writeOrBufferRequest(
                message,
                (
                    message: connection_request.ConnectionRequest,
                    writer: Writer
                ) => {
                    connection_request.ConnectionRequest.encodeDelimited(
                        message,
                        writer
                    );
                }
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
        TConnection extends BaseClient
    >(
        options: BaseClientConfiguration,
        connectedSocket: net.Socket,
        constructor: (
            socket: net.Socket,
            options?: BaseClientConfiguration
        ) => TConnection
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
            options?: BaseClientConfiguration
        ) => TConnection
    ): Promise<TConnection> {
        const path = await StartSocketConnection();
        const socket = await this.GetSocket(path);
        return await this.__createClientInternal<TConnection>(
            options,
            socket,
            constructor
        );
    }
}
