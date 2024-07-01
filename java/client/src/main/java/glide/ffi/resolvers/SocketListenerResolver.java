/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

public class SocketListenerResolver {

    /** Make an FFI call to Glide to open a UDS socket to connect to. */
    private static native String startSocketListener() throws Exception;

    // TODO: consider lazy loading the glide_rs library
    static {
        NativeUtils.loadGlideLib();
    }

    /**
     * Make an FFI call to obtain the socket path.
     *
     * @return A UDS path.
     */
    public static String getSocket() {
        try {
            return startSocketListener();
        } catch (Exception | UnsatisfiedLinkError e) {
            System.err.printf("Failed to create a UDS connection: %s%n%n", e);
            throw new RuntimeException(e);
        }
    }
}
