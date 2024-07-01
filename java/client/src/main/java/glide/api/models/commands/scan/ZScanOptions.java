/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

import glide.api.commands.SortedSetBaseCommands;
import lombok.experimental.SuperBuilder;

/**
 * Optional arguments for {@link SortedSetBaseCommands#zscan(String, String, ZScanOptions)}.
 *
 * @see <a href="https://valkey.io/commands/zscan/">valkey.io</a>
 */
@SuperBuilder
public class ZScanOptions extends BaseScanOptions {}
