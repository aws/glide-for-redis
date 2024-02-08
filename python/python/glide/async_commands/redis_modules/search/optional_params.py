# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

from enum import Enum


class FieldFlag(Enum):
    USE_FIELDS = 0
    """
    Stores attribute bits for each term.
    """
    NO_FIELDS = 1
    """
    Does not store attribute bits for each term. Saves memory but does not allow filtering by specific attributes.
    """


class Frequencies(Enum):
    SAVE_FREQUENCIES = 0
    """
    Saves the term frequencies in the index.
    """
    NO_FREQUENCIES = 1
    """
    Avoids saving the term frequencies in the index. Saves memory but does not allow sorting 
        based on the frequencies of a given term within the document.
    """


class Highlights(Enum):
    USE_HIGHLIGHTS = 0
    """
    Stores corresponding byte offsets for term positions.
    """
    NO_HIGHLIGHTS = 1
    """
    Conserves storage space and memory by disabling highlighting support. 
        Corresponding byte offsets for term positions are not stored.
        Implied by NOOFFSETS (Offset.NO_OFFSET).
    """


class InitialScan(Enum):
    SCAN_INDEX = 0
    """
    Scans the index.
    """
    SKIP_SCAN = 1
    """
    Does not scan the index.
    """


class Offset(Enum):
    STORE_OFFSET = 0
    """
    Stores term offsets for documents.
    """
    NO_OFFSET = 1
    """
    Does not store term offsets for documents. Saves memory but does not allow exact searches or highlighting.
        Implies NOHL (Highlights.NO_HIGHLIGHTS).
    """
