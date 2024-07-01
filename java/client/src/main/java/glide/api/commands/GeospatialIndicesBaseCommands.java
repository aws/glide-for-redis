/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.GlideString;
import glide.api.models.commands.geospatial.GeoAddOptions;
import glide.api.models.commands.geospatial.GeoSearchOptions;
import glide.api.models.commands.geospatial.GeoSearchOrigin.CoordOrigin;
import glide.api.models.commands.geospatial.GeoSearchOrigin.MemberOrigin;
import glide.api.models.commands.geospatial.GeoSearchOrigin.SearchOrigin;
import glide.api.models.commands.geospatial.GeoSearchResultOptions;
import glide.api.models.commands.geospatial.GeoSearchShape;
import glide.api.models.commands.geospatial.GeoSearchStoreOptions;
import glide.api.models.commands.geospatial.GeoUnit;
import glide.api.models.commands.geospatial.GeospatialData;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Geospatial Indices Commands" group for standalone and
 * cluster clients.
 *
 * @see <a href="https://redis.io/commands/?group=geo">Geospatial Indices Commands</a>
 */
public interface GeospatialIndicesBaseCommands {

    /**
     * Adds geospatial members with their positions to the specified sorted set stored at <code>key
     * </code>.<br>
     * If a member is already a part of the sorted set, its position is updated.
     *
     * @see <a href="https://redis.io/commands/geoadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersToGeospatialData A mapping of member names to their corresponding positions - see
     *     {@link GeospatialData}. The command will report an error when the user attempts to index
     *     coordinates outside the specified ranges.
     * @param options The GeoAdd options - see {@link GeoAddOptions}
     * @return The number of elements added to the sorted set. If <code>changed</code> is set to
     *     <code>true</code> in the options, returns the number of elements updated in the sorted set.
     * @example
     *     <pre>{@code
     * GeoAddOptions options = new GeoAddOptions(ConditionalChange.ONLY_IF_EXISTS, true);
     * Long num = client.geoadd("mySortedSet", Map.of("Palermo", new GeospatialData(13.361389, 38.115556)), options).get();
     * assert num == 1L; // Indicates that the position of an existing member in the sorted set "mySortedSet" has been updated.
     * }</pre>
     */
    CompletableFuture<Long> geoadd(
            String key, Map<String, GeospatialData> membersToGeospatialData, GeoAddOptions options);

    /**
     * Adds geospatial members with their positions to the specified sorted set stored at <code>key
     * </code>.<br>
     * If a member is already a part of the sorted set, its position is updated.<br>
     * To perform a <code>geoadd</code> operation while specifying optional parameters, use {@link
     * #geoadd(String, Map, GeoAddOptions)}.
     *
     * @see <a href="https://redis.io/commands/geoadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersToGeospatialData A mapping of member names to their corresponding positions - see
     *     {@link GeospatialData}. The command will report an error when the user attempts to index
     *     coordinates outside the specified ranges.
     * @return The number of elements added to the sorted set.
     * @example
     *     <pre>{@code
     * Long num = client.geoadd("mySortedSet", Map.of("Palermo", new GeospatialData(13.361389, 38.115556), "Catania", new GeospatialData(15.087269, 37.502669)).get();
     * assert num == 2L; // Indicates that two elements have been added to the sorted set "mySortedSet".
     * }</pre>
     */
    CompletableFuture<Long> geoadd(String key, Map<String, GeospatialData> membersToGeospatialData);

    /**
     * Returns the positions (longitude,latitude) of all the specified <code>members</code> of the
     * geospatial index represented by the sorted set at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/geopos">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param members The members for which to get the positions.
     * @return A 2D <code>array</code> which represent positions (longitude and latitude)
     *     corresponding to the given members. If a member does not exist, its position will be
     *     </code>null</code>.
     * @example
     *     <pre>{@code
     * // When added via GEOADD, the geospatial coordinates are converted into a 52 bit geohash, so the coordinates
     * // returned might not be exactly the same as the input values
     * client.geoadd("mySortedSet", Map.of("Palermo", new GeospatialData(13.361389, 38.115556), "Catania", new GeospatialData(15.087269, 37.502669))).get();
     * Double[][] result = client.geopos("mySortedSet", new String[]{"Palermo", "Catania", "NonExisting"}).get();
     * System.out.println(Arrays.deepToString(result));
     * }</pre>
     */
    CompletableFuture<Double[][]> geopos(String key, String[] members);

    /**
     * Returns the positions (longitude,latitude) of all the specified <code>members</code> of the
     * geospatial index represented by the sorted set at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/geopos">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param members The members for which to get the positions.
     * @return A 2D <code>array</code> which represent positions (longitude and latitude)
     *     corresponding to the given members. If a member does not exist, its position will be
     *     </code>null</code>.
     * @example
     *     <pre>{@code
     * // When added via GEOADD, the geospatial coordinates are converted into a 52 bit geohash, so the coordinates
     * // returned might not be exactly the same as the input values
     * client.geoadd(gs("mySortedSet"), Map.of(gs("Palermo"), new GeospatialData(13.361389, 38.115556), gs("Catania"), new GeospatialData(15.087269, 37.502669))).get();
     * Double[][] result = client.geopos(gs("mySortedSet", new GlideString[]{gs("Palermo"), gs("Catania"), gs("NonExisting")}).get();
     * System.out.println(Arrays.deepToString(result));
     * }</pre>
     */
    CompletableFuture<Double[][]> geopos(GlideString key, GlideString[] members);

    /**
     * Returns the distance between <code>member1</code> and <code>member2</code> saved in the
     * geospatial index stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/geodist">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member1 The name of the first member.
     * @param member2 The name of the second member.
     * @param geoUnit The unit of distance measurement - see {@link GeoUnit}.
     * @return The distance between <code>member1</code> and <code>member2</code>. If one or both
     *     members do not exist, or if the key does not exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Double result = client.geodist("mySortedSet", "Palermo", "Catania", GeoUnit.KILOMETERS).get();
     * System.out.println(result);
     * }</pre>
     */
    CompletableFuture<Double> geodist(String key, String member1, String member2, GeoUnit geoUnit);

    /**
     * Returns the distance between <code>member1</code> and <code>member2</code> saved in the
     * geospatial index stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/geodist">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member1 The name of the first member.
     * @param member2 The name of the second member.
     * @param geoUnit The unit of distance measurement - see {@link GeoUnit}.
     * @return The distance between <code>member1</code> and <code>member2</code>. If one or both
     *     members do not exist, or if the key does not exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Double result = client.geodist(gs("mySortedSet"), gs("Palermo"), gs("Catania"), GeoUnit.KILOMETERS).get();
     * System.out.println(result);
     * }</pre>
     */
    CompletableFuture<Double> geodist(
            GlideString key, GlideString member1, GlideString member2, GeoUnit geoUnit);

    /**
     * Returns the distance between <code>member1</code> and <code>member2</code> saved in the
     * geospatial index stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/geodist">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member1 The name of the first member.
     * @param member2 The name of the second member.
     * @return The distance between <code>member1</code> and <code>member2</code>. If one or both
     *     members do not exist, or if the key does not exist, returns <code>null</code>. The default
     *     unit is {@see GeoUnit#METERS}.
     * @example
     *     <pre>{@code
     * Double result = client.geodist("mySortedSet", "Palermo", "Catania").get();
     * System.out.println(result);
     * }</pre>
     */
    CompletableFuture<Double> geodist(String key, String member1, String member2);

    /**
     * Returns the distance between <code>member1</code> and <code>member2</code> saved in the
     * geospatial index stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/geodist">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member1 The name of the first member.
     * @param member2 The name of the second member.
     * @return The distance between <code>member1</code> and <code>member2</code>. If one or both
     *     members do not exist, or if the key does not exist, returns <code>null</code>. The default
     *     unit is {@see GeoUnit#METERS}.
     * @example
     *     <pre>{@code
     * Double result = client.geodist(gs("mySortedSet"), gs("Palermo"), gs("Catania")).get();
     * System.out.println(result);
     * }</pre>
     */
    CompletableFuture<Double> geodist(GlideString key, GlideString member1, GlideString member2);

    /**
     * Returns the <code>GeoHash</code> strings representing the positions of all the specified <code>
     * members</code> in the sorted set stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/geohash">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param members The array of members whose <code>GeoHash</code> strings are to be retrieved.
     * @return An array of <code>GeoHash</code> strings representing the positions of the specified
     *     members stored at <code>key</code>. If a member does not exist in the sorted set, a <code>
     *     null</code> value is returned for that member.
     * @example
     *     <pre>{@code
     * String[] result = client.geohash("mySortedSet", new String[] {"Palermo", "Catania", "NonExisting"}).get();
     * System.out.println(Arrays.toString(result)); // prints a list of corresponding GeoHash String values
     * }</pre>
     */
    CompletableFuture<String[]> geohash(String key, String[] members);

    /**
     * Returns the members of a sorted set populated with geospatial information using {@link
     * #geoadd(String, Map)}, which are within the borders of the area specified by a given shape.
     *
     * @since Valkey 6.2.0 and above.
     * @see <a href="https://valkey.io/commands/geosearch">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param searchFrom The query's center point options, could be one of:
     *     <ul>
     *       <li>{@link MemberOrigin} to use the position of the given existing member in the sorted
     *           set.
     *       <li>{@link CoordOrigin} to use the given longitude and latitude coordinates.
     *     </ul>
     *
     * @param searchBy The query's shape options:
     *     <ul>
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, GeoUnit)} to search inside circular area
     *           according to given radius.
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, double, GeoUnit)} to search inside an
     *           axis-aligned rectangle, determined by height and width.
     *     </ul>
     *
     * @return An <code>array</code> of matched member names.
     * @example
     *     <pre>{@code
     * Object[] expected = new String[] {"Catania", "Palermo"};
     * Object[] result =
     *                client
     *                        .geosearch(
     *                                "a1",
     *                                new GeoSearchOrigin("Palermo"),
     *                                new GeoSearchShape(200, GeoUnit.KILOMETERS))
     *                        .get();
     * assert expected.equals(result);
     * }</pre>
     */
    CompletableFuture<String[]> geosearch(
            String key, SearchOrigin searchFrom, GeoSearchShape searchBy);

    /**
     * Returns the members of a sorted set populated with geospatial information using {@link
     * #geoadd(String, Map)}, which are within the borders of the area specified by a given shape.
     *
     * @since Valkey 6.2.0 and above.
     * @see <a href="https://valkey.io/commands/geosearch">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param searchFrom The query's center point options, could be one of:
     *     <ul>
     *       <li>{@link MemberOrigin} to use the position of the given existing member in the sorted
     *           set.
     *       <li>{@link CoordOrigin} to use the given longitude and latitude coordinates.
     *     </ul>
     *
     * @param searchBy The query's shape options:
     *     <ul>
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, GeoUnit)} to search inside circular area
     *           according to given radius.
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, double, GeoUnit)} to search inside an
     *           axis-aligned rectangle, determined by height and width.
     *     </ul>
     *
     * @param resultOptions Optional inputs for sorting/limiting the results. See - {@link
     *     GeoSearchResultOptions}
     * @return An <code>array</code> of matched member names.
     * @example
     *     <pre>{@code
     * Object[] expected = new String[] {"Catania", "Palermo"};
     * Object[] result =
     *                client
     *                        .geosearch(
     *                                "a1",
     *                                new GeoSearchOrigin("Palermo"),
     *                                new GeoSearchShape(200, GeoUnit.KILOMETERS),
     *                                SortOrder.ASC)
     *                        .get();
     * assert expected.equals(result);
     * }</pre>
     */
    CompletableFuture<String[]> geosearch(
            String key,
            SearchOrigin searchFrom,
            GeoSearchShape searchBy,
            GeoSearchResultOptions resultOptions);

    /**
     * Returns the members of a sorted set populated with geospatial information using {@link
     * #geoadd(String, Map)}, which are within the borders of the area specified by a given shape.
     *
     * @since Valkey 6.2.0 and above.
     * @see <a href="https://valkey.io/commands/geosearch">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param searchFrom The query's center point options, could be one of:
     *     <ul>
     *       <li>{@link MemberOrigin} to use the position of the given existing member in the sorted
     *           set.
     *       <li>{@link CoordOrigin} to use the given longitude and latitude coordinates.
     *     </ul>
     *
     * @param searchBy The query's shape options:
     *     <ul>
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, GeoUnit)} to search inside circular area
     *           according to given radius.
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, double, GeoUnit)} to search inside an
     *           axis-aligned rectangle, determined by height and width.
     *     </ul>
     *
     * @param options The optional inputs to request additional information.
     * @return An array of arrays where each sub-array represents a single item in the following
     *     order:
     *     <ul>
     *       <li>The member (location) name.
     *       <li>The distance from the center as a <code>Double</code>, in the same unit specified for
     *           <code>searchBy</code>.
     *       <li>The geohash of the location as a <code>Long</code>.
     *       <li>The coordinates as a two item <code>array</code> of <code>Double</code>.
     *     </ul>
     *
     * @example
     *     <pre>{@code
     * Object[] expected =
     *            new Object[] {
     *                new Object[] {
     *                     // name
     *                    "Palermo",
     *                    new Object[] {
     *                        // distance, hash and coordinates
     *                        0.0, 3479099956230698L, new Object[] {13.361389338970184, 38.1155563954963}
     *                    }
     *                },
     *                new Object[] {
     *                    "Catania",
     *                    new Object[] {
     *                        166.2742, 3479447370796909L, new Object[] {15.087267458438873, 37.50266842333162}
     *                    }
     *                }
     *            };
     * Object[] result =
     *                client
     *                        .geosearch(
     *                                "a1",
     *                                new GeoSearchOrigin("Palermo"),
     *                                new GeoSearchShape(200, GeoUnit.KILOMETERS),
     *                                new GeoSearchOptions.GeoSearchOptionsBuilder()
     *                                             .withcoord()
     *                                             .withdist()
     *                                             .withhash()
     *                                             .count(3)
     *                                             .build(),
     *                                SortOrder.ASC)
     *                        .get();
     * // The result contains the data in the same format as expected.
     * }</pre>
     */
    CompletableFuture<Object[]> geosearch(
            String key, SearchOrigin searchFrom, GeoSearchShape searchBy, GeoSearchOptions options);

    /**
     * Returns the members of a sorted set populated with geospatial information using {@link
     * #geoadd(String, Map)}, which are within the borders of the area specified by a given shape.
     *
     * @since Valkey 6.2.0 and above.
     * @see <a href="https://valkey.io/commands/geosearch">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param searchFrom The query's center point options, could be one of:
     *     <ul>
     *       <li>{@link MemberOrigin} to use the position of the given existing member in the sorted
     *           set.
     *       <li>{@link CoordOrigin} to use the given longitude and latitude coordinates.
     *     </ul>
     *
     * @param searchBy The query's shape options:
     *     <ul>
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, GeoUnit)} to search inside circular area
     *           according to given radius.
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, double, GeoUnit)} to search inside an
     *           axis-aligned rectangle, determined by height and width.
     *     </ul>
     *
     * @param options The optional inputs to request additional information. See - {@link
     *     GeoSearchOptions}
     * @param resultOptions Optional inputs for sorting/limiting the results. See - {@link
     *     GeoSearchResultOptions}
     * @return An array of arrays where each sub-array represents a single item in the following
     *     order:
     *     <ul>
     *       <li>The member (location) name.
     *       <li>The distance from the center as a <code>Double</code>, in the same unit specified for
     *           <code>searchBy</code>.
     *       <li>The geohash of the location as a <code>Long</code>.
     *       <li>The coordinates as a two item <code>array</code> of <code>Double</code>.
     *     </ul>
     *
     * @example
     *     <pre>{@code
     * Object[] expected =
     *            new Object[] {
     *                new Object[] {
     *                     // name
     *                    "Palermo",
     *                    new Object[] {
     *                        // distance, hash and coordinates
     *                        0.0, 3479099956230698L, new Object[] {13.361389338970184, 38.1155563954963}
     *                    }
     *                },
     *                new Object[] {
     *                    "Catania",
     *                    new Object[] {
     *                        166.2742, 3479447370796909L, new Object[] {15.087267458438873, 37.50266842333162}
     *                    }
     *                }
     *            };
     * Object[] result =
     *                client
     *                        .geosearch(
     *                                "a1",
     *                                new GeoSearchOrigin("Palermo"),
     *                                new GeoSearchShape(200, GeoUnit.KILOMETERS),
     *                                new GeoSearchOptions.GeoSearchOptionsBuilder()
     *                                             .withcoord()
     *                                             .withdist()
     *                                             .withhash()
     *                                             .count(3)
     *                                             .build(),
     *                                SortOrder.ASC)
     *                        .get();
     * // The result contains the data in the same format as expected.
     * }</pre>
     */
    CompletableFuture<Object[]> geosearch(
            String key,
            SearchOrigin searchFrom,
            GeoSearchShape searchBy,
            GeoSearchOptions options,
            GeoSearchResultOptions resultOptions);

    /**
     * Searches for members in a sorted set stored at <code>source</code> representing geospatial data
     * within a circular or rectangular area and stores the result in <code>destination</code>. If
     * <code>destination</code> already exists, it is overwritten. Otherwise, a new sorted set will be
     * created. To get the result directly, see `{@link #geosearch(String, SearchOrigin,
     * GeoSearchShape)}.
     *
     * @since Valkey 6.2.0 and above.
     * @apiNote When in cluster mode, <code>source</code> and <code>destination</code> must map to the
     *     same hash slot.
     * @see <a href="https://valkey.io/commands/geosearch">valkey.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param source The key of the source sorted set.
     * @param searchFrom The query's center point options, could be one of:
     *     <ul>
     *       <li>{@link MemberOrigin} to use the position of the given existing member in the sorted
     *           set.
     *       <li>{@link CoordOrigin} to use the given longitude and latitude coordinates.
     *     </ul>
     *
     * @param searchBy The query's shape options:
     *     <ul>
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, GeoUnit)} to search inside circular area
     *           according to given radius.
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, double, GeoUnit)} to search inside an
     *           axis-aligned rectangle, determined by height and width.
     *     </ul>
     *
     * @return The number of elements in the resulting set.
     * @example
     *     <pre>{@code
     * Long result = client
     *                     .geosearchstore(
     *                             destinationKey,
     *                             sourceKey,
     *                             new CoordOrigin(new GeospatialData(15, 37)),
     *                             new GeoSearchShape(400, 400, GeoUnit.KILOMETERS))
     *                     .get();
     * assert result == 4L;
     * }</pre>
     */
    CompletableFuture<Long> geosearchstore(
            String destination, String source, SearchOrigin searchFrom, GeoSearchShape searchBy);

    /**
     * Searches for members in a sorted set stored at <code>source</code> representing geospatial data
     * within a circular or rectangular area and stores the result in <code>destination</code>. If
     * <code>destination</code> already exists, it is overwritten. Otherwise, a new sorted set will be
     * created. To get the result directly, see `{@link #geosearch(String, SearchOrigin,
     * GeoSearchShape, GeoSearchResultOptions)}.
     *
     * @since Valkey 6.2.0 and above.
     * @apiNote When in cluster mode, <code>source</code> and <code>destination</code> must map to the
     *     same hash slot.
     * @see <a href="https://valkey.io/commands/geosearch">valkey.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param source The key of the source sorted set.
     * @param searchFrom The query's center point options, could be one of:
     *     <ul>
     *       <li>{@link MemberOrigin} to use the position of the given existing member in the sorted
     *           set.
     *       <li>{@link CoordOrigin} to use the given longitude and latitude coordinates.
     *     </ul>
     *
     * @param searchBy The query's shape options:
     *     <ul>
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, GeoUnit)} to search inside circular area
     *           according to given radius.
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, double, GeoUnit)} to search inside an
     *           axis-aligned rectangle, determined by height and width.
     *     </ul>
     *
     * @param resultOptions Optional inputs for sorting/limiting the results. See - {@link
     *     GeoSearchResultOptions}
     * @return The number of elements in the resulting set.
     * @example
     *     <pre>{@code
     * Long result = client
     *                     .geosearchstore(
     *                             destinationKey,
     *                             sourceKey,
     *                             new CoordOrigin(new GeospatialData(15, 37)),
     *                             new GeoSearchShape(400, 400, GeoUnit.KILOMETERS),
     *                             new GeoSearchResultOptions(2, true))
     *                     .get();
     * assert result == 2L;
     * }</pre>
     */
    CompletableFuture<Long> geosearchstore(
            String destination,
            String source,
            SearchOrigin searchFrom,
            GeoSearchShape searchBy,
            GeoSearchResultOptions resultOptions);

    /**
     * Searches for members in a sorted set stored at <code>source</code> representing geospatial data
     * within a circular or rectangular area and stores the result in <code>destination</code>. If
     * <code>destination</code> already exists, it is overwritten. Otherwise, a new sorted set will be
     * created. To get the result directly, see `{@link #geosearch(String, SearchOrigin,
     * GeoSearchShape, GeoSearchOptions)}.
     *
     * @since Valkey 6.2.0 and above.
     * @apiNote When in cluster mode, <code>source</code> and <code>destination</code> must map to the
     *     same hash slot.
     * @see <a href="https://valkey.io/commands/geosearch">valkey.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param source The key of the source sorted set.
     * @param searchFrom The query's center point options, could be one of:
     *     <ul>
     *       <li>{@link MemberOrigin} to use the position of the given existing member in the sorted
     *           set.
     *       <li>{@link CoordOrigin} to use the given longitude and latitude coordinates.
     *     </ul>
     *
     * @param searchBy The query's shape options:
     *     <ul>
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, GeoUnit)} to search inside circular area
     *           according to given radius.
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, double, GeoUnit)} to search inside an
     *           axis-aligned rectangle, determined by height and width.
     *     </ul>
     *
     * @param options The optional inputs to request additional information.
     * @return The number of elements in the resulting set.
     * @example
     *     <pre>{@code
     * Long result = client
     *                     .geosearchstore(
     *                             destinationKey,
     *                             sourceKey,
     *                             new CoordOrigin(new GeospatialData(15, 37)),
     *                             new GeoSearchShape(400, 400, GeoUnit.KILOMETERS),
     *                             GeoSearchStoreOptions.builder().storedist().build())
     *                     .get();
     * assert result == 4L;
     * }</pre>
     */
    CompletableFuture<Long> geosearchstore(
            String destination,
            String source,
            SearchOrigin searchFrom,
            GeoSearchShape searchBy,
            GeoSearchStoreOptions options);

    /**
     * Searches for members in a sorted set stored at <code>source</code> representing geospatial data
     * within a circular or rectangular area and stores the result in <code>destination</code>. If
     * <code>destination</code> already exists, it is overwritten. Otherwise, a new sorted set will be
     * created. To get the result directly, see `{@link #geosearch(String, SearchOrigin,
     * GeoSearchShape, GeoSearchOptions, GeoSearchResultOptions)}.
     *
     * @since Valkey 6.2.0 and above.
     * @apiNote When in cluster mode, <code>source</code> and <code>destination</code> must map to the
     *     same hash slot.
     * @see <a href="https://valkey.io/commands/geosearch">valkey.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param source The key of the source sorted set.
     * @param searchFrom The query's center point options, could be one of:
     *     <ul>
     *       <li>{@link MemberOrigin} to use the position of the given existing member in the sorted
     *           set.
     *       <li>{@link CoordOrigin} to use the given longitude and latitude coordinates.
     *     </ul>
     *
     * @param searchBy The query's shape options:
     *     <ul>
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, GeoUnit)} to search inside circular area
     *           according to given radius.
     *       <li>{@link GeoSearchShape#GeoSearchShape(double, double, GeoUnit)} to search inside an
     *           axis-aligned rectangle, determined by height and width.
     *     </ul>
     *
     * @param options The optional inputs to request additional information.
     * @param resultOptions Optional inputs for sorting/limiting the results. See - {@link
     *     GeoSearchResultOptions}
     * @return The number of elements in the resulting set.
     * @example
     *     <pre>{@code
     * Long result = client
     *                     .geosearchstore(
     *                             destinationKey,
     *                             sourceKey,
     *                             new CoordOrigin(new GeospatialData(15, 37)),
     *                             new GeoSearchShape(400, 400, GeoUnit.KILOMETERS),
     *                             GeoSearchStoreOptions.builder().storedist().build()
     *                             new GeoSearchResultOptions(2, true))
     *                     .get();
     * assert result == 2L;
     * }</pre>
     */
    CompletableFuture<Long> geosearchstore(
            String destination,
            String source,
            SearchOrigin searchFrom,
            GeoSearchShape searchBy,
            GeoSearchStoreOptions options,
            GeoSearchResultOptions resultOptions);
}
