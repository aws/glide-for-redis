/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.bitmap;

import glide.api.commands.BitmapBaseCommands;

/**
 * Defines bitwise operation for {@link BitmapBaseCommands#bitop(BitwiseOperation, String,
 * String[])}. Specifies bitwise operation to perform between keys.
 *
 * @see <a href="https://redis.io/commands/bitop/">redis.io</a>
 */
public enum BitwiseOperation {
    AND,
    OR,
    XOR,
    NOT
}
