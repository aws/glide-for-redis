// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

pub mod circuit_breaker;
mod types;

use crate::cluster_scan_container::insert_cluster_scan_cursor;
use crate::compression::CompressionBackendType;
use crate::compression::lz4_backend::Lz4Backend;
use crate::compression::zstd_backend::ZstdBackend;
use crate::compression::{CompressionConfig, CompressionManager};
use crate::scripts_container::get_script;
use futures::FutureExt;
use logger_core::{log_debug, log_error, log_info, log_warn, log_warn_rate_limited};
use once_cell::sync::OnceCell;
use redis::aio::ConnectionLike;
use redis::cache::{get_or_create_cache, glide_cache::GlideCache};
use redis::cluster_async::ClusterConnection;
use redis::cluster_routing::{
    MultipleNodeRoutingInfo, ResponsePolicy, Routable, RoutingInfo, SingleNodeRoutingInfo,
};
use redis::cluster_slotmap::ReadFromReplicaStrategy;
use redis::{
    AddressResolver, ClusterScanArgs, Cmd, ErrorKind, FromRedisValue, PipelineRetryStrategy,
    PushInfo, RedisError, RedisResult, RetryStrategy, ScanStateRC, Value,
};
use regex::Regex;
pub use standalone_client::StandaloneClient;
use std::io;
use std::sync::atomic::{AtomicIsize, AtomicU32, AtomicU64, Ordering};
use std::sync::{Arc, LazyLock};
use std::thread;
use std::thread::JoinHandle;
use std::time::{Duration, Instant};
use tokio::runtime::{Builder, Handle};
pub use types::*;

use self::value_conversion::{convert_to_expected_type, expected_type_for_cmd, get_value_type};
mod reconnecting_connection;
pub use reconnecting_connection::IAMTokenHandle;
pub mod monitor_client;
pub use monitor_client::{MonitorClient, MonitorLine, MonitorLineCallback};
mod standalone_client;
mod value_conversion;
use crate::pubsub::{PubSubSynchronizer, create_pubsub_synchronizer};
use crate::request_type::RequestType;
use redis::InfoDict;
use std::future::Future;
use std::pin::Pin;
use telemetrylib::GlideOpenTelemetry;
use tokio::sync::{Notify, RwLock, mpsc, oneshot};
use versions::Versioning;

pub const HEARTBEAT_SLEEP_DURATION: Duration = Duration::from_secs(1);
pub const DEFAULT_RETRIES: u32 = 3;
/// Note: If you change the default value, make sure to change the documentation in *all* wrappers.
pub const DEFAULT_RESPONSE_TIMEOUT: Duration = Duration::from_millis(250);
pub const DEFAULT_PERIODIC_TOPOLOGY_CHECKS_INTERVAL: Duration = Duration::from_secs(60);
pub const FINISHED_SCAN_CURSOR: &str = "finished";

/// The value of 1000 for the maximum number of inflight requests is determined based on Little's Law in queuing theory:
///
/// Expected maximum request rate: 50,000 requests/second
/// Expected response time: 1 millisecond
///
/// According to Little's Law, the maximum number of inflight requests required to fully utilize the maximum request rate is:
///   (50,000 requests/second) × (1 millisecond / 1000 milliseconds) = 50 requests
///
/// The value of 1000 provides a buffer for bursts while still allowing full utilization of the maximum request rate.
pub const DEFAULT_MAX_INFLIGHT_REQUESTS: u32 = 1000;

/// Default recovery queue size: maximum requests buffered during cluster reconnect.
/// Buffered requests are retried transparently after reconnection. Requests beyond
/// this limit are failed immediately to provide bounded memory usage.
/// See `recovery_requests_queue_size` in `ConnectionRequest`.
pub const DEFAULT_RECOVERY_REQUESTS_QUEUE_SIZE: u32 = 1000;

/// The connection check interval is currently not exposed to the user via ConnectionRequest,
/// as improper configuration could negatively impact performance or pub/sub resiliency.
/// A 3-second interval provides a reasonable balance between connection validation
/// and performance overhead.
pub const CONNECTION_CHECKS_INTERVAL: Duration = Duration::from_secs(3);

/// Extract RequestType from a Redis command for decompression processing
fn extract_request_type_from_cmd(cmd: &Cmd) -> Option<RequestType> {
    // Get the command name (first argument)
    let command_name = cmd.command()?;
    let command_str = String::from_utf8_lossy(&command_name).to_uppercase();

    // Map command names to RequestType for decompression
    // Only read commands that return values needing decompression are included
    match command_str.as_str() {
        "GET" => Some(RequestType::Get),
        "MGET" => Some(RequestType::MGet),
        "GETEX" => Some(RequestType::GetEx),
        "GETDEL" => Some(RequestType::GetDel),
        "GETSET" => Some(RequestType::GetSet),
        "SET" => {
            // SET with GET option returns the old value, which needs decompression
            // Check if the command has the GET option by looking for "GET" in the arguments
            // SET key value [NX | XX] [GET] [EX seconds | PX milliseconds | EXAT unix-time | PXAT unix-time | KEEPTTL]
            let has_get_option = cmd.args_iter().skip(3).any(|arg| {
                if let redis::Arg::Simple(bytes) = arg {
                    bytes.eq_ignore_ascii_case(b"GET")
                } else {
                    false
                }
            });
            if has_get_option {
                // Treat SET with GET option like GETSET for decompression purposes
                Some(RequestType::GetSet)
            } else {
                None
            }
        }
        _ => None, // Unknown command or write command, no decompression needed
    }
}

/// A static Glide runtime instance
static RUNTIME: OnceCell<GlideRt> = OnceCell::new();

pub struct GlideRt {
    pub runtime: Handle,
    pub(crate) thread: Option<JoinHandle<()>>,
    shutdown_notifier: Arc<Notify>,
}

/// Initializes a single-threaded Tokio runtime in a dedicated thread (if not already initialized)
/// and returns a static reference to the `GlideRt` wrapper, which holds the runtime handle and a shutdown notifier.
/// The runtime remains active indefinitely until a shutdown is triggered via the notifier, allowing tasks to be spawned
/// throughout the lifetime of the application.
pub fn get_or_init_runtime() -> Result<&'static GlideRt, String> {
    RUNTIME.get_or_try_init(|| {
        let notify = Arc::new(Notify::new());
        let notify_thread = notify.clone();

        let (tx, rx) = oneshot::channel();

        let thread_handle = thread::Builder::new()
            .name("glide-runtime-thread".into())
            .spawn(move || {
                match Builder::new_current_thread().enable_all().build() {
                    Ok(runtime) => {
                        let _ = tx.send(Ok(runtime.handle().clone()));
                        // Keep runtime alive until shutdown is signaled
                        runtime.block_on(notify_thread.notified());
                    }
                    Err(err) => {
                        let _ = tx.send(Err(format!("Failed to create runtime: {err}")));
                    }
                }
            })
            .map_err(|_| "Failed to spawn runtime thread".to_string())?;

        let runtime_handle = rx
            .blocking_recv()
            .map_err(|err| format!("Failed to receive runtime handle: {err:?}"))??;

        Ok(GlideRt {
            runtime: runtime_handle,
            thread: Some(thread_handle),
            shutdown_notifier: notify,
        })
    })
}

impl Drop for GlideRt {
    fn drop(&mut self) {
        if let Some(rt) = RUNTIME.get() {
            rt.shutdown_notifier.notify_one();
        }

        // Move the JoinHandle out of the Option and join it
        if let Some(handle) = self.thread.take() {
            handle.join().expect("GlideRt thread panicked");
        }
    }
}

pub(super) fn get_port(address: &NodeAddress) -> u16 {
    const DEFAULT_PORT: u16 = 6379;
    if address.port == 0 {
        DEFAULT_PORT
    } else {
        address.port
    }
}

/// Matches the optional-tag structure of a library name.
static LIB_NAME_PATTERN: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"\A[^()]+(?:\([^()]+\))?\z").expect("library name tag regex must be valid")
});

/// Validate whether a library name:
///   - is non-empty;
///   - contains only printable ASCII characters;
///   - matches the library name pattern with optional tag – 'name' or 'name(tag)'.
pub(crate) fn validate_effective_lib_name(lib_name: &str) -> Result<(), String> {
    if lib_name.is_empty() {
        return Err("library name must not be empty".to_string());
    }
    if !lib_name.bytes().all(|b| b.is_ascii_graphic()) {
        return Err("library name must contain only printable ASCII characters".to_string());
    }
    if !LIB_NAME_PATTERN.is_match(lib_name) {
        return Err("library name parentheses must form a non-empty trailing '(tag)'".to_string());
    }
    Ok(())
}

/// Validate whether a library version:
///   - is non-empty; and
///   - contains only printable ASCII characters.
pub(crate) fn validate_effective_lib_ver(lib_ver: &str) -> Result<(), String> {
    if lib_ver.is_empty() {
        return Err("library version must not be empty".to_string());
    }
    if !lib_ver.bytes().all(|b| b.is_ascii_graphic()) {
        return Err("library version must contain only printable ASCII characters".to_string());
    }
    Ok(())
}

/// Get Valkey connection info with IAM token integration
///
/// If IAM config + token manager exist, use the IAM token as the password; otherwise use the provided password.
///
/// `iam_token_manager: Option<&Arc<IAMTokenManager>>`
/// — `Option` because IAM is optional; `&Arc` gives shared, non-owning, cheap access to a shared manager (we only read a token).
pub async fn get_valkey_connection_info(
    connection_request: &ConnectionRequest,
    iam_token_manager: Option<&Arc<crate::iam::IAMTokenManager>>,
) -> redis::RedisConnectionInfo {
    let protocol = connection_request.protocol.unwrap_or_default();
    let db = connection_request.database_id;
    let client_name = connection_request.client_name.clone();
    let lib_name = connection_request.lib_name.clone();
    let lib_ver = connection_request.lib_ver.clone();
    let cache = connection_request
        .client_side_cache
        .clone()
        .map(|client_side_cache| {
            get_or_create_cache(
                &client_side_cache.cache_id,
                client_side_cache.max_cache_kb,
                client_side_cache.entry_ttl_ms,
                client_side_cache.eviction_policy,
                client_side_cache.enable_metrics,
            )
        });

    let server_assisted_cache = connection_request
        .client_side_cache
        .as_ref()
        .map(|c| c.server_assisted)
        .unwrap_or(false);

    match &connection_request.authentication_info {
        Some(info) => {
            // If we have IAM configuration and a token manager, use the IAM token as password
            if info.iam_config.is_some() && iam_token_manager.is_some() {
                let token = if let Some(manager) = iam_token_manager {
                    manager.get_token().await
                } else {
                    // Fallback to regular password if no token manager
                    info.password.clone().unwrap_or_default()
                };

                redis::RedisConnectionInfo {
                    db,
                    username: info.username.clone(),
                    password: Some(token),
                    protocol,
                    client_name,
                    lib_name,
                    lib_ver,
                    cache,
                    server_assisted_cache,
                }
            } else {
                // Regular password-based authentication
                redis::RedisConnectionInfo {
                    db,
                    username: info.username.clone(),
                    password: info.password.clone(),
                    protocol,
                    client_name,
                    lib_name,
                    lib_ver,
                    cache,
                    server_assisted_cache,
                }
            }
        }
        None => redis::RedisConnectionInfo {
            db,
            protocol,
            client_name,
            lib_name,
            lib_ver,
            cache,
            server_assisted_cache,
            ..Default::default()
        },
    }
}

use redis::{TlsCertificates, retrieve_tls_certificates};

// tls_params should be only set if tls_mode is SecureTls
// this should be validated before calling this function
pub(super) fn get_connection_info(
    address: &NodeAddress,
    tls_mode: TlsMode,
    redis_connection_info: redis::RedisConnectionInfo,
    tls_params: Option<redis::TlsConnParams>,
    address_resolver: Option<&Arc<dyn AddressResolver>>,
) -> redis::ConnectionInfo {
    let (resolved_host, resolved_port) = if let Some(resolver) = address_resolver {
        resolver.resolve(&address.host, get_port(address))
    } else {
        (address.host.to_string(), get_port(address))
    };

    let addr = if tls_mode != TlsMode::NoTls {
        redis::ConnectionAddr::TcpTls {
            host: resolved_host,
            port: resolved_port,
            insecure: tls_mode == TlsMode::InsecureTls,
            tls_params,
        }
    } else {
        redis::ConnectionAddr::Tcp(resolved_host, resolved_port)
    };
    redis::ConnectionInfo {
        addr,
        redis: redis_connection_info,
    }
}

#[derive(Clone)]
pub enum ClientWrapper {
    Standalone(StandaloneClient),
    Cluster {
        client: ClusterConnection,
        /// Owns the background mTLS certificate reload task for cluster clients, if
        /// path-based reload is configured. Held so the task lives for the client's
        /// lifetime; the [`crate::tls_reload::CertReloadHandle`] shared with the reconnect loop keeps
        /// working as long as this manager is alive.
        _cert_material_manager: Option<Arc<crate::tls_reload::CertReloadManager>>,
    },
    Lazy(Box<LazyClient>),
}

/// A client wrapper that defers connection until the first command is executed.
#[derive(Clone)]
pub struct LazyClient {
    config: ConnectionRequest,
    push_sender: Option<mpsc::UnboundedSender<PushInfo>>,
}

/// Immutable shared state of a [`Client`], held behind a single `Arc` so that
/// cloning a `Client` (which happens on **every command** via `self.clone()`) is
/// one atomic refcount bump instead of ~8 individual `Arc` bumps plus an
/// `OTelMetadata` `String` clone. All of these fields are immutable after
/// construction — the only mutable per-client state lives inside
/// `internal_client`'s `RwLock` — so sharing them via `Arc` + `Deref` keeps every
/// existing `self.<field>` access working unchanged.
pub struct ClientShared {
    internal_client: Arc<RwLock<ClientWrapper>>,
    request_timeout: Duration,
    inflight_requests_allowed: Arc<AtomicIsize>,
    inflight_requests_limit: isize,
    inflight_log_interval: isize,
    // Optional compression manager for automatic compression/decompression
    compression_manager: Option<Arc<CompressionManager>>,
    pubsub_synchronizer: Arc<dyn PubSubSynchronizer>,
    // Optional client-side cache
    client_side_cache: Option<Arc<dyn GlideCache>>,
    // Per-client latency tracker for timeout diagnostics
    latency_tracker: Arc<crate::timeout_watchdog::LatencyTracker>,
    // Optional Client-wide circuit breaker
    circuit_breaker: Option<Arc<circuit_breaker::ClientCircuitBreaker>>,
    // Tracks the current database selected at runtime (updated on SELECT commands).
    // Used by scope connections to inherit the parent's current database.
    current_database: Arc<AtomicU32>,
    // Whether this client is in cluster mode (immutable).
    is_cluster: bool,
}

#[derive(Clone)]
pub struct Client {
    shared: Arc<ClientShared>,
    // IAM token manager for automatic credential refresh. Assigned once during
    // construction (the manager is created after the client), so it is kept as a
    // direct field rather than inside `shared`. It is an `Option<Arc<_>>`, so the
    // per-command clone is at most a single refcount bump.
    iam_token_manager: Option<Arc<crate::iam::IAMTokenManager>>,
    // Per-clone diagnostic metadata. `db_namespace` is updated on `&mut self`
    // (after SELECT) via copy-on-write, so this is an `Arc` — the per-command
    // `Client::clone` is then a single refcount bump instead of cloning two
    // `String`s (host + db_namespace) on every command.
    otel_metadata: Arc<types::OTelMetadata>,
}

impl std::ops::Deref for Client {
    type Target = ClientShared;
    #[inline]
    fn deref(&self) -> &ClientShared {
        &self.shared
    }
}

async fn run_with_timeout<T>(
    timeout: Option<Duration>,
    future: impl futures::Future<Output = RedisResult<T>> + Send,
) -> redis::RedisResult<T> {
    match timeout {
        Some(duration) => match tokio::time::timeout(duration, future).await {
            Ok(result) => result,
            Err(_) => {
                // Record timeout error metric if telemetry is initialized
                if let Err(e) = GlideOpenTelemetry::record_timeout_error() {
                    log_error(
                        "OpenTelemetry:timeout_error",
                        format!("Failed to record timeout error: {e}"),
                    );
                }
                Err(io::Error::from(io::ErrorKind::TimedOut).into())
            }
        },
        None => future.await,
    }
}

/// Extension to the request timeout for blocking commands to ensure we won't return with timeout error before the server responded
const BLOCKING_CMD_TIMEOUT_EXTENSION: f64 = 0.5; // seconds

enum TimeUnit {
    Milliseconds = 1000,
    Seconds = 1,
}

/// Enumeration representing different request timeout options.
#[derive(Default, PartialEq, Debug)]
enum RequestTimeoutOption {
    // Indicates no timeout should be set for the request.
    NoTimeout,
    // Indicates the request timeout should be based on the client's configured timeout.
    #[default]
    ClientConfig,
    // Indicates the request timeout should be based on the timeout specified in the blocking command.
    BlockingCommand(Duration),
}

/// Helper function for parsing a timeout argument to f64.
/// Attempts to parse the argument found at `timeout_idx` from bytes into an f64.
fn parse_timeout_to_f64(cmd: &Cmd, timeout_idx: usize) -> RedisResult<f64> {
    let create_err = |err_msg| {
        RedisError::from((
            ErrorKind::ResponseError,
            err_msg,
            format!(
                "Expected to find timeout value at index {:?} for command {:?}.",
                timeout_idx,
                std::str::from_utf8(&cmd.command().unwrap_or_default()),
            ),
        ))
    };
    let timeout_bytes = cmd
        .arg_idx(timeout_idx)
        .ok_or(create_err("Couldn't find timeout index"))?;
    let timeout_str = std::str::from_utf8(timeout_bytes)
        .map_err(|_| create_err("Failed to parse the timeout argument to string"))?;
    timeout_str
        .parse::<f64>()
        .map_err(|_| create_err("Failed to parse the timeout argument to f64"))
}

/// Attempts to get the timeout duration from the command argument at `timeout_idx`.
/// If the argument can be parsed into a duration, it returns the duration in seconds with BlockingCmdTimeout.
/// If the timeout argument value is zero, NoTimeout will be returned. Otherwise, ClientConfigTimeout is returned.
fn get_timeout_from_cmd_arg(
    cmd: &Cmd,
    timeout_idx: usize,
    time_unit: TimeUnit,
) -> RedisResult<RequestTimeoutOption> {
    let timeout_secs = parse_timeout_to_f64(cmd, timeout_idx)? / ((time_unit as i32) as f64);
    if timeout_secs < 0.0 {
        // Timeout cannot be negative, return the client's configured request timeout
        Err(RedisError::from((
            ErrorKind::ResponseError,
            "Timeout cannot be negative",
            format!("Received timeout = {timeout_secs:?}."),
        )))
    } else if timeout_secs == 0.0 {
        // `0` means we should set no timeout
        Ok(RequestTimeoutOption::NoTimeout)
    } else {
        // We limit the maximum timeout due to restrictions imposed by Redis and the Duration crate
        if timeout_secs > u32::MAX as f64 {
            Err(RedisError::from((
                ErrorKind::ResponseError,
                "Timeout is out of range, max timeout is 2^32 - 1 (u32::MAX)",
                format!("Received timeout = {timeout_secs:?}."),
            )))
        } else {
            // Extend the request timeout to ensure we don't timeout before receiving a response from the server.
            Ok(RequestTimeoutOption::BlockingCommand(
                Duration::from_secs_f64(
                    (timeout_secs + BLOCKING_CMD_TIMEOUT_EXTENSION).min(u32::MAX as f64),
                ),
            ))
        }
    }
}

/// Returns true for commands with user-specified blocking timeouts that
/// should be excluded from latency tracking (they distort p99).
/// Also used by the pool abandon monitor to skip clients executing blocking commands.
pub fn is_blocking_command(cmd: &Cmd) -> bool {
    let command = cmd.command().unwrap_or_default();
    match command.as_slice() {
        b"XREAD" | b"XREADGROUP" => cmd.position(b"BLOCK").is_some(),
        // `command()` already normalizes the name to uppercase, so pass it through
        // to the shared name-based check (empty args slice — the only arg-dependent
        // case, XREAD/XREADGROUP, is handled above via `cmd.position`).
        name => is_blocking_command_name(name, &[]),
    }
}

/// Blocking-command check that avoids building a `redis::Cmd`.
///
/// `name` is the command name (matched case-insensitively); `args` are the
/// remaining arguments, scanned only for the `BLOCK` token of XREAD/XREADGROUP.
/// This lets FFI hot paths detect blocking commands without allocating a `Cmd`
/// and copying every argument byte (e.g. a large SET payload). It recognizes the
/// exact same set as [`is_blocking_command`].
pub fn is_blocking_command_name(name: &[u8], args: &[Vec<u8>]) -> bool {
    let upper = name.to_ascii_uppercase();
    match upper.as_slice() {
        b"BLPOP" | b"BRPOP" | b"BLMOVE" | b"BZPOPMAX" | b"BZPOPMIN" | b"BRPOPLPUSH" | b"BLMPOP"
        | b"BZMPOP" | b"WAIT" | b"WAITAOF" => true,
        // BLOCK is matched case-insensitively, mirroring `Cmd::position`.
        b"XREAD" | b"XREADGROUP" => args.iter().any(|a| a.eq_ignore_ascii_case(b"BLOCK")),
        _ => false,
    }
}

fn get_request_timeout(cmd: &Cmd, default_timeout: Duration) -> RedisResult<Option<Duration>> {
    let command = cmd.command().unwrap_or_default();
    let timeout = match command.as_slice() {
        b"BLPOP" | b"BRPOP" | b"BLMOVE" | b"BZPOPMAX" | b"BZPOPMIN" | b"BRPOPLPUSH" => {
            get_timeout_from_cmd_arg(cmd, cmd.args_iter().len() - 1, TimeUnit::Seconds)
        }
        b"BLMPOP" | b"BZMPOP" => get_timeout_from_cmd_arg(cmd, 1, TimeUnit::Seconds),
        b"XREAD" | b"XREADGROUP" => cmd
            .position(b"BLOCK")
            .map(|idx| get_timeout_from_cmd_arg(cmd, idx + 1, TimeUnit::Milliseconds))
            .unwrap_or(Ok(RequestTimeoutOption::ClientConfig)),
        b"WAIT" | b"WAITAOF" => {
            let idx = if command.as_slice() == b"WAITAOF" {
                3
            } else {
                2
            };
            get_timeout_from_cmd_arg(cmd, idx, TimeUnit::Milliseconds)
        }
        _ => Ok(RequestTimeoutOption::ClientConfig),
    }?;

    match timeout {
        RequestTimeoutOption::NoTimeout => Ok(None),
        RequestTimeoutOption::ClientConfig => Ok(Some(default_timeout)),
        RequestTimeoutOption::BlockingCommand(blocking_cmd_duration) => {
            Ok(Some(blocking_cmd_duration))
        }
    }
}

impl Client {
    /// Returns the parent client's IAM token manager, if IAM authentication is configured.
    /// Used by scoped connections to authenticate as the IAM identity instead of
    /// running unauthenticated.
    #[cfg(feature = "proto")]
    pub(crate) fn iam_token_manager(&self) -> Option<&Arc<crate::iam::IAMTokenManager>> {
        self.iam_token_manager.as_ref()
    }

    /// Checks if the given command is a SELECT command.
    /// Returns true if the command is "SELECT", false otherwise.
    /// Handles cases where command() returns None gracefully.
    /// Note: The underlying redis-rs library normalizes commands to uppercase.
    fn is_select_command(&self, cmd: &Cmd) -> bool {
        cmd.command().is_some_and(|bytes| bytes == b"SELECT")
    }

    /// Extracts the database ID from a SELECT command.
    /// Parses the first argument of the SELECT command as an i64 database ID.
    /// Returns appropriate errors for invalid formats or missing arguments.
    fn extract_database_id_from_select(&self, cmd: &Cmd) -> RedisResult<i64> {
        // For both redis::cmd("SELECT").arg("5") and redis::Cmd::new().arg("SELECT").arg("5")
        // the database ID is at arg_idx(1)
        cmd.arg_idx(1)
            .ok_or_else(|| {
                RedisError::from((
                    ErrorKind::ResponseError,
                    "SELECT command missing database argument",
                ))
            })
            .and_then(|db_bytes| {
                std::str::from_utf8(db_bytes)
                    .map_err(|_| {
                        RedisError::from((ErrorKind::ResponseError, "Invalid database ID format"))
                    })
                    .and_then(|db_str| {
                        db_str.parse::<i64>().map_err(|_| {
                            RedisError::from((
                                ErrorKind::ResponseError,
                                "Database ID must be a valid integer",
                            ))
                        })
                    })
            })
    }

    /// Handles SELECT command processing after successful execution.
    /// Updates database state for standalone, cluster, and lazy clients.
    ///
    /// Note: `db_namespace` is updated on `&mut self`, but `Client` is cloned
    /// into each request handler. If concurrent tasks issue SELECT, a cloned
    /// Client may report a stale `db_namespace` in OTel spans. This is an
    /// acceptable trade-off since concurrent SELECTs are rare in practice.
    async fn handle_select_command(&mut self, cmd: &Cmd) -> RedisResult<()> {
        let database_id = self.extract_database_id_from_select(cmd)?;

        self.update_stored_database_id(database_id).await?;
        // Keep OTel db.namespace in sync
        Arc::make_mut(&mut self.otel_metadata).db_namespace = database_id.to_string();
        // Keep current_database in sync (used by scope connections to inherit parent state)
        self.shared
            .current_database
            .store(database_id as u32, Ordering::Release);
        Ok(())
    }

    /// Updates the stored database ID for different client types.
    /// Handles standalone, cluster, and lazy clients appropriately.
    /// Ensures thread-safe updates using existing synchronization mechanisms.
    async fn update_stored_database_id(&self, database_id: i64) -> RedisResult<()> {
        let mut guard = self.internal_client.write().await;
        match &mut *guard {
            ClientWrapper::Standalone(client) => {
                client.update_connection_database(database_id).await?;
                Ok(())
            }
            ClientWrapper::Cluster { client, .. } => {
                // Update cluster connection database configuration
                client.update_connection_database(database_id).await?;
                Ok(())
            }
            ClientWrapper::Lazy(_) => {
                unreachable!("Lazy client should have been initialized")
            }
        }
    }

    /// Checks if the given command is a CLIENT SETNAME command.
    /// Returns true if the command is "CLIENT SETNAME", false otherwise.
    fn is_client_set_name_command(&self, cmd: &Cmd) -> bool {
        // Check if the command is "CLIENT SETNAME"
        cmd.command()
            .is_some_and(|bytes| bytes == b"CLIENT SETNAME")
    }

    /// Extracts the client name from a CLIENT SETNAME command.
    /// Parses the client name argument from the CLIENT SETNAME command.
    /// Returns None if the argument is missing or invalid.
    fn extract_client_name_from_client_set_name(&self, cmd: &Cmd) -> Option<String> {
        // For redis::cmd("CLIENT").arg("SETNAME").arg("name")
        // the client name is at arg_idx(2) (after "SETNAME")
        cmd.arg_idx(2).and_then(|name_bytes| {
            std::str::from_utf8(name_bytes)
                .ok()
                .map(|name_str| name_str.to_string())
        })
    }

    /// Handles CLIENT SETNAME command processing after successful execution.
    /// Updates connection name state for standalone, cluster, and lazy clients.
    async fn handle_client_set_name_command(&mut self, cmd: &Cmd) -> RedisResult<()> {
        // Extract client name from the CLIENT SETNAME command
        let client_name = self.extract_client_name_from_client_set_name(cmd);

        // Update client name state for all client types
        self.update_stored_client_name(client_name).await?;
        Ok(())
    }

    /// Updates the stored client name for different client types.
    /// Handles standalone, cluster, and lazy clients appropriately.
    /// Ensures thread-safe updates using existing synchronization mechanisms.
    async fn update_stored_client_name(&self, client_name: Option<String>) -> RedisResult<()> {
        let mut guard = self.internal_client.write().await;
        match &mut *guard {
            ClientWrapper::Standalone(client) => {
                client.update_connection_client_name(client_name).await?;
                Ok(())
            }
            ClientWrapper::Cluster { client, .. } => {
                // Update cluster connection database configuration
                client.update_connection_client_name(client_name).await?;
                Ok(())
            }
            ClientWrapper::Lazy(_) => {
                unreachable!("Lazy client should have been initialized")
            }
        }
    }

    /// Checks if the given command is an AUTH command.
    /// Returns true if the command is "AUTH", false otherwise.
    fn is_auth_command(&self, cmd: &Cmd) -> bool {
        cmd.command().is_some_and(|bytes| bytes == b"AUTH")
    }

    /// Extracts authentication information from an AUTH command.
    /// Returns (username, password) tuple where username is None for password-only auth.
    ///
    /// AUTH command formats:
    /// - AUTH password (args: \[password\])
    /// - AUTH username password (args: \[username, password\])
    fn extract_auth_info(&self, cmd: &Cmd) -> (Option<String>, Option<String>) {
        // Get the first argument
        let first_arg = cmd
            .arg_idx(1)
            .and_then(|bytes| std::str::from_utf8(bytes).ok().map(|s| s.to_string()));

        // Get the second argument
        let second_arg = cmd
            .arg_idx(2)
            .and_then(|bytes| std::str::from_utf8(bytes).ok().map(|s| s.to_string()));

        match (first_arg, second_arg) {
            // AUTH username password
            (Some(username), Some(password)) => (Some(username), Some(password)),
            // AUTH password
            (Some(password), None) => (None, Some(password)),
            // Invalid AUTH command
            _ => (None, None),
        }
    }

    /// Handles AUTH command processing after successful execution.
    /// Updates username and password state for standalone, cluster, and lazy clients.
    async fn handle_auth_command(&mut self, cmd: &Cmd) -> RedisResult<()> {
        let (username, password) = self.extract_auth_info(cmd);

        // Update username if provided
        if username.is_some() {
            self.update_stored_username(username).await?;
        }

        // Update password if provided (updateConnectionPassword handles this, so we track it too)
        if password.is_some() {
            self.update_stored_password(password).await?;
        }

        Ok(())
    }

    /// Updates the stored username for different client types.
    async fn update_stored_username(&self, username: Option<String>) -> RedisResult<()> {
        let mut guard = self.internal_client.write().await;
        match &mut *guard {
            ClientWrapper::Standalone(client) => {
                client.update_connection_username(username).await?;
                Ok(())
            }
            ClientWrapper::Cluster { client, .. } => {
                client.update_connection_username(username).await?;
                Ok(())
            }
            ClientWrapper::Lazy(_) => {
                unreachable!("Lazy client should have been initialized")
            }
        }
    }

    /// Updates the stored password for different client types.
    async fn update_stored_password(&self, password: Option<String>) -> RedisResult<()> {
        let mut guard = self.internal_client.write().await;
        match &mut *guard {
            ClientWrapper::Standalone(client) => {
                client.update_connection_password(password).await?;
                Ok(())
            }
            ClientWrapper::Cluster { client, .. } => {
                client.update_connection_password(password).await?;
                Ok(())
            }
            ClientWrapper::Lazy(_) => {
                unreachable!("Lazy client should have been initialized")
            }
        }
    }

    /// Checks if the given command is a HELLO command.
    /// Returns true if the command is "HELLO", false otherwise.
    fn is_hello_command(&self, cmd: &Cmd) -> bool {
        cmd.command().is_some_and(|bytes| bytes == b"HELLO")
    }

    /// Extracts protocol version and optional auth info from a HELLO command.
    /// Returns (protocol_version, username, password, client_name) tuple.
    ///
    /// HELLO command formats:
    /// - HELLO 3
    /// - HELLO 3 AUTH username password
    /// - HELLO 3 SETNAME clientname
    /// - HELLO 3 AUTH username password SETNAME clientname
    fn extract_hello_info(
        &self,
        cmd: &Cmd,
    ) -> (
        Option<redis::ProtocolVersion>,
        Option<String>,
        Option<String>,
        Option<String>,
    ) {
        // Get protocol version (first argument)
        let protocol = cmd.arg_idx(1).and_then(|bytes| {
            std::str::from_utf8(bytes).ok().and_then(|s| match s {
                "2" => Some(redis::ProtocolVersion::RESP2),
                "3" => Some(redis::ProtocolVersion::RESP3),
                _ => None,
            })
        });

        let mut username = None;
        let mut password = None;
        let mut client_name = None;

        // Parse optional arguments (AUTH username password, SETNAME name)
        let mut idx = 2;
        while let Some(arg) = cmd.arg_idx(idx) {
            if let Ok(arg_str) = std::str::from_utf8(arg) {
                match arg_str.to_uppercase().as_str() {
                    "AUTH" => {
                        // Next two args are username and password
                        username = cmd.arg_idx(idx + 1).and_then(|bytes| {
                            std::str::from_utf8(bytes).ok().map(|s| s.to_string())
                        });
                        password = cmd.arg_idx(idx + 2).and_then(|bytes| {
                            std::str::from_utf8(bytes).ok().map(|s| s.to_string())
                        });
                        idx += 3;
                    }
                    "SETNAME" => {
                        // Next arg is client name
                        client_name = cmd.arg_idx(idx + 1).and_then(|bytes| {
                            std::str::from_utf8(bytes).ok().map(|s| s.to_string())
                        });
                        idx += 2;
                    }
                    _ => {
                        idx += 1;
                    }
                }
            } else {
                break;
            }
        }

        (protocol, username, password, client_name)
    }

    /// Handles HELLO command processing after successful execution.
    /// Updates protocol version and optionally auth info and client name.
    async fn handle_hello_command(&mut self, cmd: &Cmd) -> RedisResult<()> {
        let (protocol, username, password, client_name) = self.extract_hello_info(cmd);

        // Update protocol version if provided
        if let Some(protocol) = protocol {
            self.update_stored_protocol(protocol).await?;
        }

        // Update username if provided
        if username.is_some() {
            self.update_stored_username(username).await?;
        }

        // Update password if provided
        if password.is_some() {
            self.update_stored_password(password).await?;
        }

        // Update client name if provided
        if client_name.is_some() {
            self.update_stored_client_name(client_name).await?;
        }

        Ok(())
    }

    /// Updates the stored protocol version for different client types.
    async fn update_stored_protocol(&self, protocol: redis::ProtocolVersion) -> RedisResult<()> {
        let mut guard = self.internal_client.write().await;
        match &mut *guard {
            ClientWrapper::Standalone(client) => {
                client.update_connection_protocol(protocol).await?;
                Ok(())
            }
            ClientWrapper::Cluster { client, .. } => {
                client.update_connection_protocol(protocol).await?;
                Ok(())
            }
            ClientWrapper::Lazy(_) => {
                unreachable!("Lazy client should have been initialized")
            }
        }
    }

    fn is_reset_command(&self, cmd: &Cmd) -> bool {
        cmd.command().is_some_and(|bytes| bytes == b"RESET")
    }

    async fn handle_reset_command(&mut self) -> RedisResult<()> {
        // RESET resets the connection to its initial state per the Valkey spec.
        // https://valkey.io/commands/reset/
        //
        // TRACKED - glide-core updates these so reconnections restore the post-RESET state:
        //   SELECTs database 0                       -> update_stored_database_id(0)
        //   Clears client name                       -> update_stored_client_name(None)
        //   Sets protocol to RESP2                   -> update_stored_protocol(RESP2)
        //   Aborts Pub/Sub subscription state        -> remove_desired_subscriptions(all kinds)
        //     (prevents synchronizer from resubscribing on reconnect)
        //
        // NOT TRACKED - no glide-core state to update:
        //   Deauthenticates the connection           -> auth credentials kept for reconnect;
        //     (requires AUTH to reauthenticate)         live connection is deauthed until
        //                                               reconnect or manual AUTH call
        //   Discards current MULTI transaction       -> glide sends MULTI+cmds+EXEC as a
        //                                               single pipeline; no persistent state
        //   Unwatches all WATCHed keys               -> WATCH state is per-connection,
        //                                               not tracked by glide-core
        //   Disables CLIENT TRACKING                 -> not tracked; gap exists if
        //                                               client-side caching is active
        //   Sets connection to READWRITE mode         -> not tracked; glide does not
        //                                               persist read/write mode per connection
        //   Cancels ASKING mode (cluster)             -> one-shot flag sent inline,
        //                                               not persisted by glide-core
        //   Sets CLIENT REPLY to ON                  -> not tracked; CLIENT REPLY not
        //                                               yet supported by glide
        //   Exits MONITOR mode                       -> not tracked; MONITOR not yet
        //                                               supported by glide
        //   Turns off NO-EVICT mode                  -> not tracked; per-connection hint
        //   Turns off NO-TOUCH mode                  -> not tracked; per-connection hint
        self.update_stored_database_id(0).await?;
        self.update_stored_client_name(None).await?;
        self.update_stored_protocol(redis::ProtocolVersion::RESP2)
            .await?;
        Arc::make_mut(&mut self.otel_metadata).db_namespace = "0".to_string();
        for kind in [
            redis::PubSubSubscriptionKind::Exact,
            redis::PubSubSubscriptionKind::Pattern,
            redis::PubSubSubscriptionKind::Sharded,
        ] {
            self.pubsub_synchronizer
                .remove_desired_subscriptions(None, kind);
        }
        Ok(())
    }

    async fn get_or_initialize_client(&self) -> RedisResult<ClientWrapper> {
        {
            let guard = self.internal_client.read().await;
            if !matches!(&*guard, ClientWrapper::Lazy(_)) {
                return Ok(guard.clone()); // ✅ Already initialized, return clone
            }
        }

        // Handle lazy client initialization
        let (config, push_sender) = {
            let mut guard = self.internal_client.write().await;
            if let ClientWrapper::Lazy(lazy_client) = &mut *guard {
                let config = lazy_client.config.clone();
                let push_sender = lazy_client.push_sender.clone();
                (config, push_sender)
            } else {
                // Another thread initialized it while we were waiting
                return Ok(guard.clone());
            }
        };

        // Continue with client initialization
        let mut config = config;
        config.lazy_connect = false;

        let mut guard = self.internal_client.write().await;
        let iam_manager_ref = self.iam_token_manager.as_ref();
        if let ClientWrapper::Lazy(_) = &*guard {
            // Create the appropriate client based on configuration
            let real_client = if config.cluster_mode_enabled {
                // Create cluster client
                let (client, cert_material_manager) = create_cluster_client(
                    config,
                    push_sender,
                    iam_manager_ref,
                    self.pubsub_synchronizer.clone(),
                )
                .await?;
                ClientWrapper::Cluster {
                    client,
                    _cert_material_manager: cert_material_manager,
                }
            } else {
                // Create standalone client
                let client = StandaloneClient::create_client(
                    config,
                    push_sender,
                    iam_manager_ref,
                    Some(self.pubsub_synchronizer.clone()),
                )
                .await
                .map_err(|e| {
                    RedisError::from((
                        ErrorKind::IoError,
                        "Standalone connect failed",
                        format!("{e:?}"),
                    ))
                })?;
                ClientWrapper::Standalone(client)
            };

            // Replace the lazy client with the real client
            *guard = real_client;
        }

        // We must drop the guard so the pubsub synchronizer can acquire it when subscribing
        // to channels provided via config. Keeping the guard would cause a deadlock. We wait
        // for the subscription here to ensure the lazy client is subscribed immediately upon creation.
        drop(guard);
        if let Err(e) = self
            .pubsub_synchronizer
            .wait_for_sync(0, None, None, None)
            .await
        {
            log_warn(
                "Client::new",
                format!("Failed to establish initial subscriptions within timeout: {e:?}"),
            );
        }

        // Re-acquire for the return
        let guard = self.internal_client.read().await;
        Ok(guard.clone()) // ✅ Return clone of the now-initialized wrapper
    }

    /// Internal command execution logic. Takes owned data so the returned future
    /// is `Send + 'static`.
    async fn execute_command_owned(
        mut self_clone: Client,
        cmd: Arc<Cmd>,
        routing: Option<RoutingInfo>,
        client: ClientWrapper,
        compression_manager: Option<Arc<CompressionManager>>,
    ) -> RedisResult<Value> {
        let raw_value = match client {
            ClientWrapper::Standalone(mut client) => client.send_command(&cmd).await,
            ClientWrapper::Cluster { mut client, .. } => {
                let final_routing = if let Some(RoutingInfo::SingleNode(
                    SingleNodeRoutingInfo::Random,
                )) = routing
                {
                    let cmd_name = cmd.command().unwrap_or_default();
                    let cmd_name = String::from_utf8_lossy(&cmd_name);
                    if redis::cluster_routing::is_readonly_cmd(cmd_name.as_bytes()) {
                        RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random)
                    } else {
                        log_warn(
                            "send_command",
                            format!(
                                "User provided 'Random' routing which is not suitable for the writeable command '{cmd_name}'. Changing it to 'RandomPrimary'"
                            ),
                        );
                        RoutingInfo::SingleNode(SingleNodeRoutingInfo::RandomPrimary)
                    }
                } else {
                    routing
                        .or_else(|| RoutingInfo::for_routable(cmd.as_ref()))
                        .unwrap_or(RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random))
                };
                client.route_command(&cmd, final_routing).await
            }
            ClientWrapper::Lazy(_) => unreachable!("Lazy client should have been initialized"),
        }?;

        // Post-process: decompress and convert to expected type.
        // Done after the mutable borrow on cmd is released.
        let processed_value = if let Some(ref compression_manager) = compression_manager {
            if let Some(request_type) = extract_request_type_from_cmd(&cmd) {
                match crate::compression::process_response_for_decompression(
                    raw_value.clone(),
                    request_type,
                    Some(compression_manager.as_ref()),
                ) {
                    Ok(decompressed_value) => decompressed_value,
                    Err(e) => {
                        // Propagate critical errors (size limit exceeded, incompatible command)
                        // to the user instead of silently falling back to raw value
                        if e.should_propagate() {
                            return Err(redis::RedisError::from((
                                redis::ErrorKind::IoError,
                                "Decompression error",
                                e.to_string(),
                            )));
                        }
                        log_warn(
                            "send_command_decompression",
                            format!("Failed to decompress response: {}", e),
                        );
                        raw_value
                    }
                }
            } else {
                raw_value
            }
        } else {
            raw_value
        };

        let expected_type = expected_type_for_cmd(&cmd);
        let value = convert_to_expected_type(processed_value, expected_type)?;

        if self_clone.is_client_set_name_command(&cmd) {
            self_clone.handle_client_set_name_command(&cmd).await?;
        }
        if self_clone.is_select_command(&cmd) {
            self_clone.handle_select_command(&cmd).await?;
        }
        if self_clone.is_auth_command(&cmd) {
            self_clone.handle_auth_command(&cmd).await?;
        }
        if self_clone.is_hello_command(&cmd) {
            self_clone.handle_hello_command(&cmd).await?;
        }
        if self_clone.is_reset_command(&cmd) {
            self_clone.handle_reset_command().await?;
        }
        Ok(value)
    }

    pub fn send_command<'a>(
        &'a mut self,
        cmd: &'a mut Cmd,
        routing: Option<RoutingInfo>,
    ) -> redis::RedisFuture<'a, Value> {
        Box::pin(async move {
            // Check for IAM token changes and update the password without authentication if needed (pull model)
            if let Some(iam_manager) = &self.iam_token_manager
                && iam_manager.token_changed()
            {
                let current_token = iam_manager.get_token().await;
                if current_token.is_empty() {
                    return Err(RedisError::from((
                        ErrorKind::ClientError,
                        "IAM token not available",
                    )));
                }
                iam_manager.clear_token_changed();
                log_debug(
                    "update_connection_password",
                    "Updating connection password with IAM token",
                );
                self.update_connection_password(Some(current_token), false)
                    .await?;
            }

            let client = self.get_or_initialize_client().await?;

            // Reject immediately if circuit breaker is open.
            if !self.is_circuit_breaker_healthy() {
                return Err(RedisError::from((
                    ErrorKind::CircuitBreakerOpen,
                    "Client circuit breaker is open - core unhealthy",
                )));
            }

            if let Some(result) = self.pubsub_synchronizer.intercept_pubsub_command(cmd).await {
                return result;
            }

            let request_timeout = get_request_timeout(cmd, self.request_timeout)?;

            // Reserve an inflight slot. The tracker holds the slot until the
            // last clone of the Cmd is dropped (i.e. all sub-commands in the
            // cluster event loop finish). This decouples user-facing timeout
            // from internal pipeline cleanup.
            let tracker = match self.reserve_inflight_request() {
                Some(t) => t,
                None => {
                    let available = self.inflight_requests_allowed.load(Ordering::Relaxed);
                    log_warn_rate_limited!(
                        "inflight",
                        10,
                        format!(
                            "Inflight request limit exhausted. limit={}, available={}",
                            self.inflight_requests_limit, available
                        )
                    );
                    return Err(RedisError::from((
                        ErrorKind::ClientError,
                        "Reached maximum inflight requests",
                    )));
                }
            };

            // Log at debug level when inflight usage crosses a 10% threshold.
            // Only one log per threshold crossing — zero noise when stable.
            {
                static LAST_BUCKET: AtomicIsize = AtomicIsize::new(0);
                let remaining = self.inflight_requests_allowed.load(Ordering::Relaxed);
                let used = self.inflight_requests_limit - remaining;
                let bucket = used / self.inflight_log_interval;
                let prev = LAST_BUCKET.load(Ordering::Relaxed);
                if bucket != prev {
                    LAST_BUCKET.store(bucket, Ordering::Relaxed);
                    log_debug(
                        "inflight",
                        format!(
                            "Inflight: {used}/{} slots used",
                            self.inflight_requests_limit
                        ),
                    );
                }
            }

            cmd.set_inflight_tracker(tracker);
            cmd.set_response_timeout(request_timeout);

            // Clone compression_manager reference only if compression is enabled
            let compression_manager = if self.is_compression_enabled() {
                self.compression_manager.clone()
            } else {
                None
            };
            let self_clone = self.clone();

            // Blocking commands have artificially long latencies; exclude from tracker.
            let is_blocking_cmd = is_blocking_command(cmd);
            // Propagate the blocking flag into the Cmd BEFORE cloning owned_cmd so
            // the copy that actually travels to the multiplexed connection carries
            // it, letting that connection suppress false-positive response-wait
            // warnings (#6283).
            cmd.set_is_blocking(is_blocking_cmd);
            let owned_cmd = cmd.clone();

            // Captured by the timeout path for watchdog-informed CB decisions.
            let mut timeout_cause: Option<crate::timeout_watchdog::TimeoutCause> = None;

            let result = match request_timeout {
                Some(duration) => {
                    // Compute inflight count (cheap atomic load)
                    let inflight = Some(
                        (self.inflight_requests_limit
                            - self.inflight_requests_allowed.load(Ordering::Relaxed))
                            as usize,
                    );

                    // Wrap Cmd in Arc so the timeout arm can still read watchdog fields after execute takes ownership
                    let owned_cmd = Arc::new(owned_cmd);

                    // Single Instant::now() shared between watchdog and latency tracking
                    let cmd_start = Instant::now();

                    let timeout_rx = crate::timeout_watchdog::TimeoutWatchdog::global()
                        .register(duration, cmd_start);
                    // Defer the expensive Debug-format of the route to the (rare)
                    // timeout path. Cloning the routing is cheap for the common
                    // single-node case (no heap allocation); previously a String was
                    // allocated and Debug-formatted on EVERY command just for a
                    // diagnostic field that is only read when a timeout fires.
                    let routing_for_diag = routing.clone();
                    let execute = Self::execute_command_owned(
                        self_clone,
                        owned_cmd.clone(),
                        routing,
                        client,
                        compression_manager,
                    );

                    tokio::pin!(execute);
                    tokio::select! {
                        result = &mut execute => {
                            // Record latency into per-client tracker
                            if !is_blocking_cmd {
                                let elapsed = cmd_start.elapsed();
                                self.latency_tracker.record(elapsed);
                            }
                            result
                        }
                        recv_result = timeout_rx => {
                            match recv_result {
                                Err(_) => {
                                    // Watchdog thread died — fall through to let the
                                    // command complete via Tokio's timer as fallback.
                                    execute.await
                                }
                                Ok(()) => {
                                    // Build diagnostic event on the consumer side (rare timeout path)
                                    let actual_elapsed = cmd_start.elapsed();
                                    let (phase, node, retry_count, command) = {
                                        let p = owned_cmd.watchdog_phase.load(Ordering::Acquire);
                                        let n: String = routing_for_diag
                                            .as_ref()
                                            .map(|r| format!("{:?}", r))
                                            .unwrap_or_else(|| "unknown".to_owned());
                                        let r = owned_cmd.watchdog_retry_count.load(Ordering::Relaxed);
                                        let c = owned_cmd.arg_idx(0)
                                            .map(crate::timeout_watchdog::cmd_name_from_bytes)
                                            .unwrap_or("UNKNOWN");
                                        (
                                            if p == redis::PHASE_SENT {
                                                crate::timeout_watchdog::CommandPhase::Sent
                                            } else {
                                                crate::timeout_watchdog::CommandPhase::Queued
                                            },
                                            n,
                                            r,
                                            c,
                                        )
                                    };
                                    let pending = crate::timeout_watchdog::pending_count();
                                    let inflight_now = (self.inflight_requests_limit
                                        - self.inflight_requests_allowed.load(Ordering::Relaxed))
                                        as usize;
                                    let p99 = self.latency_tracker.p99();
                                    let cause = if phase == crate::timeout_watchdog::CommandPhase::Queued {
                                        crate::timeout_watchdog::TimeoutCause::ClientBackpressure {
                                            queue_depth: pending,
                                            scheduling_delay: actual_elapsed,
                                        }
                                    } else if pending > 100 {
                                        crate::timeout_watchdog::TimeoutCause::SystemOverload {
                                            pending_total: pending,
                                        }
                                    } else {
                                        crate::timeout_watchdog::TimeoutCause::ServerUnresponsive {
                                            node: node.clone(),
                                        }
                                    };
                                    timeout_cause = Some(cause.clone());
                                    let event = crate::timeout_watchdog::TimeoutEvent {
                                        cause,
                                        command,
                                        node,
                                        phase,
                                        configured_timeout: duration,
                                        actual_elapsed,
                                        pending_commands: pending,
                                        recent_p99_latency: p99,
                                        rss_bytes: crate::timeout_watchdog::get_rss(),
                                        suggested_timeout: p99.map(|p| (p * 3).max(duration)),
                                        inflight_at_register: inflight,
                                        inflight_at_timeout: Some(inflight_now),
                                        retry_count,
                                    };

                                    log_warn_rate_limited!(
                                        "timeout_watchdog",
                                        2,
                                        event.to_string()
                                    );
                                    if let Err(e) = GlideOpenTelemetry::record_timeout_error() {
                                        log_error(
                                            "OpenTelemetry:timeout_error",
                                            format!("Failed to record timeout error: {e}"),
                                        );
                                    }
                                    Err(io::Error::from(io::ErrorKind::TimedOut).into())
                                }
                            }
                        }
                    }
                }
                None => {
                    let owned_cmd = Arc::new(owned_cmd);
                    let execute = Self::execute_command_owned(
                        self_clone,
                        owned_cmd,
                        routing,
                        client,
                        compression_manager,
                    );
                    execute.await
                }
            };

            // Report result to client-wide circuit breaker
            if let Some(cb) = &self.circuit_breaker {
                let (is_error, error_kind) = match result.as_ref() {
                    Ok(_) => (false, None),
                    Err(e) => {
                        let counts = if e.is_timeout() {
                            cb.counts_timeouts()
                        } else {
                            matches!(
                                e.kind(),
                                ErrorKind::IoError
                                    | ErrorKind::FatalSendError
                                    | ErrorKind::FatalReceiveError
                            ) || e.is_connection_dropped()
                        };
                        if counts {
                            let kind_str = if e.is_timeout() {
                                match &timeout_cause {
                                    Some(
                                        crate::timeout_watchdog::TimeoutCause::SystemOverload {
                                            ..
                                        },
                                    ) => "TimeoutSystemOverload",
                                    Some(
                                        crate::timeout_watchdog::TimeoutCause::ClientBackpressure {
                                            ..
                                        },
                                    ) => "TimeoutClientBackpressure",
                                    _ => "TimeoutServerUnresponsive",
                                }
                            } else {
                                match e.kind() {
                                    ErrorKind::FatalSendError => "FatalSendError",
                                    ErrorKind::FatalReceiveError => "FatalReceiveError",
                                    _ => "IoError",
                                }
                            };
                            (true, Some(kind_str))
                        } else {
                            (false, None)
                        }
                    }
                };
                let current_inflight = (self.inflight_requests_limit
                    - self.inflight_requests_allowed.load(Ordering::Relaxed))
                    as u32;
                cb.on_result(is_error, error_kind, current_inflight);
            }

            result
        })
    }

    /// Execute a command on a provided dedicated connection (for isolated execution).
    ///
    /// Applies the same timeout, decompression, compression, and IAM token refresh
    /// logic as `send_command`, but routes the command to the given
    /// `MultiplexedConnection` instead of the client's internal managed connection.
    ///
    /// IAM change-detection here uses `last_seen_generation` (owned by the caller)
    /// instead of the shared `token_changed` flag — see `IAMTokenManager::token_generation`.
    ///
    /// Note: OTel spans and inflight tracking are not applied here because scoped
    /// connections operate outside the multiplexer's pipeline. OTel support for
    /// scopes is tracked as a follow-up enhancement.
    pub async fn send_command_on_connection(
        &self,
        cmd: &Cmd,
        connection: &mut redis::aio::MultiplexedConnection,
        last_seen_generation: &AtomicU64,
        defer_reauth: bool,
    ) -> RedisResult<Value> {
        // IAM token refresh: re-authenticate when the token rotated (generation
        // advanced since this connection last applied one). `defer_reauth` skips it
        // where AUTH can't run — an open transaction (queued, corrupting the EXEC
        // reply) or RESP2 subscribed mode (rejected outright). The bookmark stays
        // unadvanced, so it retries after EXEC/DISCARD, or on the next borrow for
        // subscribed mode (subscriptions clear only on release).
        if !defer_reauth && let Some(iam_manager) = &self.iam_token_manager {
            let current_generation = iam_manager.token_generation();
            if current_generation != last_seen_generation.load(Ordering::Acquire) {
                let current_token = iam_manager.get_token().await;
                if current_token.is_empty() {
                    return Err(RedisError::from((
                        ErrorKind::ClientError,
                        "IAM token not available",
                    )));
                }
                let auth_cmd = redis::cmd("AUTH")
                    .arg(iam_manager.username())
                    .arg(current_token.as_str())
                    .to_owned();
                // Signals both failure modes as AuthenticationFailed so the caller
                // discards the connection. The server never produces this kind
                // itself, so it can't be confused with a command's own auth error.
                match tokio::time::timeout(
                    self.request_timeout,
                    connection.send_packed_command(&auth_cmd),
                )
                .await
                {
                    Ok(Ok(_)) => {}
                    Ok(Err(e)) => {
                        return Err(RedisError::from((
                            ErrorKind::AuthenticationFailed,
                            "IAM re-authentication failed",
                            e.to_string(),
                        )));
                    }
                    Err(_) => {
                        return Err(RedisError::from((
                            ErrorKind::AuthenticationFailed,
                            "IAM re-authentication timed out",
                        )));
                    }
                }
                last_seen_generation.store(current_generation, Ordering::Release);
            }
        }

        // Compression on write: compress command args if compression is enabled
        let mut cmd_to_send = if let Some(ref compression_manager) = self.compression_manager {
            if compression_manager.is_enabled() {
                // Clone the command and apply compression to its args
                // Note: for scope commands, args are already serialized — this handles
                // cases where values passed to SET/LPUSH/etc. should be compressed.
                // The scope wire format passes raw args, so compression applies here.
                cmd.clone() // TODO: apply arg compression when scope command args support it
            } else {
                cmd.clone()
            }
        } else {
            cmd.clone()
        };

        // Blocking commands must honor their own timeout, not the flat request timeout.
        let request_timeout = get_request_timeout(cmd, self.request_timeout)?;
        // Scoped connections use Duration::MAX as their base response timeout, so
        // without this, the pipeline driver's slow-response warning (gated on
        // `!is_blocking_cmd`) fires for any legitimately-blocking command that
        // waits past its threshold — mirrors the multiplexed path (see
        // `set_is_blocking` above in `send_command`).
        cmd_to_send.set_is_blocking(is_blocking_command(cmd));

        // Send with timeout
        let raw_value = match request_timeout {
            Some(duration) => {
                match tokio::time::timeout(duration, connection.send_packed_command(&cmd_to_send))
                    .await
                {
                    Ok(result) => result?,
                    Err(_) => {
                        return Err(std::io::Error::from(std::io::ErrorKind::TimedOut).into());
                    }
                }
            }
            None => connection.send_packed_command(&cmd_to_send).await?,
        };

        // Apply decompression if compression is enabled
        let processed_value = if let Some(ref compression_manager) = self.compression_manager {
            if let Some(request_type) = extract_request_type_from_cmd(cmd) {
                match crate::compression::process_response_for_decompression(
                    raw_value.clone(),
                    request_type,
                    Some(compression_manager.as_ref()),
                ) {
                    Ok(decompressed) => decompressed,
                    Err(e) => {
                        if e.should_propagate() {
                            return Err(redis::RedisError::from((
                                redis::ErrorKind::IoError,
                                "Decompression error",
                                e.to_string(),
                            )));
                        }
                        raw_value
                    }
                }
            } else {
                raw_value
            }
        } else {
            raw_value
        };

        // Type conversion
        let expected_type = expected_type_for_cmd(cmd);
        convert_to_expected_type(processed_value, expected_type)
    }

    /// Get the primary node address for a given hash slot (cluster mode).
    /// Returns the host:port string for the primary that owns the slot.
    /// Returns None in standalone mode or if the slot is unmapped.
    ///
    /// Used by isolated execution to open scoped connections
    /// directly to the correct node, avoiding MOVED redirects.
    pub async fn address_for_slot(&self, slot: u16) -> Option<String> {
        let client = self.internal_client.read().await;
        match &*client {
            ClientWrapper::Cluster { client, .. } => client.address_for_slot(slot),
            _ => None,
        }
    }

    /// Returns true if this client is connected in cluster mode.
    /// Used by scope connections to determine whether slot validation is needed.
    pub fn is_cluster_mode(&self) -> bool {
        self.shared.is_cluster
    }

    /// Returns the cache hit rate (hits / total requests).
    /// Returns an error if caching is not enabled or metrics are disabled.
    pub fn cache_hit_rate(&self) -> RedisResult<Value> {
        let cache = self.client_side_cache.as_ref().ok_or_else(|| {
            RedisError::from((
                ErrorKind::InvalidClientConfig,
                "Client-side caching is not enabled",
            ))
        })?;

        let metrics = cache.metrics()?;

        Ok(Value::Double(metrics.hit_rate()))
    }

    /// Returns the cache miss rate (misses / total requests).
    /// Returns an error if caching is not enabled or metrics are disabled.
    pub fn cache_miss_rate(&self) -> RedisResult<Value> {
        let cache = self.client_side_cache.as_ref().ok_or_else(|| {
            RedisError::from((
                ErrorKind::InvalidClientConfig,
                "Client-side caching is not enabled",
            ))
        })?;

        let metrics = cache.metrics()?;

        Ok(Value::Double(metrics.miss_rate()))
    }

    /// Returns the total number of cache entries.
    /// Returns an error if caching is not enabled.
    pub fn cache_entry_count(&self) -> RedisResult<Value> {
        let cache = self.client_side_cache.as_ref().ok_or_else(|| {
            RedisError::from((
                ErrorKind::InvalidClientConfig,
                "Client-side caching is not enabled",
            ))
        })?;
        Ok(Value::Int(cache.entry_count() as i64))
    }

    /// returns the total number of evictions that occurred in the cache.
    /// Returns an error if caching is not enabled or metrics are disabled.
    pub fn cache_evictions(&self) -> RedisResult<Value> {
        let cache = self.client_side_cache.as_ref().ok_or_else(|| {
            RedisError::from((
                ErrorKind::InvalidClientConfig,
                "Client-side caching is not enabled",
            ))
        })?;

        let metrics = cache.metrics()?;
        Ok(Value::Int(metrics.evictions() as i64))
    }

    /// Returns the total number of cache lookups (hits + misses).
    /// Returns an error if caching is not enabled or metrics are disabled.
    pub fn cache_total_lookups(&self) -> RedisResult<Value> {
        let cache = self.client_side_cache.as_ref().ok_or_else(|| {
            RedisError::from((
                ErrorKind::InvalidClientConfig,
                "Client-side caching is not enabled",
            ))
        })?;

        let metrics = cache.metrics()?;
        Ok(Value::Int(metrics.total_lookups() as i64))
    }

    /// Returns the total number of expired entries that were removed from the cache.
    /// Returns an error if caching is not enabled or metrics are disabled.
    pub fn cache_expirations(&self) -> RedisResult<Value> {
        let cache = self.client_side_cache.as_ref().ok_or_else(|| {
            RedisError::from((
                ErrorKind::InvalidClientConfig,
                "Client-side caching is not enabled",
            ))
        })?;

        let metrics = cache.metrics()?;
        Ok(Value::Int(metrics.expirations() as i64))
    }

    // Cluster scan is not passed to redis-rs as a regular command, so we need to handle it separately.
    // We send the command to a specific function in the redis-rs cluster client, which internally handles the
    // the complication of a command scan, and generate the command base on the logic in the redis-rs library.
    //
    // The function returns a tuple with the cursor and the keys found in the scan.
    // The cursor is not a regular cursor, but an ARC to a struct that contains the cursor and the data needed
    // to continue the scan called ScanState.
    // In order to avoid passing Rust GC to clean the ScanState when the cursor (ref) is passed to the wrapper,
    // which means that Rust layer is not aware of the cursor anymore, we need to keep the ScanState alive.
    // We do that by storing the ScanState in a global container, and return a cursor-id of the cursor to the wrapper.
    //
    // The wrapper create an object contain the cursor-id with a drop function that will remove the cursor from the container.
    // When the ref is removed from the hash-map, there's no more references to the ScanState, and the GC will clean it.
    pub async fn cluster_scan<'a>(
        &'a mut self,
        scan_state_cursor: &'a ScanStateRC,
        cluster_scan_args: ClusterScanArgs,
    ) -> RedisResult<Value> {
        // Clone arguments before the async block (ScanStateRC is Arc, clone is cheap)
        let scan_state_cursor_clone = scan_state_cursor.clone();
        let cluster_scan_args_clone = cluster_scan_args.clone(); // Assuming ClusterScanArgs is Clone

        // Check and initialize if lazy *inside* the async block
        let client = self.get_or_initialize_client().await?;

        match client {
            ClientWrapper::Standalone(_) => {
                unreachable!("Cluster scan is not supported in standalone mode")
            }
            ClientWrapper::Cluster { mut client, .. } => {
                let (cursor, keys) = client
                    .cluster_scan(scan_state_cursor_clone, cluster_scan_args_clone) // Use clones
                    .await?;
                let cluster_cursor_id = if cursor.is_finished() {
                    Value::BulkString(FINISHED_SCAN_CURSOR.into()) // Use constant
                } else {
                    Value::BulkString(insert_cluster_scan_cursor(cursor).into())
                };
                Ok(Value::Array(vec![cluster_cursor_id, Value::Array(keys)]))
            }
            // Lazy case is now handled by the initial check
            ClientWrapper::Lazy(_) => unreachable!("Lazy client should have been initialized"),
        }
    }

    fn get_transaction_values(
        pipeline: &redis::Pipeline,
        mut values: Vec<Value>,
        command_count: usize,
        offset: usize,
        raise_on_error: bool,
    ) -> RedisResult<Value> {
        assert_eq!(values.len(), 1);
        let value = values.pop();
        let values = match value {
            Some(Value::Array(values)) => values,
            Some(Value::Nil) => {
                return Ok(Value::Nil);
            }
            Some(value) => {
                if offset == 2 {
                    vec![value]
                } else {
                    return Err((
                        ErrorKind::ResponseError,
                        "Received non-array response for transaction",
                        format!("(response was {:?})", get_value_type(&value)),
                    )
                        .into());
                }
            }
            _ => {
                return Err((
                    ErrorKind::ResponseError,
                    "Received empty response for transaction",
                )
                    .into());
            }
        };
        Self::convert_pipeline_values_to_expected_types(
            pipeline,
            values,
            command_count,
            raise_on_error,
        )
    }

    fn convert_pipeline_values_to_expected_types(
        pipeline: &redis::Pipeline,
        values: Vec<Value>,
        command_count: usize,
        raise_on_error: bool,
    ) -> RedisResult<Value> {
        let values = values
            .into_iter()
            .map(|value| {
                if raise_on_error {
                    value.extract_error()
                } else {
                    Ok(value)
                }
            })
            .zip(
                pipeline
                    .cmd_iter()
                    .map(|cmd| expected_type_for_cmd(cmd.as_ref())),
            )
            .map(|(value, expected_type)| convert_to_expected_type(value?, expected_type))
            .try_fold(
                Vec::with_capacity(command_count),
                |mut acc, result| -> RedisResult<_> {
                    acc.push(result?);
                    Ok(acc)
                },
            )?;
        Ok(Value::Array(values))
    }

    /// Send a pipeline to the server.
    /// Transaction is a batch of commands that are sent in a single request.
    /// Unlike a pipelines, transactions are atomic, and in cluster mode, the key-based commands must route to the same slot.
    pub fn send_transaction<'a>(
        &'a mut self,
        pipeline: &'a redis::Pipeline,
        routing: Option<RoutingInfo>,
        transaction_timeout: Option<u32>,
        raise_on_error: bool,
    ) -> redis::RedisFuture<'a, Value> {
        Box::pin(async move {
            let client = self.get_or_initialize_client().await?;

            let command_count = pipeline.cmd_iter().count();
            // The offset is set to command_count + 1 to account for:
            // 1. The first command, which is the "MULTI" command, that returns "OK"
            // 2. The "QUEUED" responses for each of the commands in the pipeline (before EXEC)
            // After these initial responses (OK and QUEUED), we expect a single response,
            // which is an array containing the results of all the commands in the pipeline.
            let offset = command_count + 1;

            run_with_timeout(
                Some(to_duration(transaction_timeout, self.request_timeout)),
                async move {
                    match client {
                        ClientWrapper::Standalone(mut client) => {
                            let values = client.send_pipeline(pipeline, offset, 1).await?;
                            Client::get_transaction_values(
                                pipeline,
                                values,
                                command_count,
                                offset,
                                raise_on_error,
                            )
                        }
                        ClientWrapper::Cluster { mut client, .. } => {
                            let values = match routing {
                                Some(RoutingInfo::SingleNode(route)) => {
                                    client
                                        .route_pipeline(pipeline, offset, 1, Some(route), None)
                                        .await?
                                }
                                _ => {
                                    client
                                        .req_packed_commands(pipeline, offset, 1, None)
                                        .await?
                                }
                            };
                            Client::get_transaction_values(
                                pipeline,
                                values,
                                command_count,
                                offset,
                                raise_on_error,
                            )
                        }
                        ClientWrapper::Lazy(_) => {
                            unreachable!("Lazy client should have been initialized")
                        }
                    }
                },
            )
            .await
        })
    }

    /// Send a pipeline to the server.
    /// Pipeline is a batch of commands that are sent in a single request.
    /// Unlike a transaction, the commands are not executed atomically, and in cluster mode, the commands can be sent to different nodes.
    ///
    /// The `raise_on_error` parameter determines whether the pipeline should raise an error if any of the commands in the pipeline fail, or return the error as part of the response.
    /// - `pipeline_retry_strategy`: Configures the retry behavior for pipeline commands.
    ///   - If `retry_server_error` is `true`, failed commands with a retriable `RetryMethod` will be retried,
    ///     potentially causing reordering within the same slot.
    ///     ⚠️ **Caution**: This may lead to commands being executed in a different order than originally sent,
    ///     which could affect operations that rely on strict execution sequence.
    ///   - If `retry_connection_error` is `true`, sub-pipeline requests will be retried on connection errors.
    ///     ⚠️ **Caution**: Retrying after a connection error may result in duplicate executions, since the server might have already received and processed the request before the error occurred.
    ///     TODO: add wiki link.
    pub fn send_pipeline<'a>(
        &'a mut self,
        pipeline: &'a redis::Pipeline,
        routing: Option<RoutingInfo>,
        raise_on_error: bool,
        pipeline_timeout: Option<u32>,
        pipeline_retry_strategy: PipelineRetryStrategy,
    ) -> redis::RedisFuture<'a, Value> {
        Box::pin(async move {
            let client = self.get_or_initialize_client().await?;

            let command_count = pipeline.cmd_iter().count();
            if pipeline.is_empty() {
                return Err(RedisError::from((
                    ErrorKind::ResponseError,
                    "Received empty pipeline",
                )));
            }

            run_with_timeout(
                Some(to_duration(pipeline_timeout, self.request_timeout)),
                async move {
                    let values = match client {
                        ClientWrapper::Standalone(mut client) => {
                            client.send_pipeline(pipeline, 0, command_count).await
                        }

                        ClientWrapper::Cluster { mut client, .. } => match routing {
                            Some(RoutingInfo::SingleNode(route)) => {
                                client
                                    .route_pipeline(
                                        pipeline,
                                        0,
                                        command_count,
                                        Some(route),
                                        Some(pipeline_retry_strategy),
                                    )
                                    .await
                            }
                            _ => {
                                client
                                    .route_pipeline(
                                        pipeline,
                                        0,
                                        command_count,
                                        None,
                                        Some(pipeline_retry_strategy),
                                    )
                                    .await
                            }
                        },
                        ClientWrapper::Lazy(_) => {
                            unreachable!("Lazy client should have been initialized")
                        }
                    }?;

                    Client::convert_pipeline_values_to_expected_types(
                        pipeline,
                        values,
                        command_count,
                        raise_on_error,
                    )
                },
            )
            .await
        })
    }

    pub async fn invoke_script<'a>(
        &'a mut self,
        hash: &'a str,
        keys: &Vec<&[u8]>,
        args: &Vec<&[u8]>,
        routing: Option<RoutingInfo>,
    ) -> redis::RedisResult<Value> {
        let _ = self.get_or_initialize_client().await?;

        let mut eval = eval_cmd(hash, keys, args);
        let result = self.send_command(&mut eval, routing.clone()).await;
        let Err(err) = result else {
            return result;
        };
        if err.kind() == ErrorKind::NoScriptError {
            let Some(code) = get_script(hash) else {
                return Err(err);
            };
            let mut load = load_cmd(&code);
            self.send_command(&mut load, None).await?;
            self.send_command(&mut eval, routing).await
        } else {
            Err(err)
        }
    }

    /// Reserve an inflight slot, returning a tracker whose Drop releases it.
    /// Returns `None` if no slots available.
    pub fn reserve_inflight_request(&self) -> Option<redis::cluster_async::InflightRequestTracker> {
        redis::cluster_async::InflightRequestTracker::try_new(
            self.inflight_requests_allowed.clone(),
        )
    }

    /// Returns the current number of available inflight slots.
    /// For testing/observability — the inflight limit minus this value equals
    /// the number of commands currently held by the internal pipeline.
    pub fn available_inflight_count(&self) -> isize {
        self.inflight_requests_allowed.load(Ordering::Relaxed)
    }

    /// Returns true if the client-wide circuit breaker allows requests.
    /// If CB is not configured, always returns true.
    /// Fast path (Closed state) is a single atomic load. Open state may acquire a lock
    /// to check if transition to HalfOpen is needed.
    #[inline]
    pub fn is_circuit_breaker_healthy(&self) -> bool {
        self.circuit_breaker
            .as_ref()
            .is_none_or(|cb| cb.is_healthy())
    }

    /// Update the password used to authenticate with the servers.
    /// If None is passed, the password will be removed.
    /// If `immediate_auth` is true, the password will be used to authenticate with the servers immediately using the `AUTH` command.
    /// The default behavior is to update the password without authenticating immediately.
    /// If the password is empty or None, and `immediate_auth` is true, the password will be updated and an error will be returned.
    pub async fn update_connection_password(
        &mut self,
        password: Option<String>,
        immediate_auth: bool,
    ) -> RedisResult<Value> {
        let timeout = self.request_timeout;
        // The password update operation is wrapped in a timeout to prevent it from blocking indefinitely.
        // If the operation times out, an error is returned.
        // Since the password update operation is not a command that go through the regular command pipeline,
        // it is not have the regular timeout handling, as such we need to handle it separately.
        match tokio::time::timeout(timeout, async {
            let mut client = self.get_or_initialize_client().await?;
            match client {
                ClientWrapper::Standalone(ref mut client) => {
                    client.update_connection_password(password.clone()).await
                }
                ClientWrapper::Cluster { ref mut client, .. } => {
                    client.update_connection_password(password.clone()).await
                }
                ClientWrapper::Lazy(_) => unreachable!("Lazy client should have been initialized"),
            }
        })
        .await
        {
            Ok(result) => {
                if immediate_auth {
                    self.send_immediate_auth(password).await
                } else {
                    result
                }
            }
            Err(_elapsed) => Err(RedisError::from((
                ErrorKind::IoError,
                "Password update operation timed out, please check the connection",
            ))),
        }
    }

    /// Send AUTH command using IAM token (preferred) or the provided password
    async fn send_immediate_auth(&mut self, password: Option<String>) -> RedisResult<Value> {
        // Determine the password to use for authentication
        let pass = if let Some(ref password) = password {
            if password.is_empty() {
                return Err(RedisError::from((
                    ErrorKind::UserOperationError,
                    "Empty password provided for authentication",
                )));
            }
            log_debug("send_immediate_auth", "Using password for authentication");
            password.to_string()
        } else {
            return Err(RedisError::from((
                ErrorKind::UserOperationError,
                "No password provided for authentication",
            )));
        };

        let routing = RoutingInfo::MultiNode((
            MultipleNodeRoutingInfo::AllNodes,
            Some(ResponsePolicy::AllSucceeded),
        ));

        let username = self.get_username().await.ok().flatten();

        let mut cmd = redis::cmd("AUTH");
        if let Some(username) = username {
            cmd.arg(&username);
        }
        cmd.arg(pass);
        self.send_command(&mut cmd, Some(routing)).await
    }

    /// Returns the username if one was configured during client creation. Otherwise, returns None.
    pub async fn get_username(&mut self) -> RedisResult<Option<String>> {
        let client = self.get_or_initialize_client().await?;

        match client {
            ClientWrapper::Cluster { mut client, .. } => match client.get_username().await {
                Ok(Value::SimpleString(username)) => Ok(Some(username)),
                Ok(Value::Nil) => Ok(None),
                Ok(other) => Err(RedisError::from((
                    ErrorKind::ClientError,
                    "Unexpected type",
                    format!("Expected SimpleString or Nil, got: {other:?}"),
                ))),
                Err(e) => Err(RedisError::from((
                    ErrorKind::ResponseError,
                    "Error getting username",
                    format!("Received error - {e:?}."),
                ))),
            },
            ClientWrapper::Standalone(client) => Ok(client.get_username()),
            ClientWrapper::Lazy(_) => unreachable!("Lazy client should have been initialized"),
        }
    }

    /// Create an `IAMTokenManager` when IAM auth is configured.
    ///
    /// Client retrieves tokens on-demand during command execution.
    async fn create_iam_token_manager(
        auth_info: &crate::client::types::AuthenticationInfo,
    ) -> Result<Option<std::sync::Arc<crate::iam::IAMTokenManager>>, ConnectionError> {
        if let Some(iam_config) = &auth_info.iam_config {
            if let Some(username) = &auth_info.username {
                match crate::iam::IAMTokenManager::new(
                    iam_config.cluster_name.clone(),
                    username.clone(),
                    iam_config.region.clone(),
                    iam_config.service_type,
                    iam_config.refresh_interval_seconds,
                    iam_config.credentials_provider.clone(),
                )
                .await
                {
                    Ok(mut token_manager) => {
                        token_manager.start_refresh_task();
                        Ok(Some(std::sync::Arc::new(token_manager)))
                    }
                    Err(e) => Err(ConnectionError::IAMError(e.to_string())),
                }
            } else {
                Err(ConnectionError::IAMError(
                    "IAM authentication requires a username".to_string(),
                ))
            }
        } else {
            Ok(None)
        }
    }

    /// Manually refresh the IAM token and update connection authentication
    ///
    /// This method generates a new IAM token using the configured IAM token manager
    /// and immediately authenticates all connections with the new token.
    ///
    /// # Returns
    /// - `Ok(())` if the token was successfully refreshed and authentication succeeded
    /// - `Err(RedisError)` if no IAM token manager is configured, token generation fails,
    ///   or authentication with the new token fails.
    pub async fn refresh_iam_token(&mut self) -> RedisResult<()> {
        // Check if IAM token manager is available
        let iam_manager = self.iam_token_manager.as_ref().ok_or_else(|| {
            RedisError::from((
                ErrorKind::ClientError,
                "No IAM token manager configured - IAM token refresh requires IAM authentication to be enabled during client creation",
            ))
        })?;

        // Refresh the token using the IAM token manager
        iam_manager.refresh_token().await;
        Ok(())
    }
}
/// Trait for executing PubSub commands on the internal client wrapper
pub trait PubSubCommandApplier: Send + Sync {
    /// Send a subscription command (SUBSCRIBE, UNSUBSCRIBE, etc.)
    /// If routing is provided, use it; otherwise use default routing logic
    fn apply_pubsub_command<'a>(
        &'a mut self,
        cmd: &'a mut Cmd,
        routing: Option<SingleNodeRoutingInfo>,
    ) -> Pin<Box<dyn Future<Output = RedisResult<Value>> + Send + 'a>>;
}

/// Implement the trait for ClientWrapper
impl PubSubCommandApplier for ClientWrapper {
    fn apply_pubsub_command<'a>(
        &'a mut self,
        cmd: &'a mut Cmd,
        routing: Option<SingleNodeRoutingInfo>,
    ) -> Pin<Box<dyn Future<Output = RedisResult<Value>> + Send + 'a>> {
        Box::pin(async move {
            match self {
                ClientWrapper::Standalone(client) => {
                    // For standalone mode, send unsubscribe commands to all nodes.
                    // This handles ElastiCache scenarios where DNS address could change
                    // So we can't know which node we subscribed to
                    if let Some(command) = cmd.command() {
                        let cmd_upper = command.to_ascii_uppercase();
                        if cmd_upper == b"UNSUBSCRIBE" || cmd_upper == b"PUNSUBSCRIBE" {
                            return client
                                .send_request_to_all_nodes(cmd, Some(ResponsePolicy::AllSucceeded))
                                .await;
                        }
                    }
                    client.send_command(cmd).await
                }
                ClientWrapper::Cluster { client, .. } => {
                    let final_routing = routing
                        .map(RoutingInfo::SingleNode)
                        .or_else(|| RoutingInfo::for_routable(cmd))
                        .unwrap_or(RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random));
                    client.route_command(cmd, final_routing).await
                }
                ClientWrapper::Lazy(_) => Err(RedisError::from((
                    ErrorKind::ClientError,
                    "Client not initialized",
                ))),
            }
        })
    }
}

fn load_cmd(code: &[u8]) -> Cmd {
    let mut cmd = redis::cmd("SCRIPT");
    cmd.arg("LOAD").arg(code);
    cmd
}

fn eval_cmd(hash: &str, keys: &Vec<&[u8]>, args: &Vec<&[u8]>) -> Cmd {
    let mut cmd = redis::cmd("EVALSHA");
    cmd.arg(hash).arg(keys.len());
    for key in keys {
        cmd.arg(key);
    }
    for arg in args {
        cmd.arg(arg);
    }
    cmd
}

pub(crate) fn to_duration(time_in_millis: Option<u32>, default: Duration) -> Duration {
    time_in_millis
        .map(|val| Duration::from_millis(val as u64))
        .unwrap_or(default)
}

/// Combine the provided root/CA certificate PEM blobs into a single buffer,
/// rejecting any empty entry. Returns `Ok(None)` when no root certs are provided.
///
/// Shared by the standalone and cluster connection paths so the combining rule and
/// the empty-entry validation live in one place. Callers run this *before* building
/// the cert-reload manager, so the manager never sees unvalidated root material.
/// (Root/CA reload itself is out of scope; see #6529.)
pub(super) fn combine_root_certs(root_certs: &[Vec<u8>]) -> RedisResult<Option<Vec<u8>>> {
    if root_certs.is_empty() {
        return Ok(None);
    }
    let mut combined = Vec::new();
    for cert in root_certs {
        if cert.is_empty() {
            return Err(RedisError::from((
                ErrorKind::InvalidClientConfig,
                "Root certificate cannot be empty byte string",
            )));
        }
        combined.extend_from_slice(cert);
    }
    Ok(Some(combined))
}

/// Validate the combination of byte-based and path-based mTLS client material.
///
/// Shared by the standalone and cluster connection paths. The byte-based cert/key
/// must be provided together, the path-based cert/key must be provided together,
/// and the two families must not be mixed in the same request (byte-based lands the
/// static, non-reloading material; path-based lands the reloadable material).
pub(super) fn validate_client_cert_config(
    has_client_cert_bytes: bool,
    has_client_key_bytes: bool,
    has_cert_path: bool,
    has_key_path: bool,
) -> RedisResult<()> {
    if has_client_cert_bytes != has_client_key_bytes {
        return Err(RedisError::from((
            ErrorKind::InvalidClientConfig,
            "client_cert and client_key must both be provided or both be empty",
        )));
    }
    if has_cert_path != has_key_path {
        return Err(RedisError::from((
            ErrorKind::InvalidClientConfig,
            "client_cert_path and client_key_path must both be provided or both be empty",
        )));
    }
    if has_cert_path && has_client_cert_bytes {
        return Err(RedisError::from((
            ErrorKind::InvalidClientConfig,
            "client certificate path and client certificate bytes cannot both be provided; use one or the other",
        )));
    }
    Ok(())
}

/// Build a [`crate::tls_reload::CertReloadManager`] for a cluster client when
/// path-based mTLS is configured, starting its background reload task if reload is
/// enabled. Returns `Ok(None)` when no cert paths are configured. `root_cert` is the
/// already-combined-and-validated root material (see [`combine_root_certs`]).
async fn build_cluster_cert_material_manager(
    request: &ConnectionRequest,
    root_cert: Option<Vec<u8>>,
) -> RedisResult<Option<Arc<crate::tls_reload::CertReloadManager>>> {
    let (Some(cert_path), Some(key_path)) = (
        request.client_cert_path.as_ref(),
        request.client_key_path.as_ref(),
    ) else {
        return Ok(None);
    };

    let interval_seconds = request
        .cert_reload
        .as_ref()
        .filter(|cfg| cfg.enabled)
        .map(|cfg| cfg.interval_seconds);

    let mut manager = crate::tls_reload::CertReloadManager::new(
        cert_path.into(),
        key_path.into(),
        root_cert,
        interval_seconds.flatten(),
    )
    .await
    .map_err(|err| {
        RedisError::from((
            ErrorKind::InvalidClientConfig,
            "TLS certificate reload configuration error",
            err.to_string(),
        ))
    })?;

    if interval_seconds.is_some() {
        manager.start_reload_task();
    }

    Ok(Some(Arc::new(manager)))
}

#[allow(clippy::type_complexity)]
async fn create_cluster_client(
    request: ConnectionRequest,
    push_sender: Option<mpsc::UnboundedSender<PushInfo>>,
    iam_token_manager: Option<&Arc<crate::iam::IAMTokenManager>>,
    pubsub_synchronizer: Arc<dyn crate::pubsub::PubSubSynchronizer>,
) -> RedisResult<(
    redis::cluster_async::ClusterConnection,
    Option<Arc<crate::tls_reload::CertReloadManager>>,
)> {
    let tls_mode = request.tls_mode.unwrap_or_default();

    let valkey_connection_info = get_valkey_connection_info(&request, iam_token_manager).await;

    let has_root_certs = !request.root_certs.is_empty();
    let has_client_cert = !request.client_cert.is_empty();
    let has_client_key = !request.client_key.is_empty();
    let has_cert_path = request.client_cert_path.is_some();
    let has_key_path = request.client_key_path.is_some();
    validate_client_cert_config(has_client_cert, has_client_key, has_cert_path, has_key_path)?;

    // Combine + validate the root certs first (fail fast on an empty entry) so the
    // cert-reload manager, built next, never sees unvalidated root material.
    let root_cert_bytes = combine_root_certs(&request.root_certs)?;

    // Build the certificate reload manager when path-based mTLS is configured; it
    // validates the initial material and, if reload is enabled, drives the
    // background re-read task. The handle is wired into the cluster reconnect loop
    // so rotated certificates are adopted on reconnect (see
    // `refresh_cert_params_in_cluster_params`).
    let cert_material_manager =
        build_cluster_cert_material_manager(&request, root_cert_bytes.clone()).await?;
    let cert_material_handle = cert_material_manager.as_ref().map(|m| m.get_handle());

    // `tls_params` seeds the initial nodes and (when present) is handed to the
    // cluster builder directly. `tls_certificates` carries the raw byte material for
    // the byte-based case, which the builder re-parses at connect time. For the
    // path-based case we intentionally leave `tls_certificates` as `None` and pass
    // the manager's already-validated params instead, so the builder does not
    // re-read/re-parse the files (which would skip the key-match check and could
    // observe a torn rotation).
    let (tls_params, tls_certificates) = if let Some(manager) = &cert_material_manager {
        if tls_mode == TlsMode::NoTls {
            return Err(RedisError::from((
                ErrorKind::InvalidClientConfig,
                "TLS certificates provided but TLS is disabled",
            )));
        }
        // Path-based mTLS: reuse the manager's validated params as the single source
        // of truth for the initial cluster connection.
        (Some(manager.get_params().await), None)
    } else if has_root_certs || has_client_cert || has_client_key {
        if tls_mode == TlsMode::NoTls {
            return Err(RedisError::from((
                ErrorKind::InvalidClientConfig,
                "TLS certificates provided but TLS is disabled",
            )));
        }

        let client_tls = if has_client_cert && has_client_key {
            Some(redis::ClientTlsConfig {
                client_cert: request.client_cert.clone(),
                client_key: request.client_key.clone(),
            })
        } else {
            None
        };

        let tls_certs = TlsCertificates {
            client_tls,
            root_cert: root_cert_bytes,
        };
        let params = retrieve_tls_certificates(tls_certs.clone())?;
        (Some(params), Some(tls_certs))
    } else {
        (None, None)
    };
    let periodic_topology_checks = match request.periodic_checks {
        Some(PeriodicCheck::Disabled) => None,
        Some(PeriodicCheck::Enabled) => Some(DEFAULT_PERIODIC_TOPOLOGY_CHECKS_INTERVAL),
        Some(PeriodicCheck::ManualInterval(interval)) => Some(interval),
        None => Some(DEFAULT_PERIODIC_TOPOLOGY_CHECKS_INTERVAL),
    };
    let connection_timeout = request.get_connection_timeout();
    let address_resolver = &request.address_resolver;
    let initial_nodes: Vec<_> = request
        .addresses
        .into_iter()
        .map(|address| {
            get_connection_info(
                &address,
                tls_mode,
                valkey_connection_info.clone(),
                tls_params.clone(),
                address_resolver.as_ref(),
            )
        })
        .collect();

    let mut builder = redis::cluster::ClusterClientBuilder::new(initial_nodes)
        .connection_timeout(connection_timeout)
        .retries(DEFAULT_RETRIES);
    let read_from_strategy = request.read_from.unwrap_or_default();
    builder = builder.read_from(match read_from_strategy {
        ReadFrom::AZAffinity(az) => ReadFromReplicaStrategy::AZAffinity(az),
        ReadFrom::AZAffinityReplicasAndPrimary(az) => {
            ReadFromReplicaStrategy::AZAffinityReplicasAndPrimary(az)
        }
        ReadFrom::PreferReplica => ReadFromReplicaStrategy::RoundRobin,
        ReadFrom::AllNodes => ReadFromReplicaStrategy::AllNodes,
        ReadFrom::AZAffinityAllNodes(az) => ReadFromReplicaStrategy::AZAffinityAllNodes(az),
        ReadFrom::Primary => ReadFromReplicaStrategy::AlwaysFromPrimary,
    });
    if let Some(interval_duration) = periodic_topology_checks {
        builder = builder.periodic_topology_checks(interval_duration);
    }
    builder = builder.use_protocol(request.protocol.unwrap_or_default());
    builder = builder.database_id(valkey_connection_info.db);
    builder = builder.cache(valkey_connection_info.cache);
    builder = builder.server_assisted_cache(valkey_connection_info.server_assisted_cache);
    if let Some(client_name) = valkey_connection_info.client_name {
        builder = builder.client_name(client_name);
    }
    if let Some(lib_name) = valkey_connection_info.lib_name {
        builder = builder.lib_name(lib_name);
    }
    if let Some(lib_ver) = valkey_connection_info.lib_ver {
        builder = builder.lib_ver(lib_ver);
    }
    if tls_mode != TlsMode::NoTls {
        let tls = if tls_mode == TlsMode::SecureTls {
            redis::cluster::TlsMode::Secure
        } else {
            redis::cluster::TlsMode::Insecure
        };
        builder = builder.tls(tls);
        if let Some(certs) = tls_certificates {
            // Byte-based mTLS: the builder re-parses these at connect time.
            builder = builder.certs(certs);
        } else if let Some(params) = tls_params {
            // Path-based mTLS: hand the manager's already-validated params to the
            // builder so it reuses them verbatim instead of re-reading from disk.
            builder = builder.tls_params(params);
        }
    }

    let retry_strategy = match request.connection_retry_strategy {
        Some(strategy) => RetryStrategy::new(
            strategy.exponent_base,
            strategy.factor,
            strategy.number_of_retries,
            strategy.jitter_percent,
        ),
        None => RetryStrategy::default(),
    };
    builder = builder.reconnect_retry_strategy(retry_strategy);

    builder =
        builder.refresh_topology_from_initial_nodes(request.refresh_topology_from_initial_nodes);

    builder = builder.tcp_nodelay(request.tcp_nodelay);

    // Pass the address resolver to the builder for use during topology refresh
    if let Some(resolver) = address_resolver.clone() {
        builder = builder.address_resolver(resolver);
    }

    // Always use with Glide
    builder = builder.periodic_connections_checks(Some(CONNECTION_CHECKS_INTERVAL));

    let recovery_requests_queue_size = request
        .recovery_requests_queue_size
        .unwrap_or(DEFAULT_RECOVERY_REQUESTS_QUEUE_SIZE);
    builder = builder.recovery_requests_queue_size(recovery_requests_queue_size);

    let client = builder.build()?;
    let iam_token_provider: Option<Arc<dyn redis::IAMTokenProvider>> = iam_token_manager
        .map(|manager| Arc::new(manager.get_token_handle()) as Arc<dyn redis::IAMTokenProvider>);

    let cert_params_provider: Option<Arc<dyn redis::CertParamsProvider>> =
        cert_material_handle.map(|handle| Arc::new(handle) as Arc<dyn redis::CertParamsProvider>);

    let mut con = client
        .get_async_connection(
            push_sender,
            Some(pubsub_synchronizer),
            iam_token_provider,
            cert_params_provider,
        )
        .await?;

    // This validation ensures that sharded subscriptions are not applied to Redis engines older than version 7.0,
    // preventing scenarios where the client becomes inoperable or, worse, unaware that sharded pubsub messages are not being received.
    // The issue arises because `client.get_async_connection()` might succeed even if the engine does not support sharded pubsub.
    // For example, initial connections may exclude the target node for sharded subscriptions, allowing the creation to succeed,
    // but subsequent resubscription tasks will fail when `setup_connection()` cannot establish a connection to the node.
    //
    // One approach to handle this would be to check the engine version inside `setup_connection()` and skip applying sharded subscriptions.
    // However, this approach would leave the application unaware that the subscriptions were not applied, requiring the user to analyze logs to identify the issue.
    // Instead, we explicitly check the engine version here and fail the connection creation if it is incompatible with sharded subscriptions.

    if let Some(pubsub_subscriptions) = &request.pubsub_subscriptions
        && pubsub_subscriptions.contains_key(&redis::PubSubSubscriptionKind::Sharded)
    {
        let info_res = con
            .route_command(
                redis::cmd("INFO").arg("SERVER"),
                RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random),
            )
            .await?;
        let info_dict: InfoDict = FromRedisValue::from_redis_value(&info_res)?;
        match info_dict.get::<String>("redis_version") {
            Some(version) => match (Versioning::new(version), Versioning::new("7.0")) {
                (Some(server_ver), Some(min_ver)) => {
                    if server_ver < min_ver {
                        return Err(RedisError::from((
                            ErrorKind::InvalidClientConfig,
                            "Sharded subscriptions provided, but the engine version is < 7.0",
                        )));
                    }
                }
                _ => {
                    return Err(RedisError::from((
                        ErrorKind::ResponseError,
                        "Failed to parse engine version",
                    )));
                }
            },
            _ => {
                return Err(RedisError::from((
                    ErrorKind::ResponseError,
                    "Could not determine engine version from INFO result",
                )));
            }
        }
    }
    Ok((con, cert_material_manager))
}

#[derive(thiserror::Error)]
pub enum ConnectionError {
    Standalone(standalone_client::StandaloneClientConnectionError),
    Cluster(redis::RedisError),
    Timeout,
    IoError(std::io::Error),
    Configuration(String),
    IAMError(String),
}

impl std::fmt::Debug for ConnectionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Standalone(arg0) => f.debug_tuple("Standalone").field(arg0).finish(),
            Self::Cluster(arg0) => f.debug_tuple("Cluster").field(arg0).finish(),
            Self::IoError(arg0) => f.debug_tuple("IoError").field(arg0).finish(),
            Self::Timeout => write!(f, "Timeout"),
            Self::Configuration(arg0) => f.debug_tuple("Configuration").field(arg0).finish(),
            Self::IAMError(arg0) => f.debug_tuple("IAMError").field(arg0).finish(),
        }
    }
}

impl std::fmt::Display for ConnectionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ConnectionError::Standalone(err) => write!(f, "{err:?}"),
            ConnectionError::Cluster(err) => write!(f, "{err}"),
            ConnectionError::IoError(err) => write!(f, "{err}"),
            ConnectionError::Timeout => f.write_str("connection attempt timed out"),
            ConnectionError::Configuration(msg) => write!(f, "configuration error: {msg}"),
            ConnectionError::IAMError(msg) => write!(f, "IAM authentication error: {msg}"),
        }
    }
}

fn format_optional_value<T>(name: &'static str, value: Option<T>) -> String
where
    T: std::fmt::Display,
{
    if let Some(value) = value {
        format!("\n{name}: {value}")
    } else {
        String::new()
    }
}

fn sanitized_request_string(request: &ConnectionRequest) -> String {
    let addresses = request
        .addresses
        .iter()
        .map(|address| format!("{}:{}", address.host, address.port))
        .collect::<Vec<_>>()
        .join(", ");
    let tls_mode = request
        .tls_mode
        .map(|tls_mode| {
            format!(
                "\nTLS mode: {}",
                match tls_mode {
                    TlsMode::NoTls => "No TLS",
                    TlsMode::SecureTls => "Secure",
                    TlsMode::InsecureTls => "Insecure",
                }
            )
        })
        .unwrap_or_default();
    let cluster_mode = if request.cluster_mode_enabled {
        "\nCluster mode"
    } else {
        "\nStandalone mode"
    };
    let request_timeout = format!(
        "\nRequest timeout: {}",
        request
            .request_timeout
            .unwrap_or(DEFAULT_RESPONSE_TIMEOUT.as_millis() as u32)
    );
    let connection_timeout = format!(
        "\nConnection timeout: {}",
        request.get_connection_timeout().as_millis()
    );
    let database_id = format!("\ndatabase ID: {}", request.database_id);
    let rfr_strategy = request
        .read_from
        .clone()
        .map(|rfr| {
            format!(
                "\nRead from Replica mode: {}",
                match rfr {
                    ReadFrom::Primary => "Only primary",
                    ReadFrom::PreferReplica => "Prefer replica",
                    ReadFrom::AZAffinity(_) => "Prefer replica in user's availability zone",
                    ReadFrom::AZAffinityReplicasAndPrimary(_) =>
                        "Prefer replica and primary in user's availability zone",
                    ReadFrom::AllNodes => "All nodes (primary and replicas)",
                    ReadFrom::AZAffinityAllNodes(_) =>
                        "All nodes (primary and replicas) in user's availability zone",
                }
            )
        })
        .unwrap_or_default();
    let connection_retry_strategy = request.connection_retry_strategy.as_ref().map(|strategy|
            format!("\nreconnect backoff strategy: number of increasing duration retries: {}, base: {}, factor: {}, jitter: {:?}",
        strategy.number_of_retries, strategy.exponent_base, strategy.factor, strategy.jitter_percent)).unwrap_or_default();
    let protocol = request
        .protocol
        .map(|protocol| format!("\nProtocol: {protocol:?}"))
        .unwrap_or_default();
    let client_name = request
        .client_name
        .as_ref()
        .map(|client_name| format!("\nClient name: {client_name}"))
        .unwrap_or_default();
    let periodic_checks = if request.cluster_mode_enabled {
        match request.periodic_checks {
            Some(PeriodicCheck::Disabled) => "\nPeriodic Checks: Disabled".to_string(),
            Some(PeriodicCheck::Enabled) => format!(
                "\nPeriodic Checks: Enabled with default interval of {DEFAULT_PERIODIC_TOPOLOGY_CHECKS_INTERVAL:?}"
            ),
            Some(PeriodicCheck::ManualInterval(interval)) => format!(
                "\nPeriodic Checks: Enabled with manual interval of {:?}s",
                interval.as_secs()
            ),
            None => String::new(),
        }
    } else {
        String::new()
    };

    let pubsub_subscriptions = request
        .pubsub_subscriptions
        .as_ref()
        .map(|pubsub_subscriptions| format!("\nPubsub subscriptions: {pubsub_subscriptions:?}"))
        .unwrap_or_default();

    let inflight_requests_limit = format_optional_value(
        "\nInflight requests limit: {}",
        request.inflight_requests_limit,
    );

    let recovery_requests_queue_size = format_optional_value(
        "\nRecovery requests queue size: {}",
        request.recovery_requests_queue_size,
    );

    let node_discovery_mode = match request.node_discovery_mode {
        NodeDiscoveryMode::Standard => "\nNode discovery mode: Standard",
        NodeDiscoveryMode::Static => "\nNode discovery mode: Static",
        NodeDiscoveryMode::DiscoverAll => "\nNode discovery mode: DiscoverAll",
    };

    // Log only that path-based mTLS / reload is configured, and its interval —
    // never the certificate/key material itself.
    let client_cert_paths = if request.client_cert_path.is_some() {
        "\nmTLS client certificate: path-based (reloadable)"
    } else if !request.client_cert.is_empty() {
        "\nmTLS client certificate: byte-based (static)"
    } else {
        ""
    };
    let cert_reload = request
        .cert_reload
        .as_ref()
        .filter(|cfg| cfg.enabled)
        .map(|cfg| {
            format!(
                "\nmTLS certificate reload: enabled (interval: {}s)",
                cfg.interval_seconds.unwrap_or(300)
            )
        })
        .unwrap_or_default();

    format!(
        "\nAddresses: {addresses}{tls_mode}{cluster_mode}{request_timeout}{connection_timeout}{rfr_strategy}{connection_retry_strategy}{database_id}{protocol}{client_name}{periodic_checks}{pubsub_subscriptions}{inflight_requests_limit}{recovery_requests_queue_size}{node_discovery_mode}{client_cert_paths}{cert_reload}",
    )
}

/// Create a compression manager from the given configuration
/// Returns None if compression is disabled or not configured
fn create_compression_manager(
    compression_config: Option<CompressionConfig>,
) -> Result<Option<Arc<CompressionManager>>, ConnectionError> {
    let Some(config) = compression_config else {
        return Ok(None);
    };

    if !config.enabled {
        return Ok(None);
    }

    let backend: Box<dyn crate::compression::CompressionBackend> = match config.backend {
        CompressionBackendType::Zstd => Box::new(ZstdBackend::new()),
        CompressionBackendType::Lz4 => Box::new(Lz4Backend::new()),
    };

    let manager = CompressionManager::new(backend, config).map_err(|e| {
        ConnectionError::Configuration(format!("Failed to create compression manager: {}", e))
    })?;

    Ok(Some(Arc::new(manager)))
}

impl Client {
    pub async fn new(
        request: ConnectionRequest,
        push_sender: Option<mpsc::UnboundedSender<PushInfo>>,
    ) -> Result<Self, ConnectionError> {
        // Validate library name and version.
        if let Some(lib_name) = request.lib_name.as_deref() {
            validate_effective_lib_name(lib_name).map_err(ConnectionError::Configuration)?;
        }
        if let Some(lib_ver) = request.lib_ver.as_deref() {
            validate_effective_lib_ver(lib_ver).map_err(ConnectionError::Configuration)?;
        }

        // Add buffer to connection_timeout to allow inner connection logic to fully execute before the outer timeout triggers
        let client_creation_timeout = request.get_connection_timeout() + Duration::from_millis(500);

        log_info(
            "Connection configuration",
            sanitized_request_string(&request),
        );
        let request_timeout = to_duration(request.request_timeout, DEFAULT_RESPONSE_TIMEOUT);
        let inflight_requests_limit = request
            .inflight_requests_limit
            .unwrap_or(DEFAULT_MAX_INFLIGHT_REQUESTS);
        let inflight_requests_allowed = Arc::new(AtomicIsize::new(
            inflight_requests_limit.try_into().unwrap(),
        ));

        // Create compression manager from configuration
        let compression_manager = create_compression_manager(request.compression_config.clone())?;

        let reconciliation_interval = match request.pubsub_reconciliation_interval_ms {
            Some(ms) if ms > 0 => Some(Duration::from_millis(ms as u64)),
            _ => None,
        };

        let client_side_cache = request.client_side_cache.as_ref().map(|config| {
            get_or_create_cache(
                &config.cache_id,
                config.max_cache_kb,
                config.entry_ttl_ms,
                config.eviction_policy,
                config.enable_metrics,
            )
        });

        tokio::time::timeout(client_creation_timeout, async move {
            // Create shared, thread-safe wrapper for the internal client that starts as lazy
            // Arc<RwLock<T>> enables multiple async tasks to safely share and modify the client state
            let internal_client_arc =
                Arc::new(RwLock::new(ClientWrapper::Lazy(Box::new(LazyClient {
                    config: request.clone(),
                    push_sender: push_sender.clone(),
                }))));

            let initial_subscriptions = request.pubsub_subscriptions.clone();

            let pubsub_synchronizer = create_pubsub_synchronizer(
                push_sender.clone(),
                initial_subscriptions,
                request.cluster_mode_enabled,
                Arc::downgrade(&internal_client_arc),
                reconciliation_interval,
                request_timeout,
            )
            .await;

            // Extract connection metadata for OTel span attributes.
            // Port 0 is normalized to the default (6379) for OTel reporting.
            let otel_metadata = types::OTelMetadata {
                address: request
                    .addresses
                    .first()
                    .map(|addr| types::NodeAddress {
                        host: addr.host.clone(),
                        port: get_port(addr),
                    })
                    .unwrap_or_else(|| types::NodeAddress {
                        host: "unknown".to_string(),
                        port: 6379,
                    }),
                db_namespace: request.database_id.to_string(),
            };

            // Create the Client first without IAM token manager
            let inflight_limit: isize = inflight_requests_limit.try_into().unwrap();
            let inflight_log_interval = (inflight_limit / 10).max(1);
            let client = Self {
                shared: Arc::new(ClientShared {
                    internal_client: internal_client_arc.clone(),
                    request_timeout,
                    inflight_requests_allowed,
                    inflight_requests_limit: inflight_limit,
                    inflight_log_interval,
                    compression_manager: compression_manager.clone(),
                    pubsub_synchronizer: pubsub_synchronizer.clone(),
                    client_side_cache,
                    latency_tracker: Arc::new(crate::timeout_watchdog::LatencyTracker::new(4096)),
                    circuit_breaker: request.client_circuit_breaker.as_ref().map(|config| {
                        let defaults = circuit_breaker::ClientCircuitBreakerConfig::default();
                        Arc::new(circuit_breaker::ClientCircuitBreaker::new(
                            circuit_breaker::ClientCircuitBreakerConfig {
                                window_size: Duration::from_millis(if config.window_size_ms > 0 {
                                    config.window_size_ms as u64
                                } else {
                                    defaults.window_size.as_millis() as u64
                                }),
                                failure_rate_threshold: if config.failure_rate_threshold > 0.0 {
                                    config.failure_rate_threshold
                                } else {
                                    defaults.failure_rate_threshold
                                },
                                min_errors: if config.min_errors > 0 {
                                    config.min_errors
                                } else {
                                    defaults.min_errors
                                },
                                open_timeout: Duration::from_millis(
                                    if config.open_timeout_ms > 0 {
                                        config.open_timeout_ms as u64
                                    } else {
                                        defaults.open_timeout.as_millis() as u64
                                    },
                                ),
                                count_timeouts: config.count_timeouts,
                                consecutive_successes: if config.consecutive_successes > 0 {
                                    config.consecutive_successes
                                } else {
                                    defaults.consecutive_successes
                                },
                            },
                        ))
                    }),
                    current_database: Arc::new(AtomicU32::new(request.database_id as u32)),
                    is_cluster: request.cluster_mode_enabled,
                }),
                iam_token_manager: None,
                otel_metadata: Arc::new(otel_metadata),
            };

            let client_arc = Arc::new(RwLock::new(client));

            // Create IAM token manager if needed
            let iam_token_manager = if let Some(auth_info) = &request.authentication_info {
                Self::create_iam_token_manager(auth_info).await?
            } else {
                None
            };

            // Update the client with the IAM token manager
            {
                let mut client_guard = client_arc.write().await;
                client_guard.iam_token_manager = iam_token_manager.clone();
            }

            let is_lazy = request.lazy_connect;
            let internal_client = if is_lazy {
                ClientWrapper::Lazy(Box::new(LazyClient {
                    config: request,
                    push_sender,
                }))
            } else if request.cluster_mode_enabled {
                let (client, cert_material_manager) = create_cluster_client(
                    request,
                    push_sender,
                    iam_token_manager.as_ref(),
                    pubsub_synchronizer.clone(),
                )
                .await
                .map_err(ConnectionError::Cluster)?;
                ClientWrapper::Cluster {
                    client,
                    _cert_material_manager: cert_material_manager,
                }
            } else {
                ClientWrapper::Standalone(
                    StandaloneClient::create_client(
                        request,
                        push_sender,
                        iam_token_manager.as_ref(),
                        Some(pubsub_synchronizer.clone()),
                    )
                    .await
                    .map_err(ConnectionError::Standalone)?,
                )
            };

            // Update the internal client with the actual client
            {
                let mut guard = internal_client_arc.write().await;
                *guard = internal_client;
            }

            if !is_lazy {
                pubsub_synchronizer.trigger_reconciliation();
                if let Err(e) = pubsub_synchronizer.wait_for_sync(0, None, None, None).await {
                    log_error(
                        "Client::new",
                        format!(
                            "Failed to establish initial subscriptions within timeout: {:?}",
                            e
                        ),
                    );
                }
            }

            // Return the client from the Arc
            let client = {
                let client_guard = client_arc.read().await;
                client_guard.clone()
            };

            Ok(client)
        })
        .await
        .map_err(|_| ConnectionError::Timeout)?
    }

    /// Get the compression manager if compression is enabled
    ///
    /// # Returns
    /// * `Some(Arc<CompressionManager>)` - If compression is enabled and configured
    /// * `None` - If compression is disabled or not configured
    pub fn compression_manager(&self) -> Option<Arc<CompressionManager>> {
        self.compression_manager.clone()
    }

    /// Returns the configured request timeout for this client.
    pub fn get_request_timeout(&self) -> Duration {
        self.request_timeout
    }

    /// Returns the current database the client is operating on.
    /// Updated whenever SELECT is called. Scope connections use this to inherit
    /// the parent's current database at acquire time.
    pub fn current_database(&self) -> u32 {
        self.current_database.load(Ordering::Acquire)
    }

    /// Returns a reference to the per-client latency tracker (for watchdog diagnostics).
    pub fn latency_tracker(&self) -> &Arc<crate::timeout_watchdog::LatencyTracker> {
        &self.latency_tracker
    }

    /// Reset connection state to a clean baseline after a pool borrow is returned.
    ///
    /// Sends a batched pipeline (single round-trip) that selectively issues:
    /// - DISCARD — cancels any pending MULTI and implicitly UNWATCHes
    /// - SELECT <configured_db> — resets to the pool's configured database
    ///
    /// DISCARD is always safe to send (returns ERR if no MULTI active — we ignore it).
    /// SELECT is always sent to guarantee the connection is on the correct database.
    ///
    /// This ensures the next borrower gets a connection in a known-good state.
    ///
    /// Does not send UNSUBSCRIBE, so a borrower that subscribed leaves the
    /// connection in push-message mode for whoever gets it next. Callers must not
    /// subscribe on a pooled client; use the parent client's pub/sub API, which
    /// keeps its own dedicated connections.
    pub async fn reset_connection_state(&mut self, configured_db: u32) -> RedisResult<()> {
        // Send DISCARD — ignore ERR if no MULTI is active
        let _ = self.send_command(&mut redis::cmd("DISCARD"), None).await;

        // Send SELECT — this must succeed
        let mut select_cmd = redis::cmd("SELECT");
        select_cmd.arg(configured_db.to_string());
        self.send_command(&mut select_cmd, None).await?;

        Ok(())
    }

    /// Check if compression is enabled for this client
    ///
    /// # Returns
    /// * `true` if compression is enabled and configured
    /// * `false` if compression is disabled or not configured
    pub fn is_compression_enabled(&self) -> bool {
        self.compression_manager
            .as_ref()
            .map(|manager| manager.is_enabled())
            .unwrap_or(false)
    }

    /// Returns the initial connection address, used as the default
    /// OTel `server.address` span attribute.
    pub fn server_address(&self) -> &str {
        &self.otel_metadata.address.host
    }

    /// Returns the initial connection port, used as the default
    /// OTel `server.port` span attribute.
    pub fn server_port(&self) -> u16 {
        self.otel_metadata.address.port
    }

    pub fn db_namespace(&self) -> &str {
        &self.otel_metadata.db_namespace
    }
}

pub trait GlideClientForTests {
    fn send_command<'a>(
        &'a mut self,
        cmd: &'a mut Cmd,
        routing: Option<RoutingInfo>,
    ) -> redis::RedisFuture<'a, redis::Value>;
}

impl GlideClientForTests for Client {
    fn send_command<'a>(
        &'a mut self,
        cmd: &'a mut Cmd,
        routing: Option<RoutingInfo>,
    ) -> redis::RedisFuture<'a, redis::Value> {
        self.send_command(cmd, routing)
    }
}

impl GlideClientForTests for StandaloneClient {
    fn send_command<'a>(
        &'a mut self,
        cmd: &'a mut Cmd,
        _routing: Option<RoutingInfo>,
    ) -> redis::RedisFuture<'a, redis::Value> {
        self.send_command(cmd).boxed()
    }
}

// This is used for pubsub tests
impl GlideClientForTests for ClusterConnection {
    fn send_command<'a>(
        &'a mut self,
        cmd: &'a mut redis::Cmd,
        routing: Option<RoutingInfo>,
    ) -> redis::RedisFuture<'a, Value> {
        let final_routing =
            routing.unwrap_or(RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random));

        async move { self.route_command(cmd, final_routing).await }.boxed()
    }
}

impl Client {
    /// Create a Client wrapping an existing internal_client Arc and synchronizer.
    /// Used in tests to build a Client that shares state with an existing connection.
    #[cfg(feature = "test-util")]
    pub fn new_for_test(
        internal_client: Arc<RwLock<ClientWrapper>>,
        pubsub_synchronizer: Arc<dyn PubSubSynchronizer>,
    ) -> Self {
        use crate::client::types::{NodeAddress, OTelMetadata};
        Client {
            shared: Arc::new(ClientShared {
                internal_client,
                request_timeout: Duration::from_millis(1000),
                inflight_requests_allowed: Arc::new(AtomicIsize::new(1000)),
                inflight_requests_limit: 1000,
                inflight_log_interval: 100,
                compression_manager: None,
                pubsub_synchronizer,
                client_side_cache: None,
                latency_tracker: Arc::new(crate::timeout_watchdog::LatencyTracker::new(64)),
                circuit_breaker: None,
                current_database: Arc::new(AtomicU32::new(0)),
                is_cluster: false,
            }),
            iam_token_manager: None,
            otel_metadata: Arc::new(OTelMetadata {
                address: NodeAddress {
                    host: "localhost".to_string(),
                    port: 6379,
                },
                db_namespace: "0".to_string(),
            }),
        }
    }
}

/// Creates a no-connection (lazy) [`Client`] suitable for unit tests that need a
/// `GlideClient` value but do not issue any real network commands.
///
/// The client uses `lazy_connect = true` so no TCP connection is attempted at
/// construction time.  It **must not** be used to send actual Valkey commands.
#[cfg(test)]
pub fn create_test_glide_client() -> Client {
    use crate::client::types::{NodeAddress, OTelMetadata};
    use crate::pubsub::create_pubsub_synchronizer;
    use std::sync::atomic::AtomicIsize;
    use std::sync::atomic::AtomicU32;
    use tokio::sync::RwLock;

    let config = ConnectionRequest {
        database_id: 0,
        cluster_mode_enabled: false,
        addresses: vec![NodeAddress {
            host: "127.0.0.1".to_string(),
            port: 6379,
        }],
        lazy_connect: true,
        ..Default::default()
    };

    let lazy_client = LazyClient {
        config,
        push_sender: None,
    };

    // A throwaway runtime just to create the pubsub synchronizer.
    let rt = tokio::runtime::Runtime::new().expect("tokio runtime for test client");
    let pubsub_synchronizer = rt.block_on(create_pubsub_synchronizer(
        None,
        None,
        false,
        std::sync::Weak::new(),
        None,
        Duration::from_millis(250),
    ));

    Client {
        shared: Arc::new(ClientShared {
            internal_client: Arc::new(RwLock::new(ClientWrapper::Lazy(Box::new(lazy_client)))),
            request_timeout: Duration::from_millis(250),
            inflight_requests_allowed: Arc::new(AtomicIsize::new(1000)),
            inflight_requests_limit: 1000,
            inflight_log_interval: 100,
            compression_manager: None,
            pubsub_synchronizer,
            client_side_cache: None,
            latency_tracker: Arc::new(crate::timeout_watchdog::LatencyTracker::new(64)),
            circuit_breaker: None,
            current_database: Arc::new(AtomicU32::new(0)),
            is_cluster: false,
        }),
        iam_token_manager: None,
        otel_metadata: Arc::new(OTelMetadata {
            address: NodeAddress {
                host: "127.0.0.1".to_string(),
                port: 6379,
            },
            db_namespace: "0".to_string(),
        }),
    }
}

#[cfg(test)]
mod tests {
    use std::time::Duration;

    use redis::Cmd;

    use crate::client::types::{ConnectionRequest, NodeAddress, OTelMetadata};
    use crate::client::{
        BLOCKING_CMD_TIMEOUT_EXTENSION, ClientShared, RequestTimeoutOption, TimeUnit,
        get_request_timeout, is_blocking_command, is_blocking_command_name,
    };

    use super::{
        Client, ClientWrapper, ConnectionError, LazyClient, get_timeout_from_cmd_arg,
        validate_effective_lib_name, validate_effective_lib_ver,
    };
    use std::sync::Weak;

    #[test]
    fn test_validate_effective_lib_name_accepts_supported_values() {
        for lib_name in [
            "!",
            "~",
            "GlideRust",
            "client!#$%&'*+,-./:;<=>?@[\\]^_`{|}~",
            "GlideJava(framework:1.2)",
        ] {
            assert_eq!(
                validate_effective_lib_name(lib_name),
                Ok(()),
                "{lib_name:?}"
            );
        }
    }

    #[test]
    fn test_validate_effective_lib_name_rejects_invalid_values() {
        for lib_name in [
            "",
            "Glide Rust",
            "Glide\tRust",
            "Glide\nRust",
            "Glide\u{7f}Rust",
            "GlidéRust",
            "GlideRust(",
            "GlideRust)",
            ")GlideRust(",
            "GlideRust((tag))",
            "GlideRust(tag)(second)",
            "GlideRust(tag)suffix",
            "(tag)",
            "GlideRust()",
        ] {
            assert!(
                validate_effective_lib_name(lib_name).is_err(),
                "{lib_name:?} should be rejected"
            );
        }
    }

    #[tokio::test]
    async fn test_new_rejects_invalid_lib_name_before_lazy_client_creation() {
        let request = ConnectionRequest {
            addresses: vec![NodeAddress {
                host: "127.0.0.1".to_string(),
                port: 1,
            }],
            lazy_connect: true,
            lib_name: Some("invalid name".to_string()),
            ..Default::default()
        };

        let error = match Client::new(request, None).await {
            Ok(_) => panic!("invalid library name should fail client creation"),
            Err(error) => error,
        };

        assert!(matches!(error, ConnectionError::Configuration(_)));
        assert!(error.to_string().contains("library name"));
    }

    #[test]
    fn test_validate_effective_lib_ver_accepts_supported_values() {
        for lib_ver in ["unknown", "0.2.0", "1.2.3-rc.1+build.5", "255.255.255"] {
            assert_eq!(validate_effective_lib_ver(lib_ver), Ok(()), "{lib_ver:?}");
        }
    }

    #[test]
    fn test_validate_effective_lib_ver_rejects_invalid_values() {
        for lib_ver in [
            "",
            "1.2.3 ",
            "1.2 3",
            "1.2\t3",
            "1.2\n3",
            "1.2\u{7f}3",
            "1.2.é",
        ] {
            assert!(
                validate_effective_lib_ver(lib_ver).is_err(),
                "{lib_ver:?} should be rejected"
            );
        }
    }

    #[tokio::test]
    async fn test_new_rejects_invalid_lib_ver_before_lazy_client_creation() {
        let request = ConnectionRequest {
            addresses: vec![NodeAddress {
                host: "127.0.0.1".to_string(),
                port: 1,
            }],
            lazy_connect: true,
            lib_ver: Some("bad version".to_string()),
            ..Default::default()
        };

        let error = match Client::new(request, None).await {
            Ok(_) => panic!("invalid library version should fail client creation"),
            Err(error) => error,
        };

        assert!(matches!(error, ConnectionError::Configuration(_)));
        assert!(error.to_string().contains("library version"));
    }

    #[test]
    fn test_get_timeout_from_cmd_returns_correct_duration_int() {
        let mut cmd = Cmd::new();
        cmd.arg("BLPOP").arg("key1").arg("key2").arg("5");
        let result = get_timeout_from_cmd_arg(&cmd, cmd.args_iter().len() - 1, TimeUnit::Seconds);
        assert!(result.is_ok());
        assert_eq!(
            result.unwrap(),
            RequestTimeoutOption::BlockingCommand(Duration::from_secs_f64(
                5.0 + BLOCKING_CMD_TIMEOUT_EXTENSION
            ))
        );
    }

    #[test]
    fn test_get_timeout_from_cmd_returns_correct_duration_float() {
        let mut cmd = Cmd::new();
        cmd.arg("BLPOP").arg("key1").arg("key2").arg(0.5);
        let result = get_timeout_from_cmd_arg(&cmd, cmd.args_iter().len() - 1, TimeUnit::Seconds);
        assert!(result.is_ok());
        assert_eq!(
            result.unwrap(),
            RequestTimeoutOption::BlockingCommand(Duration::from_secs_f64(
                0.5 + BLOCKING_CMD_TIMEOUT_EXTENSION
            ))
        );
    }

    #[test]
    fn test_get_timeout_from_cmd_returns_correct_duration_milliseconds() {
        let mut cmd = Cmd::new();
        cmd.arg("XREAD").arg("BLOCK").arg("500").arg("key");
        let result = get_timeout_from_cmd_arg(&cmd, 2, TimeUnit::Milliseconds);
        assert!(result.is_ok());
        assert_eq!(
            result.unwrap(),
            RequestTimeoutOption::BlockingCommand(Duration::from_secs_f64(
                0.5 + BLOCKING_CMD_TIMEOUT_EXTENSION
            ))
        );
    }

    #[test]
    fn test_get_timeout_from_cmd_returns_err_when_timeout_isnt_passed() {
        let mut cmd = Cmd::new();
        cmd.arg("BLPOP").arg("key1").arg("key2").arg("key3");
        let result = get_timeout_from_cmd_arg(&cmd, cmd.args_iter().len() - 1, TimeUnit::Seconds);
        assert!(result.is_err());
        let err = result.unwrap_err();
        println!("{err:?}");
        assert!(err.to_string().to_lowercase().contains("index"), "{err}");
    }

    #[test]
    fn test_get_timeout_from_cmd_returns_err_when_timeout_is_larger_than_u32_max() {
        let mut cmd = Cmd::new();
        cmd.arg("BLPOP")
            .arg("key1")
            .arg("key2")
            .arg(u32::MAX as u64 + 1);
        let result = get_timeout_from_cmd_arg(&cmd, cmd.args_iter().len() - 1, TimeUnit::Seconds);
        assert!(result.is_err());
        let err = result.unwrap_err();
        println!("{err:?}");
        assert!(err.to_string().to_lowercase().contains("u32"), "{err}");
    }

    #[test]
    fn test_get_timeout_from_cmd_returns_err_when_timeout_is_negative() {
        let mut cmd = Cmd::new();
        cmd.arg("BLPOP").arg("key1").arg("key2").arg(-1);
        let result = get_timeout_from_cmd_arg(&cmd, cmd.args_iter().len() - 1, TimeUnit::Seconds);
        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(err.to_string().to_lowercase().contains("negative"), "{err}");
    }

    #[test]
    fn test_get_timeout_from_cmd_returns_no_timeout_when_zero_is_passed() {
        let mut cmd = Cmd::new();
        cmd.arg("BLPOP").arg("key1").arg("key2").arg(0);
        let result = get_timeout_from_cmd_arg(&cmd, cmd.args_iter().len() - 1, TimeUnit::Seconds);
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), RequestTimeoutOption::NoTimeout,);
    }

    #[test]
    fn test_get_request_timeout_with_blocking_command_returns_cmd_arg_timeout() {
        let mut cmd = Cmd::new();
        cmd.arg("BLPOP").arg("key1").arg("key2").arg("500");
        let result = get_request_timeout(&cmd, Duration::from_millis(100)).unwrap();
        assert_eq!(
            result,
            Some(Duration::from_secs_f64(
                500.0 + BLOCKING_CMD_TIMEOUT_EXTENSION
            ))
        );

        let mut cmd = Cmd::new();
        cmd.arg("XREADGROUP").arg("BLOCK").arg("500").arg("key");
        let result = get_request_timeout(&cmd, Duration::from_millis(100)).unwrap();
        assert_eq!(
            result,
            Some(Duration::from_secs_f64(
                0.5 + BLOCKING_CMD_TIMEOUT_EXTENSION
            ))
        );

        let mut cmd = Cmd::new();
        cmd.arg("BLMPOP").arg("0.857").arg("key");
        let result = get_request_timeout(&cmd, Duration::from_millis(100)).unwrap();
        assert_eq!(
            result,
            Some(Duration::from_secs_f64(
                0.857 + BLOCKING_CMD_TIMEOUT_EXTENSION
            ))
        );

        let mut cmd = Cmd::new();
        cmd.arg("WAIT").arg(1).arg("500");
        let result = get_request_timeout(&cmd, Duration::from_millis(500)).unwrap();
        assert_eq!(
            result,
            Some(Duration::from_secs_f64(
                0.5 + BLOCKING_CMD_TIMEOUT_EXTENSION
            ))
        );

        // WAITAOF
        let mut cmd = Cmd::new();
        cmd.arg("WAITAOF").arg(1).arg(1).arg("500");
        let result = get_request_timeout(&cmd, Duration::from_millis(500)).unwrap();
        assert_eq!(
            result,
            Some(Duration::from_secs_f64(
                0.5 + BLOCKING_CMD_TIMEOUT_EXTENSION
            ))
        );

        // Infinite block (0) — returns None (no client timeout)
        let mut cmd = Cmd::new();
        cmd.arg("BLPOP").arg("key").arg("0");
        let result = get_request_timeout(&cmd, Duration::from_millis(100)).unwrap();
        assert_eq!(result, None);
    }

    #[test]
    fn test_get_request_timeout_non_blocking_command_returns_default_timeout() {
        let mut cmd = Cmd::new();
        cmd.arg("SET").arg("key").arg("value").arg("PX").arg("500");
        let result = get_request_timeout(&cmd, Duration::from_millis(100)).unwrap();
        assert_eq!(result, Some(Duration::from_millis(100)));

        let mut cmd = Cmd::new();
        cmd.arg("XREADGROUP").arg("key");
        let result = get_request_timeout(&cmd, Duration::from_millis(100)).unwrap();
        assert_eq!(result, Some(Duration::from_millis(100)));
    }

    #[test]
    fn test_is_select_command_detects_valid_select_commands() {
        // Test detection of valid SELECT commands
        let client = create_test_client();

        // Test uppercase SELECT command
        let mut cmd = Cmd::new();
        cmd.arg("SELECT").arg("1");
        assert!(client.is_select_command(&cmd));

        // Test SELECT with different database IDs
        let mut cmd = Cmd::new();
        cmd.arg("SELECT").arg("0");
        assert!(client.is_select_command(&cmd));
    }

    #[test]
    fn test_extract_database_id_from_select() {
        // Test detection of valid SELECT commands
        let client = create_test_client();

        // Test uppercase SELECT command
        let mut cmd = Cmd::new();
        cmd.arg("SELECT").arg("1");
        assert_eq!(client.extract_database_id_from_select(&cmd), Ok(1));

        // Test SELECT with different database IDs
        let mut cmd = Cmd::new();
        cmd.arg("SELECT").arg("0");
        assert_eq!(client.extract_database_id_from_select(&cmd), Ok(0));
    }

    #[test]
    fn test_is_select_command_rejects_non_select_commands() {
        // Test rejection of non-SELECT commands
        let client = create_test_client();

        // Test common Redis commands
        let mut cmd = Cmd::new();
        cmd.arg("GET").arg("key");
        assert!(!client.is_select_command(&cmd));

        let mut cmd = Cmd::new();
        cmd.arg("SET").arg("key").arg("value");
        assert!(!client.is_select_command(&cmd));
    }

    #[test]
    fn test_is_select_command_case_normalization() {
        // Test that redis-rs normalizes commands to uppercase
        let client = create_test_client();

        // Test lowercase select (redis-rs normalizes to uppercase, so this works too)
        let mut cmd = Cmd::new();
        cmd.arg("select").arg("1");
        assert!(client.is_select_command(&cmd));
    }

    #[test]
    fn test_is_select_command_handles_empty_command() {
        // Test handling of empty or malformed commands
        let client = create_test_client();

        // Test empty command
        let cmd = Cmd::new();
        assert!(!client.is_select_command(&cmd));
    }

    /// Helper function to create a test client for unit tests
    fn create_test_client() -> Client {
        use crate::pubsub::create_pubsub_synchronizer;
        use std::sync::Arc;
        use std::sync::atomic::AtomicIsize;
        use std::sync::atomic::AtomicU32;
        use tokio::sync::RwLock;

        let config = ConnectionRequest {
            database_id: 0,
            cluster_mode_enabled: false,
            addresses: vec![NodeAddress {
                host: "127.0.0.1".to_string(),
                port: 6379,
            }],
            lazy_connect: true,
            ..Default::default()
        };

        let lazy_client = LazyClient {
            config,
            push_sender: None,
        };

        // Create runtime to initialize a stub pubsub synchronizer
        // We do this in order to keep the pubsub_synchronizer in the client struct non-optional.
        let rt = tokio::runtime::Runtime::new().unwrap();
        let pubsub_synchronizer = rt.block_on(create_pubsub_synchronizer(
            None,
            None,
            false,
            Weak::new(),
            None,
            Duration::from_millis(250),
        ));

        Client {
            shared: Arc::new(ClientShared {
                internal_client: Arc::new(RwLock::new(ClientWrapper::Lazy(Box::new(lazy_client)))),
                request_timeout: Duration::from_millis(250),
                inflight_requests_allowed: Arc::new(AtomicIsize::new(1000)),
                inflight_requests_limit: 1000,
                inflight_log_interval: 100,
                compression_manager: None,
                pubsub_synchronizer,
                client_side_cache: None,
                latency_tracker: Arc::new(crate::timeout_watchdog::LatencyTracker::new(64)),
                circuit_breaker: None,
                current_database: Arc::new(AtomicU32::new(0)),
                is_cluster: false,
            }),
            iam_token_manager: None,
            otel_metadata: Arc::new(OTelMetadata {
                address: NodeAddress {
                    host: "localhost".to_string(),
                    port: 6379,
                },
                db_namespace: "0".to_string(),
            }),
        }
    }

    #[test]
    fn test_is_client_set_name_command() {
        // Create a mock client for testing
        let client = create_test_client();

        // Test valid CLIENT SETNAME command
        let mut cmd = Cmd::new();
        cmd.arg("CLIENT").arg("SETNAME").arg("test_client");
        assert!(client.is_client_set_name_command(&cmd));

        // Test CLIENT SETNAME with different case (should work due to case-insensitive comparison)
        let mut cmd = Cmd::new();
        cmd.arg("client").arg("setname").arg("test_client");
        assert!(client.is_client_set_name_command(&cmd));

        // Test CLIENT command without SETNAME
        let mut cmd = Cmd::new();
        cmd.arg("CLIENT").arg("INFO");
        assert!(!client.is_client_set_name_command(&cmd));

        // Test non-CLIENT command
        let mut cmd = Cmd::new();
        cmd.arg("SET").arg("key").arg("value");
        assert!(!client.is_client_set_name_command(&cmd));

        // Test CLIENT SETNAME without client name argument
        let mut cmd = Cmd::new();
        cmd.arg("CLIENT").arg("SETNAME");
        assert!(client.is_client_set_name_command(&cmd));

        // Test CLIENT only
        let mut cmd = Cmd::new();
        cmd.arg("CLIENT");
        assert!(!client.is_client_set_name_command(&cmd));
    }

    #[test]
    fn test_extract_client_name_from_client_set_name() {
        // Test detection of valid CLIENT SETNAME commands
        let client = create_test_client();

        // Test uppercase CLIENT SETNAME command
        let mut cmd = Cmd::new();
        cmd.arg("CLIENT").arg("SETNAME").arg("test_name");
        assert_eq!(
            client.extract_client_name_from_client_set_name(&cmd),
            Some("test_name".to_string())
        );
    }

    #[test]
    fn test_is_auth_command() {
        let client = create_test_client();

        // Test valid AUTH command with password
        let mut cmd = Cmd::new();
        cmd.arg("AUTH").arg("password123");
        assert!(client.is_auth_command(&cmd));

        // Test AUTH command with username and password
        let mut cmd = Cmd::new();
        cmd.arg("AUTH").arg("myuser").arg("password123");
        assert!(client.is_auth_command(&cmd));

        // Test non-AUTH command
        let mut cmd = Cmd::new();
        cmd.arg("SET").arg("key").arg("value");
        assert!(!client.is_auth_command(&cmd));
    }

    #[test]
    fn test_extract_auth_info() {
        let client = create_test_client();

        // Test AUTH with password only
        let mut cmd = Cmd::new();
        cmd.arg("AUTH").arg("password123");
        let (username, password) = client.extract_auth_info(&cmd);
        assert_eq!(username, None);
        assert_eq!(password, Some("password123".to_string()));

        // Test AUTH with username and password
        let mut cmd = Cmd::new();
        cmd.arg("AUTH").arg("myuser").arg("password123");
        let (username, password) = client.extract_auth_info(&cmd);
        assert_eq!(username, Some("myuser".to_string()));
        assert_eq!(password, Some("password123".to_string()));

        // Test AUTH with no arguments (invalid)
        let mut cmd = Cmd::new();
        cmd.arg("AUTH");
        let (username, password) = client.extract_auth_info(&cmd);
        assert_eq!(username, None);
        assert_eq!(password, None);
    }

    #[test]
    fn test_is_hello_command() {
        let client = create_test_client();

        // Test valid HELLO command
        let mut cmd = Cmd::new();
        cmd.arg("HELLO").arg("3");
        assert!(client.is_hello_command(&cmd));

        // Test HELLO with AUTH
        let mut cmd = Cmd::new();
        cmd.arg("HELLO")
            .arg("3")
            .arg("AUTH")
            .arg("user")
            .arg("pass");
        assert!(client.is_hello_command(&cmd));

        // Test non-HELLO command
        let mut cmd = Cmd::new();
        cmd.arg("PING");
        assert!(!client.is_hello_command(&cmd));
    }

    #[test]
    fn test_extract_hello_info() {
        let client = create_test_client();

        // Test HELLO 3
        let mut cmd = Cmd::new();
        cmd.arg("HELLO").arg("3");
        let (protocol, username, password, client_name) = client.extract_hello_info(&cmd);
        assert_eq!(protocol, Some(redis::ProtocolVersion::RESP3));
        assert_eq!(username, None);
        assert_eq!(password, None);
        assert_eq!(client_name, None);

        // Test HELLO 2
        let mut cmd = Cmd::new();
        cmd.arg("HELLO").arg("2");
        let (protocol, username, password, client_name) = client.extract_hello_info(&cmd);
        assert_eq!(protocol, Some(redis::ProtocolVersion::RESP2));
        assert_eq!(username, None);
        assert_eq!(password, None);
        assert_eq!(client_name, None);

        // Test HELLO 3 AUTH username password
        let mut cmd = Cmd::new();
        cmd.arg("HELLO")
            .arg("3")
            .arg("AUTH")
            .arg("myuser")
            .arg("mypass");
        let (protocol, username, password, client_name) = client.extract_hello_info(&cmd);
        assert_eq!(protocol, Some(redis::ProtocolVersion::RESP3));
        assert_eq!(username, Some("myuser".to_string()));
        assert_eq!(password, Some("mypass".to_string()));
        assert_eq!(client_name, None);

        // Test HELLO 3 SETNAME myclient
        let mut cmd = Cmd::new();
        cmd.arg("HELLO").arg("3").arg("SETNAME").arg("myclient");
        let (protocol, username, password, client_name) = client.extract_hello_info(&cmd);
        assert_eq!(protocol, Some(redis::ProtocolVersion::RESP3));
        assert_eq!(username, None);
        assert_eq!(password, None);
        assert_eq!(client_name, Some("myclient".to_string()));

        // Test HELLO 3 AUTH user pass SETNAME myclient
        let mut cmd = Cmd::new();
        cmd.arg("HELLO")
            .arg("3")
            .arg("AUTH")
            .arg("myuser")
            .arg("mypass")
            .arg("SETNAME")
            .arg("myclient");
        let (protocol, username, password, client_name) = client.extract_hello_info(&cmd);
        assert_eq!(protocol, Some(redis::ProtocolVersion::RESP3));
        assert_eq!(username, Some("myuser".to_string()));
        assert_eq!(password, Some("mypass".to_string()));
        assert_eq!(client_name, Some("myclient".to_string()));

        // Test HELLO with invalid protocol version
        let mut cmd = Cmd::new();
        cmd.arg("HELLO").arg("99");
        let (protocol, username, password, client_name) = client.extract_hello_info(&cmd);
        assert_eq!(protocol, None);
        assert_eq!(username, None);
        assert_eq!(password, None);
        assert_eq!(client_name, None);
    }

    // ===== Edge case tests for blocking command timeout detection =====

    #[test]
    fn test_blocking_command_infinite_block_returns_none() {
        // BLPOP key 0 — infinite block → no client timeout
        let mut cmd = Cmd::new();
        cmd.arg("BLPOP").arg("key").arg("0");
        assert_eq!(
            get_request_timeout(&cmd, Duration::from_millis(1000)).unwrap(),
            None
        );

        // XREAD BLOCK 0 — infinite block
        let mut cmd = Cmd::new();
        cmd.arg("XREAD")
            .arg("BLOCK")
            .arg("0")
            .arg("STREAMS")
            .arg("s1")
            .arg("$");
        assert_eq!(
            get_request_timeout(&cmd, Duration::from_millis(1000)).unwrap(),
            None
        );
    }

    #[test]
    fn test_blocking_timeout_extends_beyond_block_duration() {
        // BLPOP key 5 — blocks 5s, timeout should be 5s + extension
        let mut cmd = Cmd::new();
        cmd.arg("BLPOP").arg("key").arg("5");
        let result = get_request_timeout(&cmd, Duration::from_millis(1000)).unwrap();
        let expected = Duration::from_secs_f64(5.0 + BLOCKING_CMD_TIMEOUT_EXTENSION);
        assert_eq!(result, Some(expected));
        assert!(expected > Duration::from_secs(5));
    }

    #[test]
    fn test_non_blocking_command_uses_default_timeout() {
        for cmd_name in &["SET", "GET", "DEL", "HGET", "LPUSH", "SADD", "PING"] {
            let mut cmd = Cmd::new();
            cmd.arg(*cmd_name).arg("key");
            let result = get_request_timeout(&cmd, Duration::from_millis(1000)).unwrap();
            assert_eq!(
                result,
                Some(Duration::from_millis(1000)),
                "{cmd_name} should use default timeout"
            );
        }
    }

    #[test]
    fn test_waitaof_detected_as_blocking() {
        let mut cmd = Cmd::new();
        cmd.arg("WAITAOF").arg(1).arg(1).arg("3000");
        let expected = Duration::from_secs_f64(3.0 + BLOCKING_CMD_TIMEOUT_EXTENSION);
        assert_eq!(
            get_request_timeout(&cmd, Duration::from_millis(1000)).unwrap(),
            Some(expected)
        );
    }

    #[test]
    fn test_wait_detected_as_blocking() {
        let mut cmd = Cmd::new();
        cmd.arg("WAIT").arg(1).arg("5000");
        let expected = Duration::from_secs_f64(5.0 + BLOCKING_CMD_TIMEOUT_EXTENSION);
        assert_eq!(
            get_request_timeout(&cmd, Duration::from_millis(1000)).unwrap(),
            Some(expected)
        );
    }

    #[test]
    fn test_xread_without_block_is_not_blocking() {
        let mut cmd = Cmd::new();
        cmd.arg("XREAD")
            .arg("COUNT")
            .arg("10")
            .arg("STREAMS")
            .arg("s1")
            .arg("$");
        assert_eq!(
            get_request_timeout(&cmd, Duration::from_millis(1000)).unwrap(),
            Some(Duration::from_millis(1000))
        );
    }

    #[test]
    fn test_blocking_fractional_seconds() {
        let mut cmd = Cmd::new();
        cmd.arg("BLMPOP").arg("0.857").arg("key");
        let expected = Duration::from_secs_f64(0.857 + BLOCKING_CMD_TIMEOUT_EXTENSION);
        assert_eq!(
            get_request_timeout(&cmd, Duration::from_millis(100)).unwrap(),
            Some(expected)
        );
    }

    #[test]
    fn test_all_blocking_commands_detected() {
        let blocking_cmds: Vec<(&str, Vec<&str>)> = vec![
            ("BLPOP", vec!["key", "5"]),
            ("BRPOP", vec!["key", "5"]),
            ("BLMOVE", vec!["src", "dst", "LEFT", "RIGHT", "5"]),
            ("BZPOPMAX", vec!["key", "5"]),
            ("BZPOPMIN", vec!["key", "5"]),
            ("BRPOPLPUSH", vec!["src", "dst", "5"]),
            ("BLMPOP", vec!["5", "1", "key"]),
            ("BZMPOP", vec!["5", "1", "key", "MIN"]),
            ("WAIT", vec!["1", "5000"]),
            ("WAITAOF", vec!["1", "1", "5000"]),
        ];

        for (cmd_name, args) in blocking_cmds {
            let mut cmd = Cmd::new();
            cmd.arg(cmd_name);
            for a in &args {
                cmd.arg(*a);
            }
            let result = get_request_timeout(&cmd, Duration::from_millis(100)).unwrap();
            assert!(
                result.is_some(),
                "{cmd_name} should be detected as blocking"
            );
        }
    }

    #[test]
    fn test_is_reset_command() {
        let client = create_test_client();

        let mut cmd = Cmd::new();
        cmd.arg("RESET");
        assert!(client.is_reset_command(&cmd));

        let mut cmd = Cmd::new();
        cmd.arg("PING");
        assert!(!client.is_reset_command(&cmd));
    }

    #[test]
    fn test_is_blocking_command() {
        // Always-blocking commands
        let mut cmd = Cmd::new();
        cmd.arg("BLPOP").arg("key").arg("5");
        assert!(is_blocking_command(&cmd));

        let mut cmd = Cmd::new();
        cmd.arg("BRPOP").arg("key").arg("5");
        assert!(is_blocking_command(&cmd));

        let mut cmd = Cmd::new();
        cmd.arg("WAIT").arg("1").arg("5000");
        assert!(is_blocking_command(&cmd));

        // XREAD with BLOCK is blocking
        let mut cmd = Cmd::new();
        cmd.arg("XREAD")
            .arg("BLOCK")
            .arg("5000")
            .arg("STREAMS")
            .arg("s1")
            .arg("$");
        assert!(is_blocking_command(&cmd));

        // XREAD without BLOCK is NOT blocking
        let mut cmd = Cmd::new();
        cmd.arg("XREAD")
            .arg("COUNT")
            .arg("10")
            .arg("STREAMS")
            .arg("s1")
            .arg("$");
        assert!(!is_blocking_command(&cmd));

        // XREADGROUP with BLOCK is blocking
        let mut cmd = Cmd::new();
        cmd.arg("XREADGROUP")
            .arg("GROUP")
            .arg("g1")
            .arg("c1")
            .arg("BLOCK")
            .arg("0")
            .arg("STREAMS")
            .arg("s1")
            .arg(">");
        assert!(is_blocking_command(&cmd));

        // XREADGROUP without BLOCK is NOT blocking
        let mut cmd = Cmd::new();
        cmd.arg("XREADGROUP")
            .arg("GROUP")
            .arg("g1")
            .arg("c1")
            .arg("STREAMS")
            .arg("s1")
            .arg(">");
        assert!(!is_blocking_command(&cmd));

        // Non-blocking commands
        let mut cmd = Cmd::new();
        cmd.arg("GET").arg("key");
        assert!(!is_blocking_command(&cmd));

        let mut cmd = Cmd::new();
        cmd.arg("SET").arg("key").arg("value");
        assert!(!is_blocking_command(&cmd));
    }

    #[test]
    fn test_is_blocking_command_name() {
        // Direct cases.
        assert!(is_blocking_command_name(b"BLPOP", &[]));
        assert!(!is_blocking_command_name(b"GET", &[]));

        // XREAD/XREADGROUP block only when a BLOCK token is present in args.
        let streams: Vec<Vec<u8>> = vec![b"STREAMS".to_vec(), b"s".to_vec(), b"$".to_vec()];
        assert!(!is_blocking_command_name(b"XREAD", &streams));
        let with_block: Vec<Vec<u8>> = vec![
            b"BLOCK".to_vec(),
            b"0".to_vec(),
            b"STREAMS".to_vec(),
            b"s".to_vec(),
            b"$".to_vec(),
        ];
        assert!(is_blocking_command_name(b"XREAD", &with_block));

        // Case-insensitive on both the command name and the BLOCK token.
        assert!(is_blocking_command_name(b"blpop", &[]));
        let with_block_lower: Vec<Vec<u8>> =
            vec![b"block".to_vec(), b"0".to_vec(), b"STREAMS".to_vec()];
        assert!(is_blocking_command_name(b"xread", &with_block_lower));

        // Behavioral parity with `is_blocking_command` over a representative table.
        // Each entry: (name, args, expected). `expected` is written out explicitly
        // (rather than comparing `via_cmd == via_name`) so that a regression in
        // either function's match arms is caught: for every non-XREAD/XREADGROUP
        // name, `is_blocking_command` just forwards to `is_blocking_command_name`,
        // so comparing the two outputs to each other can never fail if a command
        // is accidentally dropped from `is_blocking_command_name`'s match arms —
        // both sides would agree on the same (wrong) answer.
        let table: &[(&str, &[&str], bool)] = &[
            ("BLPOP", &["key", "0"], true),
            ("BRPOP", &["key", "5"], true),
            ("BLMOVE", &["src", "dst", "LEFT", "RIGHT", "0"], true),
            ("BRPOPLPUSH", &["src", "dst", "0"], true),
            ("BLMPOP", &["0", "1", "key", "LEFT"], true),
            ("BZPOPMIN", &["key", "0"], true),
            ("BZPOPMAX", &["key", "0"], true),
            ("BZMPOP", &["0", "1", "key", "MIN"], true),
            ("WAIT", &["0", "100"], true),
            ("WAITAOF", &["0", "0", "100"], true),
            ("XREAD", &["STREAMS", "s", "$"], false),
            ("XREAD", &["BLOCK", "0", "STREAMS", "s", "$"], true),
            (
                "XREADGROUP",
                &["GROUP", "g", "c", "STREAMS", "s", ">"],
                false,
            ),
            (
                "XREADGROUP",
                &["GROUP", "g", "c", "BLOCK", "0", "STREAMS", "s", ">"],
                true,
            ),
            ("GET", &["key"], false),
            ("SET", &["key", "value"], false),
            ("LPUSH", &["key", "value"], false),
        ];
        for (name, args, expected) in table {
            let mut cmd = Cmd::new();
            cmd.arg(*name);
            for a in *args {
                cmd.arg(*a);
            }
            let via_cmd = is_blocking_command(&cmd);
            let arg_vecs: Vec<Vec<u8>> = args.iter().map(|a| a.as_bytes().to_vec()).collect();
            let via_name = is_blocking_command_name(name.as_bytes(), &arg_vecs);
            assert_eq!(
                via_cmd, *expected,
                "is_blocking_command mismatch for {name} {args:?}: got {via_cmd}, expected {expected}"
            );
            assert_eq!(
                via_name, *expected,
                "is_blocking_command_name mismatch for {name} {args:?}: got {via_name}, expected {expected}"
            );
        }
    }

    /// Sets fake AWS credentials so `IAMTokenManager::new` can locally sign a SigV4
    /// token without reaching real AWS (same approach as `iam::tests::setup_test_credentials`).
    fn setup_test_credentials() {
        unsafe {
            std::env::set_var("AWS_ACCESS_KEY_ID", "test_access_key");
            std::env::set_var("AWS_SECRET_ACCESS_KEY", "test_secret_key");
            std::env::set_var("AWS_SESSION_TOKEN", "test_session_token");
        }
    }

    #[tokio::test]
    async fn test_iam_token_manager_username_accessor_returns_configured_username() {
        setup_test_credentials();

        let manager = crate::iam::IAMTokenManager::new(
            "test-cluster".to_string(),
            "iam-test-user".to_string(),
            "us-east-1".to_string(),
            crate::iam::ServiceType::ElastiCache,
            None,
            None,
        )
        .await
        .expect("IAMTokenManager creation should succeed with fake credentials");

        assert_eq!(manager.username(), "iam-test-user");
    }

    #[tokio::test]
    #[cfg(feature = "proto")]
    async fn test_client_iam_token_manager_accessor() {
        setup_test_credentials();

        // With IAM configured, Client::new should populate the IAM token manager,
        // and the new accessor should expose it (used by scoped connections to
        // authenticate as the IAM identity instead of running unauthenticated).
        let iam_request = ConnectionRequest {
            addresses: vec![NodeAddress {
                host: "127.0.0.1".to_string(),
                port: 1,
            }],
            lazy_connect: true,
            authentication_info: Some(crate::client::types::AuthenticationInfo {
                username: Some("iam-test-user".to_string()),
                password: None,
                iam_config: Some(crate::client::types::IamAuthenticationConfig {
                    cluster_name: "test-cluster".to_string(),
                    region: "us-east-1".to_string(),
                    service_type: crate::iam::ServiceType::ElastiCache,
                    refresh_interval_seconds: None,
                    credentials_provider: None,
                }),
            }),
            ..Default::default()
        };

        let client = Client::new(iam_request, None)
            .await
            .expect("lazy client creation with IAM config should succeed");

        let manager = client
            .iam_token_manager()
            .expect("iam_token_manager() should return Some when IAM is configured");
        assert_eq!(manager.username(), "iam-test-user");

        // Without IAM configured, the accessor should return None.
        let non_iam_request = ConnectionRequest {
            addresses: vec![NodeAddress {
                host: "127.0.0.1".to_string(),
                port: 1,
            }],
            lazy_connect: true,
            ..Default::default()
        };
        let non_iam_client = Client::new(non_iam_request, None)
            .await
            .expect("lazy client creation without IAM config should succeed");
        assert!(
            non_iam_client.iam_token_manager().is_none(),
            "iam_token_manager() should return None when IAM is not configured"
        );
    }
}
