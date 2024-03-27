/*
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */
#![deny(unsafe_op_in_unsafe_fn)]

use glide_core::client::Client as GlideClient;
use glide_core::connection_request;
use glide_core::errors;
use glide_core::errors::RequestErrorType;
use glide_core::ConnectionRequest;
use protobuf::Message;
use std::{
    ffi::{c_void, CString},
    os::raw::c_char,
};
use tokio::runtime::Builder;
use tokio::runtime::Runtime;

/// Success callback that is called when a Redis command succeeds.
///
/// `index_ptr` is a baton-pass back to the caller language to uniquely identify the promise.
/// `message` is the value returned by the Redis command. The lifetime of `message` is until the callback returns control back to the caller. The callee cannot use the data after the callback returns.
// TODO: Change message type when implementing command logic
// TODO: Consider using a single response callback instead of success and failure callbacks
pub type SuccessCallback = unsafe extern "C" fn(index_ptr: usize, message: *const c_char) -> ();

/// Failure callback that is called when a Redis command fails.
///
/// `index_ptr` is a baton-pass back to the caller language to uniquely identify the promise.
/// `error_message` is the error message returned by Redis for the failed command. The lifetime of `error_message` is until the callback returns control back to the caller. The callee cannot use the data after the callback returns.
/// `error_type` is the type of error returned by glide-core, depending on the `RedisError` returned.
pub type FailureCallback = unsafe extern "C" fn(
    index_ptr: usize,
    error_message: *const c_char,
    error_type: RequestErrorType,
) -> ();

/// The connection response.
///
/// It contains either a connection or an error. It is represented as a struct instead of a union for ease of use in the wrapper language.
///
/// The struct is freed using `free_connection_response` to avoid memory leaks. This is achieved by having the external caller invoke this function.
#[repr(C)]
pub struct ConnectionResponse {
    conn_ptr: *const c_void,
    connection_error_message: *const c_char,
}

/// The glide client.
// TODO: Remove allow(dead_code) once connection logic is implemented
#[allow(dead_code)]
pub struct Client {
    client: GlideClient,
    success_callback: SuccessCallback,
    failure_callback: FailureCallback,
    runtime: Runtime,
}

fn create_client_internal(
    connection_request_bytes: &[u8],
    success_callback: SuccessCallback,
    failure_callback: FailureCallback,
) -> Result<Client, String> {
    let request = connection_request::ConnectionRequest::parse_from_bytes(connection_request_bytes)
        .map_err(|err| err.to_string())?;
    // TODO: optimize this (e.g. by pinning each go thread to a rust thread)
    let runtime = Builder::new_multi_thread()
        .enable_all()
        .worker_threads(1)
        .thread_name("GLIDE for Redis Go thread")
        .build()
        .map_err(|err| {
            let redis_error = err.into();
            errors::error_message(&redis_error)
        })?;
    let _runtime_handle = runtime.enter();
    let client = runtime
        .block_on(GlideClient::new(ConnectionRequest::from(request)))
        .map_err(|err| err.to_string())?;
    Ok(Client {
        client,
        success_callback,
        failure_callback,
        runtime,
    })
}

/// Creates a new client with the given configuration. The success callback needs to copy the given string synchronously, since it will be dropped by Rust once the callback returns. All callbacks should be offloaded to separate threads in order not to exhaust the client's thread pool.
///
/// The returned `ConnectionResponse` should be manually freed by calling `free_connection_response`, otherwise a memory leak will occur. It should be freed whether or not an error occurs.
///
/// `connection_request_bytes` is an array of bytes that will be parsed into a Protobuf `ConnectionRequest` object.
/// `connection_request_len` is the number of bytes in `connection_request_bytes`.
/// `success_callback` is the callback that will be called in the case that the Redis command succeeds.
/// `failure_callback` is the callback that will be called in the case that the Redis command fails.
///
/// # Safety
///
/// * `connection_request_bytes` must point to `connection_request_len` consecutive properly initialized bytes. It must be a well-formed Protobuf `ConnectionRequest` object. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `connection_request_len` must not be greater than the length of the connection request bytes array. It must also not be greater than the max value of a signed pointer-sized integer.
/// * The `conn_ptr` pointer in the returned `ConnectionResponse` must live until it is passed into `close_client`.
/// * The `connection_error_message` pointer in the returned `ConnectionResponse` must live until the returned `ConnectionResponse` pointer is passed to `free_connection_response`.
/// * Both the `success_callback` and `failure_callback` function pointers need to live until the client is closed via `close_client` since they are used when issuing Redis commands.
// TODO: Consider making this async
#[no_mangle]
pub unsafe extern "C" fn create_client(
    connection_request_bytes: *const u8,
    connection_request_len: usize,
    success_callback: SuccessCallback,
    failure_callback: FailureCallback,
) -> *const ConnectionResponse {
    let request_bytes =
        unsafe { std::slice::from_raw_parts(connection_request_bytes, connection_request_len) };
    let response = match create_client_internal(request_bytes, success_callback, failure_callback) {
        Err(err) => ConnectionResponse {
            conn_ptr: std::ptr::null(),
            connection_error_message: CString::into_raw(CString::new(err).unwrap()),
        },
        Ok(client) => ConnectionResponse {
            conn_ptr: Box::into_raw(Box::new(client)) as *const c_void,
            connection_error_message: std::ptr::null(),
        },
    };
    Box::into_raw(Box::new(response))
}

/// Closes the given client, deallocating it from the heap.
///
/// `client_ptr` is a pointer to the client returned in the `ConnectionResponse` from `create_client`.
///
/// This function will do nothing when closing an already closed client (a null client pointer).
///
/// # Safety
///
/// * `client_ptr` must be obtained from the `ConnectionResponse` returned from `create_client`.
/// * `client_ptr` must be valid until `close_client` is called.
// TODO: Ensure safety when command has not completed yet
#[no_mangle]
pub unsafe extern "C" fn close_client(client_ptr: *const c_void) {
    if client_ptr.is_null() {
        return;
    }
    drop(unsafe { Box::from_raw(client_ptr as *mut Client) });
}

/// Deallocates a `ConnectionResponse`.
///
/// This function also frees the contained error. If the contained error is a null pointer, the function returns and nothing happens, so a double free cannot happen.
///
/// This function will do nothing when freeing a null `ConnectionResponse` pointer.
///
/// # Safety
///
/// * `connection_response_ptr` must be obtained from the `ConnectionResponse` returned from `create_client`.
/// * `connection_response_ptr` must be valid until `free_connection_response` is called.
/// * The contained `connection_error_message` must be obtained from the `ConnectionResponse` returned from `create_client`.
/// * The contained `connection_error_message` must be valid until `free_connection_response` is called and it must outlive the `ConnectionResponse` that contains it.
#[no_mangle]
pub unsafe extern "C" fn free_connection_response(
    connection_response_ptr: *mut ConnectionResponse,
) {
    if connection_response_ptr.is_null() {
        return;
    }
    let connection_response = unsafe { Box::from_raw(connection_response_ptr) };
    let connection_error_message = connection_response.connection_error_message;
    drop(connection_response);
    if !connection_error_message.is_null() {
        drop(unsafe { CString::from_raw(connection_error_message as *mut c_char) });
    }
}
