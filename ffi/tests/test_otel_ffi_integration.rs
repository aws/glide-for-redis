use glide_core::GlideSpan;
use glide_core::request_type::RequestType;
use glide_ffi::{
    create_batch_otel_span, create_batch_otel_span_with_parent,
    create_batch_otel_span_with_trace_context, create_named_otel_span,
    create_named_otel_span_with_trace_context, create_otel_span, create_otel_span_with_parent,
    create_otel_span_with_trace_context, drop_otel_span,
};
use opentelemetry_sdk::trace::{InMemorySpanExporter, SdkTracerProvider};
use std::ffi::CString;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Arc, OnceLock};
use std::thread;
use std::time::Duration;

struct TraceContextTestTelemetry {
    exporter: InMemorySpanExporter,
    _provider: SdkTracerProvider,
}

static TRACE_CONTEXT_TEST_TELEMETRY: OnceLock<TraceContextTestTelemetry> = OnceLock::new();

fn trace_context_test_exporter() -> &'static InMemorySpanExporter {
    &TRACE_CONTEXT_TEST_TELEMETRY
        .get_or_init(|| {
            let exporter = InMemorySpanExporter::default();
            let provider = SdkTracerProvider::builder()
                .with_simple_exporter(exporter.clone())
                .build();
            opentelemetry::global::set_tracer_provider(provider.clone());
            TraceContextTestTelemetry {
                exporter,
                _provider: provider,
            }
        })
        .exporter
}

fn assert_exported_remote_parent(expected_trace_id: &str, expected_parent_span_id: &str) {
    let spans = trace_context_test_exporter()
        .get_finished_spans()
        .expect("test exporter should return its finished spans");
    let observed_contexts = spans
        .iter()
        .map(|span| {
            (
                span.span_context.trace_id().to_string(),
                span.parent_span_id.to_string(),
            )
        })
        .collect::<Vec<_>>();

    assert!(
        observed_contexts.iter().any(|(trace_id, parent_span_id)| {
            trace_id == expected_trace_id && parent_span_id == expected_parent_span_id
        }),
        "expected exported child with trace ID {expected_trace_id} and parent span ID \
         {expected_parent_span_id}, observed {observed_contexts:?}"
    );
}

/// Take a co-owning [`Arc<GlideSpan>`] for a span pointer returned by one of the
/// `create_*_otel_span` FFI functions, WITHOUT consuming the reference still held by
/// the raw pointer.
///
/// The FFI create functions leave the span's strong count at 1 (the count owned by the
/// raw pointer handed back to the caller). Cloning that ownership here lets a test observe
/// the strong count across a subsequent `drop_otel_span` call: a correct drop releases the
/// FFI-held reference, so the count returned to this owner must fall back to 1. If
/// `drop_otel_span` ever leaked (e.g. failed to call `Arc::from_raw`), the count would stay
/// at 2 and the assertion would fire.
///
/// # Safety
/// `span_ptr` must be a live span pointer returned by a `create_*_otel_span` FFI function
/// that has NOT yet been passed to `drop_otel_span`.
unsafe fn co_owner(span_ptr: u64) -> Arc<GlideSpan> {
    unsafe {
        // Bump the strong count so converting the raw pointer back to an Arc does not steal
        // the reference owned by the FFI-side raw pointer.
        Arc::increment_strong_count(span_ptr as *const GlideSpan);
        Arc::from_raw(span_ptr as *const GlideSpan)
    }
}

#[test]
fn test_create_otel_span_with_valid_inputs() {
    // Initialize logger to capture debug messages
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test creating spans with various valid request types
    let request_types = vec![
        RequestType::Get,
        RequestType::Set,
        RequestType::Del,
        RequestType::Exists,
        RequestType::Expire,
        RequestType::TTL,
        RequestType::HGet,
        RequestType::HSet,
        RequestType::HDel,
        RequestType::LPush,
        RequestType::RPush,
        RequestType::LPop,
        RequestType::RPop,
    ];

    let mut span_ptrs = Vec::new();

    for request_type in request_types {
        let span_ptr = create_otel_span(request_type);
        assert_ne!(
            span_ptr, 0,
            "create_otel_span should succeed for {request_type:?}",
        );

        // Verify pointer is properly aligned
        assert_eq!(span_ptr % 8, 0, "Span pointer should be 8-byte aligned");

        // Verify pointer is in reasonable range
        assert!(
            span_ptr >= 0x1000,
            "Span pointer should be above minimum valid address"
        );
        assert!(
            span_ptr <= 0x7FFF_FFFF_FFFF_FFF8,
            "Span pointer should be below maximum valid address"
        );

        span_ptrs.push(span_ptr);
    }

    // Clean up all spans
    for span_ptr in span_ptrs {
        unsafe {
            drop_otel_span(span_ptr);
        }
    }
}

#[test]
fn test_create_otel_span_with_trace_context_valid_inputs() {
    logger_core::init(Some(logger_core::Level::Debug), None);
    trace_context_test_exporter();

    let trace_id = CString::new("0af7651916cd43dd8448eb211c80319c").unwrap();
    let span_id = CString::new("b7ad6b7169203331").unwrap();
    let trace_state = CString::new("vendor=value").unwrap();

    let span_ptr = unsafe {
        create_otel_span_with_trace_context(
            RequestType::Get,
            trace_id.as_ptr(),
            span_id.as_ptr(),
            1,
            trace_state.as_ptr(),
        )
    };

    assert_ne!(span_ptr, 0, "valid remote context should create a span");
    assert_eq!(span_ptr % 8, 0, "span pointer should be 8-byte aligned");

    unsafe {
        drop_otel_span(span_ptr);
    }
    assert_exported_remote_parent("0af7651916cd43dd8448eb211c80319c", "b7ad6b7169203331");
}

#[test]
fn test_create_otel_span_with_trace_context_invalid_context_falls_back() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    let invalid_trace_id = CString::new("not-valid").unwrap();
    let invalid_span_id = CString::new("zzzzzzzzzzzzzzzz").unwrap();
    let valid_trace_id = CString::new("0af7651916cd43dd8448eb211c80319c").unwrap();
    let valid_span_id = CString::new("b7ad6b7169203331").unwrap();
    let invalid_trace_state = CString::new("bad,tracestate,entry").unwrap();

    let test_cases = [
        (
            "invalid trace ID",
            invalid_trace_id.as_ptr(),
            valid_span_id.as_ptr(),
            std::ptr::null(),
        ),
        (
            "invalid span ID",
            valid_trace_id.as_ptr(),
            invalid_span_id.as_ptr(),
            std::ptr::null(),
        ),
        (
            "invalid trace state",
            valid_trace_id.as_ptr(),
            valid_span_id.as_ptr(),
            invalid_trace_state.as_ptr(),
        ),
        (
            "null trace ID",
            std::ptr::null(),
            valid_span_id.as_ptr(),
            std::ptr::null(),
        ),
        (
            "null span ID",
            valid_trace_id.as_ptr(),
            std::ptr::null(),
            std::ptr::null(),
        ),
    ];

    for (name, trace_id, span_id, trace_state) in test_cases {
        let span_ptr = unsafe {
            create_otel_span_with_trace_context(RequestType::Set, trace_id, span_id, 1, trace_state)
        };

        assert_ne!(span_ptr, 0, "{name} should fall back to standalone span",);

        unsafe {
            drop_otel_span(span_ptr);
        }
    }
}

#[test]
fn test_create_batch_otel_span_with_trace_context() {
    logger_core::init(Some(logger_core::Level::Debug), None);
    trace_context_test_exporter();

    let trace_id = CString::new("0af7651916cd43dd8448eb211c80319d").unwrap();
    let span_id = CString::new("b7ad6b7169203332").unwrap();

    let span_ptr = unsafe {
        create_batch_otel_span_with_trace_context(
            trace_id.as_ptr(),
            span_id.as_ptr(),
            1,
            std::ptr::null(),
        )
    };

    assert_ne!(
        span_ptr, 0,
        "valid remote context should create a batch span"
    );
    assert_eq!(span_ptr % 8, 0, "span pointer should be 8-byte aligned");

    unsafe {
        drop_otel_span(span_ptr);
    }
    assert_exported_remote_parent("0af7651916cd43dd8448eb211c80319d", "b7ad6b7169203332");
}

#[test]
fn test_create_named_otel_span_with_trace_context_valid_inputs() {
    logger_core::init(Some(logger_core::Level::Debug), None);
    trace_context_test_exporter();

    let span_name = CString::new("Get").unwrap();
    let trace_id = CString::new("0af7651916cd43dd8448eb211c80319e").unwrap();
    let span_id = CString::new("b7ad6b7169203333").unwrap();
    let trace_state = CString::new("vendor=value").unwrap();

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
    assert_eq!(span_ptr % 8, 0, "span pointer should be 8-byte aligned");

    // A null trace_state is the common case (an empty W3C tracestate) and must also work.
    let no_state_ptr = unsafe {
        create_named_otel_span_with_trace_context(
            span_name.as_ptr(),
            trace_id.as_ptr(),
            span_id.as_ptr(),
            0,
            std::ptr::null(),
        )
    };

    assert_ne!(
        no_state_ptr, 0,
        "null trace_state should create a span with the default trace state"
    );

    unsafe {
        drop_otel_span(span_ptr);
        drop_otel_span(no_state_ptr);
    }
    assert_exported_remote_parent("0af7651916cd43dd8448eb211c80319e", "b7ad6b7169203333");
}

#[test]
fn test_create_named_otel_span_with_trace_context_invalid_context_falls_back() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    let span_name = CString::new("EVALSHA").unwrap();
    let invalid_trace_id = CString::new("not-valid").unwrap();
    let invalid_span_id = CString::new("zzzzzzzzzzzzzzzz").unwrap();
    let valid_trace_id = CString::new("0af7651916cd43dd8448eb211c80319c").unwrap();
    let valid_span_id = CString::new("b7ad6b7169203331").unwrap();
    let invalid_trace_state = CString::new("bad,tracestate,entry").unwrap();

    let test_cases = [
        (
            "invalid trace ID",
            invalid_trace_id.as_ptr(),
            valid_span_id.as_ptr(),
            std::ptr::null(),
        ),
        (
            "invalid span ID",
            valid_trace_id.as_ptr(),
            invalid_span_id.as_ptr(),
            std::ptr::null(),
        ),
        (
            "invalid trace state",
            valid_trace_id.as_ptr(),
            valid_span_id.as_ptr(),
            invalid_trace_state.as_ptr(),
        ),
        (
            "null trace ID",
            std::ptr::null(),
            valid_span_id.as_ptr(),
            std::ptr::null(),
        ),
        (
            "null span ID",
            valid_trace_id.as_ptr(),
            std::ptr::null(),
            std::ptr::null(),
        ),
    ];

    for (name, trace_id, span_id, trace_state) in test_cases {
        let span_ptr = unsafe {
            create_named_otel_span_with_trace_context(
                span_name.as_ptr(),
                trace_id,
                span_id,
                1,
                trace_state,
            )
        };

        assert_ne!(span_ptr, 0, "{name} should fall back to standalone span");

        unsafe {
            drop_otel_span(span_ptr);
        }
    }
}

#[test]
fn test_create_named_otel_span_with_trace_context_rejects_invalid_names() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    let trace_id = CString::new("0af7651916cd43dd8448eb211c80319c").unwrap();
    let span_id = CString::new("b7ad6b7169203331").unwrap();

    let create = |name_ptr: *const std::os::raw::c_char| unsafe {
        create_named_otel_span_with_trace_context(
            name_ptr,
            trace_id.as_ptr(),
            span_id.as_ptr(),
            1,
            std::ptr::null(),
        )
    };

    assert_eq!(create(std::ptr::null()), 0, "null name should return 0");

    let too_long = CString::new("a".repeat(257)).unwrap();
    assert_eq!(
        create(too_long.as_ptr()),
        0,
        "257 character name should return 0"
    );

    let control_chars = CString::new("bad\u{7}name").unwrap();
    assert_eq!(
        create(control_chars.as_ptr()),
        0,
        "name with control characters should return 0"
    );

    let max_length = CString::new("a".repeat(256)).unwrap();
    let max_length_ptr = create(max_length.as_ptr());
    assert_ne!(max_length_ptr, 0, "256 character name should succeed");

    let empty = CString::new("").unwrap();
    let empty_ptr = create(empty.as_ptr());
    assert_ne!(empty_ptr, 0, "empty name should be allowed");

    unsafe {
        drop_otel_span(max_length_ptr);
        drop_otel_span(empty_ptr);
    }
}

#[test]
fn test_create_otel_span_with_invalid_inputs() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test with invalid request types that should return 0
    let invalid_request_types = vec![
        RequestType::InvalidRequest,
        // Add other invalid request types as needed
    ];

    for request_type in invalid_request_types {
        let span_ptr = create_otel_span(request_type);
        assert_eq!(
            span_ptr, 0,
            "create_otel_span should fail for invalid request type {request_type:?}",
        );
    }
}

#[test]
fn test_create_named_otel_span_with_valid_inputs() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    let test_cases = vec![
        ("simple_span", true),
        ("", true), // Empty names are allowed
        ("span_with_underscores", true),
        ("span-with-dashes", true),
        ("span.with.dots", true),
        ("span123", true),
        ("UPPERCASE_SPAN", true),
        ("MixedCase_Span", true),
        ("span\twith\ttabs", true),     // Tabs are allowed
        ("span\nwith\nnewlines", true), // Newlines are allowed
        ("span\rwith\rcarriage", true), // Carriage returns are allowed
    ];

    let mut span_ptrs = Vec::new();

    for (name, should_succeed) in test_cases {
        let name_cstring = CString::new(name).expect("CString::new failed");
        let span_ptr = unsafe { create_named_otel_span(name_cstring.as_ptr()) };

        if should_succeed {
            assert_ne!(
                span_ptr, 0,
                "create_named_otel_span should succeed for name: '{name}'",
            );

            // Verify pointer properties
            assert_eq!(span_ptr % 8, 0, "Span pointer should be 8-byte aligned");
            assert!(
                span_ptr >= 0x1000,
                "Span pointer should be above minimum valid address"
            );
            assert!(
                span_ptr <= 0x7FFF_FFFF_FFFF_FFF8,
                "Span pointer should be below maximum valid address"
            );

            span_ptrs.push(span_ptr);
        } else {
            assert_eq!(
                span_ptr, 0,
                "create_named_otel_span should fail for name: '{name}'",
            );
        }
    }

    // Clean up all valid spans
    for span_ptr in span_ptrs {
        unsafe {
            drop_otel_span(span_ptr);
        }
    }
}

#[test]
fn test_create_named_otel_span_with_invalid_inputs() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test with null pointer
    let null_span_ptr = unsafe { create_named_otel_span(std::ptr::null()) };
    assert_eq!(null_span_ptr, 0, "Null pointer should return 0");

    // Test with name that's too long (over 256 characters)
    let long_name = "a".repeat(300);
    let long_name_cstring = CString::new(long_name).expect("CString::new failed");
    let long_span_ptr = unsafe { create_named_otel_span(long_name_cstring.as_ptr()) };
    assert_eq!(long_span_ptr, 0, "Long name (300 chars) should return 0");

    // Test boundary case: exactly 256 characters should succeed
    let max_length_name = "a".repeat(256);
    let max_length_cstring = CString::new(max_length_name).expect("CString::new failed");
    let max_length_span_ptr = unsafe { create_named_otel_span(max_length_cstring.as_ptr()) };
    assert_ne!(max_length_span_ptr, 0, "256 character name should succeed");

    // Test boundary case: 257 characters should fail
    let over_max_name = "a".repeat(257);
    let over_max_cstring = CString::new(over_max_name).expect("CString::new failed");
    let over_max_span_ptr = unsafe { create_named_otel_span(over_max_cstring.as_ptr()) };
    assert_eq!(over_max_span_ptr, 0, "257 character name should fail");

    // Clean up valid span
    unsafe {
        drop_otel_span(max_length_span_ptr);
    }
}

#[test]
fn test_create_otel_span_with_parent_valid_inputs() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Create a parent span
    let parent_name = CString::new("parent_span").expect("CString::new failed");
    let parent_span_ptr = unsafe { create_named_otel_span(parent_name.as_ptr()) };
    assert_ne!(parent_span_ptr, 0, "Parent span creation should succeed");

    // Test creating child spans with various request types
    let request_types = vec![
        RequestType::Get,
        RequestType::Set,
        RequestType::Del,
        RequestType::HGet,
        RequestType::HSet,
    ];

    let mut child_span_ptrs = Vec::new();

    for request_type in request_types {
        let child_span_ptr = unsafe { create_otel_span_with_parent(request_type, parent_span_ptr) };
        assert_ne!(
            child_span_ptr, 0,
            "Child span creation should succeed for {request_type:?}",
        );

        // Verify child span has different pointer than parent
        assert_ne!(
            child_span_ptr, parent_span_ptr,
            "Child span should have different pointer than parent"
        );

        // Verify pointer properties
        assert_eq!(
            child_span_ptr % 8,
            0,
            "Child span pointer should be 8-byte aligned"
        );
        assert!(
            child_span_ptr >= 0x1000,
            "Child span pointer should be above minimum valid address"
        );
        assert!(
            child_span_ptr <= 0x7FFF_FFFF_FFFF_FFF8,
            "Child span pointer should be below maximum valid address"
        );

        child_span_ptrs.push(child_span_ptr);
    }

    // Clean up all spans
    for child_span_ptr in child_span_ptrs {
        unsafe {
            drop_otel_span(child_span_ptr);
        }
    }

    unsafe {
        drop_otel_span(parent_span_ptr);
    }
}

#[test]
fn test_create_otel_span_with_parent_invalid_inputs() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test with null parent (should fallback to independent span)
    let child_with_null_parent = unsafe { create_otel_span_with_parent(RequestType::Get, 0) };
    assert_ne!(
        child_with_null_parent, 0,
        "Null parent should fallback to independent span"
    );

    // Test with invalid parent pointer (should fallback to independent span)
    let invalid_parent_ptrs = vec![
        0xDEADBEEF,            // Garbage pointer
        0x1001,                // Misaligned pointer
        0x800,                 // Address too low
        0x8000_0000_0000_0000, // Address too high
    ];

    let mut fallback_span_ptrs = vec![child_with_null_parent];

    for invalid_parent_ptr in invalid_parent_ptrs {
        let child_span_ptr =
            unsafe { create_otel_span_with_parent(RequestType::Set, invalid_parent_ptr) };
        assert_ne!(
            child_span_ptr, 0,
            "Invalid parent 0x{invalid_parent_ptr:x} should fallback to independent span",
        );
        fallback_span_ptrs.push(child_span_ptr);
    }

    // Test with invalid request type (should return 0 regardless of parent)
    let valid_parent_name = CString::new("valid_parent").expect("CString::new failed");
    let valid_parent_ptr = unsafe { create_named_otel_span(valid_parent_name.as_ptr()) };
    assert_ne!(valid_parent_ptr, 0, "Valid parent creation should succeed");

    let child_with_invalid_request =
        unsafe { create_otel_span_with_parent(RequestType::InvalidRequest, valid_parent_ptr) };
    assert_eq!(
        child_with_invalid_request, 0,
        "Invalid request type should return 0 even with valid parent"
    );

    // Clean up all spans
    for span_ptr in fallback_span_ptrs {
        unsafe {
            drop_otel_span(span_ptr);
        }
    }

    unsafe {
        drop_otel_span(valid_parent_ptr);
    }
}

#[test]
fn test_drop_otel_span_memory_safety() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test dropping null pointer (should not crash)
    unsafe {
        drop_otel_span(0);
    }

    // Test dropping invalid pointers (should not crash, should log errors)
    let invalid_pointers = vec![
        0xDEADBEEF,            // Garbage pointer
        0x1001,                // Misaligned pointer
        0x800,                 // Address too low
        0x8000_0000_0000_0000, // Address too high
        0xFFFF_FFFF_FFFF_FFFF, // Maximum u64
    ];

    for invalid_ptr in invalid_pointers {
        unsafe {
            drop_otel_span(invalid_ptr); // Should not crash
        }
    }

    // Test dropping valid spans
    let valid_name = CString::new("test_drop").expect("CString::new failed");
    let valid_span_ptr = unsafe { create_named_otel_span(valid_name.as_ptr()) };
    assert_ne!(valid_span_ptr, 0, "Valid span creation should succeed");

    unsafe {
        drop_otel_span(valid_span_ptr); // Should succeed
    }
}

#[test]
fn test_ffi_functions_concurrent_access() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    let num_threads = 3; // Reduced number of threads to avoid race conditions
    let spans_per_thread = 2; // Reduced spans per thread
    let counter = Arc::new(AtomicUsize::new(0));
    let mut handles = Vec::new();

    // Test concurrent span creation and cleanup
    for thread_id in 0..num_threads {
        let counter_clone = Arc::clone(&counter);

        let handle = thread::spawn(move || {
            let mut thread_spans = Vec::new();

            // Create spans concurrently
            for i in 0..spans_per_thread {
                // Create named span
                let span_name = format!("thread_{thread_id}_span_{i}");
                let span_name_cstring = CString::new(span_name).expect("CString::new failed");
                let named_span_ptr = unsafe { create_named_otel_span(span_name_cstring.as_ptr()) };

                if named_span_ptr != 0 {
                    thread_spans.push(named_span_ptr);
                    counter_clone.fetch_add(1, Ordering::SeqCst);

                    // Only create child span if parent was successfully created
                    let child_span_ptr =
                        unsafe { create_otel_span_with_parent(RequestType::Get, named_span_ptr) };
                    if child_span_ptr != 0 {
                        thread_spans.push(child_span_ptr);
                        counter_clone.fetch_add(1, Ordering::SeqCst);
                    }
                }

                // Small delay to reduce race conditions
                thread::sleep(Duration::from_millis(10));
            }

            // Clean up spans in reverse order (children first)
            thread_spans.reverse();
            for span_ptr in thread_spans {
                unsafe {
                    drop_otel_span(span_ptr);
                }
                // Small delay between drops
                thread::sleep(Duration::from_millis(1));
            }
        });

        handles.push(handle);
    }

    // Wait for all threads to complete
    for handle in handles {
        handle.join().expect("Thread should complete successfully");
    }

    let total_spans_created = counter.load(Ordering::SeqCst);
    assert!(
        total_spans_created > 0,
        "At least some spans should have been created"
    );
    println!(
        "Successfully created and cleaned up {total_spans_created} spans across {num_threads} threads",
    );
}

#[test]
fn test_span_hierarchy_creation() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Create a multi-level span hierarchy
    let root_name = CString::new("root_span").expect("CString::new failed");
    let root_span_ptr = unsafe { create_named_otel_span(root_name.as_ptr()) };
    assert_ne!(root_span_ptr, 0, "Root span creation should succeed");

    // Create first level children
    let child1_ptr = unsafe { create_otel_span_with_parent(RequestType::Get, root_span_ptr) };
    let child2_ptr = unsafe { create_otel_span_with_parent(RequestType::Set, root_span_ptr) };

    assert_ne!(child1_ptr, 0, "Child1 span creation should succeed");
    assert_ne!(child2_ptr, 0, "Child2 span creation should succeed");
    assert_ne!(
        child1_ptr, child2_ptr,
        "Child spans should have different pointers"
    );

    // Create second level children (grandchildren)
    let grandchild1_ptr = unsafe { create_otel_span_with_parent(RequestType::Del, child1_ptr) };
    let grandchild2_ptr = unsafe { create_otel_span_with_parent(RequestType::Exists, child2_ptr) };

    assert_ne!(
        grandchild1_ptr, 0,
        "Grandchild1 span creation should succeed"
    );
    assert_ne!(
        grandchild2_ptr, 0,
        "Grandchild2 span creation should succeed"
    );
    assert_ne!(
        grandchild1_ptr, grandchild2_ptr,
        "Grandchild spans should have different pointers"
    );

    // Verify all spans have unique pointers
    let all_spans = [
        root_span_ptr,
        child1_ptr,
        child2_ptr,
        grandchild1_ptr,
        grandchild2_ptr,
    ];
    for i in 0..all_spans.len() {
        for j in (i + 1)..all_spans.len() {
            assert_ne!(
                all_spans[i], all_spans[j],
                "All spans should have unique pointers"
            );
        }
    }

    // Clean up in reverse order (children before parents)
    unsafe {
        drop_otel_span(grandchild1_ptr);
        drop_otel_span(grandchild2_ptr);
        drop_otel_span(child1_ptr);
        drop_otel_span(child2_ptr);
        drop_otel_span(root_span_ptr);
    }
}

#[test]
fn test_error_handling_and_logging() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test various error conditions to ensure proper logging

    // 1. Invalid request type
    let invalid_span = create_otel_span(RequestType::InvalidRequest);
    assert_eq!(invalid_span, 0, "Invalid request type should return 0");

    // 2. Null pointer for named span
    let null_named_span = unsafe { create_named_otel_span(std::ptr::null()) };
    assert_eq!(null_named_span, 0, "Null pointer should return 0");

    // 3. Long name for named span
    let long_name = "a".repeat(300);
    let long_name_cstring = CString::new(long_name).expect("CString::new failed");
    let long_named_span = unsafe { create_named_otel_span(long_name_cstring.as_ptr()) };
    assert_eq!(long_named_span, 0, "Long name should return 0");

    // 4. Invalid parent for child span
    let child_with_invalid_parent =
        unsafe { create_otel_span_with_parent(RequestType::Get, 0xDEADBEEF) };
    assert_ne!(
        child_with_invalid_parent, 0,
        "Invalid parent should fallback to independent span"
    );

    // 5. Drop invalid spans
    unsafe {
        drop_otel_span(0xDEADBEEF); // Should log error but not crash
        drop_otel_span(0x1001); // Should log error but not crash
    }

    // Clean up valid span
    unsafe {
        drop_otel_span(child_with_invalid_parent);
    }
}

#[test]
fn test_boundary_conditions() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test span name length boundaries
    let boundary_cases = vec![
        (255, true),  // Just under limit
        (256, true),  // At limit
        (257, false), // Just over limit
    ];

    let mut valid_spans = Vec::new();

    for (length, should_succeed) in boundary_cases {
        let name = "a".repeat(length);
        let name_cstring = CString::new(name).expect("CString::new failed");
        let span_ptr = unsafe { create_named_otel_span(name_cstring.as_ptr()) };

        if should_succeed {
            assert_ne!(span_ptr, 0, "Name with {length} characters should succeed",);
            valid_spans.push(span_ptr);
        } else {
            assert_eq!(span_ptr, 0, "Name with {length} characters should fail");
        }
    }

    // Test pointer alignment boundaries with drop_otel_span
    // These should all be handled gracefully without crashing
    let invalid_pointers = vec![
        0x1001, // Not 8-byte aligned
        0x1004, // Not 8-byte aligned
        0x1007, // Not 8-byte aligned
        0x800,  // Address too low
    ];

    for ptr_value in invalid_pointers {
        // These should handle invalid pointers gracefully without crashing
        unsafe {
            drop_otel_span(ptr_value); // Should log error but not crash
        }
    }

    // Clean up valid spans
    for span_ptr in valid_spans {
        unsafe {
            drop_otel_span(span_ptr);
        }
    }
}
#[test]
fn test_create_batch_otel_span() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test creating independent batch span
    let batch_span_ptr = create_batch_otel_span();
    assert_ne!(batch_span_ptr, 0, "create_batch_otel_span should succeed");

    // Verify pointer properties
    assert_eq!(
        batch_span_ptr % 8,
        0,
        "Batch span pointer should be 8-byte aligned"
    );
    assert!(
        batch_span_ptr >= 0x1000,
        "Batch span pointer should be above minimum valid address"
    );
    assert!(
        batch_span_ptr <= 0x7FFF_FFFF_FFFF_FFF8,
        "Batch span pointer should be below maximum valid address"
    );

    // Clean up
    unsafe {
        drop_otel_span(batch_span_ptr);
    }
}

#[test]
fn test_create_batch_otel_span_with_parent_valid_inputs() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Create a parent span
    let parent_name = CString::new("parent_operation").expect("CString::new failed");
    let parent_span_ptr = unsafe { create_named_otel_span(parent_name.as_ptr()) };
    assert_ne!(parent_span_ptr, 0, "Parent span creation should succeed");

    // Create batch span with parent
    let batch_span_ptr = unsafe { create_batch_otel_span_with_parent(parent_span_ptr) };
    assert_ne!(
        batch_span_ptr, 0,
        "create_batch_otel_span_with_parent should succeed"
    );

    // Verify batch span has different pointer than parent
    assert_ne!(
        batch_span_ptr, parent_span_ptr,
        "Batch span should have different pointer than parent"
    );

    // Verify pointer properties
    assert_eq!(
        batch_span_ptr % 8,
        0,
        "Batch span pointer should be 8-byte aligned"
    );
    assert!(
        batch_span_ptr >= 0x1000,
        "Batch span pointer should be above minimum valid address"
    );
    assert!(
        batch_span_ptr <= 0x7FFF_FFFF_FFFF_FFF8,
        "Batch span pointer should be below maximum valid address"
    );

    // Clean up (child first, then parent)
    unsafe {
        drop_otel_span(batch_span_ptr);
        drop_otel_span(parent_span_ptr);
    }
}

#[test]
fn test_create_batch_otel_span_with_parent_invalid_inputs() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test with null parent (should fallback to independent batch span)
    let batch_with_null_parent = unsafe { create_batch_otel_span_with_parent(0) };
    assert_ne!(
        batch_with_null_parent, 0,
        "Null parent should fallback to independent batch span"
    );

    // Test with invalid parent pointers (should fallback to independent batch span)
    let invalid_parent_ptrs = vec![
        0xDEADBEEF,            // Garbage pointer
        0x1001,                // Misaligned pointer
        0x800,                 // Address too low
        0x8000_0000_0000_0000, // Address too high
    ];

    let mut fallback_batch_spans = vec![batch_with_null_parent];

    for invalid_parent_ptr in invalid_parent_ptrs {
        let batch_span_ptr = unsafe { create_batch_otel_span_with_parent(invalid_parent_ptr) };
        assert_ne!(
            batch_span_ptr, 0,
            "Invalid parent 0x{invalid_parent_ptr:x} should fallback to independent batch span",
        );
        fallback_batch_spans.push(batch_span_ptr);
    }

    // Clean up all fallback batch spans
    for span_ptr in fallback_batch_spans {
        unsafe {
            drop_otel_span(span_ptr);
        }
    }
}

#[test]
fn test_batch_span_hierarchy() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Create a root operation span
    let root_name = CString::new("user_operation").expect("CString::new failed");
    let root_span_ptr = unsafe { create_named_otel_span(root_name.as_ptr()) };
    assert_ne!(root_span_ptr, 0, "Root span creation should succeed");

    // Create batch span as child of root
    let batch_span_ptr = unsafe { create_batch_otel_span_with_parent(root_span_ptr) };
    assert_ne!(batch_span_ptr, 0, "Batch span creation should succeed");

    // Create individual command spans as children of batch span
    let cmd1_span_ptr = unsafe { create_otel_span_with_parent(RequestType::Set, batch_span_ptr) };
    let cmd2_span_ptr = unsafe { create_otel_span_with_parent(RequestType::Get, batch_span_ptr) };
    let cmd3_span_ptr = unsafe { create_otel_span_with_parent(RequestType::Del, batch_span_ptr) };

    assert_ne!(cmd1_span_ptr, 0, "Command 1 span creation should succeed");
    assert_ne!(cmd2_span_ptr, 0, "Command 2 span creation should succeed");
    assert_ne!(cmd3_span_ptr, 0, "Command 3 span creation should succeed");

    // Verify all spans have unique pointers
    let all_spans = [
        root_span_ptr,
        batch_span_ptr,
        cmd1_span_ptr,
        cmd2_span_ptr,
        cmd3_span_ptr,
    ];
    for i in 0..all_spans.len() {
        for j in (i + 1)..all_spans.len() {
            assert_ne!(
                all_spans[i], all_spans[j],
                "All spans should have unique pointers"
            );
        }
    }

    // Clean up in reverse order (children before parents)
    unsafe {
        drop_otel_span(cmd1_span_ptr);
        drop_otel_span(cmd2_span_ptr);
        drop_otel_span(cmd3_span_ptr);
        drop_otel_span(batch_span_ptr);
        drop_otel_span(root_span_ptr);
    }
}

#[test]
fn test_batch_span_concurrent_creation() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    let num_threads = 3;
    let batches_per_thread = 2;
    let counter = Arc::new(AtomicUsize::new(0));
    let mut handles = Vec::new();

    // Test concurrent batch span creation
    for thread_id in 0..num_threads {
        let counter_clone = Arc::clone(&counter);

        let handle = thread::spawn(move || {
            let mut thread_spans = Vec::new();

            for i in 0..batches_per_thread {
                // Create parent operation span
                let parent_name = format!("thread_{thread_id}_operation_{i}");
                let parent_name_cstring = CString::new(parent_name).expect("CString::new failed");
                let parent_span_ptr =
                    unsafe { create_named_otel_span(parent_name_cstring.as_ptr()) };

                if parent_span_ptr != 0 {
                    thread_spans.push(parent_span_ptr);
                    counter_clone.fetch_add(1, Ordering::SeqCst);

                    // Create batch span with parent
                    let batch_span_ptr =
                        unsafe { create_batch_otel_span_with_parent(parent_span_ptr) };
                    if batch_span_ptr != 0 {
                        thread_spans.push(batch_span_ptr);
                        counter_clone.fetch_add(1, Ordering::SeqCst);

                        // Create a few command spans under the batch
                        let cmd_span_ptr = unsafe {
                            create_otel_span_with_parent(RequestType::Set, batch_span_ptr)
                        };
                        if cmd_span_ptr != 0 {
                            thread_spans.push(cmd_span_ptr);
                            counter_clone.fetch_add(1, Ordering::SeqCst);
                        }
                    }
                }

                // Small delay to reduce race conditions
                thread::sleep(Duration::from_millis(10));
            }

            // Clean up spans in reverse order (children first)
            thread_spans.reverse();
            for span_ptr in thread_spans {
                unsafe {
                    drop_otel_span(span_ptr);
                }
                thread::sleep(Duration::from_millis(1));
            }
        });

        handles.push(handle);
    }

    // Wait for all threads to complete
    for handle in handles {
        handle.join().expect("Thread should complete successfully");
    }

    let total_spans_created = counter.load(Ordering::SeqCst);
    assert!(
        total_spans_created > 0,
        "At least some spans should have been created"
    );
    println!(
        "Successfully created and cleaned up {total_spans_created} spans (including batch spans) across {num_threads} threads",
    );
}

#[test]
fn test_batch_span_error_handling() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test batch span creation with various error conditions

    // 1. Create batch span with null parent (should fallback)
    let batch_with_null = unsafe { create_batch_otel_span_with_parent(0) };
    assert_ne!(
        batch_with_null, 0,
        "Batch with null parent should fallback to independent span"
    );

    // 2. Create batch span with invalid parent (should fallback)
    let batch_with_invalid = unsafe { create_batch_otel_span_with_parent(0xDEADBEEF) };
    assert_ne!(
        batch_with_invalid, 0,
        "Batch with invalid parent should fallback to independent span"
    );

    // 3. Create batch span with misaligned parent (should fallback)
    let batch_with_misaligned = unsafe { create_batch_otel_span_with_parent(0x1001) };
    assert_ne!(
        batch_with_misaligned, 0,
        "Batch with misaligned parent should fallback to independent span"
    );

    // All should have created valid independent batch spans
    let all_batch_spans = vec![batch_with_null, batch_with_invalid, batch_with_misaligned];

    // Verify all are valid and unique
    for (i, &span_ptr) in all_batch_spans.iter().enumerate() {
        assert_ne!(span_ptr, 0, "Batch span {i} should be valid");
        assert_eq!(span_ptr % 8, 0, "Batch span {i} should be aligned");

        // Check uniqueness
        for (j, &other_span_ptr) in all_batch_spans.iter().enumerate() {
            if i != j {
                assert_ne!(
                    span_ptr, other_span_ptr,
                    "Batch spans {i} and {j} should be unique",
                );
            }
        }
    }

    // Clean up all batch spans
    for span_ptr in all_batch_spans {
        unsafe {
            drop_otel_span(span_ptr);
        }
    }
}

#[test]
fn test_custom_command_span_creation() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    let span_ptr = create_otel_span(RequestType::CustomCommand);
    assert_ne!(
        span_ptr, 0,
        "create_otel_span should succeed for CustomCommand with fallback name"
    );

    // Verify pointer properties
    assert_eq!(span_ptr % 8, 0, "Span pointer should be 8-byte aligned");
    assert!(
        span_ptr >= 0x1000,
        "Span pointer should be above minimum valid address"
    );

    // Clean up
    unsafe {
        drop_otel_span(span_ptr);
    }
}

#[test]
fn test_custom_command_span_with_parent() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Create a parent span
    let parent_name = CString::new("user_operation").expect("CString::new failed");
    let parent_span_ptr = unsafe { create_named_otel_span(parent_name.as_ptr()) };
    assert_ne!(parent_span_ptr, 0, "Parent span creation should succeed");

    let child_span_ptr =
        unsafe { create_otel_span_with_parent(RequestType::CustomCommand, parent_span_ptr) };
    assert_ne!(
        child_span_ptr, 0,
        "create_otel_span_with_parent should succeed for CustomCommand with fallback name"
    );

    // Verify child span has different pointer than parent
    assert_ne!(
        child_span_ptr, parent_span_ptr,
        "Child span should have different pointer than parent"
    );

    // Verify pointer properties
    assert_eq!(
        child_span_ptr % 8,
        0,
        "Child span pointer should be 8-byte aligned"
    );

    // Clean up (child first, then parent)
    unsafe {
        drop_otel_span(child_span_ptr);
        drop_otel_span(parent_span_ptr);
    }
}

#[test]
fn test_multiple_custom_command_spans() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    let mut span_ptrs = Vec::new();

    for i in 0..5 {
        let span_ptr = create_otel_span(RequestType::CustomCommand);
        assert_ne!(
            span_ptr, 0,
            "create_otel_span should succeed for CustomCommand iteration {i}"
        );

        // Verify pointer properties
        assert_eq!(span_ptr % 8, 0, "Span pointer should be 8-byte aligned");
        assert!(
            span_ptr >= 0x1000,
            "Span pointer should be above minimum valid address"
        );

        span_ptrs.push(span_ptr);
    }

    // Verify all spans are unique
    for i in 0..span_ptrs.len() {
        for j in (i + 1)..span_ptrs.len() {
            assert_ne!(
                span_ptrs[i], span_ptrs[j],
                "All CustomCommand spans should have unique pointers"
            );
        }
    }

    // Clean up all spans
    for span_ptr in span_ptrs {
        unsafe {
            drop_otel_span(span_ptr);
        }
    }
}

#[test]
fn test_custom_command_hierarchy() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Create a root operation span
    let root_name = CString::new("script_execution").expect("CString::new failed");
    let root_span_ptr = unsafe { create_named_otel_span(root_name.as_ptr()) };
    assert_ne!(root_span_ptr, 0, "Root span creation should succeed");

    // Create batch span as child of root
    let batch_span_ptr = unsafe { create_batch_otel_span_with_parent(root_span_ptr) };
    assert_ne!(batch_span_ptr, 0, "Batch span creation should succeed");

    let custom_span_1 =
        unsafe { create_otel_span_with_parent(RequestType::CustomCommand, batch_span_ptr) };
    let custom_span_2 =
        unsafe { create_otel_span_with_parent(RequestType::CustomCommand, batch_span_ptr) };
    let custom_span_3 =
        unsafe { create_otel_span_with_parent(RequestType::CustomCommand, batch_span_ptr) };

    assert_ne!(
        custom_span_1, 0,
        "CustomCommand span 1 creation should succeed"
    );
    assert_ne!(
        custom_span_2, 0,
        "CustomCommand span 2 creation should succeed"
    );
    assert_ne!(
        custom_span_3, 0,
        "CustomCommand span 3 creation should succeed"
    );

    // Verify all spans have unique pointers
    let all_spans = [
        root_span_ptr,
        batch_span_ptr,
        custom_span_1,
        custom_span_2,
        custom_span_3,
    ];
    for i in 0..all_spans.len() {
        for j in (i + 1)..all_spans.len() {
            assert_ne!(
                all_spans[i], all_spans[j],
                "All spans should have unique pointers"
            );
        }
    }

    // Clean up in reverse order (children before parents)
    unsafe {
        drop_otel_span(custom_span_1);
        drop_otel_span(custom_span_2);
        drop_otel_span(custom_span_3);
        drop_otel_span(batch_span_ptr);
        drop_otel_span(root_span_ptr);
    }
}

#[test]
fn test_mixed_command_types_in_batch() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Create a batch span
    let batch_span_ptr = create_batch_otel_span();
    assert_ne!(batch_span_ptr, 0, "Batch span creation should succeed");

    // Create a mix of regular commands and custom commands
    let command_types = vec![
        RequestType::Set,
        RequestType::Get,
        RequestType::CustomCommand, // Represents EVAL, EVALSHA, etc.
        RequestType::Del,
        RequestType::CustomCommand, // Another custom command
        RequestType::Exists,
        RequestType::CustomCommand, // Yet another custom command
    ];

    let mut child_spans = Vec::new();

    for request_type in command_types {
        let child_span_ptr = unsafe { create_otel_span_with_parent(request_type, batch_span_ptr) };
        assert_ne!(
            child_span_ptr, 0,
            "Child span creation should succeed for {request_type:?}"
        );
        child_spans.push(child_span_ptr);
    }

    // Verify all child spans are unique
    for i in 0..child_spans.len() {
        for j in (i + 1)..child_spans.len() {
            assert_ne!(
                child_spans[i], child_spans[j],
                "All child spans should have unique pointers"
            );
        }
    }

    // Clean up all child spans first
    for child_span_ptr in child_spans {
        unsafe {
            drop_otel_span(child_span_ptr);
        }
    }

    // Clean up batch span
    unsafe {
        drop_otel_span(batch_span_ptr);
    }
}

#[test]
fn test_custom_command_with_null_parent() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test CustomCommand span creation with null parent (should fallback to independent span)
    let span_ptr = unsafe { create_otel_span_with_parent(RequestType::CustomCommand, 0) };
    assert_ne!(
        span_ptr, 0,
        "CustomCommand with null parent should fallback to independent span"
    );

    // Verify pointer properties
    assert_eq!(span_ptr % 8, 0, "Span pointer should be 8-byte aligned");

    // Clean up
    unsafe {
        drop_otel_span(span_ptr);
    }
}

#[test]
fn test_custom_command_with_invalid_parent() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test CustomCommand span creation with invalid parent pointers
    let invalid_parents = vec![
        0xDEADBEEF,            // Garbage pointer
        0x1001,                // Misaligned pointer
        0x800,                 // Address too low
        0x8000_0000_0000_0000, // Address too high
    ];

    let mut fallback_spans = Vec::new();

    for invalid_parent_ptr in invalid_parents {
        let span_ptr =
            unsafe { create_otel_span_with_parent(RequestType::CustomCommand, invalid_parent_ptr) };
        assert_ne!(
            span_ptr, 0,
            "CustomCommand with invalid parent 0x{invalid_parent_ptr:x} should fallback to independent span"
        );
        fallback_spans.push(span_ptr);
    }

    // Clean up all fallback spans
    for span_ptr in fallback_spans {
        unsafe {
            drop_otel_span(span_ptr);
        }
    }
}

#[test]
fn test_regression_no_error_logs_for_custom_command() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    let span_ptr = create_otel_span(RequestType::CustomCommand);
    assert_ne!(
        span_ptr, 0,
        "CustomCommand span creation should succeed without errors"
    );

    // Create CustomCommand span with parent - should succeed without errors
    let parent_name = CString::new("parent").expect("CString::new failed");
    let parent_span_ptr = unsafe { create_named_otel_span(parent_name.as_ptr()) };
    assert_ne!(parent_span_ptr, 0, "Parent span creation should succeed");

    let child_span_ptr =
        unsafe { create_otel_span_with_parent(RequestType::CustomCommand, parent_span_ptr) };
    assert_ne!(
        child_span_ptr, 0,
        "CustomCommand child span creation should succeed without errors"
    );

    // Clean up
    unsafe {
        drop_otel_span(child_span_ptr);
        drop_otel_span(parent_span_ptr);
        drop_otel_span(span_ptr);
    }

    // If we reach here without panics, the fix is working correctly
    // No error logs should have been produced
}

// ---------------------------------------------------------------------------
// Native-memory leak regression tests (tracked by issue #6226).
//
// These replace the two Java integration tests `testSpanMemoryLeak` and
// `testSpanTransactionMemoryLeak` disabled in #6008. Those tests ran commands and then
// asserted on the JVM heap (`Runtime.totalMemory() - freeMemory()`), but OpenTelemetry
// spans are allocated in native Rust memory (`Arc<GlideSpan>` handed across FFI via
// `Arc::into_raw`), so a JVM-heap measurement could never observe the leak it targeted
// and varied 20-30% from GC/JIT noise.
//
// Scope: these are FFI-boundary guards. They prove `drop_otel_span` releases its
// `Arc<GlideSpan>` reference rather than leaking it — the only layer at which a leak can
// occur given the current design. They do not cover a wrapper that forgets to call
// `drop_otel_span` at all; that is a per-language binding concern.
//
// The checks below instead observe the span's `Arc` strong count directly, so a leak in
// `drop_otel_span` (a missed `Arc::from_raw`) is caught deterministically at the layer
// where it can actually occur.
// ---------------------------------------------------------------------------

/// A single create -> drop cycle must release the span's native allocation: the FFI-held
/// `Arc` reference is gone after `drop_otel_span`, leaving only the test's co-owner.
#[test]
fn test_drop_otel_span_releases_native_reference() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    let span_ptr = create_otel_span(RequestType::Get);
    assert_ne!(span_ptr, 0, "Span creation should succeed");

    // Co-own the span so we can watch the strong count across the FFI drop.
    let owner = unsafe { co_owner(span_ptr) };
    assert_eq!(
        Arc::strong_count(&owner),
        2,
        "Before drop: the FFI raw pointer and the test co-owner should both hold a reference"
    );

    unsafe {
        drop_otel_span(span_ptr);
    }

    assert_eq!(
        Arc::strong_count(&owner),
        1,
        "After drop: only the test co-owner should remain; drop_otel_span must release the \
         FFI-held reference rather than leak it"
    );
}

/// Repeatedly creating and dropping spans through the FFI entry points must not accumulate
/// native allocations: every span's reference count must return to baseline after its drop.
/// This is the sustained-load loop the disabled Java `testSpanMemoryLeak` intended, run at
/// the FFI boundary where the allocation actually lives.
#[test]
fn test_span_create_drop_loop_does_not_leak() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    const ITERATIONS: usize = 1000;
    let named = CString::new("loop_named_span").expect("CString::new failed");

    for i in 0..ITERATIONS {
        // Rotate across the parentless create paths to exercise each one's into_raw/from_raw.
        let span_ptr = match i % 3 {
            0 => create_otel_span(RequestType::Get),
            1 => create_otel_span(RequestType::Set),
            _ => unsafe { create_named_otel_span(named.as_ptr()) },
        };
        assert_ne!(span_ptr, 0, "Span creation should succeed on iteration {i}");

        let owner = unsafe { co_owner(span_ptr) };
        assert_eq!(
            Arc::strong_count(&owner),
            2,
            "Iteration {i}: span should have exactly the FFI reference plus the test co-owner"
        );

        unsafe {
            drop_otel_span(span_ptr);
        }

        assert_eq!(
            Arc::strong_count(&owner),
            1,
            "Iteration {i}: drop_otel_span must release the native reference (no leak)"
        );
        // `owner` is dropped here, fully reclaiming the span before the next iteration.
    }
}

/// A parent batch span with command children (the shape exercised by the disabled
/// `testSpanTransactionMemoryLeak`) must release every native allocation once each span is
/// dropped, including the parent reference held by its children's creation path.
#[test]
fn test_batch_span_hierarchy_does_not_leak() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    let parent_ptr = create_batch_otel_span();
    assert_ne!(parent_ptr, 0, "Batch parent span creation should succeed");
    let parent_owner = unsafe { co_owner(parent_ptr) };

    assert_eq!(
        Arc::strong_count(&parent_owner),
        2,
        "Parent before children: FFI reference plus test co-owner"
    );

    // Create a batch of command children, mirroring an exec/transaction with multiple commands.
    // Include a nested batch span via create_batch_otel_span_with_parent to match the
    // parented-batch shape of the disabled testSpanTransactionMemoryLeak.
    const CHILDREN: usize = 50;
    let mut child_ptrs = Vec::with_capacity(CHILDREN + 1);
    for i in 0..CHILDREN {
        let request_type = if i % 2 == 0 {
            RequestType::Set
        } else {
            RequestType::Get
        };
        let child_ptr = unsafe { create_otel_span_with_parent(request_type, parent_ptr) };
        assert_ne!(child_ptr, 0, "Child span {i} creation should succeed");
        child_ptrs.push(child_ptr);
    }
    let nested_batch_ptr = unsafe { create_batch_otel_span_with_parent(parent_ptr) };
    assert_ne!(
        nested_batch_ptr, 0,
        "Nested batch span creation should succeed"
    );
    child_ptrs.push(nested_batch_ptr);

    // Creating children must not bump the parent's strong count: the link is via span context,
    // not a retained clone of the parent's outer Arc. A leak here would be masked if the count
    // were only checked after all drops, so assert stability while the children are still live.
    assert_eq!(
        Arc::strong_count(&parent_owner),
        2,
        "Parent count must be unchanged by child creation (children hold no parent Arc clone)"
    );

    // Each child holds only its own FFI reference, so dropping a child returns its count to the
    // co-owner baseline.
    for (i, &child_ptr) in child_ptrs.iter().enumerate() {
        let child_owner = unsafe { co_owner(child_ptr) };
        assert_eq!(
            Arc::strong_count(&child_owner),
            2,
            "Child {i} before drop: FFI reference plus test co-owner"
        );
        unsafe {
            drop_otel_span(child_ptr);
        }
        assert_eq!(
            Arc::strong_count(&child_owner),
            1,
            "Child {i} after drop: native reference released (no leak)"
        );
    }

    // The parent falls back to the co-owner baseline once its own FFI reference is dropped.
    unsafe {
        drop_otel_span(parent_ptr);
    }
    assert_eq!(
        Arc::strong_count(&parent_owner),
        1,
        "Parent batch span after drop: native reference released (no leak)"
    );
}
