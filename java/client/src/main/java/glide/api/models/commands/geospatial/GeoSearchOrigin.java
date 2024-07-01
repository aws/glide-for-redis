/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.geospatial;

import glide.api.commands.GeospatialIndicesBaseCommands;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;

/**
 * The query's starting point for {@link GeospatialIndicesBaseCommands} command.
 *
 * @see <a href="https://valkey.io/commands/geosearch/">valkey.io</a>
 */
public final class GeoSearchOrigin {
    /** Valkey API keyword used to perform search from the position of a given member. */
    public static final String FROMMEMBER_VALKEY_API = "FROMMEMBER";

    /** Valkey API keyword used to perform search from the given longtitude & latitue position. */
    public static final String FROMLONLAT_VALKEY_API = "FROMLONLAT";

    /**
     * Basic interface. Please use one of the following implementations:
     *
     * <ul>
     *   <li>{@link CoordOrigin}
     *   <li>{@link MemberOrigin}
     * </ul>
     */
    public interface SearchOrigin {
        /** Convert to command arguments according to the Valkey API. */
        String[] toArgs();
    }

    /** The search origin represented by a {@link GeospatialData} position. */
    @RequiredArgsConstructor
    public static class CoordOrigin implements SearchOrigin {
        private final GeospatialData position;

        /**
         * Converts GeoSearchOrigin into a String[].
         *
         * @return String[] An array containing arguments corresponding to the starting point of the
         *     query.
         */
        public String[] toArgs() {
            return ArrayUtils.addAll(new String[] {FROMLONLAT_VALKEY_API}, position.toArgs());
        }
    }

    /** The search origin represented by an existing member. */
    @RequiredArgsConstructor
    public static class MemberOrigin implements SearchOrigin {
        private final String member;

        /**
         * Converts GeoSearchOrigin into a String[].
         *
         * @return String[] An array containing arguments corresponding to the starting point of the
         *     query.
         */
        public String[] toArgs() {
            return new String[] {FROMMEMBER_VALKEY_API, member};
        }
    }
}
