# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

import random
from typing import AsyncGenerator, List, Optional, Union

import pytest
from glide.config import (
    ClusterClientConfiguration,
    NodeAddress,
    ProtocolVersion,
    RedisClientConfiguration,
    RedisCredentials,
)
from glide.logger import Level as logLevel
from glide.logger import Logger
from glide.redis_client import RedisClient, RedisClusterClient, TRedisClient
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
        "--mock-pubsub",
        default=True,
        action="store_true",
        help="Use PUBUSB mock implementation, defaults to `%(default)s`",
    )


@pytest.fixture(autouse=True, scope="session")
def call_before_all_pytests(request):
    """
    Called after the Session object has been created and
    before performing collection and entering the run test loop.
    """
    tls = request.config.getoption("--tls")
    load_module = request.config.getoption("--load-module")
    pytest.redis_cluster = RedisCluster(tls, True, load_module=load_module)
    pytest.standalone_cluster = RedisCluster(tls, False, 1, 1, load_module=load_module)


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


@pytest.fixture()
async def redis_client(
    request, cluster_mode: bool, protocol: ProtocolVersion
) -> AsyncGenerator[TRedisClient, None]:
    "Get async socket client for tests"
    client = await create_client(request, cluster_mode, protocol=protocol)
    yield client
    await client.close()


async def create_client(
    request,
    cluster_mode: bool,
    credentials: Optional[RedisCredentials] = None,
    database_id: int = 0,
    addresses: Optional[List[NodeAddress]] = None,
    client_name: Optional[str] = None,
    protocol: ProtocolVersion = ProtocolVersion.RESP3,
) -> Union[RedisClient, RedisClusterClient]:
    # Create async socket client
    use_tls = request.config.getoption("--tls")
    if cluster_mode:
        assert type(pytest.redis_cluster) is RedisCluster
        assert database_id == 0
        seed_nodes = random.sample(pytest.redis_cluster.nodes_addr, k=3)
        cluster_config = ClusterClientConfiguration(
            addresses=seed_nodes if addresses is None else addresses,
            use_tls=use_tls,
            credentials=credentials,
            client_name=client_name,
            protocol=protocol,
        )
        return await RedisClusterClient.create(cluster_config)
    else:
        assert type(pytest.standalone_cluster) is RedisCluster
        config = RedisClientConfiguration(
            addresses=(
                pytest.standalone_cluster.nodes_addr if addresses is None else addresses
            ),
            use_tls=use_tls,
            credentials=credentials,
            database_id=database_id,
            client_name=client_name,
            protocol=protocol,
        )
        return await RedisClient.create(config)
