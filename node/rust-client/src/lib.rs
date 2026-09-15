// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

mod pool;

use glide_core::client::{MonitorClient, MonitorLine, MonitorLineCallback, NodeAddress, TlsMode};
use glide_core::cluster_scan_container::get_cluster_scan_cursor;
use glide_core::compression::process_command_args_for_compression;
use glide_core::connection_request;
use glide_core::errors::{error_message, error_type};
use glide_core::otel_db_semantics::{set_db_attributes, set_db_batch_attributes};
use glide_core::{
    DEFAULT_FLUSH_SIGNAL_INTERVAL_MS, GlideOpenTelemetry, GlideOpenTelemetryConfigBuilder,
    GlideOpenTelemetrySignalsExporter, GlideSpan, Telemetry,
};
use logger_core::{log_warn, log_warn_lazy};
use redis::cluster_routing::Routable;
use redis::{
    Arg, ClusterScanArgs, Cmd, ErrorKind, PipelineRetryStrategy, PushInfo, RedisError, ScanStateRC,
};
use telemetrylib::GlideSpanStatus;

#[cfg(not(target_env = "msvc"))]
use tikv_jemallocator::Jemalloc;

#[cfg(not(target_env = "msvc"))]
#[global_allocator]
static GLOBAL: Jemalloc = Jemalloc;
pub const FINISHED_SCAN_CURSOR: &str = "finished";
use byteorder::{LittleEndian, WriteBytesExt};
use bytes::Bytes;
/// Maximum number of request args per command (2^12 = 4096).
/// Defined locally since it was previously imported from the socket listener layer.
const MAX_REQUEST_ARGS_LENGTH: usize = 2_usize.pow(12);
use glide_core::client::ConnectionRequest;
use glide_core::client::{Client, ConnectionError, get_or_init_runtime};
use glide_core::command_request::{
    RequestType as ProtobufRequestType, Routes as ProtobufRoutes, SimpleRoutes, SlotTypes,
    routes::Value as RoutesValue,
};
use glide_core::connection_request::ConnectionRequest as ProtobufConnectionRequest;
use glide_core::request_type::RequestType;
use napi::bindgen_prelude::BigInt;
use napi::bindgen_prelude::Either;
use napi::bindgen_prelude::Uint8Array;
use napi::bindgen_prelude::{
    BufferSlice, FnArgs, Function, JsObjectValue, Null, Object, ToNapiValue,
};
use napi::threadsafe_function::{ThreadsafeFunction, ThreadsafeFunctionCallMode};
use napi::{Env, Error, Result, Status, Unknown};
use napi_derive::napi;
use num_traits::sign::Signed;
use protobuf::Message;
use redis::Value;
use redis::cluster_routing::{
    MultipleNodeRoutingInfo, ResponsePolicy, Route, RoutingInfo, SingleNodeRoutingInfo, SlotAddr,
};
#[cfg(feature = "testing_utilities")]
use std::collections::HashMap;
use std::collections::HashMap as StdHashMap;
use std::ptr::from_mut;
use std::str::FromStr;
use std::sync::atomic::{AtomicIsize, AtomicU64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};
use tokio::sync::mpsc;
use tokio::task;
use tokio_util::task::LocalPoolHandle;

#[napi]
pub enum Level {
    Debug = 3,
    Error = 0,
    Info = 2,
    Trace = 4,
    Warn = 1,
    Off = 5,
}

#[napi]
pub const MAX_REQUEST_ARGS_LEN: u32 = MAX_REQUEST_ARGS_LENGTH as u32;

#[napi]
pub const DEFAULT_REQUEST_TIMEOUT_IN_MILLISECONDS: u32 =
    glide_core::client::DEFAULT_RESPONSE_TIMEOUT.as_millis() as u32;

#[napi]
pub const DEFAULT_CONNECTION_TIMEOUT_IN_MILLISECONDS: u32 =
    glide_core::client::DEFAULT_CONNECTION_TIMEOUT.as_millis() as u32;

#[napi]
pub const DEFAULT_INFLIGHT_REQUESTS_LIMIT: u32 = glide_core::client::DEFAULT_MAX_INFLIGHT_REQUESTS;

// ============================================================================
// Direct NAPI Layer - Command Response Types
// ============================================================================

/// Response object passed to the JavaScript callback for command results.
/// This replaces the protobuf-based response used in the socket IPC layer.
#[napi(object)]
#[derive(Clone)]
pub struct CommandResponse {
    /// The callback index that identifies this request on the JS side
    pub callback_idx: u32,
    /// High 32 bits of the response value pointer (if response is a Value)
    pub resp_pointer_high: Option<u32>,
    /// Low 32 bits of the response value pointer (if response is a Value)
    pub resp_pointer_low: Option<u32>,
    /// Constant response string (e.g., "OK" for simple responses)
    pub constant_response: Option<String>,
    /// Error information if the request failed
    pub request_error: Option<RequestErrorNapi>,
    /// Closing error message if the client is closing
    pub closing_error: Option<String>,
    /// Whether this is a push message (pub/sub)
    pub is_push: bool,
}

/// Error information for failed requests.
#[napi(object)]
#[derive(Clone)]
pub struct RequestErrorNapi {
    /// Human-readable error message
    pub message: String,
    /// Error type code (maps to RequestErrorType enum)
    /// 0 = Unspecified, 1 = ExecAbort, 2 = Timeout, 3 = Disconnect
    pub error_type: u32,
}

// ============================================================================
// Response Buffer - Shared between Rust workers and JS callback
// ============================================================================

use parking_lot::Mutex as PLMutex;
use std::sync::atomic::AtomicBool;

/// Per-client response buffer with batched wake-up.
/// Each client owns its response buffer to avoid contention between clients.
/// Responses are accumulated in the buffer, and JS is notified once per batch.
/// Uses Vec instead of VecDeque for better cache locality (we only push/drain).
struct ResponseBuffer {
    /// Pending responses waiting to be sent to JS
    /// Using parking_lot mutex for fast uncontended locking
    responses: PLMutex<Vec<CommandResponse>>,
    /// Flag indicating if a wake-up is already pending
    wake_pending: AtomicBool,
    /// Flag indicating the client has been closed - prevents callbacks after shutdown
    closed: AtomicBool,
}

impl ResponseBuffer {
    fn new() -> Self {
        Self {
            // Pre-allocate for typical batch sizes
            responses: PLMutex::new(Vec::with_capacity(256)),
            wake_pending: AtomicBool::new(false),
            closed: AtomicBool::new(false),
        }
    }

    /// Mark the buffer as closed - no more wake callbacks will be made
    #[inline]
    fn mark_closed(&self) {
        self.closed.store(true, Ordering::Release);
    }

    /// Check if the buffer is closed
    #[inline]
    fn is_closed(&self) -> bool {
        self.closed.load(Ordering::Acquire)
    }

    /// Push a response to the buffer and return whether a wake-up is needed.
    /// Called from worker tasks and synchronous error paths.
    /// Returns false if the buffer is closed (no wake-up needed).
    #[inline]
    fn push(&self, response: CommandResponse) -> bool {
        // Fast path: don't acquire the lock if already closed
        if self.is_closed() {
            Self::free_response_value(&response);
            return false;
        }
        {
            let mut guard = self.responses.lock();
            // Re-check under lock to close the race with close()+free_leaked_values().
            // Without this, a response pushed after free_leaked_values() drains the
            // buffer but before the post-push closed check would leak its Value pointer.
            if self.is_closed() {
                drop(guard);
                Self::free_response_value(&response);
                return false;
            }
            guard.push(response);
        }
        // Try to set wake_pending from false to true
        // Returns true if we successfully changed it (meaning we should wake)
        self.wake_pending
            .compare_exchange(false, true, Ordering::AcqRel, Ordering::Relaxed)
            .is_ok()
    }

    /// Drain all pending responses (called from JS callback)
    #[inline]
    fn drain(&self) -> Vec<CommandResponse> {
        // Reset wake_pending before draining
        self.wake_pending.store(false, Ordering::Release);
        let mut guard = self.responses.lock();
        // drain().collect() - original Vec keeps its capacity, returns new Vec with elements
        guard.drain(..).collect()
    }

    /// Free a leaked Value pointer from a single CommandResponse, if present.
    /// Called when a response is dropped without being consumed by JS
    /// (e.g., push() finds the buffer already closed).
    fn free_response_value(response: &CommandResponse) {
        if let (Some(high), Some(low)) = (response.resp_pointer_high, response.resp_pointer_low) {
            let mut bytes = [0u8; 8];
            (&mut bytes[..4]).write_u32::<LittleEndian>(low).unwrap();
            (&mut bytes[4..]).write_u32::<LittleEndian>(high).unwrap();
            let pointer = u64::from_le_bytes(bytes);
            if pointer != 0 {
                unsafe { drop(Box::from_raw(pointer as *mut Value)) };
            }
        }
    }

    /// Free all leaked Value pointers in pending responses.
    /// Called during close() to prevent memory leaks when responses are
    /// never consumed by JS (e.g., responses in-flight when client shuts down).
    fn free_leaked_values(&self) {
        let mut guard = self.responses.lock();
        for response in guard.drain(..) {
            Self::free_response_value(&response);
        }
    }
}

// ============================================================================
// Worker Pool - Thread Pinning for Concurrent Execution
// ============================================================================

use parking_lot::Mutex as PLMutex2;

/// Global worker pool state with reference counting for clean shutdown.
/// When the last client is dropped, the pool is dropped, allowing worker threads to exit.
/// This prevents the process from hanging on exit due to non-daemon worker threads.
struct WorkerPoolState {
    /// The actual pool handle, None if no clients exist
    pool: Option<LocalPoolHandle>,
    /// Number of active clients using the pool
    client_count: usize,
}

static WORKER_POOL_STATE: OnceLock<PLMutex2<WorkerPoolState>> = OnceLock::new();

/// Global counter for unique client IDs (used for scope registry).
static NEXT_CLIENT_ID: AtomicU64 = AtomicU64::new(1);

fn get_worker_pool_state() -> &'static PLMutex2<WorkerPoolState> {
    WORKER_POOL_STATE.get_or_init(|| {
        PLMutex2::new(WorkerPoolState {
            pool: None,
            client_count: 0,
        })
    })
}

/// Acquire a reference to the worker pool, incrementing the client count.
/// Creates the pool if it doesn't exist.
/// Returns a clone of the LocalPoolHandle.
fn acquire_worker_pool() -> LocalPoolHandle {
    let mut state = get_worker_pool_state().lock();
    state.client_count += 1;
    if state.pool.is_none() {
        let num_workers = num_cpus::get();
        state.pool = Some(LocalPoolHandle::new(num_workers));
    }
    state.pool.as_ref().unwrap().clone()
}

/// Release a reference to the worker pool, decrementing the client count.
/// When the count reaches zero, the pool is dropped, allowing worker threads to exit.
fn release_worker_pool() {
    let mut state = get_worker_pool_state().lock();
    if state.client_count > 0 {
        state.client_count -= 1;
        if state.client_count == 0 {
            // Drop the pool - this drops all LocalPoolHandle clones held by the state,
            // which closes the channels to worker threads, causing them to exit.
            state.pool = None;
        }
    }
}

/// RAII guard that clears the pool blocking flag when dropped.
/// Ensures the flag is always unset on every exit path from a `spawn_local` task,
/// including early returns, panics, and task cancellation.
struct UnmarkOnDrop(Option<std::sync::Arc<std::sync::atomic::AtomicBool>>);
impl Drop for UnmarkOnDrop {
    fn drop(&mut self) {
        if let Some(arc) = self.0.take() {
            arc.store(false, Ordering::Release);
        }
    }
}

/// Message sent from NAPI thread to pinned worker thread for command execution.
/// Kept minimal to reduce per-command overhead - no Arc cloning per message.
enum WorkerMessage {
    /// Single command execution
    Command(SingleCommandMessage),
    /// Batch command execution (pipeline or transaction)
    Batch(BatchCommandMessage),
    /// Script invocation (EVALSHA with auto-LOAD)
    ScriptInvocation(ScriptInvocationMessage),
    /// Cluster scan
    ClusterScan(ClusterScanMessage),
    /// Update connection password
    UpdateConnectionPassword(UpdateConnectionPasswordMessage),
    /// Refresh IAM token
    RefreshIamToken(RefreshIamTokenMessage),
    /// Get client-side cache metrics
    GetCacheMetrics(GetCacheMetricsMessage),
}

struct SingleCommandMessage {
    callback_idx: u32,
    cmd: redis::Cmd,
    routing: Option<RoutingInfo>,
    /// If this command was marked as blocking for pool abandon detection,
    /// contains the client_id to unmark after completion.
    pool_blocking_ids: Option<u64>,
}

/// Batch command message for pipeline or transaction execution.
struct BatchCommandMessage {
    callback_idx: u32,
    commands: Vec<redis::Cmd>,
    is_atomic: bool, // true = transaction (MULTI/EXEC), false = pipeline
    raise_on_error: bool,
    timeout: Option<u32>,
    retry_server_error: bool,
    retry_connection_error: bool,
    routing: Option<RoutingInfo>,
    command_span: Option<GlideSpan>,
    /// Pool client_id for blocking protection during batch execution.
    pool_ids: Option<u64>,
}

/// Script invocation message (EVALSHA with auto-LOAD fallback)
struct ScriptInvocationMessage {
    callback_idx: u32,
    hash: String,
    keys: Vec<Bytes>,
    args: Vec<Bytes>,
    routing: Option<RoutingInfo>,
    /// Pool client_id for blocking protection during script execution.
    pool_ids: Option<u64>,
}

/// Cluster scan message
struct ClusterScanMessage {
    callback_idx: u32,
    cursor: String,
    match_pattern: Option<Bytes>,
    count: Option<i64>,
    object_type: Option<String>,
    allow_non_covered_slots: bool,
}

/// Update connection password message
struct UpdateConnectionPasswordMessage {
    callback_idx: u32,
    password: Option<String>,
    immediate_auth: bool,
}

/// Refresh IAM token message
struct RefreshIamTokenMessage {
    callback_idx: u32,
}

/// Client-side cache metrics message
struct GetCacheMetricsMessage {
    callback_idx: u32,
    metrics_type: u32,
}

// ============================================================================
// Helper Functions for Response Building
// ============================================================================

/// Build a CommandResponse from a Redis result
fn build_response(
    callback_idx: u32,
    result: redis::RedisResult<Value>,
    command_span: Option<GlideSpan>,
) -> CommandResponse {
    match result {
        Ok(value) => {
            if let Some(span) = &command_span {
                span.set_status(GlideSpanStatus::Ok);
            }

            if matches!(value, Value::Okay) {
                CommandResponse {
                    callback_idx,
                    resp_pointer_high: None,
                    resp_pointer_low: None,
                    constant_response: Some("OK".into()),
                    request_error: None,
                    closing_error: None,
                    is_push: false,
                }
            } else {
                let value_ptr = from_mut(Box::leak(Box::new(value)));
                let [low, high] = split_pointer(value_ptr);
                CommandResponse {
                    callback_idx,
                    resp_pointer_high: Some(high),
                    resp_pointer_low: Some(low),
                    constant_response: None,
                    request_error: None,
                    closing_error: None,
                    is_push: false,
                }
            }
        }
        Err(err) => {
            let err_type = error_type(&err);
            let err_msg = error_message(&err);
            if let Some(span) = &command_span {
                span.set_status(GlideSpanStatus::Error((&err_msg).into()));
            }
            CommandResponse {
                callback_idx,
                resp_pointer_high: None,
                resp_pointer_low: None,
                constant_response: None,
                request_error: Some(RequestErrorNapi {
                    message: err_msg,
                    error_type: err_type as u32,
                }),
                closing_error: None,
                is_push: false,
            }
        }
    }
}

fn push_response_to_js(
    response_buffer: &Arc<ResponseBuffer>,
    wake_callback: &Option<Arc<ThreadsafeFunction<(), (), (), Status, false>>>,
    response: CommandResponse,
) {
    if response_buffer.push(response)
        && let Some(cb) = wake_callback
    {
        cb.call((), ThreadsafeFunctionCallMode::NonBlocking);
    }
}

fn get_cache_metrics(client: &Client, metrics_type: u32) -> redis::RedisResult<Value> {
    match metrics_type {
        0 => client.cache_hit_rate(),
        1 => client.cache_miss_rate(),
        2 => client.cache_entry_count(),
        3 => client.cache_evictions(),
        4 => client.cache_expirations(),
        5 => client.cache_total_lookups(),
        _ => Err(redis::RedisError::from((
            redis::ErrorKind::InvalidClientConfig,
            "Invalid cache metrics type",
        ))),
    }
}

fn compression_error_to_redis(err: glide_core::compression::CompressionError) -> RedisError {
    RedisError::from((ErrorKind::ClientError, "Compression error", err.to_string()))
}

fn process_batch_response_for_decompression(
    response: Value,
    client: &Client,
) -> std::result::Result<Value, glide_core::compression::CompressionError> {
    let compression_manager = client.compression_manager();
    let Some(manager) = compression_manager.as_deref() else {
        return Ok(response);
    };

    glide_core::compression::decompress_batch_response(response, manager)
}

fn process_command_for_compression(
    cmd: &mut Cmd,
    client: &Client,
) -> std::result::Result<(), glide_core::compression::CompressionError> {
    let compression_manager = client.compression_manager();
    let compression_manager_ref = compression_manager.as_deref();

    if compression_manager_ref
        .map(|m| !m.is_enabled())
        .unwrap_or(true)
    {
        return Ok(());
    }

    let all_args: Vec<Vec<u8>> = cmd
        .args_iter()
        .filter_map(|arg| match arg {
            Arg::Simple(bytes) => Some(bytes.to_vec()),
            Arg::Cursor => None,
        })
        .collect();

    if all_args.is_empty() {
        return Ok(());
    }

    let command_name = &all_args[0];
    let command_str = String::from_utf8_lossy(command_name).to_uppercase();
    let request_type = match command_str.as_str() {
        "SET" => RequestType::Set,
        "MSET" => RequestType::MSet,
        "MSETNX" => RequestType::MSetNX,
        "SETEX" => RequestType::SetEx,
        "PSETEX" => RequestType::PSetEx,
        "SETNX" => RequestType::SetNX,
        "APPEND" => RequestType::Append,
        "GETRANGE" => RequestType::GetRange,
        "SETRANGE" => RequestType::SetRange,
        "STRLEN" => RequestType::Strlen,
        "LCS" => RequestType::LCS,
        "SUBSTR" => RequestType::Substr,
        "INCR" => RequestType::Incr,
        "INCRBY" => RequestType::IncrBy,
        "INCRBYFLOAT" => RequestType::IncrByFloat,
        "DECR" => RequestType::Decr,
        "DECRBY" => RequestType::DecrBy,
        "GETBIT" => RequestType::GetBit,
        "SETBIT" => RequestType::SetBit,
        "BITCOUNT" => RequestType::BitCount,
        "BITPOS" => RequestType::BitPos,
        "BITFIELD" => RequestType::BitField,
        "BITFIELD_RO" => RequestType::BitFieldReadOnly,
        "BITOP" => RequestType::BitOp,
        _ => return Ok(()),
    };

    glide_core::compression::validate_command_compression_compatibility(
        request_type,
        compression_manager_ref,
    )?;

    let mut args: Vec<Vec<u8>> = all_args[1..].to_vec();
    process_command_args_for_compression(&mut args, request_type, compression_manager_ref)?;

    let command_span = cmd.span();
    *cmd = Cmd::new();
    cmd.arg(command_name);
    for arg in args {
        cmd.arg(arg);
    }
    cmd.set_span(command_span);

    Ok(())
}

fn prepare_command_for_execution(
    cmd: &mut Cmd,
    client: &Client,
    context: &str,
) -> redis::RedisResult<()> {
    if client.is_compression_enabled()
        && let Err(err) = process_command_for_compression(cmd, client)
    {
        if err.is_incompatible_command() {
            return Err(compression_error_to_redis(err));
        }

        log_warn(
            context,
            format!("Compression processing failed: {err}, continuing with original command"),
        );
    }

    Ok(())
}

fn span_from_bigint(span_ptr: Option<BigInt>) -> Option<GlideSpan> {
    let span_ptr = span_ptr?;
    let (is_negative, span_ptr, lossless) = span_ptr.get_u64();
    if is_negative || !lossless || span_ptr == 0 {
        log_warn(
            "OpenTelemetry",
            "Invalid span pointer passed to direct NAPI command",
        );
        return None;
    }

    unsafe { GlideOpenTelemetry::span_from_pointer(span_ptr).ok() }
}

fn mark_span_error(command_span: &Option<GlideSpan>, message: &str) {
    if let Some(span) = command_span {
        span.set_status(GlideSpanStatus::Error(message.into()));
    }
}

fn invalid_route_error(message: impl Into<String>) -> RedisError {
    RedisError::from((ErrorKind::ClientError, "Invalid route", message.into()))
}

/// Convert SlotTypes enum to SlotAddr for routing
fn get_slot_addr(slot_type: &protobuf::EnumOrUnknown<SlotTypes>) -> redis::RedisResult<SlotAddr> {
    match slot_type.enum_value() {
        Ok(SlotTypes::Primary) => Ok(SlotAddr::Master),
        Ok(SlotTypes::Replica) => Ok(SlotAddr::ReplicaRequired),
        Err(_) => Err(invalid_route_error("Unknown slot route type")),
    }
}

/// Parse protobuf route bytes into RoutingInfo for cluster routing.
/// This mirrors the get_route function in socket_listener.rs.
fn parse_route_bytes(
    route_bytes: &[u8],
    cmd: Option<&redis::Cmd>,
) -> redis::RedisResult<Option<RoutingInfo>> {
    let routes: ProtobufRoutes = ProtobufRoutes::parse_from_bytes(route_bytes)
        .map_err(|err| invalid_route_error(format!("Failed to parse route bytes: {err}")))?;

    let route_value = routes
        .value
        .ok_or_else(|| invalid_route_error("Missing route value"))?;

    // Helper to get response policy for multi-node commands
    let get_response_policy = |cmd: Option<&redis::Cmd>| {
        cmd.and_then(|cmd| {
            cmd.command()
                .and_then(|cmd_bytes| ResponsePolicy::for_command(&cmd_bytes))
        })
    };

    match route_value {
        RoutesValue::SimpleRoutes(simple_route) => match simple_route.enum_value() {
            Ok(SimpleRoutes::AllNodes) => Ok(Some(RoutingInfo::MultiNode((
                MultipleNodeRoutingInfo::AllNodes,
                get_response_policy(cmd),
            )))),
            Ok(SimpleRoutes::AllPrimaries) => Ok(Some(RoutingInfo::MultiNode((
                MultipleNodeRoutingInfo::AllMasters,
                get_response_policy(cmd),
            )))),
            Ok(SimpleRoutes::Random) => {
                Ok(Some(RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random)))
            }
            Err(_) => Err(invalid_route_error("Unknown simple route type")),
        },
        RoutesValue::SlotKeyRoute(slot_key_route) => {
            let slot_addr = get_slot_addr(&slot_key_route.slot_type)?;
            Ok(Some(RoutingInfo::SingleNode(
                SingleNodeRoutingInfo::SpecificNode(Route::new(
                    redis::cluster_topology::get_slot(slot_key_route.slot_key.as_bytes()),
                    slot_addr,
                )),
            )))
        }
        RoutesValue::SlotIdRoute(slot_id_route) => {
            let slot_addr = get_slot_addr(&slot_id_route.slot_type)?;
            Ok(Some(RoutingInfo::SingleNode(
                SingleNodeRoutingInfo::SpecificNode(Route::new(
                    slot_id_route.slot_id as u16,
                    slot_addr,
                )),
            )))
        }
        RoutesValue::ByAddressRoute(by_address_route) => {
            let port = u16::try_from(by_address_route.port)
                .map_err(|_| invalid_route_error("Route port is out of range"))?;
            Ok(Some(RoutingInfo::SingleNode(
                SingleNodeRoutingInfo::ByAddress {
                    host: by_address_route.host.to_string(),
                    port,
                },
            )))
        }
        _ => Err(invalid_route_error("Unsupported route type")),
    }
}

/// Execute a batch of commands (pipeline or transaction)
#[allow(clippy::too_many_arguments)]
async fn execute_batch(
    client: &mut Client,
    commands: Vec<redis::Cmd>,
    is_atomic: bool,
    raise_on_error: bool,
    timeout: Option<u32>,
    retry_server_error: bool,
    retry_connection_error: bool,
    routing: Option<RoutingInfo>,
    command_span: Option<GlideSpan>,
) -> redis::RedisResult<Value> {
    use redis::Pipeline;

    // Build the pipeline from commands
    let mut pipeline = if is_atomic {
        Pipeline::with_capacity(commands.len())
    } else {
        Pipeline::new()
    };
    pipeline.set_pipeline_span(command_span);

    // Add MULTI for transactions
    if is_atomic {
        pipeline.atomic();
    }

    let mut redis_cmds = Vec::with_capacity(commands.len());
    for mut cmd in commands {
        prepare_command_for_execution(&mut cmd, client, "batch_command_compression")?;
        redis_cmds.push(cmd);
    }

    if let Some(ref span) = pipeline.span() {
        set_db_batch_attributes(span, &redis_cmds, client);
    }

    // Add all commands to the pipeline
    for cmd in redis_cmds {
        pipeline.add_command(cmd);
    }

    // Execute based on type
    let result = if is_atomic {
        client
            .send_transaction(&pipeline, routing, timeout, raise_on_error)
            .await
    } else {
        let retry_strategy = PipelineRetryStrategy {
            retry_server_error,
            retry_connection_error,
        };
        client
            .send_pipeline(&pipeline, routing, raise_on_error, timeout, retry_strategy)
            .await
    };

    match result {
        Ok(value) => match process_batch_response_for_decompression(value.clone(), client) {
            Ok(processed_value) => Ok(processed_value),
            Err(err) => {
                log_warn(
                    "batch_response_decompression",
                    format!("Failed to decompress batch response: {err}"),
                );
                Ok(value)
            }
        },
        Err(err) => Err(err),
    }
}

// ============================================================================
// Direct NAPI Layer - GlideClientHandle
// ============================================================================

/// A handle to a Glide client that allows sending commands directly via NAPI.
/// The client is pinned to a dedicated worker thread for thread-local command execution.
/// Commands are sent via channel to the worker thread which executes them via spawn_local.
///
/// Response Buffering Architecture:
/// - Responses are pushed to a shared buffer instead of calling ThreadsafeFunction per-response
/// - A wake-up callback is called ONCE when the buffer transitions from empty to non-empty
/// - JS drains all responses from the buffer in a single call
#[napi]
pub struct GlideClientHandle {
    /// Channel sender to send commands to the pinned worker thread.
    /// Wrapped in Option to allow explicit drop during close().
    command_tx: Option<mpsc::UnboundedSender<WorkerMessage>>,
    /// Counter tracking inflight requests to enforce limits
    inflight_requests: Arc<AtomicIsize>,
    /// Shared response buffer
    response_buffer: Arc<ResponseBuffer>,
    /// Wake-up callback to notify JS when responses are available.
    /// Wrapped in Option to allow explicit drop during close(), which allows Node.js to exit.
    wake_callback: Option<Arc<ThreadsafeFunction<(), (), (), Status, false>>>,
    /// Unique client ID registered in the glide-core scope registry.
    /// Used for scope operations (try_acquire, execute, release).
    client_id: u64,
}

/// Creates a new direct NAPI client connection with response buffering.
///
/// This function creates a Client using the glide-core library and wraps it
/// in a GlideClientHandle that can send commands directly without socket IPC.
///
/// Response Buffering:
/// - Responses are accumulated in a shared buffer
/// - A single wake-up callback notifies JS when responses are available
/// - JS then calls drainResponses() to get all pending responses at once
/// - This reduces ThreadsafeFunction call overhead from N to ~1 per batch
///
/// # Arguments
/// * `connection_request_bytes` - Protobuf-encoded ConnectionRequest
/// * `wake_callback` - JavaScript callback to wake up when responses available
///
/// # Returns
/// A Promise that resolves to a GlideClientHandle on success
/// Wrap an already-created [`Client`] in a [`GlideClientHandle`] with a dedicated
/// pinned worker thread, command channel, and response buffer.
///
/// This is the factored-out "step 2" of [`create_direct_client`], reused by the
/// pool warmup path so that pool-managed connections get the same full N-API
/// client handle (worker thread + response buffer) as standalone connections.
///
/// The function spawns a pinned task on the worker pool and returns a future
/// that resolves to the handle once the worker has initialised.  Callers must
/// `.await` the returned future from a context that can drive the oneshot
/// receive — the pool runtime's `spawn` context works correctly.
///
/// `push_receiver` must be the receiving end of the channel that was passed
/// (as `push_sender`) to [`Client::new`] when `client` was created.
pub(crate) async fn create_handle_for_client(
    client: glide_core::client::Client,
    mut push_receiver: mpsc::UnboundedReceiver<PushInfo>,
    wake_tsfn: Arc<ThreadsafeFunction<(), (), (), Status, false>>,
    inflight_requests_limit: isize,
    provided_client_id: Option<u64>,
) -> std::result::Result<GlideClientHandle, napi::Error> {
    // Shared response buffer for this handle.
    let response_buffer = Arc::new(ResponseBuffer::new());
    let response_buffer_worker = Arc::clone(&response_buffer);

    // Command channel: JS → worker thread.
    let (command_tx, mut command_rx) = mpsc::unbounded_channel::<WorkerMessage>();

    // Weak wake handles for the worker / push listener.
    let wake_tsfn_worker = Arc::downgrade(&wake_tsfn);

    // Acquire a worker-pool slot (released when the handle's message loop exits).
    let worker_pool = acquire_worker_pool();

    let response_buffer_push = Arc::clone(&response_buffer);
    let wake_tsfn_push = Arc::downgrade(&wake_tsfn);

    // Oneshot channel: worker → caller, delivers the constructed GlideClientHandle.
    let (handle_tx, handle_rx) = tokio::sync::oneshot::channel::<GlideClientHandle>();

    worker_pool.spawn_pinned(move || async move {
        // Clone command_tx for the handle; drop the original so the channel
        // closes when the handle is dropped, letting the message loop exit.
        let command_tx_for_handle = command_tx.clone();
        drop(command_tx);

        let inflight_counter = Arc::new(AtomicIsize::new(inflight_requests_limit));
        let client_id =
            provided_client_id.unwrap_or_else(|| NEXT_CLIENT_ID.fetch_add(1, Ordering::Relaxed));

        // Register client in the scope registry.
        glide_core::scope::register_client(client_id, client.clone());

        let handle = GlideClientHandle {
            command_tx: Some(command_tx_for_handle),
            inflight_requests: inflight_counter.clone(),
            response_buffer: Arc::clone(&response_buffer_worker),
            wake_callback: Some(Arc::clone(&wake_tsfn)),
            client_id,
        };

        // Store worker-local references to avoid Arc::clone per command.
        // These are cloned ONCE here and reused for all commands.
        let worker_inflight = inflight_counter;

        // Spawn a local task to listen for push notifications (pub/sub).
        // NOTE: push listener is spawned BEFORE sending the handle so that
        // cluster-mode push notifications cannot arrive in the window between
        // the caller receiving the handle and the listener being scheduled.
        task::spawn_local(async move {
            while let Some(push_info) = push_receiver.recv().await {
                let push_value = Value::Push {
                    kind: push_info.kind,
                    data: push_info.data,
                };
                let value_ptr = from_mut(Box::leak(Box::new(push_value)));
                let [low, high] = split_pointer(value_ptr);
                let response = CommandResponse {
                    callback_idx: 0,
                    resp_pointer_high: Some(high),
                    resp_pointer_low: Some(low),
                    constant_response: None,
                    request_error: None,
                    closing_error: None,
                    is_push: true,
                };
                if response_buffer_push.push(response)
                    && let Some(wake_callback) = wake_tsfn_push.upgrade()
                {
                    wake_callback.call((), ThreadsafeFunctionCallMode::NonBlocking);
                }
            }
        });

        // Send the handle to the awaiting caller; if the receiver has gone away
        // (caller dropped the future) we continue running the message loop anyway
        // so that the client is not orphaned.
        let _ = handle_tx.send(handle);

        // Process messages from the channel.
        // Each message spawns a local task for concurrent execution within this thread.
        while let Some(msg) = command_rx.recv().await {
            run_worker_message(
                msg,
                &client,
                &worker_inflight,
                &response_buffer_worker,
                &wake_tsfn_worker,
            );
        }

        // Message loop has exited (channel closed by handle.close()).
        release_worker_pool();
    });

    handle_rx
        .await
        .map_err(|_| napi::Error::new(Status::Unknown, "Worker thread failed to initialise"))
}

/// Dispatch a single [`WorkerMessage`] received by a pinned worker task.
///
/// Extracted from the monolithic `spawn_pinned` closure so that both
/// [`create_direct_client`] and [`create_handle_for_client`] share identical
/// dispatch logic without duplication.
fn run_worker_message(
    msg: WorkerMessage,
    client: &glide_core::client::Client,
    worker_inflight: &Arc<AtomicIsize>,
    response_buffer_worker: &Arc<ResponseBuffer>,
    wake_tsfn_worker: &std::sync::Weak<ThreadsafeFunction<(), (), (), Status, false>>,
) {
    match msg {
        WorkerMessage::Command(cmd_msg) => {
            let mut client_clone = client.clone();
            let mut cmd = cmd_msg.cmd;
            let callback_idx = cmd_msg.callback_idx;
            let routing = cmd_msg.routing;
            let pool_blocking_ids = cmd_msg.pool_blocking_ids;
            let inflight = Arc::clone(worker_inflight);
            let buffer = Arc::clone(response_buffer_worker);
            let wake = wake_tsfn_worker.clone();

            task::spawn_local(async move {
                // RAII guard: clears the blocking flag on all exit paths
                // (normal completion, early return, and task cancellation/drop).
                let _unmark_guard = pool_blocking_ids
                    .and_then(glide_core::pool::get_blocking_flag)
                    .map(|arc| UnmarkOnDrop(Some(arc)));
                if let Some(ref span) = cmd.span() {
                    set_db_attributes(span, &cmd, &client_clone);
                }
                let result =
                    match prepare_command_for_execution(&mut cmd, &client_clone, "send_command") {
                        Ok(()) => client_clone.send_command(&mut cmd, routing).await,
                        Err(err) => Err(err),
                    };
                if let Some(client_id) = pool_blocking_ids {
                    pool::mark_blocking(client_id, false);
                }
                let response = build_response(callback_idx, result, cmd.span());
                inflight.fetch_add(1, Ordering::Release);
                if buffer.push(response)
                    && let Some(wake_callback) = wake.upgrade()
                {
                    wake_callback.call((), ThreadsafeFunctionCallMode::NonBlocking);
                }
            });
        }
        WorkerMessage::Batch(batch_msg) => {
            let mut client_clone = client.clone();
            let callback_idx = batch_msg.callback_idx;
            let pool_ids = batch_msg.pool_ids;
            let inflight = Arc::clone(worker_inflight);
            let buffer = Arc::clone(response_buffer_worker);
            let wake = wake_tsfn_worker.clone();

            task::spawn_local(async move {
                // RAII guard: clears the blocking flag on all exit paths
                // (normal completion, early return, and task cancellation/drop).
                let _unmark_guard = pool_ids
                    .and_then(glide_core::pool::get_blocking_flag)
                    .map(|arc| UnmarkOnDrop(Some(arc)));
                let command_span = batch_msg.command_span.clone();
                let result = execute_batch(
                    &mut client_clone,
                    batch_msg.commands,
                    batch_msg.is_atomic,
                    batch_msg.raise_on_error,
                    batch_msg.timeout,
                    batch_msg.retry_server_error,
                    batch_msg.retry_connection_error,
                    batch_msg.routing,
                    batch_msg.command_span,
                )
                .await;
                if let Some(client_id) = pool_ids {
                    pool::mark_blocking(client_id, false);
                }
                let response = build_response(callback_idx, result, command_span);
                inflight.fetch_add(1, Ordering::Release);
                if buffer.push(response)
                    && let Some(wake_callback) = wake.upgrade()
                {
                    wake_callback.call((), ThreadsafeFunctionCallMode::NonBlocking);
                }
            });
        }
        WorkerMessage::ScriptInvocation(script_msg) => {
            let mut client_clone = client.clone();
            let callback_idx = script_msg.callback_idx;
            let pool_ids = script_msg.pool_ids;
            let inflight = Arc::clone(worker_inflight);
            let buffer = Arc::clone(response_buffer_worker);
            let wake = wake_tsfn_worker.clone();

            task::spawn_local(async move {
                // RAII guard: clears the blocking flag on all exit paths
                // (normal completion, early return, and task cancellation/drop).
                let _unmark_guard = pool_ids
                    .and_then(glide_core::pool::get_blocking_flag)
                    .map(|arc| UnmarkOnDrop(Some(arc)));
                let keys: Vec<&[u8]> = script_msg.keys.iter().map(|k| k.as_ref()).collect();
                let args: Vec<&[u8]> = script_msg.args.iter().map(|a| a.as_ref()).collect();
                let result = client_clone
                    .invoke_script(&script_msg.hash, &keys, &args, script_msg.routing)
                    .await;
                if let Some(client_id) = pool_ids {
                    pool::mark_blocking(client_id, false);
                }
                let response = build_response(callback_idx, result, None);
                inflight.fetch_add(1, Ordering::Release);
                if buffer.push(response)
                    && let Some(wake_callback) = wake.upgrade()
                {
                    wake_callback.call((), ThreadsafeFunctionCallMode::NonBlocking);
                }
            });
        }
        WorkerMessage::ClusterScan(scan_msg) => {
            let mut client_clone = client.clone();
            let callback_idx = scan_msg.callback_idx;
            let inflight = Arc::clone(worker_inflight);
            let buffer = Arc::clone(response_buffer_worker);
            let wake = wake_tsfn_worker.clone();

            task::spawn_local(async move {
                let cursor_result = if scan_msg.cursor.is_empty() {
                    Ok(ScanStateRC::new())
                } else {
                    get_cluster_scan_cursor(scan_msg.cursor)
                };

                let result = match cursor_result {
                    Ok(scan_cursor) => {
                        let mut args_builder = ClusterScanArgs::builder()
                            .allow_non_covered_slots(scan_msg.allow_non_covered_slots);
                        if let Some(pattern) = scan_msg.match_pattern {
                            args_builder = args_builder.with_match_pattern::<Bytes>(pattern);
                        }
                        if let Some(count) = scan_msg.count {
                            args_builder = args_builder.with_count(count as u32);
                        }
                        if let Some(obj_type) = scan_msg.object_type {
                            args_builder = args_builder.with_object_type(obj_type.into());
                        }
                        let scan_args = args_builder.build();
                        client_clone.cluster_scan(&scan_cursor, scan_args).await
                    }
                    Err(e) => Err(e),
                };
                let response = build_response(callback_idx, result, None);
                inflight.fetch_add(1, Ordering::Release);
                if buffer.push(response)
                    && let Some(wake_callback) = wake.upgrade()
                {
                    wake_callback.call((), ThreadsafeFunctionCallMode::NonBlocking);
                }
            });
        }
        WorkerMessage::UpdateConnectionPassword(pwd_msg) => {
            let mut client_clone = client.clone();
            let callback_idx = pwd_msg.callback_idx;
            let inflight = Arc::clone(worker_inflight);
            let buffer = Arc::clone(response_buffer_worker);
            let wake = wake_tsfn_worker.clone();

            task::spawn_local(async move {
                let result = client_clone
                    .update_connection_password(pwd_msg.password, pwd_msg.immediate_auth)
                    .await;
                let response = build_response(callback_idx, result, None);
                inflight.fetch_add(1, Ordering::Release);
                if buffer.push(response)
                    && let Some(wake_callback) = wake.upgrade()
                {
                    wake_callback.call((), ThreadsafeFunctionCallMode::NonBlocking);
                }
            });
        }
        WorkerMessage::RefreshIamToken(iam_msg) => {
            let mut client_clone = client.clone();
            let callback_idx = iam_msg.callback_idx;
            let inflight = Arc::clone(worker_inflight);
            let buffer = Arc::clone(response_buffer_worker);
            let wake = wake_tsfn_worker.clone();

            task::spawn_local(async move {
                let result = client_clone.refresh_iam_token().await.map(|()| Value::Okay);
                let response = build_response(callback_idx, result, None);
                inflight.fetch_add(1, Ordering::Release);
                if buffer.push(response)
                    && let Some(wake_callback) = wake.upgrade()
                {
                    wake_callback.call((), ThreadsafeFunctionCallMode::NonBlocking);
                }
            });
        }
        WorkerMessage::GetCacheMetrics(metrics_msg) => {
            let client_clone = client.clone();
            let callback_idx = metrics_msg.callback_idx;
            let inflight = Arc::clone(worker_inflight);
            let buffer = Arc::clone(response_buffer_worker);
            let wake = wake_tsfn_worker.clone();

            task::spawn_local(async move {
                let result = get_cache_metrics(&client_clone, metrics_msg.metrics_type);
                let response = build_response(callback_idx, result, None);
                inflight.fetch_add(1, Ordering::Release);
                if buffer.push(response)
                    && let Some(wake_callback) = wake.upgrade()
                {
                    wake_callback.call((), ThreadsafeFunctionCallMode::NonBlocking);
                }
            });
        }
    }
}

#[napi(
    js_name = "CreateDirectClient",
    ts_return_type = "Promise<GlideClientHandle>"
)]
pub fn create_direct_client<'a>(
    env: &'a Env,
    connection_request_bytes: Uint8Array,
    #[napi(ts_arg_type = "() => void")] wake_callback: Function<(), ()>,
) -> Result<Object<'a>> {
    let (deferred, promise) = env.create_deferred()?;

    // Create the wake-up ThreadsafeFunction
    // This callback takes no arguments - it just signals "responses available"
    let wake_tsfn: Arc<ThreadsafeFunction<(), (), (), Status, false>> =
        Arc::new(wake_callback.build_threadsafe_function().build()?);

    // Parse the connection request protobuf
    let proto_connection_request =
        match ProtobufConnectionRequest::parse_from_bytes(&connection_request_bytes) {
            Ok(req) => req,
            Err(err) => {
                return Err(napi::Error::new(
                    Status::InvalidArg,
                    format!("Failed to parse connection request: {err}"),
                ));
            }
        };
    let resolver_key = proto_connection_request
        .address_resolver_key
        .as_ref()
        .filter(|key| !key.is_empty())
        .map(ToString::to_string);

    // Get the inflight requests limit from the protobuf connection request
    let inflight_requests_limit = if proto_connection_request.inflight_requests_limit > 0 {
        proto_connection_request.inflight_requests_limit as isize
    } else {
        glide_core::client::DEFAULT_MAX_INFLIGHT_REQUESTS as isize
    };

    // Convert protobuf ConnectionRequest to internal ConnectionRequest
    let mut connection_request: ConnectionRequest = proto_connection_request.into();
    if let Some(key) = resolver_key
        && let Some(resolver) = glide_core::address_resolver_registry::remove(&key)
    {
        connection_request.address_resolver = Some(resolver);
    }

    // Create shared response buffer
    let response_buffer = Arc::new(ResponseBuffer::new());
    let response_buffer_worker = Arc::clone(&response_buffer);

    // Create channel for sending commands to the worker thread
    let (command_tx, mut command_rx) = mpsc::unbounded_channel::<WorkerMessage>();

    // Share wake callback with worker as a weak handle so
    // in-flight tasks do not keep the JS callback alive after close().
    let wake_tsfn_worker = Arc::downgrade(&wake_tsfn);

    // Acquire a reference to the worker pool (increments client count).
    // The pool will be released when the GlideClientHandle is dropped.
    let worker_pool = acquire_worker_pool();

    // Create push notification channel for pub/sub
    let (push_sender, mut push_receiver) = mpsc::unbounded_channel::<PushInfo>();
    let response_buffer_push = Arc::clone(&response_buffer);
    let wake_tsfn_push = Arc::downgrade(&wake_tsfn);

    // Spawn a pinned worker task that owns the Client and processes commands.
    // Client::new is called INSIDE spawn_pinned so the push listener is set up
    // atomically with the connection — this prevents a race where PubSub push
    // notifications (subscription confirmations) arrive before the listener task
    // is running, which would cause them to buffer silently with no wake_callback
    // fired, resulting in PubSub tests timing out.
    worker_pool.spawn_pinned(move || async move {
        // Create the client on this worker thread
        let client = match Client::new(connection_request, Some(push_sender)).await {
            Ok(c) => c,
            Err(err) => {
                deferred.reject(napi::Error::new(
                    Status::Unknown,
                    format!("Failed to create client: {err}"),
                ));
                // Release pool reference since client creation failed
                release_worker_pool();
                return;
            }
        };

        // Create handle to return to JavaScript.
        // Clone command_tx for the handle — the original will be dropped after this.
        // This ensures the channel closes when the handle is dropped,
        // which allows the message loop below to exit.
        let command_tx_for_handle = command_tx.clone();
        drop(command_tx);

        let inflight_counter = Arc::new(AtomicIsize::new(inflight_requests_limit));
        let client_id = NEXT_CLIENT_ID.fetch_add(1, Ordering::Relaxed);

        // Register client in scope registry so scoped connections can find
        // their parent client for compression, timeout, IAM, and CB checks.
        glide_core::scope::register_client(client_id, client.clone());

        let handle = GlideClientHandle {
            command_tx: Some(command_tx_for_handle),
            inflight_requests: inflight_counter.clone(),
            response_buffer: Arc::clone(&response_buffer_worker),
            wake_callback: Some(Arc::clone(&wake_tsfn)),
            client_id,
        };

        // Store worker-local references to avoid Arc::clone per command.
        // These are cloned ONCE here and reused for all commands.
        let worker_inflight = inflight_counter;

        // Spawn a local task to listen for push notifications (pub/sub).
        // Use a weak wake handle to avoid extending callback lifetime after close().
        // Push messages arrive from glide-core via the push_receiver channel.
        // NOTE: push listener is spawned BEFORE resolving the promise so that
        // cluster-mode push notifications (e.g. subscription confirmations)
        // cannot arrive in the window between promise resolution and listener
        // scheduling.
        task::spawn_local(async move {
            while let Some(push_info) = push_receiver.recv().await {
                let push_value = Value::Push {
                    kind: push_info.kind,
                    data: push_info.data,
                };
                let value_ptr = from_mut(Box::leak(Box::new(push_value)));
                let [low, high] = split_pointer(value_ptr);
                let response = CommandResponse {
                    callback_idx: 0,
                    resp_pointer_high: Some(high),
                    resp_pointer_low: Some(low),
                    constant_response: None,
                    request_error: None,
                    closing_error: None,
                    is_push: true,
                };
                if response_buffer_push.push(response)
                    && let Some(wake_callback) = wake_tsfn_push.upgrade()
                {
                    wake_callback.call((), ThreadsafeFunctionCallMode::NonBlocking);
                }
            }
        });

        // Resolve the promise with the handle — push listener is now scheduled,
        // so no push notifications can be missed after this point.
        deferred.resolve(|_| Ok(handle));

        // Process messages from the channel.
        // Each message spawns a local task for concurrent execution within this thread.
        while let Some(msg) = command_rx.recv().await {
            run_worker_message(
                msg,
                &client,
                &worker_inflight,
                &response_buffer_worker,
                &wake_tsfn_worker,
            );
        }

        // Message loop has exited (channel closed by handle.close()).
        release_worker_pool();
    });

    Ok(promise)
}

#[napi]
impl GlideClientHandle {
    /// Sends a command to the Valkey/Redis server.
    ///
    /// This method is the core of the direct NAPI layer. It:
    /// 1. Checks inflight request limits synchronously
    /// 2. Reconstructs the command arguments from the pointer
    /// 3. Spawns an async task to execute the command
    /// 4. Calls the response callback with the result
    ///
    /// # Arguments
    /// * `callback_idx` - Index to identify this request in JavaScript
    /// * `request_type` - The type of Redis command (maps to RequestType enum)
    /// * `args_pointer_high` - High 32 bits of the args `Vec<Bytes>` pointer
    /// * `args_pointer_low` - Low 32 bits of the args `Vec<Bytes>` pointer
    /// * `span_ptr` - Optional OpenTelemetry span pointer
    /// * `route_bytes` - Optional routing information for cluster mode
    ///
    /// # Returns
    /// * `true` if the command was successfully queued
    /// * `false` if the inflight request limit was exceeded
    #[napi]
    #[inline]
    pub fn send_command(
        &self,
        callback_idx: u32,
        request_type: u32,
        args_pointer_high: u32,
        args_pointer_low: u32,
        span_ptr: Option<BigInt>,
        route_bytes: Option<Uint8Array>,
    ) -> Result<bool> {
        // Reconstruct the args pointer from high/low bits (simple bit ops, no allocation)
        // IMPORTANT: Reclaim the pointer BEFORE the inflight check to avoid leaking
        // the args if the inflight limit is exceeded and we return early.
        let pointer = (args_pointer_low as u64) | ((args_pointer_high as u64) << 32);

        // Take ownership of the args vector from the raw pointer
        // SAFETY: The pointer must have been created by create_leaked_string_vec
        // and must not have been freed yet. A zero pointer means no arguments.
        let args: Vec<Bytes> = if pointer == 0 {
            Vec::new()
        } else {
            *unsafe { Box::from_raw(pointer as *mut Vec<Bytes>) }
        };

        // Check inflight limit synchronously
        // fetch_sub returns the previous value, so we check if it was > 0
        // Use AcqRel for the decrement (Acquire to see current value, Release for visibility)
        let prev = self.inflight_requests.fetch_sub(1, Ordering::AcqRel);
        if prev <= 0 {
            // Restore the counter since we're not actually sending (Relaxed is fine for restore)
            self.inflight_requests.fetch_add(1, Ordering::Relaxed);
            // args is dropped here, freeing the reclaimed pointer
            return Ok(false);
        }

        // Convert request_type u32 to RequestType enum via protobuf EnumOrUnknown
        let proto_request_type =
            protobuf::EnumOrUnknown::<ProtobufRequestType>::from_i32(request_type as i32);
        let request_type_enum: RequestType = proto_request_type.into();
        let command_span = span_from_bigint(span_ptr);

        // Get the base command for this request type
        let mut cmd = match request_type_enum.get_command() {
            Some(cmd) => cmd,
            None => {
                // Invalid request type - push error to buffer
                self.inflight_requests.fetch_add(1, Ordering::Relaxed);
                let error_message = format!("Invalid request type: {request_type}");
                mark_span_error(&command_span, &error_message);
                let response = CommandResponse {
                    callback_idx,
                    resp_pointer_high: None,
                    resp_pointer_low: None,
                    constant_response: None,
                    request_error: Some(RequestErrorNapi {
                        message: error_message,
                        error_type: 0, // Unspecified
                    }),
                    closing_error: None,
                    is_push: false,
                };
                push_response_to_js(&self.response_buffer, &self.wake_callback, response);
                return Ok(true);
            }
        };

        // Add arguments to the command
        for arg in args.iter() {
            cmd.arg(arg.as_ref());
        }

        cmd.set_span(command_span);

        // Parse routing information if provided
        let routing = match route_bytes {
            Some(bytes) => match parse_route_bytes(&bytes, Some(&cmd)) {
                Ok(routing) => routing,
                Err(err) => {
                    self.inflight_requests.fetch_add(1, Ordering::Relaxed);
                    let response = build_response(callback_idx, Err(err), cmd.span());
                    push_response_to_js(&self.response_buffer, &self.wake_callback, response);
                    return Ok(true);
                }
            },
            None => None,
        };
        let command_span_for_error = cmd.span();

        // Pool abandon detection: refresh activity and mark blocking commands
        let pool_blocking_ids = if glide_core::pool::is_pool_client(self.client_id) {
            glide_core::pool::refresh_activity_by_client(self.client_id);
            if glide_core::client::is_blocking_command(&cmd)
                && pool::mark_blocking(self.client_id, true)
            {
                Some(self.client_id)
            } else {
                None
            }
        } else {
            None
        };

        // Send command to the pinned worker thread via channel
        let msg = WorkerMessage::Command(SingleCommandMessage {
            callback_idx,
            cmd,
            routing,
            pool_blocking_ids,
        });

        // Send via channel (non-blocking)
        let send_failed = match &self.command_tx {
            Some(tx) => tx.send(msg).is_err(),
            None => true, // Channel already dropped via close()
        };
        if send_failed {
            // Channel closed - client was shut down
            self.inflight_requests.fetch_add(1, Ordering::Relaxed);
            // Release blocking mark — the worker will never run
            if let Some(client_id) = pool_blocking_ids {
                pool::mark_blocking(client_id, false);
            }
            mark_span_error(&command_span_for_error, "Client connection closed");
            let response = CommandResponse {
                callback_idx,
                resp_pointer_high: None,
                resp_pointer_low: None,
                constant_response: None,
                request_error: None,
                closing_error: Some("Client connection closed".to_string()),
                is_push: false,
            };
            push_response_to_js(&self.response_buffer, &self.wake_callback, response);
        }

        Ok(true)
    }

    /// Drains all pending responses from the buffer.
    /// This should be called from JS when the wake callback is triggered.
    #[napi]
    #[inline]
    pub fn drain_responses(&self) -> Vec<CommandResponse> {
        self.response_buffer.drain()
    }

    /// Closes the client connection.
    /// After calling this, the client handle should not be used.
    /// This marks the buffer as closed to prevent any further wake-up callbacks,
    /// which prevents use-after-free crashes during shutdown.
    #[napi]
    pub fn close(&mut self) -> Result<()> {
        // IMPORTANT: Mark closed FIRST to prevent any in-flight tasks from calling wake_callback
        // This prevents segfaults when the ThreadsafeFunction is dropped while tasks are running
        self.response_buffer.mark_closed();

        // Unregister from scope registry
        glide_core::scope::unregister_client(self.client_id);

        // Free any leaked Value pointers in pending responses that were never consumed by JS
        self.response_buffer.free_leaked_values();

        // Drop the command channel sender to signal the worker loop to exit.
        // When all senders are dropped, command_rx.recv() will return None,
        // causing the worker loop to exit gracefully.
        drop(self.command_tx.take());

        // Drop our reference to the wake callback.
        // This allows Node.js to exit cleanly once all other references are dropped.
        // The spawned worker task also holds references, but those will be dropped
        // when the message loop exits (which happens after command_tx is dropped above).
        drop(self.wake_callback.take());

        Ok(())
    }

    /// Stop this handle's worker thread WITHOUT removing the client from the
    /// scope registry.
    ///
    /// Used by pool clients: when a pool client is released back to the pool,
    /// its underlying [`glide_core::client::Client`] must remain registered in
    /// the scope registry so that the next `pool_build_handle` call can find it
    /// and spin up a new worker for the next borrower.
    ///
    /// For standalone (non-pool) clients, call `close()` instead.
    #[napi]
    pub fn close_for_pool_release(&mut self) -> Result<()> {
        // Mark buffer closed first — prevents callbacks after this point.
        self.response_buffer.mark_closed();

        // Do NOT unregister from scope registry: the Client must stay registered
        // so that the next pool_build_handle() call can find it.

        // Do NOT call unregister_pool_client here: release_client_async (called
        // by pool_release after this) already calls unregister_pool_client, so
        // calling it here would result in a double-unregister.

        // Free any leaked Value pointers.
        self.response_buffer.free_leaked_values();

        // Drop command_tx to signal the worker loop to exit.
        drop(self.command_tx.take());

        // Drop the wake callback reference.
        drop(self.wake_callback.take());

        Ok(())
    }

    /// Returns the number of available inflight request slots.
    /// This can be used to check if more commands can be sent.
    #[napi]
    pub fn available_inflight_slots(&self) -> i32 {
        self.inflight_requests.load(Ordering::Relaxed) as i32
    }

    /// Returns the client ID registered in the scope registry.
    /// Used by IsolatedScope to acquire/execute/release scoped connections.
    #[napi(getter)]
    pub fn client_id(&self) -> i64 {
        self.client_id as i64
    }

    /// Sends a batch of commands to the Valkey/Redis server.
    ///
    /// # Arguments
    /// * `callback_idx` - Index to identify this request in JavaScript
    /// * `commands` - Array of command tuples (request_type, args_pointer_high, args_pointer_low)
    /// * `is_atomic` - If true, execute as transaction (MULTI/EXEC); if false, as pipeline
    /// * `raise_on_error` - Whether to raise error on first failure
    /// * `timeout` - Optional timeout in milliseconds
    ///
    /// # Returns
    /// * `true` if the batch was successfully queued
    /// * `false` if the inflight request limit was exceeded
    #[napi]
    #[allow(clippy::too_many_arguments)]
    pub fn send_batch(
        &self,
        callback_idx: u32,
        commands: Vec<BatchCommand>,
        is_atomic: bool,
        raise_on_error: bool,
        timeout: Option<u32>,
        retry_server_error: Option<bool>,
        retry_connection_error: Option<bool>,
        span_ptr: Option<BigInt>,
        route_bytes: Option<Uint8Array>,
    ) -> Result<bool> {
        // Check inflight limit synchronously
        let prev = self.inflight_requests.fetch_sub(1, Ordering::AcqRel);
        if prev <= 0 {
            self.inflight_requests.fetch_add(1, Ordering::Relaxed);
            // Free all leaked arg pointers in the batch commands before returning
            for cmd in &commands {
                let ptr = (cmd.args_pointer_low as u64) | ((cmd.args_pointer_high as u64) << 32);
                if ptr != 0 {
                    unsafe { drop(Box::from_raw(ptr as *mut Vec<Bytes>)) };
                }
            }
            return Ok(false);
        }

        // Convert BatchCommand array to Vec<redis::Cmd>
        let command_span = span_from_bigint(span_ptr);
        let mut cmds = Vec::with_capacity(commands.len());
        let mut commands_iter = commands.into_iter();
        while let Some(batch_cmd) = commands_iter.next() {
            // Reconstruct the args pointer (zero means no arguments)
            let pointer =
                (batch_cmd.args_pointer_low as u64) | ((batch_cmd.args_pointer_high as u64) << 32);
            let args: Vec<Bytes> = if pointer == 0 {
                Vec::new()
            } else {
                *unsafe { Box::from_raw(pointer as *mut Vec<Bytes>) }
            };

            // Convert request_type to RequestType enum
            let proto_request_type = protobuf::EnumOrUnknown::<ProtobufRequestType>::from_i32(
                batch_cmd.request_type as i32,
            );
            let request_type_enum: RequestType = proto_request_type.into();

            // Get the base command
            let Some(mut cmd) = request_type_enum.get_command() else {
                // Free remaining batch command arg pointers to prevent leaks
                for remaining_cmd in commands_iter {
                    let ptr = (remaining_cmd.args_pointer_low as u64)
                        | ((remaining_cmd.args_pointer_high as u64) << 32);
                    if ptr != 0 {
                        unsafe { drop(Box::from_raw(ptr as *mut Vec<Bytes>)) };
                    }
                }
                self.inflight_requests.fetch_add(1, Ordering::Relaxed);
                let error_message = format!("Invalid request type: {}", batch_cmd.request_type);
                mark_span_error(&command_span, &error_message);
                let response = CommandResponse {
                    callback_idx,
                    resp_pointer_high: None,
                    resp_pointer_low: None,
                    constant_response: None,
                    request_error: Some(RequestErrorNapi {
                        message: error_message,
                        error_type: 0,
                    }),
                    closing_error: None,
                    is_push: false,
                };
                push_response_to_js(&self.response_buffer, &self.wake_callback, response);
                return Ok(true);
            };

            // Add arguments to the command
            for arg in args.iter() {
                cmd.arg(arg.as_ref());
            }

            cmds.push(cmd);
        }

        // Parse routing information if provided
        let routing = match route_bytes {
            Some(bytes) => match parse_route_bytes(&bytes, None) {
                Ok(routing) => routing,
                Err(err) => {
                    self.inflight_requests.fetch_add(1, Ordering::Relaxed);
                    let response = build_response(callback_idx, Err(err), command_span);
                    push_response_to_js(&self.response_buffer, &self.wake_callback, response);
                    return Ok(true);
                }
            },
            None => None,
        };
        let command_span_for_error = command_span.clone();

        // Pool abandon detection: refresh activity for batch duration
        let pool_ids = if glide_core::pool::is_pool_client(self.client_id) {
            glide_core::pool::refresh_activity_by_client(self.client_id);
            Some(self.client_id)
        } else {
            None
        };

        // Set is_blocking synchronously on the sender thread BEFORE channel send,
        // so the abandon monitor cannot observe is_blocking=false in the window
        // between enqueue and the worker thread dequeuing the message (#6971).
        if let Some(client_id) = pool_ids {
            pool::mark_blocking(client_id, true);
        }

        // Send batch message to worker
        let msg = WorkerMessage::Batch(BatchCommandMessage {
            callback_idx,
            commands: cmds,
            is_atomic,
            raise_on_error,
            timeout,
            retry_server_error: retry_server_error.unwrap_or(false),
            retry_connection_error: retry_connection_error.unwrap_or(false),
            routing,
            command_span,
            pool_ids,
        });

        let send_failed = match &self.command_tx {
            Some(tx) => tx.send(msg).is_err(),
            None => true,
        };
        if send_failed {
            // Sender-side mark was already applied; undo it since the message was never delivered.
            if let Some(client_id) = pool_ids {
                pool::mark_blocking(client_id, false);
            }
            self.inflight_requests.fetch_add(1, Ordering::Relaxed);
            mark_span_error(&command_span_for_error, "Client connection closed");
            let response = CommandResponse {
                callback_idx,
                resp_pointer_high: None,
                resp_pointer_low: None,
                constant_response: None,
                request_error: None,
                closing_error: Some("Client connection closed".to_string()),
                is_push: false,
            };
            push_response_to_js(&self.response_buffer, &self.wake_callback, response);
        }

        Ok(true)
    }

    /// Invokes a Lua script using EVALSHA with automatic LOAD fallback.
    #[napi]
    #[allow(clippy::too_many_arguments)]
    pub fn invoke_script(
        &self,
        callback_idx: u32,
        hash: String,
        keys_pointer_high: u32,
        keys_pointer_low: u32,
        args_pointer_high: u32,
        args_pointer_low: u32,
        route_bytes: Option<Uint8Array>,
    ) -> Result<bool> {
        // Reconstruct keys pointer
        // IMPORTANT: Reclaim pointers BEFORE the inflight check to avoid leaking
        // the keys/args if the inflight limit is exceeded and we return early.
        // A zero pointer means no keys/args.
        let keys_pointer = (keys_pointer_low as u64) | ((keys_pointer_high as u64) << 32);
        let keys: Vec<Bytes> = if keys_pointer == 0 {
            Vec::new()
        } else {
            *unsafe { Box::from_raw(keys_pointer as *mut Vec<Bytes>) }
        };

        // Reconstruct args pointer
        let args_pointer = (args_pointer_low as u64) | ((args_pointer_high as u64) << 32);
        let args: Vec<Bytes> = if args_pointer == 0 {
            Vec::new()
        } else {
            *unsafe { Box::from_raw(args_pointer as *mut Vec<Bytes>) }
        };

        // Check inflight limit
        let prev = self.inflight_requests.fetch_sub(1, Ordering::AcqRel);
        if prev <= 0 {
            self.inflight_requests.fetch_add(1, Ordering::Relaxed);
            // keys and args are dropped here, freeing the reclaimed pointers
            return Ok(false);
        }

        // Parse routing information if provided
        let routing = match route_bytes {
            Some(bytes) => match parse_route_bytes(&bytes, None) {
                Ok(routing) => routing,
                Err(err) => {
                    self.inflight_requests.fetch_add(1, Ordering::Relaxed);
                    let response = build_response(callback_idx, Err(err), None);
                    push_response_to_js(&self.response_buffer, &self.wake_callback, response);
                    return Ok(true);
                }
            },
            None => None,
        };

        // Pool abandon detection: refresh activity for script execution
        let pool_ids = if glide_core::pool::is_pool_client(self.client_id) {
            glide_core::pool::refresh_activity_by_client(self.client_id);
            Some(self.client_id)
        } else {
            None
        };

        // Set is_blocking synchronously on the sender thread BEFORE channel send,
        // so the abandon monitor cannot observe is_blocking=false in the window
        // between enqueue and the worker thread dequeuing the message (#6971).
        if let Some(client_id) = pool_ids {
            pool::mark_blocking(client_id, true);
        }

        let msg = WorkerMessage::ScriptInvocation(ScriptInvocationMessage {
            callback_idx,
            hash,
            keys,
            args,
            routing,
            pool_ids,
        });

        let send_failed = match &self.command_tx {
            Some(tx) => tx.send(msg).is_err(),
            None => true,
        };
        if send_failed {
            // Sender-side mark was already applied; undo it since the message was never delivered.
            if let Some(client_id) = pool_ids {
                pool::mark_blocking(client_id, false);
            }
            self.inflight_requests.fetch_add(1, Ordering::Relaxed);
            let response = CommandResponse {
                callback_idx,
                resp_pointer_high: None,
                resp_pointer_low: None,
                constant_response: None,
                request_error: None,
                closing_error: Some("Client connection closed".to_string()),
                is_push: false,
            };
            if self.response_buffer.push(response)
                && let Some(cb) = &self.wake_callback
            {
                cb.call((), ThreadsafeFunctionCallMode::NonBlocking);
            }
        }

        Ok(true)
    }

    /// Performs cluster-wide key scanning.
    #[napi]
    pub fn cluster_scan(
        &self,
        callback_idx: u32,
        cursor: String,
        match_pattern: Option<Uint8Array>,
        count: Option<i64>,
        object_type: Option<String>,
        allow_non_covered_slots: Option<bool>,
    ) -> Result<bool> {
        // Check inflight limit
        let prev = self.inflight_requests.fetch_sub(1, Ordering::AcqRel);
        if prev <= 0 {
            self.inflight_requests.fetch_add(1, Ordering::Relaxed);
            return Ok(false);
        }

        let msg = WorkerMessage::ClusterScan(ClusterScanMessage {
            callback_idx,
            cursor,
            match_pattern: match_pattern.map(|p| Bytes::from(p.to_vec())),
            count,
            object_type,
            allow_non_covered_slots: allow_non_covered_slots.unwrap_or(false),
        });

        // Pool abandon detection: refresh activity for scan
        if glide_core::pool::is_pool_client(self.client_id) {
            glide_core::pool::refresh_activity_by_client(self.client_id);
        }

        let send_failed = match &self.command_tx {
            Some(tx) => tx.send(msg).is_err(),
            None => true,
        };
        if send_failed {
            self.inflight_requests.fetch_add(1, Ordering::Relaxed);
            let response = CommandResponse {
                callback_idx,
                resp_pointer_high: None,
                resp_pointer_low: None,
                constant_response: None,
                request_error: None,
                closing_error: Some("Client connection closed".to_string()),
                is_push: false,
            };
            if self.response_buffer.push(response)
                && let Some(cb) = &self.wake_callback
            {
                cb.call((), ThreadsafeFunctionCallMode::NonBlocking);
            }
        }

        Ok(true)
    }

    /// Updates the connection password.
    #[napi]
    pub fn update_connection_password(
        &self,
        callback_idx: u32,
        password: Option<String>,
        immediate_auth: bool,
    ) -> Result<bool> {
        // Check inflight limit
        let prev = self.inflight_requests.fetch_sub(1, Ordering::AcqRel);
        if prev <= 0 {
            self.inflight_requests.fetch_add(1, Ordering::Relaxed);
            return Ok(false);
        }

        let msg = WorkerMessage::UpdateConnectionPassword(UpdateConnectionPasswordMessage {
            callback_idx,
            password,
            immediate_auth,
        });

        let send_failed = match &self.command_tx {
            Some(tx) => tx.send(msg).is_err(),
            None => true,
        };
        if send_failed {
            self.inflight_requests.fetch_add(1, Ordering::Relaxed);
            let response = CommandResponse {
                callback_idx,
                resp_pointer_high: None,
                resp_pointer_low: None,
                constant_response: None,
                request_error: None,
                closing_error: Some("Client connection closed".to_string()),
                is_push: false,
            };
            if self.response_buffer.push(response)
                && let Some(cb) = &self.wake_callback
            {
                cb.call((), ThreadsafeFunctionCallMode::NonBlocking);
            }
        }

        Ok(true)
    }

    /// Refreshes the IAM token.
    #[napi]
    pub fn refresh_iam_token(&self, callback_idx: u32) -> Result<bool> {
        // Check inflight limit
        let prev = self.inflight_requests.fetch_sub(1, Ordering::AcqRel);
        if prev <= 0 {
            self.inflight_requests.fetch_add(1, Ordering::Relaxed);
            return Ok(false);
        }

        let msg = WorkerMessage::RefreshIamToken(RefreshIamTokenMessage { callback_idx });

        let send_failed = match &self.command_tx {
            Some(tx) => tx.send(msg).is_err(),
            None => true,
        };
        if send_failed {
            self.inflight_requests.fetch_add(1, Ordering::Relaxed);
            let response = CommandResponse {
                callback_idx,
                resp_pointer_high: None,
                resp_pointer_low: None,
                constant_response: None,
                request_error: None,
                closing_error: Some("Client connection closed".to_string()),
                is_push: false,
            };
            if self.response_buffer.push(response)
                && let Some(cb) = &self.wake_callback
            {
                cb.call((), ThreadsafeFunctionCallMode::NonBlocking);
            }
        }

        Ok(true)
    }

    /// Gets client-side cache metrics.
    #[napi]
    pub fn get_cache_metrics(&self, callback_idx: u32, metrics_type: u32) -> Result<bool> {
        let prev = self.inflight_requests.fetch_sub(1, Ordering::AcqRel);
        if prev <= 0 {
            self.inflight_requests.fetch_add(1, Ordering::Relaxed);
            return Ok(false);
        }

        let msg = WorkerMessage::GetCacheMetrics(GetCacheMetricsMessage {
            callback_idx,
            metrics_type,
        });

        let send_failed = match &self.command_tx {
            Some(tx) => tx.send(msg).is_err(),
            None => true,
        };
        if send_failed {
            self.inflight_requests.fetch_add(1, Ordering::Relaxed);
            let response = CommandResponse {
                callback_idx,
                resp_pointer_high: None,
                resp_pointer_low: None,
                constant_response: None,
                request_error: None,
                closing_error: Some("Client connection closed".to_string()),
                is_push: false,
            };
            if self.response_buffer.push(response)
                && let Some(cb) = &self.wake_callback
            {
                cb.call((), ThreadsafeFunctionCallMode::NonBlocking);
            }
        }

        Ok(true)
    }
}

impl Drop for GlideClientHandle {
    fn drop(&mut self) {
        // Ensure cleanup happens even if close() was never called.
        // mark_closed() is idempotent (uses AtomicBool), so calling it again is safe.
        self.response_buffer.mark_closed();

        // Free any leaked Value pointers in pending responses.
        // If close() already called this, the buffer will be empty and this is a no-op.
        self.response_buffer.free_leaked_values();

        // Drop the command channel to signal worker to exit.
        // If already dropped by close(), this is a no-op.
        // The spawned worker task will call release_worker_pool() when its message loop exits,
        // which happens after this channel is closed.
        drop(self.command_tx.take());
    }
}

/// A single command in a batch
#[napi(object)]
pub struct BatchCommand {
    pub request_type: u32,
    pub args_pointer_high: u32,
    pub args_pointer_low: u32,
}

/// Configuration for OpenTelemetry integration in the Node.js client.
///
/// This struct allows you to configure how telemetry data (traces and metrics) is exported to an OpenTelemetry collector.
/// - `traces`: Optional configuration for exporting trace data. If `None`, trace data will not be exported.
/// - `metrics`: Optional configuration for exporting metrics data. If `None`, metrics data will not be exported.
/// - `flush_interval_ms`: Optional interval in milliseconds between consecutive exports of telemetry data. If `None`, a default value will be used.
///
/// At least one of traces or metrics must be provided.
#[napi(object)]
#[derive(Clone)]
pub struct OpenTelemetryConfig {
    /// Optional configuration for exporting trace data. If `None`, trace data will not be exported.
    pub traces: Option<OpenTelemetryTracesConfig>,
    /// Optional configuration for exporting metrics data. If `None`, metrics data will not be exported.
    pub metrics: Option<OpenTelemetryMetricsConfig>,
    /// Optional interval in milliseconds between consecutive exports of telemetry data. If `None`, the default `DEFAULT_FLUSH_SIGNAL_INTERVAL_MS` will be used.
    pub flush_interval_ms: Option<i64>,
}

/// Configuration for exporting OpenTelemetry traces.
///
/// - `endpoint`: The endpoint to which trace data will be exported. Expected format:
///   - For gRPC: `grpc://host:port`
///   - For HTTP: `http://host:port` or `https://host:port`
///   - For file exporter: `file:///absolute/path/to/folder/file.json`
/// - `sample_percentage`: The percentage of requests to sample and create a span for, used to measure command duration. If `None`, a default value DEFAULT_TRACE_SAMPLE_PERCENTAGE will be used.
///   Note: There is a tradeoff between sampling percentage and performance. Higher sampling percentages will provide more detailed telemetry data but will impact performance.
///   It is recommended to keep this number low (1-5%) in production environments unless you have specific needs for higher sampling rates.
#[napi(object)]
#[derive(Clone)]
pub struct OpenTelemetryTracesConfig {
    /// The endpoint to which trace data will be exported.
    pub endpoint: String,
    /// The percentage of requests to sample and create a span for, used to measure command duration. If `None`, a default value DEFAULT_TRACE_SAMPLE_PERCENTAGE will be used.
    /// Note: There is a tradeoff between sampling percentage and performance. Higher sampling percentages will provide more detailed telemetry data but will impact performance.
    /// It is recommended to keep this number low (1-5%) in production environments unless you have specific needs for higher sampling rates.
    pub sample_percentage: Option<u32>,
}

/// Configuration for exporting OpenTelemetry metrics.
///
/// - `endpoint`: The endpoint to which metrics data will be exported. Expected format:
///   - For gRPC: `grpc://host:port`
///   - For HTTP: `http://host:port` or `https://host:port`
///   - For file exporter: `file:///absolute/path/to/folder/file.json`
#[napi(object)]
#[derive(Clone)]
pub struct OpenTelemetryMetricsConfig {
    /// The endpoint to which metrics data will be exported.
    pub endpoint: String,
}

fn to_js_error(err: impl std::error::Error) -> Error {
    napi::Error::new(Status::Unknown, err.to_string())
}

fn to_js_result<T, E: std::error::Error>(result: std::result::Result<T, E>) -> Result<T> {
    result.map_err(to_js_error)
}

#[napi(js_name = "InitOpenTelemetry")]
pub fn init_open_telemetry(open_telemetry_config: OpenTelemetryConfig) -> Result<()> {
    // At least one of traces or metrics must be provided
    if open_telemetry_config.traces.is_none() && open_telemetry_config.metrics.is_none() {
        return Err(napi::Error::new(
            Status::InvalidArg,
            "At least one of traces or metrics must be provided for OpenTelemetry configuration."
                .to_owned(),
        ));
    }

    let mut config = GlideOpenTelemetryConfigBuilder::default();
    // initilaize open telemetry traces exporter
    if let Some(traces) = open_telemetry_config.traces {
        config = config.with_trace_exporter(
            GlideOpenTelemetrySignalsExporter::from_str(&traces.endpoint)
                .map_err(ConnectionError::IoError)
                .map_err(|e| napi::Error::new(Status::Unknown, format!("{e}")))?,
            traces.sample_percentage,
        );
    }

    // initialize open telemetry metrics exporter
    if let Some(metrics) = open_telemetry_config.metrics {
        config = config.with_metrics_exporter(
            GlideOpenTelemetrySignalsExporter::from_str(&metrics.endpoint)
                .map_err(ConnectionError::IoError)
                .map_err(|e| napi::Error::new(Status::Unknown, format!("{e}")))?,
        );
    }

    let flush_interval_ms = open_telemetry_config
        .flush_interval_ms
        .unwrap_or(DEFAULT_FLUSH_SIGNAL_INTERVAL_MS as i64);

    if flush_interval_ms <= 0 {
        return Err(napi::Error::new(
            Status::Unknown,
            format!(
                "InvalidInput: flushIntervalMs must be a positive integer (got: {flush_interval_ms})"
            ),
        ));
    }

    config = config.with_flush_interval(std::time::Duration::from_millis(flush_interval_ms as u64));

    let glide_rt = match get_or_init_runtime() {
        Ok(handle) => handle,
        Err(err) => {
            return Err(napi::Error::new(
                Status::Unknown,
                format!("Failed to get or init runtime: {err}"),
            ));
        }
    };

    glide_rt.runtime.block_on(async {
        if let Err(e) = GlideOpenTelemetry::initialise(config.build()) {
            log(
                Level::Error,
                "OpenTelemetry".to_string(),
                format!("Failed to initialize OpenTelemetry: {e}"),
            );
            return Err(napi::Error::new(
                Status::Unknown,
                format!("Failed to initialize OpenTelemetry: {e}"),
            ));
        }
        Ok(())
    })?;

    Ok(())
}

impl From<logger_core::Level> for Level {
    fn from(level: logger_core::Level) -> Self {
        match level {
            logger_core::Level::Error => Level::Error,
            logger_core::Level::Warn => Level::Warn,
            logger_core::Level::Info => Level::Info,
            logger_core::Level::Debug => Level::Debug,
            logger_core::Level::Trace => Level::Trace,
            logger_core::Level::Off => Level::Off,
        }
    }
}

impl From<Level> for logger_core::Level {
    fn from(level: Level) -> logger_core::Level {
        match level {
            Level::Error => logger_core::Level::Error,
            Level::Warn => logger_core::Level::Warn,
            Level::Info => logger_core::Level::Info,
            Level::Debug => logger_core::Level::Debug,
            Level::Trace => logger_core::Level::Trace,
            Level::Off => logger_core::Level::Off,
        }
    }
}

#[napi]
pub fn log(log_level: Level, log_identifier: String, message: String) {
    logger_core::log(log_level.into(), log_identifier, message);
}

#[napi(js_name = "InitInternalLogger")]
pub fn init(level: Option<Level>, file_name: Option<String>) -> Level {
    let logger_level = logger_core::init(level.map(|level| level.into()), file_name.as_deref());
    logger_level.into()
}

fn resp_value_to_js<'a>(val: Value, js_env: &'a Env, string_decoder: bool) -> Result<Unknown<'a>> {
    match val {
        Value::Nil => Null.into_unknown(js_env),
        Value::SimpleString(str) => {
            if string_decoder {
                js_env
                    .create_string_from_std(str)
                    .and_then(|val| val.into_unknown(js_env))
            } else {
                BufferSlice::from_data(js_env, str.into_bytes())?.into_unknown(js_env)
            }
        }
        Value::Okay => js_env
            .create_string("OK")
            .and_then(|val| val.into_unknown(js_env)),
        Value::Int(num) => js_env
            .create_int64(num)
            .and_then(|val| val.into_unknown(js_env)),
        Value::BulkString(data) => {
            if string_decoder {
                let str = to_js_result(std::str::from_utf8(data.as_ref()))?;
                js_env
                    .create_string(str)
                    .and_then(|val| val.into_unknown(js_env))
            } else {
                BufferSlice::from_data(js_env, data)?.into_unknown(js_env)
            }
        }
        Value::Array(array) => {
            let mut js_array_view = js_env.create_array(array.len() as u32)?;
            for (index, item) in array.into_iter().enumerate() {
                js_array_view.set_element(
                    index as u32,
                    resp_value_to_js(item, js_env, string_decoder)?,
                )?;
            }
            js_array_view.into_unknown(js_env)
        }
        Value::Map(map) => {
            // Convert map to array of key-value pairs instead of a `Record` (object),
            // because `Record` does not support `GlideString` as a key.
            // The result is in format `GlideRecord<T>`.
            let mut js_array = js_env.create_array(map.len() as u32)?;
            for (idx, (key, value)) in (0_u32..).zip(map) {
                let mut obj = Object::new(js_env)?;
                obj.set_named_property("key", resp_value_to_js(key, js_env, string_decoder)?)?;
                obj.set_named_property("value", resp_value_to_js(value, js_env, string_decoder)?)?;
                js_array.set_element(idx, obj)?;
            }
            js_array.into_unknown(js_env)
        }
        Value::Double(float) => js_env
            .create_double(float)
            .and_then(|val| val.into_unknown(js_env)),
        Value::Boolean(bool) => bool.into_unknown(js_env),
        // format is ignored, as per the RESP3 recommendations -
        // "Normal client libraries may ignore completely the difference between this"
        // "type and the String type, and return a string in both cases.""
        // https://github.com/redis/redis-specifications/blob/master/protocol/RESP3.md
        Value::VerbatimString { format: _, text } => {
            if string_decoder {
                js_env
                    .create_string_from_std(text)
                    .and_then(|val| val.into_unknown(js_env))
            } else {
                // VerbatimString is binary safe -> convert it into such
                BufferSlice::from_data(js_env, text.into_bytes())?.into_unknown(js_env)
            }
        }
        Value::BigNumber(num) => {
            let bigint = BigInt {
                sign_bit: num.is_negative(),
                words: num.iter_u64_digits().collect(),
            };
            bigint.into_unknown(js_env)
        }
        Value::Set(array) => {
            // Returns Array instead of JS Set for consistency with other GLIDE language bindings
            // and because RESP3 Set elements can contain non-hashable types (nested arrays/maps)
            let mut js_array_view = js_env.create_array(array.len() as u32)?;
            for (index, item) in array.into_iter().enumerate() {
                js_array_view.set_element(
                    index as u32,
                    resp_value_to_js(item, js_env, string_decoder)?,
                )?;
            }
            js_array_view.into_unknown(js_env)
        }
        Value::Attribute { data, attributes } => {
            let mut obj = Object::new(js_env)?;
            let value = resp_value_to_js(*data, js_env, string_decoder)?;
            obj.set_named_property("value", value)?;

            let value = resp_value_to_js(Value::Map(attributes), js_env, string_decoder)?;
            obj.set_named_property("attributes", value)?;

            obj.into_unknown(js_env)
        }
        Value::Push { kind, data } => {
            let mut obj = Object::new(js_env)?;
            obj.set_named_property("kind", format!("{kind:?}"))?;
            let js_array_view = data
                .into_iter()
                .map(|item| resp_value_to_js(item, js_env, string_decoder))
                .collect::<Result<Vec<_>, _>>()?;
            obj.set_named_property("values", js_array_view)?;
            obj.into_unknown(js_env)
        }
        Value::ServerError(error) => {
            let err_msg = error_message(&error.into());
            let err = Error::new(Status::Ok, err_msg);
            let mut js_error = js_env.create_error(err)?;
            js_error.set_named_property("name", "RequestError")?;
            js_error.into_unknown(js_env)
        }
    }
}

/// Dereference a response pointer passed as a single JS number.
///
/// napi-rs marshals `i64` via `napi_get_value_int64`, which preserves all
/// bits for values within the safe integer range. User-space heap addresses
/// on current 64-bit platforms (48-bit on arm64 macOS, 47-bit on x86-64
/// Linux) are well within this range. Using a single integer avoids the
/// high/low u32 split and eliminates the class of bugs where the caller
/// passes the wrong high bits.
#[napi(
    ts_return_type = "null | string | Uint8Array | number | {} | Boolean | BigInt | Set<any> | any[] | Buffer"
)]
pub fn value_from_pointer<'a>(
    js_env: &'a Env,
    pointer_number: i64,
    string_decoder: bool,
) -> Result<Unknown<'a>> {
    if pointer_number == 0 {
        return Err(napi::Error::new(
            Status::InvalidArg,
            "Null pointer passed to value_from_pointer",
        ));
    }
    let value = unsafe { Box::from_raw(pointer_number as *mut Value) };
    resp_value_to_js(*value, js_env, string_decoder)
}

#[napi(
    ts_return_type = "null | string | Uint8Array | number | {} | Boolean | BigInt | Set<any> | any[] | Buffer"
)]
pub fn value_from_split_pointer<'a>(
    js_env: &'a Env,
    high_bits: u32,
    low_bits: u32,
    string_decoder: bool,
) -> Result<Unknown<'a>> {
    let mut bytes = [0_u8; 8];
    (&mut bytes[..4])
        .write_u32::<LittleEndian>(low_bits)
        .unwrap();
    (&mut bytes[4..])
        .write_u32::<LittleEndian>(high_bits)
        .unwrap();
    let pointer = u64::from_le_bytes(bytes);
    if pointer == 0 {
        return Err(napi::Error::new(
            Status::InvalidArg,
            "Null pointer passed to value_from_split_pointer",
        ));
    }
    let value = unsafe { Box::from_raw(pointer as *mut Value) };
    resp_value_to_js(*value, js_env, string_decoder)
}

// Split a pointer into [low, high] u32 pair for testing utilities.
fn split_pointer<T>(pointer: *mut T) -> [u32; 2] {
    let pointer = pointer as usize;
    let bytes = usize::to_le_bytes(pointer);
    let [lower, higher] = unsafe { std::mem::transmute::<[u8; 8], [u32; 2]>(bytes) };
    [lower, higher]
}

#[napi(ts_return_type = "[number, number]")]
/// @internal @test
/// This function is for tests that require a value allocated on the heap.
/// Should NOT be used in production.
#[cfg(feature = "testing_utilities")]
pub fn create_leaked_string(message: String) -> [u32; 2] {
    let value = Value::SimpleString(message);
    let pointer = from_mut(Box::leak(Box::new(value)));
    split_pointer(pointer)
}

#[napi(ts_return_type = "[number, number]")]
/// @internal @test
/// This function is for tests that require a value allocated on the heap.
/// Should NOT be used in production.
pub fn create_leaked_string_vec(message: Vec<Uint8Array>) -> [u32; 2] {
    // Convert the string vec -> Bytes vector
    let bytes_vec: Vec<Bytes> = message.iter().map(|v| Bytes::from(v.to_vec())).collect();
    let pointer = from_mut(Box::leak(Box::new(bytes_vec)));
    split_pointer(pointer)
}

/// Free a previously leaked string vec by its split pointer.
/// Called from JS to reclaim memory when a leaked pointer will not be
/// passed to send_command/send_batch/invoke_script (e.g., early error paths).
#[napi]
pub fn free_leaked_string_vec(high_bits: u32, low_bits: u32) {
    let pointer = (low_bits as u64) | ((high_bits as u64) << 32);
    if pointer != 0 {
        unsafe { drop(Box::from_raw(pointer as *mut Vec<Bytes>)) };
    }
}

#[napi(ts_return_type = "[number, number]")]
/// @internal @test
/// This function is for tests that require a value allocated on the heap.
/// Should NOT be used in production.
#[cfg(feature = "testing_utilities")]
pub fn create_leaked_map(map: HashMap<String, String>) -> [u32; 2] {
    let pointer = from_mut(Box::leak(Box::new(Value::Map(
        map.into_iter()
            .map(|(key, value)| (Value::SimpleString(key), Value::SimpleString(value)))
            .collect(),
    ))));
    split_pointer(pointer)
}

#[napi(ts_return_type = "[number, number]")]
/// @internal @test
/// This function is for tests that require a value allocated on the heap.
/// Should NOT be used in production.
#[cfg(feature = "testing_utilities")]
pub fn create_leaked_array(array: Vec<String>) -> [u32; 2] {
    let pointer = from_mut(Box::leak(Box::new(Value::Array(
        array.into_iter().map(Value::SimpleString).collect(),
    ))));
    split_pointer(pointer)
}

#[napi(ts_return_type = "[number, number]")]
/// @internal @test
/// This function is for tests that require a value allocated on the heap.
/// Should NOT be used in production.
#[cfg(feature = "testing_utilities")]
pub fn create_leaked_attribute(message: String, attribute: HashMap<String, String>) -> [u32; 2] {
    let pointer = from_mut(Box::leak(Box::new(Value::Attribute {
        data: Box::new(Value::SimpleString(message)),
        attributes: attribute
            .into_iter()
            .map(|(key, value)| (Value::SimpleString(key), Value::SimpleString(value)))
            .collect(),
    })));
    split_pointer(pointer)
}

#[napi(ts_return_type = "[number, number]")]
/// @internal @test
/// This function is for tests that require a value allocated on the heap.
/// Should NOT be used in production.
#[cfg(feature = "testing_utilities")]
pub fn create_leaked_bigint(big_int: BigInt) -> [u32; 2] {
    let pointer = from_mut(Box::leak(Box::new(Value::BigNumber(
        num_bigint::BigInt::new(
            if big_int.sign_bit {
                num_bigint::Sign::Minus
            } else {
                num_bigint::Sign::Plus
            },
            big_int
                .words
                .into_iter()
                .flat_map(|word| {
                    let bytes = u64::to_le_bytes(word);
                    unsafe { std::mem::transmute::<[u8; 8], [u32; 2]>(bytes) }
                })
                .collect(),
        ),
    ))));
    split_pointer(pointer)
}

#[napi(ts_return_type = "[number, number]")]
/// @internal @test
/// This function is for tests that require a value allocated on the heap.
/// Should NOT be used in production.
#[cfg(feature = "testing_utilities")]
pub fn create_leaked_double(float: f64) -> [u32; 2] {
    let pointer = from_mut(Box::leak(Box::new(Value::Double(float))));
    split_pointer(pointer)
}

/// Creates an open telemetry span with the given name and returns a pointer to the span
#[napi(ts_return_type = "[number, number]")]
pub fn create_leaked_otel_span(name: String) -> [u32; 2] {
    let span = GlideOpenTelemetry::new_span(&name);
    let s = Arc::into_raw(Arc::new(span)) as *mut GlideSpan;
    split_pointer(s)
}

/// Creates an open telemetry span with the given name as a child of a remote span context.
/// Falls back to creating a standalone span if the trace context is invalid.
#[napi(ts_return_type = "[number, number]")]
pub fn create_otel_span_with_trace_context(
    name: String,
    trace_id: String,
    span_id: String,
    trace_flags: u8,
    trace_state: Option<String>,
) -> [u32; 2] {
    let span = match GlideSpan::new_with_remote_context(
        &name,
        &trace_id,
        &span_id,
        trace_flags,
        trace_state.as_deref(),
    ) {
        Ok(s) => s,
        Err(e) => {
            log(
                Level::Warn,
                "OpenTelemetry".to_string(),
                format!(
                    "Failed to create span with remote context, falling back to standalone span: {e}"
                ),
            );
            GlideOpenTelemetry::new_span(&name)
        }
    };
    let s = Arc::into_raw(Arc::new(span)) as *mut GlideSpan;
    split_pointer(s)
}

#[napi]
pub fn drop_otel_span(span_ptr: BigInt) {
    let (is_negative, span_ptr, lossless) = span_ptr.get_u64();
    let error_msg = if is_negative {
        "Received a negative pointer value."
    } else if !lossless {
        "Some data was lost in the conversion to u64."
    } else if span_ptr == 0 {
        "Received a zero pointer value."
    } else {
        unsafe { Arc::from_raw(span_ptr as *const GlideSpan) };
        return;
    };

    log(
        Level::Error,
        "OpenTelemetry".to_string(),
        format!("Failed to drop span. {error_msg}"),
    );
}

#[napi]
/// A wrapper for a script object. As long as this object is alive, the script's code is saved in memory, and can be resent to the server.
///
/// **IMPORTANT**: Script objects are NOT automatically garbage collected. You are responsible for calling `release()`
/// on every Script object when you're done with it to prevent memory leaks. Failure to do so will result in memory leaks.
struct Script {
    hash: String,
}

#[napi]
impl Script {
    /// Construct with the script's code.
    #[napi(constructor)]
    #[allow(dead_code)]
    pub fn new(code: Either<String, Uint8Array>) -> Self {
        let hash = match code {
            Either::A(code_str) => glide_core::scripts_container::add_script(code_str.as_bytes()),
            Either::B(code_bytes) => glide_core::scripts_container::add_script(&code_bytes),
        };
        Self { hash }
    }

    /// Returns the hash of the script.
    #[napi]
    #[allow(dead_code)]
    pub fn get_hash(&self) -> String {
        self.hash.clone()
    }

    /// Internal release logic used both by Drop and napi-exposed `release()`.
    fn release_internal(&self) {
        glide_core::scripts_container::remove_script(&self.hash);
    }

    /// Decrements the script's reference count in the local container.
    /// Removes the script when the count reaches zero.
    ///
    /// You need to call this method when you're done with the Script object. Script objects are NOT
    /// automatically garbage collected, and failure to call release() will result in memory leaks.
    #[napi]
    #[allow(dead_code)]
    pub fn release(&self) {
        self.release_internal();
    }
}

/// This struct is used to keep track of the cursor of a cluster scan.
/// We want to avoid passing the cursor between layers of the application,
/// So we keep the state in the container and only pass the id of the cursor.
/// The cursor is stored in the container and can be retrieved using the id.
/// The cursor is removed from the container when the object is deleted (dropped).
/// To create a cursor:
/// ```typescript
/// // For a new cursor
/// let cursor = new ClusterScanCursor();
/// // Using an existing id
/// let cursor = new ClusterScanCursor("cursor_id");
/// ```
/// To get the cursor id:
/// ```typescript
/// let cursorId = cursor.getCursor();
/// ```
/// To check if the scan is finished:
/// ```typescript
/// let isFinished = cursor.isFinished(); // true if the scan is finished
/// ```
#[napi]
#[derive(Default)]
pub struct ClusterScanCursor {
    cursor: String,
}

#[napi]
impl ClusterScanCursor {
    #[napi(constructor)]
    #[allow(dead_code)]
    pub fn new(new_cursor: Option<String>) -> Self {
        match new_cursor {
            Some(cursor) => ClusterScanCursor { cursor },
            None => ClusterScanCursor::default(),
        }
    }

    /// Returns the cursor id.
    #[napi]
    #[allow(dead_code)]
    pub fn get_cursor(&self) -> String {
        self.cursor.clone()
    }

    #[napi]
    #[allow(dead_code)]
    /// Returns true if the scan is finished.
    pub fn is_finished(&self) -> bool {
        self.cursor.eq(FINISHED_SCAN_CURSOR)
    }
}

impl Drop for ClusterScanCursor {
    fn drop(&mut self) {
        glide_core::cluster_scan_container::remove_scan_state_cursor(self.cursor.clone());
    }
}

#[napi]
pub fn get_statistics<'a>(env: &'a Env) -> Result<Object<'a>> {
    let total_connections = Telemetry::total_connections().to_string();
    let total_clients = Telemetry::total_clients().to_string();
    let total_values_compressed = Telemetry::total_values_compressed().to_string();
    let total_values_decompressed = Telemetry::total_values_decompressed().to_string();
    let total_original_bytes = Telemetry::total_original_bytes().to_string();
    let total_bytes_compressed = Telemetry::total_bytes_compressed().to_string();
    let total_bytes_decompressed = Telemetry::total_bytes_decompressed().to_string();
    let compression_skipped_count = Telemetry::compression_skipped_count().to_string();
    let subscription_out_of_sync_count = Telemetry::subscription_out_of_sync_count().to_string();
    let subscription_last_sync_timestamp =
        Telemetry::subscription_last_sync_timestamp().to_string();

    let mut stats = Object::new(env)?;
    stats.set_named_property("total_connections", total_connections)?;
    stats.set_named_property("total_clients", total_clients)?;
    stats.set_named_property("total_values_compressed", total_values_compressed)?;
    stats.set_named_property("total_values_decompressed", total_values_decompressed)?;
    stats.set_named_property("total_original_bytes", total_original_bytes)?;
    stats.set_named_property("total_bytes_compressed", total_bytes_compressed)?;
    stats.set_named_property("total_bytes_decompressed", total_bytes_decompressed)?;
    stats.set_named_property("compression_skipped_count", compression_skipped_count)?;
    stats.set_named_property(
        "subscription_out_of_sync_count",
        subscription_out_of_sync_count,
    )?;
    stats.set_named_property(
        "subscription_last_sync_timestamp",
        subscription_last_sync_timestamp,
    )?;

    Ok(stats)
}

/// A Node.js address resolver wrapper that implements the `AddressResolver` trait.
/// It holds a `ThreadsafeFunction` reference to a wrapper JavaScript function that
/// calls the user's resolver and sends the result through a channel.
type AddressResolverTsfn =
    ThreadsafeFunction<ResolveRequest, (String, u32), FnArgs<(String, u32)>, Status, false, true>;

struct NodeAddressResolver {
    tsfn: AddressResolverTsfn,
}

/// Internal request type passed to the JS address resolver callback.
struct ResolveRequest {
    host: String,
    port: u32,
}

impl std::fmt::Debug for NodeAddressResolver {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "NodeAddressResolver {{ callback: <JS function> }}")
    }
}

// SAFETY: ThreadsafeFunction is designed to be called from any thread.
unsafe impl Send for NodeAddressResolver {}
unsafe impl Sync for NodeAddressResolver {}

impl redis::AddressResolver for NodeAddressResolver {
    fn resolve(&self, host: &str, port: u16) -> (String, u16) {
        let (tx, rx) = std::sync::mpsc::sync_channel(1);
        let original_host = host.to_string();
        let fallback_host = original_host.clone();

        let request = ResolveRequest {
            host: original_host.clone(),
            port: port as u32,
        };

        // Schedule the JS callback on the main thread and block until it completes
        let status = self.tsfn.call_with_return_value(
            request,
            ThreadsafeFunctionCallMode::Blocking,
            move |resolved, _env| {
                let resolved = resolved
                    .and_then(|(resolved_host, resolved_port)| {
                        u16::try_from(resolved_port)
                            .map(|port| (resolved_host, port))
                            .map_err(|_| {
                                Error::new(
                                    Status::InvalidArg,
                                    format!(
                                        "Address resolver returned port outside u16 range: {resolved_port}"
                                    ),
                                )
                            })
                    })
                    .unwrap_or_else(|e| {
                        log_warn_lazy!(
                            "address_resolver",
                            format!(
                                "Address resolver failed, falling back to original address: {e}"
                            )
                        );
                        (fallback_host, port)
                    });

                let _ = tx.send(resolved);
                Ok(())
            },
        );

        if status != Status::Ok {
            log_warn_lazy!(
                "address_resolver",
                format!("Address resolver failed, falling back to original address: {status:?}")
            );
            return (original_host, port);
        }

        // Wait for the JS callback to send back the resolved address
        rx.recv().unwrap_or_else(|e| {
            log_warn_lazy!(
                "address_resolver",
                format!("Address resolver failed, falling back to original address: {e}")
            );
            (host.to_string(), port)
        })
    }
}

/// Register a JavaScript address resolver callback in the global registry.
/// Returns the registry key (UUID) that must be set in the ConnectionRequest's
/// `address_resolver_key` field so the socket listener can look it up.
///
/// The JS callback signature is: `(host: string, port: number) => [string, number]`
#[napi(js_name = "registerAddressResolver")]
pub fn register_address_resolver(
    #[napi(ts_arg_type = "(host: string, port: number) => [string, number]")] callback: Function<
        '_,
        FnArgs<(String, u32)>,
        (String, u32),
    >,
) -> Result<String> {
    let tsfn = callback
        .build_threadsafe_function::<ResolveRequest>()
        .callee_handled::<false>()
        .weak::<true>()
        .build_callback(|ctx| Ok(FnArgs::from((ctx.value.host, ctx.value.port))))?;

    let key = uuid::Uuid::new_v4().to_string();
    let resolver = Arc::new(NodeAddressResolver { tsfn });
    glide_core::address_resolver_registry::register(key.clone(), resolver);
    Ok(key)
}

/// Remove an address resolver from the global registry by key.
#[napi(js_name = "removeAddressResolver")]
pub fn remove_address_resolver(key: String) {
    glide_core::address_resolver_registry::remove(&key);
}

static NEXT_MONITOR_HANDLE: AtomicU64 = AtomicU64::new(1);

type MonitorCallbackArgs = (f64, i64, String, String, Vec<String>);
type MonitorCallback<'a> = Function<'a, FnArgs<MonitorCallbackArgs>, ()>;

fn monitor_store() -> &'static Mutex<StdHashMap<u64, MonitorClient>> {
    static STORE: OnceLock<Mutex<StdHashMap<u64, MonitorClient>>> = OnceLock::new();
    STORE.get_or_init(|| Mutex::new(StdHashMap::new()))
}

#[napi(js_name = "createMonitorClient", ts_return_type = "Promise<number>")]
pub fn create_monitor_client<'a>(
    env: &'a Env,
    connection_request_bytes: Uint8Array,
    #[napi(
        ts_arg_type = "(timestamp: number, db: number, clientAddr: string, command: string, args: string[]) => void"
    )]
    callback: MonitorCallback<'_>,
) -> Result<Object<'a>> {
    let (deferred, promise) = env.create_deferred()?;
    let conn_req =
        connection_request::ConnectionRequest::parse_from_bytes(&connection_request_bytes)
            .map_err(|e| napi::Error::new(Status::InvalidArg, e.to_string()))?;
    let proto_addr = conn_req
        .addresses
        .first()
        .ok_or_else(|| napi::Error::new(Status::InvalidArg, "No addresses provided"))?;
    let address = NodeAddress {
        host: proto_addr.host.to_string(),
        port: proto_addr.port as u16,
    };
    let tls_mode = match conn_req.tls_mode.enum_value_or_default() {
        connection_request::TlsMode::NoTls => TlsMode::NoTls,
        connection_request::TlsMode::SecureTls => TlsMode::SecureTls,
        connection_request::TlsMode::InsecureTls => TlsMode::InsecureTls,
    };
    let redis_conn_info = redis::RedisConnectionInfo {
        db: conn_req.database_id as i64,
        username: conn_req.authentication_info.as_ref().and_then(|a| {
            let u = a.username.to_string();
            if u.is_empty() { None } else { Some(u) }
        }),
        password: conn_req.authentication_info.as_ref().and_then(|a| {
            let p = a.password.to_string();
            if p.is_empty() { None } else { Some(p) }
        }),
        // MONITOR streams plain-text inline responses, which are incompatible with RESP3 push
        // messages. RESP2 must always be used for monitor connections regardless of user config.
        protocol: redis::ProtocolVersion::RESP2,
        lib_name: if conn_req.lib_name.is_empty() {
            None
        } else {
            Some(conn_req.lib_name.to_string())
        },
        ..Default::default()
    };
    let _client_name = conn_req.client_name.to_string(); // TODO: pass to MonitorClient::new once its signature supports it
    // Weak mode calls napi_unref_threadsafe_function, so monitor callbacks do not keep
    // the Node.js event loop alive while the monitor client is open.
    let tsfn = callback
        .build_threadsafe_function::<MonitorLine>()
        .callee_handled::<false>()
        .weak::<true>()
        .build_callback(|ctx| {
            let line = ctx.value;
            Ok(FnArgs::from((
                line.timestamp,
                line.db,
                line.client_addr,
                line.command,
                line.args,
            )))
        })?;
    let on_line: MonitorLineCallback = Arc::new(move |line: MonitorLine| {
        tsfn.call(
            line,
            napi::threadsafe_function::ThreadsafeFunctionCallMode::NonBlocking,
        );
    });
    let glide_rt = get_or_init_runtime().map_err(|e| napi::Error::new(Status::Unknown, e))?;
    glide_rt.runtime.spawn(async move {
        match MonitorClient::new(&address, redis_conn_info, tls_mode, on_line).await {
            Ok(client) => {
                let handle_id = NEXT_MONITOR_HANDLE.fetch_add(1, Ordering::Relaxed);
                monitor_store().lock().unwrap().insert(handle_id, client);
                deferred.resolve(move |_| Ok(handle_id as i64));
            }
            Err(e) => deferred.reject(napi::Error::new(Status::Unknown, e.to_string())),
        }
    });
    Ok(promise)
}

#[napi(js_name = "closeMonitorClient", ts_return_type = "Promise<void>")]
pub fn close_monitor_client(env: &Env, handle_id: i64) -> Result<Object<'_>> {
    let (deferred, promise) = env.create_deferred()?;
    let client = monitor_store().lock().unwrap().remove(&(handle_id as u64));
    let glide_rt = get_or_init_runtime().map_err(|e| napi::Error::new(Status::Unknown, e))?;
    glide_rt.runtime.spawn(async move {
        if let Some(c) = client {
            c.stop_async().await;
        }
        deferred.resolve(|_| Ok(()));
    });
    Ok(promise)
}
