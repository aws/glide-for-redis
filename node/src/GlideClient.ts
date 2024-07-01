/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
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
    createEcho,
    createInfo,
    createPing,
    createSelect,
    createTime,
} from "./Commands";
import { connection_request } from "./ProtobufMessage";
import { Transaction } from "./Transaction";

export type GlideClientConfiguration = BaseClientConfiguration & {
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
export class GlideClient extends BaseClient {
    /**
     * @internal
     */
    protected createClientRequest(
        options: GlideClientConfiguration,
    ): connection_request.IConnectionRequest {
        const configuration = super.createClientRequest(options);
        configuration.databaseId = options.databaseId;
        configuration.connectionRetryStrategy = options.connectionBackoff;
        return configuration;
    }

    public static createClient(
        options: GlideClientConfiguration,
    ): Promise<GlideClient> {
        return super.createClientInternal<GlideClient>(
            options,
            (socket: net.Socket) => new GlideClient(socket),
        );
    }

    static async __createClient(
        options: BaseClientConfiguration,
        connectedSocket: net.Socket,
    ): Promise<GlideClient> {
        return this.__createClientInternal(
            options,
            connectedSocket,
            (socket, options) => new GlideClient(socket, options),
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
        return this.createWritePromise<ReturnType[] | null>(
            transaction.commands,
        ).then((result: ReturnType[] | null) => {
            return this.processResultWithSetCommands(
                result,
                transaction.setCommandsIndexes,
            );
        });
    }

    /** Executes a single command, without checking inputs. Every part of the command, including subcommands,
     *  should be added as a separate value in args.
     *
     * See the [Glide for Redis Wiki](https://github.com/aws/glide-for-redis/wiki/General-Concepts#custom-command)
     * for details on the restrictions and limitations of the custom command API.
     *
     * @example
     * ```typescript
     * // Example usage of customCommand method to retrieve pub/sub clients
     * const result = await client.customCommand(["CLIENT", "LIST", "TYPE", "PUBSUB"]);
     * console.log(result); // Output: Returns a list of all pub/sub clients
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
     *
     * @example
     * ```typescript
     * // Example usage of select method
     * const result = await client.select(2);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public select(index: number): Promise<"OK"> {
        return this.createWritePromise(createSelect(index));
    }

    /** Get the name of the primary's connection.
     *  See https://redis.io/commands/client-getname/ for more details.
     *
     * @returns the name of the client connection as a string if a name is set, or null if no name is assigned.
     *
     * @example
     * ```typescript
     * // Example usage of client_getname method
     * const result = await client.client_getname();
     * console.log(result); // Output: 'Client Name'
     * ```
     */
    public clientGetName(): Promise<string | null> {
        return this.createWritePromise(createClientGetName());
    }

    /** Rewrite the configuration file with the current configuration.
     * See https://redis.io/commands/config-rewrite/ for details.
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
    public configRewrite(): Promise<"OK"> {
        return this.createWritePromise(createConfigRewrite());
    }

    /** Resets the statistics reported by Redis using the INFO and LATENCY HISTOGRAM commands.
     * See https://redis.io/commands/config-resetstat/ for details.
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
     * @example
     * ```typescript
     * // Example usage of configGet method with multiple configuration parameters
     * const result = await client.configGet(["timeout", "maxmemory"]);
     * console.log(result); // Output: {'timeout': '1000', 'maxmemory': '1GB'}
     * ```
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
     * ```typescript
     * // Example usage of configSet method to set multiple configuration parameters
     * const result = await client.configSet({ timeout: "1000", maxmemory, "1GB" });
     * console.log(result); // Output: 'OK'
     * ```
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
     * console.log(echoedMessage); // Output: 'Glide-for-Redis'
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
     *
     * @example
     * ```typescript
     * // Example usage of time method without any argument
     * const result = await client.time();
     * console.log(result); // Output: ['1710925775', '913580']
     * ```
     */
    public time(): Promise<[string, string]> {
        return this.createWritePromise(createTime());
    }
}
