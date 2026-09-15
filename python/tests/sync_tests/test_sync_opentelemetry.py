# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import gc
import os
import threading
import time
from contextlib import contextmanager
from typing import Iterator, Optional

import glide_shared.opentelemetry
import psutil  # type: ignore[import-untyped]
import pytest
from glide_shared.commands.batch import Batch, ClusterBatch
from glide_shared.config import ProtocolVersion
from glide_shared.logger import Level, Logger
from glide_sync import (
    OpenTelemetryConfig,
    OpenTelemetryMetricsConfig,
    OpenTelemetryTracesConfig,
)
from glide_sync.opentelemetry import OpenTelemetry
from glide_sync.sync_commands.script import Script
from opentelemetry import trace
from opentelemetry.trace import NonRecordingSpan, SpanContext, TraceFlags
from opentelemetry.trace.span import TraceState

from tests.otel_test_utils import (
    assert_external_parent,
    assert_root_spans,
    build_timeout_error,
    check_spans_ready,
    read_and_parse_span_file,
)
from tests.sync_tests.conftest import create_sync_client

# Constants
TIMEOUT = 50  # seconds
VALID_ENDPOINT_TRACES = "/tmp/spans.json"
VALID_FILE_ENDPOINT_TRACES = f"file://{VALID_ENDPOINT_TRACES}"
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


def _wait_for_spans_to_be_flushed(
    span_file_path: str,
    expected_span_names: list[str],
    expected_span_counts: Optional[dict[str, int]] = None,
    timeout: float = 15.0,
    check_interval: float = 0.5,
) -> None:
    """
    Wait for spans to be flushed to the span file (synchronous version).

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
        if os.path.exists(span_file_path) and os.path.getsize(span_file_path) > 0:
            try:
                _, _, span_names = read_and_parse_span_file(span_file_path)

                if check_spans_ready(
                    span_names, expected_span_names, expected_span_counts
                ):
                    return

            except Exception:
                pass

        time.sleep(check_interval)

    raise build_timeout_error(span_file_path, expected_span_names, expected_span_counts)


def test_sync_is_tracing_enabled(monkeypatch):
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


def test_sync_wrong_opentelemetry_config():
    """Test various invalid OpenTelemetry configurations"""
    from glide_shared.exceptions import ConfigurationError

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
        match=r".*flushIntervalMs must be a positive integer.*",
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
    with pytest.raises(
        OverflowError, match=r".*can't convert negative number to unsigned*"
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


def test_sync_span_not_exported_before_init_otel(request):
    """Test that spans are not exported before OpenTelemetry is initialized"""
    # Clean up any existing files
    if os.path.exists(VALID_ENDPOINT_TRACES):
        os.unlink(VALID_ENDPOINT_TRACES)

    client = create_sync_client(
        request,
        cluster_mode=False,
        protocol=ProtocolVersion.RESP3,
    )

    # Execute a command
    client.get("testSpanNotExportedBeforeInitOtel")

    # Check that no spans file was created
    assert not os.path.exists(VALID_ENDPOINT_TRACES)

    client.close()


class TestOpenTelemetryGlideSync:
    @pytest.fixture(scope="class")
    def setup_class(self, request):
        # Test wrong OpenTelemetry config before initializing
        test_sync_wrong_opentelemetry_config()

        # Test that spans are not exported before OpenTelemetry is initialized
        test_sync_span_not_exported_before_init_otel(request)

    @pytest.fixture(autouse=True)
    def setup_test(self, request, cluster_mode):
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
        time.sleep(0.1)

        yield

        # Clean up after each test
        if os.path.exists(VALID_ENDPOINT_TRACES):
            os.unlink(VALID_ENDPOINT_TRACES)

        client = create_sync_client(
            request, cluster_mode=cluster_mode, request_timeout=2000
        )
        client.custom_command(["FLUSHALL"])
        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_span_memory_leak(self, request, protocol, cluster_mode):
        """Test that spans don't cause memory leaks"""
        # Force garbage collection
        gc.collect()

        # Create client and get initial memory usage
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        process = psutil.Process()
        initial_memory = process.memory_info().rss

        # Execute a series of commands sequentially
        for i in range(100):
            key = f"test_key_{i}"
            client.set(key, f"value_{i}")
            client.get(key)

        # Close client
        client.close()

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
    def test_sync_concurrent_commands_span_lifecycle(
        self, request, protocol, cluster_mode
    ):
        """Test that spans are properly handled with concurrent commands"""
        # Force garbage collection
        gc.collect()

        # Get initial memory usage
        process = psutil.Process()
        initial_memory = process.memory_info().rss

        # Create client
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Execute multiple concurrent commands using threads
        threads = []
        commands = [
            lambda: client.set("test_key1", "value1"),
            lambda: client.get("test_key1"),
            lambda: client.set("test_key2", "value2"),
            lambda: client.get("test_key2"),
            lambda: client.set("test_key3", "value3"),
            lambda: client.get("test_key3"),
        ]

        for command in commands:
            thread = threading.Thread(target=command)
            threads.append(thread)
            thread.start()

        for thread in threads:
            thread.join()

        # Force garbage collection again
        gc.collect()

        # Wait for spans to be flushed
        time.sleep(1)

        # Get final memory usage
        final_memory = process.memory_info().rss

        # Calculate memory increase percentage
        memory_increase = ((final_memory - initial_memory) / initial_memory) * 100

        # Assert memory increase is not more than 10%
        assert (
            memory_increase < 10
        ), f"Memory usage increased by {memory_increase: .2f}%, which is more than the allowed 10%"

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_percentage_requests_config(self, request, protocol, cluster_mode):
        """Test that sample percentage configuration works correctly"""
        # Create client
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Set sample percentage to 0%
        OpenTelemetry.set_sample_percentage(0)
        assert OpenTelemetry.get_sample_percentage() == 0

        # Wait for any pending spans to be flushed
        time.sleep(0.5)

        # Clean up any existing files
        if os.path.exists(VALID_ENDPOINT_TRACES):
            os.unlink(VALID_ENDPOINT_TRACES)

        # Execute commands with 0% sampling
        for i in range(100):
            client.set("GlideClient_test_percentage_requests_config", "value")

        # Wait for any spans to be flushed (though none should be created)
        time.sleep(0.5)

        # Check that no spans file was created
        assert not os.path.exists(VALID_ENDPOINT_TRACES)

        # Set sample percentage to 100%
        OpenTelemetry.set_sample_percentage(100)

        # Execute commands with 100% sampling
        for i in range(10):
            key = f"GlideClient_test_percentage_requests_config_{i}"
            client.get(key)

        # Wait for spans to be flushed
        _wait_for_spans_to_be_flushed(
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

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_otel_global_config_not_reinitialize(
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
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Execute a command
        client.set("GlideClient_test_otel_global_config", "value")

        # Wait for spans to be flushed
        time.sleep(0.5)

        # Read the span file and check span names
        _, _, span_names = read_and_parse_span_file(VALID_ENDPOINT_TRACES)

        # Check that "Set" spans were created
        assert "Set" in span_names

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_span_batch(self, request, protocol, cluster_mode):
        """Test that batch operations create spans correctly"""
        # Force garbage collection
        gc.collect()

        # Get initial memory usage
        process = psutil.Process()
        initial_memory = process.memory_info().rss

        # Create client
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Use appropriate batch type based on cluster mode
        if cluster_mode:
            batch = ClusterBatch(is_atomic=True)
        else:
            batch = Batch(is_atomic=True)

        batch.set("test_key", "foo")
        batch.object_refcount("test_key")

        response = client.exec(batch, raise_on_error=True)
        assert response is not None

        if response is not None:
            assert len(response) == 2
            assert response[0] == "OK"  # batch.set("test_key", "foo")
            assert response[1] >= 1  # batch.object_refcount("test_key")

        # Wait for spans to be flushed
        _wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["Batch"]
        )

        # Read the span file and check span names
        _, _, span_names = read_and_parse_span_file(VALID_ENDPOINT_TRACES)

        # Check for expected span names
        assert "Batch" in span_names

        # Force garbage collection
        gc.collect()

        client.close()

        # Get final memory usage
        final_memory = process.memory_info().rss

        # Calculate memory increase percentage
        memory_increase = ((final_memory - initial_memory) / initial_memory) * 100

        # Assert memory increase is not more than 10%
        assert (
            memory_increase < 10
        ), f"Memory usage increased by {memory_increase: .2f}%, which is more than the allowed 10%"

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_batch_cluster_span_lifecycle(self, request, protocol, cluster_mode):
        """Test that spans are properly handled with batch cluster operations"""
        # This test should not run in parallel with other tests due to the memory check
        # Force garbage collection
        gc.collect()

        # Get initial memory usage
        process = psutil.Process()
        initial_memory = process.memory_info().rss  # Get resident set size in bytes

        # Create cluster client
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Execute multiple batch operations using ClusterBatch
        # Create first batch
        batch1 = ClusterBatch(is_atomic=True)
        batch1.set("{batch}key1", "value1")
        batch1.get("{batch}key1")
        batch1.strlen("{batch}key1")
        client.exec(batch1, raise_on_error=True)

        # Create second batch
        batch2 = ClusterBatch(is_atomic=True)
        batch2.set("{batch}key2", "value2")
        batch2.object_refcount("{batch}key2")
        client.exec(batch2, raise_on_error=True)

        # Create third batch
        batch3 = ClusterBatch(is_atomic=True)
        batch3.set("{batch}key3", "value3")
        batch3.get("{batch}key3")
        batch3.delete(["{batch}key1", "{batch}key2", "{batch}key3"])
        client.exec(batch3, raise_on_error=True)

        # Force garbage collection again
        gc.collect()

        # Wait for spans to be flushed
        time.sleep(1)

        # Get final memory usage
        final_memory = process.memory_info().rss

        # Calculate memory increase percentage
        memory_increase = ((final_memory - initial_memory) / initial_memory) * 100

        # Assert memory increase is not more than 10%
        assert (
            memory_increase < 10
        ), f"Memory usage increased by {memory_increase: .2f}%, which is more than the allowed 10%"

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_number_of_clients_with_same_config(
        self, request, protocol, cluster_mode
    ):
        """Test that multiple clients with the same config work correctly with OpenTelemetry"""
        # Create two clients
        client1 = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        client2 = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Execute commands on both clients
        client1.set("test_key", "value")
        client2.get("test_key")

        # Wait for spans to be flushed with retry mechanism
        _wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["Set", "Get"]
        )

        # Read the span file and check span names
        _, _, span_names = read_and_parse_span_file(VALID_ENDPOINT_TRACES)

        # Check for expected span names
        assert "Get" in span_names
        assert "Set" in span_names

        # Close clients
        client1.close()
        client2.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_span_script_invocation(self, request, protocol, cluster_mode):
        """Test that script invocation creates an EVALSHA span with DB attributes"""
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        script = Script("return 'Hello'")
        result = client.invoke_script(script, keys=["k"], args=["a"])
        assert result == b"Hello"

        # Wait for spans to be flushed
        _wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["EVALSHA"]
        )

        # Read the span file and check span names + DB attributes.
        # Mirrors the async assertions: db.operation.name, db.query.text,
        # db.system.name, server.address, server.port, db.namespace.
        _, span_objects, span_names = read_and_parse_span_file(VALID_ENDPOINT_TRACES)

        assert "EVALSHA" in span_names

        evalsha_attrs = [
            attr
            for span in span_objects
            if span.get("name") == "EVALSHA"
            for attr in span.get("span_attributes", [])
        ]
        attr_keys = {k for attr in evalsha_attrs for k in attr.keys()}

        assert {"db.operation.name": "EVALSHA"} in evalsha_attrs
        assert "db.query.text" in attr_keys
        query_texts = [
            attr["db.query.text"] for attr in evalsha_attrs if "db.query.text" in attr
        ]
        assert any(
            qt.startswith("EVALSHA ") and script.get_hash() in qt for qt in query_texts
        )

        assert {"db.system.name": "redis"} in evalsha_attrs
        assert "server.address" in attr_keys
        assert "server.port" in attr_keys
        assert "db.namespace" in attr_keys

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_span_script_invocation_error_cleanup(
        self, request, protocol, cluster_mode
    ):
        """
        Negative path: when the server rejects the script (Lua error), the
        EVALSHA span must still be ended and exported, and no span pointer
        must leak. Follow-up call succeeds, confirming no half-open state.
        """
        from glide_shared.exceptions import RequestError

        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        error_script = Script("return redis.error_reply('deliberate script error')")
        with pytest.raises(RequestError, match="script error"):
            client.invoke_script(error_script)

        ok_script = Script("return 'ok'")
        assert client.invoke_script(ok_script) == b"ok"

        _wait_for_spans_to_be_flushed(
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

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_span_script_invocation_client_closed(
        self, request, protocol, cluster_mode
    ):
        """
        Negative path: invoking a script on a closed client must not leak a
        span. The `_is_closed` guard in `_execute_script` runs before span
        creation; this test pins that ordering.
        """
        from glide_shared.exceptions import ClosingError

        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )
        script = Script("return 'Hello'")
        client.close()

        with pytest.raises(ClosingError):
            client.invoke_script(script)

        # Give exporter a moment; assert we didn't crash and no leak surfaced.
        time.sleep(0.2)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_trace_context_propagation_disabled_by_default(
        self, request, cluster_mode, monkeypatch
    ):
        """An active application span is ignored unless propagation is enabled."""
        client = create_sync_client(request, cluster_mode=cluster_mode)
        traces = OpenTelemetry._config.traces if OpenTelemetry._config else None
        assert traces is not None
        monkeypatch.setattr(traces, "enable_trace_context_propagation", False)

        remove_span_file()
        with use_parent_span(sampled=True):
            client.get("GlideSync_test_propagation_disabled")

        _wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["Get"]
        )
        _, span_objects, _ = read_and_parse_span_file(VALID_ENDPOINT_TRACES)
        assert_root_spans(span_objects, "Get")

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_sampled_parent_span_obeys_sample_percentage(
        self, request, protocol, cluster_mode, monkeypatch
    ):
        """A parent cannot bypass 0% sampling, selected spans keep that parent."""
        client = create_sync_client(
            request, cluster_mode=cluster_mode, protocol=protocol
        )

        with restore_sample_percentage():
            OpenTelemetry.set_sample_percentage(0)
            time.sleep(0.5)
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
                    client.set("GlideSync_test_sampled_parent", "value")
                    client.get("GlideSync_test_sampled_parent")
                    batch = (
                        ClusterBatch(is_atomic=False)
                        if cluster_mode
                        else Batch(is_atomic=False)
                    )
                    batch.get("GlideSync_test_sampled_parent")
                    client.exec(batch, raise_on_error=True)
                    assert client.invoke_script(Script("return 'Hello'")) == b"Hello"

            assert not os.path.exists(VALID_ENDPOINT_TRACES)

            OpenTelemetry.set_sample_percentage(100)
            with use_parent_span(sampled=True):
                client.set("GlideSync_test_sampled_parent", "value")
                client.get("GlideSync_test_sampled_parent")

            _wait_for_spans_to_be_flushed(
                VALID_ENDPOINT_TRACES, expected_span_names=["Set", "Get"]
            )

        _, span_objects, _ = read_and_parse_span_file(VALID_ENDPOINT_TRACES)
        for span_name in ("Set", "Get"):
            assert_external_parent(
                span_objects, span_name, PARENT_TRACE_ID_HEX, PARENT_SPAN_ID_HEX
            )

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_unsampled_parent_span_obeys_sample_percentage(
        self, request, cluster_mode, monkeypatch
    ):
        """Only selected spans reach an unsampled parent, the core drops them."""
        client = create_sync_client(request, cluster_mode=cluster_mode)
        create_command_span = glide_shared.opentelemetry._create_command_span
        parented_spans = 0

        def capture_parent(ffi, lib, span_name, parent):
            nonlocal parented_spans
            assert parent is not None
            assert parent.trace_id == PARENT_TRACE_ID_HEX.encode()
            assert parent.span_id == PARENT_SPAN_ID_HEX.encode()
            assert parent.trace_flags == TraceFlags.DEFAULT
            parented_spans += 1
            return create_command_span(ffi, lib, span_name, parent)

        monkeypatch.setattr(
            glide_shared.opentelemetry, "_create_command_span", capture_parent
        )

        with restore_sample_percentage():
            OpenTelemetry.set_sample_percentage(0)
            time.sleep(0.5)
            remove_span_file()

            with use_parent_span(sampled=False):
                client.set("GlideSync_test_unsampled_parent", "value")
            assert parented_spans == 0

            OpenTelemetry.set_sample_percentage(100)
            with use_parent_span(sampled=False):
                client.get("GlideSync_test_unsampled_parent")

            time.sleep(0.5)
            assert parented_spans == 1
            assert not os.path.exists(VALID_ENDPOINT_TRACES)

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_no_active_span_creates_root_spans(self, request, cluster_mode):
        """Without an active OTel span, spans remain independent trace roots."""
        client = create_sync_client(request, cluster_mode=cluster_mode)

        remove_span_file()
        client.get("GlideSync_test_no_active_span")

        _wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["Get"]
        )
        _, span_objects, _ = read_and_parse_span_file(VALID_ENDPOINT_TRACES)
        assert_root_spans(span_objects, "Get")

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_batch_span_uses_parent_context(self, request, cluster_mode):
        """Batch spans are parented to the active span context."""
        client = create_sync_client(request, cluster_mode=cluster_mode)

        remove_span_file()
        batch = (
            ClusterBatch(is_atomic=False) if cluster_mode else Batch(is_atomic=False)
        )
        batch.set("GlideSync_test_batch_parent", "value")
        batch.get("GlideSync_test_batch_parent")

        with use_parent_span(sampled=True):
            client.exec(batch, raise_on_error=True)

        _wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["Batch"]
        )
        _, span_objects, _ = read_and_parse_span_file(VALID_ENDPOINT_TRACES)
        assert_external_parent(
            span_objects, "Batch", PARENT_TRACE_ID_HEX, PARENT_SPAN_ID_HEX
        )

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_script_span_uses_parent_context(self, request, cluster_mode):
        """EVALSHA spans are parented to the active span context, and keep both their
        name and the DB semantic-convention attributes the core attaches."""
        client = create_sync_client(request, cluster_mode=cluster_mode)

        remove_span_file()
        script = Script("return 'Hello'")
        with use_parent_span(sampled=True):
            assert client.invoke_script(script) == b"Hello"

        _wait_for_spans_to_be_flushed(
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

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_trace_state_is_propagated(self, request, cluster_mode):
        """A non-empty W3C tracestate is accepted by the core rather than causing a
        silent fallback to a root span."""
        client = create_sync_client(request, cluster_mode=cluster_mode)

        remove_span_file()
        trace_state = TraceState([("vendor1", "value1"), ("vendor2", "value2")])
        with use_parent_span(sampled=True, trace_state=trace_state):
            client.get("GlideSync_test_trace_state")

        _wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["Get"]
        )
        _, span_objects, _ = read_and_parse_span_file(VALID_ENDPOINT_TRACES)
        assert_external_parent(
            span_objects, "Get", PARENT_TRACE_ID_HEX, PARENT_SPAN_ID_HEX
        )

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_innermost_active_span_wins(self, request, cluster_mode):
        """The context is read per command, so a nested span supersedes the outer one."""
        client = create_sync_client(request, cluster_mode=cluster_mode)

        remove_span_file()
        inner_span_id = 0x00F067AA0BA902B7
        with use_parent_span(sampled=True):
            with use_parent_span(sampled=True, span_id=inner_span_id):
                client.get("GlideSync_test_nested_spans")

        _wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["Get"]
        )
        _, span_objects, _ = read_and_parse_span_file(VALID_ENDPOINT_TRACES)
        assert_external_parent(
            span_objects, "Get", PARENT_TRACE_ID_HEX, format(inner_span_id, "016x")
        )

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_parent_context_ignored_without_otel_api(
        self, request, cluster_mode, monkeypatch
    ):
        """With `opentelemetry-api` unavailable, propagation is off and spans behave
        exactly as they did before: independent trace roots, no error."""
        client = create_sync_client(request, cluster_mode=cluster_mode)

        monkeypatch.setattr(glide_shared.opentelemetry, "_otel_trace", None)
        remove_span_file()

        with use_parent_span(sampled=True):
            client.get("GlideSync_test_no_otel_api")

        _wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["Get"]
        )
        _, span_objects, _ = read_and_parse_span_file(VALID_ENDPOINT_TRACES)
        assert_root_spans(span_objects, "Get")

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_parent_context_extraction_failure_falls_back(
        self, request, cluster_mode, monkeypatch
    ):
        """A misbehaving OTel SDK must never fail a command; spans fall back to roots."""
        client = create_sync_client(request, cluster_mode=cluster_mode)

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

        client.set("GlideSync_test_broken_otel_api", "value")
        assert client.get("GlideSync_test_broken_otel_api") == b"value"

        _wait_for_spans_to_be_flushed(
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

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_parented_span_memory_leak(self, request, cluster_mode):
        """The parented path allocates per-command C strings. Make sure repeated use
        does not leak."""
        client = create_sync_client(request, cluster_mode=cluster_mode)

        gc.collect()
        process = psutil.Process()
        start_memory = process.memory_info().rss

        with use_parent_span(sampled=True):
            for i in range(100):
                key = f"GlideSync_test_parented_leak_{i}"
                client.set(key, "value")
                client.get(key)

        client.close()
        gc.collect()
        end_memory = process.memory_info().rss
        assert end_memory < start_memory * 1.1
