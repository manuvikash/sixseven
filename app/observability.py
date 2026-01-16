"""
Observability module - structured logging, tracing, and metrics
"""
import structlog
import time
from contextvars import ContextVar
from typing import Optional, Dict, Any
from uuid import uuid4
from opentelemetry import trace, metrics
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor, ConsoleSpanExporter
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader, ConsoleMetricExporter
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor
from prometheus_client import Counter, Histogram, Gauge, generate_latest, CONTENT_TYPE_LATEST
from fastapi import Request, Response
import logging

# Context variables for request tracking
request_id_var: ContextVar[Optional[str]] = ContextVar("request_id", default=None)
session_id_var: ContextVar[Optional[str]] = ContextVar("session_id", default=None)
job_id_var: ContextVar[Optional[str]] = ContextVar("job_id", default=None)


# Prometheus metrics
COMMAND_COUNTER = Counter(
    'sixseven_commands_total',
    'Total number of commands received',
    ['intent', 'status']
)

JOB_DURATION = Histogram(
    'sixseven_job_duration_seconds',
    'Job execution duration',
    ['job_type', 'status']
)

ACTIVE_JOBS = Gauge(
    'sixseven_active_jobs',
    'Number of currently active jobs',
    ['job_type']
)

API_LATENCY = Histogram(
    'sixseven_api_latency_seconds',
    'API endpoint latency',
    ['endpoint', 'method', 'status_code']
)

EXTERNAL_API_CALLS = Counter(
    'sixseven_external_api_calls_total',
    'External API calls',
    ['provider', 'status']
)

EXTERNAL_API_LATENCY = Histogram(
    'sixseven_external_api_latency_seconds',
    'External API call latency',
    ['provider']
)


def setup_structlog():
    """Configure structured logging with context."""
    
    def add_context(logger, method_name, event_dict):
        """Add request context to all log entries."""
        event_dict["request_id"] = request_id_var.get()
        event_dict["session_id"] = session_id_var.get()
        event_dict["job_id"] = job_id_var.get()
        return event_dict
    
    structlog.configure(
        processors=[
            structlog.contextvars.merge_contextvars,
            structlog.stdlib.filter_by_level,
            structlog.stdlib.add_logger_name,
            structlog.stdlib.add_log_level,
            structlog.stdlib.PositionalArgumentsFormatter(),
            structlog.processors.TimeStamper(fmt="iso"),
            structlog.processors.StackInfoRenderer(),
            add_context,
            structlog.processors.format_exc_info,
            structlog.processors.UnicodeDecoder(),
            structlog.processors.JSONRenderer()
        ],
        wrapper_class=structlog.stdlib.BoundLogger,
        context_class=dict,
        logger_factory=structlog.stdlib.LoggerFactory(),
        cache_logger_on_first_use=True,
    )


def setup_tracing(service_name: str = "sixseven-backend"):
    """Setup OpenTelemetry tracing."""
    
    # Create tracer provider
    provider = TracerProvider()
    
    # Use console exporter for development (replace with OTLP for production)
    processor = BatchSpanProcessor(ConsoleSpanExporter())
    provider.add_span_processor(processor)
    
    trace.set_tracer_provider(provider)
    
    # Instrument FastAPI and httpx
    FastAPIInstrumentor().instrument()
    HTTPXClientInstrumentor().instrument()
    
    return trace.get_tracer(service_name)


def setup_metrics(service_name: str = "sixseven-backend"):
    """Setup OpenTelemetry metrics."""
    
    # Create metric reader and provider
    reader = PeriodicExportingMetricReader(ConsoleMetricExporter())
    provider = MeterProvider(metric_readers=[reader])
    
    metrics.set_meter_provider(provider)
    
    return metrics.get_meter(service_name)


def get_logger(name: str):
    """Get a structured logger."""
    return structlog.get_logger(name)


class ObservabilityMiddleware:
    """Middleware to add observability to all requests."""
    
    def __init__(self, app):
        self.app = app
    
    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        
        # Generate request ID
        request_id = str(uuid4())
        request_id_var.set(request_id)
        
        # Track request timing
        start_time = time.time()
        
        # Capture response status
        status_code = 200
        
        async def send_wrapper(message):
            nonlocal status_code
            if message["type"] == "http.response.start":
                status_code = message["status"]
                # Add request ID to response headers
                headers = list(message.get("headers", []))
                headers.append((b"x-request-id", request_id.encode()))
                message["headers"] = headers
            await send(message)
        
        try:
            await self.app(scope, receive, send_wrapper)
        finally:
            # Record metrics
            duration = time.time() - start_time
            endpoint = scope.get("path", "unknown")
            method = scope.get("method", "unknown")
            
            API_LATENCY.labels(
                endpoint=endpoint,
                method=method,
                status_code=status_code
            ).observe(duration)


class JobObserver:
    """Observer for job lifecycle events."""
    
    def __init__(self):
        self.logger = get_logger("job_observer")
        self.tracer = trace.get_tracer("job_observer")
    
    def job_created(self, job):
        """Called when a job is created."""
        job_id_var.set(job.job_id)
        session_id_var.set(job.session_id)
        
        COMMAND_COUNTER.labels(
            intent=job.type,
            status="created"
        ).inc()
        
        ACTIVE_JOBS.labels(job_type=job.type).inc()
        
        self.logger.info(
            "job_created",
            job_id=job.job_id,
            job_type=job.type,
            session_id=job.session_id,
            command=job.input.command_text[:100]
        )
    
    def job_started(self, job):
        """Called when a job starts execution."""
        job_id_var.set(job.job_id)
        
        self.logger.info(
            "job_started",
            job_id=job.job_id,
            job_type=job.type
        )
    
    def job_progress(self, job, progress: int, message: str):
        """Called when job progress updates."""
        self.logger.info(
            "job_progress",
            job_id=job.job_id,
            job_type=job.type,
            progress=progress,
            message=message
        )
    
    def job_completed(self, job):
        """Called when a job completes (success or failure)."""
        job_id_var.set(job.job_id)
        
        duration = (job.updated_at - job.created_at).total_seconds()
        
        JOB_DURATION.labels(
            job_type=job.type,
            status=job.status
        ).observe(duration)
        
        ACTIVE_JOBS.labels(job_type=job.type).dec()
        
        COMMAND_COUNTER.labels(
            intent=job.type,
            status=job.status
        ).inc()
        
        log_method = self.logger.info if job.status == "succeeded" else self.logger.error
        
        log_method(
            "job_completed",
            job_id=job.job_id,
            job_type=job.type,
            status=job.status,
            duration_seconds=duration,
            has_result=job.result is not None,
            has_error=job.error is not None,
            event_count=len(job.events)
        )
    
    def external_api_call(self, provider: str, operation: str, duration: float, success: bool):
        """Track external API calls."""
        EXTERNAL_API_CALLS.labels(
            provider=provider,
            status="success" if success else "error"
        ).inc()
        
        EXTERNAL_API_LATENCY.labels(provider=provider).observe(duration)
        
        self.logger.info(
            "external_api_call",
            provider=provider,
            operation=operation,
            duration_seconds=duration,
            success=success
        )
    
    def trace_span(self, name: str, attributes: Optional[Dict[str, Any]] = None):
        """Create a tracing span."""
        span = self.tracer.start_span(name)
        if attributes:
            for key, value in attributes.items():
                span.set_attribute(key, str(value))
        return span


# Global observer instance
job_observer = JobObserver()


def get_metrics_handler():
    """Handler for Prometheus metrics endpoint."""
    async def metrics_endpoint(request: Request):
        return Response(
            content=generate_latest(),
            media_type=CONTENT_TYPE_LATEST
        )
    return metrics_endpoint
