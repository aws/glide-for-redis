/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { connection_request } from "../build-ts/ProtobufMessage";
import { GlideClientHandle } from "../build-ts/native";
import {
    AdvancedBaseClientConfiguration,
    BaseClient,
    BaseClientConfiguration,
    convertGlideRecordToRecord,
    Decoder,
    DecoderOption,
    GlideRecord,
    GlideReturnType,
    GlideString,
    PubSubMsg,
    NodeDiscoveryMode,
} from "./BaseClient";
import { Batch } from "./Batch";
import {
    BatchOptions,
    ClientPauseMode,
    ClientTrackingInfo,
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
    createMigrate,
    createMemoryDoctor,
    createMemoryMallocStats,
    createMemoryPurge,
    createMemoryStats,
    createSave,
    createBgSave,
    createBgRewriteAof,
    createPing,
    createPublish,
    createRandomKey,
    createScan,
    createScriptExists,
    createScriptFlush,
    createScriptKill,
    createFailover,
    createReplicaOf,
    createReplicaOfNoOne,
    createTime,
    createUnWatch,
    FailoverOptions,
    FlushMode,
    FunctionListOptions,
    FunctionListResponse,
    FunctionRestorePolicy,
    FunctionStatsFullResponse,
    InfoOptions,
    LatencyEntry,
    LatencyEventInfo,
    LolwutOptions,
    MigrateOptions,
    MemoryStats,
    parseClientTrackingInfoResponse,
    ScanOptions,
    parseLatencyHistoryResponse,
    parseLatencyLatestResponse,
    parseMemoryStatsResponse,
} from "./Commands";

/* eslint-disable-next-line @typescript-eslint/no-namespace */
export namespace GlideClientConfiguration {
    /**
     * Enum representing pubsub subscription modes.
     * @see {@link  https://valkey.io/docs/topics/pubsub/|Valkey PubSub Documentation} for more details.
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
    }

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
 * Represents the subscription state for a standalone client.
 *
 * @remarks
 * This interface provides information about the current PubSub subscriptions for a standalone client.
 * It includes both the desired subscriptions (what the client wants to maintain) and the actual
 * subscriptions (what is currently established on the server).
 *
 * The subscriptions are organized by channel mode:
 * - {@link GlideClientConfiguration.PubSubChannelModes.Exact | Exact}: Exact channel names
 * - {@link GlideClientConfiguration.PubSubChannelModes.Pattern | Pattern}: Channel patterns using glob-style matching
 *
 * @example
 * ```typescript
 * const state = await client.getSubscriptions();
 * console.log("Desired exact channels:", state.desiredSubscriptions[GlideClientConfiguration.PubSubChannelModes.Exact]);
 * console.log("Actual exact channels:", state.actualSubscriptions[GlideClientConfiguration.PubSubChannelModes.Exact]);
 * ```
 */
export interface StandalonePubSubState {
    /**
     * Desired subscriptions organized by channel mode.
     * These are the subscriptions the client wants to maintain.
     */
    desiredSubscriptions: Partial<
        Record<GlideClientConfiguration.PubSubChannelModes, Set<GlideString>>
    >;

    /**
     * Actual subscriptions currently active on the server.
     * These are the subscriptions that are actually established.
     */
    actualSubscriptions: Partial<
        Record<GlideClientConfiguration.PubSubChannelModes, Set<GlideString>>
    >;
}

/**
 * Configuration options for creating a {@link GlideClient | GlideClient}.
 *
 * Extends `BaseClientConfiguration` with properties specific to `GlideClient`, such as
 * reconnection strategies, and Pub/Sub subscription settings.
 *
 * @remarks
 * This configuration allows you to tailor the client's behavior when connecting to a standalone Valkey Glide server.
 *
 * - **Database Selection**: Use `databaseId` (inherited from BaseClientConfiguration) to specify which logical database to connect to.
 * - **Client-Side Caching**: Use `clientSideCache` (inherited from BaseClientConfiguration) to enable local caching for improved performance.
 * - **Pub/Sub Subscriptions**: Predefine Pub/Sub channels and patterns to subscribe to upon connection establishment.
 *
 * @example
 * ```typescript
 * const config: GlideClientConfiguration = {
 *   databaseId: 1, // Inherited from BaseClientConfiguration
 *   clientSideCache: ClientSideCache.create(1024, 60000), // 1MB cache, 1 min TTL
 *   pubsubSubscriptions: {
 *     channelsAndPatterns: {
 *       [GlideClientConfiguration.PubSubChannelModes.Pattern]: new Set(['news.*']),
 *     },
 *     callback: (msg) => {
 *       console.log(`Received message on ${msg.channel}:`, msg.payload);
 *     },
 *   },
 * };
 * ```
 */
export type GlideClientConfiguration = BaseClientConfiguration & {
    /**
     * PubSub subscriptions to be used for the client.
     * Will be applied via SUBSCRIBE/PSUBSCRIBE commands during connection establishment.
     */
    pubsubSubscriptions?: GlideClientConfiguration.PubSubSubscriptions;
    /**
     * Advanced configuration settings for the client.
     */
    advancedConfiguration?: AdvancedGlideClientConfiguration;
    /**
     * When true, enables read-only mode for the standalone client.
     *
     * In read-only mode:
     * - The client skips primary node detection (INFO REPLICATION command)
     * - All connected nodes are treated as valid read targets
     * - Write commands are blocked and will return an error
     * - The default ReadFrom strategy becomes PreferReplica if not explicitly set
     *
     * This is useful for connecting to replica-only deployments or when you want to
     * prevent accidental write operations.
     *
     * Note: read-only mode is not compatible with AZAffinity or AZAffinityReplicasAndPrimary
     * read strategies.
     *
     * Defaults to false.
     */
    readOnly?: boolean;
    /**
     * Controls how the client discovers node roles and topology in standalone mode.
     *
     * @see {@link NodeDiscoveryMode} for available modes.
     */
    nodeDiscoveryMode?: NodeDiscoveryMode;
};

/**
 * Represents advanced configuration settings for creating a {@link GlideClient | GlideClient} used in {@link GlideClientConfiguration | GlideClientConfiguration}.
 *
 *
 * @example
 * ```typescript
 * const config: AdvancedGlideClientConfiguration = {
 *   connectionTimeout: 500, // Set the connection timeout to 500ms
 *   tlsAdvancedConfiguration: {
 *     insecure: true, // Skip TLS certificate verification (use only in development)
 *   },
 * };
 * ```
 */
export type AdvancedGlideClientConfiguration =
    AdvancedBaseClientConfiguration & {};

/**
 * Client used for connection to standalone servers.
 * Use {@link createClient} to request a client.
 *
 * @see For full documentation refer to {@link https://glide.valkey.io/how-to/client-initialization/#standalone | Valkey GLIDE Documentation}.
 */
export class GlideClient extends BaseClient {
    /**
     * @internal
     */
    protected createClientRequest(
        options: GlideClientConfiguration,
    ): connection_request.IConnectionRequest {
        const configuration = super.createClientRequest(options);

        this.configurePubsub(options, configuration);

        if (options.advancedConfiguration) {
            this.configureAdvancedConfigurationBase(
                options.advancedConfiguration,
                configuration,
            );
        }

        // Set read-only mode if specified
        if (options.readOnly !== undefined) {
            configuration.readOnly = options.readOnly;
        }

        if (options.nodeDiscoveryMode !== undefined) {
            configuration.nodeDiscoveryMode =
                options.nodeDiscoveryMode as number;
        }

        return configuration;
    }

    /**
     * Creates a new `GlideClient` instance and establishes a connection to a standalone Valkey server.
     *
     * @param options - The configuration options for the client, including server addresses, authentication credentials, TLS settings, database selection, reconnection strategy, and Pub/Sub subscriptions.
     * @returns A promise that resolves to a connected `GlideClient` instance.
     *
     * @remarks
     * Use this static method to create and connect a `GlideClient` to a standalone Valkey server.
     * The client will automatically handle connection establishment, including any authentication and TLS configurations.
     *
     * @example
     * ```typescript
     * // Connecting to a Standalone Server
     * import { GlideClient, GlideClientConfiguration } from '@valkey/valkey-glide';
     *
     * const client = await GlideClient.createClient({
     *   addresses: [
     *     { host: 'primary.example.com', port: 6379 },
     *     { host: 'replica1.example.com', port: 6379 },
     *   ],
     *   databaseId: 1,
     *   credentials: {
     *     username: 'user1',
     *     password: 'passwordA',
     *   },
     *   useTLS: true,
     *   connectionBackoff: {
     *     numberOfRetries: 5,
     *     factor: 1000,
     *     exponentBase: 2,
     *     jitter: 20,
     *   },
     *   pubsubSubscriptions: {
     *     channelsAndPatterns: {
     *       [GlideClientConfiguration.PubSubChannelModes.Exact]: new Set(['updates']),
     *     },
     *     callback: (msg) => {
     *       console.log(`Received message: ${msg.payload}`);
     *     },
     *   },
     * });
     * ```
     *
     * @remarks
     * - **Authentication**: If `credentials` are provided, the client will attempt to authenticate using the specified username and password.
     * - **TLS**: If `useTLS` is set to `true`, the client will establish a secure connection using TLS.
     *      Should match the TLS configuration of the server/cluster, otherwise the connection attempt will fail.
     *      For advanced tls configuration, please use the {@link AdvancedGlideClientConfiguration} option.
     * - **Reconnection Strategy**: The `connectionBackoff` settings define how the client will attempt to reconnect in case of disconnections.
     * - **Pub/Sub Subscriptions**: Any channels or patterns specified in `pubsubSubscriptions` will be subscribed to upon connection.
     */
    public static async createClient(
        options: GlideClientConfiguration,
    ): Promise<GlideClient> {
        return super.createClientInternal<GlideClient>(
            options,
            (options?: GlideClientConfiguration) => new GlideClient(options),
        );
    }

    /**
     * @internal
     * Wrap a pre-built {@link GlideClientHandle} (from the pool layer) in a
     * `GlideClient` instance.  No network connection is made — the handle
     * already owns a live connection managed by the pool.
     *
     * Used by `ClientPool.acquire()` after `poolBuildHandle` returns a handle.
     */
    public static createFromHandle(
        handle: GlideClientHandle,
        options: GlideClientConfiguration,
    ): GlideClient {
        return super.createClientFromHandle<GlideClient>(
            handle,
            options,
            (options?: GlideClientConfiguration) => new GlideClient(options),
        );
    }

    /**
     * Acquire a pool client by ID and wrap it as a GlideClient.
     *
     * Called by {@link ClientPool} after a successful `poolTryAcquire` or
     * `poolAcquireBlocking`. Spins up a fresh worker thread for the
     * already-connected pool client and returns a fully operational instance.
     *
     * @internal - intended for use by ClientPool only.
     */
    public static async fromPoolClientId(
        clientId: number,
        options: GlideClientConfiguration,
    ): Promise<GlideClient> {
        const { poolBuildHandle } = await import("../build-ts/native");
        // Create the client instance whose handleResponsesAvailable will be stored
        // as the Rust TSFN wake callback. The same instance must be the one that
        // receives the handle — arrow functions bind `this` at construction, so
        // the callback and the client handle MUST live on the same object.
        const client = new GlideClient(options);
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
     * Serialise a {@link GlideClientConfiguration} into the protobuf bytes
     * used by the pool Rust APIs.  Does not open a network connection.
     */
    public static serializeConfig(options: GlideClientConfiguration): {
        bytes: Uint8Array;
        resolverKey: string | undefined;
    } {
        return super.serializeConnectionRequest(
            options,
            (opts?: BaseClientConfiguration) =>
                new GlideClient(opts as GlideClientConfiguration),
        );
    }

    /**
     * Execute a batch by processing the queued commands.
     *
     * **Notes:**
     * - **Atomic Batches - Transactions:** If the transaction fails due to a `WATCH` command, `EXEC` will return `null`.
     *
     * @see {@link https://github.com/valkey-io/valkey-glide/wiki/NodeJS-wrapper#transaction|Valkey GLIDE Documentation} for details on Valkey Transactions.
     *
     * @param batch - A {@link Batch} object containing a list of commands to be executed.
     * @param raiseOnError - Determines how errors are handled within the batch response.
     *   - If `true`, the first the first encountered error in the batch will be raised as an exception of type {@link RequestError}
     * after all retries and reconnections have been exhausted.
     *  - If `false`, errors will be included as part of the batch response, allowing the caller to process both successful and failed commands together.
     * In this case, error details will be provided as instances of {@link RequestError} in the response list.
     * @param options - (Optional) See {@link BatchOptions} and {@link DecoderOption}.
     * @returns A list of results corresponding to the execution of each command in the transaction.
     *     If a command returns a value, it will be included in the list. If a command doesn't return a value,
     *     the list entry will be `null`.
     *     If the transaction failed due to a `WATCH` command, `exec` will return `null`.
     *
     * @see {@link https://valkey.io/docs/topics/transactions/|Valkey Transactions (Atomic Batches)} for details.
     * @see {@link https://valkey.io/docs/topics/pipelining/|Valkey Pipelines (Non-Atomic Batches)} for details.
     *
     * @example
     * ```typescript
     * // Example 1: Atomic Batch (Transaction) with Options
     * const transaction = new Batch(true) // Atomic (Transactional)
     *     .set("key", "value")
     *     .get("key");
     *
     * const result = await client.exec(transaction, false, {timeout: 1000}); // Execute the transaction with raiseOnError = false and a timeout of 1000ms
     * console.log(result); // Output: ['OK', 'value']
     * ```
     *
     * @example
     * ```typescript
     * // Example 2: Non-Atomic Batch (Pipelining) with Options
     * const pipeline = new Batch(false) // Non-Atomic (Pipelining)
     *    .set("key1", "value1")
     *    .set("key2", "value2")
     *   .get("key1")
     *   .get("key2");
     *
     * const result = await client.exec(pipeline, false, {timeout: 1000}); // Execute the pipeline with raiseOnError = false and a timeout of 1000ms
     * console.log(result); // Output: ['OK', 'OK', 'value1', 'value2']
     * ```
     */
    public async exec(
        batch: Batch,
        raiseOnError: boolean,
        options?: BatchOptions & DecoderOption,
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

    /** Executes a single command, without checking inputs. Every part of the command, including subcommands,
     *  should be added as a separate value in args.
     *
     * Note: An error will occur if the string decoder is used with commands that return only bytes as a response.
     *
     * @see {@link https://glide.valkey.io/concepts/client-features/custom-commands/|Valkey GLIDE Documentation} for details on the restrictions and limitations of the custom command API.
     *
     * @param args - A list including the command name and arguments for the custom command.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns The executed custom command return value.
     *
     * @example
     * ```typescript
     * // Example usage of customCommand method to retrieve pub/sub clients
     * const result = await client.customCommand(["CLIENT", "LIST", "TYPE", "PUBSUB"]);
     * console.log(result); // Output: Returns a list of all pub/sub clients
     * ```
     */
    public async customCommand(
        args: GlideString[],
        options?: DecoderOption,
    ): Promise<GlideReturnType> {
        return this.createWritePromise(createCustomCommand(args), options);
    }

    /**
     * Pings the server.
     *
     * @see {@link https://valkey.io/commands/ping/|valkey.io} for details.
     *
     * @param options - (Optional) Additional parameters:
     * - (Optional) `message` : a message to include in the `PING` command.
     *   + If not provided, the server will respond with `"PONG"`.
     *   + If provided, the server will respond with a copy of the message.
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
        } & DecoderOption,
    ): Promise<GlideString> {
        return this.createWritePromise(createPing(options?.message), options);
    }

    /**
     * Gets information and statistics about the server.
     *
     * Starting from server version 7, command supports multiple section arguments.
     *
     * @see {@link https://valkey.io/commands/info/|valkey.io} for details.
     *
     * @param sections - (Optional) A list of {@link InfoOptions} values specifying which sections of information to retrieve.
     *     When no parameter is provided, {@link InfoOptions.Default|Default} is assumed.
     * @returns A string containing the information for the sections requested.
     *
     * @example
     * ```typescript
     * // Example usage of the info method with retrieving total_net_input_bytes from the result
     * const result = await client.info(new Section[] { Section.STATS });
     * console.log(someParsingFunction(result, "total_net_input_bytes")); // Output: 1
     * ```
     */
    public async info(sections?: InfoOptions[]): Promise<string> {
        return this.createWritePromise(createInfo(sections), {
            decoder: Decoder.String,
        });
    }

    /**
     * Gets the name of the primary's connection.
     *
     * @see {@link https://valkey.io/commands/client-getname/|valkey.io} for more details.
     *
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns The name of the client connection as a string if a name is set, or `null` if no name is assigned.
     *
     * @example
     * ```typescript
     * // Example usage of client_getname method
     * const result = await client.client_getname();
     * console.log(result); // Output: 'Client Name'
     * ```
     */
    public async clientGetName(
        options?: DecoderOption,
    ): Promise<GlideString | null> {
        return this.createWritePromise(createClientGetName(), options);
    }

    /**
     * Rewrites the configuration file with the current configuration.
     *
     * @see {@link https://valkey.io/commands/config-rewrite/|valkey.io} for details.
     *
     * @returns "OK" when the configuration was rewritten properly. Otherwise, an error is thrown.
     *
     * @example
     * ```typescript
     * // Example usage of configRewrite command
     * const result = await client.configRewrite();
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async configRewrite(): Promise<"OK"> {
        return this.createWritePromise(createConfigRewrite(), {
            decoder: Decoder.String,
        });
    }

    /**
     * Resets the statistics reported by the server using the `INFO` and `LATENCY HISTOGRAM` commands.
     *
     * @see {@link https://valkey.io/commands/config-resetstat/|valkey.io} for details.
     *
     * @returns always "OK".
     *
     * @example
     * ```typescript
     * // Example usage of configResetStat command
     * const result = await client.configResetStat();
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async configResetStat(): Promise<"OK"> {
        return this.createWritePromise(createConfigResetStat(), {
            decoder: Decoder.String,
        });
    }

    /**
     * Returns the current connection ID.
     *
     * @see {@link https://valkey.io/commands/client-id/|valkey.io} for details.
     *
     * @returns The ID of the connection.
     *
     * @example
     * ```typescript
     * const result = await client.clientId();
     * console.log("Connection id: " + result);
     * ```
     */
    public async clientId(): Promise<number> {
        return this.createWritePromise(createClientId());
    }

    /**
     * Returns information about the current client connection's use
     * of the server assisted client side caching feature.
     *
     * @see {@link https://valkey.io/commands/client-trackinginfo/|valkey.io} for details.
     *
     * @returns The tracking info for the client.
     *
     * @example
     * ```typescript
     * const info = await client.clientTrackingInfo();
     * console.log(info.flags); // Set { "off" }
     * console.log(info.redirect); // -1
     * console.log(info.prefixes); // Set {}
     * ```
     */
    public async clientTrackingInfo(): Promise<ClientTrackingInfo> {
        return this.createWritePromise<GlideRecord<unknown>>(
            createClientTrackingInfo(),
        ).then((res) =>
            parseClientTrackingInfoResponse(
                convertGlideRecordToRecord(res) as Record<string, unknown>,
            ),
        );
    }

    /**
     * Reads the configuration parameters of the running server.
     * Starting from server version 7, command supports multiple parameters.
     *
     * @see {@link https://valkey.io/commands/config-get/|valkey.io} for details.
     *
     * @param parameters - A list of configuration parameter names to retrieve values for.
     * @param options - (Optional) See {@link DecoderOption}.
     *
     * @returns A map of values corresponding to the configuration parameters.
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
        options?: DecoderOption,
    ): Promise<Record<string, GlideString>> {
        return this.createWritePromise<GlideRecord<GlideString>>(
            createConfigGet(parameters),
            options,
        ).then(convertGlideRecordToRecord);
    }

    /**
     * Sets configuration parameters to the specified values.
     * Starting from server version 7, command supports multiple parameters.
     *
     * @see {@link  https://valkey.io/commands/config-set/|valkey.io} for details.
     * @param parameters - A map consisting of configuration parameters and their respective values to set.
     * @returns `"OK"` when the configuration was set properly. Otherwise an error is thrown.
     *
     * @example
     * ```typescript
     * // Example usage of configSet method to set multiple configuration parameters
     * const result = await client.configSet({ timeout: "1000", maxmemory, "1GB" });
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async configSet(
        parameters: Record<string, GlideString>,
    ): Promise<"OK"> {
        return this.createWritePromise(createConfigSet(parameters), {
            decoder: Decoder.String,
        });
    }

    /**
     * Echoes the provided `message` back.
     *
     * @see {@link https://valkey.io/commands/echo|valkey.io} for more details.
     *
     * @param message - The message to be echoed back.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns The provided `message`.
     *
     * @example
     * ```typescript
     * // Example usage of the echo command
     * const echoedMessage = await client.echo("valkey-glide");
     * console.log(echoedMessage); // Output: 'valkey-glide'
     * ```
     */
    public async echo(
        message: GlideString,
        options?: DecoderOption,
    ): Promise<GlideString> {
        return this.createWritePromise(createEcho(message), options);
    }

    /**
     * Returns the server time.
     *
     * @see {@link https://valkey.io/commands/time/|valkey.io} for details.
     *
     * @returns The current server time as an `array` with two items:
     * - A Unix timestamp,
     * - The amount of microseconds already elapsed in the current second.
     *
     * @example
     * ```typescript
     * console.log(await client.time()); // Output: ['1710925775', '913580']
     * ```
     */
    public async time(): Promise<[string, string]> {
        return this.createWritePromise(createTime(), {
            decoder: Decoder.String,
        });
    }

    /**
     * Displays a piece of generative computer art and the server version.
     *
     * @see {@link https://valkey.io/commands/lolwut/|valkey.io} for more details.
     *
     * @param options - (Optional) The LOLWUT options - see {@link LolwutOptions}.
     * @returns A piece of generative computer art along with the current server version.
     *
     * @example
     * ```typescript
     * const response = await client.lolwut({ version: 6, parameters: [40, 20] });
     * console.log(response); // Output: "Valkey ver. 7.2.3" - Indicates the current server version.
     * ```
     */
    public async lolwut(options?: LolwutOptions): Promise<string> {
        return this.createWritePromise(createLolwut(options), {
            decoder: Decoder.String,
        });
    }

    /**
     * Deletes a library and all its functions.
     *
     * @see {@link https://valkey.io/commands/function-delete/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param libraryCode - The library name to delete.
     * @returns A simple `"OK"` response.
     *
     * @example
     * ```typescript
     * const result = await client.functionDelete("libName");
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async functionDelete(libraryCode: GlideString): Promise<"OK"> {
        return this.createWritePromise(createFunctionDelete(libraryCode), {
            decoder: Decoder.String,
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
     * - (Optional) `replace`: Whether the given library should overwrite a library with the same name if it
     *     already exists.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns The library name that was loaded.
     *
     * @example
     * ```typescript
     * const code = "#!lua name=mylib \n redis.register_function('myfunc', function(keys, args) return args[1] end)";
     * const result = await client.functionLoad(code, { replace: true });
     * console.log(result); // Output: 'mylib'
     * ```
     */
    public async functionLoad(
        libraryCode: GlideString,
        options?: { replace?: boolean } & DecoderOption,
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
     * @param mode - (Optional) The flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     * @returns A simple `"OK"` response.
     *
     * @example
     * ```typescript
     * const result = await client.functionFlush(FlushMode.SYNC);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async functionFlush(mode?: FlushMode): Promise<"OK"> {
        return this.createWritePromise(createFunctionFlush(mode), {
            decoder: Decoder.String,
        });
    }

    /**
     * Returns information about the functions and libraries.
     *
     * @see {@link https://valkey.io/commands/function-list/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param options - (Optional) See {@link FunctionListOptions} and {@link DecoderOption}.
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
        options?: FunctionListOptions & DecoderOption,
    ): Promise<FunctionListResponse> {
        return this.createWritePromise<GlideRecord<unknown>[]>(
            createFunctionList(options),
            options,
        ).then(
            (res) =>
                res.map(convertGlideRecordToRecord) as FunctionListResponse,
        );
    }

    /**
     * Returns information about the function that's currently running and information about the
     * available execution engines.
     *
     * FUNCTION STATS runs on all nodes of the server, including primary and replicas.
     * The response includes a mapping from node address to the command response for that node.
     *
     * @see {@link https://valkey.io/commands/function-stats/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A Record where the key is the node address and the value is a Record with two keys:
     *          - `"running_script"`: Information about the running script, or `null` if no script is running.
     *          - `"engines"`: Information about available engines and their stats.
     *          - see example for more details.
     * @example
     * ```typescript
     * const response = await client.functionStats();
     * console.log(response); // Example output:
     * // {
     * //     "127.0.0.1:6379": {                // Response from the primary node
     * //         "running_script": {
     * //             "name": "foo",
     * //             "command": ["FCALL", "foo", "0", "hello"],
     * //             "duration_ms": 7758
     * //         },
     * //         "engines": {
     * //             "LUA": {
     * //                 "libraries_count": 1,
     * //                 "functions_count": 1
     * //             }
     * //         }
     * //     },
     * //     "127.0.0.1:6380": {                // Response from a replica node
     * //         "running_script": null,
     * //         "engines": {
     * //             "LUA": {
     * //                 "libraries_count": 1,
     * //                 "functions_count": 1
     * //             }
     * //         }
     * //     }
     * // }
     * ```
     */
    public async functionStats(
        options?: DecoderOption,
    ): Promise<FunctionStatsFullResponse> {
        return this.createWritePromise<GlideRecord<unknown>>(
            createFunctionStats(),
            options,
        ).then(
            (res) =>
                convertGlideRecordToRecord(res) as FunctionStatsFullResponse,
        );
    }

    /**
     * Kills a function that is currently executing.
     * `FUNCTION KILL` terminates read-only functions only.
     * `FUNCTION KILL` runs on all nodes of the server, including primary and replicas.
     *
     * @see {@link https://valkey.io/commands/function-kill/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @returns `"OK"` if function is terminated. Otherwise, throws an error.
     * @example
     * ```typescript
     * await client.functionKill();
     * ```
     */
    public async functionKill(): Promise<"OK"> {
        return this.createWritePromise(createFunctionKill(), {
            decoder: Decoder.String,
        });
    }

    /**
     * Returns the serialized payload of all loaded libraries.
     *
     * @see {@link https://valkey.io/commands/function-dump/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @returns The serialized payload of all loaded libraries.
     *
     * @example
     * ```typescript
     * const data = await client.functionDump();
     * // data can be used to restore loaded functions on any Valkey instance
     * ```
     */
    public async functionDump(): Promise<Buffer> {
        return this.createWritePromise(createFunctionDump(), {
            decoder: Decoder.Bytes,
        });
    }

    /**
     * Restores libraries from the serialized payload returned by {@link functionDump}.
     *
     * @see {@link https://valkey.io/commands/function-restore/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param payload - The serialized data from {@link functionDump}.
     * @param policy - (Optional) A policy for handling existing libraries, see {@link FunctionRestorePolicy}.
     *     {@link FunctionRestorePolicy.APPEND} is used by default.
     * @returns `"OK"`.
     *
     * @example
     * ```typescript
     * await client.functionRestore(data, FunctionRestorePolicy.FLUSH);
     * ```
     */
    public async functionRestore(
        payload: Buffer,
        policy?: FunctionRestorePolicy,
    ): Promise<"OK"> {
        return this.createWritePromise(createFunctionRestore(payload, policy), {
            decoder: Decoder.String,
        });
    }

    /**
     * Deletes all the keys of all the existing databases. This command never fails.
     *
     * @see {@link https://valkey.io/commands/flushall/|valkey.io} for more details.
     *
     * @param mode - (Optional) The flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     * @returns `"OK"`.
     *
     * @example
     * ```typescript
     * const result = await client.flushall(FlushMode.SYNC);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async flushall(mode?: FlushMode): Promise<"OK"> {
        return this.createWritePromise(createFlushAll(mode), {
            decoder: Decoder.String,
        });
    }

    /**
     * Deletes all the keys of the currently selected database. This command never fails.
     *
     * @see {@link https://valkey.io/commands/flushdb/|valkey.io} for more details.
     *
     * @param mode - (Optional) The flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     * @returns `"OK"`.
     *
     * @example
     * ```typescript
     * const result = await client.flushdb(FlushMode.SYNC);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async flushdb(mode?: FlushMode): Promise<"OK"> {
        return this.createWritePromise(createFlushDB(mode), {
            decoder: Decoder.String,
        });
    }

    /**
     * Returns the number of keys in the currently selected database.
     *
     * @see {@link https://valkey.io/commands/dbsize/|valkey.io} for more details.
     *
     * @returns The number of keys in the currently selected database.
     *
     * @example
     * ```typescript
     * const numKeys = await client.dbsize();
     * console.log("Number of keys in the current database: ", numKeys);
     * ```
     */
    public async dbsize(): Promise<number> {
        return this.createWritePromise(createDBSize());
    }

    /** Publish a message on pubsub channel.
     *
     * @see {@link https://valkey.io/commands/publish/|valkey.io} for more details.
     *
     * @param message - Message to publish.
     * @param channel - Channel to publish the message on.
     * @returns -  Number of subscriptions in primary node that received the message.
     * Note that this value does not include subscriptions that configured on replicas.
     *
     * @example
     * ```typescript
     * // Example usage of publish command
     * const result = await client.publish("Hi all!", "global-channel");
     * console.log(result); // Output: 1 - This message was posted to 1 subscription which is configured on primary node
     * ```
     */
    public async publish(
        message: GlideString,
        channel: GlideString,
    ): Promise<number> {
        return this.createWritePromise(createPublish(message, channel));
    }

    /**
     * Returns `UNIX TIME` of the last DB save timestamp or startup timestamp if no save
     * was made since then.
     *
     * @see {@link https://valkey.io/commands/lastsave/|valkey.io} for more details.
     *
     * @returns `UNIX TIME` of the last DB save executed with success.
     *
     * @example
     * ```typescript
     * const timestamp = await client.lastsave();
     * console.log("Last DB save was done at " + timestamp);
     * ```
     */
    public async lastsave(): Promise<number> {
        return this.createWritePromise(createLastSave());
    }

    /**
     * Returns the latency spike time series for the specified event.
     *
     * @see {@link https://valkey.io/commands/latency-history/|valkey.io} for details.
     *
     * @param event - The name of the latency event (e.g., `"command"`).
     * @returns An array of {@link LatencyEntry} for the event, or an empty array if the event doesn't exist.
     *
     * @example
     * ```typescript
     * const history = await client.latencyHistory("command");
     * for (const entry of history) {
     *     console.log(`Time: ${entry.time}, Latency: ${entry.latency}`);
     * }
     * ```
     */
    public async latencyHistory(event: GlideString): Promise<LatencyEntry[]> {
        return this.createWritePromise<unknown[]>(
            createLatencyHistory(event),
        ).then((res) => parseLatencyHistoryResponse(res));
    }

    /**
     * Reports the latest latency events logged by the server.
     *
     * @see {@link https://valkey.io/commands/latency-latest/|valkey.io} for details.
     *
     * @returns An array of {@link LatencyEventInfo} for the latest latency events.
     *
     * @example
     * ```typescript
     * const latest = await client.latencyLatest();
     * for (const info of latest) {
     *     console.log(`Event: ${info.eventName}, Latest: ${info.latestDuration}`);
     * }
     * ```
     */
    public async latencyLatest(): Promise<LatencyEventInfo[]> {
        return this.createWritePromise<unknown[]>(createLatencyLatest()).then(
            (res) => parseLatencyLatestResponse(res),
        );
    }

    /**
     * Resets the latency spike time series for all or specified events.
     * If no events are provided, resets the latency spike time series for all events.
     *
     * @see {@link https://valkey.io/commands/latency-reset/|valkey.io} for details.
     *
     * @param events - The event names to reset. If not provided, resets all events.
     * @returns The number of event time series that were reset.
     *
     * @example
     * ```typescript
     * await client.latencyReset(); // Resets all events
     * await client.latencyReset(["command"]); // Resets only "command"
     * ```
     */
    public async latencyReset(events?: GlideString[]): Promise<number> {
        return this.createWritePromise(createLatencyReset(events));
    }

    /**
     * Synchronously saves the dataset to disk.
     *
     * @see {@link https://valkey.io/commands/save/|valkey.io} for more details.
     *
     * @returns `"OK"`
     *
     * @example
     * ```typescript
     * const result = await client.save();
     * console.log(result); // "OK"
     * ```
     */
    public async save(): Promise<"OK"> {
        return this.createWritePromise(createSave(), {
            decoder: Decoder.String,
        });
    }

    /**
     * Asynchronously saves the dataset to disk in the background.
     *
     * @see {@link https://valkey.io/commands/bgsave/|valkey.io} for more details.
     *
     * @returns A non-empty status string.
     *
     * @example
     * ```typescript
     * const result = await client.bgsave();
     * console.log(result); // "Background saving started"
     * ```
     */
    public async bgsave(): Promise<string> {
        return this.createWritePromise(createBgSave(), {
            decoder: Decoder.String,
        });
    }

    /**
     * Schedules a background save of the database.
     *
     * @see {@link https://valkey.io/commands/bgsave/|valkey.io} for more details.
     *
     * @returns A non-empty status string.
     *
     * @example
     * ```typescript
     * const result = await client.bgsaveSchedule();
     * console.log(result); // "Background saving scheduled"
     * ```
     */
    public async bgsaveSchedule(): Promise<string> {
        return this.createWritePromise(createBgSave(["SCHEDULE"]), {
            decoder: Decoder.String,
        });
    }

    /**
     * Aborts all in-progress and scheduled background saves.
     *
     * @see {@link https://valkey.io/commands/bgsave/|valkey.io} for more details.
     *
     * @since Valkey 8.1
     *
     * @returns A non-empty status string.
     *
     * @example
     * ```typescript
     * const result = await client.bgsaveCancel();
     * console.log(result); // "Background saving cancelled"
     * ```
     */
    public async bgsaveCancel(): Promise<string> {
        return this.createWritePromise(createBgSave(["CANCEL"]), {
            decoder: Decoder.String,
        });
    }

    /**
     * Initiates a background rewrite of the append-only file (AOF).
     *
     * @see {@link https://valkey.io/commands/bgrewriteaof/|valkey.io} for more details.
     *
     * @returns A non-empty status string.
     *
     * @example
     * ```typescript
     * const result = await client.bgrewriteaof();
     * console.log(result); // "Background append only file rewriting started"
     * ```
     */
    public async bgrewriteaof(): Promise<string> {
        return this.createWritePromise(createBgRewriteAof(), {
            decoder: Decoder.String,
        });
    }

    /**
     * Returns a random existing key name from the currently selected database.
     *
     * @see {@link https://valkey.io/commands/randomkey/|valkey.io} for more details.
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns A random existing key name from the currently selected database.
     *
     * @example
     * ```typescript
     * const result = await client.randomKey();
     * console.log(result); // Output: "key12" - "key12" is a random existing key name from the currently selected database.
     * ```
     */
    public async randomKey(
        options?: DecoderOption,
    ): Promise<GlideString | null> {
        return this.createWritePromise(createRandomKey(), options);
    }

    /**
     * Atomically transfers a key or multiple keys from the current Valkey instance
     * to a destination Valkey instance.
     * On success, keys are deleted from the source unless `copy` is set to `true` in options.
     *
     * @see {@link https://valkey.io/commands/migrate/|valkey.io} for details.
     *
     * @param host - The host of the destination Valkey instance.
     * @param port - The port of the destination Valkey instance.
     * @param key - The key to migrate, or an array of keys to migrate.
     * @param destinationDB - The database index on the destination instance.
     * @param timeout - The maximum idle time in milliseconds for the bulk-transfer.
     * @param options - Optional migration options.
     * @returns `"OK"` on success, `"NOKEY"` if the key(s) do not exist.
     *
     * @example Single-key:
     * ```typescript
     * const result = await client.migrate("127.0.0.1", 6379, "mykey", 0, 5000);
     * console.log(result); // "OK"
     * ```
     * @example Single-key with copy and replace:
     * ```typescript
     * const result = await client.migrate("127.0.0.1", 6379, "mykey", 0, 5000, { copy: true, replace: true });
     * console.log(result); // "OK"
     * ```
     * @example Multi-key:
     * ```typescript
     * const result = await client.migrate("127.0.0.1", 6379, ["key1", "key2"], 0, 5000);
     * console.log(result); // "OK"
     * ```
     */
    public async migrate(
        host: string,
        port: number,
        key: GlideString | GlideString[],
        destinationDB: number,
        timeout: number,
        options?: MigrateOptions,
    ): Promise<string> {
        return this.createWritePromise(
            createMigrate(host, port, key, destinationDB, timeout, options),
        );
    }

    /**
     * Flushes all the previously watched keys for a transaction. Executing a transaction will
     * automatically flush all previously watched keys.
     *
     * @see {@link https://valkey.io/commands/unwatch/|valkey.io} and {@link https://valkey.io/topics/transactions/#cas|Valkey GLIDE Documentation} for more details.
     *
     * @returns A simple `"OK"` response.
     *
     * @example
     * ```typescript
     * let response = await client.unwatch();
     * console.log(response); // Output: "OK"
     * ```
     */
    public async unwatch(): Promise<"OK"> {
        return this.createWritePromise(createUnWatch(), {
            decoder: Decoder.String,
        });
    }

    /**
     * Checks existence of scripts in the script cache by their SHA1 digest.
     *
     * @see {@link https://valkey.io/commands/script-exists/|valkey.io} for more details.
     *
     * @param sha1s - List of SHA1 digests of the scripts to check.
     * @returns A list of boolean values indicating the existence of each script.
     *
     * @example
     * ```typescript
     * console result = await client.scriptExists(["sha1_digest1", "sha1_digest2"]);
     * console.log(result); // Output: [true, false]
     * ```
     */
    public async scriptExists(sha1s: GlideString[]): Promise<boolean[]> {
        return this.createWritePromise(createScriptExists(sha1s));
    }

    /**
     * Flushes the Lua scripts cache.
     *
     * @see {@link https://valkey.io/commands/script-flush/|valkey.io} for more details.
     *
     * @param mode - (Optional) The flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     * @returns A simple `"OK"` response.
     *
     * @example
     * ```typescript
     * console result = await client.scriptFlush(FlushMode.SYNC);
     * console.log(result); // Output: "OK"
     * ```
     */
    public async scriptFlush(mode?: FlushMode): Promise<"OK"> {
        return this.createWritePromise(createScriptFlush(mode), {
            decoder: Decoder.String,
        });
    }

    /**
     * Kills the currently executing Lua script, assuming no write operation was yet performed by the script.
     *
     * @see {@link https://valkey.io/commands/script-kill/|valkey.io} for more details.
     *
     * @returns A simple `"OK"` response.
     *
     * @example
     * ```typescript
     * console result = await client.scriptKill();
     * console.log(result); // Output: "OK"
     * ```
     */
    public async scriptKill(): Promise<"OK"> {
        return this.createWritePromise(createScriptKill(), {
            decoder: Decoder.String,
        });
    }

    /**
     * Incrementally iterate over a collection of keys.
     * `SCAN` is a cursor based iterator. This means that at every call of the method,
     * the server returns an updated cursor that the user needs to use as the cursor argument in the next call.
     * An iteration starts when the cursor is set to "0", and terminates when the cursor returned by the server is "0".
     *
     * A full iteration always retrieves all the elements that were present
     * in the collection from the start to the end of a full iteration.
     * Elements that were not constantly present in the collection during a full iteration, may be returned or not.
     *
     * @see {@link https://valkey.io/commands/scan|valkey.io} for more details.
     *
     * @param cursor - The `cursor` used for iteration. For the first iteration, the cursor should be set to "0".
     * Using a non-zero cursor in the first iteration,
     * or an invalid cursor at any iteration, will lead to undefined results.
     * Using the same cursor in multiple iterations will, in case nothing changed between the iterations,
     * return the same elements multiple times.
     * If the the db has changed, it may result in undefined behavior.
     * @param options - (Optional) The options to use for the scan operation, see {@link ScanOptions} and {@link DecoderOption}.
     * @returns A List containing the next cursor value and a list of keys,
     * formatted as [cursor, [key1, key2, ...]]
     *
     * @example
     * ```typescript
     * // Example usage of scan method
     * let result = await client.scan('0');
     * console.log(result); // Output: ['17', ['key1', 'key2', 'key3', 'key4', 'key5', 'set1', 'set2', 'set3']]
     * let firstCursorResult = result[0];
     * result = await client.scan(firstCursorResult);
     * console.log(result); // Output: ['349', ['key4', 'key5', 'set1', 'hash1', 'zset1', 'list1', 'list2',
     * // 'list3', 'zset2', 'zset3', 'zset4', 'zset5', 'zset6']]
     * result = await client.scan(result[0]);
     * console.log(result); // Output: ['0', ['key6', 'key7']]
     *
     * result = await client.scan(firstCursorResult, {match: 'key*', count: 2});
     * console.log(result); // Output: ['6', ['key4', 'key5']]
     *
     * result = await client.scan("0", {type: ObjectType.Set});
     * console.log(result); // Output: ['362', ['set1', 'set2', 'set3']]
     * ```
     */
    public async scan(
        cursor: GlideString,
        options?: ScanOptions & DecoderOption,
    ): Promise<[GlideString, GlideString[]]> {
        return this.createWritePromise(createScan(cursor, options), options);
    }

    /**
     * Returns the current subscription state for the client.
     *
     * @see {@link https://valkey.io/commands/pubsub/|valkey.io} for details.
     *
     * @returns A promise that resolves to the subscription state containing
     *          desired and actual subscriptions organized by channel mode.
     *
     * @example
     * ```typescript
     * const state = await client.getSubscriptions();
     * console.log("Desired exact channels:", state.desiredSubscriptions[GlideClientConfiguration.PubSubChannelModes.Exact]);
     * console.log("Actual exact channels:", state.actualSubscriptions[GlideClientConfiguration.PubSubChannelModes.Exact]);
     * ```
     */
    public async getSubscriptions(): Promise<StandalonePubSubState> {
        const response = await this.createWritePromise<unknown[]>(
            createGetSubscriptions(),
        );
        return this.parseGetSubscriptionsResponse<GlideClientConfiguration.PubSubChannelModes>(
            response,
        );
    }

    /**
     * Suspends all clients for the specified timeout.
     *
     * @see {@link https://valkey.io/commands/client-pause/|valkey.io} for details.
     *
     * @param timeout - The time in milliseconds to pause clients.
     * @param mode - (Optional) The pause mode to use.
     *   + If not provided, all commands are paused.
     * @param options - (Optional) See {@link DecoderOption}.
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
        options?: DecoderOption,
    ): Promise<"OK"> {
        return this.createWritePromise(
            createClientPause(timeout, mode),
            options,
        );
    }

    /**
     * Resumes processing commands on all clients.
     *
     * @see {@link https://valkey.io/commands/client-unpause/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link DecoderOption}.
     * @returns `"OK"` response on success.
     *
     * @example
     * ```typescript
     * const result = await client.clientUnpause();
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async clientUnpause(options?: DecoderOption): Promise<"OK"> {
        return this.createWritePromise(createClientUnpause(), options);
    }

    /**
     * Starts a coordinated failover from the connected primary to one of its replicas.
     * This is the standalone equivalent of `CLUSTER FAILOVER`.
     *
     * @see {@link https://valkey.io/commands/failover/|valkey.io} for details.
     *
     * @param options - (Optional) Failover options. See {@link FailoverOptions}.
     * @returns `"OK"` if the failover was successfully initiated.
     *
     * @example
     * ```typescript
     * const result = await client.failover();
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async failover(options?: FailoverOptions): Promise<"OK"> {
        return this.createWritePromise(createFailover(options), {
            decoder: Decoder.String,
        });
    }

    /**
     * Makes the server a replica of the specified primary.
     *
     * @see {@link https://valkey.io/commands/replicaof/|valkey.io} for details.
     *
     * @param host - The host of the primary to replicate.
     * @param port - The port of the primary to replicate.
     * @returns `"OK"` on success.
     *
     * @example
     * ```typescript
     * const result = await client.replicaof("localhost", 6379);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async replicaof(host: string, port: number): Promise<"OK"> {
        return this.createWritePromise(createReplicaOf(host, port), {
            decoder: Decoder.String,
        });
    }

    /**
     * Promotes the current server to a primary by stopping replication.
     *
     * @see {@link https://valkey.io/commands/replicaof/|valkey.io} for details.
     *
     * @returns `"OK"` on success.
     *
     * @example
     * ```typescript
     * const result = await client.replicaofNoOne();
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async replicaofNoOne(): Promise<"OK"> {
        return this.createWritePromise(createReplicaOfNoOne(), {
            decoder: Decoder.String,
        });
    }

    // TODO #6166: move to shared base

    /**
     * Returns a report about memory problems detected by the server.
     *
     * @see {@link https://valkey.io/commands/memory-doctor/|valkey.io} for details.
     *
     * @returns The memory diagnostic report.
     *
     * @example
     * ```typescript
     * const report = await client.memoryDoctor();
     * console.log(report); // Output: "Sam, I have no memory problems"
     * ```
     */
    public async memoryDoctor(): Promise<string> {
        return this.createWritePromise(createMemoryDoctor(), {
            decoder: Decoder.String,
        });
    }

    /**
     * Returns the internal statistics of the memory allocator.
     *
     * @see {@link https://valkey.io/commands/memory-malloc-stats/|valkey.io} for details.
     *
     * @returns The memory allocator statistics.
     *
     * @example
     * ```typescript
     * const stats = await client.memoryMallocStats();
     * console.log(stats);
     * ```
     */
    public async memoryMallocStats(): Promise<string> {
        return this.createWritePromise(createMemoryMallocStats(), {
            decoder: Decoder.String,
        });
    }

    /**
     * Asks the server to reclaim memory from the allocator back to the operating system.
     *
     * @see {@link https://valkey.io/commands/memory-purge/|valkey.io} for details.
     *
     * @returns `"OK"`.
     *
     * @example
     * ```typescript
     * const result = await client.memoryPurge();
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async memoryPurge(): Promise<"OK"> {
        return this.createWritePromise(createMemoryPurge(), {
            decoder: Decoder.String,
        });
    }

    /**
     * Returns detailed memory consumption statistics of the server.
     *
     * @see {@link https://valkey.io/commands/memory-stats/|valkey.io} for details.
     *
     * @returns A {@link MemoryStats} object containing detailed memory usage statistics.
     *
     * @example
     * ```typescript
     * const stats = await client.memoryStats();
     * console.log(stats.peakAllocated); // Peak memory in bytes
     * console.log(stats.totalAllocated); // Total allocated memory in bytes
     * ```
     */
    public async memoryStats(): Promise<MemoryStats> {
        return this.createWritePromise<GlideRecord<unknown>>(
            createMemoryStats(),
        ).then((res) =>
            parseMemoryStatsResponse(
                convertGlideRecordToRecord(res) as Record<string, unknown>,
            ),
        );
    }
}
