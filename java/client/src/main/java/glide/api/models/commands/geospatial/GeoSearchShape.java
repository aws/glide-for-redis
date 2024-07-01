/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.geospatial;

import glide.api.commands.GeospatialIndicesBaseCommands;
import lombok.Getter;

/**
 * The query's shape for {@link GeospatialIndicesBaseCommands} command.
 *
 * @see <a href="https://redis.io/commands/geosearch/">valkey.io</a>
 */
@Getter
public final class GeoSearchShape {
    /** Valkey API keyword used to perform geosearch by radius. */
    public static final String BYRADIUS_VALKEY_API = "BYRADIUS";

    /** Valkey API keyword used to perform geosearch by box. */
    public static final String BYBOX_VALKEY_API = "BYBOX";

    /**
     * The geosearch query's shape:
     *
     * <ul>
     *   <li>BYRADIUS - Circular shaped query.
     *   <li>BYBOX - Box shaped query.
     * </ul>
     */
    public enum SearchShape {
        BYRADIUS,
        BYBOX
    }

    /** The geosearch query's shape. */
    private final SearchShape shape;

    /** The circular geosearch query's radius. */
    private final double radius;

    /** The box geosearch query's width. */
    private final double width;

    /** The box geosearch query's height. */
    private final double height;

    /** The geosearch query's metric unit. */
    private final GeoUnit unit;

    /**
     * BYRADIUS constructor for GeoSearchShape
     *
     * @param radius The radius to search by.
     * @param unit The unit of the radius.
     */
    public GeoSearchShape(double radius, GeoUnit unit) {
        this.shape = SearchShape.BYRADIUS;
        this.radius = radius;
        this.unit = unit;

        // unused variables
        this.width = -1;
        this.height = -1;
    }

    /**
     * BYBOX constructor for GeoSearchShape
     *
     * @param width The width to search by.
     * @param height The height to search by.
     * @param unit The unit of the radius.
     */
    public GeoSearchShape(double width, double height, GeoUnit unit) {
        this.shape = SearchShape.BYBOX;
        this.width = width;
        this.height = height;
        this.unit = unit;

        // unused variable
        this.radius = -1;
    }

    /**
     * Converts GeoSearchShape into a String[].
     *
     * @return String[] An array containing arguments corresponding to the shape to search by.
     */
    public String[] toArgs() {
        switch (shape) {
            case BYRADIUS:
                return new String[] {shape.toString(), Double.toString(radius), unit.getValkeyAPI()};
            case BYBOX:
                return new String[] {
                    shape.toString(), Double.toString(width), Double.toString(height), unit.getValkeyAPI()
                };
            default:
                return new String[] {};
        }
    }
}
