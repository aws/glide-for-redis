/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static glide.api.commands.GenericBaseCommands.REPLACE_REDIS_API;
import static glide.api.commands.GenericCommands.DB_REDIS_API;
import static glide.api.models.TransactionTests.buildArgs;
import static glide.api.models.commands.SortBaseOptions.ALPHA_COMMAND_STRING;
import static glide.api.models.commands.SortBaseOptions.LIMIT_COMMAND_STRING;
import static glide.api.models.commands.SortBaseOptions.Limit;
import static glide.api.models.commands.SortBaseOptions.OrderBy.DESC;
import static glide.api.models.commands.SortBaseOptions.STORE_COMMAND_STRING;
import static glide.api.models.commands.SortOptions.BY_COMMAND_STRING;
import static glide.api.models.commands.SortOptions.GET_COMMAND_STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static redis_request.RedisRequestOuterClass.RequestType.Copy;
import static redis_request.RedisRequestOuterClass.RequestType.Move;
import static redis_request.RedisRequestOuterClass.RequestType.Select;
import static redis_request.RedisRequestOuterClass.RequestType.Sort;
import static redis_request.RedisRequestOuterClass.RequestType.SortReadOnly;

import glide.api.models.commands.SortOptions;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import redis_request.RedisRequestOuterClass;

public class StandaloneTransactionTests {
    @Test
    public void standalone_transaction_commands() {
        List<Pair<RedisRequestOuterClass.RequestType, RedisRequestOuterClass.Command.ArgsArray>>
                results = new LinkedList<>();
        Transaction transaction = new Transaction();

        transaction.select(5L);
        results.add(Pair.of(Select, buildArgs("5")));
        transaction.move("testKey", 2L);
        results.add(Pair.of(Move, buildArgs("testKey", "2")));
        transaction.copy("key1", "key2", 1, true);
        results.add(Pair.of(Copy, buildArgs("key1", "key2", DB_REDIS_API, "1", REPLACE_REDIS_API)));

        transaction.sort(
                "key1",
                SortOptions.builder()
                        .byPattern("byPattern")
                        .getPatterns(List.of("getPattern1", "getPattern2"))
                        .build());
        results.add(
                Pair.of(
                        Sort,
                        buildArgs(
                                "key1",
                                BY_COMMAND_STRING,
                                "byPattern",
                                GET_COMMAND_STRING,
                                "getPattern1",
                                GET_COMMAND_STRING,
                                "getPattern2")));
        transaction.sort(
                "key1",
                SortOptions.builder()
                        .orderBy(DESC)
                        .alpha()
                        .limit(new Limit(0L, 1L))
                        .byPattern("byPattern")
                        .getPatterns(List.of("getPattern1", "getPattern2"))
                        .build());
        results.add(
                Pair.of(
                        Sort,
                        buildArgs(
                                "key1",
                                LIMIT_COMMAND_STRING,
                                "0",
                                "1",
                                DESC.toString(),
                                ALPHA_COMMAND_STRING,
                                BY_COMMAND_STRING,
                                "byPattern",
                                GET_COMMAND_STRING,
                                "getPattern1",
                                GET_COMMAND_STRING,
                                "getPattern2")));
        transaction.sortReadOnly(
                "key1",
                SortOptions.builder()
                        .byPattern("byPattern")
                        .getPatterns(List.of("getPattern1", "getPattern2"))
                        .build());
        results.add(
                Pair.of(
                        SortReadOnly,
                        buildArgs(
                                "key1",
                                BY_COMMAND_STRING,
                                "byPattern",
                                GET_COMMAND_STRING,
                                "getPattern1",
                                GET_COMMAND_STRING,
                                "getPattern2")));
        transaction.sortReadOnly(
                "key1",
                SortOptions.builder()
                        .orderBy(DESC)
                        .alpha()
                        .limit(new Limit(0L, 1L))
                        .byPattern("byPattern")
                        .getPatterns(List.of("getPattern1", "getPattern2"))
                        .build());
        results.add(
                Pair.of(
                        SortReadOnly,
                        buildArgs(
                                "key1",
                                LIMIT_COMMAND_STRING,
                                "0",
                                "1",
                                DESC.toString(),
                                ALPHA_COMMAND_STRING,
                                BY_COMMAND_STRING,
                                "byPattern",
                                GET_COMMAND_STRING,
                                "getPattern1",
                                GET_COMMAND_STRING,
                                "getPattern2")));
        transaction.sortStore(
                "key1",
                "key2",
                SortOptions.builder()
                        .byPattern("byPattern")
                        .getPatterns(List.of("getPattern1", "getPattern2"))
                        .build());
        results.add(
                Pair.of(
                        Sort,
                        buildArgs(
                                "key1",
                                BY_COMMAND_STRING,
                                "byPattern",
                                GET_COMMAND_STRING,
                                "getPattern1",
                                GET_COMMAND_STRING,
                                "getPattern2",
                                STORE_COMMAND_STRING,
                                "key2")));
        transaction.sortStore(
                "key1",
                "key2",
                SortOptions.builder()
                        .orderBy(DESC)
                        .alpha()
                        .limit(new Limit(0L, 1L))
                        .byPattern("byPattern")
                        .getPatterns(List.of("getPattern1", "getPattern2"))
                        .build());
        results.add(
                Pair.of(
                        Sort,
                        buildArgs(
                                "key1",
                                LIMIT_COMMAND_STRING,
                                "0",
                                "1",
                                DESC.toString(),
                                ALPHA_COMMAND_STRING,
                                BY_COMMAND_STRING,
                                "byPattern",
                                GET_COMMAND_STRING,
                                "getPattern1",
                                GET_COMMAND_STRING,
                                "getPattern2",
                                STORE_COMMAND_STRING,
                                "key2")));

        var protobufTransaction = transaction.getProtobufTransaction().build();

        for (int idx = 0; idx < protobufTransaction.getCommandsCount(); idx++) {
            RedisRequestOuterClass.Command protobuf = protobufTransaction.getCommands(idx);

            assertEquals(results.get(idx).getLeft(), protobuf.getRequestType());
            assertEquals(
                    results.get(idx).getRight().getArgsCount(), protobuf.getArgsArray().getArgsCount());
            assertEquals(results.get(idx).getRight(), protobuf.getArgsArray());
        }
    }
}
