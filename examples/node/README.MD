## Pre-requirements
-   GCC
-   pkg-config
-   protobuf-compiler (protoc)
-   openssl
-   libssl-dev

Installation for ubuntu:
`sudo apt install -y gcc pkg-config protobuf-compiler openssl libssl-dev python3`

### node 16 (or newer)

This is required for the NodeJS wrapper, and for running benchmarks.

```
curl -s https://deb.nodesource.com/setup_16.x | sudo bash
apt-get install nodejs npm
npm i -g npm@8
```

## Build
To build GLIDE's Node client, run (on unix based systems):
```
cd glide-for-redis/node
git submodule update --init --recursive
npm install
rm -rf build-ts
npm run build:release
cd glide-for-redis/examples/node
npm install
npx tsc
```

## Run
To run the example:
```
cd glide-for-redis/examples/node
node index.js
```
