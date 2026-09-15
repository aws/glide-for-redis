# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
OpenTelemetry configuration classes shared between async and sync clients.

OpenTelemetry can only be initialized once per process. Calling OpenTelemetry.init()
more than once will be ignored. If you need to change configuration, restart the process
with new settings.

OpenTelemetry Configuration
----------------------------

OpenTelemetryConfig: Main configuration object for OpenTelemetry exporters and options.

* traces: (optional) Configure trace exporting using OpenTelemetryTracesConfig.

  * endpoint: The collector endpoint for traces. Supported protocols:
    http://, https:// for HTTP/HTTPS, grpc:// for gRPC, file:// for local file export
  * sample_percentage: (optional) The percentage of requests to sample (0-100). Defaults to 1.
    Note: Higher sampling percentages impact performance. Recommended: 1-5% in production.
  * enable_trace_context_propagation: (optional) Whether sampled GLIDE spans use the
    active OpenTelemetry span as their parent. Defaults to False.

* metrics: (optional) Configure metrics exporting using OpenTelemetryMetricsConfig.

  * endpoint: The collector endpoint for metrics. Same protocol rules as above.

* flush_interval_ms: (optional) Interval in milliseconds for flushing data. Defaults to 5000ms.

File Exporter Details
---------------------

For file:// endpoints:

* Path must start with file:// (e.g., file:///tmp/otel or file:///tmp/otel/traces.json)
* If path is a directory or lacks extension, data is written to signals.json in that directory
* If path includes filename with extension, that file is used as-is
* Parent directory must exist; otherwise initialization fails with InvalidInput error
* If target file exists, new data is appended (not overwritten)

Validation Rules
----------------

* flush_interval_ms must be a positive integer
* sample_percentage must be between 0 and 100
* File exporter paths must start with file:// and have an existing parent directory
* Invalid configuration will throw an error when calling OpenTelemetry.init()

Trace Context Propagation
-------------------------

When ``enable_trace_context_propagation`` is enabled, sampling selects a GLIDE
command, batch or script span, and the application has an active OpenTelemetry span,
GLIDE creates the selected span as its child. The parent is read from the OpenTelemetry
Python API, which propagates the active span through ``contextvars``, so nothing is
passed to GLIDE. Propagation requires the optional ``opentelemetry-api`` package and
is disabled by default. Without it, spans are created as independent trace roots.

See the "Trace Context Propagation" section of the Python README for the user-facing
description, including how ``sample_percentage`` interacts with the parent span and
where GLIDE's spans are exported.
"""

import random
from dataclasses import dataclass
from typing import Any, Optional, Tuple

from glide_shared.exceptions import ConfigurationError

try:
    from opentelemetry import trace as _otel_trace
except ImportError:
    _otel_trace = None  # type: ignore[assignment]


@dataclass
class _ParentSpanContext:
    """A valid parent span context extracted from the OpenTelemetry context."""

    __slots__ = ("trace_id", "span_id", "trace_flags", "trace_state")

    trace_id: bytes
    span_id: bytes
    trace_flags: int
    trace_state: Optional[bytes]

    def to_cstrings(self, ffi: Any) -> Tuple[Any, Any, Any]:
        """Marshal the trace context into C strings for an FFI span creation call.

        The returned ``char[]`` buffers must stay referenced for the duration of the
        FFI call, the caller keeps them alive by holding them until the call returns.

        Args:
            ffi: The CFFI ``ffi`` object from ``GlideFFI``.

        Returns:
            Tuple[Any, Any, Any]: The trace_id, span_id and trace_state buffers, the
                last being ``ffi.NULL`` when there is no tracestate.
        """
        return (
            ffi.new("char[]", self.trace_id),
            ffi.new("char[]", self.span_id),
            (
                ffi.new("char[]", self.trace_state)
                if self.trace_state is not None
                else ffi.NULL
            ),
        )


def _create_command_span(
    ffi: Any, lib: Any, span_name_cstr: Any, parent: Optional[_ParentSpanContext]
) -> int:
    """Create a command span, parented to ``parent`` when one was extracted.

    Args:
        ffi: The CFFI ``ffi`` object from ``GlideFFI``.
        lib: The CFFI ``lib`` object from ``GlideFFI``.
        span_name_cstr: A ``char[]`` holding the span name.
        parent: The extracted parent context, or ``None`` for an independent root span.

    Returns:
        int: The span pointer, or 0 if span creation failed.
    """
    if parent is None:
        return lib.create_named_otel_span(span_name_cstr)

    # The buffers stay referenced by these locals across the call below.
    trace_id_cstr, span_id_cstr, trace_state_cstr = parent.to_cstrings(ffi)
    return lib.create_named_otel_span_with_trace_context(
        span_name_cstr,
        trace_id_cstr,
        span_id_cstr,
        parent.trace_flags,
        trace_state_cstr,
    )


def _create_batch_span(ffi: Any, lib: Any, parent: Optional[_ParentSpanContext]) -> int:
    """Create a batch span, parented to ``parent`` when one was extracted.

    Args:
        ffi: The CFFI ``ffi`` object from ``GlideFFI``.
        lib: The CFFI ``lib`` object from ``GlideFFI``.
        parent: The extracted parent context, or ``None`` for an independent root span.

    Returns:
        int: The span pointer, or 0 if span creation failed.
    """
    if parent is None:
        return lib.create_batch_otel_span()

    # The buffers stay referenced by these locals across the call below.
    trace_id_cstr, span_id_cstr, trace_state_cstr = parent.to_cstrings(ffi)
    return lib.create_batch_otel_span_with_trace_context(
        trace_id_cstr,
        span_id_cstr,
        parent.trace_flags,
        trace_state_cstr,
    )


class OpenTelemetryTracesConfig:
    """Configuration for exporting OpenTelemetry traces."""

    def __init__(
        self,
        endpoint: str,
        sample_percentage: Optional[int] = None,
        enable_trace_context_propagation: bool = False,
    ) -> None:
        self.endpoint = endpoint
        self.sample_percentage = (
            sample_percentage if sample_percentage is not None else 1
        )
        self.enable_trace_context_propagation = enable_trace_context_propagation

    def get_endpoint(self) -> str:
        return self.endpoint

    def get_sample_percentage(self) -> int:
        return self.sample_percentage


class OpenTelemetryMetricsConfig:
    """Configuration for exporting OpenTelemetry metrics."""

    def __init__(self, endpoint: str) -> None:
        self.endpoint = endpoint

    def get_endpoint(self) -> str:
        return self.endpoint


class OpenTelemetryConfig:
    """Configuration for OpenTelemetry integration."""

    def __init__(
        self,
        traces: Optional[OpenTelemetryTracesConfig] = None,
        metrics: Optional[OpenTelemetryMetricsConfig] = None,
        flush_interval_ms: Optional[int] = None,
    ) -> None:
        self.traces = traces
        self.metrics = metrics
        self.flush_interval_ms = flush_interval_ms

    def get_traces(self) -> Optional[OpenTelemetryTracesConfig]:
        return self.traces

    def set_traces(self, traces: OpenTelemetryTracesConfig) -> None:
        self.traces = traces

    def get_metrics(self) -> Optional[OpenTelemetryMetricsConfig]:
        return self.metrics

    def get_flush_interval_ms(self) -> Optional[int]:
        return self.flush_interval_ms


class OpenTelemetry:
    """
    Singleton class for managing OpenTelemetry configuration and operations.

    This class provides a centralized way to initialize OpenTelemetry and control
    sampling behavior at runtime.

    Example usage::

        from glide import OpenTelemetry, OpenTelemetryConfig, OpenTelemetryTracesConfig, OpenTelemetryMetricsConfig

        OpenTelemetry.init(OpenTelemetryConfig(
            traces=OpenTelemetryTracesConfig(
                endpoint="http://localhost:4318/v1/traces",
                sample_percentage=10
            ),
            metrics=OpenTelemetryMetricsConfig(
                endpoint="http://localhost:4318/v1/metrics"
            ),
            flush_interval_ms=1000
        ))

    Note:
        OpenTelemetry can only be initialized once per process. Subsequent calls to
        init() will be ignored. This is by design, as OpenTelemetry is a global
        resource that should be configured once at application startup.
    """

    _instance: Optional["OpenTelemetry"] = None
    _config: Optional[OpenTelemetryConfig] = None

    @classmethod
    def init(cls, config: OpenTelemetryConfig) -> None:
        """Initialize OpenTelemetry with the given configuration.

        This method can only be called once per process. Subsequent calls will log
        a warning and be ignored.

        Args:
            config: The OpenTelemetry configuration specifying trace/metrics endpoints
                and flush intervals.

        Raises:
            ConfigurationError: If the underlying FFI initialization fails (e.g., invalid
                endpoint or file path).
        """
        if not cls._instance:
            cls._config = config
            from glide_shared._glide_ffi import GlideFFI
            from glide_shared.logger import Level, Logger

            ffi = GlideFFI.ffi
            lib = GlideFFI.lib

            traces_ptr = ffi.NULL
            traces_endpoint_cstr = None
            if config.traces:
                traces_endpoint_cstr = ffi.new(
                    "char[]", config.traces.endpoint.encode()
                )
                traces_config = ffi.new("OpenTelemetryTracesConfig*")
                traces_config.endpoint = traces_endpoint_cstr
                traces_config.has_sample_percentage = True
                traces_config.sample_percentage = config.traces.sample_percentage
                traces_ptr = traces_config

            metrics_ptr = ffi.NULL
            metrics_endpoint_cstr = None
            if config.metrics:
                metrics_endpoint_cstr = ffi.new(
                    "char[]", config.metrics.endpoint.encode()
                )
                metrics_config = ffi.new("OpenTelemetryMetricsConfig*")
                metrics_config.endpoint = metrics_endpoint_cstr
                metrics_ptr = metrics_config

            otel_config = ffi.new("OpenTelemetryConfig*")
            otel_config.traces = traces_ptr
            otel_config.metrics = metrics_ptr
            otel_config.has_flush_interval_ms = config.flush_interval_ms is not None
            otel_config.flush_interval_ms = (
                config.flush_interval_ms if config.flush_interval_ms else 0
            )

            error = lib.init_open_telemetry(otel_config)
            if error != ffi.NULL:
                error_msg = ffi.string(error).decode()
                lib.free_c_string(error)
                raise ConfigurationError(
                    f"Failed to initialize OpenTelemetry: {error_msg}"
                )

            cls._instance = OpenTelemetry()
            Logger.log(
                Level.INFO,
                "GlideOpenTelemetry",
                "OpenTelemetry initialized successfully",
            )
            return

        from glide_shared.logger import Level, Logger

        Logger.log(
            Level.WARN,
            "GlideOpenTelemetry",
            "OpenTelemetry already initialized - ignoring new configuration",
        )

    @classmethod
    def is_initialized(cls) -> bool:
        """Check whether OpenTelemetry has been initialized.

        Returns:
            bool: True if init() has been successfully called, False otherwise.
        """
        return cls._instance is not None

    @classmethod
    def is_tracing_enabled(cls) -> bool:
        """Check whether trace exporting was successfully initialized.

        Returns:
            bool: True if OpenTelemetry is initialized with traces configured.
        """
        return (
            cls._instance is not None
            and cls._config is not None
            and cls._config.traces is not None
        )

    @classmethod
    def get_sample_percentage(cls) -> Optional[int]:
        """Get the current trace sampling percentage.

        Returns:
            Optional[int]: The sampling percentage (0-100), or None if traces are not configured.
        """
        if cls._config and cls._config.traces:
            return cls._config.traces.sample_percentage
        return None

    @classmethod
    def should_sample(cls) -> bool:
        """Determine whether the current request should be sampled.

        Uses random sampling based on the configured sample percentage.

        Returns:
            bool: True if the request should be traced, False otherwise.
                Always returns False if OpenTelemetry is not initialized.
        """
        if not cls._instance:
            return False
        percentage = cls.get_sample_percentage()
        return percentage is not None and random.random() * 100 < percentage

    @classmethod
    def _get_parent_span_context(cls) -> Optional[_ParentSpanContext]:
        """Extract the OpenTelemetry span context, if it is usable as a parent.

        Returns:
            Optional[_ParentSpanContext]: The active span context, or None if
                propagation is disabled, ``opentelemetry-api`` is not installed,
                no valid span is active, or the context could not be read.
        """
        if (
            cls._config is None
            or cls._config.traces is None
            or not cls._config.traces.enable_trace_context_propagation
        ):
            return None

        if _otel_trace is None:
            return None

        try:
            span_context = _otel_trace.get_current_span().get_span_context()
            if not span_context.is_valid:
                return None
            trace_flags = span_context.trace_flags

            # to_header() is the W3C `tracestate` serialization
            trace_state = span_context.trace_state
            trace_state_header = trace_state.to_header() if trace_state else ""
            return _ParentSpanContext(
                trace_id=format(span_context.trace_id, "032x").encode(),
                span_id=format(span_context.span_id, "016x").encode(),
                trace_flags=int(trace_flags),
                trace_state=trace_state_header.encode() or None,
            )
        except Exception as e:
            from glide_shared.logger import Level, Logger

            Logger.log(
                Level.DEBUG,
                "GlideOpenTelemetry",
                f"Failed to read the active span context: {e}. "
                "Continuing as if no span were active.",
            )
            return None

    @classmethod
    def set_sample_percentage(cls, percentage: int) -> None:
        """Update the trace sampling percentage at runtime.

        Args:
            percentage: The new sampling percentage (0-100).

        Raises:
            ConfigurationError: If traces are not initialized or percentage is out of range.
        """
        if not cls._config or not cls._config.traces:
            raise ConfigurationError("OpenTelemetry traces not initialized")
        if percentage < 0 or percentage > 100:
            raise ConfigurationError("Sample percentage must be between 0 and 100")
        cls._config.traces.sample_percentage = percentage
