/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

public class ScriptResolver {

    // TODO: consider lazy loading the glide_rs library
    static {
        System.loadLibrary("glide_rs");
    }

    /**
     * Loads a Lua script object to Redis
     *
     * @param code The Lua script
     * @return String representing the saved hash
     */
    public static native String storeScript(String code);

    /**
     * Unload or drop the stored Lua script from Redis by hash
     *
     * @param hash
     */
    public static native void dropScript(String hash);
}
