/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.geospatial;

import glide.api.commands.GeospatialIndicesBaseCommands;
import glide.api.models.commands.ConditionalChange;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/**
 * Optional arguments for {@link GeospatialIndicesBaseCommands#geoadd(String, Map, GeoAddOptions)}
 * command.
 *
 * @see <a href="https://redis.io/commands/geoadd/">redis.io</a>
 */
@Getter
public final class GeoAddOptions {
    public static final String CHANGED_REDIS_API = "CH";

    /** Options for handling existing members. See {@link ConditionalChange}. */
    private final ConditionalChange updateMode;

    /** If <code>true</code>, returns the count of changed elements instead of new elements added. */
    private final boolean changed;

    /**
     * Optional arguments for {@link GeospatialIndicesBaseCommands#geoadd(String, Map, GeoAddOptions)}
     * command.
     *
     * @param updateMode Options for handling existing members. See {@link ConditionalChange}
     * @param changed If <code>true</code>, returns the count of changed elements instead of new
     *     elements added.
     */
    public GeoAddOptions(ConditionalChange updateMode, boolean changed) {
        this.updateMode = updateMode;
        this.changed = changed;
    }

    /**
     * Optional arguments for {@link GeospatialIndicesBaseCommands#geoadd(String, Map, GeoAddOptions)}
     * command.
     *
     * @param updateMode Options for handling existing members. See {@link ConditionalChange}
     */
    public GeoAddOptions(ConditionalChange updateMode) {
        this.updateMode = updateMode;
        this.changed = false;
    }

    /**
     * Optional arguments for {@link GeospatialIndicesBaseCommands#geoadd(String, Map, GeoAddOptions)}
     * command.
     *
     * @param changed If <code>true</code>, returns the count of changed elements instead of new
     *     elements added.
     */
    public GeoAddOptions(boolean changed) {
        this.updateMode = null;
        this.changed = changed;
    }

    /**
     * Converts GeoAddOptions into a String[].
     *
     * @return String[]
     */
    public String[] toArgs() {
        List<String> arguments = new ArrayList<>();

        if (updateMode != null) {
            arguments.add(updateMode.getRedisApi());
        }

        if (changed) {
            arguments.add(CHANGED_REDIS_API);
        }

        return arguments.toArray(new String[0]);
    }
}
