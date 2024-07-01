/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static glide.api.models.TransactionTests.buildArgs;
import static glide.api.models.commands.SortBaseOptions.ALPHA_COMMAND_STRING;
import static glide.api.models.commands.SortBaseOptions.LIMIT_COMMAND_STRING;
import static glide.api.models.commands.SortBaseOptions.OrderBy.ASC;
import static glide.api.models.commands.SortBaseOptions.STORE_COMMAND_STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static redis_request.RedisRequestOuterClass.RequestType.SPublish;
import static redis_request.RedisRequestOuterClass.RequestType.Sort;
import static redis_request.RedisRequestOuterClass.RequestType.SortReadOnly;

import glide.api.models.commands.SortBaseOptions;
import glide.api.models.commands.SortClusterOptions;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import redis_request.RedisRequestOuterClass.Command;
import redis_request.RedisRequestOuterClass.Command.ArgsArray;
import redis_request.RedisRequestOuterClass.RequestType;

public class ClusterTransactionTests {

    @Test
    public void cluster_transaction_builds_protobuf_request() {
        ClusterTransaction transaction = new ClusterTransaction();
        List<Pair<RequestType, ArgsArray>> results = new LinkedList<>();

        transaction.spublish("ch1", "msg");
        results.add(Pair.of(SPublish, buildArgs("ch1", "msg")));

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
            Command protobuf = protobufTransaction.getCommands(idx);

            assertEquals(results.get(idx).getLeft(), protobuf.getRequestType());
            assertEquals(
                    results.get(idx).getRight().getArgsCount(), protobuf.getArgsArray().getArgsCount());
            assertEquals(results.get(idx).getRight(), protobuf.getArgsArray());
        }
    }
}
