# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import gc
import os
import time
from contextlib import contextmanager
from typing import Iterator, Optional

import anyio
import glide.glide_client as glide_client_module
import glide_shared.opentelemetry
import psutil  # type: ignore[import-untyped]
import pytest
from glide import (
    OpenTelemetryConfig,
    OpenTelemetryMetricsConfig,
    OpenTelemetryTracesConfig,
    Script,
)
from glide.opentelemetry import OpenTelemetry
from glide_shared.commands.batch import Batch, ClusterBatch
from glide_shared.config import ProtocolVersion
from glide_shared.exceptions import ClosingError, ConfigurationError, RequestError
from glide_shared.logger import Level, Logger
from opentelemetry import trace
from opentelemetry.trace import NonRecordingSpan, SpanContext, TraceFlags
from opentelemetry.trace.span import TraceState

from tests.async_tests.conftest import create_client
from tests.otel_test_utils import (
    assert_external_parent,
    assert_root_spans,
    build_timeout_error,
    check_spans_ready,
    read_and_parse_span_file,
)

# Constants
TIMEOUT = 50  # seconds
VALID_ENDPOINT_TRACES = "/tmp/spans.json"
VALID_FILE_ENDPOINT_TRACES = f"file://{VALID_ENDPOINT_TRACES}"  # noqa: E231
VALID_ENDPOINT_METRICS = "https://valid-endpoint/v1/metrics"

# A fixed remote parent context, so assertions can name the exact expected IDs.
PARENT_TRACE_ID = 0x0AF7651916CD43DD8448EB211C80319C
PARENT_SPAN_ID = 0xB7AD6B7169203331
PARENT_TRACE_ID_HEX = format(PARENT_TRACE_ID, "032x")
PARENT_SPAN_ID_HEX = format(PARENT_SPAN_ID, "016x")


@contextmanager
def use_parent_span(
    sampled: bool,
    span_id: int = PARENT_SPAN_ID,
    trace_state: Optional[TraceState] = None,
) -> Iterator[None]:
    """Make a fixed span context the active OTel span for the duration of the block."""
    span_context = SpanContext(
        trace_id=PARENT_TRACE_ID,
        span_id=span_id,
        is_remote=True,
        trace_flags=TraceFlags(TraceFlags.SAMPLED if sampled else TraceFlags.DEFAULT),
        trace_state=trace_state,
    )
    with trace.use_span(NonRecordingSpan(span_context), end_on_exit=False):
        yield


@contextmanager
def restore_sample_percentage() -> Iterator[None]:
    """Restore the sample percentage on exit."""
    original = OpenTelemetry.get_sample_percentage()
    try:
        yield
    finally:
        if original is not None:
            OpenTelemetry.set_sample_percentage(original)


def remove_span_file() -> None:
    """Delete the span file so a test only sees the spans it produced."""
    if os.path.exists(VALID_ENDPOINT_TRACES):
        os.unlink(VALID_ENDPOINT_TRACES)


async def wait_for_spans_to_be_flushed(
    span_file_path: str,
    expected_span_names: list[str],
    expected_span_counts: Optional[dict[str, int]] = None,
    timeout: float = 15.0,
    check_interval: float = 0.5,
) -> None:
    """
    Wait for spans to be flushed to the span file with retry mechanism.

    Args:
        span_file_path: Path to the span file
        expected_span_names: List of expected span names to wait for
        expected_span_counts: Optional dict mapping span names to expected counts
        timeout: Maximum time to wait in seconds
        check_interval: Interval between checks in seconds

    Raises:
        Exception: If timeout is reached or spans are not found
    """
    start_time = time.time()

    while time.time() - start_time < timeout:
        # Check if file exists and is readable
        if os.path.exists(span_file_path) and os.path.getsize(span_file_path) > 0:
            try:
                _, _, span_names = read_and_parse_span_file(span_file_path)

                if check_spans_ready(
                    span_names, expected_span_names, expected_span_counts
                ):
                    return  # All expected spans found

            except Exception:
                # File might be in the process of being written, continue waiting
                pass

        await anyio.sleep(check_interval)

    # Timeout reached
    raise build_timeout_error(span_file_path, expected_span_names, expected_span_counts)


def test_is_tracing_enabled(monkeypatch):
    traces = OpenTelemetryTracesConfig(
        endpoint=VALID_FILE_ENDPOINT_TRACES, sample_percentage=0
    )
    assert not traces.enable_trace_context_propagation

    propagation_traces = OpenTelemetryTracesConfig(
        endpoint=VALID_FILE_ENDPOINT_TRACES,
        enable_trace_context_propagation=True,
    )
    assert propagation_traces.enable_trace_context_propagation

    monkeypatch.setattr(OpenTelemetry, "_instance", None)
    monkeypatch.setattr(OpenTelemetry, "_config", OpenTelemetryConfig(traces=traces))
    assert not OpenTelemetry.is_tracing_enabled()

    monkeypatch.setattr(OpenTelemetry, "_instance", OpenTelemetry())
    monkeypatch.setattr(OpenTelemetry, "_config", None)
    assert not OpenTelemetry.is_tracing_enabled()

    monkeypatch.setattr(
        OpenTelemetry,
        "_config",
        OpenTelemetryConfig(metrics=OpenTelemetryMetricsConfig(VALID_ENDPOINT_METRICS)),
    )
    assert not OpenTelemetry.is_tracing_enabled()

    monkeypatch.setattr(OpenTelemetry, "_config", OpenTelemetryConfig(traces=traces))
    assert OpenTelemetry.is_tracing_enabled()


def test_wrong_opentelemetry_config():
    """Test various invalid OpenTelemetry configurations"""
    # Wrong traces endpoint
    with pytest.raises(ConfigurationError, match=r".*Parse error.*"):
        OpenTelemetry.init(
            OpenTelemetryConfig(
                traces=OpenTelemetryTracesConfig(
                    endpoint="wrong.endpoint",
                    sample_percentage=100,
                ),
            )
        )

    # Wrong metrics endpoint
    with pytest.raises(ConfigurationError, match=r".*Parse error.*"):
        OpenTelemetry.init(
            OpenTelemetryConfig(
                metrics=OpenTelemetryMetricsConfig(
                    endpoint="wrong.endpoint",
                ),
            )
        )

    # Negative flush interval
    with pytest.raises(
        ConfigurationError,
        match=r".*InvalidInput: flushIntervalMs must be a positive integer.*",
    ):
        OpenTelemetry.init(
            OpenTelemetryConfig(
                traces=OpenTelemetryTracesConfig(
                    endpoint=VALID_FILE_ENDPOINT_TRACES,
                    sample_percentage=100,
                ),
                flush_interval_ms=-400,
            )
        )

    # Negative sample percentage
    # TODO: This should be a ValueError: Trace sample percentage must be between 0 and 100
    with pytest.raises(
        OverflowError, match=r".*can't convert negative number to unsigned.*"
    ):
        OpenTelemetry.init(
            OpenTelemetryConfig(
                traces=OpenTelemetryTracesConfig(
                    endpoint=VALID_FILE_ENDPOINT_TRACES,
                    sample_percentage=-10,
                ),
            )
        )

    # Wrong traces file path
    with pytest.raises(
        ConfigurationError, match=r".*File path must start with 'file://'.*"
    ):
        OpenTelemetry.init(
            OpenTelemetryConfig(
                traces=OpenTelemetryTracesConfig(
                    endpoint="file:invalid-path/v1/traces.json",
                    sample_percentage=100,
                ),
            )
        )

    # Wrong metrics file path
    with pytest.raises(
        ConfigurationError, match=r".*File path must start with 'file://'.*"
    ):
        OpenTelemetry.init(
            OpenTelemetryConfig(
                traces=None,
                metrics=OpenTelemetryMetricsConfig(
                    endpoint="file:invalid-path/v1/metrics.json",
                ),
            )
        )

    # Wrong directory path
    with pytest.raises(
        ConfigurationError,
        match=r".*The directory does not exist or is not a directory.*",
    ):
        OpenTelemetry.init(
            OpenTelemetryConfig(
                traces=OpenTelemetryTracesConfig(
                    endpoint="file:///no-exits-path/v1/traces.json",
                    sample_percentage=100,
                ),
            )
        )

    # No traces or metrics provided
    with pytest.raises(
        ConfigurationError,
        match=r".*At least one of traces or metrics must be provided.*",
    ):
        OpenTelemetry.init(
            OpenTelemetryConfig(
                traces=None,
                metrics=None,
            )
        )


@pytest.mark.anyio
async def test_span_not_exported_before_init_otel(request):
    """Test that spans are not exported before OpenTelemetry is initialized"""
    # Clean up any existing files
    if os.path.exists(VALID_ENDPOINT_TRACES):
        os.unlink(VALID_ENDPOINT_TRACES)

    client = await create_client(
        request,
        cluster_mode=False,
        protocol=ProtocolVersion.RESP3,
    )

    # Execute a command
    await client.get("testSpanNotExportedBeforeInitOtel")

    # Check that no spans file was created
    assert not os.path.exists(VALID_ENDPOINT_TRACES)

    await client.close()


@pytest.mark.anyio
class TestOpenTelemetryGlide:
    @pytest.fixture(scope="class")
    async def setup_class(self, request):
        # Test wrong OpenTelemetry config before initializing
        test_wrong_opentelemetry_config()

        # Test that spans are not exported before OpenTelemetry is initialized
        await test_span_not_exported_before_init_otel(request)

    @pytest.fixture(autouse=True)
    async def setup_test(self, request, cluster_mode):
        # Initialize OpenTelemetry with 100% sampling for tests
        opentelemetry_config = OpenTelemetryConfig(
            OpenTelemetryTracesConfig(
                endpoint=VALID_FILE_ENDPOINT_TRACES,
                sample_percentage=100,
                enable_trace_context_propagation=True,
            ),
            metrics=OpenTelemetryMetricsConfig(endpoint=VALID_ENDPOINT_METRICS),
            flush_interval_ms=100,
        )

        # Initialize OpenTelemetry
        OpenTelemetry.init(opentelemetry_config)

        # Clean up before each test - ensure file is completely removed
        if os.path.exists(VALID_ENDPOINT_TRACES):
            os.unlink(VALID_ENDPOINT_TRACES)

        # Give a small delay to ensure OpenTelemetry is fully initialized
        await anyio.sleep(0.1)

        yield

        # Clean up after each test
        if os.path.exists(VALID_ENDPOINT_TRACES):
            os.unlink(VALID_ENDPOINT_TRACES)

        client = await create_client(
            request, cluster_mode=cluster_mode, request_timeout=2000
        )
        await client.custom_command(["FLUSHALL"])
        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_concurrent_commands_span_lifecycle(
        self, request, protocol, cluster_mode
    ):
        """Test that spans are properly handled with concurrent commands"""
        # This test should not run in parallel with other tests due to the memory check
        # Force garbage collection
        gc.collect()

        # Get initial memory usage
        process = psutil.Process()
        initial_memory = process.memory_info().rss  # Get resident set size in bytes

        # Create client
        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Execute multiple concurrent commands
        commands = [
            lambda: client.set("test_key1", "value1"),
            lambda: client.get("test_key1"),
            lambda: client.set("test_key2", "value2"),
            lambda: client.get("test_key2"),
            lambda: client.set("test_key3", "value3"),
            lambda: client.get("test_key3"),
        ]

        async with anyio.create_task_group() as tg:
            for command in commands:
                tg.start_soon(command)

        # Force garbage collection again
        gc.collect()

        # Wait for spans to be flushed
        await anyio.sleep(1)

        # Get final memory usage
        final_memory = process.memory_info().rss

        # Calculate memory increase percentage
        memory_increase = ((final_memory - initial_memory) / initial_memory) * 100

        # Assert memory increase is not more than 10%
        assert (
            memory_increase < 10
        ), f"Memory usage increased by {memory_increase: .2f}%, which is more than the allowed 10%"

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_batch_cluster_span_lifecycle(self, request, protocol, cluster_mode):
        """Test that spans are properly handled with batch cluster operations"""
        # This test should not run in parallel with other tests due to the memory check
        # Force garbage collection
        gc.collect()

        # Get initial memory usage
        process = psutil.Process()
        initial_memory = process.memory_info().rss  # Get resident set size in bytes

        # Create cluster client
        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Execute multiple concurrent batch operations using ClusterBatch
        batch_operations = []

        # Create first batch
        batch1 = ClusterBatch(is_atomic=True)
        batch1.set("{batch}key1", "value1")
        batch1.get("{batch}key1")
        batch1.strlen("{batch}key1")
        batch_operations.append(lambda b=batch1: client.exec(b, raise_on_error=True))

        # Create second batch
        batch2 = ClusterBatch(is_atomic=True)
        batch2.set("{batch}key2", "value2")
        batch2.object_refcount("{batch}key2")
        batch_operations.append(lambda b=batch2: client.exec(b, raise_on_error=True))

        # Create third batch
        batch3 = ClusterBatch(is_atomic=True)
        batch3.set("{batch}key3", "value3")
        batch3.get("{batch}key3")
        batch3.delete(["{batch}key1", "{batch}key2", "{batch}key3"])
        batch_operations.append(lambda b=batch3: client.exec(b, raise_on_error=True))

        # Execute all batches concurrently
        async with anyio.create_task_group() as tg:
            for operation in batch_operations:
                tg.start_soon(operation)

        # Force garbage collection again
        gc.collect()

        # Wait for spans to be flushed
        await anyio.sleep(1)

        # Get final memory usage
        final_memory = process.memory_info().rss

        # Calculate memory increase percentage
        memory_increase = ((final_memory - initial_memory) / initial_memory) * 100

        # Assert memory increase is not more than 10%
        assert (
            memory_increase < 10
        ), f"Memory usage increased by {memory_increase: .2f}%, which is more than the allowed 10%"

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_span_memory_leak(self, request, protocol, cluster_mode):
        """Test that spans don't cause memory leaks"""
        # This test should not run in parallel with other tests due to the memory check
        # Force garbage collection
        gc.collect()

        # Create client and get initial memory usage
        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        process = psutil.Process()
        initial_memory = process.memory_info().rss

        # Execute a series of commands sequentially
        for i in range(100):
            key = f"test_key_{i}"
            await client.set(key, f"value_{i}")
            await client.get(key)

        # Close client
        await client.close()

        # Force garbage collection
        gc.collect()

        # Get final memory usage
        final_memory = process.memory_info().rss

        # Calculate memory increase percentage
        memory_increase = ((final_memory - initial_memory) / initial_memory) * 100

        # Assert memory increase is not more than 10%
        assert (
            memory_increase < 10
        ), f"Memory usage increased by {memory_increase: .2f}%, which is more than the allowed 10%"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_percentage_requests_config(self, request, protocol, cluster_mode):
        """Test that sample percentage configuration works correctly"""
        # Create client
        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Set sample percentage to 0%
        OpenTelemetry.set_sample_percentage(0)
        assert OpenTelemetry.get_sample_percentage() == 0

        # Wait for any pending spans to be flushed
        await anyio.sleep(0.5)

        # Clean up any existing files
        if os.path.exists(VALID_ENDPOINT_TRACES):
            os.unlink(VALID_ENDPOINT_TRACES)

        # Execute commands with 0% sampling
        for i in range(100):
            await client.set(
                "GlideClusterClient_test_percentage_requests_config", "value"
            )

        # Wait for any spans to be flushed (though none should be created)
        await anyio.sleep(0.5)

        # Check that no spans file was created
        assert not os.path.exists(VALID_ENDPOINT_TRACES)

        # Set sample percentage to 100%
        OpenTelemetry.set_sample_percentage(100)

        # Execute commands with 100% sampling
        for i in range(10):
            key = f"GlideClusterClient_test_percentage_requests_config_{i}"
            await client.get(key)

        # Wait for spans to be flushed
        await wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES,
            expected_span_names=["Get"],
            expected_span_counts={"Get": 10},
        )

        # Read the span file and check span names
        _, _, span_names = read_and_parse_span_file(VALID_ENDPOINT_TRACES)

        # Check that "Get" spans were created
        assert "Get" in span_names

        # Check that exactly 10 "Get" spans were created
        assert span_names.count("Get") == 10

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_otel_global_config_not_reinitialize(
        self, request, protocol, cluster_mode
    ):
        """Test that OpenTelemetry cannot be reinitialized"""
        # Try to reinitialize with invalid config
        opentelemetry_config = OpenTelemetryConfig(
            OpenTelemetryTracesConfig(endpoint="wrong.endpoint", sample_percentage=1)
        )

        # This should not throw an error because OpenTelemetry is already initialized
        OpenTelemetry.init(opentelemetry_config)

        # Create client
        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Execute a command
        await client.set("GlideClusterClient_test_otel_global_config", "value")

        # Wait for spans to be flushed
        await anyio.sleep(0.5)

        # Read the span file and check span names
        _, _, span_names = read_and_parse_span_file(VALID_ENDPOINT_TRACES)

        # Check that "Set" spans were created
        assert "Set" in span_names

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_span_batch(self, request, protocol, cluster_mode):
        """Test that batch operations create spans correctly"""
        # This test should not run in parallel with other tests due to the memory check
        # Force garbage collection
        gc.collect()

        # Get initial memory usage
        process = psutil.Process()
        initial_memory = process.memory_info().rss

        # Create client
        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Create and execute a batch using the correct Python API

        # Use appropriate batch type based on cluster mode
        if cluster_mode:
            batch = ClusterBatch(is_atomic=True)
        else:
            batch = Batch(is_atomic=True)

        batch.set("test_key", "foo")
        batch.object_refcount("test_key")

        response = await client.exec(batch, raise_on_error=True)
        assert response is not None

        if response is not None:
            assert len(response) == 2
            assert response[0] == "OK"  # batch.set("test_key", "foo")
            assert response[1] >= 1  # batch.object_refcount("test_key")

        # Wait for spans to be flushed
        await wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["Batch"]
        )

        # Read the span file and check span names
        _, _, span_names = read_and_parse_span_file(VALID_ENDPOINT_TRACES)

        # Check for expected span names
        assert "Batch" in span_names

        # Force garbage collection
        gc.collect()

        await client.close()

        # Get final memory usage
        final_memory = process.memory_info().rss

        # Calculate memory increase percentage
        memory_increase = ((final_memory - initial_memory) / initial_memory) * 100

        # Assert memory increase is not more than 10%
        assert (
            memory_increase < 10
        ), f"Memory usage increased by {memory_increase: .2f}%, which is more than the allowed 10%"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_number_of_clients_with_same_config(
        self, request, protocol, cluster_mode
    ):
        """Test that multiple clients with the same config work correctly with OpenTelemetry"""
        # Create two clients
        client1 = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        client2 = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Execute commands on both clients
        await client1.set("test_key", "value")
        await client2.get("test_key")

        # Wait for spans to be flushed with retry mechanism
        await wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["Set", "Get"]
        )

        # Read the span file and check span names
        _, _, span_names = read_and_parse_span_file(VALID_ENDPOINT_TRACES)

        # Check for expected span names
        assert "Get" in span_names
        assert "Set" in span_names

        # Close clients
        await client1.close()
        await client2.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_span_script_invocation(self, request, protocol, cluster_mode):
        """Test that script invocation (EVALSHA) creates a span with DB attributes"""
        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        script = Script("return 'Hello'")
        result = await client.invoke_script(script, keys=["k"], args=["a"])
        assert result == b"Hello"

        # Wait for spans to be flushed
        await wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["EVALSHA"]
        )

        # Read the span file and check the EVALSHA span and its DB attributes
        _, span_objects, span_names = read_and_parse_span_file(VALID_ENDPOINT_TRACES)

        assert "EVALSHA" in span_names

        # The core attaches DB semantic convention attributes via invoke_script.
        # Mirror the attributes set by `set_db_script_attributes` in
        # glide-core/src/otel_db_semantics.rs: db.operation.name, db.query.text
        # (script hash + keys, args masked), plus the connection-level attributes
        # from `set_db_connection_attributes` (db.system.name, server.address,
        # server.port, db.namespace).
        evalsha_attrs = [
            attr
            for span in span_objects
            if span.get("name") == "EVALSHA"
            for attr in span.get("span_attributes", [])
        ]
        attr_keys = {k for attr in evalsha_attrs for k in attr.keys()}

        # Command-level attributes
        assert {"db.operation.name": "EVALSHA"} in evalsha_attrs
        assert "db.query.text" in attr_keys
        query_texts = [
            attr["db.query.text"] for attr in evalsha_attrs if "db.query.text" in attr
        ]
        # Script query text starts with "EVALSHA <hash> <numkeys>" and masks args.
        assert any(
            qt.startswith("EVALSHA ") and script.get_hash() in qt for qt in query_texts
        )

        # Connection-level attributes
        assert {"db.system.name": "redis"} in evalsha_attrs
        assert "server.address" in attr_keys
        assert "server.port" in attr_keys
        assert "db.namespace" in attr_keys

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_span_script_invocation_error_cleanup(
        self, request, protocol, cluster_mode
    ):
        """
        Negative path: when the server rejects the script (Lua error), the EVALSHA
        span must still be created, ended, and exported, and no span pointer must
        leak. Mirrors the error-path expectations that `_execute_command` already
        satisfies for regular commands.
        """
        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # `redis.error_reply` returns a Lua error to the client, which surfaces
        # as a RequestError. The span must still be dropped in `finally`.
        error_script = Script("return redis.error_reply('deliberate script error')")
        with pytest.raises(RequestError, match="script error"):
            await client.invoke_script(error_script)

        # A successful call after the error confirms the client is still usable
        # and that the error path did not leak a span pointer / half-open state.
        ok_script = Script("return 'ok'")
        assert await client.invoke_script(ok_script) == b"ok"

        # Two EVALSHA spans should have been exported: the failed one and the
        # follow-up successful one. The failed span still carries the DB attrs
        # because `set_db_script_attributes` runs before the server call.
        await wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES,
            expected_span_names=["EVALSHA"],
            expected_span_counts={"EVALSHA": 2},
        )
        _, span_objects, span_names = read_and_parse_span_file(VALID_ENDPOINT_TRACES)
        assert span_names.count("EVALSHA") >= 2
        evalsha_attrs = [
            attr
            for span in span_objects
            if span.get("name") == "EVALSHA"
            for attr in span.get("span_attributes", [])
        ]
        assert {"db.operation.name": "EVALSHA"} in evalsha_attrs

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_span_script_invocation_client_closed(
        self, request, protocol, cluster_mode
    ):
        """
        Negative path: invoking a script on a closed client must not leak a span.
        The `_is_closed` guard runs before the span is ever created, so the
        `finally` cleanup contract holds trivially, but exercise it explicitly to
        pin the behaviour and catch a future refactor that reorders the checks.
        """
        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )
        script = Script("return 'Hello'")
        await client.close()

        with pytest.raises(ClosingError):
            await client.invoke_script(script)

        # Give the exporter a moment; no EVALSHA span should be attributed to the
        # closed-client call. Any pre-existing spans from other tests are fine;
        # we just assert we didn't crash and no leak surfaced.
        await anyio.sleep(0.2)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_trace_context_propagation_disabled_by_default(
        self, request, cluster_mode, monkeypatch
    ):
        """An active application span is ignored unless propagation is enabled."""
        client = await create_client(request, cluster_mode=cluster_mode)
        traces = OpenTelemetry._config.traces if OpenTelemetry._config else None
        assert traces is not None
        monkeypatch.setattr(traces, "enable_trace_context_propagation", False)

        remove_span_file()
        with use_parent_span(sampled=True):
            await client.get("GlideClient_test_propagation_disabled")

        await wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["Get"]
        )
        _, span_objects, _ = read_and_parse_span_file(VALID_ENDPOINT_TRACES)
        assert_root_spans(span_objects, "Get")

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sampled_parent_span_obeys_sample_percentage(
        self, request, protocol, cluster_mode, monkeypatch
    ):
        """A parent cannot bypass 0% sampling, selected spans keep that parent."""
        client = await create_client(
            request, cluster_mode=cluster_mode, protocol=protocol
        )

        with restore_sample_percentage():
            OpenTelemetry.set_sample_percentage(0)
            await anyio.sleep(0.5)
            remove_span_file()

            def unexpected_parent(cls):
                pytest.fail(
                    "Parent context must not be read when GLIDE does not sample"
                )

            with monkeypatch.context() as patch:
                patch.setattr(
                    OpenTelemetry,
                    "_get_parent_span_context",
                    classmethod(unexpected_parent),
                )
                with use_parent_span(sampled=True):
                    await client.set("GlideClient_test_sampled_parent", "value")
                    await client.get("GlideClient_test_sampled_parent")
                    batch = (
                        ClusterBatch(is_atomic=False)
                        if cluster_mode
                        else Batch(is_atomic=False)
                    )
                    batch.get("GlideClient_test_sampled_parent")
                    await client.exec(batch, raise_on_error=True)
                    assert (
                        await client.invoke_script(Script("return 'Hello'")) == b"Hello"
                    )

            assert not os.path.exists(VALID_ENDPOINT_TRACES)

            OpenTelemetry.set_sample_percentage(100)
            with use_parent_span(sampled=True):
                await client.set("GlideClient_test_sampled_parent", "value")
                await client.get("GlideClient_test_sampled_parent")

            await wait_for_spans_to_be_flushed(
                VALID_ENDPOINT_TRACES, expected_span_names=["Set", "Get"]
            )

        _, span_objects, _ = read_and_parse_span_file(VALID_ENDPOINT_TRACES)
        for span_name in ("Set", "Get"):
            assert_external_parent(
                span_objects, span_name, PARENT_TRACE_ID_HEX, PARENT_SPAN_ID_HEX
            )

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_unsampled_parent_span_obeys_sample_percentage(
        self, request, cluster_mode, monkeypatch
    ):
        """Only selected spans reach an unsampled parent, the core drops them."""
        client = await create_client(request, cluster_mode=cluster_mode)
        create_command_span = glide_client_module._create_command_span
        parented_spans = 0

        def capture_parent(ffi, lib, span_name, parent):
            nonlocal parented_spans
            assert parent is not None
            assert parent.trace_id == PARENT_TRACE_ID_HEX.encode()
            assert parent.span_id == PARENT_SPAN_ID_HEX.encode()
            assert parent.trace_flags == TraceFlags.DEFAULT
            parented_spans += 1
            return create_command_span(ffi, lib, span_name, parent)

        monkeypatch.setattr(glide_client_module, "_create_command_span", capture_parent)

        with restore_sample_percentage():
            OpenTelemetry.set_sample_percentage(0)
            await anyio.sleep(0.5)
            remove_span_file()

            with use_parent_span(sampled=False):
                await client.set("GlideClient_test_unsampled_parent", "value")
            assert parented_spans == 0

            OpenTelemetry.set_sample_percentage(100)
            with use_parent_span(sampled=False):
                await client.get("GlideClient_test_unsampled_parent")

            await anyio.sleep(0.5)
            assert parented_spans == 1
            assert not os.path.exists(VALID_ENDPOINT_TRACES)

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_no_active_span_creates_root_spans(self, request, cluster_mode):
        """Without an active OTel span, spans remain independent trace roots."""
        client = await create_client(request, cluster_mode=cluster_mode)

        remove_span_file()
        await client.get("GlideClient_test_no_active_span")

        await wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["Get"]
        )
        _, span_objects, _ = read_and_parse_span_file(VALID_ENDPOINT_TRACES)
        assert_root_spans(span_objects, "Get")

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_batch_span_uses_parent_context(self, request, cluster_mode):
        """Batch spans are parented to the active span context."""
        client = await create_client(request, cluster_mode=cluster_mode)

        remove_span_file()
        batch = (
            ClusterBatch(is_atomic=False) if cluster_mode else Batch(is_atomic=False)
        )
        batch.set("GlideClient_test_batch_parent", "value")
        batch.get("GlideClient_test_batch_parent")

        with use_parent_span(sampled=True):
            await client.exec(batch, raise_on_error=True)

        await wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["Batch"]
        )
        _, span_objects, _ = read_and_parse_span_file(VALID_ENDPOINT_TRACES)
        assert_external_parent(
            span_objects, "Batch", PARENT_TRACE_ID_HEX, PARENT_SPAN_ID_HEX
        )

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_script_span_uses_parent_context(self, request, cluster_mode):
        """EVALSHA spans are parented to the active span context, and keep both their
        name and the DB semantic-convention attributes the core attaches."""
        client = await create_client(request, cluster_mode=cluster_mode)

        remove_span_file()
        script = Script("return 'Hello'")
        with use_parent_span(sampled=True):
            assert await client.invoke_script(script) == b"Hello"

        await wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["EVALSHA"]
        )
        _, span_objects, span_names = read_and_parse_span_file(VALID_ENDPOINT_TRACES)

        assert "EVALSHA" in span_names
        assert_external_parent(
            span_objects, "EVALSHA", PARENT_TRACE_ID_HEX, PARENT_SPAN_ID_HEX
        )

        evalsha_attrs = [
            attr
            for span in span_objects
            if span.get("name") == "EVALSHA"
            for attr in span.get("span_attributes", [])
        ]
        assert {"db.operation.name": "EVALSHA"} in evalsha_attrs

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_trace_state_is_propagated(self, request, cluster_mode):
        """A non-empty W3C tracestate is accepted by the core rather than causing a
        silent fallback to a root span."""
        client = await create_client(request, cluster_mode=cluster_mode)

        remove_span_file()
        trace_state = TraceState([("vendor1", "value1"), ("vendor2", "value2")])
        with use_parent_span(sampled=True, trace_state=trace_state):
            await client.get("GlideClient_test_trace_state")

        await wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["Get"]
        )
        _, span_objects, _ = read_and_parse_span_file(VALID_ENDPOINT_TRACES)
        assert_external_parent(
            span_objects, "Get", PARENT_TRACE_ID_HEX, PARENT_SPAN_ID_HEX
        )

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_innermost_active_span_wins(self, request, cluster_mode):
        """The context is read per command, so a nested span supersedes the outer one."""
        client = await create_client(request, cluster_mode=cluster_mode)

        remove_span_file()
        inner_span_id = 0x00F067AA0BA902B7
        with use_parent_span(sampled=True):
            with use_parent_span(sampled=True, span_id=inner_span_id):
                await client.get("GlideClient_test_nested_spans")

        await wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["Get"]
        )
        _, span_objects, _ = read_and_parse_span_file(VALID_ENDPOINT_TRACES)
        assert_external_parent(
            span_objects, "Get", PARENT_TRACE_ID_HEX, format(inner_span_id, "016x")
        )

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_parent_context_ignored_without_otel_api(
        self, request, cluster_mode, monkeypatch
    ):
        """With `opentelemetry-api` unavailable, propagation is off and spans behave
        exactly as they did before: independent trace roots, no error."""
        client = await create_client(request, cluster_mode=cluster_mode)

        monkeypatch.setattr(glide_shared.opentelemetry, "_otel_trace", None)
        remove_span_file()

        with use_parent_span(sampled=True):
            await client.get("GlideClient_test_no_otel_api")

        await wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["Get"]
        )
        _, span_objects, _ = read_and_parse_span_file(VALID_ENDPOINT_TRACES)
        assert_root_spans(span_objects, "Get")

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_parent_context_extraction_failure_falls_back(
        self, request, cluster_mode, monkeypatch
    ):
        """A misbehaving OTel SDK must never fail a command; spans fall back to roots."""
        client = await create_client(request, cluster_mode=cluster_mode)

        class _BrokenTrace:
            @staticmethod
            def get_current_span():
                raise RuntimeError("broken instrumentation")

        monkeypatch.setattr(glide_shared.opentelemetry, "_otel_trace", _BrokenTrace)
        logged_failures = []
        monkeypatch.setattr(
            Logger,
            "log",
            lambda level, identifier, message: logged_failures.append(
                (level, identifier, message)
            ),
        )
        remove_span_file()

        await client.set("GlideClient_test_broken_otel_api", "value")
        assert (await client.get("GlideClient_test_broken_otel_api")) == b"value"

        await wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["Set", "Get"]
        )
        _, span_objects, _ = read_and_parse_span_file(VALID_ENDPOINT_TRACES)
        assert_root_spans(span_objects, "Get")
        assert (
            logged_failures
            == [
                (
                    Level.DEBUG,
                    "GlideOpenTelemetry",
                    "Failed to read the active span context: broken instrumentation. "
                    "Continuing as if no span were active.",
                )
            ]
            * 2
        )

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_parented_span_memory_leak(self, request, cluster_mode):
        """The parented path allocates per-command C strings; make sure repeated use
        does not leak."""
        client = await create_client(request, cluster_mode=cluster_mode)

        gc.collect()
        process = psutil.Process()
        start_memory = process.memory_info().rss

        with use_parent_span(sampled=True):
            for i in range(100):
                key = f"GlideClient_test_parented_leak_{i}"
                await client.set(key, "value")
                await client.get(key)

        await client.close()
        gc.collect()
        end_memory = process.memory_info().rss
        assert end_memory < start_memory * 1.1
