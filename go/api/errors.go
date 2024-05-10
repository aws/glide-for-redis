// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
import "C"

// ConnectionError is a client error that occurs when there is an error while connecting or when a connection
// disconnects.
type ConnectionError struct {
	msg string
}

func (e *ConnectionError) Error() string { return e.msg }

// RequestError is a client error that occurs when an error is reported during a request.
type RequestError struct {
	msg string
}

func (e *RequestError) Error() string { return e.msg }

// ExecAbortError is a client error that occurs when a transaction is aborted.
type ExecAbortError struct {
	msg string
}

func (e *ExecAbortError) Error() string { return e.msg }

// TimeoutError is a client error that occurs when a request times out.
type TimeoutError struct {
	msg string
}

func (e *TimeoutError) Error() string { return e.msg }

// DisconnectError is a client error that indicates a connection problem between Glide and server.
type DisconnectError struct {
	msg string
}

func (e *DisconnectError) Error() string { return e.msg }

// ClosingError is a client error that indicates that the client has closed and is no longer usable.
type ClosingError struct {
	msg string
}

func (e *ClosingError) Error() string { return e.msg }

func goError(cErrorType C.RequestErrorType, cErrorMessage *C.char) error {
	defer C.free_error_message(cErrorMessage)
	msg := C.GoString(cErrorMessage)
	switch cErrorType {
	case C.ExecAbort:
		return &ExecAbortError{msg}
	case C.Timeout:
		return &TimeoutError{msg}
	case C.Disconnect:
		return &DisconnectError{msg}
	default:
		return &RequestError{msg}
	}
}
