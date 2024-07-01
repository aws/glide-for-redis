/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

public class ScriptResolver {

    // TODO: consider lazy loading the glide_rs library
    static {
        NativeUtils.loadGlideLib();
    }

    /**
     * Loads a Lua script into the scripts cache, without executing it.
     *
     * @param code The Lua script
     * @return String representing the saved hash
     */
    public static native String storeScript(String code);

    /**
     * Unload or drop the stored Lua script from the script cache.
     *
     * @param hash
     */
    public static native void dropScript(String hash);
}
