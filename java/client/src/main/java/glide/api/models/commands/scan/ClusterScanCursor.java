/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

public class ClusterScanCursor implements AutoCloseable {
    private String cursor;

    public ClusterScanCursor() {
        this("0");
    }

    ClusterScanCursor(String nativeCursor) {
        this.cursor = nativeCursor;
    }

    @Override
    public void close() throws Exception {
        // Release the native cursor and set the cursor field to "0".
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            // Release the native cursor
            this.close();
        } finally {
            super.finalize();
        }
    }

    // TODO: Add a native function to release the Rust cursor.
}
