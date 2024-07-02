# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

import asyncio
from enum import IntEnum
from typing import Any, Dict, List, Optional, Set, Tuple, Union, cast

import pytest
from glide.async_commands.core import CoreCommands
from glide.config import (
    ClusterClientConfiguration,
    GlideClientConfiguration,
    ProtocolVersion,
)
from glide.constants import OK
from glide.exceptions import ConfigurationError
from glide.glide_client import GlideClient, GlideClusterClient, TGlideClient
from tests.conftest import create_client
from tests.utils.utils import check_if_server_version_lt, get_random_string


class MethodTesting(IntEnum):
    """
    Enumeration for specifying the method of PUBSUB subscription.
    """

    Async = 0
    "Uses asynchronous subscription method."
    Sync = 1
    "Uses synchronous subscription method."
    Callback = 2
    "Uses callback-based subscription method."


async def create_two_clients(
    request,
    cluster_mode,
    pub_sub,
    pub_sub2: Optional[Any] = None,
    protocol: ProtocolVersion = ProtocolVersion.RESP3,
) -> Tuple[
    Union[GlideClient, GlideClusterClient], Union[GlideClient, GlideClusterClient]
]:
    """
    Sets 2 up clients for testing purposes.

    Args:
        request: pytest request for creating a client.
        cluster_mode: the cluster mode.
        pub_sub: pubsub configuration subscription for a client.
        pub_sub2: pubsub configuration subscription for a client.
        protocol: what protocol to use, used for the test: `test_pubsub_resp2_raise_an_error`.
    """
    cluster_mode_pubsub, standalone_mode_pubsub = None, None
    cluster_mode_pubsub2, standalone_mode_pubsub2 = None, None
    if cluster_mode:
        cluster_mode_pubsub = pub_sub
        cluster_mode_pubsub2 = pub_sub2
    else:
        standalone_mode_pubsub = pub_sub
        standalone_mode_pubsub2 = pub_sub2

    client = await create_client(
        request,
        cluster_mode=cluster_mode,
        cluster_mode_pubsub=cluster_mode_pubsub2,
        standalone_mode_pubsub=standalone_mode_pubsub2,
        protocol=protocol,
    )
    client2 = await create_client(
        request,
        cluster_mode=cluster_mode,
        cluster_mode_pubsub=cluster_mode_pubsub,
        standalone_mode_pubsub=standalone_mode_pubsub,
        protocol=protocol,
    )
    return client, client2


def decode_pubsub_msg(msg: Optional[CoreCommands.PubSubMsg]) -> CoreCommands.PubSubMsg:
    if not msg:
        return CoreCommands.PubSubMsg("", "", None)
    string_msg = cast(bytes, msg.message).decode()
    string_channel = cast(bytes, msg.channel).decode()
    string_pattern = cast(bytes, msg.pattern).decode() if msg.pattern else None
    decoded_msg = CoreCommands.PubSubMsg(string_msg, string_channel, string_pattern)
    return decoded_msg


async def get_message_by_method(
    method: MethodTesting,
    client: TGlideClient,
    messages: Optional[List[CoreCommands.PubSubMsg]] = None,
    index: Optional[int] = None,
):
    if method == MethodTesting.Async:
        return decode_pubsub_msg(await client.get_pubsub_message())
    elif method == MethodTesting.Sync:
        return decode_pubsub_msg(client.try_get_pubsub_message())
    assert messages and (index is not None)
    return decode_pubsub_msg(messages[index])


async def check_no_messages_left(
    method,
    client: TGlideClient,
    callback: Optional[List[Any]] = None,
    expected_callback_messages_count: int = 0,
):
    if method == MethodTesting.Async:
        # assert there are no messages to read
        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(client.get_pubsub_message(), timeout=3)
    elif method == MethodTesting.Sync:
        assert client.try_get_pubsub_message() is None
    else:
        assert callback is not None
        assert len(callback) == expected_callback_messages_count


def create_pubsub_subscription(
    cluster_mode,
    cluster_channels_and_patterns: Dict[
        ClusterClientConfiguration.PubSubChannelModes, Set[str]
    ],
    standalone_channels_and_patterns: Dict[
        GlideClientConfiguration.PubSubChannelModes, Set[str]
    ],
    callback=None,
    context=None,
):
    if cluster_mode:
        return ClusterClientConfiguration.PubSubSubscriptions(
            channels_and_patterns=cluster_channels_and_patterns,
            callback=callback,
            context=context,
        )
    return GlideClientConfiguration.PubSubSubscriptions(
        channels_and_patterns=standalone_channels_and_patterns,
        callback=callback,
        context=context,
    )


def new_message(msg: CoreCommands.PubSubMsg, context: Any):
    received_messages: List[CoreCommands.PubSubMsg] = context
    received_messages.append(msg)


@pytest.mark.asyncio
class TestPubSub:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_exact_happy_path(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
    ):
        """
        Tests the basic happy path for exact PUBSUB functionality.

        This test covers the basic PUBSUB flow using three different methods:
        Async, Sync, and Callback. It verifies that a message published to a
        specific channel is correctly received by a subscriber.
        """
        channel = get_random_string(10)
        message = get_random_string(5)

        callback, context = None, None
        callback_messages: List[CoreCommands.PubSubMsg] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Exact: {channel}},
            {GlideClientConfiguration.PubSubChannelModes.Exact: {channel}},
            callback=callback,
            context=context,
        )

        publishing_client, listening_client = await create_two_clients(
            request, cluster_mode, pub_sub
        )

        try:
            result = await publishing_client.publish(message, channel)
            if cluster_mode:
                assert result == 1
            # allow the message to propagate
            await asyncio.sleep(1)

            pubsub_msg = await get_message_by_method(
                method, listening_client, callback_messages, 0
            )

            assert pubsub_msg.message == message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern is None

            await check_no_messages_left(method, listening_client, callback_messages, 1)
        finally:
            if cluster_mode:
                # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
                # In cluster mode, we check how many subscriptions received the message
                # So to avoid flakiness, we make sure to unsubscribe from the channels
                await listening_client.custom_command(["UNSUBSCRIBE", channel])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_exact_happy_path_coexistence(
        self, request, cluster_mode: bool
    ):
        """
        Tests the coexistence of async and sync message retrieval methods in exact PUBSUB.

        This test covers the scenario where messages are published to a channel
        and received using both async and sync methods to ensure that both methods
        can coexist and function correctly.
        """
        channel = get_random_string(10)
        message = get_random_string(5)
        message2 = get_random_string(7)

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Exact: {channel}},
            {GlideClientConfiguration.PubSubChannelModes.Exact: {channel}},
        )

        publishing_client, listening_client = await create_two_clients(
            request, cluster_mode, pub_sub
        )

        try:
            for msg in [message, message2]:
                result = await publishing_client.publish(msg, channel)
                if cluster_mode:
                    assert result == 1

            # allow the message to propagate
            await asyncio.sleep(1)

            async_msg_res = await listening_client.get_pubsub_message()
            sync_msg_res = listening_client.try_get_pubsub_message()
            assert sync_msg_res
            async_msg = decode_pubsub_msg(async_msg_res)
            sync_msg = decode_pubsub_msg(sync_msg_res)

            assert async_msg.message in [message, message2]
            assert async_msg.channel == channel
            assert async_msg.pattern is None

            assert sync_msg.message in [message, message2]
            assert sync_msg.channel == channel
            assert sync_msg.pattern is None
            # we do not check the order of the messages, but we can check that we received both messages once
            assert not sync_msg.message == async_msg.message

            # assert there are no messages to read
            with pytest.raises(asyncio.TimeoutError):
                await asyncio.wait_for(listening_client.get_pubsub_message(), timeout=3)

            assert listening_client.try_get_pubsub_message() is None
        finally:
            if cluster_mode:
                # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
                # In cluster mode, we check how many subscriptions received the message
                # So to avoid flakiness, we make sure to unsubscribe from the channels
                await listening_client.custom_command(["UNSUBSCRIBE", channel])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_exact_happy_path_many_channels(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Tests publishing and receiving messages across many channels in exact PUBSUB.

        This test covers the scenario where multiple channels each receive their own
        unique message. It verifies that messages are correctly published and received
        using different retrieval methods: async, sync, and callback.
        """
        NUM_CHANNELS = 256
        shard_prefix = "{same-shard}"

        # Create a map of channels to random messages with shard prefix
        channels_and_messages = {
            f"{shard_prefix}{get_random_string(10)}": get_random_string(5)
            for _ in range(NUM_CHANNELS)
        }

        callback, context = None, None
        callback_messages: List[CoreCommands.PubSubMsg] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {
                ClusterClientConfiguration.PubSubChannelModes.Exact: set(
                    channels_and_messages.keys()
                )
            },
            {
                GlideClientConfiguration.PubSubChannelModes.Exact: set(
                    channels_and_messages.keys()
                )
            },
            callback=callback,
            context=context,
        )
        publishing_client, listening_client = await create_two_clients(
            request, cluster_mode, pub_sub
        )

        try:
            # Publish messages to each channel
            for channel, message in channels_and_messages.items():
                result = await publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # Allow the messages to propagate
            await asyncio.sleep(1)

            # Check if all messages are received correctly
            for index in range(len(channels_and_messages)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client, callback_messages, index
                )
                assert pubsub_msg.channel in channels_and_messages.keys()
                assert pubsub_msg.message == channels_and_messages[pubsub_msg.channel]
                assert pubsub_msg.pattern is None
                del channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert channels_and_messages == {}
            # check no messages left
            await check_no_messages_left(
                method, listening_client, callback_messages, NUM_CHANNELS
            )

        finally:
            if cluster_mode:
                # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
                # In cluster mode, we check how many subscriptions received the message
                # So to avoid flakiness, we make sure to unsubscribe from the channels
                await listening_client.custom_command(
                    ["UNSUBSCRIBE", *list(channels_and_messages.keys())]
                )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_exact_happy_path_many_channels_co_existence(
        self, request, cluster_mode: bool
    ):
        """
        Tests publishing and receiving messages across many channels in exact PUBSUB, ensuring coexistence of async and sync retrieval methods.

        This test covers scenarios where multiple channels each receive their own unique message.
        It verifies that messages are correctly published and received using both async and sync methods to ensure that both methods
        can coexist and function correctly.
        """
        NUM_CHANNELS = 256
        shard_prefix = "{same-shard}"

        # Create a map of channels to random messages with shard prefix
        channels_and_messages = {
            f"{shard_prefix}{get_random_string(10)}": get_random_string(5)
            for _ in range(NUM_CHANNELS)
        }

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {
                ClusterClientConfiguration.PubSubChannelModes.Exact: set(
                    channels_and_messages.keys()
                )
            },
            {
                GlideClientConfiguration.PubSubChannelModes.Exact: set(
                    channels_and_messages.keys()
                )
            },
        )

        publishing_client, listening_client = await create_two_clients(
            request, cluster_mode, pub_sub
        )

        try:
            # Publish messages to each channel
            for channel, message in channels_and_messages.items():
                result = await publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # Allow the messages to propagate
            await asyncio.sleep(1)

            # Check if all messages are received correctly by each method
            for index in range(len(channels_and_messages)):
                method = MethodTesting.Async if index % 2 else MethodTesting.Sync
                pubsub_msg = await get_message_by_method(method, listening_client)

                assert pubsub_msg.channel in channels_and_messages.keys()
                assert pubsub_msg.message == channels_and_messages[pubsub_msg.channel]
                assert pubsub_msg.pattern is None
                del channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert channels_and_messages == {}
            # assert there are no messages to read
            with pytest.raises(asyncio.TimeoutError):
                await asyncio.wait_for(listening_client.get_pubsub_message(), timeout=3)

            assert listening_client.try_get_pubsub_message() is None

        finally:
            if cluster_mode:
                # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
                # In cluster mode, we check how many subscriptions received the message
                # So to avoid flakiness, we make sure to unsubscribe from the channels
                await listening_client.custom_command(
                    ["UNSUBSCRIBE", *list(channels_and_messages.keys())]
                )

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_sharded_pubsub(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Test sharded PUBSUB functionality with different message retrieval methods.

        This test covers the sharded PUBSUB flow using three different methods:
        Async, Sync, and Callback. It verifies that a message published to a
        specific sharded channel is correctly received by a subscriber.
        """
        channel = get_random_string(10)
        message = get_random_string(5)
        publish_response = 1

        callback, context = None, None
        callback_messages: List[CoreCommands.PubSubMsg] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Sharded: {channel}},
            {},
            callback=callback,
            context=context,
        )

        publishing_client, listening_client = await create_two_clients(
            request, cluster_mode, pub_sub
        )
        min_version = "7.0.0"
        if await check_if_server_version_lt(publishing_client, min_version):
            pytest.skip(reason=f"Redis version required >= {min_version}")

        try:
            assert (
                await cast(GlideClusterClient, publishing_client).publish(
                    message, channel, sharded=True
                )
                == publish_response
            )
            # allow the message to propagate
            await asyncio.sleep(1)

            pubsub_msg = await get_message_by_method(
                method, listening_client, callback_messages, 0
            )
            assert pubsub_msg.message == message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern is None

        finally:
            # assert there are no messages to read
            await check_no_messages_left(method, listening_client, callback_messages, 1)
            if cluster_mode:
                # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
                # In cluster mode, we check how many subscriptions received the message
                # So to avoid flakiness, we make sure to unsubscribe from the channels
                await listening_client.custom_command(["SUNSUBSCRIBE", channel])

    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_sharded_pubsub_co_existence(self, request, cluster_mode: bool):
        """
        Test sharded PUBSUB with co-existence of multiple messages.

        This test verifies the behavior of sharded PUBSUB when multiple messages are published
        to the same sharded channel. It ensures that both async and sync methods of message retrieval
        function correctly in this scenario.

        It covers the scenario where messages are published to a sharded channel and received using
        both async and sync methods. This ensures that the asynchronous and synchronous message
        retrieval methods can coexist without interfering with each other and operate as expected.
        """
        channel = get_random_string(10)
        message = get_random_string(5)
        message2 = get_random_string(7)
        publish_response = 1 if cluster_mode else OK

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Sharded: {channel}},
            {},
        )

        publishing_client, listening_client = await create_two_clients(
            request, cluster_mode, pub_sub
        )

        min_version = "7.0.0"
        if await check_if_server_version_lt(publishing_client, min_version):
            pytest.skip(reason=f"Redis version required >= {min_version}")

        try:
            assert (
                await cast(GlideClusterClient, publishing_client).publish(
                    message, channel, sharded=True
                )
                == publish_response
            )
            assert (
                await cast(GlideClusterClient, publishing_client).publish(
                    message2, channel, sharded=True
                )
                == publish_response
            )
            # allow the messages to propagate
            await asyncio.sleep(1)

            async_msg_res = await listening_client.get_pubsub_message()
            sync_msg_res = listening_client.try_get_pubsub_message()
            assert sync_msg_res
            async_msg = decode_pubsub_msg(async_msg_res)
            sync_msg = decode_pubsub_msg(sync_msg_res)

            assert async_msg.message in [message, message2]
            assert async_msg.channel == channel
            assert async_msg.pattern is None

            assert sync_msg.message in [message, message2]
            assert sync_msg.channel == channel
            assert sync_msg.pattern is None
            # we do not check the order of the messages, but we can check that we received both messages once
            assert not sync_msg.message == async_msg.message

            # assert there are no messages to read
            with pytest.raises(asyncio.TimeoutError):
                await asyncio.wait_for(listening_client.get_pubsub_message(), timeout=3)

            assert listening_client.try_get_pubsub_message() is None
        finally:
            if cluster_mode:
                # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
                # In cluster mode, we check how many subscriptions received the message
                # So to avoid flakiness, we make sure to unsubscribe from the channels
                await listening_client.custom_command(["SUNSUBSCRIBE", channel])

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_sharded_pubsub_many_channels(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Test sharded PUBSUB with multiple channels and different message retrieval methods.

        This test verifies the behavior of sharded PUBSUB when multiple messages are published
        across multiple sharded channels. It covers three different message retrieval methods:
        Async, Sync, and Callback.
        """
        NUM_CHANNELS = 256
        shard_prefix = "{same-shard}"
        publish_response = 1

        # Create a map of channels to random messages with shard prefix
        channels_and_messages = {
            f"{shard_prefix}{get_random_string(10)}": get_random_string(5)
            for _ in range(NUM_CHANNELS)
        }

        callback, context = None, None
        callback_messages: List[CoreCommands.PubSubMsg] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {
                ClusterClientConfiguration.PubSubChannelModes.Sharded: set(
                    channels_and_messages.keys()
                )
            },
            {},
            callback=callback,
            context=context,
        )

        publishing_client, listening_client = await create_two_clients(
            request, cluster_mode, pub_sub
        )

        min_version = "7.0.0"
        if await check_if_server_version_lt(publishing_client, min_version):
            pytest.skip(reason=f"Redis version required >= {min_version}")

        try:
            # Publish messages to each channel
            for channel, message in channels_and_messages.items():
                assert (
                    await cast(GlideClusterClient, publishing_client).publish(
                        message, channel, sharded=True
                    )
                    == publish_response
                )

            # Allow the messages to propagate
            await asyncio.sleep(1)

            # Check if all messages are received correctly
            for index in range(len(channels_and_messages)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client, callback_messages, index
                )
                assert pubsub_msg.channel in channels_and_messages.keys()
                assert pubsub_msg.message == channels_and_messages[pubsub_msg.channel]
                assert pubsub_msg.pattern is None
                del channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert channels_and_messages == {}

            # Assert there are no more messages to read
            await check_no_messages_left(
                method, listening_client, callback_messages, NUM_CHANNELS
            )

        finally:
            if cluster_mode:
                # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
                # In cluster mode, we check how many subscriptions received the message
                # So to avoid flakiness, we make sure to unsubscribe from the channels
                await listening_client.custom_command(
                    ["SUNSUBSCRIBE", *list(channels_and_messages.keys())]
                )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_pattern(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Test PUBSUB with pattern subscription using different message retrieval methods.

        This test verifies the behavior of PUBSUB when subscribing to a pattern and receiving
        messages using three different methods: Async, Sync, and Callback.
        """
        PATTERN = "{{{}}}:{}".format("channel", "*")
        channels = {
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(5),
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(5),
        }

        callback, context = None, None
        callback_messages: List[CoreCommands.PubSubMsg] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Pattern: {PATTERN}},
            {GlideClientConfiguration.PubSubChannelModes.Pattern: {PATTERN}},
            callback=callback,
            context=context,
        )
        publishing_client, listening_client = await create_two_clients(
            request, cluster_mode, pub_sub
        )

        try:
            for channel, message in channels.items():
                result = await publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # allow the message to propagate
            await asyncio.sleep(1)

            # Check if all messages are received correctly
            for index in range(len(channels)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client, callback_messages, index
                )
                assert pubsub_msg.channel in channels.keys()
                assert pubsub_msg.message == channels[pubsub_msg.channel]
                assert pubsub_msg.pattern == PATTERN
                del channels[pubsub_msg.channel]

            # check that we received all messages
            assert channels == {}

            await check_no_messages_left(method, listening_client, callback_messages, 2)

        finally:
            if cluster_mode:
                # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
                # In cluster mode, we check how many subscriptions received the message
                # So to avoid flakiness, we make sure to unsubscribe from the channels
                await listening_client.custom_command(["PUNSUBSCRIBE", PATTERN])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_pattern_co_existence(self, request, cluster_mode: bool):
        """
        Tests the coexistence of async and sync message retrieval methods in pattern-based PUBSUB.

        This test covers the scenario where messages are published to a channel that match a specified pattern
        and received using both async and sync methods to ensure that both methods
        can coexist and function correctly.
        """
        PATTERN = "{{{}}}:{}".format("channel", "*")
        channels = {
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(5),
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(5),
        }

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Pattern: {PATTERN}},
            {GlideClientConfiguration.PubSubChannelModes.Pattern: {PATTERN}},
        )

        publishing_client, listening_client = await create_two_clients(
            request, cluster_mode, pub_sub
        )

        try:
            for channel, message in channels.items():
                result = await publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # allow the message to propagate
            await asyncio.sleep(1)

            # Check if all messages are received correctly by each method
            for index in range(len(channels)):
                method = MethodTesting.Async if index % 2 else MethodTesting.Sync
                pubsub_msg = await get_message_by_method(method, listening_client)

                assert pubsub_msg.channel in channels.keys()
                assert pubsub_msg.message == channels[pubsub_msg.channel]
                assert pubsub_msg.pattern == PATTERN
                del channels[pubsub_msg.channel]

            # check that we received all messages
            assert channels == {}

            # assert there are no more messages to read
            with pytest.raises(asyncio.TimeoutError):
                await asyncio.wait_for(listening_client.get_pubsub_message(), timeout=3)

            assert listening_client.try_get_pubsub_message() is None

        finally:
            if cluster_mode:
                # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
                # In cluster mode, we check how many subscriptions received the message
                # So to avoid flakiness, we make sure to unsubscribe from the channels
                await listening_client.custom_command(["PUNSUBSCRIBE", PATTERN])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_pattern_many_channels(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Tests publishing and receiving messages across many channels in pattern-based PUBSUB.

        This test covers the scenario where messages are published to multiple channels that match a specified pattern
        and received. It verifies that messages are correctly published and received
        using different retrieval methods: async, sync, and callback.
        """
        NUM_CHANNELS = 256
        PATTERN = "{{{}}}:{}".format("channel", "*")
        channels = {
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(5)
            for _ in range(NUM_CHANNELS)
        }

        callback, context = None, None
        callback_messages: List[CoreCommands.PubSubMsg] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Pattern: {PATTERN}},
            {GlideClientConfiguration.PubSubChannelModes.Pattern: {PATTERN}},
            callback=callback,
            context=context,
        )
        publishing_client, listening_client = await create_two_clients(
            request, cluster_mode, pub_sub
        )

        try:
            for channel, message in channels.items():
                result = await publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # allow the message to propagate
            await asyncio.sleep(1)

            # Check if all messages are received correctly
            for index in range(len(channels)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client, callback_messages, index
                )
                assert pubsub_msg.channel in channels.keys()
                assert pubsub_msg.message == channels[pubsub_msg.channel]
                assert pubsub_msg.pattern == PATTERN
                del channels[pubsub_msg.channel]

            # check that we received all messages
            assert channels == {}

            await check_no_messages_left(
                method, listening_client, callback_messages, NUM_CHANNELS
            )

        finally:
            if cluster_mode:
                # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
                # In cluster mode, we check how many subscriptions received the message
                # So to avoid flakiness, we make sure to unsubscribe from the channels
                await listening_client.custom_command(["PUNSUBSCRIBE", PATTERN])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_combined_exact_and_pattern_one_client(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Tests combined exact and pattern PUBSUB with one client.

        This test verifies that a single client can correctly handle both exact and pattern PUBSUB
        subscriptions. It covers the following scenarios:
        - Subscribing to multiple channels with exact names and verifying message reception.
        - Subscribing to channels using a pattern and verifying message reception.
        - Ensuring that messages are correctly published and received using different retrieval methods (async, sync, callback).
        """
        NUM_CHANNELS = 256
        PATTERN = "{{{}}}:{}".format("pattern", "*")

        # Create dictionaries of channels and their corresponding messages
        exact_channels_and_messages = {
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(10)
            for _ in range(NUM_CHANNELS)
        }
        pattern_channels_and_messages = {
            "{{{}}}:{}".format("pattern", get_random_string(5)): get_random_string(5)
            for _ in range(NUM_CHANNELS)
        }

        all_channels_and_messages = {
            **exact_channels_and_messages,
            **pattern_channels_and_messages,
        }

        callback, context = None, None
        callback_messages: List[CoreCommands.PubSubMsg] = []

        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        # Setup PUBSUB for exact channels
        pub_sub_exact = create_pubsub_subscription(
            cluster_mode,
            {
                ClusterClientConfiguration.PubSubChannelModes.Exact: set(
                    exact_channels_and_messages.keys()
                ),
                ClusterClientConfiguration.PubSubChannelModes.Pattern: {PATTERN},
            },
            {
                GlideClientConfiguration.PubSubChannelModes.Exact: set(
                    exact_channels_and_messages.keys()
                ),
                GlideClientConfiguration.PubSubChannelModes.Pattern: {PATTERN},
            },
            callback=callback,
            context=context,
        )

        publishing_client, listening_client = await create_two_clients(
            request,
            cluster_mode,
            pub_sub_exact,
        )

        try:
            # Publish messages to all channels
            for channel, message in all_channels_and_messages.items():
                result = await publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # allow the message to propagate
            await asyncio.sleep(1)

            # Check if all messages are received correctly
            for index in range(len(all_channels_and_messages)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client, callback_messages, index
                )
                pattern = (
                    PATTERN
                    if pubsub_msg.channel in pattern_channels_and_messages.keys()
                    else None
                )
                assert pubsub_msg.channel in all_channels_and_messages.keys()
                assert (
                    pubsub_msg.message == all_channels_and_messages[pubsub_msg.channel]
                )
                assert pubsub_msg.pattern == pattern
                del all_channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert all_channels_and_messages == {}

            await check_no_messages_left(
                method, listening_client, callback_messages, NUM_CHANNELS * 2
            )
        finally:
            if cluster_mode:
                # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
                # In cluster mode, we check how many subscriptions received the message
                # So to avoid flakiness, we make sure to unsubscribe from the channels
                await listening_client.custom_command(
                    ["UNSUBSCRIBE", *list(exact_channels_and_messages.keys())]
                )
                await listening_client.custom_command(["PUNSUBSCRIBE", PATTERN])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_combined_exact_and_pattern_multiple_clients(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Tests combined exact and pattern PUBSUB with multiple clients, one for each subscription.

        This test verifies that separate clients can correctly handle both exact and pattern PUBSUB
        subscriptions. It covers the following scenarios:
        - Subscribing to multiple channels with exact names and verifying message reception.
        - Subscribing to channels using a pattern and verifying message reception.
        - Ensuring that messages are correctly published and received using different retrieval methods (async, sync, callback).
        - Verifying that no messages are left unread.
        - Properly unsubscribing from all channels to avoid interference with other tests.
        """
        NUM_CHANNELS = 256
        PATTERN = "{{{}}}:{}".format("pattern", "*")

        # Create dictionaries of channels and their corresponding messages
        exact_channels_and_messages = {
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(10)
            for _ in range(NUM_CHANNELS)
        }
        pattern_channels_and_messages = {
            "{{{}}}:{}".format("pattern", get_random_string(5)): get_random_string(5)
            for _ in range(NUM_CHANNELS)
        }

        all_channels_and_messages = {
            **exact_channels_and_messages,
            **pattern_channels_and_messages,
        }

        callback, context = None, None
        callback_messages: List[CoreCommands.PubSubMsg] = []

        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        # Setup PUBSUB for exact channels
        pub_sub_exact = create_pubsub_subscription(
            cluster_mode,
            {
                ClusterClientConfiguration.PubSubChannelModes.Exact: set(
                    exact_channels_and_messages.keys()
                )
            },
            {
                GlideClientConfiguration.PubSubChannelModes.Exact: set(
                    exact_channels_and_messages.keys()
                )
            },
            callback=callback,
            context=context,
        )

        publishing_client, listening_client_exact = await create_two_clients(
            request,
            cluster_mode,
            pub_sub_exact,
        )

        callback_messages_pattern: List[CoreCommands.PubSubMsg] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages_pattern

        # Setup PUBSUB for pattern channels
        pub_sub_pattern = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Pattern: {PATTERN}},
            {GlideClientConfiguration.PubSubChannelModes.Pattern: {PATTERN}},
            callback=callback,
            context=context,
        )

        _, listening_client_pattern = await create_two_clients(
            request, cluster_mode, pub_sub_pattern
        )

        try:
            # Publish messages to all channels
            for channel, message in all_channels_and_messages.items():
                result = await publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # allow the messages to propagate
            await asyncio.sleep(1)

            # Verify messages for exact PUBSUB
            for index in range(len(exact_channels_and_messages)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client_exact, callback_messages, index
                )
                assert pubsub_msg.channel in exact_channels_and_messages.keys()
                assert (
                    pubsub_msg.message
                    == exact_channels_and_messages[pubsub_msg.channel]
                )
                assert pubsub_msg.pattern is None
                del exact_channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert exact_channels_and_messages == {}

            # Verify messages for pattern PUBSUB
            for index in range(len(pattern_channels_and_messages)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client_pattern, callback_messages_pattern, index
                )
                assert pubsub_msg.channel in pattern_channels_and_messages.keys()
                assert (
                    pubsub_msg.message
                    == pattern_channels_and_messages[pubsub_msg.channel]
                )
                assert pubsub_msg.pattern == PATTERN
                del pattern_channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert pattern_channels_and_messages == {}

            await check_no_messages_left(
                method, listening_client_exact, callback_messages, NUM_CHANNELS
            )
            await check_no_messages_left(
                method,
                listening_client_pattern,
                callback_messages_pattern,
                NUM_CHANNELS,
            )

        finally:
            if cluster_mode:
                # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
                # In cluster mode, we check how many subscriptions received the message
                # So to avoid flakiness, we make sure to unsubscribe from the channels
                await listening_client_exact.custom_command(
                    ["UNSUBSCRIBE", *list(exact_channels_and_messages.keys())]
                )
                await listening_client_pattern.custom_command(["PUNSUBSCRIBE", PATTERN])

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_combined_exact_pattern_and_sharded_one_client(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Tests combined exact, pattern and sharded PUBSUB with one client.

        This test verifies that a single client can correctly handle both exact, pattern and sharded PUBSUB
        subscriptions. It covers the following scenarios:
        - Subscribing to multiple channels with exact names and verifying message reception.
        - Subscribing to channels using a pattern and verifying message reception.
        - Subscribing to channels using a with sharded subscription and verifying message reception.
        - Ensuring that messages are correctly published and received using different retrieval methods (async, sync, callback).
        """
        NUM_CHANNELS = 256
        PATTERN = "{{{}}}:{}".format("pattern", "*")
        SHARD_PREFIX = "{same-shard}"

        # Create dictionaries of channels and their corresponding messages
        exact_channels_and_messages = {
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(10)
            for _ in range(NUM_CHANNELS)
        }
        pattern_channels_and_messages = {
            "{{{}}}:{}".format("pattern", get_random_string(5)): get_random_string(5)
            for _ in range(NUM_CHANNELS)
        }
        sharded_channels_and_messages = {
            f"{SHARD_PREFIX}:{get_random_string(10)}": get_random_string(7)
            for _ in range(NUM_CHANNELS)
        }

        publish_response = 1

        callback, context = None, None
        callback_messages: List[CoreCommands.PubSubMsg] = []

        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        # Setup PUBSUB for exact channels
        pub_sub_exact = create_pubsub_subscription(
            cluster_mode,
            {
                ClusterClientConfiguration.PubSubChannelModes.Exact: set(
                    exact_channels_and_messages.keys()
                ),
                ClusterClientConfiguration.PubSubChannelModes.Pattern: {PATTERN},
                ClusterClientConfiguration.PubSubChannelModes.Sharded: set(
                    sharded_channels_and_messages.keys()
                ),
            },
            {},
            callback=callback,
            context=context,
        )

        publishing_client, listening_client = await create_two_clients(
            request,
            cluster_mode,
            pub_sub_exact,
        )

        # Setup PUBSUB for sharded channels (Redis version > 7)
        if await check_if_server_version_lt(publishing_client, "7.0.0"):
            pytest.skip("Redis version required >= 7.0.0")

        try:
            # Publish messages to all channels
            for channel, message in {
                **exact_channels_and_messages,
                **pattern_channels_and_messages,
            }.items():
                assert (
                    await publishing_client.publish(message, channel)
                    == publish_response
                )

            # Publish sharded messages to all channels
            for channel, message in sharded_channels_and_messages.items():
                assert (
                    await cast(GlideClusterClient, publishing_client).publish(
                        message, channel, sharded=True
                    )
                    == publish_response
                )

            # allow the messages to propagate
            await asyncio.sleep(1)

            all_channels_and_messages = {
                **exact_channels_and_messages,
                **pattern_channels_and_messages,
                **sharded_channels_and_messages,
            }
            # Check if all messages are received correctly
            for index in range(len(all_channels_and_messages)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client, callback_messages, index
                )
                pattern = (
                    PATTERN
                    if pubsub_msg.channel in pattern_channels_and_messages.keys()
                    else None
                )
                assert pubsub_msg.channel in all_channels_and_messages.keys()
                assert (
                    pubsub_msg.message == all_channels_and_messages[pubsub_msg.channel]
                )
                assert pubsub_msg.pattern == pattern
                del all_channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert all_channels_and_messages == {}

            await check_no_messages_left(
                method, listening_client, callback_messages, NUM_CHANNELS * 3
            )

        finally:
            if cluster_mode:
                # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
                # In cluster mode, we check how many subscriptions received the message
                # So to avoid flakiness, we make sure to unsubscribe from the channels
                await listening_client.custom_command(
                    ["UNSUBSCRIBE", *list(exact_channels_and_messages.keys())]
                )
                await listening_client.custom_command(["PUNSUBSCRIBE", PATTERN])
                await listening_client.custom_command(
                    ["SUNSUBSCRIBE", *list(sharded_channels_and_messages.keys())]
                )

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_combined_exact_pattern_and_sharded_multi_client(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Tests combined exact, pattern and sharded PUBSUB with multiple clients, one for each subscription.

        This test verifies that separate clients can correctly handle exact, pattern and sharded PUBSUB
        subscriptions. It covers the following scenarios:
        - Subscribing to multiple channels with exact names and verifying message reception.
        - Subscribing to channels using a pattern and verifying message reception.
        - Subscribing to channels using a sharded subscription and verifying message reception.
        - Ensuring that messages are correctly published and received using different retrieval methods (async, sync, callback).
        - Verifying that no messages are left unread.
        - Properly unsubscribing from all channels to avoid interference with other tests.
        """
        NUM_CHANNELS = 256
        PATTERN = "{{{}}}:{}".format("pattern", "*")
        SHARD_PREFIX = "{same-shard}"

        # Create dictionaries of channels and their corresponding messages
        exact_channels_and_messages = {
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(10)
            for _ in range(NUM_CHANNELS)
        }
        pattern_channels_and_messages = {
            "{{{}}}:{}".format("pattern", get_random_string(5)): get_random_string(5)
            for _ in range(NUM_CHANNELS)
        }
        sharded_channels_and_messages = {
            f"{SHARD_PREFIX}:{get_random_string(10)}": get_random_string(7)
            for _ in range(NUM_CHANNELS)
        }

        publish_response = 1

        callback, context = None, None
        callback_messages_exact: List[CoreCommands.PubSubMsg] = []
        callback_messages_pattern: List[CoreCommands.PubSubMsg] = []
        callback_messages_sharded: List[CoreCommands.PubSubMsg] = []

        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages_exact

        # Setup PUBSUB for exact channels
        pub_sub_exact = create_pubsub_subscription(
            cluster_mode,
            {
                ClusterClientConfiguration.PubSubChannelModes.Exact: set(
                    exact_channels_and_messages.keys()
                )
            },
            {
                GlideClientConfiguration.PubSubChannelModes.Exact: set(
                    exact_channels_and_messages.keys()
                )
            },
            callback=callback,
            context=context,
        )

        publishing_client, listening_client_exact = await create_two_clients(
            request,
            cluster_mode,
            pub_sub_exact,
        )

        # Setup PUBSUB for sharded channels (Redis version > 7)
        if await check_if_server_version_lt(publishing_client, "7.0.0"):
            pytest.skip("Redis version required >= 7.0.0")

        if method == MethodTesting.Callback:
            context = callback_messages_pattern

        # Setup PUBSUB for pattern channels
        pub_sub_pattern = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Pattern: {PATTERN}},
            {GlideClientConfiguration.PubSubChannelModes.Pattern: {PATTERN}},
            callback=callback,
            context=context,
        )

        if method == MethodTesting.Callback:
            context = callback_messages_sharded

        pub_sub_sharded = create_pubsub_subscription(
            cluster_mode,
            {
                ClusterClientConfiguration.PubSubChannelModes.Sharded: set(
                    sharded_channels_and_messages.keys()
                )
            },
            {},
            callback=callback,
            context=context,
        )

        listening_client_sharded, listening_client_pattern = await create_two_clients(
            request, cluster_mode, pub_sub_pattern, pub_sub_sharded
        )

        try:
            # Publish messages to all channels
            for channel, message in {
                **exact_channels_and_messages,
                **pattern_channels_and_messages,
            }.items():
                assert (
                    await publishing_client.publish(message, channel)
                    == publish_response
                )

            # Publish sharded messages to all channels
            for channel, message in sharded_channels_and_messages.items():
                assert (
                    await cast(GlideClusterClient, publishing_client).publish(
                        message, channel, sharded=True
                    )
                    == publish_response
                )

            # allow the messages to propagate
            await asyncio.sleep(1)

            # Verify messages for exact PUBSUB
            for index in range(len(exact_channels_and_messages)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client_exact, callback_messages_exact, index
                )
                assert pubsub_msg.channel in exact_channels_and_messages.keys()
                assert (
                    pubsub_msg.message
                    == exact_channels_and_messages[pubsub_msg.channel]
                )
                assert pubsub_msg.pattern is None
                del exact_channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert exact_channels_and_messages == {}

            # Verify messages for pattern PUBSUB
            for index in range(len(pattern_channels_and_messages)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client_pattern, callback_messages_pattern, index
                )
                assert pubsub_msg.channel in pattern_channels_and_messages.keys()
                assert (
                    pubsub_msg.message
                    == pattern_channels_and_messages[pubsub_msg.channel]
                )
                assert pubsub_msg.pattern == PATTERN
                del pattern_channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert pattern_channels_and_messages == {}

            # Verify messages for shaded PUBSUB
            for index in range(len(sharded_channels_and_messages)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client_sharded, callback_messages_sharded, index
                )
                assert pubsub_msg.channel in sharded_channels_and_messages.keys()
                assert (
                    pubsub_msg.message
                    == sharded_channels_and_messages[pubsub_msg.channel]
                )
                assert pubsub_msg.pattern is None
                del sharded_channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert sharded_channels_and_messages == {}

            await check_no_messages_left(
                method, listening_client_exact, callback_messages_exact, NUM_CHANNELS
            )
            await check_no_messages_left(
                method,
                listening_client_pattern,
                callback_messages_pattern,
                NUM_CHANNELS,
            )
            await check_no_messages_left(
                method,
                listening_client_sharded,
                callback_messages_sharded,
                NUM_CHANNELS,
            )

        finally:
            if cluster_mode:
                # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
                # In cluster mode, we check how many subscriptions received the message
                # So to avoid flakiness, we make sure to unsubscribe from the channels
                await listening_client_exact.custom_command(
                    ["UNSUBSCRIBE", *list(exact_channels_and_messages.keys())]
                )
                await listening_client_pattern.custom_command(["PUNSUBSCRIBE", PATTERN])
                await listening_client_sharded.custom_command(
                    ["SUNSUBSCRIBE", *list(sharded_channels_and_messages.keys())]
                )

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_combined_different_channels_with_same_name(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Tests combined PUBSUB with different channel modes using the same channel name.
        One publishing clients, 3 listening clients, one for each mode.

        This test verifies that separate clients can correctly handle subscriptions for exact, pattern, and sharded channels with the same name.
        It covers the following scenarios:
        - Subscribing to an exact channel and verifying message reception.
        - Subscribing to a pattern channel and verifying message reception.
        - Subscribing to a sharded channel and verifying message reception.
        - Ensuring that messages are correctly published and received using different retrieval methods (async, sync, callback).
        - Verifying that no messages are left unread.
        - Properly unsubscribing from all channels to avoid interference with other tests.
        """
        CHANNEL_NAME = "same-channel-name"
        MESSAGE_EXACT = get_random_string(10)
        MESSAGE_PATTERN = get_random_string(7)
        MESSAGE_SHARDED = get_random_string(5)

        callback, context = None, None
        callback_messages_exact: List[CoreCommands.PubSubMsg] = []
        callback_messages_pattern: List[CoreCommands.PubSubMsg] = []
        callback_messages_sharded: List[CoreCommands.PubSubMsg] = []

        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages_exact

        # Setup PUBSUB for exact channel
        pub_sub_exact = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Exact: {CHANNEL_NAME}},
            {GlideClientConfiguration.PubSubChannelModes.Exact: {CHANNEL_NAME}},
            callback=callback,
            context=context,
        )

        publishing_client, listening_client_exact = await create_two_clients(
            request,
            cluster_mode,
            pub_sub_exact,
        )

        # (Redis version > 7)
        if await check_if_server_version_lt(publishing_client, "7.0.0"):
            pytest.skip("Redis version required >= 7.0.0")

        # Setup PUBSUB for pattern channel
        if method == MethodTesting.Callback:
            context = callback_messages_pattern

        # Setup PUBSUB for pattern channels
        pub_sub_pattern = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Pattern: {CHANNEL_NAME}},
            {GlideClientConfiguration.PubSubChannelModes.Pattern: {CHANNEL_NAME}},
            callback=callback,
            context=context,
        )

        if method == MethodTesting.Callback:
            context = callback_messages_sharded

        pub_sub_sharded = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Sharded: {CHANNEL_NAME}},
            {},
            callback=callback,
            context=context,
        )

        listening_client_sharded, listening_client_pattern = await create_two_clients(
            request, cluster_mode, pub_sub_pattern, pub_sub_sharded
        )

        try:
            # Publish messages to each channel
            assert await publishing_client.publish(MESSAGE_EXACT, CHANNEL_NAME) == 2
            assert await publishing_client.publish(MESSAGE_PATTERN, CHANNEL_NAME) == 2
            assert (
                await cast(GlideClusterClient, publishing_client).publish(
                    MESSAGE_SHARDED, CHANNEL_NAME, sharded=True
                )
                == 1
            )

            # allow the message to propagate
            await asyncio.sleep(1)

            # Verify message for exact and pattern PUBSUB
            for client, callback, pattern in [  # type: ignore
                (listening_client_exact, callback_messages_exact, None),
                (listening_client_pattern, callback_messages_pattern, CHANNEL_NAME),
            ]:
                pubsub_msg = await get_message_by_method(method, client, callback, 0)  # type: ignore

                pubsub_msg2 = await get_message_by_method(method, client, callback, 1)  # type: ignore
                assert not pubsub_msg.message == pubsub_msg2.message
                assert pubsub_msg2.message in [MESSAGE_PATTERN, MESSAGE_EXACT]
                assert pubsub_msg.message in [MESSAGE_PATTERN, MESSAGE_EXACT]
                assert pubsub_msg.channel == pubsub_msg2.channel == CHANNEL_NAME
                assert pubsub_msg.pattern == pubsub_msg2.pattern == pattern

            # Verify message for sharded PUBSUB
            pubsub_msg_sharded = await get_message_by_method(
                method, listening_client_sharded, callback_messages_sharded, 0
            )
            assert pubsub_msg_sharded.message == MESSAGE_SHARDED
            assert pubsub_msg_sharded.channel == CHANNEL_NAME
            assert pubsub_msg_sharded.pattern is None

            await check_no_messages_left(
                method, listening_client_exact, callback_messages_exact, 2
            )
            await check_no_messages_left(
                method, listening_client_pattern, callback_messages_pattern, 2
            )
            await check_no_messages_left(
                method, listening_client_sharded, callback_messages_sharded, 1
            )

        finally:
            if cluster_mode:
                # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
                # In cluster mode, we check how many subscriptions received the message
                # So to avoid flakiness, we make sure to unsubscribe from the channels
                await listening_client_exact.custom_command(
                    ["UNSUBSCRIBE", CHANNEL_NAME]
                )
                await listening_client_pattern.custom_command(
                    ["PUNSUBSCRIBE", CHANNEL_NAME]
                )
                await listening_client_sharded.custom_command(
                    ["SUNSUBSCRIBE", CHANNEL_NAME]
                )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_two_publishing_clients_same_name(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Tests PUBSUB with two publishing clients using the same channel name.
        One client uses pattern subscription, the other uses exact.
        The clients publishes messages to each other, and to thyself.

        This test verifies that two separate clients can correctly publish to and handle subscriptions
        for exact and pattern channels with the same name. It covers the following scenarios:
        - Subscribing to an exact channel and verifying message reception.
        - Subscribing to a pattern channel and verifying message reception.
        - Ensuring that messages are correctly published and received using different retrieval methods (async, sync, callback).
        - Verifying that no messages are left unread.
        - Properly unsubscribing from all channels to avoid interference with other tests.
        """
        CHANNEL_NAME = "channel-name"
        MESSAGE_EXACT = get_random_string(10)
        MESSAGE_PATTERN = get_random_string(7)
        callback, context_exact, context_pattern = None, None, None
        callback_messages_exact: List[CoreCommands.PubSubMsg] = []
        callback_messages_pattern: List[CoreCommands.PubSubMsg] = []

        if method == MethodTesting.Callback:
            callback = new_message
            context_exact = callback_messages_exact
            context_pattern = callback_messages_pattern

        # Setup PUBSUB for exact channel
        pub_sub_exact = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Exact: {CHANNEL_NAME}},
            {GlideClientConfiguration.PubSubChannelModes.Exact: {CHANNEL_NAME}},
            callback=callback,
            context=context_exact,
        )
        # Setup PUBSUB for pattern channels
        pub_sub_pattern = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Pattern: {CHANNEL_NAME}},
            {GlideClientConfiguration.PubSubChannelModes.Pattern: {CHANNEL_NAME}},
            callback=callback,
            context=context_pattern,
        )

        client_pattern, client_exact = await create_two_clients(
            request, cluster_mode, pub_sub_exact, pub_sub_pattern
        )

        try:
            # Publish messages to each channel - both clients publishing
            for msg in [MESSAGE_EXACT, MESSAGE_PATTERN]:
                result = await client_pattern.publish(msg, CHANNEL_NAME)
                if cluster_mode:
                    assert result == 2

            # allow the message to propagate
            await asyncio.sleep(1)

            # Verify message for exact and pattern PUBSUB
            for client, callback, pattern in [  # type: ignore
                (client_exact, callback_messages_exact, None),
                (client_pattern, callback_messages_pattern, CHANNEL_NAME),
            ]:
                pubsub_msg = await get_message_by_method(method, client, callback, 0)  # type: ignore

                pubsub_msg2 = await get_message_by_method(method, client, callback, 1)  # type: ignore
                assert not pubsub_msg.message == pubsub_msg2.message
                assert pubsub_msg2.message in [MESSAGE_PATTERN, MESSAGE_EXACT]
                assert pubsub_msg.message in [MESSAGE_PATTERN, MESSAGE_EXACT]
                assert pubsub_msg.channel == pubsub_msg2.channel == CHANNEL_NAME
                assert pubsub_msg.pattern == pubsub_msg2.pattern == pattern

            await check_no_messages_left(
                method, client_pattern, callback_messages_pattern, 2
            )
            await check_no_messages_left(
                method, client_exact, callback_messages_exact, 2
            )

        finally:
            if cluster_mode:
                # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
                # In cluster mode, we check how many subscriptions received the message
                # So to avoid flakiness, we make sure to unsubscribe from the channels
                await client_exact.custom_command(["UNSUBSCRIBE", CHANNEL_NAME])
                await client_pattern.custom_command(["PUNSUBSCRIBE", CHANNEL_NAME])

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_three_publishing_clients_same_name_with_sharded(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Tests PUBSUB with 3 publishing clients using the same channel name.
        One client uses pattern subscription, one uses exact, and one uses sharded.

        This test verifies that 3 separate clients can correctly publish to and handle subscriptions
        for exact, sharded and pattern channels with the same name. It covers the following scenarios:
        - Subscribing to an exact channel and verifying message reception.
        - Subscribing to a pattern channel and verifying message reception.
        - Subscribing to a sharded channel and verifying message reception.
        - Ensuring that messages are correctly published and received using different retrieval methods (async, sync, callback).
        - Verifying that no messages are left unread.
        - Properly unsubscribing from all channels to avoid interference with other tests.
        """
        CHANNEL_NAME = "same-channel-name"
        MESSAGE_EXACT = get_random_string(10)
        MESSAGE_PATTERN = get_random_string(7)
        MESSAGE_SHARDED = get_random_string(5)
        publish_response = 2 if cluster_mode else OK
        callback, context_exact, context_pattern, context_sharded = (
            None,
            None,
            None,
            None,
        )
        callback_messages_exact: List[CoreCommands.PubSubMsg] = []
        callback_messages_pattern: List[CoreCommands.PubSubMsg] = []
        callback_messages_sharded: List[CoreCommands.PubSubMsg] = []

        if method == MethodTesting.Callback:
            callback = new_message
            context_exact = callback_messages_exact
            context_pattern = callback_messages_pattern
            context_sharded = callback_messages_sharded

        # Setup PUBSUB for exact channel
        pub_sub_exact = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Exact: {CHANNEL_NAME}},
            {GlideClientConfiguration.PubSubChannelModes.Exact: {CHANNEL_NAME}},
            callback=callback,
            context=context_exact,
        )
        # Setup PUBSUB for pattern channels
        pub_sub_pattern = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Pattern: {CHANNEL_NAME}},
            {GlideClientConfiguration.PubSubChannelModes.Pattern: {CHANNEL_NAME}},
            callback=callback,
            context=context_pattern,
        )
        # Setup PUBSUB for pattern channels
        pub_sub_sharded = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Sharded: {CHANNEL_NAME}},
            {},
            callback=callback,
            context=context_sharded,
        )

        client_pattern, client_exact = await create_two_clients(
            request, cluster_mode, pub_sub_exact, pub_sub_pattern
        )
        _, client_sharded = await create_two_clients(
            request, cluster_mode, pub_sub_sharded
        )
        # (Redis version > 7)
        if await check_if_server_version_lt(client_pattern, "7.0.0"):
            pytest.skip("Redis version required >= 7.0.0")

        try:
            # Publish messages to each channel - both clients publishing
            assert (
                await client_pattern.publish(MESSAGE_EXACT, CHANNEL_NAME)
                == publish_response
            )
            assert (
                await client_sharded.publish(MESSAGE_PATTERN, CHANNEL_NAME)
                == publish_response
            )
            assert (
                await cast(GlideClusterClient, client_exact).publish(
                    MESSAGE_SHARDED, CHANNEL_NAME, sharded=True
                )
                == 1
            )

            # allow the message to propagate
            await asyncio.sleep(1)

            # Verify message for exact and pattern PUBSUB
            for client, callback, pattern in [  # type: ignore
                (client_exact, callback_messages_exact, None),
                (client_pattern, callback_messages_pattern, CHANNEL_NAME),
            ]:
                pubsub_msg = await get_message_by_method(method, client, callback, 0)  # type: ignore

                pubsub_msg2 = await get_message_by_method(method, client, callback, 1)  # type: ignore
                assert not pubsub_msg.message == pubsub_msg2.message
                assert pubsub_msg2.message in [MESSAGE_PATTERN, MESSAGE_EXACT]
                assert pubsub_msg.message in [MESSAGE_PATTERN, MESSAGE_EXACT]
                assert pubsub_msg.channel == pubsub_msg2.channel == CHANNEL_NAME
                assert pubsub_msg.pattern == pubsub_msg2.pattern == pattern

            msg = await get_message_by_method(
                method, client_sharded, callback_messages_sharded, 0
            )
            assert msg.message == MESSAGE_SHARDED
            assert msg.channel == CHANNEL_NAME
            assert msg.pattern is None

            await check_no_messages_left(
                method, client_pattern, callback_messages_pattern, 2
            )
            await check_no_messages_left(
                method, client_exact, callback_messages_exact, 2
            )
            await check_no_messages_left(
                method, client_sharded, callback_messages_sharded, 1
            )

        finally:
            if cluster_mode:
                # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
                # In cluster mode, we check how many subscriptions received the message
                # So to avoid flakiness, we make sure to unsubscribe from the channels
                await client_exact.custom_command(["UNSUBSCRIBE", CHANNEL_NAME])
                await client_pattern.custom_command(["PUNSUBSCRIBE", CHANNEL_NAME])
                await client_sharded.custom_command(["SUNSUBSCRIBE", CHANNEL_NAME])

    @pytest.mark.skip(
        reason="no way of currently testing this, see https://github.com/aws/glide-for-redis/issues/1649"
    )
    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_exact_max_size_message(self, request, cluster_mode: bool):
        """
        Tests publishing and receiving maximum size messages in PUBSUB.

        This test verifies that very large messages (512MB - BulkString max size) can be published and received
        correctly in both cluster and standalone modes. It ensures that the PUBSUB system
        can handle maximum size messages without errors and that async and sync message
        retrieval methods can coexist and function correctly.

        The test covers the following scenarios:
        - Setting up PUBSUB subscription for a specific channel.
        - Publishing two maximum size messages to the channel.
        - Verifying that the messages are received correctly using both async and sync methods.
        - Ensuring that no additional messages are left after the expected messages are received.
        """
        channel = get_random_string(10)
        message = get_random_string(512 * 1024 * 1024)
        message2 = get_random_string(512 * 1024 * 1024)
        publish_response = 1 if cluster_mode else OK

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Exact: {channel}},
            {GlideClientConfiguration.PubSubChannelModes.Exact: {channel}},
        )

        publishing_client, listening_client = await create_two_clients(
            request, cluster_mode, pub_sub
        )

        try:
            assert await publishing_client.publish(message, channel) == publish_response
            assert (
                await publishing_client.publish(message2, channel) == publish_response
            )
            # allow the message to propagate
            await asyncio.sleep(5)

            async_msg = await listening_client.get_pubsub_message()
            sync_msg = listening_client.try_get_pubsub_message()
            assert sync_msg

            assert async_msg.message == message.encode()
            assert async_msg.channel == channel.encode()
            assert async_msg.pattern is None

            assert sync_msg.message == message2.encode()
            assert sync_msg.channel == channel.encode()
            assert sync_msg.pattern is None

            # assert there are no messages to read
            with pytest.raises(asyncio.TimeoutError):
                await asyncio.wait_for(listening_client.get_pubsub_message(), timeout=3)

            assert listening_client.try_get_pubsub_message() is None

        finally:
            if cluster_mode:
                # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
                # In cluster mode, we check how many subscriptions received the message
                # So to avoid flakiness, we make sure to unsubscribe from the channels
                await listening_client.custom_command(["UNSUBSCRIBE", channel])

    @pytest.mark.skip(
        reason="no way of currently testing this, see https://github.com/aws/glide-for-redis/issues/1649"
    )
    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_pubsub_sharded_max_size_message(self, request, cluster_mode: bool):
        """
        Tests publishing and receiving maximum size messages in sharded PUBSUB.

        This test verifies that very large messages (512MB - BulkString max size) can be published and received
        correctly. It ensures that the PUBSUB system
        can handle maximum size messages without errors and that async and sync message
        retrieval methods can coexist and function correctly.

        The test covers the following scenarios:
        - Setting up PUBSUB subscription for a specific sharded channel.
        - Publishing two maximum size messages to the channel.
        - Verifying that the messages are received correctly using both async and sync methods.
        - Ensuring that no additional messages are left after the expected messages are received.
        """
        channel = get_random_string(10)
        message = get_random_string(512 * 1024 * 1024)
        message2 = get_random_string(512 * 1024 * 1024)
        publish_response = 1 if cluster_mode else OK

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Sharded: {channel}},
            {},
        )

        publishing_client, listening_client = await create_two_clients(
            request, cluster_mode, pub_sub
        )

        # (Redis version > 7)
        if await check_if_server_version_lt(publishing_client, "7.0.0"):
            pytest.skip("Redis version required >= 7.0.0")

        try:
            assert (
                await cast(GlideClusterClient, publishing_client).publish(
                    message, channel, sharded=True
                )
                == publish_response
            )
            assert (
                await publishing_client.publish(message2, channel) == publish_response
            )
            # allow the message to propagate
            await asyncio.sleep(5)

            async_msg = await listening_client.get_pubsub_message()
            sync_msg = listening_client.try_get_pubsub_message()
            assert sync_msg

            assert async_msg.message == message.encode()
            assert async_msg.channel == channel.encode()
            assert async_msg.pattern is None

            assert sync_msg.message == message2.encode()
            assert sync_msg.channel == channel.encode()
            assert sync_msg.pattern is None

            # assert there are no messages to read
            with pytest.raises(asyncio.TimeoutError):
                await asyncio.wait_for(listening_client.get_pubsub_message(), timeout=3)

            assert listening_client.try_get_pubsub_message() is None

        finally:
            if cluster_mode:
                # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
                # In cluster mode, we check how many subscriptions received the message
                # So to avoid flakiness, we make sure to unsubscribe from the channels
                await listening_client.custom_command(["UNSUBSCRIBE", channel])

    @pytest.mark.skip(
        reason="no way of currently testing this, see https://github.com/aws/glide-for-redis/issues/1649"
    )
    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_exact_max_size_message_callback(
        self, request, cluster_mode: bool
    ):
        """
        Tests publishing and receiving maximum size messages in exact PUBSUB with callback method.

        This test verifies that very large messages (512MB - BulkString max size) can be published and received
        correctly in both cluster and standalone modes. It ensures that the PUBSUB system
        can handle maximum size messages without errors and that the callback message
        retrieval method works as expected.

        The test covers the following scenarios:
        - Setting up PUBSUB subscription for a specific channel with a callback.
        - Publishing a maximum size message to the channel.
        - Verifying that the message is received correctly using the callback method.
        """
        channel = get_random_string(10)
        message = get_random_string(512 * 1024 * 1024)
        publish_response = 1 if cluster_mode else OK

        callback_messages: List[CoreCommands.PubSubMsg] = []
        callback, context = new_message, callback_messages

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Exact: {channel}},
            {GlideClientConfiguration.PubSubChannelModes.Exact: {channel}},
            callback=callback,
            context=context,
        )

        publishing_client, listening_client = await create_two_clients(
            request, cluster_mode, pub_sub
        )

        try:
            assert await publishing_client.publish(message, channel) == publish_response
            # allow the message to propagate
            await asyncio.sleep(5)

            assert len(callback_messages) == 1

            assert callback_messages[0].message == message
            assert callback_messages[0].channel == channel
            assert callback_messages[0].pattern is None

        finally:
            if cluster_mode:
                # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
                # In cluster mode, we check how many subscriptions received the message
                # So to avoid flakiness, we make sure to unsubscribe from the channels
                await listening_client.custom_command(["UNSUBSCRIBE", channel])

    @pytest.mark.skip(
        reason="no way of currently testing this, see https://github.com/aws/glide-for-redis/issues/1649"
    )
    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_pubsub_sharded_max_size_message_callback(
        self, request, cluster_mode: bool
    ):
        """
        Tests publishing and receiving maximum size messages in sharded PUBSUB with callback method.

        This test verifies that very large messages (512MB - BulkString max size) can be published and received
        correctly. It ensures that the PUBSUB system
        can handle maximum size messages without errors and that the callback message
        retrieval method works as expected.

        The test covers the following scenarios:
        - Setting up PUBSUB subscription for a specific sharded channel with a callback.
        - Publishing a maximum size message to the channel.
        - Verifying that the message is received correctly using the callback method.
        """
        channel = get_random_string(10)
        message = get_random_string(512 * 1024 * 1024)
        publish_response = 1 if cluster_mode else OK

        callback_messages: List[CoreCommands.PubSubMsg] = []
        callback, context = new_message, callback_messages

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Sharded: {channel}},
            {},
            callback=callback,
            context=context,
        )

        publishing_client, listening_client = await create_two_clients(
            request, cluster_mode, pub_sub
        )

        # (Redis version > 7)
        if await check_if_server_version_lt(publishing_client, "7.0.0"):
            pytest.skip("Redis version required >= 7.0.0")

        try:
            assert (
                await cast(GlideClusterClient, publishing_client).publish(
                    message, channel, sharded=True
                )
                == publish_response
            )
            # allow the message to propagate
            await asyncio.sleep(5)

            assert len(callback_messages) == 1

            assert callback_messages[0].message == message
            assert callback_messages[0].channel == channel
            assert callback_messages[0].pattern is None

        finally:
            if cluster_mode:
                # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
                # In cluster mode, we check how many subscriptions received the message
                # So to avoid flakiness, we make sure to unsubscribe from the channels
                await listening_client.custom_command(["UNSUBSCRIBE", channel])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_resp2_raise_an_error(self, request, cluster_mode: bool):
        """Tests that when creating a resp2 client with PUBSUB - an error will be raised"""
        channel = get_random_string(5)

        pub_sub_exact = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Exact: {channel}},
            {GlideClientConfiguration.PubSubChannelModes.Exact: {channel}},
        )

        with pytest.raises(ConfigurationError):
            await create_two_clients(
                request, cluster_mode, pub_sub_exact, protocol=ProtocolVersion.RESP2
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_context_with_no_callback_raise_error(
        self, request, cluster_mode: bool
    ):
        """Tests that when creating a PUBSUB client in callback method with context but no callback raises an error"""
        channel = get_random_string(5)
        context: List[CoreCommands.PubSubMsg] = []
        pub_sub_exact = create_pubsub_subscription(
            cluster_mode,
            {ClusterClientConfiguration.PubSubChannelModes.Exact: {channel}},
            {GlideClientConfiguration.PubSubChannelModes.Exact: {channel}},
            context=context,
        )

        with pytest.raises(ConfigurationError):
            await create_two_clients(request, cluster_mode, pub_sub_exact)
