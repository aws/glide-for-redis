/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import java.util.Map;

/**
 * Represents a returned value object from a Redis server with cluster-mode enabled. The response
 * type may depend on the submitted {@link Route}.
 *
 * @remarks ClusterValue stores values in a union-like object. It contains a single-value or
 *     multi-value response from Redis. If the command's routing is to a single node use {@link
 *     #getSingleValue()} to return a response of type <code>T</code>. Otherwise, use {@link
 *     #getMultiValue()} to return a <code>Map</code> of <code>address: nodeResponse</code> where
 *     <code>address</code> is of type <code>string</code> and <code>nodeResponse</code> is of type
 *     <code>T</code>.
 * @see <a href="https://redis.io/docs/reference/cluster-spec/">Redis cluster specification</a>
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
        var res = new ClusterValue<T>();
        if (data instanceof Map) {
            res.multiValue = (Map<String, T>) data;
        } else {
            res.singleValue = (T) data;
        }
        return res;
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
