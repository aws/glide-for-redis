/* eslint-disable @typescript-eslint/no-var-requires */
const { AsyncClient } = require("glide-rs");
const RedisServer = require("redis-server");
const FreePort = require("find-free-port");
const { execFile } = require("child_process");

const PORT_NUMBER = 4001;
let server;
let port;

function flushallOnPort(port) {
    return new Promise((resolve, reject) => {
        execFile("redis-cli", ["-p", port, "FLUSHALL"], (error, _, stderr) => {
            if (error) {
                console.error(stderr);
                reject(error);
            } else {
                resolve();
            }
        });
    });
}

FreePort(PORT_NUMBER)
    .then(([free_port]) => {
        port = free_port;
        server = new RedisServer(port);
        server.open(async (err) => {
            if (err) {
                console.error("Error opening server:", err);
                throw err;
            }

            const client = AsyncClient.CreateConnection(
                `redis://localhost:${port}`,
            );
            await client.set("test", "test");
            let result = await client.get("test");

            if (result !== "test") {
                throw new Error("Common Test failed");
            } else {
                console.log("Common Test passed");
            }

            await flushallOnPort(port).then(() => {
                console.log("db flushed");
            });
            await server.close().then(() => {
                console.log("server closed");
            });
        });
    })
    .catch((error) => {
        console.error("Error occurred while finding a free port:", error);
    });
