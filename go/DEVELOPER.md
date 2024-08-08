# Developer Guide

This document describes how to set up your development environment to build and test the Valkey GLIDE Go wrapper.

### Development Overview

We're excited to share that the GLIDE Go client is currently in development! However, it's important to note that this client is a work in progress and is not yet complete or fully tested. Your contributions and feedback are highly encouraged as we work towards refining and improving this implementation. Thank you for your interest and understanding as we continue to develop this Go wrapper.

The Valkey GLIDE Go wrapper consists of both Go and Rust code. The Go and Rust components communicate in two ways:
1. Using the [protobuf](https://github.com/protocolbuffers/protobuf) protocol.
2. Using shared C objects. [cgo](https://pkg.go.dev/cmd/cgo) is used to interact with the C objects from Go code.

### Build from source

#### Prerequisites

Software Dependencies

-   Go
-   GNU Make
-   git
-   GCC
-   pkg-config
-   protoc (protobuf compiler) >= v3.20.0
-   openssl
-   openssl-dev
-   rustup

**Valkey installation**

To install valkey-server and valkey-cli on your host, follow the [Valkey Installation Guide](https://github.com/valkey-io/valkey).

**Dependencies installation for Ubuntu**

```bash
sudo apt update -y
sudo apt install -y git gcc pkg-config openssl libssl-dev unzip make
# Install Go
sudo snap install go --classic
export PATH="$PATH:$HOME/go/bin"
# Install rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
# Check that the Rust compiler is installed
rustc --version
# Install protobuf compiler
PB_REL="https://github.com/protocolbuffers/protobuf/releases"
curl -LO $PB_REL/download/v3.20.3/protoc-3.20.3-linux-x86_64.zip
unzip protoc-3.20.3-linux-x86_64.zip -d $HOME/.local
export PATH="$PATH:$HOME/.local/bin"
# Check that the protobuf compiler is installed. A minimum version of 3.20.0 is required.
protoc --version
```

**Dependencies installation for CentOS**

```bash
sudo yum update -y
sudo yum install -y git gcc pkgconfig openssl openssl-devel unzip wget tar
# Install Go
wget https://go.dev/dl/go1.22.0.linux-amd64.tar.gz
sudo tar -C /usr/local -xzf go1.22.0.linux-amd64.tar.gz
export PATH="$PATH:/usr/local/go/bin"
export PATH="$PATH:$HOME/go/bin"
# Install rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
# Check that the Rust compiler is installed
rustc --version
# Install protobuf compiler
PB_REL="https://github.com/protocolbuffers/protobuf/releases"
curl -LO $PB_REL/download/v3.20.3/protoc-3.20.3-linux-x86_64.zip
unzip protoc-3.20.3-linux-x86_64.zip -d $HOME/.local
export PATH="$PATH:$HOME/.local/bin"
# Check that the protobuf compiler is installed. A minimum version of 3.20.0 is required.
protoc --version
```

**Dependencies installation for MacOS**

```bash
brew update
brew install go make git gcc pkgconfig protobuf@3 openssl
export PATH="$PATH:$HOME/go/bin"
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
# Check that the Rust compiler is installed
rustc --version
# Verify the Protobuf compiler installation
protoc --version

# If protoc is not found or does not work correctly, update the PATH
echo 'export PATH="/opt/homebrew/opt/protobuf@3/bin:$PATH"' >> /Users/$USER/.bash_profile
source /Users/$USER/.bash_profile
protoc --version
```

#### Building and installation steps

Before starting this step, make sure you've installed all software requirements.

1. Clone the repository:
    ```bash
    VERSION=0.1.0 # You can modify this to other released version or set it to "main" to get the unstable branch
    git clone --branch ${VERSION} https://github.com/valkey-io/valkey-glide.git
    cd valkey-glide
    ```
2. Initialize git submodule:
    ```bash
    git submodule update --init --recursive
    ```
3. Install build dependencies:
    ```bash
    cd go
    make install-build-tools
    ```
4. If on CentOS or Ubuntu, add the glide-rs library to LD_LIBRARY_PATH:
    ```bash
    # Replace "<path to valkey-glide>" with the path to the valkey-glide root, eg "$HOME/Projects/valkey-glide"
    GLIDE_ROOT_FOLDER_PATH=<path to valkey-glide>
    export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$GLIDE_ROOT_FOLDER_PATH/go/target/release/deps/
    ```
5. Build the Go wrapper:
    ```bash
    make build
    ```
6. Run tests:
    1. Ensure that you have installed valkey-server and valkey-cli on your host. You can find the Valkey installation guide at the following link: [Valkey Installation Guide](https://github.com/valkey-io/valkey).
    2. Execute the following command from the go folder:
        ```bash
        go test -race ./...
        ```
7. Install Go development tools with:
    ```bash
    # For go1.22:
    make install-dev-tools
    # For go1.18:
    make install-dev-tools-go1.18
    ```

### Test

To run tests, use the following command:

```bash
go test -race ./...
```

For more detailed test output, add the `-v` flag:

```bash
go test -race ./... -v
```

To execute a specific test, include `-run <test_name>`. For example:

```bash
go test -race ./... -run TestConnectionRequestProtobufGeneration_allFieldsSet -v
```

### Submodules

After pulling new changes, ensure that you update the submodules by running the following command:

```bash
git submodule update
```

### Generate protobuf files

During the initial build, Go protobuf files were created in `go/protobuf`. If modifications are made to the protobuf definition files (.proto files located in `glide-core/src/protobuf`), it becomes necessary to regenerate the Go protobuf files. To do so, run:

```bash
make generate-protobuf
```


### Linters

Development on the Go wrapper may involve changes in either the Go or Rust code. Each language has distinct linter tests that must be passed before committing changes.

#### Language-specific Linters

**Go:**

-   go vet
-   gofumpt
-   staticcheck
-   golines

**Rust:**

-   clippy
-   fmt

#### Running the linters

Run from the main `/go` folder

1. Go
    ```bash
    # For go1.22:
    make install-dev-tools
    # For go1.18:
    make install-dev-tools-go1.18

    make lint
    ```
2. Rust
    ```bash
    rustup component add clippy rustfmt
    cargo clippy --all-features --all-targets -- -D warnings
    cargo fmt --manifest-path ./Cargo.toml --all
    ```

#### Fixing lint formatting errors

The following command can be used to fix Go formatting errors reported by gofumpt or golines. Note that golines does not always format comments well if they surpass the max line length (127 characters).

Run from the main `/go` folder

```bash
make format
```

### Benchmarks

To run the benchmarks, ensure you have followed the [build and installation steps](#building-and-installation-steps) (the tests do not have to be run). Then execute the following:

```bash
cd go/benchmarks
# To see a list of available options and their defaults:
go run . -help
# An example command setting various options:
go run . -resultsFile gobenchmarks.json -dataSize "100 1000" -concurrentTasks "10 100" -clients all -host localhost -port 6379 -clientCount "1 5" -tls
```

### Recommended extensions for VS Code

-   [Go](https://marketplace.visualstudio.com/items?itemName=golang.Go)
-   [rust-analyzer](https://marketplace.visualstudio.com/items?itemName=rust-lang.rust-analyzer)
