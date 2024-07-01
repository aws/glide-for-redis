/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.ServerManagementCommands;
import java.util.List;
import lombok.Builder;
import lombok.Singular;

/**
 * Optional arguments to {@link ServerManagementCommands#info(InfoOptions)}
 *
 * @see <a href="https://redis.io/commands/info/">redis.io</a>
 */
@Builder
public final class InfoOptions {

    @Singular private final List<Section> sections;

    public enum Section {
        /** SERVER: General information about the Redis server */
        SERVER,
        /** CLIENTS: Client connections section */
        CLIENTS,
        /** MEMORY: Memory consumption related information */
        MEMORY,
        /** PERSISTENCE: RDB and AOF related information */
        PERSISTENCE,
        /** STATS: General statistics */
        STATS,
        /** REPLICATION: Master/replica replication information */
        REPLICATION,
        /** CPU: CPU consumption statistics */
        CPU,
        /** COMMANDSTATS: Redis command statistics */
        COMMANDSTATS,
        /** LATENCYSTATS: Redis command latency percentile distribution statistics */
        LATENCYSTATS,
        /** SENTINEL: Redis Sentinel section (only applicable to Sentinel instances) */
        SENTINEL,
        /** CLUSTER: Redis Cluster section */
        CLUSTER,
        /** MODULES: Modules section */
        MODULES,
        /** KEYSPACE: Database related statistics */
        KEYSPACE,
        /** ERRORSTATS: Redis error statistics */
        ERRORSTATS,
        /** ALL: Return all sections (excluding module generated ones) */
        ALL,
        /** DEFAULT: Return only the default set of sections */
        DEFAULT,
        /** EVERYTHING: Includes all and modules */
        EVERYTHING,
    }

    /**
     * Converts options enum into a String[] to add to a Redis request.
     *
     * @return String[]
     */
    public String[] toArgs() {
        return sections.stream().map(Object::toString).toArray(String[]::new);
    }
}
