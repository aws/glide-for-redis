/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.models.Script;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * Optional arguments for {@link glide.api.commands.GenericBaseCommands#invokeScript(Script,
 * ScriptOptions)} command.
 *
 * @see <a href="https://redis.io/commands/evalsha/">redis.io</a>
 */
@Builder
public final class ScriptOptions {

    /** The keys that are accessed by the script. */
    @Singular @Getter private final List<String> keys;

    /** The arguments provided to the script. */
    @Singular @Getter private final List<String> args;
}
