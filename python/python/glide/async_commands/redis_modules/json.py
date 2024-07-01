# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
"""module for `RedisJSON` commands.

    Examples:

        >>> from glide import json as redisJson
        >>> import json
        >>> value = {'a': 1.0, 'b': 2}
        >>> json_str = json.dumps(value) # Convert Python dictionary to JSON string using json.dumps()
        >>> await redisJson.set(client, "doc", "$", json_str)
            'OK'  # Indicates successful setting of the value at path '$' in the key stored at `doc`.
        >>> json_get = await redisJson.get(client, "doc", "$") # Returns the value at path '$' in the JSON document stored at `doc` as JSON string.
        >>> print(json_get)
            "[{\"a\":1.0,\"b\":2}]" 
        >>> json.loads(json_get)
            [{"a": 1.0, "b" :2}] # JSON object retrieved from the key `doc` using json.loads()
        """
from typing import List, Optional, Union, cast

from glide.async_commands.core import ConditionalChange
from glide.constants import TOK, TJsonResponse
from glide.glide_client import TGlideClient
from glide.protobuf.redis_request_pb2 import RequestType


class JsonGetOptions:
    """
    Represents options for formatting JSON data, to be used in  the [JSON.GET](https://redis.io/commands/json.get/) command.

    Args:
        indent (Optional[str]): Sets an indentation string for nested levels. Defaults to None.
        newline (Optional[str]): Sets a string that's printed at the end of each line. Defaults to None.
        space (Optional[str]): Sets a string that's put between a key and a value. Defaults to None.
    """

    def __init__(
        self,
        indent: Optional[str] = None,
        newline: Optional[str] = None,
        space: Optional[str] = None,
    ):
        self.indent = indent
        self.new_line = newline
        self.space = space

    def get_options(self) -> List[str]:
        args = []
        if self.indent:
            args.extend(["INDENT", self.indent])
        if self.new_line:
            args.extend(["NEWLINE", self.new_line])
        if self.space:
            args.extend(["SPACE", self.space])
        return args


async def set(
    client: TGlideClient,
    key: str,
    path: str,
    value: str,
    set_condition: Optional[ConditionalChange] = None,
) -> Optional[TOK]:
    """
    Sets the JSON value at the specified `path` stored at `key`.

    See https://redis.io/commands/json.set/ for more details.

    Args:
        client (TGlideClient): The Redis client to execute the command.
        key (str): The key of the JSON document.
        path (str): Represents the path within the JSON document where the value will be set.
            The key will be modified only if `value` is added as the last child in the specified `path`, or if the specified `path` acts as the parent of a new child being added.
        value (set): The value to set at the specific path, in JSON formatted str.
        set_condition (Optional[ConditionalChange]): Set the value only if the given condition is met (within the key or path).
            Equivalent to [`XX` | `NX`] in the Redis API. Defaults to None.

    Returns:
        Optional[TOK]: If the value is successfully set, returns OK.
            If value isn't set because of `set_condition`, returns None.

    Examples:
        >>> from glide import json as redisJson
        >>> import json
        >>> value = {'a': 1.0, 'b': 2}
        >>> json_str = json.dumps(value)
        >>> await redisJson.set(client, "doc", "$", json_str)
            'OK'  # Indicates successful setting of the value at path '$' in the key stored at `doc`.
    """
    args = ["JSON.SET", key, path, value]
    if set_condition:
        args.append(set_condition.value)

    return cast(Optional[TOK], await client.custom_command(args))


async def get(
    client: TGlideClient,
    key: str,
    paths: Optional[Union[str, List[str]]] = None,
    options: Optional[JsonGetOptions] = None,
) -> Optional[str]:
    """
    Retrieves the JSON value at the specified `paths` stored at `key`.

    See https://redis.io/commands/json.get/ for more details.

    Args:
        client (TGlideClient): The Redis client to execute the command.
        key (str): The key of the JSON document.
        paths (Optional[Union[str, List[str]]]): The path or list of paths within the JSON document. Default is root `$`.
        options (Optional[JsonGetOptions]): Options for formatting the string representation of the JSON data. See `JsonGetOptions`.

    Returns:
        str: A bulk string representation of the returned value.
            If `key` doesn't exists, returns None.

    Examples:
        >>> from glide import json as redisJson
        >>> import json
        >>> json_str = await redisJson.get(client, "doc", "$")
        >>> json.loads(json_str) # Parse JSON string to Python data
            [{"a": 1.0, "b" :2}]  # JSON object retrieved from the key `doc` using json.loads()
        >>> await redisJson.get(client, "doc", "$")
            "[{\"a\":1.0,\"b\":2}]"  # Returns the value at path '$' in the JSON document stored at `doc`.
        >>> await redisJson.get(client, "doc", ["$.a", "$.b"], json.JsonGetOptions(indent="  ", newline="\n", space=" "))
            "{\n \"$.a\": [\n  1.0\n ],\n \"$.b\": [\n  2\n ]\n}"  # Returns the values at paths '$.a' and '$.b' in the JSON document stored at `doc`, with specified formatting options.
        >>> await redisJson.get(client, "doc", "$.non_existing_path")
            "[]"  # Returns an empty array since the path '$.non_existing_path' does not exist in the JSON document stored at `doc`.
    """
    args = ["JSON.GET", key]
    if options:
        args.extend(options.get_options())
    if paths:
        if isinstance(paths, str):
            paths = [paths]
        args.extend(paths)

    return cast(str, await client.custom_command(args))


async def delete(
    client: TGlideClient,
    key: str,
    path: Optional[str] = None,
) -> int:
    """
    Deletes the JSON value at the specified `path` within the JSON document stored at `key`.

    See https://redis.io/commands/json.del/ for more details.

    Args:
        client (TGlideClient): The Redis client to execute the command.
        key (str): The key of the JSON document.
        path (Optional[str]): Represents the path within the JSON document where the value will be deleted.
            If None, deletes the entire JSON document at `key`. Defaults to None.

    Returns:
        int: The number of elements removed.
        If `key` or path doesn't exist, returns 0.

    Examples:
        >>> from glide import json as redisJson
        >>> await redisJson.set(client, "doc", "$", '{"a": 1, "nested": {"a": 2, "b": 3}}')
            'OK'  # Indicates successful setting of the value at path '$' in the key stored at `doc`.
        >>> await redisJson.delete(client, "doc", "$..a")
            2  # Indicates successful deletion of the specific values in the key stored at `doc`.
        >>> await redisJson.get(client, "doc", "$")
            "[{\"nested\":{\"b\":3}}]"  # Returns the value at path '$' in the JSON document stored at `doc`.
        >>> await redisJson.delete(client, "doc")
            1  # Deletes the entire JSON document stored at `doc`.
    """

    return cast(
        int, await client.custom_command(["JSON.DEL", key] + ([path] if path else []))
    )


async def forget(
    client: TGlideClient,
    key: str,
    path: Optional[str] = None,
) -> Optional[int]:
    """
    Deletes the JSON value at the specified `path` within the JSON document stored at `key`.

    See https://redis.io/commands/json.forget/ for more details.

    Args:
        client (TGlideClient): The Redis client to execute the command.
        key (str): The key of the JSON document.
        path (Optional[str]): Represents the path within the JSON document where the value will be deleted.
            If None, deletes the entire JSON document at `key`. Defaults to None.

    Returns:
        int: The number of elements removed.
        If `key` or path doesn't exist, returns 0.

    Examples:
        >>> from glide import json as redisJson
        >>> await redisJson.set(client, "doc", "$", '{"a": 1, "nested": {"a": 2, "b": 3}}')
            'OK'  # Indicates successful setting of the value at path '$' in the key stored at `doc`.
        >>> await redisJson.forget(client, "doc", "$..a")
            2  # Indicates successful deletion of the specific values in the key stored at `doc`.
        >>> await redisJson.get(client, "doc", "$")
            "[{\"nested\":{\"b\":3}}]"  # Returns the value at path '$' in the JSON document stored at `doc`.
        >>> await redisJson.forget(client, "doc")
            1  # Deletes the entire JSON document stored at `doc`.
    """

    return cast(
        Optional[int],
        await client.custom_command(["JSON.FORGET", key] + ([path] if path else [])),
    )


async def toggle(
    client: TGlideClient,
    key: str,
    path: str,
) -> TJsonResponse[bool]:
    """
    Toggles a Boolean value stored at the specified `path` within the JSON document stored at `key`.

    See https://redis.io/commands/json.toggle/ for more details.

    Args:
        client (TGlideClient): The Redis client to execute the command.
        key (str): The key of the JSON document.
        path (str): The JSONPath to specify.

    Returns:
        TJsonResponse[bool]: For JSONPath (`path` starts with `$`), returns a list of boolean replies for every possible path, with the toggled boolean value,
        or None for JSON values matching the path that are not boolean.
        For legacy path (`path` doesn't starts with `$`), returns the value of the toggled boolean in `path`.
        Note that when sending legacy path syntax, If `path` doesn't exist or the value at `path` isn't a boolean, an error is raised.
        For more information about the returned type, see `TJsonResponse`.

    Examples:
        >>> from glide import json as redisJson
        >>> import json
        >>> await redisJson.set(client, "doc", "$", json.dumps({"bool": True, "nested": {"bool": False, "nested": {"bool": 10}}}))
            'OK'
        >>> await redisJson.toggle(client, "doc", "$.bool")
            [False, True, None]  # Indicates successful toggling of the Boolean values at path '$.bool' in the key stored at `doc`.
        >>> await redisJson.toggle(client, "doc", "bool")
            True  # Indicates successful toggling of the Boolean value at path 'bool' in the key stored at `doc`.
        >>> json.loads(await redisJson.get(client, "doc", "$"))
            [{"bool": True, "nested": {"bool": True, "nested": {"bool": 10}}}] # The updated JSON value in the key stored at `doc`.
    """

    return cast(
        TJsonResponse[bool],
        await client.custom_command(["JSON.TOGGLE", key, path]),
    )
