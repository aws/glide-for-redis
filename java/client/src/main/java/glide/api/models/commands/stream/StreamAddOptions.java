/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.stream;

import glide.api.commands.StreamBaseCommands;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Builder;

/**
 * Optional arguments to {@link StreamBaseCommands#xadd(String, Map, StreamAddOptions)}
 *
 * @see <a href="https://valkey.io/commands/xadd/">valkey.io</a>
 */
@Builder
public final class StreamAddOptions {
    public static final String NO_MAKE_STREAM_REDIS_API = "NOMKSTREAM";
    public static final String ID_WILDCARD_REDIS_API = "*";

    /** If set, the new entry will be added with this <code>id</code>. */
    private final String id;

    /**
     * If set to <code>false</code>, a new stream won't be created if no stream matches the given key.
     * <br>
     * Equivalent to <code>NOMKSTREAM</code> in the Redis API.
     */
    private final Boolean makeStream;

    /** If set, the add operation will also trim the older entries in the stream. */
    private final StreamTrimOptions trim;

    /**
     * Converts options for Xadd into a String[].
     *
     * @return String[]
     */
    public String[] toArgs() {
        List<String> optionArgs = new ArrayList<>();

        if (makeStream != null && !makeStream) {
            optionArgs.add(NO_MAKE_STREAM_REDIS_API);
        }

        if (trim != null) {
            optionArgs.addAll(trim.getRedisApi());
        }

        if (id != null) {
            optionArgs.add(id);
        } else {
            optionArgs.add(ID_WILDCARD_REDIS_API);
        }

        return optionArgs.toArray(new String[0]);
    }
}
