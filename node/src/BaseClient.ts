/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

/**
 * Note: 'eslint-disable-line @typescript-eslint/no-unused-vars' is used intentionally
 * to suppress unused import errors for types referenced only in JSDoc.
 */

import { readFile } from "node:fs/promises";
import Long from "long";
import { Buffer } from "protobufjs/minimal";
import {
    AggregationType,
    BaseScanOptions,
    BatchOptions,
    BitFieldGet,
    BitFieldIncrBy, // eslint-disable-line @typescript-eslint/no-unused-vars
    BitFieldOverflow, // eslint-disable-line @typescript-eslint/no-unused-vars
    BitFieldSet, // eslint-disable-line @typescript-eslint/no-unused-vars
    BitFieldSubCommands,
    BitOffset, // eslint-disable-line @typescript-eslint/no-unused-vars
    BitOffsetMultiplier, // eslint-disable-line @typescript-eslint/no-unused-vars
    BitOffsetOptions,
    BitwiseOperation,
    Boundary,
    ClientSideCache,
    ClosingError,
    ClusterBatchOptions,
    CompressionConfiguration,
    ConfigurationError,
    ConnectionError,
    CoordOrigin, // eslint-disable-line @typescript-eslint/no-unused-vars
    DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS,
    CircuitBreakerError,
    ExecAbortError,
    ExpireOptions,
    GeoAddOptions,
    GeoBoxShape, // eslint-disable-line @typescript-eslint/no-unused-vars
    GeoCircleShape, // eslint-disable-line @typescript-eslint/no-unused-vars
    GeoSearchResultOptions,
    GeoSearchShape,
    GeoSearchStoreResultOptions,
    GeoUnit,
    GeospatialData,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    HExpireOptions,
    HGetExOptions,
    HScanOptions,
    HSetExOptions,
    InsertPosition,
    KeyWeight,
    LPosOptions,
    ListDirection,
    Logger,
    MemberOrigin, // eslint-disable-line @typescript-eslint/no-unused-vars
    MigrateOptions,
    OpenTelemetry,
    RangeByIndex,
    RangeByLex,
    RangeByScore,
    RequestError,
    RestoreOptions,
    RouteOption,
    Routes,
    ScoreFilter,
    Script,
    SearchOrigin,
    SetOptions,
    SortOptions,
    CommandResponse,
    CreateDirectClient,
    GlideClientHandle,
    createLeakedStringVec,
    registerAddressResolver,
    removeAddressResolver,
    StreamAddOptions,
    StreamClaimOptions,
    StreamGroupOptions,
    StreamPendingOptions,
    StreamReadGroupOptions,
    StreamReadOptions,
    StreamTrimOptions,
    TimeUnit,
    TimeoutError,
    ValkeyError,
    ZAddOptions,
    ZScanOptions,
    convertFieldsAndValuesToHashDataType,
    convertKeysAndEntries,
    createAppend,
    createBLMPop,
    createBLMove,
    createBLPop,
    createBRPop,
    createBZMPop,
    createBZPopMax,
    createBZPopMin,
    createBitCount,
    createBitField,
    createBitOp,
    createBitPos,
    createCopy,
    createDecr,
    createDecrBy,
    createDel,
    createDump,
    createExists,
    createExpire,
    createExpireAt,
    createExpireTime,
    createFCall,
    createFCallReadOnly,
    createGeoAdd,
    createGeoDist,
    createGeoHash,
    createGeoPos,
    createGeoSearch,
    createGeoSearchStore,
    createGet,
    createGetBit,
    createGetDel,
    createGetEx,
    createGetRange,
    createHDel,
    createHExists,
    createHExpire,
    createHExpireAt,
    createHExpireTime,
    createHGet,
    createHGetAll,
    createHGetEx,
    createHIncrBy,
    createHIncrByFloat,
    createHKeys,
    createHLen,
    createHMGet,
    createHPExpire,
    createHPExpireAt,
    createHPExpireTime,
    createHPTtl,
    createHPersist,
    createHRandField,
    createHScan,
    createHSet,
    createHSetEx,
    createHSetNX,
    createHStrlen,
    createHTtl,
    createHVals,
    createIncr,
    createIncrBy,
    createIncrByFloat,
    createLCS,
    createLIndex,
    createLInsert,
    createLLen,
    createLMPop,
    createLMove,
    createLPop,
    createLPos,
    createLPush,
    createLPushX,
    createLRange,
    createLRem,
    createLSet,
    createLTrim,
    createLeakedOtelSpan,
    createOtelSpanWithTraceContext,
    createMGet,
    createMigrate,
    createMSet,
    createMSetNX,
    createMove,
    createObjectEncoding,
    createObjectFreq,
    createObjectIdletime,
    createObjectRefcount,
    createPExpire,
    createPExpireAt,
    createPExpireTime,
    createPTTL,
    createPersist,
    createPfAdd,
    createPfCount,
    createPfMerge,
    createPubSubChannels,
    createPubSubNumPat,
    createPubSubNumSub,
    createPSubscribe,
    createPSubscribeLazy,
    createPUnsubscribe,
    createPUnsubscribeLazy,
    createSubscribe,
    createSubscribeLazy,
    createUnsubscribe,
    createUnsubscribeLazy,
    createRPop,
    createRPush,
    createRPushX,
    createRename,
    createRenameNX,
    createReset,
    createRestore,
    createSAdd,
    createSCard,
    createSDiff,
    createSDiffStore,
    createSInter,
    createSInterCard,
    createSInterStore,
    createSIsMember,
    createSMIsMember,
    createSMembers,
    createSMove,
    createSPop,
    createSRandMember,
    createSRem,
    createSScan,
    createSUnion,
    createSUnionStore,
    createScriptShow,
    createSelect,
    createSet,
    createSetBit,
    createSetRange,
    createSort,
    createSortReadOnly,
    createStrlen,
    createTTL,
    createTouch,
    createType,
    createUnlink,
    createWait,
    createWatch,
    createXAck,
    createXAdd,
    createXAutoClaim,
    createXClaim,
    createXDel,
    createXGroupCreate,
    createXGroupCreateConsumer,
    createXGroupDelConsumer,
    createXGroupDestroy,
    createXGroupSetid,
    createXInfoConsumers,
    createXInfoGroups,
    createXInfoStream,
    createXLen,
    createXPending,
    createXRange,
    createXRead,
    createXReadGroup,
    createXRevRange,
    createXTrim,
    createZAdd,
    createZCard,
    createZCount,
    createZDiff,
    createZDiffStore,
    createZDiffWithScores,
    createZIncrBy,
    createZInter,
    createZInterCard,
    createZInterstore,
    createZLexCount,
    createZMPop,
    createZMScore,
    createZPopMax,
    createZPopMin,
    createZRandMember,
    createZRange,
    createZRangeStore,
    createZRangeWithScores,
    createZRank,
    createZRem,
    createZRemRangeByLex,
    createZRemRangeByRank,
    createZRemRangeByScore,
    createZRevRank,
    createZRevRankWithScore,
    createZScan,
    createZScore,
    createZUnion,
    createZUnionStore,
    dropOtelSpan,
    getStatistics,
    compressionConfigToProtobuf,
    validateCompressionConfiguration,
    valueFromPointer,
    valueFromSplitPointer,
} from ".";
import {
    command_request,
    connection_request,
    response,
} from "../build-ts/ProtobufMessage";
import { resolveClientLibraryName } from "./ClientLibraryNameResolver.js";
/* eslint-disable-next-line @typescript-eslint/no-explicit-any */
type PromiseFunction = (value?: any) => void;
type ErrorFunction = (error: ValkeyError) => void;
/* eslint @typescript-eslint/consistent-indexed-object-style: off,  @typescript-eslint/consistent-type-definitions: off */
export type ReturnTypeRecord = { [key: string]: GlideReturnType };
export type ReturnTypeMap = Map<string, GlideReturnType>;
export interface ReturnTypeAttribute {
    value: GlideReturnType;
    attributes: ReturnTypeRecord;
}
export enum ProtocolVersion {
    /** Use RESP2 to communicate with the server nodes. */
    RESP2 = connection_request.ProtocolVersion.RESP2,
    /** Use RESP3 to communicate with the server nodes. */
    RESP3 = connection_request.ProtocolVersion.RESP3,
}
export type GlideReturnType =
    | "OK"
    | string
    | number
    | null
    | boolean
    | bigint
    | Buffer
    | Set<GlideReturnType>
    | ReturnTypeRecord
    | ReturnTypeMap
    | ReturnTypeAttribute
    | RequestError
    | GlideReturnType[];

/**
 * Union type that can store either a valid UTF-8 string or array of bytes.
 */
export type GlideString = string | Buffer;

/**
 * Mutual TLS (mTLS) client authentication material.
 *
 * The `kind` field selects one of two mutually exclusive ways to supply the
 * client certificate and private key. The bytes variant uses
 * `clientCertificate` and `clientKey`; the path variant uses `clientCertPath`
 * and `clientKeyPath`. Pairing each variant's fields under a shared
 * discriminant lets the compiler reject mixed configurations (for example a
 * `clientCertificate` alongside `clientKeyPath`, or a `reloadIntervalSeconds`
 * on the bytes variant).
 *
 * With `kind: "bytes"`, `clientCertificate` and `clientKey` are PEM material
 * passed inline and read once at connect time. A `string` value is encoded as
 * UTF-8.
 *
 * With `kind: "path"`, `clientCertPath` and `clientKeyPath` point at files on
 * disk. The core reads them at connect time and re-reads them on a schedule,
 * so a rotated cert is adopted on the next reconnect while open connections
 * keep their current material. If a reload fails (missing file, mismatched
 * key, unreadable), the last known good material is kept. When
 * `reloadIntervalSeconds` is omitted, the cadence defaults to the GLIDE core
 * default (`DEFAULT_RELOAD_INTERVAL_SECONDS` in glide-core's `tls_reload`
 * module); very long intervals may adopt a rotated cert late.
 *
 * Reload is on iff `kind === "path"`. There is no separate toggle.
 */
export type MutualTls =
    | {
          readonly kind: "bytes";
          readonly clientCertificate: Buffer | string;
          readonly clientKey: Buffer | string;
      }
    | {
          readonly kind: "path";
          readonly clientCertPath: string;
          readonly clientKeyPath: string;
          readonly reloadIntervalSeconds?: number;
      };

/**
 * Largest value that fits in an unsigned 32-bit protobuf field:
 * `4_294_967_295` (`2 ** 32 - 1`). Reused by validators that need to bound
 * a value to the uint32 wire range. protobufjs casts through `value >>> 0`
 * when the request is built, so a larger value would silently truncate
 * before reaching the core. Rejecting up front keeps the user's requested
 * value visible instead of quietly changing it.
 *
 * @internal
 */
const MAX_UINT32 = 2 ** 32 - 1;

/**
 * Reads a PEM file for TLS configuration. Shared by
 * {@link loadRootCertificatesFromFile} and
 * {@link loadClientCertificateAndKeyFromFile}.
 *
 * @param path - Path to the PEM file.
 * @param label - Label used in error messages (e.g. `"Root certificate"`).
 * @returns The file contents.
 * @throws {@link ConfigurationError} If the file is missing, unreadable, or empty.
 * @internal
 */
async function loadTlsPemFile(path: string, label: string): Promise<Buffer> {
    let data: Buffer;

    try {
        data = await readFile(path);
    } catch (error) {
        const fsError = error as NodeJS.ErrnoException;
        const message =
            fsError.code === "ENOENT"
                ? `${label} file not found: ${path}`
                : `Failed to read ${label.toLowerCase()} file at ${path}: ${
                      error instanceof Error ? error.message : String(error)
                  }`;
        const wrapped = new ConfigurationError(message);
        (wrapped as Error).cause = error;
        throw wrapped;
    }

    if (data.length === 0) {
        throw new ConfigurationError(`${label} file is empty: ${path}`);
    }

    return data;
}

/**
 * Loads PEM-encoded root certificates for TLS server verification. Feed the
 * result to `rootCertificates` on
 * {@link AdvancedBaseClientConfiguration.tlsAdvancedConfiguration}.
 *
 * @param path - Path to a PEM root certificate or bundle.
 * @returns The certificate bytes.
 * @throws {@link ConfigurationError} If the file is missing, unreadable, or empty.
 */
export function loadRootCertificatesFromFile(path: string): Promise<Buffer> {
    return loadTlsPemFile(path, "Root certificate");
}

/**
 * Loads a PEM client certificate and its private key for the byte-based
 * {@link MutualTls} variant (`kind: "bytes"`). For automatic reload, use the
 * path-based variant instead; the core then owns the file lifecycle.
 *
 * The cert is read first. If it fails, the key file is not touched.
 *
 * @param clientCertPath - Path to a PEM client certificate.
 * @param clientKeyPath - Path to a PEM client private key.
 * @returns `{ cert, key }` as Buffers.
 * @throws {@link ConfigurationError} If either file is missing, unreadable, or empty.
 */
export async function loadClientCertificateAndKeyFromFile(
    clientCertPath: string,
    clientKeyPath: string,
): Promise<{ cert: Buffer; key: Buffer }> {
    const cert = await loadTlsPemFile(clientCertPath, "Client certificate");
    const key = await loadTlsPemFile(clientKeyPath, "Client key");
    return { cert, key };
}

/**
 * Encodes PEM input for the connection request and rejects empty material.
 * String values are UTF-8 encoded; Buffer values pass through. Empty content
 * has to be caught here: proto3 `bytes` treats empty as unset, so a request
 * with both `clientCertificate` and `clientKey` empty would silently
 * downgrade to server-auth-only TLS instead of surfacing a mTLS configuration
 * error.
 *
 * @internal
 */
function encodePem(value: Buffer | string, fieldName: string): Uint8Array {
    const buffer =
        typeof value === "string" ? Buffer.from(value, "utf-8") : value;

    if (buffer.length === 0) {
        throw new ConfigurationError(`${fieldName} must not be empty.`);
    }

    return new Uint8Array(buffer);
}

/**
 * Validates the optional reload interval. protobufjs casts `uint32` through
 * `value >>> 0` when the request is built, so an out-of-range or non-integer
 * value would silently truncate before reaching the core. The core cannot
 * validate what it never receives, so this check has to stay client-side.
 *
 * @internal
 */
function validateReloadInterval(value: number | undefined): void {
    if (value === undefined) {
        return;
    }

    if (!Number.isInteger(value) || value <= 0 || value > MAX_UINT32) {
        throw new ConfigurationError(
            `mutualTls.reloadIntervalSeconds must be a positive integer no greater than ${MAX_UINT32}.`,
        );
    }
}

/**
 * Writes a {@link MutualTls} value into the connection request. The byte
 * variant sets `client_cert` / `client_key` (proto fields 22/23); the path
 * variant sets `client_cert_path` / `client_key_path` / `cert_reload`
 * (proto fields 31/32/33).
 *
 * @internal
 */
function applyMutualTls(
    mtls: MutualTls,
    request: connection_request.IConnectionRequest,
): void {
    switch (mtls.kind) {
        case "bytes": {
            request.clientCert = encodePem(
                mtls.clientCertificate,
                "mutualTls.clientCertificate",
            );
            request.clientKey = encodePem(
                mtls.clientKey,
                "mutualTls.clientKey",
            );
            return;
        }

        case "path": {
            validateReloadInterval(mtls.reloadIntervalSeconds);
            request.clientCertPath = mtls.clientCertPath;
            request.clientKeyPath = mtls.clientKeyPath;
            request.certReload = {
                enabled: true,
                intervalSeconds: mtls.reloadIntervalSeconds,
            };
            return;
        }

        default: {
            // Compile-time: assigning `mtls` to `never` fails to build if a
            // new variant is added without a case. Runtime: the throw only
            // fires for an untyped JS caller supplying an unknown `kind`,
            // and the cast is needed because `mtls` is narrowed to `never`.
            const _exhaustive: never = mtls;
            void _exhaustive;
            throw new ConfigurationError(
                `Unsupported mutualTls variant: kind=${String(
                    (mtls as { kind?: unknown }).kind,
                )}`,
            );
        }
    }
}

/**
 * Applies the `tlsAdvancedConfiguration` block onto the connection request:
 * the `useTLS`-off guard, the `insecure` flag, `rootCertificates`, and the
 * mTLS dispatch.
 *
 * @internal
 */
export function applyTlsAdvancedConfiguration(
    tls: NonNullable<
        AdvancedBaseClientConfiguration["tlsAdvancedConfiguration"]
    >,
    request: connection_request.IConnectionRequest,
): void {
    if (request.tlsMode === connection_request.TlsMode.NoTls) {
        throw new ConfigurationError(
            "TLS advanced configuration cannot be set when useTLS is disabled.",
        );
    }

    if (tls.insecure) {
        request.tlsMode = connection_request.TlsMode.InsecureTls;
    }

    if (tls.rootCertificates) {
        const certData =
            typeof tls.rootCertificates === "string"
                ? Buffer.from(tls.rootCertificates, "utf-8")
                : tls.rootCertificates;
        request.rootCerts = [new Uint8Array(certData)];
    }

    if (tls.mutualTls !== undefined) {
        applyMutualTls(tls.mutualTls, request);
    }
}

/**
 * Enum representing the different types of decoders.
 */
export enum Decoder {
    /**
     * Decodes the response into a buffer array.
     */
    Bytes,
    /**
     * Decodes the response into a string.
     */
    String,
}

/**
 * An extension to command option types with {@link Decoder}.
 *
 * WARNING:
 *
 * Be aware that if decoding fails during a command execution (due to
 * invalid inputs, incorrect decoder), data COULD BE UNRECOVERABLY LOST.
 *
 * Use with caution.
 */
export interface DecoderOption {
    /**
     * {@link Decoder} type which defines how to handle the response.
     * If not set, the {@link BaseClientConfiguration.defaultDecoder|default decoder} will be used.
     */
    decoder?: Decoder;
}

/** A replacement for `Record<GlideString, T>` - array of key-value pairs. */
export type GlideRecord<T> = {
    /** The value name. */
    key: GlideString;
    /** The value itself. */
    value: T;
}[];

/**
 * Data type which represents sorted sets data, including elements and their respective scores.
 * Similar to `Record<GlideString, number>` - see {@link GlideRecord}.
 */
export type SortedSetDataType = {
    /** The sorted set element name. */
    element: GlideString;
    /** The element score. */
    score: number;
}[];

/**
 * Data type which represents how data are returned from hashes or insterted there.
 * Similar to `Record<GlideString, GlideString>` - see {@link GlideRecord}.
 */
export type HashDataType = {
    /** The hash element name. */
    field: GlideString;
    /** The hash element value. */
    value: GlideString;
}[];

/**
 * Data type which reflects now stream entries are returned.
 * The keys of the record are stream entry IDs, which are mapped to key-value pairs of the data.
 */
export type StreamEntryDataType = Record<string, [GlideString, GlideString][]>;

/**
 * Union type that can store either a number or positive/negative infinity.
 */
export type Score = number | "+inf" | "-inf";

/**
 * Constant representing "all channels" for unsubscribe operations.
 * Use this to unsubscribe from all channel subscriptions at once.
 *
 * @example
 * ```typescript
 * await client.unsubscribeLazy(ALL_CHANNELS);
 * ```
 */
export const ALL_CHANNELS = null;

/**
 * Constant representing "all patterns" for punsubscribe operations.
 * Use this to unsubscribe from all pattern subscriptions at once.
 *
 * @example
 * ```typescript
 * await client.punsubscribeLazy(ALL_PATTERNS);
 * ```
 */
export const ALL_PATTERNS = null;

/**
 * Data type which represents sorted sets data for input parameter of ZADD command,
 * including element and its respective score.
 */
export type ElementAndScore = {
    /** The sorted set element name. */
    element: GlideString;
    /** The element score. */
    score: Score;
};

/**
 * @internal
 * Convert `GlideRecord<number>` recevied after resolving the value pointer into `SortedSetDataType`.
 */
function convertGlideRecordForSortedSet(
    res: GlideRecord<number>,
): SortedSetDataType {
    return res.map((e) => {
        return { element: e.key, score: e.value };
    });
}

/**
 * @internal
 * This function converts an input from GlideRecord or Record types to GlideRecord.
 *
 * @param keysAndValues - key names and their values.
 * @returns GlideRecord array containing keys and their values.
 */
export function convertGlideRecord(
    keysAndValues: GlideRecord<GlideString> | Record<string, GlideString>,
): GlideRecord<GlideString> {
    if (!Array.isArray(keysAndValues)) {
        return Object.entries(keysAndValues).map(([key, value]) => {
            return { key, value };
        });
    }

    return keysAndValues;
}

/**
 * @internal
 * Recursively downcast `GlideRecord` to `Record`. Use if `data` keys are always strings.
 */
export function convertGlideRecordToRecord<T>(
    data: GlideRecord<T>,
): Record<string, T> {
    const res: Record<string, T> = {};

    for (const pair of data) {
        let newVal = pair.value;

        if (isGlideRecord(pair.value)) {
            newVal = convertGlideRecordToRecord(
                pair.value as GlideRecord<unknown>,
            ) as T;
        } else if (isGlideRecordArray(pair.value)) {
            newVal = (pair.value as GlideRecord<unknown>[]).map(
                convertGlideRecordToRecord,
            ) as T;
        }

        res[pair.key as string] = newVal;
    }

    return res;
}

/**
 * @internal
 * Check whether an object is a `GlideRecord` (see {@link GlideRecord}).
 */
export function isGlideRecord(obj?: unknown): boolean {
    return (
        Array.isArray(obj) &&
        obj.length > 0 &&
        typeof obj[0] === "object" &&
        "key" in obj[0] &&
        "value" in obj[0]
    );
}

/**
 * @internal
 * Check whether an object is a `GlideRecord[]` (see {@link GlideRecord}).
 */
function isGlideRecordArray(obj?: unknown): boolean {
    return Array.isArray(obj) && obj.length > 0 && isGlideRecord(obj[0]);
}

/** Represents the return type of {@link xinfoStream} command. */
export type ReturnTypeXinfoStream = Record<
    string,
    | StreamEntries
    | Record<string, StreamEntries | Record<string, StreamEntries>[]>[]
>;

/**
 * Represents an array of Stream Entires in the response of {@link xinfoStream} command.
 * See {@link ReturnTypeXinfoStream}.
 */
export type StreamEntries =
    GlideString | number | (GlideString | number | GlideString[])[][];

/**
 * @internal
 * Reverse of {@link convertGlideRecordToRecord}.
 */
export function convertRecordToGlideRecord<T>(
    data: Record<string, T>,
): GlideRecord<T> {
    return Object.entries(data).map(([key, value]) => {
        return { key, value };
    });
}

/** Represents the types of services that can be used for IAM authentication. */
export enum ServiceType {
    Elasticache = "Elasticache",
    MemoryDB = "MemoryDB",
}

/** Configuration settings for IAM authentication. */
export interface IamAuthConfig {
    /** The name of the ElastiCache/MemoryDB cluster. */
    clusterName: string;
    /** The type of service being used (ElastiCache or MemoryDB). */
    service: ServiceType;
    /** The AWS region where the ElastiCache/MemoryDB cluster is located. */
    region: string;
    /**
     * Optional refresh interval in seconds for renewing IAM authentication tokens.
     * If not provided, defaults to 300 seconds (5 min).
     */
    refreshIntervalSeconds?: number;
}

/** Represents the credentials for connecting to a server. */
export type ServerCredentials =
    | {
          /**
           * The username that will be used for authenticating connections to the Valkey servers.
           * If not supplied, "default" will be used.
           */
          username?: string;
          /**
           * The password that will be used for authenticating connections to the Valkey servers.
           * (mutually exclusive with iamConfig).
           */
          password: string;
      }
    | {
          /** Username is REQUIRED for IAM (Valkey AUTH <username> <token>). */
          username: string;
          /**
           * IAM config (mutually exclusive with password).
           * The client will automatically generate and refresh the authentication token based on the provided configuration.
           */
          iamConfig: IamAuthConfig;
      };

/** Represents the client's read from strategy. */
export type ReadFrom =
    /** Always get from primary, in order to get the freshest data.*/
    | "primary"
    /** Spread the requests between all replicas in a round robin manner.
        If no replica is available, route the requests to the primary.*/
    | "preferReplica"
    /** Spread the requests between replicas in the same client's Aviliablity zone in a round robin manner.
        If no replica is available, route the requests to the primary.*/
    | "AZAffinity"
    /** Spread the read requests among all nodes within the client's Availability Zone (AZ) in a round robin manner,
         prioritizing local replicas, then the local primary, and falling back to any replica or the primary if needed.*/
    | "AZAffinityReplicasAndPrimary"
    /** Spread the read requests between all nodes (primary and replicas) in a round robin manner.*/
    | "allNodes";

/**
 * Controls how the client discovers node roles and topology in standalone mode.
 */
export enum NodeDiscoveryMode {
    /** Default: verify node roles via INFO REPLICATION, use only provided addresses. */
    Standard = 0,
    /** Skip role detection entirely. Trust provided addresses as-is; first address is primary.
     *  Use when connecting through a proxy (e.g., Envoy) or when the topology is known and static.
     *  Note: Do not set `clientName` when using this mode with a proxy. */
    Static = 1,
    /** Discover full topology (primary + all replicas) from any starting node.
     *  Provide any single node address and the client will find and connect to all other nodes. */
    DiscoverAll = 2,
}

/**
 * Configuration settings for creating a client. Shared settings for standalone and cluster clients.
 *
 * @remarks
 * The `BaseClientConfiguration` interface defines the foundational configuration options used when creating a client to connect to a Valkey server or cluster. It includes connection details, authentication, communication protocols, and various settings that influence the client's behavior and interaction with the server.
 *
 * ### Connection Details
 *
 * - **Addresses**: Use the `addresses` property to specify the hostnames and ports of the server(s) to connect to.
 *   - **Cluster Mode**: In cluster mode, the client will discover other nodes based on the provided addresses.
 *   - **Standalone Mode**: In standalone mode, only the provided nodes will be used.
 * - **Lazy Connect**: Set `lazyConnect` to `true` to defer connection establishment until the first command is sent.
 *
 * ### Database Selection
 *
 * - **Database ID**: Use `databaseId` to specify which logical database to connect to (0-15 by default).
 *   - **Cluster Mode**: Requires Valkey 9.0+ with multi-database cluster mode enabled.
 *   - **Standalone Mode**: Works with all Valkey versions.
 *   - **Reconnection**: Database selection persists across reconnections.
 *
 * ### Security Settings
 *
 * - **TLS**: Enable secure communication using `useTLS`.
 *      Should match the TLS configuration of the server/cluster, otherwise the connection attempt will fail.
 *      For advanced tls configuration, , see {@link AdvancedBaseClientConfiguration}.
 * - **Authentication**: Provide `credentials` to authenticate with the server.
 *
 * ### Communication Settings
 *
 * - **Request Timeout**: Set `requestTimeout` to specify how long the client should wait for a request to complete.
 * - **Protocol Version**: Choose the serialization protocol using `protocol`.
 *
 * ### Client Identification
 *
 * - **Client Name**: Set `clientName` to identify the client connection.
 * - **Library Name**: Set `libName` to override the default library name (`GlideJS`) reported by `CLIENT INFO`; an empty value uses the default.
 * - **Client Info Tag**: Set `clientInfoTag` to append an attribution tag to the library name (e.g., `GlideJS(my-framework:1.0)`); an empty value adds no tag.
 * - Both options apply to ordinary standalone and cluster clients and to dedicated monitor clients. Every character in a non-empty value must be printable ASCII from `!` (U+0021) through `~` (U+007E), inclusive, excluding `(` and `)`, which are reserved as composition delimiters; otherwise a `ConfigurationError` is thrown.
 *
 * ### Read Strategy
 *
 * - Use `readFrom` to specify the client's read strategy (e.g., primary, preferReplica, AZAffinity, AZAffinityReplicasAndPrimary).
 *
 * ### Availability Zone
 *
 * - Use `clientAz` to specify the client's availability zone, which can influence read operations when using `readFrom: 'AZAffinity'or `readFrom: 'AZAffinityReplicasAndPrimary'`.
 *
 * ### Decoder Settings
 *
 * - **Default Decoder**: Set `defaultDecoder` to specify how responses are decoded by default.
 *
 * ### Concurrency Control
 *
 * - **Inflight Requests Limit**: Control the number of concurrent requests using `inflightRequestsLimit`.
 *
 * ### Reconnection Strategy
 * - **Reconnection Strategy**: Customize how the client should attempt reconnections using `connectionBackoff`.
 *   - `numberOfRetries`: The maximum number of retry attempts with increasing delays.
 *     - After this limit is reached, the retry interval becomes constant.
 *   - `factor`: A multiplier applied to the base delay between retries, specified in milliseconds (e.g., `500` means a 500ms base delay).
 *   - `exponentBase`: The exponential growth factor for delays (e.g., `2` means the delay doubles with each retry).
 *  - `jitterPercent`: An optional percentage of jitter to add to the delay (e.g., `30` means the final delay will vary randomly between 70% and 130% of the calculated delay).
 *
 * @example
 * ```typescript
 * const config: BaseClientConfiguration = {
 *   addresses: [
 *     { host: 'redis-node-1.example.com', port: 6379 },
 *     { host: 'redis-node-2.example.com' }, // Defaults to port 6379
 *   ],
 *   databaseId: 5, // Connect to database 5
 *   useTLS: true,
 *   credentials: {
 *     username: 'myUser',
 *     password: 'myPassword',
 *   },
 *   requestTimeout: 5000, // 5 seconds
 *   protocol: ProtocolVersion.RESP3,
 *   clientName: 'myValkeyClient',
 *   readFrom: ReadFrom.AZAffinity,
 *   clientAz: 'us-east-1a',
 *   defaultDecoder: Decoder.String,
 *   inflightRequestsLimit: 1000,
 *   connectionBackoff: {
 *     numberOfRetries: 10, // Maximum retries before delay becomes constant
 *     factor: 500,        // Base delay in milliseconds
 *     exponentBase: 2,    // Delay doubles with each retry (2^N)
 *     jitterPercent: 20,   // Optional jitter percentage
 *   },
 *   lazyConnect: true,
 * };
 * ```
 */
export interface BaseClientConfiguration {
    /**
     * DNS Addresses and ports of known nodes in the cluster.
     * If the server is in cluster mode the list can be partial, as the client will attempt to map out the cluster and find all nodes.
     * If the server is in standalone mode, only nodes whose addresses were provided will be used by the client.
     *
     * @example
     * ```typescript
     * configuration.addresses =
     * [
     *   { address: sample-address-0001.use1.cache.amazonaws.com, port:6378 },
     *   { address: sample-address-0002.use2.cache.amazonaws.com }
     *   { address: sample-address-0003.use2.cache.amazonaws.com, port:6380 }
     * ]
     * ```
     */
    addresses: {
        host: string;
        /**
         * If port isn't supplied, 6379 will be used
         */
        port?: number;
    }[];
    /**
     * Index of the logical database to connect to.
     *
     * @remarks
     * - **Standalone Mode**: Works with all Valkey versions.
     * - **Cluster Mode**: Requires Valkey 9.0+ with multi-database cluster mode enabled.
     * - **Reconnection**: Database selection persists across reconnections.
     * - **Default**: If not specified, defaults to database 0.
     * - **Range**: Must be non-negative. The server will validate the upper limit based on its configuration.
     * - **Server Validation**: The server determines the maximum database ID based on its `databases` configuration (standalone) or `cluster-databases` configuration (cluster mode).
     *
     * @example
     * ```typescript
     * // Connect to database 5
     * const config: BaseClientConfiguration = {
     *   addresses: [{ host: 'localhost', port: 6379 }],
     *   databaseId: 5
     * };
     *
     * // Connect to a higher database ID (server will validate the limit)
     * const configHighDb: BaseClientConfiguration = {
     *   addresses: [{ host: 'localhost', port: 6379 }],
     *   databaseId: 100
     * };
     * ```
     */
    databaseId?: number;
    /**
     * True if communication with the cluster should use Transport Level Security.
     * Should match the TLS configuration of the server/cluster,
     * otherwise the connection attempt will fail.
     */
    useTLS?: boolean;
    /**
     * Credentials for authentication process.
     * If none are set, the client will not authenticate itself with the server.
     */
    credentials?: ServerCredentials;
    /**
     * The duration in milliseconds that the client should wait for a request to complete.
     * This duration encompasses sending the request, awaiting for a response from the server, and any required reconnection or retries.
     * If the specified timeout is exceeded for a pending request, it will result in a timeout error.
     * If not explicitly set, a default value of 250 milliseconds will be used.
     * Value must be an integer.
     */
    requestTimeout?: number;
    /**
     * The client's read from strategy.
     * If not set, `Primary` will be used.
     */
    readFrom?: ReadFrom;
    /**
     * Serialization protocol to be used.
     * If not set, `RESP3` will be used.
     */
    protocol?: ProtocolVersion;
    /**
     * Client name to be used for the client. Will be used with CLIENT SETNAME command during connection establishment.
     */
    clientName?: string;
    /**
     * Optional library-name override sent with {@code CLIENT SETINFO LIB-NAME} during connection
     * establishment. If omitted or empty, the default {@code GlideJS} is used. When
     * {@link clientInfoTag} is present and non-empty, it is appended to the effective library name
     * in parentheses.
     *
     * This option applies to ordinary standalone and cluster clients and dedicated monitor clients.
     * Every character in a non-empty override must be printable ASCII from {@code !} (U+0021)
     * through {@code ~} (U+007E), inclusive, excluding {@code (} and {@code )}, which are
     * reserved as composition delimiters; all other in-range punctuation is preserved.
     *
     * @throws ConfigurationError if a non-empty override contains a character outside printable
     * ASCII U+0021 through U+007E or contains the reserved {@code (} or {@code )} delimiter.
     * See: validateClientAttr in https://github.com/valkey-io/valkey/blob/4e98093b208f956050fb441d89e1e2d7f91ac466/src/networking.c
     */
    libName?: string;
    /**
     * Optional attribution tag appended to the effective library name in parentheses.
     * For example, setting this to {@code "my-framework:1.2.3"} results in a lib-name of
     * {@code GlideJS(my-framework:1.2.3)} (or {@code custom-lib(my-framework:1.2.3)} if
     * {@link libName} is also set). An empty tag is treated as absent.
     *
     * This option applies to ordinary standalone and cluster clients and dedicated monitor clients.
     * Every character in a non-empty tag must be printable ASCII from {@code !} (U+0021) through
     * {@code ~} (U+007E), inclusive, excluding {@code (} and {@code )}, which are reserved as
     * composition delimiters; all other in-range punctuation is preserved.
     *
     * @throws ConfigurationError if a non-empty tag contains a character outside printable ASCII
     * U+0021 through U+007E or contains the reserved {@code (} or {@code )} delimiter.
     * See: validateClientAttr in https://github.com/valkey-io/valkey/blob/4e98093b208f956050fb441d89e1e2d7f91ac466/src/networking.c
     */
    clientInfoTag?: string;
    /**
     * Default decoder when decoder is not set per command.
     * If not set, 'Decoder.String' will be used.
     */
    defaultDecoder?: Decoder;
    /**
     * The maximum number of concurrent requests allowed to be in-flight (sent but not yet completed).
     * This limit is used to control the memory usage and prevent the client from overwhelming the
     * server or getting stuck in case of a queue backlog. If not set, a default value of 1000 will be
     * used.
     */
    inflightRequestsLimit?: number;
    /**
     * Availability Zone of the client.
     * If ReadFrom strategy is AZAffinity or AZAffinityReplicasAndPrimary, this setting ensures that readonly commands are directed to nodes within the specified AZ if they exist.
     *
     * @example
     * ```typescript
     * // Example configuration for setting client availability zone and read strategy
     * configuration.clientAz = 'us-east-1a'; // Sets the client's availability zone
     * configuration.readFrom = 'AZAffinity'; // Directs read operations to nodes within the same AZ
     * Or
     * configuration.readFrom = 'AZAffinityReplicasAndPrimary'; // Directs read operations to any node (primary or replica) within the same AZ
     * ```
     */
    clientAz?: string;

    /**
     * Strategy used to determine how and when to reconnect, in case of connection failures.
     * The time between attempts grows exponentially, following the formula rand(0 ... factor * (exponentBase ^ N)), where N is the number of failed attempts,
     * and rand(...) applies a jitter of up to `jitterPercent`% to introduce randomness and reduce retry storms.
     * The client will attempt to reconnect indefinitely. Once the maximum value is reached, that will remain the time between retry attempts until a
     * reconnect attempt is successful.
     * If not set, a default backoff strategy will be used.
     */
    connectionBackoff?: {
        /**
         * Number of retry attempts that the client should perform when disconnected from the server, where the time between retries increases.
         * Once the retries have reached the maximum value, the time between retries will remain constant until a reconnect attempt is succesful.
         * Value must be an integer.
         */
        numberOfRetries: number;
        /**
         * The multiplier that will be applied to the waiting time between each retry.
         * This value is specified in milliseconds.
         * Value must be an integer.
         */
        factor: number;
        /**
         * The exponent base configured for the strategy.
         * Value must be an integer.
         */
        exponentBase: number;
        /** The Jitter percent on the calculated duration.
         * If not set, a default value will be used.
         * Value is optional, and must be an integer.
         */
        jitterPercent?: number;
    };
    /**
     * Enables lazy connection mode, where physical connections to the server(s) are deferred
     * until the first command is sent. This can reduce startup latency and allow for client
     * creation in disconnected environments.
     *
     * - **Default**: `false` – connections are established immediately during client creation.
     *
     * @remarks
     * When `lazyConnect` is set to `true`, the client will not attempt to connect to the specified
     * nodes during initialization. Instead, connections will be established only when a command is
     * actually executed.
     *
     * Note that the first command executed with lazy connections may experience additional latency
     * as it needs to establish the connection first. During this initial connection, the standard
     * request timeout does not apply yet - instead, the connection establishment is governed by
     * `AdvancedBaseClientConfiguration::connectionTimeout`. The request timeout (`requestTimeout`)
     * only begins counting after the connection has been successfully established. This behavior
     * can effectively increase the total time needed for the first command to complete.
     *
     * This setting applies to both standalone and cluster modes. Note that if an operation is
     * attempted and connection fails (e.g., unreachable nodes), errors will surface at that point.
     *
     * @example
     * ```typescript
     * const client = await GlideClient.createClient({
     *   addresses: [{ host: "localhost", port: 6379 }],
     *   lazyConnect: true
     * });
     *
     * // No connection is made yet
     * await client.ping(); // Now the client connects and sends the command
     * ```
     */
    lazyConnect?: boolean;

    /**
     * Configuration for automatic compression of values.
     * When enabled, values that meet the minimum size threshold will be
     * automatically compressed before being sent to the server and
     * decompressed when retrieved.
     *
     * @example
     * ```typescript
     * const client = await GlideClient.createClient({
     *   addresses: [{ host: "localhost", port: 6379 }],
     *   compression: { enabled: true },
     * });
     * ```
     */
    compression?: CompressionConfiguration;

    /**
     * Client-side cache configuration.
     *
     * @remarks
     * When provided, enables client-side caching for cacheable commands (GET, MGET, HGETALL, SMEMBERS).
     * The cache reduces network round-trips and server load by storing frequently accessed data locally.
     * An MGET response may combine locally cached values with values fetched from the server, so it does not provide
     * an atomic snapshot across all keys.
     *
     * - **Memory Management**: The cache respects the configured memory limit and evicts entries based on the specified policy.
     * - **TTL Support**: Entries can have optional time-to-live values for automatic expiration.
     * - **Shared Caches**: Multiple clients can share the same cache instance using the same cache ID.
     * - **Metrics**: Optional metrics collection provides insights into cache performance.
     *
     * @example
     * ```typescript
     * // Simple cache configuration
     * const config: BaseClientConfiguration = {
     *   addresses: [{ host: 'localhost', port: 6379 }],
     *   clientSideCache: ClientSideCache.create(1024, 0), // 1MB cache, no TTL
     * };
     *
     * // Advanced cache configuration
     * const advancedConfig: BaseClientConfiguration = {
     *   addresses: [{ host: 'localhost', port: 6379 }],
     *   clientSideCache: new ClientSideCache({
     *     maxCacheKb: 2048,
     *     entryTtlMs: 300000,
     *     evictionPolicy: EvictionPolicy.LFU,
     *     enableMetrics: true,
     *   }),
     * };
     * ```
     */
    clientSideCache?: ClientSideCache;

    /**
     * Optional callback for resolving server addresses before connection.
     *
     * When provided, this callback will be invoked for each configured address during connection
     * establishment and during cluster topology refreshes. The callback receives the configured
     * host and port, and should return a tuple `[resolvedHost, resolvedPort]` with the actual
     * address to use for the connection.
     *
     * Use cases:
     * - Custom DNS resolution for service discovery
     * - Address translation for proxy setups
     * - Dynamic endpoint resolution for cloud environments
     *
     * If the resolver throws an exception or returns an invalid value, the original address
     * is used as a fallback.
     *
     * @example
     * ```typescript
     * const config: BaseClientConfiguration = {
     *   addresses: [{ host: "internal-service", port: 9999 }],
     *   addressResolver: (host, port) => {
     *     if (host === "internal-service") {
     *       return ["10.0.0.5", 6379];
     *     }
     *     return [host, port];
     *   },
     * };
     * ```
     */
    addressResolver?: (host: string, port: number) => [string, number];
    /**
     * Configuration for the client-wide circuit breaker.
     * When set, enables the circuit breaker which detects sustained error rates
     * and rejects requests before they enter the core.
     * If not set (undefined), the circuit breaker is disabled.
     */
    clientCircuitBreaker?: ClientCircuitBreakerConfiguration;
}

/**
 * Configuration for the client-wide circuit breaker.
 */
export interface ClientCircuitBreakerConfiguration {
    /** Sliding window duration in milliseconds for error rate calculation. Default: 10000. */
    windowSizeMs?: number;
    /** Error rate (0.0-1.0) within the window to trip the breaker. Default: 0.5. */
    failureRateThreshold?: number;
    /** Minimum errors within window before rate is evaluated. Default: 50. */
    minErrors?: number;
    /** Time in milliseconds in Open state before allowing a probe. Default: 5000. */
    openTimeoutMs?: number;
    /** Whether timeouts count toward tripping. Default: false. */
    countTimeouts?: boolean;
    /** Consecutive successful probes needed before closing. Default: 3. */
    consecutiveSuccesses?: number;
}

/**
 * Represents advanced configuration settings for a client, including connection-related options.
 *
 * @remarks
 * The `AdvancedBaseClientConfiguration` interface defines advanced configuration settings for managing the client's connection behavior.
 *
 * ### Connection Timeout
 *
 * - **Connection Timeout**: The `connectionTimeout` property specifies the duration (in milliseconds) the client should wait for a connection to be established.
 *
 * ### TLS config
 *
 * - **TLS Configuration**: The `tlsAdvancedConfiguration` property allows for advanced TLS settings, such as enabling insecure mode.
 *
 * @example
 * ```typescript
 * const config: AdvancedBaseClientConfiguration = {
 *   connectionTimeout: 5000, // 5 seconds
 * };
 * ```
 */
export interface AdvancedBaseClientConfiguration {
    /**
     * The duration in milliseconds to wait for a TCP/TLS connection to complete.
     * This applies both during initial client creation and any reconnection that may occur during request processing.
     * **Note**: A high connection timeout may lead to prolonged blocking of the entire command pipeline.
     * If not explicitly set, a default value of 2000 milliseconds will be used.
     */
    connectionTimeout?: number;

    /**
     * The advanced TLS configuration settings. This allows for more granular control of TLS behavior,
     * such as enabling an insecure mode that bypasses certificate validation.
     */
    tlsAdvancedConfiguration?: {
        /**
         * Whether to bypass TLS certificate verification.
         *
         * - When set to `true`, the client skips certificate validation.
         *   This is useful when connecting to servers or clusters using self-signed certificates,
         *   or when DNS entries (e.g., CNAMEs) don't match certificate hostnames.
         *
         * - This setting is typically used in development or testing environments.
         *   **It is strongly discouraged in production**, as it introduces security risks such as man-in-the-middle attacks.
         *
         * - Only valid if TLS is already enabled in the base client configuration.
         *   Enabling it without TLS will result in a `ConfigurationError`.
         *
         * - Default: false (verification is enforced).
         */
        insecure?: boolean;

        /**
         * Custom root certificate data for TLS connections.
         *
         * - When provided, these certificates will be used instead of the system's default trust store.
         *   If not provided, the system's default certificate trust store will be used.
         *
         * - The certificate data should be in PEM format as a string or Buffer.
         *
         * - This is useful when connecting to servers with self-signed certificates or custom certificate authorities.
         */
        rootCertificates?: string | Buffer;

        /**
         * Mutual TLS (mTLS) client authentication material. See
         * {@link MutualTls} for the two variants: `kind: "bytes"` for static
         * material and `kind: "path"` for material the core reloads from
         * disk. Reload is on iff `kind === "path"`. When
         * `reloadIntervalSeconds` is omitted, the reload cadence defaults to
         * the GLIDE core default (see `DEFAULT_RELOAD_INTERVAL_SECONDS` in
         * glide-core's `tls_reload` module).
         *
         * Requires `useTLS: true` on the base client configuration. Setting
         * `mutualTls` when TLS is disabled raises a {@link ConfigurationError}.
         */
        readonly mutualTls?: MutualTls;
    };

    /**
     * Controls TCP_NODELAY socket option (Nagle's algorithm).
     *
     * - When `true`, disables Nagle's algorithm for lower latency by sending packets immediately without buffering.
     *   This is optimal for Redis/Valkey workloads with many small requests.
     *
     * - When `false`, enables Nagle's algorithm to reduce network overhead by buffering small packets.
     *   This may increase latency by up to 200ms but reduces the number of packets sent.
     *
     * - If not explicitly set, a default value of `true` will be used by the Rust core.
     */
    tcpNoDelay?: boolean;

    /**
     * The interval in milliseconds between PubSub subscription reconciliation attempts.
     *
     * The reconciliation process ensures that the client's desired subscriptions match
     * the actual subscriptions on the server. This is useful when subscriptions may have
     * been lost due to network issues or server restarts.
     *
     * If not explicitly set, the Rust core will use its default reconciliation interval.
     *
     * @remarks
     * - Must be a positive integer representing milliseconds.
     * - The reconciliation process runs automatically in the background.
     * - A lower interval provides faster recovery from subscription issues but increases overhead.
     * - A higher interval reduces overhead but may delay recovery from subscription issues.
     *
     * @example
     * ```typescript
     * const config: GlideClientConfiguration = {
     *   addresses: [{ host: "localhost", port: 6379 }],
     *   advancedConfiguration: {
     *     pubsubReconciliationIntervalMs: 5000 // Reconcile every 5 seconds
     *   }
     * };
     * ```
     */
    pubsubReconciliationIntervalMs?: number;
}

/**
 * Enum of Valkey data types
 * `STRING`
 * `LIST`
 * `SET`
 * `ZSET`
 * `HASH`
 * `STREAM`
 */
export enum ObjectType {
    STRING = "String",
    LIST = "List",
    SET = "Set",
    ZSET = "ZSet",
    HASH = "Hash",
    STREAM = "Stream",
}

function getRequestErrorClass(
    type: response.RequestErrorType | null | undefined,
): typeof RequestError {
    if (type === response.RequestErrorType.Disconnect) {
        return ConnectionError;
    }

    if (type === response.RequestErrorType.ExecAbort) {
        return ExecAbortError;
    }

    if (type === response.RequestErrorType.Timeout) {
        return TimeoutError;
    }

    if (type === response.RequestErrorType.CircuitBreakerOpen) {
        return CircuitBreakerError;
    }

    if (type === response.RequestErrorType.Unspecified) {
        return RequestError;
    }

    return RequestError;
}

export interface PubSubMsg {
    message: GlideString;
    channel: GlideString;
    pattern?: GlideString | null;
}

/**
 * @internal
 * Options used when creating a write promise for a command.
 *
 * This type combines {@link RouteOption} and {@link DecoderOption} with optional support for
 * {@link ClusterBatchOptions} or {@link BatchOptions}.
 *
 * @see {@link DecoderOption}
 * @see {@link RouteOption}
 * @see {@link ClusterBatchOptions}
 * @see {@link BatchOptions}
 */
type BaseOptions = RouteOption & DecoderOption;
type WritePromiseOptions =
    BaseOptions | (BaseOptions & (ClusterBatchOptions | BatchOptions));

/**
 * Base client interface for GLIDE
 */
export class BaseClient {
    protected readonly promiseCallbackFunctions:
        | [PromiseFunction, ErrorFunction, Decoder | undefined][]
        | [PromiseFunction, ErrorFunction][] = [];
    protected readonly availableCallbackSlots: number[] = [];
    protected isClosed = false;
    protected defaultDecoder = Decoder.String;
    private readonly pubsubFutures: [PromiseFunction, ErrorFunction][] = [];
    private pendingPushNotification: response.Response[] = [];
    private config: BaseClientConfiguration | undefined;
    private addressResolverKey: string | undefined;
    protected clientHandle: GlideClientHandle | null = null;
    /** Stores OTel span pointers keyed by callbackIndex for span lifecycle management. */
    private readonly otelSpanPointers = new Map<number, bigint>();

    protected configurePubsub(
        options: GlideClusterClientConfiguration | GlideClientConfiguration,
        configuration: connection_request.IConnectionRequest,
    ) {
        if (options.pubsubSubscriptions) {
            if (options.protocol == ProtocolVersion.RESP2) {
                throw new ConfigurationError(
                    "PubSub subscriptions require RESP3 protocol, but RESP2 was configured.",
                );
            }

            const { context, callback } = options.pubsubSubscriptions;

            if (context && !callback) {
                throw new ConfigurationError(
                    "PubSub subscriptions with a context require a callback function to be configured.",
                );
            }

            configuration.pubsubSubscriptions =
                connection_request.PubSubSubscriptions.create({});

            for (const [channelType, channelsPatterns] of Object.entries(
                options.pubsubSubscriptions.channelsAndPatterns,
            )) {
                let entry =
                    configuration.pubsubSubscriptions!
                        .channelsOrPatternsByType![parseInt(channelType)];

                if (!entry) {
                    entry = connection_request.PubSubChannelsOrPatterns.create({
                        channelsOrPatterns: [],
                    });
                    configuration.pubsubSubscriptions!.channelsOrPatternsByType![
                        parseInt(channelType)
                    ] = entry;
                }

                for (const channelPattern of channelsPatterns) {
                    entry.channelsOrPatterns!.push(Buffer.from(channelPattern));
                }
            }
        }
    }

    protected toProtobufRoute(
        route: Routes | undefined,
    ): command_request.Routes | undefined {
        if (!route) {
            return undefined;
        }

        if (route === "allPrimaries") {
            return command_request.Routes.create({
                simpleRoutes: command_request.SimpleRoutes.AllPrimaries,
            });
        } else if (route === "allNodes") {
            return command_request.Routes.create({
                simpleRoutes: command_request.SimpleRoutes.AllNodes,
            });
        } else if (route === "randomNode") {
            return command_request.Routes.create({
                simpleRoutes: command_request.SimpleRoutes.Random,
            });
        } else if (route.type === "primarySlotKey") {
            return command_request.Routes.create({
                slotKeyRoute: command_request.SlotKeyRoute.create({
                    slotType: command_request.SlotTypes.Primary,
                    slotKey: route.key,
                }),
            });
        } else if (route.type === "replicaSlotKey") {
            return command_request.Routes.create({
                slotKeyRoute: command_request.SlotKeyRoute.create({
                    slotType: command_request.SlotTypes.Replica,
                    slotKey: route.key,
                }),
            });
        } else if (route.type === "primarySlotId") {
            return command_request.Routes.create({
                slotKeyRoute: command_request.SlotIdRoute.create({
                    slotType: command_request.SlotTypes.Primary,
                    slotId: route.id,
                }),
            });
        } else if (route.type === "replicaSlotId") {
            return command_request.Routes.create({
                slotKeyRoute: command_request.SlotIdRoute.create({
                    slotType: command_request.SlotTypes.Replica,
                    slotId: route.id,
                }),
            });
        } else if (route.type === "routeByAddress") {
            let port = route.port;
            let host = route.host;

            if (port === undefined) {
                const split = host.split(":");

                if (split.length !== 2) {
                    throw new RequestError(
                        "No port provided, expected host to be formatted as `{hostname}:{port}`. Received " +
                            host,
                    );
                }

                host = split[0];
                port = Number(split[1]);
            }

            return command_request.Routes.create({
                byAddressRoute: { host, port },
            });
        }
    }

    private encodeRouteBytes(
        route: Routes | undefined,
    ): Uint8Array | undefined {
        const protoRoute = this.toProtobufRoute(route);
        return protoRoute
            ? command_request.Routes.encode(protoRoute).finish()
            : undefined;
    }

    /**
     * Creates an OTel span for a command and stores the span pointer keyed by
     * callbackIndex so it can be dropped when the response arrives.
     *
     * Callers MUST gate this behind `OpenTelemetry.shouldSample()` to avoid
     * unnecessary work on the hot path when OTel is not sampling.
     */
    private createOtelSpanForCallback(
        callbackIndex: number,
        commandName: string,
    ): bigint {
        const parentCtx = OpenTelemetry.getParentSpanContext();
        const [low, high] = parentCtx
            ? createOtelSpanWithTraceContext(
                  commandName,
                  parentCtx.traceId,
                  parentCtx.spanId,
                  parentCtx.traceFlags,
                  parentCtx.traceState,
              )
            : createLeakedOtelSpan(commandName);
        // Combine split pointer into a single bigint for dropOtelSpan,
        // using Long to match the pointer representation used elsewhere.
        const spanPtr = BigInt(new Long(low, high, true).toString());
        this.otelSpanPointers.set(callbackIndex, spanPtr);
        return spanPtr;
    }

    /**
     * Drops the OTel span associated with the given callbackIndex, if one exists.
     */
    private dropOtelSpanForCallback(callbackIndex: number): void {
        const spanPtr = this.otelSpanPointers.get(callbackIndex);

        if (spanPtr !== undefined) {
            this.otelSpanPointers.delete(callbackIndex);
            dropOtelSpan(spanPtr);
        }
    }

    processPush(response: response.Response) {
        if (response.closingError != null || !response.respPointer) {
            const errMsg = response.closingError
                ? response.closingError
                : "Client Error - push notification without resp_pointer";

            this.close(errMsg);
            return;
        }

        const [callback, context] = this.getPubsubCallbackAndContext(
            this.config!,
        );

        if (callback) {
            const pubsubMessage =
                this.notificationToPubSubMessageSafe(response);

            if (pubsubMessage) {
                callback(pubsubMessage, context);
            }
        } else {
            this.pendingPushNotification.push(response);
            this.completePubSubFuturesSafe();
        }
    }

    /**
     * Handles command responses from the native layer.
     * @internal
     */
    private handleResponse = (response: CommandResponse): void => {
        if (response.closingError) {
            this.close(response.closingError);
            return;
        }

        // Handle push notifications (pub/sub)
        if (response.isPush) {
            if (
                response.respPointerHigh !== undefined &&
                response.respPointerLow !== undefined
            ) {
                // Create a response.Response-compatible object for processPush
                const pushResponse = {
                    respPointer: {
                        high: response.respPointerHigh,
                        low: response.respPointerLow,
                    },
                } as response.Response;
                this.processPush(pushResponse);
            }

            return;
        }

        const [resolve, reject, decoder = this.defaultDecoder] =
            this.promiseCallbackFunctions[response.callbackIdx];
        this.availableCallbackSlots.push(response.callbackIdx);

        if (response.requestError) {
            const errorType = getRequestErrorClass(
                response.requestError.errorType as response.RequestErrorType,
            );
            reject(new errorType(response.requestError.message ?? undefined));
        } else if (
            response.respPointerHigh !== undefined &&
            response.respPointerLow !== undefined
        ) {
            try {
                resolve(
                    valueFromSplitPointer(
                        response.respPointerHigh,
                        response.respPointerLow,
                        decoder === Decoder.String,
                    ),
                );
            } catch (err: unknown) {
                Logger.log("error", "Decoder", `Decoding error: '${err}'`);
                reject(
                    err instanceof ValkeyError
                        ? err
                        : new Error(
                              `Decoding error: '${err}'. \n NOTE: If this was thrown during a command with write operations, the data could be UNRECOVERABLY LOST.`,
                          ),
                );
            }
        } else if (response.constantResponse === "OK") {
            resolve("OK");
        } else {
            resolve(null);
        }

        this.dropOtelSpanForCallback(response.callbackIdx);
    };

    protected constructor(options?: BaseClientConfiguration) {
        // if logger has been initialized by the external-user on info level this log will be shown
        Logger.log("info", "Client lifetime", `construct client`);

        this.config = options;
        this.defaultDecoder = options?.defaultDecoder ?? Decoder.String;
    }

    protected getCallbackIndex(): number {
        return (
            this.availableCallbackSlots.pop() ??
            this.promiseCallbackFunctions.length
        );
    }

    protected ensureClientIsOpen() {
        if (this.isClosed) {
            throw new ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client.",
            );
        }
    }

    /**
     * @internal
     *
     * Creates a promise that resolves or rejects based on the result of a command request.
     *
     * @template T - The type of the result expected from the promise.
     * @param command - A single command or an array of commands to be executed, array of commands represents a batch and not a single command.
     * @param options - Optional settings for the write operation, including route, batch options and decoder.
     * @param isAtomic - Indicates whether the operation should be executed atomically (AKA as a Transaction, in the case of a batch). Defaults to `false`.
     * @param raiseOnError - Determines whether to raise an error if any of the commands fails, in the case of a Batch and not a single command. Defaults to `false`.
     * @returns A promise that resolves with the result of the command(s) or rejects with an error.
     */
    protected createWritePromise<T>(
        command: command_request.Command | command_request.Command[],
        options: WritePromiseOptions = {},

        isAtomic = false,

        raiseOnError = false,
    ): Promise<T> {
        this.ensureClientIsOpen();

        if (!this.clientHandle) {
            throw new ClosingError(
                "Client handle not initialized. Please create a new client.",
            );
        }

        if (!Array.isArray(command)) {
            return this.sendCommand<T>(command, options);
        }

        // Batch commands
        return this.sendBatch<T>(command, options, isAtomic, raiseOnError).then(
            (result: T) => {
                // Patch error prototypes in batch results.
                // The Rust NAPI layer returns errors as plain JS Error objects with
                // name="RequestError". We need to re-class them as RequestError instances
                // so that `instanceof RequestError` checks work correctly.
                if (Array.isArray(result)) {
                    for (const item of result) {
                        if (
                            item?.constructor?.name === "Error" &&
                            /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
                            (item as any).name === "RequestError"
                        ) {
                            Object.setPrototypeOf(item, RequestError.prototype);
                        }
                    }
                }

                return result;
            },
        );
    }

    /**
     * Sends a batch of commands to the server.
     * @internal
     */
    private sendBatch<T>(
        commands: command_request.Command[],
        options: WritePromiseOptions = {},
        isAtomic: boolean,
        raiseOnError: boolean,
    ): Promise<T> {
        // Validate: retry strategy is not supported for atomic batches (transactions)
        if (isAtomic && "retryStrategy" in options && options.retryStrategy) {
            return Promise.reject(
                new RequestError(
                    "Retry strategy is not supported for atomic batches.",
                ),
            ) as Promise<T>;
        }

        let routeBytes: Uint8Array | undefined;

        try {
            routeBytes = this.encodeRouteBytes(options.route);
        } catch (err) {
            return Promise.reject(err) as Promise<T>;
        }

        const callbackIndex = this.getCallbackIndex();
        let spanPtr: bigint | undefined;

        // Create an OTel span for this batch if tracing is enabled
        if (OpenTelemetry.shouldSample()) {
            spanPtr = this.createOtelSpanForCallback(callbackIndex, "Batch");
        }

        return new Promise<T>((resolve, reject) => {
            this.promiseCallbackFunctions[callbackIndex] = [
                resolve,
                reject,
                options?.decoder,
            ];

            // Convert commands to BatchCommand format
            const pointerBackedCommands: command_request.Command[] = [];
            const batchCommands = commands.map((cmd) => {
                let argsPointerHigh = 0;
                let argsPointerLow = 0;

                if (cmd.argsVecPointer) {
                    // Already have a heap pointer
                    pointerBackedCommands.push(cmd);

                    if (typeof cmd.argsVecPointer === "number") {
                        const long = Long.fromNumber(cmd.argsVecPointer);
                        argsPointerHigh = long.high;
                        argsPointerLow = long.low;
                    } else {
                        argsPointerHigh = cmd.argsVecPointer.high;
                        argsPointerLow = cmd.argsVecPointer.low;
                    }
                } else if (cmd.argsArray?.args) {
                    // Create a leaked string vec from the args array
                    const argsAsUint8Arrays = cmd.argsArray.args.map((arg) =>
                        arg instanceof Uint8Array
                            ? arg
                            : new TextEncoder().encode(arg as string),
                    );
                    const [low, high] =
                        createLeakedStringVec(argsAsUint8Arrays);
                    argsPointerHigh = high;
                    argsPointerLow = low;
                }

                return {
                    requestType: cmd.requestType,
                    argsPointerHigh,
                    argsPointerLow,
                };
            });

            // Extract timeout from options if present
            const timeout =
                "timeout" in options ? (options.timeout as number) : undefined;

            // Extract retry strategy from options if present (cluster batch only)
            const retryServerError =
                "retryStrategy" in options
                    ? options.retryStrategy?.retryServerError
                    : undefined;
            const retryConnectionError =
                "retryStrategy" in options
                    ? options.retryStrategy?.retryConnectionError
                    : undefined;

            // Call the Rust sendBatch via NAPI
            const success = this.clientHandle!.sendBatch(
                callbackIndex,
                batchCommands,
                isAtomic,
                raiseOnError,
                timeout,
                retryServerError,
                retryConnectionError,
                spanPtr,
                routeBytes,
            );

            for (const cmd of pointerBackedCommands) {
                cmd.argsVecPointer = undefined;
            }

            if (!success) {
                // Inflight limit exceeded - drop span and clean up
                this.dropOtelSpanForCallback(callbackIndex);
                this.availableCallbackSlots.push(callbackIndex);
                reject(
                    new RequestError(
                        "Inflight request limit exceeded. Please try again later.",
                    ),
                );
            }
        });
    }

    /**
     * Sends a single command to the server and returns a promise for the result.
     * @internal
     */
    private sendCommand<T>(
        command: command_request.Command,
        options: WritePromiseOptions = {},
    ): Promise<T> {
        let routeBytes: Uint8Array | undefined;

        try {
            routeBytes = this.encodeRouteBytes(options.route);
        } catch (err) {
            return Promise.reject(err) as Promise<T>;
        }

        const callbackIndex = this.getCallbackIndex();
        let spanPtr: bigint | undefined;

        // Create an OTel span for this command if tracing is enabled.
        // Defer the command name lookup to avoid the string table access
        // on every request when OTel is not sampling.
        if (OpenTelemetry.shouldSample()) {
            const commandName =
                command_request.RequestType[command.requestType] ?? "Unknown";
            spanPtr = this.createOtelSpanForCallback(
                callbackIndex,
                commandName,
            );
        }

        return new Promise<T>((resolve, reject) => {
            this.promiseCallbackFunctions[callbackIndex] = [
                resolve,
                reject,
                options?.decoder,
            ];

            // Get arguments from the command
            // The command.argsArray contains the arguments, or argsVecPointer contains a heap pointer
            let argsPointerHigh = 0;
            let argsPointerLow = 0;

            if (command.argsVecPointer) {
                // Already have a heap pointer from createLeakedStringVec
                if (typeof command.argsVecPointer === "number") {
                    const long = Long.fromNumber(command.argsVecPointer);
                    argsPointerHigh = long.high;
                    argsPointerLow = long.low;
                } else {
                    argsPointerHigh = command.argsVecPointer.high;
                    argsPointerLow = command.argsVecPointer.low;
                }
            } else if (command.argsArray?.args) {
                // Create a leaked string vec from the args array
                const argsAsUint8Arrays = command.argsArray.args.map((arg) =>
                    arg instanceof Uint8Array
                        ? arg
                        : new TextEncoder().encode(arg as string),
                );
                const [low, high] = createLeakedStringVec(argsAsUint8Arrays);
                argsPointerHigh = high;
                argsPointerLow = low;
            }

            // Call the Rust sendCommand via NAPI
            const success = this.clientHandle!.sendCommand(
                callbackIndex,
                command.requestType,
                argsPointerHigh,
                argsPointerLow,
                spanPtr,
                routeBytes,
            );

            if (command.argsVecPointer) {
                command.argsVecPointer = undefined;
            }

            if (!success) {
                // Inflight limit exceeded - drop span and clean up
                this.dropOtelSpanForCallback(callbackIndex);
                this.availableCallbackSlots.push(callbackIndex);
                reject(
                    new RequestError(
                        "Inflight request limit exceeded. Please try again later.",
                    ),
                );
            }
        });
    }

    protected createUpdateConnectionPasswordPromise(
        command: command_request.UpdateConnectionPassword,
    ): Promise<GlideString> {
        this.ensureClientIsOpen();
        const callbackIndex = this.getCallbackIndex();

        return new Promise<GlideString>((resolve, reject) => {
            this.promiseCallbackFunctions[callbackIndex] = [
                resolve,
                reject,
                undefined,
            ];

            const success = this.clientHandle!.updateConnectionPassword(
                callbackIndex,
                command.password ?? null,
                command.immediateAuth ?? false,
            );

            if (!success) {
                this.availableCallbackSlots.push(callbackIndex);
                reject(
                    new RequestError(
                        "Inflight request limit exceeded. Please try again later.",
                    ),
                );
            }
        });
    }

    protected createRefreshIamTokenPromise(
        _command: command_request.RefreshIamToken, // eslint-disable-line @typescript-eslint/no-unused-vars
    ): Promise<GlideString> {
        this.ensureClientIsOpen();
        const callbackIndex = this.getCallbackIndex();

        return new Promise<GlideString>((resolve, reject) => {
            this.promiseCallbackFunctions[callbackIndex] = [
                resolve,
                reject,
                undefined,
            ];

            const success = this.clientHandle!.refreshIamToken(callbackIndex);

            if (!success) {
                this.availableCallbackSlots.push(callbackIndex);
                reject(
                    new RequestError(
                        "Inflight request limit exceeded. Please try again later.",
                    ),
                );
            }
        });
    }

    protected createGetCacheMetricsPromise(
        command: command_request.GetCacheMetrics,
    ) {
        this.ensureClientIsOpen();
        const callbackIdx = this.getCallbackIndex();

        return new Promise<number>((resolve, reject) => {
            this.promiseCallbackFunctions[callbackIdx] = [resolve, reject];

            const success = this.clientHandle!.getCacheMetrics(
                callbackIdx,
                command.metricsTypes,
            );

            if (!success) {
                this.availableCallbackSlots.push(callbackIdx);
                reject(
                    new RequestError(
                        "Inflight request limit exceeded. Please try again later.",
                    ),
                );
            }
        });
    }

    /**
     * @internal
     * Get cache metrics.
     *
     * @param metricsType - Type of metric to retrieve (e.g., hit rate, miss rate).
     * @returns The requested cache metric.
     * @throws RequestError if client-side caching is not enabled or metrics tracking is disabled.
     */
    private async getCacheMetrics(
        metricsType: command_request.CacheMetricsType,
    ): Promise<number> {
        const getCacheMetrics = command_request.GetCacheMetrics.create({
            metricsTypes: metricsType,
        });
        return await this.createGetCacheMetricsPromise(getCacheMetrics);
    }

    protected createScriptInvocationPromise<T = GlideString>(
        command: command_request.ScriptInvocation,
        options: DecoderOption & RouteOption = {},
    ): Promise<T> {
        this.ensureClientIsOpen();

        let routeBytes: Uint8Array | undefined;

        try {
            routeBytes = this.encodeRouteBytes(options.route);
        } catch (err) {
            return Promise.reject(err) as Promise<T>;
        }

        const callbackIndex = this.getCallbackIndex();

        return new Promise<T>((resolve, reject) => {
            this.promiseCallbackFunctions[callbackIndex] = [
                resolve,
                reject,
                options?.decoder,
            ];

            // Convert keys to pointer
            let keysPointerHigh = 0;
            let keysPointerLow = 0;

            if (command.keys && command.keys.length > 0) {
                const keysAsUint8Arrays = command.keys.map(
                    (key: Uint8Array | string) =>
                        key instanceof Uint8Array
                            ? key
                            : new TextEncoder().encode(key as string),
                );
                const [low, high] = createLeakedStringVec(keysAsUint8Arrays);
                keysPointerHigh = high;
                keysPointerLow = low;
            }
            // else: keep default (0, 0) — Rust treats null pointer as empty vec

            // Convert args to pointer
            let argsPointerHigh = 0;
            let argsPointerLow = 0;

            if (command.args && command.args.length > 0) {
                const argsAsUint8Arrays = command.args.map(
                    (arg: Uint8Array | string) =>
                        arg instanceof Uint8Array
                            ? arg
                            : new TextEncoder().encode(arg as string),
                );
                const [low, high] = createLeakedStringVec(argsAsUint8Arrays);
                argsPointerHigh = high;
                argsPointerLow = low;
            }
            // else: keep default (0, 0) — Rust treats null pointer as empty vec

            const success = this.clientHandle!.invokeScript(
                callbackIndex,
                command.hash,
                keysPointerHigh,
                keysPointerLow,
                argsPointerHigh,
                argsPointerLow,
                routeBytes,
            );

            if (!success) {
                this.availableCallbackSlots.push(callbackIndex);
                reject(
                    new RequestError(
                        "Inflight request limit exceeded. Please try again later.",
                    ),
                );
            }
        });
    }

    // Define a common function to process the result of a batch with set commands
    /**
     * @internal
     */
    protected processResultWithSetCommands(
        result: GlideReturnType[] | null,
        setCommandsIndexes: number[],
    ): GlideReturnType[] | null {
        if (result === null) {
            return null;
        }

        for (let i = 0, len = setCommandsIndexes.length; i < len; i++) {
            if (Array.isArray(result[setCommandsIndexes[i]])) {
                result[setCommandsIndexes[i]] = new Set<GlideReturnType>(
                    result[setCommandsIndexes[i]] as GlideReturnType[],
                );
            }
        }

        return result;
    }

    cancelPubSubFuturesWithExceptionSafe(exception: ConnectionError): void {
        while (this.pubsubFutures.length > 0) {
            const nextFuture = this.pubsubFutures.shift();

            if (nextFuture) {
                const [, reject] = nextFuture;
                reject(exception);
            }
        }
    }

    isPubsubConfigured(
        config: GlideClientConfiguration | GlideClusterClientConfiguration,
    ): boolean {
        return !!config.pubsubSubscriptions;
    }

    getPubsubCallbackAndContext(
        config: GlideClientConfiguration | GlideClusterClientConfiguration,
        /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
    ): [((msg: PubSubMsg, context: any) => void) | null | undefined, any] {
        if (config.pubsubSubscriptions) {
            return [
                config.pubsubSubscriptions.callback,
                config.pubsubSubscriptions.context,
            ];
        }

        return [null, null];
    }

    public async getPubSubMessage(): Promise<PubSubMsg> {
        if (this.isClosed) {
            throw new ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client.",
            );
        }

        // only throw error if BOTH config exists AND callback exists
        if (
            this.isPubsubConfigured(this.config!) &&
            this.getPubsubCallbackAndContext(this.config!)[0]
        ) {
            throw new ConfigurationError(
                "The operation will never complete since messages will be passed to the configured callback.",
            );
        }

        return new Promise((resolve, reject) => {
            this.pubsubFutures.push([resolve, reject]);
            this.completePubSubFuturesSafe();
        });
    }

    public tryGetPubSubMessage(decoder?: Decoder): PubSubMsg | null {
        if (this.isClosed) {
            throw new ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client.",
            );
        }

        // only throw error if BOTH config exists AND callback exists
        if (
            this.isPubsubConfigured(this.config!) &&
            this.getPubsubCallbackAndContext(this.config!)[0]
        ) {
            throw new ConfigurationError(
                "The operation will never complete since messages will be passed to the configured callback.",
            );
        }

        let msg: PubSubMsg | null = null;
        this.completePubSubFuturesSafe();

        while (this.pendingPushNotification.length > 0 && !msg) {
            const pushNotification = this.pendingPushNotification.shift()!;
            msg = this.notificationToPubSubMessageSafe(
                pushNotification,
                decoder,
            );
        }

        return msg;
    }
    notificationToPubSubMessageSafe(
        pushNotification: response.Response,
        decoder?: Decoder,
    ): PubSubMsg | null {
        let msg: PubSubMsg | null = null;
        const responsePointer = pushNotification.respPointer;
        let nextPushNotificationValue: Record<string, unknown>;
        const isStringDecoder =
            (decoder ?? this.defaultDecoder) === Decoder.String;

        if (responsePointer) {
            nextPushNotificationValue =
                typeof responsePointer === "number"
                    ? (valueFromPointer(
                          responsePointer,
                          isStringDecoder,
                      ) as Record<string, unknown>)
                    : (valueFromSplitPointer(
                          responsePointer.high,
                          responsePointer.low,
                          isStringDecoder,
                      ) as Record<string, unknown>);

            const messageKind = nextPushNotificationValue["kind"];

            if (messageKind === "Disconnect") {
                Logger.log(
                    "warn",
                    "disconnect notification",
                    "Transport disconnected, messages might be lost",
                );
            } else if (
                messageKind === "Message" ||
                messageKind === "PMessage" ||
                messageKind === "SMessage"
            ) {
                const values = nextPushNotificationValue[
                    "values"
                ] as GlideString[];

                if (messageKind === "PMessage") {
                    msg = {
                        message: values[2],
                        channel: values[1],
                        pattern: values[0],
                    };
                } else {
                    msg = {
                        message: values[1],
                        channel: values[0],
                        pattern: null,
                    };
                }
            } else if (
                messageKind === "PSubscribe" ||
                messageKind === "Subscribe" ||
                messageKind === "SSubscribe" ||
                messageKind === "Unsubscribe" ||
                messageKind === "SUnsubscribe" ||
                messageKind === "PUnsubscribe"
            ) {
                // pass
            } else {
                Logger.log(
                    "error",
                    "unknown notification",
                    `Unknown notification: '${messageKind}'`,
                );
            }
        }

        return msg;
    }
    completePubSubFuturesSafe() {
        while (
            this.pendingPushNotification.length > 0 &&
            this.pubsubFutures.length > 0
        ) {
            const nextPushNotification = this.pendingPushNotification.shift()!;
            const pubsubMessage =
                this.notificationToPubSubMessageSafe(nextPushNotification);

            if (pubsubMessage) {
                const [resolve] = this.pubsubFutures.shift()!;
                resolve(pubsubMessage);
            }
        }
    }

    /** Get the value associated with the given `key`, or `null` if no such `key` exists.
     *
     * @see {@link https://valkey.io/commands/get/|valkey.io} for details.
     *
     * @param key - The `key` to retrieve from the database.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns If `key` exists, returns the value of `key`. Otherwise, return `null`.
     *
     * @example
     * ```typescript
     * // Example usage of get method to retrieve the value of a key
     * const result = await client.get("key");
     * console.log(result);
     * // Output: 'value'
     *
     * // Example usage of get method to retrieve the value of a key with Bytes decoder
     * const result = await client.get("key", { decoder: Decoder.Bytes });
     * console.log(result);
     * // Output: <Buffer 76 61 6c 75 65>
     * ```
     */
    public async get(
        key: GlideString,
        options?: DecoderOption,
    ): Promise<GlideString | null> {
        return this.createWritePromise(createGet(key), options);
    }

    /**
     * Get the value of `key` and optionally set its expiration. `GETEX` is similar to {@link get}.
     *
     * @see {@link https://valkey.io/commands/getex/|valkey.op} for more details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param key - The key to retrieve from the database.
     * @param options - (Optional) Additional Parameters:
     * - (Optional) `expiry`: expiriation to the given key:
     * `"persist"` will retain the time to live associated with the key. Equivalent to `PERSIST` in the VALKEY API.
     * Otherwise, a {@link TimeUnit} and duration of the expire time should be specified.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns If `key` exists, returns the value of `key` as a `string`. Otherwise, return `null`.
     *
     * @example
     * ```typescript
     * const result = await client.getex("key", {expiry: { type: TimeUnit.Seconds, count: 5 }});
     * console.log(result);
     * // Output: 'value'
     * ```
     */
    public async getex(
        key: GlideString,
        options?: {
            expiry: "persist" | { type: TimeUnit; duration: number };
        } & DecoderOption,
    ): Promise<GlideString | null> {
        return this.createWritePromise(
            createGetEx(key, options?.expiry),
            options,
        );
    }

    /**
     * Gets a string value associated with the given `key`and deletes the key.
     *
     * @see {@link https://valkey.io/commands/getdel/|valkey.io} for details.
     *
     * @param key - The key to retrieve from the database.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns If `key` exists, returns the `value` of `key`. Otherwise, return `null`.
     *
     * @example
     * ```typescript
     * const result = client.getdel("key");
     * console.log(result);
     * // Output: 'value'
     *
     * const value = client.getdel("key");  // value is null
     * ```
     */
    public async getdel(
        key: GlideString,
        options?: DecoderOption,
    ): Promise<GlideString | null> {
        return this.createWritePromise(createGetDel(key), options);
    }

    /**
     * Returns the substring of the string value stored at `key`, determined by the byte offsets
     * `start` and `end` (both are inclusive). Negative offsets can be used in order to provide
     * an offset starting from the end of the string. So `-1` means the last character, `-2` the
     * penultimate and so forth. If `key` does not exist, an empty string is returned. If `start`
     * or `end` are out of range, returns the substring within the valid range of the string.
     *
     * @see {@link https://valkey.io/commands/getrange/|valkey.io} for details.
     *
     * @param key - The key of the string.
     * @param start - The starting byte offset.
     * @param end - The ending byte offset.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A substring extracted from the value stored at `key`.
     *
     * @example
     * ```typescript
     * await client.set("mykey", "This is a string")
     * let result = await client.getrange("mykey", 0, 3)
     * console.log(result);
     * // Output: "This"
     *
     * result = await client.getrange("mykey", -3, -1)
     * console.log(result);
     * // Output: "ing"
     * // extracted last 3 characters of a string
     *
     * result = await client.getrange("mykey", 0, 100)
     * console.log(result);
     * // Output: "This is a string"
     *
     * result = await client.getrange("mykey", 5, 6)
     * console.log(result);
     * // Output: ""
     * ```
     */
    public async getrange(
        key: GlideString,
        start: number,
        end: number,
        options?: DecoderOption,
    ): Promise<GlideString | null> {
        return this.createWritePromise(
            createGetRange(key, start, end),
            options,
        );
    }

    /** Set the given key with the given value. Return value is dependent on the passed options.
     *
     * @see {@link https://valkey.io/commands/set/|valkey.io} for details.
     *
     * @param key - The key to store.
     * @param value - The value to store with the given key.
     * @param options - (Optional) See {@link SetOptions} and {@link DecoderOption}.
     * @returns - If the value is successfully set, return OK.
     * If `conditional` in `options` is not set, the value will be set regardless of prior value existence.
     * If value isn't set because of `onlyIfExists` or `onlyIfDoesNotExist` or `onlyIfEqual` conditions, return `null`.
     * If `returnOldValue` is set, return the old value as a string.
     *
     * @example
     * ```typescript
     * // Example usage of set method to set a key-value pair
     * const result = await client.set("my_key", "my_value");
     * console.log(result); // Output: 'OK'
     *
     * // Example usage of set method with conditional options and expiration
     * const result2 = await client.set("key", "new_value", {conditionalSet: "onlyIfExists", expiry: { type: TimeUnit.Seconds, count: 5 }});
     * console.log(result2);
     * // Output: 'OK'
     * // Set "new_value" to `key" only if `key` already exists, and set the key expiration to 5 seconds.
     *
     * // Example usage of set method with conditional options and returning old value
     * const result3 = await client.set("key", "value", {conditionalSet: "onlyIfDoesNotExist", returnOldValue: true});
     * console.log(result3);
     * // Output: 'new_value'
     * // Returns the old value of `key`.
     *
     * // Example usage of get method to retrieve the value of a key
     * const result4 = await client.get("key");
     * console.log(result4);
     * // Output: 'new_value'
     * // Value wasn't modified back to being "value" because of "NX" flag.
     *
     * // Example usage of set method with conditional option IFEQ
     * await client.set("key", "value we will compare to");
     * const result5 = await client.set("key", "new_value", {conditionalSet: "onlyIfEqual", comparisonValue: "value we will compare to"});
     * console.log(result5);
     * // Output: 'OK'
     * // Set "new_value" to "key" only if comparisonValue is equal to the current value of "key".
     *
     * const result6 = await client.set("key", "another_new_value", {conditionalSet: "onlyIfEqual", comparisonValue: "value we will compare to"});
     * console.log(result6);
     * // Output: null
     * // Value wasn't set because the comparisonValue is not equal to the current value of `key`. Value of `key` remains "new_value".
     * ```
     */
    public async set(
        key: GlideString,
        value: GlideString,
        options?: SetOptions & DecoderOption,
    ): Promise<"OK" | GlideString | null> {
        return this.createWritePromise(createSet(key, value, options), options);
    }

    /**
     * Removes the specified keys. A key is ignored if it does not exist.
     *
     * @see {@link https://valkey.io/commands/del/|valkey.io} for details.
     *
     * @remarks In cluster mode, if keys in `keys` map to different hash slots,
     * the command will be split across these slots and executed separately for each.
     * This means the command is atomic only at the slot level. If one or more slot-specific
     * requests fail, the entire call will return the first encountered error, even
     * though some requests may have succeeded while others did not.
     * If this behavior impacts your application logic, consider splitting the
     * request into sub-requests per slot to ensure atomicity.
     *
     * @param keys - The keys we wanted to remove.
     * @returns The number of keys that were removed.
     *
     * @example
     * ```typescript
     * // Example usage of del method to delete an existing key
     * await client.set("my_key", "my_value");
     * const result = await client.del(["my_key"]);
     * console.log(result); // Output: 1
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of del method for a non-existing key
     * const result = await client.del(["non_existing_key"]);
     * console.log(result); // Output: 0
     * ```
     */
    public async del(keys: GlideString[]): Promise<number> {
        return this.createWritePromise(createDel(keys));
    }

    /**
     * Serialize the value stored at `key` in a Valkey-specific format and return it to the user.
     *
     * @see {@link https://valkey.io/commands/dump/|valkey.io} for details.
     *
     * @param key - The `key` to serialize.
     * @returns The serialized value of the data stored at `key`. If `key` does not exist, `null` will be returned.
     *
     * @example
     * ```typescript
     * let result = await client.dump("myKey");
     * console.log(result);
     * // Result contains the serialized value of `myKey`
     * ```
     *
     * @example
     * ```typescript
     * result = await client.dump("nonExistingKey");
     * console.log(result);
     * // Output: null
     * ```
     */
    public async dump(key: GlideString): Promise<Buffer | null> {
        return this.createWritePromise(createDump(key), {
            decoder: Decoder.Bytes,
        });
    }

    /**
     * Create a `key` associated with a `value` that is obtained by deserializing the provided
     * serialized `value` (obtained via {@link dump}).
     *
     * @see {@link https://valkey.io/commands/restore/|valkey.io} for details.
     * @remarks `options.idletime` and `options.frequency` modifiers cannot be set at the same time.
     *
     * @param key - The `key` to create.
     * @param ttl - The expiry time (in milliseconds). If `0`, the `key` will persist.
     * @param value - The serialized value to deserialize and assign to `key`.
     * @param options - (Optional) Restore options {@link RestoreOptions}.
     * @returns Return "OK" if the `key` was successfully restored with a `value`.
     *
     * @example
     * ```typescript
     * const result = await client.restore("myKey", 0, value);
     * console.log(result); // Output: "OK"
     * ```
     *
     * @example
     * ```typescript
     * const result = await client.restore("myKey", 1000, value, {replace: true, absttl: true});
     * console.log(result); // Output: "OK"
     * ```
     *
     * @example
     * ```typescript
     * const result = await client.restore("myKey", 0, value, {replace: true, idletime: 10});
     * console.log(result); // Output: "OK"
     * ```
     *
     * @example
     * ```typescript
     * const result = await client.restore("myKey", 0, value, {replace: true, frequency: 10});
     * console.log(result); // Output: "OK"
     * ```
     */
    public async restore(
        key: GlideString,
        ttl: number,
        value: Buffer,
        options?: RestoreOptions,
    ): Promise<"OK"> {
        return this.createWritePromise(
            createRestore(key, ttl, value, options),
            { decoder: Decoder.String },
        );
    }

    /** Retrieve the values of multiple keys.
     *
     * @see {@link https://valkey.io/commands/mget/|valkey.io} for details.
     *
     * @remarks In cluster mode, if keys in `keys` map to different hash slots,
     * the command will be split across these slots and executed separately for each.
     * This means the command is atomic only at the slot level. If one or more slot-specific
     * requests fail, the entire call will return the first encountered error, even
     * though some requests may have succeeded while others did not.
     * If this behavior impacts your application logic, consider splitting the
     * request into sub-requests per slot to ensure atomicity.
     *
     * @param keys - A list of keys to retrieve values for.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A list of values corresponding to the provided keys. If a key is not found,
     * its corresponding value in the list will be null.
     *
     * @example
     * ```typescript
     * // Example usage of mget method to retrieve values of multiple keys
     * await client.set("key1", "value1");
     * await client.set("key2", "value2");
     * const result = await client.mget(["key1", "key2"]);
     * console.log(result);
     * // Output: ['value1', 'value2']
     * ```
     */
    public async mget(
        keys: GlideString[],
        options?: DecoderOption,
    ): Promise<(GlideString | null)[]> {
        return this.createWritePromise(createMGet(keys), options);
    }

    /** Set multiple keys to multiple values in a single operation.
     *
     * @see {@link https://valkey.io/commands/mset/|valkey.io} for details.
     *
     * @remarks In cluster mode, if keys in `keyValueMap` map to different hash slots,
     * the command will be split across these slots and executed separately for each.
     * This means the command is atomic only at the slot level. If one or more slot-specific
     * requests fail, the entire call will return the first encountered error, even
     * though some requests may have succeeded while others did not.
     * If this behavior impacts your application logic, consider splitting the
     * request into sub-requests per slot to ensure atomicity.
     *
     * @param keysAndValues - A list of key-value pairs to set.
     *
     * @returns A simple "OK" response.
     *
     * @example
     * ```typescript
     * // Example usage of mset method to set values for multiple keys
     * const result = await client.mset({"key1": "value1", "key2": "value2"});
     * console.log(result); // Output: 'OK'
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of mset method to set values for multiple keys (GlideRecords allow binary data in the key)
     * const result = await client.mset([{key: "key1", value: "value1"}, {key: "key2", value: "value2"}]);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async mset(
        keysAndValues: Record<string, GlideString> | GlideRecord<GlideString>,
    ): Promise<"OK"> {
        return this.createWritePromise(
            createMSet(convertGlideRecord(keysAndValues)),
        );
    }

    /**
     * Sets multiple keys to values if the key does not exist. The operation is atomic, and if one or
     * more keys already exist, the entire operation fails.
     *
     * @see {@link https://valkey.io/commands/msetnx/|valkey.io} for more details.
     * @remarks When in cluster mode, all keys in `keyValueMap` must map to the same hash slot.
     *
     * @param keysAndValues  - A list of key-value pairs to set.
     * @returns `true` if all keys were set. `false` if no key was set.
     *
     * @example
     * ```typescript
     * const result1 = await client.msetnx({"key1": "value1", "key2": "value2"});
     * console.log(result1); // Output: `true`
     *
     * const result2 = await client.msetnx({"key2": "value4", "key3": "value5"});
     * console.log(result2); // Output: `false`
     * ```
     */
    public async msetnx(
        keysAndValues: Record<string, GlideString> | GlideRecord<GlideString>,
    ): Promise<boolean> {
        return this.createWritePromise(
            createMSetNX(convertGlideRecord(keysAndValues)),
        );
    }

    /**
     * Move `key` from the currently selected database to the database specified by `dbIndex`.
     *
     * @remarks Move is available for cluster mode since Valkey 9.0.0 and above.
     *
     * @see {@link https://valkey.io/commands/move/|valkey.io} for more details.
     *
     * @param key - The key to move.
     * @param dbIndex - The index of the database to move `key` to.
     * @returns `true` if `key` was moved, or `false` if the `key` already exists in the destination
     *     database or does not exist in the source database.
     *
     * @example
     * ```typescript
     * const result = await client.move("key", 1);
     * console.log(result); // Output: true
     * ```
     */
    public async move(key: GlideString, dbIndex: number): Promise<boolean> {
        return this.createWritePromise(createMove(key, dbIndex));
    }

    /** Increments the number stored at `key` by one. If `key` does not exist, it is set to 0 before performing the operation.
     *
     * @see {@link https://valkey.io/commands/incr/|valkey.io} for details.
     *
     * @param key - The key to increment its value.
     * @returns the value of `key` after the increment.
     *
     * @example
     * ```typescript
     * // Example usage of incr method to increment the value of a key
     * await client.set("my_counter", "10");
     * const result = await client.incr("my_counter");
     * console.log(result); // Output: 11
     * ```
     */
    public async incr(key: GlideString): Promise<number> {
        return this.createWritePromise(createIncr(key));
    }

    /** Increments the number stored at `key` by `amount`. If `key` does not exist, it is set to 0 before performing the operation.
     *
     * @see {@link https://valkey.io/commands/incrby/|valkey.io} for details.
     *
     * @param key - The key to increment its value.
     * @param amount - The amount to increment.
     * @returns the value of `key` after the increment.
     *
     * @example
     * ```typescript
     * // Example usage of incrBy method to increment the value of a key by a specified amount
     * await client.set("my_counter", "10");
     * const result = await client.incrBy("my_counter", 5);
     * console.log(result); // Output: 15
     * ```
     */
    public async incrBy(key: GlideString, amount: number): Promise<number> {
        return this.createWritePromise(createIncrBy(key, amount));
    }

    /** Increment the string representing a floating point number stored at `key` by `amount`.
     * By using a negative increment value, the result is that the value stored at `key` is decremented.
     * If `key` does not exist, it is set to 0 before performing the operation.
     *
     * @see {@link https://valkey.io/commands/incrbyfloat/|valkey.io} for details.
     *
     * @param key - The key to increment its value.
     * @param amount - The amount to increment.
     * @returns the value of `key` after the increment.
     *
     * @example
     * ```typescript
     * // Example usage of incrByFloat method to increment the value of a floating point key by a specified amount
     * await client.set("my_float_counter", "10.5");
     * const result = await client.incrByFloat("my_float_counter", 2.5);
     * console.log(result); // Output: 13.0
     * ```
     */
    public async incrByFloat(
        key: GlideString,
        amount: number,
    ): Promise<number> {
        return this.createWritePromise(createIncrByFloat(key, amount));
    }

    /**
     * Copies the value stored at the `source` to the `destination` key. If `destinationDB` is specified,
     * the value will be copied to the database specified, otherwise the current database will be used.
     * When `replace` is true, removes the `destination` key first if it already exists, otherwise performs
     * no action.
     *
     * @see {@link https://valkey.io/commands/copy/|valkey.io} for more details.
     * @remarks Since Valkey version 6.2.0. destinationDB parameter for cluster mode is supported since Valkey 9.0.0 and above
     *
     * @param source - The key to the source value.
     * @param destination - The key where the value should be copied to.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `destinationDB`: the alternative logical database index for the destination key.
     *     If not provided, the current database will be used.
     * - (Optional) `replace`: if `true`, the `destination` key should be removed before copying the
     *     value to it. If not provided, no action will be performed if the key already exists.
     * @returns `true` if `source` was copied, `false` if the `source` was not copied.
     *
     * @example
     * ```typescript
     * const result = await client.copy("set1", "set2");
     * console.log(result); // Output: true - "set1" was copied to "set2".
     * ```
     * ```typescript
     * const result = await client.copy("set1", "set2", { replace: true });
     * console.log(result); // Output: true - "set1" was copied to "set2".
     * ```
     * ```typescript
     * const result = await client.copy("set1", "set2", { destinationDB: 1, replace: false });
     * console.log(result); // Output: true - "set1" was copied to "set2".
     * ```
     */
    public async copy(
        source: GlideString,
        destination: GlideString,
        options?: { destinationDB?: number; replace?: boolean },
    ): Promise<boolean> {
        return this.createWritePromise(
            createCopy(source, destination, options),
        );
    }

    /**
     * Atomically transfers a key from a source Valkey instance to a destination Valkey instance.
     * Once the key is successfully transferred, it is deleted from the source instance
     * unless `copy` is set to `true` in options.
     *
     * @see {@link https://valkey.io/commands/migrate/|valkey.io} for details.
     *
     * @param host - The host of the destination Valkey instance.
     * @param port - The port of the destination Valkey instance.
     * @param key - The key to migrate.
     * @param destinationDB - The database index on the destination instance.
     * @param timeout - The maximum idle time in milliseconds for the bulk-transfer.
     * @param options - Optional migration options.
     * @returns `"OK"` on success, or `"NOKEY"` if the key does not exist.
     *
     * @example
     * ```typescript
     * const result = await client.migrate("127.0.0.1", 6379, "mykey", 0, 5000);
     * console.log(result); // Output: "OK" - "mykey" was migrated to the destination instance.
     * ```
     * @example
     * ```typescript
     * // Migrate with copy (keep source key) and replace (overwrite destination)
     * const result = await client.migrate("127.0.0.1", 6379, "mykey", 0, 5000, { copy: true, replace: true });
     * console.log(result); // Output: "OK" - "mykey" was copied to the destination instance.
     * ```
     */
    public async migrate(
        host: string,
        port: number,
        key: GlideString,
        destinationDB: number,
        timeout: number,
        options?: MigrateOptions,
    ): Promise<string> {
        return this.createWritePromise(
            createMigrate(host, port, key, destinationDB, timeout, options),
        );
    }

    /** Decrements the number stored at `key` by one. If `key` does not exist, it is set to 0 before performing the operation.
     *
     * @see {@link https://valkey.io/commands/decr/|valkey.io} for details.
     *
     * @param key - The key to decrement its value.
     * @returns the value of `key` after the decrement.
     *
     * @example
     * ```typescript
     * // Example usage of decr method to decrement the value of a key by 1
     * await client.set("my_counter", "10");
     * const result = await client.decr("my_counter");
     * console.log(result); // Output: 9
     * ```
     */
    public async decr(key: GlideString): Promise<number> {
        return this.createWritePromise(createDecr(key));
    }

    /** Decrements the number stored at `key` by `amount`. If `key` does not exist, it is set to 0 before performing the operation.
     *
     * @see {@link https://valkey.io/commands/decrby/|valkey.io} for details.
     *
     * @param key - The key to decrement its value.
     * @param amount - The amount to decrement.
     * @returns the value of `key` after the decrement.
     *
     * @example
     * ```typescript
     * // Example usage of decrby method to decrement the value of a key by a specified amount
     * await client.set("my_counter", "10");
     * const result = await client.decrby("my_counter", 5);
     * console.log(result); // Output: 5
     * ```
     */
    public async decrBy(key: GlideString, amount: number): Promise<number> {
        return this.createWritePromise(createDecrBy(key, amount));
    }

    /**
     * Perform a bitwise operation between multiple keys (containing string values) and store the result in the
     * `destination`.
     *
     * @see {@link https://valkey.io/commands/bitop/|valkey.io} for more details.
     * @remarks When in cluster mode, `destination` and all `keys` must map to the same hash slot.
     *
     * @param operation - The bitwise operation to perform.
     * @param destination - The key that will store the resulting string.
     * @param keys - The list of keys to perform the bitwise operation on.
     * @returns The size of the string stored in `destination`.
     *
     * @example
     * ```typescript
     * await client.set("key1", "A"); // "A" has binary value 01000001
     * await client.set("key2", "B"); // "B" has binary value 01000010
     * const result1 = await client.bitop(BitwiseOperation.AND, "destination", ["key1", "key2"]);
     * console.log(result1);
     * // Output: 1
     * // The size of the resulting string stored in "destination" is 1.
     *
     * const result2 = await client.get("destination");
     * console.log(result2);
     * // Output: "@"
     * // "@" has binary value 01000000
     * ```
     */
    public async bitop(
        operation: BitwiseOperation,
        destination: GlideString,
        keys: GlideString[],
    ): Promise<number> {
        return this.createWritePromise(
            createBitOp(operation, destination, keys),
        );
    }

    /**
     * Returns the bit value at `offset` in the string value stored at `key`. `offset` must be greater than or equal
     * to zero.
     *
     * @see {@link https://valkey.io/commands/getbit/|valkey.io} for more details.
     *
     * @param key - The key of the string.
     * @param offset - The index of the bit to return.
     * @returns The bit at the given `offset` of the string. Returns `0` if the key is empty or if the `offset` exceeds
     * the length of the string.
     *
     * @example
     * ```typescript
     * const result = await client.getbit("key", 1);
     * console.log(result);
     * // Output: 1
     * // The second bit of the string stored at `key` is set to 1.
     * ```
     */
    public async getbit(key: GlideString, offset: number): Promise<number> {
        return this.createWritePromise(createGetBit(key, offset));
    }

    /**
     * Sets or clears the bit at `offset` in the string value stored at `key`. The `offset` is a zero-based index, with
     * `0` being the first element of the list, `1` being the next element, and so on. The `offset` must be less than
     * `2^32` and greater than or equal to `0`. If a key is non-existent then the bit at `offset` is set to `value` and
     * the preceding bits are set to `0`.
     *
     * @see {@link https://valkey.io/commands/setbit/|valkey.io} for more details.
     *
     * @param key - The key of the string.
     * @param offset - The index of the bit to be set.
     * @param value - The bit value to set at `offset`. The value must be `0` or `1`.
     * @returns The bit value that was previously stored at `offset`.
     *
     * @example
     * ```typescript
     * const result = await client.setbit("key", 1, 1);
     * console.log(result);
     * // Output: 0
     * // The second bit value was 0 before setting to 1.
     * ```
     */
    public async setbit(
        key: GlideString,
        offset: number,
        value: number,
    ): Promise<number> {
        return this.createWritePromise(createSetBit(key, offset, value));
    }

    /**
     * Returns the position of the first bit matching the given `bit` value. The optional starting offset
     * `start` is a zero-based index, with `0` being the first byte of the list, `1` being the next byte and so on.
     * The offset can also be a negative number indicating an offset starting at the end of the list, with `-1` being
     * the last byte of the list, `-2` being the penultimate, and so on.
     *
     * @see {@link https://valkey.io/commands/bitpos/|valkey.io} for details.
     *
     * @param key - The key of the string.
     * @param bit - The bit value to match. Must be `0` or `1`.
     * @param options - (Optional) The {@link BitOffsetOptions}.
     *
     * @returns The position of the first occurrence of `bit` in the binary value of the string held at `key`.
     *      If `start` was provided, the search begins at the offset indicated by `start`.
     *
     * @example
     * ```typescript
     * await client.set("key1", "A1");  // "A1" has binary value 01000001 00110001
     * const result1 = await client.bitpos("key1", 1);
     * console.log(result1);
     * // Output: 1
     * // The first occurrence of bit value 1 in the string stored at `key1` is at the second position.
     *
     * const result2 = await client.bitpos("key1", 1, { start: -1 });
     * console.log(result2);
     * // Output: 10
     * // The first occurrence of bit value 1, starting at the last byte in the string stored at `key1`, is at the eleventh position.
     *
     * await client.set("key1", "A12");  // "A12" has binary value 01000001 00110001 00110010
     * const result3 = await client.bitpos("key1", 1, { start: 1, end: -1 });
     * console.log(result3);
     * // Output: 10
     * // The first occurrence of bit value 1 in the second byte to the last byte of the string stored at `key1` is at the eleventh position.
     *
     * const result4 = await client.bitpos("key1", 1, { start: 2, end: 9, indexType: BitmapIndexType.BIT });
     * console.log(result4);
     * // Output: 7
     * // The first occurrence of bit value 1 in the third to tenth bits of the string stored at `key1` is at the eighth position.
     * ```
     */
    public async bitpos(
        key: GlideString,
        bit: number,
        options?: BitOffsetOptions,
    ): Promise<number> {
        return this.createWritePromise(createBitPos(key, bit, options));
    }

    /**
     * Reads or modifies the array of bits representing the string that is held at `key` based on the specified
     * `subcommands`.
     *
     * @see {@link https://valkey.io/commands/bitfield/|valkey.io} for more details.
     *
     * @param key - The key of the string.
     * @param subcommands - The subcommands to be performed on the binary value of the string at `key`, which could be
     *      any of the following:
     *
     * - {@link BitFieldGet}
     * - {@link BitFieldSet}
     * - {@link BitFieldIncrBy}
     * - {@link BitFieldOverflow}
     *
     * @returns An array of results from the executed subcommands:
     *
     * - {@link BitFieldGet} returns the value in {@link BitOffset} or {@link BitOffsetMultiplier}.
     * - {@link BitFieldSet} returns the old value in {@link BitOffset} or {@link BitOffsetMultiplier}.
     * - {@link BitFieldIncrBy} returns the new value in {@link BitOffset} or {@link BitOffsetMultiplier}.
     * - {@link BitFieldOverflow} determines the behavior of the {@link BitFieldSet} and {@link BitFieldIncrBy}
     *   subcommands when an overflow or underflow occurs. {@link BitFieldOverflow} does not return a value and
     *   does not contribute a value to the array response.
     *
     * @example
     * ```typescript
     * await client.set("key", "A");  // "A" has binary value 01000001
     * const result = await client.bitfield("key", [new BitFieldSet(new UnsignedEncoding(2), new BitOffset(1), 3), new BitFieldGet(new UnsignedEncoding(2), new BitOffset(1))]);
     * console.log(result);
     * // Output: [2, 3]
     * // The old value at offset 1 with an unsigned encoding of 2 was 2. The new value at offset 1 with an unsigned encoding of 2 is 3.
     * ```
     */
    public async bitfield(
        key: GlideString,
        subcommands: BitFieldSubCommands[],
    ): Promise<(number | null)[]> {
        return this.createWritePromise(createBitField(key, subcommands));
    }

    /**
     * Reads the array of bits representing the string that is held at `key` based on the specified `subcommands`.
     *
     * @see {@link https://valkey.io/commands/bitfield_ro/|valkey.io} for more details.
     * @remarks Since Valkey version 6.0.0.
     *
     * @param key - The key of the string.
     * @param subcommands - The {@link BitFieldGet} subcommands to be performed.
     * @returns An array of results from the {@link BitFieldGet} subcommands.
     *
     * @example
     * ```typescript
     * await client.set("key", "A");  // "A" has binary value 01000001
     * const result = await client.bitfieldReadOnly("key", [new BitFieldGet(new UnsignedEncoding(2), new BitOffset(1))]);
     * console.log(result);
     * // Output: [2]
     * // The value at offset 1 with an unsigned encoding of 2 is 2.
     * ```
     */
    public async bitfieldReadOnly(
        key: GlideString,
        subcommands: BitFieldGet[],
    ): Promise<number[]> {
        return this.createWritePromise(createBitField(key, subcommands, true));
    }

    /** Retrieve the value associated with `field` in the hash stored at `key`.
     *
     * @see {@link https://valkey.io/commands/hget/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param field - The field in the hash stored at `key` to retrieve from the database.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns the value associated with `field`, or null when `field` is not present in the hash or `key` does not exist.
     *
     * @example
     * ```typescript
     * // Example usage of the hget method on an-existing field
     * await client.hset("my_hash", {"field": "value"});
     * const result = await client.hget("my_hash", "field");
     * console.log(result);
     * // Output: "value"
     * // The value associated with `field` in the key `my_hash`.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the hget method on a non-existing field
     * const result = await client.hget("my_hash", "nonexistent_field");
     * console.log(result);
     * // Output: null
     * // Indicates non existent key.
     * ```
     */
    public async hget(
        key: GlideString,
        field: GlideString,
        options?: DecoderOption,
    ): Promise<GlideString | null> {
        return this.createWritePromise(createHGet(key, field), options);
    }

    /** Sets the specified fields to their respective values in the hash stored at `key`.
     *
     * @see {@link https://valkey.io/commands/hset/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param fieldsAndValues - A list of field names and their values.
     * @returns The number of fields that were added.
     *
     * @example
     * ```typescript
     * // Example usage of the hset method using HashDataType as input type
     * const result = await client.hset("my_hash", [{"field": "field1", "value": "value1"}, {"field": "field2", "value": "value2"}]);
     * console.log(result);
     * // Output: 2
     * // Indicates that 2 fields were successfully set in the hash `my_hash`.
     *
     * // Example usage of the hset method using Record<string, GlideString> as input
     * const result = await client.hset("my_hash", {"field1": "value", "field2": "value2"});
     * console.log(result);
     * // Output: 2
     * // Indicates that 2 fields were successfully set in the hash `my_hash`.
     * ```
     */
    public async hset(
        key: GlideString,
        fieldsAndValues: HashDataType | Record<string, GlideString>,
    ): Promise<number> {
        return this.createWritePromise(
            createHSet(
                key,
                convertFieldsAndValuesToHashDataType(fieldsAndValues),
            ),
        );
    }

    /**
     * Returns all field names in the hash stored at `key`.
     *
     * @see {@link https://valkey.io/commands/hkeys/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A list of field names for the hash, or an empty list when the key does not exist.
     *
     * @example
     * ```typescript
     * // Example usage of the hkeys method:
     * await client.hset("my_hash", {"field1": "value1", "field2": "value2", "field3": "value3"});
     * const result = await client.hkeys("my_hash");
     * console.log(result);
     * // Output: ["field1", "field2", "field3"]
     * // Returns all the field names stored in the hash `my_hash`.
     * ```
     */

    public async hkeys(
        key: GlideString,
        options?: DecoderOption,
    ): Promise<GlideString[]> {
        return this.createWritePromise(createHKeys(key), options);
    }

    /** Sets `field` in the hash stored at `key` to `value`, only if `field` does not yet exist.
     * If `key` does not exist, a new key holding a hash is created.
     * If `field` already exists, this operation has no effect.
     *
     * @see {@link https://valkey.io/commands/hsetnx/|valkey.io} for more details.
     *
     * @param key - The key of the hash.
     * @param field - The field to set the value for.
     * @param value - The value to set.
     * @returns `true` if the field was set, `false` if the field already existed and was not set.
     *
     * @example
     * ```typescript
     * // Example usage of the hsetnx method
     * const result = await client.hsetnx("my_hash", "field", "value");
     * console.log(result);
     * // Output: true
     * // Indicates that the field "field" was set successfully in the hash `my_hash`.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the hsetnx method on a field that already exists
     * const result = await client.hsetnx("my_hash", "field", "new_value");
     * console.log(result);
     * // Output: false
     * // Indicates that the field `field` already existed in the hash `my_hash` and was not set again.
     * ```
     */
    public async hsetnx(
        key: GlideString,
        field: GlideString,
        value: GlideString,
    ): Promise<boolean> {
        return this.createWritePromise(createHSetNX(key, field, value));
    }

    /** Removes the specified fields from the hash stored at `key`.
     * Specified fields that do not exist within this hash are ignored.
     *
     * @see {@link https://valkey.io/commands/hdel/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param fields - The fields to remove from the hash stored at `key`.
     * @returns the number of fields that were removed from the hash, not including specified but non existing fields.
     * If `key` does not exist, it is treated as an empty hash and it returns 0.
     *
     * @example
     * ```typescript
     * // Example usage of the hdel method
     * const result = await client.hdel("my_hash", ["field1", "field2"]);
     * console.log(result);
     * // Output: 2
     * // Indicates that two fields were successfully removed from the hash.
     * ```
     */
    public async hdel(
        key: GlideString,
        fields: GlideString[],
    ): Promise<number> {
        return this.createWritePromise(createHDel(key, fields));
    }

    /** Returns the values associated with the specified fields in the hash stored at `key`.
     *
     * @see {@link https://valkey.io/commands/hmget/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param fields - The fields in the hash stored at `key` to retrieve from the database.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns a list of values associated with the given fields, in the same order as they are requested.
     * For every field that does not exist in the hash, a null value is returned.
     * If `key` does not exist, it is treated as an empty hash and it returns a list of null values.
     *
     * @example
     * ```typescript
     * // Example usage of the hmget method
     * const result = await client.hmget("my_hash", ["field1", "field2"]);
     * console.log(result);
     * // Output: ["value1", "value2"]
     * // A list of values associated with the specified fields.
     * ```
     */
    public async hmget(
        key: GlideString,
        fields: GlideString[],
        options?: DecoderOption,
    ): Promise<(GlideString | null)[]> {
        return this.createWritePromise(createHMGet(key, fields), options);
    }

    /** Returns if `field` is an existing field in the hash stored at `key`.
     *
     * @see {@link https://valkey.io/commands/hexists/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param field - The field to check in the hash stored at `key`.
     * @returns `true` the hash contains `field`. If the hash does not contain `field`, or if `key` does not exist, it returns `false`.
     *
     * @example
     * ```typescript
     * // Example usage of the hexists method with existing field
     * const result = await client.hexists("my_hash", "field1");
     * console.log(result);
     * // Output: true
     * // Returns true because `my_hash` hash contains `field1` field.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the hexists method with non-existing field
     * const result = await client.hexists("my_hash", "nonexistent_field");
     * console.log(result);
     * // Output: false
     * // Returns false because `my_hash` hash does not contain `nonexistent_field` field.
     * ```
     */
    public async hexists(
        key: GlideString,
        field: GlideString,
    ): Promise<boolean> {
        return this.createWritePromise(createHExists(key, field));
    }

    /**
     * Returns all fields and values of the hash stored at `key`.
     *
     * @see {@link https://valkey.io/commands/hgetall/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A list of fields and their values stored in the hash.
     * If `key` does not exist, it returns an empty list.
     *
     * @example
     * ```typescript
     * // Example usage of the hgetall method
     * const result = await client.hgetall("my_hash");
     * console.log(result);
     * // Output: all fields and values stored at `my_hash`
     * // [
     * //     { field: "field1", value: "value1"},
     * //     { field: "field2", value: "value2"}
     * // ]
     * ```
     */
    public async hgetall(
        key: GlideString,
        options?: DecoderOption,
    ): Promise<HashDataType> {
        return this.createWritePromise<GlideRecord<GlideString>>(
            createHGetAll(key),
            options,
        ).then((res) =>
            res.map((r) => {
                return { field: r.key, value: r.value };
            }),
        );
    }

    /** Increments the number stored at `field` in the hash stored at `key` by increment.
     * By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
     * If `field` or `key` does not exist, it is set to 0 before performing the operation.
     *
     * @see {@link https://valkey.io/commands/hincrby/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param amount - The amount to increment.
     * @param field - The field in the hash stored at `key` to increment its value.
     * @returns the value of `field` in the hash stored at `key` after the increment.
     *
     * @example
     * ```typescript
     * // Example usage of the hincrby method to increment the value in a hash by a specified amount
     * const result = await client.hincrby("my_hash", "field1", 5);
     * console.log(result);
     * // Output: 5
     * // Increments the value stored at hash field `field1` by 5
     * ```
     */
    public async hincrBy(
        key: GlideString,
        field: GlideString,
        amount: number,
    ): Promise<number> {
        return this.createWritePromise(createHIncrBy(key, field, amount));
    }

    /** Increment the string representing a floating point number stored at `field` in the hash stored at `key` by increment.
     * By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
     * If `field` or `key` does not exist, it is set to 0 before performing the operation.
     *
     * @see {@link https://valkey.io/commands/hincrbyfloat/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param amount - The amount to increment.
     * @param field - The field in the hash stored at `key` to increment its value.
     * @returns the value of `field` in the hash stored at `key` after the increment.
     *
     * @example
     * ```typescript
     * // Example usage of the hincrbyfloat method to increment the value of a floating point in a hash by a specified amount
     * const result = await client.hincrbyfloat("my_hash", "field1", 2.5);
     * console.log(result);
     * // Output: 2.5
     * // Increments the value stored at hash field `field1` by 2.5
     * ```
     */
    public async hincrByFloat(
        key: GlideString,
        field: GlideString,
        amount: number,
    ): Promise<number> {
        return this.createWritePromise(createHIncrByFloat(key, field, amount));
    }

    /** Returns the number of fields contained in the hash stored at `key`.
     *
     * @see {@link https://valkey.io/commands/hlen/|valkey.io} for more details.
     *
     * @param key - The key of the hash.
     * @returns The number of fields in the hash, or 0 when the key does not exist.
     *
     * @example
     * ```typescript
     * // Example usage of the hlen method with an existing key
     * const result = await client.hlen("my_hash");
     * console.log(result);
     * // Output: 3
     * // Returns the number of fields for the hash stored at key `my_hash`.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the hlen method with a non-existing key
     * const result = await client.hlen("non_existing_key");
     * console.log(result);
     * // Output: 0
     * // Returns 0 for non-existent key.
     * ```
     */
    public async hlen(key: GlideString): Promise<number> {
        return this.createWritePromise(createHLen(key));
    }

    /** Returns all values in the hash stored at key.
     *
     * @see {@link https://valkey.io/commands/hvals/|valkey.io} for more details.
     *
     * @param key - The key of the hash.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns a list of values in the hash, or an empty list when the key does not exist.
     *
     * @example
     * ```typescript
     * // Example usage of the hvals method
     * const result = await client.hvals("my_hash");
     * console.log(result);
     * // Output: ["value1", "value2", "value3"]
     * // Returns all the values stored in the hash `my_hash`.
     * ```
     */
    public async hvals(
        key: GlideString,
        options?: DecoderOption,
    ): Promise<GlideString[]> {
        return this.createWritePromise(createHVals(key), options);
    }

    /**
     * Returns the string length of the value associated with `field` in the hash stored at `key`.
     *
     * @see {@link https://valkey.io/commands/hstrlen/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param field - The field in the hash.
     * @returns The string length or `0` if `field` or `key` does not exist.
     *
     * @example
     * ```typescript
     * await client.hset("my_hash", {"field": "value"});
     * const result = await client.hstrlen("my_hash", "field");
     * console.log(result);
     * // Output: 5
     * // Returns the string length of `value` which is the value associated with the field `field` stored at key `my_hash`.
     * ```
     */
    public async hstrlen(
        key: GlideString,
        field: GlideString,
    ): Promise<number> {
        return this.createWritePromise(createHStrlen(key, field));
    }

    /**
     * Returns a random field name from the hash value stored at `key`.
     *
     * @see {@link https://valkey.io/commands/hrandfield/|valkey.io} for more details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param key - The key of the hash.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A random field name from the hash stored at `key`, or `null` when
     *     the key does not exist.
     *
     * @example
     * ```typescript
     * const result = await client.hrandfield("myHash")
     * console.log(result);
     * // Output: 'field'
     * // Returns a random field stored at the key `my_hash`.
     * ```
     */
    public async hrandfield(
        key: GlideString,
        options?: DecoderOption,
    ): Promise<GlideString | null> {
        return this.createWritePromise(createHRandField(key), options);
    }

    /**
     * Iterates incrementally over a hash.
     *
     * @see {@link https://valkey.io/commands/hscan/|valkey.io} for more details.
     *
     * @param key - The key of the set.
     * @param cursor - The cursor that points to the next iteration of results. A value of `"0"` indicates the start of the search.
     * @param options - (Optional) See {@link HScanOptions} and {@link DecoderOption}.
     * @returns An array of the `cursor` and the subset of the hash held by `key`.
     * The first element is always the `cursor` for the next iteration of results. `"0"` will be the `cursor`
     * returned on the last iteration of the hash. The second element is always an array of the subset of the
     * hash held in `key`. The array in the second element is a flattened series of string pairs,
     * where the value is at even indices and the value is at odd indices.
     * If `options.noValues` is set to `true`, the second element will only contain the fields without the values.
     *
     * @example
     * ```typescript
     * // Assume "key" contains a hash with multiple members
     * let newCursor = "0";
     * let result = [];
     * do {
     *      result = await client.hscan(key1, newCursor, {
     *          match: "*",
     *          count: 3,
     *      });
     *      newCursor = result[0];
     *      console.log("Cursor: ", newCursor);
     *      console.log("Members: ", result[1]);
     * } while (newCursor !== "0");
     * // The output of the code above is something similar to:
     * // Cursor:  31  // The cursor after the first interation.
     * // Members:  ['field 79', 'value 79', 'field 20', 'value 20', 'field 115', 'value 115'] // First 3 hash field-value pairs stored at the key `key1`
     * // Cursor:  39 // The cursor after the second interation.
     * // Members:  ['field 63', 'value 63', 'field 293', 'value 293', 'field 162', 'value 162'] // The next 3 hash field-value pairs at key `key1`
     * // Cursor:  0 // The cursor after the last batch of elements is fetched.
     * // Members:  ['field 55', 'value 55', 'field 24', 'value 24', 'field 90', 'value 90', 'field 113', 'value 113']
     * // You can get more than `count` elements in the result set. Read the count documentation for more information.
     * ```
     * @example
     * ```typescript
     * // Hscan with noValues
     * let newCursor = "0";
     * let result = [];
     * do {
     *      result = await client.hscan(key1, newCursor, {
     *          match: "*",
     *          count: 3,
     *          noValues: true,
     *      });
     *      newCursor = result[0];
     *      console.log("Cursor: ", newCursor);
     *      console.log("Members: ", result[1]);
     * } while (newCursor !== "0");
     * // The output of the code above is something similar to:
     * // Cursor:  31 // The cursor after the first interation.
     * // Members:  ['field 79', 'field 20', 'field 115'] // First 3 hash fields stored at the key `key1`
     * // Cursor:  39 // The cursor after the second interation.
     * // Members:  ['field 63', 'field 293', 'field 162'] // Next 3 hash fields stored at the key `key1`
     * // Cursor:  0 // The cursor after the last batch of elements is fetched.
     * // Members:  ['field 55', 'field 24', 'field 90', 'field 113']
     * // You can get more than `count` elements in the result set. Read the count documentation for more information.
     * ```
     */
    public async hscan(
        key: GlideString,
        cursor: string,
        options?: HScanOptions & DecoderOption,
    ): Promise<[string, GlideString[]]> {
        return this.createWritePromise<[GlideString, GlideString[]]>(
            createHScan(key, cursor, options),
            options,
        ).then((res) => [res[0].toString(), res[1]]); // convert cursor back to string
    }

    /**
     * Retrieves up to `count` random field names from the hash value stored at `key`.
     *
     * @see {@link https://valkey.io/commands/hrandfield/|valkey.io} for more details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param key - The key of the hash.
     * @param count - The number of field names to return.
     *     If `count` is positive, returns unique elements.
     *     If negative, allows for duplicates.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns An `array` of random field names from the hash stored at `key`,
     *     or an `empty array` when the key does not exist.
     *
     * @example
     * ```typescript
     * result = await client.hrandfieldCount("my_hash", 2)
     * console.log(result);
     * // Output: ['field1', 'field2']
     * // Returns 2 random fields from the hash stored at key `my_hash`.
     * ```
     */
    public async hrandfieldCount(
        key: GlideString,
        count: number,
        options?: DecoderOption,
    ): Promise<GlideString[]> {
        return this.createWritePromise(createHRandField(key, count), options);
    }

    /**
     * Retrieves up to `count` random field names along with their values from the hash
     * value stored at `key`.
     *
     * @see {@link https://valkey.io/commands/hrandfield/|valkey.io} for more details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param key - The key of the hash.
     * @param count - The number of field names to return.
     *     If `count` is positive, returns unique elements.
     *     If negative, allows for duplicates.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A 2D `array` of `[fieldName, value]` `arrays`, where `fieldName` is a random
     *     field name from the hash and `value` is the associated value of the field name.
     *     If the hash does not exist or is empty, the response will be an empty `array`.
     *
     * @example
     * ```typescript
     * const result = await client.hrandfieldCountWithValues("myHash", 2);
     * console.log(result);
     * // Output: [['field1', 'value1'], ['field2', 'value2']]
     * // Returns 2 random field-value pairs from the hash stored at key `my_hash`.
     * ```
     */
    public async hrandfieldWithValues(
        key: GlideString,
        count: number,
        options?: DecoderOption,
    ): Promise<[GlideString, GlideString][]> {
        return this.createWritePromise(
            createHRandField(key, count, true),
            options,
        );
    }

    /**
     * Sets hash fields with expiration times and optional conditional changes.
     *
     * @param key - The key of the hash.
     * @param fieldsAndValues - A map or array of field-value pairs to set.
     * @param options - Optional parameters including field conditional changes and expiry settings.
     *                  See {@link HSetExOptions}.
     * @returns A number of fields that were set.
     *
     * @example
     * ```typescript
     * // Set fields with 60 second expiration, only if none exist
     * const result = await client.hsetex(
     *     "myHash",
     *     { field1: "value1", field2: "value2" },
     *     {
     *         fieldConditionalChange: HashFieldConditionalChange.ONLY_IF_NONE_EXIST,
     *         expiry: { type: TimeUnit.Seconds, count: 60 }
     *     }
     * );
     * console.log(result); // 2 - both fields were set with 60s expiration
     *
     * // Set fields and keep existing TTL
     * const keepTtlResult = await client.hsetex(
     *     "myHash",
     *     { field3: "value3" },
     *     { expiry: "KEEPTTL" }
     * );
     * console.log(keepTtlResult); // 1 - field was set, existing TTL preserved
     *
     * // Set fields with millisecond precision expiration
     * const msResult = await client.hsetex(
     *     "myHash",
     *     { field4: "value4" },
     *     { expiry: { type: TimeUnit.Milliseconds, count: 5000 } }
     * );
     * console.log(msResult); // 1 - field expires in 5000ms
     *
     * // Set fields with Unix timestamp expiration
     * const timestampResult = await client.hsetex(
     *     "myHash",
     *     { field5: "value5" },
     *     { expiry: { type: TimeUnit.UnixSeconds, count: Math.floor(Date.now() / 1000) + 3600 } }
     * );
     * console.log(timestampResult); // 1 - field expires in 1 hour
     *
     * // Only update existing fields and keep their TTL
     * const updateResult = await client.hsetex(
     *     "myHash",
     *     { field1: "newValue1" },
     *     {
     *         fieldConditionalChange: HashFieldConditionalChange.ONLY_IF_ALL_EXIST,
     *         expiry: "KEEPTTL"
     *     }
     * );
     * console.log(updateResult); // 0 or 1 depending on whether field1 exists
     * ```
     *
     * @since Valkey 9.0.0
     * @see {@link https://valkey.io/commands/hsetex/|valkey.io}
     */
    public async hsetex(
        key: GlideString,
        fieldsAndValues: HashDataType | Record<string, GlideString>,
        options?: HSetExOptions,
    ): Promise<number> {
        return this.createWritePromise(
            createHSetEx(
                key,
                convertFieldsAndValuesToHashDataType(fieldsAndValues),
                options,
            ),
        );
    }

    /**
     * Gets hash fields and optionally sets their expiration.
     *
     * @param key - The key of the hash.
     * @param fields - The fields in the hash stored at `key` to retrieve from the database.
     * @param options - Optional arguments for the HGETEX command. See {@link HGetExOptions} and see {@link DecoderOption}.
     * @returns An array of values associated with the given fields,
     *          in the same order as they are requested. For every field that does not exist
     *          in the hash, a null value is returned. If `key` does not exist, returns an
     *          array of null values.
     *
     * @example
     * ```typescript
     * // Get fields without setting expiration
     * const values = await client.hgetex("myHash", ["field1", "field2"]);
     * console.log(values); // ["value1", "value2"] or [null, null] if fields don't exist
     *
     * // Get fields and set 30 second expiration
     * const valuesWithExpiry = await client.hgetex(
     *     "myHash",
     *     ["field1", "field2"],
     *     { expiry: { type: TimeUnit.Seconds, count: 30 } }
     * );
     * console.log(valuesWithExpiry); // ["value1", "value2"] - fields now expire in 30s
     *
     * // Get fields and remove expiration (make persistent)
     * const persistValues = await client.hgetex(
     *     "myHash",
     *     ["field1", "field2"],
     *     { expiry: "PERSIST" }
     * );
     * console.log(persistValues); // ["value1", "value2"] - fields are now persistent
     *
     * // Get fields and set millisecond precision expiration
     * const msValues = await client.hgetex(
     *     "myHash",
     *     ["field3", "field4"],
     *     { expiry: { type: TimeUnit.Milliseconds, count: 2500 } }
     * );
     * console.log(msValues); // ["value3", "value4"] - fields expire in 2500ms
     *
     * // Get fields and set Unix timestamp expiration
     * const timestampValues = await client.hgetex(
     *     "myHash",
     *     ["field5"],
     *     { expiry: { type: TimeUnit.UnixMilliseconds, count: Date.now() + 60000 } }
     * );
     * console.log(timestampValues); // ["value5"] - field expires in 1 minute
     * ```
     *
     * @since Valkey 9.0.0
     * @see {@link https://valkey.io/commands/hgetex/|valkey.io}
     */
    public async hgetex(
        key: GlideString,
        fields: GlideString[],
        options?: HGetExOptions & DecoderOption,
    ): Promise<(GlideString | null)[]> {
        return this.createWritePromise(
            createHGetEx(key, fields, options),
            options,
        );
    }

    /**
     * Sets expiration time for hash fields in seconds. Creates the hash if it doesn't exist.
     *
     * @see {@link https://valkey.io/commands/hexpire/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param seconds - The expiration time in seconds.
     * @param fields - The fields to set expiration for.
     * @param options - Optional parameters for the command.
     * @returns An array of numbers indicating the result for each field:
     *          - `1` if expiration was set successfully
     *          - `0` if the specified condition (NX, XX, GT, LT) was not met
     *          - `-2` if the field does not exist or the key does not exist
     *          - `2` when called with 0 seconds (field deleted)
     *
     * @example
     * ```typescript
     * // Set expiration for hash fields
     * const result = await client.hexpire("my_hash", 60, ["field1", "field2"]);
     * console.log(result); // [1, 1] - expiration set for both fields
     * ```
     *
     * @example
     * ```typescript
     * // Set expiration only if fields don't have expiration
     * const result = await client.hexpire("my_hash", 120, ["field1", "field2"], {
     *     condition: HashExpirationCondition.ONLY_IF_NO_EXPIRY
     * });
     * console.log(result); // [1, 0] - expiration set for field1, condition not met for field2
     * ```
     *
     * @example
     * ```typescript
     * // Set expiration with greater than condition
     * const result = await client.hexpire("my_hash", 300, ["field1"], {
     *     condition: HashExpirationCondition.ONLY_IF_GREATER_THAN_CURRENT
     * });
     * console.log(result); // [1] - expiration set because 300 > current TTL
     * ```
     */
    public async hexpire(
        key: GlideString,
        seconds: number,
        fields: GlideString[],
        options?: HExpireOptions,
    ): Promise<number[]> {
        return this.createWritePromise(
            createHExpire(key, seconds, fields, options),
        );
    }

    /**
     * Removes the expiration time associated with each specified field, causing them to persist.
     *
     * @see {@link https://valkey.io/commands/hpersist/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param fields - The fields in the hash to remove expiration from.
     * @returns An array of numbers indicating the result for each field:
     *     - `1` if the field's expiration was removed successfully.
     *     - `-1` if the field exists but has no associated expiration.
     *     - `-2` if the field does not exist or the key does not exist.
     *
     * @example
     * ```typescript
     * // Remove expiration from hash fields
     * const result = await client.hpersist("my_hash", ["field1", "field2"]);
     * console.log(result); // [1, -1] - expiration removed from field1, field2 had no expiration
     * ```
     *
     * @example
     * ```typescript
     * // Remove expiration from a single field
     * const result = await client.hpersist("my_hash", ["field1"]);
     * console.log(result); // [1] - expiration removed from field1
     * ```
     */
    public async hpersist(
        key: GlideString,
        fields: GlideString[],
    ): Promise<number[]> {
        return this.createWritePromise(createHPersist(key, fields));
    }

    /**
     * Sets expiration time for hash fields in milliseconds. Creates the hash if it doesn't exist.
     *
     * @see {@link https://valkey.io/commands/hpexpire/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param milliseconds - The expiration time in milliseconds.
     * @param fields - The fields to set expiration for.
     * @param options - Optional arguments for the HPEXPIRE command. See {@link HPExpireOptions}.
     * @returns An array of numbers indicating the result for each field:
     *          - `1` if expiration was set successfully
     *          - `0` if the specified condition (NX, XX, GT, LT) was not met
     *          - `-2` if the field does not exist or the key does not exist
     *          - `2` when called with 0 milliseconds (field deleted)
     *
     * @example
     * ```typescript
     * // Set expiration for hash fields in milliseconds
     * const result = await client.hpexpire("my_hash", 60000, ["field1", "field2"]);
     * console.log(result); // [1, 1] - expiration set for both fields
     * ```
     *
     * @example
     * ```typescript
     * // Set expiration only if fields don't have expiration
     * const result = await client.hpexpire("my_hash", 120000, ["field1", "field2"], {
     *     condition: HashExpirationCondition.ONLY_IF_NO_EXPIRY
     * });
     * console.log(result); // [1, 0] - expiration set for field1, condition not met for field2
     * ```
     *
     * @example
     * ```typescript
     * // Set expiration with greater than condition
     * const result = await client.hpexpire("my_hash", 300000, ["field1"], {
     *     condition: HashExpirationCondition.ONLY_IF_GREATER_THAN_CURRENT
     * });
     * console.log(result); // [1] - expiration set because 300000 ms > current TTL
     * ```
     */
    public async hpexpire(
        key: GlideString,
        milliseconds: number,
        fields: GlideString[],
        options?: HExpireOptions,
    ): Promise<number[]> {
        return this.createWritePromise(
            createHPExpire(key, milliseconds, fields, options),
        );
    }

    /**
     * Sets expiration time for hash fields using an absolute Unix timestamp in seconds. Creates the hash if it doesn't exist.
     *
     * @see {@link https://valkey.io/commands/hexpireat/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param unixTimestampSeconds - The expiration time as a Unix timestamp in seconds.
     * @param fields - The fields to set expiration for.
     * @param options - Optional arguments for the HEXPIREAT command. See {@link HExpireOptions}.
     * @returns An array of numbers indicating the result for each field:
     *          - `1` if expiration was set successfully
     *          - `0` if the specified condition (NX, XX, GT, LT) was not met
     *          - `-2` if the field does not exist or the key does not exist
     *          - `2` when called with 0 seconds (field deleted)
     *
     * @example
     * ```typescript
     * // Set expiration for hash fields using Unix timestamp
     * const futureTimestamp = Math.floor(Date.now() / 1000) + 3600; // 1 hour from now
     * const result = await client.hexpireat("my_hash", futureTimestamp, ["field1", "field2"]);
     * console.log(result); // [1, 1] - expiration set for both fields
     * ```
     *
     * @example
     * ```typescript
     * // Set expiration only if fields don't have expiration
     * const futureTimestamp = Math.floor(Date.now() / 1000) + 7200; // 2 hours from now
     * const result = await client.hexpireat("my_hash", futureTimestamp, ["field1", "field2"], {
     *     condition: HashExpirationCondition.ONLY_IF_NO_EXPIRY
     * });
     * console.log(result); // [1, 0] - expiration set for field1, condition not met for field2
     * ```
     *
     * @example
     * ```typescript
     * // Set expiration with greater than condition
     * const futureTimestamp = Math.floor(Date.now() / 1000) + 10800; // 3 hours from now
     * const result = await client.hexpireat("my_hash", futureTimestamp, ["field1"], {
     *     condition: HashExpirationCondition.ONLY_IF_GREATER_THAN_CURRENT
     * });
     * console.log(result); // [1] - expiration set because timestamp > current expiration
     * ```
     */
    public async hexpireat(
        key: GlideString,
        unixTimestampSeconds: number,
        fields: GlideString[],
        options?: HExpireOptions,
    ): Promise<number[]> {
        return this.createWritePromise(
            createHExpireAt(key, unixTimestampSeconds, fields, options),
        );
    }

    /**
     * Sets expiration time for hash fields using an absolute Unix timestamp in milliseconds. Creates the hash if it doesn't exist.
     *
     * @see {@link https://valkey.io/commands/hpexpireat/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param unixTimestampMilliseconds - The expiration time as a Unix timestamp in milliseconds.
     * @param fields - The fields to set expiration for.
     * @param options - Optional arguments for the HPEXPIREAT command. See {@link HExpireOptions}.
     * @returns An array of numbers indicating the result for each field:
     *          - `1` if expiration was set successfully
     *          - `0` if the specified condition (NX, XX, GT, LT) was not met
     *          - `-2` if the field does not exist or the key does not exist
     *          - `2` when called with 0 milliseconds (field deleted)
     *
     * @example
     * ```typescript
     * // Set expiration for hash fields using Unix timestamp in milliseconds
     * const futureTimestamp = Date.now() + 3600000; // 1 hour from now
     * const result = await client.hpexpireat("my_hash", futureTimestamp, ["field1", "field2"]);
     * console.log(result); // [1, 1] - expiration set for both fields
     * ```
     *
     * @example
     * ```typescript
     * // Set expiration only if fields don't have expiration
     * const futureTimestamp = Date.now() + 7200000; // 2 hours from now
     * const result = await client.hpexpireat("my_hash", futureTimestamp, ["field1", "field2"], {
     *     condition: HashExpirationCondition.ONLY_IF_NO_EXPIRY
     * });
     * console.log(result); // [1, 0] - expiration set for field1, condition not met for field2
     * ```
     *
     * @example
     * ```typescript
     * // Set expiration with greater than condition
     * const futureTimestamp = Date.now() + 10800000; // 3 hours from now
     * const result = await client.hpexpireat("my_hash", futureTimestamp, ["field1"], {
     *     condition: HashExpirationCondition.ONLY_IF_GREATER_THAN_CURRENT
     * });
     * console.log(result); // [1] - expiration set because timestamp > current expiration
     * ```
     */
    public async hpexpireat(
        key: GlideString,
        unixTimestampMilliseconds: number,
        fields: GlideString[],
        options?: HExpireOptions,
    ): Promise<number[]> {
        return this.createWritePromise(
            createHPExpireAt(key, unixTimestampMilliseconds, fields, options),
        );
    }

    /**
     * Returns the remaining time to live of hash fields that have a timeout, in seconds.
     *
     * @see {@link https://valkey.io/commands/httl/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param fields - The fields in the hash stored at `key` to retrieve the TTL for.
     * @returns An array of TTL values in seconds for the specified fields.
     *     - For fields with a timeout, returns the remaining time in seconds.
     *     - For fields that exist but have no associated expire, returns -1.
     *     - For fields that do not exist, returns -2.
     *
     * @example
     * ```typescript
     * // Get TTL for hash fields
     * const result = await client.httl("my_hash", ["field1", "field2", "field3"]);
     * console.log(result); // [120, -1, -2] - field1 expires in 120 seconds, field2 has no expiration, field3 doesn't exist
     * ```
     *
     * @example
     * ```typescript
     * // Get TTL for a single field
     * const result = await client.httl("my_hash", ["field1"]);
     * console.log(result); // [60] - field1 expires in 60 seconds
     * ```
     */
    public async httl(
        key: GlideString,
        fields: GlideString[],
    ): Promise<number[]> {
        return this.createWritePromise(createHTtl(key, fields));
    }

    /**
     * Returns the absolute Unix timestamp (in seconds) at which hash fields will expire.
     *
     * @see {@link https://valkey.io/commands/hexpiretime/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param fields - The list of fields to get the expiration timestamp for.
     * @returns An array of expiration timestamps in seconds for the specified fields:
     *   - For fields with a timeout, returns the absolute Unix timestamp in seconds.
     *   - For fields without a timeout, returns -1.
     *   - For fields that do not exist, returns -2.
     *
     * @example
     * ```typescript
     * // Get expiration timestamps for hash fields
     * const result = await client.hexpiretime("my_hash", ["field1", "field2", "field3"]);
     * console.log(result); // [1672531200, -1, -2] - field1 expires at timestamp 1672531200, field2 has no expiration, field3 doesn't exist
     * ```
     *
     * @example
     * ```typescript
     * // Get expiration timestamp for a single field
     * const result = await client.hexpiretime("my_hash", ["field1"]);
     * console.log(result); // [1672531200] - field1 expires at timestamp 1672531200
     * ```
     */
    public async hexpiretime(
        key: GlideString,
        fields: GlideString[],
    ): Promise<number[]> {
        return this.createWritePromise(createHExpireTime(key, fields));
    }

    /**
     * Returns the absolute Unix timestamp (in milliseconds) at which hash fields will expire.
     *
     * @see {@link https://valkey.io/commands/hpexpiretime/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param fields - The list of fields to get the expiration timestamp for.
     * @returns An array of expiration timestamps in milliseconds for the specified fields:
     *   - For fields with a timeout, returns the absolute Unix timestamp in milliseconds.
     *   - For fields without a timeout, returns -1.
     *   - For fields that do not exist, returns -2.
     *
     * @example
     * ```typescript
     * // Get expiration timestamps for hash fields in milliseconds
     * const result = await client.hpexpiretime("my_hash", ["field1", "field2", "field3"]);
     * console.log(result); // [1672531200000, -1, -2] - field1 expires at timestamp 1672531200000, field2 has no expiration, field3 doesn't exist
     * ```
     *
     * @example
     * ```typescript
     * // Get expiration timestamp for a single field in milliseconds
     * const result = await client.hpexpiretime("my_hash", ["field1"]);
     * console.log(result); // [1672531200000] - field1 expires at timestamp 1672531200000
     * ```
     */
    public async hpexpiretime(
        key: GlideString,
        fields: GlideString[],
    ): Promise<number[]> {
        return this.createWritePromise(createHPExpireTime(key, fields));
    }

    /**
     * Returns the remaining time to live of hash fields that have a timeout, in milliseconds.
     *
     * @see {@link https://valkey.io/commands/hpttl/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param fields - The list of fields to get the TTL for.
     * @returns An array of TTL values in milliseconds for the specified fields:
     *   - For fields with a timeout, returns the remaining TTL in milliseconds.
     *   - For fields without a timeout, returns -1.
     *   - For fields that do not exist, returns -2.
     *
     * @example
     * ```typescript
     * // Get TTL for hash fields in milliseconds
     * const result = await client.hpttl("my_hash", ["field1", "field2", "field3"]);
     * console.log(result); // [120000, -1, -2] - field1 expires in 120000 ms, field2 has no expiration, field3 doesn't exist
     * ```
     *
     * @example
     * ```typescript
     * // Get TTL for a single field in milliseconds
     * const result = await client.hpttl("my_hash", ["field1"]);
     * console.log(result); // [60000] - field1 expires in 60000 ms
     * ```
     */
    public async hpttl(
        key: GlideString,
        fields: GlideString[],
    ): Promise<number[]> {
        return this.createWritePromise(createHPTtl(key, fields));
    }

    /** Inserts all the specified values at the head of the list stored at `key`.
     * `elements` are inserted one after the other to the head of the list, from the leftmost element to the rightmost element.
     * If `key` does not exist, it is created as empty list before performing the push operations.
     *
     * @see {@link https://valkey.io/commands/lpush/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @param elements - The elements to insert at the head of the list stored at `key`.
     * @returns the length of the list after the push operations.
     *
     * @example
     * ```typescript
     * // Example usage of the lpush method with an existing list
     * const result = await client.lpush("my_list", ["value2", "value3"]);
     * console.log(result);
     * // Output: 3
     * // Indicates that the new length of the list is 3 after the push operation.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the lpush method with a non-existing list
     * const result = await client.lpush("nonexistent_list", ["new_value"]);
     * console.log(result);
     * // Output: 1
     * // Indicates that a new list was created with one element
     * ```
     */
    public async lpush(
        key: GlideString,
        elements: GlideString[],
    ): Promise<number> {
        return this.createWritePromise(createLPush(key, elements));
    }

    /**
     * Inserts specified values at the head of the `list`, only if `key` already
     * exists and holds a list.
     *
     * @see {@link https://valkey.io/commands/lpushx/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @param elements - The elements to insert at the head of the list stored at `key`.
     * @returns The length of the list after the push operation.
     * @example
     * ```typescript
     * const result = await client.lpushx("my_list", ["value1", "value2"]);
     * console.log(result);
     * // Output: 2
     * // Indicates that the list has two elements after the push operation.
     * ```
     */
    public async lpushx(
        key: GlideString,
        elements: GlideString[],
    ): Promise<number> {
        return this.createWritePromise(createLPushX(key, elements));
    }

    /** Removes and returns the first elements of the list stored at `key`.
     * The command pops a single element from the beginning of the list.
     *
     * @see {@link https://valkey.io/commands/lpop/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns The value of the first element.
     * If `key` does not exist null will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of the lpop method with an existing list
     * const result = await client.lpop("my_list");
     * console.log(result);
     * // Output: 'value1'
     * // Returns and removes the first element of the list `value1`.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the lpop method with a non-existing list
     * const result = await client.lpop("non_exiting_key");
     * console.log(result);
     * // Output: null
     * // Returns null for non-existent key.
     * ```
     */
    public async lpop(
        key: GlideString,
        options?: DecoderOption,
    ): Promise<GlideString | null> {
        return this.createWritePromise(createLPop(key), options);
    }

    /** Removes and returns up to `count` elements of the list stored at `key`, depending on the list's length.
     *
     * @see {@link https://valkey.io/commands/lpop/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @param count - The count of the elements to pop from the list.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A list of the popped elements will be returned depending on the list's length.
     * If `key` does not exist null will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of the lpopCount method with an existing list
     * const result = await client.lpopCount("my_list", 2);
     * console.log(result);
     * // Output: ["value1", "value2"]
     * // Returns and removes 2 elements from the list.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the lpopCount method with a non-existing list
     * const result = await client.lpopCount("non_exiting_key", 3);
     * console.log(result);
     * // Output: null
     * // Returns null in case of non-existent key.
     * ```
     */
    public async lpopCount(
        key: GlideString,
        count: number,
        options?: DecoderOption,
    ): Promise<GlideString[] | null> {
        return this.createWritePromise(createLPop(key, count), options);
    }

    /** Returns the specified elements of the list stored at `key`.
     * The offsets `start` and `end` are zero-based indexes, with 0 being the first element of the list, 1 being the next element and so on.
     * These offsets can also be negative numbers indicating offsets starting at the end of the list,
     * with -1 being the last element of the list, -2 being the penultimate, and so on.
     *
     * @see {@link https://valkey.io/commands/lrange/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @param start - The starting point of the range.
     * @param end - The end of the range.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns list of elements in the specified range.
     * If `start` exceeds the end of the list, or if `start` is greater than `end`, an empty list will be returned.
     * If `end` exceeds the actual end of the list, the range will stop at the actual end of the list.
     * If `key` does not exist an empty list will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of the lrange method with an existing list and positive indices
     * const result = await client.lrange("my_list", 0, 2);
     * console.log(result);
     * // Output: ["value1", "value2", "value3"]
     * // Returns the first 3 elements of the list.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the lrange method with an existing list and negative indices
     * const result = await client.lrange("my_list", -2, -1);
     * console.log(result);
     * // Output: ["value2", "value3"]
     * // Returns the last 2 elements of the list.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the lrange method with a non-existing list
     * const result = await client.lrange("non_exiting_key", 0, 2);
     * console.log(result);
     * // Output: []
     * // Returns an empty list for non-existent key.
     * ```
     */
    public async lrange(
        key: GlideString,
        start: number,
        end: number,
        options?: DecoderOption,
    ): Promise<GlideString[]> {
        return this.createWritePromise(createLRange(key, start, end), options);
    }

    /** Returns the length of the list stored at `key`.
     *
     * @see {@link https://valkey.io/commands/llen/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @returns the length of the list at `key`.
     * If `key` does not exist, it is interpreted as an empty list and 0 is returned.
     *
     * @example
     * ```typescript
     * // Example usage of the llen method
     * const result = await client.llen("my_list");
     * console.log(result);
     * // Output: 3
     * // Indicates that there are 3 elements in the list.
     * ```
     */
    public async llen(key: GlideString): Promise<number> {
        return this.createWritePromise(createLLen(key));
    }

    /**
     * Atomically pops and removes the left/right-most element to the list stored at `source`
     * depending on `whereTo`, and pushes the element at the first/last element of the list
     * stored at `destination` depending on `whereFrom`, see {@link ListDirection}.
     *
     * @see {@link https://valkey.io/commands/lmove/|valkey.io} for details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param source - The key to the source list.
     * @param destination - The key to the destination list.
     * @param whereFrom - The {@link ListDirection} to remove the element from.
     * @param whereTo - The {@link ListDirection} to add the element to.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns The popped element, or `null` if `source` does not exist.
     *
     * @example
     * ```typescript
     * await client.lpush("testKey1", ["two", "one"]); // The key `testKey1` has a list ["one", "two"] after this operation.
     * await client.lpush("testKey2", ["four", "three"]); // The key `testKey2` has a list ["three", "four"] after this operation.
     *
     * const result = await client.lmove("testKey1", "testKey2", ListDirection.LEFT, ListDirection.LEFT);
     * console.log(result);
     * // Output: "one".
     * // Removes "one" from the list at key `testKey1` and adds it to the left of the list at `testKey2`.
     *
     * const updated_array_key1 = await client.lrange("testKey1", 0, -1);
     * console.log(updated_array_key1);
     * // Output: ["two"]
     * // The elements in the list at `testKey1` after lmove command.
     *
     * const updated_array_key2 = await client.lrange("testKey2", 0, -1);
     * console.log(updated_array_key2);
     * // Output: ["one", "three", "four"]
     * // The elements in the list at `testKey2` after lmove command.
     * ```
     */
    public async lmove(
        source: GlideString,
        destination: GlideString,
        whereFrom: ListDirection,
        whereTo: ListDirection,
        options?: DecoderOption,
    ): Promise<GlideString | null> {
        return this.createWritePromise(
            createLMove(source, destination, whereFrom, whereTo),
            options,
        );
    }

    /**
     * Blocks the connection until it pops atomically and removes the left/right-most element to the
     * list stored at `source` depending on `whereFrom`, and pushes the element at the first/last element
     * of the list stored at `destination` depending on `whereTo`.
     * `BLMOVE` is the blocking variant of {@link lmove}.
     *
     * @see {@link https://valkey.io/commands/blmove/|valkey.io} for details.
     * @remarks When in cluster mode, both `source` and `destination` must map to the same hash slot.
     * @remarks `BLMOVE` is a client blocking command, see {@link https://glide.valkey.io/how-to/connection-management/#blocking-commands|Valkey GLIDE Documentation} for more details and best practices.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param source - The key to the source list.
     * @param destination - The key to the destination list.
     * @param whereFrom - The {@link ListDirection} to remove the element from.
     * @param whereTo - The {@link ListDirection} to add the element to.
     * @param timeout - The number of seconds to wait for a blocking operation to complete. A value of `0` will block indefinitely.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns The popped element, or `null` if `source` does not exist or if the operation timed-out.
     *
     * @example
     * ```typescript
     * await client.lpush("testKey1", ["two", "one"]); // The key `testKey1` has a list ["one", "two"] after this operation.
     * await client.lpush("testKey2", ["four", "three"]); // The key `testKey2` has a list ["three", "four"] after this operation.
     * const result = await client.blmove("testKey1", "testKey2", ListDirection.LEFT, ListDirection.LEFT, 0.1);
     * console.log(result);
     * // Output: "one"
     * // Removes "one" from the list at key `testKey1` and adds it to the left of the list at `testKey2`.
     *
     * const updated_array1 = await client.lrange("testKey1", 0, -1);
     * console.log(updated_array1);
     * // Output: "two"
     * // The elements in the list at `testKey1` after blmove command.
     *
     * const updated_array2 = await client.lrange("testKey2", 0, -1);
     * console.log(updated_array2);
     * // Output: ["one", "three", "four"]
     * // The elements in the list at `testKey2` after blmove command.
     * ```
     */
    public async blmove(
        source: GlideString,
        destination: GlideString,
        whereFrom: ListDirection,
        whereTo: ListDirection,
        timeout: number,
        options?: DecoderOption,
    ): Promise<GlideString | null> {
        return this.createWritePromise(
            createBLMove(source, destination, whereFrom, whereTo, timeout),
            options,
        );
    }

    /**
     * Sets the list element at `index` to `element`.
     * The index is zero-based, so `0` means the first element, `1` the second element and so on.
     * Negative indices can be used to designate elements starting at the tail of
     * the list. Here, `-1` means the last element, `-2` means the penultimate and so forth.
     *
     * @see {@link https://valkey.io/commands/lset/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @param index - The index of the element in the list to be set.
     * @param element - The new element to set at the specified index.
     * @returns Always "OK".
     *
     * @example
     * ```typescript
     * // Example usage of the lset method
     * const result = await client.lset("test_key", 1, "two");
     * console.log(result);
     * // Output: 'OK'
     * // Indicates that the second index of the list has been set to "two".
     * ```
     */
    public async lset(
        key: GlideString,
        index: number,
        element: GlideString,
    ): Promise<"OK"> {
        return this.createWritePromise(createLSet(key, index, element), {
            decoder: Decoder.String,
        });
    }

    /** Trim an existing list so that it will contain only the specified range of elements specified.
     * The offsets `start` and `end` are zero-based indexes, with 0 being the first element of the list, 1 being the next element and so on.
     * These offsets can also be negative numbers indicating offsets starting at the end of the list,
     * with -1 being the last element of the list, -2 being the penultimate, and so on.
     *
     * @see {@link https://valkey.io/commands/ltrim/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @param start - The starting point of the range.
     * @param end - The end of the range.
     * @returns always "OK".
     * If `start` exceeds the end of the list, or if `start` is greater than `end`, the list is emptied and the key is removed.
     * If `end` exceeds the actual end of the list, it will be treated like the last element of the list.
     * If `key` does not exist the command will be ignored.
     *
     * @example
     * ```typescript
     * // Example usage of the ltrim method
     * const result = await client.ltrim("my_list", 0, 1);
     * console.log(result);
     * // Output: 'OK'
     * // Indicates that the list has been trimmed to contain elements from 0 to 1.
     * ```
     */
    public async ltrim(
        key: GlideString,
        start: number,
        end: number,
    ): Promise<"OK"> {
        return this.createWritePromise(createLTrim(key, start, end), {
            decoder: Decoder.String,
        });
    }

    /** Removes the first `count` occurrences of elements equal to `element` from the list stored at `key`.
     *
     * @param key - The key of the list.
     * @param count - The count of the occurrences of elements equal to `element` to remove.
     * If `count` is positive : Removes elements equal to `element` moving from head to tail.
     * If `count` is negative : Removes elements equal to `element` moving from tail to head.
     * If `count` is 0 or `count` is greater than the occurrences of elements equal to `element`: Removes all elements equal to `element`.
     * @param element - The element to remove from the list.
     * @returns the number of the removed elements.
     * If `key` does not exist, 0 is returned.
     *
     * @example
     * ```typescript
     * // Example usage of the lrem method
     * const result = await client.lrem("my_list", 2, "value");
     * console.log(result);
     * // Output: 2
     * // Removes the first 2 occurrences of "value" in the list.
     * ```
     */
    public async lrem(
        key: GlideString,
        count: number,
        element: GlideString,
    ): Promise<number> {
        return this.createWritePromise(createLRem(key, count, element));
    }

    /** Inserts all the specified values at the tail of the list stored at `key`.
     * `elements` are inserted one after the other to the tail of the list, from the leftmost element to the rightmost element.
     * If `key` does not exist, it is created as empty list before performing the push operations.
     *
     * @see {@link https://valkey.io/commands/rpush/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @param elements - The elements to insert at the tail of the list stored at `key`.
     * @returns the length of the list after the push operations.
     *
     * @example
     * ```typescript
     * // Example usage of the rpush method with an existing list
     * const result = await client.rpush("my_list", ["value2", "value3"]);
     * console.log(result);
     * // Output: 3
     * // Indicates that the new length of the list is 3 after the push operation.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the rpush method with a non-existing list
     * const result = await client.rpush("nonexistent_list", ["new_value"]);
     * console.log(result);
     * // Output: 1
     * ```
     */
    public async rpush(
        key: GlideString,
        elements: GlideString[],
    ): Promise<number> {
        return this.createWritePromise(createRPush(key, elements));
    }

    /**
     * Inserts specified values at the tail of the `list`, only if `key` already
     * exists and holds a list.
     *
     * @see {@link https://valkey.io/commands/rpushx/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @param elements - The elements to insert at the tail of the list stored at `key`.
     * @returns The length of the list after the push operation.
     * @example
     * ```typescript
     * const result = await client.rpushx("my_list", ["value1", "value2"]);
     * console.log(result);
     * // Output: 2
     * // Indicates that the list has two elements.
     * ```
     * */
    public async rpushx(
        key: GlideString,
        elements: GlideString[],
    ): Promise<number> {
        return this.createWritePromise(createRPushX(key, elements));
    }

    /** Removes and returns the last elements of the list stored at `key`.
     * The command pops a single element from the end of the list.
     *
     * @see {@link https://valkey.io/commands/rpop/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns The value of the last element.
     * If `key` does not exist null will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of the rpop method with an existing list
     * const result = await client.rpop("my_list");
     * console.log(result);
     * // Output: 'value1'
     * // Returns and removes the last element of the list stored at `my_list`.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the rpop method with a non-existing list
     * const result = await client.rpop("non_exiting_key");
     * console.log(result);
     * // Output: null
     * // Returns null for non-existent key.
     * ```
     */
    public async rpop(
        key: GlideString,
        options?: DecoderOption,
    ): Promise<GlideString | null> {
        return this.createWritePromise(createRPop(key), options);
    }

    /** Removes and returns up to `count` elements from the list stored at `key`, depending on the list's length.
     *
     * @see {@link https://valkey.io/commands/rpop/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @param count - The count of the elements to pop from the list.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A list of popped elements will be returned depending on the list's length.
     * If `key` does not exist null will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of the rpopCount method with an existing list
     * const result = await client.rpopCount("my_list", 2);
     * console.log(result);
     * // Output: ["value1", "value2"]
     * // Returns and removes the last 2 elements from the list stored at `my_list`.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the rpopCount method with a non-existing list
     * const result = await client.rpopCount("non_exiting_key", 7);
     * console.log(result);
     * // Output: null
     * // Returns null for a non-existing key.
     * ```
     */
    public async rpopCount(
        key: GlideString,
        count: number,
        options?: DecoderOption,
    ): Promise<GlideString[] | null> {
        return this.createWritePromise(createRPop(key, count), options);
    }

    /** Adds the specified members to the set stored at `key`. Specified members that are already a member of this set are ignored.
     * If `key` does not exist, a new set is created before adding `members`.
     *
     * @see {@link https://valkey.io/commands/sadd/|valkey.io} for details.
     *
     * @param key - The key to store the members to its set.
     * @param members - A list of members to add to the set stored at `key`.
     * @returns The number of members that were added to the set, not including all the members already present in the set.
     *
     * @example
     * ```typescript
     * // Example usage of the sadd method with an existing set
     * const result = await client.sadd("my_set", ["member1", "member2"]);
     * console.log(result);
     * // Output: 2
     * // Adds 2 members to the set at key `my_set`
     * ```
     */
    public async sadd(
        key: GlideString,
        members: GlideString[],
    ): Promise<number> {
        return this.createWritePromise(createSAdd(key, members));
    }

    /**
     * Changes the currently selected database.
     *
     * @see {@link https://valkey.io/commands/select/|valkey.io} for details.
     *
     * @param index - The index of the database to select.
     * @returns A simple `"OK"` response.
     *
     * @example
     * ```typescript
     * // Example usage of select method (NOT RECOMMENDED)
     * const result = await client.select(2);
     * console.log(result); // Output: 'OK'
     * // Note: Database selection will be lost on reconnection!
     * ```
     */
    public async select(index: number): Promise<"OK"> {
        return this.createWritePromise(createSelect(index), {
            decoder: Decoder.String,
        });
    }

    /** Removes the specified members from the set stored at `key`. Specified members that are not a member of this set are ignored.
     *
     * @see {@link https://valkey.io/commands/srem/|valkey.io} for details.
     *
     * @param key - The key to remove the members from its set.
     * @param members - A list of members to remove from the set stored at `key`.
     * @returns The number of members that were removed from the set, not including non existing members.
     * If `key` does not exist, it is treated as an empty set and this command returns 0.
     *
     * @example
     * ```typescript
     * // Example usage of the srem method
     * const result = await client.srem("my_set", ["member1", "member2"]);
     * console.log(result);
     * // Output: 2
     * // Removes `member1` and `member2` from the set at key `my_set`.
     * ```
     */
    public async srem(
        key: GlideString,
        members: GlideString[],
    ): Promise<number> {
        return this.createWritePromise(createSRem(key, members));
    }

    /**
     * Iterates incrementally over a set.
     *
     * @see {@link https://valkey.io/commands/sscan} for details.
     *
     * @param key - The key of the set.
     * @param cursor - The cursor that points to the next iteration of results. A value of `"0"` indicates the start of the search.
     * @param options - (Optional) See {@link BaseScanOptions} and {@link DecoderOption}.
     * @returns An array of the cursor and the subset of the set held by `key`. The first element is always the `cursor` and for the next iteration of results.
     * The `cursor` will be `"0"` on the last iteration of the set. The second element is always an array of the subset of the set held in `key`.
     *
     * @example
     * ```typescript
     * // Assume key contains a set with 200 members
     * let newCursor = "0";
     * let result = [];
     *
     * do {
     *      result = await client.sscan(key1, newCursor, {
     *      match: "*",
     *      count: 5,
     * });
     *      newCursor = result[0];
     *      console.log("Cursor: ", newCursor);
     *      console.log("Members: ", result[1]);
     * } while (newCursor !== "0");
     *
     * // The output of the code above is something similar to:
     * // Cursor:  8, Match: "f*"
     * // Members:  ['field', 'fur', 'fun', 'fame']
     * // Cursor:  20, Count: 3
     * // Members:  ['1', '2', '3', '4', '5', '6']
     * // Cursor:  0
     * // Members:  ['1', '2', '3', '4', '5', '6']
     * ```
     */
    public async sscan(
        key: GlideString,
        cursor: GlideString,
        options?: BaseScanOptions & DecoderOption,
    ): Promise<[GlideString, GlideString[]]> {
        return this.createWritePromise(
            createSScan(key, cursor, options),
            options,
        );
    }

    /** Returns all the members of the set value stored at `key`.
     *
     * @see {@link https://valkey.io/commands/smembers/|valkey.io} for details.
     *
     * @param key - The key to return its members.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A `Set` containing all members of the set.
     * If `key` does not exist, it is treated as an empty set and this command returns an empty `Set`.
     *
     * @example
     * ```typescript
     * // Example usage of the smembers method
     * const result = await client.smembers("my_set");
     * console.log(result);
     * // Output: Set(3) {'member1', 'member2', 'member3'}
     * ```
     */
    public async smembers(
        key: GlideString,
        options?: DecoderOption,
    ): Promise<Set<GlideString>> {
        return this.createWritePromise<GlideString[]>(
            createSMembers(key),
            options,
        ).then((smembers) => new Set<GlideString>(smembers));
    }

    /** Moves `member` from the set at `source` to the set at `destination`, removing it from the source set.
     * Creates a new destination set if needed. The operation is atomic.
     *
     * @see {@link https://valkey.io/commands/smove/|valkey.io} for more details.
     * @remarks When in cluster mode, `source` and `destination` must map to the same hash slot.
     *
     * @param source - The key of the set to remove the element from.
     * @param destination - The key of the set to add the element to.
     * @param member - The set element to move.
     * @returns `true` on success, or `false` if the `source` set does not exist or the element is not a member of the source set.
     *
     * @example
     * ```typescript
     * const result = await client.smove("set1", "set2", "member1");
     * console.log(result);
     * // Output: true
     * // `member1` was moved from `set1` to `set2`.
     * ```
     */
    public async smove(
        source: GlideString,
        destination: GlideString,
        member: GlideString,
    ): Promise<boolean> {
        return this.createWritePromise(
            createSMove(source, destination, member),
        );
    }

    /** Returns the set cardinality (number of elements) of the set stored at `key`.
     *
     * @see {@link https://valkey.io/commands/scard/|valkey.io} for details.
     *
     * @param key - The key to return the number of its members.
     * @returns The cardinality (number of elements) of the set, or 0 if key does not exist.
     *
     * @example
     * ```typescript
     * // Example usage of the scard method
     * const result = await client.scard("my_set");
     * console.log(result);
     * // Output: 3
     * ```
     */
    public async scard(key: GlideString): Promise<number> {
        return this.createWritePromise(createSCard(key));
    }

    /** Gets the intersection of all the given sets.
     *
     * @see {@link https://valkey.io/commands/sinter/|valkey.io} for more details.
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     *
     * @param keys - The `keys` of the sets to get the intersection.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns - A set of members which are present in all given sets.
     * If one or more sets do not exist, an empty set will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of sinter method when member exists
     * const result = await client.sinter(["my_set1", "my_set2"]);
     * console.log(result);
     * // Output: Set(1) {'member2'}
     * // Indicates that sets have one common member
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of sinter method with non-existing key
     * const result = await client.sinter(["my_set", "non_existing_key"]);
     * console.log(result);
     * // Output: Set(0) {}
     * // An empty set is returned since the key does not exist.
     * ```
     */
    public async sinter(
        keys: GlideString[],
        options?: DecoderOption,
    ): Promise<Set<GlideString>> {
        return this.createWritePromise<GlideString[]>(
            createSInter(keys),
            options,
        ).then((sinter) => new Set<GlideString>(sinter));
    }

    /**
     * Gets the cardinality of the intersection of all the given sets.
     *
     * @see {@link https://valkey.io/commands/sintercard/|valkey.io} for more details.
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param keys - The keys of the sets.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `limit`: the limit for the intersection cardinality value. If not specified, or set to `0`, no limit is used.
     * @returns The cardinality of the intersection result. If one or more sets do not exist, `0` is returned.
     *
     * @example
     * ```typescript
     * await client.sadd("set1", ["a", "b", "c"]);
     * await client.sadd("set2", ["b", "c", "d"]);
     * const result1 = await client.sintercard(["set1", "set2"]);
     * console.log(result1);
     * // Output: 2
     * // The intersection of `set1` and `set2` contains 2 elements: `b` and `c`.
     *
     * const result2 = await client.sintercard(["set1", "set2"], { limit: 1 });
     * console.log(result2);
     * // Output: 1
     * // The computation stops early as the intersection cardinality reaches the limit of 1.
     * ```
     */
    public async sintercard(
        keys: GlideString[],
        options?: { limit?: number },
    ): Promise<number> {
        return this.createWritePromise(createSInterCard(keys, options?.limit));
    }

    /**
     * Stores the members of the intersection of all given sets specified by `keys` into a new set at `destination`.
     *
     * @see {@link https://valkey.io/commands/sinterstore/|valkey.io} for more details.
     * @remarks When in cluster mode, `destination` and all `keys` must map to the same hash slot.
     *
     * @param destination - The key of the destination set.
     * @param keys - The keys from which to retrieve the set members.
     * @returns The number of elements in the resulting set.
     *
     * @example
     * ```typescript
     * const result = await client.sinterstore("my_set", ["set1", "set2"]);
     * console.log(result);
     * // Output: 2
     * // Two elements were stored at `my_set`, and those elements are the intersection of `set1` and `set2`.
     * ```
     */
    public async sinterstore(
        destination: GlideString,
        keys: GlideString[],
    ): Promise<number> {
        return this.createWritePromise(createSInterStore(destination, keys));
    }

    /**
     * Computes the difference between the first set and all the successive sets in `keys`.
     *
     * @see {@link https://valkey.io/commands/sdiff/|valkey.io} for more details.
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     *
     * @param keys - The keys of the sets to diff.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A `Set` of elements representing the difference between the sets.
     * If a key in `keys` does not exist, it is treated as an empty set.
     *
     * @example
     * ```typescript
     * await client.sadd("set1", ["member1", "member2"]);
     * await client.sadd("set2", ["member1"]);
     * const result = await client.sdiff(["set1", "set2"]);
     * console.log(result);
     * // Output: Set(1) {"member1"}
     * // `member2` is in `set1` but not `set2`
     * ```
     */
    public async sdiff(
        keys: GlideString[],
        options?: DecoderOption,
    ): Promise<Set<GlideString>> {
        return this.createWritePromise<GlideString[]>(
            createSDiff(keys),
            options,
        ).then((sdiff) => new Set<GlideString>(sdiff));
    }

    /**
     * Stores the difference between the first set and all the successive sets in `keys` into a new set at `destination`.
     *
     * @see {@link https://valkey.io/commands/sdiffstore/|valkey.io} for more details.
     * @remarks When in cluster mode, `destination` and all `keys` must map to the same hash slot.
     *
     * @param destination - The key of the destination set.
     * @param keys - The keys of the sets to diff.
     * @returns The number of elements in the resulting set.
     *
     * @example
     * ```typescript
     * await client.sadd("set1", ["member1", "member2"]);
     * await client.sadd("set2", ["member1"]);
     * const result = await client.sdiffstore("set3", ["set1", "set2"]);
     * console.log(result);
     * // Output: 1
     * // One member was stored in `set3`, and that member is the diff between `set1` and `set2`.
     * ```
     */
    public async sdiffstore(
        destination: GlideString,
        keys: GlideString[],
    ): Promise<number> {
        return this.createWritePromise(createSDiffStore(destination, keys));
    }

    /**
     * Gets the union of all the given sets.
     *
     * @see {@link https://valkey.io/commands/sunion/|valkey.io} for more details.
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     *
     * @param keys - The keys of the sets.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A `Set` of members which are present in at least one of the given sets.
     * If none of the sets exist, an empty `Set` will be returned.
     *
     * @example
     * ```typescript
     * await client.sadd("my_set1", ["member1", "member2"]);
     * await client.sadd("my_set2", ["member2", "member3"]);
     * const result1 = await client.sunion(["my_set1", "my_set2"]);
     * console.log(result1);
     * // Output: Set(3) {'member1', 'member2', 'member3'}
     * // Sets `my_set1` and `my_set2` have three unique members.
     *
     * const result2 = await client.sunion(["my_set1", "non_existing_set"]);
     * console.log(result2);
     * // Output: Set(2) {'member1', 'member2'}
     * ```
     */
    public async sunion(
        keys: GlideString[],
        options?: DecoderOption,
    ): Promise<Set<GlideString>> {
        return this.createWritePromise<GlideString[]>(
            createSUnion(keys),
            options,
        ).then((sunion) => new Set<GlideString>(sunion));
    }

    /**
     * Stores the members of the union of all given sets specified by `keys` into a new set
     * at `destination`.
     *
     * @see {@link https://valkey.io/commands/sunionstore/|valkey.io} for details.
     * @remarks When in cluster mode, `destination` and all `keys` must map to the same hash slot.
     *
     * @param destination - The key of the destination set.
     * @param keys - The keys from which to retrieve the set members.
     * @returns The number of elements in the resulting set.
     *
     * @example
     * ```typescript
     * const length = await client.sunionstore("mySet", ["set1", "set2"]);
     * console.log(length);
     * // Output: 2
     * // Two elements were stored in `mySet`, and those two members are the union of `set1` and `set2`.
     * ```
     */
    public async sunionstore(
        destination: GlideString,
        keys: GlideString[],
    ): Promise<number> {
        return this.createWritePromise(createSUnionStore(destination, keys));
    }

    /** Returns if `member` is a member of the set stored at `key`.
     *
     * @see {@link https://valkey.io/commands/sismember/|valkey.io} for more details.
     *
     * @param key - The key of the set.
     * @param member - The member to check for existence in the set.
     * @returns `true` if the member exists in the set, `false` otherwise.
     * If `key` doesn't exist, it is treated as an empty set and the command returns `false`.
     *
     * @example
     * ```typescript
     * // Example usage of the sismember method when member exists
     * const result = await client.sismember("my_set", "member1");
     * console.log(result);
     * // Output: true
     * // Indicates that `member1` exists in the set `my_set`.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the sismember method when member does not exist
     * const result = await client.sismember("my_set", "non_existing_member");
     * console.log(result);
     * // Output: false
     * // Indicates that `non_existing_member` does not exist in the set `my_set`.
     * ```
     */
    public async sismember(
        key: GlideString,
        member: GlideString,
    ): Promise<boolean> {
        return this.createWritePromise(createSIsMember(key, member));
    }

    /**
     * Checks whether each member is contained in the members of the set stored at `key`.
     *
     * @see {@link https://valkey.io/commands/smismember/|valkey.io} for more details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param key - The key of the set to check.
     * @param members - A list of members to check for existence in the set.
     * @returns An `array` of `boolean` values, each indicating if the respective member exists in the set.
     *
     * @example
     * ```typescript
     * await client.sadd("set1", ["a", "b", "c"]);
     * const result = await client.smismember("set1", ["b", "c", "d"]);
     * console.log(result);
     * // Output: [true, true, false]
     * // `b` and `c` are members of `set1`, but `d` is not.
     * ```
     */
    public async smismember(
        key: GlideString,
        members: GlideString[],
    ): Promise<boolean[]> {
        return this.createWritePromise(createSMIsMember(key, members));
    }

    /** Removes and returns one random member from the set value store at `key`.
     * To pop multiple members, see {@link spopCount}.
     *
     * @see {@link https://valkey.io/commands/spop/|valkey.io} for details.
     *
     * @param key - The key of the set.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns the value of the popped member.
     * If `key` does not exist, null will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of spop method to remove and return a random member from a set
     * const result = await client.spop("my_set");
     * console.log(result);
     * // Output: 'member1'
     * // Removes and returns a random member from the set `my_set`.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of spop method with non-existing key
     * const result = await client.spop("non_existing_key");
     * console.log(result);
     * // Output: null
     * ```
     */
    public async spop(
        key: GlideString,
        options?: DecoderOption,
    ): Promise<GlideString | null> {
        return this.createWritePromise(createSPop(key), options);
    }

    /** Removes and returns up to `count` random members from the set value store at `key`, depending on the set's length.
     *
     * @see {@link https://valkey.io/commands/spop/|valkey.io} for details.
     *
     * @param key - The key of the set.
     * @param count - The count of the elements to pop from the set.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A `Set` containing the popped elements, depending on the set's length.
     * If `key` does not exist, an empty `Set` will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of spopCount method to remove and return multiple random members from a set
     * const result = await client.spopCount("my_set", 2);
     * console.log(result);
     * // Output: Set(2) {'member2', 'member3'}
     * // Removes and returns 2 random members from the set `my_set`.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of spopCount method with non-existing key
     * const result = await client.spopCount("non_existing_key");
     * console.log(result);
     * // Output: Set(0) {}
     * // An empty set is returned since the key does not exist.
     * ```
     */
    public async spopCount(
        key: GlideString,
        count: number,
        options?: DecoderOption,
    ): Promise<Set<GlideString>> {
        return this.createWritePromise<GlideString[]>(
            createSPop(key, count),
            options,
        ).then((spop) => new Set<GlideString>(spop));
    }

    /**
     * Returns a random element from the set value stored at `key`.
     *
     * @see {@link https://valkey.io/commands/srandmember/|valkey.io} for more details.
     *
     * @param key - The key from which to retrieve the set member.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A random element from the set, or null if `key` does not exist.
     *
     * @example
     * ```typescript
     * // Example usage of srandmember method to return a random member from a set
     * const result = await client.srandmember("my_set");
     * console.log(result);
     * // Output: 'member1'
     * // A random member of `my_set`.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of srandmember method with non-existing key
     * const result = await client.srandmember("non_existing_set");
     * console.log(result);
     * // Output: null
     * ```
     */
    public async srandmember(
        key: GlideString,
        options?: DecoderOption,
    ): Promise<GlideString | null> {
        return this.createWritePromise(createSRandMember(key), options);
    }

    /**
     * Returns one or more random elements from the set value stored at `key`.
     *
     * @see {@link https://valkey.io/commands/srandmember/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param count - The number of members to return.
     *                If `count` is positive, returns unique members.
     *                If `count` is negative, allows for duplicates members.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns a list of members from the set. If the set does not exist or is empty, an empty list will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of srandmemberCount method to return multiple random members from a set
     * const result = await client.srandmemberCount("my_set", -3);
     * console.log(result);
     * // Output: ['member1', 'member1', 'member2']
     * // Random members of `my_set`.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of srandmemberCount method with non-existing key
     * const result = await client.srandmemberCount("non_existing_set", 3);
     * console.log(result);
     * // Output: []
     * // An empty list since the key does not exist.
     * ```
     */
    public async srandmemberCount(
        key: GlideString,
        count: number,
        options?: DecoderOption,
    ): Promise<GlideString[]> {
        return this.createWritePromise(createSRandMember(key, count), options);
    }

    /**
     * Returns the number of keys in `keys` that exist in the database.
     *
     * @remarks In cluster mode, if keys in `keys` map to different hash slots,
     * the command will be split across these slots and executed separately for each.
     * This means the command is atomic only at the slot level. If one or more slot-specific
     * requests fail, the entire call will return the first encountered error, even
     * though some requests may have succeeded while others did not.
     * If this behavior impacts your application logic, consider splitting the
     * request into sub-requests per slot to ensure atomicity.
     *
     * @see {@link https://valkey.io/commands/exists/|valkey.io} for details.
     *
     * @param keys - The keys list to check.
     * @returns The number of keys that exist. If the same existing key is mentioned in `keys` multiple times,
     * it will be counted multiple times.
     *
     * @example
     * ```typescript
     * // Example usage of the exists method
     * const result = await client.exists(["key1", "key2", "key3"]);
     * console.log(result);
     * // Output: 3
     * // Indicates that all three keys exist in the database.
     * ```
     */
    public async exists(keys: GlideString[]): Promise<number> {
        return this.createWritePromise(createExists(keys));
    }

    /**
     * Removes the specified keys. A key is ignored if it does not exist.
     * This command, similar to {@link del}, removes specified keys and ignores non-existent ones.
     * However, this command does not block the server, while {@link https://valkey.io/commands/del|`DEL`} does.
     *
     * @remarks In cluster mode, if keys in `keys` map to different hash slots,
     * the command will be split across these slots and executed separately for each.
     * This means the command is atomic only at the slot level. If one or more slot-specific
     * requests fail, the entire call will return the first encountered error, even
     * though some requests may have succeeded while others did not.
     * If this behavior impacts your application logic, consider splitting the
     * request into sub-requests per slot to ensure atomicity.
     *
     * @see {@link https://valkey.io/commands/unlink/|valkey.io} for details.
     *
     * @param keys - The keys we wanted to unlink.
     * @returns The number of keys that were unlinked.
     *
     * @example
     * ```typescript
     * // Example usage of the unlink method
     * const result = await client.unlink(["key1", "key2", "key3"]);
     * console.log(result);
     * // Output: 3
     * // Indicates that all three keys were unlinked from the database.
     * ```
     */
    public async unlink(keys: GlideString[]): Promise<number> {
        return this.createWritePromise(createUnlink(keys));
    }

    /**
     * Sets a timeout on `key` in seconds. After the timeout has expired, the key will automatically be deleted.
     * If `key` already has an existing expire set, the time to live is updated to the new value.
     * If `seconds` is non-positive number, the key will be deleted rather than expired.
     * The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
     *
     * @see {@link https://valkey.io/commands/expire/|valkey.io} for details.
     *
     * @param key - The key to set timeout on it.
     * @param seconds - The timeout in seconds.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `expireOption`: the expire option - see {@link ExpireOptions}.
     * @returns `true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
     * or operation skipped due to the provided arguments.
     *
     * @example
     * ```typescript
     * // Example usage of the expire method
     * const result = await client.expire("my_key", 60);
     * console.log(result);
     * // Output: true
     * // Indicates that a timeout of 60 seconds has been set for `my_key`.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the expire method with exisiting expiry
     * const result = await client.expire("my_key", 60, { expireOption: ExpireOptions.HasNoExpiry });
     * console.log(result);
     * // Output: false
     * // Indicates that `my_key` has an existing expiry.
     * ```
     */
    public async expire(
        key: GlideString,
        seconds: number,
        options?: { expireOption?: ExpireOptions },
    ): Promise<boolean> {
        return this.createWritePromise(
            createExpire(key, seconds, options?.expireOption),
        );
    }

    /**
     * Sets a timeout on `key`. It takes an absolute Unix timestamp (seconds since January 1, 1970) instead of specifying the number of seconds.
     * A timestamp in the past will delete the key immediately. After the timeout has expired, the key will automatically be deleted.
     * If `key` already has an existing expire set, the time to live is updated to the new value.
     * The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
     *
     * @see {@link https://valkey.io/commands/expireat/|valkey.io} for details.
     *
     * @param key - The key to set timeout on it.
     * @param unixSeconds - The timeout in an absolute Unix timestamp.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `expireOption`: the expire option - see {@link ExpireOptions}.
     * @returns `true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
     * or operation skipped due to the provided arguments.
     *
     * @example
     * ```typescript
     * // Example usage of the expireAt method on a key with no previous expiry
     * const result = await client.expireAt("my_key", 1672531200, { expireOption: ExpireOptions.HasNoExpiry });
     * console.log(result);
     * // Output: true
     * // Indicates that the expiration time for `my_key` was successfully set.
     * ```
     */
    public async expireAt(
        key: GlideString,
        unixSeconds: number,
        options?: { expireOption?: ExpireOptions },
    ): Promise<boolean> {
        return this.createWritePromise(
            createExpireAt(key, unixSeconds, options?.expireOption),
        );
    }

    /**
     * Returns the absolute Unix timestamp (since January 1, 1970) at which the given `key` will expire, in seconds.
     * To get the expiration with millisecond precision, use {@link pexpiretime}.
     *
     * @see {@link https://valkey.io/commands/expiretime/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param key - The `key` to determine the expiration value of.
     * @returns The expiration Unix timestamp in seconds, `-2` if `key` does not exist or `-1` if `key` exists but has no associated expire.
     *
     * @example
     * ```typescript
     * const result1 = await client.expiretime("myKey");
     * console.log(result1);
     * // Output: -2
     * // `myKey` doesn't exist.
     *
     * const result2 = await client.set("myKey", "value");
     * const result3 = await client.expireTime("myKey");
     * console.log(result3);
     * // Output: -1
     * // `myKey` has no associated expiration.
     *
     * client.expire(myKey, 60);
     * const result3 = await client.expireTime("myKey");
     * console.log(result3);
     * // Output: 123456
     * // The Unix timestamp (in seconds) when `myKey` will expire.
     * ```
     */
    public async expiretime(key: GlideString): Promise<number> {
        return this.createWritePromise(createExpireTime(key));
    }

    /**
     * Sets a timeout on `key` in milliseconds. After the timeout has expired, the key will automatically be deleted.
     * If `key` already has an existing expire set, the time to live is updated to the new value.
     * If `milliseconds` is non-positive number, the key will be deleted rather than expired.
     * The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
     *
     * @see {@link https://valkey.io/commands/pexpire/|valkey.io} for details.
     *
     * @param key - The key to set timeout on it.
     * @param milliseconds - The timeout in milliseconds.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `expireOption`: the expire option - see {@link ExpireOptions}.
     * @returns `true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
     * or operation skipped due to the provided arguments.
     *
     * @example
     * ```typescript
     * // Example usage of the pexpire method on a key with no previous expiry
     * const result = await client.pexpire("my_key", 60000, { expireOption: ExpireOptions.HasNoExpiry });
     * console.log(result);
     * // Output: true
     * // Indicates that a timeout of 60,000 milliseconds has been set for `my_key`.
     * ```
     */
    public async pexpire(
        key: GlideString,
        milliseconds: number,
        options?: { expireOption?: ExpireOptions },
    ): Promise<boolean> {
        return this.createWritePromise(
            createPExpire(key, milliseconds, options?.expireOption),
        );
    }

    /**
     * Sets a timeout on `key`. It takes an absolute Unix timestamp (milliseconds since January 1, 1970) instead of specifying the number of milliseconds.
     * A timestamp in the past will delete the key immediately. After the timeout has expired, the key will automatically be deleted.
     * If `key` already has an existing expire set, the time to live is updated to the new value.
     * The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
     *
     * @see {@link https://valkey.io/commands/pexpireat/|valkey.io} for details.
     *
     * @param key - The key to set timeout on it.
     * @param unixMilliseconds - The timeout in an absolute Unix timestamp.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `expireOption`: the expire option - see {@link ExpireOptions}.
     * @returns `true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
     * or operation skipped due to the provided arguments.
     *
     * @example
     * ```typescript
     * // Example usage of the pexpireAt method on a key with no previous expiry
     * const result = await client.pexpireAt("my_key", 1672531200000, { expireOption: ExpireOptions.HasNoExpiry });
     * console.log(result);
     * // Output: true
     * // Indicates that the expiration time for `my_key` was successfully set.
     * ```
     */
    public async pexpireAt(
        key: GlideString,
        unixMilliseconds: number,
        options?: { expireOption?: ExpireOptions },
    ): Promise<number> {
        return this.createWritePromise(
            createPExpireAt(key, unixMilliseconds, options?.expireOption),
        );
    }

    /**
     * Returns the absolute Unix timestamp (since January 1, 1970) at which the given `key` will expire, in milliseconds.
     *
     * @see {@link https://valkey.io/commands/pexpiretime/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param key - The `key` to determine the expiration value of.
     * @returns The expiration Unix timestamp in seconds, `-2` if `key` does not exist or `-1` if `key` exists but has no associated expire.
     *
     * @example
     * ```typescript
     * const result1 = client.pexpiretime("myKey");
     * console.log(result1);
     * // Output: -2
     * // `myKey` doesn't exist.
     *
     * const result2 = client.set(myKey, "value");
     * const result3 = client.pexpireTime(myKey);
     * console.log(result2);
     * // Output: -1
     * // `myKey` has no associated expiration.
     *
     * client.expire(myKey, 60);
     * const result3 = client.pexpireTime(myKey);
     * console.log(result3);
     * // Output: 123456789
     * // The Unix timestamp (in milliseconds) when `myKey` will expire.
     * ```
     */
    public async pexpiretime(key: GlideString): Promise<number> {
        return this.createWritePromise(createPExpireTime(key));
    }

    /**
     * Returns the remaining time to live of `key` that has a timeout.
     *
     * @see {@link https://valkey.io/commands/ttl/|valkey.io} for details.
     *
     * @param key - The key to return its timeout.
     * @returns TTL in seconds, `-2` if `key` does not exist or `-1` if `key` exists but has no associated expire.
     *
     * @example
     * ```typescript
     * // Example usage of the ttl method with existing key
     * const result = await client.ttl("my_key");
     * console.log(result);
     * // Output: 3600
     * // Indicates that `my_key` has a remaining time to live of 3600 seconds.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the ttl method with existing key that has no associated expire.
     * const result = await client.ttl("key");
     * console.log(result);
     * // Output: -1
     * // Indicates that the key has no associated expire.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the ttl method with a non-existing key
     * const result = await client.ttl("nonexistent_key");
     * console.log(result);
     * // Output: -2
     * // Indicates that the key doesn't exist.
     * ```
     */
    public async ttl(key: GlideString): Promise<number> {
        return this.createWritePromise(createTTL(key));
    }

    /**
     * Invokes a Lua script with its keys and arguments.
     * This method simplifies the process of invoking scripts on a Valkey server by using an object that represents a Lua script.
     * The script loading, argument preparation, and execution will all be handled internally. If the script has not already been loaded,
     * it will be loaded automatically using the `SCRIPT LOAD` command. After that, it will be invoked using the `EVALSHA` command.
     *
     * @see {@link https://valkey.io/commands/script-load/|SCRIPT LOAD} and {@link https://valkey.io/commands/evalsha/|EVALSHA} on valkey.io for details.
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     *
     * @param script - The Lua script to execute.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `keys` : the keys that are used in the script.
     * - (Optional) `args`: the arguments for the script.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns A value that depends on the script that was executed.
     *
     * @example
     * ```typescript
     * const luaScript = new Script("return { KEYS[1], ARGV[1] }");
     * const scriptOptions = {
     *      keys: ["foo"],
     *      args: ["bar"],
     * };
     * const result = await invokeScript(luaScript, scriptOptions);
     * console.log(result);
     * // Output: ['foo', 'bar']
     * // The result for the script.
     * ```
     */
    public async invokeScript(
        script: Script,
        options?: {
            keys?: GlideString[];
            args?: GlideString[];
        } & DecoderOption,
    ): Promise<GlideReturnType> {
        const scriptInvocation = command_request.ScriptInvocation.create({
            hash: script.getHash(),
            keys: options?.keys?.map((key) =>
                typeof key === "string" ? Buffer.from(key) : key,
            ),
            args: options?.args?.map((arg) =>
                typeof arg === "string" ? Buffer.from(arg) : arg,
            ),
        });
        return this.createScriptInvocationPromise(scriptInvocation, options);
    }

    /**
     * Returns the original source code of a script in the script cache.
     *
     * @see {@link https://valkey.io/commands/script-show|valkey.io} for more details.
     * @remarks Since Valkey version 8.0.0.
     *
     * @param sha1 - The SHA1 digest of the script.
     * @param options - (Optional) See {@link DecoderOption}.
     * @return The original source code of the script, if present in the cache.
     * If the script is not found in the cache, an error is thrown.
     *
     * @example
     * ```typescript
     * const scriptHash = script.getHash();
     * const scriptSource = await client.scriptShow(scriptHash);
     * console.log(scriptSource);
     * // Output: "return { KEYS[1], ARGV[1] }"
     * // The source for the script
     * ```
     */
    public async scriptShow(
        sha1: GlideString,
        options?: DecoderOption,
    ): Promise<GlideString> {
        return this.createWritePromise(createScriptShow(sha1), options);
    }

    /**
     * Returns stream entries matching a given range of entry IDs.
     *
     * @see {@link https://valkey.io/commands/xrange/|valkey.io} for more details.
     *
     * @param key - The key of the stream.
     * @param start - The starting stream entry ID bound for the range.
     *     - Use `value` to specify a stream entry ID.
     *     - Use `isInclusive: false` to specify an exclusive bounded stream entry ID. This is only available starting with Valkey version 6.2.0.
     *     - Use `InfBoundary.NegativeInfinity` to start with the minimum available ID.
     * @param end - The ending stream entry ID bound for the range.
     *     - Use `value` to specify a stream entry ID.
     *     - Use `isInclusive: false` to specify an exclusive bounded stream entry ID. This is only available starting with Valkey version 6.2.0.
     *     - Use `InfBoundary.PositiveInfinity` to end with the maximum available ID.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `count`: the maximum count of stream entries to return.
     *     If `count` is not provided, all stream entries in the range will be returned.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns A map of stream entry ids, to an array of entries, or `null` if `count` is non-positive.
     *
     * @example
     * ```typescript
     * await client.xadd("mystream", [["field1", "value1"]], {id: "0-1"});
     * await client.xadd("mystream", [["field2", "value2"], ["field2", "value3"]], {id: "0-2"});
     * console.log(await client.xrange("mystream", InfBoundary.NegativeInfinity, InfBoundary.PositiveInfinity));
     * // Output:
     * // {
     * //     "0-1": [["field1", "value1"]],
     * //     "0-2": [["field2", "value2"], ["field2", "value3"]],
     * // }
     * // Indicates the stream entry IDs and their associated field-value pairs for all stream entries in `mystream`.
     * ```
     */
    public async xrange(
        key: GlideString,
        start: Boundary<string>,
        end: Boundary<string>,
        options?: { count?: number } & DecoderOption,
    ): Promise<StreamEntryDataType | null> {
        return this.createWritePromise<GlideRecord<
            [GlideString, GlideString][]
        > | null>(createXRange(key, start, end, options?.count), options).then(
            (res) => (res === null ? null : convertGlideRecordToRecord(res)),
        );
    }

    /**
     * Returns stream entries matching a given range of entry IDs in reverse order. Equivalent to {@link xrange} but returns the
     * entries in reverse order.
     *
     * @see {@link https://valkey.io/commands/xrevrange/|valkey.io} for more details.
     *
     * @param key - The key of the stream.
     * @param end - The ending stream entry ID bound for the range.
     *     - Use `value` to specify a stream entry ID.
     *     - Use `isInclusive: false` to specify an exclusive bounded stream entry ID. This is only available starting with Valkey version 6.2.0.
     *     - Use `InfBoundary.PositiveInfinity` to end with the maximum available ID.
     * @param start - The ending stream ID bound for the range.
     *     - Use `value` to specify a stream entry ID.
     *     - Use `isInclusive: false` to specify an exclusive bounded stream entry ID. This is only available starting with Valkey version 6.2.0.
     *     - Use `InfBoundary.NegativeInfinity` to start with the minimum available ID.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `count`: the maximum count of stream entries to return.
     *     If `count` is not provided, all stream entries in the range will be returned.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns A map of stream entry ids, to an array of entries, or `null` if `count` is non-positive.
     *
     * @example
     * ```typescript
     * await client.xadd("mystream", [["field1", "value1"]], {id: "0-1"});
     * await client.xadd("mystream", [["field2", "value2"], ["field2", "value3"]], {id: "0-2"});
     * console.log(await client.xrevrange("mystream", InfBoundary.PositiveInfinity, InfBoundary.NegativeInfinity));
     * // Output:
     * // {
     * //     "0-2": [["field2", "value2"], ["field2", "value3"]],
     * //     "0-1": [["field1", "value1"]],
     * // }
     * // Indicates the stream entry IDs and their associated field-value pairs for all stream entries in `mystream`.
     * ```
     */
    public async xrevrange(
        key: GlideString,
        end: Boundary<string>,
        start: Boundary<string>,
        options?: { count?: number } & DecoderOption,
    ): Promise<StreamEntryDataType | null> {
        return this.createWritePromise<GlideRecord<
            [GlideString, GlideString][]
        > | null>(
            createXRevRange(key, end, start, options?.count),
            options,
        ).then((res) =>
            res === null ? null : convertGlideRecordToRecord(res),
        );
    }

    /**
     * Adds members with their scores to the sorted set stored at `key`.
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @see {@link https://valkey.io/commands/zadd/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param membersAndScores - A list of members and their corresponding scores or a mapping of members to their corresponding scores.
     * @param options - (Optional) The `ZADD` options - see {@link ZAddOptions}.
     * @returns The number of elements added to the sorted set.
     * If {@link ZAddOptions.changed} is set to `true`, returns the number of elements updated in the sorted set.
     *
     * @example
     * ```typescript
     * // Example usage of the zadd method to add elements to a sorted set
     * const data = [{ element: "member1", score: 10.5 }, { element: "member2", score: 8.2 }]
     * const result = await client.zadd("my_sorted_set", data);
     * console.log(result);
     * // Output: 2
     * // Indicates that two elements have been added to the sorted set `my_sorted_set`.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the zadd method to update scores in an existing sorted set
     * const options = { conditionalChange: ConditionalChange.ONLY_IF_EXISTS, changed: true };
     * const result = await client.zadd("existing_sorted_set", { "member1": 10.5, "member2": 8.2 }, options);
     * console.log(result);
     * // Output: 2
     * // Updates the scores of two existing members in the sorted set `existing_sorted_set`.
     * ```
     */
    public async zadd(
        key: GlideString,
        membersAndScores: ElementAndScore[] | Record<string, Score>,
        options?: ZAddOptions,
    ): Promise<number> {
        return this.createWritePromise(
            createZAdd(key, membersAndScores, options),
        );
    }

    /**
     * Increments the score of member in the sorted set stored at `key` by `increment`.
     * If `member` does not exist in the sorted set, it is added with `increment` as its score (as if its previous score was 0.0).
     * If `key` does not exist, a new sorted set with the specified member as its sole member is created.
     *
     * @see {@link https://valkey.io/commands/zadd/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param member - A member in the sorted set to increment score to.
     * @param increment - The score to increment the member.
     * @param options - (Optional) The `ZADD` options - see {@link ZAddOptions}.
     * @returns The score of the member.
     * If there was a conflict with the options, the operation aborts and `null` is returned.
     *
     * @example
     * ```typescript
     * // Example usage of the zaddIncr method to add a member with a score to a sorted set
     * const result = await client.zaddIncr("my_sorted_set", member, 5.0);
     * console.log(result);
     * // Output: 5.0
     * // Score of the member after being updated.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the zaddIncr method to add or update a member with a score in an existing sorted set
     * const result = await client.zaddIncr("existing_sorted_set", member, "3.0", { updateOptions: UpdateByScore.LESS_THAN });
     * console.log(result);
     * // Output: null
     * // Indicates that the member in the sorted set haven't been updated.
     * ```
     */
    public async zaddIncr(
        key: GlideString,
        member: GlideString,
        increment: number,
        options?: ZAddOptions,
    ): Promise<number | null> {
        return this.createWritePromise(
            createZAdd(
                key,
                [{ element: member, score: increment }],
                options,
                true,
            ),
        );
    }

    /**
     * Removes the specified members from the sorted set stored at `key`.
     * Specified members that are not a member of this set are ignored.
     *
     * @see {@link https://valkey.io/commands/zrem/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param members - A list of members to remove from the sorted set.
     * @returns The number of members that were removed from the sorted set, not including non-existing members.
     * If `key` does not exist, it is treated as an empty sorted set, and this command returns 0.
     *
     * @example
     * ```typescript
     * // Example usage of the zrem function to remove members from a sorted set
     * const result = await client.zrem("my_sorted_set", ["member1", "member2"]);
     * console.log(result);
     * // Output: 2
     * // Indicates that two members have been removed from the sorted set `my_sorted_set`.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the zrem function when the sorted set does not exist
     * const result = await client.zrem("non_existing_sorted_set", ["member1", "member2"]);
     * console.log(result);
     * // Output: 0
     * // Indicates that no members were removed as the sorted set `non_existing_sorted_set` does not exist.
     * ```
     */
    public async zrem(
        key: GlideString,
        members: GlideString[],
    ): Promise<number> {
        return this.createWritePromise(createZRem(key, members));
    }

    /**
     * Returns the cardinality (number of elements) of the sorted set stored at `key`.
     *
     * @see {@link https://valkey.io/commands/zcard/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @returns The number of elements in the sorted set.
     * If `key` does not exist, it is treated as an empty sorted set, and this command returns `0`.
     *
     * @example
     * ```typescript
     * // Example usage of the zcard method to get the cardinality of a sorted set
     * const result = await client.zcard("my_sorted_set");
     * console.log(result);
     * // Output: 3
     * // Indicates that there are 3 elements in the sorted set `my_sorted_set`.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the zcard method with a non-existing key
     * const result = await client.zcard("non_existing_key");
     * console.log(result);
     * // Output: 0
     * ```
     */
    public async zcard(key: GlideString): Promise<number> {
        return this.createWritePromise(createZCard(key));
    }

    /**
     * Returns the cardinality of the intersection of the sorted sets specified by `keys`.
     *
     * @see {@link https://valkey.io/commands/zintercard/|valkey.io} for more details.
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param keys - The keys of the sorted sets to intersect.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `limit`: the limit for the intersection cardinality value. If not specified, or set to `0`, no limit is used.
     * @returns The cardinality of the intersection of the given sorted sets.
     *
     * @example
     * ```typescript
     * const cardinality = await client.zintercard(["key1", "key2"], { limit: 10 });
     * console.log(cardinality);
     * // Output: 3
     * // The intersection of the sorted sets at `key1` and `key2` has a cardinality of 3.
     * ```
     */
    public async zintercard(
        keys: GlideString[],
        options?: { limit?: number },
    ): Promise<number> {
        return this.createWritePromise(createZInterCard(keys, options?.limit));
    }

    /**
     * Returns the difference between the first sorted set and all the successive sorted sets.
     * To get the elements with their scores, see {@link zdiffWithScores}.
     *
     * @see {@link https://valkey.io/commands/zdiff/|valkey.io} for more details.
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param keys - The keys of the sorted sets.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns An `array` of elements representing the difference between the sorted sets.
     * If the first key does not exist, it is treated as an empty sorted set, and the command returns an empty `array`.
     *
     * @example
     * ```typescript
     * await client.zadd("zset1", {"member1": 1.0, "member2": 2.0, "member3": 3.0});
     * await client.zadd("zset2", {"member2": 2.0});
     * await client.zadd("zset3", {"member3": 3.0});
     * const result = await client.zdiff(["zset1", "zset2", "zset3"]);
     * console.log(result);
     * // Output: ["member1"]
     * // `member1` is in `zset1` but not `zset2` or `zset3`.
     * ```
     */
    public async zdiff(
        keys: GlideString[],
        options?: DecoderOption,
    ): Promise<GlideString[]> {
        return this.createWritePromise(createZDiff(keys), options);
    }

    /**
     * Returns the difference between the first sorted set and all the successive sorted sets, with the associated
     * scores.
     *
     * @see {@link https://valkey.io/commands/zdiff/|valkey.io} for more details.
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param keys - The keys of the sorted sets.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A list of elements and their scores representing the difference between the sorted sets.
     * If the first key does not exist, it is treated as an empty sorted set, and the command returns an empty `array`.
     *
     * @example
     * ```typescript
     * await client.zadd("zset1", {"member1": 1.0, "member2": 2.0, "member3": 3.0});
     * await client.zadd("zset2", {"member2": 2.0});
     * await client.zadd("zset3", {"member3": 3.0});
     * const result = await client.zdiffWithScores(["zset1", "zset2", "zset3"]);
     * console.log(result);
     * // Output: [{ element: "member1", score: 1.0 }]
     * // `member1` is in `zset1` but not `zset2` or `zset3`
     * ```
     */
    public async zdiffWithScores(
        keys: GlideString[],
        options?: DecoderOption,
    ): Promise<SortedSetDataType> {
        return this.createWritePromise<GlideRecord<number>>(
            createZDiffWithScores(keys),
            options,
        ).then(convertGlideRecordForSortedSet);
    }

    /**
     * Calculates the difference between the first sorted set and all the successive sorted sets in `keys` and stores
     * the difference as a sorted set to `destination`, overwriting it if it already exists. Non-existent keys are
     * treated as empty sets.
     *
     * @see {@link https://valkey.io/commands/zdiffstore/|valkey.io} for more details.
     * @remarks When in cluster mode, all keys in `keys` and `destination` must map to the same hash slot.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param destination - The key for the resulting sorted set.
     * @param keys - The keys of the sorted sets to compare.
     * @returns The number of members in the resulting sorted set stored at `destination`.
     *
     * @example
     * ```typescript
     * await client.zadd("zset1", {"member1": 1.0, "member2": 2.0});
     * await client.zadd("zset2", {"member1": 1.0});
     * const result1 = await client.zdiffstore("zset3", ["zset1", "zset2"]);
     * console.log(result1);
     * // Output: 1
     * // One member exists in `key1` but not `key2`, and this member was stored in `zset3`.
     *
     * const result2 = await client.zrange("zset3", {start: 0, end: -1});
     * console.log(result2);
     * // Output: ["member2"]
     * // `member2` is now stored in `my_sorted_set`.
     * ```
     */
    public async zdiffstore(
        destination: GlideString,
        keys: GlideString[],
    ): Promise<number> {
        return this.createWritePromise(createZDiffStore(destination, keys));
    }

    /**
     * Returns the score of `member` in the sorted set stored at `key`.
     *
     * @see {@link https://valkey.io/commands/zscore/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param member - The member whose score is to be retrieved.
     * @returns The score of the member.
     * If `member` does not exist in the sorted set, null is returned.
     * If `key` does not exist, null is returned.
     *
     * @example
     * ```typescript
     * // Example usage of the zscore method∂∂ to get the score of a member in a sorted set
     * const result = await client.zscore("my_sorted_set", "member");
     * console.log(result);
     * // Output: 10.5
     * // Indicates that the score of `member` in the sorted set `my_sorted_set` is 10.5.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the zscore method when the member does not exist in the sorted set
     * const result = await client.zscore("my_sorted_set", "non_existing_member");
     * console.log(result);
     * // Output: null
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the zscore method with non existimng key
     * const result = await client.zscore("non_existing_set", "member");
     * console.log(result);
     * // Output: null
     * ```
     */
    public async zscore(
        key: GlideString,
        member: GlideString,
    ): Promise<number | null> {
        return this.createWritePromise(createZScore(key, member));
    }

    /**
     * Computes the union of sorted sets given by the specified `keys` and stores the result in `destination`.
     * If `destination` already exists, it is overwritten. Otherwise, a new sorted set will be created.
     * To get the result directly, see {@link zunionWithScores}.
     *
     * @see {@link https://valkey.io/commands/zunionstore/|valkey.io} for details.
     * @remarks When in cluster mode, `destination` and all keys in `keys` both must map to the same hash slot.
     *
     * @param destination - The key of the destination sorted set.
     * @param keys - The keys of the sorted sets with possible formats:
     *  - `GlideString[]` - for keys only.
     *  - `KeyWeight[]` - for weighted keys with their score multipliers.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `aggregationType`: the aggregation strategy to apply when combining the scores of elements. See {@link AggregationType}.
     *   If `aggregationType` is not specified, defaults to `AggregationType.SUM`.
     * @returns The number of elements in the resulting sorted set stored at `destination`.
     *
     * @example
     * ```typescript
     * await client.zadd("key1", {"member1": 10.5, "member2": 8.2})
     * await client.zadd("key2", {"member1": 9.5})
     *
     * // use `zunionstore` with default aggregation and weights
     * console.log(await client.zunionstore("my_sorted_set", ["key1", "key2"]))
     * // Output: 2
     * // Indicates that the sorted set `my_sorted_set` contains two elements.
     *
     * console.log(await client.zrangeWithScores("my_sorted_set", {start: 0, stop: -1}))
     * // Output: {'member1': 20, 'member2': 8.2}
     * // `member1` is now stored in `my_sorted_set` with score of 20 and `member2` with score of 8.2.
     * ```
     *
     * @example
     * ```typescript
     * // use `zunionstore` with default weights
     * console.log(await client.zunionstore("my_sorted_set", ["key1", "key2"], { aggregationType: AggregationType.MAX }))
     * // Output: 2
     * // Indicates that the sorted set `my_sorted_set` contains two elements, and each score is the maximum score between the sets.
     *
     * console.log(await client.zrangeWithScores("my_sorted_set", {start: 0, stop: -1}))
     * // Output: {'member1': 10.5, 'member2': 8.2}
     * // `member1` is now stored in `my_sorted_set` with score of 10.5 and `member2` with score of 8.2.
     * ```
     *
     * @example
     * ```typescript
     * // use `zunionstore` with default aggregation
     * console.log(await client.zunionstore("my_sorted_set", [["key1", 2], ["key2", 1]]))
     * // Output: 2
     * // Indicates that the sorted set `my_sorted_set` contains two elements
     *
     * console.log(await client.zrangeWithScores("my_sorted_set", {start: 0, stop: -1}))
     * // Output: { member2: 16.4, member1: 30.5 }
     * ```
     */
    public async zunionstore(
        destination: GlideString,
        keys: GlideString[] | KeyWeight[],
        options?: { aggregationType?: AggregationType },
    ): Promise<number> {
        return this.createWritePromise(
            createZUnionStore(destination, keys, options?.aggregationType),
        );
    }

    /**
     * Returns the scores associated with the specified `members` in the sorted set stored at `key`.
     *
     * @see {@link https://valkey.io/commands/zmscore/|valkey.io} for more details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param key - The key of the sorted set.
     * @param members - A list of members in the sorted set.
     * @returns An `array` of scores corresponding to `members`.
     * If a member does not exist in the sorted set, the corresponding value in the list will be `null`.
     *
     * @example
     * ```typescript
     * const result = await client.zmscore("zset1", ["member1", "non_existent_member", "member2"]);
     * console.log(result); // Output: [1.0, null, 2.0]
     * // `member1` has a score of 1.0, `non_existent_member` does not exist in the sorted set, and `member2` has a score of 2.0.
     * ```
     */
    public async zmscore(
        key: GlideString,
        members: GlideString[],
    ): Promise<(number | null)[]> {
        return this.createWritePromise(createZMScore(key, members));
    }

    /**
     * Returns the number of members in the sorted set stored at `key` with scores between `minScore` and `maxScore`.
     *
     * @see {@link https://valkey.io/commands/zcount/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param minScore - The minimum score to count from. Can be positive/negative infinity, or specific score and inclusivity.
     * @param maxScore - The maximum score to count up to. Can be positive/negative infinity, or specific score and inclusivity.
     * @returns The number of members in the specified score range.
     * If `key` does not exist, it is treated as an empty sorted set, and the command returns `0`.
     * If `minScore` is greater than `maxScore`, `0` is returned.
     *
     * @example
     * ```typescript
     * // Example usage of the zcount method to count members in a sorted set within a score range
     * const result = await client.zcount("my_sorted_set", { value: 5.0, isInclusive: true }, InfBoundary.PositiveInfinity);
     * console.log(result);
     * // Output: 2
     * // Indicates that there are 2 members with scores between 5.0 (inclusive) and +inf in the sorted set `my_sorted_set`.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of the zcount method to count members in a sorted set within a score range
     * const result = await client.zcount("my_sorted_set", { value: 5.0, isInclusive: true }, { value: 10.0, isInclusive: false });
     * console.log(result);
     * // Output: 1
     * // Indicates that there is one member with score between 5.0 (inclusive) and 10.0 (exclusive) in the sorted set `my_sorted_set`.
     * ```
     */
    public async zcount(
        key: GlideString,
        minScore: Boundary<number>,
        maxScore: Boundary<number>,
    ): Promise<number> {
        return this.createWritePromise(createZCount(key, minScore, maxScore));
    }

    /**
     * Returns the specified range of elements in the sorted set stored at `key`.
     * `ZRANGE` can perform different types of range queries: by index (rank), by the score, or by lexicographical order.
     *
     * To get the elements with their scores, see {@link zrangeWithScores}.
     *
     * @see {@link https://valkey.io/commands/zrange/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param rangeQuery - The range query object representing the type of range query to perform.
     * - For range queries by index (rank), use {@link RangeByIndex}.
     * - For range queries by lexicographical order, use {@link RangeByLex}.
     * - For range queries by score, use {@link RangeByScore}.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `reverse`: if `true`, reverses the sorted set, with index `0` as the element with the highest score.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns A list of elements within the specified range.
     * If `key` does not exist, it is treated as an empty sorted set, and the command returns an empty array.
     *
     * @example
     * ```typescript
     * // Example usage of zrange method to retrieve all members of a sorted set in ascending order
     * const result = await client.zrange("my_sorted_set", { start: 0, end: -1 });
     * console.log(result1);
     * // Output: ['member1', 'member2', 'member3']
     * // All members in ascending order
     * ```
     * @example
     * ```typescript
     * // Example usage of zrange method to retrieve members within a score range in descending order
     * const result = await client.zrange("my_sorted_set", {
     *              start: { value: 3, isInclusive: false },
     *              end: InfBoundary.NegativeInfinity,
     *              type: "byScore",
     *           }, { reverse: true });
     * console.log(result);
     * // Output: ['member2', 'member1']
     * // Members with scores within the range of negative infinity to 3, in descending order
     * ```
     */
    public async zrange(
        key: GlideString,
        rangeQuery: RangeByScore | RangeByLex | RangeByIndex,
        options?: { reverse?: boolean } & DecoderOption,
    ): Promise<GlideString[]> {
        return this.createWritePromise(
            createZRange(key, rangeQuery, options?.reverse),
            options,
        );
    }

    /**
     * Returns the specified range of elements with their scores in the sorted set stored at `key`.
     * Similar to {@link ZRange} but with a `WITHSCORE` flag.
     *
     * @see {@link https://valkey.io/commands/zrange/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param rangeQuery - The range query object representing the type of range query to perform.
     * - For range queries by index (rank), use {@link RangeByIndex}.
     * - For range queries by score, use {@link RangeByScore}.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `reverse`: if `true`, reverses the sorted set, with index `0` as the element with the highest score.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns A list of elements and their scores within the specified range.
     * If `key` does not exist, it is treated as an empty sorted set, and the command returns an empty list.
     *
     * @example
     * ```typescript
     * // Example usage of zrangeWithScores method to retrieve members within a score range with their scores
     * const result = await client.zrangeWithScores("my_sorted_set", {
     *              start: { value: 10, isInclusive: false },
     *              end: { value: 20, isInclusive: false },
     *              type: "byScore",
     *           });
     * console.log(result);
     * // Output: [{ element: 'member1', score: 10.5 }, { element: 'member2', score: 15.2 }]
     * // Members with scores between 10 and 20 with their scores
     * ```
     * @example
     * ```typescript
     * // Example usage of zrangeWithScores method to retrieve members within a score range with their scores
     * const result = await client.zrangeWithScores("my_sorted_set", {
     *              start: { value: 3, isInclusive: false },
     *              end: InfBoundary.NegativeInfinity,
     *              type: "byScore",
     *           }, { reverse: true });
     * console.log(result);
     * // Output: [{ element: 'member7', score: 1.5 }, { element: 'member4', score: -2.0 }]
     * // Members with scores within the range of negative infinity to 3, with their scores
     * ```
     */
    public async zrangeWithScores(
        key: GlideString,
        rangeQuery: RangeByScore | RangeByIndex,
        options?: { reverse?: boolean } & DecoderOption,
    ): Promise<SortedSetDataType> {
        return this.createWritePromise<GlideRecord<number>>(
            createZRangeWithScores(key, rangeQuery, options?.reverse),
            options,
        ).then(convertGlideRecordForSortedSet);
    }

    /**
     * Stores a specified range of elements from the sorted set at `source`, into a new
     * sorted set at `destination`. If `destination` doesn't exist, a new sorted
     * set is created; if it exists, it's overwritten.
     *
     * @see {@link https://valkey.io/commands/zrangestore/|valkey.io} for more details.
     * @remarks When in cluster mode, `destination` and `source` must map to the same hash slot.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param destination - The key for the destination sorted set.
     * @param source - The key of the source sorted set.
     * @param rangeQuery - The range query object representing the type of range query to perform.
     * - For range queries by index (rank), use {@link RangeByIndex}.
     * - For range queries by lexicographical order, use {@link RangeByLex}.
     * - For range queries by score, use {@link RangeByScore}.
     * @param reverse - If `true`, reverses the sorted set, with index `0` as the element with the highest score.
     * @returns The number of elements in the resulting sorted set.
     *
     * @example
     * ```typescript
     * // Example usage of zrangeStore to retrieve and store all members of a sorted set in ascending order.
     * const result = await client.zrangeStore("destination_key", "my_sorted_set", { start: 0, end: -1 });
     * console.log(result);
     * // Output: 7
     * // `destination_key` contains a sorted set with the 7 members from `my_sorted_set`.
     * ```
     * @example
     * ```typescript
     * // Example usage of zrangeStore method to retrieve members within a score range in ascending order and store in `destination_key`
     * const result = await client.zrangeStore("destination_key", "my_sorted_set", {
     *              start: InfBoundary.NegativeInfinity,
     *              end: { value: 3, isInclusive: false },
     *              type: "byScore",
     *           });
     * console.log(result);
     * // Output: 5
     * // Stores 5 members with scores within the range of negative infinity to 3, in ascending order, in `destination_key`.
     * ```
     */
    public async zrangeStore(
        destination: GlideString,
        source: GlideString,
        rangeQuery: RangeByScore | RangeByLex | RangeByIndex,
        reverse = false,
    ): Promise<number> {
        return this.createWritePromise(
            createZRangeStore(destination, source, rangeQuery, reverse),
        );
    }

    /**
     * Computes the intersection of sorted sets given by the specified `keys` and stores the result in `destination`.
     * If `destination` already exists, it is overwritten. Otherwise, a new sorted set will be created.
     * To get the result directly, see {@link zinterWithScores}.
     *
     * @see {@link https://valkey.io/commands/zinterstore/|valkey.io} for more details.
     * @remarks When in cluster mode, `destination` and all keys in `keys` must map to the same hash slot.
     *
     * @param destination - The key of the destination sorted set.
     * @param keys - The keys of the sorted sets with possible formats:
     *  - `GlideString[]` - for keys only.
     *  - `KeyWeight[]` - for weighted keys with score multipliers.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `aggregationType`: the aggregation strategy to apply when combining the scores of elements. See {@link AggregationType}.
     *   If `aggregationType` is not specified, defaults to `AggregationType.SUM`.
     * @returns The number of elements in the resulting sorted set stored at `destination`.
     *
     * @example
     * ```typescript
     * await client.zadd("key1", {"member1": 10.5, "member2": 8.2})
     * await client.zadd("key2", {"member1": 9.5})
     *
     * // use `zinterstore` with default aggregation and weights
     * console.log(await client.zinterstore("my_sorted_set", ["key1", "key2"]))
     * // Output: 1
     * // Indicates that the sorted set `my_sorted_set` contains one element.
     *
     * console.log(await client.zrangeWithScores("my_sorted_set", {start: 0, end: -1}))
     * // Output: {'member1': 20}
     * // `member1` is now stored in `my_sorted_set` with score of 20.
     *
     * // use `zinterstore` with default weights
     * console.log(await client.zinterstore("my_sorted_set", ["key1", "key2"] , { aggregationType: AggregationType.MAX }))
     * // Output: 1
     * // Indicates that the sorted set `my_sorted_set` contains one element, and it's score is the maximum score between the sets.
     *
     * console.log(await client.zrangeWithScores("my_sorted_set", {start: 0, end: -1}))
     * // Output: {'member1': 10.5}
     * // `member1` is now stored in `my_sorted_set` with score of 10.5.
     * ```
     */
    public async zinterstore(
        destination: GlideString,
        keys: GlideString[] | KeyWeight[],
        options?: { aggregationType?: AggregationType },
    ): Promise<number> {
        return this.createWritePromise(
            createZInterstore(destination, keys, options?.aggregationType),
        );
    }

    /**
     * Computes the intersection of sorted sets given by the specified `keys` and returns a list of intersecting elements.
     * To get the scores as well, see {@link zinterWithScores}.
     * To store the result in a key as a sorted set, see {@link zinterStore}.
     *
     * @remarks When in cluster mode, all keys in `keys` must map to the same hash slot.
     *
     * @remarks Since Valkey version 6.2.0.
     *
     * @see {@link https://valkey.io/commands/zinter/|valkey.io} for details.
     *
     * @param keys - The keys of the sorted sets.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns The resulting array of intersecting elements.
     *
     * @example
     * ```typescript
     * await client.zadd("key1", {"member1": 10.5, "member2": 8.2});
     * await client.zadd("key2", {"member1": 9.5});
     * const result = await client.zinter(["key1", "key2"]);
     * console.log(result);
     * // Output: ['member1']
     * // The intersecting element for the sorted sets at `key1` and `key2`.
     * ```
     */
    public async zinter(
        keys: GlideString[],
        options?: DecoderOption,
    ): Promise<GlideString[]> {
        return this.createWritePromise(createZInter(keys), options);
    }

    /**
     * Computes the intersection of sorted sets given by the specified `keys` and returns a list of intersecting elements with scores.
     * To get the elements only, see {@link zinter}.
     * To store the result in a key as a sorted set, see {@link zinterStore}.
     *
     * @remarks When in cluster mode, all keys in `keys` must map to the same hash slot.
     *
     * @see {@link https://valkey.io/commands/zinter/|valkey.io} for details.
     *
     * @remarks Since Valkey version 6.2.0.
     *
     * @param keys - The keys of the sorted sets with possible formats:
     *  - `GlideString[]` - for keys only.
     *  - `KeyWeight[]` - for weighted keys with score multipliers.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `aggregationType`: the aggregation strategy to apply when combining the scores of elements.
     *     If `aggregationType` is not specified, defaults to `AggregationType.SUM`. See {@link AggregationType}.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns A list of elements and their scores representing the intersection of the sorted sets.
     * If a key does not exist, it is treated as an empty sorted set, and the command returns an empty result.
     *
     * @example
     * ```typescript
     * await client.zadd("key1", {"member1": 10.5, "member2": 8.2});
     * await client.zadd("key2", {"member1": 9.5});
     * const result1 = await client.zinterWithScores(["key1", "key2"]);
     * console.log(result1);
     * // Output: [{ element: 'member1', score: 20 }]
     * // `member1` with score of 20 is the result
     *
     * const result2 = await client.zinterWithScores(["key1", "key2"], AggregationType.MAX)
     * console.log(result2);
     * // Output: [{ element: 'member1', score: 10.5 }]
     * // `member1` with score of 10.5 is the result
     * ```
     */
    public async zinterWithScores(
        keys: GlideString[] | KeyWeight[],
        options?: { aggregationType?: AggregationType } & DecoderOption,
    ): Promise<SortedSetDataType> {
        return this.createWritePromise<GlideRecord<number>>(
            createZInter(keys, options?.aggregationType, true),
            options,
        ).then(convertGlideRecordForSortedSet);
    }

    /**
     * Computes the union of sorted sets given by the specified `keys` and returns a list of union elements.
     *
     * To get the scores as well, see {@link zunionWithScores}.
     * To store the result in a key as a sorted set, see {@link zunionStore}.
     *
     * @see {@link https://valkey.io/commands/zunion/|valkey.io} for details.
     * @remarks When in cluster mode, all keys in `keys` must map to the same hash slot.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param keys - The keys of the sorted sets.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns The resulting array of union elements.
     *
     * @example
     * ```typescript
     * await client.zadd("key1", {"member1": 10.5, "member2": 8.2});
     * await client.zadd("key2", {"member1": 9.5});
     * const result = await client.zunion(["key1", "key2"]);
     * console.log(result);
     * // Output: ['member1', 'member2']
     * // Union elements from sets stored at keys `key1` and `key2`.
     * ```
     */
    public async zunion(
        keys: GlideString[],
        options?: DecoderOption,
    ): Promise<GlideString[]> {
        return this.createWritePromise(createZUnion(keys), options);
    }

    /**
     * Computes the intersection of sorted sets given by the specified `keys` and returns a list of union elements with scores.
     * To get the elements only, see {@link zunion}.
     *
     * @remarks When in cluster mode, all keys in `keys` must map to the same hash slot.
     *
     * @see {@link https://valkey.io/commands/zunion/|valkey.io} for details.
     *
     * @remarks Since Valkey version 6.2.0.
     *
     * @param keys - The keys of the sorted sets with possible formats:
     *  - string[] - for keys only.
     *  - KeyWeight[] - for weighted keys with score multipliers.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `aggregationType`: the aggregation strategy to apply when combining the scores of elements.
     *     If `aggregationType` is not specified, defaults to `AggregationType.SUM`. See {@link AggregationType}.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns A list of elements and their scores representing the intersection of the sorted sets.
     *
     * @example
     * ```typescript
     * await client.zadd("key1", {"member1": 10.5, "member2": 8.2});
     * await client.zadd("key2", {"member1": 9.5});
     * const result1 = await client.zunionWithScores(["key1", "key2"]);
     * console.log(result1);
     * // Output: [{ element: 'member1', score: 20 }, { element: 'member2', score: 8.2 }]
     * // A list of union elements with scores.
     *
     * const result2 = await client.zunionWithScores(["key1", "key2"], "MAX");
     * console.log(result2);
     * // Output: [{ element: 'member1', score: 10.5}, { element: 'member2', score: 8.2 }]
     * // A list of union elements with score. The score of the common element in both sets (`member1`), is the maximum of scores(10.5) for that member from both sets.
     * ```
     */
    public async zunionWithScores(
        keys: GlideString[] | KeyWeight[],
        options?: { aggregationType?: AggregationType } & DecoderOption,
    ): Promise<SortedSetDataType> {
        return this.createWritePromise<GlideRecord<number>>(
            createZUnion(keys, options?.aggregationType, true),
            options,
        ).then(convertGlideRecordForSortedSet);
    }

    /**
     * Returns a random member from the sorted set stored at `key`.
     *
     * @see {@link https://valkey.io/commands/zrandmember/|valkey.io} for more details.
     *
     * @param keys - The key of the sorted set.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A string representing a random member from the sorted set.
     *     If the sorted set does not exist or is empty, the response will be `null`.
     *
     * @example
     * ```typescript
     * const payload1 = await client.zrandmember("mySortedSet");
     * console.log(payload1);
     * // Output: "Glide"
     * // A random member from the set
     * ```
     *
     * @example
     * ```typescript
     * const payload2 = await client.zrandmember("nonExistingSortedSet");
     * console.log(payload2);
     * // Output: null
     * // Since the sorted set does not exist.
     * ```
     */
    public async zrandmember(
        key: GlideString,
        options?: DecoderOption,
    ): Promise<GlideString | null> {
        return this.createWritePromise(createZRandMember(key), options);
    }

    /**
     * Returns random members from the sorted set stored at `key`.
     *
     * @see {@link https://valkey.io/commands/zrandmember/|valkey.io} for more details.
     *
     * @param keys - The key of the sorted set.
     * @param count - The number of members to return.
     *     If `count` is positive, returns unique members.
     *     If negative, allows for duplicates.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns An `array` of members from the sorted set.
     *     If the sorted set does not exist or is empty, the response will be an empty `array`.
     *
     * @example
     * ```typescript
     * const payload1 = await client.zrandmemberWithCount("mySortedSet", -3);
     * console.log(payload1);
     * // Output: ["Glide", "GLIDE", "node"]
     * // Returns 3 random members from the sorted set, with duplicates allowed.
     * ```
     *
     * @example
     * ```typescript
     * const payload2 = await client.zrandmemberWithCount("nonExistingKey", 3);
     * console.log(payload1);
     * // Output: []
     * // Since the sorted set does not exist.
     * ```
     */
    public async zrandmemberWithCount(
        key: GlideString,
        count: number,
        options?: DecoderOption,
    ): Promise<GlideString[]> {
        return this.createWritePromise(createZRandMember(key, count), options);
    }

    /**
     * Returns random members with scores from the sorted set stored at `key`.
     *
     * @see {@link https://valkey.io/commands/zrandmember/|valkey.io} for more details.
     *
     * @param keys - The key of the sorted set.
     * @param count - The number of members to return.
     *     If `count` is positive, returns unique members.
     *     If negative, allows for duplicates.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A list of {@link KeyWeight} tuples, which store member names and their respective scores.
     *     If the sorted set does not exist or is empty, the response will be an empty `array`.
     *
     * @example
     * ```typescript
     * const payload1 = await client.zrandmemberWithCountWithScore("mySortedSet", -3);
     * console.log(payload1);
     * // Output: [["Glide", 1.0], ["GLIDE", 1.0], ["node", 2.0]]
     * // Returns 3 random members with scores, with duplicates allowed.
     * ```
     *
     * @example
     * ```typescript
     * const payload2 = await client.zrandmemberWithCountWithScore("nonExistingKey", 3);
     * console.log(payload1);
     * // Output: []
     * // Since the sorted set does not exist.
     * ```
     */
    public async zrandmemberWithCountWithScores(
        key: GlideString,
        count: number,
        options?: DecoderOption,
    ): Promise<KeyWeight[]> {
        return this.createWritePromise(
            createZRandMember(key, count, true),
            options,
        );
    }

    /**
     * Returns the length of the string value stored at `key`.
     *
     * @see {@link https://valkey.io/commands/strlen/|valkey.io} for more details.
     *
     * @param key - The key to check its length.
     * @returns The length of the string value stored at key
     * If `key` does not exist, it is treated as an empty string, and the command returns `0`.
     *
     * @example
     * ```typescript
     * // Example usage of strlen method with an existing key
     * await client.set("key", "GLIDE");
     * const length1 = await client.strlen("key");
     * console.log(length1);
     * // Output: 5
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of strlen method with a non-existing key
     * const length2 = await client.strlen("non_existing_key");
     * console.log(length2);
     * // Output: 0
     * ```
     */
    public async strlen(key: GlideString): Promise<number> {
        return this.createWritePromise(createStrlen(key));
    }

    /**
     * Returns the string representation of the type of the value stored at `key`.
     *
     * @see {@link https://valkey.io/commands/type/|valkey.io} for more details.
     *
     * @param key - The `key` to check its data type.
     * @returns If the `key` exists, the type of the stored value is returned. Otherwise, a "none" string is returned.
     *
     * @example
     * ```typescript
     * // Example usage of type method with a string value
     * await client.set("key", "value");
     * const type = await client.type("key");
     * console.log(type);
     * // Output: 'string'
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of type method with a list
     * await client.lpush("key", ["value"]);
     * const type = await client.type("key");
     * console.log(type);
     * // Output: 'list'
     * ```
     */
    public async type(key: GlideString): Promise<string> {
        return this.createWritePromise(createType(key), {
            decoder: Decoder.String,
        });
    }

    /**
     * Removes and returns the members with the lowest scores from the sorted set stored at `key`.
     * If `count` is provided, up to `count` members with the lowest scores are removed and returned.
     * Otherwise, only one member with the lowest score is removed and returned.
     *
     * @see {@link https://valkey.io/commands/zpopmin/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `count`: the maximum number of popped elements. If not specified, pops one member.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns A list of the removed members and their scores, ordered from the one with the lowest score to the one with the highest.
     * If `key` doesn't exist, it will be treated as an empty sorted set and the command returns an empty map.
     * If `count` is higher than the sorted set's cardinality, returns all members and their scores.
     *
     * @example
     * ```typescript
     * // Example usage of zpopmin method to remove and return the member with the lowest score from a sorted set
     * const result = await client.zpopmin("my_sorted_set");
     * console.log(result);
     * // Output: [{ element: 'member1', score: 5.0 }]
     * // `member1` with a score of 5.0 has been removed from the sorted set
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of zpopmin method to remove and return multiple members with the lowest scores from a sorted set
     * const result = await client.zpopmin("my_sorted_set", 2);
     * console.log(result);
     * // Output:
     * // [
     * //     { element: 'member3', score: 7.5 },
     * //     { element: 'member2', score: 8.0 }
     * // ]
     * // `member3` with a score of 7.5 and `member2` with a score of 8.0 have been removed from the sorted set
     * ```
     */
    public async zpopmin(
        key: GlideString,
        options?: { count?: number } & DecoderOption,
    ): Promise<SortedSetDataType> {
        return this.createWritePromise<GlideRecord<number>>(
            createZPopMin(key, options?.count),
            options,
        ).then(convertGlideRecordForSortedSet);
    }

    /**
     * Blocks the connection until it removes and returns a member with the lowest score from the
     * first non-empty sorted set, with the given `key` being checked in the order they
     * are provided.
     * `BZPOPMIN` is the blocking variant of {@link zpopmin}.
     *
     * @see {@link https://valkey.io/commands/bzpopmin/|valkey.io} for more details.
     * @remarks When in cluster mode, `keys` must map to the same hash slot.
     *
     * @param keys - The keys of the sorted sets.
     * @param timeout - The number of seconds to wait for a blocking operation to complete. A value of
     *     `0` will block indefinitely. Since 6.0.0: timeout is interpreted as a double instead of an integer.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns An `array` containing the key where the member was popped out, the member, itself, and the member score.
     *     If no member could be popped and the `timeout` expired, returns `null`.
     *
     * @example
     * ```typescript
     * const data = await client.bzpopmin(["zset1", "zset2"], 0.5);
     * console.log(data);
     * // Output: ["zset1", "a", 2];
     * // Pops the member `a` with score 2 stored at key `zset1`.
     * ```
     */
    public async bzpopmin(
        keys: GlideString[],
        timeout: number,
        options?: DecoderOption,
    ): Promise<[GlideString, GlideString, number] | null> {
        return this.createWritePromise(createBZPopMin(keys, timeout), options);
    }

    /**
     * Removes and returns the members with the highest scores from the sorted set stored at `key`.
     * If `count` is provided, up to `count` members with the highest scores are removed and returned.
     * Otherwise, only one member with the highest score is removed and returned.
     *
     * @see {@link https://valkey.io/commands/zpopmax/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `count`: the maximum number of popped elements. If not specified, pops one member.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns A list of the removed members and their scores, ordered from the one with the highest score to the one with the lowest.
     * If `key` doesn't exist, it will be treated as an empty sorted set and the command returns an empty map.
     * If `count` is higher than the sorted set's cardinality, returns all members and their scores, ordered from highest to lowest.
     *
     * @example
     * ```typescript
     * // Example usage of zpopmax method to remove and return the member with the highest score from a sorted set
     * const result = await client.zpopmax("my_sorted_set");
     * console.log(result);
     * // Output: [{ element: 'member1', score: 10.0 }]
     * // `member1` with a score of 10.0 has been removed from the sorted set
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of zpopmax method to remove and return multiple members with the highest scores from a sorted set
     * const result = await client.zpopmax("my_sorted_set", 2);
     * console.log(result);
     * // Output:
     * // [
     * //     { element: 'member3', score: 7.5 },
     * //     { element: 'member2', score: 8.0 }
     * // ]
     * // `member3` with a score of 7.5 and `member2` with a score of 8.0 have been removed from the sorted set
     * ```
     */
    public async zpopmax(
        key: GlideString,
        options?: { count?: number } & DecoderOption,
    ): Promise<SortedSetDataType> {
        return this.createWritePromise<GlideRecord<number>>(
            createZPopMax(key, options?.count),
            options,
        ).then(convertGlideRecordForSortedSet);
    }

    /**
     * Blocks the connection until it removes and returns a member with the highest score from the
     * first non-empty sorted set, with the given `key` being checked in the order they
     * are provided.
     * `BZPOPMAX` is the blocking variant of {@link zpopmax}.
     *
     * @see {@link https://valkey.io/commands/zpopmax/|valkey.io} for more details.
     * @remarks When in cluster mode, `keys` must map to the same hash slot.
     *
     * @param keys - The keys of the sorted sets.
     * @param timeout - The number of seconds to wait for a blocking operation to complete. A value of
     *     `0` will block indefinitely. Since 6.0.0: timeout is interpreted as a double instead of an integer.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns An `array` containing the key where the member was popped out, the member, itself, and the member score.
     *     If no member could be popped and the `timeout` expired, returns `null`.
     *
     * @example
     * ```typescript
     * const data = await client.bzpopmax(["zset1", "zset2"], 0.5);
     * console.log(data);
     * // Output: ["zset1", "c", 2];
     * ```
     */
    public async bzpopmax(
        keys: GlideString[],
        timeout: number,
        options?: DecoderOption,
    ): Promise<[GlideString, GlideString, number] | null> {
        return this.createWritePromise(createBZPopMax(keys, timeout), options);
    }

    /**
     * Returns the remaining time to live of `key` that has a timeout, in milliseconds.
     *
     * @see {@link https://valkey.io/commands/pttl/|valkey.io} for more details.
     *
     * @param key - The key to return its timeout.
     * @returns TTL in milliseconds, `-2` if `key` does not exist, `-1` if `key` exists but has no associated expire.
     *
     * @example
     * ```typescript
     * // Example usage of pttl method with an existing key
     * const result = await client.pttl("my_key");
     * console.log(result);
     * // Output: 5000
     * // Indicates that the key `my_key` has a remaining time to live of 5000 milliseconds.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of pttl method with a non-existing key
     * const result = await client.pttl("non_existing_key");
     * console.log(result);
     * // Output: -2
     * // Indicates that the key `non_existing_key` does not exist.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of pttl method with an exisiting key that has no associated expire.
     * const result = await client.pttl("key");
     * console.log(result);
     * // Output: -1
     * // Indicates that the key `key` has no associated expire.
     * ```
     */
    public async pttl(key: GlideString): Promise<number> {
        return this.createWritePromise(createPTTL(key));
    }

    /**
     * Removes all elements in the sorted set stored at `key` with rank between `start` and `end`.
     * Both `start` and `end` are zero-based indexes with 0 being the element with the lowest score.
     * These indexes can be negative numbers, where they indicate offsets starting at the element with the highest score.
     *
     * @see {@link https://valkey.io/commands/zremrangebyrank/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param start - The starting point of the range.
     * @param end - The end of the range.
     * @returns The number of members removed.
     * If `start` exceeds the end of the sorted set, or if `start` is greater than `end`, 0 returned.
     * If `end` exceeds the actual end of the sorted set, the range will stop at the actual end of the sorted set.
     * If `key` does not exist 0 will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of zremRangeByRank method
     * const result = await client.zremRangeByRank("my_sorted_set", 0, 2);
     * console.log(result);
     * // Output: 3
     * // Indicates that three elements have been removed from the sorted set `my_sorted_set` between ranks 0 and 2.
     * ```
     */
    public async zremRangeByRank(
        key: GlideString,
        start: number,
        end: number,
    ): Promise<number> {
        return this.createWritePromise(createZRemRangeByRank(key, start, end));
    }

    /**
     * Removes all elements in the sorted set stored at `key` with lexicographical order between `minLex` and `maxLex`.
     *
     * @see {@link https://valkey.io/commands/zremrangebylex/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param minLex - The minimum lex to count from. Can be negative infinity, or a specific lex and inclusivity.
     * @param maxLex - The maximum lex to count up to. Can be positive infinity, or a specific lex and inclusivity.
     * @returns The number of members removed.
     * If `key` does not exist, it is treated as an empty sorted set, and the command returns 0.
     * If `minLex` is greater than `maxLex`, 0 is returned.
     *
     * @example
     * ```typescript
     * // Example usage of zremRangeByLex method to remove members from a sorted set based on lexicographical order range
     * const result = await client.zremRangeByLex("my_sorted_set", { value: "a", isInclusive: false }, { value: "e" });
     * console.log(result);
     * // Output: 4
     * // Indicates that 4 members, with lexicographical values ranging from `a` (exclusive) to `e` (inclusive), have been removed from `my_sorted_set`.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of zremRangeByLex method when the sorted set does not exist
     * const result = await client.zremRangeByLex("non_existing_sorted_set", InfBoundary.NegativeInfinity, { value: "e" });
     * console.log(result);
     * // Output: 0
     * // Indicates that no elements were removed.
     * ```
     */
    public async zremRangeByLex(
        key: GlideString,
        minLex: Boundary<GlideString>,
        maxLex: Boundary<GlideString>,
    ): Promise<number> {
        return this.createWritePromise(
            createZRemRangeByLex(key, minLex, maxLex),
        );
    }

    /**
     * Removes all elements in the sorted set stored at `key` with a score between `minScore` and `maxScore`.
     *
     * @see {@link https://valkey.io/commands/zremrangebyscore/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param minScore - The minimum score to remove from. Can be negative infinity, or specific score and inclusivity.
     * @param maxScore - The maximum score to remove to. Can be positive infinity, or specific score and inclusivity.
     * @returns The number of members removed.
     * If `key` does not exist, it is treated as an empty sorted set, and the command returns 0.
     * If `minScore` is greater than `maxScore`, 0 is returned.
     *
     * @example
     * ```typescript
     * // Example usage of zremRangeByScore method to remove members from a sorted set based on score range
     * const result = await client.zremRangeByScore("my_sorted_set", { value: 5.0, isInclusive: true }, InfBoundary.PositiveInfinity);
     * console.log(result);
     * // Output: 2
     * // Indicates that 2 members with scores between 5.0 (inclusive) and +inf have been removed from the sorted set `my_sorted_set`.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of zremRangeByScore method when the sorted set does not exist
     * const result = await client.zremRangeByScore("non_existing_sorted_set", { value: 5.0, isInclusive: true }, { value: 10.0, isInclusive: false });
     * console.log(result);
     * // Output: 0
     * // Indicates that no members were removed as the sorted set `non_existing_sorted_set` does not exist.
     * ```
     */
    public async zremRangeByScore(
        key: GlideString,
        minScore: Boundary<number>,
        maxScore: Boundary<number>,
    ): Promise<number> {
        return this.createWritePromise(
            createZRemRangeByScore(key, minScore, maxScore),
        );
    }

    /**
     * Returns the number of members in the sorted set stored at 'key' with scores between 'minLex' and 'maxLex'.
     *
     * @see {@link https://valkey.io/commands/zlexcount/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param minLex - The minimum lex to count from. Can be negative infinity, or a specific lex and inclusivity.
     * @param maxLex - The maximum lex to count up to. Can be positive infinity, or a specific lex and inclusivity.
     * @returns The number of members in the specified lex range.
     * If 'key' does not exist, it is treated as an empty sorted set, and the command returns '0'.
     * If maxLex is less than minLex, '0' is returned.
     *
     * @example
     * ```typescript
     * const result = await client.zlexcount("my_sorted_set", {value: "c"}, InfBoundary.PositiveInfinity);
     * console.log(result);
     * // Output: 2
     * // Indicates that there are 2 members with lex scores between `c` (inclusive) and positive infinity in the sorted set `my_sorted_set`.
     * ```
     *
     * @example
     * ```typescript
     * const result = await client.zlexcount("my_sorted_set", {value: "c"}, {value: "k", isInclusive: false});
     * console.log(result);
     * // Output: 1
     * // Indicates that there is one member with a lex score between `c` (inclusive) and `k` (exclusive) in the sorted set `my_sorted_set`.
     * ```
     */
    public async zlexcount(
        key: GlideString,
        minLex: Boundary<GlideString>,
        maxLex: Boundary<GlideString>,
    ): Promise<number> {
        return this.createWritePromise(createZLexCount(key, minLex, maxLex));
    }

    /**
     * Returns the rank of `member` in the sorted set stored at `key`, with scores ordered from low to high.
     * To get the rank of `member` with its score, see {@link zrankWithScore}.
     *
     * @see {@link https://valkey.io/commands/zrank/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param member - The member whose rank is to be retrieved.
     * @returns The rank of `member` in the sorted set.
     * If `key` doesn't exist, or if `member` is not present in the set, null will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of zrank method to retrieve the rank of a member in a sorted set
     * const result = await client.zrank("my_sorted_set", "member2");
     * console.log(result);
     * // Output: 1
     * // Indicates that `member2` has the second-lowest score in the sorted set `my_sorted_set`.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of zrank method with a non-existing member
     * const result = await client.zrank("my_sorted_set", "non_existing_member");
     * console.log(result);
     * // Output: null
     * // Indicates that `non_existing_member` is not present in the sorted set `my_sorted_set`.
     * ```
     */
    public async zrank(
        key: GlideString,
        member: GlideString,
    ): Promise<number | null> {
        return this.createWritePromise(createZRank(key, member));
    }

    /**
     * Returns the rank of `member` in the sorted set stored at `key` with its score, where scores are ordered from the lowest to highest.
     *
     * @see {@link https://valkey.io/commands/zrank/|valkey.io} for more details.
     * @remarks Since Valkey version 7.2.0.
     *
     * @param key - The key of the sorted set.
     * @param member - The member whose rank is to be retrieved.
     * @returns A list containing the rank and score of `member` in the sorted set.
     * If `key` doesn't exist, or if `member` is not present in the set, null will be returned.
     *
     * @example
     * ```typescript
     * // Example usage of zrank_withscore method to retrieve the rank and score of a member in a sorted set
     * const result = await client.zrank_withscore("my_sorted_set", "member2");
     * console.log(result);
     * // Output: [1, 6.0]
     * // Indicates that `member2` with score 6.0 has the second-lowest score in the sorted set `my_sorted_set`.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of zrank_withscore method with a non-existing member
     * const result = await client.zrank_withscore("my_sorted_set", "non_existing_member");
     * console.log(result);
     * // Output: null
     * // Indicates that `non_existing_member` is not present in the sorted set `my_sorted_set`.
     * ```
     */
    public async zrankWithScore(
        key: GlideString,
        member: GlideString,
    ): Promise<[number, number] | null> {
        return this.createWritePromise(createZRank(key, member, true));
    }

    /**
     * Returns the rank of `member` in the sorted set stored at `key`, where
     * scores are ordered from the highest to lowest, starting from `0`.
     * To get the rank of `member` with its score, see {@link zrevrankWithScore}.
     *
     * @see {@link https://valkey.io/commands/zrevrank/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param member - The member whose rank is to be retrieved.
     * @returns The rank of `member` in the sorted set, where ranks are ordered from high to low based on scores.
     *     If `key` doesn't exist, or if `member` is not present in the set, `null` will be returned.
     *
     * @example
     * ```typescript
     * const result = await client.zrevrank("my_sorted_set", "member2");
     * console.log(result);
     * // Output: 1
     * // Indicates that `member2` has the second-highest score in the sorted set `my_sorted_set`.
     * ```
     */
    public async zrevrank(
        key: GlideString,
        member: GlideString,
    ): Promise<number | null> {
        return this.createWritePromise(createZRevRank(key, member));
    }

    /**
     * Returns the rank of `member` in the sorted set stored at `key` with its
     * score, where scores are ordered from the highest to lowest, starting from `0`.
     *
     * @see {@link https://valkey.io/commands/zrevrank/|valkey.io} for more details.
     * @remarks Since Valkey version 7.2.0.
     *
     * @param key - The key of the sorted set.
     * @param member - The member whose rank is to be retrieved.
     * @returns A list containing the rank and score of `member` in the sorted set, where ranks
     *     are ordered from high to low based on scores.
     *     If `key` doesn't exist, or if `member` is not present in the set, `null` will be returned.
     *
     * @example
     * ```typescript
     * const result = await client.zrevankWithScore("my_sorted_set", "member2");
     * console.log(result);
     * // Output: [1, 6.0]
     * // Indicates that `member2` with score 6.0 has the second-highest score in the sorted set `my_sorted_set`.
     * ```
     */
    public async zrevrankWithScore(
        key: GlideString,
        member: GlideString,
    ): Promise<[number, number] | null> {
        return this.createWritePromise(createZRevRankWithScore(key, member));
    }

    /**
     * Adds an entry to the specified stream stored at `key`. If the `key` doesn't exist, the stream is created.
     *
     * @see {@link https://valkey.io/commands/xadd/|valkey.io} for more details.
     *
     * @param key - The key of the stream.
     * @param values - field-value pairs to be added to the entry.
     * @param options - options detailing how to add to the stream.
     * @param options - (Optional) See {@link StreamAddOptions} and {@link DecoderOption}.
     * @returns The id of the added entry, or `null` if `options.makeStream` is set to `false` and no stream with the matching `key` exists.
     */
    public async xadd(
        key: GlideString,
        values: [GlideString, GlideString][],
        options?: StreamAddOptions & DecoderOption,
    ): Promise<GlideString | null> {
        return this.createWritePromise(
            createXAdd(key, values, options),
            options,
        );
    }

    /**
     * Removes the specified entries by id from a stream, and returns the number of entries deleted.
     *
     * @see {@link https://valkey.io/commands/xdel/|valkey.io} for more details.
     *
     * @param key - The key of the stream.
     * @param ids - An array of entry ids.
     * @returns The number of entries removed from the stream. This number may be less than the number of entries in
     *      `ids`, if the specified `ids` don't exist in the stream.
     *
     * @example
     * ```typescript
     * console.log(await client.xdel("key", ["1538561698944-0", "1538561698944-1"]));
     * // Output: 2
     * // the stream marked 2 entries as deleted.
     * ```
     */
    public async xdel(key: GlideString, ids: string[]): Promise<number> {
        return this.createWritePromise(createXDel(key, ids));
    }

    /**
     * Trims the stream stored at `key` by evicting older entries.
     *
     * @see {@link https://valkey.io/commands/xtrim/|valkey.io} for more details.
     *
     * @param key - the key of the stream
     * @param options - options detailing how to trim the stream.
     * @returns The number of entries deleted from the stream. If `key` doesn't exist, 0 is returned.
     */
    public async xtrim(
        key: GlideString,
        options: StreamTrimOptions,
    ): Promise<number> {
        return this.createWritePromise(createXTrim(key, options));
    }

    /**
     * Reads entries from the given streams.
     *
     * @see {@link https://valkey.io/commands/xread/|valkey.io} for more details.
     *
     * @param keys_and_ids - An object of stream keys and entry IDs to read from.
     * @param options - (Optional) Parameters detailing how to read the stream - see {@link StreamReadOptions} and {@link DecoderOption}.
     * @returns A list of stream keys with a `Record` of stream IDs mapped to an `Array` of entries or `null` if key does not exist.
     *
     * @example
     * ```typescript
     * const streamResults = await client.xread({"my_stream": "0-0", "writers": "0-0"});
     * console.log(result);
     * // Output:
     * // [
     * //     {
     * //         key: "my_stream",                                                                         // Stream key
     * //         value: {                                                                                  // Stream Ids mapped to entries array.
     * //             "1526984818136-0": [["duration", "1532"], ["event-id", "5"], ["user-id", "7782813"]], // Each entry is a key/value tuple.
     * //             "1526999352406-0": [["duration", "812"], ["event-id", "9"], ["user-id", "388234"]],
     * //         }
     * //     },
     * //     {
     * //         key: "writers",
     * //         value: {
     * //             "1526985676425-0": [["name", "Virginia"], ["surname", "Woolf"]],
     * //             "1526985685298-0": [["name", "Jane"], ["surname", "Austen"]],
     * //         }
     * //     }
     * // ]
     * ```
     */
    public async xread(
        keys_and_ids: Record<string, string> | GlideRecord<string>,
        options?: StreamReadOptions & DecoderOption,
    ): Promise<GlideRecord<StreamEntryDataType> | null> {
        return this.createWritePromise<GlideRecord<
            GlideRecord<[GlideString, GlideString][]>
        > | null>(
            createXRead(convertKeysAndEntries(keys_and_ids), options),
            options,
        ).then(
            (res) =>
                res?.map((k) => {
                    return {
                        key: k.key,
                        value: convertGlideRecordToRecord(k.value),
                    };
                }) ?? null,
        );
    }

    /**
     * Reads entries from the given streams owned by a consumer group.
     *
     * @see {@link https://valkey.io/commands/xreadgroup/|valkey.io} for details.
     *
     * @param group - The consumer group name.
     * @param consumer - The group consumer.
     * @param keys_and_ids - An object of stream keys and entry IDs to read from.
     *     Use the special entry ID of `">"` to receive only new messages.
     * @param options - (Optional) Parameters detailing how to read the stream - see {@link StreamReadGroupOptions} and {@link DecoderOption}.
     * @returns A list of stream keys with a `Record` of stream IDs mapped to an `Array` of entries.
     *     Returns `null` if there is no stream that can be served.
     *
     * @example
     * ```typescript
     * const streamResults = await client.xreadgroup("my_group", "my_consumer", {"my_stream": "0-0", "writers_stream": "0-0", "readers_stream", ">"});
     * console.log(result);
     * // Output:
     * // [
     * //     {
     * //         key: "my_stream",
     * //         value: {
     * //             "1526984818136-0": [["duration", "1532"], ["event-id", "5"], ["user-id", "7782813"]],
     * //             "1526999352406-0": [["duration", "812"], ["event-id", "9"], ["user-id", "388234"]],
     * //         }
     * //     },
     * //     {
     * //         key: "writers_stream",
     * //         value: {
     * //             "1526985676425-0": [["name", "Virginia"], ["surname", "Woolf"]],
     * //             "1526985685298-0": null,                                          // entry was deleted
     * //         }
     * //     },
     * //     {
     * //         key: "readers_stream",                                                // stream is empty
     * //         value: {}
     * //     }
     * // ]
     * ```
     */
    public async xreadgroup(
        group: GlideString,
        consumer: GlideString,
        keys_and_ids: Record<string, string> | GlideRecord<string>,
        options?: StreamReadGroupOptions & DecoderOption,
    ): Promise<GlideRecord<
        Record<string, [GlideString, GlideString][] | null>
    > | null> {
        return this.createWritePromise<GlideRecord<
            GlideRecord<[GlideString, GlideString][] | null>
        > | null>(
            createXReadGroup(
                group,
                consumer,
                convertKeysAndEntries(keys_and_ids),
                options,
            ),
            options,
        ).then(
            (res) =>
                res?.map((k) => {
                    return {
                        key: k.key,
                        value: convertGlideRecordToRecord(k.value),
                    };
                }) ?? null,
        );
    }

    /**
     * Returns the number of entries in the stream stored at `key`.
     *
     * @see {@link https://valkey.io/commands/xlen/|valkey.io} for more details.
     *
     * @param key - The key of the stream.
     * @returns The number of entries in the stream. If `key` does not exist, returns `0`.
     *
     * @example
     * ```typescript
     * const numEntries = await client.xlen("my_stream");
     * console.log(numEntries);
     * // Output: 2
     * // `my_stream` contains 2 entries.
     * ```
     */
    public async xlen(key: GlideString): Promise<number> {
        return this.createWritePromise(createXLen(key));
    }

    /**
     * Returns stream message summary information for pending messages matching a given range of IDs.
     *
     * @see {@link https://valkey.io/commands/xpending/|valkey.io} for more details.
     *
     * @param key - The key of the stream.
     * @param group - The consumer group name.
     * @returns An `array` that includes the summary of the pending messages. See example for more details.
     * @example
     * ```typescript
     * console.log(await client.xpending("my_stream", "my_group"));
     * // Output:
     * // [
     * //     42,                            // The total number of pending messages
     * //     "1722643465939-0",             // The smallest ID among the pending messages
     * //     "1722643484626-0",             // The greatest ID among the pending messages
     * //     [                              // A 2D-`array` of every consumer in the group
     * //         [ "consumer1", "10" ],     // with at least one pending message, and the
     * //         [ "consumer2", "32" ],     // number of pending messages it has
     * //     ]
     * // ]
     * ```
     */
    public async xpending(
        key: GlideString,
        group: GlideString,
    ): Promise<[number, GlideString, GlideString, [GlideString, number][]]> {
        return this.createWritePromise(createXPending(key, group));
    }

    /**
     * Returns an extended form of stream message information for pending messages matching a given range of IDs.
     *
     * @see {@link https://valkey.io/commands/xpending/|valkey.io} for more details.
     *
     * @param key - The key of the stream.
     * @param group - The consumer group name.
     * @param options - Additional options to filter entries, see {@link StreamPendingOptions}.
     * @returns A 2D-`array` of 4-tuples containing extended message information. See example for more details.
     *
     * @example
     * ```typescript
     * console.log(await client.xpending("my_stream", "my_group"), {
     *     start: { value: "0-1", isInclusive: true },
     *     end: InfBoundary.PositiveInfinity,
     *     count: 2,
     *     consumer: "consumer1"
     * });
     * // Output:
     * // [
     * //     [
     * //         "1722643465939-0",  // The ID of the message
     * //         "consumer1",        // The name of the consumer that fetched the message and has still to acknowledge it
     * //         174431,             // The number of milliseconds that elapsed since the last time this message was delivered to this consumer
     * //         1                   // The number of times this message was delivered
     * //     ],
     * //     [
     * //         "1722643484626-0",
     * //         "consumer1",
     * //         202231,
     * //         1
     * //     ]
     * // ]
     * ```
     */
    public async xpendingWithOptions(
        key: GlideString,
        group: GlideString,
        options: StreamPendingOptions,
    ): Promise<[GlideString, GlideString, number, number][]> {
        return this.createWritePromise(createXPending(key, group, options));
    }

    /**
     * Returns the list of all consumers and their attributes for the given consumer group of the
     * stream stored at `key`.
     *
     * @see {@link https://valkey.io/commands/xinfo-consumers/|valkey.io} for more details.
     *
     * @param key - The key of the stream.
     * @param group - The consumer group name.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns An `Array` of `Records`, where each mapping contains the attributes
     *     of a consumer for the given consumer group of the stream at `key`.
     *
     * @example
     * ```typescript
     * const result = await client.xinfoConsumers("my_stream", "my_group");
     * console.log(result);
     * // Output:
     * // [
     * //     {
     * //         "name": "Alice",      // The consumer name.
     * //         "pending": 1,         // The number of entries in Pending entries list.
     * //         "idle": 9104628,      // The time passed since last attempted interaction.
     * //         "inactive": 18104698  // The time passed since last successful interaction. Added in Valkey 7.2.0.
     * //     },
     * //     ...
     * // ]
     * ```
     */
    public async xinfoConsumers(
        key: GlideString,
        group: GlideString,
        options?: DecoderOption,
    ): Promise<Record<string, GlideString | number>[]> {
        return this.createWritePromise<GlideRecord<GlideString | number>[]>(
            createXInfoConsumers(key, group),
            options,
        ).then((res) => res.map(convertGlideRecordToRecord));
    }

    /**
     * Returns the list of all consumer groups and their attributes for the stream stored at `key`.
     *
     * @see {@link https://valkey.io/commands/xinfo-groups/|valkey.io} for details.
     *
     * @param key - The key of the stream.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns An array of maps, where each mapping represents the
     *     attributes of a consumer group for the stream at `key`.
     * @example
     * ```typescript
     * const result = await client.xinfoGroups("my_stream");
     * console.log(result);
     * // Output:
     * // [
     * //     {
     * //         "name": "mygroup",                      // The consumer group name.
     * //         "consumers": 2,                         // Number of consumers in the group.
     * //         "pending": 2,                           // The length of the group's pending entry list.
     * //         "last-delivered-id": "1638126030001-0", // The id of the last delivered entry to the consumers.
     * //         "entries-read": 2,                      // The read counter. Added in Valkey 7.0.0.
     * //         "lag": 0                                // The number of entries that are still waiting to be delivered. Added in Valkey 7.0.0.
     * //     },
     * //     {
     * //         "name": "some-other-group",
     * //         "consumers": 1,
     * //         "pending": 0,
     * //         "last-delivered-id": "0-0",
     * //         "entries-read": null,                    // Added in Valkey 7.0.0.
     * //         "lag": 1                                 // Added in Valkey 7.0.0.
     * //     }
     * // ]
     * ```
     */
    public async xinfoGroups(
        key: GlideString,
        options?: DecoderOption,
    ): Promise<Record<string, GlideString | number | null>[]> {
        return this.createWritePromise<
            GlideRecord<GlideString | number | null>[]
        >(createXInfoGroups(key), options).then((res) =>
            res.map(convertGlideRecordToRecord),
        );
    }

    /**
     * Changes the ownership of a pending message.
     *
     * @see {@link https://valkey.io/commands/xclaim/|valkey.io} for more details.
     *
     * @param key - The key of the stream.
     * @param group - The consumer group name.
     * @param consumer - The group consumer.
     * @param minIdleTime - The minimum idle time for the message to be claimed.
     * @param ids - An array of entry ids.
     * @param options - (Optional) See {@link StreamClaimOptions} and {@link DecoderOption}.
     * @returns A `Record` of message entries that are claimed by the consumer.
     *
     * @example
     * ```typescript
     * const result = await client.xclaim("myStream", "myGroup", "myConsumer", 42,
     *     ["1-0", "2-0", "3-0"], { idle: 500, retryCount: 3, isForce: true });
     * console.log(result);
     * // Output:
     * // {
     * //     "2-0": [                    // Stream Entry id
     * //         ["duration", "1532"],   // Entry data tuple containing the field and associated value.
     * //         ["event-id", "5"],
     * //         ["user-id", "7782813"]
     * //     ]
     * // }
     * ```
     */
    public async xclaim(
        key: GlideString,
        group: GlideString,
        consumer: GlideString,
        minIdleTime: number,
        ids: string[],
        options?: StreamClaimOptions & DecoderOption,
    ): Promise<StreamEntryDataType> {
        return this.createWritePromise<
            GlideRecord<[GlideString, GlideString][]>
        >(
            createXClaim(key, group, consumer, minIdleTime, ids, options),
            options,
        ).then(convertGlideRecordToRecord);
    }

    /**
     * Transfers ownership of pending stream entries that match the specified criteria.
     *
     * @see {@link https://valkey.io/commands/xautoclaim/|valkey.io} for more details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param key - The key of the stream.
     * @param group - The consumer group name.
     * @param consumer - The group consumer.
     * @param minIdleTime - The minimum idle time for the message to be claimed.
     * @param start - Filters the claimed entries to those that have an ID equal or greater than the
     *     specified value.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `count`: the number of claimed entries. Default value is 100.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns A `tuple` containing the following elements:
     *   - A stream ID to be used as the start argument for the next call to `XAUTOCLAIM`. This ID is
     *     equivalent to the next ID in the stream after the entries that were scanned, or "0-0" if
     *     the entire stream was scanned.
     *   - A `Record` of the claimed entries.
     *   - If you are using Valkey 7.0.0 or above, the response list will also include a list containing
     *     the message IDs that were in the Pending Entries List but no longer exist in the stream.
     *     These IDs are deleted from the Pending Entries List.
     *
     * @example
     * ```typescript
     * const result = await client.xautoclaim("myStream", "myGroup", "myConsumer", 42, "0-0", { count: 25 });
     * console.log(result);
     * // Output:
     * // [
     * //     "1609338788321-0",                // value to be used as `start` argument
     * //                                       // for the next `xautoclaim` call
     * //     {
     * //         "1609338752495-0": [          // claimed entries
     * //             ["field 1", "value 1"],
     * //             ["field 2", "value 2"]
     * //         ]
     * //     },
     * //     [
     * //         "1594324506465-0",            // array of IDs of deleted messages,
     * //         "1594568784150-0"             // included in the response only on valkey 7.0.0 and above
     * //     ]
     * // ]
     * ```
     */
    public async xautoclaim(
        key: GlideString,
        group: GlideString,
        consumer: GlideString,
        minIdleTime: number,
        start: string,
        options?: { count?: number } & DecoderOption,
    ): Promise<[GlideString, StreamEntryDataType, GlideString[]?]> {
        return this.createWritePromise<
            [
                GlideString,
                GlideRecord<[GlideString, GlideString][]>,
                GlideString[],
            ]
        >(
            createXAutoClaim(
                key,
                group,
                consumer,
                minIdleTime,
                start,
                options?.count,
            ),
            options,
        ).then((res) =>
            res.length === 3
                ? [res[0], convertGlideRecordToRecord(res[1]), res[2]]
                : [res[0], convertGlideRecordToRecord(res[1])],
        );
    }

    /**
     * Transfers ownership of pending stream entries that match the specified criteria.
     *
     * @see {@link https://valkey.io/commands/xautoclaim/|valkey.io} for more details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param key - The key of the stream.
     * @param group - The consumer group name.
     * @param consumer - The group consumer.
     * @param minIdleTime - The minimum idle time for the message to be claimed.
     * @param start - Filters the claimed entries to those that have an ID equal or greater than the
     *     specified value.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `count`: limits the number of claimed entries to the specified value. Default value is 100.
     * @returns An `array` containing the following elements:
     *   - A stream ID to be used as the start argument for the next call to `XAUTOCLAIM`. This ID is
     *     equivalent to the next ID in the stream after the entries that were scanned, or "0-0" if
     *     the entire stream was scanned.
     *   - A list of the IDs for the claimed entries.
     *   - If you are using Valkey 7.0.0 or above, the response list will also include a list containing
     *     the message IDs that were in the Pending Entries List but no longer exist in the stream.
     *     These IDs are deleted from the Pending Entries List.
     *
     * @example
     * ```typescript
     * const result = await client.xautoclaim("myStream", "myGroup", "myConsumer", 42, "0-0", { count: 25 });
     * console.log(result);
     * // Output:
     * // [
     * //     "1609338788321-0",                // value to be used as `start` argument
     * //                                       // for the next `xautoclaim` call
     * //     [
     * //         "1609338752495-0",            // claimed entries
     * //         "1609338752495-1",
     * //     ],
     * //     [
     * //         "1594324506465-0",            // array of IDs of deleted messages,
     * //         "1594568784150-0"             // included in the response only on valkey 7.0.0 and above
     * //     ]
     * // ]
     * ```
     */
    public async xautoclaimJustId(
        key: GlideString,
        group: GlideString,
        consumer: GlideString,
        minIdleTime: number,
        start: string,
        options?: { count?: number },
    ): Promise<[string, string[], string[]?]> {
        return this.createWritePromise(
            createXAutoClaim(
                key,
                group,
                consumer,
                minIdleTime,
                start,
                options?.count,
                true,
            ),
            { decoder: Decoder.String },
        );
    }

    /**
     * Changes the ownership of a pending message. This function returns an `array` with
     * only the message/entry IDs, and is equivalent to using `JUSTID` in the Valkey API.
     *
     * @see {@link https://valkey.io/commands/xclaim/|valkey.io} for more details.
     *
     * @param key - The key of the stream.
     * @param group - The consumer group name.
     * @param consumer - The group consumer.
     * @param minIdleTime - The minimum idle time for the message to be claimed.
     * @param ids - An array of entry ids.
     * @param options - (Optional) Stream claim options {@link StreamClaimOptions}.
     * @returns An `array` of message ids claimed by the consumer.
     *
     * @example
     * ```typescript
     * const result = await client.xclaimJustId("my_stream", "my_group", "my_consumer", 42,
     *     ["1-0", "2-0", "3-0"], { idle: 500, retryCount: 3, isForce: true });
     * console.log(result);
     * // Output: [ "2-0", "3-0" ]
     * // A list of entry ids.
     * ```
     */
    public async xclaimJustId(
        key: GlideString,
        group: GlideString,
        consumer: GlideString,
        minIdleTime: number,
        ids: string[],
        options?: StreamClaimOptions,
    ): Promise<string[]> {
        return this.createWritePromise(
            createXClaim(key, group, consumer, minIdleTime, ids, options, true),
            { decoder: Decoder.String },
        );
    }

    /**
     * Creates a new consumer group uniquely identified by `groupname` for the stream stored at `key`.
     *
     * @see {@link https://valkey.io/commands/xgroup-create/|valkey.io} for more details.
     *
     * @param key - The key of the stream.
     * @param groupName - The newly created consumer group name.
     * @param id - Stream entry ID that specifies the last delivered entry in the stream from the new
     *     group’s perspective. The special ID `"$"` can be used to specify the last entry in the stream.
     * @returns `"OK"`.
     *
     * @example
     * ```typescript
     * // Create the consumer group "mygroup", using zero as the starting ID:
     * console.log(await client.xgroupCreate("mystream", "mygroup", "0-0"));
     * // Output: "OK"
     * ```
     */
    public async xgroupCreate(
        key: GlideString,
        groupName: GlideString,
        id: string,
        options?: StreamGroupOptions,
    ): Promise<"OK"> {
        return this.createWritePromise(
            createXGroupCreate(key, groupName, id, options),
            { decoder: Decoder.String },
        );
    }

    /**
     * Destroys the consumer group `groupname` for the stream stored at `key`.
     *
     * @see {@link https://valkey.io/commands/xgroup-destroy/|valkey.io} for more details.
     *
     * @param key - The key of the stream.
     * @param groupname - The consumer group name to delete.
     * @returns `true` if the consumer group is destroyed. Otherwise, `false`.
     *
     * @example
     * ```typescript
     * // Destroys the consumer group "mygroup"
     * console.log(await client.xgroupDestroy("mystream", "mygroup"));
     * // Output: true
     * ```
     */
    public async xgroupDestroy(
        key: GlideString,
        groupName: GlideString,
    ): Promise<boolean> {
        return this.createWritePromise(createXGroupDestroy(key, groupName));
    }

    /**
     * Returns information about the stream stored at `key`.
     *
     * @see {@link https://valkey.io/commands/xinfo-stream/|valkey.io} for more details.
     *
     * @param key - The key of the stream.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `fullOptions`: If `true`, returns verbose information with a limit of the first 10 PEL entries.
     * If `number` is specified, returns verbose information limiting the returned PEL entries.
     * If `0` is specified, returns verbose information with no limit.
     * @remark `fullOptions` - Available since Valkey version 6.0.0
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns A {@link ReturnTypeXinfoStream} of detailed stream information for the given `key`. See
     *     the example for a sample response.
     *
     * @example
     * ```typescript
     * const infoResult = await client.xinfoStream("my_stream");
     * console.log(infoResult);
     * // Output: {
     * //   length: 2,                                                                // The number of entries in the stream.
     * //   "radix-tree-keys": 1,                                                     // The number of keys in the underlying radix data structure.
     * //   "radix-tree-nodes": 2,                                                    // The number of nodes in the underlying radix data structure.
     * //   "last-generated-id": "1719877599564-1",                                   // The ID of the least-recently entry that was added to the stream.
     * //   "max-deleted-entry-id": "0-0",                                            // The maximal entry ID that was deleted from the stream. Added in Valkey 7.0.0.
     * //   "entries-added": 2,                                                       // The count of all entries added to the stream during its lifetime. Added in Valkey 7.0.0.
     * //   "recorded-first-entry-id": "1719877599564-0",                             // Recorded first entry id. Added in Valkey 7.0.0.
     * //   "first-entry": [ "1719877599564-0", ["some_field", "some_value", ...] ],  // The ID and field-value tuples of the first entry in the stream.
     * //   "last-entry": [ "1719877599564-0", ["some_field", "some_value", ...] ],   // The ID and field-value tuples of the last entry in the stream.
     * //   "groups": 1,                                                              // The number of consumer groups defined for the stream
     * // }
     * ```
     *
     * @example
     * ```typescript
     * const infoResult = await client.xinfoStream("my_stream", true); // default limit of 10 entries
     * const infoResult = await client.xinfoStream("my_stream", 15);   // limit of 15 entries
     * console.log(infoResult);
     * // Output: {
     * //   "length": 2,                                                              // The number of entries in the stream.
     * //   "radix-tree-keys": 1,                                                     // The number of keys in the underlying radix data structure.
     * //   "radix-tree-nodes": 2,                                                    // The number of nodes in the underlying radix data structure.
     * //   "last-generated-id": "1719877599564-1",                                   // The ID of the least-recently entry that was added to the stream.
     * //   "max-deleted-entry-id": "0-0",                                            // The maximal entry ID that was deleted from the stream. Added in Valkey 7.0.0.
     * //   "entries-added": 2,                                                       // The count of all entries added to the stream during its lifetime. Added in Valkey 7.0.0.
     * //   "recorded-first-entry-id": "1719877599564-0",                             // Recorded first entry id. Added in Valkey 7.0.0.
     * //   "entries": [ [ "1719877599564-0", ["some_field", "some_value", ...] ] ],  // Array of the stream entries (ID and field-value tuples) in ascending order.
     * //   "groups': [ {                                                             // An array of groups containing information about each consumer group.
     * //     "name': "group",                                                        // The consumer group's name.
     * //     "last-delivered-id": "1719877599564-0",                                 // The ID of the last entry delivered to the group's consumers.
     * //     "entries-read": 1,                                                      // The logical "read counter" of the last entry delivered to the group's consumers. Added in Valkey 7.0.0.
     * //     "lag": 1,                                                               // The number of entries in the stream that are still waiting to be delivered. Added in Valkey 7.0.0.
     * //     "pel-count": 1,                                                         // The length of the group's pending entries list (PEL).
     * //     "pending": [ [ "1719877599564-0", "consumer", 1722624726802, 1 ] ],     // An array with pending entries.
     * //     "consumers": [ {
     * //         "name": "consumer",                                                 // The consumer's name.
     * //         "seen-time": 1722624726802,                                         // The UNIX timestamp of the last attempted interaction.
     * //         "active-time": 1722624726802,                                       // The UNIX timestamp of the last successful interaction. Added in Valkey 7.2.0.
     * //         "pel-count": 1,                                                     // The number of entries in the PEL.
     * //         "pending": [ [ "1719877599564-0", "consumer", 1722624726802, 1 ] ], // An array with pending entries information.
     * //         }
     * //       ]
     * //     }
     * //   ]
     * // }
     * ```
     */
    public async xinfoStream(
        key: GlideString,
        options?: { fullOptions?: boolean | number } & DecoderOption,
    ): Promise<ReturnTypeXinfoStream> {
        return this.createWritePromise<
            GlideRecord<
                | StreamEntries
                | GlideRecord<StreamEntries | GlideRecord<StreamEntries>[]>[]
            >
        >(createXInfoStream(key, options?.fullOptions ?? false), options).then(
            (xinfoStream) =>
                convertGlideRecordToRecord(
                    xinfoStream,
                ) as ReturnTypeXinfoStream,
        );
    }

    /**
     * Creates a consumer named `consumerName` in the consumer group `groupName` for the stream stored at `key`.
     *
     * @see {@link https://valkey.io/commands/xgroup-createconsumer/|valkey.io} for more details.
     *
     * @param key - The key of the stream.
     * @param groupName - The consumer group name.
     * @param consumerName - The newly created consumer.
     * @returns `true` if the consumer is created. Otherwise, returns `false`.
     *
     * @example
     * ```typescript
     * // The consumer "myconsumer" was created in consumer group "mygroup" for the stream "mystream".
     * console.log(await client.xgroupCreateConsumer("mystream", "mygroup", "myconsumer"));
     * // Output: true
     * ```
     */
    public async xgroupCreateConsumer(
        key: GlideString,
        groupName: GlideString,
        consumerName: GlideString,
    ): Promise<boolean> {
        return this.createWritePromise(
            createXGroupCreateConsumer(key, groupName, consumerName),
        );
    }

    /**
     * Deletes a consumer named `consumerName` in the consumer group `groupName` for the stream stored at `key`.
     *
     * @see {@link https://valkey.io/commands/xgroup-delconsumer/|valkey.io} for more details.
     *
     * @param key - The key of the stream.
     * @param groupName - The consumer group name.
     * @param consumerName - The consumer to delete.
     * @returns The number of pending messages the `consumer` had before it was deleted.
     *
     * * @example
     * ```typescript
     * // Consumer "myconsumer" was deleted, and had 5 pending messages unclaimed.
     * console.log(await client.xgroupDelConsumer("mystream", "mygroup", "myconsumer"));
     * // Output: 5
     * ```
     */
    public async xgroupDelConsumer(
        key: GlideString,
        groupName: GlideString,
        consumerName: GlideString,
    ): Promise<number> {
        return this.createWritePromise(
            createXGroupDelConsumer(key, groupName, consumerName),
        );
    }

    private readonly MAP_READ_FROM_STRATEGY: Record<
        ReadFrom,
        connection_request.ReadFrom
    > = {
        primary: connection_request.ReadFrom.Primary,
        preferReplica: connection_request.ReadFrom.PreferReplica,
        AZAffinity: connection_request.ReadFrom.AZAffinity,
        AZAffinityReplicasAndPrimary:
            connection_request.ReadFrom.AZAffinityReplicasAndPrimary,
        allNodes: connection_request.ReadFrom.AllNodes,
    };

    /**
     * Returns the number of messages that were successfully acknowledged by the consumer group member of a stream.
     * This command should be called on a pending message so that such message does not get processed again.
     *
     * @see {@link https://valkey.io/commands/xack/|valkey.io} for details.
     *
     * @param key - The key of the stream.
     * @param group - The consumer group name.
     * @param ids - An array of entry ids.
     * @returns The number of messages that were successfully acknowledged.
     *
     * @example
     * ```typescript
     * const entryId = await client.xadd("mystream", ["myfield", "mydata"]);
     * // read messages from streamId
     * const readResult = await client.xreadgroup(["myfield", "mydata"], "mygroup", "my0consumer");
     * // acknowledge messages on stream
     * console.log(await client.xack("mystream", "mygroup", [entryId]));
     * // Output: 1
     * ```
     */
    public async xack(
        key: GlideString,
        group: GlideString,
        ids: string[],
    ): Promise<number> {
        return this.createWritePromise(createXAck(key, group, ids));
    }

    /**
     * Sets the last delivered ID for a consumer group.
     *
     * @see {@link https://valkey.io/commands/xgroup-setid|valkey.io} for more details.
     *
     * @param key - The key of the stream.
     * @param groupName - The consumer group name.
     * @param id - The stream entry ID that should be set as the last delivered ID for the consumer group.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `entriesRead`: the number of stream entries already read by the group.
     *     This option can only be specified if you are using Valkey version 7.0.0 or above.
     * @returns `"OK"`.
     *
     * * @example
     * ```typescript
     * console.log(await client.xgroupSetId("mystream", "mygroup", "0", { entriesRead: 1 }));
     * // Output: "OK"
     * ```
     */
    public async xgroupSetId(
        key: GlideString,
        groupName: GlideString,
        id: string,
        options?: { entriesRead?: number },
    ): Promise<"OK"> {
        return this.createWritePromise(
            createXGroupSetid(key, groupName, id, options?.entriesRead),
            { decoder: Decoder.String },
        );
    }

    /** Returns the element at index `index` in the list stored at `key`.
     * The index is zero-based, so 0 means the first element, 1 the second element and so on.
     * Negative indices can be used to designate elements starting at the tail of the list.
     * Here, -1 means the last element, -2 means the penultimate and so forth.
     *
     * @see {@link https://valkey.io/commands/lindex/|valkey.io} for more details.
     *
     * @param key - The `key` of the list.
     * @param index - The `index` of the element in the list to retrieve.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns - The element at `index` in the list stored at `key`.
     * If `index` is out of range or if `key` does not exist, null is returned.
     *
     * @example
     * ```typescript
     * // Example usage of lindex method to retrieve elements from a list by index
     * const result = await client.lindex("my_list", 0);
     * console.log(result);
     * // Output: 'value1'
     * // Returns the first element in the list stored at `my_list`.
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of lindex method to retrieve elements from a list by negative index
     * const result = await client.lindex("my_list", -1);
     * console.log(result);
     * // Output: 'value3'
     * // Returns the last element in the list stored at `my_list`.
     * ```
     */
    public async lindex(
        key: GlideString,
        index: number,
        options?: DecoderOption,
    ): Promise<GlideString | null> {
        return this.createWritePromise(createLIndex(key, index), options);
    }

    /**
     * Inserts `element` in the list at `key` either before or after the `pivot`.
     *
     * @see {@link https://valkey.io/commands/linsert/|valkey.io} for more details.
     *
     * @param key - The key of the list.
     * @param position - The relative position to insert into - either `InsertPosition.Before` or
     *     `InsertPosition.After` the `pivot`.
     * @param pivot - An element of the list.
     * @param element - The new element to insert.
     * @returns The list length after a successful insert operation.
     * If the `key` doesn't exist returns `-1`.
     * If the `pivot` wasn't found, returns `0`.
     *
     * @example
     * ```typescript
     * const length = await client.linsert("my_list", InsertPosition.Before, "World", "There");
     * console.log(length);
     * // Output: 2
     * // The list has a length of 2 after performing the insert.
     * ```
     */
    public async linsert(
        key: GlideString,
        position: InsertPosition,
        pivot: GlideString,
        element: GlideString,
    ): Promise<number> {
        return this.createWritePromise(
            createLInsert(key, position, pivot, element),
        );
    }

    /**
     * Removes the existing timeout on `key`, turning the key from volatile (a key with an expire set) to
     * persistent (a key that will never expire as no timeout is associated).
     *
     * @see {@link https://valkey.io/commands/persist/|valkey.io} for more details.
     *
     * @param key - The key to remove the existing timeout on.
     * @returns `false` if `key` does not exist or does not have an associated timeout, `true` if the timeout has been removed.
     *
     * @example
     * ```typescript
     * // Example usage of persist method to remove the timeout associated with a key
     * const result = await client.persist("my_key");
     * console.log(result);
     * // Output: true
     * // Indicates that the timeout associated with the key `my_key` was successfully removed.
     * ```
     */
    public async persist(key: GlideString): Promise<boolean> {
        return this.createWritePromise(createPersist(key));
    }

    /**
     * Renames `key` to `newkey`.
     * If `newkey` already exists it is overwritten.
     *
     * @see {@link https://valkey.io/commands/rename/|valkey.io} for more details.
     * @remarks When in cluster mode, `key` and `newKey` must map to the same hash slot.
     *
     * @param key - The key to rename.
     * @param newKey - The new name of the key.
     * @returns - If the `key` was successfully renamed, return "OK". If `key` does not exist, an error is thrown.
     *
     * @example
     * ```typescript
     * // Example usage of rename method to rename a key
     * await client.set("old_key", "value");
     * const result = await client.rename("old_key", "new_key");
     * console.log(result);
     * // Output: OK
     * // Indicates successful renaming of the key `old_key` to `new_key`.
     * ```
     */
    public async rename(key: GlideString, newKey: GlideString): Promise<"OK"> {
        return this.createWritePromise(createRename(key, newKey), {
            decoder: Decoder.String,
        });
    }

    /**
     * Renames `key` to `newkey` if `newkey` does not yet exist.
     *
     * @see {@link https://valkey.io/commands/renamenx/|valkey.io} for more details.
     * @remarks When in cluster mode, `key` and `newKey` must map to the same hash slot.
     *
     * @param key - The key to rename.
     * @param newKey - The new name of the key.
     * @returns - If the `key` was successfully renamed, returns `true`. Otherwise, returns `false`.
     * If `key` does not exist, an error is thrown.
     *
     * @example
     * ```typescript
     * // Example usage of renamenx method to rename a key
     * await client.set("old_key", "value");
     * const result = await client.renamenx("old_key", "new_key");
     * console.log(result);
     * // Output: true
     * // Indicates successful renaming of the key `old_key` to `new_key`.
     * ```
     */
    public async renamenx(
        key: GlideString,
        newKey: GlideString,
    ): Promise<boolean> {
        return this.createWritePromise(createRenameNX(key, newKey));
    }

    /** Blocking list pop primitive.
     * Pop an element from the tail of the first list that is non-empty,
     * with the given `keys` being checked in the order that they are given.
     * Blocks the connection when there are no elements to pop from any of the given lists.
     *
     * @see {@link https://valkey.io/commands/brpop/|valkey.io} for more details.
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @remarks `BRPOP` is a blocking command, see [Blocking Commands](https://glide.valkey.io/how-to/connection-management/#blocking-commands) for more details and best practices.
     *
     * @param keys - The `keys` of the lists to pop from.
     * @param timeout - The `timeout` in seconds.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns - An `array` containing the `key` from which the element was popped and the value of the popped element,
     * formatted as [key, value]. If no element could be popped and the timeout expired, returns `null`.
     *
     * @example
     * ```typescript
     * // Example usage of brpop method to block and wait for elements from multiple lists
     * const result = await client.brpop(["list1", "list2"], 5);
     * console.log(result);
     * // Output: ["list1", "element"]
     * // Indicates an element `element` was popped from `list1`.
     * ```
     */
    public async brpop(
        keys: GlideString[],
        timeout: number,
        options?: DecoderOption,
    ): Promise<[GlideString, GlideString] | null> {
        return this.createWritePromise(createBRPop(keys, timeout), options);
    }

    /** Blocking list pop primitive.
     * Pop an element from the head of the first list that is non-empty,
     * with the given `keys` being checked in the order that they are given.
     * Blocks the connection when there are no elements to pop from any of the given lists.
     *
     * @see {@link https://valkey.io/commands/blpop/|valkey.io} for more details.
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @remarks `BLPOP` is a blocking command, see [Blocking Commands](https://glide.valkey.io/how-to/connection-management/#blocking-commands) for more details and best practices.
     *
     * @param keys - The `keys` of the lists to pop from.
     * @param timeout - The `timeout` in seconds.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns - An `array` containing the `key` from which the element was popped and the value of the popped element,
     * formatted as [key, value]. If no element could be popped and the timeout expired, returns `null`.
     *
     * @example
     * ```typescript
     * const result = await client.blpop(["list1", "list2"], 5);
     * console.log(result);
     * // Output: ['list1', 'element']
     * // An element `element` popped from the list stored at key `list1`.
     * ```
     */
    public async blpop(
        keys: GlideString[],
        timeout: number,
        options?: DecoderOption,
    ): Promise<[GlideString, GlideString] | null> {
        return this.createWritePromise(createBLPop(keys, timeout), options);
    }

    /** Adds all elements to the HyperLogLog data structure stored at the specified `key`.
     * Creates a new structure if the `key` does not exist.
     * When no elements are provided, and `key` exists and is a HyperLogLog, then no operation is performed.
     *
     * @see {@link https://valkey.io/commands/pfadd/|valkey.io} for more details.
     *
     * @param key - The key of the HyperLogLog data structure to add elements into.
     * @param elements - An array of members to add to the HyperLogLog stored at `key`.
     * @returns - If the HyperLogLog is newly created, or if the HyperLogLog approximated cardinality is
     *     altered, then returns `true`. Otherwise, returns `false`.
     * @example
     * ```typescript
     * const result = await client.pfadd("hll_1", ["a", "b", "c"]);
     * console.log(result);
     * // Output: true
     * // Indicates that a data structure was created or modified.
     *
     * const result = await client.pfadd("hll_2", []);
     * console.log(result);
     * // Output: true
     * // Indicates that a new empty data structure was created.
     * ```
     */
    public async pfadd(
        key: GlideString,
        elements: GlideString[],
    ): Promise<boolean> {
        return this.createWritePromise(createPfAdd(key, elements));
    }

    /** Estimates the cardinality of the data stored in a HyperLogLog structure for a single key or
     * calculates the combined cardinality of multiple keys by merging their HyperLogLogs temporarily.
     *
     * @see {@link https://valkey.io/commands/pfcount/|valkey.io} for more details.
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     *
     * @param keys - The keys of the HyperLogLog data structures to be analyzed.
     * @returns - The approximated cardinality of given HyperLogLog data structures.
     *     The cardinality of a key that does not exist is `0`.
     * @example
     * ```typescript
     * const result = await client.pfcount(["hll_1", "hll_2"]);
     * console.log(result);
     * // Output: 4
     * // The approximate cardinality of the union of `hll_1` and `hll_2`
     * ```
     */
    public async pfcount(keys: GlideString[]): Promise<number> {
        return this.createWritePromise(createPfCount(keys));
    }

    /**
     * Merges multiple HyperLogLog values into a unique value. If the destination variable exists, it is
     * treated as one of the source HyperLogLog data sets, otherwise a new HyperLogLog is created.
     *
     * @see {@link https://valkey.io/commands/pfmerge/|valkey.io} for more details.
     * @remarks When in Cluster mode, all keys in `sourceKeys` and `destination` must map to the same hash slot.
     *
     * @param destination - The key of the destination HyperLogLog where the merged data sets will be stored.
     * @param sourceKeys - The keys of the HyperLogLog structures to be merged.
     * @returns A simple "OK" response.
     *
     * @example
     * ```typescript
     * await client.pfadd("hll1", ["a", "b"]);
     * await client.pfadd("hll2", ["b", "c"]);
     * const result = await client.pfmerge("new_hll", ["hll1", "hll2"]);
     * console.log(result);
     * // Output: OK
     * // The value of `hll1` merged with `hll2` was stored in `new_hll`.
     *
     * const count = await client.pfcount(["new_hll"]);
     * console.log(count);
     * // Output: 3
     * // The approximate cardinality of `new_hll` is 3.
     * ```
     */
    public async pfmerge(
        destination: GlideString,
        sourceKeys: GlideString[],
    ): Promise<"OK"> {
        return this.createWritePromise(createPfMerge(destination, sourceKeys), {
            decoder: Decoder.String,
        });
    }

    /**
     * Returns the internal encoding for the Valkey object stored at `key`.
     *
     * @see {@link https://valkey.io/commands/object-encoding/|valkey.io} for more details.
     *
     * @param key - The `key` of the object to get the internal encoding of.
     * @returns - If `key` exists, returns the internal encoding of the object stored at `key` as a string.
     *     Otherwise, returns `null`.
     *
     * @example
     * ```typescript
     * const result = await client.objectEncoding("my_hash");
     * console.log(result);
     * // Output: "listpack"
     * ```
     */
    public async objectEncoding(key: GlideString): Promise<string | null> {
        return this.createWritePromise(createObjectEncoding(key), {
            decoder: Decoder.String,
        });
    }

    /**
     * Returns the logarithmic access frequency counter of a Valkey object stored at `key`.
     *
     * @see {@link https://valkey.io/commands/object-freq/|valkey.io} for more details.
     *
     * @param key - The `key` of the object to get the logarithmic access frequency counter of.
     * @returns - If `key` exists, returns the logarithmic access frequency counter of the object
     *            stored at `key` as a `number`. Otherwise, returns `null`.
     *
     * @example
     * ```typescript
     * const result = await client.objectFreq("my_hash");
     * console.log(result);
     * // Output: 2
     * // The logarithmic access frequency counter of `my_hash`.
     * ```
     */
    public async objectFreq(key: GlideString): Promise<number | null> {
        return this.createWritePromise(createObjectFreq(key));
    }

    /**
     * Returns the time in seconds since the last access to the value stored at `key`.
     *
     * @see {@link https://valkey.io/commands/object-idletime/|valkey.io} for more details.
     *
     * @param key - The key of the object to get the idle time of.
     * @returns If `key` exists, returns the idle time in seconds. Otherwise, returns `null`.
     *
     * @example
     * ```typescript
     * const result = await client.objectIdletime("my_hash");
     * console.log(result);
     * // Output: 13
     * // `my_hash` was last accessed 13 seconds ago.
     * ```
     */
    public async objectIdletime(key: GlideString): Promise<number | null> {
        return this.createWritePromise(createObjectIdletime(key));
    }

    /**
     * Returns the reference count of the object stored at `key`.
     *
     * @see {@link https://valkey.io/commands/object-refcount/|valkey.io} for more details.
     *
     * @param key - The `key` of the object to get the reference count of.
     * @returns If `key` exists, returns the reference count of the object stored at `key` as a `number`.
     * Otherwise, returns `null`.
     *
     * @example
     * ```typescript
     * const result = await client.objectRefcount("my_hash");
     * console.log(result);
     * // Output: 2
     * // `my_hash` has a reference count of 2.
     * ```
     */
    public async objectRefcount(key: GlideString): Promise<number | null> {
        return this.createWritePromise(createObjectRefcount(key));
    }

    /**
     * Invokes a previously loaded function.
     *
     * @see {@link https://valkey.io/commands/fcall/|valkey.io} for more details.
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param func - The function name.
     * @param keys - A list of `keys` accessed by the function. To ensure the correct execution of functions,
     *     all names of keys that a function accesses must be explicitly provided as `keys`.
     * @param args - A list of `function` arguments and it should not represent names of keys.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns The invoked function's return value.
     *
     * @example
     * ```typescript
     * const response = await client.fcall("Deep_Thought", [], []);
     * console.log(response); // Returns the function's return value.
     * ```
     */
    public async fcall(
        func: GlideString,
        keys: GlideString[],
        args: GlideString[],
        options?: DecoderOption,
    ): Promise<GlideReturnType> {
        return this.createWritePromise(createFCall(func, keys, args), options);
    }

    /**
     * Invokes a previously loaded read-only function.
     *
     * @see {@link https://valkey.io/commands/fcall/|valkey.io} for more details.
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param func - The function name.
     * @param keys - A list of `keys` accessed by the function. To ensure the correct execution of functions,
     *     all names of keys that a function accesses must be explicitly provided as `keys`.
     * @param args - A list of `function` arguments and it should not represent names of keys.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns The invoked function's return value.
     *
     * @example
     * ```typescript
     * const response = await client.fcallReadOnly("Deep_Thought", ["key1"], ["Answer", "to", "the",
     *            "Ultimate", "Question", "of", "Life,", "the", "Universe,", "and", "Everything"]);
     * console.log(response);
     * // Output: 42
     * // The return value on the function that was executed.
     * ```
     */
    public async fcallReadonly(
        func: GlideString,
        keys: GlideString[],
        args: GlideString[],
        options?: DecoderOption,
    ): Promise<GlideReturnType> {
        return this.createWritePromise(
            createFCallReadOnly(func, keys, args),
            options,
        );
    }

    /**
     * Returns the index of the first occurrence of `element` inside the list specified by `key`. If no
     * match is found, `null` is returned. If the `count` option is specified, then the function returns
     * an `array` of indices of matching elements within the list.
     *
     * @see {@link https://valkey.io/commands/lpos/|valkey.io} for more details.
     * @remarks Since Valkey version 6.0.6.
     *
     * @param key - The name of the list.
     * @param element - The value to search for within the list.
     * @param options - (Optional) The LPOS options - see {@link LPosOptions}.
     * @returns The index of `element`, or `null` if `element` is not in the list. If the `count` option
     * is specified, then the function returns an `array` of indices of matching elements within the list.
     *
     * @example
     * ```typescript
     * await client.rpush("myList", ["a", "b", "c", "d", "e", "e"]);
     * console.log(await client.lpos("myList", "e", { rank: 2 }));
     * // Output: 5
     * // The second occurrence of `e` is at index 5.
     *
     * console.log(await client.lpos("myList", "e", { count: 3 }));
     * // Output: [ 4, 5 ]
     * // indices for the occurrences of `e` in list `myList`.
     * ```
     */
    public async lpos(
        key: GlideString,
        element: GlideString,
        options?: LPosOptions,
    ): Promise<number | number[] | null> {
        return this.createWritePromise(createLPos(key, element, options));
    }

    /**
     * Counts the number of set bits (population counting) in the string stored at `key`. The `options` argument can
     * optionally be provided to count the number of bits in a specific string interval.
     *
     * @see {@link https://valkey.io/commands/bitcount/|valkey.io} for more details.
     *
     * @param key - The key for the string to count the set bits of.
     * @param options - The offset options - see {@link BitOffsetOptions}.
     * @returns If `options` is provided, returns the number of set bits in the string interval specified by `options`.
     *     If `options` is not provided, returns the number of set bits in the string stored at `key`.
     *     Otherwise, if `key` is missing, returns `0` as it is treated as an empty string.
     *
     * @example
     * ```typescript
     * console.log(await client.bitcount("my_key1"));
     * // Output: 2
     * // The string stored at `my_key1` contains 2 set bits.
     *
     * console.log(await client.bitcount("my_key2", { start: 1 }));
     * // Output: 8
     * // From the second to to the last bytes of the string stored at `my_key2` are contain 8 set bits.
     *
     * console.log(await client.bitcount("my_key2", { start: 1, end: 3 }));
     * // Output: 2
     * // The second to fourth bytes of the string stored at `my_key2` contain 2 set bits.
     *
     * console.log(await client.bitcount("my_key3", { start: 1, end: 1, indexType: BitmapIndexType.BIT }));
     * // Output: 1
     * // Indicates that the second bit of the string stored at `my_key3` is set.
     *
     * console.log(await client.bitcount("my_key3", { start: -1, end: -1, indexType: BitmapIndexType.BIT }));
     * // Output: 1
     * // Indicates that the last bit of the string stored at `my_key3` is set.
     * ```
     */
    public async bitcount(
        key: GlideString,
        options?: BitOffsetOptions,
    ): Promise<number> {
        return this.createWritePromise(createBitCount(key, options));
    }

    /**
     * Adds geospatial members with their positions to the specified sorted set stored at `key`.
     * If a member is already a part of the sorted set, its position is updated.
     *
     * @see {@link https://valkey.io/commands/geoadd/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param membersToGeospatialData - A mapping of member names to their corresponding positions - see
     *     {@link GeospatialData}. The command will report an error when the user attempts to index
     *     coordinates outside the specified ranges.
     * @param options - The GeoAdd options - see {@link GeoAddOptions}.
     * @returns The number of elements added to the sorted set. If `changed` is set to
     *    `true` in the options, returns the number of elements updated in the sorted set.
     *
     * @example
     * ```typescript
     * const options = {updateMode: ConditionalChange.ONLY_IF_EXISTS, changed: true};
     * const membersToCoordinates = new Map<string, GeospatialData>([
     *      ["Palermo", { longitude: 13.361389, latitude: 38.115556 }],
     * ]);
     * const num = await client.geoadd("mySortedSet", membersToCoordinates, options);
     * console.log(num);
     * // Output: 1
     * // Indicates that the position of an existing member in the sorted set `mySortedSet` has been updated.
     * ```
     */
    public async geoadd(
        key: GlideString,
        membersToGeospatialData: Map<GlideString, GeospatialData>,
        options?: GeoAddOptions,
    ): Promise<number> {
        return this.createWritePromise(
            createGeoAdd(key, membersToGeospatialData, options),
        );
    }

    /**
     * Returns the members of a sorted set populated with geospatial information using {@link geoadd},
     * which are within the borders of the area specified by a given shape.
     *
     * @see {@link https://valkey.io/commands/geosearch/|valkey.io} for more details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param key - The key of the sorted set.
     * @param searchFrom - The query's center point options, could be one of:
     * - {@link MemberOrigin} to use the position of the given existing member in the sorted set.
     * - {@link CoordOrigin} to use the given longitude and latitude coordinates.
     * @param searchBy - The query's shape options, could be one of:
     * - {@link GeoCircleShape} to search inside circular area according to given radius.
     * - {@link GeoBoxShape} to search inside an axis-aligned rectangle, determined by height and width.
     * @param options - (Optional) Parameters to request additional information and configure sorting/limiting the results,
     *     see {@link GeoSearchResultOptions} and {@link DecoderOption}.
     * @returns By default, returns an `Array` of members (locations) names.
     *     If any of `withCoord`, `withDist` or `withHash` are set to `true` in {@link GeoSearchResultOptions}, a 2D `Array` returned,
     *     where each sub-array represents a single item in the following order:
     * - The member (location) name.
     * - The distance from the center as a floating point `number`, in the same unit specified for `searchBy`, if `withDist` is set to `true`.
     * - The geohash of the location as a integer `number`, if `withHash` is set to `true`.
     * - The coordinates as a two item `array` of floating point `number`s, if `withCoord` is set to `true`.
     *
     * @example
     * ```typescript
     * const data = new Map<GlideString, GeospatialData>([["Palermo", { longitude: 13.361389, latitude: 38.115556 }], ["Catania", { longitude: 15.087269, latitude: 37.502669 }]]);
     * await client.geoadd("mySortedSet", data);
     * // search for locations within 200 km circle around stored member named 'Palermo'
     * const result1 = await client.geosearch("mySortedSet", { member: "Palermo" }, { radius: 200, unit: GeoUnit.KILOMETERS });
     * console.log(result1);
     * // Output: ['Palermo', 'Catania']
     * // Locations within the specified radius.
     *
     * // search for locations in 200x300 mi rectangle centered at coordinate (15, 37), requesting additional info,
     * // limiting results by 2 best matches, ordered by ascending distance from the search area center
     * const result2 = await client.geosearch(
     *     "mySortedSet",
     *     { position: { longitude: 15, latitude: 37 } },
     *     { width: 200, height: 300, unit: GeoUnit.MILES },
     *     {
     *         sortOrder: SortOrder.ASC,
     *         count: 2,
     *         withCoord: true,
     *         withDist: true,
     *         withHash: true,
     *     },
     * );
     * console.log(result2);
     * // Output:
     * // [
     * //     [
     * //         'Catania',                                       // location name
     * //         [
     * //             56.4413,                                     // distance
     * //             3479447370796909,                            // geohash of the location
     * //             [15.087267458438873, 37.50266842333162],     // coordinates of the location
     * //         ],
     * //     ],
     * //     [
     * //         'Palermo',
     * //         [
     * //             190.4424,
     * //             3479099956230698,
     * //             [13.361389338970184, 38.1155563954963],
     * //         ],
     * //     ],
     * // ]
     * ```
     */
    public async geosearch(
        key: GlideString,
        searchFrom: SearchOrigin,
        searchBy: GeoSearchShape,
        options?: GeoSearchResultOptions & DecoderOption,
    ): Promise<[GlideString, [number?, number?, [number, number]?]?][]> {
        return this.createWritePromise(
            createGeoSearch(key, searchFrom, searchBy, options),
            options,
        );
    }

    /**
     * Searches for members in a sorted set stored at `source` representing geospatial data
     * within a circular or rectangular area and stores the result in `destination`.
     *
     * If `destination` already exists, it is overwritten. Otherwise, a new sorted set will be created.
     *
     * To get the result directly, see {@link geosearch}.
     *
     * @see {@link https://valkey.io/commands/geosearchstore/|valkey.io} for more details.
     * @remarks When in cluster mode, `destination` and `source` must map to the same hash slot.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param destination - The key of the destination sorted set.
     * @param source - The key of the sorted set.
     * @param searchFrom - The query's center point options, could be one of:
     * - {@link MemberOrigin} to use the position of the given existing member in the sorted set.
     * - {@link CoordOrigin} to use the given longitude and latitude coordinates.
     * @param searchBy - The query's shape options, could be one of:
     * - {@link GeoCircleShape} to search inside circular area according to given radius.
     * - {@link GeoBoxShape} to search inside an axis-aligned rectangle, determined by height and width.
     * @param options - (Optional) Parameters to request additional information and configure sorting/limiting the results,
     *     see {@link GeoSearchStoreResultOptions}.
     * @returns The number of elements in the resulting sorted set stored at `destination`.
     *
     * @example
     * ```typescript
     * const data = new Map([["Palermo", { longitude: 13.361389, latitude: 38.115556 }], ["Catania", { longitude: 15.087269, latitude: 37.502669 }]]);
     * await client.geoadd("mySortedSet", data);
     * // search for locations within 200 km circle around stored member named 'Palermo' and store in `destination`:
     * await client.geosearchstore("destination", "mySortedSet", { member: "Palermo" }, { radius: 200, unit: GeoUnit.KILOMETERS });
     * // query the stored results
     * const result1 = await client.zrangeWithScores("destination", { start: 0, end: -1 });
     * console.log(result1);
     * // Output:
     * // {
     * //     Palermo: 3479099956230698,   // geohash of the location is stored as element's score
     * //     Catania: 3479447370796909
     * // }
     *
     * // search for locations in 200x300 mi rectangle centered at coordinate (15, 37), requesting to store distance instead of geohashes,
     * // limiting results by 2 best matches, ordered by ascending distance from the search area center
     * await client.geosearchstore(
     *     "destination",
     *     "mySortedSet",
     *     { position: { longitude: 15, latitude: 37 } },
     *     { width: 200, height: 300, unit: GeoUnit.MILES },
     *     {
     *         sortOrder: SortOrder.ASC,
     *         count: 2,
     *         storeDist: true,
     *     },
     * );
     * // query the stored results
     * const result2 = await client.zrangeWithScores("destination", { start: 0, end: -1 });
     * console.log(result2);
     * // Output:
     * // {
     * //     Palermo: 190.4424,   // distance from the search area center is stored as element's score
     * //     Catania: 56.4413,    // the distance is measured in units used for the search query (miles)
     * // }
     * ```
     */
    public async geosearchstore(
        destination: GlideString,
        source: GlideString,
        searchFrom: SearchOrigin,
        searchBy: GeoSearchShape,
        options?: GeoSearchStoreResultOptions,
    ): Promise<number> {
        return this.createWritePromise(
            createGeoSearchStore(
                destination,
                source,
                searchFrom,
                searchBy,
                options,
            ),
        );
    }

    /**
     * Returns the positions (longitude, latitude) of all the specified `members` of the
     * geospatial index represented by the sorted set at `key`.
     *
     * @see {@link https://valkey.io/commands/geopos/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param members - The members for which to get the positions.
     * @returns A 2D `Array` which represents positions (longitude and latitude) corresponding to the
     *     given members. The order of the returned positions matches the order of the input members.
     *     If a member does not exist, its position will be `null`.
     *
     * @example
     * ```typescript
     * const data = new Map([["Palermo", { longitude: 13.361389, latitude: 38.115556 }], ["Catania", { longitude: 15.087269, latitude: 37.502669 }]]);
     * await client.geoadd("mySortedSet", data);
     * const result = await client.geopos("mySortedSet", ["Palermo", "Catania", "NonExisting"]);
     * // When added via GEOADD, the geospatial coordinates are converted into a 52 bit geohash, so the coordinates
     * // returned might not be exactly the same as the input values
     * console.log(result);
     * // Output:
     * // [
     * //     [13.36138933897018433, 38.11555639549629859], // Returns the position of member `Palermo`
     * //     [15.08726745843887329, 37.50266842333162032], // Returns the position of member `Catania`
     * //     null                                          // Returns null for non existent key.
     * // ]
     * ```
     */
    public async geopos(
        key: GlideString,
        members: GlideString[],
    ): Promise<([number, number] | null)[]> {
        return this.createWritePromise(createGeoPos(key, members));
    }

    /**
     * Pops member-score pairs from the first non-empty sorted set, with the given `keys`
     * being checked in the order they are provided.
     *
     * @see {@link https://valkey.io/commands/zmpop/|valkey.io} for more details.
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param keys - The keys of the sorted sets.
     * @param modifier - The element pop criteria - either {@link ScoreFilter.MIN} or
     *     {@link ScoreFilter.MAX} to pop the member with the lowest/highest score accordingly.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `count`: the maximum number of popped elements. If not specified, pops one member.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns A two-element `array` containing the key name of the set from which the element
     *     was popped, and a {@link SortedSetDataType} of the popped elements.
     *     If no member could be popped, returns `null`.
     *
     * @example
     * ```typescript
     * await client.zadd("zSet1", { one: 1.0, two: 2.0, three: 3.0 });
     * await client.zadd("zSet2", { four: 4.0 });
     * console.log(await client.zmpop(["zSet1", "zSet2"], ScoreFilter.MAX, 2));
     * // Output:
     * // [ "zSet1", [
     * //     { element: 'three', score: 3 },
     * //     { element: 'two', score: 2 }
     * // ] ]
     * // `three` with score 3 and `two` with score 2 were popped from `zSet1`.
     * ```
     */
    public async zmpop(
        keys: GlideString[],
        modifier: ScoreFilter,
        options?: { count?: number } & DecoderOption,
    ): Promise<[GlideString, SortedSetDataType] | null> {
        return this.createWritePromise<
            [GlideString, GlideRecord<number>] | null
        >(createZMPop(keys, modifier, options?.count), options).then((res) =>
            res === null
                ? null
                : [res[0], convertGlideRecordForSortedSet(res[1])],
        );
    }

    /**
     * Pops a member-score pair from the first non-empty sorted set, with the given `keys` being
     * checked in the order they are provided. Blocks the connection when there are no members
     * to pop from any of the given sorted sets. `BZMPOP` is the blocking variant of {@link zmpop}.
     *
     * @see {@link https://valkey.io/commands/bzmpop/|valkey.io} for more details.
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @remarks `BZMPOP` is a client blocking command, see {@link https://glide.valkey.io/how-to/connection-management/#blocking-commands | Valkey GLIDE Documentation} for more details and best practices.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param keys - The keys of the sorted sets.
     * @param modifier - The element pop criteria - either {@link ScoreFilter.MIN} or
     *     {@link ScoreFilter.MAX} to pop the member with the lowest/highest score accordingly.
     * @param timeout - The number of seconds to wait for a blocking operation to complete.
     *     A value of 0 will block indefinitely.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `count`: the maximum number of popped elements. If not specified, pops one member.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns A two-element `array` containing the key name of the set from which the element
     *     was popped, and a {@link SortedSetDataType} of the popped elements.
     *     If no member could be popped, returns `null`.
     *
     * @example
     * ```typescript
     * await client.zadd("zSet1", { one: 1.0, two: 2.0, three: 3.0 });
     * await client.zadd("zSet2", { four: 4.0 });
     * console.log(await client.bzmpop(["zSet1", "zSet2"], ScoreFilter.MAX, 0.1, 2));
     * // Output:
     * // [ "zSet1", [
     * //     { element: 'three', score: 3 },
     * //     { element: 'two', score: 2 }
     * // ] ]
     * // `three` with score 3 and `two` with score 2 were popped from `zSet1`
     * ```
     */
    public async bzmpop(
        keys: GlideString[],
        modifier: ScoreFilter,
        timeout: number,
        options?: { count?: number } & DecoderOption,
    ): Promise<[GlideString, SortedSetDataType] | null> {
        return this.createWritePromise<
            [GlideString, GlideRecord<number>] | null
        >(createBZMPop(keys, modifier, timeout, options?.count), options).then(
            (res) =>
                res === null
                    ? null
                    : [res[0], convertGlideRecordForSortedSet(res[1])],
        );
    }

    /**
     * Increments the score of `member` in the sorted set stored at `key` by `increment`.
     * If `member` does not exist in the sorted set, it is added with `increment` as its score.
     * If `key` does not exist, a new sorted set is created with the specified member as its sole member.
     *
     * @see {@link https://valkey.io/commands/zincrby/|valkey.io} for details.
     *
     * @param key - The key of the sorted set.
     * @param increment - The score increment.
     * @param member - A member of the sorted set.
     *
     * @returns The new score of `member`.
     *
     * @example
     * ```typescript
     * // Example usage of zincrBy method to increment the value of a member's score
     * await client.zadd("my_sorted_set", {"member": 10.5, "member2": 8.2});
     *
     * console.log(await client.zincrby("my_sorted_set", 1.2, "member"));
     * // Output: 11.7
     * // The member existed in the set before score was altered, the new score is 11.7.
     *
     * console.log(await client.zincrby("my_sorted_set", -1.7, "member"));
     * // Output: 10.0
     * // Negative increment, decrements the score.
     *
     * console.log(await client.zincrby("my_sorted_set", 5.5, "non_existing_member"));
     * // Output: 5.5
     * // A new member is added to the sorted set with the score of 5.5.
     * ```
     */
    public async zincrby(
        key: GlideString,
        increment: number,
        member: GlideString,
    ): Promise<number> {
        return this.createWritePromise(createZIncrBy(key, increment, member));
    }

    /**
     * Iterates incrementally over a sorted set.
     *
     * @see {@link https://valkey.io/commands/zscan/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param cursor - The cursor that points to the next iteration of results. A value of `"0"` indicates the start of
     *      the search.
     * @param options - (Optional) The `zscan` options - see {@link ZScanOptions} and {@link DecoderOption}.
     * @returns An `Array` of the `cursor` and the subset of the sorted set held by `key`.
     *      The first element is always the `cursor` for the next iteration of results. `0` will be the `cursor`
     *      returned on the last iteration of the sorted set. The second element is always an `Array` of the subset
     *      of the sorted set held in `key`. The `Array` in the second element is a flattened series of
     *      `string` pairs, where the value is at even indices and the score is at odd indices.
     *      If `options.noScores` is to `true`, the second element will only contain the members without scores.
     *
     * @example
     * ```typescript
     * // Assume "key1" contains a sorted set with multiple members
     * let cursor = "0";
     * do {
     *      const result = await client.zscan(key1, cursor, {
     *          match: "*",
     *          count: 5,
     *      });
     *      cursor = result[0];
     *      console.log("Cursor: ", cursor);
     *      console.log("Members: ", result[1]);
     * } while (cursor !== "0");
     * // The output of the code above is something similar to:
     * // Cursor:  123
     * // Members:  ['value 163', '163', 'value 114', '114', 'value 25', '25', 'value 82', '82', 'value 64', '64']
     * // Cursor:  47
     * // Members:  ['value 39', '39', 'value 127', '127', 'value 43', '43', 'value 139', '139', 'value 211', '211']
     * // Cursor:  0
     * // Members:  ['value 55', '55', 'value 24', '24', 'value 90', '90', 'value 113', '113']
     * ```
     *
     * @example
     * ```typescript
     * // Zscan with no scores
     * let newCursor = "0";
     * let result = [];
     *
     * do {
     *      result = await client.zscan(key1, newCursor, {
     *          match: "*",
     *          count: 5,
     *          noScores: true,
     *      });
     *      newCursor = result[0];
     *      console.log("Cursor: ", newCursor);
     *      console.log("Members: ", result[1]);
     * } while (newCursor !== "0");
     * // The output of the code above is something similar to:
     * // Cursor:  123
     * // Members:  ['value 163', 'value 114', 'value 25', 'value 82', 'value 64']
     * // Cursor:  47
     * // Members:  ['value 39', 'value 127', 'value 43', 'value 139', 'value 211']
     * // Cursor:  0
     * // Members:  ['value 55', 'value 24' 'value 90', 'value 113']
     * ```
     */
    public async zscan(
        key: GlideString,
        cursor: string,
        options?: ZScanOptions & DecoderOption,
    ): Promise<[string, GlideString[]]> {
        return this.createWritePromise<[GlideString, GlideString[]]>(
            createZScan(key, cursor, options),
            options,
        ).then((res) => [res[0].toString(), res[1]]); // convert cursor back to string
    }

    /**
     * Returns the distance between `member1` and `member2` saved in the geospatial index stored at `key`.
     *
     * @see {@link https://valkey.io/commands/geodist/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param member1 - The name of the first member.
     * @param member2 - The name of the second member.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `unit`: the unit of distance measurement - see {@link GeoUnit}.
     *   If not specified, the {@link GeoUnit.METERS} is used as a default unit.
     * @returns The distance between `member1` and `member2`. Returns `null`, if one or both members do not exist,
     *     or if the key does not exist.
     *
     * @example
     * ```typescript
     * const result = await client.geodist("mySortedSet", "Place1", "Place2", { unit: GeoUnit.KILOMETERS });
     * console.log(num);
     * // Output: the distance between Place1 and Place2.
     * ```
     */
    public async geodist(
        key: GlideString,
        member1: GlideString,
        member2: GlideString,
        options?: { unit?: GeoUnit },
    ): Promise<number | null> {
        return this.createWritePromise(
            createGeoDist(key, member1, member2, options?.unit),
        );
    }

    /**
     * Returns the `GeoHash` strings representing the positions of all the specified `members` in the sorted set stored at `key`.
     *
     * @see {@link https://valkey.io/commands/geohash/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param members - The array of members whose `GeoHash` strings are to be retrieved.
     * @returns An array of `GeoHash` strings representing the positions of the specified members stored at `key`.
     *     If a member does not exist in the sorted set, a `null` value is returned for that member.
     *
     * @example
     * ```typescript
     * const result = await client.geohash("mySortedSet", ["Palermo", "Catania", "NonExisting"]);
     * console.log(result);
     * // Output: ["sqc8b49rny0", "sqdtr74hyu0", null]
     * // An array of GeoHash string.
     * ```
     */
    public async geohash(
        key: GlideString,
        members: GlideString[],
    ): Promise<(string | null)[]> {
        return this.createWritePromise(createGeoHash(key, members), {
            decoder: Decoder.String,
        });
    }

    /**
     * Returns all the longest common subsequences combined between strings stored at `key1` and `key2`.
     *
     * @see {@link https://valkey.io/commands/lcs/|valkey.io} for more details.
     * @remarks When in cluster mode, `key1` and `key2` must map to the same hash slot.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param key1 - The key that stores the first string.
     * @param key2 - The key that stores the second string.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A `String` containing all the longest common subsequence combined between the 2 strings.
     *     An empty `String` is returned if the keys do not exist or have no common subsequences.
     *
     * @example
     * ```typescript
     * await client.mset({"testKey1": "abcd", "testKey2": "axcd"});
     * const result = await client.lcs("testKey1", "testKey2");
     * console.log(result);
     * // Output: 'acd'
     * // Returned the longest common subsequence of strings stored at `testKey1` and `testKey2`.
     * ```
     */
    public async lcs(
        key1: GlideString,
        key2: GlideString,
        options?: DecoderOption,
    ): Promise<string> {
        return this.createWritePromise(createLCS(key1, key2), options);
    }

    /**
     * Returns the total length of all the longest common subsequences between strings stored at `key1` and `key2`.
     *
     * @see {@link https://valkey.io/commands/lcs/|valkey.io} for more details.
     * @remarks When in cluster mode, `key1` and `key2` must map to the same hash slot.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param key1 - The key that stores the first string.
     * @param key2 - The key that stores the second string.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns The total length of all the longest common subsequences between the 2 strings.
     *
     * @example
     * ```typescript
     * await client.mset({"testKey1": "abcd", "testKey2": "axcd"});
     * const result = await client.lcsLen("testKey1", "testKey2");
     * console.log(result);
     * // Output: 3
     * // The total length of all the longest common subsequences between the strings stored at `testKey1` and `testKey2`.
     * ```
     */
    public async lcsLen(
        key1: GlideString,
        key2: GlideString,
        options?: DecoderOption,
    ): Promise<number> {
        return this.createWritePromise(
            createLCS(key1, key2, { len: true }),
            options,
        );
    }

    /**
     * Returns the indices and lengths of the longest common subsequences between strings stored at
     * `key1` and `key2`.
     *
     * @see {@link https://valkey.io/commands/lcs/|valkey.io} for more details.
     * @remarks When in cluster mode, `key1` and `key2` must map to the same hash slot.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param key1 - The key that stores the first string.
     * @param key2 - The key that stores the second string.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `withMatchLen`: if `true`, include the length of the substring matched for the each match.
     * - (Optional) `minMatchLen`: the minimum length of matches to include in the result.
     * @returns A `Record` containing the indices of the longest common subsequences between the
     *     2 strings and the lengths of the longest common subsequences. The resulting map contains two
     *     keys, "matches" and "len":
     *     - `"len"` is mapped to the total length of the all longest common subsequences between the 2 strings
     *           stored as an integer. This value doesn't count towards the `minMatchLen` filter.
     *     - `"matches"` is mapped to a three dimensional array of integers that stores pairs
     *           of indices that represent the location of the common subsequences in the strings held
     *           by `key1` and `key2`.
     *
     *     See example for more details.
     *
     * @example
     * ```typescript
     * await client.mset({"key1": "ohmytext", "key2": "mynewtext"});
     * const result = await client.lcsIdx("key1", "key2");
     * console.log(result);
     * // Output:
     * {
     *     "matches" :
     *     [
     *         [              // first substring match is "text"
     *             [4, 7],    // in `key1` it is located between indices 4 and 7
     *             [5, 8],    // and in `key2` - in between 5 and 8
     *             4          // the match length, returned if `withMatchLen` set to `true`
     *         ],
     *         [              // second substring match is "my"
     *             [2, 3],    // in `key1` it is located between indices 2 and 3
     *             [0, 1],    // and in `key2` - in between 0 and 1
     *             2          // the match length, returned if `withMatchLen` set to `true`
     *         ]
     *     ],
     *     "len" : 6          // total length of the all matches found
     * }
     * ```
     */
    public async lcsIdx(
        key1: GlideString,
        key2: GlideString,
        options?: {
            withMatchLen?: boolean;
            minMatchLen?: number;
        },
    ): Promise<Record<string, (number | [number, number])[][] | number>> {
        return this.createWritePromise<
            GlideRecord<(number | [number, number])[][] | number>
        >(createLCS(key1, key2, { idx: options ?? {} })).then(
            convertGlideRecordToRecord,
        );
    }

    /**
     * Updates the last access time of the specified keys.
     *
     * @see {@link https://valkey.io/commands/touch/|valkey.io} for more details.
     *
     * @remarks In cluster mode, if keys in `keys` map to different hash slots,
     * the command will be split across these slots and executed separately for each.
     * This means the command is atomic only at the slot level. If one or more slot-specific
     * requests fail, the entire call will return the first encountered error, even
     * though some requests may have succeeded while others did not.
     * If this behavior impacts your application logic, consider splitting the
     * request into sub-requests per slot to ensure atomicity.
     *
     * @param keys - The keys to update the last access time of.
     * @returns The number of keys that were updated. A key is ignored if it doesn't exist.
     *
     * @example
     * ```typescript
     * await client.set("key1", "value1");
     * await client.set("key2", "value2");
     * const result = await client.touch(["key1", "key2", "nonExistingKey"]);
     * console.log(result);
     * // Output: 2
     * // The last access time of 2 keys has been updated.
     * ```
     */
    public async touch(keys: GlideString[]): Promise<number> {
        return this.createWritePromise(createTouch(keys));
    }

    /**
     * Marks the given keys to be watched for conditional execution of a transaction. Transactions
     * will only execute commands if the watched keys are not modified before execution of the
     * transaction. Executing a transaction will automatically flush all previously watched keys.
     *
     * @see {@link https://valkey.io/commands/watch/|valkey.io} and {@link https://valkey.io/topics/transactions/#cas|Valkey GLIDE Documentation} for more details.
     *
     * @remarks In cluster mode, if keys in `keys` map to different hash slots,
     * the command will be split across these slots and executed separately for each.
     * This means the command is atomic only at the slot level. If one or more slot-specific
     * requests fail, the entire call will return the first encountered error, even
     * though some requests may have succeeded while others did not.
     * If this behavior impacts your application logic, consider splitting the
     * request into sub-requests per slot to ensure atomicity.
     *
     * @param keys - The keys to watch.
     * @returns A simple `"OK"` response.
     *
     * @example
     * ```typescript
     * const response = await client.watch(["sampleKey"]);
     * console.log(response);
     * // Output: "OK"
     *
     * const transaction = new Batch(true).set("SampleKey", "foobar");
     * const result = await client.exec(transaction);
     * console.log(result);
     *
     * // Output: "OK"
     * // Executes successfully and keys are unwatched.
     * ```
     * ```typescript
     * const response = await client.watch(["sampleKey"]);
     * console.log(response);
     * // Output: "OK"
     *
     * const transaction = new Batch(true).set("SampleKey", "foobar");
     * await client.set("sampleKey", "hello world");
     * const result = await client.exec(transaction);
     * console.log(result);
     * // Output: null
     * // null is returned when the watched key is modified before transaction execution.
     * ```
     */
    public async watch(keys: GlideString[]): Promise<"OK"> {
        return this.createWritePromise(createWatch(keys), {
            decoder: Decoder.String,
        });
    }

    /**
     * Blocks the current client until all the previous write commands are successfully transferred and
     * acknowledged by at least `numreplicas` of replicas. If `timeout` is reached, the command returns
     * the number of replicas that were not yet reached.
     *
     * @see {@link https://valkey.io/commands/wait/|valkey.io} for more details.
     *
     * @param numreplicas - The number of replicas to reach.
     * @param timeout - The timeout value specified in milliseconds. A value of 0 will block indefinitely.
     * @returns The number of replicas reached by all the writes performed in the context of the current connection.
     *
     * @example
     * ```typescript
     * await client.set(key, value);
     * let response = await client.wait(1, 1000);
     * console.log(response);
     * // Output: return 1 when a replica is reached or 0 if 1000ms is reached.
     * ```
     */
    public async wait(numreplicas: number, timeout: number): Promise<number> {
        return this.createWritePromise(createWait(numreplicas, timeout));
    }

    /**
     * Resets the connection state.
     *
     * @see {@link https://valkey.io/commands/reset/|valkey.io} for more details.
     *
     * @remarks Resets the database index, client name, protocol, and pubsub subscriptions.
     *
     * @returns "RESET" when the connection state is successfully reset.
     *
     * @example
     * ```typescript
     * const result = await client.reset();
     * console.log(result); // Output: "RESET"
     * ```
     */
    public async reset(): Promise<"RESET"> {
        return this.createWritePromise(createReset(), {
            decoder: Decoder.String,
        });
    }

    /**
     * Overwrites part of the string stored at `key`, starting at the specified byte `offset`,
     * for the entire length of `value`. If the `offset` is larger than the current length of the string at `key`,
     * the string is padded with zero bytes to make `offset` fit. Creates the `key` if it doesn't exist.
     *
     * @see {@link https://valkey.io/commands/setrange/|valkey.io} for more details.
     *
     * @param key - The key of the string to update.
     * @param offset - The byte position in the string where `value` should be written.
     * @param value - The string written with `offset`.
     * @returns The length of the string stored at `key` after it was modified.
     *
     * @example
     * ```typescript
     * const len = await client.setrange("key", 6, "GLIDE");
     * console.log(len);
     * // Output: 11
     * // New key was created with length of 11 symbols
     *
     * const value = await client.get("key");
     * console.log(result);
     * // Output: "\0\0\0\0\0\0GLIDE"
     * // The string was padded with zero bytes
     * ```
     */
    public async setrange(
        key: GlideString,
        offset: number,
        value: GlideString,
    ): Promise<number> {
        return this.createWritePromise(createSetRange(key, offset, value));
    }

    /**
     * Appends a `value` to a `key`. If `key` does not exist it is created and set as an empty string,
     * so `APPEND` will be similar to {@link set} in this special case.
     *
     * @see {@link https://valkey.io/commands/append/|valkey.io} for more details.
     *
     * @param key - The key of the string.
     * @param value - The key of the string.
     * @returns The length of the string after appending the value.
     *
     * @example
     * ```typescript
     * const len = await client.append("key", "Hello");
     * console.log(len);
     * // Output: 5
     * // Indicates that "Hello" has been appended to the value of "key", which was initially
     * // empty, resulting in a new value of "Hello" with a length of 5 - similar to the set operation.
     *
     * len = await client.append("key", " world");
     * console.log(result);
     * // Output: 11
     * // Indicates that " world" has been appended to the value of "key", resulting in a
     * // new value of "Hello world" with a length of 11.
     * ```
     */
    public async append(key: GlideString, value: GlideString): Promise<number> {
        return this.createWritePromise(createAppend(key, value));
    }

    /**
     * Pops one or more elements from the first non-empty list from the provided `keys`.
     *
     * @see {@link https://valkey.io/commands/lmpop/|valkey.io} for more details.
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param keys - An array of keys.
     * @param direction - The direction based on which elements are popped from - see {@link ListDirection}.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `count`: the maximum number of popped elements. If not specified, pops one member.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns A `Record` which stores the key name where elements were popped out and the array of popped elements.
     *     If no member could be popped, returns `null`.
     *
     * @example
     * ```typescript
     * await client.lpush("testKey", ["one", "two", "three"]);
     * await client.lpush("testKey2", ["five", "six", "seven"]);
     * const result = await client.lmpop(["testKey", "testKey2"], ListDirection.LEFT, 1L);
     * console.log(result);
     * // Output: { key: "testKey", elements: ["three"] }
     * ```
     */
    public async lmpop(
        keys: GlideString[],
        direction: ListDirection,
        options?: { count?: number } & DecoderOption,
    ): Promise<{ key: GlideString; elements: GlideString[] } | null> {
        return this.createWritePromise<GlideRecord<GlideString[]> | null>(
            createLMPop(keys, direction, options?.count),
            options,
        ).then((res) =>
            res === null
                ? null
                : res!.map((r) => {
                      return { key: r.key, elements: r.value };
                  })[0],
        );
    }

    /**
     * Blocks the connection until it pops one or more elements from the first non-empty list from the
     * provided `key`. `BLMPOP` is the blocking variant of {@link lmpop}.
     *
     * @see {@link https://valkey.io/commands/blmpop/|valkey.io} for more details.
     * @remarks When in cluster mode, all `keys` must map to the same hash slot.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param keys - An array of keys.
     * @param direction - The direction based on which elements are popped from - see {@link ListDirection}.
     * @param timeout - The number of seconds to wait for a blocking operation to complete. A value of `0` will block indefinitely.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `count`: the maximum number of popped elements. If not specified, pops one member.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns A `Record` which stores the key name where elements were popped out and the array of popped elements.
     *     If no member could be popped and the timeout expired, returns `null`.
     *
     * @example
     * ```typescript
     * await client.lpush("testKey", ["one", "two", "three"]);
     * await client.lpush("testKey2", ["five", "six", "seven"]);
     * const result = await client.blmpop(["testKey", "testKey2"], ListDirection.LEFT, 0.1, 1);
     * console.log(result"testKey");
     * // Output: { key: "testKey", elements: ["three"] }
     * ```
     */
    public async blmpop(
        keys: GlideString[],
        direction: ListDirection,
        timeout: number,
        options?: { count?: number } & DecoderOption,
    ): Promise<{ key: GlideString; elements: GlideString[] } | null> {
        return this.createWritePromise<GlideRecord<GlideString[]> | null>(
            createBLMPop(keys, direction, timeout, options?.count),
            options,
        ).then((res) =>
            res === null
                ? null
                : res!.map((r) => {
                      return { key: r.key, elements: r.value };
                  })[0],
        );
    }

    /**
     * Lists the currently active channels.
     * The command is routed to all nodes, and aggregates the response to a single array.
     *
     * @see {@link https://valkey.io/commands/pubsub-channels/|valkey.io} for more details.
     *
     * @param options - (Optional) Additional parameters:
     * - (Optional) `pattern`: A glob-style pattern to match active channels.
     *     If not provided, all active channels are returned.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns A list of currently active channels matching the given pattern.
     *          If no pattern is specified, all active channels are returned.
     *
     * @example
     * ```typescript
     * const channels = await client.pubsubChannels();
     * console.log(channels);
     * // Output: ["channel1", "channel2"]
     *
     * const newsChannels = await client.pubsubChannels("news.*");
     * console.log(newsChannels);
     * // Output: ["news.sports", "news.weather"]
     * ```
     */
    public async pubsubChannels(
        options?: { pattern?: GlideString } & DecoderOption,
    ): Promise<GlideString[]> {
        return this.createWritePromise(
            createPubSubChannels(options?.pattern),
            options,
        );
    }

    /**
     * Returns the number of unique patterns that are subscribed to by clients.
     *
     * Note: This is the total number of unique patterns all the clients are subscribed to,
     * not the count of clients subscribed to patterns.
     * The command is routed to all nodes, and aggregates the response to the sum of all pattern subscriptions.
     *
     * @see {@link https://valkey.io/commands/pubsub-numpat/|valkey.io} for more details.
     *
     * @returns The number of unique patterns.
     *
     * @example
     * ```typescript
     * const patternCount = await client.pubsubNumpat();
     * console.log(patternCount);
     * // Output: 3
     * ```
     */
    public async pubsubNumPat(): Promise<number> {
        return this.createWritePromise(createPubSubNumPat());
    }

    /**
     * Returns the number of subscribers (exclusive of clients subscribed to patterns) for the specified channels.
     *
     * @see {@link https://valkey.io/commands/pubsub-numsub/|valkey.io} for more details.
     * @remarks When in cluster mode, the command is routed to all nodes, and aggregates the response into a single list.
     *
     * @param channels - The list of channels to query for the number of subscribers.
     * @param options - (Optional) see {@link DecoderOption}.
     * @returns A list of the channel names and their numbers of subscribers.
     *
     * @example
     * ```typescript
     * const result1 = await client.pubsubNumsub(["channel1", "channel2"]);
     * console.log(result1);
     * // Output: [{ channel: "channel1", numSub: 3}, { channel: "channel2", numSub: 5 }]
     *
     * const result2 = await client.pubsubNumsub([]);
     * console.log(result2);
     * // Output: []
     * ```
     */
    public async pubsubNumSub(
        channels: GlideString[],
        options?: DecoderOption,
    ): Promise<{ channel: GlideString; numSub: number }[]> {
        return this.createWritePromise<GlideRecord<number>>(
            createPubSubNumSub(channels),
            options,
        ).then((res) =>
            res.map((r) => {
                return { channel: r.key, numSub: r.value };
            }),
        );
    }

    /**
     * Sorts the elements in the list, set, or sorted set at `key` and returns the result.
     *
     * The `sort` command can be used to sort elements based on different criteria and
     * apply transformations on sorted elements.
     *
     * To store the result into a new key, see {@link sortStore}.
     *
     * @see {@link https://valkey.io/commands/sort/|valkey.io} for more details.
     * @remarks When in cluster mode, both `key` and the patterns specified in {@link SortOptions.byPattern}
     * and {@link SortOptions.getPatterns} must map to the same hash slot. The use of {@link SortOptions.byPattern}
     * and {@link SortOptions.getPatterns} in cluster mode is supported since Valkey version 8.0.
     * @param key - The key of the list, set, or sorted set to be sorted.
     * @param options - (Optional) The {@link SortOptions} and {@link DecoderOption}.
     *
     * @returns An `Array` of sorted elements.
     *
     * @example
     * ```typescript
     * await client.hset("user:1", new Map([["name", "Alice"], ["age", "30"]]));
     * await client.hset("user:2", new Map([["name", "Bob"], ["age", "25"]]));
     * await client.lpush("user_ids", ["2", "1"]);
     * const result = await client.sort("user_ids", { byPattern: "user:*->age", getPattern: ["user:*->name"] });
     * console.log(result);
     * // Output: [ 'Bob', 'Alice' ]
     * // Returns a list of the names sorted by age
     * ```
     */
    public async sort(
        key: GlideString,
        options?: SortOptions & DecoderOption,
    ): Promise<(GlideString | null)[]> {
        return this.createWritePromise(createSort(key, options), options);
    }

    /**
     * Sorts the elements in the list, set, or sorted set at `key` and returns the result.
     *
     * The `sortReadOnly` command can be used to sort elements based on different criteria and
     * apply transformations on sorted elements.
     *
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * @see {@link https://valkey.io/commands/sort/|valkey.io} for more details.
     * @remarks Since Valkey version 7.0.0.
     * @remarks When in cluster mode, both `key` and the patterns specified in {@link SortOptions.byPattern}
     * and {@link SortOptions.getPatterns} must map to the same hash slot. The use of {@link SortOptions.byPattern}
     * and {@link SortOptions.getPatterns} in cluster mode is supported since Valkey version 8.0.
     * @param key - The key of the list, set, or sorted set to be sorted.
     * @param options - (Optional) The {@link SortOptions} and {@link DecoderOption}.
     * @returns An `Array` of sorted elements
     *
     * @example
     * ```typescript
     * await client.hset("user:1", new Map([["name", "Alice"], ["age", "30"]]));
     * await client.hset("user:2", new Map([["name", "Bob"], ["age", "25"]]));
     * await client.lpush("user_ids", ["2", "1"]);
     * const result = await client.sortReadOnly("user_ids", { byPattern: "user:*->age", getPattern: ["user:*->name"] });
     * console.log(result);
     * // Output: [ 'Bob', 'Alice' ]
     * // Returns a list of the names sorted by age
     * ```
     */
    public async sortReadOnly(
        key: GlideString,
        options?: SortOptions & DecoderOption,
    ): Promise<(GlideString | null)[]> {
        return this.createWritePromise(
            createSortReadOnly(key, options),
            options,
        );
    }

    /**
     * Sorts the elements in the list, set, or sorted set at `key` and stores the result in
     * `destination`.
     *
     * The `sort` command can be used to sort elements based on different criteria and
     * apply transformations on sorted elements, and store the result in a new key.
     *
     * To get the sort result without storing it into a key, see {@link sort} or {@link sortReadOnly}.
     *
     * @see {@link https://valkey.io/commands/sort|valkey.io} for more details.
     * @remarks When in cluster mode, `key`, `destination` and the patterns specified in {@link SortOptions.byPattern}
     * and {@link SortOptions.getPatterns} must map to the same hash slot. The use of {@link SortOptions.byPattern}
     * and {@link SortOptions.getPatterns} in cluster mode is supported since Valkey version 8.0.
     *
     * @param key - The key of the list, set, or sorted set to be sorted.
     * @param destination - The key where the sorted result will be stored.
     * @param options - (Optional) The {@link SortOptions}.
     * @returns The number of elements in the sorted key stored at `destination`.
     *
     * @example
     * ```typescript
     * await client.hset("user:1", new Map([["name", "Alice"], ["age", "30"]]));
     * await client.hset("user:2", new Map([["name", "Bob"], ["age", "25"]]));
     * await client.lpush("user_ids", ["2", "1"]);
     * const sortedElements = await client.sortStore("user_ids", "sortedList", { byPattern: "user:*->age", getPattern: ["user:*->name"] });
     * console.log(sortedElements);
     * // Output: 2
     * // number of elements sorted and stored
     *
     * console.log(await client.lrange("sortedList", 0, -1));
     * // Output: [ 'Bob', 'Alice' ]
     * // Returns a list of the names sorted by age stored in `sortedList`
     * ```
     */
    public async sortStore(
        key: GlideString,
        destination: GlideString,
        options?: SortOptions,
    ): Promise<number> {
        return this.createWritePromise(createSort(key, options, destination));
    }

    /**
     * @internal
     */
    // TODO #6669: move to module level so the request is testable
    protected createClientRequest(
        options: BaseClientConfiguration,
    ): connection_request.IConnectionRequest {
        const readFrom = options.readFrom
            ? this.MAP_READ_FROM_STRATEGY[options.readFrom]
            : connection_request.ReadFrom.Primary;

        const creds = options.credentials;

        // Build a protobuf AuthenticationInfo
        let authenticationInfo:
            connection_request.IAuthenticationInfo | undefined;

        if (creds) {
            if ("iamConfig" in creds) {
                if (!creds.username) {
                    throw new ConfigurationError(
                        "IAM authentication requires a username.",
                    );
                }

                const iamCredentials = connection_request.IamCredentials.create(
                    {
                        clusterName: creds.iamConfig.clusterName,
                        region: creds.iamConfig.region,
                        serviceType:
                            creds.iamConfig.service === ServiceType.Elasticache
                                ? connection_request.ServiceType.ELASTICACHE
                                : connection_request.ServiceType.MEMORYDB,
                        // leave undefined if not provided (optional field)
                        refreshIntervalSeconds:
                            creds.iamConfig.refreshIntervalSeconds,
                    },
                );

                authenticationInfo =
                    connection_request.AuthenticationInfo.create({
                        username: creds.username, // REQUIRED for IAM
                        iamCredentials,
                        // do NOT set password in IAM mode
                    });
            } else if ("password" in creds) {
                // Password branch
                authenticationInfo =
                    connection_request.AuthenticationInfo.create({
                        username: creds.username ?? "", // optional
                        password: creds.password ?? "", // empty means “no password”
                    });
            } else {
                authenticationInfo = undefined;
            }
        }

        const protocol = options.protocol as
            connection_request.ProtocolVersion | undefined;

        // Validate that clientAz is set when using AZ affinity strategies
        if (
            (options.readFrom === "AZAffinity" ||
                options.readFrom === "AZAffinityReplicasAndPrimary") &&
            !options.clientAz
        ) {
            throw new ConfigurationError(
                `clientAz must be set when readFrom is set to ${options.readFrom}`,
            );
        }

        // Configure client-side cache if provided
        let clientSideCache: connection_request.IClientSideCache | undefined;

        if (options.clientSideCache) {
            const cache = options.clientSideCache;
            clientSideCache = connection_request.ClientSideCache.create({
                cacheId: cache.cacheId,
                maxCacheKb: cache.maxCacheKb,
                entryTtlMs: cache.entryTtlMs,
                evictionPolicy: cache.evictionPolicy,
                enableMetrics: cache.enableMetrics,
                serverAssisted: cache.serverAssisted,
            });
        }

        const request: connection_request.IConnectionRequest = {
            protocol,
            clientName: options.clientName,
            libName: resolveClientLibraryName(
                options.libName,
                options.clientInfoTag,
            ),
            addresses: options.addresses,
            tlsMode: options.useTLS
                ? connection_request.TlsMode.SecureTls
                : connection_request.TlsMode.NoTls,
            requestTimeout: options.requestTimeout,
            clusterModeEnabled: false,
            readFrom,
            authenticationInfo,
            databaseId: options.databaseId,
            inflightRequestsLimit: options.inflightRequestsLimit,
            clientAz: options.clientAz ?? null,
            connectionRetryStrategy: options.connectionBackoff,
            lazyConnect: options.lazyConnect ?? false,
            clientSideCache,
        };

        if (options.compression) {
            validateCompressionConfiguration(options.compression);
            request.compressionConfig = compressionConfigToProtobuf(
                options.compression,
                connection_request,
            );
        }

        if (options.clientCircuitBreaker) {
            const cb = options.clientCircuitBreaker;

            if (cb.windowSizeMs !== undefined && cb.windowSizeMs <= 0) {
                throw new ConfigurationError("windowSizeMs must be positive");
            }

            if (
                cb.failureRateThreshold !== undefined &&
                (cb.failureRateThreshold <= 0.0 ||
                    cb.failureRateThreshold > 1.0)
            ) {
                throw new ConfigurationError(
                    "failureRateThreshold must be between 0.0 (exclusive) and 1.0 (inclusive)",
                );
            }

            if (cb.minErrors !== undefined && cb.minErrors <= 0) {
                throw new ConfigurationError("minErrors must be positive");
            }

            if (cb.openTimeoutMs !== undefined && cb.openTimeoutMs <= 0) {
                throw new ConfigurationError("openTimeoutMs must be positive");
            }

            if (
                cb.consecutiveSuccesses !== undefined &&
                cb.consecutiveSuccesses <= 0
            ) {
                throw new ConfigurationError(
                    "consecutiveSuccesses must be positive",
                );
            }

            request.clientCircuitBreaker = {
                windowSizeMs: cb.windowSizeMs,
                failureRateThreshold: cb.failureRateThreshold,
                minErrors: cb.minErrors,
                openTimeoutMs: cb.openTimeoutMs,
                countTimeouts: cb.countTimeouts,
                consecutiveSuccesses: cb.consecutiveSuccesses,
            };
        }

        return request;
    }

    /**
     * @internal
     */
    protected configureAdvancedConfigurationBase(
        options: AdvancedBaseClientConfiguration,
        request: connection_request.IConnectionRequest,
    ): void {
        request.connectionTimeout =
            options.connectionTimeout ??
            DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS;

        // Set TCP_NODELAY if explicitly configured
        if (options.tcpNoDelay !== undefined) {
            request.tcpNodelay = options.tcpNoDelay;
        }

        // Set PubSub reconciliation interval if explicitly configured
        if (options.pubsubReconciliationIntervalMs !== undefined) {
            request.pubsubReconciliationIntervalMs =
                options.pubsubReconciliationIntervalMs;
        }

        if (options.tlsAdvancedConfiguration) {
            applyTlsAdvancedConfiguration(
                options.tlsAdvancedConfiguration,
                request,
            );
        }
    }

    /**
     * @internal
     * Establishes the connection to the server.
     */
    protected async connectToServer(
        options: BaseClientConfiguration,
    ): Promise<void> {
        const request = this.createClientRequest(options);

        if (options.addressResolver) {
            this.addressResolverKey = registerAddressResolver(
                options.addressResolver,
            );
            request.addressResolverKey = this.addressResolverKey;
        }

        try {
            const connectionRequestBytes = Buffer.from(
                connection_request.ConnectionRequest.encode(
                    connection_request.ConnectionRequest.create(request),
                ).finish(),
            );

            this.clientHandle = await CreateDirectClient(
                connectionRequestBytes,
                this.handleResponsesAvailable,
            );
            Logger.log(
                "info",
                "Client lifetime",
                "Client connection established",
            );
        } catch (err) {
            if (this.addressResolverKey) {
                removeAddressResolver(this.addressResolverKey);
                this.addressResolverKey = undefined;
            }

            throw err;
        }
    }

    /**
     * Callback invoked when responses are available.
     *
     * Response handling failures must not escape this callback because one
     * exception would stop the rest of the drained responses from processing.
     * @internal
     */
    private handleResponsesAvailable = (): void => {
        if (!this.clientHandle) return;

        const responses = this.clientHandle.drainResponses();

        for (const response of responses) {
            try {
                this.handleResponse(response);
            } catch (err) {
                Logger.log(
                    "error",
                    "Response handling",
                    `Unhandled exception while handling response: '${err}'`,
                );
            }
        }
    };

    /**
     * Returns the internal client ID used by the Rust core for scope/pool registration.
     * @internal
     */
    public getClientId(): number {
        return this.clientHandle?.clientId ?? -1;
    }

    /**
     *  Terminate the client by closing all associated resources and any active promises.
     *  All open promises will be closed with an exception.
     * @param errorMessage - If defined, this error message will be passed along with the exceptions when closing all open promises.
     */
    public close(errorMessage?: string): void {
        this.isClosed = true;
        this.promiseCallbackFunctions.forEach(([, reject]) => {
            reject(new ClosingError(errorMessage || ""));
        });

        // Handle pubsub futures
        this.pubsubFutures.forEach(([, reject]) => {
            reject(new ClosingError(errorMessage || ""));
        });

        // Clean up address resolver from the global registry
        if (this.addressResolverKey) {
            removeAddressResolver(this.addressResolverKey);
            this.addressResolverKey = undefined;
        }

        // Clean up OTel spans for in-flight requests to prevent memory leaks
        for (const spanPtr of this.otelSpanPointers.values()) {
            dropOtelSpan(spanPtr);
        }

        this.otelSpanPointers.clear();

        if (this.clientHandle) {
            try {
                this.clientHandle.close();
            } catch (err) {
                Logger.log(
                    "warn",
                    "Client lifetime",
                    `Error closing client handle: ${err}`,
                );
            }

            this.clientHandle = null;
        }

        Logger.log("info", "Client lifetime", "disposing of client");
    }

    /**
     * @internal
     * Serialize a client configuration into the protobuf bytes expected by the
     * Rust pool APIs (`createPool`, etc.).
     *
     * Exposed as a public static so that `ClientPool` (which is not a
     * BaseClient subclass) can serialise connection config without making a
     * real connection.
     */
    public static serializeConnectionRequest(
        options: BaseClientConfiguration,
        constructor: (options?: BaseClientConfiguration) => BaseClient,
    ): { bytes: Uint8Array; resolverKey: string | undefined } {
        const instance = constructor(options);
        const request = instance.createClientRequest(options);

        let resolverKey: string | undefined;
        if (options.addressResolver) {
            // Register the resolver so Rust can find it by key when creating
            // pool connections. The key must be embedded in the serialised
            // request so every new pool connection can locate the callback.
            resolverKey = registerAddressResolver(options.addressResolver);
            request.addressResolverKey = resolverKey;
        }

        const bytes = Buffer.from(
            connection_request.ConnectionRequest.encode(
                connection_request.ConnectionRequest.create(request),
            ).finish(),
        );
        return { bytes, resolverKey };
    }

    /**
     * @internal
     * Wrap an already-built {@link GlideClientHandle} in a client instance.
     *
     * Used by the pool layer: after `pool_build_handle` returns a handle for a
     * pool-managed connection, the pool calls this method to get a fully-typed
     * GlideClient / GlideClusterClient without going through `connectToServer`.
     *
     * The `options` argument is used only to configure defaults (decoder,
     * config snapshot) — no network connection is made.
     */
    protected static createClientFromHandle<TConnection extends BaseClient>(
        handle: GlideClientHandle,
        options: BaseClientConfiguration,
        constructor: (options?: BaseClientConfiguration) => TConnection,
    ): TConnection {
        const connection = constructor(options);
        connection.clientHandle = handle;
        return connection;
    }

    /**
     * @internal
     * Creates and connects a client instance.
     */
    protected static async createClientInternal<TConnection extends BaseClient>(
        options: BaseClientConfiguration,
        constructor: (options?: BaseClientConfiguration) => TConnection,
    ): Promise<TConnection> {
        const overallStart = Date.now();

        const connection = constructor(options);
        const connectStart = Date.now();
        await connection.connectToServer(options);
        const connectTime = Date.now() - connectStart;
        Logger.log(
            "info",
            "Client lifetime",
            `connected to server in ${connectTime}ms`,
        );

        const totalTime = Date.now() - overallStart;
        Logger.log(
            "info",
            "Client lifetime",
            `total client creation time: ${totalTime}ms`,
        );
        return connection;
    }

    /**
     * Update the current connection with a new password.
     *
     * This method is useful in scenarios where the server password has changed or when utilizing short-lived passwords for enhanced security.
     * It allows the client to update its password to reconnect upon disconnection without the need to recreate the client instance.
     * This ensures that the internal reconnection mechanism can handle reconnection seamlessly, preventing the loss of in-flight commands.
     *
     * This method updates the client's internal password configuration and does not perform password rotation on the server side.
     *
     * @param password - `String | null`. The new password to update the current password, or `null` to remove the current password.
     * @param immidiateAuth - A `boolean` flag. If `true`, the client will authenticate immediately with the new password against all connections, Using `AUTH` command.
     *                 If password supplied is an empty string, the client will not perform auth and instead a warning will be returned.
     *                 The default is `false`.
     *
     * @example
     * ```typescript
     * await client.updateConnectionPassword("newPassword", true)
     * // Output: "OK"
     * ```
     */
    async updateConnectionPassword(
        password: string | null,
        immediateAuth = false,
    ) {
        // If we’re on IAM, forbid password updates to avoid confusion.
        const creds = this.config?.credentials;
        const usingIam = !!creds && "iamConfig" in creds;

        if (usingIam) {
            throw new ConfigurationError(
                "updateConnectionPassword is not supported when IAM authentication is enabled.",
            );
        }

        const updateConnectionPassword =
            command_request.UpdateConnectionPassword.create({
                password,
                immediateAuth,
            });

        const response = await this.createUpdateConnectionPasswordPromise(
            updateConnectionPassword,
        );

        if (response === "OK" && !this.config?.credentials) {
            this.config = {
                ...this.config!,
                credentials: {
                    ...this.config!.credentials,
                    password: password ? password : "",
                },
            };
        }

        return response;
    }

    /**
     * Manually refresh the IAM token for the current connection.
     *
     * This method is only available if the client was created with IAM authentication.
     * It triggers an immediate refresh of the IAM token and updates the connection.
     *
     * @throws ConfigurationError if the client is not using IAM authentication.
     * @example
     * ```typescript
     * await client.refreshToken();
     * ```
     */
    public async refreshIamToken(): Promise<GlideString> {
        if (
            !this.config?.credentials ||
            !("iamConfig" in this.config.credentials)
        ) {
            throw new ConfigurationError(
                "refreshIamToken is only available when IAM authentication is enabled.",
            );
        }

        const refresh = command_request.RefreshIamToken.create({});
        const response = await this.createRefreshIamTokenPromise(refresh);
        return response; // "OK"
    }
    /**
     * Get the cache hit rate (hits / total requests).
     *
     * @returns The cache hit rate as a number between 0.0 and 1.0.
     * @throws RequestError if client-side caching is not enabled or metrics tracking is disabled.
     * @example
     * ```typescript
     * const hitRate = await client.getCacheHitRate();
     * console.log(`Cache hit rate: ${(hitRate * 100).toFixed(2)}%`);
     * // Output: Cache hit rate: 85.50%
     * ```
     */
    public async getCacheHitRate(): Promise<number> {
        return await this.getCacheMetrics(
            command_request.CacheMetricsType.HitRate,
        );
    }
    /**
     * Get the cache miss rate (misses / total requests).
     *
     * @returns The cache miss rate as a number between 0.0 and 1.0.
     * @throws RequestError if client-side caching is not enabled or metrics tracking is disabled.
     * @example
     * ```typescript
     * const missRate = await client.getCacheMissRate();
     * console.log(`Cache miss rate: ${(missRate * 100).toFixed(2)}%`);
     * // Output: Cache miss rate: 14.50%
     * ```
     */
    public async getCacheMissRate(): Promise<number> {
        return await this.getCacheMetrics(
            command_request.CacheMetricsType.MissRate,
        );
    }
    /**
     * Get the current number of entries in the client-side cache.
     *
     * @returns The number of entries in the cache.
     * @throws RequestError if client-side caching is not enabled.
     * @example
     * ```typescript
     * const entryCount = await client.getCacheEntryCount();
     * console.log(`Cache entry count: ${entryCount}`);
     * // Output: Cache entry count: 1500
     * ```
     */
    public async getCacheEntryCount(): Promise<number> {
        return await this.getCacheMetrics(
            command_request.CacheMetricsType.EntryCount,
        );
    }
    /**
     * Get the total number of entries evicted from the client-side cache due to memory constraints.
     *
     * @returns The number of evictions.
     * @throws RequestError if client-side caching is not enabled or metrics tracking is disabled.
     * @example
     * ```typescript
     * const evictions = await client.getCacheEvictions();
     * console.log(`Cache evictions: ${evictions}`);
     * // Output: Cache evictions: 100
     * ```
     */
    public async getCacheEvictions(): Promise<number> {
        return await this.getCacheMetrics(
            command_request.CacheMetricsType.Evictions,
        );
    }

    /**
     * Get the total number of entries expired from the client-side cache.
     *
     * @returns The number of expirations.
     * @throws RequestError if client-side caching is not enabled or metrics tracking is disabled.
     * @example
     * ```typescript
     * const expirations = await client.getCacheExpirations();
     * console.log(`Cache expirations: ${expirations}`);
     * // Output: Cache expirations: 250
     * ```
     */
    public async getCacheExpirations(): Promise<number> {
        return await this.getCacheMetrics(
            command_request.CacheMetricsType.Expirations,
        );
    }

    /**
     * Get the total number of cache lookups (hits + misses).
     *
     * @returns The total number of cache lookups.
     * @throws RequestError if client-side caching is not enabled or metrics tracking is disabled.
     * @example
     * ```typescript
     * const totalLookups = await client.getCacheTotalLookups();
     * console.log(`Total cache lookups: ${totalLookups}`);
     * // Output: Total cache lookups: 5000
     * ```
     */
    public async getCacheTotalLookups(): Promise<number> {
        return await this.getCacheMetrics(
            command_request.CacheMetricsType.TotalLookups,
        );
    }

    /**
     * Subscribes the client to the specified channels (non-blocking).
     * Returns immediately without waiting for subscription confirmation.
     *
     * @see {@link https://valkey.io/commands/subscribe/|valkey.io} for details.
     *
     * @param channels - A collection of channel names to subscribe to.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A promise that resolves immediately.
     *
     * @example
     * ```typescript
     * await client.subscribeLazy(new Set(["news", "updates"]));
     * ```
     */
    public async subscribeLazy(
        channels: Iterable<GlideString>,
        options?: DecoderOption,
    ): Promise<void> {
        const channelsArray = Array.from(channels);
        return this.createWritePromise(
            createSubscribeLazy(channelsArray),
            options,
        );
    }

    /**
     * Subscribes the client to the specified channels (blocking).
     * Waits for subscription confirmation or until timeout.
     *
     * @see {@link https://valkey.io/commands/subscribe/|valkey.io} for details.
     *
     * @param channels - A collection of channel names to subscribe to.
     * @param timeoutMs - Maximum time in milliseconds to wait. Use 0 for indefinite wait.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A promise that resolves when subscription is confirmed or timeout occurs.
     *
     * @example
     * ```typescript
     * // Wait up to 5 seconds
     * await client.subscribe(new Set(["news"]), 5000);
     * // Wait indefinitely
     * await client.subscribe(new Set(["news"]), 0);
     * ```
     */
    public async subscribe(
        channels: Iterable<GlideString>,
        timeoutMs: number,
        options?: DecoderOption,
    ): Promise<void> {
        const channelsArray = Array.from(channels);
        return this.createWritePromise(
            createSubscribe(channelsArray, timeoutMs),
            options,
        );
    }

    /**
     * Subscribes the client to the specified patterns (non-blocking).
     * Returns immediately without waiting for subscription confirmation.
     *
     * @see {@link https://valkey.io/commands/psubscribe/|valkey.io} for details.
     *
     * @param patterns - An array of glob-style patterns to subscribe to.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A promise that resolves immediately.
     *
     * @example
     * ```typescript
     * await client.psubscribeLazy(["news.*", "updates.*"]);
     * ```
     */
    public async psubscribeLazy(
        patterns: Iterable<GlideString>,
        options?: DecoderOption,
    ): Promise<void> {
        const patternsArray = Array.from(patterns);
        return this.createWritePromise(
            createPSubscribeLazy(patternsArray),
            options,
        );
    }

    /**
     * Subscribes the client to the specified patterns (blocking).
     * Waits for subscription confirmation or until timeout.
     *
     * @see {@link https://valkey.io/commands/psubscribe/|valkey.io} for details.
     *
     * @param patterns - An array of glob-style patterns to subscribe to.
     * @param timeoutMs - Maximum time in milliseconds to wait. Use 0 for indefinite wait.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A promise that resolves when subscription is confirmed or timeout occurs.
     *
     * @example
     * ```typescript
     * await client.psubscribe(["news.*"], 5000);
     * ```
     */
    public async psubscribe(
        patterns: Iterable<GlideString>,
        timeoutMs: number,
        options?: DecoderOption,
    ): Promise<void> {
        const patternsArray = Array.from(patterns);
        return this.createWritePromise(
            createPSubscribe(patternsArray, timeoutMs),
            options,
        );
    }

    /**
     * Unsubscribes the client from the specified channels (non-blocking).
     * Pass null or ALL_CHANNELS to unsubscribe from all exact channels.
     *
     * @see {@link https://valkey.io/commands/unsubscribe/|valkey.io} for details.
     *
     * @param channels - Channel names to unsubscribe from, or null for all channels.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A promise that resolves immediately.
     *
     * @example
     * ```typescript
     * await client.unsubscribeLazy(new Set(["news"]));
     * // Unsubscribe from all channels
     * await client.unsubscribeLazy(ALL_CHANNELS);
     * ```
     */
    public async unsubscribeLazy(
        channels?: Iterable<GlideString> | null,
        options?: DecoderOption,
    ): Promise<void> {
        const channelsArray = channels ? Array.from(channels) : undefined;
        return this.createWritePromise(
            createUnsubscribeLazy(channelsArray),
            options,
        );
    }

    /**
     * Unsubscribes the client from the specified channels (blocking).
     * Pass null or ALL_CHANNELS to unsubscribe from all exact channels.
     *
     * @see {@link https://valkey.io/commands/unsubscribe/|valkey.io} for details.
     *
     * @param channels - Channel names to unsubscribe from, or null for all channels.
     * @param timeoutMs - Maximum time in milliseconds to wait. Use 0 for indefinite wait.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A promise that resolves when unsubscription is confirmed or timeout occurs.
     *
     * @example
     * ```typescript
     * await client.unsubscribe(new Set(["news"]), 5000);
     * // Unsubscribe from all channels with timeout
     * await client.unsubscribe(ALL_CHANNELS, 5000);
     * ```
     */
    public async unsubscribe(
        channels: Iterable<GlideString> | null,
        timeoutMs: number,
        options?: DecoderOption,
    ): Promise<void> {
        const channelsArray = channels ? Array.from(channels) : [];
        return this.createWritePromise(
            createUnsubscribe(channelsArray, timeoutMs),
            options,
        );
    }

    /**
     * Unsubscribes the client from the specified patterns (non-blocking).
     * Pass null or ALL_PATTERNS to unsubscribe from all patterns.
     *
     * @see {@link https://valkey.io/commands/punsubscribe/|valkey.io} for details.
     *
     * @param patterns - Pattern names to unsubscribe from, or null for all patterns.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A promise that resolves immediately.
     *
     * @example
     * ```typescript
     * await client.punsubscribeLazy(new Set(["news.*"]));
     * // Unsubscribe from all patterns
     * await client.punsubscribeLazy(ALL_PATTERNS);
     * ```
     */
    public async punsubscribeLazy(
        patterns?: Iterable<GlideString> | null,
        options?: DecoderOption,
    ): Promise<void> {
        const patternsArray = patterns ? Array.from(patterns) : undefined;
        return this.createWritePromise(
            createPUnsubscribeLazy(patternsArray),
            options,
        );
    }

    /**
     * Unsubscribes the client from the specified patterns (blocking).
     * Pass null or ALL_PATTERNS to unsubscribe from all patterns.
     *
     * @see {@link https://valkey.io/commands/punsubscribe/|valkey.io} for details.
     *
     * @param patterns - Pattern names to unsubscribe from, or null for all patterns.
     * @param timeoutMs - Maximum time in milliseconds to wait. Use 0 for indefinite wait.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A promise that resolves when unsubscription is confirmed or timeout occurs.
     *
     * @example
     * ```typescript
     * await client.punsubscribe(new Set(["news.*"]), 5000);
     * // Unsubscribe from all patterns with timeout
     * await client.punsubscribe(ALL_PATTERNS, 5000);
     * ```
     */
    public async punsubscribe(
        patterns: Iterable<GlideString> | null,
        timeoutMs: number,
        options?: DecoderOption,
    ): Promise<void> {
        const patternsArray = patterns ? Array.from(patterns) : [];
        return this.createWritePromise(
            createPUnsubscribe(patternsArray, timeoutMs),
            options,
        );
    }

    /**
     * @internal
     * Helper method to parse GetSubscriptions response from Rust core.
     * Converts array response to structured object with desired and actual subscriptions.
     *
     * The Rust core returns subscription data as a Value::Map with string keys ("Exact", "Pattern", "Sharded").
     * The NAPI layer converts this to GlideRecord format: [{key: "Exact", value: [...]}, ...]
     *
     * @param response - The response array from Rust core with format:
     *   ["desired", GlideRecord, "actual", GlideRecord]
     * @returns Parsed subscription state with desired and actual subscriptions
     */
    protected parseGetSubscriptionsResponse<T extends number>(
        response: unknown[],
    ): {
        desiredSubscriptions: Partial<Record<T, Set<GlideString>>>;
        actualSubscriptions: Partial<Record<T, Set<GlideString>>>;
    } {
        // Response format: ["desired", GlideRecord, "actual", GlideRecord]
        if (!Array.isArray(response) || response.length !== 4) {
            throw new Error(
                `Invalid GetSubscriptions response format: expected array of length 4, got ${JSON.stringify(response)}`,
            );
        }

        // Map string mode names to numeric enum values
        const modeNameToNumber: Record<string, number> = {
            Exact: 0,
            Pattern: 1,
            Sharded: 2,
        };

        const desiredSubscriptions: Partial<Record<T, Set<GlideString>>> = {};
        const actualSubscriptions: Partial<Record<T, Set<GlideString>>> = {};

        // Helper function to parse subscription data from GlideRecord format
        const parseSubscriptionData = (
            data: unknown,
            target: Partial<Record<T, Set<GlideString>>>,
        ): void => {
            if (!Array.isArray(data)) {
                return;
            }

            for (const entry of data) {
                if (
                    !entry ||
                    typeof entry !== "object" ||
                    !("key" in entry) ||
                    !("value" in entry)
                ) {
                    continue;
                }

                // Key might be a Buffer, convert to string
                const modeName =
                    entry.key instanceof Buffer
                        ? entry.key.toString()
                        : String(entry.key);
                const modeKey = modeNameToNumber[modeName] as T;

                if (modeKey !== undefined && Array.isArray(entry.value)) {
                    target[modeKey] = new Set(entry.value as GlideString[]);
                }
            }
        };

        // Parse desired and actual subscriptions
        parseSubscriptionData(response[1], desiredSubscriptions);
        parseSubscriptionData(response[3], actualSubscriptions);

        return { desiredSubscriptions, actualSubscriptions };
    }

    /**
     * Return a statistics
     *
     * @return Return an object that contains the statistics collected internally by GLIDE core
     */
    public getStatistics(): object {
        return getStatistics();
    }
}
