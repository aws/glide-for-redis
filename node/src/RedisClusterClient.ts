/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

import * as net from "net";
import { BaseClient, BaseClientConfiguration, ReturnType } from "./BaseClient";
import {
    InfoOptions,
    createClientGetName,
    createClientId,
    createConfigGet,
    createConfigResetStat,
    createConfigRewrite,
    createConfigSet,
    createCustomCommand,
    createInfo,
    createPing,
    createTime,
} from "./Commands";
import { RequestError } from "./Errors";
import { connection_request, redis_request } from "./ProtobufMessage";
import { ClusterTransaction } from "./Transaction";

export type ClusterClientConfiguration = BaseClientConfiguration;

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
): redis_request.Routes | undefined {
    if (route === undefined) {
        return undefined;
    }

    if (route === "allPrimaries") {
        return redis_request.Routes.create({
            simpleRoutes: redis_request.SimpleRoutes.AllPrimaries,
        });
    } else if (route === "allNodes") {
        return redis_request.Routes.create({
            simpleRoutes: redis_request.SimpleRoutes.AllNodes,
        });
    } else if (route === "randomNode") {
        return redis_request.Routes.create({
            simpleRoutes: redis_request.SimpleRoutes.Random,
        });
    } else if (route.type === "primarySlotKey") {
        return redis_request.Routes.create({
            slotKeyRoute: redis_request.SlotKeyRoute.create({
                slotType: redis_request.SlotTypes.Primary,
                slotKey: route.key,
            }),
        });
    } else if (route.type === "replicaSlotKey") {
        return redis_request.Routes.create({
            slotKeyRoute: redis_request.SlotKeyRoute.create({
                slotType: redis_request.SlotTypes.Replica,
                slotKey: route.key,
            }),
        });
    } else if (route.type === "primarySlotId") {
        return redis_request.Routes.create({
            slotKeyRoute: redis_request.SlotIdRoute.create({
                slotType: redis_request.SlotTypes.Primary,
                slotId: route.id,
            }),
        });
    } else if (route.type === "replicaSlotId") {
        return redis_request.Routes.create({
            slotKeyRoute: redis_request.SlotIdRoute.create({
                slotType: redis_request.SlotTypes.Replica,
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

        return redis_request.Routes.create({
            byAddressRoute: { host, port },
        });
    }
}

/**
 * Client used for connection to cluster Redis servers.
 * For full documentation, see
 * https://github.com/aws/babushka/wiki/NodeJS-wrapper#redis-cluster
 */
export class RedisClusterClient extends BaseClient {
    /**
     * @internal
     */
    protected createClientRequest(
        options: ClusterClientConfiguration,
    ): connection_request.IConnectionRequest {
        const configuration = super.createClientRequest(options);
        configuration.clusterModeEnabled = true;
        return configuration;
    }

    public static async createClient(
        options: ClusterClientConfiguration,
    ): Promise<RedisClusterClient> {
        return await super.createClientInternal(
            options,
            (socket: net.Socket, options?: ClusterClientConfiguration) =>
                new RedisClusterClient(socket, options),
        );
    }

    static async __createClient(
        options: BaseClientConfiguration,
        connectedSocket: net.Socket,
    ): Promise<RedisClusterClient> {
        return super.__createClientInternal(
            options,
            connectedSocket,
            (socket, options) => new RedisClusterClient(socket, options),
        );
    }

    /** Executes a single command, without checking inputs. Every part of the command, including subcommands,
     *  should be added as a separate value in args.
     *  The command will be routed automatically based on the passed command's default request policy, unless `route` is provided,
     *  in which case the client will route the command to the nodes defined by `route`.
     *
     *  @remarks - This function should only be used for single-response commands. Commands that don't return response (such as SUBSCRIBE), or that return potentially more than a single response (such as XREAD), or that change the client's behavior (such as entering pub/sub mode on RESP2 connections) shouldn't be called using this function.
     *
     * @example
     * Returns a list of all pub/sub clients on all primary nodes
     * ```ts
     * connection.customCommand(["CLIENT", "LIST","TYPE", "PUBSUB"], "allPrimaries")
     * ```
     */
    public customCommand(args: string[], route?: Routes): Promise<ReturnType> {
        const command = createCustomCommand(args);
        return super.createWritePromise(command, toProtobufRoute(route));
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
        return this.createWritePromise(
            transaction.commands,
            toProtobufRoute(route),
        );
    }

    /** Ping the Redis server.
     * See https://redis.io/commands/ping/ for details.
     *
     * @param message - An optional message to include in the PING command.
     * If not provided, the server will respond with "PONG".
     * If provided, the server will respond with a copy of the message.
     * @param route - The command will be routed to all primaries, unless `route` is provided, in which
     *   case the client will route the command to the nodes defined by `route`.
     * @returns - "PONG" if `message` is not provided, otherwise return a copy of `message`.
     */
    public ping(message?: string, route?: Routes): Promise<string> {
        return this.createWritePromise(
            createPing(message),
            toProtobufRoute(route),
        );
    }

    /** Get information and statistics about the Redis server.
     *  See https://redis.io/commands/info/ for details.
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
            toProtobufRoute(route),
        );
    }

    /** Get the name of the connection to which the request is routed.
     *  See https://redis.io/commands/client-getname/ for more details.
     *
     * @param route - The command will be routed a random node, unless `route` is provided, in which
     *   case the client will route the command to the nodes defined by `route`.
     *
     * @returns - the name of the client connection as a string if a name is set, or null if no name is assigned.
     * When specifying a route other than a single node, it returns a dictionary where each address is the key and
     * its corresponding node response is the value.
     */
    public clientGetName(
        route?: Routes,
    ): Promise<ClusterResponse<string | null>> {
        return this.createWritePromise<ClusterResponse<string | null>>(
            createClientGetName(),
            toProtobufRoute(route),
        );
    }

    /** Rewrite the configuration file with the current configuration.
     * See https://redis.io/commands/config-rewrite/ for details.
     *
     * @param route - The command will be routed to all nodes, unless `route` is provided, in which
     *   case the client will route the command to the nodes defined by `route`.
     *
     * @returns "OK" when the configuration was rewritten properly. Otherwise, an error is thrown.
     */
    public configRewrite(route?: Routes): Promise<"OK"> {
        return this.createWritePromise(
            createConfigRewrite(),
            toProtobufRoute(route),
        );
    }

    /** Resets the statistics reported by Redis using the INFO and LATENCY HISTOGRAM commands.
     * See https://redis.io/commands/config-resetstat/ for details.
     *
     * @param route - The command will be routed to all nodes, unless `route` is provided, in which
     *   case the client will route the command to the nodes defined by `route`.
     *
     * @returns always "OK".
     */
    public configResetStat(route?: Routes): Promise<"OK"> {
        return this.createWritePromise(
            createConfigResetStat(),
            toProtobufRoute(route),
        );
    }

    /** Returns the current connection id.
     * See https://redis.io/commands/client-id/ for details.
     *
     * @param route - The command will be routed to a random node, unless `route` is provided, in which
     *   case the client will route the command to the nodes defined by `route`.
     * @returns the id of the client. When specifying a route other than a single node,
     * it returns a dictionary where each address is the key and its corresponding node response is the value.
     */
    public clientId(route?: Routes): Promise<ClusterResponse<number>> {
        return this.createWritePromise<ClusterResponse<number>>(
            createClientId(),
            toProtobufRoute(route),
        );
    }

    /** Reads the configuration parameters of a running Redis server.
     *  See https://redis.io/commands/config-get/ for details.
     *
     * @param parameters - A list of configuration parameter names to retrieve values for.
     * @param route - The command will be routed to a random node, unless `route` is provided, in which
     *  case the client will route the command to the nodes defined by `route`.
     *  If `route` is not provided, the command will be sent to a random node.
     *
     * @returns A map of values corresponding to the configuration parameters. When specifying a route other than a single node,
     *  it returns a dictionary where each address is the key and its corresponding node response is the value.
     */
    public configGet(
        parameters: string[],
        route?: Routes,
    ): Promise<ClusterResponse<Record<string, string>>> {
        return this.createWritePromise<ClusterResponse<Record<string, string>>>(
            createConfigGet(parameters),
            toProtobufRoute(route),
        );
    }

    /** Set configuration parameters to the specified values.
     *   See https://redis.io/commands/config-set/ for details.
     *
     * @param parameters - A List of keyValuePairs consisting of configuration parameters and their respective values to set.
     * @param route - The command will be routed to all nodes, unless `route` is provided, in which
     *   case the client will route the command to the nodes defined by `route`.
     *   If `route` is not provided, the command will be sent to the all nodes.
     *
     * @returns "OK" when the configuration was set properly. Otherwise an error is thrown.
     *
     * @example
     *   config_set([("timeout", "1000")], [("maxmemory", "1GB")]) - Returns OK
     *
     */
    public configSet(
        parameters: Record<string, string>,
        route?: Routes,
    ): Promise<"OK"> {
        return this.createWritePromise(
            createConfigSet(parameters),
            toProtobufRoute(route),
        );
    }

    /** Returns the server time.
     * See https://redis.io/commands/time/ for details.
     *
     * @param route - The command will be routed to a random node, unless `route` is provided, in which
     *  case the client will route the command to the nodes defined by `route`.
     *
     * @returns - The current server time as a two items `array`:
     * A Unix timestamp and the amount of microseconds already elapsed in the current second.
     * The returned `array` is in a [Unix timestamp, Microseconds already elapsed] format.
     * When specifying a route other than a single node, it returns a dictionary where each address is the key and
     * its corresponding node response is the value.
     */
    public time(route?: Routes): Promise<ClusterResponse<[string, string]>> {
        return this.createWritePromise(createTime(), toProtobufRoute(route));
    }
}
