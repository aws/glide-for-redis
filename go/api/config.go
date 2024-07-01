// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import "github.com/aws/glide-for-redis/go/glide/protobuf"

const (
	defaultHost = "localhost"
	defaultPort = 6379
)

// NodeAddress represents the host address and port of a node in the cluster.
type NodeAddress struct {
	Host string // If not supplied, "localhost" will be used.
	Port int    // If not supplied, 6379 will be used.
}

func (addr *NodeAddress) toProtobuf() *protobuf.NodeAddress {
	if addr.Host == "" {
		addr.Host = defaultHost
	}

	if addr.Port == 0 {
		addr.Port = defaultPort
	}

	return &protobuf.NodeAddress{Host: addr.Host, Port: uint32(addr.Port)}
}

// RedisCredentials represents the credentials for connecting to a Redis server.
type RedisCredentials struct {
	// The username that will be used for authenticating connections to the Redis servers. If not supplied, "default"
	// will be used.
	username string
	// The password that will be used for authenticating connections to the Redis servers.
	password string
}

// NewRedisCredentials returns a [RedisCredentials] struct with the given username and password.
func NewRedisCredentials(username string, password string) *RedisCredentials {
	return &RedisCredentials{username, password}
}

// NewRedisCredentialsWithDefaultUsername returns a [RedisCredentials] struct with a default username of "default" and the
// given password.
func NewRedisCredentialsWithDefaultUsername(password string) *RedisCredentials {
	return &RedisCredentials{password: password}
}

func (creds *RedisCredentials) toProtobuf() *protobuf.AuthenticationInfo {
	return &protobuf.AuthenticationInfo{Username: creds.username, Password: creds.password}
}

// ReadFrom represents the client's read from strategy.
type ReadFrom int

const (
	// Primary - Always get from primary, in order to get the freshest data.
	Primary ReadFrom = iota
	// PreferReplica - Spread the requests between all replicas in a round-robin manner. If no replica is available, route the
	// requests to the primary.
	PreferReplica
)

func mapReadFrom(readFrom ReadFrom) protobuf.ReadFrom {
	if readFrom == PreferReplica {
		return protobuf.ReadFrom_PreferReplica
	}

	return protobuf.ReadFrom_Primary
}

type baseClientConfiguration struct {
	addresses      []NodeAddress
	useTLS         bool
	credentials    *RedisCredentials
	readFrom       ReadFrom
	requestTimeout int
	clientName     string
}

func (config *baseClientConfiguration) toProtobuf() *protobuf.ConnectionRequest {
	request := protobuf.ConnectionRequest{}
	for _, address := range config.addresses {
		request.Addresses = append(request.Addresses, address.toProtobuf())
	}

	if config.useTLS {
		request.TlsMode = protobuf.TlsMode_SecureTls
	} else {
		request.TlsMode = protobuf.TlsMode_NoTls
	}

	if config.credentials != nil {
		request.AuthenticationInfo = config.credentials.toProtobuf()
	}

	request.ReadFrom = mapReadFrom(config.readFrom)
	if config.requestTimeout != 0 {
		request.RequestTimeout = uint32(config.requestTimeout)
	}

	if config.clientName != "" {
		request.ClientName = config.clientName
	}

	return &request
}

// BackoffStrategy represents the strategy used to determine how and when to reconnect, in case of connection failures. The
// time between attempts grows exponentially, to the formula:
//
//	rand(0 ... factor * (exponentBase ^ N))
//
// where N is the number of failed attempts.
//
// Once the maximum value is reached, that will remain the time between retry attempts until a reconnect attempt is successful.
// The client will attempt to reconnect indefinitely.
type BackoffStrategy struct {
	// Number of retry attempts that the client should perform when disconnected from the server, where the time
	// between retries increases. Once the retries have reached the maximum value, the time between retries will remain
	// constant until a reconnect attempt is successful.
	numOfRetries int
	// The multiplier that will be applied to the waiting time between each retry.
	factor int
	// The exponent base configured for the strategy.
	exponentBase int
}

// NewBackoffStrategy returns a [BackoffStrategy] with the given configuration parameters.
func NewBackoffStrategy(numOfRetries int, factor int, exponentBase int) *BackoffStrategy {
	return &BackoffStrategy{numOfRetries, factor, exponentBase}
}

func (strategy *BackoffStrategy) toProtobuf() *protobuf.ConnectionRetryStrategy {
	return &protobuf.ConnectionRetryStrategy{
		NumberOfRetries: uint32(strategy.numOfRetries),
		Factor:          uint32(strategy.factor),
		ExponentBase:    uint32(strategy.exponentBase),
	}
}

// RedisClientConfiguration represents the configuration settings for a Standalone Redis client. baseClientConfiguration is an
// embedded struct that contains shared settings for standalone and cluster clients.
type RedisClientConfiguration struct {
	baseClientConfiguration
	reconnectStrategy *BackoffStrategy
	databaseId        int
}

// NewRedisClientConfiguration returns a [RedisClientConfiguration] with default configuration settings. For further
// configuration, use the [RedisClientConfiguration] With* methods.
func NewRedisClientConfiguration() *RedisClientConfiguration {
	return &RedisClientConfiguration{}
}

func (config *RedisClientConfiguration) toProtobuf() *protobuf.ConnectionRequest {
	request := config.baseClientConfiguration.toProtobuf()
	request.ClusterModeEnabled = false
	if config.reconnectStrategy != nil {
		request.ConnectionRetryStrategy = config.reconnectStrategy.toProtobuf()
	}

	if config.databaseId != 0 {
		request.DatabaseId = uint32(config.databaseId)
	}

	return request
}

// WithAddress adds an address for a known node in the cluster to this configuration's list of addresses. WithAddress can be
// called multiple times to add multiple addresses to the list. If the server is in cluster mode the list can be partial, as
// the client will attempt to map out the cluster and find all nodes. If the server is in standalone mode, only nodes whose
// addresses were provided will be used by the client. For example:
//
//	config := NewRedisClientConfiguration().
//	    WithAddress(&NodeAddress{
//	        Host: "sample-address-0001.use1.cache.amazonaws.com", Port: 6379}).
//	    WithAddress(&NodeAddress{
//	        Host: "sample-address-0002.use1.cache.amazonaws.com", Port: 6379})
func (config *RedisClientConfiguration) WithAddress(address *NodeAddress) *RedisClientConfiguration {
	config.addresses = append(config.addresses, *address)
	return config
}

// WithUseTLS configures the TLS settings for this configuration. Set to true if communication with the cluster should use
// Transport Level Security. This setting should match the TLS configuration of the server/cluster, otherwise the connection
// attempt will fail.
func (config *RedisClientConfiguration) WithUseTLS(useTLS bool) *RedisClientConfiguration {
	config.useTLS = useTLS
	return config
}

// WithCredentials sets the credentials for the authentication process. If none are set, the client will not authenticate
// itself with the server.
func (config *RedisClientConfiguration) WithCredentials(credentials *RedisCredentials) *RedisClientConfiguration {
	config.credentials = credentials
	return config
}

// WithReadFrom sets the client's [ReadFrom] strategy. If not set, [Primary] will be used.
func (config *RedisClientConfiguration) WithReadFrom(readFrom ReadFrom) *RedisClientConfiguration {
	config.readFrom = readFrom
	return config
}

// WithRequestTimeout sets the duration in milliseconds that the client should wait for a request to complete. This duration
// encompasses sending the request, awaiting for a response from the server, and any required reconnections or retries. If the
// specified timeout is exceeded for a pending request, it will result in a timeout error. If not set, a default value will be
// used.
func (config *RedisClientConfiguration) WithRequestTimeout(requestTimeout int) *RedisClientConfiguration {
	config.requestTimeout = requestTimeout
	return config
}

// WithClientName sets the client name to be used for the client. Will be used with CLIENT SETNAME command during connection
// establishment.
func (config *RedisClientConfiguration) WithClientName(clientName string) *RedisClientConfiguration {
	config.clientName = clientName
	return config
}

// WithReconnectStrategy sets the [BackoffStrategy] used to determine how and when to reconnect, in case of connection
// failures. If not set, a default backoff strategy will be used.
func (config *RedisClientConfiguration) WithReconnectStrategy(strategy *BackoffStrategy) *RedisClientConfiguration {
	config.reconnectStrategy = strategy
	return config
}

// WithDatabaseId sets the index of the logical database to connect to.
func (config *RedisClientConfiguration) WithDatabaseId(id int) *RedisClientConfiguration {
	config.databaseId = id
	return config
}

// RedisClusterClientConfiguration represents the configuration settings for a Cluster Redis client.
// Note: Currently, the reconnection strategy in cluster mode is not configurable, and exponential backoff with fixed values is
// used.
type RedisClusterClientConfiguration struct {
	baseClientConfiguration
}

// NewRedisClusterClientConfiguration returns a [RedisClusterClientConfiguration] with default configuration settings. For
// further configuration, use the [RedisClientConfiguration] With* methods.
func NewRedisClusterClientConfiguration() *RedisClusterClientConfiguration {
	return &RedisClusterClientConfiguration{
		baseClientConfiguration: baseClientConfiguration{},
	}
}

func (config *RedisClusterClientConfiguration) toProtobuf() *protobuf.ConnectionRequest {
	request := config.baseClientConfiguration.toProtobuf()
	request.ClusterModeEnabled = true
	return request
}

// WithAddress adds an address for a known node in the cluster to this configuration's list of addresses. WithAddress can be
// called multiple times to add multiple addresses to the list. If the server is in cluster mode the list can be partial, as
// the client will attempt to map out the cluster and find all nodes. If the server is in standalone mode, only nodes whose
// addresses were provided will be used by the client. For example:
//
//	config := NewRedisClusterClientConfiguration().
//	    WithAddress(&NodeAddress{
//	        Host: "sample-address-0001.use1.cache.amazonaws.com", Port: 6379}).
//	    WithAddress(&NodeAddress{
//	        Host: "sample-address-0002.use1.cache.amazonaws.com", Port: 6379})
func (config *RedisClusterClientConfiguration) WithAddress(address *NodeAddress) *RedisClusterClientConfiguration {
	config.addresses = append(config.addresses, *address)
	return config
}

// WithUseTLS configures the TLS settings for this configuration. Set to true if communication with the cluster should use
// Transport Level Security. This setting should match the TLS configuration of the server/cluster, otherwise the connection
// attempt will fail.
func (config *RedisClusterClientConfiguration) WithUseTLS(useTLS bool) *RedisClusterClientConfiguration {
	config.useTLS = useTLS
	return config
}

// WithCredentials sets the credentials for the authentication process. If none are set, the client will not authenticate
// itself with the server.
func (config *RedisClusterClientConfiguration) WithCredentials(
	credentials *RedisCredentials,
) *RedisClusterClientConfiguration {
	config.credentials = credentials
	return config
}

// WithReadFrom sets the client's [ReadFrom] strategy. If not set, [Primary] will be used.
func (config *RedisClusterClientConfiguration) WithReadFrom(readFrom ReadFrom) *RedisClusterClientConfiguration {
	config.readFrom = readFrom
	return config
}

// WithRequestTimeout sets the duration in milliseconds that the client should wait for a request to complete. This duration
// encompasses sending the request, awaiting for a response from the server, and any required reconnections or retries. If the
// specified timeout is exceeded for a pending request, it will result in a timeout error. If not set, a default value will be
// used.
func (config *RedisClusterClientConfiguration) WithRequestTimeout(requestTimeout int) *RedisClusterClientConfiguration {
	config.requestTimeout = requestTimeout
	return config
}

// WithClientName sets the client name to be used for the client. Will be used with CLIENT SETNAME command during connection
// establishment.
func (config *RedisClusterClientConfiguration) WithClientName(clientName string) *RedisClusterClientConfiguration {
	config.clientName = clientName
	return config
}
