/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.geospatial;

import glide.api.commands.GeospatialIndicesBaseCommands;
import glide.api.models.commands.SortOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional arguments for {@link GeospatialIndicesBaseCommands#geosearch(String,
 * GeoSearchOrigin.SearchOrigin, GeoSearchShape, GeoSearchOptions)} command that contains up to 2
 * optional input, including:
 *
 * <ul>
 *   <li>SortOrder: The query's order to sort the results by:
 *       <ul>
 *         <li>ASC: Sort returned items from the nearest to the farthest, relative to the center
 *             point.
 *         <li>DESC: Sort returned items from the farthest to the nearest, relative to the center
 *             point.
 *       </ul>
 *   <li>COUNT: Limits the results to the first N matching items, when the 'ANY' option is used, the
 *       command returns as soon as enough matches are found. This means that the results might may
 *       not be the ones closest to the origin.
 * </ul>
 *
 * @see <a href="https://valkey.io/commands/geosearch/">valkey.io</a>
 */
public class GeoSearchResultOptions {
    /** Valkey API keyword used to perform geosearch with count. */
    public static final String COUNT_VALKEY_API = "COUNT";

    /**
     * Valkey API keyword used to change search behavior to return as soon as enough matches are
     * found.
     */
    public static final String ANY_VALKEY_API = "ANY";

    /** Indicates the order the result should be sorted in. See {@link SortOrder} for detail. */
    private final SortOrder sortOrder;

    /** Indicates the number of matches the result should be limited to. */
    private final long count;

    /** Indicates if the 'ANY' keyword is used for 'COUNT'. */
    private final boolean isAny;

    /** Constructor with count only. */
    public GeoSearchResultOptions(long count) {
        this.sortOrder = null;
        this.count = count;
        this.isAny = false;
    }

    /** Constructor with count and the 'ANY' keyword. */
    public GeoSearchResultOptions(long count, boolean isAny) {
        this.sortOrder = null;
        this.count = count;
        this.isAny = isAny;
    }

    /** Constructor with sort order only. */
    public GeoSearchResultOptions(SortOrder order) {
        this.sortOrder = order;
        this.count = -1;
        this.isAny = false;
    }

    /** Constructor with sort order and count. */
    public GeoSearchResultOptions(SortOrder order, long count) {
        this.sortOrder = order;
        this.count = count;
        this.isAny = false;
    }

    /** Constructor with sort order, count and 'ANY' keyword. */
    public GeoSearchResultOptions(SortOrder order, long count, boolean isAny) {
        this.sortOrder = order;
        this.count = count;
        this.isAny = isAny;
    }

    /**
     * Converts GeoSearchResultOptions into a String[].
     *
     * @return String[]
     */
    public String[] toArgs() {
        List<String> arguments = new ArrayList<>();

        if (count > 0) {
            arguments.add(COUNT_VALKEY_API);
            arguments.add(Long.toString(count));

            if (isAny) {
                arguments.add(ANY_VALKEY_API);
            }
        }

        if (sortOrder != null) {
            arguments.add(sortOrder.toString());
        }

        return arguments.toArray(new String[0]);
    }
}
