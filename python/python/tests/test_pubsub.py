import asyncio
import fnmatch
from typing import Any, Callable, Dict, List, Optional, Set, Tuple, cast

import pytest
from glide.async_commands.core import CoreCommands
from glide.constants import TResult
from glide.protobuf.redis_request_pb2 import RequestType
from glide.redis_client import RedisClient, RedisClusterClient
from glide.routes import Route
from tests.conftest import create_client
from typing_extensions import Self

MessagesByExactChannel = Dict[str, Dict[str, int]]  # channel -> {msg -> cnt}
MessagesByChannelAndPattern = Dict[
    Tuple[str, Optional[str]], Dict[str, int]
]  # (channel, pattern) -> {msg -> cnt}
SubscriptionsByMode = Dict[
    CoreCommands.ChannelModes, Set[str]
]  # subscription_type -> {channel_or_pattern}


@pytest.mark.asyncio
class TestPubSub:

    class RedisClientPubSubMock(CoreCommands):
        """Only PUBSUB methods are implemented, calling other base methods is not supported"""

        SubsByClient = Dict[
            "TestPubSub.RedisClientPubSubMock",
            Tuple[Callable[[CoreCommands.PubSubMsg], None], Optional[Any]],
        ]
        SubsByChannelMode = Dict[
            CoreCommands.ChannelModes, Dict[str, SubsByClient]
        ]  # channel -> {pattern_or_channel -> {client -> (callback, context)}}

        subs: SubsByChannelMode = {
            CoreCommands.ChannelModes.Sharded: {},
            CoreCommands.ChannelModes.Pattern: {},
            CoreCommands.ChannelModes.Exact: {},
        }

        async def _execute_command(
            self,
            request_type: RequestType.ValueType,
            args: List[str],
            route: Optional[Route] = ...,
        ) -> TResult:
            raise NotImplementedError()

        async def _execute_transaction(
            self,
            commands: List[Tuple[RequestType.ValueType, List[str]]],
            route: Optional[Route] = None,
        ) -> List[TResult]:
            raise NotImplementedError()

        async def _execute_script(
            self,
            hash: str,
            keys: Optional[List[str]] = None,
            args: Optional[List[str]] = None,
            route: Optional[Route] = None,
        ) -> TResult:
            raise NotImplementedError()

        async def subscribe(
            self,
            channels_or_patterns: Set[str],
            channel_mode: CoreCommands.ChannelModes,
            callback: Callable[[CoreCommands.PubSubMsg], None],
            context: Optional[Any],
        ) -> None:
            for channel_or_pattern in channels_or_patterns:
                clients_for_channel_and_pattern = TestPubSub.RedisClientPubSubMock.subs[
                    channel_mode
                ].setdefault(channel_or_pattern, {})
                clients_for_channel_and_pattern[self] = callback, context

        async def publish(
            self, message: str, channel: str, sharded: bool = False
        ) -> int:

            publish_cnt = 0

            def exact_channel(mode: CoreCommands.ChannelModes) -> int:
                clients = TestPubSub.RedisClientPubSubMock.subs[mode].get(channel, {})
                publish_cnt = 0
                for _, (callback, context) in clients.items():
                    msg = CoreCommands.PubSubMsg(
                        message=message, channel=channel, pattern=None, context=context
                    )
                    callback(msg)
                    publish_cnt += 1
                return publish_cnt

            if sharded:
                # go over all exact sharded subscriptions
                publish_cnt = exact_channel(CoreCommands.ChannelModes.Sharded)
            else:
                # go over all exact unsharded subscriptions
                publish_cnt = exact_channel(CoreCommands.ChannelModes.Exact)

                # go over patterns
                for pattern, clients in TestPubSub.RedisClientPubSubMock.subs[
                    CoreCommands.ChannelModes.Pattern
                ].items():
                    if fnmatch.filter(names=[channel], pat=pattern):
                        for _, (callback, context) in clients.items():
                            msg = CoreCommands.PubSubMsg(
                                message=message,
                                channel=channel,
                                pattern=pattern,
                                context=context,
                            )
                            callback(msg)
                            publish_cnt += 1

            return publish_cnt

        async def unsubscribe(
            self,
            channels_or_patterns: Set[str],
            channel_mode: CoreCommands.ChannelModes,
        ) -> Set[str]:
            unsubscribed: Set[str] = set()
            for channel_or_pattern in channels_or_patterns:
                clients_for_channel_and_pattern = TestPubSub.RedisClientPubSubMock.subs[
                    channel_mode
                ].get(channel_or_pattern, {})
                if clients_for_channel_and_pattern.pop(self, None):
                    unsubscribed.add(channel_or_pattern)
                    if not len(clients_for_channel_and_pattern):
                        del TestPubSub.RedisClientPubSubMock.subs[channel_mode][
                            channel_or_pattern
                        ]

            return unsubscribed

    class PubSubClient:
        def __init__(
            self,
            subsciptions: SubscriptionsByMode,
            sharded_messages_to_publish: MessagesByExactChannel,
            unsharded_messages_to_publish: MessagesByExactChannel,
            cluster_mode: bool,
            request,
        ):
            self.messages_received: MessagesByChannelAndPattern = {}
            self.messages_to_receive: MessagesByChannelAndPattern = {}

            self.sharded_messages_to_publish: MessagesByExactChannel = (
                sharded_messages_to_publish
            )
            self.unsharded_messages_to_publish: MessagesByExactChannel = (
                unsharded_messages_to_publish
            )

            self.subsciptions = subsciptions

            self.cluster_mode = cluster_mode
            self.request = request

            self.redis_client: CoreCommands | None = None

        @staticmethod
        def handle_new_message(message: CoreCommands.PubSubMsg) -> None:
            key = (message.channel, message.pattern)
            client: TestPubSub.PubSubClient = cast(
                TestPubSub.PubSubClient, message.context
            )
            channel_and_pattern_msgs = client.messages_received.setdefault(key, {})
            channel_and_pattern_msgs[message.message] = (
                channel_and_pattern_msgs.get(message.message, 0) + 1
            )

        async def connect_and_subscribe(self, use_mock: bool) -> Self:
            if not use_mock:
                self.redis_client = await create_client(self.request, self.cluster_mode)
            else:
                self.redis_client = TestPubSub.RedisClientPubSubMock()

            for channel_mode, channels_or_patterns in self.subsciptions.items():
                await self.redis_client.subscribe(
                    channels_or_patterns=channels_or_patterns,
                    channel_mode=channel_mode,
                    callback=TestPubSub.PubSubClient.handle_new_message,
                    context=self,
                )
            return self

        def assert_client(self) -> None:
            # TODO print both in case of failure
            assert self.messages_received == self.messages_to_receive

        def calculate_expected_messages(
            self, all_clients: List["TestPubSub.PubSubClient"]
        ) -> None:
            for sender in all_clients:
                for (
                    sharded_channel,
                    sharded_channel_messages,
                ) in sender.sharded_messages_to_publish.items():
                    # should receive by sctrict sub?
                    if sharded_channel in self.subsciptions.get(
                        CoreCommands.ChannelModes.Sharded, {}
                    ):
                        channel_and_pattern: Tuple[str, Optional[str]] = (
                            sharded_channel,
                            None,
                        )
                        messages_for_channel_and_pattern = (
                            self.messages_to_receive.setdefault(channel_and_pattern, {})
                        )
                        for message, cnt in sharded_channel_messages.items():
                            messages_for_channel_and_pattern[message] = (
                                messages_for_channel_and_pattern.get(message, 0) + cnt
                            )

                for (
                    unsharded_channel,
                    unsharded_channel_messages,
                ) in sender.unsharded_messages_to_publish.items():
                    # should receive by sctrict sub?
                    if unsharded_channel in self.subsciptions.get(
                        CoreCommands.ChannelModes.Exact, {}
                    ):
                        channel_and_pattern = unsharded_channel, None
                        messages_for_channel_and_pattern = (
                            self.messages_to_receive.setdefault(channel_and_pattern, {})
                        )
                        for message, cnt in unsharded_channel_messages.items():
                            messages_for_channel_and_pattern[message] = (
                                messages_for_channel_and_pattern.get(message, 0) + cnt
                            )

                    # should receive by glob pattern?
                    for glob_pattern in self.subsciptions.get(
                        CoreCommands.ChannelModes.Pattern, {}
                    ):
                        if fnmatch.filter(names=[unsharded_channel], pat=glob_pattern):
                            channel_and_pattern = unsharded_channel, glob_pattern
                            messages_for_channel_and_pattern = (
                                self.messages_to_receive.setdefault(
                                    channel_and_pattern, {}
                                )
                            )
                            for message, cnt in unsharded_channel_messages.items():
                                messages_for_channel_and_pattern[message] = (
                                    messages_for_channel_and_pattern.get(message, 0)
                                    + cnt
                                )

        async def publish_messages(self) -> None:
            assert self.redis_client is not None
            for (
                sharded_channel,
                sharded_messages_to_publish,
            ) in self.sharded_messages_to_publish.items():
                for message, cnt in sharded_messages_to_publish.items():
                    for i in range(cnt):
                        await self.redis_client.publish(
                            message=message, channel=sharded_channel, sharded=True
                        )

            for (
                unsharded_channel,
                unsharded_messages_to_publish,
            ) in self.unsharded_messages_to_publish.items():
                for message, cnt in unsharded_messages_to_publish.items():
                    for i in range(cnt):
                        await self.redis_client.publish(
                            message=message, channel=unsharded_channel, sharded=False
                        )

    @staticmethod
    async def publish_and_assert(all_clients: List["TestPubSub.PubSubClient"]):
        for client in all_clients:
            await client.publish_messages()

        # TODO - Wait for how long?
        await asyncio.sleep(1)

        for client in all_clients:
            client.calculate_expected_messages(all_clients=all_clients)
            client.assert_client()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("sharded_pubsub", [True, False])
    async def test_pubsub_basic(self, request, cluster_mode, sharded_pubsub):
        CHANNEL_A_NAME = "test-channel-a"

        callback_a_called = False

        redis_client: (
            RedisClient | RedisClusterClient | TestPubSub.RedisClientPubSubMock | None
        ) = None
        if not request.config.getoption("--mock-pubsub"):
            redis_client = await create_client(request, cluster_mode)
        else:
            redis_client = TestPubSub.RedisClientPubSubMock()

        def callback_a(message: CoreCommands.PubSubMsg) -> None:
            nonlocal callback_a_called
            nonlocal redis_client

            callback_a_called = True
            assert message.context == redis_client
            assert message.channel == CHANNEL_A_NAME
            assert not message.pattern

        await redis_client.subscribe(
            channels_or_patterns={CHANNEL_A_NAME},
            channel_mode=(
                CoreCommands.ChannelModes.Sharded
                if sharded_pubsub
                else CoreCommands.ChannelModes.Exact
            ),
            callback=callback_a,
            context=redis_client,
        )

        await redis_client.publish(
            message="hi", channel=CHANNEL_A_NAME, sharded=sharded_pubsub
        )
        await asyncio.sleep(1)
        assert callback_a_called

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("sharded_pubsub", [True, False])
    async def test_pubsub_exact(self, request, cluster_mode, sharded_pubsub):
        """
        PUBSUB basic happy case using exact channel names
        Tests that clients receive all the messages using exact subscribe to a single channel
        """

        CHANNEL_NAME = "test-channel"
        CLIENTS_COUNT = 2
        MESSAGES_BY_CLIENT = {
            id: {
                CHANNEL_NAME: {
                    msg + str(id): 1 for msg in ["foo_from_", "bar_from_", "baz_from_"]
                }
            }
            for id in range(CLIENTS_COUNT)
        }

        all_clients: List[TestPubSub.PubSubClient] = []
        for id in range(CLIENTS_COUNT):
            all_clients.append(
                TestPubSub.PubSubClient(
                    subsciptions={CoreCommands.ChannelModes.Exact: {CHANNEL_NAME}},
                    sharded_messages_to_publish=(
                        MESSAGES_BY_CLIENT[id] if sharded_pubsub else {}
                    ),
                    unsharded_messages_to_publish=(
                        MESSAGES_BY_CLIENT[id] if not sharded_pubsub else {}
                    ),
                    cluster_mode=cluster_mode,
                    request=request,
                )
            )
            await all_clients[-1].connect_and_subscribe(
                request.config.getoption("--mock-pubsub")
            )

        await TestPubSub.publish_and_assert(all_clients)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_pattern(self, request, cluster_mode):
        """
        PUBSUB using patterns happy case
        Tests that clients receive all the messages using pattern subscribe to a single channel
        """

        CLIENTS_COUNT = 2
        CHANNEL_NAME = "test-channel"
        CHANNEL_PATTERN = "test-*"
        MESSAGES_BY_CLIENT = {
            id: {
                CHANNEL_NAME: {
                    msg + str(id): 1 for msg in ["foo_from_", "bar_from_", "baz_from_"]
                }
            }
            for id in range(CLIENTS_COUNT)
        }

        all_clients: List[TestPubSub.PubSubClient] = []
        for id in range(CLIENTS_COUNT):
            all_clients.append(
                TestPubSub.PubSubClient(
                    subsciptions={CoreCommands.ChannelModes.Pattern: {CHANNEL_PATTERN}},
                    sharded_messages_to_publish={},
                    unsharded_messages_to_publish=MESSAGES_BY_CLIENT[id],
                    cluster_mode=cluster_mode,
                    request=request,
                )
            )
            await all_clients[-1].connect_and_subscribe(
                request.config.getoption("--mock-pubsub")
            )

        await TestPubSub.publish_and_assert(all_clients)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_pattern_and_exact(self, request, cluster_mode):
        """Tests that subscription to exact channel and it's pattern triggers reception of 2 messages"""

        CLIENTS_COUNT = 5
        CHANNEL_NAME = "test-channel"
        CHANNEL_PATTERN = "test-*"
        MESSAGES_BY_CLIENT = {
            id: {
                CHANNEL_NAME: {
                    msg + str(id): 1 for msg in ["foo_from_", "bar_from_", "baz_from_"]
                }
            }
            for id in range(CLIENTS_COUNT)
        }

        all_clients: List[TestPubSub.PubSubClient] = []
        for id in range(CLIENTS_COUNT):
            all_clients.append(
                TestPubSub.PubSubClient(
                    subsciptions={
                        CoreCommands.ChannelModes.Pattern: {CHANNEL_PATTERN},
                        CoreCommands.ChannelModes.Exact: {CHANNEL_NAME},
                    },
                    sharded_messages_to_publish={},
                    unsharded_messages_to_publish=MESSAGES_BY_CLIENT[id],
                    cluster_mode=cluster_mode,
                    request=request,
                )
            )
            await all_clients[-1].connect_and_subscribe(
                request.config.getoption("--mock-pubsub")
            )

        await TestPubSub.publish_and_assert(all_clients)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_compound_pattern(self, request, cluster_mode):
        """Tests that subscription to compound patterns triggers reception of 2 messages (one for each pattern)"""

        CLIENTS_COUNT = 5
        CHANNEL_NAME = "test-channel"
        CHANNEL_PATTERNS = {"test-*", "test?channel"}
        MESSAGES_BY_CLIENT = {
            id: {
                CHANNEL_NAME: {
                    msg + str(id): 1 for msg in ["foo_from_", "bar_from_", "baz_from_"]
                }
            }
            for id in range(CLIENTS_COUNT)
        }

        all_clients: List[TestPubSub.PubSubClient] = []
        for id in range(CLIENTS_COUNT):
            all_clients.append(
                TestPubSub.PubSubClient(
                    subsciptions={CoreCommands.ChannelModes.Pattern: CHANNEL_PATTERNS},
                    sharded_messages_to_publish={},
                    unsharded_messages_to_publish=MESSAGES_BY_CLIENT[id],
                    cluster_mode=cluster_mode,
                    request=request,
                )
            )
            await all_clients[-1].connect_and_subscribe(
                request.config.getoption("--mock-pubsub")
            )

        await TestPubSub.publish_and_assert(all_clients)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("sharded_pubsub", [True, False])
    async def test_pubsub_channel_boundary_exact(
        self, request, cluster_mode, sharded_pubsub
    ):
        """Tests that messages do not cross channel boundaries by exact subsctiption"""

        CLIENTS_COUNT_PER_CHANNEL = 5
        CHANNEL_NAMES = {"test-channel-1", "test-channel-2"}
        MESSAGES_BY_CHANNEL = {
            channel: {
                id: {
                    channel: {
                        "on_" + channel + "_" + msg + str(id): 1
                        for msg in ["foo_from_", "bar_from_", "baz_from_"]
                    }
                }
                for id in range(CLIENTS_COUNT_PER_CHANNEL)
            }
            for channel in CHANNEL_NAMES
        }

        all_clients: List[TestPubSub.PubSubClient] = []
        for channel in CHANNEL_NAMES:
            for id in range(CLIENTS_COUNT_PER_CHANNEL):
                all_clients.append(
                    TestPubSub.PubSubClient(
                        subsciptions={CoreCommands.ChannelModes.Exact: {channel}},
                        sharded_messages_to_publish=(
                            MESSAGES_BY_CHANNEL[channel][id] if sharded_pubsub else {}
                        ),
                        unsharded_messages_to_publish=(
                            MESSAGES_BY_CHANNEL[channel][id]
                            if not sharded_pubsub
                            else {}
                        ),
                        cluster_mode=cluster_mode,
                        request=request,
                    )
                )
                await all_clients[-1].connect_and_subscribe(
                    request.config.getoption("--mock-pubsub")
                )

        await TestPubSub.publish_and_assert(all_clients)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_channel_boundary_pattern(self, request, cluster_mode):
        """Tests that messages do not cross channel boundaries by pattern subsctiption"""

        CLIENTS_COUNT_PER_CHANNEL = 5
        CHANNEL_NAMES = ["test-channel-1", "test-channel-2"]
        CHANNEL_PATTERNS = ["test?channel-1", "test?channel-2"]
        MESSAGES_BY_CHANNEL = {
            channel: {
                id: {
                    channel: {
                        "on_" + channel + "_" + msg + str(id): 1
                        for msg in ["foo_from_", "bar_from_", "baz_from_"]
                    }
                }
                for id in range(CLIENTS_COUNT_PER_CHANNEL)
            }
            for channel in CHANNEL_NAMES
        }

        all_clients: List[TestPubSub.PubSubClient] = []
        for channel_id in range(len(CHANNEL_NAMES)):
            for id in range(CLIENTS_COUNT_PER_CHANNEL):
                all_clients.append(
                    TestPubSub.PubSubClient(
                        subsciptions={
                            CoreCommands.ChannelModes.Pattern: {
                                CHANNEL_PATTERNS[channel_id]
                            }
                        },
                        sharded_messages_to_publish={},
                        unsharded_messages_to_publish=MESSAGES_BY_CHANNEL[
                            CHANNEL_NAMES[channel_id]
                        ][id],
                        cluster_mode=cluster_mode,
                        request=request,
                    )
                )
                await all_clients[-1].connect_and_subscribe(
                    request.config.getoption("--mock-pubsub")
                )

        await TestPubSub.publish_and_assert(all_clients)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("sharded_pubsub", [True, False])
    async def test_pubsub_callbacks_per_channel(
        self, request, cluster_mode, sharded_pubsub
    ):
        """Tests that callbacks are per channel"""

        CHANNEL_A_NAME = "test-channel-a"
        CHANNEL_B_NAME = "test-channel-b"

        callback_a_called = False
        callback_a_context: str | None = None

        callback_b_called = False
        callback_b_context: str | None = None

        def callback_a(message: CoreCommands.PubSubMsg) -> None:
            nonlocal callback_a_called
            nonlocal callback_a_context

            callback_a_called = True
            callback_a_context = message.context

        def callback_b(message: CoreCommands.PubSubMsg) -> None:
            nonlocal callback_b_called
            nonlocal callback_b_context

            callback_b_called = True
            callback_b_context = message.context

        redis_client: (
            RedisClient | RedisClusterClient | TestPubSub.RedisClientPubSubMock | None
        ) = None
        if not request.config.getoption("--mock-pubsub"):
            redis_client = await create_client(request, cluster_mode)
        else:
            redis_client = TestPubSub.RedisClientPubSubMock()

        await redis_client.subscribe(
            channels_or_patterns={CHANNEL_A_NAME},
            channel_mode=(
                CoreCommands.ChannelModes.Sharded
                if sharded_pubsub
                else CoreCommands.ChannelModes.Exact
            ),
            callback=callback_a,
            context=CHANNEL_A_NAME,
        )

        await redis_client.subscribe(
            channels_or_patterns={CHANNEL_B_NAME},
            channel_mode=(
                CoreCommands.ChannelModes.Sharded
                if sharded_pubsub
                else CoreCommands.ChannelModes.Exact
            ),
            callback=callback_b,
            context=CHANNEL_B_NAME,
        )

        await redis_client.publish(
            message="hi", channel=CHANNEL_A_NAME, sharded=sharded_pubsub
        )
        await asyncio.sleep(1)

        assert callback_a_called
        assert callback_a_context == CHANNEL_A_NAME
        assert not callback_b_called
        assert not callback_b_context

        callback_a_called = False
        callback_a_context = None
        await redis_client.publish(
            message="hi", channel=CHANNEL_B_NAME, sharded=sharded_pubsub
        )
        await asyncio.sleep(1)

        assert callback_b_called
        assert callback_b_context == CHANNEL_B_NAME
        assert not callback_a_called
        assert not callback_a_context

        callback_b_called = False
        callback_b_context = None
        await redis_client.subscribe(
            channels_or_patterns={CHANNEL_B_NAME},
            channel_mode=(
                CoreCommands.ChannelModes.Sharded
                if sharded_pubsub
                else CoreCommands.ChannelModes.Exact
            ),
            callback=callback_a,
            context=CHANNEL_B_NAME,
        )
        await redis_client.publish(
            message="hi", channel=CHANNEL_B_NAME, sharded=sharded_pubsub
        )
        await asyncio.sleep(1)

        assert callback_a_called
        assert callback_a_context == CHANNEL_B_NAME
        assert not callback_b_called
        assert not callback_b_context

        callback_a_called = False
        callback_a_context = None
        await redis_client.publish(
            message="hi", channel="empy-channel", sharded=sharded_pubsub
        )
        await asyncio.sleep(1)

        assert not callback_a_called
        assert not callback_a_context
        assert not callback_b_called
        assert not callback_b_context

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_sharded_unsharded_do_not_cross(self, request, cluster_mode):
        """Tests not leakage between sharded and unsharded channels"""

        CHANNEL_NAME = "test-channel"
        CLIENTS_COUNT = 2
        MESSAGES_BY_CLIENT = {
            id: {
                CHANNEL_NAME: {
                    msg + str(id): 1 for msg in ["foo_from_", "bar_from_", "baz_from_"]
                }
            }
            for id in range(CLIENTS_COUNT)
        }

        all_clients: List[TestPubSub.PubSubClient] = []
        all_clients.append(
            await TestPubSub.PubSubClient(
                subsciptions={CoreCommands.ChannelModes.Exact: {CHANNEL_NAME}},
                sharded_messages_to_publish={},
                unsharded_messages_to_publish=MESSAGES_BY_CLIENT[0],
                cluster_mode=cluster_mode,
                request=request,
            ).connect_and_subscribe(request.config.getoption("--mock-pubsub"))
        )

        all_clients.append(
            await TestPubSub.PubSubClient(
                subsciptions={CoreCommands.ChannelModes.Sharded: {CHANNEL_NAME}},
                sharded_messages_to_publish=MESSAGES_BY_CLIENT[1],
                unsharded_messages_to_publish={},
                cluster_mode=cluster_mode,
                request=request,
            ).connect_and_subscribe(request.config.getoption("--mock-pubsub"))
        )

        await TestPubSub.publish_and_assert(all_clients)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("sharded_pubsub", [True, False])
    async def test_pubsub_large_messages(self, request, cluster_mode, sharded_pubsub):
        """Tests that large messages (1MB) are supported correctly"""

        CHANNEL_NAME = "test-channel"
        CLIENTS_COUNT = 5
        MESSAGES_BY_CLIENT = {
            id: {
                CHANNEL_NAME: {
                    msg + str(id) + "_" + ("0" * 1024 * 1024): 1
                    for msg in ["foo_from_", "bar_from_", "baz_from_"]
                }
            }
            for id in range(CLIENTS_COUNT)
        }

        all_clients: List[TestPubSub.PubSubClient] = []
        for id in range(CLIENTS_COUNT):
            all_clients.append(
                TestPubSub.PubSubClient(
                    subsciptions={CoreCommands.ChannelModes.Exact: {CHANNEL_NAME}},
                    sharded_messages_to_publish=(
                        MESSAGES_BY_CLIENT[id] if sharded_pubsub else {}
                    ),
                    unsharded_messages_to_publish=(
                        MESSAGES_BY_CLIENT[id] if not sharded_pubsub else {}
                    ),
                    cluster_mode=cluster_mode,
                    request=request,
                )
            )
            await all_clients[-1].connect_and_subscribe(
                request.config.getoption("--mock-pubsub")
            )

        await TestPubSub.publish_and_assert(all_clients)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("sharded_pubsub", [True, False])
    async def test_pubsub_many_clients(self, request, cluster_mode, sharded_pubsub):
        """Tests that many clients (1K) are supported correctly"""

        CHANNEL_NAME = "test-channel"
        CLIENTS_COUNT = 1024
        MESSAGES_BY_CLIENT = {
            id: {
                CHANNEL_NAME: {
                    msg + str(id): 1 for msg in ["foo_from_", "bar_from_", "baz_from_"]
                }
            }
            for id in range(CLIENTS_COUNT)
        }

        all_clients: List[TestPubSub.PubSubClient] = []
        for id in range(CLIENTS_COUNT):
            all_clients.append(
                TestPubSub.PubSubClient(
                    subsciptions={CoreCommands.ChannelModes.Exact: {CHANNEL_NAME}},
                    sharded_messages_to_publish=(
                        MESSAGES_BY_CLIENT[id] if sharded_pubsub else {}
                    ),
                    unsharded_messages_to_publish=(
                        MESSAGES_BY_CLIENT[id] if not sharded_pubsub else {}
                    ),
                    cluster_mode=cluster_mode,
                    request=request,
                )
            )
            await all_clients[-1].connect_and_subscribe(
                request.config.getoption("--mock-pubsub")
            )

        await TestPubSub.publish_and_assert(all_clients)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("sharded_pubsub", [True, False])
    async def test_pubsub_many_channels(self, request, cluster_mode, sharded_pubsub):
        """Tests that many channels (256) are supported correctly"""

        CLIENTS_COUNT_PER_CHANNEL = 4
        CHANNELS_COUNT = 256
        CHANNEL_NAMES = {"test-channel-" + str(id) for id in range(CHANNELS_COUNT)}
        MESSAGES_BY_CHANNEL = {
            channel: {
                id: {
                    channel: {
                        "on_" + channel + "_" + msg + str(id): 1
                        for msg in ["foo_from_", "bar_from_", "baz_from_"]
                    }
                }
                for id in range(CLIENTS_COUNT_PER_CHANNEL)
            }
            for channel in CHANNEL_NAMES
        }

        all_clients: List[TestPubSub.PubSubClient] = []
        for channel in CHANNEL_NAMES:
            for id in range(CLIENTS_COUNT_PER_CHANNEL):
                all_clients.append(
                    TestPubSub.PubSubClient(
                        subsciptions={CoreCommands.ChannelModes.Exact: {channel}},
                        sharded_messages_to_publish=(
                            MESSAGES_BY_CHANNEL[channel][id] if sharded_pubsub else {}
                        ),
                        unsharded_messages_to_publish=(
                            MESSAGES_BY_CHANNEL[channel][id]
                            if not sharded_pubsub
                            else {}
                        ),
                        cluster_mode=cluster_mode,
                        request=request,
                    )
                )
                await all_clients[-1].connect_and_subscribe(
                    request.config.getoption("--mock-pubsub")
                )

        await TestPubSub.publish_and_assert(all_clients)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("sharded_pubsub", [True, False])
    async def test_pubsub_unsubscribe(self, request, cluster_mode, sharded_pubsub):

        CHANNEL_A_NAME = "test-channel-a"

        callback_a_called = False
        callback_a_context: str | None = None

        def callback_a(message: CoreCommands.PubSubMsg) -> None:
            nonlocal callback_a_called
            nonlocal callback_a_context

            callback_a_called = True
            callback_a_context = message.context

        redis_client: (
            RedisClient | RedisClusterClient | TestPubSub.RedisClientPubSubMock | None
        ) = None
        if not request.config.getoption("--mock-pubsub"):
            redis_client = await create_client(request, cluster_mode)
        else:
            redis_client = TestPubSub.RedisClientPubSubMock()

        await redis_client.subscribe(
            channels_or_patterns={CHANNEL_A_NAME},
            channel_mode=(
                CoreCommands.ChannelModes.Sharded
                if sharded_pubsub
                else CoreCommands.ChannelModes.Exact
            ),
            callback=callback_a,
            context=CHANNEL_A_NAME,
        )

        await redis_client.publish(
            message="hi", channel=CHANNEL_A_NAME, sharded=sharded_pubsub
        )
        await asyncio.sleep(1)

        assert callback_a_called
        assert callback_a_context == CHANNEL_A_NAME

        callback_a_called = False
        callback_a_context = None

        assert await redis_client.unsubscribe(
            channels_or_patterns={CHANNEL_A_NAME},
            channel_mode=(
                CoreCommands.ChannelModes.Sharded
                if sharded_pubsub
                else CoreCommands.ChannelModes.Exact
            ),
        ), {CHANNEL_A_NAME}

        await redis_client.publish(
            message="hi", channel=CHANNEL_A_NAME, sharded=sharded_pubsub
        )
        await asyncio.sleep(1)

        assert not callback_a_called
        assert not callback_a_context
