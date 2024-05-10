// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package main

import (
	"context"
	"crypto/tls"
	"errors"
	"fmt"

	"github.com/redis/go-redis/v9"
)

type goRedisBenchmarkClient struct {
	client redis.Cmdable
}

func (goRedisClient *goRedisBenchmarkClient) connect(connectionSettings *connectionSettings) error {
	if connectionSettings.clusterModeEnabled {
		clusterOptions := &redis.ClusterOptions{
			Addrs: []string{fmt.Sprintf("%s:%d", connectionSettings.host, connectionSettings.port)},
		}

		if connectionSettings.useTLS {
			clusterOptions.TLSConfig = &tls.Config{MinVersion: tls.VersionTLS12}
		}

		goRedisClient.client = redis.NewClusterClient(clusterOptions)
	} else {
		options := &redis.Options{
			Addr: fmt.Sprintf("%s:%d", connectionSettings.host, connectionSettings.port),
			DB:   0,
		}

		if connectionSettings.useTLS {
			options.TLSConfig = &tls.Config{MinVersion: tls.VersionTLS12}
		}

		goRedisClient.client = redis.NewClient(options)
	}

	return goRedisClient.client.Ping(context.Background()).Err()
}

func (goRedisClient *goRedisBenchmarkClient) set(key string, value string) (string, error) {
	return goRedisClient.client.Set(context.Background(), key, value, 0).Result()
}

func (goRedisClient *goRedisBenchmarkClient) get(key string) (string, error) {
	value, err := goRedisClient.client.Get(context.Background(), key).Result()
	if err != nil && !errors.Is(err, redis.Nil) {
		return "", err
	}

	return value, nil
}

func (goRedisClient *goRedisBenchmarkClient) close() error {
	switch c := goRedisClient.client.(type) {
	case *redis.Client:
		return c.Close()
	case *redis.ClusterClient:
		return c.Close()
	default:
		return fmt.Errorf("unsupported client type")
	}
}

func (goRedisClient *goRedisBenchmarkClient) getName() string {
	return "go-redis"
}
