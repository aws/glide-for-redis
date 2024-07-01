/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

import glide.api.commands.SetBaseCommands;
import lombok.experimental.SuperBuilder;

/**
 * Optional arguments for {@link SetBaseCommands#sscan(String, String, SScanOptions)}.
 *
 * @see <a href="https://valkey.io/commands/sscan/">valkey.io</a>
 */
@SuperBuilder
public class SScanOptions extends BaseScanOptions {}
