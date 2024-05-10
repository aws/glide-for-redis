// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
import "C"

import (
	"unsafe"
)

func convertCharArrayToString(arr *C.char, length C.long) string {
	if arr == nil {
		return ""
	}
	byteSlice := C.GoBytes(unsafe.Pointer(arr), C.int(int64(length)))
	// Create Go string from byte slice (preserving null characters)
	return string(byteSlice)
}

func handleStringResponse(response *C.struct_CommandResponse) string {
	defer C.free_command_response(response)
	return convertCharArrayToString(response.string_value, response.string_value_len)
}

func handleStringOrNullResponse(response *C.struct_CommandResponse) string {
	if response == nil {
		return ""
	}
	return handleStringResponse(response)
}
