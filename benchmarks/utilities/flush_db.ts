/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { RedisClientType, RedisClusterType } from "redis";
import { createRedisClient, receivedOptions } from "./utils";

async function flush_database(
    host: string,
    isCluster: boolean,
    tls: boolean,
    port: number,
) {
    if (isCluster) {
        const client = (await createRedisClient(
            host,
            isCluster,
            tls,
            port,
        )) as RedisClusterType;
        await Promise.all(
            client.masters.map((master) => {
                return flush_database(master.host, false, tls, master.port);
            }),
        );
        await client.quit();
    } else {
        const client = (await createRedisClient(
            host,
            isCluster,
            tls,
            port,
        )) as RedisClientType;
        await client.connect();
        await client.flushAll();
        await client.quit();
    }
}

Promise.resolve()
    .then(async () => {
        console.log("Flushing " + receivedOptions.host);
        await flush_database(
            receivedOptions.host,
            receivedOptions.clusterModeEnabled,
            receivedOptions.tls,
            receivedOptions.port,
        );
    })
    .then(() => {
        process.exit(0);
    });
