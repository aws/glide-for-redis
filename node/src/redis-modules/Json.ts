/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

import { TRedisClient } from "../../src/Constants";
import { ConditionalChange } from "../Commands";

/**
 * Represents options for formatting JSON data in the [JSON.GET](https://redis.io/commands/json.get/) command.
 */
export type JsonGetOptions = {
    /**
     * Sets an indentation string for nested levels.
     */
    indent?: string;

    /**
     * Sets a string that's printed at the end of each line.
     */
    newline?: string;

    /**
     * Sets a string that's put between a key and a value.
     */
    space?: string;
};

/**
 * Sets the JSON value at the specified `path` stored at `key`.
 * @param client - The Redis client to execute the command.
 * @param key - The key of the JSON document.
 * @param path - Represents the path within the JSON document where the value will be set.
 * @param value - The value to set at the specific path, in JSON formatted string.
 * @param setCondition - Set the value only if the given condition is met (within the key or path). see `ConditionalChange`.
 *
 * @returns If the value is successfully set, returns OK.
 * If value isn't set because of `setCondition`, returns null.
 *
 * @example
 *
 *      import * as json from "glide/redis-modules/Json";
 *      const json_value = \{ a: 1.0, b: 2 \};
 *      await json.set(client, "doc", "$", JSON.stringify(json_value));
 *       `OK` # Indicates successful setting of the value at path '$' in the key stored at `doc`.
 */
export async function set(
    client: TRedisClient,
    key: string,
    path: string,
    value: string,
    setCondition?: ConditionalChange,
): Promise<"OK" | null> {
    const args = ["JSON.SET", key, path, value];

    if (setCondition) {
        args.push(setCondition.valueOf());
    }

    const result = client.customCommand(args);
    return result as unknown as "OK" | null;
}

/**
 * Retrieves the JSON value at the specified `paths` stored at `key`.
 * See https://redis.io/commands/json.get/ for more details.
 *
 * @param client - The Redis client to execute the command.
 * @param key - The key of the JSON document.
 * @param paths - The path or list of paths within the JSON document. Default is root `$`.
 * @param options - Options for formatting the string representation of the JSON data. See `JsonGetOptions`.
 * @returns A bulk string representation of the returned value.
 * If `key` doesn't exist, returns null.
 *
 * @example
 *
 *      import * as json from "glide/redis-modules/Json";
 *      const json_value = \{ a: 1.0, b: 2 \};
 *      await json.set(client, "doc", "$", JSON.stringify(json_value));
 *       "OK"
 *      const result = await json.get(client, "doc", "$");
 *      JSON.parse(result!);
 *       [\{ a: 1.0, b: 2 \}] # JSON object retrieved from the key `doc` using JSON.parse()
 *      await json.get(client, "doc", "$");
 *       `[\{\"a\":1.0,\"b\":2\}]` # Returns the value at path '$' in the JSON document stored at `doc`.
 *      await redisJson.get(client, "doc", ["$.a", "$.b"], \{ indent: " ", newline: "\\n", space: " "\});
 *       `\{\n "$.a": [\n  1.0\n ],\n "$.b": [\n  2\n ]\n\}`  // Returns the values at paths '$.a' and '$.b' in the JSON document stored at `doc`, with specified formatting options.
 *      await redisJson.get(client, "doc", "$.non_existing_path")
 *       `[]`  # Returns an empty array since the path '$.non_existing_path' does not exist in the JSON document stored at `doc`.
 */
export async function get(
    client: TRedisClient,
    key: string,
    paths?: string | string[],
    options?: JsonGetOptions,
): Promise<string | null> {
    const args = ["JSON.GET", key];

    if (options) {
        if (options.indent) {
            args.push("INDENT", options.indent);
        }

        if (options.newline) {
            args.push("NEWLINE", options.newline);
        }

        if (options.space) {
            args.push("SPACE", options.space);
        }
    }

    if (paths) {
        if (typeof paths === "string") {
            paths = [paths];
        }

        args.push(...paths);
    }

    const result = client.customCommand(args);
    return result as unknown as string | null;
}
