// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// GlideClusterClient is a client used for connection in cluster mode.
type GlideClusterClient struct {
	*baseClient
}

// NewGlideClusterClient creates a [GlideClusterClient] in cluster mode using the given [GlideClusterClientConfiguration].
func NewGlideClusterClient(config *GlideClusterClientConfiguration) (*GlideClusterClient, error) {
	client, err := createClient(config)
	if err != nil {
		return nil, err
	}

	return &GlideClusterClient{client}, nil
}
