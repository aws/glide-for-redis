/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

import { beforeAll, expect } from "@jest/globals";
import { exec } from "child_process";
import { v4 as uuidv4 } from "uuid";
import { ClusterTransaction, Logger, ReturnType, Transaction } from "..";
import { checkIfServerVersionLessThan } from "./SharedTests";

beforeAll(() => {
    Logger.init("info");
});

export type Client = {
    set: (key: string, value: string) => Promise<string | "OK" | null>;
    get: (key: string) => Promise<string | null>;
};

export async function GetAndSetRandomValue(client: Client) {
    const key = uuidv4();
    // Adding random repetition, to prevent the inputs from always having the same alignment.
    const value = uuidv4() + "0".repeat(Math.random() * 7);
    const setResult = await client.set(key, value);
    expect(setResult).toEqual("OK");
    const result = await client.get(key);
    expect(result).toEqual(value);
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

/// This function takes the first result of the response if it got more than one response (like cluster responses).
export function getFirstResult(
    res: string | number | Record<string, string> | Record<string, number>,
): string | number {
    if (typeof res == "string" || typeof res == "number") {
        return res;
    }

    return Object.values(res).at(0);
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
    const field = uuidv4();
    const value = uuidv4();
    const args: ReturnType[] = [];
    baseTransaction.set(key1, "bar");
    args.push("OK");
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
    baseTransaction.srem(key7, ["foo"]);
    args.push(1);
    baseTransaction.scard(key7);
    args.push(1);
    baseTransaction.sismember(key7, "bar");
    args.push(true);
    baseTransaction.smembers(key7);
    args.push(["bar"]);
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
    baseTransaction.zcount(key8, { bound: 2 }, "positiveInfinity");
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
    args.push(1);
    baseTransaction.xadd(key9, [["field", "value1"]], { id: "0-1" });
    args.push("0-1");
    baseTransaction.xadd(key9, [["field", "value2"]], { id: "0-2" });
    args.push("0-2");
    baseTransaction.xadd(key9, [["field", "value3"]], { id: "0-3" });
    args.push("0-3");
    baseTransaction.xread({ [key9]: "0-1" });
    args.push({
        [key9]: [
            ["0-2", ["field", "value2"]],
            ["0-3", ["field", "value3"]],
        ],
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
    baseTransaction.rpush(key6, [field + "1", field + "2", field + "3"]);
    args.push(3);
    baseTransaction.brpop([key6], 0.1);
    args.push([key6, field + "3"]);
    return args;
}

export class RedisCluster {
    private usedPorts: number[];
    private clusterFolder: string;

    private constructor(ports: number[], clusterFolder: string) {
        this.usedPorts = ports;
        this.clusterFolder = clusterFolder;
    }

    private static parseOutput(input: string): {
        clusterFolder: string;
        ports: number[];
    } {
        const lines = input.split(/\r\n|\r|\n/);
        const clusterFolder = lines
            .find((line) => line.startsWith("CLUSTER_FOLDER"))
            ?.split("=")[1];
        const ports = lines
            .find((line) => line.startsWith("CLUSTER_NODES"))
            ?.split("=")[1]
            .split(",")
            .map((address) => address.split(":")[1])
            .map((port) => Number(port));

        if (clusterFolder === undefined || ports === undefined) {
            throw new Error(`Insufficient data in input: ${input}`);
        }

        return {
            clusterFolder,
            ports,
        };
    }

    public static createCluster(
        cluster_mode: boolean,
        shardCount: number,
        replicaCount: number,
        loadModule?: string[],
    ): Promise<RedisCluster> {
        return new Promise<RedisCluster>((resolve, reject) => {
            let command = `python3 ../utils/cluster_manager.py start -r ${replicaCount} -n ${shardCount}`;

            if (cluster_mode) {
                command += " --cluster-mode";
            }

            if (loadModule) {
                if (loadModule.length === 0) {
                    throw new Error(
                        "Please provide the path(s) to the module(s) you want to load.",
                    );
                }

                for (const module of loadModule) {
                    command += ` --load-module ${module}`;
                }
            }

            console.log(command);
            exec(command, (error, stdout, stderr) => {
                if (error) {
                    console.error(stderr);
                    reject(error);
                } else {
                    const { clusterFolder, ports } = this.parseOutput(stdout);
                    resolve(new RedisCluster(ports, clusterFolder));
                }
            });
        });
    }

    public ports(): number[] {
        return [...this.usedPorts];
    }

    public async close() {
        await new Promise<void>((resolve, reject) =>
            exec(
                `python3 ../utils/cluster_manager.py stop --cluster-folder ${this.clusterFolder}`,
                (error, _, stderr) => {
                    if (error) {
                        console.error(stderr);
                        reject(error);
                    } else {
                        resolve();
                    }
                },
            ),
        );
    }
}
