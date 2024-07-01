/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.geospatial;

import glide.api.commands.GeospatialIndicesBaseCommands;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enumeration representing distance units options for the {@link
 * GeospatialIndicesBaseCommands#geodist(String, String, String, GeoUnit)} command.
 */
@RequiredArgsConstructor
@Getter
public enum GeoUnit {
    /** Represents distance in meters. */
    METERS("m"),
    /** Represents distance in kilometers. */
    KILOMETERS("km"),
    /** Represents distance in miles. */
    MILES("mi"),
    /** Represents distance in feet. */
    FEET("ft");

    private final String valkeyAPI;
}
