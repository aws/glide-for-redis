/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * Client-Instance Pool for Node.js.
 *
 * All pool state (idle/active/waiters) is managed by `glide-core::ClientPool`
 * via the N-API functions `createPool`, `poolTryAcquire`, `poolAcquireBlocking`,
 * `poolRelease`, `poolMetrics`, and `poolDestroy`.  The TypeScript layer only:
 *   1. Serialises the connection request and pool config for Rust.
 *   2. Calls `poolBuildHandle` (JIT) to wrap an acquired `client_id` into a
 *      full `GlideClientHandle` with a dedicated worker thread.
 *   3. Wraps the handle in a `GlideClient` / `GlideClusterClient` for callers.
 *
 * Pool clients are created without a pub/sub push channel (pub/sub is not
 * supported on pooled connections).  State reset (DISCARD + UNWATCH + SELECT)
 * on release is performed by `release_client_async` inside Rust.
 *
 * Scope commands (WATCH/MULTI/EXEC via IsolatedScope) go through the Rust
 * glide-core::scope module for per-connection state tracking and slot pinning.
 *
 * ## When to use a pool in Node.js
 *
 * - **Blocking commands** (`BLPOP`, `BRPOP`, `XREAD BLOCK`) — holds one
 *   connection, letting other pool clients serve requests unblocked.
 * - **WATCH/MULTI/EXEC transactions** (use `IsolatedScope` for dedicated connections).
 * - **Large response deserialization** — parallel Rust worker threads decode concurrently.
 * - **TCP head-of-line blocking** avoidance under high throughput.
 * - **`worker_threads`** environments where each thread needs its own connection.
 */

import type { BaseClient, BaseClientConfiguration } from "./BaseClient";
import { GlideClient } from "./GlideClient";
import type { GlideClientConfiguration } from "./GlideClient";
import { GlideClusterClient } from "./GlideClusterClient";
import type { GlideClusterClientConfiguration } from "./GlideClusterClient";
import {
    createPool,
    poolTryAcquire,
    poolAcquireBlocking,
    poolRelease,
    poolMetrics,
    poolDestroy,
    removeAddressResolver,
} from "../build-ts/native";

/** Re-export the pool client type (full command set). */
export type PoolClient = BaseClient;

/**
 * Configuration for the client-instance pool.
 */
export interface PoolConfig {
    /** Maximum number of clients in the pool. Default: 10. */
    maxSize?: number;
    /** Minimum idle clients to pre-warm at creation. Default: 1. */
    minIdle?: number;
    /** Maximum time to wait when pool is exhausted (seconds). Default: 5. */
    acquireTimeoutS?: number;
    /**
     * Maximum inactivity time for a borrowed client before the pool reclaims it (ms).
     * The timer resets on every command sent. The abandon monitor skips clients
     * executing blocking commands (BLPOP, XREAD BLOCK, etc.).
     * Set to 0 to disable abandon detection. Default: 300000 (5 minutes).
     */
    abandonTimeoutMs?: number;
    /**
     * Time after which an idle connection is evicted (ms).
     * Default: 30000 (30 seconds).
     */
    idleTimeoutMs?: number;
    /** Whether to create cluster clients. Default: false. */
    clusterMode?: boolean;
}

/**
 * Pool metrics snapshot.
 */
export interface ClientPoolMetrics {
    idle: number;
    active: number;
    total: number;
}

/**
 * Client-instance pool managing real GlideClient / GlideClusterClient instances.
 *
 * All pool state (idle list, active set, waiters) is managed by
 * `glide-core::ClientPool`.  TypeScript only wraps acquired client IDs as
 * `GlideClient` handles.
 */
export class ClientPool {
    private closed = false;
    private readonly poolId: number;
    private readonly acquireTimeoutMs: number;
    private readonly isCluster: boolean;
    private readonly clientConfig: BaseClientConfiguration;
    private readonly resolverKey: string | undefined;

    private constructor(
        poolId: number,
        acquireTimeoutMs: number,
        isCluster: boolean,
        clientConfig: BaseClientConfiguration,
        resolverKey: string | undefined,
    ) {
        this.poolId = poolId;
        this.acquireTimeoutMs = acquireTimeoutMs;
        this.isCluster = isCluster;
        this.clientConfig = clientConfig;
        this.resolverKey = resolverKey;
    }

    /**
     * Create a new client-instance pool.
     *
     * Warms up `minIdle` real connections in the background (via Rust).
     * The returned Promise resolves after the first connection succeeds
     * (connectivity validation) and rejects on first-connection failure.
     */
    static async create(
        clientConfig: BaseClientConfiguration,
        poolConfig?: PoolConfig,
    ): Promise<ClientPool> {
        const maxSize = poolConfig?.maxSize ?? 10;
        const minIdle = poolConfig?.minIdle ?? 1;
        const acquireTimeoutS = poolConfig?.acquireTimeoutS ?? 5;
        const abandonTimeoutMs = poolConfig?.abandonTimeoutMs ?? 300_000;
        const isCluster = poolConfig?.clusterMode ?? false;

        // Reject pubsub subscriptions.
        const cfg = clientConfig as
            GlideClientConfiguration | GlideClusterClientConfiguration;

        if ("pubsubSubscriptions" in cfg && cfg.pubsubSubscriptions) {
            throw new Error(
                "Pool clients cannot have pubsub subscriptions configured. " +
                    "Use the main client's pubsub API for subscriptions.",
            );
        }

        // Serialise the connection config into protobuf bytes using the
        // appropriate typed client without opening a network connection.
        // serializeConfig also registers any addressResolver and returns the
        // key so we can clean up if pool creation fails.
        const { bytes: connectionRequestBytes, resolverKey } = isCluster
            ? GlideClusterClient.serializeConfig(
                  clientConfig as GlideClusterClientConfiguration,
              )
            : GlideClient.serializeConfig(
                  clientConfig as GlideClientConfiguration,
              );

        const poolConfigNapi = {
            maxSize,
            minIdle,
            idleTimeoutMs: poolConfig?.idleTimeoutMs ?? 30_000,
            requestTimeoutMs:
                (clientConfig as { requestTimeout?: number }).requestTimeout ??
                5_000,
            abandonTimeoutMs,
        };

        // createPool returns Promise<pool_id>.  Rejects if first connection fails.
        let poolId: number;
        try {
            poolId = await createPool(connectionRequestBytes, poolConfigNapi);
        } catch (e) {
            // Clean up the address resolver registration if pool creation failed.
            if (resolverKey) {
                removeAddressResolver(resolverKey);
            }
            throw e;
        }

        return new ClientPool(
            poolId,
            acquireTimeoutS * 1000,
            isCluster,
            clientConfig,
            resolverKey,
        );
    }

    /**
     * Acquire a client from the pool.
     *
     * Returns a real GlideClient or GlideClusterClient with the full command API.
     */
    async acquire(timeout?: number): Promise<BaseClient> {
        if (this.closed) {
            throw new Error("Pool is closed");
        }

        // Try non-blocking acquire first.
        const clientId = poolTryAcquire(this.poolId);

        if (clientId >= 0) {
            try {
                return await this.buildClientForId(clientId);
            } catch (e) {
                // Handle build failed — release the slot so pool capacity is recovered.
                await poolRelease(this.poolId, clientId).catch(() => {
                    /* best effort */
                });
                throw e;
            }
        }

        // Pool full / no idle — wait with timeout.
        const timeoutMs = timeout ? timeout * 1000 : this.acquireTimeoutMs;
        const result = await poolAcquireBlocking(this.poolId, timeoutMs);

        if (result >= 0) {
            try {
                return await this.buildClientForId(result);
            } catch (e) {
                // Handle build failed — release the slot so pool capacity is recovered.
                await poolRelease(this.poolId, result).catch(() => {
                    /* best effort */
                });
                throw e;
            }
        }

        if (result === -1) {
            throw new Error(
                `Pool exhausted: could not acquire client within ${timeoutMs / 1000}s`,
            );
        }

        // result === -2: pool was destroyed
        throw new Error("Pool is closed");
    }

    /**
     * Release a client back to the pool.
     *
     * State reset (DISCARD + UNWATCH + SELECT) is performed in Rust before
     * returning the connection to idle.  The handle is stopped without
     * unregistering the client from the scope registry (so the next acquire
     * can reuse the same underlying connection).
     */
    async release(client: BaseClient): Promise<void> {
        const clientId = client.getClientId();
        if (clientId < 0) return;

        // Stop the handle's worker thread without removing from scope registry.
        // This is pool-safe: the underlying Client remains registered so that
        // the next pool_build_handle() call can find and reuse it.
        (
            client as unknown as {
                clientHandle: { closeForPoolRelease?: () => void } | null;
            }
        ).clientHandle?.closeForPoolRelease?.();

        // Null out the handle to prevent use-after-release.
        (client as unknown as { clientHandle: null }).clientHandle = null;

        // Rust state reset + return to idle.
        await poolRelease(this.poolId, clientId);
    }

    /**
     * Borrow a client, execute callback, then auto-release.
     *
     * The callback receives a real GlideClient/GlideClusterClient with full API.
     */
    async borrow<T>(
        fn: (client: BaseClient) => Promise<T>,
        timeout?: number,
    ): Promise<T> {
        const client = await this.acquire(timeout);

        try {
            return await fn(client);
        } finally {
            await this.release(client);
        }
    }

    /** Get pool metrics. */
    getMetrics(): ClientPoolMetrics {
        if (this.closed) return { idle: 0, active: 0, total: 0 };
        const m = poolMetrics(this.poolId);
        return { idle: m.idle, active: m.active, total: m.total };
    }

    get idleCount(): number {
        return this.getMetrics().idle;
    }

    get activeCount(): number {
        return this.getMetrics().active;
    }

    get totalCount(): number {
        return this.getMetrics().total;
    }

    get isClosed(): boolean {
        return this.closed;
    }

    /** Close the pool and destroy all managed connections. */
    close(): void {
        if (!this.closed) {
            this.closed = true;
            poolDestroy(this.poolId);
            // Clean up the address resolver registration if one was used.
            // Pool paths use get() (not remove()), so the entry persists until
            // the pool is closed and we explicitly remove it here.
            if (this.resolverKey) {
                removeAddressResolver(this.resolverKey);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Build a GlideClient / GlideClusterClient for an acquired pool client_id.
     *
     * Calls `poolBuildHandle` to spin up a new worker thread wrapping the
     * already-connected pool client, then returns a fully operational
     * GlideClient / GlideClusterClient instance.
     */
    private async buildClientForId(clientId: number): Promise<BaseClient> {
        return this.isCluster
            ? GlideClusterClient.fromPoolClientId(
                  clientId,
                  this.clientConfig as GlideClusterClientConfiguration,
              )
            : GlideClient.fromPoolClientId(
                  clientId,
                  this.clientConfig as GlideClientConfiguration,
              );
    }
}
