#!/usr/bin/env node

/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

const { platform, arch } = process;
let nativeBinding = null;

switch (platform) {
    case "linux":
        switch (arch) {
            case "x64":
                nativeBinding = require("@scope/glide-for-redis-linux-x64");
                break;
            case "arm64":
                nativeBinding = require("@scope/glide-for-redis-linux-arm64");
                break;
            default:
                throw new Error(
                    `Unsupported OS: ${platform}, architecture: ${arch}`,
                );
        }
        break;
    case "darwin":
        switch (arch) {
            case "x64":
                nativeBinding = require("@scope/glide-for-redis-darwin-x64");
                break;
            case "arm64":
                nativeBinding = require("@scope/glide-for-redis-darwin-arm64");
                break;
            default:
                throw new Error(
                    `Unsupported OS: ${platform}, architecture: ${arch}`,
                );
        }
        break;
    default:
        throw new Error(`Unsupported OS: ${platform}, architecture: ${arch}`);
}

if (!nativeBinding) {
    throw new Error(`Failed to load native binding`);
}

const {
    RedisClient,
    RedisClusterClient,
    Logger,
    ExpireOptions,
    InfoOptions,
    ClosingError,
    ExecAbortError,
    RedisError,
    RequestError,
    TimeoutError,
    ClusterTransaction,
    Transaction,
} = nativeBinding;

module.exports = {
    RedisClient,
    RedisClusterClient,
    Logger,
    ExpireOptions,
    InfoOptions,
    ClosingError,
    ExecAbortError,
    RedisError,
    RequestError,
    TimeoutError,
    ClusterTransaction,
    Transaction,
};

export default Object.assign(global, nativeBinding);
