# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from enum import Enum
from typing import List, Optional, Tuple, Union

from glide.async_commands.command_args import Limit, OrderBy


class InfBound(Enum):
    """
    Enumeration representing numeric and lexicographic positive and negative infinity bounds for sorted set.
    """

    POS_INF = {"score_arg": "+inf", "lex_arg": "+"}
    """
    Positive infinity bound for sorted set.
        score_arg: represents numeric positive infinity (+inf).
        lex_arg: represents lexicographic positive infinity (+).
    """
    NEG_INF = {"score_arg": "-inf", "lex_arg": "-"}
    """
    Negative infinity bound for sorted set.
        score_arg: represents numeric negative infinity (-inf).
        lex_arg: represents lexicographic negative infinity (-).
    """


class AggregationType(Enum):
    """
    Enumeration representing aggregation types for `ZINTERSTORE` and `ZUNIONSTORE` sorted set commands.
    """

    SUM = "SUM"
    """
    Represents aggregation by summing the scores of elements across inputs where they exist.
    """
    MIN = "MIN"
    """
    Represents aggregation by selecting the minimum score of an element across inputs where it exists.
    """
    MAX = "MAX"
    """
    Represents aggregation by selecting the maximum score of an element across inputs where it exists.
    """


class ScoreFilter(Enum):
    """
    Defines which elements to pop from a sorted set.

    ScoreFilter is a mandatory option for ZMPOP (https://valkey.io/commands/zmpop)
    and BZMPOP (https://valkey.io/commands/bzmpop).
    """

    MIN = "MIN"
    """
    Pop elements with the lowest scores.
    """
    MAX = "MAX"
    """
    Pop elements with the highest scores.
    """


class ScoreBoundary:
    """
    Represents a specific numeric score boundary in a sorted set.

    Args:
        value (float): The score value.
        is_inclusive (bool): Whether the score value is inclusive. Defaults to True.
    """

    def __init__(self, value: float, is_inclusive: bool = True):
        # Convert the score boundary to the Redis protocol format
        self.value = str(value) if is_inclusive else f"({value}"


class LexBoundary:
    """
    Represents a specific lexicographic boundary in a sorted set.

    Args:
        value (str): The lex value.
        is_inclusive (bool): Whether the score value is inclusive. Defaults to True.
    """

    def __init__(self, value: str, is_inclusive: bool = True):
        # Convert the lexicographic boundary to the Redis protocol format
        self.value = f"[{value}" if is_inclusive else f"({value}"


class RangeByIndex:
    """
    Represents a range by index (rank) in a sorted set.

    The `start` and `stop` arguments represent zero-based indexes.

    Args:
        start (int): The start index of the range.
        stop (int): The stop index of the range.
    """

    def __init__(self, start: int, stop: int):
        self.start = start
        self.stop = stop


class RangeByScore:
    """
    Represents a range by score in a sorted set.

    The `start` and `stop` arguments represent score boundaries.

    Args:
        start (Union[InfBound, ScoreBoundary]): The start score boundary.
        stop (Union[InfBound, ScoreBoundary]): The stop score boundary.
        limit (Optional[Limit]): The limit argument for a range query. Defaults to None. See `Limit` class for more information.
    """

    def __init__(
        self,
        start: Union[InfBound, ScoreBoundary],
        stop: Union[InfBound, ScoreBoundary],
        limit: Optional[Limit] = None,
    ):
        self.start = (
            start.value["score_arg"] if type(start) == InfBound else start.value
        )
        self.stop = stop.value["score_arg"] if type(stop) == InfBound else stop.value
        self.limit = limit


class RangeByLex:
    """
    Represents a range by lexicographical order in a sorted set.

    The `start` and `stop` arguments represent lexicographical boundaries.

    Args:
        start (Union[InfBound, LexBoundary]): The start lexicographic boundary.
        stop (Union[InfBound, LexBoundary]): The stop lexicographic boundary.
        limit (Optional[Limit]): The limit argument for a range query. Defaults to None. See `Limit` class for more information.
    """

    def __init__(
        self,
        start: Union[InfBound, LexBoundary],
        stop: Union[InfBound, LexBoundary],
        limit: Optional[Limit] = None,
    ):
        self.start = start.value["lex_arg"] if type(start) == InfBound else start.value
        self.stop = stop.value["lex_arg"] if type(stop) == InfBound else stop.value
        self.limit = limit


class GeospatialData:
    def __init__(self, longitude: float, latitude: float):
        """
        Represents a geographic position defined by longitude and latitude.

        The exact limits, as specified by EPSG:900913 / EPSG:3785 / OSGEO:41001 are the following:
            - Valid longitudes are from -180 to 180 degrees.
            - Valid latitudes are from -85.05112878 to 85.05112878 degrees.

        Args:
            longitude (float): The longitude coordinate.
            latitude (float): The latitude coordinate.
        """
        self.longitude = longitude
        self.latitude = latitude


class GeoUnit(Enum):
    """
    Enumeration representing distance units options for the `GEODIST` command.
    """

    METERS = "m"
    """
    Represents distance in meters.
    """
    KILOMETERS = "km"
    """
    Represents distance in kilometers.
    """
    MILES = "mi"
    """
    Represents distance in miles.
    """
    FEET = "ft"
    """
    Represents distance in feet.
    """


class GeoSearchByRadius:
    """
    Represents search criteria of searching within a certain radius from a specified point.

    Args:
        radius (float): Radius of the search area.
        unit (GeoUnit): Unit of the radius. See `GeoUnit`.
    """

    def __init__(self, radius: float, unit: GeoUnit):
        """
        Initialize the search criteria.
        """
        self.radius = radius
        self.unit = unit

    def to_args(self) -> List[str]:
        """
        Convert the search criteria to the corresponding part of the Redis command.

        Returns:
            List[str]: List representation of the search criteria.
        """
        return ["BYRADIUS", str(self.radius), self.unit.value]


class GeoSearchByBox:
    """
    Represents search criteria of searching within a specified rectangular area.

    Args:
        width (float): Width of the bounding box.
        height (float): Height of the bounding box
        unit (GeoUnit): Unit of the radius. See `GeoUnit`.
    """

    def __init__(self, width: float, height: float, unit: GeoUnit):
        """
        Initialize the search criteria.
        """
        self.width = width
        self.height = height
        self.unit = unit

    def to_args(self) -> List[str]:
        """
        Convert the search criteria to the corresponding part of the Redis command.

        Returns:
            List[str]: List representation of the search criteria.
        """
        return ["BYBOX", str(self.width), str(self.height), self.unit.value]


class GeoSearchCount:
    """
    Represents the count option for limiting the number of results in a GeoSearch.

    Args:
        count (int): The maximum number of results to return.
        any_option (bool): Whether to allow returning as enough matches are found.
        This means that the results returned may not be the ones closest to the specified point. Default to False.
    """

    def __init__(self, count: int, any_option: bool = False):
        """
        Initialize the count option.
        """
        self.count = count
        self.any_option = any_option

    def to_args(self) -> List[str]:
        """
        Convert the count option to the corresponding part of the Redis command.

        Returns:
            List[str]: List representation of the count option.
        """
        if self.any_option:
            return ["COUNT", str(self.count), "ANY"]
        return ["COUNT", str(self.count)]


def _create_zrange_args(
    key: str,
    range_query: Union[RangeByLex, RangeByScore, RangeByIndex],
    reverse: bool,
    with_scores: bool,
    destination: Optional[str] = None,
) -> List[str]:
    args = [destination] if destination else []
    args += [key, str(range_query.start), str(range_query.stop)]

    if isinstance(range_query, RangeByScore):
        args.append("BYSCORE")
    elif isinstance(range_query, RangeByLex):
        args.append("BYLEX")
    if reverse:
        args.append("REV")
    if hasattr(range_query, "limit") and range_query.limit is not None:
        args.extend(
            [
                "LIMIT",
                str(range_query.limit.offset),
                str(range_query.limit.count),
            ]
        )
    if with_scores:
        args.append("WITHSCORES")

    return args


def separate_keys(
    keys: Union[List[str], List[Tuple[str, float]]]
) -> Tuple[List[str], List[str]]:
    """
    Returns seperate lists of keys and weights in case of weighted keys.
    """
    if not keys:
        return [], []

    key_list: List[str] = []
    weight_list: List[str] = []

    if isinstance(keys[0], tuple):
        key_list = [item[0] for item in keys]
        weight_list = [str(item[1]) for item in keys]
    else:
        key_list = keys  # type: ignore

    return key_list, weight_list


def _create_zinter_zunion_cmd_args(
    keys: Union[List[str], List[Tuple[str, float]]],
    aggregation_type: Optional[AggregationType] = None,
    destination: Optional[str] = None,
) -> List[str]:
    args = []

    if destination:
        args.append(destination)

    args.append(str(len(keys)))

    only_keys, weights = separate_keys(keys)

    args += only_keys

    if weights:
        args.append("WEIGHTS")
        args += weights

    if aggregation_type:
        args.append("AGGREGATE")
        args.append(aggregation_type.value)

    return args


def _create_geosearch_args(
    keys: List[str],
    search_from: Union[str, GeospatialData],
    seach_by: Union[GeoSearchByRadius, GeoSearchByBox],
    order_by: Optional[OrderBy] = None,
    count: Optional[GeoSearchCount] = None,
    with_coord: bool = False,
    with_dist: bool = False,
    with_hash: bool = False,
    store_dist: bool = False,
) -> List[str]:
    args = keys
    if isinstance(search_from, str):
        args += ["FROMMEMBER", search_from]
    else:
        args += [
            "FROMLONLAT",
            str(search_from.longitude),
            str(search_from.latitude),
        ]

    args += seach_by.to_args()

    if order_by:
        args.append(order_by.value)
    if count:
        args.extend(count.to_args())

    if with_coord:
        args.append("WITHCOORD")
    if with_dist:
        args.append("WITHDIST")
    if with_hash:
        args.append("WITHHASH")

    if store_dist:
        args.append("STOREDIST")

    return args
