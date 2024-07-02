# Developer Guide

This document describes how to set up your development environment to build and test the GLIDE for Redis Python wrapper.

### Development Overview

The GLIDE for Redis Python wrapper consists of both Python and Rust code. Rust bindings for Python are implemented using [PyO3](https://github.com/PyO3/pyo3), and the Python package is built using [maturin](https://github.com/PyO3/maturin). The Python and Rust components communicate using the [protobuf](https://github.com/protocolbuffers/protobuf) protocol.

### Build from source

#### Prerequisites

Software Dependencies

-   python3 virtualenv
-   git
-   GCC
-   pkg-config
-   protoc (protobuf compiler) >= v3.20.0
-   openssl
-   openssl-dev
-   rustup

**Dependencies installation for Ubuntu**

```bash
sudo apt update -y
sudo apt install -y python3 python3-venv git gcc pkg-config openssl libssl-dev unzip
# Install rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
# Check that the Rust compiler is installed
rustc --version
# Install protobuf compiler
PB_REL="https://github.com/protocolbuffers/protobuf/releases"
# For other arch type from x86 example below, the signature of the curl url should be protoc-<version>-<os>-<arch>.zip,
# e.g. protoc-3.20.3-linux-aarch_64.zip for ARM64.
curl -LO $PB_REL/download/v3.20.3/protoc-3.20.3-linux-x86_64.zip
unzip protoc-3.20.3-linux-x86_64.zip -d $HOME/.local
export PATH="$PATH:$HOME/.local/bin"
# Check that the protobuf compiler is installed
protoc --version
```

**Dependencies installation for CentOS**

```bash
sudo yum update -y
sudo yum install -y python3 git gcc pkgconfig openssl openssl-devel unzip
pip3 install virtualenv
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
# Check that the protobuf compiler is installed
protoc --version
```

**Dependencies installation for MacOS**

```bash
brew update
brew install python3 git gcc pkgconfig protobuf@3 openssl
pip3 install virtualenv
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
# Check that the Rust compiler is installed
rustc --version
```

#### Building and installation steps

Before starting this step, make sure you've installed all software requirments.

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
    GLIDE_ROOT_FOLDER_PATH=.
    protoc -Iprotobuf=${GLIDE_ROOT_FOLDER_PATH}/glide-core/src/protobuf/ --python_out=${GLIDE_ROOT_FOLDER_PATH}/python/python/glide ${GLIDE_ROOT_FOLDER_PATH}/glide-core/src/protobuf/*.proto
    ```
4. Create a virtual environment:
    ```bash
    cd python
    python3 -m venv .env
    ```
5. Activate the virtual environment:
    ```bash
    source .env/bin/activate
    ```
6. Install requirements:
    ```bash
    pip install -r requirements.txt
    ```
7. Build the Python wrapper in release mode:
    ```
    maturin develop --release --strip
    ```
    > **Note:** To build the wrapper binary with debug symbols remove the --strip flag.
8. Run tests:
    1. Ensure that you have installed redis-server and redis-cli on your host. You can find the Redis installation guide at the following link: [Redis Installation Guide](https://redis.io/docs/install/install-redis/install-redis-on-linux/).
    2. Validate the activation of the virtual environment from step 4 by ensuring its name (`.env`) is displayed next to your command prompt.
    3. Execute the following command from the python folder:
        ```bash
        pytest --asyncio-mode=auto
        ```
        > **Note:** To run redis modules tests, add -k "test_redis_modules.py".

-   Install Python development requirements with:

    ```bash
    pip install -r python/dev_requirements.txt
    ```

-   For a fast build, execute `maturin develop` without the release flag. This will perform an unoptimized build, which is suitable for developing tests. Keep in mind that performance is significantly affected in an unoptimized build, so it's required to include the "--release" flag when measuring performance.

### Test

To run tests, use the following command:

```bash
pytest --asyncio-mode=auto
```

To execute a specific test, include the `-k <test_name>` option. For example:

```bash
pytest --asyncio-mode=auto -k test_socket_set_and_get
```

### Submodules

After pulling new changes, ensure that you update the submodules by running the following command:

```bash
git submodule update
```

### Generate protobuf files

During the initial build, Python protobuf files were created in `python/python/glide/protobuf`. If modifications are made to the protobuf definition files (.proto files located in `glide-core/src/protofuf`), it becomes necessary to regenerate the Python protobuf files. To do so, run:

```bash
GLIDE_ROOT_FOLDER_PATH=. # e.g. /home/ubuntu/glide-for-redis
protoc -Iprotobuf=${GLIDE_ROOT_FOLDER_PATH}/glide-core/src/protobuf/ --python_out=${GLIDE_ROOT_FOLDER_PATH}/python/python/glide ${GLIDE_ROOT_FOLDER_PATH}/glide-core/src/protobuf/*.proto
```

#### Protobuf interface files

To generate the protobuf files with Python Interface files (pyi) for type-checking purposes, ensure you have installed `mypy-protobuf` with pip, and then execute the following command:

```bash
GLIDE_ROOT_FOLDER_PATH=. # e.g. /home/ubuntu/glide-for-redis
MYPY_PROTOC_PATH=`which protoc-gen-mypy`
protoc --plugin=protoc-gen-mypy=${MYPY_PROTOC_PATH} -Iprotobuf=${GLIDE_ROOT_FOLDER_PATH}/glide-core/src/protobuf/ --python_out=${GLIDE_ROOT_FOLDER_PATH}/python/python/glide --mypy_out=${GLIDE_ROOT_FOLDER_PATH}/python/python/glide ${GLIDE_ROOT_FOLDER_PATH}/glide-core/src/protobuf/*.proto
```

### Linters

Development on the Python wrapper may involve changes in either the Python or Rust code. Each language has distinct linter tests that must be passed before committing changes.

#### Language-specific Linters

**Python:**

-   flake8
-   isort
-   black
-   mypy

**Rust:**

-   clippy
-   fmt

#### Running the linters

Run from the main `/python` folder

1. Python
    > Note: make sure to [generate protobuf with interface files]("#protobuf-interface-files") before running mypy linter
    ```bash
    pip install -r dev_requirements.txt
    isort . --profile black --skip-glob python/glide/protobuf --skip-glob .env
    black . --exclude python/glide/protobuf --exclude .env
    flake8 . --count --select=E9,F63,F7,F82 --show-source --statistics --exclude=python/glide/protobuf,.env/* --extend-ignore=E230
    flake8 . --count --exit-zero --max-complexity=12 --max-line-length=127 --statistics --exclude=python/glide/protobuf,.env/* --extend-ignore=E230
    # run type check
    mypy .
    ```
2. Rust

    ```bash
    rustup component add clippy rustfmt
    cargo clippy --all-features --all-targets -- -D warnings
    cargo fmt --manifest-path ./Cargo.toml --all

    ```

### Recommended extensions for VS Code

-   [Python](https://marketplace.visualstudio.com/items?itemName=ms-python.python)
-   [isort](https://marketplace.visualstudio.com/items?itemName=ms-python.isort)
-   [Black Formetter](https://marketplace.visualstudio.com/items?itemName=ms-python.black-formatter)
-   [Flake8](https://marketplace.visualstudio.com/items?itemName=ms-python.flake8)
-   [rust-analyzer](https://marketplace.visualstudio.com/items?itemName=rust-lang.rust-analyzer)
