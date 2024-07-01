/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.clients;

/** A Redis client with sync capabilities */
public interface SyncClient extends Client {
    void set(String key, String value);

    String get(String key);
}
