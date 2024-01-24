import asyncio
from typing import Optional, Union

from glide import (
    AllNodes,
    BaseClientConfiguration,
    Logger,
    LogLevel,
    NodeAddress,
    RedisClient,
    RedisClusterClient,
)


def set_console_logger(level: LogLevel = LogLevel.WARN):
    Logger.set_logger_config(level)


def set_file_logger(level: LogLevel = LogLevel.WARN, file: Optional[str] = None):
    if file is None:
        from datetime import datetime, timezone

        curr_time = datetime.now(timezone.utc)
        curr_time_str = curr_time.strftime("%Y-%m-%dT%H:%M:%SZ")
        file = f"{curr_time_str}-glide.log"
    Logger.set_logger_config(level, file)


async def send_set_and_get(client: Union[RedisClient, RedisClusterClient]):
    set_response = await client.set("foo", "bar")
    print(f"Set response is = {set_response}")
    get_response = await client.get("foo")
    print(f"Get response is = {get_response}")


async def test_standalone_client(host: str = "localhost", port: int = 6379):
    # When in Redis is in standalone mode, add address of the primary node,
    # and any replicas you'd like to be able to read from.
    addresses = [NodeAddress(host, port)]
    # Check `RedisClientConfiguration/ClusterClientConfiguration` for additional options.
    config = BaseClientConfiguration(
        addresses=addresses,
        client_name="test_standalone_client"
        # if the server use TLS, you'll need to enable it. Otherwise the connection attempt will time out silently.
        # tls_mode=TlsMode.Auto
    )
    client = await RedisClient.create(config)

    # Send SET and GET
    await send_set_and_get(client)
    # Send PING to the primary node
    pong = await client.custom_command(["PING"])
    print(f"PONG response is = {pong}")


async def test_cluster_client(host: str = "localhost", port: int = 6379):
    # When in Redis is cluster mode, add address of any nodes, and the client will find all nodes in the cluster.
    addresses = [NodeAddress(host, port)]
    # Check `RedisClientConfiguration/ClusterClientConfiguration` for additional options.
    config = BaseClientConfiguration(
        addresses=addresses,
        client_name="test_cluster_client"
        # if the cluster nodes use TLS, you'll need to enable it. Otherwise the connection attempt will time out silently.
        # tls_mode=TlsMode.Auto
    )
    client = await RedisClusterClient.create(config)

    # Send SET and GET
    await send_set_and_get(client)
    # Send PING to all primaries (according to Redis's PING request_policy)
    pong = await client.custom_command(["PING"])
    print(f"PONG response is = {pong}")
    # Send INFO REPLICATION with routing option to all nodes
    info_repl_resps = await client.custom_command(["INFO", "REPLICATION"], AllNodes())
    print(f"INFO REPLICATION responses to all nodes are = {info_repl_resps}")


async def main():
    set_console_logger(LogLevel.DEBUG)
    set_file_logger(LogLevel.DEBUG)
    await test_standalone_client()
    await test_cluster_client()


if __name__ == "__main__":
    asyncio.run(main())
