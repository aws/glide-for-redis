/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.GenericBaseCommands;
import lombok.experimental.SuperBuilder;

/**
 * Optional arguments to {@link GenericBaseCommands#sort(String, SortClusterOptions)}, {@link
 * GenericBaseCommands#sortReadOnly(String, SortClusterOptions)}, and {@link
 * GenericBaseCommands#sortStore(String, String, SortClusterOptions)}
 */
@SuperBuilder
public class SortClusterOptions extends SortBaseOptions {}
