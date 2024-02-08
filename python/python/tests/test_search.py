# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

from typing import Dict, List, Union, cast

import pytest
from glide.async_commands.core import InfoSection
from glide.async_commands.redis_modules.search.commands import Search
from glide.async_commands.redis_modules.search.field import (
    CoordinateSystem,
    Field,
    GeoField,
    GeoShapeField,
    NumericField,
    SortableIndexableField,
    TagField,
    TextField,
    VectorAlgorithm,
    VectorField,
)
from glide.async_commands.redis_modules.search.index import Index, IndexType
from glide.async_commands.redis_modules.search.optional_params import (
    FieldFlag,
    Frequencies,
    Highlights,
    Offset,
)
from glide.constants import OK
from glide.exceptions import RequestError
from glide.redis_client import TRedisClient
from tests.test_async_client import (
    check_if_server_version_lt,
    get_random_string,
    parse_info_response,
)


def parse_ft_info_response(
    res: Union[List[Union[str, List[str], List[List[str]]]], List[List[str]]]
) -> Dict[str, Union[str, List[str], List[List[str]]]]:
    info_dict: Dict[str, Union[str, List[str], List[List[str]]]] = {}
    for i in range(0, len(res), 2):
        key = res[i]
        value = res[i + 1]
        assert type(key) == str
        info_dict[key] = value
    return info_dict


@pytest.mark.asyncio
class TestSearchModule:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_search_module_loaded(self, redis_client: TRedisClient):
        res = parse_info_response(await redis_client.info([InfoSection.MODULES]))
        assert "search" in res["module"]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_create_index_hash(self, redis_client: TRedisClient):
        search = Search()
        idx_name = get_random_string(5)
        index_definition = Index(
            name=idx_name, filter="@age>15", prefix=["man:", "woman:"]
        )

        schema: List[Field] = [
            TextField(name="first_name"),
            TextField(name="last_name"),
            NumericField(name="age"),
            GeoField(name="location"),
        ]

        assert (
            await search.create_index(
                client=redis_client, index=index_definition, schema=schema
            )
            == OK
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_create_index_json(self, redis_client: TRedisClient):
        res = parse_info_response(await redis_client.info([InfoSection.MODULES]))
        assert "ReJSON" in res["module"]

        search = Search()
        idx_name = get_random_string(5)
        index_definition = Index(
            name=idx_name,
            index_type=IndexType.JSON,
            filter="@age>15",
            prefix=["man:", "woman:"],
        )

        schema: List[Field] = [
            TextField(name="$.first_name", alias="first_name"),
            TextField(name="$.last_name", alias="last_name"),
            NumericField(name="$.age", alias="age"),
            GeoField(name="$.location", alias="location"),
        ]

        assert (
            await search.create_index(
                client=redis_client, index=index_definition, schema=schema
            )
            == OK
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_create_index_existing_index(self, redis_client: TRedisClient):
        search = Search()
        index_definition = Index(name=get_random_string(5))
        schema: List[Field] = [TextField(name="field")]
        assert (
            await search.create_index(
                redis_client, index=index_definition, schema=schema
            )
            == OK
        )
        schema += [NumericField(name="numeric field")]
        with pytest.raises(RequestError) as e:
            await search.create_index(
                redis_client, index=index_definition, schema=schema
            )
        assert "index: already exists" in str(e).lower()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_create_index_hash_json(self, redis_client: TRedisClient):
        res = parse_info_response(await redis_client.info([InfoSection.MODULES]))
        assert "ReJSON" in res["module"]

        search = Search()
        idx_name = get_random_string(5)
        idx_name2 = get_random_string(5)
        index_definition = Index(
            name=idx_name,
        )

        schema = [
            TextField(name="text"),
            NumericField(name="numeric"),
            GeoField(name="geo"),
            VectorField(
                name="$.vector_field",
                algorithm=VectorAlgorithm.FLAT,
                attributes={"TYPE": "FLOAT32", "DIM": "8", "DISTANCE_METRIC": "COSINE"},
            ),
            TagField(name="tags"),
        ]
        if not await check_if_server_version_lt(redis_client, "7.2.0"):
            # GeoShape field is only supported on redis 7.2.0 and higher
            schema += [
                GeoShapeField(name="geoshape", coordinate_system=CoordinateSystem.FLAT),
            ]
        assert (
            await search.create_index(
                client=redis_client, index=index_definition, schema=schema
            )
            == OK
        )
        index_definition.name = idx_name2
        index_definition.index_type = IndexType.JSON
        assert (
            await search.create_index(
                client=redis_client, index=index_definition, schema=schema
            )
            == OK
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_create_index_stopwords_empty_list(self, redis_client: TRedisClient):
        search = Search()
        idx_name = get_random_string(5)
        index_definition = Index(
            name=idx_name,
        )
        schema: List[Field] = [TextField(name="text")]
        assert (
            await search.create_index(
                client=redis_client,
                index=index_definition,
                schema=schema,
                stop_words=[],
            )
            == OK
        )

        if await check_if_server_version_lt(redis_client, "7.2.0"):
            info_res: List[List[str]] = cast(
                List[List[str]],
                await redis_client.custom_command(["FT.INFO", idx_name]),
            )
            info = parse_ft_info_response(info_res)
        else:
            info = cast(
                Dict[str, Union[str, List[str], List[List[str]]]],
                await redis_client.custom_command(["FT.INFO", idx_name]),
            )

        assert "stopwords_list" in info.keys()
        assert info["stopwords_list"] == []

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_index_empty_prefix_filter(self, redis_client: TRedisClient):
        search = Search()
        idx_name = get_random_string(5)
        index_definition = Index(name=idx_name, filter="")
        schema: List[Field] = [TextField(name="text")]

        assert (
            await search.create_index(
                client=redis_client, index=index_definition, schema=schema
            )
        ) == OK

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_index_options(self, redis_client: TRedisClient):
        search = Search()
        idx_name = get_random_string(5)
        index_definition = Index(
            name=idx_name,
        )
        schema: List[Field] = [TextField(name="text")]
        assert (
            await search.create_index(
                client=redis_client,
                index=index_definition,
                schema=schema,
                offset=Offset.NO_OFFSET,
                field_flag=FieldFlag.NO_FIELDS,
                frequencies=Frequencies.NO_FREQUENCIES,
                highlight=Highlights.NO_HIGHLIGHTS,
            )
            == OK
        )

        if await check_if_server_version_lt(redis_client, "7.2.0"):
            info_res: List[List[str]] = cast(
                List[List[str]],
                await redis_client.custom_command(["FT.INFO", idx_name]),
            )
            info = parse_ft_info_response(info_res)
        else:
            info = cast(
                Dict[str, Union[str, List[str], List[List[str]]]],
                await redis_client.custom_command(["FT.INFO", idx_name]),
            )
        assert "index_options" in info.keys()
        assert set(info["index_options"]) == set(
            ["NOFREQS", "NOFIELDS", "NOOFFSETS", "NOHL"]
        )

    async def test_abstract_fields(self):
        with pytest.raises(TypeError) as e:
            field = Field("field", "text")

        assert "instantiate abstract class" in str(e)

        with pytest.raises(TypeError) as e:
            field = SortableIndexableField("field", "text")

        assert "instantiate abstract class" in str(e)
