/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { createLeakedStringVec, MAX_REQUEST_ARGS_LEN } from "glide-rs";
import Long from "long";

import { redis_request } from "./ProtobufMessage";

import RequestType = redis_request.RequestType;

function isLargeCommand(args: BulkString[]) {
    let lenSum = 0;

    for (const arg of args) {
        lenSum += arg.length;

        if (lenSum >= MAX_REQUEST_ARGS_LEN) {
            return true;
        }
    }

    return false;
}

type BulkString = string | Uint8Array;

/**
 * Convert a string array into Uint8Array[]
 */
function toBuffersArray(args: BulkString[]) {
    const argsBytes: Uint8Array[] = [];

    for (const arg of args) {
        if (typeof arg == "string") {
            argsBytes.push(Buffer.from(arg));
        } else {
            argsBytes.push(arg);
        }
    }

    return argsBytes;
}

/**
 * @internal
 */
export function parseInfoResponse(response: string): Record<string, string> {
    const lines = response.split("\n");
    const parsedResponse: Record<string, string> = {};

    for (const line of lines) {
        // Ignore lines that start with '#'
        if (!line.startsWith("#")) {
            const [key, value] = line.trim().split(":");
            parsedResponse[key] = value;
        }
    }

    return parsedResponse;
}

function createCommand(
    requestType: redis_request.RequestType,
    args: BulkString[],
): redis_request.Command {
    const singleCommand = redis_request.Command.create({
        requestType,
    });

    const argsBytes = toBuffersArray(args);

    if (isLargeCommand(args)) {
        // pass as a pointer
        const pointerArr = createLeakedStringVec(argsBytes);
        const pointer = new Long(pointerArr[0], pointerArr[1]);
        singleCommand.argsVecPointer = pointer;
    } else {
        singleCommand.argsArray = redis_request.Command.ArgsArray.create({
            args: argsBytes,
        });
    }

    return singleCommand;
}

/**
 * @internal
 */
export function createGet(key: string): redis_request.Command {
    return createCommand(RequestType.Get, [key]);
}

export type SetOptions = {
    /**
     *  `onlyIfDoesNotExist` - Only set the key if it does not already exist.
     * Equivalent to `NX` in the Redis API. `onlyIfExists` - Only set the key if
     * it already exist. Equivalent to `EX` in the Redis API. if `conditional` is
     * not set the value will be set regardless of prior value existence. If value
     * isn't set because of the condition, return null.
     */
    conditionalSet?: "onlyIfExists" | "onlyIfDoesNotExist";
    /**
     * Return the old string stored at key, or nil if key did not exist. An error
     * is returned and SET aborted if the value stored at key is not a string.
     * Equivalent to `GET` in the Redis API.
     */
    returnOldValue?: boolean;
    /**
     * If not set, no expiry time will be set for the value.
     */
    expiry?: /**
     * Retain the time to live associated with the key. Equivalent to
     * `KEEPTTL` in the Redis API.
     */
    | "keepExisting"
        | {
              type: /**
               * Set the specified expire time, in seconds. Equivalent to
               * `EX` in the Redis API.
               */
              | "seconds"
                  /**
                   * Set the specified expire time, in milliseconds. Equivalent
                   * to `PX` in the Redis API.
                   */
                  | "milliseconds"
                  /**
                   * Set the specified Unix time at which the key will expire,
                   * in seconds. Equivalent to `EXAT` in the Redis API.
                   */
                  | "unixSeconds"
                  /**
                   * Set the specified Unix time at which the key will expire,
                   * in milliseconds. Equivalent to `PXAT` in the Redis API.
                   */
                  | "unixMilliseconds";
              count: number;
          };
};

/**
 * @internal
 */
export function createSet(
    key: BulkString,
    value: BulkString,
    options?: SetOptions,
): redis_request.Command {
    const args = [key, value];

    if (options) {
        if (options.conditionalSet === "onlyIfExists") {
            args.push("XX");
        } else if (options.conditionalSet === "onlyIfDoesNotExist") {
            args.push("NX");
        }

        if (options.returnOldValue) {
            args.push("GET");
        }

        if (
            options.expiry &&
            options.expiry !== "keepExisting" &&
            !Number.isInteger(options.expiry.count)
        ) {
            throw new Error(
                `Received expiry '${JSON.stringify(
                    options.expiry,
                )}'. Count must be an integer`,
            );
        }

        if (options.expiry === "keepExisting") {
            args.push("KEEPTTL");
        } else if (options.expiry?.type === "seconds") {
            args.push("EX", options.expiry.count.toString());
        } else if (options.expiry?.type === "milliseconds") {
            args.push("PX", options.expiry.count.toString());
        } else if (options.expiry?.type === "unixSeconds") {
            args.push("EXAT", options.expiry.count.toString());
        } else if (options.expiry?.type === "unixMilliseconds") {
            args.push("PXAT", options.expiry.count.toString());
        }
    }

    return createCommand(RequestType.Set, args);
}

/**
 * INFO option: a specific section of information:
 * When no parameter is provided, the default option is assumed.
 */
export enum InfoOptions {
    /**
     * SERVER: General information about the Redis server
     */
    Server = "server",
    /**
     * CLIENTS: Client connections section
     */
    Clients = "clients",
    /**
     * MEMORY: Memory consumption related information
     */
    Memory = "memory",
    /**
     * PERSISTENCE: RDB and AOF related information
     */
    Persistence = "persistence",
    /**
     * STATS: General statistics
     */
    Stats = "stats",
    /**
     * REPLICATION: Master/replica replication information
     */
    Replication = "replication",
    /**
     * CPU: CPU consumption statistics
     */
    Cpu = "cpu",
    /**
     * COMMANDSTATS: Redis command statistics
     */
    Commandstats = "commandstats",
    /**
     * LATENCYSTATS: Redis command latency percentile distribution statistics
     */
    Latencystats = "latencystats",
    /**
     * SENTINEL: Redis Sentinel section (only applicable to Sentinel instances)
     */
    Sentinel = "sentinel",
    /**
     * CLUSTER: Redis Cluster section
     */
    Cluster = "cluster",
    /**
     * MODULES: Modules section
     */
    Modules = "modules",
    /**
     * KEYSPACE: Database related statistics
     */
    Keyspace = "keyspace",
    /**
     * ERRORSTATS: Redis error statistics
     */
    Errorstats = "errorstats",
    /**
     * ALL: Return all sections (excluding module generated ones)
     */
    All = "all",
    /**
     * DEFAULT: Return only the default set of sections
     */
    Default = "default",
    /**
     * EVERYTHING: Includes all and modules
     */
    Everything = "everything",
}

/**
 * @internal
 */
export function createPing(str?: string): redis_request.Command {
    const args: string[] = str == undefined ? [] : [str];
    return createCommand(RequestType.Ping, args);
}

/**
 * @internal
 */
export function createInfo(options?: InfoOptions[]): redis_request.Command {
    const args: string[] = options == undefined ? [] : options;
    return createCommand(RequestType.Info, args);
}

/**
 * @internal
 */
export function createDel(keys: string[]): redis_request.Command {
    return createCommand(RequestType.Del, keys);
}

/**
 * @internal
 */
export function createSelect(index: number): redis_request.Command {
    return createCommand(RequestType.Select, [index.toString()]);
}

/**
 * @internal
 */
export function createClientGetName(): redis_request.Command {
    return createCommand(RequestType.ClientGetName, []);
}

/**
 * @internal
 */
export function createConfigRewrite(): redis_request.Command {
    return createCommand(RequestType.ConfigRewrite, []);
}

/**
 * @internal
 */
export function createConfigResetStat(): redis_request.Command {
    return createCommand(RequestType.ConfigResetStat, []);
}

/**
 * @internal
 */
export function createMGet(keys: string[]): redis_request.Command {
    return createCommand(RequestType.MGet, keys);
}

/**
 * @internal
 */
export function createMSet(
    keyValueMap: Record<string, string>,
): redis_request.Command {
    return createCommand(RequestType.MSet, Object.entries(keyValueMap).flat());
}

/**
 * @internal
 */
export function createIncr(key: string): redis_request.Command {
    return createCommand(RequestType.Incr, [key]);
}

/**
 * @internal
 */
export function createIncrBy(
    key: string,
    amount: number,
): redis_request.Command {
    return createCommand(RequestType.IncrBy, [key, amount.toString()]);
}

/**
 * @internal
 */
export function createIncrByFloat(
    key: string,
    amount: number,
): redis_request.Command {
    return createCommand(RequestType.IncrByFloat, [key, amount.toString()]);
}

/**
 * @internal
 */
export function createClientId(): redis_request.Command {
    return createCommand(RequestType.ClientId, []);
}

/**
 * @internal
 */
export function createConfigGet(parameters: string[]): redis_request.Command {
    return createCommand(RequestType.ConfigGet, parameters);
}

/**
 * @internal
 */
export function createConfigSet(
    parameters: Record<string, string>,
): redis_request.Command {
    return createCommand(
        RequestType.ConfigSet,
        Object.entries(parameters).flat(),
    );
}

/**
 * @internal
 */
export function createHGet(key: string, field: string): redis_request.Command {
    return createCommand(RequestType.HGet, [key, field]);
}

/**
 * @internal
 */
export function createHSet(
    key: string,
    fieldValueMap: Record<string, string>,
): redis_request.Command {
    return createCommand(
        RequestType.HSet,
        [key].concat(Object.entries(fieldValueMap).flat()),
    );
}

/**
 * @internal
 */
export function createHSetNX(
    key: string,
    field: string,
    value: string,
): redis_request.Command {
    return createCommand(RequestType.HSetNX, [key, field, value]);
}

/**
 * @internal
 */
export function createDecr(key: string): redis_request.Command {
    return createCommand(RequestType.Decr, [key]);
}

/**
 * @internal
 */
export function createDecrBy(
    key: string,
    amount: number,
): redis_request.Command {
    return createCommand(RequestType.DecrBy, [key, amount.toString()]);
}

/**
 * @internal
 */
export function createHDel(
    key: string,
    fields: string[],
): redis_request.Command {
    return createCommand(RequestType.HDel, [key].concat(fields));
}

/**
 * @internal
 */
export function createHMGet(
    key: string,
    fields: string[],
): redis_request.Command {
    return createCommand(RequestType.HMGet, [key].concat(fields));
}

/**
 * @internal
 */
export function createHExists(
    key: string,
    field: string,
): redis_request.Command {
    return createCommand(RequestType.HExists, [key, field]);
}

/**
 * @internal
 */
export function createHGetAll(key: string): redis_request.Command {
    return createCommand(RequestType.HGetAll, [key]);
}

/**
 * @internal
 */
export function createLPush(
    key: string,
    elements: string[],
): redis_request.Command {
    return createCommand(RequestType.LPush, [key].concat(elements));
}

/**
 * @internal
 */
export function createLPop(key: string, count?: number): redis_request.Command {
    const args: string[] = count == undefined ? [key] : [key, count.toString()];
    return createCommand(RequestType.LPop, args);
}

/**
 * @internal
 */
export function createLRange(
    key: string,
    start: number,
    end: number,
): redis_request.Command {
    return createCommand(RequestType.LRange, [
        key,
        start.toString(),
        end.toString(),
    ]);
}

/**
 * @internal
 */
export function createLLen(key: string): redis_request.Command {
    return createCommand(RequestType.LLen, [key]);
}

/**
 * @internal
 */
export function createLTrim(
    key: string,
    start: number,
    end: number,
): redis_request.Command {
    return createCommand(RequestType.LTrim, [
        key,
        start.toString(),
        end.toString(),
    ]);
}

/**
 * @internal
 */
export function createLRem(
    key: string,
    count: number,
    element: string,
): redis_request.Command {
    return createCommand(RequestType.LRem, [key, count.toString(), element]);
}

/**
 * @internal
 */
export function createRPush(
    key: string,
    elements: string[],
): redis_request.Command {
    return createCommand(RequestType.RPush, [key].concat(elements));
}

/**
 * @internal
 */
export function createRPop(key: string, count?: number): redis_request.Command {
    const args: string[] = count == undefined ? [key] : [key, count.toString()];
    return createCommand(RequestType.RPop, args);
}

/**
 * @internal
 */
export function createSAdd(
    key: string,
    members: string[],
): redis_request.Command {
    return createCommand(RequestType.SAdd, [key].concat(members));
}

/**
 * @internal
 */
export function createSRem(
    key: string,
    members: string[],
): redis_request.Command {
    return createCommand(RequestType.SRem, [key].concat(members));
}

/**
 * @internal
 */
export function createSMembers(key: string): redis_request.Command {
    return createCommand(RequestType.SMembers, [key]);
}

/**
 *
 * @internal
 */
export function createSMove(
    source: string,
    destination: string,
    member: string,
): redis_request.Command {
    return createCommand(RequestType.SMove, [source, destination, member]);
}

/**
 * @internal
 */
export function createSCard(key: string): redis_request.Command {
    return createCommand(RequestType.SCard, [key]);
}

/**
 * @internal
 */
export function createSInter(keys: string[]): redis_request.Command {
    return createCommand(RequestType.SInter, keys);
}

/**
 * @internal
 */
export function createSUnionStore(
    destination: string,
    keys: string[],
): redis_request.Command {
    return createCommand(RequestType.SUnionStore, [destination].concat(keys));
}

/**
 * @internal
 */
export function createSIsMember(
    key: string,
    member: string,
): redis_request.Command {
    return createCommand(RequestType.SIsMember, [key, member]);
}

/**
 * @internal
 */
export function createSPop(key: string, count?: number): redis_request.Command {
    const args: string[] = count == undefined ? [key] : [key, count.toString()];
    return createCommand(RequestType.SPop, args);
}

/**
 * @internal
 */
export function createCustomCommand(args: string[]) {
    return createCommand(RequestType.CustomCommand, args);
}

/**
 * @internal
 */
export function createHIncrBy(
    key: string,
    field: string,
    amount: number,
): redis_request.Command {
    return createCommand(RequestType.HIncrBy, [key, field, amount.toString()]);
}

/**
 * @internal
 */
export function createHIncrByFloat(
    key: string,
    field: string,
    amount: number,
): redis_request.Command {
    return createCommand(RequestType.HIncrByFloat, [
        key,
        field,
        amount.toString(),
    ]);
}

/**
 * @internal
 */
export function createHLen(key: string): redis_request.Command {
    return createCommand(RequestType.HLen, [key]);
}

/**
 * @internal
 */
export function createHVals(key: string): redis_request.Command {
    return createCommand(RequestType.HVals, [key]);
}

/**
 * @internal
 */
export function createExists(keys: string[]): redis_request.Command {
    return createCommand(RequestType.Exists, keys);
}

/**
 * @internal
 */
export function createUnlink(keys: string[]): redis_request.Command {
    return createCommand(RequestType.Unlink, keys);
}

export enum ExpireOptions {
    /**
     * `HasNoExpiry` - Sets expiry only when the key has no expiry.
     */
    HasNoExpiry = "NX",
    /**
     * `HasExistingExpiry` - Sets expiry only when the key has an existing expiry.
     */
    HasExistingExpiry = "XX",
    /**
     * `NewExpiryGreaterThanCurrent` - Sets expiry only when the new expiry is
     * greater than current one.
     */
    NewExpiryGreaterThanCurrent = "GT",
    /**
     * `NewExpiryLessThanCurrent` - Sets expiry only when the new expiry is less
     * than current one.
     */
    NewExpiryLessThanCurrent = "LT",
}

/**
 * @internal
 */
export function createExpire(
    key: string,
    seconds: number,
    option?: ExpireOptions,
): redis_request.Command {
    const args: string[] =
        option == undefined
            ? [key, seconds.toString()]
            : [key, seconds.toString(), option];
    return createCommand(RequestType.Expire, args);
}

/**
 * @internal
 */
export function createExpireAt(
    key: string,
    unixSeconds: number,
    option?: ExpireOptions,
): redis_request.Command {
    const args: string[] =
        option == undefined
            ? [key, unixSeconds.toString()]
            : [key, unixSeconds.toString(), option];
    return createCommand(RequestType.ExpireAt, args);
}

/**
 * @internal
 */
export function createPExpire(
    key: string,
    milliseconds: number,
    option?: ExpireOptions,
): redis_request.Command {
    const args: string[] =
        option == undefined
            ? [key, milliseconds.toString()]
            : [key, milliseconds.toString(), option];
    return createCommand(RequestType.PExpire, args);
}

/**
 * @internal
 */
export function createPExpireAt(
    key: string,
    unixMilliseconds: number,
    option?: ExpireOptions,
): redis_request.Command {
    const args: string[] =
        option == undefined
            ? [key, unixMilliseconds.toString()]
            : [key, unixMilliseconds.toString(), option];
    return createCommand(RequestType.PExpireAt, args);
}

/**
 * @internal
 */
export function createTTL(key: string): redis_request.Command {
    return createCommand(RequestType.TTL, [key]);
}

export type ZAddOptions = {
    /**
     * `onlyIfDoesNotExist` - Only add new elements. Don't update already existing
     * elements. Equivalent to `NX` in the Redis API. `onlyIfExists` - Only update
     * elements that already exist. Don't add new elements. Equivalent to `XX` in
     * the Redis API.
     */
    conditionalChange?: "onlyIfExists" | "onlyIfDoesNotExist";
    /**
     * `scoreLessThanCurrent` - Only update existing elements if the new score is
     * less than the current score. Equivalent to `LT` in the Redis API.
     * `scoreGreaterThanCurrent` - Only update existing elements if the new score
     * is greater than the current score. Equivalent to `GT` in the Redis API.
     */
    updateOptions?: "scoreLessThanCurrent" | "scoreGreaterThanCurrent";
};

/**
 * @internal
 */
export function createZAdd(
    key: string,
    membersScoresMap: Record<string, number>,
    options?: ZAddOptions,
    changedOrIncr?: "CH" | "INCR",
): redis_request.Command {
    let args = [key];

    if (options) {
        if (options.conditionalChange === "onlyIfExists") {
            args.push("XX");
        } else if (options.conditionalChange === "onlyIfDoesNotExist") {
            if (options.updateOptions) {
                throw new Error(
                    `The GT, LT, and NX options are mutually exclusive. Cannot choose both ${options.updateOptions} and NX.`,
                );
            }

            args.push("NX");
        }

        if (options.updateOptions === "scoreLessThanCurrent") {
            args.push("LT");
        } else if (options.updateOptions === "scoreGreaterThanCurrent") {
            args.push("GT");
        }
    }

    if (changedOrIncr) {
        args.push(changedOrIncr);
    }

    args = args.concat(
        Object.entries(membersScoresMap).flatMap(([key, value]) => [
            value.toString(),
            key,
        ]),
    );
    return createCommand(RequestType.ZAdd, args);
}

/**
 * `KeyWeight` - pair of variables represents a weighted key for the `ZINTERSTORE` and `ZUNIONSTORE` sorted sets commands.
 */
export type KeyWeight = [string, number];
/**
 * `AggregationType` - representing aggregation types for `ZINTERSTORE` and `ZUNIONSTORE` sorted set commands.
 */
export type AggregationType = "SUM" | "MIN" | "MAX";

/**
 * @internal
 */
export function createZInterstore(
    destination: string,
    keys: string[] | KeyWeight[],
    aggregationType?: AggregationType,
): redis_request.Command {
    const args = createZCmdStoreArgs(destination, keys, aggregationType);
    return createCommand(RequestType.ZInterStore, args);
}

function createZCmdStoreArgs(
    destination: string,
    keys: string[] | KeyWeight[],
    aggregationType?: AggregationType,
): string[] {
    const args: string[] = [destination, keys.length.toString()];

    if (typeof keys[0] === "string") {
        args.push(...(keys as string[]));
    } else {
        const weightsKeys = keys.map(([key]) => key);
        args.push(...(weightsKeys as string[]));
        const weights = keys.map(([, weight]) => weight.toString());
        args.push("WEIGHTS", ...weights);
    }

    if (aggregationType) {
        args.push("AGGREGATE", aggregationType);
    }

    return args;
}

/**
 * @internal
 */
export function createZRem(
    key: string,
    members: string[],
): redis_request.Command {
    return createCommand(RequestType.ZRem, [key].concat(members));
}

/**
 * @internal
 */
export function createZCard(key: string): redis_request.Command {
    return createCommand(RequestType.ZCard, [key]);
}

/**
 * @internal
 */
export function createZInterCard(
    keys: string[],
    limit?: number,
): redis_request.Command {
    let args: string[] = keys;
    args.unshift(keys.length.toString());

    if (limit != undefined) {
        args = args.concat(["LIMIT", limit.toString()]);
    }

    return createCommand(RequestType.ZInterCard, args);
}

/**
 * @internal
 */
export function createZScore(
    key: string,
    member: string,
): redis_request.Command {
    return createCommand(RequestType.ZScore, [key, member]);
}

export type ScoreBoundary<T> =
    /**
     * Positive infinity bound for sorted set.
     */
    | `positiveInfinity`
    /**
     * Negative infinity bound for sorted set.
     */
    | `negativeInfinity`
    /**
     *  Represents a specific numeric score boundary in a sorted set.
     */
    | {
          /**
           * The score value.
           */
          value: T;
          /**
           * Whether the score value is inclusive. Defaults to True.
           */
          isInclusive?: boolean;
      };

/**
 * Represents a range by index (rank) in a sorted set.
 * The `start` and `stop` arguments represent zero-based indexes.
 */
export type RangeByIndex = {
    /**
     *  The start index of the range.
     */
    start: number;
    /**
     * The stop index of the range.
     */
    stop: number;
};

/**
 * Represents a range by score or a range by lex in a sorted set.
 * The `start` and `stop` arguments represent score boundaries.
 */
type SortedSetRange<T> = {
    /**
     * The start boundary.
     */
    start: ScoreBoundary<T>;
    /**
     * The stop boundary.
     */
    stop: ScoreBoundary<T>;
    /**
     * The limit argument for a range query.
     * Represents a limit argument for a range query in a sorted set to
     * be used in [ZRANGE](https://redis.io/commands/zrange) command.
     *
     * The optional LIMIT argument can be used to obtain a sub-range from the
     * matching elements (similar to SELECT LIMIT offset, count in SQL).
     */
    limit?: {
        /**
         * The offset from the start of the range.
         */
        offset: number;
        /**
         * The number of elements to include in the range.
         * A negative count returns all elements from the offset.
         */
        count: number;
    };
};

export type RangeByScore = SortedSetRange<number> & { type: "byScore" };
export type RangeByLex = SortedSetRange<string> & { type: "byLex" };

/**
 * Returns a string representation of a score boundary in Redis protocol format.
 * @param score - The score boundary object containing value and inclusivity
 *     information.
 * @param isLex - Indicates whether to return lexical representation for
 *     positive/negative infinity.
 * @returns A string representation of the score boundary in Redis protocol
 *     format.
 */
function getScoreBoundaryArg(
    score: ScoreBoundary<number> | ScoreBoundary<string>,
    isLex: boolean = false,
): string {
    if (score == "positiveInfinity") {
        return isLex ? "+" : "+inf";
    } else if (score == "negativeInfinity") {
        return isLex ? "-" : "-inf";
    }

    if (score.isInclusive == false) {
        return "(" + score.value.toString();
    }

    const value = isLex ? "[" + score.value.toString() : score.value.toString();
    return value;
}

function createZRangeArgs(
    key: string,
    rangeQuery: RangeByScore | RangeByLex | RangeByIndex,
    reverse: boolean,
    withScores: boolean,
): string[] {
    const args: string[] = [key];

    if (typeof rangeQuery.start != "number") {
        rangeQuery = rangeQuery as RangeByScore | RangeByLex;
        const isLex = rangeQuery.type == "byLex";
        args.push(getScoreBoundaryArg(rangeQuery.start, isLex));
        args.push(getScoreBoundaryArg(rangeQuery.stop, isLex));
        args.push(isLex == true ? "BYLEX" : "BYSCORE");
    } else {
        args.push(rangeQuery.start.toString());
        args.push(rangeQuery.stop.toString());
    }

    if (reverse) {
        args.push("REV");
    }

    if ("limit" in rangeQuery && rangeQuery.limit !== undefined) {
        args.push(
            "LIMIT",
            String(rangeQuery.limit.offset),
            String(rangeQuery.limit.count),
        );
    }

    if (withScores) {
        args.push("WITHSCORES");
    }

    return args;
}

/**
 * @internal
 */
export function createZCount(
    key: string,
    minScore: ScoreBoundary<number>,
    maxScore: ScoreBoundary<number>,
): redis_request.Command {
    const args = [key];
    args.push(getScoreBoundaryArg(minScore));
    args.push(getScoreBoundaryArg(maxScore));
    return createCommand(RequestType.ZCount, args);
}

/**
 * @internal
 */
export function createZRange(
    key: string,
    rangeQuery: RangeByIndex | RangeByScore | RangeByLex,
    reverse: boolean = false,
): redis_request.Command {
    const args = createZRangeArgs(key, rangeQuery, reverse, false);
    return createCommand(RequestType.ZRange, args);
}

/**
 * @internal
 */
export function createZRangeWithScores(
    key: string,
    rangeQuery: RangeByIndex | RangeByScore | RangeByLex,
    reverse: boolean = false,
): redis_request.Command {
    const args = createZRangeArgs(key, rangeQuery, reverse, true);
    return createCommand(RequestType.ZRange, args);
}

/**
 * @internal
 */
export function createType(key: string): redis_request.Command {
    return createCommand(RequestType.Type, [key]);
}

/**
 * @internal
 */
export function createStrlen(key: string): redis_request.Command {
    return createCommand(RequestType.Strlen, [key]);
}

/**
 * @internal
 */
export function createLIndex(
    key: string,
    index: number,
): redis_request.Command {
    return createCommand(RequestType.LIndex, [key, index.toString()]);
}

/**
 * Defines where to insert new elements into a list.
 */
export enum InsertPosition {
    /**
     * Insert new element before the pivot.
     */
    Before = "before",
    /**
     * Insert new element after the pivot.
     */
    After = "after",
}

/**
 * @internal
 */
export function createLInsert(
    key: string,
    position: InsertPosition,
    pivot: string,
    element: string,
): redis_request.Command {
    return createCommand(RequestType.LInsert, [key, position, pivot, element]);
}

/**
 * @internal
 */
export function createZPopMin(
    key: string,
    count?: number,
): redis_request.Command {
    const args: string[] = count == undefined ? [key] : [key, count.toString()];
    return createCommand(RequestType.ZPopMin, args);
}

/**
 * @internal
 */
export function createZPopMax(
    key: string,
    count?: number,
): redis_request.Command {
    const args: string[] = count == undefined ? [key] : [key, count.toString()];
    return createCommand(RequestType.ZPopMax, args);
}

/**
 * @internal
 */
export function createEcho(message: string): redis_request.Command {
    return createCommand(RequestType.Echo, [message]);
}

/**
 * @internal
 */
export function createPTTL(key: string): redis_request.Command {
    return createCommand(RequestType.PTTL, [key]);
}

/**
 * @internal
 */
export function createZRemRangeByRank(
    key: string,
    start: number,
    stop: number,
): redis_request.Command {
    return createCommand(RequestType.ZRemRangeByRank, [
        key,
        start.toString(),
        stop.toString(),
    ]);
}

/**
 * @internal
 */
export function createZRemRangeByScore(
    key: string,
    minScore: ScoreBoundary<number>,
    maxScore: ScoreBoundary<number>,
): redis_request.Command {
    const args = [key];
    args.push(getScoreBoundaryArg(minScore));
    args.push(getScoreBoundaryArg(maxScore));
    return createCommand(RequestType.ZRemRangeByScore, args);
}

export function createPersist(key: string): redis_request.Command {
    return createCommand(RequestType.Persist, [key]);
}

export function createZRank(
    key: string,
    member: string,
    withScores?: boolean,
): redis_request.Command {
    const args = [key, member];

    if (withScores) {
        args.push("WITHSCORE");
    }

    return createCommand(RequestType.ZRank, args);
}

export type StreamTrimOptions = (
    | {
          /**
           * Trim the stream according to entry ID.
           * Equivalent to `MINID` in the Redis API.
           */
          method: "minid";
          threshold: string;
      }
    | {
          /**
           * Trim the stream according to length.
           * Equivalent to `MAXLEN` in the Redis API.
           */
          method: "maxlen";
          threshold: number;
      }
) & {
    /**
     * If `true`, the stream will be trimmed exactly. Equivalent to `=` in the
     * Redis API. Otherwise the stream will be trimmed in a near-exact manner,
     * which is more efficient, equivalent to `~` in the Redis API.
     */
    exact: boolean;
    /**
     * If set, sets the maximal amount of entries that will be deleted.
     */
    limit?: number;
};

export type StreamAddOptions = {
    /**
     * If set, the new entry will be added with this ID.
     */
    id?: string;
    /**
     * If set to `false`, a new stream won't be created if no stream matches the
     * given key. Equivalent to `NOMKSTREAM` in the Redis API.
     */
    makeStream?: boolean;
    /**
     * If set, the add operation will also trim the older entries in the stream.
     */
    trim?: StreamTrimOptions;
};

function addTrimOptions(options: StreamTrimOptions, args: string[]) {
    if (options.method === "maxlen") {
        args.push("MAXLEN");
    } else if (options.method === "minid") {
        args.push("MINID");
    }

    if (options.exact) {
        args.push("=");
    } else {
        args.push("~");
    }

    if (options.method === "maxlen") {
        args.push(options.threshold.toString());
    } else if (options.method === "minid") {
        args.push(options.threshold);
    }

    if (options.limit) {
        args.push("LIMIT");
        args.push(options.limit.toString());
    }
}

export function createXAdd(
    key: string,
    values: [string, string][],
    options?: StreamAddOptions,
): redis_request.Command {
    const args = [key];

    if (options?.makeStream === false) {
        args.push("NOMKSTREAM");
    }

    if (options?.trim) {
        addTrimOptions(options.trim, args);
    }

    if (options?.id) {
        args.push(options.id);
    } else {
        args.push("*");
    }

    values.forEach(([field, value]) => {
        args.push(field);
        args.push(value);
    });

    return createCommand(RequestType.XAdd, args);
}

/**
 * @internal
 */
export function createXTrim(
    key: string,
    options: StreamTrimOptions,
): redis_request.Command {
    const args = [key];
    addTrimOptions(options, args);
    return createCommand(RequestType.XTrim, args);
}

/**
 * @internal
 */
export function createTime(): redis_request.Command {
    return createCommand(RequestType.Time, []);
}

/**
 * @internal
 */
export function createBRPop(
    keys: string[],
    timeout: number,
): redis_request.Command {
    const args = [...keys, timeout.toString()];
    return createCommand(RequestType.BRPop, args);
}

/**
 * @internal
 */
export function createBLPop(
    keys: string[],
    timeout: number,
): redis_request.Command {
    const args = [...keys, timeout.toString()];
    return createCommand(RequestType.BLPop, args);
}

export type StreamReadOptions = {
    /**
     * If set, the read request will block for the set amount of milliseconds or
     * until the server has the required number of entries. Equivalent to `BLOCK`
     * in the Redis API.
     */
    block?: number;
    /**
     * The maximal number of elements requested.
     * Equivalent to `COUNT` in the Redis API.
     */
    count?: number;
};

function addReadOptions(options: StreamReadOptions, args: string[]) {
    if (options.count !== undefined) {
        args.push("COUNT");
        args.push(options.count.toString());
    }

    if (options.block !== undefined) {
        args.push("BLOCK");
        args.push(options.block.toString());
    }
}

function addStreamsArgs(keys_and_ids: Record<string, string>, args: string[]) {
    args.push("STREAMS");

    const pairs = Object.entries(keys_and_ids);

    for (const [key] of pairs) {
        args.push(key);
    }

    for (const [, id] of pairs) {
        args.push(id);
    }
}

/**
 * @internal
 */
export function createXRead(
    keys_and_ids: Record<string, string>,
    options?: StreamReadOptions,
): redis_request.Command {
    const args: string[] = [];

    if (options) {
        addReadOptions(options, args);
    }

    addStreamsArgs(keys_and_ids, args);

    return createCommand(RequestType.XRead, args);
}

/**
 * @internal
 */
export function createXLen(key: string): redis_request.Command {
    return createCommand(RequestType.XLen, [key]);
}

/**
 * @internal
 */
export function createRename(
    key: string,
    newKey: string,
): redis_request.Command {
    return createCommand(RequestType.Rename, [key, newKey]);
}

/**
 * @internal
 */
export function createRenameNX(
    key: string,
    newKey: string,
): redis_request.Command {
    return createCommand(RequestType.RenameNX, [key, newKey]);
}

/**
 * @internal
 */
export function createPfAdd(
    key: string,
    elements: string[],
): redis_request.Command {
    const args = [key, ...elements];
    return createCommand(RequestType.PfAdd, args);
}

/**
 * @internal
 */
export function createPfCount(keys: string[]): redis_request.Command {
    return createCommand(RequestType.PfCount, keys);
}

/**
 * @internal
 */
export function createObjectEncoding(key: string): redis_request.Command {
    return createCommand(RequestType.ObjectEncoding, [key]);
}

/**
 * @internal
 */
export function createObjectFreq(key: string): redis_request.Command {
    return createCommand(RequestType.ObjectFreq, [key]);
}

/**
 * @internal
 */
export function createObjectIdletime(key: string): redis_request.Command {
    return createCommand(RequestType.ObjectIdleTime, [key]);
}

/**
 * @internal
 */
export function createObjectRefcount(key: string): redis_request.Command {
    return createCommand(RequestType.ObjectRefCount, [key]);
}
