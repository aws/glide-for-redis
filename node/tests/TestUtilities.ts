/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { expect } from "@jest/globals";
import { exec } from "child_process";
import parseArgs from "minimist";
import { gte } from "semver";
import { v4 as uuidv4 } from "uuid";
import { TimeUnit } from "../src/Commands";
import {
    BaseClient,
    BaseClientConfiguration,
    BitFieldGet,
    BitFieldSet,
    BitOffset,
    BitOffsetMultiplier,
    BitmapIndexType,
    BitwiseOperation,
    ClusterTransaction,
    FlushMode,
    FunctionListResponse,
    FunctionStatsResponse,
    GeoUnit,
    GeospatialData,
    GlideClient,
    GlideClusterClient,
    InfScoreBoundary,
    InsertPosition,
    ListDirection,
    ProtocolVersion,
    ReturnType,
    ReturnTypeMap,
    ScoreFilter,
    SignedEncoding,
    SortOrder,
    Transaction,
    UnsignedEncoding,
} from "..";

/* eslint-disable @typescript-eslint/no-explicit-any */
function intoArrayInternal(obj: any, builder: Array<string>) {
    if (obj == null) {
        builder.push("null");
    } else if (typeof obj === "string") {
        builder.push(obj);
    } else if (typeof obj === "number") {
        builder.push(obj.toPrecision(3));
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
        const sortedArr = Array.from(obj.entries()).sort();

        for (const [key, value] of sortedArr) {
            intoArrayInternal(key, builder);
            intoArrayInternal(value, builder);
        }
    } else if (typeof obj[Symbol.iterator] === "function") {
        // iterable, recurse into children
        for (const item of obj) {
            intoArrayInternal(item, builder);
        }
    } else {
        const sortedArr = Array.from(Object.entries(obj)).sort();

        for (const [k, v] of sortedArr) {
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

// TODO use matcher instead of predicate
/** Check a multi-node response from a cluster. */
export function checkClusterMultiNodeResponse(
    res: object,
    predicate: (value: ReturnType) => void,
) {
    for (const nodeResponse of Object.values(res)) {
        predicate(nodeResponse);
    }
}

/** Check a response from a cluster. Response could be either single-node (value) or multi-node (string-value map). */
export function checkClusterResponse(
    res: object,
    singleNodeRoute: boolean,
    predicate: (value: ReturnType) => void,
) {
    if (singleNodeRoute) predicate(res as ReturnType);
    else checkClusterMultiNodeResponse(res, predicate);
}

/** Generate a String of LUA library code. */
export function generateLuaLibCode(
    libName: string,
    functions: Map<string, string>,
    readonly: boolean,
): string {
    let code = `#!lua name=${libName}\n`;

    for (const [functionName, functionBody] of functions) {
        code += `redis.register_function{ function_name = '${functionName}', callback = function(keys, args) ${functionBody} end`;
        if (readonly) code += ", flags = { 'no-writes' }";
        code += " }\n";
    }

    return code;
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

/**
 * Validate whether `FUNCTION LIST` response contains required info.
 *
 * @param response - The response from server.
 * @param libName - Expected library name.
 * @param functionDescriptions - Expected function descriptions. Key - function name, value - description.
 * @param functionFlags - Expected function flags. Key - function name, value - flags set.
 * @param libCode - Expected library to check if given.
 */
export function checkFunctionListResponse(
    response: FunctionListResponse,
    libName: string,
    functionDescriptions: Map<string, string | null>,
    functionFlags: Map<string, string[]>,
    libCode?: string,
) {
    // TODO rework after #1953 https://github.com/valkey-io/valkey-glide/pull/1953
    expect(response.length).toBeGreaterThan(0);
    let hasLib = false;

    for (const lib of response) {
        hasLib = lib["library_name"] == libName;

        if (hasLib) {
            const functions = lib["functions"];
            expect(functions.length).toEqual(functionDescriptions.size);

            for (const functionData of functions) {
                const functionInfo = functionData as Record<
                    string,
                    string | string[]
                >;
                const name = functionInfo["name"] as string;
                const flags = functionInfo["flags"] as string[];
                expect(functionInfo["description"]).toEqual(
                    functionDescriptions.get(name),
                );

                expect(flags).toEqual(functionFlags.get(name));
            }

            if (libCode) {
                expect(lib["library_code"]).toEqual(libCode);
            }

            break;
        }
    }

    expect(hasLib).toBeTruthy();
}

/**
 * Validate whether `FUNCTION STATS` response contains required info.
 *
 * @param response - The response from server.
 * @param runningFunction - Command line of running function expected. Empty, if nothing expected.
 * @param libCount - Expected libraries count.
 * @param functionCount - Expected functions count.
 */
export function checkFunctionStatsResponse(
    response: FunctionStatsResponse,
    runningFunction: string[],
    libCount: number,
    functionCount: number,
) {
    if (response.running_script === null && runningFunction.length > 0) {
        fail("No running function info");
    }

    if (response.running_script !== null && runningFunction.length == 0) {
        fail(
            "Unexpected running function info: " +
                (response.running_script.command as string[]).join(" "),
        );
    }

    if (response.running_script !== null) {
        expect(response.running_script.command).toEqual(runningFunction);
        // command line format is:
        // fcall|fcall_ro <function name> <num keys> <key>* <arg>*
        expect(response.running_script.name).toEqual(runningFunction[1]);
    }

    expect(response.engines).toEqual({
        LUA: { libraries_count: libCount, functions_count: functionCount },
    });
}

/**
 * Check transaction response.
 * @param response - Transaction result received from `exec` call.
 * @param expectedResponseData - Expected result data from {@link transactionTest}.
 */
export function validateTransactionResponse(
    response: ReturnType[] | null,
    expectedResponseData: [string, ReturnType][],
) {
    const failedChecks: string[] = [];

    for (let i = 0; i < expectedResponseData.length; i++) {
        const [testName, expectedResponse] = expectedResponseData[i];

        try {
            expect(response?.[i]).toEqual(expectedResponse);
        } catch {
            const expected =
                expectedResponse instanceof Map
                    ? JSON.stringify(Array.from(expectedResponse.entries()))
                    : JSON.stringify(expectedResponse);
            const actual =
                response?.[i] instanceof Map
                    ? JSON.stringify(
                          Array.from(
                              (response?.[i] as ReturnTypeMap)?.entries(),
                          ),
                      )
                    : JSON.stringify(response?.[i]);
            failedChecks.push(
                `${testName} failed, expected <${expected}>, actual <${actual}>`,
            );
        }
    }

    if (failedChecks.length > 0) {
        throw new Error(
            "Checks failed in transaction response:\n" +
                failedChecks.join("\n"),
        );
    }
}

/**
 * Populates a transaction with commands to test.
 * @param baseTransaction - A transaction.
 * @returns Array of tuples, where first element is a test name/description, second - expected return value.
 */
export async function transactionTest(
    baseTransaction: Transaction | ClusterTransaction,
    version: string,
): Promise<[string, ReturnType][]> {
    const key1 = "{key}" + uuidv4(); // string
    const key2 = "{key}" + uuidv4(); // string
    const key3 = "{key}" + uuidv4(); // string
    const key4 = "{key}" + uuidv4(); // hash
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
    const key15 = "{key}" + uuidv4(); // list
    const key16 = "{key}" + uuidv4(); // list
    const key17 = "{key}" + uuidv4(); // bitmap
    const key18 = "{key}" + uuidv4(); // Geospatial Data/ZSET
    const key19 = "{key}" + uuidv4(); // bitmap
    const key20 = "{key}" + uuidv4(); // list
    const key21 = "{key}" + uuidv4(); // list for sort
    const key22 = "{key}" + uuidv4(); // list for sort
    const key23 = "{key}" + uuidv4(); // zset random
    const key24 = "{key}" + uuidv4(); // list value
    const key25 = "{key}" + uuidv4(); // Geospatial Data/ZSET
    const field = uuidv4();
    const value = uuidv4();
    const groupName1 = uuidv4();
    const groupName2 = uuidv4();
    const consumer = uuidv4();
    // array of tuples - first element is test name/description, second - expected return value
    const responseData: [string, ReturnType][] = [];

    baseTransaction.publish("test_message", key1);
    responseData.push(['publish("test_message", key1)', 0]);
    baseTransaction.pubsubChannels();
    responseData.push(["pubsubChannels()", []]);
    baseTransaction.pubsubNumPat();
    responseData.push(["pubsubNumPat()", 0]);
    baseTransaction.pubsubNumSub();
    responseData.push(["pubsubNumSub()", {}]);

    baseTransaction.flushall();
    responseData.push(["flushall()", "OK"]);
    baseTransaction.flushall(FlushMode.SYNC);
    responseData.push(["flushall(FlushMode.SYNC)", "OK"]);
    baseTransaction.flushdb();
    responseData.push(["flushdb()", "OK"]);
    baseTransaction.flushdb(FlushMode.SYNC);
    responseData.push(["flushdb(FlushMode.SYNC)", "OK"]);
    baseTransaction.dbsize();
    responseData.push(["dbsize()", 0]);
    baseTransaction.set(key1, "bar");
    responseData.push(['set(key1, "bar")', "OK"]);

    if (gte(version, "6.2.0")) {
        baseTransaction.getex(key1);
        responseData.push(["getex(key1)", "bar"]);
        baseTransaction.getex(key1, { unit: TimeUnit.seconds, duration: 1 });
        responseData.push([
            'getex(key1, {expiry: { type: "seconds", count: 1 }})',
            "bar",
        ]);
    }

    baseTransaction.randomKey();
    responseData.push(["randomKey()", key1]);
    baseTransaction.getrange(key1, 0, -1);
    responseData.push(["getrange(key1, 0, -1)", "bar"]);
    baseTransaction.getdel(key1);
    responseData.push(["getdel(key1)", "bar"]);
    baseTransaction.set(key1, "bar");
    responseData.push(['set(key1, "bar")', "OK"]);
    baseTransaction.objectEncoding(key1);
    responseData.push(["objectEncoding(key1)", "embstr"]);
    baseTransaction.type(key1);
    responseData.push(["type(key1)", "string"]);
    baseTransaction.echo(value);
    responseData.push(["echo(value)", value]);
    baseTransaction.persist(key1);
    responseData.push(["persist(key1)", false]);

    if (gte(version, "7.0.0")) {
        baseTransaction.expireTime(key1);
        responseData.push(["expiretime(key1)", -1]);

        baseTransaction.pexpireTime(key1);
        responseData.push(["pexpiretime(key1)", -1]);
    }

    baseTransaction.set(key2, "baz", { returnOldValue: true });
    responseData.push(['set(key2, "baz", { returnOldValue: true })', null]);
    baseTransaction.customCommand(["MGET", key1, key2]);
    responseData.push(['customCommand(["MGET", key1, key2])', ["bar", "baz"]]);
    baseTransaction.mset({ [key3]: value });
    responseData.push(["mset({ [key3]: value })", "OK"]);
    baseTransaction.msetnx({ [key3]: value });
    responseData.push(["msetnx({ [key3]: value })", false]);
    baseTransaction.mget([key1, key2]);
    responseData.push(["mget([key1, key2])", ["bar", "baz"]]);
    baseTransaction.strlen(key1);
    responseData.push(["strlen(key1)", 3]);
    baseTransaction.setrange(key1, 0, "GLIDE");
    responseData.push(["setrange(key1, 0, 'GLIDE')", 5]);
    baseTransaction.del([key1]);
    responseData.push(["del([key1])", 1]);
    baseTransaction.append(key1, "bar");
    responseData.push(["append(key1, value)", 3]);
    baseTransaction.del([key1]);
    responseData.push(["del([key1])", 1]);
    baseTransaction.hset(key4, { [field]: value });
    responseData.push(["hset(key4, { [field]: value })", 1]);
    baseTransaction.hstrlen(key4, field);
    responseData.push(["hstrlen(key4, field)", value.length]);
    baseTransaction.hlen(key4);
    responseData.push(["hlen(key4)", 1]);
    baseTransaction.hrandfield(key4);
    responseData.push(["hrandfield(key4)", field]);
    baseTransaction.hrandfieldCount(key4, -2);
    responseData.push(["hrandfieldCount(key4, -2)", [field, field]]);
    baseTransaction.hrandfieldWithValues(key4, 2);
    responseData.push(["hrandfieldWithValues(key4, 2)", [[field, value]]]);
    baseTransaction.hsetnx(key4, field, value);
    responseData.push(["hsetnx(key4, field, value)", false]);
    baseTransaction.hvals(key4);
    responseData.push(["hvals(key4)", [value]]);
    baseTransaction.hget(key4, field);
    responseData.push(["hget(key4, field)", value]);
    baseTransaction.hgetall(key4);
    responseData.push(["hgetall(key4)", { [field]: value }]);
    baseTransaction.hdel(key4, [field]);
    responseData.push(["hdel(key4, [field])", 1]);
    baseTransaction.hmget(key4, [field]);
    responseData.push(["hmget(key4, [field])", [null]]);
    baseTransaction.hexists(key4, field);
    responseData.push(["hexists(key4, field)", false]);
    baseTransaction.hrandfield(key4);
    responseData.push(["hrandfield(key4)", null]);

    baseTransaction.lpush(key5, [
        field + "1",
        field + "2",
        field + "3",
        field + "4",
    ]);
    responseData.push(["lpush(key5, [1, 2, 3, 4])", 4]);

    if (gte("7.0.0", version)) {
        baseTransaction.lpush(key24, [field + "1", field + "2"]);
        responseData.push(["lpush(key22, [1, 2])", 2]);
        baseTransaction.lmpop([key24], ListDirection.LEFT);
        responseData.push([
            "lmpop([key22], ListDirection.LEFT)",
            { [key24]: [field + "2"] },
        ]);
        baseTransaction.lpush(key24, [field + "2"]);
        responseData.push(["lpush(key22, [2])", 2]);
        baseTransaction.blmpop([key24], ListDirection.LEFT, 0.1, 1);
        responseData.push([
            "blmpop([key22], ListDirection.LEFT, 0.1, 1)",
            { [key24]: [field + "2"] },
        ]);
    }

    baseTransaction.lpop(key5);
    responseData.push(["lpop(key5)", field + "4"]);
    baseTransaction.llen(key5);
    responseData.push(["llen(key5)", 3]);
    baseTransaction.lrem(key5, 1, field + "1");
    responseData.push(['lrem(key5, 1, field + "1")', 1]);
    baseTransaction.ltrim(key5, 0, 1);
    responseData.push(["ltrim(key5, 0, 1)", "OK"]);
    baseTransaction.lset(key5, 0, field + "3");
    responseData.push(['lset(key5, 0, field + "3")', "OK"]);
    baseTransaction.lrange(key5, 0, -1);
    responseData.push(["lrange(key5, 0, -1)", [field + "3", field + "2"]]);

    if (gte(version, "6.2.0")) {
        baseTransaction.lmove(
            key5,
            key20,
            ListDirection.LEFT,
            ListDirection.LEFT,
        );
        responseData.push([
            "lmove(key5, key20, ListDirection.LEFT, ListDirection.LEFT)",
            field + "3",
        ]);

        baseTransaction.blmove(
            key20,
            key5,
            ListDirection.LEFT,
            ListDirection.LEFT,
            3,
        );
        responseData.push([
            "blmove(key20, key5, ListDirection.LEFT, ListDirection.LEFT, 3)",
            field + "3",
        ]);
    }

    baseTransaction.lpopCount(key5, 2);
    responseData.push(["lpopCount(key5, 2)", [field + "3", field + "2"]]);

    baseTransaction.linsert(
        key5,
        InsertPosition.Before,
        "nonExistingPivot",
        "element",
    );
    responseData.push(["linsert", 0]);
    baseTransaction.rpush(key6, [field + "1", field + "2", field + "3"]);
    responseData.push([
        'rpush(key6, [field + "1", field + "2", field + "3"])',
        3,
    ]);
    baseTransaction.lindex(key6, 0);
    responseData.push(["lindex(key6, 0)", field + "1"]);
    baseTransaction.rpop(key6);
    responseData.push(["rpop(key6)", field + "3"]);
    baseTransaction.rpopCount(key6, 2);
    responseData.push(["rpopCount(key6, 2)", [field + "2", field + "1"]]);
    baseTransaction.rpushx(key15, ["_"]); // key15 is empty
    responseData.push(['rpushx(key15, ["_"])', 0]);
    baseTransaction.lpushx(key15, ["_"]);
    responseData.push(['lpushx(key15, ["_"])', 0]);
    baseTransaction.rpush(key16, [
        field + "1",
        field + "1",
        field + "2",
        field + "3",
        field + "3",
    ]);
    responseData.push(["rpush(key16, [1, 1, 2, 3, 3,])", 5]);
    baseTransaction.lpos(key16, field + "1", { rank: 2 });
    responseData.push(['lpos(key16, field + "1", { rank: 2 })', 1]);
    baseTransaction.lpos(key16, field + "1", { rank: 2, count: 0 });
    responseData.push(['lpos(key16, field + "1", { rank: 2, count: 0 })', [1]]);
    baseTransaction.sadd(key7, ["bar", "foo"]);
    responseData.push(['sadd(key7, ["bar", "foo"])', 2]);
    baseTransaction.sunionstore(key7, [key7, key7]);
    responseData.push(["sunionstore(key7, [key7, key7])", 2]);
    baseTransaction.sunion([key7, key7]);
    responseData.push(["sunion([key7, key7])", new Set(["bar", "foo"])]);
    baseTransaction.sinter([key7, key7]);
    responseData.push(["sinter([key7, key7])", new Set(["bar", "foo"])]);

    if (gte(version, "7.0.0")) {
        baseTransaction.sintercard([key7, key7]);
        responseData.push(["sintercard([key7, key7])", 2]);
        baseTransaction.sintercard([key7, key7], 1);
        responseData.push(["sintercard([key7, key7], 1)", 1]);
    }

    baseTransaction.sinterstore(key7, [key7, key7]);
    responseData.push(["sinterstore(key7, [key7, key7])", 2]);
    baseTransaction.sdiff([key7, key7]);
    responseData.push(["sdiff([key7, key7])", new Set()]);
    baseTransaction.sdiffstore(key7, [key7]);
    responseData.push(["sdiffstore(key7, [key7])", 2]);
    baseTransaction.srem(key7, ["foo"]);
    responseData.push(['srem(key7, ["foo"])', 1]);
    baseTransaction.scard(key7);
    responseData.push(["scard(key7)", 1]);
    baseTransaction.sismember(key7, "bar");
    responseData.push(['sismember(key7, "bar")', true]);

    if (gte(version, "6.2.0")) {
        baseTransaction.smismember(key7, ["bar", "foo", "baz"]);
        responseData.push([
            'smismember(key7, ["bar", "foo", "baz"])',
            [true, false, false],
        ]);
    }

    baseTransaction.smembers(key7);
    responseData.push(["smembers(key7)", new Set(["bar"])]);
    baseTransaction.srandmember(key7);
    responseData.push(["srandmember(key7)", "bar"]);
    baseTransaction.srandmemberCount(key7, 2);
    responseData.push(["srandmemberCount(key7, 2)", ["bar"]]);
    baseTransaction.srandmemberCount(key7, -2);
    responseData.push(["srandmemberCount(key7, -2)", ["bar", "bar"]]);
    baseTransaction.spop(key7);
    responseData.push(["spop(key7)", "bar"]);
    baseTransaction.spopCount(key7, 2);
    responseData.push(["spopCount(key7, 2)", new Set()]);
    baseTransaction.smove(key7, key7, "non_existing_member");
    responseData.push(['smove(key7, key7, "non_existing_member")', false]);
    baseTransaction.scard(key7);
    responseData.push(["scard(key7)", 0]);
    baseTransaction.zadd(key8, {
        member1: 1,
        member2: 2,
        member3: 3.5,
        member4: 4,
        member5: 5,
    });
    responseData.push(["zadd(key8, { ... } ", 5]);
    baseTransaction.zrank(key8, "member1");
    responseData.push(['zrank(key8, "member1")', 0]);

    if (gte(version, "7.2.0")) {
        baseTransaction.zrankWithScore(key8, "member1");
        responseData.push(['zrankWithScore(key8, "member1")', [0, 1]]);
    }

    baseTransaction.zrevrank(key8, "member5");
    responseData.push(['zrevrank(key8, "member5")', 0]);

    if (gte(version, "7.2.0")) {
        baseTransaction.zrevrankWithScore(key8, "member5");
        responseData.push(['zrevrankWithScore(key8, "member5")', [0, 5]]);
    }

    baseTransaction.zaddIncr(key8, "member2", 1);
    responseData.push(['zaddIncr(key8, "member2", 1)', 3]);
    baseTransaction.zincrby(key8, 0.3, "member1");
    responseData.push(['zincrby(key8, 0.3, "member1")', 1.3]);
    baseTransaction.zrem(key8, ["member1"]);
    responseData.push(['zrem(key8, ["member1"])', 1]);
    baseTransaction.zcard(key8);
    responseData.push(["zcard(key8)", 4]);

    baseTransaction.zscore(key8, "member2");
    responseData.push(['zscore(key8, "member2")', 3.0]);
    baseTransaction.zrange(key8, { start: 0, stop: -1 });
    responseData.push([
        "zrange(key8, { start: 0, stop: -1 })",
        ["member2", "member3", "member4", "member5"],
    ]);
    baseTransaction.zrangeWithScores(key8, { start: 0, stop: -1 });
    responseData.push([
        "zrangeWithScores(key8, { start: 0, stop: -1 })",
        { member2: 3, member3: 3.5, member4: 4, member5: 5 },
    ]);
    baseTransaction.zadd(key12, { one: 1, two: 2 });
    responseData.push(["zadd(key12, { one: 1, two: 2 })", 2]);
    baseTransaction.zscan(key12, "0");
    responseData.push(['zscan(key12, "0")', ["0", ["one", "1", "two", "2"]]]);
    baseTransaction.zscan(key12, "0", { match: "*", count: 20 });
    responseData.push([
        'zscan(key12, "0", {match: "*", count: 20})',
        ["0", ["one", "1", "two", "2"]],
    ]);
    baseTransaction.zadd(key13, { one: 1, two: 2, three: 3.5 });
    responseData.push(["zadd(key13, { one: 1, two: 2, three: 3.5 })", 3]);

    if (gte(version, "6.2.0")) {
        baseTransaction.zdiff([key13, key12]);
        responseData.push(["zdiff([key13, key12])", ["three"]]);
        baseTransaction.zdiffWithScores([key13, key12]);
        responseData.push(["zdiffWithScores([key13, key12])", { three: 3.5 }]);
        baseTransaction.zdiffstore(key13, [key13, key13]);
        responseData.push(["zdiffstore(key13, [key13, key13])", 0]);
        baseTransaction.zmscore(key12, ["two", "one"]);
        responseData.push(['zmscore(key12, ["two", "one"]', [2.0, 1.0]]);
        baseTransaction.zinterstore(key12, [key12, key13]);
        responseData.push(["zinterstore(key12, [key12, key13])", 0]);
    } else {
        baseTransaction.zinterstore(key12, [key12, key13]);
        responseData.push(["zinterstore(key12, [key12, key13])", 2]);
    }

    baseTransaction.zcount(
        key8,
        { value: 2 },
        InfScoreBoundary.PositiveInfinity,
    );
    responseData.push([
        "zcount(key8, { value: 2 }, InfScoreBoundary.PositiveInfinity)",
        4,
    ]);
    baseTransaction.zlexcount(
        key8,
        { value: "a" },
        InfScoreBoundary.PositiveInfinity,
    );
    responseData.push([
        'zlexcount(key8, { value: "a" }, InfScoreBoundary.PositiveInfinity)',
        4,
    ]);
    baseTransaction.zpopmin(key8);
    responseData.push(["zpopmin(key8)", { member2: 3.0 }]);
    baseTransaction.zpopmax(key8);
    responseData.push(["zpopmax(key8)", { member5: 5 }]);
    baseTransaction.zadd(key8, { member6: 6 });
    responseData.push(["zadd(key8, {member6: 6})", 1]);
    baseTransaction.bzpopmax([key8], 0.5);
    responseData.push(["bzpopmax([key8], 0.5)", [key8, "member6", 6]]);
    baseTransaction.zadd(key8, { member7: 1 });
    responseData.push(["zadd(key8, {member7: 1})", 1]);
    baseTransaction.bzpopmin([key8], 0.5);
    responseData.push(["bzpopmin([key8], 0.5)", [key8, "member7", 1]]);
    baseTransaction.zremRangeByRank(key8, 1, 1);
    responseData.push(["zremRangeByRank(key8, 1, 1)", 1]);
    baseTransaction.zremRangeByScore(
        key8,
        InfScoreBoundary.NegativeInfinity,
        InfScoreBoundary.PositiveInfinity,
    );
    responseData.push(["zremRangeByScore(key8, -Inf, +Inf)", 1]); // key8 is now empty
    baseTransaction.zremRangeByLex(
        key8,
        InfScoreBoundary.NegativeInfinity,
        InfScoreBoundary.PositiveInfinity,
    );
    responseData.push(["zremRangeByLex(key8, -Inf, +Inf)", 0]); // key8 is already empty

    if (gte(version, "7.0.0")) {
        baseTransaction.zadd(key14, { one: 1.0, two: 2.0 });
        responseData.push(["zadd(key14, { one: 1.0, two: 2.0 })", 2]);
        baseTransaction.zintercard([key8, key14]);
        responseData.push(["zintercard([key8, key14])", 0]);
        baseTransaction.zintercard([key8, key14], 1);
        responseData.push(["zintercard([key8, key14], 1)", 0]);
        baseTransaction.zmpop([key14], ScoreFilter.MAX);
        responseData.push(["zmpop([key14], MAX)", [key14, { two: 2.0 }]]);
        baseTransaction.zmpop([key14], ScoreFilter.MAX, 1);
        responseData.push(["zmpop([key14], MAX, 1)", [key14, { one: 1.0 }]]);
        baseTransaction.zadd(key14, { one: 1.0, two: 2.0 });
        responseData.push(["zadd(key14, { one: 1.0, two: 2.0 })", 2]);
        baseTransaction.bzmpop([key14], ScoreFilter.MAX, 0.1);
        responseData.push([
            "bzmpop([key14], ScoreFilter.MAX, 0.1)",
            [key14, { two: 2.0 }],
        ]);
        baseTransaction.bzmpop([key14], ScoreFilter.MAX, 0.1, 1);
        responseData.push([
            "bzmpop([key14], ScoreFilter.MAX, 0.1, 1)",
            [key14, { one: 1.0 }],
        ]);
    }

    baseTransaction.xadd(key9, [["field", "value1"]], { id: "0-1" });
    responseData.push([
        'xadd(key9, [["field", "value1"]], { id: "0-1" })',
        "0-1",
    ]);
    baseTransaction.xadd(key9, [["field", "value2"]], { id: "0-2" });
    responseData.push([
        'xadd(key9, [["field", "value2"]], { id: "0-2" })',
        "0-2",
    ]);
    baseTransaction.xadd(key9, [["field", "value3"]], { id: "0-3" });
    responseData.push([
        'xadd(key9, [["field", "value3"]], { id: "0-3" })',
        "0-3",
    ]);
    baseTransaction.xlen(key9);
    responseData.push(["xlen(key9)", 3]);
    baseTransaction.xread({ [key9]: "0-1" });
    responseData.push([
        'xread({ [key9]: "0-1" })',
        {
            [key9]: {
                "0-2": [["field", "value2"]],
                "0-3": [["field", "value3"]],
            },
        },
    ]);
    baseTransaction.xtrim(key9, {
        method: "minid",
        threshold: "0-2",
        exact: true,
    });
    responseData.push([
        'xtrim(key9, { method: "minid", threshold: "0-2", exact: true }',
        1,
    ]);
    baseTransaction.xgroupCreate(key9, groupName1, "0-0");
    responseData.push(['xgroupCreate(key9, groupName1, "0-0")', "OK"]);
    baseTransaction.xgroupCreate(key9, groupName2, "0-0", { mkStream: true });
    responseData.push([
        'xgroupCreate(key9, groupName2, "0-0", { mkStream: true })',
        "OK",
    ]);
    baseTransaction.xinfoConsumers(key9, groupName1);
    responseData.push(["xinfoConsumers(key9, groupName1)", []]);
    baseTransaction.xdel(key9, ["0-3", "0-5"]);
    responseData.push(["xdel(key9, [['0-3', '0-5']])", 1]);

    // key9 has one entry here: {"0-2":[["field","value2"]]}

    baseTransaction.xgroupCreateConsumer(key9, groupName1, "consumer1");
    responseData.push([
        "xgroupCreateConsumer(key9, groupName1, consumer1)",
        true,
    ]);
    baseTransaction.customCommand([
        "xreadgroup",
        "group",
        groupName1,
        "consumer1",
        "STREAMS",
        key9,
        ">",
    ]);
    responseData.push([
        'xreadgroup(groupName1, "consumer1", key9, >)',
        { [key9]: { "0-2": [["field", "value2"]] } },
    ]);
    baseTransaction.xclaim(key9, groupName1, "consumer1", 0, ["0-2"]);
    responseData.push([
        'xclaim(key9, groupName1, "consumer1", 0, ["0-2"])',
        { "0-2": [["field", "value2"]] },
    ]);
    baseTransaction.xclaim(key9, groupName1, "consumer1", 0, ["0-2"], {
        isForce: true,
        retryCount: 0,
        idle: 0,
    });
    responseData.push([
        'xclaim(key9, groupName1, "consumer1", 0, ["0-2"], { isForce: true, retryCount: 0, idle: 0})',
        { "0-2": [["field", "value2"]] },
    ]);
    baseTransaction.xclaimJustId(key9, groupName1, "consumer1", 0, ["0-2"]);
    responseData.push([
        'xclaimJustId(key9, groupName1, "consumer1", 0, ["0-2"])',
        ["0-2"],
    ]);
    baseTransaction.xclaimJustId(key9, groupName1, "consumer1", 0, ["0-2"], {
        isForce: true,
        retryCount: 0,
        idle: 0,
    });
    responseData.push([
        'xclaimJustId(key9, groupName1, "consumer1", 0, ["0-2"], { isForce: true, retryCount: 0, idle: 0})',
        ["0-2"],
    ]);
    baseTransaction.xgroupCreateConsumer(key9, groupName1, consumer);
    responseData.push([
        "xgroupCreateConsumer(key9, groupName1, consumer)",
        true,
    ]);
    baseTransaction.xgroupDelConsumer(key9, groupName1, consumer);
    responseData.push(["xgroupDelConsumer(key9, groupName1, consumer)", 0]);
    baseTransaction.xgroupDestroy(key9, groupName1);
    responseData.push(["xgroupDestroy(key9, groupName1)", true]);
    baseTransaction.xgroupDestroy(key9, groupName2);
    responseData.push(["xgroupDestroy(key9, groupName2)", true]);

    baseTransaction.rename(key9, key10);
    responseData.push(["rename(key9, key10)", "OK"]);
    baseTransaction.exists([key10]);
    responseData.push(["exists([key10])", 1]);
    baseTransaction.touch([key10]);
    responseData.push(["touch([key10])", 1]);
    baseTransaction.renamenx(key10, key9);
    responseData.push(["renamenx(key10, key9)", true]);
    baseTransaction.exists([key9, key10]);
    responseData.push(["exists([key9, key10])", 1]);
    baseTransaction.rpush(key6, [field + "1", field + "2", field + "3"]);
    responseData.push([
        'rpush(key6, [field + "1", field + "2", field + "3"])',
        3,
    ]);
    baseTransaction.brpop([key6], 0.1);
    responseData.push(["brpop([key6], 0.1)", [key6, field + "3"]]);
    baseTransaction.blpop([key6], 0.1);
    responseData.push(["blpop([key6], 0.1)", [key6, field + "1"]]);

    baseTransaction.setbit(key17, 1, 1);
    responseData.push(["setbit(key17, 1, 1)", 0]);
    baseTransaction.getbit(key17, 1);
    responseData.push(["getbit(key17, 1)", 1]);
    baseTransaction.set(key17, "foobar");
    responseData.push(['set(key17, "foobar")', "OK"]);
    baseTransaction.bitcount(key17);
    responseData.push(["bitcount(key17)", 26]);
    baseTransaction.bitcount(key17, { start: 1, end: 1 });
    responseData.push(["bitcount(key17, { start: 1, end: 1 })", 6]);
    baseTransaction.bitpos(key17, 1);
    responseData.push(["bitpos(key17, 1)", 1]);

    if (gte("6.0.0", version)) {
        baseTransaction.bitfieldReadOnly(key17, [
            new BitFieldGet(new SignedEncoding(5), new BitOffset(3)),
        ]);
        responseData.push([
            "bitfieldReadOnly(key17, [new BitFieldGet(...)])",
            [6],
        ]);
    }

    baseTransaction.set(key19, "abcdef");
    responseData.push(['set(key19, "abcdef")', "OK"]);
    baseTransaction.bitop(BitwiseOperation.AND, key19, [key19, key17]);
    responseData.push([
        "bitop(BitwiseOperation.AND, key19, [key19, key17])",
        6,
    ]);
    baseTransaction.get(key19);
    responseData.push(["get(key19)", "`bc`ab"]);

    if (gte(version, "7.0.0")) {
        baseTransaction.bitcount(key17, {
            start: 5,
            end: 30,
            indexType: BitmapIndexType.BIT,
        });
        responseData.push([
            "bitcount(key17, new BitOffsetOptions(5, 30, BitmapIndexType.BIT))",
            17,
        ]);
        baseTransaction.bitposInterval(key17, 1, 44, 50, BitmapIndexType.BIT);
        responseData.push([
            "bitposInterval(key17, 1, 44, 50, BitmapIndexType.BIT)",
            46,
        ]);
    }

    baseTransaction.bitfield(key17, [
        new BitFieldSet(
            new UnsignedEncoding(10),
            new BitOffsetMultiplier(3),
            4,
        ),
    ]);
    responseData.push(["bitfield(key17, [new BitFieldSet(...)])", [609]]);

    baseTransaction.pfadd(key11, ["a", "b", "c"]);
    responseData.push(['pfadd(key11, ["a", "b", "c"])', 1]);
    baseTransaction.pfmerge(key11, []);
    responseData.push(["pfmerge(key11, [])", "OK"]);
    baseTransaction.pfcount([key11]);
    responseData.push(["pfcount([key11])", 3]);
    baseTransaction.geoadd(
        key18,
        new Map<string, GeospatialData>([
            ["Palermo", { longitude: 13.361389, latitude: 38.115556 }],
            ["Catania", { longitude: 15.087269, latitude: 37.502669 }],
        ]),
    );
    responseData.push(["geoadd(key18, { Palermo: ..., Catania: ... })", 2]);
    baseTransaction.geopos(key18, ["Palermo", "Catania"]);
    responseData.push([
        'geopos(key18, ["Palermo", "Catania"])',
        [
            [13.36138933897018433, 38.11555639549629859],
            [15.08726745843887329, 37.50266842333162032],
        ],
    ]);
    baseTransaction.geodist(key18, "Palermo", "Catania");
    responseData.push(['geodist(key18, "Palermo", "Catania")', 166274.1516]);
    baseTransaction.geodist(key18, "Palermo", "Catania", GeoUnit.KILOMETERS);
    responseData.push([
        'geodist(key18, "Palermo", "Catania", GeoUnit.KILOMETERS)',
        166.2742,
    ]);
    baseTransaction.geohash(key18, ["Palermo", "Catania", "NonExisting"]);
    responseData.push([
        'geohash(key18, ["Palermo", "Catania", "NonExisting"])',
        ["sqc8b49rny0", "sqdtr74hyu0", null],
    ]);
    baseTransaction.zadd(key23, { one: 1.0 });
    responseData.push(["zadd(key23, {one: 1.0}", 1]);
    baseTransaction.zrandmember(key23);
    responseData.push(["zrandmember(key23)", "one"]);
    baseTransaction.zrandmemberWithCount(key23, 1);
    responseData.push(["zrandmemberWithCount(key23, 1)", ["one"]]);
    baseTransaction.zrandmemberWithCountWithScores(key23, 1);
    responseData.push([
        "zrandmemberWithCountWithScores(key23, 1)",
        [["one", 1.0]],
    ]);

    if (gte(version, "6.2.0")) {
        baseTransaction
            .geosearch(
                key18,
                { member: "Palermo" },
                { radius: 200, unit: GeoUnit.KILOMETERS },
                { sortOrder: SortOrder.ASC },
            )
            .geosearch(
                key18,
                { position: { longitude: 15, latitude: 37 } },
                { width: 400, height: 400, unit: GeoUnit.KILOMETERS },
            )
            .geosearch(
                key18,
                { member: "Palermo" },
                { radius: 200, unit: GeoUnit.KILOMETERS },
                {
                    sortOrder: SortOrder.ASC,
                    count: 2,
                    withCoord: true,
                    withDist: true,
                    withHash: true,
                },
            )
            .geosearch(
                key18,
                { position: { longitude: 15, latitude: 37 } },
                { width: 400, height: 400, unit: GeoUnit.KILOMETERS },
                {
                    sortOrder: SortOrder.ASC,
                    count: 2,
                    withCoord: true,
                    withDist: true,
                    withHash: true,
                },
            );
        responseData.push([
            'geosearch(key18, "Palermo", R200 KM, ASC)',
            ["Palermo", "Catania"],
        ]);
        responseData.push([
            "geosearch(key18, (15, 37), 400x400 KM, ASC)",
            ["Palermo", "Catania"],
        ]);
        responseData.push([
            'geosearch(key18, "Palermo", R200 KM, ASC 2 3x true)',
            [
                [
                    "Palermo",
                    [
                        0.0,
                        3479099956230698,
                        [13.361389338970184, 38.1155563954963],
                    ],
                ],
                [
                    "Catania",
                    [
                        166.2742,
                        3479447370796909,
                        [15.087267458438873, 37.50266842333162],
                    ],
                ],
            ],
        ]);
        responseData.push([
            "geosearch(key18, (15, 37), 400x400 KM, ASC 2 3x true)",
            [
                [
                    "Catania",
                    [
                        56.4413,
                        3479447370796909,
                        [15.087267458438873, 37.50266842333162],
                    ],
                ],
                [
                    "Palermo",
                    [
                        190.4424,
                        3479099956230698,
                        [13.361389338970184, 38.1155563954963],
                    ],
                ],
            ],
        ]);

        baseTransaction.geosearchstore(
            key25,
            key18,
            { position: { longitude: 15, latitude: 37 } },
            { width: 400, height: 400, unit: GeoUnit.KILOMETERS },
        );
        responseData.push([
            "geosearchstore(key25, key18, (15, 37), 400x400 KM)",
            2,
        ]);
    }

    const libName = "mylib1C" + uuidv4().replaceAll("-", "");
    const funcName = "myfunc1c" + uuidv4().replaceAll("-", "");
    const code = generateLuaLibCode(
        libName,
        new Map([[funcName, "return args[1]"]]),
        true,
    );

    if (gte(version, "7.0.0")) {
        baseTransaction.functionFlush();
        responseData.push(["functionFlush()", "OK"]);
        baseTransaction.functionLoad(code);
        responseData.push(["functionLoad(code)", libName]);
        baseTransaction.functionLoad(code, true);
        responseData.push(["functionLoad(code, true)", libName]);
        baseTransaction.functionList({ libNamePattern: "another" });
        responseData.push(['functionList("another")', []]);
        baseTransaction.fcall(funcName, [], ["one", "two"]);
        responseData.push(['fcall(funcName, [], ["one", "two"])', "one"]);
        baseTransaction.fcallReadonly(funcName, [], ["one", "two"]);
        responseData.push([
            'fcallReadonly(funcName, [], ["one", "two"]',
            "one",
        ]);
        baseTransaction.functionStats();
        responseData.push([
            "functionStats()",
            {
                running_script: null,
                engines: { LUA: { libraries_count: 1, functions_count: 1 } },
            },
        ]);
        baseTransaction.functionDelete(libName);
        responseData.push(["functionDelete(libName)", "OK"]);
        baseTransaction.functionFlush();
        responseData.push(["functionFlush()", "OK"]);
        baseTransaction.functionFlush(FlushMode.ASYNC);
        responseData.push(["functionFlush(FlushMode.ASYNC)", "OK"]);
        baseTransaction.functionFlush(FlushMode.SYNC);
        responseData.push(["functionFlush(FlushMode.SYNC)", "OK"]);
        baseTransaction.functionList({
            libNamePattern: libName,
            withCode: true,
        });
        responseData.push(["functionList({ libName, true})", []]);

        baseTransaction
            .mset({ [key1]: "abcd", [key2]: "bcde", [key3]: "wxyz" })
            .lcs(key1, key2)
            .lcs(key1, key3)
            .lcsLen(key1, key2)
            .lcsLen(key1, key3)
            .lcsIdx(key1, key2)
            .lcsIdx(key1, key2, { minMatchLen: 1 })
            .lcsIdx(key1, key2, { withMatchLen: true })
            .lcsIdx(key1, key2, { withMatchLen: true, minMatchLen: 1 })
            .del([key1, key2, key3]);

        responseData.push(
            ['mset({[key1]: "abcd", [key2]: "bcde", [key3]: "wxyz"})', "OK"],
            ["lcs(key1, key2)", "bcd"],
            ["lcs(key1, key3)", ""],
            ["lcsLen(key1, key2)", 3],
            ["lcsLen(key1, key3)", 0],
            [
                "lcsIdx(key1, key2)",
                {
                    matches: [
                        [
                            [1, 3],
                            [0, 2],
                        ],
                    ],
                    len: 3,
                },
            ],
            [
                "lcsIdx(key1, key2, {minMatchLen: 1})",
                {
                    matches: [
                        [
                            [1, 3],
                            [0, 2],
                        ],
                    ],
                    len: 3,
                },
            ],
            [
                "lcsIdx(key1, key2, {withMatchLen: true})",
                { matches: [[[1, 3], [0, 2], 3]], len: 3 },
            ],
            [
                "lcsIdx(key1, key2, {withMatchLen: true, minMatchLen: 1})",
                { matches: [[[1, 3], [0, 2], 3]], len: 3 },
            ],
            ["del([key1, key2, key3])", 3],
        );
    }

    baseTransaction
        .lpush(key21, ["3", "1", "2"])
        .sort(key21)
        .sortStore(key21, key22)
        .lrange(key22, 0, -1);
    responseData.push(
        ['lpush(key21, ["3", "1", "2"])', 3],
        ["sort(key21)", ["1", "2", "3"]],
        ["sortStore(key21, key22)", 3],
        ["lrange(key22, 0, -1)", ["1", "2", "3"]],
    );

    if (gte(version, "7.0.0")) {
        baseTransaction.sortReadOnly(key21);
        responseData.push(["sortReadOnly(key21)", ["1", "2", "3"]]);
    }

    return responseData;
}
