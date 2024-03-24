# Developer Guide

This document describes how to set up your development environment to build and test the GLIDE for Redis Node wrapper.

### Development Overview

The GLIDE Node wrapper consists of both TypeScript and Rust code. Rust bindings for Node.js are implemented using [napi-rs](https://github.com/napi-rs/napi-rs). The Node and Rust components communicate using the [protobuf](https://github.com/protocolbuffers/protobuf) protocol.

### Build from source

#### Prerequisites

Software Dependencies

> If your NodeJS version is below the supported version specified in the client's [documentation](https://github.com/aws/glide-for-redis/blob/main/node/README.md#nodejs-supported-version), you can upgrade it using [NVM](https://github.com/nvm-sh/nvm?tab=readme-ov-file#install--update-script).

-   npm
-   git
-   GCC
-   pkg-config
-   protoc (protobuf compiler)
-   openssl
-   openssl-dev
-   rustup

**Dependencies installation for Ubuntu**

```bash
sudo apt update -y
sudo apt install -y nodejs npm git gcc pkg-config protobuf-compiler openssl libssl-dev
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

**Dependencies installation for CentOS**

```bash
sudo yum update -y
sudo yum install -y nodejs git gcc pkgconfig protobuf-compiler openssl openssl-devel gettext
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

**Dependencies installation for MacOS**

```bash
brew update
brew install nodejs git gcc pkgconfig protobuf openssl
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

#### Building and installation steps

Before starting this step, make sure you've installed all software requirments.

1.  Clone the repository:
    ```bash
    VERSION=0.1.0 # You can modify this to other released version or set it to "main" to get the unstable branch
    git clone --branch ${VERSION} https://github.com/aws/glide-for-redis.git
    cd glide-for-redis
    ```
2.  Initialize git submodule:
    ```bash
    git submodule update --init --recursive
    ```
3.  Install all node dependencies:
    ```bash
    cd node
    npm i
    cd rust-client
    npm i
    cd ..
    ```
4.  Build the Node wrapper:
    Choose a build option from the following and run it from the `node` folder:

        1. Build in release mode, stripped from all debug symbols (optimized and minimized binary size):

            ```bash
            npm run build:release
            ```

        2. Build in release mode with debug symbols (optimized but large binary size):

            ```bash
            npm run build:benchmark
            ```

        3. For testing purposes, you can execute an unoptimized but fast build using:
           `bash

    npm run build
    `       Once building completed, you'll find the compiled JavaScript code in the`./build-ts` folder.

5.  Run tests:
    1. Ensure that you have installed redis-server and redis-cli on your host. You can find the Redis installation guide at the following link: [Redis Installation Guide](https://redis.io/docs/install/install-redis/install-redis-on-linux/).
    2. Execute the following command from the node folder:
        ```bash
        npm test
        ```
6.  Integrating the built GLIDE package into your project:
    Add the package to your project using the folder path with the command `npm install <path to GLIDE>/node`.

-   For a fast build, execute `npm run build`. This will perform a full, unoptimized build, which is suitable for developing tests. Keep in mind that performance is significantly affected in an unoptimized build, so it's required to build with the `build:release` or `build:benchmark` option when measuring performance.
-   If your modifications are limited to the TypeScript code, run `npm run build-external` to build the external package without rebuilding the internal package.
-   If your modifications are limited to the Rust code, execute `npm run build-internal` to build the internal package and generate TypeScript code.
-   To generate Node's protobuf files, execute `npm run build-protobuf`. Keep in mind that protobuf files are generated as part of full builds (e.g., `build`, `build:release`, etc.).

> Note: Once building completed, you'll find the compiled JavaScript code in the `node/build-ts` folder.

### Troubleshooting

-   If the build fails after running `npx tsc` because `glide-rs` isn't found, check if your npm version is in the range 9.0.0-9.4.1, and if so, upgrade it. 9.4.2 contains a fix to a change introduced in 9.0.0 that is required in order to build the library.

### Test

To run tests, use the following command:

```bash
npm test
```

### Submodules

After pulling new changes, ensure that you update the submodules by running the following command:

```bash
git submodule update
```

### Linters

Development on the Node wrapper may involve changes in either the TypeScript or Rust code. Each language has distinct linter tests that must be passed before committing changes.

#### Language-specific Linters

**TypeScript:**

-   ESLint
-   Prettier

**Rust:**

-   clippy
-   fmt

#### Running the linters

1. TypeScript
    ```bash
    # Run from the `node` folder
    npm install eslint-plugin-import@latest  @typescript-eslint/parser @typescript-eslint/eslint-plugin eslint-plugin-tsdoc eslint typescript eslint-plugin-import@latest eslint-config-prettier prettier
    npm i
    npx eslint . --max-warnings=0
    npx prettier --check .
    ```
2. Rust
    ```bash
    # Run from the `node/rust-client` folder
    rustup component add clippy rustfmt
    cargo clippy --all-features --all-targets -- -D warnings
    cargo fmt --manifest-path ./Cargo.toml --all
    ```

### Recommended extensions for VS Code

-   [Prettier - Code formatter](https://marketplace.visualstudio.com/items?itemName=esbenp.prettier-vscode) - JavaScript / TypeScript formatter.
-   [ESLint](https://marketplace.visualstudio.com/items?itemName=dbaeumer.vscode-eslint) - linter.
-   [Jest Runner](https://marketplace.visualstudio.com/items?itemName=firsttris.vscode-jest-runner) - in-editor test runner.
-   [Jest Test Explorer](https://marketplace.visualstudio.com/items?itemName=kavod-io.vscode-jest-test-adapter) - adapter to the VSCode testing UI.
-   [rust-analyzer](https://marketplace.visualstudio.com/items?itemName=rust-lang.rust-analyzer) - Rust language support for VSCode.

### Hybrid package publishing method

In this project we are using hybrid method for building the package for NodeJS, in order to support GLIDE's usage in projects based on either `CommonJS` or `ECMAScript` modules.
Hybrid method -
In order to build the package for usage in either module system, we use three different tsconfig files:

-   `tsconfig` is the general tsconfig file, which the others will extend.
-   `tsconfig-cjs` for `commonJS` build with `CommonJS` as the target of translation.
-   `tsconfig` for `ECMA` build with `ECMA` as the target of translation.

The build is composed of 2 build steps, one will use `tsconfig` and write the results of the package's translation into the `build-ts/mjs` folder, and the other will use `tsconfig-cjs` and will write into the `build-ts/cjs` folder.
Additionaly, we add to `package.json` the following export rule, which presents to the user the correct index file, based on the import statement used - `import` for `ECMA`, and `require` for `CommonJS`.
As part of the build we run the `fixup_pj_files_for_build_type` script, which adds the `type` and `types` entries to the different `package.json` that been created for `ECMAScript` and `CommonJS` with the fitting values.
