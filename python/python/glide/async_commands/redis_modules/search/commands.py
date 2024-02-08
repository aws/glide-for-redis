# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

from typing import List, Optional, cast

from glide.async_commands.redis_modules.search.optional_params import (
    FieldFlag,
    Frequencies,
    Highlights,
    InitialScan,
    Offset,
)
from glide.constants import TOK
from glide.protobuf.redis_request_pb2 import RequestType
from glide.redis_client import TRedisClient

from .field import Field
from .index import Index


class Search:
    """Class for RediSearch commands."""

    async def create_index(
        self,
        client: TRedisClient,
        index: Index,
        schema: List[Field],
        max_text_fields: bool = False,
        temporary_seconds: Optional[int] = None,
        offset: Offset = Offset.STORE_OFFSET,
        highlight: Highlights = Highlights.USE_HIGHLIGHTS,
        field_flag: FieldFlag = FieldFlag.USE_FIELDS,
        frequencies: Frequencies = Frequencies.SAVE_FREQUENCIES,
        stop_words: Optional[List[str]] = None,
        initial_scan: InitialScan = InitialScan.SCAN_INDEX,
    ) -> TOK:
        """
        Creates a new search index with the specified configuration.
        See https://redis.io/commands/ft.create/ for more details.

        Args:
            client (TRedisClient): The Redis client to execute the command.
            index (Index): The definition of the index, including its name and type.
            schema (List[Field]): The schema of the index, specifying the fields and their types.
            max_text_fields (bool): Forces to encode indexes as if there were more than 32 text attributes.
                Additional attributes (beyond 32) can be added using the module's alter command
            temporary_seconds (Optional[int]): Creates a lightweight temporary index that automatically expires after a set period of inactivity, measured in seconds.
                The index's internal idle timer resets each time a search or addition operation occurs.
                Because such indexes are lightweight, you can create thousands of such indexes without negative performance implications and, therefore,
                you should consider setting `initial_scan` to be InitialScan.SKIP_SCAN to avoid costly scanning.
                Warning: When temporary indexes expire, they drop all the records associated with them.
            offset (Offset): Whether to store term offsets for documents.
            highlight (Highlights): Whether to disable / enable highlighting support.
            field_flag (FieldFlag): Whether to store attribute bits for each term.
            frequencies (Frequencies): Whether to save the term frequencies in the index.
            stop_words (Optional[List[str]]): Sets the index with a custom stopword list, to be ignored during indexing and search time.
                If not set, the module defaults to using its predefined list of stopwords. If an empty list is provided, the index will have no stopwords.
            initial_scan (InitialScan): Whether to scan and index.

        Returns:
            A simple OK response.

        Examples:
            >>> index = Index("my_index", IndexType.HASH)
            >>> schema = [TextField("name"), NumericField("age")]
            >>> await search_commands.create_index(
                    redis_client,
                    index,
                    schema,
                    max_text_fields=True,
                    temporary_seconds=3600,
                    field_flags=FieldFlags.NO_FIELDS,
                    stopwords=["the", "and", "is"]
                )
                "OK"
        """
        args = index.get_index_atr()
        if max_text_fields:
            args.append("MAXTEXTFIELDS")
        if temporary_seconds is not None:
            args.extend(["TEMPORARY", str(temporary_seconds)])
        if offset == Offset.NO_OFFSET:
            args.append("NOOFFSETS")
        if highlight == Highlights.NO_HIGHLIGHTS:
            args.append("NOHL")
        if field_flag == FieldFlag.NO_FIELDS:
            args.append("NOFIELDS")
        if frequencies == Frequencies.NO_FREQUENCIES:
            args.append("NOFREQS")
        if stop_words is not None:
            args.extend(["STOPWORDS", str(len(stop_words)), *stop_words])
        if initial_scan == InitialScan.SKIP_SCAN:
            args.append("SKIPINITIALSCAN")
        args.append("SCHEMA")
        for field in schema:
            args.extend(field.get_field_args())

        return cast(TOK, await client._execute_command(RequestType.FTCreateIndex, args))
