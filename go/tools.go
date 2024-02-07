//go:build tools
// +build tools

package main

import (
	_ "github.com/DarkDrim/go-test-report"
	_ "google.golang.org/protobuf/cmd/protoc-gen-go"
	_ "honnef.co/go/tools/cmd/staticcheck"
)
