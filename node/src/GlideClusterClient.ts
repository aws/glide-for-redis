/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import * as net from "net";
import {
    BaseClient,
    BaseClientConfiguration,
    Decoder,
    PubSubMsg,
    ReadFrom, // eslint-disable-line @typescript-eslint/no-unused-vars
    ReturnType,
} from "./BaseClient";
import {
    FlushMode,
    FunctionListOptions,
    FunctionListResponse,
    InfoOptions,
    LolwutOptions,
    SortClusterOptions,
    createClientGetName,
    createClientId,
    createConfigGet,
    createConfigResetStat,
    createConfigRewrite,
    createConfigSet,
    createCopy,
    createCustomCommand,
    createDBSize,
    createEcho,
    createFCall,
    createFCallReadOnly,
    createFlushAll,
    createFlushDB,
    createFunctionDelete,
    createFunctionFlush,
    createFunctionList,
    createFunctionLoad,
    createInfo,
    createLastSave,
    createLolwut,
    createPing,
    createPubSubShardNumSub,
    createPublish,
    createPubsubShardChannels,
    createRandomKey,
    createSort,
    createSortReadOnly,
    createTime,
    createUnWatch,
} from "./Commands";
import { RequestError } from "./Errors";
import { command_request, connection_request } from "./ProtobufMessage";
import { ClusterTransaction } from "./Transaction";

/**
 * Represents a manually configured interval for periodic checks.
 */
export type PeriodicChecksManualInterval = {
    /**
     * The duration in seconds for the interval between periodic checks.
     */
    duration_in_sec: number;
};

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
     * See [Valkey PubSub Documentation](https://valkey.io/docs/topics/pubsub/) for more details.
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

    export type PubSubSubscriptions = {
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
    };
}
export type GlideClusterClientConfiguration = BaseClientConfiguration & {
    /**
     * Configure the periodic topology checks.
     * These checks evaluate changes in the cluster's topology, triggering a slot refresh when detected.
     * Periodic checks ensure a quick and efficient process by querying a limited number of nodes.
     * If not set, `enabledDefaultConfigs` will be used.
     */
    periodicChecks?: PeriodicChecks;

    /**
     * PubSub subscriptions to be used for the client.
     * Will be applied via SUBSCRIBE/PSUBSCRIBE/SSUBSCRIBE commands during connection establishment.
     */
    pubsubSubscriptions?: GlideClusterClientConfiguration.PubSubSubscriptions;
};

/**
 * If the command's routing is to one node we will get T as a response type,
 * otherwise, we will get a dictionary of address: nodeResponse, address is of type string and nodeResponse is of type T.
 */
export type ClusterResponse<T> = T | Record<string, T>;

export type SlotIdTypes = {
    /**
     * `replicaSlotId` overrides the `readFrom` configuration. If it's used the request
     * will be routed to a replica, even if the strategy is `alwaysFromPrimary`.
     */
    type: "primarySlotId" | "replicaSlotId";
    /**
     * Slot number. There are 16384 slots in a redis cluster, and each shard manages a slot range.
     * Unless the slot is known, it's better to route using `SlotKeyTypes`
     */
    id: number;
};

export type SlotKeyTypes = {
    /**
     * `replicaSlotKey` overrides the `readFrom` configuration. If it's used the request
     * will be routed to a replica, even if the strategy is `alwaysFromPrimary`.
     */
    type: "primarySlotKey" | "replicaSlotKey";
    /**
     * The request will be sent to nodes managing this key.
     */
    key: string;
};

/// Route command to specific node.
export type RouteByAddress = {
    type: "routeByAddress";
    /**
     *The endpoint of the node. If `port` is not provided, should be in the `${address}:${port}` format, where `address` is the preferred endpoint as shown in the output of the `CLUSTER SLOTS` command.
     */
    host: string;
    /**
     * The port to access on the node. If port is not provided, `host` is assumed to be in the format `${address}:${port}`.
     */
    port?: number;
};

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

function toProtobufRoute(
    route: Routes | undefined,
): command_request.Routes | undefined {
    if (route === undefined) {
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

/**
 * Client used for connection to cluster Redis servers.
 * For full documentation, see
 * https://github.com/valkey-io/valkey-glide/wiki/NodeJS-wrapper#cluster
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
        return configuration;
    }

    public static async createClient(
        options: GlideClusterClientConfiguration,
    ): Promise<GlideClusterClient> {
        return await super.createClientInternal(
            options,
            (socket: net.Socket, options?: GlideClusterClientConfiguration) =>
                new GlideClusterClient(socket, options),
        );
    }

    static async __createClient(
        options: BaseClientConfiguration,
        connectedSocket: net.Socket,
    ): Promise<GlideClusterClient> {
        return super.__createClientInternal(
            options,
            connectedSocket,
            (socket, options) => new GlideClusterClient(socket, options),
        );
    }

    /** Executes a single command, without checking inputs. Every part of the command, including subcommands,
     *  should be added as a separate value in args.
     *  The command will be routed automatically based on the passed command's default request policy, unless `route` is provided,
     *  in which case the client will route the command to the nodes defined by `route`.
     *
     * See the [Glide for Redis Wiki](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command)
     * for details on the restrictions and limitations of the custom command API.
     *
     * @example
     * ```typescript
     * // Example usage of customCommand method to retrieve pub/sub clients with routing to all primary nodes
     * const result = await client.customCommand(["CLIENT", "LIST", "TYPE", "PUBSUB"], "allPrimaries");
     * console.log(result); // Output: Returns a list of all pub/sub clients
     * ```
     */
    public customCommand(args: string[], route?: Routes): Promise<ReturnType> {
        const command = createCustomCommand(args);
        return super.createWritePromise(
            command,
            undefined, // This will trigger the default value for decode
            toProtobufRoute(route),
        );
    }

    /** Execute a transaction by processing the queued commands.
     *   See https://redis.io/topics/Transactions/ for details on Redis Transactions.
     *
     * @param transaction - A ClusterTransaction object containing a list of commands to be executed.
     * @param route - If `route` is not provided, the transaction will be routed to the slot owner of the first key found in the transaction.
     *   If no key is found, the command will be sent to a random node.
     *   If `route` is provided, the client will route the command to the nodes defined by `route`.
     * @returns A list of results corresponding to the execution of each command in the transaction.
     *      If a command returns a value, it will be included in the list. If a command doesn't return a value,
     *      the list entry will be null.
     *      If the transaction failed due to a WATCH command, `exec` will return `null`.
     */
    public exec(
        transaction: ClusterTransaction,
        route?: SingleNodeRoute,
    ): Promise<ReturnType[] | null> {
        return this.createWritePromise<ReturnType[] | null>(
            transaction.commands,
            undefined, // This will trigger the default value for decode
            toProtobufRoute(route),
        ).then((result: ReturnType[] | null) => {
            return this.processResultWithSetCommands(
                result,
                transaction.setCommandsIndexes,
            );
        });
    }

    /** Ping the Redis server.
     * See https://valkey.io/commands/ping/ for details.
     *
     * @param message - An optional message to include in the PING command.
     * If not provided, the server will respond with "PONG".
     * If provided, the server will respond with a copy of the message.
     * @param route - The command will be routed to all primaries, unless `route` is provided, in which
     *   case the client will route the command to the nodes defined by `route`.
     * @returns - "PONG" if `message` is not provided, otherwise return a copy of `message`.
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
    public ping(message?: string, route?: Routes): Promise<string> {
        return this.createWritePromise(
            createPing(message),
            undefined, // This will trigger the default value for decode
            toProtobufRoute(route),
        );
    }

    /** Get information and statistics about the Redis server.
     *  See https://valkey.io/commands/info/ for details.
     *
     * @param options - A list of InfoSection values specifying which sections of information to retrieve.
     *  When no parameter is provided, the default option is assumed.
     * @param route - The command will be routed to all primaries, unless `route` is provided, in which
     *   case the client will route the command to the nodes defined by `route`.
     * @returns a string containing the information for the sections requested. When specifying a route other than a single node,
     * it returns a dictionary where each address is the key and its corresponding node response is the value.
     */
    public info(
        options?: InfoOptions[],
        route?: Routes,
    ): Promise<ClusterResponse<string>> {
        return this.createWritePromise<ClusterResponse<string>>(
            createInfo(options),
            undefined, // This will trigger the default value for decode
            toProtobufRoute(route),
        );
    }

    /** Get the name of the connection to which the request is routed.
     *  See https://valkey.io/commands/client-getname/ for more details.
     *
     * @param route - The command will be routed a random node, unless `route` is provided, in which
     *   case the client will route the command to the nodes defined by `route`.
     *
     * @returns - the name of the client connection as a string if a name is set, or null if no name is assigned.
     * When specifying a route other than a single node, it returns a dictionary where each address is the key and
     * its corresponding node response is the value.
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
    public clientGetName(
        route?: Routes,
    ): Promise<ClusterResponse<string | null>> {
        return this.createWritePromise<ClusterResponse<string | null>>(
            createClientGetName(),
            undefined, // This will trigger the default value for decode
            toProtobufRoute(route),
        );
    }

    /** Rewrite the configuration file with the current configuration.
     * See https://valkey.io/commands/config-rewrite/ for details.
     *
     * @param route - The command will be routed to all nodes, unless `route` is provided, in which
     *   case the client will route the command to the nodes defined by `route`.
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
    public configRewrite(route?: Routes): Promise<"OK"> {
        return this.createWritePromise(
            createConfigRewrite(),
            undefined, // This will trigger the default value for decode
            toProtobufRoute(route),
        );
    }

    /** Resets the statistics reported by Redis using the INFO and LATENCY HISTOGRAM commands.
     * See https://valkey.io/commands/config-resetstat/ for details.
     *
     * @param route - The command will be routed to all nodes, unless `route` is provided, in which
     *   case the client will route the command to the nodes defined by `route`.
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
    public configResetStat(route?: Routes): Promise<"OK"> {
        return this.createWritePromise(
            createConfigResetStat(),
            undefined, // This will trigger the default value for decode
            toProtobufRoute(route),
        );
    }

    /** Returns the current connection id.
     * See https://valkey.io/commands/client-id/ for details.
     *
     * @param route - The command will be routed to a random node, unless `route` is provided, in which
     *   case the client will route the command to the nodes defined by `route`.
     * @returns the id of the client. When specifying a route other than a single node,
     * it returns a dictionary where each address is the key and its corresponding node response is the value.
     */
    public clientId(route?: Routes): Promise<ClusterResponse<number>> {
        return this.createWritePromise<ClusterResponse<number>>(
            createClientId(),
            undefined, // This will trigger the default value for decode
            toProtobufRoute(route),
        );
    }

    /** Reads the configuration parameters of a running Redis server.
     *  See https://valkey.io/commands/config-get/ for details.
     *
     * @param parameters - A list of configuration parameter names to retrieve values for.
     * @param route - The command will be routed to a random node, unless `route` is provided, in which
     *  case the client will route the command to the nodes defined by `route`.
     *  If `route` is not provided, the command will be sent to a random node.
     *
     * @returns A map of values corresponding to the configuration parameters. When specifying a route other than a single node,
     *  it returns a dictionary where each address is the key and its corresponding node response is the value.
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
    public configGet(
        parameters: string[],
        route?: Routes,
    ): Promise<ClusterResponse<Record<string, string>>> {
        return this.createWritePromise<ClusterResponse<Record<string, string>>>(
            createConfigGet(parameters),
            undefined, // This will trigger the default value for decode
            toProtobufRoute(route),
        );
    }

    /** Set configuration parameters to the specified values.
     *   See https://valkey.io/commands/config-set/ for details.
     *
     * @param parameters - A List of keyValuePairs consisting of configuration parameters and their respective values to set.
     * @param route - The command will be routed to all nodes, unless `route` is provided, in which
     *   case the client will route the command to the nodes defined by `route`.
     *   If `route` is not provided, the command will be sent to the all nodes.
     *
     * @returns "OK" when the configuration was set properly. Otherwise an error is thrown.
     *
     * @example
     * ```typescript
     * // Example usage of configSet method to set multiple configuration parameters
     * const result = await client.configSet({ timeout: "1000", maxmemory, "1GB" });
     * console.log(result); // Output: 'OK'
     * ```
     */
    public configSet(
        parameters: Record<string, string>,
        route?: Routes,
    ): Promise<"OK"> {
        return this.createWritePromise(
            createConfigSet(parameters),
            undefined, // This will trigger the default value for decode
            toProtobufRoute(route),
        );
    }

    /** Echoes the provided `message` back.
     * See https://valkey.io/commands/echo for more details.
     *
     * @param message - The message to be echoed back.
     * @param route - The command will be routed to a random node, unless `route` is provided, in which
     *  case the client will route the command to the nodes defined by `route`.
     * @returns The provided `message`. When specifying a route other than a single node,
     *  it returns a dictionary where each address is the key and its corresponding node response is the value.
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
    public echo(
        message: string,
        route?: Routes,
    ): Promise<ClusterResponse<string>> {
        return this.createWritePromise(
            createEcho(message),
            undefined, // This will trigger the default value for decode
            toProtobufRoute(route),
        );
    }

    /** Returns the server time.
     * See https://valkey.io/commands/time/ for details.
     *
     * @param route - The command will be routed to a random node, unless `route` is provided, in which
     *  case the client will route the command to the nodes defined by `route`.
     *
     * @returns - The current server time as a two items `array`:
     * A Unix timestamp and the amount of microseconds already elapsed in the current second.
     * The returned `array` is in a [Unix timestamp, Microseconds already elapsed] format.
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
    public time(route?: Routes): Promise<ClusterResponse<[string, string]>> {
        return this.createWritePromise(
            createTime(),
            undefined, // This will trigger the default value for decode
            toProtobufRoute(route),
        );
    }

    /**
     * Copies the value stored at the `source` to the `destination` key. When `replace` is `true`,
     * removes the `destination` key first if it already exists, otherwise performs no action.
     *
     * See https://valkey.io/commands/copy/ for more details.
     *
     * @remarks When in cluster mode, `source` and `destination` must map to the same hash slot.
     * @param source - The key to the source value.
     * @param destination - The key where the value should be copied to.
     * @param replace - (Optional) If `true`, the `destination` key should be removed before copying the
     *     value to it. If not provided, no action will be performed if the key already exists.
     * @returns `true` if `source` was copied, `false` if the `source` was not copied.
     *
     * since Valkey version 6.2.0.
     *
     * @example
     * ```typescript
     * const result = await client.copy("set1", "set2", true);
     * console.log(result); // Output: true - "set1" was copied to "set2".
     * ```
     */
    public async copy(
        source: string,
        destination: string,
        replace?: boolean,
    ): Promise<boolean> {
        return this.createWritePromise(
            createCopy(source, destination, { replace: replace }),
        );
    }

    /**
     * Displays a piece of generative computer art and the server version.
     *
     * See https://valkey.io/commands/lolwut/ for more details.
     *
     * @param options - The LOLWUT options.
     * @param route - The command will be routed to a random node, unless `route` is provided, in which
     *  case the client will route the command to the nodes defined by `route`.
     * @returns A piece of generative computer art along with the current server version.
     *
     * @example
     * ```typescript
     * const response = await client.lolwut({ version: 6, parameters: [40, 20] }, "allNodes");
     * console.log(response); // Output: "Redis ver. 7.2.3" - Indicates the current server version.
     * ```
     */
    public lolwut(
        options?: LolwutOptions,
        route?: Routes,
        decoder?: Decoder,
    ): Promise<ClusterResponse<string>> {
        return this.createWritePromise(
            createLolwut(options),
            decoder,
            toProtobufRoute(route),
        );
    }

    /**
     * Invokes a previously loaded function.
     *
     * See https://valkey.io/commands/fcall/ for more details.
     *
     * since Valkey version 7.0.0.
     *
     * @param func - The function name.
     * @param args - A list of `function` arguments and it should not represent names of keys.
     * @param route - The command will be routed to a random node, unless `route` is provided, in which
     *     case the client will route the command to the nodes defined by `route`.
     * @returns The invoked function's return value.
     *
     * @example
     * ```typescript
     * const response = await client.fcallWithRoute("Deep_Thought", [], "randomNode");
     * console.log(response); // Output: Returns the function's return value.
     * ```
     */
    public fcallWithRoute(
        func: string,
        args: string[],
        route?: Routes,
    ): Promise<ReturnType> {
        return this.createWritePromise(
            createFCall(func, [], args),
            toProtobufRoute(route),
        );
    }

    /**
     * Invokes a previously loaded read-only function.
     *
     * See https://valkey.io/commands/fcall/ for more details.
     *
     * since Valkey version 7.0.0.
     *
     * @param func - The function name.
     * @param args - A list of `function` arguments and it should not represent names of keys.
     * @param route - The command will be routed to a random node, unless `route` is provided, in which
     *     case the client will route the command to the nodes defined by `route`.
     * @returns The invoked function's return value.
     *
     * @example
     * ```typescript
     * const response = await client.fcallReadonlyWithRoute("Deep_Thought", ["Answer", "to", "the", "Ultimate",
     *            "Question", "of", "Life,", "the", "Universe,", "and", "Everything"], "randomNode");
     * console.log(response); // Output: 42 # The return value on the function that was execute.
     * ```
     */
    public fcallReadonlyWithRoute(
        func: string,
        args: string[],
        route?: Routes,
    ): Promise<ReturnType> {
        return this.createWritePromise(
            createFCallReadOnly(func, [], args),
            toProtobufRoute(route),
        );
    }

    /**
     * Deletes a library and all its functions.
     *
     * See https://valkey.io/commands/function-delete/ for details.
     *
     * since Valkey version 7.0.0.
     *
     * @param libraryCode - The library name to delete.
     * @param route - The command will be routed to all primary node, unless `route` is provided, in which
     *     case the client will route the command to the nodes defined by `route`.
     * @returns A simple OK response.
     *
     * @example
     * ```typescript
     * const result = await client.functionDelete("libName");
     * console.log(result); // Output: 'OK'
     * ```
     */
    public functionDelete(
        libraryCode: string,
        route?: Routes,
    ): Promise<string> {
        return this.createWritePromise(
            createFunctionDelete(libraryCode),
            toProtobufRoute(route),
        );
    }

    /**
     * Loads a library to Valkey.
     *
     * See https://valkey.io/commands/function-load/ for details.
     *
     * since Valkey version 7.0.0.
     *
     * @param libraryCode - The source code that implements the library.
     * @param replace - Whether the given library should overwrite a library with the same name if it
     *     already exists.
     * @param route - The command will be routed to a random node, unless `route` is provided, in which
     *     case the client will route the command to the nodes defined by `route`.
     * @returns The library name that was loaded.
     *
     * @example
     * ```typescript
     * const code = "#!lua name=mylib \n redis.register_function('myfunc', function(keys, args) return args[1] end)";
     * const result = await client.functionLoad(code, true, 'allNodes');
     * console.log(result); // Output: 'mylib'
     * ```
     */
    public functionLoad(
        libraryCode: string,
        replace?: boolean,
        route?: Routes,
        decoder?: Decoder,
    ): Promise<string> {
        return this.createWritePromise(
            createFunctionLoad(libraryCode, replace),
            decoder,
            toProtobufRoute(route),
        );
    }

    /**
     * Deletes all function libraries.
     *
     * See https://valkey.io/commands/function-flush/ for details.
     *
     * since Valkey version 7.0.0.
     *
     * @param mode - The flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     * @param route - The command will be routed to all primary node, unless `route` is provided, in which
     *   case the client will route the command to the nodes defined by `route`.
     * @returns A simple OK response.
     *
     * @example
     * ```typescript
     * const result = await client.functionFlush(FlushMode.SYNC);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public functionFlush(mode?: FlushMode, route?: Routes, decoder?: Decoder): Promise<string> {
        return this.createWritePromise(
            createFunctionFlush(mode),
            decoder,
            toProtobufRoute(route),
        );
    }

    /**
     * Returns information about the functions and libraries.
     *
     * See https://valkey.io/commands/function-list/ for details.
     *
     * since Valkey version 7.0.0.
     *
     * @param options - Parameters to filter and request additional info.
     * @param route - The client will route the command to the nodes defined by `route`.
     *     If not defined, the command will be routed to a random route.
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
        options?: FunctionListOptions,
        route?: Routes,
    ): Promise<ClusterResponse<FunctionListResponse>> {
        return this.createWritePromise(
            createFunctionList(options),
            toProtobufRoute(route),
        );
    }

    /**
     * Deletes all the keys of all the existing databases. This command never fails.
     *
     * See https://valkey.io/commands/flushall/ for more details.
     *
     * @param mode - The flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     * @param route - The command will be routed to all primary nodes, unless `route` is provided, in which
     *     case the client will route the command to the nodes defined by `route`.
     * @returns `OK`.
     *
     * @example
     * ```typescript
     * const result = await client.flushall(FlushMode.SYNC);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public flushall(mode?: FlushMode, route?: Routes, decoder?: Decoder): Promise<string> {
        return this.createWritePromise(
            createFlushAll(mode),
            decoder,
            toProtobufRoute(route),
        );
    }

    /**
     * Deletes all the keys of the currently selected database. This command never fails.
     *
     * See https://valkey.io/commands/flushdb/ for more details.
     *
     * @param mode - The flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     * @param route - The command will be routed to all primary nodes, unless `route` is provided, in which
     *     case the client will route the command to the nodes defined by `route`.
     * @returns `OK`.
     *
     * @example
     * ```typescript
     * const result = await client.flushdb(FlushMode.SYNC);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public flushdb(mode?: FlushMode, route?: Routes, decoder?: Decoder): Promise<string> {
        return this.createWritePromise(
            createFlushDB(mode),
            decoder,
            toProtobufRoute(route),
        );
    }

    /**
     * Returns the number of keys in the database.
     *
     * See https://valkey.io/commands/dbsize/ for more details.

     * @param route - The command will be routed to all primary nodes, unless `route` is provided, in which
     *     case the client will route the command to the nodes defined by `route`.
     * @returns The number of keys in the database.
     *     In the case of routing the query to multiple nodes, returns the aggregated number of keys across the different nodes.
     *
     * @example
     * ```typescript
     * const numKeys = await client.dbsize("allPrimaries");
     * console.log("Number of keys across all primary nodes: ", numKeys);
     * ```
     */
    public dbsize(route?: Routes, decoder?: Decoder): Promise<ClusterResponse<number>> {
        return this.createWritePromise(createDBSize(), decoder, toProtobufRoute(route));
    }

    /** Publish a message on pubsub channel.
     * This command aggregates PUBLISH and SPUBLISH commands functionalities.
     * The mode is selected using the 'sharded' parameter.
     * For both sharded and non-sharded mode, request is routed using hashed channel as key.
     *
     * See https://valkey.io/commands/publish and https://valkey.io/commands/spublish for more details.
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
    public publish(
        message: string,
        channel: string,
        sharded: boolean = false,
    ): Promise<number> {
        return this.createWritePromise(
            createPublish(message, channel, sharded),
        );
    }

    /**
     * Lists the currently active shard channels.
     * The command is routed to all nodes, and aggregates the response to a single array.
     *
     * See https://valkey.io/commands/pubsub-shardchannels for more details.
     *
     * @param pattern - A glob-style pattern to match active shard channels.
     *                  If not provided, all active shard channels are returned.
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
    public async pubsubShardChannels(pattern?: string): Promise<string[]> {
        return this.createWritePromise(createPubsubShardChannels(pattern));
    }

    /**
     * Returns the number of subscribers (exclusive of clients subscribed to patterns) for the specified shard channels.
     *
     * Note that it is valid to call this command without channels. In this case, it will just return an empty map.
     * The command is routed to all nodes, and aggregates the response to a single map of the channels and their number of subscriptions.
     *
     * See https://valkey.io/commands/pubsub-shardnumsub for more details.
     *
     * @param channels - The list of shard channels to query for the number of subscribers.
     *                   If not provided, returns an empty map.
     * @returns A map where keys are the shard channel names and values are the number of subscribers.
     *
     * @example
     * ```typescript
     * const result1 = await client.pubsubShardnumsub(["channel1", "channel2"]);
     * console.log(result1); // Output: { "channel1": 3, "channel2": 5 }
     *
     * const result2 = await client.pubsubShardnumsub();
     * console.log(result2); // Output: {}
     * ```
     */
    public async pubsubShardNumSub(
        channels?: string[],
    ): Promise<Record<string, number>> {
        return this.createWritePromise(createPubSubShardNumSub(channels));
    }

    /**
     * Sorts the elements in the list, set, or sorted set at `key` and returns the result.
     *
     * The `sort` command can be used to sort elements based on different criteria and
     * apply transformations on sorted elements.
     *
     * To store the result into a new key, see {@link sortStore}.
     *
     * See https://valkey.io/commands/sort for more details.
     *
     * @param key - The key of the list, set, or sorted set to be sorted.
     * @param options - (Optional) {@link SortClusterOptions}.
     * @returns An `Array` of sorted elements.
     *
     * @example
     * ```typescript
     * await client.lpush("mylist", ["3", "1", "2", "a"]);
     * const result = await client.sort("mylist", { alpha: true, orderBy: SortOrder.DESC, limit: { offset: 0, count: 3 } });
     * console.log(result); // Output: [ 'a', '3', '2' ] - List is sorted in descending order lexicographically
     * ```
     */
    public async sort(
        key: string,
        options?: SortClusterOptions,
    ): Promise<string[]> {
        return this.createWritePromise(createSort(key, options));
    }

    /**
     * Sorts the elements in the list, set, or sorted set at `key` and returns the result.
     *
     * The `sortReadOnly` command can be used to sort elements based on different criteria and
     * apply transformations on sorted elements.
     *
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * since Valkey version 7.0.0.
     *
     * @param key - The key of the list, set, or sorted set to be sorted.
     * @param options - (Optional) {@link SortClusterOptions}.
     * @returns An `Array` of sorted elements
     *
     * @example
     * ```typescript
     * await client.lpush("mylist", ["3", "1", "2", "a"]);
     * const result = await client.sortReadOnly("mylist", { alpha: true, orderBy: SortOrder.DESC, limit: { offset: 0, count: 3 } });
     * console.log(result); // Output: [ 'a', '3', '2' ] - List is sorted in descending order lexicographically
     * ```
     */
    public async sortReadOnly(
        key: string,
        options?: SortClusterOptions,
    ): Promise<string[]> {
        return this.createWritePromise(createSortReadOnly(key, options));
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
     * See https://valkey.io/commands/sort for more details.
     *
     * @remarks When in cluster mode, `destination` and `key` must map to the same hash slot.
     * @param key - The key of the list, set, or sorted set to be sorted.
     * @param destination - The key where the sorted result will be stored.
     * @param options - (Optional) {@link SortClusterOptions}.
     * @returns The number of elements in the sorted key stored at `destination`.
     *
     * @example
     * ```typescript
     * await client.lpush("mylist", ["3", "1", "2", "a"]);
     * const sortedElements = await client.sortReadOnly("mylist", "sortedList", { alpha: true, orderBy: SortOrder.DESC, limit: { offset: 0, count: 3 } });
     * console.log(sortedElements); // Output: 3 - number of elements sorted and stored
     * console.log(await client.lrange("sortedList", 0, -1)); // Output: [ 'a', '3', '2' ] - List is sorted in descending order lexicographically and stored in `sortedList`
     * ```
     */
    public async sortStore(
        key: string,
        destination: string,
        options?: SortClusterOptions,
    ): Promise<number> {
        return this.createWritePromise(createSort(key, options, destination));
    }

    /**
     * Returns `UNIX TIME` of the last DB save timestamp or startup timestamp if no save
     * was made since then.
     *
     * See https://valkey.io/commands/lastsave/ for more details.
     *
     * @param route - (Optional) The command will be routed to a random node, unless `route` is provided, in which
     *     case the client will route the command to the nodes defined by `route`.
     * @returns `UNIX TIME` of the last DB save executed with success.
     * @example
     * ```typescript
     * const timestamp = await client.lastsave();
     * console.log("Last DB save was done at " + timestamp);
     * ```
     */
    public async lastsave(route?: Routes): Promise<ClusterResponse<number>> {
        return this.createWritePromise(
            createLastSave(),
            toProtobufRoute(route),
        );
    }

    /**
     * Returns a random existing key name.
     *
     * See https://valkey.io/commands/randomkey/ for more details.
     *
     * @param route - (Optional) The command will be routed to all primary nodes, unless `route` is provided,
     *      in which case the client will route the command to the nodes defined by `route`.
     * @returns A random existing key name.
     *
     * @example
     * ```typescript
     * const result = await client.randomKey();
     * console.log(result); // Output: "key12" - "key12" is a random existing key name.
     * ```
     */
    public async randomKey(route?: Routes): Promise<string | null> {
        return this.createWritePromise(
            createRandomKey(),
            toProtobufRoute(route),
        );
    }

    /**
     * Flushes all the previously watched keys for a transaction. Executing a transaction will
     * automatically flush all previously watched keys.
     *
     * See https://valkey.io/commands/unwatch/ and https://valkey.io/topics/transactions/#cas for more details.
     *
     * @param route - (Optional) The command will be routed to all primary nodes, unless `route` is provided,
     *      in which case the client will route the command to the nodes defined by `route`.
     * @returns A simple "OK" response.
     *
     * @example
     * ```typescript
     * let response = await client.watch(["sampleKey"]);
     * console.log(response); // Output: "OK"
     * response = await client.unwatch();
     * console.log(response); // Output: "OK"
     * ```
     */
    public async unwatch(route?: Routes): Promise<"OK"> {
        return this.createWritePromise(createUnWatch(), toProtobufRoute(route));
    }
}
