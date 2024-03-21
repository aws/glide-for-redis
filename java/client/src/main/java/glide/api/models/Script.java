/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static glide.ffi.resolvers.ScriptResolver.dropScript;
import static glide.ffi.resolvers.ScriptResolver.storeScript;

/**
 * A wrapper for a Script object for {@link
 * glide.api.commands.GenericBaseCommands#invokeScript(Script)} As long as this object is not
 * closed, the script's code is saved in memory, and can be resent to the server.
 */
public class Script implements AutoCloseable {

    /** hash string representing the code */
    private final String hash;

    /**
     * Wraps around creating a Script object from <code>code</code>.
     *
     * @param code To execute with a ScriptInvoke call
     */
    public Script(String code) {
        this.hash = storeScript(code);
    }

    /**
     * Retrieve the stored hash
     *
     * @return hash
     */
    public String getHash() {
        return this.hash;
    }

    /** Drop the linked script from glide-rs <code>code</code>. */
    @Override
    public void close() throws Exception {
        dropScript(this.hash);
    }
}
