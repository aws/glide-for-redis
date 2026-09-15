// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use glide_core::request_type::RequestType;
use glide_core::connection_request::{ConnectionRequest, NodeAddress, TlsMode};
use miri_tests::{
    ClientType, ConnectionResponse, PushKind, close_client, create_client, create_client_from_uri,
    free_connection_response,
};
use miri_tests::{Level, LogResult, free_log_result, glide_log, init};
use miri_tests::{
    create_batch_otel_span, create_batch_otel_span_with_parent, create_named_otel_span,
    create_named_otel_span_with_trace_context, create_otel_span, create_otel_span_with_parent,
    drop_otel_span,
};
use protobuf::Message;
use std::ffi::{CStr, CString, c_char};
use std::ptr;

fn create_connection_request(port: u16) -> Vec<u8> {
    let host = "localhost";
    let mut request = ConnectionRequest::new();
    request.tls_mode = TlsMode::NoTls.into();
    let mut address_info = NodeAddress::new();
    address_info.host = host.into();
    address_info.port = port as u32;
    request.addresses.push(address_info);
    request.write_to_bytes().expect("Failed to serialize")
}

unsafe extern "C-unwind" fn pubsub_callback(
    _client_ptr: usize,
    _kind: PushKind,
    _message: *const u8,
    _message_len: i64,
    _channel: *const u8,
    _channel_len: i64,
    _pattern: *const u8,
    _pattern_len: i64,
) {
}

/// No-op address resolver that returns port 0 to signal "use original address".
unsafe extern "C-unwind" fn noop_address_resolver(
    _client_id: usize,
    _host: *const u8,
    _host_len: usize,
    _port: u16,
    _resolved_host_buf: *mut u8,
    _resolved_host_buf_len: usize,
    _resolved_host_len: *mut usize,
) -> u16 {
    0
}

fn get_logger_error_message(log_result: &LogResult) -> Option<String> {
    if log_result.log_error.is_null() {
        None
    } else {
        unsafe {
            CStr::from_ptr(log_result.log_error)
                .to_str()
                .ok()
                .map(|s| s.to_string())
        }
    }
}

/// Create_client

#[test]
fn create_client_test() {
    let connection_request_bytes = create_connection_request(6378);
    let connection_request_len = connection_request_bytes.len();
    let connection_request_ptr = connection_request_bytes.as_ptr();
    let client_type = Box::new(ClientType::SyncClient);
    let client_type_ptr = Box::into_raw(client_type);

    unsafe {
        let connection_response_ptr = create_client(
            connection_request_ptr,
            connection_request_len,
            client_type_ptr,
            pubsub_callback,
            noop_address_resolver,
            0usize,
        );
        let conn_ptr = (*connection_response_ptr).conn_ptr;
        close_client(conn_ptr);
        free_connection_response(connection_response_ptr as *mut ConnectionResponse);
        let _ = Box::from_raw(client_type_ptr);
    }
}

#[test]
fn create_client_from_uri_test() {
    let uri = CString::new("redis://localhost:6378").unwrap();
    let client_type = Box::new(ClientType::SyncClient);
    let client_type_ptr = Box::into_raw(client_type);

    unsafe {
        let connection_response_ptr = create_client_from_uri(
            uri.as_ptr(),
            ptr::null(),
            client_type_ptr,
            pubsub_callback,
        );
        let conn_ptr = (*connection_response_ptr).conn_ptr;
        close_client(conn_ptr);
        free_connection_response(connection_response_ptr as *mut ConnectionResponse);
        let _ = Box::from_raw(client_type_ptr);
    }
}

#[test]
fn test_create_otel_span_miri() {
    // Test basic span creation
    let span_ptr = create_otel_span(RequestType::Get);
    assert_ne!(
        span_ptr, 0,
        "create_otel_span should return non-zero pointer"
    );

    // Test with different request types
    let span_ptr2 = create_otel_span(RequestType::Set);
    assert_ne!(
        span_ptr2, 0,
        "create_otel_span should work with Set command"
    );

    // Clean up
    unsafe {
        drop_otel_span(span_ptr);
        drop_otel_span(span_ptr2);
    }
}

#[test]
fn test_create_batch_otel_span_miri() {
    // Test batch span creation
    let batch_span_ptr = create_batch_otel_span();
    assert_ne!(
        batch_span_ptr, 0,
        "create_batch_otel_span should return non-zero pointer"
    );

    // Clean up
    unsafe {
        drop_otel_span(batch_span_ptr);
    }
}

#[test]
fn test_create_named_otel_span_miri() {
    // Test named span creation with valid name
    let span_name = CString::new("test_operation").expect("CString::new failed");
    let span_ptr = unsafe { create_named_otel_span(span_name.as_ptr()) };
    assert_ne!(
        span_ptr, 0,
        "create_named_otel_span should return non-zero pointer"
    );

    // Test with empty name
    let empty_name = CString::new("").expect("CString::new failed");
    let empty_span_ptr = unsafe { create_named_otel_span(empty_name.as_ptr()) };
    assert_ne!(
        empty_span_ptr, 0,
        "create_named_otel_span should work with empty name"
    );

    // Test with null pointer (should return 0)
    let null_span_ptr = unsafe { create_named_otel_span(std::ptr::null()) };
    assert_eq!(
        null_span_ptr, 0,
        "create_named_otel_span should return 0 for null pointer"
    );

    // Clean up
    unsafe {
        drop_otel_span(span_ptr);
        drop_otel_span(empty_span_ptr);
    }
}

#[test]
fn test_create_named_otel_span_with_trace_context_miri() {
    let span_name = CString::new("Get").expect("CString::new failed");
    let trace_id = CString::new("0af7651916cd43dd8448eb211c80319c").expect("CString::new failed");
    let span_id = CString::new("b7ad6b7169203331").expect("CString::new failed");
    let trace_state = CString::new("vendor=value").expect("CString::new failed");

    // All string pointers non-null
    let span_ptr = unsafe {
        create_named_otel_span_with_trace_context(
            span_name.as_ptr(),
            trace_id.as_ptr(),
            span_id.as_ptr(),
            1,
            trace_state.as_ptr(),
        )
    };
    assert_ne!(span_ptr, 0, "valid remote context should create a span");

    // Null trace_state is the common case (empty W3C tracestate)
    let no_state_ptr = unsafe {
        create_named_otel_span_with_trace_context(
            span_name.as_ptr(),
            trace_id.as_ptr(),
            span_id.as_ptr(),
            0,
            std::ptr::null(),
        )
    };
    assert_ne!(no_state_ptr, 0, "null trace_state should create a span");

    // Null trace_id / span_id must fall back rather than dereference null
    let null_trace_id_ptr = unsafe {
        create_named_otel_span_with_trace_context(
            span_name.as_ptr(),
            std::ptr::null(),
            span_id.as_ptr(),
            1,
            std::ptr::null(),
        )
    };
    assert_ne!(null_trace_id_ptr, 0, "null trace_id should fall back");

    let null_span_id_ptr = unsafe {
        create_named_otel_span_with_trace_context(
            span_name.as_ptr(),
            trace_id.as_ptr(),
            std::ptr::null(),
            1,
            std::ptr::null(),
        )
    };
    assert_ne!(null_span_id_ptr, 0, "null span_id should fall back");

    // Null span name is a caller bug and returns 0 without touching the trace context
    let null_name_ptr = unsafe {
        create_named_otel_span_with_trace_context(
            std::ptr::null(),
            trace_id.as_ptr(),
            span_id.as_ptr(),
            1,
            std::ptr::null(),
        )
    };
    assert_eq!(null_name_ptr, 0, "null span_name should return 0");

    unsafe {
        drop_otel_span(span_ptr);
        drop_otel_span(no_state_ptr);
        drop_otel_span(null_trace_id_ptr);
        drop_otel_span(null_span_id_ptr);
    }
}

#[test]
fn test_create_otel_span_with_parent_miri() {
    // Create parent span
    let parent_span_ptr = create_otel_span(RequestType::Set);
    assert_ne!(parent_span_ptr, 0, "Parent span creation should succeed");

    // Create child span with valid parent
    let child_span_ptr = unsafe { create_otel_span_with_parent(RequestType::Get, parent_span_ptr) };
    assert_ne!(child_span_ptr, 0, "Child span creation should succeed");

    // Test with null parent (should fallback to independent span)
    let child_null_parent = unsafe { create_otel_span_with_parent(RequestType::Del, 0) };
    assert_ne!(
        child_null_parent, 0,
        "Child span with null parent should fallback"
    );

    // Test with invalid parent pointer (should fallback to independent span)
    let child_invalid_parent =
        unsafe { create_otel_span_with_parent(RequestType::Exists, 0xDEADBEEF) };
    assert_ne!(
        child_invalid_parent, 0,
        "Child span with invalid parent should fallback"
    );

    // Clean up
    unsafe {
        drop_otel_span(parent_span_ptr);
        drop_otel_span(child_span_ptr);
        drop_otel_span(child_null_parent);
        drop_otel_span(child_invalid_parent);
    }
}

#[test]
fn test_create_batch_otel_span_with_parent_miri() {
    // Create parent span
    let parent_span_ptr = create_named_otel_span_safe("batch_parent");
    assert_ne!(parent_span_ptr, 0, "Parent span creation should succeed");

    // Create batch child span with valid parent
    let batch_child_ptr = unsafe { create_batch_otel_span_with_parent(parent_span_ptr) };
    assert_ne!(
        batch_child_ptr, 0,
        "Batch child span creation should succeed"
    );

    // Test with null parent (should fallback to independent batch span)
    let batch_null_parent = unsafe { create_batch_otel_span_with_parent(0) };
    assert_ne!(
        batch_null_parent, 0,
        "Batch span with null parent should fallback"
    );

    // Test with invalid parent pointer (should fallback to independent batch span)
    let batch_invalid_parent = unsafe { create_batch_otel_span_with_parent(0xDEADBEEF) };
    assert_ne!(
        batch_invalid_parent, 0,
        "Batch span with invalid parent should fallback"
    );

    // Clean up
    unsafe {
        drop_otel_span(parent_span_ptr);
        drop_otel_span(batch_child_ptr);
        drop_otel_span(batch_null_parent);
        drop_otel_span(batch_invalid_parent);
    }
}

#[test]
fn test_drop_otel_span_miri() {
    // Test dropping valid span
    let span_ptr = create_otel_span(RequestType::Get);
    assert_ne!(span_ptr, 0, "Span creation should succeed");

    unsafe {
        drop_otel_span(span_ptr); // Should not crash
    }

    // Test dropping null pointer (should not crash)
    unsafe {
        drop_otel_span(0);
    }

    // Test dropping invalid pointer (should not crash, but may log error)
    unsafe {
        drop_otel_span(0xDEADBEEF);
    }
}

#[test]
fn test_span_from_pointer_functionality_miri() {
    // This test verifies that the span_from_pointer function works correctly
    // in the context of parent-child span relationships

    // Create a parent span using create_named_otel_span
    let parent_name = CString::new("parent_span").expect("CString::new failed");
    let parent_ptr = unsafe { create_named_otel_span(parent_name.as_ptr()) };
    assert_ne!(parent_ptr, 0, "Parent span creation should succeed");

    // Create multiple child spans using the parent
    let child1_ptr = unsafe { create_otel_span_with_parent(RequestType::Get, parent_ptr) };
    let child2_ptr = unsafe { create_otel_span_with_parent(RequestType::Set, parent_ptr) };
    let batch_child_ptr = unsafe { create_batch_otel_span_with_parent(parent_ptr) };

    assert_ne!(child1_ptr, 0, "Child span 1 creation should succeed");
    assert_ne!(child2_ptr, 0, "Child span 2 creation should succeed");
    assert_ne!(
        batch_child_ptr, 0,
        "Batch child span creation should succeed"
    );

    // Verify all pointers are different (each span should be unique)
    assert_ne!(
        parent_ptr, child1_ptr,
        "Parent and child1 should have different pointers"
    );
    assert_ne!(
        parent_ptr, child2_ptr,
        "Parent and child2 should have different pointers"
    );
    assert_ne!(
        parent_ptr, batch_child_ptr,
        "Parent and batch child should have different pointers"
    );
    assert_ne!(
        child1_ptr, child2_ptr,
        "Child spans should have different pointers"
    );

    // Clean up all spans
    unsafe {
        drop_otel_span(parent_ptr);
        drop_otel_span(child1_ptr);
        drop_otel_span(child2_ptr);
        drop_otel_span(batch_child_ptr);
    }
}

// Helper function to safely create named spans for testing
fn create_named_otel_span_safe(name: &str) -> u64 {
    let span_name = CString::new(name).expect("CString::new failed");
    unsafe { create_named_otel_span(span_name.as_ptr()) }
}

/// Logger tests
#[test]
fn test_init_logger_with_valid_level() {
    unsafe {
        let level = Level::INFO;
        let log_result_ptr = init(&level, ptr::null());
        assert!(!log_result_ptr.is_null());

        let log_result = &*log_result_ptr;
        assert!(log_result.log_error.is_null());

        free_log_result(log_result_ptr);
    }
}

#[test]
fn test_init_logger_with_invalid_utf8_filename() {
    unsafe {
        let level = Level::INFO;
        let invalid_utf8 = vec![0xFF, 0xFE, 0xFD, 0x00];

        let log_result_ptr = init(&level, invalid_utf8.as_ptr() as *const c_char);
        assert!(!log_result_ptr.is_null());

        let log_result = &*log_result_ptr;
        assert!(!log_result.log_error.is_null());

        // Check that the error message contains the expected text
        let error_message = get_logger_error_message(log_result);
        assert!(error_message.is_some());
        let error_text = error_message.unwrap();
        assert!(error_text.contains("File name contains invalid UTF-8"));

        free_log_result(log_result_ptr);
    }
}

#[test]
fn test_log_with_valid_inputs() {
    unsafe {
        let level = Level::INFO;
        let init_result_ptr = init(&level, ptr::null());
        free_log_result(init_result_ptr);

        let identifier = CString::new("test_identifier").unwrap();
        let message = CString::new("This is a test log message").unwrap();

        let log_result_ptr = glide_log(Level::INFO, identifier.as_ptr(), message.as_ptr());
        assert!(!log_result_ptr.is_null());

        let log_result = &*log_result_ptr;
        assert!(log_result.log_error.is_null());

        free_log_result(log_result_ptr);
    }
}

#[test]
fn test_log_with_invalid_utf8_message() {
    unsafe {
        let level = Level::INFO;
        let init_result_ptr = init(&level, ptr::null());
        free_log_result(init_result_ptr);

        let identifier = CString::new("valid_identifier").unwrap();
        let invalid_utf8 = vec![0xFF, 0xFE, 0xFD, 0x00];

        let log_result_ptr = glide_log(
            Level::ERROR,
            identifier.as_ptr(),
            invalid_utf8.as_ptr() as *const c_char,
        );
        assert!(!log_result_ptr.is_null());

        let log_result = &*log_result_ptr;
        assert!(!log_result.log_error.is_null());

        let error_message = get_logger_error_message(log_result);
        assert!(error_message.is_some());
        let error_text = error_message.unwrap();
        assert!(error_text.contains("Log message contains invalid UTF-8"));

        free_log_result(log_result_ptr);
    }
}
