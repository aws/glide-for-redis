# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

from enum import Enum
from typing import List, Optional


class IndexType(Enum):
    HASH = "HASH"
    JSON = "JSON"


class Index:
    """
    Class for defining an index in a schema.

    Args:
        index (str): The name of the index.
        index_type (IndexType): The type of the index. To index JSON, ensure the RedisJSON module is installed.
            Default to HASH.
        prefix (Optional[List[str]]): The prefix for the index, tells the index which keys it should index. Multiple prefixes can be added.
            If not set, the default is '*' (all keys).
        filter (Optional[str]): A filter expression. It is possible to use "@__key" to access the key that was just added/changed.
        language (Optional[str]): The default language for documents in the index.
        language_field (Optional[str]): Document attribute set as the document language. If specified, this attribute will be
            used to determine the language for each individual document.
        score (Optional[float]): Default score for documents in the index.
        score_field (Optional[str]): Document attribute used as the document rank based on user ranking
            (between 0.0 and 1.0).
        payload_field (Optional[str]): Document attribute used as a binary-safe payload string to the document.
            It can be evaluated at query time by a custom scoring function or retrieved to the client.
    """

    def __init__(
        self,
        name: str,
        index_type: IndexType = IndexType.HASH,
        prefix: Optional[List[str]] = [],
        filter: Optional[str] = None,
        language: Optional[str] = None,
        language_field: Optional[str] = None,
        score: Optional[float] = None,
        score_field: Optional[str] = None,
        payload_field: Optional[str] = None,
    ):
        self.name = name
        self.index_type = index_type
        self.prefix = prefix
        self.filter = filter
        self.language = language
        self.language_field = language_field
        self.score = score
        self.score_field = score_field
        self.payload_field = payload_field

    def get_index_atr(self):
        """
        Get the index attributes as a list.

        Returns:
            List[str]: A list of index attributes.
        """
        args = [self.name, "ON", self.index_type.value]
        if self.prefix:
            if len(self.prefix) > 0:
                args.extend(["PREFIX", str(len(self.prefix)), *self.prefix])
            else:
                raise ValueError(
                    "Prefix list must contain at least one element when specifying a prefix."
                )
        if self.filter:
            args.extend(["FILTER", self.filter])
        if self.language:
            args.extend(["LANGUAGE", self.language])
        if self.language_field:
            args.extend(["LANGUAGE_FIELD", self.language_field])
        if self.score:
            args.extend(["SCORE", str(self.score)])
        if self.score_field:
            args.extend(["SCORE_FIELD", self.score_field])
        if self.payload_field:
            args.extend(["PAYLOAD_FIELD", self.payload_field])

        return args
