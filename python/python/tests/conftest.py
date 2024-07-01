# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import random
from typing import AsyncGenerator, List, Optional, Union

import pytest
from glide.config import (
    ClusterClientConfiguration,
    GlideClientConfiguration,
    NodeAddress,
    ProtocolVersion,
    RedisCredentials,
)
from glide.glide_client import GlideClient, GlideClusterClient, TGlideClient
from glide.logger import Level as logLevel
from glide.logger import Logger
from tests.utils.cluster import RedisCluster

DEFAULT_HOST = "localhost"
DEFAULT_PORT = 6379
DEFAULT_TEST_LOG_LEVEL = logLevel.WARN

Logger.set_logger_config(DEFAULT_TEST_LOG_LEVEL)


def pytest_addoption(parser):
    parser.addoption(
        "--host",
        default=DEFAULT_HOST,
        action="store",
        help="Redis host endpoint, defaults to `%(default)s`",
    )

    parser.addoption(
        "--port",
        default=DEFAULT_PORT,
        action="store",
        help="Redis port, defaults to `%(default)s`",
    )

    parser.addoption(
        "--tls",
        default=False,
        action="store_true",
        help="TLS enabled, defaults to `%(default)s`",
    )

    parser.addoption(
        "--no-tls",
        dest="tls",
        action="store_false",
        help="TLS disabled, defaults to `%(default)s`",
    )

    parser.addoption(
        "--load-module",
        action="append",
        help="""Load additional Redis modules (provide full path for the module's shared library).
            Use multiple times for multiple modules.
            Example:
            pytest --load-module=/path/to/module1.so --load-module=/path/to/module2.so""",
        default=[],
    )

    parser.addoption(
        "--cluster-endpoints",
        action="store",
        help="""Comma-separated list of cluster endpoints for standalone cluster in the format host1:port1,host2:port2,...
            Note: The cluster will be flashed between tests.
            Example:
                pytest --asyncio-mode=auto --cluster-endpoints=127.0.0.1:6379
                pytest --asyncio-mode=auto --cluster-endpoints=127.0.0.1:6379,127.0.0.1:6380
            """,
        default=None,
    )

    parser.addoption(
        "--standalone-endpoints",
        action="store",
        help="""Comma-separated list of cluster endpoints for cluster mode cluster in the format host1:port1,host2:port2,...
            Note: The cluster will be flashed between tests.
            Example:
                pytest --asyncio-mode=auto --standalone-endpoints=127.0.0.1:6379
                pytest --asyncio-mode=auto --standalone-endpoints=127.0.0.1:6379,127.0.0.1:6380
            """,
        default=None,
    )


def parse_endpoints(endpoints_str: str) -> List[List[str]]:
    """
    Parse the endpoints string into a list of lists containing host and port.
    """
    try:
        endpoints = [endpoint.split(":") for endpoint in endpoints_str.split(",")]
        for endpoint in endpoints:
            if len(endpoint) != 2:
                raise ValueError(
                    "Each endpoint should be in the format 'host:port'.\nEndpoints should be separated by commas."
                )
            host, port = endpoint
            if not host or not port.isdigit():
                raise ValueError(
                    "Both host and port should be specified and port should be a valid integer."
                )
        return endpoints
    except ValueError as e:
        raise ValueError("Invalid endpoints format: " + str(e))


def create_clusters(tls, load_module, cluster_endpoints, standalone_endpoints):
    """
    Create Redis clusters based on the provided options.
    """
    if cluster_endpoints or standalone_endpoints:
        # Endpoints were passed by the caller, not creating clusters internally
        if cluster_endpoints:
            cluster_endpoints = parse_endpoints(cluster_endpoints)
            pytest.redis_cluster = RedisCluster(tls=tls, addresses=cluster_endpoints)
        if standalone_endpoints:
            standalone_endpoints = parse_endpoints(standalone_endpoints)
            pytest.standalone_cluster = RedisCluster(
                tls=tls, addresses=standalone_endpoints
            )
    else:
        # No endpoints were provided, create new clusters
        pytest.redis_cluster = RedisCluster(
            tls=tls,
            cluster_mode=True,
            load_module=load_module,
            addresses=cluster_endpoints,
        )
        pytest.standalone_cluster = RedisCluster(
            tls=tls,
            cluster_mode=False,
            shard_count=1,
            replica_count=1,
            load_module=load_module,
            addresses=standalone_endpoints,
        )


@pytest.fixture(autouse=True, scope="session")
def call_before_all_pytests(request):
    """
    Called after the Session object has been created and
    before performing collection and entering the run test loop.
    """
    tls = request.config.getoption("--tls")
    load_module = request.config.getoption("--load-module")
    cluster_endpoints = request.config.getoption("--cluster-endpoints")
    standalone_endpoints = request.config.getoption("--standalone-endpoints")

    create_clusters(tls, load_module, cluster_endpoints, standalone_endpoints)


def pytest_sessionfinish(session, exitstatus):
    """
    Called after whole test run finished, right before
    returning the exit status to the system.
    """
    try:
        del pytest.redis_cluster
    except AttributeError:
        # redis_cluster was not set, skip deletion
        pass

    try:
        del pytest.standalone_cluster
    except AttributeError:
        # standalone_cluster was not set, skip deletion
        pass


def pytest_collection_modifyitems(config, items):
    """
    Modify collected test items.

    This function checks if cluster or standalone endpoints are provided. If so, it checks if the test requires
    cluster mode and skips it accordingly.
    """
    for item in items:
        if config.getoption("--cluster-endpoints") or config.getoption(
            "--standalone-endpoints"
        ):
            if "cluster_mode" in item.fixturenames:
                cluster_mode_value = item.callspec.params.get("cluster_mode", None)
                if cluster_mode_value is True and not config.getoption(
                    "--cluster-endpoints"
                ):
                    item.add_marker(
                        pytest.mark.skip(
                            reason="Test skipped because cluster_mode=True and cluster endpoints weren't provided"
                        )
                    )
                elif cluster_mode_value is False and not config.getoption(
                    "--standalone-endpoints"
                ):
                    item.add_marker(
                        pytest.mark.skip(
                            reason="Test skipped because cluster_mode=False and standalone endpoints weren't provided"
                        )
                    )


@pytest.fixture()
async def redis_client(
    request, cluster_mode: bool, protocol: ProtocolVersion
) -> AsyncGenerator[TGlideClient, None]:
    "Get async socket client for tests"
    client = await create_client(request, cluster_mode, protocol=protocol)
    yield client
    await test_teardown(request, cluster_mode, protocol)
    await client.close()


async def create_client(
    request,
    cluster_mode: bool,
    credentials: Optional[RedisCredentials] = None,
    database_id: int = 0,
    addresses: Optional[List[NodeAddress]] = None,
    client_name: Optional[str] = None,
    protocol: ProtocolVersion = ProtocolVersion.RESP3,
    timeout: Optional[int] = None,
    cluster_mode_pubsub: Optional[
        ClusterClientConfiguration.PubSubSubscriptions
    ] = None,
    standalone_mode_pubsub: Optional[
        GlideClientConfiguration.PubSubSubscriptions
    ] = None,
) -> Union[GlideClient, GlideClusterClient]:
    # Create async socket client
    use_tls = request.config.getoption("--tls")
    if cluster_mode:
        assert type(pytest.redis_cluster) is RedisCluster
        assert database_id == 0
        k = min(3, len(pytest.redis_cluster.nodes_addr))
        seed_nodes = random.sample(pytest.redis_cluster.nodes_addr, k=k)
        cluster_config = ClusterClientConfiguration(
            addresses=seed_nodes if addresses is None else addresses,
            use_tls=use_tls,
            credentials=credentials,
            client_name=client_name,
            protocol=protocol,
            request_timeout=timeout,
            pubsub_subscriptions=cluster_mode_pubsub,
        )
        return await GlideClusterClient.create(cluster_config)
    else:
        assert type(pytest.standalone_cluster) is RedisCluster
        config = GlideClientConfiguration(
            addresses=(
                pytest.standalone_cluster.nodes_addr if addresses is None else addresses
            ),
            use_tls=use_tls,
            credentials=credentials,
            database_id=database_id,
            client_name=client_name,
            protocol=protocol,
            request_timeout=timeout,
            pubsub_subscriptions=standalone_mode_pubsub,
        )
        return await GlideClient.create(config)


async def test_teardown(request, cluster_mode: bool, protocol: ProtocolVersion):
    """
    Perform teardown tasks such as flushing all data from the cluster.

    We create a new client here because some tests load lots of data to the cluster,
    which might cause the client to time out during flushing. Therefore, we create
    a client with a custom timeout to ensure the operation completes successfully.
    """
    client = await create_client(request, cluster_mode, protocol=protocol, timeout=2000)
    await client.custom_command(["FLUSHALL"])
    await client.close()
