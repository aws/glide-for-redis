/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.GeospatialIndicesBaseCommands;
import glide.api.models.commands.geospatial.GeoAddOptions;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * An optional condition to the {@link GeospatialIndicesBaseCommands#geoadd(String, Map,
 * GeoAddOptions)} command.
 */
@RequiredArgsConstructor
@Getter
public enum ConditionalChange {
    /**
     * Only update elements that already exist. Don't add new elements. Equivalent to <code>XX
     * </code> in the Redis API.
     */
    ONLY_IF_EXISTS("XX"),
    /**
     * Only add new elements. Don't update already existing elements. Equivalent to <code>NX</code> in
     * the Redis API.
     */
    ONLY_IF_DOES_NOT_EXIST("NX");

    private final String redisApi;
}
