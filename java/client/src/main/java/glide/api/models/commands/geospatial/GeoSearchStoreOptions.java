/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.geospatial;

import glide.api.commands.GeospatialIndicesBaseCommands;
import lombok.Builder;

/**
 * Optional arguments for {@link GeospatialIndicesBaseCommands#geosearchstore(String, String,
 * GeoSearchOrigin.SearchOrigin, GeoSearchShape, GeoSearchStoreOptions)} command.
 *
 * @see <a href="https://valkey.io/commands/geosearch/">valkey.io</a>
 */
@Builder
public final class GeoSearchStoreOptions {
    /** Valkey API keyword for {@link #storeDist} parameter. */
    public static final String GEOSEARCHSTORE_VALKEY_API = "STOREDIST";

    /**
     * Determines what is stored as the sorted set score. Defaults to <code>false</code>.<br>
     * If set to <code>false</code>, the geohash of the location will be stored as the sorted set
     * score.<br>
     * If set to <code>true</code>, the distance from the center of the shape (circle or box) will be
     * stored as the sorted set score. The distance is represented as a floating-point number in the
     * same unit specified for that shape.
     */
    private final boolean storeDist;

    /**
     * Converts GeoSearchStoreOptions into a String[].
     *
     * @return String[]
     */
    public String[] toArgs() {
        if (storeDist) {
            return new String[] {GEOSEARCHSTORE_VALKEY_API};
        }

        return new String[] {};
    }

    public static class GeoSearchStoreOptionsBuilder {
        public GeoSearchStoreOptionsBuilder() {}

        /** Enable sorting the results with their distance from the center. */
        public GeoSearchStoreOptionsBuilder storedist() {
            return storeDist(true);
        }
    }
}
