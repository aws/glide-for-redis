// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package main

import (
	"crypto/rand"
	"encoding/json"
	"fmt"
	"log"
	"math"
	"math/big"
	"os"
	"sort"
	"strings"
	"time"
)

type connectionSettings struct {
	host               string
	port               int
	useTLS             bool
	clusterModeEnabled bool
}

func runBenchmarks(runConfig *runConfiguration) error {
	connSettings := &connectionSettings{
		host:               runConfig.host,
		port:               runConfig.port,
		useTLS:             runConfig.tls,
		clusterModeEnabled: runConfig.clusterModeEnabled,
	}

	err := executeBenchmarks(runConfig, connSettings)
	if err != nil {
		return err
	}

	if runConfig.resultsFile != os.Stdout {
		return writeResults(runConfig.resultsFile)
	}

	return nil
}

type benchmarkConfig struct {
	clientName         string
	numConcurrentTasks int
	clientCount        int
	dataSize           int
	minimal            bool
	connectionSettings *connectionSettings
	resultsFile        *os.File
}

func executeBenchmarks(runConfig *runConfiguration, connectionSettings *connectionSettings) error {
	var benchmarkConfigs []benchmarkConfig
	for _, clientName := range runConfig.clientNames {
		for _, numConcurrentTasks := range runConfig.concurrentTasks {
			for _, clientCount := range runConfig.clientCount {
				for _, dataSize := range runConfig.dataSize {
					benchmarkConfig := benchmarkConfig{
						clientName:         clientName,
						numConcurrentTasks: numConcurrentTasks,
						clientCount:        clientCount,
						dataSize:           dataSize,
						minimal:            runConfig.minimal,
						connectionSettings: connectionSettings,
						resultsFile:        runConfig.resultsFile,
					}

					benchmarkConfigs = append(benchmarkConfigs, benchmarkConfig)
				}
			}
		}
	}

	for _, config := range benchmarkConfigs {
		err := runSingleBenchmark(&config)
		if err != nil {
			return err
		}
	}

	return nil
}

func runSingleBenchmark(config *benchmarkConfig) error {
	fmt.Printf("Running benchmarking for %s client:\n", config.clientName)
	fmt.Printf(
		"\n =====> %s <===== clientCount: %d, concurrentTasks: %d, dataSize: %d \n\n",
		config.clientName,
		config.clientCount,
		config.numConcurrentTasks,
		config.dataSize,
	)

	clients, err := createClients(config)
	if err != nil {
		return err
	}

	benchmarkResult := measureBenchmark(clients, config)
	if config.resultsFile != os.Stdout {
		addJsonResults(config, benchmarkResult)
	}

	printResults(benchmarkResult)
	fmt.Println()
	return closeClients(clients)
}

type benchmarkClient interface {
	connect(connectionSettings *connectionSettings) error
	set(key string, value string) (string, error)
	get(key string) (string, error)
	close() error
	getName() string
}

func createClients(config *benchmarkConfig) ([]benchmarkClient, error) {
	var clients []benchmarkClient
	for clientNum := 0; clientNum < config.clientCount; clientNum++ {
		var client benchmarkClient
		switch config.clientName {
		case goRedis:
			client = &goRedisBenchmarkClient{}
		case glide:
			client = &glideBenchmarkClient{}
		}

		err := client.connect(config.connectionSettings)
		if err != nil {
			return nil, err
		}

		clients = append(clients, client)
	}

	return clients, nil
}

func closeClients(clients []benchmarkClient) error {
	for _, client := range clients {
		err := client.close()
		if err != nil {
			return err
		}
	}

	return nil
}

var jsonResults []map[string]interface{}

func writeResults(file *os.File) error {
	fileInfo, err := file.Stat()
	if err != nil {
		return err
	}

	if fileInfo.Size() != 0 {
		decoder := json.NewDecoder(file)
		var existingData []map[string]interface{}
		err = decoder.Decode(&existingData)
		if err != nil {
			return err
		}

		jsonResults = append(existingData, jsonResults...)
	}

	marshalledJson, err := json.Marshal(jsonResults)
	if err != nil {
		return err
	}
	_, err = file.WriteAt(marshalledJson, 0)
	if err != nil {
		return err
	}

	return nil
}

type benchmarkResults struct {
	iterationsPerTask int
	duration          time.Duration
	tps               float64
	latencyStats      map[string]*latencyStats
}

func measureBenchmark(clients []benchmarkClient, config *benchmarkConfig) *benchmarkResults {
	var iterationsPerTask int
	if config.minimal {
		iterationsPerTask = 1000
	} else {
		iterationsPerTask = int(math.Min(math.Max(1e5, float64(config.numConcurrentTasks*1e4)), 1e7))
	}

	actions := getActions(config.dataSize)
	duration, latencies := runBenchmark(iterationsPerTask, config.numConcurrentTasks, actions, clients)
	tps := calculateTPS(latencies, duration)
	stats := getLatencyStats(latencies)
	return &benchmarkResults{
		iterationsPerTask: iterationsPerTask,
		duration:          duration,
		tps:               tps,
		latencyStats:      stats,
	}
}

func calculateTPS(latencies map[string][]time.Duration, totalDuration time.Duration) float64 {
	numRequests := 0
	for _, durations := range latencies {
		numRequests += len(durations)
	}

	return float64(numRequests) / totalDuration.Seconds()
}

type operations func(client benchmarkClient) (string, error)

const (
	getExisting    = "get_existing"
	getNonExisting = "get_non_existing"
	set            = "set"
)

func getActions(dataSize int) map[string]operations {
	actions := map[string]operations{
		getExisting: func(client benchmarkClient) (string, error) {
			return client.get(keyFromExistingKeyspace())
		},
		getNonExisting: func(client benchmarkClient) (string, error) {
			return client.get(keyFromNewKeyspace())
		},
		set: func(client benchmarkClient) (string, error) {
			return client.set(keyFromExistingKeyspace(), strings.Repeat("0", dataSize))
		},
	}

	return actions
}

const (
	sizeNewKeyspace      = 3750000
	sizeExistingKeyspace = 3000000
)

func keyFromExistingKeyspace() string {
	randNum, err := rand.Int(rand.Reader, big.NewInt(sizeExistingKeyspace))
	if err != nil {
		log.Fatal("Error while generating random number for existing keyspace: ", err)
	}

	return fmt.Sprint(randNum.Int64() + 1)
}

func keyFromNewKeyspace() string {
	var totalRange int64 = sizeNewKeyspace - sizeExistingKeyspace
	randNum, err := rand.Int(rand.Reader, big.NewInt(totalRange))
	if err != nil {
		log.Fatal("Error while generating random number for existing keyspace: ", err)
	}

	return fmt.Sprint(randNum.Int64() + sizeExistingKeyspace + 1)
}

type actionLatency struct {
	action  string
	latency time.Duration
}

func runBenchmark(
	iterationsPerTask int,
	concurrentTasks int,
	actions map[string]operations,
	clients []benchmarkClient,
) (totalDuration time.Duration, latencies map[string][]time.Duration) {
	latencies = map[string][]time.Duration{
		getExisting:    {},
		getNonExisting: {},
		set:            {},
	}

	numResults := concurrentTasks * iterationsPerTask
	results := make(chan *actionLatency, numResults)
	start := time.Now()
	for i := 0; i < concurrentTasks; i++ {
		go runTask(results, iterationsPerTask, actions, clients)
	}

	for i := 0; i < numResults; i++ {
		result := <-results
		latencies[result.action] = append(latencies[result.action], result.latency)
	}

	return time.Since(start), latencies
}

func runTask(results chan<- *actionLatency, iterations int, actions map[string]operations, clients []benchmarkClient) {
	for i := 0; i < iterations; i++ {
		clientIndex := i % len(clients)
		action := randomAction()
		operation := actions[action]
		latency := measureOperation(operation, clients[clientIndex])
		results <- &actionLatency{action: action, latency: latency}
	}
}

func measureOperation(operation operations, client benchmarkClient) time.Duration {
	start := time.Now()
	_, err := operation(client)
	duration := time.Since(start)
	if err != nil {
		log.Print("error while executing operation: ", err)
	}

	return duration
}

const (
	probGet            = 0.8
	probGetExistingKey = 0.8
)

// randFloat generates a random float64 in the range [0.0, 1.0)
func randFloat() float64 {
	// 1 << 53 is used because a float64 value contains 53 bits for the mantissa and 11 other bits.
	randInt, err := rand.Int(rand.Reader, big.NewInt(1<<53))
	if err != nil {
		log.Fatal("Error creating random float64: ", err)
	}
	return float64(randInt.Int64()) / (1 << 53)
}

func randomAction() string {
	if randFloat() > probGet {
		return set
	}

	if randFloat() > probGetExistingKey {
		return getNonExisting
	}

	return getExisting
}

type latencyStats struct {
	avgLatency   time.Duration
	p50Latency   time.Duration
	p90Latency   time.Duration
	p99Latency   time.Duration
	stdDeviation time.Duration
	numRequests  int
}

func getLatencyStats(actionLatencies map[string][]time.Duration) map[string]*latencyStats {
	results := make(map[string]*latencyStats)

	for action, latencies := range actionLatencies {
		sort.Slice(latencies, func(i, j int) bool {
			return latencies[i] < latencies[j]
		})

		results[action] = &latencyStats{
			// TODO: Replace with a stats library, eg https://pkg.go.dev/github.com/montanaflynn/stats
			avgLatency:   average(latencies),
			p50Latency:   percentile(latencies, 50),
			p90Latency:   percentile(latencies, 90),
			p99Latency:   percentile(latencies, 99),
			stdDeviation: standardDeviation(latencies),
			numRequests:  len(latencies),
		}
	}

	return results
}

func average(observations []time.Duration) time.Duration {
	var sumNano int64 = 0
	for _, observation := range observations {
		sumNano += observation.Nanoseconds()
	}

	avgNano := sumNano / int64(len(observations))
	return time.Duration(avgNano)
}

func percentile(observations []time.Duration, p float64) time.Duration {
	N := float64(len(observations))
	n := (N-1)*p/100 + 1

	if n == 1.0 {
		return observations[0]
	} else if n == N {
		return observations[int(N)-1]
	}

	k := int(n)
	d := n - float64(k)
	interpolatedValue := float64(observations[k-1]) + d*(float64(observations[k])-float64(observations[k-1]))
	return time.Duration(int64(math.Round(interpolatedValue)))
}

func standardDeviation(observations []time.Duration) time.Duration {
	var sum, mean, sd float64
	numObservations := len(observations)
	for i := 0; i < numObservations; i++ {
		sum += float64(observations[i])
	}

	mean = sum / float64(numObservations)
	for j := 0; j < numObservations; j++ {
		sd += math.Pow(float64(observations[j])-mean, 2)
	}

	sd = math.Sqrt(sd / float64(numObservations))
	return time.Duration(sd)
}

func printResults(results *benchmarkResults) {
	fmt.Printf("Runtime (sec): %.3f\n", results.duration.Seconds())
	fmt.Printf("Iterations: %d\n", results.iterationsPerTask)
	fmt.Printf("TPS: %d\n", int(results.tps))

	var totalRequests int
	for action, latencyStat := range results.latencyStats {
		fmt.Printf("===> %s <===\n", action)
		fmt.Printf("avg. latency (ms): %.3f\n", latencyStat.avgLatency.Seconds()*1000)
		fmt.Printf("std dev (ms): %.3f\n", latencyStat.stdDeviation.Seconds()*1000)
		fmt.Printf("p50 latency (ms): %.3f\n", latencyStat.p50Latency.Seconds()*1000)
		fmt.Printf("p90 latency (ms): %.3f\n", latencyStat.p90Latency.Seconds()*1000)
		fmt.Printf("p99 latency (ms): %.3f\n", latencyStat.p99Latency.Seconds()*1000)
		fmt.Printf("Number of requests: %d\n", latencyStat.numRequests)
		totalRequests += latencyStat.numRequests
	}

	fmt.Printf("Total requests: %d\n", totalRequests)
}

func addJsonResults(config *benchmarkConfig, results *benchmarkResults) {
	jsonResult := make(map[string]interface{})

	jsonResult["client"] = config.clientName
	jsonResult["is_cluster"] = config.connectionSettings.clusterModeEnabled
	jsonResult["num_of_tasks"] = config.numConcurrentTasks
	jsonResult["data_size"] = config.dataSize
	jsonResult["client_count"] = config.clientCount
	jsonResult["tps"] = results.tps

	for key, value := range results.latencyStats {
		jsonResult[key+"_p50_latency"] = value.p50Latency.Seconds() * 1000
		jsonResult[key+"_p90_latency"] = value.p90Latency.Seconds() * 1000
		jsonResult[key+"_p99_latency"] = value.p99Latency.Seconds() * 1000
		jsonResult[key+"_average_latency"] = value.avgLatency.Seconds() * 1000
		jsonResult[key+"_std_dev"] = value.stdDeviation.Seconds() * 1000
	}

	jsonResults = append(jsonResults, jsonResult)
}
