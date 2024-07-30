/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import {
    afterAll,
    afterEach,
    beforeAll,
    describe,
    expect,
    it,
} from "@jest/globals";
import { gte } from "semver";
import { v4 as uuidv4 } from "uuid";
import {
    BitwiseOperation,
    ClusterTransaction,
    FunctionListResponse,
    GlideClusterClient,
    InfoOptions,
    ListDirection,
    ProtocolVersion,
    RequestError,
    Routes,
    ScoreFilter,
} from "..";
import { RedisCluster } from "../../utils/TestUtils.js";
import { FlushMode, SortOrder } from "../build-ts/src/Commands";
import { runBaseTests } from "./SharedTests";
import {
    checkClusterResponse,
    checkFunctionListResponse,
    flushAndCloseClient,
    generateLuaLibCode,
    getClientConfigurationOption,
    getFirstResult,
    intoArray,
    intoString,
    parseCommandLineArgs,
    parseEndpoints,
    transactionTest,
    validateTransactionResponse,
} from "./TestUtilities";
type Context = {
    client: GlideClusterClient;
};

const TIMEOUT = 50000;

describe("GlideClusterClient", () => {
    let testsFailed = 0;
    let cluster: RedisCluster;
    let client: GlideClusterClient;
    beforeAll(async () => {
        const clusterAddresses = parseCommandLineArgs()["cluster-endpoints"];
        // Connect to cluster or create a new one based on the parsed addresses
        cluster = clusterAddresses
            ? await RedisCluster.initFromExistingCluster(
                  parseEndpoints(clusterAddresses),
              )
            : // setting replicaCount to 1 to facilitate tests routed to replicas
              await RedisCluster.createCluster(true, 3, 1);
    }, 20000);

    afterEach(async () => {
        await flushAndCloseClient(true, cluster.getAddresses(), client);
    });

    afterAll(async () => {
        if (testsFailed === 0) {
            await cluster.close();
        }
    });

    runBaseTests<Context>({
        init: async (protocol, clientName?) => {
            const options = getClientConfigurationOption(
                cluster.getAddresses(),
                protocol,
            );
            options.protocol = protocol;
            options.clientName = clientName;
            testsFailed += 1;
            client = await GlideClusterClient.createClient(options);
            return {
                context: {
                    client,
                },
                client,
                cluster,
            };
        },
        close: (context: Context, testSucceeded: boolean) => {
            if (testSucceeded) {
                testsFailed -= 1;
            }
        },
        timeout: TIMEOUT,
    });

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `info with server and replication_%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const info_server = getFirstResult(
                await client.info([InfoOptions.Server]),
            );
            expect(info_server).toEqual(
                expect.stringContaining("# Server"),
            );

            const infoReplicationValues = Object.values(
                await client.info([InfoOptions.Replication]),
            );

            const replicationInfo = intoArray(infoReplicationValues);

            for (const item of replicationInfo) {
                expect(item).toContain("role:master");
                expect(item).toContain("# Replication");
            }
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `info with server and randomNode route_%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const result = await client.info(
                [InfoOptions.Server],
                "randomNode",
            );
            expect(intoString(result)).toEqual(
                expect.stringContaining("# Server"),
            );
            expect(intoString(result)).toEqual(
                expect.not.stringContaining("# Errorstats"),
            );
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `route by address reaches correct node_%p`,
        async (protocol) => {
            // returns the line that contains the word "myself", up to that point. This is done because the values after it might change with time.
            const cleanResult = (value: string) => {
                return (
                    value
                        .split("\n")
                        .find((line: string) => line.includes("myself"))
                        ?.split("myself")[0] ?? ""
                );
            };

            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const result = cleanResult(
                intoString(
                    await client.customCommand(
                        ["cluster", "nodes"],
                        "randomNode",
                    ),
                ),
            );

            // check that routing without explicit port works
            const host = result.split(" ")[1].split("@")[0] ?? "";

            if (!host) {
                throw new Error("No host could be parsed");
            }

            const secondResult = cleanResult(
                intoString(
                    await client.customCommand(["cluster", "nodes"], {
                        type: "routeByAddress",
                        host,
                    }),
                ),
            );

            expect(result).toEqual(secondResult);

            const [host2, port] = host.split(":");

            // check that routing with explicit port works
            const thirdResult = cleanResult(
                intoString(
                    await client.customCommand(["cluster", "nodes"], {
                        type: "routeByAddress",
                        host: host2,
                        port: Number(port),
                    }),
                ),
            );

            expect(result).toEqual(thirdResult);
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `fail routing by address if no port is provided_%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            expect(() =>
                client.info(undefined, {
                    type: "routeByAddress",
                    host: "foo",
                }),
            ).toThrowError();
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `config get and config set transactions test_%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const transaction = new ClusterTransaction();
            transaction.configSet({ timeout: "1000" });
            transaction.configGet(["timeout"]);
            const result = await client.exec(transaction);
            expect(intoString(result)).toEqual(
                intoString(["OK", { timeout: "1000" }]),
            );
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `can send transactions_%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const transaction = new ClusterTransaction();

            const expectedRes = await transactionTest(
                transaction,
                cluster.getVersion(),
            );

            if (!cluster.checkIfServerVersionLessThan("7.0.0")) {
                transaction.publish("message", "key", true);
                expectedRes.push(['publish("message", "key", true)', 0]);

                transaction.pubsubShardChannels();
                expectedRes.push(["pubsubShardChannels()", []]);
                transaction.pubsubShardNumSub();
                expectedRes.push(["pubsubShardNumSub()", {}]);
            }

            const result = await client.exec(transaction);
            validateTransactionResponse(result, expectedRes);
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `can return null on WATCH transaction failures_%p`,
        async (protocol) => {
            const client1 = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const client2 = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const transaction = new ClusterTransaction();
            transaction.get("key");
            const result1 = await client1.watch(["key"]);
            expect(result1).toEqual("OK");

            const result2 = await client2.set("key", "foo");
            expect(result2).toEqual("OK");

            const result3 = await client1.exec(transaction);
            expect(result3).toBeNull();

            client1.close();
            client2.close();
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `echo with all nodes routing_%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const message = uuidv4();
            const echoDict = await client.echo(message, "allNodes");

            expect(typeof echoDict).toBe("object");
            expect(intoArray(echoDict)).toEqual(
                expect.arrayContaining(intoArray([message])),
            );
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `check that multi key command returns a cross slot error`,
        async (protocol) => {
            const client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            const promises: Promise<unknown>[] = [
                client.blpop(["abc", "zxy", "lkn"], 0.1),
                client.rename("abc", "zxy"),
                client.msetnx({ abc: "xyz", def: "abc", hij: "def" }),
                client.brpop(["abc", "zxy", "lkn"], 0.1),
                client.bitop(BitwiseOperation.AND, "abc", ["zxy", "lkn"]),
                client.smove("abc", "zxy", "value"),
                client.renamenx("abc", "zxy"),
                client.sinter(["abc", "zxy", "lkn"]),
                client.sinterstore("abc", ["zxy", "lkn"]),
                client.zinterstore("abc", ["zxy", "lkn"]),
                client.sunionstore("abc", ["zxy", "lkn"]),
                client.sunion(["abc", "zxy", "lkn"]),
                client.pfcount(["abc", "zxy", "lkn"]),
                client.pfmerge("abc", ["def", "ghi"]),
                client.sdiff(["abc", "zxy", "lkn"]),
                client.sdiffstore("abc", ["zxy", "lkn"]),
                client.sortStore("abc", "zyx"),
                client.sortStore("abc", "zyx", { isAlpha: true }),
                client.lmpop(["abc", "def"], ListDirection.LEFT, 1),
                client.blmpop(["abc", "def"], ListDirection.RIGHT, 0.1, 1),
            ];

            if (gte(cluster.getVersion(), "6.2.0")) {
                promises.push(
                    client.blmove(
                        "abc",
                        "def",
                        ListDirection.LEFT,
                        ListDirection.LEFT,
                        0.2,
                    ),
                    client.zdiff(["abc", "zxy", "lkn"]),
                    client.zdiffWithScores(["abc", "zxy", "lkn"]),
                    client.zdiffstore("abc", ["zxy", "lkn"]),
                    client.copy("abc", "zxy", true),
                );
            }

            if (gte(cluster.getVersion(), "7.0.0")) {
                promises.push(
                    client.sintercard(["abc", "zxy", "lkn"]),
                    client.zintercard(["abc", "zxy", "lkn"]),
                    client.zmpop(["abc", "zxy", "lkn"], ScoreFilter.MAX),
                    client.bzmpop(["abc", "zxy", "lkn"], ScoreFilter.MAX, 0.1),
                    client.lcs("abc", "xyz"),
                    client.lcsLen("abc", "xyz"),
                    client.lcsIdx("abc", "xyz"),
                );
            }

            for (const promise of promises) {
                await expect(promise).rejects.toThrowError(/crossslot/i);
            }

            client.close();
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `check that multi key command routed to multiple nodes`,
        async (protocol) => {
            const client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            await client.exists(["abc", "zxy", "lkn"]);
            await client.unlink(["abc", "zxy", "lkn"]);
            await client.del(["abc", "zxy", "lkn"]);
            await client.mget(["abc", "zxy", "lkn"]);
            await client.mset({ abc: "1", zxy: "2", lkn: "3" });
            await client.touch(["abc", "zxy", "lkn"]);
            await client.watch(["ghi", "zxy", "lkn"]);
            client.close();
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "object freq transaction test_%p",
        async (protocol) => {
            const client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            const key = uuidv4();
            const maxmemoryPolicyKey = "maxmemory-policy";
            const config = await client.configGet([maxmemoryPolicyKey]);
            const maxmemoryPolicy = String(config[maxmemoryPolicyKey]);

            try {
                const transaction = new ClusterTransaction();
                transaction.configSet({
                    [maxmemoryPolicyKey]: "allkeys-lfu",
                });
                transaction.set(key, "foo");
                transaction.objectFreq(key);

                const response = await client.exec(transaction);
                expect(response).not.toBeNull();

                if (response != null) {
                    expect(response.length).toEqual(3);
                    expect(response[0]).toEqual("OK");
                    expect(response[1]).toEqual("OK");
                    expect(response[2]).toBeGreaterThanOrEqual(0);
                }
            } finally {
                expect(
                    await client.configSet({
                        [maxmemoryPolicyKey]: maxmemoryPolicy,
                    }),
                ).toEqual("OK");
            }

            client.close();
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "object idletime transaction test_%p",
        async (protocol) => {
            const client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            const key = uuidv4();
            const maxmemoryPolicyKey = "maxmemory-policy";
            const config = await client.configGet([maxmemoryPolicyKey]);
            const maxmemoryPolicy = String(config[maxmemoryPolicyKey]);

            try {
                const transaction = new ClusterTransaction();
                transaction.configSet({
                    // OBJECT IDLETIME requires a non-LFU maxmemory-policy
                    [maxmemoryPolicyKey]: "allkeys-random",
                });
                transaction.set(key, "foo");
                transaction.objectIdletime(key);

                const response = await client.exec(transaction);
                expect(response).not.toBeNull();

                if (response != null) {
                    expect(response.length).toEqual(3);
                    // transaction.configSet({[maxmemoryPolicyKey]: "allkeys-random"});
                    expect(response[0]).toEqual("OK");
                    // transaction.set(key, "foo");
                    expect(response[1]).toEqual("OK");
                    // transaction.objectIdletime(key);
                    expect(response[2]).toBeGreaterThanOrEqual(0);
                }
            } finally {
                expect(
                    await client.configSet({
                        [maxmemoryPolicyKey]: maxmemoryPolicy,
                    }),
                ).toEqual("OK");
            }

            client.close();
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "object refcount transaction test_%p",
        async (protocol) => {
            const client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            const key = uuidv4();
            const transaction = new ClusterTransaction();
            transaction.set(key, "foo");
            transaction.objectRefcount(key);

            const response = await client.exec(transaction);
            expect(response).not.toBeNull();

            if (response != null) {
                expect(response.length).toEqual(2);
                expect(response[0]).toEqual("OK"); // transaction.set(key, "foo");
                expect(response[1]).toBeGreaterThanOrEqual(1); // transaction.objectRefcount(key);
            }

            client.close();
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `lolwut test_%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            // test with multi-node route
            const result1 = await client.lolwut({}, "allNodes");
            expect(intoString(result1)).toEqual(
                expect.stringContaining("Redis ver. "),
            );

            const result2 = await client.lolwut(
                { version: 2, parameters: [10, 20] },
                "allNodes",
            );
            expect(intoString(result2)).toEqual(
                expect.stringContaining("Redis ver. "),
            );

            // test with single-node route
            const result3 = await client.lolwut({}, "randomNode");
            expect(intoString(result3)).toEqual(
                expect.stringContaining("Redis ver. "),
            );

            const result4 = await client.lolwut(
                { version: 2, parameters: [10, 20] },
                "randomNode",
            );
            expect(intoString(result4)).toEqual(
                expect.stringContaining("Redis ver. "),
            );

            // transaction tests
            const transaction = new ClusterTransaction();
            transaction.lolwut();
            transaction.lolwut({ version: 5 });
            transaction.lolwut({ parameters: [1, 2] });
            transaction.lolwut({ version: 6, parameters: [42] });
            const results = await client.exec(transaction);

            if (results) {
                for (const element of results) {
                    expect(intoString(element)).toEqual(
                        expect.stringContaining("Redis ver. "),
                    );
                }
            } else {
                throw new Error("Invalid LOLWUT transaction test results.");
            }

            client.close();
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "copy test_%p",
        async (protocol) => {
            const client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            if (cluster.checkIfServerVersionLessThan("6.2.0")) return;

            const source = `{key}-${uuidv4()}`;
            const destination = `{key}-${uuidv4()}`;
            const value1 = uuidv4();
            const value2 = uuidv4();

            // neither key exists
            expect(await client.copy(source, destination, true)).toEqual(false);
            expect(await client.copy(source, destination)).toEqual(false);

            // source exists, destination does not
            expect(await client.set(source, value1)).toEqual("OK");
            expect(await client.copy(source, destination, false)).toEqual(true);
            expect(await client.get(destination)).toEqual(value1);

            // new value for source key
            expect(await client.set(source, value2)).toEqual("OK");

            // both exists, no REPLACE
            expect(await client.copy(source, destination)).toEqual(false);
            expect(await client.copy(source, destination, false)).toEqual(
                false,
            );
            expect(await client.get(destination)).toEqual(value1);

            // both exists, with REPLACE
            expect(await client.copy(source, destination, true)).toEqual(true);
            expect(await client.get(destination)).toEqual(value2);

            //transaction tests
            const transaction = new ClusterTransaction();
            transaction.set(source, value1);
            transaction.copy(source, destination, true);
            transaction.get(destination);
            const results = await client.exec(transaction);

            expect(results).toEqual(["OK", true, value1]);

            client.close();
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "flushdb flushall dbsize test_%p",
        async (protocol) => {
            const client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            expect(await client.dbsize()).toBeGreaterThanOrEqual(0);
            expect(await client.set(uuidv4(), uuidv4())).toEqual("OK");
            expect(await client.dbsize()).toBeGreaterThan(0);

            expect(await client.flushall()).toEqual("OK");
            expect(await client.dbsize()).toEqual(0);

            expect(await client.set(uuidv4(), uuidv4())).toEqual("OK");
            expect(await client.dbsize()).toEqual(1);
            expect(await client.flushdb(FlushMode.ASYNC)).toEqual("OK");
            expect(await client.dbsize()).toEqual(0);

            expect(await client.set(uuidv4(), uuidv4())).toEqual("OK");
            expect(await client.dbsize()).toEqual(1);
            expect(await client.flushdb(FlushMode.SYNC)).toEqual("OK");
            expect(await client.dbsize()).toEqual(0);

            client.close();
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "sort sortstore sort_store sortro sort_ro sortreadonly test_%p",
        async (protocol) => {
            const client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const key1 = "{sort}" + uuidv4();
            const key2 = "{sort}" + uuidv4();
            const key3 = "{sort}" + uuidv4();
            const key4 = "{sort}" + uuidv4();
            const key5 = "{sort}" + uuidv4();

            expect(await client.sort(key3)).toEqual([]);
            expect(await client.lpush(key1, ["2", "1", "4", "3"])).toEqual(4);
            expect(await client.sort(key1)).toEqual(["1", "2", "3", "4"]);

            // sort RO
            if (!cluster.checkIfServerVersionLessThan("7.0.0")) {
                expect(await client.sortReadOnly(key3)).toEqual([]);
                expect(await client.sortReadOnly(key1)).toEqual([
                    "1",
                    "2",
                    "3",
                    "4",
                ]);
            }

            // sort with store
            expect(await client.sortStore(key1, key2)).toEqual(4);
            expect(await client.lrange(key2, 0, -1)).toEqual([
                "1",
                "2",
                "3",
                "4",
            ]);

            // SORT with strings require ALPHA
            expect(
                await client.rpush(key3, ["2", "1", "a", "x", "c", "4", "3"]),
            ).toEqual(7);
            await expect(client.sort(key3)).rejects.toThrow(RequestError);
            expect(await client.sort(key3, { isAlpha: true })).toEqual([
                "1",
                "2",
                "3",
                "4",
                "a",
                "c",
                "x",
            ]);

            // check transaction and options
            const transaction = new ClusterTransaction()
                .lpush(key4, ["3", "1", "2"])
                .sort(key4, {
                    orderBy: SortOrder.DESC,
                    limit: { count: 2, offset: 0 },
                })
                .sortStore(key4, key5, {
                    orderBy: SortOrder.ASC,
                    limit: { count: 100, offset: 1 },
                })
                .lrange(key5, 0, -1);

            if (!cluster.checkIfServerVersionLessThan("7.0.0")) {
                transaction.sortReadOnly(key4, {
                    orderBy: SortOrder.DESC,
                    limit: { count: 2, offset: 0 },
                });
            }

            const result = await client.exec(transaction);
            const expectedResult = [3, ["3", "2"], 2, ["2", "3"]];

            if (!cluster.checkIfServerVersionLessThan("7.0.0")) {
                expectedResult.push(["3", "2"]);
            }

            expect(result).toEqual(expectedResult);

            client.close();
        },
    );

    describe.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "Protocol is RESP2 = %s",
        (protocol) => {
            describe.each([true, false])(
                "Single node route = %s",
                (singleNodeRoute) => {
                    it(
                        "function load and function list",
                        async () => {
                            if (cluster.checkIfServerVersionLessThan("7.0.0"))
                                return;

                            const client =
                                await GlideClusterClient.createClient(
                                    getClientConfigurationOption(
                                        cluster.getAddresses(),
                                        protocol,
                                    ),
                                );

                            try {
                                const libName =
                                    "mylib1C" + uuidv4().replaceAll("-", "");
                                const funcName =
                                    "myfunc1c" + uuidv4().replaceAll("-", "");
                                const code = generateLuaLibCode(
                                    libName,
                                    new Map([[funcName, "return args[1]"]]),
                                    true,
                                );
                                const route: Routes = singleNodeRoute
                                    ? { type: "primarySlotKey", key: "1" }
                                    : "allPrimaries";

                                let functionList = await client.functionList(
                                    { libNamePattern: libName },
                                    route,
                                );
                                checkClusterResponse(
                                    functionList as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual([]),
                                );
                                // load the library
                                expect(await client.functionLoad(code)).toEqual(
                                    libName,
                                );

                                functionList = await client.functionList(
                                    { libNamePattern: libName },
                                    route,
                                );
                                let expectedDescription = new Map<
                                    string,
                                    string | null
                                >([[funcName, null]]);
                                let expectedFlags = new Map<string, string[]>([
                                    [funcName, ["no-writes"]],
                                ]);

                                checkClusterResponse(
                                    functionList,
                                    singleNodeRoute,
                                    (value) =>
                                        checkFunctionListResponse(
                                            value as FunctionListResponse,
                                            libName,
                                            expectedDescription,
                                            expectedFlags,
                                        ),
                                );

                                // call functions from that library to confirm that it works
                                let fcall = await client.fcallWithRoute(
                                    funcName,
                                    ["one", "two"],
                                    route,
                                );
                                checkClusterResponse(
                                    fcall as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual("one"),
                                );
                                fcall = await client.fcallReadonlyWithRoute(
                                    funcName,
                                    ["one", "two"],
                                    route,
                                );
                                checkClusterResponse(
                                    fcall as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual("one"),
                                );

                                // re-load library without replace
                                await expect(
                                    client.functionLoad(code),
                                ).rejects.toThrow(
                                    `Library '${libName}' already exists`,
                                );

                                // re-load library with replace
                                expect(
                                    await client.functionLoad(code, true),
                                ).toEqual(libName);

                                // overwrite lib with new code
                                const func2Name =
                                    "myfunc2c" + uuidv4().replaceAll("-", "");
                                const newCode = generateLuaLibCode(
                                    libName,
                                    new Map([
                                        [funcName, "return args[1]"],
                                        [func2Name, "return #args"],
                                    ]),
                                    true,
                                );
                                expect(
                                    await client.functionLoad(newCode, true),
                                ).toEqual(libName);

                                functionList = await client.functionList(
                                    { libNamePattern: libName, withCode: true },
                                    route,
                                );
                                expectedDescription = new Map<
                                    string,
                                    string | null
                                >([
                                    [funcName, null],
                                    [func2Name, null],
                                ]);
                                expectedFlags = new Map<string, string[]>([
                                    [funcName, ["no-writes"]],
                                    [func2Name, ["no-writes"]],
                                ]);

                                checkClusterResponse(
                                    functionList,
                                    singleNodeRoute,
                                    (value) =>
                                        checkFunctionListResponse(
                                            value as FunctionListResponse,
                                            libName,
                                            expectedDescription,
                                            expectedFlags,
                                            newCode,
                                        ),
                                );

                                fcall = await client.fcallWithRoute(
                                    func2Name,
                                    ["one", "two"],
                                    route,
                                );
                                checkClusterResponse(
                                    fcall as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual(2),
                                );

                                fcall = await client.fcallReadonlyWithRoute(
                                    func2Name,
                                    ["one", "two"],
                                    route,
                                );
                                checkClusterResponse(
                                    fcall as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual(2),
                                );
                            } finally {
                                expect(await client.functionFlush()).toEqual(
                                    "OK",
                                );
                                client.close();
                            }
                        },
                        TIMEOUT,
                    );
                },
            );
        },
    );

    describe.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "Protocol is RESP2 = %s",
        (protocol) => {
            describe.each([true, false])(
                "Single node route = %s",
                (singleNodeRoute) => {
                    it(
                        "function flush",
                        async () => {
                            if (cluster.checkIfServerVersionLessThan("7.0.0"))
                                return;

                            const client =
                                await GlideClusterClient.createClient(
                                    getClientConfigurationOption(
                                        cluster.getAddresses(),
                                        protocol,
                                    ),
                                );

                            try {
                                const libName =
                                    "mylib1C" + uuidv4().replaceAll("-", "");
                                const funcName =
                                    "myfunc1c" + uuidv4().replaceAll("-", "");
                                const code = generateLuaLibCode(
                                    libName,
                                    new Map([[funcName, "return args[1]"]]),
                                    true,
                                );
                                const route: Routes = singleNodeRoute
                                    ? { type: "primarySlotKey", key: "1" }
                                    : "allPrimaries";

                                const functionList1 = await client.functionList(
                                    {},
                                    route,
                                );
                                checkClusterResponse(
                                    functionList1 as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual([]),
                                );

                                // load the library
                                expect(
                                    await client.functionLoad(
                                        code,
                                        undefined,
                                        route,
                                    ),
                                ).toEqual(libName);

                                // flush functions
                                expect(
                                    await client.functionFlush(
                                        FlushMode.SYNC,
                                        route,
                                    ),
                                ).toEqual("OK");
                                expect(
                                    await client.functionFlush(
                                        FlushMode.ASYNC,
                                        route,
                                    ),
                                ).toEqual("OK");

                                const functionList2 =
                                    await client.functionList();
                                checkClusterResponse(
                                    functionList2 as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual([]),
                                );

                                // Attempt to re-load library without overwriting to ensure FLUSH was effective
                                expect(
                                    await client.functionLoad(
                                        code,
                                        undefined,
                                        route,
                                    ),
                                ).toEqual(libName);
                            } finally {
                                expect(await client.functionFlush()).toEqual(
                                    "OK",
                                );
                                client.close();
                            }
                        },
                        TIMEOUT,
                    );
                },
            );
        },
    );

    describe.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "Protocol is RESP2 = %s",
        (protocol) => {
            describe.each([true, false])(
                "Single node route = %s",
                (singleNodeRoute) => {
                    it(
                        "function delete",
                        async () => {
                            if (cluster.checkIfServerVersionLessThan("7.0.0"))
                                return;

                            const client =
                                await GlideClusterClient.createClient(
                                    getClientConfigurationOption(
                                        cluster.getAddresses(),
                                        protocol,
                                    ),
                                );

                            try {
                                const libName =
                                    "mylib1C" + uuidv4().replaceAll("-", "");
                                const funcName =
                                    "myfunc1c" + uuidv4().replaceAll("-", "");
                                const code = generateLuaLibCode(
                                    libName,
                                    new Map([[funcName, "return args[1]"]]),
                                    true,
                                );
                                const route: Routes = singleNodeRoute
                                    ? { type: "primarySlotKey", key: "1" }
                                    : "allPrimaries";
                                let functionList = await client.functionList(
                                    {},
                                    route,
                                );
                                checkClusterResponse(
                                    functionList as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual([]),
                                );
                                // load the library
                                expect(
                                    await client.functionLoad(
                                        code,
                                        undefined,
                                        route,
                                    ),
                                ).toEqual(libName);

                                // Delete the function
                                expect(
                                    await client.functionDelete(libName, route),
                                ).toEqual("OK");

                                functionList = await client.functionList(
                                    { libNamePattern: libName, withCode: true },
                                    route,
                                );
                                checkClusterResponse(
                                    functionList as object,
                                    singleNodeRoute,
                                    (value) => expect(value).toEqual([]),
                                );

                                // Delete a non-existing library
                                await expect(
                                    client.functionDelete(libName, route),
                                ).rejects.toThrow(`Library not found`);
                            } finally {
                                expect(await client.functionFlush()).toEqual(
                                    "OK",
                                );
                                client.close();
                            }
                        },
                        TIMEOUT,
                    );
                },
            );
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `randomKey test_%p`,
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            const key = uuidv4();

            // setup: delete all keys
            expect(await client.flushall(FlushMode.SYNC)).toEqual("OK");

            // no keys exist so randomKey returns null
            expect(await client.randomKey()).toBeNull();

            expect(await client.set(key, "foo")).toEqual("OK");
            // `key` should be the only existing key, so randomKey should return `key`
            expect(await client.randomKey()).toEqual(key);
            expect(await client.randomKey("allPrimaries")).toEqual(key);

            client.close();
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "watch test_%p",
        async (protocol) => {
            const client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            const key1 = "{key}-1" + uuidv4();
            const key2 = "{key}-2" + uuidv4();
            const key3 = "{key}-3" + uuidv4();
            const key4 = "{key}-4" + uuidv4();
            const setFoobarTransaction = new ClusterTransaction();
            const setHelloTransaction = new ClusterTransaction();

            // Returns null when a watched key is modified before it is executed in a transaction command.
            // Transaction commands are not performed.
            expect(await client.watch([key1, key2, key3])).toEqual("OK");
            expect(await client.set(key2, "hello")).toEqual("OK");
            setFoobarTransaction
                .set(key1, "foobar")
                .set(key2, "foobar")
                .set(key3, "foobar");
            let results = await client.exec(setFoobarTransaction);
            expect(results).toEqual(null);
            // sanity check
            expect(await client.get(key1)).toEqual(null);
            expect(await client.get(key2)).toEqual("hello");
            expect(await client.get(key3)).toEqual(null);

            // Transaction executes command successfully with a read command on the watch key before
            // transaction is executed.
            expect(await client.watch([key1, key2, key3])).toEqual("OK");
            expect(await client.get(key2)).toEqual("hello");
            results = await client.exec(setFoobarTransaction);
            expect(results).toEqual(["OK", "OK", "OK"]);
            // sanity check
            expect(await client.get(key1)).toEqual("foobar");
            expect(await client.get(key2)).toEqual("foobar");
            expect(await client.get(key3)).toEqual("foobar");

            // Transaction executes command successfully with unmodified watched keys
            expect(await client.watch([key1, key2, key3])).toEqual("OK");
            results = await client.exec(setFoobarTransaction);
            expect(results).toEqual(["OK", "OK", "OK"]);
            // sanity check
            expect(await client.get(key1)).toEqual("foobar");
            expect(await client.get(key2)).toEqual("foobar");
            expect(await client.get(key3)).toEqual("foobar");

            // Transaction executes command successfully with a modified watched key but is not in the
            // transaction.
            expect(await client.watch([key4])).toEqual("OK");
            setHelloTransaction
                .set(key1, "hello")
                .set(key2, "hello")
                .set(key3, "hello");
            results = await client.exec(setHelloTransaction);
            expect(results).toEqual(["OK", "OK", "OK"]);
            // sanity check
            expect(await client.get(key1)).toEqual("hello");
            expect(await client.get(key2)).toEqual("hello");
            expect(await client.get(key3)).toEqual("hello");

            // WATCH can not have an empty String array parameter
            await expect(client.watch([])).rejects.toThrow(RequestError);

            client.close();
        },
        TIMEOUT,
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "unwatch test_%p",
        async (protocol) => {
            const client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            const key1 = "{key}-1" + uuidv4();
            const key2 = "{key}-2" + uuidv4();
            const setFoobarTransaction = new ClusterTransaction();

            // UNWATCH returns OK when there no watched keys
            expect(await client.unwatch()).toEqual("OK");

            // Transaction executes successfully after modifying a watched key then calling UNWATCH
            expect(await client.watch([key1, key2])).toEqual("OK");
            expect(await client.set(key2, "hello")).toEqual("OK");
            expect(await client.unwatch()).toEqual("OK");
            expect(await client.unwatch("allPrimaries")).toEqual("OK");
            setFoobarTransaction.set(key1, "foobar").set(key2, "foobar");
            const results = await client.exec(setFoobarTransaction);
            expect(results).toEqual(["OK", "OK"]);
            // sanity check
            expect(await client.get(key1)).toEqual("foobar");
            expect(await client.get(key2)).toEqual("foobar");

            client.close();
        },
        TIMEOUT,
    );
});
