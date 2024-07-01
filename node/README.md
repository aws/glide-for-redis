# GLIDE for Redis

General Language Independent Driver for the Enterprise (GLIDE) for Redis, is an AWS-sponsored, open-source Redis client. GLIDE for Redis works with any Redis distribution that adheres to the Redis Serialization Protocol (RESP) specification, including open-source Redis, Amazon ElastiCache for Redis, and Amazon MemoryDB for Redis.
Strategic, mission-critical Redis-based applications have requirements for security, optimized performance, minimal downtime, and observability. GLIDE for Redis is designed to provide a client experience that helps meet these objectives. It is sponsored and supported by AWS, and comes pre-configured with best practices learned from over a decade of operating Redis-compatible services used by hundreds of thousands of customers. To help ensure consistency in development and operations, GLIDE for Redis is implemented using a core driver framework, written in Rust, with extensions made available for each supported programming language. This design ensures that updates easily propagate to each language and reduces overall complexity. In this Preview release, GLIDE for Redis is available for Python and Javascript (Node.js), with support for Java actively under development.

## Supported Redis Versions

GLIDE for Redis is API-compatible with open source Redis version 6 and 7.

## Current Status

We've made GLIDE for Redis an open-source project, and are releasing it in Preview to the community to gather feedback, and actively collaborate on the project roadmap. We welcome questions and contributions from all Redis stakeholders.
This preview release is recommended for testing purposes only.

# Getting Started - Node Wrapper

## System Requirements

The beta release of GLIDE for Redis was tested on Intel x86_64 using Ubuntu 22.04.1, Amazon Linux 2023 (AL2023), and macOS 12.7.

## NodeJS supported version

Node.js 16.20 or higher.

## Installation and Setup

### Installing via Package Manager (npm)

To install GLIDE for Redis using `npm`, follow these steps:

1. Open your terminal.
2. Execute the command below:
    ```bash
    $ npm install @aws/glide-for-redis
    ```
3. After installation, confirm the client is installed by running:
    ```bash
    $ npm list
    myApp@ /home/ubuntu/myApp
    └── @aws/glide-for-redis@0.1.0
    ```

## Basic Examples

#### Cluster Redis:

```node
import { GlideClusterClient } from "@aws/glide-for-redis";

const addresses = [
    {
        host: "redis.example.com",
        port: 6379,
    },
];
const client = await GlideClusterClient.createClient({
    addresses: addresses,
});
await client.set("foo", "bar");
const value = await client.get("foo");
client.close();
```

#### Standalone Redis:

```node
import { GlideClient } from "@aws/glide-for-redis";

const addresses = [
    {
        host: "redis_primary.example.com",
        port: 6379,
    },
    {
        host: "redis_replica.example.com",
        port: 6379,
    },
];
const client = await GlideClient.createClient({
    addresses: addresses,
});
await client.set("foo", "bar");
const value = await client.get("foo");
client.close();
```

## Documentation

Visit our [wiki](https://github.com/aws/glide-for-redis/wiki/NodeJS-wrapper) for examples and further details on TLS, Read strategy, Timeouts and various other configurations.

### Building & Testing

Development instructions for local building & testing the package are in the [DEVELOPER.md](https://github.com/aws/glide-for-redis/blob/main/node/DEVELOPER.md#build-from-source) file.

### Supported platforms

Currentlly the package is supported on:

| Operation systems | C lib                | Architecture      |
| ----------------- | -------------------- | ----------------- |
| `Linux`           | `glibc`, `musl libc` | `x86_64`, `arm64` |
| `macOS`           | `Darwin`             | `x86_64`, `arm64` |
