# Changelog

## Pending 2.6

### Fixes

* Core/All: Fix a race condition where the `ClientPool` abandon monitor could silently reclaim a pooled client executing a blocking command (`BLPOP`, `XREAD BLOCK`, etc.), causing the in-flight future to complete with a `ClosingException`. The `is_blocking` flag is now set lock-free via a process-global `Arc<AtomicBool>` registry in `glide-core`, populated at borrow time and set synchronously on the caller thread before any `spawn()`. This eliminates the `try_lock` race that could leave the flag unset when the pool mutex was contended. ([#6971](https://github.com/valkey-io/valkey-glide/issues/6971))
* Core: A scope pool configured with `max_total = N` now permits N concurrent scopes instead of N-1. The pool reserved a slot against `max_total` and the caller then re-checked capacity after that reservation, so the last acquire was never given a connection and the borrower timed out ([#6795](https://github.com/valkey-io/valkey-glide/issues/6795))
* Java: Map the Jedis compatibility layer's database selection onto GLIDE's `databaseId` instead of logging a warning and discarding it. A `JedisPool` configured for a non-zero database ran every command against database 0, silently writing to a database the caller did not ask for ([#6994](https://github.com/valkey-io/valkey-glide/issues/6994))
* Core: Mark `PSUBSCRIBE` and `PUNSUBSCRIBE` as readonly commands so cluster routing treats them consistently with `SUBSCRIBE`/`UNSUBSCRIBE` ([#6756](https://github.com/valkey-io/valkey-glide/pull/6756))
* Core/FFI: Scoped connections honor blocking-command timeouts (e.g. `BLPOP key 0` blocks instead of timing out at the request timeout), and a scoped connection whose blocking command timed out or was cancelled is discarded on release instead of being reused with a stale server-side waiter ([#6780](https://github.com/valkey-io/valkey-glide/issues/6780), [#6794](https://github.com/valkey-io/valkey-glide/issues/6794))
* Core: Scoped connections authenticate as the configured IAM identity, re-authenticate on IAM token rotation, and are discarded rather than reused when re-authentication fails ([#7001](https://github.com/valkey-io/valkey-glide/issues/7001))
* Java: Fix scoped connection truncating values larger than 16 KB ([#6893](https://github.com/valkey-io/valkey-glide/issues/6893))
* Go: Propagate pool ConnectionRequest into pool-borrowed clients so `ScopedConnection` works on pooled clients ([#6763](https://github.com/valkey-io/valkey-glide/issues/6763))
* Core/FFI: Standalone AZ-affinity reads skip nodes that are reconnecting instead of blocking on them; accept `AllNodes` in `create_client_from_uri`'s `read_from` option ([#6721](https://github.com/valkey-io/valkey-glide/pull/6721))
* Core: retry empty-receivers multi-node fan-out under topology churn ([#6768](https://github.com/valkey-io/valkey-glide/pull/6768))
* Java: Allow scopedConnection on pool-borrowed GlideClient ([#6764](https://github.com/valkey-io/valkey-glide/issues/6764))
* Core/FFI: Strip brackets from IPv6 address literals in `create_client_from_uri` so bracketed hosts (e.g. `redis://[::1]:6379`) resolve correctly. `url::Url::host_str()` returns the literal with its surrounding brackets (`[::1]`), which `tokio::net::lookup_host` cannot resolve, causing connections to IPv6 literals to hang until the connect timeout. The URI parser now uses the unbracketed canonical form via `Ipv6Addr::to_string()`.
* Python: Make async pipe transport fork-safe. After `fork()`, the flush thread is gone but `OnceLock` prevented reinitialization, causing commands in forked child processes (e.g. PySpark workers) to hang indefinitely. Now detects fork via PID comparison and reinitializes the pipe. Using a parent's client object in a forked child now raises `ClosingError` on command paths and skips the FFI call on `close()`, instead of silently hanging. ([#6673](https://github.com/valkey-io/valkey-glide/issues/6673))
* fix: bound mTLS cert reload interval and align Node field names across SDKs ([#6678](https://github.com/valkey-io/valkey-glide/pull/6678))
* Core/FFI: Percent-decode userinfo in `create_client_from_uri` so credentials containing URI-reserved characters (`@`, `:`, `/`, `?`, `#`, `%`, `+`, space, non-ASCII) authenticate correctly ([#6659](https://github.com/valkey-io/valkey-glide/issues/6659))
* Core/All: Buffer pending cluster requests during reconnect instead of failing immediately. When a circular MOVED redirect triggers a reconnect, requests arriving during the recovery window are now queued and retried transparently once reconnection completes. A new `recovery_requests_queue_size` option (default: 1000) controls the queue depth. ([#6640](https://github.com/valkey-io/valkey-glide/pull/6640))
* Python: fix trio hang on free-threaded builds by waking `_CompatFuture` waiters from the owning trio thread ([#6685](https://github.com/valkey-io/valkey-glide/pull/6685))
* Python: Fix trio pub/sub BusyResourceError from duplicate shared-pipe reader ([#6605](https://github.com/valkey-io/valkey-glide/pull/6605))
* Java: Make the automatic JVM shutdown hook non-destructive so a command issued from a user's own shutdown hook is no longer rejected with `ClosingException: Client is shutting down`. The JVM runs all shutdown hooks concurrently, and GLIDE's hook previously tore the client down and blocked new requests, racing with (and aborting) legitimate work such as persisting state before exit. Deterministic teardown remains available via the client's `close()`. ([#4809](https://github.com/valkey-io/valkey-glide/issues/4809))
* Core: Fix native panic for setex psetex and setnx commands ([#6551](https://github.com/valkey-io/valkey-glide/pull/6551))
* Core/FFI: fix(ffi): forward Disconnection push notifications past the malformed-frame guard ([#6543](https://github.com/valkey-io/valkey-glide/pull/6543))
* CI: Run `test-release` in `pypi-cd.yml` when only one package is published manually, so a skipped sibling publish job no longer causes post-publish validation to be skipped entirely ([#6542](https://github.com/valkey-io/valkey-glide/pull/6542))
* Core/FFI: fix(ffi): prevent pub/sub DoS from malformed server push frames ([#6530](https://github.com/valkey-io/valkey-glide/pull/6530))
* Python: Restore `BaseClient.__aenter__` return type to `Self` (from the widened `"BaseClient"` introduced in 2.5.0). Entering the async context manager (`async with await GlideClusterClient.create(...) as client`) now preserves the concrete subclass for static type checkers, matching `create()`. ([#6531](https://github.com/valkey-io/valkey-glide/issues/6531))
* Core: fix(core): enforce RESP3 recursion-depth guard for all aggregate types ([#6477](https://github.com/valkey-io/valkey-glide/pull/6477))
* Core: Update `anyhow` to 1.0.103 to fix RUSTSEC-2026-0190, an unsoundness advisory in `anyhow::Error::downcast_mut()` that can trigger undefined behavior ([#6364](https://github.com/valkey-io/valkey-glide/pull/6364))
* Go: Remove `.gitignore` from the released module so consumers who commit `vendor/` keep the generated artifacts (`internal/protobuf/*.pb.go`, `rustbin/**`, `lib.h`) ([#6441](https://github.com/valkey-io/valkey-glide/pull/6441))
* Core: Validate client library names ([#6891](https://github.com/valkey-io/valkey-glide/pull/6891))

### Changes

* Node: Migrate `ClientPool` to use `glide-core::ClientPool` directly, matching the architecture of Java, Go, and Python. Pool state (idle stack, active map, waiter queue, abandon monitor) is now fully managed in Rust. A new `create_handle_for_client()` function wraps pool-managed Rust connections as full `GlideClientHandle` N-API objects, preserving the complete command API. The TypeScript-side state duplication introduced in #6338 is removed. Public API is unchanged. ([#6887](https://github.com/valkey-io/valkey-glide/issues/6887))
* Core: Add client-side caching support for MGET. Fully cached requests return locally; partially cached requests fetch only misses and preserve duplicate-key and NIL response semantics. ([#6793](https://github.com/valkey-io/valkey-glide/issues/6793))
* Rust: Add an initial native Rust client (preview) built directly on `glide-core` ([#6864](https://github.com/valkey-io/valkey-glide/pull/6864))
* Java, Node, Python, Go: Add optional client information tags across standalone, cluster, pooled, async/sync, and standalone monitor clients, plus configurable library-name overrides in Node, Python, and Go. Tags are composed with the default or custom library name reported in server client metadata, with runtime library names preferred during connection setup and existing fallbacks retained. Non-empty library-name overrides and tags must contain only printable ASCII characters from ! (U+0021) through ~ (U+007E). ([#6755](https://github.com/valkey-io/valkey-glide/pull/6755))
* Java: Add `GlideCredentialProvider` support to `IamAuthConfig`. When set, the Rust core calls back into Java to retrieve AWS credentials (via the new `AwsCredentials` builder, which supports optional expiry) for each IAM token signing operation. When not set, the default AWS credential chain is used — fully backwards compatible. ([#6825](https://github.com/valkey-io/valkey-glide/pull/6825))
* Java: Add `GlideString.asReadOnlyByteBuffer()` for zero-copy, read-only access to binary payloads ([#6600](https://github.com/valkey-io/valkey-glide/issues/6600))
* Go: Convert FFI string payloads with `GoStringN` (one copy, interior NULs preserved) instead of `GoBytes`+`string` in GET/MGET response parsing, pubsub callbacks, MONITOR client/command strings, and script hashes ([#6751](https://github.com/valkey-io/valkey-glide/issues/6751))
* Core, Python: Add `AZ_AFFINITY_ALL_NODES` read policy ([#6721](https://github.com/valkey-io/valkey-glide/pull/6721))
* feat(go): add AzAffinityAllNodes read strategy ([#6927](https://github.com/valkey-io/valkey-glide/pull/6927))
* Core: Zero-copy receive path for GET/MGET ([#6559](https://github.com/valkey-io/valkey-glide/pull/6559))
* Go: Expose `inflightRequestsLimit` configuration via `WithInflightRequestsLimit`, bringing the Go client to parity with Java, Python, and Node ([#6385](https://github.com/valkey-io/valkey-glide/issues/6385))
* Core, Java, Python, Node, Go: Add client-instance pooling and isolated execution scopes. Pools eliminate multiplexer contention under high concurrency; scopes provide dedicated connections for WATCH/MULTI/EXEC and CLIENT TRACKING. All languages share a unified Rust implementation via `send_scope_command()` and `release_client_async()`. Pool release resets state (DISCARD + SELECT). Scopes inherit parent's current database, credentials, and compression. Circuit breaker and inflight limits enforced. Abandon detection reclaims leaked borrows after configurable timeout (default 5 min, skips blocking commands, 0 to disable). ([#6338](https://github.com/valkey-io/valkey-glide/pull/6338))
* Python: Add Python 3.14t free-threaded support to the test matrix. ([#5445](https://github.com/valkey-io/valkey-glide/issues/5445))
* Core/FFI: Add `command_with_route_info` FFI entrypoint, accepting routing as a `RouteInfo` C-struct pointer instead of protobuf-encoded bytes — the same mechanism `batch()` already uses. Existing `command`, `command_with_buffer`, `command_with_buffers`, and `invoke_script` are unchanged. ([#6494](https://github.com/valkey-io/valkey-glide/pull/6494))
* CI: Publish the Python `valkey-glide` and `valkey-glide-sync` packages to PyPI via Trusted Publishing (OIDC) with PEP 740 attestations, replacing API-token uploads ([#6478](https://github.com/valkey-io/valkey-glide/pull/6478))
* Node: Replace socket IPC with direct NAPI layer ([#5325](https://github.com/valkey-io/valkey-glide/pull/5325))
* feat(python-sync): add zero-copy buffers to mget ([#6367](https://github.com/valkey-io/valkey-glide/pull/6367))
* Python: Add OpenTelemetry span creation for script invocations (`EVALSHA`) so `invoke_script` calls appear in traces with DB semantic convention attributes ([#6350](https://github.com/valkey-io/valkey-glide/pull/6350))
* Core, Java: add mTLS client certificates with automatic reloading ([#6386](https://github.com/valkey-io/valkey-glide/pull/6386))
* Go: add mTLS client certificates with automatic reloading ([#6384](https://github.com/valkey-io/valkey-glide/pull/6384))
* Node: add mTLS client certificate/key support with automatic certificate reloading ([#6383](https://github.com/valkey-io/valkey-glide/pull/6383))
* Python: add automatic mTLS client certificate/key reload ([#6596](https://github.com/valkey-io/valkey-glide/pull/6596))

## 2.5

### Fixes

* Python: fix(python): correct set return type hint to Optional[Union[TOK, bytes]] ([#6351](https://github.com/valkey-io/valkey-glide/pull/6351))
* Core/FFI: Python: Change async client to use FFI for requests and unnamed pipe for responses instead of UDS for both ([#5637](https://github.com/valkey-io/valkey-glide/pull/5637))
* Python: fix(python): use response_buffer.nbytes for buffer GET capacity ([#6311](https://github.com/valkey-io/valkey-glide/pull/6311))
* Core: fix(core): honor AWS_ENDPOINT_URL_STS in IAM credentials-provider loader ([#5968](https://github.com/valkey-io/valkey-glide/pull/5968))
* Core: Make the pipeline send-timeout liveness-aware so sustained backpressure on a live-but-slow connection waits for channel capacity instead of failing commands with `FatalSendError`, while a genuinely dead connection still fails fast ([#5446](https://github.com/valkey-io/valkey-glide/issues/5446))

### Changes

* Go: Add multi-key `MIGRATE` support ([#6293](https://github.com/valkey-io/valkey-glide/pull/6293))
* Core, Java, Go, Node, Python: Support server-assisted invalidation and add `CLIENT TRACKINGINFO` command ([#5961](https://github.com/valkey-io/valkey-glide/issues/5961))
* Core, Java, Python, Node, Go: Add `MEMORY DOCTOR`, `MEMORY MALLOC-STATS`, `MEMORY PURGE`, and `MEMORY STATS` commands ([#6286](https://github.com/valkey-io/valkey-glide/issues/6286))
* Java: implement MONITOR command ([#6187](https://github.com/valkey-io/valkey-glide/pull/6187))
* Node: Add GlideMonitorClient for MONITOR command ([#6212](https://github.com/valkey-io/valkey-glide/pull/6212))
* Python: implement MONITOR command for sync and async clients ([#6132](https://github.com/valkey-io/valkey-glide/pull/6132))
* Go: Add MonitorClient for MONITOR command ([#6211](https://github.com/valkey-io/valkey-glide/pull/6211))
* Java: Add `MIGRATE KEYS` (multi-key) variant ([#6063](https://github.com/valkey-io/valkey-glide/pull/6063))
* Node: Add `MIGRATE KEYS` (multi-key) variant ([#6064](https://github.com/valkey-io/valkey-glide/pull/6064))
* Core, Java, Python, Node, Go: Add `LATENCY HISTORY`, `LATENCY LATEST`, and `LATENCY RESET` command support ([#6206](https://github.com/valkey-io/valkey-glide/issues/6206))
* Node, Python, Go: Add `FAILOVER` and `REPLICAOF` command support ([#6222](https://github.com/valkey-io/valkey-glide/pull/6222))
* Python Async: Replace UDS+protobuf transport with FFI+pipe architecture. Commands go directly through CFFI to Rust; responses return via anonymous pipe with Rust-native parsing. Adds trio/anyio support, address resolver, cache metrics. +19-21% throughput for simple commands, +11-16% for collections vs v2.4.1. ([#5637](https://github.com/valkey-io/valkey-glide/pull/5637))
* Core: Extend timeout watchdog with structured diagnostics. Timeouts now report classified root cause (ServerUnresponsive, ClientBackpressure, SystemOverload), command phase (Queued vs Sent), inflight trend, per-client p99 latency, and suggested timeout. ([#6044](https://github.com/valkey-io/valkey-glide/pull/6044))
* Core, Python, Java, Node, Go: Add `SAVE`, `BGSAVE` and `BGREWRITEAOF` command support ([#6095](https://github.com/valkey-io/valkey-glide/issues/6095))
* Java: Add `FAILOVER` and `REPLICAOF` command support ([#6170](https://github.com/valkey-io/valkey-glide/pull/6170))
* Core, Java, Python, Node, Go: Add client-wide circuit breaker that detects sustained error rates and rejects requests at the FFI boundary before threads park. Opt-in via `ClientCircuitBreakerConfiguration`. Tracks error rate in a sliding window, trips when threshold is exceeded, and recovers automatically via optimistic HalfOpen with consecutive success validation. Java additionally performs a synchronous pre-check to prevent thread explosion under `managedBlock()`. ([#5996](https://github.com/valkey-io/valkey-glide/issues/5996))
* Core: Use watchdog diagnostics to inform circuit breaker recovery. Tolerates straggler failures in HalfOpen when inflight is draining below trip level, and excludes blocking commands from latency tracking. ([#6208](https://github.com/valkey-io/valkey-glide/issues/6208))
* Core, Python, Java, Node, Go: Add `CLIENT PAUSE` and `CLIENT UNPAUSE` command support ([#6035](https://github.com/valkey-io/valkey-glide/issues/6035))
* Go: Add RESET command support ([#5946](https://github.com/valkey-io/valkey-glide/pull/5946))
* Java: Add RESET command support ([#5947](https://github.com/valkey-io/valkey-glide/pull/5947))
* Core/FFI: Add `MonitorClient` for the MONITOR command ([#5977](https://github.com/valkey-io/valkey-glide/pull/5977))
* Node: Add RESET command support ([#5945](https://github.com/valkey-io/valkey-glide/pull/5945))
* Python: Add RESET command support ([#5944](https://github.com/valkey-io/valkey-glide/pull/5944))
* Python: Add `MIGRATE KEYS` (multi-key) variant ([#6066](https://github.com/valkey-io/valkey-glide/pull/6066))
* Python: Add `MIGRATE` command support ([#5933](https://github.com/valkey-io/valkey-glide/pull/5933))
* Core: Phase 2 client-side caching ([#5962](https://github.com/valkey-io/valkey-glide/pull/5962))
* Core: Add RESET command support ([#5959](https://github.com/valkey-io/valkey-glide/pull/5959))
* Node: Support custom socket address resolution when connecting to valkey ([#5873](https://github.com/valkey-io/valkey-glide/issues/5873))
* Node: Add `MIGRATE` command support ([#5934](https://github.com/valkey-io/valkey-glide/pull/5934))
* Go: Support custom socket address resolution when connecting to valkey ([#5873](https://github.com/valkey-io/valkey-glide/issues/5873))
* Go: Add `MIGRATE` command support ([#5935](https://github.com/valkey-io/valkey-glide/pull/5935))
* Core: Avoid panic on cluster `SCAN` when a read-from-replica AZ affinity strategy is configured. The slot map carries no AZ metadata, so these strategies now fall back to their documented round-robin behavior (replicas for `AZAffinity`, replicas plus primary for `AZAffinityReplicasAndPrimary`) instead of hitting `todo!()` ([#5909](https://github.com/valkey-io/valkey-glide/issues/5909))
* FFI: Add `client_side_cache` configuration to the URI-based client creation API (`create_client_from_uri`) — supports `max_cache_kb`, `entry_ttl_ms`, `eviction_policy` (LRU/LFU), and `enable_metrics` via JSON options; `cache_id` is auto-generated internally ([#5860](https://github.com/valkey-io/valkey-glide/pull/5860))
* Go: Add MEMORY server management commands (MEMORY DOCTOR, MEMORY MALLOC-STATS, MEMORY PURGE, MEMORY STATS) with full cluster routing support ([#5957](https://github.com/valkey-io/valkey-glide/issues/5957))
* Java: Add Jedis 4.x compatibility layer (`jedis-4-compatibility`) and shared `jedis-compat-shared` module for common Jedis 4/5 facade code ([#5751](https://github.com/valkey-io/valkey-glide/pull/5751))

## 2.4

### Fixes

* Core: Add dedicated timeout watchdog thread independent of the Tokio runtime. Under memory pressure or Tokio starvation, `tokio::time::sleep` may not fire on time. The watchdog uses a separate OS thread to guarantee timeout delivery, preventing commands from hanging indefinitely when the async runtime is overloaded. ([#5752](https://github.com/valkey-io/valkey-glide/issues/5752))
* Core: Propagate per-command response timeout to the multiplexed connection layer. The configured `request_timeout` (including blocking command timeouts) is now enforced at the connection level, ensuring commands time out reliably instead of waiting indefinitely with `Duration::MAX`. ([#5581](https://github.com/valkey-io/valkey-glide/issues/5581))
* Core: Fall back to existing cluster connections when initial nodes are unavailable during topology refresh. When `refreshTopologyFromInitialNodes=true` and the initial/seed node is unreachable (e.g., dead primary during failover), the topology refresh now queries healthy existing connections instead of failing silently. This complements #5812 to enable failover recovery when the seed node itself is the failed node. ([#5814](https://github.com/valkey-io/valkey-glide/pull/5814))
* Node: Fix SIGSEGV in pubsub under pnpm strict hoisting — replace `value_from_split_pointer(high_u32, low_u32)` with `value_from_pointer(i64)` in the Node.js Rust client to accept response pointers as a single integer, eliminating the high/low split that caused upper 32-bit truncation when `protobufjs.util.Long` was null ([#5851](https://github.com/valkey-io/valkey-glide/issues/5851))

### Changes

* Go: Expose client interfaces (`BaseClientCommands`, `GlideClientCommands`, `GlideClusterClientCommands`, etc.) as a public package for testing and abstraction ([#4900](https://github.com/valkey-io/valkey-glide/issues/4900))
* Core, Python, Java, Node, Go: Add `NodeDiscoveryMode` configuration for standalone clients — `STATIC` mode skips role detection for proxy compatibility (e.g., Envoy), `DISCOVER_ALL` mode discovers full topology (primary + all replicas) from any starting node ([#5724](https://github.com/valkey-io/valkey-glide/pull/5724))
* Go: Add Valkey Search (FT) command support — FT.CREATE, FT.SEARCH, FT.AGGREGATE, FT.DROPINDEX, FT.LIST, FT.INFO, FT.EXPLAIN, FT.EXPLAINCLI, FT.ALIASADD, FT.ALIASDEL, FT.ALIASUPDATE, FT.ALIASLIST with full Valkey Search 1.2 options for both standalone and cluster clients ([#5590](https://github.com/valkey-io/valkey-glide/pull/5590))
* FFI: Add URI-based client creation API with full ConnectionRequest support ([#5620](https://github.com/valkey-io/valkey-glide/pull/5620))
  * New `create_client_from_uri` FFI function accepts Redis/Valkey URI strings and optional JSON configuration
  * Supports all ConnectionRequest options including compression, periodic checks, IAM credentials, and pub/sub subscriptions
  * Simplifies language binding implementation by moving protobuf construction to Rust
* Python, Java, Node, Core: Add valkey-search 1.2 support — FT.CREATE index options (SCore, LANGUAGE, SKIPINITIALSCAN, MINSTEMSIZE, WITHOFFSETS/NOOFFSETS, NOSTOPWORDS/STOPWORDS, PUNCTUATION) and field options (NOSTEM, WEIGHT, WITHSUFFIXTRIE/NOSUFFIXTRIE, SORTABLE); FT.SEARCH options (VERBATIM, INORDER, SLOP, SORTBY, WITHSORTKEYS, NOCONTENT, DIALECT, shard scope, consistency mode); FT.AGGREGATE options (VERBATIM, INORDER, SLOP, DIALECT); FT.INFO scope options (LOCAL, PRIMARY, CLUSTER, shard scope, consistency mode); WITHSORTKEYS and NOCONTENT response conversion in glide-core. Python `options` parameter for `ft.search()` and `ft.aggregate()` is now optional. ([#5571](https://github.com/valkey-io/valkey-glide/pull/5571))
* Node: Fix HNSW vector field serialization to use correct property names (`numberOfEdges`, `vectorsExaminedOnConstruction`, `vectorsExaminedOnRuntime`) matching the TypeScript type definitions ([#5571](https://github.com/valkey-io/valkey-glide/pull/5571))
* Node: Fix missing `break` for NUMERIC field case in FT.CREATE serialization, preventing fall-through to VECTOR case ([#5571](https://github.com/valkey-io/valkey-glide/pull/5571))
* Core, Python, Java, Node: Add missing NOCONTENT and DIALECT options for FT.SEARCH ([#5550](https://github.com/valkey-io/valkey-glide/pull/5550))
* Go: Add JSON.SET and JSON.GET commands ([#5589](https://github.com/valkey-io/valkey-glide/issues/5589))
* Go: Add JSON module commands - DEL, FORGET, CLEAR, MGET, TYPE, and array operations ([#5589](https://github.com/valkey-io/valkey-glide/issues/5589))
* Go: Add JSON TOGGLE, RESP, DEBUG, numeric, string and object commands ([#5589](https://github.com/valkey-io/valkey-glide/issues/5589))
* Core: Fix Tokio runtime blocking in cluster async code by replacing std::sync locks with async-safe alternatives ([#5450](https://github.com/valkey-io/valkey-glide/issues/5450))
* Core: Maintain throughput during cluster failover by making reconnection non-blocking ([#4990](https://github.com/valkey-io/valkey-glide/issues/4990))
* FFI: Add OpenTelemetry DB semantic convention attributes to FFI path ([#5596](https://github.com/valkey-io/valkey-glide/issues/5596))
* Java: Add cluster management commands (CLUSTER MEET, CLUSTER FORGET, CLUSTER REPLICATE, CLUSTER REPLICAS, CLUSTER COUNT-FAILURE-REPORTS, CLUSTER FAILOVER, CLUSTER SETSLOT, CLUSTER BUMPEPOCH, CLUSTER SET-CONFIG-EPOCH, CLUSTER FLUSHSLOTS, CLUSTER RESET, READONLY, READWRITE, ASKING, CLUSTER SAVECONFIG, CLUSTER GETKEYSINSLOT) ([#5503](https://github.com/valkey-io/valkey-glide/pull/5503))
* Go: Client-Side Caching Support ([#5721](https://github.com/valkey-io/valkey-glide/pull/5721))
* Go: Add `PeriodicChecks` configuration to `AdvancedClusterClientConfiguration` — supports `PeriodicChecksEnabled`, `PeriodicChecksDisabled`, and `PeriodicChecksManualInterval` modes for controlling cluster topology check intervals ([#5842](https://github.com/valkey-io/valkey-glide/issues/5842))
* Java: Client-Side Caching Support ([#5172](https://github.com/valkey-io/valkey-glide/pull/5172))
* Node: Client-Side Caching Support ([#5720](https://github.com/valkey-io/valkey-glide/pull/5720))
* Java: Support custom socket address resolution when connecting to valkey ([#4396](https://github.com/valkey-io/valkey-glide/issues/4396))
* Python: Support custom socket address resolution when connecting to valkey ([#5873](https://github.com/valkey-io/valkey-glide/issues/5873))

### Fixes

* Java: Populate actual `lib-ver` instead of `unknown` in `CLIENT INFO` responses ([#5634](https://github.com/valkey-io/valkey-glide/issues/5634))
* Core: Add adaptive timeout on pipeline send to detect dead connections during half-open TCP scenarios, replace `now_or_never()` with proper `poll()` in recovery to fix busy-spin, remove `loop{}` from `poll_flush`, and fail pending requests immediately during recovery to prevent OOM under sustained partition ([#5715](https://github.com/valkey-io/valkey-glide/issues/5715), [#5716](https://github.com/valkey-io/valkey-glide/issues/5716))
* Core, Java: Add diagnostic logging for failover, topology refresh, and pipeline issues — lazy and rate-limited logging macros in logger_core, MOVED error scenario identification, topology refresh throttle/overwrite tracking, pipeline send/response timing, inflight slot exhaustion, recovery state transitions, adaptive health snapshots, and Java-side timeout elapsed time. Fix Java Logger Supplier overloads to check level before evaluating ([#5756](https://github.com/valkey-io/valkey-glide/pull/5756), [#5791](https://github.com/valkey-io/valkey-glide/pull/5791))
* Core: Skip compression/decompression code paths when compression is not configured to eliminate per-command overhead ([#5644](https://github.com/valkey-io/valkey-glide/pull/5644))
* Python Async: `Logger.logger_level` now returns a `Level` enum instead of an internal PyO3 type, consistent with the sync client ([#5637](https://github.com/valkey-io/valkey-glide/pull/5637))
* Core: Fixed standalone client read strategy AZAffinity including the primary in the read list when it is in the same az as the client.
* Core: Fixed standalone client read strategy AZAffinityAndPrimary not falling back to primary when there are no local replicas.

## 2.3.1

### Fixes

* Core: Add IAM token cache support for cluster reconnections — switch from push to pull model for IAM token refresh, add `IAMTokenProvider` trait so the cluster reconnection loop fetches a fresh IAM token before each connection attempt, preventing AUTH failures when tokens expire during node downtime ([#5663](https://github.com/valkey-io/valkey-glide/pull/5663))
* Core: Refresh IAM token before slot refresh and initial node reconnection — ensure `cluster_params.password` has a valid IAM token in `refresh_slots_inner` and `reconnect_to_initial_nodes`, not just the per-node reconnection loop ([#5663](https://github.com/valkey-io/valkey-glide/pull/5663))

### Operational Enhancements

* Python Sync: Use `current_thread` tokio runtime for sync FFI clients, eliminating cross-thread condvar wake overhead on every command ([#5083](https://github.com/valkey-io/valkey-glide/issues/5083), [#5624](https://github.com/valkey-io/valkey-glide/issues/5624))
* Python: Add arena-based response allocator and PyO3 fast response parser to eliminate per-node heap allocations and replace CFFI-based recursive Python traversal for large responses ([#5083](https://github.com/valkey-io/valkey-glide/issues/5083), [#5624](https://github.com/valkey-io/valkey-glide/issues/5624))
* Python Async: Replace protobuf+UDS transport with direct FFI calls and shared buffered pipe for response delivery, eliminating protobuf overhead, UDS socket I/O, and GIL contention ([#5083](https://github.com/valkey-io/valkey-glide/issues/5083), [#5624](https://github.com/valkey-io/valkey-glide/issues/5624))
* Python: Move shared code (logger, script, cluster_scan_cursor, request_type, ffi_helpers, opentelemetry) to glide-shared, eliminating circular dependencies between glide-async and glide-sync ([#5083](https://github.com/valkey-io/valkey-glide/issues/5083))
* Python Async: Add `GLIDE_TOKIO_WORKER_THREADS` env var to control tokio worker thread count for the async client runtime (default 1) ([#5083](https://github.com/valkey-io/valkey-glide/issues/5083))

## 2.3

### Changes

* Node: add dynamic PubSub support ([#5295](https://github.com/valkey-io/valkey-glide/pull/5295))
* Java: Add uber JAR support for multi-platform builds ([5484](https://github.com/valkey-io/valkey-glide/issues/5484))
* Java: Add EVAL_RO, EVALSHA_RO, and SCRIPT DEBUG commands ([#5125](https://github.com/valkey-io/valkey-glide/pull/5125))
* Core: Add client certificate and private key support for mTLS ([#5092](https://github.com/valkey-io/valkey-glide/issues/5092))
* Python: Add client certificate and private key support for mTLS ([5123](https://github.com/valkey-io/valkey-glide/issues/5123))
* Java: Add KEYS, MIGRATE and WAITAOF command support ([#5017](https://github.com/valkey-io/valkey-glide/pull/5107))
* Python Sync: Add dynamic PubSub methods (subscribe, psubscribe, unsubscribe, punsubscribe, ssubscribe, sunsubscribe with timeout support), get_subscriptions(), and pubsub_reconciliation_interval configuration support. Unified configuration classes with async client by importing from glide_shared ([#5270](https://github.com/valkey-io/valkey-glide/pull/5270))
* Go: Add dynamic PubSub methods (Subscribe, PSubscribe, SSubscribe, Unsubscribe, PUnsubscribe, SUnsubscribe with blocking variants and timeout support), GetSubscriptions(), and helper functions (UnsubscribeAll, PUnsubscribeAll, SUnsubscribeAll). Export pubsub constants (AllChannels, AllPatterns, AllShardedChannels)
* Java: Add ACL server management commands (ACL CAT, ACL DELUSER, ACL DRYRUN, ACL GENPASS, ACL GETUSER, ACL LIST, ACL LOAD, ACL LOG, ACL SAVE, ACL SETUSER, ACL USERS, ACL WHOAMI)
* Go: Add ACL server management commands (ACL CAT, ACL DELUSER, ACL DRYRUN, ACL GENPASS, ACL GETUSER, ACL LIST, ACL LOAD, ACL LOG, ACL LOGRESET, ACL SAVE, ACL SETUSER, ACL USERS, ACL WHOAMI)
* Core: Fix typo in ACL SETUSER command mapping (AclSetSser → AclSetUser)
* Python: Add inflight request limit support to sync client
* Python Sync: Add OpenTelemetry support with traces and metrics configuration
* Python: Move OpenTelemetry config classes to glide_shared for code reuse between async and sync clients
* Java: Add dynamic PubSub methods (subscribe, psubscribe, ssubscribe, unsubscribe, punsubscribe, sunsubscribe and their non-blocking "Lazy" variants), getSubscriptions() for subscription state tracking, pubsubReconciliationIntervalMs configuration option, and subscription_out_of_sync_count and subscription_last_sync_timestamp metrics ([#5267](https://github.com/valkey-io/valkey-glide/issues/5267))
* Go: Add ALLOW_NON_COVERED_SLOTS flag support for cluster scan ([#4895](https://github.com/valkey-io/valkey-glide/issues/4895))
* Core: Track HELLO and AUTH state for reconnection ([#5145](https://github.com/valkey-io/valkey-glide/issues/5145))
* Core: Add support for ZRANGEBYLEX, ZRANGEBYSCore, ZREVRANGE, ZREVRANGEBYLEX, and ZREVRANGEBYSCore commands in request_type ([#5379](https://github.com/valkey-io/valkey-glide/pull/5379))
* Go: Add CLUSTER management commands (CLUSTER INFO, CLUSTER NODES, CLUSTER SHARDS, CLUSTER KEYSLOT, CLUSTER MYID, CLUSTER MYSHARDID, CLUSTER GETKEYSINSLOT, CLUSTER COUNTKEYSINSLOT, CLUSTER LINKS) ([#5206](https://github.com/valkey-io/valkey-glide/issues/5206))
* Java: Make client java 8 compatible
* Node: Add OpenTelemetry parent span context propagation support ([#4655](https://github.com/valkey-io/valkey-glide/issues/4655))
* Java: Add cluster information and topology commands (CLUSTER INFO, CLUSTER NODES, CLUSTER SHARDS, CLUSTER LINKS, CLUSTER MYID, CLUSTER MYSHARDID) with batch support ([#5106](https://github.com/valkey-io/valkey-glide/issues/5106))
* Core: Add read only flag, enforcing no write commands and allowing for connecting without a primary ([#5411](https://github.com/valkey-io/valkey-glide/issues/5411))
* Java: Add cluster information and topology commands (CLUSTER INFO, CLUSTER NODES, CLUSTER SHARDS, CLUSTER LINKS, CLUSTER MYID, CLUSTER MYSHARDID) and cluster slot commands (CLUSTER KEYSLOT, CLUSTER COUNTKEYSINSLOT, CLUSTER GETKEYSINSLOT, CLUSTER ADDSLOTS, CLUSTER ADDSLOTSRANGE, CLUSTER DELSLOTS, CLUSTER DELSLOTSRANGE) with batch support ([#5106](https://github.com/valkey-io/valkey-glide/issues/5106))
* Core: Add OpenTelemetry DB semantic convention attributes to command spans ([#5416](https://github.com/valkey-io/valkey-glide/issues/5416))
* Python Sync: Accept `bytearray` and `memoryview` as command argument types to improve performance by reducing copies ([#5492](https://github.com/valkey-io/valkey-glide/pull/5492))
* Python Sync: Add response buffer support to get() to improve performance by reducing copies ([#5493](https://github.com/valkey-io/valkey-glide/pull/5493))

### Fixes

* Java: Fix thread safety issue in NativeClusterScanCursor causing potential JVM crash ([#5527](https://github.com/valkey-io/valkey-glide/issues/5527))
* Java: optimize `convertMapToKeyValueStringArray` and `convertMapToKeyValueGlideStringArray` to fix performance bottleneck and ArrayStoreException ([#5602](https://github.com/valkey-io/valkey-glide/issues/5602))
* Core: Fix empty hostname in CLUSTER SLOTS metadata causing AllConnectionsUnavailable ([#5367](https://github.com/valkey-io/valkey-glide/issues/5367)). AWS ElastiCache (plaintext, cluster mode) returns `hostname: ""` in node metadata, which was used as the connection address instead of falling back to the IP.
* Node: Fix to handle non-string types in toBuffersArray ([#4842](https://github.com/valkey-io/valkey-glide/issues/4842))
* Core: Enforce connection_timeout for initial standalone connection failures ([#4991](https://github.com/valkey-io/valkey-glide/issues/4991))
* Node: Fixed `Failed to convert napi value Undefined into rust type u32` error ([#5128](https://github.com/valkey-io/valkey-glide/pull/5128))
* Python: Fix Sphinx docs build failure with duplicate object warnings ([#5163](https://github.com/valkey-io/valkey-glide/issues/5163))
* Java: Restore shading and relocation of protobuf ([#5031](https://github.com/valkey-io/valkey-glide/pull/5031))
* Core: Remove DEFAULT_CLIENT_CREATION_TIMEOUT and honor user-provided connection timeout by centralizing timeout logic in ConnectionRequest ([#5183](https://github.com/valkey-io/valkey-glide/issues/5183))
* Java: Fix mget large binary data issue and add test case ([#5341](https://github.com/valkey-io/valkey-glide/pull/5341))
* Java: Fix resource leak in integration tests where clients created via @MethodSource were never closed ([#5347](https://github.com/valkey-io/valkey-glide/issues/5347))
* Java: Fix hanging issue in AWS Lambda when response exceeds 16KB ([#5081](https://github.com/valkey-io/valkey-glide/issues/5081))
* Core: Fixed a bug where permission errors for CLUSTER SLOTS command is not surfaced during initial connection ([#5486](https://github.com/valkey-io/valkey-glide/pull/5486))
* Core: Move credential fetching to token generation time ([#5508](https://github.com/valkey-io/valkey-glide/pull/5508))
* Python: Fix async client becoming permanently unusable after cancelled commands due to callback index collisions ([#5442](https://github.com/valkey-io/valkey-glide/issues/5442))

### Operational Enhancements

* Docs: Add missing references to windows-x86_64 classifier ([#5028](https://github.com/valkey-io/valkey-glide/pull/5028))
* CI: Upgrade ORT from 46.0.0 to 59.0.0 to fix Synopsys repository 403 error ([#5169](https://github.com/valkey-io/valkey-glide/pull/5169))
* Java: Enable Windows integration test in workflow through WSL([#5112](https://github.com/valkey-io/valkey-glide/pull/5112/))

## 2.2.5

### Changes

* All Clients: Add TCPNoDelay option ([#5100](https://github.com/valkey-io/valkey-glide/pull/5100))

### Fixes

* Node: Fix `Failed to convert napi value Undefined into rust type u32` error ([#5128](https://github.com/valkey-io/valkey-glide/pull/5128))

### Operational Enhancements

* Rust: Rust 2024 compatibility fix ([#5101](https://github.com/valkey-io/valkey-glide/pull/5101))
* CI: Update NPM CD with Trusted Publishing ([#5110](https://github.com/valkey-io/valkey-glide/pull/5110))
* Rust: Add RUSTSEC-2025-0141 to ignore list in deny.toml ([#5137](https://github.com/valkey-io/valkey-glide/pull/5137))

## 2.2.3

### Changes

* Java: Unshadow Apache Commons Lang3 to avoid failures due to dependency conflicts ([#5074](https://github.com/valkey-io/valkey-glide/pull/5074))

### Fixes

* Core: Rust Lint is failing due to unmaintained advisory detected (RUSTSEC-2025-0141) ([#5136](https://github.com/valkey-io/valkey-glide/issues/5136))

## 2.2.2

### Security Fixes

* Java: Update Apache Commons Lang3 from 3.13.0 to 3.20.0 to address CVE-2025-48924 ([#5049](https://github.com/valkey-io/valkey-glide/pull/5049))

### Fixes

* Java: Add protobuf shading to prevent version conflicts with user dependencies ([#5031](https://github.com/valkey-io/valkey-glide/pull/5031))
* Java: Fix classloader issues in JNI method caching ([#5029](https://github.com/valkey-io/valkey-glide/pull/5029))
* Python: Update sniffio dependency ([#5044](https://github.com/valkey-io/valkey-glide/pull/5044))

## 2.2.1

### Changes

* Java: Windows Java binaries are now available ([#5049](https://github.com/valkey-io/valkey-glide/pull/5049))

## 2.2

### Changes

* Added in documentation to how to use Lua scripts with Glide
* Java: Add refreshTopologyFromInitialNodes Configuration Option ([#4870](https://github.com/valkey-io/valkey-glide/pull/4870))
* Go: add RefreshTopologyFromInitialNodes configuration option ([#4871](https://github.com/valkey-io/valkey-glide/pull/4871))
* Node: add refreshTopologyFromInitialNodes configuration option ([#4872](https://github.com/valkey-io/valkey-glide/pull/4872))
* Python: Add IAM authentication support with manual token refresh ([#4890](https://github.com/valkey-io/valkey-glide/pull/4890))
* Java: Add IAM authentication support for ElastiCache/MemoryDB ([#4891](https://github.com/valkey-io/valkey-glide/pull/4891/))
* FFI/Go: Add IAM authentication support with automatic token refresh ([#4892](https://github.com/valkey-io/valkey-glide/pull/4892))
* Go: Add TLS Custom Root Certificate Support for Go Client ([#4921](https://github.com/valkey-io/valkey-glide/pull/4921))
* Go: Enable `go mod vendor` support via CI-generated marker files ([#4721](https://github.com/valkey-io/valkey-glide/issues/4721))
* Python: Add Custom Root Certificate Support for Python TLS Connections ([#4930](https://github.com/valkey-io/valkey-glide/pull/4930))
* Python: Add Python 3.14 support ([#4897](https://github.com/valkey-io/valkey-glide/pull/4897))
* Java: Implement TLS support for Java client ([#4905](https://github.com/valkey-io/valkey-glide/pull/4905))
* Node: Implement TLS support for Node client ([#4911](https://github.com/valkey-io/valkey-glide/pull/4911))

### Fixes

* Node: Resolved circular import dependency between `BaseClient.ts`, `GlideClient.ts`, and `GlideClusterClient.ts.` ([#4607](https://github.com/valkey-io/valkey-glide/issues/4607))
* Java: Add Windows support and prevent double-loading in NativeUtils library loader
* Java: Add cross-platform compatibility fixes for builds and tests - UTF-8 encoding, platform-native line endings, and system temp directory usage
* Java: Make shutdown hook optional ([#4935](https://github.com/valkey-io/valkey-glide/pull/4935))
* Core/Java/Node: Fix default connection timeout value for clients ([#4966](https://github.com/valkey-io/valkey-glide/pull/4966))
* Java: Fix java applications from hanging after logic completion ([#4984](https://github.com/valkey-io/valkey-glide/pull/4984))
* Java: Fix Gradle toolchain provisioning conflicts on Windows self-hosted runners

### Operational Enhancements

* Rust: Updates the `install-rust-and-protoc` action to explicitly include the `rustfmt` and `clippy` components. ([#4816](https://github.com/valkey-io/valkey-glide/issues/4816))

## 2.1.1

### Changes

* Node: Move SELECT command from GlideClient to BaseClient ([#4849](https://github.com/valkey-io/valkey-glide/pull/4849))
* Java: Add SELECT command support for cluster mode ([#4846](https://github.com/valkey-io/valkey-glide/pull/4846))
* Go: Add Select command support for ClusterClient ([#4850](https://github.com/valkey-io/valkey-glide/pull/4850))
* Python: Move SELECT command from StandaloneCommands to CoreCommands([#4851](https://github.com/valkey-io/valkey-glide/pull/4851))
* Python: Consolidate COPY command implementation to CoreCommands ([#4806](https://github.com/valkey-io/valkey-glide/pull/4659))
* Node: move COPY command to BaseClient and add comprehensive tests([#4805](https://github.com/valkey-io/valkey-glide/pull/4805))
* Go: Add DBDestination option tests to CopyWithOptions command ([#4802](https://github.com/valkey-io/valkey-glide/pull/4802))
* Java: Move COPY command with database ID from standalone to base client ([#4804](https://github.com/valkey-io/valkey-glide/pull/4804))
* Go: Add cluster support for Move command ([#4748](https://github.com/valkey-io/valkey-glide/pull/4748))
* Node: add MOVE command support for cluster clients ([#4750](https://github.com/valkey-io/valkey-glide/pull/4750))
* Python: Add MOVE command support for cluster clients ([#4751](https://github.com/valkey-io/valkey-glide/pull/4751))
* Java: Add cluster support for Move command ([#4749])(https://github.com/valkey-io/valkey-glide/pull/4749)

### Fixes

* Core: Fix SELECT Command Database Persistence Across Reconnections ([#4764](https://github.com/valkey-io/valkey-glide/issues/4764))
* Rust: Updates the `install-rust-and-protoc` action to explicitly include the `rustfmt` and `clippy` components. ([#4816](https://github.com/valkey-io/valkey-glide/issues/4816))
* Core: Persist Client Name Across Reconnections for CLIENT SETNAME Command. ([#4841](https://github.com/valkey-io/valkey-glide/pull/4841))

## 2.1

### Changes

* Go: Hash Field Expiration commands for Valkey 9 ([#4554](https://github.com/valkey-io/valkey-glide/pull/4554))
* Java: Valkey 9 new commands HASH items expiration ([#4556](https://github.com/valkey-io/valkey-glide/pull/4556))
* Node: Valkey 9 support - Add Hash Field Expiration Commands Support ([#4598](https://github.com/valkey-io/valkey-glide/pull/4598))
* Python: Valkey 9 new hash field expiration commands ([#4610](https://github.com/valkey-io/valkey-glide/pull/4610))
* Node: Add Multi-Database Support for Cluster Mode (Valkey 9.0) ([#4657](https://github.com/valkey-io/valkey-glide/pull/4657))
* Java: Multi-Database Support for Cluster Mode Valkey 9.0 ([#4658](https://github.com/valkey-io/valkey-glide/pull/4658))
* Go: Add Multi-Database Support for Cluster Mode Valkey 9.0 ([#4660](https://github.com/valkey-io/valkey-glide/pull/4660))
* Python: Add Multi-Database Support for Cluster Mode Valkey 9.0 ([#4659](https://github.com/valkey-io/valkey-glide/pull/4659))

### Fixes

* Java: Add lazy connection support to Java module ([#4350](https://github.com/valkey-io/valkey-glide/pull/4370))
* Go: Add lazy connection support ([#4374](https://github.com/valkey-io/valkey-glide/pull/4374))
* Java/Go/Node/Python: Fixed LOLWUT tests and Jedis compatibility layer tests for Valkey 9 ([#4632](https://github.com/valkey-io/valkey-glide/pull/4632))
* Core/Python: fix 100% CPU when blocking command returning data after client closure ([#4612](https://github.com/valkey-io/valkey-glide/pull/4612))
* Java: Add MUSL build to support Alpine Linux ([#4073](https://github.com/valkey-io/valkey-glide/issues/4073))

### Operational Enhancements

* FFI: Add MIRI tests to CI ([#4506](https://github.com/valkey-io/valkey-glide/pull/4506))
* Core/FFI/Go - SpanFromContext implementation ([#4507](https://github.com/valkey-io/valkey-glide/pull/4507))

## 2.0 (2025-06-18)

### Changes

* Core/FFI/Go: Fix memory leak in FFI layer ([#4395](https://github.com/valkey-io/valkey-glide/pull/4395))

## 2.0.1 (2025-07-19)

### Changes

* Java: fix addReturnField method in FT module ([#4084]https://github.com/valkey-io/valkey-glide/pull/4084)
* Python: Create openTelemetry span to measure command latency ([#3985](https://github.com/valkey-io/valkey-glide/pull/3985))
* Go: Add client name and version identifiers ([#3903](https://github.com/valkey-io/valkey-glide/pull/3903))
* Python: Add support for Trio ([#3465](https://github.com/valkey-io/valkey-glide/pull/3465))
* Core: Add an OK response type to FFI ([#3630](https://github.com/valkey-io/valkey-glide/pull/3630))
* Core: Move UDS Socket Filename to tmp ([#3615](https://github.com/valkey-io/valkey-glide/pull/3615))
* Node: Added build improvements for rust release ([#3606](https://github.com/valkey-io/valkey-glide/pull/3606))
* Core: Ensure UDS socket filename is truly unique. ([#3596](https://github.com/valkey-io/valkey-glide/pull/3596))
* Python: Add Batch support ([#3555](https://github.com/valkey-io/valkey-glide/pull/3555))
* Node: Fix type declarations ([#3489](https://github.com/valkey-io/valkey-glide/pull/3489))
* Core: Add `opentelemetry` protocols support ([#3191](https://github.com/valkey-io/valkey-glide/pull/3191))
* Node: Make the request sampling percentage configurable, and create spans conditionally based on it ([#3830](https://github.com/valkey-io/valkey-glide/pull/3830))
* Core/Node: Make OpenTelemetry config to be global per process ([#3771](https://github.com/valkey-io/valkey-glide/pull/3771))
* Core/Node: Create openTelemetry span to measure command latency ([#3391](https://github.com/valkey-io/valkey-glide/pull/3391))
* Core: Add `opentelemetry` metrics support ([#3466](https://github.com/valkey-io/valkey-glide/pull/3466))
* Core: Add `opentelemetry` moved, retry attemps counters and set_status ([#3944](https://github.com/valkey-io/valkey-glide/pull/3944))
* Node: Fix ZADD, enabling `+inf` and `-inf` as score ([#3370](https://github.com/valkey-io/valkey-glide/pull/3370))
* Go: Add JSON.SET and JSON.GET ([#3115](https://github.com/valkey-io/valkey-glide/pull/3115))
* Csharp: updating xUnit, adding xUnit analyser rules and guidelines ([#3035](https://github.com/valkey-io/valkey-glide/pull/3035))
* Go: Add ZRangeStore ([3105](https://github.com/valkey-io/valkey-glide/pull/3105))
* Go: Add `ZUNION` ([#3119](https://github.com/valkey-io/valkey-glide/pull/3119))
* Go: Add `ZUNIONSTORE` ([#3136](https://github.com/valkey-io/valkey-glide/pull/3136))
* Go: Add `XINFO CONSUMERS` ([#3120](https://github.com/valkey-io/valkey-glide/pull/3120))
* Go: Add `XINFO GROUPS` ([#3106](https://github.com/valkey-io/valkey-glide/pull/3106))
* Go: Add `ZInterCard` ([#3078](https://github.com/valkey-io/valkey-glide/issues/3078))
* Go: Add `ZLEXCOUNT` ([#3140](https://github.com/valkey-io/valkey-glide/pull/3140))
* Go: Updating examples for `ZInterCard` ([#3232](https://github.com/valkey-io/valkey-glide/pull/3232))
* Go: Fix channel passing from Go to Rust by using `runtime.Pinner` or `cgo.Handle` ([#3208](https://github.com/valkey-io/valkey-glide/pull/3208))
* Go: Add `BZPOPMAX` and `ZMPOP` [#3257](https://github.com/valkey-io/valkey-glide/pull/3257)
* Go: Fix data race on the coreClient with `sync.Mutex` and a channel map ([#3236](https://github.com/valkey-io/valkey-glide/pull/3236))
* Go: Adding support for Az Affinity ([#3235](https://github.com/valkey-io/valkey-glide/pull/3235))
* Go: Adding support for advanced client configs and connectionTimeout ([#3290](https://github.com/valkey-io/valkey-glide/pull/3290))
* Go: Add Cluster Scan support ([#3295](https://github.com/valkey-io/valkey-glide/pull/3295))
* Go: Fix unsafe precondition violation for the slice::from_raw_parts ([#3350](https://github.com/valkey-io/valkey-glide/issues/3350))
* Go: Add `GeoAdd` and the Geospatial interface ([#3366](https://github.com/valkey-io/valkey-glide/pull/3366))
* Go: Add `LOLWUT` ([#3355](https://github.com/valkey-io/valkey-glide/pull/3355))
* Go: Add `BITPOS` ([#3407](https://github.com/valkey-io/valkey-glide/pull/3407))
* Go: Add `FLUSHALL` ([#3117](https://github.com/valkey-io/valkey-glide/pull/3117))
* Go: Add `FLUSHDB` ([#3117](https://github.com/valkey-io/valkey-glide/pull/3117))
* Go: Add `PFMERGE` ([#3082](https://github.com/valkey-io/valkey-glide/pull/3082))
* Go: Add password update api ([#3346](https://github.com/valkey-io/valkey-glide/pull/3346))
* Add/update documentation for `GlideClient` and `GlideClusterClient` and for factory constructor for all clients ([#3418](https://github.com/valkey-io/valkey-glide/pull/3418))
* Go: Add `BITOP` ([#3384](https://github.com/valkey-io/valkey-glide/pull/3384))
* Go: Add `GeoHash` ([#3439](https://github.com/valkey-io/valkey-glide/pull/3439))
* Go/Core: Move FFI to a dedicated folder for reusability ([#3372](https://github.com/valkey-io/valkey-glide/pull/3372))
* Go: Add `GeoPos` ([#3409](https://github.com/valkey-io/valkey-glide/pull/3409))
* Go: Add `GeoDist` ([#3446](https://github.com/valkey-io/valkey-glide/pull/3446))
* Go: Add `ClientId` ([#3077](https://github.com/valkey-io/valkey-glide/pull/3077))
* Go: Add `LastSave` ([#3086](https://github.com/valkey-io/valkey-glide/pull/3086))
* Go: Add `Config Reset Stat` ([#3121](https://github.com/valkey-io/valkey-glide/pull/3121))
* Go: Add `GeoSearch` and `GeoSearchStore` ([#3385](https://github.com/valkey-io/valkey-glide/pull/3385))
* Go: Add `ConfigSet` & `ConfigGet` for cluster client ([#3274](https://github.com/valkey-io/valkey-glide/pull/3274), [#3594](https://github.com/valkey-io/valkey-glide/pull/3594))
* Go: Add `ClientSetName` & `ClientGetName` ([#3302](https://github.com/valkey-io/valkey-glide/pull/3302))
* Go: Add `Move` ([#3369](https://github.com/valkey-io/valkey-glide/pull/3369))
* Go: Add `Scan` ([#3378](https://github.com/valkey-io/valkey-glide/pull/3378))
* Go: Add `LCS` ([#3475](https://github.com/valkey-io/valkey-glide/pull/3475))
* Core/FFI/Go: Add Support for Async and Sync Client Types in FFI ([#3451](https://github.com/valkey-io/valkey-glide/pull/3451))
* Core/FFI/Go: Add support for async and sync client types in FFI ([#3451](https://github.com/valkey-io/valkey-glide/pull/3451))
* Go: Add PubSub support ([#3417](https://github.com/valkey-io/valkey-glide/pull/3417))
* Go: Add `Publish` ([#3417](https://github.com/valkey-io/valkey-glide/pull/3417))
* Go: Add `PubSubChannels` ([#3665](https://github.com/valkey-io/valkey-glide/pull/3665))
* Go: Add `PubSubNumPat` ([#3666](https://github.com/valkey-io/valkey-glide/pull/3666))
* Go: Add `PubSubNumSub` ([#3667](https://github.com/valkey-io/valkey-glide/pull/3667))
* Go: Add `Sharded Publish` ([#3692](https://github.com/valkey-io/valkey-glide/pull/3692))
* Go: Add `PubSub ShardChannels` ([#3695](https://github.com/valkey-io/valkey-glide/pull/3695))
* Go: Add `PubSub ShardNumSub` ([#3708](https://github.com/valkey-io/valkey-glide/pull/3708))
* Go: Add `Config Rewrite` ([#3156](https://github.com/valkey-io/valkey-glide/pull/3156))
* Go: Add `Random Key` ([#3358](https://github.com/valkey-io/valkey-glide/pull/3358))
* Go: Add Function Load, Function Flush, FCall and FCall_RO ([#3474](https://github.com/valkey-io/valkey-glide/pull/3474))
* Python: Add equallity check to `ExpirySet` ([#3554](https://github.com/valkey-io/valkey-glide/issues/3554))
* Java: Add Batches support ([#3561](https://github.com/valkey-io/valkey-glide/pull/3561))
* Go: Add Function Stats ([#3526](https://github.com/valkey-io/valkey-glide/pull/3526))
* Go: Add Function Delete ([#3603](https://github.com/valkey-io/valkey-glide/pull/3603))
* Go: Add Function Kill ([#3604](https://github.com/valkey-io/valkey-glide/pull/3604))
* Go: Script eval/load([#3605](https://github.com/valkey-io/valkey-glide/pull/3605))
* Benchmarks: Fix rust benchmark latencies calculation
* Go: Add Script Exists ([#3649](https://github.com/valkey-io/valkey-glide/pull/3649))
* Python: Moved the tests folder to be under the root folder ([#3661](https://github.com/valkey-io/valkey-glide/pull/3661))
* Go: Add script flush command([#3662](https://github.com/valkey-io/valkey-glide/pull/3662))
* Go: Add Script Kill ([#3670](https://github.com/valkey-io/valkey-glide/pull/3670))
* Go: Add Script Show ([#3668](https://github.com/valkey-io/valkey-glide/pull/3668))
* Go: Add Function List ([#3673](https://github.com/valkey-io/valkey-glide/pull/3673))
* Go: Move private types to internal packages ([#3841](https://github.com/valkey-io/valkey-glide/pull/3841))
* Go: Move private interfaces to internal packages ([#3856](https://github.com/valkey-io/valkey-glide/pull/3856))
* Java, Python, Node: Fix logger docs, add option to log an exception ([#3844](https://github.com/valkey-io/valkey-glide/pull/3844))
* Core: Refactor socket listener to use a runtime that lives for the application lifetime [#3842](https://github.com/valkey-io/valkey-glide/pull/3842)
* Go: Add Function Dump & Restore ([#3721](https://github.com/valkey-io/valkey-glide/pull/3721))
* Python: Fix restore command ([#3853](https://github.com/valkey-io/valkey-glide/pull/3853))
* Update Go Ort ([#3469](https://github.com/valkey-io/valkey-glide/pull/3469))
* Go: Remove redundant implementations of echo ([#3859](https://github.com/valkey-io/valkey-glide/pull/3859))
* Java: Add toArgs() to restore batch command ([#3883](https://github.com/valkey-io/valkey-glide/pull/3883))
* Java: Add error restore command ([#3905](https://github.com/valkey-io/valkey-glide/pull/3905))
* Core: Add reference counting to Script container to support multiple instances ([#3897](https://github.com/valkey-io/valkey-glide/pull/3897))
* Java: Implement support for Insecure TLS authentication mode([#3386](https://github.com/valkey-io/valkey-glide/pull/3386))
* Java: Otel implementation ([#3962](https://github.com/valkey-io/valkey-glide/pull/3962))
* Go: SRandMemberCount command added ([#4037](https://github.com/valkey-io/valkey-glide/pull/4037))
* Go: SPopCount command added ([#4026](https://github.com/valkey-io/valkey-glide/pull/4026))
* Go: Batch/Transaction/Pipeline ([#3938](https://github.com/valkey-io/valkey-glide/pull/3938) and [#4005](https://github.com/valkey-io/valkey-glide/pull/4005))
* Go: `WATCH` and `UNWATCH` ([#4054](https://github.com/valkey-io/valkey-glide/pull/4054))
* Java: OTEL fix for script ([#4065](https://github.com/valkey-io/valkey-glide/pull/4065))
* Go: Add Opentelemetry support of creating spans ([#3932](https://github.com/valkey-io/valkey-glide/pull/3932))
* Go: Fix API to use errors idiomatically ([#4090](https://github.com/valkey-io/valkey-glide/pull/4090))
* Go: XInfoStream example fix ([#4185](https://github.com/valkey-io/valkey-glide/pull/4185))
* Node: Validate clientAZ when using AZ affinity strategies ([#4267](https://github.com/valkey-io/valkey-glide/pull/4267))
* Core: Add support for OTLP environment variables ([#4334](https://github.com/valkey-io/valkey-glide/issues/4334))

### Breaking Changes

* Go: Drop support for Go 1.20 ([#3513](https://github.com/valkey-io/valkey-glide/pull/3513))
* Java: Deprecate `Transaction` and `ClusterTransaction` ([#3561](https://github.com/valkey-io/valkey-glide/pull/3561))
* Go: Fix response handler for `ZRangeWithScores` to return an ordered result ([#3694](https://github.com/valkey-io/valkey-glide/pull/3694))
* Go: Fix response handler for other sorted set with scores commands to return an ordered result ([#3712](https://github.com/valkey-io/valkey-glide/pull/3712))
* Java: Change `BZMPOP` and `ZMPOP` to return `Map<String, Object>` and `Map<GlideString, Object>` instead of `Object[]` ([#3733](https://github.com/valkey-io/valkey-glide/pull/3733))
* Go: Add support for `context` ([#3802](https://github.com/valkey-io/valkey-glide/pull/3802))
* Go: Change parameters of `EchoWithOptions` to be more user-friendly ([#3806](https://github.com/valkey-io/valkey-glide/pull/3806))
* Java: Shadow netty dependencies. ([#3004](https://github.com/valkey-io/valkey-glide/pull/3004))
* Go: Modify `requestTimeout` and `connectionTimeout` to be of type `time.Duration` ([#3857](https://github.com/valkey-io/valkey-glide/pull/3857))
* Go: Update options.Expiry to use time.Duration ([#4089](https://github.com/valkey-io/valkey-glide/pull/4089))
* Go: XRead and XReadGroup response update ([#4085](https://github.com/valkey-io/valkey-glide/pull/4085))
* Go: XClaim and XClaimWithOptions update response type ([#4091](https://github.com/valkey-io/valkey-glide/pull/4091))
* Modify `PFADD` to return a boolean rather than an integer ([#4094](https://github.com/valkey-io/valkey-glide/pull/4094))
* Go: Modify blocking commands to use type `time.Duration` for timeouts ([#4086](https://github.com/valkey-io/valkey-glide/pull/4086))
* Go: Modify most commands to use type `time.Duration` (check PR for list of commands modified) ([#4105](https://github.com/valkey-io/valkey-glide/pull/4105))
* Go: XInfoStream update response ([#4136](https://github.com/valkey-io/valkey-glide/pull/4136))
* Go: Update return type for `XAdd` without optional arguments ([#4141](https://github.com/valkey-io/valkey-glide/pull/4141))
* Go: Change options arguments to not be pointers ([#4106](https://github.com/valkey-io/valkey-glide/pull/4106))
* Go: Change `ZAddIncr` Return Type to be `float64` ([#4190](https://github.com/valkey-io/valkey-glide/pull/4190))
* Go: Update return type for `LMPop` and related commands ([#4166](https://github.com/valkey-io/valkey-glide/pull/4166))
* Go: `ZRankWithScore` and `ZRevRankWithScore` update response type ([#4196](https://github.com/valkey-io/valkey-glide/pull/4196))
* Go: Update return type for LCS commands ([#4187](https://github.com/valkey-io/valkey-glide/pull/4187))

### Fixes

* Add support for Intel MAC (x86_64/amd64) ([#3482](https://github.com/valkey-io/valkey-glide/pull/3482))
* Go, Java: Fix response handling for `customCommand` API for cluster client ([#3593](https://github.com/valkey-io/valkey-glide/pull/3593))
* Java: Bump `netty` version ([#3804](https://github.com/valkey-io/valkey-glide/pull/3804))
* Go: Fix `GeoHash`'s return type to allow for both strings and null ([#4098](https://github.com/valkey-io/valkey-glide/pull/4098))
* Go: `ClientGetName` returns a nullable string ([#4088](https://github.com/valkey-io/valkey-glide/pull/4088))

### Operational Enhancements

## 1.3.0 (2025-02-14)

### Changes

* Java: Add support to AzAffinityReplicasAndPrimary read strategy ([#3083](https://github.com/valkey-io/valkey-glide/pull/3083))
* Python: Add support to AzAffinityReplicasAndPrimary read strategy ([#3071](https://github.com/valkey-io/valkey-glide/pull/3071))
* Node: Add support to AzAffinityReplicasAndPrimary read strategy ([#3063](https://github.com/valkey-io/valkey-glide/pull/3063))
* Core: Add support to AzAffinityReplicasAndPrimary read strategy ([#2986](https://github.com/valkey-io/valkey-glide/pull/2986))
* Java, Node, Python: Add transaction commands for JSON module ([#2862](https://github.com/valkey-io/valkey-glide/pull/2862))
* Java: bump `netty` version ([#2795](https://github.com/valkey-io/valkey-glide/pull/2795))
* Java: Bump protobuf (protoc) version ([#2796](https://github.com/valkey-io/valkey-glide/pull/2796), [#2800](https://github.com/valkey-io/valkey-glide/pull/2800))
* Java: Add binary version of `ZRANK WITHSCORE` ([#2896](https://github.com/valkey-io/valkey-glide/pull/2896))
* Java, Node, Python: Update documentation for CONFIG SET and CONFIG GET ([#2919](https://github.com/valkey-io/valkey-glide/pull/2919))
* Java: Shadow `protobuf` dependency ([#2931](https://github.com/valkey-io/valkey-glide/pull/2931))
* Java: Add `RESP2` support ([#2383](https://github.com/valkey-io/valkey-glide/pull/2383))
* Node, Python: Add `IFEQ` option ([#2909](https://github.com/valkey-io/valkey-glide/pull/2909), [#2962](https://github.com/valkey-io/valkey-glide/pull/2962))
* Java: Add `IFEQ` option ([#2978](https://github.com/valkey-io/valkey-glide/pull/2978))
* Core: Add AzAffinityReplicasAndPrimary Read Strategy([#2986](https://github.com/valkey-io/valkey-glide/pull/2986))

### Breaking Changes

### Fixes

* Node: Fix `zrangeWithScores` (disallow `RangeByLex` as it is not supported) ([#2926](https://github.com/valkey-io/valkey-glide/pull/2926))
* Core: improve fix in #2381 ([#2929](https://github.com/valkey-io/valkey-glide/pull/2929))
* Java: Fix `lpopCount` null handling ([#3025](https://github.com/valkey-io/valkey-glide/pull/3025))
* Core: Fix `opentelemetry` related dependency issue ([#3123](https://github.com/valkey-io/valkey-glide/pull/3123))
* Node: Fix NPM CD tag ([#3155](https://github.com/valkey-io/valkey-glide/pull/3155))
* Java: replacing map usage in response handling with LinkedHashMap ([#3324](https://github.com/valkey-io/valkey-glide/pull/3324))

### Operational Enhancements

### Features

* Go: Preview release of Go client. See documentation for details on specific supported commands in this release.

## 1.2.1 (2024-12-29)

### Changes

* Node, Python, Java: Add allow uncovered slots scanning flag option in cluster scan ([#2814](https://github.com/valkey-io/valkey-glide/pull/2814), [#2815](https://github.com/valkey-io/valkey-glide/pull/2815), [#2860](https://github.com/valkey-io/valkey-glide/pull/2860))
* Java: Bump protobuf (protoc) version ([#2561](https://github.com/valkey-io/valkey-glide/pull/2561), [#2802](https://github.com/valkey-io/valkey-glide/pull/2802))
* Java: bump `netty` version ([#2777](https://github.com/valkey-io/valkey-glide/pull/2777))
* Node: Remove native package references for MacOs x64 architecture ([#2799](https://github.com/valkey-io/valkey-glide/issues/2799))
* Node, Python, Java: Add connection timeout to client configuration ([#2823](https://github.com/valkey-io/valkey-glide/issues/2823))

### Breaking Changes

### Fixes

* Core: Fix RESP2 multi-node response from cluster ([#2381](https://github.com/valkey-io/valkey-glide/pull/2381))
* Core: Ensure cluster client creation fail when engine is < 7.0 and sharded subscriptions are configured ([#2819](https://github.com/valkey-io/valkey-glide/pull/2819))

### Operational Enhancements

## 1.2.0 (2024-11-27)

### Changes

* Java: Added Client API for retrieving internal statistics ([#2672](https://github.com/valkey-io/valkey-glide/pull/2672))
* Node: Client API for retrieving internal statistics ([#2727](https://github.com/valkey-io/valkey-glide/pull/2727))
* Python: Client API for retrieving internal statistics ([#2707](https://github.com/valkey-io/valkey-glide/pull/2707))
* Node, Python, Java: Adding support for replacing connection configured password ([#2651](https://github.com/valkey-io/valkey-glide/pull/2651), [#2659](https://github.com/valkey-io/valkey-glide/pull/2659), [#2677](https://github.com/valkey-io/valkey-glide/pull/2677))
* Node, Python, Java: AZ Affinity - Python Wrapper Support ([#2686](https://github.com/valkey-io/valkey-glide/pull/2686), [#2676](https://github.com/valkey-io/valkey-glide/pull/2676), [#2678](https://github.com/valkey-io/valkey-glide/pull/2678))
* Node: Add `FT._ALIASLIST` command ([#2652](https://github.com/valkey-io/valkey-glide/pull/2652))
* Python: Add `FT._ALIASLIST` command ([#2638](https://github.com/valkey-io/valkey-glide/pull/2638))
* Node: Add `FT.ALIASADD`, `FT.ALIADDEL`, `FT.ALIASUPDATE` ([#2596](https://github.com/valkey-io/valkey-glide/pull/2596))
* Python code cleanup ([#2573](https://github.com/valkey-io/valkey-glide/pull/2573))
* Python: Add `FT.PROFILE` command ([#2543](https://github.com/valkey-io/valkey-glide/pull/2543))
* Python: Add `FT.AGGREGATE` command ([#2530](https://github.com/valkey-io/valkey-glide/pull/2530))
* Python: Add `JSON.OBJLEN` command ([#2495](https://github.com/valkey-io/valkey-glide/pull/2495))
* Python: Add `FT.EXPLAIN` and `FT.EXPLAINCLI` commands ([#2508](https://github.com/valkey-io/valkey-glide/pull/2508))
* Python: Add `FT.INFO` command ([#2429](https://github.com/valkey-io/valkey-glide/pull/2494))
* Python: Add `FT.SEARCH` command ([#2470](https://github.com/valkey-io/valkey-glide/pull/2470))
* Python: Add commands `FT.ALIASADD`, `FT.ALIASDEL`, `FT.ALIASUPDATE` ([#2471](https://github.com/valkey-io/valkey-glide/pull/2471))
* Python: Add `FT.DROPINDEX` command ([#2437](https://github.com/valkey-io/valkey-glide/pull/2437))
* Python: Add `FT.CREATE` command ([#2413](https://github.com/valkey-io/valkey-glide/pull/2413))
* Python: Add `JSON.MGET` command ([#2507](https://github.com/valkey-io/valkey-glide/pull/2507))
* Python: Add `JSON.ARRLEN` command ([#2403](https://github.com/valkey-io/valkey-glide/pull/2403))
* Python: Add `JSON.CLEAR` command ([#2418](https://github.com/valkey-io/valkey-glide/pull/2418))
* Python: Add `JSON.TYPE` command ([#2409](https://github.com/valkey-io/valkey-glide/pull/2409))
* Python: Add `JSON.NUMINCRBY` command ([#2448](https://github.com/valkey-io/valkey-glide/pull/2448))
* Python: Add `JSON.NUMMULTBY` command ([#2458](https://github.com/valkey-io/valkey-glide/pull/2458))
* Python: Add `JSON.ARRINDEX` command ([#2528](https://github.com/valkey-io/valkey-glide/pull/2528))
* Python: Add `FT._LIST` command ([#2571](https://github.com/valkey-io/valkey-glide/pull/2571))
* Python: Add `JSON.DEBUG_MEMORY` and `JSON.DEBUG_FIELDS` commands ([#2481](https://github.com/valkey-io/valkey-glide/pull/2481))
* Java: Added `FT.CREATE` ([#2414](https://github.com/valkey-io/valkey-glide/pull/2414))
* Java: Added `FT.INFO` ([#2405](https://github.com/valkey-io/valkey-glide/pull/2441))
* Java: Added `FT.DROPINDEX` ([#2440](https://github.com/valkey-io/valkey-glide/pull/2440))
* Java: Added `FT.SEARCH` ([#2439](https://github.com/valkey-io/valkey-glide/pull/2439))
* Java: Added `FT.AGGREGATE` ([#2466](https://github.com/valkey-io/valkey-glide/pull/2466))
* Java: Added `FT.PROFILE` ([#2473](https://github.com/valkey-io/valkey-glide/pull/2473))
* Java: Added `JSON.SET` and `JSON.GET` ([#2462](https://github.com/valkey-io/valkey-glide/pull/2462))
* Java: Added `JSON.MGET` ([#2514](https://github.com/valkey-io/valkey-glide/pull/2514))
* Node: Added `FT.CREATE` ([#2501](https://github.com/valkey-io/valkey-glide/pull/2501))
* Node: Added `FT.INFO` ([#2540](https://github.com/valkey-io/valkey-glide/pull/2540))
* Node: Added `FT.AGGREGATE` ([#2554](https://github.com/valkey-io/valkey-glide/pull/2554))
* Node: Added `FT.PROFILE` ([#2633](https://github.com/valkey-io/valkey-glide/pull/2633))
* Java: Added `JSON.DEBUG` ([#2520](https://github.com/valkey-io/valkey-glide/pull/2520))
* Java: Added `JSON.ARRINSERT` and `JSON.ARRLEN` ([#2476](https://github.com/valkey-io/valkey-glide/pull/2476))
* Java: Added `JSON.ARRINDEX` ([#2546](https://github.com/valkey-io/valkey-glide/pull/2546))
* Java: Added `JSON.ARRPOP` ([#2486](https://github.com/valkey-io/valkey-glide/pull/2486))
* Java: Added `JSON.OBJLEN` and `JSON.OBJKEYS` ([#2492](https://github.com/valkey-io/valkey-glide/pull/2492))
* Java: Added `JSON.DEL` and `JSON.FORGET` ([#2490](https://github.com/valkey-io/valkey-glide/pull/2490))
* Java: Added `FT.ALIASADD`, `FT.ALIASDEL`, `FT.ALIASUPDATE` ([#2442](https://github.com/valkey-io/valkey-glide/pull/2442))
* Java: Added `FT._ALIASLIST` ([#2569](https://github.com/valkey-io/valkey-glide/pull/2569))
* Java: Added `FT.EXPLAIN`, `FT.EXPLAINCLI` ([#2515](https://github.com/valkey-io/valkey-glide/pull/2515))
* Core: Update routing for commands from server modules ([#2461](https://github.com/valkey-io/valkey-glide/pull/2461))
* Node: Added `JSON.SET` and `JSON.GET` ([#2427](https://github.com/valkey-io/valkey-glide/pull/2427))
* Node: Added `JSON.MGET` ([#2567](https://github.com/valkey-io/valkey-glide/pull/2567))
* Java: Added `JSON.NUMINCRBY` and `JSON.NUMMULTBY` ([#2511](https://github.com/valkey-io/valkey-glide/pull/2511))
* Java: Added `JSON.ARRAPPEND` ([#2489](https://github.com/valkey-io/valkey-glide/pull/2489))
* Java: Added `JSON.ARRTRIM` ([#2518](https://github.com/valkey-io/valkey-glide/pull/2518))
* Node: Added `JSON.TOGGLE` ([#2491](https://github.com/valkey-io/valkey-glide/pull/2491))
* Node: Added `JSON.ARRINSERT`, `JSON.ARRPOP` and `JSON.ARRLEN` ([#2542](https://github.com/valkey-io/valkey-glide/pull/2542))
* Node: Added `JSON.DEL` and `JSON.FORGET` ([#2505](https://github.com/valkey-io/valkey-glide/pull/2505))
* Java: Added `JSON.TOGGLE` ([#2504](https://github.com/valkey-io/valkey-glide/pull/2504))
* Java: Added `JSON.STRAPPEND` and `JSON.STRLEN` ([#2522](https://github.com/valkey-io/valkey-glide/pull/2522))
* Java: Added `JSON.CLEAR` ([#2519](https://github.com/valkey-io/valkey-glide/pull/2519))
* Node: Added `JSON.TYPE` ([#2510](https://github.com/valkey-io/valkey-glide/pull/2510))
* Node: Added `JSON.ARRAPPEND` ([#2562](https://github.com/valkey-io/valkey-glide/pull/2562))
* Java: Added `JSON.RESP` ([#2513](https://github.com/valkey-io/valkey-glide/pull/2513))
* Java: Added `JSON.TYPE` ([#2525](https://github.com/valkey-io/valkey-glide/pull/2525))
* Java: Added `FT._LIST` ([#2568](https://github.com/valkey-io/valkey-glide/pull/2568))
* Node: Added `FT.DROPINDEX` ([#2516](https://github.com/valkey-io/valkey-glide/pull/2516))
* Node: Added `FT._LIST` ([#2570](https://github.com/valkey-io/valkey-glide/pull/2570))
* Node: Added `JSON.RESP` ([#2517](https://github.com/valkey-io/valkey-glide/pull/2517))
* Node: Added `FT.EXPLAIN` and `FT.EXPLAINCLI` ([#2560](https://github.com/valkey-io/valkey-glide/pull/2560))
* Node: Added `JSON.CLEAR` ([#2566](https://github.com/valkey-io/valkey-glide/pull/2566))
* Node: Added `JSON.ARRTRIM` ([#2550](https://github.com/valkey-io/valkey-glide/pull/2550))
* Python: Add `JSON.STRAPPEND`, `JSON.STRLEN` commands ([#2372](https://github.com/valkey-io/valkey-glide/pull/2372))
* Node: Added `JSON.ARRINDEX` ([#2559](https://github.com/valkey-io/valkey-glide/pull/2559))
* Node: Added `JSON.OBJLEN` and `JSON.OBJKEYS` ([#2563](https://github.com/valkey-io/valkey-glide/pull/2563))
* Python: Add `JSON.STRAPPEND`, `JSON.STRLEN` commands ([#2372](https://github.com/valkey-io/valkey-glide/pull/2372))
* Python: Add `JSON.OBJKEYS` command ([#2395](https://github.com/valkey-io/valkey-glide/pull/2395))
* Python: Add `JSON.ARRINSERT` command ([#2464](https://github.com/valkey-io/valkey-glide/pull/2464))
* Python: Add `JSON.ARRTRIM` command ([#2457](https://github.com/valkey-io/valkey-glide/pull/2457))
* Python: Add `JSON.ARRAPPEND` command ([#2382](https://github.com/valkey-io/valkey-glide/pull/2382))
* Python: Add `JSON.RESP` command ([#2451](https://github.com/valkey-io/valkey-glide/pull/2451))
* Python: Add `JSON.ARRPOP` command ([#2407](https://github.com/valkey-io/valkey-glide/pull/2407))
* Node: Add `JSON.STRLEN` and `JSON.STRAPPEND` command ([#2537](https://github.com/valkey-io/valkey-glide/pull/2537))
* Node: Add `FT.SEARCH` ([#2551](https://github.com/valkey-io/valkey-glide/pull/2551))
* Python: Fix example ([#2556](https://github.com/valkey-io/valkey-glide/issues/2556))
* Core: Add support for sending multi-slot JSON.MSET and JSON.MGET commands ([#2587](https://github.com/valkey-io/valkey-glide/pull/2587))
* Node: Add `JSON.DEBUG` command ([#2572](https://github.com/valkey-io/valkey-glide/pull/2572))
* Node: Add `JSON.NUMINCRBY` and `JSON.NUMMULTBY` command ([#2555](https://github.com/valkey-io/valkey-glide/pull/2555))
* Core: Add support to Availability Zone Affinity read strategy ([#2539](https://github.com/valkey-io/valkey-glide/pull/2539))
* Core: Fix list of readonly commands ([#2634](https://github.com/valkey-io/valkey-glide/pull/2634), [#2649](https://github.com/valkey-io/valkey-glide/pull/2649))
* Core: Improve retry logic and update unmaintained dependencies for Rust lint CI ([#2673](https://github.com/valkey-io/valkey-glide/pull/2643))
* Core: Release the read lock while creating connections in `refresh_connections` ([#2630](https://github.com/valkey-io/valkey-glide/issues/2630))
* Core: SlotMap refactor - Added NodesMap, Update the slot map upon MOVED errors ([#2682](https://github.com/valkey-io/valkey-glide/issues/2682))

### Breaking Changes

### Fixes

* Core: UDS Socket Handling Rework ([#2482](https://github.com/valkey-io/valkey-glide/pull/2482))

### Operational Enhancements

* Java: Add modules CI ([#2388](https://github.com/valkey-io/valkey-glide/pull/2388), [#2404](https://github.com/valkey-io/valkey-glide/pull/2404), [#2416](https://github.com/valkey-io/valkey-glide/pull/2416))
* Node: Add modules CI ([#2472](https://github.com/valkey-io/valkey-glide/pull/2472))
* Python: Fix modules CI ([#2487](https://github.com/valkey-io/valkey-glide/pull/2487))

## 1.1.0 (2024-09-24)

### Changes

* Node: Fix binary variant for xinfogroups and lrem ([#2324](https://github.com/valkey-io/valkey-glide/pull/2324))
* Node: Fixed missing exports ([#2301](https://github.com/valkey-io/valkey-glide/pull/2301))
* Node: Use `options` struct for all optional arguments ([#2287](https://github.com/valkey-io/valkey-glide/pull/2287))
* Node: Added `invokeScript` API with routing for cluster client ([#2284](https://github.com/valkey-io/valkey-glide/pull/2284))
* Java: Expanded tests for converting non UTF-8 bytes to Strings ([#2286](https://github.com/valkey-io/valkey-glide/pull/2286))
* Python: Replace instances of Redis with Valkey ([#2266](https://github.com/valkey-io/valkey-glide/pull/2266))
* Java: Replace instances of Redis with Valkey ([#2268](https://github.com/valkey-io/valkey-glide/pull/2268))
* Node: Replace instances of Redis with Valkey ([#2260](https://github.com/valkey-io/valkey-glide/pull/2260))
* Node: Added binary variant for commands which have `Record` as input or output ([#2207](https://github.com/valkey-io/valkey-glide/pull/2207))
* Node: Renamed `ReturnType` to `GlideReturnType` ([#2241](https://github.com/valkey-io/valkey-glide/pull/2241))
* Node: Fix BITPOS for Valkey8 ([#2227](https://github.com/valkey-io/valkey-glide/pull/2227))
* Node, Python: Rename `stop` to `end` in sorted set queries ([#2214](https://github.com/valkey-io/valkey-glide/pull/2214))
* Node: Added binary variant to sorted set commands - part 1 ([#2190](https://github.com/valkey-io/valkey-glide/pull/2190))
* Python: Fix BITPOS for Valkey8 ([#2256](https://github.com/valkey-io/valkey-glide/pull/2256))
* Node: Added binary variant to HSCAN command ([#2240](https://github.com/valkey-io/valkey-glide/pull/2240))
* Node: replace decoder by DecoderOption and route by RouteOption in API([#2234](https://github.com/valkey-io/valkey-glide/pull/2234/))
* Node: Added binary variant to sorted set commands ([#2190](https://github.com/valkey-io/valkey-glide/pull/2190), [#2210](https://github.com/valkey-io/valkey-glide/pull/2210))
* Node: Added binary variant for MSET, MSETNX commands ([#2229](https://github.com/valkey-io/valkey-glide/pull/2229))
* Node: Added binary variant to HASH commands ([#2194](https://github.com/valkey-io/valkey-glide/pull/2194))
* Node: Added binary variant to server management commands ([#2179](https://github.com/valkey-io/valkey-glide/pull/2179))
* Node: Added/updated binary variant to connection management commands and WATCH/UNWATCH ([#2160](https://github.com/valkey-io/valkey-glide/pull/2160))
* Java: Fix docs for stream commands ([#2086](https://github.com/valkey-io/valkey-glide/pull/2086))
* Node: Added binary variant to bitmap commands ([#2178](https://github.com/valkey-io/valkey-glide/pull/2178))
* Node: Added binary variant to generic commands ([#2158](https://github.com/valkey-io/valkey-glide/pull/2158))
* Node: Added binary variant to geo commands ([#2149](https://github.com/valkey-io/valkey-glide/pull/2149))
* Node: Added binary variant to HYPERLOGLOG commands ([#2176](https://github.com/valkey-io/valkey-glide/pull/2176))
* Node: Added FUNCTION DUMP and FUNCTION RESTORE commands ([#2129](https://github.com/valkey-io/valkey-glide/pull/2129), [#2173](https://github.com/valkey-io/valkey-glide/pull/2173))
* Node: Added binary variant to FUNCTION commands ([#2172](https://github.com/valkey-io/valkey-glide/pull/2172))
* Node: Added ZUNIONSTORE command ([#2145](https://github.com/valkey-io/valkey-glide/pull/2145))
* Node: Added XREADGROUP command ([#2124](https://github.com/valkey-io/valkey-glide/pull/2124))
* Node: Added XINFO GROUPS command ([#2122](https://github.com/valkey-io/valkey-glide/pull/2122))
* Java: Added PUBSUB CHANNELS, NUMPAT and NUMSUB commands ([#2105](https://github.com/valkey-io/valkey-glide/pull/2105))
* Java: Added PUBSUB SHARDCHANNELS command ([#2265](https://github.com/valkey-io/valkey-glide/pull/2265))
* Java: Added PUBSUB SHARDNUMSUB command ([#2279](https://github.com/valkey-io/valkey-glide/pull/2279))
* Java: Added binary support for custom command ([#2109](https://github.com/valkey-io/valkey-glide/pull/2109))
* Node: Added SSCAN command ([#2132](https://github.com/valkey-io/valkey-glide/pull/2132))
* Node: Added HKEYS command ([#2136](https://github.com/valkey-io/valkey-glide/pull/2136))
* Node: Added FUNCTION KILL command ([#2114](https://github.com/valkey-io/valkey-glide/pull/2114))
* Node: Update all commands to use `async` ([#2110](https://github.com/valkey-io/valkey-glide/pull/2110))
* Node: Added XAUTOCLAIM command ([#2108](https://github.com/valkey-io/valkey-glide/pull/2108))
* Node: Added XPENDING commands ([#2085](https://github.com/valkey-io/valkey-glide/pull/2085))
* Node: Added HSCAN command ([#2098](https://github.com/valkey-io/valkey-glide/pull/2098/))
* Node: Added XINFO CONSUMERS command ([#2093](https://github.com/valkey-io/valkey-glide/pull/2093))
* Node: Added HRANDFIELD command ([#2096](https://github.com/valkey-io/valkey-glide/pull/2096))
* Node: Added FUNCTION STATS commands ([#2082](https://github.com/valkey-io/valkey-glide/pull/2082))
* Node: Added XCLAIM command ([#2092](https://github.com/valkey-io/valkey-glide/pull/2092))
* Node: Added EXPIRETIME and PEXPIRETIME commands ([#2063](https://github.com/valkey-io/valkey-glide/pull/2063))
* Node: Added SORT commands ([#2028](https://github.com/valkey-io/valkey-glide/pull/2028))
* Node: Added LASTSAVE command ([#2059](https://github.com/valkey-io/valkey-glide/pull/2059))
* Node: Added GEOSEARCHSTORE command ([#2080](https://github.com/valkey-io/valkey-glide/pull/2080))
* Node: Added LCS command ([#2049](https://github.com/valkey-io/valkey-glide/pull/2049))
* Node: Added MSETNX command ([#2046](https://github.com/valkey-io/valkey-glide/pull/2046))
* Node: Added BLMOVE command ([#2027](https://github.com/valkey-io/valkey-glide/pull/2027))
* Node: Exported client configuration types ([#2023](https://github.com/valkey-io/valkey-glide/pull/2023))
* Java, Python: Update docs for GEOSEARCH command ([#2017](https://github.com/valkey-io/valkey-glide/pull/2017))
* Python: Update docs for BITFIELD and BITFIELD_RO commands ([#2048](https://github.com/valkey-io/valkey-glide/pull/2048))
* Node: Added FUNCTION LIST command ([#2019](https://github.com/valkey-io/valkey-glide/pull/2019))
* Node: Added GEOSEARCH command ([#2007](https://github.com/valkey-io/valkey-glide/pull/2007))
* Node: Added LMOVE command ([#2002](https://github.com/valkey-io/valkey-glide/pull/2002))
* Node: Added GEOPOS command ([#1991](https://github.com/valkey-io/valkey-glide/pull/1991))
* Node: Added BITCOUNT command ([#1982](https://github.com/valkey-io/valkey-glide/pull/1982))
* Node: Added BITPOS command ([#1998](https://github.com/valkey-io/valkey-glide/pull/1998))
* Node: Added BITFIELD and BITFIELD_RO commands ([#2026](https://github.com/valkey-io/valkey-glide/pull/2026))
* Node: Added TOUCH command ([#2055](https://github.com/valkey-io/valkey-glide/pull/2055))
* Node: Added FLUSHDB command ([#1986](https://github.com/valkey-io/valkey-glide/pull/1986))
* Node: Added GETDEL command ([#1968](https://github.com/valkey-io/valkey-glide/pull/1968))
* Node: Added GETRANGE command ([#2079](https://github.com/valkey-io/valkey-glide/pull/2079))
* Node: Added BITOP command ([#2012](https://github.com/valkey-io/valkey-glide/pull/2012))
* Node: Added GETBIT command ([#1989](https://github.com/valkey-io/valkey-glide/pull/1989))
* Node: Added SETBIT command ([#1978](https://github.com/valkey-io/valkey-glide/pull/1978))
* Node: Added RANDOMKEY command ([#2057](https://github.com/valkey-io/valkey-glide/pull/2057))
* Node: Added LPUSHX and RPUSHX command([#1959](https://github.com/valkey-io/valkey-glide/pull/1959))
* Node: Added LSET command ([#1952](https://github.com/valkey-io/valkey-glide/pull/1952))
* Node: Added SDIFFSTORE command ([#1931](https://github.com/valkey-io/valkey-glide/pull/1931))
* Node: Added ZDIFF command ([#1972](https://github.com/valkey-io/valkey-glide/pull/1972))
* Node: Added ZDIFFSTORE command ([#1985](https://github.com/valkey-io/valkey-glide/pull/1985))
* Node: Added SINTERCARD command ([#1956](https://github.com/valkey-io/valkey-glide/pull/1956))
* Node: Added SINTERSTORE command ([#1929](https://github.com/valkey-io/valkey-glide/pull/1929))
* Node: Added SUNION command ([#1919](https://github.com/valkey-io/valkey-glide/pull/1919))
* Node: Added SMISMEMBER command ([#1955](https://github.com/valkey-io/valkey-glide/pull/1955))
* Node: Added SDIFF command ([#1924](https://github.com/valkey-io/valkey-glide/pull/1924))
* Node: Added ZMSCORE command ([#1987](https://github.com/valkey-io/valkey-glide/pull/1987))
* Node: Added LOLWUT command ([#1934](https://github.com/valkey-io/valkey-glide/pull/1934))
* Node: Added LPOS command ([#1927](https://github.com/valkey-io/valkey-glide/pull/1927))
* Node: Added FUNCTION LOAD command ([#1969](https://github.com/valkey-io/valkey-glide/pull/1969))
* Node: Added FUNCTION DELETE command ([#1990](https://github.com/valkey-io/valkey-glide/pull/1990))
* Node: Added FUNCTION FLUSH command ([#1984](https://github.com/valkey-io/valkey-glide/pull/1984))
* Node: Added FCALL and FCALL_RO commands ([#2011](https://github.com/valkey-io/valkey-glide/pull/2011))
* Node: Added COPY command ([#2024](https://github.com/valkey-io/valkey-glide/pull/2024))
* Node: Added MOVE command ([#2104](https://github.com/valkey-io/valkey-glide/pull/2104))
* Node: Added ZMPOP command ([#1994](https://github.com/valkey-io/valkey-glide/pull/1994))
* Node: Added ZINCRBY command ([#2009](https://github.com/valkey-io/valkey-glide/pull/2009))
* Node: Added BZMPOP command ([#2018](https://github.com/valkey-io/valkey-glide/pull/2018))
* Node: Added XRANGE command ([#2069](https://github.com/valkey-io/valkey-glide/pull/2069))
* Node: Added XREVRANGE command ([#2148](https://github.com/valkey-io/valkey-glide/pull/2148))
* Node: Added PFMERGE command ([#2053](https://github.com/valkey-io/valkey-glide/pull/2053))
* Node: Added WATCH and UNWATCH commands ([#2076](https://github.com/valkey-io/valkey-glide/pull/2076))
* Node: Added WAIT command ([#2113](https://github.com/valkey-io/valkey-glide/pull/2113))
* Node: Added DUMP and RESTORE commands ([#2126](https://github.com/valkey-io/valkey-glide/pull/2126))
* Node: Added transaction supports for DUMP and RESTORE ([#2159](https://github.com/valkey-io/valkey-glide/pull/2159))
* Node: Added ZLEXCOUNT command ([#2022](https://github.com/valkey-io/valkey-glide/pull/2022))
* Node: Added ZREMRANGEBYLEX command ([#2025](https://github.com/valkey-io/valkey-glide/pull/2025))
* Node: Added ZRANGESTORE command ([#2068](https://github.com/valkey-io/valkey-glide/pull/2068))
* Node: Added SRANDMEMBER command ([#2067](https://github.com/valkey-io/valkey-glide/pull/2067))
* Node: Added XINFO STREAM command ([#2083](https://github.com/valkey-io/valkey-glide/pull/2083))
* Node: Added ZSCAN command ([#2061](https://github.com/valkey-io/valkey-glide/pull/2061))
* Node: Added SETRANGE command ([#2066](https://github.com/valkey-io/valkey-glide/pull/2066))
* Node: Added APPEND command ([#2095](https://github.com/valkey-io/valkey-glide/pull/2095))
* Node: Added XDEL command ([#2064](https://github.com/valkey-io/valkey-glide/pull/2064))
* Node: Added LMPOP & BLMPOP command ([#2050](https://github.com/valkey-io/valkey-glide/pull/2050))
* Node: Added PUBSUB support ([#1964](https://github.com/valkey-io/valkey-glide/pull/1964))
* Node: Added PUBSUB * commands ([#2090](https://github.com/valkey-io/valkey-glide/pull/2090))
* Python: Added PUBSUB * commands ([#2043](https://github.com/valkey-io/valkey-glide/pull/2043))
* Node: Added XGROUP CREATE & XGROUP DESTROY commands ([#2084](https://github.com/valkey-io/valkey-glide/pull/2084))
* Node: Added BZPOPMAX & BZPOPMIN command ([#2077](https://github.com/valkey-io/valkey-glide/pull/2077))
* Node: Added XGROUP CREATECONSUMER & XGROUP DELCONSUMER commands ([#2088](https://github.com/valkey-io/valkey-glide/pull/2088))
* Node: Added GETEX command ([#2107](https://github.com/valkey-io/valkey-glide/pull/2107))
* Node: Added ZINTER and ZUNION commands ([#2146](https://github.com/valkey-io/valkey-glide/pull/2146))
* Node: Added XACK commands ([#2112](https://github.com/valkey-io/valkey-glide/pull/2112))
* Node: Added XGROUP SETID command ([#2135](https://github.com/valkey-io/valkey-glide/pull/2135))
* Node: Added binary variant to string commands ([#2183](https://github.com/valkey-io/valkey-glide/pull/2183))
* Node: Added binary variant to stream commands ([#2200](https://github.com/valkey-io/valkey-glide/pull/2200), [#2222](https://github.com/valkey-io/valkey-glide/pull/2222))
* Python: Add Script commands ([#2208](https://github.com/valkey-io/valkey-glide/pull/2208))
* Node: Added Script commands ([#2267](https://github.com/valkey-io/valkey-glide/pull/2267))
* Java, Node, Python: Add BY/GET support for SORT/RO in cluster mode (Valkey-8) ([#2252](https://github.com/valkey-io/valkey-glide/pull/2252))
* Java, Node, Python: Add SCRIPT SHOW command (Valkey-8) ([#2171](https://github.com/valkey-io/valkey-glide/pull/2171))
* Java, Node, Python: Change BITCOUNT end param to optional (Valkey-8) ([#2248](https://github.com/valkey-io/valkey-glide/pull/2248))
* Java, Node, Python: Add NOSCORES option to ZSCAN & NOVALUES option to HSCAN (Valkey-8) ([#2174](https://github.com/valkey-io/valkey-glide/pull/2174))
* Node: Add SCAN command ([#2257](https://github.com/valkey-io/valkey-glide/pull/2257))
* Java: Add Script commands ([#2261](https://github.com/valkey-io/valkey-glide/pull/2261))
* Python: Replace google-api-python-client with protobuf ([#2304](https://github.com/valkey-io/valkey-glide/pull/2304))

### Breaking Changes

* Java: Update INFO command ([#2274](https://github.com/valkey-io/valkey-glide/pull/2274))
* Node: (Refactor) Convert types to interfaces ([#2263](https://github.com/valkey-io/valkey-glide/pull/2263))
* Node: (Refactor) Convert classes to types ([#2005](https://github.com/valkey-io/valkey-glide/pull/2005))
* Core: Change FUNCTION STATS command to return multi node response for standalone mode ([#2117](https://github.com/valkey-io/valkey-glide/pull/2117))

### Fixes

* Java: Fix GlideString conversion from byte to String ([#2271](https://github.com/valkey-io/valkey-glide/pull/2271))
* Java: Add overloads for XADD to allow duplicate entry keys ([#1970](https://github.com/valkey-io/valkey-glide/pull/1970))
* Node: Fix ZADD bug where command could not be called with only the `changed` optional parameter ([#1995](https://github.com/valkey-io/valkey-glide/pull/1995))
* Java: `XRange`/`XRevRange` should return `null` instead of `GlideException` when given a negative count ([#1920](https://github.com/valkey-io/valkey-glide/pull/1920))
* Python: Fix `XClaim` return type to `List[bytes]` instead of `List[TEncodable]` ([#2075](https://github.com/valkey-io/valkey-glide/pull/2075))
* Python: Add missing exports ([#2341](https://github.com/valkey-io/valkey-glide/pull/2341))
* Node: Add missing exports ([#2342](https://github.com/valkey-io/valkey-glide/pull/2342))

### Operational Enhancements

* CI/CD: Create Workflow to deploy artifacts for all platforms ([#2285](https://github.com/valkey-io/valkey-glide/pull/2285))
* Node: Get valkey/redis version using client's info command ([#2276](https://github.com/valkey-io/valkey-glide/pull/2276))
* Java: Fetch server version using client's info command ([#2258](https://github.com/valkey-io/valkey-glide/pull/2258))
* CI/CD: Add workflow for automating Maven release ([#2128](https://github.com/valkey-io/valkey-glide/pull/2128))

## 1.0.0 (2024-07-09)

### Changes

* Node: Added ZINTERSTORE command ([#1513](https://github.com/valkey-io/valkey-glide/pull/1513))
* Python: Added OBJECT ENCODING command ([#1471](https://github.com/valkey-io/valkey-glide/pull/1471))
* Python: Added OBJECT FREQ command ([#1472](https://github.com/valkey-io/valkey-glide/pull/1472))
* Python: Added OBJECT IDLETIME command ([#1474](https://github.com/valkey-io/valkey-glide/pull/1474))
* Python: Added GEOSEARCH command ([#1482](https://github.com/valkey-io/valkey-glide/pull/1482))
* Python: Added GEOSEARCHSTORE command ([#1581](https://github.com/valkey-io/valkey-glide/pull/1581))
* Node: Added RENAMENX command ([#1483](https://github.com/valkey-io/valkey-glide/pull/1483))
* Python: Added OBJECT REFCOUNT command ([#1485](https://github.com/valkey-io/valkey-glide/pull/1485))
* Python: Added RENAMENX command ([#1492](https://github.com/valkey-io/valkey-glide/pull/1492))
* Python: Added PFCOUNT command ([#1493](https://github.com/valkey-io/valkey-glide/pull/1493))
* Python: Added PFMERGE command ([#1497](https://github.com/valkey-io/valkey-glide/pull/1497))
* Node: Added SINTER command ([#1500](https://github.com/valkey-io/valkey-glide/pull/1500))
* Python: Added XLEN command ([#1503](https://github.com/valkey-io/valkey-glide/pull/1503))
* Python: Added LASTSAVE command ([#1509](https://github.com/valkey-io/valkey-glide/pull/1509))
* Python: Added GETDEL command ([#1514](https://github.com/valkey-io/valkey-glide/pull/1514))
* Python: Added GETRANGE command ([#1585](https://github.com/valkey-io/valkey-glide/pull/1585))
* Python: Added ZINTER, ZUNION commands ([#1478](https://github.com/valkey-io/valkey-glide/pull/1478))
* Python: Added SINTERCARD command ([#1511](https://github.com/valkey-io/valkey-glide/pull/1511))
* Python: Added SORT command ([#1439](https://github.com/valkey-io/valkey-glide/pull/1439))
* Node: Added OBJECT ENCODING command ([#1518](https://github.com/valkey-io/valkey-glide/pull/1518), [#1559](https://github.com/valkey-io/valkey-glide/pull/1559))
* Python: Added LMOVE and BLMOVE commands ([#1536](https://github.com/valkey-io/valkey-glide/pull/1536))
* Node: Added SUNIONSTORE command ([#1549](https://github.com/valkey-io/valkey-glide/pull/1549))
* Python: Added SUNION command ([#1583](https://github.com/valkey-io/valkey-glide/pull/1583))
* Node: Added PFCOUNT command ([#1545](https://github.com/valkey-io/valkey-glide/pull/1545))
* Node: Added OBJECT FREQ command ([#1542](https://github.com/valkey-io/valkey-glide/pull/1542), [#1559](https://github.com/valkey-io/valkey-glide/pull/1559))
* Node: Added LINSERT command ([#1544](https://github.com/valkey-io/valkey-glide/pull/1544))
* Node: Added XLEN command ([#1555](https://github.com/valkey-io/valkey-glide/pull/1555))
* Node: Added ZINTERCARD command ([#1553](https://github.com/valkey-io/valkey-glide/pull/1553))
* Python: Added ZINCBY command ([#1586](https://github.com/valkey-io/valkey-glide/pull/1586))
* Python: Added LMPOP and BLMPOP commands ([#1547](https://github.com/valkey-io/valkey-glide/pull/1547))
* Python: Added HSTRLEN command ([#1564](https://github.com/valkey-io/valkey-glide/pull/1564))
* Python: Added MSETNX command ([#1565](https://github.com/valkey-io/valkey-glide/pull/1565))
* Python: Added MOVE command ([#1566](https://github.com/valkey-io/valkey-glide/pull/1566))
* Python: Added EXPIRETIME, PEXPIRETIME commands ([#1587](https://github.com/valkey-io/valkey-glide/pull/1587))
* Python: Added LSET command ([#1584](https://github.com/valkey-io/valkey-glide/pull/1584))
* Node: Added OBJECT IDLETIME command ([#1567](https://github.com/valkey-io/valkey-glide/pull/1567))
* Node: Added OBJECT REFCOUNT command ([#1568](https://github.com/valkey-io/valkey-glide/pull/1568))
* Python: Added SETBIT command ([#1571](https://github.com/valkey-io/valkey-glide/pull/1571))
* Python: Added SRandMember command ([#1578](https://github.com/valkey-io/valkey-glide/pull/1578))
* Python: Added GETBIT command ([#1575](https://github.com/valkey-io/valkey-glide/pull/1575))
* Python: Added BITCOUNT command ([#1592](https://github.com/valkey-io/valkey-glide/pull/1592))
* Python: Added FLUSHALL command ([#1579](https://github.com/valkey-io/valkey-glide/pull/1579))
* Python: Added TOUCH command ([#1582](https://github.com/valkey-io/valkey-glide/pull/1582))
* Python: Added BITOP command ([#1596](https://github.com/valkey-io/valkey-glide/pull/1596))
* Python: Added BITPOS command ([#1604](https://github.com/valkey-io/valkey-glide/pull/1604))
* Python: Added GETEX command ([#1612](https://github.com/valkey-io/valkey-glide/pull/1612))
* Python: Added BITFIELD and BITFIELD_RO commands ([#1615](https://github.com/valkey-io/valkey-glide/pull/1615))
* Python: Added ZREVRANK command ([#1614](https://github.com/valkey-io/valkey-glide/pull/1614))
* Python: Added XDEL command ([#1619](https://github.com/valkey-io/valkey-glide/pull/1619))
* Python: Added XRANGE command ([#1624](https://github.com/valkey-io/valkey-glide/pull/1624))
* Python: Added COPY command ([#1626](https://github.com/valkey-io/valkey-glide/pull/1626))
* Python: Added XREVRANGE command ([#1625](https://github.com/valkey-io/valkey-glide/pull/1625))
* Python: Added XREAD command ([#1644](https://github.com/valkey-io/valkey-glide/pull/1644))
* Python: Added XGROUP CREATE and XGROUP DESTROY commands ([#1646](https://github.com/valkey-io/valkey-glide/pull/1646))
* Python: Added XGROUP CREATECONSUMER and XGROUP DELCONSUMER commands ([#1658](https://github.com/valkey-io/valkey-glide/pull/1658))
* Python: Added LOLWUT command ([#1657](https://github.com/valkey-io/valkey-glide/pull/1657))
* Python: Added XREADGROUP command ([#1679](https://github.com/valkey-io/valkey-glide/pull/1679))
* Python: Added XACK command ([#1681](https://github.com/valkey-io/valkey-glide/pull/1681))
* Python: Added FLUSHDB command ([#1680](https://github.com/valkey-io/valkey-glide/pull/1680))
* Python: Added XGROUP SETID command ([#1683](https://github.com/valkey-io/valkey-glide/pull/1683))
* Python: Added FUNCTION LOAD command ([#1699](https://github.com/valkey-io/valkey-glide/pull/1699))
* Python: Added XPENDING command ([#1704](https://github.com/valkey-io/valkey-glide/pull/1704))
* Python: Added RANDOMKEY command ([#1701](https://github.com/valkey-io/valkey-glide/pull/1701))
* Python: Added FUNCTION FLUSH command ([#1700](https://github.com/valkey-io/valkey-glide/pull/1700))
* Python: Added FUNCTION DELETE command ([#1714](https://github.com/valkey-io/valkey-glide/pull/1714))
* Python: Added FUNCTION LIST command ([#1738](https://github.com/valkey-io/valkey-glide/pull/1738))
* Python: Added SSCAN command ([#1709](https://github.com/valkey-io/valkey-glide/pull/1709))
* Python: Added LCS command ([#1716](https://github.com/valkey-io/valkey-glide/pull/1716))
* Python: Added WAIT command ([#1710](https://github.com/valkey-io/valkey-glide/pull/1710))
* Python: Added XAUTOCLAIM command ([#1718](https://github.com/valkey-io/valkey-glide/pull/1718))
* Python: Add ZSCAN and HSCAN commands ([#1732](https://github.com/valkey-io/valkey-glide/pull/1732))
* Python: Added FCALL_RO command ([#1721](https://github.com/valkey-io/valkey-glide/pull/1721))
* Python: Added WATCH and UNWATCH command ([#1736](https://github.com/valkey-io/valkey-glide/pull/1736))
* Python: Added XCLAIM command ([#1772](https://github.com/valkey-io/valkey-glide/pull/1772))
* Python: Added XINFO GROUPS and XINFO CONSUMERS commands ([#1753](https://github.com/valkey-io/valkey-glide/pull/1753))
* Python: Added LPOS command ([#1740](https://github.com/valkey-io/valkey-glide/pull/1740))
* Python: Added SCAN command ([#1623](https://github.com/valkey-io/valkey-glide/pull/1623))
* Python: Added DUMP and Restore commands ([#1733](https://github.com/valkey-io/valkey-glide/pull/1733))
* Java: Added SCAN command ([#1751](https://github.com/valkey-io/valkey-glide/pull/1751))
* Python: Added FUNCTION KILL command ([#1797](https://github.com/valkey-io/valkey-glide/pull/1797))
* Python: Type migration for entries_read ([#1768](https://github.com/valkey-io/valkey-glide/pull/1768))
* Python: Added FUNCTION DUMP and FUNCTION RESTORE commands ([#1769](https://github.com/valkey-io/valkey-glide/pull/1769))
* Python: Added FUNCTION STATS command ([#1794](https://github.com/valkey-io/valkey-glide/pull/1794))
* Python: Added XINFO STREAM command ([#1816](https://github.com/valkey-io/valkey-glide/pull/1816))
* Python: Added transaction supports for DUMP, RESTORE, FUNCTION DUMP and FUNCTION RESTORE ([#1814](https://github.com/valkey-io/valkey-glide/pull/1814))
* Node: Added FlushAll command ([#1958](https://github.com/valkey-io/valkey-glide/pull/1958))
* Node: Added DBSize command ([#1932](https://github.com/valkey-io/valkey-glide/pull/1932))
* Node: Added GeoAdd command ([#1980](https://github.com/valkey-io/valkey-glide/pull/1980))
* Node: Added ZRevRank command ([#1977](https://github.com/valkey-io/valkey-glide/pull/1977))
* Node: Added GeoDist command ([#1988](https://github.com/valkey-io/valkey-glide/pull/1988))
* Node: Added GeoHash command ([#1997](https://github.com/valkey-io/valkey-glide/pull/1997))
* Node: Added HStrlen command ([#2020](https://github.com/valkey-io/valkey-glide/pull/2020))
* Node: Added ZRandMember command ([#2013](https://github.com/valkey-io/valkey-glide/pull/2013))

### Breaking Changes

* Node: Update XREAD to return a Map of Map ([#1494](https://github.com/valkey-io/valkey-glide/pull/1494))
* Node: Rename RedisClient to GlideClient and RedisClusterClient to GlideClusterClient ([#1670](https://github.com/valkey-io/valkey-glide/pull/1670))
* Python: Rename RedisClient to GlideClient, RedisClusterClient to GlideClusterClient and BaseRedisClient to BaseClient([#1669](https://github.com/valkey-io/valkey-glide/pull/1669))
* Python: Rename ClusterClientConfiguration to GlideClusterClientConfiguration ([#1806](https://github.com/valkey-io/valkey-glide/pull/1806))

### Fixes

* Python: fixing a bug with transaction exec ([#1796](https://github.com/valkey-io/valkey-glide/pull/1796))

## 0.4.1 (2024-06-02)

### Fixes

* Node: Fix set command bug with expiry option ([#1508](https://github.com/valkey-io/valkey-glide/pull/1508))

## 0.4.0 (2024-05-26)

### Changes

* Python: Added JSON.DEL JSON.FORGET commands ([#1146](https://github.com/valkey-io/valkey-glide/pull/1146))
* Python: Added STRLEN command ([#1230](https://github.com/valkey-io/valkey-glide/pull/1230))
* Python: Added HKEYS command ([#1228](https://github.com/valkey-io/valkey-glide/pull/1228))
* Python: Added RPUSHX and LPUSHX commands ([#1239](https://github.com/valkey-io/valkey-glide/pull/1239))
* Python: Added ZREMRANGEBYSCORE command ([#1151](https://github.com/valkey-io/valkey-glide/pull/1151))
* Node, Python: Added SPOP, SPOPCOUNT commands. ([#1117](https://github.com/valkey-io/valkey-glide/pull/1117), [#1261](https://github.com/valkey-io/valkey-glide/pull/1261))
* Node: Added ZRANGE command ([#1115](https://github.com/valkey-io/valkey-glide/pull/1115))
* Python: Added RENAME command ([#1252](https://github.com/valkey-io/valkey-glide/pull/1252))
* Python: Added APPEND command ([#1152](https://github.com/valkey-io/valkey-glide/pull/1152))
* Python: Added GEOADD command ([#1259](https://github.com/valkey-io/valkey-glide/pull/1259))
* Python: Added GEODIST command ([#1260](https://github.com/valkey-io/valkey-glide/pull/1260))
* Python: Added GEOHASH command ([#1281](https://github.com/valkey-io/valkey-glide/pull/1281))
* Python: Added ZLEXCOUNT command ([#1305](https://github.com/valkey-io/valkey-glide/pull/1305))
* Python: Added ZREMRANGEBYLEX command ([#1306](https://github.com/valkey-io/valkey-glide/pull/1306))
* Python: Added LINSERT command ([#1304](https://github.com/valkey-io/valkey-glide/pull/1304))
* Python: Added GEOPOS command ([#1301](https://github.com/valkey-io/valkey-glide/pull/1301))
* Node: Added PFADD command ([#1317](https://github.com/valkey-io/valkey-glide/pull/1317))
* Python: Added PFADD command ([#1315](https://github.com/valkey-io/valkey-glide/pull/1315))
* Python: Added ZMSCORE command ([#1357](https://github.com/valkey-io/valkey-glide/pull/1357))
* Python: Added HRANDFIELD command ([#1334](https://github.com/valkey-io/valkey-glide/pull/1334))
* Node: Added BLPOP command ([#1223](https://github.com/valkey-io/valkey-glide/pull/1223))
* Python: Added XADD, XTRIM commands ([#1320](https://github.com/valkey-io/valkey-glide/pull/1320))
* Python: Added BLPOP and BRPOP commands ([#1369](https://github.com/valkey-io/valkey-glide/pull/1369))
* Python: Added ZRANGESTORE command ([#1377](https://github.com/valkey-io/valkey-glide/pull/1377))
* Python: Added ZDIFFSTORE command ([#1378](https://github.com/valkey-io/valkey-glide/pull/1378))
* Python: Added ZDIFF command ([#1401](https://github.com/valkey-io/valkey-glide/pull/1401))
* Python: Added BZPOPMIN and BZPOPMAX commands ([#1399](https://github.com/valkey-io/valkey-glide/pull/1399))
* Python: Added ZUNIONSTORE, ZINTERSTORE commands ([#1388](https://github.com/valkey-io/valkey-glide/pull/1388))
* Python: Added ZRANDMEMBER command ([#1413](https://github.com/valkey-io/valkey-glide/pull/1413))
* Python: Added BZMPOP command ([#1412](https://github.com/valkey-io/valkey-glide/pull/1412))
* Python: Added ZINTERCARD command ([#1418](https://github.com/valkey-io/valkey-glide/pull/1418))
* Python: Added ZMPOP command ([#1417](https://github.com/valkey-io/valkey-glide/pull/1417))
* Python: Added SMOVE command ([#1421](https://github.com/valkey-io/valkey-glide/pull/1421))
* Python: Added SUNIONSTORE command ([#1423](https://github.com/valkey-io/valkey-glide/pull/1423))
* Python: Added SINTER command ([#1434](https://github.com/valkey-io/valkey-glide/pull/1434))
* Python: Added SDIFF command ([#1437](https://github.com/valkey-io/valkey-glide/pull/1437))
* Python: Added SDIFFSTORE command ([#1449](https://github.com/valkey-io/valkey-glide/pull/1449))
* Python: Added SINTERSTORE command ([#1459](https://github.com/valkey-io/valkey-glide/pull/1459))
* Python: Added SMISMEMBER command ([#1461](https://github.com/valkey-io/valkey-glide/pull/1461))
* Python: Added SETRANGE command ([#1453](https://github.com/valkey-io/valkey-glide/pull/1453))

### Fixes

* Python: Fix typing error "'type' object is not subscriptable" ([#1203](https://github.com/valkey-io/valkey-glide/pull/1203))
* Core: Fixed blocking commands to use the specified timeout from the command argument ([#1283](https://github.com/valkey-io/valkey-glide/pull/1283))

### Breaking Changes

* Node: Changed `smembers` and `spopCount` functions to return Set instead of string[] ([#1299](https://github.com/valkey-io/valkey-glide/pull/1299))

### Features

* Node: Added support for alpine based platform (Or any x64-musl or arm64-musl based platforms) ([#1379](https://github.com/valkey-io/valkey-glide/pull/1379))

## 0.3.3 (2024-03-28)

### Fixes

* Node: Fix issue with dual usage, `CommonJS` and `ECMAScript` modules. ([#1199](https://github.com/valkey-io/valkey-glide/pull/1199))

## 0.3.0 (2024-03-25)

### Changes

* Python Node: Allow routing Cluster requests by address. ([#1021](https://github.com/valkey-io/valkey-glide/pull/1021))
* Python, Node: Added HSETNX command. ([#954](https://github.com/valkey-io/valkey-glide/pull/954), [#1091](https://github.com/valkey-io/valkey-glide/pull/1091))
* Python, Node: Added SISMEMBER command ([#972](https://github.com/valkey-io/valkey-glide/pull/972), [#1083](https://github.com/valkey-io/valkey-glide/pull/1083))
* Python, Node: Added TYPE command ([#945](https://github.com/valkey-io/valkey-glide/pull/945), [#980](https://github.com/valkey-io/valkey-glide/pull/980))
* Python, Node: Added HLEN command ([#944](https://github.com/valkey-io/valkey-glide/pull/944), [#981](https://github.com/valkey-io/valkey-glide/pull/981))
* Python, Node: Added ZCOUNT command ([#878](https://github.com/valkey-io/valkey-glide/pull/878)) ([#909](https://github.com/valkey-io/valkey-glide/pull/909))
* Python, Node: Added ECHO command ([#953](https://github.com/valkey-io/valkey-glide/pull/953), [#1010](https://github.com/valkey-io/valkey-glide/pull/1010))
* Python, Node: Added ZPOPMIN command ([#975](https://github.com/valkey-io/valkey-glide/pull/975), [#1008](https://github.com/valkey-io/valkey-glide/pull/1008))
* Node: Added STRLEN command ([#993](https://github.com/valkey-io/valkey-glide/pull/993))
* Node: Added LINDEX command ([#999](https://github.com/valkey-io/valkey-glide/pull/999))
* Python, Node: Added ZPOPMAX command ([#996](https://github.com/valkey-io/valkey-glide/pull/996), [#1009](https://github.com/valkey-io/valkey-glide/pull/1009))
* Python: Added ZRANGE command ([#906](https://github.com/valkey-io/valkey-glide/pull/906))
* Python, Node: Added PTTL command ([#1036](https://github.com/valkey-io/valkey-glide/pull/1036), [#1082](https://github.com/valkey-io/valkey-glide/pull/1082))
* Python, Node: Added HVAL command ([#1130](https://github.com/valkey-io/valkey-glide/pull/1130)), ([#1022](https://github.com/valkey-io/valkey-glide/pull/1022))
* Python, Node: Added PERSIST command ([#1129](https://github.com/valkey-io/valkey-glide/pull/1129)), ([#1023](https://github.com/valkey-io/valkey-glide/pull/1023))
* Node: Added ZREMRANGEBYSCORE command ([#926](https://github.com/valkey-io/valkey-glide/pull/926))
* Node: Added ZREMRANGEBYRANK command ([#924](https://github.com/valkey-io/valkey-glide/pull/924))
* Node: Added Xadd, Xtrim commands. ([#1057](https://github.com/valkey-io/valkey-glide/pull/1057))
* Python: Added json module and JSON.SET JSON.GET commands ([#1056](https://github.com/valkey-io/valkey-glide/pull/1056))
* Python, Node: Added Time command ([#1147](https://github.com/valkey-io/valkey-glide/pull/1147)), ([#1114](https://github.com/valkey-io/valkey-glide/pull/1114))
* Python, Node: Added LINDEX command ([#1058](https://github.com/valkey-io/valkey-glide/pull/1058), [#999](https://github.com/valkey-io/valkey-glide/pull/999))
* Python, Node: Added ZRANK command ([#1065](https://github.com/valkey-io/valkey-glide/pull/1065), [#1149](https://github.com/valkey-io/valkey-glide/pull/1149))
* Core: Enabled Cluster Mode periodic checks by default ([#1089](https://github.com/valkey-io/valkey-glide/pull/1089))
* Node: Added Rename command. ([#1124](https://github.com/valkey-io/valkey-glide/pull/1124))
* Python: Added JSON.TOGGLE command ([#1184](https://github.com/valkey-io/valkey-glide/pull/1184))

### Features

* Python: Allow chaining function calls on transaction. ([#987](https://github.com/valkey-io/valkey-glide/pull/987))
* Node: Adding support for GLIDE's usage in projects based on either `CommonJS` or `ECMAScript` modules. ([#1132](https://github.com/valkey-io/valkey-glide/pull/1132))
* Python, Node: Added Cluster Mode configuration for periodic checks interval ([#1089](https://github.com/valkey-io/valkey-glide/pull/1089), [#1158](https://github.com/valkey-io/valkey-glide/pull/1158))

## 0.2.0 (2024-02-11)

### Changes

* Python, Node: Added ZCARD command ([#871](https://github.com/valkey-io/valkey-glide/pull/871), [#885](https://github.com/valkey-io/valkey-glide/pull/885))
* Python, Node: Added ZADD and ZADDINCR commands ([#814](https://github.com/valkey-io/valkey-glide/pull/814), [#830](https://github.com/valkey-io/valkey-glide/pull/830))
* Python, Node: Added ZREM command ([#834](https://github.com/valkey-io/valkey-glide/pull/834), [#831](https://github.com/valkey-io/valkey-glide/pull/831))
* Python, Node: Added ZSCORE command ([#877](https://github.com/valkey-io/valkey-glide/pull/877), [#889](https://github.com/valkey-io/valkey-glide/pull/889))
* Use jemalloc as default allocator. ([#847](https://github.com/valkey-io/valkey-glide/pull/847))
* Python, Node: Added RPOPCOUNT and LPOPCOUNT to transaction ([#874](https://github.com/valkey-io/valkey-glide/pull/874))
* Standalone client: Improve connection errors. ([#854](https://github.com/valkey-io/valkey-glide/pull/854))
* Python, Node: When recieving LPOP/RPOP with count, convert result to Array. ([#811](https://github.com/valkey-io/valkey-glide/pull/811))
* Python, Node: Added TYPE command ([#945](https://github.com/valkey-io/valkey-glide/pull/945), [#980](https://github.com/valkey-io/valkey-glide/pull/980))
* Python, Node: Added HLEN command ([#944](https://github.com/valkey-io/valkey-glide/pull/944), [#981](https://github.com/valkey-io/valkey-glide/pull/981))
* Python, Node: Added ZCOUNT command ([#878](https://github.com/valkey-io/valkey-glide/pull/878)) ([#909](https://github.com/valkey-io/valkey-glide/pull/909))
* Python: Added ECHO command ([#953](https://github.com/valkey-io/valkey-glide/pull/953))
* Python, Node: Added ZPOPMIN command ([#975](https://github.com/valkey-io/valkey-glide/pull/975), [#1008](https://github.com/valkey-io/valkey-glide/pull/1008))
* Node: Added STRLEN command ([#993](https://github.com/valkey-io/valkey-glide/pull/993))
* Node: Added LINDEX command ([#999](https://github.com/valkey-io/valkey-glide/pull/999))
* Python, Node: Added ZPOPMAX command ([#996](https://github.com/valkey-io/valkey-glide/pull/996), [#1009](https://github.com/valkey-io/valkey-glide/pull/1009))
* Python: Added DBSIZE command ([#1040](https://github.com/valkey-io/valkey-glide/pull/1040))
* Core: Log directory can now be modified by setting the environment variable `GLIDE_LOG_DIR` ([#2704](https://github.com/valkey-io/valkey-glide/issues/2704))

### Features

* Python, Node: Added support in Lua Scripts ([#775](https://github.com/valkey-io/valkey-glide/pull/775), [#860](https://github.com/valkey-io/valkey-glide/pull/860))
* Node: Allow chaining function calls on transaction. ([#902](https://github.com/valkey-io/valkey-glide/pull/902))

### Fixes

* Core: Fixed `Connection Refused` error not to close the client ([#872](https://github.com/valkey-io/valkey-glide/pull/872))
* Socket listener: fix identifier for closed reader error. ([#853](https://github.com/valkey-io/valkey-glide/pull/853))
* Node: Fix issues with type import & exports ([#767](https://github.com/valkey-io/valkey-glide/pull/767))
* Core: Added handling to "?" and NULL hostnames in CLUSTER SLOTS ([#104](https://github.com/amazon-contributing/redis-rs/pull/104))
* Core: Cluster connection now reconnects after full disconnect. ([#100](https://github.com/amazon-contributing/redis-rs/pull/100))

## 0.1.0 (2024-01-17)

Preview release of **GLIDE for Redis** a Polyglot Redis client.

See the [README](README.md) for additional information.
