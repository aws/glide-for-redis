/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import * as net from "net";
import {
    BaseClient,
    BaseClientConfiguration,
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
    SortOptions,
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
    createPublish,
    createRandomKey,
    createSelect,
    createSort,
    createSortReadOnly,
    createTime,
    createUnWatch,
} from "./Commands";
import { connection_request } from "./ProtobufMessage";
import { Transaction } from "./Transaction";

/* eslint-disable-next-line @typescript-eslint/no-namespace */
export namespace GlideClientConfiguration {
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
    /**
     * PubSub subscriptions to be used for the client.
     * Will be applied via SUBSCRIBE/PSUBSCRIBE commands during connection establishment.
     */
    pubsubSubscriptions?: GlideClientConfiguration.PubSubSubscriptions;
};

/**
 * Client used for connection to standalone Redis servers.
 * For full documentation, see
 * https://github.com/valkey-io/valkey-glide/wiki/NodeJS-wrapper#standalone
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
        this.configurePubsub(options, configuration);
        return configuration;
    }

    public static createClient(
        options: GlideClientConfiguration,
    ): Promise<GlideClient> {
        return super.createClientInternal<GlideClient>(
            options,
            (socket: net.Socket, options?: GlideClientConfiguration) =>
                new GlideClient(socket, options),
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
     * See the [Glide for Redis Wiki](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command)
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
     * See https://valkey.io/commands/ping/ for details.
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
     *  See https://valkey.io/commands/info/ for details.
     *
     * @param options - A list of InfoSection values specifying which sections of information to retrieve.
     *  When no parameter is provided, the default option is assumed.
     * @returns a string containing the information for the sections requested.
     */
    public info(options?: InfoOptions[]): Promise<string> {
        return this.createWritePromise(createInfo(options));
    }

    /** Change the currently selected Redis database.
     * See https://valkey.io/commands/select/ for details.
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
     *  See https://valkey.io/commands/client-getname/ for more details.
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
     * See https://valkey.io/commands/config-rewrite/ for details.
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
     * See https://valkey.io/commands/config-resetstat/ for details.
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
     * See https://valkey.io/commands/client-id/ for details.
     *
     * @returns the id of the client.
     */
    public clientId(): Promise<number> {
        return this.createWritePromise(createClientId());
    }

    /** Reads the configuration parameters of a running Redis server.
     *  See https://valkey.io/commands/config-get/ for details.
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
     *   See https://valkey.io/commands/config-set/ for details.
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
     * See https://valkey.io/commands/echo for more details.
     *
     * @param message - The message to be echoed back.
     * @returns The provided `message`.
     *
     * @example
     * ```typescript
     * // Example usage of the echo command
     * const echoedMessage = await client.echo("valkey-glide");
     * console.log(echoedMessage); // Output: 'valkey-glide'
     * ```
     */
    public echo(message: string): Promise<string> {
        return this.createWritePromise(createEcho(message));
    }

    /** Returns the server time
     * See https://valkey.io/commands/time/ for details.
     *
     * @returns - The current server time as a two items `array`:
     * A Unix timestamp and the amount of microseconds already elapsed in the current second.
     * The returned `array` is in a [Unix timestamp, Microseconds already elapsed] format.
     *
     * @example
     * ```typescript
     * // Example usage of time command
     * const result = await client.time();
     * console.log(result); // Output: ['1710925775', '913580']
     * ```
     */
    public time(): Promise<[string, string]> {
        return this.createWritePromise(createTime());
    }

    /**
     * Copies the value stored at the `source` to the `destination` key. If `destinationDB` is specified,
     * the value will be copied to the database specified, otherwise the current database will be used.
     * When `replace` is true, removes the `destination` key first if it already exists, otherwise performs
     * no action.
     *
     * See https://valkey.io/commands/copy/ for more details.
     *
     * @param source - The key to the source value.
     * @param destination - The key where the value should be copied to.
     * @param destinationDB - (Optional) The alternative logical database index for the destination key.
     *     If not provided, the current database will be used.
     * @param replace - (Optional) If `true`, the `destination` key should be removed before copying the
     *     value to it. If not provided, no action will be performed if the key already exists.
     * @returns `true` if `source` was copied, `false` if the `source` was not copied.
     *
     * since Valkey version 6.2.0.
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
        source: string,
        destination: string,
        options?: { destinationDB?: number; replace?: boolean },
    ): Promise<boolean> {
        return this.createWritePromise(
            createCopy(source, destination, options),
        );
    }

    /**
     * Displays a piece of generative computer art and the server version.
     *
     * See https://valkey.io/commands/lolwut/ for more details.
     *
     * @param options - The LOLWUT options
     * @returns A piece of generative computer art along with the current server version.
     *
     * @example
     * ```typescript
     * const response = await client.lolwut({ version: 6, parameters: [40, 20] });
     * console.log(response); // Output: "Redis ver. 7.2.3" - Indicates the current server version.
     * ```
     */
    public lolwut(options?: LolwutOptions): Promise<string> {
        return this.createWritePromise(createLolwut(options), {decoder: options? options.decoder: undefined});
    }

    /**
     * Deletes a library and all its functions.
     *
     * See https://valkey.io/commands/function-delete/ for details.
     *
     * since Valkey version 7.0.0.
     *
     * @param libraryCode - The library name to delete.
     * @returns A simple OK response.
     *
     * @example
     * ```typescript
     * const result = await client.functionDelete("libName");
     * console.log(result); // Output: 'OK'
     * ```
     */
    public functionDelete(libraryCode: string): Promise<string> {
        return this.createWritePromise(createFunctionDelete(libraryCode));
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
     * @returns The library name that was loaded.
     *
     * @example
     * ```typescript
     * const code = "#!lua name=mylib \n redis.register_function('myfunc', function(keys, args) return args[1] end)";
     * const result = await client.functionLoad(code, true);
     * console.log(result); // Output: 'mylib'
     * ```
     */
    public functionLoad(
        libraryCode: string,
        replace?: boolean,
    ): Promise<string> {
        return this.createWritePromise(
            createFunctionLoad(libraryCode, replace),
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
     * @returns A simple OK response.
     *
     * @example
     * ```typescript
     * const result = await client.functionFlush(FlushMode.SYNC);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public functionFlush(mode?: FlushMode): Promise<string> {
        return this.createWritePromise(createFunctionFlush(mode));
    }

    /**
     * Returns information about the functions and libraries.
     *
     * See https://valkey.io/commands/function-list/ for details.
     *
     * since Valkey version 7.0.0.
     *
     * @param options - Parameters to filter and request additional info.
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
    ): Promise<FunctionListResponse> {
        return this.createWritePromise(createFunctionList(options));
    }

    /**
     * Deletes all the keys of all the existing databases. This command never fails.
     *
     * See https://valkey.io/commands/flushall/ for more details.
     *
     * @param mode - The flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     * @returns `OK`.
     *
     * @example
     * ```typescript
     * const result = await client.flushall(FlushMode.SYNC);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public flushall(mode?: FlushMode): Promise<string> {
        return this.createWritePromise(createFlushAll(mode));
    }

    /**
     * Deletes all the keys of the currently selected database. This command never fails.
     *
     * See https://valkey.io/commands/flushdb/ for more details.
     *
     * @param mode - The flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     * @returns `OK`.
     *
     * @example
     * ```typescript
     * const result = await client.flushdb(FlushMode.SYNC);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public flushdb(mode?: FlushMode): Promise<string> {
        return this.createWritePromise(createFlushDB(mode));
    }

    /**
     * Returns the number of keys in the currently selected database.
     *
     * See https://valkey.io/commands/dbsize/ for more details.
     *
     * @returns The number of keys in the currently selected database.
     *
     * @example
     * ```typescript
     * const numKeys = await client.dbsize();
     * console.log("Number of keys in the current database: ", numKeys);
     * ```
     */
    public dbsize(): Promise<number> {
        return this.createWritePromise(createDBSize());
    }

    /** Publish a message on pubsub channel.
     *
     * See https://valkey.io/commands/publish for more details.
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
    public publish(message: string, channel: string): Promise<number> {
        return this.createWritePromise(createPublish(message, channel));
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
     * @param options - The {@link SortOptions}.
     * @returns An `Array` of sorted elements.
     *
     * @example
     * ```typescript
     * await client.hset("user:1", new Map([["name", "Alice"], ["age", "30"]]));
     * await client.hset("user:2", new Map([["name", "Bob"], ["age", "25"]]));
     * await client.lpush("user_ids", ["2", "1"]);
     * const result = await client.sort("user_ids", { byPattern: "user:*->age", getPattern: ["user:*->name"] });
     * console.log(result); // Output: [ 'Bob', 'Alice' ] - Returns a list of the names sorted by age
     * ```
     */
    public async sort(
        key: string,
        options?: SortOptions,
    ): Promise<(string | null)[]> {
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
     * @param options - The {@link SortOptions}.
     * @returns An `Array` of sorted elements
     *
     * @example
     * ```typescript
     * await client.hset("user:1", new Map([["name", "Alice"], ["age", "30"]]));
     * await client.hset("user:2", new Map([["name", "Bob"], ["age", "25"]]));
     * await client.lpush("user_ids", ["2", "1"]);
     * const result = await client.sortReadOnly("user_ids", { byPattern: "user:*->age", getPattern: ["user:*->name"] });
     * console.log(result); // Output: [ 'Bob', 'Alice' ] - Returns a list of the names sorted by age
     * ```
     */
    public async sortReadOnly(
        key: string,
        options?: SortOptions,
    ): Promise<(string | null)[]> {
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
     * @param options - The {@link SortOptions}.
     * @returns The number of elements in the sorted key stored at `destination`.
     *
     * @example
     * ```typescript
     * await client.hset("user:1", new Map([["name", "Alice"], ["age", "30"]]));
     * await client.hset("user:2", new Map([["name", "Bob"], ["age", "25"]]));
     * await client.lpush("user_ids", ["2", "1"]);
     * const sortedElements = await client.sortStore("user_ids", "sortedList", { byPattern: "user:*->age", getPattern: ["user:*->name"] });
     * console.log(sortedElements); // Output: 2 - number of elements sorted and stored
     * console.log(await client.lrange("sortedList", 0, -1)); // Output: [ 'Bob', 'Alice' ] - Returns a list of the names sorted by age stored in `sortedList`
     * ```
     */
    public async sortStore(
        key: string,
        destination: string,
        options?: SortOptions,
    ): Promise<number> {
        return this.createWritePromise(createSort(key, options, destination));
    }

    /**
     * Returns `UNIX TIME` of the last DB save timestamp or startup timestamp if no save
     * was made since then.
     *
     * See https://valkey.io/commands/lastsave/ for more details.
     *
     * @returns `UNIX TIME` of the last DB save executed with success.
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
     * Returns a random existing key name from the currently selected database.
     *
     * See https://valkey.io/commands/randomkey/ for more details.
     *
     * @returns A random existing key name from the currently selected database.
     *
     * @example
     * ```typescript
     * const result = await client.randomKey();
     * console.log(result); // Output: "key12" - "key12" is a random existing key name from the currently selected database.
     * ```
     */
    public async randomKey(): Promise<string | null> {
        return this.createWritePromise(createRandomKey());
    }

    /**
     * Flushes all the previously watched keys for a transaction. Executing a transaction will
     * automatically flush all previously watched keys.
     *
     * See https://valkey.io/commands/unwatch/ and https://valkey.io/topics/transactions/#cas for more details.
     *
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
    public async unwatch(): Promise<"OK"> {
        return this.createWritePromise(createUnWatch());
    }
}
