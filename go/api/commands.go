// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// StringCommands defines an interface for the "String Commands" group of commands for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/?group=string
type StringCommands interface {
	// Set the given key with the given value. The return value is a response from Valkey containing the string "OK".
	//
	// See [valkey.io] for details.
	//
	// For example:
	//
	//	result, err := client.Set("key", "value")
	//
	// [valkey.io]: https://valkey.io/commands/set/
	Set(key string, value string) (string, error)

	// SetWithOptions sets the given key with the given value using the given options. The return value is dependent on the
	// passed options. If the value is successfully set, "OK" is returned. If value isn't set because of [OnlyIfExists] or
	// [OnlyIfDoesNotExist] conditions, an empty string is returned (""). If [SetOptions#ReturnOldValue] is set, the old
	// value is returned.
	//
	// See [valkey.io] for details.
	//
	// For example:
	//
	//  result, err := client.SetWithOptions("key", "value", &api.SetOptions{
	//      ConditionalSet: api.OnlyIfExists,
	//      Expiry: &api.Expiry{
	//          Type: api.Seconds,
	//          Count: uint64(5),
	//      },
	//  })
	//
	// [valkey.io]: https://valkey.io/commands/set/
	SetWithOptions(key string, value string, options *SetOptions) (string, error)

	// Get string value associated with the given key, or an empty string is returned ("") if no such value exists
	//
	// See [valkey.io] for details.
	//
	// For example:
	//
	//	result, err := client.Get("key")
	//
	// [valkey.io]: https://valkey.io/commands/get/
	Get(key string) (string, error)
}
