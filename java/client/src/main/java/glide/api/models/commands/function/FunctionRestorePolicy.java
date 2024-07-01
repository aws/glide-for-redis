/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.function;

import glide.api.commands.ScriptingAndFunctionsClusterCommands;
import glide.api.commands.ScriptingAndFunctionsCommands;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;

/**
 * Option for <code>FUNCTION RESTORE</code> command: {@link
 * ScriptingAndFunctionsCommands#functionRestore(byte[], FunctionRestorePolicy)}, {@link
 * ScriptingAndFunctionsClusterCommands#functionRestore(byte[], FunctionRestorePolicy)}, and {@link
 * ScriptingAndFunctionsClusterCommands#functionRestore(byte[], FunctionRestorePolicy, Route)}.
 *
 * @see <a href="https://redis.io/docs/latest/commands/function-restore/">redis.io</a> for details.
 */
public enum FunctionRestorePolicy {
    /**
     * Appends the restored libraries to the existing libraries and aborts on collision. This is the
     * default policy.
     */
    APPEND,
    /** Deletes all existing libraries before restoring the payload. */
    FLUSH,
    /**
     * Appends the restored libraries to the existing libraries, replacing any existing ones in case
     * of name collisions. Note that this policy doesn't prevent function name collisions, only
     * libraries.
     */
    REPLACE
}
