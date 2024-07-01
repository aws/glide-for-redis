/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

import java.util.concurrent.CompletableFuture;

public interface ClusterScanCursor extends AutoCloseable {
    // The caller must wait until the most recent call to next() completed.
    Object[] getCurrentData();

    // When this future completes, this updates the current data on the cursor and the result
    // is true if there is more data available to request.
    //
    // The caller must wait until the most recent call to next() completed.
    CompletableFuture<Boolean> next();

    // When this future completes, this updates the current data on the cursor and the result
    // is true if there is more data available to request.
    //
    // The caller must wait until the most recent call to next() completed.
    CompletableFuture<Boolean> next(ScanOptions options);

    @Override
    void close();
}
