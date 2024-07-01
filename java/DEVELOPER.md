# Developer Guide

This document describes how to set up your development environment to build and test the GLIDE for Redis Java wrapper.

### Development Overview

The GLIDE for Redis Java wrapper consists of both Java and Rust code. Rust bindings for the Java Native Interface are implemented using [jni-rs](https://github.com/jni-rs/jni-rs), and the Java JAR is built using [Gradle](https://github.com/gradle/gradle). The Java and Rust components communicate using the [protobuf](https://github.com/protocolbuffers/protobuf) protocol.

### Build from source

#### Prerequisites

Software Dependencies

-   git
-   GCC
-   pkg-config
-   protoc (protobuf compiler) >= 26.1
-   openssl
-   openssl-dev
-   rustup
-   Java 11

**Install protobuf compiler**

To install protobuf for MacOS, run:
```bash
brew install protobuf
# Check that the protobuf compiler version 26.1 or higher is installed
protoc --version
```

For the remaining systems, do the following:
```bash
PB_REL="https://github.com/protocolbuffers/protobuf/releases"
curl -LO $PB_REL/download/v26.1/protoc-26.1-linux-x86_64.zip
unzip protoc-26.1-linux-x86_64.zip -d $HOME/.local
export PATH="$PATH:$HOME/.local/bin"
# Check that the protobuf compiler version 26.1 or higher is installed
protoc --version
```

**Dependencies installation for Ubuntu**

```bash
sudo apt update -y
sudo apt install -y openjdk-11-jdk git gcc pkg-config openssl libssl-dev unzip
# Install rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
# Check that the Rust compiler is installed
rustc --version
```

**Dependencies installation for CentOS**

```bash
sudo yum update -y
sudo yum install -y java-11-openjdk-devel git gcc pkgconfig openssl openssl-devel unzip
# Install rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```
**Dependencies installation for MacOS**

```bash
brew update
brew install openjdk@11 git gcc pkgconfig protobuf openssl protobuf
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

#### Building and installation steps

Before starting this step, make sure you've installed all software dependencies.

1. Clone the repository:
    ```bash
    VERSION=0.1.0 # You can modify this to other released version or set it to "main" to get the unstable branch
    git clone --branch ${VERSION} https://github.com/aws/glide-for-redis.git
    cd glide-for-redis/java
    ```
2. Initialize git submodule:
    ```bash
    git submodule update --init --recursive
    ```
3. Build the Java wrapper (Choose a build option from the following and run it from the `java` folder):

    1. Enter the java directory:

    ```bash
    cd java
    ```

    2. Build in debug mode:

    ```bash
    ./gradlew :client:buildAll
    ```

    3. Build in release mode:

    ```bash
    ./gradlew :client:buildAllRelease
    ```

### Linters

Development on the Java wrapper may involve changes in either the Java or Rust code. Each language has distinct linter tests that must be passed before committing changes.

Firstly, install the Rust linter
```bash
# Run from the `java` folder
# Will only need to run once during the installation process
rustup component add clippy rustfmt
cargo clippy --all-features --all-targets -- -D warnings
cargo fmt --manifest-path ./Cargo.toml --all
```

#### Language-specific Linters

**Java:**

#### Running the linters

For Java, the only linter we use is Spotless.
    Spotless

    ```bash
    # Run from the `java` folder
    ./gradlew :spotlessCheck # run first to see if there are any linting changes to make
    ./gradlew :spotlessApply # to apply these changes
    ```

### Troubleshooting

Some troubleshooting issues:
- Failed to find `cargo` after `rustup`: `gradle` daemon may need to be restarted via `./gradlew --stop` to recognize the new `$PATH`. If that doesn't work, you may need to restart your machine.
- If build fails because of rust compiler fails, make sure submodules are updated using `git submodule update`.
- If protobuf 26.0 or earlier is detected, upgrade to the latest protobuf release.

### Test

To run all tests, use the following command:

```bash
./gradlew test
```

To run the unit tests, use the following command:

```bash
./gradlew :client:test
```

To run FFI tests between Java and Rust, use the following command:

```bash
./gradlew :client:testFfi
```

To run end-to-end tests, use the following command:

```bash
./gradlew :integTest:test
```

To run a single test, use the following command:
```bash
./gradlew :integTest:test --tests '*.functionLoad_and_functionList' --rerun
```

To run one class, use the following command:
```bash
./gradlew :client:test --tests 'TransactionTests' --rerun
```

### Generate files
To (re)generate protobuf code, use the following command:

```bash
./gradlew protobuf
```

### Submodules

After pulling new changes, ensure that you update the submodules by running the following command:

```bash
git submodule update
```

### Contributing new ValKey commands

A redis command can either have a standalone or cluster implementation which is dependent on their specifications.
- A node is an instance of a Redis server, and a redis cluster is composed of multiple nodes working in tandem.
- A cluster command will require a note to indicate a node will follow a specific routing.
Refer to https://redis.io/docs/latest/operate/oss_and_stack/reference/cluster-spec for more details on how hash slots work for cluster commands.

When you start implementing a new command, check the [redis_request.proto](https://github.com/aws/glide-for-redis/blob/main/glide-core/src/protobuf/redis_request.proto) and [request_type.rs](https://github.com/aws/glide-for-redis/blob/main/glide-core/src/request_type.rs) files to see whether the command has already been implemented in another language such as Python or Node.js.

Standalone and cluster clients both extend [BaseClient.java](https://github.com/aws/glide-for-redis/blob/main/java/client/src/main/java/glide/api/BaseClient.java) and implement methods from the interfaces listed in `java/client/src/main/java/glide/api/commands`.
The return types of these methods are in the form of a `CompletableFuture`, which fulfill the purpose of the asynchronous features of the program.

### Tests

When implementing a command, include both a unit test and an integration test.

Implement unit tests in the following files:
- [RedisClientTest.java](https://github.com/aws/glide-for-redis/blob/main/java/client/src/test/java/glide/api/RedisClientTest.java) for standalone commands.
- [RedisClusterClientTest.java](https://github.com/aws/glide-for-redis/blob/main/java/client/src/test/java/glide/api/RedisClusterClientTest.java) for cluster commands.
These files are found in the java/client/src/test/java/glide/api path.

Implement integration tests in the following files:
- [TransactionTests.java](https://github.com/aws/glide-for-redis/blob/main/java/client/src/test/java/glide/api/models/TransactionTests.java) (standalone and cluster).
- [TransactionTestsUtilities.java](https://github.com/aws/glide-for-redis/blob/main/java/integTest/src/test/java/glide/TransactionTestUtilities.java) (standalone and cluster).
- [SharedCommandTests.java](https://github.com/aws/glide-for-redis/blob/main/java/integTest/src/test/java/glide/SharedCommandTests.java) (standalone and cluster).
- [cluster/CommandTests.java](https://github.com/aws/glide-for-redis/blob/main/java/integTest/src/test/java/glide/cluster/CommandTests.java) (cluster).
- [standalone/CommandTests.java](https://github.com/aws/glide-for-redis/blob/main/java/integTest/src/test/java/glide/standalone/CommandTests.java) (standalone).
For commands that have options, create a separate file for the optional values.

[BaseTransaction.java](https://github.com/aws/glide-for-redis/blob/main/java/client/src/main/java/glide/api/models/BaseTransaction.java) will add the command to the Transactions API.
Refer to [this](https://github.com/aws/glide-for-redis/tree/35f36ee61a777ee2dd3a15dc6078c5f3759aeaa7/java/client/src/main/java/glide/api/commands) link to view the interface directory.
Refer to https://redis.io/docs/latest/develop/interact/transactions/ for more details about how Transactions work in Redis.

### Javadocs

[BaseTransaction.java](https://github.com/aws/glide-for-redis/blob/main/java/client/src/main/java/glide/api/models/BaseTransaction.java) and the methods within the command interfaces will both contain documentation on how the command operates.
In the command interface each command's javadoc should contain:
- Detail on when the Redis started supporting the command (if it wasn't initially implemented in 6.0.0 or before).
- A link to the Redis documentation.
- Information about the function parameters.
- Any glide-core implementation details, such as how glide-core manages default routing for the command. Reference this [link](https://github.com/aws/glide-for-redis/blob/4df0dd939b515dbf9da0a00bfca6d3ad2f27440b/java/client/src/main/java/glide/api/commands/SetBaseCommands.java#L119) for an example.
- The command's return type. In the [BaseTransaction.java](https://github.com/aws/glide-for-redis/blob/main/java/client/src/main/java/glide/api/models/BaseTransaction.java) file, include "Command Response" before specifying the return type.

### Previous PR's

Refer to [closed-PRs](https://github.com/aws/glide-for-redis/pulls?q=is%3Apr+is%3Aclosed+label%3Ajava) to see commands that have been previously merged.

### FFI naming and signatures, and features

Javac will create the name of the signature in Rust convention which can be called on native code.
- In the command line write:
```bash
javac -h . RedisValueResolver.java
```
The results can be found in the `glide_ffi_resolvers_RedisValueResolver` file once the `javac -h. RedisValueResolver.java` command is ran.
In this project, only the function name and signature name is necessary. lib.rs method names explicitly point to the native functions defined there.

### Module Information

- The [module-info.java](https://github.com/aws/glide-for-redis/blob/main/java/client/src/main/java/module-info.java) (glide.api) contains a list of all of the directories the user can access.
- Ensure to update the exports list if there are more directories the user will need to access.

### Recommended extensions for VS Code

-   [rust-analyzer](https://marketplace.visualstudio.com/items?itemName=rust-lang.rust-analyzer) - Rust language support for VSCode.
-   [spotless-gradle](https://marketplace.visualstudio.com/items?itemName=richardwillis.vscode-spotless-gradle) - Spotless Gradle plugin for VSCode.
-   [gradle](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-gradle) - Gradle extension for Java.

### Recommended extensions for IntelliJ

-   [spotless-gradle](https://plugins.jetbrains.com/plugin/18321-spotless-gradle) - Spotless Gradle plugin for IntelliJ.
-   [lombok](https://plugins.jetbrains.com/plugin/6317-lombok) - Lombok plugin for IntelliJ.
