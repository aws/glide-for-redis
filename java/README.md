# Getting Started - Java Wrapper

## Notice: Java Wrapper - Work in Progress

We're excited to share that the Java client is currently in development! However, it's important to note that this client
is a work in progress and is not yet complete or fully tested. Your contributions and feedback are highly encouraged as
we work towards refining and improving this implementation. Thank you for your interest and understanding as we continue
to develop this Java wrapper.

The Java client contains the following parts:

1. `client`: A Java-wrapper around the rust-core client.
2. `examples`: An examples app to test the client against a Redis localhost
3. `benchmark`: A dedicated benchmarking tool designed to evaluate and compare the performance of GLIDE for Redis and other Java clients.
4. `integTest`: An integration test sub-project for API and E2E testing

## Installation and Setup

### Install from Gradle

At the moment, the Java client must be built from source.

### Build from source

Software Dependencies:

- JDK 11+
- git
- protoc (protobuf compiler)
- Rust

#### Prerequisites

**Dependencies installation for Ubuntu**
```bash
sudo apt update -y
sudo apt install -y protobuf-compiler openjdk-11-jdk openssl gcc
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

**Dependencies for MacOS**

Ensure that you have a minimum Java version of JDK 11 installed on your system:
```bash
 $ echo $JAVA_HOME
/Library/Java/JavaVirtualMachines/adoptopenjdk-11.jdk/Contents/Home

$ java -version
 openjdk version "11.0.1" 2018-10-16
 OpenJDK Runtime Environment 18.9 (build 11.0.1+13)
 OpenJDK 64-Bit Server VM 18.9 (build 11.0.1+13, mixed mode)
```

#### Building and installation steps
The Java client is currently a work in progress and offers no guarantees. Users should build at their own risk.

Before starting this step, make sure you've installed all software requirements.
1. Clone the repository:
```bash
VERSION=0.1.0 # You can modify this to other released version or set it to "main" to get the unstable branch
git clone --branch ${VERSION} https://github.com/aws/glide-for-redis.git
cd glide-for-redis
```
2. Initialize git submodule:
```bash
git submodule update --init --recursive
```
3. Generate protobuf files:
```bash
cd java/
./gradlew :client:protobuf
```
4. Build the client library:
```bash
cd java/
./gradlew :client:build
```
5. Run tests:
```bash
cd java/
$ ./gradlew :client:test
```

Other useful gradle developer commands:
* `./gradlew :client:test` to run client unit tests
* `./gradlew :integTest:test` to run client examples
* `./gradlew spotlessCheck` to check for codestyle issues
* `./gradlew spotlessApply` to apply codestyle recommendations
* `./gradlew :examples:run` to run client examples (make sure you have a running redis on port `6379`)
* `./gradlew :benchmarks:run` to run performance benchmarks

## Basic Examples

### Standalone Redis:

```java
import glide.api.GlideClient;

GlideClient client = GlideClient.CreateClient().get();

CompletableFuture<String> setResponse = client.set("key", "foobar");
assert setResponse.get() == "OK" : "Failed on client.set("key", "foobar") request";

CompletableFuture<String> getResponse = client.get("key");
assert getResponse.get() == "foobar" : "Failed on client.get("key") request";
```

### Benchmarks

You can run benchmarks using `./gradlew run`. You can set arguments using the args flag like:

```shell
./gradlew run --args="--help"
./gradlew run --args="--resultsFile=output --dataSize \"100 1000\" --concurrentTasks \"10 100\" --clients all --host localhost --port 6279 --clientCount \"1 5\" --tls"
```

The following arguments are accepted:
* `resultsFile`: the results output file
* `concurrentTasks`: Number of concurrent tasks
* `clients`: one of: all|jedis|lettuce|glide
* `clientCount`: Client count
* `host`: redis server host url
* `port`: redis server port number
* `tls`: redis TLS configured
