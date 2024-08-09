/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestConfiguration.SERVER_VERSION;
import static glide.TestUtilities.assertDeepEquals;
import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static glide.api.BaseClient.OK;
import static glide.api.models.GlideString.gs;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_NODES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.ClusterTransaction;
import glide.api.models.GlideString;
import glide.api.models.PubSubMessage;
import glide.api.models.Transaction;
import glide.api.models.configuration.BaseSubscriptionConfiguration.ChannelMode;
import glide.api.models.configuration.BaseSubscriptionConfiguration.MessageCallback;
import glide.api.models.configuration.ClusterSubscriptionConfiguration;
import glide.api.models.configuration.ClusterSubscriptionConfiguration.PubSubClusterChannelMode;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotKeyRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotType;
import glide.api.models.configuration.StandaloneSubscriptionConfiguration;
import glide.api.models.configuration.StandaloneSubscriptionConfiguration.PubSubChannelMode;
import glide.api.models.exceptions.ConfigurationError;
import glide.api.models.exceptions.RequestException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30) // sec
public class PubSubTests {

    // TODO protocol version
    @SneakyThrows
    @SuppressWarnings("unchecked")
    private <M extends ChannelMode> BaseClient createClientWithSubscriptions(
            boolean standalone,
            Map<M, Set<GlideString>> subscriptions,
            Optional<MessageCallback> callback,
            Optional<Object> context) {
        if (standalone) {
            var subConfigBuilder =
                    StandaloneSubscriptionConfiguration.builder()
                            .subscriptions((Map<PubSubChannelMode, Set<GlideString>>) subscriptions);

            if (callback.isPresent()) {
                subConfigBuilder.callback(callback.get(), context.get());
            }
            return GlideClient.createClient(
                            commonClientConfig()
                                    .requestTimeout(5000)
                                    .subscriptionConfiguration(subConfigBuilder.build())
                                    .build())
                    .get();
        } else {
            var subConfigBuilder =
                    ClusterSubscriptionConfiguration.builder()
                            .subscriptions((Map<PubSubClusterChannelMode, Set<GlideString>>) subscriptions);

            if (callback.isPresent()) {
                subConfigBuilder.callback(callback.get(), context.get());
            }

            return GlideClusterClient.createClient(
                            commonClusterClientConfig()
                                    .requestTimeout(5000)
                                    .subscriptionConfiguration(subConfigBuilder.build())
                                    .build())
                    .get();
        }
    }

    private <M extends ChannelMode> BaseClient createClientWithSubscriptions(
            boolean standalone, Map<M, Set<GlideString>> subscriptions) {
        return createClientWithSubscriptions(
                standalone, subscriptions, Optional.empty(), Optional.empty());
    }

    @SneakyThrows
    private BaseClient createClient(boolean standalone) {
        return standalone
                ? GlideClient.createClient(commonClientConfig().build()).get()
                : GlideClusterClient.createClient(commonClusterClientConfig().build()).get();
    }

    /**
     * pubsubMessage queue used in callback to analyze received pubsubMessages. Number is a client ID.
     */
    private final ConcurrentLinkedDeque<Pair<Integer, PubSubMessage>> pubsubMessageQueue =
            new ConcurrentLinkedDeque<>();

    /** Clients used in a test. */
    private final List<BaseClient> clients = new ArrayList<>();

    private static final int MESSAGE_DELIVERY_DELAY = 500; // ms

    @AfterEach
    @SneakyThrows
    public void cleanup() {
        for (var client : clients) {
            if (client instanceof GlideClusterClient) {
                ((GlideClusterClient) client)
                        .customCommand(new GlideString[] {gs("unsubscribe")}, ALL_NODES)
                        .get();
                ((GlideClusterClient) client)
                        .customCommand(new GlideString[] {gs("punsubscribe")}, ALL_NODES)
                        .get();
                ((GlideClusterClient) client)
                        .customCommand(new GlideString[] {gs("sunsubscribe")}, ALL_NODES)
                        .get();
            } else {
                ((GlideClient) client).customCommand(new GlideString[] {gs("unsubscribe")}).get();
                ((GlideClient) client).customCommand(new GlideString[] {gs("punsubscribe")}).get();
            }
            client.close();
        }
        clients.clear();
        pubsubMessageQueue.clear();
    }

    private enum MessageReadMethod {
        Callback,
        Async,
        Sync
    }

    @SneakyThrows
    private void verifyReceivedPubsubMessages(
            Set<Pair<Integer, PubSubMessage>> pubsubMessages,
            BaseClient listener,
            MessageReadMethod method) {
        if (method == MessageReadMethod.Callback) {
            assertEquals(pubsubMessages, new HashSet<>(pubsubMessageQueue));
        } else if (method == MessageReadMethod.Async) {
            var received = new HashSet<PubSubMessage>(pubsubMessages.size());
            CompletableFuture<PubSubMessage> messagePromise;
            while ((messagePromise = listener.getPubSubMessage()).isDone()) {
                received.add(messagePromise.get());
            }
            assertEquals(
                    pubsubMessages.stream().map(Pair::getValue).collect(Collectors.toSet()), received);
        } else { // Sync
            var received = new HashSet<PubSubMessage>(pubsubMessages.size());
            PubSubMessage message;
            while ((message = listener.tryGetPubSubMessage()) != null) {
                received.add(message);
            }
            assertEquals(
                    pubsubMessages.stream().map(Pair::getValue).collect(Collectors.toSet()), received);
        }
    }

    /** Permute all combinations of `standalone` as bool vs {@link MessageReadMethod}. */
    private static Stream<Arguments> getTestScenarios() {
        return Stream.of(
                Arguments.of(true, MessageReadMethod.Callback),
                Arguments.of(true, MessageReadMethod.Sync),
                Arguments.of(true, MessageReadMethod.Async),
                Arguments.of(false, MessageReadMethod.Callback),
                Arguments.of(false, MessageReadMethod.Sync),
                Arguments.of(false, MessageReadMethod.Async));
    }

    private ChannelMode exact(boolean standalone) {
        return standalone ? PubSubChannelMode.EXACT : PubSubClusterChannelMode.EXACT;
    }

    private ChannelMode pattern(boolean standalone) {
        return standalone ? PubSubChannelMode.PATTERN : PubSubClusterChannelMode.PATTERN;
    }

    @SuppressWarnings("unchecked")
    private BaseClient createListener(
            boolean standalone,
            boolean withCallback,
            int clientId,
            Map<? extends ChannelMode, Set<GlideString>> subscriptions) {
        MessageCallback callback =
                (msg, ctx) ->
                        ((ConcurrentLinkedDeque<Pair<Integer, PubSubMessage>>) ctx)
                                .push(Pair.of(clientId, msg));
        return withCallback
                ? createClientWithSubscriptions(
                        standalone, subscriptions, Optional.of(callback), Optional.of(pubsubMessageQueue))
                : createClientWithSubscriptions(standalone, subscriptions);
    }

    // TODO why `publish` returns 0 on cluster or > 1 on standalone when there is only 1 receiver???
    //  meanwhile, all pubsubMessages are delivered.
    //  debug this and add checks for `publish` return value

    // TODO: remove once fixed
    private void skipTestsOnMac() {
        assumeFalse(
                System.getProperty("os.name").toLowerCase().contains("mac"),
                "PubSub doesn't work on mac OS");
    }

    /** Similar to `test_pubsub_exact_happy_path` in python client tests. */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, read messages via {1}")
    @MethodSource("getTestScenarios")
    public void exact_happy_path(boolean standalone, MessageReadMethod method) {
        skipTestsOnMac();
        GlideString channel = gs(UUID.randomUUID().toString());
        GlideString message = gs(UUID.randomUUID().toString());
        var subscriptions = Map.of(exact(standalone), Set.of(channel));

        var listener =
                createListener(standalone, method == MessageReadMethod.Callback, 1, subscriptions);
        var sender = createClient(standalone);
        clients.addAll(List.of(listener, sender));

        sender.publish(message, channel).get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the message

        verifyReceivedPubsubMessages(
                Set.of(Pair.of(1, new PubSubMessage(message, channel))), listener, method);
    }

    /** Similar to `test_pubsub_exact_happy_path_many_channels` in python client tests. */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, read messages via {1}")
    @MethodSource("getTestScenarios")
    public void exact_happy_path_many_channels(boolean standalone, MessageReadMethod method) {
        skipTestsOnMac();
        int numChannels = 256;
        int messagesPerChannel = 256;
        var messages = new ArrayList<PubSubMessage>(numChannels * messagesPerChannel);
        ChannelMode mode = exact(standalone);
        Map<? extends ChannelMode, Set<GlideString>> subscriptions = Map.of(mode, new HashSet<>());

        for (var i = 0; i < numChannels; i++) {
            GlideString channel = gs(i + "-" + UUID.randomUUID());
            subscriptions.get(mode).add(channel);
            for (var j = 0; j < messagesPerChannel; j++) {
                GlideString message = gs(i + "-" + j + "-" + UUID.randomUUID());
                messages.add(new PubSubMessage(message, channel));
            }
        }

        var listener =
                createListener(standalone, method == MessageReadMethod.Callback, 1, subscriptions);
        var sender = createClient(standalone);
        clients.addAll(List.of(listener, sender));

        for (var pubsubMessage : messages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        verifyReceivedPubsubMessages(
                messages.stream().map(m -> Pair.of(1, m)).collect(Collectors.toSet()), listener, method);
    }

    /** Similar to `test_sharded_pubsub` in python client tests. */
    @SneakyThrows
    @ParameterizedTest
    @EnumSource(MessageReadMethod.class)
    public void sharded_pubsub(MessageReadMethod method) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");
        skipTestsOnMac();

        GlideString channel = gs(UUID.randomUUID().toString());
        GlideString pubsubMessage = gs(UUID.randomUUID().toString());
        var subscriptions = Map.of(PubSubClusterChannelMode.SHARDED, Set.of(channel));

        var listener = createListener(false, method == MessageReadMethod.Callback, 1, subscriptions);
        var sender = (GlideClusterClient) createClient(false);
        clients.addAll(List.of(listener, sender));

        sender.publish(pubsubMessage, channel, true).get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the message

        verifyReceivedPubsubMessages(
                Set.of(Pair.of(1, new PubSubMessage(pubsubMessage, channel))), listener, method);
    }

    /** Similar to `test_sharded_pubsub_many_channels` in python client tests. */
    @SneakyThrows
    @ParameterizedTest
    @EnumSource(MessageReadMethod.class)
    public void sharded_pubsub_many_channels(MessageReadMethod method) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");
        skipTestsOnMac();

        int numChannels = 256;
        int pubsubMessagesPerChannel = 256;
        var pubsubMessages = new ArrayList<PubSubMessage>(numChannels * pubsubMessagesPerChannel);
        PubSubClusterChannelMode mode = PubSubClusterChannelMode.SHARDED;
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptions = Map.of(mode, new HashSet<>());

        for (var i = 0; i < numChannels; i++) {
            GlideString channel = gs(i + "-" + UUID.randomUUID());
            subscriptions.get(mode).add(channel);
            for (var j = 0; j < pubsubMessagesPerChannel; j++) {
                GlideString message = gs(i + "-" + j + "-" + UUID.randomUUID());
                pubsubMessages.add(new PubSubMessage(message, channel));
            }
        }

        var listener = createListener(false, method == MessageReadMethod.Callback, 1, subscriptions);
        var sender = (GlideClusterClient) createClient(false);
        clients.addAll(List.of(listener, sender));

        for (var pubsubMessage : pubsubMessages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel(), true).get();
        }
        sender.publish(UUID.randomUUID().toString(), UUID.randomUUID().toString(), true).get();

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        verifyReceivedPubsubMessages(
                pubsubMessages.stream().map(m -> Pair.of(1, m)).collect(Collectors.toSet()),
                listener,
                method);
    }

    /** Similar to `test_pubsub_pattern` in python client tests. */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, read messages via {1}")
    @MethodSource("getTestScenarios")
    public void pattern(boolean standalone, MessageReadMethod method) {
        skipTestsOnMac();
        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        Map<GlideString, GlideString> message2channels =
                Map.of(
                        gs(prefix + "1"),
                        gs(UUID.randomUUID().toString()),
                        gs(prefix + "2"),
                        gs(UUID.randomUUID().toString()));
        var subscriptions =
                Map.of(
                        standalone ? PubSubChannelMode.PATTERN : PubSubClusterChannelMode.PATTERN,
                        Set.of(pattern));

        var listener =
                createListener(standalone, method == MessageReadMethod.Callback, 1, subscriptions);
        var sender = createClient(standalone);
        clients.addAll(List.of(listener, sender));

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // need some time to propagate subscriptions - why?

        for (var entry : message2channels.entrySet()) {
            sender.publish(entry.getValue(), entry.getKey()).get();
        }
        sender.publish(UUID.randomUUID().toString(), "channel").get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        var expected =
                message2channels.entrySet().stream()
                        .map(e -> Pair.of(1, new PubSubMessage(e.getValue(), e.getKey(), pattern)))
                        .collect(Collectors.toSet());

        verifyReceivedPubsubMessages(expected, listener, method);
    }

    /** Similar to `test_pubsub_pattern_many_channels` in python client tests. */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, read messages via {1}")
    @MethodSource("getTestScenarios")
    public void pattern_many_channels(boolean standalone, MessageReadMethod method) {
        skipTestsOnMac();
        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        int numChannels = 256;
        int messagesPerChannel = 256;
        ChannelMode mode = standalone ? PubSubChannelMode.PATTERN : PubSubClusterChannelMode.PATTERN;
        var messages = new ArrayList<PubSubMessage>(numChannels * messagesPerChannel);
        var subscriptions = Map.of(mode, Set.of(pattern));

        for (var i = 0; i < numChannels; i++) {
            GlideString channel = gs(prefix + "-" + i + "-" + UUID.randomUUID());
            for (var j = 0; j < messagesPerChannel; j++) {
                GlideString message = gs(i + "-" + j + "-" + UUID.randomUUID());
                messages.add(new PubSubMessage(message, channel, pattern));
            }
        }

        var listener =
                createListener(standalone, method == MessageReadMethod.Callback, 1, subscriptions);
        var sender = createClient(standalone);
        clients.addAll(List.of(listener, sender));

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // need some time to propagate subscriptions - why?

        for (var pubsubMessage : messages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }
        sender.publish(UUID.randomUUID().toString(), "channel").get();
        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        verifyReceivedPubsubMessages(
                messages.stream().map(m -> Pair.of(1, m)).collect(Collectors.toSet()), listener, method);
    }

    /** Similar to `test_pubsub_combined_exact_and_pattern_one_client` in python client tests. */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, read messages via {1}")
    @MethodSource("getTestScenarios")
    public void combined_exact_and_pattern_one_client(boolean standalone, MessageReadMethod method) {
        skipTestsOnMac();
        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        int numChannels = 256;
        int messagesPerChannel = 256;
        var messages = new ArrayList<PubSubMessage>(numChannels * messagesPerChannel);
        ChannelMode mode = standalone ? PubSubChannelMode.EXACT : PubSubClusterChannelMode.EXACT;
        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                Map.of(
                        mode,
                        new HashSet<>(),
                        standalone ? PubSubChannelMode.PATTERN : PubSubClusterChannelMode.PATTERN,
                        Set.of(pattern));

        for (var i = 0; i < numChannels; i++) {
            GlideString channel = gs(i + "-" + UUID.randomUUID());
            subscriptions.get(mode).add(channel);
            for (var j = 0; j < messagesPerChannel; j++) {
                GlideString message = gs(i + "-" + j + "-" + UUID.randomUUID());
                messages.add(new PubSubMessage(message, channel));
            }
        }

        for (var j = 0; j < messagesPerChannel; j++) {
            GlideString pubsubMessage = gs(j + "-" + UUID.randomUUID());
            GlideString channel = gs(prefix + "-" + j + "-" + UUID.randomUUID());
            messages.add(new PubSubMessage(pubsubMessage, channel, pattern));
        }

        var listener =
                createListener(standalone, method == MessageReadMethod.Callback, 1, subscriptions);
        var sender = createClient(standalone);
        clients.addAll(List.of(listener, sender));

        for (var pubsubMessage : messages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        verifyReceivedPubsubMessages(
                messages.stream().map(m -> Pair.of(1, m)).collect(Collectors.toSet()), listener, method);
    }

    /**
     * Similar to `test_pubsub_combined_exact_and_pattern_multiple_clients` in python client tests.
     */
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, read messages via {1}")
    @MethodSource("getTestScenarios")
    public void combined_exact_and_pattern_multiple_clients(
            boolean standalone, MessageReadMethod method) {
        skipTestsOnMac();
        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        int numChannels = 256;
        var messages = new ArrayList<PubSubMessage>(numChannels * 2);
        ChannelMode mode = exact(standalone);
        Map<? extends ChannelMode, Set<GlideString>> subscriptions = Map.of(mode, new HashSet<>());

        for (var i = 0; i < numChannels; i++) {
            GlideString channel = gs(i + "-" + UUID.randomUUID());
            subscriptions.get(mode).add(channel);
            GlideString message = gs(i + "-" + UUID.randomUUID());
            messages.add(new PubSubMessage(message, channel));
        }

        for (var j = 0; j < numChannels; j++) {
            GlideString message = gs(j + "-" + UUID.randomUUID());
            GlideString channel = gs(prefix + "-" + j + "-" + UUID.randomUUID());
            messages.add(new PubSubMessage(message, channel, pattern));
        }

        var listenerExactSub =
                createListener(standalone, method == MessageReadMethod.Callback, 1, subscriptions);

        subscriptions = Map.of(pattern(standalone), Set.of(pattern));
        var listenerPatternSub =
                createListener(standalone, method == MessageReadMethod.Callback, 2, subscriptions);

        var sender = createClient(standalone);
        clients.addAll(List.of(listenerExactSub, listenerPatternSub, sender));

        for (var pubsubMessage : messages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        if (method == MessageReadMethod.Callback) {
            verifyReceivedPubsubMessages(
                    messages.stream()
                            .map(m -> Pair.of(m.getPattern().isEmpty() ? 1 : 2, m))
                            .collect(Collectors.toSet()),
                    listenerExactSub,
                    method);
        } else {
            verifyReceivedPubsubMessages(
                    messages.stream()
                            .filter(m -> m.getPattern().isEmpty())
                            .map(m -> Pair.of(1, m))
                            .collect(Collectors.toSet()),
                    listenerExactSub,
                    method);
            verifyReceivedPubsubMessages(
                    messages.stream()
                            .filter(m -> m.getPattern().isPresent())
                            .map(m -> Pair.of(2, m))
                            .collect(Collectors.toSet()),
                    listenerPatternSub,
                    method);
        }
    }

    /**
     * Similar to `test_pubsub_combined_exact_pattern_and_sharded_one_client` in python client tests.
     */
    @SneakyThrows
    @ParameterizedTest
    @EnumSource(MessageReadMethod.class)
    public void combined_exact_pattern_and_sharded_one_client(MessageReadMethod method) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");
        skipTestsOnMac();

        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        String shardPrefix = "{shard}";
        int numChannels = 256;
        var messages = new ArrayList<PubSubMessage>(numChannels * 2);
        var shardedMessages = new ArrayList<PubSubMessage>(numChannels);
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptions =
                Map.of(
                        PubSubClusterChannelMode.EXACT, new HashSet<>(),
                        PubSubClusterChannelMode.PATTERN, Set.of(pattern),
                        PubSubClusterChannelMode.SHARDED, new HashSet<>());

        for (var i = 0; i < numChannels; i++) {
            GlideString channel = gs(i + "-" + UUID.randomUUID());
            subscriptions.get(PubSubClusterChannelMode.EXACT).add(channel);
            GlideString message = gs(i + "-" + UUID.randomUUID());
            messages.add(new PubSubMessage(message, channel));
        }

        for (var i = 0; i < numChannels; i++) {
            GlideString channel = gs(shardPrefix + "-" + i + "-" + UUID.randomUUID());
            subscriptions.get(PubSubClusterChannelMode.SHARDED).add(channel);
            GlideString message = gs(i + "-" + UUID.randomUUID());
            shardedMessages.add(new PubSubMessage(message, channel));
        }

        for (var j = 0; j < numChannels; j++) {
            GlideString message = gs(j + "-" + UUID.randomUUID());
            GlideString channel = gs(prefix + "-" + j + "-" + UUID.randomUUID());
            messages.add(new PubSubMessage(message, channel, pattern));
        }

        var listener = createListener(false, method == MessageReadMethod.Callback, 1, subscriptions);
        var sender = (GlideClusterClient) createClient(false);
        clients.addAll(List.of(listener, sender));

        for (var pubsubMessage : messages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }
        for (var pubsubMessage : shardedMessages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel(), true).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        messages.addAll(shardedMessages);
        verifyReceivedPubsubMessages(
                messages.stream().map(m -> Pair.of(1, m)).collect(Collectors.toSet()), listener, method);
    }

    /** This test fully covers all `test_pubsub_*_co_existence` tests in python client. */
    @SneakyThrows
    @Test
    public void coexistense_of_sync_and_async_read() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");
        skipTestsOnMac();

        String prefix = "channel.";
        String pattern = prefix + "*";
        String shardPrefix = "{shard}";
        int numChannels = 256;
        var messages = new ArrayList<PubSubMessage>(numChannels * 2);
        var shardedMessages = new ArrayList<PubSubMessage>(numChannels);
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptions =
                Map.of(
                        PubSubClusterChannelMode.EXACT, new HashSet<>(),
                        PubSubClusterChannelMode.PATTERN, Set.of(gs(pattern)),
                        PubSubClusterChannelMode.SHARDED, new HashSet<>());

        for (var i = 0; i < numChannels; i++) {
            var channel = gs(i + "-" + UUID.randomUUID());
            subscriptions.get(PubSubClusterChannelMode.EXACT).add(channel);
            var message = gs(i + "-" + UUID.randomUUID());
            messages.add(new PubSubMessage(message, channel));
        }

        for (var i = 0; i < numChannels; i++) {
            var channel = gs(shardPrefix + "-" + i + "-" + UUID.randomUUID());
            subscriptions.get(PubSubClusterChannelMode.SHARDED).add(channel);
            var message = gs(i + "-" + UUID.randomUUID());
            shardedMessages.add(new PubSubMessage(message, channel));
        }

        for (var j = 0; j < numChannels; j++) {
            var message = gs(j + "-" + UUID.randomUUID());
            var channel = gs(prefix + "-" + j + "-" + UUID.randomUUID());
            messages.add(new PubSubMessage(message, channel, gs(pattern)));
        }

        var listener = createListener(false, false, 1, subscriptions);
        var sender = (GlideClusterClient) createClient(false);
        clients.addAll(List.of(listener, sender));

        for (var pubsubMessage : messages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }
        for (var pubsubMessage : shardedMessages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel(), true).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        messages.addAll(shardedMessages);

        var received = new LinkedHashSet<PubSubMessage>(messages.size());
        var rand = new Random();
        while (true) {
            if (rand.nextBoolean()) {
                CompletableFuture<PubSubMessage> messagePromise = listener.getPubSubMessage();
                if (messagePromise.isDone()) {
                    received.add(messagePromise.get());
                } else {
                    break; // all messages read
                }
            } else {
                var message = listener.tryGetPubSubMessage();
                if (message != null) {
                    received.add(message);
                } else {
                    break; // all messages read
                }
            }
        }

        // valkey can reorder the messages, so we can't validate that the order (without big delays
        // between sends)
        assertEquals(new LinkedHashSet<>(messages), received);
    }

    /**
     * Similar to `test_pubsub_combined_exact_pattern_and_sharded_multi_client` in python client
     * tests.
     */
    @SneakyThrows
    @ParameterizedTest
    @EnumSource(MessageReadMethod.class)
    public void combined_exact_pattern_and_sharded_multi_client(MessageReadMethod method) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");
        skipTestsOnMac();

        String prefix = "channel.";
        GlideString pattern = gs(prefix + "*");
        String shardPrefix = "{shard}";
        int numChannels = 256;
        var exactMessages = new ArrayList<PubSubMessage>(numChannels);
        var patternMessages = new ArrayList<PubSubMessage>(numChannels);
        var shardedMessages = new ArrayList<PubSubMessage>(numChannels);
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptionsExact =
                Map.of(PubSubClusterChannelMode.EXACT, new HashSet<>());
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptionsPattern =
                Map.of(PubSubClusterChannelMode.PATTERN, Set.of(pattern));
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptionsSharded =
                Map.of(PubSubClusterChannelMode.SHARDED, new HashSet<>());

        for (var i = 0; i < numChannels; i++) {
            GlideString channel = gs(i + "-" + UUID.randomUUID());
            subscriptionsExact.get(PubSubClusterChannelMode.EXACT).add(channel);
            GlideString pubsubMessage = gs(i + "-" + UUID.randomUUID());
            exactMessages.add(new PubSubMessage(pubsubMessage, channel));
        }

        for (var i = 0; i < numChannels; i++) {
            GlideString channel = gs(shardPrefix + "-" + i + "-" + UUID.randomUUID());
            subscriptionsSharded.get(PubSubClusterChannelMode.SHARDED).add(channel);
            GlideString message = gs(i + "-" + UUID.randomUUID());
            shardedMessages.add(new PubSubMessage(message, channel));
        }

        for (var j = 0; j < numChannels; j++) {
            GlideString message = gs(j + "-" + UUID.randomUUID());
            GlideString channel = gs(prefix + "-" + j + "-" + UUID.randomUUID());
            patternMessages.add(new PubSubMessage(message, channel, pattern));
        }

        var listenerExact =
                createListener(
                        false,
                        method == MessageReadMethod.Callback,
                        PubSubClusterChannelMode.EXACT.ordinal(),
                        subscriptionsExact);
        var listenerPattern =
                createListener(
                        false,
                        method == MessageReadMethod.Callback,
                        PubSubClusterChannelMode.PATTERN.ordinal(),
                        subscriptionsPattern);
        var listenerSharded =
                createListener(
                        false,
                        method == MessageReadMethod.Callback,
                        PubSubClusterChannelMode.SHARDED.ordinal(),
                        subscriptionsSharded);

        var sender = (GlideClusterClient) createClient(false);
        clients.addAll(List.of(listenerExact, listenerPattern, listenerSharded, sender));

        for (var pubsubMessage : exactMessages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }
        for (var pubsubMessage : patternMessages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel()).get();
        }
        for (var pubsubMessage : shardedMessages) {
            sender.publish(pubsubMessage.getMessage(), pubsubMessage.getChannel(), true).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        if (method == MessageReadMethod.Callback) {
            var expected = new HashSet<Pair<Integer, PubSubMessage>>();
            expected.addAll(
                    exactMessages.stream()
                            .map(m -> Pair.of(PubSubClusterChannelMode.EXACT.ordinal(), m))
                            .collect(Collectors.toSet()));
            expected.addAll(
                    patternMessages.stream()
                            .map(m -> Pair.of(PubSubClusterChannelMode.PATTERN.ordinal(), m))
                            .collect(Collectors.toSet()));
            expected.addAll(
                    shardedMessages.stream()
                            .map(m -> Pair.of(PubSubClusterChannelMode.SHARDED.ordinal(), m))
                            .collect(Collectors.toSet()));

            verifyReceivedPubsubMessages(expected, listenerExact, method);
        } else {
            verifyReceivedPubsubMessages(
                    exactMessages.stream()
                            .map(m -> Pair.of(PubSubClusterChannelMode.EXACT.ordinal(), m))
                            .collect(Collectors.toSet()),
                    listenerExact,
                    method);
            verifyReceivedPubsubMessages(
                    patternMessages.stream()
                            .map(m -> Pair.of(PubSubClusterChannelMode.PATTERN.ordinal(), m))
                            .collect(Collectors.toSet()),
                    listenerPattern,
                    method);
            verifyReceivedPubsubMessages(
                    shardedMessages.stream()
                            .map(m -> Pair.of(PubSubClusterChannelMode.SHARDED.ordinal(), m))
                            .collect(Collectors.toSet()),
                    listenerSharded,
                    method);
        }
    }

    /**
     * Similar to `test_pubsub_three_publishing_clients_same_name_with_sharded` in python client
     * tests.
     */
    @SneakyThrows
    @ParameterizedTest
    @EnumSource(MessageReadMethod.class)
    public void three_publishing_clients_same_name_with_sharded(MessageReadMethod method) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");
        skipTestsOnMac();

        GlideString channel = gs(UUID.randomUUID().toString());
        var exactMessage = new PubSubMessage(gs(UUID.randomUUID().toString()), channel);
        var patternMessage = new PubSubMessage(gs(UUID.randomUUID().toString()), channel, channel);
        var shardedMessage = new PubSubMessage(gs(UUID.randomUUID().toString()), channel);
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptionsExact =
                Map.of(PubSubClusterChannelMode.EXACT, Set.of(channel));
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptionsPattern =
                Map.of(PubSubClusterChannelMode.PATTERN, Set.of(channel));
        Map<PubSubClusterChannelMode, Set<GlideString>> subscriptionsSharded =
                Map.of(PubSubClusterChannelMode.SHARDED, Set.of(channel));

        var listenerExact =
                method == MessageReadMethod.Callback
                        ? (GlideClusterClient)
                                createListener(
                                        false, true, PubSubClusterChannelMode.EXACT.ordinal(), subscriptionsExact)
                        : (GlideClusterClient) createClientWithSubscriptions(false, subscriptionsExact);

        var listenerPattern =
                method == MessageReadMethod.Callback
                        ? createListener(
                                false, true, PubSubClusterChannelMode.PATTERN.ordinal(), subscriptionsPattern)
                        : (GlideClusterClient) createClientWithSubscriptions(false, subscriptionsPattern);

        var listenerSharded =
                method == MessageReadMethod.Callback
                        ? createListener(
                                false, true, PubSubClusterChannelMode.SHARDED.ordinal(), subscriptionsSharded)
                        : (GlideClusterClient) createClientWithSubscriptions(false, subscriptionsSharded);

        clients.addAll(List.of(listenerExact, listenerPattern, listenerSharded));

        listenerPattern.publish(exactMessage.getMessage(), channel).get();
        listenerSharded.publish(patternMessage.getMessage(), channel).get();
        listenerExact.publish(shardedMessage.getMessage(), channel, true).get();

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        if (method == MessageReadMethod.Callback) {
            var expected =
                    Set.of(
                            Pair.of(PubSubClusterChannelMode.EXACT.ordinal(), exactMessage),
                            Pair.of(
                                    PubSubClusterChannelMode.EXACT.ordinal(),
                                    new PubSubMessage(patternMessage.getMessage(), channel)),
                            Pair.of(PubSubClusterChannelMode.PATTERN.ordinal(), patternMessage),
                            Pair.of(
                                    PubSubClusterChannelMode.PATTERN.ordinal(),
                                    new PubSubMessage(exactMessage.getMessage(), channel, channel)),
                            Pair.of(PubSubClusterChannelMode.SHARDED.ordinal(), shardedMessage));

            verifyReceivedPubsubMessages(expected, listenerExact, method);
        } else {
            verifyReceivedPubsubMessages(
                    Set.of(
                            Pair.of(PubSubClusterChannelMode.EXACT.ordinal(), exactMessage),
                            Pair.of(
                                    PubSubClusterChannelMode.EXACT.ordinal(),
                                    new PubSubMessage(patternMessage.getMessage(), channel))),
                    listenerExact,
                    method);
            verifyReceivedPubsubMessages(
                    Set.of(
                            Pair.of(PubSubClusterChannelMode.PATTERN.ordinal(), patternMessage),
                            Pair.of(
                                    PubSubClusterChannelMode.PATTERN.ordinal(),
                                    new PubSubMessage(exactMessage.getMessage(), channel, channel))),
                    listenerPattern,
                    method);
            verifyReceivedPubsubMessages(
                    Set.of(Pair.of(PubSubClusterChannelMode.SHARDED.ordinal(), shardedMessage)),
                    listenerSharded,
                    method);
        }
    }

    @SneakyThrows
    @Test
    public void error_cases() {
        skipTestsOnMac();
        // client isn't configured with subscriptions
        var client = createClient(true);
        assertThrows(ConfigurationError.class, client::tryGetPubSubMessage);
        client.close();

        // client configured with callback and doesn't return pubsubMessages via API
        MessageCallback callback = (msg, ctx) -> fail();
        client =
                createClientWithSubscriptions(
                        true, Map.of(), Optional.of(callback), Optional.of(pubsubMessageQueue));
        assertThrows(ConfigurationError.class, client::tryGetPubSubMessage);
        client.close();

        // using sharded channels from different slots in a transaction causes a cross slot error
        var clusterClient = (GlideClusterClient) createClient(false);
        var transaction =
                new ClusterTransaction()
                        .publish("one", "abc", true)
                        .publish("two", "mnk", true)
                        .publish("three", "xyz", true);
        var exception =
                assertThrows(ExecutionException.class, () -> clusterClient.exec(transaction).get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().toLowerCase().contains("crossslot"));
    }

    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}, read messages via {1}")
    @MethodSource("getTestScenarios")
    public void transaction_with_all_types_of_messages(boolean standalone, MessageReadMethod method) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");
        skipTestsOnMac();
        assumeTrue(
                standalone, // TODO activate tests after fix
                "Test doesn't work on cluster due to Cross Slot error, probably a bug in `redis-rs`");

        String prefix = "channel";
        GlideString pattern = gs(prefix + "*");
        GlideString shardPrefix = gs("{shard}");
        GlideString channel = gs(UUID.randomUUID().toString());
        var exactMessage = new PubSubMessage(gs(UUID.randomUUID().toString()), channel);
        var patternMessage = new PubSubMessage(gs(UUID.randomUUID().toString()), gs(prefix), pattern);
        var shardedMessage = new PubSubMessage(gs(UUID.randomUUID().toString()), shardPrefix);

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Map.of(
                                PubSubChannelMode.EXACT,
                                Set.of(channel),
                                PubSubChannelMode.PATTERN,
                                Set.of(pattern))
                        : Map.of(
                                PubSubClusterChannelMode.EXACT,
                                Set.of(channel),
                                PubSubClusterChannelMode.PATTERN,
                                Set.of(pattern),
                                PubSubClusterChannelMode.SHARDED,
                                Set.of(shardPrefix));

        var listener =
                createListener(standalone, method == MessageReadMethod.Callback, 1, subscriptions);
        var sender = createClient(standalone);
        clients.addAll(List.of(listener, sender));

        if (standalone) {
            var transaction =
                    new Transaction()
                            .publish(exactMessage.getMessage(), exactMessage.getChannel())
                            .publish(patternMessage.getMessage(), patternMessage.getChannel());
            ((GlideClient) sender).exec(transaction).get();
        } else {
            var transaction =
                    new ClusterTransaction()
                            .publish(shardedMessage.getMessage(), shardedMessage.getChannel(), true)
                            .publish(exactMessage.getMessage(), exactMessage.getChannel())
                            .publish(patternMessage.getMessage(), patternMessage.getChannel());
            ((GlideClusterClient) sender).exec(transaction).get();
        }

        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        var expected =
                standalone
                        ? Set.of(Pair.of(1, exactMessage), Pair.of(1, patternMessage))
                        : Set.of(
                                Pair.of(1, exactMessage), Pair.of(1, patternMessage), Pair.of(1, shardedMessage));
        verifyReceivedPubsubMessages(expected, listener, method);
    }

    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    @Disabled(
            "No way of currently testing this, see https://github.com/valkey-io/valkey-glide/issues/1649")
    public void pubsub_exact_max_size_message(boolean standalone) {
        final GlideString channel = gs(UUID.randomUUID().toString());
        final GlideString message = gs("1".repeat(512 * 1024 * 1024)); // 512MB
        final GlideString message2 = gs("2".repeat(1 << 25)); // 3MB

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Map.of(PubSubChannelMode.EXACT, Set.of(channel))
                        : Map.of(PubSubClusterChannelMode.EXACT, Set.of(channel));
        var listener = createClientWithSubscriptions(standalone, subscriptions);
        var sender = createClient(standalone);
        clients.addAll(Arrays.asList(listener, sender));

        assertEquals(OK, sender.publish(message, channel).get());
        assertEquals(OK, sender.publish(message2, channel).get());

        // Allow the message to propagate.
        Thread.sleep(MESSAGE_DELIVERY_DELAY);

        PubSubMessage asyncMessage = listener.getPubSubMessage().get();
        assertEquals(message, asyncMessage.getMessage());
        assertEquals(channel, asyncMessage.getChannel());
        assertTrue(asyncMessage.getPattern().isEmpty());

        PubSubMessage syncMessage = listener.tryGetPubSubMessage();
        assertEquals(message2, syncMessage.getMessage());
        assertEquals(channel, syncMessage.getChannel());
        assertTrue(syncMessage.getPattern().isEmpty());

        // Assert there are no more messages to read.
        assertThrows(
                TimeoutException.class, () -> listener.getPubSubMessage().get(3, TimeUnit.SECONDS));
        assertNull(listener.tryGetPubSubMessage());
    }

    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {false})
    @Disabled(
            "No way of currently testing this, see https://github.com/valkey-io/valkey-glide/issues/1649")
    public void pubsub_sharded_max_size_message(boolean standalone) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        final GlideString channel = gs(UUID.randomUUID().toString());
        final GlideString message = gs("1".repeat(512 * 1024 * 1024)); // 512MB
        final GlideString message2 = gs("2".repeat(1 << 25)); // 3MB

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                Map.of(PubSubClusterChannelMode.SHARDED, Set.of(channel));
        var listener = createClientWithSubscriptions(standalone, subscriptions);
        var sender = createClient(standalone);
        clients.addAll(Arrays.asList(listener, sender));

        assertEquals(OK, sender.publish(message, channel).get());
        assertEquals(OK, ((GlideClusterClient) sender).publish(message2, channel, true).get());

        // Allow the message to propagate.
        Thread.sleep(MESSAGE_DELIVERY_DELAY);

        PubSubMessage asyncMessage = listener.getPubSubMessage().get();
        assertEquals(message, asyncMessage.getMessage());
        assertEquals(channel, asyncMessage.getChannel());
        assertTrue(asyncMessage.getPattern().isEmpty());

        PubSubMessage syncMessage = listener.tryGetPubSubMessage();
        assertEquals(message2, syncMessage.getMessage());
        assertEquals(channel, syncMessage.getChannel());
        assertTrue(syncMessage.getPattern().isEmpty());

        // Assert there are no more messages to read.
        assertThrows(
                TimeoutException.class,
                () -> {
                    listener.getPubSubMessage().get(3, TimeUnit.SECONDS);
                });

        assertNull(listener.tryGetPubSubMessage());
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    @Disabled(
            "No way of currently testing this, see https://github.com/valkey-io/valkey-glide/issues/1649")
    public void pubsub_exact_max_size_message_callback(boolean standalone) {
        final GlideString channel = gs(UUID.randomUUID().toString());
        final GlideString message = gs("1".repeat(512 * 1024 * 1024)); // 512MB

        ArrayList<PubSubMessage> callbackMessages = new ArrayList<>();
        final MessageCallback callback =
                (pubSubMessage, context) -> {
                    ArrayList<PubSubMessage> receivedMessages = (ArrayList<PubSubMessage>) context;
                    receivedMessages.add(pubSubMessage);
                };

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Map.of(PubSubChannelMode.EXACT, Set.of(channel))
                        : Map.of(PubSubClusterChannelMode.EXACT, Set.of(channel));

        var listener =
                createClientWithSubscriptions(
                        standalone,
                        subscriptions,
                        Optional.ofNullable(callback),
                        Optional.of(callbackMessages));
        var sender = createClient(standalone);
        clients.addAll(Arrays.asList(listener, sender));

        assertEquals(OK, sender.publish(message, channel).get());

        // Allow the message to propagate.
        Thread.sleep(MESSAGE_DELIVERY_DELAY);

        assertEquals(1, callbackMessages.size());
        assertEquals(message, callbackMessages.get(0).getMessage());
        assertEquals(channel, callbackMessages.get(0).getChannel());
        assertTrue(callbackMessages.get(0).getPattern().isEmpty());
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {false})
    @Disabled(
            "No way of currently testing this, see https://github.com/valkey-io/valkey-glide/issues/1649")
    public void pubsub_sharded_max_size_message_callback(boolean standalone) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        final GlideString channel = gs(UUID.randomUUID().toString());
        final GlideString message = gs("1".repeat(512 * 1024 * 1024)); // 512MB

        ArrayList<PubSubMessage> callbackMessages = new ArrayList<>();
        final MessageCallback callback =
                (pubSubMessage, context) -> {
                    ArrayList<PubSubMessage> receivedMessages = (ArrayList<PubSubMessage>) context;
                    receivedMessages.add(pubSubMessage);
                };

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                Map.of(PubSubClusterChannelMode.SHARDED, Set.of(channel));

        var listener =
                createClientWithSubscriptions(
                        standalone,
                        subscriptions,
                        Optional.ofNullable(callback),
                        Optional.of(callbackMessages));
        var sender = createClient(standalone);
        clients.addAll(Arrays.asList(listener, sender));

        assertEquals(OK, ((GlideClusterClient) sender).publish(message, channel, true).get());

        // Allow the message to propagate.
        Thread.sleep(MESSAGE_DELIVERY_DELAY);

        assertEquals(1, callbackMessages.size());
        assertEquals(message, callbackMessages.get(0).getMessage());
    }

    /** Test the behavior if the callback supplied to a subscription throws an uncaught exception. */
    @SuppressWarnings("unchecked")
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    public void pubsub_test_callback_exception(boolean standalone) {
        final GlideString channel = gs(UUID.randomUUID().toString());
        final GlideString message1 = gs("message1");
        final GlideString message2 = gs("message2");
        final GlideString errorMsg = gs("errorMsg");
        final GlideString message3 = gs("message3");

        ArrayList<PubSubMessage> callbackMessages = new ArrayList<>();
        final MessageCallback callback =
                (pubSubMessage, context) -> {
                    if (pubSubMessage.getMessage().equals(errorMsg)) {
                        throw new RuntimeException("Test callback error message");
                    }
                    ArrayList<PubSubMessage> receivedMessages = (ArrayList<PubSubMessage>) context;
                    receivedMessages.add(pubSubMessage);
                };

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Map.of(PubSubChannelMode.EXACT, Set.of(channel))
                        : Map.of(PubSubClusterChannelMode.EXACT, Set.of(channel));

        var listener =
                createClientWithSubscriptions(
                        standalone,
                        subscriptions,
                        Optional.ofNullable(callback),
                        Optional.of(callbackMessages));
        var sender = createClient(standalone);
        clients.addAll(Arrays.asList(listener, sender));

        assertEquals(OK, sender.publish(message1, channel).get());
        assertEquals(OK, sender.publish(message2, channel).get());
        assertEquals(OK, sender.publish(errorMsg, channel).get());
        assertEquals(OK, sender.publish(message3, channel).get());

        // Allow the message to propagate.
        Thread.sleep(MESSAGE_DELIVERY_DELAY);

        assertEquals(3, callbackMessages.size());
        assertEquals(message1, callbackMessages.get(0).getMessage());
        assertEquals(channel, callbackMessages.get(0).getChannel());
        assertTrue(callbackMessages.get(0).getPattern().isEmpty());

        assertEquals(message2, callbackMessages.get(1).getMessage());
        assertEquals(channel, callbackMessages.get(1).getChannel());
        assertTrue(callbackMessages.get(1).getPattern().isEmpty());

        // Ensure we can receive message 3 which is after the message that triggers a throw.
        assertEquals(message3, callbackMessages.get(2).getMessage());
        assertEquals(channel, callbackMessages.get(2).getChannel());
        assertTrue(callbackMessages.get(2).getPattern().isEmpty());
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    public void pubsub_with_binary(boolean standalone) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        GlideString channel = gs(new byte[] {(byte) 0xE2, 0x28, (byte) 0xA1});
        var message =
                new PubSubMessage(gs(new byte[] {(byte) 0xF0, 0x28, (byte) 0x8C, (byte) 0xBC}), channel);

        ArrayList<PubSubMessage> callbackMessages = new ArrayList<>();
        final MessageCallback callback =
                (pubSubMessage, context) -> {
                    ArrayList<PubSubMessage> receivedMessages = (ArrayList<PubSubMessage>) context;
                    receivedMessages.add(pubSubMessage);
                };

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Map.of(PubSubChannelMode.EXACT, Set.of(channel))
                        : Map.of(PubSubClusterChannelMode.EXACT, Set.of(channel));

        var listener = createClientWithSubscriptions(standalone, subscriptions);
        var listener2 =
                createClientWithSubscriptions(
                        standalone, subscriptions, Optional.of(callback), Optional.of(callbackMessages));
        var sender = createClient(standalone);
        clients.addAll(List.of(listener, listener2, sender));

        assertEquals(OK, sender.publish(message.getMessage(), channel).get());
        Thread.sleep(MESSAGE_DELIVERY_DELAY); // deliver the messages

        assertEquals(message, listener.tryGetPubSubMessage());
        assertEquals(1, callbackMessages.size());
        assertEquals(message, callbackMessages.get(0));
    }

    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    public void pubsub_channels(boolean standalone) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        // no channels exists yet
        var client = createClient(standalone);
        assertEquals(0, client.pubsubChannels().get().length);
        assertEquals(0, client.pubsubChannelsBinary().get().length);
        assertEquals(0, client.pubsubChannels("**").get().length);
        assertEquals(0, client.pubsubChannels(gs("**")).get().length);

        var channels = Set.of("test_channel1", "test_channel2", "some_channel");
        String pattern = "test_*";

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Map.of(
                                PubSubChannelMode.EXACT,
                                channels.stream().map(GlideString::gs).collect(Collectors.toSet()))
                        : Map.of(
                                PubSubClusterChannelMode.EXACT,
                                channels.stream().map(GlideString::gs).collect(Collectors.toSet()));

        var listener = createClientWithSubscriptions(standalone, subscriptions);
        clients.addAll(List.of(client, listener));

        // test without pattern
        assertEquals(channels, Set.of(client.pubsubChannels().get()));
        assertEquals(channels, Set.of(listener.pubsubChannels().get()));
        assertEquals(
                channels.stream().map(GlideString::gs).collect(Collectors.toSet()),
                Set.of(client.pubsubChannelsBinary().get()));
        assertEquals(
                channels.stream().map(GlideString::gs).collect(Collectors.toSet()),
                Set.of(listener.pubsubChannelsBinary().get()));

        // test with pattern
        assertEquals(
                Set.of("test_channel1", "test_channel2"), Set.of(client.pubsubChannels(pattern).get()));
        assertEquals(
                Set.of(gs("test_channel1"), gs("test_channel2")),
                Set.of(client.pubsubChannels(gs(pattern)).get()));
        assertEquals(
                Set.of("test_channel1", "test_channel2"), Set.of(listener.pubsubChannels(pattern).get()));
        assertEquals(
                Set.of(gs("test_channel1"), gs("test_channel2")),
                Set.of(listener.pubsubChannels(gs(pattern)).get()));

        // test with non-matching pattern
        assertEquals(0, client.pubsubChannels("non_matching_*").get().length);
        assertEquals(0, client.pubsubChannels(gs("non_matching_*")).get().length);
        assertEquals(0, listener.pubsubChannels("non_matching_*").get().length);
        assertEquals(0, listener.pubsubChannels(gs("non_matching_*")).get().length);
    }

    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    public void pubsub_numpat(boolean standalone) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        // no channels exists yet
        var client = createClient(standalone);
        assertEquals(0, client.pubsubNumPat().get());

        var patterns = Set.of("news.*", "announcements.*");

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Map.of(
                                PubSubChannelMode.PATTERN,
                                patterns.stream().map(GlideString::gs).collect(Collectors.toSet()))
                        : Map.of(
                                PubSubClusterChannelMode.PATTERN,
                                patterns.stream().map(GlideString::gs).collect(Collectors.toSet()));

        var listener = createClientWithSubscriptions(standalone, subscriptions);
        clients.addAll(List.of(client, listener));

        assertEquals(2, client.pubsubNumPat().get());
        assertEquals(2, listener.pubsubNumPat().get());
    }

    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    public void pubsub_numsub(boolean standalone) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        // no channels exists yet
        var client = createClient(standalone);
        var channels = new String[] {"channel1", "channel2", "channel3"};
        assertEquals(
                Arrays.stream(channels).collect(Collectors.toMap(c -> c, c -> 0L)),
                client.pubsubNumSub(channels).get());

        Map<? extends ChannelMode, Set<GlideString>> subscriptions1 =
                standalone
                        ? Map.of(
                                PubSubChannelMode.EXACT, Set.of(gs("channel1"), gs("channel2"), gs("channel3")))
                        : Map.of(
                                PubSubClusterChannelMode.EXACT,
                                Set.of(gs("channel1"), gs("channel2"), gs("channel3")));
        var listener1 = createClientWithSubscriptions(standalone, subscriptions1);

        Map<? extends ChannelMode, Set<GlideString>> subscriptions2 =
                standalone
                        ? Map.of(PubSubChannelMode.EXACT, Set.of(gs("channel2"), gs("channel3")))
                        : Map.of(PubSubClusterChannelMode.EXACT, Set.of(gs("channel2"), gs("channel3")));
        var listener2 = createClientWithSubscriptions(standalone, subscriptions2);

        Map<? extends ChannelMode, Set<GlideString>> subscriptions3 =
                standalone
                        ? Map.of(PubSubChannelMode.EXACT, Set.of(gs("channel3")))
                        : Map.of(PubSubClusterChannelMode.EXACT, Set.of(gs("channel3")));
        var listener3 = createClientWithSubscriptions(standalone, subscriptions3);

        Map<? extends ChannelMode, Set<GlideString>> subscriptions4 =
                standalone
                        ? Map.of(PubSubChannelMode.PATTERN, Set.of(gs("channel*")))
                        : Map.of(PubSubClusterChannelMode.PATTERN, Set.of(gs("channel*")));
        var listener4 = createClientWithSubscriptions(standalone, subscriptions4);

        clients.addAll(List.of(client, listener1, listener2, listener3, listener4));

        var expected = Map.of("channel1", 1L, "channel2", 2L, "channel3", 3L, "channel4", 0L);
        assertEquals(expected, client.pubsubNumSub(ArrayUtils.addFirst(channels, "channel4")).get());
        assertEquals(expected, listener1.pubsubNumSub(ArrayUtils.addFirst(channels, "channel4")).get());

        var expectedGs =
                Map.of(gs("channel1"), 1L, gs("channel2"), 2L, gs("channel3"), 3L, gs("channel4"), 0L);
        assertEquals(
                expectedGs,
                client
                        .pubsubNumSub(
                                new GlideString[] {gs("channel1"), gs("channel2"), gs("channel3"), gs("channel4")})
                        .get());
        assertEquals(
                expectedGs,
                listener2
                        .pubsubNumSub(
                                new GlideString[] {gs("channel1"), gs("channel2"), gs("channel3"), gs("channel4")})
                        .get());
    }

    @SneakyThrows
    @ParameterizedTest(name = "standalone = {0}")
    @ValueSource(booleans = {true, false})
    public void pubsub_channels_and_numpat_and_numsub_in_transaction(boolean standalone) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in version 7");

        var prefix = "{boo}-";
        var route = new SlotKeyRoute(prefix, SlotType.PRIMARY);
        var client = createClient(standalone);
        var channels =
                new String[] {prefix + "test_channel1", prefix + "test_channel2", prefix + "some_channel"};
        var patterns = Set.of(prefix + "news.*", prefix + "announcements.*");
        String pattern = prefix + "test_*";

        var transaction =
                (standalone ? new Transaction() : new ClusterTransaction())
                        .pubsubChannels()
                        .pubsubChannels(pattern)
                        .pubsubNumPat()
                        .pubsubNumSub(channels);
        // no channels exists yet
        var result =
                standalone
                        ? ((GlideClient) client).exec((Transaction) transaction).get()
                        : ((GlideClusterClient) client).exec((ClusterTransaction) transaction, route).get();
        assertDeepEquals(
                new Object[] {
                    new String[0], // pubsubChannels()
                    new String[0], // pubsubChannels(pattern)
                    0L, // pubsubNumPat()
                    Arrays.stream(channels)
                            .collect(Collectors.toMap(c -> c, c -> 0L)), // pubsubNumSub(channels)
                },
                result);

        Map<? extends ChannelMode, Set<GlideString>> subscriptions =
                standalone
                        ? Map.of(
                                PubSubChannelMode.EXACT,
                                Arrays.stream(channels).map(GlideString::gs).collect(Collectors.toSet()),
                                PubSubChannelMode.PATTERN,
                                patterns.stream().map(GlideString::gs).collect(Collectors.toSet()))
                        : Map.of(
                                PubSubClusterChannelMode.EXACT,
                                Arrays.stream(channels).map(GlideString::gs).collect(Collectors.toSet()),
                                PubSubClusterChannelMode.PATTERN,
                                patterns.stream().map(GlideString::gs).collect(Collectors.toSet()));

        var listener = createClientWithSubscriptions(standalone, subscriptions);
        clients.addAll(List.of(client, listener));

        result =
                standalone
                        ? ((GlideClient) client).exec((Transaction) transaction).get()
                        : ((GlideClusterClient) client).exec((ClusterTransaction) transaction, route).get();

        // convert arrays to sets, because we can't compare arrays - they received reordered
        result[0] = Set.of((Object[]) result[0]);
        result[1] = Set.of((Object[]) result[1]);

        assertDeepEquals(
                new Object[] {
                    Set.of(channels), // pubsubChannels()
                    Set.of("{boo}-test_channel1", "{boo}-test_channel2"), // pubsubChannels(pattern)
                    2L, // pubsubNumPat()
                    Arrays.stream(channels)
                            .collect(Collectors.toMap(c -> c, c -> 1L)), // pubsubNumSub(channels)
                },
                result);
    }
}
