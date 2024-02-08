# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

from abc import ABC, abstractmethod
from enum import Enum
from typing import Dict, List, Optional


class SortableOption(Enum):
    """
    Options for fields with SORTABLE argument.
    """

    SORTABLE = "SORTABLE"
    """
    Sets the field as sortable.
    """
    SORTABLE_UNF = "SORTABLE UNF"
    """
    Sets the field as sortable, disables the normalization and keep the original form of the field value.
    """


class PhoneticType(Enum):
    """
    Activate phonetic matching for text searches, specifies the phonetic algorithm and language used.
    """

    DM_ENGLISH = "dm:en"
    """
    Double metaphone for English.
    """
    DM_FRENCH = "dm:fn"
    """
    Double metaphone for French.
    """
    DM_PORTUGUESE = "dm:pt"
    """
    Double metaphone for Portuguese
    """
    DM_SPANISH = "dm:es"
    """
    Double metaphone for Spanish.
    """


class CoordinateSystem(Enum):
    """
    Specifies the coordinate system used for geo fields.
    """

    SPHERICAL = "SPHERICAL"
    """
    Spherical coordinates system used with latitude/longitude.
    """
    FLAT = "FLAT"
    """
    Flat coordinates system used with XY coordinates.
    """


class VectorAlgorithm(Enum):
    """
    Specifies the algorithm used for vector similarity search.
    """

    FLAT = "FLAT"
    """
    Brute-force index. Used for flat vector indexing.
    """
    HNSW = "HNSW"
    """
    Modified version of nmslib/hnswlib, which is an implementation of Efficient and robust 
    approximate nearest neighbor search using Hierarchical Navigable Small World graphs.
    """


class Field(ABC):
    """
    Abstract base class for defining fields in a schema.

    Args:
        name (str): The name of the field.
        alias (Optional[str]): An alias for the field.
        type (str): The type of the field.
    """

    @abstractmethod
    def __init__(
        self,
        name: str,
        type: str,
        alias: Optional[str] = None,
    ):
        """Initialize a new Field instance."""
        self.name = name
        self.alias = alias
        self._type = type

    @abstractmethod
    def get_field_args(self) -> List[str]:
        """
        Get the arguments representing the field.

        Returns:
            List[str]: A list of field arguments.
        """
        args = [self.name]
        if self.alias:
            args.extend(["AS", self.alias])
        args.append(self._type)
        return args


class SortableIndexableField(Field):
    """
    Abstract base class for defining sortable and indexable fields in a schema.
    Args:
        name (str): The name of the sortable field.
        alias (Optional[str]): An alias for the field.
        sortable (Optional[SortableOption]): If not None, sets the field as sortable with the specified sortable option.
        no_index (bool): Indicate whether the field should be excluded from indexing.
    """

    @abstractmethod
    def __init__(
        self,
        name: str,
        type: str,
        alias: Optional[str] = None,
        sortable: Optional[SortableOption] = None,
        no_index: bool = False,
    ):
        """Initialize a new SortableField instance."""
        super().__init__(name, type, alias)
        self.sortable = sortable
        self.no_index = no_index

    def get_field_args(self) -> List[str]:
        """
        Get the arguments representing the field.

        Returns:
            List[str]: A list of field arguments.
        """
        args = super().get_field_args()
        if self.sortable:
            args.extend(self.sortable.value.split(" "))
        if self.no_index:
            args.append("NOINDEX")
        return args


class TextField(SortableIndexableField):
    """
    Class for defining text fields in a schema.

    Args:
        name (str): The name of the text field.
        alias (Optional[str]): An alias for the field.
        sortable (Optional[SortableOption]): If not None, sets the field as sortable with the specified sortable option.
        no_stem (bool): Indicate whether to disable stemming when indexing field values.
        no_index (bool): Indicate whether the field should be excluded from indexing.
        phonetic (Optional[PhoneticType]): Activate phonetic matching for text searches, specifies the phonetic algorithm and language used (e.g., English: dm:en).
        weight (Optional[float]): Declare the importance of this attribute when calculating result accuracy.
            This is a multiplication factor, and defaults to 1 if not specified.
        with_suffix_trie (bool): Improve query performance by utilizing a suffix trie for contains (foo) and suffix (*foo) queries.
            This feature avoids brute-force searches on the trie.
    """

    def __init__(
        self,
        name: str,
        alias: Optional[str] = None,
        sortable: Optional[SortableOption] = None,
        no_stem: bool = False,
        no_index: bool = False,
        phonetic: Optional[PhoneticType] = None,
        weight: Optional[float] = None,
        with_suffix_trie: bool = False,
    ):
        """Initialize a new TextField instance."""
        super().__init__(name, "TEXT", alias, sortable, no_index)
        self.no_stem = no_stem
        self.phonetic = phonetic
        self.weight = weight
        self.with_suffix_trie = with_suffix_trie

    def get_field_args(self) -> List[str]:
        """
        Get the arguments representing the text field.

        Returns:
            List[str]: A list of text field arguments.
        """
        args = super().get_field_args()
        if self.no_stem:
            args.append("NOSTEM")
        if self.phonetic:
            args.extend(["PHONETIC", self.phonetic.value])
        if self.weight:
            args.extend(["WEIGHT", str(self.weight)])
        if self.with_suffix_trie:
            args.append("WITHSUFFIXTRIE")
        return args


class TagField(SortableIndexableField):
    """
    Class for defining tag fields in a schema.

    Args:
        name (str): The name of the tag field.
        alias (Optional[str]): An alias for the field.
        sortable (Optional[SortableOption]): If not None, sets the field as sortable with the specified sortable option.
        no_index (bool): Indicate whether the field should be excluded from indexing.
        separator (Optional[str]): Specify how text in the attribute is split into individual tags. Must be a single character.
        case_sensitive (bool): Preserve the original letter cases of tags.
            If set to False, characters are converted to lowercase by default.
        with_suffix_trie (bool): Improve query performance by utilizing a suffix trie for contains (foo) and suffix (*foo) queries.
            This feature avoids brute-force searches on the trie.
    """

    def __init__(
        self,
        name: str,
        alias: Optional[str] = None,
        sortable: Optional[SortableOption] = None,
        no_index: bool = False,
        separator: Optional[str] = None,
        case_sensitive: bool = False,
        with_suffix_trie: bool = False,
    ):
        """Initialize a new TagField instance."""
        super().__init__(name, "TAG", alias, sortable, no_index)
        self.sortable = sortable
        self.no_index = no_index
        self.separator = separator
        self.case_sensitive = case_sensitive
        self.with_suffix_trie = with_suffix_trie

    def get_field_args(self) -> List[str]:
        """
        Get the arguments representing the tag field.

        Returns:
            List[str]: A list of tag field arguments.
        """
        args = super().get_field_args()
        if self.separator:
            args.extend(["SEPARATOR", self.separator])
        if self.case_sensitive:
            args.append("CASESENSITIVE")
        if self.with_suffix_trie:
            args.append("WITHSUFFIXTRIE")

        return args


class NumericField(SortableIndexableField):
    """
    Class for defining numeric fields in a schema.

    Args:
        name (str): The name of the numeric field.
        alias (Optional[str]): An alias for the field.
        sortable (Optional[SortableOption]): If not None, sets the field as sortable with the specified sortable option.
        no_index (bool): Indicate whether the field should be excluded from indexing.
    """

    def __init__(
        self,
        name: str,
        alias: Optional[str] = None,
        sortable: Optional[SortableOption] = None,
        no_index: bool = False,
    ):
        """Initialize a new NumericField instance."""
        super().__init__(name, "NUMERIC", alias, sortable, no_index)


class GeoField(SortableIndexableField):
    """
    Class for defining geo fields in a schema.

    Args:
        name (str): The name of the geo field.
        alias (Optional[str]): An alias for the field.
        sortable (Optional[SortableOption]): If not None, sets the field as sortable with the specified sortable option.
        no_index (bool): Indicate whether the field should be excluded from indexing.
    """

    def __init__(
        self,
        name: str,
        alias: Optional[str] = None,
        sortable: Optional[SortableOption] = None,
        no_index: bool = False,
    ):
        """Initialize a new GeoField instance."""
        super().__init__(name, "GEO", alias, sortable, no_index)


class VectorField(Field):
    """
    Class for defining vector fields in a schema.

    Args:
        name (str): The name of the vector field.
        algorithm (str): The vector indexing algorithm.
        alias (Optional[str]): An alias for the field.
        attributes (Optional[Dict[str, str]]): Additional attributes for the vector field.
    """

    def __init__(
        self,
        name: str,
        algorithm: VectorAlgorithm,
        alias: Optional[str] = None,
        attributes: Optional[Dict[str, str]] = None,
    ):
        """Initialize a new VectorField instance."""
        super().__init__(name, "VECTOR", alias)
        self.algorithm = algorithm
        self.attributes = attributes

    def get_field_args(self) -> List[str]:
        """
        Get the arguments representing the vector field.

        Returns:
            List[str]: A list of vector field arguments.
        """
        args = super().get_field_args()
        args.append(self.algorithm.value)
        if self.attributes:
            args.append(str(2 * len(self.attributes)))
            for key, value in self.attributes.items():
                args.extend([key, value])

        return args


class GeoShapeField(SortableIndexableField):
    """
    Class for defining geo shape fields in a schema.

    Args:
        name (str): The name of the geo shape field.
        alias (Optional[str]): An alias for the field.
        sortable (Optional[SortableOption]): If not None, sets the field as sortable with the specified sortable option.
        no_index (bool): Indicate whether the field should be excluded from indexing.
        coordinate_system (Optional[CoordinateSystem]): The coordinate system for the geo shape field.
    """

    def __init__(
        self,
        name: str,
        alias: Optional[str] = None,
        sortable: Optional[SortableOption] = None,
        no_index: bool = False,
        coordinate_system: Optional[CoordinateSystem] = None,
    ):
        """Initialize a new GeoShapeField instance."""
        super().__init__(name, "GEOSHAPE", alias, sortable, no_index)
        self.coordinate_system = coordinate_system

    def get_field_args(self) -> List[str]:
        """
        Get the arguments representing the geo shape field.
        Returns:
            List[str]: A list of geo shape field arguments.
        """
        args = super().get_field_args()
        if self.coordinate_system:
            args.append(self.coordinate_system.value)

        return args
