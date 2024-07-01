/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static glide.api.models.GlideString.gs;

import glide.api.commands.GenericBaseCommands;
import glide.api.models.GlideString;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

/**
 * Optional arguments to {@link GenericBaseCommands#restore(GlideString, long, byte[],
 * RestoreOptions)}
 *
 * @see <a href="https://valkey.io/commands/restore/">valkey.io</a>
 */
@Getter
@Builder
public final class RestoreOptions {
    /** <code>REPLACE</code> subcommand string to replace existing key */
    public static final String REPLACE_REDIS_API = "REPLACE";

    /**
     * <code>ABSTTL</code> subcommand string to represent absolute timestamp (in milliseconds) for TTL
     */
    public static final String ABSTTL_REDIS_API = "ABSTTL";

    /** <code>IDELTIME</code> subcommand string to set Object Idletime */
    public static final String IDLETIME_REDIS_API = "IDLETIME";

    /** <code>FREQ</code> subcommand string to set Object Frequency */
    public static final String FREQ_REDIS_API = "FREQ";

    /** When `true`, it represents <code>REPLACE</code> keyword has been used */
    @Builder.Default private boolean hasReplace = false;

    /** When `true`, it represents <code>ABSTTL</code> keyword has been used */
    @Builder.Default private boolean hasAbsttl = false;

    /** It represents the idletime of object */
    @Builder.Default private Long idletime = null;

    /** It represents the frequency of object */
    @Builder.Default private Long frequency = null;

    /**
     * Creates the argument to be used in {@link GenericBaseCommands#restore(GlideString, long,
     * byte[], RestoreOptions)}
     *
     * @return a <code>GlideString</code> array that holds the subcommands and their arguments.
     */
    public GlideString[] toArgs(GlideString key, long ttl, byte[] value) {
        List<GlideString> resultList = new ArrayList<>();

        resultList.add(key);
        resultList.add(gs(Long.toString(ttl)));
        resultList.add(gs(value));

        if (hasReplace) {
            resultList.add(gs(REPLACE_REDIS_API));
        }

        if (hasAbsttl) {
            resultList.add(gs(ABSTTL_REDIS_API));
        }

        if (idletime != null) {
            resultList.add(gs(IDLETIME_REDIS_API));
            resultList.add(gs(Long.toString(idletime)));
        }

        if (frequency != null) {
            resultList.add(gs(FREQ_REDIS_API));
            resultList.add(gs(Long.toString(frequency)));
        }

        return resultList.toArray(new GlideString[0]);
    }

    /** Custom setter methods for replace and absttl */
    public static class RestoreOptionsBuilder {
        public RestoreOptionsBuilder replace() {
            return hasReplace(true);
        }

        public RestoreOptionsBuilder absttl() {
            return hasAbsttl(true);
        }
    }
}
