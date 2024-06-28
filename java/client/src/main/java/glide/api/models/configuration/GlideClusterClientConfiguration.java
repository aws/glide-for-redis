/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import lombok.experimental.SuperBuilder;

/**
 * Represents the configuration settings for a Cluster Redis client. Notes: Currently, the
 * reconnection strategy in cluster mode is not configurable, and exponential backoff with fixed
 * values is used.
 */
@SuperBuilder
public class GlideClusterClientConfiguration extends BaseClientConfiguration {}
