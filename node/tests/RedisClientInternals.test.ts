/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

import { beforeAll, describe, expect, it } from "@jest/globals";
import fs from "fs";
import {
    MAX_REQUEST_ARGS_LEN,
    createLeakedArray,
    createLeakedAttribute,
    createLeakedBigint,
    createLeakedDouble,
    createLeakedMap,
    createLeakedString,
} from "glide-rs";
import Long from "long";
import net from "net";
import os from "os";
import path from "path";
import { Reader } from "protobufjs";
import {
    BaseClientConfiguration,
    ClosingError,
    ClusterClientConfiguration,
    InfoOptions,
    Logger,
    RedisClient,
    RedisClientConfiguration,
    RedisClusterClient,
    RequestError,
    ReturnType,
    SlotKeyTypes,
    Transaction,
} from "..";
import {
    connection_request,
    redis_request,
    response,
} from "../src/ProtobufMessage";

const { RequestType, RedisRequest } = redis_request;

beforeAll(() => {
    Logger.init("info");
});

enum ResponseType {
    /** Type of a response that returns a null. */
    Null,
    /** Type of a response that returns a value which isn't an error. */
    Value,
    /** Type of response containing an error that impacts a single request. */
    RequestError,
    /** Type of response containing an error causes the connection to close. */
    ClosingError,
    /** Type of response containing the string "OK". */
    OK,
}

function createLeakedValue(value: ReturnType): Long {
    if (value == null) {
        return new Long(0, 0);
    }

    let pair = [0, 0];

    if (typeof value === "string") {
        pair = createLeakedString(value);
    } else if (value instanceof Array) {
        pair = createLeakedArray(value as string[]);
    } else if (typeof value === "object") {
        if ("attributes" in value) {
            pair = createLeakedAttribute(
                value.value as string,
                value.attributes as Record<string, string>,
            );
        } else {
            pair = createLeakedMap(value as Record<string, string>);
        }
    } else if (typeof value == "bigint") {
        pair = createLeakedBigint(value);
    } else if (typeof value == "number") {
        pair = createLeakedDouble(value);
    }

    return new Long(pair[0], pair[1]);
}

function sendResponse(
    socket: net.Socket,
    responseType: ResponseType,
    callbackIndex: number,
    response_data?: {
        message?: string;
        value?: ReturnType;
        requestErrorType?: response.RequestErrorType;
    },
) {
    const new_response = response.Response.create();
    new_response.callbackIdx = callbackIndex;

    if (responseType == ResponseType.Value) {
        const pointer = createLeakedValue(response_data?.value ?? "fake data");
        new_response.respPointer = pointer;
    } else if (responseType == ResponseType.ClosingError) {
        new_response.closingError = response_data?.message;
    } else if (responseType == ResponseType.RequestError) {
        new_response.requestError = new response.RequestError({
            type: response_data?.requestErrorType,
            message: response_data?.message,
        });
    } else if (responseType == ResponseType.Null) {
        // do nothing
    } else if (responseType == ResponseType.OK) {
        new_response.constantResponse = response.ConstantResponse.OK;
    } else {
        throw new Error("Got unknown response type: " + responseType);
    }

    const response_bytes =
        response.Response.encodeDelimited(new_response).finish();
    socket.write(response_bytes);
}

function getConnectionAndSocket(
    checkRequest?: (request: connection_request.ConnectionRequest) => boolean,
    connectionOptions?: ClusterClientConfiguration | RedisClientConfiguration,
    isCluster?: boolean,
): Promise<{
    socket: net.Socket;
    connection: RedisClient | RedisClusterClient;
    server: net.Server;
}> {
    return new Promise((resolve, reject) => {
        const temporaryFolder = fs.mkdtempSync(
            path.join(os.tmpdir(), `socket_listener`),
        );
        const socketName = path.join(temporaryFolder, "read");
        let connectionPromise: Promise<RedisClient | RedisClusterClient>; // eslint-disable-line prefer-const
        const server = net
            .createServer(async (socket) => {
                socket.once("data", (data) => {
                    const reader = Reader.create(data);
                    const request =
                        connection_request.ConnectionRequest.decodeDelimited(
                            reader,
                        );

                    if (checkRequest && !checkRequest(request)) {
                        reject(
                            `${JSON.stringify(request)}  did not pass condition`,
                        );
                    }

                    sendResponse(socket, ResponseType.Null, 0);
                });

                if (!connectionPromise) {
                    throw new Error("connectionPromise wasn't set");
                }

                const connection = await connectionPromise;
                resolve({
                    connection,
                    socket,
                    server,
                });
            })
            .listen(socketName);
        connectionPromise = new Promise((resolve) => {
            const socket = new net.Socket();
            socket.connect(socketName).once("connect", async () => {
                const options = connectionOptions ?? {
                    addresses: [{ host: "foo" }],
                };
                const connection = isCluster
                    ? await RedisClusterClient.__createClient(options, socket)
                    : await RedisClient.__createClient(options, socket);

                resolve(connection);
            });
        });
    });
}

function closeTestResources(
    connection: RedisClient | RedisClusterClient,
    server: net.Server,
    socket: net.Socket,
) {
    connection.close();
    server.close();
    socket.end();
}

async function testWithResources(
    testFunction: (
        connection: RedisClient | RedisClusterClient,
        socket: net.Socket,
    ) => Promise<void>,
    connectionOptions?: BaseClientConfiguration,
) {
    const { connection, server, socket } = await getConnectionAndSocket(
        undefined,
        connectionOptions,
    );

    await testFunction(connection, socket);

    closeTestResources(connection, server, socket);
}

async function testWithClusterResources(
    testFunction: (
        connection: RedisClusterClient,
        socket: net.Socket,
    ) => Promise<void>,
    connectionOptions?: BaseClientConfiguration,
) {
    const { connection, server, socket } = await getConnectionAndSocket(
        undefined,
        connectionOptions,
        true,
    );

    try {
        if (connection instanceof RedisClusterClient) {
            await testFunction(connection, socket);
        } else {
            throw new Error("Not cluster connection");
        }
    } finally {
        closeTestResources(connection, server, socket);
    }
}

async function testSentValueMatches(config: {
    sendRequest: (client: RedisClient | RedisClusterClient) => Promise<unknown>;
    expectedRequestType: redis_request.RequestType | null | undefined;
    expectedValue: unknown;
}) {
    let counter = 0;
    await testWithResources(async (connection, socket) => {
        socket.on("data", (data) => {
            const reader = Reader.create(data);
            const request = redis_request.RedisRequest.decodeDelimited(reader);
            expect(request.singleCommand?.requestType).toEqual(
                config.expectedRequestType,
            );

            expect(request.singleCommand?.argsArray?.args).toEqual(
                config.expectedValue,
            );

            counter = counter + 1;

            sendResponse(socket, ResponseType.Null, request.callbackIdx);
        });

        await config.sendRequest(connection);

        expect(counter).toEqual(1);
    });
}

describe("SocketConnectionInternals", () => {
    it("Test setup returns values", async () => {
        await testWithResources((connection, socket) => {
            expect(connection).toEqual(expect.anything());
            expect(socket).toEqual(expect.anything());
            return Promise.resolve();
        });
    });

    it("should close socket on close", async () => {
        await testWithResources(async (connection, socket) => {
            const endReceived = new Promise((resolve) => {
                socket.once("end", () => resolve(true));
            });
            connection.close();

            expect(await endReceived).toBeTruthy();
        });
    });

    describe("handling types", () => {
        const test_receiving_value = async (expected: ReturnType) => {
            await testWithResources(async (connection, socket) => {
                socket.once("data", (data) => {
                    const reader = Reader.create(data);
                    const request = RedisRequest.decodeDelimited(reader);
                    expect(request.singleCommand?.requestType).toEqual(
                        RequestType.GetString,
                    );
                    expect(
                        request.singleCommand?.argsArray?.args?.length,
                    ).toEqual(1);

                    sendResponse(
                        socket,
                        ResponseType.Value,
                        request.callbackIdx,
                        {
                            value: expected,
                        },
                    );
                });
                const result = await connection.get("foo");
                expect(result).toEqual(expected);
            });
        };

        it("should pass strings received from socket", async () => {
            await test_receiving_value("bar");
        });

        it("should pass maps received from socket", async () => {
            await test_receiving_value({ foo: "bar", bar: "baz" });
        });

        it("should pass arrays received from socket", async () => {
            await test_receiving_value(["foo", "bar", "baz"]);
        });

        it("should pass attributes received from socket", async () => {
            await test_receiving_value({
                value: "bar",
                attributes: { foo: "baz" },
            });
        });

        it("should pass bigints received from socket", async () => {
            await test_receiving_value(BigInt("9007199254740991"));
        });

        it("should pass floats received from socket", async () => {
            await test_receiving_value(0.75);
        });
    });

    it("should pass null returned from socket", async () => {
        await testWithResources(async (connection, socket) => {
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request = RedisRequest.decodeDelimited(reader);
                expect(request.singleCommand?.requestType).toEqual(
                    RequestType.GetString,
                );
                expect(request.singleCommand?.argsArray?.args?.length).toEqual(
                    1,
                );

                sendResponse(socket, ResponseType.Null, request.callbackIdx);
            });
            const result = await connection.get("foo");
            expect(result).toBeNull();
        });
    });

    it("should pass transaction with SlotKeyType", async () => {
        await testWithClusterResources(async (connection, socket) => {
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request = RedisRequest.decodeDelimited(reader);

                expect(
                    request.transaction?.commands?.at(0)?.requestType,
                ).toEqual(RequestType.SetString);
                expect(
                    request.transaction?.commands?.at(0)?.argsArray?.args
                        ?.length,
                ).toEqual(2);
                expect(request.route?.slotKeyRoute?.slotKey).toEqual("key");
                expect(request.route?.slotKeyRoute?.slotType).toEqual(0); // Primary = 0

                sendResponse(socket, ResponseType.OK, request.callbackIdx);
            });
            const transaction = new Transaction();
            transaction.set("key", "value");
            const slotKey: SlotKeyTypes = {
                type: "primarySlotKey",
                key: "key",
            };
            const result = await connection.exec(transaction, slotKey);
            expect(result).toBe("OK");
        });
    });

    it("should pass transaction with random node", async () => {
        await testWithClusterResources(async (connection, socket) => {
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request = RedisRequest.decodeDelimited(reader);

                expect(
                    request.transaction?.commands?.at(0)?.requestType,
                ).toEqual(RequestType.Info);
                expect(
                    request.transaction?.commands?.at(0)?.argsArray?.args
                        ?.length,
                ).toEqual(1);
                expect(request.route?.simpleRoutes).toEqual(
                    redis_request.SimpleRoutes.Random,
                );

                sendResponse(socket, ResponseType.Value, request.callbackIdx, {
                    value: "# Server",
                });
            });
            const transaction = new Transaction();
            transaction.info([InfoOptions.Server]);
            const result = await connection.exec(transaction, "randomNode");
            expect(result).toEqual(expect.stringContaining("# Server"));
        });
    });

    it("should pass OK returned from socket", async () => {
        await testWithResources(async (connection, socket) => {
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request = RedisRequest.decodeDelimited(reader);
                expect(request.singleCommand?.requestType).toEqual(
                    RequestType.SetString,
                );
                expect(request.singleCommand?.argsArray?.args?.length).toEqual(
                    2,
                );

                sendResponse(socket, ResponseType.OK, request.callbackIdx);
            });
            const result = await connection.set("foo", "bar");
            expect(result).toEqual("OK");
        });
    });

    it("should reject requests that received a response error", async () => {
        await testWithResources(async (connection, socket) => {
            const error = "check";
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request = RedisRequest.decodeDelimited(reader);
                expect(request.singleCommand?.requestType).toEqual(
                    RequestType.GetString,
                );
                expect(request.singleCommand?.argsArray?.args?.length).toEqual(
                    1,
                );
                sendResponse(
                    socket,
                    ResponseType.RequestError,
                    request.callbackIdx,
                    { message: error },
                );
            });
            const request = connection.get("foo");

            await expect(request).rejects.toEqual(new RequestError(error));
        });
    });

    it("should close all requests when receiving a closing error", async () => {
        await testWithResources(async (connection, socket) => {
            const error = "check";
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request = RedisRequest.decodeDelimited(reader);
                expect(request.singleCommand?.requestType).toEqual(
                    RequestType.GetString,
                );
                expect(request.singleCommand?.argsArray?.args?.length).toEqual(
                    1,
                );
                sendResponse(
                    socket,
                    ResponseType.ClosingError,
                    request.callbackIdx,
                    { message: error },
                );
            });
            const request1 = connection.get("foo");
            const request2 = connection.get("bar");

            await expect(request1).rejects.toEqual(new ClosingError(error));
            await expect(request2).rejects.toEqual(new ClosingError(error));
        });
    });

    it("should fail all requests when receiving a closing error with an unknown callback index", async () => {
        await testWithResources(async (connection, socket) => {
            const error = "check";
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request =
                    redis_request.RedisRequest.decodeDelimited(reader);
                expect(request.singleCommand?.requestType).toEqual(
                    RequestType.GetString,
                );
                sendResponse(
                    socket,
                    ResponseType.ClosingError,
                    Number.MAX_SAFE_INTEGER,
                    { message: error },
                );
            });
            const request1 = connection.get("foo");
            const request2 = connection.get("bar");

            await expect(request1).rejects.toEqual(new ClosingError(error));
            await expect(request2).rejects.toEqual(new ClosingError(error));
        });
    });

    it("should pass SET arguments", async () => {
        await testWithResources(async (connection, socket) => {
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request = RedisRequest.decodeDelimited(reader);
                expect(request.singleCommand?.requestType).toEqual(
                    RequestType.SetString,
                );
                const args = request.singleCommand?.argsArray?.args;

                if (!args) {
                    throw new Error("no args");
                }

                expect(args.length).toEqual(5);
                expect(args[0]).toEqual("foo");
                expect(args[1]).toEqual("bar");
                expect(args[2]).toEqual("XX");
                expect(args[3]).toEqual("GET");
                expect(args[4]).toEqual("EX 10");
                sendResponse(socket, ResponseType.OK, request.callbackIdx);
            });
            const request1 = connection.set("foo", "bar", {
                conditionalSet: "onlyIfExists",
                returnOldValue: true,
                expiry: { type: "seconds", count: 10 },
            });

            await expect(await request1).toMatch("OK");
        });
    });

    it("should send pointer for request with large size arguments", async () => {
        await testWithResources(async (connection, socket) => {
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request =
                    redis_request.RedisRequest.decodeDelimited(reader);
                expect(request.singleCommand?.requestType).toEqual(
                    RequestType.GetString,
                );
                expect(request.singleCommand?.argsVecPointer).not.toBeNull();
                expect(request.singleCommand?.argsArray).toBeNull();

                sendResponse(socket, ResponseType.Null, request.callbackIdx);
            });
            const key = "0".repeat(MAX_REQUEST_ARGS_LEN);
            const result = await connection.get(key);

            expect(result).toBeNull();
        });
    });

    it("should send vector of strings for request with small size arguments", async () => {
        await testWithResources(async (connection, socket) => {
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request =
                    redis_request.RedisRequest.decodeDelimited(reader);
                expect(request.singleCommand?.requestType).toEqual(
                    RequestType.GetString,
                );
                expect(request.singleCommand?.argsArray).not.toBeNull();
                expect(request.singleCommand?.argsVecPointer).toBeNull();

                sendResponse(socket, ResponseType.Null, request.callbackIdx);
            });
            const key = "0".repeat(MAX_REQUEST_ARGS_LEN - 1);
            const result = await connection.get(key);

            expect(result).toBeNull();
        });
    });

    it("should pass credentials on connection", async () => {
        const username = "this is a username";
        const password = "more like losername, amiright?";
        const { connection, server, socket } = await getConnectionAndSocket(
            (request: connection_request.ConnectionRequest) =>
                request.authenticationInfo?.password === password &&
                request.authenticationInfo?.username === username,
            {
                addresses: [{ host: "foo" }],
                credentials: { username, password },
            },
        );
        closeTestResources(connection, server, socket);
    });

    it("should pass database id", async () => {
        const { connection, server, socket } = await getConnectionAndSocket(
            (request: connection_request.ConnectionRequest) =>
                request.databaseId === 42,
            {
                addresses: [{ host: "foo" }],
                databaseId: 42,
            },
        );
        closeTestResources(connection, server, socket);
    });

    it("should pass periodic checks disabled", async () => {
        const { connection, server, socket } = await getConnectionAndSocket(
            (request: connection_request.ConnectionRequest) =>
                request.periodicChecksDisabled != null,
            {
                addresses: [{ host: "foo" }],
                periodicChecks: "disabled",
            },
            true,
        );
        closeTestResources(connection, server, socket);
    });

    it("should pass periodic checks with manual interval", async () => {
        const { connection, server, socket } = await getConnectionAndSocket(
            (request: connection_request.ConnectionRequest) =>
                request.periodicChecksManualInterval?.durationInSec === 20,
            {
                addresses: [{ host: "foo" }],
                periodicChecks: { duration_in_sec: 20 },
            },
            true,
        );
        closeTestResources(connection, server, socket);
    });

    it("shouldn't pass periodic checks parameter when set to default", async () => {
        const { connection, server, socket } = await getConnectionAndSocket(
            (request: connection_request.ConnectionRequest) =>
                request.periodicChecksManualInterval === null &&
                request.periodicChecksDisabled === null,
            {
                addresses: [{ host: "foo" }],
                periodicChecks: "enabledDefaultConfigs",
            },
            true,
        );
        closeTestResources(connection, server, socket);
    });

    it("should pass routing information from user", async () => {
        const route1 = "allPrimaries";
        const route2 = {
            type: "replicaSlotKey" as const,
            key: "foo",
        };
        await testWithClusterResources(async (connection, socket) => {
            socket.on("data", (data) => {
                const reader = Reader.create(data);
                const request =
                    redis_request.RedisRequest.decodeDelimited(reader);
                expect(request.singleCommand?.requestType).toEqual(
                    RequestType.CustomCommand,
                );

                if (request.singleCommand?.argsArray?.args?.at(0) === "SET") {
                    expect(request.route?.simpleRoutes).toEqual(
                        redis_request.SimpleRoutes.AllPrimaries,
                    );
                } else if (
                    request.singleCommand?.argsArray?.args?.at(0) === "GET"
                ) {
                    expect(request.route?.slotKeyRoute).toEqual({
                        slotType: redis_request.SlotTypes.Replica,
                        slotKey: "foo",
                    });
                } else {
                    throw new Error("unexpected command");
                }

                sendResponse(socket, ResponseType.Null, request.callbackIdx);
            });
            const result1 = await connection.customCommand(
                ["SET", "foo", "bar"],
                route1,
            );
            expect(result1).toBeNull();

            const result2 = await connection.customCommand(
                ["GET", "foo"],
                route2,
            );
            expect(result2).toBeNull();
        });
    });

    it("should set arguments according to xadd request", async () => {
        await testSentValueMatches({
            sendRequest: (client) =>
                client.xadd("foo", [
                    ["a", "1"],
                    ["b", "2"],
                ]),
            expectedRequestType: RequestType.XAdd,
            expectedValue: ["foo", "*", "a", "1", "b", "2"],
        });
    });

    it("should set arguments according to xadd options with makeStream: true", async () => {
        await testSentValueMatches({
            sendRequest: (client) =>
                client.xadd("bar", [["a", "1"]], {
                    id: "YOLO",
                    makeStream: true,
                }),
            expectedRequestType: RequestType.XAdd,
            expectedValue: ["bar", "YOLO", "a", "1"],
        });
    });

    it("should set arguments according to xadd options with trim", async () => {
        await testSentValueMatches({
            sendRequest: (client) =>
                client.xadd("baz", [["c", "3"]], {
                    trim: {
                        method: "maxlen",
                        threshold: 1000,
                        exact: true,
                    },
                }),
            expectedRequestType: RequestType.XAdd,
            expectedValue: ["baz", "MAXLEN", "=", "1000", "*", "c", "3"],
        });
    });

    it("should set arguments according to xadd options with makeStream: false and inexact trim", async () => {
        await testSentValueMatches({
            sendRequest: (client) =>
                client.xadd("foobar", [["d", "4"]], {
                    makeStream: false,
                    trim: {
                        method: "minid",
                        threshold: "foo",
                        exact: false,
                        limit: 1000,
                    },
                }),
            expectedRequestType: RequestType.XAdd,
            expectedValue: [
                "foobar",
                "NOMKSTREAM",
                "MINID",
                "~",
                "foo",
                "LIMIT",
                "1000",
                "*",
                "d",
                "4",
            ],
        });
    });

    it("should set arguments according to xtrim request", async () => {
        await testSentValueMatches({
            sendRequest: (client) =>
                client.xtrim("foo", {
                    method: "maxlen",
                    threshold: 1000,
                    exact: true,
                }),
            expectedRequestType: RequestType.XTrim,
            expectedValue: ["foo", "MAXLEN", "=", "1000"],
        });
    });

    it("should set arguments according to inexact xtrim request", async () => {
        await testSentValueMatches({
            sendRequest: (client) =>
                client.xtrim("bar", {
                    method: "minid",
                    threshold: "foo",
                    exact: false,
                    limit: 1000,
                }),
            expectedRequestType: RequestType.XTrim,
            expectedValue: ["bar", "MINID", "~", "foo", "LIMIT", "1000"],
        });
    });

    it("should set arguments according to xread request", async () => {
        await testSentValueMatches({
            sendRequest: (client) =>
                client.xread({
                    foo: "bar",
                    foobar: "baz",
                }),
            expectedRequestType: RequestType.XRead,
            expectedValue: ["STREAMS", "foo", "foobar", "bar", "baz"],
        });
    });

    it("should set arguments according to xread request with block clause", async () => {
        await testSentValueMatches({
            sendRequest: (client) =>
                client.xread(
                    { foo: "bar" },
                    {
                        block: 100,
                    },
                ),
            expectedRequestType: RequestType.XRead,
            expectedValue: ["BLOCK", "100", "STREAMS", "foo", "bar"],
        });
    });

    it("should set arguments according to xread request with count clause", async () => {
        await testSentValueMatches({
            sendRequest: (client) =>
                client.xread(
                    { bar: "baz" },
                    {
                        count: 2,
                    },
                ),
            expectedRequestType: RequestType.XRead,
            expectedValue: ["COUNT", "2", "STREAMS", "bar", "baz"],
        });
    });
});
