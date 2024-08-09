/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a returned value object from a the server with cluster-mode enabled. The response type
 * may depend on the submitted {@link Route}.
 *
 * @remarks ClusterValue stores values in a union-like object. It contains a single-value or
 *     multi-value response from the server. If the command's routing is to a single node use {@link
 *     #getSingleValue()} to return a response of type <code>T</code>. Otherwise, use {@link
 *     #getMultiValue()} to return a <code>Map</code> of <code>address: nodeResponse</code> where
 *     <code>address</code> is of type <code>string</code> and <code>nodeResponse</code> is of type
 *     <code>T</code>.
 * @see <a href="https://valkey.io/docs/topics/cluster-spec/">Valkey cluster specification</a>
 * @param <T> The wrapped response type
 */
public class ClusterValue<T> {
    private Map<String, T> multiValue = null;

    private T singleValue = null;

    private ClusterValue() {}

    /**
     * Get per-node value.<br>
     * Asserts if {@link #hasMultiData()} is false.
     */
    public Map<String, T> getMultiValue() {
        assert hasMultiData() : "No multi value stored";
        return multiValue;
    }

    /**
     * Get the single value.<br>
     * Asserts if {@link #hasSingleData()} is false.
     */
    public T getSingleValue() {
        assert hasSingleData() : "No single value stored";
        return singleValue;
    }

    /** A constructor for the value with type auto-detection. */
    @SuppressWarnings("unchecked")
    public static <T> ClusterValue<T> of(Object data) {
        if (data instanceof Map) {
            var map = (Map<?, T>) data;
            if (map.isEmpty()) {
                return ofMultiValue((Map<String, T>) data);
            }
            if (map.keySet().toArray()[0] instanceof String) {
                return ofMultiValue((Map<String, T>) data);
            } else { // GlideString
                return ofMultiValueBinary((Map<GlideString, T>) data);
            }
        } else {
            return ofSingleValue((T) data);
        }
    }

    /** A constructor for the value. */
    public static <T> ClusterValue<T> ofSingleValue(T data) {
        var res = new ClusterValue<T>();
        res.singleValue = data;
        return res;
    }

    /** A constructor for the value. */
    public static <T> ClusterValue<T> ofMultiValue(Map<String, T> data) {
        var res = new ClusterValue<T>();
        res.multiValue = data;
        return res;
    }

    /** A constructor for the value. */
    public static <T> ClusterValue<T> ofMultiValueBinary(Map<GlideString, T> data) {
        var res = new ClusterValue<T>();
        // the map node address can be converted to a string
        res.multiValue =
                data.entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().getString(), Map.Entry::getValue));
        return res;
    }

    /**
     * Check that multi-value is stored in this object. Should be called prior to {@link
     * #getMultiValue()}.
     */
    public boolean hasMultiData() {
        return multiValue != null;
    }

    /**
     * Check that single-value is stored in this object. Should be called prior to {@link
     * #getSingleValue()}.
     */
    public boolean hasSingleData() {
        return !hasMultiData();
    }
}
