/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.GenericBaseCommands;
import glide.api.models.GlideString;
import glide.api.models.Script;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * Optional arguments for {@link GenericBaseCommands#invokeScript(Script, ScriptOptionsGlideString)}
 * command.
 *
 * @see <a href="https://redis.io/commands/evalsha/">redis.io</a>
 */
@Builder
public class ScriptOptionsGlideString {

    /** The keys that are used in the script. */
    @Singular @Getter private final List<GlideString> keys;

    /** The arguments for the script. */
    @Singular @Getter private final List<GlideString> args;
}
