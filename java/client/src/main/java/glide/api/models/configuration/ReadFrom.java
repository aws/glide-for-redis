/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

/** Represents the client's read from strategy. */
public enum ReadFrom {
    /** Always get from primary, in order to get the freshest data. */
    PRIMARY,
    /**
     * Spread the requests between all replicas in a round-robin manner. If no replica is available,
     * route the requests to the primary.
     */
    PREFER_REPLICA,
    /**
     * Spread the read requests between replicas in the same client's AZ (Aviliablity zone) in a
     * round-robin manner, falling back to other replicas or the primary if needed.
     */
    AZ_AFFINITY,
    /**
     * Spread the read requests among nodes within the client's Availability Zone (AZ) in a round
     * robin manner, prioritizing local replicas, then the local primary, and falling back to any
     * replica or the primary if needed.
     */
    AZ_AFFINITY_REPLICAS_AND_PRIMARY,
    /** Spread the read requests between all nodes (primary and replicas) in a round-robin manner. */
    ALL_NODES,
    /**
     * Spread the read requests equally among all nodes (primary and replicas) within the client's
     * Availability Zone (AZ) in a round-robin manner, falling back to a round-robin across all nodes
     * if no node in the client's AZ is available.
     *
     * <p>Unlike {@link #AZ_AFFINITY_REPLICAS_AND_PRIMARY}, this strategy does not prioritize replicas
     * ahead of the primary within the AZ, which is what makes an even per-node read distribution
     * possible. Unlike {@link #ALL_NODES}, which is AZ-agnostic, this strategy is scoped to the
     * client's AZ.
     *
     * <p>Choose this over {@link #AZ_AFFINITY_REPLICAS_AND_PRIMARY} when the in-AZ primary should
     * take a share of the reads. That strategy sends every read to an in-AZ replica whenever one is
     * connected, so the primary serves reads only as a fallback.
     *
     * <p>Requires {@code clientAZ} to be set on the client configuration.
     */
    AZ_AFFINITY_ALL_NODES;

    /** Whether this strategy needs {@code clientAZ} set on the client configuration. */
    public boolean requiresClientAz() {
        // A new AZ-scoped strategy has to be listed here too.
        // ConnectionManagerTest.requiresClientAz_isSetForExactlyTheAzStrategies fails until every
        // constant has an explicit answer.
        return this == AZ_AFFINITY
                || this == AZ_AFFINITY_REPLICAS_AND_PRIMARY
                || this == AZ_AFFINITY_ALL_NODES;
    }
}
