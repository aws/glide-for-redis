/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.SortedSetBaseCommands;

/**
 * Mandatory option for {@link SortedSetBaseCommands#bzmpop} and for {@link
 * SortedSetBaseCommands#zmpop}.<br>
 * Defines which elements to pop from the sorted set.
 */
public enum ScoreFilter {
    /** Pop elements with the lowest scores. */
    MIN,
    /** Pop elements with the highest scores. */
    MAX
}
