/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static glide.ffi.resolvers.ScriptResolver.dropScript;
import static glide.ffi.resolvers.ScriptResolver.storeScript;

import glide.api.commands.GenericBaseCommands;
import lombok.Getter;

/**
 * A wrapper for a Script object for {@link GenericBaseCommands#invokeScript(Script)} As long as
 * this object is not closed, the script's code is saved in memory, and can be resent to the server.
 * Script should be enclosed with a try-with-resource block or {@link Script#close()} must be called
 * to invalidate the code hash.
 */
public class Script implements AutoCloseable {

    /** Hash string representing the code. */
    @Getter private final String hash;

    /**
     * Wraps around creating a Script object from <code>code</code>.
     *
     * @param code To execute with a ScriptInvoke call.
     */
    public Script(String code) {
        hash = storeScript(code);
    }

    /** Drop the linked script from glide-rs <code>code</code>. */
    @Override
    public void close() throws Exception {
        dropScript(hash);
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            // Drop the linked script on garbage collection.
            this.close();
        } finally {
            super.finalize();
        }
    }
}
