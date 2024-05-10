// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import "strconv"

// SetOptions represents optional arguments for the [api.StringCommands.SetWithOptions] command.
//
// See [valkey.io]
//
// [valkey.io]: https://valkey.io/commands/set/
type SetOptions struct {
	// If ConditionalSet is not set the value will be set regardless of prior value existence. If value isn't set because of
	// the condition, [api.StringCommands.SetWithOptions] will return a zero-value string ("").
	ConditionalSet ConditionalSet
	// Set command to return the old value stored at the given key, or a zero-value string ("") if the key did not exist. An
	// error is returned and [api.StringCommands.SetWithOptions] is aborted if the value stored at key is not a string.
	// Equivalent to GET in the valkey API.
	ReturnOldValue bool
	// If not set, no expiry time will be set for the value.
	Expiry *Expiry
}

func (opts *SetOptions) toArgs() []string {
	args := []string{}
	if opts.ConditionalSet != "" {
		args = append(args, string(opts.ConditionalSet))
	}

	if opts.ReturnOldValue {
		args = append(args, returnOldValue)
	}

	if opts.Expiry != nil {
		args = append(args, string(opts.Expiry.Type))
		if opts.Expiry.Type != KeepExisting {
			args = append(args, strconv.FormatUint(opts.Expiry.Count, 10))
		}
	}

	return args
}

const returnOldValue = "GET"

// A ConditionalSet defines whether a new value should be set or not.
type ConditionalSet string

const (
	// OnlyIfExists only sets the key if it already exists. Equivalent to "XX" in the valkey API.
	OnlyIfExists ConditionalSet = "XX"
	// OnlyIfDoesNotExist only sets the key if it does not already exist. Equivalent to "NX" in the valkey API.
	OnlyIfDoesNotExist ConditionalSet = "NX"
)

// Expiry is used to configure the lifetime of a value.
type Expiry struct {
	Type  ExpiryType
	Count uint64
}

// An ExpiryType is used to configure the type of expiration for a value.
type ExpiryType string

const (
	KeepExisting     ExpiryType = "KEEPTTL" // keep the existing expiration of the value
	Seconds          ExpiryType = "EX"      // expire the value after [api.Expiry.Count] seconds
	Milliseconds     ExpiryType = "PX"      // expire the value after [api.Expiry.Count] milliseconds
	UnixSeconds      ExpiryType = "EXAT"    // expire the value after the Unix time specified by [api.Expiry.Count], in seconds
	UnixMilliseconds ExpiryType = "PXAT"    // expire the value after the Unix time specified by [api.Expiry.Count], in milliseconds
)
