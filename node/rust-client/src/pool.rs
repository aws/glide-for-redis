// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Node.js N-API bindings for client-instance pool and isolated execution scopes.
//!
//! Uses the same deferred Promise pattern as GlideClientHandle for async work:
//! synchronous N-API call spawns work on a runtime, resolves/rejects via Deferred.
//!
//! Note: `#[napi]` exports are invisible to the Rust compiler's usage analysis,
//! so dead_code warnings are suppressed at module level.

#![allow(dead_code)]

use crate::create_handle_for_client;
use glide_core::client::{Client, ConnectionRequest};
use glide_core::connection_request::ConnectionRequest as ProtobufConnectionRequest;
use glide_core::pool::{self, ClientPool, POOL_RUNNING, PoolConfig};
use glide_core::scope;
use napi::bindgen_prelude::*;
use napi::threadsafe_function::ThreadsafeFunction;
use napi::{Env, Error, Result, Status};
use napi_derive::napi;
use protobuf::Message;
use redis::PushInfo;
use std::sync::atomic::Ordering;
use std::sync::{Arc, OnceLock};
use std::time::Duration;
use tokio::sync::mpsc;

// ═══════════════════════════════════════════════════════════════════════════════

static POOL_RUNTIME: OnceLock<tokio::runtime::Runtime> = OnceLock::new();

fn get_pool_runtime() -> &'static tokio::runtime::Runtime {
    POOL_RUNTIME.get_or_init(|| {
        tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .worker_threads(2)
            .thread_name("glide-node-pool")
            .build()
            .expect("Failed to create pool runtime")
    })
}

// ═══════════════════════════════════════════════════════════════════════════════

/// Mark/unmark a pool-borrowed client as executing a blocking command.
/// Returns true if the mark was applied (client is registered).
pub fn mark_blocking(client_id: u64, blocking: bool) -> bool {
    if let Some(arc) = glide_core::pool::get_blocking_flag(client_id) {
        if !blocking {
            // Refresh activity BEFORE clearing the flag so the abandon monitor
            // never observes is_blocking=false with a stale borrowed_at timestamp.
            glide_core::pool::refresh_activity_by_client(client_id);
        }
        arc.store(blocking, Ordering::Release);
        true
    } else {
        false
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// POOL CONFIG / METRICS
// ═══════════════════════════════════════════════════════════════════════════════

#[napi(object)]
pub struct PoolConfigNapi {
    pub max_size: u32,
    pub min_idle: u32,
    pub idle_timeout_ms: u32,
    pub request_timeout_ms: u32,
    /// Maximum inactivity time for a borrowed client before the pool reclaims it (ms).
    /// The timer resets on every command sent.
    /// The abandon monitor skips clients executing blocking commands (BLPOP, XREAD BLOCK, etc.).
    /// Set to 0 to disable abandon detection. Default: 300000 (5 minutes).
    pub abandon_timeout_ms: u32,
}

#[napi(object)]
pub struct PoolMetrics {
    pub idle: u32,
    pub active: u32,
    pub total: u32,
}

// ═══════════════════════════════════════════════════════════════════════════════
// POOL LIFECYCLE
// ═══════════════════════════════════════════════════════════════════════════════

/// Create a glide-core-managed pool and return a `Promise<pool_id>`.
///
/// The warmup creates `min_idle` connections and adds them to the pool's idle
/// list so that `pool_try_acquire` can hand them out immediately.  The Promise
/// resolves after the first connection succeeds (connectivity validation) and
/// rejects if the first connection fails.
///
/// Pool clients are created without a push-message channel because pub/sub is
/// not supported on pooled connections.
#[napi(ts_return_type = "Promise<number>")]
pub fn create_pool<'a>(
    env: &'a Env,
    connection_request_bytes: Uint8Array,
    pool_config: PoolConfigNapi,
) -> Result<Object<'a>> {
    let (deferred, promise) = env.create_deferred()?;

    let conn_req_bytes = connection_request_bytes.as_ref().to_vec();

    let proto_req = ProtobufConnectionRequest::parse_from_bytes(&conn_req_bytes).map_err(|e| {
        Error::new(
            Status::InvalidArg,
            format!("Invalid connection request: {e}"),
        )
    })?;

    if proto_req.pubsub_subscriptions.is_some() {
        return Err(Error::new(
            Status::InvalidArg,
            "Pool clients cannot have pubsub subscriptions configured.",
        ));
    }

    let configured_database_id = proto_req.database_id;

    let config = PoolConfig {
        max_size: pool_config.max_size,
        min_idle: pool_config.min_idle,
        idle_timeout: Duration::from_millis(pool_config.idle_timeout_ms as u64),
        request_timeout: Duration::from_millis(pool_config.request_timeout_ms as u64),
        test_on_borrow: false,
        connection_request: conn_req_bytes.clone(),
        is_async: true,
        configured_database_id,
        abandon_timeout: Duration::from_millis(pool_config.abandon_timeout_ms as u64),
    };

    let pool = ClientPool::new(config)
        .map_err(|e| Error::new(Status::InvalidArg, format!("Invalid pool config: {e}")))?;

    let pool_id = pool::register_pool(pool) as i64;

    // Start abandon monitor for this pool.
    pool::start_abandon_monitor(pool_id as u64, get_pool_runtime().handle());

    // Background warmup: create min_idle raw Client objects and add to the pool's
    // idle list.  No GlideClientHandle is built here — handles are built JIT on
    // acquire via pool_build_handle.  Pool clients are created without a push
    // channel (pub/sub not supported for pooled connections).
    let min_idle = pool_config.min_idle;
    let conn_bytes = conn_req_bytes;

    // Oneshot: first-client result → deferred resolver.
    let (first_tx, first_rx) = tokio::sync::oneshot::channel::<std::result::Result<(), String>>();

    get_pool_runtime().spawn(async move {
        let mut first_tx_opt = Some(first_tx);

        if min_idle == 0 {
            if let Some(tx) = first_tx_opt.take() {
                let _ = tx.send(Ok(()));
            }
            return;
        }

        for i in 0..min_idle {
            let connection_request = match ProtobufConnectionRequest::parse_from_bytes(&conn_bytes)
            {
                Ok(req) => req,
                Err(e) => {
                    if let Some(tx) = first_tx_opt.take() {
                        let _ = tx.send(Err(format!(
                            "Failed to parse connection request during warmup: {e}"
                        )));
                    }
                    return;
                }
            };

            // Extract address resolver key BEFORE converting to internal request,
            // because the key field exists only on the protobuf type.
            let resolver_key = connection_request
                .address_resolver_key
                .as_ref()
                .filter(|k| !k.is_empty())
                .map(ToString::to_string);

            let mut internal_req: ConnectionRequest = connection_request.into();

            // Pool connections share the resolver — use get() not remove() so
            // subsequent pool connections (warmup, on-demand, reconnects) also
            // have the resolver.  The resolver is cleaned up in ClientPool.close()
            // on the TypeScript side.
            if let Some(key) = resolver_key
                && let Some(resolver) = glide_core::address_resolver_registry::get(&key)
            {
                internal_req.address_resolver = Some(resolver);
            }

            // No push sender: pool clients do not support pub/sub.
            let client: Client = match Client::new(internal_req, None).await {
                Ok(c) => c,
                Err(e) => {
                    let msg = format!("Pool warmup: failed to create client: {e}");
                    if i == 0 {
                        if let Some(tx) = first_tx_opt.take() {
                            let _ = tx.send(Err(msg));
                        }
                        return;
                    }
                    logger_core::log_warn("pool", msg);
                    break;
                }
            };

            // Register the client in the pool's idle list.
            {
                let registry = pool::get_pool_registry();
                match registry.get(&(pool_id as u64)) {
                    Some(pool_entry) => {
                        let mut pool_guard = pool_entry.value().lock().await;
                        if pool_guard.state.load(Ordering::Acquire) == POOL_RUNNING {
                            let cid = pool_guard.add_client(client.clone());
                            scope::register_client(cid, client);
                            let (_, condvar) = &*pool_guard.release_notify;
                            condvar.notify_all();
                        } else {
                            // Pool was destroyed before warmup finished.
                            return;
                        }
                    }
                    None => return,
                }
            }

            // Notify caller that the first connection succeeded.
            if i == 0
                && let Some(tx) = first_tx_opt.take()
            {
                let _ = tx.send(Ok(()));
            }
        }

        // Ensure the oneshot is always resolved.
        if let Some(tx) = first_tx_opt.take() {
            let _ = tx.send(Ok(()));
        }
    });

    // Resolve/reject the deferred based on the first-client outcome.
    get_pool_runtime().spawn(async move {
        match first_rx.await {
            Ok(Ok(())) => deferred.resolve(move |_| Ok(pool_id)),
            Ok(Err(msg)) => deferred.reject(Error::new(Status::Unknown, msg)),
            Err(_) => deferred.reject(Error::new(Status::Unknown, "Pool warmup task dropped")),
        }
    });

    Ok(promise)
}

/// Build a `GlideClientHandle` for a pool-acquired client identified by `client_id`.
///
/// Called by TS after `pool_try_acquire` or `pool_acquire_blocking` returns a
/// non-negative `client_id`.  Looks up the [`Client`] in the scope registry
/// and wraps it in a handle with a dedicated worker thread and response buffer.
///
/// The `wake_callback` is the JS callback that signals "responses available"
/// — it should be the `handleResponsesAvailable` bound method of the
/// `GlideClient` (or `GlideClusterClient`) being constructed.
///
/// Pool clients were created without a pub/sub push channel, so the handle's
/// push listener will simply block forever on an empty channel — this is
/// correct and harmless.
#[napi(ts_return_type = "Promise<GlideClientHandle>")]
pub fn pool_build_handle<'a>(
    env: &'a Env,
    client_id: i64,
    #[napi(ts_arg_type = "() => void")] wake_callback: Function<(), ()>,
) -> Result<Object<'a>> {
    let (deferred, promise) = env.create_deferred()?;

    let wake_tsfn: Arc<ThreadsafeFunction<(), (), (), Status, false>> =
        Arc::new(wake_callback.build_threadsafe_function().build()?);

    let client_id_u64 = client_id as u64;

    get_pool_runtime().spawn(async move {
        // Retrieve the Client from the scope registry.
        let client = match scope::get_parent_client(client_id_u64).await {
            Some(c) => c,
            None => {
                deferred.reject(Error::new(
                    Status::InvalidArg,
                    format!("No client registered for client_id {client_id_u64}"),
                ));
                return;
            }
        };

        // Resolve the inflight_requests_limit from the pool config stored for
        // this client.  Falls back to the global default if not found so
        // pool_build_handle is always safe to call even without pool context.
        let inflight_requests_limit = if let Some(pool_id) =
            glide_core::pool::get_pool_id_for_client(client_id_u64)
        {
            if let Some(pool_arc) = glide_core::pool::get_pool(pool_id) {
                let pool_guard = pool_arc.lock().await;
                let req = ProtobufConnectionRequest::parse_from_bytes(
                    &pool_guard.config.connection_request,
                );
                match req {
                    Ok(r) if r.inflight_requests_limit > 0 => r.inflight_requests_limit as isize,
                    _ => glide_core::client::DEFAULT_MAX_INFLIGHT_REQUESTS as isize,
                }
            } else {
                glide_core::client::DEFAULT_MAX_INFLIGHT_REQUESTS as isize
            }
        } else {
            glide_core::client::DEFAULT_MAX_INFLIGHT_REQUESTS as isize
        };

        // Create a dummy push channel.  Pool clients don't use pub/sub, so
        // push_receiver will never receive any messages.
        let (_push_sender, push_receiver) = mpsc::unbounded_channel::<PushInfo>();

        match create_handle_for_client(
            client,
            push_receiver,
            wake_tsfn,
            inflight_requests_limit,
            Some(client_id_u64),
        )
        .await
        {
            Ok(handle) => deferred.resolve(|_| Ok(handle)),
            Err(e) => deferred.reject(e),
        }
    });

    Ok(promise)
}

/// Create a raw pool client (no push channel, no worker thread) from serialised
/// protobuf connection-request bytes.  This is the same path as the warmup loop
/// in `create_pool`, factored out so that `pool_try_acquire` can trigger
/// on-demand connection creation when the pool is not yet full.
async fn create_raw_pool_client(conn_bytes: &[u8]) -> std::result::Result<Client, String> {
    let connection_request =
        ProtobufConnectionRequest::parse_from_bytes(conn_bytes).map_err(|e| {
            format!("Failed to parse connection request during on-demand creation: {e}")
        })?;

    // Extract address resolver key BEFORE converting to internal request,
    // because the key field exists only on the protobuf type.
    let resolver_key = connection_request
        .address_resolver_key
        .as_ref()
        .filter(|k| !k.is_empty())
        .map(ToString::to_string);

    let mut internal_req: ConnectionRequest = connection_request.into();

    // Pool connections share the resolver — use get() not remove() so
    // subsequent pool connections (warmup, on-demand, reconnects) also
    // have the resolver.  The resolver is cleaned up in ClientPool.close()
    // on the TypeScript side.
    if let Some(key) = resolver_key
        && let Some(resolver) = glide_core::address_resolver_registry::get(&key)
    {
        internal_req.address_resolver = Some(resolver);
    }

    // No push sender: pool clients do not support pub/sub.
    Client::new(internal_req, None)
        .await
        .map_err(|e| format!("On-demand pool client creation failed: {e}"))
}

/// Spawn a background task to create a new pool client if `pool_guard.should_create()`.
/// Pre-increments `total_count` under the caller's lock, then decrements on failure.
/// Notifies waiters via `release_notify` on success so `pool_acquire_blocking` wakes up.
fn maybe_spawn_on_demand_creation(
    pool_entry: Arc<tokio::sync::Mutex<ClientPool>>,
    pool_guard: &mut ClientPool,
) {
    if !pool_guard.should_create() {
        return;
    }
    // Pre-reserve the slot so concurrent callers don't over-create.
    pool_guard
        .total_count
        .fetch_add(1, std::sync::atomic::Ordering::AcqRel);
    let bytes = pool_guard.config.connection_request.clone();
    get_pool_runtime().spawn(async move {
        match create_raw_pool_client(&bytes).await {
            Ok(client) => {
                let mut pg = pool_entry.lock().await;
                if pg.state.load(std::sync::atomic::Ordering::Acquire) != POOL_RUNNING {
                    // Pool was destroyed while we were connecting; release reservation.
                    pg.total_count
                        .fetch_sub(1, std::sync::atomic::Ordering::AcqRel);
                    return;
                }
                // add_client_reserved: total_count already incremented above.
                let cid = pg.add_client_reserved(client.clone());
                scope::register_client(cid, client);
                let (_, condvar) = &*pg.release_notify;
                condvar.notify_all();
            }
            Err(e) => {
                logger_core::log_warn("pool", format!("On-demand pool creation failed: {e}"));
                // Release the pre-reserved slot.
                let pg = pool_entry.lock().await;
                pg.total_count
                    .fetch_sub(1, std::sync::atomic::Ordering::AcqRel);
            }
        }
    });
}

#[napi]
pub fn pool_try_acquire(pool_id: i64) -> Result<i64> {
    let pool_id_u64 = pool_id as u64;
    let registry = pool::get_pool_registry();
    let pool_entry = registry
        .get(&pool_id_u64)
        .ok_or_else(|| Error::new(Status::InvalidArg, "Invalid pool_id"))?;

    let pool_arc = pool_entry.value().clone();
    let result = get_pool_runtime().block_on(async {
        let mut pool_guard = pool_arc.lock().await;
        // Drain discarded IDs first (abandoned clients).
        let discarded = pool_guard.drain_discarded_ids();
        for cid in discarded {
            glide_core::pool::unregister_blocking_flag(cid);
            glide_core::pool::unregister_pool_client(cid);
            scope::unregister_client(cid);
        }
        let result = pool_guard.try_acquire();
        // On-demand creation: no idle client but pool is not yet full.
        // Spawn background task; the new client will appear in the idle list and
        // the next pool_acquire_blocking poll (or a subsequent pool_try_acquire)
        // will hand it out.
        if result < 0 {
            maybe_spawn_on_demand_creation(pool_entry.value().clone(), &mut pool_guard);
        }
        result
    });

    // Register the acquired client in glide-core's CLIENT_TO_POOL map so that
    // refresh_activity_by_client() works at command dispatch.
    if result >= 0 {
        pool::register_pool_client(pool_id_u64, result as u64);
    }

    Ok(result)
}

/// Acquire with timeout — returns `Promise<number>` via deferred.
#[napi(ts_return_type = "Promise<number>")]
pub fn pool_acquire_blocking<'a>(
    env: &'a Env,
    pool_id: i64,
    timeout_ms: u32,
) -> Result<Object<'a>> {
    let (deferred, promise) = env.create_deferred()?;
    let pool_id_u64 = pool_id as u64;
    let timeout = Duration::from_millis(timeout_ms as u64);

    get_pool_runtime().spawn(async move {
        let deadline = tokio::time::Instant::now() + timeout;
        let poll_interval = Duration::from_millis(5);
        let result_value: i64;

        loop {
            let registry = pool::get_pool_registry();
            let pool_entry = match registry.get(&pool_id_u64) {
                Some(e) => e.value().clone(),
                None => {
                    result_value = -2;
                    break;
                }
            };

            let result = {
                let mut pool_guard = pool_entry.lock().await;
                // Drain discarded IDs (abandoned clients) on every poll.
                let discarded = pool_guard.drain_discarded_ids();
                for cid in discarded {
                    glide_core::pool::unregister_blocking_flag(cid);
                    glide_core::pool::unregister_pool_client(cid);
                    scope::unregister_client(cid);
                }
                let r = pool_guard.try_acquire();
                // Trigger on-demand creation when pool has capacity but no idle clients.
                if r < 0 {
                    maybe_spawn_on_demand_creation(pool_entry.clone(), &mut pool_guard);
                }
                r
            };

            if result >= 0 {
                result_value = result;
                // Register in glide-core's CLIENT_TO_POOL for activity refresh at dispatch.
                pool::register_pool_client(pool_id_u64, result as u64);
                break;
            }
            if result == -1 {
                result_value = -2;
                break;
            }

            if tokio::time::Instant::now() >= deadline {
                result_value = -1;
                break;
            }

            tokio::time::sleep(poll_interval).await;
        }

        deferred.resolve(move |_| Ok(result_value));
    });

    Ok(promise)
}

/// Release a client back to the pool. Returns Promise that resolves when release completes.
#[napi(ts_return_type = "Promise<void>")]
pub fn pool_release<'a>(env: &'a Env, pool_id: i64, client_id: i64) -> Result<Object<'a>> {
    let (deferred, promise) = env.create_deferred()?;
    let pool_arc = pool::get_pool(pool_id as u64);

    get_pool_runtime().spawn(async move {
        if let Some(arc) = pool_arc {
            pool::release_client_async(arc, client_id as u64).await;
        }
        deferred.resolve(move |_| Ok(()));
    });

    Ok(promise)
}

#[napi]
pub fn pool_metrics(pool_id: i64) -> Result<PoolMetrics> {
    let registry = pool::get_pool_registry();
    let pool_entry = registry
        .get(&(pool_id as u64))
        .ok_or_else(|| Error::new(Status::InvalidArg, "Invalid pool_id"))?;

    let (idle, active, total) = get_pool_runtime().block_on(async {
        let pool_guard = pool_entry.value().lock().await;
        let idle = pool_guard.idle.len() as u32;
        let active = pool_guard.in_use.len() as u32;
        let total = pool_guard.total_count.load(Ordering::Acquire);
        (idle, active, total)
    });

    Ok(PoolMetrics {
        idle,
        active,
        total,
    })
}

#[napi]
pub fn pool_destroy(pool_id: i64) {
    if let Some(pool_arc) = pool::unregister_pool(pool_id as u64) {
        get_pool_runtime().block_on(async {
            let mut pool_guard = pool_arc.lock().await;
            // Clean up global registries before destroying the pool.
            for entry in pool_guard.idle.iter() {
                glide_core::pool::unregister_blocking_flag(entry.client_id);
                glide_core::pool::unregister_pool_client(entry.client_id);
                scope::unregister_client(entry.client_id);
            }
            for entry in pool_guard.in_use.iter() {
                glide_core::pool::unregister_blocking_flag(*entry.key());
                glide_core::pool::unregister_pool_client(*entry.key());
                scope::unregister_client(*entry.key());
            }
            // Also drain any discard ids the monitor queued.
            let discarded = pool_guard.drain_discarded_ids();
            for cid in discarded {
                glide_core::pool::unregister_blocking_flag(cid);
                glide_core::pool::unregister_pool_client(cid);
                scope::unregister_client(cid);
            }
            pool_guard.destroy();
        });
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SCOPE FUNCTIONS — deferred Promise pattern
// ═══════════════════════════════════════════════════════════════════════════════

#[napi]
pub fn scope_try_acquire(
    client_id: i64,
    connection_request_bytes: Uint8Array,
    routing_slot: u16,
) -> Result<i64> {
    let conn_bytes = connection_request_bytes.as_ref().to_vec();
    let runtime = get_pool_runtime();
    let result =
        scope::try_acquire_scope(client_id as u64, conn_bytes, runtime.handle(), routing_slot);
    Ok(result)
}

/// Execute a command on a scoped connection. Returns `Promise<string | null>`.
#[napi(ts_return_type = "Promise<string | null>")]
pub fn scope_execute<'a>(
    env: &'a Env,
    scope_id: i64,
    client_id: i64,
    cmd_bytes: Uint8Array,
) -> Result<Object<'a>> {
    let (deferred, promise) = env.create_deferred()?;
    let cmd_data = cmd_bytes.as_ref().to_vec();
    let scope_id_u64 = scope_id as u64;
    let client_id_u64 = client_id as u64;

    get_pool_runtime().spawn(async move {
        let parsed = scope::deserialize_command(&cmd_data);
        let (cmd_name, args) = match parsed {
            Some(p) => p,
            None => {
                deferred.reject(Error::new(
                    Status::InvalidArg,
                    "Invalid command wire format",
                ));
                return;
            }
        };

        let client_registry = scope::get_client_registry();
        let client = client_registry
            .get(&client_id_u64)
            .map(|e| e.value().clone());

        match scope::execute_scope_command(scope_id_u64, &cmd_name, &args, client.as_ref()).await {
            Ok(value) => {
                let result = value_to_string(value);
                deferred.resolve(|_| Ok(result));
            }
            Err(e) => {
                deferred.reject(Error::new(Status::GenericFailure, format!("{e}")));
            }
        }
    });

    Ok(promise)
}

#[napi]
pub fn scope_release(scope_id: i64, client_id: i64) {
    let runtime = get_pool_runtime();
    scope::release_scope(scope_id as u64, client_id as u64, runtime.handle());
}

// ═══════════════════════════════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════════════════════════════

fn value_to_string(value: redis::Value) -> Option<String> {
    match value {
        redis::Value::Nil => None,
        redis::Value::Okay => Some("OK".to_string()),
        redis::Value::Int(i) => Some(i.to_string()),
        redis::Value::BulkString(bytes) => Some(String::from_utf8_lossy(&bytes).to_string()),
        redis::Value::SimpleString(s) => Some(s),
        redis::Value::Array(arr) => {
            let parts: Vec<String> = arr
                .into_iter()
                .map(|v| match v {
                    redis::Value::Nil => "null".to_string(),
                    redis::Value::Okay => "OK".to_string(),
                    redis::Value::Int(i) => i.to_string(),
                    redis::Value::BulkString(b) => String::from_utf8_lossy(&b).to_string(),
                    redis::Value::SimpleString(s) => s,
                    _ => format!("{:?}", v),
                })
                .collect();
            Some(format!("[{}]", parts.join(",")))
        }
        other => Some(format!("{:?}", other)),
    }
}
