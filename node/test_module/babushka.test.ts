import { AsyncClient, SocketConnection, setLoggerConfig } from "..";
import RedisServer from "redis-server";
/* eslint-disable @typescript-eslint/no-var-requires */
const FreePort = require("find-free-port");
import { v4 as uuidv4 } from "uuid";
import { pb_message } from "../src/ProtobufMessage";
import { BufferWriter, BufferReader } from "protobufjs";
import { describe, expect, beforeAll, it } from "@jest/globals";

function OpenServerAndExecute(port: number, action: () => Promise<void>) {
    return new Promise<void>((resolve, reject) => {
        const server = new RedisServer(port);
        server.open(async (err: Error | null) => {
            if (err) {
                reject(err);
            }
            await action();
            server.close();
            resolve();
        });
    });
}

beforeAll(() => {
    setLoggerConfig("info");
});

async function GetAndSetRandomValue(client: {
    set: (key: string, value: string) => Promise<string | "OK" | null>;
    get: (key: string) => Promise<string | null>;
}) {
    const key = uuidv4();
    // Adding random repetition, to prevent the inputs from always having the same alignment.
    const value = uuidv4() + "0".repeat(Math.random() * 7);
    const setResult = await client.set(key, value);
    expect(setResult).toEqual("OK");
    const result = await client.get(key);
    expect(result).toEqual(value);
}

describe("NAPI client", () => {
    it("set and get flow works", async () => {
        const port = await FreePort(3000).then(
            ([free_port]: number[]) => free_port
        );
        await OpenServerAndExecute(port, async () => {
            const client = await AsyncClient.CreateConnection(
                "redis://localhost:" + port
            );

            await GetAndSetRandomValue(client);
        });
    });

    it("can handle non-ASCII unicode", async () => {
        const port = await FreePort(3000).then(
            ([free_port]: number[]) => free_port
        );
        await OpenServerAndExecute(port, async () => {
            const client = await AsyncClient.CreateConnection(
                "redis://localhost:" + port
            );

            const key = uuidv4();
            const value = "שלום hello 汉字";
            await client.set(key, value);
            const result = await client.get(key);
            expect(result).toEqual(value);
        });
    });

    it("get for missing key returns null", async () => {
        const port = await FreePort(3000).then(
            ([free_port]: number[]) => free_port
        );
        await OpenServerAndExecute(port, async () => {
            const client = await AsyncClient.CreateConnection(
                "redis://localhost:" + port
            );

            const result = await client.get(uuidv4());

            expect(result).toEqual(null);
        });
    });

    it("get for empty string", async () => {
        const port = await FreePort(3000).then(
            ([free_port]: number[]) => free_port
        );
        await OpenServerAndExecute(port, async () => {
            const client = await AsyncClient.CreateConnection(
                "redis://localhost:" + port
            );

            const key = uuidv4();
            await client.set(key, "");
            const result = await client.get(key);

            expect(result).toEqual("");
        });
    });

    it("send very large values", async () => {
        const port = await FreePort(3000).then(
            ([free_port]: number[]) => free_port
        );
        await OpenServerAndExecute(port, async () => {
            const client = await AsyncClient.CreateConnection(
                "redis://localhost:" + port
            );

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
    });

    it("can handle concurrent operations", async () => {
        const port = await FreePort(3000).then(
            ([free_port]: number[]) => free_port
        );
        await OpenServerAndExecute(port, async () => {
            const client = await AsyncClient.CreateConnection(
                "redis://localhost:" + port
            );

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
    });
});

describe("socket client", () => {
    const getAddress = (port: number) => {
        return [{ address: "localhost", port }];
    };

    const getOptions = (port: number) => {
        return {
            addresses: getAddress(port),
        };
    };

    it("test protobuf encode/decode delimited", () => {
        // This test is required in order to verify that the autogenerated protobuf
        // files has been corrected and the encoding/decoding works as expected.
        // See "Manually compile protobuf files" in node/README.md to get more info about the fix.
        const writer = new BufferWriter();
        const request = {
            callbackIdx: 1,
            requestType: 2,
            argsArray: pb_message.Request.ArgsArray.create({args: ["bar1", "bar2"]}),
        };
        const request2 = {
            callbackIdx: 3,
            requestType: 4,
            argsArray: pb_message.Request.ArgsArray.create({args: ["bar3", "bar4"]}),
        };
        pb_message.Request.encodeDelimited(request, writer);
        pb_message.Request.encodeDelimited(request2, writer);
        const buffer = writer.finish();
        const reader = new BufferReader(buffer);

        const dec_msg1 = pb_message.Request.decodeDelimited(reader);
        expect(dec_msg1.callbackIdx).toEqual(1);
        expect(dec_msg1.requestType).toEqual(2);
        expect(dec_msg1.argsArray!.args).toEqual(["bar1", "bar2"]);

        const dec_msg2 = pb_message.Request.decodeDelimited(reader);
        expect(dec_msg2.callbackIdx).toEqual(3);
        expect(dec_msg2.requestType).toEqual(4);
        expect(dec_msg2.argsArray!.args).toEqual(["bar3", "bar4"]);
    });

    it("set and get flow works", async () => {
        const port = await FreePort(3000).then(
            ([free_port]: number[]) => free_port
        );
        await OpenServerAndExecute(port, async () => {
            const client = await SocketConnection.CreateConnection(
                getOptions(port)
            );

            await GetAndSetRandomValue(client);

            client.dispose();
        });
    });

    it("set and with return of old value works", async () => {
        const port = await FreePort(3000).then(
            ([free_port]: number[]) => free_port
        );
        await OpenServerAndExecute(port, async () => {
            const client = await SocketConnection.CreateConnection(
                getOptions(port)
            );

            const key = uuidv4();
            // Adding random repetition, to prevent the inputs from always having the same alignment.
            const value = uuidv4() + "0".repeat(Math.random() * 7);

            let result = await client.set(key, value);
            expect(result).toEqual("OK");

            result = await client.set(key, "", {
                returnOldValue: true,
            });
            expect(result).toEqual(value);

            result = await client.get(key);
            expect(result).toEqual("");

            client.dispose();
        });
    });

    it("conditional set works", async () => {
        const port = await FreePort(3000).then(
            ([free_port]: number[]) => free_port
        );
        await OpenServerAndExecute(port, async () => {
            const client = await SocketConnection.CreateConnection(
                getOptions(port)
            );

            const key = uuidv4();
            // Adding random repetition, to prevent the inputs from always having the same alignment.
            const value = uuidv4() + "0".repeat(Math.random() * 7);
            let result = await client.set(key, value, {
                conditionalSet: "onlyIfExists",
            });
            expect(result).toEqual(null);

            result = await client.set(key, value, {
                conditionalSet: "onlyIfDoesNotExist",
            });
            expect(result).toEqual("OK");
            expect(await client.get(key)).toEqual(value);

            result = await client.set(key, "foobar", {
                conditionalSet: "onlyIfDoesNotExist",
            });
            expect(result).toEqual(null);

            result = await client.set(key, "foobar", {
                conditionalSet: "onlyIfExists",
            });
            expect(result).toEqual("OK");

            expect(await client.get(key)).toEqual("foobar");

            client.dispose();
        });
    });

    it("can handle non-ASCII unicode", async () => {
        const port = await FreePort(3000).then(
            ([free_port]: number[]) => free_port
        );
        await OpenServerAndExecute(port, async () => {
            const client = await SocketConnection.CreateConnection(
                getOptions(port)
            );

            const key = uuidv4();
            const value = "שלום hello 汉字";
            await client.set(key, value);
            const result = await client.get(key);
            expect(result).toEqual(value);

            client.dispose();
        });
    });

    it("get for missing key returns null", async () => {
        const port = await FreePort(3000).then(
            ([free_port]: number[]) => free_port
        );
        await OpenServerAndExecute(port, async () => {
            const client = await SocketConnection.CreateConnection(
                getOptions(port)
            );

            const result = await client.get(uuidv4());

            expect(result).toEqual(null);

            client.dispose();
        });
    });

    it("get for empty string", async () => {
        const port = await FreePort(3000).then(
            ([free_port]: number[]) => free_port
        );
        await OpenServerAndExecute(port, async () => {
            const client = await SocketConnection.CreateConnection(
                getOptions(port)
            );

            const key = uuidv4();
            await client.set(key, "");
            const result = await client.get(key);

            expect(result).toEqual("");

            client.dispose();
        });
    });

    it("send very large values", async () => {
        const port = await FreePort(3000).then(
            ([free_port]: number[]) => free_port
        );
        await OpenServerAndExecute(port, async () => {
            const client = await SocketConnection.CreateConnection(
                getOptions(port)
            );

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

            client.dispose();
        });
    });

    it("can handle concurrent operations", async () => {
        const port = await FreePort(3000).then(
            ([free_port]: number[]) => free_port
        );
        await OpenServerAndExecute(port, async () => {
            const client = await SocketConnection.CreateConnection(
                getOptions(port)
            );

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

            client.dispose();
        });
    });
});
