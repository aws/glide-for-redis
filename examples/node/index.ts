/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { GlideClient, GlideClusterClient, Logger } from "@aws/glide-for-redis";

async function sendPingToNode() {
    // When in Redis is in standalone mode, add address of the primary node, and any replicas you'd like to be able to read from.
    const addresses = [
        {
            host: "localhost",
            port: 6379,
        },
    ];
    // Check `GlideClientConfiguration/ClusterClientConfiguration` for additional options.
    const client = await GlideClient.createClient({
        addresses: addresses,
        // if the server uses TLS, you'll need to enable it. Otherwise the connection attempt will time out silently.
        // useTLS: true,
        clientName: "test_standalone_client",
    });
    // The empty array signifies that there are no additional arguments.
    const pong = await client.customCommand(["PING"]);
    console.log(pong);
    await send_set_and_get(client);
    client.close();
}

async function send_set_and_get(client: GlideClient | GlideClusterClient) {
    const set_response = await client.set("foo", "bar");
    console.log(`Set response is = ${set_response}`);
    const get_response = await client.get("foo");
    console.log(`Get response is = ${get_response}`);
}

async function sendPingToRandomNodeInCluster() {
    // When in Redis is cluster mode, add address of any nodes, and the client will find all nodes in the cluster.
    const addresses = [
        {
            host: "localhost",
            port: 6380,
        },
    ];
    // Check `GlideClientConfiguration/ClusterClientConfiguration` for additional options.
    const client = await GlideClusterClient.createClient({
        addresses: addresses,
        // if the cluster nodes use TLS, you'll need to enable it. Otherwise the connection attempt will time out silently.
        // useTLS: true,
        clientName: "test_cluster_client",
    });
    // The empty array signifies that there are no additional arguments.
    const pong = await client.customCommand(["PING"], "randomNode");
    console.log(pong);
    await send_set_and_get(client);
    client.close();
}

function setFileLogger() {
    Logger.setLoggerConfig("warn", "glide.log");
}

function setConsoleLogger() {
    Logger.setLoggerConfig("warn");
}

setFileLogger();
await sendPingToNode();
setConsoleLogger();
await sendPingToRandomNodeInCluster();
