/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import {
    SIZE_SET_KEYSPACE,
    createRedisClient,
    generateValue,
    receivedOptions,
} from "./utils";

async function fill_database(
    data_size: number,
    host: string,
    isCluster: boolean,
    tls: boolean,
    port: number,
) {
    const client = await createRedisClient(host, isCluster, tls, port);
    const data = generateValue(data_size);
    await client.connect();

    const CONCURRENT_SETS = 1000;
    const sets = Array.from(Array(CONCURRENT_SETS).keys()).map(
        async (index) => {
            for (let i = 0; i < SIZE_SET_KEYSPACE / CONCURRENT_SETS; ++i) {
                const key = (i * CONCURRENT_SETS + index).toString();
                await client.set(key, data);
            }
        },
    );

    await Promise.all(sets);
    await client.quit();
}

Promise.resolve()
    .then(async () => {
        console.log(
            `Filling ${receivedOptions.host} with data size ${receivedOptions.dataSize}`,
        );
        await fill_database(
            receivedOptions.dataSize,
            receivedOptions.host,
            receivedOptions.clusterModeEnabled,
            receivedOptions.tls,
            receivedOptions.port,
        );
    })
    .then(() => {
        process.exit(0);
    });
