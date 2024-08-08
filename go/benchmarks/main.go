// Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

package main

import (
	"errors"
	"flag"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
)

type options struct {
	resultsFile        string
	dataSize           string
	concurrentTasks    string
	clients            string
	host               string
	port               int
	clientCount        string
	tls                bool
	clusterModeEnabled bool
	minimal            bool
}

type runConfiguration struct {
	resultsFile        *os.File
	dataSize           []int
	concurrentTasks    []int
	clientNames        []string
	host               string
	port               int
	clientCount        []int
	tls                bool
	clusterModeEnabled bool
	minimal            bool
}

const (
	goRedis = "go-redis"
	glide   = "glide"
	all     = "all"
)

func main() {
	opts := parseArguments()

	runConfig, err := verifyOptions(opts)
	if err != nil {
		log.Fatal("Error verifying options:", err)
		return
	}

	if runConfig.resultsFile != os.Stdout {
		defer closeFile(runConfig.resultsFile)
	}

	err = runBenchmarks(runConfig)
	if err != nil {
		log.Fatal("Error running benchmarking:", err)
	}
}

func closeFile(file *os.File) {
	err := file.Close()
	if err != nil {
		log.Fatal("Error closing file:", err)
	}
}

func parseArguments() *options {
	resultsFile := flag.String("resultsFile", "results/go-results.json", "Result filepath")
	dataSize := flag.String("dataSize", "[100]", "Data block size")
	concurrentTasks := flag.String("concurrentTasks", "[1 10 100 1000]", "Number of concurrent tasks")
	clientNames := flag.String("clients", "all", "One of: all|go-redis|glide")
	host := flag.String("host", "localhost", "Hostname")
	port := flag.Int("port", 6379, "Port number")
	clientCount := flag.String("clientCount", "[1]", "Number of clients to run")
	tls := flag.Bool("tls", false, "Use TLS")
	clusterModeEnabled := flag.Bool("clusterModeEnabled", false, "Is cluster mode enabled")
	minimal := flag.Bool("minimal", false, "Run benchmark in minimal mode")

	flag.Parse()

	return &options{
		resultsFile:        *resultsFile,
		dataSize:           *dataSize,
		concurrentTasks:    *concurrentTasks,
		clients:            *clientNames,
		host:               *host,
		port:               *port,
		clientCount:        *clientCount,
		tls:                *tls,
		clusterModeEnabled: *clusterModeEnabled,
		minimal:            *minimal,
	}
}

func verifyOptions(opts *options) (*runConfiguration, error) {
	var runConfig runConfiguration
	var err error

	if opts.resultsFile == "" {
		runConfig.resultsFile = os.Stdout
	} else if _, err = os.Stat(opts.resultsFile); err == nil {
		// File exists
		runConfig.resultsFile, err = os.OpenFile(opts.resultsFile, os.O_RDWR, os.ModePerm)
		if err != nil {
			return nil, err
		}
	} else if errors.Is(err, os.ErrNotExist) {
		// File does not exist
		err = os.MkdirAll(filepath.Dir(opts.resultsFile), os.ModePerm)
		if err != nil {
			return nil, err
		}

		runConfig.resultsFile, err = os.Create(opts.resultsFile)
		if err != nil {
			return nil, err
		}
	} else {
		// Some other error occurred
		return nil, err
	}

	runConfig.concurrentTasks, err = parseOptionsIntList(opts.concurrentTasks)
	if err != nil {
		return nil, fmt.Errorf("invalid concurrentTasks option: %v", err)
	}

	runConfig.dataSize, err = parseOptionsIntList(opts.dataSize)
	if err != nil {
		return nil, fmt.Errorf("invalid dataSize option: %v", err)
	}

	runConfig.clientCount, err = parseOptionsIntList(opts.clientCount)
	if err != nil {
		return nil, fmt.Errorf("invalid clientCount option: %v", err)
	}

	switch {
	case strings.EqualFold(opts.clients, goRedis):
		runConfig.clientNames = append(runConfig.clientNames, goRedis)

	case strings.EqualFold(opts.clients, glide):
		runConfig.clientNames = append(runConfig.clientNames, glide)

	case strings.EqualFold(opts.clients, all):
		runConfig.clientNames = append(runConfig.clientNames, goRedis, glide)
	default:
		return nil, fmt.Errorf("invalid clients option, should be one of: all|go-redis|glide")
	}

	runConfig.host = opts.host
	runConfig.port = opts.port
	runConfig.tls = opts.tls
	runConfig.clusterModeEnabled = opts.clusterModeEnabled
	runConfig.minimal = opts.minimal

	return &runConfig, nil
}

func parseOptionsIntList(listAsString string) ([]int, error) {
	listAsString = strings.Trim(strings.TrimSpace(listAsString), "[]")
	if len(listAsString) == 0 {
		return nil, fmt.Errorf("option is empty or contains only brackets")
	}

	matched, err := regexp.MatchString("^\\d+(\\s+\\d+)*$", listAsString)
	if err != nil {
		return nil, err
	}

	if !matched {
		return nil, fmt.Errorf("wrong format for option")
	}

	stringList := strings.Split(listAsString, " ")
	var intList []int
	for _, intString := range stringList {
		num, err := strconv.Atoi(strings.TrimSpace(intString))
		if err != nil {
			return nil, fmt.Errorf("wrong number format for option: %s", intString)
		}

		intList = append(intList, num)
	}

	return intList, nil
}
