/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.ListBaseCommands;

/**
 * Options for {@link ListBaseCommands#linsert}.
 *
 * @see <a href="https://redis.io/commands/linsert/">redis.io</a>
 */
public class LInsertOptions {
    /** Defines where to insert new elements into a list. */
    public enum InsertPosition {
        /** Insert new element before the pivot. */
        BEFORE,
        /** Insert new element after the pivot. */
        AFTER
    }
}
