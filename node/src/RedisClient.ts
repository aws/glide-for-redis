/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

import * as net from "net";
import { BaseClient, BaseClientConfiguration } from "./BaseClient";
import {
    InfoOptions,
    createClientGetName,
    createClientId,
    createConfigGet,
    createConfigResetStat,
    createConfigRewrite,
    createConfigSet,
    createCustomCommand,
    createEcho,
    createInfo,
    createPing,
    createSelect,
    createTime,
} from "./Commands";
import { ReturnType } from "./Constants";
import { connection_request } from "./ProtobufMessage";
import { Transaction } from "./Transaction";

export type RedisClientConfiguration = BaseClientConfiguration & {
    /**
     * index of the logical database to connect to.
     */
    databaseId?: number;
    /**
     * Strategy used to determine how and when to reconnect, in case of connection failures.
     * The time between attempts grows exponentially, to the formula rand(0 .. factor * (exponentBase ^ N)), where N is the number of failed attempts.
     * The client will attempt to reconnect indefinitely. Once the maximum value is reached, that will remain the time between retry attempts until a
     * reconnect attempt is succesful.
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
         * Value must be an integer.
         */
        factor: number;
        /**
         * The exponent base configured for the strategy.
         * Value must be an integer.
         */
        exponentBase: number;
    };
};

/**
 * Client used for connection to standalone Redis servers.
 * For full documentation, see
 * https://github.com/aws/babushka/wiki/NodeJS-wrapper#redis-standalone
 */
export class RedisClient extends BaseClient {
    /**
     * @internal
     */
    protected createClientRequest(
        options: RedisClientConfiguration,
    ): connection_request.IConnectionRequest {
        const configuration = super.createClientRequest(options);
        configuration.databaseId = options.databaseId;
        configuration.connectionRetryStrategy = options.connectionBackoff;
        return configuration;
    }

    public static createClient(
        options: RedisClientConfiguration,
    ): Promise<RedisClient> {
        return super.createClientInternal<RedisClient>(
            options,
            (socket: net.Socket) => new RedisClient(socket),
        );
    }

    static async __createClient(
        options: BaseClientConfiguration,
        connectedSocket: net.Socket,
    ): Promise<RedisClient> {
        return this.__createClientInternal(
            options,
            connectedSocket,
            (socket, options) => new RedisClient(socket, options),
        );
    }

    /** Execute a transaction by processing the queued commands.
     *   See https://redis.io/topics/Transactions/ for details on Redis Transactions.
     *
     * @param transaction - A Transaction object containing a list of commands to be executed.
     * @returns A list of results corresponding to the execution of each command in the transaction.
     *      If a command returns a value, it will be included in the list. If a command doesn't return a value,
     *      the list entry will be null.
     *      If the transaction failed due to a WATCH command, `exec` will return `null`.
     */
    public exec(transaction: Transaction): Promise<ReturnType[] | null> {
        return this.createWritePromise(transaction.commands);
    }

    /** Executes a single command, without checking inputs. Every part of the command, including subcommands,
     *  should be added as a separate value in args.
     *
     *  @remarks - This function should only be used for single-response commands. Commands that don't return response (such as SUBSCRIBE), or that return potentially more than a single response (such as XREAD), or that change the client's behavior (such as entering pub/sub mode on RESP2 connections) shouldn't be called using this function.
     *
     * @example
     * Returns a list of all pub/sub clients:
     * ```ts
     * connection.customCommand(["CLIENT","LIST","TYPE", "PUBSUB"])
     * ```
     */
    public customCommand(args: string[]): Promise<ReturnType> {
        return this.createWritePromise(createCustomCommand(args));
    }

    /** Ping the Redis server.
     * See https://redis.io/commands/ping/ for details.
     *
     * @param message - An optional message to include in the PING command.
     * If not provided, the server will respond with "PONG".
     * If provided, the server will respond with a copy of the message.
     * @returns - "PONG" if `message` is not provided, otherwise return a copy of `message`.
     */
    public ping(message?: string): Promise<string> {
        return this.createWritePromise(createPing(message));
    }

    /** Get information and statistics about the Redis server.
     *  See https://redis.io/commands/info/ for details.
     *
     * @param options - A list of InfoSection values specifying which sections of information to retrieve.
     *  When no parameter is provided, the default option is assumed.
     * @returns a string containing the information for the sections requested.
     */
    public info(options?: InfoOptions[]): Promise<string> {
        return this.createWritePromise(createInfo(options));
    }

    /** Change the currently selected Redis database.
     * See https://redis.io/commands/select/ for details.
     *
     * @param index - The index of the database to select.
     * @returns A simple OK response.
     */
    public select(index: number): Promise<"OK"> {
        return this.createWritePromise(createSelect(index));
    }

    /** Get the name of the primary's connection.
     *  See https://redis.io/commands/client-getname/ for more details.
     *
     * @returns the name of the client connection as a string if a name is set, or null if no name is assigned.
     */
    public clientGetName(): Promise<string | null> {
        return this.createWritePromise(createClientGetName());
    }

    /** Rewrite the configuration file with the current configuration.
     * See https://redis.io/commands/config-rewrite/ for details.
     *
     * @returns "OK" when the configuration was rewritten properly. Otherwise, an error is thrown.
     */
    public configRewrite(): Promise<"OK"> {
        return this.createWritePromise(createConfigRewrite());
    }

    /** Resets the statistics reported by Redis using the INFO and LATENCY HISTOGRAM commands.
     * See https://redis.io/commands/config-resetstat/ for details.
     *
     * @returns always "OK".
     */
    public configResetStat(): Promise<"OK"> {
        return this.createWritePromise(createConfigResetStat());
    }

    /** Returns the current connection id.
     * See https://redis.io/commands/client-id/ for details.
     *
     * @returns the id of the client.
     */
    public clientId(): Promise<number> {
        return this.createWritePromise(createClientId());
    }

    /** Reads the configuration parameters of a running Redis server.
     *  See https://redis.io/commands/config-get/ for details.
     *
     * @param parameters - A list of configuration parameter names to retrieve values for.
     *
     * @returns A map of values corresponding to the configuration parameters.
     *
     */
    public configGet(parameters: string[]): Promise<Record<string, string>> {
        return this.createWritePromise(createConfigGet(parameters));
    }

    /** Set configuration parameters to the specified values.
     *   See https://redis.io/commands/config-set/ for details.
     *
     * @param parameters - A List of keyValuePairs consisting of configuration parameters and their respective values to set.
     *
     * @returns "OK" when the configuration was set properly. Otherwise an error is thrown.
     *
     * @example
     *  config_set([("timeout", "1000")], [("maxmemory", "1GB")]) - Returns OK
     *
     */
    public configSet(parameters: Record<string, string>): Promise<"OK"> {
        return this.createWritePromise(createConfigSet(parameters));
    }

    /** Echoes the provided `message` back.
     * See https://redis.io/commands/echo for more details.
     *
     * @param message - The message to be echoed back.
     * @returns The provided `message`.
     *
     * @example
     * ```typescript
     * // Example usage of the echo command
     * const echoedMessage = await client.echo("Glide-for-Redis");
     * console.log(echoedMessage); // Output: "Glide-for-Redis"
     * ```
     */
    public echo(message: string): Promise<string> {
        return this.createWritePromise(createEcho(message));
    }

    /** Returns the server time
     * See https://redis.io/commands/time/ for details.
     *
     * @returns - The current server time as a two items `array`:
     * A Unix timestamp and the amount of microseconds already elapsed in the current second.
     * The returned `array` is in a [Unix timestamp, Microseconds already elapsed] format.
     */
    public time(): Promise<[string, string]> {
        return this.createWritePromise(createTime());
    }
}
