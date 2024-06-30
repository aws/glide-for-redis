/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

import glide.api.models.commands.scan.ClusterScanCursor;
import lombok.NonNull;

public final class NativeClusterScanCursor implements ClusterScanCursor {
    // TODO: consider lazy loading the glide_rs library
    static {
        NativeUtils.loadGlideLib();
    }

    private String cursor;

    public NativeClusterScanCursor() {
        this("0");
    }

    // This is for internal use only.
    public NativeClusterScanCursor(@NonNull String nativeCursor) {
        this.cursor = nativeCursor;
    }

    @Override
    public String getCursor() {
        return cursor;
    }

    @Override
    public void close() throws Exception {
        if (!"0".equals(cursor)) {
            releaseNativeCursor(cursor);

            // Save the cursor as "0" now to avoid double-free (if close() gets called more than once).
            cursor = "0";
        }
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

    private static native void releaseNativeCursor(String cursor);
}
