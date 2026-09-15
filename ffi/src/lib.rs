// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use glide_core::ConnectionRequest;
use glide_core::client::Client as GlideClient;
use glide_core::cluster_scan_container::get_cluster_scan_cursor;
use glide_core::command_request::SimpleRoutes;
use glide_core::command_request::{CacheMetricsType, Routes, SlotTypes};
use glide_core::connection_request;
use glide_core::errors::RequestErrorType;
use glide_core::errors::{self, error_message};
use glide_core::otel_db_semantics::{
    set_db_attributes, set_db_batch_attributes, set_db_script_attributes,
};
use glide_core::request_type::RequestType;
use glide_core::scripts_container;
use glide_core::{
    DEFAULT_FLUSH_SIGNAL_INTERVAL_MS, GlideOpenTelemetry, GlideOpenTelemetryConfigBuilder,
    GlideOpenTelemetrySignalsExporter, GlideSpan, Telemetry,
};
use protobuf::{Enum, Message};
use redis::ErrorKind;
use redis::ObjectType;
use redis::ScanStateRC;
use redis::cluster_routing::ResponsePolicy;
// Routable trait provides the command() method used for response policy lookup.
// In miri-tests with mock-redis, this may appear unused due to mock implementations.
#[allow(unused_imports)]
use redis::cluster_routing::Routable;
use redis::cluster_routing::{
    MultipleNodeRoutingInfo, Route, RoutingInfo, SingleNodeRoutingInfo, SlotAddr,
};
use redis::{ClusterScanArgs, RedisError};
use redis::{Cmd, Pipeline, PipelineRetryStrategy, RedisResult, Value};
use std::borrow::Cow;
use std::ffi::CStr;
use std::future::Future;
use std::mem::ManuallyDrop;
use std::slice::from_raw_parts;
use std::str;
use std::str::FromStr;
use std::sync::Arc;
use std::sync::Condvar;
use std::{
    ffi::{CString, c_void},
    os::raw::{c_char, c_double, c_long, c_ulong},
};
use tokio::runtime::Builder;
use tokio::runtime::Runtime;
use uuid::Uuid;

#[repr(C)]
pub struct ScriptHashBuffer {
    pub ptr: *mut u8,
    pub len: usize,
    pub capacity: usize,
}

/// Store a Lua script in the script cache and return its SHA1 hash.
///
/// # Parameters
///
/// * `script_bytes`: Pointer to the script bytes.
/// * `script_len`: Length of the script in bytes.
///
/// # Returns
///
/// A C string containing the SHA1 hash of the script. The caller is responsible for freeing this memory.
/// We can free the memory using [`drop_script`].
///
/// # Safety
///
/// * `script_bytes` must point to `script_len` consecutive properly initialized bytes.
/// * The returned buffer must be freed by the caller using [`free_script_hash_buffer`].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn store_script(
    script_bytes: *const u8,
    script_len: usize,
) -> *mut ScriptHashBuffer {
    let script = unsafe { std::slice::from_raw_parts(script_bytes, script_len) };
    let hash = scripts_container::add_script(script);
    let mut hash = ManuallyDrop::new(hash);
    let script_hash_buffer = ScriptHashBuffer {
        ptr: hash.as_mut_ptr(),
        len: hash.len(),
        capacity: hash.capacity(),
    };
    Box::into_raw(Box::new(script_hash_buffer))
}

/// Free a `ScriptHashBuffer` obtained from [`store_script`].
///
/// # Parameters
///
/// * `buffer`: Pointer to the `ScriptHashBuffer`.
///
/// # Safety
///
/// * `buffer` must be a pointer returned from [`store_script`].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_script_hash_buffer(buffer: *mut ScriptHashBuffer) {
    let buffer = unsafe { Box::from_raw(buffer) };
    let _hash = unsafe { String::from_raw_parts(buffer.ptr, buffer.len, buffer.capacity) };
}

/// Remove a script from the script cache.
///
/// Returns a null pointer if it succeeds and a C string error message if it fails.
///
/// # Parameters
///
/// * `hash`: The SHA1 hash of the script to remove as a byte array.
/// * `len`: The length of `hash`.
///
/// # Safety
///
/// * `hash` must be a valid pointer to a UTF-8 string obtained from [`store_script`].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn drop_script(hash: *mut u8, len: usize) -> *mut c_char {
    if !hash.is_null() {
        let slice = std::ptr::slice_from_raw_parts_mut(hash, len);
        let Ok(hash_str) = str::from_utf8(unsafe { &*slice }) else {
            return CString::new("Unable to convert hash to UTF-8 string.")
                .unwrap()
                .into_raw();
        };
        scripts_container::remove_script(hash_str);
        std::ptr::null_mut()
    } else {
        CString::new("Hash pointer was null.").unwrap().into_raw()
    }
}

/// Free an error message from a failed drop_script call.
///
/// # Parameters
///
/// * `error`: The error to free.
///
/// # Safety
///
/// * `error` must be an error returned by [`drop_script`].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_drop_script_error(error: *mut c_char) {
    if !error.is_null() {
        _ = unsafe { CString::from_raw(error) };
    }
}

/// The struct represents the response of the command.
///
/// It will have one of the value populated depending on the return type of the command.
///
/// The struct is freed by the external caller by using `free_command_response` to avoid memory leaks.
/// TODO: Add a type enum to validate what type of response is being sent in the CommandResponse.
///
/// # Changing this struct
///
/// This struct's layout is relied upon by multiple consumers across languages.
/// When adding, removing, or reordering fields you MUST update all of the following:
///
/// 1. **CFFI declarations** — `python/glide-shared/glide_shared/_glide_ffi.py`
///    (the `typedef struct CommandResponse { ... }` block)
/// 2. **Fast response parser** — `python/glide-shared/src/lib.rs`
///    (field offset reads via raw pointer arithmetic)
/// 3. **Layout test** — `ffi/tests/test_command_response_layout.rs`
///    (update expected size and field offsets to match the new layout)
#[repr(C)]
#[derive(Debug, Clone)]
pub struct CommandResponse {
    pub response_type: ResponseType,
    pub int_value: i64,
    pub float_value: c_double,
    pub bool_value: bool,

    /// Below two values are related to each other.
    /// `string_value` represents the string.
    /// `string_value_len` represents the length of the string.
    ///
    /// Despite the `*mut` type (kept for C ABI compatibility), callers MUST
    /// treat this memory as **read-only**: for arena-allocated responses it
    /// points into a refcounted buffer shared with every other string in the
    /// same response (a zero-copy slice of the decoded network frame), so
    /// writing through it corrupts sibling values. It is valid only until
    /// `free_command_response` / `free_response_arena`; the backing buffer is
    /// recycled afterwards, so a stale pointer reads (or corrupts) unrelated
    /// future response data rather than faulting.
    pub string_value: *mut c_char,
    pub string_value_len: c_long,

    /// Below two values are related to each other.
    /// `array_value` represents the array of CommandResponse.
    /// `array_value_len` represents the length of the array.
    pub array_value: *mut CommandResponse,
    pub array_value_len: c_long,

    /// Below two values represent the Map structure inside CommandResponse.
    /// The map is transformed into an array of (map_key: CommandResponse, map_value: CommandResponse) and passed to the foreign language.
    /// These are represented as pointers as the map can be null (optionally present).
    pub map_key: *mut CommandResponse,
    pub map_value: *mut CommandResponse,

    /// Below two values are related to each other.
    /// `sets_value` represents the set of CommandResponse.
    /// `sets_value_len` represents the length of the set.
    pub sets_value: *mut CommandResponse,
    pub sets_value_len: c_long,

    /// Pointer to the `ResponseArena` that owns this response tree.
    /// Non-null when this response was allocated via the arena allocator.
    /// Callers must free the arena via `free_response_arena(arena_ptr)` after
    /// processing the response, or use `free_command_response` which handles
    /// both arena and box-allocated responses automatically.
    pub arena_ptr: *mut c_void,
}

impl Default for CommandResponse {
    fn default() -> Self {
        CommandResponse {
            response_type: ResponseType::default(),
            int_value: 0,
            float_value: 0.0,
            bool_value: false,
            string_value: std::ptr::null_mut(),
            string_value_len: 0,
            array_value: std::ptr::null_mut(),
            array_value_len: 0,
            map_key: std::ptr::null_mut(),
            map_value: std::ptr::null_mut(),
            sets_value: std::ptr::null_mut(),
            sets_value_len: 0,
            arena_ptr: std::ptr::null_mut(),
        }
    }
}

/// IMPORTANT: This enum is mirrored in python/glide-shared/src/lib.rs (fast response parser,
/// `convert` function) and declared in the CFFI definitions in
/// python/glide-shared/glide_shared/_glide_ffi.py. Any changes here must be reflected in both.
#[derive(Debug, Default, Clone)]
#[repr(C)]
pub enum ResponseType {
    #[default]
    Null = 0,
    Int = 1,
    Float = 2,
    Bool = 3,
    String = 4,
    Array = 5,
    Map = 6,
    Sets = 7,
    Ok = 8,
    Error = 9,
}

/// A Send-safe wrapper around caller-provided response buffers.
/// The caller guarantees the buffers remain valid for the duration of the FFI call.
enum ResponseBuffer {
    /// One caller buffer for a scalar reply (e.g. GET into a buffer). Stored
    /// inline so the common single-buffer path does not allocate.
    Single((*mut u8, usize)),
    /// One buffer per top-level array element (e.g. MGET into buffers).
    Multi(Vec<(*mut u8, usize)>),
}
unsafe impl Send for ResponseBuffer {}
impl ResponseBuffer {
    /// The buffers as a slice, consumed positionally by the arena builder
    /// (array element `i` -> buffer `i`; a scalar uses the first entry).
    fn as_slice(&self) -> &[(*mut u8, usize)] {
        match self {
            ResponseBuffer::Single(buf) => std::slice::from_ref(buf),
            ResponseBuffer::Multi(bufs) => bufs.as_slice(),
        }
    }
}

/// Success callback that is called when a command succeeds.
///
/// The success callback needs to copy the given string synchronously, since it will be dropped by Rust once the callback returns. The callback should be offloaded to a separate thread in order not to exhaust the client's thread pool.
///
/// `index_ptr` is a baton-pass back to the caller language to uniquely identify the promise.
/// `message` is the value returned by the command. The 'message' is managed by Rust and is freed when the callback returns control back to the caller.
///
/// # Safety
/// `message` must be a valid pointer to a `CommandResponse` and must be freed using [`free_command_response`].
pub type SuccessCallback =
    unsafe extern "C-unwind" fn(index_ptr: usize, message: *const CommandResponse) -> ();

/// Failure callback that is called when a command fails.
///
/// The failure callback needs to copy the given string synchronously, since it will be dropped by Rust once the callback returns. The callback should be offloaded to a separate thread in order not to exhaust the client's thread pool.
///
/// `index_ptr` is a baton-pass back to the caller language to uniquely identify the promise.
/// `error_message` is the error message returned by server for the failed command. The 'error_message' is managed by Rust and is freed when the callback returns control back to the caller.
/// `error_type` is the type of error returned by glide-core, depending on the `RedisError` returned.
///
/// # Safety
/// `error_message` must be a valid pointer to a `c_char`.
pub type FailureCallback = unsafe extern "C-unwind" fn(
    index_ptr: usize,
    error_message: *const c_char,
    error_type: RequestErrorType,
) -> ();

/// PubSub callback that is called when a push notification is received.
///
/// The PubSub callback needs to handle the push notification synchronously, since the data will be dropped by Rust once the callback returns.
/// The callback should be offloaded to a separate thread in order not to exhaust the client's thread pool.
///
/// # Parameters
/// * `client_ptr`: A baton-pass back to the caller language to uniquely identify the client.
/// * `kind`: An enum variant representing the PushKind (Message, PMessage, SMessage, etc.)
/// * `message`: A pointer to the raw message bytes.
/// * `message_len`: The length of the message data in bytes.
/// * `channel`: A pointer to the raw request name bytes.
/// * `channel_len`: The length of the request name in bytes.
/// * `pattern`: A pointer to the raw pattern bytes (null if no pattern).
/// * `pattern_len`: The length of the pattern in bytes (0 if no pattern).
///
/// # Safety
/// The pointers are only valid during the callback execution and will be freed
/// automatically when the callback returns. Any data needed beyond the callback's
/// execution must be copied.
pub type PubSubCallback = unsafe extern "C-unwind" fn(
    client_ptr: usize,
    kind: PushKind,
    message: *const u8,
    message_len: i64,
    channel: *const u8,
    channel_len: i64,
    pattern: *const u8,
    pattern_len: i64,
) -> ();

/// Address resolver callback that is called to resolve server addresses before connection.
///
/// The callback receives a host string and port, and should write the resolved host
/// into the provided buffer and return the resolved port. If the callback returns 0
/// for the port, the original address is used as a fallback.
///
/// # Parameters
/// * `host`: A pointer to the host string bytes (not null-terminated).
/// * `host_len`: The length of the host string in bytes.
/// * `port`: The port number to resolve.
/// * `resolved_host_buf`: A pointer to a buffer where the resolved host should be written.
/// * `resolved_host_buf_len`: The length of the resolved host buffer.
/// * `resolved_host_len`: A pointer where the actual length of the resolved host should be written.
///
/// # Returns
/// The resolved port number. If 0 is returned, the original address is used as a fallback.
///
/// # Safety
/// * `host` must point to `host_len` consecutive properly initialized bytes.
/// * `resolved_host_buf` must point to `resolved_host_buf_len` consecutive writable bytes.
/// * `resolved_host_len` must be a valid pointer to a writable `usize`.
/// * The callback must write the resolved host into `resolved_host_buf` and set `resolved_host_len`.
pub type AddressResolverCallback = unsafe extern "C-unwind" fn(
    client_id: usize,
    host: *const u8,
    host_len: usize,
    port: u16,
    resolved_host_buf: *mut u8,
    resolved_host_buf_len: usize,
    resolved_host_len: *mut usize,
) -> u16;

/// A wrapper around an FFI address resolver callback that implements the `AddressResolver` trait.
struct FFIAddressResolver {
    callback: AddressResolverCallback,
    client_id: usize,
}

// SAFETY: The callback is a C function pointer that is safe to send across threads.
unsafe impl Send for FFIAddressResolver {}
unsafe impl Sync for FFIAddressResolver {}

impl std::fmt::Debug for FFIAddressResolver {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "FFIAddressResolver {{ callback: <C function pointer> }}")
    }
}

impl redis::AddressResolver for FFIAddressResolver {
    fn resolve(&self, host: &str, port: u16) -> (String, u16) {
        // Provide a buffer for the resolved host (1024 bytes should be more than enough for a hostname)
        let mut resolved_host_buf = vec![0u8; 1024];
        let mut resolved_host_len: usize = 0;

        let resolved_port = unsafe {
            (self.callback)(
                self.client_id,
                host.as_ptr(),
                host.len(),
                port,
                resolved_host_buf.as_mut_ptr(),
                resolved_host_buf.len(),
                &mut resolved_host_len,
            )
        };

        // If the callback returned port 0 or didn't write a host, fall back to original
        if resolved_port == 0
            || resolved_host_len == 0
            || resolved_host_len > resolved_host_buf.len()
        {
            return (host.to_string(), port);
        }

        match std::str::from_utf8(&resolved_host_buf[..resolved_host_len]) {
            Ok(resolved_host) => (resolved_host.to_string(), resolved_port),
            Err(_) => {
                logger_core::log_error_lazy!(
                    "address_resolver",
                    "Address resolver returned invalid UTF-8 for host, using original address"
                );
                (host.to_string(), port)
            }
        }
    }
}

/// The connection response.
///
/// It contains either a connection or an error. It is represented as a struct instead of a union for ease of use in the wrapper language.
///
/// The struct is freed by the external caller by using `free_connection_response` to avoid memory leaks.
#[repr(C)]
pub struct ConnectionResponse {
    pub conn_ptr: *const c_void,
    pub connection_error_message: *const c_char,
}

/// Represents an error returned from a command execution.
///
/// This struct is returned as part of a [`CommandResult`] when a command fails in synchronous operations.
/// It contains both the error type and a message explaining the cause.
///
/// # Fields
///
/// - `command_error_message`: A null-terminated C string describing the error.
/// - `command_error_type`: An enum identifying the type of error. See [`RequestErrorType`] for details.
///
/// # Safety
///
/// The pointer `command_error_message` must remain valid and not be freed until after
/// [`free_command_result`] is called.
///
#[repr(C)]
pub struct CommandError {
    pub command_error_message: *const c_char,
    pub command_error_type: RequestErrorType,
}

/// Represents the result of a logging operation.
///
/// This struct is used to communicate both success/failure status and relevant data
/// across the FFI boundary. For initialization operations, it contains the log level
/// that was set. For other operations, it primarily indicates success or failure.
///
/// # Fields
///
/// - `log_error`: A pointer to a null-terminated C string containing an error message.
///   This field is `null` if the operation succeeded, or points to an error description
///   if the operation failed.
/// - `level`: The log level value. For initialization operations, this contains the
///   actual level that was set by the logger. For other operations, this field may
///   be ignored when there's an error.
///
/// # Safety
///
/// The returned `LogResult` must be freed using [`free_log_result`] to avoid memory leaks.
/// This will properly deallocate both the struct itself and any error message it contains.
///
/// - The `log_error` field must either be null or point to a valid, null-terminated C string
/// - The struct must be freed exactly once using [`free_log_result`]
/// - The error string must not be accessed after the struct has been freed
/// - The `level` field is only meaningful when `log_error` is null (success case)
#[repr(C)]
pub struct LogResult {
    pub log_error: *mut c_char,
    pub level: Level,
}

/// Represents the result of executing a command, either a successful response or an error.
///
/// This is the  return type for FFI functions that execute commands synchronously (e.g. with a SyncClient).
/// It is a tagged struct containing either a valid [`CommandResponse`] or a [`CommandError`].
/// If `command_error` is non-null, then `response` is guaranteed to be null and vice versa.
///
/// # Fields
///
/// - `response`: A pointer to a [`CommandResponse`] if the command was successful. Null if there was an error.
/// - `command_error`: A pointer to a [`CommandError`] if the command failed. Null if the command succeeded.
///
/// # Ownership
///
/// The returned pointer to `CommandResult` must be freed using [`free_command_result`] to avoid memory leaks.
/// This will recursively free both the response or the error, depending on which is set.
///
/// # Safety
///
/// The caller must check which field is non-null before accessing its contents.
/// Only one of the two fields (`response` or `command_error`) will be set.
#[repr(C)]
pub struct CommandResult {
    pub response: *mut CommandResponse,
    pub command_error: *mut CommandError,
    /// Opaque pointer to the ResponseArena that owns the response tree.
    /// If non-null, freeing this frees all response nodes and strings at once.
    pub arena: *mut ResponseArena,
}

// Deallocates a `CommandResult`.
///
/// This function frees both the `CommandResult` itself and its internal components if preset.
///
/// # Behavior
///
/// - If the provided `command_result_ptr` is null, the function returns immediately.
/// - If either `response` or `command_error` is non-null, they are deallocated accordingly.
///
/// # Safety
///
/// * `free_command_result` must only be called **once** for any given `CommandResult`.
///   Calling it multiple times is undefined behavior and may lead to double-free errors.
/// * The `command_result_ptr` must be a valid pointer returned by a function that creates a `CommandResult`.
/// * The memory behind `command_result_ptr` must remain valid until this function is called.
/// * If `command_error.command_error_message` is non-null, it must be a valid pointer obtained from Rust
///   and must outlive the `CommandError` itself.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_command_result(command_result_ptr: *mut CommandResult) {
    if command_result_ptr.is_null() {
        return;
    }
    unsafe {
        let command_result = Box::from_raw(command_result_ptr);
        if !command_result.arena.is_null() {
            // Arena owns all response nodes and strings — one free for everything
            free_response_arena(command_result.arena);
        } else if !command_result.response.is_null() {
            free_command_response(command_result.response);
        }
        if !command_result.command_error.is_null() {
            let command_error = Box::from_raw(command_result.command_error);
            if !command_error.command_error_message.is_null() {
                _ = CString::from_raw(command_error.command_error_message as *mut c_char);
            }
        }
    }
}

/// Specifies the type of client used to execute commands.
///
/// This enum distinguishes between synchronous and asynchronous client modes.
/// It is passed from the calling language (e.g. Go or Python) to determine how
/// command execution should be handled.
///
/// # Variants
///
/// - `AsyncClient`: Executes commands asynchronously. Includes callbacks for success and failure
///   that will be invoked once the command completes.
/// - `SyncClient`: Executes commands synchronously and returns a result directly.
#[repr(C)]
#[derive(Clone)]
pub enum ClientType {
    AsyncClient {
        success_callback: SuccessCallback,
        failure_callback: FailureCallback,
        /// When true, the success callback may receive a pointer to a stack-allocated
        /// CommandResponse for simple types (Nil, Int, Float, Bool, String, Ok).
        /// The callback must copy all data before returning and must NOT free the pointer.
        /// When false, all responses are heap-allocated via the arena and the caller
        /// is responsible for freeing them (e.g. via free_command_response).
        allow_stack_response: bool,
    },
    SyncClient,
}

const FRAME_SIZE: usize = 32;
/// Maximum total pubsub payload size sent inline through the pipe.
/// Messages larger than this use a heap pointer in a standard 32-byte frame
/// to avoid blocking other clients' responses during multi-read accumulation.
const MAX_INLINE_PUBSUB: usize = 1 << 16; // 64 KiB
struct SharedPipeWriter {
    buffer: std::sync::Mutex<Vec<u8>>,
    condvar: Condvar,
    /// Kept for potential future use (e.g. graceful shutdown via close(pipe_fd)).
    #[allow(dead_code)]
    pipe_fd: i32,
}
/// The process-wide async pipe state. Uses an atomic pointer so the read path
/// is lock-free and cannot be inherited in a locked state after fork().
/// Note: the `SharedPipeWriter` itself contains a `Mutex<Vec<u8>>` which is
/// fork-unsafe; callers must swap in a fresh writer via `reinit_async_pipe`
/// before any push touches the inherited instance.
static ASYNC_PIPE: std::sync::atomic::AtomicPtr<SharedPipeWriter> =
    std::sync::atomic::AtomicPtr::new(std::ptr::null_mut());

/// Returns the current pipe writer, if one is installed. Lock-free read.
#[inline]
fn get_async_pipe() -> Option<&'static SharedPipeWriter> {
    let p = ASYNC_PIPE.load(std::sync::atomic::Ordering::Acquire);
    if p.is_null() {
        None
    } else {
        // Safety: only leaked &'static SharedPipeWriter values are ever stored.
        Some(unsafe { &*p })
    }
}
impl SharedPipeWriter {
    fn push_success(&self, cid: u64, rid: usize, rp: usize, ap: usize) {
        let mut b = self.buffer.lock().unwrap();
        b.extend_from_slice(&cid.to_ne_bytes());
        b.extend_from_slice(&(rid as u64).to_ne_bytes());
        b.extend_from_slice(&(rp as u64).to_ne_bytes());
        b.extend_from_slice(&(ap as u64).to_ne_bytes());
        drop(b);
        self.condvar.notify_one();
    }
    fn push_error(&self, cid: u64, rid: usize, et: RequestErrorType, em: String) {
        let cs = CString::new(em).unwrap_or_else(|_| CString::new("unknown error").unwrap());
        let p = CString::into_raw(cs) as u64;
        // Pack error_type in top byte, pointer in low 56 bits.
        // On standard x86_64/aarch64 userspace, pointers use at most 48 bits.
        let pk = ((et as u64) << 56) | (p & 0x00FFFFFFFFFFFFFF);
        let mut b = self.buffer.lock().unwrap();
        b.extend_from_slice(&cid.to_ne_bytes());
        b.extend_from_slice(&(rid as u64).to_ne_bytes());
        b.extend_from_slice(&0u64.to_ne_bytes());
        b.extend_from_slice(&pk.to_ne_bytes());
        drop(b);
        self.condvar.notify_one();
    }
    /// Push a pubsub message with inline data (no heap pointer transfer).
    /// Format: cid(8) sentinel(8) total_len(8) unused(8) + kind(4) msg_len(4) msg(...) ch_len(4) ch(...) pat_len(4) pat(...)
    /// The 32-byte header is followed by variable-length payload.
    fn push_pubsub_inline(
        &self,
        cid: u64,
        kind: i32,
        message: &[u8],
        channel: &[u8],
        pattern: &[u8],
    ) {
        let payload_len = 4 + 4 + message.len() + 4 + channel.len() + 4 + pattern.len();
        let mut b = self.buffer.lock().unwrap();
        // 32-byte frame header
        b.extend_from_slice(&cid.to_ne_bytes());
        b.extend_from_slice(&u64::MAX.to_ne_bytes()); // sentinel
        b.extend_from_slice(&(payload_len as u64).to_ne_bytes()); // total payload len
        b.extend_from_slice(&0u64.to_ne_bytes()); // unused
        // Inline payload
        b.extend_from_slice(&kind.to_ne_bytes());
        b.extend_from_slice(&(message.len() as u32).to_ne_bytes());
        b.extend_from_slice(message);
        b.extend_from_slice(&(channel.len() as u32).to_ne_bytes());
        b.extend_from_slice(channel);
        b.extend_from_slice(&(pattern.len() as u32).to_ne_bytes());
        b.extend_from_slice(pattern);
        drop(b);
        self.condvar.notify_one();
    }

    /// Push a large pubsub message as a heap-allocated flat buffer.
    /// Same wire format as inline payload but delivered via pointer to avoid
    /// blocking the pipe for other clients during multi-read accumulation.
    /// Python frees via free_pubsub_pointer_payload(ptr, len).
    fn push_pubsub_pointer(
        &self,
        cid: u64,
        kind: i32,
        message: &[u8],
        channel: &[u8],
        pattern: &[u8],
    ) {
        let payload_len = 4 + 4 + message.len() + 4 + channel.len() + 4 + pattern.len();
        let mut buf = Vec::with_capacity(payload_len);
        buf.extend_from_slice(&kind.to_ne_bytes());
        buf.extend_from_slice(&(message.len() as u32).to_ne_bytes());
        buf.extend_from_slice(message);
        buf.extend_from_slice(&(channel.len() as u32).to_ne_bytes());
        buf.extend_from_slice(channel);
        buf.extend_from_slice(&(pattern.len() as u32).to_ne_bytes());
        buf.extend_from_slice(pattern);
        let boxed = buf.into_boxed_slice();
        let ptr = Box::into_raw(boxed) as *mut u8 as u64;
        // Pack length with pointer-mode flag in bit 63
        let len_with_flag = (payload_len as u64) | (1u64 << 63);
        let mut b = self.buffer.lock().unwrap();
        b.extend_from_slice(&cid.to_ne_bytes());
        b.extend_from_slice(&u64::MAX.to_ne_bytes()); // sentinel
        b.extend_from_slice(&ptr.to_ne_bytes());
        b.extend_from_slice(&len_with_flag.to_ne_bytes());
        drop(b);
        self.condvar.notify_one();
    }
}

/// No-op success callback — safe to call from any thread (no GIL needed).
#[unsafe(no_mangle)]
pub extern "C" fn noop_success_callback(_index: usize, _msg: *const CommandResponse) {}

/// No-op failure callback — safe to call from any thread (no GIL needed).
#[unsafe(no_mangle)]
pub extern "C" fn noop_failure_callback(
    _index: usize,
    _msg: *const c_char,
    _err_type: RequestErrorType,
) {
}

/// Free a heap-allocated pubsub payload buffer (pointer-mode frame).
///
/// # Safety
/// `ptr` must be a valid pointer from a pointer-mode pubsub frame, or null.
/// `len` must be the original payload length.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_pubsub_pointer_payload(ptr: *mut u8, len: usize) {
    if !ptr.is_null() {
        let _ = unsafe { Box::from_raw(std::ptr::slice_from_raw_parts_mut(ptr, len)) };
    }
}

/// Free an error string delivered via the shared pipe error frame.
///
/// # Safety
/// `ptr` must be a valid pointer returned in a pipe error frame, or null.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_pipe_error_string(ptr: *mut c_char) {
    if !ptr.is_null() {
        let _ = unsafe { CString::from_raw(ptr) };
    }
}

/// Initialize the process-wide shared pipe for async response delivery.
/// Spawns a dedicated OS flush thread with adaptive batching.
///
/// This function is idempotent within a single process — calling it multiple
/// times with the same or different fd is safe (only the first call takes effect).
/// After `fork()`, call [`reinit_async_pipe`] instead to create a fresh pipe
/// and flush thread in the child process.
///
/// # Safety
///
/// * `pipe_write_fd` must be a valid writable pipe file descriptor.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn init_async_pipe(pipe_write_fd: i32) {
    let w = create_pipe_writer(pipe_write_fd);
    let ptr = w as *const SharedPipeWriter as *mut SharedPipeWriter;
    // First-call-wins: only install if currently null.
    if ASYNC_PIPE
        .compare_exchange(
            std::ptr::null_mut(),
            ptr,
            std::sync::atomic::Ordering::AcqRel,
            std::sync::atomic::Ordering::Acquire,
        )
        .is_err()
    {
        // Another call already initialized — the leaked alloc is harmless.
    }
}

/// Reinitialize the async pipe after `fork()`.
///
/// After `fork()`, the flush thread from the parent process is gone but the
/// `ASYNC_PIPE` state is inherited. This function replaces the stale pipe
/// writer with a fresh one backed by the new `pipe_write_fd`, and spawns a
/// new flush thread.
///
/// The old `SharedPipeWriter` is intentionally leaked (not dropped) because
/// the parent's Mutex/Condvar state is in an undefined state post-fork.
///
/// # Safety
///
/// * `pipe_write_fd` must be a valid writable pipe file descriptor.
/// * Must only be called once per child process, before any commands are issued.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn reinit_async_pipe(pipe_write_fd: i32) {
    let w = create_pipe_writer(pipe_write_fd);
    let ptr = w as *const SharedPipeWriter as *mut SharedPipeWriter;
    // Replace the stale writer. The old one is intentionally leaked —
    // its mutex/condvar are in undefined state post-fork.
    ASYNC_PIPE.store(ptr, std::sync::atomic::Ordering::Release);

    // Also reinitialize the timeout watchdog — its thread is dead post-fork.
    glide_core::timeout_watchdog::TimeoutWatchdog::reinit_global();
}

/// Create a new `SharedPipeWriter` and spawn its flush thread.
/// Returns a `&'static` reference by leaking the allocation (lives for
/// the lifetime of the process).
fn create_pipe_writer(pipe_write_fd: i32) -> &'static SharedPipeWriter {
    let w = Box::new(SharedPipeWriter {
        buffer: std::sync::Mutex::new(Vec::with_capacity(FRAME_SIZE * 64)),
        condvar: Condvar::new(),
        pipe_fd: pipe_write_fd,
    });
    // Leak to get 'static lifetime — the pipe writer lives for the process lifetime.
    let w_ref: &'static SharedPipeWriter = Box::leak(w);
    let fd = pipe_write_fd;
    // Spawn flush thread. `w_ref` is &'static and SharedPipeWriter is Sync,
    // so sharing across threads is safe.
    let sw_ref = w_ref;
    std::thread::Builder::new()
        .name("glide-async-pipe-flush".into())
        .spawn(move || {
            let flush_threshold: usize = std::env::var("GLIDE_PIPE_FLUSH_THRESHOLD")
                .ok()
                .and_then(|v| v.parse().ok())
                .unwrap_or(1);
            let sw = sw_ref;
            loop {
                let data = {
                    let mut buf = sw.buffer.lock().unwrap();
                    while buf.is_empty() {
                        buf = sw.condvar.wait(buf).unwrap();
                    }
                    let fc = buf.len() / FRAME_SIZE;
                    if fc <= flush_threshold {
                        let mut d = Vec::with_capacity(FRAME_SIZE * 4);
                        std::mem::swap(&mut *buf, &mut d);
                        d
                    } else {
                        drop(buf);
                        std::thread::yield_now();
                        let mut buf = sw.buffer.lock().unwrap();
                        let mut d = Vec::with_capacity(FRAME_SIZE * 64);
                        std::mem::swap(&mut *buf, &mut d);
                        d
                    }
                };
                if data.is_empty() {
                    continue;
                }
                let mut off = 0;
                while off < data.len() {
                    let w = unsafe {
                        libc::write(
                            fd,
                            data[off..].as_ptr() as *const libc::c_void,
                            data.len() - off,
                        )
                    };
                    if w > 0 {
                        off += w as usize;
                    } else if w == 0 {
                        break;
                    } else {
                        let e = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
                        if e == libc::EINTR || e == libc::EAGAIN {
                            continue;
                        }
                        // EPIPE/EBADF — fd is gone, exit the flush thread.
                        return;
                    }
                }
            }
        })
        .expect("flush thread");
    w_ref
}

/// A `GlideClient` adapter.
pub struct ClientAdapter {
    runtime: Runtime,
    pipe_client_id: std::sync::atomic::AtomicU64,
    /// Background runtime for spawned tasks (connection drivers, reconnection, cluster manager).
    /// Only used by sync clients with current_thread main runtime — tokio::spawn calls during
    /// client creation are directed here via _guard so they run independently of block_on.
    /// For async/multi_thread clients this is None since the main runtime handles everything.
    background_runtime: Option<Runtime>,
    core: Arc<CommandExecutionCore>,
    pubsub_callback: Arc<std::sync::RwLock<Option<PubSubCallback>>>,
}

struct CommandExecutionCore {
    client: GlideClient,
    client_type: ClientType,
}

impl ClientAdapter {
    /// Executes a command and routes the result based on client type.
    ///
    /// For async clients, spawns the future and returns null immediately.
    /// For sync clients, blocks on the future and returns a `CommandResult`.
    #[must_use]
    fn execute_request<Fut>(&self, request_id: usize, request_future: Fut) -> *mut CommandResult
    where
        Fut: Future<Output = RedisResult<Value>> + Send + 'static,
    {
        self.execute_request_with_buffer(request_id, request_future, None)
    }

    fn execute_request_with_buffer<Fut>(
        &self,
        request_id: usize,
        request_future: Fut,
        response_buf: Option<ResponseBuffer>,
    ) -> *mut CommandResult
    where
        Fut: Future<Output = RedisResult<Value>> + Send + 'static,
    {
        match self.core.client_type {
            ClientType::AsyncClient {
                success_callback,
                failure_callback,
                allow_stack_response,
            } => {
                let cid = self
                    .pipe_client_id
                    .load(std::sync::atomic::Ordering::Relaxed);
                if cid != 0 && get_async_pipe().is_some() {
                    self.runtime.spawn(async move {
                        match request_future.await {
                            Ok(value) => {
                                let buf: &[(*mut u8, usize)] =
                                    response_buf.as_ref().map(|rb| rb.as_slice()).unwrap_or(&[]);
                                match valkey_value_to_arena_response(value, buf) {
                                    Ok((root_ptr, arena_ptr)) => {
                                        if let Some(w) = get_async_pipe() {
                                            w.push_success(
                                                cid,
                                                request_id,
                                                root_ptr as usize,
                                                arena_ptr as usize,
                                            );
                                        }
                                    }
                                    Err(err) => {
                                        if let Some(w) = get_async_pipe() {
                                            w.push_error(
                                                cid,
                                                request_id,
                                                errors::error_type(&err),
                                                errors::error_message(&err),
                                            );
                                        }
                                    }
                                }
                            }
                            Err(err) => {
                                if let Some(w) = get_async_pipe() {
                                    w.push_error(
                                        cid,
                                        request_id,
                                        errors::error_type(&err),
                                        errors::error_message(&err),
                                    );
                                }
                            }
                        }
                    });
                    return std::ptr::null_mut();
                }
                self.runtime.spawn(async move {
                    let result = request_future.await;
                    let _ = Self::handle_result(
                        result,
                        Some(success_callback),
                        Some(failure_callback),
                        request_id,
                        response_buf,
                        allow_stack_response,
                    );
                });
                std::ptr::null_mut()
            }
            ClientType::SyncClient => {
                // Enter background runtime context inside the future so tokio::spawn
                // calls within the command (e.g. reconnection, lazy subscribe) land
                // on the background runtime where TCP I/O is registered.
                // Note: block_on overrides any context entered before it, so the
                // guard must be held inside the future being polled.
                let bg = self
                    .background_runtime
                    .as_ref()
                    .map(|rt| rt.handle().clone());
                let result = self.runtime.block_on(async {
                    let _guard = bg.as_ref().map(|h| h.enter());
                    request_future.await
                });
                Self::handle_result(result, None, None, request_id, response_buf, false)
            }
        }
    }

    /// Handles the result of a command and returns a `CommandResult`.
    ///
    /// For async clients, invokes the appropriate callback and returns null.
    /// For sync clients, returns a `CommandResult`.
    // TODO SAFETY
    #[must_use]
    fn handle_result(
        result: RedisResult<Value>,
        success_callback: Option<SuccessCallback>,
        failure_callback: Option<FailureCallback>,
        request_id: usize,
        response_buf: Option<ResponseBuffer>,
        allow_stack_response: bool,
    ) -> *mut CommandResult {
        match result {
            Ok(value) => {
                let buf: &[(*mut u8, usize)] =
                    response_buf.as_ref().map(|rb| rb.as_slice()).unwrap_or(&[]);
                if let Some(success_callback) = success_callback {
                    // Stack fast-path for simple types: avoids arena allocation.
                    // Only used when the caller opts in (allow_stack_response=true),
                    // meaning the callback copies all data and never frees the pointer.
                    if allow_stack_response {
                        match &value {
                            Value::Okay
                            | Value::Nil
                            | Value::Int(_)
                            | Value::Double(_)
                            | Value::Boolean(_)
                            | Value::BulkString(_)
                            | Value::SimpleString(_) => {
                                let mut resp = CommandResponse::default();
                                match &value {
                                    Value::Okay => resp.response_type = ResponseType::Ok,
                                    Value::Nil => {}
                                    Value::Int(n) => {
                                        resp.response_type = ResponseType::Int;
                                        resp.int_value = *n;
                                    }
                                    Value::Double(n) => {
                                        resp.response_type = ResponseType::Float;
                                        resp.float_value = *n;
                                    }
                                    Value::Boolean(b) => {
                                        resp.response_type = ResponseType::Bool;
                                        resp.bool_value = *b;
                                    }
                                    Value::BulkString(data) => {
                                        resp.response_type = ResponseType::String;
                                        resp.string_value = data.as_ptr() as *mut c_char;
                                        resp.string_value_len = data.len() as c_long;
                                    }
                                    Value::SimpleString(text) => {
                                        resp.response_type = ResponseType::String;
                                        resp.string_value = text.as_ptr() as *mut c_char;
                                        resp.string_value_len = text.len() as c_long;
                                    }
                                    _ => unreachable!(),
                                }
                                unsafe { (success_callback)(request_id, &resp as *const _) };
                                // Debug canary: poison the stack response after the
                                // callback returns so that any binding that stashed the
                                // pointer instead of copying will read obvious garbage
                                // and trip sanitizers / debug assertions.
                                #[cfg(debug_assertions)]
                                unsafe {
                                    std::ptr::write_bytes(
                                        &mut resp as *mut CommandResponse as *mut u8,
                                        0xDE,
                                        std::mem::size_of::<CommandResponse>(),
                                    );
                                }
                                return std::ptr::null_mut();
                            }
                            _ => {}
                        }
                    }
                    // Heap path: arena-allocated response
                    match valkey_value_to_arena_response(value, buf) {
                        Ok((root_ptr, _arena_ptr)) => {
                            unsafe { (success_callback)(request_id, root_ptr) };
                        }
                        Err(err) => {
                            if let Some(failure_callback) = failure_callback {
                                unsafe {
                                    Self::send_async_redis_error(failure_callback, err, request_id)
                                };
                            }
                        }
                    }
                } else {
                    // Sync path: always use arena
                    match valkey_value_to_arena_response(value, buf) {
                        Ok((root_ptr, _arena_ptr)) => {
                            return Box::into_raw(Box::new(CommandResult {
                                response: root_ptr,
                                command_error: std::ptr::null_mut(),
                                arena: _arena_ptr,
                            }));
                        }
                        Err(err) => {
                            eprintln!("Error converting value to CommandResponse: {err:?}");
                            return create_error_result_with_redis_error(err);
                        }
                    }
                }
            }
            Err(err) => {
                if let Some(failure_callback) = failure_callback {
                    unsafe { Self::send_async_redis_error(failure_callback, err, request_id) };
                } else {
                    eprintln!("Error executing command: {err:?}");
                    return create_error_result_with_redis_error(err);
                }
            }
        };
        std::ptr::null_mut()
    }

    /// Handles a Redis error by either invoking the failure callback (for async clients)
    /// or returning a heap-allocated `CommandResult` (for sync clients).
    ///
    /// This method ensures consistent error handling logic across both client types.
    ///
    /// # Parameters
    /// - `err`: The `RedisError` to handle.
    /// - `request_id`: The unique ID associated with the request.
    ///
    /// # Returns
    /// - For async clients: Returns a null pointer after invoking the failure callback.
    /// - For sync clients: Returns a pointer to a `CommandResult` containing the error.
    ///
    /// # Safety
    /// Unsafe, because calls to an FFI function. See the safety documentation of [`Self::handle_custom_error`].
    #[must_use]
    unsafe fn handle_redis_error(&self, err: RedisError, request_id: usize) -> *mut CommandResult {
        let error_string = errors::error_message(&err);
        let error_type = errors::error_type(&err);
        unsafe { Self::handle_custom_error(self, error_string, error_type, request_id) }
    }

    /// Handles a Redis error by either invoking the failure callback (for async clients)
    /// or returning a heap-allocated `CommandResult` (for sync clients).
    ///
    /// This method ensures consistent error handling logic across both client types.
    ///
    /// # Parameters
    /// - `error_string`: The error to handle.
    /// - `error_type`: The error type.
    /// - `request_id`: The unique ID associated with the request.
    ///
    /// # Returns
    /// - For async clients: Returns a null pointer after invoking the failure callback.
    /// - For sync clients: Returns a pointer to a `CommandResult` containing the error.
    ///
    /// # Safety
    /// Unsafe, because calls to an FFI function. See the safety documentation of [`Self::send_async_custom_error`].
    unsafe fn handle_custom_error(
        &self,
        error_string: String,
        error_type: RequestErrorType,
        request_id: usize,
    ) -> *mut CommandResult {
        match self.core.client_type {
            ClientType::AsyncClient {
                success_callback: _,
                failure_callback,
                allow_stack_response: _,
            } => {
                let cid = self
                    .pipe_client_id
                    .load(std::sync::atomic::Ordering::Relaxed);
                if cid != 0 && get_async_pipe().is_some() {
                    if let Some(w) = get_async_pipe() {
                        w.push_error(cid, request_id, error_type, error_string);
                    }
                } else {
                    unsafe {
                        Self::send_async_custom_error(
                            failure_callback,
                            error_string,
                            error_type,
                            request_id,
                        )
                    };
                }
                std::ptr::null_mut()
            }
            ClientType::SyncClient => {
                create_error_result_with_custom_error(error_string, error_type)
            }
        }
    }

    /// Invokes the asynchronous failure callback with an error.
    ///
    /// This function is used in async client flows to report command execution failures
    /// back to the calling language (e.g. Go) via a registered failure callback.
    ///
    /// # Parameters
    /// - `failure_callback`: The callback to invoke with the error.
    /// - `error_string`: The error message to report.
    /// - `error_type`: The error type to report.
    /// - `request_id`: An identifier used to correlate the error to the original request.
    ///
    /// # Safety
    /// Unsafe, because calls to an FFI function. See the safety documentation of [`FailureCallback`].
    unsafe fn send_async_custom_error(
        failure_callback: FailureCallback,
        error_string: String,
        error_type: RequestErrorType,
        request_id: usize,
    ) {
        let err_ptr = CString::into_raw(
            CString::new(error_string).expect("Couldn't convert error message to CString"),
        );
        unsafe { (failure_callback)(request_id, err_ptr, error_type) };
        _ = unsafe { CString::from_raw(err_ptr as *mut c_char) };
    }

    /// Invokes the asynchronous failure callback with an error.
    ///
    /// This function is used in async client flows to report command execution failures
    /// back to the calling language (e.g. Go) via a registered failure callback.
    ///
    /// # Parameters
    /// - `failure_callback`: The callback to invoke with the error.
    /// - `err`: The `RedisError` to report.
    /// - `request_id`: An identifier used to correlate the error to the original request.
    ///
    /// # Safety
    /// Unsafe, because calls to an FFI function. See the safety documentation of [`FailureCallback`].
    unsafe fn send_async_redis_error(
        failure_callback: FailureCallback,
        err: RedisError,
        request_id: usize,
    ) {
        let (c_err_str, error_type) = to_c_error(err);
        unsafe { (failure_callback)(request_id, c_err_str, error_type) };
        _ = unsafe { CString::from_raw(c_err_str as *mut c_char) };
    }
}

#[repr(C)]
#[derive(Debug, Clone)]
pub enum PushKind {
    PushDisconnection,
    PushOther,
    PushInvalidate,
    PushMessage,
    PushPMessage,
    PushSMessage,
    PushUnsubscribe,
    PushPUnsubscribe,
    PushSUnsubscribe,
    PushSubscribe,
    PushPSubscribe,
    PushSSubscribe,
}

impl From<redis::PushKind> for PushKind {
    fn from(value: redis::PushKind) -> Self {
        match value {
            redis::PushKind::Disconnection => PushKind::PushDisconnection,
            redis::PushKind::Other(_) => PushKind::PushOther,
            redis::PushKind::Invalidate => PushKind::PushInvalidate,
            redis::PushKind::Message => PushKind::PushMessage,
            redis::PushKind::PMessage => PushKind::PushPMessage,
            redis::PushKind::SMessage => PushKind::PushSMessage,
            redis::PushKind::Unsubscribe => PushKind::PushUnsubscribe,
            redis::PushKind::PUnsubscribe => PushKind::PushPUnsubscribe,
            redis::PushKind::SUnsubscribe => PushKind::PushSUnsubscribe,
            redis::PushKind::Subscribe => PushKind::PushSubscribe,
            redis::PushKind::PSubscribe => PushKind::PushPSubscribe,
            redis::PushKind::SSubscribe => PushKind::PushSSubscribe,
        }
    }
}

/// Processes a push notification message and calls the provided callback function.
///
/// This function converts a PushInfo message to a CommandResponse, determines the
/// notification type, and invokes the callback with the appropriate parameters.
///
/// # Parameters
/// - `push_msg`: The push notification message to process.
/// - `pubsub_callback`: The callback function to invoke with the processed notification.
/// - `client_adapter_ptr`: A pointer to the client adapter to pass to the callback.
///
/// # Returns
/// - `true` if the message was successfully processed and the callback was called.
/// - `false` if there was an error processing the message (e.g., conversion failed).
///
/// # Safety
/// Extract pubsub message/channel/pattern bytes from a PushInfo.
/// Returns (message, channel, pattern) as owned byte vectors.
#[allow(clippy::type_complexity)]
fn extract_pubsub_data(push_msg: &redis::PushInfo) -> Option<(Vec<u8>, Vec<u8>, Option<Vec<u8>>)> {
    let strings: Vec<&[u8]> = push_msg
        .data
        .iter()
        .filter_map(|v| {
            if let Value::BulkString(s) = v {
                Some(s.as_ref())
            } else {
                None
            }
        })
        .collect();

    if strings.len() >= 3 {
        Some((
            strings[2].to_vec(),
            strings[1].to_vec(),
            Some(strings[0].to_vec()),
        ))
    } else if strings.len() == 2 {
        Some((strings[1].to_vec(), strings[0].to_vec(), None))
    } else {
        None
    }
}

/// Process a push notification by invoking the pubsub callback with extracted message data.
///
/// # Safety
/// This function is unsafe because it:
/// - Dereferences raw pointers (the callback and client_adapter_ptr)
/// - Calls an extern C function pointer (pubsub_callback)
/// - Passes raw pointers that must remain valid for the callback duration
/// - The caller must ensure client_adapter_ptr points to a valid ClientAdapter
unsafe fn process_push_notification(
    push_msg: redis::PushInfo,
    pubsub_callback: PubSubCallback,
    client_adapter_ptr: usize,
) {
    let (message, channel, pattern) = if push_msg.kind == redis::PushKind::Disconnection {
        (vec![], vec![], None)
    } else {
        let Some(data) = extract_pubsub_data(&push_msg) else {
            return;
        };
        data
    };

    let (message_ptr, message_len) = convert_vec_to_pointer(message);
    let (channel_ptr, channel_len) = convert_vec_to_pointer(channel);
    let (pattern_ptr, pattern_len) = match pattern {
        Some(p) => convert_vec_to_pointer(p),
        None => (std::ptr::null_mut::<u8>(), 0),
    };

    unsafe {
        pubsub_callback(
            client_adapter_ptr,
            push_msg.kind.into(),
            message_ptr,
            message_len,
            channel_ptr,
            channel_len,
            pattern_ptr,
            pattern_len,
        );
        let _ = Box::from_raw(std::ptr::slice_from_raw_parts_mut(
            message_ptr,
            message_len as usize,
        ));
        let _ = Box::from_raw(std::ptr::slice_from_raw_parts_mut(
            channel_ptr,
            channel_len as usize,
        ));
        if !pattern_ptr.is_null() {
            let _ = Box::from_raw(std::ptr::slice_from_raw_parts_mut(
                pattern_ptr,
                pattern_len as usize,
            ));
        }
    }
}

fn create_client_internal(
    connection_request_bytes: &[u8],
    client_type: ClientType,
    pubsub_callback: Option<PubSubCallback>,
    address_resolver: Option<AddressResolverCallback>,
    client_id: usize,
) -> Result<*const ClientAdapter, String> {
    let request = connection_request::ConnectionRequest::parse_from_bytes(connection_request_bytes)
        .map_err(|err| err.to_string())?;
    let runtime = match &client_type {
        ClientType::SyncClient => {
            // current_thread runtime: block_on drives the reactor directly on the
            // calling thread, eliminating the condvar park/wake overhead that occurs
            // with multi_thread when the calling thread waits for a worker.
            Builder::new_current_thread()
                .enable_all()
                .build()
                .map_err(|err| {
                    let redis_error: redis::RedisError = err.into();
                    errors::error_message(&redis_error)
                })?
        }
        ClientType::AsyncClient { .. } => {
            // Async clients need a background worker thread to drive the reactor
            // since the calling thread is owned by the foreign language's event loop.
            // GLIDE_TOKIO_WORKER_THREADS controls the number of tokio worker threads
            // (default 1). More workers can help concurrent large-response workloads.
            let worker_threads = std::env::var("GLIDE_TOKIO_WORKER_THREADS")
                .ok()
                .and_then(|s| s.parse::<usize>().ok())
                .unwrap_or(1)
                .max(1);
            Builder::new_multi_thread()
                .enable_all()
                .worker_threads(worker_threads)
                .thread_name("Valkey-GLIDE thread")
                .build()
                .map_err(|err| {
                    let redis_error: redis::RedisError = err.into();
                    errors::error_message(&redis_error)
                })?
        }
    };

    // For sync clients, create a background runtime for spawned tasks (connection
    // drivers, reconnection, cluster manager). Enter its context during client
    // creation so tokio::spawn calls land on the background runtime, not the
    // current_thread runtime which only polls tasks inside block_on.
    let background_runtime = match &client_type {
        ClientType::SyncClient => Some(
            Builder::new_multi_thread()
                .enable_all()
                .worker_threads(1)
                .thread_name("Valkey-GLIDE background")
                .build()
                .map_err(|err| {
                    let redis_error: redis::RedisError = err.into();
                    errors::error_message(&redis_error)
                })?,
        ),
        _ => None,
    };

    // Always create push channels to support dynamic pubsub
    let (push_tx, mut push_rx) = tokio::sync::mpsc::unbounded_channel();

    let client = {
        // Create the client on the background runtime so all TCP I/O and spawned
        // tasks (connection drivers, cluster manager) are registered there.
        // The current_thread runtime is only used for block_on in the command path.
        let create_rt = background_runtime.as_ref().unwrap_or(&runtime);
        let mut connection_request = ConnectionRequest::from(request);

        // Set the address resolver if provided
        if let Some(resolver_callback) = address_resolver {
            connection_request.address_resolver = Some(Arc::new(FFIAddressResolver {
                callback: resolver_callback,
                client_id,
            }));
        }

        create_rt
            .block_on(GlideClient::new(connection_request, Some(push_tx)))
            .map_err(|err| err.to_string())?
    };

    // Create the client adapter that will be returned and used as conn_ptr
    let is_sync = matches!(client_type, ClientType::SyncClient);
    let core = Arc::new(CommandExecutionCore {
        client,
        client_type,
    });
    let pubsub_callback_store = Arc::new(std::sync::RwLock::new(pubsub_callback));
    let client_adapter = Arc::new(ClientAdapter {
        runtime,
        pipe_client_id: std::sync::atomic::AtomicU64::new(client_id as u64),
        background_runtime,
        core,
        pubsub_callback: pubsub_callback_store.clone(),
    });
    let client_adapter_ptr = Arc::as_ptr(&client_adapter).addr();

    // Spawn push handler on the background runtime (if any) so it runs
    // independently of block_on, otherwise on the main runtime.
    let spawn_runtime = client_adapter
        .background_runtime
        .as_ref()
        .unwrap_or(&client_adapter.runtime);
    let callback_store = pubsub_callback_store.clone();
    let pipe_cid = client_id as u64;
    if is_sync {
        // Sync clients: direct callback (CFFI acquires GIL automatically).
        spawn_runtime.spawn(async move {
            while let Some(push_msg) = push_rx.recv().await {
                if (push_msg.kind == redis::PushKind::Message
                    || push_msg.kind == redis::PushKind::PMessage
                    || push_msg.kind == redis::PushKind::SMessage)
                    && let Ok(guard) = callback_store.read()
                    && let Some(callback) = *guard
                {
                    unsafe {
                        process_push_notification(push_msg, callback, client_adapter_ptr);
                    }
                }
            }
        });
    } else {
        // Async clients: route through ASYNC_PIPE.
        spawn_runtime.spawn(async move {
            while let Some(push_msg) = push_rx.recv().await {
                if pipe_cid != 0 {
                    // Wait for ASYNC_PIPE if not yet initialized (brief spin during startup)
                    let w = loop {
                        if let Some(w) = get_async_pipe() {
                            break w;
                        }
                        std::hint::spin_loop();
                    };
                    if push_msg.kind == redis::PushKind::Disconnection {
                        let kind: i32 = PushKind::from(push_msg.kind) as i32;
                        w.push_pubsub_inline(pipe_cid, kind, &[], &[], &[]);
                    } else if (push_msg.kind == redis::PushKind::Message
                        || push_msg.kind == redis::PushKind::PMessage
                        || push_msg.kind == redis::PushKind::SMessage)
                        && let Some((message, channel, pattern)) = extract_pubsub_data(&push_msg)
                    {
                        let kind: i32 = PushKind::from(push_msg.kind) as i32;
                        let pat_slice = pattern.as_deref().unwrap_or(&[]);
                        let total_len = message.len() + channel.len() + pat_slice.len();
                        if total_len > MAX_INLINE_PUBSUB {
                            w.push_pubsub_pointer(pipe_cid, kind, &message, &channel, pat_slice);
                        } else {
                            w.push_pubsub_inline(pipe_cid, kind, &message, &channel, pat_slice);
                        }
                    }
                    continue;
                }
                // Fallback: direct callback (Go/other languages)
                if (push_msg.kind == redis::PushKind::Message
                    || push_msg.kind == redis::PushKind::PMessage
                    || push_msg.kind == redis::PushKind::SMessage)
                    && let Ok(guard) = callback_store.read()
                    && let Some(callback) = *guard
                {
                    unsafe {
                        process_push_notification(push_msg, callback, client_adapter_ptr);
                    }
                }
            }
        });
    }

    // Register client in scope registry so scoped connections can find their
    // parent client for compression, timeout, inflight, and CB checks.
    #[cfg(feature = "pool-support")]
    {
        let client_clone = client_adapter.core.client.clone();
        glide_core::scope::register_client(client_adapter_ptr as u64, client_clone);
    }

    Ok(Arc::into_raw(client_adapter))
}

/// Creates a new `ClientAdapter` with a new `GlideClient` configured using a Protobuf `ConnectionRequest`.
///
/// The returned `ConnectionResponse` will only be freed by calling [`free_connection_response`].
///
/// `connection_request_bytes` is an array of bytes that will be parsed into a Protobuf `ConnectionRequest` object.
/// `connection_request_len` is the number of bytes in `connection_request_bytes`.
/// `success_callback` is the callback that will be called when a command succeeds.
/// `failure_callback` is the callback that will be called when a command fails.
/// `pubsub_callback` is an optional callback for pubsub messages. Pass 0 (null) to create a client without pubsub support.
///
/// # Safety
///
/// * `connection_request_bytes` must point to `connection_request_len` consecutive properly initialized bytes. It must be a well-formed Protobuf `ConnectionRequest` object. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `connection_request_len` must not be greater than the length of the connection request bytes array. It must also not be greater than the max value of a signed pointer-sized integer.
/// * The `conn_ptr` pointer in the returned `ConnectionResponse` must live while the client is open/active and must be explicitly freed by calling [`close_client``].
/// * The `connection_error_message` pointer in the returned `ConnectionResponse` must live until the returned `ConnectionResponse` pointer is passed to [`free_connection_response``].
/// * Both the `success_callback` and `failure_callback` function pointers need to live while the client is open/active. The caller is responsible for freeing both callbacks.
/// * If `pubsub_callback` is non-zero, it must be a valid function pointer that lives while the client is open/active.
// TODO: Consider making this async
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn create_client(
    connection_request_bytes: *const u8,
    connection_request_len: usize,
    client_type: *const ClientType,
    pubsub_callback: PubSubCallback,
    address_resolver: AddressResolverCallback,
    client_id: usize,
) -> *const ConnectionResponse {
    assert!(!connection_request_bytes.is_null());
    let request_bytes =
        unsafe { std::slice::from_raw_parts(connection_request_bytes, connection_request_len) };
    let client_type = unsafe { &*client_type };

    // Convert callback pointer to Option - 0 means no callback
    let callback_opt = if pubsub_callback as usize == 0 {
        None
    } else {
        Some(pubsub_callback)
    };

    // Convert address resolver pointer to Option - 0 means no resolver
    let resolver_opt = if address_resolver as usize == 0 {
        None
    } else {
        Some(address_resolver)
    };

    let response = match create_client_internal(
        request_bytes,
        client_type.clone(),
        callback_opt,
        resolver_opt,
        client_id,
    ) {
        Err(err) => ConnectionResponse {
            conn_ptr: std::ptr::null(),
            connection_error_message: CString::into_raw(
                CString::new(err).expect("Couldn't convert error message to CString"),
            ),
        },
        Ok(client) => ConnectionResponse {
            conn_ptr: client as *const c_void,
            connection_error_message: std::ptr::null(),
        },
    };
    Box::into_raw(Box::new(response))
}

/// Creates a new client from a URI string and optional JSON configuration.
///
/// This is an alternative to [`create_client`] that accepts a connection URI instead of protobuf bytes.
/// The URI parsing and configuration building happens in Rust, making it easier to add new connection
/// options without changing FFI signatures across all language bindings.
///
/// # Parameters
///
/// * `uri_str`: A null-terminated C string containing the connection URI.
///   Preferred format: `valkey://` (or `valkey+unix://` for Unix sockets) and `valkeys://` for TLS. `redis://`, `redis+unix://`, and `rediss://` are accepted for compatibility.
///   Examples:
///   - `valkey://localhost:6379`
///   - `valkeys://:password@example.com:6380/0`
///   - `valkey://user:pass@localhost:6379/1`
///   - `redis://localhost:6379` (Redis-compatible alias)
///
/// * `extra_options_json`: Optional null-terminated C string containing additional connection options as JSON.
///   Can be null if no extra options are needed. Supported options include:
///   - `request_timeout`: Request timeout in milliseconds (u32)
///   - `connection_timeout`: Connection timeout in milliseconds (u32)
///   - `client_name`: Client name string (string)
///   - `cluster_mode_enabled`: Boolean for cluster mode (bool)
///   - `refresh_topology_from_initial_nodes`: When cluster mode is enabled, refresh topology using only the initial seed nodes (bool)
///   - `protocol`: Protocol version - "RESP2" or "RESP3" (string)
///   - `read_from`: Read routing - "Primary", "PreferReplica", "LowestLatency", "AZAffinity", "AZAffinityReplicasAndPrimary", "AllNodes", or "AZAffinityAllNodes" (string)
///   - `connection_retry_strategy`: Retry configuration with `number_of_retries`, `factor`, `exponent_base`, and optional `jitter_percent` (object)
///   - `root_certs`: Array of PEM-encoded CA certificates for TLS (array of strings)
///   - `client_az`: Client availability zone for AZ affinity routing (string)
///   - `database_id`: Database number to select (u32, overrides URI database)
///   - `inflight_requests_limit`: Maximum concurrent requests (u32)
///   - `tls_mode`: TLS mode - "NoTls", "SecureTls", or "InsecureTls" (string, overrides URI scheme)
///   - `client_cert`: PEM-encoded client certificate for mutual TLS (string)
///   - `client_key`: PEM-encoded client private key for mutual TLS (string)
///   - `lib_name`: Library name identifier (string)
///   - `tcp_nodelay`: Enable TCP_NODELAY option (bool)
///   - `lazy_connect`: Delay connection until first command (bool)
///   - `read_only`: Standalone read-only client mode (bool)
///   - `pubsub_reconciliation_interval_ms`: Interval for pub/sub reconnection checks in milliseconds (u32)
///   - `compression_config`: Compression settings with `enabled` (bool), `backend` ("ZSTD" or "LZ4"), optional `compression_level` (i32), and `min_compression_size` (u32) (object)
///   - `periodic_checks`: Health check configuration with either `manual_interval` (object with `duration_in_sec`) or `disabled` (bool) (object)
///   - `iam_credentials`: AWS IAM authentication with `cluster_name`, `region`, `service_type` ("ELASTICACHE" or "MEMORYDB"), and optional `refresh_interval_seconds` (object)
///   - `pubsub_subscriptions`: Pre-subscribe to channels on connection - map of channel type (0=Exact, 1=Pattern, 2=Sharded) to array of channel names (object)
///   - `client_side_cache`: Client-side caching configuration with `max_cache_kb` (u64, required), `entry_ttl_ms` (u64, required, 0 = no expiration), optional `eviction_policy` ("LRU" or "LFU", defaults to LRU), and optional `enable_metrics` (bool, defaults to false). The `cache_id` is auto-generated internally. (object)
///
/// * `client_type`: Type of client to create (sync/async).
///
/// * `pubsub_callback`: Optional callback function for pub/sub messages (0 for none).
///
/// # Returns
///
/// A pointer to a `ConnectionResponse`. The caller must call [`free_connection_response`] when done.
///
/// # Examples
///
/// ```c
/// // Simple URI
/// create_client_from_uri("valkey://localhost:6379", NULL, &client_type, 0);
///
/// // URI with basic options
/// const char* basic_opts = "{\"request_timeout\": 5000, \"client_name\": \"myapp\"}";
/// create_client_from_uri("valkey://localhost:6379", basic_opts, &client_type, 0);
///
/// // URI with advanced options
/// const char* advanced_opts = "{"
///     "\"request_timeout\": 5000,"
///     "\"client_name\": \"myapp\","
///     "\"read_from\": \"PreferReplica\","
///     "\"connection_retry_strategy\": {"
///         "\"number_of_retries\": 5,"
///         "\"factor\": 2,"
///         "\"exponent_base\": 2"
///     "},"
///     "\"client_az\": \"us-west-2a\""
/// "}";
/// create_client_from_uri("valkey://localhost:6379", advanced_opts, &client_type, 0);
///
/// // TLS with custom CA certificates
/// const char* tls_opts = "{"
///     "\"root_certs\": [\"-----BEGIN CERTIFICATE-----\\n...\"],"
///     "\"client_cert\": \"-----BEGIN CERTIFICATE-----\\n...\","
///     "\"client_key\": \"-----BEGIN PRIVATE KEY-----\\n...\""
/// "}";
/// create_client_from_uri("valkeys://secure.example.com:6380", tls_opts, &client_type, 0);
///
/// // Compression and periodic checks
/// const char* compression_opts = "{"
///     "\"compression_config\": {"
///         "\"enabled\": true,"
///         "\"backend\": \"ZSTD\","
///         "\"compression_level\": 3,"
///         "\"min_compression_size\": 1024"
///     "},"
///     "\"periodic_checks\": {"
///         "\"manual_interval\": {\"duration_in_sec\": 30}"
///     "}"
/// "}";
/// create_client_from_uri("valkey://localhost:6379", compression_opts, &client_type, 0);
///
/// // AWS IAM authentication
/// const char* iam_opts = "{"
///     "\"iam_credentials\": {"
///         "\"cluster_name\": \"my-cluster\","
///         "\"region\": \"us-east-1\","
///         "\"service_type\": \"ELASTICACHE\","
///         "\"refresh_interval_seconds\": 900"
///     "}"
/// "}";
/// create_client_from_uri("valkey://my-cluster.aws.com:6379", iam_opts, &client_type, 0);
///
/// // Pre-subscribe to pub/sub channels
/// const char* pubsub_opts = "{"
///     "\"pubsub_subscriptions\": {"
///         "\"0\": [\"news\", \"updates\"],"  // Exact channels
///         "\"1\": [\"events:*\"],"          // Pattern
///         "\"2\": [\"shard-channel\"]"      // Sharded
///     "}"
/// "}";
/// create_client_from_uri("valkey://localhost:6379", pubsub_opts, &client_type, 0);
///
/// // Client-side caching
/// const char* cache_opts = "{"
///     "\"client_side_cache\": {"
///         "\"max_cache_kb\": 2048,"
///         "\"entry_ttl_ms\": 60000,"
///         "\"eviction_policy\": \"LRU\","
///         "\"enable_metrics\": true"
///     "}"
/// "}";
/// create_client_from_uri("valkey://localhost:6379", cache_opts, &client_type, 0);
/// ```
///
/// # Safety
///
/// * `uri_str` must be a valid null-terminated C string pointer.
/// * If `extra_options_json` is non-null, it must be a valid null-terminated C string containing valid JSON.
/// * `client_type` must be a valid pointer to a `ClientType`.
/// * If `pubsub_callback` is non-zero, it must be a valid function pointer that lives while the client is open/active.
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn create_client_from_uri(
    uri_str: *const c_char,
    extra_options_json: *const c_char,
    client_type: *const ClientType,
    pubsub_callback: PubSubCallback,
) -> *const ConnectionResponse {
    assert!(!uri_str.is_null());
    let client_type = unsafe { &*client_type };

    // Convert callback pointer to Option - 0 means no callback
    let callback_opt = if pubsub_callback as usize == 0 {
        None
    } else {
        Some(pubsub_callback)
    };

    let response = match create_client_from_uri_internal(uri_str, extra_options_json) {
        Err(err) => ConnectionResponse {
            conn_ptr: std::ptr::null(),
            connection_error_message: CString::into_raw(
                CString::new(err).expect("Couldn't convert error message to CString"),
            ),
        },
        Ok(connection_request) => {
            // Convert ConnectionRequest to protobuf bytes to reuse existing logic
            let request_bytes = connection_request
                .write_to_bytes()
                .map_err(|err| format!("Failed to serialize connection request: {}", err));

            match request_bytes {
                Err(err) => ConnectionResponse {
                    conn_ptr: std::ptr::null(),
                    connection_error_message: CString::into_raw(
                        CString::new(err).expect("Couldn't convert error message to CString"),
                    ),
                },
                Ok(bytes) => {
                    match create_client_internal(&bytes, client_type.clone(), callback_opt, None, 0)
                    {
                        Err(err) => ConnectionResponse {
                            conn_ptr: std::ptr::null(),
                            connection_error_message: CString::into_raw(
                                CString::new(err)
                                    .expect("Couldn't convert error message to CString"),
                            ),
                        },
                        Ok(client) => ConnectionResponse {
                            conn_ptr: client as *const c_void,
                            connection_error_message: std::ptr::null(),
                        },
                    }
                }
            }
        }
    };
    Box::into_raw(Box::new(response))
}

/// Normalizes Valkey URI schemes to their Redis equivalents understood by `parse_redis_url`.
/// `valkey://` → `redis://`, `valkeys://` → `rediss://`, `valkey+unix://` → `redis+unix://`
fn normalize_uri_scheme(input: &str) -> Cow<'_, str> {
    let lower = input.to_ascii_lowercase();
    if lower.starts_with("valkeys://") {
        Cow::Owned(format!("rediss://{}", &input["valkeys://".len()..]))
    } else if lower.starts_with("valkey+unix://") {
        Cow::Owned(format!("redis+unix://{}", &input["valkey+unix://".len()..]))
    } else if lower.starts_with("valkey://") {
        Cow::Owned(format!("redis://{}", &input["valkey://".len()..]))
    } else {
        Cow::Borrowed(input)
    }
}

/// Parses a Valkey- or Redis-style connection URI for use with GLIDE.
fn parse_connection_url(input: &str) -> Option<url::Url> {
    redis::parse_redis_url(normalize_uri_scheme(input).as_ref())
}

/// Internal function to parse URI and JSON options into a ConnectionRequest protobuf message.
fn create_client_from_uri_internal(
    uri_str: *const c_char,
    extra_options_json: *const c_char,
) -> Result<connection_request::ConnectionRequest, String> {
    // Parse URI string
    let uri_string = unsafe {
        CStr::from_ptr(uri_str)
            .to_str()
            .map_err(|e| format!("Invalid UTF-8 in URI: {}", e))?
    };

    let url =
        parse_connection_url(uri_string).ok_or_else(|| "Invalid connection URI".to_string())?;

    // Build base ConnectionRequest from URI
    let mut request = connection_request::ConnectionRequest::new();

    // Extract host and port.
    let host = match url.host() {
        Some(url::Host::Domain(domain)) => domain.to_string(),
        Some(url::Host::Ipv4(addr)) => addr.to_string(),
        Some(url::Host::Ipv6(addr)) => addr.to_string(),
        None => return Err("URI missing host".to_string()),
    };
    let port = url.port().unwrap_or(6379) as u32;

    let mut node_address = connection_request::NodeAddress::new();
    node_address.host = host.into();
    node_address.port = port;
    request.addresses.push(node_address);

    // Extract authentication. `url::Url::password()` / `::username()` return the
    // *percent-encoded* substring per RFC 3986 §3.2.1; the caller is expected
    // to decode. Forwarding the encoded form as-is causes AUTH to fail whenever
    // the password contains reserved characters (@, :, /, ?, #, %, +, space,
    // non-ASCII) — see valkey-glide/issues/6659. This mirrors the decode step
    // redis-rs itself performs at glide-core/redis-rs/redis/src/connection.rs:370,379.
    if let Some(password) = url.password() {
        let mut auth_info = connection_request::AuthenticationInfo::new();
        auth_info.password = percent_encoding::percent_decode(password.as_bytes())
            .decode_utf8()
            .map_err(|_| "Password in URI is not valid UTF-8".to_string())?
            .into_owned()
            .into();

        // Handle username if present
        if !url.username().is_empty() {
            auth_info.username = percent_encoding::percent_decode(url.username().as_bytes())
                .decode_utf8()
                .map_err(|_| "Username in URI is not valid UTF-8".to_string())?
                .into_owned()
                .into();
        }

        request.authentication_info = ::protobuf::MessageField::some(auth_info);
    }

    // Extract database ID from path
    if let Some(segments) = url.path_segments()
        && let Some(db_str) = segments.into_iter().next()
        && !db_str.is_empty()
    {
        let db_id = db_str
            .parse::<u32>()
            .map_err(|e| format!("Invalid database ID '{}': {}", db_str, e))?;
        request.database_id = db_id;
    }

    // Set TLS mode based on scheme (valkeys:// is normalized to rediss:// before parsing)
    let tls_mode = if url.scheme() == "rediss" {
        connection_request::TlsMode::SecureTls
    } else {
        connection_request::TlsMode::NoTls
    };
    request.tls_mode = ::protobuf::EnumOrUnknown::new(tls_mode);

    // Parse extra options from JSON if provided
    if !extra_options_json.is_null() {
        let json_string = unsafe {
            CStr::from_ptr(extra_options_json)
                .to_str()
                .map_err(|e| format!("Invalid UTF-8 in JSON: {}", e))?
        };

        if !json_string.is_empty() {
            apply_json_options(&mut request, json_string)?;
        }
    }

    Ok(request)
}

#[cfg(test)]
mod tests_create_client_from_uri_internal {
    //! Direct unit tests for `create_client_from_uri_internal`. The wider
    //! integration suite in `ffi/tests/test_create_client_from_uri.rs` only
    //! exercises the FFI entry point end-to-end (which requires a live server
    //! and can't cheaply assert on the protobuf shape). These tests call the
    //! internal function directly so we can assert that reserved characters
    //! in userinfo round-trip into `AuthenticationInfo` as raw bytes — the
    //! regression this file was written to prevent (issue #6659).
    use super::*;
    use std::ffi::CString;

    fn parse_uri(uri: &str) -> connection_request::ConnectionRequest {
        let c_uri = CString::new(uri).unwrap();
        create_client_from_uri_internal(c_uri.as_ptr(), std::ptr::null())
            .unwrap_or_else(|e| panic!("failed to parse {uri}: {e}"))
    }

    #[test]
    fn reserved_char_password_is_percent_decoded() {
        let req = parse_uri("redis://:p%40ss@127.0.0.1:6379");
        let auth = req.authentication_info.as_ref().expect("auth info missing");
        assert_eq!(&*auth.password, "p@ss");
        assert_eq!(&*auth.username, "");
    }

    #[test]
    fn reserved_char_username_is_percent_decoded() {
        let req = parse_uri("redis://us%3Aer:p%40ss@127.0.0.1:6379");
        let auth = req.authentication_info.as_ref().expect("auth info missing");
        assert_eq!(&*auth.username, "us:er");
        assert_eq!(&*auth.password, "p@ss");
    }

    #[test]
    fn ascii_alphanumeric_credentials_are_unchanged() {
        // Percent-decoding is a no-op on already-safe inputs — the pre-existing
        // integration tests use these shapes; keep them working verbatim.
        let req = parse_uri("redis://user:pass@127.0.0.1:6379");
        let auth = req.authentication_info.as_ref().expect("auth info missing");
        assert_eq!(&*auth.username, "user");
        assert_eq!(&*auth.password, "pass");
    }

    #[test]
    fn space_and_plus_in_password_are_percent_decoded() {
        // '+' is NOT decoded to space in URI userinfo (that's application/x-www-form-urlencoded);
        // "%20" decodes to space, "+" stays as "+".
        let req = parse_uri("redis://:hello%20world+plus@127.0.0.1:6379");
        let auth = req.authentication_info.as_ref().expect("auth info missing");
        assert_eq!(&*auth.password, "hello world+plus");
    }

    #[test]
    fn non_ascii_utf8_password_is_percent_decoded() {
        // 'é' -> UTF-8 bytes 0xC3 0xA9 -> "%C3%A9"
        let req = parse_uri("redis://:caf%C3%A9@127.0.0.1:6379");
        let auth = req.authentication_info.as_ref().expect("auth info missing");
        assert_eq!(&*auth.password, "café");
    }

    #[test]
    fn invalid_utf8_in_password_returns_error() {
        // "%C3%28" is an invalid UTF-8 sequence (0xC3 0x28 — 0xC3 expects a
        // continuation byte in [0x80, 0xBF]). Decoding must surface a clear
        // error rather than silently corrupt bytes.
        let c_uri = CString::new("redis://:%C3%28@127.0.0.1:6379").unwrap();
        let err = create_client_from_uri_internal(c_uri.as_ptr(), std::ptr::null())
            .expect_err("expected UTF-8 error");
        assert!(
            err.contains("Password in URI is not valid UTF-8"),
            "unexpected error: {err}"
        );
    }

    #[test]
    fn invalid_utf8_in_username_returns_error() {
        // Symmetric to invalid_utf8_in_password_returns_error: the username
        // decode branch must surface the same clear UTF-8 error rather than
        // silently corrupt bytes. Password is valid so parsing reaches the
        // username check.
        let c_uri = CString::new("redis://%C3%28:pw@127.0.0.1:6379").unwrap();
        let err = create_client_from_uri_internal(c_uri.as_ptr(), std::ptr::null())
            .expect_err("expected UTF-8 error");
        assert!(
            err.contains("Username in URI is not valid UTF-8"),
            "unexpected error: {err}"
        );
    }

    fn host_and_port(uri: &str) -> (String, u32) {
        let req = parse_uri(uri);
        let addr = req.addresses.first().expect("no address parsed");
        (addr.host.to_string(), addr.port)
    }

    #[test]
    fn ipv6_literal_host_is_unbracketed() {
        let (host, port) = host_and_port("redis://[::1]:6400");
        assert_eq!(host, "::1");
        assert_eq!(port, 6400);
    }

    #[test]
    fn ipv6_uncompressed_literal_is_canonicalized_unbracketed() {
        let (host, port) = host_and_port("redis://[0:0:0:0:0:0:0:1]:6400");
        assert_eq!(host, "::1");
        assert_eq!(port, 6400);
    }

    #[test]
    fn ipv6_v4_mapped_literal_is_unbracketed() {
        // v4-mapped address: assert on the canonical `Ipv6Addr::to_string()`
        // output (no brackets). Kept in sync with the std canonical form.
        let (host, port) = host_and_port("redis://[::ffff:127.0.0.1]:6400");
        assert_eq!(
            host,
            std::net::Ipv6Addr::new(0, 0, 0, 0, 0, 0xffff, 0x7f00, 0x0001).to_string()
        );
        assert_eq!(port, 6400);
    }

    #[test]
    fn ipv4_literal_host_is_unchanged() {
        // Regression control: IPv4 literals must be byte-identical to before.
        let (host, port) = host_and_port("redis://127.0.0.1:6400");
        assert_eq!(host, "127.0.0.1");
        assert_eq!(port, 6400);
    }

    #[test]
    fn domain_host_is_unchanged() {
        // Regression control: domain hosts must be byte-identical to before.
        let (host, port) = host_and_port("redis://localhost:6400");
        assert_eq!(host, "localhost");
        assert_eq!(port, 6400);
    }

    #[test]
    fn hostless_uri_returns_missing_host_error() {
        // A URI that parses as a URL but carries no authority (e.g. only a
        // path) has `url::Url::host()` == None. The `None` match arm must
        // surface a clear "URI missing host" error rather than panicking or
        // silently building an address with an empty host.
        let c_uri = CString::new("redis:///0").unwrap();
        let err = create_client_from_uri_internal(c_uri.as_ptr(), std::ptr::null())
            .expect_err("expected missing-host error");
        assert!(err.contains("URI missing host"), "unexpected error: {err}");
    }
}

fn is_known_connection_options_json_key(key: &str) -> bool {
    matches!(
        key,
        "request_timeout"
            | "connection_timeout"
            | "client_name"
            | "cluster_mode_enabled"
            | "refresh_topology_from_initial_nodes"
            | "protocol"
            | "read_from"
            | "connection_retry_strategy"
            | "root_certs"
            | "client_az"
            | "database_id"
            | "inflight_requests_limit"
            | "tls_mode"
            | "client_cert"
            | "client_key"
            | "lib_name"
            | "tcp_nodelay"
            | "lazy_connect"
            | "read_only"
            | "node_discovery_mode"
            | "pubsub_reconciliation_interval_ms"
            | "compression_config"
            | "periodic_checks"
            | "iam_credentials"
            | "pubsub_subscriptions"
            | "client_side_cache"
    )
}

fn validate_connection_options_json_keys(
    obj: &serde_json::Map<String, serde_json::Value>,
) -> Result<(), String> {
    let mut unknown: Vec<&str> = obj
        .keys()
        .map(String::as_str)
        .filter(|k| !is_known_connection_options_json_key(k))
        .collect();
    if unknown.is_empty() {
        return Ok(());
    }
    unknown.sort_unstable();
    Err(format!(
        "Unknown key(s) in connection options JSON: {}",
        unknown.join(", ")
    ))
}

/// Apply additional connection options from JSON to the ConnectionRequest
fn apply_json_options(
    request: &mut connection_request::ConnectionRequest,
    json_str: &str,
) -> Result<(), String> {
    let json_value: serde_json::Value = serde_json::from_str(json_str).map_err(|e| {
        format!(
            "Invalid JSON in connection options: {:?} at line {}, column {}",
            e.classify(),
            e.line(),
            e.column()
        )
    })?;

    if !json_value.is_object() {
        return Err("JSON options must be an object".to_string());
    }

    let obj = json_value.as_object().unwrap();
    validate_connection_options_json_keys(obj)?;

    // Handle request_timeout
    if let Some(timeout) = obj.get("request_timeout") {
        let timeout_ms = timeout
            .as_u64()
            .ok_or_else(|| "request_timeout must be a positive integer".to_string())?
            as u32;
        request.request_timeout = timeout_ms;
    }

    // Handle connection_timeout
    if let Some(timeout) = obj.get("connection_timeout") {
        let timeout_ms = timeout
            .as_u64()
            .ok_or_else(|| "connection_timeout must be a positive integer".to_string())?
            as u32;
        request.connection_timeout = timeout_ms;
    }

    // Handle client_name
    if let Some(name) = obj.get("client_name") {
        let name_str = name
            .as_str()
            .ok_or_else(|| "client_name must be a string".to_string())?;
        request.client_name = name_str.to_string().into();
    }

    // Handle cluster_mode_enabled
    if let Some(cluster_mode) = obj.get("cluster_mode_enabled") {
        let enabled = cluster_mode
            .as_bool()
            .ok_or_else(|| "cluster_mode_enabled must be a boolean".to_string())?;
        request.cluster_mode_enabled = enabled;
    }

    // Handle refresh_topology_from_initial_nodes
    if let Some(refresh) = obj.get("refresh_topology_from_initial_nodes") {
        let enabled = refresh
            .as_bool()
            .ok_or_else(|| "refresh_topology_from_initial_nodes must be a boolean".to_string())?;
        request.refresh_topology_from_initial_nodes = enabled;
    }

    // Handle protocol version
    if let Some(protocol) = obj.get("protocol") {
        let protocol_str = protocol
            .as_str()
            .ok_or_else(|| "protocol must be a string".to_string())?;
        let protocol_version = match protocol_str.to_uppercase().as_str() {
            "RESP2" => connection_request::ProtocolVersion::RESP2,
            "RESP3" => connection_request::ProtocolVersion::RESP3,
            _ => return Err(format!("Unknown protocol version: {}", protocol_str)),
        };
        request.protocol = ::protobuf::EnumOrUnknown::new(protocol_version);
    }

    // Handle read_from
    if let Some(read_from) = obj.get("read_from") {
        let read_from_str = read_from
            .as_str()
            .ok_or_else(|| "read_from must be a string".to_string())?;
        let read_from_enum = match read_from_str {
            "Primary" => connection_request::ReadFrom::Primary,
            "PreferReplica" => connection_request::ReadFrom::PreferReplica,
            "LowestLatency" => connection_request::ReadFrom::LowestLatency,
            "AZAffinity" => connection_request::ReadFrom::AZAffinity,
            "AZAffinityReplicasAndPrimary" => {
                connection_request::ReadFrom::AZAffinityReplicasAndPrimary
            }
            "AllNodes" => connection_request::ReadFrom::AllNodes,
            "AZAffinityAllNodes" => connection_request::ReadFrom::AZAffinityAllNodes,
            _ => return Err(format!("Unknown read_from value: {}", read_from_str)),
        };
        request.read_from = ::protobuf::EnumOrUnknown::new(read_from_enum);
    }

    // Handle connection_retry_strategy
    if let Some(retry) = obj.get("connection_retry_strategy") {
        let retry_obj = retry
            .as_object()
            .ok_or_else(|| "connection_retry_strategy must be an object".to_string())?;

        let mut strategy = connection_request::ConnectionRetryStrategy::new();

        if let Some(retries) = retry_obj.get("number_of_retries") {
            strategy.number_of_retries = retries
                .as_u64()
                .ok_or_else(|| "number_of_retries must be a positive integer".to_string())?
                as u32;
        }

        if let Some(factor) = retry_obj.get("factor") {
            strategy.factor = factor
                .as_u64()
                .ok_or_else(|| "factor must be a positive integer".to_string())?
                as u32;
        }

        if let Some(base) = retry_obj.get("exponent_base") {
            strategy.exponent_base = base
                .as_u64()
                .ok_or_else(|| "exponent_base must be a positive integer".to_string())?
                as u32;
        }

        if let Some(jitter) = retry_obj.get("jitter_percent") {
            strategy.jitter_percent = Some(
                jitter
                    .as_u64()
                    .ok_or_else(|| "jitter_percent must be a positive integer".to_string())?
                    as u32,
            );
        }

        request.connection_retry_strategy = ::protobuf::MessageField::some(strategy);
    }

    // Handle root_certs (array of certificate strings)
    if let Some(certs) = obj.get("root_certs") {
        let certs_array = certs
            .as_array()
            .ok_or_else(|| "root_certs must be an array".to_string())?;

        for cert in certs_array {
            let cert_str = cert
                .as_str()
                .ok_or_else(|| "Each root_cert must be a string".to_string())?;
            request.root_certs.push(cert_str.as_bytes().to_vec().into());
        }
    }

    // Handle client_az (availability zone)
    if let Some(az) = obj.get("client_az") {
        let az_str = az
            .as_str()
            .ok_or_else(|| "client_az must be a string".to_string())?;
        request.client_az = az_str.to_string().into();
    }

    // Handle database_id (override URI database if specified)
    if let Some(db_id) = obj.get("database_id") {
        let db = db_id
            .as_u64()
            .ok_or_else(|| "database_id must be a positive integer".to_string())?
            as u32;
        request.database_id = db;
    }

    // Handle inflight_requests_limit
    if let Some(limit) = obj.get("inflight_requests_limit") {
        let limit_val = limit
            .as_u64()
            .ok_or_else(|| "inflight_requests_limit must be a positive integer".to_string())?
            as u32;
        request.inflight_requests_limit = limit_val;
    }

    // Handle TLS mode (override URI scheme if specified)
    if let Some(tls) = obj.get("tls_mode") {
        let tls_str = tls
            .as_str()
            .ok_or_else(|| "tls_mode must be a string".to_string())?;
        let tls_mode = match tls_str {
            "NoTls" => connection_request::TlsMode::NoTls,
            "SecureTls" => connection_request::TlsMode::SecureTls,
            "InsecureTls" => connection_request::TlsMode::InsecureTls,
            _ => return Err(format!("Unknown tls_mode value: {}", tls_str)),
        };
        request.tls_mode = ::protobuf::EnumOrUnknown::new(tls_mode);
    }

    // Handle client_cert (for mutual TLS)
    if let Some(cert) = obj.get("client_cert") {
        let cert_str = cert
            .as_str()
            .ok_or_else(|| "client_cert must be a string".to_string())?;
        request.client_cert = cert_str.as_bytes().to_vec().into();
    }

    // Handle client_key (for mutual TLS)
    if let Some(key) = obj.get("client_key") {
        let key_str = key
            .as_str()
            .ok_or_else(|| "client_key must be a string".to_string())?;
        request.client_key = key_str.as_bytes().to_vec().into();
    }

    // Handle lib_name
    if let Some(lib_name) = obj.get("lib_name") {
        let name_str = lib_name
            .as_str()
            .ok_or_else(|| "lib_name must be a string".to_string())?;
        request.lib_name = name_str.to_string().into();
    }

    // Handle tcp_nodelay
    if let Some(nodelay) = obj.get("tcp_nodelay") {
        let enabled = nodelay
            .as_bool()
            .ok_or_else(|| "tcp_nodelay must be a boolean".to_string())?;
        request.tcp_nodelay = Some(enabled);
    }

    // Handle lazy_connect
    if let Some(lazy) = obj.get("lazy_connect") {
        let enabled = lazy
            .as_bool()
            .ok_or_else(|| "lazy_connect must be a boolean".to_string())?;
        request.lazy_connect = enabled;
    }

    // Handle read_only
    if let Some(read_only) = obj.get("read_only") {
        let enabled = read_only
            .as_bool()
            .ok_or_else(|| "read_only must be a boolean".to_string())?;
        request.read_only = Some(enabled);
    }

    // Handle node_discovery_mode
    if let Some(mode) = obj.get("node_discovery_mode") {
        let mode_str = mode
            .as_str()
            .ok_or_else(|| "node_discovery_mode must be a string".to_string())?;
        let mode_enum = match mode_str {
            "Standard" => connection_request::NodeDiscoveryMode::Standard,
            "Static" => connection_request::NodeDiscoveryMode::Static,
            "DiscoverAll" => connection_request::NodeDiscoveryMode::DiscoverAll,
            _ => return Err(format!("Unknown node_discovery_mode value: {}", mode_str)),
        };
        request.node_discovery_mode = ::protobuf::EnumOrUnknown::new(mode_enum);
    }

    // Handle pubsub_reconciliation_interval_ms
    if let Some(interval) = obj.get("pubsub_reconciliation_interval_ms") {
        let interval_ms = interval.as_u64().ok_or_else(|| {
            "pubsub_reconciliation_interval_ms must be a positive integer".to_string()
        })? as u32;
        request.pubsub_reconciliation_interval_ms = Some(interval_ms);
    }

    // Handle compression_config
    if let Some(compression) = obj.get("compression_config") {
        let compression_obj = compression
            .as_object()
            .ok_or_else(|| "compression_config must be an object".to_string())?;

        let mut config = connection_request::CompressionConfig::new();

        if let Some(enabled) = compression_obj.get("enabled") {
            config.enabled = enabled
                .as_bool()
                .ok_or_else(|| "compression_config.enabled must be a boolean".to_string())?;
        }

        if let Some(backend) = compression_obj.get("backend") {
            let backend_str = backend
                .as_str()
                .ok_or_else(|| "compression_config.backend must be a string".to_string())?;
            let backend_enum = match backend_str.to_uppercase().as_str() {
                "ZSTD" => connection_request::CompressionBackend::ZSTD,
                "LZ4" => connection_request::CompressionBackend::LZ4,
                _ => return Err(format!("Unknown compression backend: {}", backend_str)),
            };
            config.backend = ::protobuf::EnumOrUnknown::new(backend_enum);
        }

        if let Some(level) = compression_obj.get("compression_level") {
            let level_val = level.as_i64().ok_or_else(|| {
                "compression_config.compression_level must be an integer".to_string()
            })? as i32;
            config.compression_level = Some(level_val);
        }

        if let Some(min_size) = compression_obj.get("min_compression_size") {
            config.min_compression_size = min_size.as_u64().ok_or_else(|| {
                "compression_config.min_compression_size must be a positive integer".to_string()
            })? as u32;
        }

        request.compression_config = ::protobuf::MessageField::some(config);
    }

    // Handle periodic_checks (oneof: manual_interval or disabled)
    if let Some(periodic) = obj.get("periodic_checks") {
        let periodic_obj = periodic
            .as_object()
            .ok_or_else(|| "periodic_checks must be an object".to_string())?;

        if let Some(manual) = periodic_obj.get("manual_interval") {
            let manual_obj = manual
                .as_object()
                .ok_or_else(|| "periodic_checks.manual_interval must be an object".to_string())?;

            let duration = manual_obj.get("duration_in_sec").ok_or_else(|| {
                "periodic_checks.manual_interval.duration_in_sec is required".to_string()
            })?;
            let duration_val = duration.as_u64().ok_or_else(|| {
                "periodic_checks.manual_interval.duration_in_sec must be a positive integer"
                    .to_string()
            })? as u32;

            let mut manual_interval = connection_request::PeriodicChecksManualInterval::new();
            manual_interval.duration_in_sec = duration_val;
            request.set_periodic_checks_manual_interval(manual_interval);
        } else if let Some(disabled) = periodic_obj.get("disabled") {
            let is_disabled = disabled
                .as_bool()
                .ok_or_else(|| "periodic_checks.disabled must be a boolean".to_string())?;

            if is_disabled {
                request.set_periodic_checks_disabled(connection_request::PeriodicChecksDisabled::new());
            }
        } else {
            return Err(
                "periodic_checks must have either 'manual_interval' or 'disabled'".to_string(),
            );
        }
    }

    // Handle IAM credentials (nested in authentication_info)
    if let Some(iam) = obj.get("iam_credentials") {
        let iam_obj = iam
            .as_object()
            .ok_or_else(|| "iam_credentials must be an object".to_string())?;

        let mut iam_creds = connection_request::IamCredentials::new();

        if let Some(cluster_name) = iam_obj.get("cluster_name") {
            let name = cluster_name
                .as_str()
                .ok_or_else(|| "iam_credentials.cluster_name must be a string".to_string())?;
            iam_creds.cluster_name = name.to_string().into();
        }

        if let Some(region) = iam_obj.get("region") {
            let region_str = region
                .as_str()
                .ok_or_else(|| "iam_credentials.region must be a string".to_string())?;
            iam_creds.region = region_str.to_string().into();
        }

        if let Some(service_type) = iam_obj.get("service_type") {
            let service_str = service_type
                .as_str()
                .ok_or_else(|| "iam_credentials.service_type must be a string".to_string())?;
            let service_enum = match service_str.to_uppercase().as_str() {
                "ELASTICACHE" => connection_request::ServiceType::ELASTICACHE,
                "MEMORYDB" => connection_request::ServiceType::MEMORYDB,
                _ => return Err(format!("Unknown service type: {}", service_str)),
            };
            iam_creds.service_type = ::protobuf::EnumOrUnknown::new(service_enum);
        }

        if let Some(refresh_interval) = iam_obj.get("refresh_interval_seconds") {
            let interval_val = refresh_interval.as_u64().ok_or_else(|| {
                "iam_credentials.refresh_interval_seconds must be a positive integer".to_string()
            })? as u32;
            iam_creds.refresh_interval_seconds = Some(interval_val);
        }

        // Get or create authentication_info and set IAM credentials
        let mut auth_info = request.authentication_info.take().unwrap_or_default();
        auth_info.iam_credentials = ::protobuf::MessageField::some(iam_creds);
        request.authentication_info = ::protobuf::MessageField::some(auth_info);
    }

    // Handle pubsub_subscriptions
    if let Some(pubsub) = obj.get("pubsub_subscriptions") {
        let pubsub_obj = pubsub
            .as_object()
            .ok_or_else(|| "pubsub_subscriptions must be an object".to_string())?;

        let mut subscriptions = connection_request::PubSubSubscriptions::new();

        for (channel_type_str, channels_value) in pubsub_obj {
            // Parse channel type (0=Exact, 1=Pattern, 2=Sharded)
            let channel_type_num = channel_type_str.parse::<u32>().map_err(|_| {
                format!(
                    "pubsub_subscriptions key '{}' must be a number (0=Exact, 1=Pattern, 2=Sharded)",
                    channel_type_str
                )
            })?;

            let channels_array = channels_value.as_array().ok_or_else(|| {
                format!("pubsub_subscriptions.{} must be an array", channel_type_str)
            })?;

            let mut channels_or_patterns = connection_request::PubSubChannelsOrPatterns::new();

            for channel in channels_array {
                let channel_str = channel
                    .as_str()
                    .ok_or_else(|| "Each channel/pattern must be a string".to_string())?;
                channels_or_patterns
                    .channels_or_patterns
                    .push(channel_str.as_bytes().to_vec().into());
            }

            subscriptions
                .channels_or_patterns_by_type
                .insert(channel_type_num, channels_or_patterns);
        }

        request.pubsub_subscriptions = ::protobuf::MessageField::some(subscriptions);
    }

    // Handle client_side_cache
    if let Some(cache) = obj.get("client_side_cache") {
        let cache_obj = cache
            .as_object()
            .ok_or_else(|| "client_side_cache must be an object".to_string())?;

        if cache_obj.contains_key("cache_id") {
            return Err(
                "client_side_cache.cache_id is not accepted; it is generated internally"
                    .to_string(),
            );
        }

        let mut config = connection_request::ClientSideCache::new();

        // max_cache_kb (required)
        let max_cache_kb = cache_obj
            .get("max_cache_kb")
            .ok_or_else(|| "client_side_cache.max_cache_kb is required".to_string())?;
        config.max_cache_kb = max_cache_kb.as_u64().ok_or_else(|| {
            "client_side_cache.max_cache_kb must be a positive integer".to_string()
        })?;
        if config.max_cache_kb == 0 {
            return Err("client_side_cache.max_cache_kb must be greater than 0".to_string());
        }

        // entry_ttl_ms (required)
        let entry_ttl_ms = cache_obj
            .get("entry_ttl_ms")
            .ok_or_else(|| "client_side_cache.entry_ttl_ms is required".to_string())?;
        config.entry_ttl_ms = entry_ttl_ms.as_u64().ok_or_else(|| {
            "client_side_cache.entry_ttl_ms must be a non-negative integer".to_string()
        })?;

        // eviction_policy (optional, defaults to LRU)
        if let Some(policy) = cache_obj.get("eviction_policy") {
            let policy_str = policy
                .as_str()
                .ok_or_else(|| "client_side_cache.eviction_policy must be a string".to_string())?;
            let policy_enum = match policy_str.to_uppercase().as_str() {
                "LRU" => connection_request::EvictionPolicy::LRU,
                "LFU" => connection_request::EvictionPolicy::LFU,
                _ => {
                    return Err(format!(
                        "Unknown eviction_policy: {}. Valid values are: LRU, LFU",
                        policy_str
                    ));
                }
            };
            config.eviction_policy = Some(::protobuf::EnumOrUnknown::new(policy_enum));
        }

        // enable_metrics (optional, defaults to false)
        if let Some(metrics) = cache_obj.get("enable_metrics") {
            config.enable_metrics = metrics
                .as_bool()
                .ok_or_else(|| "client_side_cache.enable_metrics must be a boolean".to_string())?;
        }

        // Auto-generate cache_id after all validation passes (not accepted from JSON input)
        config.cache_id = Uuid::new_v4().to_string().into();
        request.client_side_cache = ::protobuf::MessageField::some(config);
    }

    Ok(())
}

/// Closes the given `GlideClient`, freeing it from the heap.
///
/// `client_adapter_ptr` is a pointer to a valid `GlideClient` returned in the `ConnectionResponse` from [`create_client`].
///
/// # Panics
///
/// This function panics when called with a null `client_adapter_ptr`.
///
/// # Safety
///
/// * `close_client` can only be called once per client. Calling it twice is undefined behavior, since the address will be freed twice.
/// * `close_client` must be called after `free_connection_response` has been called to avoid creating a dangling pointer in the `ConnectionResponse`.
/// * `client_adapter_ptr` must be obtained from the `ConnectionResponse` returned from [`create_client`].
/// * `client_adapter_ptr` must be valid until `close_client` is called.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn close_client(client_adapter_ptr: *const c_void) {
    assert!(!client_adapter_ptr.is_null());

    // Clean up scope pool and registry for this client (if any)
    #[cfg(feature = "pool-support")]
    {
        let client_id = client_adapter_ptr as usize as u64;
        glide_core::pool::get_client_scope_pools().remove(&client_id);
        glide_core::scope::unregister_client(client_id);
    }

    // This will bring the strong count down to 0 once all client requests are done.
    unsafe { Arc::decrement_strong_count(client_adapter_ptr as *const ClientAdapter) };
}

/// Deallocates a `ConnectionResponse`.
///
/// This function also frees the contained error. If the contained error is a null pointer, the function returns and only the `ConnectionResponse` is freed.
///
/// # Panics
///
/// This function panics when called with a null `ConnectionResponse` pointer.
///
/// # Safety
///
/// * `free_connection_response` can only be called once per `ConnectionResponse`. Calling it twice is undefined behavior, since the address will be freed twice.
/// * `connection_response_ptr` must be obtained from the `ConnectionResponse` returned from [`create_client`].
/// * `connection_response_ptr` must be valid until `free_connection_response` is called.
/// * The contained `connection_error_message` must be obtained from the `ConnectionResponse` returned from [`create_client`].
/// * The contained `connection_error_message` must be valid until `free_connection_response` is called and it must outlive the `ConnectionResponse` that contains it.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_connection_response(
    connection_response_ptr: *mut ConnectionResponse,
) {
    assert!(!connection_response_ptr.is_null());
    let connection_response = unsafe { Box::from_raw(connection_response_ptr) };
    let connection_error_message = connection_response.connection_error_message;
    drop(connection_response);
    if !connection_error_message.is_null() {
        drop(unsafe { CString::from_raw(connection_error_message as *mut c_char) });
    }
}

/// Deallocates a `CommandResponse`.
///
/// This function also frees the contained string_value and array_value. If the string_value and array_value are null pointers, the function returns and only the `CommandResponse` is freed.
///
/// # Safety
///
/// * `free_command_response` can only be called once per `CommandResponse`. Calling it twice is undefined behavior, since the address will be freed twice.
/// * `command_response_ptr` must be obtained from the `CommandResponse` returned in [`SuccessCallback`] from [`command`].
/// * `command_response_ptr` must be valid until `free_command_response` is called.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_command_response(command_response_ptr: *mut CommandResponse) {
    if !command_response_ptr.is_null() {
        let resp = unsafe { &*command_response_ptr };
        if !resp.arena_ptr.is_null() {
            // Arena-allocated response: free the entire arena (which owns this pointer)
            unsafe { free_response_arena(resp.arena_ptr as *mut ResponseArena) };
        } else {
            let command_response = unsafe { Box::from_raw(command_response_ptr) };
            unsafe { free_command_response_elements(*command_response) };
        }
    }
}

/// Frees the nested elements of `CommandResponse`.
/// TODO: Add a test case to check for memory leak.
///
/// # Safety
///
/// * `free_command_response_elements` can only be called once per `CommandResponse`. Calling it twice is undefined behavior, since the address will be freed twice.
/// * The contained `string_value` must be obtained from the `CommandResponse` returned in [`SuccessCallback`] from [`command`].
/// * The contained `string_value` must be valid until `free_command_response` is called and it must outlive the `CommandResponse` that contains it.
/// * The contained `array_value` must be obtained from the `CommandResponse` returned in [`SuccessCallback`] from [`command`].
/// * The contained `array_value` must be valid until `free_command_response` is called and it must outlive the `CommandResponse` that contains it.
/// * The contained `map_key` must be obtained from the `CommandResponse` returned in [`SuccessCallback`] from [`command`].
/// * The contained `map_key` must be valid until `free_command_response` is called and it must outlive the `CommandResponse` that contains it.
/// * The contained `map_value` must be obtained from the `CommandResponse` returned in [`SuccessCallback`] from [`command`].
/// * The contained `map_value` must be valid until `free_command_response` is called and it must outlive the `CommandResponse` that contains it.
unsafe fn free_command_response_elements(command_response: CommandResponse) {
    let string_value = command_response.string_value;
    let string_value_len = command_response.string_value_len;
    let array_value = command_response.array_value;
    let array_value_len = command_response.array_value_len;
    let map_key = command_response.map_key;
    let map_value = command_response.map_value;
    let sets_value = command_response.sets_value;
    let sets_value_len = command_response.sets_value_len;
    if !string_value.is_null() {
        let len = string_value_len as usize;
        let _ = unsafe { Box::from_raw(std::ptr::slice_from_raw_parts_mut(string_value, len)) };
    }
    if !array_value.is_null() {
        let len = array_value_len as usize;
        let boxed = unsafe { Box::from_raw(std::ptr::slice_from_raw_parts_mut(array_value, len)) };
        for element in Vec::from(boxed).into_iter() {
            unsafe { free_command_response_elements(element) };
        }
    }
    if !map_key.is_null() {
        unsafe { free_command_response(map_key) };
    }
    if !map_value.is_null() {
        unsafe { free_command_response(map_value) };
    }
    if !sets_value.is_null() {
        let len = sets_value_len as usize;
        let boxed = unsafe { Box::from_raw(std::ptr::slice_from_raw_parts_mut(sets_value, len)) };
        for element in Vec::from(boxed).into_iter() {
            unsafe { free_command_response_elements(element) };
        }
    }
}

/// Resolves the actual RequestType for a CustomCommand by parsing the command name from the first argument.
/// This is needed for compression validation since CustomCommand doesn't carry the actual command type.
fn resolve_custom_command_type(args: &[Vec<u8>]) -> RequestType {
    if args.is_empty() {
        return RequestType::CustomCommand;
    }

    let command_name = &args[0];
    let command_str = String::from_utf8_lossy(command_name);

    // Use the centralized from_command_name method in glide-core
    RequestType::from_command_name(&command_str).unwrap_or(RequestType::CustomCommand)
}

///
/// # Safety
///
/// `convert_double_pointer_to_vec` returns a `Vec` of u8 slice which holds pointers of `go`
/// strings. The returned `Vec<&'a [u8]>` is meant to be copied into Rust code. Storing them
/// for later use will cause the program to crash as the pointers will be freed by go's gc
unsafe fn convert_double_pointer_to_vec<'a>(
    data: *const *const c_void,
    len: c_ulong,
    data_len: *const c_ulong,
) -> Vec<&'a [u8]> {
    let string_ptrs = unsafe { from_raw_parts(data, len as usize) };
    let string_lengths = unsafe { from_raw_parts(data_len, len as usize) };
    let mut result = Vec::<&[u8]>::with_capacity(string_ptrs.len());
    for (i, &str_ptr) in string_ptrs.iter().enumerate() {
        let slice = unsafe { from_raw_parts(str_ptr as *const u8, string_lengths[i] as usize) };
        result.push(slice);
    }
    result
}

fn convert_vec_to_pointer<T>(vec: Vec<T>) -> (*mut T, c_long) {
    // into_boxed_slice guarantees capacity == len (unlike shrink_to_fit which is a hint).
    // This is critical because from_raw_parts later uses len as capacity for dealloc.
    let len = vec.len() as c_long;
    let ptr = Box::into_raw(vec.into_boxed_slice()) as *mut T;
    (ptr, len)
}

// ==================== Arena-based response builder ====================

/// Arena that owns all CommandResponse nodes and string buffers for a single response tree.
/// Freed in one shot via `free_response_arena`.
pub struct ResponseArena {
    /// All CommandResponse nodes. Index 0 is the root.
    nodes: Vec<CommandResponse>,
    /// Owned string buffers (kept alive until arena is freed).
    ///
    /// Held as [`bytes::Bytes`] so `BulkString` payloads — zero-copy slices
    /// of the connection read buffer — can be stored without a `to_vec()`
    /// copy. NOTE: while the arena is alive it pins the read-buffer chunks
    /// its slices came from; bindings free the arena promptly after
    /// materializing values, keeping that window short.
    strings: Vec<bytes::Bytes>,
}

const MAX_ARENA_POOL_SIZE: usize = 16;
thread_local! { static ARENA_POOL: std::cell::RefCell<Vec<ResponseArena>> = const { std::cell::RefCell::new(Vec::new()) }; }
impl ResponseArena {
    fn from_pool(value: &Value) -> Self {
        let nc = Self::count_nodes(value);
        ARENA_POOL.with(|p| {
            let mut p = p.borrow_mut();
            if let Some(mut a) = p.pop() {
                a.nodes.clear();
                a.nodes.reserve(nc);
                a.strings.clear();
                a
            } else {
                ResponseArena {
                    nodes: Vec::with_capacity(nc),
                    strings: Vec::new(),
                }
            }
        })
    }
    fn return_to_pool(mut self) {
        // Drop the string buffers NOW, not when this pool slot is next
        // reused: `strings` holds refcounted `Bytes` that pin the decoded
        // response frames (and their recycled `buf_pool` allocations). A
        // parked arena that kept them alive would pin up to
        // MAX_ARENA_POOL_SIZE frames per thread indefinitely on an idle
        // thread and starve the frame-buffer pool. Only the node Vec's
        // capacity is worth recycling.
        self.strings.clear();
        self.nodes.clear();
        ARENA_POOL.with(|p| {
            let mut p = p.borrow_mut();
            if p.len() < MAX_ARENA_POOL_SIZE {
                p.push(self);
            }
        });
    }
}

impl ResponseArena {
    /// Count total CommandResponse nodes needed for a Value tree.
    /// Maps use wrapper nodes: 1 wrapper per entry + key nodes + value nodes.
    fn count_nodes(value: &Value) -> usize {
        match value {
            Value::Array(arr) => 1 + arr.iter().map(Self::count_nodes).sum::<usize>(),
            Value::Map(map) => {
                1 + map
                    .iter()
                    .map(|(k, v)| 1 + Self::count_nodes(k) + Self::count_nodes(v))
                    .sum::<usize>()
            }
            Value::Set(arr) => 1 + arr.iter().map(Self::count_nodes).sum::<usize>(),
            Value::Push { data, .. } => {
                // Encoded as Map with 2 entries: wrapper + "kind"->str, wrapper + "values"->array
                7 + data.iter().map(Self::count_nodes).sum::<usize>()
            }
            _ => 1,
        }
    }

    /// Allocate a node in the arena, returning its index.
    fn alloc_node(&mut self) -> usize {
        let idx = self.nodes.len();
        self.nodes.push(CommandResponse::default());
        idx
    }

    /// Store a string buffer, returning (ptr, len) for the CommandResponse fields.
    /// Accepts anything convertible to `Bytes`; `Vec<u8>` converts zero-copy.
    ///
    /// The returned pointer aliases the (possibly shared) `Bytes` buffer —
    /// for zero-copy `BulkString`s that is the decoded network frame, shared
    /// by every other slice of the same response and recycled through
    /// `buf_pool` after release. It is therefore read-only and valid only
    /// until the arena releases its strings (`return_to_pool`); see the
    /// `CommandResponse::string_value` contract.
    fn store_string(&mut self, data: impl Into<bytes::Bytes>) -> (*mut c_char, c_long) {
        let data = data.into();
        let ptr = data.as_ptr() as *mut c_char;
        let len = data.len() as c_long;
        self.strings.push(data);
        (ptr, len)
    }

    /// Build the response tree into the arena. Returns index of the root node.
    fn build(&mut self, value: Value, bufs: &[(*mut u8, usize)]) -> RedisResult<usize> {
        let idx = self.alloc_node();
        self.build_into(idx, value, bufs)?;
        Ok(idx)
    }

    /// Build a value into a pre-allocated node at the given index.
    ///
    /// `bufs` carries caller-owned response buffers consumed positionally: a
    /// scalar value uses `bufs[0]`; an `Array` distributes `bufs[i]` to child
    /// `i`, enabling zero-copy multi-key reads (e.g. MGET-into-buffers). An
    /// empty slice means "no caller buffer" (normal heap-allocated response).
    fn build_into(
        &mut self,
        idx: usize,
        value: Value,
        bufs: &[(*mut u8, usize)],
    ) -> RedisResult<()> {
        match value {
            Value::Nil => {}
            Value::Okay => {
                self.nodes[idx].response_type = ResponseType::Ok;
            }
            Value::Int(n) => {
                self.nodes[idx].response_type = ResponseType::Int;
                self.nodes[idx].int_value = n;
            }
            Value::Double(n) => {
                self.nodes[idx].response_type = ResponseType::Float;
                self.nodes[idx].float_value = n;
            }
            Value::Boolean(b) => {
                self.nodes[idx].response_type = ResponseType::Bool;
                self.nodes[idx].bool_value = b;
            }
            Value::SimpleString(text) => {
                let (ptr, len) = self.store_string(text.into_bytes());
                self.nodes[idx].response_type = ResponseType::String;
                self.nodes[idx].string_value = ptr;
                self.nodes[idx].string_value_len = len;
            }
            Value::BulkString(data) => {
                // Use the caller buffer only when a non-null destination was
                // provided; a null entry falls back to a normal heap response
                // (avoids writing through a null/invalid pointer).
                let data =
                    if let Some(&(buf, buf_len)) = bufs.first().filter(|&&(p, _)| !p.is_null()) {
                        if data.len() > buf_len {
                            return Err(RedisError::from((
                                ErrorKind::ClientError,
                                "Value size exceeds buffer capacity",
                                format!(
                                    "value is {} bytes but buffer is {} bytes",
                                    data.len(),
                                    buf_len
                                ),
                            )));
                        }
                        unsafe {
                            std::ptr::copy_nonoverlapping(data.as_ptr(), buf, data.len());
                        }
                        bytes::Bytes::from(data.len().to_string().into_bytes())
                    } else {
                        // Zero-copy: the arena keeps the refcounted slice of
                        // the read buffer alive; no payload copy.
                        data
                    };
                let (ptr, len) = self.store_string(data);
                self.nodes[idx].response_type = ResponseType::String;
                self.nodes[idx].string_value = ptr;
                self.nodes[idx].string_value_len = len;
            }
            Value::VerbatimString { text, .. } => {
                let (ptr, len) = self.store_string(text.into_bytes());
                self.nodes[idx].response_type = ResponseType::String;
                self.nodes[idx].string_value = ptr;
                self.nodes[idx].string_value_len = len;
            }
            Value::Array(arr) => {
                let child_count = arr.len();
                // Pre-allocate direct child slots so they are contiguous
                let child_start = self.nodes.len();
                for _ in 0..child_count {
                    self.alloc_node();
                }
                // Build each child; descendants go after all child slots.
                // Distribute caller buffers positionally so each element of an
                // MGET-style response is written into its own buffer.
                for (i, item) in arr.into_iter().enumerate() {
                    let ci = child_start + i;
                    let child_bufs = bufs.get(i).map(core::slice::from_ref).unwrap_or(&[]);
                    self.build_into(ci, item, child_bufs)?;
                }
                self.nodes[idx].response_type = ResponseType::Array;
                self.nodes[idx].array_value_len = child_count as c_long;
                self.nodes[idx].array_value = child_start as *mut CommandResponse;
            }
            Value::Map(map) => {
                let num_entries = map.len();
                // Allocate wrapper nodes first (one per entry), then build keys/values.
                // Each wrapper's map_key/map_value will point into the arena.
                let wrapper_start = self.nodes.len();
                for _ in 0..num_entries {
                    self.alloc_node();
                }
                for (i, (k, v)) in map.into_iter().enumerate() {
                    let ki = self.nodes.len();
                    self.build(k, &[])?;
                    let vi = self.nodes.len();
                    self.build(v, &[])?;
                    // Pointers fixed up in finalize() along with array_value
                    self.nodes[wrapper_start + i].map_key = ki as *mut CommandResponse;
                    self.nodes[wrapper_start + i].map_value = vi as *mut CommandResponse;
                }
                self.nodes[idx].response_type = ResponseType::Map;
                self.nodes[idx].array_value_len = num_entries as c_long;
                self.nodes[idx].array_value = wrapper_start as *mut CommandResponse;
            }
            Value::Set(arr) => {
                let child_count = arr.len();
                let child_start = self.nodes.len();
                for _ in 0..child_count {
                    self.alloc_node();
                }
                for (i, item) in arr.into_iter().enumerate() {
                    let ci = child_start + i;
                    self.build_into(ci, item, &[])?;
                }
                self.nodes[idx].response_type = ResponseType::Sets;
                self.nodes[idx].sets_value_len = child_count as c_long;
                self.nodes[idx].sets_value = child_start as *mut CommandResponse;
            }
            Value::ServerError(server_error) => {
                let msg = error_message(&server_error.into()).into_bytes();
                let (ptr, len) = self.store_string(msg);
                self.nodes[idx].response_type = ResponseType::Error;
                self.nodes[idx].string_value = ptr;
                self.nodes[idx].string_value_len = len;
            }
            Value::Push { kind, data } => {
                // Encode as Map with 2 entries using wrapper nodes
                let wrapper_start = self.nodes.len();
                let w0 = self.alloc_node(); // wrapper for "kind" entry
                let w1 = self.alloc_node(); // wrapper for "values" entry

                // kind key
                let ki = self.alloc_node();
                let (ptr, len) = self.store_string(bytes::Bytes::from_static(b"kind"));
                self.nodes[ki].response_type = ResponseType::String;
                self.nodes[ki].string_value = ptr;
                self.nodes[ki].string_value_len = len;
                // kind value
                let kv = self.alloc_node();
                let (ptr, len) = self.store_string(format!("{:?}", kind).into_bytes());
                self.nodes[kv].response_type = ResponseType::String;
                self.nodes[kv].string_value = ptr;
                self.nodes[kv].string_value_len = len;
                self.nodes[w0].map_key = ki as *mut CommandResponse;
                self.nodes[w0].map_value = kv as *mut CommandResponse;

                // values key
                let vk = self.alloc_node();
                let (ptr, len) = self.store_string(bytes::Bytes::from_static(b"values"));
                self.nodes[vk].response_type = ResponseType::String;
                self.nodes[vk].string_value = ptr;
                self.nodes[vk].string_value_len = len;
                // values value (array)
                let vv = self.nodes.len();
                self.build(Value::Array(data), &[])?;
                self.nodes[w1].map_key = vk as *mut CommandResponse;
                self.nodes[w1].map_value = vv as *mut CommandResponse;

                self.nodes[idx].response_type = ResponseType::Map;
                self.nodes[idx].array_value_len = 2; // 2 map entries
                self.nodes[idx].array_value = wrapper_start as *mut CommandResponse;
            }
            _ => todo!(),
        }
        Ok(())
    }

    /// Finalize: convert index-based pointers to real pointers, then leak the arena.
    /// Returns pointer to root CommandResponse and an opaque arena pointer for freeing.
    fn finalize(mut self) -> (*mut CommandResponse, *mut ResponseArena) {
        let base = self.nodes.as_ptr() as usize;
        for node in &mut self.nodes {
            match node.response_type {
                ResponseType::Array | ResponseType::Map if node.array_value_len > 0 => {
                    let offset = node.array_value as usize;
                    node.array_value = unsafe { (base as *mut CommandResponse).add(offset) };
                }
                ResponseType::Sets if node.sets_value_len > 0 => {
                    let offset = node.sets_value as usize;
                    node.sets_value = unsafe { (base as *mut CommandResponse).add(offset) };
                }
                _ => {}
            }
            // Fix up map_key/map_value pointers (stored as indices during build)
            if !node.map_key.is_null() {
                let offset = node.map_key as usize;
                node.map_key = unsafe { (base as *mut CommandResponse).add(offset) };
            }
            if !node.map_value.is_null() {
                let offset = node.map_value as usize;
                node.map_value = unsafe { (base as *mut CommandResponse).add(offset) };
            }
        }
        let root = self.nodes.as_mut_ptr();
        let arena_ptr = Box::into_raw(Box::new(self));
        // Store arena pointer in root node so callers can free it
        unsafe {
            (*root).arena_ptr = arena_ptr as *mut c_void;
        }
        (root, arena_ptr)
    }
}

/// Build a CommandResponse tree using arena allocation. Returns (root_ptr, arena_ptr).
/// The arena_ptr must be freed with `free_response_arena`.
fn valkey_value_to_arena_response(
    value: Value,
    bufs: &[(*mut u8, usize)],
) -> RedisResult<(*mut CommandResponse, *mut ResponseArena)> {
    let mut arena = ResponseArena::from_pool(&value);
    arena.build(value, bufs)?;
    Ok(arena.finalize())
}

/// Free an arena-allocated response tree in one shot.
///
/// # Safety
/// `arena_ptr` must have been returned by `valkey_value_to_arena_response`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_response_arena(arena_ptr: *mut ResponseArena) {
    if !arena_ptr.is_null() {
        let arena = unsafe { *Box::from_raw(arena_ptr) };
        arena.return_to_pool();
    }
}

#[cfg(test)]
mod tests_response_arena {
    use super::*;
    use std::sync::Arc;
    use std::sync::atomic::{AtomicBool, Ordering};

    /// Owner for `Bytes::from_owner` that records when the allocation is
    /// released, so tests can observe whether a `Bytes` slice is still
    /// pinning its backing buffer (as zero-copy `BulkString`s pin the
    /// decoded response frame).
    struct DropTracked(Vec<u8>, Arc<AtomicBool>);

    impl AsRef<[u8]> for DropTracked {
        fn as_ref(&self) -> &[u8] {
            &self.0
        }
    }

    impl Drop for DropTracked {
        fn drop(&mut self) {
            self.1.store(true, Ordering::SeqCst);
        }
    }

    /// Regression test: freeing a response arena must release the `Bytes`
    /// frame slices it holds, even though the arena itself is parked in
    /// `ARENA_POOL` for reuse. If `return_to_pool` kept `strings` populated,
    /// a freed GET/MGET response would pin its read-buffer frame (and its
    /// recycled `buf_pool` allocation) indefinitely on an idle thread.
    #[test]
    fn free_response_arena_releases_frame_buffers() {
        let released = Arc::new(AtomicBool::new(false));
        let frame = bytes::Bytes::from_owner(DropTracked(vec![7u8; 64], released.clone()));

        // Two BulkStrings slicing the same frame, as the zero-copy decoder
        // produces for an MGET response.
        let value = Value::Array(vec![
            Value::BulkString(frame.slice(0..16)),
            Value::BulkString(frame.slice(16..32)),
        ]);
        drop(frame);

        let (root, arena_ptr) =
            valkey_value_to_arena_response(value, &[]).expect("arena build failed");

        // While the response is alive, its string pointers alias the pinned
        // frame; read them back the way a binding would (this also gives
        // MIRI a provenance check on the handed-out pointers).
        unsafe {
            let root_ref = &*root;
            assert!(matches!(root_ref.response_type, ResponseType::Array));
            assert_eq!(root_ref.array_value_len, 2);
            for i in 0..root_ref.array_value_len {
                let child = &*root_ref.array_value.add(i as usize);
                assert!(matches!(child.response_type, ResponseType::String));
                assert_eq!(child.string_value_len, 16);
                let payload = std::slice::from_raw_parts(
                    child.string_value as *const u8,
                    child.string_value_len as usize,
                );
                assert_eq!(payload, &[7u8; 16]);
            }
        }
        assert!(
            !released.load(Ordering::SeqCst),
            "frame must stay pinned while the response is alive"
        );

        // Freeing the response must drop the frame slices immediately —
        // NOT hold them until the pooled arena slot is next reused.
        unsafe { free_response_arena(arena_ptr) };
        assert!(
            released.load(Ordering::SeqCst),
            "freed arena parked in ARENA_POOL must not keep pinning response frame buffers"
        );
    }

    /// The caller-buffer path (`response_buffers=` / MGET-into-buffers):
    /// exercises the bounds-checked `copy_nonoverlapping` into caller memory
    /// under MIRI, plus the undersized-buffer error before the copy.
    #[test]
    fn response_buffer_copy_path_is_bounds_checked() {
        let payload = bytes::Bytes::from_static(b"0123456789abcdef");

        // Large enough buffer: value copied, node reports the byte count.
        let mut buf = vec![0u8; 32];
        let value = Value::BulkString(payload.clone());
        let (root, arena_ptr) =
            valkey_value_to_arena_response(value, &[(buf.as_mut_ptr(), buf.len())])
                .expect("arena build failed");
        unsafe {
            let root_ref = &*root;
            assert!(matches!(root_ref.response_type, ResponseType::String));
            // string_value holds the written-length as a decimal string.
            let len_str = std::slice::from_raw_parts(
                root_ref.string_value as *const u8,
                root_ref.string_value_len as usize,
            );
            assert_eq!(len_str, b"16");
            assert_eq!(&buf[..16], &payload[..]);
            free_response_arena(arena_ptr);
        }

        // Undersized buffer: must error before any copy happens.
        let mut small = vec![0u8; 4];
        let value = Value::BulkString(payload);
        let res = valkey_value_to_arena_response(value, &[(small.as_mut_ptr(), small.len())]);
        assert!(res.is_err(), "oversized value must not be copied");
        assert_eq!(&small[..], &[0u8; 4], "no partial write on error");
    }

    /// Map-shaped tree: exercises `finalize`'s index->pointer fixups for
    /// `map_key`/`map_value` and nested string reads under MIRI.
    #[test]
    fn map_tree_finalize_fixups() {
        let frame = bytes::Bytes::from_static(b"key1val1key2val2");
        let value = Value::Map(vec![
            (
                Value::BulkString(frame.slice(0..4)),
                Value::BulkString(frame.slice(4..8)),
            ),
            (
                Value::BulkString(frame.slice(8..12)),
                Value::BulkString(frame.slice(12..16)),
            ),
        ]);
        let (root, arena_ptr) = valkey_value_to_arena_response(value, &[]).expect("build failed");
        unsafe {
            let root_ref = &*root;
            assert!(matches!(root_ref.response_type, ResponseType::Map));
            assert_eq!(root_ref.array_value_len, 2);
            let expected: [(&[u8], &[u8]); 2] = [(b"key1", b"val1"), (b"key2", b"val2")];
            for (i, (ek, ev)) in expected.iter().enumerate() {
                let wrapper = &*root_ref.array_value.add(i);
                let k = &*wrapper.map_key;
                let v = &*wrapper.map_value;
                let kb = std::slice::from_raw_parts(
                    k.string_value as *const u8,
                    k.string_value_len as usize,
                );
                let vb = std::slice::from_raw_parts(
                    v.string_value as *const u8,
                    v.string_value_len as usize,
                );
                assert_eq!(kb, *ek);
                assert_eq!(vb, *ev);
            }
            free_response_arena(arena_ptr);
        }
    }
}

/// Executes a command.
///
/// # Safety
///
/// * `client_adapter_ptr` must not be `null` and must be obtained from the `ConnectionResponse` returned from [`create_client`].
/// * `client_adapter_ptr` must be able to be safely casted to a valid [`Arc<ClientAdapter>`] via [`Arc::from_raw`]. See the safety documentation of [`std::sync::Arc::from_raw`].
/// * `request_id` must be a request ID from the foreign language and must be valid until either `success_callback` or `failure_callback` is finished.
/// * `args` is an optional bytes pointers array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `args_len` is an optional bytes length array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `arg_count` the number of elements in `args` and `args_len`. It must also not be greater than the max value of a signed pointer-sized integer.
/// * `arg_count` must be 0 if `args` and `args_len` are null.
/// * `args` and `args_len` must either be both null or be both not null.
/// * `route_bytes` is an optional array of bytes that will be parsed into a Protobuf `Routes` object. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `route_bytes_len` is the number of bytes in `route_bytes`. It must also not be greater than the max value of a signed pointer-sized integer.
/// * `route_bytes_len` must be 0 if `route_bytes` is null.
/// * `span_ptr` is a valid pointer to [`Arc<GlideSpan>`], a span created by [`create_otel_span`] or `0`. The span must be valid until the command is finished.
/// * This function should only be called should with a `client_adapter_ptr` created by [`create_client`], before [`close_client`] was called with the pointer.
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn command(
    client_adapter_ptr: *const c_void,
    request_id: usize,
    command_type: RequestType,
    arg_count: c_ulong,
    args: *const usize,
    args_len: *const c_ulong,
    route_bytes: *const u8,
    route_bytes_len: usize,
    span_ptr: u64,
) -> *mut CommandResult {
    unsafe {
        command_with_buffer(
            client_adapter_ptr,
            request_id,
            command_type,
            arg_count,
            args,
            args_len,
            route_bytes,
            route_bytes_len,
            std::ptr::null_mut(),
            0,
            span_ptr,
        )
    }
}

/// Where routing info came from across the FFI boundary: legacy protobuf
/// bytes ([`command_with_buffer`]) or a [`RouteInfo`] C-struct
/// ([`command_with_route_info`]).
enum RouteInput {
    /// Protobuf-encoded `Routes` bytes. Null means no route.
    ProtobufBytes {
        route_bytes: *const u8,
        route_bytes_len: usize,
    },
    /// A `RouteInfo` C-struct pointer. Null means no route.
    RouteInfo(*const RouteInfo),
}

impl RouteInput {
    /// Resolves to a [`RoutingInfo`] given a `cmd` is built (needed for
    /// `AllNodes`/`AllPrimaries` `ResponsePolicy`).
    ///
    /// # Safety
    /// Pointers must be null or valid for their variant. See
    /// [`create_route`].
    unsafe fn resolve(self, cmd: &Cmd) -> RedisResult<Option<RoutingInfo>> {
        match self {
            RouteInput::ProtobufBytes {
                route_bytes,
                route_bytes_len,
            } => unsafe { Self::resolve_protobuf_bytes(route_bytes, route_bytes_len, cmd) },
            RouteInput::RouteInfo(route_info) => unsafe { Ok(create_route(route_info, Some(cmd))) },
        }
    }

    /// Decodes `route_bytes` as protobuf `Routes` (or defaults when null),
    /// then converts via [`get_route`].
    ///
    /// # Safety
    /// `route_bytes` must either point to valid `route_bytes_len` bytes or be null.
    unsafe fn resolve_protobuf_bytes(
        route_bytes: *const u8,
        route_bytes_len: usize,
        cmd: &Cmd,
    ) -> RedisResult<Option<RoutingInfo>> {
        let route = if !route_bytes.is_null() {
            let r_bytes = unsafe { std::slice::from_raw_parts(route_bytes, route_bytes_len) };
            Routes::parse_from_bytes(r_bytes).map_err(|err| {
                RedisError::from((
                    ErrorKind::ClientError,
                    "Decoding route failed",
                    err.to_string(),
                ))
            })?
        } else {
            Routes::default()
        };
        get_route(route, Some(cmd))
    }
}

/// Executes a command, optionally copying a BulkString response directly into a
/// caller-provided buffer instead of returning it as a heap-allocated value.
///
/// When `response_buf` is null (and `response_buf_len` is 0), behaves identically
/// to [`command`] — the response flows through the normal `execute_request` path.
///
/// When `response_buf` is non-null, the response is written directly into the buffer:
/// - `response.string_value` = number of bytes written as a string, or Nil response for missing keys.
/// - Errors if the value exceeds `response_buf_len`.
///
/// # Safety
///
/// * `client_adapter_ptr` must not be `null` and must be obtained from the `ConnectionResponse` returned from [`create_client`].
/// * `client_adapter_ptr` must be able to be safely casted to a valid [`Arc<ClientAdapter>`] via [`Arc::from_raw`]. See the safety documentation of [`std::sync::Arc::from_raw`].
/// * `request_id` must be a request ID from the foreign language and must be valid until either `success_callback` or `failure_callback` is finished.
/// * `args` is an optional bytes pointers array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `args_len` is an optional bytes length array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `arg_count` the number of elements in `args` and `args_len`. It must also not be greater than the max value of a signed pointer-sized integer.
/// * `arg_count` must be 0 if `args` and `args_len` are null.
/// * `args` and `args_len` must either be both null or be both not null.
/// * `route_bytes` is an optional array of bytes that will be parsed into a Protobuf `Routes` object. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `route_bytes_len` is the number of bytes in `route_bytes`. It must also not be greater than the max value of a signed pointer-sized integer.
/// * `route_bytes_len` must be 0 if `route_bytes` is null.
/// * When non-null, `response_buf` must point to a writable buffer of at least `response_buf_len` bytes.
/// * `response_buf_len` must be 0 if `response_buf` is null.
/// * `span_ptr` is a valid pointer to [`Arc<GlideSpan>`], a span created by [`create_otel_span`] or `0`. The span must be valid until the command is finished.
/// * This function should only be called with a `client_adapter_ptr` created by [`create_client`], before [`close_client`] was called with the pointer.
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn command_with_buffer(
    client_adapter_ptr: *const c_void,
    request_id: usize,
    command_type: RequestType,
    arg_count: c_ulong,
    args: *const usize,
    args_len: *const c_ulong,
    route_bytes: *const u8,
    route_bytes_len: usize,
    response_buf: *mut u8,
    response_buf_len: usize,
    span_ptr: u64,
) -> *mut CommandResult {
    let buf_option = if response_buf.is_null() {
        None
    } else {
        Some(ResponseBuffer::Single((response_buf, response_buf_len)))
    };
    unsafe {
        execute_command_with_buffer(
            client_adapter_ptr,
            request_id,
            command_type,
            arg_count,
            args,
            args_len,
            route_bytes,
            route_bytes_len,
            buf_option,
            span_ptr,
        )
    }
}

/// Append a caller-provided argument to `cmd` with minimal copying.
///
/// Large payloads are copied ONCE at the FFI boundary into a refcounted
/// `Bytes` (the caller's buffer is only guaranteed to live until this call
/// returns) and then written to the socket zero-copy via vectored I/O —
/// instead of being copied again at packing and encode time. Small args are
/// inlined as before.
fn append_cmd_arg(cmd: &mut Cmd, arg: &[u8]) {
    // `write_arg` auto-shares large args (> SHARED_ARG_INLINE_MAX) through
    // the recycled buffer pool: one pooled copy at the FFI boundary, then
    // zero-copy vectored I/O to the socket. A direct
    // `Bytes::copy_from_slice` here would allocate fresh per command and
    // re-introduce page-fault churn under pipelined load (the buffer lives
    // until the socket write completes).
    cmd.arg(arg);
}

/// Like [`append_cmd_arg`] for owned buffers (compressed args): the `Vec` is
/// adopted as-is via `Bytes::from_owner`, so large args are never copied at
/// all. (`Bytes::from(Vec)` would shrink-to-fit — a realloc + full copy —
/// whenever the compressor left excess capacity.)
fn append_cmd_arg_owned(cmd: &mut Cmd, arg: Vec<u8>) {
    if arg.len() > redis::SHARED_ARG_INLINE_MAX {
        cmd.arg_shared(bytes::Bytes::from_owner(arg));
    } else {
        cmd.arg(arg);
    }
}

/// The underlying command execution implementation. Used by various command
/// execution API like: [`command_with_buffer`], [`command_with_buffers`],
/// and [`command_with_route_info`]. It builds and dispatches the command,
/// writing the reply into `response_buffer` when present (a single buffer for
/// a scalar reply, one buffer per element for an array reply).
///
/// # Safety
/// `client_adapter_ptr` and `args`/`args_len` follow the same contract as
/// [`command_with_buffer`]. `route_input` follows the safety contract
/// documented on [`RouteInput::resolve`]. Any buffers referenced by
/// `response_buffer` must remain valid and writable until the command completes.
// Mirrors the C ABI of the FFI entry points it backs, hence the argument count.
#[allow(clippy::too_many_arguments)]
unsafe fn execute_command(
    client_adapter_ptr: *const c_void,
    request_id: usize,
    command_type: RequestType,
    arg_count: c_ulong,
    args: *const usize,
    args_len: *const c_ulong,
    route_input: RouteInput,
    response_buffer: Option<ResponseBuffer>,
    span_ptr: u64,
) -> *mut CommandResult {
    let client_adapter = unsafe {
        // we increment the strong count to ensure that the client is not dropped just because we turned it into an Arc.
        Arc::increment_strong_count(client_adapter_ptr);
        Arc::from_raw(client_adapter_ptr as *mut ClientAdapter)
    };

    let arg_vec: Vec<&[u8]> = if !args.is_null() && !args_len.is_null() {
        unsafe { convert_double_pointer_to_vec(args as *const *const c_void, arg_count, args_len) }
    } else {
        Vec::new()
    };

    // Create the command outside of the task to ensure that the command arguments passed
    // from the foreign code are still valid
    let mut cmd = match command_type.get_command() {
        Some(cmd) => cmd,
        None => {
            let err = RedisError::from((ErrorKind::ClientError, "Couldn't fetch command type"));
            return unsafe { client_adapter.handle_redis_error(err, request_id) };
        }
    };

    // Check if compression is enabled before converting args
    let compression_manager = client_adapter.core.client.compression_manager();

    let should_process_compression = compression_manager
        .as_ref()
        .map(|cm| cm.is_enabled())
        .unwrap_or(false);

    if should_process_compression {
        // Convert arg_vec to owned Vec<Vec<u8>> for compression processing
        let mut owned_args: Vec<Vec<u8>> = arg_vec.iter().map(|&arg| arg.to_vec()).collect();

        // For CustomCommand, we need to determine the actual command type from the first argument
        // and process compression on args[1..] since args[0] is the command name
        let is_custom_command = matches!(command_type, RequestType::CustomCommand);
        let effective_command_type = if is_custom_command {
            resolve_custom_command_type(&owned_args)
        } else {
            command_type
        };

        // Apply compression to command arguments
        // For CustomCommand, skip the first argument (command name) when processing compression
        let args_to_compress = if is_custom_command && !owned_args.is_empty() {
            &mut owned_args[1..]
        } else {
            &mut owned_args[..]
        };

        if let Err(err) = glide_core::compression::process_command_args_for_compression(
            args_to_compress,
            effective_command_type,
            compression_manager.as_deref(),
        ) {
            let err = RedisError::from((
                ErrorKind::ClientError,
                "Compression failed",
                err.to_string(),
            ));
            return unsafe { client_adapter.handle_redis_error(err, request_id) };
        }

        // Use the compressed arguments (owned: Vec->Bytes is zero-copy)
        for command_arg in owned_args {
            append_cmd_arg_owned(&mut cmd, command_arg);
        }
    } else {
        // Use the original arguments
        for command_arg in &arg_vec {
            append_cmd_arg(&mut cmd, command_arg);
        }
    }

    if span_ptr != 0 {
        cmd.set_span(unsafe { get_unsafe_span_from_ptr(Some(span_ptr)) });
    }

    if let Some(ref span) = cmd.span() {
        set_db_attributes(span, &cmd, &client_adapter.core.client);
    }

    let routing_info = match unsafe { route_input.resolve(&cmd) } {
        Ok(routing_info) => routing_info,
        Err(err) => {
            return unsafe { client_adapter.handle_redis_error(err, request_id) };
        }
    };

    // Inflight tracking is handled by send_command() via InflightRequestTracker on Cmd.
    let mut client = client_adapter.core.client.clone();

    // Abandon monitor integration: for pool-borrowed clients refresh the inactivity
    // timer on every command, and mark blocking commands so the monitor skips them
    // while they are in flight. Non-pooled clients skip this entirely (no map lookup cost).
    #[cfg(feature = "pool-support")]
    let blocking_flag = crate::pool_ffi::get_pool_adapter_map()
        .get(&(client_adapter_ptr as usize))
        .map(|entry| *entry.value())
        .and_then(|(pool_id, client_id)| {
            glide_core::pool::refresh_client_activity(pool_id, client_id);
            if glide_core::client::is_blocking_command(&cmd)
                && glide_core::pool::mark_client_blocking(pool_id, client_id, true)
            {
                Some((pool_id, client_id))
            } else {
                None
            }
        });
    #[cfg(not(feature = "pool-support"))]
    let blocking_flag: Option<(u64, u64)> = None;

    client_adapter.execute_request_with_buffer(
        request_id,
        async move {
            let result = client.send_command(&mut cmd, routing_info).await;
            // Unmark blocking after command completes
            #[cfg(feature = "pool-support")]
            if let Some((pool_id, client_id)) = blocking_flag {
                glide_core::pool::mark_client_blocking(pool_id, client_id, false);
            }
            let _ = blocking_flag; // suppress unused warning when pool-support disabled
            result
        },
        response_buffer,
    )
}

/// Thin wrapper around [`execute_command`] that resolves routing from
/// protobuf-encoded `route_bytes`, matching the contract shared by
/// [`command_with_buffer`] and [`command_with_buffers`].
///
/// # Parameters
/// - `client_adapter_ptr`: Pointer to the `Arc<ClientAdapter>` obtained from
///   [`create_client`]. The strong count is incremented so the caller's
///   original `Arc` remains valid.
/// - `request_id`: Caller-supplied ID used to correlate the eventual
///   response (or async callback invocation) with this call.
/// - `command_type`: Identifies which Valkey/Redis command to run; mapped to
///   the internal `Cmd` via `command_type.get_command()`.
/// - `arg_count`: Number of elements in the `args` / `args_len` arrays.
/// - `args`: Pointer to an array of pointers, each pointing to one command
///   argument's raw bytes. Caller-owned; must remain valid for the duration
///   of the call.
/// - `args_len`: Parallel array giving the byte length of each argument
///   pointed to by `args`. Must be null iff `args` is null.
/// - `route_bytes`: Optional protobuf-encoded `Routes` message describing
///   where to send the command (e.g. specific node, all primaries). Null
///   means no explicit route (default routing is used).
/// - `route_bytes_len`: Number of bytes in `route_bytes`; must be `0` when
///   `route_bytes` is null.
/// - `response_buffer`: Optional caller-provided buffer(s) for zero-copy
///   response writing — `Single` for a scalar reply (e.g. GET), `Multi` with
///   one buffer per element for an array reply (e.g. MGET). When `None`, the
///   response is heap/arena-allocated and returned normally.
/// - `span_ptr`: Pointer (as `u64`) to an `Arc<GlideSpan>` OpenTelemetry span
///   created by `create_otel_span`, or `0` for no tracing. When non-zero, it
///   is attached to the command so the request is traced.
///
/// # Safety
/// `client_adapter_ptr`, `args`/`args_len`, and `route_bytes` follow the same
/// contract as [`command_with_buffer`]. Any buffers referenced by `response_buffer`
/// must remain valid and writable until the command completes.
// Mirrors the C ABI of the FFI entry points it backs, hence the argument count.
#[allow(clippy::too_many_arguments)]
unsafe fn execute_command_with_buffer(
    client_adapter_ptr: *const c_void,
    request_id: usize,
    command_type: RequestType,
    arg_count: c_ulong,
    args: *const usize,
    args_len: *const c_ulong,
    route_bytes: *const u8,
    route_bytes_len: usize,
    response_buffer: Option<ResponseBuffer>,
    span_ptr: u64,
) -> *mut CommandResult {
    unsafe {
        execute_command(
            client_adapter_ptr,
            request_id,
            command_type,
            arg_count,
            args,
            args_len,
            RouteInput::ProtobufBytes {
                route_bytes,
                route_bytes_len,
            },
            response_buffer,
            span_ptr,
        )
    }
}

/// MGET-style variant of [`command_with_buffer`] that writes each element of an
/// array response into its own caller-provided buffer (zero-copy multi-key GET).
///
/// `response_bufs` / `response_buf_lens` are parallel arrays of
/// `response_buf_count` writable buffers; element `i` of the array response is
/// copied into buffer `i`. A `Nil` element (e.g. a missing key) leaves its
/// buffer untouched and is reported as a null response. The command response
/// carries the number of bytes written per element instead of the value bytes.
///
/// Intended for commands whose reply is a flat array of bulk strings (e.g.
/// `MGET`) or a scalar bulk string. If an element's value is larger than its
/// buffer, the whole command fails with a client error. A null buffer entry, or
/// fewer buffers than reply elements, makes the affected elements fall back to a
/// normal heap-allocated response (no zero-copy) rather than erroring.
///
/// # Safety
/// Same contract as [`command_with_buffer`]. When non-null, `response_bufs` and
/// `response_buf_lens` must each point to `response_buf_count` valid entries.
/// Each buffer must remain valid and writable until the command **completes**:
/// for sync clients that is until this call returns; for async clients it is
/// until the success/failure callback fires (the copy happens off-thread).
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn command_with_buffers(
    client_adapter_ptr: *const c_void,
    request_id: usize,
    command_type: RequestType,
    arg_count: c_ulong,
    args: *const usize,
    args_len: *const c_ulong,
    route_bytes: *const u8,
    route_bytes_len: usize,
    response_bufs: *const *mut u8,
    response_buf_lens: *const usize,
    response_buf_count: usize,
    span_ptr: u64,
) -> *mut CommandResult {
    // Collect the caller's per-element buffers into a single owned Vec, consumed
    // positionally by the arena builder (element i -> buffer i). Both parallel
    // arrays must be non-null; otherwise the request runs without buffers.
    let buf_option =
        if response_bufs.is_null() || response_buf_lens.is_null() || response_buf_count == 0 {
            None
        } else {
            let ptrs = unsafe { std::slice::from_raw_parts(response_bufs, response_buf_count) };
            let lens = unsafe { std::slice::from_raw_parts(response_buf_lens, response_buf_count) };
            Some(ResponseBuffer::Multi(
                ptrs.iter()
                    .zip(lens.iter())
                    .map(|(&p, &l)| (p, l))
                    .collect(),
            ))
        };
    unsafe {
        execute_command_with_buffer(
            client_adapter_ptr,
            request_id,
            command_type,
            arg_count,
            args,
            args_len,
            route_bytes,
            route_bytes_len,
            buf_option,
            span_ptr,
        )
    }
}

/// Creates a heap-allocated `CommandResult` containing a `CommandError`.
///
/// This function is used to construct an error response when a Valkey command fails,
/// intended to be returned through FFI to the calling language.
///
/// The resulting `CommandResult` contains:
/// - A null `response` pointer.
/// - A valid `command_error` pointer with error details (message and type).
///
/// # Parameters
/// - `err`: The `RedisError` to be converted into a `CommandError`.
///
/// # Returns
/// A raw pointer to a `CommandResult`. This must be freed using [`free_command_result`]
/// to avoid memory leaks.
///
/// # Safety
/// The returned pointer must be passed back to Rust for cleanup. Failing to call
/// [`free_command_result`] will result in a memory leak.
fn create_error_result_with_redis_error(err: RedisError) -> *mut CommandResult {
    let (c_err_str, error_type) = to_c_error(err);
    Box::into_raw(Box::new(CommandResult {
        response: std::ptr::null_mut(),
        command_error: Box::into_raw(Box::new(CommandError {
            command_error_message: c_err_str,
            command_error_type: error_type,
        })),
        arena: std::ptr::null_mut(),
    }))
}

/// Creates a heap-allocated `CommandResult` containing a `CommandError`.
///
/// This function is used to construct an error response when a Valkey command fails,
/// intended to be returned through FFI to the calling language.
///
/// The resulting `CommandResult` contains:
/// - A null `response` pointer.
/// - A valid `command_error` pointer with error details (message and type).
///
/// # Parameters
/// - `error_string`: The error message to be converted into a `CommandError`.
/// - `error_type`: The error type.
///
/// # Returns
/// A raw pointer to a `CommandResult`. This must be freed using [`free_command_result`]
/// to avoid memory leaks.
///
/// # Safety
/// The returned pointer must be passed back to Rust for cleanup. Failing to call
/// [`free_command_result`] will result in a memory leak.
fn create_error_result_with_custom_error(
    error_string: String,
    error_type: RequestErrorType,
) -> *mut CommandResult {
    let c_err_str = CString::into_raw(
        CString::new(error_string).expect("Couldn't convert error message to CString"),
    );
    Box::into_raw(Box::new(CommandResult {
        response: std::ptr::null_mut(),
        command_error: Box::into_raw(Box::new(CommandError {
            command_error_message: c_err_str,
            command_error_type: error_type,
        })),
        arena: std::ptr::null_mut(),
    }))
}

/// Converts a `RedisError` into a C-compatible error representation.
///
/// This helper function extracts the error message and error type,
/// and returns a raw C string pointer (`*const c_char`) along with
/// the corresponding [`RequestErrorType`].
///
/// # Parameters
/// - `err`: The `RedisError` to convert.
///
/// # Returns
/// A tuple containing:
/// - A raw C string (`*const c_char`) containing the error message.
/// - A `RequestErrorType` representing the kind of error.
///
/// # Panics
/// This function will panic if the error message cannot be converted into a `CString`.
fn to_c_error(err: RedisError) -> (*const c_char, RequestErrorType) {
    let message = errors::error_message(&err);
    let error_type = errors::error_type(&err);

    let c_err_str = CString::into_raw(
        CString::new(message).expect("Couldn't convert error message to CString"),
    );
    (c_err_str, error_type)
}

fn get_route(route: Routes, cmd: Option<&Cmd>) -> RedisResult<Option<RoutingInfo>> {
    use glide_core::command_request::routes::Value;
    let route = match route.value {
        Some(route) => route,
        None => return Ok(None),
    };
    let get_response_policy = |cmd: Option<&Cmd>| {
        cmd.and_then(|cmd| {
            cmd.command()
                .and_then(|cmd| ResponsePolicy::for_command(&cmd))
        })
    };
    match route {
        Value::SimpleRoutes(simple_route) => {
            let simple_route = match simple_route.enum_value() {
                Ok(simple_route) => simple_route,
                Err(value) => {
                    return Err(RedisError::from((
                        ErrorKind::ClientError,
                        "simple_route was not a valid enum variant",
                        format!("Value: {value}"),
                    )));
                }
            };
            match simple_route {
                SimpleRoutes::AllNodes => Ok(Some(RoutingInfo::MultiNode((
                    MultipleNodeRoutingInfo::AllNodes,
                    get_response_policy(cmd),
                )))),
                SimpleRoutes::AllPrimaries => Ok(Some(RoutingInfo::MultiNode((
                    MultipleNodeRoutingInfo::AllMasters,
                    get_response_policy(cmd),
                )))),
                SimpleRoutes::Random => {
                    Ok(Some(RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random)))
                }
            }
        }
        Value::SlotKeyRoute(slot_key_route) => Ok(Some(RoutingInfo::SingleNode(
            SingleNodeRoutingInfo::SpecificNode(Route::new(
                redis::cluster_topology::get_slot(slot_key_route.slot_key.as_bytes()),
                get_slot_addr(&slot_key_route.slot_type)?,
            )),
        ))),
        Value::SlotIdRoute(slot_id_route) => Ok(Some(RoutingInfo::SingleNode(
            SingleNodeRoutingInfo::SpecificNode(Route::new(
                slot_id_route.slot_id as u16,
                get_slot_addr(&slot_id_route.slot_type)?,
            )),
        ))),
        Value::ByAddressRoute(by_address_route) => match u16::try_from(by_address_route.port) {
            Ok(port) => Ok(Some(RoutingInfo::SingleNode(
                SingleNodeRoutingInfo::ByAddress {
                    host: by_address_route.host.to_string(),
                    port,
                },
            ))),
            Err(_) => Err(RedisError::from((
                ErrorKind::ClientError,
                "by_address_route port could not be converted to u16.",
                format!("Value: {}", by_address_route.port),
            ))),
        },
        _ => Err(RedisError::from((
            ErrorKind::ClientError,
            "Unknown route type.",
            format!("Value: {route:?}"),
        ))),
    }
}

fn get_slot_addr(slot_type: &protobuf::EnumOrUnknown<SlotTypes>) -> RedisResult<SlotAddr> {
    let slot_addr_result = slot_type.enum_value().map(|slot_type| match slot_type {
        SlotTypes::Primary => SlotAddr::Master,
        SlotTypes::Replica => SlotAddr::ReplicaRequired,
    });
    slot_addr_result.map_err(|_| {
        RedisError::from((
            ErrorKind::ClientError,
            "Received unexpected slot id type",
            format!("Value: {slot_type:?}"),
        ))
    })
}

/// Allows the client to request a cluster scan command to be executed.
///
/// `client_adapter_ptr` is a pointer to a valid `GlideClusterClient` returned in the `ConnectionResponse` from [`create_client`].
/// `request_id` is a unique identifier for a valid payload buffer which is created in the client.
/// `cursor` is a cursor string.
/// `arg_count` keeps track of how many option arguments are passed in the client.
/// `args` is a pointer to C string representation of the string args.
/// `args_len` is a pointer to the lengths of the C string representation of the string args.
/// `success_callback` is the callback that will be called when a command succeeds.
/// `failure_callback` is the callback that will be called when a command fails.
///
/// # Safety
///
/// * `client_adapter_ptr` must be obtained from the `ConnectionResponse` returned from [`create_client`].
/// * `client_adapter_ptr` must be valid until `close_client` is called.
/// * `request_id` must be valid until it is passed in a call to [`free_command_response`].
/// * `cursor` must not be null. It must point to a valid C string ([`CStr`]). See the safety documentation of [`CStr::from_ptr`].
/// * `cursor` must remain valid until the end of this call. The caller is responsible for freeing the memory allocated for this string.
/// * Both the `success_callback` and `failure_callback` function pointers need to live while the client is open/active. The caller is responsible for freeing both callbacks.
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn request_cluster_scan(
    client_adapter_ptr: *const c_void,
    request_id: usize,
    cursor: *const c_char,
    arg_count: c_ulong,
    args: *const usize,
    args_len: *const c_ulong,
) -> *mut CommandResult {
    let client_adapter = unsafe {
        // we increment the strong count to ensure that the client is not dropped just because we turned it into an Arc.
        Arc::increment_strong_count(client_adapter_ptr);
        Arc::from_raw(client_adapter_ptr as *mut ClientAdapter)
    };
    let cursor_id = unsafe { CStr::from_ptr(cursor) }
        .to_str()
        .unwrap_or("0")
        .to_owned();

    let cluster_scan_args: ClusterScanArgs = if arg_count > 0 {
        let arg_vec = unsafe {
            convert_double_pointer_to_vec(args as *const *const c_void, arg_count, args_len)
        };

        let mut pattern: &[u8] = &[];
        let mut object_type: &[u8] = &[];
        let mut count: &[u8] = &[];
        let mut allow_non_covered_slots: bool = false;

        let mut iter = arg_vec.iter().peekable();
        while let Some(arg) = iter.next() {
            match *arg {
                b"MATCH" => match iter.next() {
                    Some(pat) => pattern = pat,
                    None => {
                        let err = RedisError::from((
                            ErrorKind::ClientError,
                            "No argument following MATCH.",
                        ));
                        return unsafe { client_adapter.handle_redis_error(err, request_id) };
                    }
                },
                b"TYPE" => match iter.next() {
                    Some(obj_type) => object_type = obj_type,
                    None => {
                        let err = RedisError::from((
                            ErrorKind::ClientError,
                            "No argument following TYPE.",
                        ));
                        return unsafe { client_adapter.handle_redis_error(err, request_id) };
                    }
                },
                b"COUNT" => match iter.next() {
                    Some(c) => count = c,
                    None => {
                        let err = RedisError::from((
                            ErrorKind::ClientError,
                            "No argument following COUNT.",
                        ));
                        return unsafe { client_adapter.handle_redis_error(err, request_id) };
                    }
                },
                b"ALLOW_NON_COVERED_SLOTS" => {
                    allow_non_covered_slots = true;
                }
                _ => {
                    // Unknown or unsupported arg — safely skip or log
                    continue;
                }
            }
        }

        // Convert back to proper types
        let converted_count = match str::from_utf8(count) {
            Ok(v) => {
                if !count.is_empty() {
                    match str::parse::<u32>(v) {
                        Ok(v) => v,
                        Err(e) => {
                            return unsafe {
                                client_adapter.handle_redis_error(RedisError::from(e), request_id)
                            };
                        }
                    }
                } else {
                    10 // default count value
                }
            }
            Err(e) => {
                return unsafe {
                    client_adapter.handle_redis_error(RedisError::from(e), request_id)
                };
            }
        };

        let converted_type = match str::from_utf8(object_type) {
            Ok(v) => ObjectType::from(v.to_string()),
            Err(e) => {
                return unsafe {
                    client_adapter.handle_redis_error(RedisError::from(e), request_id)
                };
            }
        };

        let mut cluster_scan_args_builder = ClusterScanArgs::builder();
        if !count.is_empty() {
            cluster_scan_args_builder = cluster_scan_args_builder.with_count(converted_count);
        }
        if !pattern.is_empty() {
            cluster_scan_args_builder = cluster_scan_args_builder.with_match_pattern(pattern);
        }
        if !object_type.is_empty() {
            cluster_scan_args_builder = cluster_scan_args_builder.with_object_type(converted_type);
        }
        cluster_scan_args_builder =
            cluster_scan_args_builder.allow_non_covered_slots(allow_non_covered_slots);
        cluster_scan_args_builder.build()
    } else {
        ClusterScanArgs::builder().build()
    };

    let scan_state_cursor = if cursor_id.is_empty() || cursor_id == "0" {
        ScanStateRC::new()
    } else {
        match get_cluster_scan_cursor(cursor_id) {
            Ok(existing_cursor) => existing_cursor,
            Err(err) => {
                return unsafe { client_adapter.handle_redis_error(err, request_id) };
            }
        }
    };
    let mut client = client_adapter.core.client.clone();
    client_adapter.execute_request(request_id, async move {
        client
            .cluster_scan(&scan_state_cursor, cluster_scan_args)
            .await
    })
}

/// Remove a cluster scan cursor from the container.
///
/// `cursor_id` is the cursor ID returned by a previous cluster scan operation.
///
/// # Safety
/// * `cursor_id` must point to a valid C string.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn remove_cluster_scan_cursor(cursor_id: *const c_char) {
    if cursor_id.is_null() {
        return;
    }

    if let Ok(cursor_str) = unsafe { CStr::from_ptr(cursor_id).to_str() } {
        glide_core::cluster_scan_container::remove_scan_state_cursor(cursor_str.to_string());
    }
}

/// Allows the client to request an update to the connection password.
///
/// `client_adapter_ptr` is a pointer to a valid `GlideClusterClient` returned in the `ConnectionResponse` from [`create_client`].
/// `request_id` is a unique identifier for a valid payload buffer which is created in the client.
/// `password` is a pointer to C string representation of the password.
/// `immediate_auth` is a boolean flag to indicate if the password should be updated immediately.
/// `success_callback` is the callback that will be called when a command succeeds.
/// `failure_callback` is the callback that will be called when a command fails.
///
/// # Safety
///
/// * `client_adapter_ptr` must be obtained from the `ConnectionResponse` returned from [`create_client`].
/// * `client_adapter_ptr` must be valid until `close_client` is called.
/// * `request_id` must be valid until it is passed in a call to [`free_command_response`].
/// * Both the `success_callback` and `failure_callback` function pointers need to live while the client is open/active. The caller is responsible for freeing both callbacks.
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn update_connection_password(
    client_adapter_ptr: *const c_void,
    request_id: usize,
    password: *const c_char,
    immediate_auth: bool,
) -> *mut CommandResult {
    let client_adapter = unsafe {
        // we increment the strong count to ensure that the client is not dropped just because we turned it into an Arc.
        Arc::increment_strong_count(client_adapter_ptr);
        Arc::from_raw(client_adapter_ptr as *mut ClientAdapter)
    };

    // argument conversion to be used in the async block
    let password = match unsafe { CStr::from_ptr(password).to_str() } {
        Ok(password) => password,
        Err(e) => {
            return unsafe { client_adapter.handle_redis_error(RedisError::from(e), request_id) };
        }
    };
    let password_option = if password.is_empty() {
        None
    } else {
        Some(password.to_string())
    };
    let mut client = client_adapter.core.client.clone();
    client_adapter.execute_request(request_id, async move {
        client
            .update_connection_password(password_option, immediate_auth)
            .await
    })
}

/// Manually refresh the IAM authentication token.
///
/// This function triggers an immediate refresh of the IAM token and updates the connection.
/// It is only available if the client was created with IAM authentication.
///
/// # Parameters
///
/// * `client_adapter_ptr`: Pointer to a valid client returned from [`create_client`].
/// * `request_id`: Unique identifier for a valid payload buffer created in the calling language.
///
/// # Returns
///
/// * A pointer to a [`CommandResult`] containing "OK" on success, or an error if:
///   - The client is not using IAM authentication
///   - Token generation fails
///   - Authentication with the new token fails
///
/// # Safety
///
/// * `client_adapter_ptr` must not be `null` and must be obtained from the `ConnectionResponse` returned from [`create_client`].
/// * `client_adapter_ptr` must be able to be safely casted to a valid [`Arc<ClientAdapter>`] via [`Arc::from_raw`].
/// * `request_id` must be valid until it is passed in a call to [`free_command_response`].
/// * This function should only be called with a `client_adapter_ptr` created by [`create_client`], before [`close_client`] was called with the pointer.
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn refresh_iam_token(
    client_adapter_ptr: *const c_void,
    request_id: usize,
) -> *mut CommandResult {
    let client_adapter = unsafe {
        // we increment the strong count to ensure that the client is not dropped just because we turned it into an Arc.
        Arc::increment_strong_count(client_adapter_ptr);
        Arc::from_raw(client_adapter_ptr as *mut ClientAdapter)
    };

    let mut client = client_adapter.core.client.clone();
    client_adapter.execute_request(request_id, async move {
        client.refresh_iam_token().await.map(|_| Value::Okay)
    })
}

/// Get cache metrics for the client.
///
/// This function retrieves cache performance metrics such as hit rate, miss rate,
/// entry count, evictions, expirations and total lookups based on the specified metrics type.
///
/// # Parameters
///
/// * `client_adapter_ptr`: Pointer to a valid client returned from [`create_client`].
/// * `request_id`: Unique identifier for a valid payload buffer created in the calling language.
/// * `metrics_type`: Integer representing the type of cache metrics to retrieve:
///   - 0: HitRate - Cache hit rate as a double (0.0 to 1.0)
///   - 1: MissRate - Cache miss rate as a double (0.0 to 1.0)
///   - 2: EntryCount - Number of entries in cache as an integer
///   - 3: Evictions - Number of cache evictions as an integer
///   - 4: Expirations - Number of cache expirations as an integer
///   - 5: TotalLookups - Total cache lookups as an integer
///
/// # Returns
///
/// * A pointer to a [`CommandResult`] containing the requested metric value on success, or an error if:
///   - Client-side caching is not enabled
///   - Metrics tracking is disabled
///   - Invalid metrics type is specified
///   - Client is closed or invalid
///
/// # Safety
///
/// * `client_adapter_ptr` must not be `null` and must be obtained from the `ConnectionResponse` returned from [`create_client`].
/// * `client_adapter_ptr` must be able to be safely casted to a valid [`Arc<ClientAdapter>`] via [`Arc::from_raw`].
/// * `request_id` must be valid until it is passed in a call to [`free_command_response`].
/// * This function should only be called with a `client_adapter_ptr` created by [`create_client`], before [`close_client`] was called with the pointer.
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn get_cache_metrics(
    client_adapter_ptr: *const c_void,
    request_id: usize,
    metrics_type: i32,
) -> *mut CommandResult {
    let client_adapter = unsafe {
        Arc::increment_strong_count(client_adapter_ptr);
        Arc::from_raw(client_adapter_ptr as *mut ClientAdapter)
    };

    let client = client_adapter.core.client.clone();
    let result = match CacheMetricsType::from_i32(metrics_type) {
        Some(CacheMetricsType::HitRate) => client.cache_hit_rate(),
        Some(CacheMetricsType::MissRate) => client.cache_miss_rate(),
        Some(CacheMetricsType::EntryCount) => client.cache_entry_count(),
        Some(CacheMetricsType::Evictions) => client.cache_evictions(),
        Some(CacheMetricsType::Expirations) => client.cache_expirations(),
        Some(CacheMetricsType::TotalLookups) => client.cache_total_lookups(),
        None => Err(RedisError::from((
            ErrorKind::ClientError,
            "Invalid cache metrics type",
            format!("Unsupported metrics type: {}", metrics_type),
        ))),
    };
    // For pipe clients (Python async) and sync clients, return directly.
    // For callback clients (Go, etc.), use execute_request to invoke callbacks.
    let cid = client_adapter
        .pipe_client_id
        .load(std::sync::atomic::Ordering::Relaxed);
    // Python pipe clients have cid != 0 AND ASYNC_PIPE initialized.
    // Go/other clients may have cid != 0 for address resolver but no pipe.
    let is_pipe_or_sync = matches!(client_adapter.core.client_type, ClientType::SyncClient)
        || (cid != 0 && get_async_pipe().is_some());
    if is_pipe_or_sync {
        match result {
            Ok(value) => match valkey_value_to_arena_response(value, &[]) {
                Ok((root_ptr, arena_ptr)) => Box::into_raw(Box::new(CommandResult {
                    response: root_ptr,
                    command_error: std::ptr::null_mut(),
                    arena: arena_ptr,
                })),
                Err(err) => create_error_result_with_redis_error(err),
            },
            Err(err) => create_error_result_with_redis_error(err),
        }
    } else {
        client_adapter.execute_request(request_id, async move { result })
    }
}

/// Executes a Lua script.
///
/// # Parameters
///
/// * `client_adapter_ptr`: Pointer to a valid `GlideClusterClient` returned from [`create_client`].
/// * `request_id`: Unique identifier for a valid payload buffer created in the calling language.
/// * `hash`: SHA1 hash of the script for script caching.
/// * `keys_count`: Number of keys in the keys array.
/// * `keys`: Array of keys used by the script.
/// * `keys_len`: Array of lengths for each key.
/// * `args_count`: Number of arguments in the args array.
/// * `args`: Array of arguments to pass to the script.
/// * `args_len`: Array of lengths for each argument.
/// * `route_bytes`: Optional array of bytes for routing information.
/// * `route_bytes_len`: Length of the route_bytes array.
///
/// # Safety
///
/// * `client_adapter_ptr` must not be `null` and must be obtained from the `ConnectionResponse` returned from [`create_client`].
/// * `client_adapter_ptr` must be able to be safely casted to a valid [`Arc<ClientAdapter>`] via [`Arc::from_raw`].
/// * `request_id` must be valid until either `success_callback` or `failure_callback` is finished.
/// * `hash` must be a valid null-terminated C string.
/// * `keys` is an optional bytes pointers array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `keys_len` is an optional bytes length array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `keys_count` must be 0 if `keys` and `keys_len` are null.
/// * `keys` and `keys_len` must either be both null or be both not null.
/// * `args` is an optional bytes pointers array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `args_len` is an optional bytes length array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `args_count` must be 0 if `args` and `args_len` are null.
/// * `args` and `args_len` must either be both null or be both not null.
/// * `route_bytes` is an optional array of bytes that will be parsed into a Protobuf `Routes` object. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `route_bytes_len` is the number of bytes in `route_bytes`. It must also not be greater than the max value of a signed pointer-sized integer.
/// * `route_bytes_len` must be 0 if `route_bytes` is null.
/// * `span_ptr` is a valid pointer to [`Arc<GlideSpan>`], a span created by [`create_otel_span`] or `0`. The span must be valid until the command is finished.
/// * This function should only be called with a `client_adapter_ptr` created by [`create_client`], before [`close_client`] was called with the pointer.
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn invoke_script(
    client_adapter_ptr: *const c_void,
    request_id: usize,
    hash: *const c_char,
    keys_count: c_ulong,
    keys: *const usize,
    keys_len: *const c_ulong,
    args_count: c_ulong,
    args: *const usize,
    args_len: *const c_ulong,
    route_bytes: *const u8,
    route_bytes_len: usize,
    span_ptr: u64,
) -> *mut CommandResult {
    let client_adapter = unsafe {
        // we increment the strong count to ensure that the client is not dropped just because we turned it into an Arc.
        Arc::increment_strong_count(client_adapter_ptr);
        Arc::from_raw(client_adapter_ptr as *mut ClientAdapter)
    };

    // Convert hash to Rust string
    let hash_str = match unsafe { CStr::from_ptr(hash).to_str() } {
        Ok(hash) => hash,
        Err(e) => {
            return unsafe { client_adapter.handle_redis_error(RedisError::from(e), request_id) };
        }
    };

    // Convert keys to Vec<&[u8]>
    let keys_vec: Vec<&[u8]> = if !keys.is_null() && !keys_len.is_null() && keys_count > 0 {
        unsafe { convert_double_pointer_to_vec(keys as *const *const c_void, keys_count, keys_len) }
    } else {
        Vec::new()
    };

    // Convert args to Vec<&[u8]>
    let args_vec: Vec<&[u8]> = if !args.is_null() && !args_len.is_null() && args_count > 0 {
        unsafe { convert_double_pointer_to_vec(args as *const *const c_void, args_count, args_len) }
    } else {
        Vec::new()
    };

    if span_ptr != 0
        && let Some(span) = unsafe { get_unsafe_span_from_ptr(Some(span_ptr)) }
    {
        set_db_script_attributes(
            &span,
            hash_str,
            &keys_vec,
            &args_vec,
            &client_adapter.core.client,
        );
    }

    // Parse routing information if provided
    let route = if !route_bytes.is_null() {
        let r_bytes = unsafe { std::slice::from_raw_parts(route_bytes, route_bytes_len) };
        match Routes::parse_from_bytes(r_bytes) {
            Ok(route) => route,
            Err(err) => {
                let err = RedisError::from((
                    ErrorKind::ClientError,
                    "Decoding route failed",
                    err.to_string(),
                ));
                return unsafe { client_adapter.handle_redis_error(err, request_id) };
            }
        }
    } else {
        Routes::default()
    };

    let mut client = client_adapter.core.client.clone();

    // Refresh pool activity for batch/script dispatch (same as execute_command)
    #[cfg(feature = "pool-support")]
    let script_pool_ids = crate::pool_ffi::get_pool_adapter_map()
        .get(&(client_adapter_ptr as usize))
        .map(|entry| *entry.value());
    #[cfg(feature = "pool-support")]
    if let Some((pool_id, client_id)) = script_pool_ids {
        glide_core::pool::refresh_client_activity(pool_id, client_id);
    }
    #[cfg(not(feature = "pool-support"))]
    let script_pool_ids: Option<(u64, u64)> = None;

    client_adapter.execute_request(request_id, async move {
        // Mark as blocking for duration of script execution
        #[cfg(feature = "pool-support")]
        if let Some((pool_id, client_id)) = script_pool_ids {
            glide_core::pool::mark_client_blocking(pool_id, client_id, true);
        }

        let routing_info = get_route(route, None)?;
        let result = client
            .invoke_script(hash_str, &keys_vec, &args_vec, routing_info)
            .await;

        // Unmark blocking after script completes
        #[cfg(feature = "pool-support")]
        if let Some((pool_id, client_id)) = script_pool_ids {
            glide_core::pool::mark_client_blocking(pool_id, client_id, false);
        }
        let _ = script_pool_ids;

        result
    })
}

#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub enum RouteType {
    AllNodes = 0,
    AllPrimaries,
    Random,
    SlotId,
    SlotKey,
    ByAddress,
}

/// A mirror of [`SlotAddr`]
#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub enum SlotType {
    Primary = 0,
    Replica,
}

impl From<&SlotType> for SlotAddr {
    fn from(val: &SlotType) -> Self {
        match val {
            SlotType::Primary => SlotAddr::Master,
            SlotType::Replica => SlotAddr::ReplicaRequired,
        }
    }
}

/// A structure which represents a route. To avoid extra pointer mandgling, it has fields for all route types.
/// Depending on [`RouteType`], the struct stores:
/// * Only `route_type` is filled, if route is a simple route;
/// * `route_type`, `slot_id` and `slot_type`, if route is a Slot ID route;
/// * `route_type`, `slot_key` and `slot_type`, if route is a Slot key route;
/// * `route_type`, `hostname` and `port`, if route is a Address route;
#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub struct RouteInfo {
    pub route_type: RouteType,
    pub slot_id: i32,
    /// zero pointer is valid, means no slot key is given (`None`)
    pub slot_key: *const c_char,
    pub slot_type: SlotType,
    /// zero pointer is valid, means no hostname is given (`None`)
    pub hostname: *const c_char,
    pub port: i32,
}

#[repr(C)]
#[derive(Clone, Debug, Copy)]
pub struct CmdInfo {
    pub request_type: RequestType,
    pub args: *const *const u8,
    pub arg_count: usize,
    pub args_len: *const usize,
}

#[repr(C)]
#[derive(Clone, Debug, Copy)]
pub struct BatchInfo {
    pub cmd_count: usize,
    pub cmds: *const *const CmdInfo,
    pub is_atomic: bool,
}

#[repr(C)]
#[derive(Clone, Debug, Copy)]
pub struct BatchOptionsInfo {
    // two params from PipelineRetryStrategy
    pub retry_server_error: bool,
    pub retry_connection_error: bool,
    pub has_timeout: bool,
    pub timeout: u32,
    pub route_info: *const RouteInfo,
}

/// Execute a batch.
///
/// # Safety
/// * `client_ptr` must not be `null`.
/// * `client_ptr` must be able to be safely casted to a valid [`Arc<ClientAdapter>`] via [`Arc::from_raw`]. See the safety documentation of [`Box::from_raw`].
/// * This function should only be called should with a pointer created by [`create_client`], before [`close_client`] was called with the pointer.
/// * `batch_ptr` must not be `null`.
/// * `batch_ptr` must be able to be safely casted to a valid [`BatchInfo`]. See the safety documentation of [`create_pipeline`].
/// * `options_ptr` could be `null`, but if it is not `null`, it must be a valid [`BatchOptionsInfo`] pointer. See the safety documentation of [`get_pipeline_options`].
#[allow(rustdoc::private_intra_doc_links)]
#[unsafe(no_mangle)]
pub unsafe extern "C" fn batch(
    client_ptr: *const c_void,
    callback_index: usize,
    batch_ptr: *const BatchInfo,
    raise_on_error: bool,
    options_ptr: *const BatchOptionsInfo,
    span_ptr: u64,
) -> *mut CommandResult {
    let client_adapter = unsafe {
        // we increment the strong count to ensure that the client is not dropped just because we turned it into an Arc.
        Arc::increment_strong_count(client_ptr);
        Arc::from_raw(client_ptr as *mut ClientAdapter)
    };
    let mut client = client_adapter.core.client.clone();

    // Refresh pool activity for batch/script dispatch (same as execute_command)
    #[cfg(feature = "pool-support")]
    let batch_pool_ids = crate::pool_ffi::get_pool_adapter_map()
        .get(&(client_ptr as usize))
        .map(|entry| *entry.value());
    #[cfg(feature = "pool-support")]
    if let Some((pool_id, client_id)) = batch_pool_ids {
        glide_core::pool::refresh_client_activity(pool_id, client_id);
    }
    #[cfg(not(feature = "pool-support"))]
    let batch_pool_ids: Option<(u64, u64)> = None;

    // Get compression manager for batch operations
    let compression_manager = client_adapter.core.client.compression_manager();
    // Clone for use in async block
    let compression_manager_for_decompression = compression_manager.clone();

    // TODO handle panics
    let mut pipeline = match unsafe { create_pipeline(batch_ptr, compression_manager.as_ref()) } {
        Ok(pipeline) => pipeline,
        Err(err) => {
            return unsafe {
                client_adapter.handle_custom_error(
                    err,
                    RequestErrorType::Unspecified,
                    callback_index,
                )
            };
        }
    };
    if span_ptr != 0 {
        pipeline.set_pipeline_span(unsafe { get_unsafe_span_from_ptr(Some(span_ptr)) });
    }

    if let Some(ref span) = pipeline.span() {
        set_db_batch_attributes(span, pipeline.commands(), &client_adapter.core.client);
    }

    let (routing, timeout, pipeline_retry_strategy) = unsafe { get_pipeline_options(options_ptr) };

    client_adapter.execute_request(callback_index, async move {
        // Mark as blocking for duration of batch execution
        #[cfg(feature = "pool-support")]
        if let Some((pool_id, client_id)) = batch_pool_ids {
            glide_core::pool::mark_client_blocking(pool_id, client_id, true);
        }

        let result = if pipeline.is_atomic() {
            client
                .send_transaction(&pipeline, routing, timeout, raise_on_error)
                .await
        } else {
            client
                .send_pipeline(
                    &pipeline,
                    routing,
                    raise_on_error,
                    timeout,
                    pipeline_retry_strategy,
                )
                .await
        };

        // Unmark blocking after batch completes
        #[cfg(feature = "pool-support")]
        if let Some((pool_id, client_id)) = batch_pool_ids {
            glide_core::pool::mark_client_blocking(pool_id, client_id, false);
        }
        let _ = batch_pool_ids;

        // Process batch response for decompression if compression is enabled
        match result {
            Ok(value) => glide_core::compression::try_decompress_batch_response(
                value,
                compression_manager_for_decompression.as_deref(),
            )
            .map_err(|e| {
                redis::RedisError::from((
                    redis::ErrorKind::IoError,
                    "Decompression error",
                    e.to_string(),
                ))
            }),
            Err(e) => Err(e),
        }
    })
}

/// Convert raw C string to a rust string.
///
/// # Safety
///
/// * `ptr` must be able to be safely casted to a valid [`CStr`] via [`CStr::from_ptr`]. See the safety documentation of [`std::ffi::CStr::from_ptr`].
unsafe fn ptr_to_str(ptr: *const c_char) -> String {
    if !ptr.is_null() {
        unsafe { CStr::from_ptr(ptr) }.to_str().unwrap().into()
    } else {
        "".into()
    }
}

/// Convert route configuration to a corresponding object.
///
/// # Safety
/// * `route_ptr` could be `null`, but if it is not `null`, it must be a valid pointer to a [`RouteInfo`] struct.
/// * `slot_key` and `hostname` in dereferenced [`RouteInfo`] struct must contain valid string pointers when corresponding `route_type` is set.
///   See description of [`RouteInfo`] and the safety documentation of [`ptr_to_str`].
pub(crate) unsafe fn create_route(
    route_ptr: *const RouteInfo,
    cmd: Option<&Cmd>,
) -> Option<RoutingInfo> {
    if route_ptr.is_null() {
        return None;
    }
    let route = unsafe { *route_ptr };
    match route.route_type {
        RouteType::Random => Some(RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random)),
        RouteType::AllNodes => Some(RoutingInfo::MultiNode((
            MultipleNodeRoutingInfo::AllNodes,
            cmd.and_then(|c| ResponsePolicy::for_command(&c.command().unwrap())),
        ))),
        RouteType::AllPrimaries => Some(RoutingInfo::MultiNode((
            MultipleNodeRoutingInfo::AllMasters,
            cmd.and_then(|c| ResponsePolicy::for_command(&c.command().unwrap())),
        ))),
        RouteType::SlotId => Some(RoutingInfo::SingleNode(
            SingleNodeRoutingInfo::SpecificNode(Route::new(
                route.slot_id as u16,
                (&route.slot_type).into(),
            )),
        )),
        RouteType::SlotKey => Some(RoutingInfo::SingleNode(
            SingleNodeRoutingInfo::SpecificNode(Route::new(
                redis::cluster_topology::get_slot(unsafe { ptr_to_str(route.slot_key) }.as_bytes()),
                (&route.slot_type).into(),
            )),
        )),
        RouteType::ByAddress => Some(RoutingInfo::SingleNode(SingleNodeRoutingInfo::ByAddress {
            host: unsafe { ptr_to_str(route.hostname) },
            port: route.port as u16,
        })),
    }
}

/// Applies compression to command arguments if compression is enabled.
///
/// For `CustomCommand`, resolves the actual request type from the command name (first arg)
/// Convert [`CmdInfo`] to a [`Cmd`].
///
/// # Safety
/// * `cmd_ptr` must be able to be safely casted to a valid [`CmdInfo`]
/// * `args` and `args_len` in a referred [`CmdInfo`] structure must not be `null`.
/// * `data` in a referred [`CmdInfo`] structure must point to `arg_count` consecutive string pointers.
/// * `args_len` in a referred [`CmdInfo`] structure must point to `arg_count` consecutive string lengths. See the safety documentation of [`convert_double_pointer_to_vec`].
pub(crate) unsafe fn create_cmd(
    ptr: *const CmdInfo,
    compression_manager: Option<&std::sync::Arc<glide_core::compression::CompressionManager>>,
) -> Result<Cmd, String> {
    let info = unsafe { *ptr };
    let arg_vec = unsafe {
        convert_double_pointer_to_vec(
            info.args as *const *const c_void,
            info.arg_count as c_ulong,
            info.args_len as *const c_ulong,
        )
    };

    let Some(mut cmd) = info.request_type.get_command() else {
        return Err("Couldn't fetch command type".into());
    };

    // Check if compression is enabled before converting args
    let should_process_compression = compression_manager
        .as_ref()
        .map(|cm| cm.is_enabled())
        .unwrap_or(false);

    if should_process_compression {
        // Convert arg_vec to owned Vec<Vec<u8>> for compression processing
        let mut owned_args: Vec<Vec<u8>> = arg_vec.iter().map(|&arg| arg.to_vec()).collect();

        // For CustomCommand, we need to determine the actual command type from the first argument
        // and process compression on args[1..] since args[0] is the command name
        let is_custom_command = matches!(info.request_type, RequestType::CustomCommand);
        let effective_command_type = if is_custom_command {
            resolve_custom_command_type(&owned_args)
        } else {
            info.request_type
        };

        // Apply compression to command arguments
        // For CustomCommand, skip the first argument (command name) when processing compression
        let args_to_compress = if is_custom_command && !owned_args.is_empty() {
            &mut owned_args[1..]
        } else {
            &mut owned_args[..]
        };

        if let Err(err) = glide_core::compression::process_command_args_for_compression(
            args_to_compress,
            effective_command_type,
            compression_manager.map(|m| m.as_ref()),
        ) {
            return Err(format!("Compression failed: {}", err));
        }

        // Use the compressed arguments (owned: Vec->Bytes is zero-copy)
        for command_arg in owned_args {
            append_cmd_arg_owned(&mut cmd, command_arg);
        }
    } else {
        // Use the original arguments
        for command_arg in &arg_vec {
            append_cmd_arg(&mut cmd, command_arg);
        }
    }

    Ok(cmd)
}

/// Convert [`BatchInfo`] to a [`Pipeline`].
///
/// # Safety
/// * `ptr` must be able to be safely casted to a valid [`BatchInfo`].
/// * `cmds` in a referred [`BatchInfo`] structure must not be `null`.
/// * `cmds` in a referred [`BatchInfo`] structure must point to `cmd_count` consecutive [`CmdInfo`] pointers.
///   They must be able to be safely casted to a valid to a slice of the corresponding type via [`from_raw_parts`]. See the safety documentation of [`from_raw_parts`].
/// * Every pointer stored in `cmds` must not be `null` and must point to a valid [`CmdInfo`] structure.
/// * All data in referred [`CmdInfo`] structure(s) should be valid. See the safety documentation of [`create_cmd`].
pub(crate) unsafe fn create_pipeline(
    ptr: *const BatchInfo,
    compression_manager: Option<&std::sync::Arc<glide_core::compression::CompressionManager>>,
) -> Result<Pipeline, String> {
    let info = unsafe { *ptr };
    let cmd_pointers = unsafe { from_raw_parts(info.cmds, info.cmd_count) };
    let mut pipeline = Pipeline::with_capacity(info.cmd_count);
    for (i, cmd_ptr) in cmd_pointers.iter().enumerate() {
        match unsafe { create_cmd(*cmd_ptr, compression_manager) } {
            Ok(cmd) => pipeline.add_command(cmd),
            Err(err) => return Err(format!("Coudln't create {i:?}'th command: {err:?}")),
        };
    }
    if info.is_atomic {
        pipeline.atomic();
    }

    Ok(pipeline)
}

/// Convert [`BatchOptionsInfo`] to a tuple of corresponding values.
///
/// # Safety
/// * `ptr` could be `null`, but if it is not `null`, it must be a valid pointer to a [`BatchOptionsInfo`] struct.
/// * `route_info` in dereferenced [`BatchOptionsInfo`] struct must contain a [`RouteInfo`] pointer.
///   See description of [`RouteInfo`] and the safety documentation of [`create_route`].
pub(crate) unsafe fn get_pipeline_options(
    ptr: *const BatchOptionsInfo,
) -> (Option<RoutingInfo>, Option<u32>, PipelineRetryStrategy) {
    if ptr.is_null() {
        return (None, None, PipelineRetryStrategy::new(false, false));
    }
    let info = unsafe { *ptr };
    let timeout = if info.has_timeout {
        Some(info.timeout)
    } else {
        None
    };
    let route = unsafe { create_route(info.route_info, None) };

    (
        route,
        timeout,
        PipelineRetryStrategy::new(info.retry_server_error, info.retry_connection_error),
    )
}

/// Helper function to extract and validate command name from RequestType.
/// Returns the command name or None if validation fails.
/// Falls back to "CustomCommand" for user-defined commands (e.g., EVAL, EVALSHA).
fn extract_command_name(request_type: RequestType, context: &str) -> Option<String> {
    // Validate request type and extract command
    let cmd = match request_type.get_command() {
        Some(cmd) => cmd,
        None => {
            logger_core::log_error(
                "ffi_otel",
                format!("{context}: RequestType has no command available"),
            );
            return None;
        }
    };

    // Extract command name, falling back to "CustomCommand" for user-defined commands
    // (e.g. EVAL) where the Cmd struct has no pre-set command bytes.
    let command_name = match cmd.command() {
        Some(bytes) => match std::str::from_utf8(bytes.as_slice()) {
            Ok(name) => name.to_owned(),
            Err(e) => {
                logger_core::log_error(
                    "ffi_otel",
                    format!("{context}: Command bytes are not valid UTF-8: {e}"),
                );
                return None;
            }
        },
        None => "CustomCommand".to_owned(),
    };

    // Validate command name length (reasonable limit to prevent abuse)
    if command_name.len() > 256 {
        logger_core::log_error(
            "ffi_otel",
            format!(
                "{context}: Command name too long ({} chars), max 256",
                command_name.len()
            ),
        );
        return None;
    }

    Some(command_name)
}

/// Creates an OpenTelemetry span with the given name and returns a pointer to the span as u64.
#[unsafe(no_mangle)]
pub extern "C" fn create_otel_span(request_type: RequestType) -> u64 {
    let command_name = match extract_command_name(request_type, "create_otel_span") {
        Some(name) => name,
        None => return 0,
    };

    // Create span and convert to pointer
    let span = GlideOpenTelemetry::new_span(&command_name);
    let arc = Arc::new(span);
    let ptr = Arc::into_raw(arc);
    let span_ptr = ptr as u64;

    logger_core::log_debug(
        "ffi_otel",
        format!(
            "create_otel_span: Successfully created span '{command_name}' with pointer 0x{span_ptr:x}",
        ),
    );
    span_ptr
}

/// Creates an OpenTelemetry span with a fixed name "batch" and returns a pointer to the span as u64.
///
#[unsafe(no_mangle)]
pub extern "C" fn create_batch_otel_span() -> u64 {
    let command_name = "Batch";

    // Create span and convert to pointer
    let span = GlideOpenTelemetry::new_span(command_name);
    let arc = Arc::new(span);
    let ptr = Arc::into_raw(arc);
    let span_ptr = ptr as u64;

    logger_core::log_debug(
        "ffi_otel",
        format!(
            "create_batch_otel_span: Successfully created batch span with pointer 0x{span_ptr:x}",
        ),
    );
    span_ptr
}

/// Converts a nullable C string pointer to an optional UTF-8 string slice.
///
/// # Safety
/// * If `ptr` is not null, it must point to a valid, null-terminated C string.
/// * The pointed-to memory must remain valid for the returned string slice lifetime.
unsafe fn optional_c_str<'a>(
    ptr: *const c_char,
    field_name: &str,
) -> Result<Option<&'a str>, String> {
    if ptr.is_null() {
        return Ok(None);
    }

    unsafe { CStr::from_ptr(ptr) }
        .to_str()
        .map(Some)
        .map_err(|err| format!("{field_name} is not valid UTF-8: {err}"))
}

/// Converts a required C string pointer to a UTF-8 string slice.
///
/// # Safety
/// * If `ptr` is not null, it must point to a valid, null-terminated C string.
/// * The pointed-to memory must remain valid for the returned string slice lifetime.
unsafe fn required_c_str<'a>(ptr: *const c_char, field_name: &str) -> Result<&'a str, String> {
    if ptr.is_null() {
        return Err(format!("{field_name} pointer is null"));
    }

    unsafe { CStr::from_ptr(ptr) }
        .to_str()
        .map_err(|err| format!("{field_name} is not valid UTF-8: {err}"))
}

/// Validates a span name C string and returns it as a UTF-8 string slice.
///
/// # Safety
/// * If `span_name` is not null, it must point to a valid, null-terminated C string.
/// * The pointed-to memory must remain valid for the returned string slice lifetime.
unsafe fn validate_span_name<'a>(span_name: *const c_char, context: &str) -> Option<&'a str> {
    let name_str = match unsafe { required_c_str(span_name, "span_name") } {
        Ok(value) => value,
        Err(err) => {
            logger_core::log_error("ffi_otel", format!("{context}: {err}"));
            return None;
        }
    };

    // Validate string length (reasonable limit to prevent abuse)
    // Note: Empty names are allowed as per test expectations
    if name_str.len() > 256 {
        logger_core::log_error(
            "ffi_otel",
            format!(
                "{context}: span_name too long ({} bytes), max 256",
                name_str.len()
            ),
        );
        return None;
    }

    // Validate string content (basic sanity check for control characters)
    if name_str
        .chars()
        .any(|c| c.is_control() && c != '\t' && c != '\n' && c != '\r')
    {
        logger_core::log_error(
            "ffi_otel",
            format!("{context}: span_name contains invalid control characters"),
        );
        return None;
    }

    Some(name_str)
}

fn span_to_ffi_pointer(span: GlideSpan) -> u64 {
    let arc = Arc::new(span);
    let ptr = Arc::into_raw(arc);
    ptr as u64
}

unsafe fn create_span_with_remote_context(
    span_name: &str,
    trace_id: *const c_char,
    span_id: *const c_char,
    trace_flags: u8,
    trace_state: *const c_char,
    function_name: &str,
) -> u64 {
    let trace_id = match unsafe { required_c_str(trace_id, "trace_id") } {
        Ok(value) => value,
        Err(err) => {
            logger_core::log_warn(
                "ffi_otel",
                format!("{function_name}: {err}. Creating independent span as fallback."),
            );
            return span_to_ffi_pointer(GlideOpenTelemetry::new_span(span_name));
        }
    };

    let span_id = match unsafe { required_c_str(span_id, "span_id") } {
        Ok(value) => value,
        Err(err) => {
            logger_core::log_warn(
                "ffi_otel",
                format!("{function_name}: {err}. Creating independent span as fallback."),
            );
            return span_to_ffi_pointer(GlideOpenTelemetry::new_span(span_name));
        }
    };

    let trace_state = match unsafe { optional_c_str(trace_state, "trace_state") } {
        Ok(value) => value,
        Err(err) => {
            logger_core::log_warn(
                "ffi_otel",
                format!("{function_name}: {err}. Creating independent span as fallback."),
            );
            return span_to_ffi_pointer(GlideOpenTelemetry::new_span(span_name));
        }
    };

    let span = match GlideSpan::new_with_remote_context(
        span_name,
        trace_id,
        span_id,
        trace_flags,
        trace_state,
    ) {
        Ok(span) => span,
        Err(err) => {
            logger_core::log_warn(
                "ffi_otel",
                format!(
                    "{function_name}: failed to create span with remote context: {err}. Creating independent span as fallback.",
                ),
            );
            GlideOpenTelemetry::new_span(span_name)
        }
    };

    span_to_ffi_pointer(span)
}

/// Creates an OpenTelemetry batch span with a parent span and returns a pointer to the span as u64.
/// This function creates a child span with the name "Batch" under the provided parent span.
/// Returns 0 on failure.
///
/// # Parameters
/// * `parent_span_ptr`: A u64 pointer to the parent span created by create_otel_span, create_named_otel_span, or create_batch_otel_span
///
/// # Returns
/// * A u64 pointer to the created child batch span, or 0 if creation fails
///
/// # Safety
/// * `parent_span_ptr` must be a valid pointer to a [`Arc<GlideSpan>`] span created by [`create_otel_span`], [`create_named_otel_span`], or [`create_batch_otel_span`], or 0.
/// * If `parent_span_ptr` is 0 or invalid, the function will create an independent batch span as fallback.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn create_batch_otel_span_with_parent(parent_span_ptr: u64) -> u64 {
    let command_name = "Batch";

    // Handle parent span pointer validation with graceful fallback
    if parent_span_ptr == 0 {
        logger_core::log_warn(
            "ffi_otel",
            "create_batch_otel_span_with_parent: parent_span_ptr is null (0), creating independent batch span as fallback",
        );
        // Graceful fallback: create independent batch span
        let span = GlideOpenTelemetry::new_span(command_name);
        let arc = Arc::new(span);
        let ptr = Arc::into_raw(arc);
        let span_ptr = ptr as u64;
        logger_core::log_debug(
            "ffi_otel",
            format!(
                "create_batch_otel_span_with_parent: Created independent fallback batch span with pointer 0x{span_ptr:x}",
            ),
        );
        return span_ptr;
    }

    // Convert parent pointer to GlideSpan and use existing add_span method
    let span = match unsafe { GlideOpenTelemetry::span_from_pointer(parent_span_ptr) } {
        Ok(parent_span) => {
            // Use existing add_span method to create child batch span
            match parent_span.add_span(command_name) {
                Ok(child_span) => child_span,
                Err(e) => {
                    logger_core::log_warn(
                        "ffi_otel",
                        format!(
                            "create_batch_otel_span_with_parent: Failed to create child batch span with parent 0x{parent_span_ptr:x}: {e}. Creating independent batch span as fallback.",
                        ),
                    );
                    // Graceful fallback: create independent batch span
                    GlideOpenTelemetry::new_span(command_name)
                }
            }
        }
        Err(e) => {
            logger_core::log_warn(
                "ffi_otel",
                format!(
                    "create_batch_otel_span_with_parent: Invalid parent span pointer 0x{parent_span_ptr:x}: {e}. Creating independent batch span as fallback.",
                ),
            );
            // Graceful fallback: create independent batch span
            GlideOpenTelemetry::new_span(command_name)
        }
    };

    // Convert span to pointer and return
    let arc = Arc::new(span);
    let ptr = Arc::into_raw(arc);
    let span_ptr = ptr as u64;

    logger_core::log_debug(
        "ffi_otel",
        format!(
            "create_batch_otel_span_with_parent: Successfully created batch span with parent 0x{parent_span_ptr:x}, child pointer 0x{span_ptr:x}",
        ),
    );
    span_ptr
}

/// Creates an OpenTelemetry span with a custom name and returns a pointer to the span as u64.
/// This function is intended for creating parent spans that can be used with create_otel_span_with_parent.
/// Returns 0 on failure.
///
/// # Parameters
/// * `span_name`: A null-terminated C string containing the name for the span
///
/// # Returns
/// * A u64 pointer to the created span, or 0 if creation fails
///
/// # Safety
/// * `span_name` must be a valid pointer to a null-terminated C string
/// * The string must be valid UTF-8
/// * The caller is responsible for eventually calling drop_otel_span with the returned pointer
#[unsafe(no_mangle)]
pub unsafe extern "C" fn create_named_otel_span(span_name: *const c_char) -> u64 {
    let name_str = match unsafe { validate_span_name(span_name, "create_named_otel_span") } {
        Some(name) => name,
        None => return 0,
    };

    // Create the named span using existing new_span method
    let span = GlideOpenTelemetry::new_span(name_str);
    let arc = Arc::new(span);
    let ptr = Arc::into_raw(arc);
    let span_ptr = ptr as u64;

    logger_core::log_debug(
        "ffi_otel",
        format!(
            "create_named_otel_span: Successfully created named span '{name_str}' with pointer 0x{span_ptr:x}",
        ),
    );
    span_ptr
}

/// Creates an OpenTelemetry span with the given request type as a child of the provided parent span.
/// Returns a pointer to the child span as u64, or 0 on failure.
///
/// # Parameters
/// * `request_type`: The type of request to create a span for
/// * `parent_span_ptr`: A pointer to the parent span (created by create_otel_span or create_named_otel_span)
///
/// # Returns
/// * A u64 pointer to the created child span, or 0 if creation fails
///
/// # Safety
/// * `parent_span_ptr` must be a valid pointer to a [`Arc<GlideSpan>`] span created by [`create_otel_span`], [`create_named_otel_span`], or [`create_batch_otel_span`], or 0.
/// * If `parent_span_ptr` is 0 or invalid, the function will create an independent span as fallback.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn create_otel_span_with_parent(
    request_type: RequestType,
    parent_span_ptr: u64,
) -> u64 {
    let command_name = match extract_command_name(request_type, "create_otel_span_with_parent") {
        Some(name) => name,
        None => return 0,
    };

    // Handle parent span pointer validation with graceful fallback
    if parent_span_ptr == 0 {
        logger_core::log_warn(
            "ffi_otel",
            "create_otel_span_with_parent: parent_span_ptr is null (0), creating independent span as fallback",
        );
        // Graceful fallback: create independent span
        let span = GlideOpenTelemetry::new_span(&command_name);
        let arc = Arc::new(span);
        let ptr = Arc::into_raw(arc);
        let span_ptr = ptr as u64;
        logger_core::log_debug(
            "ffi_otel",
            format!(
                "create_otel_span_with_parent: Created independent fallback span '{command_name}' with pointer 0x{span_ptr:x}",
            ),
        );
        return span_ptr;
    }

    // Convert parent pointer to GlideSpan and use existing add_span method
    let span = match unsafe { GlideOpenTelemetry::span_from_pointer(parent_span_ptr) } {
        Ok(parent_span) => {
            // Use existing add_span method to create child span
            match parent_span.add_span(&command_name) {
                Ok(child_span) => child_span,
                Err(e) => {
                    logger_core::log_warn(
                        "ffi_otel",
                        format!(
                            "create_otel_span_with_parent: Failed to create child span '{command_name}' with parent 0x{parent_span_ptr:x}: {e}. Creating independent span as fallback.",
                        ),
                    );
                    // Graceful fallback: create independent span
                    GlideOpenTelemetry::new_span(&command_name)
                }
            }
        }
        Err(e) => {
            logger_core::log_warn(
                "ffi_otel",
                format!(
                    "create_otel_span_with_parent: Invalid parent span pointer 0x{parent_span_ptr:x}: {e}. Creating independent span as fallback.",
                ),
            );
            // Graceful fallback: create independent span
            GlideOpenTelemetry::new_span(&command_name)
        }
    };

    // Convert span to pointer and return
    let arc = Arc::new(span);
    let ptr = Arc::into_raw(arc);
    let span_ptr = ptr as u64;

    logger_core::log_debug(
        "ffi_otel",
        format!(
            "create_otel_span_with_parent: Successfully created span '{command_name}' with parent 0x{parent_span_ptr:x}, child pointer 0x{span_ptr:x}",
        ),
    );
    span_ptr
}

/// Creates an OpenTelemetry span with the given request type as a child of a remote span context.
/// Invalid remote context falls back to creating an independent span.
///
/// # Safety
/// * `trace_id`, `span_id`, and `trace_state` may be null.
/// * Any non-null string pointer must point to a valid, null-terminated UTF-8 C string.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn create_otel_span_with_trace_context(
    request_type: RequestType,
    trace_id: *const c_char,
    span_id: *const c_char,
    trace_flags: u8,
    trace_state: *const c_char,
) -> u64 {
    let command_name =
        match extract_command_name(request_type, "create_otel_span_with_trace_context") {
            Some(name) => name,
            None => return 0,
        };

    unsafe {
        create_span_with_remote_context(
            &command_name,
            trace_id,
            span_id,
            trace_flags,
            trace_state,
            "create_otel_span_with_trace_context",
        )
    }
}

/// Creates an OpenTelemetry span with a custom name as a child of a remote span context.
/// Invalid remote context falls back to creating an independent span.
/// Returns 0 if `span_name` is rejected.
///
/// This is the remote context counterpart of [`create_named_otel_span`], for callers that
/// name spans themselves rather than deriving the name from a [`RequestType`].
///
/// # Safety
/// * `span_name`, `trace_id`, `span_id`, and `trace_state` may be null.
/// * Any non-null string pointer must point to a valid, null-terminated UTF-8 C string.
/// * The caller is responsible for eventually calling [`drop_otel_span`] with the returned pointer.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn create_named_otel_span_with_trace_context(
    span_name: *const c_char,
    trace_id: *const c_char,
    span_id: *const c_char,
    trace_flags: u8,
    trace_state: *const c_char,
) -> u64 {
    let name_str =
        match unsafe { validate_span_name(span_name, "create_named_otel_span_with_trace_context") }
        {
            Some(name) => name,
            None => return 0,
        };

    unsafe {
        create_span_with_remote_context(
            name_str,
            trace_id,
            span_id,
            trace_flags,
            trace_state,
            "create_named_otel_span_with_trace_context",
        )
    }
}

/// Creates an OpenTelemetry batch span as a child of a remote span context.
/// Invalid remote context falls back to creating an independent batch span.
///
/// # Safety
/// * `trace_id`, `span_id`, and `trace_state` may be null.
/// * Any non-null string pointer must point to a valid, null-terminated UTF-8 C string.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn create_batch_otel_span_with_trace_context(
    trace_id: *const c_char,
    span_id: *const c_char,
    trace_flags: u8,
    trace_state: *const c_char,
) -> u64 {
    let command_name = "Batch";

    unsafe {
        create_span_with_remote_context(
            command_name,
            trace_id,
            span_id,
            trace_flags,
            trace_state,
            "create_batch_otel_span_with_trace_context",
        )
    }
}

/// Drops an OpenTelemetry span given its pointer as u64.
///
/// # Safety
/// * `span_ptr` must be a valid pointer to a [`Arc<GlideSpan>`] span created by [`create_otel_span`] or `0`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn drop_otel_span(span_ptr: u64) {
    // Validate span pointer
    if span_ptr == 0 {
        logger_core::log_debug("ffi_otel", "drop_otel_span: Ignoring null span pointer (0)");
        return;
    }

    // Validate pointer alignment and bounds (basic safety checks)
    if !span_ptr.is_multiple_of(8) {
        logger_core::log_error(
            "ffi_otel",
            format!("drop_otel_span: Invalid span pointer - misaligned: 0x{span_ptr:x}",),
        );
        return;
    }

    // Check for obviously invalid pointer values
    const MIN_VALID_ADDRESS: u64 = 0x1000; // 4KB, below this is likely invalid
    const MAX_VALID_ADDRESS: u64 = 0x7FFF_FFFF_FFFF_FFF8; // Max user space on most 64-bit systems

    if span_ptr < MIN_VALID_ADDRESS {
        logger_core::log_error(
            "ffi_otel",
            format!("drop_otel_span: Invalid span pointer - address too low: 0x{span_ptr:x}",),
        );
        return;
    }

    if span_ptr > MAX_VALID_ADDRESS {
        logger_core::log_error(
            "ffi_otel",
            format!("drop_otel_span: Invalid span pointer - address too high: 0x{span_ptr:x}",),
        );
        return;
    }

    // Attempt to safely drop the span
    unsafe {
        // Use std::panic::catch_unwind to handle potential panics during Arc::from_raw
        let result = std::panic::catch_unwind(|| {
            Arc::from_raw(span_ptr as *const GlideSpan);
        });

        match result {
            Ok(_) => {
                logger_core::log_debug(
                    "ffi_otel",
                    format!(
                        "drop_otel_span: Successfully dropped span with pointer 0x{span_ptr:x}",
                    ),
                );
            }
            Err(_) => {
                logger_core::log_error(
                    "ffi_otel",
                    format!(
                        "drop_otel_span: Panic occurred while dropping span pointer 0x{span_ptr:x} - likely invalid pointer",
                    ),
                );
            }
        }
    }
}

/// Configuration for OpenTelemetry integration in the Node.js client.
///
/// This struct allows you to configure how telemetry data (traces and metrics) is exported to an OpenTelemetry collector.
/// - `traces`: Optional configuration for exporting trace data. If `None`, trace data will not be exported.
/// - `metrics`: Optional configuration for exporting metrics data. If `None`, metrics data will not be exported.
/// - `flush_interval_ms`: Optional interval in milliseconds between consecutive exports of telemetry data. If `None`, a default value will be used.
///
/// At least one of traces or metrics must be provided.
#[repr(C)]
#[derive(Clone, Debug)]
pub struct OpenTelemetryConfig {
    /// Configuration for exporting trace data. Only valid if has_traces is true.
    pub traces: *const OpenTelemetryTracesConfig,
    /// Configuration for exporting metrics data. Only valid if has_metrics is true.
    pub metrics: *const OpenTelemetryMetricsConfig,
    /// Whether flush interval is specified
    pub has_flush_interval_ms: bool,
    /// Interval in milliseconds between consecutive exports of telemetry data. Only valid if has_flush_interval_ms is true.
    pub flush_interval_ms: i64,
}

/// Configuration for exporting OpenTelemetry traces.
///
/// - `endpoint`: The endpoint to which trace data will be exported. Expected format:
///   - For gRPC: `grpc://host:port`
///   - For HTTP: `http://host:port` or `https://host:port`
///   - For file exporter: `file:///absolute/path/to/folder/file.json`
/// - `has_sample_percentage`: Whether sample percentage is specified
/// - `sample_percentage`: The percentage of requests to sample and create a span for, used to measure command duration. Only valid if has_sample_percentage is true.
#[repr(C)]
#[derive(Clone, Debug)]
pub struct OpenTelemetryTracesConfig {
    /// The endpoint to which trace data will be exported, `null` if not specified.
    pub endpoint: *const c_char,
    /// Whether sample percentage is specified
    pub has_sample_percentage: bool,
    /// The percentage of requests to sample and create a span for, used to measure command duration. Only valid if has_sample_percentage is true.
    pub sample_percentage: u32,
}

/// Configuration for exporting OpenTelemetry metrics.
///
/// - `endpoint`: The endpoint to which metrics data will be exported. Expected format:
///   - For gRPC: `grpc://host:port`
///   - For HTTP: `http://host:port` or `https://host:port`
///   - For file exporter: `file:///absolute/path/to/folder/file.json`
#[repr(C)]
#[derive(Clone, Debug)]
pub struct OpenTelemetryMetricsConfig {
    /// The endpoint to which metrics data will be exported, `null` if not specified.
    pub endpoint: *const c_char,
}

/// Initializes OpenTelemetry with the given configuration.
///
/// # Safety
/// * `open_telemetry_config` and its underlying traces and metrics pointers must be valid until the function returns.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn init_open_telemetry(
    open_telemetry_config: *const OpenTelemetryConfig,
) -> *const c_char {
    // At least one of traces or metrics must be provided
    if unsafe { (*open_telemetry_config).traces.is_null() }
        && unsafe { (*open_telemetry_config).metrics.is_null() }
    {
        let error_msg =
            "At least one of traces or metrics must be provided for OpenTelemetry configuration";
        return CString::new(error_msg)
            .unwrap_or_else(|_| CString::new("Couldn't convert error message to C string").unwrap())
            .into_raw();
    }

    let mut config = GlideOpenTelemetryConfigBuilder::default();

    // Initialize open telemetry traces exporter
    if !unsafe { (*open_telemetry_config).traces.is_null() } {
        let endpoint = unsafe { CStr::from_ptr((*(*open_telemetry_config).traces).endpoint) }
            .to_string_lossy()
            .to_string();
        match GlideOpenTelemetrySignalsExporter::from_str(&endpoint) {
            Ok(exporter) => {
                let sample_percentage =
                    if unsafe { (*(*open_telemetry_config).traces).has_sample_percentage } {
                        Some(unsafe { (*(*open_telemetry_config).traces).sample_percentage })
                    } else {
                        None
                    };
                config = config.with_trace_exporter(exporter, sample_percentage);
            }
            Err(e) => {
                let error_msg = format!("Invalid traces exporter configuration: {e}");
                return CString::new(error_msg)
                    .unwrap_or_else(|_| {
                        CString::new("Couldn't convert error message to C string").unwrap()
                    })
                    .into_raw();
            }
        }
    }

    // Initialize open telemetry metrics exporter
    if !unsafe { (*open_telemetry_config).metrics.is_null() } {
        let endpoint = unsafe { CStr::from_ptr((*(*open_telemetry_config).metrics).endpoint) }
            .to_string_lossy()
            .to_string();
        match GlideOpenTelemetrySignalsExporter::from_str(&endpoint) {
            Ok(exporter) => {
                config = config.with_metrics_exporter(exporter);
            }
            Err(e) => {
                let error_msg = format!("Invalid metrics exporter configuration: {e}");
                return CString::new(error_msg)
                    .unwrap_or_else(|_| {
                        CString::new("Couldn't convert error message to C string").unwrap()
                    })
                    .into_raw();
            }
        }
    }

    let flush_interval_ms = if unsafe { (*open_telemetry_config).has_flush_interval_ms } {
        unsafe { (*open_telemetry_config).flush_interval_ms }
    } else {
        DEFAULT_FLUSH_SIGNAL_INTERVAL_MS as i64
    };

    if flush_interval_ms <= 0 {
        let error_msg = format!(
            "InvalidInput: flushIntervalMs must be a positive integer (got: {flush_interval_ms})"
        );
        return CString::new(error_msg)
            .unwrap_or_else(|_| CString::new("Couldn't convert error message to C string").unwrap())
            .into_raw();
    }

    config = config.with_flush_interval(std::time::Duration::from_millis(flush_interval_ms as u64));

    // Initialize OpenTelemetry synchronously
    match glide_core::client::get_or_init_runtime() {
        Ok(glide_runtime) => {
            match glide_runtime
                .runtime
                .block_on(async { GlideOpenTelemetry::initialise(config.build()) })
            {
                Ok(_) => std::ptr::null(), // Success
                Err(e) => {
                    let error_msg = format!("Failed to initialize OpenTelemetry: {e}");
                    CString::new(error_msg)
                        .unwrap_or_else(|_| {
                            CString::new("Couldn't convert error message to C string").unwrap()
                        })
                        .into_raw()
                }
            }
        }
        Err(e) => CString::new(e)
            .unwrap_or_else(|_| CString::new("Couldn't convert error message to C string").unwrap())
            .into_raw(),
    }
}

/// Frees a C string.
///
/// # Safety
/// * `s` must be a valid pointer to a C string or `null`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_c_string(s: *mut c_char) {
    unsafe {
        if s.is_null() {
            return;
        }
        drop(CString::from_raw(s));
    };
}

/// This function converts a raw pointer to a GlideSpan into a safe Rust reference.
/// It handles the unsafe pointer operations internally, incrementing the reference count
/// to ensure the span remains valid while in use.
///
/// # Safety
///
/// This function is marked as unsafe because it dereferences a raw pointer. The caller
/// must ensure that:
/// * The pointer is valid and points to a properly allocated GlideSpan
/// * The pointer is properly aligned
/// * The data pointed to is not modified while the returned reference is in use
/// * The pointer is not used after the referenced data is dropped
///
/// # Arguments
///
/// * `command_span` - An optional raw pointer (as u64) to a GlideSpan
///
/// # Returns
///
/// * `Some(GlideSpan)` - A cloned GlideSpan if the pointer is valid
/// * `None` - If the pointer is None
unsafe fn get_unsafe_span_from_ptr(command_span: Option<u64>) -> Option<GlideSpan> {
    command_span.map(|command_span| unsafe {
        Arc::increment_strong_count(command_span as *const GlideSpan);
        (*Arc::from_raw(command_span as *const GlideSpan)).clone()
    })
}

#[repr(C)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum Level {
    ERROR = 0,
    WARN = 1,
    INFO = 2,
    DEBUG = 3,
    TRACE = 4,
    OFF = 5,
}

impl From<logger_core::Level> for Level {
    fn from(level: logger_core::Level) -> Self {
        match level {
            logger_core::Level::Error => Level::ERROR,
            logger_core::Level::Warn => Level::WARN,
            logger_core::Level::Info => Level::INFO,
            logger_core::Level::Debug => Level::DEBUG,
            logger_core::Level::Trace => Level::TRACE,
            logger_core::Level::Off => Level::OFF,
        }
    }
}

impl From<Level> for logger_core::Level {
    fn from(level: Level) -> Self {
        match level {
            Level::ERROR => logger_core::Level::Error,
            Level::WARN => logger_core::Level::Warn,
            Level::INFO => logger_core::Level::Info,
            Level::DEBUG => logger_core::Level::Debug,
            Level::TRACE => logger_core::Level::Trace,
            Level::OFF => logger_core::Level::Off,
        }
    }
}

/// Logs a message using the logger backend.
///
/// # Parameters
///
/// * `level` - The severity level of the current message (e.g., Error, Warn, Info).
/// * `identifier` - A pointer to a null-terminated C string identifying the source of the log message.
/// * `message` - A pointer to a null-terminated C string containing the actual log message.
///
/// # Safety
///
///  The returned pointer must be freed using [`free_log_result`].
///
/// * `identifier` must be a valid, non-null pointer to a null-terminated UTF-8 encoded C string.
/// * `message` must be a valid, non-null pointer to a null-terminated UTF-8 encoded C string.
///
/// # Note
///
/// The caller (Python Sync wrapper, Go wrapper, etc.) is responsible for filtering log messages according to the logger's current log level.
/// This function will log any message it receives.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn glide_log(
    level: Level,
    identifier: *const c_char,
    message: *const c_char,
) -> *mut LogResult {
    let id_str = match unsafe { CStr::from_ptr(identifier).to_str() } {
        Ok(s) => s,
        Err(err) => {
            let c_err = CString::new(format!("Log identifier contains invalid UTF-8: {err}"))
                .unwrap_or_default()
                .into_raw();
            return Box::into_raw(Box::new(LogResult {
                log_error: c_err,
                level: Level::OFF, // Default value, should be ignored when there's an error
            }));
        }
    };

    let msg_str = match unsafe { CStr::from_ptr(message).to_str() } {
        Ok(s) => s,
        Err(err) => {
            let c_err = CString::new(format!("Log message contains invalid UTF-8: {err}"))
                .unwrap_or_default()
                .into_raw();
            return Box::into_raw(Box::new(LogResult {
                log_error: c_err,
                level: Level::OFF, // Default value, should be ignored when there's an error
            }));
        }
    };

    logger_core::log(level.into(), id_str, msg_str);

    Box::into_raw(Box::new(LogResult {
        log_error: std::ptr::null_mut(),
        level, // Level is not meaningful for log operations, but set a default
    }))
}

/// Initializes the logger with the provided log level and optional log file path.
///
/// Success is indicated by a `LogResult` with a null `log_error` field and the actual
/// log level set in the `level` field. Failure is indicated by a `LogResult` with a non-null
/// `log_error` field containing an error message, and the `level` field should be ignored.
///
/// # Parameters
///
/// * `level` - A pointer to a `Level` enum value that sets the maximum log level. If null, a WARN level will be used.
/// * `file_name` - A pointer to a null-terminated C string representing the desired log file path.
///
/// # Returns
///
/// A pointer to a `LogResult` struct containing either:
/// - Success: `log_error` is null, `level` contains the actual log level that was set
/// - Error: `log_error` contains the error message, `level` should be ignored
///
///
/// # Safety
///
/// The returned pointer must be freed using [`free_log_result`].
///
/// * `level` may be null. If not null, it must point to a valid instance of the `Level` enum.
/// * `file_name` may be null. If not null, it must point to a valid, null-terminated C string.
///   If the string contains invalid UTF-8, an error will be returned instead of panicking.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn init(level: *const Level, file_name: *const c_char) -> *mut LogResult {
    let level_option = if level.is_null() {
        None
    } else {
        Some(unsafe { *level }.into())
    };

    let file_name_option = if file_name.is_null() {
        None
    } else {
        match unsafe { CStr::from_ptr(file_name).to_str() } {
            Ok(file_str) => Some(file_str),
            Err(err) => {
                let c_err = CString::new(format!("File name contains invalid UTF-8: {err}"))
                    .unwrap_or_default()
                    .into_raw();
                return Box::into_raw(Box::new(LogResult {
                    log_error: c_err,
                    level: Level::OFF, // Default value, should be ignored when there's an error
                }));
            }
        }
    };

    let logger_level = logger_core::init(level_option, file_name_option);

    Box::into_raw(Box::new(LogResult {
        log_error: std::ptr::null_mut(),
        level: logger_level.into(),
    }))
}

/// Frees a log result.
///
/// This function deallocates a `LogResult` struct and any error message it contains.
///
/// # Parameters
///
/// * `result_ptr` - A pointer to the `LogResult` to free, or null.
///
/// # Safety
///
/// * `result_ptr` must be a valid pointer to a `LogResult` returned by [`glide_log`] or [`init`], or null.
/// * This function must be called exactly once for each `LogResult`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_log_result(result_ptr: *mut LogResult) {
    if result_ptr.is_null() {
        return;
    }
    unsafe {
        let result = Box::from_raw(result_ptr);
        if !result.log_error.is_null() {
            free_c_string(result.log_error);
        }
    }
}

/// Statistics structure containing telemetry data.
///
/// This struct provides compression and connection statistics for the client.
#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub struct Statistics {
    /// Total number of connections opened to Valkey
    pub total_connections: c_ulong,
    /// Total number of GLIDE clients
    pub total_clients: c_ulong,
    /// Total number of values compressed
    pub total_values_compressed: c_ulong,
    /// Total number of values decompressed
    pub total_values_decompressed: c_ulong,
    /// Total original bytes before compression
    pub total_original_bytes: c_ulong,
    /// Total bytes after compression
    pub total_bytes_compressed: c_ulong,
    /// Total bytes after decompression
    pub total_bytes_decompressed: c_ulong,
    /// Number of times compression was skipped
    pub compression_skipped_count: c_ulong,
    /// Number of times subscriptions were out of sync during reconciliation
    pub subscription_out_of_sync_count: c_ulong,
    /// Timestamp of last successful subscription sync (milliseconds since epoch)
    pub subscription_last_sync_timestamp: c_ulong,
}

/// Get compression and connection statistics.
///
/// Returns a `Statistics` struct containing current telemetry data.
/// This function is thread-safe and can be called at any time.
///
/// # Returns
///
/// A `Statistics` struct with the current statistics values.
#[unsafe(no_mangle)]
pub extern "C" fn get_statistics() -> Statistics {
    Statistics {
        total_connections: Telemetry::total_connections() as c_ulong,
        total_clients: Telemetry::total_clients() as c_ulong,
        total_values_compressed: Telemetry::total_values_compressed() as c_ulong,
        total_values_decompressed: Telemetry::total_values_decompressed() as c_ulong,
        total_original_bytes: Telemetry::total_original_bytes() as c_ulong,
        total_bytes_compressed: Telemetry::total_bytes_compressed() as c_ulong,
        total_bytes_decompressed: Telemetry::total_bytes_decompressed() as c_ulong,
        compression_skipped_count: Telemetry::compression_skipped_count() as c_ulong,
        subscription_out_of_sync_count: Telemetry::subscription_out_of_sync_count() as c_ulong,
        subscription_last_sync_timestamp: Telemetry::subscription_last_sync_timestamp() as c_ulong,
    }
}

/// Returns the minimum size in bytes for compression.
///
/// This constant represents the minimum size a value must be to be eligible for compression.
/// It is calculated as `HEADER_SIZE + 1` to ensure that compressed data is always larger
/// than just the compression header itself.
///
/// This function allows language bindings to validate compression configuration without
/// hardcoding the constant, ensuring consistency with the Rust core implementation.
///
/// # Returns
///
/// The minimum compression size in bytes (currently 6 bytes: 5-byte header + 1 byte data).
#[unsafe(no_mangle)]
pub extern "C" fn get_min_compressed_size() -> c_ulong {
    glide_core::compression::MIN_COMPRESSED_SIZE as c_ulong
}

/// Register a pubsub callback for an existing client.
///
/// # Safety
/// * `client_adapter_ptr` must be a valid client pointer from create_client
/// * `pubsub_callback` must be a valid function pointer that lives while the client is active
#[unsafe(no_mangle)]
pub unsafe extern "C" fn register_pubsub_callback(
    client_adapter_ptr: *const c_void,
    pubsub_callback: PubSubCallback,
) -> *const c_char {
    if client_adapter_ptr.is_null() {
        return CString::new("Client adapter pointer is null")
            .unwrap()
            .into_raw();
    }

    let client_adapter = unsafe {
        Arc::increment_strong_count(client_adapter_ptr);
        Arc::from_raw(client_adapter_ptr as *const ClientAdapter)
    };

    match client_adapter.pubsub_callback.write() {
        Ok(mut guard) => {
            *guard = Some(pubsub_callback);
            std::ptr::null()
        }
        Err(_) => CString::new("Failed to acquire write lock on pubsub callback")
            .unwrap()
            .into_raw(),
    }
}

/// Unregister pubsub callback for a client.
///
/// # Safety
/// * `client_adapter_ptr` must be a valid client pointer from create_client
#[unsafe(no_mangle)]
pub unsafe extern "C" fn unregister_pubsub_callback(
    client_adapter_ptr: *const c_void,
) -> *const c_char {
    if client_adapter_ptr.is_null() {
        return CString::new("Client adapter pointer is null")
            .unwrap()
            .into_raw();
    }

    let client_adapter = unsafe {
        Arc::increment_strong_count(client_adapter_ptr);
        Arc::from_raw(client_adapter_ptr as *const ClientAdapter)
    };

    match client_adapter.pubsub_callback.write() {
        Ok(mut guard) => {
            *guard = None;
            std::ptr::null()
        }
        Err(_) => CString::new("Failed to acquire write lock on pubsub callback")
            .unwrap()
            .into_raw(),
    }
}

// ─── MonitorClient FFI ────────────────────────────────────────────────────────

use glide_core::client::{MonitorClient, MonitorLine, MonitorLineCallback, NodeAddress};

/// Callback invoked for each parsed MONITOR line.
/// `client_ptr` is the opaque pointer returned in `ConnectionResponse.conn_ptr`.
/// String fields are UTF-8, not null-terminated. `args_json` is a JSON array string.
///
/// # Safety
/// The string pointers (`client_addr`, `command`, `args_json`) are only valid for
/// the duration of the callback invocation. They must not be stored or accessed
/// after the callback returns.
pub type MonitorCallback = unsafe extern "C-unwind" fn(
    client_ptr: usize,
    timestamp: f64,
    db: i64,
    client_addr: *const u8,
    client_addr_len: i64,
    command: *const u8,
    command_len: i64,
    args_json: *const u8,
    args_json_len: i64,
);

struct MonitorAdapter {
    client: std::mem::ManuallyDrop<MonitorClient>,
    runtime: Runtime,
}

impl Drop for MonitorAdapter {
    fn drop(&mut self) {
        // SAFETY: we are in drop; client will not be used again.
        let client = unsafe { std::mem::ManuallyDrop::take(&mut self.client) };
        self.runtime.block_on(client.stop_async());
    }
}

/// Create a MonitorClient connected to the first address in `connection_request_bytes`.
///
/// Returns a `ConnectionResponse`. On success, `conn_ptr` is the monitor client handle
/// and `connection_error_message` is null. On failure, `conn_ptr` is null and
/// `connection_error_message` contains the error. The caller must free the returned
/// `ConnectionResponse` by calling `free_connection_response`.
///
/// # Safety
/// - `connection_request_bytes` must point to `connection_request_len` valid bytes
///   containing a serialized `ConnectionRequest` protobuf.
/// - `monitor_callback` must be a valid function pointer that remains valid for the
///   lifetime of the returned client.
/// - The returned `ConnectionResponse` must be freed with `free_connection_response`.
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn create_monitor_client(
    connection_request_bytes: *const u8,
    connection_request_len: usize,
    monitor_callback: MonitorCallback,
) -> *const ConnectionResponse {
    let request_bytes =
        unsafe { std::slice::from_raw_parts(connection_request_bytes, connection_request_len) };
    let connection_request =
        match glide_core::connection_request::ConnectionRequest::parse_from_bytes(request_bytes) {
            Ok(r) => r,
            Err(e) => {
                let err_msg = CString::new(format!("Failed to parse connection request: {e}"))
                    .unwrap_or_default();
                return Box::into_raw(Box::new(ConnectionResponse {
                    conn_ptr: std::ptr::null(),
                    connection_error_message: err_msg.into_raw(),
                }));
            }
        };
    // Extract address, tls, and auth from the protobuf ConnectionRequest BEFORE .into(),
    // so this compiles against the mock-glide-core stub (which lacks these fields).
    let Some(proto_addr) = connection_request.addresses.first() else {
        let err_msg = CString::new("No addresses provided").unwrap_or_default();
        return Box::into_raw(Box::new(ConnectionResponse {
            conn_ptr: std::ptr::null(),
            connection_error_message: err_msg.into_raw(),
        }));
    };
    let address = NodeAddress {
        host: proto_addr.host.to_string(),
        port: proto_addr.port as u16,
    };
    let tls_mode = match connection_request.tls_mode.enum_value_or_default() {
        glide_core::connection_request::TlsMode::NoTls => glide_core::client::TlsMode::NoTls,
        glide_core::connection_request::TlsMode::SecureTls => {
            glide_core::client::TlsMode::SecureTls
        }
        glide_core::connection_request::TlsMode::InsecureTls => {
            glide_core::client::TlsMode::InsecureTls
        }
    };
    let redis_conn_info = redis::RedisConnectionInfo {
        db: connection_request.database_id as i64,
        username: connection_request
            .authentication_info
            .as_ref()
            .and_then(|a| {
                if a.username.is_empty() {
                    None
                } else {
                    Some(a.username.to_string())
                }
            }),
        password: connection_request
            .authentication_info
            .as_ref()
            .and_then(|a| {
                if a.password.is_empty() {
                    None
                } else {
                    Some(a.password.to_string())
                }
            }),
        protocol: redis::ProtocolVersion::RESP2,
        client_name: if connection_request.client_name.is_empty() {
            None
        } else {
            Some(connection_request.client_name.to_string())
        },
        lib_name: if connection_request.lib_name.is_empty() {
            None
        } else {
            Some(connection_request.lib_name.to_string())
        },
        lib_ver: None,
        server_assisted_cache: false,
        cache: None,
    };

    let runtime = match Builder::new_multi_thread().enable_all().build() {
        Ok(r) => r,
        Err(e) => {
            let err_msg =
                CString::new(format!("Failed to create runtime: {e}")).unwrap_or_default();
            return Box::into_raw(Box::new(ConnectionResponse {
                conn_ptr: std::ptr::null(),
                connection_error_message: err_msg.into_raw(),
            }));
        }
    };

    // ptr_cell is written once (Release) after Box::into_raw, before any real
    // monitor lines can arrive. The closure reads it with Acquire. Safe because
    // close_monitor_client blocks (via stop_async) until the task exits before
    // freeing the adapter.
    let ptr_cell = std::sync::Arc::new(std::sync::atomic::AtomicUsize::new(0));
    let ptr_cell_clone = ptr_cell.clone();

    let on_line: MonitorLineCallback = std::sync::Arc::new(move |line: MonitorLine| {
        let ptr = ptr_cell_clone.load(std::sync::atomic::Ordering::Acquire);
        if ptr == 0 {
            return;
        }
        let client_addr_bytes = line.client_addr.as_bytes();
        let command_bytes = line.command.as_bytes();
        let args_json = serde_json::to_string(&line.args).unwrap_or_else(|_| "[]".to_string());
        let args_bytes = args_json.as_bytes();
        unsafe {
            monitor_callback(
                ptr,
                line.timestamp,
                line.db,
                client_addr_bytes.as_ptr(),
                client_addr_bytes.len() as i64,
                command_bytes.as_ptr(),
                command_bytes.len() as i64,
                args_bytes.as_ptr(),
                args_bytes.len() as i64,
            );
        }
    });

    let monitor_client = match runtime
        .block_on(async { MonitorClient::new(&address, redis_conn_info, tls_mode, on_line).await })
    {
        Ok(c) => c,
        Err(e) => {
            let err_msg =
                CString::new(format!("Failed to create monitor client: {e}")).unwrap_or_default();
            return Box::into_raw(Box::new(ConnectionResponse {
                conn_ptr: std::ptr::null(),
                connection_error_message: err_msg.into_raw(),
            }));
        }
    };

    let adapter = Box::new(MonitorAdapter {
        client: std::mem::ManuallyDrop::new(monitor_client),
        runtime,
    });
    let conn_ptr = Box::into_raw(adapter) as *const c_void;
    // Store ptr after MonitorClient::new returns. There is a brief window between
    // new() returning and this store where the background task could invoke the
    // callback with ptr == 0, causing those lines to be silently dropped. In
    // practice this is benign: new() returns only after MONITOR +OK, and the
    // server sends lines only for commands issued after that point. However,
    // callers should not rely on receiving lines issued concurrently with
    // create_monitor_client returning.
    ptr_cell.store(conn_ptr as usize, std::sync::atomic::Ordering::Release);

    Box::into_raw(Box::new(ConnectionResponse {
        conn_ptr,
        connection_error_message: std::ptr::null(),
    }))
}

/// Stop and free a MonitorClient created by `create_monitor_client`.
///
/// # Safety
/// - `client_ptr` must be a `conn_ptr` returned by `create_monitor_client`.
/// - Must not be called more than once for the same pointer. Calling it twice
///   is undefined behaviour (double-free). The caller is responsible for
///   nulling or discarding the pointer after this call.
/// - Must not be called concurrently with any active monitor callback.
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn close_monitor_client(client_ptr: *const c_void) {
    if !client_ptr.is_null() {
        // Drop calls runtime.block_on(client.stop_async()), ensuring the task
        // has fully exited before the adapter memory is freed.
        let _ = unsafe { Box::from_raw(client_ptr as *mut MonitorAdapter) };
    }
}
/// Execute a command with routing using a [`RouteInfo`] C-struct pointer
/// instead of protobuf-encoded route bytes.
///
/// Behaves identically to [`command_with_buffer`] otherwise, including the
/// buffer-response behavior: when `response_buf` is null, the response flows
/// through the normal `execute_request` path; when non-null, the response is
/// written directly into the buffer.
///
/// # Safety
///
/// * `client_adapter_ptr` must not be `null` and must be obtained from the `ConnectionResponse` returned from [`create_client`].
/// * `client_adapter_ptr` must be able to be safely casted to a valid [`Arc<ClientAdapter>`] via [`Arc::from_raw`]. See the safety documentation of [`std::sync::Arc::from_raw`].
/// * `request_id` must be a request ID from the foreign language and must be valid until either `success_callback` or `failure_callback` is finished.
/// * `args` is an optional bytes pointers array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `args_len` is an optional bytes length array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `arg_count` the number of elements in `args` and `args_len`. It must also not be greater than the max value of a signed pointer-sized integer.
/// * `arg_count` must be 0 if `args` and `args_len` are null.
/// * `args` and `args_len` must either be both null or be both not null.
/// * `route_info` could be `null`, which means no route (equivalent to an unset `route_bytes`); if not `null`, it must be a valid pointer to a [`RouteInfo`] struct for the duration of this call.
/// * When non-null, `response_buf` must point to a writable buffer of at least `response_buf_len` bytes.
/// * `response_buf_len` must be 0 if `response_buf` is null.
/// * `span_ptr` is a valid pointer to [`Arc<GlideSpan>`], a span created by [`create_otel_span`] or `0`. The span must be valid until the command is finished.
/// * This function should only be called with a `client_adapter_ptr` created by [`create_client`], before [`close_client`] was called with the pointer.
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn command_with_route_info(
    client_adapter_ptr: *const c_void,
    request_id: usize,
    command_type: RequestType,
    arg_count: c_ulong,
    args: *const usize,
    args_len: *const c_ulong,
    route_info: *const RouteInfo,
    response_buf: *mut u8,
    response_buf_len: usize,
    span_ptr: u64,
) -> *mut CommandResult {
    let buf_option = if response_buf.is_null() {
        None
    } else {
        Some(ResponseBuffer::Single((response_buf, response_buf_len)))
    };
    unsafe {
        execute_command(
            client_adapter_ptr,
            request_id,
            command_type,
            arg_count,
            args,
            args_len,
            RouteInput::RouteInfo(route_info),
            buf_option,
            span_ptr,
        )
    }
}

#[cfg(test)]
mod tests_push_notification_safety {
    use super::*;
    use std::sync::Mutex;
    use std::sync::atomic::{AtomicUsize, Ordering};

    static TEST_LOCK: Mutex<()> = Mutex::new(());

    static CALLBACK_INVOCATIONS: AtomicUsize = AtomicUsize::new(0);
    static LAST_CALLBACK_DATA: Mutex<Option<CallbackCapture>> = Mutex::new(None);

    struct CallbackCapture {
        message: Vec<u8>,
        channel: Vec<u8>,
        pattern: Option<Vec<u8>>,
    }

    unsafe extern "C-unwind" fn counting_callback(
        _client_ptr: usize,
        _kind: PushKind,
        message: *const u8,
        message_len: i64,
        channel: *const u8,
        channel_len: i64,
        pattern: *const u8,
        pattern_len: i64,
    ) {
        CALLBACK_INVOCATIONS.fetch_add(1, Ordering::SeqCst);
        unsafe {
            let msg = std::slice::from_raw_parts(message, message_len as usize).to_vec();
            let ch = std::slice::from_raw_parts(channel, channel_len as usize).to_vec();
            let pat = if pattern.is_null() {
                None
            } else {
                Some(std::slice::from_raw_parts(pattern, pattern_len as usize).to_vec())
            };
            *LAST_CALLBACK_DATA.lock().unwrap() = Some(CallbackCapture {
                message: msg,
                channel: ch,
                pattern: pat,
            });
        }
    }

    fn reset_callback_count() {
        CALLBACK_INVOCATIONS.store(0, Ordering::SeqCst);
        *LAST_CALLBACK_DATA.lock().unwrap() = None;
    }

    #[test]
    fn test_non_bulkstring_element_does_not_panic() {
        let _guard = TEST_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        reset_callback_count();
        let push_msg = redis::PushInfo {
            kind: redis::PushKind::Message,
            data: vec![
                Value::BulkString(b"channel".to_vec().into()),
                Value::Int(42),
            ],
        };
        unsafe {
            process_push_notification(push_msg, counting_callback, 0);
        }
        // Only one BulkString element after filtering, so the frame is too short
        // and is silently dropped (no callback invocation).
        assert_eq!(CALLBACK_INVOCATIONS.load(Ordering::SeqCst), 0);
    }

    #[test]
    fn test_non_bulkstring_with_enough_valid_elements_delivers() {
        let _guard = TEST_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        reset_callback_count();
        let push_msg = redis::PushInfo {
            kind: redis::PushKind::Message,
            data: vec![
                Value::BulkString(b"channel".to_vec().into()),
                Value::Int(42),
                Value::BulkString(b"message".to_vec().into()),
            ],
        };
        unsafe {
            process_push_notification(push_msg, counting_callback, 0);
        }
        // Two BulkString elements remain after filtering the Int, enough for delivery.
        assert_eq!(CALLBACK_INVOCATIONS.load(Ordering::SeqCst), 1);
        let data = LAST_CALLBACK_DATA.lock().unwrap();
        let capture = data.as_ref().unwrap();
        assert_eq!(capture.channel, b"channel");
        assert_eq!(capture.message, b"message");
        assert!(capture.pattern.is_none());
    }

    #[test]
    fn test_too_few_elements_does_not_panic() {
        let _guard = TEST_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        reset_callback_count();
        let push_msg = redis::PushInfo {
            kind: redis::PushKind::Message,
            data: vec![Value::BulkString(b"only_one".to_vec().into())],
        };
        unsafe {
            process_push_notification(push_msg, counting_callback, 0);
        }
        assert_eq!(CALLBACK_INVOCATIONS.load(Ordering::SeqCst), 0);
    }

    #[test]
    fn test_empty_data_does_not_panic() {
        let _guard = TEST_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        reset_callback_count();
        let push_msg = redis::PushInfo {
            kind: redis::PushKind::SMessage,
            data: vec![],
        };
        unsafe {
            process_push_notification(push_msg, counting_callback, 0);
        }
        assert_eq!(CALLBACK_INVOCATIONS.load(Ordering::SeqCst), 0);
    }

    #[test]
    fn test_all_non_bulkstring_elements_does_not_panic() {
        let _guard = TEST_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        reset_callback_count();
        let push_msg = redis::PushInfo {
            kind: redis::PushKind::Message,
            data: vec![Value::Int(1), Value::Int(2), Value::Int(3)],
        };
        unsafe {
            process_push_notification(push_msg, counting_callback, 0);
        }
        assert_eq!(CALLBACK_INVOCATIONS.load(Ordering::SeqCst), 0);
    }

    #[test]
    fn test_well_formed_two_element_message() {
        let _guard = TEST_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        reset_callback_count();
        let push_msg = redis::PushInfo {
            kind: redis::PushKind::Message,
            data: vec![
                Value::BulkString(b"my-channel".to_vec().into()),
                Value::BulkString(b"hello world".to_vec().into()),
            ],
        };
        unsafe {
            process_push_notification(push_msg, counting_callback, 0);
        }
        assert_eq!(CALLBACK_INVOCATIONS.load(Ordering::SeqCst), 1);
        let data = LAST_CALLBACK_DATA.lock().unwrap();
        let capture = data.as_ref().unwrap();
        assert_eq!(capture.channel, b"my-channel");
        assert_eq!(capture.message, b"hello world");
        assert!(capture.pattern.is_none());
    }

    #[test]
    fn test_well_formed_three_element_pmessage() {
        let _guard = TEST_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        reset_callback_count();
        let push_msg = redis::PushInfo {
            kind: redis::PushKind::PMessage,
            data: vec![
                Value::BulkString(b"my-pattern*".to_vec().into()),
                Value::BulkString(b"my-channel".to_vec().into()),
                Value::BulkString(b"hello world".to_vec().into()),
            ],
        };
        unsafe {
            process_push_notification(push_msg, counting_callback, 0);
        }
        assert_eq!(CALLBACK_INVOCATIONS.load(Ordering::SeqCst), 1);
        let data = LAST_CALLBACK_DATA.lock().unwrap();
        let capture = data.as_ref().unwrap();
        assert_eq!(capture.channel, b"my-channel");
        assert_eq!(capture.message, b"hello world");
        assert_eq!(capture.pattern.as_deref(), Some(b"my-pattern*".as_slice()));
    }

    #[test]
    fn test_extra_elements_no_leak() {
        let _guard = TEST_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        reset_callback_count();
        let push_msg = redis::PushInfo {
            kind: redis::PushKind::PMessage,
            data: vec![
                Value::BulkString(b"pattern".to_vec().into()),
                Value::BulkString(b"channel".to_vec().into()),
                Value::BulkString(b"message".to_vec().into()),
                Value::BulkString(b"extra1".to_vec().into()),
                Value::BulkString(b"extra2".to_vec().into()),
            ],
        };
        unsafe {
            process_push_notification(push_msg, counting_callback, 0);
        }
        // Callback is invoked with the first 3 elements; extra elements are never
        // allocated as pointers (no leak). We verify the callback received the
        // correct data from positions 0, 1, 2.
        assert_eq!(CALLBACK_INVOCATIONS.load(Ordering::SeqCst), 1);
        let data = LAST_CALLBACK_DATA.lock().unwrap();
        let capture = data.as_ref().unwrap();
        assert_eq!(capture.pattern.as_deref(), Some(b"pattern".as_slice()));
        assert_eq!(capture.channel, b"channel");
        assert_eq!(capture.message, b"message");
    }

    #[test]
    fn test_extract_pubsub_data_returns_none_for_malformed_frames() {
        let one_bulk_one_int = redis::PushInfo {
            kind: redis::PushKind::Message,
            data: vec![
                Value::BulkString(b"channel".to_vec().into()),
                Value::Int(42),
            ],
        };
        assert!(extract_pubsub_data(&one_bulk_one_int).is_none());

        let single_bulk = redis::PushInfo {
            kind: redis::PushKind::Message,
            data: vec![Value::BulkString(b"only_one".to_vec().into())],
        };
        assert!(extract_pubsub_data(&single_bulk).is_none());

        let empty = redis::PushInfo {
            kind: redis::PushKind::Message,
            data: vec![],
        };
        assert!(extract_pubsub_data(&empty).is_none());

        let all_ints = redis::PushInfo {
            kind: redis::PushKind::Message,
            data: vec![Value::Int(1), Value::Int(2), Value::Int(3)],
        };
        assert!(extract_pubsub_data(&all_ints).is_none());
    }

    #[test]
    fn test_disconnection_with_empty_data_reaches_callback() {
        let _guard = TEST_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        reset_callback_count();
        let push_msg = redis::PushInfo {
            kind: redis::PushKind::Disconnection,
            data: vec![],
        };
        unsafe {
            process_push_notification(push_msg, counting_callback, 0);
        }
        assert_eq!(CALLBACK_INVOCATIONS.load(Ordering::SeqCst), 1);
        let data = LAST_CALLBACK_DATA.lock().unwrap();
        let capture = data.as_ref().unwrap();
        assert!(capture.message.is_empty());
        assert!(capture.channel.is_empty());
        assert!(capture.pattern.is_none());
    }

    #[test]
    fn test_malformed_message_frame_still_dropped() {
        let _guard = TEST_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        reset_callback_count();
        let push_msg = redis::PushInfo {
            kind: redis::PushKind::Message,
            data: vec![Value::Int(99)],
        };
        unsafe {
            process_push_notification(push_msg, counting_callback, 0);
        }
        assert_eq!(CALLBACK_INVOCATIONS.load(Ordering::SeqCst), 0);
    }
}

// Pool and scope FFI — excluded from MIRI tests (requires real tokio runtime)
#[cfg(feature = "pool-support")]
mod pool_ffi;
#[cfg(feature = "pool-support")]
pub use pool_ffi::*;
