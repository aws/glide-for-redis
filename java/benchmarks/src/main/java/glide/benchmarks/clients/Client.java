/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.clients;

import glide.benchmarks.utils.ConnectionSettings;

/** A Redis client interface */
public interface Client {
    void connectToRedis(ConnectionSettings connectionSettings);

    default void closeConnection() {}

    String getName();
}
