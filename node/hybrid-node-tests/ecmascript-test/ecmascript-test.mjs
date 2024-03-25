import { execFile } from "child_process";
import findFreePorts from "find-free-ports";
import { AsyncClient } from "glide-rs";
import RedisServer from "redis-server";

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

port = await findFreePorts(PORT_NUMBER).then(([free_port]) => free_port);
server = await new Promise((resolve, reject) => {
    const server = new RedisServer(port);
    server.open(async (err) => {
        if (err) {
            reject(err);
        }

        resolve(server);
    });
});
const client = AsyncClient.CreateConnection("redis://localhost:" + port);
await client.set("test", "test");
let result = await client.get("test");

if (result !== "test") {
    throw new Error("Ecma Test failed");
} else {
    console.log("Ecma Test passed");
}

await flushallOnPort(port).then(() => {
    console.log("db flushed");
});
await server.close().then(() => {
    console.log("server closed");
});
