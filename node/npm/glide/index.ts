#!/usr/bin/env node

/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { GLIBC, MUSL, familySync } from "detect-libc";
import { arch, platform } from "process";

let globalObject = global as unknown;

/* eslint-disable @typescript-eslint/no-require-imports */
function loadNativeBinding() {
    let nativeBinding = null;
    switch (platform) {
        case "linux":
            switch (arch) {
                case "x64":
                    switch (familySync()) {
                        case GLIBC:
                            nativeBinding = require("@scope/valkey-glide-linux-x64");
                            break;
                        case MUSL:
                            nativeBinding = require("@scope/valkey-glide-linux-musl-x64");
                            break;
                        default:
                            nativeBinding = require("@scope/valkey-glide-linux-x64");
                            break;
                    }
                    break;
                case "arm64":
                    switch (familySync()) {
                        case GLIBC:
                            nativeBinding = require("@scope/valkey-glide-linux-arm64");
                            break;
                        case MUSL:
                            nativeBinding = require("@scope/valkey-glide-linux-musl-arm64");
                            break;
                        default:
                            nativeBinding = require("@scope/valkey-glide-linux-arm64");
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
                    nativeBinding = require("@scope/valkey-glide-darwin-x64");
                    break;
                case "arm64":
                    nativeBinding = require("@scope/valkey-glide-darwin-arm64");
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
        BaseScanOptions,
        BitEncoding,
        BitFieldGet,
        BitFieldIncrBy,
        BitFieldOffset,
        BitFieldOverflow,
        BitFieldSet,
        BitFieldSubCommands,
        BitOffset,
        BitOffsetMultiplier,
        BitOffsetOptions,
        BitOverflowControl,
        BitmapIndexType,
        BitwiseOperation,
        ConditionalChange,
        GeoAddOptions,
        CoordOrigin,
        MemberOrigin,
        SearchOrigin,
        GeoBoxShape,
        GeoCircleShape,
        GeoSearchShape,
        GeoSearchResultOptions,
        SortOrder,
        GeoUnit,
        GeospatialData,
        GlideClient,
        GlideClusterClient,
        GlideClientConfiguration,
        FunctionListOptions,
        FunctionListResponse,
        SlotIdTypes,
        SlotKeyTypes,
        RouteByAddress,
        Routes,
        SingleNodeRoute,
        PeriodicChecksManualInterval,
        PeriodicChecks,
        Logger,
        LPosOptions,
        ListDirection,
        ExpireOptions,
        FlushMode,
        InfoOptions,
        InsertPosition,
        SetOptions,
        ZaddOptions,
        InfScoreBoundary,
        ScoreBoundary,
        UpdateOptions,
        ProtocolVersion,
        RangeByIndex,
        RangeByScore,
        RangeByLex,
        ReadFrom,
        RedisCredentials,
        SortClusterOptions,
        SortOptions,
        SortedSetRange,
        StreamGroupOptions,
        StreamTrimOptions,
        StreamAddOptions,
        StreamReadOptions,
        ScriptOptions,
        ClosingError,
        ConfigurationError,
        ExecAbortError,
        RedisError,
        ReturnType,
        RequestError,
        TimeoutError,
        ConnectionError,
        ClusterTransaction,
        Transaction,
        PubSubMsg,
        ScoreFilter,
        SignedEncoding,
        UnsignedEncoding,
        createLeakedArray,
        createLeakedAttribute,
        createLeakedBigint,
        createLeakedDouble,
        createLeakedMap,
        createLeakedString,
        parseInfoResponse,
    } = nativeBinding;

    module.exports = {
        BaseScanOptions,
        BitEncoding,
        BitFieldGet,
        BitFieldIncrBy,
        BitFieldOffset,
        BitFieldOverflow,
        BitFieldSet,
        BitFieldSubCommands,
        BitOffset,
        BitOffsetMultiplier,
        BitOffsetOptions,
        BitOverflowControl,
        BitmapIndexType,
        BitwiseOperation,
        ConditionalChange,
        GeoAddOptions,
        CoordOrigin,
        MemberOrigin,
        SearchOrigin,
        GeoBoxShape,
        GeoCircleShape,
        GeoSearchShape,
        GeoSearchResultOptions,
        SortOrder,
        GeoUnit,
        GeospatialData,
        GlideClient,
        GlideClusterClient,
        GlideClientConfiguration,
        FunctionListOptions,
        FunctionListResponse,
        SlotIdTypes,
        SlotKeyTypes,
        RouteByAddress,
        Routes,
        SingleNodeRoute,
        PeriodicChecksManualInterval,
        PeriodicChecks,
        Logger,
        LPosOptions,
        ListDirection,
        ExpireOptions,
        FlushMode,
        InfoOptions,
        InsertPosition,
        SetOptions,
        ZaddOptions,
        InfScoreBoundary,
        ScoreBoundary,
        UpdateOptions,
        ProtocolVersion,
        RangeByIndex,
        RangeByScore,
        RangeByLex,
        ReadFrom,
        RedisCredentials,
        SortClusterOptions,
        SortOptions,
        SortedSetRange,
        StreamGroupOptions,
        StreamTrimOptions,
        StreamAddOptions,
        StreamReadOptions,
        ScriptOptions,
        ClosingError,
        ConfigurationError,
        ExecAbortError,
        RedisError,
        ReturnType,
        RequestError,
        TimeoutError,
        ConnectionError,
        ClusterTransaction,
        Transaction,
        PubSubMsg,
        ScoreFilter,
        SignedEncoding,
        UnsignedEncoding,
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
