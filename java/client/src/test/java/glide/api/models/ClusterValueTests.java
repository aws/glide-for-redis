/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static glide.api.models.GlideString.gs;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class ClusterValueTests {

    @Test
    public void handle_null() {
        var value = ClusterValue.of(null);
        assertAll(
                () -> assertFalse(value.hasMultiData()),
                () -> assertTrue(value.hasSingleData()),
                () -> assertNull(value.getSingleValue()),
                () ->
                        assertEquals(
                                "No multi value stored",
                                assertThrows(Throwable.class, value::getMultiValue).getMessage()));
    }

    @Test
    public void handle_single_data() {
        var value = ClusterValue.of(42);
        assertAll(
                () -> assertFalse(value.hasMultiData()),
                () -> assertTrue(value.hasSingleData()),
                () -> assertEquals(42, value.getSingleValue()),
                () ->
                        assertEquals(
                                "No multi value stored",
                                assertThrows(Throwable.class, value::getMultiValue).getMessage()));
    }

    @Test
    public void handle_empty_map() {
        var value = ClusterValue.of(Map.of());
        assertAll(
                () -> assertTrue(value.hasMultiData()),
                () -> assertFalse(value.hasSingleData()),
                () -> assertNotNull(value.getMultiValue()),
                () -> assertEquals(Map.of(), value.getMultiValue()),
                () ->
                        assertEquals(
                                "No single value stored",
                                assertThrows(Throwable.class, value::getSingleValue).getMessage()));
    }

    @Test
    public void handle_multi_data() {
        var data = Map.of("node1", Map.of("config1", "param1", "config2", "param2"), "node2", Map.of());
        var value = ClusterValue.of(data);
        assertAll(
                () -> assertTrue(value.hasMultiData()),
                () -> assertFalse(value.hasSingleData()),
                () -> assertNotNull(value.getMultiValue()),
                () -> assertEquals(data, value.getMultiValue()),
                () ->
                        assertEquals(
                                "No single value stored",
                                assertThrows(Throwable.class, value::getSingleValue).getMessage()));
    }

    @Test
    public void handle_multi_binary_data() {
        var data =
                Map.of(
                        gs("node1"),
                        Map.of(gs("config1"), gs("param1"), gs("config2"), gs("param2")),
                        gs("node2"),
                        Map.of());
        var dataNormalized =
                Map.of(
                        "node1",
                        Map.of(gs("config1"), gs("param1"), gs("config2"), gs("param2")),
                        "node2",
                        Map.of());
        var value = ClusterValue.of(data);
        assertAll(
                () -> assertTrue(value.hasMultiData()),
                () -> assertFalse(value.hasSingleData()),
                () -> assertNotNull(value.getMultiValue()),
                () -> assertEquals(dataNormalized, value.getMultiValue()),
                () ->
                        assertEquals(
                                "No single value stored",
                                assertThrows(Throwable.class, value::getSingleValue).getMessage()));
    }

    @Test
    public void single_value_ctor() {
        var value = ClusterValue.ofSingleValue(Map.of("config1", "param1", "config2", "param2"));
        assertAll(
                () -> assertFalse(value.hasMultiData()),
                () -> assertTrue(value.hasSingleData()),
                () -> assertNotNull(value.getSingleValue()));
    }

    @Test
    public void multi_value_ctor() {
        var value = ClusterValue.ofMultiValue(Map.of("config1", "param1", "config2", "param2"));
        assertAll(
                () -> assertTrue(value.hasMultiData()),
                () -> assertFalse(value.hasSingleData()),
                () -> assertNotNull(value.getMultiValue()),
                () -> assertTrue(value.getMultiValue().containsKey("config1")),
                () -> assertTrue(value.getMultiValue().containsKey("config2")));
    }

    @Test
    public void multi_value_binary_ctor() {
        var value =
                ClusterValue.ofMultiValueBinary(
                        Map.of(gs("config1"), gs("param1"), gs("config2"), gs("param2")));
        assertAll(
                () -> assertTrue(value.hasMultiData()),
                () -> assertFalse(value.hasSingleData()),
                () -> assertNotNull(value.getMultiValue()),
                // ofMultiValueBinary converts the key to a String, but the values are not converted
                () -> assertTrue(value.getMultiValue().containsKey("config1")),
                () -> assertEquals(gs("param1"), value.getMultiValue().get("config1")),
                () -> assertTrue(value.getMultiValue().containsKey("config2")),
                () -> assertEquals(gs("param2"), value.getMultiValue().get("config2")));
    }
}
