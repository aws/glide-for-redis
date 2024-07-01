/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

import glide.api.models.commands.scan.ClusterScanCursor;
import lombok.NonNull;

public final class NativeClusterScanCursor implements ClusterScanCursor {
    // TODO: consider lazy loading the glide_rs library
    static {
        NativeUtils.loadGlideLib();
    }

    // TODO: This should be made a constant in Rust.
    private static final String FINISHED_CURSOR_MARKER = "finished";

    private final String cursor;
    private final boolean isMarkedFinished;
    private boolean isClosed = false;

    // This is for internal use only.
    public NativeClusterScanCursor(@NonNull String nativeCursor) {
        this.cursor = nativeCursor;
        this.isMarkedFinished = nativeCursor.equals(FINISHED_CURSOR_MARKER);
    }

    @Override
    public String getCursor() {
        return cursor;
    }

    @Override
    public boolean isFinished() {
        return isMarkedFinished;
    }

    @Override
    public void close() throws Exception {
        if (!isMarkedFinished && !isClosed) {
            releaseNativeCursor(cursor);

            // Mark the cursor as closed to avoid double-free (if close() gets called more than once).
            isClosed = true;
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
