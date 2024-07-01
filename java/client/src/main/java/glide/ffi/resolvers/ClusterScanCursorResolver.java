/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

public final class ClusterScanCursorResolver {
    // TODO: consider lazy loading the glide_rs library
    static {
        NativeUtils.loadGlideLib();
    }

    public static native void releaseNativeCursor(String cursor);
}
