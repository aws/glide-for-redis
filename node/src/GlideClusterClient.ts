/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import {
    ClusterScanCursor,
    GlideClientHandle,
    Script,
} from "../build-ts/native";
import {
    command_request,
    connection_request,
} from "../build-ts/ProtobufMessage";
import {
    AdvancedBaseClientConfiguration,
    BaseClient,
    BaseClientConfiguration,
    Decoder,
    DecoderOption,
    GlideRecord,
    GlideReturnType,
    GlideString,
    PubSubMsg,
    convertGlideRecordToRecord,
    isGlideRecord,
} from "./BaseClient";
import { RequestError } from "./Errors";
import { ClusterBatch } from "./Batch";
import {
    ClientPauseMode,
    ClientTrackingInfo,
    ClusterBatchOptions,
    ClusterScanOptions,
    FlushMode,
    FunctionListOptions,
    FunctionListResponse,
    FunctionRestorePolicy,
    FunctionStatsSingleResponse,
    InfoOptions,
    LatencyEntry,
    LatencyEventInfo,
    LolwutOptions,
    MemoryStats,
    createClientGetName,
    createClientId,
    createClientPause,
    createClientTrackingInfo,
    createClientUnpause,
    createConfigGet,
    createConfigResetStat,
    createConfigRewrite,
    createConfigSet,
    createCustomCommand,
    createDBSize,
    createEcho,
    createFCall,
    createFCallReadOnly,
    createFlushAll,
    createFlushDB,
    createFunctionDelete,
    createFunctionDump,
    createFunctionFlush,
    createFunctionKill,
    createFunctionList,
    createFunctionLoad,
    createFunctionRestore,
    createFunctionStats,
    createGetSubscriptions,
    createInfo,
    createLastSave,
    createLatencyHistory,
    createLatencyLatest,
    createLatencyReset,
    createLolwut,
    createMemoryDoctor,
    createMemoryMallocStats,
    createMemoryPurge,
    createMemoryStats,
    parseLatencyHistoryResponse,
    parseLatencyLatestResponse,
    parseMemoryStatsResponse,
    createSave,
    createBgSave,
    createBgRewriteAof,
    createPing,
    createPubSubShardNumSub,
    createPublish,
    createPubsubShardChannels,
    createRandomKey,
    createScriptExists,
    createScriptFlush,
    createScriptKill,
    createSSubscribeLazy,
    createSSubscribe,
    createSUnsubscribeLazy,
    createSUnsubscribe,
    createTime,
    createUnWatch,
    parseClientTrackingInfoResponse,
} from "./Commands";

/**
 * Constant representing all sharded channels.
 * Use this to unsubscribe from all sharded channel subscriptions at once..
 *
 * @example
 * ```typescript
 * await client.sunsubscribeLazy(ALL_SHARDED_CHANNELS);
 * ```
 */
export const ALL_SHARDED_CHANNELS = null;

/** An extension to command option types with {@link Routes}. */
export interface RouteOption {
    /**
     * Specifies the routing configuration for the command.
     * The client will route the command to the nodes defined by `route`.
     */
    route?: Routes;
}

/**
 * Represents a manually configured interval for periodic checks.
 */
export interface PeriodicChecksManualInterval {
    /**
     * The duration in seconds for the interval between periodic checks.
     */
    duration_in_sec: number;
}

/**
 * Periodic checks configuration.
 */
export type PeriodicChecks =
    /**
     * Enables the periodic checks with the default configurations.
     */
    | "enabledDefaultConfigs"
    /**
     * Disables the periodic checks.
     */
    | "disabled"
    /**
     * Manually configured interval for periodic checks.
     */
    | PeriodicChecksManualInterval;

/* eslint-disable-next-line @typescript-eslint/no-namespace */
export namespace GlideClusterClientConfiguration {
    /**
     * Enum representing pubsub subscription modes.
     * @see {@link https://valkey.io/docs/topics/pubsub/|Valkey PubSub Documentation} for more details.
     */
    export enum PubSubChannelModes {
        /**
         * Use exact channel names.
         */
        Exact = 0,

        /**
         * Use channel name patterns.
         */
        Pattern = 1,

        /**
         * Use sharded pubsub. Available since Valkey version 7.0.
         */
        Sharded = 2,
    }
    /**
     * Configuration for Pub/Sub subscriptions that the client will establish upon connection.
     */
    export interface PubSubSubscriptions {
        /**
         * Channels and patterns by modes.
         */
        channelsAndPatterns: Partial<Record<PubSubChannelModes, Set<string>>>;

        /**
         * Optional callback to accept the incoming messages.
         */
        /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
        callback?: (msg: PubSubMsg, context: any) => void;

        /**
         * Arbitrary context to pass to the callback.
         */
        /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
        context?: any;
    }
}

/**
 * Represents the subscription state for a cluster client.
 *
 * @remarks
 * This interface provides information about the current PubSub subscriptions for a cluster client.
 * It includes both the desired subscriptions (what the client wants to maintain) and the actual
 * subscriptions (what is currently established on the server).
 *
 * The subscriptions are organized by channel mode:
 * - {@link GlideClusterClientConfiguration.PubSubChannelModes.Exact | Exact}: Exact channel names
 * - {@link GlideClusterClientConfiguration.PubSubChannelModes.Pattern | Pattern}: Channel patterns using glob-style matching
 * - {@link GlideClusterClientConfiguration.PubSubChannelModes.Sharded | Sharded}: Sharded channels (available since Valkey 7.0)
 *
 * @example
 * ```typescript
 * const state = await clusterClient.getSubscriptions();
 * console.log("Desired exact channels:", state.desiredSubscriptions[GlideClusterClientConfiguration.PubSubChannelModes.Exact]);
 * console.log("Actual sharded channels:", state.actualSubscriptions[GlideClusterClientConfiguration.PubSubChannelModes.Sharded]);
 * ```
 */
export interface ClusterPubSubState {
    /**
     * Desired subscriptions organized by channel mode.
     * These are the subscriptions the client wants to maintain.
     */
    desiredSubscriptions: Partial<
        Record<
            GlideClusterClientConfiguration.PubSubChannelModes,
            Set<GlideString>
        >
    >;

    /**
     * Actual subscriptions currently active on the server.
     * These are the subscriptions that are actually established.
     */
    actualSubscriptions: Partial<
        Record<
            GlideClusterClientConfiguration.PubSubChannelModes,
            Set<GlideString>
        >
    >;
}

/**
 * Configuration options for creating a {@link GlideClusterClient | GlideClusterClient}.
 *
 * Extends {@link BaseClientConfiguration | BaseClientConfiguration} with properties specific to `GlideClusterClient`, such as periodic topology checks
 * and Pub/Sub subscription settings.
 *
 * @remarks
 * This configuration allows you to tailor the client's behavior when connecting to a Valkey GLIDE Cluster.
 *
 * - **Periodic Topology Checks**: Use `periodicChecks` to configure how the client performs periodic checks to detect changes in the cluster's topology.
 *   - `"enabledDefaultConfigs"`: Enables periodic checks with default configurations.
 *   - `"disabled"`: Disables periodic topology checks.
 *   - `{ duration_in_sec: number }`: Manually configure the interval for periodic checks.
 * - **Client-Side Caching**: Use `clientSideCache` (inherited from BaseClientConfiguration) to enable local caching for improved performance.
 * - **Pub/Sub Subscriptions**: Predefine Pub/Sub channels and patterns to subscribe to upon connection establishment.
 *   - Supports exact channels, patterns, and sharded channels (available since Valkey version 7.0).
 *
 * @example
 * ```typescript
 * const config: GlideClusterClientConfiguration = {
 *   addresses: [
 *     { host: 'cluster-node-1.example.com', port: 6379 },
 *     { host: 'cluster-node-2.example.com', port: 6379 },
 *   ],
 *   databaseId: 5, // Connect to database 5 (requires Valkey 9.0+ with multi-database cluster mode)
 *   clientSideCache: ClientSideCache.create(2048, 0), // Enable 2MB client-side cache, no TTL
 *   periodicChecks: {
 *     duration_in_sec: 30, // Perform periodic checks every 30 seconds
 *   },
 *   pubsubSubscriptions: {
 *     channelsAndPatterns: {
 *       [GlideClusterClientConfiguration.PubSubChannelModes.Pattern]: new Set(['cluster.*']),
 *     },
 *     callback: (msg) => {
 *       console.log(`Received message on ${msg.channel}:`, msg.payload);
 *     },
 *   },
 * };
 * ```
 */
export type GlideClusterClientConfiguration = BaseClientConfiguration & {
    /**
     * Configure the periodic topology checks.
     * These checks evaluate changes in the cluster's topology, triggering a slot refresh when detected.
     * Periodic checks ensure a quick and efficient process by querying a limited number of nodes.
     * If not set, `enabledDefaultConfigs` will be used.
     */
    periodicChecks?: PeriodicChecks;

    /**
     * The maximum number of requests to buffer in the recovery queue when a cluster
     * reconnect is in progress. Buffered requests are retried transparently after
     * reconnection. Requests beyond this limit are failed immediately to provide
     * bounded memory usage. If not set, a default value of 1000 will be used.
     * Set to 0 to disable the recovery queue and use fail-fast behavior.
     */
    recoveryRequestsQueueSize?: number;

    /**
     * PubSub subscriptions to be used for the client.
     * Will be applied via SUBSCRIBE/PSUBSCRIBE/SSUBSCRIBE commands during connection establishment.
     */
    pubsubSubscriptions?: GlideClusterClientConfiguration.PubSubSubscriptions;
    /**
     * Advanced configuration settings for the client.
     */
    advancedConfiguration?: AdvancedGlideClusterClientConfiguration;
};

/**
 * Represents advanced configuration settings for creating a {@link GlideClusterClient | GlideClusterClient} used in {@link GlideClusterClientConfiguration | GlideClusterClientConfiguration}.
 *
 *
 * @example
 * ```typescript
 * const config: AdvancedGlideClusterClientConfiguration = {
 *   connectionTimeout: 500, // Set the connection timeout to 500ms
 *   tlsAdvancedConfiguration: {
 *     insecure: true, // Skip TLS certificate verification (use only in development)
 *   },
 *   refreshTopologyFromInitialNodes: true, // Refresh topology from initial seed nodes
 * };
 * ```
 */
export type AdvancedGlideClusterClientConfiguration =
    AdvancedBaseClientConfiguration & {
        /**
         * Enables refreshing the cluster topology using only the initial nodes.
         *
         * When this option is enabled, all topology updates (both the periodic checks and on-demand
         * refreshes triggered by topology changes) will query only the initial nodes provided when
         * creating the client, rather than using the internal cluster view.
         *
         * If not set, defaults to `false` (uses internal cluster view for topology refresh).
         */
        refreshTopologyFromInitialNodes?: boolean;
    };

/**
 * If the command's routing is to one node we will get T as a response type,
 * otherwise, we will get a dictionary of address: nodeResponse, address is of type string and nodeResponse is of type T.
 */
export type ClusterResponse<T> = T | Record<string, T>;

/**
 * @internal
 * Type which returns GLIDE core for commands routed to multiple nodes.
 * Should be converted to {@link ClusterResponse}.
 */
type ClusterGlideRecord<T> = GlideRecord<T> | T;

/**
 * @internal
 * Determines whether a command response is from a single node based on routing.
 *
 * @param isRoutedToSingleNodeByDefault - Whether the command defaults to a single node.
 * @param route - The route option provided by the caller.
 * @returns `true` if the response is from a single node.
 */
function isSingleNodeRoute(
    isRoutedToSingleNodeByDefault: boolean,
    route?: Routes,
): boolean {
    return (
        // route not given and command is routed by default to a random node
        (!route && isRoutedToSingleNodeByDefault) ||
        // or route is given and it is a single node route
        (Boolean(route) && route !== "allPrimaries" && route !== "allNodes")
    );
}

/**
 * @internal
 * Convert {@link ClusterGlideRecord} to {@link ClusterResponse}.
 *
 * @param res - Value received from Glide core.
 * @param isRoutedToSingleNodeByDefault - Default routing policy.
 * @param route - The route.
 * @returns Converted value.
 */
function convertClusterGlideRecord<T>(
    res: ClusterGlideRecord<T>,
    isRoutedToSingleNodeByDefault: boolean,
    route?: Routes,
): ClusterResponse<T> {
    return isSingleNodeRoute(isRoutedToSingleNodeByDefault, route)
        ? (res as T)
        : convertGlideRecordToRecord(res as GlideRecord<T>);
}

/**
 * Converts a cluster response by applying a transform function to each node's raw value.
 * Handles both single-node and multi-node routing automatically.
 *
 * @param res - Raw response from Glide core.
 * @param isSingleNode - Whether the response is from a single node.
 * @param transform - A function that transforms the raw value into the desired type.
 * @returns Transformed single value or a record mapping node addresses to transformed values.
 */
function convertAndParseClusterResponse<TRaw, TParsed>(
    res: ClusterGlideRecord<TRaw>,
    isSingleNode: boolean,
    transform: (raw: TRaw) => TParsed,
): ClusterResponse<TParsed> {
    if (isSingleNode) {
        return transform(res as TRaw);
    }

    const record = convertGlideRecordToRecord(
        res as GlideRecord<TRaw>,
    ) as Record<string, TRaw>;
    const result: Record<string, TParsed> = {};

    for (const [node, raw] of Object.entries(record)) {
        result[node] = transform(raw);
    }

    return result;
}

/**
 * Routing configuration for commands based on a specific slot ID in a Valkey cluster.
 *
 * @remarks
 * This interface allows you to specify routing of a command to a node responsible for a particular slot ID in the cluster.
 * Valkey clusters use hash slots to distribute data across multiple shards. There are 16,384 slots in total, and each shard
 * manages a range of slots.
 *
 * - **Slot ID**: A number between 0 and 16383 representing a hash slot.
 * - **Routing Type**:
 *   - `"primarySlotId"`: Routes the command to the primary node responsible for the specified slot ID.
 *   - `"replicaSlotId"`: Routes the command to a replica node responsible for the specified slot ID, overriding the `readFrom` configuration.
 *
 * @example
 * ```typescript
 * // Route command to the primary node responsible for slot ID 12345
 * const routeBySlotId: SlotIdTypes = {
 *   type: "primarySlotId",
 *   id: 12345,
 * };
 *
 * // Route command to a replica node responsible for slot ID 12345
 * const routeToReplicaBySlotId: SlotIdTypes = {
 *   type: "replicaSlotId",
 *   id: 12345,
 * };
 *
 * // Use the routing configuration when executing a command
 * const result = await client.get("mykey", { route: routeBySlotId });
 * ```
 */

export interface SlotIdTypes {
    /**
     * `replicaSlotId` overrides the `readFrom` configuration. If it's used the request
     * will be routed to a replica, even if the strategy is `alwaysFromPrimary`.
     */
    type: "primarySlotId" | "replicaSlotId";
    /**
     * Slot number. There are 16384 slots in a Valkey cluster, and each shard manages a slot range.
     * Unless the slot is known, it's better to route using `SlotKeyTypes`
     */
    id: number;
}
/**
 * Routing configuration for commands based on a key in a Valkey cluster.
 *
 * @remarks
 * This interface allows you to specify routing of a command to a node responsible for the slot that a specific key hashes to.
 * Valkey clusters use consistent hashing to map keys to hash slots, which are then managed by different shards in the cluster.
 *
 * - **Key**: The key whose hash slot will determine the routing of the command.
 * - **Routing Type**:
 *   - `"primarySlotKey"`: Routes the command to the primary node responsible for the key's slot.
 *   - `"replicaSlotKey"`: Routes the command to a replica node responsible for the key's slot, overriding the `readFrom` configuration.
 *
 * @example
 * ```typescript
 * // Route command to the primary node responsible for the key's slot
 * const routeByKey: SlotKeyTypes = {
 *   type: "primarySlotKey",
 *   key: "user:1001",
 * };
 *
 * // Route command to a replica node responsible for the key's slot
 * const routeToReplicaByKey: SlotKeyTypes = {
 *   type: "replicaSlotKey",
 *   key: "user:1001",
 * };
 *
 * // Use the routing configuration when executing a command
 * const result = await client.get("user:1001", { route: routeByKey });
 * ```
 */
export interface SlotKeyTypes {
    /**
     * `replicaSlotKey` overrides the `readFrom` configuration. If it's used the request
     * will be routed to a replica, even if the strategy is `alwaysFromPrimary`.
     */
    type: "primarySlotKey" | "replicaSlotKey";
    /**
     * The request will be sent to nodes managing this key.
     */
    key: string;
}

/**
 * Routing configuration to send a command to a specific node by its address and port.
 *
 * @remarks
 * This interface allows you to specify routing of a command to a node in the Valkey cluster by providing its network address and port.
 * It's useful when you need to direct a command to a particular node.
 *
 * - **Type**: Must be set to `"routeByAddress"` to indicate that the routing should be based on the provided address.
 * - **Host**: The endpoint of the node.
 *   - If `port` is not provided, `host` should be in the format `${address}:${port}`, where `address` is the preferred endpoint as shown in the output of the `CLUSTER SLOTS` command.
 *   - If `port` is provided, `host` should be the address or hostname of the node without the port.
 * - **Port**: (Optional) The port to access on the node.
 *   - If `port` is not provided, `host` is assumed to include the port number.
 *
 * @example
 * ```typescript
 * // Route command to a node at '192.168.1.10:6379'
 * const routeByAddress: RouteByAddress = {
 *   type: "routeByAddress",
 *   host: "192.168.1.10",
 *   port: 6379,
 * };
 *
 * // Alternatively, include the port in the host string
 * const routeByAddressWithPortInHost: RouteByAddress = {
 *   type: "routeByAddress",
 *   host: "192.168.1.10:6379",
 * };
 *
 * // Use the routing configuration when executing a command
 * const result = await client.ping({ route: routeByAddress });
 * ```
 */
export interface RouteByAddress {
    type: "routeByAddress";
    /**
     *The endpoint of the node. If `port` is not provided, should be in the `${address}:${port}` format, where `address` is the preferred endpoint as shown in the output of the `CLUSTER SLOTS` command.
     */
    host: string;
    /**
     * The port to access on the node. If port is not provided, `host` is assumed to be in the format `${address}:${port}`.
     */
    port?: number;
}
/**
 * Defines the routing configuration for a command in a Valkey cluster.
 *
 * @remarks
 * The `Routes` type allows you to specify how a command should be routed in a Valkey cluster.
 * Commands can be routed to a single node or broadcast to multiple nodes depending on the routing strategy.
 *
 * **Routing Options**:
 *
 * - **Single Node Routing** (`SingleNodeRoute`):
 *   - **"randomNode"**: Route the command to a random node in the cluster.
 *   - **`SlotIdTypes`**: Route based on a specific slot ID.
 *   - **`SlotKeyTypes`**: Route based on the slot of a specific key.
 *   - **`RouteByAddress`**: Route to a specific node by its address and port.
 * - **Broadcast Routing**:
 *   - **"allPrimaries"**: Route the command to all primary nodes in the cluster.
 *   - **"allNodes"**: Route the command to all nodes (both primaries and replicas) in the cluster.
 *
 * @example
 * ```typescript
 * // Route command to a random node
 * const routeRandom: Routes = "randomNode";
 *
 * // Route command to all primary nodes
 * const routeAllPrimaries: Routes = "allPrimaries";
 *
 * // Route command to all nodes
 * const routeAllNodes: Routes = "allNodes";
 *
 * // Route command to a node by slot key
 * const routeByKey: Routes = {
 *   type: "primarySlotKey",
 *   key: "myKey",
 * };
 *
 * // Route command to a specific node by address
 * const routeByAddress: Routes = {
 *   type: "routeByAddress",
 *   host: "192.168.1.10",
 *   port: 6379,
 * };
 *
 * // Use the routing configuration when executing a command
 * const result = await client.ping({ route: routeByAddress });
 * ```
 */
export type Routes =
    | SingleNodeRoute
    /**
     * Route request to all primary nodes.
     */
    | "allPrimaries"
    /**
     * Route request to all nodes.
     */
    | "allNodes";
/**
 * Defines the routing configuration to a single node in the Valkey cluster.
 *
 * @remarks
 * The `SingleNodeRoute` type allows you to specify routing of a command to a single node in the cluster.
 * This can be based on various criteria such as a random node, a node responsible for a specific slot, or a node identified by its address.
 *
 * **Options**:
 *
 * - **"randomNode"**: Route the command to a random node in the cluster.
 * - **`SlotIdTypes`**: Route to the node responsible for a specific slot ID.
 * - **`SlotKeyTypes`**: Route to the node responsible for the slot of a specific key.
 * - **`RouteByAddress`**: Route to a specific node by its address and port.
 *
 * @example
 * ```typescript
 * // Route to a random node
 * const routeRandomNode: SingleNodeRoute = "randomNode";
 *
 * // Route based on slot ID
 * const routeBySlotId: SingleNodeRoute = {
 *   type: "primarySlotId",
 *   id: 12345,
 * };
 *
 * // Route based on key
 * const routeByKey: SingleNodeRoute = {
 *   type: "primarySlotKey",
 *   key: "myKey",
 * };
 *
 * // Route to a specific node by address
 * const routeByAddress: SingleNodeRoute = {
 *   type: "routeByAddress",
 *   host: "192.168.1.10",
 *   port: 6379,
 * };
 *
 * // Use the routing configuration when executing a command
 * const result = await client.get("myKey", { route: routeByKey });
 * ```
 */
export type SingleNodeRoute =
    /**
     * Route request to a random node.
     */
    | "randomNode"
    /**
     * Route request to the node that contains the slot with the given id.
     */
    | SlotIdTypes
    /**
     * Route request to the node that contains the slot that the given key matches.
     */
    | SlotKeyTypes
    | RouteByAddress;

/**
 * Client used for connection to cluster servers.
 * Use {@link createClient} to request a client.
 *
 * @see For full documentation refer to {@link https://glide.valkey.io/how-to/client-initialization/#cluster | Valkey GLIDE Documentation}.
 */
export class GlideClusterClient extends BaseClient {
    /**
     * @internal
     */
    protected createClientRequest(
        options: GlideClusterClientConfiguration,
    ): connection_request.IConnectionRequest {
        const configuration = super.createClientRequest(options);
        configuration.clusterModeEnabled = true;

        // "enabledDefaultConfigs" is the default configuration and doesn't need setting
        if (
            options.periodicChecks !== undefined &&
            options.periodicChecks !== "enabledDefaultConfigs"
        ) {
            if (options.periodicChecks === "disabled") {
                configuration.periodicChecksDisabled =
                    connection_request.PeriodicChecksDisabled.create();
            } else {
                configuration.periodicChecksManualInterval =
                    connection_request.PeriodicChecksManualInterval.create({
                        durationInSec: options.periodicChecks.duration_in_sec,
                    });
            }
        }

        this.configurePubsub(options, configuration);

        if (options.recoveryRequestsQueueSize !== undefined) {
            configuration.recoveryRequestsQueueSize =
                options.recoveryRequestsQueueSize;
        }

        if (options.advancedConfiguration) {
            this.configureAdvancedConfigurationBase(
                options.advancedConfiguration,
                configuration,
            );

            // Set refresh topology from initial nodes
            if (
                options.advancedConfiguration
                    .refreshTopologyFromInitialNodes !== undefined
            ) {
                configuration.refreshTopologyFromInitialNodes =
                    options.advancedConfiguration.refreshTopologyFromInitialNodes;
            }
        }

        return configuration;
    }

    /**
     * Creates a new `GlideClusterClient` instance and establishes connections to a Valkey Cluster.
     *
     * @param options - The configuration options for the client, including cluster addresses, database selection, authentication credentials, TLS settings, periodic checks, and Pub/Sub subscriptions.
     * @returns A promise that resolves to a connected `GlideClusterClient` instance.
     *
     * @remarks
     * Use this static method to create and connect a `GlideClusterClient` to a Valkey Cluster.
     * The client will automatically handle connection establishment, including cluster topology discovery, database selection, and handling of authentication and TLS configurations.
     *
     * @example
     * ```typescript
     * // Connecting to a Cluster
     * import { GlideClusterClient, GlideClusterClientConfiguration } from '@valkey/valkey-glide';
     *
     * const client = await GlideClusterClient.createClient({
     *   addresses: [
     *     { host: 'address1.example.com', port: 6379 },
     *     { host: 'address2.example.com', port: 6379 },
     *   ],
     *   databaseId: 5, // Connect to database 5 (requires Valkey 9.0+)
     *   credentials: {
     *     username: 'user1',
     *     password: 'passwordA',
     *   },
     *   useTLS: true,
     *   periodicChecks: {
     *     duration_in_sec: 30, // Perform periodic checks every 30 seconds
     *   },
     *   pubsubSubscriptions: {
     *     channelsAndPatterns: {
     *       [GlideClusterClientConfiguration.PubSubChannelModes.Exact]: new Set(['updates']),
     *       [GlideClusterClientConfiguration.PubSubChannelModes.Sharded]: new Set(['sharded_channel']),
     *     },
     *     callback: (msg) => {
     *       console.log(`Received message: ${msg.payload}`);
     *     },
     *   },
     *   connectionBackoff: {
     *     numberOfRetries: 5,
     *     factor: 1000,
     *     exponentBase: 2,
     *     jitter: 20,
     *   },
     * });
     * ```
     *
     * @remarks
     * - **Cluster Topology Discovery**: The client will automatically discover the cluster topology based on the seed addresses provided.
     * - **Database Selection**: Use `databaseId` to specify which logical database to connect to. Requires Valkey 9.0+ with multi-database cluster mode enabled.
     * - **Authentication**: If `credentials` are provided, the client will attempt to authenticate using the specified username and password.
     * - **TLS**: If `useTLS` is set to `true`, the client will establish secure connections using TLS.
     *      Should match the TLS configuration of the server/cluster, otherwise the connection attempt will fail.
     *      For advanced tls configuration, please use the {@link AdvancedGlideClusterClientConfiguration} option.
     * - **Periodic Checks**: The `periodicChecks` setting allows you to configure how often the client checks for cluster topology changes.
     * - **Pub/Sub Subscriptions**: Any channels or patterns specified in `pubsubSubscriptions` will be subscribed to upon connection.
     */
    public static async createClient(
        options: GlideClusterClientConfiguration,
    ): Promise<GlideClusterClient> {
        return await super.createClientInternal(
            options,
            (options?: GlideClusterClientConfiguration) =>
                new GlideClusterClient(options),
        );
    }

    /**
     * @internal
     * Wrap a pre-built {@link GlideClientHandle} (from the pool layer) in a
     * `GlideClusterClient` instance.  No network connection is made — the handle
     * already owns a live connection managed by the pool.
     */
    public static createFromHandle(
        handle: GlideClientHandle,
        options: GlideClusterClientConfiguration,
    ): GlideClusterClient {
        return super.createClientFromHandle<GlideClusterClient>(
            handle,
            options,
            (options?: GlideClusterClientConfiguration) =>
                new GlideClusterClient(options),
        );
    }

    /**
     * Acquire a pool client by ID and wrap it as a GlideClusterClient.
     *
     * Called by {@link ClientPool} after a successful `poolTryAcquire` or
     * `poolAcquireBlocking`. Spins up a fresh worker thread for the
     * already-connected pool client and returns a fully operational instance.
     *
     * @internal - intended for use by ClientPool only.
     */
    public static async fromPoolClientId(
        clientId: number,
        options: GlideClusterClientConfiguration,
    ): Promise<GlideClusterClient> {
        const { poolBuildHandle } = await import("../build-ts/native");
        // Create the client instance whose handleResponsesAvailable will be stored
        // as the Rust TSFN wake callback. The same instance must be the one that
        // receives the handle — arrow functions bind `this` at construction, so
        // the callback and the client handle MUST live on the same object.
        const client = new GlideClusterClient(options);
        const handle = await poolBuildHandle(
            clientId,
            (client as unknown as { handleResponsesAvailable: () => void })
                .handleResponsesAvailable,
        );
        // Inject the handle into the SAME instance whose callback was registered.
        (client as unknown as { clientHandle: typeof handle }).clientHandle =
            handle;
        return client;
    }

    /**
     * @internal
     * Serialise a {@link GlideClusterClientConfiguration} into the protobuf
     * bytes used by the pool Rust APIs.  Does not open a network connection.
     */
    public static serializeConfig(options: GlideClusterClientConfiguration): {
        bytes: Uint8Array;
        resolverKey: string | undefined;
    } {
        return super.serializeConnectionRequest(
            options,
            (opts?: BaseClientConfiguration) =>
                new GlideClusterClient(opts as GlideClusterClientConfiguration),
        );
    }

    /**
     * @internal
     */
    protected createClusterScanPromise(
        cursor: ClusterScanCursor,
        options?: ClusterScanOptions & DecoderOption,
    ): Promise<[ClusterScanCursor, GlideString[]]> {
        this.ensureClientIsOpen();
        const callbackIndex = this.getCallbackIndex();

        return new Promise<[ClusterScanCursor, GlideString[]]>(
            (resolve, reject) => {
                // Store a custom handler that wraps the result
                this.promiseCallbackFunctions[callbackIndex] = [
                    (result: [GlideString, GlideString[]]) => {
                        // Result is [cursor_string, keys_array]
                        // Cursor may come back as Buffer or string
                        let cursorStr: string;

                        if (typeof result[0] === "string") {
                            cursorStr = result[0];
                        } else if (result[0] instanceof Buffer) {
                            cursorStr = result[0].toString();
                        } else if (result[0] instanceof Uint8Array) {
                            cursorStr = new TextDecoder().decode(result[0]);
                        } else {
                            cursorStr = String(result[0]);
                        }

                        const newCursor = new ClusterScanCursor(cursorStr);
                        resolve([newCursor, result[1]]);
                    },
                    reject,
                    options?.decoder,
                ];

                // Convert match pattern to Uint8Array to preserve binary patterns
                let matchPattern: Uint8Array | undefined;

                if (options?.match) {
                    if (typeof options.match === "string") {
                        matchPattern = new TextEncoder().encode(options.match);
                    } else if (options.match instanceof Uint8Array) {
                        matchPattern = options.match;
                    }
                }

                const success = this.clientHandle!.clusterScan(
                    callbackIndex,
                    cursor.getCursor(),
                    matchPattern,
                    options?.count ?? undefined,
                    options?.type,
                    options?.allowNonCoveredSlots,
                );

                if (!success) {
                    this.availableCallbackSlots.push(callbackIndex);
                    reject(
                        new RequestError(
                            "Inflight request limit exceeded. Please try again later.",
                        ),
                    );
                }
            },
        );
    }

    /**
     * Incrementally iterates over the keys in the Cluster.
     *
     * This command is similar to the `SCAN` command but designed for Cluster environments.
     * It uses a {@link ClusterScanCursor} object to manage iterations.
     *
     * For each iteration, use the new cursor object to continue the scan.
     * Using the same cursor object for multiple iterations may result in unexpected behavior.
     *
     * For more information about the Cluster Scan implementation, see
     * {@link https://glide.valkey.io/concepts/client-features/cluster-scan/ | Cluster Scan}.
     *
     * This method can iterate over all keys in the database from the start of the scan until it ends.
     * The same key may be returned in multiple scan iterations.
     * The API does not accept `route` as it go through all slots in the cluster.
     *
     * @see {@link https://valkey.io/commands/scan/ | valkey.io} for more details.
     *
     * @param cursor - The cursor object that wraps the scan state.
     *   To start a new scan, create a new empty `ClusterScanCursor` using {@link ClusterScanCursor}.
     * @param options - (Optional) The scan options, see {@link ClusterScanOptions} and  {@link DecoderOption}.
     * @returns A Promise resolving to an array containing the next cursor and an array of keys,
     *   formatted as [`ClusterScanCursor`, `string[]`].
     *
     * @example
     * ```typescript
     * // Iterate over all keys in the cluster
     * await client.mset([{key: "key1", value: "value1"}, {key: "key2", value: "value2"}, {key: "key3", value: "value3"}]);
     * let cursor = new ClusterScanCursor();
     * const allKeys: GlideString[] = [];
     * let keys: GlideString[] = [];
     * while (!cursor.isFinished()) {
     *   [cursor, keys] = await client.scan(cursor, { count: 10 });
     *   allKeys.push(...keys);
     * }
     * console.log(allKeys); // ["key1", "key2", "key3"]
     *
     * // Iterate over keys matching a pattern
     * await client.mset([{key: "key1", value: "value1"}, {key: "key2", value: "value2"}, {key: "notMyKey", value: "value3"}, {key: "somethingElse", value: "value4"}]);
     * let cursor = new ClusterScanCursor();
     * const matchedKeys: GlideString[] = [];
     * let keys: GlideString[] = [];
     * while (!cursor.isFinished()) {
     *    [cursor, keys] = await client.scan(cursor, { match: "*key*", count: 10 });
     *   matchedKeys.push(...keys);
     * }
     * console.log(matchedKeys); // ["key1", "key2"]
     *
     * // Iterate over keys of a specific type
     * await client.mset([{key: "key1", value: "value1"}, {key: "key2", value: "value2"}, {key: "key3", value: "value3"}]);
     * await client.sadd("thisIsASet", ["value4"]);
     * let cursor = new ClusterScanCursor();
     * const stringKeys: GlideString[] = [];
     * let keys: GlideString[];
     * while (!cursor.isFinished()) {
     *   [cursor, keys] = await client.scan(cursor, { type: ObjectType.STRING });
     *   stringKeys.push(...keys);
     * }
     * console.log(stringKeys); // ["key1", "key2", "key3"]
     * ```
     */
    public async scan(
        cursor: ClusterScanCursor,
        options?: ClusterScanOptions & DecoderOption,
    ): Promise<[ClusterScanCursor, GlideString[]]> {
        return this.createClusterScanPromise(cursor, options);
    }

    /** Executes a single command, without checking inputs. Every part of the command, including subcommands,
     *  should be added as a separate value in args.
     *  The command will be routed automatically based on the passed command's default request policy, unless `route` is provided,
     *  in which case the client will route the command to the nodes defined by `route`.
     *
     * Note: An error will occur if the string decoder is used with commands that return only bytes as a response.
     *
     * @see {@link https://glide.valkey.io/concepts/client-features/custom-commands/|Glide for Valkey Wiki} for details on the restrictions and limitations of the custom command API.
     *
     * @param args - A list including the command name and arguments for the custom command.
     * @param options - (Optional) See {@link RouteOption} and {@link DecoderOption}
     * @returns The executed custom command return value.
     *
     * @example
     * ```typescript
     * // Example usage of customCommand method to retrieve pub/sub clients with routing to all primary nodes
     * const result = await client.customCommand(["CLIENT", "LIST", "TYPE", "PUBSUB"], {route: "allPrimaries", decoder: Decoder.String});
     * console.log(result); // Output: Returns a list of all pub/sub clients
     * ```
     */
    public async customCommand(
        args: GlideString[],
        options?: RouteOption & DecoderOption,
    ): Promise<ClusterResponse<GlideReturnType>> {
        const command = createCustomCommand(args);
        return super.createWritePromise(command, options);
    }

    /**
     * Executes a batch by processing the queued commands.
     *
     * **Routing Behavior:**
     *
     * - If a `route` is specified in {@link ClusterBatchOptions}, the entire batch is sent to the specified node.
     * - If no `route` is specified:
     *   - **Atomic batches (Transactions):** Routed to the slot owner of the first key in the batch. If no key is found, the request is sent to a random node.
     *   - **Non-atomic batches (Pipelines):** Each command is routed to the node owning the corresponding key's slot. If no key is present, routing follows the command's request policy. Multi-node commands are automatically split and dispatched to the appropriate nodes.
     *
     * **Behavior Notes:**
     *
     * - **Atomic Batches (Transactions):** All key-based commands must map to the same hash slot. If keys span different slots, the transaction will fail. If the transaction fails due to a `WATCH` command, `exec` will return `null`.
     *
     * @see {@link https://github.com/valkey-io/valkey-glide/wiki/NodeJS-wrapper#transaction|Valkey GLIDE Documentation} for details on Valkey Transactions.
     *
     * **Retry and Redirection:**
     *
     * - If a redirection error occurs:
     *   - **Atomic batches (Transactions):** The entire transaction will be redirected.
     *   - **Non-atomic batches (Pipelines):** Only commands that encountered redirection errors will be retried.
     * - Retries for failures will be handled according to the configured {@link BatchRetryStrategy}.
     *
     * @param batch - A {@link ClusterBatch} containing the commands to execute.
     * @param raiseOnError - Determines how errors are handled within the batch response.
     *   - If `true`, the first encountered error in the batch will be raised as an exception of type {@link RequestError}
     *     after all retries and reconnections have been exhausted.
     *   - If `false`, errors will be included as part of the batch response, allowing the caller to process both successful and failed commands together.
     *     In this case, error details will be provided as instances of {@link RequestError} in the response list.
     * @param options - (Optional) {@link ClusterBatchOptions} and {@link DecoderOption} specifying execution and decoding behavior.
     * @returns A Promise resolving to an array of results, where each entry corresponds to a command’s execution result.
     *   - If the transaction fails due to a `WATCH` command, the promise resolves to `null`.
     *
     * @see {@link https://valkey.io/docs/topics/transactions/|Valkey Transactions (Atomic Batches)}
     * @see {@link https://valkey.io/docs/topics/pipelining/|Valkey Pipelines (Non-Atomic Batches)}
     * @example
     * ```typescript
     * // Atomic batch (transaction): all keys must share the same hash slot
     * const atomicBatch = new ClusterBatch(true)
     *   .set('key', '1')
     *   .incr('key')
     *   .get('key');
     *
     * const atomicOptions = {timeout: 1000};
     * const atomicResult = await clusterClient.exec(atomicBatch, false, atomicOptions);
     * console.log('Atomic Batch Result:', atomicResult);
     * // Output: ['OK', 2, '2']
     *
     * // Non-atomic batch (pipeline): keys may span different hash slots
     * const nonAtomicBatch = new ClusterBatch(false)
     *   .set('key1', 'value1')
     *   .set('key2', 'value2')
     *   .get('key1')
     *   .get('key2');
     *
     * const pipelineOptions = { retryStrategy: { retryServerError: true, retryConnectionError: false } };
     * const nonAtomicResult = await clusterClient.exec(nonAtomicBatch, false, pipelineOptions);
     * console.log(nonAtomicResult);
     * // Output: ['OK', 'OK', 'value1', 'value2']
     * ```
     */
    public async exec(
        batch: ClusterBatch,
        raiseOnError: boolean,
        options?: ClusterBatchOptions & DecoderOption,
    ): Promise<GlideReturnType[] | null> {
        return this.createWritePromise<GlideReturnType[] | null>(
            batch.commands,
            options,
            batch.isAtomic,
            raiseOnError,
        ).then((result) =>
            this.processResultWithSetCommands(result, batch.setCommandsIndexes),
        );
    }

    /**
     * Pings the server.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/ping/|valkey.io} for details.
     *
     * @param options - (Optional) Additional parameters:
     * - (Optional) `message` : a message to include in the `PING` command.
     *   + If not provided, the server will respond with `"PONG"`.
     *   + If provided, the server will respond with a copy of the message.
     * - (Optional) `route`: see {@link RouteOption}.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns `"PONG"` if `message` is not provided, otherwise return a copy of `message`.
     *
     * @example
     * ```typescript
     * // Example usage of ping method without any message
     * const result = await client.ping();
     * console.log(result); // Output: 'PONG'
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of ping method with a message
     * const result = await client.ping("Hello");
     * console.log(result); // Output: 'Hello'
     * ```
     */
    public async ping(
        options?: {
            message?: GlideString;
        } & RouteOption &
            DecoderOption,
    ): Promise<GlideString> {
        return this.createWritePromise(createPing(options?.message), options);
    }

    /**
     * Gets information and statistics about the server.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * Starting from server version 7, command supports multiple section arguments.
     *
     * @see {@link https://valkey.io/commands/info/|valkey.io} for details.
     *
     * @param options - (Optional) Additional parameters:
     * - (Optional) `sections`: a list of {@link InfoOptions} values specifying which sections of information to retrieve.
     *     When no parameter is provided, {@link InfoOptions.Default|Default} is assumed.
     * - (Optional) `route`: see {@link RouteOption}.
     * @returns A string containing the information for the sections requested.
     *     When specifying a route other than a single node,
     *     it returns a dictionary where each address is the key and its corresponding node response is the value.
     *
     * @example
     * ```typescript
     * // Example usage of the info method with retrieving total_net_input_bytes from the result
     * const result = await client.info(new Section[] { Section.STATS });
     * console.log(someClusterParsingFunction(result, "total_net_input_bytes")); // Output: 1
     * ```
     */
    public async info(
        options?: { sections?: InfoOptions[] } & RouteOption,
    ): Promise<ClusterResponse<string>> {
        return this.createWritePromise<ClusterGlideRecord<string>>(
            createInfo(options?.sections),
            { decoder: Decoder.String, ...options },
        ).then((res) => convertClusterGlideRecord(res, false, options?.route));
    }

    /**
     * Gets the name of the connection to which the request is routed.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/client-getname/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption} and {@link DecoderOption}.
     *
     * @returns - The name of the client connection as a string if a name is set, or `null` if no name is assigned.
     *     When specifying a route other than a single node, it returns a dictionary where each address is the key and
     *     its corresponding node response is the value.
     *
     * @example
     * ```typescript
     * // Example usage of client_getname method
     * const result = await client.client_getname();
     * console.log(result); // Output: 'Connection Name'
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of clientGetName method with routing to all nodes
     * const result = await client.clientGetName('allNodes');
     * console.log(result); // Output: {'addr': 'Connection Name', 'addr2': 'Connection Name', 'addr3': 'Connection Name'}
     * ```
     */
    public async clientGetName(
        options?: RouteOption & DecoderOption,
    ): Promise<ClusterResponse<GlideString | null>> {
        return this.createWritePromise<ClusterGlideRecord<GlideString | null>>(
            createClientGetName(),
            options,
        ).then((res) => convertClusterGlideRecord(res, true, options?.route));
    }

    /**
     * Rewrites the configuration file with the current configuration.
     *
     * The command will be routed to a all nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/config-rewrite/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns `"OK"` when the configuration was rewritten properly. Otherwise, an error is thrown.
     *
     * @example
     * ```typescript
     * // Example usage of configRewrite command
     * const result = await client.configRewrite();
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async configRewrite(options?: RouteOption): Promise<"OK"> {
        return this.createWritePromise(createConfigRewrite(), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Resets the statistics reported by the server using the `INFO` and `LATENCY HISTOGRAM` commands.
     *
     * The command will be routed to all nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/config-resetstat/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns always `"OK"`.
     *
     * @example
     * ```typescript
     * // Example usage of configResetStat command
     * const result = await client.configResetStat();
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async configResetStat(options?: RouteOption): Promise<"OK"> {
        return this.createWritePromise(createConfigResetStat(), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Returns the current connection ID.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/client-id/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns The ID of the connection. When specifying a route other than a single node,
     *     it returns a dictionary where each address is the key and its corresponding node response is the value.
     *
     * @example
     * ```typescript
     * const result = await client.clientId();
     * console.log("Connection id: " + result);
     * ```
     */
    public async clientId(
        options?: RouteOption,
    ): Promise<ClusterResponse<number>> {
        return this.createWritePromise<ClusterGlideRecord<number>>(
            createClientId(),
            options,
        ).then((res) => convertClusterGlideRecord(res, true, options?.route));
    }

    /**
     * Returns information about the current client connection's use
     * of the server assisted client side caching feature.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/client-trackinginfo/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns A {@link ClusterResponse} containing tracking info(s) for the client.
     *
     * @example
     * ```typescript
     * const info = await client.clientTrackingInfo();
     * console.log(info.flags); // Set { "off" }
     * console.log(info.redirect); // -1
     * ```
     *
     * @example
     * ```typescript
     * const info = await client.clientTrackingInfo({ route: "allNodes" });
     * // info is Record<string, ClientTrackingInfo>
     * ```
     */
    public async clientTrackingInfo(
        options?: RouteOption,
    ): Promise<ClusterResponse<ClientTrackingInfo>> {
        return this.createWritePromise<GlideRecord<unknown>>(
            createClientTrackingInfo(),
            options,
        ).then((res) => {
            if (isSingleNodeRoute(true, options?.route)) {
                return parseClientTrackingInfoResponse(
                    convertGlideRecordToRecord(res) as Record<string, unknown>,
                );
            }

            const record = convertGlideRecordToRecord(
                res as GlideRecord<unknown>,
            ) as Record<string, Record<string, unknown>>;
            const result: Record<string, ClientTrackingInfo> = {};

            for (const [node, entry] of Object.entries(record)) {
                result[node] = parseClientTrackingInfoResponse(entry);
            }

            return result;
        });
    }

    /**
     * Reads the configuration parameters of the running server.
     * Starting from server version 7, command supports multiple parameters.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/config-get/|valkey.io} for details.
     *
     * @param parameters - A list of configuration parameter names to retrieve values for.
     * @param options - (Optional) See {@link RouteOption} and {@link DecoderOption}.
     *
     * @returns A map of values corresponding to the configuration parameters. When specifying a route other than a single node,
     *     it returns a dictionary where each address is the key and its corresponding node response is the value.
     *
     * @example
     * ```typescript
     * // Example usage of config_get method with a single configuration parameter with routing to a random node
     * const result = await client.config_get(["timeout"], "randomNode");
     * console.log(result); // Output: {'timeout': '1000'}
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of configGet method with multiple configuration parameters
     * const result = await client.configGet(["timeout", "maxmemory"]);
     * console.log(result); // Output: {'timeout': '1000', 'maxmemory': '1GB'}
     * ```
     */
    public async configGet(
        parameters: string[],
        options?: RouteOption & DecoderOption,
    ): Promise<ClusterResponse<Record<string, GlideString>>> {
        return this.createWritePromise<
            ClusterGlideRecord<GlideRecord<GlideString>>
        >(createConfigGet(parameters), options).then((res) =>
            convertGlideRecordToRecord(res as GlideRecord<string>),
        );
    }

    /**
     * Sets configuration parameters to the specified values.
     * Starting from server version 7, command supports multiple parameters.
     *
     * The command will be routed to all nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/config-set/|valkey.io} for details.
     *
     * @param parameters - A map consisting of configuration parameters and their respective values to set.
     * @param options - (Optional) See {@link RouteOption}.
     * @returns "OK" when the configuration was set properly. Otherwise an error is thrown.
     *
     * @example
     * ```typescript
     * // Example usage of configSet method to set multiple configuration parameters
     * const result = await client.configSet({ timeout: "1000", maxmemory: "1GB" });
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async configSet(
        parameters: Record<string, GlideString>,
        options?: RouteOption,
    ): Promise<"OK"> {
        return this.createWritePromise(createConfigSet(parameters), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Echoes the provided `message` back.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/echo/|valkey.io} for details.
     *
     * @param message - The message to be echoed back.
     * @param options - (Optional) See {@link RouteOption} and {@link DecoderOption}.
     * @returns The provided `message`. When specifying a route other than a single node,
     *     it returns a dictionary where each address is the key and its corresponding node response is the value.
     *
     * @example
     * ```typescript
     * // Example usage of the echo command
     * const echoedMessage = await client.echo("valkey-glide");
     * console.log(echoedMessage); // Output: "valkey-glide"
     * ```
     * @example
     * ```typescript
     * // Example usage of the echo command with routing to all nodes
     * const echoedMessage = await client.echo("valkey-glide", "allNodes");
     * console.log(echoedMessage); // Output: {'addr': 'valkey-glide', 'addr2': 'valkey-glide', 'addr3': 'valkey-glide'}
     * ```
     */
    public async echo(
        message: GlideString,
        options?: RouteOption & DecoderOption,
    ): Promise<ClusterResponse<GlideString>> {
        return this.createWritePromise<ClusterGlideRecord<GlideString>>(
            createEcho(message),
            options,
        ).then((res) => convertClusterGlideRecord(res, true, options?.route));
    }

    /**
     * Returns the server time.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/time/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption}.
     *
     * @returns The current server time as an `array` with two items:
     * - A Unix timestamp,
     * - The amount of microseconds already elapsed in the current second.
     *
     * When specifying a route other than a single node, it returns a dictionary where each address is the key and
     * its corresponding node response is the value.
     *
     * @example
     * ```typescript
     * // Example usage of time method without any argument
     * const result = await client.time();
     * console.log(result); // Output: ['1710925775', '913580']
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of time method with routing to all nodes
     * const result = await client.time('allNodes');
     * console.log(result); // Output: {'addr': ['1710925775', '913580'], 'addr2': ['1710925775', '913580'], 'addr3': ['1710925775', '913580']}
     * ```
     */
    public async time(
        options?: RouteOption,
    ): Promise<ClusterResponse<[string, string]>> {
        return this.createWritePromise<ClusterGlideRecord<[string, string]>>(
            createTime(),
            options,
        ).then((res) => convertClusterGlideRecord(res, true, options?.route));
    }

    /**
     * Displays a piece of generative computer art and the server version.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/lolwut/|valkey.io} for details.
     *
     * @param options - (Optional) The LOLWUT options - see {@link LolwutOptions} and {@link RouteOption}.
     * @returns A piece of generative computer art along with the current server version.
     *
     * @example
     * ```typescript
     * const response = await client.lolwut({ version: 6, parameters: [40, 20] }, "allNodes");
     * console.log(response); // Output: "Valkey ver. 7.2.3" - Indicates the current server version.
     * ```
     */
    public async lolwut(
        options?: LolwutOptions & RouteOption,
    ): Promise<ClusterResponse<string>> {
        return this.createWritePromise<ClusterGlideRecord<string>>(
            createLolwut(options),
            options,
        ).then((res) => convertClusterGlideRecord(res, true, options?.route));
    }

    /**
     * Invokes a previously loaded function.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/fcall/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param func - The function name.
     * @param args - A list of `function` arguments and it should not represent names of keys.
     * @param options - (Optional) See {@link RouteOption} and {@link DecoderOption}.
     * @returns The invoked function's return value.
     *
     * @example
     * ```typescript
     * const response = await client.fcallWithRoute("Deep_Thought", [], "randomNode");
     * console.log(response); // Output: Returns the function's return value.
     * ```
     */
    public async fcallWithRoute(
        func: GlideString,
        args: GlideString[],
        options?: RouteOption & DecoderOption,
    ): Promise<ClusterResponse<GlideReturnType>> {
        return this.createWritePromise<ClusterGlideRecord<GlideReturnType>>(
            createFCall(func, [], args),
            options,
        ).then((res) => convertClusterGlideRecord(res, true, options?.route));
    }

    /**
     * Invokes a previously loaded read-only function.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/fcall/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param func - The function name.
     * @param args - A list of `function` arguments and it should not represent names of keys.
     * @param options - (Optional) See {@link RouteOption} and {@link DecoderOption}.
     * @returns The invoked function's return value.
     *
     * @example
     * ```typescript
     * const response = await client.fcallReadonlyWithRoute("Deep_Thought", ["Answer", "to", "the", "Ultimate",
     *            "Question", "of", "Life,", "the", "Universe,", "and", "Everything"], "randomNode");
     * console.log(response); // Output: 42 # The return value on the function that was execute.
     * ```
     */
    public async fcallReadonlyWithRoute(
        func: GlideString,
        args: GlideString[],
        options?: RouteOption & DecoderOption,
    ): Promise<ClusterResponse<GlideReturnType>> {
        return this.createWritePromise<ClusterGlideRecord<GlideReturnType>>(
            createFCallReadOnly(func, [], args),
            options,
        ).then((res) => convertClusterGlideRecord(res, true, options?.route));
    }

    /**
     * Deletes a library and all its functions.
     *
     * @see {@link https://valkey.io/commands/function-delete/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param libraryCode - The library name to delete.
     * @param route - (Optional) The command will be routed to all primary node, unless `route` is provided, in which
     *     case the client will route the command to the nodes defined by `route`.
     * @returns A simple `"OK"` response.
     *
     * @example
     * ```typescript
     * const result = await client.functionDelete("libName");
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async functionDelete(
        libraryCode: GlideString,
        options?: RouteOption,
    ): Promise<"OK"> {
        return this.createWritePromise(createFunctionDelete(libraryCode), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Loads a library to Valkey.
     *
     * @see {@link https://valkey.io/commands/function-load/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param libraryCode - The source code that implements the library.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `replace`: whether the given library should overwrite a library with the same name if it
     *     already exists.
     * - (Optional) `route`: see {@link RouteOption}.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns The library name that was loaded.
     *
     * @example
     * ```typescript
     * const code = "#!lua name=mylib \n redis.register_function('myfunc', function(keys, args) return args[1] end)";
     * const result = await client.functionLoad(code, { replace: true, route: 'allNodes' });
     * console.log(result); // Output: 'mylib'
     * ```
     */
    public async functionLoad(
        libraryCode: GlideString,
        options?: {
            replace?: boolean;
        } & RouteOption &
            DecoderOption,
    ): Promise<GlideString> {
        return this.createWritePromise(
            createFunctionLoad(libraryCode, options?.replace),
            options,
        );
    }

    /**
     * Deletes all function libraries.
     *
     * @see {@link https://valkey.io/commands/function-flush/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param options - (Optional) Additional parameters:
     * - (Optional) `mode`: the flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     * - (Optional) `route`: see {@link RouteOption}.
     * @returns A simple `"OK"` response.
     *
     * @example
     * ```typescript
     * const result = await client.functionFlush(FlushMode.SYNC);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async functionFlush(
        options?: {
            mode?: FlushMode;
        } & RouteOption,
    ): Promise<"OK"> {
        return this.createWritePromise(createFunctionFlush(options?.mode), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Returns information about the functions and libraries.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/function-list/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param options - (Optional) See {@link FunctionListOptions}, {@link DecoderOption}, and {@link RouteOption}.
     * @returns Info about all or selected libraries and their functions in {@link FunctionListResponse} format.
     *
     * @example
     * ```typescript
     * // Request info for specific library including the source code
     * const result1 = await client.functionList({ libNamePattern: "myLib*", withCode: true });
     * // Request info for all libraries
     * const result2 = await client.functionList();
     * console.log(result2); // Output:
     * // [{
     * //     "library_name": "myLib5_backup",
     * //     "engine": "LUA",
     * //     "functions": [{
     * //         "name": "myfunc",
     * //         "description": null,
     * //         "flags": [ "no-writes" ],
     * //     }],
     * //     "library_code": "#!lua name=myLib5_backup \n redis.register_function('myfunc', function(keys, args) return args[1] end)"
     * // }]
     * ```
     */
    public async functionList(
        options?: FunctionListOptions & DecoderOption & RouteOption,
    ): Promise<ClusterResponse<FunctionListResponse>> {
        return this.createWritePromise<
            GlideRecord<unknown> | GlideRecord<unknown>[]
        >(createFunctionList(options), options).then((res) =>
            res.length == 0
                ? (res as FunctionListResponse) // no libs
                : ((Array.isArray(res[0])
                      ? // single node response
                        ((res as GlideRecord<unknown>[]).map(
                            convertGlideRecordToRecord,
                        ) as FunctionListResponse)
                      : // multi node response
                        convertGlideRecordToRecord(
                            res as GlideRecord<unknown>,
                        )) as ClusterResponse<FunctionListResponse>),
        );
    }

    /**
     * Returns information about the function that's currently running and information about the
     * available execution engines.
     *
     * The command will be routed to all nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/function-stats/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param options - (Optional) See {@link DecoderOption} and {@link RouteOption}.
     * @returns A `Record` with two keys:
     *     - `"running_script"` with information about the running script.
     *     - `"engines"` with information about available engines and their stats.
     *     - See example for more details.
     *
     * @example
     * ```typescript
     * const response = await client.functionStats("randomNode");
     * console.log(response); // Output:
     * // {
     * //     "running_script":
     * //     {
     * //         "name": "deep_thought",
     * //         "command": ["fcall", "deep_thought", "0"],
     * //         "duration_ms": 5008
     * //     },
     * //     "engines":
     * //     {
     * //         "LUA":
     * //         {
     * //             "libraries_count": 2,
     * //             "functions_count": 3
     * //         }
     * //     }
     * // }
     * // Output if no scripts running:
     * // {
     * //     "running_script": null
     * //     "engines":
     * //     {
     * //         "LUA":
     * //         {
     * //             "libraries_count": 2,
     * //             "functions_count": 3
     * //         }
     * //     }
     * // }
     * ```
     */
    public async functionStats(
        options?: RouteOption & DecoderOption,
    ): Promise<ClusterResponse<FunctionStatsSingleResponse>> {
        return this.createWritePromise<
            ClusterGlideRecord<GlideRecord<unknown>>
        >(createFunctionStats(), options).then(
            (res) =>
                convertGlideRecordToRecord(
                    res,
                ) as ClusterResponse<FunctionStatsSingleResponse>,
        );
    }

    /**
     * Kills a function that is currently executing.
     * `FUNCTION KILL` terminates read-only functions only.
     *
     * @see {@link https://valkey.io/commands/function-kill/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns `"OK"` if function is terminated. Otherwise, throws an error.
     *
     * @example
     * ```typescript
     * await client.functionKill();
     * ```
     */
    public async functionKill(options?: RouteOption): Promise<"OK"> {
        return this.createWritePromise(createFunctionKill(), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Returns the serialized payload of all loaded libraries.
     *
     * @see {@link https://valkey.io/commands/function-dump/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns The serialized payload of all loaded libraries.
     *
     * @example
     * ```typescript
     * const data = await client.functionDump();
     * // data can be used to restore loaded functions on any Valkey instance
     * ```
     */
    public async functionDump(
        options?: RouteOption,
    ): Promise<ClusterResponse<Buffer>> {
        return this.createWritePromise<ClusterGlideRecord<Buffer>>(
            createFunctionDump(),
            { decoder: Decoder.Bytes, ...options },
        ).then((res) => convertClusterGlideRecord(res, true, options?.route));
    }

    /**
     * Restores libraries from the serialized payload returned by {@link functionDump}.
     *
     * @see {@link https://valkey.io/commands/function-restore/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param payload - The serialized data from {@link functionDump}.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `policy`: a policy for handling existing libraries, see {@link FunctionRestorePolicy}.
     *     {@link FunctionRestorePolicy.APPEND} is used by default.
     * - (Optional) `route`: see {@link RouteOption}.
     * @returns `"OK"`.
     *
     * @example
     * ```typescript
     * await client.functionRestore(data, { policy: FunctionRestorePolicy.FLUSH, route: "allPrimaries" });
     * ```
     */
    public async functionRestore(
        payload: Buffer,
        options?: { policy?: FunctionRestorePolicy } & RouteOption,
    ): Promise<"OK"> {
        return this.createWritePromise(
            createFunctionRestore(payload, options?.policy),
            { decoder: Decoder.String, ...options },
        );
    }

    /**
     * Deletes all the keys of all the existing databases. This command never fails.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/flushall/|valkey.io} for details.
     *
     * @param options - (Optional) Additional parameters:
     * - (Optional) `mode`: the flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     * - (Optional) `route`: see {@link RouteOption}.
     * @returns `OK`.
     *
     * @example
     * ```typescript
     * const result = await client.flushall(FlushMode.SYNC);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async flushall(
        options?: {
            mode?: FlushMode;
        } & RouteOption,
    ): Promise<"OK"> {
        return this.createWritePromise(createFlushAll(options?.mode), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Deletes all the keys of the currently selected database. This command never fails.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/flushdb/|valkey.io} for details.
     *
     * @param options - (Optional) Additional parameters:
     * - (Optional) `mode`: the flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     * - (Optional) `route`: see {@link RouteOption}.
     * @returns `OK`.
     *
     * @example
     * ```typescript
     * const result = await client.flushdb(FlushMode.SYNC);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async flushdb(
        options?: {
            mode?: FlushMode;
        } & RouteOption,
    ): Promise<"OK"> {
        return this.createWritePromise(createFlushDB(options?.mode), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Returns the number of keys in the database.
     *
     * The command will be routed to all nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/dbsize/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns The number of keys in the database.
     *     In the case of routing the query to multiple nodes, returns the aggregated number of keys across the different nodes.
     *
     * @example
     * ```typescript
     * const numKeys = await client.dbsize("allPrimaries");
     * console.log("Number of keys across all primary nodes: ", numKeys);
     * ```
     */
    public async dbsize(options?: RouteOption): Promise<number> {
        return this.createWritePromise<number>(createDBSize(), options);
    }

    /** Publish a message on pubsub channel.
     * This command aggregates PUBLISH and SPUBLISH commands functionalities.
     * The mode is selected using the 'sharded' parameter.
     * For both sharded and non-sharded mode, request is routed using hashed channel as key.
     *
     * @see {@link https://valkey.io/commands/publish} and {@link https://valkey.io/commands/spublish} for more details.
     *
     * @param message - Message to publish.
     * @param channel - Channel to publish the message on.
     * @param sharded - Use sharded pubsub mode. Available since Valkey version 7.0.
     * @returns -  Number of subscriptions in primary node that received the message.
     *
     * @example
     * ```typescript
     * // Example usage of publish command
     * const result = await client.publish("Hi all!", "global-channel");
     * console.log(result); // Output: 1 - This message was posted to 1 subscription which is configured on primary node
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of spublish command
     * const result = await client.publish("Hi all!", "global-channel", true);
     * console.log(result); // Output: 2 - Published 2 instances of "Hi to sharded channel1!" message on channel1 using sharded mode
     * ```
     */
    public async publish(
        message: GlideString,
        channel: GlideString,
        sharded = false,
    ): Promise<number> {
        return this.createWritePromise(
            createPublish(message, channel, sharded),
        );
    }

    /**
     * Lists the currently active shard channels.
     * The command is routed to all nodes, and aggregates the response to a single array.
     *
     * @see {@link https://valkey.io/commands/pubsub-shardchannels/|valkey.io} for details.
     *
     * @param options - (Optional) Additional parameters:
     * - (Optional) `pattern`: A glob-style pattern to match active shard channels.
     *     If not provided, all active shard channels are returned.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns A list of currently active shard channels matching the given pattern.
     *          If no pattern is specified, all active shard channels are returned.
     *
     * @example
     * ```typescript
     * const allChannels = await client.pubsubShardchannels();
     * console.log(allChannels); // Output: ["channel1", "channel2"]
     *
     * const filteredChannels = await client.pubsubShardchannels("channel*");
     * console.log(filteredChannels); // Output: ["channel1", "channel2"]
     * ```
     */
    public async pubsubShardChannels(
        options?: {
            pattern?: GlideString;
        } & DecoderOption,
    ): Promise<GlideString[]> {
        return this.createWritePromise(
            createPubsubShardChannels(options?.pattern),
            options,
        );
    }

    /**
     * Returns the number of subscribers (exclusive of clients subscribed to patterns) for the specified shard channels.
     *
     * @see {@link https://valkey.io/commands/pubsub-shardnumsub/|valkey.io} for details.
     * @remarks The command is routed to all nodes, and aggregates the response into a single list.
     *
     * @param channels - The list of shard channels to query for the number of subscribers.
     * @param options - (Optional) see {@link DecoderOption}.
     * @returns A list of the shard channel names and their numbers of subscribers.
     *
     * @example
     * ```typescript
     * const result1 = await client.pubsubShardnumsub(["channel1", "channel2"]);
     * console.log(result1); // Output:
     * // [{ channel: "channel1", numSub: 3}, { channel: "channel2", numSub: 5 }]
     *
     * const result2 = await client.pubsubShardnumsub([]);
     * console.log(result2); // Output: []
     * ```
     */
    public async pubsubShardNumSub(
        channels: GlideString[],
        options?: DecoderOption,
    ): Promise<{ channel: GlideString; numSub: number }[]> {
        return this.createWritePromise<GlideRecord<number>>(
            createPubSubShardNumSub(channels),
            options,
        ).then((res) =>
            res.map((r) => {
                return { channel: r.key, numSub: r.value };
            }),
        );
    }

    /**
     * Returns `UNIX TIME` of the last DB save timestamp or startup timestamp if no save
     * was made since then.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/lastsave/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns `UNIX TIME` of the last DB save executed with success.
     *
     * @example
     * ```typescript
     * const timestamp = await client.lastsave();
     * console.log("Last DB save was done at " + timestamp);
     * ```
     */
    public async lastsave(
        options?: RouteOption,
    ): Promise<ClusterResponse<number>> {
        return this.createWritePromise<ClusterGlideRecord<number>>(
            createLastSave(),
            options,
        ).then((res) => convertClusterGlideRecord(res, true, options?.route));
    }

    /**
     * Returns the latency spike time series for the specified event.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/latency-history/|valkey.io} for details.
     *
     * @param event - The name of the latency event (e.g., `"command"`).
     * @param options - (Optional) See {@link RouteOption}.
     * @returns A {@link ClusterResponse} containing array(s) of {@link LatencyEntry} for the event.
     *
     * @example
     * ```typescript
     * const history = await client.latencyHistory("command");
     * // history is Record<string, LatencyEntry[]> (multi-node) or LatencyEntry[] (single-node)
     * ```
     */
    public async latencyHistory(
        event: GlideString,
        options?: RouteOption,
    ): Promise<ClusterResponse<LatencyEntry[]>> {
        return this.createWritePromise<ClusterGlideRecord<unknown[]>>(
            createLatencyHistory(event),
            options,
        ).then((res) =>
            convertAndParseClusterResponse(
                res,
                isSingleNodeRoute(false, options?.route),
                (raw) => parseLatencyHistoryResponse(raw as unknown[]),
            ),
        );
    }

    /**
     * Reports the latest latency events logged by the server.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/latency-latest/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns A {@link ClusterResponse} containing array(s) of {@link LatencyEventInfo} for the latest latency events.
     *
     * @example
     * ```typescript
     * const latest = await client.latencyLatest();
     * // latest is Record<string, LatencyEventInfo[]> (multi-node) or LatencyEventInfo[] (single-node)
     * ```
     */
    public async latencyLatest(
        options?: RouteOption,
    ): Promise<ClusterResponse<LatencyEventInfo[]>> {
        return this.createWritePromise<ClusterGlideRecord<unknown[]>>(
            createLatencyLatest(),
            options,
        ).then((res) =>
            convertAndParseClusterResponse(
                res,
                isSingleNodeRoute(false, options?.route),
                (raw) => parseLatencyLatestResponse(raw as unknown[]),
            ),
        );
    }

    /**
     * Resets the latency spike time series for all or specified events.
     * If no events are provided, resets the latency spike time series for all events.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/latency-reset/|valkey.io} for details.
     *
     * @param events - The event names to reset. If not provided, resets all events.
     * @param options - (Optional) See {@link RouteOption}.
     * @returns The total number of event time series that were reset across all nodes.
     *
     * @example
     * ```typescript
     * await client.latencyReset(); // Resets all events
     * await client.latencyReset(["command"], { route: "allNodes" });
     * ```
     */
    public async latencyReset(
        events?: GlideString[],
        options?: RouteOption,
    ): Promise<number> {
        return this.createWritePromise(createLatencyReset(events), options);
    }

    /**
     * Synchronously saves the dataset to disk.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/save/|valkey.io} for more details.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns `"OK"`
     *
     * @example
     * ```typescript
     * const result = await client.save();
     * console.log(result); // "OK"
     * ```
     */
    public async save(options?: RouteOption): Promise<"OK"> {
        return this.createWritePromise(createSave(), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Asynchronously saves the dataset to disk in the background.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/bgsave/|valkey.io} for more details.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns A non-empty status string.
     *
     * @example
     * ```typescript
     * const result = await client.bgsave();
     * console.log(result); // "Background saving started"
     * ```
     */
    public async bgsave(
        options?: RouteOption,
    ): Promise<ClusterResponse<string>> {
        return this.createWritePromise<ClusterGlideRecord<string>>(
            createBgSave(),
            {
                decoder: Decoder.String,
                ...options,
            },
        ).then((res) => convertClusterGlideRecord(res, false, options?.route));
    }

    /**
     * Schedules a background save of the database.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/bgsave/|valkey.io} for more details.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns A non-empty status string.
     *
     * @example
     * ```typescript
     * const result = await client.bgsaveSchedule();
     * console.log(result); // "Background saving scheduled"
     * ```
     */
    public async bgsaveSchedule(
        options?: RouteOption,
    ): Promise<ClusterResponse<string>> {
        return this.createWritePromise<ClusterGlideRecord<string>>(
            createBgSave(["SCHEDULE"]),
            {
                decoder: Decoder.String,
                ...options,
            },
        ).then((res) => convertClusterGlideRecord(res, false, options?.route));
    }

    /**
     * Aborts all in-progress and scheduled background saves.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/bgsave/|valkey.io} for more details.
     *
     * @since Valkey 8.1
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns A non-empty status string.
     *
     * @example
     * ```typescript
     * const result = await client.bgsaveCancel();
     * console.log(result); // "Background saving cancelled"
     * ```
     */
    public async bgsaveCancel(
        options?: RouteOption,
    ): Promise<ClusterResponse<string>> {
        return this.createWritePromise<ClusterGlideRecord<string>>(
            createBgSave(["CANCEL"]),
            {
                decoder: Decoder.String,
                ...options,
            },
        ).then((res) => convertClusterGlideRecord(res, false, options?.route));
    }

    /**
     * Initiates a background rewrite of the append-only file (AOF).
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/bgrewriteaof/|valkey.io} for more details.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns A non-empty status string.
     *
     * @example
     * ```typescript
     * const result = await client.bgrewriteaof();
     * console.log(result); // "Background append only file rewriting started"
     * ```
     */
    public async bgrewriteaof(
        options?: RouteOption,
    ): Promise<ClusterResponse<string>> {
        return this.createWritePromise<ClusterGlideRecord<string>>(
            createBgRewriteAof(),
            {
                decoder: Decoder.String,
                ...options,
            },
        ).then((res) => convertClusterGlideRecord(res, false, options?.route));
    }

    /**
     * Returns a random existing key name.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/randomkey/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption} and {@link DecoderOption}.
     * @returns A random existing key name.
     *
     * @example
     * ```typescript
     * const result = await client.randomKey();
     * console.log(result); // Output: "key12" - "key12" is a random existing key name.
     * ```
     */
    public async randomKey(
        options?: DecoderOption & RouteOption,
    ): Promise<GlideString | null> {
        return this.createWritePromise(createRandomKey(), options);
    }

    /**
     * Flushes all the previously watched keys for a transaction. Executing a transaction will
     * automatically flush all previously watched keys.
     *
     * The command will be routed to all primary nodes, unless `route` is provided
     *
     * @see {@link https://valkey.io/commands/unwatch/|valkey.io} and {@link https://valkey.io/topics/transactions/#cas|Valkey GLIDE Documentation} for more details.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns A simple `"OK"` response.
     *
     * @example
     * ```typescript
     * let response = await client.unwatch();
     * console.log(response); // Output: "OK"
     * ```
     */
    public async unwatch(options?: RouteOption): Promise<"OK"> {
        return this.createWritePromise(createUnWatch(), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Suspends all clients for the specified timeout.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/client-pause/|valkey.io} for details.
     *
     * @param timeout - The time in milliseconds to pause clients.
     * @param mode - (Optional) The pause mode to use.
     *   + If not provided, all commands are paused.
     * @param options - (Optional) See {@link RouteOption} and {@link DecoderOption}.
     * @returns `"OK"` response on success.
     *
     * @example
     * ```typescript
     * const result = await client.clientPause(1000);
     * console.log(result); // Output: 'OK'
     * ```
     *
     * @example
     * ```typescript
     * const result = await client.clientPause(1000, ClientPauseMode.WRITE);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async clientPause(
        timeout: number,
        mode?: ClientPauseMode,
        options?: RouteOption & DecoderOption,
    ): Promise<"OK"> {
        return this.createWritePromise(createClientPause(timeout, mode), {
            route: "allPrimaries" as const,
            ...options,
        });
    }

    /**
     * Resumes processing commands on all clients.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/client-unpause/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption} and {@link DecoderOption}.
     * @returns `"OK"` response on success.
     *
     * @example
     * ```typescript
     * const result = await client.clientUnpause();
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async clientUnpause(
        options?: RouteOption & DecoderOption,
    ): Promise<"OK"> {
        return this.createWritePromise(createClientUnpause(), {
            route: "allPrimaries" as const,
            ...options,
        });
    }

    /**
     * Invokes a Lua script with arguments.
     * This method simplifies the process of invoking scripts on a Valkey server by using an object that represents a Lua script.
     * The script loading, argument preparation, and execution will all be handled internally. If the script has not already been loaded,
     * it will be loaded automatically using the `SCRIPT LOAD` command. After that, it will be invoked using the `EVALSHA` command.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/script-load/|SCRIPT LOAD} and {@link https://valkey.io/commands/evalsha/|EVALSHA} on valkey.io for details.
     *
     * @param script - The Lua script to execute.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `args`: the arguments for the script.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * - (Optional) `route`: see {@link RouteOption}.
     * @returns A value that depends on the script that was executed.
     *
     * @example
     * ```typescript
     * const luaScript = new Script("return { ARGV[1] }");
     * const result = await client.invokeScript(luaScript, { args: ["bar"] });
     * console.log(result); // Output: ['bar']
     * ```
     */
    public async invokeScriptWithRoute(
        script: Script,
        options?: { args?: GlideString[] } & DecoderOption & RouteOption,
    ): Promise<ClusterResponse<GlideReturnType>> {
        const scriptInvocation = command_request.ScriptInvocation.create({
            hash: script.getHash(),
            keys: [],
            args: options?.args?.map((arg) =>
                typeof arg === "string" ? Buffer.from(arg) : arg,
            ),
        });
        return this.createScriptInvocationWithRoutePromise<
            ClusterGlideRecord<GlideReturnType>
        >(scriptInvocation, options).then((res) =>
            convertClusterGlideRecord(res, true, options?.route),
        );
    }

    private async createScriptInvocationWithRoutePromise<T = GlideString>(
        command: command_request.ScriptInvocation,
        options?: { args?: GlideString[] } & DecoderOption & RouteOption,
    ): Promise<T> {
        return this.createScriptInvocationPromise<T>(command, options);
    }

    /**
     * Checks existence of scripts in the script cache by their SHA1 digest.
     *
     * @see {@link https://valkey.io/commands/script-exists/|valkey.io} for more details.
     *
     * @param sha1s - List of SHA1 digests of the scripts to check.
     * @param options - (Optional) See {@link RouteOption}.
     * @returns A list of boolean values indicating the existence of each script.
     *
     * @example
     * ```typescript
     * console result = await client.scriptExists(["sha1_digest1", "sha1_digest2"]);
     * console.log(result); // Output: [true, false]
     * ```
     */
    public async scriptExists(
        sha1s: GlideString[],
        options?: RouteOption,
    ): Promise<ClusterResponse<boolean[]>> {
        return this.createWritePromise(createScriptExists(sha1s), options);
    }

    /**
     * Flushes the Lua scripts cache.
     *
     * @see {@link https://valkey.io/commands/script-flush/|valkey.io} for more details.
     *
     * @param options - (Optional) Additional parameters:
     * - (Optional) `mode`: the flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     * - (Optional) `route`: see {@link RouteOption}.
     * @returns A simple `"OK"` response.
     *
     * @example
     * ```typescript
     * console result = await client.scriptFlush(FlushMode.SYNC);
     * console.log(result); // Output: "OK"
     * ```
     */
    public async scriptFlush(
        options?: {
            mode?: FlushMode;
        } & RouteOption,
    ): Promise<"OK"> {
        return this.createWritePromise(createScriptFlush(options?.mode), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Kills the currently executing Lua script, assuming no write operation was yet performed by the script.
     *
     * @see {@link https://valkey.io/commands/script-kill/|valkey.io} for more details.
     * @remarks The command is routed to all nodes, and aggregates the response to a single array.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns A simple `"OK"` response.
     *
     * @example
     * ```typescript
     * console result = await client.scriptKill();
     * console.log(result); // Output: "OK"
     * ```
     */
    public async scriptKill(options?: RouteOption): Promise<"OK"> {
        return this.createWritePromise(createScriptKill(), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Subscribes the client to the specified sharded channels (non-blocking).
     * Returns immediately without waiting for subscription confirmation.
     * Available since Valkey 7.0.
     *
     * @see {@link https://valkey.io/commands/ssubscribe/|valkey.io} for details.
     *
     * @param channels - A collection of channel names to subscribe to.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A promise that resolves immediately.
     *
     * @example
     * ```typescript
     * await clusterClient.ssubscribeLazy(new Set(["shard-channel-1"]));
     * ```
     */
    public async ssubscribeLazy(
        channels: Iterable<GlideString>,
        options?: DecoderOption,
    ): Promise<void> {
        const channelsArray = Array.from(channels);
        return this.createWritePromise(
            createSSubscribeLazy(channelsArray),
            options,
        );
    }

    /**
     * Subscribes the client to the specified sharded channels (blocking).
     * Waits for subscription confirmation or until timeout.
     * Available since Valkey 7.0.
     *
     * @see {@link https://valkey.io/commands/ssubscribe/|valkey.io} for details.
     *
     * @param channels - A collection of channel names to subscribe to.
     * @param timeoutMs - Maximum time in milliseconds to wait. Use 0 for indefinite wait.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A promise that resolves when subscription is confirmed or timeout occurs.
     *
     * @example
     * ```typescript
     * // Wait up to 5 seconds
     * await clusterClient.ssubscribe(new Set(["shard-channel-1"]), 5000);
     * // Wait indefinitely
     * await clusterClient.ssubscribe(new Set(["shard-channel-1"]), 0);
     * ```
     */
    public async ssubscribe(
        channels: Iterable<GlideString>,
        timeoutMs: number,
        options?: DecoderOption,
    ): Promise<void> {
        const channelsArray = Array.from(channels);
        return this.createWritePromise(
            createSSubscribe(channelsArray, timeoutMs),
            options,
        );
    }

    /**
     * Unsubscribes the client from the specified sharded channels (non-blocking).
     * Pass null or ALL_CHANNELS to unsubscribe from all sharded channels.
     * Available since Valkey 7.0.
     *
     * @see {@link https://valkey.io/commands/sunsubscribe/|valkey.io} for details.
     *
     * @param channels - Sharded channel names to unsubscribe from, or null for all channels.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A promise that resolves immediately.
     *
     * @example
     * ```typescript
     * await clusterClient.sunsubscribeLazy(new Set(["shard-channel-1"]));
     * // Unsubscribe from all sharded channels
     * await clusterClient.sunsubscribeLazy(ALL_SHARDED_CHANNELS);
     * ```
     */
    public async sunsubscribeLazy(
        channels?: Iterable<GlideString> | null,
        options?: DecoderOption,
    ): Promise<void> {
        const channelsArray = channels ? Array.from(channels) : undefined;
        return this.createWritePromise(
            createSUnsubscribeLazy(channelsArray),
            options,
        );
    }

    /**
     * Unsubscribes the client from the specified sharded channels (blocking).
     * Pass null or ALL_CHANNELS to unsubscribe from all sharded channels.
     * Available since Valkey 7.0.
     *
     * @see {@link https://valkey.io/commands/sunsubscribe/|valkey.io} for details.
     *
     * @param channels - Sharded channel names to unsubscribe from, or null for all channels.
     * @param timeoutMs - Maximum time in milliseconds to wait. Use 0 for indefinite wait.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A promise that resolves when unsubscription is confirmed or timeout occurs.
     *
     * @example
     * ```typescript
     * await clusterClient.sunsubscribe(new Set(["shard-channel-1"]), 5000);
     * // Unsubscribe from all sharded channels with timeout
     * await clusterClient.sunsubscribe(ALL_SHARDED_CHANNELS, 5000);
     * ```
     */
    public async sunsubscribe(
        channels: Iterable<GlideString> | null,
        timeoutMs: number,
        options?: DecoderOption,
    ): Promise<void> {
        const channelsArray = channels ? Array.from(channels) : [];
        return this.createWritePromise(
            createSUnsubscribe(channelsArray, timeoutMs),
            options,
        );
    }

    /**
     * Returns the current subscription state for the cluster client.
     *
     * @see {@link https://valkey.io/commands/pubsub/|valkey.io} for details.
     *
     * @returns A promise that resolves to the subscription state containing
     *          desired and actual subscriptions organized by channel mode.
     *
     * @example
     * ```typescript
     * const state = await clusterClient.getSubscriptions();
     * console.log("Desired exact channels:", state.desiredSubscriptions[GlideClusterClientConfiguration.PubSubChannelModes.Exact]);
     * console.log("Actual sharded channels:", state.actualSubscriptions[GlideClusterClientConfiguration.PubSubChannelModes.Sharded]);
     * ```
     */
    public async getSubscriptions(): Promise<ClusterPubSubState> {
        const response = await this.createWritePromise<unknown[]>(
            createGetSubscriptions(),
        );
        return this.parseGetSubscriptionsResponse<GlideClusterClientConfiguration.PubSubChannelModes>(
            response,
        );
    }

    // TODO #6166: move to shared base once server management refactor lands

    /**
     * Returns a report about memory problems detected by the server.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/memory-doctor/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption} to specify the route to override the default.
     * @returns A {@link ClusterResponse} containing the memory diagnostic report string(s).
     *
     * @example
     * ```typescript
     * const report = await client.memoryDoctor();
     * // report is Record<string, string> (multi-node) or string (single-node)
     * ```
     */
    public async memoryDoctor(
        options?: RouteOption,
    ): Promise<ClusterResponse<string>> {
        return this.createWritePromise<ClusterGlideRecord<string>>(
            createMemoryDoctor(),
            { decoder: Decoder.String, ...options },
        ).then((res) => convertClusterGlideRecord(res, false, options?.route));
    }

    /**
     * Returns the internal statistics of the memory allocator.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/memory-malloc-stats/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption} to specify the route to override the default.
     * @returns A {@link ClusterResponse} containing the memory allocator statistics string(s).
     *
     * @example
     * ```typescript
     * const stats = await client.memoryMallocStats();
     * // stats is Record<string, string> (multi-node) or string (single-node)
     * ```
     */
    public async memoryMallocStats(
        options?: RouteOption,
    ): Promise<ClusterResponse<string>> {
        return this.createWritePromise<ClusterGlideRecord<string>>(
            createMemoryMallocStats(),
            { decoder: Decoder.String, ...options },
        ).then((res) => convertClusterGlideRecord(res, false, options?.route));
    }

    /**
     * Asks the server to reclaim memory from the allocator back to the operating system.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/memory-purge/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption} to specify the route to override the default.
     * @returns `"OK"`.
     *
     * @example
     * ```typescript
     * const result = await client.memoryPurge();
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async memoryPurge(options?: RouteOption): Promise<"OK"> {
        return this.createWritePromise(createMemoryPurge(), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Returns detailed memory consumption statistics of the server.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/memory-stats/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption} to specify the route to override the default.
     * @returns A {@link ClusterResponse} containing {@link MemoryStats} object(s).
     *
     * @example
     * ```typescript
     * const stats = await client.memoryStats();
     * // stats is Record<string, MemoryStats> (multi-node) or MemoryStats (single-node)
     * ```
     */
    public async memoryStats(
        options?: RouteOption,
    ): Promise<ClusterResponse<MemoryStats>> {
        return this.createWritePromise<
            ClusterGlideRecord<Record<string, unknown>>
        >(createMemoryStats(), options).then((res) =>
            convertAndParseClusterResponse(
                res,
                isSingleNodeRoute(false, options?.route),
                (raw) =>
                    parseMemoryStatsResponse(
                        isGlideRecord(raw)
                            ? (convertGlideRecordToRecord(
                                  raw as unknown as GlideRecord<unknown>,
                              ) as Record<string, unknown>)
                            : (raw as Record<string, unknown>),
                    ),
            ),
        );
    }
}
