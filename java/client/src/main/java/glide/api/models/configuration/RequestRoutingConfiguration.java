/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import glide.api.models.exceptions.RequestException;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/** Request routing configuration. */
public class RequestRoutingConfiguration {

    /**
     * Basic interface. Please use one of the following implementations:
     *
     * <ul>
     *   <li>{@link SimpleMultiNodeRoute}
     *   <li>{@link SimpleSingleNodeRoute}
     *   <li>{@link SlotIdRoute}
     *   <li>{@link SlotKeyRoute}
     *   <li>{@link ByAddressRoute}
     * </ul>
     */
    public interface Route {}

    /** A route that addresses single node. */
    public interface SingleNodeRoute extends Route {}

    /** A route that addresses multiple nodes. */
    public interface MultiNodeRoute extends Route {}

    @RequiredArgsConstructor
    @Getter
    public enum SimpleSingleNodeRoute implements SingleNodeRoute {
        /**
         * Route request to a random node.<br>
         * <b>Warning</b><br>
         * Don't use it with write commands, because they could be randomly routed to a replica (RO)
         * node and fail.
         */
        RANDOM(2);

        private final int ordinal;
    }

    @RequiredArgsConstructor
    @Getter
    public enum SimpleMultiNodeRoute implements MultiNodeRoute {
        /**
         * Route request to all nodes. <b>Warning</b><br>
         * Don't use it with write commands, they could be routed to a replica (RO) node and fail.
         */
        ALL_NODES(0),
        /** Route request to all primary nodes. */
        ALL_PRIMARIES(1);

        private final int ordinal;
    }

    /** Defines type of the node being addressed. */
    public enum SlotType {
        /** Address a primary node. */
        PRIMARY,
        /** Address a replica node. */
        REPLICA,
    }

    /**
     * Request routing configuration overrides the {@link ReadFrom} connection configuration.<br>
     * If {@link SlotType#REPLICA} is used, the request will be routed to a replica, even if the
     * strategy is {@link ReadFrom#PRIMARY}.
     */
    @RequiredArgsConstructor
    @Getter
    public static class SlotIdRoute implements SingleNodeRoute {
        /**
         * Slot number. There are 16384 slots in a redis cluster, and each shard manages a slot range.
         * Unless the slot is known, it's better to route using {@link SlotType#PRIMARY}.
         */
        private final int slotId;

        /** Defines type of the node being addressed. */
        private final SlotType slotType;
    }

    /**
     * Request routing configuration overrides the {@link ReadFrom} connection configuration.<br>
     * If {@link SlotType#REPLICA} is used, the request will be routed to a replica, even if the
     * strategy is {@link ReadFrom#PRIMARY}.
     */
    @RequiredArgsConstructor
    @Getter
    public static class SlotKeyRoute implements SingleNodeRoute {
        /** The request will be sent to nodes managing this key. */
        private final String slotKey;

        /** Defines type of the node being addressed. */
        private final SlotType slotType;
    }

    /** Routes a request to a node by its address. */
    @Getter
    public static class ByAddressRoute implements SingleNodeRoute {
        /**
         * The endpoint of the node. If <code>port</code> is not provided, should be in the <code>
         * "address:port"</code> format, where <code>address</code> is the preferred endpoint as shown
         * in the output of the <code>CLUSTER SLOTS</code> command.
         */
        private final String host;

        /**
         * The port to access the node. If port is not provided, <code>host</code> is assumed to be in
         * the format <code>"address:port"</code>.
         */
        private final int port;

        /** Create a route using hostname/address and port. */
        public ByAddressRoute(@NonNull String host, int port) {
            this.host = host;
            this.port = port;
        }

        /** Create a route using address string formatted as <code>"address:port"</code>. */
        public ByAddressRoute(@NonNull String host) {
            String[] split = host.split(":");
            if (split.length < 2) {
                throw new RequestException(
                        "No port provided, and host is not in the expected format 'hostname:port'. Received: "
                                + host);
            }

            this.host = split[0];

            try {
                this.port = Integer.parseInt(split[1]);
            } catch (NumberFormatException e) {
                throw new RequestException("Port must be a valid integer. Received: " + split[1]);
            }
        }
    }
}
