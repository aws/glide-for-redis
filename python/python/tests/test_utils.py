# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import pytest
from glide.logger import Level, Logger
from tests.conftest import DEFAULT_TEST_LOG_LEVEL
from tests.utils.utils import compare_maps


class TestLogger:
    def test_init_logger(self):
        # The logger is already configured in the conftest file, so calling init again shouldn't modify the log level
        Logger.init(Level.ERROR)
        assert Logger.logger_level == DEFAULT_TEST_LOG_LEVEL.value

    def test_set_logger_config(self):
        Logger.set_logger_config(Level.INFO)
        assert Logger.logger_level == Level.INFO.value
        # Revert to the tests default log level
        Logger.set_logger_config(DEFAULT_TEST_LOG_LEVEL)
        assert Logger.logger_level == DEFAULT_TEST_LOG_LEVEL.value


class TestCompareMaps:
    def test_empty_maps(self):
        map1 = {}
        map2 = {}
        assert compare_maps(map1, map2) is True

    def test_same_key_value_pairs(self):
        map1 = {"a": 1, "b": 2}
        map2 = {"a": 1, "b": 2}
        assert compare_maps(map1, map2) is True

    def test_different_key_value_pairs(self):
        map1 = {"a": 1, "b": 2}
        map2 = {"a": 1, "b": 3}
        assert compare_maps(map1, map2) is False

    def test_different_key_value_pairs_order(self):
        map1 = {"a": 1, "b": 2}
        map2 = {"b": 2, "a": 1}
        assert compare_maps(map1, map2) is False

    def test_nested_maps_same_values(self):
        map1 = {"a": {"b": 1}}
        map2 = {"a": {"b": 1}}
        assert compare_maps(map1, map2) is True

    def test_nested_maps_different_values(self):
        map1 = {"a": {"b": 1}}
        map2 = {"a": {"b": 2}}
        assert compare_maps(map1, map2) is False

    def test_nested_maps_different_order(self):
        map1 = {"a": {"b": 1, "c": 2}}
        map2 = {"a": {"c": 2, "b": 1}}
        assert compare_maps(map1, map2) is False

    def test_arrays_same_values(self):
        map1 = {"a": [1, 2]}
        map2 = {"a": [1, 2]}
        assert compare_maps(map1, map2) is True

    def test_arrays_different_values(self):
        map1 = {"a": [1, 2]}
        map2 = {"a": [1, 3]}
        assert compare_maps(map1, map2) is False

    def test_null_values(self):
        map1 = {"a": None}
        map2 = {"a": None}
        assert compare_maps(map1, map2) is True

    def test_mixed_types_same_values(self):
        map1 = {
            "a": 1,
            "b": {"c": [2, 3]},
            "d": None,
            "e": "string",
            "f": [1, "2", True],
        }
        map2 = {
            "a": 1,
            "b": {"c": [2, 3]},
            "d": None,
            "e": "string",
            "f": [1, "2", True],
        }
        assert compare_maps(map1, map2) is True

    def test_mixed_types_different_values(self):
        map1 = {
            "a": 1,
            "b": {"c": [2, 3]},
            "d": None,
            "e": "string",
            "f": [1, "2", False],
        }
        map2 = {
            "a": 1,
            "b": {"c": [2, 3]},
            "d": None,
            "f": [1, "2", False],
            "e": "string",
        }
        assert compare_maps(map1, map2) is False
