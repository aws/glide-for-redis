/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { expect, it } from "@jest/globals";
import { exec } from "child_process";
import { v4 as uuidv4 } from "uuid";
import {
    ClosingError,
    ExpireOptions,
    GlideClient,
    GlideClusterClient,
    InfoOptions,
    InsertPosition,
    ProtocolVersion,
    Script,
    parseInfoResponse,
} from "../";
import {
    Client,
    GetAndSetRandomValue,
    checkSimple,
    compareMaps,
    getFirstResult,
    intoArray,
    intoString,
} from "./TestUtilities";

async function getVersion(): Promise<[number, number, number]> {
    const versionString = await new Promise<string>((resolve, reject) => {
        exec(`redis-server -v`, (error, stdout) => {
            if (error) {
                reject(error);
            } else {
                resolve(stdout);
            }
        });
    });
    const version = versionString.split("v=")[1].split(" ")[0];
    const numbers = version?.split(".");

    if (numbers.length != 3) {
        return [0, 0, 0];
    }

    return [parseInt(numbers[0]), parseInt(numbers[1]), parseInt(numbers[2])];
}

export async function checkIfServerVersionLessThan(
    minVersion: string,
): Promise<boolean> {
    const version = await getVersion();
    const versionToCompare =
        version[0].toString() +
        "." +
        version[1].toString() +
        "." +
        version[2].toString();
    return versionToCompare < minVersion;
}

export type BaseClient = GlideClient | GlideClusterClient;

export function runBaseTests<Context>(config: {
    init: (
        protocol: ProtocolVersion,
        clientName?: string,
    ) => Promise<{
        context: Context;
        client: BaseClient;
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
        test: (client: BaseClient) => Promise<void>,
        protocol: ProtocolVersion,
        clientName?: string,
    ) => {
        const { context, client } = await config.init(protocol, clientName);
        let testSucceeded = false;

        try {
            await test(client);
            testSucceeded = true;
        } finally {
            config.close(context, testSucceeded);
        }
    };

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `should register client library name and version_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                if (await checkIfServerVersionLessThan("7.2.0")) {
                    return;
                }

                const result = await client.customCommand(["CLIENT", "INFO"]);
                expect(intoString(result)).toContain("lib-name=GlideJS");
                expect(intoString(result)).toContain("lib-ver=unknown");
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
                    expect(intoString(await client.clientGetName())).toBe(
                        "TEST_CLIENT",
                    );
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
                checkSimple(setResult).toEqual("OK");
                const result = await client.customCommand(["GET", key]);
                checkSimple(result).toEqual(value);
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
                checkSimple(setResult1).toEqual("OK");
                const setResult2 = await client.customCommand([
                    "SET",
                    key2,
                    value2,
                ]);
                checkSimple(setResult2).toEqual("OK");
                const mget_result = await client.customCommand([
                    "MGET",
                    key1,
                    key2,
                    key3,
                ]);
                checkSimple(mget_result).toEqual([value1, value2, null]);
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
                const serverInfo = intoString(
                    await client.info([InfoOptions.Server]),
                );
                const conf_file = parseInfoResponse(
                    getFirstResult(serverInfo).toString(),
                )["config_file"];

                if (conf_file.length > 0) {
                    checkSimple(await client.configRewrite()).toEqual("OK");
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
                const oldResult = await client.info([InfoOptions.Commandstats]);
                const oldResultAsString = intoString(oldResult);
                console.log(oldResult);
                console.log(oldResultAsString);
                expect(oldResultAsString).toContain("cmdstat_set");
                checkSimple(await client.configResetStat()).toEqual("OK");

                const result = intoArray(
                    await client.info([InfoOptions.Commandstats]),
                );
                expect(result).not.toContain("cmdstat_set");
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
                checkSimple(await client.mset(keyValueList)).toEqual("OK");
                checkSimple(
                    await client.mget([key1, key2, "nonExistingKey", key3]),
                ).toEqual([value, value, null, value]);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `incr, incrBy and incrByFloat with existing key_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                checkSimple(await client.set(key, "10")).toEqual("OK");
                expect(await client.incr(key)).toEqual(11);
                checkSimple(await client.get(key)).toEqual("11");
                checkSimple(await client.incrBy(key, 4)).toEqual(15);
                checkSimple(await client.get(key)).toEqual("15");
                checkSimple(await client.incrByFloat(key, 1.5)).toEqual(16.5);
                checkSimple(await client.get(key)).toEqual("16.5");
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
                checkSimple(await client.get(key1)).toEqual("1");
                expect(await client.incrBy(key2, 2)).toEqual(2);
                checkSimple(await client.get(key2)).toEqual("2");
                expect(await client.incrByFloat(key3, -0.5)).toEqual(-0.5);
                checkSimple(await client.get(key3)).toEqual("-0.5");
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `incr, incrBy and incrByFloat with a key that contains a value of string that can not be represented as integer_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                checkSimple(await client.set(key, "foo")).toEqual("OK");

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
                checkSimple(await client.ping()).toEqual("PONG");
                checkSimple(await client.ping("Hello")).toEqual("Hello");
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
                checkSimple(await client.set(key, "10")).toEqual("OK");
                expect(await client.decr(key)).toEqual(9);
                checkSimple(await client.get(key)).toEqual("9");
                expect(await client.decrBy(key, 4)).toEqual(5);
                checkSimple(await client.get(key)).toEqual("5");
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
                checkSimple(await client.get(key1)).toEqual("-1");
                expect(await client.get(key2)).toBeNull();
                expect(await client.decrBy(key2, 3)).toEqual(-3);
                checkSimple(await client.get(key2)).toEqual("-3");
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
        `config get and config set with timeout parameter_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const prevTimeout = (await client.configGet([
                    "timeout",
                ])) as Record<string, string>;
                checkSimple(
                    await client.configSet({ timeout: "1000" }),
                ).toEqual("OK");
                const currTimeout = (await client.configGet([
                    "timeout",
                ])) as Record<string, string>;
                checkSimple(currTimeout).toEqual({ timeout: "1000" });
                /// Revert to the pervious configuration
                checkSimple(
                    await client.configSet({
                        timeout: prevTimeout["timeout"],
                    }),
                ).toEqual("OK");
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
                checkSimple(await client.hget(key, field1)).toEqual(value);
                checkSimple(await client.hget(key, field2)).toEqual(value);
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
                checkSimple(
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

                expect(intoString(await client.hgetall(key))).toEqual(
                    intoString({
                        [field1]: value,
                        [field2]: value,
                    }),
                );

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
                checkSimple(await client.hvals(key1)).toEqual([
                    "value1",
                    "value2",
                ]);
                expect(await client.hdel(key1, [field1])).toEqual(1);
                checkSimple(await client.hvals(key1)).toEqual(["value2"]);
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
                checkSimple(await client.hget(key1, field)).toEqual("value");

                checkSimple(await client.set(key2, "value")).toEqual("OK");
                await expect(
                    client.hsetnx(key2, field, "value"),
                ).rejects.toThrow();
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
                checkSimple(await client.lpop(key)).toEqual("value1");
                checkSimple(await client.lrange(key, 0, -1)).toEqual([
                    "value2",
                    "value3",
                    "value4",
                ]);
                checkSimple(await client.lpopCount(key, 2)).toEqual([
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
                checkSimple(await client.set(key, "foo")).toEqual("OK");

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
        `llen with existing, non-existing key and key that holds a value that is not a list_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                const valueList = ["value4", "value3", "value2", "value1"];
                expect(await client.lpush(key1, valueList)).toEqual(4);
                expect(await client.llen(key1)).toEqual(4);

                expect(await client.llen("nonExistingKey")).toEqual(0);

                checkSimple(await client.set(key2, "foo")).toEqual("OK");

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
        `ltrim with existing key and key that holds a value that is not a list_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const valueList = ["value4", "value3", "value2", "value1"];
                expect(await client.lpush(key, valueList)).toEqual(4);
                checkSimple(await client.ltrim(key, 0, 1)).toEqual("OK");
                checkSimple(await client.lrange(key, 0, -1)).toEqual([
                    "value1",
                    "value2",
                ]);

                /// `start` is greater than `end` so the key will be removed.
                checkSimple(await client.ltrim(key, 4, 2)).toEqual("OK");
                expect(await client.lrange(key, 0, -1)).toEqual([]);

                checkSimple(await client.set(key, "foo")).toEqual("OK");

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
                checkSimple(await client.lrange(key, 0, -1)).toEqual([
                    "value2",
                    "value2",
                    "value1",
                ]);
                expect(await client.lrem(key, -1, "value2")).toEqual(1);
                checkSimple(await client.lrange(key, 0, -1)).toEqual([
                    "value2",
                    "value1",
                ]);
                expect(await client.lrem(key, 0, "value2")).toEqual(1);
                checkSimple(await client.lrange(key, 0, -1)).toEqual([
                    "value1",
                ]);
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
                checkSimple(await client.rpop(key)).toEqual("value4");
                checkSimple(await client.rpopCount(key, 2)).toEqual([
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
                checkSimple(await client.set(key, "foo")).toEqual("OK");

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
                checkSimple(await client.smembers(key)).toEqual(
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
                checkSimple(await client.smembers(key1)).toEqual(
                    new Set(["2", "3"]),
                );
                checkSimple(await client.smembers(key2)).toEqual(
                    new Set(["1", "2", "3"]),
                );

                // moved element already exists in the destination set
                expect(await client.smove(key2, key1, "2"));
                checkSimple(await client.smembers(key1)).toEqual(
                    new Set(["2", "3"]),
                );
                checkSimple(await client.smembers(key2)).toEqual(
                    new Set(["1", "3"]),
                );

                // attempt to move from a non-existing key
                expect(await client.smove(non_existing_key, key1, "4")).toEqual(
                    false,
                );
                checkSimple(await client.smembers(key1)).toEqual(
                    new Set(["2", "3"]),
                );

                // move to a new set
                expect(await client.smove(key1, key3, "2"));
                checkSimple(await client.smembers(key1)).toEqual(
                    new Set(["3"]),
                );
                checkSimple(await client.smembers(key3)).toEqual(
                    new Set(["2"]),
                );

                // attempt to move a missing element
                expect(await client.smove(key1, key3, "42")).toEqual(false);
                checkSimple(await client.smembers(key1)).toEqual(
                    new Set(["3"]),
                );
                checkSimple(await client.smembers(key3)).toEqual(
                    new Set(["2"]),
                );

                // move missing element to missing key
                expect(
                    await client.smove(key1, non_existing_key, "42"),
                ).toEqual(false);
                checkSimple(await client.smembers(key1)).toEqual(
                    new Set(["3"]),
                );
                checkSimple(await client.type(non_existing_key)).toEqual(
                    "none",
                );

                // key exists, but it is not a set
                checkSimple(await client.set(string_key, "value")).toEqual(
                    "OK",
                );
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
                checkSimple(await client.set(key, "foo")).toEqual("OK");

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
                checkSimple(await client.sinter([key1, key2])).toEqual(
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
                checkSimple(await client.set(key2, "value")).toEqual("OK");

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
                checkSimple(await client.smembers(key4)).toEqual(
                    new Set(["a", "b", "c", "d", "e"]),
                );

                // overwrite existing set
                expect(await client.sunionstore(key1, [key4, key2])).toEqual(5);
                checkSimple(await client.smembers(key1)).toEqual(
                    new Set(["a", "b", "c", "d", "e"]),
                );

                // overwrite one of the source keys
                expect(await client.sunionstore(key2, [key4, key2])).toEqual(5);
                checkSimple(await client.smembers(key2)).toEqual(
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
                checkSimple(await client.set(stringKey, "foo")).toEqual("OK");
                await expect(
                    client.sunionstore(key4, [stringKey, key1]),
                ).rejects.toThrow();

                // overwrite destination when destination is not a set
                expect(
                    await client.sunionstore(stringKey, [key1, key3]),
                ).toEqual(7);
                checkSimple(await client.smembers(stringKey)).toEqual(
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

                checkSimple(await client.set(key2, "foo")).toEqual("OK");
                await expect(
                    client.sismember(key2, "member1"),
                ).rejects.toThrow();
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
                expect(members).toContain(intoString(result1));

                members = members.filter((item) => item != result1);
                const result2 = await client.spopCount(key, 2);
                expect(intoString(result2)).toEqual(intoString(members));
                expect(await client.spop("nonExistingKey")).toEqual(null);
                expect(await client.spopCount("nonExistingKey", 1)).toEqual(
                    new Set(),
                );
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
                checkSimple(await client.set(key1, value)).toEqual("OK");
                expect(await client.exists([key1])).toEqual(1);
                checkSimple(await client.set(key2, value)).toEqual("OK");
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
                checkSimple(await client.set(key1, value)).toEqual("OK");
                checkSimple(await client.set(key2, value)).toEqual("OK");
                checkSimple(await client.set(key3, value)).toEqual("OK");
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
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                checkSimple(await client.set(key, "foo")).toEqual("OK");
                expect(await client.expire(key, 10)).toEqual(true);
                expect(await client.ttl(key)).toBeLessThanOrEqual(10);
                /// set command clears the timeout.
                checkSimple(await client.set(key, "bar")).toEqual("OK");
                const versionLessThan =
                    await checkIfServerVersionLessThan("7.0.0");

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
                }

                expect(await client.ttl(key)).toBeLessThanOrEqual(15);
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `expireAt, pexpireAt and ttl with positive timeout_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                checkSimple(await client.set(key, "foo")).toEqual("OK");
                expect(
                    await client.expireAt(
                        key,
                        Math.floor(Date.now() / 1000) + 10,
                    ),
                ).toEqual(true);
                expect(await client.ttl(key)).toBeLessThanOrEqual(10);
                const versionLessThan =
                    await checkIfServerVersionLessThan("7.0.0");

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
                checkSimple(await client.set(key, "bar")).toEqual("OK");

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
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                checkSimple(await client.set(key, "foo")).toEqual("OK");
                expect(await client.ttl(key)).toEqual(-1);
                expect(await client.expire(key, -10)).toEqual(true);
                expect(await client.ttl(key)).toEqual(-2);
                checkSimple(await client.set(key, "foo")).toEqual("OK");
                expect(await client.pexpire(key, -10000)).toEqual(true);
                expect(await client.ttl(key)).toEqual(-2);
                checkSimple(await client.set(key, "foo")).toEqual("OK");
                expect(
                    await client.expireAt(
                        key,
                        Math.floor(Date.now() / 1000) - 50, /// timeout in the past
                    ),
                ).toEqual(true);
                expect(await client.ttl(key)).toEqual(-2);
                checkSimple(await client.set(key, "foo")).toEqual("OK");
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
            await runTest(async (client: BaseClient) => {
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
                checkSimple(await client.invokeScript(script)).toEqual("Hello");

                script = new Script(
                    Buffer.from("return redis.call('SET', KEYS[1], ARGV[1])"),
                );
                checkSimple(
                    await client.invokeScript(script, {
                        keys: [key1],
                        args: [Buffer.from("value1")],
                    }),
                ).toEqual("OK");

                /// Reuse the same script with different parameters.
                checkSimple(
                    await client.invokeScript(script, {
                        keys: [key2],
                        args: [Buffer.from("value2")],
                    }),
                ).toEqual("OK");

                script = new Script(
                    Buffer.from("return redis.call('GET', KEYS[1])"),
                );
                checkSimple(
                    await client.invokeScript(script, { keys: [key1] }),
                ).toEqual("value1");

                checkSimple(
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
                checkSimple(await client.invokeScript(script)).toEqual("Hello");

                script = new Script(
                    "return redis.call('SET', KEYS[1], ARGV[1])",
                );
                checkSimple(
                    await client.invokeScript(script, {
                        keys: [key1],
                        args: ["value1"],
                    }),
                ).toEqual("OK");

                /// Reuse the same script with different parameters.
                checkSimple(
                    await client.invokeScript(script, {
                        keys: [key2],
                        args: ["value2"],
                    }),
                ).toEqual("OK");

                script = new Script("return redis.call('GET', KEYS[1])");
                checkSimple(
                    await client.invokeScript(script, { keys: [key1] }),
                ).toEqual("value1");

                checkSimple(
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

                expect(await client.zadd(key, membersScores)).toEqual(3);
                expect(await client.zaddIncr(key, "one", 2)).toEqual(3.0);
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
                        conditionalChange: "onlyIfExists",
                    }),
                ).toEqual(0);

                expect(
                    await client.zadd(key, membersScores, {
                        conditionalChange: "onlyIfDoesNotExist",
                    }),
                ).toEqual(3);

                expect(
                    await client.zaddIncr(key, "one", 5.0, {
                        conditionalChange: "onlyIfDoesNotExist",
                    }),
                ).toEqual(null);

                expect(
                    await client.zaddIncr(key, "one", 5.0, {
                        conditionalChange: "onlyIfExists",
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
                    await client.zadd(
                        key,
                        membersScores,
                        {
                            updateOptions: "scoreGreaterThanCurrent",
                        },
                        true,
                    ),
                ).toEqual(1);

                expect(
                    await client.zadd(
                        key,
                        membersScores,
                        {
                            updateOptions: "scoreLessThanCurrent",
                        },
                        true,
                    ),
                ).toEqual(0);

                expect(
                    await client.zaddIncr(key, "one", -3.0, {
                        updateOptions: "scoreLessThanCurrent",
                    }),
                ).toEqual(7.0);

                expect(
                    await client.zaddIncr(key, "one", -3.0, {
                        updateOptions: "scoreGreaterThanCurrent",
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
            await runTest(async (client: BaseClient) => {
                if (await checkIfServerVersionLessThan("7.0.0")) {
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

                checkSimple(await client.set(key2, "foo")).toEqual("OK");
                await expect(client.zscore(key2, "foo")).rejects.toThrow();
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
                        "negativeInfinity",
                        "positiveInfinity",
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
                    await client.zcount(key1, "negativeInfinity", {
                        value: 3,
                    }),
                ).toEqual(3);
                expect(
                    await client.zcount(key1, "positiveInfinity", {
                        value: 3,
                    }),
                ).toEqual(0);
                expect(
                    await client.zcount(
                        "nonExistingKey",
                        "negativeInfinity",
                        "positiveInfinity",
                    ),
                ).toEqual(0);

                checkSimple(await client.set(key2, "foo")).toEqual("OK");
                await expect(
                    client.zcount(key2, "negativeInfinity", "positiveInfinity"),
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

                checkSimple(
                    await client.zrange(key, { start: 0, stop: 1 }),
                ).toEqual(["one", "two"]);
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
                checkSimple(
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

                checkSimple(
                    await client.zrange(key, {
                        start: "negativeInfinity",
                        stop: { value: 3, isInclusive: false },
                        type: "byScore",
                    }),
                ).toEqual(["one", "two"]);
                const result = await client.zrangeWithScores(key, {
                    start: "negativeInfinity",
                    stop: "positiveInfinity",
                    type: "byScore",
                });

                expect(
                    compareMaps(result, {
                        one: 1.0,
                        two: 2.0,
                        three: 3.0,
                    }),
                ).toBe(true);
                checkSimple(
                    await client.zrange(
                        key,
                        {
                            start: { value: 3, isInclusive: false },
                            stop: "negativeInfinity",
                            type: "byScore",
                        },
                        true,
                    ),
                ).toEqual(["two", "one"]);

                checkSimple(
                    await client.zrange(key, {
                        start: "negativeInfinity",
                        stop: "positiveInfinity",
                        limit: { offset: 1, count: 2 },
                        type: "byScore",
                    }),
                ).toEqual(["two", "three"]);

                expect(
                    await client.zrange(
                        key,
                        {
                            start: "negativeInfinity",
                            stop: { value: 3, isInclusive: false },
                            type: "byScore",
                        },
                        true,
                    ),
                ).toEqual([]);

                expect(
                    await client.zrange(key, {
                        start: "positiveInfinity",
                        stop: { value: 3, isInclusive: false },
                        type: "byScore",
                    }),
                ).toEqual([]);

                expect(
                    await client.zrangeWithScores(
                        key,
                        {
                            start: "negativeInfinity",
                            stop: { value: 3, isInclusive: false },
                            type: "byScore",
                        },
                        true,
                    ),
                ).toEqual({});

                expect(
                    await client.zrangeWithScores(key, {
                        start: "positiveInfinity",
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

                checkSimple(
                    await client.zrange(key, {
                        start: "negativeInfinity",
                        stop: { value: "c", isInclusive: false },
                        type: "byLex",
                    }),
                ).toEqual(["a", "b"]);

                checkSimple(
                    await client.zrange(key, {
                        start: "negativeInfinity",
                        stop: "positiveInfinity",
                        limit: { offset: 1, count: 2 },
                        type: "byLex",
                    }),
                ).toEqual(["b", "c"]);

                checkSimple(
                    await client.zrange(
                        key,
                        {
                            start: { value: "c", isInclusive: false },
                            stop: "negativeInfinity",
                            type: "byLex",
                        },
                        true,
                    ),
                ).toEqual(["b", "a"]);

                expect(
                    await client.zrange(
                        key,
                        {
                            start: "negativeInfinity",
                            stop: { value: "c", isInclusive: false },
                            type: "byLex",
                        },
                        true,
                    ),
                ).toEqual([]);

                expect(
                    await client.zrange(key, {
                        start: "positiveInfinity",
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
                checkSimple(await client.set(key, "value")).toEqual("OK");
                checkSimple(await client.type(key)).toEqual("string");
                checkSimple(await client.del([key])).toEqual(1);

                checkSimple(await client.lpush(key, ["value"])).toEqual(1);
                checkSimple(await client.type(key)).toEqual("list");
                checkSimple(await client.del([key])).toEqual(1);

                checkSimple(await client.sadd(key, ["value"])).toEqual(1);
                checkSimple(await client.type(key)).toEqual("set");
                checkSimple(await client.del([key])).toEqual(1);

                checkSimple(await client.zadd(key, { member: 1.0 })).toEqual(1);
                checkSimple(await client.type(key)).toEqual("zset");
                checkSimple(await client.del([key])).toEqual(1);

                checkSimple(await client.hset(key, { field: "value" })).toEqual(
                    1,
                );
                checkSimple(await client.type(key)).toEqual("hash");
                checkSimple(await client.del([key])).toEqual(1);

                await client.customCommand([
                    "XADD",
                    key,
                    "*",
                    "field",
                    "value",
                ]);
                checkSimple(await client.type(key)).toEqual("stream");
                checkSimple(await client.del([key])).toEqual(1);
                checkSimple(await client.type(key)).toEqual("none");
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `echo test_%p`,
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const message = uuidv4();
                checkSimple(await client.echo(message)).toEqual(message);
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
                checkSimple(await client.set(key1, key1Value)).toEqual("OK");
                checkSimple(await client.strlen(key1)).toEqual(key1ValueLength);

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
                checkSimple(await client.lindex(listName, 0)).toEqual(
                    listKey2Value,
                );
                checkSimple(await client.lindex(listName, 1)).toEqual(
                    listKey1Value,
                );
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
                checkSimple(await client.lrange(key1, 0, -1)).toEqual([
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
                checkSimple(await client.set(key, "value")).toEqual("OK");
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
                checkSimple(await client.set(key, "value")).toEqual("OK");
                await expect(client.zpopmax(key)).rejects.toThrow();
                expect(await client.zpopmax("notExsitingKey")).toEqual({});
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

                checkSimple(await client.set(key, "value")).toEqual("OK");
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
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                const membersScores = { one: 1.5, two: 2, three: 3 };
                expect(await client.zadd(key1, membersScores)).toEqual(3);
                expect(await client.zrank(key1, "one")).toEqual(0);

                if (!(await checkIfServerVersionLessThan("7.2.0"))) {
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

                checkSimple(await client.set(key2, "value")).toEqual("OK");
                await expect(client.zrank(key2, "member")).rejects.toThrow();
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
                checkSimple(await client.brpop(["brpop-test"], 0.1)).toEqual([
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
                checkSimple(await client.blpop(["blpop-test"], 0.1)).toEqual([
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
                checkSimple(await client.set(key, "foo")).toEqual("OK");
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
                checkSimple(timestamp1).toEqual("0-1");
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
                        "negativeInfinity",
                    ),
                ).toEqual(0);

                expect(
                    await client.zremRangeByScore(
                        "nonExistingKey",
                        "negativeInfinity",
                        "positiveInfinity",
                    ),
                ).toEqual(0);
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
                checkSimple(result).toEqual(expected);
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
                checkSimple(result).toEqual("value");
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
                checkSimple(await client.renamenx(key1, key2)).toEqual(true);
                // sanity check
                checkSimple(await client.get(key2)).toEqual("key1");
                // Test that renamenx doesn't rename key2 to key3 (with an existing value)
                checkSimple(await client.renamenx(key2, key3)).toEqual(false);
                // sanity check
                checkSimple(await client.get(key3)).toEqual("key3");
            }, protocol);
        },
        config.timeout,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "pfadd test_%p",
        async (protocol) => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                checkSimple(await client.pfadd(key, [])).toEqual(1);
                checkSimple(await client.pfadd(key, ["one", "two"])).toEqual(1);
                checkSimple(await client.pfadd(key, ["two"])).toEqual(0);
                checkSimple(await client.pfadd(key, [])).toEqual(0);

                // key exists, but it is not a HyperLogLog
                checkSimple(await client.set("foo", "value")).toEqual("OK");
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
        checkSimple(setResWithExpirySetMilli).toEqual("OK");
        const getWithExpirySetMilli = await client.get(key);
        checkSimple(getWithExpirySetMilli).toEqual(value);

        const setResWithExpirySec = await client.set(key, value, {
            expiry: {
                type: "seconds",
                count: 1,
            },
        });
        checkSimple(setResWithExpirySec).toEqual("OK");
        const getResWithExpirySec = await client.get(key);
        checkSimple(getResWithExpirySec).toEqual(value);

        const setWithUnixSec = await client.set(key, value, {
            expiry: {
                type: "unixSeconds",
                count: Math.floor(Date.now() / 1000) + 1,
            },
        });
        checkSimple(setWithUnixSec).toEqual("OK");
        const getWithUnixSec = await client.get(key);
        checkSimple(getWithUnixSec).toEqual(value);

        const setResWithExpiryKeep = await client.set(key, value, {
            expiry: "keepExisting",
        });
        checkSimple(setResWithExpiryKeep).toEqual("OK");
        const getResWithExpiryKeep = await client.get(key);
        checkSimple(getResWithExpiryKeep).toEqual(value);
        // wait for the key to expire base on the previous set
        let sleep = new Promise((resolve) => setTimeout(resolve, 1000));
        await sleep;
        const getResExpire = await client.get(key);
        // key should have expired
        checkSimple(getResExpire).toEqual(null);
        const setResWithExpiryWithUmilli = await client.set(key, value, {
            expiry: {
                type: "unixMilliseconds",
                count: Date.now() + 1000,
            },
        });
        checkSimple(setResWithExpiryWithUmilli).toEqual("OK");
        // wait for the key to expire
        sleep = new Promise((resolve) => setTimeout(resolve, 1001));
        await sleep;
        const getResWithExpiryWithUmilli = await client.get(key);
        // key should have expired
        checkSimple(getResWithExpiryWithUmilli).toEqual(null);
    }

    async function setWithOnlyIfExistOptions(client: BaseClient) {
        const key = uuidv4();
        const value = uuidv4();
        const setKey = await client.set(key, value);
        checkSimple(setKey).toEqual("OK");
        const getRes = await client.get(key);
        checkSimple(getRes).toEqual(value);
        const setExistingKeyRes = await client.set(key, value, {
            conditionalSet: "onlyIfExists",
        });
        checkSimple(setExistingKeyRes).toEqual("OK");
        const getExistingKeyRes = await client.get(key);
        checkSimple(getExistingKeyRes).toEqual(value);

        const notExistingKeyRes = await client.set(key + 1, value, {
            conditionalSet: "onlyIfExists",
        });
        // key does not exist, so it should not be set
        checkSimple(notExistingKeyRes).toEqual(null);
        const getNotExistingKey = await client.get(key + 1);
        // key should not have been set
        checkSimple(getNotExistingKey).toEqual(null);
    }

    async function setWithOnlyIfNotExistOptions(client: BaseClient) {
        const key = uuidv4();
        const value = uuidv4();
        const notExistingKeyRes = await client.set(key, value, {
            conditionalSet: "onlyIfDoesNotExist",
        });
        // key does not exist, so it should be set
        checkSimple(notExistingKeyRes).toEqual("OK");
        const getNotExistingKey = await client.get(key);
        // key should have been set
        checkSimple(getNotExistingKey).toEqual(value);

        const existingKeyRes = await client.set(key, value, {
            conditionalSet: "onlyIfDoesNotExist",
        });
        // key exists, so it should not be set
        checkSimple(existingKeyRes).toEqual(null);
        const getExistingKey = await client.get(key);
        // key should not have been set
        checkSimple(getExistingKey).toEqual(value);
    }

    async function setWithGetOldOptions(client: BaseClient) {
        const key = uuidv4();
        const value = uuidv4();

        const setResGetNotExistOld = await client.set(key, value, {
            returnOldValue: true,
        });
        // key does not exist, so old value should be null
        checkSimple(setResGetNotExistOld).toEqual(null);
        // key should have been set
        const getResGetNotExistOld = await client.get(key);
        checkSimple(getResGetNotExistOld).toEqual(value);

        const setResGetExistOld = await client.set(key, value, {
            returnOldValue: true,
        });
        // key exists, so old value should be returned
        checkSimple(setResGetExistOld).toEqual(value);
        // key should have been set
        const getResGetExistOld = await client.get(key);
        checkSimple(getResGetExistOld).toEqual(value);
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
                checkSimple(setRes).toEqual("OK");
                exist = true;
            } else {
                checkSimple(setRes).toEqual(null);
            }

            const getRes = await client.get(key);
            checkSimple(getRes).toEqual(value);
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
            await runTest(async (client: BaseClient) => {
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
                    await checkIfServerVersionLessThan("7.0.0");
                const versionLessThan72 =
                    await checkIfServerVersionLessThan("7.2.0");

                expect(await client.objectEncoding(non_existing_key)).toEqual(
                    null,
                );

                checkSimple(
                    await client.set(
                        string_key,
                        "a really loooooooooooooooooooooooooooooooooooooooong value",
                    ),
                ).toEqual("OK");

                checkSimple(await client.objectEncoding(string_key)).toEqual(
                    "raw",
                );

                checkSimple(await client.set(string_key, "2")).toEqual("OK");
                checkSimple(await client.objectEncoding(string_key)).toEqual(
                    "int",
                );

                checkSimple(await client.set(string_key, "value")).toEqual(
                    "OK",
                );
                checkSimple(await client.objectEncoding(string_key)).toEqual(
                    "embstr",
                );

                expect(await client.lpush(list_key, ["1"])).toEqual(1);

                if (versionLessThan72) {
                    checkSimple(await client.objectEncoding(list_key)).toEqual(
                        "quicklist",
                    );
                } else {
                    checkSimple(await client.objectEncoding(list_key)).toEqual(
                        "listpack",
                    );
                }

                // The default value of set-max-intset-entries is 512
                for (let i = 0; i < 513; i++) {
                    expect(
                        await client.sadd(hashtable_key, [String(i)]),
                    ).toEqual(1);
                }

                checkSimple(await client.objectEncoding(hashtable_key)).toEqual(
                    "hashtable",
                );

                expect(await client.sadd(intset_key, ["1"])).toEqual(1);
                checkSimple(await client.objectEncoding(intset_key)).toEqual(
                    "intset",
                );

                expect(await client.sadd(set_listpack_key, ["foo"])).toEqual(1);

                if (versionLessThan72) {
                    checkSimple(
                        await client.objectEncoding(set_listpack_key),
                    ).toEqual("hashtable");
                } else {
                    checkSimple(
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

                checkSimple(
                    await client.objectEncoding(hash_hashtable_key),
                ).toEqual("hashtable");

                expect(
                    await client.hset(hash_listpack_key, { "1": "2" }),
                ).toEqual(1);

                if (versionLessThan7) {
                    checkSimple(
                        await client.objectEncoding(hash_listpack_key),
                    ).toEqual("ziplist");
                } else {
                    checkSimple(
                        await client.objectEncoding(hash_listpack_key),
                    ).toEqual("listpack");
                }

                // The default value of zset-max-listpack-entries is 128
                for (let i = 0; i < 129; i++) {
                    expect(
                        await client.zadd(skiplist_key, { [String(i)]: 2.0 }),
                    ).toEqual(1);
                }

                checkSimple(await client.objectEncoding(skiplist_key)).toEqual(
                    "skiplist",
                );

                expect(
                    await client.zadd(zset_listpack_key, { "1": 2.0 }),
                ).toEqual(1);

                if (versionLessThan7) {
                    checkSimple(
                        await client.objectEncoding(zset_listpack_key),
                    ).toEqual("ziplist");
                } else {
                    checkSimple(
                        await client.objectEncoding(zset_listpack_key),
                    ).toEqual("listpack");
                }

                expect(
                    await client.xadd(stream_key, [["field", "value"]]),
                ).not.toBeNull();
                checkSimple(await client.objectEncoding(stream_key)).toEqual(
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
                checkSimple(result).toEqual(value);
            });
        },
        config.timeout,
    );

    it(
        "get for missing key returns null",
        async () => {
            await runTest(async (client: Client) => {
                const result = await client.get(uuidv4());

                checkSimple(result).toEqual(null);
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

                checkSimple(result).toEqual("");
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

                checkSimple(result).toEqual(value);
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
                        checkSimple(result).toEqual(null);
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
