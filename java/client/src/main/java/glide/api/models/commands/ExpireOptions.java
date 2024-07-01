/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.GenericBaseCommands;
import glide.api.models.GlideString;
import lombok.RequiredArgsConstructor;

/**
 * Optional arguments for {@link GenericBaseCommands#expire(String, long, ExpireOptions)}, and
 * similar commands.
 *
 * @see <a href="https://redis.io/commands/expire/">redis.io</a>
 */
@RequiredArgsConstructor
public enum ExpireOptions {
    /**
     * Sets expiry only when the key has no expiry. Equivalent to <code>NX</code> in the Redis API.
     */
    HAS_NO_EXPIRY("NX"),
    /**
     * Sets expiry only when the key has an existing expiry. Equivalent to <code>XX</code> in the
     * Redis API.
     */
    HAS_EXISTING_EXPIRY("XX"),
    /**
     * Sets expiry only when the new expiry is greater than current one. Equivalent to <code>GT</code>
     * in the Redis API.
     */
    NEW_EXPIRY_GREATER_THAN_CURRENT("GT"),
    /**
     * Sets expiry only when the new expiry is less than current one. Equivalent to <code>LT</code> in
     * the Redis API.
     */
    NEW_EXPIRY_LESS_THAN_CURRENT("LT");

    private final String redisApi;

    /**
     * Converts ExpireOptions into a String[].
     *
     * @return String[]
     */
    public String[] toArgs() {
        return new String[] {this.redisApi};
    }

    /**
     * Converts SetOptions into a GlideString[] to add to a {@link Command} arguments.
     *
     * @return GlideString[]
     */
    public GlideString[] toGlideStringArgs() {
        return new GlideString[] {GlideString.gs(redisApi)};
    }
}
