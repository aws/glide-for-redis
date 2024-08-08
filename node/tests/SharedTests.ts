/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

// This file contains tests common for standalone and cluster clients, it covers API defined in
// BaseClient.ts - commands which manipulate with keys.
// Each test cases has access to a client instance and, optionally, to a cluster - object, which
// represents a running server instance. See first 2 test cases as examples.

import { expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import {
    BitFieldGet,
    BitFieldIncrBy,
    BitFieldOverflow,
    BitFieldSet,
    BitOffset,
    BitOffsetMultiplier,
    BitOverflowControl,
    BitmapIndexType,
    BitwiseOperation,
    ClosingError,
    ClusterTransaction,
    ConditionalChange,
    ExpireOptions,
    FlushMode,
    GeoUnit,
    GeospatialData,
    GlideClient,
    GlideClusterClient,
    InfScoreBoundary,
    InfoOptions,
    InsertPosition,
    ListDirection,
    ProtocolVersion,
    RequestError,
    ScoreFilter,
    Script,
    SignedEncoding,
    SortOrder,
    Transaction,
    UnsignedEncoding,
    UpdateByScore,
    parseInfoResponse,
} from "../";
import { RedisCluster } from "../../utils/TestUtils";
import { SingleNodeRoute } from "../build-ts/src/GlideClusterClient";
import {
    Client,
    GetAndSetRandomValue,
    compareMaps,
    getFirstResult,
} from "./TestUtilities";

export type BaseClient = GlideClient | GlideClusterClient;

export function runBaseTests<Context>(config: {
    init: (
        protocol: ProtocolVersion,
        clientName?: string,
    ) => Promise<{
        context: Context;
        client: BaseClient;
        cluster: RedisCluster;
    }>;
    close: (context: Context, testSucceeded: boolean) => void;
    timeout?: number;
}) {
    runCommonTests({
        init: () => config.init(ProtocolVersion.RESP2),
        close: config.close,
        timeout: config.timeout,
    });

    const runTest = async (
        test: (client: BaseClient, cluster: RedisCluster) => Promise<void>,
        protocol: ProtocolVersion,
        clientName?: string,
    ) => {
        const { context, client, cluster } = await config.init(
            protocol,
            clientName,
        );
        let testSucceeded = false;

        try {
            await test(client, cluster);
            testSucceeded = true;
        } finally {
            config.close(context, testSucceeded);
        }
    };

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `should register client library name and version_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster: RedisCluster) => {
                if (cluster.checkIfServerVersionLessThan("7.2.0")) {
                    return;
                }

                const result = await client.customCommand(["CLIENT", "INFO"]);
                expect(result).toContain("lib-name=GlideJS");
                expect(result).toContain("lib-ver=unknown");
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `closed client raises error_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                client.close();

                try {
                    expect(await client.set("foo", "bar")).toThrow();
                } catch (e) {
                    expect((e as ClosingError).message).toMatch(
                        "Unable to execute requests; the client is closed. Please create a new client.",
                    );
                }
            }, protocol);
        },
        config.timeout,
    );

    it(
        `Check protocol version is RESP3`,
        async () => {
            await runTest(async (client: BaseClient) => {
                const result = (await client.customCommand(["HELLO"])) as {
                    proto: number;
                };
                expect(result?.proto).toEqual(3);
            }, ProtocolVersion.RESP3);
        },
        config.timeout,
    );

    it(
        `Check possible to opt-in to RESP2`,
        async () => {
            await runTest(async (client: BaseClient) => {
                const result = (await client.customCommand(["HELLO"])) as {
                    proto: number;
                };
                expect(result?.proto).toEqual(2);
            }, ProtocolVersion.RESP2);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `Check client name is configured correctly_%p`,
        async (protocol) => {
            await runTest(
                async (client: BaseClient) => {
                    expect(await client.clientGetName()).toBe("TEST_CLIENT");
                },
                protocol,
                "TEST_CLIENT",
            );
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `custom command works_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                // Adding random repetition, to prevent the inputs from always having the same alignment.
                const value = uuidv4() + "0".repeat(Math.random() * 7);
                const setResult = await client.customCommand([
                    "SET",
                    key,
                    value,
                ]);
                expect(setResult).toEqual("OK");
                const result = await client.customCommand(["GET", key]);
                expect(result).toEqual(value);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `getting array return value works_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = "{key}" + uuidv4();
                const key2 = "{key}" + uuidv4();
                const key3 = "{key}" + uuidv4();
                // Adding random repetition, to prevent the inputs from always having the same alignment.
                const value1 = uuidv4() + "0".repeat(Math.random() * 7);
                const value2 = uuidv4() + "0".repeat(Math.random() * 7);
                const setResult1 = await client.customCommand([
                    "SET",
                    key1,
                    value1,
                ]);
                expect(setResult1).toEqual("OK");
                const setResult2 = await client.customCommand([
                    "SET",
                    key2,
                    value2,
                ]);
                expect(setResult2).toEqual("OK");
                const mget_result = await client.customCommand([
                    "MGET",
                    key1,
                    key2,
                    key3,
                ]);
                expect(mget_result).toEqual([value1, value2, null]);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `delete multiple existing keys and an non existing key_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = "{key}" + uuidv4();
                const key2 = "{key}" + uuidv4();
                const key3 = "{key}" + uuidv4();
                const value = uuidv4();
                let result = await client.set(key1, value);
                expect(result).toEqual("OK");
                result = await client.set(key2, value);
                expect(result).toEqual("OK");
                result = await client.set(key3, value);
                expect(result).toEqual("OK");
                let deletedKeysNum = await client.del([key1, key2, key3]);
                expect(deletedKeysNum).toEqual(3);
                deletedKeysNum = await client.del([uuidv4()]);
                expect(deletedKeysNum).toEqual(0);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `testing clientGetName_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                expect(await client.clientGetName()).toBeNull();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `test config rewrite_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const serverInfo = await client.info([InfoOptions.Server]);
                const conf_file = parseInfoResponse(
                    getFirstResult(serverInfo).toString(),
                )["config_file"];

                if (conf_file.length > 0) {
                    expect(await client.configRewrite()).toEqual("OK");
                } else {
                    try {
                        /// We expect Redis to return an error since the test cluster doesn't use redis.conf file
                        expect(await client.configRewrite()).toThrow();
                    } catch (e) {
                        expect((e as Error).message).toMatch(
                            "The server is running without a config file",
                        );
                    }
                }
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `info stats before and after Config ResetStat is different_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                /// we execute set and info so the commandstats will show `cmdstat_set::calls` greater than 1
                /// after the configResetStat call we initiate an info command and the the commandstats won't contain `cmdstat_set`.
                await client.set("foo", "bar");
                const oldResult =
                    client instanceof GlideClient
                        ? await client.info([InfoOptions.Commandstats])
                        : Object.values(
                              await client.info([InfoOptions.Commandstats]),
                          ).join();
                expect(oldResult).toContain("cmdstat_set");
                expect(await client.configResetStat()).toEqual("OK");

                const result =
                    client instanceof GlideClient
                        ? await client.info([InfoOptions.Commandstats])
                        : Object.values(
                              await client.info([InfoOptions.Commandstats]),
                          ).join();
                expect(result).not.toContain("cmdstat_set");
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "lastsave %p",
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const today = new Date();
                today.setDate(today.getDate() - 1);
                const yesterday = today.getTime() / 1000; // as epoch time

                expect(await client.lastsave()).toBeGreaterThan(yesterday);

                if (client instanceof GlideClusterClient) {
                    Object.values(await client.lastsave("allNodes")).forEach(
                        (v) => expect(v).toBeGreaterThan(yesterday),
                    );
                }

                const response =
                    client instanceof GlideClient
                        ? await client.exec(new Transaction().lastsave())
                        : await client.exec(
                              new ClusterTransaction().lastsave(),
                          );
                expect(response?.[0]).toBeGreaterThan(yesterday);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `testing mset and mget with multiple existing keys and one non existing key_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                const key3 = uuidv4();
                const value = uuidv4();
                const keyValueList = {
                    [key1]: value,
                    [key2]: value,
                    [key3]: value,
                };
                expect(await client.mset(keyValueList)).toEqual("OK");
                expect(
                    await client.mget([key1, key2, "nonExistingKey", key3]),
                ).toEqual([value, value, null, value]);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `msetnx test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = "{key}-1" + uuidv4();
                const key2 = "{key}-2" + uuidv4();
                const key3 = "{key}-3" + uuidv4();
                const nonExistingKey = uuidv4();
                const value = uuidv4();
                const keyValueMap1 = {
                    [key1]: value,
                    [key2]: value,
                };
                const keyValueMap2 = {
                    [key2]: value,
                    [key3]: value,
                };

                expect(await client.msetnx(keyValueMap1)).toEqual(true);

                expect(await client.mget([key1, key2, nonExistingKey])).toEqual(
                    [value, value, null],
                );

                expect(await client.msetnx(keyValueMap2)).toEqual(false);

                expect(await client.get(key3)).toEqual(null);
                expect(await client.get(key2)).toEqual(value);

                // empty map and RequestError is thrown
                const emptyMap = {};
                await expect(client.msetnx(emptyMap)).rejects.toThrow(
                    RequestError,
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `incr, incrBy and incrByFloat with existing key_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                expect(await client.set(key, "10")).toEqual("OK");
                expect(await client.incr(key)).toEqual(11);
                expect(await client.get(key)).toEqual("11");
                expect(await client.incrBy(key, 4)).toEqual(15);
                expect(await client.get(key)).toEqual("15");
                expect(await client.incrByFloat(key, 1.5)).toEqual(16.5);
                expect(await client.get(key)).toEqual("16.5");
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `incr, incrBy and incrByFloat with non existing key_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                const key3 = uuidv4();
                /// key1 and key2 does not exist, so it set to 0 before performing the operation.
                expect(await client.incr(key1)).toEqual(1);
                expect(await client.get(key1)).toEqual("1");
                expect(await client.incrBy(key2, 2)).toEqual(2);
                expect(await client.get(key2)).toEqual("2");
                expect(await client.incrByFloat(key3, -0.5)).toEqual(-0.5);
                expect(await client.get(key3)).toEqual("-0.5");
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `incr, incrBy and incrByFloat with a key that contains a value of string that can not be represented as integer_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                expect(await client.set(key, "foo")).toEqual("OK");

                try {
                    expect(await client.incr(key)).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "value is not an integer",
                    );
                }

                try {
                    expect(await client.incrBy(key, 1)).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "value is not an integer",
                    );
                }

                try {
                    expect(await client.incrByFloat(key, 1.5)).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "value is not a valid float",
                    );
                }
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `ping test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                expect(await client.ping()).toEqual("PONG");
                expect(await client.ping("Hello")).toEqual("Hello");
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `clientId test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                expect(getFirstResult(await client.clientId())).toBeGreaterThan(
                    0,
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `decr and decrBy existing key_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                expect(await client.set(key, "10")).toEqual("OK");
                expect(await client.decr(key)).toEqual(9);
                expect(await client.get(key)).toEqual("9");
                expect(await client.decrBy(key, 4)).toEqual(5);
                expect(await client.get(key)).toEqual("5");
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `decr and decrBy with non existing key_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                /// key1 and key2 does not exist, so it set to 0 before performing the operation.
                expect(await client.get(key1)).toBeNull();
                expect(await client.decr(key1)).toEqual(-1);
                expect(await client.get(key1)).toEqual("-1");
                expect(await client.get(key2)).toBeNull();
                expect(await client.decrBy(key2, 3)).toEqual(-3);
                expect(await client.get(key2)).toEqual("-3");
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `decr and decrBy with a key that contains a value of string that can not be represented as integer_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                expect(await client.set(key, "foo")).toEqual("OK");

                try {
                    expect(await client.decr(key)).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "value is not an integer",
                    );
                }

                try {
                    expect(await client.decrBy(key, 3)).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "value is not an integer",
                    );
                }
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `bitop test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = `{key}-${uuidv4()}`;
                const key2 = `{key}-${uuidv4()}`;
                const key3 = `{key}-${uuidv4()}`;
                const keys = [key1, key2];
                const destination = `{key}-${uuidv4()}`;
                const nonExistingKey1 = `{key}-${uuidv4()}`;
                const nonExistingKey2 = `{key}-${uuidv4()}`;
                const nonExistingKey3 = `{key}-${uuidv4()}`;
                const nonExistingKeys = [
                    nonExistingKey1,
                    nonExistingKey2,
                    nonExistingKey3,
                ];
                const setKey = `{key}-${uuidv4()}`;
                const value1 = "foobar";
                const value2 = "abcdef";

                expect(await client.set(key1, value1)).toEqual("OK");
                expect(await client.set(key2, value2)).toEqual("OK");
                expect(
                    await client.bitop(BitwiseOperation.AND, destination, keys),
                ).toEqual(6);
                expect(await client.get(destination)).toEqual("`bc`ab");
                expect(
                    await client.bitop(BitwiseOperation.OR, destination, keys),
                ).toEqual(6);
                expect(await client.get(destination)).toEqual("goofev");

                // reset values for simplicity of results in XOR
                expect(await client.set(key1, "a")).toEqual("OK");
                expect(await client.set(key2, "b")).toEqual("OK");
                expect(
                    await client.bitop(BitwiseOperation.XOR, destination, keys),
                ).toEqual(1);
                expect(await client.get(destination)).toEqual("\u0003");

                // test single source key
                expect(
                    await client.bitop(BitwiseOperation.AND, destination, [
                        key1,
                    ]),
                ).toEqual(1);
                expect(await client.get(destination)).toEqual("a");
                expect(
                    await client.bitop(BitwiseOperation.OR, destination, [
                        key1,
                    ]),
                ).toEqual(1);
                expect(await client.get(destination)).toEqual("a");
                expect(
                    await client.bitop(BitwiseOperation.XOR, destination, [
                        key1,
                    ]),
                ).toEqual(1);
                expect(await client.get(destination)).toEqual("a");

                // Sets to a string (not a space character) with value 11000010 10011110.
                expect(await client.set(key3, "")).toEqual("OK");
                expect(await client.getbit(key3, 0)).toEqual(1);
                expect(
                    await client.bitop(BitwiseOperation.NOT, destination, [
                        key3,
                    ]),
                ).toEqual(2);
                // Value becomes 00111101 01100001.
                expect(await client.get(destination)).toEqual("=a");

                expect(await client.setbit(key1, 0, 1)).toEqual(0);
                expect(
                    await client.bitop(BitwiseOperation.NOT, destination, [
                        key1,
                    ]),
                ).toEqual(1);
                expect(await client.get(destination)).toEqual("\u001e");

                // stores null when all keys hold empty strings
                expect(
                    await client.bitop(
                        BitwiseOperation.AND,
                        destination,
                        nonExistingKeys,
                    ),
                ).toEqual(0);
                expect(await client.get(destination)).toBeNull();
                expect(
                    await client.bitop(
                        BitwiseOperation.OR,
                        destination,
                        nonExistingKeys,
                    ),
                ).toEqual(0);
                expect(await client.get(destination)).toBeNull();
                expect(
                    await client.bitop(
                        BitwiseOperation.XOR,
                        destination,
                        nonExistingKeys,
                    ),
                ).toEqual(0);
                expect(await client.get(destination)).toBeNull();
                expect(
                    await client.bitop(BitwiseOperation.NOT, destination, [
                        nonExistingKey1,
                    ]),
                ).toEqual(0);
                expect(await client.get(destination)).toBeNull();

                // invalid argument - source key list cannot be empty
                await expect(
                    client.bitop(BitwiseOperation.OR, destination, []),
                ).rejects.toThrow(RequestError);

                // invalid arguments - NOT cannot be passed more than 1 key
                await expect(
                    client.bitop(BitwiseOperation.NOT, destination, keys),
                ).rejects.toThrow(RequestError);

                expect(await client.sadd(setKey, ["foo"])).toEqual(1);
                // invalid argument - source key has the wrong type
                await expect(
                    client.bitop(BitwiseOperation.AND, destination, [setKey]),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `getbit test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = `{key}-${uuidv4()}`;
                const nonExistingKey = `{key}-${uuidv4()}`;
                const setKey = `{key}-${uuidv4()}`;

                expect(await client.set(key, "foo")).toEqual("OK");
                expect(await client.getbit(key, 1)).toEqual(1);
                // When offset is beyond the string length, the string is assumed to be a contiguous space with 0 bits.
                expect(await client.getbit(key, 1000)).toEqual(0);
                // When key does not exist it is assumed to be an empty string, so offset is always out of range and the
                // value is also assumed to be a contiguous space with 0 bits.
                expect(await client.getbit(nonExistingKey, 1)).toEqual(0);

                // invalid argument - offset can't be negative
                await expect(client.getbit(key, -1)).rejects.toThrow(
                    RequestError,
                );

                // key exists, but it is not a string
                expect(await client.sadd(setKey, ["foo"])).toEqual(1);
                await expect(client.getbit(setKey, 0)).rejects.toThrow(
                    RequestError,
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `setbit test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = `{key}-${uuidv4()}`;
                const setKey = `{key}-${uuidv4()}`;

                expect(await client.setbit(key, 1, 1)).toEqual(0);
                expect(await client.setbit(key, 1, 0)).toEqual(1);

                // invalid argument - offset can't be negative
                await expect(client.setbit(key, -1, 1)).rejects.toThrow(
                    RequestError,
                );

                // invalid argument - "value" arg must be 0 or 1
                await expect(client.setbit(key, 0, 2)).rejects.toThrow(
                    RequestError,
                );

                // key exists, but it is not a string
                expect(await client.sadd(setKey, ["foo"])).toEqual(1);
                await expect(client.setbit(setKey, 0, 0)).rejects.toThrow(
                    RequestError,
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `bitpos and bitposInterval test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                const key = `{key}-${uuidv4()}`;
                const nonExistingKey = `{key}-${uuidv4()}`;
                const setKey = `{key}-${uuidv4()}`;
                const value = "?f0obar"; // 00111111 01100110 00110000 01101111 01100010 01100001 01110010

                expect(await client.set(key, value)).toEqual("OK");
                expect(await client.bitpos(key, 0)).toEqual(0);
                expect(await client.bitpos(key, 1)).toEqual(2);
                expect(await client.bitpos(key, 1, 1)).toEqual(9);
                expect(await client.bitposInterval(key, 0, 3, 5)).toEqual(24);

                // -1 is returned if start > end
                expect(await client.bitposInterval(key, 0, 1, 0)).toEqual(-1);

                // `BITPOS` returns -1 for non-existing strings
                expect(await client.bitpos(nonExistingKey, 1)).toEqual(-1);
                expect(
                    await client.bitposInterval(nonExistingKey, 1, 3, 5),
                ).toEqual(-1);

                // invalid argument - bit value must be 0 or 1
                await expect(client.bitpos(key, 2)).rejects.toThrow(
                    RequestError,
                );
                await expect(
                    client.bitposInterval(key, 2, 3, 5),
                ).rejects.toThrow(RequestError);

                // key exists, but it is not a string
                expect(await client.sadd(setKey, ["foo"])).toEqual(1);
                await expect(client.bitpos(setKey, 1)).rejects.toThrow(
                    RequestError,
                );
                await expect(
                    client.bitposInterval(setKey, 1, 1, -1),
                ).rejects.toThrow(RequestError);

                if (cluster.checkIfServerVersionLessThan("7.0.0")) {
                    await expect(
                        client.bitposInterval(
                            key,
                            1,
                            1,
                            -1,
                            BitmapIndexType.BYTE,
                        ),
                    ).rejects.toThrow(RequestError);
                    await expect(
                        client.bitposInterval(
                            key,
                            1,
                            1,
                            -1,
                            BitmapIndexType.BIT,
                        ),
                    ).rejects.toThrow(RequestError);
                } else {
                    expect(
                        await client.bitposInterval(
                            key,
                            0,
                            3,
                            5,
                            BitmapIndexType.BYTE,
                        ),
                    ).toEqual(24);
                    expect(
                        await client.bitposInterval(
                            key,
                            1,
                            43,
                            -2,
                            BitmapIndexType.BIT,
                        ),
                    ).toEqual(47);
                    expect(
                        await client.bitposInterval(
                            nonExistingKey,
                            1,
                            3,
                            5,
                            BitmapIndexType.BYTE,
                        ),
                    ).toEqual(-1);
                    expect(
                        await client.bitposInterval(
                            nonExistingKey,
                            1,
                            3,
                            5,
                            BitmapIndexType.BIT,
                        ),
                    ).toEqual(-1);

                    // -1 is returned if the bit value wasn't found
                    expect(
                        await client.bitposInterval(
                            key,
                            1,
                            -1,
                            -1,
                            BitmapIndexType.BIT,
                        ),
                    ).toEqual(-1);

                    // key exists, but it is not a string
                    await expect(
                        client.bitposInterval(
                            setKey,
                            1,
                            1,
                            -1,
                            BitmapIndexType.BIT,
                        ),
                    ).rejects.toThrow(RequestError);
                }
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `bitfield test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = `{key}-${uuidv4()}`;
                const key2 = `{key}-${uuidv4()}`;
                const nonExistingKey = `{key}-${uuidv4()}`;
                const setKey = `{key}-${uuidv4()}`;
                const foobar = "foobar";
                const u2 = new UnsignedEncoding(2);
                const u7 = new UnsignedEncoding(7);
                const i3 = new SignedEncoding(3);
                const i8 = new SignedEncoding(8);
                const offset1 = new BitOffset(1);
                const offset5 = new BitOffset(5);
                const offset_multiplier4 = new BitOffsetMultiplier(4);
                const offset_multiplier8 = new BitOffsetMultiplier(8);
                const overflowSet = new BitFieldSet(u2, offset1, -10);
                const overflowGet = new BitFieldGet(u2, offset1);

                // binary value: 01100110 01101111 01101111 01100010 01100001 01110010
                expect(await client.set(key1, foobar)).toEqual("OK");

                // SET tests
                expect(
                    await client.bitfield(key1, [
                        // binary value becomes: 0(10)00110 01101111 01101111 01100010 01100001 01110010
                        new BitFieldSet(u2, offset1, 2),
                        // binary value becomes: 01000(011) 01101111 01101111 01100010 01100001 01110010
                        new BitFieldSet(i3, offset5, 3),
                        // binary value becomes: 01000011 01101111 01101111 0110(0010 010)00001 01110010
                        new BitFieldSet(u7, offset_multiplier4, 18),
                        // addressing with SET or INCRBY bits outside the current string length will enlarge the string,
                        // zero-padding it, as needed, for the minimal length needed, according to the most far bit touched.
                        //
                        // binary value becomes:
                        // 01000011 01101111 01101111 01100010 01000001 01110010 00000000 00000000 (00010100)
                        new BitFieldSet(i8, offset_multiplier8, 20),
                        new BitFieldGet(u2, offset1),
                        new BitFieldGet(i3, offset5),
                        new BitFieldGet(u7, offset_multiplier4),
                        new BitFieldGet(i8, offset_multiplier8),
                    ]),
                ).toEqual([3, -2, 19, 0, 2, 3, 18, 20]);

                // INCRBY tests
                expect(
                    await client.bitfield(key1, [
                        // binary value becomes:
                        // 0(11)00011 01101111 01101111 01100010 01000001 01110010 00000000 00000000 00010100
                        new BitFieldIncrBy(u2, offset1, 1),
                        // binary value becomes:
                        // 01100(101) 01101111 01101111 01100010 01000001 01110010 00000000 00000000 00010100
                        new BitFieldIncrBy(i3, offset5, 2),
                        // binary value becomes:
                        // 01100101 01101111 01101111 0110(0001 111)00001 01110010 00000000 00000000 00010100
                        new BitFieldIncrBy(u7, offset_multiplier4, -3),
                        // binary value becomes:
                        // 01100101 01101111 01101111 01100001 11100001 01110010 00000000 00000000 (00011110)
                        new BitFieldIncrBy(i8, offset_multiplier8, 10),
                    ]),
                ).toEqual([3, -3, 15, 30]);

                // OVERFLOW WRAP is used by default if no OVERFLOW is specified
                expect(
                    await client.bitfield(key2, [
                        overflowSet,
                        new BitFieldOverflow(BitOverflowControl.WRAP),
                        overflowSet,
                        overflowGet,
                    ]),
                ).toEqual([0, 2, 2]);

                // OVERFLOW affects only SET or INCRBY after OVERFLOW subcommand
                expect(
                    await client.bitfield(key2, [
                        overflowSet,
                        new BitFieldOverflow(BitOverflowControl.SAT),
                        overflowSet,
                        overflowGet,
                        new BitFieldOverflow(BitOverflowControl.FAIL),
                        overflowSet,
                    ]),
                ).toEqual([2, 2, 3, null]);

                // if the key doesn't exist, the operation is performed as though the missing value was a string with all bits
                // set to 0.
                expect(
                    await client.bitfield(nonExistingKey, [
                        new BitFieldSet(
                            new UnsignedEncoding(2),
                            new BitOffset(3),
                            2,
                        ),
                    ]),
                ).toEqual([0]);

                // empty subcommands argument returns an empty list
                expect(await client.bitfield(key1, [])).toEqual([]);

                // invalid argument - offset must be >= 0
                await expect(
                    client.bitfield(key1, [
                        new BitFieldSet(
                            new UnsignedEncoding(5),
                            new BitOffset(-1),
                            1,
                        ),
                    ]),
                ).rejects.toThrow(RequestError);

                // invalid argument - encoding size must be > 0
                await expect(
                    client.bitfield(key1, [
                        new BitFieldSet(
                            new UnsignedEncoding(0),
                            new BitOffset(1),
                            1,
                        ),
                    ]),
                ).rejects.toThrow(RequestError);

                // invalid argument - unsigned encoding must be < 64
                await expect(
                    client.bitfield(key1, [
                        new BitFieldSet(
                            new UnsignedEncoding(64),
                            new BitOffset(1),
                            1,
                        ),
                    ]),
                ).rejects.toThrow(RequestError);

                // invalid argument - signed encoding must be < 65
                await expect(
                    client.bitfield(key1, [
                        new BitFieldSet(
                            new SignedEncoding(65),
                            new BitOffset(1),
                            1,
                        ),
                    ]),
                ).rejects.toThrow(RequestError);

                // key exists, but it is not a string
                expect(await client.sadd(setKey, [foobar])).toEqual(1);
                await expect(
                    client.bitfield(setKey, [
                        new BitFieldSet(
                            new SignedEncoding(3),
                            new BitOffset(1),
                            2,
                        ),
                    ]),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `bitfieldReadOnly test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                if (cluster.checkIfServerVersionLessThan("6.0.0")) {
                    return;
                }

                const key = `{key}-${uuidv4()}`;
                const nonExistingKey = `{key}-${uuidv4()}`;
                const setKey = `{key}-${uuidv4()}`;
                const foobar = "foobar";
                const unsignedOffsetGet = new BitFieldGet(
                    new UnsignedEncoding(2),
                    new BitOffset(1),
                );

                // binary value: 01100110 01101111 01101111 01100010 01100001 01110010
                expect(await client.set(key, foobar)).toEqual("OK");
                expect(
                    await client.bitfieldReadOnly(key, [
                        // Get value in: 0(11)00110 01101111 01101111 01100010 01100001 01110010 00010100
                        unsignedOffsetGet,
                        // Get value in: 01100(110) 01101111 01101111 01100010 01100001 01110010 00010100
                        new BitFieldGet(
                            new SignedEncoding(3),
                            new BitOffset(5),
                        ),
                        // Get value in: 01100110 01101111 01101(111 0110)0010 01100001 01110010 00010100
                        new BitFieldGet(
                            new UnsignedEncoding(7),
                            new BitOffsetMultiplier(3),
                        ),
                        // Get value in: 01100110 01101111 (01101111) 01100010 01100001 01110010 00010100
                        new BitFieldGet(
                            new SignedEncoding(8),
                            new BitOffsetMultiplier(2),
                        ),
                    ]),
                ).toEqual([3, -2, 118, 111]);

                // offset is greater than current length of string: the operation is performed like the missing part all
                // consists of bits set to 0.
                expect(
                    await client.bitfieldReadOnly(key, [
                        new BitFieldGet(
                            new UnsignedEncoding(3),
                            new BitOffset(100),
                        ),
                    ]),
                ).toEqual([0]);

                // similarly, if the key doesn't exist, the operation is performed as though the missing value was a string with
                // all bits set to 0.
                expect(
                    await client.bitfieldReadOnly(nonExistingKey, [
                        unsignedOffsetGet,
                    ]),
                ).toEqual([0]);

                // empty subcommands argument returns an empty list
                expect(await client.bitfieldReadOnly(key, [])).toEqual([]);

                // invalid argument - offset must be >= 0
                await expect(
                    client.bitfieldReadOnly(key, [
                        new BitFieldGet(
                            new UnsignedEncoding(5),
                            new BitOffset(-1),
                        ),
                    ]),
                ).rejects.toThrow(RequestError);

                // invalid argument - encoding size must be > 0
                await expect(
                    client.bitfieldReadOnly(key, [
                        new BitFieldGet(
                            new UnsignedEncoding(0),
                            new BitOffset(1),
                        ),
                    ]),
                ).rejects.toThrow(RequestError);

                // invalid argument - unsigned encoding must be < 64
                await expect(
                    client.bitfieldReadOnly(key, [
                        new BitFieldGet(
                            new UnsignedEncoding(64),
                            new BitOffset(1),
                        ),
                    ]),
                ).rejects.toThrow(RequestError);

                // invalid argument - signed encoding must be < 65
                await expect(
                    client.bitfieldReadOnly(key, [
                        new BitFieldGet(
                            new SignedEncoding(65),
                            new BitOffset(1),
                        ),
                    ]),
                ).rejects.toThrow(RequestError);

                // key exists, but it is not a string
                expect(await client.sadd(setKey, [foobar])).toEqual(1);
                await expect(
                    client.bitfieldReadOnly(setKey, [
                        new BitFieldGet(
                            new SignedEncoding(3),
                            new BitOffset(1),
                        ),
                    ]),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `config get and config set with timeout parameter_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const prevTimeout = (await client.configGet([
                    "timeout",
                ])) as Record<string, string>;
                expect(await client.configSet({ timeout: "1000" })).toEqual(
                    "OK",
                );
                const currTimeout = (await client.configGet([
                    "timeout",
                ])) as Record<string, string>;
                expect(currTimeout).toEqual({ timeout: "1000" });
                /// Revert to the pervious configuration
                expect(
                    await client.configSet({
                        timeout: prevTimeout["timeout"],
                    }),
                ).toEqual("OK");
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `getdel test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const value1 = uuidv4();
                const key2 = uuidv4();

                expect(await client.set(key1, value1)).toEqual("OK");
                expect(await client.getdel(key1)).toEqual(value1);
                expect(await client.getdel(key1)).toEqual(null);

                // key isn't a string
                expect(await client.sadd(key2, ["a"])).toEqual(1);
                await expect(client.getdel(key2)).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `getrange test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                const key = uuidv4();
                const nonStringKey = uuidv4();

                expect(await client.set(key, "This is a string")).toEqual("OK");
                expect(await client.getrange(key, 0, 3)).toEqual("This");
                expect(await client.getrange(key, -3, -1)).toEqual("ing");
                expect(await client.getrange(key, 0, -1)).toEqual(
                    "This is a string",
                );

                // out of range
                expect(await client.getrange(key, 10, 100)).toEqual("string");
                expect(await client.getrange(key, -200, -3)).toEqual(
                    "This is a stri",
                );
                expect(await client.getrange(key, 100, 200)).toEqual("");

                // incorrect range
                expect(await client.getrange(key, -1, -3)).toEqual("");

                // a bug fixed in version 8: https://github.com/redis/redis/issues/13207
                expect(await client.getrange(key, -200, -100)).toEqual(
                    cluster.checkIfServerVersionLessThan("8.0.0") ? "T" : "",
                );

                // empty key (returning null isn't implemented)
                expect(await client.getrange(nonStringKey, 0, -1)).toEqual(
                    cluster.checkIfServerVersionLessThan("8.0.0") ? "" : null,
                );

                // non-string key
                expect(await client.lpush(nonStringKey, ["_"])).toEqual(1);
                await expect(
                    client.getrange(nonStringKey, 0, -1),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `hscan test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = "{key}-1" + uuidv4();
                const key2 = "{key}-2" + uuidv4();
                const initialCursor = "0";
                const defaultCount = 20;
                const resultCursorIndex = 0;
                const resultCollectionIndex = 1;

                // Setup test data - use a large number of entries to force an iterative cursor.
                const numberMap: Record<string, string> = {};

                for (let i = 0; i < 50000; i++) {
                    numberMap[i.toString()] = "num" + i;
                }

                const charMembers = ["a", "b", "c", "d", "e"];
                const charMap: Record<string, string> = {};

                for (let i = 0; i < charMembers.length; i++) {
                    charMap[charMembers[i]] = i.toString();
                }

                // Result contains the whole set
                expect(await client.hset(key1, charMap)).toEqual(
                    charMembers.length,
                );
                let result = await client.hscan(key1, initialCursor);
                expect(result[resultCursorIndex]).toEqual(initialCursor);
                expect(result[resultCollectionIndex].length).toEqual(
                    Object.keys(charMap).length * 2, // Length includes the score which is twice the map size
                );

                const resultArray = result[resultCollectionIndex];
                const resultKeys = [];
                const resultValues: string[] = [];

                for (let i = 0; i < resultArray.length; i += 2) {
                    resultKeys.push(resultArray[i]);
                    resultValues.push(resultArray[i + 1]);
                }

                // Verify if all keys from charMap are in resultKeys
                const allKeysIncluded = resultKeys.every(
                    (key) => key in charMap,
                );
                expect(allKeysIncluded).toEqual(true);

                const allValuesIncluded = Object.values(charMap).every(
                    (value) => value in resultValues,
                );
                expect(allValuesIncluded).toEqual(true);

                // Test hscan with match
                result = await client.hscan(key1, initialCursor, {
                    match: "a",
                });

                expect(result[resultCursorIndex]).toEqual(initialCursor);
                expect(result[resultCollectionIndex]).toEqual(["a", "0"]);

                // Set up testing data for key1 tests
                expect(await client.hset(key1, numberMap)).toEqual(
                    Object.keys(numberMap).length,
                );

                let resultCursor = initialCursor;
                const secondResultAllKeys: string[] = [];
                const secondResultAllValues: string[] = [];
                let isFirstLoop = true;

                do {
                    result = await client.hscan(key1, resultCursor);
                    resultCursor = result[resultCursorIndex].toString();
                    const resultEntry = result[resultCollectionIndex];

                    for (let i = 0; i < resultEntry.length; i += 2) {
                        secondResultAllKeys.push(resultEntry[i]);
                        secondResultAllValues.push(resultEntry[i + 1]);
                    }

                    if (isFirstLoop) {
                        expect(resultCursor).not.toBe("0");
                        isFirstLoop = false;
                    } else if (resultCursor === initialCursor) {
                        break;
                    }

                    // Scan with result cursor has a different set
                    const secondResult = await client.hscan(key1, resultCursor);
                    const newResultCursor =
                        secondResult[resultCursorIndex].toString();
                    expect(resultCursor).not.toBe(newResultCursor);
                    resultCursor = newResultCursor;
                    const secondResultEntry =
                        secondResult[resultCollectionIndex];

                    expect(result[resultCollectionIndex]).not.toBe(
                        secondResult[resultCollectionIndex],
                    );

                    for (let i = 0; i < secondResultEntry.length; i += 2) {
                        secondResultAllKeys.push(secondResultEntry[i]);
                        secondResultAllValues.push(secondResultEntry[i + 1]);
                    }
                } while (resultCursor != initialCursor); // 0 is returned for the cursor of the last iteration.

                // Verify all data is found in hscan
                const allSecondResultKeys = Object.keys(numberMap).every(
                    (key) => key in secondResultAllKeys,
                );
                expect(allSecondResultKeys).toEqual(true);

                const allSecondResultValues = Object.keys(numberMap).every(
                    (value) => value in secondResultAllValues,
                );
                expect(allSecondResultValues).toEqual(true);

                // Test match pattern
                result = await client.hscan(key1, initialCursor, {
                    match: "*",
                });
                expect(result[resultCursorIndex]).not.toEqual(initialCursor);
                expect(
                    result[resultCollectionIndex].length,
                ).toBeGreaterThanOrEqual(defaultCount);

                // Test count
                result = await client.hscan(key1, initialCursor, {
                    count: 25,
                });
                expect(result[resultCursorIndex]).not.toEqual(initialCursor);
                expect(
                    result[resultCollectionIndex].length,
                ).toBeGreaterThanOrEqual(25);

                // Test count with match returns a non-empty list
                result = await client.hscan(key1, initialCursor, {
                    match: "1*",
                    count: 30,
                });
                expect(result[resultCursorIndex]).not.toEqual(initialCursor);
                expect(result[resultCollectionIndex].length).toBeGreaterThan(0);

                // Exceptions
                // Non-hash key
                expect(await client.set(key2, "test")).toEqual("OK");
                await expect(client.hscan(key2, initialCursor)).rejects.toThrow(
                    RequestError,
                );
                await expect(
                    client.hscan(key2, initialCursor, {
                        match: "test",
                        count: 20,
                    }),
                ).rejects.toThrow(RequestError);

                // Negative count
                await expect(
                    client.hscan(key2, initialCursor, {
                        count: -1,
                    }),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `hscan, sscan, and zscan empty set and negative cursor tests`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = "{key}-1" + uuidv4();
                const initialCursor = "0";
                const resultCursorIndex = 0;
                const resultCollectionIndex = 1;

                // Empty set
                let result = await client.hscan(key1, initialCursor);
                expect(result[resultCursorIndex]).toEqual(initialCursor);
                expect(result[resultCollectionIndex]).toEqual([]);

                // Negative cursor
                result = await client.hscan(key1, "-1");
                expect(result[resultCursorIndex]).toEqual(initialCursor);
                expect(result[resultCollectionIndex]).toEqual([]);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `testing hset and hget with multiple existing fields and one non existing field_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const field1 = uuidv4();
                const field2 = uuidv4();
                const value = uuidv4();
                const fieldValueMap = {
                    [field1]: value,
                    [field2]: value,
                };
                expect(await client.hset(key, fieldValueMap)).toEqual(2);
                expect(await client.hget(key, field1)).toEqual(value);
                expect(await client.hget(key, field2)).toEqual(value);
                expect(await client.hget(key, "nonExistingField")).toEqual(
                    null,
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `hdel multiple existing fields, an non existing field and an non existing key_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const field1 = uuidv4();
                const field2 = uuidv4();
                const field3 = uuidv4();
                const value = uuidv4();
                const fieldValueMap = {
                    [field1]: value,
                    [field2]: value,
                    [field3]: value,
                };

                expect(await client.hset(key, fieldValueMap)).toEqual(3);
                expect(await client.hdel(key, [field1, field2])).toEqual(2);
                expect(await client.hdel(key, ["nonExistingField"])).toEqual(0);
                expect(await client.hdel("nonExistingKey", [field3])).toEqual(
                    0,
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `testing hmget with multiple existing fields, an non existing field and an non existing key_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const field1 = uuidv4();
                const field2 = uuidv4();
                const value = uuidv4();
                const fieldValueMap = {
                    [field1]: value,
                    [field2]: value,
                };
                expect(await client.hset(key, fieldValueMap)).toEqual(2);
                expect(
                    await client.hmget(key, [
                        field1,
                        "nonExistingField",
                        field2,
                    ]),
                ).toEqual([value, null, value]);
                expect(
                    await client.hmget("nonExistingKey", [field1, field2]),
                ).toEqual([null, null]);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `hexists existing field, an non existing field and an non existing key_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const field1 = uuidv4();
                const field2 = uuidv4();
                const fieldValueMap = {
                    [field1]: "value1",
                    [field2]: "value2",
                };
                expect(await client.hset(key, fieldValueMap)).toEqual(2);
                expect(await client.hexists(key, field1)).toEqual(true);
                expect(await client.hexists(key, "nonExistingField")).toEqual(
                    false,
                );
                expect(await client.hexists("nonExistingKey", field2)).toEqual(
                    false,
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `hgetall with multiple fields in an existing key and one non existing key_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const field1 = uuidv4();
                const field2 = uuidv4();
                const value = uuidv4();
                const fieldValueMap = {
                    [field1]: value,
                    [field2]: value,
                };
                expect(await client.hset(key, fieldValueMap)).toEqual(2);

                expect(await client.hgetall(key)).toEqual({
                    [field1]: value,
                    [field2]: value,
                });

                expect(await client.hgetall("nonExistingKey")).toEqual({});
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `hincrBy and hincrByFloat with existing key and field_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const field = uuidv4();
                const fieldValueMap = {
                    [field]: "10",
                };
                expect(await client.hset(key, fieldValueMap)).toEqual(1);
                expect(await client.hincrBy(key, field, 1)).toEqual(11);
                expect(await client.hincrBy(key, field, 4)).toEqual(15);
                expect(await client.hincrByFloat(key, field, 1.5)).toEqual(
                    16.5,
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `hincrBy and hincrByFloat with non existing key and non existing field_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                const field = uuidv4();
                const fieldValueMap = {
                    [field]: "10",
                };
                expect(
                    await client.hincrBy("nonExistingKey", field, 1),
                ).toEqual(1);
                expect(await client.hset(key1, fieldValueMap)).toEqual(1);
                expect(
                    await client.hincrBy(key1, "nonExistingField", 2),
                ).toEqual(2);
                expect(await client.hset(key2, fieldValueMap)).toEqual(1);
                expect(
                    await client.hincrByFloat(key2, "nonExistingField", -0.5),
                ).toEqual(-0.5);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `hincrBy and hincrByFloat with a field that contains a value of string that can not be represented as as integer or float_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const field = uuidv4();
                const fieldValueMap = {
                    [field]: "foo",
                };
                expect(await client.hset(key, fieldValueMap)).toEqual(1);

                try {
                    expect(await client.hincrBy(key, field, 2)).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "hash value is not an integer",
                    );
                }

                try {
                    expect(
                        await client.hincrByFloat(key, field, 1.5),
                    ).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "hash value is not a float",
                    );
                }
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `hlen test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const field1 = uuidv4();
                const field2 = uuidv4();
                const fieldValueMap = {
                    [field1]: "value1",
                    [field2]: "value2",
                };

                expect(await client.hset(key1, fieldValueMap)).toEqual(2);
                expect(await client.hlen(key1)).toEqual(2);
                expect(await client.hdel(key1, [field1])).toEqual(1);
                expect(await client.hlen(key1)).toEqual(1);
                expect(await client.hlen("nonExistingHash")).toEqual(0);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `hvals test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const field1 = uuidv4();
                const field2 = uuidv4();
                const fieldValueMap = {
                    [field1]: "value1",
                    [field2]: "value2",
                };

                expect(await client.hset(key1, fieldValueMap)).toEqual(2);
                expect(await client.hvals(key1)).toEqual(["value1", "value2"]);
                expect(await client.hdel(key1, [field1])).toEqual(1);
                expect(await client.hvals(key1)).toEqual(["value2"]);
                expect(await client.hvals("nonExistingHash")).toEqual([]);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `hsetnx test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                const field = uuidv4();

                expect(await client.hsetnx(key1, field, "value")).toEqual(true);
                expect(await client.hsetnx(key1, field, "newValue")).toEqual(
                    false,
                );
                expect(await client.hget(key1, field)).toEqual("value");

                expect(await client.set(key2, "value")).toEqual("OK");
                await expect(
                    client.hsetnx(key2, field, "value"),
                ).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `hstrlen test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                const field = uuidv4();

                expect(await client.hset(key1, { field: "value" })).toBe(1);
                expect(await client.hstrlen(key1, "field")).toBe(5);

                // missing value
                expect(await client.hstrlen(key1, "nonExistingField")).toBe(0);

                // missing key
                expect(await client.hstrlen(key2, "field")).toBe(0);

                // key exists but holds non hash type value
                expect(await client.set(key2, "value")).toEqual("OK");
                await expect(client.hstrlen(key2, field)).rejects.toThrow(
                    RequestError,
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `hrandfield test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                if (cluster.checkIfServerVersionLessThan("6.2.0")) {
                    return;
                }

                const key1 = uuidv4();
                const key2 = uuidv4();

                // key does not exist
                expect(await client.hrandfield(key1)).toBeNull();
                expect(await client.hrandfieldCount(key1, 5)).toEqual([]);
                expect(await client.hrandfieldWithValues(key1, 5)).toEqual([]);

                const data = { "f 1": "v 1", "f 2": "v 2", "f 3": "v 3" };
                const fields = Object.keys(data);
                const entries = Object.entries(data);
                expect(await client.hset(key1, data)).toEqual(3);

                expect(fields).toContain(await client.hrandfield(key1));

                // With Count - positive count
                let result = await client.hrandfieldCount(key1, 5);
                expect(result).toEqual(fields);

                // With Count - negative count
                result = await client.hrandfieldCount(key1, -5);
                expect(result.length).toEqual(5);
                result.map((r) => expect(fields).toContain(r));

                // With values - positive count
                let result2 = await client.hrandfieldWithValues(key1, 5);
                expect(result2).toEqual(entries);

                // With values - negative count
                result2 = await client.hrandfieldWithValues(key1, -5);
                expect(result2.length).toEqual(5);
                result2.map((r) => expect(entries).toContainEqual(r));

                // key exists but holds non hash type value
                expect(await client.set(key2, "value")).toEqual("OK");
                await expect(client.hrandfield(key2)).rejects.toThrow(
                    RequestError,
                );
                await expect(client.hrandfieldCount(key2, 42)).rejects.toThrow(
                    RequestError,
                );
                await expect(
                    client.hrandfieldWithValues(key2, 42),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `lpush, lpop and lrange with existing and non existing key_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const valueList = ["value4", "value3", "value2", "value1"];
                expect(await client.lpush(key, valueList)).toEqual(4);
                expect(await client.lpop(key)).toEqual("value1");
                expect(await client.lrange(key, 0, -1)).toEqual([
                    "value2",
                    "value3",
                    "value4",
                ]);
                expect(await client.lpopCount(key, 2)).toEqual([
                    "value2",
                    "value3",
                ]);
                expect(await client.lrange("nonExistingKey", 0, -1)).toEqual(
                    [],
                );
                expect(await client.lpop("nonExistingKey")).toEqual(null);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `lpush, lpop and lrange with key that holds a value that is not a list_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                expect(await client.set(key, "foo")).toEqual("OK");

                try {
                    expect(await client.lpush(key, ["bar"])).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "Operation against a key holding the wrong kind of value",
                    );
                }

                try {
                    expect(await client.lpop(key)).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "Operation against a key holding the wrong kind of value",
                    );
                }

                try {
                    expect(await client.lrange(key, 0, -1)).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "Operation against a key holding the wrong kind of value",
                    );
                }
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `lpushx list_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                const key3 = uuidv4();

                expect(await client.lpush(key1, ["0"])).toEqual(1);
                expect(await client.lpushx(key1, ["1", "2", "3"])).toEqual(4);
                expect(await client.lrange(key1, 0, -1)).toEqual([
                    "3",
                    "2",
                    "1",
                    "0",
                ]);

                expect(await client.lpushx(key2, ["1"])).toEqual(0);
                expect(await client.lrange(key2, 0, -1)).toEqual([]);

                // Key exists, but is not a list
                expect(await client.set(key3, "bar"));
                await expect(client.lpushx(key3, ["_"])).rejects.toThrow(
                    RequestError,
                );

                // Empty element list
                await expect(client.lpushx(key2, [])).rejects.toThrow(
                    RequestError,
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `llen with existing, non-existing key and key that holds a value that is not a list_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                const valueList = ["value4", "value3", "value2", "value1"];
                expect(await client.lpush(key1, valueList)).toEqual(4);
                expect(await client.llen(key1)).toEqual(4);

                expect(await client.llen("nonExistingKey")).toEqual(0);

                expect(await client.set(key2, "foo")).toEqual("OK");

                try {
                    expect(await client.llen(key2)).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "Operation against a key holding the wrong kind of value",
                    );
                }
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `lmove list_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                if (cluster.checkIfServerVersionLessThan("6.2.0")) {
                    return;
                }

                const key1 = "{key}-1" + uuidv4();
                const key2 = "{key}-2" + uuidv4();
                const lpushArgs1 = ["2", "1"];
                const lpushArgs2 = ["4", "3"];

                // Initialize the tests
                expect(await client.lpush(key1, lpushArgs1)).toEqual(2);
                expect(await client.lpush(key2, lpushArgs2)).toEqual(2);

                // Move from LEFT to LEFT
                expect(
                    await client.lmove(
                        key1,
                        key2,
                        ListDirection.LEFT,
                        ListDirection.LEFT,
                    ),
                ).toEqual("1");

                // Move from LEFT to RIGHT
                expect(
                    await client.lmove(
                        key1,
                        key2,
                        ListDirection.LEFT,
                        ListDirection.RIGHT,
                    ),
                ).toEqual("2");

                expect(await client.lrange(key2, 0, -1)).toEqual([
                    "1",
                    "3",
                    "4",
                    "2",
                ]);
                expect(await client.lrange(key1, 0, -1)).toEqual([]);

                // Move from RIGHT to LEFT - non-existing destination key
                expect(
                    await client.lmove(
                        key2,
                        key1,
                        ListDirection.RIGHT,
                        ListDirection.LEFT,
                    ),
                ).toEqual("2");

                // Move from RIGHT to RIGHT
                expect(
                    await client.lmove(
                        key2,
                        key1,
                        ListDirection.RIGHT,
                        ListDirection.RIGHT,
                    ),
                ).toEqual("4");

                expect(await client.lrange(key2, 0, -1)).toEqual(["1", "3"]);
                expect(await client.lrange(key1, 0, -1)).toEqual(["2", "4"]);

                // Non-existing source key
                expect(
                    await client.lmove(
                        "{key}-non_existing_key" + uuidv4(),
                        key1,
                        ListDirection.LEFT,
                        ListDirection.LEFT,
                    ),
                ).toEqual(null);

                // Non-list source key
                const key3 = "{key}-3" + uuidv4();
                expect(await client.set(key3, "value")).toEqual("OK");
                await expect(
                    client.lmove(
                        key3,
                        key1,
                        ListDirection.LEFT,
                        ListDirection.LEFT,
                    ),
                ).rejects.toThrow(RequestError);

                // Non-list destination key
                await expect(
                    client.lmove(
                        key1,
                        key3,
                        ListDirection.LEFT,
                        ListDirection.LEFT,
                    ),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `blmove list_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                if (cluster.checkIfServerVersionLessThan("6.2.0")) {
                    return;
                }

                const key1 = "{key}-1" + uuidv4();
                const key2 = "{key}-2" + uuidv4();
                const lpushArgs1 = ["2", "1"];
                const lpushArgs2 = ["4", "3"];

                // Initialize the tests
                expect(await client.lpush(key1, lpushArgs1)).toEqual(2);
                expect(await client.lpush(key2, lpushArgs2)).toEqual(2);

                // Move from LEFT to LEFT with blocking
                expect(
                    await client.blmove(
                        key1,
                        key2,
                        ListDirection.LEFT,
                        ListDirection.LEFT,
                        0.1,
                    ),
                ).toEqual("1");

                // Move from LEFT to RIGHT with blocking
                expect(
                    await client.blmove(
                        key1,
                        key2,
                        ListDirection.LEFT,
                        ListDirection.RIGHT,
                        0.1,
                    ),
                ).toEqual("2");

                expect(await client.lrange(key2, 0, -1)).toEqual([
                    "1",
                    "3",
                    "4",
                    "2",
                ]);
                expect(await client.lrange(key1, 0, -1)).toEqual([]);

                // Move from RIGHT to LEFT non-existing destination with blocking
                expect(
                    await client.blmove(
                        key2,
                        key1,
                        ListDirection.RIGHT,
                        ListDirection.LEFT,
                        0.1,
                    ),
                ).toEqual("2");

                expect(await client.lrange(key2, 0, -1)).toEqual([
                    "1",
                    "3",
                    "4",
                ]);
                expect(await client.lrange(key1, 0, -1)).toEqual(["2"]);

                // Move from RIGHT to RIGHT with blocking
                expect(
                    await client.blmove(
                        key2,
                        key1,
                        ListDirection.RIGHT,
                        ListDirection.RIGHT,
                        0.1,
                    ),
                ).toEqual("4");

                expect(await client.lrange(key2, 0, -1)).toEqual(["1", "3"]);
                expect(await client.lrange(key1, 0, -1)).toEqual(["2", "4"]);

                // Non-existing source key with blocking
                expect(
                    await client.blmove(
                        "{key}-non_existing_key" + uuidv4(),
                        key1,
                        ListDirection.LEFT,
                        ListDirection.LEFT,
                        0.1,
                    ),
                ).toEqual(null);

                // Non-list source key with blocking
                const key3 = "{key}-3" + uuidv4();
                expect(await client.set(key3, "value")).toEqual("OK");
                await expect(
                    client.blmove(
                        key3,
                        key1,
                        ListDirection.LEFT,
                        ListDirection.LEFT,
                        0.1,
                    ),
                ).rejects.toThrow(RequestError);

                // Non-list destination key
                await expect(
                    client.blmove(
                        key1,
                        key3,
                        ListDirection.LEFT,
                        ListDirection.LEFT,
                        0.1,
                    ),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `lset test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const nonExistingKey = uuidv4();
                const index = 0;
                const oobIndex = 10;
                const negativeIndex = -1;
                const element = "zero";
                const lpushArgs = ["four", "three", "two", "one"];
                const expectedList = ["zero", "two", "three", "four"];
                const expectedList2 = ["zero", "two", "three", "zero"];

                // key does not exist
                await expect(
                    client.lset(nonExistingKey, index, element),
                ).rejects.toThrow(RequestError);

                expect(await client.lpush(key, lpushArgs)).toEqual(4);

                // index out of range
                await expect(
                    client.lset(key, oobIndex, element),
                ).rejects.toThrow(RequestError);

                // assert lset result
                expect(await client.lset(key, index, element)).toEqual("OK");
                expect(await client.lrange(key, 0, negativeIndex)).toEqual(
                    expectedList,
                );

                // assert lset with a negative index for the last element in the list
                expect(await client.lset(key, negativeIndex, element)).toEqual(
                    "OK",
                );
                expect(await client.lrange(key, 0, negativeIndex)).toEqual(
                    expectedList2,
                );

                // assert lset against a non-list key
                const nonListKey = "nonListKey";
                expect(await client.sadd(nonListKey, ["a"])).toEqual(1);

                await expect(client.lset(nonListKey, 0, "b")).rejects.toThrow(
                    RequestError,
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `ltrim with existing key and key that holds a value that is not a list_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const valueList = ["value4", "value3", "value2", "value1"];
                expect(await client.lpush(key, valueList)).toEqual(4);
                expect(await client.ltrim(key, 0, 1)).toEqual("OK");
                expect(await client.lrange(key, 0, -1)).toEqual([
                    "value1",
                    "value2",
                ]);

                /// `start` is greater than `end` so the key will be removed.
                expect(await client.ltrim(key, 4, 2)).toEqual("OK");
                expect(await client.lrange(key, 0, -1)).toEqual([]);

                expect(await client.set(key, "foo")).toEqual("OK");

                try {
                    expect(await client.ltrim(key, 0, 1)).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "Operation against a key holding the wrong kind of value",
                    );
                }
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `lrem with existing key and non existing key_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const valueList = [
                    "value1",
                    "value2",
                    "value1",
                    "value1",
                    "value2",
                ];
                expect(await client.lpush(key, valueList)).toEqual(5);
                expect(await client.lrem(key, 2, "value1")).toEqual(2);
                expect(await client.lrange(key, 0, -1)).toEqual([
                    "value2",
                    "value2",
                    "value1",
                ]);
                expect(await client.lrem(key, -1, "value2")).toEqual(1);
                expect(await client.lrange(key, 0, -1)).toEqual([
                    "value2",
                    "value1",
                ]);
                expect(await client.lrem(key, 0, "value2")).toEqual(1);
                expect(await client.lrange(key, 0, -1)).toEqual(["value1"]);
                expect(await client.lrem("nonExistingKey", 2, "value")).toEqual(
                    0,
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `rpush and rpop with existing and non existing key_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const valueList = ["value1", "value2", "value3", "value4"];
                expect(await client.rpush(key, valueList)).toEqual(4);
                expect(await client.rpop(key)).toEqual("value4");
                expect(await client.rpopCount(key, 2)).toEqual([
                    "value3",
                    "value2",
                ]);
                expect(await client.rpop("nonExistingKey")).toEqual(null);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `rpush and rpop with key that holds a value that is not a list_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                expect(await client.set(key, "foo")).toEqual("OK");

                try {
                    expect(await client.rpush(key, ["bar"])).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "Operation against a key holding the wrong kind of value",
                    );
                }

                try {
                    expect(await client.rpop(key)).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "Operation against a key holding the wrong kind of value",
                    );
                }
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `rpushx list_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                const key3 = uuidv4();

                expect(await client.rpush(key1, ["0"])).toEqual(1);
                expect(await client.rpushx(key1, ["1", "2", "3"])).toEqual(4);
                expect(await client.lrange(key1, 0, -1)).toEqual([
                    "0",
                    "1",
                    "2",
                    "3",
                ]);

                expect(await client.rpushx(key2, ["1"])).toEqual(0);
                expect(await client.lrange(key2, 0, -1)).toEqual([]);

                // Key exists, but is not a list
                expect(await client.set(key3, "bar"));
                await expect(client.rpushx(key3, ["_"])).rejects.toThrow(
                    RequestError,
                );

                // Empty element list
                await expect(client.rpushx(key2, [])).rejects.toThrow(
                    RequestError,
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `sadd, srem, scard and smembers with existing set_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const valueList = ["member1", "member2", "member3", "member4"];
                expect(await client.sadd(key, valueList)).toEqual(4);
                expect(
                    await client.srem(key, ["member3", "nonExistingMember"]),
                ).toEqual(1);
                /// compare the 2 sets.
                expect(await client.smembers(key)).toEqual(
                    new Set(["member1", "member2", "member4"]),
                );
                expect(await client.srem(key, ["member1"])).toEqual(1);
                expect(await client.scard(key)).toEqual(2);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `smove test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = "{key}" + uuidv4();
                const key2 = "{key}" + uuidv4();
                const key3 = "{key}" + uuidv4();
                const string_key = "{key}" + uuidv4();
                const non_existing_key = "{key}" + uuidv4();

                expect(await client.sadd(key1, ["1", "2", "3"])).toEqual(3);
                expect(await client.sadd(key2, ["2", "3"])).toEqual(2);

                // move an element
                expect(await client.smove(key1, key2, "1"));
                expect(await client.smembers(key1)).toEqual(
                    new Set(["2", "3"]),
                );
                expect(await client.smembers(key2)).toEqual(
                    new Set(["1", "2", "3"]),
                );

                // moved element already exists in the destination set
                expect(await client.smove(key2, key1, "2"));
                expect(await client.smembers(key1)).toEqual(
                    new Set(["2", "3"]),
                );
                expect(await client.smembers(key2)).toEqual(
                    new Set(["1", "3"]),
                );

                // attempt to move from a non-existing key
                expect(await client.smove(non_existing_key, key1, "4")).toEqual(
                    false,
                );
                expect(await client.smembers(key1)).toEqual(
                    new Set(["2", "3"]),
                );

                // move to a new set
                expect(await client.smove(key1, key3, "2"));
                expect(await client.smembers(key1)).toEqual(new Set(["3"]));
                expect(await client.smembers(key3)).toEqual(new Set(["2"]));

                // attempt to move a missing element
                expect(await client.smove(key1, key3, "42")).toEqual(false);
                expect(await client.smembers(key1)).toEqual(new Set(["3"]));
                expect(await client.smembers(key3)).toEqual(new Set(["2"]));

                // move missing element to missing key
                expect(
                    await client.smove(key1, non_existing_key, "42"),
                ).toEqual(false);
                expect(await client.smembers(key1)).toEqual(new Set(["3"]));
                expect(await client.type(non_existing_key)).toEqual("none");

                // key exists, but it is not a set
                expect(await client.set(string_key, "value")).toEqual("OK");
                await expect(
                    client.smove(string_key, key1, "_"),
                ).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `srem, scard and smembers with non existing key_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                expect(await client.srem("nonExistingKey", ["member"])).toEqual(
                    0,
                );
                expect(await client.scard("nonExistingKey")).toEqual(0);
                expect(await client.smembers("nonExistingKey")).toEqual(
                    new Set(),
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `sadd, srem, scard and smembers with with key that holds a value that is not a set_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                expect(await client.set(key, "foo")).toEqual("OK");

                try {
                    expect(await client.sadd(key, ["bar"])).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "Operation against a key holding the wrong kind of value",
                    );
                }

                try {
                    expect(await client.srem(key, ["bar"])).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "Operation against a key holding the wrong kind of value",
                    );
                }

                try {
                    expect(await client.scard(key)).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "Operation against a key holding the wrong kind of value",
                    );
                }

                try {
                    expect(await client.smembers(key)).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "Operation against a key holding the wrong kind of value",
                    );
                }
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `sinter test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = `{key}-1-${uuidv4()}`;
                const key2 = `{key}-2-${uuidv4()}`;
                const non_existing_key = `{key}`;
                const member1_list = ["a", "b", "c", "d"];
                const member2_list = ["c", "d", "e"];

                // positive test case
                expect(await client.sadd(key1, member1_list)).toEqual(4);
                expect(await client.sadd(key2, member2_list)).toEqual(3);
                expect(await client.sinter([key1, key2])).toEqual(
                    new Set(["c", "d"]),
                );

                // invalid argument - key list must not be empty
                try {
                    expect(await client.sinter([])).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "ResponseError: wrong number of arguments",
                    );
                }

                // non-existing key returns empty set
                expect(await client.sinter([key1, non_existing_key])).toEqual(
                    new Set(),
                );

                // non-set key
                expect(await client.set(key2, "value")).toEqual("OK");

                try {
                    expect(await client.sinter([key2])).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "Operation against a key holding the wrong kind of value",
                    );
                }
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `sintercard test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                if (cluster.checkIfServerVersionLessThan("7.0.0")) {
                    return;
                }

                const key1 = `{key}-${uuidv4()}`;
                const key2 = `{key}-${uuidv4()}`;
                const nonExistingKey = `{key}-${uuidv4()}`;
                const stringKey = `{key}-${uuidv4()}`;
                const member1_list = ["a", "b", "c", "d"];
                const member2_list = ["b", "c", "d", "e"];

                expect(await client.sadd(key1, member1_list)).toEqual(4);
                expect(await client.sadd(key2, member2_list)).toEqual(4);

                expect(await client.sintercard([key1, key2])).toEqual(3);

                // returns limit as cardinality when the limit is reached partway through the computation
                const limit = 2;
                expect(await client.sintercard([key1, key2], limit)).toEqual(
                    limit,
                );

                // returns actual cardinality if limit is higher
                expect(await client.sintercard([key1, key2], 4)).toEqual(3);

                // one of the keys is empty, intersection is empty, cardinality equals 0
                expect(await client.sintercard([key1, nonExistingKey])).toEqual(
                    0,
                );

                expect(
                    await client.sintercard([nonExistingKey, nonExistingKey]),
                ).toEqual(0);
                expect(
                    await client.sintercard(
                        [nonExistingKey, nonExistingKey],
                        2,
                    ),
                ).toEqual(0);

                // invalid argument - key list must not be empty
                await expect(client.sintercard([])).rejects.toThrow(
                    RequestError,
                );

                // source key exists, but it is not a set
                expect(await client.set(stringKey, "foo")).toEqual("OK");
                await expect(
                    client.sintercard([key1, stringKey]),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `sinterstore test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = `{key}-1-${uuidv4()}`;
                const key2 = `{key}-2-${uuidv4()}`;
                const key3 = `{key}-3-${uuidv4()}`;
                const nonExistingKey = `{key}-4-${uuidv4()}`;
                const stringKey = `{key}-5-${uuidv4()}`;
                const member1_list = ["a", "b", "c"];
                const member2_list = ["c", "d", "e"];

                expect(await client.sadd(key1, member1_list)).toEqual(3);
                expect(await client.sadd(key2, member2_list)).toEqual(3);

                // store in a new key
                expect(await client.sinterstore(key3, [key1, key2])).toEqual(1);
                expect(await client.smembers(key3)).toEqual(new Set(["c"]));

                // overwrite existing set, which is also a source set
                expect(await client.sinterstore(key2, [key2, key3])).toEqual(1);
                expect(await client.smembers(key2)).toEqual(new Set(["c"]));

                // source set is the same as the existing set
                expect(await client.sinterstore(key2, [key2])).toEqual(1);
                expect(await client.smembers(key2)).toEqual(new Set(["c"]));

                // intersection with non-existing key
                expect(
                    await client.sinterstore(key1, [key2, nonExistingKey]),
                ).toEqual(0);
                expect(await client.smembers(key1)).toEqual(new Set());

                // invalid argument - key list must not be empty
                await expect(client.sinterstore(key3, [])).rejects.toThrow();

                // non-set key
                expect(await client.set(stringKey, "foo")).toEqual("OK");
                await expect(
                    client.sinterstore(key3, [stringKey]),
                ).rejects.toThrow();

                // overwrite non-set key
                expect(await client.sinterstore(stringKey, [key2])).toEqual(1);
                expect(await client.smembers(stringKey)).toEqual(new Set("c"));
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `sdiff test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = `{key}-1-${uuidv4()}`;
                const key2 = `{key}-2-${uuidv4()}`;
                const stringKey = `{key}-3-${uuidv4()}`;
                const nonExistingKey = `{key}-4-${uuidv4()}`;
                const member1_list = ["a", "b", "c"];
                const member2_list = ["c", "d", "e"];

                expect(await client.sadd(key1, member1_list)).toEqual(3);
                expect(await client.sadd(key2, member2_list)).toEqual(3);

                expect(await client.sdiff([key1, key2])).toEqual(
                    new Set(["a", "b"]),
                );
                expect(await client.sdiff([key2, key1])).toEqual(
                    new Set(["d", "e"]),
                );

                expect(await client.sdiff([key1, nonExistingKey])).toEqual(
                    new Set(["a", "b", "c"]),
                );
                expect(await client.sdiff([nonExistingKey, key1])).toEqual(
                    new Set(),
                );

                // invalid arg - key list must not be empty
                await expect(client.sdiff([])).rejects.toThrow();

                // key exists, but it is not a set
                expect(await client.set(stringKey, "foo")).toEqual("OK");
                await expect(client.sdiff([stringKey])).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `sdiffstore test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = `{key}-1-${uuidv4()}`;
                const key2 = `{key}-2-${uuidv4()}`;
                const key3 = `{key}-3-${uuidv4()}`;
                const stringKey = `{key}-4-${uuidv4()}`;
                const nonExistingKey = `{key}-5-${uuidv4()}`;
                const member1_list = ["a", "b", "c"];
                const member2_list = ["c", "d", "e"];

                expect(await client.sadd(key1, member1_list)).toEqual(3);
                expect(await client.sadd(key2, member2_list)).toEqual(3);

                // store diff in new key
                expect(await client.sdiffstore(key3, [key1, key2])).toEqual(2);
                expect(await client.smembers(key3)).toEqual(
                    new Set(["a", "b"]),
                );

                // overwrite existing set
                expect(await client.sdiffstore(key3, [key2, key1])).toEqual(2);
                expect(await client.smembers(key3)).toEqual(
                    new Set(["d", "e"]),
                );

                // overwrite one of the source sets
                expect(await client.sdiffstore(key3, [key2, key3])).toEqual(1);
                expect(await client.smembers(key3)).toEqual(new Set(["c"]));

                // diff between non-empty set and empty set
                expect(
                    await client.sdiffstore(key3, [key1, nonExistingKey]),
                ).toEqual(3);
                expect(await client.smembers(key3)).toEqual(
                    new Set(["a", "b", "c"]),
                );

                // diff between empty set and non-empty set
                expect(
                    await client.sdiffstore(key3, [nonExistingKey, key1]),
                ).toEqual(0);
                expect(await client.smembers(key3)).toEqual(new Set());

                // invalid argument - key list must not be empty
                await expect(client.sdiffstore(key3, [])).rejects.toThrow();

                // source key exists, but it is not a set
                expect(await client.set(stringKey, "foo")).toEqual("OK");
                await expect(
                    client.sdiffstore(key3, [stringKey]),
                ).rejects.toThrow();

                // overwrite a key holding a non-set value
                expect(
                    await client.sdiffstore(stringKey, [key1, key2]),
                ).toEqual(2);
                expect(await client.smembers(stringKey)).toEqual(
                    new Set(["a", "b"]),
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `sunion test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = `{key}:${uuidv4()}`;
                const key2 = `{key}:${uuidv4()}`;
                const stringKey = `{key}:${uuidv4()}`;
                const nonExistingKey = `{key}:${uuidv4()}`;
                const memberList1 = ["a", "b", "c"];
                const memberList2 = ["b", "c", "d", "e"];

                expect(await client.sadd(key1, memberList1)).toEqual(3);
                expect(await client.sadd(key2, memberList2)).toEqual(4);
                expect(await client.sunion([key1, key2])).toEqual(
                    new Set(["a", "b", "c", "d", "e"]),
                );

                // invalid argument - key list must not be empty
                await expect(client.sunion([])).rejects.toThrow();

                // non-existing key returns the set of existing keys
                expect(await client.sunion([key1, nonExistingKey])).toEqual(
                    new Set(memberList1),
                );

                // key exists, but it is not a set
                expect(await client.set(stringKey, "foo")).toEqual("OK");
                await expect(client.sunion([stringKey])).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `sunionstore test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = `{key}:${uuidv4()}`;
                const key2 = `{key}:${uuidv4()}`;
                const key3 = `{key}:${uuidv4()}`;
                const key4 = `{key}:${uuidv4()}`;
                const stringKey = `{key}:${uuidv4()}`;
                const nonExistingKey = `{key}:${uuidv4()}`;

                expect(await client.sadd(key1, ["a", "b", "c"])).toEqual(3);
                expect(await client.sadd(key2, ["c", "d", "e"])).toEqual(3);
                expect(await client.sadd(key3, ["e", "f", "g"])).toEqual(3);

                // store union in new key
                expect(await client.sunionstore(key4, [key1, key2])).toEqual(5);
                expect(await client.smembers(key4)).toEqual(
                    new Set(["a", "b", "c", "d", "e"]),
                );

                // overwrite existing set
                expect(await client.sunionstore(key1, [key4, key2])).toEqual(5);
                expect(await client.smembers(key1)).toEqual(
                    new Set(["a", "b", "c", "d", "e"]),
                );

                // overwrite one of the source keys
                expect(await client.sunionstore(key2, [key4, key2])).toEqual(5);
                expect(await client.smembers(key2)).toEqual(
                    new Set(["a", "b", "c", "d", "e"]),
                );

                // union with a non-existing key
                expect(
                    await client.sunionstore(key2, [nonExistingKey]),
                ).toEqual(0);
                expect(await client.smembers(key2)).toEqual(new Set());

                // invalid argument - key list must not be empty
                await expect(client.sunionstore(key4, [])).rejects.toThrow();

                // key exists, but it is not a set
                expect(await client.set(stringKey, "foo")).toEqual("OK");
                await expect(
                    client.sunionstore(key4, [stringKey, key1]),
                ).rejects.toThrow();

                // overwrite destination when destination is not a set
                expect(
                    await client.sunionstore(stringKey, [key1, key3]),
                ).toEqual(7);
                expect(await client.smembers(stringKey)).toEqual(
                    new Set(["a", "b", "c", "d", "e", "f", "g"]),
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `sismember test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                expect(await client.sadd(key1, ["member1"])).toEqual(1);
                expect(await client.sismember(key1, "member1")).toEqual(true);
                expect(
                    await client.sismember(key1, "nonExistingMember"),
                ).toEqual(false);
                expect(
                    await client.sismember("nonExistingKey", "member1"),
                ).toEqual(false);

                expect(await client.set(key2, "foo")).toEqual("OK");
                await expect(
                    client.sismember(key2, "member1"),
                ).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `smismember test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                if (cluster.checkIfServerVersionLessThan("6.2.0")) {
                    return;
                }

                const key = uuidv4();
                const stringKey = uuidv4();
                const nonExistingKey = uuidv4();

                expect(await client.sadd(key, ["a", "b"])).toEqual(2);
                expect(await client.smismember(key, ["b", "c"])).toEqual([
                    true,
                    false,
                ]);

                expect(await client.smismember(nonExistingKey, ["b"])).toEqual([
                    false,
                ]);

                // invalid argument - member list must not be empty
                await expect(client.smismember(key, [])).rejects.toThrow(
                    RequestError,
                );

                // key exists, but it is not a set
                expect(await client.set(stringKey, "foo")).toEqual("OK");
                await expect(
                    client.smismember(stringKey, ["a"]),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `spop and spopCount test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                let members = ["member1", "member2", "member3"];
                expect(await client.sadd(key, members)).toEqual(3);

                const result1 = await client.spop(key);
                expect(members).toContain(result1);

                members = members.filter((item) => item != result1);
                const result2 = await client.spopCount(key, 2);
                expect(result2).toEqual(new Set(members));
                expect(await client.spop("nonExistingKey")).toEqual(null);
                expect(await client.spopCount("nonExistingKey", 1)).toEqual(
                    new Set(),
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `srandmember and srandmemberCount test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const members = ["member1", "member2", "member3"];
                expect(await client.sadd(key, members)).toEqual(3);

                const result2 = await client.srandmember(key);
                expect(members).toContain(result2);
                expect(await client.srandmember("nonExistingKey")).toEqual(
                    null,
                );

                // unique values are expected as count is positive
                let result = await client.srandmemberCount(key, 4);
                expect(result.length).toEqual(3);
                expect(new Set(result)).toEqual(new Set(members));

                // duplicate values are expected as count is negative
                result = await client.srandmemberCount(key, -4);
                expect(result.length).toEqual(4);
                result.forEach((member) => {
                    expect(members).toContain(member);
                });

                // empty return values for non-existing or empty keys
                result = await client.srandmemberCount(key, 0);
                expect(result.length).toEqual(0);
                expect(result).toEqual([]);
                expect(
                    await client.srandmemberCount("nonExistingKey", 0),
                ).toEqual([]);

                expect(await client.set(key, "value")).toBe("OK");
                await expect(client.srandmember(key)).rejects.toThrow();
                await expect(client.srandmemberCount(key, 2)).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `exists with existing keys, an non existing key_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                const value = uuidv4();
                expect(await client.set(key1, value)).toEqual("OK");
                expect(await client.exists([key1])).toEqual(1);
                expect(await client.set(key2, value)).toEqual("OK");
                expect(
                    await client.exists([key1, "nonExistingKey", key2]),
                ).toEqual(2);
                expect(await client.exists([key1, key1])).toEqual(2);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `unlink multiple existing keys and an non existing key_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = "{key}" + uuidv4();
                const key2 = "{key}" + uuidv4();
                const key3 = "{key}" + uuidv4();
                const value = uuidv4();
                expect(await client.set(key1, value)).toEqual("OK");
                expect(await client.set(key2, value)).toEqual("OK");
                expect(await client.set(key3, value)).toEqual("OK");
                expect(
                    await client.unlink([key1, key2, "nonExistingKey", key3]),
                ).toEqual(3);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `expire, pexpire and ttl with positive timeout_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                const key = uuidv4();
                expect(await client.set(key, "foo")).toEqual("OK");
                expect(await client.expire(key, 10)).toEqual(true);
                expect(await client.ttl(key)).toBeLessThanOrEqual(10);
                /// set command clears the timeout.
                expect(await client.set(key, "bar")).toEqual("OK");
                const versionLessThan =
                    cluster.checkIfServerVersionLessThan("7.0.0");

                if (versionLessThan) {
                    expect(await client.pexpire(key, 10000)).toEqual(true);
                } else {
                    expect(
                        await client.pexpire(
                            key,
                            10000,
                            ExpireOptions.HasNoExpiry,
                        ),
                    ).toEqual(true);
                }

                expect(await client.ttl(key)).toBeLessThanOrEqual(10);

                /// TTL will be updated to the new value = 15
                if (versionLessThan) {
                    expect(await client.expire(key, 15)).toEqual(true);
                } else {
                    expect(
                        await client.expire(
                            key,
                            15,
                            ExpireOptions.HasExistingExpiry,
                        ),
                    ).toEqual(true);
                    expect(await client.expiretime(key)).toBeGreaterThan(
                        Math.floor(Date.now() / 1000),
                    );
                    expect(await client.pexpiretime(key)).toBeGreaterThan(
                        Date.now(),
                    );
                }

                expect(await client.ttl(key)).toBeLessThanOrEqual(15);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `expireAt, pexpireAt and ttl with positive timeout_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                const key = uuidv4();
                expect(await client.set(key, "foo")).toEqual("OK");
                expect(
                    await client.expireAt(
                        key,
                        Math.floor(Date.now() / 1000) + 10,
                    ),
                ).toEqual(true);
                expect(await client.ttl(key)).toBeLessThanOrEqual(10);
                const versionLessThan =
                    cluster.checkIfServerVersionLessThan("7.0.0");

                if (versionLessThan) {
                    expect(
                        await client.expireAt(
                            key,
                            Math.floor(Date.now() / 1000) + 50,
                        ),
                    ).toEqual(true);
                } else {
                    expect(
                        await client.expireAt(
                            key,
                            Math.floor(Date.now() / 1000) + 50,
                            ExpireOptions.NewExpiryGreaterThanCurrent,
                        ),
                    ).toEqual(true);
                }

                expect(await client.ttl(key)).toBeLessThanOrEqual(50);

                /// set command clears the timeout.
                expect(await client.set(key, "bar")).toEqual("OK");

                if (!versionLessThan) {
                    expect(
                        await client.pexpireAt(
                            key,
                            Date.now() + 50000,
                            ExpireOptions.HasExistingExpiry,
                        ),
                    ).toEqual(false);
                }
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `expire, pexpire, expireAt and pexpireAt with timestamp in the past or negative timeout_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                const key = uuidv4();
                expect(await client.set(key, "foo")).toEqual("OK");
                expect(await client.ttl(key)).toEqual(-1);
                expect(await client.expire(key, -10)).toEqual(true);
                expect(await client.ttl(key)).toEqual(-2);
                expect(await client.set(key, "foo")).toEqual("OK");
                expect(await client.pexpire(key, -10000)).toEqual(true);
                expect(await client.ttl(key)).toEqual(-2);
                expect(await client.set(key, "foo")).toEqual("OK");
                expect(
                    await client.expireAt(
                        key,
                        Math.floor(Date.now() / 1000) - 50, /// timeout in the past
                    ),
                ).toEqual(true);
                expect(await client.ttl(key)).toEqual(-2);
                expect(await client.set(key, "foo")).toEqual("OK");

                // no timeout set yet
                if (!cluster.checkIfServerVersionLessThan("7.0.0")) {
                    expect(await client.expiretime(key)).toEqual(-1);
                    expect(await client.pexpiretime(key)).toEqual(-1);
                }

                expect(
                    await client.pexpireAt(
                        key,
                        Date.now() - 50000, /// timeout in the past
                    ),
                ).toEqual(true);
                expect(await client.ttl(key)).toEqual(-2);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `expire, pexpire, expireAt, pexpireAt and ttl with non-existing key_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                const key = uuidv4();
                expect(await client.expire(key, 10)).toEqual(false);
                expect(await client.pexpire(key, 10000)).toEqual(false);
                expect(
                    await client.expireAt(
                        key,
                        Math.floor(Date.now() / 1000) + 50, /// timeout in the past
                    ),
                ).toEqual(false);
                expect(
                    await client.pexpireAt(
                        key,
                        Date.now() + 50000, /// timeout in the past
                    ),
                ).toEqual(false);
                expect(await client.ttl(key)).toEqual(-2);

                if (!cluster.checkIfServerVersionLessThan("7.0.0")) {
                    expect(await client.expiretime(key)).toEqual(-2);
                    expect(await client.pexpiretime(key)).toEqual(-2);
                }
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `script test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = Buffer.from(uuidv4());
                const key2 = Buffer.from(uuidv4());

                let script = new Script(Buffer.from("return 'Hello'"));
                expect(await client.invokeScript(script)).toEqual("Hello");

                script = new Script(
                    Buffer.from("return redis.call('SET', KEYS[1], ARGV[1])"),
                );
                expect(
                    await client.invokeScript(script, {
                        keys: [key1],
                        args: [Buffer.from("value1")],
                    }),
                ).toEqual("OK");

                /// Reuse the same script with different parameters.
                expect(
                    await client.invokeScript(script, {
                        keys: [key2],
                        args: [Buffer.from("value2")],
                    }),
                ).toEqual("OK");

                script = new Script(
                    Buffer.from("return redis.call('GET', KEYS[1])"),
                );
                expect(
                    await client.invokeScript(script, { keys: [key1] }),
                ).toEqual("value1");

                expect(
                    await client.invokeScript(script, { keys: [key2] }),
                ).toEqual("value2");
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `script test_binary_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();

                let script = new Script("return 'Hello'");
                expect(await client.invokeScript(script)).toEqual("Hello");

                script = new Script(
                    "return redis.call('SET', KEYS[1], ARGV[1])",
                );
                expect(
                    await client.invokeScript(script, {
                        keys: [key1],
                        args: ["value1"],
                    }),
                ).toEqual("OK");

                /// Reuse the same script with different parameters.
                expect(
                    await client.invokeScript(script, {
                        keys: [key2],
                        args: ["value2"],
                    }),
                ).toEqual("OK");

                script = new Script("return redis.call('GET', KEYS[1])");
                expect(
                    await client.invokeScript(script, { keys: [key1] }),
                ).toEqual("value1");

                expect(
                    await client.invokeScript(script, { keys: [key2] }),
                ).toEqual("value2");
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zadd and zaddIncr test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const membersScores = { one: 1, two: 2, three: 3 };
                const newMembersScores = { one: 2, two: 3 };

                expect(await client.zadd(key, membersScores)).toEqual(3);
                expect(await client.zaddIncr(key, "one", 2)).toEqual(3.0);
                expect(
                    await client.zadd(key, newMembersScores, { changed: true }),
                ).toEqual(2);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zadd and zaddIncr with NX XX test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const membersScores = { one: 1, two: 2, three: 3 };
                expect(
                    await client.zadd(key, membersScores, {
                        conditionalChange: ConditionalChange.ONLY_IF_EXISTS,
                    }),
                ).toEqual(0);

                expect(
                    await client.zadd(key, membersScores, {
                        conditionalChange:
                            ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
                    }),
                ).toEqual(3);

                expect(
                    await client.zaddIncr(key, "one", 5.0, {
                        conditionalChange:
                            ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
                    }),
                ).toEqual(null);

                expect(
                    await client.zaddIncr(key, "one", 5.0, {
                        conditionalChange: ConditionalChange.ONLY_IF_EXISTS,
                    }),
                ).toEqual(6.0);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zadd and zaddIncr with GT LT test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const membersScores = { one: -3, two: 2, three: 3 };

                expect(await client.zadd(key, membersScores)).toEqual(3);
                membersScores["one"] = 10;

                expect(
                    await client.zadd(key, membersScores, {
                        updateOptions: UpdateByScore.GREATER_THAN,
                        changed: true,
                    }),
                ).toEqual(1);

                expect(
                    await client.zadd(key, membersScores, {
                        updateOptions: UpdateByScore.LESS_THAN,
                        changed: true,
                    }),
                ).toEqual(0);

                expect(
                    await client.zaddIncr(key, "one", -3.0, {
                        updateOptions: UpdateByScore.LESS_THAN,
                    }),
                ).toEqual(7.0);

                expect(
                    await client.zaddIncr(key, "one", -3.0, {
                        updateOptions: UpdateByScore.GREATER_THAN,
                    }),
                ).toEqual(null);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zrem test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const membersScores = { one: 1, two: 2, three: 3 };
                expect(await client.zadd(key, membersScores)).toEqual(3);
                expect(await client.zrem(key, ["one"])).toEqual(1);
                expect(await client.zrem(key, ["one", "two", "three"])).toEqual(
                    2,
                );
                expect(
                    await client.zrem("non_existing_set", ["member"]),
                ).toEqual(0);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zcard test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const membersScores = { one: 1, two: 2, three: 3 };
                expect(await client.zadd(key, membersScores)).toEqual(3);
                expect(await client.zcard(key)).toEqual(3);
                expect(await client.zrem(key, ["one"])).toEqual(1);
                expect(await client.zcard(key)).toEqual(2);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zintercard test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                if (cluster.checkIfServerVersionLessThan("7.0.0")) {
                    return;
                }

                const key1 = `{key}:${uuidv4()}`;
                const key2 = `{key}:${uuidv4()}`;
                const stringKey = `{key}:${uuidv4()}`;
                const nonExistingKey = `{key}:${uuidv4()}`;
                const memberScores1 = { one: 1, two: 2, three: 3 };
                const memberScores2 = { two: 2, three: 3, four: 4 };

                expect(await client.zadd(key1, memberScores1)).toEqual(3);
                expect(await client.zadd(key2, memberScores2)).toEqual(3);

                expect(await client.zintercard([key1, key2])).toEqual(2);
                expect(await client.zintercard([key1, nonExistingKey])).toEqual(
                    0,
                );

                expect(await client.zintercard([key1, key2], 0)).toEqual(2);
                expect(await client.zintercard([key1, key2], 1)).toEqual(1);
                expect(await client.zintercard([key1, key2], 2)).toEqual(2);

                // invalid argument - key list must not be empty
                await expect(client.zintercard([])).rejects.toThrow();

                // invalid argument - limit must be non-negative
                await expect(
                    client.zintercard([key1, key2], -1),
                ).rejects.toThrow();

                // key exists, but it is not a sorted set
                expect(await client.set(stringKey, "foo")).toEqual("OK");
                await expect(client.zintercard([stringKey])).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zdiff test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                if (cluster.checkIfServerVersionLessThan("6.2.0")) {
                    return;
                }

                const key1 = `{key}-${uuidv4()}`;
                const key2 = `{key}-${uuidv4()}`;
                const key3 = `{key}-${uuidv4()}`;
                const nonExistingKey = `{key}-${uuidv4()}`;
                const stringKey = `{key}-${uuidv4()}`;

                const entries1 = {
                    one: 1.0,
                    two: 2.0,
                    three: 3.0,
                };
                const entries2 = { two: 2.0 };
                const entries3 = {
                    one: 1.0,
                    two: 2.0,
                    three: 3.0,
                    four: 4.0,
                };

                expect(await client.zadd(key1, entries1)).toEqual(3);
                expect(await client.zadd(key2, entries2)).toEqual(1);
                expect(await client.zadd(key3, entries3)).toEqual(4);

                expect(await client.zdiff([key1, key2])).toEqual([
                    "one",
                    "three",
                ]);
                expect(await client.zdiff([key1, key3])).toEqual([]);
                expect(await client.zdiff([nonExistingKey, key3])).toEqual([]);

                let result = await client.zdiffWithScores([key1, key2]);
                const expected = {
                    one: 1.0,
                    three: 3.0,
                };
                expect(compareMaps(result, expected)).toBe(true);

                result = await client.zdiffWithScores([key1, key3]);
                expect(compareMaps(result, {})).toBe(true);

                result = await client.zdiffWithScores([nonExistingKey, key3]);
                expect(compareMaps(result, {})).toBe(true);

                // invalid arg - key list must not be empty
                await expect(client.zdiff([])).rejects.toThrow(RequestError);
                await expect(client.zdiffWithScores([])).rejects.toThrow(
                    RequestError,
                );

                // key exists, but it is not a sorted set
                expect(await client.set(stringKey, "foo")).toEqual("OK");
                await expect(client.zdiff([stringKey, key1])).rejects.toThrow();
                await expect(
                    client.zdiffWithScores([stringKey, key1]),
                ).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zdiffstore test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                if (cluster.checkIfServerVersionLessThan("6.2.0")) {
                    return;
                }

                const key1 = `{key}-${uuidv4()}`;
                const key2 = `{key}-${uuidv4()}`;
                const key3 = `{key}-${uuidv4()}`;
                const key4 = `{key}-${uuidv4()}`;
                const nonExistingKey = `{key}-${uuidv4()}`;
                const stringKey = `{key}-${uuidv4()}`;

                const entries1 = {
                    one: 1.0,
                    two: 2.0,
                    three: 3.0,
                };
                const entries2 = { two: 2.0 };
                const entries3 = {
                    one: 1.0,
                    two: 2.0,
                    three: 3.0,
                    four: 4.0,
                };

                expect(await client.zadd(key1, entries1)).toEqual(3);
                expect(await client.zadd(key2, entries2)).toEqual(1);
                expect(await client.zadd(key3, entries3)).toEqual(4);

                expect(await client.zdiffstore(key4, [key1, key2])).toEqual(2);
                const result1 = await client.zrangeWithScores(key4, {
                    start: 0,
                    stop: -1,
                });
                const expected1 = { one: 1.0, three: 3.0 };
                expect(compareMaps(result1, expected1)).toBe(true);

                expect(
                    await client.zdiffstore(key4, [key3, key2, key1]),
                ).toEqual(1);
                const result2 = await client.zrangeWithScores(key4, {
                    start: 0,
                    stop: -1,
                });
                expect(compareMaps(result2, { four: 4.0 })).toBe(true);

                expect(await client.zdiffstore(key4, [key1, key3])).toEqual(0);
                const result3 = await client.zrangeWithScores(key4, {
                    start: 0,
                    stop: -1,
                });
                expect(compareMaps(result3, {})).toBe(true);

                expect(
                    await client.zdiffstore(key4, [nonExistingKey, key1]),
                ).toEqual(0);
                const result4 = await client.zrangeWithScores(key4, {
                    start: 0,
                    stop: -1,
                });
                expect(compareMaps(result4, {})).toBe(true);

                // invalid arg - key list must not be empty
                await expect(client.zdiffstore(key4, [])).rejects.toThrow(
                    RequestError,
                );

                // key exists, but it is not a sorted set
                expect(await client.set(stringKey, "foo")).toEqual("OK");
                await expect(
                    client.zdiffstore(key4, [stringKey, key1]),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zscore test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                const membersScores = { one: 1, two: 2, three: 3 };
                expect(await client.zadd(key1, membersScores)).toEqual(3);
                expect(await client.zscore(key1, "one")).toEqual(1.0);
                expect(await client.zscore(key1, "nonExistingMember")).toEqual(
                    null,
                );
                expect(
                    await client.zscore("nonExistingKey", "nonExistingMember"),
                ).toEqual(null);

                expect(await client.set(key2, "foo")).toEqual("OK");
                await expect(client.zscore(key2, "foo")).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zmscore test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                if (cluster.checkIfServerVersionLessThan("6.2.0")) {
                    return;
                }

                const key1 = `{key}-${uuidv4()}`;
                const nonExistingKey = `{key}-${uuidv4()}`;
                const stringKey = `{key}-${uuidv4()}`;

                const entries = {
                    one: 1.0,
                    two: 2.0,
                    three: 3.0,
                };
                expect(await client.zadd(key1, entries)).toEqual(3);

                expect(
                    await client.zmscore(key1, ["one", "three", "two"]),
                ).toEqual([1.0, 3.0, 2.0]);
                expect(
                    await client.zmscore(key1, [
                        "one",
                        "nonExistingMember",
                        "two",
                        "nonExistingMember",
                    ]),
                ).toEqual([1.0, null, 2.0, null]);
                expect(await client.zmscore(nonExistingKey, ["one"])).toEqual([
                    null,
                ]);

                // invalid arg - member list must not be empty
                await expect(client.zmscore(key1, [])).rejects.toThrow(
                    RequestError,
                );

                // key exists, but it is not a sorted set
                expect(await client.set(stringKey, "foo")).toEqual("OK");
                await expect(
                    client.zmscore(stringKey, ["one"]),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zcount test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                const membersScores = { one: 1, two: 2, three: 3 };
                expect(await client.zadd(key1, membersScores)).toEqual(3);
                expect(
                    await client.zcount(
                        key1,
                        InfScoreBoundary.NegativeInfinity,
                        InfScoreBoundary.PositiveInfinity,
                    ),
                ).toEqual(3);
                expect(
                    await client.zcount(
                        key1,
                        { value: 1, isInclusive: false },
                        { value: 3, isInclusive: false },
                    ),
                ).toEqual(1);
                expect(
                    await client.zcount(
                        key1,
                        { value: 1, isInclusive: false },
                        { value: 3 },
                    ),
                ).toEqual(2);
                expect(
                    await client.zcount(
                        key1,
                        InfScoreBoundary.NegativeInfinity,
                        {
                            value: 3,
                        },
                    ),
                ).toEqual(3);
                expect(
                    await client.zcount(
                        key1,
                        InfScoreBoundary.PositiveInfinity,
                        {
                            value: 3,
                        },
                    ),
                ).toEqual(0);
                expect(
                    await client.zcount(
                        "nonExistingKey",
                        InfScoreBoundary.NegativeInfinity,
                        InfScoreBoundary.PositiveInfinity,
                    ),
                ).toEqual(0);

                expect(await client.set(key2, "foo")).toEqual("OK");
                await expect(
                    client.zcount(
                        key2,
                        InfScoreBoundary.NegativeInfinity,
                        InfScoreBoundary.PositiveInfinity,
                    ),
                ).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zrange by index test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const membersScores = { one: 1, two: 2, three: 3 };
                expect(await client.zadd(key, membersScores)).toEqual(3);

                expect(await client.zrange(key, { start: 0, stop: 1 })).toEqual(
                    ["one", "two"],
                );
                const result = await client.zrangeWithScores(key, {
                    start: 0,
                    stop: -1,
                });

                expect(
                    compareMaps(result, {
                        one: 1.0,
                        two: 2.0,
                        three: 3.0,
                    }),
                ).toBe(true);
                expect(
                    await client.zrange(key, { start: 0, stop: 1 }, true),
                ).toEqual(["three", "two"]);
                expect(await client.zrange(key, { start: 3, stop: 1 })).toEqual(
                    [],
                );
                expect(
                    await client.zrangeWithScores(key, { start: 3, stop: 1 }),
                ).toEqual({});
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zrange by score test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const membersScores = { one: 1, two: 2, three: 3 };
                expect(await client.zadd(key, membersScores)).toEqual(3);

                expect(
                    await client.zrange(key, {
                        start: InfScoreBoundary.NegativeInfinity,
                        stop: { value: 3, isInclusive: false },
                        type: "byScore",
                    }),
                ).toEqual(["one", "two"]);
                const result = await client.zrangeWithScores(key, {
                    start: InfScoreBoundary.NegativeInfinity,
                    stop: InfScoreBoundary.PositiveInfinity,
                    type: "byScore",
                });

                expect(
                    compareMaps(result, {
                        one: 1.0,
                        two: 2.0,
                        three: 3.0,
                    }),
                ).toBe(true);
                expect(
                    await client.zrange(
                        key,
                        {
                            start: { value: 3, isInclusive: false },
                            stop: InfScoreBoundary.NegativeInfinity,
                            type: "byScore",
                        },
                        true,
                    ),
                ).toEqual(["two", "one"]);

                expect(
                    await client.zrange(key, {
                        start: InfScoreBoundary.NegativeInfinity,
                        stop: InfScoreBoundary.PositiveInfinity,
                        limit: { offset: 1, count: 2 },
                        type: "byScore",
                    }),
                ).toEqual(["two", "three"]);

                expect(
                    await client.zrange(
                        key,
                        {
                            start: InfScoreBoundary.NegativeInfinity,
                            stop: { value: 3, isInclusive: false },
                            type: "byScore",
                        },
                        true,
                    ),
                ).toEqual([]);

                expect(
                    await client.zrange(key, {
                        start: InfScoreBoundary.PositiveInfinity,
                        stop: { value: 3, isInclusive: false },
                        type: "byScore",
                    }),
                ).toEqual([]);

                expect(
                    await client.zrangeWithScores(
                        key,
                        {
                            start: InfScoreBoundary.NegativeInfinity,
                            stop: { value: 3, isInclusive: false },
                            type: "byScore",
                        },
                        true,
                    ),
                ).toEqual({});

                expect(
                    await client.zrangeWithScores(key, {
                        start: InfScoreBoundary.PositiveInfinity,
                        stop: { value: 3, isInclusive: false },
                        type: "byScore",
                    }),
                ).toEqual({});
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zrange by lex test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const membersScores = { a: 1, b: 2, c: 3 };
                expect(await client.zadd(key, membersScores)).toEqual(3);

                expect(
                    await client.zrange(key, {
                        start: InfScoreBoundary.NegativeInfinity,
                        stop: { value: "c", isInclusive: false },
                        type: "byLex",
                    }),
                ).toEqual(["a", "b"]);

                expect(
                    await client.zrange(key, {
                        start: InfScoreBoundary.NegativeInfinity,
                        stop: InfScoreBoundary.PositiveInfinity,
                        limit: { offset: 1, count: 2 },
                        type: "byLex",
                    }),
                ).toEqual(["b", "c"]);

                expect(
                    await client.zrange(
                        key,
                        {
                            start: { value: "c", isInclusive: false },
                            stop: InfScoreBoundary.NegativeInfinity,
                            type: "byLex",
                        },
                        true,
                    ),
                ).toEqual(["b", "a"]);

                expect(
                    await client.zrange(
                        key,
                        {
                            start: InfScoreBoundary.NegativeInfinity,
                            stop: { value: "c", isInclusive: false },
                            type: "byLex",
                        },
                        true,
                    ),
                ).toEqual([]);

                expect(
                    await client.zrange(key, {
                        start: InfScoreBoundary.PositiveInfinity,
                        stop: { value: "c", isInclusive: false },
                        type: "byLex",
                    }),
                ).toEqual([]);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zrange different typesn of keys test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                expect(
                    await client.zrange("nonExistingKey", {
                        start: 0,
                        stop: 1,
                    }),
                ).toEqual([]);

                expect(
                    await client.zrangeWithScores("nonExistingKey", {
                        start: 0,
                        stop: 1,
                    }),
                ).toEqual({});

                expect(await client.set(key, "value")).toEqual("OK");

                await expect(
                    client.zrange(key, { start: 0, stop: 1 }),
                ).rejects.toThrow();

                await expect(
                    client.zrangeWithScores(key, { start: 0, stop: 1 }),
                ).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    // Zinterstore command tests
    async function zinterstoreWithAggregation(client: BaseClient) {
        const key1 = "{testKey}:1-" + uuidv4();
        const key2 = "{testKey}:2-" + uuidv4();
        const key3 = "{testKey}:3-" + uuidv4();
        const range = {
            start: 0,
            stop: -1,
        };

        const membersScores1 = { one: 1.0, two: 2.0 };
        const membersScores2 = { one: 2.0, two: 3.0, three: 4.0 };

        expect(await client.zadd(key1, membersScores1)).toEqual(2);
        expect(await client.zadd(key2, membersScores2)).toEqual(3);

        // Intersection results are aggregated by the MAX score of elements
        expect(await client.zinterstore(key3, [key1, key2], "MAX")).toEqual(2);
        const zinterstoreMapMax = await client.zrangeWithScores(key3, range);
        const expectedMapMax = {
            one: 2,
            two: 3,
        };
        expect(compareMaps(zinterstoreMapMax, expectedMapMax)).toBe(true);

        // Intersection results are aggregated by the MIN score of elements
        expect(await client.zinterstore(key3, [key1, key2], "MIN")).toEqual(2);
        const zinterstoreMapMin = await client.zrangeWithScores(key3, range);
        const expectedMapMin = {
            one: 1,
            two: 2,
        };
        expect(compareMaps(zinterstoreMapMin, expectedMapMin)).toBe(true);

        // Intersection results are aggregated by the SUM score of elements
        expect(await client.zinterstore(key3, [key1, key2], "SUM")).toEqual(2);
        const zinterstoreMapSum = await client.zrangeWithScores(key3, range);
        const expectedMapSum = {
            one: 3,
            two: 5,
        };
        expect(compareMaps(zinterstoreMapSum, expectedMapSum)).toBe(true);
    }

    async function zinterstoreBasicTest(client: BaseClient) {
        const key1 = "{testKey}:1-" + uuidv4();
        const key2 = "{testKey}:2-" + uuidv4();
        const key3 = "{testKey}:3-" + uuidv4();
        const range = {
            start: 0,
            stop: -1,
        };

        const membersScores1 = { one: 1.0, two: 2.0 };
        const membersScores2 = { one: 2.0, two: 3.0, three: 4.0 };

        expect(await client.zadd(key1, membersScores1)).toEqual(2);
        expect(await client.zadd(key2, membersScores2)).toEqual(3);

        expect(await client.zinterstore(key3, [key1, key2])).toEqual(2);
        const zinterstoreMap = await client.zrangeWithScores(key3, range);
        const expectedMap = {
            one: 3,
            two: 5,
        };
        expect(compareMaps(zinterstoreMap, expectedMap)).toBe(true);
    }

    async function zinterstoreWithWeightsAndAggregation(client: BaseClient) {
        const key1 = "{testKey}:1-" + uuidv4();
        const key2 = "{testKey}:2-" + uuidv4();
        const key3 = "{testKey}:3-" + uuidv4();
        const range = {
            start: 0,
            stop: -1,
        };
        const membersScores1 = { one: 1.0, two: 2.0 };
        const membersScores2 = { one: 2.0, two: 3.0, three: 4.0 };

        expect(await client.zadd(key1, membersScores1)).toEqual(2);
        expect(await client.zadd(key2, membersScores2)).toEqual(3);

        // Scores are multiplied by 2.0 for key1 and key2 during aggregation.
        expect(
            await client.zinterstore(
                key3,
                [
                    [key1, 2.0],
                    [key2, 2.0],
                ],
                "SUM",
            ),
        ).toEqual(2);
        const zinterstoreMapMultiplied = await client.zrangeWithScores(
            key3,
            range,
        );
        const expectedMapMultiplied = {
            one: 6,
            two: 10,
        };
        expect(
            compareMaps(zinterstoreMapMultiplied, expectedMapMultiplied),
        ).toBe(true);
    }

    async function zinterstoreEmptyCases(client: BaseClient) {
        const key1 = "{testKey}:1-" + uuidv4();
        const key2 = "{testKey}:2-" + uuidv4();

        // Non existing key
        expect(
            await client.zinterstore(key2, [
                key1,
                "{testKey}-non_existing_key",
            ]),
        ).toEqual(0);

        // Empty list check
        await expect(client.zinterstore("{xyz}", [])).rejects.toThrow();
    }

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zinterstore test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                await zinterstoreBasicTest(client);
                await zinterstoreWithAggregation(client);
                await zinterstoreWithWeightsAndAggregation(client);
                await zinterstoreEmptyCases(client);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `type test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                expect(await client.set(key, "value")).toEqual("OK");
                expect(await client.type(key)).toEqual("string");
                expect(await client.del([key])).toEqual(1);

                expect(await client.lpush(key, ["value"])).toEqual(1);
                expect(await client.type(key)).toEqual("list");
                expect(await client.del([key])).toEqual(1);

                expect(await client.sadd(key, ["value"])).toEqual(1);
                expect(await client.type(key)).toEqual("set");
                expect(await client.del([key])).toEqual(1);

                expect(await client.zadd(key, { member: 1.0 })).toEqual(1);
                expect(await client.type(key)).toEqual("zset");
                expect(await client.del([key])).toEqual(1);

                expect(await client.hset(key, { field: "value" })).toEqual(1);
                expect(await client.type(key)).toEqual("hash");
                expect(await client.del([key])).toEqual(1);

                await client.customCommand([
                    "XADD",
                    key,
                    "*",
                    "field",
                    "value",
                ]);
                expect(await client.type(key)).toEqual("stream");
                expect(await client.del([key])).toEqual(1);
                expect(await client.type(key)).toEqual("none");
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `echo test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const message = uuidv4();
                expect(await client.echo(message)).toEqual(message);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `strlen test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key1Value = uuidv4();
                const key1ValueLength = key1Value.length;
                expect(await client.set(key1, key1Value)).toEqual("OK");
                expect(await client.strlen(key1)).toEqual(key1ValueLength);

                expect(await client.strlen("nonExistKey")).toEqual(0);

                const listName = "myList";
                const listKey1Value = uuidv4();
                const listKey2Value = uuidv4();

                expect(
                    await client.lpush(listName, [
                        listKey1Value,
                        listKey2Value,
                    ]),
                ).toEqual(2);
                // An error is returned when key holds a non-string value
                await expect(client.strlen(listName)).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `lindex test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const listName = uuidv4();
                const listKey1Value = uuidv4();
                const listKey2Value = uuidv4();
                expect(
                    await client.lpush(listName, [
                        listKey1Value,
                        listKey2Value,
                    ]),
                ).toEqual(2);
                expect(await client.lindex(listName, 0)).toEqual(listKey2Value);
                expect(await client.lindex(listName, 1)).toEqual(listKey1Value);
                expect(await client.lindex("notExsitingList", 1)).toEqual(null);
                expect(await client.lindex(listName, 3)).toEqual(null);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `linsert test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const stringKey = uuidv4();
                const nonExistingKey = uuidv4();

                expect(await client.lpush(key1, ["4", "3", "2", "1"])).toEqual(
                    4,
                );
                expect(
                    await client.linsert(
                        key1,
                        InsertPosition.Before,
                        "2",
                        "1.5",
                    ),
                ).toEqual(5);
                expect(
                    await client.linsert(
                        key1,
                        InsertPosition.After,
                        "3",
                        "3.5",
                    ),
                ).toEqual(6);
                expect(await client.lrange(key1, 0, -1)).toEqual([
                    "1",
                    "1.5",
                    "2",
                    "3",
                    "3.5",
                    "4",
                ]);

                expect(
                    await client.linsert(
                        key1,
                        InsertPosition.Before,
                        "nonExistingPivot",
                        "4",
                    ),
                ).toEqual(-1);
                expect(
                    await client.linsert(
                        nonExistingKey,
                        InsertPosition.Before,
                        "pivot",
                        "elem",
                    ),
                ).toEqual(0);

                // key exists, but it is not a list
                expect(await client.set(stringKey, "value")).toEqual("OK");
                await expect(
                    client.linsert(stringKey, InsertPosition.Before, "a", "b"),
                ).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zpopmin test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const membersScores = { a: 1, b: 2, c: 3 };
                expect(await client.zadd(key, membersScores)).toEqual(3);
                expect(await client.zpopmin(key)).toEqual({ a: 1.0 });

                expect(
                    compareMaps(await client.zpopmin(key, 3), {
                        b: 2.0,
                        c: 3.0,
                    }),
                ).toBe(true);
                expect(await client.zpopmin(key)).toEqual({});
                expect(await client.set(key, "value")).toEqual("OK");
                await expect(client.zpopmin(key)).rejects.toThrow();
                expect(await client.zpopmin("notExsitingKey")).toEqual({});
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zpopmax test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const membersScores = { a: 1, b: 2, c: 3 };
                expect(await client.zadd(key, membersScores)).toEqual(3);
                expect(await client.zpopmax(key)).toEqual({ c: 3.0 });

                expect(
                    compareMaps(await client.zpopmax(key, 3), {
                        b: 2.0,
                        a: 1.0,
                    }),
                ).toBe(true);
                expect(await client.zpopmax(key)).toEqual({});
                expect(await client.set(key, "value")).toEqual("OK");
                await expect(client.zpopmax(key)).rejects.toThrow();
                expect(await client.zpopmax("notExsitingKey")).toEqual({});
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `bzpopmax test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster: RedisCluster) => {
                const key1 = "{key}-1" + uuidv4();
                const key2 = "{key}-2" + uuidv4();
                const key3 = "{key}-3" + uuidv4();

                expect(await client.zadd(key1, { a: 1.0, b: 1.5 })).toBe(2);
                expect(await client.zadd(key2, { c: 2.0 })).toBe(1);
                expect(await client.bzpopmax([key1, key2], 0.5)).toEqual([
                    key1,
                    "b",
                    1.5,
                ]);

                // nothing popped out / key does not exist
                expect(
                    await client.bzpopmax(
                        [key3],
                        cluster.checkIfServerVersionLessThan("6.0.0")
                            ? 1.0
                            : 0.001,
                    ),
                ).toBeNull();

                // pops from the second key
                expect(await client.bzpopmax([key3, key2], 0.5)).toEqual([
                    key2,
                    "c",
                    2.0,
                ]);

                // key exists but holds non-ZSET value
                expect(await client.set(key3, "bzpopmax")).toBe("OK");
                await expect(client.bzpopmax([key3], 0.5)).rejects.toThrow(
                    RequestError,
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `bzpopmin test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster: RedisCluster) => {
                const key1 = "{key}-1" + uuidv4();
                const key2 = "{key}-2" + uuidv4();
                const key3 = "{key}-3" + uuidv4();

                expect(await client.zadd(key1, { a: 1.0, b: 1.5 })).toBe(2);
                expect(await client.zadd(key2, { c: 2.0 })).toBe(1);
                expect(await client.bzpopmin([key1, key2], 0.5)).toEqual([
                    key1,
                    "a",
                    1.0,
                ]);

                // nothing popped out / key does not exist
                expect(
                    await client.bzpopmin(
                        [key3],
                        cluster.checkIfServerVersionLessThan("6.0.0")
                            ? 1.0
                            : 0.001,
                    ),
                ).toBeNull();

                // pops from the second key
                expect(await client.bzpopmin([key3, key2], 0.5)).toEqual([
                    key2,
                    "c",
                    2.0,
                ]);

                // key exists but holds non-ZSET value
                expect(await client.set(key3, "bzpopmin")).toBe("OK");
                await expect(client.bzpopmin([key3], 0.5)).rejects.toThrow(
                    RequestError,
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `Pttl test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                expect(await client.pttl(key)).toEqual(-2);

                expect(await client.set(key, "value")).toEqual("OK");
                expect(await client.pttl(key)).toEqual(-1);

                expect(await client.expire(key, 10)).toEqual(true);
                let result = await client.pttl(key);
                expect(result).toBeGreaterThan(0);
                expect(result).toBeLessThanOrEqual(10000);

                expect(
                    await client.expireAt(
                        key,
                        Math.floor(Date.now() / 1000) + 20,
                    ),
                ).toEqual(true);
                result = await client.pttl(key);
                expect(result).toBeGreaterThan(0);
                expect(result).toBeLessThanOrEqual(20000);

                expect(await client.pexpireAt(key, Date.now() + 30000)).toEqual(
                    true,
                );
                result = await client.pttl(key);
                expect(result).toBeGreaterThan(0);
                expect(result).toBeLessThanOrEqual(30000);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zremRangeByRank test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const membersScores = { one: 1, two: 2, three: 3 };
                expect(await client.zadd(key, membersScores)).toEqual(3);
                expect(await client.zremRangeByRank(key, 2, 1)).toEqual(0);
                expect(await client.zremRangeByRank(key, 0, 1)).toEqual(2);
                expect(await client.zremRangeByRank(key, 0, 10)).toEqual(1);
                expect(
                    await client.zremRangeByRank("nonExistingKey", 0, -1),
                ).toEqual(0);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zrank test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                const membersScores = { one: 1.5, two: 2, three: 3 };
                expect(await client.zadd(key1, membersScores)).toEqual(3);
                expect(await client.zrank(key1, "one")).toEqual(0);

                if (!cluster.checkIfServerVersionLessThan("7.2.0")) {
                    expect(await client.zrankWithScore(key1, "one")).toEqual([
                        0, 1.5,
                    ]);
                    expect(
                        await client.zrankWithScore(key1, "nonExistingMember"),
                    ).toEqual(null);
                    expect(
                        await client.zrankWithScore("nonExistingKey", "member"),
                    ).toEqual(null);
                }

                expect(await client.zrank(key1, "nonExistingMember")).toEqual(
                    null,
                );
                expect(await client.zrank("nonExistingKey", "member")).toEqual(
                    null,
                );

                expect(await client.set(key2, "value")).toEqual("OK");
                await expect(client.zrank(key2, "member")).rejects.toThrow();
            }, protocol);
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zrevrank test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                const key = uuidv4();
                const nonSetKey = uuidv4();
                const membersScores = { one: 1.5, two: 2, three: 3 };
                expect(await client.zadd(key, membersScores)).toEqual(3);
                expect(await client.zrevrank(key, "three")).toEqual(0);

                if (!cluster.checkIfServerVersionLessThan("7.2.0")) {
                    expect(await client.zrevrankWithScore(key, "one")).toEqual([
                        2, 1.5,
                    ]);
                    expect(
                        await client.zrevrankWithScore(
                            key,
                            "nonExistingMember",
                        ),
                    ).toBeNull();
                    expect(
                        await client.zrevrankWithScore(
                            "nonExistingKey",
                            "member",
                        ),
                    ).toBeNull();
                }

                expect(
                    await client.zrevrank(key, "nonExistingMember"),
                ).toBeNull();
                expect(
                    await client.zrevrank("nonExistingKey", "member"),
                ).toBeNull();

                // Key exists, but is not a sorted set
                expect(await client.set(nonSetKey, "value")).toEqual("OK");
                await expect(
                    client.zrevrank(nonSetKey, "member"),
                ).rejects.toThrow();
            }, protocol);
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `test brpop test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                expect(
                    await client.rpush("brpop-test", ["foo", "bar", "baz"]),
                ).toEqual(3);
                // Test basic usage
                expect(await client.brpop(["brpop-test"], 0.1)).toEqual([
                    "brpop-test",
                    "baz",
                ]);
                // Delete all values from list
                expect(await client.del(["brpop-test"])).toEqual(1);
                // Test null return when key doesn't exist
                expect(await client.brpop(["brpop-test"], 0.1)).toEqual(null);
                // key exists, but it is not a list
                await client.set("foo", "bar");
                await expect(client.brpop(["foo"], 0.1)).rejects.toThrow();

                // Same-slot requirement
                if (client instanceof GlideClusterClient) {
                    try {
                        expect(
                            await client.brpop(["abc", "zxy", "lkn"], 0.1),
                        ).toThrow();
                    } catch (e) {
                        expect((e as Error).message.toLowerCase()).toMatch(
                            "crossslot",
                        );
                    }
                }
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `test blpop test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                expect(
                    await client.rpush("blpop-test", ["foo", "bar", "baz"]),
                ).toEqual(3);
                // Test basic usage
                expect(await client.blpop(["blpop-test"], 0.1)).toEqual([
                    "blpop-test",
                    "foo",
                ]);
                // Delete all values from list
                expect(await client.del(["blpop-test"])).toEqual(1);
                // Test null return when key doesn't exist
                expect(await client.blpop(["blpop-test"], 0.1)).toEqual(null);
                // key exists, but it is not a list
                await client.set("foo", "bar");
                await expect(client.blpop(["foo"], 0.1)).rejects.toThrow();

                // Same-slot requirement
                if (client instanceof GlideClusterClient) {
                    try {
                        expect(
                            await client.blpop(["abc", "zxy", "lkn"], 0.1),
                        ).toThrow();
                    } catch (e) {
                        expect((e as Error).message.toLowerCase()).toMatch(
                            "crossslot",
                        );
                    }
                }
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `persist test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                expect(await client.set(key, "foo")).toEqual("OK");
                expect(await client.persist(key)).toEqual(false);

                expect(await client.expire(key, 10)).toEqual(true);
                expect(await client.persist(key)).toEqual(true);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `streams add, trim, and len test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const nonExistingKey = uuidv4();
                const stringKey = uuidv4();
                const field1 = uuidv4();
                const field2 = uuidv4();

                const nullResult = await client.xadd(
                    key,
                    [
                        [field1, "foo"],
                        [field2, "bar"],
                    ],
                    {
                        makeStream: false,
                    },
                );
                expect(nullResult).toBeNull();

                const timestamp1 = await client.xadd(
                    key,
                    [
                        [field1, "foo1"],
                        [field2, "bar1"],
                    ],
                    { id: "0-1" },
                );
                expect(timestamp1).toEqual("0-1");
                expect(
                    await client.xadd(key, [
                        [field1, "foo2"],
                        [field2, "bar2"],
                    ]),
                ).not.toBeNull();
                expect(await client.xlen(key)).toEqual(2);

                // this will trim the first entry.
                const id = await client.xadd(
                    key,
                    [
                        [field1, "foo3"],
                        [field2, "bar3"],
                    ],
                    {
                        trim: {
                            method: "maxlen",
                            threshold: 2,
                            exact: true,
                        },
                    },
                );
                expect(id).not.toBeNull();
                expect(await client.xlen(key)).toEqual(2);

                // this will trim the 2nd entry.
                expect(
                    await client.xadd(
                        key,
                        [
                            [field1, "foo4"],
                            [field2, "bar4"],
                        ],
                        {
                            trim: {
                                method: "minid",
                                threshold: id as string,
                                exact: true,
                            },
                        },
                    ),
                ).not.toBeNull();
                expect(await client.xlen(key)).toEqual(2);

                expect(
                    await client.xtrim(key, {
                        method: "maxlen",
                        threshold: 1,
                        exact: true,
                    }),
                ).toEqual(1);
                expect(await client.xlen(key)).toEqual(1);

                expect(
                    await client.xtrim(key, {
                        method: "maxlen",
                        threshold: 0,
                        exact: true,
                    }),
                ).toEqual(1);
                // Unlike other Redis collection types, stream keys still exist even after removing all entries
                expect(await client.exists([key])).toEqual(1);
                expect(await client.xlen(key)).toEqual(0);

                expect(
                    await client.xtrim(nonExistingKey, {
                        method: "maxlen",
                        threshold: 1,
                        exact: true,
                    }),
                ).toEqual(0);
                expect(await client.xlen(nonExistingKey)).toEqual(0);

                // key exists, but it is not a stream
                expect(await client.set(stringKey, "foo")).toEqual("OK");
                await expect(
                    client.xtrim(stringKey, {
                        method: "maxlen",
                        threshold: 1,
                        exact: true,
                    }),
                ).rejects.toThrow();
                await expect(client.xlen(stringKey)).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zremRangeByLex test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const stringKey = uuidv4();
                const membersScores = { a: 1, b: 2, c: 3, d: 4 };
                expect(await client.zadd(key, membersScores)).toEqual(4);

                expect(
                    await client.zremRangeByLex(
                        key,
                        { value: "a", isInclusive: false },
                        { value: "c" },
                    ),
                ).toEqual(2);

                expect(
                    await client.zremRangeByLex(
                        key,
                        { value: "d" },
                        InfScoreBoundary.PositiveInfinity,
                    ),
                ).toEqual(1);

                // MinLex > MaxLex
                expect(
                    await client.zremRangeByLex(
                        key,
                        { value: "a" },
                        InfScoreBoundary.NegativeInfinity,
                    ),
                ).toEqual(0);

                expect(
                    await client.zremRangeByLex(
                        "nonExistingKey",
                        InfScoreBoundary.NegativeInfinity,
                        InfScoreBoundary.PositiveInfinity,
                    ),
                ).toEqual(0);

                // Key exists, but it is not a set
                expect(await client.set(stringKey, "foo")).toEqual("OK");
                await expect(
                    client.zremRangeByLex(
                        stringKey,
                        InfScoreBoundary.NegativeInfinity,
                        InfScoreBoundary.PositiveInfinity,
                    ),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zremRangeByScore test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const membersScores = { one: 1, two: 2, three: 3 };
                expect(await client.zadd(key, membersScores)).toEqual(3);

                expect(
                    await client.zremRangeByScore(
                        key,
                        { value: 1, isInclusive: false },
                        { value: 2 },
                    ),
                ).toEqual(1);

                expect(
                    await client.zremRangeByScore(
                        key,
                        { value: 1 },
                        InfScoreBoundary.NegativeInfinity,
                    ),
                ).toEqual(0);

                expect(
                    await client.zremRangeByScore(
                        "nonExistingKey",
                        InfScoreBoundary.NegativeInfinity,
                        InfScoreBoundary.PositiveInfinity,
                    ),
                ).toEqual(0);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zlexcount test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const stringKey = uuidv4();
                const membersScores = { a: 1, b: 2, c: 3 };
                expect(await client.zadd(key, membersScores)).toEqual(3);

                // In range negative to positive infinity.
                expect(
                    await client.zlexcount(
                        key,
                        InfScoreBoundary.NegativeInfinity,
                        InfScoreBoundary.PositiveInfinity,
                    ),
                ).toEqual(3);

                // In range a (exclusive) to positive infinity
                expect(
                    await client.zlexcount(
                        key,
                        { value: "a", isInclusive: false },
                        InfScoreBoundary.PositiveInfinity,
                    ),
                ).toEqual(2);

                // In range negative infinity to c (inclusive)
                expect(
                    await client.zlexcount(
                        key,
                        InfScoreBoundary.NegativeInfinity,
                        {
                            value: "c",
                            isInclusive: true,
                        },
                    ),
                ).toEqual(3);

                // Incorrect range start > end
                expect(
                    await client.zlexcount(
                        key,
                        InfScoreBoundary.PositiveInfinity,
                        {
                            value: "c",
                            isInclusive: true,
                        },
                    ),
                ).toEqual(0);

                // Non-existing key
                expect(
                    await client.zlexcount(
                        "non_existing_key",
                        InfScoreBoundary.NegativeInfinity,
                        InfScoreBoundary.PositiveInfinity,
                    ),
                ).toEqual(0);

                // Key exists, but it is not a set
                expect(await client.set(stringKey, "foo")).toEqual("OK");
                await expect(
                    client.zlexcount(
                        stringKey,
                        InfScoreBoundary.NegativeInfinity,
                        InfScoreBoundary.PositiveInfinity,
                    ),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "time test_%p",
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                // Take the time now, convert to 10 digits and subtract 1 second
                const now = Math.floor(new Date().getTime() / 1000 - 1);
                const result = (await client.time()) as [string, string];
                expect(result?.length).toEqual(2);
                expect(Number(result?.at(0))).toBeGreaterThan(now);
                // Test its not more than 1 second
                expect(Number(result?.at(1))).toBeLessThan(1000000);
            }, protocol);
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `streams read test_%p`,
        async () => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = `{${key1}}${uuidv4()}`;
                const field1 = "foo";
                const field2 = "bar";
                const field3 = "barvaz";

                const timestamp_1_1 = await client.xadd(key1, [
                    [field1, "foo1"],
                    [field3, "barvaz1"],
                ]);
                expect(timestamp_1_1).not.toBeNull();
                const timestamp_2_1 = await client.xadd(key2, [
                    [field2, "bar1"],
                ]);
                expect(timestamp_2_1).not.toBeNull();
                const timestamp_1_2 = await client.xadd(key1, [
                    [field1, "foo2"],
                ]);
                const timestamp_2_2 = await client.xadd(key2, [
                    [field2, "bar2"],
                ]);
                const timestamp_1_3 = await client.xadd(key1, [
                    [field1, "foo3"],
                    [field3, "barvaz3"],
                ]);
                const timestamp_2_3 = await client.xadd(key2, [
                    [field2, "bar3"],
                ]);

                const result = await client.xread(
                    {
                        [key1]: timestamp_1_1 as string,
                        [key2]: timestamp_2_1 as string,
                    },
                    {
                        block: 1,
                    },
                );

                const expected = {
                    [key1]: {
                        [timestamp_1_2 as string]: [[field1, "foo2"]],
                        [timestamp_1_3 as string]: [
                            [field1, "foo3"],
                            [field3, "barvaz3"],
                        ],
                    },
                    [key2]: {
                        [timestamp_2_2 as string]: [["bar", "bar2"]],
                        [timestamp_2_3 as string]: [["bar", "bar3"]],
                    },
                };
                expect(result).toEqual(expected);
            }, ProtocolVersion.RESP2);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "rename test_%p",
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                // Making sure both keys will be oart of the same slot
                const key = uuidv4() + "{123}";
                const newKey = uuidv4() + "{123}";
                await client.set(key, "value");
                await client.rename(key, newKey);
                const result = await client.get(newKey);
                expect(result).toEqual("value");
                // If key doesn't exist it should throw, it also test that key has successfully been renamed
                await expect(client.rename(key, newKey)).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "renamenx test_%p",
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = `{key}-1-${uuidv4()}`;
                const key2 = `{key}-2-${uuidv4()}`;
                const key3 = `{key}-3-${uuidv4()}`;

                // renamenx missing key
                try {
                    expect(await client.renamenx(key1, key2)).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch("no such key");
                }

                // renamenx a string
                await client.set(key1, "key1");
                await client.set(key3, "key3");
                // Test that renamenx can rename key1 to key2 (non-existing value)
                expect(await client.renamenx(key1, key2)).toEqual(true);
                // sanity check
                expect(await client.get(key2)).toEqual("key1");
                // Test that renamenx doesn't rename key2 to key3 (with an existing value)
                expect(await client.renamenx(key2, key3)).toEqual(false);
                // sanity check
                expect(await client.get(key3)).toEqual("key3");
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "pfadd test_%p",
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                expect(await client.pfadd(key, [])).toEqual(1);
                expect(await client.pfadd(key, ["one", "two"])).toEqual(1);
                expect(await client.pfadd(key, ["two"])).toEqual(0);
                expect(await client.pfadd(key, [])).toEqual(0);

                // key exists, but it is not a HyperLogLog
                expect(await client.set("foo", "value")).toEqual("OK");
                await expect(client.pfadd("foo", [])).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "pfcount test_%p",
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = `{key}-1-${uuidv4()}`;
                const key2 = `{key}-2-${uuidv4()}`;
                const key3 = `{key}-3-${uuidv4()}`;
                const stringKey = `{key}-4-${uuidv4()}`;
                const nonExistingKey = `{key}-5-${uuidv4()}`;

                expect(await client.pfadd(key1, ["a", "b", "c"])).toEqual(1);
                expect(await client.pfadd(key2, ["b", "c", "d"])).toEqual(1);
                expect(await client.pfcount([key1])).toEqual(3);
                expect(await client.pfcount([key2])).toEqual(3);
                expect(await client.pfcount([key1, key2])).toEqual(4);
                expect(
                    await client.pfcount([key1, key2, nonExistingKey]),
                ).toEqual(4);

                // empty HyperLogLog data set
                expect(await client.pfadd(key3, [])).toEqual(1);
                expect(await client.pfcount([key3])).toEqual(0);

                // invalid argument - key list must not be empty
                try {
                    expect(await client.pfcount([])).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "ResponseError: wrong number of arguments",
                    );
                }

                // key exists, but it is not a HyperLogLog
                expect(await client.set(stringKey, "value")).toEqual("OK");
                await expect(client.pfcount([stringKey])).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "pfmerget test_%p",
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = `{key}-1-${uuidv4()}`;
                const key2 = `{key}-2-${uuidv4()}`;
                const key3 = `{key}-3-${uuidv4()}`;
                const stringKey = `{key}-4-${uuidv4()}`;
                const nonExistingKey = `{key}-5-${uuidv4()}`;

                expect(await client.pfadd(key1, ["a", "b", "c"])).toEqual(1);
                expect(await client.pfadd(key2, ["b", "c", "d"])).toEqual(1);

                // merge into new HyperLogLog data set
                expect(await client.pfmerge(key3, [key1, key2])).toEqual("OK");
                expect(await client.pfcount([key3])).toEqual(4);

                // merge into existing HyperLogLog data set
                expect(await client.pfmerge(key1, [key2])).toEqual("OK");
                expect(await client.pfcount([key1])).toEqual(4);

                // non-existing source key
                expect(
                    await client.pfmerge(key2, [key1, nonExistingKey]),
                ).toEqual("OK");
                expect(await client.pfcount([key2])).toEqual(4);

                // empty source key list
                expect(await client.pfmerge(key1, [])).toEqual("OK");
                expect(await client.pfcount([key1])).toEqual(4);

                // source key exists, but it is not a HyperLogLog
                await client.set(stringKey, "foo");
                await expect(client.pfmerge(key3, [stringKey])).rejects.toThrow(
                    RequestError,
                );

                // destination key exists, but it is not a HyperLogLog
                await expect(client.pfmerge(stringKey, [key3])).rejects.toThrow(
                    RequestError,
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "setrange test_%p",
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const nonStringKey = uuidv4();

                // new key
                expect(await client.setrange(key, 0, "Hello World")).toBe(11);

                // existing key
                expect(await client.setrange(key, 6, "GLIDE")).toBe(11);
                expect(await client.get(key)).toEqual("Hello GLIDE");

                // offset > len
                expect(await client.setrange(key, 15, "GLIDE")).toBe(20);
                expect(await client.get(key)).toEqual(
                    "Hello GLIDE\0\0\0\0GLIDE",
                );

                // non-string key
                expect(await client.lpush(nonStringKey, ["_"])).toBe(1);
                await expect(
                    client.setrange(nonStringKey, 0, "_"),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "append test_%p",
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                const value = uuidv4();

                // Append on non-existing string(similar to SET)
                expect(await client.append(key1, value)).toBe(value.length);
                expect(await client.append(key1, value)).toBe(value.length * 2);
                expect(await client.get(key1)).toEqual(value.concat(value));

                // key exists but holding the wrong kind of value
                expect(await client.sadd(key2, ["a"])).toBe(1);
                await expect(client.append(key2, "_")).rejects.toThrow(
                    RequestError,
                );
            }, protocol);
        },
        config.timeout,
    );

    // Set command tests

    async function setWithExpiryOptions(client: BaseClient) {
        const key = uuidv4();
        const value = uuidv4();
        const setResWithExpirySetMilli = await client.set(key, value, {
            expiry: {
                type: "milliseconds",
                count: 500,
            },
        });
        expect(setResWithExpirySetMilli).toEqual("OK");
        const getWithExpirySetMilli = await client.get(key);
        expect(getWithExpirySetMilli).toEqual(value);

        const setResWithExpirySec = await client.set(key, value, {
            expiry: {
                type: "seconds",
                count: 1,
            },
        });
        expect(setResWithExpirySec).toEqual("OK");
        const getResWithExpirySec = await client.get(key);
        expect(getResWithExpirySec).toEqual(value);

        const setWithUnixSec = await client.set(key, value, {
            expiry: {
                type: "unixSeconds",
                count: Math.floor(Date.now() / 1000) + 1,
            },
        });
        expect(setWithUnixSec).toEqual("OK");
        const getWithUnixSec = await client.get(key);
        expect(getWithUnixSec).toEqual(value);

        const setResWithExpiryKeep = await client.set(key, value, {
            expiry: "keepExisting",
        });
        expect(setResWithExpiryKeep).toEqual("OK");
        const getResWithExpiryKeep = await client.get(key);
        expect(getResWithExpiryKeep).toEqual(value);
        // wait for the key to expire base on the previous set
        let sleep = new Promise((resolve) => setTimeout(resolve, 1000));
        await sleep;
        const getResExpire = await client.get(key);
        // key should have expired
        expect(getResExpire).toEqual(null);
        const setResWithExpiryWithUmilli = await client.set(key, value, {
            expiry: {
                type: "unixMilliseconds",
                count: Date.now() + 1000,
            },
        });
        expect(setResWithExpiryWithUmilli).toEqual("OK");
        // wait for the key to expire
        sleep = new Promise((resolve) => setTimeout(resolve, 1001));
        await sleep;
        const getResWithExpiryWithUmilli = await client.get(key);
        // key should have expired
        expect(getResWithExpiryWithUmilli).toEqual(null);
    }

    async function setWithOnlyIfExistOptions(client: BaseClient) {
        const key = uuidv4();
        const value = uuidv4();
        const setKey = await client.set(key, value);
        expect(setKey).toEqual("OK");
        const getRes = await client.get(key);
        expect(getRes).toEqual(value);
        const setExistingKeyRes = await client.set(key, value, {
            conditionalSet: "onlyIfExists",
        });
        expect(setExistingKeyRes).toEqual("OK");
        const getExistingKeyRes = await client.get(key);
        expect(getExistingKeyRes).toEqual(value);

        const notExistingKeyRes = await client.set(key + 1, value, {
            conditionalSet: "onlyIfExists",
        });
        // key does not exist, so it should not be set
        expect(notExistingKeyRes).toEqual(null);
        const getNotExistingKey = await client.get(key + 1);
        // key should not have been set
        expect(getNotExistingKey).toEqual(null);
    }

    async function setWithOnlyIfNotExistOptions(client: BaseClient) {
        const key = uuidv4();
        const value = uuidv4();
        const notExistingKeyRes = await client.set(key, value, {
            conditionalSet: "onlyIfDoesNotExist",
        });
        // key does not exist, so it should be set
        expect(notExistingKeyRes).toEqual("OK");
        const getNotExistingKey = await client.get(key);
        // key should have been set
        expect(getNotExistingKey).toEqual(value);

        const existingKeyRes = await client.set(key, value, {
            conditionalSet: "onlyIfDoesNotExist",
        });
        // key exists, so it should not be set
        expect(existingKeyRes).toEqual(null);
        const getExistingKey = await client.get(key);
        // key should not have been set
        expect(getExistingKey).toEqual(value);
    }

    async function setWithGetOldOptions(client: BaseClient) {
        const key = uuidv4();
        const value = uuidv4();

        const setResGetNotExistOld = await client.set(key, value, {
            returnOldValue: true,
        });
        // key does not exist, so old value should be null
        expect(setResGetNotExistOld).toEqual(null);
        // key should have been set
        const getResGetNotExistOld = await client.get(key);
        expect(getResGetNotExistOld).toEqual(value);

        const setResGetExistOld = await client.set(key, value, {
            returnOldValue: true,
        });
        // key exists, so old value should be returned
        expect(setResGetExistOld).toEqual(value);
        // key should have been set
        const getResGetExistOld = await client.get(key);
        expect(getResGetExistOld).toEqual(value);
    }

    async function setWithAllOptions(client: BaseClient) {
        const key = uuidv4();
        const value = uuidv4();

        // set with multiple options:
        // * only apply SET if the key already exists
        // * expires after 1 second
        // * returns the old value
        const setResWithAllOptions = await client.set(key, value, {
            expiry: {
                type: "unixSeconds",
                count: Math.floor(Date.now() / 1000) + 1,
            },
            conditionalSet: "onlyIfExists",
            returnOldValue: true,
        });
        // key does not exist, so old value should be null
        expect(setResWithAllOptions).toEqual(null);
        // key does not exist, so SET should not have applied
        expect(await client.get(key)).toEqual(null);
    }

    async function testSetWithAllCombination(client: BaseClient) {
        const key = uuidv4();
        const value = uuidv4();
        const count = 2;
        const expiryCombination = [
            { type: "seconds", count },
            { type: "unixSeconds", count },
            { type: "unixMilliseconds", count },
            { type: "milliseconds", count },
            "keepExisting",
        ];
        let exist = false;

        for (const expiryVal of expiryCombination) {
            const setRes = await client.set(key, value, {
                expiry: expiryVal as
                    | "keepExisting"
                    | {
                          type:
                              | "seconds"
                              | "milliseconds"
                              | "unixSeconds"
                              | "unixMilliseconds";
                          count: number;
                      },
                conditionalSet: "onlyIfDoesNotExist",
            });

            if (exist == false) {
                expect(setRes).toEqual("OK");
                exist = true;
            } else {
                expect(setRes).toEqual(null);
            }

            const getRes = await client.get(key);
            expect(getRes).toEqual(value);
        }

        for (const expiryVal of expiryCombination) {
            const setRes = await client.set(key, value, {
                expiry: expiryVal as
                    | "keepExisting"
                    | {
                          type:
                              | "seconds"
                              | "milliseconds"
                              | "unixSeconds"
                              | "unixMilliseconds";
                          count: number;
                      },

                conditionalSet: "onlyIfExists",
                returnOldValue: true,
            });

            expect(setRes).toBeDefined();
        }
    }

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "Set commands with options test_%p",
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                await setWithExpiryOptions(client);
                await setWithOnlyIfExistOptions(client);
                await setWithOnlyIfNotExistOptions(client);
                await setWithGetOldOptions(client);
                await setWithAllOptions(client);
                await testSetWithAllCombination(client);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "object encoding test_%p",
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                const string_key = uuidv4();
                const list_key = uuidv4();
                const hashtable_key = uuidv4();
                const intset_key = uuidv4();
                const set_listpack_key = uuidv4();
                const hash_hashtable_key = uuidv4();
                const hash_listpack_key = uuidv4();
                const skiplist_key = uuidv4();
                const zset_listpack_key = uuidv4();
                const stream_key = uuidv4();
                const non_existing_key = uuidv4();
                const versionLessThan7 =
                    cluster.checkIfServerVersionLessThan("7.0.0");
                const versionLessThan72 =
                    cluster.checkIfServerVersionLessThan("7.2.0");

                expect(await client.objectEncoding(non_existing_key)).toEqual(
                    null,
                );

                expect(
                    await client.set(
                        string_key,
                        "a really loooooooooooooooooooooooooooooooooooooooong value",
                    ),
                ).toEqual("OK");

                expect(await client.objectEncoding(string_key)).toEqual("raw");

                expect(await client.set(string_key, "2")).toEqual("OK");
                expect(await client.objectEncoding(string_key)).toEqual("int");

                expect(await client.set(string_key, "value")).toEqual("OK");
                expect(await client.objectEncoding(string_key)).toEqual(
                    "embstr",
                );

                expect(await client.lpush(list_key, ["1"])).toEqual(1);

                if (versionLessThan72) {
                    expect(await client.objectEncoding(list_key)).toEqual(
                        "quicklist",
                    );
                } else {
                    expect(await client.objectEncoding(list_key)).toEqual(
                        "listpack",
                    );
                }

                // The default value of set-max-intset-entries is 512
                for (let i = 0; i < 513; i++) {
                    expect(
                        await client.sadd(hashtable_key, [String(i)]),
                    ).toEqual(1);
                }

                expect(await client.objectEncoding(hashtable_key)).toEqual(
                    "hashtable",
                );

                expect(await client.sadd(intset_key, ["1"])).toEqual(1);
                expect(await client.objectEncoding(intset_key)).toEqual(
                    "intset",
                );

                expect(await client.sadd(set_listpack_key, ["foo"])).toEqual(1);

                if (versionLessThan72) {
                    expect(
                        await client.objectEncoding(set_listpack_key),
                    ).toEqual("hashtable");
                } else {
                    expect(
                        await client.objectEncoding(set_listpack_key),
                    ).toEqual("listpack");
                }

                // The default value of hash-max-listpack-entries is 512
                for (let i = 0; i < 513; i++) {
                    expect(
                        await client.hset(hash_hashtable_key, {
                            [String(i)]: "2",
                        }),
                    ).toEqual(1);
                }

                expect(await client.objectEncoding(hash_hashtable_key)).toEqual(
                    "hashtable",
                );

                expect(
                    await client.hset(hash_listpack_key, { "1": "2" }),
                ).toEqual(1);

                if (versionLessThan7) {
                    expect(
                        await client.objectEncoding(hash_listpack_key),
                    ).toEqual("ziplist");
                } else {
                    expect(
                        await client.objectEncoding(hash_listpack_key),
                    ).toEqual("listpack");
                }

                // The default value of zset-max-listpack-entries is 128
                for (let i = 0; i < 129; i++) {
                    expect(
                        await client.zadd(skiplist_key, { [String(i)]: 2.0 }),
                    ).toEqual(1);
                }

                expect(await client.objectEncoding(skiplist_key)).toEqual(
                    "skiplist",
                );

                expect(
                    await client.zadd(zset_listpack_key, { "1": 2.0 }),
                ).toEqual(1);

                if (versionLessThan7) {
                    expect(
                        await client.objectEncoding(zset_listpack_key),
                    ).toEqual("ziplist");
                } else {
                    expect(
                        await client.objectEncoding(zset_listpack_key),
                    ).toEqual("listpack");
                }

                expect(
                    await client.xadd(stream_key, [["field", "value"]]),
                ).not.toBeNull();
                expect(await client.objectEncoding(stream_key)).toEqual(
                    "stream",
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "object freq test_%p",
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const nonExistingKey = uuidv4();
                const maxmemoryPolicyKey = "maxmemory-policy";
                const config = await client.configGet([maxmemoryPolicyKey]);
                const maxmemoryPolicy = String(config[maxmemoryPolicyKey]);

                try {
                    expect(
                        await client.configSet({
                            [maxmemoryPolicyKey]: "allkeys-lfu",
                        }),
                    ).toEqual("OK");
                    expect(await client.objectFreq(nonExistingKey)).toEqual(
                        null,
                    );
                    expect(await client.set(key, "foobar")).toEqual("OK");
                    expect(await client.objectFreq(key)).toBeGreaterThanOrEqual(
                        0,
                    );
                } finally {
                    expect(
                        await client.configSet({
                            [maxmemoryPolicyKey]: maxmemoryPolicy,
                        }),
                    ).toEqual("OK");
                }
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "object idletime test_%p",
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const nonExistingKey = uuidv4();
                const maxmemoryPolicyKey = "maxmemory-policy";
                const config = await client.configGet([maxmemoryPolicyKey]);
                const maxmemoryPolicy = String(config[maxmemoryPolicyKey]);

                try {
                    expect(
                        await client.configSet({
                            // OBJECT IDLETIME requires a non-LFU maxmemory-policy
                            [maxmemoryPolicyKey]: "allkeys-random",
                        }),
                    ).toEqual("OK");
                    expect(await client.objectIdletime(nonExistingKey)).toEqual(
                        null,
                    );
                    expect(await client.set(key, "foobar")).toEqual("OK");

                    await wait(2000);

                    expect(await client.objectIdletime(key)).toBeGreaterThan(0);
                } finally {
                    expect(
                        await client.configSet({
                            [maxmemoryPolicyKey]: maxmemoryPolicy,
                        }),
                    ).toEqual("OK");
                }
            }, protocol);
        },
        config.timeout,
    );

    function wait(numMilliseconds: number) {
        return new Promise((resolve) => {
            setTimeout(resolve, numMilliseconds);
        });
    }

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `object refcount test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = `{key}:${uuidv4()}`;
                const nonExistingKey = `{key}:${uuidv4()}`;

                expect(await client.objectRefcount(nonExistingKey)).toBeNull();
                expect(await client.set(key, "foo")).toEqual("OK");
                expect(await client.objectRefcount(key)).toBeGreaterThanOrEqual(
                    1,
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `flushall test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                // Test FLUSHALL SYNC
                expect(await client.flushall(FlushMode.SYNC)).toBe("OK");

                // TODO: replace with KEYS command when implemented
                const keysAfter = (await client.customCommand([
                    "keys",
                    "*",
                ])) as string[];
                expect(keysAfter.length).toBe(0);

                // Test various FLUSHALL calls
                expect(await client.flushall()).toBe("OK");
                expect(await client.flushall(FlushMode.ASYNC)).toBe("OK");

                if (client instanceof GlideClusterClient) {
                    const key = uuidv4();
                    const primaryRoute: SingleNodeRoute = {
                        type: "primarySlotKey",
                        key: key,
                    };
                    expect(await client.flushall(undefined, primaryRoute)).toBe(
                        "OK",
                    );
                    expect(
                        await client.flushall(FlushMode.ASYNC, primaryRoute),
                    ).toBe("OK");

                    //Test FLUSHALL on replica (should fail)
                    const key2 = uuidv4();
                    const replicaRoute: SingleNodeRoute = {
                        type: "replicaSlotKey",
                        key: key2,
                    };
                    await expect(
                        client.flushall(undefined, replicaRoute),
                    ).rejects.toThrowError();
                }
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `lpos test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = `{key}:${uuidv4()}`;
                const valueArray = ["a", "a", "b", "c", "a", "b"];
                expect(await client.rpush(key, valueArray)).toEqual(6);

                // simplest case
                expect(await client.lpos(key, "a")).toEqual(0);
                expect(await client.lpos(key, "b", { rank: 2 })).toEqual(5);

                // element doesn't exist
                expect(await client.lpos(key, "e")).toBeNull();

                // reverse traversal
                expect(await client.lpos(key, "b", { rank: -2 })).toEqual(2);

                // unlimited comparisons
                expect(
                    await client.lpos(key, "a", { rank: 1, maxLength: 0 }),
                ).toEqual(0);

                // limited comparisons
                expect(
                    await client.lpos(key, "c", { rank: 1, maxLength: 2 }),
                ).toBeNull();

                // invalid rank value
                await expect(
                    client.lpos(key, "a", { rank: 0 }),
                ).rejects.toThrow(RequestError);

                // invalid maxlen value
                await expect(
                    client.lpos(key, "a", { maxLength: -1 }),
                ).rejects.toThrow(RequestError);

                // non-existent key
                expect(await client.lpos("non-existent_key", "e")).toBeNull();

                // wrong key data type
                const wrongDataType = `{key}:${uuidv4()}`;
                expect(await client.sadd(wrongDataType, ["a", "b"])).toEqual(2);

                await expect(client.lpos(wrongDataType, "a")).rejects.toThrow(
                    RequestError,
                );

                // invalid count value
                await expect(
                    client.lpos(key, "a", { count: -1 }),
                ).rejects.toThrow(RequestError);

                // with count
                expect(await client.lpos(key, "a", { count: 2 })).toEqual([
                    0, 1,
                ]);
                expect(await client.lpos(key, "a", { count: 0 })).toEqual([
                    0, 1, 4,
                ]);
                expect(
                    await client.lpos(key, "a", { rank: 1, count: 0 }),
                ).toEqual([0, 1, 4]);
                expect(
                    await client.lpos(key, "a", { rank: 2, count: 0 }),
                ).toEqual([1, 4]);
                expect(
                    await client.lpos(key, "a", { rank: 3, count: 0 }),
                ).toEqual([4]);

                // reverse traversal
                expect(
                    await client.lpos(key, "a", { rank: -1, count: 0 }),
                ).toEqual([4, 1, 0]);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `dbsize test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                // flush all data
                expect(await client.flushall()).toBe("OK");

                // check that DBSize is 0
                expect(await client.dbsize()).toBe(0);

                // set 10 random key-value pairs
                for (let i = 0; i < 10; i++) {
                    const key = `{key}:${uuidv4()}`;
                    const value = "0".repeat(Math.random() * 7);

                    expect(await client.set(key, value)).toBe("OK");
                }

                // check DBSIZE after setting
                expect(await client.dbsize()).toBe(10);

                // additional test for the standalone client
                if (client instanceof GlideClient) {
                    expect(await client.flushall()).toBe("OK");
                    const key = uuidv4();
                    expect(await client.set(key, "value")).toBe("OK");
                    expect(await client.dbsize()).toBe(1);
                    // switching to another db to check size
                    expect(await client.select(1)).toBe("OK");
                    expect(await client.dbsize()).toBe(0);
                }

                // additional test for the cluster client
                if (client instanceof GlideClusterClient) {
                    expect(await client.flushall()).toBe("OK");
                    const key = uuidv4();
                    expect(await client.set(key, "value")).toBe("OK");
                    const primaryRoute: SingleNodeRoute = {
                        type: "primarySlotKey",
                        key: key,
                    };
                    expect(await client.dbsize(primaryRoute)).toBe(1);
                }
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `bitcount test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                const value = "foobar";

                expect(await client.set(key1, value)).toEqual("OK");
                expect(await client.bitcount(key1)).toEqual(26);
                expect(
                    await client.bitcount(key1, { start: 1, end: 1 }),
                ).toEqual(6);
                expect(
                    await client.bitcount(key1, { start: 0, end: -5 }),
                ).toEqual(10);
                // non-existing key
                expect(await client.bitcount(uuidv4())).toEqual(0);
                expect(
                    await client.bitcount(uuidv4(), { start: 5, end: 30 }),
                ).toEqual(0);
                // key exists, but it is not a string
                expect(await client.sadd(key2, [value])).toEqual(1);
                await expect(client.bitcount(key2)).rejects.toThrow(
                    RequestError,
                );
                await expect(
                    client.bitcount(key2, { start: 1, end: 1 }),
                ).rejects.toThrow(RequestError);

                if (cluster.checkIfServerVersionLessThan("7.0.0")) {
                    await expect(
                        client.bitcount(key1, {
                            start: 2,
                            end: 5,
                            indexType: BitmapIndexType.BIT,
                        }),
                    ).rejects.toThrow();
                    await expect(
                        client.bitcount(key1, {
                            start: 2,
                            end: 5,
                            indexType: BitmapIndexType.BYTE,
                        }),
                    ).rejects.toThrow();
                } else {
                    expect(
                        await client.bitcount(key1, {
                            start: 2,
                            end: 5,
                            indexType: BitmapIndexType.BYTE,
                        }),
                    ).toEqual(16);
                    expect(
                        await client.bitcount(key1, {
                            start: 5,
                            end: 30,
                            indexType: BitmapIndexType.BIT,
                        }),
                    ).toEqual(17);
                    expect(
                        await client.bitcount(key1, {
                            start: 5,
                            end: -5,
                            indexType: BitmapIndexType.BIT,
                        }),
                    ).toEqual(23);
                    expect(
                        await client.bitcount(uuidv4(), {
                            start: 2,
                            end: 5,
                            indexType: BitmapIndexType.BYTE,
                        }),
                    ).toEqual(0);
                    // key exists, but it is not a string
                    await expect(
                        client.bitcount(key2, {
                            start: 1,
                            end: 1,
                            indexType: BitmapIndexType.BYTE,
                        }),
                    ).rejects.toThrow(RequestError);
                }
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `geoadd geopos test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                const membersToCoordinates = new Map<string, GeospatialData>();
                membersToCoordinates.set("Palermo", {
                    longitude: 13.361389,
                    latitude: 38.115556,
                });
                membersToCoordinates.set("Catania", {
                    longitude: 15.087269,
                    latitude: 37.502669,
                });

                // default geoadd
                expect(await client.geoadd(key1, membersToCoordinates)).toBe(2);

                let geopos = await client.geopos(key1, [
                    "Palermo",
                    "Catania",
                    "New York",
                ]);
                // inner array is possibly null, we need a null check or a cast
                expect(geopos[0]?.[0]).toBeCloseTo(13.361389, 5);
                expect(geopos[0]?.[1]).toBeCloseTo(38.115556, 5);
                expect(geopos[1]?.[0]).toBeCloseTo(15.087269, 5);
                expect(geopos[1]?.[1]).toBeCloseTo(37.502669, 5);
                expect(geopos[2]).toBeNull();

                // empty array of places
                geopos = await client.geopos(key1, []);
                expect(geopos).toEqual([]);

                // not existing key
                geopos = await client.geopos(key2, []);
                expect(geopos).toEqual([]);
                geopos = await client.geopos(key2, ["Palermo"]);
                expect(geopos).toEqual([null]);

                // with update mode options
                membersToCoordinates.set("Catania", {
                    longitude: 15.087269,
                    latitude: 39,
                });
                expect(
                    await client.geoadd(key1, membersToCoordinates, {
                        updateMode: ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
                    }),
                ).toBe(0);
                expect(
                    await client.geoadd(key1, membersToCoordinates, {
                        updateMode: ConditionalChange.ONLY_IF_EXISTS,
                    }),
                ).toBe(0);

                // with changed option
                membersToCoordinates.set("Catania", {
                    longitude: 15.087269,
                    latitude: 40,
                });
                membersToCoordinates.set("Tel-Aviv", {
                    longitude: 32.0853,
                    latitude: 34.7818,
                });
                expect(
                    await client.geoadd(key1, membersToCoordinates, {
                        changed: true,
                    }),
                ).toBe(2);

                // key exists but holding non-zset value
                expect(await client.set(key2, "foo")).toBe("OK");
                await expect(
                    client.geoadd(key2, membersToCoordinates),
                ).rejects.toThrow();
                await expect(client.geopos(key2, ["*_*"])).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `geoadd invalid args test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();

                // empty coordinate map
                await expect(client.geoadd(key, new Map())).rejects.toThrow();

                // coordinate out of bound
                await expect(
                    client.geoadd(
                        key,
                        new Map([["Place", { longitude: -181, latitude: 0 }]]),
                    ),
                ).rejects.toThrow();
                await expect(
                    client.geoadd(
                        key,
                        new Map([["Place", { longitude: 181, latitude: 0 }]]),
                    ),
                ).rejects.toThrow();
                await expect(
                    client.geoadd(
                        key,
                        new Map([["Place", { longitude: 0, latitude: 86 }]]),
                    ),
                ).rejects.toThrow();
                await expect(
                    client.geoadd(
                        key,
                        new Map([["Place", { longitude: 0, latitude: -86 }]]),
                    ),
                ).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `geosearch geosearchstore test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                if (cluster.checkIfServerVersionLessThan("6.2.0")) return;

                const key1 = "{geosearch}" + uuidv4();
                const key2 = "{geosearch}" + uuidv4();
                const key3 = "{geosearch}" + uuidv4();

                const members: string[] = [
                    "Catania",
                    "Palermo",
                    "edge2",
                    "edge1",
                ];
                const membersSet: Set<string> = new Set(members);
                const membersCoordinates: [number, number][] = [
                    [15.087269, 37.502669],
                    [13.361389, 38.115556],
                    [17.24151, 38.788135],
                    [12.758489, 38.788135],
                ];

                const membersGeoData: GeospatialData[] = [];

                for (const [lon, lat] of membersCoordinates) {
                    membersGeoData.push({ longitude: lon, latitude: lat });
                }

                const membersToCoordinates = new Map<string, GeospatialData>();

                for (let i = 0; i < members.length; i++) {
                    membersToCoordinates.set(members[i], membersGeoData[i]);
                }

                const expectedResult = [
                    [
                        members[0],
                        [
                            56.4413,
                            3479447370796909,
                            [15.087267458438873, 37.50266842333162],
                        ],
                    ],
                    [
                        members[1],
                        [
                            190.4424,
                            3479099956230698,
                            [13.361389338970184, 38.1155563954963],
                        ],
                    ],
                    [
                        members[2],
                        [
                            279.7403,
                            3481342659049484,
                            [17.241510450839996, 38.78813451624225],
                        ],
                    ],
                    [
                        members[3],
                        [
                            279.7405,
                            3479273021651468,
                            [12.75848776102066, 38.78813451624225],
                        ],
                    ],
                ];

                // geoadd
                expect(await client.geoadd(key1, membersToCoordinates)).toBe(
                    members.length,
                );

                let searchResult = await client.geosearch(
                    key1,
                    { position: { longitude: 15, latitude: 37 } },
                    { width: 400, height: 400, unit: GeoUnit.KILOMETERS },
                );
                // using set to compare, because results are reordrered
                expect(new Set(searchResult)).toEqual(membersSet);
                // same with geosearchstore
                expect(
                    await client.geosearchstore(
                        key2,
                        key1,
                        { position: { longitude: 15, latitude: 37 } },
                        { width: 400, height: 400, unit: GeoUnit.KILOMETERS },
                    ),
                ).toEqual(4);
                expect(
                    await client.zrange(key2, { start: 0, stop: -1 }),
                ).toEqual(searchResult);

                // order search result
                searchResult = await client.geosearch(
                    key1,
                    { position: { longitude: 15, latitude: 37 } },
                    { width: 400, height: 400, unit: GeoUnit.KILOMETERS },
                    { sortOrder: SortOrder.ASC },
                );
                expect(searchResult).toEqual(members);
                // same with geosearchstore
                expect(
                    await client.geosearchstore(
                        key2,
                        key1,
                        { position: { longitude: 15, latitude: 37 } },
                        { width: 400, height: 400, unit: GeoUnit.KILOMETERS },
                        { sortOrder: SortOrder.ASC, storeDist: true },
                    ),
                ).toEqual(4);
                expect(
                    await client.zrange(key2, { start: 0, stop: -1 }),
                ).toEqual(searchResult);

                // order and query all extra data
                searchResult = await client.geosearch(
                    key1,
                    { position: { longitude: 15, latitude: 37 } },
                    { width: 400, height: 400, unit: GeoUnit.KILOMETERS },
                    {
                        sortOrder: SortOrder.ASC,
                        withCoord: true,
                        withDist: true,
                        withHash: true,
                    },
                );
                expect(searchResult).toEqual(expectedResult);

                // order, query and limit by 1
                searchResult = await client.geosearch(
                    key1,
                    { position: { longitude: 15, latitude: 37 } },
                    { width: 400, height: 400, unit: GeoUnit.KILOMETERS },
                    {
                        sortOrder: SortOrder.ASC,
                        withCoord: true,
                        withDist: true,
                        withHash: true,
                        count: 1,
                    },
                );
                expect(searchResult).toEqual(expectedResult.slice(0, 1));
                // same with geosearchstore
                expect(
                    await client.geosearchstore(
                        key2,
                        key1,
                        { position: { longitude: 15, latitude: 37 } },
                        { width: 400, height: 400, unit: GeoUnit.KILOMETERS },
                        {
                            sortOrder: SortOrder.ASC,
                            count: 1,
                            storeDist: true,
                        },
                    ),
                ).toEqual(1);
                expect(
                    await client.zrange(key2, { start: 0, stop: -1 }),
                ).toEqual([members[0]]);

                // test search by box, unit: meters, from member, with distance
                const meters = 400 * 1000;
                searchResult = await client.geosearch(
                    key1,
                    { member: "Catania" },
                    { width: meters, height: meters, unit: GeoUnit.METERS },
                    {
                        withDist: true,
                        withCoord: false,
                        sortOrder: SortOrder.DESC,
                    },
                );
                expect(searchResult).toEqual([
                    ["edge2", [236529.1799]],
                    ["Palermo", [166274.1516]],
                    ["Catania", [0.0]],
                ]);
                // same with geosearchstore
                expect(
                    await client.geosearchstore(
                        key2,
                        key1,
                        { member: "Catania" },
                        { width: meters, height: meters, unit: GeoUnit.METERS },
                        { sortOrder: SortOrder.DESC, storeDist: true },
                    ),
                ).toEqual(3);
                // TODO deep close to https://github.com/maasencioh/jest-matcher-deep-close-to
                expect(
                    await client.zrangeWithScores(
                        key2,
                        { start: 0, stop: -1 },
                        true,
                    ),
                ).toEqual({
                    edge2: 236529.17986494553,
                    Palermo: 166274.15156960033,
                    Catania: 0.0,
                });

                // test search by box, unit: feet, from member, with limited count 2, with hash
                const feet = 400 * 3280.8399;
                searchResult = await client.geosearch(
                    key1,
                    { member: "Palermo" },
                    { width: feet, height: feet, unit: GeoUnit.FEET },
                    {
                        withDist: false,
                        withCoord: false,
                        withHash: true,
                        sortOrder: SortOrder.ASC,
                        count: 2,
                    },
                );
                expect(searchResult).toEqual([
                    ["Palermo", [3479099956230698]],
                    ["edge1", [3479273021651468]],
                ]);
                // same with geosearchstore
                expect(
                    await client.geosearchstore(
                        key2,
                        key1,
                        { member: "Palermo" },
                        { width: feet, height: feet, unit: GeoUnit.FEET },
                        {
                            sortOrder: SortOrder.ASC,
                            count: 2,
                        },
                    ),
                ).toEqual(2);
                expect(
                    await client.zrangeWithScores(key2, { start: 0, stop: -1 }),
                ).toEqual({
                    Palermo: 3479099956230698,
                    edge1: 3479273021651468,
                });

                // test search by box, unit: miles, from geospatial position, with limited ANY count to 1
                const miles = 250;
                searchResult = await client.geosearch(
                    key1,
                    { position: { longitude: 15, latitude: 37 } },
                    { width: miles, height: miles, unit: GeoUnit.MILES },
                    { count: 1, isAny: true },
                );
                expect(members).toContainEqual(searchResult[0]);
                // same with geosearchstore
                expect(
                    await client.geosearchstore(
                        key2,
                        key1,
                        { position: { longitude: 15, latitude: 37 } },
                        { width: miles, height: miles, unit: GeoUnit.MILES },
                        { count: 1, isAny: true },
                    ),
                ).toEqual(1);
                expect(
                    await client.zrange(key2, { start: 0, stop: -1 }),
                ).toEqual(searchResult);

                // test search by radius, units: feet, from member
                const feetRadius = 200 * 3280.8399;
                searchResult = await client.geosearch(
                    key1,
                    { member: "Catania" },
                    { radius: feetRadius, unit: GeoUnit.FEET },
                    { sortOrder: SortOrder.ASC },
                );
                expect(searchResult).toEqual(["Catania", "Palermo"]);
                // same with geosearchstore
                expect(
                    await client.geosearchstore(
                        key2,
                        key1,
                        { member: "Catania" },
                        { radius: feetRadius, unit: GeoUnit.FEET },
                        { sortOrder: SortOrder.ASC, storeDist: true },
                    ),
                ).toEqual(2);
                expect(
                    await client.zrange(key2, { start: 0, stop: -1 }),
                ).toEqual(searchResult);

                // Test search by radius, unit: meters, from member
                const metersRadius = 200 * 1000;
                searchResult = await client.geosearch(
                    key1,
                    { member: "Catania" },
                    { radius: metersRadius, unit: GeoUnit.METERS },
                    { sortOrder: SortOrder.DESC },
                );
                expect(searchResult).toEqual(["Palermo", "Catania"]);
                // same with geosearchstore
                expect(
                    await client.geosearchstore(
                        key2,
                        key1,
                        { member: "Catania" },
                        { radius: metersRadius, unit: GeoUnit.METERS },
                        { sortOrder: SortOrder.DESC, storeDist: true },
                    ),
                ).toEqual(2);
                expect(
                    await client.zrange(key2, { start: 0, stop: -1 }, true),
                ).toEqual(searchResult);

                searchResult = await client.geosearch(
                    key1,
                    { member: "Catania" },
                    { radius: metersRadius, unit: GeoUnit.METERS },
                    {
                        sortOrder: SortOrder.DESC,
                        withHash: true,
                    },
                );
                expect(searchResult).toEqual([
                    ["Palermo", [3479099956230698]],
                    ["Catania", [3479447370796909]],
                ]);

                // Test search by radius, unit: miles, from geospatial data
                searchResult = await client.geosearch(
                    key1,
                    { position: { longitude: 15, latitude: 37 } },
                    { radius: 175, unit: GeoUnit.MILES },
                    { sortOrder: SortOrder.DESC },
                );
                expect(searchResult).toEqual([
                    "edge1",
                    "edge2",
                    "Palermo",
                    "Catania",
                ]);
                // same with geosearchstore
                expect(
                    await client.geosearchstore(
                        key2,
                        key1,
                        { position: { longitude: 15, latitude: 37 } },
                        { radius: 175, unit: GeoUnit.MILES },
                        { sortOrder: SortOrder.DESC, storeDist: true },
                    ),
                ).toEqual(4);
                expect(
                    await client.zrange(key2, { start: 0, stop: -1 }, true),
                ).toEqual(searchResult);

                // Test search by radius, unit: kilometers, from a geospatial data, with limited count to 2
                searchResult = await client.geosearch(
                    key1,
                    { position: { longitude: 15, latitude: 37 } },
                    { radius: 200, unit: GeoUnit.KILOMETERS },
                    {
                        sortOrder: SortOrder.ASC,
                        count: 2,
                        withHash: true,
                        withCoord: true,
                        withDist: true,
                    },
                );
                expect(searchResult).toEqual(expectedResult.slice(0, 2));
                // same with geosearchstore
                expect(
                    await client.geosearchstore(
                        key2,
                        key1,
                        { position: { longitude: 15, latitude: 37 } },
                        { radius: 200, unit: GeoUnit.KILOMETERS },
                        {
                            sortOrder: SortOrder.ASC,
                            count: 2,
                            storeDist: true,
                        },
                    ),
                ).toEqual(2);
                expect(
                    await client.zrange(key2, { start: 0, stop: -1 }),
                ).toEqual(members.slice(0, 2));

                // Test search by radius, unit: kilometers, from a geospatial data, with limited ANY count to 1
                searchResult = await client.geosearch(
                    key1,
                    { position: { longitude: 15, latitude: 37 } },
                    { radius: 200, unit: GeoUnit.KILOMETERS },
                    {
                        sortOrder: SortOrder.ASC,
                        count: 1,
                        isAny: true,
                        withCoord: true,
                        withDist: true,
                        withHash: true,
                    },
                );
                expect(members).toContainEqual(searchResult[0][0]);
                // same with geosearchstore
                expect(
                    await client.geosearchstore(
                        key2,
                        key1,
                        { position: { longitude: 15, latitude: 37 } },
                        { radius: 200, unit: GeoUnit.KILOMETERS },
                        {
                            sortOrder: SortOrder.ASC,
                            count: 1,
                            isAny: true,
                        },
                    ),
                ).toEqual(1);
                expect(
                    await client.zrange(key2, { start: 0, stop: -1 }),
                ).toEqual([searchResult[0][0]]);

                // no members within the area
                searchResult = await client.geosearch(
                    key1,
                    { position: { longitude: 15, latitude: 37 } },
                    { width: 50, height: 50, unit: GeoUnit.METERS },
                    { sortOrder: SortOrder.ASC },
                );
                expect(searchResult).toEqual([]);
                // same with geosearchstore
                expect(
                    await client.geosearchstore(
                        key2,
                        key1,
                        { position: { longitude: 15, latitude: 37 } },
                        { width: 50, height: 50, unit: GeoUnit.METERS },
                        { sortOrder: SortOrder.ASC },
                    ),
                ).toEqual(0);
                expect(await client.zcard(key2)).toEqual(0);

                // no members within the area
                searchResult = await client.geosearch(
                    key1,
                    { position: { longitude: 15, latitude: 37 } },
                    { radius: 5, unit: GeoUnit.METERS },
                    { sortOrder: SortOrder.ASC },
                );
                expect(searchResult).toEqual([]);
                // same with geosearchstore
                expect(
                    await client.geosearchstore(
                        key2,
                        key1,
                        { position: { longitude: 15, latitude: 37 } },
                        { radius: 5, unit: GeoUnit.METERS },
                        { sortOrder: SortOrder.ASC },
                    ),
                ).toEqual(0);
                expect(await client.zcard(key2)).toEqual(0);

                // member does not exist
                await expect(
                    client.geosearch(
                        key1,
                        { member: "non-existing-member" },
                        { radius: 100, unit: GeoUnit.METERS },
                    ),
                ).rejects.toThrow(RequestError);
                await expect(
                    client.geosearchstore(
                        key2,
                        key1,
                        { member: "non-existing-member" },
                        { radius: 100, unit: GeoUnit.METERS },
                    ),
                ).rejects.toThrow(RequestError);

                // key exists but holds a non-ZSET value
                expect(await client.set(key3, uuidv4())).toEqual("OK");
                await expect(
                    client.geosearch(
                        key3,
                        { position: { longitude: 15, latitude: 37 } },
                        { radius: 100, unit: GeoUnit.METERS },
                    ),
                ).rejects.toThrow(RequestError);
                await expect(
                    client.geosearchstore(
                        key2,
                        key3,
                        { position: { longitude: 15, latitude: 37 } },
                        { radius: 100, unit: GeoUnit.METERS },
                    ),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zmpop test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster: RedisCluster) => {
                if (cluster.checkIfServerVersionLessThan("7.0.0")) return;
                const key1 = "{key}-1" + uuidv4();
                const key2 = "{key}-2" + uuidv4();
                const nonExistingKey = "{key}-0" + uuidv4();
                const stringKey = "{key}-string" + uuidv4();

                expect(await client.zadd(key1, { a1: 1, b1: 2 })).toEqual(2);
                expect(await client.zadd(key2, { a2: 0.1, b2: 0.2 })).toEqual(
                    2,
                );

                expect(
                    await client.zmpop([key1, key2], ScoreFilter.MAX),
                ).toEqual([key1, { b1: 2 }]);
                expect(
                    await client.zmpop([key2, key1], ScoreFilter.MAX, 10),
                ).toEqual([key2, { a2: 0.1, b2: 0.2 }]);

                expect(
                    await client.zmpop([nonExistingKey], ScoreFilter.MIN),
                ).toBeNull();
                expect(
                    await client.zmpop([nonExistingKey], ScoreFilter.MIN, 1),
                ).toBeNull();

                // key exists, but it is not a sorted set
                expect(await client.set(stringKey, "value")).toEqual("OK");
                await expect(
                    client.zmpop([stringKey], ScoreFilter.MAX),
                ).rejects.toThrow(RequestError);
                await expect(
                    client.zmpop([stringKey], ScoreFilter.MAX, 1),
                ).rejects.toThrow(RequestError);

                // incorrect argument: key list should not be empty
                await expect(
                    client.zmpop([], ScoreFilter.MAX, 1),
                ).rejects.toThrow(RequestError);

                // incorrect argument: count should be greater than 0
                await expect(
                    client.zmpop([key1], ScoreFilter.MAX, 0),
                ).rejects.toThrow(RequestError);

                // check that order of entries in the response is preserved
                const entries: Record<string, number> = {};

                for (let i = 0; i < 10; i++) {
                    // a0 => 0, a1 => 1 etc
                    entries["a" + i] = i;
                }

                expect(await client.zadd(key2, entries)).toEqual(10);
                const result = await client.zmpop([key2], ScoreFilter.MIN, 10);

                if (result) {
                    expect(result[1]).toEqual(entries);
                }
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zincrby test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = "{key}" + uuidv4();
                const member = "{member}-1" + uuidv4();
                const othermember = "{member}-1" + uuidv4();
                const stringKey = "{key}-string" + uuidv4();

                // key does not exist
                expect(await client.zincrby(key, 2.5, member)).toEqual(2.5);
                expect(await client.zscore(key, member)).toEqual(2.5);

                // key exists, but value doesn't
                expect(await client.zincrby(key, -3.3, othermember)).toEqual(
                    -3.3,
                );
                expect(await client.zscore(key, othermember)).toEqual(-3.3);

                // updating existing value in existing key
                expect(await client.zincrby(key, 1.0, member)).toEqual(3.5);
                expect(await client.zscore(key, member)).toEqual(3.5);

                // Key exists, but it is not a sorted set
                expect(await client.set(stringKey, "value")).toEqual("OK");
                await expect(
                    client.zincrby(stringKey, 0.5, "_"),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zscan test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = "{key}-1" + uuidv4();
                const key2 = "{key}-2" + uuidv4();
                const initialCursor = "0";
                const defaultCount = 20;
                const resultCursorIndex = 0;
                const resultCollectionIndex = 1;

                // Setup test data - use a large number of entries to force an iterative cursor.
                const numberMap: Record<string, number> = {};

                for (let i = 0; i < 50000; i++) {
                    numberMap[i.toString()] = i;
                }

                const charMembers = ["a", "b", "c", "d", "e"];
                const charMap: Record<string, number> = {};
                const expectedCharMapArray: string[] = [];

                for (let i = 0; i < charMembers.length; i++) {
                    expectedCharMapArray.push(charMembers[i]);
                    expectedCharMapArray.push(i.toString());
                    charMap[charMembers[i]] = i;
                }

                // Empty set
                let result = await client.zscan(key1, initialCursor);
                expect(result[resultCursorIndex]).toEqual(initialCursor);
                expect(result[resultCollectionIndex]).toEqual([]);

                // Negative cursor
                result = await client.zscan(key1, "-1");
                expect(result[resultCursorIndex]).toEqual(initialCursor);
                expect(result[resultCollectionIndex]).toEqual([]);

                // Result contains the whole set
                expect(await client.zadd(key1, charMap)).toEqual(
                    charMembers.length,
                );
                result = await client.zscan(key1, initialCursor);
                expect(result[resultCursorIndex]).toEqual(initialCursor);
                expect(result[resultCollectionIndex].length).toEqual(
                    expectedCharMapArray.length,
                );
                expect(result[resultCollectionIndex]).toEqual(
                    expectedCharMapArray,
                );

                result = await client.zscan(key1, initialCursor, {
                    match: "a",
                });
                expect(result[resultCursorIndex]).toEqual(initialCursor);
                expect(result[resultCollectionIndex]).toEqual(["a", "0"]);

                // Result contains a subset of the key
                expect(await client.zadd(key1, numberMap)).toEqual(
                    Object.keys(numberMap).length,
                );

                result = await client.zscan(key1, initialCursor);
                let resultCursor = result[resultCursorIndex];
                let resultIterationCollection = result[resultCollectionIndex];
                let fullResultMapArray: string[] = resultIterationCollection;
                let nextResult;
                let nextResultCursor;

                // 0 is returned for the cursor of the last iteration.
                while (resultCursor != "0") {
                    nextResult = await client.zscan(key1, resultCursor);
                    nextResultCursor = nextResult[resultCursorIndex];
                    expect(nextResultCursor).not.toEqual(resultCursor);

                    expect(nextResult[resultCollectionIndex]).not.toEqual(
                        resultIterationCollection,
                    );
                    fullResultMapArray = fullResultMapArray.concat(
                        nextResult[resultCollectionIndex],
                    );
                    resultIterationCollection =
                        nextResult[resultCollectionIndex];
                    resultCursor = nextResultCursor;
                }

                // Fetching by cursor is randomized.
                const expectedFullMap: Record<string, number> = {
                    ...numberMap,
                    ...charMap,
                };

                expect(fullResultMapArray.length).toEqual(
                    Object.keys(expectedFullMap).length * 2,
                );

                for (let i = 0; i < fullResultMapArray.length; i += 2) {
                    expect(fullResultMapArray[i] in expectedFullMap).toEqual(
                        true,
                    );
                }

                // Test match pattern
                result = await client.zscan(key1, initialCursor, {
                    match: "*",
                });
                expect(result[resultCursorIndex]).not.toEqual(initialCursor);
                expect(
                    result[resultCollectionIndex].length,
                ).toBeGreaterThanOrEqual(defaultCount);

                // Test count
                result = await client.zscan(key1, initialCursor, { count: 20 });
                expect(result[resultCursorIndex]).not.toEqual("0");
                expect(
                    result[resultCollectionIndex].length,
                ).toBeGreaterThanOrEqual(20);

                // Test count with match returns a non-empty list
                result = await client.zscan(key1, initialCursor, {
                    match: "1*",
                    count: 20,
                });
                expect(result[resultCursorIndex]).not.toEqual("0");
                expect(result[resultCollectionIndex].length).toBeGreaterThan(0);

                // Exceptions
                // Non-set key
                expect(await client.set(key2, "test")).toEqual("OK");
                await expect(client.zscan(key2, initialCursor)).rejects.toThrow(
                    RequestError,
                );
                await expect(
                    client.zscan(key2, initialCursor, {
                        match: "test",
                        count: 20,
                    }),
                ).rejects.toThrow(RequestError);

                // Negative count
                await expect(
                    client.zscan(key2, initialCursor, { count: -1 }),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `bzmpop test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster: RedisCluster) => {
                if (cluster.checkIfServerVersionLessThan("7.0.0")) return;
                const key1 = "{key}-1" + uuidv4();
                const key2 = "{key}-2" + uuidv4();
                const nonExistingKey = "{key}-0" + uuidv4();
                const stringKey = "{key}-string" + uuidv4();

                expect(await client.zadd(key1, { a1: 1, b1: 2 })).toEqual(2);
                expect(await client.zadd(key2, { a2: 0.1, b2: 0.2 })).toEqual(
                    2,
                );

                expect(
                    await client.bzmpop([key1, key2], ScoreFilter.MAX, 0.1),
                ).toEqual([key1, { b1: 2 }]);
                expect(
                    await client.bzmpop([key2, key1], ScoreFilter.MAX, 0.1, 10),
                ).toEqual([key2, { a2: 0.1, b2: 0.2 }]);

                // ensure that command doesn't time out even if timeout > request timeout (250ms by default)
                expect(
                    await client.bzmpop([nonExistingKey], ScoreFilter.MAX, 0.5),
                ).toBeNull();
                expect(
                    await client.bzmpop(
                        [nonExistingKey],
                        ScoreFilter.MAX,
                        0.55,
                        1,
                    ),
                ).toBeNull();

                // key exists, but it is not a sorted set
                expect(await client.set(stringKey, "value")).toEqual("OK");
                await expect(
                    client.bzmpop([stringKey], ScoreFilter.MAX, 0.1),
                ).rejects.toThrow(RequestError);
                await expect(
                    client.bzmpop([stringKey], ScoreFilter.MAX, 0.1, 1),
                ).rejects.toThrow(RequestError);

                // incorrect argument: key list should not be empty
                await expect(
                    client.bzmpop([], ScoreFilter.MAX, 0.1, 1),
                ).rejects.toThrow(RequestError);

                // incorrect argument: count should be greater than 0
                await expect(
                    client.bzmpop([key1], ScoreFilter.MAX, 0.1, 0),
                ).rejects.toThrow(RequestError);

                // incorrect argument: timeout can not be a negative number
                await expect(
                    client.bzmpop([key1], ScoreFilter.MAX, -1, 10),
                ).rejects.toThrow(RequestError);

                // check that order of entries in the response is preserved
                const entries: Record<string, number> = {};

                for (let i = 0; i < 10; i++) {
                    // a0 => 0, a1 => 1 etc
                    entries["a" + i] = i;
                }

                expect(await client.zadd(key2, entries)).toEqual(10);
                const result = await client.bzmpop(
                    [key2],
                    ScoreFilter.MIN,
                    0.1,
                    10,
                );

                if (result) {
                    expect(result[1]).toEqual(entries);
                }

                // TODO: add test case with 0 timeout (no timeout) should never time out,
                // but we wrap the test with timeout to avoid test failing or stuck forever
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `geodist test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                const member1 = "Palermo";
                const member2 = "Catania";
                const nonExistingMember = "NonExisting";
                const expected = 166274.1516;
                const expectedKM = 166.2742;
                const delta = 1e-9;

                // adding the geo locations
                const membersToCoordinates = new Map<string, GeospatialData>();
                membersToCoordinates.set(member1, {
                    longitude: 13.361389,
                    latitude: 38.115556,
                });
                membersToCoordinates.set(member2, {
                    longitude: 15.087269,
                    latitude: 37.502669,
                });
                expect(await client.geoadd(key1, membersToCoordinates)).toBe(2);

                // checking result with default metric
                expect(
                    await client.geodist(key1, member1, member2),
                ).toBeCloseTo(expected, delta);

                // checking result with metric specification of kilometers
                expect(
                    await client.geodist(
                        key1,
                        member1,
                        member2,
                        GeoUnit.KILOMETERS,
                    ),
                ).toBeCloseTo(expectedKM, delta);

                // null result when member index is missing
                expect(
                    await client.geodist(key1, member1, nonExistingMember),
                ).toBeNull();

                // key exists but holds non-ZSET value
                expect(await client.set(key2, "geodist")).toBe("OK");
                await expect(
                    client.geodist(key2, member1, member2),
                ).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `geohash test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                const members = ["Palermo", "Catania", "NonExisting"];
                const empty: string[] = [];
                const expected = ["sqc8b49rny0", "sqdtr74hyu0", null];

                // adding the geo locations
                const membersToCoordinates = new Map<string, GeospatialData>();
                membersToCoordinates.set("Palermo", {
                    longitude: 13.361389,
                    latitude: 38.115556,
                });
                membersToCoordinates.set("Catania", {
                    longitude: 15.087269,
                    latitude: 37.502669,
                });
                expect(await client.geoadd(key1, membersToCoordinates)).toBe(2);

                // checking result with default metric
                expect(await client.geohash(key1, members)).toEqual(expected);

                // empty members array
                expect(await (await client.geohash(key1, empty)).length).toBe(
                    0,
                );

                // key exists but holds non-ZSET value
                expect(await client.set(key2, "geohash")).toBe("OK");
                await expect(client.geohash(key2, members)).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `touch test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = `{key}-${uuidv4()}`;
                const key2 = `{key}-${uuidv4()}`;
                const nonExistingKey = `{key}-${uuidv4()}`;

                expect(
                    await client.mset({ [key1]: "value1", [key2]: "value2" }),
                ).toEqual("OK");
                expect(await client.touch([key1, key2])).toEqual(2);
                expect(
                    await client.touch([key2, nonExistingKey, key1]),
                ).toEqual(2);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zrandmember test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();

                const memberScores = { one: 1.0, two: 2.0 };
                const elements = ["one", "two"];
                expect(await client.zadd(key1, memberScores)).toBe(2);

                // check random memember belongs to the set
                const randmember = await client.zrandmember(key1);

                if (randmember !== null) {
                    expect(elements.includes(randmember)).toEqual(true);
                }

                // non existing key should return null
                expect(await client.zrandmember("nonExistingKey")).toBeNull();

                // Key exists, but is not a set
                expect(await client.set(key2, "foo")).toBe("OK");
                await expect(client.zrandmember(key2)).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zrandmemberWithCount test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();

                const memberScores = { one: 1.0, two: 2.0 };
                expect(await client.zadd(key1, memberScores)).toBe(2);

                // unique values are expected as count is positive
                let randMembers = await client.zrandmemberWithCount(key1, 4);
                expect(randMembers.length).toBe(2);
                expect(randMembers.length).toEqual(new Set(randMembers).size);

                // Duplicate values are expected as count is negative
                randMembers = await client.zrandmemberWithCount(key1, -4);
                expect(randMembers.length).toBe(4);
                const randMemberSet = new Set<string>();

                for (const member of randMembers) {
                    const memberStr = member + "";

                    if (!randMemberSet.has(memberStr)) {
                        randMemberSet.add(memberStr);
                    }
                }

                expect(randMembers.length).not.toEqual(randMemberSet.size);

                // non existing key should return empty array
                randMembers = await client.zrandmemberWithCount(
                    "nonExistingKey",
                    -4,
                );
                expect(randMembers.length).toBe(0);

                // Key exists, but is not a set
                expect(await client.set(key2, "foo")).toBe("OK");
                await expect(
                    client.zrandmemberWithCount(key2, 1),
                ).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `zrandmemberWithCountWithScores test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();

                const memberScores = { one: 1.0, two: 2.0 };
                const memberScoreMap = new Map<string, number>([
                    ["one", 1.0],
                    ["two", 2.0],
                ]);
                expect(await client.zadd(key1, memberScores)).toBe(2);

                // unique values are expected as count is positive
                let randMembers = await client.zrandmemberWithCountWithScores(
                    key1,
                    4,
                );

                for (const member of randMembers) {
                    const key = String(member[0]);
                    const score = Number(member[1]);
                    expect(score).toEqual(memberScoreMap.get(key));
                }

                // Duplicate values are expected as count is negative
                randMembers = await client.zrandmemberWithCountWithScores(
                    key1,
                    -4,
                );
                expect(randMembers.length).toBe(4);
                const keys = [];

                for (const member of randMembers) {
                    keys.push(String(member[0]));
                }

                expect(randMembers.length).not.toEqual(new Set(keys).size);

                // non existing key should return empty array
                randMembers = await client.zrandmemberWithCountWithScores(
                    "nonExistingKey",
                    -4,
                );
                expect(randMembers.length).toBe(0);

                // Key exists, but is not a set
                expect(await client.set(key2, "foo")).toBe("OK");
                await expect(
                    client.zrandmemberWithCount(key2, 1),
                ).rejects.toThrow();
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `lcs %p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                if (cluster.checkIfServerVersionLessThan("7.0.0")) return;

                const key1 = "{lcs}" + uuidv4();
                const key2 = "{lcs}" + uuidv4();
                const key3 = "{lcs}" + uuidv4();
                const key4 = "{lcs}" + uuidv4();

                // keys does not exist or is empty
                expect(await client.lcs(key1, key2)).toEqual("");
                expect(await client.lcsLen(key1, key2)).toEqual(0);
                expect(await client.lcsIdx(key1, key2)).toEqual({
                    matches: [],
                    len: 0,
                });

                // LCS with some strings
                expect(
                    await client.mset({
                        [key1]: "abcdefghijk",
                        [key2]: "defjkjuighijk",
                        [key3]: "123",
                    }),
                ).toEqual("OK");
                expect(await client.lcs(key1, key2)).toEqual("defghijk");
                expect(await client.lcsLen(key1, key2)).toEqual(8);

                // LCS with only IDX
                expect(await client.lcsIdx(key1, key2)).toEqual({
                    matches: [
                        [
                            [6, 10],
                            [8, 12],
                        ],
                        [
                            [3, 5],
                            [0, 2],
                        ],
                    ],
                    len: 8,
                });
                expect(await client.lcsIdx(key1, key2, {})).toEqual({
                    matches: [
                        [
                            [6, 10],
                            [8, 12],
                        ],
                        [
                            [3, 5],
                            [0, 2],
                        ],
                    ],
                    len: 8,
                });
                expect(
                    await client.lcsIdx(key1, key2, { withMatchLen: false }),
                ).toEqual({
                    matches: [
                        [
                            [6, 10],
                            [8, 12],
                        ],
                        [
                            [3, 5],
                            [0, 2],
                        ],
                    ],
                    len: 8,
                });

                // LCS with IDX and WITHMATCHLEN
                expect(
                    await client.lcsIdx(key1, key2, { withMatchLen: true }),
                ).toEqual({
                    matches: [
                        [[6, 10], [8, 12], 5],
                        [[3, 5], [0, 2], 3],
                    ],
                    len: 8,
                });

                // LCS with IDX and MINMATCHLEN
                expect(
                    await client.lcsIdx(key1, key2, { minMatchLen: 4 }),
                ).toEqual({
                    matches: [
                        [
                            [6, 10],
                            [8, 12],
                        ],
                    ],
                    len: 8,
                });
                // LCS with IDX and a negative MINMATCHLEN
                expect(
                    await client.lcsIdx(key1, key2, { minMatchLen: -1 }),
                ).toEqual({
                    matches: [
                        [
                            [6, 10],
                            [8, 12],
                        ],
                        [
                            [3, 5],
                            [0, 2],
                        ],
                    ],
                    len: 8,
                });

                // LCS with IDX, MINMATCHLEN, and WITHMATCHLEN
                expect(
                    await client.lcsIdx(key1, key2, {
                        minMatchLen: 4,
                        withMatchLen: true,
                    }),
                ).toEqual({ matches: [[[6, 10], [8, 12], 5]], len: 8 });

                // non-string keys are used
                expect(await client.sadd(key4, ["_"])).toEqual(1);
                await expect(client.lcs(key1, key4)).rejects.toThrow(
                    RequestError,
                );
                await expect(client.lcsLen(key1, key4)).rejects.toThrow(
                    RequestError,
                );
                await expect(client.lcsIdx(key1, key4)).rejects.toThrow(
                    RequestError,
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `xdel test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const stringKey = uuidv4();
                const nonExistentKey = uuidv4();
                const streamId1 = "0-1";
                const streamId2 = "0-2";
                const streamId3 = "0-3";

                expect(
                    await client.xadd(
                        key,
                        [
                            ["f1", "foo1"],
                            ["f2", "foo2"],
                        ],
                        { id: streamId1 },
                    ),
                ).toEqual(streamId1);

                expect(
                    await client.xadd(
                        key,
                        [
                            ["f1", "foo1"],
                            ["f2", "foo2"],
                        ],
                        { id: streamId2 },
                    ),
                ).toEqual(streamId2);

                expect(await client.xlen(key)).toEqual(2);

                // deletes one stream id, and ignores anything invalid
                expect(await client.xdel(key, [streamId1, streamId3])).toEqual(
                    1,
                );
                expect(await client.xdel(nonExistentKey, [streamId3])).toEqual(
                    0,
                );

                // invalid argument - id list should not be empty
                await expect(client.xdel(key, [])).rejects.toThrow(
                    RequestError,
                );

                // key exists, but it is not a stream
                expect(await client.set(stringKey, "foo")).toEqual("OK");
                await expect(
                    client.xdel(stringKey, [streamId3]),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `xinfoconsumers xinfo consumers %p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                const key = uuidv4();
                const stringKey = uuidv4();
                const groupName1 = uuidv4();
                const consumer1 = uuidv4();
                const consumer2 = uuidv4();
                const streamId1 = "0-1";
                const streamId2 = "0-2";
                const streamId3 = "0-3";
                const streamId4 = "0-4";

                expect(
                    await client.xadd(
                        key,
                        [
                            ["entry1_field1", "entry1_value1"],
                            ["entry1_field2", "entry1_value2"],
                        ],
                        { id: streamId1 },
                    ),
                ).toEqual(streamId1);

                expect(
                    await client.xadd(
                        key,
                        [
                            ["entry2_field1", "entry2_value1"],
                            ["entry2_field2", "entry2_value2"],
                        ],
                        { id: streamId2 },
                    ),
                ).toEqual(streamId2);

                expect(
                    await client.xadd(
                        key,
                        [["entry3_field1", "entry3_value1"]],
                        { id: streamId3 },
                    ),
                ).toEqual(streamId3);

                expect(
                    await client.xgroupCreate(key, groupName1, "0-0"),
                ).toEqual("OK");
                expect(
                    await client.customCommand([
                        "XREADGROUP",
                        "GROUP",
                        groupName1,
                        consumer1,
                        "COUNT",
                        "1",
                        "STREAMS",
                        key,
                        ">",
                    ]),
                ).toEqual({
                    [key]: {
                        [streamId1]: [
                            ["entry1_field1", "entry1_value1"],
                            ["entry1_field2", "entry1_value2"],
                        ],
                    },
                });
                // Sleep to ensure the idle time value and inactive time value returned by xinfo_consumers is > 0
                await new Promise((resolve) => setTimeout(resolve, 2000));
                let result = await client.xinfoConsumers(key, groupName1);
                expect(result.length).toEqual(1);
                expect(result[0].name).toEqual(consumer1);
                expect(result[0].pending).toEqual(1);
                expect(result[0].idle).toBeGreaterThan(0);

                if (cluster.checkIfServerVersionLessThan("7.2.0")) {
                    expect(result[0].inactive).toBeGreaterThan(0);
                }

                expect(
                    await client.customCommand([
                        "XGROUP",
                        "CREATECONSUMER",
                        key,
                        groupName1,
                        consumer2,
                    ]),
                ).toBeTruthy();
                expect(
                    await client.customCommand([
                        "XREADGROUP",
                        "GROUP",
                        groupName1,
                        consumer2,
                        "STREAMS",
                        key,
                        ">",
                    ]),
                ).toEqual({
                    [key]: {
                        [streamId2]: [
                            ["entry2_field1", "entry2_value1"],
                            ["entry2_field2", "entry2_value2"],
                        ],
                        [streamId3]: [["entry3_field1", "entry3_value1"]],
                    },
                });

                // Verify that xinfo_consumers contains info for 2 consumers now
                result = await client.xinfoConsumers(key, groupName1);
                expect(result.length).toEqual(2);

                // key exists, but it is not a stream
                expect(await client.set(stringKey, "foo")).toEqual("OK");
                await expect(
                    client.xinfoConsumers(stringKey, "_"),
                ).rejects.toThrow(RequestError);

                // Passing a non-existing key raises an error
                const key2 = uuidv4();
                await expect(client.xinfoConsumers(key2, "_")).rejects.toThrow(
                    RequestError,
                );

                expect(
                    await client.xadd(key2, [["field", "value"]], {
                        id: streamId4,
                    }),
                ).toEqual(streamId4);

                // Passing a non-existing group raises an error
                await expect(client.xinfoConsumers(key2, "_")).rejects.toThrow(
                    RequestError,
                );

                expect(
                    await client.xgroupCreate(key2, groupName1, "0-0"),
                ).toEqual("OK");
                expect(await client.xinfoConsumers(key2, groupName1)).toEqual(
                    [],
                );
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `xclaim test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const group = uuidv4();

                expect(
                    await client.xgroupCreate(key, group, "0", {
                        mkStream: true,
                    }),
                ).toEqual("OK");
                expect(
                    await client.customCommand([
                        "xgroup",
                        "createconsumer",
                        key,
                        group,
                        "consumer",
                    ]),
                ).toEqual(true);

                expect(
                    await client.xadd(
                        key,
                        [
                            ["entry1_field1", "entry1_value1"],
                            ["entry1_field2", "entry1_value2"],
                        ],
                        { id: "0-1" },
                    ),
                ).toEqual("0-1");
                expect(
                    await client.xadd(
                        key,
                        [["entry2_field1", "entry2_value1"]],
                        { id: "0-2" },
                    ),
                ).toEqual("0-2");

                expect(
                    await client.customCommand([
                        "xreadgroup",
                        "group",
                        group,
                        "consumer",
                        "STREAMS",
                        key,
                        ">",
                    ]),
                ).toEqual({
                    [key]: {
                        "0-1": [
                            ["entry1_field1", "entry1_value1"],
                            ["entry1_field2", "entry1_value2"],
                        ],
                        "0-2": [["entry2_field1", "entry2_value1"]],
                    },
                });

                expect(
                    await client.xclaim(key, group, "consumer", 0, ["0-1"]),
                ).toEqual({
                    "0-1": [
                        ["entry1_field1", "entry1_value1"],
                        ["entry1_field2", "entry1_value2"],
                    ],
                });
                expect(
                    await client.xclaimJustId(key, group, "consumer", 0, [
                        "0-2",
                    ]),
                ).toEqual(["0-2"]);

                // add one more entry
                expect(
                    await client.xadd(
                        key,
                        [["entry3_field1", "entry3_value1"]],
                        { id: "0-3" },
                    ),
                ).toEqual("0-3");
                // using force, we can xclaim the message without reading it
                expect(
                    await client.xclaimJustId(
                        key,
                        group,
                        "consumer",
                        0,
                        ["0-3"],
                        { isForce: true, retryCount: 99 },
                    ),
                ).toEqual(["0-3"]);

                // incorrect IDs - response is empty
                expect(
                    await client.xclaim(key, group, "consumer", 0, ["000"]),
                ).toEqual({});
                expect(
                    await client.xclaimJustId(key, group, "consumer", 0, [
                        "000",
                    ]),
                ).toEqual([]);

                // empty ID array
                await expect(
                    client.xclaim(key, group, "consumer", 0, []),
                ).rejects.toThrow(RequestError);

                // key exists, but it is not a stream
                const stringKey = uuidv4();
                expect(await client.set(stringKey, "foo")).toEqual("OK");
                await expect(
                    client.xclaim(stringKey, "_", "_", 0, ["_"]),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `lmpop test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster: RedisCluster) => {
                if (cluster.checkIfServerVersionLessThan("7.0.0")) {
                    return;
                }

                const key1 = "{key}" + uuidv4();
                const key2 = "{key}" + uuidv4();
                const nonListKey = uuidv4();
                const singleKeyArray = [key1];
                const multiKeyArray = [key2, key1];
                const count = 1;
                const lpushArgs = ["one", "two", "three", "four", "five"];
                const expected = { [key1]: ["five"] };
                const expected2 = { [key2]: ["one", "two"] };

                // nothing to be popped
                expect(
                    await client.lmpop(
                        singleKeyArray,
                        ListDirection.LEFT,
                        count,
                    ),
                ).toBeNull();

                // pushing to the arrays to be popped
                expect(await client.lpush(key1, lpushArgs)).toEqual(5);
                expect(await client.lpush(key2, lpushArgs)).toEqual(5);

                // checking correct result from popping
                expect(
                    await client.lmpop(singleKeyArray, ListDirection.LEFT),
                ).toEqual(expected);

                // popping multiple elements from the right
                expect(
                    await client.lmpop(multiKeyArray, ListDirection.RIGHT, 2),
                ).toEqual(expected2);

                // Key exists, but is not a set
                expect(await client.set(nonListKey, "lmpop")).toBe("OK");
                await expect(
                    client.lmpop([nonListKey], ListDirection.RIGHT),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `blmpop test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster: RedisCluster) => {
                if (cluster.checkIfServerVersionLessThan("7.0.0")) {
                    return;
                }

                const key1 = "{key}" + uuidv4();
                const key2 = "{key}" + uuidv4();
                const nonListKey = uuidv4();
                const singleKeyArray = [key1];
                const multiKeyArray = [key2, key1];
                const count = 1;
                const lpushArgs = ["one", "two", "three", "four", "five"];
                const expected = { [key1]: ["five"] };
                const expected2 = { [key2]: ["one", "two"] };

                // nothing to be popped
                expect(
                    await client.blmpop(
                        singleKeyArray,
                        ListDirection.LEFT,
                        0.1,
                        count,
                    ),
                ).toBeNull();

                // pushing to the arrays to be popped
                expect(await client.lpush(key1, lpushArgs)).toEqual(5);
                expect(await client.lpush(key2, lpushArgs)).toEqual(5);

                // checking correct result from popping
                expect(
                    await client.blmpop(
                        singleKeyArray,
                        ListDirection.LEFT,
                        0.1,
                    ),
                ).toEqual(expected);

                // popping multiple elements from the right
                expect(
                    await client.blmpop(
                        multiKeyArray,
                        ListDirection.RIGHT,
                        0.1,
                        2,
                    ),
                ).toEqual(expected2);

                // Key exists, but is not a set
                expect(await client.set(nonListKey, "blmpop")).toBe("OK");
                await expect(
                    client.blmpop([nonListKey], ListDirection.RIGHT, 0.1, 1),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `xgroupCreate and xgroupDestroy test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient, cluster) => {
                const key = uuidv4();
                const nonExistentKey = uuidv4();
                const stringKey = uuidv4();
                const groupName1 = uuidv4();
                const groupName2 = uuidv4();
                const streamId = "0-1";

                // trying to create a consumer group for a non-existing stream without the "MKSTREAM" arg results in error
                await expect(
                    client.xgroupCreate(nonExistentKey, groupName1, streamId),
                ).rejects.toThrow(RequestError);

                // calling with the "MKSTREAM" arg should create the new stream automatically
                expect(
                    await client.xgroupCreate(key, groupName1, streamId, {
                        mkStream: true,
                    }),
                ).toEqual("OK");

                // invalid arg - group names must be unique, but group_name1 already exists
                await expect(
                    client.xgroupCreate(key, groupName1, streamId),
                ).rejects.toThrow(RequestError);

                // Invalid stream ID format
                await expect(
                    client.xgroupCreate(
                        key,
                        groupName2,
                        "invalid_stream_id_format",
                    ),
                ).rejects.toThrow(RequestError);

                expect(await client.xgroupDestroy(key, groupName1)).toEqual(
                    true,
                );
                // calling xgroup_destroy again returns False because the group was already destroyed above
                expect(await client.xgroupDestroy(key, groupName1)).toEqual(
                    false,
                );

                // attempting to destroy a group for a non-existing key should raise an error
                await expect(
                    client.xgroupDestroy(nonExistentKey, groupName1),
                ).rejects.toThrow(RequestError);

                // "ENTRIESREAD" option was added in Valkey 7.0.0
                if (cluster.checkIfServerVersionLessThan("7.0.0")) {
                    await expect(
                        client.xgroupCreate(key, groupName1, streamId, {
                            entriesRead: "10",
                        }),
                    ).rejects.toThrow(RequestError);
                } else {
                    expect(
                        await client.xgroupCreate(key, groupName1, streamId, {
                            entriesRead: "10",
                        }),
                    ).toEqual("OK");

                    // invalid entries_read_id - cannot be the zero ("0-0") ID
                    await expect(
                        client.xgroupCreate(key, groupName1, streamId, {
                            entriesRead: "0-0",
                        }),
                    ).rejects.toThrow(RequestError);
                }

                // key exists, but it is not a stream
                expect(await client.set(stringKey, "foo")).toEqual("OK");
                await expect(
                    client.xgroupCreate(stringKey, groupName1, streamId, {
                        mkStream: true,
                    }),
                ).rejects.toThrow(RequestError);
                await expect(
                    client.xgroupDestroy(stringKey, groupName1),
                ).rejects.toThrow(RequestError);
            }, protocol);
        },
        config.timeout,
    );
}

export function runCommonTests<Context>(config: {
    init: () => Promise<{ context: Context; client: Client }>;
    close: (context: Context, testSucceeded: boolean) => void;
    timeout?: number;
}) {
    const runTest = async (test: (client: Client) => Promise<void>) => {
        const { context, client } = await config.init();
        let testSucceeded = false;

        try {
            await test(client);
            testSucceeded = true;
        } finally {
            config.close(context, testSucceeded);
        }
    };

    it(
        "set and get flow works",
        async () => {
            await runTest((client: Client) => GetAndSetRandomValue(client));
        },
        config.timeout,
    );

    it(
        "can set and get non-ASCII unicode without modification",
        async () => {
            await runTest(async (client: Client) => {
                const key = uuidv4();
                const value = "שלום hello 汉字";
                await client.set(key, value);
                const result = await client.get(key);
                expect(result).toEqual(value);
            });
        },
        config.timeout,
    );

    it(
        "get for missing key returns null",
        async () => {
            await runTest(async (client: Client) => {
                const result = await client.get(uuidv4());

                expect(result).toEqual(null);
            });
        },
        config.timeout,
    );

    it(
        "get for empty string",
        async () => {
            await runTest(async (client: Client) => {
                const key = uuidv4();
                await client.set(key, "");
                const result = await client.get(key);

                expect(result).toEqual("");
            });
        },
        config.timeout,
    );

    it(
        "send very large values",
        async () => {
            await runTest(async (client: Client) => {
                const WANTED_LENGTH = Math.pow(2, 16);

                const getLongUUID = () => {
                    let id = uuidv4();

                    while (id.length < WANTED_LENGTH) {
                        id += uuidv4();
                    }

                    return id;
                };

                const key = getLongUUID();
                const value = getLongUUID();
                await client.set(key, value);
                const result = await client.get(key);

                expect(result).toEqual(value);
            });
        },
        config.timeout,
    );

    it(
        "can handle concurrent operations without dropping or changing values",
        async () => {
            await runTest(async (client: Client) => {
                const singleOp = async (index: number) => {
                    if (index % 2 === 0) {
                        await GetAndSetRandomValue(client);
                    } else {
                        const result = await client.get(uuidv4());
                        expect(result).toEqual(null);
                    }
                };

                const operations: Promise<void>[] = [];

                for (let i = 0; i < 100; ++i) {
                    operations.push(singleOp(i));
                }

                await Promise.all(operations);
            });
        },
        config.timeout,
    );
}
