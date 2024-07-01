/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.geospatial;

import glide.api.commands.GeospatialIndicesBaseCommands;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;

/**
 * Optional arguments for {@link GeospatialIndicesBaseCommands#geosearch(String,
 * GeoSearchOrigin.SearchOrigin, GeoSearchShape, GeoSearchOptions)} command, options include:
 *
 * <ul>
 *   <li>WITHDIST: Also return the distance of the returned items from the specified center point.
 *       The distance is returned in the same unit as specified for the <code>searchBy
 *           </code> argument.
 *   <li>WITHCOORD: Also return the coordinate of the returned items.
 *   <li>WITHHASH: Also return the geohash of the returned items. for the general user.
 * </ul>
 *
 * @see <a href="https://valkey.io/commands/geosearch/">valkey.io</a>
 */
@Builder
public final class GeoSearchOptions {
    /** Valkey API keyword used to perform geosearch with coordinates. */
    public static final String WITHCOORD_VALKEY_API = "WITHCOORD";

    /** Valkey API keyword used to perform geosearch with distance. */
    public static final String WITHDIST_VALKEY_API = "WITHDIST";

    /** Valkey API keyword used to perform geosearch with hash value. */
    public static final String WITHHASH_VALKEY_API = "WITHHASH";

    /**
     * Indicates if the 'WITHCOORD' keyword should be included. Can be included in builder
     * construction by using {@link GeoSearchOptionsBuilder#withcoord()}.
     */
    @Builder.Default private boolean withCoord = false;

    /**
     * Indicates if the 'WITHDIST' keyword should be included. Can be included in builder construction
     * by using {@link GeoSearchOptionsBuilder#withdist()}.
     */
    @Builder.Default private boolean withDist = false;

    /**
     * Indicates if the 'WITHHASH' keyword should be included. Can be included in builder construction
     * by using {@link GeoSearchOptionsBuilder#withhash()}.
     */
    @Builder.Default private boolean withHash = false;

    /**
     * Converts GeoSearchOptions into a String[].
     *
     * @return String[]
     */
    public String[] toArgs() {
        List<String> arguments = new ArrayList<>();

        if (withDist) {
            arguments.add(WITHDIST_VALKEY_API);
        }

        if (withCoord) {
            arguments.add(WITHCOORD_VALKEY_API);
        }

        if (withHash) {
            arguments.add(WITHHASH_VALKEY_API);
        }

        return arguments.toArray(new String[0]);
    }

    public static class GeoSearchOptionsBuilder {
        public GeoSearchOptionsBuilder() {}

        public GeoSearchOptionsBuilder withdist() {
            return withDist(true);
        }

        public GeoSearchOptionsBuilder withcoord() {
            return withCoord(true);
        }

        public GeoSearchOptionsBuilder withhash() {
            return withHash(true);
        }
    }
}
