import { afterAll, beforeAll, describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import {
    BaseClientConfiguration,
    ConditionalChange,
    InfoOptions,
    ProtocolVersion,
    RedisClient,
    RedisClusterClient,
    parseInfoResponse,
} from "../../";
import { TRedisClient } from "../../src/Constants";
import * as json from "../../src/redis-modules/Json";
import { RedisCluster, getFirstResult } from "../TestUtilities";

type Context = {
    client: TRedisClient;
};

const TIMEOUT = 20000;

describe("JsonModule", () => {
    let testsFailed = 0;
    let cluster: RedisCluster;
    let loadModuleList: string[];

    beforeAll(async () => {
        const args = process.argv.slice(2);
        const loadModuleArgs = args.filter((arg) =>
            arg.startsWith("--load-module="),
        );

        if (loadModuleArgs.length === 0) {
            throw new Error(
                "No --load-module argument provided. Redis modules tests require --load-module argument to be specified.",
            );
        }

        loadModuleList = loadModuleArgs.map((arg) => arg.split("=")[1]);
    }, 20000);

    afterAll(async () => {
        if (testsFailed === 0) {
            await cluster.close();
        }
    });

    const getOptions = (
        ports: number[],
        protocol: ProtocolVersion,
    ): BaseClientConfiguration => {
        return {
            addresses: ports.map((port) => ({
                host: "localhost",
                port,
            })),
            protocol,
        };
    };

    const runBaseTests = (config: {
        init: (
            cluster_mode: boolean,
            protocol: ProtocolVersion,
            clientName?: string,
        ) => Promise<{
            context: Context;
            client: TRedisClient;
        }>;
        close: (context: Context, testSucceeded: boolean) => void;
        timeout?: number;
    }) => {
        const runTest = async (
            test: (client: TRedisClient) => Promise<void>,
            cluster_mode: boolean,
            protocol: ProtocolVersion,
            clientName?: string,
        ) => {
            const { context, client } = await config.init(
                cluster_mode,
                protocol,
                clientName,
            );
            let testSucceeded = false;

            try {
                await test(client);
                testSucceeded = true;
            } finally {
                config.close(context, testSucceeded);
            }
        };

        it.each([
            [true, ProtocolVersion.RESP2],
            [true, ProtocolVersion.RESP3],
            [false, ProtocolVersion.RESP2],
            [false, ProtocolVersion.RESP3],
        ])(
            `test ReJSON module is loaded_%p%p`,
            async (clusterMode, protocol) => {
                await runTest(
                    async (client: TRedisClient) => {
                        const info = parseInfoResponse(
                            getFirstResult(
                                await client.info([InfoOptions.Modules]),
                            ).toString(),
                        )["module"];
                        expect(info).toEqual(expect.stringContaining("ReJSON"));
                    },
                    clusterMode,
                    protocol,
                );
            },
            config.timeout,
        );

        it.each([
            [true, ProtocolVersion.RESP2],
            [true, ProtocolVersion.RESP3],
            [false, ProtocolVersion.RESP2],
            [false, ProtocolVersion.RESP3],
        ])(
            `test json set get_%p%p`,
            async (clusterMode, protocol) => {
                await runTest(
                    async (client: TRedisClient) => {
                        const key = uuidv4();
                        const json_value = { a: 1.0, b: 2 };
                        expect(
                            await json.set(
                                client,
                                key,
                                "$",
                                JSON.stringify(json_value),
                            ),
                        ).toBe("OK");

                        let result = await json.get(client, key, ".");
                        expect(typeof result).toBe("string");
                        expect(JSON.parse(result!)).toEqual(json_value);

                        result = await json.get(client, key, ["$.a", "$.b"]);
                        expect(typeof result).toBe("string");
                        expect(JSON.parse(result!)).toEqual({
                            "$.a": [1.0],
                            "$.b": [2],
                        });

                        expect(
                            await json.get(client, "non_existing_key", "$"),
                        ).toBeNull();
                        expect(await json.get(client, key, "$.d")).toBe("[]");
                    },
                    clusterMode,
                    protocol,
                );
            },
            config.timeout,
        );

        it.each([
            [true, ProtocolVersion.RESP2],
            [true, ProtocolVersion.RESP3],
            [false, ProtocolVersion.RESP2],
            [false, ProtocolVersion.RESP3],
        ])(
            `test json set get all data types_%p%p`,
            async (clusterMode, protocol) => {
                await runTest(
                    async (client: TRedisClient) => {
                        const key = uuidv4();
                        const json_value = {
                            integer: 42,
                            float: 3.14,
                            boolean: true,
                            string: "Hello, world!",
                            array: [1, 2, 3],
                            object: { nested: "data" },
                            nullValue: null,
                        };
                        expect(
                            await json.set(
                                client,
                                key,
                                "$",
                                JSON.stringify(json_value),
                            ),
                        ).toBe("OK");

                        let result = await json.get(client, key, ".");
                        expect(typeof result).toBe("string");
                        expect(JSON.parse(result!)).toEqual(json_value);

                        result = await json.get(client, key, [
                            "$.integer",
                            "$.string",
                        ]);
                        expect(typeof result).toBe("string");
                        expect(JSON.parse(result!)).toEqual({
                            "$.integer": [json_value.integer],
                            "$.string": [json_value.string],
                        });
                    },
                    clusterMode,
                    protocol,
                );
            },
            config.timeout,
        );

        it.each([
            [true, ProtocolVersion.RESP2],
            [true, ProtocolVersion.RESP3],
            [false, ProtocolVersion.RESP2],
            [false, ProtocolVersion.RESP3],
        ])(
            `Test JSON set and get multiple values with cluster mode: %s, and protocol: %s`,
            async (clusterMode, protocol) => {
                await runTest(
                    async (client: TRedisClient) => {
                        const key = uuidv4();
                        expect(
                            await json.set(
                                client,
                                key,
                                "$",
                                JSON.stringify({
                                    a: { c: 1, d: 4 },
                                    b: { c: 2 },
                                    c: true,
                                }),
                            ),
                        ).toBe("OK");

                        let result = await json.get(client, key, "$..c");
                        expect(JSON.parse(result!)).toEqual([true, 1, 2]);

                        result = await json.get(client, key, ["$..c", "$.c"]);
                        expect(JSON.parse(result!)).toEqual({
                            "$..c": [true, 1, 2],
                            "$.c": [true],
                        });

                        expect(
                            await json.set(client, key, "$..c", '"new_value"'),
                        ).toBe("OK");
                        result = await json.get(client, key, "$..c");
                        expect(typeof result).toBe("string");
                        expect(JSON.parse(result!)).toEqual([
                            "new_value",
                            "new_value",
                            "new_value",
                        ]);
                    },
                    clusterMode,
                    protocol,
                );
            },
            config.timeout,
        );

        it.each([
            [true, ProtocolVersion.RESP2],
            [true, ProtocolVersion.RESP3],
            [false, ProtocolVersion.RESP2],
            [false, ProtocolVersion.RESP3],
        ])(
            `test json set conditional set_%p%p`,
            async (clusterMode, protocol) => {
                await runTest(
                    async (client: TRedisClient) => {
                        const key = uuidv4();
                        const json_value = { a: 1.0, b: 2 };
                        expect(
                            await json.set(
                                client,
                                key,
                                "$",
                                JSON.stringify(json_value),
                                ConditionalChange.OnlyIfExist,
                            ),
                        ).toBeNull();

                        expect(
                            await json.set(
                                client,
                                key,
                                "$",
                                JSON.stringify(json_value),
                                ConditionalChange.OnlyIfDoesNotExist,
                            ),
                        ).toBe("OK");

                        expect(
                            await json.set(
                                client,
                                key,
                                "$.a",
                                "4.5",
                                ConditionalChange.OnlyIfDoesNotExist,
                            ),
                        ).toBeNull();

                        const result = await json.get(client, key, "$.a");
                        expect(JSON.parse(result!)).toEqual([1.0]);

                        expect(
                            await json.set(
                                client,
                                key,
                                "$.a",
                                "4.5",
                                ConditionalChange.OnlyIfExist,
                            ),
                        ).toBe("OK");

                        expect(await json.get(client, key, ".a")).toBe("4.5");
                    },
                    clusterMode,
                    protocol,
                );
            },
            config.timeout,
        );

        it.each([
            [true, ProtocolVersion.RESP2],
            [true, ProtocolVersion.RESP3],
            [false, ProtocolVersion.RESP2],
            [false, ProtocolVersion.RESP3],
        ])(
            `Test JSON get formatting, cluster mode: %s, and protocol: %s`,
            async (clusterMode, protocol) => {
                await runTest(
                    async (client: TRedisClient) => {
                        const key = uuidv4();
                        const json_value = { a: 1.5, b: 2, c: { d: 3, e: 4 } };
                        expect(
                            await json.set(
                                client,
                                key,
                                "$",
                                JSON.stringify(json_value),
                            ),
                        ).toBe("OK");

                        let result = await json.get(client, key, "$", {
                            indent: "  ",
                            newline: "\n",
                            space: " ",
                        });
                        let expected_result = `[\n  {\n    "a": 1.5,\n    "b": 2,\n    "c": {\n      "d": 3,\n      "e": 4\n    }\n  }\n]`;
                        expect(result).toEqual(expected_result);

                        result = await json.get(client, key, "$", {
                            indent: "~",
                            newline: "\n",
                            space: "*",
                        });

                        expected_result = `[\n~{\n~~"a":*1.5,\n~~"b":*2,\n~~"c":*{\n~~~"d":*3,\n~~~"e":*4\n~~}\n~}\n]`;
                        expect(result).toEqual(expected_result);
                    },
                    clusterMode,
                    protocol,
                );
            },
            config.timeout,
        );
    };

    runBaseTests({
        init: async (cluster_mode, protocol) => {
            const cluster = await RedisCluster.createCluster(
                cluster_mode,
                3,
                0,
                loadModuleList,
            );

            if (cluster_mode) {
                const client = await RedisClusterClient.createClient(
                    getOptions(cluster.ports(), protocol),
                );
                return { context: { client }, client };
            }

            const client = await RedisClient.createClient(
                getOptions(cluster.ports(), protocol),
            );

            return { context: { client }, client };
        },
        close: async (context: Context, testSucceeded: boolean) => {
            if (testSucceeded) {
                testsFailed -= 1;
            }

            context.client.close();
        },
        timeout: TIMEOUT,
    });
});
