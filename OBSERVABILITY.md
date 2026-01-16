# Observability Guide

This document explains the observability features built into the sixseven (67) backend.

## Overview

The backend includes three pillars of observability:

1. **Structured Logging** - JSON logs with context
2. **Distributed Tracing** - OpenTelemetry spans
3. **Metrics** - Prometheus-compatible metrics

## Structured Logging

### Features

- **JSON format** for easy parsing
- **Contextual information** automatically added to all logs:
  - `request_id`: Unique ID for each API request
  - `session_id`: User session identifier
  - `job_id`: Job identifier for background tasks
- **Structured fields** instead of string interpolation
- **Log levels**: info, warning, error

### Example Log Output

```json
{
  "event": "command_received",
  "command": "research quantum computing",
  "has_image": false,
  "session_id": "abc-123",
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-01-16T10:30:45.123456Z",
  "level": "info",
  "logger": "app.main"
}
```

### Key Log Events

**API Layer:**
- `command_received` - New command received
- `command_handled` - Command successfully processed
- `command_error` - Command processing failed
- `health_check` - Health endpoint called
- `get_job` - Job details requested
- `jobs_listed` - Jobs list requested
- `job_cancelled` - Job cancellation requested

**Orchestrator:**
- `intent_parsed` - Command intent determined
- `research_execution_error` - Research workflow error
- `creative_execution_error` - Creative workflow error

**Research Agent:**
- `research_started` - Research job started
- `research_task_created` - Yutori task created
- `research_poll_completed` - Polling finished
- `research_succeeded` - Research completed successfully
- `research_failed` - Research failed
- `research_cancelled` - Research cancelled
- `research_error` - Unexpected error

**Creative Agent:**
- `creative_started` - Creative job started
- `creative_succeeded` - Image generated successfully
- `creative_async` - Async response received
- `creative_failed` - Image generation failed
- `creative_no_image` - No image provided
- `creative_error` - Unexpected error

**Job Observer:**
- `job_created` - New job created
- `job_started` - Job execution started
- `job_progress` - Job progress update
- `job_completed` - Job finished (success/failure)
- `external_api_call` - External API called

### Usage in Code

```python
from app.observability import get_logger

logger = get_logger(__name__)

# Simple log
logger.info("operation_completed")

# Log with structured data
logger.info(
    "user_action",
    user_id="123",
    action="create",
    resource="job"
)

# Error with exception
logger.error(
    "operation_failed",
    error=str(e),
    exc_info=True
)
```

## Distributed Tracing

### Features

- **OpenTelemetry** standard
- **Automatic instrumentation** for FastAPI and httpx
- **Custom spans** for business logic
- **Trace context propagation** across async operations

### Trace Hierarchy

```
handle_command (span)
├── parse_intent
├── create_job
└── execute_research (span)
    ├── create_yutori_task (span)
    └── poll_yutori_task (span)
        ├── http_get (auto-instrumented)
        └── http_get (auto-instrumented)
```

### Span Attributes

Each span includes:
- `command.text` - Command text
- `command.has_image` - Whether image provided
- `response.intent` - Detected intent
- `response.job_id` - Created job ID
- `job.type` - Job type (research/creative)
- `job.status` - Job status

### Exporting Traces

**Development** (default):
- Console exporter - prints to stdout

**Production** (configure):
```python
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter

exporter = OTLPSpanExporter(
    endpoint="http://jaeger:4317",
    insecure=True
)
```

Supported backends:
- Jaeger
- Zipkin
- Honeycomb
- Datadog
- New Relic

## Metrics

### Prometheus Metrics

**Command Counter** (`sixseven_commands_total`)
- Type: Counter
- Labels: `intent`, `status`
- Tracks: Total commands by intent and outcome

**Job Duration** (`sixseven_job_duration_seconds`)
- Type: Histogram
- Labels: `job_type`, `status`
- Tracks: Job execution time distribution

**Active Jobs** (`sixseven_active_jobs`)
- Type: Gauge
- Labels: `job_type`
- Tracks: Currently running jobs

**API Latency** (`sixseven_api_latency_seconds`)
- Type: Histogram
- Labels: `endpoint`, `method`, `status_code`
- Tracks: API endpoint response times

**External API Calls** (`sixseven_external_api_calls_total`)
- Type: Counter
- Labels: `provider`, `status`
- Tracks: Calls to Yutori/Freepik

**External API Latency** (`sixseven_external_api_latency_seconds`)
- Type: Histogram
- Labels: `provider`
- Tracks: External API response times

### Metrics Endpoint

```bash
curl http://localhost:8000/metrics
```

**Example Output:**
```
# HELP sixseven_commands_total Total number of commands received
# TYPE sixseven_commands_total counter
sixseven_commands_total{intent="research",status="created"} 42.0
sixseven_commands_total{intent="research",status="succeeded"} 38.0
sixseven_commands_total{intent="research",status="failed"} 4.0

# HELP sixseven_job_duration_seconds Job execution duration
# TYPE sixseven_job_duration_seconds histogram
sixseven_job_duration_seconds_bucket{job_type="research",status="succeeded",le="5.0"} 10.0
sixseven_job_duration_seconds_bucket{job_type="research",status="succeeded",le="10.0"} 25.0
sixseven_job_duration_seconds_bucket{job_type="research",status="succeeded",le="+Inf"} 38.0
sixseven_job_duration_seconds_sum{job_type="research",status="succeeded"} 285.5
sixseven_job_duration_seconds_count{job_type="research",status="succeeded"} 38.0

# HELP sixseven_active_jobs Number of currently active jobs
# TYPE sixseven_active_jobs gauge
sixseven_active_jobs{job_type="research"} 3.0
sixseven_active_jobs{job_type="creative"} 1.0
```

### Prometheus Configuration

**prometheus.yml:**
```yaml
scrape_configs:
  - job_name: 'sixseven'
    scrape_interval: 15s
    static_configs:
      - targets: ['localhost:8000']
    metrics_path: '/metrics'
```

## Request Context

### Context Variables

The system uses Python `contextvars` to propagate context:

```python
request_id_var: ContextVar[Optional[str]]
session_id_var: ContextVar[Optional[str]]
job_id_var: ContextVar[Optional[str]]
```

These are automatically:
- Set by middleware for each request
- Included in all log entries
- Propagated to background tasks
- Added to response headers (`X-Request-ID`)

### Request ID Flow

```
1. Request arrives
2. Middleware generates UUID
3. request_id_var.set(uuid)
4. All logs include request_id
5. Response includes X-Request-ID header
6. Client can use for correlation
```

## Job Observer

### Purpose

The `JobObserver` class provides a centralized way to track job lifecycle events and emit metrics/logs.

### Methods

**job_created(job)**
- Increments command counter
- Increments active jobs gauge
- Logs job creation

**job_started(job)**
- Logs job execution start

**job_progress(job, progress, message)**
- Logs progress updates

**job_completed(job)**
- Records job duration histogram
- Decrements active jobs gauge
- Increments command counter with final status
- Logs completion with details

**external_api_call(provider, operation, duration, success)**
- Increments API call counter
- Records API latency histogram
- Logs API call details

**trace_span(name, attributes)**
- Creates OpenTelemetry span
- Sets custom attributes

### Usage

```python
# In orchestrator
if self.observer:
    self.observer.job_created(job)

# In agent
if self.observer:
    self.observer.job_started(job)
    self.observer.job_progress(job, 50, "Halfway done")
    self.observer.external_api_call("yutori", "create_task", 1.5, True)
    self.observer.job_completed(job)
```

## Monitoring Dashboards

### Grafana Dashboard Example

**Key Panels:**

1. **Request Rate**
   ```promql
   rate(sixseven_commands_total[5m])
   ```

2. **Success Rate**
   ```promql
   sum(rate(sixseven_commands_total{status="succeeded"}[5m])) /
   sum(rate(sixseven_commands_total[5m]))
   ```

3. **P95 Latency**
   ```promql
   histogram_quantile(0.95, 
     rate(sixseven_api_latency_seconds_bucket[5m])
   )
   ```

4. **Active Jobs**
   ```promql
   sixseven_active_jobs
   ```

5. **External API Errors**
   ```promql
   rate(sixseven_external_api_calls_total{status="error"}[5m])
   ```

6. **Job Duration by Type**
   ```promql
   histogram_quantile(0.95,
     rate(sixseven_job_duration_seconds_bucket[5m])
   ) by (job_type)
   ```

## Alerting

### Recommended Alerts

**High Error Rate:**
```yaml
alert: HighErrorRate
expr: |
  sum(rate(sixseven_commands_total{status="failed"}[5m])) /
  sum(rate(sixseven_commands_total[5m])) > 0.1
for: 5m
annotations:
  summary: "Error rate above 10%"
```

**Slow API Response:**
```yaml
alert: SlowAPIResponse
expr: |
  histogram_quantile(0.95,
    rate(sixseven_api_latency_seconds_bucket[5m])
  ) > 5
for: 5m
annotations:
  summary: "P95 latency above 5 seconds"
```

**External API Failures:**
```yaml
alert: ExternalAPIFailures
expr: |
  rate(sixseven_external_api_calls_total{status="error"}[5m]) > 0.5
for: 5m
annotations:
  summary: "External API error rate high"
```

**Stuck Jobs:**
```yaml
alert: StuckJobs
expr: sixseven_active_jobs > 10
for: 30m
annotations:
  summary: "More than 10 jobs active for 30+ minutes"
```

## Log Aggregation

### ELK Stack

**Filebeat configuration:**
```yaml
filebeat.inputs:
  - type: log
    enabled: true
    paths:
      - /var/log/sixseven/*.log
    json.keys_under_root: true
    json.add_error_key: true

output.elasticsearch:
  hosts: ["localhost:9200"]
  index: "sixseven-%{+yyyy.MM.dd}"
```

**Kibana queries:**
```
# All errors
level: "error"

# Specific job
job_id: "550e8400-e29b-41d4-a716-446655440000"

# Slow operations
duration_seconds: >10

# External API failures
event: "external_api_call" AND success: false
```

### Datadog

**Agent configuration:**
```yaml
logs:
  - type: file
    path: /var/log/sixseven/*.log
    service: sixseven
    source: python
    sourcecategory: sourcecode
```

## Best Practices

1. **Always use structured logging**
   ```python
   # Good
   logger.info("user_created", user_id=user_id, email=email)
   
   # Bad
   logger.info(f"User {user_id} created with email {email}")
   ```

2. **Include context in errors**
   ```python
   logger.error(
       "operation_failed",
       operation="create_task",
       job_id=job.job_id,
       error=str(e),
       exc_info=True
   )
   ```

3. **Use observer for job events**
   ```python
   if self.observer:
       self.observer.job_completed(job)
   ```

4. **Create spans for important operations**
   ```python
   with tracer.start_as_current_span("expensive_operation") as span:
       span.set_attribute("param", value)
       # do work
   ```

5. **Track external API calls**
   ```python
   start = time.time()
   result = await api_call()
   duration = time.time() - start
   
   if self.observer:
       self.observer.external_api_call(
           "provider",
           "operation",
           duration,
           not result.get("error")
       )
   ```

## Troubleshooting

### No logs appearing

Check structlog configuration:
```python
import structlog
print(structlog.get_config())
```

### Metrics not updating

Verify observer is passed to agents:
```python
orchestrator = OrchestratorAgent(store, job_observer)
```

### Traces not showing

Check tracer setup:
```python
from opentelemetry import trace
tracer = trace.get_tracer(__name__)
print(tracer)
```

### Context not propagating

Ensure context vars are set:
```python
from app.observability import request_id_var
print(request_id_var.get())
```

## Production Recommendations

1. **Use OTLP exporter** for traces (not console)
2. **Ship logs** to centralized system (ELK, Datadog, etc.)
3. **Set up Prometheus** scraping
4. **Create Grafana dashboards** for key metrics
5. **Configure alerts** for critical issues
6. **Add log rotation** to prevent disk fill
7. **Sample traces** in high-traffic scenarios (e.g., 10%)
8. **Set retention policies** for logs and metrics
9. **Monitor observer overhead** in production
10. **Use async log shipping** to avoid blocking
