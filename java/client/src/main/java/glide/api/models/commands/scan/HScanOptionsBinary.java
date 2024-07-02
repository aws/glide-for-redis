/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

import glide.api.commands.HashBaseCommands;
import lombok.experimental.SuperBuilder;

/**
 * Optional arguments for {@link HashBaseCommands#hscan(GlideString, GlideString,
 * HScanOptionsBinary)}.
 *
 * @see <a href="https://valkey.io/commands/hscan/">valkey.io</a>
 */
@SuperBuilder
public class HScanOptionsBinary extends BaseScanOptionsBinary {}
