/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { describe, expect, it } from "@jest/globals";
import { compareMaps } from "./TestUtilities";

describe("test compareMaps", () => {
    it("Map comparison of empty maps should return true", () => {
        const map1 = {};
        const map2 = {};
        expect(compareMaps(map1, map2)).toBe(true);
    });

    it("Map comparison of maps with the same key-value pairs should return true", () => {
        const map1 = { a: 1, b: 2 };
        const map2 = { a: 1, b: 2 };
        expect(compareMaps(map1, map2)).toBe(true);
    });

    it("Map comparison of maps with different key-value pairs should return false", () => {
        const map1 = { a: 1, b: 2 };
        const map2 = { a: 1, b: 3 };
        expect(compareMaps(map1, map2)).toBe(false);
    });

    it("Map comparison of maps with different key-value pairs order should return false", () => {
        const map1 = { a: 1, b: 2 };
        const map2 = { b: 2, a: 1 };
        expect(compareMaps(map1, map2)).toBe(false);
    });

    it("Map comparison of maps with nested maps having the same values should return true", () => {
        const map1 = { a: { b: 1 } };
        const map2 = { a: { b: 1 } };
        expect(compareMaps(map1, map2)).toBe(true);
    });

    it("Map comparison of maps with nested maps having different values should return false", () => {
        const map1 = { a: { b: 1 } };
        const map2 = { a: { b: 2 } };
        expect(compareMaps(map1, map2)).toBe(false);
    });

    it("Map comparison of maps with nested maps having different order should return false", () => {
        const map1 = { a: { b: 1, c: 2 } };
        const map2 = { a: { c: 2, b: 1 } };
        expect(compareMaps(map1, map2)).toBe(false);
    });

    it("Map comparison of maps with arrays as values having the same values should return true", () => {
        const map1 = { a: [1, 2] };
        const map2 = { a: [1, 2] };
        expect(compareMaps(map1, map2)).toBe(true);
    });

    it("Map comparison of maps with arrays as values having different values should return false", () => {
        const map1 = { a: [1, 2] };
        const map2 = { a: [1, 3] };
        expect(compareMaps(map1, map2)).toBe(false);
    });

    it("Map comparison of maps with null values should return true", () => {
        const map1 = { a: null };
        const map2 = { a: null };
        expect(compareMaps(map1, map2)).toBe(true);
    });

    it("Map comparison of maps with mixed types of values should return true", () => {
        const map1 = {
            a: 1,
            b: { c: [2, 3] },
            d: null,
            e: "string",
            f: [1, "2", true],
        };
        const map2 = {
            a: 1,
            b: { c: [2, 3] },
            d: null,
            e: "string",
            f: [1, "2", true],
        };
        expect(compareMaps(map1, map2)).toBe(true);
    });

    it("Map comparison of maps with mixed types of values should return false", () => {
        const map1 = {
            a: 1,
            b: { c: [2, 3] },
            d: null,
            e: "string",
            f: [1, "2", false],
        };
        const map2 = {
            a: 1,
            b: { c: [2, 3] },
            d: null,
            f: [1, "2", false],
            e: "string",
        };
        expect(compareMaps(map1, map2)).toBe(false);
    });
});
