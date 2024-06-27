/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static command_request.CommandRequestOuterClass.RequestType.Sort;
import static command_request.CommandRequestOuterClass.RequestType.SortReadOnly;
import static glide.api.models.TransactionTests.buildArgs;
import static glide.api.models.commands.SortBaseOptions.ALPHA_COMMAND_STRING;
import static glide.api.models.commands.SortBaseOptions.LIMIT_COMMAND_STRING;
import static glide.api.models.commands.SortBaseOptions.OrderBy.ASC;
import static glide.api.models.commands.SortBaseOptions.STORE_COMMAND_STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;

import command_request.CommandRequestOuterClass;
import glide.api.models.commands.SortBaseOptions;
import glide.api.models.commands.SortClusterOptions;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ClusterTransactionTests {
    private static Stream<Arguments> getTransactionBuilders() {
        return Stream.of(
                Arguments.of(new ClusterTransaction()), Arguments.of(new ClusterTransaction()));
    }

    @ParameterizedTest
    @MethodSource("getTransactionBuilders")
    public void cluster_transaction_builds_protobuf_request(ClusterTransaction transaction) {
        List<Pair<CommandRequestOuterClass.RequestType, CommandRequestOuterClass.Command.ArgsArray>>
                results = new LinkedList<>();

        transaction.sortReadOnly(
                "key1",
                SortClusterOptions.builder()
                        .orderBy(ASC)
                        .alpha()
                        .limit(new SortBaseOptions.Limit(0L, 1L))
                        .build());
        results.add(
                Pair.of(
                        SortReadOnly,
                        buildArgs(
                                "key1", LIMIT_COMMAND_STRING, "0", "1", ASC.toString(), ALPHA_COMMAND_STRING)));

        transaction.sort(
                "key1",
                SortClusterOptions.builder()
                        .orderBy(ASC)
                        .alpha()
                        .limit(new SortBaseOptions.Limit(0L, 1L))
                        .build());
        results.add(
                Pair.of(
                        Sort,
                        buildArgs(
                                "key1", LIMIT_COMMAND_STRING, "0", "1", ASC.toString(), ALPHA_COMMAND_STRING)));

        transaction.sortStore(
                "key1",
                "key2",
                SortClusterOptions.builder()
                        .orderBy(ASC)
                        .alpha()
                        .limit(new SortBaseOptions.Limit(0L, 1L))
                        .build());
        results.add(
                Pair.of(
                        Sort,
                        buildArgs(
                                "key1",
                                LIMIT_COMMAND_STRING,
                                "0",
                                "1",
                                ASC.toString(),
                                ALPHA_COMMAND_STRING,
                                STORE_COMMAND_STRING,
                                "key2")));

        var protobufTransaction = transaction.getProtobufTransaction().build();

        for (int idx = 0; idx < protobufTransaction.getCommandsCount(); idx++) {
            CommandRequestOuterClass.Command protobuf = protobufTransaction.getCommands(idx);

            assertEquals(results.get(idx).getLeft(), protobuf.getRequestType());
            assertEquals(
                    results.get(idx).getRight().getArgsCount(), protobuf.getArgsArray().getArgsCount());
            assertEquals(results.get(idx).getRight(), protobuf.getArgsArray());
        }
    }
}
