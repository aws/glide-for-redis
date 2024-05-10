// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"errors"
	"fmt"
	"log"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
	"github.com/valkey-io/valkey-glide/go/glide/api"
)

type GlideTestSuite struct {
	suite.Suite
	standalonePorts []int
	clusterPorts    []int
	redisVersion    string
	clients         []*api.GlideClient
	clusterClients  []*api.GlideClusterClient
}

func (suite *GlideTestSuite) SetupSuite() {
	// Stop cluster in case previous test run was interrupted or crashed and didn't stop.
	// If an error occurs, we ignore it in case the servers actually were stopped before running this.
	runClusterManager(suite, []string{"stop", "--prefix", "redis-cluster"}, true)

	// Delete dirs if stop failed due to https://github.com/valkey-io/valkey-glide/issues/849
	err := os.RemoveAll("../../utils/clusters")
	if err != nil && !os.IsNotExist(err) {
		log.Fatal(err)
	}

	// Start standalone instance
	clusterManagerOutput := runClusterManager(suite, []string{"start", "-r", "0"}, false)

	suite.standalonePorts = extractPorts(suite, clusterManagerOutput)
	suite.T().Logf("Standalone ports = %s", fmt.Sprint(suite.standalonePorts))

	// Start cluster
	clusterManagerOutput = runClusterManager(suite, []string{"start", "--cluster-mode"}, false)

	suite.clusterPorts = extractPorts(suite, clusterManagerOutput)
	suite.T().Logf("Cluster ports = %s", fmt.Sprint(suite.clusterPorts))

	// Get Redis version
	byteOutput, err := exec.Command("redis-server", "-v").Output()
	if err != nil {
		suite.T().Fatal(err.Error())
	}

	suite.redisVersion = extractServerVersion(string(byteOutput))
	suite.T().Logf("Redis version = %s", suite.redisVersion)
}

func extractPorts(suite *GlideTestSuite, output string) []int {
	var ports []int
	for _, line := range strings.Split(output, "\n") {
		if !strings.HasPrefix(line, "CLUSTER_NODES=") {
			continue
		}

		addresses := strings.Split(line, "=")[1]
		addressList := strings.Split(addresses, ",")
		for _, address := range addressList {
			portString := strings.Split(address, ":")[1]
			port, err := strconv.Atoi(portString)
			if err != nil {
				suite.T().Fatalf("Failed to parse port from cluster_manager.py output: %s", err.Error())
			}

			ports = append(ports, port)
		}
	}

	return ports
}

func runClusterManager(suite *GlideTestSuite, args []string, ignoreExitCode bool) string {
	pythonArgs := append([]string{"../../utils/cluster_manager.py"}, args...)
	output, err := exec.Command("python3", pythonArgs...).Output()
	if len(output) > 0 {
		suite.T().Logf("cluster_manager.py stdout:\n====\n%s\n====\n", string(output))
	}

	if err != nil {
		var exitError *exec.ExitError
		isExitError := errors.As(err, &exitError)
		if !isExitError {
			suite.T().Fatalf("Unexpected error while executing cluster_manager.py: %s", err.Error())
		}

		if exitError.Stderr != nil && len(exitError.Stderr) > 0 {
			suite.T().Logf("cluster_manager.py stderr:\n====\n%s\n====\n", string(exitError.Stderr))
		}

		if !ignoreExitCode {
			suite.T().Fatalf("cluster_manager.py script failed: %s", exitError.Error())
		}
	}

	return string(output)
}

func extractServerVersion(output string) string {
	// Redis response:
	// Redis server v=7.2.3 sha=00000000:0 malloc=jemalloc-5.3.0 bits=64 build=7504b1fedf883f2
	// Valkey response:
	// Server v=7.2.5 sha=26388270:0 malloc=jemalloc-5.3.0 bits=64 build=ea40bb1576e402d6
	versionSection := strings.Split(output, "v=")[1]
	return strings.Split(versionSection, " ")[0]
}

func TestGlideTestSuite(t *testing.T) {
	suite.Run(t, new(GlideTestSuite))
}

func (suite *GlideTestSuite) TearDownSuite() {
	runClusterManager(suite, []string{"stop", "--prefix", "redis-cluster", "--keep-folder"}, false)
}

func (suite *GlideTestSuite) TearDownTest() {
	for _, client := range suite.clients {
		client.Close()
	}

	for _, client := range suite.clusterClients {
		client.Close()
	}
}

func (suite *GlideTestSuite) runWithDefaultClients(test func(client api.BaseClient)) {
	clients := suite.getDefaultClients()
	suite.runWithClients(clients, test)
}

func (suite *GlideTestSuite) getDefaultClients() []api.BaseClient {
	return []api.BaseClient{suite.defaultClient(), suite.defaultClusterClient()}
}

func (suite *GlideTestSuite) defaultClient() *api.GlideClient {
	config := api.NewGlideClientConfiguration().
		WithAddress(&api.NodeAddress{Port: suite.standalonePorts[0]}).
		WithRequestTimeout(5000)
	return suite.client(config)
}

func (suite *GlideTestSuite) client(config *api.GlideClientConfiguration) *api.GlideClient {
	client, err := api.NewGlideClient(config)

	assert.Nil(suite.T(), err)
	assert.NotNil(suite.T(), client)

	suite.clients = append(suite.clients, client)
	return client
}

func (suite *GlideTestSuite) defaultClusterClient() *api.GlideClusterClient {
	config := api.NewGlideClusterClientConfiguration().
		WithAddress(&api.NodeAddress{Port: suite.clusterPorts[0]}).
		WithRequestTimeout(5000)
	return suite.clusterClient(config)
}

func (suite *GlideTestSuite) clusterClient(config *api.GlideClusterClientConfiguration) *api.GlideClusterClient {
	client, err := api.NewGlideClusterClient(config)

	assert.Nil(suite.T(), err)
	assert.NotNil(suite.T(), client)

	suite.clusterClients = append(suite.clusterClients, client)
	return client
}

func (suite *GlideTestSuite) runWithClients(clients []api.BaseClient, test func(client api.BaseClient)) {
	for i, client := range clients {
		suite.T().Run(fmt.Sprintf("Testing [%v]", i), func(t *testing.T) {
			test(client)
		})
	}
}

func (suite *GlideTestSuite) verifyOK(result string, err error) {
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), api.OK, result)
}
