# Welcome to Valkey GLIDE!

Valkey General Language Independent Driver for the Enterprise (GLIDE) is the official open-source Valkey client library, proudly part of the Valkey organization. Our mission is to make your experience with Valkey and Redis OSS seamless and enjoyable. Whether you're a seasoned developer or just starting out, Valkey GLIDE is here to support you every step of the way.

# Why Choose Valkey GLIDE?

- **Community and Open Source**: Join our vibrant community and contribute to the project. We are always here to respond, and the client is for the community.
- **Reliability**: Built with best practices learned from over a decade of operating Redis OSS-compatible services.
- **Performance**: Optimized for high performance and low latency.
- **High Availability**: Designed to ensure your applications are always up and running.
- **Cross-Language Support**: Implemented using a core driver framework written in Rust, with language-specific extensions to ensure consistency and reduce complexity.
- **Stability and Fault Tolerance**: We brought our years of experience to create a bulletproof client.
- **Backed and Supported by AWS and GCP**: Ensuring robust support and continuous improvement of the project.

## Documentation

See GLIDE's Python [documentation site](https://glide.valkey.io/getting-started/quickstart/?lang=python).

## Supported Engine Versions

Refer to the [Supported Engine Versions table](https://github.com/valkey-io/valkey-glide/blob/main/README.md#supported-engine-versions) for details.

# Getting Started - Python Wrapper

## System Requirements

The release of Valkey GLIDE was tested on the following platforms:

Linux:

-   Ubuntu 20 (x86_64/amd64 and arm64/aarch64)
-   Amazon Linux 2 (AL2) and 2023 (AL2023) (x86_64)

**Note: Currently Alpine Linux / MUSL is NOT supported.**

macOS:

-   macOS 14.7 (Apple silicon/aarch_64)
-   macOS 13.7 (x86_64/amd64)

## Python Supported Versions

| Python Version |
|----------------|
| 3.9            |
| 3.10           |
| 3.11           |
| 3.12           |
| 3.13           |

Valkey GLIDE transparently supports both the `asyncio` and `trio` concurrency frameworks.

## Installation and Setup

### ✅ Async Client

To install the async version:

```bash
pip install valkey-glide
```

Verify installation:

```bash
python3
>>> import glide
```

### ✅ Sync Client

To install the sync version:

```bash
pip install valkey-glide-sync
```

Verify installation:

```bash
python3
>>> import glide_sync
```

---

## Basic Examples

### 🔁 Async Client

### ✅ Async Cluster Mode

```python
import asyncio
from glide import GlideClusterClientConfiguration, NodeAddress, GlideClusterClient

async def test_cluster_client():
    addresses = [NodeAddress("address.example.com", 6379)]
    # It is recommended to set a timeout for your specific use case
    config = GlideClusterClientConfiguration(addresses, request_timeout=500)  # 500ms timeout
    client = await GlideClusterClient.create(config)
    set_result = await client.set("foo", "bar")
    print(f"Set response is {set_result}")
    get_result = await client.get("foo")
    print(f"Get response is {get_result}")

asyncio.run(test_cluster_client())
```

#### ✅ Async Standalone Mode

```python
import asyncio
from glide import GlideClientConfiguration, NodeAddress, GlideClient

async def test_standalone_client():
    addresses = [
        NodeAddress("server_primary.example.com", 6379),
        NodeAddress("server_replica.example.com", 6379)
    ]
    # It is recommended to set a timeout for your specific use case
    config = GlideClientConfiguration(addresses, request_timeout=500)  # 500ms timeout
    client = await GlideClient.create(config)
    set_result = await client.set("foo", "bar")
    print(f"Set response is {set_result}")
    get_result = await client.get("foo")
    print(f"Get response is {get_result}")

asyncio.run(test_standalone_client())
```

---

### 🔂 Sync Client

#### ✅ Sync Cluster Mode

```python
from glide_sync import GlideClusterClientConfiguration, NodeAddress, GlideClusterClient

def test_cluster_client():
    addresses = [NodeAddress("address.example.com", 6379)]
    # It is recommended to set a timeout for your specific use case
    config = GlideClusterClientConfiguration(addresses, request_timeout=500)  # 500ms timeout
    client = GlideClusterClient.create(config)
    set_result = client.set("foo", "bar")
    print(f"Set response is {set_result}")
    get_result = client.get("foo")
    print(f"Get response is {get_result}")

test_cluster_client()
```

#### ✅ Sync Standalone Mode

```python
from glide_sync import GlideClientConfiguration, NodeAddress, GlideClient

def test_standalone_client():
    addresses = [
        NodeAddress("server_primary.example.com", 6379),
        NodeAddress("server_replica.example.com", 6379)
    ]
    # It is recommended to set a timeout for your specific use case
    config = GlideClientConfiguration(addresses, request_timeout=500)  # 500ms timeout
    client = GlideClient.create(config)
    set_result = client.set("foo", "bar")
    print(f"Set response is {set_result}")
    get_result = client.get("foo")
    print(f"Get response is {get_result}")

test_standalone_client()
```

---

## PubSub Configuration

Valkey GLIDE supports dynamic PubSub with automatic subscription reconciliation. Configure the reconciliation interval to ensure subscriptions remain synchronized:

```python
# Async client
from glide import GlideClientConfiguration, NodeAddress, GlideClient, AdvancedGlideClientConfiguration

config = GlideClientConfiguration(
    addresses=[NodeAddress("localhost", 6379)],
    advanced_config=AdvancedGlideClientConfiguration(
        pubsub_reconciliation_interval=5000  # Reconcile every 5 seconds (in milliseconds)
    )
)
client = await GlideClient.create(config)

# Sync client
from glide_sync import GlideClientConfiguration, NodeAddress, GlideClient, AdvancedGlideClientConfiguration

config = GlideClientConfiguration(
    addresses=[NodeAddress("localhost", 6379)],
    advanced_config=AdvancedGlideClientConfiguration(
        pubsub_reconciliation_interval=5000  # Reconcile every 5 seconds (in milliseconds)
    )
)
client = GlideClient.create(config)
```

### Pre-configured Subscriptions

You can configure subscriptions at client creation time. The client will automatically establish these subscriptions during connection:

```python
# Async client with pre-configured subscriptions
from glide import (
    GlideClientConfiguration,
    NodeAddress,
    GlideClient,
)

def message_callback(msg, context):
    print(f"Received message on {msg.channel}: {msg.message}")

config = GlideClientConfiguration(
    addresses=[NodeAddress("localhost", 6379)],
    pubsub_subscriptions=GlideClientConfiguration.PubSubSubscriptions(
        channels_and_patterns={
            GlideClientConfiguration.PubSubChannelModes.Exact: {"news", "updates"},
            GlideClientConfiguration.PubSubChannelModes.Pattern: {"events.*", "logs.*"},
        },
        callback=message_callback,
        context=None  # Optional context passed to callback
    )
)
client = await GlideClient.create(config)

# Cluster client with sharded pubsub
from glide import GlideClusterClientConfiguration, GlideClusterClient

config = GlideClusterClientConfiguration(
    addresses=[NodeAddress("localhost", 6379)],
    pubsub_subscriptions=GlideClusterClientConfiguration.PubSubSubscriptions(
        channels_and_patterns={
            GlideClusterClientConfiguration.PubSubChannelModes.Exact: {"channel1"},
            GlideClusterClientConfiguration.PubSubChannelModes.Pattern: {"pattern*"},
            GlideClusterClientConfiguration.PubSubChannelModes.Sharded: {"shard_channel"},
        },
        callback=message_callback,
        context=None
    )
)
cluster_client = await GlideClusterClient.create(config)
```

### Dynamic Subscription Management

Subscribe and unsubscribe at runtime:

```python
# Subscribe to channels
await client.subscribe({"channel1", "channel2"}, timeout_ms=5000)

# Subscribe to patterns
await client.psubscribe({"news.*", "events.*"}, timeout_ms=5000)

# Unsubscribe from specific channels
await client.unsubscribe({"channel1"}, timeout_ms=5000)

# Unsubscribe from all channels
from glide.async_commands.core import ALL_CHANNELS
await client.unsubscribe(ALL_CHANNELS, timeout_ms=5000)

# Unsubscribe from all patterns
from glide.async_commands.core import ALL_PATTERNS
await client.punsubscribe(ALL_PATTERNS, timeout_ms=5000)

# Cluster: sharded pubsub
await cluster_client.ssubscribe({"shard_channel"}, timeout_ms=5000)
await cluster_client.sunsubscribe({"shard_channel"}, timeout_ms=5000)

# Check subscription state
state = await client.get_subscriptions()
print(f"Desired: {state.desired_subscriptions}")
print(f"Actual: {state.actual_subscriptions}")
```

### Client Statistics

Monitor client performance and subscription health using `get_statistics()`:

```python
stats = await client.get_statistics()  # Async
# or
stats = client.get_statistics()  # Sync

# Available metrics:
# - total_connections: Number of active connections
# - total_clients: Number of client instances
# - total_values_compressed: Count of compressed values
# - total_values_decompressed: Count of decompressed values
# - total_original_bytes: Original data size before compression
# - total_bytes_compressed: Compressed data size
# - total_bytes_decompressed: Decompressed data size
# - compression_skipped_count: Times compression was skipped
# - subscription_out_of_sync_count: Failed reconciliation attempts
# - subscription_last_sync_timestamp: Last successful sync (milliseconds since epoch)
```

---

## OpenTelemetry Configuration

Valkey GLIDE supports OpenTelemetry for distributed tracing and metrics collection. This allows you to monitor command execution, measure latency, and track performance across your application.

### Basic OpenTelemetry Setup

Both async and sync clients support OpenTelemetry configuration:

```python
# Async client
from glide import OpenTelemetry, OpenTelemetryConfig, OpenTelemetryTracesConfig, OpenTelemetryMetricsConfig

# Sync client
from glide_sync import OpenTelemetry, OpenTelemetryConfig, OpenTelemetryTracesConfig, OpenTelemetryMetricsConfig

# Initialize OpenTelemetry (once per process)
OpenTelemetry.init(OpenTelemetryConfig(
    traces=OpenTelemetryTracesConfig(
        endpoint="http://localhost:4318/v1/traces",  # OTLP HTTP endpoint
        sample_percentage=1  # Sample 1% of requests (default)
    ),
    metrics=OpenTelemetryMetricsConfig(
        endpoint="http://localhost:4318/v1/metrics"
    ),
    flush_interval_ms=5000  # Flush every 5 seconds (default)
))
```

### Supported Endpoints

- **HTTP/HTTPS**: `http://localhost:4318/v1/traces` or `https://...`
- **gRPC**: `grpc://localhost:4317`
- **File**: `file:///tmp/traces.json` (for local testing)

### Runtime Configuration

You can adjust the sampling percentage at runtime:

```python
# Change sampling to 10%
OpenTelemetry.set_sample_percentage(10)

# Check current sampling rate
current_rate = OpenTelemetry.get_sample_percentage()
```

**Note**: OpenTelemetry can only be initialized once per process. To change configuration, restart your application.

### Trace Context Propagation

Trace context propagation is disabled by default. Set `enable_trace_context_propagation=True` to use the active application span as the parent of sampled GLIDE spans. GLIDE's default `sample_percentage=1` selects about 1% of commands even inside a sampled parent, set it to `100` when every command span should join the application trace. The active span is picked up through Python's `contextvars`, so no context argument is needed for sync, `asyncio` or `trio` commands.

GLIDE only needs the optional `opentelemetry-api>=1.6.0` package to read the active span. The following example also uses your application's OpenTelemetry SDK and OTLP/HTTP exporter. With the API package alone, there is no recording provider and therefore no valid parent span.

Install the API through the `otel` extra for the client you use:

```bash
pip install "valkey-glide[otel]"       # async client
pip install "valkey-glide-sync[otel]"  # sync client
```

```bash
pip install opentelemetry-sdk opentelemetry-exporter-otlp-proto-http
```

With a Valkey server on `localhost:6379` and an OTLP/HTTP collector on `localhost:4318`, this example exports both sides of the trace to the same collector:

```python
import asyncio

from glide import (
    GlideClient, GlideClientConfiguration, NodeAddress,
    OpenTelemetry, OpenTelemetryConfig, OpenTelemetryTracesConfig,
)
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

traces_endpoint = "http://localhost:4318/v1/traces"

# Application spans use the application's SDK exporter.
provider = TracerProvider()
provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter(endpoint=traces_endpoint)))
trace.set_tracer_provider(provider)

# GLIDE spans use GLIDE's separate exporter at the same OTLP/HTTP endpoint.
OpenTelemetry.init(OpenTelemetryConfig(
    traces=OpenTelemetryTracesConfig(
        endpoint=traces_endpoint,
        sample_percentage=100,
        enable_trace_context_propagation=True,
    ),
    flush_interval_ms=1000,
))

async def main():
    client = await GlideClient.create(GlideClientConfiguration(
        addresses=[NodeAddress("localhost", 6379)],
    ))
    try:
        with trace.get_tracer(__name__).start_as_current_span("checkout"):
            await client.set("cart:42", "value")
            await client.get("cart:42")
    finally:
        await client.close()
        provider.force_flush()
        await asyncio.sleep(2)  # Allow GLIDE's exporter to flush before exit.

asyncio.run(main())
```

#### Where GLIDE's Spans Are Exported

GLIDE exports its spans through the `endpoint` given to `OpenTelemetryTracesConfig`. Your application exports the parent through its own SDK. They share a trace ID but travel as separate streams. If the endpoints feed different backends or either exporter drops a span, the backend can show a parent without its GLIDE child or a child with a dangling `parent_span_id`. GLIDE cannot detect this from the command response. Point both exporters at the same collector and check both pipelines when a joined trace is missing.

#### Sampling and the Parent Span

- **Propagation is enabled and a valid parent span is active**: `sample_percentage` decides whether GLIDE creates a child span. At `0`, no GLIDE span is created. When a span is selected, it uses the active span as its parent. The parent's sampled flag then controls whether the Rust OpenTelemetry SDK exports the child. An unsampled parent produces no exported GLIDE child spans, even when `sample_percentage` is `100`.
- **Propagation is disabled or no valid parent span is active**: `sample_percentage` decides whether GLIDE creates an independent trace root.

#### Without `opentelemetry-api`

The package is optional and only needed when `enable_trace_context_propagation=True`. Without it installed, propagation is off and GLIDE spans behave as they always have: independent trace roots governed by `sample_percentage` alone. If reading the active span context ever fails, it never fails your command. GLIDE logs each failure at debug level and continues as if no span were active, leaving `sample_percentage` to decide.

---

## Compression Configuration (EXPERIMENTAL)

**⚠️ WARNING: This feature is experimental and can result in incorrect responses from certain commands without careful use.**

Valkey GLIDE supports automatic compression and decompression of string values to reduce memory usage and network bandwidth.

**Incompatible Commands**: Compression is NOT compatible with commands that manipulate string data on the server:
- APPEND, GETRANGE, SETRANGE, STRLEN, LCS
- INCR, INCRBY, INCRBYFLOAT, DECR, DECRBY
- GETBIT, SETBIT, BITCOUNT, BITPOS, BITFIELD, BITFIELD_RO, BITOP

Using these commands with compressed values will result in incorrect behavior or errors.

### Basic Compression Setup

```python
# Async client
from glide import GlideClientConfiguration, NodeAddress, GlideClient, CompressionConfiguration, CompressionBackend

config = GlideClientConfiguration(
    addresses=[NodeAddress("localhost", 6379)],
    compression_configuration=CompressionConfiguration(
        backend=CompressionBackend.ZSTD,  # or CompressionBackend.LZ4
        min_compression_size=64,  # Only compress values >= 64 bytes
        compression_level=3  # ZSTD: 1-22, LZ4: -128 to 12
    )
)
client = await GlideClient.create(config)

# Sync client
from glide_sync import GlideClientConfiguration, NodeAddress, GlideClient, CompressionConfiguration, CompressionBackend

config = GlideClientConfiguration(
    addresses=[NodeAddress("localhost", 6379)],
    compression_configuration=CompressionConfiguration(
        backend=CompressionBackend.ZSTD,
        min_compression_size=64,
        compression_level=3
    )
)
client = GlideClient.create(config)
```

### Supported Commands

**Write Commands** (automatic compression):
- SET, MSET, SETEX, PSETEX, SETNX

**Read Commands** (automatic decompression):
- GET, MGET, GETEX, GETDEL

### Monitoring Compression

Use `get_statistics()` to monitor compression effectiveness:

```python
stats = await client.get_statistics()  # or client.get_statistics() for sync
print(f"Values compressed: {stats['total_values_compressed']}")
print(f"Original bytes: {stats['total_original_bytes']}")
print(f"Compressed bytes: {stats['total_bytes_compressed']}")
print(f"Compression skipped: {stats['compression_skipped_count']}")
```

---

For complete examples with error handling, please refer to:
- [Async Cluster Example](https://github.com/valkey-io/valkey-glide/blob/main/examples/python/cluster_example.py)
- [Async Standalone Example](https://github.com/valkey-io/valkey-glide/blob/main/examples/python/standalone_example.py)


### Building & Testing

Development instructions for local building & testing the package are in the [DEVELOPER.md](https://github.com/valkey-io/valkey-glide/blob/main/python/DEVELOPER.md#build-from-source) file.

## Community and Feedback

We encourage you to join our community to support, share feedback, and ask questions. You can approach us for anything on our Valkey Slack: [Join Valkey Slack](https://join.slack.com/t/valkey-oss-developer/shared_invite/zt-2nxs51chx-EB9hu9Qdch3GMfRcztTSkQ).
