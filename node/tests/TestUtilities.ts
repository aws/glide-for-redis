/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { beforeAll, expect } from "@jest/globals";
import { exec } from "child_process";
import parseArgs from "minimist";
import { v4 as uuidv4 } from "uuid";
import {
    BaseClient,
    BaseClientConfiguration,
    ClusterTransaction,
    GlideClient,
    GlideClusterClient,
    InsertPosition,
    Logger,
    ProtocolVersion,
    ReturnType,
    Transaction,
} from "..";
import { checkIfServerVersionLessThan } from "./SharedTests";

beforeAll(() => {
    Logger.init("info");
});

/* eslint-disable @typescript-eslint/no-explicit-any */
function intoArrayInternal(obj: any, builder: Array<string>) {
    if (obj == null) {
        builder.push("null");
    } else if (typeof obj === "string") {
        builder.push(obj);
    } else if (obj instanceof Uint8Array) {
        builder.push(obj.toString());
    } else if (obj instanceof Array) {
        for (const item of obj) {
            intoArrayInternal(item, builder);
        }
    } else if (obj instanceof Set) {
        const arr = Array.from(obj);
        arr.sort();

        for (const item of arr) {
            intoArrayInternal(item, builder);
        }
    } else if (obj instanceof Map) {
        for (const [key, value] of obj) {
            intoArrayInternal(key, builder);
            intoArrayInternal(value, builder);
        }
    } else if (typeof obj[Symbol.iterator] === "function") {
        // iterable, recurse into children
        for (const item of obj) {
            intoArrayInternal(item, builder);
        }
    } else {
        for (const [k, v] of Object.entries(obj)) {
            intoArrayInternal(k, builder);
            intoArrayInternal(v, builder);
        }
    }
}

/**
 * accept any variable `v` and convert it into String, recursively
 */
export function intoString(v: any): string {
    const builder: Array<string> = [];
    intoArrayInternal(v, builder);
    return builder.join("");
}

/**
 * accept any variable `v` and convert it into array of string
 */
export function intoArray(v: any): Array<string> {
    const result: Array<string> = [];
    intoArrayInternal(v, result);
    return result;
}

/**
 * Convert array of strings into array of `Uint8Array`
 */
export function convertStringArrayToBuffer(value: string[]): Uint8Array[] {
    const bytesarr: Uint8Array[] = [];

    for (const str of value) {
        bytesarr.push(Buffer.from(str));
    }

    return bytesarr;
}

export class Checker {
    left: string;

    constructor(left: any) {
        this.left = intoString(left);
    }

    toEqual(right: any) {
        right = intoString(right);
        return expect(this.left).toEqual(right);
    }
}

export function checkSimple(left: any): Checker {
    return new Checker(left);
}

export type Client = {
    set: (key: string, value: string) => Promise<string | "OK" | null>;
    get: (key: string) => Promise<string | null>;
};

export async function GetAndSetRandomValue(client: Client) {
    const key = uuidv4();
    // Adding random repetition, to prevent the inputs from always having the same alignment.
    const value = uuidv4() + "0".repeat(Math.random() * 7);
    const setResult = await client.set(key, value);
    expect(intoString(setResult)).toEqual("OK");
    const result = await client.get(key);
    expect(intoString(result)).toEqual(value);
}

export function flushallOnPort(port: number): Promise<void> {
    return new Promise<void>((resolve, reject) =>
        exec(`redis-cli -p ${port} FLUSHALL`, (error, _, stderr) => {
            if (error) {
                console.error(stderr);
                reject(error);
            } else {
                resolve();
            }
        }),
    );
}

/**
 * Parses a string of endpoints into an array of host-port pairs.
 * Each endpoint in the string should be in the format 'host:port', separated by commas.
 *
 * @param endpointsStr - The string containing endpoints in the format 'host:port', separated by commas.
 * @returns An array of host-port pairs parsed from the endpoints string.
 * @throws If the endpoints string or its format is invalid.
 * @example
 * ```typescript
 * const endpointsStr = 'localhost:8080,example.com:3000,192.168.1.100:5000';
 * const endpoints = parseEndpoints(endpointsStr);
 * console.log(endpoints);
 * // Output: [['localhost', 8080], ['example.com', 3000], ['192.168.1.100', 5000]]
 * ```
 */
export const parseEndpoints = (endpointsStr: string): [string, number][] => {
    try {
        console.log(endpointsStr);
        const endpoints: string[][] = endpointsStr
            .split(",")
            .map((endpoint) => endpoint.split(":"));
        endpoints.forEach((endpoint) => {
            // Check if each endpoint has exactly two elements (host and port)
            if (endpoint.length !== 2) {
                throw new Error(
                    "Each endpoint should be in the format 'host:port'.\nEndpoints should be separated by commas.",
                );
            }

            // Extract host and port
            const [host, portStr] = endpoint;

            // Check if both host and port are specified and if port is a valid integer
            if (!host || !portStr || !/^\d+$/.test(portStr)) {
                throw new Error(
                    "Both host and port should be specified and port should be a valid integer.",
                );
            }
        });

        // Convert port strings to numbers and return the result
        return endpoints.map(([host, portStr]) => [host, Number(portStr)]);
    } catch (error) {
        throw new Error(
            "Invalid endpoints format: " + (error as Error).message,
        );
    }
};

/// This function takes the first result of the response if it got more than one response (like cluster responses).
export function getFirstResult(
    res: string | number | Record<string, string> | Record<string, number>,
): string | number {
    if (typeof res == "string" || typeof res == "number") {
        return res;
    }

    return Object.values(res).at(0);
}

/**
 * Parses the command-line arguments passed to the Node.js process.
 *
 * @returns Parsed command-line arguments.
 *
 * @example
 * ```typescript
 * // Command: node script.js --name="John Doe" --age=30
 * const args = parseCommandLineArgs();
 * // args = { name: 'John Doe', age: 30 }
 * ```
 */
export function parseCommandLineArgs() {
    return parseArgs(process.argv.slice(2));
}

export async function testTeardown(
    cluster_mode: boolean,
    option: BaseClientConfiguration,
) {
    const client = cluster_mode
        ? await GlideClusterClient.createClient(option)
        : await GlideClient.createClient(option);

    await client.customCommand(["FLUSHALL"]);
    client.close();
}

export const getClientConfigurationOption = (
    addresses: [string, number][],
    protocol: ProtocolVersion,
    timeout?: number,
): BaseClientConfiguration => {
    return {
        addresses: addresses.map(([host, port]) => ({
            host,
            port,
        })),
        protocol,
        ...(timeout && { requestTimeout: timeout }),
    };
};

export async function flushAndCloseClient(
    cluster_mode: boolean,
    addresses: [string, number][],
    client?: BaseClient,
) {
    await testTeardown(
        cluster_mode,
        getClientConfigurationOption(addresses, ProtocolVersion.RESP3, 2000),
    );

    // some tests don't initialize a client
    if (client == undefined) {
        return;
    }

    client.close();
}

/**
 * Compare two maps by converting them to JSON strings and checking for equality, including property order.
 *
 * @param map - The first map to compare.
 * @param map2 - The second map to compare.
 * @returns True if the maps are equal.
 * @remarks This function is used to compare maps, including their property order.
 * Direct comparison with `expect(map).toEqual(map2)` might ignore the order of properties,
 * whereas this function considers property order in the comparison by converting the maps to JSON strings.
 * This ensures a stricter comparison that takes property order into account.
 *
 * @example
 * ```typescript
 * const mapA = { name: 'John', age: 30 };
 * const mapB = { age: 30, name: 'John' };
 *
 * // Direct comparison will pass because it ignores property order
 * expect(mapA).toEqual(mapB); // This will pass
 *
 * // Correct comparison using compareMaps function
 * compareMaps(mapA, mapB); // This will return false due to different property order
 * ```
 */
export function compareMaps(
    map: Record<string, unknown>,
    map2: Record<string, unknown>,
): boolean {
    return JSON.stringify(map) == JSON.stringify(map2);
}

export async function transactionTest(
    baseTransaction: Transaction | ClusterTransaction,
): Promise<ReturnType[]> {
    const key1 = "{key}" + uuidv4();
    const key2 = "{key}" + uuidv4();
    const key3 = "{key}" + uuidv4();
    const key4 = "{key}" + uuidv4();
    const key5 = "{key}" + uuidv4();
    const key6 = "{key}" + uuidv4();
    const key7 = "{key}" + uuidv4();
    const key8 = "{key}" + uuidv4();
    const key9 = "{key}" + uuidv4();
    const key10 = "{key}" + uuidv4();
    const key11 = "{key}" + uuidv4(); // hyper log log
    const key12 = "{key}" + uuidv4();
    const key13 = "{key}" + uuidv4();
    const key14 = "{key}" + uuidv4(); // sorted set
    const field = uuidv4();
    const value = uuidv4();
    const args: ReturnType[] = [];
    baseTransaction.set(key1, "bar");
    args.push("OK");
    baseTransaction.objectEncoding(key1);
    args.push("embstr");
    baseTransaction.type(key1);
    args.push("string");
    baseTransaction.echo(value);
    args.push(value);
    baseTransaction.persist(key1);
    args.push(false);
    baseTransaction.set(key2, "baz", {
        returnOldValue: true,
    });
    args.push(null);
    baseTransaction.customCommand(["MGET", key1, key2]);
    args.push(["bar", "baz"]);
    baseTransaction.mset({ [key3]: value });
    args.push("OK");
    baseTransaction.mget([key1, key2]);
    args.push(["bar", "baz"]);
    baseTransaction.strlen(key1);
    args.push(3);
    baseTransaction.del([key1]);
    args.push(1);
    baseTransaction.hset(key4, { [field]: value });
    args.push(1);
    baseTransaction.hlen(key4);
    args.push(1);
    baseTransaction.hsetnx(key4, field, value);
    args.push(false);
    baseTransaction.hvals(key4);
    args.push([value]);
    baseTransaction.hget(key4, field);
    args.push(value);
    baseTransaction.hgetall(key4);
    args.push({ [field]: value });
    baseTransaction.hdel(key4, [field]);
    args.push(1);
    baseTransaction.hmget(key4, [field]);
    args.push([null]);
    baseTransaction.hexists(key4, field);
    args.push(false);
    baseTransaction.lpush(key5, [
        field + "1",
        field + "2",
        field + "3",
        field + "4",
    ]);
    args.push(4);
    baseTransaction.lpop(key5);
    args.push(field + "4");
    baseTransaction.llen(key5);
    args.push(3);
    baseTransaction.lrem(key5, 1, field + "1");
    args.push(1);
    baseTransaction.ltrim(key5, 0, 1);
    args.push("OK");
    baseTransaction.lrange(key5, 0, -1);
    args.push([field + "3", field + "2"]);
    baseTransaction.lpopCount(key5, 2);
    args.push([field + "3", field + "2"]);
    baseTransaction.linsert(
        key5,
        InsertPosition.Before,
        "nonExistingPivot",
        "element",
    );
    args.push(0);
    baseTransaction.rpush(key6, [field + "1", field + "2", field + "3"]);
    args.push(3);
    baseTransaction.lindex(key6, 0);
    args.push(field + "1");
    baseTransaction.rpop(key6);
    args.push(field + "3");
    baseTransaction.rpopCount(key6, 2);
    args.push([field + "2", field + "1"]);
    baseTransaction.sadd(key7, ["bar", "foo"]);
    args.push(2);
    baseTransaction.sunionstore(key7, [key7, key7]);
    args.push(2);
    baseTransaction.sinter([key7, key7]);
    args.push(new Set(["bar", "foo"]));
    baseTransaction.srem(key7, ["foo"]);
    args.push(1);
    baseTransaction.scard(key7);
    args.push(1);
    baseTransaction.sismember(key7, "bar");
    args.push(true);
    baseTransaction.smembers(key7);
    args.push(new Set(["bar"]));
    baseTransaction.spop(key7);
    args.push("bar");
    baseTransaction.spopCount(key7, 2);
    args.push(new Set());
    baseTransaction.smove(key7, key7, "non_existing_member");
    args.push(false);
    baseTransaction.scard(key7);
    args.push(0);
    baseTransaction.zadd(key8, {
        member1: 1,
        member2: 2,
        member3: 3.5,
        member4: 4,
        member5: 5,
    });
    args.push(5);
    baseTransaction.zrank(key8, "member1");
    args.push(0);

    if (!(await checkIfServerVersionLessThan("7.2.0"))) {
        baseTransaction.zrankWithScore(key8, "member1");
        args.push([0, 1]);
    }

    baseTransaction.zaddIncr(key8, "member2", 1);
    args.push(3);
    baseTransaction.zrem(key8, ["member1"]);
    args.push(1);
    baseTransaction.zcard(key8);
    args.push(4);
    baseTransaction.zscore(key8, "member2");
    args.push(3.0);
    baseTransaction.zrange(key8, { start: 0, stop: -1 });
    args.push(["member2", "member3", "member4", "member5"]);
    baseTransaction.zrangeWithScores(key8, { start: 0, stop: -1 });
    args.push({ member2: 3, member3: 3.5, member4: 4, member5: 5 });
    baseTransaction.zadd(key12, { one: 1, two: 2 });
    args.push(2);
    baseTransaction.zadd(key13, { one: 1, two: 2, tree: 3.5 });
    args.push(3);
    baseTransaction.zinterstore(key12, [key12, key13]);
    args.push(2);
    baseTransaction.zcount(key8, { value: 2 }, "positiveInfinity");
    args.push(4);
    baseTransaction.zpopmin(key8);
    args.push({ member2: 3.0 });
    baseTransaction.zpopmax(key8);
    args.push({ member5: 5 });
    baseTransaction.zremRangeByRank(key8, 1, 1);
    args.push(1);
    baseTransaction.zremRangeByScore(
        key8,
        "negativeInfinity",
        "positiveInfinity",
    );
    args.push(1); // key8 is now empty

    if (!(await checkIfServerVersionLessThan("7.0.0"))) {
        baseTransaction.zadd(key14, { one: 1.0, two: 2.0 });
        args.push(2);
        baseTransaction.zintercard([key8, key14]);
        args.push(0);
        baseTransaction.zintercard([key8, key14], 1);
        args.push(0);
    }

    baseTransaction.xadd(key9, [["field", "value1"]], { id: "0-1" });
    args.push("0-1");
    baseTransaction.xadd(key9, [["field", "value2"]], { id: "0-2" });
    args.push("0-2");
    baseTransaction.xadd(key9, [["field", "value3"]], { id: "0-3" });
    args.push("0-3");
    baseTransaction.xlen(key9);
    args.push(3);
    baseTransaction.xread({ [key9]: "0-1" });
    args.push({
        [key9]: {
            "0-2": [["field", "value2"]],
            "0-3": [["field", "value3"]],
        },
    });
    baseTransaction.xtrim(key9, {
        method: "minid",
        threshold: "0-2",
        exact: true,
    });
    args.push(1);
    baseTransaction.rename(key9, key10);
    args.push("OK");
    baseTransaction.exists([key10]);
    args.push(1);
    baseTransaction.renamenx(key10, key9);
    args.push(true);
    baseTransaction.exists([key9, key10]);
    args.push(1);
    baseTransaction.rpush(key6, [field + "1", field + "2", field + "3"]);
    args.push(3);
    baseTransaction.brpop([key6], 0.1);
    args.push([key6, field + "3"]);
    baseTransaction.blpop([key6], 0.1);
    args.push([key6, field + "1"]);
    baseTransaction.pfadd(key11, ["a", "b", "c"]);
    args.push(1);
    baseTransaction.pfcount([key11]);
    args.push(3);
    return args;
}
