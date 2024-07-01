/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { writeFileSync } from "fs";
import { GlideClient, GlideClusterClient, Logger } from "glide-for-redis";
import { Cluster, Redis } from "ioredis";
import { parse } from "path";
import percentile from "percentile";
import { RedisClientType, createClient, createCluster } from "redis";
import { stdev } from "stats-lite";
import {
    generateKeyGet,
    generateKeySet,
    generateValue,
    getAddress,
    receivedOptions,
} from "../utilities/utils";

export const PROB_GET = 0.8;
export const PROB_GET_EXISTING_KEY = 0.8;

enum ChosenAction {
    GET_NON_EXISTING,
    GET_EXISTING,
    SET,
}
// Setting the internal logger to log every log that has a level of info and above,
// and save the logs to a file with the name of the results file.
Logger.setLoggerConfig("info", parse(receivedOptions.resultsFile).name);

let startedTasksCounter = 0;
const runningTasks: Promise<void>[] = [];
const benchJsonResults: object[] = [];

interface IAsyncClient {
    set: (key: string, value: string) => Promise<string | "OK" | null>;
    get: (key: string) => Promise<string | null>;
}

function chooseAction(): ChosenAction {
    if (Math.random() > PROB_GET) {
        return ChosenAction.SET;
    }

    if (Math.random() > PROB_GET_EXISTING_KEY) {
        return ChosenAction.GET_NON_EXISTING;
    }

    return ChosenAction.GET_EXISTING;
}

function calculateLatency(latencyList: number[], percentile_point: number) {
    const percentileCalculation = percentile(percentile_point, latencyList);
    const percentileValue = Array.isArray(percentileCalculation)
        ? percentileCalculation[0]
        : percentileCalculation;
    return Math.round(percentileValue * 100.0) / 100.0; // round to 2 decimal points
}

function printResults(resultsFile: string) {
    writeFileSync(resultsFile, JSON.stringify(benchJsonResults));
}

async function redisBenchmark(
    clients: IAsyncClient[],
    totalCommands: number,
    data: string,
    actionLatencies: Record<ChosenAction, number[]>,
) {
    while (startedTasksCounter < totalCommands) {
        startedTasksCounter += 1;
        const chosen_action = chooseAction();
        const tic = process.hrtime();
        const client = clients[startedTasksCounter % clients.length];

        switch (chosen_action) {
            case ChosenAction.GET_EXISTING:
                await client.get(generateKeySet());
                break;
            case ChosenAction.GET_NON_EXISTING:
                await client.get(generateKeyGet());
                break;
            case ChosenAction.SET:
                await client.set(generateKeySet(), data);
                break;
        }

        const toc = process.hrtime(tic);
        const latencyList = actionLatencies[chosen_action];
        latencyList.push(toc[0] * 1000 + toc[1] / 1000000);
    }
}

async function createBenchTasks(
    clients: IAsyncClient[],
    totalCommands: number,
    numOfConcurrentTasks: number,
    data: string,
    actionLatencies: Record<ChosenAction, number[]>,
) {
    startedTasksCounter = 0;
    const tic = process.hrtime();

    for (let i = 0; i < numOfConcurrentTasks; i++) {
        runningTasks.push(
            redisBenchmark(clients, totalCommands, data, actionLatencies),
        );
    }

    await Promise.all(runningTasks);
    const toc = process.hrtime(tic);
    return toc[0] + toc[1] / 1000000000;
}

function latencyResults(
    prefix: string,
    latencies: number[],
): Record<string, number> {
    const result: Record<string, number> = {};
    result[prefix + "_p50_latency"] = calculateLatency(latencies, 50);
    result[prefix + "_p90_latency"] = calculateLatency(latencies, 90);
    result[prefix + "_p99_latency"] = calculateLatency(latencies, 99);
    result[prefix + "_average_latency"] =
        latencies.reduce((a, b) => a + b, 0) / latencies.length;
    result[prefix + "_std_dev"] = stdev(latencies);

    return result;
}

async function runClients(
    clients: IAsyncClient[],
    clientName: string,
    totalCommands: number,
    numOfConcurrentTasks: number,
    dataSize: number,
    data: string,
    clientDisposal: (client: IAsyncClient) => void,
    isCluster: boolean,
) {
    const now = new Date();
    console.log(
        `Starting ${clientName} data size: ${dataSize} concurrency: ${numOfConcurrentTasks} client count: ${
            clients.length
        } isCluster: ${isCluster} ${now.toLocaleTimeString()}`,
    );
    const actionLatencies = {
        [ChosenAction.SET]: [],
        [ChosenAction.GET_NON_EXISTING]: [],
        [ChosenAction.GET_EXISTING]: [],
    };

    const time = await createBenchTasks(
        clients,
        totalCommands,
        numOfConcurrentTasks,
        data,
        actionLatencies,
    );
    const tps = Math.round(startedTasksCounter / time);

    const getNonExistingLatencies =
        actionLatencies[ChosenAction.GET_NON_EXISTING];
    const getNonExistingLatencyResults = latencyResults(
        "get_non_existing",
        getNonExistingLatencies,
    );

    const getExistingLatencies = actionLatencies[ChosenAction.GET_EXISTING];
    const getExistingLatencyResults = latencyResults(
        "get_existing",
        getExistingLatencies,
    );

    const setLatencies = actionLatencies[ChosenAction.SET];
    const setLatencyResults = latencyResults("set", setLatencies);

    const jsonRes = {
        client: clientName,
        num_of_tasks: numOfConcurrentTasks,
        data_size: dataSize,
        tps,
        client_count: clients.length,
        is_cluster: isCluster,
        ...setLatencyResults,
        ...getExistingLatencyResults,
        ...getNonExistingLatencyResults,
    };
    benchJsonResults.push(jsonRes);

    Promise.all(clients.map((client) => clientDisposal(client)));
}

function createClients(
    clientCount: number,
    createAction: () => Promise<IAsyncClient>,
): Promise<IAsyncClient[]> {
    const creationActions = Array.from({ length: clientCount }, () =>
        createAction(),
    );
    return Promise.all(creationActions);
}

async function main(
    totalCommands: number,
    numOfConcurrentTasks: number,
    dataSize: number,
    clientsToRun: "all" | "glide",
    host: string,
    clientCount: number,
    useTLS: boolean,
    clusterModeEnabled: boolean,
    port: number,
) {
    const data = generateValue(dataSize);

    if (clientsToRun == "all" || clientsToRun == "glide") {
        const clientClass = clusterModeEnabled
            ? GlideClusterClient
            : GlideClient;
        const clients = await createClients(clientCount, () =>
            clientClass.createClient({
                addresses: [{ host, port }],
                useTLS,
            }),
        );
        await runClients(
            clients,
            "glide",
            totalCommands,
            numOfConcurrentTasks,
            dataSize,
            data,
            (client) => {
                (client as GlideClient).close();
            },
            clusterModeEnabled,
        );
        await new Promise((resolve) => setTimeout(resolve, 100));
    }

    if (clientsToRun == "all") {
        const nodeGlideClients = await createClients(clientCount, async () => {
            const node = {
                url: getAddress(host, useTLS, port),
            };
            const nodeGlideClient = clusterModeEnabled
                ? createCluster({
                      rootNodes: [{ socket: { host, port, tls: useTLS } }],
                      defaults: {
                          socket: {
                              tls: useTLS,
                          },
                      },
                      useReplicas: true,
                  })
                : createClient(node);
            await nodeGlideClient.connect();
            return nodeGlideClient;
        });
        await runClients(
            nodeGlideClients,
            "node_redis",
            totalCommands,
            numOfConcurrentTasks,
            dataSize,
            data,
            (client) => {
                (client as RedisClientType).disconnect();
            },
            clusterModeEnabled,
        );
        await new Promise((resolve) => setTimeout(resolve, 100));

        const tls = useTLS ? {} : undefined;
        const ioredisClients = await createClients(clientCount, async () => {
            const ioredisClient = clusterModeEnabled
                ? new Cluster([{ host, port }], {
                      dnsLookup: (address, callback) => callback(null, address),
                      scaleReads: "all",
                      redisOptions: {
                          tls: {},
                      },
                  })
                : new Redis(port, host, {
                      tls,
                  });
            return ioredisClient;
        });
        await runClients(
            ioredisClients,
            "ioredis",
            totalCommands,
            numOfConcurrentTasks,
            dataSize,
            data,
            (client) => {
                (client as RedisClientType).disconnect();
            },
            clusterModeEnabled,
        );
    }
}

const numberOfIterations = (numOfConcurrentTasks: number) =>
    Math.min(Math.max(100000, numOfConcurrentTasks * 10000), 10000000);

Promise.resolve() // just added to clean the indentation of the rest of the calls
    .then(async () => {
        const dataSize = parseInt(receivedOptions.dataSize);
        const concurrentTasks: string[] = receivedOptions.concurrentTasks;
        const clientsToRun = receivedOptions.clients;
        const clientCount: string[] = receivedOptions.clientCount;
        const lambda: (
            numOfClients: string,
            concurrentTasks: string,
        ) => [number, number, number] = (
            numOfClients: string,
            concurrentTasks: string,
        ) => [parseInt(concurrentTasks), dataSize, parseInt(numOfClients)];
        const product: [number, number, number][] = concurrentTasks
            .flatMap((concurrentTasks: string) =>
                clientCount.map((clientCount) =>
                    lambda(clientCount, concurrentTasks),
                ),
            )
            .filter(
                ([concurrentTasks, , clientCount]) =>
                    clientCount <= concurrentTasks,
            );

        for (const [concurrentTasks, dataSize, clientCount] of product) {
            const iterations = receivedOptions.minimal
                ? 1000
                : numberOfIterations(concurrentTasks);
            await main(
                iterations,
                concurrentTasks,
                dataSize,
                clientsToRun,
                receivedOptions.host,
                clientCount,
                receivedOptions.tls,
                receivedOptions.clusterModeEnabled,
                receivedOptions.port,
            );
        }

        printResults(receivedOptions.resultsFile);
    })
    .then(() => {
        process.exit(0);
    });
