#!/usr/bin/env node

/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { GLIBC, MUSL, familySync } from "detect-libc";
import { arch, platform } from "process";

let globalObject = global as unknown;

function loadNativeBinding() {
    let nativeBinding = null;
    switch (platform) {
        case "linux":
            switch (arch) {
                case "x64":
                    switch (familySync()) {
                        case GLIBC:
                            nativeBinding = require("@scope/glide-for-redis-linux-x64");
                            break;
                        case MUSL:
                            nativeBinding = require("@scope/glide-for-redis-linux-musl-x64");
                            break;
                        default:
                            nativeBinding = require("@scope/glide-for-redis-linux-x64");
                            break;
                    }
                    break;
                case "arm64":
                    switch (familySync()) {
                        case GLIBC:
                            nativeBinding = require("@scope/glide-for-redis-linux-arm64");
                            break;
                        case MUSL:
                            nativeBinding = require("@scope/glide-for-redis-linux-musl-arm64");
                            break;
                        default:
                            nativeBinding = require("@scope/glide-for-redis-linux-arm64");
                            break;
                    }
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
            throw new Error(
                `Unsupported OS: ${platform}, architecture: ${arch}`,
            );
    }
    if (!nativeBinding) {
        throw new Error(`Failed to load native binding`);
    }
    return nativeBinding;
}

function initialize() {
    const nativeBinding = loadNativeBinding();
    const {
        GlideClient,
        GlideClusterClient,
        GlideClientConfiguration,
        SlotIdTypes,
        SlotKeyTypes,
        RouteByAddress,
        Routes,
        SingleNodeRoute,
        PeriodicChecksManualInterval,
        PeriodicChecks,
        Logger,
        ExpireOptions,
        InfoOptions,
        InsertPosition,
        SetOptions,
        ZaddOptions,
        ScoreBoundry,
        RangeByIndex,
        RangeByScore,
        RangeByLex,
        SortedSetRange,
        StreamTrimOptions,
        StreamAddOptions,
        StreamReadOptions,
        ScriptOptions,
        ClosingError,
        ExecAbortError,
        RedisError,
        RequestError,
        TimeoutError,
        ConnectionError,
        ClusterTransaction,
        Transaction,
        createLeakedArray,
        createLeakedAttribute,
        createLeakedBigint,
        createLeakedDouble,
        createLeakedMap,
        createLeakedString,
        parseInfoResponse,
    } = nativeBinding;

    module.exports = {
        GlideClient,
        GlideClusterClient,
        GlideClientConfiguration,
        SlotIdTypes,
        SlotKeyTypes,
        RouteByAddress,
        Routes,
        SingleNodeRoute,
        PeriodicChecksManualInterval,
        PeriodicChecks,
        Logger,
        ExpireOptions,
        InfoOptions,
        InsertPosition,
        SetOptions,
        ZaddOptions,
        ScoreBoundry,
        RangeByIndex,
        RangeByScore,
        RangeByLex,
        SortedSetRange,
        StreamTrimOptions,
        StreamAddOptions,
        StreamReadOptions,
        ScriptOptions,
        ClosingError,
        ExecAbortError,
        RedisError,
        RequestError,
        TimeoutError,
        ConnectionError,
        ClusterTransaction,
        Transaction,
        createLeakedArray,
        createLeakedAttribute,
        createLeakedBigint,
        createLeakedDouble,
        createLeakedMap,
        createLeakedString,
        parseInfoResponse,
    };

    globalObject = Object.assign(global, nativeBinding);
}

initialize();

export default globalObject;
