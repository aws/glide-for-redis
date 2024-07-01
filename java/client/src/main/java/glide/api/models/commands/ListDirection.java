/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.ListBaseCommands;

/**
 * Enumeration representing element popping or adding direction for the {@link ListBaseCommands}
 * commands.
 */
public enum ListDirection {
    /**
     * Represents the option that elements should be popped from or added to the left side of a list.
     */
    LEFT,
    /**
     * Represents the option that elements should be popped from or added to the right side of a list.
     */
    RIGHT
}
