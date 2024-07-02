/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import lombok.experimental.SuperBuilder;

/**
 * Optional arguments to {@link GenericClusterCommands#sort(String, SortClusterOptions)}, {@link
 * GenericClusterCommands#sort(GlideString, SortClusterOptions)}, {@link
 * GenericClusterCommands#sortReadOnly(String, SortClusterOptions)}, {@link
 * GenericClusterCommands#sortReadOnly(GlideString, SortClusterOptions)}, {@link
 * GenericClusterCommands#sortStore(String, String, SortClusterOptions)} and {@link
 * GenericClusterCommands#sortStore(GlideString, GlideString, SortClusterOptions)},
 */
@SuperBuilder
public class SortClusterOptions extends SortBaseOptions {}
