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
import { v4 as uuidv4 } from "uuid";

import {
    ClusterTransaction,
    GlideClusterClient,
    InfoOptions,
    ProtocolVersion,
} from "..";
import { RedisCluster } from "../../utils/TestUtils.js";
import { checkIfServerVersionLessThan, runBaseTests } from "./SharedTests";
import {
    flushAndCloseClient,
    getClientConfigurationOption,
    getFirstResult,
    intoArray,
    intoString,
    parseCommandLineArgs,
    parseEndpoints,
    transactionTest,
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
            ? RedisCluster.initFromExistingCluster(
                  parseEndpoints(clusterAddresses),
              )
            : await RedisCluster.createCluster(true, 3, 0);
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
            expect(intoString(info_server)).toEqual(
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
            const expectedRes = await transactionTest(transaction);
            const result = await client.exec(transaction);
            expect(intoString(result)).toEqual(intoString(expectedRes));
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
            const result1 = await client1.customCommand(["WATCH", "key"]);
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

            const versionLessThan7 =
                await checkIfServerVersionLessThan("7.0.0");

            const promises: Promise<unknown>[] = [
                client.blpop(["abc", "zxy", "lkn"], 0.1),
                client.rename("abc", "zxy"),
                client.brpop(["abc", "zxy", "lkn"], 0.1),
                client.smove("abc", "zxy", "value"),
                client.renamenx("abc", "zxy"),
                client.sinter(["abc", "zxy", "lkn"]),
                client.zinterstore("abc", ["zxy", "lkn"]),
                client.sunionstore("abc", ["zxy", "lkn"]),
                client.pfcount(["abc", "zxy", "lkn"]),
                // TODO all rest multi-key commands except ones tested below
            ];

            if (!versionLessThan7) {
                promises.push(client.zintercard(["abc", "zxy", "lkn"]));
            }

            for (const promise of promises) {
                try {
                    await promise;
                } catch (e) {
                    expect((e as Error).message.toLowerCase()).toContain(
                        "crossslot",
                    );
                }
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
            // TODO touch
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
});
